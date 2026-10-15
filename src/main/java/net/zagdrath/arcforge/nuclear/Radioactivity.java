/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;

// How radioactive something is (the arcforge:radioactivity data maps on items, blocks and fluids): the dose rate, in
// mSv/h, one item, one block or one bucket (1,000 mB) of it gives at a distance of one block, unshielded. See
// RadiationField for how sources add up at a player.
public record Radioactivity(float doseRate) {
    public static final Codec<Radioactivity> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.POSITIVE_FLOAT.fieldOf("dose_rate").forGetter(Radioactivity::doseRate))
            .apply(i, Radioactivity::new));
}
