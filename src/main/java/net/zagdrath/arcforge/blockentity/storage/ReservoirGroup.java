/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// Reservoir blocks that touch (on a face, in loaded chunks) and hold the same fluid, or none, form one tank: a group.
// Each block keeps its own 32,000 mB share in its own tank, so a block always carries exactly its share when it's broken
// or unloaded, and the group only decides where fluid goes. The group fills from the bottom up: fluid goes into the
// lowest layer (the group's blocks at one height) with room, spread evenly across it, and comes out of the highest layer
// that holds any, so every layer below the surface is full, every one above it empty, and the blocks of the surface layer
// share one level. After the group changes shape (a block placed, broken, loaded or unloaded) the fluid is settled into
// that order again on the next tick (never inside a transaction).
//
// A group is built lazily from any of its blocks (a flood fill, up to logistics.reservoir.maxBlocks) and shared by all of them;
// placing a block simply builds a new group that takes in its neighbours, and removing or unloading one invalidates the
// group it was in, so the rest rebuild. Blocks holding a different fluid next to the group stay separate tanks.
public final class ReservoirGroup {
    private final List<ReservoirBlockEntity> members;
    // The members by height, lowest first; each layer one height.
    private final List<List<ReservoirBlockEntity>> layers = new ArrayList<>();
    private final ResourceHandler<FluidResource> handler = new Handler();
    private boolean valid = true;
    private boolean settled;
    private long tickedAt = Long.MIN_VALUE;
    private int comparatorSignal = -1;
    private boolean hadFluid;

    private ReservoirGroup(List<ReservoirBlockEntity> members) {
        this.members = members;
        List<ReservoirBlockEntity> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparingInt(reservoir -> reservoir.getBlockPos().getY()));
        int y = Integer.MIN_VALUE;
        for (ReservoirBlockEntity reservoir : sorted) {
            if (reservoir.getBlockPos().getY() != y) {
                y = reservoir.getBlockPos().getY();
                layers.add(new ArrayList<>());
            }
            layers.getLast().add(reservoir);
        }
        this.hadFluid = !fluid().isEmpty();
    }

    // The group a block belongs to, built from it by a flood fill over loaded Reservoirs that hold this group's fluid or
    // none. Members of other groups it takes in leave theirs (which then rebuild from what's left). Without a level (a
    // block entity not yet placed) the group is the block alone.
    static ReservoirGroup build(@Nullable Level level, ReservoirBlockEntity seed) {
        int limit = ArcforgeConfig.RESERVOIR_MAX_BLOCKS.getAsInt();
        List<ReservoirBlockEntity> members = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<ReservoirBlockEntity> queue = new ArrayDeque<>();
        FluidResource fluid = seed.ownFluid();
        seen.add(seed.getBlockPos());
        queue.add(seed);
        while (!queue.isEmpty() && members.size() < limit) {
            ReservoirBlockEntity reservoir = queue.poll();
            members.add(reservoir);
            for (Direction direction : level != null ? Direction.values() : new Direction[0]) {
                BlockPos next = reservoir.getBlockPos().relative(direction);
                if (!seen.add(next) || !level.isLoaded(next) || !(level.getBlockEntity(next) instanceof ReservoirBlockEntity neighbour)
                        || neighbour.isRemoved()) {
                    continue;
                }
                FluidResource theirs = neighbour.groupFluid();
                if (!theirs.isEmpty()) {
                    if (fluid.isEmpty()) {
                        fluid = theirs;
                    } else if (!fluid.equals(theirs)) {
                        continue;
                    }
                }
                queue.add(neighbour);
            }
        }
        ReservoirGroup group = new ReservoirGroup(members);
        for (ReservoirBlockEntity member : members) {
            member.joinGroup(group);
        }
        return group;
    }

    public boolean isValid() {
        return valid;
    }

    void invalidate() {
        valid = false;
    }

    boolean contains(ReservoirBlockEntity reservoir) {
        return members.contains(reservoir);
    }

    public int size() {
        return members.size();
    }

    public ResourceHandler<FluidResource> handler() {
        return handler;
    }

    // The group's fluid: the first one any member holds.
    public FluidResource fluid() {
        for (List<ReservoirBlockEntity> layer : layers) {
            for (ReservoirBlockEntity reservoir : layer) {
                FluidResource fluid = reservoir.ownFluid();
                if (!fluid.isEmpty()) {
                    return fluid;
                }
            }
        }
        return FluidResource.EMPTY;
    }

    public long amount() {
        long amount = 0;
        for (ReservoirBlockEntity reservoir : members) {
            amount += reservoir.ownAmount();
        }
        return amount;
    }

    public long capacity() {
        return (long) members.size() * ReservoirBlockEntity.CAPACITY;
    }

    public int comparatorSignal() {
        long capacity = capacity();
        return capacity <= 0 ? 0 : Mth.floor(15.0 * amount() / capacity);
    }

    // --- Ticking (once per tick, from whichever member ticks first) ---

    void tick(ServerLevel level) {
        if (tickedAt == level.getGameTime()) {
            return;
        }
        tickedAt = level.getGameTime();
        if (!settled) {
            settle();
            settled = true;
        }
        boolean hasFluid = amount() > 0;
        if (hadFluid && !hasFluid) {
            // Emptied: let it join neighbours holding another fluid.
            hadFluid = false;
            invalidate();
            return;
        }
        hadFluid = hasFluid;
        int signal = comparatorSignal();
        if (signal != comparatorSignal) {
            comparatorSignal = signal;
            for (ReservoirBlockEntity reservoir : members) {
                if (!reservoir.isRemoved()) {
                    level.updateNeighbourForOutputSignal(reservoir.getBlockPos(), reservoir.getBlockState().getBlock());
                }
            }
        }
    }

    // Lays the group's fluid out bottom-up again, each layer filled evenly.
    private void settle() {
        FluidResource fluid = fluid();
        long remaining = amount();
        for (List<ReservoirBlockEntity> layer : layers) {
            long take = Math.min(remaining, (long) layer.size() * ReservoirBlockEntity.CAPACITY);
            remaining -= take;
            int base = (int) (take / layer.size());
            int extra = (int) (take % layer.size());
            for (int i = 0; i < layer.size(); i++) {
                layer.get(i).setOwn(fluid, base + (i < extra ? 1 : 0));
            }
        }
    }

    // --- The group as one tank ---

    private final class Handler implements ResourceHandler<FluidResource> {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            return fluid();
        }

        @Override
        public long getAmountAsLong(int index) {
            return amount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return capacity();
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return ReservoirBlockEntity.accepts(resource);
        }

        // Into the lowest layer with room, spread evenly over its blocks (least full first), then the next layer up.
        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            if (index != 0 || amount <= 0 || !ReservoirBlockEntity.accepts(resource)) {
                return 0;
            }
            FluidResource fluid = fluid();
            if (!fluid.isEmpty() && !fluid.equals(resource)) {
                return 0;
            }
            int inserted = 0;
            for (List<ReservoirBlockEntity> layer : layers) {
                if (inserted >= amount) {
                    break;
                }
                List<ReservoirBlockEntity> open = new ArrayList<>(layer);
                open.sort(Comparator.comparingInt(ReservoirBlockEntity::ownAmount));
                for (int i = 0; i < open.size() && inserted < amount; i++) {
                    int left = amount - inserted;
                    int share = Mth.positiveCeilDiv(left, open.size() - i);
                    inserted += open.get(i).tank().insert(0, resource, share, transaction);
                }
            }
            return inserted;
        }

        // From the highest layer that holds any, evenly over its blocks (fullest first), then the next layer down.
        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            if (index != 0 || amount <= 0 || resource.isEmpty() || !resource.equals(fluid())) {
                return 0;
            }
            int extracted = 0;
            for (List<ReservoirBlockEntity> layer : layers.reversed()) {
                if (extracted >= amount) {
                    break;
                }
                List<ReservoirBlockEntity> full = new ArrayList<>(layer);
                full.sort(Comparator.comparingInt(ReservoirBlockEntity::ownAmount).reversed());
                for (int i = 0; i < full.size() && extracted < amount; i++) {
                    int left = amount - extracted;
                    int share = Mth.positiveCeilDiv(left, full.size() - i);
                    extracted += full.get(i).tank().extract(0, resource, share, transaction);
                }
            }
            return extracted;
        }
    }
}
