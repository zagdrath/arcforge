/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.registry.ModCapabilities;

// Moves heat (HU) from output sides into the conduits, and from the conduits into input sides.
// Heat in the conduits carries the temperature of the hottest source it was drawn from, and is only
// given to inputs colder than that: heat never flows from colder to hotter. The conduits keep that
// temperature, so a network rebuilt around heat it already holds (e.g. after a side change) can still
// hand it on even when it's too full to draw more in.
public class ThermalConduitNetwork extends ActiveConduitNetwork<HeatHandler> {
    // Temperature of the heat in the conduits; unknown (ambient) until something is drawn in.
    private int temperature = HeatBuffer.AMBIENT_CELSIUS;
    private boolean pulledThisTick;

    public int getTemperature() {
        return temperature;
    }

    public ThermalConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        super(level, members, memberSet, tier, ModCapabilities.HEAT);
    }

    @Override
    protected void onCreated() {
        super.onCreated();
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit) {
                temperature = Math.max(temperature, conduit.getHeatTemperature());
            }
        }
        saveTemperature();
    }

    @Override
    public void tick(long gameTime) {
        pulledThisTick = false;
        int before = temperature;
        // Heat at ambient can't flow anywhere, so a network full of it would never draw hotter heat in. It's lost.
        if (temperature <= HeatBuffer.AMBIENT_CELSIUS || isStale()) {
            clear();
        }
        super.tick(gameTime);
        if (getStored() == 0) {
            temperature = HeatBuffer.AMBIENT_CELSIUS;
        }
        if (temperature != before) {
            saveTemperature();
        }
    }

    // Heat too cool for every input (drawn while its source was cooling) can't be handed on, and while it fills the
    // conduits they draw nothing in. It's stale once a source has hotter heat that an input could take: losing it lets
    // that heat through. A network with nothing to feed keeps what it holds.
    private boolean isStale() {
        if (getStored() == 0) {
            return false;
        }
        int coldest = Integer.MAX_VALUE;
        for (Endpoint<HeatHandler> sink : sinks) {
            HeatHandler handler = sink.handler();
            if (handler != null) {
                coldest = Math.min(coldest, handler.getTemperature());
            }
        }
        if (coldest == Integer.MAX_VALUE || coldest < temperature) {
            return false;
        }
        for (Endpoint<HeatHandler> source : sources) {
            HeatHandler handler = source.handler();
            if (handler != null && handler.getTemperature() > coldest && handler.extractHeat(1, true) > 0) {
                return true;
            }
        }
        return false;
    }

    private void saveTemperature() {
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit) {
                conduit.setHeatTemperature(temperature);
            }
        }
    }

    @Override
    protected int budget() {
        return tier.heatPerTick();
    }

    @Override
    protected int extract(HeatHandler source, int max) {
        int sourceTemperature = source.getTemperature();
        if (sourceTemperature <= HeatBuffer.AMBIENT_CELSIUS) {
            return 0;
        }
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
