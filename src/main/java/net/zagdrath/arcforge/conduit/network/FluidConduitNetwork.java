/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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
import net.zagdrath.arcforge.steam.Gases;

// A shared tank of one fluid (1,000 mB per conduit). Pulls from output sides and pushes into input
// sides, each capped at the tier's mB/t. Fluid conduits never carry gases (see GasConduitNetwork). The tank is split evenly back into the conduit block
// entities whenever it changes, so splitting or unloading a network never creates or loses fluid.
public class FluidConduitNetwork extends ConduitNetwork<ResourceHandler<FluidResource>> {
    private FluidResource fluid = FluidResource.EMPTY;
    private int amount;
    private final int capacity;
    private final int rate;
    private final Predicate<FluidResource> accepts;

    public FluidConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        this(level, members, memberSet, tier, ConduitTier.FLUID_CAPACITY_PER_CONDUIT, tier.fluidPerTick(), resource -> !Gases.isGas(resource));
    }

    // perConduit: mB each conduit holds. rate: mB/t pulled and pushed. accepts: the fluids it carries.
    protected FluidConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier,
            int perConduit, int rate, Predicate<FluidResource> accepts) {
        super(level, members, memberSet, tier, Capabilities.Fluid.BLOCK);
        this.capacity = members.size() * perConduit;
        this.rate = rate;
        this.accepts = accepts;
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
        int moved = pull() + push();
        onMoved(moved, gameTime);
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

    // Called every tick with how much was pulled in and pushed out.
    protected void onMoved(int moved, long gameTime) {}

    private int pull() {
        int budget = rate;
        int pulled = 0;
        for (Endpoint<ResourceHandler<FluidResource>> source : sources) {
            ResourceHandler<FluidResource> handler = source.handler();
            int space = Math.min(capacity - amount, budget);
            if (handler == null || space <= 0) {
                continue;
            }
            for (int index = 0; index < handler.size() && space > 0; index++) {
                FluidResource resource = handler.getResource(index);
                if (resource.isEmpty() || !accepts.test(resource) || (!fluid.isEmpty() && !resource.equals(fluid))) {
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
                        pulled += extracted;
                    }
                }
            }
        }
        return pulled;
    }

    private int push() {
        if (fluid.isEmpty()) {
            return 0;
        }
        int budget = rate;
        int pushed = 0;
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
                pushed += inserted;
            }
        }
        return pushed;
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
