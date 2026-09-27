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
import net.zagdrath.arcforge.registry.ModRecipes;

// Fiberizer: one input melted and spun into mineral wool. Each tick of the operation uses fe_per_tick FE
// and hu_per_tick HU, and the machine only works while it is at least min_temp °C.
public record FiberizingRecipe(Ingredient ingredient, ItemStackTemplate result, int time, int fePerTick, int huPerTick, int minTemp)
        implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 160;
    public static final int DEFAULT_FE_PER_TICK = 30;
    public static final int DEFAULT_HU_PER_TICK = 20;
    public static final int DEFAULT_MIN_TEMP = 800;

    public static final MapCodec<FiberizingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(FiberizingRecipe::ingredient),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(FiberizingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(FiberizingRecipe::time),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("fe_per_tick", DEFAULT_FE_PER_TICK).forGetter(FiberizingRecipe::fePerTick),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("hu_per_tick", DEFAULT_HU_PER_TICK).forGetter(FiberizingRecipe::huPerTick),
            Codec.INT.optionalFieldOf("min_temp", DEFAULT_MIN_TEMP).forGetter(FiberizingRecipe::minTemp))
            .apply(i, FiberizingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FiberizingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, FiberizingRecipe::ingredient,
            ItemStackTemplate.STREAM_CODEC, FiberizingRecipe::result,
            ByteBufCodecs.VAR_INT, FiberizingRecipe::time,
            ByteBufCodecs.VAR_INT, FiberizingRecipe::fePerTick,
            ByteBufCodecs.VAR_INT, FiberizingRecipe::huPerTick,
            ByteBufCodecs.VAR_INT, FiberizingRecipe::minTemp,
            FiberizingRecipe::new);

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
    public RecipeSerializer<FiberizingRecipe> getSerializer() {
        return ModRecipes.FIBERIZING_SERIALIZER.get();
    }

    @Override
    public RecipeType<FiberizingRecipe> getType() {
        return ModRecipes.FIBERIZING.get();
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
