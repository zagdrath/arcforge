/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

// The block entity of an Oil Derrick's or a Pumpjack's controller (see OilfieldStructures).
public interface OilfieldController extends MultiblockController {
    // Recheck the structure on the next tick (a nearby part was placed or removed).
    void requestCheck();

    // Recheck it now.
    void checkNow();
}
