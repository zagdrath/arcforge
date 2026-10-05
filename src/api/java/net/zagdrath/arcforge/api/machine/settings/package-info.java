/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

/**
 * A machine's settings, read and changed through {@link net.zagdrath.arcforge.api.machine.settings.MachineSettings}:
 * on/off, redstone mode, sides or ports, auto-eject, machine-specific options and (read only) upgrades. Every change is
 * checked by the machine and answered with a {@link net.zagdrath.arcforge.api.machine.settings.SettingResult}.
 *
 * <p>Server side only, like the rest of {@link net.zagdrath.arcforge.api.machine}.
 */
@NullMarked
package net.zagdrath.arcforge.api.machine.settings;

import org.jspecify.annotations.NullMarked;
