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
import net.zagdrath.arcforge.recipe.CrackingMode;
import net.zagdrath.arcforge.recipe.RefiningProcess;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The refinery units' menu (176x186, the inventory 104 down), one for all of them (see RefiningProcess): FE, heat and
// the least temperature the recipe needs, the feed tank (and the second feed tank where the unit has one), up to four
// product tanks, the catalyst and item output slots where the unit has them (the item slot right of the last tank), and
// the Cracking Unit's mode, set by its buttons.
public class RefiningMenu extends MachineMenu {
    public static final int PRODUCTS = 4;
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
    public static final int DATA_SECOND_FLUID = DATA_SIDE_CONFIG + 1;
    public static final int DATA_SECOND = DATA_SECOND_FLUID + 1;
    public static final int DATA_SECOND_CAPACITY = DATA_SECOND + 1;
    public static final int DATA_MODE = DATA_SECOND_CAPACITY + 1;
    public static final int DATA_VALUES = DATA_MODE + 1;
    public static final int INVENTORY_Y = 104;
    public static final int CATALYST_X = 44, CATALYST_Y = 35;
    public static final int ITEM_Y = 35;
    // The Cracking Unit's mode buttons: BUTTON_MODE + the CrackingMode's ordinal.
    public static final int BUTTON_MODE = 200;

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
            addOutputSlot(0, itemX(process), ITEM_Y, player -> {});
        }
        finish(inventory, INVENTORY_Y);
    }

    // The item output slot sits 4 px right of the last product tank (tanks at x 89 + 16k, 12 wide).
    public static int itemX(RefiningProcess process) {
        return 89 + 16 * process.tanks() + 4;
    }

    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player player, int buttonId) {
        if (process == RefiningProcess.CRACKING && buttonId >= BUTTON_MODE && buttonId < BUTTON_MODE + CrackingMode.values().length) {
            CrackingMode mode = CrackingMode.byOrdinal(buttonId - BUTTON_MODE);
            access.execute((level, pos) -> {
                if (level.getBlockEntity(pos) instanceof CrackingUnitBlockEntity cracker) {
                    cracker.setMode(mode);
                }
            });
            return true;
        }
        return super.clickMenuButton(player, buttonId);
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
            case HYDROTREATING -> ModMenuTypes.HYDROTREATER.get();
            case SULFUR_RECOVERY -> ModMenuTypes.SULFUR_RECOVERY_UNIT.get();
            case STEAM_REFORMING -> ModMenuTypes.STEAM_METHANE_REFORMER.get();
            case STEAM_CRACKING -> ModMenuTypes.STEAM_CRACKER.get();
        };
    }

    private static Block block(RefiningProcess process) {
        return switch (process) {
            case CRACKING -> ModBlocks.CRACKING_UNIT_CONTROLLER.get();
            case REFORMING -> ModBlocks.CATALYTIC_REFORMER.get();
            case COKING -> ModBlocks.COKER.get();
            case BLENDING -> ModBlocks.LUBRICANT_BLENDER.get();
            case HYDROTREATING -> ModBlocks.HYDROTREATER.get();
            case SULFUR_RECOVERY -> ModBlocks.SULFUR_RECOVERY_UNIT.get();
            case STEAM_REFORMING -> ModBlocks.STEAM_METHANE_REFORMER.get();
            case STEAM_CRACKING -> ModBlocks.STEAM_CRACKER_CONTROLLER.get();
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

    public Fluid getSecondFluid() {
        return ElectricPumpMenu.fluid(value(DATA_SECOND_FLUID));
    }

    public int getSecond() {
        return value(DATA_SECOND);
    }

    public int getSecondCapacity() {
        return value(DATA_SECOND_CAPACITY);
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

    // The Cracking Unit's mode (catalytic for the other units).
    public CrackingMode getMode() {
        return CrackingMode.byOrdinal(value(DATA_MODE));
    }
}
