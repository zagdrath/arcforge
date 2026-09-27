/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.registry.ModRecipes;

// Recipe lookups for Arcforge machines that work on both sides: the server reads its recipe manager,
// the client the recipes the server synced (see ModRecipes), so GUI slots agree on what they accept.
public final class MachineRecipes {
    private static volatile RecipeMap clientRecipes = RecipeMap.EMPTY;

    private MachineRecipes() {}

    // Called by the client when recipes arrive, and with RecipeMap.EMPTY on logout.
    public static void setClientRecipes(RecipeMap recipes) {
        clientRecipes = recipes;
    }

    private static RecipeMap recipes(@Nullable Level level) {
        return level instanceof ServerLevel serverLevel ? serverLevel.recipeAccess().recipeMap() : clientRecipes;
    }

    public static Optional<RecipeHolder<CarbonizingRecipe>> carbonizing(ServerLevel level, ItemStack input) {
        return level.recipeAccess().getRecipeFor(ModRecipes.CARBONIZING.get(), new SingleRecipeInput(input), level);
    }

    public static Optional<RecipeHolder<ArcforgeSmeltingRecipe>> arcforgeSmelting(ServerLevel level, ItemStack input, ItemStack reagent) {
        return level.recipeAccess().getRecipeFor(ModRecipes.ARCFORGE_SMELTING.get(), new ArcforgeSmeltingRecipe.Input(input, reagent), level);
    }

    public static Optional<RecipeHolder<CrushingRecipe>> crushing(ServerLevel level, ItemStack input) {
        return level.recipeAccess().getRecipeFor(ModRecipes.CRUSHING.get(), new SingleRecipeInput(input), level);
    }

    // Vanilla furnace recipes (and any mod's), for the Induction Furnaces.
    public static Optional<RecipeHolder<SmeltingRecipe>> smelting(ServerLevel level, ItemStack input) {
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
    }

    public static boolean isSmeltingInput(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(RecipeType.SMELTING).stream()
                .anyMatch(holder -> holder.value().input().test(stack));
    }

    public static boolean isCrusherInput(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.CRUSHING.get()).stream()
                .anyMatch(holder -> holder.value().ingredient().test(stack));
    }

    public static boolean isCarbonizerInput(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.CARBONIZING.get()).stream()
                .anyMatch(holder -> holder.value().ingredient().test(stack));
    }

    public static boolean isArcforgeInput(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.ARCFORGE_SMELTING.get()).stream()
                .anyMatch(holder -> holder.value().input().test(stack));
    }

    public static boolean isArcforgeReagent(@Nullable Level level, ItemStack stack) {
        return recipes(level).byType(ModRecipes.ARCFORGE_SMELTING.get()).stream()
                .anyMatch(holder -> holder.value().reagent().test(stack));
    }
}
