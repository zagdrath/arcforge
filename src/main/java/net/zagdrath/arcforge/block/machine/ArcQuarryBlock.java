/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.sound.MachineSounds;

// The Arc Quarry's main block: the centre of its 3x3x3, which draws the whole machine (the model spans -16..32). The
// other 26 positions are ArcQuarryBoundingBlocks that forward everything here. See ArcQuarryItem for placing it.
public class ArcQuarryBlock extends MachineBlock {
    private static final MapCodec<ArcQuarryBlock> CODEC = simpleCodec(ArcQuarryBlock::new);

    @Override
    protected MapCodec<ArcQuarryBlock> codec() {
        return CODEC;
    }

    // The model's boxes (block/arc_quarry/model.json, in pixels from the main block's corner, facing north), and the
    // arc emitter's reach as it spins. The whole machine's shape is built from these, so it can be hit and stood on
    // like the model rather than as a 3x3x3 cube.
    private static final int[][] BOXES = {
            { -12, -16, -12, 28, -13, 28 }, { -11, -13, -11, 27, -12, 27 }, { -9, -12, -9, 25, -1, 25 }, { -8, -1, -8, 24, 1, 24 },
            { -6, -10, -10, 10, -3, -9 },
            { -12, -13, -12, -7, -5, -7 }, { -12, -13, 23, -7, -5, 28 }, { 23, -13, -12, 28, -5, -7 }, { 23, -13, 23, 28, -5, 28 },
            { -4, 1, -4, 20, 21, 20 }, { -5, 6, -5, 21, 8, 21 }, { -5, 14, -5, 21, 16, 21 },
            { -8, 1, -8, -4, 12, -4 }, { 20, 1, -8, 24, 12, -4 }, { -8, 1, 20, -4, 12, 24 }, { 20, 1, 20, 24, 12, 24 },
            { -6, 21, -6, 22, 27, 22 }, { -7, 23, -7, 23, 25, 23 }, { 3, 27, 3, 13, 29, 13 },
            { 2, 28, 2, 14, 32, 14 } };
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    // Per facing and part: the whole shape seen from that part (outline and hits), and just the piece inside it
    // (collision).
    private static final Map<Long, VoxelShape> PART_SHAPES = new ConcurrentHashMap<>();
    private static final Map<Long, VoxelShape> PART_COLLISIONS = new ConcurrentHashMap<>();

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (int[] box : BOXES) {
                shape = Shapes.or(shape, rotated(box, facing));
            }
            SHAPES.put(facing, shape.optimize());
        }
    }

    // A north-facing box turned to face `facing` about the block's centre, as the blockstate turns the model.
    private static VoxelShape rotated(int[] box, Direction facing) {
        double x0 = box[0], z0 = box[2], x1 = box[3], z1 = box[5];
        for (int turns = (facing.get2DDataValue() + 2) % 4; turns > 0; turns--) {
            // 90° clockwise seen from above: (x, z) -> (16 - z, x).
            double nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
            x0 = nx0;
            x1 = nx1;
            z0 = nz0;
            z1 = nz1;
        }
        return Shapes.box(x0 / 16.0, box[1] / 16.0, z0 / 16.0, x1 / 16.0, box[4] / 16.0, z1 / 16.0);
    }

    // The whole machine's shape from the main block.
    public static VoxelShape shape(Direction facing) {
        return SHAPES.getOrDefault(facing, Shapes.block());
    }

    private static long key(Direction facing, BlockPos offset) {
        return facing.get2DDataValue() * 1000L + (offset.getX() + 1) * 100L + (offset.getY() + 1) * 10L + offset.getZ() + 1;
    }

    // The whole machine's shape seen from the part at `offset` from the main block.
    public static VoxelShape partShape(Direction facing, BlockPos offset) {
        return PART_SHAPES.computeIfAbsent(key(facing, offset), k -> shape(facing).move(-offset.getX(), -offset.getY(), -offset.getZ()));
    }

    // Only the piece of the machine inside the part at `offset` (what entities bump into there).
    public static VoxelShape partCollision(Direction facing, BlockPos offset) {
        return PART_COLLISIONS.computeIfAbsent(key(facing, offset),
                k -> Shapes.join(partShape(facing, offset), Shapes.block(), BooleanOp.AND).optimize());
    }

    public ArcQuarryBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return partCollision(state.getValue(FACING), BlockPos.ZERO);
    }

    // The 27 positions of a quarry centred on `main`.
    public static List<BlockPos> positions(BlockPos main) {
        List<BlockPos> positions = new ArrayList<>(27);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    positions.add(main.offset(dx, dy, dz));
                }
            }
        }
        return positions;
    }

    // Whether pos is one of the quarry's own positions.
    public static boolean isPart(BlockPos main, BlockPos pos) {
        return Math.abs(pos.getX() - main.getX()) <= 1 && Math.abs(pos.getY() - main.getY()) <= 1 && Math.abs(pos.getZ() - main.getZ()) <= 1;
    }

    // Clears the 26 bounding blocks around a main block that is being removed (nothing drops from them).
    public static void removeBoundingBlocks(Level level, BlockPos main) {
        for (BlockPos pos : positions(main)) {
            if (!pos.equals(main) && level.getBlockState(pos).getBlock() instanceof ArcQuarryBoundingBlock) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcQuarryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.ARC_QUARRY.get(),
                        (innerLevel, pos, blockState, quarry) -> quarry.serverTick(serverLevel, pos, blockState))
                : createTickerHelper(type, ModBlockEntityTypes.ARC_QUARRY.get(), MachineSounds.clientTicker());
    }
}
