/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.fusion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.safety.Fallout;
import net.zagdrath.arcforge.registry.ModFluids;

// Tritium, the fusion tier's bred fuel: weakly radioactive (a weak source outside, but it gets into things), decaying to
// Helium-3 with fusion.tritium.halfLifeDays, and slipping through single walls: a tank, cylinder or cartridge holding it
// loses fusion.tritium.leakPerMinute of it to the air round it each minute. Tritium Canisters and the Tritium Plant are
// double-walled and keep it. What gets out (a leak, a vented exhaust, a breached vessel) contaminates the ground and air
// where it's let go (see Fallout.seep: into a containment's air if it's in one), to be washed off as fallout is.
public final class Tritium {
    // Ground dose rate (mSv/h) one mB let out gives its chunk.
    public static final double RELEASE_DOSE = 0.004;

    private Tritium() {}

    public static boolean is(FluidResource resource) {
        return !resource.isEmpty() && resource.is(ModFluids.TRITIUM.get());
    }

    public static boolean is(Fluid fluid) {
        return fluid.isSame(ModFluids.TRITIUM.get());
    }

    // The share of tritium that decays in this many ticks.
    public static double decayFraction(double ticks) {
        double halfLife = ArcforgeConfig.TRITIUM_HALF_LIFE.getAsDouble() * 24_000.0;
        return ticks <= 0.0 ? 0.0 : 1.0 - Math.pow(0.5, ticks / Math.max(1.0, halfLife));
    }

    // mB a second a single-walled store holding amount of tritium loses (fractional; the caller carries the rest).
    public static double leakPerSecond(int amount) {
        return amount * ArcforgeConfig.TRITIUM_LEAK.getAsDouble() / 60.0;
    }

    // mB a single-walled store holding amount of tritium loses this second: leakPerSecond, its fraction lost now and
    // then so small amounts still get out.
    public static int leakNow(int amount, RandomSource random) {
        double perSecond = leakPerSecond(amount);
        int lost = (int) perSecond;
        if (random.nextDouble() < perSecond - lost) {
            lost++;
        }
        return Math.min(amount, lost);
    }

    // Once a second: a single-walled tank, cylinder or cartridge holding tritium lets some out at a point (see
    // leakNow). Returns what it lost.
    public static int leak(ServerLevel level, Vec3 at, FluidResource resource, int amount) {
        if (!is(resource) || amount <= 0) {
            return 0;
        }
        int lost = leakNow(amount, level.getRandom());
        release(level, at, lost);
        return lost;
    }

    // Tritium let out at a point (mB).
    public static void release(ServerLevel level, Vec3 at, double amount) {
        if (amount <= 0.0 || !ArcforgeConfig.FUSION_TRITIUM_CONTAMINATION.getAsBoolean()) {
            return;
        }
        Fallout.seep(level, BlockPos.containing(at), amount * RELEASE_DOSE);
    }
}
