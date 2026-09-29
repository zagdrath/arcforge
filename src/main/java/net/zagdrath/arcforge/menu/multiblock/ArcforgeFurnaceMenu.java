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
import net.zagdrath.arcforge.menu.common.MenuReach;
import net.zagdrath.arcforge.menu.common.PortSync;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.network.PortsPayload;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.security.SecuredMenu;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

import java.util.List;

// Served by the furnace's port; opened from any of its blocks.
public class ArcforgeFurnaceMenu extends AbstractContainerMenu implements SecuredMenu {
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
    // mB of oxygen in the tank, and the current smelt's oxygen speed-up x100 (0 when not boosted).
    public static final int DATA_OXYGEN = 10;
    public static final int DATA_BOOST = 11;
    public static final int DATA_VALUES = 12;

    // Slot positions from arcforge_furnace_gui_layout.json.
    public static final int METAL_X = 9, METAL_Y = 23;
    public static final int ADDITIVE_X = 27, ADDITIVE_Y = 23;
    public static final int ADDITIVE_2_X = 45, ADDITIVE_2_Y = 23;
    public static final int COKE_X = 63, COKE_Y = 23;
    public static final int OUTPUT_X = 115, OUTPUT_Y = 23;
    public static final int BYPRODUCT_X = 145, BYPRODUCT_Y = 23;

    private static final int MACHINE_SLOTS = ArcforgeFurnaceBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INV_END = MACHINE_SLOTS + 27;
    private static final int PLAYER_HOTBAR_END = PLAYER_INV_END + 9;

    private final ContainerLevelAccess access;
    private final PortSync ports;
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
        this.ports = new PortSync(inventory, pos);
        this.data = data;

        addSlot(new ResourceHandlerSlot(items, items::set, ArcforgeFurnaceBlockEntity.SLOT_METAL, METAL_X, METAL_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE, ADDITIVE_X, ADDITIVE_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE_2, ADDITIVE_2_X, ADDITIVE_2_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, ArcforgeFurnaceBlockEntity.SLOT_COKE, COKE_X, COKE_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, ArcforgeFurnaceBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, ArcforgeFurnaceBlockEntity.SLOT_BYPRODUCT, BYPRODUCT_X, BYPRODUCT_Y));
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    // Runs on the server when the client clicks a redstone or side-config button.
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return MachineMenuButtons.handle(access, player, buttonId);
    }

    // Multiblocks: their ports, for the Ports tab (see PortSync).
    public List<PortsPayload.Entry> getPorts() {
        return PortsPayload.forMenu(containerId);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        ports.update(containerId);
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
        } else if (inputSlotFor(player, resource) >= 0) {
            int target = inputSlotFor(player, resource);
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

    // The metal, additive or coke slot this item belongs in, or -1. An additive goes to the additive slot
    // already holding it, else the first empty one (as through an input port).
    private int inputSlotFor(Player player, ItemResource resource) {
        for (int slot : new int[] { ArcforgeFurnaceBlockEntity.SLOT_METAL, ArcforgeFurnaceBlockEntity.SLOT_COKE }) {
            if (ArcforgeFurnaceBlockEntity.isItemValid(player.level(), slot, resource)) {
                return slot;
            }
        }
        if (!ArcforgeFurnaceBlockEntity.isItemValid(player.level(), ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE, resource)) {
            return -1;
        }
        int[] additives = { ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE, ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE_2 };
        for (int slot : additives) {
            if (resource.matches(slots.get(slot).getItem())) {
                return slot;
            }
        }
        for (int slot : additives) {
            if (!slots.get(slot).hasItem()) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public ContainerLevelAccess securityAccess() {
        return access;
    }

    @Override
    public boolean stillValid(Player player) {
        return MenuReach.stillValid(access, player, ModBlocks.ARCFORGE_FURNACE_PORT.get());
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

    public int getOxygen() {
        return value(DATA_OXYGEN);
    }

    // A speed-up for display: 1.5 -> "1.5", 2.0 -> "2".
    public static String boostText(double boost) {
        return boost == Math.rint(boost) ? Integer.toString((int) boost) : Double.toString(Math.round(boost * 100.0) / 100.0);
    }

    // The current smelt's speed-up from oxygen (1.5), or 0 when it has none.
    public double getBoost() {
        return value(DATA_BOOST) / 100.0;
    }
}
