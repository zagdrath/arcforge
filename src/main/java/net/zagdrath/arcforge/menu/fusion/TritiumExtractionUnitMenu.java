/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.fusion;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.fusion.TritiumExtractionUnitBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Tritium Extraction Unit's GUI (the Fiberizer's layout, a second tank beside the first): the heat buffer, the rod
// in, the arrow, the cladding out, the tritium and helium tanks, the temperature bar.
public class TritiumExtractionUnitMenu extends MachineMenu {
    public static final int DATA_HEAT = 0;
    public static final int DATA_HEAT_CAPACITY = 1;
    public static final int DATA_TEMPERATURE = 2;
    public static final int DATA_MIN_TEMP = 3;
    public static final int DATA_HEAT_USAGE = 4;
    public static final int DATA_PROGRESS = 5;
    public static final int DATA_TOTAL = 6;
    public static final int DATA_TRITIUM = 7;
    public static final int DATA_HELIUM = 8;
    public static final int DATA_TANK = 9;
    public static final int DATA_STATUS = 10;
    public static final int DATA_REDSTONE_MODE = 11;
    public static final int DATA_SIDE_CONFIG = 12;
    public static final int DATA_VALUES = 13;

    public static final int INPUT_X = 44, INPUT_Y = 35;
    public static final int OUTPUT_X = 101, OUTPUT_Y = 35;

    public TritiumExtractionUnitMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), TritiumExtractionUnitBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public TritiumExtractionUnitMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.TRITIUM_EXTRACTION_UNIT.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.TRITIUM_EXTRACTION_UNIT.get());
        addMachineSlot(TritiumExtractionUnitBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y);
        addMachineSlot(TritiumExtractionUnitBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
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

    public int get(int index) {
        return value(index);
    }
}
