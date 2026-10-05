/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.status;

/**
 * What a machine has done. The totals are saved with the machine and survive unloading and restarts (they are lost
 * if the machine is broken); {@link #operationsPerMinute()} is worked out while the machine is loaded.
 *
 * <p>An <em>operation</em> is one unit of the machine's work: a recipe processed, an item crafted, a fuel item burned,
 * a block broken or placed, a batch finished. Machines that work continuously (pumps, boilers, turbines, collectors of
 * heat) may count only amounts and leave the operation count at 0.
 */
public interface MachineStatistics {
    /**
     * Returns the operations completed.
     *
     * @return the total operations
     */
    long operationsCompleted();

    /**
     * Returns the items produced, by-products included.
     *
     * @return the total items produced
     */
    long itemsProduced();

    /**
     * Returns the items used up (ingredients, fuel, consumed catalysts).
     *
     * @return the total items consumed
     */
    long itemsConsumed();

    /**
     * Returns the fluid produced, gases included.
     *
     * @return the total fluid produced, in mB
     */
    long fluidProduced();

    /**
     * Returns the fluid used up, gases included.
     *
     * @return the total fluid consumed, in mB
     */
    long fluidConsumed();

    /**
     * Returns how long the machine has spent {@link MachineStatus#RUNNING} while loaded.
     *
     * @return the total running time, in ticks
     */
    long uptimeTicks();

    /**
     * Returns how long the machine has been loaded since it was placed (or, for a multiblock, since its controller
     * started tracking).
     *
     * @return the total loaded time, in ticks
     */
    long loadedTicks();

    /**
     * Returns the rate of operations over the last minute the machine was loaded (fewer if it has been loaded for
     * less than a minute, scaled up to a minute).
     *
     * @return operations per minute
     */
    double operationsPerMinute();
}
