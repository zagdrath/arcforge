/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import java.util.Set;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.zagdrath.arcforge.block.logistics.HeatPipeBlock;
import net.zagdrath.arcforge.blockentity.logistics.HeatPipeBlockEntity;

// Colours a Heat Pipe's core (tint index 0 of its model), seen through the window band: dark steel while it carries
// nothing, then the Thermodynamic Conduits' heat colours, dull red through orange and yellow to white-hot, by the
// temperature it carries.
public final class HeatPipeTint implements BlockTintSource {
    public static final HeatPipeTint INSTANCE = new HeatPipeTint();
    // The cold core: the machine steel's dark tone.
    private static final int COLD = 0xFF2B2F34;

    private HeatPipeTint() {}

    @Override
    public int color(BlockState state) {
        return COLD;
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (!state.hasProperty(HeatPipeBlock.LIT) || !state.getValue(HeatPipeBlock.LIT)
                || !(level.getBlockEntity(pos) instanceof HeatPipeBlockEntity pipe) || pipe.getGlowCelsius() <= 0) {
            return COLD;
        }
        return ConduitTints.heatColor(pipe.getGlowCelsius());
    }

    @Override
    public Set<Property<?>> relevantProperties() {
        return Set.of(HeatPipeBlock.LIT);
    }
}
