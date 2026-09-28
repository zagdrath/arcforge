/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBoundingBlockEntity;
import net.zagdrath.arcforge.registry.ModItems;

// One of the 26 invisible parts of an Arc Quarry around its main block. It can be hit and broken like the machine,
// and forwards everything to the main block: using it opens the quarry, breaking it breaks the whole quarry (the
// item drops once, from the main block), and its outer faces are the quarry's faces. It never drops anything
// itself, and removes itself if its main block has gone (a broken edit).
public class ArcQuarryBoundingBlock extends BaseEntityBlock {
    public ArcQuarryBoundingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcQuarryBoundingBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // The main block this part belongs to, or null.
    public static @Nullable BlockPos mainOf(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ArcQuarryBoundingBlockEntity part ? part.mainPos() : null;
    }

    // The quarry this part belongs to, or null.
    public static @Nullable ArcQuarryBlockEntity quarryOf(BlockGetter level, BlockPos pos) {
        BlockPos main = mainOf(level, pos);
        return main != null && level.getBlockEntity(main) instanceof ArcQuarryBlockEntity quarry ? quarry : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        ArcQuarryBlockEntity quarry = quarryOf(level, pos);
        if (quarry == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(quarry, quarry.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // As hard to break as the machine.
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockPos main = mainOf(level, pos);
        return main != null ? level.getBlockState(main).getDestroyProgress(player, level, main) : super.getDestroyProgress(state, player, level, pos);
    }

    // A player breaking a part breaks the machine: its item drops once from the main block (in survival).
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos main = mainOf(level, pos);
        if (!level.isClientSide() && main != null && level.getBlockState(main).getBlock() instanceof ArcQuarryBlock) {
            level.destroyBlock(main, !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(ModItems.ARC_QUARRY.get());
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    // A part whose main block is gone removes itself.
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (quarryOf(level, pos) == null) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
