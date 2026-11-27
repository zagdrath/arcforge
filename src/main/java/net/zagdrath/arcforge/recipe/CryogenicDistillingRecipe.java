/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.zagdrath.arcforge.registry.ModRecipes;

// The Cryogenic Distillation Column's (arcforge:cryogenic_distilling): input (a fluid and how much; none: the air round
// it, drawn in) chilled and split into up to three outputs, one per tank, every time ticks. The column's height runs
// that many at once (see CryogenicColumnBlockEntity).
public record CryogenicDistillingRecipe(Optional<ChemicalReactingRecipe.FluidInput> input, List<FluidStackTemplate> outputs, int time)
        implements Recipe<CryogenicDistillingRecipe.Input> {
    public static final int MAX_OUTPUTS = 3;

    public static final MapCodec<CryogenicDistillingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ChemicalReactingRecipe.FluidInput.CODEC.optionalFieldOf("input").forGetter(CryogenicDistillingRecipe::input),
            FluidStackTemplate.CODEC.listOf(1, MAX_OUTPUTS).fieldOf("outputs").forGetter(CryogenicDistillingRecipe::outputs),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", 20).forGetter(CryogenicDistillingRecipe::time))
            .apply(i, CryogenicDistillingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CryogenicDistillingRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ChemicalReactingRecipe.FluidInput.STREAM_CODEC), CryogenicDistillingRecipe::input,
            FluidStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OUTPUTS)), CryogenicDistillingRecipe::outputs,
            ByteBufCodecs.VAR_INT, CryogenicDistillingRecipe::time,
            CryogenicDistillingRecipe::new);

    // What the column's feed tank holds (empty: it runs on air).
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

    public boolean isAir() {
        return input.isEmpty();
    }

    public boolean takes(FluidResource fluid) {
        return input.isPresent() && input.get().test(fluid);
    }

    @Override
    public boolean matches(Input in, Level level) {
        return in.fluid().isEmpty() ? input.isEmpty() : takes(in.fluid());
    }

    @Override
    public ItemStack assemble(Input input) {
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
    public RecipeSerializer<CryogenicDistillingRecipe> getSerializer() {
        return ModRecipes.CRYOGENIC_DISTILLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CryogenicDistillingRecipe> getType() {
        return ModRecipes.CRYOGENIC_DISTILLING.get();
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
