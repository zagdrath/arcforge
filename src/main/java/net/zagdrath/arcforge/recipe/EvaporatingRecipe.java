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

// Thermal Evaporator Array (arcforge:evaporating): a fluid boiled down with heat. Every input.amount mB the tower
// evaporates gives fluid_result (Seawater -> Brine) and/or item_result (Brine -> Salt), returns water mB of the steam as
// Water, and takes heat HU. How fast it works is the tower's own (its throughput and temperature), not the recipe's.
public record EvaporatingRecipe(ChemicalReactingRecipe.FluidInput input, Optional<FluidStackTemplate> fluidResult,
        Optional<ItemStackTemplate> itemResult, int water, int heat) implements Recipe<EvaporatingRecipe.Input> {
    public static final MapCodec<EvaporatingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.FluidInput.CODEC.fieldOf("input").forGetter(EvaporatingRecipe::input),
            FluidStackTemplate.CODEC.optionalFieldOf("fluid_result").forGetter(EvaporatingRecipe::fluidResult),
            ItemStackTemplate.CODEC.optionalFieldOf("item_result").forGetter(EvaporatingRecipe::itemResult),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("water", 0).forGetter(EvaporatingRecipe::water),
            ExtraCodecs.POSITIVE_INT.fieldOf("heat").forGetter(EvaporatingRecipe::heat))
            .apply(i, EvaporatingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EvaporatingRecipe> STREAM_CODEC = StreamCodec.composite(
            ChemicalReactingRecipe.FluidInput.STREAM_CODEC, EvaporatingRecipe::input,
            ByteBufCodecs.optional(FluidStackTemplate.STREAM_CODEC), EvaporatingRecipe::fluidResult,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), EvaporatingRecipe::itemResult,
            ByteBufCodecs.VAR_INT, EvaporatingRecipe::water,
            ByteBufCodecs.VAR_INT, EvaporatingRecipe::heat,
            EvaporatingRecipe::new);

    // HU per mB of input.
    public double heatPerMb() {
        return heat / (double) Math.max(1, input.amount());
    }

    // What the tower's input tank holds.
    public record Input(FluidResource fluid) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    @Override
    public boolean matches(Input input, Level level) {
        return this.input.test(input.fluid());
    }

    // The tower fills its own tanks and Salt slot.
    @Override
    public ItemStack assemble(Input input) {
        return itemResult.map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
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
    public RecipeSerializer<EvaporatingRecipe> getSerializer() {
        return ModRecipes.EVAPORATING_SERIALIZER.get();
    }

    @Override
    public RecipeType<EvaporatingRecipe> getType() {
        return ModRecipes.EVAPORATING.get();
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
