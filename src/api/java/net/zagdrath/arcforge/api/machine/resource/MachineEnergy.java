/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.resource;

/**
 * A machine's energy buffer, in FE. Read only: energy moves through NeoForge's energy capability as usual.
 */
public interface MachineEnergy {
    /**
     * Returns whether the machine consumes, generates or stores energy.
     *
     * @return the machine's energy role
     */
    EnergyRole role();

    /**
     * Returns the energy stored right now.
     *
     * @return the stored energy in FE, from 0 to {@link #capacity()}
     */
    long stored();

    /**
     * Returns how much energy the buffer holds when full.
     *
     * @return the capacity in FE
     */
    long capacity();

    /**
     * Returns the energy moved in the last tick. For a {@link EnergyRole#CONSUMER} it is the energy used (0 when it
     * did not work); for a {@link EnergyRole#GENERATOR} the energy generated; for {@link EnergyRole#STORAGE} the
     * net flow, positive while charging and negative while discharging.
     *
     * @return FE in the last tick
     */
    long perTick();
}
