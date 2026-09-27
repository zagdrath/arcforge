/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.zagdrath.arcforge.registry.ModRecipes;

// One Carbonizer chamber cycle: one input item baked into a result, with an optional fluid by-product
// (coal -> coal coke + creosote). Each chamber runs one of these at a time.
public record CarbonizingRecipe(Ingredient ingredient, ItemStackTemplate result, Optional<FluidStackTemplate> byproduct, int time)
        implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 600;

    public static final MapCodec<CarbonizingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(CarbonizingRecipe::ingredient),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(CarbonizingRecipe::result),
            FluidStackTemplate.CODEC.optionalFieldOf("byproduct").forGetter(CarbonizingRecipe::byproduct),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(CarbonizingRecipe::time))
            .apply(i, CarbonizingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CarbonizingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, CarbonizingRecipe::ingredient,
            ItemStackTemplate.STREAM_CODEC, CarbonizingRecipe::result,
            ByteBufCodecs.optional(FluidStackTemplate.STREAM_CODEC), CarbonizingRecipe::byproduct,
            ByteBufCodecs.VAR_INT, CarbonizingRecipe::time,
            CarbonizingRecipe::new);

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return result.create();
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
    public RecipeSerializer<CarbonizingRecipe> getSerializer() {
        return ModRecipes.CARBONIZING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CarbonizingRecipe> getType() {
        return ModRecipes.CARBONIZING.get();
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
