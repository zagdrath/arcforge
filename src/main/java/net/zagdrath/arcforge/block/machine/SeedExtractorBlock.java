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
import net.zagdrath.arcforge.blockentity.machine.SeedExtractorBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Seed Extractor (see SeedExtractorBlockEntity). Its front shows the threshing drum.
public class SeedExtractorBlock extends MachineBlock {
    private static final MapCodec<SeedExtractorBlock> CODEC = simpleCodec(SeedExtractorBlock::new);

    @Override
    protected MapCodec<SeedExtractorBlock> codec() {
        return CODEC;
    }

    public SeedExtractorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeedExtractorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.SEED_EXTRACTOR.get(),
                        (innerLevel, pos, blockState, machine) -> machine.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // Chaff blowing out of the front, and the rattle of the drum.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.5;
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.4;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.5;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 0.0, 0.0, 0.0);
        }
        if (random.nextDouble() < 0.06) {
            level.playLocalSound(x, y, z, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.3F, 1.2F, false);
        }
    }
}
