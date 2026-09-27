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
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.storage.EnergyCellBlock;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.StorageBlockEntity;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

public class EnergyCellMenu extends StorageMenu {
    // Logical data indices (each is a full int, see WideIntContainerData).
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_RECEIVED = 2;
    public static final int DATA_EXTRACTED = 3;
    public static final int DATA_REDSTONE_MODE = 4;
    public static final int DATA_SIDE_CONFIG = 5;
    public static final int DATA_VALUES = 6;

    // Client constructor, called with the block position written by the server.
    public EnergyCellMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new FilteredItemHandler(MACHINE_SLOTS, (slot, resource) -> EnergyCellBlockEntity.isEnergyItem(resource), () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public EnergyCellMenu(int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items, ContainerData data) {
        super(ModMenuTypes.ENERGY_CELL.get(), containerId, inventory, pos, items, data, DATA_VALUES, DATA_SIDE_CONFIG,
                block -> block instanceof EnergyCellBlock);
    }

    // Energy items go to the discharge slot first; if it's taken, to the charge slot.
    @Override
    protected int quickMoveTarget(ItemStack stack) {
        if (!EnergyCellBlockEntity.isEnergyItem(ItemResource.of(stack))) {
            return -1;
        }
        return getInputSlot().hasItem() ? StorageBlockEntity.SLOT_OUT : StorageBlockEntity.SLOT_IN;
    }

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }

    public int getReceivedPerTick() {
        return value(DATA_RECEIVED);
    }

    public int getExtractedPerTick() {
        return value(DATA_EXTRACTED);
    }

    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.byId(value(DATA_REDSTONE_MODE));
    }
}
