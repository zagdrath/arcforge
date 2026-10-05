/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.gas;

import java.util.Optional;

/**
 * A snapshot of one tank of a {@link GasHandler}. It doesn't change when the tank does; read the tank again for
 * fresh values.
 *
 * @param gas        the gas in the tank, or empty if it holds none
 * @param amount     how much it holds, in mB; 0 when {@code gas} is empty
 * @param capacity   how much it holds when full, in mB
 * @param canInsert  whether the tank takes gas from outside at all (a machine's input tank, a storage tank); what it
 *                   takes still depends on {@link GasHandler#accepts(int, Gas)}
 * @param canExtract whether gas can be taken out of the tank (a machine's output tank, a storage tank)
 */
public record GasTank(Optional<Gas> gas, int amount, int capacity, boolean canInsert, boolean canExtract) {
    /** A tank that holds nothing and allows nothing, for an index out of range. */
    public static final GasTank NONE = new GasTank(Optional.empty(), 0, 0, false, false);

    /**
     * Returns whether the tank holds no gas.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return gas.isEmpty() || amount <= 0;
    }

    /**
     * Returns how much more the tank could hold.
     *
     * @return the free space in mB, never negative
     */
    public int space() {
        return Math.max(0, capacity - amount);
    }
}
