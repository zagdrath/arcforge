/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.nuclear;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// What the nuclear tier's small block entities share: their whole saved state synced to clients (for their renderers
// and Jade), sent when sync() is called, and a fixed row of item slots saved as a list.
public abstract class NuclearBlockEntity extends BlockEntity {
    protected NuclearBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // Saves and sends the block entity to the clients that see it.
    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    protected static void storeSlots(ValueOutput out, String key, ItemStack[] slots) {
        out.store(key, ItemStack.OPTIONAL_CODEC.listOf(), List.of(slots));
    }

    protected static void readSlots(ValueInput in, String key, ItemStack[] slots) {
        List<ItemStack> list = in.read(key, ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i < list.size() ? list.get(i) : ItemStack.EMPTY;
        }
    }

    protected static List<ItemStack> nonEmpty(ItemStack[] slots) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }
}
