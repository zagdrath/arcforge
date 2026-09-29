/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.ShellCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;

// A hollow 3x3 tube of casings and Pressure Glass, between minLength and maxLength long along one of
// the allowed axes (the Steam Boiler Array stands 3-7 tall; the Steam Turbine Array lies 3-9 long). The
// eight corners must be casings; the rest of the shell may be casing or glass, so a window can take a
// whole wall. The core (the middle of the cross-section, between the end faces) must be air.
//
// Every casing's block entity remembers the shell it belongs to; the one at the minimum corner (always a
// casing) is the master that runs the machine and holds its contents.
public final class ShellStructure {
    public static final int WIDTH = 3;

    // A candidate or formed structure: its minimum corner, long axis and length along it.
    public record Shell(BlockPos min, Direction.Axis axis, int length) {
        public BlockPos max() {
            return min.offset(size(Direction.Axis.X) - 1, size(Direction.Axis.Y) - 1, size(Direction.Axis.Z) - 1);
        }

        public int size(Direction.Axis of) {
            return of == axis ? length : WIDTH;
        }

        public boolean contains(BlockPos pos) {
            BlockPos max = max();
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }

        // Offset of pos from the minimum corner along an axis.
        public int offset(BlockPos pos, Direction.Axis of) {
            return pos.get(of) - min.get(of);
        }

        // The hollow middle: centred across, and not on an end face.
        public boolean isCore(BlockPos pos) {
            for (Direction.Axis other : Direction.Axis.values()) {
                int offset = offset(pos, other);
                if (other == axis ? offset <= 0 || offset >= length - 1 : offset != 1) {
                    return false;
                }
            }
            return true;
        }

        public boolean isCorner(BlockPos pos) {
            for (Direction.Axis other : Direction.Axis.values()) {
                int offset = offset(pos, other);
                if (offset != 0 && offset != size(other) - 1) {
                    return false;
                }
            }
            return true;
        }

        // The middle block of an end face (on the long axis), at the given end.
        public BlockPos endCenter(Direction.AxisDirection end) {
            BlockPos center = min.offset(1, 1, 1);
            int along = end == Direction.AxisDirection.POSITIVE ? length - 1 : 0;
            return switch (axis) {
                case X -> new BlockPos(min.getX() + along, center.getY(), center.getZ());
                case Y -> new BlockPos(center.getX(), min.getY() + along, center.getZ());
                case Z -> new BlockPos(center.getX(), center.getY(), min.getZ() + along);
            };
        }

        public List<BlockPos> corners() {
            List<BlockPos> corners = new ArrayList<>(8);
            BlockPos max = max();
            for (int x : new int[] { min.getX(), max.getX() }) {
                for (int y : new int[] { min.getY(), max.getY() }) {
                    for (int z : new int[] { min.getZ(), max.getZ() }) {
                        corners.add(new BlockPos(x, y, z));
                    }
                }
            }
            return corners;
        }

        public Iterable<BlockPos> positions() {
            return BlockPos.betweenClosed(min, max());
        }
    }

    private final Predicate<Block> casing;
    private final Set<Direction.Axis> axes;
    private final int minLength;
    private final int maxLength;
    // Whether the middle blocks of the two end faces must be casings, not glass (the Gas Turbine Array's
    // intake and exhaust).
    private final boolean casingEndCenters;

    public ShellStructure(Predicate<Block> casing, Set<Direction.Axis> axes, int minLength, int maxLength) {
        this(casing, axes, minLength, maxLength, false);
    }

    public ShellStructure(Predicate<Block> casing, Set<Direction.Axis> axes, int minLength, int maxLength, boolean casingEndCenters) {
        this.casing = casing;
        this.axes = axes;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.casingEndCenters = casingEndCenters;
    }

    public int minLength() {
        return minLength;
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean isCasing(BlockState state) {
        return casing.test(state.getBlock());
    }

    // --- Finding ---

    // Every shell of the allowed sizes that has pos in its skin, in axis order (preferred axis first).
    private List<Shell> candidates(BlockPos pos, Direction.@Nullable Axis preferred) {
        List<Shell> shells = new ArrayList<>();
        List<Direction.Axis> order = new ArrayList<>(axes);
        if (preferred != null && order.remove(preferred)) {
            order.addFirst(preferred);
        }
        for (Direction.Axis axis : order) {
            for (int length = minLength; length <= maxLength; length++) {
                for (int along = 0; along < length; along++) {
                    for (int a = 0; a < WIDTH; a++) {
                        for (int b = 0; b < WIDTH; b++) {
                            BlockPos min = switch (axis) {
                                case X -> pos.offset(-along, -a, -b);
                                case Y -> pos.offset(-a, -along, -b);
                                case Z -> pos.offset(-a, -b, -along);
                            };
                            Shell shell = new Shell(min, axis, length);
                            if (!shell.isCore(pos)) {
                                shells.add(shell);
                            }
                        }
                    }
                }
            }
        }
        return shells;
    }

    // A complete, unclaimed shell with pos in its skin, or null.
    public @Nullable Shell find(Level level, BlockPos pos, Direction.@Nullable Axis preferred) {
        for (Shell shell : candidates(pos, preferred)) {
            if (isValid(level, shell)) {
                return shell;
            }
        }
        return null;
    }

    private boolean isValid(Level level, Shell shell) {
        // Corners first: most candidates fail here.
        for (BlockPos corner : shell.corners()) {
            if (!level.isLoaded(corner) || !isCasing(level.getBlockState(corner))) {
                return false;
            }
        }
        if (casingEndCenters) {
            for (Direction.AxisDirection end : Direction.AxisDirection.values()) {
                BlockPos center = shell.endCenter(end);
                if (!level.isLoaded(center) || !isCasing(level.getBlockState(center))) {
                    return false;
                }
            }
        }
        for (BlockPos pos : shell.positions()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (shell.isCore(pos)) {
                if (!state.isAir()) {
                    return false;
                }
            } else if (isCasing(state)) {
                // Not already part of another structure.
                if (level.getBlockEntity(pos) instanceof ShellMultiblockBlockEntity part && part.getShell() != null && !shell.equals(part.getShell())) {
                    return false;
                }
            } else if (!(state.getBlock() instanceof PressureGlassBlock) || PressureGlassBlock.isFormed(state)) {
                return false;
            }
        }
        return true;
    }

    // The formed shell containing pos (a casing, glass or the core), or null. Looked up from the corner
    // casings, since the block at pos may be glass or already gone.
    public @Nullable Shell findFormed(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ShellMultiblockBlockEntity part && part.getShell() != null && isCasing(level.getBlockState(pos))) {
            return part.getShell();
        }
        for (Direction.Axis axis : axes) {
            for (int length = minLength; length <= maxLength; length++) {
                for (int along = 0; along < length; along++) {
                    for (int a = 0; a < WIDTH; a++) {
                        for (int b = 0; b < WIDTH; b++) {
                            BlockPos min = switch (axis) {
                                case X -> pos.offset(-along, -a, -b);
                                case Y -> pos.offset(-a, -along, -b);
                                case Z -> pos.offset(-a, -b, -along);
                            };
                            Shell shell = new Shell(min, axis, length);
                            if (claimedBy(level, shell, pos)) {
                                return shell;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // Whether a corner casing other than `except` says it belongs to this shell.
    private boolean claimedBy(BlockGetter level, Shell shell, BlockPos except) {
        for (BlockPos corner : shell.corners()) {
            if (!corner.equals(except) && level.getBlockEntity(corner) instanceof ShellMultiblockBlockEntity part
                    && isCasing(level.getBlockState(corner))) {
                return shell.equals(part.getShell());
            }
        }
        return false;
    }

    public @Nullable ShellMultiblockBlockEntity findMaster(BlockGetter level, BlockPos pos) {
        Shell shell = findFormed(level, pos);
        return shell != null && level.getBlockEntity(shell.min()) instanceof ShellMultiblockBlockEntity master && shell.equals(master.getShell())
                ? master
                : null;
    }

    // --- Forming and breaking ---

    // A casing or glass was placed at pos: forms the structure it completes, if any. preferred: the axis
    // to try first (the placing player's facing), for a cube that could run either way.
    public void rebuild(ServerLevel level, BlockPos pos, Direction.@Nullable Axis preferred, @Nullable Direction facing) {
        if (findFormed(level, pos) != null) {
            return;
        }
        Shell shell = find(level, pos, preferred);
        if (shell != null) {
            form(level, shell, facing);
        }
    }

    // The block at pos is gone (or no longer fits): the structure it was in falls apart.
    public void onRemoved(ServerLevel level, BlockPos pos) {
        Shell shell = findFormed(level, pos);
        if (shell != null) {
            unform(level, shell);
        }
    }

    private void form(ServerLevel level, Shell shell, @Nullable Direction facing) {
        for (BlockPos pos : shell.positions()) {
            if (shell.isCore(pos)) {
                continue;
            }
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            if (state.getBlock() instanceof ShellCasingBlock block) {
                level.setBlock(immutable, block.formedState(state, shell, immutable), Block.UPDATE_ALL);
                if (level.getBlockEntity(immutable) instanceof ShellMultiblockBlockEntity part) {
                    part.setShell(shell);
                }
            } else if (state.getBlock() instanceof PressureGlassBlock) {
                level.setBlock(immutable, state.setValue(PressureGlassBlock.FORMED, true), Block.UPDATE_ALL);
            }
        }
        if (level.getBlockEntity(shell.min()) instanceof ShellMultiblockBlockEntity master) {
            master.onFormed(facing);
        }
        MultiblockAutomation.refresh(level, shell.min(), shell.max());
        MultiblockEffects.formed(level, shell.min(), shell.max());
    }

    private void unform(ServerLevel level, Shell shell) {
        if (level.getBlockEntity(shell.min()) instanceof ShellMultiblockBlockEntity master && shell.equals(master.getShell())) {
            master.onUnformed();
        }
        for (BlockPos pos : shell.positions()) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            if (state.getBlock() instanceof ShellCasingBlock block && isCasing(state)) {
                if (level.getBlockEntity(immutable) instanceof ShellMultiblockBlockEntity part && shell.equals(part.getShell())) {
                    part.setShell(null);
                }
                level.setBlock(immutable, block.looseState(state), Block.UPDATE_ALL);
            } else if (state.getBlock() instanceof PressureGlassBlock && PressureGlassBlock.isFormed(state)) {
                level.setBlock(immutable, state.setValue(PressureGlassBlock.FORMED, false), Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, shell.min(), shell.max());
    }
}
