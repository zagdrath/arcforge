/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.sound.MachineSounds;

// The front's cell fizzes while it splits water (an animated texture); the sound is its running loop, see MachineLoopSound.
public class ElectrolyzerBlock extends MachineBlock {
    private static final MapCodec<ElectrolyzerBlock> CODEC = simpleCodec(ElectrolyzerBlock::new);

    @Override
    protected MapCodec<ElectrolyzerBlock> codec() {
        return CODEC;
    }

    public ElectrolyzerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectrolyzerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.ELECTROLYZER.get(),
                        (innerLevel, pos, blockState, electrolyzer) -> electrolyzer.serverTick(serverLevel, pos, blockState))
                : createTickerHelper(type, ModBlockEntityTypes.ELECTROLYZER.get(), MachineSounds.clientTicker());
    }
}
