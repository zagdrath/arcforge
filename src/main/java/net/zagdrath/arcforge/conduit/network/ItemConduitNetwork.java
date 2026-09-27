/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
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

// Every few ticks (per tier) pulls one stack from an output side and sends it as a packet towards an
// input side that will accept it. Packets travel conduit by conduit along precomputed routes.
// A destination that refuses the items makes the packet reroute; items are only dropped into the
// world when nothing in the network can take them.
public class ItemConduitNetwork extends ConduitNetwork<ResourceHandler<ItemResource>> {
    // For each sink, the direction to leave each conduit in to get one step closer to it.
    private final Map<SinkKey, Map<BlockPos, Direction>> routes = new HashMap<>();
    private final Map<SinkKey, Endpoint<ResourceHandler<ItemResource>>> sinksByKey = new HashMap<>();
    private int sourceCursor;

    public ItemConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        super(level, members, memberSet, tier, Capabilities.Item.BLOCK);
    }

    private record SinkKey(BlockPos conduit, Direction side) {}

    @Override
    protected void onCreated() {
        for (Endpoint<ResourceHandler<ItemResource>> sink : sinks) {
            SinkKey key = new SinkKey(sink.conduit(), sink.side());
            sinksByKey.put(key, sink);
            routes.put(key, buildRoute(sink));
        }
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit) {
                conduit.setItemSpeed(tier.itemSpeed());
            }
        }
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
        if (!sources.isEmpty() && !sinks.isEmpty() && gameTime % tier.ticksPerItemOperation() == 0) {
            extract();
        }
        movePackets();
    }

    // --- Pulling items into the network ---

    private void extract() {
        for (int attempt = 0; attempt < sources.size(); attempt++) {
            Endpoint<ResourceHandler<ItemResource>> source = sources.get(sourceCursor++ % sources.size());
            if (extractFrom(source)) {
                return;
            }
        }
    }

    private boolean extractFrom(Endpoint<ResourceHandler<ItemResource>> source) {
        ResourceHandler<ItemResource> handler = source.handler();
        if (handler == null || !(level.getBlockEntity(source.conduit()) instanceof ConduitBlockEntity conduit)) {
            return false;
        }
        for (int index = 0; index < handler.size(); index++) {
            ItemResource resource = handler.getResource(index);
            int available = Math.min(handler.getAmountAsInt(index), tier.itemsPerOperation());
            if (resource.isEmpty() || available <= 0) {
                continue;
            }
            Target target = findTarget(resource, available, source.conduit(), source.machine());
            if (target == null) {
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
                return true;
            }
        }
        return false;
    }

    private record Target(SinkKey key, Map<BlockPos, Direction> route, int accepted) {}

    // The first sink (round-robin) reachable from `from` that will accept some of the items.
    private @Nullable Target findTarget(ItemResource resource, int amount, BlockPos from, @Nullable BlockPos excludeMachine) {
        for (Endpoint<ResourceHandler<ItemResource>> sink : sinksInTurn()) {
            if (sink.machine().equals(excludeMachine)) {
                continue;
            }
            SinkKey key = new SinkKey(sink.conduit(), sink.side());
            Map<BlockPos, Direction> route = routes.get(key);
            ResourceHandler<ItemResource> handler = sink.handler();
            if (route == null || !route.containsKey(from) || handler == null) {
                continue;
            }
            int accepted;
            try (Transaction simulation = Transaction.openRoot()) {
                accepted = handler.insert(resource, amount, simulation);
            }
            if (accepted > 0) {
                return new Target(key, route, accepted);
            }
        }
        return null;
    }

    // --- Moving packets ---

    private record Handoff(ConduitBlockEntity to, ItemPacket packet) {}

    private void movePackets() {
        List<Handoff> handoffs = new ArrayList<>();
        for (BlockPos pos : members) {
            if (!(level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit) || conduit.getPackets().isEmpty()) {
                continue;
            }
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
        if (handler == null) {
            return stack;
        }
        int inserted;
        try (Transaction tx = Transaction.openRoot()) {
            inserted = handler.insert(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
        }
        return stack.copyWithCount(stack.getCount() - inserted);
    }

    // Sends the packet back through this conduit towards another sink, or drops it if nothing can take it.
    private boolean reroute(BlockPos pos, ItemPacket packet) {
        Target target = findTarget(ItemResource.of(packet.stack), packet.stack.getCount(), pos, null);
        if (target == null) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, packet.stack);
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
