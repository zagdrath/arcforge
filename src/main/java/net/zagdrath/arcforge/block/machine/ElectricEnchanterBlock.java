/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
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
import net.zagdrath.arcforge.blockentity.machine.ElectricEnchanterBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Electric Enchanter: a casing with an enchanting plate on top and an open book floating over it
// (ElectricEnchanterRenderer), which turns its pages while it works. The client ticks it for the book.
public class ElectricEnchanterBlock extends MachineBlock {
    public ElectricEnchanterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricEnchanterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level instanceof ServerLevel serverLevel) {
            return createTickerHelper(type, ModBlockEntityTypes.ELECTRIC_ENCHANTER.get(),
                    (innerLevel, pos, blockState, machine) -> machine.serverTick(serverLevel, pos, blockState));
        }
        return createTickerHelper(type, ModBlockEntityTypes.ELECTRIC_ENCHANTER.get(),
                (innerLevel, pos, blockState, machine) -> machine.book().tick(blockState.getValue(LIT), innerLevel.getRandom()));
    }

    // Glyphs drifting up from the plate into the book while it works, and now and then the rustle of its pages.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + 0.5, y = pos.getY() + 1.35, z = pos.getZ() + 0.5;
            level.addParticle(ParticleTypes.ENCHANT, x, y, z, (random.nextDouble() - 0.5) * 1.6, -0.6 - random.nextDouble() * 0.3,
                    (random.nextDouble() - 0.5) * 1.6);
        }
        if (random.nextInt(40) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS,
                    0.5F, 0.9F + random.nextFloat() * 0.2F, false);
        }
    }
}
