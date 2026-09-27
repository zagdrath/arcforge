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

// One Arcforge Furnace smelt: some metal, plus an optional additive, plus coal coke from the coke slot, into
// a result and an optional by-product (iron + coke -> steel + slag; iron + gold + coke -> Wrought Alloy
// + slag). Only progresses while the furnace is at least minHeat °C. A recipe with no additive only
// matches while the additive slot is empty, so the metal of an alloy never makes steel by accident.
public record ArcforgeSmeltingRecipe(Counted metal, Optional<Counted> additive, int coke, ItemStackTemplate result,
        Optional<ItemStackTemplate> byproduct, int time, int minHeat) implements Recipe<ArcforgeSmeltingRecipe.Input> {
    public static final int DEFAULT_TIME = 400;
    public static final int DEFAULT_MIN_HEAT = 1_200;

    // An ingredient and how many of it a smelt uses.
    public record Counted(Ingredient ingredient, int count) {
        public static final MapCodec<Counted> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Counted::ingredient),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Counted::count))
                .apply(i, Counted::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Counted> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, Counted::ingredient,
                ByteBufCodecs.VAR_INT, Counted::count,
                Counted::new);

        // Whether the stack is this ingredient, however many there are.
        public boolean is(ItemStack stack) {
            return ingredient.test(stack);
        }

        // Whether the stack is this ingredient and there are enough of it.
        public boolean test(ItemStack stack) {
            return ingredient.test(stack) && stack.getCount() >= count;
        }
    }

    // The furnace's metal and additive slots. (Coke is checked by the furnace: it's fuel as well.)
    public record Input(ItemStack metal, ItemStack additive) implements net.minecraft.world.item.crafting.RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return switch (index) {
                case 0 -> metal;
                case 1 -> additive;
                default -> throw new IllegalArgumentException("No item for index " + index);
            };
        }

        @Override
        public int size() {
            return 2;
        }
    }

    public static final MapCodec<ArcforgeSmeltingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Counted.MAP_CODEC.codec().fieldOf("metal").forGetter(ArcforgeSmeltingRecipe::metal),
            Counted.MAP_CODEC.codec().optionalFieldOf("additive").forGetter(ArcforgeSmeltingRecipe::additive),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("coke", 1).forGetter(ArcforgeSmeltingRecipe::coke),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(ArcforgeSmeltingRecipe::result),
            ItemStackTemplate.CODEC.optionalFieldOf("byproduct").forGetter(ArcforgeSmeltingRecipe::byproduct),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(ArcforgeSmeltingRecipe::time),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("min_temp", DEFAULT_MIN_HEAT).forGetter(ArcforgeSmeltingRecipe::minHeat))
            .apply(i, ArcforgeSmeltingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcforgeSmeltingRecipe> STREAM_CODEC = StreamCodec.composite(
            Counted.STREAM_CODEC, ArcforgeSmeltingRecipe::metal,
            ByteBufCodecs.optional(Counted.STREAM_CODEC), ArcforgeSmeltingRecipe::additive,
            ByteBufCodecs.VAR_INT, ArcforgeSmeltingRecipe::coke,
            ItemStackTemplate.STREAM_CODEC, ArcforgeSmeltingRecipe::result,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), ArcforgeSmeltingRecipe::byproduct,
            ByteBufCodecs.VAR_INT, ArcforgeSmeltingRecipe::time,
            ByteBufCodecs.VAR_INT, ArcforgeSmeltingRecipe::minHeat,
            ArcforgeSmeltingRecipe::new);

    @Override
    public boolean matches(Input input, Level level) {
        return metal.test(input.metal())
                && additive.map(needed -> needed.test(input.additive())).orElse(input.additive().isEmpty());
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
