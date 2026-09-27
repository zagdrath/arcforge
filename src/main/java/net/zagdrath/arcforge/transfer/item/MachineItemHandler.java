/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.item;

import java.util.Set;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.item.upgrade.UpgradeItem;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// A machine's item slots: its own (checked by the filter), then UPGRADE_SLOTS upgrade slots. Each
// upgrade slot holds up to 8 of one upgrade type the machine accepts, and a type can't be split across
// two slots, so a machine holds at most 8 of each.
public class MachineItemHandler extends FilteredItemHandler {
    public static final int UPGRADE_SLOTS = 4;

    private final int firstUpgradeSlot;
    private final Set<UpgradeType> acceptedUpgrades;

    public MachineItemHandler(int machineSlots, SlotFilter filter, Set<UpgradeType> acceptedUpgrades, Runnable onChanged) {
        super(machineSlots + UPGRADE_SLOTS, filter, onChanged);
        this.firstUpgradeSlot = machineSlots;
        this.acceptedUpgrades = acceptedUpgrades;
    }

    public int getFirstUpgradeSlot() {
        return firstUpgradeSlot;
    }

    public boolean isUpgradeSlot(int index) {
        return index >= firstUpgradeSlot;
    }

    public Set<UpgradeType> getAcceptedUpgrades() {
        return acceptedUpgrades;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        if (!isUpgradeSlot(index)) {
            return super.isValid(index, resource);
        }
        if (!(resource.getItem() instanceof UpgradeItem upgrade) || !acceptedUpgrades.contains(upgrade.getType())) {
            return false;
        }
        // One slot per type: refuse it here if another upgrade slot already holds that type.
        for (int slot = firstUpgradeSlot; slot < size(); slot++) {
            if (slot != index && typeIn(slot) == upgrade.getType()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        return isUpgradeSlot(index) ? Math.min(UpgradeType.MAX_PER_MACHINE, super.getCapacity(index, resource)) : super.getCapacity(index, resource);
    }

    private UpgradeType typeIn(int slot) {
        ItemStack stack = getStack(slot);
        return !stack.isEmpty() && stack.getItem() instanceof UpgradeItem upgrade ? upgrade.getType() : null;
    }

    // How many upgrades of this type are installed.
    public int count(UpgradeType type) {
        int count = 0;
        for (int slot = firstUpgradeSlot; slot < size(); slot++) {
            if (typeIn(slot) == type) {
                count += getStack(slot).getCount();
            }
        }
        return Math.min(count, UpgradeType.MAX_PER_MACHINE);
    }
}
