/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.tool;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.zagdrath.arcforge.item.storage.LeadBoxItem;
import net.zagdrath.arcforge.registry.ModMenuTypes;

// A Lead Box's nine slots (3x3), for the box in the player's main hand: loaded from its minecraft:container when the
// menu opens and written back on every change. The box's own hotbar slot can't be moved while it's open, and nothing
// that holds items goes in.
public class LeadBoxMenu extends AbstractContainerMenu {
    public static final int GRID_X = 62, GRID_Y = 17;

    private final Player player;
    private final int boxSlot;
    private final int hotbarSlotIndex;
    // The box this menu opened, so nothing is written into another one put in its slot meanwhile.
    private final ItemStack opened;
    private boolean loading;
    private final SimpleContainer contents = new SimpleContainer(LeadBoxItem.SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            save();
        }
    };

    // Client constructor, called with the hotbar slot written by the server.
    public LeadBoxMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readVarInt());
    }

    public LeadBoxMenu(int containerId, Inventory inventory, int boxSlot) {
        super(ModMenuTypes.LEAD_BOX.get(), containerId);
        this.player = inventory.player;
        this.boxSlot = boxSlot;
        this.opened = inventory.getItem(boxSlot);
        load();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(contents, row * 3 + column, GRID_X + column * 18, GRID_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.getItem().canFitInsideContainerItems() && !stack.has(DataComponents.CONTAINER)
                                && !stack.has(DataComponents.BUNDLE_CONTENTS);
                    }
                });
            }
        }
        addStandardInventorySlots(inventory, 8, 84);
        this.hotbarSlotIndex = LeadBoxItem.SLOTS + 27 + boxSlot;
    }

    public ItemStack box() {
        return player.getInventory().getItem(boxSlot);
    }

    private void load() {
        loading = true;
        NonNullList<ItemStack> stacks = NonNullList.withSize(LeadBoxItem.SLOTS, ItemStack.EMPTY);
        box().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(stacks);
        for (int i = 0; i < LeadBoxItem.SLOTS; i++) {
            contents.setItem(i, stacks.get(i));
        }
        loading = false;
    }

    private void save() {
        ItemStack box = box();
        if (loading || box != opened || !(box.getItem() instanceof LeadBoxItem)) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < LeadBoxItem.SLOTS; i++) {
            stacks.add(contents.getItem(i).copy());
        }
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks));
    }

    // The box's own hotbar slot can't be picked up, swapped or thrown while it's open.
    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
        if (slotIndex == hotbarSlotIndex || (containerInput == ContainerInput.SWAP && buttonNum == boxSlot)) {
            return;
        }
        super.clicked(slotIndex, buttonNum, containerInput, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index == hotbarSlotIndex) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < LeadBoxItem.SLOTS) {
            if (!moveItemStackTo(stack, LeadBoxItem.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!slots.get(0).mayPlace(stack) || !moveItemStackTo(stack, 0, LeadBoxItem.SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getInventory().getSelectedSlot() == boxSlot && box() == opened && box().getItem() instanceof LeadBoxItem;
    }
}
