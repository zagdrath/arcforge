/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.Optional;

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
import net.zagdrath.arcforge.registry.ModRecipes;

// Seed Extractor (arcforge:seed_extracting): a crop threshed into its seeds, with an optional bonus rolled once per
// item, over `time` ticks.
public record SeedExtractingRecipe(Ingredient ingredient, ItemStackTemplate result, Optional<ItemStackTemplate> bonus, float bonusChance,
        int time) implements Recipe<SingleRecipeInput>, ItemProcessingRecipe {
    public static final int DEFAULT_TIME = 100;
    public static final float DEFAULT_BONUS_CHANCE = 0.25F;

    public static final MapCodec<SeedExtractingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(SeedExtractingRecipe::ingredient),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(SeedExtractingRecipe::result),
            ItemStackTemplate.CODEC.optionalFieldOf("bonus").forGetter(SeedExtractingRecipe::bonus),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("bonus_chance", DEFAULT_BONUS_CHANCE).forGetter(SeedExtractingRecipe::bonusChance),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(SeedExtractingRecipe::time))
            .apply(i, SeedExtractingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SeedExtractingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, SeedExtractingRecipe::ingredient,
            ItemStackTemplate.STREAM_CODEC, SeedExtractingRecipe::result,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), SeedExtractingRecipe::bonus,
            ByteBufCodecs.FLOAT, SeedExtractingRecipe::bonusChance,
            ByteBufCodecs.VAR_INT, SeedExtractingRecipe::time,
            SeedExtractingRecipe::new);

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return result.create();
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
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
    public RecipeSerializer<SeedExtractingRecipe> getSerializer() {
        return ModRecipes.SEED_EXTRACTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<SeedExtractingRecipe> getType() {
        return ModRecipes.SEED_EXTRACTING.get();
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
