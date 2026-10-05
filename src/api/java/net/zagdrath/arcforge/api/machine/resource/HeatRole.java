/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.resource;

/** How a machine relates to heat (HU). */
public enum HeatRole {
    /** Takes heat in and uses it to work. */
    CONSUMER,
    /** Produces heat for others. */
    PRODUCER
}
