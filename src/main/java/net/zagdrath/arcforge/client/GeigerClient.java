/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.GeigerCounterItem;
import net.zagdrath.arcforge.nuclear.RadiationDose;
import net.zagdrath.arcforge.registry.ModSounds;

// The Geiger Counter's clicks, played on the client from the player's synced dose rate (RadiationDose.EXPOSURE) while
// they hold one: random, like real counts, about BACKGROUND a second with nothing about and rising toward MAX_RATE as
// the rate climbs (half of it at HALF mSv/h), at most a few a tick.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class GeigerClient {
    private static final double BACKGROUND = 0.3, MAX_RATE = 60.0, HALF = 1.0;
    private static final int MOST_PER_TICK = 3;

    private GeigerClient() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.isPaused() || !GeigerCounterItem.isHeld(player)) {
            return;
        }
        double rate = player.getData(RadiationDose.EXPOSURE).rate();
        double perSecond = BACKGROUND + MAX_RATE * rate / (rate + HALF);
        double expected = perSecond / 20.0;
        RandomSource random = player.getRandom();
        int clicks = 0;
        // A Poisson draw by its definition (expected is small).
        double limit = Math.exp(-expected), product = random.nextDouble();
        while (product > limit && clicks < MOST_PER_TICK) {
            clicks++;
            product *= random.nextDouble();
        }
        for (int i = 0; i < clicks; i++) {
            player.level().playLocalSound(player.getX(), player.getY() + 1.0, player.getZ(), ModSounds.GEIGER_CLICK.get(), SoundSource.PLAYERS,
                    0.35F, 0.9F + random.nextFloat() * 0.2F, false);
        }
    }
}
