/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.steam.BoilerCore;
import net.zagdrath.arcforge.steam.SteamGrade;

// A boiling Fission Reactor's Contaminated Steam in the Steam Turbine Array: it runs as High-Pressure Steam. It isn't a
// steam grade itself (boilers never make it, and the public API's grades stay as they are); the turbine holds it, so a
// turbine running on it is a little radioactive while it does (see the radioactivity data map).
public final class ReactorSteam {
    private ReactorSteam() {}

    public static boolean isContaminated(FluidResource resource) {
        return !resource.isEmpty() && resource.is(ModFluids.CONTAMINATED_STEAM.get());
    }

    // What the Steam Turbine Array takes: any steam grade, or Contaminated Steam.
    public static boolean isTurbineSteam(FluidResource resource) {
        return BoilerCore.isSteam(resource) || isContaminated(resource);
    }

    // The grade it runs as.
    public static @Nullable SteamGrade turbineGrade(FluidResource resource) {
        return isContaminated(resource) ? SteamGrade.HIGH_PRESSURE : SteamGrade.of(resource);
    }
}
