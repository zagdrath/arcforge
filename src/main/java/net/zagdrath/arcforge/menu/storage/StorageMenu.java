/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.storage;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.zagdrath.arcforge.blockentity.storage.StorageBlockEntity;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// Fluid tank and energy cell menus: an input slot above an output slot, the player inventory, and
// synced data including the side configuration. One menu type serves every tier of a block.
public abstract class StorageMenu extends AbstractContainerMenu {
    public static final int SLOT_IN_X = 27, SLOT_IN_Y = 19;
    public static final int SLOT_OUT_X = 27, SLOT_OUT_Y = 53;

    protected static final int MACHINE_SLOTS = StorageBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INV_END = MACHINE_SLOTS + 27;
    private static final int PLAYER_HOTBAR_END = PLAYER_INV_END + 9;

    protected final ContainerLevelAccess access;
    protected final ContainerData data;
    private final Predicate<Block> validBlock;
    private final int sideConfigIndex;

    protected StorageMenu(MenuType<?> type, int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items,
            ContainerData data, int dataValues, int sideConfigIndex, Predicate<Block> validBlock) {
        super(type, containerId);
        checkContainerDataCount(data, dataValues * 2);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = data;
        this.validBlock = validBlock;
        this.sideConfigIndex = sideConfigIndex;

        addSlot(new ResourceHandlerSlot(items, items::set, StorageBlockEntity.SLOT_IN, SLOT_IN_X, SLOT_IN_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, StorageBlockEntity.SLOT_OUT, SLOT_OUT_X, SLOT_OUT_Y));
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    // Where shift-clicking an item from the player inventory sends it: a machine slot index, or -1.
    protected abstract int quickMoveTarget(ItemStack stack);

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
        int target = slotIndex < MACHINE_SLOTS ? -1 : quickMoveTarget(stack);

        if (slotIndex < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (target >= 0) {
            if (!moveItemStackTo(stack, target, target + 1, false)) {
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
        return access.evaluate((level, pos) -> validBlock.test(level.getBlockState(pos).getBlock())
                && player.isWithinBlockInteractionRange(pos, 4.0), true);
    }

    public Slot getInputSlot() {
        return slots.get(StorageBlockEntity.SLOT_IN);
    }

    public Slot getOutputSlot() {
        return slots.get(StorageBlockEntity.SLOT_OUT);
    }

    protected int value(int index) {
        return WideIntContainerData.read(data, index);
    }

    public SideMode getSideMode(RelativeSide side) {
        return SideConfig.unpack(value(sideConfigIndex), side);
    }
}
