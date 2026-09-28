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

// Electrolyzer: one fluid split into two with FE (100 mB water -> 200 mB hydrogen + 100 mB oxygen). The primary
// output goes to the Electrolyzer's "hydrogen" tank and the secondary to its "oxygen" tank, whatever they are. The
// FE is energyPerMbInput for each mB of input unless the recipe gives its own "energy"; the Electrolyzer never
// charges less than EnergyBalance.minEnergyFor, so what it makes can't be burnt for more FE than it cost.
public record ElectrolyzingRecipe(ChemicalReactingRecipe.FluidInput input, FluidStackTemplate primary,
        Optional<FluidStackTemplate> secondary, Optional<Integer> energy) implements Recipe<ElectrolyzingRecipe.Input> {
    public static final MapCodec<ElectrolyzingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.FluidInput.CODEC.fieldOf("input").forGetter(ElectrolyzingRecipe::input),
            FluidStackTemplate.CODEC.fieldOf("primary").forGetter(ElectrolyzingRecipe::primary),
            FluidStackTemplate.CODEC.optionalFieldOf("secondary").forGetter(ElectrolyzingRecipe::secondary),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy").forGetter(ElectrolyzingRecipe::energy))
            .apply(i, ElectrolyzingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ElectrolyzingRecipe> STREAM_CODEC = StreamCodec.composite(
            ChemicalReactingRecipe.FluidInput.STREAM_CODEC, ElectrolyzingRecipe::input,
            FluidStackTemplate.STREAM_CODEC, ElectrolyzingRecipe::primary,
            ByteBufCodecs.optional(FluidStackTemplate.STREAM_CODEC), ElectrolyzingRecipe::secondary,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), ElectrolyzingRecipe::energy,
            ElectrolyzingRecipe::new);

    // What the Electrolyzer's water tank holds.
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

    // FE for one operation, before Energy upgrades and the balance floor.
    public int totalEnergy() {
        return energy.orElseGet(() -> input.amount() * ArcforgeConfig.ELECTROLYZER_ENERGY_PER_MB_INPUT.getAsInt());
    }

    @Override
    public boolean matches(Input input, Level level) {
        return this.input.test(input.fluid());
    }

    // The outputs are fluids; the Electrolyzer fills its tanks itself.
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
    public RecipeSerializer<ElectrolyzingRecipe> getSerializer() {
        return ModRecipes.ELECTROLYZING_SERIALIZER.get();
    }

    @Override
    public RecipeType<ElectrolyzingRecipe> getType() {
        return ModRecipes.ELECTROLYZING.get();
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
