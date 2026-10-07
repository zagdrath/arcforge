/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.redstone;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.zagdrath.arcforge.blockentity.redstone.ThrottleLeverBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// The Throttle Lever: an aircraft-style throttle quadrant with a redstone signal of 0-15, placed on floors, walls and
// ceilings like a vanilla lever. Scroll the mouse wheel while looking at it (ThrottleLeverClient) or right-click to
// raise the signal by 1 and sneak + right-click to lower it, stopping at 0 and 15. Like a lever it powers the block
// it's attached to strongly and its other neighbours weakly, so it can set a Gas Turbine Array's Throttle mode
// directly. The housing (with its LED gauge) comes from the blockstate model; the arm pivots on the quadrant's axle,
// drawn by ThrottleLeverRenderer.
public class ThrottleLeverBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<ThrottleLeverBlock> CODEC = simpleCodec(ThrottleLeverBlock::new);

    @Override
    protected MapCodec<ThrottleLeverBlock> codec() {
        return CODEC;
    }

    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    private final Function<BlockState, VoxelShape> shapes;

    public ThrottleLeverBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWER, 0).setValue(FACE, AttachFace.WALL));
        shapes = makeShapes();
    }

    // The 10 x 14 quadrant and the arm's full sweep (up to 14 px from the mounting face), turned for each face and
    // facing as a lever's shape is.
    private Function<BlockState, VoxelShape> makeShapes() {
        Map<AttachFace, Map<Direction, VoxelShape>> byFace = Shapes.rotateAttachFace(Block.boxZ(10.0, 14.0, 2.0, 16.0));
        return getShapeForEachState(state -> byFace.get(state.getValue(FACE)).get(state.getValue(FACING)), POWER);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, POWER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.apply(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            step(level, pos, state, player.isShiftKeyDown() ? -1 : 1, player);
        }
        return InteractionResult.SUCCESS;
    }

    // Moves the throttle by delta notches, stopping at 0 and 15 (right-click, and the mouse wheel through
    // ThrottleLeverPayload). The signal shows in the popup under the crosshair (ThrottleLeverClient).
    public static void step(Level level, BlockPos pos, BlockState state, int delta, @Nullable Player player) {
        if (!(state.getBlock() instanceof ThrottleLeverBlock block)) {
            return;
        }
        int power = state.getValue(POWER);
        int next = Math.clamp(power + delta, 0, 15);
        if (next != power) {
            block.setPower(state, level, pos, next, player);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThrottleLeverBlockEntity(pos, state);
    }

    // Client only: eases the arm toward the angle for the current signal.
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() && type == ModBlockEntityTypes.THROTTLE_LEVER.get()
                ? (l, p, s, be) -> ((ThrottleLeverBlockEntity) be).clientTick(s)
                : null;
    }

    // Sets the signal and tells the neighbours, with a click that rises in pitch with the level.
    public void setPower(BlockState state, Level level, BlockPos pos, int power, @Nullable Player player) {
        BlockState after = state.setValue(POWER, power);
        level.setBlockAndUpdate(pos, after);
        updateNeighbours(after, level, pos);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, 0.5F + power / 30.0F);
        level.gameEvent(player, power > 0 ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston && state.getValue(POWER) > 0) {
            updateNeighbours(state, level, pos);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getConnectedDirection(state) == direction ? state.getValue(POWER) : 0;
    }

    // As LeverBlock: tell the neighbours and the block it's attached to (and that block's neighbours).
    private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
        Direction front = getConnectedDirection(state).getOpposite();
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, front,
                front.getAxis().isHorizontal() ? Direction.UP : state.getValue(FACING));
        level.updateNeighborsAt(pos, this, orientation);
        level.updateNeighborsAt(pos.relative(front), this, orientation);
    }
}
