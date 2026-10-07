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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.zagdrath.arcforge.blockentity.experience.XpDrainBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The XP Drain: a thin steel grate laid on top of any fluid container (a Reservoir, a Fluid Tank, another mod's tank).
// Experience orbs that land on it, and the experience of a player sneaking on it, run into the container as Liquid
// Experience (see XpDrainBlockEntity). It can only be placed on something that takes fluid from above.
public class XpDrainBlock extends BaseEntityBlock {
    private static final MapCodec<XpDrainBlock> CODEC = simpleCodec(XpDrainBlock::new);

    @Override
    protected MapCodec<XpDrainBlock> codec() {
        return CODEC;
    }

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public XpDrainBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return hasContainerBelow(context.getLevel(), context.getClickedPos()) ? defaultBlockState() : null;
    }

    // Whether the block below takes fluid through its top.
    public static boolean hasContainerBelow(Level level, BlockPos pos) {
        return level.getCapability(Capabilities.Fluid.BLOCK, pos.below(), Direction.UP) != null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new XpDrainBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.XP_DRAIN.get(),
                        (innerLevel, pos, blockState, drain) -> drain.serverTick(serverLevel, pos))
                : null;
    }
}
