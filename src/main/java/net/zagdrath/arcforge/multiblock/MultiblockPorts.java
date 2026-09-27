/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;

// Multiblock ports: a structure does IO only through the blocks set as ports, each on every one of its
// outer faces (see MultiblockController.faceMode). The port is a block state (PORT), so it needs no block
// entity, bakes into the block's model (a port plate on its outer faces) and stays with the block when the
// structure breaks and forms again; loose blocks remember it but show nothing. Ports are set with the
// Wrench in Port mode, cycling through the modes the structure allows.
public final class MultiblockPorts {
    public static final EnumProperty<SideMode> PORT = EnumProperty.create("port", SideMode.class);

    private MultiblockPorts() {}

    public static SideMode get(BlockState state) {
        return state.hasProperty(PORT) ? state.getValue(PORT) : SideMode.NONE;
    }

    // The sides of pos that face out of the structure's box.
    public static List<Direction> outerFaces(MultiblockController controller, BlockPos pos) {
        List<Direction> faces = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (!controller.isInside(pos.relative(side))) {
                faces.add(side);
            }
        }
        return faces;
    }

    // Whether the block at pos can be one of the structure's ports: a part that can hold one, on its outside.
    public static boolean canHold(Level level, MultiblockController controller, BlockPos pos) {
        return controller.isPart(pos) && level.getBlockState(pos).hasProperty(PORT) && !outerFaces(controller, pos).isEmpty();
    }

    // The mode after current in the structure's allowed modes (wrapping through NONE).
    public static SideMode next(MultiblockController controller, SideMode current, boolean backward) {
        List<SideMode> allowed = new ArrayList<>(controller.getAllowedSideModes());
        if (!allowed.contains(SideMode.NONE)) {
            allowed.addFirst(SideMode.NONE);
        }
        int index = allowed.indexOf(current);
        int step = backward ? allowed.size() - 1 : 1;
        return allowed.get(index < 0 ? 0 : (index + step) % allowed.size());
    }

    // Sets the port at pos, and reconnects everything round the structure.
    public static void set(Level level, MultiblockController controller, BlockPos pos, SideMode mode) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(PORT) && state.getValue(PORT) != mode) {
            level.setBlock(pos, state.setValue(PORT, mode), Block.UPDATE_ALL);
            changed(level, controller);
        }
    }

    private static void changed(Level level, MultiblockController controller) {
        MultiblockAutomation.refresh(level, controller.getMinCorner(), controller.getMaxCorner());
        controller.onPortsChanged();
    }

    // --- The ports of a structure ---

    // A port: where it is, what it does, and the side of the structure it's on (an outer side face if it
    // has one, else its top or bottom).
    public record Port(BlockPos pos, SideMode mode, RelativeSide side) {}

    public static List<Port> list(Level level, MultiblockController controller) {
        List<Port> ports = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(controller.getMinCorner(), controller.getMaxCorner())) {
            SideMode mode = get(level.getBlockState(pos));
            if (mode == SideMode.NONE || !controller.isPart(pos)) {
                continue;
            }
            List<Direction> faces = outerFaces(controller, pos);
            if (!faces.isEmpty()) {
                Direction side = faces.stream().filter(face -> face.getAxis().isHorizontal()).findFirst().orElse(faces.getFirst());
                ports.add(new Port(pos.immutable(), mode, RelativeSide.fromDirection(controller.getStructureFacing(), side)));
            }
        }
        return ports;
    }

    // --- Defaults ---

    // The part that can hold a port nearest to target on the given side of the structure, or null.
    public static @Nullable BlockPos nearest(Level level, MultiblockController controller, Direction side, Vec3 target) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(controller.getMinCorner(), controller.getMaxCorner())) {
            if (controller.isInside(pos.relative(side)) || !canHold(level, controller, pos)) {
                continue;
            }
            double distance = Vec3.atCenterOf(pos).distanceToSqr(target);
            if (distance < bestDistance - 1.0E-6) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    // The block in the middle of one side of the structure (the nearest that can hold a port).
    public static @Nullable BlockPos faceCentre(Level level, MultiblockController controller, Direction side) {
        BlockPos min = controller.getMinCorner();
        BlockPos max = controller.getMaxCorner();
        Vec3 centre = new Vec3((min.getX() + max.getX()) / 2.0 + 0.5, (min.getY() + max.getY()) / 2.0 + 0.5, (min.getZ() + max.getZ()) / 2.0 + 0.5);
        return nearest(level, controller, side, centre);
    }

    // A port in the middle of each side of the structure that its side configuration uses: how the side
    // configuration of saves from before ports carries over, and the usual defaults.
    public static Map<BlockPos, SideMode> fromSideConfig(Level level, MultiblockController controller) {
        Map<BlockPos, SideMode> ports = new LinkedHashMap<>();
        for (RelativeSide side : RelativeSide.values()) {
            SideMode mode = controller.getSideMode(side);
            if (mode == SideMode.NONE) {
                continue;
            }
            BlockPos pos = controller.defaultPortPos(level, side.toDirection(controller.getStructureFacing()));
            if (pos != null) {
                ports.putIfAbsent(pos, mode);
            }
        }
        return ports;
    }

    // Whether any part of the structure is a port.
    private static boolean hasPorts(Level level, MultiblockController controller) {
        for (BlockPos pos : BlockPos.betweenClosed(controller.getMinCorner(), controller.getMaxCorner())) {
            if (get(level.getBlockState(pos)) != SideMode.NONE && controller.isPart(pos)) {
                return true;
            }
        }
        return false;
    }

    // Kept by each controller: whether its structure has been given its first ports. The first time it's
    // formed with no ports at all, it gets its defaults (or, in a save from before ports, ports where its
    // side configuration had faces); after that, nothing is filled in again, so clearing every port sticks.
    public static final class Defaults {
        private boolean initialised;
        // Loaded from a save that predates ports.
        private boolean fromOldSave;

        public void tick(Level level, MultiblockController controller) {
            if (initialised || !controller.isFormed()) {
                return;
            }
            initialised = true;
            if (!hasPorts(level, controller)) {
                Map<BlockPos, SideMode> ports = fromOldSave ? fromSideConfig(level, controller) : controller.defaultPorts(level);
                for (Map.Entry<BlockPos, SideMode> port : ports.entrySet()) {
                    BlockState state = level.getBlockState(port.getKey());
                    if (state.hasProperty(PORT)) {
                        level.setBlock(port.getKey(), state.setValue(PORT, port.getValue()), Block.UPDATE_ALL);
                    }
                }
                changed(level, controller);
            }
        }

        public boolean isInitialised() {
            return initialised;
        }

        public void load(ValueInput input) {
            initialised = input.getBooleanOr("ports_initialised", false);
            fromOldSave = input.getBooleanOr("ports_initialised", true) && !initialised;
        }

        public void save(ValueOutput output) {
            output.putBoolean("ports_initialised", initialised);
        }
    }
}
