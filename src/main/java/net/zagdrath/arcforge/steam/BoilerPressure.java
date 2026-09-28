/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;

// A boiler's pressure setting (see BoilerCore). Auto makes whatever grade the boiler is hot enough for;
// the others hold the boiler at their grade's temperature, heating without boiling until it gets there.
public enum BoilerPressure {
    AUTO("auto", null),
    STEAM("steam", SteamGrade.STEAM),
    HIGH_PRESSURE("high_pressure", SteamGrade.HIGH_PRESSURE),
    SUPERHEATED("superheated", SteamGrade.SUPERHEATED);

    private final String name;
    private final @Nullable SteamGrade grade;

    BoilerPressure(String name, @Nullable SteamGrade grade) {
        this.name = name;
        this.grade = grade;
    }

    public String getSerializedName() {
        return name;
    }

    // The grade this setting holds, or null for Auto.
    public @Nullable SteamGrade grade() {
        return grade;
    }

    public Component getDescription() {
        return Component.translatable("gui.arcforge.pressure." + name);
    }

    public static BoilerPressure byId(int id) {
        BoilerPressure[] values = values();
        return id >= 0 && id < values.length ? values[id] : AUTO;
    }
}
