/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.settings;

/**
 * How a machine responds to a redstone signal. Each machine offers only some of these; see
 * {@link MachineSettings#allowedRedstoneModes()}.
 */
public enum RedstoneMode {
    /** Runs whatever the signal. */
    IGNORE,
    /** Runs only while powered. */
    HIGH,
    /** Runs only while unpowered. */
    LOW,
    /** Does one operation for each rising edge of the signal. */
    PULSE,
    /** Runs at a rate set by the signal strength (the Gas Turbine Array's throttle). */
    THROTTLE
}
