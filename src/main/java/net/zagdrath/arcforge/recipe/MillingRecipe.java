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

// Millstone and Mill (arcforge:milling): one item ground into a result, with an optional bonus rolled once per item.
// The Millstone grinds it in `turns` turns (one per redstone pulse or use); the Mill takes `time` ticks per item in
// each of its three lanes.
public record MillingRecipe(Ingredient ingredient, ItemStackTemplate result, Optional<ItemStackTemplate> bonus, float bonusChance, int turns,
        int time) implements Recipe<SingleRecipeInput>, ItemProcessingRecipe {
    public static final int DEFAULT_TURNS = 2;
    public static final int DEFAULT_TIME = 100;
    public static final float DEFAULT_BONUS_CHANCE = 0.25F;

    public static final MapCodec<MillingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(MillingRecipe::ingredient),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(MillingRecipe::result),
            ItemStackTemplate.CODEC.optionalFieldOf("bonus").forGetter(MillingRecipe::bonus),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("bonus_chance", DEFAULT_BONUS_CHANCE).forGetter(MillingRecipe::bonusChance),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("turns", DEFAULT_TURNS).forGetter(MillingRecipe::turns),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(MillingRecipe::time))
            .apply(i, MillingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MillingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, MillingRecipe::ingredient,
            ItemStackTemplate.STREAM_CODEC, MillingRecipe::result,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), MillingRecipe::bonus,
            ByteBufCodecs.FLOAT, MillingRecipe::bonusChance,
            ByteBufCodecs.VAR_INT, MillingRecipe::turns,
            ByteBufCodecs.VAR_INT, MillingRecipe::time,
            MillingRecipe::new);

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
    public RecipeSerializer<MillingRecipe> getSerializer() {
        return ModRecipes.MILLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<MillingRecipe> getType() {
        return ModRecipes.MILLING.get();
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
