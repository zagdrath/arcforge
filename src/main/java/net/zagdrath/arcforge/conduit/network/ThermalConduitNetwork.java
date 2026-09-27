/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.registry.ModCapabilities;

// Moves heat (HU) from output sides into the conduits, and from the conduits into input sides.
public class ThermalConduitNetwork extends ActiveConduitNetwork<HeatHandler> {
    public ThermalConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        super(level, members, memberSet, tier, ModCapabilities.HEAT);
    }

    @Override
    protected int budget() {
        return tier.heatPerTick();
    }

    @Override
    protected int extract(HeatHandler source, int max) {
        return source.extractHeat(max, false);
    }

    @Override
    protected int insert(HeatHandler sink, int max) {
        return sink.receiveHeat(max, false);
    }
}
