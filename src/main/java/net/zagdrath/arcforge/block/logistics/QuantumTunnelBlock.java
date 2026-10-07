/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.logistics;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.logistics.QuantumTunnelBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Quantum Tunnel: a steel frame cube with a socketed port on every face and a cyan core spinning in the middle
// (QuantumTunnelRenderer). Tunnels on one frequency share its FE, heat, liquid, gas and items, across dimensions.
public class QuantumTunnelBlock extends BaseEntityBlock {
    private static final MapCodec<QuantumTunnelBlock> CODEC = simpleCodec(QuantumTunnelBlock::new);

    @Override
    protected MapCodec<QuantumTunnelBlock> codec() {
        return CODEC;
    }

    public QuantumTunnelBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof QuantumTunnelBlockEntity tunnel) {
            serverPlayer.openMenu(tunnel, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ConduitBlock.refreshAround(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuantumTunnelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.QUANTUM_TUNNEL.get(),
                        (innerLevel, pos, blockState, tunnel) -> tunnel.serverTick(serverLevel, pos))
                : null;
    }
}
