/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.heat;

// Arcforge heat capability, in heat units (HU). Exposed through ModCapabilities.HEAT.
public interface HeatHandler {
    int getHeat();

    int getMaxHeat();

    // Returns how much heat was (or would be) accepted.
    int receiveHeat(int amount, boolean simulate);

    // Returns how much heat was (or would be) removed.
    int extractHeat(int amount, boolean simulate);
}
