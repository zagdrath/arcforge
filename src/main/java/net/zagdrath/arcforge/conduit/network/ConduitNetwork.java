/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
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
                    var cache = BlockCapabilityCache.create(capability, level, pos.relative(side), side.getOpposite());
                    (mode == ConnectionMode.OUTPUT ? sources : sinks).add(new Endpoint<>(pos, side, cache));
                }
            }
        }
    }

    // Called once after the network is built.
    protected void onCreated() {}

    public abstract void tick(long gameTime);

    public List<BlockPos> getMembers() {
        return members;
    }

    public boolean contains(BlockPos pos) {
        return memberSet.contains(pos);
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

    // A conduit side configured as input or output, and the machine behind it.
    public record Endpoint<H>(BlockPos conduit, Direction side, BlockCapabilityCache<H, @Nullable Direction> cache) {
        public BlockPos machine() {
            return conduit.relative(side);
        }

        public @Nullable H handler() {
            return cache.getCapability();
        }
    }
}
