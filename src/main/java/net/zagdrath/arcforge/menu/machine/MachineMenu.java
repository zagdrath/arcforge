/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import java.util.ArrayList;
import java.util.List;

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
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.slot.ToggleableSlot;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// Shared menu for single-block machines: the machine's own slots, then its upgrade slots (which live in
// the Upgrades side tab), then the player's inventory. Subclasses add their slots in the constructor
// before calling finish(), and declare where the status, redstone and side data sit.
public abstract class MachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_CLEAR_SIDES = MachineMenuButtons.CLEAR_SIDES;
    // Upgrade slots sit in the Upgrades side tab (x 172, the Nth tab at y = 6 + N * 25), with slot items at +9,+25.
    private static final int UPGRADE_SLOT_X = 181, UPGRADE_SLOT_PITCH = 20;

    protected final FilteredItemHandler items;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final Block block;
    private final List<ToggleableSlot> upgradeSlots = new ArrayList<>();
    private int machineSlots;

    protected MachineMenu(MenuType<?> type, int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items,
            ContainerData data, int dataValues, Block block) {
        super(type, containerId);
        checkContainerDataCount(data, dataValues * 2);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.items = items;
        this.data = data;
        this.block = block;
    }

    protected void addMachineSlot(int index, int x, int y) {
        addSlot(new ResourceHandlerSlot(items, items::set, index, x, y));
    }

    // Adds the upgrade slots (for the Upgrades tab at the given position in the tab stack), the player
    // inventory and the data slots. Call once, after the machine's own slots.
    protected void finish(Inventory inventory, int firstUpgradeSlot, int upgradesTabIndex) {
        int y = 6 + upgradesTabIndex * 25 + 25;
        for (int i = 0; i < MachineBlockEntity.UPGRADE_SLOTS; i++) {
            ToggleableSlot slot = new ToggleableSlot(items, items::set, firstUpgradeSlot + i, UPGRADE_SLOT_X + i * UPGRADE_SLOT_PITCH, y);
            upgradeSlots.add(slot);
            addSlot(slot);
        }
        machineSlots = slots.size();
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    public static int redstoneButtonId(RedstoneMode mode) {
        return MachineMenuButtons.redstoneButtonId(mode);
    }

    public static int sideButtonId(RelativeSide side, int action) {
        return MachineMenuButtons.sideButtonId(side, action);
    }

    // Runs on the server when the client clicks a redstone or side-config button.
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return MachineMenuButtons.handle(access, buttonId);
    }

    // Shift-click: machine slots go to the player; player items go to the first machine slot that
    // accepts them, else between the inventory and the hotbar.
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int inventoryEnd = machineSlots + 27;
        int hotbarEnd = inventoryEnd + 9;

        if (slotIndex < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, hotbarEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveIntoMachine(stack)) {
            boolean moved = slotIndex < inventoryEnd
                    ? moveItemStackTo(stack, inventoryEnd, hotbarEnd, false)
                    : moveItemStackTo(stack, machineSlots, inventoryEnd, false);
            if (!moved) {
                return ItemStack.EMPTY;
            }
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

    private boolean moveIntoMachine(ItemStack stack) {
        ItemResource resource = ItemResource.of(stack);
        for (int i = 0; i < machineSlots && !stack.isEmpty(); i++) {
            if (slots.get(i) instanceof ResourceHandlerSlot machineSlot && items.isValid(machineSlot.getContainerSlot(), resource)
                    && moveItemStackTo(stack, i, i + 1, false)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, block);
    }

    public List<ToggleableSlot> getUpgradeSlots() {
        return upgradeSlots;
    }

    protected int value(int index) {
        return WideIntContainerData.read(data, index);
    }

    protected abstract int statusIndex();

    protected abstract int redstoneIndex();

    protected abstract int sidesIndex();

    public MachineStatus getStatus() {
        return MachineStatus.byId(value(statusIndex()));
    }

    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.byId(value(redstoneIndex()));
    }

    public SideMode getSideMode(RelativeSide side) {
        return SideConfig.unpack(value(sidesIndex()), side);
    }
}
