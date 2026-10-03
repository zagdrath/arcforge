/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

// A menu with ghost slots (set by clicking with an item, never using it up) that can also be set from an item or fluid
// the player doesn't hold: one dragged from JEI (GhostSlotPayload). Setting a slot this way does exactly what clicking it
// with that item does. The accepts methods decide which slots light up while dragging, and are checked again on the
// server.
public interface GhostSlotMenu {
    int ghostSlotCount();

    // Whether this item (never empty) may be dropped on the slot.
    boolean acceptsGhostItem(int slot, ItemStack stack);

    // Whether this fluid or gas may be dropped on the slot.
    default boolean acceptsGhostFluid(int slot, Fluid fluid) {
        return false;
    }

    // Server side: sets the slot as clicking it with the stack would. Returns whether it did.
    boolean setGhostItem(Player player, int slot, ItemStack stack);

    // Server side: sets the slot to the fluid or gas. Returns whether it did.
    default boolean setGhostFluid(Player player, int slot, Fluid fluid) {
        return false;
    }
}
