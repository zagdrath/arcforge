/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.safety.Fallout;

// Solidified Corium: set core melt, still radioactive. Left out in the world it seeps contamination into its chunk's
// ground (a quarter of molten Corium's, see Fallout.seep), unless a sealed containment holds it.
public class SolidifiedCoriumBlock extends Block {
    public SolidifiedCoriumBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Fallout.seep(level, pos, ArcforgeConfig.CORIUM_SEEP.getAsDouble() * 0.25);
    }
}
