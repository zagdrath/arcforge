/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.zagdrath.arcforge.Arcforge;

public final class ModFluidTags {
    // Fluid fuels with carbon in them: burning them gives off Carbon Dioxide through a Flue Gas face (see FlueGas). The
    // oils, Creosote, Ethanol, Biodiesel, Biogas and Syngas; not Hydrogen.
    public static final TagKey<Fluid> CARBON_FUELS = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Arcforge.MODID, "carbon_fuels"));

    private ModFluidTags() {}
}
