/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.Arcforge;

// Gases that eat through ordinary seals and linings (#arcforge:corrosive_gases: Uranium Hexafluoride and its depleted
// tails). Only Pressurized Conduits, a PVC-lined Pressurized Cylinder and the machines that work them hold one: Gas
// Cartridges, Jetpacks, the Gas Reservoir, the Quantum Tunnel and the Flare Stack refuse it, and an unlined cylinder
// won't take it.
public final class Corrosion {
    public static final TagKey<Fluid> TAG = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Arcforge.MODID, "corrosive_gases"));

    private Corrosion() {}

    public static boolean isCorrosive(Fluid fluid) {
        return fluid.defaultFluidState().is(TAG);
    }

    public static boolean isCorrosive(FluidResource resource) {
        return !resource.isEmpty() && isCorrosive(resource.getFluid());
    }

    public static boolean isCorrosive(FluidStack stack) {
        return !stack.isEmpty() && isCorrosive(stack.getFluid());
    }
}
