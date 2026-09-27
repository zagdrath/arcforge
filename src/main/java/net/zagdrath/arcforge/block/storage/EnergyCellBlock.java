/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.storage;

import org.jspecify.annotations.Nullable;

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
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A full-block FE store. CHARGE (0-4) picks the model: 0 is fully dark, then 1-4 lit segments.
// It also sets the light level, so a charged cell glows at night.
public class EnergyCellBlock extends StorageBlock {
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    private static final int[] LIGHT_BY_CHARGE = { 0, 3, 5, 7, 9 };

    public EnergyCellBlock(BlockBehaviour.Properties properties, ConduitTier tier) {
        super(properties.lightLevel(state -> LIGHT_BY_CHARGE[state.getValue(CHARGE)]), tier);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyCellBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.ENERGY_CELL.get(),
                        (innerLevel, pos, blockState, cell) -> EnergyCellBlockEntity.serverTick(serverLevel, pos, blockState, cell))
                : null;
    }
}
