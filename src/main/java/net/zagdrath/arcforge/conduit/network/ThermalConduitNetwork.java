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

// Moves heat (HU) from output sides to input sides, split across sinks in round-robin order.
public class ThermalConduitNetwork extends ActiveConduitNetwork<HeatHandler> {
    public ThermalConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        super(level, members, memberSet, tier, ModCapabilities.HEAT);
    }

    @Override
    protected int budget() {
        return tier.heatPerTick();
    }

    @Override
    protected int transfer(int budget) {
        int moved = 0;
        List<Endpoint<HeatHandler>> ordered = sinksInTurn();
        for (int i = 0; i < ordered.size() && budget > 0; i++) {
            Endpoint<HeatHandler> sink = ordered.get(i);
            HeatHandler target = sink.handler();
            if (target == null) {
                continue;
            }
            int share = Math.ceilDiv(budget, ordered.size() - i);
            for (Endpoint<HeatHandler> source : sources) {
                HeatHandler from = source.handler();
                if (share <= 0) {
                    break;
                }
                if (from == null || source.machine().equals(sink.machine())) {
                    continue;
                }
                int accepted = target.receiveHeat(share, true);
                int extracted = from.extractHeat(accepted, false);
                int received = target.receiveHeat(extracted, false);
                // Anything the sink refused after all goes back to the source, so no heat is lost.
                if (received < extracted) {
                    from.receiveHeat(extracted - received, false);
                }
                share -= received;
                budget -= received;
                moved += received;
            }
        }
        return moved;
    }
}
