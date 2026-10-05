/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

/**
 * Monitoring and controlling Arcforge machines and multiblocks.
 *
 * <p>Look a machine up with {@link net.zagdrath.arcforge.api.machine.MachineCapabilities#MACHINE_CONTROL}, then
 * read or change it through {@link net.zagdrath.arcforge.api.machine.MachineControl}.
 *
 * <p><b>Threading:</b> every type in this package is server side only. Call every method on the logical server
 * thread (for example from a block entity tick or a server tick event). The capability is never provided on the
 * client. Calling from another thread has undefined results.
 *
 * <p><b>Validation:</b> every write goes through the machine's own rules: recipe filters, slot rules, the
 * redstone and side modes the machine offers, and so on. A write the machine would not accept from a player or a
 * pipe is refused, never forced.
 *
 * <p><b>Missing features:</b> a machine that lacks a feature returns an empty {@link java.util.Optional}, an
 * empty list, zero, or {@link net.zagdrath.arcforge.api.machine.settings.SettingResult#UNSUPPORTED}. Methods never
 * throw because a feature is missing.
 *
 * <p>The types are grouped by what they describe: {@link net.zagdrath.arcforge.api.machine.status} (status,
 * statistics and events), {@link net.zagdrath.arcforge.api.machine.resource} (energy, items, fluids and heat) and
 * {@link net.zagdrath.arcforge.api.machine.settings} (settings).
 *
 * <p><b>Permissions:</b> the API does not check who is calling. Checking that a player may control a machine is the
 * consumer's job; {@link net.zagdrath.arcforge.api.machine.MachineControl#owner()} gives the machine's owner.
 */
@NullMarked
package net.zagdrath.arcforge.api.machine;

import org.jspecify.annotations.NullMarked;
