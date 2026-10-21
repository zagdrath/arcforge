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
import net.zagdrath.arcforge.blockentity.multiblock.NuclearSteamTurbineBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.steam.NuclearTurbineLayout;
import net.zagdrath.arcforge.steam.NuclearTurbineMesh;
import net.zagdrath.arcforge.steam.SteamGrade;

// The Nuclear Steam Turbine: a 7x7x21 of Nuclear Turbine Casings forms (a 7x7x20 doesn't), sized by its sections as the
// Steam Turbine Array is (114.3: 27,440 mB/t rated, 6,860 the least it runs on), with its fitted ports (steam under both
// steam chests, energy at the terminal box, an Exhaust port under each low-pressure casing); it takes High-Pressure and
// Contaminated Steam but not plain or Superheated; fed its rated flow it rolls up to its synchronous speed and makes 40 FE
// a mB, scaled while it rolls up; with the steam cut it trips and coasts down, and it rolls up again only once the steam
// coming in has held over its minimum. Its model's rotor is built about the shaft it turns on.
public final class NuclearTurbineGameTests {
    private NuclearTurbineGameTests() {}

    // A turbine length long along X from (0, 1, 0).
    private static BlockPos build(GameTestHelper helper, int length) {
        BlockPos min = new BlockPos(0, 1, 0);
        BigArrayGameTests.buildBox(helper, min, length, NuclearTurbineLayout.HEIGHT, NuclearTurbineLayout.WIDTH, ModBlocks.NUCLEAR_TURBINE_CASING.get(), Set.of());
        return min;
    }

    private static NuclearSteamTurbineBlockEntity turbine(GameTestHelper helper, BlockPos min) {
        return helper.getBlockEntity(min, NuclearSteamTurbineBlockEntity.class);
    }

    private static int insert(NuclearSteamTurbineBlockEntity turbine, FluidResource steam, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = turbine.getSteam().insert(0, steam, amount, tx);
            tx.commit();
            return inserted;
        }
    }

    // The longest forms (a 7x7x45, past the 28 blocks the shell search used to reach), one longer doesn't.
    static void formsLongest(GameTestHelper helper) {
        BlockPos min = build(helper, NuclearTurbineLayout.MAX_LENGTH);
        BlockPos longMin = new BlockPos(0, 1, 9);
        BigArrayGameTests.buildBox(helper, longMin, NuclearTurbineLayout.MAX_LENGTH + 1, NuclearTurbineLayout.HEIGHT, NuclearTurbineLayout.WIDTH,
                ModBlocks.NUCLEAR_TURBINE_CASING.get(), Set.of());
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    NuclearSteamTurbineBlockEntity turbine = turbine(helper, min);
                    helper.assertTrue(turbine.isMaster() && turbine.getShell() != null && turbine.getShell().length() == NuclearTurbineLayout.MAX_LENGTH,
                            "The 7x7x45 didn't form");
                    helper.assertTrue(turbine.maxFlow() == 58_800, "Rated " + turbine.maxFlow());
                    helper.assertFalse(turbine(helper, longMin).isMaster(), "A 7x7x46 formed");
                })
                .thenSucceed();
    }

    static void forms(GameTestHelper helper) {
        BlockPos min = build(helper, NuclearTurbineLayout.MIN_LENGTH);
        BlockPos shortMin = new BlockPos(0, 1, 9);
        BigArrayGameTests.buildBox(helper, shortMin, NuclearTurbineLayout.MIN_LENGTH - 1, NuclearTurbineLayout.HEIGHT, NuclearTurbineLayout.WIDTH,
                ModBlocks.NUCLEAR_TURBINE_CASING.get(), Set.of());
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    NuclearSteamTurbineBlockEntity turbine = turbine(helper, min);
                    helper.assertTrue(turbine.isMaster() && turbine.getShell() != null && turbine.getShell().axis() == Direction.Axis.X,
                            "The 7x7x21 didn't form along X");
                    helper.assertFalse(turbine(helper, shortMin).isMaster(), "A 7x7x20 formed");
                    helper.assertTrue(turbine.maxFlow() == 27_440 && turbine.minFlow() == 6_860, "Rated " + turbine.maxFlow() + ", least " + turbine.minFlow());
                    helper.assertTrue(turbine.getEnergy().getCapacityAsInt() == turbine.maxOutput() * ArcforgeConfig.NUCLEAR_TURBINE_ENERGY_BUFFER_TICKS.getAsInt(),
                            "FE buffer " + turbine.getEnergy().getCapacityAsInt());
                    // Its fitted ports, given on its first ticks.
                    Map<SideMode, Long> ports = MultiblockPorts.list(helper.getLevel(), turbine).stream()
                            .collect(Collectors.groupingBy(port -> port.mode(), Collectors.counting()));
                    helper.assertTrue(ports.getOrDefault(SideMode.INPUT, 0L) == 2 && ports.getOrDefault(SideMode.ENERGY, 0L) == 1
                            && ports.getOrDefault(SideMode.EXHAUST, 0L) == NuclearTurbineLayout.MIN_LENGTH / 9, "Its ports: " + ports);
                    helper.assertTrue(turbine.hasExhaust(), "It didn't notice its Exhaust ports");
                    // The steam it takes.
                    helper.assertTrue(insert(turbine, SteamGrade.SUPERHEATED.resource(), 1_000) == 0, "It took Superheated Steam");
                    helper.assertTrue(insert(turbine, SteamGrade.STEAM.resource(), 1_000) == 0, "It took plain Steam");
                    helper.assertTrue(insert(turbine, FluidResource.of(ModFluids.CONTAMINATED_STEAM.get()), 1_000) == 1_000, "It refused Contaminated Steam");
                    turbine.getSteam().set(0, FluidResource.EMPTY, 0);
                    helper.assertTrue(insert(turbine, SteamGrade.HIGH_PRESSURE.resource(), 1_000) == 1_000, "It refused High-Pressure Steam");
                    turbine.getSteam().set(0, FluidResource.EMPTY, 0);
                })
                .thenSucceed();
    }

    static void runsAndTrips(GameTestHelper helper) {
        BlockPos min = build(helper, NuclearTurbineLayout.MIN_LENGTH);
        FluidResource steam = SteamGrade.HIGH_PRESSURE.resource();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(turbine(helper, min).isMaster(), "It didn't form"))
                // Its rated flow, for about three time constants of rolling up.
                .thenExecuteFor(250, () -> insert(turbine(helper, min), steam, turbine(helper, min).maxFlow()))
                .thenExecute(() -> {
                    NuclearSteamTurbineBlockEntity turbine = turbine(helper, min);
                    int max = NuclearSteamTurbineBlockEntity.maxRpm();
                    helper.assertTrue(turbine.getFlow() == turbine.maxFlow(), "It takes " + turbine.getFlow() + " of " + turbine.maxFlow() + " mB/t");
                    helper.assertTrue(turbine.getRpm() > 0.85 * max && turbine.getRpm() <= max, "It's at " + turbine.getRpm() + " of " + max + " RPM");
                    double expected = turbine.getFlow() * ArcforgeConfig.NUCLEAR_TURBINE_FE_PER_MB.getAsDouble() * ArcforgeConfig.POWER_GENERATION_MULTIPLIER.getAsDouble()
                            * turbine.getRpm() / max;
                    helper.assertTrue(Math.abs(turbine.getFePerTick() - expected) < 0.02 * expected, "It makes " + turbine.getFePerTick() + " FE/t, not " + expected);
                    helper.assertTrue(!turbine.isTripped(), "It tripped at its rated flow");
                })
                // The steam cut: under the minimum for tripSeconds, it trips and coasts.
                .thenIdle(ArcforgeConfig.NUCLEAR_TURBINE_TRIP_SECONDS.getAsInt() * 20 + 20)
                .thenExecute(() -> {
                    NuclearSteamTurbineBlockEntity turbine = turbine(helper, min);
                    helper.assertTrue(turbine.isTripped() && turbine.getStatus() == MachineStatus.TRIPPED, "No trip: " + turbine.getStatus());
                    helper.assertTrue(turbine.getRpm() > 1.0 && turbine.getFePerTick() == 0, "It isn't coasting: " + turbine.getRpm() + " RPM, "
                            + turbine.getFePerTick() + " FE/t");
                })
                // Half its rated flow comes back: over its minimum, so once that has held it rolls up again.
                .thenExecuteFor(ArcforgeConfig.NUCLEAR_TURBINE_RESTART_SECONDS.getAsInt() * 20 + 60,
                        () -> insert(turbine(helper, min), steam, turbine(helper, min).maxFlow() / 2))
                .thenExecute(() -> {
                    NuclearSteamTurbineBlockEntity turbine = turbine(helper, min);
                    helper.assertTrue(!turbine.isTripped() && turbine.getFlow() > 0, "It didn't restart: " + turbine.getStatus() + ", steam coming in "
                            + turbine.getInflow());
                })
                .thenSucceed();
    }

    // The rotor and blades are built about the shaft their renderer turns them on (ModelMesh.rotorAxis): across it, the
    // rotor's box is centred on the axis, whichever way the turbine stands.
    static void rotorOnShaft(GameTestHelper helper) {
        for (boolean alongX : new boolean[] { false, true }) {
            for (boolean frontHigh : new boolean[] { false, true }) {
                NuclearTurbineMesh mesh = new NuclearTurbineMesh(NuclearTurbineLayout.of(27, frontHigh), alongX);
                float[] axis = mesh.rotorAxis();
                for (float[] part : new float[][] { mesh.buildRotor(), mesh.buildBlades(false), mesh.buildBlades(true) }) {
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
                            helper.fail((alongX ? "Along X" : "Along Z") + (frontHigh ? ", front high" : ", front low") + ": a turning part's middle is "
                                    + middle + " on axis " + k + ", the shaft at " + axis[k]);
                            return;
                        }
                    }
                }
            }
        }
        helper.succeed();
    }
}
