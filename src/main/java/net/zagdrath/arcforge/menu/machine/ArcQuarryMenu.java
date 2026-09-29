/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Arc Quarry's main menu: its status, the replace slot and the 27-slot buffer, with Start/Stop, Reset and a
// button to its settings (see ArcQuarryConfigMenu).
public class ArcQuarryMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_TARGETS = 3;
    public static final int DATA_MINED = 4;
    public static final int DATA_SCAN_PERCENT = 5;
    public static final int DATA_STATUS = 6;
    public static final int DATA_REDSTONE_MODE = 7;
    public static final int DATA_SIDE_CONFIG = 8;
    public static final int DATA_VALUES = 9;

    public static final int BUTTON_START_STOP = 200;
    public static final int BUTTON_RESET = 201;
    public static final int BUTTON_SETTINGS = 202;

    // Positions from gui_layouts.json "main" (176x214).
    public static final int REPLACE_X = 124, REPLACE_Y = 17;
    public static final int BUFFER_X = 8, BUFFER_Y = 67;
    public static final int INVENTORY_Y = 133;

    // Client constructor, called with the block position written by the server.
    public ArcQuarryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), ArcQuarryBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public ArcQuarryMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.ARC_QUARRY.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.ARC_QUARRY.get());
        addMachineSlot(ArcQuarryBlockEntity.SLOT_REPLACE, REPLACE_X, REPLACE_Y);
        for (int i = 0; i < ArcQuarryBlockEntity.BUFFER_SLOTS; i++) {
            addMachineSlot(ArcQuarryBlockEntity.FIRST_BUFFER + i, BUFFER_X + (i % 9) * 18, BUFFER_Y + (i / 9) * 18);
        }
        finish(inventory, INVENTORY_Y);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        switch (buttonId) {
            case BUTTON_START_STOP -> quarry(ArcQuarryBlockEntity::toggleRunning);
            case BUTTON_RESET -> quarry(ArcQuarryBlockEntity::reset);
            case BUTTON_SETTINGS -> access.execute((level, pos) -> openSettings(player, pos));
            default -> {
                return super.clickMenuButton(player, buttonId);
            }
        }
        return true;
    }

    private void quarry(java.util.function.Consumer<ArcQuarryBlockEntity> action) {
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof ArcQuarryBlockEntity quarry) {
                action.accept(quarry);
            }
        });
    }

    // Opens the settings menu for the quarry at pos.
    public static void openSettings(Player player, BlockPos pos) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ArcQuarryConfigMenu(id, inventory, pos),
                Component.translatable("container.arcforge.arc_quarry_config")), pos);
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

    public ArcQuarryBlockEntity.State getState() {
        return ArcQuarryBlockEntity.State.byId(value(DATA_STATE));
    }

    public int getTargets() {
        return value(DATA_TARGETS);
    }

    public int getMined() {
        return value(DATA_MINED);
    }

    public int getScanPercent() {
        return value(DATA_SCAN_PERCENT);
    }
}
