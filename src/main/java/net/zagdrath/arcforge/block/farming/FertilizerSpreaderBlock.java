/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.farming.FertilizerSpreaderBlockEntity;
import net.zagdrath.arcforge.item.farming.FertilizerItem;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Fertilizer Spreader (see FertilizerSpreaderBlockEntity). Use it holding fertilizer to put the stack in; sneak-use
// with an empty hand to take it all back.
public class FertilizerSpreaderBlock extends BaseEntityBlock {
    // The hopper (y 10-16) and its throat over four legs, and the spinner disc.
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 10, 0, 16, 16, 16),
            Block.box(4, 8, 4, 12, 10, 12),
            Block.box(3, 3, 3, 13, 5, 13),
            Block.box(1, 0, 1, 3, 10, 3), Block.box(13, 0, 1, 15, 10, 3),
            Block.box(1, 0, 13, 3, 10, 15), Block.box(13, 0, 13, 15, 10, 15));

    public FertilizerSpreaderBlock(BlockBehaviour.Properties properties) {
        super(properties);
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
        if (!(stack.getItem() instanceof FertilizerItem)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FertilizerSpreaderBlockEntity spreader) {
            int inserted = spreader.insertByHand(stack);
            if (inserted == 0) {
                player.sendOverlayMessage(Component.translatable("message.arcforge.fertilizer_spreader.full"));
            } else if (!player.hasInfiniteMaterials()) {
                stack.shrink(inserted);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FertilizerSpreaderBlockEntity spreader && !spreader.giveAll(level, player)) {
            player.sendOverlayMessage(Component.translatable("message.arcforge.fertilizer_spreader.empty"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FertilizerSpreaderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel ? createTickerHelper(type, ModBlockEntityTypes.FERTILIZER_SPREADER.get(),
                (l, pos, s, spreader) -> spreader.serverTick(serverLevel, pos, s)) : null;
    }
}
