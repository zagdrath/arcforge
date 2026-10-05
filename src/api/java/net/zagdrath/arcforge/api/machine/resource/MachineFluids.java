/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.resource;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * A machine's fluid tanks, each with a {@link SlotRole}. Gases are included: Arcforge stores gases as fluids.
 * Amounts are in millibuckets (mB). Tanks can be read freely; filling is allowed only into
 * {@linkplain SlotRole#insertable() insertable} tanks and draining only from
 * {@linkplain SlotRole#extractable() extractable} ones, and both still go through the machine's own rules (each
 * tank accepts only the fluids its recipes use).
 *
 * <p>The stack-based methods each run in their own transaction, so do not call them while a NeoForge transfer
 * transaction is open; use {@link #handler()} for that.
 */
public interface MachineFluids {
    /**
     * Returns the number of tanks.
     *
     * @return the tank count
     */
    int tankCount();

    /**
     * Returns what a tank is for.
     *
     * @param tank the tank index, from 0 to {@link #tankCount()} - 1
     * @return the tank's role, or {@link SlotRole#OTHER} for an index out of range
     */
    SlotRole role(int tank);

    /**
     * Returns a copy of a tank's contents.
     *
     * @param tank the tank index
     * @return a copy of the fluid in the tank, or {@link FluidStack#EMPTY} for an empty tank or an index out of range
     */
    FluidStack fluid(int tank);

    /**
     * Returns how much the tank holds when full.
     *
     * @param tank the tank index
     * @return the capacity in mB, or 0 for an index out of range
     */
    int capacity(int tank);

    /**
     * Fills one tank. Only insertable tanks accept fluid, and only fluid the machine accepts there.
     *
     * @param tank     the tank index
     * @param fluid    the fluid to fill with; not modified
     * @param simulate {@code true} to only report what would be filled
     * @return the amount filled, in mB
     */
    int insert(int tank, FluidStack fluid, boolean simulate);

    /**
     * Fills whichever insertable tanks accept the fluid, as a pipe feeding the machine would.
     *
     * @param fluid    the fluid to fill with; not modified
     * @param simulate {@code true} to only report what would be filled
     * @return the amount filled, in mB
     */
    int insert(FluidStack fluid, boolean simulate);

    /**
     * Drains one tank. Only extractable tanks give fluid.
     *
     * @param tank     the tank index
     * @param amount   the most to drain, in mB
     * @param simulate {@code true} to only report what would be drained
     * @return the fluid drained (empty if none)
     */
    FluidStack extract(int tank, int amount, boolean simulate);

    /**
     * Returns a NeoForge resource handler over the same tanks, with the same role and tank rules, for use inside
     * transfer transactions. Its indices are this view's tank indices.
     *
     * @return the role-checked handler
     */
    ResourceHandler<FluidResource> handler();
}
