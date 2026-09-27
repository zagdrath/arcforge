/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.registry.ModRecipes;

// Metal Press: count of the ingredient pressed into the result by a die (plate_die + 1 steel ingot ->
// 1 steel plate). The die must be exactly the recipe's and is never used up. time is for the single
// Metal Press; the Metal Pressing Array works faster.
public record PressingRecipe(Item die, Ingredient ingredient, int count, ItemStackTemplate result, int time)
        implements Recipe<PressingRecipe.Input> {
    public static final int DEFAULT_TIME = 100;

    // The die slot and the input slot.
    public record Input(ItemStack die, ItemStack input) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return index == 0 ? die : input;
        }

        @Override
        public int size() {
            return 2;
        }
    }

    public static final MapCodec<PressingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("die").forGetter(PressingRecipe::die),
            Ingredient.CODEC.fieldOf("ingredient").forGetter(PressingRecipe::ingredient),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(PressingRecipe::count),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(PressingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(PressingRecipe::time))
            .apply(i, PressingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PressingRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), PressingRecipe::die,
            Ingredient.CONTENTS_STREAM_CODEC, PressingRecipe::ingredient,
            ByteBufCodecs.VAR_INT, PressingRecipe::count,
            ItemStackTemplate.STREAM_CODEC, PressingRecipe::result,
            ByteBufCodecs.VAR_INT, PressingRecipe::time,
            PressingRecipe::new);

    // Whether this die can press this item, whatever the count.
    public boolean accepts(ItemStack die, ItemStack stack) {
        return die.is(this.die) && ingredient.test(stack);
    }

    @Override
    public boolean matches(Input input, Level level) {
        return accepts(input.die(), input.input()) && input.input().getCount() >= count;
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
    public RecipeSerializer<PressingRecipe> getSerializer() {
        return ModRecipes.PRESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<PressingRecipe> getType() {
        return ModRecipes.PRESSING.get();
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
