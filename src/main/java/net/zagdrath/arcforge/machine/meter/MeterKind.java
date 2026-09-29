/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.meter;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// What a Meter passes: FE, heat, liquids or gases. Each has its unit, its cap per tick (config "meters", the
// Arcforged conduit rates by default) and the conduits it connects to.
public enum MeterKind implements StringRepresentable {
    ENERGY("energy", ConduitType.ENERGY),
    HEAT("heat", ConduitType.THERMAL),
    FLUID("fluid", ConduitType.FLUID),
    GAS("gas", ConduitType.GAS);

    private final String name;
    private final ConduitType conduit;

    MeterKind(String name, ConduitType conduit) {
        this.name = name;
        this.conduit = conduit;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public ConduitType conduitType() {
        return conduit;
    }

    // The most it passes per tick.
    public int cap() {
        return switch (this) {
            case ENERGY -> ArcforgeConfig.METER_ENERGY_CAP.getAsInt();
            case HEAT -> ArcforgeConfig.METER_HEAT_CAP.getAsInt();
            case FLUID -> ArcforgeConfig.METER_FLUID_CAP.getAsInt();
            case GAS -> ArcforgeConfig.METER_GAS_CAP.getAsInt();
        };
    }

    // "FE/t", "HU/t", "mB/t".
    public Component unit() {
        return Component.translatable("gui.arcforge.meter.unit." + name);
    }
}
