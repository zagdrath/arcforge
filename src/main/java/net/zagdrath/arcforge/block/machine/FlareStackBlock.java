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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.zagdrath.arcforge.blockentity.machine.FlareStackBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Flare Stack (see FlareStackBlockEntity): a base block whose stack and flame (drawn by FlareStackRenderer) stand
// four blocks above it. Only the base is solid; the stack is for show. LIT while it burns.
public class FlareStackBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FlareStackBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlareStackBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.FLARE_STACK.get(), (innerLevel, pos, blockState, flare) -> flare.serverTick(serverLevel))
                : null;
    }

    // Smoke off the top of the flame while it burns.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || random.nextInt(3) != 0) {
            return;
        }
        level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.4 + random.nextDouble() * 0.2, pos.getY() + 5.9, pos.getZ() + 0.4 + random.nextDouble() * 0.2,
                0.0, 0.06, 0.0);
    }
}
