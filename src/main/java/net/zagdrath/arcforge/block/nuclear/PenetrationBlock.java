/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

// A containment Penetration: a sealed sleeve through the wall that pipes, conduits and cables connect through. It
// seals the containment like the wall round it (see Containment) and passes items, fluids, gases and FE straight
// through along its axis (FACING, set toward where it was placed from): whatever's on one face sees what's on the
// other (see ModCapabilities). Two penetrations end to end pass through both.
public class PenetrationBlock extends Block {
    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    // How many penetrations a capability passes through at most (a chain of them through a thick wall).
    public static final int MAX_CHAIN = 8;

    public PenetrationBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    // The block on the far side of a chain of penetrations, seen from side (the face asked about), or null when the
    // side isn't along the axis or the chain is too long.
    public static @Nullable BlockPos through(Level level, BlockPos pos, BlockState state, @Nullable Direction side) {
        if (side == null || side.getAxis() != state.getValue(FACING).getAxis()) {
            return null;
        }
        Direction onward = side.getOpposite();
        BlockPos at = pos.relative(onward);
        for (int i = 0; i < MAX_CHAIN; i++) {
            BlockState there = level.getBlockState(at);
            if (!(there.getBlock() instanceof PenetrationBlock) || there.getValue(FACING).getAxis() != onward.getAxis()) {
                return at;
            }
            at = at.relative(onward);
        }
        return null;
    }
}
