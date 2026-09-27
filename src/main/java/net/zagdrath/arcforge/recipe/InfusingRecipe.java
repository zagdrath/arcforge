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
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModRecipes;

// Infuser: one item soaked in a fluid (planks + 50 mB creosote -> treated planks). The fluid is used up
// when the item is done. Any fluid works, so later recipes can use other fluids.
public record InfusingRecipe(Ingredient ingredient, FluidStackTemplate fluid, ItemStackTemplate result, int time)
        implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 100;

    public static final MapCodec<InfusingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(InfusingRecipe::ingredient),
            FluidStackTemplate.CODEC.fieldOf("fluid").forGetter(InfusingRecipe::fluid),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(InfusingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(InfusingRecipe::time))
            .apply(i, InfusingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, InfusingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, InfusingRecipe::ingredient,
            FluidStackTemplate.STREAM_CODEC, InfusingRecipe::fluid,
            ItemStackTemplate.STREAM_CODEC, InfusingRecipe::result,
            ByteBufCodecs.VAR_INT, InfusingRecipe::time,
            InfusingRecipe::new);

    // Only the item is matched here; the machine checks the fluid, so it can say which one is missing.
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    public boolean usesFluid(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid() == fluid.fluid().value();
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
    public RecipeSerializer<InfusingRecipe> getSerializer() {
        return ModRecipes.INFUSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<InfusingRecipe> getType() {
        return ModRecipes.INFUSING.get();
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
