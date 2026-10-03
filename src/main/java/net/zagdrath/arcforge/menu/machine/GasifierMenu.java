/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.GasifierBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Gasifier: the Hydrothermal Carbonizer's layout, with the steam tank on the left and the Syngas tank on the right.
public class GasifierMenu extends MachineMenu {
    public static final int DATA_HEAT = 0;
    public static final int DATA_HEAT_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    public static final int DATA_MIN_TEMPERATURE = 3;
    public static final int DATA_PROGRESS = 4;
    public static final int DATA_TOTAL = 5;
    public static final int DATA_HEAT_USAGE = 6;
    public static final int DATA_STATUS = 7;
    public static final int DATA_REDSTONE_MODE = 8;
    public static final int DATA_SIDE_CONFIG = 9;
    public static final int DATA_STEAM = 10;
    public static final int DATA_STEAM_CAPACITY = 11;
    public static final int DATA_SYNGAS = 12;
    public static final int DATA_SYNGAS_CAPACITY = 13;
    public static final int DATA_VALUES = 14;

    // Client constructor, called with the block position written by the server.
    public GasifierMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), GasifierBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public GasifierMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.GASIFIER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.GASIFIER.get());
        addMachineSlot(GasifierBlockEntity.SLOT_INPUT, 44, 35);
        addMachineSlot(GasifierBlockEntity.SLOT_ASH, 101, 35);
        finish(inventory);
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

    public int getHeat() {
        return value(DATA_HEAT);
    }

    public int getHeatCapacity() {
        return value(DATA_HEAT_CAPACITY);
    }

    public int getTemperature() {
        return value(DATA_TEMPERATURE);
    }

    public int getMinTemperature() {
        return value(DATA_MIN_TEMPERATURE);
    }

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getTotal() {
        return value(DATA_TOTAL);
    }

    public int getHeatUsage() {
        return value(DATA_HEAT_USAGE);
    }

    public int getSteam() {
        return value(DATA_STEAM);
    }

    public int getSteamCapacity() {
        return value(DATA_STEAM_CAPACITY);
    }

    public int getSyngas() {
        return value(DATA_SYNGAS);
    }

    public int getSyngasCapacity() {
        return value(DATA_SYNGAS_CAPACITY);
    }
}
