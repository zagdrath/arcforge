/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.item;

import java.util.Arrays;
import java.util.Comparator;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;

// Input for a single-block machine with lanes (the Mill): items put in through input faces go to the lane holding the
// fewest, of those that take them, so a hopper keeps every lane busy. Like the lane arrays' own balanced input.
public class BalancedLaneInput extends AutomationResourceHandler<ItemResource> {
    private final FilteredItemHandler items;
    private final int[] inputSlots;

    public BalancedLaneInput(FilteredItemHandler items, int[] inputSlots) {
        super(items, slot -> Arrays.stream(inputSlots).anyMatch(input -> input == slot), slot -> false);
        this.items = items;
        this.inputSlots = inputSlots;
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        Integer[] order = Arrays.stream(inputSlots).boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator.comparingInt(slot -> items.getStack(slot).getCount()));
        int inserted = 0;
        for (int slot : order) {
            if (inserted >= amount) {
                break;
            }
            inserted += insert(slot, resource, amount - inserted, transaction);
        }
        return inserted;
    }
}
