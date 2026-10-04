/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.registry.ModRecipes;

// Vulcanizer (arcforge:vulcanizing): two items (each with a count, one from each input slot, either way round) cured
// into a result over `time` ticks, using hu_per_tick heat while it works: Raw Rubber + Sulfur -> Rubber. The Vulcanizer
// only works at farming.cropProcessing.vulcanizer.minTemperature or hotter.
public record VulcanizingRecipe(ChemicalReactingRecipe.ItemInput input, ChemicalReactingRecipe.ItemInput secondInput, ItemStackTemplate result,
        int time, int huPerTick) implements Recipe<VulcanizingRecipe.Input> {
    public static final int DEFAULT_TIME = 200;
    public static final int DEFAULT_HU_PER_TICK = 10;

    public static final MapCodec<VulcanizingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.ItemInput.CODEC.fieldOf("input").forGetter(VulcanizingRecipe::input),
            ChemicalReactingRecipe.ItemInput.CODEC.fieldOf("second_input").forGetter(VulcanizingRecipe::secondInput),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(VulcanizingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(VulcanizingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("hu_per_tick", DEFAULT_HU_PER_TICK).forGetter(VulcanizingRecipe::huPerTick))
            .apply(i, VulcanizingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VulcanizingRecipe> STREAM_CODEC = StreamCodec.composite(
            ChemicalReactingRecipe.ItemInput.STREAM_CODEC, VulcanizingRecipe::input,
            ChemicalReactingRecipe.ItemInput.STREAM_CODEC, VulcanizingRecipe::secondInput,
            ItemStackTemplate.STREAM_CODEC, VulcanizingRecipe::result,
            ByteBufCodecs.VAR_INT, VulcanizingRecipe::time,
            ByteBufCodecs.VAR_INT, VulcanizingRecipe::huPerTick,
            VulcanizingRecipe::new);

    // The two input slots.
    public record Input(ItemStack a, ItemStack b) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return index == 0 ? a : b;
        }

        @Override
        public int size() {
            return 2;
        }
    }

    // Which slot supplies input and which second_input: {0, 1} or {1, 0}, or null if the two slots can't.
    public int @Nullable [] slotsFor(ItemStack a, ItemStack b) {
        if (input.test(a) && secondInput.test(b)) {
            return new int[] { 0, 1 };
        }
        if (input.test(b) && secondInput.test(a)) {
            return new int[] { 1, 0 };
        }
        return null;
    }

    // Whether this item is one of the two it takes (in any amount).
    public boolean takes(ItemStack stack) {
        return input.ingredient().test(stack) || secondInput.ingredient().test(stack);
    }

    @Override
    public boolean matches(Input input, Level level) {
        return slotsFor(input.a(), input.b()) != null;
    }

    @Override
    public ItemStack assemble(Input input) {
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
    public RecipeSerializer<VulcanizingRecipe> getSerializer() {
        return ModRecipes.VULCANIZING_SERIALIZER.get();
    }

    @Override
    public RecipeType<VulcanizingRecipe> getType() {
        return ModRecipes.VULCANIZING.get();
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
