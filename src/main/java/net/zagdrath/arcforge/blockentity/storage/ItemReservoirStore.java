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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.block.storage.ItemReservoirBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// Item Reservoir blocks that touch on a face make one store when together they fill a solid box: a cross-section 1 to
// itemReservoir.maxWidth blocks wide and tall, and a length (its longest side) up to itemReservoir.lengthRatio times the
// cross-section's wider side. Then every block of it is FORMED, with AXIS along its length (vertical when no side is
// longest, else the first longest of X then Z), and the store is all their slots as one inventory. Touching blocks that
// don't make such a box are each a store of their own (FORMED false). Each block keeps its own slots, so a broken block
// spills (or, wrenched, carries) exactly its share.
//
// The structure is worked out on the server when a block is placed or removed (update); a store is built lazily from any
// of its blocks (build) and shared by all of them until a block changes or unloads.
public final class ItemReservoirStore {
    // How far a structure check follows touching blocks: past the biggest box allowed, so too-big groups are seen as such.
    private static final int SEARCH_LIMIT = 4_096;

    // A valid box: its corners, and the axis along its length.
    public record Box(BlockPos min, BlockPos max, Direction.Axis axis) {
        public int size(Direction.Axis of) {
            return max.get(of) - min.get(of) + 1;
        }

        public int volume() {
            return size(Direction.Axis.X) * size(Direction.Axis.Y) * size(Direction.Axis.Z);
        }

        // "W×H×L": across, up (or the other across for a vertical store), along.
        public String describe() {
            Direction.Axis first = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            Direction.Axis second = axis == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
            return size(first) + "×" + size(second) + "×" + size(axis);
        }
    }

    private final List<ItemReservoirBlockEntity> members;
    private final @Nullable Box box;
    private final ResourceHandler<ItemResource> handler = new Handler();
    private boolean valid = true;
    private long tickedAt = Long.MIN_VALUE;
    private int comparatorSignal = -1;

    private ItemReservoirStore(List<ItemReservoirBlockEntity> members, @Nullable Box box) {
        this.members = members;
        this.box = box;
    }

    // --- The structure ---

    // The box these blocks fill, if they fill one and it's an allowed size; null otherwise.
    public static @Nullable Box validBox(Set<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }
        int[] lo = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE }, hi = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (BlockPos pos : blocks) {
            int[] p = { pos.getX(), pos.getY(), pos.getZ() };
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], p[k]);
                hi[k] = Math.max(hi[k], p[k]);
            }
        }
        int[] size = { hi[0] - lo[0] + 1, hi[1] - lo[1] + 1, hi[2] - lo[2] + 1 };
        if ((long) size[0] * size[1] * size[2] != blocks.size()) {
            return null;
        }
        Direction.Axis axis = lengthAxis(size);
        int length = size[axis.ordinal()];
        int wide = 0;
        int maxWidth = ArcforgeConfig.ITEM_RESERVOIR_MAX_WIDTH.getAsInt();
        for (Direction.Axis other : Direction.Axis.values()) {
            if (other != axis) {
                if (size[other.ordinal()] > maxWidth) {
                    return null;
                }
                wide = Math.max(wide, size[other.ordinal()]);
            }
        }
        if (length > ArcforgeConfig.ITEM_RESERVOIR_LENGTH_RATIO.getAsInt() * wide) {
            return null;
        }
        return new Box(new BlockPos(lo[0], lo[1], lo[2]), new BlockPos(hi[0], hi[1], hi[2]), axis);
    }

    // The length axis of a box this size: its longest side, Y first, then X, then Z on a tie.
    public static Direction.Axis lengthAxis(int[] size) {
        Direction.Axis best = Direction.Axis.Y;
        for (Direction.Axis axis : new Direction.Axis[] { Direction.Axis.X, Direction.Axis.Z }) {
            if (size[axis.ordinal()] > size[best.ordinal()]) {
                best = axis;
            }
        }
        return best;
    }

    // The blocks of this kind touching start on a face, start included (up to limit).
    static Set<BlockPos> component(BlockGetter level, BlockPos start, Block block, int limit, boolean formedOnly) {
        Set<BlockPos> found = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        found.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && found.size() < limit) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (found.contains(next) || level instanceof Level loaded && !loaded.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.is(block) && (!formedOnly || state.getValue(ItemReservoirBlock.FORMED))) {
                    found.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    // A block was placed at pos, or the one there removed: works out again whether the blocks touching it make a store,
    // setting FORMED and AXIS on each, and drops the stores built before.
    public static void update(Level level, BlockPos pos, Block block) {
        Set<BlockPos> done = new HashSet<>();
        List<BlockPos> starts = new ArrayList<>();
        starts.add(pos);
        for (Direction direction : Direction.values()) {
            starts.add(pos.relative(direction));
        }
        for (BlockPos start : starts) {
            if (done.contains(start) || !level.getBlockState(start).is(block)) {
                continue;
            }
            Set<BlockPos> blocks = component(level, start, block, SEARCH_LIMIT, false);
            done.addAll(blocks);
            Box box = blocks.size() < SEARCH_LIMIT ? validBox(blocks) : null;
            for (BlockPos member : blocks) {
                BlockState state = level.getBlockState(member);
                BlockState next = state.setValue(ItemReservoirBlock.FORMED, box != null)
                        .setValue(ItemReservoirBlock.AXIS, box != null ? box.axis() : Direction.Axis.Y);
                if (next != state) {
                    level.setBlock(member, next, Block.UPDATE_CLIENTS);
                }
                if (level.getBlockEntity(member) instanceof ItemReservoirBlockEntity reservoir) {
                    reservoir.dropStore();
                }
            }
        }
    }

    // The store a block belongs to: its formed box's blocks, or the block alone.
    static ItemReservoirStore build(@Nullable Level level, ItemReservoirBlockEntity seed) {
        List<ItemReservoirBlockEntity> members = new ArrayList<>();
        Box box = null;
        BlockState state = seed.getBlockState();
        if (level != null && state.getBlock() instanceof ItemReservoirBlock && state.getValue(ItemReservoirBlock.FORMED)) {
            Set<BlockPos> blocks = component(level, seed.getBlockPos(), state.getBlock(), SEARCH_LIMIT, true);
            box = validBox(blocks);
            if (box != null) {
                for (BlockPos pos : blocks) {
                    if (level.getBlockEntity(pos) instanceof ItemReservoirBlockEntity member && !member.isRemoved()) {
                        members.add(member);
                    }
                }
                if (members.size() != blocks.size()) {
                    // Part of it isn't loaded or hasn't its block entity yet: a store of its own for now.
                    members.clear();
                    box = null;
                }
            }
        }
        if (members.isEmpty()) {
            members.add(seed);
        }
        // Slots in order along the length, then up, then across.
        Direction.Axis axis = box != null ? box.axis() : Direction.Axis.Y;
        members.sort(Comparator.<ItemReservoirBlockEntity>comparingInt(member -> member.getBlockPos().get(axis))
                .thenComparingInt(member -> member.getBlockPos().getY())
                .thenComparingInt(member -> member.getBlockPos().getX())
                .thenComparingInt(member -> member.getBlockPos().getZ()));
        ItemReservoirStore store = new ItemReservoirStore(List.copyOf(members), box);
        for (ItemReservoirBlockEntity member : store.members) {
            member.joinStore(store);
        }
        return store;
    }

    // --- The store ---

    public boolean isValid() {
        return valid;
    }

    void invalidate() {
        valid = false;
    }

    public List<ItemReservoirBlockEntity> members() {
        return members;
    }

    // The box, or null for a block that's a store of its own because it isn't part of a valid box.
    public @Nullable Box box() {
        return box;
    }

    public int blocks() {
        return members.size();
    }

    public ResourceHandler<ItemResource> handler() {
        return handler;
    }

    public int slots() {
        int slots = 0;
        for (ItemReservoirBlockEntity member : members) {
            slots += member.items().size();
        }
        return slots;
    }

    // The stack in the store's slot index (EMPTY past the end).
    public ItemStack getStack(int index) {
        for (ItemReservoirBlockEntity member : members) {
            FilteredItemHandler items = member.items();
            if (index < items.size()) {
                return items.getStack(index);
            }
            index -= items.size();
        }
        return ItemStack.EMPTY;
    }

    // Sets the stack in the store's slot index (as a player's click does), bypassing nothing but the slot's own rules.
    public void set(int index, ItemResource resource, int amount) {
        for (ItemReservoirBlockEntity member : members) {
            FilteredItemHandler items = member.items();
            if (index < items.size()) {
                items.set(index, resource, amount);
                return;
            }
            index -= items.size();
        }
    }

    // Vanilla's container formula over every slot: 0 when empty, then 1-15 by how full the slots are.
    public int comparatorSignal() {
        float fill = 0.0F;
        int slots = 0;
        boolean any = false;
        for (ItemReservoirBlockEntity member : members) {
            FilteredItemHandler items = member.items();
            for (int slot = 0; slot < items.size(); slot++) {
                ItemStack stack = items.getStack(slot);
                if (!stack.isEmpty()) {
                    fill += (float) stack.getCount() / stack.getMaxStackSize();
                    any = true;
                }
            }
            slots += items.size();
        }
        if (!any || slots == 0) {
            return 0;
        }
        return Mth.lerpDiscrete(fill / slots, 0, 15);
    }

    // How full the store is, 0-1 (by stacks, as the comparator counts them).
    public float fullness() {
        float fill = 0.0F;
        int slots = 0;
        for (ItemReservoirBlockEntity member : members) {
            FilteredItemHandler items = member.items();
            for (int slot = 0; slot < items.size(); slot++) {
                ItemStack stack = items.getStack(slot);
                if (!stack.isEmpty()) {
                    fill += (float) stack.getCount() / stack.getMaxStackSize();
                }
            }
            slots += items.size();
        }
        return slots == 0 ? 0.0F : fill / slots;
    }

    // Once a tick, from whichever member ticks first: comparators next to any block hear of a change.
    void tick(ServerLevel level) {
        if (tickedAt == level.getGameTime()) {
            return;
        }
        tickedAt = level.getGameTime();
        int signal = comparatorSignal();
        if (signal != comparatorSignal) {
            comparatorSignal = signal;
            for (ItemReservoirBlockEntity member : members) {
                if (!member.isRemoved()) {
                    level.updateNeighbourForOutputSignal(member.getBlockPos(), member.getBlockState().getBlock());
                }
            }
        }
    }

    // --- The store as one inventory: each member's slots in turn ---

    private final class Handler implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return slots();
        }

        private @Nullable FilteredItemHandler owner(int[] index) {
            for (ItemReservoirBlockEntity member : members) {
                FilteredItemHandler items = member.items();
                if (index[0] < items.size()) {
                    return member.isRemoved() ? null : items;
                }
                index[0] -= items.size();
            }
            return null;
        }

        @Override
        public ItemResource getResource(int index) {
            int[] at = { index };
            FilteredItemHandler items = owner(at);
            return items != null ? items.getResource(at[0]) : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            int[] at = { index };
            FilteredItemHandler items = owner(at);
            return items != null ? items.getAmountAsLong(at[0]) : 0;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            int[] at = { index };
            FilteredItemHandler items = owner(at);
            return items != null ? items.getCapacityAsLong(at[0], resource) : 0;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            int[] at = { index };
            FilteredItemHandler items = owner(at);
            return items != null && items.isValid(at[0], resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            int[] at = { index };
            FilteredItemHandler items = owner(at);
            return items != null ? items.insert(at[0], resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            int[] at = { index };
            FilteredItemHandler items = owner(at);
            return items != null ? items.extract(at[0], resource, amount, transaction) : 0;
        }
    }
}
