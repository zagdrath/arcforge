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
        BUILDER.comment("Geothermal Plant: converts heat (HU) into Forge Energy (FE).").push("geothermalPlant");
    }

    public static final ModConfigSpec.IntValue GEOTHERMAL_ENERGY_CAPACITY = BUILDER
            .comment("Internal FE buffer size.")
            .defineInRange("energyCapacity", 100_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_MAX_OUTPUT = BUILDER
            .comment("Maximum FE/t pushed into neighbouring energy receivers (shared across all sides).")
            .defineInRange("maxEnergyOutput", 1_000, 1, 1_000_000_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_TANK_CAPACITY = BUILDER
            .comment("Internal lava tank capacity in mB.")
            .defineInRange("lavaTankCapacity", 8_000, 1_000, 1_000_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_FE_PER_HEAT = BUILDER
            .comment("FE/t produced per heat unit (HU). This is the single heat -> FE conversion factor.")
            .defineInRange("fePerHeat", 2, 1, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_HEAT_RATE = BUILDER
            .comment("How many HU the plant heats up or cools down per tick while approaching its target heat.")
            .defineInRange("heatChangePerTick", 1, 1, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_SOURCE_HEAT = BUILDER
            .comment("Passive HU from each adjacent lava source block (all 6 sides count; blocks are never consumed).")
            .defineInRange("lavaSourceHeat", 5, 0, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_HEAT = BUILDER
            .comment("HU while burning lava from the internal tank.")
            .defineInRange("lavaCombustionHeat", 40, 0, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_PER_BURN = BUILDER
            .comment("mB of lava consumed per lava burn cycle.")
            .defineInRange("lavaPerBurn", 50, 1, 1_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_LAVA_BURN_TICKS = BUILDER
            .comment("Ticks one lava burn cycle lasts.")
            .defineInRange("lavaBurnTicks", 100, 1, 32_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_COAL_HEAT = BUILDER
            .comment("HU while burning coal.")
            .defineInRange("coalHeat", 20, 0, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_CHARCOAL_HEAT = BUILDER
            .comment("HU while burning charcoal.")
            .defineInRange("charcoalHeat", 15, 0, 10_000);

    public static final ModConfigSpec.IntValue GEOTHERMAL_SOLID_FUEL_BURN_TICKS = BUILDER
            .comment("Ticks one piece of coal or charcoal burns for (vanilla furnace value is 1600).")
            .defineInRange("solidFuelBurnTicks", 1_600, 1, 32_000);

    public static final ModConfigSpec.DoubleValue GEOTHERMAL_COAL_BLOCK_MULTIPLIER = BUILDER
            .comment("A block of coal burns at coal heat for this many times as long as one coal (9 coal, plus a bonus).")
            .defineInRange("coalBlockMultiplier", 9.5, 1.0, 100.0);

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
