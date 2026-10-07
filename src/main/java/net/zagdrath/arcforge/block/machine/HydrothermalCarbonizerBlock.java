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
import net.zagdrath.arcforge.blockentity.machine.HydrothermalCarbonizerBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Hydrothermal Carbonizer (see HydrothermalCarbonizerBlockEntity): a pressure vessel that cooks biomass in hot water
// into Bio-Coal, lit while it works.
public class HydrothermalCarbonizerBlock extends MachineBlock {
    private static final MapCodec<HydrothermalCarbonizerBlock> CODEC = simpleCodec(HydrothermalCarbonizerBlock::new);

    @Override
    protected MapCodec<HydrothermalCarbonizerBlock> codec() {
        return CODEC;
    }

    public HydrothermalCarbonizerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HydrothermalCarbonizerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.HYDROTHERMAL_CARBONIZER.get(),
                        (innerLevel, pos, blockState, machine) -> machine.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // A wisp of steam from the vessel's relief valve on top.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || random.nextInt(3) != 0) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 - facing.getStepX() * 0.2;
        double z = pos.getZ() + 0.5 - facing.getStepZ() * 0.2;
        level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 1.05, z, 0.0, 0.03, 0.0);
    }
}
