/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.api.gas.GasRegistry;

// Gases are fluids that are lighter than air (a negative FluidType density), or tagged #arcforge:gases so
// other mods' gases can join in. Pressurized Conduits and Pressurized Cylinders carry only gases; Fluid
// Conduits and Fluid Tanks carry everything else. The gas API's GasRegistry is the definition; these are shorthands.
public final class Gases {
    public static final TagKey<Fluid> TAG = GasRegistry.TAG;

    private Gases() {}

    public static boolean isGas(Fluid fluid) {
        return GasRegistry.isGas(fluid);
    }

    public static boolean isGas(FluidResource resource) {
        return !resource.isEmpty() && isGas(resource.getFluid());
    }

    public static boolean isGas(FluidStack stack) {
        return !stack.isEmpty() && isGas(stack.getFluid());
    }
}
