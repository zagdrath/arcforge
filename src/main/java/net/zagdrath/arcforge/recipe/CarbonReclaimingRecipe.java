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
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModRecipes;

// Carbon Reclaimer (arcforge:carbon_reclaiming): `carbon_dioxide` mB of Carbon Dioxide and `hydrogen` mB of Hydrogen
// become the result (Carbon Dust) and `water` mB of Water, for `energy` FE. Every field but the result is optional and
// follows the machines.chemistry.carbonReclaimer config when left out, so the built-in recipe is tuned there. Whatever the energy,
// the machine never charges less than EnergyBalance.minEnergyFor (the most FE the result could give back, with a safety
// factor), so it renews carbon, never energy.
public record CarbonReclaimingRecipe(Optional<Integer> carbonDioxide, Optional<Integer> hydrogen, ItemStackTemplate result,
        Optional<Integer> water, Optional<Integer> energy) implements Recipe<CarbonReclaimingRecipe.Input> {
    public static final MapCodec<CarbonReclaimingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("carbon_dioxide").forGetter(CarbonReclaimingRecipe::carbonDioxide),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("hydrogen").forGetter(CarbonReclaimingRecipe::hydrogen),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(CarbonReclaimingRecipe::result),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("water").forGetter(CarbonReclaimingRecipe::water),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy").forGetter(CarbonReclaimingRecipe::energy))
            .apply(i, CarbonReclaimingRecipe::new));

    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<Integer>> OPTIONAL_INT = ByteBufCodecs.optional(ByteBufCodecs.VAR_INT);

    public static final StreamCodec<RegistryFriendlyByteBuf, CarbonReclaimingRecipe> STREAM_CODEC = StreamCodec.composite(
            OPTIONAL_INT, CarbonReclaimingRecipe::carbonDioxide,
            OPTIONAL_INT, CarbonReclaimingRecipe::hydrogen,
            ItemStackTemplate.STREAM_CODEC, CarbonReclaimingRecipe::result,
            OPTIONAL_INT, CarbonReclaimingRecipe::water,
            OPTIONAL_INT, CarbonReclaimingRecipe::energy,
            CarbonReclaimingRecipe::new);

    // What the Carbon Reclaimer's two gas tanks hold, in mB.
    public record Input(int carbonDioxide, int hydrogen) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 0;
        }
    }

    public int carbonDioxideAmount() {
        return carbonDioxide.orElseGet(ArcforgeConfig.RECLAIMER_CARBON_DIOXIDE::getAsInt);
    }

    public int hydrogenAmount() {
        return hydrogen.orElseGet(ArcforgeConfig.RECLAIMER_HYDROGEN::getAsInt);
    }

    public int waterAmount() {
        return water.orElseGet(ArcforgeConfig.RECLAIMER_WATER::getAsInt);
    }

    // FE per operation before Energy upgrades and the balance floor.
    public int totalEnergy() {
        return energy.orElseGet(ArcforgeConfig.RECLAIMER_ENERGY_PER_OPERATION::getAsInt);
    }

    @Override
    public boolean matches(Input input, Level level) {
        return input.carbonDioxide() >= carbonDioxideAmount() && input.hydrogen() >= hydrogenAmount();
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
    public RecipeSerializer<CarbonReclaimingRecipe> getSerializer() {
        return ModRecipes.CARBON_RECLAIMING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CarbonReclaimingRecipe> getType() {
        return ModRecipes.CARBON_RECLAIMING.get();
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
