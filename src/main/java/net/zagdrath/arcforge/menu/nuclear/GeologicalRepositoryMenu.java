/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.zagdrath.arcforge.blockentity.nuclear.GeologicalRepositoryBlockEntity;
import net.zagdrath.arcforge.menu.common.MenuReach;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// A Geological Repository's GUI: its one deposit slot and what it holds (see GeologicalRepositoryBlockEntity).
public class GeologicalRepositoryMenu extends AbstractContainerMenu {
    public static final int SLOT_X = 80, SLOT_Y = 52;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    public GeologicalRepositoryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), new FilteredItemHandler(1, (index, resource) -> GeologicalRepositoryBlockEntity.accepts(resource.toStack(1)), () -> {}),
                new SimpleContainerData(GeologicalRepositoryBlockEntity.DATA_VALUES));
    }

    public GeologicalRepositoryMenu(int containerId, Inventory inventory, BlockPos pos, FilteredItemHandler slot, ContainerData data) {
        super(ModMenuTypes.GEOLOGICAL_REPOSITORY.get(), containerId);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = data;
        addSlot(new ResourceHandlerSlot(slot, slot::set, 0, SLOT_X, SLOT_Y));
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    public int logs() {
        return data.get(GeologicalRepositoryBlockEntity.DATA_LOGS);
    }

    public int casks() {
        return data.get(GeologicalRepositoryBlockEntity.DATA_CASKS);
    }

    public int rods() {
        return data.get(GeologicalRepositoryBlockEntity.DATA_RODS);
    }

    // mSv/h locked away.
    public double activity() {
        return data.get(GeologicalRepositoryBlockEntity.DATA_ACTIVITY) / 10.0;
    }

    public boolean validSite() {
        return data.get(GeologicalRepositoryBlockEntity.DATA_VALID) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!GeologicalRepositoryBlockEntity.accepts(stack) || !moveItemStackTo(stack, 0, 1, false)) {
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
        return MenuReach.stillValid(access, player, ModBlocks.GEOLOGICAL_REPOSITORY.get());
    }
}
