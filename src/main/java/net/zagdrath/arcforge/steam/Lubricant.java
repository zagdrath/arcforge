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

// The Steam and Gas Turbine Arrays' lubricants: any fluid in #arcforge:lubricants (Heavy Oil and Seed Oil). While a
// turbine's lubricant tank isn't empty it makes more FE and spins up faster. The tank holds one fluid at a time.
public final class Lubricant {
    public static final TagKey<Fluid> TAG = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Arcforge.MODID, "lubricants"));

    private Lubricant() {}

    public static boolean isLubricant(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid().defaultFluidState().is(TAG);
    }

    // The output multiplier while lubricated.
    public static double bonus() {
        return 1.0 + ArcforgeConfig.LUBRICANT_OUTPUT_BONUS.getAsDouble();
    }
}
