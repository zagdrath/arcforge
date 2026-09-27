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
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;

// Energy and thermal networks. Each conduit holds up to one tick's worth of its tier's rate, pooled
// across the network: output sides are pulled into the pool even when nothing wants it yet, and the
// pool is pushed into input sides. Conduits light up while the network holds anything or it is moving.
public abstract class ActiveConduitNetwork<H> extends ConduitNetwork<H> {
    // Ticks without movement before the conduits go dark (prevents flicker).
    private static final int IDLE_TICKS = 10;

    private final int capacity;
    private int stored;
    private boolean active;
    private long lastMovedTick;

    protected ActiveConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier, BlockCapability<H, @Nullable Direction> capability) {
        super(level, members, memberSet, tier, capability);
        this.capacity = members.size() * budget();
    }

    @Override
    protected void onCreated() {
        // Conduits saved as lit stay lit until the network confirms it is idle.
        active = members.stream().anyMatch(pos -> {
            var state = level.getBlockState(pos);
            return state.hasProperty(ActiveConduitBlock.ACTIVE) && state.getValue(ActiveConduitBlock.ACTIVE);
        });
        lastMovedTick = level.getGameTime();
        // A merged or extended network mixes lit and unlit conduits; make them all match.
        for (BlockPos pos : members) {
            ActiveConduitBlock.setActive(level, pos, active);
        }

        long total = 0;
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit) {
                total += conduit.getStored();
            }
        }
        stored = (int) Math.min(capacity, total);
        distribute();
    }

    // The tier's rate per tick; also what each conduit can hold.
    protected abstract int budget();

    // Takes up to `max` from a source and returns how much was taken.
    protected abstract int extract(H source, int max);

    // Gives up to `max` to a sink and returns how much was accepted.
    protected abstract int insert(H sink, int max);

    public int getCapacity() {
        return capacity;
    }

    // What the network holds right now, across all its conduits.
    public int getStored() {
        return stored;
    }

    @Override
    public void tick(long gameTime) {
        int before = stored;
        int moved = pull() + push();
        if (stored != before) {
            distribute();
        }
        if (moved > 0 || stored > 0) {
            lastMovedTick = gameTime;
            setActive(true);
        } else if (active && gameTime - lastMovedTick >= IDLE_TICKS) {
            setActive(false);
        }
    }

    private int pull() {
        int budget = Math.min(budget(), capacity - stored);
        int pulled = 0;
        for (Endpoint<H> source : sources) {
            H handler = source.handler();
            if (budget <= 0) {
                break;
            }
            if (handler == null) {
                continue;
            }
            int amount = extract(handler, budget);
            budget -= amount;
            pulled += amount;
        }
        stored += pulled;
        return pulled;
    }

    // Splits what's stored (up to the tier rate) evenly across sinks in round-robin order.
    private int push() {
        int budget = Math.min(budget(), stored);
        int pushed = 0;
        List<Endpoint<H>> ordered = sinksInTurn();
        for (int i = 0; i < ordered.size() && budget > 0; i++) {
            H handler = ordered.get(i).handler();
            if (handler == null) {
                continue;
            }
            int share = Math.ceilDiv(budget, ordered.size() - i);
            int amount = insert(handler, share);
            budget -= amount;
            pushed += amount;
        }
        stored -= pushed;
        return pushed;
    }

    // Splits the pool evenly across the conduits (the remainder goes to the first ones) so it survives
    // saving, unloading and the network being rebuilt.
    private void distribute() {
        int count = members.size();
        int each = stored / count;
        int remainder = stored % count;
        for (int i = 0; i < count; i++) {
            if (level.getBlockEntity(members.get(i)) instanceof ConduitBlockEntity conduit) {
                conduit.setStored(each + (i < remainder ? 1 : 0));
            }
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
