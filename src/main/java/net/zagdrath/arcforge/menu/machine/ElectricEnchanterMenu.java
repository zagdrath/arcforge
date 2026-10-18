/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.zagdrath.arcforge.blockentity.machine.ElectricEnchanterBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Electric Enchanter's menu (176x222, gui_layouts.json "electric_enchanter"): the input, Lapis and output slots at
// the top, the target list under them (drawn by the screen, set with EnchanterTargetPayload), the inventory below.
public class ElectricEnchanterMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_XP = 2;
    public static final int DATA_XP_CAPACITY = 3;
    public static final int DATA_PROGRESS = 4;
    public static final int DATA_TOTAL = 5;
    public static final int DATA_USAGE = 6;
    public static final int DATA_STATUS = 7;
    public static final int DATA_REDSTONE_MODE = 8;
    public static final int DATA_SIDE_CONFIG = 9;
    // What the item in the input costs: Lapis, and Liquid Experience in mB.
    public static final int DATA_LAPIS_COST = 10;
    public static final int DATA_XP_COST = 11;
    // 1 when treasure enchantments are offered (the server's config).
    public static final int DATA_TREASURE = 12;
    public static final int DATA_VALUES = 13;

    public static final int INPUT_X = 43, INPUT_Y = 19;
    public static final int LAPIS_X = 43, LAPIS_Y = 47;
    public static final int OUTPUT_X = 93, OUTPUT_Y = 19;
    public static final int INVENTORY_Y = 140;

    // Client constructor, called with the block position written by the server.
    public ElectricEnchanterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), ElectricEnchanterBlockEntity.clientItems(),
                WideIntContainerData.client(DATA_VALUES));
    }

    public ElectricEnchanterMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.ELECTRIC_ENCHANTER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.ELECTRIC_ENCHANTER.get());
        addMachineSlot(ElectricEnchanterBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(ElectricEnchanterBlockEntity.SLOT_LAPIS, LAPIS_X, LAPIS_Y);
        addMachineSlot(ElectricEnchanterBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
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

    public Slot getSlotFor(int machineSlot) {
        return slots.get(machineSlot);
    }

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return value(DATA_ENERGY_CAPACITY);
    }

    public int getXp() {
        return value(DATA_XP);
    }

    public int getXpCapacity() {
        return value(DATA_XP_CAPACITY);
    }

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getTotal() {
        return value(DATA_TOTAL);
    }

    public int getUsage() {
        return value(DATA_USAGE);
    }

    public int getLapisCost() {
        return value(DATA_LAPIS_COST);
    }

    public int getXpCost() {
        return value(DATA_XP_COST);
    }

    public boolean treasureOffered() {
        return value(DATA_TREASURE) != 0;
    }
}
