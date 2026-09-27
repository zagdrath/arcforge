/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.heat.GeothermalHeat;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.slot.ToggleableSlot;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

public class GeothermalPlantMenu extends AbstractContainerMenu {
    // Logical data indices (each is a full int, see WideIntContainerData).
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_LAVA = 2;
    public static final int DATA_LAVA_CAPACITY = 3;
    public static final int DATA_HEAT = 4;
    public static final int DATA_MAX_HEAT = 5;
    public static final int DATA_BURN_TIME = 6;
    public static final int DATA_BURN_TOTAL = 7;
    public static final int DATA_FE_PER_TICK = 8;
    public static final int DATA_LAVA_SOURCES = 9;
    public static final int DATA_STATUS = 10;
    public static final int DATA_REDSTONE_MODE = 11;
    public static final int DATA_SIDE_CONFIG = 12;
    public static final int DATA_VALUES = 13;

    // Slot positions from the GUI layout.
    public static final int INPUT_SLOT_X = 31, INPUT_SLOT_Y = 19;
    public static final int OUTPUT_SLOT_X = 31, OUTPUT_SLOT_Y = 53;
    // Upgrade slots sit in the Upgrades side tab, which is the 4th tab (y = 6 + 3 * 25), with slot items at +9,+25.
    public static final int UPGRADE_SLOT_X = 181, UPGRADE_SLOT_Y = 106, UPGRADE_SLOT_PITCH = 20;

    public static final int BUTTON_CLEAR_SIDES = MachineMenuButtons.CLEAR_SIDES;

    private static final int MACHINE_SLOTS = GeothermalPlantBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INV_END = MACHINE_SLOTS + 27;
    private static final int PLAYER_HOTBAR_END = PLAYER_INV_END + 9;

    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final List<ToggleableSlot> upgradeSlots = new ArrayList<>();

    // Client constructor, called with the block position written by the server.
    public GeothermalPlantMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new FilteredItemHandler(MACHINE_SLOTS, GeothermalPlantBlockEntity::isItemValid, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public GeothermalPlantMenu(int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items, ContainerData data) {
        super(ModMenuTypes.GEOTHERMAL_PLANT.get(), containerId);
        checkContainerDataCount(data, DATA_VALUES * 2);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = data;

        addSlot(new ResourceHandlerSlot(items, items::set, GeothermalPlantBlockEntity.SLOT_INPUT, INPUT_SLOT_X, INPUT_SLOT_Y));
        addSlot(new ResourceHandlerSlot(items, items::set, GeothermalPlantBlockEntity.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y));
        for (int i = 0; i < GeothermalPlantBlockEntity.UPGRADE_SLOTS; i++) {
            ToggleableSlot slot = new ToggleableSlot(items, items::set, GeothermalPlantBlockEntity.SLOT_UPGRADE_FIRST + i,
                    UPGRADE_SLOT_X + i * UPGRADE_SLOT_PITCH, UPGRADE_SLOT_Y);
            upgradeSlots.add(slot);
            addSlot(slot);
        }
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

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (slotIndex < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (GeothermalHeat.isSolidFuel(stack) || stack.is(Items.LAVA_BUCKET)) {
            if (!moveItemStackTo(stack, GeothermalPlantBlockEntity.SLOT_INPUT, GeothermalPlantBlockEntity.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItemTags.UPGRADES)) {
            if (!moveItemStackTo(stack, GeothermalPlantBlockEntity.SLOT_UPGRADE_FIRST, GeothermalPlantBlockEntity.SLOT_COUNT, false)) {
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
        return stillValid(access, player, ModBlocks.GEOTHERMAL_PLANT.get());
    }

    public List<ToggleableSlot> getUpgradeSlots() {
        return upgradeSlots;
    }

    public Slot getOutputSlot() {
        return slots.get(GeothermalPlantBlockEntity.SLOT_OUTPUT);
    }

    private int value(int index) {
        return WideIntContainerData.read(data, index);
    }

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return value(DATA_ENERGY_CAPACITY);
    }

    public int getLava() {
        return value(DATA_LAVA);
    }

    public int getLavaCapacity() {
        return value(DATA_LAVA_CAPACITY);
    }

    public int getHeat() {
        return value(DATA_HEAT);
    }

    public int getMaxHeat() {
        return value(DATA_MAX_HEAT);
    }

    public int getBurnTime() {
        return value(DATA_BURN_TIME);
    }

    public int getBurnTotal() {
        return value(DATA_BURN_TOTAL);
    }

    public int getFePerTick() {
        return value(DATA_FE_PER_TICK);
    }

    public int getAdjacentLavaSources() {
        return value(DATA_LAVA_SOURCES);
    }

    public MachineStatus getStatus() {
        return MachineStatus.byId(value(DATA_STATUS));
    }

    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.byId(value(DATA_REDSTONE_MODE));
    }

    public SideMode getSideMode(RelativeSide side) {
        return SideConfig.unpack(value(DATA_SIDE_CONFIG), side);
    }
}
