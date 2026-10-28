/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer.item;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.NonNullList;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.item.upgrade.UpgradeItem;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// A machine's own item slots, then one designated slot per supported upgrade type. Legacy spare
// storage slots remain in the saved layout for compatibility but accept no new upgrades and are not shown.
// Each visible upgrade slot holds at most eight of its designated type.
public class MachineItemHandler extends FilteredItemHandler {
    public static final int UPGRADE_SLOTS = 4;

    private final int firstUpgradeSlot;
    private final Set<UpgradeType> acceptedUpgrades;
    private final List<UpgradeType> upgradeTypes;
    private final int minimumSize;
    private final java.util.Map<Integer, Integer> slotLimits = new java.util.HashMap<>();

    public MachineItemHandler(int machineSlots, SlotFilter filter, Set<UpgradeType> acceptedUpgrades, Runnable onChanged) {
        this(machineSlots, UPGRADE_SLOTS, filter, acceptedUpgrades, onChanged);
    }

    // For blocks with fewer upgrade slots than a machine (a Heat Cell has one).
    public MachineItemHandler(int machineSlots, int upgradeSlots, SlotFilter filter, Set<UpgradeType> acceptedUpgrades, Runnable onChanged) {
        super(machineSlots + Math.max(upgradeSlots, acceptedUpgrades.size()), filter, onChanged);
        this.firstUpgradeSlot = machineSlots;
        this.acceptedUpgrades = Set.copyOf(acceptedUpgrades);
        this.upgradeTypes = acceptedUpgrades.stream().sorted().toList();
        this.minimumSize = machineSlots + Math.max(upgradeSlots, acceptedUpgrades.size());
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

    public List<UpgradeType> getUpgradeTypes() {
        return upgradeTypes;
    }

    public int slotFor(UpgradeType type) {
        int index = upgradeTypes.indexOf(type);
        return index < 0 ? -1 : firstUpgradeSlot + index;
    }

    // Old saves allowed each upgrade type in any of four slots. Rehome them by type on load, keeping
    // unexpected/overflow stacks in spare storage so breaking the machine can still recover them.
    public void normalizeUpgrades() {
        List<ItemStack> old = new ArrayList<>();
        for (int slot = firstUpgradeSlot; slot < size(); slot++) {
            if (!getStack(slot).isEmpty()) {
                old.add(getStack(slot).copy());
            }
        }
        NonNullList<ItemStack> restored = NonNullList.withSize(Math.max(size(), minimumSize), ItemStack.EMPTY);
        for (int slot = 0; slot < Math.min(firstUpgradeSlot, size()); slot++) {
            restored.set(slot, getStack(slot));
        }
        setStacks(restored);
        for (ItemStack stack : old) {
            int target = stack.getItem() instanceof UpgradeItem upgrade ? slotFor(upgrade.getType()) : -1;
            if (target >= 0 && getStack(target).isEmpty()) {
                stacks.set(target, stack);
            } else {
                int spare = firstUpgradeSlot + upgradeTypes.size();
                while (spare < size() && !getStack(spare).isEmpty()) {
                    spare++;
                }
                ensureSize(spare + 1);
                stacks.set(spare, stack);
            }
        }
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        if (!isUpgradeSlot(index)) {
            return super.isValid(index, resource);
        }
        if (!(resource.getItem() instanceof UpgradeItem upgrade) || slotFor(upgrade.getType()) != index) {
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

    // A machine slot that holds fewer than a stack (the Vacuum Collector's filter slot holds one).
    public void setSlotLimit(int index, int limit) {
        slotLimits.put(index, limit);
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        int capacity = isUpgradeSlot(index) ? Math.min(UpgradeType.MAX_PER_MACHINE, super.getCapacity(index, resource)) : super.getCapacity(index, resource);
        Integer limit = slotLimits.get(index);
        return limit != null ? Math.min(limit, capacity) : capacity;
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
