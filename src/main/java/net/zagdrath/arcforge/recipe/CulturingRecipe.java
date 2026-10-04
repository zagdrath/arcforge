/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

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
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModRecipes;

// A Fermenter culture (arcforge:culturing): one or two item ingredients (in either of the Fermenter's two input slots)
// and some water grow `result` over `time` ticks, fed to a starter culture in the additive slot that is never used up
// (a Slimeball grows Slimeballs). No Ethanol or Carbon Dioxide comes of it, and Dried Hops don't change it. FE per
// operation defaults to time x fermenter.energyPerTick, as for fermenting.
public record CulturingRecipe(List<ChemicalReactingRecipe.ItemInput> ingredients, FermentingRecipe.FluidAmount fluidInput, Ingredient culture,
        ItemStackTemplate result, int time, Optional<Integer> energy) implements Recipe<CulturingRecipe.Input> {
    // The two input slots and the additive slot.
    public record Input(ItemStack first, ItemStack second, ItemStack culture) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return switch (index) {
                case 0 -> first;
                case 1 -> second;
                default -> culture;
            };
        }

        @Override
        public int size() {
            return 3;
        }
    }

    public static final MapCodec<CulturingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.ItemInput.CODEC.listOf(1, 2).fieldOf("ingredients").forGetter(CulturingRecipe::ingredients),
            FermentingRecipe.FluidAmount.CODEC.fieldOf("fluid_input").forGetter(CulturingRecipe::fluidInput),
            Ingredient.CODEC.fieldOf("culture").forGetter(CulturingRecipe::culture),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(CulturingRecipe::result),
            ExtraCodecs.POSITIVE_INT.fieldOf("time").forGetter(CulturingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy").forGetter(CulturingRecipe::energy))
            .apply(i, CulturingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CulturingRecipe> STREAM_CODEC = StreamCodec.composite(
            ChemicalReactingRecipe.ItemInput.STREAM_CODEC.apply(ByteBufCodecs.list(2)), CulturingRecipe::ingredients,
            FermentingRecipe.FluidAmount.STREAM_CODEC, CulturingRecipe::fluidInput,
            Ingredient.CONTENTS_STREAM_CODEC, CulturingRecipe::culture,
            ItemStackTemplate.STREAM_CODEC, CulturingRecipe::result,
            ByteBufCodecs.VAR_INT, CulturingRecipe::time,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), CulturingRecipe::energy,
            CulturingRecipe::new);

    // FE for one operation, before Energy upgrades.
    public int totalEnergy() {
        return energy.orElseGet(() -> time * ArcforgeConfig.FERMENTER_ENERGY_PER_TICK.getAsInt());
    }

    // Which input slot (0 or 1) each ingredient is taken from, or null if the two slots don't hold them all. A
    // one-ingredient recipe takes it from either slot.
    public int @Nullable [] slotsFor(ItemStack first, ItemStack second) {
        ChemicalReactingRecipe.ItemInput a = ingredients.get(0);
        if (ingredients.size() == 1) {
            return a.test(first) ? new int[] { 0 } : a.test(second) ? new int[] { 1 } : null;
        }
        ChemicalReactingRecipe.ItemInput b = ingredients.get(1);
        if (a.test(first) && b.test(second)) {
            return new int[] { 0, 1 };
        }
        if (a.test(second) && b.test(first)) {
            return new int[] { 1, 0 };
        }
        return null;
    }

    // Whether this item is one of the recipe's ingredients (what the input slots take for it).
    public boolean usesIngredient(ItemStack stack) {
        return ingredients.stream().anyMatch(input -> input.ingredient().test(stack));
    }

    @Override
    public boolean matches(Input input, Level level) {
        return culture.test(input.culture()) && slotsFor(input.first(), input.second()) != null;
    }

    // The result goes into the Fermenter's output slot, which it fills itself.
    @Override
    public ItemStack assemble(Input input) {
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
    public RecipeSerializer<CulturingRecipe> getSerializer() {
        return ModRecipes.CULTURING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CulturingRecipe> getType() {
        return ModRecipes.CULTURING.get();
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
