/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.interaction;

// A machine block entity that can be picked up with the wrench. Its fluid, energy and settings travel
// on the dropped item; the items in its slots always drop into the world (the wrench leaves them out
// of the saved data), so nothing is hidden inside the item.
public interface Dismantleable {
    // Called just before the wrench removes the block, e.g. so a tank doesn't also spill its fluid.
    default void markDismantled() {}
}
