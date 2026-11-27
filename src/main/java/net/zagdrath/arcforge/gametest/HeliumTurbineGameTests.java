/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.blockentity.multiblock.HeliumGasTurbineBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.steam.HeliumTurbineLayout;
import net.zagdrath.arcforge.steam.HeliumTurbineMesh;
import net.zagdrath.arcforge.steam.NuclearTurbineShapes;
import net.zagdrath.arcforge.steam.SteamGrade;

// The Helium Gas Turbine: a 5x5x15 of Helium Turbine Casings forms (a 5x5x14 doesn't), and a 5x5x31 (not a 5x5x32), sized
// by its sections as the Steam Turbine Array is (41.7: 4,833 mB/t rated; 86.1: 9,989), with its fitted ports (Hot Helium
// under each power turbine's inlet duct, the Helium outlet under the compressor's discharge, energy at the terminal box);
// it takes Hot Helium only; fed its rated flow it rolls up to its synchronous speed and makes 800 FE a mB, every mB
// coming back out as Helium; with nowhere for that Helium to go it holds. Its collision shape keeps players out of its
// model, and its rotor is built about the shaft it turns on.
public final class HeliumTurbineGameTests {
    private HeliumTurbineGameTests() {}

    private static BlockPos build(GameTestHelper helper, BlockPos min, int length) {
        BigArrayGameTests.buildBox(helper, min, length, HeliumTurbineLayout.HEIGHT, HeliumTurbineLayout.WIDTH, ModBlocks.HELIUM_TURBINE_CASING.get(), Set.of());
        return min;
    }

    private static HeliumGasTurbineBlockEntity turbine(GameTestHelper helper, BlockPos min) {
        return helper.getBlockEntity(min, HeliumGasTurbineBlockEntity.class);
    }

    private static int insert(HeliumGasTurbineBlockEntity turbine, FluidResource gas, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = turbine.getHotHelium().insert(0, gas, amount, tx);
            tx.commit();
            return inserted;
        }
    }

    static void forms(GameTestHelper helper) {
        BlockPos min = build(helper, new BlockPos(0, 1, 0), HeliumTurbineLayout.MIN_LENGTH);
        BlockPos shortMin = build(helper, new BlockPos(0, 1, 7), HeliumTurbineLayout.MIN_LENGTH - 1);
        BlockPos longMin = build(helper, new BlockPos(0, 1, 14), HeliumTurbineLayout.MAX_LENGTH);
        BlockPos tooLong = build(helper, new BlockPos(0, 1, 21), HeliumTurbineLayout.MAX_LENGTH + 1);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    HeliumGasTurbineBlockEntity turbine = turbine(helper, min);
                    helper.assertTrue(turbine.isMaster() && turbine.getShell() != null && turbine.getShell().axis() == Direction.Axis.X,
                            "The 5x5x15 didn't form along X");
                    helper.assertFalse(turbine(helper, shortMin).isMaster(), "A 5x5x14 formed");
                    helper.assertTrue(turbine(helper, longMin).isMaster(), "The 5x5x31 didn't form");
                    helper.assertFalse(turbine(helper, tooLong).isMaster(), "A 5x5x32 formed");
                    helper.assertTrue(turbine.maxFlow() == 4_833, "Rated " + turbine.maxFlow());
                    helper.assertTrue(turbine(helper, longMin).maxFlow() == 9_989, "The 5x5x31 rated " + turbine(helper, longMin).maxFlow());
                    helper.assertTrue(turbine.getEnergy().getCapacityAsInt() == turbine.maxOutput() * ArcforgeConfig.HELIUM_TURBINE_ENERGY_BUFFER_TICKS.getAsInt(),
                            "FE buffer " + turbine.getEnergy().getCapacityAsInt());
                    Map<SideMode, Long> ports = MultiblockPorts.list(helper.getLevel(), turbine).stream()
                            .collect(Collectors.groupingBy(port -> port.mode(), Collectors.counting()));
                    helper.assertTrue(ports.getOrDefault(SideMode.INPUT, 0L) == HeliumTurbineLayout.MIN_LENGTH / 8 && ports.getOrDefault(SideMode.OUTPUT, 0L) == 1
                            && ports.getOrDefault(SideMode.ENERGY, 0L) == 1, "Its ports: " + ports);
                    helper.assertTrue(insert(turbine, FluidResource.of(ModFluids.HELIUM.get()), 1_000) == 0, "It took cold Helium");
                    helper.assertTrue(insert(turbine, SteamGrade.HIGH_PRESSURE.resource(), 1_000) == 0, "It took steam");
                    helper.assertTrue(insert(turbine, FluidResource.of(ModFluids.HOT_HELIUM.get()), 1_000) == 1_000, "It refused Hot Helium");
                    turbine.getHotHelium().set(0, FluidResource.EMPTY, 0);
                })
                .thenSucceed();
    }

    static void runsAndReturnsHelium(GameTestHelper helper) {
        BlockPos min = build(helper, new BlockPos(0, 1, 0), HeliumTurbineLayout.MIN_LENGTH);
        FluidResource gas = FluidResource.of(ModFluids.HOT_HELIUM.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(turbine(helper, min).isMaster(), "It didn't form"))
                // Its rated flow, the Helium it gives back taken away each tick, for about three time constants of rolling up.
                .thenExecuteFor(250, () -> {
                    HeliumGasTurbineBlockEntity turbine = turbine(helper, min);
                    turbine.getHelium().set(0, FluidResource.EMPTY, 0);
                    insert(turbine, gas, turbine.maxFlow());
                })
                .thenExecute(() -> {
                    HeliumGasTurbineBlockEntity turbine = turbine(helper, min);
                    int max = HeliumGasTurbineBlockEntity.maxRpm();
                    helper.assertTrue(turbine.getFlow() == turbine.maxFlow(), "It takes " + turbine.getFlow() + " of " + turbine.maxFlow() + " mB/t");
                    helper.assertTrue(turbine.getHelium().getAmount() >= turbine.getFlow(), "It gave back " + turbine.getHelium().getAmount() + " mB of Helium");
                    helper.assertTrue(turbine.getRpm() > 0.85 * max && turbine.getRpm() <= max, "It's at " + turbine.getRpm() + " of " + max + " RPM");
                    double expected = turbine.getFlow() * ArcforgeConfig.HELIUM_TURBINE_FE_PER_MB.getAsDouble() * ArcforgeConfig.POWER_GENERATION_MULTIPLIER.getAsDouble()
                            * turbine.getRpm() / max;
                    helper.assertTrue(Math.abs(turbine.getFePerTick() - expected) < 0.02 * expected, "It makes " + turbine.getFePerTick() + " FE/t, not " + expected);
                })
                // Nowhere for its Helium to go: its Helium side fills and it holds.
                .thenExecuteFor(40, () -> insert(turbine(helper, min), gas, turbine(helper, min).maxFlow()))
                .thenExecute(() -> {
                    HeliumGasTurbineBlockEntity turbine = turbine(helper, min);
                    helper.assertTrue(turbine.getHelium().getSpace() == 0 && turbine.getFlow() == 0, "It runs with its Helium side full: " + turbine.getFlow());
                    helper.assertTrue(turbine.getStatus() == MachineStatus.OUTPUT_FULL, "Status " + turbine.getStatus());
                })
                .thenSucceed();
    }

    // Its collision shape keeps a player out of every part of the model (see NuclearTurbineGameTests.hitboxKeepsPlayersOut).
    static void hitboxKeepsPlayersOut(GameTestHelper helper) {
        for (int length : new int[] { HeliumTurbineLayout.MIN_LENGTH, 23, HeliumTurbineLayout.MAX_LENGTH }) {
            for (boolean frontHigh : new boolean[] { false, true }) {
                NuclearTurbineShapes shapes = NuclearTurbineShapes.of(HeliumTurbineLayout.of(length, frontHigh));
                int gaps = shapes.headroomGaps();
                helper.assertTrue(gaps == 0, "A 5x5x" + length + (frontHigh ? " (front high)" : "") + " can be walked into at " + gaps + " pixel columns: "
                        + shapes.lastGaps);
            }
        }
        helper.succeed();
    }

    // The rotor is built about the shaft its renderer turns it on (ModelMesh.rotorAxis), whichever way the turbine stands.
    static void rotorOnShaft(GameTestHelper helper) {
        for (boolean alongX : new boolean[] { false, true }) {
            for (boolean frontHigh : new boolean[] { false, true }) {
                HeliumTurbineMesh mesh = new HeliumTurbineMesh(HeliumTurbineLayout.of(23, frontHigh), alongX);
                float[] axis = mesh.rotorAxis();
                float[] part = mesh.buildRotor();
                float[] lo = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE }, hi = { -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
                for (int i = 0; i + 7 < part.length; i += 8) {
                    for (int k = 0; k < 3; k++) {
                        lo[k] = Math.min(lo[k], part[i + k]);
                        hi[k] = Math.max(hi[k], part[i + k]);
                    }
                }
                for (int k = 0; k < 3; k++) {
                    boolean along = Math.abs(axis[3 + k]) > 0.5F;
                    float middle = (lo[k] + hi[k]) / 2.0F;
                    if (!along && Math.abs(middle - axis[k]) > 0.02F) {
                        helper.fail((alongX ? "Along X" : "Along Z") + (frontHigh ? ", front high" : ", front low") + ": the rotor's middle is " + middle
                                + " on axis " + k + ", the shaft at " + axis[k]);
                        return;
                    }
                }
            }
        }
        helper.succeed();
    }
}
