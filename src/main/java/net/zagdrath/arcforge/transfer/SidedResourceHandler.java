/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer;

import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// View of a storage block's handler for one face: input faces only accept, output faces only give,
// and each operation moves at most the block's rate.
public class SidedResourceHandler<T extends Resource> extends DelegatingResourceHandler<T> {
    private final boolean canInsert;
    private final boolean canExtract;
    private final int maxPerOperation;

    public SidedResourceHandler(ResourceHandler<T> delegate, boolean canInsert, boolean canExtract, int maxPerOperation) {
        super(delegate);
        this.canInsert = canInsert;
        this.canExtract = canExtract;
        this.maxPerOperation = maxPerOperation;
    }

    @Override
    public boolean isValid(int index, T resource) {
        return canInsert && super.isValid(index, resource);
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        return canInsert ? super.insert(index, resource, Math.min(amount, maxPerOperation), transaction) : 0;
    }

    @Override
    public int insert(T resource, int amount, TransactionContext transaction) {
        int inserted = 0;
        int limit = Math.min(amount, maxPerOperation);
        for (int index = 0; index < size() && inserted < limit; index++) {
            inserted += insert(index, resource, limit - inserted, transaction);
        }
        return inserted;
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        return canExtract ? super.extract(index, resource, Math.min(amount, maxPerOperation), transaction) : 0;
    }

    @Override
    public int extract(T resource, int amount, TransactionContext transaction) {
        int extracted = 0;
        int limit = Math.min(amount, maxPerOperation);
        for (int index = 0; index < size() && extracted < limit; index++) {
            extracted += extract(index, resource, limit - extracted, transaction);
        }
        return extracted;
    }
}
