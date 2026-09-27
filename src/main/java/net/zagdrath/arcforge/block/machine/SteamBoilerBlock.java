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
import net.zagdrath.arcforge.blockentity.machine.SteamBoilerBlockEntity;
import net.zagdrath.arcforge.machine.interaction.MachineInteractions;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

public class SteamBoilerBlock extends MachineBlock {
    public SteamBoilerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamBoilerBlockEntity(pos, state);
    }

    // A held water bucket pours into the water tank.
    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        InteractionResult result = MachineInteractions.useFluidContainer(level, pos, player, hand);
        return result != null ? result : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.STEAM_BOILER.get(),
                        (innerLevel, pos, blockState, boiler) -> boiler.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // Wisps of steam from the top and a soft hiss while boiling.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.05,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.03, 0.0);
        }
        if (random.nextDouble() < 0.04) {
            level.playLocalSound(pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.12F, 1.6F, false);
        }
    }
}
