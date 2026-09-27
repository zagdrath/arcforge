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
