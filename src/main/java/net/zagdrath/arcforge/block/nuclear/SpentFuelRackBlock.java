/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.zagdrath.arcforge.blockentity.nuclear.SpentFuelRackBlockEntity;
import net.zagdrath.arcforge.item.nuclear.FuelRodItem;

// A Spent Fuel Rack (see SpentFuelRackBlockEntity): a steel grid, placed under water in a Spent Fuel Pool (it takes the
// water in, waterlogged). Using it with a fuel rod racks it; with an empty hand takes the last rod out.
public class SpentFuelRackBlock extends NuclearEntityBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public SpentFuelRackBlock(Supplier<? extends BlockEntityType<?>> type, BlockBehaviour.Properties properties) {
        super(type, Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(0, 2, 0, 16, 15, 1), Block.box(0, 2, 15, 16, 15, 16),
                Block.box(0, 2, 1, 1, 15, 15), Block.box(15, 2, 1, 16, 15, 15), Block.box(7.5, 2, 1, 8.5, 15, 15), Block.box(1, 2, 7.5, 15, 15, 8.5)),
                properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!FuelRodItem.isFuelRod(stack) || !(level.getBlockEntity(pos) instanceof SpentFuelRackBlockEntity rack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            if (rack.insert(stack)) {
                stack.consume(1, player);
            } else {
                player.sendOverlayMessage(Component.translatable("message.arcforge.spent_fuel_rack.full"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof SpentFuelRackBlockEntity rack) || rack.count() == 0) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack out = rack.extract();
            if (!player.getInventory().add(out)) {
                player.drop(out, false, Prediction.SERVER_ONLY);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
