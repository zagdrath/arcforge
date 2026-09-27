/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import java.util.function.IntPredicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// The Metal Press as automation sees it: items only go into the input slot, and only ones the installed
// die presses, so a pipe can't fill it with something it will never use. The die slot is never reachable.
public class DieInput extends AutomationResourceHandler<ItemResource> {
    private final FilteredItemHandler items;
    private final Supplier<@Nullable Level> level;
    private final int dieSlot;
    private final int inputSlot;

    public DieInput(FilteredItemHandler items, Supplier<@Nullable Level> level, int dieSlot, int inputSlot, IntPredicate canExtract) {
        super(items, slot -> slot == inputSlot, canExtract);
        this.items = items;
        this.level = level;
        this.dieSlot = dieSlot;
        this.inputSlot = inputSlot;
    }

    private boolean pressable(int index, ItemResource resource) {
        return index == inputSlot && MachineRecipes.isPressingInput(level.get(), items.getStack(dieSlot), resource.toStack(1));
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return pressable(index, resource) && super.isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return pressable(index, resource) ? super.insert(index, resource, amount, transaction) : 0;
    }
}
