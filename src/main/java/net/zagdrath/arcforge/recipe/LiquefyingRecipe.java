/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.zagdrath.arcforge.registry.ModRecipes;

// The Cryogenic Plant's (arcforge:liquefying): input gas (a fluid and how much) cooled into result (a cryogenic liquid,
// how much), at energy FE for each input amount. The plant's cold box runs fusion.cryogenics.cryoPlantRatePerBlock mB a
// tick per block inside it (see CryogenicPlantBlockEntity).
public record LiquefyingRecipe(ChemicalReactingRecipe.FluidInput input, FluidStackTemplate result, int energy) implements Recipe<CryogenicDistillingRecipe.Input> {
    public static final MapCodec<LiquefyingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.FluidInput.CODEC.fieldOf("input").forGetter(LiquefyingRecipe::input),
            FluidStackTemplate.CODEC.fieldOf("result").forGetter(LiquefyingRecipe::result),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("energy", 6_000).forGetter(LiquefyingRecipe::energy))
            .apply(i, LiquefyingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LiquefyingRecipe> STREAM_CODEC = StreamCodec.composite(
            ChemicalReactingRecipe.FluidInput.STREAM_CODEC, LiquefyingRecipe::input,
            FluidStackTemplate.STREAM_CODEC, LiquefyingRecipe::result,
            ByteBufCodecs.VAR_INT, LiquefyingRecipe::energy,
            LiquefyingRecipe::new);

    public boolean takes(FluidResource fluid) {
        return input.test(fluid);
    }

    @Override
    public boolean matches(CryogenicDistillingRecipe.Input in, Level level) {
        return takes(in.fluid());
    }

    @Override
    public ItemStack assemble(CryogenicDistillingRecipe.Input input) {
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
    public RecipeSerializer<LiquefyingRecipe> getSerializer() {
        return ModRecipes.LIQUEFYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<LiquefyingRecipe> getType() {
        return ModRecipes.LIQUEFYING.get();
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
