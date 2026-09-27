/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.block.storage.HeatCellBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// No item slots: heat has no item form yet.
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
    public static final int DATA_VALUES = 9;

    // Client constructor, called with the block position written by the server.
    public HeatCellMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new FilteredItemHandler(MACHINE_SLOTS, (slot, resource) -> false, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public HeatCellMenu(int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items, ContainerData data) {
        super(ModMenuTypes.HEAT_CELL.get(), containerId, inventory, pos, items, data, DATA_VALUES, DATA_SIDE_CONFIG,
                block -> block instanceof HeatCellBlock, false);
    }

    @Override
    protected int quickMoveTarget(ItemStack stack) {
        return -1;
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

    public ConduitTier getTier() {
        ConduitTier[] tiers = ConduitTier.values();
        int index = value(DATA_TIER);
        return index >= 0 && index < tiers.length ? tiers[index] : ConduitTier.WROUGHT;
    }

    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.byId(value(DATA_REDSTONE_MODE));
    }
}
