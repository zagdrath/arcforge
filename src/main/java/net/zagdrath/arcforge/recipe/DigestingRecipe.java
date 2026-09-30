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
import net.zagdrath.arcforge.registry.ModRecipes;

// Biogas Digester (arcforge:digesting): one item of plant matter digested in `water` mB of water over `time` ticks into
// a gas (Biogas), with an optional by-product (Digestate) rolled once per item. Each of the digester's lanes digests one
// item at a time, and it only works while warm (biogasDigester.minTemperature).
public record DigestingRecipe(Ingredient ingredient, int water, FluidStackTemplate result, Optional<ItemStackTemplate> byproduct,
        float byproductChance, int time) implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_WATER = 100;
    public static final int DEFAULT_TIME = 200;

    public static final MapCodec<DigestingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(DigestingRecipe::ingredient),
            Codec.intRange(0, 1_000_000).optionalFieldOf("water", DEFAULT_WATER).forGetter(DigestingRecipe::water),
            FluidStackTemplate.CODEC.fieldOf("result").forGetter(DigestingRecipe::result),
            ItemStackTemplate.CODEC.optionalFieldOf("byproduct").forGetter(DigestingRecipe::byproduct),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("byproduct_chance", 1.0F).forGetter(DigestingRecipe::byproductChance),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(DigestingRecipe::time))
            .apply(i, DigestingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DigestingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, DigestingRecipe::ingredient,
            ByteBufCodecs.VAR_INT, DigestingRecipe::water,
            FluidStackTemplate.STREAM_CODEC, DigestingRecipe::result,
            ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC), DigestingRecipe::byproduct,
            ByteBufCodecs.FLOAT, DigestingRecipe::byproductChance,
            ByteBufCodecs.VAR_INT, DigestingRecipe::time,
            DigestingRecipe::new);

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    // The result is a gas; the digester fills its tank itself.
    @Override
    public ItemStack assemble(SingleRecipeInput input) {
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
    public RecipeSerializer<DigestingRecipe> getSerializer() {
        return ModRecipes.DIGESTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<DigestingRecipe> getType() {
        return ModRecipes.DIGESTING.get();
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
