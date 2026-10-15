/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.zagdrath.arcforge.blockentity.multiblock.PumpjackBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.oil.DepositKind;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Pumpjack's GUI, opened from any block of a formed pumpjack (served by the controller).
public class PumpjackMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_USAGE = 2;
    public static final int DATA_CRUDE = 3;
    public static final int DATA_CRUDE_CAPACITY = 4;
    // mB/t x 100.
    public static final int DATA_RATE = 5;
    // The deposit: its kind + 1 (0: none), buckets left and buckets it held.
    public static final int DATA_KIND = 6;
    public static final int DATA_LEFT = 7;
    public static final int DATA_AMOUNT = 8;
    public static final int DATA_STATUS = 9;
    public static final int DATA_REDSTONE_MODE = 10;
    public static final int DATA_SIDE_CONFIG = 11;
    // 1 when its tank holds (or, empty, its deposit gives) Sour Crude Oil.
    public static final int DATA_SOUR = 12;
    public static final int DATA_VALUES = 13;

    // Slot positions as the Electric Pump's.
    public static final int BUCKET_IN_X = 133, BUCKET_IN_Y = 19;
    public static final int BUCKET_OUT_X = 133, BUCKET_OUT_Y = 53;

    // Client constructor, called with the controller's position written by the server.
    public PumpjackMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), PumpjackBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public PumpjackMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.PUMPJACK.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.PUMPJACK_CONTROLLER.get());
        addMachineSlot(PumpjackBlockEntity.SLOT_BUCKET_IN, BUCKET_IN_X, BUCKET_IN_Y);
        addMachineSlot(PumpjackBlockEntity.SLOT_BUCKET_OUT, BUCKET_OUT_X, BUCKET_OUT_Y);
        finish(inventory);
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

    public Slot getBucketInSlot() {
        return slots.get(0);
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

    public int getCrude() {
        return value(DATA_CRUDE);
    }

    public int getCrudeCapacity() {
        return value(DATA_CRUDE_CAPACITY);
    }

    // What it pumps: Crude Oil or Sour Crude Oil.
    public net.minecraft.world.level.material.Fluid getOil() {
        return value(DATA_SOUR) != 0 ? net.zagdrath.arcforge.registry.ModFluids.SOUR_CRUDE_OIL.get() : net.zagdrath.arcforge.registry.ModFluids.CRUDE_OIL.get();
    }

    public double getRate() {
        return value(DATA_RATE) / 100.0;
    }

    // The deposit's kind, or null with no oil under it.
    public @Nullable DepositKind getKind() {
        int kind = value(DATA_KIND);
        return kind > 0 && kind <= DepositKind.values().length ? DepositKind.values()[kind - 1] : null;
    }

    public int getLeft() {
        return value(DATA_LEFT);
    }

    public int getAmount() {
        return value(DATA_AMOUNT);
    }
}
