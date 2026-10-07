/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.farming.MillstoneBlockEntity;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Millstone (see MillstoneBlockEntity): two stones on a treated-wood frame with a chute at the front (FACING, towards
// the player who placed it). TURN steps the top stone and its handle a quarter round each turn.
//  - Use it holding something millable to put the stack in.
//  - Use it with an empty hand to turn the stone.
//  - Sneak-use with an empty hand to take the results (or, with none, the input back).
// A redstone pulse turns it too, so a clock or an observer can run it.
public class MillstoneBlock extends BaseEntityBlock {
    private static final MapCodec<MillstoneBlock> CODEC = simpleCodec(MillstoneBlock::new);

    @Override
    protected MapCodec<MillstoneBlock> codec() {
        return CODEC;
    }

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty TURN = IntegerProperty.create("turn", 0, 3);
    // The frame (y 0-6) and the stones (x/z 2-14, y 6-12), with the handle's peg.
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 6, 16), Block.box(2, 6, 2, 14, 12, 14));

    public MillstoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TURN, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TURN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !MachineRecipes.isMillingInput(level, stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MillstoneBlockEntity millstone) {
            int inserted = millstone.insertByHand(stack);
            if (inserted == 0) {
                player.sendOverlayMessage(Component.translatable("message.arcforge.millstone.full"));
            } else if (!player.hasInfiniteMaterials()) {
                stack.shrink(inserted);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof MillstoneBlockEntity millstone)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            if (!millstone.takeByHand(level, player)) {
                player.sendOverlayMessage(Component.translatable("message.arcforge.millstone.empty"));
            }
        } else if (millstone.getItems().getStack(MillstoneBlockEntity.SLOT_INPUT).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.arcforge.millstone.nothing"));
        } else {
            millstone.turnByHand(serverLevel);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MillstoneBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel ? createTickerHelper(type, ModBlockEntityTypes.MILLSTONE.get(),
                (l, pos, s, millstone) -> millstone.serverTick(serverLevel, pos, s)) : null;
    }
}
