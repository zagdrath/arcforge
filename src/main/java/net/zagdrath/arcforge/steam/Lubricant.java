/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModFluids;

// Heavy Oil as the Steam Turbine Array's lubricant: while its lubricant tank isn't empty it makes more FE
// and spins up faster.
public final class Lubricant {
    private Lubricant() {}

    public static boolean isLubricant(FluidResource resource) {
        return resource.is(ModFluids.HEAVY_OIL.get());
    }

    // The output multiplier while lubricated.
    public static double bonus() {
        return 1.0 + ArcforgeConfig.LUBRICANT_OUTPUT_BONUS.getAsDouble();
    }
}
