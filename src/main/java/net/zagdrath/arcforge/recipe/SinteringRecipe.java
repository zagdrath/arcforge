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
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.nuclear.Enrichment;
import net.zagdrath.arcforge.registry.ModRecipes;

// Sintering, in the Induction Furnace (and its array) only, never a plain furnace: a green fuel pellet fired into a
// Fuel Pellet. The result keeps the input's U-235 assay (see Enrichment.carry), so enriched fuel stays enriched. JSON:
// ingredient, result, optional time (ticks before the furnace's speed-up, 200) and experience.
public record SinteringRecipe(Ingredient ingredient, ItemStackTemplate result, int time, float experience) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 200;

    public static final MapCodec<SinteringRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(SinteringRecipe::ingredient),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(SinteringRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(SinteringRecipe::time),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("experience", 0.1F).forGetter(SinteringRecipe::experience))
            .apply(i, SinteringRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SinteringRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, SinteringRecipe::ingredient,
            ItemStackTemplate.STREAM_CODEC, SinteringRecipe::result,
            ByteBufCodecs.VAR_INT, SinteringRecipe::time,
            ByteBufCodecs.FLOAT, SinteringRecipe::experience,
            SinteringRecipe::new);

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return Enrichment.carry(input.item(), result.create());
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
    public RecipeSerializer<SinteringRecipe> getSerializer() {
        return ModRecipes.SINTERING_SERIALIZER.get();
    }

    @Override
    public RecipeType<SinteringRecipe> getType() {
        return ModRecipes.SINTERING.get();
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
