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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
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
// Both are shared evenly among the machines (see ConduitNetwork.shareOut).
// Conduit Filters are checked live: sources and sinks whose filter rejects the fluid are skipped. The network
// still carries one fluid at a time, so a fluid no sink accepts stays in it until one does.
// While fluid moves through, every conduit shows it (ConduitBlockEntity.getFlowing), even when none stays in them; it
// clears FLOW_IDLE_TICKS after the flow stops. (GasConduitNetwork does the same its own way, lighting the conduits.)
public class FluidConduitNetwork extends ConduitNetwork<ResourceHandler<FluidResource>> {
    private static final int FLOW_IDLE_TICKS = 10;

    private FluidResource fluid = FluidResource.EMPTY;
    private int amount;
    private final int capacity;
    private final int rate;
    private final Predicate<FluidResource> accepts;
    // The fluid the conduits show moving through, and when it last moved.
    private Fluid shownFlowing = Fluids.EMPTY;
    private long lastFlowTick;

    public FluidConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        this(level, members, memberSet, tier, tier.fluidBuffer(), tier.fluidPerTick(), resource -> !Gases.isGas(resource));
    }

    // perConduit: mB each conduit holds (at least a tick of rate, see ConduitTier.fluidBuffer). rate: mB/t pulled and
    // pushed. accepts: the fluids it carries. The conduits' buffers pool into one tank.
    protected FluidConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier,
            int perConduit, int rate, Predicate<FluidResource> accepts) {
        super(level, members, memberSet, tier, Capabilities.Fluid.BLOCK);
        this.capacity = members.size() * perConduit;
        this.rate = rate;
        this.accepts = accepts;
    }

    public FluidResource getFluid() {
        return fluid;
    }

    public int getAmount() {
        return amount;
    }

    public int getCapacity() {
        return capacity;
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
        // A rebuilt network carries on showing what its conduits showed, until it's seen to stop.
        lastFlowTick = level.getGameTime();
        for (ConduitBlockEntity conduit : conduits) {
            if (conduit != null && conduit.getFlowing() != Fluids.EMPTY) {
                shownFlowing = conduit.getFlowing();
                break;
            }
        }
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
    }

    // Called every tick with how much was pulled in and pushed out (before an emptied tank forgets its fluid).
    protected void onMoved(int moved, long gameTime) {
        if (moved > 0 && !fluid.isEmpty()) {
            lastFlowTick = gameTime;
            showFlowing(fluid.getFluid());
        } else if (shownFlowing != Fluids.EMPTY && gameTime - lastFlowTick >= FLOW_IDLE_TICKS) {
            showFlowing(Fluids.EMPTY);
        }
    }

    private void showFlowing(Fluid moving) {
        if (moving == shownFlowing) {
            return;
        }
        shownFlowing = moving;
        for (ConduitBlockEntity conduit : conduits) {
            if (conduit != null) {
                conduit.setFlowing(moving);
            }
        }
    }

    // Draws up to the rate evenly from the sources, taken in turn (see shareOut).
    private int pull() {
        if (amount >= capacity || sources.isEmpty()) {
            return 0;
        }
        return shareOut(sourcesInTurn(), Math.min(rate, capacity - amount), this::pullFrom);
    }

    // Draws up to max of the network's fluid (or, while it's empty, of whatever fluid comes first) from one source.
    private int pullFrom(Endpoint<ResourceHandler<FluidResource>> source, int max) {
        ResourceHandler<FluidResource> handler = source.handler();
        if (handler == null) {
            return 0;
        }
        int pulled = 0;
        for (int index = 0; index < handler.size() && pulled < max; index++) {
            FluidResource resource = handler.getResource(index);
            if (resource.isEmpty() || !accepts.test(resource) || (!fluid.isEmpty() && !resource.equals(fluid))
                    || !filterAllows(source, resource.getFluid(), false)) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                int extracted = handler.extract(index, resource, max - pulled, tx);
                tx.commit();
                if (extracted > 0) {
                    fluid = resource;
                    amount += extracted;
                    pulled += extracted;
                }
            }
        }
        return pulled;
    }

    // Splits up to the rate evenly across the sinks, taken in turn (see shareOut). Sinks whose filter rejects the fluid
    // are left out, so the rest share it.
    private int push() {
        if (fluid.isEmpty()) {
            return 0;
        }
        List<Endpoint<ResourceHandler<FluidResource>>> ordered = sinksInTurn().stream()
                .filter(sink -> sink.handler() != null && filterAllows(sink, fluid.getFluid(), true))
                .toList();
        return shareOut(ordered, Math.min(rate, amount), (sink, max) -> {
            countInsertCall();
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = sink.handler().insert(fluid, max, tx);
                tx.commit();
                amount -= inserted;
                return inserted;
            }
        });
    }

    // Splits the tank evenly across the conduits (the remainder goes to the first ones).
    private void distribute() {
        int count = members.size();
        int each = amount / count;
        int remainder = amount % count;
        for (int i = 0; i < count; i++) {
            ConduitBlockEntity conduit = conduits[i];
            if (conduit != null) {
                int share = each + (i < remainder ? 1 : 0);
                conduit.setFluid(share > 0 ? fluid.toStack(share) : FluidStack.EMPTY);
            }
        }
    }
}
