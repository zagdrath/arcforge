/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.fusion.FusionExhaustPool;
import net.zagdrath.arcforge.fusion.Tritium;

// The fusion tier's server-wide upkeep: once a minute (on the Overworld's clock), the tritium in the world's Fusion
// Exhaust decays to Helium-3 (see FusionExhaustPool).
@EventBusSubscriber(modid = Arcforge.MODID)
public final class FusionEvents {
    private static final int DECAY_INTERVAL = 1200;

    private FusionEvents() {}

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD && level.getGameTime() % DECAY_INTERVAL == 0) {
            FusionExhaustPool.get(level.getServer()).decay(Tritium.decayFraction(DECAY_INTERVAL));
        }
    }
}
