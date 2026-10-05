/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.gas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;

/**
 * A gas's entry in the {@link GasRegistry#PROPERTIES} data map ({@code data/<namespace>/data_maps/fluid/gas_properties.json}),
 * keyed by fluid. Arcforge ships one for each of its gases; a datapack or another mod can add one for its own gas (a
 * fluid tagged {@code #arcforge:gases} or lighter than air) or override Arcforge's.
 *
 * <pre>{@code
 * { "values": { "arcforge:hydrogen": { "color": "#EAF2FA" } } }
 * }</pre>
 *
 * @param color the colour the gas is drawn in, {@code 0xAARRGGBB}; written in JSON as {@code "#RRGGBB"} (opaque) or
 *              as an integer
 */
public record GasProperties(int color) {
    /** The data map's codec. */
    public static final Codec<GasProperties> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.STRING_RGB_COLOR.fieldOf("color").forGetter(GasProperties::color))
            .apply(i, GasProperties::new));
}
