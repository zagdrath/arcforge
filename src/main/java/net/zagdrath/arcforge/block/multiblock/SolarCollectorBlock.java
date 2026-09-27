/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

// A Solar Collector: the top layer of the Solar Thermal Array's tower is four of them. Formed they're drawn
// as the trough mirror and its receiver (see SolarThermalArrayRenderer); each one that sees the sky adds a
// quarter of the array's heat.
public class SolarCollectorBlock extends SolarBlock {
    public SolarCollectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }
}
