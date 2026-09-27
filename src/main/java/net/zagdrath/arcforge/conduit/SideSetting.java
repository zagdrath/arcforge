/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit;

import net.minecraft.network.chat.Component;

// What the player (via the wrench) has chosen for one conduit side. The block state holds the
// resulting ConnectionMode; this is the intent behind it.
public enum SideSetting {
    // Conduits: connect. Machines: let the machine's side configuration decide input or output.
    AUTO("auto"),
    // Forced: always push into the neighbouring block.
    INPUT("input"),
    // Forced: always pull from the neighbouring block.
    OUTPUT("output"),
    // Never connect on this side.
    DISABLED("disabled");

    private final String name;

    SideSetting(String name) {
        this.name = name;
    }

    // Wrench cycle for machine sides: auto -> input -> output -> disabled -> auto.
    public SideSetting next(boolean backwards) {
        SideSetting[] values = values();
        return values[(ordinal() + (backwards ? values.length - 1 : 1)) % values.length];
    }

    public Component getDisplayName() {
        return Component.translatable("conduit_setting.arcforge." + name);
    }

    public String getSerializedName() {
        return name;
    }

    public static SideSetting byId(int id) {
        SideSetting[] values = values();
        return id >= 0 && id < values.length ? values[id] : AUTO;
    }
}
