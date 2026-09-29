/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Distillation Array's GUI, opened from any block of a formed column (served by the controller).
public class DistillationArrayMenu extends MachineMenu {
    public static final int DATA_HEAT = 0;
    public static final int DATA_HEAT_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    public static final int DATA_FEED = 3;
    public static final int DATA_FEED_AMOUNT = 4;
    public static final int DATA_FEED_CAPACITY = 5;
    public static final int DATA_STEAM = 6;
    public static final int DATA_STEAM_AMOUNT = 7;
    public static final int DATA_STEAM_CAPACITY = 8;
    public static final int DATA_NAPHTHA = 9;
    public static final int DATA_LIGHT_OIL = 10;
    public static final int DATA_HEAVY_OIL = 11;
    public static final int DATA_OUTPUT_CAPACITY = 12;
    public static final int DATA_HEIGHT = 13;
    public static final int DATA_BONUS = 14;
    public static final int DATA_HEAT_PER_TICK = 15;
    public static final int DATA_STATUS = 16;
    public static final int DATA_REDSTONE_MODE = 17;
    public static final int DATA_SIDE_CONFIG = 18;
    public static final int DATA_VALUES = 19;

    public static final int PITCH_X = 151, PITCH_Y = 36;

    // Client constructor, called with the controller's position written by the server.
    public DistillationArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new MachineItemHandler(DistillationArrayBlockEntity.MACHINE_SLOTS, (slot, resource) -> false, DistillationArrayBlockEntity.UPGRADES, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public DistillationArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.DISTILLATION_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get());
        addMachineSlot(DistillationArrayBlockEntity.SLOT_PITCH, PITCH_X, PITCH_Y);
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

    public Fluid getFeed() {
        return ElectricPumpMenu.fluid(value(DATA_FEED));
    }

    public int getFeedAmount() {
        return value(DATA_FEED_AMOUNT);
    }

    public int getFeedCapacity() {
        return value(DATA_FEED_CAPACITY);
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

    public int getNaphtha() {
        return value(DATA_NAPHTHA);
    }

    public int getLightOil() {
        return value(DATA_LIGHT_OIL);
    }

    public int getHeavyOil() {
        return value(DATA_HEAVY_OIL);
    }

    public int getOutputCapacity() {
        return value(DATA_OUTPUT_CAPACITY);
    }

    public int getHeight() {
        return value(DATA_HEIGHT);
    }

    // The steam stripping bonus in percent, 0 without steam.
    public int getBonus() {
        return value(DATA_BONUS);
    }

    public int getHeatPerTick() {
        return value(DATA_HEAT_PER_TICK);
    }
}
