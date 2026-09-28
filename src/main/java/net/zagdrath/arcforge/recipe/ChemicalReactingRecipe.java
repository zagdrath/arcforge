/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModRecipes;

// Chemical Reactor: an item (with a count) and up to two fluids react into an item, a fluid or both, with an
// optional byproduct rolled once per operation (Slag, when precipitating). Each fluid input is drawn from a
// different input tank. "category" only labels the recipe in JEI: general, leaching or precipitating.
public record ChemicalReactingRecipe(Optional<ItemInput> itemInput, List<FluidInput> fluidInputs, Optional<ItemStackTemplate> itemOutput,
        Optional<FluidStackTemplate> fluidOutput, Optional<ItemStackTemplate> byproduct, float byproductChance, int time,
        Optional<Integer> energyPerTick, String category) implements Recipe<ChemicalReactorInput> {
    public static final int DEFAULT_TIME = 100;
    public static final int MAX_FLUID_INPUTS = 2;

    // {"ingredient": ..., "count": 1..64}
    public record ItemInput(Ingredient ingredient, int count) {
        public static final Codec<ItemInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(ItemInput::ingredient),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(ItemInput::count))
                .apply(i, ItemInput::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ItemInput> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, ItemInput::ingredient,
                ByteBufCodecs.VAR_INT, ItemInput::count,
                ItemInput::new);

        public boolean test(ItemStack stack) {
            return ingredient.test(stack) && stack.getCount() >= count;
        }
    }

    // {"fluid": "arcforge:sulfuric_acid", "amount": 250} or {"tag": "minecraft:water", "amount": 100}.
    public record FluidInput(Either<Holder<Fluid>, TagKey<Fluid>> fluid, int amount) {
        public static final Codec<FluidInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.mapEither(BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("fluid"), TagKey.codec(Registries.FLUID).fieldOf("tag"))
                        .forGetter(FluidInput::fluid),
                ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidInput::amount))
                .apply(i, FluidInput::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, FluidInput> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.either(ByteBufCodecs.holderRegistry(Registries.FLUID), TagKey.streamCodec(Registries.FLUID)), FluidInput::fluid,
                ByteBufCodecs.VAR_INT, FluidInput::amount,
                FluidInput::new);

        // The fluid itself, or any fluid in the tag.
        public boolean test(FluidResource resource) {
            return !resource.isEmpty() && fluid.map(holder -> resource.getFluid() == holder.value(), tag -> resource.getFluid().defaultFluidState().is(tag));
        }

        // Whether a tank holding this much of that fluid can supply this input.
        public boolean test(FluidResource resource, int available) {
            return available >= amount && test(resource);
        }

        // The fluids that satisfy it (a tag's source fluids only), for display.
        public List<Fluid> fluids() {
            return fluid.<List<Fluid>>map(holder -> List.of(holder.value()), tag -> StreamSupport.stream(BuiltInRegistries.FLUID.getTagOrEmpty(tag).spliterator(), false)
                    .map(Holder::value)
                    .filter(value -> value.isSource(value.defaultFluidState()))
                    .toList());
        }
    }

    public static final MapCodec<ChemicalReactingRecipe> MAP_CODEC = RecordCodecBuilder.<ChemicalReactingRecipe>mapCodec(i -> i.group(
            ItemInput.CODEC.optionalFieldOf("item_input").forGetter(ChemicalReactingRecipe::itemInput),
            FluidInput.CODEC.listOf(0, MAX_FLUID_INPUTS).optionalFieldOf("fluid_inputs", List.of()).forGetter(ChemicalReactingRecipe::fluidInputs),
            ItemStackTemplate.CODEC.optionalFieldOf("item_output").forGetter(ChemicalReactingRecipe::itemOutput),
            FluidStackTemplate.CODEC.optionalFieldOf("fluid_output").forGetter(ChemicalReactingRecipe::fluidOutput),
            ItemStackTemplate.CODEC.optionalFieldOf("byproduct").forGetter(ChemicalReactingRecipe::byproduct),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("byproduct_chance", 1.0F).forGetter(ChemicalReactingRecipe::byproductChance),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(ChemicalReactingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy_per_tick").forGetter(ChemicalReactingRecipe::energyPerTick),
            Codec.STRING.optionalFieldOf("category", "general").forGetter(ChemicalReactingRecipe::category))
            .apply(i, ChemicalReactingRecipe::new))
            .validate(ChemicalReactingRecipe::validate);

    public static final StreamCodec<RegistryFriendlyByteBuf, ChemicalReactingRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ItemInput.STREAM_CODEC), ChemicalReactingRecipe::itemInput,
            FluidInput.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FLUID_INPUTS)), ChemicalReactingRecipe::fluidInputs,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), ChemicalReactingRecipe::itemOutput,
            ByteBufCodecs.optional(FluidStackTemplate.STREAM_CODEC), ChemicalReactingRecipe::fluidOutput,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), ChemicalReactingRecipe::byproduct,
            ByteBufCodecs.FLOAT, ChemicalReactingRecipe::byproductChance,
            ByteBufCodecs.VAR_INT, ChemicalReactingRecipe::time,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), ChemicalReactingRecipe::energyPerTick,
            ByteBufCodecs.STRING_UTF8, ChemicalReactingRecipe::category,
            ChemicalReactingRecipe::new);

    // Something goes in and something comes out.
    private static DataResult<ChemicalReactingRecipe> validate(ChemicalReactingRecipe recipe) {
        if (recipe.itemInput.isEmpty() && recipe.fluidInputs.isEmpty()) {
            return DataResult.error(() -> "A chemical_reacting recipe needs an item_input or a fluid_input");
        }
        if (recipe.itemOutput.isEmpty() && recipe.fluidOutput.isEmpty()) {
            return DataResult.error(() -> "A chemical_reacting recipe needs an item_output or a fluid_output");
        }
        return DataResult.success(recipe);
    }

    // FE/t before upgrades.
    public int baseEnergyPerTick() {
        return energyPerTick.orElseGet(ArcforgeConfig.REACTOR_ENERGY_PER_TICK::getAsInt);
    }

    @Override
    public boolean matches(ChemicalReactorInput input, Level level) {
        return (itemInput.isEmpty() || itemInput.get().test(input.item())) && tanksFor(input) != null;
    }

    // Which input tank (0 or 1) supplies each fluid input, each from a different tank, or null if they can't all
    // be supplied. Tries A then B for the first input, and the other way round.
    public int @Nullable [] tanksFor(ChemicalReactorInput input) {
        return switch (fluidInputs.size()) {
            case 0 -> new int[0];
            case 1 -> fluidInputs.get(0).test(input.fluidA(), input.amountA()) ? new int[] { 0 }
                    : fluidInputs.get(0).test(input.fluidB(), input.amountB()) ? new int[] { 1 } : null;
            default -> {
                FluidInput first = fluidInputs.get(0), second = fluidInputs.get(1);
                if (first.test(input.fluidA(), input.amountA()) && second.test(input.fluidB(), input.amountB())) {
                    yield new int[] { 0, 1 };
                }
                if (first.test(input.fluidB(), input.amountB()) && second.test(input.fluidA(), input.amountA())) {
                    yield new int[] { 1, 0 };
                }
                yield null;
            }
        };
    }

    // Whether this item could be part of the recipe (ignoring the count), for the input slot.
    public boolean usesItem(ItemStack stack) {
        return itemInput.isPresent() && itemInput.get().ingredient().test(stack);
    }

    public boolean usesFluid(FluidResource resource) {
        return fluidInputs.stream().anyMatch(input -> input.test(resource));
    }

    @Override
    public ItemStack assemble(ChemicalReactorInput input) {
        return itemOutput.map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
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
    public RecipeSerializer<ChemicalReactingRecipe> getSerializer() {
        return ModRecipes.CHEMICAL_REACTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<ChemicalReactingRecipe> getType() {
        return ModRecipes.CHEMICAL_REACTING.get();
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
