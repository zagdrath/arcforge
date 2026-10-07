/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

public class FuelBurnerBlock extends MachineBlock {
    private static final MapCodec<FuelBurnerBlock> CODEC = simpleCodec(FuelBurnerBlock::new);

    @Override
    protected MapCodec<FuelBurnerBlock> codec() {
        return CODEC;
    }

    public FuelBurnerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelBurnerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.FUEL_BURNER.get(),
                        (innerLevel, pos, blockState, machine) -> machine.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // The burner jet roaring across the window.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double along = (random.nextDouble() - 0.5) * 0.5;
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.52 + facing.getClockWise().getStepX() * along;
        double y = pos.getY() + 0.4 + random.nextDouble() * 0.2;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52 + facing.getClockWise().getStepZ() * along;
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.1, z, 0.0, 0.0, 0.0);
        }
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(x, y, z, SoundEvents.BLAZE_BURN, SoundSource.BLOCKS, 0.4F, 0.8F, false);
        }
    }
}
