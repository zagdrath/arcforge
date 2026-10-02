/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class VacuumCollectorMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_RANGE = 2;
    public static final int DATA_MAX_RANGE = 3;
    public static final int DATA_STATUS = 4;
    public static final int DATA_REDSTONE_MODE = 5;
    public static final int DATA_SIDE_CONFIG = 6;
    public static final int DATA_XP = 7;
    public static final int DATA_XP_CAPACITY = 8;
    public static final int DATA_VALUES = 9;

    // Range buttons. Show/hide (202) only changes the outline on this client, so it's never sent.
    public static final int BUTTON_RANGE_MINUS = 200;
    public static final int BUTTON_RANGE_PLUS = 201;
    public static final int BUTTON_TOGGLE_OUTLINE = 202;

    // Positions from gui_layouts.json "vacuum_collector" (176x206).
    public static final int FILTER_X = 30, FILTER_Y = 18;
    public static final int BUFFER_X = 8, BUFFER_Y = 76;
    public static final int INVENTORY_Y = 124;

    // Client constructor, called with the block position written by the server.
    public VacuumCollectorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), VacuumCollectorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public VacuumCollectorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.VACUUM_COLLECTOR.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.VACUUM_COLLECTOR.get());
        addMachineSlot(VacuumCollectorBlockEntity.SLOT_FILTER, FILTER_X, FILTER_Y);
        for (int i = 0; i < VacuumCollectorBlockEntity.BUFFER_SLOTS; i++) {
            addMachineSlot(VacuumCollectorBlockEntity.FIRST_BUFFER + i, BUFFER_X + (i % 9) * 18, BUFFER_Y + (i / 9) * 18);
        }
        finish(inventory, INVENTORY_Y);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_RANGE_MINUS || buttonId == BUTTON_RANGE_PLUS) {
            access.execute((level, pos) -> {
                if (level.getBlockEntity(pos) instanceof VacuumCollectorBlockEntity collector) {
                    collector.setRange(collector.getRange() + (buttonId == BUTTON_RANGE_PLUS ? 1 : -1));
                }
            });
            return true;
        }
        return super.clickMenuButton(player, buttonId);
    }

    @Override
    protected int statusIndex() {
        return DATA_STATUS;
    }

    @Override
    protected int redstoneIndex() {
        return DATA_REDSTONE_MODE;
    }

    @Override
    protected int sidesIndex() {
        return DATA_SIDE_CONFIG;
    }

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }

    public int getRange() {
        return value(DATA_RANGE);
    }

    public int getMaxRange() {
        return value(DATA_MAX_RANGE);
    }

    public int getXp() {
        return value(DATA_XP);
    }

    public int getXpCapacity() {
        return value(DATA_XP_CAPACITY);
    }
}
