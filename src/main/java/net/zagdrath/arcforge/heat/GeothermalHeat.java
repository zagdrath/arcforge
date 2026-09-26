/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.heat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// Heat values for every Geothermal Plant heat source, and the heat -> FE conversion.
// Ranking: lava > coal > charcoal. Passive lava source heat stacks on top of the combustion chamber.
public final class GeothermalHeat {
    private GeothermalHeat() {}

    public static boolean isSolidFuel(ItemStack stack) {
        return solidFuelHeat(stack) > 0;
    }

    // HU produced while this item burns, or 0 if it is not a valid fuel.
    public static int solidFuelHeat(ItemStack stack) {
        if (stack.is(Items.COAL)) return ArcforgeConfig.GEOTHERMAL_COAL_HEAT.getAsInt();
        if (stack.is(Items.CHARCOAL)) return ArcforgeConfig.GEOTHERMAL_CHARCOAL_HEAT.getAsInt();
        return 0;
    }

    public static int lavaHeat() {
        return ArcforgeConfig.GEOTHERMAL_LAVA_HEAT.getAsInt();
    }

    public static int passiveHeat(int adjacentLavaSources) {
        return adjacentLavaSources * ArcforgeConfig.GEOTHERMAL_LAVA_SOURCE_HEAT.getAsInt();
    }

    // Highest heat the plant can ever reach: the hottest combustion fuel plus lava sources on all 6 sides.
    // The GUI heat gauge scales against this, so a fully-fed plant reads as full.
    public static int maxHeat() {
        int hottestFuel = Math.max(lavaHeat(), Math.max(
                ArcforgeConfig.GEOTHERMAL_COAL_HEAT.getAsInt(),
                ArcforgeConfig.GEOTHERMAL_CHARCOAL_HEAT.getAsInt()));
        return Math.max(1, hottestFuel + passiveHeat(Direction.values().length));
    }

    public static int toFePerTick(int heat) {
        return heat * ArcforgeConfig.GEOTHERMAL_FE_PER_HEAT.getAsInt();
    }

    // Display temperature: ambient 20°C plus 10°C per HU (lava alone reads 420°C).
    public static int toCelsius(int heat) {
        return 20 + heat * 10;
    }

    public static int countAdjacentLavaSources(Level level, BlockPos pos) {
        int count = 0;
        for (Direction direction : Direction.values()) {
            FluidState fluid = level.getFluidState(pos.relative(direction));
            if (fluid.isSource() && fluid.is(FluidTags.LAVA)) {
                count++;
            }
        }
        return count;
    }
}
