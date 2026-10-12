/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.output;

// A machine (or multiblock controller) that gives out heat, FE or steam and watches whether what's connected holds it
// back (see OutputLimit). MachineMenu syncs it to the GUI, which shows the warning line; Jade shows it too.
public interface OutputLimited {
    OutputLimit getOutputLimit();
}
