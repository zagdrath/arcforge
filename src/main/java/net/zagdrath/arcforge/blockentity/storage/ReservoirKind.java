/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.storage;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.steam.Gases;

// The tank Reservoirs: the Fluid Reservoir (liquids, filling from the bottom up, Fluid Conduits) and the Gas Reservoir
// (gases, filling every block evenly, Pressurized Conduits). Only Reservoirs of the same kind join into one tank.
public enum ReservoirKind {
    FLUID, GAS;

    // mB a block holds (reservoir.fluidCapacity / gasCapacity).
    public int capacity() {
        return this == FLUID ? ArcforgeConfig.FLUID_RESERVOIR_CAPACITY.getAsInt() : ArcforgeConfig.GAS_RESERVOIR_CAPACITY.getAsInt();
    }

    // A Fluid Reservoir takes any liquid and no gas, a Gas Reservoir any gas and no liquid.
    public boolean accepts(FluidResource resource) {
        return !resource.isEmpty() && Gases.isGas(resource) == (this == GAS);
    }

    // Whether the tank fills layer by layer from the bottom (a liquid); a gas fills every block alike.
    public boolean layered() {
        return this == FLUID;
    }

    // The conduits that connect to it.
    public ConduitType conduitType() {
        return this == FLUID ? ConduitType.FLUID : ConduitType.GAS;
    }
}
