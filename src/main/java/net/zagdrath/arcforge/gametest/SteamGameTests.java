/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.ShellCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.block.storage.PressurizedCylinderBlock;
import net.zagdrath.arcforge.blockentity.machine.ElectricPumpBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.common.MenuReach;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.BoilerPressure;
import net.zagdrath.arcforge.steam.SteamGrade;

// Steam and gases: the gas rules, Pressurized Conduits and Cylinders, the Electric Pump, the Steam Boiler
// and Steam Turbine Arrays, and the copper parts. Machines are placed facing north.
public final class SteamGameTests {
    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);

    private SteamGameTests() {}

    static int fill(ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(fluid, amount, tx);
            tx.commit();
            return inserted;
        }
    }

    // Steam can't go in a Fluid Tank; water can't go in a Pressurized Cylinder; a cylinder's gauge and
    // comparator follow its fill.
    static void gasRules(GameTestHelper helper) {
        BlockPos tankPos = new BlockPos(0, 1, 0);
        BlockPos cylinderPos = new BlockPos(2, 1, 0);
        helper.setBlock(tankPos, ModBlocks.fluidTank(ConduitTier.WROUGHT).get());
        helper.setBlock(cylinderPos, ModBlocks.pressurizedCylinder(ConduitTier.WROUGHT).get());
        FluidTankBlockEntity tank = helper.getBlockEntity(tankPos, FluidTankBlockEntity.class);
        PressurizedCylinderBlockEntity cylinder = helper.getBlockEntity(cylinderPos, PressurizedCylinderBlockEntity.class);
        helper.assertTrue(fill(tank.getFluidHandler(null), SteamGrade.STEAM.resource(), 1_000) == 0, "A fluid tank took steam");
        // (Automation moves up to the tier rate per operation.)
        helper.assertTrue(fill(tank.getFluidHandler(null), WATER, 1_000) == ConduitTier.WROUGHT.tankRate(), "A fluid tank refused water");
        helper.assertTrue(fill(cylinder.getFluidHandler(null), WATER, 1_000) == 0, "A cylinder took water");
        helper.assertTrue(fill(cylinder.getFluidHandler(null), SteamGrade.SUPERHEATED.resource(), 1_000) == 1_000, "A cylinder refused steam");
        helper.assertTrue(fill(cylinder.getFluidHandler(null), SteamGrade.STEAM.resource(), 1_000) == 0, "A cylinder mixed two gases");
        cylinder.getTank().set(0, SteamGrade.SUPERHEATED.resource(), ConduitTier.WROUGHT.cylinderCapacity() * 3 / 5);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(cylinderPos).getValue(PressurizedCylinderBlock.LEVEL) == 3, "A 60% cylinder's gauge reads "
                            + helper.getBlockState(cylinderPos).getValue(PressurizedCylinderBlock.LEVEL));
                    helper.assertTrue(cylinder.getComparatorSignal() == 9, "Comparator reads " + cylinder.getComparatorSignal());
                })
                .thenSucceed();
    }

    // A Pressurized Conduit carries steam from one cylinder to the one below and glows while it does.
    // A Fluid Conduit doesn't even connect to a cylinder.
    static void gasConduitCarriesSteam(GameTestHelper helper) {
        BlockPos top = new BlockPos(0, 3, 0);
        BlockPos conduit = new BlockPos(0, 2, 0);
        BlockPos bottom = new BlockPos(0, 1, 0);
        helper.setBlock(top, ModBlocks.pressurizedCylinder(ConduitTier.WROUGHT).get());
        helper.setBlock(bottom, ModBlocks.pressurizedCylinder(ConduitTier.WROUGHT).get());
        helper.setBlock(conduit, ModBlocks.conduit(ConduitType.GAS, ConduitTier.WROUGHT).get());
        PressurizedCylinderBlockEntity source = helper.getBlockEntity(top, PressurizedCylinderBlockEntity.class);
        PressurizedCylinderBlockEntity target = helper.getBlockEntity(bottom, PressurizedCylinderBlockEntity.class);
        source.getTank().set(0, SteamGrade.HIGH_PRESSURE.resource(), 10_000);

        BlockPos fluidConduit = new BlockPos(2, 2, 0);
        helper.setBlock(new BlockPos(2, 1, 0), ModBlocks.pressurizedCylinder(ConduitTier.WROUGHT).get());
        helper.setBlock(fluidConduit, ModBlocks.conduit(ConduitType.FLUID, ConduitTier.WROUGHT).get());
        // Placed without a player, so work out their connections as placing them would.
        net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(conduit));
        net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(fluidConduit));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(target.getGas().getAmount() > 0, "No steam reached the lower cylinder"))
                .thenExecute(() -> {
                    helper.assertTrue(SteamGrade.of(target.getGas().getFluid()) == SteamGrade.HIGH_PRESSURE, "Wrong gas arrived: " + target.getGas());
                    helper.assertTrue(helper.getBlockState(conduit).getValue(ActiveConduitBlock.ACTIVE), "The conduit is dark while steam moves");
                    helper.assertTrue(helper.getBlockState(fluidConduit).getValue(net.zagdrath.arcforge.block.conduit.ConduitBlock.DOWN)
                            == net.zagdrath.arcforge.conduit.ConnectionMode.NONE, "A fluid conduit connected to a cylinder");
                })
                .thenSucceed();
    }

    // Surrounds a row of water sources with stone so it can't flow away.
    private static void waterBasin(GameTestHelper helper, int sources) {
        for (int x = -1; x <= sources; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(new BlockPos(x + 1, 0, z + 1), Blocks.STONE);
                helper.setBlock(new BlockPos(x + 1, 1, z + 1), x >= 0 && x < sources && z == 0 ? Blocks.WATER : Blocks.STONE);
            }
        }
    }

    // A lone water source is pumped up and gone; the tank gets a bucket for 200 FE. It takes 8 Speed and 8
    // Energy upgrades.
    static void pumpDrainsSource(GameTestHelper helper) {
        waterBasin(helper, 1);
        BlockPos pumpPos = new BlockPos(1, 2, 1);
        helper.setBlock(pumpPos, ModBlocks.ELECTRIC_PUMP.get());
        ElectricPumpBlockEntity pump = helper.getBlockEntity(pumpPos, ElectricPumpBlockEntity.class);
        var items = pump.getItems();
        helper.assertTrue(items.isValid(items.getFirstUpgradeSlot(), net.neoforged.neoforge.transfer.item.ItemResource.of(ModItems.SPEED_UPGRADE.get())), "Pump refuses Speed");
        helper.assertTrue(!items.isValid(items.getFirstUpgradeSlot(), net.neoforged.neoforge.transfer.item.ItemResource.of(ModItems.HEAT_UPGRADE.get())), "Pump takes Heat");
        FiberGameTests.charge(pump.getEnergy(), 20_000);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pump.getTank().getAmount() == 1_000, "No bucket pumped yet"))
                .thenExecute(() -> {
                    helper.assertTrue(pump.getTank().getResource(0).getFluid() == Fluids.WATER, "Pumped " + pump.getTank().getResource(0));
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 1)).isAir(), "The source is still there");
                    int used = 20_000 - pump.getEnergy().getAmountAsInt();
                    helper.assertTrue(used >= 200 && used <= 220, "A bucket cost " + used + " FE");
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(pump.getStatus() == MachineStatus.NO_SOURCE, "Pump over air is " + pump.getStatus()))
                .thenSucceed();
    }

    // Water with two sources beside it is infinite: the pump keeps it.
    static void pumpLeavesInfiniteWater(GameTestHelper helper) {
        waterBasin(helper, 3);
        BlockPos pumpPos = new BlockPos(2, 2, 1);
        helper.setBlock(pumpPos, ModBlocks.ELECTRIC_PUMP.get());
        ElectricPumpBlockEntity pump = helper.getBlockEntity(pumpPos, ElectricPumpBlockEntity.class);
        FiberGameTests.charge(pump.getEnergy(), 20_000);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pump.getTank().getAmount() >= 2_000, "Not two buckets yet"))
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 1)).getFluidState().isSource(), "The infinite source was taken"))
                .thenSucceed();
    }

    // A 3-tall Steam Boiler Array at min, formed (3 ticks after this returns), with its water tank filled.
    static void boilerArray(GameTestHelper helper, BlockPos min) {
        buildShell(helper, min, Direction.Axis.Y, 3, ModBlocks.STEAM_BOILER_ARRAY_CASING.get());
    }

    static SteamBoilerArrayBlockEntity boilerAt(GameTestHelper helper, BlockPos min) {
        return helper.getBlockEntity(min, SteamBoilerArrayBlockEntity.class);
    }

    // Heated past 900°C a boiler array makes Superheated Steam; at 150°C plain Steam; below 100°C nothing.
    // Cooling into a lower grade turns the stored steam into it. A 3-tall array boils with up to 1,800 HU/t,
    // and Superheated costs it 16 HU/mB (20 x 0.8), so the hot one (kept topped up) makes 112.5 mB/t.
    static void boilerGrades(GameTestHelper helper) {
        BlockPos[] mins = { new BlockPos(0, 1, 0), new BlockPos(4, 1, 0), new BlockPos(8, 1, 0) };
        int[] temperatures = { 1_400, 150, 60 };
        boolean[] feeding = { true };
        for (BlockPos min : mins) {
            boilerArray(helper, min);
        }
        helper.onEachTick(() -> {
            if (feeding[0] && boilerAt(helper, mins[0]).isMaster()) {
                boilerAt(helper, mins[0]).getHeat().add(1_800);
            }
        });
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (int i = 0; i < 3; i++) {
                        SteamBoilerArrayBlockEntity boiler = boilerAt(helper, mins[i]);
                        helper.assertTrue(boiler.isMaster(), "Boiler array " + i + " did not form");
                        fill(boiler.getWater(), WATER, 40_000);
                        FiberGameTests.heatTo(boiler.getHeat(), temperatures[i]);
                    }
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    SteamBoilerArrayBlockEntity hot = boilerAt(helper, mins[0]), warm = boilerAt(helper, mins[1]), cold = boilerAt(helper, mins[2]);
                    helper.assertTrue(SteamGrade.of(hot.getSteam().getResource(0)) == SteamGrade.SUPERHEATED, "Hot boiler made " + hot.getSteam().getResource(0));
                    helper.assertTrue(Math.abs(hot.getCore().getRate() - 112.5) < 0.5, "Hot boiler makes " + hot.getCore().getRate() + " mB/t");
                    helper.assertTrue(SteamGrade.of(warm.getSteam().getResource(0)) == SteamGrade.STEAM, "Warm boiler made " + warm.getSteam().getResource(0));
                    helper.assertTrue(cold.getSteam().getAmount() == 0 && cold.getStatus() == MachineStatus.HEATING,
                            "Cold boiler is " + cold.getStatus() + " with " + cold.getSteam().getAmount() + " mB");
                    feeding[0] = false;
                    FiberGameTests.heatTo(hot.getHeat(), 600);
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(SteamGrade.of(boilerAt(helper, mins[0]).getSteam().getResource(0)) == SteamGrade.HIGH_PRESSURE,
                        "Cooled boiler holds " + boilerAt(helper, mins[0]).getSteam().getResource(0)))
                .thenSucceed();
    }

    // Set to High-Pressure, a boiler array fed less heat than it could boil heats to 500°C without boiling,
    // then holds there making High-Pressure Steam at the rate its heat comes in: 45 HU/t at 12 HU/mB.
    static void boilerPressureHolds(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        boilerArray(helper, min);
        boolean[] started = { false };
        helper.onEachTick(() -> {
            if (started[0]) {
                boilerAt(helper, min).getHeat().add(45);
            }
        });
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamBoilerArrayBlockEntity boiler = boilerAt(helper, min);
                    helper.assertTrue(boiler.isMaster(), "Boiler array did not form");
                    fill(boiler.getWater(), WATER, 40_000);
                    boiler.setPressure(BoilerPressure.HIGH_PRESSURE);
                    FiberGameTests.heatTo(boiler.getHeat(), 490);
                    started[0] = true;
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    SteamBoilerArrayBlockEntity boiler = boilerAt(helper, min);
                    helper.assertTrue(boiler.getSteam().getAmount() == 0 && boiler.getStatus() == MachineStatus.HEATING,
                            "Below 500°C the boiler is " + boiler.getStatus() + " with " + boiler.getSteam().getAmount() + " mB");
                })
                .thenWaitUntil(() -> helper.assertTrue(boilerAt(helper, min).getSteam().getAmount() > 0,
                        "No steam yet at " + boilerAt(helper, min).getHeat().getTemperature() + "°C"))
                .thenIdle(60)
                .thenExecute(() -> {
                    SteamBoilerArrayBlockEntity boiler = boilerAt(helper, min);
                    int temperature = boiler.getHeat().getTemperature();
                    helper.assertTrue(temperature >= 500 && temperature <= 501, "Boiler at " + temperature + "°C");
                    helper.assertTrue(SteamGrade.of(boiler.getSteam().getResource(0)) == SteamGrade.HIGH_PRESSURE, "Boiler made " + boiler.getSteam().getResource(0));
                    helper.assertTrue(boiler.getStatus() == MachineStatus.BOILING, "Boiler is " + boiler.getStatus());
                    helper.assertTrue(Math.abs(boiler.getCore().getRate() - 3.75) < 0.1, "Boiler makes " + boiler.getCore().getRate() + " mB/t");
                })
                .thenSucceed();
    }

    // On Auto, a boiler array fed heat unevenly (every other tick) holds at 100°C and stays Boiling rather
    // than dropping below boiling and flipping back to Heating.
    static void boilerAutoHoldsBoiling(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        boilerArray(helper, min);
        long[] start = { -1 };
        helper.onEachTick(() -> {
            if (start[0] < 0) {
                return;
            }
            SteamBoilerArrayBlockEntity boiler = boilerAt(helper, min);
            long ticks = helper.getTick() - start[0];
            if (ticks % 2 == 0) {
                boiler.getHeat().add(60);
            }
            helper.assertTrue(boiler.getHeat().getTemperature() >= 100, "Boiler fell to " + boiler.getHeat().getTemperature() + "°C");
            if (ticks > 5) {
                helper.assertTrue(boiler.getStatus() == MachineStatus.BOILING, "Boiler is " + boiler.getStatus() + " " + ticks + " ticks in");
            }
        });
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamBoilerArrayBlockEntity boiler = boilerAt(helper, min);
                    helper.assertTrue(boiler.isMaster(), "Boiler array did not form");
                    fill(boiler.getWater(), WATER, 40_000);
                    FiberGameTests.heatTo(boiler.getHeat(), 100);
                    start[0] = helper.getTick();
                })
                .thenIdle(60)
                .thenExecute(() -> helper.assertTrue(SteamGrade.of(boilerAt(helper, min).getSteam().getResource(0)) == SteamGrade.STEAM,
                        "Boiler made " + boilerAt(helper, min).getSteam().getResource(0)))
                .thenSucceed();
    }

    // FE per mB by grade is the array's: Steam 10, High-Pressure 18, Superheated 28. Three 3-long turbines at
    // full flow (120 mB/t) spin up at the same pace (each rotor's target follows its own grade), so at any
    // moment their output is flow x FE per mB x the same spin share.
    static void turbineOutput(GameTestHelper helper) {
        SteamGrade[] grades = SteamGrade.values();
        BlockPos[] mins = { new BlockPos(0, 1, 0), new BlockPos(0, 1, 4), new BlockPos(0, 1, 8) };
        for (BlockPos min : mins) {
            buildShell(helper, min, Direction.Axis.X, 3, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (int i = 0; i < 3; i++) {
                        helper.getBlockEntity(mins[i], SteamTurbineArrayBlockEntity.class).getSteam().set(0, grades[i].resource(), 60_000);
                    }
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    double share = -1;
                    for (int i = 0; i < 3; i++) {
                        SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(mins[i], SteamTurbineArrayBlockEntity.class);
                        int full = turbine.maxFlow() * grades[i].arrayFePerMb();
                        helper.assertTrue(turbine.getFePerTick() > 0 && turbine.getFePerTick() <= full,
                                grades[i] + " makes " + turbine.getFePerTick() + " FE/t, more than " + full);
                        double mine = (double) turbine.getFePerTick() / full;
                        helper.assertTrue(share < 0 || Math.abs(mine - share) < 0.01, grades[i] + " is at " + mine + " of its full output, the others at " + share);
                        share = mine;
                    }
                })
                .thenSucceed();
    }

    // A 3x3 tube of casings along an axis, with glass at the given (non-corner) positions.
    static void buildShell(GameTestHelper helper, BlockPos min, Direction.Axis axis, int length,
            net.minecraft.world.level.block.Block casing, BlockPos... glass) {
        java.util.Set<BlockPos> panes = java.util.Set.of(glass);
        for (int along = 0; along < length; along++) {
            for (int a = 0; a < 3; a++) {
                for (int b = 0; b < 3; b++) {
                    BlockPos pos = switch (axis) {
                        case X -> min.offset(along, a, b);
                        case Y -> min.offset(a, along, b);
                        case Z -> min.offset(a, b, along);
                    };
                    boolean core = a == 1 && b == 1 && along > 0 && along < length - 1;
                    if (!core) {
                        helper.setBlock(pos, panes.contains(pos) ? ModBlocks.PRESSURE_GLASS.get() : casing);
                    }
                }
            }
        }
    }

    // 3x3x4 forms (with a wall window), the master is the minimum corner; 3x3x8 is too tall; glass on a
    // corner doesn't form; breaking a pane breaks it.
    static void boilerArrayForms(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        BlockPos pane = new BlockPos(1, 2, 0);
        buildShell(helper, min, Direction.Axis.Y, 4, ModBlocks.STEAM_BOILER_ARRAY_CASING.get(), pane, new BlockPos(1, 3, 0));
        BlockPos tallMin = new BlockPos(4, 1, 0);
        buildShell(helper, tallMin, Direction.Axis.Y, 8, ModBlocks.STEAM_BOILER_ARRAY_CASING.get());
        BlockPos cornerMin = new BlockPos(0, 1, 4);
        buildShell(helper, cornerMin, Direction.Axis.Y, 3, ModBlocks.STEAM_BOILER_ARRAY_CASING.get(), new BlockPos(2, 3, 6));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamBoilerArrayBlockEntity boiler = helper.getBlockEntity(min, SteamBoilerArrayBlockEntity.class);
                    helper.assertTrue(boiler.isMaster() && boiler.getHeight() == 4, "Boiler array: master " + boiler.isMaster() + ", height " + boiler.getHeight());
                    helper.assertTrue(PressureGlassBlock.isFormed(helper.getBlockState(pane)), "The window is not part of it");
                    helper.assertTrue(boiler.getWater().getCapacity() == 64_000, "Water tank holds " + boiler.getWater().getCapacity());
                    helper.assertTrue(!ShellCasingBlock.isFormed(helper.getBlockState(tallMin)), "A 3x3x8 boiler formed");
                    helper.assertTrue(!ShellCasingBlock.isFormed(helper.getBlockState(cornerMin)), "A boiler with glass on a corner formed");
                    // Water goes in through an input port on the left (east) face.
                    MultiblockPorts.set(helper.getLevel(), boiler, helper.absolutePos(new BlockPos(2, 2, 1)), SideMode.INPUT, Direction.EAST);
                    var left = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(new BlockPos(2, 2, 1)), Direction.EAST);
                    helper.assertTrue(left != null, "The left input port takes no water");
                    helper.setBlock(pane, Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(!ShellCasingBlock.isFormed(helper.getBlockState(min)), "Still formed without its window"))
                .thenSucceed();
    }

    // A 9-long turbine's menu (at its master, the minimum corner) stays open for a player at the far end, who
    // is out of reach of the master itself; it still closes once the player walks away from the turbine.
    static void turbineArrayMenuReach(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        buildShell(helper, min, Direction.Axis.X, 9, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(min, SteamTurbineArrayBlockEntity.class).isMaster(), "Turbine did not form");
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    ContainerLevelAccess access = ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(min));
                    Block casing = ModBlocks.STEAM_TURBINE_ARRAY_CASING.get();
                    // Two blocks past the far (+X) end cap, level with the middle.
                    Vec3 farEnd = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(10, 1, 1)));
                    player.setPos(farEnd.x, farEnd.y, farEnd.z);
                    helper.assertTrue(!player.isWithinBlockInteractionRange(helper.absolutePos(min), 4.0), "The master is within vanilla reach; the test needs a longer turbine");
                    helper.assertTrue(MenuReach.stillValid(access, player, casing), "The menu closes at the far end of the turbine");
                    Vec3 away = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(24, 1, 1)));
                    player.setPos(away.x, away.y, away.z);
                    helper.assertTrue(!MenuReach.stillValid(access, player, casing), "The menu stays open far from the turbine");
                })
                .thenSucceed();
    }

    // At the same flow, a turbine on High-Pressure Steam spins faster than one on plain Steam (its speed
    // follows the power in the steam).
    static void turbineArrayGradeSpeed(GameTestHelper helper) {
        BlockPos steamMin = new BlockPos(0, 1, 0), pressureMin = new BlockPos(0, 1, 4);
        buildShell(helper, steamMin, Direction.Axis.X, 3, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        buildShell(helper, pressureMin, Direction.Axis.X, 3, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.getBlockEntity(steamMin, SteamTurbineArrayBlockEntity.class).getSteam().set(0, SteamGrade.STEAM.resource(), 60_000);
                    helper.getBlockEntity(pressureMin, SteamTurbineArrayBlockEntity.class).getSteam().set(0, SteamGrade.HIGH_PRESSURE.resource(), 60_000);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    double steam = helper.getBlockEntity(steamMin, SteamTurbineArrayBlockEntity.class).getRpm();
                    double pressure = helper.getBlockEntity(pressureMin, SteamTurbineArrayBlockEntity.class).getRpm();
                    helper.assertTrue(pressure > steam * 1.5, "High-Pressure spins at " + pressure + " RPM, Steam at " + steam);
                })
                .thenSucceed();
    }

    // A 3x3x5 turbine along X forms with its generator at the +X end; with steam its rotor spins up over a
    // few seconds (output rising), and coasts down when the steam runs out.
    static void turbineArraySpins(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        buildShell(helper, min, Direction.Axis.X, 5, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get(), new BlockPos(2, 3, 0), new BlockPos(2, 2, 0));
        double[] early = new double[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(min, SteamTurbineArrayBlockEntity.class);
                    helper.assertTrue(turbine.isMaster() && turbine.getLength() == 5, "Turbine: master " + turbine.isMaster() + ", length " + turbine.getLength());
                    helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 1)).getValue(SteamTurbineArrayCasingBlock.END)
                            == SteamTurbineArrayCasingBlock.End.GENERATOR, "No generator at the +X end");
                    helper.assertTrue(helper.getBlockState(new BlockPos(0, 2, 1)).getValue(SteamTurbineArrayCasingBlock.END)
                            == SteamTurbineArrayCasingBlock.End.BEARING, "No bearing at the -X end");
                    helper.assertTrue(turbine.maxFlow() == 200, "Max flow " + turbine.maxFlow());
                    turbine.getSteam().set(0, SteamGrade.SUPERHEATED.resource(), 60_000);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(min, SteamTurbineArrayBlockEntity.class);
                    early[0] = turbine.getRpm();
                    helper.assertTrue(turbine.getStatus() == MachineStatus.SPINNING_UP, "Turbine is " + turbine.getStatus());
                    helper.assertTrue(early[0] > 100 && early[0] < 1_000, "After a second the rotor is at " + early[0] + " RPM");
                    helper.assertTrue(turbine.getFePerTick() > 0 && turbine.getFePerTick() < 200 * 28, "Output while spinning up: " + turbine.getFePerTick());
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(min, SteamTurbineArrayBlockEntity.class);
                    helper.assertTrue(turbine.getRpm() > early[0], "The rotor did not keep spinning up");
                    turbine.getSteam().set(0, FluidResource.EMPTY, 0);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(min, SteamTurbineArrayBlockEntity.class);
                    helper.assertTrue(turbine.getStatus() == MachineStatus.COASTING && turbine.getFePerTick() == 0,
                            "Without steam the turbine is " + turbine.getStatus() + " making " + turbine.getFePerTick() + " FE/t");
                })
                .thenSucceed();
    }

    // The turbine's front is its window, so its ends are its left and right: with glass on the north side it
    // faces north. With an energy port on the generator (east) end and an input port on the bearing (west)
    // end, conduits there connect to take FE out and put steam in.
    static void turbineArrayConduits(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        buildShell(helper, min, Direction.Axis.X, 3, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get(), new BlockPos(2, 2, 1));
        BlockPos west = new BlockPos(0, 2, 2);
        BlockPos east = new BlockPos(4, 2, 2);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(min, SteamTurbineArrayBlockEntity.class);
                    helper.assertTrue(turbine.isMaster(), "Turbine did not form");
                    helper.assertTrue(turbine.getStructureFacing() == Direction.NORTH, "Facing " + turbine.getStructureFacing());
                    MultiblockPorts.set(helper.getLevel(), turbine, helper.absolutePos(west.east()), SideMode.INPUT, Direction.WEST);
                    MultiblockPorts.set(helper.getLevel(), turbine, helper.absolutePos(east.west()), SideMode.ENERGY, Direction.EAST);
                    helper.setBlock(west, ModBlocks.conduit(ConduitType.GAS, ConduitTier.WROUGHT).get());
                    helper.setBlock(east, ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get());
                    net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(west));
                    net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(east));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    var westMode = net.zagdrath.arcforge.block.conduit.ConduitBlock.mode(helper.getBlockState(west), Direction.EAST);
                    var eastMode = net.zagdrath.arcforge.block.conduit.ConduitBlock.mode(helper.getBlockState(east), Direction.WEST);
                    helper.assertTrue(westMode == net.zagdrath.arcforge.conduit.ConnectionMode.INPUT, "Steam conduit on the bearing end is " + westMode);
                    helper.assertTrue(eastMode != net.zagdrath.arcforge.conduit.ConnectionMode.NONE, "Energy conduit on the generator end is not connected");
                })
                .thenSucceed();
    }

    // Breaking a boiler array and finishing it again from another side keeps its facing and its ports, so
    // the conduits there reconnect.
    static void boilerArrayRebuildKeepsSides(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        buildShell(helper, min, Direction.Axis.Y, 3, ModBlocks.STEAM_BOILER_ARRAY_CASING.get());
        BlockPos east = new BlockPos(4, 2, 2);
        BlockPos broken = new BlockPos(2, 2, 1);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamBoilerArrayBlockEntity boiler = helper.getBlockEntity(min, SteamBoilerArrayBlockEntity.class);
                    helper.assertTrue(boiler.isMaster() && boiler.getStructureFacing() == Direction.NORTH, "Boiler did not form facing north");
                    MultiblockPorts.set(helper.getLevel(), boiler, helper.absolutePos(east.west()), SideMode.INPUT, Direction.EAST);
                    helper.setBlock(east, ModBlocks.conduit(ConduitType.FLUID, ConduitTier.WROUGHT).get());
                    net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(east));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    var mode = net.zagdrath.arcforge.block.conduit.ConduitBlock.mode(helper.getBlockState(east), Direction.WEST);
                    helper.assertTrue(mode == net.zagdrath.arcforge.conduit.ConnectionMode.INPUT, "Conduit on the left (east) face is " + mode);
                    helper.setBlock(broken, net.minecraft.world.level.block.Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertFalse(helper.getBlockEntity(min, SteamBoilerArrayBlockEntity.class).isMaster(), "Boiler did not break");
                    helper.setBlock(broken, ModBlocks.STEAM_BOILER_ARRAY_CASING.get());
                    // Finished by a player standing on the south side.
                    net.zagdrath.arcforge.block.multiblock.SteamBoilerArrayCasingBlock.STRUCTURE.rebuild(helper.getLevel(), helper.absolutePos(broken), null, Direction.SOUTH);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    SteamBoilerArrayBlockEntity boiler = helper.getBlockEntity(min, SteamBoilerArrayBlockEntity.class);
                    helper.assertTrue(boiler.isMaster(), "Boiler did not re-form");
                    helper.assertTrue(boiler.getStructureFacing() == Direction.NORTH, "Rebuilt boiler faces " + boiler.getStructureFacing());
                    var mode = net.zagdrath.arcforge.block.conduit.ConduitBlock.mode(helper.getBlockState(east), Direction.WEST);
                    helper.assertTrue(mode == net.zagdrath.arcforge.conduit.ConnectionMode.INPUT, "Conduit did not reconnect: " + mode);
                })
                .thenSucceed();
    }

    // Copper presses like steel; the upgrades are made from plates now.
    static void copperPartsAndUpgrades(GameTestHelper helper) {
        var level = helper.getLevel();
        var plate = MachineRecipes.pressing(level, new ItemStack(ModItems.PLATE_DIE.get()), new ItemStack(Items.COPPER_INGOT)).orElse(null);
        helper.assertTrue(plate != null && plate.value().result().create().is(ModItems.COPPER_PLATE.get()), "No copper plate recipe");
        var gear = MachineRecipes.pressing(level, new ItemStack(ModItems.GEAR_DIE.get()), new ItemStack(Items.COPPER_INGOT, 4)).orElse(null);
        helper.assertTrue(gear != null && gear.value().result().create().is(ModItems.COPPER_GEAR.get()), "No copper gear recipe");
        var rod = MachineRecipes.pressing(level, new ItemStack(ModItems.ROD_DIE.get()), new ItemStack(Items.COPPER_INGOT)).orElse(null);
        helper.assertTrue(rod != null && rod.value().result().create().getCount() == 2, "Rod die doesn't make 2 copper rods");
        for (var holder : level.recipeAccess().recipeMap().byType(RecipeType.CRAFTING)) {
            String id = holder.id().identifier().getPath().replaceFirst("^crafting/", "");
            if (id.equals("speed_upgrade") || id.equals("energy_upgrade") || id.equals("heat_upgrade")) {
                boolean usesPlate = holder.value().placementInfo().ingredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(ModItems.STEEL_PLATE.get())));
                boolean usesIngot = holder.value().placementInfo().ingredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(ModItems.STEEL_INGOT.get())));
                helper.assertTrue(usesPlate && !usesIngot, id + " still uses a steel ingot");
            }
        }
        helper.succeed();
    }
}
