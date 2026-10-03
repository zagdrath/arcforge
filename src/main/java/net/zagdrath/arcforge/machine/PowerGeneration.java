/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import net.zagdrath.arcforge.config.ArcforgeConfig;

// The global power multiplier (power.generationMultiplier): every generator multiplies the FE it makes by it, where it
// makes it (the Combustion Plant, Thermoelectric Plant, Steam Turbine Array and Gas Turbine Array; a new generator
// should make its FE through fe() too). Heat and steam are never multiplied, so a chain of heat -> steam -> FE is only
// multiplied once, at the end. Output caps and FE buffers sized from a generator's own output grow with it through cap().
public final class PowerGeneration {
    private PowerGeneration() {}

    public static double multiplier() {
        return ArcforgeConfig.POWER_GENERATION_MULTIPLIER.getAsDouble();
    }

    // FE made from this much unmultiplied FE, rounded.
    public static int fe(double raw) {
        return clamp(Math.round(Math.max(0.0, raw) * multiplier()));
    }

    // A cap or buffer in FE sized for unmultiplied output, grown with the multiplier (rounded up, at least 1).
    public static int cap(double raw) {
        return Math.max(1, clamp((long) Math.ceil(raw * multiplier() - 1.0E-6)));
    }

    private static int clamp(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }
}
