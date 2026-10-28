/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */
package net.zagdrath.arcforge.nuclear;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// Visual warning at the same sources used for exposure, including radioactive contents inside machines/conduits.
// Scan only loaded chunks near players, once a second, with a fixed particle budget for busy factories.
public final class RadiationParticles {
    private static final DustParticleOptions GREEN = new DustParticleOptions(0x65D94B, 0.7F);
    private static final int RADIUS = 24, BUDGET = 256;

    private RadiationParticles() {}

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0 || !ArcforgeConfig.RADIATION_PARTICLES.getAsBoolean()) {
            return;
        }
        Set<Long> chunks = new HashSet<>();
        Set<Integer> items = new HashSet<>();
        int remaining = BUDGET;
        for (var player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            for (int cx = (player.getBlockX() - RADIUS) >> 4; cx <= (player.getBlockX() + RADIUS) >> 4; cx++) {
                for (int cz = (player.getBlockZ() - RADIUS) >> 4; cz <= (player.getBlockZ() + RADIUS) >> 4; cz++) {
                    if (!chunks.add(ChunkPos.pack(cx, cz))) {
                        continue;
                    }
                    var chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (var source : RadiationField.sources(level, chunk)) {
                        if (source.rate() > 0 && remaining-- > 0) {
                            emit(level, source.x(), source.y(), source.z());
                        }
                    }
                }
            }
            if (RadiationField.carried(player) > 0 && remaining-- > 0) {
                emit(level, player.getX(), player.getY() + 0.8, player.getZ());
            }
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(RADIUS))) {
                if (items.add(item.getId()) && RadiationField.rate(item.getItem()) > 0 && remaining-- > 0) {
                    emit(level, item.getX(), item.getY() + 0.2, item.getZ());
                }
            }
            if (remaining <= 0) {
                return;
            }
        }
    }

    private static void emit(ServerLevel level, double x, double y, double z) {
        // Place on the outside of a block rather than burying the warning in its opaque center.
        double angle = level.getRandom().nextDouble() * Math.PI * 2;
        level.sendParticles(GREEN, x + Math.cos(angle) * 0.65, y + level.getRandom().nextDouble() * 0.7,
                z + Math.sin(angle) * 0.65, 2, 0.12, 0.2, 0.12, 0.01);
    }
}
