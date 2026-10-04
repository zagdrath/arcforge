/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.chunkloading.ChunkLoaders;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.menu.logistics.QuantumTunnelMenu;

// Quantum Tunnel screens get their frequency list when they open and while they stay open; with
// logistics.chunkLoader.requireOwnerOnline, a player's Chunk Loaders let their chunks go when they log out and start again
// when they log back in.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class LogisticsEvents {
    private LogisticsEvents() {}

    @SubscribeEvent
    static void onMenuOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getContainer() instanceof QuantumTunnelMenu menu) {
            menu.sendState(player, "");
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof QuantumTunnelMenu menu) {
            menu.tickState(player);
        }
    }

    @SubscribeEvent
    static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ArcforgeConfig.CHUNK_LOADER_REQUIRE_ONLINE.getAsBoolean()) {
            ChunkLoaders.get(player.level().getServer()).wake(player.level().getServer(), player.getUUID());
        }
    }

    @SubscribeEvent
    static void onLogOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ArcforgeConfig.CHUNK_LOADER_REQUIRE_ONLINE.getAsBoolean()) {
            ChunkLoaders.get(player.level().getServer()).release(player.level().getServer(), player.getUUID());
        }
    }
}
