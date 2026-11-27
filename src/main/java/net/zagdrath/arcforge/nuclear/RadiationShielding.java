/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// How well a block shields radiation (the arcforge:radiation_shielding data map on blocks): the fraction of the dose
// that gets through one block of it (lead 0.1, depleted uranium 0.05, concrete 0.3, water 0.5, iron and steel 0.4,
// stone and deepslate 0.6). A solid block without an entry lets through radiation.defaultShielding (0.8), a block
// holding water the water's, anything else all of it.
// neutron_transmission (optional): what it lets through of a fusion plasma's fast neutrons, which light nuclei slow and
// stop best (water 0.3, concrete 0.35) and heavy metals hardly at all (lead 0.85); without it, the gamma figure.
public record RadiationShielding(float transmission, Optional<Float> neutronTransmission) {
    public static final Codec<RadiationShielding> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.0F, 1.0F).fieldOf("transmission").forGetter(RadiationShielding::transmission),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("neutron_transmission").forGetter(RadiationShielding::neutronTransmission))
            .apply(i, RadiationShielding::new));

    public RadiationShielding(float transmission) {
        this(transmission, Optional.empty());
    }

    public float transmission(boolean neutron) {
        return neutron ? neutronTransmission.orElse(transmission) : transmission;
    }
}
