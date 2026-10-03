/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.multiblock.CubeCasingBlock;
import net.zagdrath.arcforge.block.multiblock.CubeCasingBlock.Part;
import net.zagdrath.arcforge.blockentity.multiblock.CubeMultiblockBlockEntity;

// A solid 3x3x3 cube of one kind of casing (the Arc Crushing Array, the Induction Furnace Array). When
// formed, the centre block becomes part=center and draws the whole machine (and its block entity runs
// it); the other 26 become part=other and draw nothing. The front faces the player who completed it.
// Casings of different machines never join.
//
// A structure with a larger maxSize (the Superheater and Condenser Arrays) also forms as any solid box 3 to maxSize
// blocks each way. A box that isn't 3x3x3 is drawn as one connected skin (box=true on every casing; see ConnectedModel,
// "cube_box"): its master is the casing at the minimum corner (part=center, which runs it), and every casing's block
// entity remembers the box (see CubeMultiblockBlockEntity.getBox). A 3x3x3 forms exactly as before.
public final class CubeMultiblockStructure<T extends CubeMultiblockBlockEntity> {
    public static final int SIZE = 3;
    private static final int BLOCKS = SIZE * SIZE * SIZE;

    private final Class<? extends CubeCasingBlock> casing;
    private final Class<T> controller;
    private final int maxSize;

    public CubeMultiblockStructure(Class<? extends CubeCasingBlock> casing, Class<T> controller) {
        this(casing, controller, SIZE);
    }

    public CubeMultiblockStructure(Class<? extends CubeCasingBlock> casing, Class<T> controller, int maxSize) {
        this.casing = casing;
        this.controller = controller;
        this.maxSize = Math.max(SIZE, maxSize);
    }

    public int maxSize() {
        return maxSize;
    }

    private boolean isCasing(BlockState state) {
        return casing.isInstance(state.getBlock());
    }

    // The centre block of the formed structure the casing at pos belongs to, or null.
    public @Nullable BlockPos findCenter(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isCasing(state) || state.getValue(CubeCasingBlock.PART) == Part.NONE) {
            return null;
        }
        if (state.getValue(CubeCasingBlock.PART) == Part.CENTER) {
            return pos;
        }
        // A casing of a larger box knows its master.
        if (CubeCasingBlock.isBox(state)) {
            BlockPos master = level.getBlockEntity(pos) instanceof CubeMultiblockBlockEntity part && part.getBox() != null ? part.getBox().min() : null;
            if (master != null) {
                BlockState masterState = level.getBlockState(master);
                if (isCasing(masterState) && masterState.getValue(CubeCasingBlock.PART) == Part.CENTER && CubeCasingBlock.isBox(masterState)) {
                    return master;
                }
            }
            return null;
        }
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            BlockState other = level.getBlockState(candidate);
            if (isCasing(other) && other.getValue(CubeCasingBlock.PART) == Part.CENTER) {
                return candidate.immutable();
            }
        }
        return null;
    }

    public @Nullable T findController(BlockGetter level, BlockPos pos) {
        BlockPos center = findCenter(level, pos);
        return center != null && controller.isInstance(level.getBlockEntity(center)) ? controller.cast(level.getBlockEntity(center)) : null;
    }

    // Re-evaluates the casings connected to origin. viewer: the player completing it, so the front faces
    // them; null keeps the current facing.
    public void rebuild(ServerLevel level, BlockPos origin, @Nullable Vec3 viewer) {
        if (isCasing(level.getBlockState(origin))) {
            apply(level, flood(level, origin), viewer);
        }
    }

    // After a casing is removed: re-evaluates everything that touched it. A larger box first breaks as a whole, so its
    // casings beyond the removed block's neighbours don't keep the box.
    public void rebuildAround(ServerLevel level, BlockPos removed) {
        for (BlockPos pos : BlockPos.betweenClosed(removed.offset(-1, -1, -1), removed.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(pos);
            if (isCasing(state) && CubeCasingBlock.isBox(state) && level.getBlockEntity(pos) instanceof CubeMultiblockBlockEntity part
                    && part.getBox() != null) {
                Box box = part.getBox();
                Set<BlockPos> all = new HashSet<>();
                for (BlockPos inside : BlockPos.betweenClosed(box.min(), box.max())) {
                    if (isCasing(level.getBlockState(inside))) {
                        all.add(inside.immutable());
                    }
                }
                unform(level, all);
                break;
            }
        }
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(removed.offset(-1, -1, -1), removed.offset(1, 1, 1))) {
            if (!done.contains(pos) && isCasing(level.getBlockState(pos))) {
                Set<BlockPos> blocks = flood(level, pos.immutable());
                done.addAll(blocks);
                apply(level, blocks, null);
            }
        }
    }

    private Set<BlockPos> flood(ServerLevel level, BlockPos origin) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        found.add(origin);
        queue.add(origin);
        int limit = maxSize * maxSize * maxSize;
        while (!queue.isEmpty() && found.size() <= limit) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!found.contains(next) && level.isLoaded(next) && isCasing(level.getBlockState(next))) {
                    found.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    // A formed box of any size: its inclusive corners.
    public record Box(BlockPos min, BlockPos max) {
        public int size(Direction.Axis axis) {
            return max.get(axis) - min.get(axis) + 1;
        }

        public int volume() {
            return size(Direction.Axis.X) * size(Direction.Axis.Y) * size(Direction.Axis.Z);
        }

        public boolean isCube() {
            return volume() == BLOCKS;
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }

    private void apply(ServerLevel level, Set<BlockPos> blocks, @Nullable Vec3 viewer) {
        BlockPos center = centerOf(blocks);
        Box box = center == null && maxSize > SIZE ? boxOf(blocks) : null;
        if (center != null) {
            form(level, blocks, center, viewer);
        } else if (box != null) {
            formBox(level, blocks, box, viewer);
        } else {
            unform(level, blocks);
        }
    }

    // The box the set fills, if it is a solid box 3 to maxSize each way (and not 3x3x3).
    private @Nullable Box boxOf(Set<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }
        BlockPos min = null, max = null;
        for (BlockPos pos : blocks) {
            min = min == null ? pos : BlockPos.min(min, pos);
            max = max == null ? pos : BlockPos.max(max, pos);
        }
        Box box = new Box(min.immutable(), max.immutable());
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (box.size(axis) < SIZE || box.size(axis) > maxSize) {
                return null;
            }
        }
        return box.volume() == blocks.size() && !box.isCube() ? box : null;
    }

    private static void formBox(ServerLevel level, Set<BlockPos> blocks, Box box, @Nullable Vec3 viewer) {
        BlockPos master = box.min();
        // A cube's centre, if a 3x3x3 is growing into this box: its contents move to the box's master.
        BlockPos oldCentre = null;
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            if (!pos.equals(master) && state.getValue(CubeCasingBlock.PART) == Part.CENTER) {
                oldCentre = pos;
                break;
            }
        }
        BlockState masterState = level.getBlockState(master);
        boolean wasFormed = masterState.getValue(CubeCasingBlock.PART) == Part.CENTER && CubeCasingBlock.isBox(masterState)
                && level.getBlockEntity(master) instanceof CubeMultiblockBlockEntity before && box.equals(before.getBox());
        Vec3 middle = new Vec3((box.min().getX() + box.max().getX() + 1) / 2.0, 0.0, (box.min().getZ() + box.max().getZ() + 1) / 2.0);
        Direction facing = viewer != null ? Direction.getApproximateNearest(viewer.x - middle.x, 0.0, viewer.z - middle.z)
                : masterState.getValue(CubeCasingBlock.PART) == Part.CENTER ? masterState.getValue(CubeCasingBlock.FACING) : Direction.NORTH;
        if (facing.getAxis().isVertical()) {
            facing = Direction.NORTH;
        }
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            BlockState formed = (pos.equals(master)
                    ? state.setValue(CubeCasingBlock.PART, Part.CENTER).setValue(CubeCasingBlock.FACING, facing)
                    : state.setValue(CubeCasingBlock.PART, Part.OTHER).setValue(CubeCasingBlock.LIT, false))
                    .setValue(CubeCasingBlock.BOX, true);
            if (formed != state) {
                level.setBlock(pos, formed, Block.UPDATE_ALL);
            }
            if (level.getBlockEntity(pos) instanceof CubeMultiblockBlockEntity part) {
                part.setBox(box);
            }
        }
        if (oldCentre != null && level.getBlockEntity(oldCentre) instanceof CubeMultiblockBlockEntity from
                && level.getBlockEntity(master) instanceof CubeMultiblockBlockEntity to) {
            from.moveContentsTo(to);
        }
        MultiblockAutomation.refresh(level, box.min(), box.max());
        if (!wasFormed) {
            MultiblockEffects.formed(level, box.min(), box.max());
            if (level.getBlockEntity(master) instanceof MultiblockController controller) {
                ArcforgeAdvancements.formed(level, controller, null);
            }
        }
    }

    // The centre of the set if it is exactly a 3x3x3 cube.
    private static @Nullable BlockPos centerOf(Set<BlockPos> blocks) {
        if (blocks.size() != BLOCKS) {
            return null;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        // 27 distinct positions inside a 3x3x3 box fill it.
        if (maxX - minX + 1 != SIZE || maxY - minY + 1 != SIZE || maxZ - minZ + 1 != SIZE) {
            return null;
        }
        return new BlockPos(minX + 1, minY + 1, minZ + 1);
    }

    private static void form(ServerLevel level, Set<BlockPos> blocks, BlockPos center, @Nullable Vec3 viewer) {
        BlockState centerState = level.getBlockState(center);
        boolean wasFormed = centerState.getValue(CubeCasingBlock.PART) == Part.CENTER && !CubeCasingBlock.isBox(centerState);
        Direction facing = viewer != null
                ? Direction.getApproximateNearest(viewer.x - (center.getX() + 0.5), 0.0, viewer.z - (center.getZ() + 0.5))
                : wasFormed ? centerState.getValue(CubeCasingBlock.FACING) : Direction.NORTH;
        if (facing.getAxis().isVertical()) {
            facing = Direction.NORTH;
        }
        boolean changed = !wasFormed;
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            BlockState formed = CubeCasingBlock.withoutBox(pos.equals(center)
                    ? state.setValue(CubeCasingBlock.PART, Part.CENTER).setValue(CubeCasingBlock.FACING, facing)
                    : state.setValue(CubeCasingBlock.PART, Part.OTHER).setValue(CubeCasingBlock.LIT, false));
            if (level.getBlockEntity(pos) instanceof CubeMultiblockBlockEntity part) {
                part.setBox(null);
            }
            if (formed != state) {
                level.setBlock(pos, formed, Block.UPDATE_ALL);
                changed |= pos.equals(center) || state.getValue(CubeCasingBlock.PART) == Part.NONE;
            }
        }
        BlockPos min = center.offset(-1, -1, -1);
        BlockPos max = center.offset(1, 1, 1);
        MultiblockAutomation.refresh(level, min, max);
        if (changed && !wasFormed) {
            MultiblockEffects.formed(level, min, max);
            if (level.getBlockEntity(center) instanceof MultiblockController controller) {
                ArcforgeAdvancements.formed(level, controller, null);
            }
        }
    }

    private static void unform(ServerLevel level, Set<BlockPos> blocks) {
        BlockPos min = null;
        BlockPos max = null;
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            BlockState loose = CubeCasingBlock.withoutBox(state.setValue(CubeCasingBlock.PART, Part.NONE).setValue(CubeCasingBlock.LIT, false));
            if (level.getBlockEntity(pos) instanceof CubeMultiblockBlockEntity part) {
                part.setBox(null);
            }
            if (loose != state) {
                level.setBlock(pos, loose, Block.UPDATE_ALL);
                min = min == null ? pos : BlockPos.min(min, pos);
                max = max == null ? pos : BlockPos.max(max, pos);
            }
        }
        if (min != null) {
            MultiblockAutomation.refresh(level, min, max);
        }
    }
}
