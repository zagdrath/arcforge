/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.multiblock.CondenserArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.conduit.network.FluidConduitNetwork;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.ClimateHelper;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.steam.BoilerPressure;
import net.zagdrath.arcforge.steam.Lubricant;
import net.zagdrath.arcforge.steam.SteamGrade;

// The steam cycle: the Superheater Array, the Steam Turbine Array's exhaust, the Condenser Array, and all of
// them together in a closed loop.
public final class SteamCycleGameTests {
    private static final BlockPos MIN = new BlockPos(0, 1, 0);

    private SteamCycleGameTests() {}

    private static SuperheaterArrayBlockEntity superheater(GameTestHelper helper, BlockPos min) {
        return MultiblockTestHelpers.centre(helper, min, SuperheaterArrayBlockEntity.class);
    }

    private static CondenserArrayBlockEntity condenser(GameTestHelper helper, BlockPos min) {
        return MultiblockTestHelpers.centre(helper, min, CondenserArrayBlockEntity.class);
    }

    private static SteamGrade gradeOut(SuperheaterArrayBlockEntity superheater) {
        return SteamGrade.of(superheater.getSteamOut().getResource(0));
    }

    private static void emptyOut(SuperheaterArrayBlockEntity superheater) {
        superheater.getSteamOut().set(0, FluidResource.EMPTY, 0);
    }

    // At 450°C on High-Pressure, Steam passes through unchanged and no heat is used; at 600°C it comes out
    // High-Pressure.
    static void superheaterGatesOnTemperature(GameTestHelper helper) {
        MultiblockTestHelpers.buildCube(helper, MIN, ModBlocks.SUPERHEATER_ARRAY_CASING.get());
        int[] heatBefore = new int[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SuperheaterArrayBlockEntity array = superheater(helper, MIN);
                    helper.assertTrue(array.isFormed(), "Superheater did not form");
                    array.setPressure(BoilerPressure.HIGH_PRESSURE);
                    FiberGameTests.heatTo(array.getHeat(), 450);
                    heatBefore[0] = array.getHeat().getStored();
                    SteamGameTests.fill(array.getSteamIn(), SteamGrade.STEAM.resource(), 1_000);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    SuperheaterArrayBlockEntity array = superheater(helper, MIN);
                    helper.assertTrue(gradeOut(array) == SteamGrade.STEAM && array.getSteamOut().getAmount() == 1_000,
                            "At 450°C it gave " + array.getSteamOut().getAmount() + " mB of " + gradeOut(array));
                    helper.assertTrue(array.getHeat().getStored() == heatBefore[0], "Passing through used " + (heatBefore[0] - array.getHeat().getStored()) + " HU");
                    emptyOut(array);
                    FiberGameTests.heatTo(array.getHeat(), 600);
                    SteamGameTests.fill(array.getSteamIn(), SteamGrade.STEAM.resource(), 1_000);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(gradeOut(superheater(helper, MIN)) == SteamGrade.HIGH_PRESSURE,
                        "At 600°C it gave " + gradeOut(superheater(helper, MIN))))
                .thenSucceed();
    }

    // At 1,000°C on Auto, 400 mB Steam becomes 400 mB Superheated for 6 HU/mB (2,400 HU), at most 2,000 HU a
    // tick; 400 mB High-Pressure becomes Superheated for 3 HU/mB (1,200 HU).
    static void superheaterHuCost(GameTestHelper helper) {
        BlockPos steamMin = MIN, pressureMin = new BlockPos(4, 1, 0);
        MultiblockTestHelpers.buildCube(helper, steamMin, ModBlocks.SUPERHEATER_ARRAY_CASING.get());
        MultiblockTestHelpers.buildCube(helper, pressureMin, ModBlocks.SUPERHEATER_ARRAY_CASING.get());
        int[] before = new int[2];
        boolean[] started = { false };
        helper.onEachTick(() -> {
            if (started[0]) {
                helper.assertTrue(superheater(helper, steamMin).getHeatUsed() <= ArcforgeConfig.SUPERHEATER_MAX_HEAT_PER_CUBE.getAsInt(),
                        "Used " + superheater(helper, steamMin).getHeatUsed() + " HU in a tick");
            }
        });
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockPos[] mins = { steamMin, pressureMin };
                    SteamGrade[] grades = { SteamGrade.STEAM, SteamGrade.HIGH_PRESSURE };
                    for (int i = 0; i < 2; i++) {
                        SuperheaterArrayBlockEntity array = superheater(helper, mins[i]);
                        helper.assertTrue(array.isFormed(), "Superheater " + i + " did not form");
                        FiberGameTests.heatTo(array.getHeat(), 1_000);
                        before[i] = array.getHeat().getStored();
                        SteamGameTests.fill(array.getSteamIn(), grades[i].resource(), 400);
                    }
                    started[0] = true;
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    int[] expected = { 2_400, 1_200 };
                    BlockPos[] mins = { steamMin, pressureMin };
                    for (int i = 0; i < 2; i++) {
                        SuperheaterArrayBlockEntity array = superheater(helper, mins[i]);
                        helper.assertTrue(gradeOut(array) == SteamGrade.SUPERHEATED && array.getSteamOut().getAmount() == 400,
                                "Superheater " + i + " gave " + array.getSteamOut().getAmount() + " mB of " + gradeOut(array));
                        int used = before[i] - array.getHeat().getStored();
                        helper.assertTrue(used == expected[i], "Superheater " + i + " used " + used + " HU, expected " + expected[i]);
                    }
                })
                .thenSucceed();
    }

    // Just above 900°C with plenty of Steam, it makes Superheated Steam only from the heat above 900°C: it never
    // cools itself below that.
    static void superheaterNeverBelowTarget(GameTestHelper helper) {
        MultiblockTestHelpers.buildCube(helper, MIN, ModBlocks.SUPERHEATER_ARRAY_CASING.get());
        boolean[] started = { false };
        helper.onEachTick(() -> {
            if (started[0]) {
                int temperature = superheater(helper, MIN).getHeat().getTemperature();
                helper.assertTrue(temperature >= 900, "Cooled to " + temperature + "°C");
            }
        });
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SuperheaterArrayBlockEntity array = superheater(helper, MIN);
                    helper.assertTrue(array.isFormed(), "Superheater did not form");
                    FiberGameTests.heatTo(array.getHeat(), 905);
                    SteamGameTests.fill(array.getSteamIn(), SteamGrade.STEAM.resource(), 16_000);
                    started[0] = true;
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    SuperheaterArrayBlockEntity array = superheater(helper, MIN);
                    helper.assertTrue(gradeOut(array) == SteamGrade.SUPERHEATED && array.getSteamOut().getAmount() > 0, "Made no Superheated Steam");
                    helper.assertTrue(array.getSteamIn().getAmount() > 0 && array.getStatus() == MachineStatus.NO_HEAT,
                            "Once at 900°C it is " + array.getStatus() + " with " + array.getSteamIn().getAmount() + " mB left");
                })
                .thenSucceed();
    }

    // The FE a turbine should make this tick: flow x FE per mB x how far it has spun up x the bonus.
    private static int expectedFe(SteamTurbineArrayBlockEntity turbine, SteamGrade grade, double bonus) {
        double target = (double) SteamTurbineArrayBlockEntity.maxRpm() * turbine.maxFlow() * grade.arrayFePerMb()
                / (turbine.maxFlow() * SteamGrade.SUPERHEATED.arrayFePerMb());
        double spun = Math.min(1.0, turbine.getRpm() / Math.max(target, 1.0));
        return (int) Math.round(turbine.maxFlow() * grade.arrayFePerMb() * spun * bonus);
    }

    // Without an Exhaust port the spent steam vents: no Exhaust Steam anywhere, and no bonus.
    static void turbineVentsWithoutExhaust(GameTestHelper helper) {
        SteamGameTests.buildShell(helper, MIN, Direction.Axis.X, 3, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.getBlockEntity(MIN, SteamTurbineArrayBlockEntity.class).getSteam().set(0, SteamGrade.HIGH_PRESSURE.resource(), 60_000))
                .thenIdle(30)
                .thenExecute(() -> {
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(MIN, SteamTurbineArrayBlockEntity.class);
                    helper.assertTrue(!turbine.hasExhaust() && turbine.getExhaust().getAmount() == 0, "A turbine without an Exhaust port kept exhaust");
                    int expected = expectedFe(turbine, SteamGrade.HIGH_PRESSURE, 1.0);
                    helper.assertTrue(Math.abs(turbine.getFePerTick() - expected) <= 1, "Makes " + turbine.getFePerTick() + " FE/t, expected " + expected);
                })
                .thenSucceed();
    }

    // With an Exhaust port into a Pressurized Cylinder the spent steam arrives there as Exhaust Steam, mB for mB,
    // and the turbine makes 10% more (18% more with lubricant too). With its exhaust tank full and nowhere to
    // push, the bonus stops and it vents, still generating.
    static void turbineExhaustAndVacuumBonus(GameTestHelper helper) {
        BlockPos dry = MIN, oiled = new BlockPos(0, 1, 5);
        // The port on the south side of each turbine's middle ring, into the side of a cylinder (a cylinder's
        // bottom only gives out).
        BlockPos[] mins = { dry, oiled };
        for (BlockPos min : mins) {
            SteamGameTests.buildShell(helper, min, Direction.Axis.X, 3, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
            helper.setBlock(min.offset(1, 1, 3), ModBlocks.pressurizedCylinder(ConduitTier.ARCFORGED).get());
        }
        int[] steamStart = new int[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (BlockPos min : mins) {
                        SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(min, SteamTurbineArrayBlockEntity.class);
                        helper.assertTrue(turbine.isMaster(), "Turbine did not form");
                        MultiblockPorts.set(helper.getLevel(), turbine, helper.absolutePos(min.offset(1, 1, 2)), SideMode.EXHAUST, Direction.SOUTH);
                        helper.assertTrue(turbine.hasExhaust(), "The Exhaust port didn't register");
                        turbine.getSteam().set(0, SteamGrade.STEAM.resource(), 60_000);
                    }
                    steamStart[0] = 60_000;
                    SteamGameTests.fill(helper.getBlockEntity(oiled, SteamTurbineArrayBlockEntity.class).getFluidHandler(null),
                            FluidResource.of(ModFluids.HEAVY_OIL.get()), 1_000);
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    double[] bonus = { 1.0 + ArcforgeConfig.TURBINE_ARRAY_VACUUM_BONUS.getAsDouble(),
                            Lubricant.bonus() + ArcforgeConfig.TURBINE_ARRAY_VACUUM_BONUS.getAsDouble() };
                    for (int i = 0; i < 2; i++) {
                        SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(mins[i], SteamTurbineArrayBlockEntity.class);
                        helper.assertTrue(turbine.isVacuum(), "Turbine " + i + " has no vacuum bonus");
                        int expected = expectedFe(turbine, SteamGrade.STEAM, bonus[i]);
                        helper.assertTrue(Math.abs(turbine.getFePerTick() - expected) <= 1, "Turbine " + i + " makes " + turbine.getFePerTick() + " FE/t, expected " + expected);
                        PressurizedCylinderBlockEntity cylinder = helper.getBlockEntity(mins[i].offset(1, 1, 3), PressurizedCylinderBlockEntity.class);
                        int used = steamStart[0] - turbine.getSteam().getAmount();
                        int exhaust = cylinder.getGas().getAmount() + turbine.getExhaust().getAmount();
                        helper.assertTrue(cylinder.getGas().getFluid() == ModFluids.EXHAUST_STEAM.get() && exhaust == used,
                                "Turbine " + i + " used " + used + " mB of steam; " + exhaust + " mB of exhaust turned up");
                    }
                    // Block the dry turbine's port and fill its exhaust tank.
                    helper.setBlock(dry.offset(1, 1, 3), Blocks.STONE);
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(dry, SteamTurbineArrayBlockEntity.class);
                    turbine.getExhaust().set(0, FluidResource.of(ModFluids.EXHAUST_STEAM.get()), turbine.getExhaust().getCapacity());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(dry, SteamTurbineArrayBlockEntity.class);
                    helper.assertTrue(!turbine.isVacuum(), "A full exhaust tank still gives the bonus");
                    helper.assertTrue(turbine.getExhaust().getAmount() == turbine.getExhaust().getCapacity(), "The exhaust tank overfilled or drained");
                    int expected = expectedFe(turbine, SteamGrade.STEAM, 1.0);
                    helper.assertTrue(turbine.getFePerTick() > 0 && Math.abs(turbine.getFePerTick() - expected) <= 1,
                            "Venting, the turbine makes " + turbine.getFePerTick() + " FE/t, expected " + expected);
                })
                .thenSucceed();
    }

    // 120 mB/t in open air; 2 water sources and a blue ice add 40 and 60; enough blue ice caps it at 400. It
    // condenses 1:1.
    static void condenserRateFromCooling(GameTestHelper helper) {
        MultiblockTestHelpers.buildCube(helper, MIN, ModBlocks.CONDENSER_ARRAY_CASING.get());
        BlockPos centre = helper.absolutePos(MIN.offset(1, 1, 1));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(condenser(helper, MIN).isFormed(), "Condenser did not form");
                    int air = CondenserArrayBlockEntity.cooling(helper.getLevel(), centre).rate();
                    helper.assertTrue(air == 120, "In open air it condenses " + air + " mB/t");
                    // Two water sources in the bottom row against the west face (with stone under them so they
                    // stay sources), and a blue ice on top.
                    for (int z = 0; z < 3; z++) {
                        helper.setBlock(new BlockPos(-1, 0, z), Blocks.STONE);
                    }
                    helper.setBlock(new BlockPos(-1, 1, 0), Blocks.WATER);
                    helper.setBlock(new BlockPos(-1, 1, 2), Blocks.WATER);
                    helper.setBlock(MIN.offset(1, 3, 1), Blocks.BLUE_ICE);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    CondenserArrayBlockEntity.Cooling cooling = CondenserArrayBlockEntity.cooling(helper.getLevel(), centre);
                    helper.assertTrue(cooling.water() == 40 && cooling.ice() == 60 && cooling.rate() == 220,
                            "Water " + cooling.water() + ", ice " + cooling.ice() + ": " + cooling.rate() + " mB/t");
                    for (Direction side : Direction.values()) {
                        for (int a = -1; a <= 1; a++) {
                            for (int b = -1; b <= 1; b++) {
                                BlockPos face = MIN.offset(1, 1, 1).relative(side, 2);
                                BlockPos pos = switch (side.getAxis()) {
                                    case X -> face.offset(0, a, b);
                                    case Y -> face.offset(a, 0, b);
                                    case Z -> face.offset(a, b, 0);
                                };
                                if (pos.getY() >= 1) {
                                    helper.setBlock(pos, Blocks.BLUE_ICE);
                                }
                            }
                        }
                    }
                    helper.assertTrue(CondenserArrayBlockEntity.cooling(helper.getLevel(), centre).rate() == ArcforgeConfig.CONDENSER_MAX_RATE_PER_CUBE.getAsInt(),
                            "Packed in blue ice it isn't capped: " + CondenserArrayBlockEntity.cooling(helper.getLevel(), centre).rate());
                    SteamGameTests.fill(condenser(helper, MIN).getExhaustIn(), FluidResource.of(ModFluids.EXHAUST_STEAM.get()), 1_000);
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    CondenserArrayBlockEntity condenser = condenser(helper, MIN);
                    int total = condenser.getExhaustIn().getAmount() + condenser.getWaterOut().getAmount();
                    helper.assertTrue(condenser.getWaterOut().getAmount() > 0 && total == 1_000,
                            "1,000 mB of exhaust became " + condenser.getWaterOut().getAmount() + " mB of water, with " + condenser.getExhaustIn().getAmount() + " left");
                })
                .thenSucceed();
    }

    // The climate: x1.25 cold, x0.5 in the Nether, rounded down and capped. (The test world's biome is fixed, so
    // this checks the formula, and that the overworld doesn't count as the Nether.)
    static void condenserClimate(GameTestHelper helper) {
        helper.assertTrue(CondenserArrayBlockEntity.cooling(120, 40, 60, 1.25).rate() == 275, "Cold: " + CondenserArrayBlockEntity.cooling(120, 40, 60, 1.25).rate());
        helper.assertTrue(CondenserArrayBlockEntity.cooling(120, 0, 0, 0.5).rate() == 60, "Nether: " + CondenserArrayBlockEntity.cooling(120, 0, 0, 0.5).rate());
        helper.assertTrue(CondenserArrayBlockEntity.cooling(120, 0, 480, 1.25).rate() == 400, "Not capped");
        helper.assertTrue(CondenserArrayBlockEntity.cooling(120, 0, 0, 1.25).multiplierPercent() == 125, "Multiplier not kept for the GUI");
        helper.assertFalse(ClimateHelper.waterEvaporates(helper.getLevel(), helper.absolutePos(MIN)), "Water evaporates in the test world");
        helper.succeed();
    }

    private static int networkAmount(GameTestHelper helper, BlockPos conduit) {
        return ConduitNetworkManager.get(helper.getLevel()).getNetwork(helper.absolutePos(conduit)) instanceof FluidConduitNetwork network
                ? network.getAmount() : 0;
    }

    // Boiler (fed heat by the test) -> Pressurized Conduit -> turbine (Exhaust port) -> Pressurized Conduit ->
    // condenser -> Fluid Conduit -> the boiler's water port, with no pump. After a minute the turbine has made FE,
    // the boiler never ran dry, and the water, steam and exhaust (in machines and conduits) add up to what the
    // boiler started with.
    static void closedSteamLoop(GameTestHelper helper) {
        BlockPos boilerMin = new BlockPos(0, 1, 0);
        BlockPos turbineMin = new BlockPos(0, 5, 0);
        BlockPos condenserMin = new BlockPos(0, 5, 4);
        BlockPos steamConduit = new BlockPos(1, 4, 1);
        BlockPos exhaustConduit = new BlockPos(1, 6, 3);
        BlockPos[] waterConduits = { new BlockPos(1, 4, 5), new BlockPos(1, 3, 5), new BlockPos(1, 2, 5), new BlockPos(1, 2, 4), new BlockPos(1, 2, 3) };
        SteamGameTests.buildShell(helper, boilerMin, Direction.Axis.Y, 3, ModBlocks.STEAM_BOILER_ARRAY_CASING.get());
        SteamGameTests.buildShell(helper, turbineMin, Direction.Axis.X, 3, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        MultiblockTestHelpers.buildCube(helper, condenserMin, ModBlocks.CONDENSER_ARRAY_CASING.get());
        int start = 16_000;
        boolean[] running = { false };
        helper.onEachTick(() -> {
            if (!running[0]) {
                return;
            }
            SteamBoilerArrayBlockEntity boiler = helper.getBlockEntity(boilerMin, SteamBoilerArrayBlockEntity.class);
            boiler.getHeat().add(480);
            helper.assertTrue(boiler.getWater().getAmount() > 0, "The boiler ran dry");
        });
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamBoilerArrayBlockEntity boiler = helper.getBlockEntity(boilerMin, SteamBoilerArrayBlockEntity.class);
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(turbineMin, SteamTurbineArrayBlockEntity.class);
                    CondenserArrayBlockEntity condenser = condenser(helper, condenserMin);
                    helper.assertTrue(boiler.isMaster() && turbine.isMaster() && condenser.isFormed(), "Something did not form");
                    var level = helper.getLevel();
                    MultiblockPorts.set(level, boiler, helper.absolutePos(new BlockPos(1, 3, 1)), SideMode.OUTPUT, Direction.UP);
                    MultiblockPorts.set(level, boiler, helper.absolutePos(new BlockPos(1, 2, 2)), SideMode.INPUT, Direction.SOUTH);
                    MultiblockPorts.set(level, turbine, helper.absolutePos(new BlockPos(1, 5, 1)), SideMode.INPUT, Direction.DOWN);
                    MultiblockPorts.set(level, turbine, helper.absolutePos(new BlockPos(1, 6, 2)), SideMode.EXHAUST, Direction.SOUTH);
                    MultiblockPorts.set(level, condenser, helper.absolutePos(new BlockPos(1, 6, 4)), SideMode.INPUT, Direction.NORTH);
                    MultiblockPorts.set(level, condenser, helper.absolutePos(new BlockPos(1, 5, 5)), SideMode.OUTPUT, Direction.DOWN);
                    helper.setBlock(steamConduit, ModBlocks.conduit(ConduitType.GAS, ConduitTier.WROUGHT).get());
                    helper.setBlock(exhaustConduit, ModBlocks.conduit(ConduitType.GAS, ConduitTier.WROUGHT).get());
                    for (BlockPos pos : waterConduits) {
                        helper.setBlock(pos, ModBlocks.conduit(ConduitType.FLUID, ConduitTier.WROUGHT).get());
                    }
                    ConduitBlock.refreshConnections(level, helper.absolutePos(steamConduit));
                    ConduitBlock.refreshConnections(level, helper.absolutePos(exhaustConduit));
                    for (BlockPos pos : waterConduits) {
                        ConduitBlock.refreshConnections(level, helper.absolutePos(pos));
                    }
                    SteamGameTests.fill(boiler.getWater(), FluidResource.of(Fluids.WATER), start);
                    running[0] = true;
                })
                .thenIdle(1_200)
                .thenExecute(() -> {
                    SteamBoilerArrayBlockEntity boiler = helper.getBlockEntity(boilerMin, SteamBoilerArrayBlockEntity.class);
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(turbineMin, SteamTurbineArrayBlockEntity.class);
                    CondenserArrayBlockEntity condenser = condenser(helper, condenserMin);
                    helper.assertTrue(turbine.getEnergy().getAmountAsInt() > 0, "The turbine made no FE");
                    helper.assertTrue(condenser.getWaterOut().getAmount() + networkAmount(helper, waterConduits[0]) > 0
                            || boiler.getWater().getAmount() < start, "Nothing went round the loop");
                    int total = boiler.getWater().getAmount() + boiler.getSteam().getAmount() + turbine.getSteam().getAmount()
                            + turbine.getExhaust().getAmount() + condenser.getExhaustIn().getAmount() + condenser.getWaterOut().getAmount()
                            + networkAmount(helper, steamConduit) + networkAmount(helper, exhaustConduit) + networkAmount(helper, waterConduits[0]);
                    helper.assertTrue(Math.abs(total - start) <= start / 100, "The loop holds " + total + " mB, from " + start);
                })
                .thenSucceed();
    }
}
