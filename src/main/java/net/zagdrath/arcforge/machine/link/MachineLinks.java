/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.link;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.Multiblocks;

// The Wired Machine Link: a Link Conduit runs from one face of a machine (or any outer face of a formed multiblock) to a
// Wired Machine Link block, whose faces then work as that machine's ports. The machine keeps its MachineLink (one at
// most); the face the cable is on reads as MACHINE_LINK and keeps its own mode underneath. The Wired Machine Link block
// gets each face's handler by asking the machine's own capability code for a mode (LinkableMachine.withForcedMode),
// so its faces hand out exactly what that port would on the machine, straight from its inventories, tanks and buffers.
public final class MachineLinks {
    private MachineLinks() {}

    // Whether a Link Conduit can attach to the block at pos: a machine, or a block that can be part of a multiblock.
    // (Whether it actually links is worked out by the Wired Machine Link: the structure has to be formed.)
    public static boolean canAttach(BlockGetter level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof MultiblockPart || block instanceof PressureGlassBlock
                || level.getBlockEntity(pos) instanceof MachineBlockEntity;
    }

    // Which way a linked machine faces: a single machine's front, or its structure's.
    public static Direction facing(LinkableMachine machine) {
        if (machine instanceof MultiblockController controller) {
            return controller.getStructureFacing();
        }
        return machine instanceof MachineBlockEntity single ? single.getFacing() : Direction.NORTH;
    }

    // The machine a cable attached to the block at pos links: the single machine there, or the controller of the formed
    // structure it's an outer block of. Null for anything else (an unformed structure, a block inside one).
    public static @Nullable LinkableMachine machineAt(Level level, BlockPos pos, Direction face) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MachineBlockEntity machine && !(blockEntity instanceof MultiblockController)) {
            return machine;
        }
        // The structure's own controller (a cube or shell array's casings, or a Carbonizer's parts, each have a block entity
        // of the controller's kind, but only one runs the structure).
        MultiblockController controller = Multiblocks.controllerAt(level, pos);
        if (controller == null && blockEntity instanceof MultiblockController own) {
            controller = own;
        }
        if (controller instanceof LinkableMachine machine && controller.isFormed() && controller.isInside(pos)
                && !controller.isInside(pos.relative(face))) {
            return machine;
        }
        return null;
    }

    // The handler a face in `mode` has on the linked machine (null if that mode has none of this kind): asked of the block
    // the cable is on, then (for a structure part with no capability of its own, like a window) of the controller.
    public static <T> @Nullable T handler(Level level, LinkableMachine machine, MachineLink link, BlockCapability<T, @Nullable Direction> capability,
            SideMode mode) {
        return machine.withForcedMode(mode, () -> {
            T handler = level.getCapability(capability, link.anchor(), link.face());
            if (handler == null && !link.anchor().equals(machine.getBlockPos())) {
                handler = level.getCapability(capability, machine.getBlockPos(), link.face());
            }
            return handler;
        });
    }

    // How an auto conduit touching a Wired Machine Link face in `mode` connects: as it would to that port on the machine.
    public static ConnectionMode connection(LinkableMachine machine, MachineLink link, SideMode mode, ConduitType type) {
        if (machine instanceof MultiblockController controller) {
            return controller.getConduitConnection(mode, type);
        }
        if (!(machine instanceof ConduitConnectable connectable)) {
            return ConnectionMode.NONE;
        }
        ConnectionMode connection = machine.withForcedMode(mode, () -> connectable.getConduitConnection(link.face(), type));
        return connection != null ? connection : ConnectionMode.NONE;
    }

    // The modes a Wired Machine Link face can be set to for this machine, NONE first: the machine's own port modes.
    public static List<SideMode> modes(LinkableMachine machine) {
        List<SideMode> modes = new ArrayList<>();
        modes.add(SideMode.NONE);
        for (SideMode mode : machine.getAllowedSideModes()) {
            if (mode != SideMode.NONE && mode != SideMode.MACHINE_LINK && !modes.contains(mode)) {
                modes.add(mode);
            }
        }
        return modes;
    }
}
