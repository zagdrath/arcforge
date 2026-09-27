/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

// Source blocks left behind by broken fluid tanks. The tank is still being removed when it spills,
// so the fluid is placed at the end of the level tick, once its spot is free.
public final class FluidSpills {
    private static final Map<ServerLevel, List<Spill>> PENDING = new WeakHashMap<>();

    private record Spill(BlockPos pos, FluidResource fluid) {}

    private FluidSpills() {}

    public static void queue(ServerLevel level, BlockPos pos, FluidResource fluid) {
        PENDING.computeIfAbsent(level, l -> new ArrayList<>()).add(new Spill(pos.immutable(), fluid));
    }

    // Places each queued fluid if its spot is still free (like emptying a bucket there: water
    // evaporates in the Nether, and so on).
    public static void flush(ServerLevel level) {
        List<Spill> spills = PENDING.remove(level);
        if (spills == null) {
            return;
        }
        for (Spill spill : spills) {
            if (level.isLoaded(spill.pos()) && level.getBlockState(spill.pos()).canBeReplaced()) {
                FluidUtil.tryPlaceFluid(spill.fluid(), null, level, spill.pos(), true);
            }
        }
    }
}
