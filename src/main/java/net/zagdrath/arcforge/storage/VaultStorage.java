/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.storage;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// A Vault's contents as one slot of up to `capacity` items of a single stackable type, far past a normal stack.
// The capacity is reported as it is, so conduits and hoppers fill and empty it past 64. The type is kept while
// there's anything in it, or while it's locked (locking an empty vault makes the next item inserted its type).
// In void mode anything offered to a full vault is accepted and destroyed. Takes part in transactions like
// NeoForge's own stack handlers; committed changes are reported to the listener.
public class VaultStorage extends SnapshotJournal<VaultStorage.Snapshot> implements ResourceHandler<ItemResource> {
    public interface Listener {
        // Called after a change. typeChanged: the item type, lock or void setting changed, not just the amount.
        void onChanged(boolean typeChanged);
    }

    record Snapshot(ItemStack template, int amount) {}

    private final int capacity;
    private final Listener listener;
    private ItemStack template = ItemStack.EMPTY;
    private int amount;
    private boolean locked;
    private boolean voidMode;

    public VaultStorage(int capacity, Listener listener) {
        this.capacity = capacity;
        this.listener = listener;
    }

    // --- State ---

    // The item type as a count-1 stack (EMPTY if it has none).
    public ItemStack getTemplate() {
        return template;
    }

    public int getAmount() {
        return amount;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isVoidMode() {
        return voidMode;
    }

    // Unlocking an empty vault forgets its type.
    public void setLocked(boolean value) {
        locked = value;
        if (!locked && amount == 0) {
            template = ItemStack.EMPTY;
        }
        listener.onChanged(true);
    }

    public void setVoidMode(boolean value) {
        voidMode = value;
        listener.onChanged(true);
    }

    // Replaces everything, e.g. when loading (no listener call).
    public void load(ItemStack template, int amount, boolean locked, boolean voidMode) {
        this.amount = Math.clamp(amount, 0, capacity);
        this.locked = locked;
        this.voidMode = voidMode;
        this.template = this.amount > 0 || locked ? template.copyWithCount(template.isEmpty() ? 0 : 1) : ItemStack.EMPTY;
    }

    public VaultContents toContents() {
        return VaultContents.of(template, amount, locked, voidMode);
    }

    // Stackable items that may go inside containers, of the vault's type if it has one.
    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxStackSize() > 1 && stack.canFitInsideContainerItems()
                && (template.isEmpty() || ItemStack.isSameItemSameComponents(template, stack));
    }

    public boolean accepts(ItemResource resource) {
        return !resource.isEmpty() && accepts(resource.toStack(1));
    }

    // --- ResourceHandler ---

    @Override
    public int size() {
        return 1;
    }

    @Override
    public ItemResource getResource(int index) {
        Objects.checkIndex(index, 1);
        return amount > 0 ? ItemResource.of(template) : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, 1);
        return amount;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        Objects.checkIndex(index, 1);
        return resource.isEmpty() || accepts(resource) ? capacity : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        Objects.checkIndex(index, 1);
        return accepts(resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, 1);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!accepts(resource)) {
            return 0;
        }
        int accepted = Math.min(amount, capacity - this.amount);
        if (accepted > 0) {
            updateSnapshots(transaction);
            if (template.isEmpty()) {
                template = resource.toStack(1);
            }
            this.amount += accepted;
        }
        // Void mode takes everything; what doesn't fit is destroyed.
        return voidMode ? amount : accepted;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, 1);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (this.amount == 0 || !ItemStack.isSameItemSameComponents(template, resource.toStack(1))) {
            return 0;
        }
        int extracted = Math.min(amount, this.amount);
        updateSnapshots(transaction);
        this.amount -= extracted;
        if (this.amount == 0 && !locked) {
            template = ItemStack.EMPTY;
        }
        return extracted;
    }

    // --- Transactions ---

    @Override
    protected Snapshot createSnapshot() {
        return new Snapshot(template, amount);
    }

    @Override
    protected void revertToSnapshot(Snapshot snapshot) {
        template = snapshot.template();
        amount = snapshot.amount();
    }

    @Override
    protected void onRootCommit(Snapshot original) {
        listener.onChanged(!ItemStack.isSameItemSameComponents(original.template(), template));
    }
}
