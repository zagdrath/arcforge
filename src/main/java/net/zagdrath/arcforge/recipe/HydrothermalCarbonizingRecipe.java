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
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModRecipes;

// Hydrothermal Carbonizer (arcforge:hydrothermal_carbonizing): `count` of an ingredient (#arcforge:biomass) cooked in
// `water` mB of Water with `heat` HU over `time` ticks into the result (Bio-Coal), giving `water_return` mB of the water
// back. count, heat and time are optional: left out, they follow the machines.chemistry.hydrothermalCarbonizer config
// (biomassPerBioCoal, heatPerOperation, time), so the built-in recipe is tuned there.
public record HydrothermalCarbonizingRecipe(Ingredient ingredient, Optional<Integer> count, ItemStackTemplate result, int water, int waterReturn,
        Optional<Integer> heat, Optional<Integer> time) implements Recipe<SingleRecipeInput> {
    public static final MapCodec<HydrothermalCarbonizingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(HydrothermalCarbonizingRecipe::ingredient),
            Codec.intRange(1, 64).optionalFieldOf("count").forGetter(HydrothermalCarbonizingRecipe::count),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(HydrothermalCarbonizingRecipe::result),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("water", 0).forGetter(HydrothermalCarbonizingRecipe::water),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("water_return", 0).forGetter(HydrothermalCarbonizingRecipe::waterReturn),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("heat").forGetter(HydrothermalCarbonizingRecipe::heat),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time").forGetter(HydrothermalCarbonizingRecipe::time))
            .apply(i, HydrothermalCarbonizingRecipe::new));

    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<Integer>> OPTIONAL_INT = ByteBufCodecs.optional(ByteBufCodecs.VAR_INT);

    // Written out by hand: seven fields.
    public static final StreamCodec<RegistryFriendlyByteBuf, HydrothermalCarbonizingRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.ingredient());
                OPTIONAL_INT.encode(buf, recipe.count());
                ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.result());
                ByteBufCodecs.VAR_INT.encode(buf, recipe.water());
                ByteBufCodecs.VAR_INT.encode(buf, recipe.waterReturn());
                OPTIONAL_INT.encode(buf, recipe.heat());
                OPTIONAL_INT.encode(buf, recipe.time());
            },
            buf -> new HydrothermalCarbonizingRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(buf), OPTIONAL_INT.decode(buf),
                    ItemStackTemplate.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    OPTIONAL_INT.decode(buf), OPTIONAL_INT.decode(buf)));

    // Ingredient items per operation (the config's biomassPerBioCoal unless the recipe sets it).
    public int inputCount() {
        return count.orElseGet(ArcforgeConfig.HYDROTHERMAL_BIOMASS_PER_BIO_COAL::getAsInt);
    }

    // HU per operation before Heat upgrades.
    public int heatPerOperation() {
        return heat.orElseGet(ArcforgeConfig.HYDROTHERMAL_HEAT_PER_OPERATION::getAsInt);
    }

    // Ticks per operation before Speed upgrades.
    public int ticks() {
        return time.orElseGet(ArcforgeConfig.HYDROTHERMAL_TIME::getAsInt);
    }

    public boolean test(ItemStack stack) {
        return ingredient.test(stack);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return test(input.item());
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
    public RecipeSerializer<HydrothermalCarbonizingRecipe> getSerializer() {
        return ModRecipes.HYDROTHERMAL_CARBONIZING_SERIALIZER.get();
    }

    @Override
    public RecipeType<HydrothermalCarbonizingRecipe> getType() {
        return ModRecipes.HYDROTHERMAL_CARBONIZING.get();
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
