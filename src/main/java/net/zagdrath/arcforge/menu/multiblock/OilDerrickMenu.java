/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.OilDerrickBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Oil Derrick's GUI, opened from any block of a formed rig (served by the controller).
public class OilDerrickMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_WATER = 3;
    public static final int DATA_WATER_CAPACITY = 4;
    public static final int DATA_DEPTH = 5;
    public static final int DATA_TARGET = 6;
    public static final int DATA_PROGRESS = 7;
    public static final int DATA_TICKS_PER_BLOCK = 8;
    public static final int DATA_PIPES_NEEDED = 9;
    public static final int DATA_STATUS = 10;
    public static final int DATA_REDSTONE_MODE = 11;
    public static final int DATA_SIDE_CONFIG = 12;
    public static final int DATA_VALUES = 13;

    public static final int PIPES_X = 133, PIPES_Y = 19;

    // Client constructor, called with the controller's position written by the server.
    public OilDerrickMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new MachineItemHandler(OilDerrickBlockEntity.MACHINE_SLOTS, OilDerrickBlockEntity::isItemValid, OilDerrickBlockEntity.UPGRADES, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public OilDerrickMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.OIL_DERRICK.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.OIL_DERRICK_CONTROLLER.get());
        addMachineSlot(OilDerrickBlockEntity.SLOT_PIPES, PIPES_X, PIPES_Y);
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

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }

    public int getUsage() {
        return value(DATA_USAGE);
    }

    public int getWater() {
        return value(DATA_WATER);
    }

    public int getWaterCapacity() {
        return value(DATA_WATER_CAPACITY);
    }

    public int getDepth() {
        return value(DATA_DEPTH);
    }

    // How deep the hole has to go (0: no oil under the rig).
    public int getTarget() {
        return value(DATA_TARGET);
    }

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getTicksPerBlock() {
        return value(DATA_TICKS_PER_BLOCK);
    }

    public int getPipesNeeded() {
        return value(DATA_PIPES_NEEDED);
    }
}
