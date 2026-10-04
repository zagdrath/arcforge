/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.Optional;

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
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModRecipes;

// Oil Press (arcforge:oil_pressing): one seed pressed into oil (a fluid) and a cake, over `time` ticks. The cake comes
// with byproduct_chance (1 = always). FE per item is time x farming.cropProcessing.oilPress.energyPerTick unless the recipe sets its
// own `energy`.
public record OilPressingRecipe(Ingredient ingredient, FluidStackTemplate result, Optional<ItemStackTemplate> byproduct, float byproductChance,
        int time, Optional<Integer> energy) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 100;

    public static final MapCodec<OilPressingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(OilPressingRecipe::ingredient),
            FluidStackTemplate.CODEC.fieldOf("result").forGetter(OilPressingRecipe::result),
            ItemStackTemplate.CODEC.optionalFieldOf("byproduct").forGetter(OilPressingRecipe::byproduct),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("byproduct_chance", 1.0F).forGetter(OilPressingRecipe::byproductChance),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(OilPressingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy").forGetter(OilPressingRecipe::energy))
            .apply(i, OilPressingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, OilPressingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, OilPressingRecipe::ingredient,
            FluidStackTemplate.STREAM_CODEC, OilPressingRecipe::result,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), OilPressingRecipe::byproduct,
            ByteBufCodecs.FLOAT, OilPressingRecipe::byproductChance,
            ByteBufCodecs.VAR_INT, OilPressingRecipe::time,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), OilPressingRecipe::energy,
            OilPressingRecipe::new);

    // FE for one item, before Energy upgrades.
    public int totalEnergy() {
        return energy.orElseGet(() -> time * ArcforgeConfig.OIL_PRESS_ENERGY_PER_TICK.getAsInt());
    }

    // The result is a fluid; the press fills its tank itself.
    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
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
    public RecipeSerializer<OilPressingRecipe> getSerializer() {
        return ModRecipes.OIL_PRESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<OilPressingRecipe> getType() {
        return ModRecipes.OIL_PRESSING.get();
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
