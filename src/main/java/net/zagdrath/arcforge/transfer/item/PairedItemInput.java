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

// Input slots that each keep one kind of item (the Chemical Reactor's three, the Vulcanizer's two, for recipes with more
// than one item): an item put in through an input face goes to the slot already holding it, otherwise into an empty slot,
// but never into an empty one while another of the slots holds it. So a hopper feeding Basic Slag and Wood Ash fills one
// slot with each, one item is never split across slots, and one kind too many waits while every slot is in use. Like
// InputTankRouter for fluids.
public class PairedItemInput extends AutomationResourceHandler<ItemResource> {
    private final FilteredItemHandler items;
    private final int[] slots;

    public PairedItemInput(FilteredItemHandler items, int slotA, int slotB) {
        this(items, slotA, slotB, slot -> false);
    }

    // canExtract: the slots automation may take from (outputs, for the unsided view).
    public PairedItemInput(FilteredItemHandler items, int slotA, int slotB, IntPredicate canExtract) {
        this(items, new int[] { slotA, slotB }, canExtract);
    }

    public PairedItemInput(FilteredItemHandler items, int[] slots, IntPredicate canExtract) {
        super(items, slot -> contains(slots, slot), canExtract);
        this.items = items;
        this.slots = slots.clone();
    }

    private static boolean contains(int[] slots, int slot) {
        for (int each : slots) {
            if (each == slot) {
                return true;
            }
        }
        return false;
    }

    // Whether this item may go into that slot now.
    private boolean accepts(int slot, ItemResource resource) {
        ItemStack held = items.getStack(slot);
        if (!held.isEmpty()) {
            return resource.matches(held);
        }
        for (int other : slots) {
            if (other != slot && !items.getStack(other).isEmpty() && resource.matches(items.getStack(other))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return contains(slots, index) && accepts(index, resource) ? super.insert(index, resource, amount, transaction) : 0;
    }

    // The slot already holding it first, then an empty one.
    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        for (int slot : slots) {
            if (!items.getStack(slot).isEmpty() && resource.matches(items.getStack(slot))) {
                return super.insert(slot, resource, amount, transaction);
            }
        }
        for (int slot : slots) {
            if (accepts(slot, resource)) {
                return super.insert(slot, resource, amount, transaction);
            }
        }
        return 0;
    }
}
