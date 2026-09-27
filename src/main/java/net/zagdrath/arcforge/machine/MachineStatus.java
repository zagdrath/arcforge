/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.Arcforge;

// Status shown on a machine's GUI screen, with its matching LED sprite.
public enum MachineStatus {
    RUNNING("running", "led_running"),
    FULL("full", "led_idle"),
    NO_FUEL("no_fuel", "led_blocked"),
    DISABLED("disabled", "led_off"),
    NO_LAVA("no_lava", "led_blocked"),
    NO_HEAT("no_heat", "led_blocked"),
    CRUSHING("crushing", "led_running"),
    IDLE("idle", "led_idle"),
    NO_POWER("no_power", "led_blocked"),
    OUTPUT_FULL("output_full", "led_blocked"),
    SMELTING("smelting", "led_running"),
    FIBERIZING("fiberizing", "led_running"),
    TOO_COLD("too_cold", "led_blocked"),
    INFUSING("infusing", "led_running"),
    NO_FLUID("no_fluid", "led_blocked"),
    BURNING("burning", "led_running");

    private final String name;
    private final String led;

    MachineStatus(String name, String led) {
        this.name = name;
        this.led = led;
    }

    public Component getDescription() {
        return Component.translatable("gui.arcforge.status." + name);
    }

    // LED sprite inside the given machine's sprite folder, e.g. container/geothermal_plant/led_running.
    public Identifier getLedSprite(String machine) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, "container/" + machine + "/" + led);
    }

    public static MachineStatus byId(int id) {
        MachineStatus[] values = values();
        return id >= 0 && id < values.length ? values[id] : NO_FUEL;
    }
}
