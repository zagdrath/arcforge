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

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
