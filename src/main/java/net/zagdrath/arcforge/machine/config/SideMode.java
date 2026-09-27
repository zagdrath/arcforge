/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.config;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

// What a machine face exposes to pipes, cables and other mods (and, as a block state, what a multiblock's
// port block does; see MultiblockPorts).
public enum SideMode implements StringRepresentable {
    NONE("none"),
    INPUT("input"),
    OUTPUT("output"),
    ENERGY("energy"),
    // Multiblocks: exposes only the by-product (e.g. the Carbonizer's creosote, the Arcforge Furnace's slag).
    BYPRODUCT("byproduct"),
    // Heat machines: exposes the heat buffer (producers give heat out of it, consumers take heat in).
    HEAT("heat"),
    // The Distillation Array: steam for stripping goes in, and each product has its own face mode out.
    STEAM("steam"),
    NAPHTHA("naphtha"),
    LIGHT_OIL("light_oil"),
    HEAVY_OIL("heavy_oil"),
    PITCH("pitch"),
    // Steam turbines: Heavy Oil goes into the lubricant tank.
    LUBRICANT("lubricant");

    private final String name;

    SideMode(String name) {
        this.name = name;
    }

    // Whether faces in this mode give things out (and push them out with auto-eject).
    public boolean isOutput() {
        return switch (this) {
            case OUTPUT, BYPRODUCT, NAPHTHA, LIGHT_OIL, HEAVY_OIL, PITCH -> true;
            default -> false;
        };
    }

    public SideMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public SideMode previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    @Override
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
