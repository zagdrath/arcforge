/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.settings;

/**
 * The ids of the side and port modes Arcforge uses. Side and port modes are passed as these strings, not an enum, so
 * that Arcforge can add modes without a major API change. Each machine offers only some of them; see
 * {@link MachineSettings#allowedSideModes()} and {@link MachineSettings#allowedPortModes()}.
 */
public final class SideModes {
    /** No connection. */
    public static final String NONE = "none";
    /** Items and fluids go in. */
    public static final String INPUT = "input";
    /** Products come out. */
    public static final String OUTPUT = "output";
    /** Energy goes in (consumers) or comes out (generators). */
    public static final String ENERGY = "energy";
    /** Only the by-product comes out. */
    public static final String BYPRODUCT = "byproduct";
    /** Heat goes in (consumers) or comes out (producers). */
    public static final String HEAT = "heat";
    /** Steam goes in (the Distillation Array). */
    public static final String STEAM = "steam";
    /** Naphtha comes out. */
    public static final String NAPHTHA = "naphtha";
    /** Light Oil comes out. */
    public static final String LIGHT_OIL = "light_oil";
    /** Heavy Oil comes out. */
    public static final String HEAVY_OIL = "heavy_oil";
    /** Pitch comes out. */
    public static final String PITCH = "pitch";
    /** Lubricant goes in (turbines). */
    public static final String LUBRICANT = "lubricant";
    /** Exhaust Steam comes out. */
    public static final String EXHAUST = "exhaust";
    /** Oxygen goes in or out, depending on the machine. */
    public static final String OXYGEN = "oxygen";
    /** Hydrogen comes out. */
    public static final String HYDROGEN = "hydrogen";
    /** The machine's own gas product comes out. */
    public static final String GAS_OUTPUT = "gas_output";
    /** Brine comes out (ports only). */
    public static final String BRINE = "brine";
    /** Salt comes out (ports only). */
    public static final String SALT = "salt";
    /** Returned water comes out (ports only). */
    public static final String WATER = "water";
    /** Lithium Brine comes out (ports only). */
    public static final String LITHIUM_BRINE = "lithium_brine";
    /** Energy goes in (Battery Array ports only). */
    public static final String ENERGY_INPUT = "energy_input";
    /** Energy comes out (Battery Array ports only). */
    public static final String ENERGY_OUTPUT = "energy_output";
    /** Sulfur Dust comes out (ports only). */
    public static final String SULFUR = "sulfur";

    private SideModes() {}
}
