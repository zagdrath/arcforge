/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.logistics;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.logistics.MercuryVapourLampBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Mercury Vapour Lamp: a steel-caged glass bulb on a mounting plate, on a floor, a wall or a ceiling. FACING is the
// way the bulb points (away from the block it's mounted on, like an End Rod). It draws a little FE (from a conduit on any
// side) and while it has it gives light 15 (LIT); a redstone signal (POWERED) turns it off. The block it's mounted on has
// to hold it up, or it drops.
public class MercuryVapourLampBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    // Pointing up: the 10x10 mounting plate (2 px), the 8x8 lamp holder and cage, its cap. Turned for each facing.
    private static final Map<Direction, VoxelShape> SHAPES = shapes();

    public MercuryVapourLampBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(LIT, false).setValue(POWERED, false));
    }

    private static Map<Direction, VoxelShape> shapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.values()) {
            shapes.put(facing, Shapes.or(box(facing, 3, 3, 0, 13, 13, 2), box(facing, 4, 4, 2, 12, 12, 13)));
        }
        return shapes;
    }

    // A box given across (a, b) and out from the mounting face (h), for a lamp pointing that way.
    static VoxelShape box(Direction facing, double a0, double b0, double h0, double a1, double b1, double h1) {
        return switch (facing) {
            case UP -> Block.box(a0, h0, b0, a1, h1, b1);
            case DOWN -> Block.box(a0, 16 - h1, b0, a1, 16 - h0, b1);
            case SOUTH -> Block.box(a0, b0, h0, a1, b1, h1);
            case NORTH -> Block.box(a0, b0, 16 - h1, a1, b1, 16 - h0);
            case EAST -> Block.box(h0, b0, a0, h1, b1, a1);
            case WEST -> Block.box(16 - h1, b0, a0, 16 - h0, b1, a1);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, POWERED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    // The face it's mounted on must be solid enough to hang a lantern from.
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return Block.canSupportCenter(level, pos.relative(facing.getOpposite()), facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        boolean powered = level.hasNeighborSignal(pos);
        if (!level.isClientSide() && powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered).setValue(LIT, state.getValue(LIT) && !powered), Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MercuryVapourLampBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel ? createTickerHelper(type, ModBlockEntityTypes.MERCURY_VAPOUR_LAMP.get(),
                (tickLevel, pos, tickState, lamp) -> lamp.serverTick(serverLevel, pos, tickState)) : null;
    }
}
