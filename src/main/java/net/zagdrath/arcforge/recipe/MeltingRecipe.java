/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
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

// Arc Melter: one item melted into a fluid with FE (cobblestone -> 250 mB lava). The FE is energyPerMb
// for each mB made unless the recipe gives its own "energy"; the time follows from the machine's FE/t.
public record MeltingRecipe(Ingredient ingredient, FluidStackTemplate result, Optional<Integer> energy)
        implements Recipe<SingleRecipeInput> {
    public static final MapCodec<MeltingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(MeltingRecipe::ingredient),
            FluidStackTemplate.CODEC.fieldOf("result").forGetter(MeltingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("energy").forGetter(MeltingRecipe::energy))
            .apply(i, MeltingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MeltingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, MeltingRecipe::ingredient,
            FluidStackTemplate.STREAM_CODEC, MeltingRecipe::result,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), MeltingRecipe::energy,
            MeltingRecipe::new);

    // FE for one operation, before Energy upgrades.
    public int totalEnergy() {
        return energy.orElseGet(() -> result.amount() * ArcforgeConfig.MELTER_ENERGY_PER_MB.getAsInt());
    }

    // Ticks for one operation at the base FE/t, before Speed upgrades.
    public int baseTicks() {
        return Math.max(1, (int) Math.ceil((double) totalEnergy() / ArcforgeConfig.MELTER_ENERGY_PER_TICK.getAsInt()));
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    // The result is a fluid; the melter fills its tank itself.
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
    public RecipeSerializer<MeltingRecipe> getSerializer() {
        return ModRecipes.MELTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<MeltingRecipe> getType() {
        return ModRecipes.MELTING.get();
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
