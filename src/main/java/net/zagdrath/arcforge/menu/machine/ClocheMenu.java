/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.blockentity.farming.ClocheBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.GlassClocheBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.GrowChamberBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.HydroponicCellBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.slot.ToggleableSlot;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Glass Cloche, Grow Chamber and Hydroponic Cell share a layout: the tank on the left, the seed, soil and fertilizer
// slots, the growth arrow and the four output slots, then (powered) the Carbon Dioxide tank and the FE gauge. The
// Hydroponic Cell has no soil slot (its slot stays hidden) and its fertilizer slot sits there instead.
public class ClocheMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    // The tank's fluid as a registry id, -1 when empty.
    public static final int DATA_FLUID = 2;
    public static final int DATA_FLUID_AMOUNT = 3;
    public static final int DATA_FLUID_CAPACITY = 4;
    // The Hydroponic Cell's Carbon Dioxide.
    public static final int DATA_GAS_AMOUNT = 5;
    public static final int DATA_GAS_CAPACITY = 6;
    public static final int DATA_PROGRESS = 7;
    public static final int DATA_TOTAL = 8;
    public static final int DATA_USAGE = 9;
    public static final int DATA_FERTILIZER = 10;
    // Growth per tick x100.
    public static final int DATA_RATE = 11;
    public static final int DATA_STATUS = 12;
    public static final int DATA_REDSTONE_MODE = 13;
    public static final int DATA_SIDE_CONFIG = 14;
    public static final int DATA_VALUES = 15;

    public static final int SEED_X = 30, SEED_Y = 20;
    public static final int SOIL_X = 30, SOIL_Y = 40;
    public static final int FERTILIZER_X = 50, FERTILIZER_Y = 40;
    public static final int OUTPUT_X = 100, OUTPUT_Y = 21, OUTPUT_PITCH = 18;

    private final ClocheBlockEntity.Kind kind;

    public static ClocheMenu glassCloche(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        return client(ClocheBlockEntity.Kind.GLASS_CLOCHE, containerId, inventory, extraData);
    }

    public static ClocheMenu growChamber(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        return client(ClocheBlockEntity.Kind.GROW_CHAMBER, containerId, inventory, extraData);
    }

    public static ClocheMenu hydroponicCell(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        return client(ClocheBlockEntity.Kind.HYDROPONIC_CELL, containerId, inventory, extraData);
    }

    // Client constructor, called with the block position written by the server.
    private static ClocheMenu client(ClocheBlockEntity.Kind kind, int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        MachineItemHandler items = ClocheBlockEntity.clientItems(kind, switch (kind) {
            case GLASS_CLOCHE -> GlassClocheBlockEntity.UPGRADES;
            case GROW_CHAMBER -> GrowChamberBlockEntity.UPGRADES;
            case HYDROPONIC_CELL -> HydroponicCellBlockEntity.UPGRADES;
        });
        return new ClocheMenu(kind, containerId, inventory, extraData.readBlockPos(), items, WideIntContainerData.client(DATA_VALUES));
    }

    public ClocheMenu(ClocheBlockEntity.Kind kind, int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(menuType(kind), containerId, inventory, pos, items, data, DATA_VALUES, block(kind));
        this.kind = kind;
        addMachineSlot(ClocheBlockEntity.SLOT_SEED, SEED_X, SEED_Y);
        if (kind.hydroponic()) {
            // Kept (so slot numbers line up) but hidden: nothing goes in it.
            ToggleableSlot soil = new ToggleableSlot(items, items::set, ClocheBlockEntity.SLOT_SOIL, SOIL_X, SOIL_Y);
            soil.setActive(false);
            addSlot(soil);
            addMachineSlot(ClocheBlockEntity.SLOT_FERTILIZER, SOIL_X, SOIL_Y);
        } else {
            addMachineSlot(ClocheBlockEntity.SLOT_SOIL, SOIL_X, SOIL_Y);
            addMachineSlot(ClocheBlockEntity.SLOT_FERTILIZER, FERTILIZER_X, FERTILIZER_Y);
        }
        for (int i = 0; i < ClocheBlockEntity.OUTPUT_SLOTS; i++) {
            addMachineSlot(ClocheBlockEntity.SLOT_OUTPUT_FIRST + i, OUTPUT_X + (i % 2) * OUTPUT_PITCH, OUTPUT_Y + (i / 2) * OUTPUT_PITCH);
        }
        finish(inventory);
    }

    private static MenuType<?> menuType(ClocheBlockEntity.Kind kind) {
        return switch (kind) {
            case GLASS_CLOCHE -> ModMenuTypes.GLASS_CLOCHE.get();
            case GROW_CHAMBER -> ModMenuTypes.GROW_CHAMBER.get();
            case HYDROPONIC_CELL -> ModMenuTypes.HYDROPONIC_CELL.get();
        };
    }

    private static Block block(ClocheBlockEntity.Kind kind) {
        return switch (kind) {
            case GLASS_CLOCHE -> ModBlocks.GLASS_CLOCHE.get();
            case GROW_CHAMBER -> ModBlocks.GROW_CHAMBER.get();
            case HYDROPONIC_CELL -> ModBlocks.HYDROPONIC_CELL.get();
        };
    }

    public ClocheBlockEntity.Kind kind() {
        return kind;
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

    public Fluid getFluid() {
        int id = value(DATA_FLUID);
        return id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }

    public int getFluidAmount() {
        return value(DATA_FLUID_AMOUNT);
    }

    public int getFluidCapacity() {
        return value(DATA_FLUID_CAPACITY);
    }

    public int getGasAmount() {
        return value(DATA_GAS_AMOUNT);
    }

    public int getGasCapacity() {
        return value(DATA_GAS_CAPACITY);
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

    public int getFertilizer() {
        return value(DATA_FERTILIZER);
    }

    // Growth per tick (1 = the recipe's time).
    public double getRate() {
        return value(DATA_RATE) / 100.0;
    }
}
