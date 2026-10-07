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
import net.zagdrath.arcforge.blockentity.machine.FiberizerBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

public class FiberizerBlock extends MachineBlock {
    private static final MapCodec<FiberizerBlock> CODEC = simpleCodec(FiberizerBlock::new);

    @Override
    protected MapCodec<FiberizerBlock> codec() {
        return CODEC;
    }

    public FiberizerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FiberizerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.FIBERIZER.get(),
                        (innerLevel, pos, blockState, fiberizer) -> fiberizer.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // Pale fibres drifting off the spinning disc at the window, and the whirr of the spinner.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.5;
        double y = pos.getY() + 0.35 + random.nextDouble() * 0.3;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.5;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0.0, 0.0, 0.0);
        }
        if (random.nextDouble() < 0.08) {
            level.playLocalSound(x, y, z, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.5F, 1.4F, false);
        }
    }
}
