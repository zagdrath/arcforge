/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.safety.Fallout;

// The air over contaminated ground, like Mekanism's: in every chunk round the player whose fallout (Fallout.GROUND,
// synced with the chunk) is above Fallout.CLEAN, green motes (vanilla dust, tinted radiation green, a few sizes) hang
// and drift through the whole chunk, more the more contaminated it is; over heavily contaminated ground (ASH_LEVEL
// mSv/h) grey ash falls too. Only in loaded chunks within RADIUS chunks, and none while paused or with
// nuclear.failures.falloutParticles off.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class FalloutParticles {
    private static final int RADIUS = 2;
    // Motes a tick in a chunk: one per MOTE_STEP mSv/h, at least one, at most MOST.
    private static final double MOTE_STEP = 4.0, ASH_LEVEL = 20.0;
    private static final int MOST = 6;
    private static final DustParticleOptions[] MOTES = { new DustParticleOptions(0x7CFF4A, 0.6F), new DustParticleOptions(0x5FE03A, 0.9F),
            new DustParticleOptions(0xA8FF7A, 0.45F) };

    private FalloutParticles() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused() || !ArcforgeConfig.FALLOUT_PARTICLES.getAsBoolean()) {
            return;
        }
        long now = level.getGameTime();
        int pcx = player.blockPosition().getX() >> 4, pcz = player.blockPosition().getZ() >> 4;
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int cx = pcx - RADIUS; cx <= pcx + RADIUS; cx++) {
            for (int cz = pcz - RADIUS; cz <= pcz + RADIUS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null || !chunk.hasData(Fallout.GROUND)) {
                    continue;
                }
                double total = chunk.getData(Fallout.GROUND).at(now).total();
                if (total < Fallout.CLEAN) {
                    continue;
                }
                int motes = (int) Math.min(MOST, Math.max(1.0, total / MOTE_STEP));
                // A faint chunk only sometimes shows a mote.
                if (total < MOTE_STEP && random.nextDouble() > total / MOTE_STEP) {
                    continue;
                }
                for (int i = 0; i < motes; i++) {
                    int x = (cx << 4) + random.nextInt(16), z = (cz << 4) + random.nextInt(16);
                    int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    // Through the air over the ground, round the player's height (and not inside blocks).
                    double y = Math.max(ground, player.getY() - 6.0) + random.nextDouble() * 12.0;
                    at.set(x, (int) Math.floor(y), z);
                    if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                        continue;
                    }
                    DustParticleOptions mote = MOTES[random.nextInt(MOTES.length)];
                    level.addParticle(mote, x + random.nextDouble(), y, z + random.nextDouble(), 0.0, 0.01, 0.0);
                }
                if (total >= ASH_LEVEL && random.nextInt(3) == 0) {
                    int x = (cx << 4) + random.nextInt(16), z = (cz << 4) + random.nextInt(16);
                    level.addParticle(ParticleTypes.ASH, x + random.nextDouble(), player.getY() + 6.0 + random.nextDouble() * 8.0, z + random.nextDouble(),
                            0.0, 0.0, 0.0);
                }
            }
        }
    }
}
