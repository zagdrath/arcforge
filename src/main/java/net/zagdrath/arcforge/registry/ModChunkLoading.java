/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.chunkloading.ChunkLoaders;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// Chunk tickets: the Arc Quarry keeps its own chunk and the one it's working in loaded (with chunkLoading on). A
// quarry takes its tickets again as it works, so any left from before a restart are dropped when the level loads.
// Chunk Loaders keep theirs across restarts, as long as ChunkLoaders still lists the loader (and, with
// logistics.chunkLoader.requireOwnerOnline, not at all: nobody is online when the level loads, so they wait for their owner).
public final class ModChunkLoading {
    public static final TicketController QUARRY = new TicketController(Identifier.fromNamespaceAndPath(Arcforge.MODID, "arc_quarry"),
            (level, tickets) -> List.copyOf(tickets.getBlockTickets().keySet()).forEach(tickets::removeAllTickets));
    public static final TicketController LOADER = new TicketController(Identifier.fromNamespaceAndPath(Arcforge.MODID, "chunk_loader"),
            (level, tickets) -> {
                ChunkLoaders loaders = ChunkLoaders.get(level.getServer());
                boolean requireOnline = ArcforgeConfig.CHUNK_LOADER_REQUIRE_ONLINE.getAsBoolean();
                for (BlockPos pos : List.copyOf(tickets.getBlockTickets().keySet())) {
                    ChunkLoaders.Entry entry = loaders.get(GlobalPos.of(level.dimension(), pos));
                    if (entry == null || requireOnline) {
                        tickets.removeAllTickets(pos);
                        if (entry != null) {
                            loaders.put(entry.pos(), entry.owner(), List.of());
                        }
                    }
                }
            });

    private ModChunkLoading() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterTicketControllersEvent.class, event -> {
            event.register(QUARRY);
            event.register(LOADER);
        });
    }
}
