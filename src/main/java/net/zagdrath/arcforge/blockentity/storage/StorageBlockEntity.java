/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.storage;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.Dismantleable;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// Shared by fluid tanks and energy cells: a tier, per-face input/output/none configuration relative to
// the direction the block was placed facing (the model looks the same from every side), a redstone
// mode, and two GUI item slots that drop when the block is broken. The stored fluid or energy travels
// on the dropped item instead (see the loot tables).
public abstract class StorageBlockEntity extends BlockEntity implements MenuProvider, ConfigurableMachine, ConduitConnectable, Dismantleable {
    public static final int SLOT_IN = 0;
    public static final int SLOT_OUT = 1;
    public static final int SLOT_COUNT = 2;

    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT);

    protected final ConduitTier tier;
    protected final SideConfig sideConfig;
    protected final FilteredItemHandler items;
    protected RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    private Direction facing = Direction.NORTH;
    private boolean dismantled;

    protected StorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, ConduitTier tier, SideConfig sideConfig) {
        super(type, pos, state);
        this.tier = tier;
        this.sideConfig = sideConfig;
        this.items = new FilteredItemHandler(SLOT_COUNT, this::isItemValid, this::setChanged);
    }

    // Whether players may put this item in a GUI slot. The block itself bypasses this (e.g. to fill an output slot).
    protected abstract boolean isItemValid(int slot, ItemResource resource);

    // The conduit type whose auto connections follow this block's side configuration.
    protected abstract ConduitType conduitType();

    // Comparator output, 0-15 by how full the block is.
    public abstract int getComparatorSignal();

    public ConduitTier getTier() {
        return tier;
    }

    public FilteredItemHandler getItems() {
        return items;
    }

    public void setFacing(Direction facing) {
        this.facing = facing;
        onSideConfigChanged();
    }

    // --- Configuration ---

    @Override
    public List<SideMode> getAllowedSideModes() {
        return SIDE_MODES;
    }

    @Override
    public SideMode getSideMode(RelativeSide side) {
        return sideConfig.get(side);
    }

    @Override
    public void setSideMode(RelativeSide side, SideMode mode) {
        if (sideConfig.get(side) != mode) {
            sideConfig.set(side, mode);
            onSideConfigChanged();
        }
    }

    @Override
    public void clearSideModes() {
        sideConfig.clear();
        onSideConfigChanged();
    }

    public int getPackedSideConfig() {
        return sideConfig.pack();
    }

    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    @Override
    public void setRedstoneMode(RedstoneMode mode) {
        redstoneMode = mode;
        setChanged();
    }

    protected boolean isRedstoneEnabled() {
        return level != null && redstoneMode.canRun(level.hasNeighborSignal(worldPosition));
    }

    private void onSideConfigChanged() {
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            ConduitBlock.refreshAround(level, worldPosition);
        }
    }

    // What a face does. A null side is an internal/unsided query.
    protected @Nullable SideMode modeFor(@Nullable Direction side) {
        return side == null ? null : sideConfig.get(facing, side);
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        if (type != conduitType()) {
            return ConnectionMode.NONE;
        }
        return switch (sideConfig.get(facing, side)) {
            case INPUT -> ConnectionMode.INPUT;
            case OUTPUT -> ConnectionMode.OUTPUT;
            default -> ConnectionMode.NONE;
        };
    }

    // --- Removal ---

    @Override
    public void markDismantled() {
        dismantled = true;
    }

    // Whether the block is being picked up with the wrench rather than broken.
    protected boolean isDismantled() {
        return dismantled;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        // Slot items drop however the block is removed, including with the wrench.
        if (level != null) {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStack(slot));
            }
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.deserialize(input.childOrEmpty("items"));
        sideConfig.deserialize(input);
        redstoneMode = RedstoneMode.byId(input.getIntOr("redstone_mode", 0));
        facing = Direction.from2DDataValue(input.getIntOr("facing", Direction.NORTH.get2DDataValue()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        items.serialize(output.child("items"));
        sideConfig.serialize(output);
        output.putInt("redstone_mode", redstoneMode.ordinal());
        output.putInt("facing", facing.get2DDataValue());
    }
}
