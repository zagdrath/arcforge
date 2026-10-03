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

// Gasifier (arcforge:gasifying): `count` (default 1) of an ingredient and `steam` mB of Steam, with `heat` HU over `time`
// ticks at machines.gasifier.minTemperature or hotter, make the result (Syngas, with its amount) and, with `ash_chance`,
// the ash (Wood Ash). steam, heat, time and ash_chance follow the machines.gasifier config when left out.
public record GasifyingRecipe(Ingredient ingredient, int count, FluidStackTemplate result, Optional<ItemStackTemplate> ash,
        Optional<Float> ashChance, Optional<Integer> steam, Optional<Integer> heat, Optional<Integer> time) implements Recipe<SingleRecipeInput> {
    public static final MapCodec<GasifyingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(GasifyingRecipe::ingredient),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(GasifyingRecipe::count),
            FluidStackTemplate.CODEC.fieldOf("result").forGetter(GasifyingRecipe::result),
            ItemStackTemplate.CODEC.optionalFieldOf("ash").forGetter(GasifyingRecipe::ash),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("ash_chance").forGetter(GasifyingRecipe::ashChance),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("steam").forGetter(GasifyingRecipe::steam),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("heat").forGetter(GasifyingRecipe::heat),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time").forGetter(GasifyingRecipe::time))
            .apply(i, GasifyingRecipe::new));

    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<Integer>> OPTIONAL_INT = ByteBufCodecs.optional(ByteBufCodecs.VAR_INT);
    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<Float>> OPTIONAL_FLOAT = ByteBufCodecs.optional(ByteBufCodecs.FLOAT);

    // Written out by hand: eight fields.
    public static final StreamCodec<RegistryFriendlyByteBuf, GasifyingRecipe> STREAM_CODEC = StreamCodec.of(
            (buf, recipe) -> {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.ingredient());
                ByteBufCodecs.VAR_INT.encode(buf, recipe.count());
                FluidStackTemplate.STREAM_CODEC.encode(buf, recipe.result());
                ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC).encode(buf, recipe.ash());
                OPTIONAL_FLOAT.encode(buf, recipe.ashChance());
                OPTIONAL_INT.encode(buf, recipe.steam());
                OPTIONAL_INT.encode(buf, recipe.heat());
                OPTIONAL_INT.encode(buf, recipe.time());
            },
            buf -> new GasifyingRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    FluidStackTemplate.STREAM_CODEC.decode(buf), ByteBufCodecs.optional(ItemStackTemplate.STREAM_CODEC).decode(buf),
                    OPTIONAL_FLOAT.decode(buf), OPTIONAL_INT.decode(buf), OPTIONAL_INT.decode(buf), OPTIONAL_INT.decode(buf)));

    public int steamAmount() {
        return steam.orElseGet(ArcforgeConfig.GASIFIER_STEAM::getAsInt);
    }

    // HU per operation before Heat upgrades.
    public int heatPerOperation() {
        return heat.orElseGet(ArcforgeConfig.GASIFIER_HEAT_PER_OPERATION::getAsInt);
    }

    // Ticks per operation before Speed upgrades.
    public int ticks() {
        return time.orElseGet(ArcforgeConfig.GASIFIER_TIME::getAsInt);
    }

    public double ashChanceOrDefault() {
        return ashChance.map(Float::doubleValue).orElseGet(ArcforgeConfig.GASIFIER_ASH_CHANCE::getAsDouble);
    }

    public boolean test(ItemStack stack) {
        return ingredient.test(stack);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return test(input.item());
    }

    // The output is a fluid; the Gasifier fills its tank itself.
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
    public RecipeSerializer<GasifyingRecipe> getSerializer() {
        return ModRecipes.GASIFYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<GasifyingRecipe> getType() {
        return ModRecipes.GASIFYING.get();
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
