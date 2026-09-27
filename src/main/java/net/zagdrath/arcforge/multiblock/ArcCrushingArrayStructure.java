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
import net.zagdrath.arcforge.block.multiblock.ArcCrushingArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.ArcCrushingArrayCasingBlock.Part;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;

// The Arc Crushing Array: 27 casings as a solid 3x3x3 cube. When formed, the centre block becomes
// part=center and draws the whole machine (and its block entity runs it); the other 26 become
// part=other and draw nothing. The front faces the player who completed it.
public final class ArcCrushingArrayStructure {
    public static final int SIZE = 3;
    private static final int BLOCKS = SIZE * SIZE * SIZE;

    private ArcCrushingArrayStructure() {}

    private static boolean isCasing(BlockState state) {
        return state.getBlock() instanceof ArcCrushingArrayCasingBlock;
    }

    // The centre block of the formed structure the casing at pos belongs to, or null.
    public static @Nullable BlockPos findCenter(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isCasing(state) || state.getValue(ArcCrushingArrayCasingBlock.PART) == Part.NONE) {
            return null;
        }
        if (state.getValue(ArcCrushingArrayCasingBlock.PART) == Part.CENTER) {
            return pos;
        }
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            BlockState other = level.getBlockState(candidate);
            if (isCasing(other) && other.getValue(ArcCrushingArrayCasingBlock.PART) == Part.CENTER) {
                return candidate.immutable();
            }
        }
        return null;
    }

    public static @Nullable ArcCrushingArrayBlockEntity findController(BlockGetter level, BlockPos pos) {
        BlockPos center = findCenter(level, pos);
        return center != null && level.getBlockEntity(center) instanceof ArcCrushingArrayBlockEntity array ? array : null;
    }

    // Re-evaluates the casings connected to origin. viewer: the player completing it, so the front faces
    // them; null keeps the current facing.
    public static void rebuild(ServerLevel level, BlockPos origin, @Nullable Vec3 viewer) {
        if (isCasing(level.getBlockState(origin))) {
            apply(level, flood(level, origin), viewer);
        }
    }

    // After a casing is removed: re-evaluates everything that touched it.
    public static void rebuildAround(ServerLevel level, BlockPos removed) {
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(removed.offset(-1, -1, -1), removed.offset(1, 1, 1))) {
            if (!done.contains(pos) && isCasing(level.getBlockState(pos))) {
                Set<BlockPos> blocks = flood(level, pos.immutable());
                done.addAll(blocks);
                apply(level, blocks, null);
            }
        }
    }

    private static Set<BlockPos> flood(ServerLevel level, BlockPos origin) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        found.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && found.size() <= BLOCKS) {
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

    private static void apply(ServerLevel level, Set<BlockPos> blocks, @Nullable Vec3 viewer) {
        BlockPos center = centerOf(blocks);
        if (center != null) {
            form(level, blocks, center, viewer);
        } else {
            unform(level, blocks);
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
        boolean wasFormed = centerState.getValue(ArcCrushingArrayCasingBlock.PART) == Part.CENTER;
        Direction facing = viewer != null
                ? Direction.getApproximateNearest(viewer.x - (center.getX() + 0.5), 0.0, viewer.z - (center.getZ() + 0.5))
                : wasFormed ? centerState.getValue(ArcCrushingArrayCasingBlock.FACING) : Direction.NORTH;
        if (facing.getAxis().isVertical()) {
            facing = Direction.NORTH;
        }
        boolean changed = !wasFormed;
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            BlockState formed = pos.equals(center)
                    ? state.setValue(ArcCrushingArrayCasingBlock.PART, Part.CENTER).setValue(ArcCrushingArrayCasingBlock.FACING, facing)
                    : state.setValue(ArcCrushingArrayCasingBlock.PART, Part.OTHER).setValue(ArcCrushingArrayCasingBlock.LIT, false);
            if (formed != state) {
                level.setBlock(pos, formed, Block.UPDATE_ALL);
                changed |= pos.equals(center) || state.getValue(ArcCrushingArrayCasingBlock.PART) == Part.NONE;
            }
        }
        BlockPos min = center.offset(-1, -1, -1);
        BlockPos max = center.offset(1, 1, 1);
        MultiblockAutomation.refresh(level, min, max);
        if (changed && !wasFormed) {
            MultiblockEffects.formed(level, min, max);
        }
    }

    private static void unform(ServerLevel level, Set<BlockPos> blocks) {
        BlockPos min = null;
        BlockPos max = null;
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            BlockState loose = state.setValue(ArcCrushingArrayCasingBlock.PART, Part.NONE).setValue(ArcCrushingArrayCasingBlock.LIT, false);
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
