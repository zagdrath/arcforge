/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.List;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.zagdrath.arcforge.Arcforge;

// Chunk tickets: the Arc Quarry keeps its own chunk and the one it's working in loaded (with chunkLoading on). A
// quarry takes its tickets again as it works, so any left from before a restart are dropped when the level loads.
public final class ModChunkLoading {
    public static final TicketController QUARRY = new TicketController(Identifier.fromNamespaceAndPath(Arcforge.MODID, "arc_quarry"),
            (level, tickets) -> List.copyOf(tickets.getBlockTickets().keySet()).forEach(tickets::removeAllTickets));

    private ModChunkLoading() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterTicketControllersEvent.class, event -> event.register(QUARRY));
    }
}
