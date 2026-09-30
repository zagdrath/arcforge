/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.item;

import java.util.function.IntPredicate;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;

// Two input slots that each keep one kind of item (the Chemical Reactor's, for recipes with two items): an item put in
// through an input face goes to the slot already holding it, otherwise into an empty slot, but never into an empty one
// while the other slot holds it. So a hopper feeding Basic Slag and Wood Ash fills one slot with each, one item is never
// split across both, and a third kind waits while both are in use. Like InputTankRouter for fluids.
public class PairedItemInput extends AutomationResourceHandler<ItemResource> {
    private final FilteredItemHandler items;
    private final int slotA;
    private final int slotB;

    public PairedItemInput(FilteredItemHandler items, int slotA, int slotB) {
        this(items, slotA, slotB, slot -> false);
    }

    // canExtract: the slots automation may take from (outputs, for the unsided view).
    public PairedItemInput(FilteredItemHandler items, int slotA, int slotB, IntPredicate canExtract) {
        super(items, slot -> slot == slotA || slot == slotB, canExtract);
        this.items = items;
        this.slotA = slotA;
        this.slotB = slotB;
    }

    // Whether this item may go into that slot now.
    private boolean accepts(int slot, ItemResource resource) {
        ItemStack held = items.getStack(slot);
        ItemStack other = items.getStack(slot == slotA ? slotB : slotA);
        if (!held.isEmpty()) {
            return resource.matches(held);
        }
        return other.isEmpty() || !resource.matches(other);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return (index == slotA || index == slotB) && accepts(index, resource) ? super.insert(index, resource, amount, transaction) : 0;
    }

    // The slot already holding it first, then an empty one.
    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        for (int slot : new int[] { slotA, slotB }) {
            if (!items.getStack(slot).isEmpty() && resource.matches(items.getStack(slot))) {
                return super.insert(slot, resource, amount, transaction);
            }
        }
        for (int slot : new int[] { slotA, slotB }) {
            if (accepts(slot, resource)) {
                return super.insert(slot, resource, amount, transaction);
            }
        }
        return 0;
    }
}
