/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control;

import java.util.function.IntFunction;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;

// The API's view of a machine's slots or tanks: reads come from its contents, and writes go through its automation view
// (what a pipe gets) and only to indices whose role allows them, so the API can do nothing a pipe couldn't.
final class RoleGatedHandler<T extends Resource> implements ResourceHandler<T> {
    private final ResourceHandler<T> contents;
    // Where writes go: the machine's automation view, with the same indices as contents.
    private final ResourceHandler<T> automation;
    private final IntFunction<SlotRole> roles;
    // Whether automation fills as the machine wants (e.g. one kind per slot), so a fill without an index goes to it whole.
    private final boolean automationFills;

    RoleGatedHandler(ResourceHandler<T> contents, ResourceHandler<T> automation, IntFunction<SlotRole> roles, boolean automationFills) {
        this.contents = contents;
        this.automation = automation;
        this.roles = roles;
        this.automationFills = automationFills;
    }

    private SlotRole role(int index) {
        return index >= 0 && index < contents.size() ? roles.apply(index) : SlotRole.OTHER;
    }

    @Override
    public int size() {
        return contents.size();
    }

    @Override
    public T getResource(int index) {
        return contents.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return contents.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, T resource) {
        return contents.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, T resource) {
        return role(index).insertable() && index < automation.size() && automation.isValid(index, resource);
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty() || !role(index).insertable() || index >= automation.size()) {
            return 0;
        }
        return automation.insert(index, resource, amount, transaction);
    }

    @Override
    public int insert(T resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        if (automationFills) {
            return automation.insert(resource, amount, transaction);
        }
        int inserted = 0;
        for (int index = 0; index < size() && inserted < amount; index++) {
            inserted += insert(index, resource, amount - inserted, transaction);
        }
        return inserted;
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty() || !role(index).extractable() || index >= automation.size()) {
            return 0;
        }
        return automation.extract(index, resource, amount, transaction);
    }

    @Override
    public int extract(T resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        int extracted = 0;
        for (int index = 0; index < size() && extracted < amount; index++) {
            extracted += extract(index, resource, amount - extracted, transaction);
        }
        return extracted;
    }
}
