/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.List;
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

// Sifter (arcforge:sifting): one of the ingredient (gravel, sand, Deepslate Gravel) is sieved over `time` ticks at
// `energy_per_tick` FE/t into its outputs, each rolled on its own with its `chance` (0-1), raised by the mesh in the
// Sifter. time and energy_per_tick follow machines.sifter when left out.
public record SiftingRecipe(Ingredient ingredient, List<Output> outputs, Optional<Integer> time, Optional<Integer> energyPerTick)
        implements Recipe<SingleRecipeInput> {
    // As many as the Sifter has output slots, since a block waits until all its finds would fit at once.
    public static final int MAX_OUTPUTS = 6;

    // One possible output and its chance per operation, before the mesh.
    public record Output(ItemStackTemplate item, float chance) {
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(Output::item),
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(Output::chance))
                .apply(i, Output::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Output> STREAM_CODEC = StreamCodec.composite(
                ItemStackTemplate.STREAM_CODEC, Output::item,
                ByteBufCodecs.FLOAT, Output::chance,
                Output::new);
    }

    public static final MapCodec<SiftingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(SiftingRecipe::ingredient),
            Output.CODEC.listOf(1, MAX_OUTPUTS).fieldOf("outputs").forGetter(SiftingRecipe::outputs),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time").forGetter(SiftingRecipe::time),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy_per_tick").forGetter(SiftingRecipe::energyPerTick))
            .apply(i, SiftingRecipe::new));

    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<Integer>> OPTIONAL_INT = ByteBufCodecs.optional(ByteBufCodecs.VAR_INT);

    public static final StreamCodec<RegistryFriendlyByteBuf, SiftingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, SiftingRecipe::ingredient,
            Output.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OUTPUTS)), SiftingRecipe::outputs,
            OPTIONAL_INT, SiftingRecipe::time,
            OPTIONAL_INT, SiftingRecipe::energyPerTick,
            SiftingRecipe::new);

    // Ticks per operation before the mesh and Speed upgrades.
    public int ticks() {
        return time.orElseGet(ArcforgeConfig.SIFTER_TIME::getAsInt);
    }

    // FE/t before Speed and Energy upgrades.
    public int baseEnergyPerTick() {
        return energyPerTick.orElseGet(ArcforgeConfig.SIFTER_ENERGY_PER_TICK::getAsInt);
    }

    public boolean test(ItemStack stack) {
        return ingredient.test(stack);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return test(input.item());
    }

    // The outputs are rolled by the Sifter.
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
    public RecipeSerializer<SiftingRecipe> getSerializer() {
        return ModRecipes.SIFTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<SiftingRecipe> getType() {
        return ModRecipes.SIFTING.get();
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
