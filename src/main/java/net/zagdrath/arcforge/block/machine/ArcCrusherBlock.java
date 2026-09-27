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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

public class ArcCrusherBlock extends MachineBlock {
    public ArcCrusherBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcCrusherBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.ARC_CRUSHER.get(),
                        (innerLevel, pos, blockState, crusher) -> crusher.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // Arc sparks at the rollers and a grinding hum while crushing.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.3;
        double y = pos.getY() + 0.4 + random.nextDouble() * 0.2;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.3;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.0, 0.0);
        }
        if (random.nextDouble() < 0.08) {
            level.playLocalSound(x, y, z, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.25F, 0.6F, false);
        }
    }
}
