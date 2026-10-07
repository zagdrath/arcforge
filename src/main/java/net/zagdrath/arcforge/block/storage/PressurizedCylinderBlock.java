/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.storage;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A stainless gas cylinder: holds one gas. It has no window; LEVEL (0-4) only moves the needle of its
// pressure gauge: 0 empty, then under 25%, 50%, 75%, and 75% or more.
public class PressurizedCylinderBlock extends StorageBlock {
    // 26.1 requires a block codec. Nothing decodes this block type, and its constructor arguments aren't
    // data, so the codec stands for this instance.
    @Override
    protected MapCodec<PressurizedCylinderBlock> codec() {
        return MapCodec.unit(this);
    }

    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 13, 13), Block.box(4, 13, 4, 12, 16, 12));

    public PressurizedCylinderBlock(BlockBehaviour.Properties properties, ConduitTier tier) {
        super(properties, tier);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }

    public static int level(long amount, long capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }
        return (int) Math.min(4, 1 + amount * 4 / capacity);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PressurizedCylinderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.PRESSURIZED_CYLINDER.get(),
                        (innerLevel, pos, blockState, cylinder) -> PressurizedCylinderBlockEntity.serverTick(serverLevel, pos, blockState, cylinder))
                : null;
    }
}
