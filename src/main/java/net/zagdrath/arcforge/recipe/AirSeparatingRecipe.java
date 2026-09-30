/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModRecipes;

// Air Separator (arcforge:air_separating): the air where it stands, separated into a primary gas (its Nitrogen tank)
// and an optional secondary one (its Oxygen tank) every `time` ticks at energy_per_tick FE/t (airSeparator.energyPerTick
// if unset). "dimensions" limits a recipe to those dimensions; "excluded_dimensions" keeps it out of them (the End has
// no air to separate). The first recipe that fits the dimension is used, so a pack can give the Nether its own air.
public record AirSeparatingRecipe(FluidStackTemplate primary, Optional<FluidStackTemplate> secondary, int time, Optional<Integer> energyPerTick,
        List<ResourceKey<Level>> dimensions, List<ResourceKey<Level>> excludedDimensions) implements Recipe<AirSeparatingRecipe.Input> {
    public static final int DEFAULT_TIME = 40;

    public static final MapCodec<AirSeparatingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FluidStackTemplate.CODEC.fieldOf("primary").forGetter(AirSeparatingRecipe::primary),
            FluidStackTemplate.CODEC.optionalFieldOf("secondary").forGetter(AirSeparatingRecipe::secondary),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(AirSeparatingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy_per_tick").forGetter(AirSeparatingRecipe::energyPerTick),
            ResourceKey.codec(Registries.DIMENSION).listOf().optionalFieldOf("dimensions", List.of()).forGetter(AirSeparatingRecipe::dimensions),
            ResourceKey.codec(Registries.DIMENSION).listOf().optionalFieldOf("excluded_dimensions", List.of()).forGetter(AirSeparatingRecipe::excludedDimensions))
            .apply(i, AirSeparatingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AirSeparatingRecipe> STREAM_CODEC = StreamCodec.composite(
            FluidStackTemplate.STREAM_CODEC, AirSeparatingRecipe::primary,
            ByteBufCodecs.optional(FluidStackTemplate.STREAM_CODEC), AirSeparatingRecipe::secondary,
            ByteBufCodecs.VAR_INT, AirSeparatingRecipe::time,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), AirSeparatingRecipe::energyPerTick,
            ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs.list()), AirSeparatingRecipe::dimensions,
            ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs.list()), AirSeparatingRecipe::excludedDimensions,
            AirSeparatingRecipe::new);

    // Where the Air Separator stands.
    public record Input(ResourceKey<Level> dimension) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    // Whether this recipe separates the air of that dimension.
    public boolean worksIn(ResourceKey<Level> dimension) {
        return (dimensions.isEmpty() || dimensions.contains(dimension)) && !excludedDimensions.contains(dimension);
    }

    // FE/t before upgrades.
    public int baseEnergyPerTick() {
        return energyPerTick.orElseGet(ArcforgeConfig.AIR_SEPARATOR_ENERGY_PER_TICK::getAsInt);
    }

    @Override
    public boolean matches(Input input, Level level) {
        return worksIn(input.dimension());
    }

    // The outputs are gases; the Air Separator fills its tanks itself.
    @Override
    public ItemStack assemble(Input input) {
        return ItemStack.EMPTY;
    }

    // Machine recipes stay out of the recipe book.
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<AirSeparatingRecipe> getSerializer() {
        return ModRecipes.AIR_SEPARATING_SERIALIZER.get();
    }

    @Override
    public RecipeType<AirSeparatingRecipe> getType() {
        return ModRecipes.AIR_SEPARATING.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipes.MACHINE_CATEGORY.get();
    }
}
