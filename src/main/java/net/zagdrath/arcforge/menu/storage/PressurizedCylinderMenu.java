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
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.block.storage.PressurizedCylinderBlock;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// Gas Cartridge slots on the left (drain above, fill below), the gas gauge and the tabs.
public class PressurizedCylinderMenu extends StorageMenu {
    // Logical data indices (each is a full int, see WideIntContainerData).
    public static final int DATA_FLUID = 0;
    public static final int DATA_AMOUNT = 1;
    public static final int DATA_CAPACITY = 2;
    public static final int DATA_REDSTONE_MODE = 3;
    public static final int DATA_SIDE_CONFIG = 4;
    public static final int DATA_VALUES = 5;

    // Client constructor, called with the block position written by the server.
    public PressurizedCylinderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(),
                new FilteredItemHandler(MACHINE_SLOTS, (slot, resource) -> PressurizedCylinderBlockEntity.accepts(resource), () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    private final ConduitTier tier;

    public PressurizedCylinderMenu(int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler items, ContainerData data) {
        super(ModMenuTypes.PRESSURIZED_CYLINDER.get(), containerId, inventory, pos, items, data, DATA_VALUES, DATA_SIDE_CONFIG,
                block -> block instanceof PressurizedCylinderBlock, SLOT_X_LEFT);
        // Read from the block rather than synced, since the screen's title needs it straight away.
        this.tier = inventory.player.level().getBlockState(pos).getBlock() instanceof PressurizedCylinderBlock cylinder
                ? cylinder.getTier()
                : ConduitTier.WROUGHT;
    }

    public ConduitTier getTier() {
        return tier;
    }

    @Override
    protected int quickMoveTarget(ItemStack stack) {
        return PortableStorageItem.is(stack, PortableStorageItem.Kind.GAS_CARTRIDGE) ? portableTarget(stack) : -1;
    }

    // The stored gas, rebuilt from its registry id.
    public FluidStack getGas() {
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

    public RedstoneMode getRedstoneMode() {
        return RedstoneMode.byId(value(DATA_REDSTONE_MODE));
    }
}
