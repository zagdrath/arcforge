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
import net.zagdrath.arcforge.blockentity.machine.RefiningMachineBlockEntity;
import net.zagdrath.arcforge.recipe.RefiningProcess;

// The Catalytic Reformer, Coker and Lubricant Blender (see RefiningMachineBlockEntity). Lit while they work: the
// Reformer's catalyst tubes glow, the Coker's drum door glows and smokes, the Blender's mixer turns.
public class RefiningMachineBlock extends MachineBlock {
    private final RefiningProcess process;

    public RefiningMachineBlock(BlockBehaviour.Properties properties, RefiningProcess process) {
        super(properties);
        this.process = process;
    }

    public RefiningProcess getProcess() {
        return process;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RefiningMachineBlockEntity(process, pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return (innerLevel, pos, blockState, entity) -> {
            if (entity instanceof RefiningMachineBlockEntity machine) {
                machine.serverTick(serverLevel, pos, blockState);
            }
        };
    }

    // The Coker smokes from its top while it runs; the Reformer gives off a heat shimmer.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || process == RefiningProcess.BLENDING || random.nextInt(process == RefiningProcess.COKING ? 2 : 5) != 0) {
            return;
        }
        if (process == RefiningProcess.COKING) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.05, pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                    0.0, 0.04, 0.0);
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.52 + (random.nextDouble() - 0.5) * 0.4;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52 + (random.nextDouble() - 0.5) * 0.4;
        level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 0.8, z, 0.0, 0.02, 0.0);
    }
}
