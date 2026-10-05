/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control;

import net.zagdrath.arcforge.api.machine.status.MachineStatus;

// The API's broad status for each of the machines' own statuses. Exhaustive, so a new status won't compile until it's
// placed here.
public final class StatusMapping {
    private StatusMapping() {}

    public static MachineStatus toApi(net.zagdrath.arcforge.machine.MachineStatus status) {
        return switch (status) {
            case RUNNING, CRUSHING, SMELTING, FIBERIZING, INFUSING, BURNING, PRESSING, PUMPING, BOILING, GENERATING,
                    SPINNING_UP, TRACKING, MELTING, REACTING, SUPERHEATING, CONDENSING, SPLITTING, CRAFTING, BREAKING,
                    PLACING, COLLECTING, MINING, SCANNING, FERMENTING, SPOOLING, IGNITING, OXY_FUEL, MILLING, EXTRACTING,
                    DRYING, SEPARATING, SYNTHESIZING, DIGESTING, GROWING, EVAPORATING, VULCANIZING, CHARGING, DISCHARGING,
                    CARBONIZING, FELLING, PLANTING, RECLAIMING, GASIFYING, SIFTING -> MachineStatus.RUNNING;
            case IDLE, HEATING, COASTING, NIGHT, STOWED, NO_PATTERN, NOTHING_TO_BREAK, NOTHING_TO_PLACE, WAITING_PULSE,
                    FINISHED, NO_SOIL, NO_CROPS, NO_LIGHT, GROWING_TREES -> MachineStatus.IDLE;
            // Heat machines run on heat as others run on FE.
            case NO_POWER, NO_HEAT, TOO_COLD -> MachineStatus.NO_POWER;
            case NO_FUEL, NO_LAVA, NO_FLUID, NO_DIE, NO_SOURCE, NO_WATER, NO_STEAM, NO_FEED, MISSING_FLUID, MISSING_ITEMS,
                    OUT_OF_REPLACE, NO_AIR, NO_NUTRIENTS, NO_ADDITIVE, NO_SAPLINGS, NO_CATALYST, NO_MESH -> MachineStatus.NO_INPUT;
            case FULL, OUTPUT_FULL, TANK_FULL, STEAM_FULL, FRONT_BLOCKED -> MachineStatus.OUTPUT_BLOCKED;
            case DISABLED, STOPPED, NO_SIGNAL, SWITCHED_OFF -> MachineStatus.DISABLED;
            case NOT_FORMED -> MachineStatus.NOT_FORMED;
            case TOO_HOT, CANNOT_BREAK, CANT_GROW, NO_SKY, FLAMEOUT, INTAKE_SHUT, WAITING_CHUNK -> MachineStatus.FAULT;
        };
    }
}
