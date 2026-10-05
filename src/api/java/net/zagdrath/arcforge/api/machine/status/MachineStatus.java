/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.status;

import net.zagdrath.arcforge.api.machine.MachineControl;
import net.zagdrath.arcforge.api.machine.settings.MachineSettings;

/**
 * What a machine is doing, in broad terms. {@link MachineControl#statusReason()} gives the machine's own, more
 * detailed reason, as its GUI shows it.
 */
public enum MachineStatus {
    /** Ready but with nothing to do: no recipe matches, waiting for a redstone pulse, finished, night time, etc. */
    IDLE,
    /** Working: processing, generating, transferring or otherwise doing its job. */
    RUNNING,
    /** Cannot work for lack of energy, or of heat for a machine that runs on heat. */
    NO_POWER,
    /** Has a job it cannot do for lack of an input: an ingredient, fluid, fuel, catalyst, die or similar. */
    NO_INPUT,
    /** Cannot work because its output slots, tanks or energy buffer are full, or its front is blocked. */
    OUTPUT_BLOCKED,
    /** Stopped by its redstone mode, or switched off through {@link MachineSettings#setEnabled(boolean)}. */
    DISABLED,
    /** A multiblock whose structure is not formed. */
    NOT_FORMED,
    /**
     * Cannot work because of a problem that needs attention: too hot, no view of the sky, a block it cannot break,
     * a flameout and similar.
     */
    FAULT
}
