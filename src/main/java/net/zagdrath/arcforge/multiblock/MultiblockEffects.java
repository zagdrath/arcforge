/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.zagdrath.arcforge.Arcforge;

// Tells the player a multiblock was built correctly: a chime, and a green outline of particles
// around the whole structure that lingers for a couple of seconds.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class MultiblockEffects {
    private static final DustParticleOptions OUTLINE = new DustParticleOptions(0x55FF55, 1.0F);
    // Particles along each edge, per block of length.
    private static final int PER_BLOCK = 4;
    // The outline is redrawn this many times, this many ticks apart, so it stays visible.
    private static final int PULSES = 4;
    private static final int PULSE_INTERVAL = 10;

    private record Outline(BlockPos min, BlockPos max, long startTick) {}

    private static final Map<ServerLevel, List<Outline>> PENDING = new WeakHashMap<>();

    private MultiblockEffects() {}

    // Call when a structure has just formed (or grown); min and max are its inclusive corner blocks.
    public static void formed(ServerLevel level, BlockPos min, BlockPos max) {
        double x = (min.getX() + max.getX() + 1) / 2.0;
        double y = (min.getY() + max.getY() + 1) / 2.0;
        double z = (min.getZ() + max.getZ() + 1) / 2.0;
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.4F);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
        PENDING.computeIfAbsent(level, key -> new ArrayList<>()).add(new Outline(min, max, level.getGameTime()));
        drawOutline(level, min, max);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Outline> outlines = PENDING.get(level);
        if (outlines == null || outlines.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        outlines.removeIf(outline -> {
            long age = now - outline.startTick();
            if (age > 0 && age % PULSE_INTERVAL == 0) {
                drawOutline(level, outline.min(), outline.max());
            }
            return age >= (long) (PULSES - 1) * PULSE_INTERVAL;
        });
    }

    // Dust particles along the 12 edges of the box enclosing the blocks from min to max.
    private static void drawOutline(ServerLevel level, BlockPos min, BlockPos max) {
        double x0 = min.getX(), y0 = min.getY(), z0 = min.getZ();
        double x1 = max.getX() + 1, y1 = max.getY() + 1, z1 = max.getZ() + 1;
        for (double y : new double[] { y0, y1 }) {
            for (double z : new double[] { z0, z1 }) {
                edge(level, x0, y, z, x1, y, z);
            }
            for (double x : new double[] { x0, x1 }) {
                edge(level, x, y, z0, x, y, z1);
            }
        }
        for (double x : new double[] { x0, x1 }) {
            for (double z : new double[] { z0, z1 }) {
                edge(level, x, y0, z, x, y1, z);
            }
        }
    }

    private static void edge(ServerLevel level, double ax, double ay, double az, double bx, double by, double bz) {
        double length = Math.abs(bx - ax) + Math.abs(by - ay) + Math.abs(bz - az);
        int steps = Math.max(1, (int) Math.round(length * PER_BLOCK));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            level.sendParticles(OUTLINE, ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
