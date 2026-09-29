/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModRecipes;

// A Fermenter recipe (arcforge:fermenting): a crop and some water become ethanol over `time` ticks, sometimes with a
// byproduct item. FE per operation defaults to time x fermenter.energyPerTick.
public record FermentingRecipe(Ingredient ingredient, FluidAmount fluidInput, FluidStackTemplate result, Optional<ItemStackTemplate> byproduct,
        float byproductChance, int time, Optional<Integer> energy) implements Recipe<SingleRecipeInput> {
    // A fluid and how much of it: {"fluid": id, "amount": mB}.
    public record FluidAmount(Fluid fluid, int amount) {
        public static final Codec<FluidAmount> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidAmount::fluid),
                ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(FluidAmount::amount))
                .apply(i, FluidAmount::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidAmount> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(net.minecraft.core.registries.Registries.FLUID), FluidAmount::fluid,
                ByteBufCodecs.VAR_INT, FluidAmount::amount,
                FluidAmount::new);
    }

    public static final MapCodec<FermentingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(FermentingRecipe::ingredient),
            FluidAmount.CODEC.fieldOf("fluid_input").forGetter(FermentingRecipe::fluidInput),
            FluidStackTemplate.CODEC.fieldOf("result").forGetter(FermentingRecipe::result),
            ItemStackTemplate.CODEC.optionalFieldOf("byproduct").forGetter(FermentingRecipe::byproduct),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("byproduct_chance", 0.0F).forGetter(FermentingRecipe::byproductChance),
            ExtraCodecs.POSITIVE_INT.fieldOf("time").forGetter(FermentingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy").forGetter(FermentingRecipe::energy))
            .apply(i, FermentingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FermentingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, FermentingRecipe::ingredient,
            FluidAmount.STREAM_CODEC, FermentingRecipe::fluidInput,
            FluidStackTemplate.STREAM_CODEC, FermentingRecipe::result,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), FermentingRecipe::byproduct,
            ByteBufCodecs.FLOAT, FermentingRecipe::byproductChance,
            ByteBufCodecs.VAR_INT, FermentingRecipe::time,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), FermentingRecipe::energy,
            FermentingRecipe::new);

    // FE for one operation, before Energy upgrades.
    public int totalEnergy() {
        return energy.orElseGet(() -> time * ArcforgeConfig.FERMENTER_ENERGY_PER_TICK.getAsInt());
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    // The result is a fluid; the Fermenter fills its tank itself.
    @Override
    public ItemStack assemble(SingleRecipeInput input) {
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
    public RecipeSerializer<FermentingRecipe> getSerializer() {
        return ModRecipes.FERMENTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<FermentingRecipe> getType() {
        return ModRecipes.FERMENTING.get();
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
