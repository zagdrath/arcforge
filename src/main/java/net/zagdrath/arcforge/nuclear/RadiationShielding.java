/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// How well a block shields radiation (the arcforge:radiation_shielding data map on blocks): the fraction of the dose
// that gets through one block of it (lead 0.1, depleted uranium 0.05, concrete 0.3, water 0.5, iron and steel 0.4,
// stone and deepslate 0.6). A solid block without an entry lets through radiation.defaultShielding (0.8), a block
// holding water the water's, anything else all of it.
public record RadiationShielding(float transmission) {
    public static final Codec<RadiationShielding> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.0F, 1.0F).fieldOf("transmission").forGetter(RadiationShielding::transmission))
            .apply(i, RadiationShielding::new));
}
