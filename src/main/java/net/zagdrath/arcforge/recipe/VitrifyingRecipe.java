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
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.zagdrath.arcforge.registry.ModRecipes;

// The Vitrification Plant's: liquid waste melted into glass and poured into a steel canister. JSON: fluid (the waste and
// how much a log takes), glass (an ingredient and how many), result (the Vitrified Waste Log), time, energy (FE/t).
// The log carries the waste's radioactivity (see WasteLog), which is why the plant sets it, not the recipe.
public record VitrifyingRecipe(ChemicalReactingRecipe.FluidInput fluid, ChemicalReactingRecipe.ItemInput glass, ItemStackTemplate result, int time,
        int energy) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 300;
    public static final int DEFAULT_ENERGY = 160;

    public static final MapCodec<VitrifyingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.FluidInput.CODEC.fieldOf("fluid").forGetter(VitrifyingRecipe::fluid),
            ChemicalReactingRecipe.ItemInput.CODEC.fieldOf("glass").forGetter(VitrifyingRecipe::glass),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(VitrifyingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(VitrifyingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy", DEFAULT_ENERGY).forGetter(VitrifyingRecipe::energy))
            .apply(i, VitrifyingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VitrifyingRecipe> STREAM_CODEC = StreamCodec.composite(
            ChemicalReactingRecipe.FluidInput.STREAM_CODEC, VitrifyingRecipe::fluid,
            ChemicalReactingRecipe.ItemInput.STREAM_CODEC, VitrifyingRecipe::glass,
            ItemStackTemplate.STREAM_CODEC, VitrifyingRecipe::result,
            ByteBufCodecs.VAR_INT, VitrifyingRecipe::time,
            ByteBufCodecs.VAR_INT, VitrifyingRecipe::energy,
            VitrifyingRecipe::new);

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return glass.ingredient().test(input.item());
    }

    public boolean takes(FluidResource resource) {
        return !resource.isEmpty() && fluid.fluids().contains(resource.getFluid());
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
    public RecipeSerializer<VitrifyingRecipe> getSerializer() {
        return ModRecipes.VITRIFYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<VitrifyingRecipe> getType() {
        return ModRecipes.VITRIFYING.get();
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
