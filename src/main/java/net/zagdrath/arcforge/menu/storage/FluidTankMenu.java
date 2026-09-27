/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.zagdrath.arcforge.block.storage.FluidTankBlock;
import net.zagdrath.arcforge.blockentity.storage.StorageBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

public class FluidTankMenu extends StorageMenu {
    // Logical data indices (each is a full int, see WideIntContainerData).
    public static final int DATA_FLUID = 0;
    public static final int DATA_AMOUNT = 1;
    public static final int DATA_CAPACITY = 2;
    public static final int DATA_SIDE_CONFIG = 3;
    public static final int DATA_VALUES = 4;

    // Client constructor, called with the block position written by the server.
    public FluidTankMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new FilteredItemHandler(MACHINE_SLOTS, (slot, resource) -> slot == StorageBlockEntity.SLOT_IN, () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public FluidTankMenu(int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items, ContainerData data) {
        super(ModMenuTypes.FLUID_TANK.get(), containerId, inventory, pos, items, data, DATA_VALUES, DATA_SIDE_CONFIG,
                block -> block instanceof FluidTankBlock);
    }

    // Anything holding or able to hold fluid goes to the bucket slot.
    @Override
    protected int quickMoveTarget(ItemStack stack) {
        return ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Fluid.ITEM) != null ? StorageBlockEntity.SLOT_IN : -1;
    }

    // The stored fluid, rebuilt from its registry id (components are not synced; they don't affect the display).
    public FluidStack getFluid() {
        int id = value(DATA_FLUID);
        int amount = getAmount();
        if (id < 0 || amount <= 0) {
            return FluidStack.EMPTY;
        }
        Fluid fluid = BuiltInRegistries.FLUID.byId(id);
        return fluid == null || fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, amount);
    }

    public int getAmount() {
        return value(DATA_AMOUNT);
    }

    public int getCapacity() {
        return value(DATA_CAPACITY);
    }
}
