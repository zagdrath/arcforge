/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.config;

import net.minecraft.network.chat.Component;

// What a machine face exposes to pipes, cables and other mods.
public enum SideMode {
    NONE("none"),
    INPUT("input"),
    OUTPUT("output"),
    ENERGY("energy"),
    // Multiblocks: exposes only the by-product (e.g. the Carbonizer's creosote, the Arcforge Furnace's slag).
    BYPRODUCT("byproduct"),
    // Heat machines: exposes the heat buffer (producers give heat out of it, consumers take heat in).
    HEAT("heat");

    private final String name;

    SideMode(String name) {
        this.name = name;
    }

    public SideMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public SideMode previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    public String getSerializedName() {
        return name;
    }

    public Component getDescription() {
        return Component.translatable("gui.arcforge.side_mode." + name);
    }

    public static SideMode byId(int id) {
        SideMode[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }
}
