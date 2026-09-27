/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.SideMode;

// The block entity that runs a formed multiblock (the Carbonizer's master block, the Arcforge Furnace's
// port). The structure does IO only through its ports (see MultiblockPorts): each port block exposes its
// mode's capability on the one outer face the port is on. Its side configuration is kept only for auto-eject
// and for carrying ports over from saves made before ports.
public interface MultiblockController extends ConfigurableMachine {
    // Every controller is a block entity.
    @Nullable Level getLevel();

    BlockPos getBlockPos();

    boolean isFormed();

    Direction getStructureFacing();

    // Inclusive corners of the structure's bounding box.
    BlockPos getMinCorner();

    BlockPos getMaxCorner();

    // Whether this position holds a block of the structure (as opposed to a hollow inside it).
    boolean isPart(BlockPos pos);

    // The handler exposed on faces with this mode; null mode is an internal/unsided query.
    @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode);

    @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode);

    // How an auto conduit touching a face with this mode connects.
    ConnectionMode getConduitConnection(SideMode mode, ConduitType type);

    default boolean isInside(BlockPos pos) {
        BlockPos min = getMinCorner();
        BlockPos max = getMaxCorner();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    // The mode of the port at pos, if it's on `side` and that faces out of the structure; null if it
    // isn't a port, the port is on another face, or that side faces into the structure.
    default @Nullable SideMode faceMode(BlockPos pos, Direction side) {
        Level level = getLevel();
        if (level == null || isInside(pos.relative(side))) {
            return null;
        }
        SideMode mode = MultiblockPorts.get(level.getBlockState(pos), side);
        return mode == SideMode.NONE ? null : mode;
    }

    // A port was set or cleared (the capabilities round the structure have been refreshed already).
    default void onPortsChanged() {}

    // Where a port for one side of the structure goes by default: the middle of it.
    default @Nullable BlockPos defaultPortPos(Level level, Direction side) {
        return MultiblockPorts.faceCentre(level, this, side);
    }

    // The ports a structure gets the first time it forms (see MultiblockPorts.Defaults): by default, one
    // in the middle of each side its default side configuration uses.
    default Map<BlockPos, MultiblockPorts.DefaultPort> defaultPorts(Level level) {
        return MultiblockPorts.fromSideConfig(level, this);
    }

    default @Nullable ResourceHandler<ItemResource> itemHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return getItemHandler(null);
        }
        SideMode mode = faceMode(pos, side);
        return mode == null ? null : getItemHandler(mode);
    }

    default @Nullable ResourceHandler<FluidResource> fluidHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return getFluidHandler(null);
        }
        SideMode mode = faceMode(pos, side);
        return mode == null ? null : getFluidHandler(mode);
    }

    default ConnectionMode conduitConnectionAt(BlockPos pos, Direction side, ConduitType type) {
        SideMode mode = faceMode(pos, side);
        return mode == null ? ConnectionMode.NONE : getConduitConnection(mode, type);
    }
}
