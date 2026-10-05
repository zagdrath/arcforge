/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.resource;

/** How a machine relates to energy (FE). */
public enum EnergyRole {
    /** Uses energy to work. */
    CONSUMER,
    /** Produces energy. */
    GENERATOR,
    /** Stores energy for others (the Battery Array). */
    STORAGE
}
