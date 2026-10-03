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

// Chemical Reactor: up to three items (each with a count) and up to three fluids react into an item, a fluid or both, with
// an optional byproduct rolled once per operation (Slag, when precipitating), and an optional fluid_byproduct that always
// comes (the Water left when Ethanol is dehydrated into Ethylene), into the reactor's by-product tank. The items are
// item_input, second_item_input and third_item_input, each drawn from a different input slot; each fluid input from a
// different input tank. "category" only labels the recipe in JEI: general, leaching or precipitating.
public record ChemicalReactingRecipe(Optional<ItemInput> itemInput, Optional<ItemInput> secondItemInput, List<FluidInput> fluidInputs,
        Optional<ItemStackTemplate> itemOutput,
        Optional<FluidStackTemplate> fluidOutput, Optional<ItemStackTemplate> byproduct, float byproductChance, int time,
        Optional<Integer> energyPerTick, String category, Optional<FluidStackTemplate> fluidByproduct, Optional<ItemInput> thirdItemInput)
        implements Recipe<ChemicalReactorInput> {
    public static final int DEFAULT_TIME = 100;
    public static final int MAX_FLUID_INPUTS = ChemicalReactorInput.TANKS;

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
            ItemInput.CODEC.optionalFieldOf("second_item_input").forGetter(ChemicalReactingRecipe::secondItemInput),
            FluidInput.CODEC.listOf(0, MAX_FLUID_INPUTS).optionalFieldOf("fluid_inputs", List.of()).forGetter(ChemicalReactingRecipe::fluidInputs),
            ItemStackTemplate.CODEC.optionalFieldOf("item_output").forGetter(ChemicalReactingRecipe::itemOutput),
            FluidStackTemplate.CODEC.optionalFieldOf("fluid_output").forGetter(ChemicalReactingRecipe::fluidOutput),
            ItemStackTemplate.CODEC.optionalFieldOf("byproduct").forGetter(ChemicalReactingRecipe::byproduct),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("byproduct_chance", 1.0F).forGetter(ChemicalReactingRecipe::byproductChance),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(ChemicalReactingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy_per_tick").forGetter(ChemicalReactingRecipe::energyPerTick),
            Codec.STRING.optionalFieldOf("category", "general").forGetter(ChemicalReactingRecipe::category),
            FluidStackTemplate.CODEC.optionalFieldOf("fluid_byproduct").forGetter(ChemicalReactingRecipe::fluidByproduct),
            ItemInput.CODEC.optionalFieldOf("third_item_input").forGetter(ChemicalReactingRecipe::thirdItemInput))
            .apply(i, ChemicalReactingRecipe::new))
            .validate(ChemicalReactingRecipe::validate);

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<ItemInput>> OPTIONAL_ITEM = ByteBufCodecs.optional(ItemInput.STREAM_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<FluidInput>> FLUID_INPUTS = FluidInput.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FLUID_INPUTS));
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<ItemStackTemplate>> OPTIONAL_TEMPLATE = ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<FluidStackTemplate>> OPTIONAL_FLUID = ByteBufCodecs.optional(FluidStackTemplate.STREAM_CODEC);
    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<Integer>> OPTIONAL_INT = ByteBufCodecs.optional(ByteBufCodecs.VAR_INT);

    // Written out by hand: twelve fields.
    public static final StreamCodec<RegistryFriendlyByteBuf, ChemicalReactingRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                OPTIONAL_ITEM.encode(buf, recipe.itemInput());
                OPTIONAL_ITEM.encode(buf, recipe.secondItemInput());
                FLUID_INPUTS.encode(buf, recipe.fluidInputs());
                OPTIONAL_TEMPLATE.encode(buf, recipe.itemOutput());
                OPTIONAL_FLUID.encode(buf, recipe.fluidOutput());
                OPTIONAL_TEMPLATE.encode(buf, recipe.byproduct());
                buf.writeFloat(recipe.byproductChance());
                ByteBufCodecs.VAR_INT.encode(buf, recipe.time());
                OPTIONAL_INT.encode(buf, recipe.energyPerTick());
                ByteBufCodecs.STRING_UTF8.encode(buf, recipe.category());
                OPTIONAL_FLUID.encode(buf, recipe.fluidByproduct());
                OPTIONAL_ITEM.encode(buf, recipe.thirdItemInput());
            },
            buf -> new ChemicalReactingRecipe(OPTIONAL_ITEM.decode(buf), OPTIONAL_ITEM.decode(buf), FLUID_INPUTS.decode(buf),
                    OPTIONAL_TEMPLATE.decode(buf), OPTIONAL_FLUID.decode(buf), OPTIONAL_TEMPLATE.decode(buf), buf.readFloat(),
                    ByteBufCodecs.VAR_INT.decode(buf), OPTIONAL_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), OPTIONAL_FLUID.decode(buf),
                    OPTIONAL_ITEM.decode(buf)));

    // Something goes in and something comes out.
    private static DataResult<ChemicalReactingRecipe> validate(ChemicalReactingRecipe recipe) {
        if (recipe.itemInput.isEmpty() && recipe.fluidInputs.isEmpty()) {
            return DataResult.error(() -> "A chemical_reacting recipe needs an item_input or a fluid_input");
        }
        if (recipe.secondItemInput.isPresent() && recipe.itemInput.isEmpty()) {
            return DataResult.error(() -> "A chemical_reacting recipe with a second_item_input needs an item_input");
        }
        if (recipe.thirdItemInput.isPresent() && recipe.secondItemInput.isEmpty()) {
            return DataResult.error(() -> "A chemical_reacting recipe with a third_item_input needs a second_item_input");
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
        return slotsFor(input) != null && tanksFor(input) != null;
    }

    // Which input slot (0, 1 or 2) supplies each item input (item_input, second_item_input, then third_item_input), each
    // from a different slot, or null if they can't all be supplied. Any item may come from any slot: the slots are tried
    // in order for the first input, then for the next among those left, backtracking like the tanks.
    public int @Nullable [] slotsFor(ChemicalReactorInput input) {
        List<ItemInput> wanted = itemInputs();
        int[] slots = new int[wanted.size()];
        return assignSlots(input, wanted, 0, 0, slots) ? slots : null;
    }

    private static boolean assignSlots(ChemicalReactorInput input, List<ItemInput> wanted, int index, int used, int[] slots) {
        if (index == wanted.size()) {
            return true;
        }
        for (int slot = 0; slot < ChemicalReactorInput.SLOTS; slot++) {
            if ((used & 1 << slot) == 0 && wanted.get(index).test(input.slot(slot))) {
                slots[index] = slot;
                if (assignSlots(input, wanted, index + 1, used | 1 << slot, slots)) {
                    return true;
                }
            }
        }
        return false;
    }

    // The item inputs in order (none to three).
    public List<ItemInput> itemInputs() {
        return java.util.stream.Stream.of(itemInput, secondItemInput, thirdItemInput).flatMap(Optional::stream).toList();
    }

    // Which input tank (0, 1 or 2) supplies each fluid input, each from a different tank, or null if they can't all be
    // supplied. Tanks are tried in order for the first input, then for the next among those left, and so on.
    public int @Nullable [] tanksFor(ChemicalReactorInput input) {
        int[] tanks = new int[fluidInputs.size()];
        return assign(input, 0, 0, tanks) ? tanks : null;
    }

    // Gives fluid input `index` a tank not in `used` (a bit mask), then the rest; backtracks when one can't be supplied.
    private boolean assign(ChemicalReactorInput input, int index, int used, int[] tanks) {
        if (index == fluidInputs.size()) {
            return true;
        }
        FluidInput wanted = fluidInputs.get(index);
        for (int tank = 0; tank < ChemicalReactorInput.TANKS; tank++) {
            if ((used & 1 << tank) == 0 && wanted.test(input.fluid(tank), input.amount(tank))) {
                tanks[index] = tank;
                if (assign(input, index + 1, used | 1 << tank, tanks)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Whether this item could be part of the recipe (ignoring the count), for the input slot.
    public boolean usesItem(ItemStack stack) {
        return itemInputs().stream().anyMatch(input -> input.ingredient().test(stack));
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
