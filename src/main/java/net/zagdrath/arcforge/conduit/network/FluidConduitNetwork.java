/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;

// A shared tank of one fluid (1,000 mB per conduit). Pulls from output sides and pushes into input
// sides, each capped at the tier's mB/t. The tank is split evenly back into the conduit block
// entities whenever it changes, so splitting or unloading a network never creates or loses fluid.
public class FluidConduitNetwork extends ConduitNetwork<ResourceHandler<FluidResource>> {
    private FluidResource fluid = FluidResource.EMPTY;
    private int amount;
    private final int capacity;

    public FluidConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        super(level, members, memberSet, tier, Capabilities.Fluid.BLOCK);
        this.capacity = members.size() * ConduitTier.FLUID_CAPACITY_PER_CONDUIT;
    }

    @Override
    protected void onCreated() {
        // Gather the conduits' shares. Connection rules keep different fluids apart, so there is
        // normally only one; if there are several the largest wins and the rest is discarded.
        Map<FluidResource, Integer> totals = new HashMap<>();
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit && !conduit.getFluid().isEmpty()) {
                totals.merge(FluidResource.of(conduit.getFluid()), conduit.getFluid().getAmount(), Integer::sum);
            }
        }
        totals.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(largest -> {
            fluid = largest.getKey();
            amount = Math.min(capacity, largest.getValue());
            if (totals.size() > 1) {
                Arcforge.LOGGER.warn("Fluid conduit network at {} held {} different fluids; kept {}", members.getFirst(), totals.size(), fluid);
            }
        });
        distribute();
    }

    @Override
    public void tick(long gameTime) {
        int before = amount;
        pull();
        push();
        if (amount == 0) {
            fluid = FluidResource.EMPTY;
        }
        if (amount != before) {
            distribute();
            // Once empty the network may now merge with neighbours that hold another fluid.
            if (amount == 0) {
                members.forEach(pos -> ConduitBlock.refreshConnections(level, pos));
            }
        }
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit) {
                conduit.flushSync(false);
            }
        }
    }

    private void pull() {
        int budget = tier.fluidPerTick();
        for (Endpoint<ResourceHandler<FluidResource>> source : sources) {
            ResourceHandler<FluidResource> handler = source.handler();
            int space = Math.min(capacity - amount, budget);
            if (handler == null || space <= 0) {
                continue;
            }
            for (int index = 0; index < handler.size() && space > 0; index++) {
                FluidResource resource = handler.getResource(index);
                if (resource.isEmpty() || (!fluid.isEmpty() && !resource.equals(fluid))) {
                    continue;
                }
                try (Transaction tx = Transaction.openRoot()) {
                    int extracted = handler.extract(index, resource, space, tx);
                    tx.commit();
                    if (extracted > 0) {
                        fluid = resource;
                        amount += extracted;
                        budget -= extracted;
                        space -= extracted;
                    }
                }
            }
        }
    }

    private void push() {
        if (fluid.isEmpty()) {
            return;
        }
        int budget = tier.fluidPerTick();
        List<Endpoint<ResourceHandler<FluidResource>>> ordered = sinksInTurn();
        for (int i = 0; i < ordered.size() && amount > 0 && budget > 0; i++) {
            ResourceHandler<FluidResource> handler = ordered.get(i).handler();
            if (handler == null) {
                continue;
            }
            int share = Math.ceilDiv(Math.min(amount, budget), ordered.size() - i);
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = handler.insert(fluid, share, tx);
                tx.commit();
                amount -= inserted;
                budget -= inserted;
            }
        }
    }

    // Splits the tank evenly across the conduits (the remainder goes to the first ones).
    private void distribute() {
        int count = members.size();
        int each = amount / count;
        int remainder = amount % count;
        for (int i = 0; i < count; i++) {
            if (level.getBlockEntity(members.get(i)) instanceof ConduitBlockEntity conduit) {
                int share = each + (i < remainder ? 1 : 0);
                conduit.setFluid(share > 0 ? fluid.toStack(share) : FluidStack.EMPTY);
            }
        }
    }
}
