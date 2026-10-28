/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.slot;

import org.jspecify.annotations.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.upgrade.UpgradeType;
import net.zagdrath.arcforge.item.upgrade.UpgradeItem;

import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

// A slot the screen can hide, e.g. upgrade slots that only exist while their side tab is open.
// It stays active on the server so shift-clicking still works.
public class ToggleableSlot extends ResourceHandlerSlot {
    private boolean active = true;
    private @Nullable UpgradeType upgradeType;

    public ToggleableSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> slotModifier, int handlerSlot, int x, int y) {
        super(handler, slotModifier, handlerSlot, x, y);
    }

    public ToggleableSlot forUpgrade(UpgradeType type) {
        this.upgradeType = type;
        return this;
    }

    public @Nullable UpgradeType upgradeType() {
        return upgradeType;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return (upgradeType == null || stack.getItem() instanceof UpgradeItem upgrade
                && upgrade.getType() == upgradeType) && super.mayPlace(stack);
    }

    @Override
    public @Nullable Identifier getNoItemIcon() {
        return upgradeType == null ? super.getNoItemIcon() : Identifier.fromNamespaceAndPath("arcforge",
                "widget/arcforge/empty_" + upgradeType.getSerializedName() + "_upgrade");
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean isActive() {
        return active;
    }
}
