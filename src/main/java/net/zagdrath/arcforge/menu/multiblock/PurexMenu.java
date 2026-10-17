/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.PurexBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The PUREX Plant's GUI: its rods in (2x2), its products out (3x2), its FE, acid, solvent and waste, the operation's
// progress and the PUREX / THOREX setting (button MODE).
public class PurexMenu extends MachineMenu {
    public static final int DATA_STATUS = 0;
    public static final int DATA_FORMED = 1;
    public static final int DATA_REDSTONE_MODE = 2;
    public static final int DATA_SIDE_CONFIG = 3;
    public static final int DATA_ENERGY = 4;
    public static final int DATA_ENERGY_CAPACITY = 5;
    public static final int DATA_ACID = 6;
    public static final int DATA_ACID_CAPACITY = 7;
    public static final int DATA_SOLVENT = 8;
    public static final int DATA_SOLVENT_FLUID = 9;
    public static final int DATA_SOLVENT_CAPACITY = 10;
    public static final int DATA_WASTE = 11;
    public static final int DATA_WASTE_CAPACITY = 12;
    public static final int DATA_PROGRESS = 13;
    public static final int DATA_TOTAL = 14;
    public static final int DATA_MODE = 15;
    public static final int DATA_OWED = 16;           // fissile owed, x 100
    public static final int DATA_USAGE = 17;
    public static final int DATA_VALUES = 18;

    public static final int MODE = 200;
    public static final int INPUT_X = 8, OUTPUT_X = 116, SLOT_Y = 78, INVENTORY_Y = 122, HEIGHT = 204;

    public PurexMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), PurexBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public PurexMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.PUREX_PLANT.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.PUREX_CONTROLLER.get());
        for (int i = 0; i < PurexBlockEntity.INPUT_SLOTS; i++) {
            addMachineSlot(i, INPUT_X + (i % 2) * 18, SLOT_Y + (i / 2) * 18);
        }
        for (int i = 0; i < PurexBlockEntity.OUTPUT_SLOTS; i++) {
            addMachineSlot(PurexBlockEntity.INPUT_SLOTS + i, OUTPUT_X + (i % 3) * 18, SLOT_Y + (i / 3) * 18);
        }
        finish(inventory, INVENTORY_Y);
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

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != MODE) {
            return super.clickMenuButton(player, buttonId);
        }
        access.execute((level, at) -> {
            if (level.getBlockEntity(at) instanceof PurexBlockEntity plant) {
                plant.toggleMode();
            }
        });
        return true;
    }

    public int get(int index) {
        return value(index);
    }

    public boolean isFormed() {
        return value(DATA_FORMED) != 0;
    }

    public boolean isThorex() {
        return value(DATA_MODE) == 1;
    }
}
