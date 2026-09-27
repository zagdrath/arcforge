/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.block.storage.HeatCellBlock;
import net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.slot.ToggleableSlot;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// No item slots of its own (heat has no item form), but one upgrade slot for Insulation Upgrades, which
// lives in the Upgrades side tab after the player inventory.
public class HeatCellMenu extends StorageMenu {
    // Logical data indices (each is a full int, see WideIntContainerData).
    public static final int DATA_HEAT = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    public static final int DATA_RECEIVED = 3;
    public static final int DATA_EXTRACTED = 4;
    public static final int DATA_LEAK = 5;
    public static final int DATA_TIER = 6;
    public static final int DATA_REDSTONE_MODE = 7;
    public static final int DATA_SIDE_CONFIG = 8;
    public static final int DATA_INSULATION = 9;
    public static final int DATA_VALUES = 10;

    // The Upgrades tab is the third (after Redstone and Sides): tab N sits at y = 6 + N * 25, its slot at +9,+25.
    private static final int UPGRADES_TAB = 2;
    private static final int UPGRADE_SLOT_X = 181, UPGRADE_SLOT_Y = 6 + UPGRADES_TAB * 25 + 25;

    private final ToggleableSlot upgradeSlot;
    private final int upgradeSlotIndex;

    // Client constructor, called with the block position written by the server.
    public HeatCellMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new FilteredItemHandler(MACHINE_SLOTS, (slot, resource) -> false, () -> {}),
                HeatCellBlockEntity.upgradeSlots(() -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public HeatCellMenu(int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items, MachineItemHandler upgrades, ContainerData data) {
        super(ModMenuTypes.HEAT_CELL.get(), containerId, inventory, pos, items, data, DATA_VALUES, DATA_SIDE_CONFIG,
                block -> block instanceof HeatCellBlock, false);
        this.upgradeSlotIndex = slots.size();
        this.upgradeSlot = new ToggleableSlot(upgrades, upgrades::set, upgrades.getFirstUpgradeSlot(), UPGRADE_SLOT_X, UPGRADE_SLOT_Y);
        addSlot(upgradeSlot);
    }

    // Insulation Upgrades shift-click into the upgrade slot.
    @Override
    protected int quickMoveTarget(ItemStack stack) {
        return upgradeSlot.mayPlace(stack) ? upgradeSlotIndex : -1;
    }

    // Shift-clicking the upgrade slot sends it back to the player.
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex != upgradeSlotIndex) {
            return super.quickMoveStack(player, slotIndex);
        }
        if (!upgradeSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = upgradeSlot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, 0, upgradeSlotIndex, true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            upgradeSlot.setByPlayer(ItemStack.EMPTY);
        } else {
            upgradeSlot.setChanged();
        }
        upgradeSlot.onTake(player, stack);
        return original;
    }

    public ToggleableSlot getUpgradeSlot() {
        return upgradeSlot;
    }

    public int getHeat() {
        return value(DATA_HEAT);
    }

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }

    public int getTemperature() {
        return value(DATA_TEMPERATURE);
    }

    public int getReceivedPerTick() {
        return value(DATA_RECEIVED);
    }

    public int getExtractedPerTick() {
        return value(DATA_EXTRACTED);
    }

    public int getLeakPerTick() {
        return value(DATA_LEAK);
    }

    public int getInsulation() {
        return value(DATA_INSULATION);
    }

    public ConduitTier getTier() {
        ConduitTier[] tiers = ConduitTier.values();
        int index = value(DATA_TIER);
        return index >= 0 && index < tiers.length ? tiers[index] : ConduitTier.WROUGHT;
    }

    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.byId(value(DATA_REDSTONE_MODE));
    }
}
