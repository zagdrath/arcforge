/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

// What a Chemical Reactor holds: its two input slots and its two input tanks (fluid and mB each).
public record ChemicalReactorInput(ItemStack item, ItemStack itemB, FluidResource fluidA, int amountA, FluidResource fluidB, int amountB)
        implements RecipeInput {
    // Just the first input slot (recipes with one item).
    public ChemicalReactorInput(ItemStack item, FluidResource fluidA, int amountA, FluidResource fluidB, int amountB) {
        this(item, ItemStack.EMPTY, fluidA, amountA, fluidB, amountB);
    }

    public FluidResource fluid(int tank) {
        return tank == 0 ? fluidA : fluidB;
    }

    public int amount(int tank) {
        return tank == 0 ? amountA : amountB;
    }

    // Input slot 0 or 1.
    public ItemStack slot(int slot) {
        return slot == 0 ? item : itemB;
    }

    @Override
    public ItemStack getItem(int index) {
        return index == 0 ? item : index == 1 ? itemB : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 2;
    }
}
