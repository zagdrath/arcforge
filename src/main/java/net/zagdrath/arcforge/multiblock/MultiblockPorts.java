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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;

// Multiblock ports: a structure does IO only through its ports, each an outer face of one of its blocks set
// to a mode (see MultiblockController.faceMode). Each face of a block is its own port, so a corner can take
// items in on one side and give them out on another. Ports are kept by position (see PortStore), so they
// stay where they were set when the structure breaks and forms again, even if a port's block is broken and
// put back; they only count on a block that can hold ports (PortHolder) in a formed structure, and loose
// blocks show nothing. The models draw a plate on each port face. Ports are set with the Wrench in Port
// mode on the face clicked, cycling through the modes the structure allows.
public final class MultiblockPorts {
    private MultiblockPorts() {}

    // The ports on every face of the block at pos (on a server level, or a client's while its chunk meshes).
    public static PortFaces faces(BlockGetter level, BlockPos pos) {
        return PortStore.get(level, pos);
    }

    // The port on this face of the block at pos, or NONE.
    public static SideMode get(BlockGetter level, BlockPos pos, Direction face) {
        return faces(level, pos).get(face);
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
        return controller.isPart(pos) && level.getBlockState(pos).getBlock() instanceof PortHolder holder
                && holder.holdsPorts(level.getBlockState(pos)) && !outerFaces(controller, pos).isEmpty();
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

    // Sets this face of the block at pos to a port in mode (NONE clears it), and reconnects everything round
    // the structure. The block's other faces keep their ports.
    public static void set(Level level, MultiblockController controller, BlockPos pos, SideMode mode, Direction face) {
        if (PortStore.set(level, pos, faces(level, pos).with(face, mode))) {
            changed(level, controller);
        }
    }

    private static void changed(Level level, MultiblockController controller) {
        MultiblockAutomation.refresh(level, controller.getMinCorner(), controller.getMaxCorner());
        controller.onPortsChanged();
    }

    // --- The ports of a structure ---

    // A port: the block it's on, what it does, the face it's on, and that face's side of the structure.
    public record Port(BlockPos pos, SideMode mode, Direction face, RelativeSide side) {}

    // Every port of the structure: each outer face of a block that can hold ports, set to a mode.
    public static List<Port> list(Level level, MultiblockController controller) {
        List<Port> ports = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(controller.getMinCorner(), controller.getMaxCorner())) {
            if (!canHold(level, controller, pos)) {
                continue;
            }
            PortFaces faces = faces(level, pos);
            for (Direction face : outerFaces(controller, pos)) {
                SideMode mode = faces.get(face);
                if (mode != SideMode.NONE) {
                    ports.add(new Port(pos.immutable(), mode, face, RelativeSide.fromDirection(controller.getStructureFacing(), face)));
                }
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
        return !list(level, controller).isEmpty();
    }

    // Kept by each controller: whether its structure has been given its first ports. The first time it's
    // formed with no ports at all, it gets its defaults (or, in a save from before ports, ports where its
    // side configuration had faces); after that, nothing is filled in again, so clearing every port sticks.
    // Saves from when a port was part of its block's state lost those ports, so they get their defaults
    // once more.
    public static final class Defaults {
        private boolean initialised;
        // Loaded from a save that predates ports.
        private boolean fromOldSave;

        public void tick(Level level, MultiblockController controller) {
            if (initialised || !controller.isFormed()) {
                return;
            }
            initialised = true;
            if (hasPorts(level, controller)) {
                return;
            }
            Map<BlockPos, DefaultPort> ports = fromOldSave ? fromSideConfig(level, controller) : controller.defaultPorts(level);
            for (Map.Entry<BlockPos, DefaultPort> port : ports.entrySet()) {
                BlockPos pos = port.getKey();
                Direction face = controller.isInside(pos.relative(port.getValue().face())) ? preferredFace(controller, pos) : port.getValue().face();
                if (face != null && canHold(level, controller, pos)) {
                    PortStore.set(level, pos, faces(level, pos).with(face, port.getValue().mode()));
                }
            }
            changed(level, controller);
        }

        public boolean isInitialised() {
            return initialised;
        }

        public void load(ValueInput input) {
            initialised = input.getBooleanOr("ports_v2_initialised", false);
            fromOldSave = !initialised && input.getBooleanOr("ports_initialised", true);
        }

        public void save(ValueOutput output) {
            output.putBoolean("ports_initialised", true);
            output.putBoolean("ports_v2_initialised", initialised);
        }
    }
}
