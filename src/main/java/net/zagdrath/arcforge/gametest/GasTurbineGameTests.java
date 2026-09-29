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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.multiblock.GasTurbineArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;

// The Gas Turbine Array: which fuels it takes, the throttle, flameout and restart, its exhaust heat, a blocked
// intake, and the rotor spooling up and coasting down. Every turbine here is 5 long along X, so it burns up
// to 2,800 HU/t (7 mB/t of Naphtha for 4,200 FE/t).
public final class GasTurbineGameTests {
    private GasTurbineGameTests() {}

    private static void build(GameTestHelper helper, BlockPos min) {
        SteamGameTests.buildShell(helper, min, Direction.Axis.X, 5, ModBlocks.GAS_TURBINE_ARRAY_CASING.get());
    }

    private static GasTurbineArrayBlockEntity turbine(GameTestHelper helper, BlockPos min) {
        return helper.getBlockEntity(min, GasTurbineArrayBlockEntity.class);
    }

    private static int fill(GasTurbineArrayBlockEntity turbine, Fluid fluid, int amount) {
        return SteamGameTests.fill(turbine.getFluidHandler(SideMode.INPUT), FluidResource.of(fluid), amount);
    }

    private static GasTurbineArrayCasingBlock.End end(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(GasTurbineArrayCasingBlock.END);
    }

    // Naphtha, Light Oil, Ethanol and Hydrogen burn in it; Heavy Oil, Creosote and water don't. Both ends are
    // open, so the intake is the negative (west) end; the tank holds 8,000 mB per block of length.
    static void fuelAcceptance(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        build(helper, min);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    GasTurbineArrayBlockEntity turbine = turbine(helper, min);
                    helper.assertTrue(turbine.isMaster() && turbine.getLength() == 5, "The turbine did not form");
                    helper.assertTrue(end(helper, new BlockPos(0, 2, 1)) == GasTurbineArrayCasingBlock.End.INTAKE, "No intake at the west end");
                    helper.assertTrue(end(helper, new BlockPos(4, 2, 1)) == GasTurbineArrayCasingBlock.End.EXHAUST, "No exhaust at the east end");
                    helper.assertTrue(end(helper, new BlockPos(2, 1, 1)) == GasTurbineArrayCasingBlock.End.NONE, "A side casing is marked as an end");
                    helper.assertTrue(turbine.getFuel().getCapacity() == 40_000, "The fuel tank holds " + turbine.getFuel().getCapacity());
                    for (Fluid fuel : new Fluid[] { ModFluids.NAPHTHA.get(), ModFluids.LIGHT_OIL.get(), ModFluids.ETHANOL.get(), ModFluids.HYDROGEN.get() }) {
                        helper.assertTrue(GasTurbineArrayBlockEntity.isFuel(FluidResource.of(fuel)), fuel + " is not a Gas Turbine fuel");
                    }
                    for (Fluid other : new Fluid[] { ModFluids.HEAVY_OIL.get(), ModFluids.CREOSOTE.get(), Fluids.WATER }) {
                        helper.assertFalse(GasTurbineArrayBlockEntity.isFuel(FluidResource.of(other)), other + " is a Gas Turbine fuel");
                        helper.assertTrue(fill(turbine, other, 1_000) == 0, "The tank took " + other);
                    }
                    helper.assertTrue(fill(turbine, ModFluids.NAPHTHA.get(), 1_000) == 1_000, "The tank refused Naphtha");
                })
                .thenSucceed();
    }

    // In Throttle mode the signal sets the fuel burned: signal 15 (a redstone block) burns 7 mB/t of Naphtha for
    // 4,200 FE/t; signal 5 (a comparator reading a chest a third full) a third of that, 1,400 FE/t.
    static void throttle(GameTestHelper helper) {
        BlockPos full = new BlockPos(0, 1, 0);
        BlockPos third = new BlockPos(0, 1, 5);
        build(helper, full);
        build(helper, third);
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);
        BlockPos chest = new BlockPos(2, 1, 9);
        BlockPos comparator = new BlockPos(2, 1, 8);
        helper.setBlock(new BlockPos(2, 0, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, 9), Blocks.STONE);
        helper.setBlock(chest, Blocks.CHEST);
        ChestBlockEntity box = helper.getBlockEntity(chest, ChestBlockEntity.class);
        for (int i = 0; i < 9; i++) {
            box.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.SOUTH));
        int[] fuelBefore = new int[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (BlockPos min : new BlockPos[] { full, third }) {
                        GasTurbineArrayBlockEntity turbine = turbine(helper, min);
                        helper.assertTrue(turbine.isMaster(), "A turbine did not form");
                        turbine.setRedstoneMode(RedstoneMode.THROTTLE);
                        fill(turbine, ModFluids.NAPHTHA.get(), 20_000);
                    }
                })
                .thenIdle(180)
                .thenExecute(() -> fuelBefore[0] = turbine(helper, full).getFuel().getAmount())
                .thenIdle(1)
                .thenExecute(() -> {
                    GasTurbineArrayBlockEntity fullTurbine = turbine(helper, full);
                    GasTurbineArrayBlockEntity thirdTurbine = turbine(helper, third);
                    helper.assertTrue(Math.abs(fullTurbine.throttle() - 1.0) < 1e-9, "Signal 15 throttles to " + fullTurbine.throttle());
                    helper.assertTrue(Math.abs(thirdTurbine.throttle() - 5.0 / 15.0) < 1e-9, "Signal 5 throttles to " + thirdTurbine.throttle());
                    helper.assertTrue(fullTurbine.getFePerTick() == 4_200, "Full throttle makes " + fullTurbine.getFePerTick() + " FE/t");
                    helper.assertTrue(thirdTurbine.getFePerTick() == 1_400, "A third throttle makes " + thirdTurbine.getFePerTick() + " FE/t");
                    int burned = fuelBefore[0] - fullTurbine.getFuel().getAmount();
                    helper.assertTrue(burned == 7, "Full throttle burned " + burned + " mB in a tick");
                    helper.assertTrue(Math.abs(fullTurbine.fuelPerTick() - 7.0) < 1e-9, "Full throttle reports " + fullTurbine.fuelPerTick() + " mB/t");
                })
                .thenSucceed();
    }

    // Running dry is a flameout: it stops, waits 20 ticks, then ignites again once there is fuel.
    static void flameoutRestart(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        build(helper, min);
        long[] flamedOut = new long[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> fill(turbine(helper, min), ModFluids.NAPHTHA.get(), 100))
                .thenWaitUntil(() -> helper.assertTrue(turbine(helper, min).getPhase() == GasTurbineArrayBlockEntity.Phase.RUNNING, "Not running yet"))
                .thenWaitUntil(() -> helper.assertTrue(turbine(helper, min).getStatus() == MachineStatus.FLAMEOUT, "No flameout yet"))
                .thenExecute(() -> {
                    GasTurbineArrayBlockEntity turbine = turbine(helper, min);
                    helper.assertTrue(turbine.getPhase() == GasTurbineArrayBlockEntity.Phase.OFF, "Still " + turbine.getPhase() + " after a flameout");
                    helper.assertTrue(turbine.getFePerTick() == 0, "Making " + turbine.getFePerTick() + " FE/t after a flameout");
                    flamedOut[0] = helper.getLevel().getGameTime();
                    fill(turbine, ModFluids.NAPHTHA.get(), 1_000);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(turbine(helper, min).getPhase() == GasTurbineArrayBlockEntity.Phase.OFF,
                        "Reignited " + (helper.getLevel().getGameTime() - flamedOut[0]) + " ticks after a flameout"))
                .thenWaitUntil(() -> helper.assertTrue(turbine(helper, min).getPhase() == GasTurbineArrayBlockEntity.Phase.RUNNING, "Did not restart"))
                .thenExecute(() -> {
                    long waited = helper.getLevel().getGameTime() - flamedOut[0];
                    helper.assertTrue(waited >= 40, "Restarted " + waited + " ticks after the flameout (20 delay + 20 ignition)");
                })
                .thenSucceed();
    }

    // A quarter of the fuel heat leaves the exhaust at half the burn temperature (Naphtha: 700 HU/t at 600°C).
    // Its default Heat port, the middle of the exhaust end, feeds a Steam Boiler Array's Heat port, which
    // boils with it; nothing is vented.
    static void exhaustToBoiler(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        BlockPos boilerMin = new BlockPos(5, 1, 0);
        build(helper, min);
        SteamGameTests.buildShell(helper, boilerMin, Direction.Axis.Y, 3, ModBlocks.STEAM_BOILER_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    GasTurbineArrayBlockEntity turbine = turbine(helper, min);
                    SteamBoilerArrayBlockEntity boiler = helper.getBlockEntity(boilerMin, SteamBoilerArrayBlockEntity.class);
                    helper.assertTrue(turbine.isMaster() && boiler.isMaster(), "Something did not form");
                    MultiblockPorts.set(helper.getLevel(), boiler, helper.absolutePos(new BlockPos(5, 2, 1)), SideMode.HEAT, Direction.WEST);
                    SteamGameTests.fill(boiler.getWater(), FluidResource.of(Fluids.WATER), 40_000);
                    fill(turbine, ModFluids.NAPHTHA.get(), 20_000);
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    GasTurbineArrayBlockEntity turbine = turbine(helper, min);
                    SteamBoilerArrayBlockEntity boiler = helper.getBlockEntity(boilerMin, SteamBoilerArrayBlockEntity.class);
                    helper.assertTrue(turbine.getExhaustHu() == 700 && turbine.getExhaustCelsius() == 600,
                            "Exhaust is " + turbine.getExhaustHu() + " HU/t at " + turbine.getExhaustCelsius() + "°C");
                    helper.assertFalse(turbine.isVenting(), "The exhaust vents with a boiler on its Heat port");
                    helper.assertTrue(boiler.getHeat().getStored() > 0 || boiler.getSteam().getAmount() > 0, "The boiler got no heat");
                    helper.assertTrue(boiler.getSteam().getAmount() > 0, "The boiler made no steam");
                })
                .thenSucceed();
    }

    // A block in front of the intake shuts it (within the 20-tick check): no fuel burns and the status says so.
    // A turbine built with its west end already blocked takes its intake at the east end instead. The intake's
    // middle casing never holds a port.
    static void blockedIntake(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        BlockPos otherMin = new BlockPos(0, 1, 5);
        build(helper, min);
        helper.setBlock(new BlockPos(-1, 2, 6), Blocks.STONE);
        build(helper, otherMin);
        int[] fuel = new int[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    GasTurbineArrayBlockEntity turbine = turbine(helper, min);
                    helper.assertTrue(turbine.isMaster() && turbine(helper, otherMin).isMaster(), "A turbine did not form");
                    helper.assertTrue(end(helper, new BlockPos(4, 2, 6)) == GasTurbineArrayCasingBlock.End.INTAKE,
                            "The turbine with a blocked west end took its intake at " + turbine(helper, otherMin).getIntakeEnd());
                    helper.assertFalse(MultiblockPorts.canHold(helper.getLevel(), turbine, helper.absolutePos(new BlockPos(0, 2, 1))),
                            "The intake can hold a port");
                    fill(turbine, ModFluids.NAPHTHA.get(), 20_000);
                })
                .thenWaitUntil(() -> helper.assertTrue(turbine(helper, min).getFePerTick() > 0, "Not generating yet"))
                .thenExecute(() -> helper.setBlock(new BlockPos(-1, 2, 1), Blocks.STONE))
                .thenIdle(25)
                .thenExecute(() -> fuel[0] = turbine(helper, min).getFuel().getAmount())
                .thenIdle(10)
                .thenExecute(() -> {
                    GasTurbineArrayBlockEntity turbine = turbine(helper, min);
                    helper.assertTrue(turbine.getStatus() == MachineStatus.INTAKE_SHUT, "Status is " + turbine.getStatus());
                    helper.assertTrue(turbine.getFuel().getAmount() == fuel[0], "It burned fuel with the intake shut");
                    helper.assertTrue(turbine.getFePerTick() == 0, "It makes " + turbine.getFePerTick() + " FE/t with the intake shut");
                })
                .thenSucceed();
    }

    // Ignition takes 20 ticks without burning fuel or making FE; then the rotor spools to 95% of full speed in
    // about 30 ticks, and with the fuel gone it coasts down to under 10% in about 75.
    static void spool(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        build(helper, min);
        int[] fuelAtIgnition = new int[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> fill(turbine(helper, min), ModFluids.NAPHTHA.get(), 20_000))
                .thenWaitUntil(() -> helper.assertTrue(turbine(helper, min).getPhase() == GasTurbineArrayBlockEntity.Phase.IGNITING, "Not igniting"))
                .thenExecute(() -> {
                    GasTurbineArrayBlockEntity turbine = turbine(helper, min);
                    fuelAtIgnition[0] = turbine.getFuel().getAmount();
                    helper.assertTrue(turbine.getStatus() == MachineStatus.IGNITING, "Status is " + turbine.getStatus() + " while igniting");
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    GasTurbineArrayBlockEntity turbine = turbine(helper, min);
                    helper.assertTrue(turbine.getPhase() == GasTurbineArrayBlockEntity.Phase.IGNITING, "Ignition ended early");
                    helper.assertTrue(turbine.getFuel().getAmount() == fuelAtIgnition[0] && turbine.getFePerTick() == 0,
                            "Burned fuel or made FE while igniting");
                })
                .thenWaitUntil(() -> helper.assertTrue(turbine(helper, min).getPhase() == GasTurbineArrayBlockEntity.Phase.RUNNING, "Not running"))
                .thenIdle(32)
                .thenExecute(() -> {
                    GasTurbineArrayBlockEntity turbine = turbine(helper, min);
                    double share = turbine.getRpm() / GasTurbineArrayBlockEntity.maxRpm();
                    helper.assertTrue(share >= 0.95, "The rotor is at " + Math.round(share * 100) + "% after 32 ticks");
                    turbine.getFuel().set(0, FluidResource.EMPTY, 0);
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    double share = turbine(helper, min).getRpm() / GasTurbineArrayBlockEntity.maxRpm();
                    helper.assertTrue(share < 0.10, "The rotor is still at " + Math.round(share * 100) + "% after 80 ticks of coasting");
                })
                .thenSucceed();
    }
}
