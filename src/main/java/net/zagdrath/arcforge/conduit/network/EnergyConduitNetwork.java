/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitTier;

// Moves FE from output sides into the conduits, and from the conduits into input sides. Each pull and each push runs in
// one root transaction, committed once every machine has had its share. A machine that takes (or gives) nothing is left
// out for a few ticks unless it has more room (or more FE) than it had then, and is then asked with a simulation first
// (see ConduitNetwork.Endpoint.backOff and ActiveConduitNetwork.takesPart).
public class EnergyConduitNetwork extends ActiveConduitNetwork<EnergyHandler> {
    // The open pull or push transaction, while one runs.
    private @Nullable Transaction transaction;

    public EnergyConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        super(level, members, memberSet, tier, Capabilities.Energy.BLOCK);
    }

    @Override
    protected int budget() {
        return tier.energyPerTick();
    }

    @Override
    protected boolean backsOff() {
        return true;
    }

    @Override
    protected long level(EnergyHandler handler, boolean sink) {
        return sink ? handler.getCapacityAsLong() - handler.getAmountAsLong() : handler.getAmountAsLong();
    }

    @Override
    protected int batch(IntSupplier transfer) {
        try (Transaction tx = Transaction.openRoot()) {
            transaction = tx;
            int moved = transfer.getAsInt();
            tx.commit();
            return moved;
        } finally {
            transaction = null;
        }
    }

    // In a nested transaction that's never committed, so it's rolled back.
    @Override
    protected int simulate(EnergyHandler handler, int max, boolean sink) {
        try (Transaction simulation = Transaction.open(openTransaction())) {
            return sink ? handler.insert(max, simulation) : handler.extract(max, simulation);
        }
    }

    @Override
    protected int extract(EnergyHandler source, int max) {
        return source.extract(max, openTransaction());
    }

    @Override
    protected int insert(EnergyHandler sink, int max) {
        return sink.insert(max, openTransaction());
    }

    private Transaction openTransaction() {
        if (transaction == null) {
            throw new IllegalStateException("Energy conduits move FE only inside a pull or push");
        }
        return transaction;
    }
}
