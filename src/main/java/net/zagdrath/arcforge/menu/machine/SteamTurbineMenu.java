/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.blockentity.machine.SteamTurbineBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

public class SteamTurbineMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STEAM = 2;
    public static final int DATA_STEAM_AMOUNT = 3;
    public static final int DATA_STEAM_CAPACITY = 4;
    public static final int DATA_FLOW = 5;
    public static final int DATA_MAX_FLOW = 6;
    public static final int DATA_FE_PER_TICK = 7;
    public static final int DATA_STATUS = 8;
    public static final int DATA_REDSTONE_MODE = 9;
    public static final int DATA_SIDE_CONFIG = 10;
    public static final int DATA_VALUES = 11;

    // Tabs: Energy, Redstone, Sides; the (hidden) upgrade slots would sit under a fourth.
    private static final int UPGRADES_TAB = 3;

    // Client constructor, called with the block position written by the server.
    public SteamTurbineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new MachineItemHandler(0, (slot, resource) -> false, SteamTurbineBlockEntity.UPGRADES, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public SteamTurbineMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.STEAM_TURBINE.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.STEAM_TURBINE.get());
        finish(inventory, UPGRADES_TAB);
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

    public Fluid getSteam() {
        return ElectricPumpMenu.fluid(value(DATA_STEAM));
    }

    public int getSteamAmount() {
        return value(DATA_STEAM_AMOUNT);
    }

    public int getSteamCapacity() {
        return value(DATA_STEAM_CAPACITY);
    }

    public int getFlow() {
        return value(DATA_FLOW);
    }

    public int getMaxFlow() {
        return value(DATA_MAX_FLOW);
    }

    public int getFePerTick() {
        return value(DATA_FE_PER_TICK);
    }
}
