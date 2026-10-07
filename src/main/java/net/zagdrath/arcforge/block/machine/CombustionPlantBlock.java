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
import net.zagdrath.arcforge.blockentity.machine.CombustionPlantBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

public class CombustionPlantBlock extends MachineBlock {
    private static final MapCodec<CombustionPlantBlock> CODEC = simpleCodec(CombustionPlantBlock::new);

    @Override
    protected MapCodec<CombustionPlantBlock> codec() {
        return CODEC;
    }

    public CombustionPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CombustionPlantBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.COMBUSTION_PLANT.get(),
                        (innerLevel, pos, blockState, plant) -> plant.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // Smoke from the exhaust stack on top, and the crackle of the fire behind the grate.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.15, pos.getY() + 1.0,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.15, 0.0, 0.07, 0.0);
        if (random.nextDouble() < 0.1) {
            Direction facing = state.getValue(FACING);
            level.playLocalSound(pos.getX() + 0.5 + facing.getStepX() * 0.5, pos.getY() + 0.4, pos.getZ() + 0.5 + facing.getStepZ() * 0.5,
                    SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.0F, false);
        }
    }
}
