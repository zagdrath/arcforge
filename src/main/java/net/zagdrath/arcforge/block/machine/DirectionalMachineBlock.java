/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

// A machine that can face any of the six directions (the Block Breaker and Block Placer). Placed, it faces the
// player like a dispenser. Facing up or down, its other faces are fixed (see RelativeSide.toDirection).
public abstract class DirectionalMachineBlock extends MachineBlock {
    public static final EnumProperty<Direction> FACING_6 = BlockStateProperties.FACING;

    protected DirectionalMachineBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected EnumProperty<Direction> facingProperty() {
        return FACING_6;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING_6, context.getNearestLookingDirection().getOpposite());
    }
}
