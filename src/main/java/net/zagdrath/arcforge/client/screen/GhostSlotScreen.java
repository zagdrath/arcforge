/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.arcforge.menu.GhostSlotMenu;
import net.zagdrath.arcforge.network.GhostSlotPayload;

// A screen whose ghost slots take items (and fluids or gases) dragged from JEI (see the JEI plugin's GhostSlotHandler).
// By default the menu is a GhostSlotMenu, which decides what each slot takes, and a drop goes to the server as a
// GhostSlotPayload; a screen with its own packet overrides the rest.
public interface GhostSlotScreen {
    AbstractContainerMenu getMenu();

    // Where ghost slot i is drawn, in screen coordinates.
    Rect2i ghostSlotArea(int slot);

    default int ghostSlotCount() {
        return getMenu() instanceof GhostSlotMenu menu ? menu.ghostSlotCount() : 0;
    }

    default boolean acceptsGhostItem(int slot, ItemStack stack) {
        return getMenu() instanceof GhostSlotMenu menu && menu.acceptsGhostItem(slot, stack);
    }

    default boolean acceptsGhostFluid(int slot, Fluid fluid) {
        return getMenu() instanceof GhostSlotMenu menu && menu.acceptsGhostFluid(slot, fluid);
    }

    default void setGhostItem(int slot, ItemStack stack) {
        ClientPacketDistributor.sendToServer(GhostSlotPayload.item(getMenu().containerId, slot, stack));
    }

    default void setGhostFluid(int slot, Fluid fluid) {
        ClientPacketDistributor.sendToServer(GhostSlotPayload.fluid(getMenu().containerId, slot, fluid));
    }
}
