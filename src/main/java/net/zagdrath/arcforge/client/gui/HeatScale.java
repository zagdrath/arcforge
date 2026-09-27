/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui;

import net.minecraft.util.Mth;

// One heat bar colour scale for every machine, keyed to absolute temperature, so the same colour
// always means the same heat. The heat_bar sprites are pre-rendered along this scale: 20°C at the
// left edge up to 1,600°C at the right, linear.
public final class HeatScale {
    public static final int MIN_CELSIUS = 20;
    public static final int MAX_CELSIUS = 1_600;

    private HeatScale() {}

    // Width of the heat bar fill for a track of the given width.
    public static int fillWidth(int trackWidth, int celsius) {
        return Mth.clamp(Math.round((float) trackWidth * (celsius - MIN_CELSIUS) / (MAX_CELSIUS - MIN_CELSIUS)), 0, trackWidth);
    }
}
