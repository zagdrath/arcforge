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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.machine.interaction.MachineInteractions;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

public class GeothermalPlantBlock extends MachineBlock {
    public GeothermalPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeothermalPlantBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.GEOTHERMAL_PLANT.get(),
                        (innerLevel, pos, blockState, plant) -> GeothermalPlantBlockEntity.serverTick(serverLevel, pos, blockState, plant))
                : null;
    }

    // Right-click (or sneak right-click) with a fluid container, e.g. a lava bucket, to fill the lava tank.
    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        InteractionResult result = MachineInteractions.useFluidContainer(level, pos, player, hand);
        return result != null ? result : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 0.8F, false);
        }
        level.addParticle(ParticleTypes.SMOKE, x + random.nextDouble() * 0.6 - 0.3, y, z + random.nextDouble() * 0.6 - 0.3, 0.0, 0.05, 0.0);
    }
}
