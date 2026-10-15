/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
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
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModRecipes;

// The refinery's downstream processes, one shape for every refining recipe type (see RefiningProcess): arcforge:cracking
// (the Cracking Unit), arcforge:reforming (the Catalytic Reformer), arcforge:coking (the Coker),
// arcforge:lubricant_blending (the Lubricant Blender), arcforge:hydrotreating (the Hydrotreater), arcforge:sulfur_recovery
// (the Sulfur Recovery Unit) and arcforge:steam_reforming (the Steam Methane Reformer). One fluid input (with its amount),
// and for the processes that take one a second fluid ("second_input", in the machine's second feed tank: Hydrogen,
// Oxygen or Steam), becomes up to three fluids
// ("fluids", each {"id", "amount"}; gases and liquids alike, in the machine's product tanks in that order) and an
// optional item ("item"), over "time" ticks, drawing "heat_per_tick" HU/t at "min_temp" °C or hotter and
// "energy_per_tick" FE/t (each 0 when left out, and the machine's own config fills in time when that's left out). An
// optional catalyst ("catalyst", an ingredient) sits in the machine's catalyst slot; one item lasts "catalyst_uses"
// operations.
public record RefiningRecipe(RefiningProcess process, ChemicalReactingRecipe.FluidInput input,
        Optional<ChemicalReactingRecipe.FluidInput> secondInput, List<FluidStackTemplate> fluids,
        Optional<ItemStackTemplate> item, Optional<Integer> time, int heatPerTick, int minTemp, int energyPerTick, Optional<Ingredient> catalyst,
        int catalystUses) implements Recipe<RefiningRecipe.Input> {
    public static final int MAX_FLUIDS = 3;

    public static MapCodec<RefiningRecipe> mapCodec(RefiningProcess process) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                ChemicalReactingRecipe.FluidInput.CODEC.fieldOf("input").forGetter(RefiningRecipe::input),
                ChemicalReactingRecipe.FluidInput.CODEC.optionalFieldOf("second_input").forGetter(RefiningRecipe::secondInput),
                FluidStackTemplate.CODEC.listOf(0, MAX_FLUIDS).optionalFieldOf("fluids", List.of()).forGetter(RefiningRecipe::fluids),
                ItemStackTemplate.CODEC.optionalFieldOf("item").forGetter(RefiningRecipe::item),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("time").forGetter(RefiningRecipe::time),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("heat_per_tick", 0).forGetter(RefiningRecipe::heatPerTick),
                Codec.intRange(0, 100_000).optionalFieldOf("min_temp", 0).forGetter(RefiningRecipe::minTemp),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("energy_per_tick", 0).forGetter(RefiningRecipe::energyPerTick),
                Ingredient.CODEC.optionalFieldOf("catalyst").forGetter(RefiningRecipe::catalyst),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("catalyst_uses", 1).forGetter(RefiningRecipe::catalystUses))
                .apply(i, (input, second, fluids, item, time, heat, minTemp, energy, catalyst, uses) ->
                        new RefiningRecipe(process, input, second, fluids, item, time, heat, minTemp, energy, catalyst, uses)));
    }

    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<Integer>> OPTIONAL_INT = ByteBufCodecs.optional(ByteBufCodecs.VAR_INT);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<FluidStackTemplate>> FLUIDS =
            FluidStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FLUIDS));
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<ItemStackTemplate>> OPTIONAL_ITEM = ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<ChemicalReactingRecipe.FluidInput>> OPTIONAL_INPUT =
            ByteBufCodecs.optional(ChemicalReactingRecipe.FluidInput.STREAM_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> OPTIONAL_INGREDIENT = ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC);

    public static StreamCodec<RegistryFriendlyByteBuf, RefiningRecipe> streamCodec(RefiningProcess process) {
        return StreamCodec.of(
                (buf, recipe) -> {
                    ChemicalReactingRecipe.FluidInput.STREAM_CODEC.encode(buf, recipe.input());
                    OPTIONAL_INPUT.encode(buf, recipe.secondInput());
                    FLUIDS.encode(buf, recipe.fluids());
                    OPTIONAL_ITEM.encode(buf, recipe.item());
                    OPTIONAL_INT.encode(buf, recipe.time());
                    ByteBufCodecs.VAR_INT.encode(buf, recipe.heatPerTick());
                    ByteBufCodecs.VAR_INT.encode(buf, recipe.minTemp());
                    ByteBufCodecs.VAR_INT.encode(buf, recipe.energyPerTick());
                    OPTIONAL_INGREDIENT.encode(buf, recipe.catalyst());
                    ByteBufCodecs.VAR_INT.encode(buf, recipe.catalystUses());
                },
                buf -> new RefiningRecipe(process, ChemicalReactingRecipe.FluidInput.STREAM_CODEC.decode(buf), OPTIONAL_INPUT.decode(buf), FLUIDS.decode(buf),
                        OPTIONAL_ITEM.decode(buf), OPTIONAL_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), OPTIONAL_INGREDIENT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
    }

    // What the machine's feed tank holds.
    public record Input(FluidResource fluid, int amount) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    // Ticks per operation before Speed upgrades.
    public int ticks() {
        return time.orElseGet(process::defaultTime);
    }

    // Whether it takes this fluid at all (what the feed tank accepts).
    public boolean takes(FluidResource fluid) {
        return input.test(fluid);
    }

    // Whether its second input is this fluid (what the second feed tank accepts).
    public boolean takesSecond(FluidResource fluid) {
        return secondInput.map(second -> second.test(fluid)).orElse(false);
    }

    // Whether a second feed tank holding this much of that fluid supplies it (always, when it takes none).
    public boolean hasSecond(FluidResource fluid, int amount) {
        return secondInput.map(second -> second.test(fluid, amount)).orElse(true);
    }

    public boolean usesCatalyst() {
        return catalyst.isPresent();
    }

    // Whether this item is its catalyst.
    public boolean isCatalyst(ItemStack stack) {
        return !stack.isEmpty() && catalyst.map(ingredient -> ingredient.test(stack)).orElse(false);
    }

    public ItemStack itemResult() {
        return item.map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean matches(Input input, Level level) {
        return this.input.test(input.fluid(), input.amount());
    }

    @Override
    public ItemStack assemble(Input input) {
        return itemResult();
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
    @SuppressWarnings("unchecked")
    public RecipeSerializer<RefiningRecipe> getSerializer() {
        return (RecipeSerializer<RefiningRecipe>) process.serializer();
    }

    @Override
    public RecipeType<RefiningRecipe> getType() {
        return process.type();
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
