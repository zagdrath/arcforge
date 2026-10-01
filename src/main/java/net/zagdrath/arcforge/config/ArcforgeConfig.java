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

    public static final ModConfigSpec.IntValue FIREBOX_OXYGEN_TANK_CAPACITY = BUILDER
            .comment("Oxygen tank capacity in mB (oxy-fuel, see oxyFuel).")
            .defineInRange("oxygenTankCapacity", 2_000, 100, 1_000_000);

    public static final ModConfigSpec.DoubleValue FIREBOX_OXYGEN_PER_TICK = BUILDER
            .comment("mB of oxygen burnt per tick of oxy-fuel.")
            .defineInRange("oxygenPerTick", 0.25, 0.001, 1_000.0);

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

    public static final ModConfigSpec.IntValue FUEL_BURNER_OXYGEN_TANK_CAPACITY = BUILDER
            .comment("Oxygen tank capacity in mB (oxy-fuel, see oxyFuel).")
            .defineInRange("oxygenTankCapacity", 2_000, 100, 1_000_000);

    public static final ModConfigSpec.DoubleValue FUEL_BURNER_OXYGEN_PER_TICK = BUILDER
            .comment("mB of oxygen burnt per tick of oxy-fuel.")
            .defineInRange("oxygenPerTick", 0.25, 0.001, 1_000.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Oxy-fuel: a Firebox or Fuel Burner fed oxygen through an Oxygen face burns hotter and makes more heat",
                "per fuel while the oxygen lasts. Without oxygen they run as normal.").push("oxyFuel");
    }

    public static final ModConfigSpec.IntValue OXY_FUEL_TEMPERATURE_BONUS = BUILDER
            .comment("°C added to the burn temperature (the Firebox's maximum, or the fuel's).")
            .defineInRange("temperatureBonus", 300, 0, 2_000);

    public static final ModConfigSpec.IntValue OXY_FUEL_MAX_TEMPERATURE = BUILDER
            .comment("The hottest oxy-fuel burns, in °C.")
            .defineInRange("maxTemperature", 1_600, 21, 10_000);

    public static final ModConfigSpec.DoubleValue OXY_FUEL_HEAT_MULTIPLIER = BUILDER
            .comment("Heat made per fuel on oxy-fuel, as a multiple of the normal heat.")
            .defineInRange("heatMultiplier", 1.25, 1.0, 10.0);

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

    public static final ModConfigSpec.IntValue GAS_TURBINE_SPOOL_TIME = BUILDER
            .comment("Fewest ticks the rotor takes from standstill to full speed: however far the throttle jumps, it speeds up",
                    "by at most full speed / spoolTime each tick (Heavy Oil or Seed Oil lubricant shortens it like spinUp).")
            .defineInRange("spoolTime", 200, 1, 12_000);

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

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PUMP_SEAWATER_BIOME_TAGS = BUILDER
            .comment("Biome tags where water pumps up as Seawater (ocean and beach biomes).")
            .defineListAllowEmpty("seawaterBiomeTags", List.of("minecraft:is_ocean", "minecraft:is_beach"), () -> "minecraft:is_ocean",
                    value -> value instanceof String);

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

    public static final ModConfigSpec.DoubleValue FERMENTER_ADDITIVE_BONUS = BUILDER
            .comment("Extra Ethanol from each operation while the additive slot holds Dried Hops (#arcforge:fermenter_additives): 0.2 = +20%.")
            .defineInRange("additiveBonus", 0.2, 0.0, 10.0);

    public static final ModConfigSpec.IntValue FERMENTER_ADDITIVE_OPERATIONS = BUILDER
            .comment("Operations one Dried Hops lasts.")
            .defineInRange("additiveOperations", 4, 1, 1_000);

    public static final ModConfigSpec.DoubleValue FERMENTER_CO2_PER_ETHANOL = BUILDER
            .comment("Carbon Dioxide given off per mB of Ethanol made. It leaves through Gas Output faces; what doesn't fit in the "
                    + "tank goes into the air.")
            .defineInRange("carbonDioxidePerEthanol", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.IntValue FERMENTER_CO2_TANK = BUILDER
            .comment("Carbon Dioxide tank size in mB.")
            .defineInRange("carbonDioxideTank", 4_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue FERMENTER_GAS_OUTPUT_RATE = BUILDER
            .comment("Most mB/t of Carbon Dioxide it pushes out of its Gas Output faces (shared across them).")
            .defineInRange("gasOutputRate", 100, 1, 1_000_000);

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
            .comment("Size in mB of each of its tanks (three inputs, the output and the liquid by-product).")
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

    public static final ModConfigSpec.IntValue ELECTROLYZER_LIQUID_CAPACITY = BUILDER
            .comment("Liquid output tank size in mB: a recipe's third product (Lye, from Brine).")
            .defineInRange("liquidTankCapacity", 8_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue ELECTROLYZER_GAS_OUTPUT_RATE = BUILDER
            .comment("Most mB/t of each product it pushes out of its Hydrogen, Oxygen and Output faces.")
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
        BUILDER.comment("Thermal Evaporator Array: a fixed 3x3x9 tower that boils fluids down with heat (Seawater into Brine, Brine",
                "into Salt), returning most of the steam as Water. Recipes are data-driven (arcforge:evaporating) and set the heat per",
                "operation; how fast it works is set here.").push("thermalEvaporator");
    }

    public static final ModConfigSpec.IntValue EVAPORATOR_THROUGHPUT = BUILDER
            .comment("mB of input evaporated per tick at fullSpeedTemperature or hotter.")
            .defineInRange("throughput", 25, 1, 100_000);

    public static final ModConfigSpec.IntValue EVAPORATOR_MIN_TEMPERATURE = BUILDER
            .comment("It works only at this temperature (°C) or hotter.")
            .defineInRange("minTemperature", 100, 20, 10_000);

    public static final ModConfigSpec.IntValue EVAPORATOR_FULL_SPEED_TEMPERATURE = BUILDER
            .comment("At this temperature (°C) or hotter it runs at its full throughput.")
            .defineInRange("fullSpeedTemperature", 400, 21, 10_000);

    public static final ModConfigSpec.DoubleValue EVAPORATOR_MIN_SPEED = BUILDER
            .comment("Share of its throughput at minTemperature; it rises in a straight line to 1 at fullSpeedTemperature.")
            .defineInRange("minSpeed", 0.2, 0.01, 1.0);

    public static final ModConfigSpec.IntValue EVAPORATOR_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size in HU.")
            .defineInRange("heatCapacity", 200_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue EVAPORATOR_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C. Heat only flows in from something hotter.")
            .defineInRange("maxTemperature", 1_000, 100, 10_000);

    public static final ModConfigSpec.IntValue EVAPORATOR_INPUT_CAPACITY = BUILDER
            .comment("Input tank (Seawater or Brine) size in mB.")
            .defineInRange("inputCapacity", 32_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue EVAPORATOR_OUTPUT_CAPACITY = BUILDER
            .comment("Brine (fluid result) tank size in mB.")
            .defineInRange("outputCapacity", 16_000, 1_000, 100_000_000);

    public static final ModConfigSpec.IntValue EVAPORATOR_WATER_CAPACITY = BUILDER
            .comment("Returned Water tank size in mB. When it's full, the water that doesn't fit is lost as steam and evaporating",
                    "carries on.")
            .defineInRange("waterCapacity", 16_000, 1_000, 100_000_000);

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
        ore("halite", "Halite: large flat beds of Rock Salt (veinsPerChunk: beds tried in 1 chunk in 4; veinSize: each small vein of a bed).",
                true, 1, 12, -32, 40, 0.0);
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Halite beds: how the ore's flat beds are laid (see ores.halite for whether, how often and how deep).")
                .push("haliteBeds");
    }

    public static final ModConfigSpec.IntValue HALITE_BED_RADIUS = BUILDER
            .comment("Largest radius of a bed, in blocks (each bed is an oval between 60% and 100% of it each way).")
            .defineInRange("radius", 11, 3, 14);

    public static final ModConfigSpec.IntValue HALITE_BED_LAYERS = BUILDER
            .comment("How many vein layers a bed is laid in (each about 2 blocks thick).")
            .defineInRange("layers", 1, 1, 8);

    public static final ModConfigSpec.IntValue HALITE_THICK_BED_LAYERS = BUILDER
            .comment("Layers under thickBiomeTags (deserts and oceans, where the old seas dried).")
            .defineInRange("thickLayers", 3, 1, 8);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> HALITE_THICK_BIOME_TAGS = BUILDER
            .comment("Biome tags (at the bed's centre) whose beds use thickLayers.")
            .defineListAllowEmpty("thickBiomeTags", List.of("c:is_desert", "minecraft:is_ocean", "minecraft:is_deep_ocean"), () -> "c:is_desert",
                    value -> value instanceof String);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Energy, Heat, Fluid and Gas Meters: pass flow from their left side to their right, up to a cap per tick, and",
                "report the rate. The caps default to the Arcforged conduit rates.").push("meters");
    }

    public static final ModConfigSpec.IntValue METER_ENERGY_CAP = BUILDER
            .comment("Most FE an Energy Meter passes per tick.")
            .defineInRange("energyRateCap", net.zagdrath.arcforge.conduit.ConduitTier.ARCFORGED.energyPerTick(), 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue METER_HEAT_CAP = BUILDER
            .comment("Most HU a Heat Meter passes per tick.")
            .defineInRange("heatRateCap", net.zagdrath.arcforge.conduit.ConduitTier.ARCFORGED.heatPerTick(), 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue METER_FLUID_CAP = BUILDER
            .comment("Most mB of liquid a Fluid Meter passes per tick.")
            .defineInRange("fluidRateCap", net.zagdrath.arcforge.conduit.ConduitTier.ARCFORGED.fluidPerTick(), 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue METER_GAS_CAP = BUILDER
            .comment("Most mB of gas a Gas Meter passes per tick.")
            .defineInRange("gasRateCap", net.zagdrath.arcforge.conduit.ConduitTier.ARCFORGED.gasPerTick(), 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue METER_SMOOTHING_TICKS = BUILDER
            .comment("The rate shown is the average over this many ticks.")
            .defineInRange("smoothingTicks", 20, 1, 1_200);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Chargepad: charges the FE items of players standing on it, from FE fed into its back.").push("chargepad");
    }

    public static final ModConfigSpec.IntValue CHARGEPAD_CAPACITY = BUILDER
            .comment("FE buffer size.")
            .defineInRange("energyCapacity", 500_000, 1_000, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CHARGEPAD_MAX_INPUT = BUILDER
            .comment("Most FE it takes in per tick.")
            .defineInRange("maxInput", 8_192, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CHARGEPAD_RATE_PER_ITEM = BUILDER
            .comment("Most FE it gives one item per tick.")
            .defineInRange("chargeRatePerItem", 2_048, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CHARGEPAD_MAX_TRANSFER = BUILDER
            .comment("Most FE it gives out per tick in all.")
            .defineInRange("maxTransferPerTick", 8_192, 1, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
    }

    // --- Farming ---

    static {
        BUILDER.comment("Farming: the Compost Bin, fertilizers, Loam and Loam Farmland, and the crops.").push("farming");
    }

    static {
        BUILDER.comment("Compost Bin: fills with plant matter like a vanilla composter (each item's chance is the",
                "neoforge:compostables data map) and, once full, turns it into Compost.").push("compostBin");
    }

    public static final ModConfigSpec.IntValue COMPOST_BIN_ITEM_INTERVAL = BUILDER
            .comment("Ticks between items taken from its input slot (items fed by hoppers and conduits wait there).")
            .defineInRange("itemInterval", 10, 1, 1_200);

    public static final ModConfigSpec.IntValue COMPOST_BIN_READY_DELAY = BUILDER
            .comment("Ticks from full to the Compost being ready.")
            .defineInRange("readyDelay", 40, 1, 72_000);

    public static final ModConfigSpec.IntValue COMPOST_BIN_YIELD = BUILDER
            .comment("Compost made from a full bin.")
            .defineInRange("compostPerBatch", 1, 1, 64);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Fertilizers: nutrients each adds to Loam Farmland (which holds 0 to 15).").push("fertilizers");
    }

    public static final ModConfigSpec.IntValue COMPOST_NUTRIENTS = BUILDER
            .comment("Nutrients from one Compost.")
            .defineInRange("compost", 2, 0, 15);

    public static final ModConfigSpec.IntValue WOOD_ASH_NUTRIENTS = BUILDER
            .comment("Nutrients from one Wood Ash.")
            .defineInRange("woodAsh", 3, 0, 15);

    public static final ModConfigSpec.IntValue BASIC_SLAG_NUTRIENTS = BUILDER
            .comment("Nutrients from one Basic Slag.")
            .defineInRange("basicSlag", 3, 0, 15);

    public static final ModConfigSpec.IntValue MIXED_FERTILIZER_NUTRIENTS = BUILDER
            .comment("Nutrients from one Mixed Fertilizer.")
            .defineInRange("mixedFertilizer", 8, 0, 15);

    public static final ModConfigSpec.IntValue SEED_MEAL_NUTRIENTS = BUILDER
            .comment("Nutrients from one Seed Meal (milled seeds).")
            .defineInRange("seedMeal", 1, 0, 15);

    public static final ModConfigSpec.IntValue NPK_NUTRIENTS = BUILDER
            .comment("Nutrients from one NPK Fertilizer. It also enriches the farmland (see loamFarmland.npkGrowthMultiplier).")
            .defineInRange("npk", 15, 0, 15);

    public static final ModConfigSpec.IntValue DIGESTATE_NUTRIENTS = BUILDER
            .comment("Nutrients from one Digestate (the Biogas Digester's by-product).")
            .defineInRange("digestate", 4, 0, 15);

    public static final ModConfigSpec.DoubleValue WOOD_ASH_CHANCE = BUILDER
            .comment("Chance that burning one item of #arcforge:leaves_wood_ash (charcoal) in a Firebox or Combustion Plant",
                    "leaves a Wood Ash in its ash slot. A full ash slot loses it.")
            .defineInRange("woodAshChance", 0.5, 0.0, 1.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Loam Farmland (tilled Loam) and Irrigated Loam Farmland. Neither can be trampled.").push("loamFarmland");
    }

    public static final ModConfigSpec.DoubleValue LOAM_DRYING_CHANCE = BUILDER
            .comment("Chance that a random tick away from water dries Loam Farmland by one step (vanilla farmland: 1.0,",
                    "so 0.5 stays moist twice as long). Irrigated Loam Farmland never dries.")
            .defineInRange("dryingChance", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue LOAM_GROWTH_MULTIPLIER = BUILDER
            .comment("How much faster crops grow on it while it has nutrients (1.5 = half again as fast; up to 2).",
                    "Each natural growth stage then has a (multiplier - 1) chance of an extra stage.")
            .defineInRange("growthMultiplier", 1.5, 1.0, 2.0);

    public static final ModConfigSpec.DoubleValue LOAM_NPK_GROWTH_MULTIPLIER = BUILDER
            .comment("How much faster crops grow on farmland enriched with NPK Fertilizer, while its nutrients last (2.0 = twice",
                    "as fast: every natural growth stage brings one more). Past 2, the rest is the chance of a third stage.")
            .defineInRange("npkGrowthMultiplier", 2.0, 1.0, 3.0);

    public static final ModConfigSpec.IntValue LOAM_NUTRIENTS_PER_STAGE = BUILDER
            .comment("Nutrients each growth stage of the crop on it uses.")
            .defineInRange("nutrientsPerStage", 1, 0, 15);

    public static final ModConfigSpec.IntValue LOAM_LEGUME_NUTRIENTS_PER_STAGE = BUILDER
            .comment("Nutrients each growth stage of a legume (#arcforge:legumes, e.g. Soybeans) adds to the Loam Farmland under it",
                    "instead of using any (capped at 15). Legumes grow at the nutrient rate even on empty farmland.")
            .defineInRange("legumeNutrientsPerStage", 1, 0, 15);

    public static final ModConfigSpec.DoubleValue LOAM_ROTATION_MULTIPLIER = BUILDER
            .comment("Crop rotation: how much faster a non-legume grows on Loam Farmland whose last harvested crop was a legume,",
                    "until it is harvested (1.25 = a quarter again as fast; on top of the nutrient bonus).")
            .defineInRange("rotationMultiplier", 1.25, 1.0, 2.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Hops: a perennial vine that climbs a Trellis on farmland and bears Hop Cones.").push("hops");
    }

    public static final ModConfigSpec.DoubleValue HOPS_GROWTH_CHANCE = BUILDER
            .comment("Chance that a random tick grows a vine one stage on moist farmland (half on dry).")
            .defineInRange("growthChance", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.IntValue HOP_CONES_MIN = BUILDER
            .comment("Fewest Hop Cones picked from a bearing trellis.")
            .defineInRange("conesMin", 1, 0, 64);

    public static final ModConfigSpec.IntValue HOP_CONES_MAX = BUILDER
            .comment("Most Hop Cones picked from a bearing trellis.")
            .defineInRange("conesMax", 3, 0, 64);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Rustic farming machines and tools: unpowered, built from treated wood, copper and iron.").push("rusticMachines");
    }

    public static final ModConfigSpec.IntValue MACHINE_TIMER_TICKS = BUILDER
            .comment("Ticks between runs of a Planter or Harvester with no redstone attached (100 = 5 seconds). With redstone "
                    + "attached, each rising pulse runs it once instead.")
            .defineInRange("timerTicks", 100, 1, 72_000);

    public static final ModConfigSpec.IntValue PLANTER_RADIUS = BUILDER
            .comment("Planter area: a square of (2 x radius + 1) blocks in front of it (1 = 3x3).")
            .defineInRange("planterRadius", 1, 0, 8);

    public static final ModConfigSpec.IntValue HARVESTER_RADIUS = BUILDER
            .comment("Harvester area: a square of (2 x radius + 1) blocks in front of it (1 = 3x3).")
            .defineInRange("harvesterRadius", 1, 0, 8);

    public static final ModConfigSpec.IntValue SPREADER_RADIUS = BUILDER
            .comment("Fertilizer Spreader area: a square of (2 x radius + 1) blocks centred on it (2 = 5x5), down to 3 blocks below.")
            .defineInRange("spreaderRadius", 2, 0, 8);

    public static final ModConfigSpec.IntValue SPREADER_INTERVAL = BUILDER
            .comment("Ticks between the Fertilizer Spreader's checks of the soil.")
            .defineInRange("spreaderInterval", 40, 1, 72_000);

    public static final ModConfigSpec.IntValue SPREADER_THRESHOLD = BUILDER
            .comment("The Fertilizer Spreader tops up Loam Farmland whose nutrients are below this (0 to 15).")
            .defineInRange("spreaderThreshold", 4, 1, 15);

    public static final ModConfigSpec.IntValue SPRINKLER_RADIUS = BUILDER
            .comment("Copper Sprinkler range: farmland within this many blocks across (a square), down to 3 blocks below.")
            .defineInRange("sprinklerRadius", 4, 0, 8);

    public static final ModConfigSpec.IntValue SPRINKLER_WATER_PER_TICK = BUILDER
            .comment("Water (mB) the Copper Sprinkler uses each tick while it runs.")
            .defineInRange("sprinklerWaterPerTick", 1, 1, 1_000);

    public static final ModConfigSpec.IntValue SPRINKLER_TANK_CAPACITY = BUILDER
            .comment("Copper Sprinkler water tank capacity (mB).")
            .defineInRange("sprinklerTankCapacity", 1_000, 100, 64_000);

    public static final ModConfigSpec.IntValue SPRINKLER_INTERVAL = BUILDER
            .comment("Ticks between the Copper Sprinkler's passes over its area (moistening and the growth bonus).")
            .defineInRange("sprinklerInterval", 20, 1, 1_200);

    public static final ModConfigSpec.DoubleValue SPRINKLER_GROWTH_CHANCE = BUILDER
            .comment("Chance, each pass, that a crop in range gets an extra growth tick (a random tick).")
            .defineInRange("sprinklerGrowthChance", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.IntValue SCARECROW_RADIUS = BUILDER
            .comment("Mobs can't trample farmland within this many blocks of a Scarecrow (a cube).")
            .defineInRange("scarecrowRadius", 9, 0, 32);

    public static final ModConfigSpec.IntValue SICKLE_RADIUS = BUILDER
            .comment("Sickle area: a square of (2 x radius + 1) crops around the one used (1 = 3x3).")
            .defineInRange("sickleRadius", 1, 0, 8);

    public static final ModConfigSpec.IntValue SCYTHE_RADIUS = BUILDER
            .comment("Scythe area: a square of (2 x radius + 1) crops around the one used (2 = 5x5).")
            .defineInRange("scytheRadius", 2, 0, 8);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Millstone: an unpowered quern. Recipes (arcforge:milling) set how many turns an item takes.").push("millstone");
    }

    public static final ModConfigSpec.IntValue MILLSTONE_HAND_COOLDOWN = BUILDER
            .comment("Ticks between turns by hand, so holding use turns it at a steady pace.")
            .defineInRange("handCooldown", 5, 0, 200);

    public static final ModConfigSpec.IntValue MILLSTONE_TURNS_PER_PULSE = BUILDER
            .comment("Turns each rising redstone pulse gives it.")
            .defineInRange("turnsPerPulse", 1, 1, 64);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Mill: the powered Millstone, with three lanes. Recipes (arcforge:milling) set the time per item.").push("mill");
    }

    public static final ModConfigSpec.IntValue MILL_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue MILL_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 200, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue MILL_ENERGY_PER_TICK = BUILDER
            .comment("FE/t for each lane that is milling, before upgrades.")
            .defineInRange("energyPerTick", 8, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue MILL_TIME_MULTIPLIER = BUILDER
            .comment("Each lane's time per item, as a share of the recipe's time (0.5 = twice as fast), before Speed upgrades.")
            .defineInRange("timeMultiplier", 0.5, 0.01, 100.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Oil Press: presses seeds into Seed Oil and Press Cake. Recipes (arcforge:oil_pressing) set the oil, cake and time.")
                .push("oilPress");
    }

    public static final ModConfigSpec.IntValue OIL_PRESS_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue OIL_PRESS_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 200, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue OIL_PRESS_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while pressing, before upgrades (unless a recipe sets its own energy).")
            .defineInRange("energyPerTick", 16, 1, 1_000_000);

    public static final ModConfigSpec.IntValue OIL_PRESS_OIL_TANK = BUILDER
            .comment("Seed Oil tank size in mB.")
            .defineInRange("oilTank", 8_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue OIL_PRESS_OUTPUT_RATE = BUILDER
            .comment("Most mB/t of Seed Oil it pushes out of its Output faces (shared across them).")
            .defineInRange("fluidOutputRate", 100, 1, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Seed Extractor: threshes crops into extra seeds. Recipes (arcforge:seed_extracting) set the seeds and time.")
                .push("seedExtractor");
    }

    public static final ModConfigSpec.IntValue SEED_EXTRACTOR_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 20_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue SEED_EXTRACTOR_MAX_INPUT = BUILDER
            .comment("Most FE/t it takes in.")
            .defineInRange("maxEnergyInput", 200, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue SEED_EXTRACTOR_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while threshing, before upgrades.")
            .defineInRange("energyPerTick", 10, 1, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Grain Dryer: dries Hop Cones, grain and sorghum with heat (HU), no FE. Recipes (arcforge:drying) set the time "
                + "and HU/t.").push("grainDryer");
    }

    public static final ModConfigSpec.IntValue GRAIN_DRYER_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 4_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue GRAIN_DRYER_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 250, 21, 10_000);

    public static final ModConfigSpec.IntValue GRAIN_DRYER_MIN_TEMPERATURE = BUILDER
            .comment("It only dries while its heat buffer is above this, in °C.")
            .defineInRange("minTemperature", 60, 21, 10_000);

    public static final ModConfigSpec.IntValue GRAIN_DRYER_TANK_CAPACITY = BUILDER
            .comment("Fluid tank capacity in mB, for what it dries from a fluid (Latex into Raw Rubber).")
            .defineInRange("tankCapacity", 4_000, 1_000, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Vulcanizer: cures Raw Rubber with Sulfur into Rubber with heat (HU), no FE. Recipes (arcforge:vulcanizing) set",
                "the inputs, time and HU/t.").push("vulcanizer");
    }

    public static final ModConfigSpec.IntValue VULCANIZER_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 8_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue VULCANIZER_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 400, 21, 10_000);

    public static final ModConfigSpec.IntValue VULCANIZER_MIN_TEMPERATURE = BUILDER
            .comment("It only works at this temperature or hotter, in °C.")
            .defineInRange("minTemperature", 140, 21, 10_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Resin Tap: hung on the side of a log with leaves above it (a living tree), it fills by itself. On a jungle",
                "log it drips Latex into its tank; on a spruce log it collects Pine Resin; on any other log, Pine Resin more",
                "slowly.").push("resinTap");
    }

    public static final ModConfigSpec.IntValue RESIN_TAP_INTERVAL = BUILDER
            .comment("Ticks between drips.")
            .defineInRange("interval", 200, 1, 72_000);

    public static final ModConfigSpec.IntValue RESIN_TAP_LATEX_PER_DRIP = BUILDER
            .comment("Latex (mB) a tap on a jungle log gains each drip.")
            .defineInRange("latexPerDrip", 25, 1, 64_000);

    public static final ModConfigSpec.DoubleValue RESIN_TAP_SPRUCE_CHANCE = BUILDER
            .comment("Chance each drip that a tap on a spruce log gains a Pine Resin.")
            .defineInRange("spruceResinChance", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue RESIN_TAP_OTHER_CHANCE = BUILDER
            .comment("Chance each drip that a tap on any other log gains a Pine Resin.")
            .defineInRange("otherResinChance", 0.15, 0.0, 1.0);

    public static final ModConfigSpec.IntValue RESIN_TAP_TANK_CAPACITY = BUILDER
            .comment("Latex tank capacity (mB).")
            .defineInRange("tankCapacity", 1_000, 100, 64_000);

    public static final ModConfigSpec.IntValue RESIN_TAP_MAX_RESIN = BUILDER
            .comment("Pine Resin it holds before it stops collecting.")
            .defineInRange("maxResin", 16, 1, 64);

    public static final ModConfigSpec.IntValue RESIN_TAP_LEAF_SEARCH = BUILDER
            .comment("How far up the trunk (in blocks, from the tapped log) it looks for the tree's leaves.")
            .defineInRange("leafSearchHeight", 12, 1, 64);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Air Separator: separates air into Nitrogen and Oxygen with FE, anywhere but the End. Recipes",
                "(arcforge:air_separating) set the amounts, time and FE/t.").push("airSeparator");
    }

    public static final ModConfigSpec.IntValue AIR_SEPARATOR_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 40_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue AIR_SEPARATOR_MAX_INPUT = BUILDER
            .comment("Most FE/t it accepts.")
            .defineInRange("maxInput", 400, 1, 1_000_000);

    public static final ModConfigSpec.IntValue AIR_SEPARATOR_ENERGY_PER_TICK = BUILDER
            .comment("FE/t for a recipe that doesn't set its own.")
            .defineInRange("energyPerTick", 80, 1, 1_000_000);

    public static final ModConfigSpec.IntValue AIR_SEPARATOR_GAS_CAPACITY = BUILDER
            .comment("Size of each gas tank (Nitrogen, Oxygen), in mB.")
            .defineInRange("gasCapacity", 8_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue AIR_SEPARATOR_OUTPUT_RATE = BUILDER
            .comment("Most mB/t of each gas it pushes out of its faces for that gas (shared across them).")
            .defineInRange("outputRate", 200, 1, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Haber Reactor: makes Ammonia from Hydrogen and Nitrogen with FE and heat, at minTemperature or hotter.",
                "Recipes (arcforge:synthesizing) set the gases, time, FE/t and HU/t.").push("haberReactor");
    }

    public static final ModConfigSpec.IntValue HABER_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 40_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue HABER_MAX_INPUT = BUILDER
            .comment("Most FE/t it accepts.")
            .defineInRange("maxInput", 400, 1, 1_000_000);

    public static final ModConfigSpec.IntValue HABER_ENERGY_PER_TICK = BUILDER
            .comment("FE/t for a recipe that doesn't set its own.")
            .defineInRange("energyPerTick", 60, 1, 1_000_000);

    public static final ModConfigSpec.IntValue HABER_TANK_CAPACITY = BUILDER
            .comment("Size of each gas tank (two inputs, one output), in mB.")
            .defineInRange("tankCapacity", 8_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue HABER_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 20_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue HABER_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 800, 21, 10_000);

    public static final ModConfigSpec.IntValue HABER_MIN_TEMPERATURE = BUILDER
            .comment("It only runs at this temperature or hotter, in °C.")
            .defineInRange("minTemperature", 450, 21, 10_000);

    public static final ModConfigSpec.IntValue HABER_OUTPUT_RATE = BUILDER
            .comment("Most mB/t of Ammonia it pushes out of its Gas Output faces (shared across them).")
            .defineInRange("outputRate", 200, 1, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Biogas Digester: a 3x3x3 multiblock that digests plant matter in water into Biogas and Digestate, while",
                "warm. Recipes (arcforge:digesting) set what each item gives and how long it takes.").push("biogasDigester");
    }

    public static final ModConfigSpec.IntValue DIGESTER_WATER_CAPACITY = BUILDER
            .comment("Water tank size, in mB.")
            .defineInRange("waterCapacity", 16_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue DIGESTER_GAS_CAPACITY = BUILDER
            .comment("Biogas tank size, in mB.")
            .defineInRange("gasCapacity", 32_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue DIGESTER_LANES = BUILDER
            .comment("Items it digests at once (each with its own recipe time).")
            .defineInRange("lanes", 4, 1, 9);

    public static final ModConfigSpec.IntValue DIGESTER_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 8_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue DIGESTER_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C.")
            .defineInRange("maxTemperature", 80, 21, 10_000);

    public static final ModConfigSpec.IntValue DIGESTER_MIN_TEMPERATURE = BUILDER
            .comment("It only digests at this temperature or hotter, in °C.")
            .defineInRange("minTemperature", 35, 21, 10_000);

    public static final ModConfigSpec.IntValue DIGESTER_HEAT_PER_TICK = BUILDER
            .comment("HU/t it uses while digesting (to stay warm), however many lanes are busy.")
            .defineInRange("heatPerTick", 2, 0, 1_000_000);

    public static final ModConfigSpec.IntValue DIGESTER_OUTPUT_RATE = BUILDER
            .comment("Most mB/t of Biogas it pushes out of its Gas Output ports (shared across them).")
            .defineInRange("outputRate", 400, 1, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Automated farms (Glass Cloche, Grow Chamber, Hydroponic Cell): a seed grows inside and is harvested over",
                "and over. Recipes (arcforge:cloche) set each crop's time at the Glass Cloche's speed and its harvest; the",
                "arcforge:cloche_soils data map sets how fast crops grow in each soil. Each farm's speed multiplies that.").push("cloche");
    }

    public static final ModConfigSpec.IntValue CLOCHE_FALLBACK_TIME = BUILDER
            .comment("Ticks a crop no recipe names (any vanilla-style crop from another mod) takes at speed 1.")
            .defineInRange("fallbackTime", 2_400, 20, 1_000_000);

    public static final ModConfigSpec.DoubleValue CLOCHE_FERTILIZER_BONUS = BUILDER
            .comment("Growth multiplier while fertilized (each harvest uses one of a fertilizer's nutrient points).")
            .defineInRange("fertilizerBonus", 1.5, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue CLOCHE_ENRICHED_BONUS = BUILDER
            .comment("Growth multiplier while fertilized with an enriching fertilizer (NPK Fertilizer).")
            .defineInRange("enrichedBonus", 2.0, 1.0, 100.0);

    static {
        BUILDER.comment("Glass Cloche: unpowered; needs water and a soil. Pushes its harvest down.").push("glassCloche");
    }

    public static final ModConfigSpec.DoubleValue GLASS_CLOCHE_SPEED = BUILDER
            .comment("Growth speed (recipe times are for speed 1).")
            .defineInRange("speed", 1.0, 0.01, 100.0);

    public static final ModConfigSpec.IntValue GLASS_CLOCHE_WATER_CAPACITY = BUILDER
            .comment("Water tank size, in mB.")
            .defineInRange("waterCapacity", 4_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue GLASS_CLOCHE_WATER_PER_HARVEST = BUILDER
            .comment("Water each harvest uses, in mB (taken when it starts growing).")
            .defineInRange("waterPerHarvest", 100, 0, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Grow Chamber: FE and water, with a grow lamp; takes Speed and Energy upgrades.").push("growChamber");
    }

    public static final ModConfigSpec.DoubleValue GROW_CHAMBER_SPEED = BUILDER
            .comment("Growth speed, before Speed upgrades (recipe times are for speed 1).")
            .defineInRange("speed", 3.0, 0.01, 100.0);

    public static final ModConfigSpec.IntValue GROW_CHAMBER_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 40_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue GROW_CHAMBER_MAX_INPUT = BUILDER
            .comment("Most FE/t it accepts.")
            .defineInRange("maxInput", 400, 1, 1_000_000);

    public static final ModConfigSpec.IntValue GROW_CHAMBER_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while growing, before upgrades.")
            .defineInRange("energyPerTick", 24, 0, 1_000_000);

    public static final ModConfigSpec.IntValue GROW_CHAMBER_WATER_CAPACITY = BUILDER
            .comment("Water tank size, in mB.")
            .defineInRange("waterCapacity", 8_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue GROW_CHAMBER_WATER_PER_HARVEST = BUILDER
            .comment("Water each harvest uses, in mB (taken when it starts growing).")
            .defineInRange("waterPerHarvest", 100, 0, 1_000_000);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Hydroponic Cell: FE and Nutrient Solution, no soil; optional Carbon Dioxide speeds it up. Also grows",
                "saplings, flowers, nether wart and Hops. Takes Speed and Energy upgrades.").push("hydroponicCell");
    }

    public static final ModConfigSpec.DoubleValue HYDROPONIC_CELL_SPEED = BUILDER
            .comment("Growth speed, before Speed upgrades and Carbon Dioxide (recipe times are for speed 1).")
            .defineInRange("speed", 6.0, 0.01, 100.0);

    public static final ModConfigSpec.IntValue HYDROPONIC_CELL_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 80_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue HYDROPONIC_CELL_MAX_INPUT = BUILDER
            .comment("Most FE/t it accepts.")
            .defineInRange("maxInput", 800, 1, 1_000_000);

    public static final ModConfigSpec.IntValue HYDROPONIC_CELL_ENERGY_PER_TICK = BUILDER
            .comment("FE/t while growing, before upgrades.")
            .defineInRange("energyPerTick", 48, 0, 1_000_000);

    public static final ModConfigSpec.IntValue HYDROPONIC_CELL_NUTRIENT_CAPACITY = BUILDER
            .comment("Nutrient Solution tank size, in mB.")
            .defineInRange("nutrientCapacity", 8_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue HYDROPONIC_CELL_NUTRIENT_PER_HARVEST = BUILDER
            .comment("Nutrient Solution each harvest uses, in mB (taken when it starts growing).")
            .defineInRange("nutrientPerHarvest", 50, 0, 1_000_000);

    public static final ModConfigSpec.IntValue HYDROPONIC_CELL_CO2_CAPACITY = BUILDER
            .comment("Carbon Dioxide tank size, in mB.")
            .defineInRange("co2Capacity", 4_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue HYDROPONIC_CELL_CO2_PER_TICK = BUILDER
            .comment("Carbon Dioxide it uses per tick while growing, in mB, for the bonus.")
            .defineInRange("co2PerTick", 1, 1, 1_000);

    public static final ModConfigSpec.DoubleValue HYDROPONIC_CELL_CO2_BONUS = BUILDER
            .comment("Growth multiplier while it has Carbon Dioxide.")
            .defineInRange("co2Bonus", 1.25, 1.0, 100.0);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("Greenhouse Array: a Greenhouse Frame and Pressure Glass building (5x5 to 11x11, 4 to 8 tall) whose Planting",
                "Beds grow the arcforge:cloche recipes (and any vanilla-style crop) by themselves. Each bed grows at baseSpeed x the",
                "soil's growth x the temperature factor x the bonuses of the systems fed through its ports, while it has light.")
                .push("greenhouse");
    }

    public static final ModConfigSpec.DoubleValue GREENHOUSE_BASE_SPEED = BUILDER
            .comment("Each bed's growth speed on water alone, in ideal warmth and light (recipe times are for speed 1).")
            .defineInRange("baseSpeed", 1.0, 0.01, 100.0);

    public static final ModConfigSpec.IntValue GREENHOUSE_WATER_CAPACITY = BUILDER
            .comment("Water tank size, in mB.")
            .defineInRange("waterCapacity", 16_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue GREENHOUSE_WATER_PER_HARVEST = BUILDER
            .comment("Water each bed's harvest takes when it starts growing, in mB. Without it, nothing grows.")
            .defineInRange("waterPerHarvest", 50, 0, 1_000_000);

    public static final ModConfigSpec.IntValue GREENHOUSE_NUTRIENT_CAPACITY = BUILDER
            .comment("Nutrient Solution tank size, in mB.")
            .defineInRange("nutrientCapacity", 8_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue GREENHOUSE_NUTRIENT_PER_HARVEST = BUILDER
            .comment("Nutrient Solution a harvest takes for the nutrient bonus, in mB (used before fertilizer).")
            .defineInRange("nutrientPerHarvest", 25, 0, 1_000_000);

    public static final ModConfigSpec.DoubleValue GREENHOUSE_FERTILIZER_BONUS = BUILDER
            .comment("Growth multiplier of a harvest fed with a fertilizer point (fertilizer or bone meal).")
            .defineInRange("fertilizerBonus", 1.5, 1.0, 100.0);

    public static final ModConfigSpec.DoubleValue GREENHOUSE_NUTRIENT_BONUS = BUILDER
            .comment("Growth multiplier of a harvest fed with Nutrient Solution or an enriching fertilizer (NPK Fertilizer).")
            .defineInRange("nutrientBonus", 2.0, 1.0, 100.0);

    public static final ModConfigSpec.IntValue GREENHOUSE_CO2_CAPACITY = BUILDER
            .comment("Carbon Dioxide tank size, in mB.")
            .defineInRange("co2Capacity", 8_000, 100, 1_000_000);

    public static final ModConfigSpec.IntValue GREENHOUSE_CO2_PER_HARVEST = BUILDER
            .comment("Carbon Dioxide a harvest takes for the CO2 bonus, in mB.")
            .defineInRange("co2PerHarvest", 20, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue GREENHOUSE_CO2_BONUS = BUILDER
            .comment("Growth multiplier of a harvest fed with Carbon Dioxide.")
            .defineInRange("co2Bonus", 1.3, 1.0, 100.0);

    public static final ModConfigSpec.IntValue GREENHOUSE_IDEAL_MIN = BUILDER
            .comment("Crops grow at full speed from this temperature, in °C...")
            .defineInRange("idealMin", 18, -50, 100);

    public static final ModConfigSpec.IntValue GREENHOUSE_IDEAL_MAX = BUILDER
            .comment("...up to this one.")
            .defineInRange("idealMax", 30, -50, 100);

    public static final ModConfigSpec.DoubleValue GREENHOUSE_TEMPERATURE_FALLOFF = BUILDER
            .comment("Growth lost per °C outside the ideal range (0.05: growth stops 20°C outside it).")
            .defineInRange("temperatureFalloff", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue GREENHOUSE_CELSIUS_PER_BIOME_TEMPERATURE = BUILDER
            .comment("The outside temperature is the biome's temperature times this, in °C (plains 0.8: 20°C, snowy 0: 0°C).")
            .defineInRange("celsiusPerBiomeTemperature", 25.0, 0.0, 100.0);

    public static final ModConfigSpec.IntValue GREENHOUSE_NO_SKY_AMBIENT = BUILDER
            .comment("The outside temperature in dimensions without a sky (the Nether, the End), in °C: cold and dark.")
            .defineInRange("noSkyAmbient", 5, -50, 100);

    public static final ModConfigSpec.IntValue GREENHOUSE_SOLAR_GAIN = BUILDER
            .comment("How much warmer than outside the glass keeps it by day under the sky, in °C.")
            .defineInRange("solarGain", 6, 0, 100);

    public static final ModConfigSpec.DoubleValue GREENHOUSE_HEAT_EXCHANGE = BUILDER
            .comment("How fast the air inside drifts toward the outside temperature, per tick (a fraction of the difference).")
            .defineInRange("heatExchange", 0.002, 0.0, 1.0);

    public static final ModConfigSpec.IntValue GREENHOUSE_TARGET_TEMPERATURE = BUILDER
            .comment("With heat through a heat port, it heats up to this temperature, in °C.")
            .defineInRange("targetTemperature", 24, -50, 100);

    public static final ModConfigSpec.IntValue GREENHOUSE_HEAT_BASE = BUILDER
            .comment("HU/t it uses while heating, plus heatPerSurface for each block of its walls and roof.")
            .defineInRange("heatBase", 4, 0, 1_000_000);

    public static final ModConfigSpec.DoubleValue GREENHOUSE_HEAT_PER_SURFACE = BUILDER
            .comment("HU/t per block of walls and roof while heating.")
            .defineInRange("heatPerSurface", 0.05, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue GREENHOUSE_HEATING_RATE = BUILDER
            .comment("°C per tick it warms by while heating (and at most up to targetTemperature).")
            .defineInRange("heatingRate", 0.05, 0.0, 100.0);

    public static final ModConfigSpec.IntValue GREENHOUSE_HEAT_CAPACITY = BUILDER
            .comment("Heat buffer size, in HU.")
            .defineInRange("heatCapacity", 20_000, 100, 1_000_000_000);

    public static final ModConfigSpec.IntValue GREENHOUSE_HEAT_MAX_TEMPERATURE = BUILDER
            .comment("Temperature of a full heat buffer, in °C (heat sources must be hotter than the buffer to feed it).")
            .defineInRange("heatMaxTemperature", 200, 21, 10_000);

    public static final ModConfigSpec.IntValue GREENHOUSE_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size, for the Grow Lamps.")
            .defineInRange("energyCapacity", 100_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue GREENHOUSE_MAX_INPUT = BUILDER
            .comment("Most FE/t it accepts.")
            .defineInRange("maxInput", 2_000, 1, 1_000_000);

    public static final ModConfigSpec.IntValue GREENHOUSE_LAMP_ENERGY = BUILDER
            .comment("FE/t each Grow Lamp draws while it's lit (at night, or without sky, while beds grow).")
            .defineInRange("lampEnergyPerTick", 16, 0, 1_000_000);

    public static final ModConfigSpec.IntValue GREENHOUSE_LAMP_RANGE = BUILDER
            .comment("How far a Grow Lamp lights beds, in blocks across (3: a 7x7 patch under it).")
            .defineInRange("lampRange", 3, 0, 16);

    public static final ModConfigSpec.IntValue GREENHOUSE_MIN_SKY_LIGHT = BUILDER
            .comment("Sky light a bed needs to grow by day without a lamp (glass lets all of it through).")
            .defineInRange("minSkyLight", 13, 0, 15);

    public static final ModConfigSpec.IntValue GREENHOUSE_CHECK_INTERVAL = BUILDER
            .comment("Ticks between full checks of the structure (placing or breaking its blocks rechecks at once).")
            .defineInRange("checkInterval", 40, 1, 1_200);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
