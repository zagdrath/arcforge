/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.blockentity.fusion.FusionReactorBlockEntity;
import net.zagdrath.arcforge.fusion.TokamakStructure;

// The burning plasma's particles, seen through a Fusion Reactor's viewports: purple motes through the torus (bluer
// lilac on D-He3) and bright sparks streaming round the ring, more of them the hotter it burns. Spawned each client tick
// by a formed controller (see FusionReactorBlockEntity.clientHook), only while a player is near enough to see them.
public final class FusionPlasmaParticles {
    private static final double RANGE = 48.0;
    // The plasma's shape as TokamakMesh draws it: its minor radius of the chamber's half-width, and its elongation.
    private static final double FILL = 0.78, KAPPA = 1.7;
    private static final DustParticleOptions[] DT = { new DustParticleOptions(0xB04CFF, 0.9F), new DustParticleOptions(0xD98CFF, 0.6F) };
    private static final DustParticleOptions[] DHE3 = { new DustParticleOptions(0x8C7CFF, 0.9F), new DustParticleOptions(0xB8B0FF, 0.6F) };

    private FusionPlasmaParticles() {}

    public static void clientTick(FusionReactorBlockEntity reactor) {
        Level level = reactor.getLevel();
        TokamakStructure.Shape shape = reactor.getShape();
        int glow = reactor.getGlow();
        if (level == null || shape == null || !reactor.isAlive() || glow <= 0) {
            return;
        }
        BlockPos axis = shape.axis();
        double cx = axis.getX() + 0.5, cy = axis.getY() + 0.5, cz = axis.getZ() + 0.5;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.distanceToSqr(cx, cy, cz) > RANGE * RANGE) {
            return;
        }
        // The chamber runs from 1.5 blocks out from the axis to half a block inside the blanket (see TokamakMesh).
        double major = (shape.radius() + 1.0) / 2.0, minor = (shape.radius() - 2.0) / 2.0 * FILL;
        if (minor <= 0.0) {
            return;
        }
        RandomSource random = level.getRandom();
        DustParticleOptions[] motes = reactor.getFuelMode() == FusionReactorBlockEntity.FuelMode.DHE3 ? DHE3 : DT;
        // More the hotter it burns, and the bigger the ring (an R3's is the smallest).
        int count = Math.round((3 + glow / 2.5F) * Math.max(1.0F, shape.radius() / 3.0F));
        for (int i = 0; i < count; i++) {
            double phi = random.nextDouble() * Math.PI * 2.0, theta = random.nextDouble() * Math.PI * 2.0;
            double rho = minor * Math.sqrt(random.nextDouble());
            double r = major + rho * Math.cos(theta);
            double x = cx + r * Math.cos(phi), y = cy + KAPPA * rho * Math.sin(theta), z = cz + r * Math.sin(phi);
            float pick = random.nextFloat();
            if (pick < 0.6F) {
                level.addParticle(motes[random.nextInt(motes.length)], x, y, z, 0.0, 0.0, 0.0);
            } else if (pick < 0.92F) {
                // Round the ring, the way the plasma current runs.
                double speed = 0.05 + 0.1 * random.nextDouble();
                level.addParticle(ParticleTypes.END_ROD, x, y, z, -Math.sin(phi) * speed, 0.0, Math.cos(phi) * speed);
            } else {
                level.addParticle(ParticleTypes.WITCH, x, y, z, 0.0, 0.0, 0.0);
            }
        }
    }
}
