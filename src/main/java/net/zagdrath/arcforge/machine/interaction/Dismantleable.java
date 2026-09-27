/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.interaction;

// A machine block entity that can be picked up with the wrench. Its data (contents, energy, settings)
// travels on the dropped item, so it must not also drop its contents when removed.
public interface Dismantleable {
    void markDismantled();
}
