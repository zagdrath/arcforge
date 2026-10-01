/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.Optional;

import com.mojang.serialization.DataResult;
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
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModRecipes;

// Infuser: one item soaked in a fluid (planks + 50 mB creosote -> treated planks), or worked with an additive item in
// the Infuser's additive slot instead (planks + 1 Pine Resin -> treated planks). A recipe takes a fluid or an additive,
// never both. The fluid or the additive is used up when the item is done. Any fluid or item works.
public record InfusingRecipe(Ingredient ingredient, Optional<FluidStackTemplate> fluid, Optional<ChemicalReactingRecipe.ItemInput> additive,
        ItemStackTemplate result, int time) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 100;

    public static final MapCodec<InfusingRecipe> MAP_CODEC = RecordCodecBuilder.<InfusingRecipe>mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(InfusingRecipe::ingredient),
            FluidStackTemplate.CODEC.optionalFieldOf("fluid").forGetter(InfusingRecipe::fluid),
            ChemicalReactingRecipe.ItemInput.CODEC.optionalFieldOf("additive").forGetter(InfusingRecipe::additive),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(InfusingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(InfusingRecipe::time))
            .apply(i, InfusingRecipe::new))
            .validate(InfusingRecipe::validate);

    public static final StreamCodec<RegistryFriendlyByteBuf, InfusingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, InfusingRecipe::ingredient,
            ByteBufCodecs.optional(FluidStackTemplate.STREAM_CODEC), InfusingRecipe::fluid,
            ByteBufCodecs.optional(ChemicalReactingRecipe.ItemInput.STREAM_CODEC), InfusingRecipe::additive,
            ItemStackTemplate.STREAM_CODEC, InfusingRecipe::result,
            ByteBufCodecs.VAR_INT, InfusingRecipe::time,
            InfusingRecipe::new);

    private static DataResult<InfusingRecipe> validate(InfusingRecipe recipe) {
        if (recipe.fluid.isPresent() == recipe.additive.isPresent()) {
            return DataResult.error(() -> "An infusing recipe takes a fluid or an additive, and not both");
        }
        return DataResult.success(recipe);
    }

    // Only the item is matched here; the machine checks the fluid or the additive, so it can say which one is missing.
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    public boolean usesFluid(FluidResource resource) {
        return fluid.isPresent() && !resource.isEmpty() && resource.getFluid() == fluid.get().fluid().value();
    }

    // The fluid's amount, or 0 for an additive recipe.
    public int fluidAmount() {
        return fluid.map(FluidStackTemplate::amount).orElse(0);
    }

    public boolean usesAdditive(ItemStack stack) {
        return additive.isPresent() && additive.get().ingredient().test(stack);
    }

    // Whether this additive stack is enough for one item.
    public boolean hasAdditive(ItemStack stack) {
        return additive.isPresent() && additive.get().test(stack);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
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
    public RecipeSerializer<InfusingRecipe> getSerializer() {
        return ModRecipes.INFUSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<InfusingRecipe> getType() {
        return ModRecipes.INFUSING.get();
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
