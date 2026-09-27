/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;

// Drives conduit networks once per server level tick.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class ConduitNetworkEvents {
    private ConduitNetworkEvents() {}

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ConduitNetworkManager.get(level).tick();
        }
    }

    @SubscribeEvent
    static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ConduitNetworkManager.remove(level);
        }
    }
}
