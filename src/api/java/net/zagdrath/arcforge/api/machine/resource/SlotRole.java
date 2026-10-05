/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.resource;

/**
 * What an item slot or fluid tank is for. The role decides what the API may do with it: {@link #INPUT},
 * {@link #FUEL} and {@link #CATALYST} can be inserted into, {@link #OUTPUT} can be extracted from, and
 * {@link #UPGRADE} and {@link #OTHER} are read only.
 */
public enum SlotRole {
    /** An ingredient or input fluid. Insertable. */
    INPUT,
    /** A product, by-product or emptied container. Extractable. */
    OUTPUT,
    /** Fuel burned to run the machine. Insertable. */
    FUEL,
    /** Something the machine needs but does not use up every operation: a die, mesh, catalyst or soil. Insertable. */
    CATALYST,
    /** An upgrade slot. Read only: upgrades are changed in the machine's GUI. */
    UPGRADE,
    /** Anything else: patterns, filters, internal buffers. Read only. */
    OTHER;

    /**
     * Returns whether the API may insert into slots or tanks with this role.
     *
     * @return {@code true} for {@link #INPUT}, {@link #FUEL} and {@link #CATALYST}
     */
    public boolean insertable() {
        return this == INPUT || this == FUEL || this == CATALYST;
    }

    /**
     * Returns whether the API may extract from slots or tanks with this role.
     *
     * @return {@code true} for {@link #OUTPUT}
     */
    public boolean extractable() {
        return this == OUTPUT;
    }
}
