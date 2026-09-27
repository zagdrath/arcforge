/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// Served by the furnace's port; opened from any of its blocks.
public class ArcforgeFurnaceMenu extends AbstractContainerMenu {
    // Logical data indices (each is a full int, see WideIntContainerData).
    public static final int DATA_FORMED = 0;
    public static final int DATA_HEAT = 1;
    public static final int DATA_MAX_HEAT = 2;
    public static final int DATA_MIN_HEAT = 3;
    public static final int DATA_PROGRESS = 4;
    public static final int DATA_PROGRESS_TOTAL = 5;
    public static final int DATA_BURN_TIME = 6;
    public static final int DATA_BURN_TOTAL = 7;
    public static final int DATA_REDSTONE_MODE = 8;
    public static final int DATA_SIDE_CONFIG = 9;
    public static final int DATA_VALUES = 10;

    // Slot positions from arcforge_furnace_gui_layout.json.
    public static final int INPUT_X = 9, INPUT_Y = 23;
    public static final int FUEL_X = 29, FUEL_Y = 23;
    public static final int OUTPUT_X = 85, OUTPUT_Y = 23;
    public static final int BYPRODUCT_X = 115, BYPRODUCT_Y = 23;

    private static final int MACHINE_SLOTS = ArcforgeFurnaceBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INV_END = MACHINE_SLOTS + 27;
    private static final int PLAYER_HOTBAR_END = PLAYER_INV_END + 9;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    // Client constructor, called with the port's position written by the server.
    public ArcforgeFurnaceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new FilteredItemHandler(MACHINE_SLOTS, (slot, resource) -> ArcforgeFurnaceBlockEntity.isItemValid(null, slot, resource), () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public ArcforgeFurnaceMenu(int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items, ContainerData data) {
        super(ModMenuTypes.ARCFORGE_FURNACE.get(), containerId);
        checkContainerDataCount(data, DATA_VALUES * 2);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = data;

        addSlot(new ResourceHandlerSlot(items, items::set, ArcforgeFurnaceBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, ArcforgeFurnaceBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, ArcforgeFurnaceBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, ArcforgeFurnaceBlockEntity.SLOT_BYPRODUCT, BYPRODUCT_X, BYPRODUCT_Y));
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    // Runs on the server when the client clicks a redstone or side-config button.
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return MachineMenuButtons.handle(access, buttonId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        ItemResource resource = ItemResource.of(stack);

        if (slotIndex < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (ArcforgeFurnaceBlockEntity.isItemValid(player.level(), ArcforgeFurnaceBlockEntity.SLOT_INPUT, resource)) {
            if (!moveItemStackTo(stack, ArcforgeFurnaceBlockEntity.SLOT_INPUT, ArcforgeFurnaceBlockEntity.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (ArcforgeFurnaceBlockEntity.isItemValid(player.level(), ArcforgeFurnaceBlockEntity.SLOT_FUEL, resource)) {
            if (!moveItemStackTo(stack, ArcforgeFurnaceBlockEntity.SLOT_FUEL, ArcforgeFurnaceBlockEntity.SLOT_FUEL + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < PLAYER_INV_END) {
            if (!moveItemStackTo(stack, PLAYER_INV_END, PLAYER_HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_INV_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.ARCFORGE_FURNACE_PORT.get());
    }

    public Slot getSlotFor(int machineSlot) {
        return slots.get(machineSlot);
    }

    private int value(int index) {
        return WideIntContainerData.read(data, index);
    }

    public boolean isFormed() {
        return value(DATA_FORMED) != 0;
    }

    // Current temperature in °C.
    public int getHeat() {
        return value(DATA_HEAT);
    }

    public int getMaxHeat() {
        return value(DATA_MAX_HEAT);
    }

    // Lowest temperature the current smelt progresses at, in °C.
    public int getMinHeat() {
        return value(DATA_MIN_HEAT);
    }

    public int getProgress() {
        return value(DATA_PROGRESS);
    }

    public int getProgressTotal() {
        return value(DATA_PROGRESS_TOTAL);
    }

    public int getBurnTime() {
        return value(DATA_BURN_TIME);
    }

    public int getBurnTotal() {
        return value(DATA_BURN_TOTAL);
    }

    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.byId(value(DATA_REDSTONE_MODE));
    }

    public SideMode getSideMode(RelativeSide side) {
        return SideConfig.unpack(value(DATA_SIDE_CONFIG), side);
    }

    public boolean isAutoEject() {
        return SideConfig.unpackAutoEject(value(DATA_SIDE_CONFIG));
    }
}
