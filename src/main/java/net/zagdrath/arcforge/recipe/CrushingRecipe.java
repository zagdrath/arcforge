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

// Arc Crusher / Arc Crushing Array: one input item crushed into a result, with an optional bonus item
// rolled once per operation. "ore" marks recipes the Arc Crushing Array doubles; never set it on
// ingots, gems or blocks, or the Array would duplicate items.
public record CrushingRecipe(Ingredient ingredient, ItemStackTemplate result, Optional<ItemStackTemplate> bonus, float bonusChance,
        int time, boolean ore) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 200;
    public static final float DEFAULT_BONUS_CHANCE = 0.25F;

    public static final MapCodec<CrushingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(CrushingRecipe::ingredient),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(CrushingRecipe::result),
            ItemStackTemplate.CODEC.optionalFieldOf("bonus").forGetter(CrushingRecipe::bonus),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("bonus_chance", DEFAULT_BONUS_CHANCE).forGetter(CrushingRecipe::bonusChance),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(CrushingRecipe::time),
            Codec.BOOL.optionalFieldOf("ore", false).forGetter(CrushingRecipe::ore))
            .apply(i, CrushingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrushingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, CrushingRecipe::ingredient,
            ItemStackTemplate.STREAM_CODEC, CrushingRecipe::result,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), CrushingRecipe::bonus,
            ByteBufCodecs.FLOAT, CrushingRecipe::bonusChance,
            ByteBufCodecs.VAR_INT, CrushingRecipe::time,
            ByteBufCodecs.BOOL, CrushingRecipe::ore,
            CrushingRecipe::new);

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
    public RecipeSerializer<CrushingRecipe> getSerializer() {
        return ModRecipes.CRUSHING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CrushingRecipe> getType() {
        return ModRecipes.CRUSHING.get();
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
