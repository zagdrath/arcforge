/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

// A natural stone pillar that joins with the same pillar lying on the same axis, so a stack reads as one column: its
// capital only on the end block at one end, its base only at the other, a plain shaft between (the blockstate picks
// the side texture from these). UP: the same pillar continues past this block's positive end along its axis (above it
// for a standing pillar, east for X, south for Z); DOWN: past its negative end.
public class StonePillarBlock extends RotatedPillarBlock {
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    public StonePillarBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : joined(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        Direction.Axis axis = state.getValue(AXIS);
        if (direction.getAxis() == axis) {
            return state.setValue(direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? UP : DOWN, joins(neighborState, axis));
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    // Rotating the block can swap which way its axis points, so the joins are worked out again where it stands.
    private BlockState joined(BlockState state, LevelReader level, BlockPos pos) {
        Direction.Axis axis = state.getValue(AXIS);
        Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return state.setValue(UP, joins(level.getBlockState(pos.relative(positive)), axis))
                .setValue(DOWN, joins(level.getBlockState(pos.relative(positive.getOpposite())), axis));
    }

    private boolean joins(BlockState other, Direction.Axis axis) {
        return other.is(this) && other.getValue(AXIS) == axis;
    }
}
