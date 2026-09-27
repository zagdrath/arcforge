/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.heat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// The Geothermal Plant's heat sources: lava drained from its tank, plus passive heat from touching
// lava source blocks and magma blocks (which are never consumed).
public final class GeothermalHeat {
    private GeothermalHeat() {}

    // What touches the plant, counted every so often.
    public record Surroundings(int lavaSources, int magmaBlocks) {
        public static final Surroundings NONE = new Surroundings(0, 0);

        public int passiveHeat() {
            return lavaSources * ArcforgeConfig.GEOTHERMAL_LAVA_SOURCE_HEAT.getAsInt()
                    + magmaBlocks * ArcforgeConfig.GEOTHERMAL_MAGMA_HEAT.getAsInt();
        }
    }

    public static int lavaHeat() {
        return ArcforgeConfig.GEOTHERMAL_LAVA_HEAT.getAsInt();
    }

    public static Surroundings scan(Level level, BlockPos pos) {
        int lava = 0;
        int magma = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            FluidState fluid = level.getFluidState(neighbour);
            if (fluid.isSource() && fluid.is(FluidTags.LAVA)) {
                lava++;
            } else if (level.getBlockState(neighbour).is(Blocks.MAGMA_BLOCK)) {
                magma++;
            }
        }
        return new Surroundings(lava, magma);
    }
}
