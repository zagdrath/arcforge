/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.blockentity.nuclear.GeologicalRepositoryBlockEntity;

// A Geological Repository (see GeologicalRepositoryBlockEntity): its vault door. Using it opens its GUI.
public class GeologicalRepositoryBlock extends NuclearEntityBlock {
    public GeologicalRepositoryBlock(Supplier<? extends BlockEntityType<?>> type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof GeologicalRepositoryBlockEntity vault) {
            serverPlayer.openMenu(vault, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
