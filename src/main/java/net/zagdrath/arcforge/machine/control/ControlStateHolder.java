/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control;

// A block entity the machine control API can expose (see MachineControls): it keeps a MachineControlState, saves it,
// and tells MachineControlTracker when it loads and unloads.
public interface ControlStateHolder {
    MachineControlState machineControlState();
}
