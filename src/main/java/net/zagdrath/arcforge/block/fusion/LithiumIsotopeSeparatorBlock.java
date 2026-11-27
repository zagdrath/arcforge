/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.fusion;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.fusion.LithiumIsotopeSeparatorBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Lithium Isotope Separator (see LithiumIsotopeSeparatorBlockEntity).
public class LithiumIsotopeSeparatorBlock extends MachineBlock {
    public LithiumIsotopeSeparatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LithiumIsotopeSeparatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.LITHIUM_ISOTOPE_SEPARATOR.get(),
                        (innerLevel, pos, blockState, machine) -> machine.serverTick(serverLevel, pos, blockState))
                : null;
    }
}
