/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.storage;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A full-block HU store, built like an energy cell but in orange. HEAT (0-4) comes from its temperature
// and picks the model (lit window segments and a glowing core) and the light level.
public class HeatCellBlock extends StorageBlock {
    // 26.1 requires a block codec. Nothing decodes this block type, and its constructor arguments aren't
    // data, so the codec stands for this instance.
    @Override
    protected MapCodec<HeatCellBlock> codec() {
        return MapCodec.unit(this);
    }

    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 4);

    public HeatCellBlock(BlockBehaviour.Properties properties, ConduitTier tier) {
        super(properties.lightLevel(state -> state.getValue(HEAT) * 3), tier);
        registerDefaultState(stateDefinition.any().setValue(HEAT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeatCellBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.HEAT_CELL.get(),
                        (innerLevel, pos, blockState, cell) -> cell.serverTick(serverLevel, pos, blockState))
                : null;
    }
}
