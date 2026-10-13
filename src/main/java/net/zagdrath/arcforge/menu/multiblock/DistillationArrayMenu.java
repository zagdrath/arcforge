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
    // One per DistillationArrayBlockEntity.Product, lightest first.
    public static final int DATA_PRODUCTS = 9;
    public static final int DATA_OUTPUT_CAPACITY = 16;
    public static final int DATA_HEIGHT = 17;
    public static final int DATA_WIDTH = 18;
    public static final int DATA_BONUS = 19;
    public static final int DATA_HEAT_PER_TICK = 20;
    public static final int DATA_FEED_RATE = 21;
    public static final int DATA_MAKES = 22;
    public static final int DATA_STATUS = 23;
    public static final int DATA_REDSTONE_MODE = 24;
    public static final int DATA_SIDE_CONFIG = 25;
    public static final int DATA_VALUES = 26;

    public static final int PITCH_X = 41, PITCH_Y = 55;

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

    public int getProduct(DistillationArrayBlockEntity.Product product) {
        return value(DATA_PRODUCTS + product.ordinal());
    }

    // Whether the column's recipe makes this product at its height.
    public boolean makes(DistillationArrayBlockEntity.Product product) {
        return (value(DATA_MAKES) & 1 << product.ordinal()) != 0;
    }

    public int getOutputCapacity() {
        return value(DATA_OUTPUT_CAPACITY);
    }

    public int getHeight() {
        return value(DATA_HEIGHT);
    }

    public int getWidth() {
        return value(DATA_WIDTH);
    }

    public int getFeedRate() {
        return value(DATA_FEED_RATE);
    }

    // The steam stripping bonus in percent, 0 without steam.
    public int getBonus() {
        return value(DATA_BONUS);
    }

    public int getHeatPerTick() {
        return value(DATA_HEAT_PER_TICK);
    }
}
