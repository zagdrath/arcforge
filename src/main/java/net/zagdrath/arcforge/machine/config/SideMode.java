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
    LUBRICANT("lubricant"),
    // The Steam Turbine Array: spent steam comes out as Exhaust Steam instead of venting.
    EXHAUST("exhaust"),
    // Gases by name. The Electrolyzer gives them out; the Arcforge Furnace takes oxygen in. Not outputs in
    // general (see isOutput), so each machine handles them itself.
    OXYGEN("oxygen"),
    HYDROGEN("hydrogen"),
    // A machine's own gas out, whatever it is: the Fermenter's Carbon Dioxide, the Air Separator's Nitrogen, the Haber
    // Reactor's Ammonia, the Biogas Digester's Biogas (their Sides tabs name the gas). Handled by the machine.
    // SideConfig packs 4 bits per face and this is ordinal 15, the last that fits there.
    GAS_OUTPUT("gas_output"),
    // The Thermal Evaporator Array's ports: Brine (or what its input concentrates into) out, Salt out, and the water it
    // returns. Ports only (kept by name in PortStore): no single-block machine's SideConfig uses them, so they never
    // need to fit its 4 bits per face.
    BRINE("brine"),
    SALT("salt"),
    WATER("water"),
    // The Thermal Evaporator Array's by-product out (Lithium Brine, left when Brine boils down to Salt). Ports only.
    LITHIUM_BRINE("lithium_brine"),
    // The Battery Array's energy ports: FE in, and FE out (pushed into what touches them). Ports only.
    ENERGY_INPUT("energy_input"),
    ENERGY_OUTPUT("energy_output"),
    // The Biogas Digester's Sulfur Dust out (scrubbed from its Biogas). Ports only.
    SULFUR("sulfur");

    private final String name;

    SideMode(String name) {
        this.name = name;
    }

    // Whether faces in this mode give things out (and push them out with auto-eject).
    public boolean isOutput() {
        return switch (this) {
            case OUTPUT, BYPRODUCT, NAPHTHA, LIGHT_OIL, HEAVY_OIL, PITCH, EXHAUST, BRINE, SALT, WATER, LITHIUM_BRINE, SULFUR -> true;
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
