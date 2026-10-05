/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.resource;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A machine's item slots, each with a {@link SlotRole}. Slots can be read freely. Inserting is allowed only into
 * {@linkplain SlotRole#insertable() insertable} slots and extracting only from
 * {@linkplain SlotRole#extractable() extractable} ones, and both still go through the machine's own slot rules
 * (an Arc Crusher's input takes only crushable items, for example).
 *
 * <p>The stack-based methods each run in their own transaction, so do not call them while a NeoForge transfer
 * transaction is open; use {@link #handler()} for that.
 */
public interface MachineItems {
    /**
     * Returns the number of slots, upgrade slots included.
     *
     * @return the slot count
     */
    int slotCount();

    /**
     * Returns what a slot is for.
     *
     * @param slot the slot index, from 0 to {@link #slotCount()} - 1
     * @return the slot's role, or {@link SlotRole#OTHER} for an index out of range
     */
    SlotRole role(int slot);

    /**
     * Returns a copy of a slot's contents.
     *
     * @param slot the slot index
     * @return a copy of the stack in the slot, or {@link ItemStack#EMPTY} for an empty slot or an index out of range
     */
    ItemStack stack(int slot);

    /**
     * Returns the most items the slot can hold of the item in it (or of a stack of 64 if it is empty).
     *
     * @param slot the slot index
     * @return the slot's capacity, or 0 for an index out of range
     */
    int capacity(int slot);

    /**
     * Inserts into one slot. Only insertable slots accept items, and only items the machine accepts there.
     *
     * @param slot     the slot index
     * @param stack    the items to insert; not modified
     * @param simulate {@code true} to only report what would be inserted
     * @return the items that were not inserted (empty if all were)
     */
    ItemStack insert(int slot, ItemStack stack, boolean simulate);

    /**
     * Inserts into whichever insertable slots accept the items, as a pipe feeding the machine would.
     *
     * @param stack    the items to insert; not modified
     * @param simulate {@code true} to only report what would be inserted
     * @return the items that were not inserted (empty if all were)
     */
    ItemStack insert(ItemStack stack, boolean simulate);

    /**
     * Extracts from one slot. Only extractable slots give items.
     *
     * @param slot     the slot index
     * @param amount   the most items to extract
     * @param simulate {@code true} to only report what would be extracted
     * @return the items extracted (empty if none)
     */
    ItemStack extract(int slot, int amount, boolean simulate);

    /**
     * Returns a NeoForge resource handler over the same slots, with the same role and slot rules, for use inside
     * transfer transactions. Its indices are this view's slot indices.
     *
     * @return the role-checked handler
     */
    ResourceHandler<ItemResource> handler();
}
