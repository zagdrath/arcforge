/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.gas;

import java.util.List;

/**
 * The gas tanks of a block or an item: what each holds, and moving gas in and out by gas and amount. Amounts are in
 * millibuckets (mB).
 *
 * <p>Only tanks that can hold a gas are listed; a machine's water or oil tanks are left out, so indices here are not
 * {@link net.zagdrath.arcforge.api.machine.resource.MachineFluids} indices. A tank that holds liquids as well as gases
 * reads as empty, and takes no gas, while it holds a liquid. The list of tanks is fixed for the life of the handler.
 *
 * <p>Every transfer goes through the tank's own rules, exactly as a pipe's would: a machine's input tanks only take the
 * gases its recipes use and never give them back, its output tanks only give, a Pressurized Cylinder moves at most its
 * tier's rate per call, and a Gas Cartridge its own. Nothing is ever forced.
 *
 * <p>With {@code simulate} set, a method only reports what it would move. Otherwise each call runs in its own NeoForge
 * transfer transaction and commits it; if your own transaction is open, the call nests in it and is undone if yours
 * is aborted.
 */
public interface GasHandler {
    /**
     * Returns the number of gas tanks.
     *
     * @return the tank count, at least 1
     */
    int tankCount();

    /**
     * Returns a snapshot of one tank.
     *
     * @param tank the tank index, from 0 to {@link #tankCount()} - 1
     * @return the tank, or {@link GasTank#NONE} for an index out of range
     */
    GasTank tank(int tank);

    /**
     * Returns a snapshot of every tank, in index order.
     *
     * @return the tanks; unmodifiable
     */
    List<GasTank> tanks();

    /**
     * Returns whether a tank takes a gas at all, whatever it holds now. A full tank, or one holding another gas, may
     * still refuse an insert; only {@link #insert(int, Gas, int, boolean) inserting} (simulated, if you like) tells
     * for certain.
     *
     * @param tank the tank index
     * @param gas  the gas
     * @return {@code true} if the tank can take that gas from outside
     */
    boolean accepts(int tank, Gas gas);

    /**
     * Fills one tank.
     *
     * @param tank     the tank index
     * @param gas      the gas to fill with
     * @param amount   the most to fill, in mB
     * @param simulate {@code true} to only report what would be filled
     * @return the amount filled, in mB; 0 if the tank refuses it
     */
    int insert(int tank, Gas gas, int amount, boolean simulate);

    /**
     * Fills whichever tanks take the gas, as a pipe feeding the block would: into the tank that already holds it
     * first, never splitting it where the block keeps one gas to a tank.
     *
     * @param gas      the gas to fill with
     * @param amount   the most to fill, in mB
     * @param simulate {@code true} to only report what would be filled
     * @return the amount filled, in mB
     */
    int insert(Gas gas, int amount, boolean simulate);

    /**
     * Drains a gas from one tank.
     *
     * @param tank     the tank index
     * @param gas      the gas to drain; nothing is drained if the tank holds another
     * @param amount   the most to drain, in mB
     * @param simulate {@code true} to only report what would be drained
     * @return the amount drained, in mB
     */
    int extract(int tank, Gas gas, int amount, boolean simulate);

    /**
     * Drains a gas from whichever tanks give it, in index order.
     *
     * @param gas      the gas to drain
     * @param amount   the most to drain, in mB
     * @param simulate {@code true} to only report what would be drained
     * @return the amount drained, in mB
     */
    int extract(Gas gas, int amount, boolean simulate);
}
