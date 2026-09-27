/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

// Common config for Arcforge. Values are read when machines tick, so most changes apply without a restart.
public class ArcforgeConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Heat (HU) shared by every heat machine. A machine's temperature rises from 20°C when its heat",
                "buffer is empty to its maximum temperature when full, and heat only flows from hotter to colder.").push("heat");
    }

    public static final ModConfigSpec.IntValue HEAT_CONTACT_RATE = BUILDER
            .comment("Most HU/t a heat face pushes into a touching machine's heat face (without a thermodynamic conduit).")
            .defineInRange("contactTransferRate", 100, 1, 1_000_000);

    static {
        BUILDER.pop();
        BUILDER.comment("Geothermal Plant: turns lava into heat (HU). It makes no FE; feed its heat to a Thermoelectric Plant.")
                .push("geothermalPlant");
    }

    public static final ModConfigSpec.IntValue GEOTHERMAL_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 20_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 600, 21, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_TANK_CAPACITY = BUILDER
            .comment("Internal lava tank capacity in mB.")
            .defineInRange("lavaTankCapacity", 8_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_HEAT = BUILDER
            .comment("HU/t made while lava drains from the tank (at 1 mB/t).")
            .defineInRange("lavaHeat", 20, 0, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_PER_BURN = BUILDER
            .comment("mB of lava taken from the tank at a time; it then drains at 1 mB/t (the GUI flame shows what's left).")
            .defineInRange("lavaPerBurn", 100, 1, 1_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_SOURCE_HEAT = BUILDER
            .comment("Passive HU/t from each touching lava source block (all 6 sides count; blocks are never consumed).")
            .defineInRange("lavaSourceHeat", 3, 0, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_MAGMA_HEAT = BUILDER
            .comment("Passive HU/t from each touching magma block (never consumed).")
            .defineInRange("magmaHeat", 1, 0, 10_000);

    static {
        BUILDER.pop();
        BUILDER.comment("Combustion Generator: burns coal, charcoal and coal blocks (#arcforge:combustion_fuel) straight into FE.")
                .push("combustionGenerator");
    }

    public static final ModConfigSpec.IntValue GENERATOR_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 50_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue GENERATOR_ENERGY_PER_TICK = BUILDER
            .comment("FE/t made while burning.")
            .defineInRange("energyPerTick", 40, 1, 1_000_000);

    public static final ModConfigSpec.IntValue GENERATOR_MAX_OUTPUT = BUILDER
            .comment("Most FE/t pushed out of energy faces (shared across them).")
            .defineInRange("maxEnergyOutput", 200, 1, 1_000_000_000);

    public static final ModConfigSpec.DoubleValue GENERATOR_BURN_SPEED = BUILDER
            .comment("How much faster than a vanilla furnace fuel burns (2 = coal lasts 800 ticks instead of 1,600).")
            .defineInRange("burnSpeed", 2.0, 0.1, 100.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Firebox: burns coal, charcoal and coal blocks (#arcforge:combustion_fuel) into heat (HU).")
                .push("firebox");
    }

    public static final ModConfigSpec.IntValue FIREBOX_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 40_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue FIREBOX_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 1_100, 21, 10_000);

    public static final ModConfigSpec.IntValue FIREBOX_HEAT_PER_TICK = BUILDER
            .comment("HU/t made while burning.")
            .defineInRange("heatPerTick", 40, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue FIREBOX_BURN_SPEED = BUILDER
            .comment("How much faster than a vanilla furnace fuel burns (1 = coal lasts 1,600 ticks).")
            .defineInRange("burnSpeed", 1.0, 0.1, 100.0);

    static {
        BUILDER.pop();
        BUILDER.comment("Thermoelectric Plant: turns heat (HU) into FE. The hotter it runs, the more FE each HU gives.")
                .push("thermoelectricPlant");
    }

    public static final ModConfigSpec.IntValue THERMOELECTRIC_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 20_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 1_100, 21, 10_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_HEAT_THROUGHPUT = BUILDER
            .comment("Most HU/t it takes in, and the HU/t it converts when its buffer is full (it converts",
                    "in proportion to how full the buffer is, so it has to warm up).")
            .defineInRange("heatThroughput", 40, 1, 1_000_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_MIN_TEMPERATURE = BUILDER
            .comment("Temperature at which efficiency is 0%, in °C.")
            .defineInRange("zeroEfficiencyTemperature", 100, 0, 10_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_FULL_TEMPERATURE = BUILDER
            .comment("Temperature at which efficiency reaches 100% (1 FE per HU), in °C.")
            .defineInRange("fullEfficiencyTemperature", 1_100, 1, 10_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 50_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_MAX_OUTPUT = BUILDER
            .comment("Most FE/t pushed out of energy faces (shared across them).")
            .defineInRange("maxEnergyOutput", 200, 1, 1_000_000_000);

    static {
        BUILDER.pop();
        BUILDER.comment("Carbonizer: multiblock that bakes coal into coal coke, collecting creosote. Recipes are data-driven (arcforge:carbonizing).")
                .push("carbonizer");
    }

    public static final ModConfigSpec.IntValue CARBONIZER_MAX_SLICES = BUILDER
            .comment("Most slices (chambers) one Carbonizer can have. Each slice is 1 wide x 2 tall x 2 deep.")
            .defineInRange("maxSlices", 8, 1, 8);

    public static final ModConfigSpec.IntValue CARBONIZER_CREOSOTE_PER_SLICE = BUILDER
            .comment("Creosote buffer capacity per slice, in mB.")
            .defineInRange("creosotePerSlice", 4_000, 250, 1_000_000);

    public static final ModConfigSpec.IntValue CARBONIZER_MIN_CREOSOTE_CAPACITY = BUILDER
            .comment("Smallest creosote buffer, in mB, whatever the slice count.")
            .defineInRange("minCreosoteCapacity", 8_000, 250, 1_000_000);

    static {
        BUILDER.pop();
        BUILDER.comment("Arcforge Furnace: 3x3x6 brick multiblock that smelts steel. Recipes are data-driven (arcforge:arcforge_smelting).")
                .push("arcforgeFurnace");
    }

    public static final ModConfigSpec.IntValue FURNACE_MAX_HEAT = BUILDER
            .comment("Hottest the furnace can get, in °C.")
            .defineInRange("maxHeat", 1_600, 100, 10_000);

    public static final ModConfigSpec.IntValue FURNACE_HEAT_GAIN = BUILDER
            .comment("°C gained per tick while fuel burns.")
            .defineInRange("heatGainPerTick", 2, 1, 1_000);

    public static final ModConfigSpec.IntValue FURNACE_HEAT_LOSS = BUILDER
            .comment("°C lost per tick while no fuel burns.")
            .defineInRange("heatLossPerTick", 1, 1, 1_000);

    public static final ModConfigSpec.IntValue FURNACE_FUEL_BURN_TICKS = BUILDER
            .comment("Ticks one fuel item (#arcforge:arcforge_furnace_fuels, e.g. coal coke) keeps the furnace heating.")
            .defineInRange("fuelBurnTicks", 800, 1, 32_000);

    static {
        BUILDER.pop();
        BUILDER.comment("Shared multiblock settings.").push("multiblock");
    }

    public static final ModConfigSpec.IntValue MULTIBLOCK_PUSH_INTERVAL = BUILDER
            .comment("Ticks between pushes out of output and by-product faces into neighbouring inventories and tanks.")
            .defineInRange("autoPushInterval", 10, 1, 1_200);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
