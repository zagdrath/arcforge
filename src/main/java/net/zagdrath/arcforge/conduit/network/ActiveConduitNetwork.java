/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;

// Energy and thermal networks move resource directly from sources to sinks each tick (no buffer)
// and light their conduits while anything is flowing.
public abstract class ActiveConduitNetwork<H> extends ConduitNetwork<H> {
    // Ticks without movement before the conduits go dark (prevents flicker).
    private static final int IDLE_TICKS = 10;

    private boolean active;
    private long lastMovedTick;

    protected ActiveConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier, BlockCapability<H, @Nullable Direction> capability) {
        super(level, members, memberSet, tier, capability);
    }

    @Override
    protected void onCreated() {
        // Conduits saved as lit stay lit until the network confirms it is idle.
        active = members.stream().anyMatch(pos -> {
            var state = level.getBlockState(pos);
            return state.hasProperty(ActiveConduitBlock.ACTIVE) && state.getValue(ActiveConduitBlock.ACTIVE);
        });
        lastMovedTick = level.getGameTime();
    }

    // Moves up to `budget` from sources to sinks and returns how much moved.
    protected abstract int transfer(int budget);

    protected abstract int budget();

    @Override
    public void tick(long gameTime) {
        int moved = sources.isEmpty() || sinks.isEmpty() ? 0 : transfer(budget());
        if (moved > 0) {
            lastMovedTick = gameTime;
            setActive(true);
        } else if (active && gameTime - lastMovedTick >= IDLE_TICKS) {
            setActive(false);
        }
    }

    private void setActive(boolean value) {
        if (active == value) {
            return;
        }
        active = value;
        for (BlockPos pos : members) {
            ActiveConduitBlock.setActive(level, pos, value);
        }
    }
}
