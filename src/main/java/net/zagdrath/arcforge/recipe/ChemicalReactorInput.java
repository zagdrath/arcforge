/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

// What a Chemical Reactor holds: its input slot and its two input tanks (fluid and mB each).
public record ChemicalReactorInput(ItemStack item, FluidResource fluidA, int amountA, FluidResource fluidB, int amountB) implements RecipeInput {
    public FluidResource fluid(int tank) {
        return tank == 0 ? fluidA : fluidB;
    }

    public int amount(int tank) {
        return tank == 0 ? amountA : amountB;
    }

    @Override
    public ItemStack getItem(int index) {
        return index == 0 ? item : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 1;
    }
}
