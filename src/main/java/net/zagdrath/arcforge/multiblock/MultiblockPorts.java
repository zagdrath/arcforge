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

// Multiblock ports: a structure does IO only through the blocks set as ports, each on one of its outer
// faces (see MultiblockController.faceMode). The port is block state (PORT, and PORT_FACE for the face it's
// on), so it needs no block entity, bakes into the block's model (a port plate on that face) and stays with
// the block when the structure breaks and forms again; loose blocks remember it but show nothing. Ports
// are set with the Wrench in Port mode on the face clicked, cycling through the modes the structure allows.
public final class MultiblockPorts {
    public static final EnumProperty<SideMode> PORT = EnumProperty.create("port", SideMode.class);
    public static final EnumProperty<Direction> PORT_FACE = EnumProperty.create("port_face", Direction.class);

    private MultiblockPorts() {}

    public static SideMode get(BlockState state) {
        return state.hasProperty(PORT) ? state.getValue(PORT) : SideMode.NONE;
    }

    public static Direction face(BlockState state) {
        return state.hasProperty(PORT_FACE) ? state.getValue(PORT_FACE) : Direction.NORTH;
    }

    // The port's mode on this face of the block: NONE unless the port is on it.
    public static SideMode get(BlockState state, Direction face) {
        return face(state) == face ? get(state) : SideMode.NONE;
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

    // The outer face of pos that faces most squarely out of the structure (on a tie, a side rather than
    // the top or bottom), or null if it has none: where a port goes when no face was picked for it.
    public static @Nullable Direction preferredFace(MultiblockController controller, BlockPos pos) {
        BlockPos min = controller.getMinCorner();
        BlockPos max = controller.getMaxCorner();
        Direction best = null;
        double bestScore = 0;
        for (Direction face : outerFaces(controller, pos)) {
            Direction.Axis axis = face.getAxis();
            double halfSize = (max.get(axis) - min.get(axis) + 1) / 2.0;
            double score = (pos.get(axis) + 0.5 - (min.get(axis) + max.get(axis) + 1) / 2.0) * face.getAxisDirection().getStep() / halfSize;
            boolean better = best == null || score > bestScore + 1.0E-6
                    || score > bestScore - 1.0E-6 && best.getAxis().isVertical() && axis.isHorizontal();
            if (better) {
                best = face;
                bestScore = score;
            }
        }
        return best;
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

    // Sets the port at pos to face, and reconnects everything round the structure. A block has one port,
    // so setting it on another face moves it there.
    public static void set(Level level, MultiblockController controller, BlockPos pos, SideMode mode, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(PORT) && (state.getValue(PORT) != mode || mode != SideMode.NONE && face(state) != face)) {
            level.setBlock(pos, withPort(state, mode, face), Block.UPDATE_ALL);
            changed(level, controller);
        }
    }

    private static BlockState withPort(BlockState state, SideMode mode, Direction face) {
        state = state.setValue(PORT, mode);
        return state.hasProperty(PORT_FACE) ? state.setValue(PORT_FACE, face) : state;
    }

    // Turns every port facing into the structure (one from a save made before port faces, or left facing
    // in by a structure that formed differently) to its preferred face. Whether any turned.
    private static boolean fixFaces(Level level, MultiblockController controller) {
        boolean turned = false;
        for (BlockPos pos : BlockPos.betweenClosed(controller.getMinCorner(), controller.getMaxCorner())) {
            BlockState state = level.getBlockState(pos);
            if (get(state) == SideMode.NONE || !controller.isPart(pos) || !controller.isInside(pos.relative(face(state)))) {
                continue;
            }
            Direction face = preferredFace(controller, pos);
            if (face != null && state.hasProperty(PORT_FACE)) {
                level.setBlock(pos, state.setValue(PORT_FACE, face), Block.UPDATE_ALL);
                turned = true;
            }
        }
        return turned;
    }

    private static void changed(Level level, MultiblockController controller) {
        MultiblockAutomation.refresh(level, controller.getMinCorner(), controller.getMaxCorner());
        controller.onPortsChanged();
    }

    // --- The ports of a structure ---

    // A port: where it is, what it does, the face it's on, and that face's side of the structure.
    public record Port(BlockPos pos, SideMode mode, Direction face, RelativeSide side) {}

    public static List<Port> list(Level level, MultiblockController controller) {
        List<Port> ports = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(controller.getMinCorner(), controller.getMaxCorner())) {
            BlockState state = level.getBlockState(pos);
            SideMode mode = get(state);
            Direction face = face(state);
            if (mode != SideMode.NONE && controller.isPart(pos) && !controller.isInside(pos.relative(face))) {
                ports.add(new Port(pos.immutable(), mode, face, RelativeSide.fromDirection(controller.getStructureFacing(), face)));
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
    public static Map<BlockPos, DefaultPort> fromSideConfig(Level level, MultiblockController controller) {
        Map<BlockPos, DefaultPort> ports = new LinkedHashMap<>();
        for (RelativeSide side : RelativeSide.values()) {
            SideMode mode = controller.getSideMode(side);
            if (mode == SideMode.NONE) {
                continue;
            }
            Direction face = side.toDirection(controller.getStructureFacing());
            BlockPos pos = controller.defaultPortPos(level, face);
            if (pos != null) {
                ports.putIfAbsent(pos, new DefaultPort(mode, face));
            }
        }
        return ports;
    }

    // A port a structure starts with: its mode, and the face it's on.
    public record DefaultPort(SideMode mode, Direction face) {}

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
    // Each time the structure forms (or loads formed), ports facing into it are turned to face out.
    public static final class Defaults {
        private boolean initialised;
        // Loaded from a save that predates ports.
        private boolean fromOldSave;
        // The port faces have been checked since the structure last formed.
        private boolean facesChecked;

        public void tick(Level level, MultiblockController controller) {
            if (!controller.isFormed()) {
                facesChecked = false;
                return;
            }
            boolean changed = !facesChecked && fixFaces(level, controller);
            facesChecked = true;
            if (!initialised) {
                initialised = true;
                if (!hasPorts(level, controller)) {
                    Map<BlockPos, DefaultPort> ports = fromOldSave ? fromSideConfig(level, controller) : controller.defaultPorts(level);
                    for (Map.Entry<BlockPos, DefaultPort> port : ports.entrySet()) {
                        BlockPos pos = port.getKey();
                        BlockState state = level.getBlockState(pos);
                        Direction face = controller.isInside(pos.relative(port.getValue().face())) ? preferredFace(controller, pos) : port.getValue().face();
                        if (state.hasProperty(PORT) && face != null) {
                            level.setBlock(pos, withPort(state, port.getValue().mode(), face), Block.UPDATE_ALL);
                        }
                    }
                    changed = true;
                }
            }
            if (changed) {
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
