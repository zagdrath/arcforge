/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// Carbonizer formation. Connected carbonizer blocks form a structure when they fill exactly a box N wide
// (1..maxSlices) x 2 tall x 2 deep, all facing the same way, with the depth running along the facing.
// Slice i is row column i, counted from the left end (facing.getCounterClockWise(), model -X). The
// master is the left, bottom, front block; it runs the structure and holds its inventory.
public final class CarbonizerStructure {
    // Stop flood-filling past this; anything this big is invalid anyway.
    private static final int SEARCH_LIMIT = 256;

    public record Formation(Direction facing, BlockPos min, BlockPos max, int slices, BlockPos master) {
        public static final Codec<Formation> CODEC = RecordCodecBuilder.create(i -> i.group(
                Direction.CODEC.fieldOf("facing").forGetter(Formation::facing),
                BlockPos.CODEC.fieldOf("min").forGetter(Formation::min),
                BlockPos.CODEC.fieldOf("max").forGetter(Formation::max),
                Codec.INT.fieldOf("slices").forGetter(Formation::slices),
                BlockPos.CODEC.fieldOf("master").forGetter(Formation::master))
                .apply(i, Formation::new));

        // Blocks along the row run from the master towards the facing's clockwise side.
        public Direction rowDirection() {
            return facing.getClockWise();
        }

        // The four blocks of slice i: front and back, bottom and top.
        public BlockPos[] slice(int index) {
            BlockPos frontBottom = master.relative(rowDirection(), index);
            BlockPos backBottom = frontBottom.relative(facing.getOpposite());
            return new BlockPos[] { frontBottom, frontBottom.above(), backBottom, backBottom.above() };
        }
    }

    private CarbonizerStructure() {}

    // Re-evaluates the structure the carbonizer at origin belongs to.
    public static void rebuild(ServerLevel level, BlockPos origin) {
        if (level.getBlockState(origin).getBlock() instanceof CarbonizerBlock) {
            apply(level, flood(level, origin));
        }
    }

    // After a carbonizer is removed: re-evaluates every structure that touched it.
    public static void rebuildAround(ServerLevel level, BlockPos removed) {
        Set<BlockPos> done = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = removed.relative(direction);
            if (!done.contains(neighbour) && level.getBlockState(neighbour).getBlock() instanceof CarbonizerBlock) {
                Set<BlockPos> blocks = flood(level, neighbour);
                done.addAll(blocks);
                apply(level, blocks);
            }
        }
    }

    private static Set<BlockPos> flood(ServerLevel level, BlockPos origin) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        found.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && found.size() < SEARCH_LIMIT) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!found.contains(next) && level.isLoaded(next) && level.getBlockState(next).getBlock() instanceof CarbonizerBlock) {
                    found.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    private static void apply(ServerLevel level, Set<BlockPos> blocks) {
        Formation formation = validate(level, blocks);
        if (formation != null) {
            form(level, blocks, formation);
        } else {
            unform(level, blocks);
        }
    }

    public static @Nullable Formation validate(ServerLevel level, Set<BlockPos> blocks) {
        int maxSlices = ArcforgeConfig.CARBONIZER_MAX_SLICES.getAsInt();
        if (blocks.isEmpty() || blocks.size() % 4 != 0 || blocks.size() > maxSlices * 4) {
            return null;
        }

        Direction facing = null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            Direction blockFacing = level.getBlockState(pos).getValue(CarbonizerBlock.FACING);
            if (facing == null) {
                facing = blockFacing;
            } else if (facing != blockFacing) {
                return null;
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        int depth = facing.getAxis() == Direction.Axis.X ? sizeX : sizeZ;
        int width = facing.getAxis() == Direction.Axis.X ? sizeZ : sizeX;
        // Distinct positions filling the whole box's volume means the box is solid.
        if (maxY - minY + 1 != 2 || depth != 2 || width < 1 || width > maxSlices || width * 4 != blocks.size()) {
            return null;
        }

        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);
        // Master: the left end of the row, bottom layer, front row.
        Direction row = facing.getClockWise();
        int masterX = row.getStepX() > 0 ? minX : row.getStepX() < 0 ? maxX : facing.getStepX() > 0 ? maxX : minX;
        int masterZ = row.getStepZ() > 0 ? minZ : row.getStepZ() < 0 ? maxZ : facing.getStepZ() > 0 ? maxZ : minZ;
        return new Formation(facing, min, max, width, new BlockPos(masterX, minY, masterZ));
    }

    private static void form(ServerLevel level, Set<BlockPos> blocks, Formation formation) {
        Direction row = formation.rowDirection();
        Direction facing = formation.facing();
        BlockPos master = formation.master();
        // Rechecking a structure that's already built this way (e.g. after a wrench click) isn't news.
        boolean unchanged = level.getBlockEntity(master) instanceof CarbonizerBlockEntity current
                && current.isFormed() && current.getSlices() == formation.slices();
        for (BlockPos pos : blocks) {
            int index = (pos.getX() - master.getX()) * row.getStepX() + (pos.getZ() - master.getZ()) * row.getStepZ();
            boolean front = (pos.getX() - master.getX()) * facing.getStepX() + (pos.getZ() - master.getZ()) * facing.getStepZ() == 0;
            BlockState state = level.getBlockState(pos);
            BlockState formed = state
                    .setValue(CarbonizerBlock.ROW, CarbonizerBlock.Row.of(index, formation.slices()))
                    .setValue(CarbonizerBlock.HALF, pos.getY() == formation.max().getY() ? CarbonizerBlock.Half.TOP : CarbonizerBlock.Half.BOTTOM)
                    .setValue(CarbonizerBlock.DEPTH, front ? CarbonizerBlock.Depth.FRONT : CarbonizerBlock.Depth.BACK);
            if (formed != state) {
                level.setBlock(pos, formed, Block.UPDATE_ALL);
            }
        }

        if (!(level.getBlockEntity(master) instanceof CarbonizerBlockEntity controller)) {
            return;
        }
        for (BlockPos pos : blocks) {
            if (level.getBlockEntity(pos) instanceof CarbonizerBlockEntity part && part != controller) {
                part.setMaster(master);
                // A block that used to be a master (e.g. the structure was extended to its left) hands its contents over.
                controller.absorb(part);
            }
        }
        controller.becomeMaster(formation);
        MultiblockAutomation.refresh(level, formation.min(), formation.max());
        if (!unchanged) {
            MultiblockEffects.formed(level, formation.min(), formation.max());
        }
    }

    private static void unform(ServerLevel level, Set<BlockPos> blocks) {
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            BlockState loose = state.setValue(CarbonizerBlock.ROW, CarbonizerBlock.Row.NONE).setValue(CarbonizerBlock.LIT, false);
            if (loose != state) {
                level.setBlock(pos, loose, Block.UPDATE_ALL);
            }
            if (level.getBlockEntity(pos) instanceof CarbonizerBlockEntity part) {
                part.clearMaster();
            }
            level.invalidateCapabilities(pos);
            ConduitBlock.refreshAround(level, pos);
        }
    }
}
