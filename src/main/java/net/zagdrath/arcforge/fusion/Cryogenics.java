/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.fusion;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// Cryogenic liquids (#arcforge:cryogenic: Liquid Nitrogen and Liquid Helium) boil away in an ordinary tank, warmed by
// what's round it; a Cryogenic Tank (vacuum-jacketed) keeps them. A tank loses fusion.cryogenics.boilOffPerMinute of
// what it holds each minute, at least fusion.cryogenics.boilOffMinimum mB a second, so a small amount goes quickly and a
// full tank lasts a while.
public final class Cryogenics {
    public static final TagKey<Fluid> TAG = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Arcforge.MODID, "cryogenic"));

    private Cryogenics() {}

    public static boolean isCryogenic(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid().defaultFluidState().is(TAG);
    }

    public static boolean isCryogenic(Fluid fluid) {
        return fluid.defaultFluidState().is(TAG);
    }

    // mB a tank holding amount of a cryogenic liquid loses in one second.
    public static int boilOffPerSecond(int amount) {
        if (amount <= 0) {
            return 0;
        }
        double share = ArcforgeConfig.CRYOGENIC_BOIL_OFF.getAsDouble() / 60.0;
        int lost = (int) Math.ceil(amount * share);
        return Math.min(amount, Math.max(lost, ArcforgeConfig.CRYOGENIC_BOIL_OFF_MINIMUM.getAsInt()));
    }
}
