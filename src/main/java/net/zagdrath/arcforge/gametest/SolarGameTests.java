/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.WeatherData;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.block.multiblock.SolarThermalArrayControllerBlock;
import net.zagdrath.arcforge.blockentity.machine.SteamBoilerBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SolarThermalArrayBlockEntity;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.SolarModel;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.steam.SteamGrade;

// The Solar Thermal Array: output by the sun, the weather, the tracking axis and the collectors' sky, and
// heating a Steam Boiler through its heat port. Each test that sets the time or the weather (which are the
// whole level's) runs in its own batch (see ArcforgeGameTests), in the open, on plains (biome x1.0).
public final class SolarGameTests {
    private SolarGameTests() {}

    // Past the controller's 40-tick sky and biome scan.
    private static final int SETTLE = 45;
    private static final long NOON = 6_000, NIGHT = 18_000;

    // A tower with its bottom-layer minimum corner at min and the controller facing out along facing.
    static SolarThermalArrayBlockEntity buildTower(GameTestHelper helper, BlockPos min, Direction facing) {
        BlockPos controller = min.offset(facing == Direction.EAST ? 1 : 0, 0, facing == Direction.SOUTH ? 1 : 0);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos pos = min.offset(x, y, z);
                    if (y == 3) {
                        helper.setBlock(pos, ModBlocks.SOLAR_COLLECTOR.get());
                    } else if (!pos.equals(controller)) {
                        helper.setBlock(pos, ModBlocks.SOLAR_THERMAL_ARRAY_CASING.get());
                    }
                }
            }
        }
        helper.setBlock(controller, ModBlocks.SOLAR_THERMAL_ARRAY_CONTROLLER.get().defaultBlockState().setValue(SolarThermalArrayControllerBlock.FACING, facing));
        return helper.getBlockEntity(controller, SolarThermalArrayBlockEntity.class);
    }

    // The test world is a dry, hot biome: the towers stand on plains (x1.0, and it rains there).
    static void plains(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FillBiomeCommand.fill(level, helper.absolutePos(new BlockPos(-2, -1, -2)), helper.absolutePos(new BlockPos(10, 10, 6)),
                level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
    }

    static void time(GameTestHelper helper, long dayTime) {
        ServerLevel level = helper.getLevel();
        level.dimensionType().defaultClock().ifPresent(clock -> level.getServer().clockManager().setTotalTicks(clock, dayTime));
    }

    static void weather(GameTestHelper helper, boolean rain, boolean thunder) {
        ServerLevel level = helper.getLevel();
        WeatherData data = level.getWeatherData();
        data.setClearWeatherTime(rain ? 0 : 1_000_000);
        data.setRaining(rain);
        data.setRainTime(rain ? 1_000_000 : 0);
        data.setThundering(thunder);
        data.setThunderTime(thunder ? 1_000_000 : 0);
        level.setRainLevel(rain ? 1.0F : 0.0F);
        level.setThunderLevel(thunder ? 1.0F : 0.0F);
    }

    // Clear skies at the time given, a tower facing north, then a check once it has settled.
    private static void atTime(GameTestHelper helper, long dayTime, boolean rain, boolean thunder, java.util.function.Consumer<SolarThermalArrayBlockEntity> check) {
        plains(helper);
        time(helper, dayTime);
        weather(helper, rain, thunder);
        SolarThermalArrayBlockEntity array = buildTower(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        helper.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    helper.assertTrue(array.isFormed(), "Tower did not form: " + array.getProblem());
                    check.accept(array);
                })
                .thenExecute(() -> weather(helper, false, false))
                .thenSucceed();
    }

    // 1. Nothing at night, and the trough is stowed.
    static void zeroAtNight(GameTestHelper helper) {
        atTime(helper, NIGHT, false, false, array -> {
            helper.assertTrue(array.getHeatPerTick() == 0, "Makes " + array.getHeatPerTick() + " HU/t at night");
            helper.assertTrue(array.isStowed(), "Trough not stowed at night");
        });
    }

    // 2. Clear noon: the full 600 HU/t at 550°C.
    static void peakAtNoon(GameTestHelper helper) {
        atTime(helper, NOON, false, false, array -> {
            helper.assertTrue(array.getHeatPerTick() == 600, "Noon gives " + array.getHeatPerTick() + " HU/t");
            helper.assertTrue(array.getReceiverTemperature() == 550, "Noon receiver at " + array.getReceiverTemperature() + "°C");
            helper.assertTrue(!array.isStowed(), "Trough stowed at noon");
        });
    }

    // 3. Rain at noon: 30%, too cool for High-Pressure Steam.
    static void rainReducesOutput(GameTestHelper helper) {
        atTime(helper, NOON, true, false, array -> {
            helper.assertTrue(array.getHeatPerTick() == 180, "Rain gives " + array.getHeatPerTick() + " HU/t");
            helper.assertTrue(array.getReceiverTemperature() < 500, "Rain receiver at " + array.getReceiverTemperature() + "°C");
        });
    }

    // 4. A thunderstorm stows the trough; the receiver still takes 10%.
    static void thunderStowsPanel(GameTestHelper helper) {
        atTime(helper, NOON, true, true, array -> {
            helper.assertTrue(array.isStowed(), "Trough not stowed in a thunderstorm");
            helper.assertTrue(array.getHeatPerTick() == 60, "Thunderstorm gives " + array.getHeatPerTick() + " HU/t");
        });
    }

    // 5. A block over one collector cuts a quarter.
    static void shadingOneCollector(GameTestHelper helper) {
        plains(helper);
        time(helper, NOON);
        weather(helper, false, false);
        SolarThermalArrayBlockEntity array = buildTower(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        helper.setBlock(new BlockPos(1, 6, 1), Blocks.STONE);
        helper.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    helper.assertTrue(array.getSkyCount() == 3, array.getSkyCount() + " collectors see the sky");
                    helper.assertTrue(array.getHeatPerTick() == 450, "One shaded collector gives " + array.getHeatPerTick() + " HU/t");
                })
                .thenSucceed();
    }

    // 6. North-south tracking beats east-west: 600 against 420 HU/t, and only north-south reaches 500°C.
    static void northSouthBeatsEastWest(GameTestHelper helper) {
        plains(helper);
        time(helper, NOON);
        weather(helper, false, false);
        SolarThermalArrayBlockEntity northSouth = buildTower(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        SolarThermalArrayBlockEntity eastWest = buildTower(helper, new BlockPos(5, 1, 1), Direction.EAST);
        helper.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    helper.assertTrue(northSouth.getHeatPerTick() == 600, "North-south gives " + northSouth.getHeatPerTick() + " HU/t");
                    helper.assertTrue(eastWest.getHeatPerTick() == 420, "East-west gives " + eastWest.getHeatPerTick() + " HU/t");
                    helper.assertTrue(northSouth.getReceiverTemperature() >= 500, "North-south only reaches " + northSouth.getReceiverTemperature() + "°C");
                    helper.assertTrue(eastWest.getReceiverTemperature() < 500, "East-west reaches " + eastWest.getReceiverTemperature() + "°C");
                })
                .thenSucceed();
    }

    // 7. Through its heat port the tower heats a Steam Boiler to High-Pressure Steam at clear noon; in the
    //    rain the receiver cools, heat stops flowing and the boiler drops back to plain Steam.
    static void boilerGrades(GameTestHelper helper) {
        plains(helper);
        time(helper, NOON);
        weather(helper, false, false);
        // Controller facing south at (1,1,2): its default heat port is the block behind it, (1,1,1), whose
        // north face touches the boiler; the boiler faces north, so its back (heat) face is against it.
        SolarThermalArrayBlockEntity array = buildTower(helper, new BlockPos(1, 1, 1), Direction.SOUTH);
        BlockPos boilerPos = new BlockPos(1, 1, 0);
        helper.setBlock(boilerPos, ModBlocks.STEAM_BOILER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        SteamBoilerBlockEntity boiler = helper.getBlockEntity(boilerPos, SteamBoilerBlockEntity.class);
        try (Transaction tx = Transaction.openRoot()) {
            boiler.getWater().insert(0, FluidResource.of(Fluids.WATER), boiler.getWater().getCapacity(), tx);
            tx.commit();
        }
        HeatBuffer heat = boiler.getHeat();
        heat.add(heat.storedAt(480) - heat.getStored());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(MultiblockPorts.get(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH) == SideMode.HEAT,
                        "No heat port behind the controller"))
                .thenWaitUntil(() -> helper.assertTrue(boiler.getCore().currentGrade() == SteamGrade.HIGH_PRESSURE,
                        "Boiler at " + heat.getTemperature() + "°C, not High-Pressure"))
                .thenExecute(() -> weather(helper, true, false))
                .thenWaitUntil(() -> {
                    drainSteam(boiler);
                    helper.assertTrue(boiler.getCore().currentGrade() == SteamGrade.STEAM,
                            "Boiler still at " + heat.getTemperature() + "°C in the rain (receiver " + array.getReceiverTemperature() + "°C)");
                })
                .thenExecute(() -> weather(helper, false, false))
                .thenSucceed();
    }

    private static void drainSteam(SteamBoilerBlockEntity boiler) {
        if (boiler.getSteam().getAmount() > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                boiler.getSteam().extract(0, boiler.getSteam().getResource(0), boiler.getSteam().getAmount(), tx);
                tx.commit();
            }
        }
    }

    // 8. No sky, no heat: in a dimension without one (the Nether: tested on the model), and with every
    //    collector covered.
    static void noSky(GameTestHelper helper) {
        SolarModel.Result nether = SolarModel.compute(new SolarModel.Conditions(NOON, SolarModel.Weather.CLEAR, 1.0, true, 4, false),
                SolarThermalArrayBlockEntity.settings());
        helper.assertTrue(nether.heatPerTick() == 0 && nether.temperature() == 20, "No sky gives " + nether.heatPerTick() + " HU/t at " + nether.temperature() + "°C");
        plains(helper);
        time(helper, NOON);
        weather(helper, false, false);
        SolarThermalArrayBlockEntity array = buildTower(helper, new BlockPos(1, 1, 1), Direction.NORTH);
        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 6, z), Blocks.STONE);
            }
        }
        helper.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    helper.assertTrue(array.getSkyCount() == 0, array.getSkyCount() + " covered collectors see the sky");
                    helper.assertTrue(array.getHeatPerTick() == 0, "Covered tower gives " + array.getHeatPerTick() + " HU/t");
                })
                .thenSucceed();
    }

    // 9. The model's worked values (plains, all four collectors in sun).
    static void modelWorkedValues(GameTestHelper helper) {
        SolarModel.Settings settings = new SolarModel.Settings(200, 550, 0.3, 0.1, 1.0, 0.7);
        check(helper, settings, NOON, SolarModel.Weather.CLEAR, 1.0, true, 4, 200, 550, "clear noon");
        check(helper, settings, 3_000, SolarModel.Weather.CLEAR, 1.0, true, 4, 141, 395, "9:00");
        check(helper, settings, NOON, SolarModel.Weather.RAIN, 1.0, true, 4, 60, 179, "rain at noon");
        check(helper, settings, NOON, SolarModel.Weather.THUNDER, 1.0, true, 4, 20, 73, "thunder");
        check(helper, settings, NOON, SolarModel.Weather.CLEAR, 1.0, false, 4, 140, 391, "east-west at noon");
        check(helper, settings, NOON, SolarModel.Weather.CLEAR, 1.0, true, 3, 150, 550, "one collector shaded");
        check(helper, settings, NOON, SolarModel.Weather.CLEAR, 1.2, true, 4, 240, 550, "desert");
        check(helper, settings, NIGHT, SolarModel.Weather.CLEAR, 1.0, true, 4, 0, 20, "night");
        helper.assertTrue(SolarModel.compute(new SolarModel.Conditions(NOON, SolarModel.Weather.THUNDER, 1.0, true, 4, true), settings).stowed(),
                "Not stowed in thunder");
        // High-Pressure Steam (500°C) only while the sun is at 0.906 or higher: about 4,320 to 7,680.
        check(helper, settings, 4_200, SolarModel.Weather.CLEAR, 1.0, true, 4, -1, -499, "early morning under 500");
        check(helper, settings, 4_400, SolarModel.Weather.CLEAR, 1.0, true, 4, -1, 500, "late morning over 500");
        helper.succeed();
    }

    // heat/temperature -1 skips that check; a negative temperature means "below" its absolute value, a
    // positive one of 500 "at least".
    private static void check(GameTestHelper helper, SolarModel.Settings settings, long dayTime, SolarModel.Weather weather, double biome,
            boolean northSouth, int sky, int heat, int temperature, String what) {
        SolarModel.Result result = SolarModel.compute(new SolarModel.Conditions(dayTime, weather, biome, northSouth, sky, true), settings);
        if (heat >= 0) {
            helper.assertTrue(result.heatPerTick() == heat, what + ": " + result.heatPerTick() + " HU/t, expected " + heat);
        }
        if (temperature == 500) {
            helper.assertTrue(result.temperature() >= 500, what + ": " + result.temperature() + "°C");
        } else if (temperature < 0) {
            helper.assertTrue(result.temperature() <= -temperature, what + ": " + result.temperature() + "°C");
        } else {
            helper.assertTrue(result.temperature() == temperature, what + ": " + result.temperature() + "°C, expected " + temperature);
        }
    }
}
