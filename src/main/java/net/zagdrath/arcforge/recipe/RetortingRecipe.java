/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import com.mojang.serialization.Codec;
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

// Retorting, in the Induction Furnace only: an amalgam heated until its Mercury boils off, leaving the metal (as dust)
// and giving the Mercury back into the furnace's by-product tank. The furnace won't start one unless the fluid fits.
// JSON: ingredient, result, fluid_output, optional time (ticks before the furnace's speed-up, 200) and experience.
public record RetortingRecipe(Ingredient ingredient, ItemStackTemplate result, FluidStackTemplate fluidOutput, int time, float experience)
        implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 200;

    public static final MapCodec<RetortingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(RetortingRecipe::ingredient),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(RetortingRecipe::result),
            FluidStackTemplate.CODEC.fieldOf("fluid_output").forGetter(RetortingRecipe::fluidOutput),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(RetortingRecipe::time),
            Codec.FLOAT.optionalFieldOf("experience", 0.2F).forGetter(RetortingRecipe::experience))
            .apply(i, RetortingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RetortingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, RetortingRecipe::ingredient,
            ItemStackTemplate.STREAM_CODEC, RetortingRecipe::result,
            FluidStackTemplate.STREAM_CODEC, RetortingRecipe::fluidOutput,
            ByteBufCodecs.VAR_INT, RetortingRecipe::time,
            ByteBufCodecs.FLOAT, RetortingRecipe::experience,
            RetortingRecipe::new);

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
    public RecipeSerializer<RetortingRecipe> getSerializer() {
        return ModRecipes.RETORTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<RetortingRecipe> getType() {
        return ModRecipes.RETORTING.get();
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
