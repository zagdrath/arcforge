/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.NuclearTurbineHitbox;

// Keeps the player out of the Nuclear Steam Turbine's parts deep in its core, which no collision shape can reach (see
// NuclearTurbineHitbox.overlapsDeep): after each tick, a player who has moved into one is put back where they last
// stood clear, and stops. Players move on their own client, so this runs there. Spectators pass through as they do
// everything else.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class TurbineDeepCollision {
    // How far (blocks) a last clear spot may be and still be gone back to; further, the player was moved some other way.
    private static final double RETURN_DISTANCE = 4.0;

    private static @Nullable Vec3 clear;
    private static @Nullable Level clearLevel;

    private TurbineDeepCollision() {}

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            clear = null;
            return;
        }
        Level level = player.level();
        if (!NuclearTurbineHitbox.overlapsDeep(level, player.getBoundingBox())) {
            clear = player.position();
            clearLevel = level;
            return;
        }
        Vec3 back = clear;
        if (back != null && clearLevel == level && back.distanceToSqr(player.position()) < RETURN_DISTANCE * RETURN_DISTANCE) {
            player.setPos(back);
            player.setDeltaMovement(Vec3.ZERO);
        }
    }
}
