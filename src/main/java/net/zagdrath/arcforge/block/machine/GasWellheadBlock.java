/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.block.multiblock.WellHeadBlock;
import net.zagdrath.arcforge.blockentity.machine.GasWellheadBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Gas Wellhead (see GasWellheadBlockEntity): fitted on top of a gas well (a Well Head drilled into a deposit's gas
// cap), a flanged casing head with a valve tree, a choke and a pressure gauge, and a flow line out of the side it faces
// to its Natural Gas port. It can only be placed on a gas well. Its shape follows its model
// (tools/GasWellheadModelGen.java).
public class GasWellheadBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    // The model as built facing north (the flow line out of the north face): casing head, valve tree with its wheel,
    // the choke body and the flow line.
    // The model's elements (tools/GasWellheadModelGen): casing head flange, spool, master valve, swab cap and stem, the
    // wing with its flow-line flange, the choke with its handwheel, and the pressure gauge.
    private static final VoxelShape NORTH = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(5, 2, 5, 11, 5, 11), Block.box(4, 5, 4, 12, 10, 12),
            Block.box(6, 10, 6, 10, 13, 10), Block.box(5, 13, 5, 11, 14, 11), Block.box(7, 14, 7, 9, 16, 9), Block.box(6, 6, 1, 10, 9, 4),
            Block.box(5, 5, 0, 11, 10, 1), Block.box(6, 9, 1, 10, 11, 4), Block.box(7, 11, 2, 9, 12, 3), Block.box(5, 12, 0, 11, 13, 5),
            Block.box(12, 7, 7, 13, 8, 9), Block.box(13, 5, 6, 15, 10, 10));
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            SHAPES[direction.get2DDataValue()] = rotate(NORTH, direction);
        }
    }

    public GasWellheadBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // The north-facing shape turned to face direction (about the block's middle).
    private static VoxelShape rotate(VoxelShape shape, Direction direction) {
        VoxelShape[] out = { Shapes.empty() };
        int turns = (direction.get2DDataValue() + 2) % 4;
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double ax = x0, az = z0, bx = x1, bz = z1;
            for (int i = 0; i < turns; i++) {
                double nax = 1 - bz, nbx = 1 - az, naz = ax, nbz = bx;
                ax = nax;
                bx = nbx;
                az = naz;
                bz = nbz;
            }
            out[0] = Shapes.or(out[0], Shapes.box(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz)));
        });
        return out[0];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // Only on a gas well, its flow line facing away from the player.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!WellHeadBlock.isGasWell(context.getLevel().getBlockState(context.getClickedPos().below()))) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GasWellheadBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.GAS_WELLHEAD.get(), (innerLevel, pos, blockState, wellhead) -> wellhead.serverTick(serverLevel))
                : null;
    }
}
