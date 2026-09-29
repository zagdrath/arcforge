/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.redstone;

import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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

// The Throttle Lever: a lever with a redstone signal of 0-15, placed on floors, walls and ceilings like a vanilla
// lever. Right-click raises the signal by 1 and sneak + right-click lowers it, stopping at 0 and 15. Like a lever it
// powers the block it's attached to strongly and its other neighbours weakly, so it can set a Gas Turbine Array's
// Throttle mode directly.
public class ThrottleLeverBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    private final Function<BlockState, VoxelShape> shapes;

    public ThrottleLeverBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWER, 0).setValue(FACE, AttachFace.WALL));
        shapes = makeShapes();
    }

    // The 10 x 14 plate and the grip, 9 px deep, turned for each face and facing as a lever's shape is.
    private Function<BlockState, VoxelShape> makeShapes() {
        Map<AttachFace, Map<Direction, VoxelShape>> byFace = Shapes.rotateAttachFace(Block.boxZ(10.0, 14.0, 7.0, 16.0));
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
            int power = state.getValue(POWER);
            int next = Math.clamp(power + (player.isShiftKeyDown() ? -1 : 1), 0, 15);
            if (next != power) {
                setPower(state, level, pos, next, player);
            }
            player.sendOverlayMessage(Component.translatable("block.arcforge.throttle_lever.signal", next, Math.round(next * 100 / 15.0F)));
        }
        return InteractionResult.SUCCESS;
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
    protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
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
