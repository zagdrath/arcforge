/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

// Spreads a structure's periodic work (re-validating its blocks, rechecking the redstone across them, scanning round
// it) over its interval by where it stands: each still does it once an interval, but a base full of structures no
// longer does all of theirs on the same tick, which made a spike every second or two however few the rest of the ticks
// cost.
public final class TickSpread {
    private TickSpread() {}

    // Whether the structure at pos does its every-interval work this tick.
    public static boolean due(Level level, BlockPos pos, int interval) {
        return Math.floorMod(level.getGameTime() + (pos.hashCode() & 0x7FFFFFFF), interval) == 0;
    }
}
