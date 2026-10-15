/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

// Which of the oil and refinery family's multiblocks a controller runs (see OilfieldStructures). Every one is drawn by
// its renderer once formed: its casings show only their port plates and take the shape of the model (see PlantHitbox).
public enum PlantKind {
    DERRICK,
    PUMPJACK,
    CRACKING_UNIT,
    DIESEL_GENERATOR,
    STEAM_CRACKER;
}
