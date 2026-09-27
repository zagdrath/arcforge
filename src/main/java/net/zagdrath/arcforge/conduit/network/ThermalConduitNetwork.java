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
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.registry.ModCapabilities;

// Moves heat (HU) from output sides into the conduits, and from the conduits into input sides.
// Heat in the conduits carries the temperature of the hottest source it was drawn from, and is only
// given to inputs colder than that: heat never flows from colder to hotter.
public class ThermalConduitNetwork extends ActiveConduitNetwork<HeatHandler> {
    // Temperature of the heat in the conduits; unknown (ambient) until something is drawn in.
    private int temperature = HeatBuffer.AMBIENT_CELSIUS;
    private boolean pulledThisTick;

    public ThermalConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        super(level, members, memberSet, tier, ModCapabilities.HEAT);
    }

    @Override
    public void tick(long gameTime) {
        pulledThisTick = false;
        super.tick(gameTime);
    }

    @Override
    protected int budget() {
        return tier.heatPerTick();
    }

    @Override
    protected int extract(HeatHandler source, int max) {
        int sourceTemperature = source.getTemperature();
        int extracted = source.extractHeat(max, false);
        if (extracted > 0) {
            // The first heat drawn in a tick sets the temperature; hotter sources that tick raise it.
            temperature = pulledThisTick ? Math.max(temperature, sourceTemperature) : sourceTemperature;
            pulledThisTick = true;
        }
        return extracted;
    }

    @Override
    protected int insert(HeatHandler sink, int max) {
        if (sink.getTemperature() >= temperature) {
            return 0;
        }
        return sink.receiveHeat(max, false);
    }
}
