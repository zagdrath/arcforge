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
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitTier;

// Moves FE from output sides into the conduits, and from the conduits into input sides.
public class EnergyConduitNetwork extends ActiveConduitNetwork<EnergyHandler> {
    public EnergyConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        super(level, members, memberSet, tier, Capabilities.Energy.BLOCK);
    }

    @Override
    protected int budget() {
        return tier.energyPerTick();
    }

    @Override
    protected int extract(EnergyHandler source, int max) {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = source.extract(max, tx);
            tx.commit();
            return extracted;
        }
    }

    @Override
    protected int insert(EnergyHandler sink, int max) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = sink.insert(max, tx);
            tx.commit();
            return inserted;
        }
    }
}
