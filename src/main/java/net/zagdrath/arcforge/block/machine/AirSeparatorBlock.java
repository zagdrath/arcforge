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
import net.zagdrath.arcforge.blockentity.machine.AirSeparatorBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Air Separator (see AirSeparatorBlockEntity): a compressor and cold box that draws air in through its top grille.
public class AirSeparatorBlock extends MachineBlock {
    public AirSeparatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AirSeparatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.AIR_SEPARATOR.get(),
                        (innerLevel, pos, blockState, machine) -> machine.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // Frost breath off the cold box: a wisp of cold vapour sinking from the top now and then.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.02,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, -0.005, 0.0);
        }
    }
}
