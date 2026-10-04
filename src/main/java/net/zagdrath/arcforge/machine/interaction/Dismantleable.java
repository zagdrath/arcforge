/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.interaction;

import net.minecraft.world.level.storage.ValueOutput;

// A machine block entity that can be picked up with the wrench. Its fluid, energy, settings and upgrades travel
// on the dropped item; the other items in its slots drop into the world (the wrench leaves them out of the saved
// data), so nothing but upgrades is hidden inside the item.
public interface Dismantleable {
    // Called just before the wrench removes the block, e.g. so a tank doesn't also spill its fluid, or a machine drop
    // the upgrades it keeps.
    default void markDismantled() {}

    // Adds what stays inside the dismantled item to its saved data, after the wrench has left out the "items" list:
    // a machine writes its upgrade slots back into it. Placing the item loads them like any saved data.
    default void saveKeptItems(ValueOutput output) {}
}
