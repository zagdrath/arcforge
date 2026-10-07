/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.SideMode;

// The block entity that runs a formed multiblock (the Carbonizer's master block, the Arcforge Furnace's
// port). The structure does IO only through its ports (see MultiblockPorts): each port block exposes its
// mode's capability on each outer face that's a port. Its side configuration is kept only for auto-eject
// and for carrying ports over from saves made before ports.
public interface MultiblockController extends ConfigurableMachine {
    // Which structure this is, for advancements (arcforge:multiblock_formed): its block entity type's id, e.g.
    // arcforge:steam_turbine_array.
    default Identifier multiblockId() {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(((BlockEntity) this).getType());
    }

    // How many blocks long the structure is along its axis (the shell arrays), or 0.
    default int length() {
        return 0;
    }

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

    // The mode of the port on `side` of the block at pos, if that faces out of the structure; null if that
    // face isn't a port, faces into the structure, or the block can't hold ports.
    default @Nullable SideMode faceMode(BlockPos pos, Direction side) {
        Level level = getLevel();
        if (level == null || isInside(pos.relative(side))) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PortHolder holder) || !holder.holdsPorts(state)) {
            return null;
        }
        SideMode mode = MultiblockPorts.get(level, pos, side);
        return mode == SideMode.NONE ? null : mode;
    }

    // A port was set or cleared (the capabilities round the structure have been refreshed already).
    default void onPortsChanged() {}

    // Where a port for one side of the structure goes by default: the middle of it.
    default @Nullable BlockPos defaultPortPos(Level level, Direction side) {
        return MultiblockPorts.faceCentre(level, this, side);
    }

    // The ports a structure gets the first time it forms (see MultiblockPorts.Defaults): none, unless it overrides
    // this (the Steam Turbine Array does). New structures start without ports; the player places them with the Wrench
    // in Port mode. (Saves from
    // before ports still get ports from their side configuration once, in MultiblockPorts.Defaults.)
    default Map<BlockPos, MultiblockPorts.DefaultPort> defaultPorts(Level level) {
        return Map.of();
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
