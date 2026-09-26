/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer;

import java.util.function.IntPredicate;

import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// View of a machine's handler exposed to pipes, hoppers and other mods, limiting which indices
// can be inserted into or extracted from.
public class AutomationResourceHandler<T extends Resource> extends DelegatingResourceHandler<T> {
    private final IntPredicate canInsert;
    private final IntPredicate canExtract;

    public AutomationResourceHandler(ResourceHandler<T> delegate, IntPredicate canInsert, IntPredicate canExtract) {
        super(delegate);
        this.canInsert = canInsert;
        this.canExtract = canExtract;
    }

    @Override
    public boolean isValid(int index, T resource) {
        return canInsert.test(index) && super.isValid(index, resource);
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        return canInsert.test(index) ? super.insert(index, resource, amount, transaction) : 0;
    }

    @Override
    public int insert(T resource, int amount, TransactionContext transaction) {
        int inserted = 0;
        for (int index = 0; index < size() && inserted < amount; index++) {
            inserted += insert(index, resource, amount - inserted, transaction);
        }
        return inserted;
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        return canExtract.test(index) ? super.extract(index, resource, amount, transaction) : 0;
    }

    @Override
    public int extract(T resource, int amount, TransactionContext transaction) {
        int extracted = 0;
        for (int index = 0; index < size() && extracted < amount; index++) {
            extracted += extract(index, resource, amount - extracted, transaction);
        }
        return extracted;
    }
}
