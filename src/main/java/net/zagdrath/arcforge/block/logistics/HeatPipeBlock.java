/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.logistics;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.logistics.HeatPipeBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Heat Pipe: a thick insulated pipe along AXIS. Heat Pipes in a straight line along the same axis form pipes of up to
// heatPipe.maxLength blocks, counted from the line's negative end (a longer line is split, and the pieces don't pass heat
// to each other). NEG_END and POS_END mark a pipe's two end blocks, which carry the flanged end caps; the pipe joins the
// heat port or heat face beyond each end and nothing along its sides. LIT is set while it carries heat (see
// HeatPipeBlockEntity, whose negative end runs the pipe).
public class HeatPipeBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty NEG_END = BooleanProperty.create("neg_end");
    public static final BooleanProperty POS_END = BooleanProperty.create("pos_end");
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    // How far a line is followed when working out its pipes.
    private static final int SEARCH_LIMIT = 4_096;
    // The 12x12 jacket, with full-size flanged caps at a pipe's ends: a full block, for simplicity.
    private static final VoxelShape SHAPE = Shapes.block();

    public HeatPipeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(NEG_END, true).setValue(POS_END, true)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, NEG_END, POS_END, LIT);
    }

    // Along the face it was placed against (like a log), so it runs out from the port it's put on.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction.Axis axis = state.getValue(AXIS);
        if (axis != Direction.Axis.Y && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
            return state.setValue(AXIS, axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // Whether this is the end of a pipe facing out through side (where it joins a port).
    public static boolean isEndFacing(BlockState state, Direction side) {
        if (!(state.getBlock() instanceof HeatPipeBlock) || side.getAxis() != state.getValue(AXIS)) {
            return false;
        }
        return state.getValue(side.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? NEG_END : POS_END);
    }

    // --- Working out the pipes ---

    // Placed (not just changed state): its line is worked out again next tick, once it's in place.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && !level.isClientSide()) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockState(pos).is(this)) {
            update(level, pos, state.getValue(AXIS));
        }
    }

    // Removed: what's left of the line on either side is worked out again.
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this)) {
            Direction.Axis axis = state.getValue(AXIS);
            for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
                BlockPos next = pos.relative(Direction.fromAxisAndDirection(axis, sign));
                if (isPipe(level.getBlockState(next), axis)) {
                    update(level, next, axis);
                }
            }
        }
    }

    private static boolean isPipe(BlockState state, Direction.Axis axis) {
        return state.getBlock() instanceof HeatPipeBlock && state.getValue(AXIS) == axis;
    }

    // Sets NEG_END and POS_END along the whole straight line through pos: pipes of maxLength from the line's negative end.
    public static void update(Level level, BlockPos pos, Direction.Axis axis) {
        Direction back = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        Direction forward = back.getOpposite();
        BlockPos start = pos;
        for (int i = 0; i < SEARCH_LIMIT && level.isLoaded(start.relative(back)) && isPipe(level.getBlockState(start.relative(back)), axis); i++) {
            start = start.relative(back);
        }
        int length = 1;
        while (length < SEARCH_LIMIT && level.isLoaded(start.relative(forward, length)) && isPipe(level.getBlockState(start.relative(forward, length)), axis)) {
            length++;
        }
        int max = Math.max(1, ArcforgeConfig.HEAT_PIPE_MAX_LENGTH.getAsInt());
        for (int i = 0; i < length; i++) {
            BlockPos at = start.relative(forward, i);
            BlockState state = level.getBlockState(at);
            boolean neg = i % max == 0;
            boolean posEnd = i % max == max - 1 || i == length - 1;
            BlockState next = state.setValue(NEG_END, neg).setValue(POS_END, posEnd);
            if (next != state) {
                level.setBlock(at, next, Block.UPDATE_CLIENTS);
            }
            if (level.getBlockEntity(at) instanceof HeatPipeBlockEntity pipe) {
                pipe.structureChanged();
            }
        }
    }

    // --- Block entity ---

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeatPipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Every block ticks; only a pipe's negative end does anything (its role can change without a new block entity).
        return level instanceof ServerLevel serverLevel ? createTickerHelper(type, ModBlockEntityTypes.HEAT_PIPE.get(),
                (tickLevel, pos, tickState, pipe) -> pipe.serverTick(serverLevel, pos, tickState)) : null;
    }
}
