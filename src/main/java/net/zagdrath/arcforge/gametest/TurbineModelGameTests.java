/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.steam.TurbineLayout;
import net.zagdrath.arcforge.steam.TurbineMesh;
import net.zagdrath.arcforge.steam.TurbineShapes;

// The formed Steam Turbine Array's turbine-generator set (TurbineLayout): for every size it can be, the right casings
// in order (one to three low-pressure casings on the longest), growing from high to low pressure and fitting the
// cross-section, the sections sharing the length, the generator at the far end, a walkway only on a cross-section wider
// than tall, more and longer blades as the pressure drops, a window in every casing with its glass inside the
// casing and the blade tips clear of the inner wall; its hitbox (TurbineShapes) a deck to walk on over the whole
// footprint and nothing in the core; and on a formed turbine, the fitted port spots on the right faces, the sections
// Jade names, ports set there working as before, and formed casings shaped to the model while loose ones are full
// blocks.
public final class TurbineModelGameTests {
    private TurbineModelGameTests() {}

    private static final float EPSILON = 0.01F;

    // The rotor is built once at rest and drawn turned (SteamTurbineArrayRenderer): turned about ModelMesh.rotorAxis, it's
    // the rotor built at that angle, every vertex where it would be, every normal turned with it and every UV the same.
    static void rotorTurns(GameTestHelper helper) {
        for (boolean alongX : new boolean[] { false, true }) {
            TurbineMesh mesh = new TurbineMesh(TurbineLayout.of(21, 7, 7, alongX), alongX);
            float[] rest = mesh.buildRotor(0.0F);
            float[] axis = mesh.rotorAxis();
            for (float angle : new float[] { 17.0F, 90.0F, 233.0F }) {
                float[] turned = mesh.buildRotor(angle);
                helper.assertTrue(turned.length == rest.length, "The rotor at " + angle + "° has " + turned.length + " floats, at rest " + rest.length);
                org.joml.Matrix4f turn = new org.joml.Matrix4f().translate(axis[0], axis[1], axis[2])
                        .rotate((float) Math.toRadians(angle), axis[3], axis[4], axis[5]).translate(-axis[0], -axis[1], -axis[2]);
                org.joml.Vector3f p = new org.joml.Vector3f(), n = new org.joml.Vector3f();
                for (int i = 0; i + 7 < rest.length; i += 8) {
                    turn.transformPosition(rest[i], rest[i + 1], rest[i + 2], p);
                    turn.transformDirection(rest[i + 5], rest[i + 6], rest[i + 7], n);
                    boolean same = Math.abs(p.x - turned[i]) < 1.0E-3F && Math.abs(p.y - turned[i + 1]) < 1.0E-3F && Math.abs(p.z - turned[i + 2]) < 1.0E-3F
                            && Math.abs(n.x - turned[i + 5]) < 1.0E-3F && Math.abs(n.y - turned[i + 6]) < 1.0E-3F && Math.abs(n.z - turned[i + 7]) < 1.0E-3F
                            && rest[i + 3] == turned[i + 3] && rest[i + 4] == turned[i + 4];
                    if (!same) {
                        helper.fail("Along " + (alongX ? "X" : "Z") + " at " + angle + "°, vertex " + i / 8 + " turned is (" + p.x + ", " + p.y + ", " + p.z
                                + "), built (" + turned[i] + ", " + turned[i + 1] + ", " + turned[i + 2] + ")");
                        return;
                    }
                }
            }
        }
        helper.succeed();
    }

    static void layoutSizes(GameTestHelper helper) {
        for (int length = 3; length <= SteamTurbineArrayCasingBlock.MAX_LENGTH; length++) {
            for (int width = 3; width <= SteamTurbineArrayCasingBlock.MAX_WIDTH; width++) {
                for (int height = 3; height <= SteamTurbineArrayCasingBlock.MAX_HEIGHT; height++) {
                    for (boolean high : new boolean[] { false, true }) {
                        TurbineLayout layout = TurbineLayout.of(length, width, height, high);
                        String size = length + "x" + width + "x" + height + (high ? " high" : " low");
                        check(helper, layout, size);
                        checkShapes(helper, layout, size);
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
        // The casings by length: one; high and low; high, intermediate and one to three double-flow low.
        List<TurbineLayout.Part> parts = casings.stream().map(TurbineLayout.Casing::part).toList();
        List<TurbineLayout.Part> expected = new ArrayList<>();
        if (length <= TurbineLayout.SINGLE_MAX) {
            expected.add(TurbineLayout.Part.TURBINE);
        } else if (length <= TurbineLayout.DOUBLE_MAX) {
            expected.addAll(List.of(TurbineLayout.Part.HIGH_PRESSURE, TurbineLayout.Part.LOW_PRESSURE));
        } else {
            helper.assertTrue(layout.lowCount() >= 1 && layout.lowCount() <= TurbineLayout.MAX_LOW_PRESSURE, size + ": " + layout.lowCount() + " low-pressure casings");
            expected.addAll(List.of(TurbineLayout.Part.HIGH_PRESSURE, TurbineLayout.Part.INTERMEDIATE));
            for (int i = 0; i < layout.lowCount(); i++) {
                expected.add(TurbineLayout.Part.LOW_PRESSURE);
            }
        }
        helper.assertTrue(parts.equals(expected), size + ": casings " + parts);
        for (TurbineLayout.Casing casing : casings) {
            helper.assertTrue(casing.doubleFlow() == (casing.part() == TurbineLayout.Part.LOW_PRESSURE && length > TurbineLayout.DOUBLE_MAX), size + ": double flow");
        }
        // Bigger toward low pressure (the low-pressure casings all alike); the low-pressure casing nearly fills the
        // shorter side, the high-pressure about half.
        for (int i = 1; i < casings.size(); i++) {
            TurbineLayout.Casing casing = casings.get(i), before = casings.get(i - 1);
            float tip = casing.radius() * casing.tip(), beforeTip = before.radius() * before.tip();
            if (casing.part() == before.part()) {
                helper.assertTrue(casing.diameter() == before.diameter() && casing.bladeRows() == before.bladeRows(), size + ": low-pressure casing " + i + " differs");
                continue;
            }
            helper.assertTrue(casing.diameter() > before.diameter(), size + ": casing " + i + " isn't bigger");
            helper.assertTrue(casing.blades() > before.blades(), size + ": casing " + i + " hasn't more blades a row");
            helper.assertTrue(tip > beforeTip, size + ": casing " + i + "'s blades aren't longer");
        }
        TurbineLayout.Casing last = casings.get(casings.size() - 1);
        helper.assertTrue(last.diameter() >= n16 * 0.6F && last.diameter() <= n16, size + ": low-pressure diameter " + last.diameter());
        if (casings.size() > 1) {
            helper.assertTrue(casings.get(0).diameter() <= n16 * 0.55F, size + ": high-pressure diameter " + casings.get(0).diameter());
        }
        // Windows: in every casing, each long enough, the glass between the casing's outside and its inner wall, and
        // the blade tips clear of the inner wall's flats.
        for (TurbineLayout.Casing casing : casings) {
            helper.assertTrue(!casing.windows().isEmpty(), size + ": " + casing.part() + " has no window");
            helper.assertTrue(casing.glassRadius() < casing.radius() && casing.glassRadius() > casing.innerRadius(), size + ": " + casing.part() + "'s glass isn't in its wall");
            float clear = casing.innerRadius() * TurbineLayout.CASING_APOTHEM - TurbineLayout.BLADE_CLEARANCE + EPSILON;
            for (TurbineLayout.Span window : casing.windows()) {
                helper.assertTrue(window.length() >= TurbineLayout.MIN_WINDOW - EPSILON, size + ": " + casing.part() + " has a window " + window.length() + " long");
                helper.assertTrue(casing.tipAt(window.u0()) <= clear && casing.tipAt(window.u1()) <= clear, size + ": " + casing.part() + "'s blades reach the wall");
            }
        }
        // Everything inside the box: casings with their flanges, the crossover, the generator's terminal box, the chest.
        float sx = layout.shaftX(), sy = layout.shaftY();
        for (TurbineLayout.Casing casing : casings) {
            float r = casing.radius();
            helper.assertTrue(sy - r >= layout.deckTop() - EPSILON && sy + r <= h16 + EPSILON, size + ": " + casing.part() + " sticks out up or down");
            helper.assertTrue(sx - r - 2.0F >= -EPSILON && sx + r + 2.0F <= w16 + EPSILON, size + ": " + casing.part() + " sticks out sideways");
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

    // The hitbox: a deck to stand on over the whole footprint (from whichever blocks' shapes hold it); every shell
    // block with its boxes (none reaching more than a block past its own); nothing for the core's blocks.
    private static void checkShapes(GameTestHelper helper, TurbineLayout layout, String size) {
        TurbineShapes shapes = TurbineShapes.of(layout);
        int deck = Math.round(layout.deckTop());
        // The highest solid under the middle of each block's footprint, up to the deck.
        int[][] top = new int[layout.length()][layout.width()];
        for (int u = 0; u < layout.length(); u++) {
            for (int y = 0; y < layout.height(); y++) {
                for (int x = 0; x < layout.width(); x++) {
                    int[][] boxes = shapes.at(u, y, x);
                    boolean core = u > 0 && u < layout.length() - 1 && y > 0 && y < layout.height() - 1 && x > 0 && x < layout.width() - 1;
                    helper.assertTrue((boxes == null) == core, size + ": block " + u + "," + y + "," + x + (core ? " in the core has a shape" : " has no shape"));
                    for (int[] box : boxes != null ? boxes : new int[0][]) {
                        boolean inside = true;
                        for (int k = 0; k < 6; k++) {
                            inside &= box[k] >= -16 && box[k] <= 32;
                        }
                        helper.assertTrue(inside && box[0] < box[3] && box[1] < box[4] && box[2] < box[5], size + ": a bad box in block " + u + "," + y + "," + x);
                        int x0 = x * 16 + box[0], x1 = x * 16 + box[3], u0 = u * 16 + box[2], u1 = u * 16 + box[5], y1 = y * 16 + box[4];
                        if (y * 16 + box[1] > deck) {
                            continue;
                        }
                        for (int bu = 0; bu < layout.length(); bu++) {
                            for (int bx = 0; bx < layout.width(); bx++) {
                                int mu = bu * 16 + 8, mx = bx * 16 + 8;
                                if (u0 <= mu && u1 > mu && x0 <= mx && x1 > mx) {
                                    top[bu][bx] = Math.max(top[bu][bx], Math.min(y1, deck));
                                }
                            }
                        }
                    }
                }
            }
        }
        for (int u = 0; u < layout.length(); u++) {
            for (int x = 0; x < layout.width(); x++) {
                helper.assertTrue(top[u][x] >= deck, size + ": no deck under block " + u + "," + x);
            }
        }
    }

    // A 3x3x10 along X: high, intermediate and low pressure; the fitted spots on its top, generator end and bottom; a new
    // turbine's ports on them, working as any port does; the parts Jade names along it; its casings' shapes.
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

                    // The hitbox: formed casings shaped to the model (some neither full nor empty, none reaching more than a
                    // block past their own, a deck under the middle of the bottom layer), a loose casing a full block.
                    int shaped = 0;
                    for (BlockPos pos : shell.positions()) {
                        if (shell.isCore(pos)) {
                            continue;
                        }
                        VoxelShape shape = helper.getLevel().getBlockState(pos).getCollisionShape(helper.getLevel(), pos);
                        if (!shape.isEmpty() && !Block.isShapeFullBlock(shape)) {
                            shaped++;
                            helper.assertTrue(shape.bounds().minY >= -1.0 && shape.bounds().maxY <= 2.0, "A casing's shape reaches too far: " + pos);
                        }
                    }
                    helper.assertTrue(shaped > 0, "No formed casing is shaped to the model");
                    BlockPos bottom = shell.min().offset(4, 0, 1);
                    VoxelShape deck = helper.getLevel().getBlockState(bottom).getCollisionShape(helper.getLevel(), bottom);
                    helper.assertTrue(!deck.isEmpty() && deck.max(Direction.Axis.Y) >= layout.deckTop() / 16.0 - 0.01, "No deck to stand on at " + bottom);
                    VoxelShape loose = ModBlocks.STEAM_TURBINE_ARRAY_CASING.get().defaultBlockState().getCollisionShape(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 5)));
                    helper.assertTrue(Block.isShapeFullBlock(loose), "A loose casing isn't a full block");
                })
                .thenSucceed();
    }
}
