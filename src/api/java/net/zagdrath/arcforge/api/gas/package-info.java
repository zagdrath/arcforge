/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

/**
 * Storing and moving Arcforge gases: a gas's identity ({@link net.zagdrath.arcforge.api.gas.Gas}), every gas
 * ({@link net.zagdrath.arcforge.api.gas.GasRegistry}), and the gas tanks of blocks and items
 * ({@link net.zagdrath.arcforge.api.gas.GasHandler}, found through
 * {@link net.zagdrath.arcforge.api.gas.GasCapabilities}).
 *
 * <p><b>Units:</b> gas amounts are in millibuckets (mB), the unit of NeoForge fluids: Arcforge stores gases as fluids,
 * so 1,000 mB of Hydrogen in a gas tank is 1,000 mB of the {@code arcforge:hydrogen} fluid. Temperatures are in
 * kelvin.
 *
 * <p><b>Threading:</b> {@link net.zagdrath.arcforge.api.gas.Gas} and
 * {@link net.zagdrath.arcforge.api.gas.GasRegistry} work on both sides once a world is loaded. Use handlers on the
 * logical server thread; the block capability is never provided on the client.
 */
@NullMarked
package net.zagdrath.arcforge.api.gas;

import org.jspecify.annotations.NullMarked;
