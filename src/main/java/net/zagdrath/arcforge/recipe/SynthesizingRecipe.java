/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
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
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModRecipes;

// Haber Reactor (arcforge:synthesizing): one or two gases (each from its own input tank) combined into a gas over
// `time` ticks, with energy_per_tick FE/t (haberReactor.energyPerTick if unset) and heat_per_tick HU/t, and only at
// min_temperature °C or hotter (haberReactor.minTemperature if unset). 60 mB of Hydrogen and 20 mB of Nitrogen make
// 40 mB of Ammonia (3 H2 + N2 -> 2 NH3).
public record SynthesizingRecipe(List<ChemicalReactingRecipe.FluidInput> inputs, FluidStackTemplate output, int time, Optional<Integer> energyPerTick,
        int heatPerTick, Optional<Integer> minTemperature) implements Recipe<SynthesizingRecipe.Input> {
    public static final int DEFAULT_TIME = 20;
    public static final int DEFAULT_HEAT_PER_TICK = 10;

    public static final MapCodec<SynthesizingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.FluidInput.CODEC.listOf(1, 2).fieldOf("inputs").forGetter(SynthesizingRecipe::inputs),
            FluidStackTemplate.CODEC.fieldOf("output").forGetter(SynthesizingRecipe::output),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(SynthesizingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy_per_tick").forGetter(SynthesizingRecipe::energyPerTick),
            Codec.intRange(0, 1_000_000).optionalFieldOf("heat_per_tick", DEFAULT_HEAT_PER_TICK).forGetter(SynthesizingRecipe::heatPerTick),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("min_temperature").forGetter(SynthesizingRecipe::minTemperature))
            .apply(i, SynthesizingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SynthesizingRecipe> STREAM_CODEC = StreamCodec.composite(
            ChemicalReactingRecipe.FluidInput.STREAM_CODEC.apply(ByteBufCodecs.list(2)), SynthesizingRecipe::inputs,
            FluidStackTemplate.STREAM_CODEC, SynthesizingRecipe::output,
            ByteBufCodecs.VAR_INT, SynthesizingRecipe::time,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), SynthesizingRecipe::energyPerTick,
            ByteBufCodecs.VAR_INT, SynthesizingRecipe::heatPerTick,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), SynthesizingRecipe::minTemperature,
            SynthesizingRecipe::new);

    // The reactor's two input tanks (fluid and mB each).
    public record Input(FluidResource fluidA, int amountA, FluidResource fluidB, int amountB) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    public int baseEnergyPerTick() {
        return energyPerTick.orElseGet(ArcforgeConfig.HABER_ENERGY_PER_TICK::getAsInt);
    }

    public int minTemperatureOrDefault() {
        return minTemperature.orElseGet(ArcforgeConfig.HABER_MIN_TEMPERATURE::getAsInt);
    }

    @Override
    public boolean matches(Input input, Level level) {
        return tanksFor(input) != null;
    }

    // Which input tank (0 or 1) supplies each input, each from a different tank, or null if they can't all be
    // supplied. Tries A then B for the first input, and the other way round.
    public int @Nullable [] tanksFor(Input input) {
        ChemicalReactingRecipe.FluidInput first = inputs.get(0);
        if (inputs.size() == 1) {
            return first.test(input.fluidA(), input.amountA()) ? new int[] { 0 } : first.test(input.fluidB(), input.amountB()) ? new int[] { 1 } : null;
        }
        ChemicalReactingRecipe.FluidInput second = inputs.get(1);
        if (first.test(input.fluidA(), input.amountA()) && second.test(input.fluidB(), input.amountB())) {
            return new int[] { 0, 1 };
        }
        if (first.test(input.fluidB(), input.amountB()) && second.test(input.fluidA(), input.amountA())) {
            return new int[] { 1, 0 };
        }
        return null;
    }

    public boolean usesFluid(FluidResource resource) {
        return inputs.stream().anyMatch(input -> input.test(resource));
    }

    // The output is a gas; the reactor fills its tank itself.
    @Override
    public ItemStack assemble(Input input) {
        return ItemStack.EMPTY;
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
    public RecipeSerializer<SynthesizingRecipe> getSerializer() {
        return ModRecipes.SYNTHESIZING_SERIALIZER.get();
    }

    @Override
    public RecipeType<SynthesizingRecipe> getType() {
        return ModRecipes.SYNTHESIZING.get();
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
