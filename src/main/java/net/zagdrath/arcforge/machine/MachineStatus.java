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
    BURNING("burning", "led_running"),
    PRESSING("pressing", "led_running"),
    NO_DIE("no_die", "led_blocked"),
    PUMPING("pumping", "led_running"),
    NO_SOURCE("no_source", "led_blocked"),
    TANK_FULL("tank_full", "led_blocked"),
    BOILING("boiling", "led_running"),
    HEATING("heating", "led_idle"),
    NO_WATER("no_water", "led_blocked"),
    STEAM_FULL("steam_full", "led_blocked"),
    GENERATING("generating", "led_running"),
    NO_STEAM("no_steam", "led_blocked"),
    SPINNING_UP("spinning_up", "led_running"),
    COASTING("coasting", "led_idle"),
    NO_FEED("no_feed", "led_blocked"),
    NOT_FORMED("not_formed", "led_off"),
    // The Solar Thermal Array.
    TRACKING("tracking", "led_running"),
    STOWED("stowed", "led_blocked"),
    NIGHT("night", "led_idle"),
    NO_SKY("no_sky", "led_blocked"),
    // The Arc Melter.
    MELTING("melting", "led_running"),
    // The Chemical Reactor.
    REACTING("reacting", "led_running"),
    MISSING_FLUID("missing_fluid", "led_blocked"),
    // The Superheater and Condenser Arrays.
    SUPERHEATING("superheating", "led_running"),
    CONDENSING("condensing", "led_running"),
    // The Electrolyzer.
    SPLITTING("splitting", "led_running"),
    // The Assembler.
    CRAFTING("crafting", "led_running"),
    NO_PATTERN("no_pattern", "led_idle"),
    MISSING_ITEMS("missing_items", "led_blocked"),
    // The Block Breaker.
    BREAKING("breaking", "led_running"),
    CANNOT_BREAK("cannot_break", "led_blocked"),
    NOTHING_TO_BREAK("nothing_to_break", "led_idle"),
    // The Block Placer.
    PLACING("placing", "led_running"),
    FRONT_BLOCKED("front_blocked", "led_blocked"),
    NOTHING_TO_PLACE("nothing_to_place", "led_idle"),
    // The Vacuum Collector.
    COLLECTING("collecting", "led_running"),
    // Pulse redstone mode, between pulses.
    WAITING_PULSE("waiting_pulse", "led_idle"),
    // The Arc Quarry.
    MINING("mining", "led_running"),
    SCANNING("scanning", "led_running"),
    OUT_OF_REPLACE("out_of_replace", "led_blocked"),
    WAITING_CHUNK("waiting_chunk", "led_blocked"),
    FINISHED("finished", "led_idle"),
    STOPPED("stopped", "led_off"),
    // The Fermenter.
    FERMENTING("fermenting", "led_running"),
    // The Gas Turbine Array.
    SPOOLING("spooling", "led_running"),
    IGNITING("igniting", "led_running"),
    FLAMEOUT("flameout", "led_blocked"),
    INTAKE_SHUT("intake_shut", "led_blocked"),
    NO_SIGNAL("no_signal", "led_off");

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
