/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.zagdrath.arcforge.block.logistics.ChunkLoaderBlock;
import net.zagdrath.arcforge.blockentity.logistics.ChunkLoaderBlockEntity;
import net.zagdrath.arcforge.chunkloading.ChunkLoaderStatus;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.common.MenuReach;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.security.SecuredMenu;

// A Chunk Loader's screen: no slots, its radius (the - and + buttons), the chunks it loads on a map, its status, its
// owner's total against the limit, and its FE when FE is required. Security tab buttons are MachineMenuButtons'.
public class ChunkLoaderMenu extends AbstractContainerMenu implements SecuredMenu {
    public static final int DATA_RADIUS = 0, DATA_STATUS = 1, DATA_LOADED = 2, DATA_OWNER_TOTAL = 3, DATA_LIMIT = 4;
    public static final int DATA_ENERGY = 5, DATA_CAPACITY = 6, DATA_COST = 7;
    public static final int DATA_VALUES = 8;
    public static final int BUTTON_RADIUS_MINUS = 0, BUTTON_RADIUS_PLUS = 1;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final Block block;

    // Client constructor, called with the loader's position written by the server.
    public ChunkLoaderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), WideIntContainerData.client(DATA_VALUES));
    }

    public ChunkLoaderMenu(int containerId, Inventory inventory, BlockPos pos, ContainerData data) {
        super(ModMenuTypes.CHUNK_LOADER.get(), containerId);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = data;
        Block here = inventory.player.level().getBlockState(pos).getBlock();
        this.block = here instanceof ChunkLoaderBlock ? here : Blocks.AIR;
        addDataSlots(data);
    }

    public BlockPos getPos() {
        return pos;
    }

    public int value(int index) {
        return WideIntContainerData.read(data, index);
    }

    public int getRadius() {
        return value(DATA_RADIUS);
    }

    public ChunkLoaderStatus getStatus() {
        return ChunkLoaderStatus.byId(value(DATA_STATUS));
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_RADIUS_MINUS || buttonId == BUTTON_RADIUS_PLUS) {
            access.execute((level, at) -> {
                if (level.getBlockEntity(at) instanceof ChunkLoaderBlockEntity loader) {
                    loader.setRadius(loader.getRadius() + (buttonId == BUTTON_RADIUS_PLUS ? 1 : -1));
                }
            });
            return true;
        }
        return MachineMenuButtons.handle(access, player, buttonId);
    }

    @Override
    public ContainerLevelAccess securityAccess() {
        return access;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return MenuReach.stillValid(access, player, block);
    }
}
