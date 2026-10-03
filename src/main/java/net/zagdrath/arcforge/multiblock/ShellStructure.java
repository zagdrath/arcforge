/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
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
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.ShellCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;

// A hollow box of casings and Pressure Glass along one of the allowed axes. Its cross-section (the two
// other axes) and its length along the axis may be any size the structure's SizeRule allows: the Steam
// Boiler Array stands 3-12 tall on a footprint 3-7 by 3-9; the Steam Turbine Array lies 3-15 long with a
// cross-section 3-7 wide and 3-9 tall; the Gas Turbine Array keeps its 3x3 cross-section, 5-9 long. The
// eight corners must be casings. In a 3x3 cross-section the rest of the skin may be casing or glass, as it
// always could, so a window can take a whole wall; in a larger one the twelve edges must be casings too,
// and the faces between them may be casing or glass. Everything inside (the core) must be air.
//
// Every casing's block entity remembers the shell it belongs to; the one at the minimum corner (always a
// casing) is the master that runs the machine and holds its contents.
public final class ShellStructure {
    // The cross-section of the original arrays, and of a shell saved before cross-sections could vary.
    public static final int WIDTH = 3;
    // The most blocks a search follows, and how far it reaches from where it started: past the biggest box.
    private static final int MAX_SPAN = 16;
    private static final int MAX_FLOOD = 4_096;

    // Which box sizes make a valid structure, by its long axis and its sizes along X, Y and Z.
    @FunctionalInterface
    public interface SizeRule {
        boolean allows(Direction.Axis axis, int x, int y, int z);
    }

    // A candidate or formed structure: its minimum corner, long axis and length along it, and its sizes on the
    // other two axes (in X, Y, Z order: Y and Z for an X shell, X and Z for a Y shell, X and Y for a Z shell).
    public record Shell(BlockPos min, Direction.Axis axis, int length, int a, int b) {
        // A 3x3 shell (as every shell was before cross-sections could vary).
        public Shell(BlockPos min, Direction.Axis axis, int length) {
            this(min, axis, length, WIDTH, WIDTH);
        }

        // The shell spanning the box from min to max, along axis.
        public static Shell between(BlockPos min, BlockPos max, Direction.Axis axis) {
            int[] size = { max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1 };
            Direction.Axis first = first(axis), second = second(axis);
            return new Shell(min.immutable(), axis, size[axis.ordinal()], size[first.ordinal()], size[second.ordinal()]);
        }

        // The other two axes, in X, Y, Z order.
        public static Direction.Axis first(Direction.Axis axis) {
            return axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        }

        public static Direction.Axis second(Direction.Axis axis) {
            return axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        }

        public BlockPos max() {
            return min.offset(size(Direction.Axis.X) - 1, size(Direction.Axis.Y) - 1, size(Direction.Axis.Z) - 1);
        }

        public int size(Direction.Axis of) {
            return of == axis ? length : of == first(axis) ? a : b;
        }

        // The cross-section's sides: the shorter and the longer.
        public int narrowSide() {
            return Math.min(a, b);
        }

        public int wideSide() {
            return Math.max(a, b);
        }

        // Blocks in the box, skin and core.
        public int volume() {
            return length * a * b;
        }

        // Whether the cross-section is the original 3x3.
        public boolean isNarrow() {
            return a == WIDTH && b == WIDTH;
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

        // On how many axes pos lies on the box's boundary: 0 in the core, 1 on a face, 2 on an edge, 3 a corner.
        public int boundaries(BlockPos pos) {
            int count = 0;
            for (Direction.Axis other : Direction.Axis.values()) {
                int offset = offset(pos, other);
                if (offset == 0 || offset == size(other) - 1) {
                    count++;
                }
            }
            return count;
        }

        // The hollow middle: inside the box on every axis.
        public boolean isCore(BlockPos pos) {
            return contains(pos) && boundaries(pos) == 0;
        }

        public boolean isCorner(BlockPos pos) {
            return contains(pos) && boundaries(pos) == 3;
        }

        public boolean isEdge(BlockPos pos) {
            return contains(pos) && boundaries(pos) >= 2;
        }

        // The middle block of an end face (on the long axis), at the given end (for an even side, the block
        // just below the middle).
        public BlockPos endCenter(Direction.AxisDirection end) {
            int along = end == Direction.AxisDirection.POSITIVE ? length - 1 : 0;
            int[] offset = new int[3];
            for (Direction.Axis other : Direction.Axis.values()) {
                offset[other.ordinal()] = other == axis ? along : (size(other) - 1) / 2;
            }
            return min.offset(offset[0], offset[1], offset[2]);
        }

        // The block at the middle of the box (for an even side, the one just below the middle).
        public BlockPos centre() {
            return min.offset((size(Direction.Axis.X) - 1) / 2, (size(Direction.Axis.Y) - 1) / 2, (size(Direction.Axis.Z) - 1) / 2);
        }

        // Whether both cross-section sides are odd, so the end faces have a true middle block.
        public boolean hasEndCenters() {
            return a % 2 == 1 && b % 2 == 1;
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
    private final SizeRule sizes;
    // Whether the middle blocks of the two end faces must be casings, not glass (the Gas Turbine Array's
    // intake and exhaust).
    private final boolean casingEndCenters;

    public ShellStructure(Predicate<Block> casing, Set<Direction.Axis> axes, SizeRule sizes) {
        this(casing, axes, sizes, false);
    }

    public ShellStructure(Predicate<Block> casing, Set<Direction.Axis> axes, SizeRule sizes, boolean casingEndCenters) {
        this.casing = casing;
        this.axes = axes;
        this.sizes = sizes;
        this.casingEndCenters = casingEndCenters;
    }

    // A rule for a 3x3 tube minLength to maxLength long (the Gas Turbine Array).
    public static SizeRule narrow(int minLength, int maxLength) {
        return (axis, x, y, z) -> {
            int[] size = { x, y, z };
            for (Direction.Axis other : Direction.Axis.values()) {
                int value = size[other.ordinal()];
                if (other == axis ? value < minLength || value > maxLength : value != WIDTH) {
                    return false;
                }
            }
            return true;
        };
    }

    public boolean allows(Shell shell) {
        return axes.contains(shell.axis()) && sizes.allows(shell.axis(), shell.size(Direction.Axis.X), shell.size(Direction.Axis.Y),
                shell.size(Direction.Axis.Z));
    }

    public boolean isCasing(BlockState state) {
        return casing.test(state.getBlock());
    }

    // --- Finding ---

    // A complete, unclaimed shell with pos in its skin, or null. It first takes the box spanned by every loose
    // casing and pane joined to pos (so a box of any size forms at once); if that isn't a valid shell (another
    // array's loose blocks touch it, say), it tries every 3x3 tube through pos, as the arrays always formed.
    public @Nullable Shell find(Level level, BlockPos pos, Direction.@Nullable Axis preferred) {
        List<Direction.Axis> order = new ArrayList<>(axes);
        if (preferred != null && order.remove(preferred)) {
            order.addFirst(preferred);
        }
        BlockPos[] box = joinedBox(level, pos);
        if (box != null) {
            for (Direction.Axis axis : order) {
                Shell shell = Shell.between(box[0], box[1], axis);
                if (allows(shell) && isValid(level, shell)) {
                    return shell;
                }
            }
        }
        for (Direction.Axis axis : order) {
            for (int length = 3; length <= MAX_SPAN; length++) {
                for (int along = 0; along < length; along++) {
                    for (int a = 0; a < WIDTH; a++) {
                        for (int b = 0; b < WIDTH; b++) {
                            BlockPos min = switch (axis) {
                                case X -> pos.offset(-along, -a, -b);
                                case Y -> pos.offset(-a, -along, -b);
                                case Z -> pos.offset(-a, -b, -along);
                            };
                            Shell shell = new Shell(min, axis, length);
                            if (!shell.isCore(pos) && allows(shell) && isValid(level, shell)) {
                                return shell;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // The bounding box of the loose casings and unformed panes joined (face to face) to pos, or null if there
    // are none or they reach too far to be one structure.
    private BlockPos @Nullable [] joinedBox(Level level, BlockPos pos) {
        if (!isLoose(level, pos)) {
            return null;
        }
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = pos.immutable();
        found.add(start);
        queue.add(start);
        int minX = start.getX(), minY = start.getY(), minZ = start.getZ(), maxX = minX, maxY = minY, maxZ = minZ;
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (found.contains(next) || Math.abs(next.getX() - start.getX()) >= MAX_SPAN || Math.abs(next.getY() - start.getY()) >= MAX_SPAN
                        || Math.abs(next.getZ() - start.getZ()) >= MAX_SPAN || !isLoose(level, next)) {
                    continue;
                }
                if (found.size() >= MAX_FLOOD) {
                    return null;
                }
                found.add(next);
                queue.add(next);
                minX = Math.min(minX, next.getX());
                minY = Math.min(minY, next.getY());
                minZ = Math.min(minZ, next.getZ());
                maxX = Math.max(maxX, next.getX());
                maxY = Math.max(maxY, next.getY());
                maxZ = Math.max(maxZ, next.getZ());
            }
        }
        return new BlockPos[] { new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ) };
    }

    // A casing of this structure not in a formed one, or an unformed pane.
    private boolean isLoose(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (isCasing(state)) {
            return !(level.getBlockEntity(pos) instanceof ShellMultiblockBlockEntity part) || part.getShell() == null;
        }
        return state.getBlock() instanceof PressureGlassBlock && !PressureGlassBlock.isFormed(state);
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
        boolean edgesCasing = !shell.isNarrow();
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
            } else if (edgesCasing && shell.isEdge(pos)) {
                return false;
            } else if (!(state.getBlock() instanceof PressureGlassBlock) || PressureGlassBlock.isFormed(state)) {
                return false;
            }
        }
        return true;
    }

    // The formed shell containing pos (a casing, a pane, or a block that was one and is gone), or null. A
    // casing knows its shell; otherwise the casings near pos are asked, walking out through formed panes (a
    // window can be a whole wall), so the shell is found however big it is.
    public @Nullable Shell findFormed(BlockGetter level, BlockPos pos) {
        Shell own = ownShell(level, pos);
        if (own != null) {
            return own;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = pos.immutable();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < MAX_FLOOD) {
            BlockPos at = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (!seen.add(next)) {
                    continue;
                }
                Shell shell = ownShell(level, next);
                if (shell != null && shell.contains(pos)) {
                    return shell;
                }
                if (PressureGlassBlock.isFormed(level.getBlockState(next)) && next.distManhattan(start) <= 3 * MAX_SPAN) {
                    queue.add(next);
                }
            }
        }
        return null;
    }

    // The shell the casing at pos belongs to, if it is a casing of this structure in a formed one.
    private @Nullable Shell ownShell(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ShellMultiblockBlockEntity part && part.getShell() != null && isCasing(level.getBlockState(pos))
                ? part.getShell()
                : null;
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
        if (level.getBlockEntity(shell.min()) instanceof ShellMultiblockBlockEntity master) {
            ArcforgeAdvancements.formed(level, master, null);
        }
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
