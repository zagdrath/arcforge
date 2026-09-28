/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.machine.quarry.BlockFilter;
import net.zagdrath.arcforge.machine.quarry.QuarrySettings;
import net.zagdrath.arcforge.menu.common.MenuReach;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;

// The Arc Quarry's settings: area, block filter and switches. The screen reads the settings from the quarry as
// synced to the client; every change is a menu button or a payload (ArcQuarryAreaPayload, ArcQuarryTagPayload)
// the server applies to the quarry, which clamps it and syncs it back. The player's inventory is here so items
// can be picked up to set filter cells.
public class ArcQuarryConfigMenu extends AbstractContainerMenu {
    // Filter cell i: 300 + i sets it from the carried block (or clears it with an empty hand); 320 + i and 340 + i
    // step its tag chip forward and back.
    public static final int BUTTON_CELL = 300;
    public static final int BUTTON_TAG_NEXT = 320;
    public static final int BUTTON_TAG_PREVIOUS = 340;
    public static final int BUTTON_LIST_MODE = 360;
    public static final int BUTTON_SILK = 361;
    public static final int BUTTON_REPLACE = 362;
    public static final int BUTTON_SHOW_AREA = 363;
    public static final int BUTTON_SCAN = 364;
    public static final int BUTTON_BACK = 365;
    // Filter cell i: 370 + i clears it (right-click), whatever is carried.
    public static final int BUTTON_CLEAR_CELL = 370;

    public static final int INVENTORY_Y = 155;

    private final ContainerLevelAccess access;
    private final BlockPos pos;

    // Client constructor, called with the block position written by the server.
    public ArcQuarryConfigMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    public ArcQuarryConfigMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuTypes.ARC_QUARRY_CONFIG.get(), containerId);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
    }

    public BlockPos getPos() {
        return pos;
    }

    // The quarry, on either side (the client's copy has the synced settings).
    public @Nullable ArcQuarryBlockEntity quarry(Player player) {
        return player.level().getBlockEntity(pos) instanceof ArcQuarryBlockEntity quarry ? quarry : null;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        ArcQuarryBlockEntity quarry = quarry(player);
        if (quarry == null) {
            return false;
        }
        if (buttonId == BUTTON_BACK) {
            player.openMenu(quarry, pos);
            return true;
        }
        if (buttonId == BUTTON_SCAN) {
            quarry.scanOnly();
            return true;
        }
        UnaryOperator<QuarrySettings> change = change(buttonId, getCarried());
        if (change == null) {
            return false;
        }
        quarry.setSettings(change.apply(quarry.getSettings()));
        return true;
    }

    // What a button does to the settings, or null if it isn't one of ours (or does nothing, like a non-block item).
    private static @Nullable UnaryOperator<QuarrySettings> change(int buttonId, ItemStack carried) {
        if (buttonId >= BUTTON_CELL && buttonId < BUTTON_CELL + QuarrySettings.FILTER_SIZE) {
            int cell = buttonId - BUTTON_CELL;
            FilterSettings.Entry entry = carried.isEmpty() ? FilterSettings.Entry.EMPTY : BlockFilter.of(carried);
            return entry == null ? null : settings -> settings.withEntry(cell, entry);
        }
        if (buttonId >= BUTTON_CLEAR_CELL && buttonId < BUTTON_CLEAR_CELL + QuarrySettings.FILTER_SIZE) {
            int cell = buttonId - BUTTON_CLEAR_CELL;
            return settings -> settings.withEntry(cell, FilterSettings.Entry.EMPTY);
        }
        if (buttonId >= BUTTON_TAG_NEXT && buttonId < BUTTON_TAG_NEXT + QuarrySettings.FILTER_SIZE) {
            int cell = buttonId - BUTTON_TAG_NEXT;
            return settings -> settings.withEntry(cell, BlockFilter.cycleTag(settings.entry(cell), 1));
        }
        if (buttonId >= BUTTON_TAG_PREVIOUS && buttonId < BUTTON_TAG_PREVIOUS + QuarrySettings.FILTER_SIZE) {
            int cell = buttonId - BUTTON_TAG_PREVIOUS;
            return settings -> settings.withEntry(cell, BlockFilter.cycleTag(settings.entry(cell), -1));
        }
        return switch (buttonId) {
            case BUTTON_LIST_MODE -> settings -> settings.withDeny(!settings.deny());
            case BUTTON_SILK -> settings -> settings.withSilkTouch(!settings.silkTouch());
            case BUTTON_REPLACE -> settings -> settings.withReplace(!settings.replace());
            case BUTTON_SHOW_AREA -> settings -> settings.withShowArea(!settings.showArea());
            default -> null;
        };
    }

    // Only the player's inventory: shift-click moves between it and the hotbar.
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        var slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = slotIndex < 27 ? moveItemStackTo(stack, 27, 36, false) : moveItemStackTo(stack, 0, 27, false);
        if (!moved) {
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
        return MenuReach.stillValid(access, player, ModBlocks.ARC_QUARRY.get());
    }
}
