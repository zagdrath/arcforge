/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.farming;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.registry.ModDataMaps;

// A soil for the Glass Cloche and Grow Chamber (the arcforge:cloche_soils data map, keyed by item, in
// data/<namespace>/data_maps/item/cloche_soils.json): the block drawn inside the farm under the crop (with fixed
// properties, e.g. moist farmland for dirt) and how fast crops grow in it (Loam: 1.25). Only items in the map go in
// the soil slot; which crops grow in which soil is up to each arcforge:cloche recipe.
public record ClocheSoil(Block block, Map<String, String> properties, float growth) {
    public static final Codec<ClocheSoil> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(ClocheSoil::block),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("properties", Map.of()).forGetter(ClocheSoil::properties),
            ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("growth", 1.0F).forGetter(ClocheSoil::growth))
            .apply(i, ClocheSoil::new));

    public static @Nullable ClocheSoil of(ItemStack stack) {
        return stack.isEmpty() ? null : BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getData(ModDataMaps.CLOCHE_SOILS);
    }

    // The block drawn as the soil.
    public BlockState renderState() {
        return ClochePlants.withProperties(block.defaultBlockState(), properties);
    }
}
