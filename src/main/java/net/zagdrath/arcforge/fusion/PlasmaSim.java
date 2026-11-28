/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.fusion;

// A fusion plasma as a 0-D power and particle balance, stepped once a second (in substeps): cheap enough to run every
// second for every reactor, close enough to real tokamak physics to behave like one.
//
// Units: densities in 10^20 m^-3 (one in-game mB of gas is 10^20 particles), temperature in keV, current in MA, field in
// T, power in MW, energy in MJ, time in s, lengths in m.
//
//  - Fusion: D-T, D-He3 and both D-D branches from the Bosch-Hale reactivities (Reactivity), times a profile peaking
//    factor (the core is hotter and denser than the volume average). The charged products (D-T's alphas, all of D-He3)
//    heat the plasma; the neutrons leave it for the blanket.
//  - Losses: transport with the IPB98(y,2) H-mode confinement time (ISS04 for a stellarator) times an H factor,
//    bremsstrahlung (Zeff-weighted), and impurity line radiation from the impurity fraction (poor vacuum, an eroded wall).
//  - Particles: each species is lost with tau_p = tauPRatio x tau_E (helium ash with heRatio x tau_E); the pumped share
//    leaves as exhaust (what the cryopumps take), the rest recycles back into the plasma.
//  - Current (tokamak): ramped by the Central Solenoid at ipRampRate, each MA costing inductive and resistive flux; in
//    flat-top the solenoid supplies the loop voltage (Spitzer resistivity) less what neutral-beam current drive and the
//    bootstrap current carry. When its flux runs out the current ramps down and the pulse ends.
//  - Limits: the Greenwald density, the Troyon normalised beta, q95 >= 2, a radiative collapse (too cold while carrying
//    current) and the vertical position, held by the Poloidal Field coils; crossing one is a disruption. A stellarator
//    carries no current: no disruptions, no vertical instability, no pulse limit; past its density limit it simply goes
//    out.
public final class PlasmaSim {
    private static final double MU0 = 4.0e-7 * Math.PI;
    // keV x 10^20 m^-3 -> Pa.
    private static final double PRESSURE = 1.602e4;
    // MeV per reaction x 10^40 (two densities) x cm³ -> m³, as MW/m³.
    private static final double POWER = 1.602e-13 * 1.0e40 * 1.0e-6 / 1.0e6;
    private static final int SUBSTEPS = 20;

    // Tunable physics, from the config.
    public static final class Params {
        public double hFactor = 1.0;
        public double stellaratorH = 0.95;
        public double peaking = 1.4;
        public double tauPRatio = 3.0;
        public double heRatio = 5.0;
        public double betaNLimit = 3.5;
        public double greenwaldLimit = 1.0;
        public double ipRampRate = 0.5;
        // Central Solenoid flux swing per R² at 5.3 T (Wb/m²); it scales with the field.
        public double fluxPerR2 = 6.5;
        // Impurity line radiation coefficient (MW m³ per (10^20 m^-3)² per unit impurity fraction).
        public double impurityRadiation = 100.0;
        public double impurityZ = 10.0;
        public double nbiCurrentDrive = 0.2;
        public double bootstrap = 0.6;
        // How far the plasma can drift (as a share of the minor radius) before it hits the wall.
        public double verticalLimit = 0.15;
        // A stellarator's beta limit (%): past it its confinement fails and the plasma goes out.
        public double stellaratorBeta = 4.0;
        // The Sudo density limit's coefficient (0.25 in the original fit; stellarator experiments since sit higher).
        public double sudo = 0.4;
        // D-He3 reactivity multiplier: a gameplay allowance (real D-He3 needs confinement no tokamak reaches), so a large,
        // hot, thin plasma can burn it at all.
        public double dhe3Boost = 40.0;
    }

    public enum Event { NONE, BREAKDOWN, PULSE_END, SHUTDOWN, DISRUPTION_DENSITY, DISRUPTION_BETA, DISRUPTION_VERTICAL,
        DISRUPTION_COLLAPSE, DISRUPTION_Q, STELLARATOR_COLLAPSE }

    // --- Geometry ---
    public final double r, a, kappa, volume, surface;
    public final boolean stellarator;
    private final Params p;

    // --- State ---
    public boolean alive;
    public double nD, nT, nHe3, nAsh;
    public double t;
    public double ip;
    public double z;
    // Central Solenoid flux left in this pulse (Wb), and the full swing at the field it was charged at.
    public double flux;
    public double fluxSwing;
    // Out of flux: the current is being ramped down, and fuelling stops.
    public boolean rampDown;
    public double seconds;

    // --- Inputs, set before each step ---
    public double bField;
    public double pNbi, pEch;
    public double srcD, srcT, srcHe3;
    public double ipTarget;
    // Vertical control: the PF coils' capacity against what the plasma needs (1 = just enough).
    public double pfControl = 1.0;
    public double impurity;
    // Share of the exhaust the cryopumps take (the rest recycles into the plasma).
    public double pumping = 1.0;
    // The share of the power in that radiation may take before the fuelling holds the density back (see fuelDemand).
    public static final double RADIATED_SHARE = 0.6;
    // Whether the vessel is at vacuum and the magnets at field, so breakdown can happen.
    public boolean ready;
    public boolean fastShutdown;

    // --- Outputs, from the last step ---
    public double pFusion, pAlpha, pNeutron, pOhmic, pBrems, pImpurity, pTransport, pHeating;
    public double tauE, betaN, greenwald, q95, vLoop, iDriven;
    public double dtRate, dhe3Rate, ddTRate, ddHe3Rate, neutronRate;
    public double exhaustD, exhaustT, exhaustHe3, exhaustAsh;
    public double energy;

    public PlasmaSim(double majorRadius, boolean stellarator, Params params) {
        this.r = majorRadius;
        this.a = majorRadius / 3.0;
        this.kappa = stellarator ? 1.0 : 1.7;
        this.volume = 2.0 * Math.PI * Math.PI * r * a * a * kappa;
        this.surface = 4.0 * Math.PI * Math.PI * r * a * Math.sqrt((1.0 + kappa * kappa) / 2.0);
        this.stellarator = stellarator;
        this.p = params;
        this.fluxSwing = 0.0;
        this.flux = 0.0;
    }

    // --- Derived quantities ---

    public double electrons() {
        return nD + nT + 2.0 * nHe3 + 2.0 * nAsh;
    }

    public double ions() {
        return nD + nT + nHe3 + nAsh;
    }

    // The effective charge, with the impurity fraction (of the electron density) at impurityZ.
    public double zeff() {
        double ne = electrons();
        if (ne <= 0.0) {
            return 1.0;
        }
        double nImp = impurity * ne;
        double sum = nD + nT + 4.0 * nHe3 + 4.0 * nAsh + nImp * p.impurityZ * p.impurityZ;
        return sum / (ne + nImp * p.impurityZ);
    }

    // The most current the field allows at q95 = 3, and the Greenwald density at a current.
    public double maxCurrent() {
        if (stellarator) {
            return 0.0;
        }
        return 5.0 * a * a * bField * (1.0 + kappa * kappa) / (2.0 * r * 3.0);
    }

    public double greenwaldDensity(double current) {
        return current / (Math.PI * a * a);
    }

    // A stellarator's density limit (Sudo), at its external heating power (the ECH and beams that hold its edge).
    public double sudoDensity() {
        return p.sudo * Math.sqrt(Math.max(pNbi + pEch, 1.0) * Math.max(bField, 0.1) / (a * a * r));
    }

    // The density limit now: Greenwald for a tokamak (at its current), Sudo for a stellarator.
    public double densityLimit() {
        return stellarator ? sudoDensity() : greenwaldDensity(Math.max(ip, 0.01));
    }

    // Plasma thermal energy (MJ).
    public double thermalEnergy() {
        return 0.024 * (electrons() + ions()) * t * volume;
    }

    public double beta() {
        if (bField <= 0.0) {
            return 0.0;
        }
        return (electrons() + ions()) * t * PRESSURE * 2.0 * MU0 / (bField * bField) * 100.0;
    }

    // Inductance and resistive (Ejima) flux to ramp one MA (Wb).
    public double fluxPerMa() {
        return MU0 * r * (Math.log(8.0 * r / a) - 2.0 + 0.35 + 0.3) * 1.0e6;
    }

    // Seconds the solenoid's flux lasts at the present loop voltage (infinite if the current is fully driven).
    public double pulseLeft() {
        if (stellarator) {
            return Double.POSITIVE_INFINITY;
        }
        return vLoop > 1.0e-6 ? flux / vLoop : Double.POSITIVE_INFINITY;
    }

    public double q() {
        return pHeating > 0.01 ? pFusion / pHeating : 0.0;
    }

    public boolean ignited() {
        return alive && pAlpha > 0.0 && pAlpha >= pTransport + pBrems + pImpurity - pOhmic && pFusion > 1.0;
    }

    // Ends the plasma (a disruption or the end of a pulse): the fuel goes out as exhaust, the solenoid recharges for the
    // next pulse.
    public void quench() {
        exhaustD += nD * volume;
        exhaustT += nT * volume;
        exhaustHe3 += nHe3 * volume;
        exhaustAsh += nAsh * volume;
        alive = false;
        nD = nT = nHe3 = nAsh = 0.0;
        t = 0.0;
        ip = 0.0;
        z = 0.0;
        flux = 0.0;
        rampDown = false;
        seconds = 0.0;
    }

    // Particles (10^20) of fuel a second that bring the electron density toward a share of the density limit, for a fuel mix
    // carrying electronsPerIon electrons per ion (1 for D-T, 1.5 for an even D-He3 mix). Replaces what the pumps take (the
    // rest of what's lost recycles back in, so replacing it too would overfill the plasma on a weakly pumped vessel) and
    // closes a fifth of the gap each second, holds back as the pressure nears the beta limit, and stops during a ramp-down.
    public double fuelDemand(double fraction, double electronsPerIon) {
        if (!alive) {
            return 0.0;
        }
        if (rampDown) {
            return 0.0;
        }
        double ne = electrons();
        double target = Math.max(0.0, fraction) * densityLimit();
        // Burn control: thin the plasma as its pressure nears the beta limit, so a hot burn can't run away into it.
        double betaRatio = stellarator ? beta() / p.stellaratorBeta : betaN / p.betaNLimit;
        if (betaRatio > 0.8) {
            target = Math.min(target, ne * 0.8 / betaRatio);
        }
        // Radiation control: hold the density where radiation takes at most RADIATED_SHARE of the power in (heating,
        // ohmic and alpha). Line radiation grows with the density squared and as the plasma cools, so fuelling on past
        // that point would collapse the plasma into a cold, radiating state that burns through the solenoid's flux.
        double powerIn = pHeating + pAlpha, radiated = pBrems + pImpurity;
        if (powerIn > 0.0 && radiated > RADIATED_SHARE * powerIn) {
            target = Math.min(target, ne * Math.sqrt(RADIATED_SHARE * powerIn / radiated));
        }
        double pumped = Math.max(0.0, Math.min(1.0, pumping));
        double ions = (target - ne) * volume * 0.2 + ne * volume * pumped / Math.max(p.tauPRatio * tauE, 0.5);
        return Math.max(0.0, ions / Math.max(1.0, electronsPerIon));
    }

    // The solenoid's full swing at a field.
    public double swingAt(double field) {
        return stellarator ? 0.0 : p.fluxPerR2 * r * r * field / 5.3;
    }

    // One second.
    public Event step() {
        exhaustD = exhaustT = exhaustHe3 = exhaustAsh = 0.0;
        dtRate = dhe3Rate = ddTRate = ddHe3Rate = neutronRate = 0.0;
        pFusion = pAlpha = pNeutron = pOhmic = pBrems = pImpurity = pTransport = 0.0;
        pHeating = 0.0;
        if (!alive) {
            // Breakdown: vessel at vacuum, field up, gas in, and either microwaves or (tokamak) the solenoid to start a current.
            boolean fuel = srcD + srcT > 0.0;
            boolean drive = pEch > 0.0 || (!stellarator && ipTarget > 0.0);
            if (ready && fuel && drive && bField > 0.2) {
                alive = true;
                t = 0.05;
                nD = srcD / volume;
                nT = srcT / volume;
                ip = stellarator ? 0.0 : 0.05;
                fluxSwing = swingAt(bField);
                flux = fluxSwing;
                rampDown = false;
                seconds = 0.0;
                return Event.BREAKDOWN;
            }
            energy = 0.0;
            return Event.NONE;
        }
        if (fastShutdown) {
            quench();
            return Event.SHUTDOWN;
        }
        seconds += 1.0;
        if (rampDown) {
            srcD = srcT = srcHe3 = 0.0;
        }
        double dt = 1.0 / SUBSTEPS;
        double sumFusion = 0, sumAlpha = 0, sumNeutron = 0, sumOhm = 0, sumBrems = 0, sumImp = 0, sumTransport = 0, sumHeat = 0;
        for (int i = 0; i < SUBSTEPS; i++) {
            double ne = electrons();
            double ti = Math.max(t, 0.01);
            // Fusion, with the profile peaking (core pressure squared over the average's).
            double pk = p.peaking;
            double sDT = Reactivity.dt(ti * pk), sDHe3 = Reactivity.dhe3(ti * pk);
            double sDDt = Reactivity.ddT(ti * pk), sDDh = Reactivity.ddHe3(ti * pk);
            double rDT = nD * nT * sDT * 1.0e34 * pk;
            double rDHe3 = nD * nHe3 * sDHe3 * 1.0e34 * pk * p.dhe3Boost;
            double rDDt = 0.5 * nD * nD * sDDt * 1.0e34 * pk;
            double rDDh = 0.5 * nD * nD * sDDh * 1.0e34 * pk;
            // MW/m³.
            double wDT = rDT * Reactivity.DT_ENERGY * 1.602e-19, wDHe3 = rDHe3 * Reactivity.DHE3_ENERGY * 1.602e-19;
            double wDDt = rDDt * Reactivity.DD_T_ENERGY * 1.602e-19, wDDh = rDDh * Reactivity.DD_HE3_ENERGY * 1.602e-19;
            double fusion = (wDT + wDHe3 + wDDt + wDDh) * volume;
            double neutron = (wDT * Reactivity.DT_NEUTRON_SHARE + wDDh * Reactivity.DD_HE3_NEUTRON_SHARE) * volume;
            double alpha = fusion - neutron;
            // Ohmic heating and the current drive.
            double zeff = zeff();
            double resistance = 1.0e-7 * zeff * r / (a * a * kappa * Math.pow(ti, 1.5));
            double driven = 0.0;
            if (!stellarator && ip > 0.0) {
                driven = Math.min(ip, p.nbiCurrentDrive * pNbi / (Math.max(ne, 0.05) * r) + bootstrapCurrent());
            }
            double inductive = Math.max(0.0, ip - driven);
            double ohm = inductive * inductive * resistance * 1.0e6;
            // Heating absorbed: neutral beams shine through a thin plasma.
            double nbiAbsorbed = pNbi * (1.0 - Math.exp(-ne * a * 4.0));
            double heat = nbiAbsorbed + pEch * 0.95 + ohm;
            // Radiation.
            double brems = 5.35e-3 * zeff * ne * ne * Math.sqrt(ti) * volume;
            double lineRad = p.impurityRadiation * impurity * ne * ne * volume / Math.max(1.0, Math.sqrt(ti));
            double loss = Math.max(heat + alpha - brems - lineRad, 0.5);
            double tau = confinement(loss, ne);
            tauE = tau;
            double w = thermalEnergy();
            // Implicit in transport: w' = (w + dt (in - rad)) / (1 + dt / tau).
            double wNew = (w + dt * (alpha + heat - brems - lineRad)) / (1.0 + dt / tau);
            double transport = wNew / tau;
            double nSum = electrons() + ions();
            t = nSum > 1.0e-6 ? Math.max(0.0, wNew / (0.024 * nSum * volume)) : 0.0;
            // Particles: burn, fuelling (NBI deposits deuterium too), losses and pumping.
            double tauP = Math.max(0.05, p.tauPRatio * tau), tauHe = Math.max(0.05, p.heRatio * tau);
            if (rampDown) {
                // The ramp-down pumps the density out with the current, to stay under the falling density limit.
                tauP = Math.min(tauP, 1.0);
                tauHe = Math.min(tauHe, 1.0);
            }
            double pumped = Math.max(0.0, Math.min(1.0, pumping));
            double beamD = pNbi > 0.0 && !rampDown ? pNbi * 0.002 : 0.0;
            double lossD = nD / tauP, lossT = nT / tauP, lossHe3 = nHe3 / tauP, lossAsh = nAsh / tauHe;
            nD += dt * ((srcD + beamD) / volume - 2.0 * (rDDt + rDDh) * 1.0e-20 - (rDT + rDHe3) * 1.0e-20 - lossD * pumped);
            nT += dt * (srcT / volume - rDT * 1.0e-20 + rDDt * 1.0e-20 - lossT * pumped);
            nHe3 += dt * (srcHe3 / volume - rDHe3 * 1.0e-20 + rDDh * 1.0e-20 - lossHe3 * pumped);
            nAsh += dt * ((rDT + rDHe3) * 1.0e-20 - lossAsh * pumped);
            nD = Math.max(0.0, nD);
            nT = Math.max(0.0, nT);
            nHe3 = Math.max(0.0, nHe3);
            nAsh = Math.max(0.0, nAsh);
            exhaustD += dt * lossD * pumped * volume;
            exhaustT += dt * lossT * pumped * volume;
            exhaustHe3 += dt * lossHe3 * pumped * volume;
            exhaustAsh += dt * lossAsh * pumped * volume;
            dtRate += dt * rDT * 1.0e-20 * volume;
            dhe3Rate += dt * rDHe3 * 1.0e-20 * volume;
            ddTRate += dt * rDDt * 1.0e-20 * volume;
            ddHe3Rate += dt * rDDh * 1.0e-20 * volume;
            neutronRate += dt * (rDT + rDDh) * 1.0e-20 * volume;
            sumFusion += fusion * dt;
            sumAlpha += alpha * dt;
            sumNeutron += neutron * dt;
            sumOhm += ohm * dt;
            sumBrems += brems * dt;
            sumImp += lineRad * dt;
            sumTransport += transport * dt;
            sumHeat += (nbiAbsorbed + pEch * 0.95) * dt;
            iDriven = driven;
            vLoop = inductive * resistance * 1.0e6;
        }
        pFusion = sumFusion;
        pAlpha = sumAlpha;
        pNeutron = sumNeutron;
        pOhmic = sumOhm;
        pBrems = sumBrems;
        pImpurity = sumImp;
        pTransport = sumTransport;
        pHeating = sumHeat + sumOhm;
        energy = thermalEnergy();
        return stellarator ? stellaratorLimits() : tokamakCurrent();
    }

    private double bootstrapCurrent() {
        if (ip <= 0.01) {
            return 0.0;
        }
        double bp = 0.2 * ip / (a * Math.sqrt((1.0 + kappa * kappa) / 2.0));
        double betaP = (electrons() + ions()) * t * PRESSURE * 2.0 * MU0 / (bp * bp);
        double fraction = Math.min(0.6, p.bootstrap * Math.sqrt(a / r) * betaP);
        return fraction * ip;
    }

    // Energy confinement time (s) at a heating power and electron density.
    public double confinement(double power, double ne) {
        double n19 = Math.max(ne, 0.01) * 10.0;
        double b = Math.max(bField, 0.05);
        double pw = Math.max(power, 0.5);
        if (stellarator) {
            // ISS04 at iota 0.9.
            return p.stellaratorH * 0.134 * Math.pow(a, 2.28) * Math.pow(r, 0.64) * Math.pow(pw, -0.61) * Math.pow(n19, 0.54)
                    * Math.pow(b, 0.84) * Math.pow(0.9, 0.41);
        }
        double current = Math.max(ip, 0.05);
        return p.hFactor * 0.0562 * Math.pow(current, 0.93) * Math.pow(b, 0.15) * Math.pow(pw, -0.69) * Math.pow(n19, 0.41)
                * Math.pow(2.5, 0.19) * Math.pow(r, 1.97) * Math.pow(a / r, 0.58) * Math.pow(kappa, 0.78);
    }

    // The tokamak's current: ramped by the solenoid toward the target, held while its flux lasts; then the limits.
    private Event tokamakCurrent() {
        double target = Math.min(ipTarget, maxCurrent() * 1.5);
        if (flux > 0.0) {
            if (ip < target) {
                double step = Math.min(p.ipRampRate, target - ip);
                double cost = step * fluxPerMa();
                if (cost <= flux) {
                    ip += step;
                    flux -= cost;
                } else {
                    ip += flux / fluxPerMa();
                    flux = 0.0;
                }
            } else if (ip > target) {
                ip = Math.max(target, ip - p.ipRampRate);
            }
            flux = Math.max(0.0, flux - vLoop);
        } else {
            // Out of flux: the current decays (a planned ramp-down), with the fuelling off.
            rampDown = true;
            ip = Math.max(0.0, ip - p.ipRampRate);
        }
        q95 = ip > 0.01 ? 5.0 * a * a * bField * (1.0 + kappa * kappa) / (2.0 * r * ip) : 99.0;
        double ne = electrons();
        greenwald = ip > 0.01 ? ne / greenwaldDensity(ip) : 0.0;
        betaN = ip > 0.01 ? beta() * a * bField / ip : 0.0;
        // Vertical position: drifts out when the PF coils can't hold the elongated plasma, recentres when they can.
        double need = (kappa - 1.2) * ip / Math.max(maxCurrent(), 0.1);
        double capacity = need <= 0.0 ? 10.0 : pfControl / need;
        if (capacity >= 1.0) {
            z *= 0.5;
        } else {
            z += Math.signum(z == 0.0 ? 1.0 : z) * (1.0 - capacity) * 0.08 * a;
        }
        if (Math.abs(z) > p.verticalLimit * a) {
            return Event.DISRUPTION_VERTICAL;
        }
        if (ip > 0.2 && greenwald > p.greenwaldLimit && !rampDown) {
            return Event.DISRUPTION_DENSITY;
        }
        if (ip > 0.2 && betaN > p.betaNLimit) {
            return Event.DISRUPTION_BETA;
        }
        if (ip > 0.5 && q95 < 2.0) {
            return Event.DISRUPTION_Q;
        }
        // A radiative collapse: too cold while still carrying current. Not during the planned ramp-down at the end of a
        // pulse, when the fuelling is off and the plasma is meant to cool as its current comes down.
        if (seconds > 15.0 && ip > 0.5 && t < 0.1 && !rampDown) {
            return Event.DISRUPTION_COLLAPSE;
        }
        if (flux <= 0.0 && ip <= 0.05) {
            // The pulse is over: the plasma goes out (what's left of it to the pumps), so the solenoid can recharge for
            // the next one.
            quench();
            return Event.PULSE_END;
        }
        return Event.NONE;
    }

    private Event stellaratorLimits() {
        q95 = 0.0;
        betaN = 0.0;
        ip = 0.0;
        vLoop = 0.0;
        double ne = electrons();
        double sudo = sudoDensity();
        greenwald = sudo > 0.0 ? ne / sudo : 0.0;
        if (seconds > 15.0 && (t < 0.1 || greenwald > 1.5 * p.greenwaldLimit || beta() > p.stellaratorBeta)) {
            return Event.STELLARATOR_COLLAPSE;
        }
        return Event.NONE;
    }
}
