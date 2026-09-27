/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.heat;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModDataMaps;

// What a liquid fuel is worth in the Fuel Burner: the heat each mB gives, and how fast it burns. Set per
// fluid in the arcforge:burner_fuels data map (data/<namespace>/data_maps/fluid/burner_fuels.json), so
// fuels can be added without code. Fluids not in the map aren't fuel.
public record BurnerFuel(int huPerMb, float mbPerTick) {
    public static final Codec<BurnerFuel> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("hu_per_mb").forGetter(BurnerFuel::huPerMb),
            ExtraCodecs.POSITIVE_FLOAT.fieldOf("mb_per_tick").forGetter(BurnerFuel::mbPerTick))
            .apply(i, BurnerFuel::new));

    public static @Nullable BurnerFuel of(Fluid fluid) {
        return BuiltInRegistries.FLUID.wrapAsHolder(fluid).getData(ModDataMaps.BURNER_FUELS);
    }

    public static @Nullable BurnerFuel of(FluidResource resource) {
        return resource.isEmpty() ? null : of(resource.getFluid());
    }
}
