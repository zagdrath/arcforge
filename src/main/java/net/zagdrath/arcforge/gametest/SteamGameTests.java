/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
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
import net.zagdrath.arcforge.blockentity.machine.SteamBoilerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.SteamTurbineBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.SteamGrade;

// Steam and gases: the gas rules, Pressurized Conduits and Cylinders, the Electric Pump, the Steam Boiler
// and Turbine and their arrays, and the copper parts. Machines are placed facing north.
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

    // Heated past 900°C the boiler makes Superheated Steam; at 150°C plain Steam; below 100°C nothing.
    // Cooling into a lower grade turns the stored steam into it.
    static void boilerGrades(GameTestHelper helper) {
        BlockPos hotPos = new BlockPos(0, 1, 0);
        BlockPos warmPos = new BlockPos(2, 1, 0);
        BlockPos coldPos = new BlockPos(4, 1, 0);
        SteamBoilerBlockEntity[] boilers = new SteamBoilerBlockEntity[3];
        int[] temperatures = { 1_000, 150, 60 };
        BlockPos[] positions = { hotPos, warmPos, coldPos };
        for (int i = 0; i < 3; i++) {
            helper.setBlock(positions[i], ModBlocks.STEAM_BOILER.get());
            boilers[i] = helper.getBlockEntity(positions[i], SteamBoilerBlockEntity.class);
            fill(boilers[i].getWater(), WATER, 8_000);
            FiberGameTests.heatTo(boilers[i].getHeat(), temperatures[i]);
        }
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(SteamGrade.of(boilers[0].getSteam().getResource(0)) == SteamGrade.SUPERHEATED, "Hot boiler made " + boilers[0].getSteam().getResource(0));
                    // 80 HU/t at 20 HU/mB.
                    helper.assertTrue(Math.abs(boilers[0].getCore().getRate() - 4.0) < 0.01, "Hot boiler makes " + boilers[0].getCore().getRate() + " mB/t");
                    helper.assertTrue(SteamGrade.of(boilers[1].getSteam().getResource(0)) == SteamGrade.STEAM, "Warm boiler made " + boilers[1].getSteam().getResource(0));
                    helper.assertTrue(boilers[2].getSteam().getAmount() == 0 && boilers[2].getStatus() == MachineStatus.HEATING,
                            "Cold boiler is " + boilers[2].getStatus() + " with " + boilers[2].getSteam().getAmount() + " mB");
                    FiberGameTests.heatTo(boilers[0].getHeat(), 600);
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(SteamGrade.of(boilers[0].getSteam().getResource(0)) == SteamGrade.HIGH_PRESSURE,
                        "Cooled boiler holds " + boilers[0].getSteam().getResource(0)))
                .thenSucceed();
    }

    // FE per mB by grade: Steam 8, High-Pressure 14, Superheated 22, at 10 mB/t.
    static void turbineOutput(GameTestHelper helper) {
        SteamGrade[] grades = SteamGrade.values();
        int[] expected = { 80, 140, 220 };
        SteamTurbineBlockEntity[] turbines = new SteamTurbineBlockEntity[3];
        for (int i = 0; i < 3; i++) {
            BlockPos pos = new BlockPos(i * 2, 1, 0);
            helper.setBlock(pos, ModBlocks.STEAM_TURBINE.get());
            turbines[i] = helper.getBlockEntity(pos, SteamTurbineBlockEntity.class);
            fill(turbines[i].getSteam(), grades[i].resource(), 1_000);
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (int i = 0; i < 3; i++) {
                        helper.assertTrue(turbines[i].getFePerTick() == expected[i], grades[i] + " makes " + turbines[i].getFePerTick() + " FE/t");
                        helper.assertTrue(turbines[i].getStatus() == MachineStatus.GENERATING, grades[i] + " turbine is " + turbines[i].getStatus());
                    }
                })
                .thenSucceed();
    }

    // A 3x3 tube of casings along an axis, with glass at the given (non-corner) positions.
    private static void buildShell(GameTestHelper helper, BlockPos min, Direction.Axis axis, int length,
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
                    // Water goes in through the left face (default input), from any casing on it.
                    var left = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(new BlockPos(2, 2, 1)), Direction.EAST);
                    helper.assertTrue(left != null || helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                            helper.absolutePos(new BlockPos(0, 2, 1)), Direction.WEST) != null, "No side takes water");
                    helper.setBlock(pane, Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(!ShellCasingBlock.isFormed(helper.getBlockState(min)), "Still formed without its window"))
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
    // faces north, and by default the generator (east) end takes FE out and the bearing (west) end takes steam.
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
            String id = holder.id().identifier().getPath();
            if (id.equals("speed_upgrade") || id.equals("energy_upgrade") || id.equals("heat_upgrade")) {
                boolean usesPlate = holder.value().placementInfo().ingredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(ModItems.STEEL_PLATE.get())));
                boolean usesIngot = holder.value().placementInfo().ingredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(ModItems.STEEL_INGOT.get())));
                helper.assertTrue(usesPlate && !usesIngot, id + " still uses a steel ingot");
            }
        }
        helper.succeed();
    }
}
