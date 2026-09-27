/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConnectionMode;

// Owns every conduit network in one level. Networks are rebuilt only around positions marked dirty
// (placement, removal, chunk load/unload, connection changes), never every tick.
public final class ConduitNetworkManager {
    private static final Map<ServerLevel, ConduitNetworkManager> MANAGERS = new WeakHashMap<>();

    private final ServerLevel level;
    private final Map<BlockPos, ConduitNetwork<?>> byPos = new HashMap<>();
    private final Set<ConduitNetwork<?>> networks = new LinkedHashSet<>();
    private final Set<BlockPos> dirty = new HashSet<>();

    private ConduitNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static ConduitNetworkManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, ConduitNetworkManager::new);
    }

    public static void remove(ServerLevel level) {
        MANAGERS.remove(level);
    }

    public void markDirty(BlockPos pos) {
        dirty.add(pos.immutable());
    }

    public void tick() {
        if (!dirty.isEmpty()) {
            rebuild();
        }
        long gameTime = level.getGameTime();
        for (ConduitNetwork<?> network : networks) {
            network.tick(gameTime);
        }
    }

    // Dissolves every network touching a dirty position and re-forms networks from those positions.
    private void rebuild() {
        Set<BlockPos> seeds = new HashSet<>(dirty);
        for (BlockPos pos : dirty) {
            dissolveAt(pos, seeds);
            for (Direction side : Direction.values()) {
                dissolveAt(pos.relative(side), seeds);
            }
        }
        dirty.clear();

        for (BlockPos seed : seeds) {
            if (!byPos.containsKey(seed) && level.isLoaded(seed) && level.getBlockState(seed).getBlock() instanceof ConduitBlock) {
                register(build(seed));
            }
        }
    }

    private void dissolveAt(BlockPos pos, Set<BlockPos> seeds) {
        ConduitNetwork<?> network = byPos.get(pos);
        if (network != null) {
            networks.remove(network);
            for (BlockPos member : network.getMembers()) {
                byPos.remove(member);
                seeds.add(member);
            }
        }
    }

    private void register(ConduitNetwork<?> network) {
        networks.add(network);
        for (BlockPos member : network.getMembers()) {
            byPos.put(member, network);
        }
        network.onCreated();
    }

    // Flood fill over sides that both conduits agree are pipes.
    private ConduitNetwork<?> build(BlockPos start) {
        ConduitBlock startBlock = (ConduitBlock) level.getBlockState(start).getBlock();
        List<BlockPos> members = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        ConduitTier tier = startBlock.getTier();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            var state = level.getBlockState(pos);
            members.add(pos);
            tier = ConduitTier.lowest(tier, ((ConduitBlock) state.getBlock()).getTier());
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (ConduitBlock.mode(state, side) != ConnectionMode.PIPE || visited.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                var nextState = level.getBlockState(next);
                if (nextState.getBlock() instanceof ConduitBlock other && other.getConduitType() == startBlock.getConduitType()
                        && ConduitBlock.mode(nextState, side.getOpposite()) == ConnectionMode.PIPE) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }

        return switch (startBlock.getConduitType()) {
            case ENERGY -> new EnergyConduitNetwork(level, members, visited, tier);
            case THERMAL -> new ThermalConduitNetwork(level, members, visited, tier);
            case FLUID -> new FluidConduitNetwork(level, members, visited, tier);
            case ITEM -> new ItemConduitNetwork(level, members, visited, tier);
        };
    }
}
