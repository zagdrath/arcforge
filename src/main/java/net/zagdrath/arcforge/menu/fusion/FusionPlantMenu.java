/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.fusion;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.zagdrath.arcforge.blockentity.fusion.FusionPlantBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The GUI of the Cryogenic Plant, the Cryogenic Distillation Column and the Tritium Plant (176x166, the Heavy Water
// Tower's layout): the FE gauge, the input tank, the house screen (size, progress, what the last step made, status)
// and one to four output tanks.
public class FusionPlantMenu extends MachineMenu {
    public static final int DATA_KIND = 0;
    public static final int DATA_STATUS = 1;
    public static final int DATA_FORMED = 2;
    public static final int DATA_REDSTONE_MODE = 3;
    public static final int DATA_SIDE_CONFIG = 4;
    public static final int DATA_ENERGY = 5;
    public static final int DATA_ENERGY_CAPACITY = 6;
    public static final int DATA_INPUT = 7;
    public static final int DATA_INPUT_FLUID = 8;
    public static final int DATA_CAPACITY = 9;
    public static final int DATA_SIZE = 10;
    public static final int DATA_PROGRESS = 11;
    public static final int DATA_TIME = 12;
    public static final int DATA_LAST_MADE = 13;
    public static final int DATA_ENERGY_USE = 14;
    public static final int DATA_OUTPUT = 15;        // 4 tanks' amounts
    public static final int DATA_OUTPUT_FLUID = 19;  // 4 tanks' fluid ids
    public static final int DATA_VALUES = 23;

    private final FusionPlantBlockEntity.Kind kind;

    public static FusionPlantMenu client(FusionPlantBlockEntity.Kind kind, int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        return new FusionPlantMenu(kind, containerId, inventory, extraData.readBlockPos(), FusionPlantBlockEntity.clientItems(),
                WideIntContainerData.client(DATA_VALUES));
    }

    public FusionPlantMenu(FusionPlantBlockEntity.Kind kind, int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(type(kind), containerId, inventory, pos, items, data, DATA_VALUES, kind.controller());
        this.kind = kind;
        finish(inventory);
    }

    private static MenuType<FusionPlantMenu> type(FusionPlantBlockEntity.Kind kind) {
        return switch (kind) {
            case CRYOGENIC_PLANT -> ModMenuTypes.CRYOGENIC_PLANT.get();
            case CRYOGENIC_COLUMN -> ModMenuTypes.CRYOGENIC_COLUMN.get();
            case TRITIUM_PLANT -> ModMenuTypes.TRITIUM_PLANT.get();
        };
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

    public FusionPlantBlockEntity.Kind kind() {
        return kind;
    }

    public int get(int index) {
        return value(index);
    }

    public boolean isFormed() {
        return value(DATA_FORMED) != 0;
    }
}
