/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.resource;

/**
 * A machine's heat buffer, in heat units (HU), with a temperature in °C. Heat is Arcforge's own resource and moves
 * through Arcforge's heat capability; this view reads the buffer and can move heat through the machine's own heat
 * input or output, with the same limits a heat conduit has.
 */
public interface MachineHeat {
    /**
     * Returns whether the machine uses or produces heat.
     *
     * @return the machine's heat role
     */
    HeatRole role();

    /**
     * Returns the heat stored right now.
     *
     * @return the stored heat in HU
     */
    long stored();

    /**
     * Returns how much heat the buffer holds when full.
     *
     * @return the capacity in HU
     */
    long capacity();

    /**
     * Returns the buffer's temperature.
     *
     * @return the temperature in °C
     */
    int temperature();

    /**
     * Returns the hottest the buffer can get.
     *
     * @return the maximum temperature in °C
     */
    int maxTemperature();

    /**
     * Returns the heat moved in the last tick: used by a {@link HeatRole#CONSUMER}, produced by a
     * {@link HeatRole#PRODUCER}.
     *
     * @return HU in the last tick
     */
    long perTick();

    /**
     * Gives heat to the machine through its heat input. Only a {@link HeatRole#CONSUMER} accepts any.
     *
     * @param amount   the most heat to give, in HU
     * @param simulate {@code true} to only report what would be accepted
     * @return the heat accepted, in HU
     */
    int insert(int amount, boolean simulate);

    /**
     * Takes heat from the machine through its heat output. Only a {@link HeatRole#PRODUCER} gives any.
     *
     * @param amount   the most heat to take, in HU
     * @param simulate {@code true} to only report what would be taken
     * @return the heat taken, in HU
     */
    int extract(int amount, boolean simulate);
}
