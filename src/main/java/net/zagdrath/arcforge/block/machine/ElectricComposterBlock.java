/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.machine.ElectricComposterBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Electric Composter (see ElectricComposterBlockEntity). Its top vents the warm, aerated heap.
public class ElectricComposterBlock extends MachineBlock {
    public ElectricComposterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricComposterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.ELECTRIC_COMPOSTER.get(),
                        (innerLevel, pos, blockState, machine) -> machine.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // Steam off the heap from the top vent, and now and then a green speck of the composter's own.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
        double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.WHITE_SMOKE, x, pos.getY() + 1.02, z, 0.0, 0.03, 0.0);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.COMPOSTER, x, pos.getY() + 1.02, z, 0.0, 0.0, 0.0);
        }
    }
}
