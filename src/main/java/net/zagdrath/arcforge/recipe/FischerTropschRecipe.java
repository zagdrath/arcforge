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
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModRecipes;

// Fischer-Tropsch Reactor (arcforge:fischer_tropsch): the input (Syngas, with its amount) becomes `naphtha`, `light_oil`,
// `heavy_oil` and `water` mB of those four (each optional, 0 when left out), over `time` ticks at `energy_per_tick` FE/t and
// `heat_per_tick` HU/t, between machines.chemistry.fischerTropschReactor.minTemperature and maxOperatingTemperature. time,
// energy_per_tick and heat_per_tick follow that config when left out.
public record FischerTropschRecipe(ChemicalReactingRecipe.FluidInput input, int naphtha, int lightOil, int heavyOil, int water,
        Optional<Integer> energyPerTick, Optional<Integer> heatPerTick, Optional<Integer> time) implements Recipe<FischerTropschRecipe.Input> {
    public static final MapCodec<FischerTropschRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.FluidInput.CODEC.fieldOf("input").forGetter(FischerTropschRecipe::input),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("naphtha", 0).forGetter(FischerTropschRecipe::naphtha),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("light_oil", 0).forGetter(FischerTropschRecipe::lightOil),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("heavy_oil", 0).forGetter(FischerTropschRecipe::heavyOil),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("water", 0).forGetter(FischerTropschRecipe::water),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("energy_per_tick").forGetter(FischerTropschRecipe::energyPerTick),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("heat_per_tick").forGetter(FischerTropschRecipe::heatPerTick),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time").forGetter(FischerTropschRecipe::time))
            .apply(i, FischerTropschRecipe::new));

    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<Integer>> OPTIONAL_INT = ByteBufCodecs.optional(ByteBufCodecs.VAR_INT);

    // Written out by hand: eight fields.
    public static final StreamCodec<RegistryFriendlyByteBuf, FischerTropschRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                ChemicalReactingRecipe.FluidInput.STREAM_CODEC.encode(buf, recipe.input());
                ByteBufCodecs.VAR_INT.encode(buf, recipe.naphtha());
                ByteBufCodecs.VAR_INT.encode(buf, recipe.lightOil());
                ByteBufCodecs.VAR_INT.encode(buf, recipe.heavyOil());
                ByteBufCodecs.VAR_INT.encode(buf, recipe.water());
                OPTIONAL_INT.encode(buf, recipe.energyPerTick());
                OPTIONAL_INT.encode(buf, recipe.heatPerTick());
                OPTIONAL_INT.encode(buf, recipe.time());
            },
            buf -> new FischerTropschRecipe(ChemicalReactingRecipe.FluidInput.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    OPTIONAL_INT.decode(buf), OPTIONAL_INT.decode(buf), OPTIONAL_INT.decode(buf)));

    // What the reactor's Syngas tank holds.
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

    // FE/t before Speed and Energy upgrades.
    public int baseEnergyPerTick() {
        return energyPerTick.orElseGet(ArcforgeConfig.FT_ENERGY_PER_TICK::getAsInt);
    }

    // HU/t before Speed upgrades.
    public int baseHeatPerTick() {
        return heatPerTick.orElseGet(ArcforgeConfig.FT_HEAT_PER_TICK::getAsInt);
    }

    // Ticks per operation before Speed upgrades.
    public int ticks() {
        return time.orElseGet(ArcforgeConfig.FT_TIME::getAsInt);
    }

    // The products in tank order: Naphtha, Light Oil, Heavy Oil, Water.
    public int[] products() {
        return new int[] { naphtha, lightOil, heavyOil, water };
    }

    // Whether it takes this fluid at all (what the Syngas tank accepts).
    public boolean takes(FluidResource fluid) {
        return input.test(fluid);
    }

    @Override
    public boolean matches(Input input, Level level) {
        return this.input.test(input.fluid(), input.amount());
    }

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
    public RecipeSerializer<FischerTropschRecipe> getSerializer() {
        return ModRecipes.FISCHER_TROPSCH_SERIALIZER.get();
    }

    @Override
    public RecipeType<FischerTropschRecipe> getType() {
        return ModRecipes.FISCHER_TROPSCH.get();
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
