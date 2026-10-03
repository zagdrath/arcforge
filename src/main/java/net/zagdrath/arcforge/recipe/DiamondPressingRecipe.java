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

// Diamond Press (arcforge:diamond_pressing): `count` of the ingredient (Graphite) pressed into the result (a Diamond)
// with `energy` FE and `heat` HU over `time` ticks, at `min_temperature` °C or hotter. Every field but the ingredient and
// result is optional and follows machines.diamondPress when left out, so the built-in recipe is tuned there.
public record DiamondPressingRecipe(Ingredient ingredient, Optional<Integer> count, ItemStackTemplate result, Optional<Integer> energy,
        Optional<Integer> heat, Optional<Integer> time, Optional<Integer> minTemperature) implements Recipe<SingleRecipeInput> {
    public static final MapCodec<DiamondPressingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(DiamondPressingRecipe::ingredient),
            Codec.intRange(1, 64).optionalFieldOf("count").forGetter(DiamondPressingRecipe::count),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(DiamondPressingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy").forGetter(DiamondPressingRecipe::energy),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("heat").forGetter(DiamondPressingRecipe::heat),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time").forGetter(DiamondPressingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("min_temperature").forGetter(DiamondPressingRecipe::minTemperature))
            .apply(i, DiamondPressingRecipe::new));

    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<Integer>> OPTIONAL_INT = ByteBufCodecs.optional(ByteBufCodecs.VAR_INT);

    // Written out by hand: seven fields.
    public static final StreamCodec<RegistryFriendlyByteBuf, DiamondPressingRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.ingredient());
                OPTIONAL_INT.encode(buf, recipe.count());
                ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.result());
                OPTIONAL_INT.encode(buf, recipe.energy());
                OPTIONAL_INT.encode(buf, recipe.heat());
                OPTIONAL_INT.encode(buf, recipe.time());
                OPTIONAL_INT.encode(buf, recipe.minTemperature());
            },
            buf -> new DiamondPressingRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(buf), OPTIONAL_INT.decode(buf),
                    ItemStackTemplate.STREAM_CODEC.decode(buf), OPTIONAL_INT.decode(buf), OPTIONAL_INT.decode(buf), OPTIONAL_INT.decode(buf),
                    OPTIONAL_INT.decode(buf)));

    public int inputCount() {
        return count.orElseGet(ArcforgeConfig.DIAMOND_PRESS_GRAPHITE_PER_DIAMOND::getAsInt);
    }

    // FE per operation before Energy upgrades.
    public int energyPerOperation() {
        return energy.orElseGet(ArcforgeConfig.DIAMOND_PRESS_ENERGY_PER_DIAMOND::getAsInt);
    }

    // HU per operation before Heat upgrades.
    public int heatPerOperation() {
        return heat.orElseGet(ArcforgeConfig.DIAMOND_PRESS_HEAT_PER_DIAMOND::getAsInt);
    }

    // Ticks per operation before Speed upgrades.
    public int ticks() {
        return time.orElseGet(ArcforgeConfig.DIAMOND_PRESS_TIME::getAsInt);
    }

    public int minTemperatureOrDefault() {
        return minTemperature.orElseGet(ArcforgeConfig.DIAMOND_PRESS_MIN_TEMPERATURE::getAsInt);
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
    public RecipeSerializer<DiamondPressingRecipe> getSerializer() {
        return ModRecipes.DIAMOND_PRESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<DiamondPressingRecipe> getType() {
        return ModRecipes.DIAMOND_PRESSING.get();
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
