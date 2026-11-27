/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.zagdrath.arcforge.registry.ModRecipes;

// The Tritium Extraction Unit's (arcforge:tritium_extracting): an irradiated Lithium Target Rod (ingredient) baked at
// min_temp °C, heat_per_tick HU/t, for time ticks; its tritium (tritium mB for a fully bred rod, less for one bred
// less) and the helium bred with it (helium mB, likewise) come off as gases, and the emptied cladding (result) is left.
public record TritiumExtractingRecipe(Ingredient ingredient, ItemStackTemplate result, int tritium, int helium, int time, int heatPerTick, int minTemp)
        implements Recipe<SingleRecipeInput> {
    public static final MapCodec<TritiumExtractingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(TritiumExtractingRecipe::ingredient),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(TritiumExtractingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("tritium", 1_000).forGetter(TritiumExtractingRecipe::tritium),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("helium", 250).forGetter(TritiumExtractingRecipe::helium),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", 400).forGetter(TritiumExtractingRecipe::time),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("heat_per_tick", 400).forGetter(TritiumExtractingRecipe::heatPerTick),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("min_temp", 600).forGetter(TritiumExtractingRecipe::minTemp))
            .apply(i, TritiumExtractingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TritiumExtractingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, TritiumExtractingRecipe::ingredient,
            ItemStackTemplate.STREAM_CODEC, TritiumExtractingRecipe::result,
            ByteBufCodecs.VAR_INT, TritiumExtractingRecipe::tritium,
            ByteBufCodecs.VAR_INT, TritiumExtractingRecipe::helium,
            ByteBufCodecs.VAR_INT, TritiumExtractingRecipe::time,
            ByteBufCodecs.VAR_INT, TritiumExtractingRecipe::heatPerTick,
            ByteBufCodecs.VAR_INT, TritiumExtractingRecipe::minTemp,
            TritiumExtractingRecipe::new);

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return result.create();
    }

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
    public RecipeSerializer<TritiumExtractingRecipe> getSerializer() {
        return ModRecipes.TRITIUM_EXTRACTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<TritiumExtractingRecipe> getType() {
        return ModRecipes.TRITIUM_EXTRACTING.get();
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
