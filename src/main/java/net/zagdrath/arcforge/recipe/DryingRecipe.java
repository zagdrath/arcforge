/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.Optional;

import com.mojang.serialization.DataResult;

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
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModRecipes;

// Grain Dryer (arcforge:drying): one item, or an amount of a fluid from its tank (Latex), dried into an item over `time`
// ticks, using hu_per_tick heat while it works. A recipe takes an ingredient or a fluid, never both. The dryer only works
// above farming.grainDryer.minTemperature.
public record DryingRecipe(Optional<Ingredient> ingredient, Optional<ChemicalReactingRecipe.FluidInput> fluid, ItemStackTemplate result,
        int time, int huPerTick) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 200;
    public static final int DEFAULT_HU_PER_TICK = 4;

    public static final MapCodec<DryingRecipe> MAP_CODEC = RecordCodecBuilder.<DryingRecipe>mapCodec(i -> i.group(
            Ingredient.CODEC.optionalFieldOf("ingredient").forGetter(DryingRecipe::ingredient),
            ChemicalReactingRecipe.FluidInput.CODEC.optionalFieldOf("fluid").forGetter(DryingRecipe::fluid),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(DryingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(DryingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("hu_per_tick", DEFAULT_HU_PER_TICK).forGetter(DryingRecipe::huPerTick))
            .apply(i, DryingRecipe::new))
            .validate(DryingRecipe::validate);

    public static final StreamCodec<RegistryFriendlyByteBuf, DryingRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), DryingRecipe::ingredient,
            ByteBufCodecs.optional(ChemicalReactingRecipe.FluidInput.STREAM_CODEC), DryingRecipe::fluid,
            ItemStackTemplate.STREAM_CODEC, DryingRecipe::result,
            ByteBufCodecs.VAR_INT, DryingRecipe::time,
            ByteBufCodecs.VAR_INT, DryingRecipe::huPerTick,
            DryingRecipe::new);

    private static DataResult<DryingRecipe> validate(DryingRecipe recipe) {
        if (recipe.ingredient.isPresent() == recipe.fluid.isPresent()) {
            return DataResult.error(() -> "A drying recipe takes an ingredient or a fluid, and not both");
        }
        return DataResult.success(recipe);
    }

    public boolean testItem(ItemStack stack) {
        return ingredient.isPresent() && ingredient.get().test(stack);
    }

    public boolean testFluid(FluidResource resource) {
        return fluid.isPresent() && fluid.get().test(resource);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return result.create();
    }

    // Item recipes only; the dryer looks fluid recipes up by what its tank holds (MachineRecipes.dryingFluid).
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return testItem(input.item());
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
    public RecipeSerializer<DryingRecipe> getSerializer() {
        return ModRecipes.DRYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<DryingRecipe> getType() {
        return ModRecipes.DRYING.get();
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
