/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.steam.TurbineLayout;

// The formed Steam Turbine Array's turbine-generator set (TurbineLayout): for every size it can be, the right casings in
// order, growing from high to low pressure and fitting the cross-section, the sections sharing the length, the
// generator at the far end, a walkway only on a cross-section wider than tall, and more and longer blade rows as the
// pressure drops; and on a formed turbine, the fitted port spots on the right faces, the sections Jade names, and ports
// set there working as before.
public final class TurbineModelGameTests {
    private TurbineModelGameTests() {}

    private static final float EPSILON = 0.01F;

    static void layoutSizes(GameTestHelper helper) {
        for (int length = 3; length <= 15; length++) {
            for (int width = 3; width <= 7; width++) {
                for (int height = 3; height <= 9; height++) {
                    for (boolean high : new boolean[] { false, true }) {
                        check(helper, TurbineLayout.of(length, width, height, high), length + "x" + width + "x" + height + (high ? " high" : " low"));
                    }
                }
            }
        }
        helper.succeed();
    }

    private static void check(GameTestHelper helper, TurbineLayout layout, String size) {
        int length = layout.length();
        float w16 = layout.widthPx(), h16 = layout.heightPx(), n16 = Math.min(w16, h16);
        List<TurbineLayout.Casing> casings = layout.casings();
        // The casings by length: one; high and low; high, intermediate and double-flow low.
        List<TurbineLayout.Part> parts = casings.stream().map(TurbineLayout.Casing::part).toList();
        List<TurbineLayout.Part> expected = length <= TurbineLayout.SINGLE_MAX ? List.of(TurbineLayout.Part.TURBINE)
                : length <= TurbineLayout.DOUBLE_MAX ? List.of(TurbineLayout.Part.HIGH_PRESSURE, TurbineLayout.Part.LOW_PRESSURE)
                : List.of(TurbineLayout.Part.HIGH_PRESSURE, TurbineLayout.Part.INTERMEDIATE, TurbineLayout.Part.LOW_PRESSURE);
        helper.assertTrue(parts.equals(expected), size + ": casings " + parts);
        helper.assertTrue(casings.get(casings.size() - 1).doubleFlow() == (length > TurbineLayout.DOUBLE_MAX), size + ": double flow");
        // Bigger toward low pressure; the low-pressure casing nearly fills the shorter side, the high-pressure about half.
        for (int i = 1; i < casings.size(); i++) {
            helper.assertTrue(casings.get(i).diameter() > casings.get(i - 1).diameter(), size + ": casing " + i + " isn't bigger");
            helper.assertTrue(casings.get(i).bladeRows() > casings.get(i - 1).bladeRows(), size + ": casing " + i + " hasn't more blade rows");
            float tip = casings.get(i).radius() * casings.get(i).tip(), before = casings.get(i - 1).radius() * casings.get(i - 1).tip();
            helper.assertTrue(tip > before, size + ": casing " + i + "'s blades aren't longer");
        }
        TurbineLayout.Casing last = casings.get(casings.size() - 1);
        helper.assertTrue(last.diameter() >= n16 * 0.6F && last.diameter() <= n16, size + ": low-pressure diameter " + last.diameter());
        if (casings.size() > 1) {
            helper.assertTrue(casings.get(0).diameter() <= n16 * 0.55F, size + ": high-pressure diameter " + casings.get(0).diameter());
        }
        // Everything inside the box: casings with their flanges, the crossover, the generator's terminal box, the chest.
        float sx = layout.shaftX(), sy = layout.shaftY();
        for (TurbineLayout.Casing casing : casings) {
            float r = casing.radius();
            helper.assertTrue(sy - r >= layout.deckTop() - EPSILON && sy + r <= h16 + EPSILON, size + ": " + casing.part() + " sticks out up or down");
            helper.assertTrue(sx - r - 2.0F >= -EPSILON && sx + r + 2.0F <= w16 + EPSILON, size + ": " + casing.part() + " sticks out sideways");
            helper.assertTrue(casing.window().length() > 0.0F, size + ": " + casing.part() + " has no window");
            helper.assertTrue(casing.span().length() >= TurbineLayout.MIN_CASING - EPSILON, size + ": " + casing.part() + " too short");
        }
        TurbineLayout.Crossover crossover = layout.crossover();
        helper.assertTrue((crossover != null) == (length > TurbineLayout.DOUBLE_MAX), size + ": crossover");
        if (crossover != null) {
            helper.assertTrue(crossover.runY() + crossover.diameter() / 2.0F + 0.5F <= h16 + EPSILON, size + ": crossover sticks out");
            helper.assertTrue(crossover.runY() - crossover.diameter() / 2.0F > sy + last.radius(), size + ": crossover runs through the low-pressure casing");
        }
        helper.assertTrue(layout.terminalBox().y1() <= h16 + EPSILON && layout.steamChest().y1() <= h16 + EPSILON, size + ": generator or chest sticks out");
        // Along: gaps and sections in order with no overlap, the generator last; the low-pressure casing the longest casing.
        float u = 0.0F;
        for (int i = 0; i < casings.size(); i++) {
            TurbineLayout.Span gap = layout.gaps().get(i);
            helper.assertTrue(Math.abs(gap.u0() - u) < EPSILON && Math.abs(casings.get(i).span().u0() - gap.u1()) < EPSILON, size + ": sections don't join");
            u = casings.get(i).span().u1();
        }
        TurbineLayout.Span lastGap = layout.gaps().get(layout.gaps().size() - 1);
        helper.assertTrue(Math.abs(lastGap.u0() - u) < EPSILON && Math.abs(layout.generator().u0() - lastGap.u1()) < EPSILON, size + ": generator doesn't join");
        helper.assertTrue(layout.generator().u1() <= layout.lengthPx() + EPSILON && layout.generator().length() >= TurbineLayout.MIN_GENERATOR - EPSILON,
                size + ": generator " + layout.generator());
        for (TurbineLayout.Casing casing : casings) {
            helper.assertTrue(casing.span().length() <= last.span().length() + EPSILON, size + ": " + casing.part() + " longer than the low-pressure casing");
        }
        // The walkway only on a cross-section wider than tall, beside the turbine.
        TurbineLayout.Walkway walkway = layout.walkway();
        helper.assertTrue((walkway != null) == (layout.width() > layout.height()), size + ": walkway");
        if (walkway != null) {
            boolean clear = walkway.high() ? walkway.x0() >= layout.regionX1() : walkway.x1() <= layout.regionX0();
            helper.assertTrue(clear && walkway.x0() >= 0.0F && walkway.x1() <= w16, size + ": walkway overlaps the turbine");
        }
        helper.assertTrue(layout.footingTop() >= TurbineLayout.FOOTING - EPSILON, size + ": no footings");
    }

    // A 3x3x10 along X: high, intermediate and low pressure; the fitted spots on its top, generator end and bottom; a new
    // turbine's ports on them, working as any port does; the parts Jade names along it.
    static void formedSpots(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        BigArrayGameTests.buildBox(helper, min, 10, 3, 3, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get(), Set.of());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(min, SteamTurbineArrayBlockEntity.class);
                    ShellStructure.Shell shell = turbine.getShell();
                    helper.assertTrue(turbine.isFormed() && shell != null && shell.axis() == Direction.Axis.X, "The turbine didn't form along X");
                    helper.assertTrue(MultiblockPorts.list(helper.getLevel(), turbine).size() == 3,
                            "A new turbine has " + MultiblockPorts.list(helper.getLevel(), turbine).size() + " ports, not 3");
                    TurbineLayout layout = TurbineLayout.of(shell, turbine.getFacing());
                    helper.assertTrue(layout.tier() == 3, "A 10-long turbine has " + layout.casings().size() + " casings");
                    helper.assertTrue(layout.partAt(1) == TurbineLayout.Part.HIGH_PRESSURE, "Block 1 is " + layout.partAt(1));
                    helper.assertTrue(layout.partAt(9) == TurbineLayout.Part.GENERATOR, "Block 9 is " + layout.partAt(9));
                    helper.assertTrue(layout.partAt(6) == TurbineLayout.Part.LOW_PRESSURE, "Block 6 is " + layout.partAt(6));

                    BlockPos steam = layout.steamSpot(shell), energy = layout.energySpot(shell), exhaust = layout.exhaustSpot(shell);
                    helper.assertTrue(shell.offset(steam, Direction.Axis.Y) == 2 && shell.contains(steam), "Steam spot not on the top: " + steam);
                    helper.assertTrue(shell.offset(exhaust, Direction.Axis.Y) == 0 && shell.contains(exhaust), "Exhaust spot not on the bottom: " + exhaust);
                    helper.assertTrue(shell.offset(energy, Direction.Axis.X) == 9 && shell.contains(energy), "Energy spot not on the generator end: " + energy);
                    helper.assertTrue(layout.spotAt(shell, steam) == TurbineLayout.Spot.STEAM_INLET && layout.spotAt(shell, energy) == TurbineLayout.Spot.GENERATOR
                            && layout.spotAt(shell, exhaust) == TurbineLayout.Spot.EXHAUST, "Spots aren't named");
                    helper.assertTrue(layout.partAt(TurbineLayout.blockU(shell, steam)) == TurbineLayout.Part.HIGH_PRESSURE, "The steam spot isn't over the high-pressure casing");
                    helper.assertTrue(layout.partAt(TurbineLayout.blockU(shell, exhaust)) == TurbineLayout.Part.LOW_PRESSURE, "The exhaust spot isn't under the low-pressure casing");

                    helper.assertTrue(MultiblockPorts.get(helper.getLevel(), steam, Direction.UP) == SideMode.INPUT, "No steam port at the steam spot");
                    helper.assertTrue(MultiblockPorts.get(helper.getLevel(), energy, Direction.EAST) == SideMode.ENERGY, "No energy port at the generator end");
                    helper.assertTrue(MultiblockPorts.get(helper.getLevel(), exhaust, Direction.DOWN) == SideMode.EXHAUST, "No Exhaust port under the exhaust neck");
                    helper.assertTrue(turbine.hasExhaust(), "The Exhaust port under the turbine doesn't count");
                })
                .thenSucceed();
    }
}
