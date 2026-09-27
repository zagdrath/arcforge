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

    // The heat_bar sprite's colours at nine evenly spaced points from MIN_CELSIUS to MAX_CELSIUS.
    private static final int[] COLORS = {
            0xFF3C699A, 0xFF465670, 0xFF5B363B, 0xFF9D2A10, 0xFFCF3E15, 0xFFF15E1F, 0xFFFFA033, 0xFFFFCF61, 0xFFFFF0BE };

    private HeatScale() {}

    // Width of the heat bar fill for a track of the given width.
    public static int fillWidth(int trackWidth, int celsius) {
        return Mth.clamp(Math.round((float) trackWidth * (celsius - MIN_CELSIUS) / (MAX_CELSIUS - MIN_CELSIUS)), 0, trackWidth);
    }

    // The scale's colour at a temperature (for item fill bars, which have no sprite). Plain maths, so
    // common code may call it.
    public static int color(int celsius) {
        float position = Mth.clamp((float) (celsius - MIN_CELSIUS) / (MAX_CELSIUS - MIN_CELSIUS), 0.0F, 1.0F) * (COLORS.length - 1);
        int index = Math.min(COLORS.length - 2, (int) position);
        float t = position - index;
        int from = COLORS[index], to = COLORS[index + 1];
        return 0xFF000000
                | (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF) << 16
                | (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF) << 8
                | (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
    }
}
