/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.blockentity.machine.RefiningMachineBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CrackingUnitBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.recipe.RefiningProcess;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The refinery units' menu (176x186, the inventory 104 down), one for all four (see RefiningProcess): FE, heat and the
// least temperature the recipe needs, the feed tank, up to three product tanks, and the catalyst and item output slots
// where the unit has them.
public class RefiningMenu extends MachineMenu {
    public static final int PRODUCTS = 3;
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_HEAT = 5;
    public static final int DATA_HEAT_CAPACITY = 6;
    public static final int DATA_TEMPERATURE = 7;
    public static final int DATA_MIN_TEMPERATURE = 8;
    public static final int DATA_HEAT_USAGE = 9;
    public static final int DATA_FEED_FLUID = 10;
    public static final int DATA_FEED = 11;
    public static final int DATA_FEED_CAPACITY = 12;
    public static final int DATA_PRODUCT_FLUID = 13;
    public static final int DATA_PRODUCT = DATA_PRODUCT_FLUID + PRODUCTS;
    public static final int DATA_PRODUCT_CAPACITY = DATA_PRODUCT + PRODUCTS;
    public static final int DATA_CATALYST_LEFT = DATA_PRODUCT_CAPACITY + 1;
    public static final int DATA_CATALYST_LIFE = DATA_CATALYST_LEFT + 1;
    public static final int DATA_STATUS = DATA_CATALYST_LIFE + 1;
    public static final int DATA_REDSTONE_MODE = DATA_STATUS + 1;
    public static final int DATA_SIDE_CONFIG = DATA_REDSTONE_MODE + 1;
    public static final int DATA_VALUES = DATA_SIDE_CONFIG + 1;
    public static final int INVENTORY_Y = 104;
    public static final int CATALYST_X = 44, CATALYST_Y = 35;
    public static final int ITEM_X = 109, ITEM_Y = 35;

    private final RefiningProcess process;

    // Client constructor, called with the block position written by the server.
    public RefiningMenu(RefiningProcess process, int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(process, containerId, inventory, extraData.readBlockPos(), clientItems(process), WideIntContainerData.client(DATA_VALUES));
    }

    public RefiningMenu(RefiningProcess process, int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(type(process), containerId, inventory, pos, items, data, DATA_VALUES, block(process));
        this.process = process;
        if (process == RefiningProcess.CRACKING) {
            addMachineSlot(CrackingUnitBlockEntity.SLOT_CATALYST, CATALYST_X, CATALYST_Y);
        }
        if (process.makesItem()) {
            addOutputSlot(0, ITEM_X, ITEM_Y, player -> {});
        }
        finish(inventory, INVENTORY_Y);
    }

    private static MachineItemHandler clientItems(RefiningProcess process) {
        return process == RefiningProcess.CRACKING ? CrackingUnitBlockEntity.clientItems() : RefiningMachineBlockEntity.clientItems(process);
    }

    private static MenuType<?> type(RefiningProcess process) {
        return switch (process) {
            case CRACKING -> ModMenuTypes.CRACKING_UNIT.get();
            case REFORMING -> ModMenuTypes.CATALYTIC_REFORMER.get();
            case COKING -> ModMenuTypes.COKER.get();
            case BLENDING -> ModMenuTypes.LUBRICANT_BLENDER.get();
        };
    }

    private static Block block(RefiningProcess process) {
        return switch (process) {
            case CRACKING -> ModBlocks.CRACKING_UNIT_CONTROLLER.get();
            case REFORMING -> ModBlocks.CATALYTIC_REFORMER.get();
            case COKING -> ModBlocks.COKER.get();
            case BLENDING -> ModBlocks.LUBRICANT_BLENDER.get();
        };
    }

    public RefiningProcess getProcess() {
        return process;
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

    public int getHeatUsage() {
        return value(DATA_HEAT_USAGE);
    }

    public Fluid getFeedFluid() {
        return ElectricPumpMenu.fluid(value(DATA_FEED_FLUID));
    }

    public int getFeed() {
        return value(DATA_FEED);
    }

    public int getFeedCapacity() {
        return value(DATA_FEED_CAPACITY);
    }

    // What product tank i holds (Fluids.EMPTY when it's empty).
    public Fluid getProductFluid(int i) {
        return ElectricPumpMenu.fluid(value(DATA_PRODUCT_FLUID + i));
    }

    public int getProduct(int i) {
        return value(DATA_PRODUCT + i);
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
