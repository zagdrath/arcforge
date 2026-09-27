/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.interaction;

// Marks a machine block the wrench can rotate (use) and dismantle (sneak-use).
// The block needs a horizontal FACING property; its block entity should implement Dismantleable.
public interface WrenchableMachine {
}
