/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
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
    // The members' block entities (same order as members; null where one is missing). The network is rebuilt whenever
    // a member's block entity is loaded or removed, so these stay current and the tick needn't look them up.
    protected final @Nullable ConduitBlockEntity[] conduits;
    private int sinkCursor;
    private int sourceCursor;
    private int nextSink;
    private boolean dissolved;
    // Debug counters (see /arcforge debug conduits): this second's so far, and the last full second's.
    private int insertCalls;
    private int syncs;
    private int lastInsertCalls;
    private int lastSyncs;

    protected ConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier, BlockCapability<H, @Nullable Direction> capability) {
        this.level = level;
        this.members = members;
        this.memberSet = memberSet;
        this.tier = tier;
        this.conduits = new ConduitBlockEntity[members.size()];
        for (int i = 0; i < members.size(); i++) {
            BlockPos pos = members.get(i);
            ConduitBlockEntity conduit = level.getBlockEntity(pos) instanceof ConduitBlockEntity be ? be : null;
            conduits[i] = conduit;
            var state = level.getBlockState(pos);
            for (Direction side : Direction.values()) {
                ConnectionMode mode = ConduitBlock.mode(state, side);
                if (mode.isPort()) {
                    (mode == ConnectionMode.OUTPUT ? sources : sinks).add(new Endpoint<>(pos, side, capability, level, conduit, () -> !dissolved));
                }
            }
        }
    }

    // Called once after the network is built.
    protected void onCreated() {}

    // Called once when the network is taken apart, before the networks replacing it are built.
    protected void onDissolved() {}

    // Taken apart: its capability caches stop listening.
    final void dissolve() {
        dissolved = true;
        onDissolved();
    }

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

    // --- Debug counters (see /arcforge debug conduits) ---

    public int getSourceCount() {
        return sources.size();
    }

    public int getSinkCount() {
        return sinks.size();
    }

    // Calls into machines' insert (simulated or not) over the last full second.
    public int getInsertCallsPerSecond() {
        return lastInsertCalls;
    }

    // Client updates its conduits sent over the last full second.
    public int getSyncsPerSecond() {
        return lastSyncs;
    }

    protected void countInsertCall() {
        insertCalls++;
    }

    void countSync() {
        syncs++;
    }

    // Called by the manager once a second.
    void rollCounters() {
        lastInsertCalls = insertCalls;
        lastSyncs = syncs;
        insertCalls = 0;
        syncs = 0;
    }

    // The index in sinks to start the next round-robin search from: just after the last one served (see served).
    // Skipping a sink (full, or its filter rejects what's offered) doesn't use up a turn, so the sinks that do accept
    // take equal turns.
    protected int nextSink() {
        return nextSink;
    }

    protected void served(int sinkIndex) {
        nextSink = (sinkIndex + 1) % sinks.size();
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
    // When every endpoint takes its part (or the budget runs out) that's one call each; only one filling up part way
    // gives the others another round.
    protected static <E> int shareOut(List<E> endpoints, int budget, ToIntBiFunction<E, Integer> move) {
        int moved = 0;
        List<E> open = new ArrayList<>(endpoints);
        while (budget > 0 && !open.isEmpty()) {
            int count = open.size();
            int each = budget / count;
            int remainder = budget % count;
            // The endpoints still open are packed to the front as the round goes.
            int kept = 0;
            for (int i = 0; i < count; i++) {
                E endpoint = open.get(i);
                int share = each + (i < remainder ? 1 : 0);
                if (share == 0) {
                    open.set(kept++, endpoint);
                    continue;
                }
                int amount = move.applyAsInt(endpoint, share);
                budget -= amount;
                moved += amount;
                if (amount >= share) {
                    open.set(kept++, endpoint);
                }
            }
            if (kept == count) {
                break;
            }
            open.subList(kept, count).clear();
        }
        return moved;
    }

    // Whether the Conduit Filter on an endpoint's side (if any) lets this through, when the network inserts into
    // (a sink) or extracts from (a source) its machine. Networks skip what it rejects.
    protected boolean filterAllows(Endpoint<H> endpoint, ItemStack stack, boolean inserting) {
        ConduitBlockEntity conduit = endpoint.conduitEntity();
        return conduit == null || conduit.filterAllows(endpoint.side(), stack, inserting);
    }

    protected boolean filterAllows(Endpoint<H> endpoint, Fluid fluid, boolean inserting) {
        ConduitBlockEntity conduit = endpoint.conduitEntity();
        return conduit == null || conduit.filterAllows(endpoint.side(), fluid, inserting);
    }

    // A conduit side configured as input or output, and the machine behind it.
    public static final class Endpoint<H> {
        // The longest wait before a machine that took (or gave) nothing is tried again (see backOff).
        static final int MAX_BACKOFF_TICKS = 8;

        private final BlockPos conduit;
        private final Direction side;
        private final BlockCapability<H, @Nullable Direction> capability;
        private final @Nullable ConduitBlockEntity conduitEntity;
        // False once the network is gone, so its caches stop calling back.
        private final BooleanSupplier valid;
        private BlockCapabilityCache<H, @Nullable Direction> cache;
        // A machine that took (or gave) nothing is left out until retryAt, unless its level (how much room it has, or how
        // much it holds; see ActiveConduitNetwork.level) rises past what it was then, or its capabilities are invalidated.
        private int backoff;
        private long retryAt;
        private long refusedLevel;

        Endpoint(BlockPos conduit, Direction side, BlockCapability<H, @Nullable Direction> capability, ServerLevel level,
                @Nullable ConduitBlockEntity conduitEntity, BooleanSupplier valid) {
            this.conduit = conduit;
            this.side = side;
            this.capability = capability;
            this.conduitEntity = conduitEntity;
            this.valid = valid;
            this.cache = createCache(level);
        }

        private BlockCapabilityCache<H, @Nullable Direction> createCache(ServerLevel level) {
            return BlockCapabilityCache.create(capability, level, machine(), side.getOpposite(), valid, this::resetBackoff);
        }

        public BlockPos conduit() {
            return conduit;
        }

        // The conduit's block entity, or null if it had none when the network was built.
        public @Nullable ConduitBlockEntity conduitEntity() {
            return conduitEntity;
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
                cache = createCache(level);
            }
            return handler;
        }

        // Whether it refused the last time it was asked.
        boolean isBackedOff() {
            return backoff > 0;
        }

        boolean isWaiting(long gameTime) {
            return gameTime < retryAt;
        }

        boolean levelRose(long level) {
            return level > refusedLevel;
        }

        // Still waiting at this game time, its level no higher than when it last refused.
        boolean isBackingOff(long gameTime, long level) {
            return isWaiting(gameTime) && !levelRose(level);
        }

        // It took (or gave) nothing: leave it out for 2, then 4, then 8 ticks (MAX_BACKOFF_TICKS) at a time.
        void backOff(long gameTime, long level) {
            backoff = Math.min(MAX_BACKOFF_TICKS, Math.max(2, backoff * 2));
            retryAt = gameTime + backoff;
            refusedLevel = level;
        }

        void resetBackoff() {
            backoff = 0;
            retryAt = 0;
        }
    }
}
