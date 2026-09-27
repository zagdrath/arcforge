/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.zagdrath.arcforge.conduit.ConduitTier;

// Moves FE from output sides to input sides, split across sinks in round-robin order.
public class EnergyConduitNetwork extends ActiveConduitNetwork<EnergyHandler> {
    public EnergyConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        super(level, members, memberSet, tier, Capabilities.Energy.BLOCK);
    }

    @Override
    protected int budget() {
        return tier.energyPerTick();
    }

    @Override
    protected int transfer(int budget) {
        int moved = 0;
        List<Endpoint<EnergyHandler>> ordered = sinksInTurn();
        for (int i = 0; i < ordered.size() && budget > 0; i++) {
            Endpoint<EnergyHandler> sink = ordered.get(i);
            EnergyHandler target = sink.handler();
            if (target == null) {
                continue;
            }
            // Even share of what's left for this sink and the ones after it.
            int share = Math.ceilDiv(budget, ordered.size() - i);
            for (Endpoint<EnergyHandler> source : sources) {
                if (share <= 0) {
                    break;
                }
                if (source.machine().equals(sink.machine())) {
                    continue;
                }
                int amount = EnergyHandlerUtil.move(source.handler(), target, share, null);
                share -= amount;
                budget -= amount;
                moved += amount;
            }
        }
        return moved;
    }
}
