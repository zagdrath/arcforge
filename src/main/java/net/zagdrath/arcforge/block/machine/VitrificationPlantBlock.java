/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.machine.VitrificationPlantBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Vitrification Plant (see VitrificationPlantBlockEntity).
public class VitrificationPlantBlock extends MachineBlock {
    public VitrificationPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VitrificationPlantBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.VITRIFICATION_PLANT.get(),
                        (innerLevel, pos, blockState, machine) -> machine.serverTick(serverLevel, pos, blockState))
                : null;
    }
}
