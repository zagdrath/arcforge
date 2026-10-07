/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.experience;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.experience.XpShowerBlockEntity;
import net.zagdrath.arcforge.machine.interaction.MachineInteractions;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The XP Shower: a ceiling fixture (a mounting plate, a short riser and a shower head) hung under a block. It's fed Liquid
// Experience through its top, by conduit or from a container above it, and gives a player sneaking under it a level at a
// time (see XpShowerBlockEntity). A redstone signal turns it off.
public class XpShowerBlock extends BaseEntityBlock {
    private static final MapCodec<XpShowerBlock> CODEC = simpleCodec(XpShowerBlock::new);

    @Override
    protected MapCodec<XpShowerBlock> codec() {
        return CODEC;
    }

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(4, 14, 4, 12, 16, 12),
            Block.box(6.5, 9, 6.5, 9.5, 14, 9.5),
            Block.box(3, 6, 3, 13, 9, 13));

    public XpShowerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // Hung from the middle of the block above (a tank, a conduit or any block solid there).
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.above(), Direction.DOWN);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return direction == Direction.UP && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    // A bucket of Liquid Experience pours in; an empty bucket takes some back.
    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        InteractionResult result = MachineInteractions.useFluidContainer(level, pos, player, hand);
        return result != null ? result : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new XpShowerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.XP_SHOWER.get(),
                        (innerLevel, pos, blockState, shower) -> shower.serverTick(serverLevel, pos))
                : null;
    }
}
