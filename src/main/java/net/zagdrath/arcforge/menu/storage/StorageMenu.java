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
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// Storage block menus: a drain slot above a fill slot, the player inventory, and synced data including
// the side configuration. One menu type serves every tier of a block.
public abstract class StorageMenu extends AbstractContainerMenu {
    // Energy Cells and Fluid Tanks put their slots here; Heat Cells and Pressurized Cylinders at SLOT_X_LEFT.
    public static final int SLOT_X = 27, SLOT_X_LEFT = 9;
    public static final int SLOT_IN_Y = 19, SLOT_OUT_Y = 53;

    protected static final int MACHINE_SLOTS = StorageBlockEntity.SLOT_COUNT;

    protected final ContainerLevelAccess access;
    protected final ContainerData data;
    private final Predicate<Block> validBlock;
    private final int sideConfigIndex;
    // Machine slots in this menu (0 or MACHINE_SLOTS), then the inventory and the hotbar.
    private final int machineSlots;
    private final int playerInventoryEnd;
    private final int playerHotbarEnd;

    protected StorageMenu(MenuType<?> type, int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items,
            ContainerData data, int dataValues, int sideConfigIndex, Predicate<Block> validBlock) {
        this(type, containerId, inventory, pos, items, data, dataValues, sideConfigIndex, validBlock, SLOT_X);
    }

    protected StorageMenu(MenuType<?> type, int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items,
            ContainerData data, int dataValues, int sideConfigIndex, Predicate<Block> validBlock, int slotX) {
        super(type, containerId);
        checkContainerDataCount(data, dataValues * 2);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = data;
        this.validBlock = validBlock;
        this.sideConfigIndex = sideConfigIndex;

        addSlot(new ResourceHandlerSlot(items, items::set, StorageBlockEntity.SLOT_IN, slotX, SLOT_IN_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, StorageBlockEntity.SLOT_OUT, slotX, SLOT_OUT_Y));
        this.machineSlots = MACHINE_SLOTS;
        this.playerInventoryEnd = machineSlots + 27;
        this.playerHotbarEnd = playerInventoryEnd + 9;
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    // Where shift-clicking an item from the player inventory sends it: a machine slot index, or -1.
    protected abstract int quickMoveTarget(ItemStack stack);

    // For portable storage: one holding something goes to the drain slot, an empty one to the fill slot.
    protected static int portableTarget(ItemStack stack) {
        return stack.getItem() instanceof PortableStorageItem portable && portable.amount(stack) > 0
                ? StorageBlockEntity.SLOT_IN : StorageBlockEntity.SLOT_OUT;
    }

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
        int target = slotIndex < machineSlots ? -1 : quickMoveTarget(stack);

        if (slotIndex < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, playerHotbarEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (target >= 0) {
            if (!moveItemStackTo(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < playerInventoryEnd) {
            if (!moveItemStackTo(stack, playerInventoryEnd, playerHotbarEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, machineSlots, playerInventoryEnd, false)) {
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

    public boolean isAutoEject() {
        return SideConfig.unpackAutoEject(value(sideConfigIndex));
    }
}
