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
import net.zagdrath.arcforge.blockentity.machine.SteamTurbineBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

public class SteamTurbineBlock extends MachineBlock {
    public SteamTurbineBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamTurbineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.STEAM_TURBINE.get(),
                        (innerLevel, pos, blockState, turbine) -> turbine.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // Exhaust puffs out of the side louvres while generating.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || random.nextInt(3) != 0) {
            return;
        }
        Direction side = random.nextBoolean() ? state.getValue(FACING).getClockWise() : state.getValue(FACING).getCounterClockWise();
        double x = pos.getX() + 0.5 + side.getStepX() * 0.55;
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.4;
        double z = pos.getZ() + 0.5 + side.getStepZ() * 0.55;
        level.addParticle(ParticleTypes.CLOUD, x, y, z, side.getStepX() * 0.03, 0.01, side.getStepZ() * 0.03);
    }
}
