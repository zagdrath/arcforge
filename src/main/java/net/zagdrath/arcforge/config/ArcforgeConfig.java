/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

// Common config for Arcforge. Values are read when machines tick, so most changes apply without a restart.
public class ArcforgeConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Heat sources, heat transfer and the machines that turn heat into FE.").push("power");
    }

    static {
        BUILDER.comment("Heat (HU) shared by every heat machine. A machine's temperature rises from 20°C when its heat",
                "buffer is empty to its maximum temperature when full, and heat only flows from hotter to colder.").push("heat");
    }

    public static final ModConfigSpec.IntValue HEAT_CONTACT_RATE = BUILDER
            .comment("Most HU/t a heat face pushes into a touching machine's heat face (without a thermodynamic conduit).")
            .defineInRange("contactTransferRate", 100, 1, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Geothermal Plant: turns lava into heat (HU). It makes no FE; feed its heat to a Thermoelectric Plant.")
                .push("geothermalPlant");
    }

    public static final ModConfigSpec.IntValue GEOTHERMAL_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 60_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 1_400, 21, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_TANK_CAPACITY = BUILDER
            .comment("Internal lava tank capacity in mB.")
            .defineInRange("lavaTankCapacity", 8_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_HEAT = BUILDER
            .comment("HU/t made while lava drains from the tank (at 1 mB/t).")
            .defineInRange("lavaHeat", 80, 0, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_PER_BURN = BUILDER
            .comment("mB of lava taken from the tank at a time; it then drains at 1 mB/t (the GUI flame shows what's left).")
            .defineInRange("lavaPerBurn", 100, 1, 1_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_SOURCE_HEAT = BUILDER
            .comment("Passive HU/t from each touching lava source block (all 6 sides count; blocks are never consumed).")
            .defineInRange("lavaSourceHeat", 10, 0, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_MAGMA_HEAT = BUILDER
            .comment("Passive HU/t from each touching magma block (never consumed).")
            .defineInRange("magmaHeat", 4, 0, 10_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Combustion Plant: burns coal, charcoal and coal blocks (#arcforge:combustion_fuel) straight into FE.")
                .push("combustionPlant");
    }

    public static final ModConfigSpec.IntValue COMBUSTION_PLANT_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 50_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue COMBUSTION_PLANT_ENERGY_PER_TICK = BUILDER
            .comment("FE/t made while burning.")
            .defineInRange("energyPerTick", 40, 1, 1_000_000);

    public static final ModConfigSpec.IntValue COMBUSTION_PLANT_MAX_OUTPUT = BUILDER
            .comment("Most FE/t pushed out of energy faces (shared across them).")
            .defineInRange("maxEnergyOutput", 200, 1, 1_000_000_000);

    public static final ModConfigSpec.DoubleValue COMBUSTION_PLANT_BURN_SPEED = BUILDER
            .comment("How much faster than a vanilla furnace fuel burns (2 = coal lasts 800 ticks instead of 1,600).")
            .defineInRange("burnSpeed", 2.0, 0.1, 100.0);

    static {
        BUILDER.pop();
    }

    static {
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
            .defineInRange("heatPerTick", 80, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue FIREBOX_BURN_SPEED = BUILDER
            .comment("How much faster than a vanilla furnace fuel burns (2 = coal lasts 800 ticks instead of 1,600).")
            .defineInRange("burnSpeed", 2.0, 0.1, 100.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Fuel Burner: burns liquid fuel into heat (HU). Fuels and their heat are data-driven",
                "(data map arcforge:burner_fuels, keyed by fluid).").push("fuelBurner");
    }

    public static final ModConfigSpec.IntValue FUEL_BURNER_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 40_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue FUEL_BURNER_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 1_200, 21, 10_000);

    public static final ModConfigSpec.IntValue FUEL_BURNER_TANK_CAPACITY = BUILDER
            .comment("Fuel tank capacity in mB.")
            .defineInRange("tankCapacity", 8_000, 1_000, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Thermoelectric Plant: turns heat (HU) into FE. The hotter it runs, the more FE each HU gives.")
                .push("thermoelectricPlant");
    }

    public static final ModConfigSpec.IntValue THERMOELECTRIC_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 20_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C. Above the 100% efficiency temperature it runs",
                    "no faster, but each HU gives more FE (see bonusEfficiency).")
            .defineInRange("maxTemperature", 1_400, 21, 10_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_HEAT_THROUGHPUT = BUILDER
            .comment("HU/t it converts at the 100% efficiency temperature. It converts in proportion to its temperature",
                    "above 20°C, so it has to warm up, and hotter heat passes faster: when full (at maxTemperature) it",
                    "takes in and converts heatThroughput x (maxTemperature - 20) / (fullEfficiencyTemperature - 20).")
            .defineInRange("heatThroughput", 80, 1, 1_000_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_MIN_TEMPERATURE = BUILDER
            .comment("Temperature at which efficiency is 0%, in °C.")
            .defineInRange("zeroEfficiencyTemperature", 100, 0, 10_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_FULL_TEMPERATURE = BUILDER
            .comment("Temperature at which efficiency reaches 100% (1 FE per HU), in °C.")
            .defineInRange("fullEfficiencyTemperature", 1_100, 1, 10_000);

    public static final ModConfigSpec.DoubleValue THERMOELECTRIC_BONUS_EFFICIENCY = BUILDER
            .comment("Efficiency reached at the bonus temperature (1.15 = 115%). It rises linearly from 100% at the",
                    "100% efficiency temperature, and goes no higher.")
            .defineInRange("bonusEfficiency", 1.15, 1.0, 10.0);

    public static final ModConfigSpec.DoubleValue THERMOELECTRIC_UPGRADE_EFFICIENCY = BUILDER
            .comment("Extra FE per HU each Thermoelectric Efficiency Upgrade adds (0.0625: 8 cards give +50%).")
            .defineInRange("upgradeEfficiencyPerCard", 0.0625, 0.0, 1.0);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_BONUS_TEMPERATURE = BUILDER
            .comment("Temperature at which the bonus efficiency is reached, in °C.")
            .defineInRange("bonusEfficiencyTemperature", 1_400, 1, 10_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 50_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue THERMOELECTRIC_MAX_OUTPUT = BUILDER
            .comment("Most FE/t pushed out of energy faces (shared across them).")
            .defineInRange("maxEnergyOutput", 200, 1, 1_000_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Solar Thermal Array: a 2x2 tower, 4 tall, whose trough mirror tracks the sun and heats a receiver.",
                "Heat per tick = peakHeat x sun x weather x axis x biome x (collectors in sunlight / 4); the temperature",
                "climbs from 20°C to maxTemperature with the same factors (capped at the peak).").push("solarThermalArray");
    }

    public static final ModConfigSpec.IntValue SOLAR_PEAK_HEAT = BUILDER
            .comment("HU/t at clear noon, all four collectors in sunlight, on the north-south axis, in a temperate biome.")
            .defineInRange("peakHeat", 600, 1, 1_000_000);

    public static final ModConfigSpec.IntValue SOLAR_MAX_TEMPERATURE = BUILDER
            .comment("Receiver temperature at full sun, in °C (High-Pressure Steam needs 500).")
            .defineInRange("maxTemperature", 550, 100, 10_000);

    public static final ModConfigSpec.IntValue SOLAR_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size in HU.")
            .defineInRange("heatCapacity", 16_000, 100, 100_000_000);

    public static final ModConfigSpec.DoubleValue SOLAR_RAIN_MULTIPLIER = BUILDER
            .comment("Output in rain or snow, as a share of clear weather.")
            .defineInRange("rainMultiplier", 0.3, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SOLAR_THUNDER_MULTIPLIER = BUILDER
            .comment("Output in a thunderstorm (the trough stows face down; the receiver still takes diffuse light).")
            .defineInRange("thunderMultiplier", 0.1, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SOLAR_HOT_BIOME_MULTIPLIER = BUILDER
            .comment("Output in hot biomes (hotBiomeTags). The temperature is still capped at maxTemperature.")
            .defineInRange("hotBiomeMultiplier", 1.2, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue SOLAR_COLD_BIOME_MULTIPLIER = BUILDER
            .comment("Output and temperature in cold biomes (coldBiomeTags, or anywhere cold enough to snow).")
            .defineInRange("coldBiomeMultiplier", 0.85, 0.0, 10.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SOLAR_HOT_BIOME_TAGS = BUILDER
            .comment("Biome tags counted as hot.")
            .defineListAllowEmpty("hotBiomeTags", List.of("c:is_desert", "minecraft:is_badlands", "minecraft:is_savanna"), () -> "c:is_desert",
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SOLAR_COLD_BIOME_TAGS = BUILDER
            .comment("Biome tags counted as cold.")
            .defineListAllowEmpty("coldBiomeTags", List.of("c:is_cold"), () -> "c:is_cold", value -> value instanceof String);

    public static final ModConfigSpec.DoubleValue SOLAR_NORTH_SOUTH_MULTIPLIER = BUILDER
            .comment("Output with the tracking axis north-south (controller facing north or south).")
            .defineInRange("northSouthMultiplier", 1.0, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue SOLAR_EAST_WEST_MULTIPLIER = BUILDER
            .comment("Output with the tracking axis east-west (controller facing east or west).")
            .defineInRange("eastWestMultiplier", 0.7, 0.0, 10.0);

    public static final ModConfigSpec.IntValue SOLAR_TRACKING_LIMIT = BUILDER
            .comment("How far the trough tilts from level toward the sun, in degrees.")
            .defineInRange("trackingLimitDegrees", 75, 0, 90);

    public static final ModConfigSpec.DoubleValue SOLAR_PANEL_SPEED = BUILDER
            .comment("How fast the trough turns, in degrees per tick.")
            .defineInRange("panelSpeedDegreesPerTick", 2.0, 0.1, 180.0);

    public static final ModConfigSpec.IntValue SOLAR_GLOW_FULLBRIGHT = BUILDER
            .comment("Receiver temperature from which it glows full-bright, in °C.")
            .defineInRange("glowFullbrightTemperature", 300, 20, 10_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Boiling water into steam and steam into FE.").push("steam");
    }

    static {
        BUILDER.comment("Steam Boiler: boils water into steam with heat. Hotter boilers make higher grades of steam.").push("steamBoiler");
    }

    public static final ModConfigSpec.IntValue BOILER_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size in HU (a full buffer is at the maximum temperature).")
            .defineInRange("heatCapacity", 40_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue BOILER_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 1_400, 200, 10_000);

    public static final ModConfigSpec.IntValue BOILER_MAX_HEAT_PER_TICK = BUILDER
            .comment("Most HU/t it boils with.")
            .defineInRange("maxHeatPerTick", 80, 1, 1_000_000);

    public static final ModConfigSpec.IntValue BOILER_TANK_CAPACITY = BUILDER
            .comment("Water and steam tank sizes in mB.")
            .defineInRange("tankCapacity", 8_000, 1_000, 1_000_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Steam Boiler Array: a 3x3 boiler 3 to 7 blocks tall. Values are per block of height.").push("steamBoilerArray");
    }

    public static final ModConfigSpec.IntValue BOILER_ARRAY_HEAT_PER_HEIGHT = BUILDER
            .comment("Heat buffer size in HU, per block of height.")
            .defineInRange("heatCapacityPerHeight", 100_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue BOILER_ARRAY_MAX_HEAT_PER_HEIGHT = BUILDER
            .comment("Most HU/t it boils with, per block of height.")
            .defineInRange("maxHeatPerTickPerHeight", 600, 1, 1_000_000);

    public static final ModConfigSpec.IntValue BOILER_ARRAY_TANK_PER_HEIGHT = BUILDER
            .comment("Water and steam tank sizes in mB, per block of height.")
            .defineInRange("tankCapacityPerHeight", 16_000, 1_000, 100_000_000);

    public static final ModConfigSpec.DoubleValue BOILER_ARRAY_HEAT_COST = BUILDER
            .comment("Multiplier on the heat per mB of steam (0.8 = the big drum loses 20% less).")
            .defineInRange("heatCostMultiplier", 0.8, 0.1, 10.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Steam Turbine: turns steam into FE. FE per mB depends on the steam grade.").push("steamTurbine");
    }

    public static final ModConfigSpec.IntValue TURBINE_MAX_FLOW = BUILDER
            .comment("Most steam it uses, in mB/t.")
            .defineInRange("maxFlow", 10, 1, 1_000_000);

    public static final ModConfigSpec.IntValue TURBINE_TANK_CAPACITY = BUILDER
            .comment("Steam tank size in mB.")
            .defineInRange("tankCapacity", 8_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue TURBINE_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 50_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue TURBINE_MAX_OUTPUT = BUILDER
            .comment("Most FE/t pushed out of its energy faces.")
            .defineInRange("maxEnergyOutput", 400, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue TURBINE_LUBRICANT_CAPACITY = BUILDER
            .comment("Lubricant (Heavy Oil) tank size in mB.")
            .defineInRange("lubricantCapacity", 1_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue TURBINE_LUBRICANT_INTERVAL = BUILDER
            .comment("Ticks of generating per mB of lubricant used.")
            .defineInRange("lubricantInterval", 100, 1, 100_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Steam Turbine Array: a 3x3 turbine 3 to 9 blocks long, whose rotor spins up and coasts down.",
                "Values are per block of length.").push("steamTurbineArray");
    }

    public static final ModConfigSpec.IntValue TURBINE_ARRAY_FLOW_PER_LENGTH = BUILDER
            .comment("Most steam it uses in mB/t, per block of length.")
            .defineInRange("maxFlowPerLength", 40, 1, 1_000_000);

    public static final ModConfigSpec.IntValue TURBINE_ARRAY_TANK_PER_LENGTH = BUILDER
            .comment("Steam tank size in mB, per block of length.")
            .defineInRange("tankCapacityPerLength", 32_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue TURBINE_ARRAY_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 1_000_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue TURBINE_ARRAY_MAX_OUTPUT = BUILDER
            .comment("Most FE/t pushed out of its energy faces.")
            .defineInRange("maxEnergyOutput", 16_384, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue TURBINE_ARRAY_MAX_RPM = BUILDER
            .comment("Rotor speed at a full flow of Superheated Steam (it spins in proportion to the power in the steam).")
            .defineInRange("maxRpm", 3_600, 100, 100_000);

    public static final ModConfigSpec.IntValue TURBINE_ARRAY_LUBRICANT_CAPACITY = BUILDER
            .comment("Lubricant (Heavy Oil) tank size in mB.")
            .defineInRange("lubricantCapacity", 4_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue TURBINE_ARRAY_LUBRICANT_INTERVAL = BUILDER
            .comment("Ticks of generating per mB of lubricant used, for every 3 blocks of length.")
            .defineInRange("lubricantInterval", 20, 1, 100_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Heavy Oil in a steam turbine's lubricant tank, while it isn't empty and the turbine generates.").push("lubricant");
    }

    public static final ModConfigSpec.DoubleValue LUBRICANT_OUTPUT_BONUS = BUILDER
            .comment("Extra FE per mB of steam (0.08 = +8%).")
            .defineInRange("outputBonus", 0.08, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue LUBRICANT_SPIN_UP = BUILDER
            .comment("Multiplier on how fast a Steam Turbine Array's rotor spins up.")
            .defineInRange("arraySpinUpMultiplier", 2.0, 1.0, 100.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Single-block processing machines.").push("machines");
    }

    static {
        BUILDER.comment("Arc Crusher: crushes ores and materials into dusts with FE. Recipes are data-driven (arcforge:crushing).")
                .push("arcCrusher");
    }

    public static final ModConfigSpec.IntValue CRUSHER_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue CRUSHER_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 200, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue CRUSHER_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while crushing, before upgrades (a 200-tick recipe costs 200x this).")
            .defineInRange("energyPerTick", 20, 1, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Induction Furnace: smelts anything with a furnace recipe using FE.").push("inductionFurnace");
    }

    public static final ModConfigSpec.IntValue INDUCTION_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue INDUCTION_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 200, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue INDUCTION_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while smelting, before upgrades.")
            .defineInRange("energyPerTick", 20, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue INDUCTION_TIME_MULTIPLIER = BUILDER
            .comment("Multiplier on the recipe's cooking time (0.5 = 100 ticks for a normal 200-tick furnace recipe).")
            .defineInRange("timeMultiplier", 0.5, 0.01, 10.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Metal Press: presses ingots into plates, gears and rods with a die, using FE. Recipes are data-driven",
                "(arcforge:pressing) and set the time.").push("metalPress");
    }

    public static final ModConfigSpec.IntValue PRESS_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue PRESS_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 200, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue PRESS_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while pressing, before upgrades (a 100-tick recipe costs 100x this).")
            .defineInRange("energyPerTick", 20, 1, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Electric Pump: pumps the fluid source block directly below it using FE.").push("electricPump");
    }

    public static final ModConfigSpec.IntValue PUMP_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue PUMP_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 200, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue PUMP_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while pumping, before upgrades.")
            .defineInRange("energyPerTick", 10, 1, 1_000_000);

    public static final ModConfigSpec.IntValue PUMP_CYCLE_TICKS = BUILDER
            .comment("Ticks to pump one bucket (1,000 mB), before Speed upgrades.")
            .defineInRange("cycleTicks", 20, 1, 1_200);

    public static final ModConfigSpec.IntValue PUMP_TANK_CAPACITY = BUILDER
            .comment("Internal tank size in mB.")
            .defineInRange("tankCapacity", 16_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue PUMP_OUTPUT_RATE = BUILDER
            .comment("Most mB/t it pushes up out of its top face (and out of output faces with auto-eject).")
            .defineInRange("outputRate", 1_000, 1, 1_000_000);

    public static final ModConfigSpec.BooleanValue PUMP_INFINITE_WATER = BUILDER
            .comment("Leave water in place when it is an infinite source (2+ water sources beside it), like a bucket would refill.")
            .define("pumpInfiniteWater", true);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Arc Melter: melts rock into lava with FE. Recipes are data-driven (arcforge:melting).").push("arcMelter");
    }

    public static final ModConfigSpec.IntValue MELTER_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 40_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue MELTER_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in. Keep it at 16x energyPerTick or more, so 8 Speed upgrades can run flat out.")
            .defineInRange("maxEnergyInput", 2_000, 1, 1_000_000);

    public static final ModConfigSpec.IntValue MELTER_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while melting, before upgrades. With energyPerMb this sets the time: 250 mB at 50 FE/mB and 100 FE/t is 125 ticks.")
            .defineInRange("energyPerTick", 100, 1, 100_000);

    public static final ModConfigSpec.IntValue MELTER_ENERGY_PER_MB = BUILDER
            .comment("FE per mB of fluid made, before Energy upgrades. A recipe's \"energy\" field overrides it. 50 FE/mB is about 63% of",
                    "what a Geothermal and Thermoelectric Plant get back from the lava.")
            .defineInRange("energyPerMb", 50, 0, 100_000);

    public static final ModConfigSpec.IntValue MELTER_TANK_CAPACITY = BUILDER
            .comment("Internal tank size in mB.")
            .defineInRange("tankCapacity", 4_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue MELTER_OUTPUT_RATE = BUILDER
            .comment("Most mB/t it pushes out of its output faces.")
            .defineInRange("lavaOutputRate", 1_000, 1, 100_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Fiberizer: spins slag and basalt into mineral wool using FE and heat. Recipes are data-driven",
                "(arcforge:fiberizing) and set the FE/t, HU/t and minimum temperature.").push("fiberizer");
    }

    public static final ModConfigSpec.IntValue FIBERIZER_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue FIBERIZER_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 200, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue FIBERIZER_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 10_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue FIBERIZER_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 1_100, 21, 10_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Infuser: soaks vanilla wood in creosote with FE, making treated wood. Recipes are data-driven",
                "(arcforge:infusing) and set the fluid used.").push("infuser");
    }

    public static final ModConfigSpec.IntValue INFUSER_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue INFUSER_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 200, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue INFUSER_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while infusing, before upgrades.")
            .defineInRange("energyPerTick", 20, 1, 1_000_000);

    public static final ModConfigSpec.IntValue INFUSER_TANK_CAPACITY = BUILDER
            .comment("Fluid tank capacity in mB.")
            .defineInRange("tankCapacity", 8_000, 1_000, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Multiblock machines, and settings they share.").push("multiblocks");
    }

    static {
        BUILDER.comment("Shared multiblock settings.").push("multiblock");
    }

    public static final ModConfigSpec.IntValue MULTIBLOCK_PUSH_INTERVAL = BUILDER
            .comment("Ticks between pushes out of output and by-product faces into neighbouring inventories and tanks.")
            .defineInRange("autoPushInterval", 10, 1, 1_200);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Arc Crushing Array: 3x3x3 multiblock with three crushing lanes that doubles ores.").push("arcCrushingArray");
    }

    public static final ModConfigSpec.IntValue ARRAY_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 100_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue ARRAY_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 1_000, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue ARRAY_ENERGY_PER_TICK = BUILDER
            .comment("FE/t per working lane, before upgrades.")
            .defineInRange("energyPerTick", 16, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue ARRAY_TIME_MULTIPLIER = BUILDER
            .comment("Recipe time multiplier (0.5 = twice as fast as the Arc Crusher).")
            .defineInRange("timeMultiplier", 0.5, 0.01, 10.0);

    public static final ModConfigSpec.IntValue ARRAY_ORE_YIELD = BUILDER
            .comment("Main output multiplier for ore recipes (\"ore\": true).")
            .defineInRange("oreYield", 2, 1, 16);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Induction Furnace Array: 3x3x3 multiblock with three smelting lanes.").push("inductionFurnaceArray");
    }

    public static final ModConfigSpec.IntValue INDUCTION_ARRAY_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 100_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue INDUCTION_ARRAY_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 1_000, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue INDUCTION_ARRAY_ENERGY_PER_TICK = BUILDER
            .comment("FE/t per working lane, before upgrades.")
            .defineInRange("energyPerTick", 16, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue INDUCTION_ARRAY_TIME_MULTIPLIER = BUILDER
            .comment("Multiplier on the recipe's cooking time (0.25 = 50 ticks, twice as fast as the Induction Furnace).")
            .defineInRange("timeMultiplier", 0.25, 0.01, 10.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Metal Pressing Array: 3x3x3 multiblock with three pressing lanes, each with its own die.").push("metalPressingArray");
    }

    public static final ModConfigSpec.IntValue PRESSING_ARRAY_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 100_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue PRESSING_ARRAY_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 1_000, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue PRESSING_ARRAY_ENERGY_PER_TICK = BUILDER
            .comment("FE/t per working lane, before upgrades.")
            .defineInRange("energyPerTick", 16, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue PRESSING_ARRAY_TIME_MULTIPLIER = BUILDER
            .comment("Recipe time multiplier (0.5 = twice as fast as the Metal Press).")
            .defineInRange("timeMultiplier", 0.5, 0.01, 10.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Carbonizer: multiblock that bakes coal into coal coke, collecting creosote. Recipes are data-driven (arcforge:carbonizing).")
                .push("carbonizer");
    }

    public static final ModConfigSpec.IntValue CARBONIZER_MAX_SLICES = BUILDER
            .comment("Most slices (chambers) one Carbonizer can have. Each slice is 1 wide x 2 tall x 2 deep.")
            .defineInRange("maxSlices", 8, 1, 8);

    public static final ModConfigSpec.IntValue CARBONIZER_CREOSOTE_PER_VOLUME = BUILDER
            .comment("Creosote buffer capacity per block of slice volume, in mB (a 2x2 slice gets 4x this, a 3x3 slice 9x).")
            .defineInRange("creosotePerSliceBlock", 1_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue CARBONIZER_MIN_CREOSOTE_CAPACITY = BUILDER
            .comment("Smallest creosote buffer, in mB, whatever the slice count.")
            .defineInRange("minCreosoteCapacity", 8_000, 250, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
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
    }

    static {
        BUILDER.comment("Distillation Array: a 2x2 column 4, 6 or 8 tall that splits creosote into fractions with heat.",
                "Recipes are data-driven (arcforge:distilling). Values scale with the height (a 4-high column gets them as given).")
                .push("distillationArray");
    }

    public static final ModConfigSpec.IntValue DISTILLATION_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size in HU for a 4-high column.")
            .defineInRange("heatCapacity", 40_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue DISTILLATION_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 1_400, 400, 10_000);

    public static final ModConfigSpec.IntValue DISTILLATION_FEED_RATE = BUILDER
            .comment("Feed distilled per tick in mB, for a 4-high column (6 high: x1.5, 8 high: x2).")
            .defineInRange("feedPerTick", 10, 1, 100_000);

    public static final ModConfigSpec.IntValue DISTILLATION_FEED_CAPACITY = BUILDER
            .comment("Feed (creosote) tank size in mB.")
            .defineInRange("feedCapacity", 16_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue DISTILLATION_STEAM_CAPACITY = BUILDER
            .comment("Steam tank size in mB.")
            .defineInRange("steamCapacity", 8_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue DISTILLATION_OUTPUT_CAPACITY = BUILDER
            .comment("Size of each product tank (Naphtha, Light Oil, Heavy Oil) in mB.")
            .defineInRange("outputCapacity", 8_000, 1_000, 100_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Ore generation, per ore. Read when a world loads; changes apply to chunks generated after a restart.",
                "Turning an ore off only stops it generating: its items and recipes stay (other mods' ores tagged",
                "the same still work). Arcite can't be turned off.").push("ores");
    }

    // One ore's generation settings (enabled is null for arcite, which is always on).
    public record OreSettings(ModConfigSpec.@Nullable BooleanValue enabled, ModConfigSpec.IntValue veinsPerChunk,
            ModConfigSpec.IntValue veinSize, ModConfigSpec.IntValue minY, ModConfigSpec.IntValue maxY,
            ModConfigSpec.DoubleValue airExposureDiscard) {
        public boolean isEnabled() {
            return enabled == null || enabled.getAsBoolean();
        }
    }

    public static final Map<String, OreSettings> ORES = new LinkedHashMap<>();

    private static void ore(String key, String description, boolean toggle, int veins, int size, int minY, int maxY, double discard) {
        BUILDER.comment(description).push(key);
        ModConfigSpec.BooleanValue enabled = toggle ? BUILDER.comment("Whether it generates.").define("enabled", true) : null;
        ORES.put(key, new OreSettings(enabled,
                BUILDER.comment("Veins per chunk.").defineInRange("veinsPerChunk", veins, 0, 256),
                BUILDER.comment("Blocks per vein, at most.").defineInRange("veinSize", size, 1, 64),
                BUILDER.comment("Lowest Y it generates at.").defineInRange("minY", minY, -2_032, 2_031),
                BUILDER.comment("Highest Y it generates at.").defineInRange("maxY", maxY, -2_032, 2_031),
                BUILDER.comment("Chance an ore block touching air is left out (0 to 1).").defineInRange("airExposureDiscard", discard, 0.0, 1.0)));
        BUILDER.pop();
    }

    static {
        ore("silver", "Silver: common, mid-depth.", true, 8, 9, -32, 64, 0.0);
        ore("nickel", "Nickel: deep, near iron's lower band.", true, 6, 8, -64, 16, 0.0);
        ore("fluorite", "Fluorite: fairly common, mid-depth.", true, 8, 8, -16, 48, 0.0);
        ore("bismuth", "Bismuth: fairly common, mid-depth.", true, 8, 8, 0, 56, 0.0);
        ore("tungsten", "Tungsten (wolframite ore): uncommon and deep.", true, 6, 6, -64, -16, 0.0);
        ore("arcite", "Arcite: rare, the deepest; needs a diamond pickaxe.", false, 4, 5, -64, -40, 0.2);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
