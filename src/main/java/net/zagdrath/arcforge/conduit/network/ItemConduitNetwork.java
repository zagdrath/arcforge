/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.conduit.item.ItemPacket;

// Every few ticks (per tier) pulls up to the tier's items per operation from an output side, slot after slot until that's
// used up, and sends each slot's share as a packet towards an input side that will accept it. Packets travel conduit by conduit along precomputed routes.
// When nothing has room for the items they are pulled into the source conduit's storage anyway (up to
// ConduitTier.ITEM_STORAGE_SLOTS stacks) and sent on once a destination has room, but only if some destination's filter
// lets them through (or there is no destination yet): items every destination's filter rejects stay in the machine. A destination that
// refuses a packet makes it reroute, and if nothing can take it the items are stored where they are.
// Conduit Filters are checked live: a destination whose filter rejects the items is skipped (the rest keep
// their round-robin turns), and a source's extract filter leaves what it rejects in the machine.
// A source that yields nothing (empty, or nowhere takes what it holds) is skipped for a few ticks (see
// ConduitNetwork.Endpoint.backOff), and which sinks can be reached from where is worked out once per network.
public class ItemConduitNetwork extends ConduitNetwork<ResourceHandler<ItemResource>> {
    // For each sink, the direction to leave each conduit in to get one step closer to it.
    private final Map<SinkKey, Map<BlockPos, Direction>> routes = new HashMap<>();
    private final Map<SinkKey, Endpoint<ResourceHandler<ItemResource>>> sinksByKey = new HashMap<>();
    // The same, by index in sinks.
    private final List<SinkKey> sinkKeys = new ArrayList<>();
    private final List<Map<BlockPos, Direction>> sinkRoutes = new ArrayList<>();
    // The sinks reachable from each origin, as indexes into sinks in order (see reachableSinks). Any change to the
    // conduits or their sides rebuilds the network, and with it this.
    private final Map<Origin, int[]> reachable = new HashMap<>();
    private int sourceCursor;
    private int storageCursor;

    public ItemConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        super(level, members, memberSet, tier, Capabilities.Item.BLOCK);
    }

    private record SinkKey(BlockPos conduit, Direction side) {}

    // Where items leave from: a conduit, and the machine they came out of (never sent back into it), if any.
    private record Origin(BlockPos from, @Nullable BlockPos excludeMachine) {}

    @Override
    protected void onCreated() {
        for (Endpoint<ResourceHandler<ItemResource>> sink : sinks) {
            SinkKey key = new SinkKey(sink.conduit(), sink.side());
            Map<BlockPos, Direction> route = buildRoute(sink);
            sinksByKey.put(key, sink);
            routes.put(key, route);
            sinkKeys.add(key);
            sinkRoutes.add(route);
        }
        for (ConduitBlockEntity conduit : conduits) {
            if (conduit != null) {
                conduit.setItemSpeed(tier.itemSpeed());
            }
        }
    }

    // The sinks items leaving `from` can reach, leaving out excludeMachine, in the order of sinks.
    private int[] reachableSinks(BlockPos from, @Nullable BlockPos excludeMachine) {
        return reachable.computeIfAbsent(new Origin(from, excludeMachine), origin -> {
            int[] found = new int[sinks.size()];
            int count = 0;
            for (int i = 0; i < sinks.size(); i++) {
                if (!sinks.get(i).machine().equals(excludeMachine) && sinkRoutes.get(i).containsKey(from)) {
                    found[count++] = i;
                }
            }
            return Arrays.copyOf(found, count);
        });
    }

    // Breadth-first search outwards from the sink's conduit.
    private Map<BlockPos, Direction> buildRoute(Endpoint<ResourceHandler<ItemResource>> sink) {
        Map<BlockPos, Direction> nextHop = new HashMap<>();
        nextHop.put(sink.conduit(), sink.side());
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(sink.conduit());
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            var state = level.getBlockState(current);
            for (Direction side : Direction.values()) {
                BlockPos next = current.relative(side);
                if (ConduitBlock.mode(state, side) == ConnectionMode.PIPE && memberSet.contains(next) && !nextHop.containsKey(next)) {
                    nextHop.put(next, side.getOpposite());
                    queue.add(next);
                }
            }
        }
        return nextHop;
    }

    @Override
    public void tick(long gameTime) {
        if (gameTime % tier.ticksPerItemOperation() == 0) {
            if (!sinks.isEmpty()) {
                dispatchStored();
            }
            if (!sources.isEmpty()) {
                extract(gameTime);
            }
        }
        movePackets();
    }

    // --- Pulling items into the network ---

    // Sources take turns; one backing off is passed over like one with nothing to give.
    private void extract(long gameTime) {
        for (int attempt = 0; attempt < sources.size(); attempt++) {
            Endpoint<ResourceHandler<ItemResource>> source = sources.get(sourceCursor++ % sources.size());
            if (source.isBackingOff(gameTime, 0)) {
                continue;
            }
            if (extractFrom(source)) {
                source.resetBackoff();
                return;
            }
            source.backOff(gameTime, 0);
        }
    }

    private boolean extractFrom(Endpoint<ResourceHandler<ItemResource>> source) {
        ResourceHandler<ItemResource> handler = source.handler();
        ConduitBlockEntity conduit = source.conduitEntity();
        if (handler == null || conduit == null) {
            return false;
        }
        // The operation's items may come from several slots: a machine that tops up its first output slot as it works
        // (a Block Breaker) would otherwise only ever have that slot's few new items taken, never its full slots.
        int budget = tier.itemsPerOperation();
        boolean any = false;
        for (int index = 0; index < handler.size() && budget > 0; index++) {
            ItemResource resource = handler.getResource(index);
            int available = Math.min(handler.getAmountAsInt(index), budget);
            if (resource.isEmpty() || available <= 0) {
                continue;
            }
            // An extract filter on the source side keeps what it rejects in the machine.
            if (!filterAllows(source, resource.toStack(1), false)) {
                continue;
            }
            Target target = findTarget(resource, available, source.conduit(), source.machine());
            if (target == null) {
                // Nowhere has room: keep it in the conduit until somewhere does, unless no destination would ever take it.
                if (!anySinkAllows(resource, source.conduit(), source.machine())) {
                    continue;
                }
                int space = Math.min(available, conduit.storageSpaceFor(resource.toStack(1)));
                int stored = space > 0 ? extractInto(handler, index, resource, space, conduit) : 0;
                budget -= stored;
                any |= stored > 0;
                continue;
            }
            int extracted;
            try (Transaction tx = Transaction.openRoot()) {
                extracted = handler.extract(index, resource, target.accepted(), tx);
                tx.commit();
            }
            if (extracted > 0) {
                Direction exit = target.route().get(source.conduit());
                conduit.getPackets().add(new ItemPacket(resource.toStack(extracted), source.side(), exit, 0.0F,
                        target.key().conduit(), target.key().side()));
                conduit.markContentsChanged(true);
                budget -= extracted;
                any = true;
            }
        }
        return any;
    }

    // Returns how many it pulled into the conduit's storage.
    private static int extractInto(ResourceHandler<ItemResource> handler, int index, ItemResource resource, int amount, ConduitBlockEntity conduit) {
        int extracted;
        try (Transaction tx = Transaction.openRoot()) {
            extracted = handler.extract(index, resource, amount, tx);
            tx.commit();
        }
        if (extracted > 0) {
            conduit.storeItems(resource.toStack(extracted), true);
        }
        return extracted;
    }

    // Sends one stored stack (conduits taken in turn) towards a destination that will accept it.
    // The packet starts at the conduit's core, where stored items rest.
    private void dispatchStored() {
        int count = members.size();
        for (int attempt = 0; attempt < count; attempt++) {
            int index = storageCursor++ % count;
            BlockPos pos = members.get(index);
            ConduitBlockEntity conduit = conduits[index];
            if (conduit == null || conduit.getStoredItems().isEmpty()) {
                continue;
            }
            var iterator = conduit.getStoredItems().iterator();
            while (iterator.hasNext()) {
                ItemStack stack = iterator.next();
                int amount = Math.min(stack.getCount(), tier.itemsPerOperation());
                Target target = findTarget(ItemResource.of(stack), amount, pos, null);
                if (target == null) {
                    continue;
                }
                Direction exit = target.route().get(pos);
                conduit.getPackets().add(new ItemPacket(stack.copyWithCount(target.accepted()), exit.getOpposite(), exit, 0.5F,
                        target.key().conduit(), target.key().side()));
                stack.shrink(target.accepted());
                if (stack.isEmpty()) {
                    iterator.remove();
                }
                conduit.markContentsChanged(true);
                return;
            }
        }
    }

    // Whether a sink reachable from `from` (other than excludeMachine) has a filter that lets these items through, or
    // there is no such sink at all yet (the items wait in the conduits for one to be connected).
    private boolean anySinkAllows(ItemResource resource, BlockPos from, @Nullable BlockPos excludeMachine) {
        int[] candidates = reachableSinks(from, excludeMachine);
        ItemStack stack = resource.toStack(1);
        for (int index : candidates) {
            if (filterAllows(sinks.get(index), stack, true)) {
                return true;
            }
        }
        return candidates.length == 0;
    }

    private record Target(SinkKey key, Map<BlockPos, Direction> route, int accepted) {}

    // The first sink (round-robin, from the one after the last served) reachable from `from` that will accept some of
    // the items. Simulated: the items are only inserted when their packet arrives.
    private @Nullable Target findTarget(ItemResource resource, int amount, BlockPos from, @Nullable BlockPos excludeMachine) {
        int[] candidates = reachableSinks(from, excludeMachine);
        if (candidates.length == 0) {
            return null;
        }
        // The first candidate at or after nextSink, wrapping round.
        int start = Arrays.binarySearch(candidates, nextSink());
        if (start < 0) {
            start = -start - 1;
        }
        ItemStack stack = resource.toStack(1);
        for (int k = 0; k < candidates.length; k++) {
            int index = candidates[(start + k) % candidates.length];
            Endpoint<ResourceHandler<ItemResource>> sink = sinks.get(index);
            ResourceHandler<ItemResource> handler = sink.handler();
            if (handler == null || !filterAllows(sink, stack, true)) {
                continue;
            }
            int accepted;
            countInsertCall();
            try (Transaction simulation = Transaction.openRoot()) {
                accepted = handler.insert(resource, amount, simulation);
            }
            if (accepted > 0) {
                served(index);
                return new Target(sinkKeys.get(index), sinkRoutes.get(index), accepted);
            }
        }
        return null;
    }

    // --- Moving packets ---

    private record Handoff(ConduitBlockEntity to, ItemPacket packet) {}

    private void movePackets() {
        List<Handoff> handoffs = new ArrayList<>();
        for (int index = 0; index < conduits.length; index++) {
            ConduitBlockEntity conduit = conduits[index];
            if (conduit == null || conduit.getPackets().isEmpty()) {
                continue;
            }
            BlockPos pos = members.get(index);
            boolean changed = false;
            var iterator = conduit.getPackets().iterator();
            while (iterator.hasNext()) {
                ItemPacket packet = iterator.next();
                packet.progress += tier.itemSpeed();
                if (packet.progress < 1.0F) {
                    continue;
                }
                changed = true;
                if (!arrive(pos, conduit, packet, handoffs)) {
                    iterator.remove();
                }
            }
            if (changed) {
                conduit.markContentsChanged(true);
            }
        }
        for (Handoff handoff : handoffs) {
            handoff.to().getPackets().add(handoff.packet());
            handoff.to().markContentsChanged(true);
        }
    }

    // Handles a packet reaching the exit face of its conduit. Returns true if it stays in this conduit.
    private boolean arrive(BlockPos pos, ConduitBlockEntity conduit, ItemPacket packet, List<Handoff> handoffs) {
        // Delivering into the destination machine.
        if (pos.equals(packet.destinationPos) && packet.to == packet.destinationSide) {
            ItemStack remaining = deliver(new SinkKey(pos, packet.to), packet.stack);
            if (remaining.isEmpty()) {
                return false;
            }
            packet.stack = remaining;
            return reroute(pos, packet);
        }

        // Moving on to the next conduit.
        BlockPos next = pos.relative(packet.to);
        Map<BlockPos, Direction> route = packet.destinationPos == null ? null
                : routes.get(new SinkKey(packet.destinationPos, packet.destinationSide));
        if (route != null && route.containsKey(next) && memberSet.contains(next)
                && ConduitBlock.mode(level.getBlockState(pos), packet.to) == ConnectionMode.PIPE
                && level.getBlockEntity(next) instanceof ConduitBlockEntity nextConduit) {
            handoffs.add(new Handoff(nextConduit, new ItemPacket(packet.stack, packet.to.getOpposite(), route.get(next),
                    packet.progress - 1.0F, packet.destinationPos, packet.destinationSide)));
            return false;
        }

        // The route no longer exists (network changed or destination removed).
        return reroute(pos, packet);
    }

    private ItemStack deliver(SinkKey key, ItemStack stack) {
        Endpoint<ResourceHandler<ItemResource>> sink = sinksByKey.get(key);
        ResourceHandler<ItemResource> handler = sink == null ? null : sink.handler();
        // A filter changed while the packet was on its way: treated like a full destination, so it reroutes.
        if (handler == null || !filterAllows(sink, stack, true)) {
            return stack;
        }
        int inserted;
        countInsertCall();
        try (Transaction tx = Transaction.openRoot()) {
            inserted = handler.insert(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
        }
        return stack.copyWithCount(stack.getCount() - inserted);
    }

    // Sends the packet back through this conduit towards another sink, or stores it here if nothing can take it.
    private boolean reroute(BlockPos pos, ItemPacket packet) {
        Target target = findTarget(ItemResource.of(packet.stack), packet.stack.getCount(), pos, null);
        if (target == null) {
            if (level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit) {
                conduit.storeItems(packet.stack, true);
            } else {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, packet.stack);
            }
            return false;
        }
        packet.from = packet.to;
        packet.to = target.route().get(pos);
        packet.progress = 0.0F;
        packet.destinationPos = target.key().conduit();
        packet.destinationSide = target.key().side();
        return true;
    }
}
