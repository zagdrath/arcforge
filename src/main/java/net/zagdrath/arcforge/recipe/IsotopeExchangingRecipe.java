/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModRecipes;

// The Heavy Water Tower's isotope exchange (the Girdler sulfide process: water and Hydrogen Sulfide trade deuterium on
// trays, hot and cold, with steam for the hot half): each tray does one operation every "time" ticks, taking every
// input (one to three fluids) and giving the output. JSON: inputs (FluidInput list), output, optional time (20).
public record IsotopeExchangingRecipe(List<ChemicalReactingRecipe.FluidInput> inputs, FluidStackTemplate output, int time)
        implements Recipe<IsotopeExchangingRecipe.Input> {
    public static final int DEFAULT_TIME = 20;

    public static final MapCodec<IsotopeExchangingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.FluidInput.CODEC.listOf(1, 3).fieldOf("inputs").forGetter(IsotopeExchangingRecipe::inputs),
            FluidStackTemplate.CODEC.fieldOf("output").forGetter(IsotopeExchangingRecipe::output),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(IsotopeExchangingRecipe::time))
            .apply(i, IsotopeExchangingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, IsotopeExchangingRecipe> STREAM_CODEC = StreamCodec.composite(
            ChemicalReactingRecipe.FluidInput.STREAM_CODEC.apply(ByteBufCodecs.list(3)), IsotopeExchangingRecipe::inputs,
            FluidStackTemplate.STREAM_CODEC, IsotopeExchangingRecipe::output,
            ByteBufCodecs.VAR_INT, IsotopeExchangingRecipe::time,
            IsotopeExchangingRecipe::new);

    // What the tower's input tanks hold.
    public record Input(List<FluidResource> fluids) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    // Every input is in one of the tanks.
    @Override
    public boolean matches(Input input, Level level) {
        for (ChemicalReactingRecipe.FluidInput wanted : inputs) {
            if (input.fluids().stream().noneMatch(wanted::test)) {
                return false;
            }
        }
        return true;
    }

    // Whether this fluid is one of its inputs.
    public boolean takes(FluidResource fluid) {
        return inputs.stream().anyMatch(input -> input.test(fluid));
    }

    @Override
    public ItemStack assemble(Input input) {
        return ItemStack.EMPTY;
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
    public RecipeSerializer<IsotopeExchangingRecipe> getSerializer() {
        return ModRecipes.ISOTOPE_EXCHANGING_SERIALIZER.get();
    }

    @Override
    public RecipeType<IsotopeExchangingRecipe> getType() {
        return ModRecipes.ISOTOPE_EXCHANGING.get();
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
