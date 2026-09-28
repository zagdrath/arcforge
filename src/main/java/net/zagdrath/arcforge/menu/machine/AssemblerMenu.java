/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.blockentity.machine.AssemblerBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Assembler's menu. Its 3x3 pattern cells are display-only slots (so the game syncs them to the client), set
// through menu buttons: left-click with an item copies it (count 1), an empty hand or right-click clears it. A
// hidden slot carries what the pattern crafts, for the result ghost.
public class AssemblerMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_TOTAL = 4;
    public static final int DATA_STATUS = 5;
    public static final int DATA_REDSTONE_MODE = 6;
    public static final int DATA_SIDE_CONFIG = 7;
    public static final int DATA_VALUES = 8;

    // Pattern cell i: 200 + i sets it from the carried stack (or clears it with an empty hand), 209 + i clears it.
    public static final int BUTTON_SET_CELL = 200;
    public static final int BUTTON_CLEAR_CELL = 209;

    // Positions from gui_layouts.json "assembler" (176x206).
    public static final int PATTERN_X = 26, PATTERN_Y = 18;
    public static final int BUFFER_X = 8, BUFFER_Y = 76;
    public static final int OUTPUT_X = 123, OUTPUT_Y = 35;
    public static final int REMAINDER_X = 150, REMAINDER_Y = 26, REMAINDER_Y2 = 44;
    public static final int INVENTORY_Y = 124;
    // Tabs: Energy, Redstone, Sides, Upgrades.
    private static final int UPGRADES_TAB = 3;

    private final SimpleContainer pattern;
    private final int firstPatternSlot;
    private final Slot previewSlot;

    // Client constructor, called with the block position written by the server.
    public AssemblerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), new SimpleContainer(AssemblerBlockEntity.PATTERN_SIZE), new SimpleContainer(1));
    }

    private AssemblerMenu(int containerId, Inventory inventory, BlockPos pos, SimpleContainer pattern, SimpleContainer preview) {
        this(containerId, inventory, pos, AssemblerBlockEntity.clientItems(pattern), WideIntContainerData.client(DATA_VALUES), pattern, preview);
    }

    public AssemblerMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data,
            SimpleContainer pattern, SimpleContainer preview) {
        super(ModMenuTypes.ASSEMBLER.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.ASSEMBLER.get());
        this.pattern = pattern;
        for (int i = 0; i < AssemblerBlockEntity.BUFFER_SLOTS; i++) {
            addMachineSlot(AssemblerBlockEntity.FIRST_BUFFER + i, BUFFER_X + (i % 9) * 18, BUFFER_Y + (i / 9) * 18);
        }
        addMachineSlot(AssemblerBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y);
        addMachineSlot(AssemblerBlockEntity.FIRST_REMAINDER, REMAINDER_X, REMAINDER_Y);
        addMachineSlot(AssemblerBlockEntity.FIRST_REMAINDER + 1, REMAINDER_X, REMAINDER_Y2);
        finish(inventory, UPGRADES_TAB, INVENTORY_Y);
        // After everything else, so the shared shift-click logic never counts them.
        this.firstPatternSlot = slots.size();
        for (int i = 0; i < AssemblerBlockEntity.PATTERN_SIZE; i++) {
            addSlot(new GhostSlot(pattern, i, PATTERN_X + (i % 3) * 18, PATTERN_Y + (i / 3) * 18, true));
        }
        this.previewSlot = addSlot(new GhostSlot(preview, 0, OUTPUT_X, OUTPUT_Y, false));
    }

    // A slot that only shows an item: nothing can be put in or taken out by hand.
    private static final class GhostSlot extends Slot {
        private final boolean visible;

        GhostSlot(Container container, int index, int x, int y, boolean visible) {
            super(container, index, x, y);
            this.visible = visible;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return visible;
        }
    }

    public boolean isPatternSlot(Slot slot) {
        return slot.index >= firstPatternSlot && slot.index < firstPatternSlot + AssemblerBlockEntity.PATTERN_SIZE;
    }

    // Which pattern cell a slot is (0-8).
    public int patternCell(Slot slot) {
        return slot.index - firstPatternSlot;
    }

    public ItemStack getPatternCell(int cell) {
        return pattern.getItem(cell);
    }

    // What the pattern crafts (EMPTY without a recipe).
    public ItemStack getPreview() {
        return previewSlot.getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= BUTTON_SET_CELL && buttonId < BUTTON_SET_CELL + AssemblerBlockEntity.PATTERN_SIZE) {
            ItemStack carried = getCarried();
            pattern.setItem(buttonId - BUTTON_SET_CELL, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            return true;
        }
        if (buttonId >= BUTTON_CLEAR_CELL && buttonId < BUTTON_CLEAR_CELL + AssemblerBlockEntity.PATTERN_SIZE) {
            pattern.setItem(buttonId - BUTTON_CLEAR_CELL, ItemStack.EMPTY);
            return true;
        }
        return super.clickMenuButton(player, buttonId);
    }

    // Sets the whole pattern, row by row (JEI's +, through AssemblerPatternPayload). Server side.
    public void setPattern(List<ItemStack> cells) {
        for (int i = 0; i < AssemblerBlockEntity.PATTERN_SIZE; i++) {
            ItemStack cell = i < cells.size() ? cells.get(i) : ItemStack.EMPTY;
            pattern.setItem(i, cell.isEmpty() ? ItemStack.EMPTY : cell.copyWithCount(1));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return slotIndex >= firstPatternSlot ? ItemStack.EMPTY : super.quickMoveStack(player, slotIndex);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return !(target instanceof GhostSlot) && super.canTakeItemForPickAll(carried, target);
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
}
