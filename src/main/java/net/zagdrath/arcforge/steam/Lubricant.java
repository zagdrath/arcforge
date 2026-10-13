/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModFluids;

// The Steam and Gas Turbine Arrays' lubricants: any fluid in #arcforge:lubricants (Heavy Oil, Seed Oil and Lubricant).
// While a turbine's lubricant tank isn't empty it makes more FE (lubricant.outputBonus, or lubricantOutputBonus with
// the refined Lubricant) and spins up faster. The tank holds one fluid at a time.
public final class Lubricant {
    public static final TagKey<Fluid> TAG = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Arcforge.MODID, "lubricants"));

    private Lubricant() {}

    public static boolean isLubricant(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid().defaultFluidState().is(TAG);
    }

    // The output multiplier with this lubricant in the tank (1 with none).
    public static double bonus(FluidResource resource) {
        return 1.0 + share(resource);
    }

    // The extra share of output this lubricant gives (0 with none).
    public static double share(FluidResource resource) {
        if (!isLubricant(resource)) {
            return 0.0;
        }
        return resource.getFluid() == ModFluids.LUBRICANT.get() ? ArcforgeConfig.LUBRICANT_PREMIUM_BONUS.getAsDouble()
                : ArcforgeConfig.LUBRICANT_OUTPUT_BONUS.getAsDouble();
    }

    // The output multiplier with the best lubricant, for the most a turbine can make.
    public static double best() {
        return 1.0 + Math.max(ArcforgeConfig.LUBRICANT_OUTPUT_BONUS.getAsDouble(), ArcforgeConfig.LUBRICANT_PREMIUM_BONUS.getAsDouble());
    }
}
