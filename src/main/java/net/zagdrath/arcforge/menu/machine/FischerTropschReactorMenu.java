/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.FischerTropschReactorBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.minecraft.world.level.material.Fluid;

// The Fischer-Tropsch Reactor (176x186, the inventory 104 down): FE, heat and its window, the Syngas tank, four product tanks and the catalyst.
public class FischerTropschReactorMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_HEAT = 5;
    public static final int DATA_HEAT_CAPACITY = 6;
    public static final int DATA_TEMPERATURE = 7;
    public static final int DATA_MIN_TEMPERATURE = 8;
    public static final int DATA_MAX_TEMPERATURE = 9;
    public static final int DATA_HEAT_USAGE = 10;
    public static final int DATA_SYNGAS_FLUID = 11;
    public static final int DATA_SYNGAS = 12;
    public static final int DATA_SYNGAS_CAPACITY = 13;
    public static final int DATA_NAPHTHA = 14;
    public static final int DATA_LIGHT_OIL = 15;
    public static final int DATA_HEAVY_OIL = 16;
    public static final int DATA_WATER = 17;
    public static final int DATA_PRODUCT_CAPACITY = 18;
    public static final int DATA_CATALYST_LEFT = 19;
    public static final int DATA_CATALYST_LIFE = 20;
    public static final int DATA_STATUS = 21;
    public static final int DATA_REDSTONE_MODE = 22;
    public static final int DATA_SIDE_CONFIG = 23;
    public static final int INVENTORY_Y = 104;
    public static final int CATALYST_X = 44, CATALYST_Y = 35;
    public static final int DATA_VALUES = 24;

    // Client constructor, called with the block position written by the server.
    public FischerTropschReactorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), FischerTropschReactorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public FischerTropschReactorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.FISCHER_TROPSCH_REACTOR.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.FISCHER_TROPSCH_REACTOR.get());
        addMachineSlot(FischerTropschReactorBlockEntity.SLOT_CATALYST, CATALYST_X, CATALYST_Y);
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

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }

    public int getUsage() {
        return value(DATA_USAGE);
    }

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getTotal() {
        return value(DATA_TOTAL);
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

    public int getMaxTemperature() {
        return value(DATA_MAX_TEMPERATURE);
    }

    public int getHeatUsage() {
        return value(DATA_HEAT_USAGE);
    }

    public Fluid getSyngasFluid() {
        return ElectricPumpMenu.fluid(value(DATA_SYNGAS_FLUID));
    }

    public int getSyngas() {
        return value(DATA_SYNGAS);
    }

    public int getSyngasCapacity() {
        return value(DATA_SYNGAS_CAPACITY);
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

    public int getWater() {
        return value(DATA_WATER);
    }

    public int getProductCapacity() {
        return value(DATA_PRODUCT_CAPACITY);
    }

    public int getCatalystLeft() {
        return value(DATA_CATALYST_LEFT);
    }

    public int getCatalystLife() {
        return value(DATA_CATALYST_LIFE);
    }
}
