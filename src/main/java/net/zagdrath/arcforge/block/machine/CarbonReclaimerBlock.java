/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.machine.CarbonReclaimerBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Carbon Reclaimer (see CarbonReclaimerBlockEntity): Carbon Dioxide and Hydrogen back into Carbon Dust and Water, with
// FE. LIT while it works.
public class CarbonReclaimerBlock extends MachineBlock {
    private static final MapCodec<CarbonReclaimerBlock> CODEC = simpleCodec(CarbonReclaimerBlock::new);

    @Override
    protected MapCodec<CarbonReclaimerBlock> codec() {
        return CODEC;
    }

    public CarbonReclaimerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CarbonReclaimerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.CARBON_RECLAIMER.get(),
                        (innerLevel, pos, blockState, machine) -> machine.serverTick(serverLevel, pos, blockState))
                : null;
    }
}
