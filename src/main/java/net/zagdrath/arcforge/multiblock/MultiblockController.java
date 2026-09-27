/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;

// The block entity that runs a formed multiblock (the Carbonizer's master block, the Arcforge Furnace's
// port). Side configuration applies to the faces of the whole structure, relative to its facing: every
// block lying on a configured face exposes that face's capability on its outward side.
public interface MultiblockController extends ConfigurableMachine {
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

    // The mode of the structure face that `side` of the part at `pos` lies on, or null if that side
    // faces into the structure.
    default @Nullable SideMode faceMode(BlockPos pos, Direction side) {
        if (isInside(pos.relative(side))) {
            return null;
        }
        return getSideMode(RelativeSide.fromDirection(getStructureFacing(), side));
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
