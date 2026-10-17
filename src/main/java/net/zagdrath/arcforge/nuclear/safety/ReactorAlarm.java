/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear.safety;

import java.util.ArrayList;
import java.util.List;

// A reactor's alarms: warnings that come before each failure, as bit flags (the GUI's Safety page lights them,
// Jade lists them, and while any klaxon alarm is up and not silenced the reactor sounds its klaxon). Each has its
// lang key (gui.arcforge.alarm.<key>).
public enum ReactorAlarm {
    // Cladding nearing its failure temperature, and failing (Hydrogen and fission products into the coolant).
    CLADDING_HOT("cladding_hot", true),
    CLADDING_FAILED("cladding_failed", true),
    // The fuel nearing its melting point, and melted.
    FUEL_HOT("fuel_hot", true),
    CORE_MELT("core_melt", true),
    // Above the relief pressure (venting), and nearing the vessel's burst pressure.
    HIGH_PRESSURE("high_pressure", true),
    OVERPRESSURE("overpressure", true),
    // A fast power rise, and a prompt-critical excursion.
    POWER_RISE("power_rise", true),
    EXCURSION("excursion", true),
    // The coolant low (Emergency Core Cooling injecting), and radioactive.
    LOW_COOLANT("low_coolant", true),
    COOLANT_ACTIVITY("coolant_activity", false),
    // Hydrogen in the containment's air near its ignition concentration, and radioactive air.
    HYDROGEN("hydrogen", true),
    AIR_ACTIVITY("air_activity", false),
    // No sealed containment round it.
    NO_CONTAINMENT("no_containment", false),
    // A molten salt core's Freeze Plug has melted: the salt is draining (or drained) into the Drain Tank.
    SALT_DRAINED("salt_drained", true),
    // The vessel burst.
    RUPTURED("ruptured", true);

    private final String key;
    private final boolean klaxon;

    ReactorAlarm(String key, boolean klaxon) {
        this.key = key;
        this.klaxon = klaxon;
    }

    public String key() {
        return key;
    }

    // Whether it sounds the klaxon (the others only show).
    public boolean klaxon() {
        return klaxon;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public boolean in(int flags) {
        return (flags & bit()) != 0;
    }

    public static List<ReactorAlarm> of(int flags) {
        List<ReactorAlarm> list = new ArrayList<>();
        for (ReactorAlarm alarm : values()) {
            if (alarm.in(flags)) {
                list.add(alarm);
            }
        }
        return list;
    }

    // The flags that sound the klaxon.
    public static int klaxonMask() {
        int mask = 0;
        for (ReactorAlarm alarm : values()) {
            if (alarm.klaxon) {
                mask |= alarm.bit();
            }
        }
        return mask;
    }
}
