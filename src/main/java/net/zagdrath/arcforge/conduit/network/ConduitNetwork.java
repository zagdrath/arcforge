/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntBiFunction;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConnectionMode;

// A group of connected conduits of one type. Sources are sides set to OUTPUT (pulled from),
// sinks are sides set to INPUT (pushed into). Throughput is set by the lowest tier in the network.
public abstract class ConduitNetwork<H> {
    protected final ServerLevel level;
    protected final List<BlockPos> members;
    protected final Set<BlockPos> memberSet;
    protected final ConduitTier tier;
    protected final List<Endpoint<H>> sources = new ArrayList<>();
    protected final List<Endpoint<H>> sinks = new ArrayList<>();
    private int sinkCursor;
    private int sourceCursor;
    private int nextSink;

    protected ConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier, BlockCapability<H, @Nullable Direction> capability) {
        this.level = level;
        this.members = members;
        this.memberSet = memberSet;
        this.tier = tier;
        for (BlockPos pos : members) {
            var state = level.getBlockState(pos);
            for (Direction side : Direction.values()) {
                ConnectionMode mode = ConduitBlock.mode(state, side);
                if (mode.isPort()) {
                    (mode == ConnectionMode.OUTPUT ? sources : sinks).add(new Endpoint<>(pos, side, capability, level));
                }
            }
        }
    }

    // Called once after the network is built.
    protected void onCreated() {}

    public abstract void tick(long gameTime);

    public ConduitTier getTier() {
        return tier;
    }

    public List<BlockPos> getMembers() {
        return members;
    }

    public boolean contains(BlockPos pos) {
        return memberSet.contains(pos);
    }

    // Sinks in round-robin order, starting just after the last one served (see served). Skipping a sink (full,
    // or its filter rejects what's offered) doesn't use up a turn, so the sinks that do accept take equal turns.
    protected List<Endpoint<H>> sinksFromLastServed() {
        int count = sinks.size();
        List<Endpoint<H>> ordered = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ordered.add(sinks.get((nextSink + i) % count));
        }
        return ordered;
    }

    protected void served(Endpoint<H> sink) {
        int index = sinks.indexOf(sink);
        if (index >= 0) {
            nextSink = (index + 1) % sinks.size();
        }
    }

    // Sinks in round-robin order, starting one further along each call so no machine is always served first.
    protected List<Endpoint<H>> sinksInTurn() {
        int count = sinks.size();
        if (count == 0) {
            return List.of();
        }
        int start = sinkCursor++ % count;
        List<Endpoint<H>> ordered = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ordered.add(sinks.get((start + i) % count));
        }
        return ordered;
    }

    // Sources in round-robin order, starting one further along each call so no machine is always drawn from first.
    protected List<Endpoint<H>> sourcesInTurn() {
        int count = sources.size();
        if (count == 0) {
            return List.of();
        }
        int start = sourceCursor++ % count;
        List<Endpoint<H>> ordered = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ordered.add(sources.get((start + i) % count));
        }
        return ordered;
    }

    // Load balancing: shares up to `budget` out across the endpoints (in the order given, so callers pass them in turn)
    // and returns how much was moved. move(endpoint, max) moves up to max and returns how much it did. Each round offers
    // every endpoint still open an equal part of what's left (the remainder to the first ones). One that takes less than
    // its part is done (full, or empty when pulling), and what it left is shared among the rest in the next round, so
    // one full machine never holds back the others, nor favours the one after it.
    protected static <E> int shareOut(List<E> endpoints, int budget, ToIntBiFunction<E, Integer> move) {
        int moved = 0;
        List<E> open = new ArrayList<>(endpoints);
        while (budget > 0 && !open.isEmpty()) {
            int each = budget / open.size();
            int remainder = budget % open.size();
            List<E> stillOpen = new ArrayList<>(open.size());
            for (int i = 0; i < open.size(); i++) {
                int share = each + (i < remainder ? 1 : 0);
                if (share == 0) {
                    stillOpen.add(open.get(i));
                    continue;
                }
                int amount = move.applyAsInt(open.get(i), share);
                budget -= amount;
                moved += amount;
                if (amount >= share) {
                    stillOpen.add(open.get(i));
                }
            }
            if (stillOpen.size() == open.size()) {
                break;
            }
            open = stillOpen;
        }
        return moved;
    }

    // Whether the Conduit Filter on an endpoint's side (if any) lets this through, when the network inserts into
    // (a sink) or extracts from (a source) its machine. Networks skip what it rejects.
    protected boolean filterAllows(Endpoint<H> endpoint, ItemStack stack, boolean inserting) {
        return !(level.getBlockEntity(endpoint.conduit()) instanceof ConduitBlockEntity conduit)
                || conduit.filterAllows(endpoint.side(), stack, inserting);
    }

    protected boolean filterAllows(Endpoint<H> endpoint, Fluid fluid, boolean inserting) {
        return !(level.getBlockEntity(endpoint.conduit()) instanceof ConduitBlockEntity conduit)
                || conduit.filterAllows(endpoint.side(), fluid, inserting);
    }

    // A conduit side configured as input or output, and the machine behind it.
    public static final class Endpoint<H> {
        private final BlockPos conduit;
        private final Direction side;
        private final BlockCapability<H, @Nullable Direction> capability;
        private BlockCapabilityCache<H, @Nullable Direction> cache;

        Endpoint(BlockPos conduit, Direction side, BlockCapability<H, @Nullable Direction> capability, ServerLevel level) {
            this.conduit = conduit;
            this.side = side;
            this.capability = capability;
            this.cache = BlockCapabilityCache.create(capability, level, machine(), side.getOpposite());
        }

        public BlockPos conduit() {
            return conduit;
        }

        public Direction side() {
            return side;
        }

        public BlockPos machine() {
            return conduit.relative(side);
        }

        // The cache keeps a missing handler until the machine invalidates its capabilities, and a multiblock casing
        // whose master turns up later (loaded after it, in another chunk) never does: ask again while it's missing,
        // and once there's an answer go back to a cache that holds it.
        public @Nullable H handler() {
            H handler = cache.getCapability();
            if (handler != null) {
                return handler;
            }
            ServerLevel level = cache.level();
            if (!level.isLoaded(machine())) {
                return null;
            }
            handler = level.getCapability(capability, machine(), side.getOpposite());
            if (handler != null) {
                cache = BlockCapabilityCache.create(capability, level, machine(), side.getOpposite());
            }
            return handler;
        }
    }
}
