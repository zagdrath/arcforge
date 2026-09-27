/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;

// Energy and thermal conduits: glow (swap to the "_on" textures and emit light) while their network is moving resource.
public class ActiveConduitBlock extends ConduitBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final int ACTIVE_LIGHT = 4;

    public ActiveConduitBlock(BlockBehaviour.Properties properties, ConduitType conduitType, ConduitTier tier) {
        super(properties.lightLevel(state -> state.getValue(ACTIVE) ? ACTIVE_LIGHT : 0), conduitType, tier);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    // Only touches the world when the value changes, and never triggers neighbour shape updates.
    public static void setActive(Level level, BlockPos pos, boolean active) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ActiveConduitBlock && state.getValue(ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ACTIVE, active), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }
}
