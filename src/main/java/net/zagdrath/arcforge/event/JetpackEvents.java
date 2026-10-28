/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.JetpackFlight;
import net.zagdrath.arcforge.item.tool.LongFallBootsItem;

// Runs each server player's Jetpack every tick (see JetpackFlight), and forgets a player's keys when they leave,
// change dimension or die.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class JetpackEvents {
    private JetpackEvents() {}

    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (LongFallBootsItem.absorb(event.getEntity(), event.getSource(), event.getAmount())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JetpackFlight.serverTick(player);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        JetpackFlight.forget(event.getEntity());
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        JetpackFlight.forget(event.getEntity());
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JetpackFlight.forget(player);
        }
    }
}
