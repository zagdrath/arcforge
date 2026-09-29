/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.FoundrySuit;

// The Foundry Suit at work: it cuts or cancels fire, hot-block and (while its shield lasts) lava damage, and runs
// the full suit's fire immunity and lava shield every tick (see FoundrySuit).
@EventBusSubscriber(modid = Arcforge.MODID)
public final class FoundryEvents {
    private FoundryEvents() {}

    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        float adjusted = FoundrySuit.adjust(event.getEntity(), event.getSource(), event.getAmount());
        if (adjusted <= 0.0F) {
            event.setCanceled(true);
            event.getEntity().setRemainingFireTicks(0);
        } else if (adjusted != event.getAmount()) {
            event.setAmount(adjusted);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FoundrySuit.tick(player, player.isInLava());
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FoundrySuit.forget(event.getEntity());
    }
}
