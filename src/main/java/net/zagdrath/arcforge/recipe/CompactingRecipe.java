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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.zagdrath.arcforge.registry.ModRecipes;

// The Waste Compactor's: low-level waste (Used HEPA Filters, worn Hazmat Suit pieces, contaminated scrap) crushed
// into an empty Waste Drum: input (an ingredient and how many), result (a Low-Level Waste Drum), time (ticks).
// Any contaminated item (see Contamination) goes the same way without a recipe of its own (WasteCompactorBlockEntity).
public record CompactingRecipe(ChemicalReactingRecipe.ItemInput input, ItemStackTemplate result, int time) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 160;

    public static final MapCodec<CompactingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.ItemInput.CODEC.fieldOf("input").forGetter(CompactingRecipe::input),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(CompactingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(CompactingRecipe::time))
            .apply(i, CompactingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CompactingRecipe> STREAM_CODEC = StreamCodec.composite(
            ChemicalReactingRecipe.ItemInput.STREAM_CODEC, CompactingRecipe::input,
            ItemStackTemplate.STREAM_CODEC, CompactingRecipe::result,
            ByteBufCodecs.VAR_INT, CompactingRecipe::time,
            CompactingRecipe::new);

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.ingredient().test(input.item());
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
    public RecipeSerializer<CompactingRecipe> getSerializer() {
        return ModRecipes.COMPACTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CompactingRecipe> getType() {
        return ModRecipes.COMPACTING.get();
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
