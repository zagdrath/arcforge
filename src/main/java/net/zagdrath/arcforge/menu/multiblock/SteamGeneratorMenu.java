/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.SteamGeneratorBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Steam Generator's GUI: no slots. Its four tanks (hot coolant in, feedwater in, cooled coolant out, steam out),
// its tubes and what they moved last tick.
public class SteamGeneratorMenu extends MachineMenu {
    public static final int DATA_STATUS = 0;
    public static final int DATA_FORMED = 1;
    public static final int DATA_REDSTONE_MODE = 2;
    public static final int DATA_SIDE_CONFIG = 3;
    public static final int DATA_TUBES = 4;
    public static final int DATA_HOT = 5;
    public static final int DATA_FEED = 6;
    public static final int DATA_COOLED = 7;
    public static final int DATA_STEAM = 8;
    public static final int DATA_CAPACITY = 9;
    public static final int DATA_STEAM_CAPACITY = 10;
    public static final int DATA_LAST_HOT = 11;
    public static final int DATA_LAST_STEAM = 12;
    public static final int DATA_RATE = 13;
    public static final int DATA_HOT_FLUID = 14;
    public static final int DATA_COOLED_FLUID = 15;
    public static final int DATA_VALUES = 16;

    public SteamGeneratorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), SteamGeneratorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public SteamGeneratorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.STEAM_GENERATOR.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.STEAM_GENERATOR_CONTROLLER.get());
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

    public int get(int index) {
        return value(index);
    }

    public boolean isFormed() {
        return value(DATA_FORMED) != 0;
    }
}
