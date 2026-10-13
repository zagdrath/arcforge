/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModDataMaps;

// What a fuel is worth in the Diesel Generator (data map arcforge:engine_fuels, keyed by fluid): the FE each mB makes
// before power.generationMultiplier. Fluids not in the map aren't engine fuel. Diesel, Gasoline, Biodiesel and Refinery
// Gas by default.
public record EngineFuel(int fePerMb) {
    public static final Codec<EngineFuel> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("fe_per_mb").forGetter(EngineFuel::fePerMb))
            .apply(i, EngineFuel::new));

    public static @Nullable EngineFuel of(Fluid fluid) {
        return BuiltInRegistries.FLUID.wrapAsHolder(fluid).getData(ModDataMaps.ENGINE_FUELS);
    }

    public static @Nullable EngineFuel of(FluidResource resource) {
        return resource.isEmpty() ? null : of(resource.getFluid());
    }
}
