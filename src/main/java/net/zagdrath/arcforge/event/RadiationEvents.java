/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.RadiationDose;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.safety.Contamination;

// Radiation (see RadiationField and RadiationDose): each server player's dose (and the fallout they pick up, see
// Contamination) every radiation.doseCheckInterval ticks, mobs'
// (with radiation.affectMobs) eight times less often, the dose reset on death and the chunk caches dropped as chunks and
// levels unload.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class RadiationEvents {
    // Mobs are checked this many times less often than players.
    private static final int MOB_SLOWDOWN = 8;

    private RadiationEvents() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            int interval = ArcforgeConfig.RADIATION_CHECK_INTERVAL.getAsInt();
            if (player.tickCount % interval == 0) {
                Contamination.tick(level, player, interval / 20.0);
                RadiationDose.tick(level, player, interval);
            }
        }
    }

    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Mob mob && mob.level() instanceof ServerLevel level && ArcforgeConfig.RADIATION_AFFECTS_MOBS.getAsBoolean()) {
            int interval = ArcforgeConfig.RADIATION_CHECK_INTERVAL.getAsInt() * MOB_SLOWDOWN;
            // Spread over the ticks by id, so a crowd isn't all checked at once.
            if ((mob.tickCount + mob.getId()) % interval == 0) {
                RadiationDose.tick(level, mob, interval);
            }
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player player && !entity.level().isClientSide()) {
            RadiationDose.reset(player);
            Contamination.setSkin(player, 0.0);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RadiationDose.forget(event.getEntity());
    }

    @SubscribeEvent
    static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RadiationField.forget(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RadiationField.forget(level);
        }
    }
}
