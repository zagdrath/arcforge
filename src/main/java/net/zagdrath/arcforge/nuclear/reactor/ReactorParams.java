/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear.reactor;

// Every number the reactor model uses. The lattice constants are game-scaled (a block is a lattice cell, so neutrons
// travel a few blocks) and tuned so that real designs behave as they should: a light-water core needs enriched fuel
// and has a negative void coefficient; a graphite-moderated, water-cooled core runs on less and has a positive one;
// heavy water and graphite let natural uranium go critical. The rest (rates, limits, times) come from the config (see
// ReactorConfig.params) and default to these.
public final class ReactorParams {
    // --- Lattice (per block) ---
    public double nu = 2.43;
    public double fissionCross = 6.0;
    public double captureToFission = 0.17;
    public double fastFission = 0.06;
    public double u238Capture = 0.045;
    public double captureBase = 0.30;
    public double resonance = 1.6;
    public double doppler = 0.015;
    public double moderationHalf = 0.5;
    public double channelWater = 0.7;
    public double channelModeration = 0.12;
    public double graphiteModeration = 0.75;
    public double heavyWaterModeration = 0.85;
    public double lightWaterAbsorption = 0.022;
    public double heavyWaterAbsorption = 0.0004;
    public double graphiteAbsorption = 0.0009;
    public double steelAbsorption = 0.030;
    public double controlAbsorption = 2.0;
    public double rodFluxRatio = 0.15;          // flux in a rodded cell against an unrodded one (partial cells)
    public double diffusion = 0.12;
    public double graphiteDiffusion = 0.18;
    public double steelDiffusion = 0.08;
    public double wallLeakage = 0.4;
    public double voidStreaming = 1.0;
    public double xenonWorth = 0.001;
    public double fissionProductWorth = 0.003;
    // A lead-bismuth (fast) core and a molten salt core.
    public double leadBismuthModeration = 0.02;
    public double leadBismuthAbsorption = 0.002;
    public double fastDiffusion = 1.5;          // fast neutrons travel this much further
    public double fastResonance = 0.4;         // of U-238's resonance capture left in a fast spectrum
    public double fastCoreFission = 0.089;       // a fissile atom's fast fission, against thermal, in a fast core
    public double saltModeration = 0.10;
    public double saltAbsorption = 0.004;
    public double saltFertile = 0.35;           // the salt's fertile (thorium) capture, against a fuel rod's U-238
    public double saltCellShare = 0.5;          // a salt cell's full power, against a fuel channel's
    public double saltXenonRemoval = 0.01;      // of the salt's xenon bubbled off a tick (off-gas)
    public int outerIterations = 80;
    public int innerIterations = 4;
    public double tolerance = 2.0E-6;

    // --- Kinetics ---
    public double beta = 0.010;
    public double precursorDecay = 0.05;        // λ, per second
    public double generationTime = 0.02;        // Λ, seconds (game-scaled: prompt critical climbs in seconds)
    public double sourceStrength = 2.0E-7;      // the start-up source, as power fraction per second

    // --- Power and heat (HU) ---
    public double channelHeat = 4_000.0;        // HU/t from a fuel channel at full power (relative power 1)
    public double fuelCapacity = 600.0;         // HU/°C per fuel channel
    public double fuelToClad = 7.0;             // HU/t per °C per channel
    public double cladCapacity = 150.0;
    public double cladToCoolant = 140.0;
    public double vesselCapacity = 2_000.0;     // HU/°C per block inside the vessel (water and steel)
    public double ambientLoss = 0.0002;         // of the coolant's heat above 20 °C, per tick
    public double hotCoolantHeat = 60.0;        // HU a mB of Hot Coolant carries to the Steam Generator
    public double pwrInletCelsius = 100.0;      // below this the hot leg sends nothing; full flow by pwrHotCelsius
    public double pwrHotCelsius = 300.0;
    public double boilingHeat = 12.0;           // HU per mB of steam a boiling core makes
    public double pwrPressure = 155.0;          // bar, held by the pressurizer
    public double bwrPressure = 70.0;
    public double reliefPressure = 175.0;       // the relief valve vents above this
    public double reliefCapacity = 1.2;         // of full power the relief valve can vent (as steam)
    public double coolantPerBlock = 2_000.0;    // mB of coolant an empty cell holds (a channel holds channelWater of it)
    public double voidPerBoil = 0.55;           // core void at full boiling (rises up the core)
    // Liquid-metal and salt cores: no boiling, near atmospheric pressure, hot.
    public double leadBismuthInletCelsius = 450.0, leadBismuthHotCelsius = 900.0;
    public double saltInletCelsius = 560.0, saltHotCelsius = 700.0;
    public double liquidPressure = 2.0;         // bar: a lead-bismuth or salt core's pump head
    public double leadBismuthBoil = 1_670.0;    // °C
    public double saltBoil = 1_430.0;

    // --- Xenon (half-lives in seconds, scaled) ---
    public double iodineHalfLife = 800.0;
    public double xenonHalfLife = 1_100.0;
    public double xenonBurnout = 4.0;           // burn-out at full flux, as a multiple of xenon's decay

    // --- Decay heat ---
    public double decayFraction = 0.065;
    public double[] decayShares = { 0.45, 0.35, 0.20 };
    public double[] decayTicks = { 1_200.0, 12_000.0, 72_000.0 };

    // --- Burnup ---
    public double rodLifeTicks = 1_152_000.0;   // full-power ticks a rod lasts (16 real hours, 48 in-game days)
    public double u235Use = 0.45;               // of its U-235 used when spent
    public double plutoniumMade = 0.40;         // fissile plutonium made, against the U-235 it started with
    public double plutoniumUse = 0.40;          // of a MOX rod's plutonium used when spent
    public double breeding = 0.06;              // fissile bred in a blanket rod at full flux over a rod's life
    public double fastBreeding = 2.5;           // breeding's multiple in a fast core (more neutrons spare)

    // --- Rods ---
    public double rodSpeed = 0.02;              // insertion a second
    public double scramTicks = 40.0;            // all the way in

    // --- Limits and failures ---
    public double fuelMelt = 2_800.0;
    public double cladFail = 1_200.0;
    // Zirconium and steam: the cladding's oxidation per tick at cladFail (doubling every zrDoubling °C above it), the
    // Hydrogen (mB) and heat (HU) a channel's cladding gives when it's all oxidised.
    public double zrRate = 0.0004;
    public double zrDoubling = 120.0;
    public double zrHydrogen = 6_000.0;
    public double zrHeat = 400_000.0;
    // The vessel bursts at its relief pressure times this; a core above excursionPower (relative) blows apart.
    public double burstFactor = 1.3;
    public double excursionPower = 8.0;
    // Relief capacity (of full power, as steam) a Relief Valve gives.
    public double reliefPerValve = 0.4;
    // Whether the fuel is solid (it can melt into corium); a molten salt core's isn't.
    public boolean solidFuel = true;

    public static ReactorParams defaults() {
        return new ReactorParams();
    }
}
