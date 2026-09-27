/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.conduit;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.item.ItemPacket;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// Per-block conduit state: wrench-disabled sides, plus contents for rendering (items and liquid).
// All transfer logic lives in ConduitNetworkManager.
public class ConduitBlockEntity extends BlockEntity {
    // Liquid and item conduits sync their contents to clients at most this often.
    private static final int LIQUID_SYNC_INTERVAL = 5;

    private byte disabledSides;
    private final List<ItemPacket> packets = new ArrayList<>();
    private FluidStack fluid = FluidStack.EMPTY;
    private float itemSpeed;
    private boolean syncPending;
    // Game time is never negative, so the first sync is always allowed. (Long.MIN_VALUE would overflow the subtraction.)
    private long lastSyncTick = -LIQUID_SYNC_INTERVAL;

    public ConduitBlockEntity(BlockPos pos, BlockState state) {
        super(typeFor(state), pos, state);
    }

    private static net.minecraft.world.level.block.entity.BlockEntityType<ConduitBlockEntity> typeFor(BlockState state) {
        return state.getBlock() instanceof ConduitBlock conduit && conduit.getConduitType().isTransparent()
                ? ModBlockEntityTypes.TRANSPARENT_CONDUIT.get()
                : ModBlockEntityTypes.CONDUIT.get();
    }

    public ConduitType getConduitType() {
        return ((ConduitBlock) getBlockState().getBlock()).getConduitType();
    }

    // --- Wrench-disabled conduit-to-conduit sides ---

    public boolean isSideDisabled(Direction side) {
        return (disabledSides & (1 << side.ordinal())) != 0;
    }

    public void setSideDisabled(Direction side, boolean disabled) {
        byte updated = (byte) (disabled ? disabledSides | (1 << side.ordinal()) : disabledSides & ~(1 << side.ordinal()));
        if (updated != disabledSides) {
            disabledSides = updated;
            setChanged();
        }
    }

    // --- Item packets ---

    public List<ItemPacket> getPackets() {
        return packets;
    }

    public float getItemSpeed() {
        return itemSpeed;
    }

    public void setItemSpeed(float speed) {
        if (speed != itemSpeed) {
            itemSpeed = speed;
            markContentsChanged(true);
        }
    }

    // --- Liquid share of the network tank ---

    public FluidStack getFluid() {
        return fluid;
    }

    public void setFluid(FluidStack stack) {
        if (!FluidStack.matches(stack, fluid)) {
            fluid = stack.copy();
            markContentsChanged(false);
        }
    }

    // Records a content change. Items sync right away so clients see packets enter and leave;
    // liquid levels change every tick and are throttled.
    public void markContentsChanged(boolean immediate) {
        setChanged();
        if (!getConduitType().isTransparent()) {
            return;
        }
        syncPending = true;
        if (immediate) {
            flushSync(true);
        }
    }

    // Called by the network manager each tick.
    public void flushSync(boolean force) {
        if (!syncPending || level == null || level.isClientSide()) {
            return;
        }
        long now = level.getGameTime();
        if (force || now - lastSyncTick >= LIQUID_SYNC_INTERVAL) {
            syncPending = false;
            lastSyncTick = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // Clients move item packets along between server updates.
    public static void clientTick(Level level, BlockPos pos, BlockState state, ConduitBlockEntity conduit) {
        for (ItemPacket packet : conduit.packets) {
            packet.progress = Math.min(1.0F, packet.progress + conduit.itemSpeed);
        }
    }

    // --- Lifecycle ---

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ConduitNetworkManager.get(serverLevel).markDirty(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            ConduitNetworkManager.get(serverLevel).markDirty(worldPosition);
        }
    }

    // Items in transit are dropped when the conduit is broken; they are never deleted.
    // Liquid moves into the connected neighbours, which pool it when their network is rebuilt
    // (only liquid beyond the remaining network's capacity is lost).
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (ItemPacket packet : packets) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, packet.stack);
        }
        packets.clear();
        if (!fluid.isEmpty()) {
            handOffFluid(pos, state);
        }
    }

    private void handOffFluid(BlockPos pos, BlockState state) {
        List<ConduitBlockEntity> neighbours = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (ConduitBlock.mode(state, side) == net.zagdrath.arcforge.conduit.ConnectionMode.PIPE
                    && level.getBlockEntity(pos.relative(side)) instanceof ConduitBlockEntity neighbour
                    && (neighbour.fluid.isEmpty() || FluidStack.isSameFluidSameComponents(neighbour.fluid, fluid))) {
                neighbours.add(neighbour);
            }
        }
        int remaining = fluid.getAmount();
        for (int i = 0; i < neighbours.size(); i++) {
            ConduitBlockEntity neighbour = neighbours.get(i);
            int share = Math.ceilDiv(remaining, neighbours.size() - i);
            neighbour.setFluid(fluid.copyWithAmount(neighbour.fluid.getAmount() + share));
            remaining -= share;
        }
        fluid = FluidStack.EMPTY;
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        disabledSides = (byte) input.getIntOr("disabled_sides", 0);
        packets.clear();
        input.listOrEmpty("packets", ItemPacket.CODEC).forEach(packets::add);
        fluid = input.read("fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        itemSpeed = input.getFloatOr("item_speed", 0.0F);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("disabled_sides", disabledSides);
        if (!packets.isEmpty()) {
            var list = output.list("packets", ItemPacket.CODEC);
            packets.forEach(list::add);
        }
        if (!fluid.isEmpty()) {
            output.store("fluid", FluidStack.OPTIONAL_CODEC, fluid);
        }
        output.putFloat("item_speed", itemSpeed);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return getConduitType().isTransparent() ? ClientboundBlockEntityDataPacket.create(this) : null;
    }
}
