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
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.registry.ModRecipes;

// One Arcforge Furnace smelt: an input plus a reagent (iron ingot + coal coke) into a result and an
// optional by-product (steel ingot + slag). Only progresses while the furnace is at least minHeat °C.
public record ArcforgeSmeltingRecipe(Ingredient input, Ingredient reagent, ItemStackTemplate result, Optional<ItemStackTemplate> byproduct,
        int time, int minHeat) implements Recipe<ArcforgeSmeltingRecipe.Input> {
    public static final int DEFAULT_TIME = 400;
    public static final int DEFAULT_MIN_HEAT = 1_200;

    // The furnace's two input slots.
    public record Input(ItemStack input, ItemStack reagent) implements net.minecraft.world.item.crafting.RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return switch (index) {
                case 0 -> input;
                case 1 -> reagent;
                default -> throw new IllegalArgumentException("No item for index " + index);
            };
        }

        @Override
        public int size() {
            return 2;
        }
    }

    public static final MapCodec<ArcforgeSmeltingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("input").forGetter(ArcforgeSmeltingRecipe::input),
            Ingredient.CODEC.fieldOf("reagent").forGetter(ArcforgeSmeltingRecipe::reagent),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(ArcforgeSmeltingRecipe::result),
            ItemStackTemplate.CODEC.optionalFieldOf("byproduct").forGetter(ArcforgeSmeltingRecipe::byproduct),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(ArcforgeSmeltingRecipe::time),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("min_heat", DEFAULT_MIN_HEAT).forGetter(ArcforgeSmeltingRecipe::minHeat))
            .apply(i, ArcforgeSmeltingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcforgeSmeltingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, ArcforgeSmeltingRecipe::input,
            Ingredient.CONTENTS_STREAM_CODEC, ArcforgeSmeltingRecipe::reagent,
            ItemStackTemplate.STREAM_CODEC, ArcforgeSmeltingRecipe::result,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), ArcforgeSmeltingRecipe::byproduct,
            ByteBufCodecs.VAR_INT, ArcforgeSmeltingRecipe::time,
            ByteBufCodecs.VAR_INT, ArcforgeSmeltingRecipe::minHeat,
            ArcforgeSmeltingRecipe::new);

    @Override
    public boolean matches(Input input, Level level) {
        return this.input.test(input.input()) && reagent.test(input.reagent());
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
    public RecipeSerializer<ArcforgeSmeltingRecipe> getSerializer() {
        return ModRecipes.ARCFORGE_SMELTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<ArcforgeSmeltingRecipe> getType() {
        return ModRecipes.ARCFORGE_SMELTING.get();
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
