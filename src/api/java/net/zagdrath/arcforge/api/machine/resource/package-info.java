/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

/**
 * A machine's resources: its energy ({@link net.zagdrath.arcforge.api.machine.resource.MachineEnergy}), item slots
 * ({@link net.zagdrath.arcforge.api.machine.resource.MachineItems}), fluid and gas tanks
 * ({@link net.zagdrath.arcforge.api.machine.resource.MachineFluids}) and heat
 * ({@link net.zagdrath.arcforge.api.machine.resource.MachineHeat}). Each slot and tank has a
 * {@link net.zagdrath.arcforge.api.machine.resource.SlotRole} that decides what may be inserted or extracted.
 *
 * <p>Server side only, like the rest of {@link net.zagdrath.arcforge.api.machine}.
 */
@NullMarked
package net.zagdrath.arcforge.api.machine.resource;

import org.jspecify.annotations.NullMarked;
