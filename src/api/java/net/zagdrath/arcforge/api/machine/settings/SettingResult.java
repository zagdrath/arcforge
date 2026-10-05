/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.settings;

/** The outcome of changing a setting through {@link MachineSettings}. */
public enum SettingResult {
    /** The setting was changed. */
    APPLIED,
    /** The setting already had that value; nothing changed. */
    UNCHANGED,
    /** The machine does not have this setting. */
    UNSUPPORTED,
    /** The machine has this setting but refuses that value (a mode it does not offer, a number out of range...). */
    INVALID,
    /** The setting cannot be changed in the machine's current state, such as a port on an unformed multiblock. */
    REJECTED;

    /**
     * Returns whether the machine now has the requested value.
     *
     * @return {@code true} for {@link #APPLIED} and {@link #UNCHANGED}
     */
    public boolean succeeded() {
        return this == APPLIED || this == UNCHANGED;
    }
}
