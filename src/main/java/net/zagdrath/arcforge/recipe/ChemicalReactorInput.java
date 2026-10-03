/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

// What a Chemical Reactor holds: its three input slots (the third, itemC, came last) and its three input tanks (fluid and
// mB each).
public record ChemicalReactorInput(ItemStack item, ItemStack itemB, FluidResource fluidA, int amountA, FluidResource fluidB, int amountB,
        FluidResource fluidC, int amountC, ItemStack itemC) implements RecipeInput {
    public static final int TANKS = 3;
    public static final int SLOTS = 3;

    // Two input slots (the third empty), as the reactor had before it gained its third.
    public ChemicalReactorInput(ItemStack item, ItemStack itemB, FluidResource fluidA, int amountA, FluidResource fluidB, int amountB,
            FluidResource fluidC, int amountC) {
        this(item, itemB, fluidA, amountA, fluidB, amountB, fluidC, amountC, ItemStack.EMPTY);
    }

    // Two input tanks (the third empty), as the reactor had before it gained its third.
    public ChemicalReactorInput(ItemStack item, ItemStack itemB, FluidResource fluidA, int amountA, FluidResource fluidB, int amountB) {
        this(item, itemB, fluidA, amountA, fluidB, amountB, FluidResource.EMPTY, 0);
    }

    // Just the first input slot (recipes with one item).
    public ChemicalReactorInput(ItemStack item, FluidResource fluidA, int amountA, FluidResource fluidB, int amountB) {
        this(item, ItemStack.EMPTY, fluidA, amountA, fluidB, amountB);
    }

    public FluidResource fluid(int tank) {
        return switch (tank) {
            case 0 -> fluidA;
            case 1 -> fluidB;
            default -> fluidC;
        };
    }

    public int amount(int tank) {
        return switch (tank) {
            case 0 -> amountA;
            case 1 -> amountB;
            default -> amountC;
        };
    }

    // Input slot 0, 1 or 2.
    public ItemStack slot(int slot) {
        return switch (slot) {
            case 0 -> item;
            case 1 -> itemB;
            default -> itemC;
        };
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < SLOTS ? slot(index) : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return SLOTS;
    }
}
