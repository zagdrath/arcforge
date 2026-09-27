/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.conduit;

import java.util.ArrayList;
import java.util.Arrays;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.conduit.SideSetting;
import net.zagdrath.arcforge.conduit.item.ItemPacket;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// Per-block conduit state: the wrench setting of each side, plus what this conduit holds (its share of
// the network's energy/heat/fluid, items in transit and items stored). All transfer logic lives in
// ConduitNetworkManager.
public class ConduitBlockEntity extends BlockEntity {
    // Fluid and item conduits sync their contents to clients at most this often.
    private static final int CONTENTS_SYNC_INTERVAL = 5;

    private final SideSetting[] settings = new SideSetting[Direction.values().length];
    private final List<ItemPacket> packets = new ArrayList<>();
    // Items pulled in with nowhere to go yet; shown resting in the conduit's core.
    private final List<ItemStack> storedItems = new ArrayList<>();
    // Energy (FE) or heat (HU) held by this conduit, for energy and thermal conduits.
    private int stored;
    private FluidStack fluid = FluidStack.EMPTY;
    private float itemSpeed;
    private boolean syncPending;
    // Game time is never negative, so the first sync is always allowed. (Long.MIN_VALUE would overflow the subtraction.)
    private long lastSyncTick = -CONTENTS_SYNC_INTERVAL;

    public ConduitBlockEntity(BlockPos pos, BlockState state) {
        super(typeFor(state), pos, state);
        Arrays.fill(settings, SideSetting.AUTO);
    }

    private static net.minecraft.world.level.block.entity.BlockEntityType<ConduitBlockEntity> typeFor(BlockState state) {
        return state.getBlock() instanceof ConduitBlock conduit && conduit.getConduitType().isTransparent()
                ? ModBlockEntityTypes.TRANSPARENT_CONDUIT.get()
                : ModBlockEntityTypes.CONDUIT.get();
    }

    public ConduitType getConduitType() {
        return ((ConduitBlock) getBlockState().getBlock()).getConduitType();
    }

    // --- Side settings (changed with the wrench) ---

    public SideSetting getSetting(Direction side) {
        return settings[side.ordinal()];
    }

    public void setSetting(Direction side, SideSetting setting) {
        if (settings[side.ordinal()] != setting) {
            settings[side.ordinal()] = setting;
            setChanged();
        }
    }

    public boolean isSideDisabled(Direction side) {
        return getSetting(side) == SideSetting.DISABLED;
    }

    // --- Energy / heat buffer ---

    public int getStored() {
        return stored;
    }

    public void setStored(int amount) {
        if (amount != stored) {
            stored = amount;
            setChanged();
        }
    }

    // --- Stored items ---

    public List<ItemStack> getStoredItems() {
        return storedItems;
    }

    // Adds as much of the stack as fits in the storage slots and returns how many were stored.
    // With `overflow`, items that do not fit get a slot of their own anyway (used for items already
    // inside the network, which must never be deleted).
    public int storeItems(ItemStack stack, boolean overflow) {
        int remaining = stack.getCount();
        for (ItemStack held : storedItems) {
            if (remaining <= 0) {
                break;
            }
            if (ItemStack.isSameItemSameComponents(held, stack)) {
                int moved = Math.min(remaining, held.getMaxStackSize() - held.getCount());
                if (moved > 0) {
                    held.grow(moved);
                    remaining -= moved;
                }
            }
        }
        while (remaining > 0 && (overflow || storedItems.size() < ConduitTier.ITEM_STORAGE_SLOTS)) {
            int moved = Math.min(remaining, stack.getMaxStackSize());
            storedItems.add(stack.copyWithCount(moved));
            remaining -= moved;
        }
        int storedCount = stack.getCount() - remaining;
        if (storedCount > 0) {
            markContentsChanged(true);
        }
        return storedCount;
    }

    // How many of this item could still be stored.
    public int storageSpaceFor(ItemStack stack) {
        int space = (ConduitTier.ITEM_STORAGE_SLOTS - storedItems.size()) * stack.getMaxStackSize();
        for (ItemStack held : storedItems) {
            if (ItemStack.isSameItemSameComponents(held, stack)) {
                space += held.getMaxStackSize() - held.getCount();
            }
        }
        return Math.max(0, space);
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

    // --- Fluid share of the network tank ---

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
    // fluid levels change every tick and are throttled.
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
        if (force || now - lastSyncTick >= CONTENTS_SYNC_INTERVAL) {
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
    // Fluid moves into the connected neighbours, which pool it when their network is rebuilt
    // (only fluid beyond the remaining network's capacity is lost).
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
        for (ItemStack stack : storedItems) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        storedItems.clear();
        if (!fluid.isEmpty()) {
            handOffFluid(pos, state);
        }
    }

    private void handOffFluid(BlockPos pos, BlockState state) {
        List<ConduitBlockEntity> neighbours = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (ConduitBlock.mode(state, side) == ConnectionMode.PIPE
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
        input.getInt("sides").ifPresentOrElse(this::unpackSettings, () -> migrateSettings(input.getIntOr("disabled_sides", 0)));
        packets.clear();
        input.listOrEmpty("packets", ItemPacket.CODEC).forEach(packets::add);
        storedItems.clear();
        input.listOrEmpty("stored_items", ItemStack.OPTIONAL_CODEC).forEach(stack -> {
            if (!stack.isEmpty()) {
                storedItems.add(stack);
            }
        });
        stored = input.getIntOr("stored", 0);
        fluid = input.read("fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        itemSpeed = input.getFloatOr("item_speed", 0.0F);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("sides", packSettings());
        if (!packets.isEmpty()) {
            var list = output.list("packets", ItemPacket.CODEC);
            packets.forEach(list::add);
        }
        if (!storedItems.isEmpty()) {
            var list = output.list("stored_items", ItemStack.OPTIONAL_CODEC);
            storedItems.forEach(list::add);
        }
        if (stored > 0) {
            output.putInt("stored", stored);
        }
        if (!fluid.isEmpty()) {
            output.store("fluid", FluidStack.OPTIONAL_CODEC, fluid);
        }
        output.putFloat("item_speed", itemSpeed);
    }

    // Two bits per side.
    private int packSettings() {
        int packed = 0;
        for (Direction side : Direction.values()) {
            packed |= settings[side.ordinal()].ordinal() << (side.ordinal() * 2);
        }
        return packed;
    }

    private void unpackSettings(int packed) {
        for (Direction side : Direction.values()) {
            settings[side.ordinal()] = SideSetting.byId((packed >>> (side.ordinal() * 2)) & 3);
        }
    }

    // Saves from before side settings existed: a wrench-set input/output lived only in the block state,
    // and disabled conduit joints in a bit mask. Keep both as forced settings.
    private void migrateSettings(int disabledMask) {
        BlockState state = getBlockState();
        for (Direction side : Direction.values()) {
            SideSetting setting = SideSetting.AUTO;
            if ((disabledMask & (1 << side.ordinal())) != 0) {
                setting = SideSetting.DISABLED;
            } else if (state.getBlock() instanceof ConduitBlock) {
                ConnectionMode mode = ConduitBlock.mode(state, side);
                if (mode == ConnectionMode.INPUT) setting = SideSetting.INPUT;
                if (mode == ConnectionMode.OUTPUT) setting = SideSetting.OUTPUT;
            }
            settings[side.ordinal()] = setting;
        }
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
