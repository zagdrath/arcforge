/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.Locale;

// The Diesel Generator's engine, set by how long it's built (see OilfieldStructures.GENERATOR_LENGTHS): a V8 5 blocks
// long, a V12 7 long, a V16 9 long. Its cylinders (half on each bank) and turbochargers shape the model (see
// DieselGeneratorLayout); its output and efficiency are in config (see DieselGeneratorBlockEntity).
public enum DieselEngine {
    V8(5, 8, 2),
    V12(7, 12, 2),
    V16(9, 16, 4);

    public final int length, cylinders, turbos;

    DieselEngine(int length, int cylinders, int turbos) {
        this.length = length;
        this.cylinders = cylinders;
        this.turbos = turbos;
    }

    // The engine of a generator this long (the V8 for anything else).
    public static DieselEngine of(int length) {
        for (DieselEngine engine : values()) {
            if (engine.length == length) {
                return engine;
            }
        }
        return V8;
    }

    // Cylinders on each bank.
    public int perBank() {
        return cylinders / 2;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
