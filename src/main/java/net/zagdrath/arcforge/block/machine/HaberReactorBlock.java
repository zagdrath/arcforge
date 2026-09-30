/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.machine.HaberReactorBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Haber Reactor (see HaberReactorBlockEntity): a pressure vessel round a hot iron catalyst bed, lit (the bed glows
// through its sight glass) while it synthesizes.
public class HaberReactorBlock extends MachineBlock {
    public HaberReactorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HaberReactorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.HABER_REACTOR.get(),
                        (innerLevel, pos, blockState, machine) -> machine.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // Heat shimmer off the front while it runs.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || random.nextInt(4) != 0) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.4;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.4;
        level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 0.8, z, 0.0, 0.02, 0.0);
    }
}
