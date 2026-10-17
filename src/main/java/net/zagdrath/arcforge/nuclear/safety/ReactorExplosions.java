/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear.safety;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlocks;

// A reactor's explosions (a burst vessel, a prompt-critical excursion, a Hydrogen burn), sized by what they let go:
//  - radius = scale x cbrt(energy / 1,000) for a vessel (energy in HU; see ReactorSim.storedEnergy) and hydrogenScale x
//    cbrt(mB of Hydrogen) for a burn, never more than nuclear.failures.explosionMaxRadius.
//  - Inside a sealed containment, an explosion no bigger than containmentStrength is taken by its walls (Containment
//    Concrete and the Steel Liner shrug off any blast); a bigger one breaches them, blowing a hole through the wall
//    nearest where it went off.
//  - nuclear.failures.explosionsBreakBlocks off: they still hurt what's near, but break no blocks.
public final class ReactorExplosions {
    private ReactorExplosions() {}

    public static double vesselRadius(double energy) {
        return Math.min(ArcforgeConfig.EXPLOSION_MAX_RADIUS.getAsDouble(),
                ArcforgeConfig.EXPLOSION_SCALE.getAsDouble() * Math.cbrt(Math.max(0.0, energy) / 1_000.0));
    }

    public static double hydrogenRadius(double hydrogen) {
        return Math.min(ArcforgeConfig.EXPLOSION_MAX_RADIUS.getAsDouble(),
                ArcforgeConfig.HYDROGEN_EXPLOSION_SCALE.getAsDouble() * Math.cbrt(Math.max(0.0, hydrogen)));
    }

    // An explosion at a point, in a containment (or not). Returns whether the containment held it.
    public static boolean explode(ServerLevel level, Vec3 at, double radius, @Nullable Containment containment) {
        if (radius < 0.5) {
            return containment != null && containment.sealed();
        }
        boolean breaks = ArcforgeConfig.EXPLOSIONS_BREAK_BLOCKS.getAsBoolean();
        level.explode(null, at.x, at.y, at.z, (float) radius, breaks ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        if (containment == null || !containment.sealed()) {
            return false;
        }
        double strength = ArcforgeConfig.CONTAINMENT_STRENGTH.getAsDouble();
        if (radius <= strength) {
            return true;
        }
        if (breaks) {
            breach(level, at, radius, (radius - strength) * 0.5 + 1.0);
        }
        return false;
    }

    // Blows a hole of this radius through the containment wall nearest the explosion (found along a few rays out
    // from it, up to its radius).
    private static void breach(ServerLevel level, Vec3 at, double reach, double hole) {
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (int ray = 0; ray < 12; ray++) {
            double yaw = random.nextDouble() * Math.PI * 2.0, pitch = (random.nextDouble() - 0.3) * Math.PI * 0.8;
            double dx = Math.cos(yaw) * Math.cos(pitch), dy = Math.sin(pitch), dz = Math.sin(yaw) * Math.cos(pitch);
            for (double d = 1.0; d <= reach; d += 0.5) {
                pos.set(at.x + dx * d, at.y + dy * d, at.z + dz * d);
                if (isWall(level.getBlockState(pos))) {
                    if (d < best) {
                        best = d;
                        nearest = pos.immutable();
                    }
                    break;
                }
            }
        }
        if (nearest == null) {
            return;
        }
        int r = (int) Math.ceil(hole);
        for (BlockPos each : BlockPos.betweenClosed(nearest.offset(-r, -r, -r), nearest.offset(r, r, r))) {
            if (each.distSqr(nearest) <= hole * hole && !level.getBlockState(each).is(Blocks.BEDROCK)) {
                level.destroyBlock(each, false);
            }
        }
    }

    private static boolean isWall(BlockState state) {
        return state.is(ModBlocks.STEEL_LINER.get()) || state.is(ModBlocks.CONTAINMENT_CONCRETE.get()) || state.is(ModBlocks.CONTAINMENT_DOME.get())
                || state.is(ModBlocks.AIRLOCK.get()) || state.is(ModBlocks.EQUIPMENT_HATCH.get()) || state.is(ModBlocks.PENETRATION.get());
    }
}
