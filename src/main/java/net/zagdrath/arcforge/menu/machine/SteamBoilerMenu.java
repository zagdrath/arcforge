/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.blockentity.machine.SteamBoilerBlockEntity;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.steam.BoilerCore;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Steam Boiler and the Steam Boiler Array share this menu: the same slots and values, laid out the
// same (steam_boiler_gui_layout.json). The array also sends its height.
public class SteamBoilerMenu extends MachineMenu {
    public static final int DATA_HEAT = 0;
    public static final int DATA_HEAT_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    public static final int DATA_HEAT_USED = 3;
    public static final int DATA_WATER = 4;
    public static final int DATA_WATER_AMOUNT = 5;
    public static final int DATA_WATER_CAPACITY = 6;
    public static final int DATA_STEAM = 7;
    public static final int DATA_STEAM_AMOUNT = 8;
    public static final int DATA_STEAM_CAPACITY = 9;
    public static final int DATA_GRADE = 10;
    public static final int DATA_RATE_TENTHS = 11;
    public static final int DATA_STATUS = 12;
    public static final int DATA_REDSTONE_MODE = 13;
    public static final int DATA_SIDE_CONFIG = 14;
    public static final int DATA_HEIGHT = 15;
    public static final int DATA_VALUES = 16;

    // Slot positions from steam_boiler_gui_layout.json.
    public static final int BUCKET_IN_X = 27, BUCKET_IN_Y = 19;
    public static final int BUCKET_OUT_X = 27, BUCKET_OUT_Y = 53;
    // Tabs: Heat, Redstone, Sides; the (hidden) upgrade slots would sit under a fourth.
    private static final int UPGRADES_TAB = 3;

    // The synced value at an index, for both boilers. height: the array's height (1 for the single boiler).
    public static int value(int index, HeatBuffer heat, FilteredFluidTank water, FilteredFluidTank steam, BoilerCore core,
            MachineStatus status, RedstoneMode redstoneMode, SideConfig sideConfig, int height) {
        SteamGrade grade = core.currentGrade();
        return switch (index) {
            case DATA_HEAT -> heat.getStored();
            case DATA_HEAT_CAPACITY -> heat.getCapacity();
            case DATA_TEMPERATURE -> heat.getTemperature();
            case DATA_HEAT_USED -> core.getHeatUsed();
            case DATA_WATER -> fluidId(water);
            case DATA_WATER_AMOUNT -> water.getAmount();
            case DATA_WATER_CAPACITY -> water.getCapacity();
            case DATA_STEAM -> fluidId(steam);
            case DATA_STEAM_AMOUNT -> steam.getAmount();
            case DATA_STEAM_CAPACITY -> steam.getCapacity();
            case DATA_GRADE -> grade != null ? grade.ordinal() : -1;
            case DATA_RATE_TENTHS -> (int) Math.round(core.getRate() * 10.0);
            case DATA_STATUS -> status.ordinal();
            case DATA_REDSTONE_MODE -> redstoneMode.ordinal();
            case DATA_SIDE_CONFIG -> sideConfig.pack();
            case DATA_HEIGHT -> height;
            default -> 0;
        };
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    // --- The single Steam Boiler ---

    public static SteamBoilerMenu single(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        return single(containerId, inventory, extraData.readBlockPos(), SteamBoilerBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public static SteamBoilerMenu single(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        return new SteamBoilerMenu(ModMenuTypes.STEAM_BOILER.get(), containerId, inventory, pos, items, data, ModBlocks.STEAM_BOILER.get());
    }

    // --- The Steam Boiler Array (served by its master casing) ---

    public static SteamBoilerMenu array(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        return array(containerId, inventory, extraData.readBlockPos(), SteamBoilerBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public static SteamBoilerMenu array(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        return new SteamBoilerMenu(ModMenuTypes.STEAM_BOILER_ARRAY.get(), containerId, inventory, pos, items, data, ModBlocks.STEAM_BOILER_ARRAY_CASING.get());
    }

    private SteamBoilerMenu(MenuType<?> type, int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data, Block block) {
        super(type, containerId, inventory, pos, items, data, DATA_VALUES, block);
        addMachineSlot(SteamBoilerBlockEntity.SLOT_BUCKET_IN, BUCKET_IN_X, BUCKET_IN_Y);
        addMachineSlot(SteamBoilerBlockEntity.SLOT_BUCKET_OUT, BUCKET_OUT_X, BUCKET_OUT_Y);
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

    public Slot getBucketOutSlot() {
        return slots.get(1);
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

    public int getHeatUsed() {
        return value(DATA_HEAT_USED);
    }

    public Fluid getWater() {
        return ElectricPumpMenu.fluid(value(DATA_WATER));
    }

    public int getWaterAmount() {
        return value(DATA_WATER_AMOUNT);
    }

    public int getWaterCapacity() {
        return value(DATA_WATER_CAPACITY);
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

    // The grade the boiler is hot enough to make, or null below boiling.
    public @Nullable SteamGrade getGrade() {
        int grade = value(DATA_GRADE);
        return grade >= 0 && grade < SteamGrade.values().length ? SteamGrade.values()[grade] : null;
    }

    public double getRate() {
        return value(DATA_RATE_TENTHS) / 10.0;
    }

    public int getHeight() {
        return value(DATA_HEIGHT);
    }
}
