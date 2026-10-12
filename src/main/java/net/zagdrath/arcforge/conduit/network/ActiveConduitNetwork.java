/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

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
// pool is pushed into input sides, both shared evenly among the machines (see ConduitNetwork.shareOut). Conduits
// light up while the network holds anything or it is moving.
// The pool lives in the network; each conduit's share of it (see shareOf) is worked out only when the conduit is saved
// or the network is taken apart, so moving energy touches no block entity.
public abstract class ActiveConduitNetwork<H> extends ConduitNetwork<H> {
    // Ticks without movement before the conduits go dark (prevents flicker).
    private static final int IDLE_TICKS = 10;

    private final int capacity;
    private int stored;
    private boolean active;
    private long lastMovedTick;
    // Whether the members' chunks have been marked as needing saving since a member was last saved (see poolChanged).
    private boolean unsavedMarked;

    protected ActiveConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier, BlockCapability<H, @Nullable Direction> capability) {
        super(level, members, memberSet, tier, capability);
        this.capacity = (int) Math.min(Integer.MAX_VALUE, (long) members.size() * budget());
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
        for (ConduitBlockEntity conduit : conduits) {
            if (conduit != null) {
                total += conduit.getStored();
            }
        }
        // The total is what matters: how it was split among the conduits when saved doesn't need saving again.
        stored = (int) Math.min(capacity, total);
        for (int i = 0; i < conduits.length; i++) {
            if (conduits[i] != null) {
                conduits[i].joinPool(this, i);
            }
        }
    }

    // Hands each conduit its share, so the networks built in its place pool the same amount.
    @Override
    protected void onDissolved() {
        for (int i = 0; i < conduits.length; i++) {
            if (conduits[i] != null) {
                conduits[i].leavePool(this, shareOf(i));
            }
        }
    }

    // The tier's rate per tick; also what each conduit can hold.
    protected abstract int budget();

    // Takes up to `max` from a source and returns how much was taken.
    protected abstract int extract(H source, int max);

    // Gives up to `max` to a sink and returns how much was accepted.
    protected abstract int insert(H sink, int max);

    // Whether a machine that takes (or gives) nothing is left out for a few ticks (see Endpoint.backOff). Only for
    // networks whose machines refuse because they are full or empty, which `level` can see change.
    protected boolean backsOff() {
        return false;
    }

    // How much room a sink has, or how much a source holds: a machine backing off is tried again at once if this
    // rises.
    protected long level(H handler, boolean sink) {
        return 0;
    }

    // Runs one pull or push. Energy networks run each in a single transaction (see EnergyConduitNetwork).
    protected int batch(IntSupplier transfer) {
        return transfer.getAsInt();
    }

    public int getCapacity() {
        return capacity;
    }

    // What the network holds right now, across all its conduits.
    public int getStored() {
        return stored;
    }

    // The conduit at this index in members' share of the pool: an even split, the remainder to the first ones.
    public int shareOf(int index) {
        int count = members.size();
        return stored / count + (index < stored % count ? 1 : 0);
    }

    // A member was saved (with its share then): the next change has to mark the chunks again.
    public void onMemberSaved() {
        unsavedMarked = false;
    }

    @Override
    public void tick(long gameTime) {
        int before = stored;
        int moved = pull(gameTime) + push(gameTime);
        if (stored != before) {
            poolChanged();
        }
        if (moved > 0 || stored > 0) {
            lastMovedTick = gameTime;
            setActive(true);
        } else if (active && gameTime - lastMovedTick >= IDLE_TICKS) {
            setActive(false);
        }
    }

    // Empties the network (what it holds is lost).
    protected void clear() {
        if (stored > 0) {
            stored = 0;
            poolChanged();
        }
    }

    // Draws up to the tier rate evenly from the sources, taken in turn (see shareOut).
    private int pull(long gameTime) {
        int room = capacity - stored;
        if (room <= 0 || sources.isEmpty()) {
            return 0;
        }
        int pulled = transfer(sourcesInTurn(), Math.min(budget(), room), gameTime, false);
        stored += pulled;
        return pulled;
    }

    // Splits what's stored (up to the tier rate) evenly across the sinks, taken in turn (see shareOut).
    private int push(long gameTime) {
        if (stored <= 0 || sinks.isEmpty()) {
            return 0;
        }
        int pushed = transfer(sinksInTurn(), Math.min(budget(), stored), gameTime, true);
        stored -= pushed;
        return pushed;
    }

    // Shares the budget among the machines behind these endpoints, leaving out any that aren't there (unloaded, or
    // broken) and any backing off (see takesPart).
    private int transfer(List<Endpoint<H>> endpoints, int budget, long gameTime, boolean sinks) {
        boolean backsOff = backsOff();
        return batch(() -> {
            List<Endpoint<H>> open = new ArrayList<>(endpoints.size());
            for (Endpoint<H> endpoint : endpoints) {
                H handler = endpoint.handler();
                if (handler != null && (!backsOff || takesPart(endpoint, handler, budget, gameTime, sinks))) {
                    open.add(endpoint);
                }
            }
            return open.isEmpty() ? 0 : share(open, budget, gameTime, sinks, backsOff);
        });
    }

    // Whether a machine that refused last time it was asked takes part this tick: at once if its level rose since, not
    // while it's waiting, and once its wait is over only if a simulated transfer says it would take (or give) some
    // now. The simulation keeps a still-full machine out of the share: refusing a real part would shift the rounding
    // between the others.
    private boolean takesPart(Endpoint<H> endpoint, H handler, int budget, long gameTime, boolean sink) {
        if (!endpoint.isBackedOff()) {
            return true;
        }
        long level = level(handler, sink);
        if (endpoint.levelRose(level)) {
            return true;
        }
        if (endpoint.isWaiting(gameTime)) {
            return false;
        }
        if (sink) {
            countInsertCall();
        }
        if (simulate(handler, budget, sink) > 0) {
            return true;
        }
        endpoint.backOff(gameTime, level);
        return false;
    }

    // What a transfer of up to max would move, without moving it. Only networks that back off need it.
    protected int simulate(H handler, int max, boolean sink) {
        throw new UnsupportedOperationException();
    }

    private int share(List<Endpoint<H>> open, int budget, long gameTime, boolean sinks, boolean backsOff) {
        return shareOut(open, budget, (endpoint, max) -> {
            H handler = endpoint.handler();
            if (handler == null) {
                return 0;
            }
            int amount;
            if (sinks) {
                countInsertCall();
                amount = insert(handler, max);
            } else {
                amount = extract(handler, max);
            }
            if (backsOff) {
                if (amount > 0) {
                    endpoint.resetBackoff();
                } else {
                    endpoint.backOff(gameTime, level(handler, sinks));
                }
            }
            return amount;
        });
    }

    // The pool changed. The conduits' shares are read when they're saved, so their chunks must be saved again; once
    // marked they stay marked until saved, so this only walks the members again after one of them is saved.
    private void poolChanged() {
        if (unsavedMarked) {
            return;
        }
        unsavedMarked = true;
        for (BlockPos pos : members) {
            level.blockEntityChanged(pos);
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
