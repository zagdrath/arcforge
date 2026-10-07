/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.farming.ResinTapBlockEntity;
import net.zagdrath.arcforge.machine.interaction.MachineInteractions;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Resin Tap (see ResinTapBlockEntity): a copper spout driven into the side of a log, dripping into a treated-wood cup
// on a bracket. It hangs on the log behind it (FACING points out, away from the log) and drops when that log goes. Placed
// only on the side of a log. CONTENT shows what's in the cup: nothing, Latex or Pine Resin. A bucket takes the Latex;
// an empty hand the Pine Resin. Hoppers under it and conduits empty it too.
public class ResinTapBlock extends BaseEntityBlock {
    private static final MapCodec<ResinTapBlock> CODEC = simpleCodec(ResinTapBlock::new);

    @Override
    protected MapCodec<ResinTapBlock> codec() {
        return CODEC;
    }

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Content> CONTENT = EnumProperty.create("content", Content.class);

    public enum Content implements StringRepresentable {
        EMPTY("empty"), LATEX("latex"), RESIN("resin");

        private final String name;

        Content(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    // Facing north (the log to the south): the bracket against the log, its shelf, the cup and the spout over it.
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(4, 0, 15, 12, 6, 16),
            Block.box(4, 0, 6, 12, 1, 15),
            Block.box(5, 1, 6, 11, 7, 12),
            Block.box(7, 8, 9, 9, 12, 16)));

    public ResinTapBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CONTENT, Content.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONTENT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // The log it hangs on.
    public static BlockPos logPos(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    public static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    // Only on the side of a log: it sticks out of the face clicked.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (!face.getAxis().isHorizontal()) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, face);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isLog(level.getBlockState(logPos(state, pos)));
    }

    // Drops when the log behind it goes.
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    // A bucket takes the Latex.
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = MachineInteractions.useFluidContainer(level, pos, player, hand);
        return result != null ? result : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    // An empty hand takes the Pine Resin.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ResinTapBlockEntity tap) || tap.getResin().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            tap.takeResin(level, player);
        }
        return InteractionResult.SUCCESS;
    }

    // A slow amber drip from the spout of a tap gathering Pine Resin (the tap itself decides whether the tree is alive).
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockState log = level.getBlockState(logPos(state, pos));
        if (random.nextInt(5) != 0 || !isLog(log) || ResinTapBlockEntity.dripsLatex(log)) {
            return;
        }
        Direction out = state.getValue(FACING);
        double x = pos.getX() + 0.5 + out.getStepX() * 0.0625, z = pos.getZ() + 0.5 + out.getStepZ() * 0.0625;
        level.addParticle(ParticleTypes.FALLING_HONEY, x, pos.getY() + 0.5, z, 0, 0, 0);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResinTapBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel ? createTickerHelper(type, ModBlockEntityTypes.RESIN_TAP.get(),
                (l, pos, s, tap) -> tap.serverTick(serverLevel, pos, s)) : null;
    }
}
