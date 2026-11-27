/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.chemistry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.conduit.ConduitTier;

// Liquids too heavy or too searching for Wrought storage (#arcforge:needs_tempered_containment: Mercury): only Tempered
// Fluid Tanks, Canisters and Fluid Conduits and up hold them. A Wrought one refuses them, and a conduit network with a
// Wrought conduit in it (it runs at its lowest tier) won't carry them.
public final class Containment {
    public static final TagKey<Fluid> TEMPERED = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Arcforge.MODID, "needs_tempered_containment"));

    private Containment() {}

    public static boolean needsTempered(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid().defaultFluidState().is(TEMPERED);
    }

    // Whether storage or a conduit of this tier can hold it.
    public static boolean holds(ConduitTier tier, FluidResource resource) {
        return tier.ordinal() >= ConduitTier.TEMPERED.ordinal() || !needsTempered(resource);
    }
}
