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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.machine.MetalPressBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.sound.MachineSounds;

public class MetalPressBlock extends MachineBlock {
    private static final MapCodec<MetalPressBlock> CODEC = simpleCodec(MetalPressBlock::new);

    @Override
    protected MapCodec<MetalPressBlock> codec() {
        return CODEC;
    }

    public MetalPressBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MetalPressBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.METAL_PRESS.get(),
                        (innerLevel, pos, blockState, press) -> press.serverTick(serverLevel, pos, blockState))
                : createTickerHelper(type, ModBlockEntityTypes.METAL_PRESS.get(), MachineSounds.clientTicker());
    }

    // Sparks at the die while pressing (the sound is its running loop, see MachineLoopSound).
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.3;
        double y = pos.getY() + 0.35 + random.nextDouble() * 0.1;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.3;
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
