/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// Served by the master block of a Carbonizer; opened from any of its blocks.
public class CarbonizerMenu extends AbstractContainerMenu {
    // Logical data indices (each is a full int, see WideIntContainerData).
    public static final int DATA_CHAMBERS = 0;
    // One per chamber: permille progress while working, -1 while idle.
    public static final int DATA_CHAMBER_FIRST = 1;
    public static final int DATA_FLUID = DATA_CHAMBER_FIRST + CarbonizerBlockEntity.MAX_CHAMBERS;
    public static final int DATA_FLUID_AMOUNT = DATA_FLUID + 1;
    public static final int DATA_FLUID_CAPACITY = DATA_FLUID + 2;
    public static final int DATA_REDSTONE_MODE = DATA_FLUID + 3;
    public static final int DATA_SIDE_CONFIG = DATA_FLUID + 4;
    // Slice height and depth (0 while unformed).
    public static final int DATA_SLICE_HEIGHT = DATA_FLUID + 5;
    public static final int DATA_SLICE_DEPTH = DATA_FLUID + 6;
    // One per chamber: how many inputs it is baking, and which item (-1 when empty).
    public static final int DATA_BATCH_FIRST = DATA_FLUID + 7;
    public static final int DATA_BATCH_ITEM_FIRST = DATA_BATCH_FIRST + CarbonizerBlockEntity.MAX_CHAMBERS;
    public static final int DATA_VALUES = DATA_BATCH_ITEM_FIRST + CarbonizerBlockEntity.MAX_CHAMBERS;

    // Slot positions from carbonizer_gui_layout.json.
    public static final int INPUT_X = 27, INPUT_Y = 23;
    public static final int OUTPUT_X = 85, OUTPUT_Y = 23;
    public static final int BUCKET_IN_X = 125, BUCKET_IN_Y = 19;
    public static final int BUCKET_OUT_X = 125, BUCKET_OUT_Y = 51;

    private static final int MACHINE_SLOTS = CarbonizerBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INV_END = MACHINE_SLOTS + 27;
    private static final int PLAYER_HOTBAR_END = PLAYER_INV_END + 9;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    // Client constructor, called with the master's position written by the server.
    public CarbonizerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new FilteredItemHandler(MACHINE_SLOTS, (slot, resource) -> CarbonizerBlockEntity.isItemValid(null, slot, resource), () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public CarbonizerMenu(int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items, ContainerData data) {
        super(ModMenuTypes.CARBONIZER.get(), containerId);
        checkContainerDataCount(data, DATA_VALUES * 2);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = data;

        addSlot(new ResourceHandlerSlot(items, items::set, CarbonizerBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, CarbonizerBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, CarbonizerBlockEntity.SLOT_BUCKET_IN, BUCKET_IN_X, BUCKET_IN_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, CarbonizerBlockEntity.SLOT_BUCKET_OUT, BUCKET_OUT_X, BUCKET_OUT_Y));
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
        } else if (CarbonizerBlockEntity.isItemValid(player.level(), CarbonizerBlockEntity.SLOT_INPUT, resource)) {
            if (!moveItemStackTo(stack, CarbonizerBlockEntity.SLOT_INPUT, CarbonizerBlockEntity.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (CarbonizerBlockEntity.isItemValid(player.level(), CarbonizerBlockEntity.SLOT_BUCKET_IN, resource)) {
            if (!moveItemStackTo(stack, CarbonizerBlockEntity.SLOT_BUCKET_IN, CarbonizerBlockEntity.SLOT_BUCKET_IN + 1, false)) {
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
        return stillValid(access, player, ModBlocks.CARBONIZER.get());
    }

    public Slot getSlotFor(int machineSlot) {
        return slots.get(machineSlot);
    }

    private int value(int index) {
        return WideIntContainerData.read(data, index);
    }

    // Number of chambers (slices), or 0 while the structure is incomplete.
    public int getChambers() {
        return value(DATA_CHAMBERS);
    }

    // Progress of a chamber from 0 to 1, or -1 while it is idle.
    public float getChamberProgress(int chamber) {
        int permille = value(DATA_CHAMBER_FIRST + chamber);
        return permille < 0 ? -1.0F : permille / 1000.0F;
    }

    public int getSliceHeight() {
        return value(DATA_SLICE_HEIGHT);
    }

    public int getSliceDepth() {
        return value(DATA_SLICE_DEPTH);
    }

    public int getChamberBatch(int chamber) {
        return value(DATA_BATCH_FIRST + chamber);
    }

    // The item a chamber is baking, or null when it's empty.
    public net.minecraft.world.item.@org.jspecify.annotations.Nullable Item getChamberItem(int chamber) {
        int id = value(DATA_BATCH_ITEM_FIRST + chamber);
        return id < 0 ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(id);
    }

    public Fluid getFluid() {
        int id = value(DATA_FLUID);
        return id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }

    public int getFluidAmount() {
        return value(DATA_FLUID_AMOUNT);
    }

    public int getFluidCapacity() {
        return value(DATA_FLUID_CAPACITY);
    }

    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.byId(value(DATA_REDSTONE_MODE));
    }

    public SideMode getSideMode(RelativeSide side) {
        return SideConfig.unpack(value(DATA_SIDE_CONFIG), side);
    }
}
