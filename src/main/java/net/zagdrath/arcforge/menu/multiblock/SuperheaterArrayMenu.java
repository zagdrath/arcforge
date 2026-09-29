/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.steam.BoilerPressure;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Served by the centre of a Superheater Array; opened from any of its casings. It has no slots.
public class SuperheaterArrayMenu extends MachineMenu {
    public static final int DATA_HEAT = 0;
    public static final int DATA_HEAT_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    public static final int DATA_IN_FLUID = 3;
    public static final int DATA_IN_AMOUNT = 4;
    public static final int DATA_OUT_FLUID = 5;
    public static final int DATA_OUT_AMOUNT = 6;
    public static final int DATA_TANK_CAPACITY = 7;
    public static final int DATA_FLOW = 8;
    public static final int DATA_HEAT_USED = 9;
    // The grade it made last tick (a SteamGrade ordinal), or -1 passing through.
    public static final int DATA_TARGET = 10;
    public static final int DATA_PRESSURE = 11;
    public static final int DATA_STATUS = 12;
    public static final int DATA_REDSTONE_MODE = 13;
    public static final int DATA_SIDE_CONFIG = 14;
    public static final int DATA_VALUES = 15;
    // The Pressure tab's buttons: this plus the setting's ordinal (as the Steam Boiler Array's).
    private static final int PRESSURE_BUTTON_FIRST = 200;

    // No slots: the upgrade tab index only has to be past the other tabs.

    // Client constructor, called with the centre's position written by the server.
    public SuperheaterArrayMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), SuperheaterArrayBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public SuperheaterArrayMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.SUPERHEATER_ARRAY.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.SUPERHEATER_ARRAY_CASING.get());
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

    public static int pressureButtonId(BoilerPressure pressure) {
        return PRESSURE_BUTTON_FIRST + pressure.ordinal();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        int pressure = buttonId - PRESSURE_BUTTON_FIRST;
        if (pressure >= 0 && pressure < BoilerPressure.values().length) {
            access.execute((level, pos) -> {
                if (level.getBlockEntity(pos) instanceof SuperheaterArrayBlockEntity superheater) {
                    superheater.setPressure(BoilerPressure.byId(pressure));
                }
            });
            return true;
        }
        return super.clickMenuButton(player, buttonId);
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

    public Fluid getInFluid() {
        return ElectricPumpMenu.fluid(value(DATA_IN_FLUID));
    }

    public int getInAmount() {
        return value(DATA_IN_AMOUNT);
    }

    public Fluid getOutFluid() {
        return ElectricPumpMenu.fluid(value(DATA_OUT_FLUID));
    }

    public int getOutAmount() {
        return value(DATA_OUT_AMOUNT);
    }

    public int getTankCapacity() {
        return value(DATA_TANK_CAPACITY);
    }

    public int getFlow() {
        return value(DATA_FLOW);
    }

    public int getHeatUsed() {
        return value(DATA_HEAT_USED);
    }

    public @Nullable SteamGrade getTarget() {
        int target = value(DATA_TARGET);
        return target >= 0 && target < SteamGrade.values().length ? SteamGrade.values()[target] : null;
    }

    public BoilerPressure getPressure() {
        return BoilerPressure.byId(value(DATA_PRESSURE));
    }
}
