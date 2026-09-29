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
            .defineInRange("lavaSourceHeat", 40, 0, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_MAGMA_HEAT = BUILDER
            .comment("Passive HU/t from each touching magma block (never consumed).")
            .defineInRange("magmaHeat", 16, 0, 10_000);

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
            .comment("Temperature of a full heat buffer, in °C. Each fuel burns no hotter than its own burn_temperature",
                    "(hydrogen 1,400°C).")
            .defineInRange("maxTemperature", 1_400, 21, 10_000);

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
            .comment("Receiver temperature at full sun, in °C. High-Pressure Steam needs 500 and a Superheater 900; at 1,100",
                    "a clear north-south tower stays above 900 from about 9:40 to 14:20.")
            .defineInRange("maxTemperature", 1_100, 100, 10_000);

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
        BUILDER.comment("Steam Boiler Array: a 3x3 boiler 3 to 7 blocks tall. Values are per block of height.").push("steamBoilerArray");
    }

    public static final ModConfigSpec.IntValue BOILER_ARRAY_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 1_400, 200, 10_000);

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
            .comment("Multiplier on the heat per mB of steam (0.8: 8 / 12 / 16 HU per mB of Steam / High-Pressure / Superheated).")
            .defineInRange("heatCostMultiplier", 0.8, 0.1, 10.0);

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

    public static final ModConfigSpec.IntValue TURBINE_ARRAY_EXHAUST_PER_LENGTH = BUILDER
            .comment("Exhaust Steam tank size in mB, per block of length (used only with an Exhaust port).")
            .defineInRange("exhaustTankCapacityPerLength", 32_000, 1_000, 100_000_000);

    public static final ModConfigSpec.DoubleValue TURBINE_ARRAY_VACUUM_BONUS = BUILDER
            .comment("Extra FE while an Exhaust port drains its spent steam (0.10 = +10%), on top of the lubricant bonus.")
            .defineInRange("vacuumBonus", 0.10, 0.0, 10.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Gas Turbine Array: a 3x3 turbine 5 to 9 blocks long that burns Fuel Burner fuels (those not marked",
                "\"gas_turbine\": false) straight to FE, and passes part of the heat out of its exhaust.").push("gasTurbineArray");
    }

    public static final ModConfigSpec.IntValue GAS_TURBINE_MAX_HU_PER_LENGTH = BUILDER
            .comment("Most fuel heat it burns in HU/t, per block of length (the limit is heat, not mB, so thin fuels aren't punished).")
            .defineInRange("maxFuelHuPerLength", 560, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue GAS_TURBINE_SIMPLE_CYCLE_FACTOR = BUILDER
            .comment("FE per HU of fuel burned, at or above the reference temperature.")
            .defineInRange("simpleCycleFactor", 1.5, 0.0, 100.0);

    public static final ModConfigSpec.IntValue GAS_TURBINE_REFERENCE_TEMPERATURE = BUILDER
            .comment("Burn temperature (°C) for full efficiency; a cooler fuel gets burnTemperature / this.")
            .defineInRange("referenceTemperature", 1_200, 1, 100_000);

    public static final ModConfigSpec.DoubleValue GAS_TURBINE_EXHAUST_FRACTION = BUILDER
            .comment("Share of the fuel heat that leaves as exhaust heat (HU) through its Heat ports.")
            .defineInRange("exhaustFraction", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue GAS_TURBINE_EXHAUST_TEMPERATURE_FACTOR = BUILDER
            .comment("Exhaust temperature as a share of the fuel's burn temperature.")
            .defineInRange("exhaustTemperatureFactor", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.IntValue GAS_TURBINE_MAX_RPM = BUILDER
            .comment("Rotor speed at full throttle.")
            .defineInRange("maxRpm", 12_000, 100, 100_000);

    public static final ModConfigSpec.DoubleValue GAS_TURBINE_SPIN_UP = BUILDER
            .comment("Share of the gap to its target speed the rotor closes each tick while speeding up.")
            .defineInRange("spinUp", 0.10, 0.001, 1.0);

    public static final ModConfigSpec.DoubleValue GAS_TURBINE_SPIN_DOWN = BUILDER
            .comment("Share of the gap to its target speed the rotor closes each tick while slowing down.")
            .defineInRange("spinDown", 0.04, 0.001, 1.0);

    public static final ModConfigSpec.DoubleValue GAS_TURBINE_LUBRICANT_SPIN_UP = BUILDER
            .comment("Spin-up multiplier while it has lubricant.")
            .defineInRange("lubricantSpinUpMultiplier", 1.5, 1.0, 10.0);

    public static final ModConfigSpec.IntValue GAS_TURBINE_IGNITION_TICKS = BUILDER
            .comment("Ticks the igniter runs before it burns fuel.")
            .defineInRange("ignitionTicks", 20, 1, 1_200);

    public static final ModConfigSpec.IntValue GAS_TURBINE_REIGNITION_DELAY = BUILDER
            .comment("Ticks after a flameout before it can ignite again.")
            .defineInRange("reignitionDelay", 20, 0, 1_200);

    public static final ModConfigSpec.IntValue GAS_TURBINE_TANK_PER_LENGTH = BUILDER
            .comment("Fuel tank size in mB, per block of length.")
            .defineInRange("fuelTankPerLength", 8_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue GAS_TURBINE_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 1_000_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue GAS_TURBINE_MAX_OUTPUT = BUILDER
            .comment("Most FE/t pushed out of its energy faces.")
            .defineInRange("maxEnergyOutput", 16_384, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue GAS_TURBINE_INTAKE_CHECK_INTERVAL = BUILDER
            .comment("Ticks between checks that the intake has air in front of it.")
            .defineInRange("intakeCheckInterval", 20, 1, 1_200);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Superheater Array: a 3x3x3 cube that upgrades steam one or two grades with heat, once it is at least as hot",
                "as the grade it makes.").push("superheaterArray");
    }

    public static final ModConfigSpec.IntValue SUPERHEATER_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size in HU (a full buffer is at the maximum temperature).")
            .defineInRange("heatCapacity", 200_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue SUPERHEATER_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 1_400, 200, 10_000);

    public static final ModConfigSpec.IntValue SUPERHEATER_MAX_HEAT_PER_TICK = BUILDER
            .comment("Most HU/t it takes in, and most it uses.")
            .defineInRange("maxHeatPerTick", 2_000, 1, 1_000_000);

    public static final ModConfigSpec.IntValue SUPERHEATER_TANK_CAPACITY = BUILDER
            .comment("Steam-in and steam-out tank sizes in mB (each).")
            .defineInRange("tankCapacity", 16_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue SUPERHEATER_MAX_FLOW = BUILDER
            .comment("Most steam it upgrades or passes through, in mB/t.")
            .defineInRange("maxFlow", 1_000, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue SUPERHEATER_HEAT_COST = BUILDER
            .comment("Multiplier on the heat per mB (the difference in HU/mB between the grades: 5 Steam to High-Pressure,",
                    "5 High-Pressure to Superheated, 10 Steam to Superheated). At 0.6 (3 / 3 / 6 HU), boiling plain Steam and",
                    "superheating it (8 + 6 = 14 HU per mB) beats boiling Superheated Steam directly (16), and only the",
                    "superheating needs 900°C heat.")
            .defineInRange("heatCostMultiplier", 0.6, 0.1, 10.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Condenser Array: a 3x3x3 cube that turns Exhaust Steam back into water, 1:1, faster with water and ice around",
                "it and in cold biomes.").push("condenserArray");
    }

    public static final ModConfigSpec.IntValue CONDENSER_TANK_CAPACITY = BUILDER
            .comment("Exhaust-in and water-out tank sizes in mB (each).")
            .defineInRange("tankCapacity", 16_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue CONDENSER_BASE_RATE = BUILDER
            .comment("mB/t it condenses in open air.")
            .defineInRange("baseRate", 120, 0, 1_000_000);

    public static final ModConfigSpec.IntValue CONDENSER_WATER_BONUS = BUILDER
            .comment("mB/t added by each water source touching its outer faces.")
            .defineInRange("waterSourceBonus", 20, 0, 1_000_000);

    public static final ModConfigSpec.IntValue CONDENSER_ICE_BONUS = BUILDER
            .comment("mB/t added by each ice block touching its outer faces.")
            .defineInRange("iceBonus", 30, 0, 1_000_000);

    public static final ModConfigSpec.IntValue CONDENSER_PACKED_ICE_BONUS = BUILDER
            .comment("mB/t added by each packed ice block touching its outer faces.")
            .defineInRange("packedIceBonus", 40, 0, 1_000_000);

    public static final ModConfigSpec.IntValue CONDENSER_BLUE_ICE_BONUS = BUILDER
            .comment("mB/t added by each blue ice block touching its outer faces.")
            .defineInRange("blueIceBonus", 60, 0, 1_000_000);

    public static final ModConfigSpec.DoubleValue CONDENSER_COLD_MULTIPLIER = BUILDER
            .comment("Multiplier in cold biomes (or anywhere cold enough to snow).")
            .defineInRange("coldBiomeMultiplier", 1.25, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue CONDENSER_NETHER_MULTIPLIER = BUILDER
            .comment("Multiplier where water evaporates (the Nether).")
            .defineInRange("netherMultiplier", 0.5, 0.0, 10.0);

    public static final ModConfigSpec.IntValue CONDENSER_MAX_RATE = BUILDER
            .comment("Most mB/t it condenses, whatever cools it.")
            .defineInRange("maxRate", 400, 1, 1_000_000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CONDENSER_COLD_BIOME_TAGS = BUILDER
            .comment("Biome tags counted as cold.")
            .defineListAllowEmpty("coldBiomeTags", List.of("c:is_cold"), () -> "c:is_cold", value -> value instanceof String);

    public static final ModConfigSpec.IntValue CONDENSER_SCAN_INTERVAL = BUILDER
            .comment("Ticks between looks at what's touching it (and at the biome).")
            .defineInRange("scanInterval", 40, 1, 1_200);

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
        BUILDER.comment("Fermenter: ferments crops and water into Ethanol with FE. Recipes are data-driven (arcforge:fermenting)",
                "and set the time; FE per operation is time x energyPerTick unless a recipe sets its own energy.").push("fermenter");
    }

    public static final ModConfigSpec.IntValue FERMENTER_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while fermenting, before upgrades.")
            .defineInRange("energyPerTick", 10, 1, 1_000_000);

    public static final ModConfigSpec.IntValue FERMENTER_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue FERMENTER_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 200, 1, 1_000_000);

    public static final ModConfigSpec.IntValue FERMENTER_WATER_TANK = BUILDER
            .comment("Water tank size in mB.")
            .defineInRange("waterTank", 8_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue FERMENTER_ETHANOL_TANK = BUILDER
            .comment("Ethanol tank size in mB.")
            .defineInRange("ethanolTank", 8_000, 1_000, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Chemical Reactor: reacts an item and up to two fluids with FE. Recipes are data-driven (arcforge:chemical_reacting)",
                "and set the time; a recipe can set its own energy_per_tick.").push("chemicalReactor");
    }

    public static final ModConfigSpec.IntValue REACTOR_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 40_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue REACTOR_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in. Keep it at 16x energyPerTick or more, so 8 Speed upgrades can run flat out.")
            .defineInRange("maxEnergyInput", 1_000, 1, 1_000_000);

    public static final ModConfigSpec.IntValue REACTOR_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while reacting, before upgrades, for recipes that don't set their own.")
            .defineInRange("energyPerTick", 60, 1, 100_000);

    public static final ModConfigSpec.IntValue REACTOR_TANK_CAPACITY = BUILDER
            .comment("Size in mB of each of its three tanks (two inputs, one output).")
            .defineInRange("tankCapacity", 8_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue REACTOR_OUTPUT_RATE = BUILDER
            .comment("Most mB/t it pushes out of its output faces with auto-eject.")
            .defineInRange("fluidOutputRate", 1_000, 1, 100_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Electrolyzer: splits water into Hydrogen and Oxygen with FE. Recipes are data-driven (arcforge:electrolyzing).",
                "Whatever the settings, an operation never costs less than balanceSafetyFactor times the FE the best setup",
                "could get back by burning what it makes (see EnergyBalance), so hydrogen is never free power.").push("electrolyzer");
    }

    public static final ModConfigSpec.IntValue ELECTROLYZER_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 100_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue ELECTROLYZER_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in. Keep it at 16x energyPerTick or more, so 8 Speed upgrades can run flat out.")
            .defineInRange("maxEnergyInput", 8_000, 1, 10_000_000);

    public static final ModConfigSpec.IntValue ELECTROLYZER_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while splitting, before Speed upgrades. With the FE per operation this sets the time.")
            .defineInRange("energyPerTick", 400, 1, 1_000_000);

    public static final ModConfigSpec.IntValue ELECTROLYZER_ENERGY_PER_MB_INPUT = BUILDER
            .comment("FE per mB of input (water), before Energy upgrades. A recipe's \"energy\" field overrides it.",
                    "1,200 FE per mB of water is 600 per mB of hydrogen.")
            .defineInRange("energyPerMbInput", 1_200, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ELECTROLYZER_WATER_CAPACITY = BUILDER
            .comment("Water tank size in mB.")
            .defineInRange("waterTankCapacity", 8_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue ELECTROLYZER_GAS_CAPACITY = BUILDER
            .comment("Hydrogen and Oxygen tank sizes in mB (each).")
            .defineInRange("gasTankCapacity", 16_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue ELECTROLYZER_GAS_OUTPUT_RATE = BUILDER
            .comment("Most mB/t of each gas it pushes out of its Hydrogen and Oxygen faces.")
            .defineInRange("gasOutputRate", 1_000, 1, 100_000);

    public static final ModConfigSpec.DoubleValue ELECTROLYZER_BALANCE_SAFETY_FACTOR = BUILDER
            .comment("Least FE an operation costs, as a multiple of the most FE its products can give back when burnt in the",
                    "best setup (1.25: always at least 25% more). Never below 1.0.")
            .defineInRange("balanceSafetyFactor", 1.25, 1.0, 10.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Assembler: crafts any crafting recipe set in its 3x3 pattern from the ingredients in its buffer, with FE.")
                .push("assembler");
    }

    public static final ModConfigSpec.IntValue ASSEMBLER_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue ASSEMBLER_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 1_000, 1, 1_000_000);

    public static final ModConfigSpec.IntValue ASSEMBLER_ENERGY_PER_CRAFT = BUILDER
            .comment("FE per craft, before Energy upgrades.")
            .defineInRange("energyPerCraft", 400, 0, 1_000_000);

    public static final ModConfigSpec.IntValue ASSEMBLER_CRAFT_TICKS = BUILDER
            .comment("Ticks per craft, before Speed upgrades.")
            .defineInRange("craftTicks", 20, 1, 10_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Block Breaker: breaks the block in front of it into its inventory with FE, dropping what the right tool",
                "would (no enchantments). Harder blocks take longer.").push("blockBreaker");
    }

    public static final ModConfigSpec.IntValue BREAKER_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue BREAKER_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 1_000, 1, 1_000_000);

    public static final ModConfigSpec.IntValue BREAKER_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while breaking, before upgrades.")
            .defineInRange("energyPerTick", 40, 0, 100_000);

    public static final ModConfigSpec.IntValue BREAKER_TICKS_PER_HARDNESS = BUILDER
            .comment("Ticks per point of the block's hardness (stone 1.5, iron ore 3, obsidian 50), before Speed upgrades.")
            .defineInRange("ticksPerHardness", 4, 1, 1_000);

    public static final ModConfigSpec.IntValue BREAKER_MIN_TICKS = BUILDER
            .comment("Fewest ticks a break takes, before Speed upgrades.")
            .defineInRange("minBreakTicks", 4, 1, 1_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Block Placer: places blocks from its inventory in front of it, with FE.").push("blockPlacer");
    }

    public static final ModConfigSpec.IntValue PLACER_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 10_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue PLACER_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 500, 1, 1_000_000);

    public static final ModConfigSpec.IntValue PLACER_ENERGY_PER_PLACE = BUILDER
            .comment("FE per block placed, before Energy upgrades.")
            .defineInRange("energyPerPlace", 20, 0, 100_000);

    public static final ModConfigSpec.IntValue PLACER_INTERVAL = BUILDER
            .comment("Ticks between placements, before Speed upgrades.")
            .defineInRange("placeInterval", 4, 1, 1_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Vacuum Collector: pulls up dropped items within its range into its buffer, with FE.").push("vacuumCollector");
    }

    public static final ModConfigSpec.IntValue VACUUM_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue VACUUM_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 1_000, 1, 1_000_000);

    public static final ModConfigSpec.IntValue VACUUM_ENERGY_PER_ITEM = BUILDER
            .comment("FE per item entity picked up (a whole stack is one), before Energy upgrades.")
            .defineInRange("energyPerItem", 10, 0, 100_000);

    public static final ModConfigSpec.IntValue VACUUM_DEFAULT_RANGE = BUILDER
            .comment("Range a new collector starts at, in blocks out from it on every axis.")
            .defineInRange("defaultRange", 5, 1, 16);

    public static final ModConfigSpec.IntValue VACUUM_MAX_RANGE = BUILDER
            .comment("Largest range it can be set to.")
            .defineInRange("maxRange", 9, 1, 16);

    public static final ModConfigSpec.IntValue VACUUM_SCAN_INTERVAL = BUILDER
            .comment("Ticks between scans for items, before Speed upgrades.")
            .defineInRange("scanInterval", 5, 1, 1_000);

    public static final ModConfigSpec.IntValue VACUUM_MIN_ITEM_AGE = BUILDER
            .comment("Ticks an item must have been on the ground before it's taken (so a player's own drops aren't snatched at once).")
            .defineInRange("minItemAge", 10, 0, 6_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Arc Quarry: a 3x3x3 digital miner that mines a square area top-down, keeping only the blocks its filter picks.")
                .push("arcQuarry");
    }

    public static final ModConfigSpec.IntValue QUARRY_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 500_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue QUARRY_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 20_000, 1, 10_000_000);

    public static final ModConfigSpec.IntValue QUARRY_ENERGY_PER_BLOCK = BUILDER
            .comment("FE per block mined, before Energy upgrades.")
            .defineInRange("energyPerBlock", 200, 0, 1_000_000);

    public static final ModConfigSpec.DoubleValue QUARRY_SILK_MULTIPLIER = BUILDER
            .comment("Multiplier on the FE per block with Silk Touch on.")
            .defineInRange("silkTouchMultiplier", 5.0, 1.0, 100.0);

    public static final ModConfigSpec.IntValue QUARRY_TICKS_PER_BLOCK = BUILDER
            .comment("Ticks per block mined, before Speed upgrades.")
            .defineInRange("ticksPerBlock", 20, 1, 1_000);

    public static final ModConfigSpec.IntValue QUARRY_DEFAULT_RADIUS = BUILDER
            .comment("Radius a new quarry starts with (the area is 2 x radius + 1 blocks square).")
            .defineInRange("defaultRadius", 10, 0, 64);

    public static final ModConfigSpec.IntValue QUARRY_MAX_RADIUS = BUILDER
            .comment("Largest radius it can be set to.")
            .defineInRange("maxRadius", 32, 0, 64);

    public static final ModConfigSpec.IntValue QUARRY_DEFAULT_MIN_Y = BUILDER
            .comment("Lowest layer a new quarry mines.")
            .defineInRange("defaultMinY", 0, -2_048, 2_048);

    public static final ModConfigSpec.IntValue QUARRY_DEFAULT_MAX_Y = BUILDER
            .comment("Highest layer a new quarry mines.")
            .defineInRange("defaultMaxY", 60, -2_048, 2_048);

    public static final ModConfigSpec.IntValue QUARRY_SCAN_PER_TICK = BUILDER
            .comment("Positions it scans per tick (a radius-32 area 384 blocks deep is about 1.6 million positions: 2.5 seconds).")
            .defineInRange("scanBlocksPerTick", 32_768, 64, 262_144);

    public static final ModConfigSpec.BooleanValue QUARRY_CHUNK_LOADING = BUILDER
            .comment("Keep the quarry's chunk and the chunk it's mining (or scanning) loaded while it works.")
            .define("chunkLoading", false);

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

    public static final ModConfigSpec.IntValue FURNACE_OXYGEN_CAPACITY = BUILDER
            .comment("Oxygen tank size in mB (fed through Oxygen ports).")
            .defineInRange("oxygenTankCapacity", 4_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue FURNACE_OXYGEN_PER_SMELT = BUILDER
            .comment("Oxygen a smelt uses when it starts, in mB. With less than this it runs at normal speed.")
            .defineInRange("oxygenPerSmelt", 50, 1, 100_000);

    public static final ModConfigSpec.DoubleValue FURNACE_OXYGEN_SPEED = BUILDER
            .comment("How much faster a smelt with oxygen runs (1.5: in 1/1.5 of the time).")
            .defineInRange("oxygenSpeedMultiplier", 1.5, 1.0, 100.0);

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

    // --- Machine security ---

    static {
        BUILDER.comment("Who may use, configure and break machines. Every machine, multiblock and storage block records the player",
                "who placed it; its owner's Security Terminal profile (or the block's own override) decides who else may use it.",
                "Conduits, hoppers and other automation always work.").push("security");
    }

    public static final ModConfigSpec.BooleanValue SECURITY_ENABLED = BUILDER
            .comment("Whether security is enforced. Off, anyone may use anything (owners are still recorded).")
            .define("enabled", true);

    public static final ModConfigSpec.BooleanValue SECURITY_OPS_BYPASS = BUILDER
            .comment("Whether operators (permission level 2) may use and edit everything.")
            .define("opsBypass", true);

    public static final ModConfigSpec.EnumValue<net.zagdrath.arcforge.security.SecurityMode> SECURITY_DEFAULT_MODE = BUILDER
            .comment("The mode of a player who never set one at a Security Terminal.")
            .defineEnum("defaultMode", net.zagdrath.arcforge.security.SecurityMode.PUBLIC);

    static {
        BUILDER.pop();
    }

    // --- The Foundry Suit ---

    static {
        BUILDER.comment("Foundry Suit: fire-resistant armour. Fire and hot-block damage is #arcforge:foundry_resists.").push("foundrySuit");
    }

    public static final ModConfigSpec.DoubleValue FOUNDRY_FIRE_REDUCTION = BUILDER
            .comment("Share of fire and hot-block damage each piece cuts (the full set stops it all).")
            .defineInRange("fireReductionPerPiece", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.IntValue FOUNDRY_LAVA_SHIELD_TICKS = BUILDER
            .comment("Ticks in lava the full set protects from lava damage.")
            .defineInRange("lavaShieldTicks", 160, 0, 72_000);

    public static final ModConfigSpec.IntValue FOUNDRY_LAVA_COOLDOWN_TICKS = BUILDER
            .comment("Ticks out of lava before the shield refills.")
            .defineInRange("lavaCooldownTicks", 1_200, 1, 72_000);

    static {
        BUILDER.pop();
    }

    // --- Tools: the Jetpack, Arc Drill and Arc Saw ---

    static {
        BUILDER.comment("Jetpacks, Arc Drills and Arc Saws. Per-tier values are listed Tempered, Hardened, Arcforged.").push("tools");
    }

    public static final ModConfigSpec.BooleanValue ENABLE_JETPACKS = BUILDER
            .comment("Whether jetpacks fly. Off, they can still be worn and filled.")
            .define("enableJetpacks", true);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> JETPACK_TANK = BUILDER
            .comment("Jetpack tank size in mB, per tier.")
            .defineList("jetpackTank", List.of(16_000, 64_000, 256_000), () -> 16_000, value -> value instanceof Integer i && i > 0);

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> JETPACK_MAX_RISE = BUILDER
            .comment("Fastest a jetpack climbs, in blocks per tick, per tier.")
            .defineList("jetpackMaxRise", List.of(0.5, 0.65, 0.8), () -> 0.5, value -> value instanceof Double d && d > 0);

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> JETPACK_AIR_SPEED = BUILDER
            .comment("Horizontal push while thrusting, in blocks per tick per tick, per tier.")
            .defineList("jetpackAirSpeed", List.of(0.03, 0.035, 0.04), () -> 0.03, value -> value instanceof Double d && d >= 0);

    public static final ModConfigSpec.DoubleValue JETPACK_HOVER_FUEL_MULTIPLIER = BUILDER
            .comment("Fuel used per tick in Hover mode, as a multiple of the fuel's normal rate.")
            .defineInRange("hoverFuelMultiplier", 1.5, 0.0, 100.0);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> ARC_TOOL_CAPACITY = BUILDER
            .comment("Arc Drill and Arc Saw FE capacity, per tier.")
            .defineList("arcToolCapacity", List.of(100_000, 400_000, 1_600_000), () -> 100_000, value -> value instanceof Integer i && i > 0);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> ARC_TOOL_RECEIVE = BUILDER
            .comment("Arc Drill and Arc Saw charge rate in FE/t, per tier.")
            .defineList("arcToolReceive", List.of(2_000, 8_000, 32_000), () -> 2_000, value -> value instanceof Integer i && i > 0);

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> ARC_TOOL_SPEED = BUILDER
            .comment("Arc Drill and Arc Saw mining speed, per tier (a diamond pickaxe is 8, netherite 9).")
            .defineList("arcToolSpeed", List.of(8.0, 10.0, 14.0), () -> 8.0, value -> value instanceof Double d && d > 0);

    public static final ModConfigSpec.IntValue ARC_TOOL_BASE_FE = BUILDER
            .comment("FE per block before hardness and modules: cost = base x (1 + hardnessFactor x hardness) x (1 + module factors).")
            .defineInRange("baseFePerBlock", 50, 0, 1_000_000);

    public static final ModConfigSpec.DoubleValue ARC_TOOL_HARDNESS_FACTOR = BUILDER
            .comment("How much each point of block hardness adds to the FE per block.")
            .defineInRange("hardnessFactor", 0.25, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue MODULE_AREA_FE = BUILDER
            .comment("Extra FE per block with the Area module on, as a fraction of the base.")
            .defineInRange("areaModuleFe", 0.25, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue MODULE_SILK_FE = BUILDER
            .comment("Extra FE per block with the Silk Touch module on.")
            .defineInRange("silkTouchModuleFe", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue MODULE_FORTUNE_FE = BUILDER
            .comment("Extra FE per block per Fortune level with a Fortune module on.")
            .defineInRange("fortuneModuleFe", 0.5, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue MODULE_VEIN_FE = BUILDER
            .comment("Extra FE per block with the Vein Mining module on.")
            .defineInRange("veinModuleFe", 0.25, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue MODULE_SPEED_FE = BUILDER
            .comment("Extra FE per block with the Speed module on.")
            .defineInRange("speedModuleFe", 0.5, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue SPEED_MODULE_MULTIPLIER = BUILDER
            .comment("Mining speed multiplier with the Speed module on.")
            .defineInRange("speedModuleMultiplier", 1.5, 1.0, 100.0);

    public static final ModConfigSpec.IntValue VEIN_LIMIT = BUILDER
            .comment("Most blocks the Vein Mining module breaks at once on an Arc Drill, the first included.")
            .defineInRange("veinLimit", 64, 1, 4_096);

    public static final ModConfigSpec.IntValue FELLING_LIMIT = BUILDER
            .comment("Most logs an Arc Saw fells at once.")
            .defineInRange("fellingLimit", 32, 1, 4_096);

    public static final ModConfigSpec.IntValue FELLING_VEIN_LIMIT = BUILDER
            .comment("Most logs an Arc Saw fells at once with the Vein Mining module on.")
            .defineInRange("fellingVeinLimit", 256, 1, 4_096);

    public static final ModConfigSpec.DoubleValue FELLING_FE_MULTIPLIER = BUILDER
            .comment("Multiplier on the FE for each log felled after the first.")
            .defineInRange("fellingFeMultiplier", 1.0, 0.0, 100.0);

    static {
        BUILDER.pop();
    }

    // A per-tier list value (Tempered, Hardened, Arcforged); a short list repeats its last entry.
    public static int perTier(ModConfigSpec.ConfigValue<List<? extends Integer>> value, int tierIndex) {
        List<? extends Integer> list = value.get();
        return list.get(Math.min(tierIndex, list.size() - 1));
    }

    public static double perTierDouble(ModConfigSpec.ConfigValue<List<? extends Double>> value, int tierIndex) {
        List<? extends Double> list = value.get();
        return list.get(Math.min(tierIndex, list.size() - 1));
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
        ore("sulfur", "Nether Sulfur Ore: common through the Nether's netherrack; drops Sulfur Dust.", true, 12, 10, 10, 117, 0.0);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
