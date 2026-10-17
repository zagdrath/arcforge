/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

// A containment's personnel Airlock door: a heavy steel door that opens by hand (or redstone) and, open or shut, keeps
// the containment sealed (see Containment), because an airlock's doors are interlocked: one won't open while another
// Airlock door within INTERLOCK blocks is open, so there's always a shut door between in and out. Build two, a short
// passage apart, through the wall.
public class AirlockBlock extends DoorBlock {
    public static final int INTERLOCK = 4;

    public AirlockBlock(BlockBehaviour.Properties properties) {
        super(BlockSetType.IRON, properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        boolean open = state.getValue(OPEN);
        if (!open && otherOpen(level, pos, state)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.arcforge.airlock.interlocked"));
            }
            return InteractionResult.FAIL;
        }
        setOpen(player, level, state, pos, !open);
        return InteractionResult.SUCCESS;
    }

    // Whether another Airlock door near this one is open.
    public static boolean otherOpen(Level level, BlockPos pos, BlockState state) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        for (BlockPos other : BlockPos.betweenClosed(lower.offset(-INTERLOCK, -1, -INTERLOCK), lower.offset(INTERLOCK, 1, INTERLOCK))) {
            if (other.equals(lower) || other.equals(lower.above())) {
                continue;
            }
            BlockState there = level.getBlockState(other);
            if (there.getBlock() instanceof AirlockBlock && there.getValue(HALF) == DoubleBlockHalf.LOWER && there.getValue(OPEN)) {
                return true;
            }
        }
        return false;
    }
}
