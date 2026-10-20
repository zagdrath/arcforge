/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear.reactor;

import java.util.Arrays;

// The Fission Reactor's physics, a tick at a time (no Minecraft classes; FissionReactorBlockEntity feeds it coolant
// and takes what it sends out):
//  - Neutronics: the core's k-effective and power shape from CoreLattice, solved each second (every quarter second
//    while the rods move or the power changes fast). Between solves the reactivity follows the fuel temperature, the
//    coolant's temperature and the void through coefficients found by solving the core again a little perturbed.
//  - Kinetics: point kinetics with one group of delayed neutrons (β 1%, scaled), so below prompt critical the power
//    changes smoothly with a period, and above it climbs in seconds. A start-up source keeps a shut-down core's
//    power just above zero, so a start-up shows on the log scale.
//  - Heat: fission heat plus decay heat (three groups, about 6.5% of the power it ran at, fading over in-game hours)
//    into the fuel, through the cladding, into the coolant (and the vessel's steel). A pressurized core (a Hot Leg
//    port) pumps its coolant out as Hot Coolant; a boiling core (Steam ports) boils it into steam; with neither, it
//    only heats up until the relief valve vents it.
//  - Xenon: iodine-135 from fission decays into xenon-135, which fission flux burns out; after the power drops, xenon
//    builds up for hours and can hold a restart off.
//  - Burnup: each fuel rod uses its U-235, makes plutonium and fission products (poisons), and is spent at 100%.
//  - Protection: trips (high power, short period, high fuel temperature, high pressure, low coolant flow), a manual
//    SCRAM and the hard trip on cladding failure drop every rod in.
public final class ReactorSim {
    public static final int BANKS = 4;
    public static final double AMBIENT = 20.0;

    public enum Mode { NONE, PRESSURIZED, BOILING }

    public enum Trip { NONE, MANUAL, HIGH_POWER, SHORT_PERIOD, HIGH_FUEL_TEMPERATURE, HIGH_PRESSURE, LOW_COOLANT_FLOW, CLADDING_FAILURE, REDSTONE }

    // One fuel rod in a fuel channel: its enrichment (U-235 fraction it started with) and what it's been through.
    // A MOX rod also starts with plutonium (plutonium0); a thorium rod is fertile thorium that breeds U-233 (kept in
    // plutonium: the fissile it has bred) in a neutron flux, as does a molten salt core's thorium.
    public static final class Rod {
        public double enrichment;
        public double burnup;          // 0 fresh .. 1 spent
        public double plutonium;       // fissile Pu made (fraction, like enrichment), or U-233 bred in thorium
        public double plutonium0;      // the plutonium it started with (MOX)
        public double fissionProducts; // 0 .. 1 (their absorption scales with it)
        public double power;           // its relative power now (for decay heat when it comes out)
        public boolean thorium;

        public Rod(double enrichment) {
            this.enrichment = enrichment;
        }

        public double fissile(ReactorParams p) {
            return Math.max(0.0, enrichment * (1.0 - p.u235Use * burnup) + plutonium);
        }

        public boolean isSpent() {
            return burnup >= 1.0;
        }

        // The full-power ticks it lasts: in proportion to what it started with that fissions (see ReactorParams.rodLife).
        public double life(ReactorParams p) {
            return p.rodLife(enrichment + plutonium0);
        }
    }

    // The trip points the player sets: each on or off and its value.
    public static final class Trips {
        public boolean highPower = true;
        public double highPowerPercent = 110.0;
        public boolean shortPeriod = true;
        public double shortPeriodSeconds = 10.0;
        public boolean highFuelTemperature = true;
        public double highFuelCelsius = 1_800.0;
        public boolean highPressure = true;
        public double highPressureMargin = 10.0;     // bar above the operating pressure
        public boolean lowFlow = true;
        public double lowFlowPercent = 50.0;
    }

    public final ReactorParams p;
    public final CoreLattice lattice;
    private final Rod[] rods;
    private final double[] iodine;
    private final int[] fuelCells;
    private final int[] controlCells;
    private final int[] blockCount = new int[1];
    public final Trips trips = new Trips();

    public CoreLattice.Coolant coolantType = CoreLattice.Coolant.LIGHT_WATER;
    public Mode mode = Mode.NONE;
    public final double[] insertion = new double[BANKS];
    public final double[] target = new double[BANKS];
    // Redstone pushes the rods in at least this far (0 none).
    public double redstoneFloor;
    public double pumpSetting = 1.0;

    // Kinetics: relative power (1 = every channel at channelHeat) and the delayed-neutron precursors.
    public double power;
    private double precursors;
    public final double[] decay = new double[3];
    public double fuelTemp = AMBIENT, cladTemp = AMBIENT, coolantTemp = AMBIENT;
    public double coolant;            // mB in the vessel
    public final double coolantCapacity;
    public double pressure = 1.0;
    public boolean scrammed;
    public Trip trip = Trip.NONE;
    // The fuel melted: the rods are ruined and it can't start again until they're all out (see the block entity).
    public boolean coreDamaged;
    // Failures, for the block entity to act on: the cladding oxidised by steam (0..1), the Hydrogen it gave off last
    // tick (mB), the share of the fission products let into the coolant (0..1), the fuel melted (into corium), the
    // vessel burst, a prompt-critical excursion.
    public double oxidised;
    public double hydrogenMade;
    public double coolantActivity;
    public boolean melted, ruptured, excursion;
    // Relief capacity (of full power, as steam) from the vessel's Relief Valves; nothing vents without one.
    public double reliefCapacity;

    // Results, for the GUI.
    public double k, rho, period = Double.POSITIVE_INFINITY;
    public double dopplerCoefficient, voidCoefficient, coolantCoefficient;  // Δρ per °C, per unit void, per °C
    public double peaking = 1.0;
    public double thermalPower, decayPower;       // HU/t
    public double hotLeg, steamMade, vented;      // mB last tick
    public double flowRatio = 1.0;
    public double coreVoid;
    public double meanXenon;

    private double rhoAtSolve, fuelAtSolve, coolantAtSolve, voidAtSolve;
    private int sinceSolve = Integer.MAX_VALUE / 2;
    private int sinceCoefficients = Integer.MAX_VALUE / 2;
    private boolean layoutChanged = true;
    private double lastLogPower = Double.NaN;
    private double boilVoid;

    public ReactorSim(ReactorParams params, CoreLattice lattice) {
        this.p = params;
        this.lattice = lattice;
        this.rods = new Rod[lattice.size];
        this.iodine = new double[lattice.size];
        int fuel = 0, control = 0;
        for (int i = 0; i < lattice.size; i++) {
            if (lattice.cell(i) == CoreLattice.Cell.FUEL) {
                fuel++;
            } else if (lattice.cell(i) == CoreLattice.Cell.CONTROL) {
                control++;
            }
        }
        fuelCells = new int[fuel];
        controlCells = new int[control];
        fuel = 0;
        control = 0;
        for (int i = 0; i < lattice.size; i++) {
            if (lattice.cell(i) == CoreLattice.Cell.FUEL) {
                fuelCells[fuel++] = i;
            } else if (lattice.cell(i) == CoreLattice.Cell.CONTROL) {
                controlCells[control++] = i;
            }
        }
        double capacity = 0.0;
        for (int i = 0; i < lattice.size; i++) {
            capacity += switch (lattice.cell(i)) {
                case COOLANT -> p.coolantPerBlock;
                case FUEL -> lattice.saltCore ? p.coolantPerBlock : p.coolantPerBlock * p.channelWater;
                case CONTROL -> p.coolantPerBlock * p.channelWater;
                default -> 0.0;
            };
        }
        coolantCapacity = capacity;
        blockCount[0] = lattice.size;
        Arrays.fill(insertion, 1.0);
        Arrays.fill(target, 1.0);
        reliefCapacity = p.reliefCapacity;
    }

    // --- Rods ---

    // The cells that are fuel channels (in the lattice's order).
    public int[] fuelCells() {
        return fuelCells;
    }

    public int[] controlCells() {
        return controlCells;
    }

    public Rod rod(int cell) {
        return rods[cell];
    }

    // Loads (or empties, rod null) a fuel channel. Its xenon and iodine start at nothing.
    public void setRod(int cell, Rod rod) {
        rods[cell] = rod;
        iodine[cell] = 0.0;
        lattice.setXenon(cell, 0.0);
        layoutChanged = true;
    }

    public int loadedRods() {
        int count = 0;
        for (int cell : fuelCells) {
            count += rods[cell] != null ? 1 : 0;
        }
        return count;
    }

    // --- Helpers ---

    // Saturation temperature (°C) at a pressure (bar), and back: from steam tables, interpolated.
    private static final double[][] SATURATION = { { 1.013, 100 }, { 4.76, 150 }, { 15.5, 200 }, { 39.7, 250 }, { 70.0, 286 }, { 85.9, 300 },
            { 112.9, 320 }, { 155.0, 345 }, { 186.5, 360 }, { 220.6, 374 } };

    public static double saturationCelsius(double bar) {
        if (bar <= SATURATION[0][0]) {
            return 100.0 * Math.max(0.0, bar / SATURATION[0][0]);
        }
        for (int i = 1; i < SATURATION.length; i++) {
            if (bar <= SATURATION[i][0]) {
                double f = (bar - SATURATION[i - 1][0]) / (SATURATION[i][0] - SATURATION[i - 1][0]);
                return SATURATION[i - 1][1] + f * (SATURATION[i][1] - SATURATION[i - 1][1]);
            }
        }
        return 374.0;
    }

    public static double saturationPressure(double celsius) {
        if (celsius <= 100.0) {
            return 1.013;
        }
        for (int i = 1; i < SATURATION.length; i++) {
            if (celsius <= SATURATION[i][1]) {
                double f = (celsius - SATURATION[i - 1][1]) / (SATURATION[i][1] - SATURATION[i - 1][1]);
                return SATURATION[i - 1][0] + f * (SATURATION[i][0] - SATURATION[i - 1][0]);
            }
        }
        // Past the critical point: rising steeply.
        return 220.6 + (celsius - 374.0) * 2.5;
    }

    public double operatingPressure() {
        if (!coolantType.isWater()) {
            return p.liquidPressure;
        }
        return switch (mode) {
            case PRESSURIZED -> p.pwrPressure;
            case BOILING -> p.bwrPressure;
            case NONE -> 1.013;
        };
    }

    public double reliefPressure() {
        if (!coolantType.isWater()) {
            return p.liquidPressure + 8.0;
        }
        return mode == Mode.BOILING ? p.bwrPressure + 20.0 : p.reliefPressure;
    }

    // Past this the vessel bursts.
    public double burstPressure() {
        return reliefPressure() * p.burstFactor;
    }

    // The boiling point of a liquid-metal or salt coolant (water boils by the steam tables).
    private double liquidBoil() {
        return coolantType == CoreLattice.Coolant.LEAD_BISMUTH ? p.leadBismuthBoil : p.saltBoil;
    }

    public double level() {
        return coolantCapacity > 0.0 ? Math.min(1.0, coolant / coolantCapacity) : 0.0;
    }

    private double waterDensity() {
        if (!coolantType.isWater()) {
            // Lead-bismuth and salt expand only a little as they heat.
            return Math.max(0.85, 1.0 - 0.0001 * (coolantTemp - AMBIENT));
        }
        return Math.max(0.6, 1.0 - 0.0006 * (Math.min(coolantTemp, 374.0) - AMBIENT));
    }

    // Full power (HU/t) of one fuel cell: a fuel channel's, or a share of it for a salt cell.
    public double cellHeat() {
        return p.channelHeat * (lattice.saltCore ? p.saltCellShare : 1.0);
    }

    // Cold coolant forced in (Emergency Core Cooling): mB at a temperature, mixing with what's there.
    public double inject(double amount, double celsius) {
        double room = Math.max(0.0, coolantCapacity - coolant);
        double added = Math.min(room, amount);
        if (added > 0.0) {
            double share = added / Math.max(1.0, coolantCapacity);
            coolantTemp += (celsius - coolantTemp) * Math.min(1.0, share * 1.5);
            coolant += added;
        }
        return added;
    }

    public double vesselCapacity() {
        return lattice.size * p.vesselCapacity;
    }

    public int fuelChannels() {
        return fuelCells.length;
    }

    // Full-power thermal output (HU/t) for this core: every loaded channel at channelHeat.
    public double ratedPower() {
        return Math.max(1, loadedRods()) * cellHeat();
    }

    private CoreLattice.Conditions conditions(double fuel, double water, double[] banks) {
        return new CoreLattice.Conditions(coolantType, water, fuel, banks);
    }

    // --- The tick ---

    // One tick. hotLegRoom and steamRoom: how much Hot Coolant and steam (mB) can leave this tick.
    public void tick(double hotLegRoom, double steamRoom) {
        moveRods();
        updateVoids();
        // Each second; every tick while the rods move (their worth isn't linear), every 5 while the power changes fast.
        boolean fast = Math.abs(period) < 20.0;
        if (layoutChanged || rodsMoving || sinceSolve >= (fast ? 5 : 20)) {
            solve();
        }
        sinceSolve++;
        if (sinceCoefficients >= 20) {
            coefficients();
        }
        sinceCoefficients++;
        rho = rhoAtSolve + dopplerCoefficient * (fuelTemp - fuelAtSolve) + coolantCoefficient * (coolantTemp - coolantAtSolve)
                + voidCoefficient * (coreVoid - voidAtSolve);
        kinetics();
        heat(hotLegRoom, steamRoom);
        xenonAndBurnup();
        protect();
    }

    private boolean rodsMoving;

    private void moveRods() {
        rodsMoving = false;
        for (int b = 0; b < BANKS; b++) {
            double want = scrammed ? 1.0 : Math.max(target[b], redstoneFloor);
            double step = scrammed ? 1.0 / Math.max(1.0, p.scramTicks) : p.rodSpeed / 20.0;
            double now = insertion[b];
            if (Math.abs(want - now) > 1.0E-9) {
                insertion[b] = want > now ? Math.min(want, now + step) : Math.max(want, now - step);
                rodsMoving = true;
            }
        }
    }

    private void updateVoids() {
        double level = level();
        for (int i = 0; i < lattice.size; i++) {
            double height = (lattice.y(i) + 0.5) / lattice.ny;
            double uncovered = height > level ? 1.0 : 0.0;
            double boiling = boilVoid * Math.min(1.0, 2.0 * height);
            lattice.setVoid(i, Math.max(uncovered, boiling));
        }
        double sum = 0.0;
        for (int i = 0; i < lattice.size; i++) {
            sum += lattice.voidAt(i);
        }
        coreVoid = lattice.size > 0 ? sum / lattice.size : 0.0;
    }

    private void pushFuel() {
        for (int cell : fuelCells) {
            Rod rod = rods[cell];
            if (rod == null) {
                lattice.set(cell, CoreLattice.Cell.COOLANT, -1);
                lattice.setFuel(cell, 0.0, 0.0);
            } else {
                lattice.set(cell, CoreLattice.Cell.FUEL, -1);
                lattice.setFuel(cell, rod.fissile(p), p.fissionProductWorth * rod.fissionProducts);
            }
        }
    }

    private void solve() {
        pushFuel();
        layoutChanged = false;
        sinceSolve = 0;
        k = lattice.solve(conditions(fuelTemp, waterDensity(), insertion), p);
        rhoAtSolve = k > 0.0 ? (k - 1.0) / k : -1.0;
        fuelAtSolve = fuelTemp;
        coolantAtSolve = coolantTemp;
        voidAtSolve = coreVoid;
        // The power shape: each loaded channel's share of the fission, normalised to an average of 1.
        double sum = 0.0;
        int count = 0;
        double max = 0.0;
        for (int cell : fuelCells) {
            if (rods[cell] != null) {
                sum += lattice.fission(cell);
                count++;
            }
        }
        double mean = count > 0 ? sum / count : 0.0;
        for (int cell : fuelCells) {
            if (rods[cell] != null && mean > 0.0) {
                max = Math.max(max, lattice.fission(cell) / mean);
            }
        }
        peaking = count > 0 && mean > 0.0 ? max : 1.0;
        meanFission = mean;
        double fluxSum = 0.0;
        for (int cell : fuelCells) {
            fluxSum += lattice.flux(cell);
        }
        meanFlux = fuelCells.length > 0 ? fluxSum / fuelCells.length : 0.0;
    }

    private double meanFission, meanFlux;
    private double steamWanted;

    // A channel's power share (1 = average).
    public double share(int cell) {
        return meanFission > 0.0 && rods[cell] != null ? lattice.fission(cell) / meanFission : 0.0;
    }

    // The feedback coefficients, from the core solved again with the fuel 100 °C hotter, the coolant 20 °C hotter, and
    // 10% more void everywhere. The lattice keeps the base solve's shape afterwards.
    private void coefficients() {
        sinceCoefficients = 0;
        if (fuelCells.length == 0 || loadedRods() == 0) {
            dopplerCoefficient = coolantCoefficient = voidCoefficient = 0.0;
            return;
        }
        pushFuel();
        double[] flux = new double[lattice.size];
        double[] fission = new double[lattice.size];
        System.arraycopy(lattice.flux, 0, flux, 0, lattice.size);
        System.arraycopy(lattice.fission, 0, fission, 0, lattice.size);
        double water = waterDensity();
        double base = reactivity(lattice.solve(conditions(fuelTemp, water, insertion), p));
        double hotFuel = reactivity(lattice.solve(conditions(fuelTemp + 100.0, water, insertion), p));
        double hotWater = reactivity(lattice.solve(conditions(fuelTemp, Math.max(0.6, water - 0.012), insertion), p));
        double[] voids = new double[lattice.size];
        for (int i = 0; i < lattice.size; i++) {
            voids[i] = lattice.voidAt(i);
            lattice.setVoid(i, voids[i] + 0.1);
        }
        double voided = reactivity(lattice.solve(conditions(fuelTemp, water, insertion), p));
        for (int i = 0; i < lattice.size; i++) {
            lattice.setVoid(i, voids[i]);
        }
        dopplerCoefficient = (hotFuel - base) / 100.0;
        coolantCoefficient = (hotWater - base) / 20.0;
        voidCoefficient = (voided - base) / 0.1;
        System.arraycopy(flux, 0, lattice.flux, 0, lattice.size);
        System.arraycopy(fission, 0, lattice.fission, 0, lattice.size);
    }

    // Power as a percentage of rated, for the GUI and Jade alike (no % sign): to 0.1% from 0.1% up, else to two figures
    // like 4.3e-03, so a climb from the source level shows.
    public static String percent(double power) {
        return power >= 0.001 ? String.format(java.util.Locale.ROOT, "%.1f", power * 100.0) : String.format(java.util.Locale.ROOT, "%.1e", power * 100.0);
    }

    private static double reactivity(double k) {
        return k > 0.0 ? (k - 1.0) / k : -1.0;
    }

    private void kinetics() {
        if (loadedRods() == 0) {
            power = 0.0;
            precursors = 0.0;
            period = Double.POSITIVE_INFINITY;
            return;
        }
        double before = Math.max(power, 1.0E-12);
        int steps = 10;
        double dt = 0.05 / steps;
        double beta = p.beta, lambda = p.precursorDecay, gen = p.generationTime;
        double source = p.sourceStrength;
        for (int s = 0; s < steps; s++) {
            double a = 1.0 - dt * (rho - beta) / gen - dt * dt * lambda * beta / (gen * (1.0 + lambda * dt));
            double b = power + dt * source + dt * lambda * precursors / (1.0 + lambda * dt);
            double next = a > 1.0E-6 ? b / a : power * 2.0;
            next = Math.max(0.0, Math.min(next, 1.0E3));
            precursors = (precursors + dt * beta / gen * next) / (1.0 + lambda * dt);
            power = next;
        }
        double log = Math.log(Math.max(power, 1.0E-12));
        double rate = (log - Math.log(before)) / 0.05;
        // The period meter: the rate of change of the log power, averaged over a few seconds (so a prompt jump after a
        // rod move doesn't read as a short period).
        double smoothed = Double.isNaN(lastLogPower) ? rate : 0.97 * lastLogPower + 0.03 * rate;
        lastLogPower = smoothed;
        period = Math.abs(smoothed) < 1.0E-4 ? Double.POSITIVE_INFINITY : 1.0 / smoothed;
    }

    private void heat(double hotLegRoom, double steamRoom) {
        int channels = Math.max(1, loadedRods());
        double fission = power * channels * cellHeat();
        decayPower = 0.0;
        for (int g = 0; g < decay.length; g++) {
            decay[g] += (p.decayShares[g] * p.decayFraction * fission - decay[g]) / p.decayTicks[g];
            decayPower += decay[g];
        }
        thermalPower = fission + decayPower;
        double cf = channels * p.fuelCapacity, cc = channels * p.cladCapacity, cw = vesselCapacity();
        double wet = 0.1 + 0.9 * level();
        double hfc = channels * p.fuelToClad, hcw = channels * p.cladToCoolant * wet;
        double toCoolant = 0.0;
        int steps = 4;
        for (int s = 0; s < steps; s++) {
            double q1 = hfc * (fuelTemp - cladTemp) / steps;
            double q2 = hcw * (cladTemp - coolantTemp) / steps;
            fuelTemp += (thermalPower / steps - q1) / cf;
            cladTemp += (q1 - q2) / cc;
            coolantTemp += q2 / cw;
            toCoolant += q2;
        }
        // Past melting the fuel slumps rather than heating on.
        fuelTemp = Math.min(fuelTemp, p.fuelMelt + 600.0);
        cladTemp = Math.min(cladTemp, p.fuelMelt + 600.0);
        oxidise(channels, cc);
        // Heat lost to the surroundings.
        coolantTemp -= p.ambientLoss * (coolantTemp - AMBIENT);
        hotLeg = steamMade = vented = 0.0;
        double demand = 0.0;
        if (!coolantType.isWater()) {
            // Lead-bismuth or salt: no boiling, the pump sends the hot liquid out of the hot leg. (Both freeze when cold,
            // lead-bismuth at 125 °C and the salt at about 460 °C: below its inlet temperature nothing flows out, so a
            // cold core sends nothing until it has warmed up.)
            double inlet = coolantType == CoreLattice.Coolant.LEAD_BISMUTH ? p.leadBismuthInletCelsius : p.saltInletCelsius;
            double hot = coolantType == CoreLattice.Coolant.LEAD_BISMUTH ? p.leadBismuthHotCelsius : p.saltHotCelsius;
            if (mode == Mode.PRESSURIZED) {
                double ramp = Math.max(0.0, Math.min(1.0, (coolantTemp - inlet) / (hot - inlet)));
                double capacity = channels * cellHeat() * 1.5 / p.hotCoolantHeat * pumpSetting;
                demand = capacity * ramp;
                double out = Math.min(Math.min(demand, hotLegRoom), coolant);
                hotLeg = out;
                coolant -= out;
                coolantTemp -= out * p.hotCoolantHeat / cw;
            }
            pressure = p.liquidPressure + Math.max(0.0, coolantTemp - liquidBoil()) * 0.5;
            relieve(cw);
            boilVoid *= 0.95;
        } else {
            switch (mode) {
                case PRESSURIZED -> {
                    double ramp = Math.max(0.0, Math.min(1.0, (coolantTemp - p.pwrInletCelsius) / (p.pwrHotCelsius - p.pwrInletCelsius)));
                    double capacity = channels * cellHeat() * 1.5 / p.hotCoolantHeat * pumpSetting;
                    demand = capacity * ramp;
                    double out = Math.min(Math.min(demand, hotLegRoom), coolant);
                    hotLeg = out;
                    coolant -= out;
                    coolantTemp -= out * p.hotCoolantHeat / cw;
                    boil(p.pwrPressure, 0.0);
                }
                case BOILING -> {
                    boil(p.bwrPressure, steamRoom);
                    demand = steamWanted;
                }
                case NONE -> boil(1.013, 0.0);
            }
        }
        // Flow: what left against what it wanted to send (1 when nothing needed to leave).
        double ratio = demand > 1.0E-3 ? Math.min(1.0, (hotLeg + steamMade) / demand) : 1.0;
        flowRatio = 0.9 * flowRatio + 0.1 * ratio;
        if (!coolantType.isWater()) {
            return;
        }
        double heatShare = thermalPower > 0 ? Math.min(1.0, toCoolant / Math.max(1.0, channels * cellHeat())) : 0.0;
        boilVoid = mode == Mode.BOILING ? p.voidPerBoil * Math.min(1.0, steamMade * p.boilingHeat / Math.max(1.0, channels * cellHeat())) : boilVoid * 0.95;
        if (mode != Mode.BOILING && coolantTemp > saturationCelsius(operatingPressure())) {
            boilVoid = Math.min(p.voidPerBoil, boilVoid + 0.02 * heatShare + 0.005);
        }
    }

    // Boiling at the operating pressure: heat above saturation turns water into steam, which leaves through the steam
    // room; what can't leave raises the pressure, and above the relief pressure the relief valve vents it.
    private void boil(double operating, double steamRoom) {
        double cw = vesselCapacity();
        double sat = saturationCelsius(operating);
        steamWanted = coolantTemp > sat ? (coolantTemp - sat) * cw / p.boilingHeat : 0.0;
        if (coolantTemp > sat && coolant > 0.0 && steamRoom > 0.0) {
            double steam = Math.min(Math.min((coolantTemp - sat) * cw / p.boilingHeat, steamRoom), coolant);
            steamMade = steam;
            coolant -= steam;
            coolantTemp -= steam * p.boilingHeat / cw;
        }
        pressure = Math.max(operating, saturationPressure(coolantTemp));
        if (mode == Mode.NONE) {
            pressure = Math.max(1.013, saturationPressure(coolantTemp));
        }
        relieve(cw);
    }

    // Above the relief pressure the Relief Valves vent steam (as much as their capacity), which brings the pressure
    // down; without valves nothing vents, and the pressure climbs on.
    private void relieve(double cw) {
        double relief = reliefPressure();
        if (pressure > relief && coolant > 0.0 && reliefCapacity > 0.0) {
            double operating = operatingPressure();
            double capacity = reliefCapacity * ratedPower() / p.boilingHeat;
            if (coolantType.isWater()) {
                double limit = saturationCelsius(relief);
                double steam = Math.min(Math.min((coolantTemp - limit) * cw / p.boilingHeat, coolant), capacity);
                if (steam > 0.0) {
                    vented = steam;
                    coolant -= steam;
                    coolantTemp -= steam * p.boilingHeat / cw;
                }
                pressure = Math.max(operating, saturationPressure(coolantTemp));
            } else {
                // Liquid-metal or salt vapour: vented (and lost) until the core is back under its boiling point.
                double vapour = Math.min(Math.min((coolantTemp - liquidBoil()) * cw / p.boilingHeat, coolant), capacity);
                if (vapour > 0.0) {
                    vented = vapour;
                    coolant -= vapour;
                    coolantTemp -= vapour * p.boilingHeat / cw;
                }
                pressure = p.liquidPressure + Math.max(0.0, coolantTemp - liquidBoil()) * 0.5;
            }
        }
    }

    // Zirconium cladding above cladFail reacts with the steam round it: it oxidises (faster the hotter, doubling every
    // zrDoubling °C), giving off Hydrogen and heat (which drives it on), and its failed cladding lets the fission
    // products into the coolant. A salt core has no cladding.
    private void oxidise(int channels, double cc) {
        hydrogenMade = 0.0;
        if (lattice.saltCore || loadedRods() == 0) {
            return;
        }
        double clad = hottestCladding();
        if (clad <= p.cladFail || oxidised >= 1.0 || !coolantType.isWater() && clad < p.fuelMelt) {
            return;
        }
        // Steam to react with: water in the vessel (or what boils off it); a dry core still has some.
        double steam = Math.max(0.2, level());
        double rate = Math.min(1.0 - oxidised, p.zrRate * Math.pow(2.0, (clad - p.cladFail) / p.zrDoubling) * (1.0 - oxidised) * steam);
        oxidised += rate;
        hydrogenMade = coolantType.isWater() ? rate * channels * p.zrHydrogen : 0.0;
        cladTemp += rate * channels * p.zrHeat / cc;
        double inventory = 0.2;
        int count = 0;
        double fp = 0.0;
        for (int cell : fuelCells) {
            if (rods[cell] != null) {
                fp += rods[cell].fissionProducts;
                count++;
            }
        }
        inventory += count > 0 ? fp / count : 0.0;
        coolantActivity = Math.max(coolantActivity, Math.min(1.0, oxidised * inventory));
    }

    private void xenonAndBurnup() {
        double dt = 0.05;
        double lambdaI = Math.log(2.0) / p.iodineHalfLife, lambdaX = Math.log(2.0) / p.xenonHalfLife;
        double sigma = p.xenonBurnout * lambdaX;
        double gain = (lambdaX + sigma) / (1.05 * lambdaI);
        double xenonSum = 0.0;
        int count = 0;
        // A salt core's cells share one Rod (the salt circulates through them all), so each takes its share.
        double each = lattice.saltCore ? 1.0 / Math.max(1, loadedRods()) : 1.0;
        for (int cell : fuelCells) {
            Rod rod = rods[cell];
            if (rod == null) {
                continue;
            }
            double q = power * share(cell);
            rod.power = q;
            double i = iodine[cell], x = lattice.xenon(cell);
            i += dt * (lambdaI * q - lambdaI * i);
            x += dt * (gain * lambdaI * i + 0.05 * gain * lambdaI * q - lambdaX * x - sigma * q * x);
            iodine[cell] = Math.max(0.0, i);
            lattice.setXenon(cell, Math.max(0.0, x));
            xenonSum += x;
            count++;
            if (lattice.saltCore) {
                // Xenon bubbles out of circulating salt.
                lattice.setXenon(cell, lattice.xenon(cell) * (1.0 - p.saltXenonRemoval));
            }
            if (q > 0.0 && !rod.isSpent()) {
                // Burnup follows the energy it has given (power, not time), and what builds up follows burnup.
                double db = q / rod.life(p) * each;
                rod.burnup = Math.min(1.0, rod.burnup + db);
                if (!rod.thorium) {
                    rod.plutonium = rod.plutonium0 * (1.0 - p.plutoniumUse * rod.burnup)
                            + p.plutoniumMade * Math.max(rod.enrichment, 0.0025) * rod.burnup * (1.0 - 0.4 * rod.burnup);
                }
                // A rod's fission products stay in it; a salt's build up until the Salt Processing Unit takes them out.
                rod.fissionProducts = lattice.saltCore ? Math.min(1.0, rod.fissionProducts + db) : rod.burnup;
            }
            // Thorium breeds U-233 from the flux it sits in (more in a fast core), up to a few percent.
            if (rod.thorium && power > 0.0) {
                double flux = meanFlux > 0.0 ? lattice.flux(cell) / meanFlux : 0.0;
                double bred = p.breeding * (coolantType == CoreLattice.Coolant.LEAD_BISMUTH ? p.fastBreeding : 1.0) * power * flux / p.rodLifeTicks * each;
                rod.plutonium = Math.min(0.08, rod.plutonium + bred * (1.0 - rod.plutonium / 0.08));
            }
        }
        meanXenon = count > 0 ? xenonSum / count : 0.0;
    }

    // --- Protection ---

    public double hottestFuel() {
        return coolantTemp + (fuelTemp - coolantTemp) * peaking;
    }

    public double hottestCladding() {
        return coolantTemp + (cladTemp - coolantTemp) * peaking;
    }

    private void protect() {
        if (p.solidFuel && hottestFuel() > p.fuelMelt && !coreDamaged) {
            coreDamaged = true;
            melted = true;
        }
        if (pressure > burstPressure()) {
            ruptured = true;
        }
        if (power > p.excursionPower) {
            excursion = true;
        }
        if (coreDamaged) {
            scram(Trip.CLADDING_FAILURE);
        }
        if (scrammed) {
            return;
        }
        if (hottestCladding() > p.cladFail) {
            scram(Trip.CLADDING_FAILURE);
        } else if (trips.highPower && power * 100.0 > trips.highPowerPercent) {
            scram(Trip.HIGH_POWER);
        } else if (trips.shortPeriod && period > 0.0 && period < trips.shortPeriodSeconds && power > 1.0E-4) {
            scram(Trip.SHORT_PERIOD);
        } else if (trips.highFuelTemperature && hottestFuel() > trips.highFuelCelsius) {
            scram(Trip.HIGH_FUEL_TEMPERATURE);
        } else if (trips.highPressure && pressure > operatingPressure() + trips.highPressureMargin) {
            scram(Trip.HIGH_PRESSURE);
        } else if (trips.lowFlow && power > 0.05 && (flowRatio * 100.0 < trips.lowFlowPercent || level() < 0.5)) {
            scram(Trip.LOW_COOLANT_FLOW);
        }
    }

    public void scram(Trip why) {
        if (!scrammed) {
            scrammed = true;
            trip = why;
            Arrays.fill(target, 1.0);
            sinceSolve = Integer.MAX_VALUE / 2;
        }
    }

    // Clears a trip, if nothing still calls for one. The rods stay in until moved.
    public boolean reset() {
        if (!scrammed) {
            return true;
        }
        scrammed = false;
        Trip was = trip;
        trip = Trip.NONE;
        protect();
        if (scrammed) {
            return false;
        }
        trip = Trip.NONE;
        return was != Trip.NONE;
    }

    // The energy (HU) a burst vessel lets go: its coolant's heat above boiling, its pressure, and in an excursion the
    // fission energy of the spike. What sizes the explosion (see the block entity).
    public double storedEnergy() {
        double heat = Math.max(0.0, coolantTemp - 100.0) * vesselCapacity();
        double squeeze = Math.max(0.0, pressure - 1.0) * lattice.size * 400.0;
        double spike = excursion ? power * ratedPower() * 400.0 : 0.0;
        return heat + squeeze + spike;
    }

    // With every fuel rod out of a damaged core it can be started again: its damage, oxidation and failures are
    // cleared (the coolant stays as radioactive until it's drained).
    public void clearDamage() {
        coreDamaged = false;
        melted = false;
        ruptured = false;
        excursion = false;
        oxidised = 0.0;
        if (trip == Trip.CLADDING_FAILURE) {
            trip = Trip.MANUAL;
        }
    }

    // Shut down: every rod in and the power down to decay heat.
    public boolean isShutDown() {
        for (double in : insertion) {
            if (in < 0.999) {
                return false;
            }
        }
        return power < 0.01;
    }

    // --- Saving (the owner writes these out) ---

    public double precursors() {
        return precursors;
    }

    public double iodine(int cell) {
        return iodine[cell];
    }

    public void restore(double power, double precursors, double fuel, double clad, double water, double coolant, double[] decay) {
        this.power = power;
        this.precursors = precursors;
        this.fuelTemp = fuel;
        this.cladTemp = clad;
        this.coolantTemp = water;
        this.coolant = Math.min(coolant, coolantCapacity);
        System.arraycopy(decay, 0, this.decay, 0, Math.min(decay.length, this.decay.length));
        layoutChanged = true;
    }

    public void restoreCell(int cell, double iodine, double xenon) {
        this.iodine[cell] = iodine;
        lattice.setXenon(cell, xenon);
    }

    // Steady precursors for a power (so a loaded reactor doesn't jump).
    public void settlePrecursors() {
        precursors = p.beta / (p.generationTime * p.precursorDecay) * power;
    }
}
