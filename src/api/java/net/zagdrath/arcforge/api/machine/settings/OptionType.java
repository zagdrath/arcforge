/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.settings;

/** The kind of value a {@link MachineOption} takes. */
public enum OptionType {
    /** {@code "true"} or {@code "false"}. */
    BOOLEAN,
    /** A whole number from {@link MachineOption#min()} to {@link MachineOption#max()}, as a decimal string. */
    INTEGER,
    /** One of {@link MachineOption#choices()}. */
    CHOICE
}
