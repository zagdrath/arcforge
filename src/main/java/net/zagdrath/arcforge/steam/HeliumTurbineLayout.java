/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.zagdrath.arcforge.multiblock.ShellStructure;

// How a formed Helium Gas Turbine is laid out as one closed-cycle (Brayton) gas turbine-generator set, drawn by its
// renderer from HeliumTurbineMesh, named by Jade and given its block shapes by NuclearTurbineShapes, in the Nuclear Steam
// Turbine's manner (NuclearTurbineLayout) but smaller and its own machine. Everything is in pixels in the turbine's own
// frame: u along the shaft from the front standard (0) to the exciter (length x 16), x across, y up. The cross-section is
// always 5 x 5.
//
// It's laid out for the front (the walkway and the staircase) at high x and mirrored across when the front is at low x:
// the canonical positions below go through x(...).
// - A raised deck (top at DECK_TOP) on columns, with the walkway along its front edge, a handrail, and a short staircase
//   up to it at the front standard's end.
// - Along the shaft (SHAFT_X, SHAFT_Y): the front standard and the turning-gear wheel; the compressor's intake plenum (a
//   square box) and the compressor (a long drum narrowing stage by stage, a flange at every stage); one power turbine
//   per 8 blocks of length (round casings flaring out toward their exhaust ends, each fed Hot Helium at its inlet ring
//   by an insulated duct rising through the deck from the front); the generator (a stator with domed end shields, one
//   cooler on top, the terminal box under its near end), the slip-ring housing and the exciter.
// - A bearing pedestal pair and a coupling in the gap between every two casings.
// - The recuperator: one long vessel lying on saddles along the back of the compressor and turbines, a crossover duct
//   arching over into it from the top of each power turbine's exhaust end; the precooler: a finned box along the front of
//   the compressor beside its plenum, water headers on top.
// - Fitted port spots, all on the bottom face: a Hot Helium inlet under each inlet duct, the Helium outlet under the
//   compressor's discharge pipe (the cooled gas back to the reactor), and the cable bus's foot (energy).
public final class HeliumTurbineLayout {
    public static final int WIDTH = 5, HEIGHT = 5, MIN_LENGTH = 15, MAX_LENGTH = 31;
    public static final float PX = 16.0F, WIDTH_PX = WIDTH * PX, HEIGHT_PX = HEIGHT * PX;
    // The deck slab, and the walkway's inner edge (canonical x) from there to the front.
    public static final float DECK_BOTTOM = 18.0F, DECK_TOP = 22.0F, WALKWAY_X = 69.0F;
    // The shaft (canonical x).
    public static final float SHAFT_X = 34.0F, SHAFT_Y = 40.0F, SHAFT_RADIUS = 2.5F;
    // The gap between two casings: a bearing pedestal, a coupling, a pedestal.
    public static final float GAP = 9.0F, PEDESTAL = 2.5F;
    // The staircase runs from the ground at STAIR_U0 up to the deck at STAIR_U1 along the front strip.
    public static final float STAIR_U0 = 2.0F, STAIR_U1 = 26.0F;
    // The front standard, and the turning-gear wheel behind it.
    public static final float STANDARD_U0 = 3.0F, STANDARD_U1 = 16.0F, WHEEL_U0 = 17.0F, WHEEL_U1 = 19.5F, WHEEL_RADIUS = 6.5F;
    // The compressor's intake plenum (canonical x, u, y) and the drum's radius at its intake and discharge ends.
    public static final float PLENUM_X0 = 21.0F, PLENUM_X1 = 50.0F, PLENUM_U0 = 22.0F, PLENUM_U1 = 31.0F, PLENUM_TOP = 54.0F;
    public static final float COMPRESSOR_IN = 10.0F, COMPRESSOR_OUT = 7.0F, STAGE = 6.0F;
    // The power turbines: their inlet and exhaust radii, the inlet ring, and the exhaust neck on top.
    public static final float TURBINE_IN = 9.0F, TURBINE_OUT = 12.0F, RING_RADIUS = 12.5F;
    // The precooler (canonical x and its top) along the front of the compressor.
    public static final float PRECOOLER_X0 = 50.0F, PRECOOLER_X1 = 66.0F, PRECOOLER_TOP = 50.0F;
    // The recuperator (canonical x), its axis height and radius.
    public static final float RECUPERATOR_X = 12.0F, RECUPERATOR_Y = 34.0F, RECUPERATOR_RADIUS = 9.0F;
    // The crossover ducts: radius and the arches' crown.
    public static final float CROSSOVER_RADIUS = 3.8F, CROWN_Y = 72.0F;
    // The Hot Helium inlet ducts (canonical x of the riser) and their radius.
    public static final float INLET_X = 58.0F, INLET_RADIUS = 3.2F, INLET_Y = 40.0F;
    // The generator's stator.
    public static final float STATOR_RADIUS = 13.5F, STATOR_END_RADIUS = 8.5F, SHIELD = 4.5F;
    // The cable bus under the terminal box (canonical x: the middle block's centre, so mirroring keeps it there).
    public static final float BUS_X = 40.0F, BUS_HALF = 3.5F;

    public enum Part {
        FRONT_STANDARD, COMPRESSOR, PRECOOLER, RECUPERATOR, POWER_TURBINE, GENERATOR, EXCITER, BEARING, DECK;

        public String translationKey() {
            return "arcforge.helium_gas_turbine.part." + name().toLowerCase(Locale.ROOT);
        }
    }

    // The fitted port spots.
    public enum Spot {
        NONE, HOT_INLET, GENERATOR, OUTLET;

        public String translationKey() {
            return "jade.arcforge.helium_turbine.spot." + name().toLowerCase(Locale.ROOT);
        }
    }

    // A stretch along the axis.
    public record Span(float u0, float u1) {
        public float length() {
            return u1 - u0;
        }

        public float middle() {
            return (u0 + u1) / 2.0F;
        }

        public boolean contains(float u) {
            return u >= u0 && u < u1;
        }
    }

    private final int length;
    private final boolean frontHigh;
    private final float lengthPx;
    private final Span compressor, generator, slipRing, exciter, recuperator, precooler;
    private final List<Span> turbines = new ArrayList<>(), gaps = new ArrayList<>();
    // Each power turbine's inlet duct u (on a block's middle, so its port sits under it).
    private final List<Float> inletU = new ArrayList<>();
    private final float dischargeU, busU;

    private HeliumTurbineLayout(int length, boolean frontHigh) {
        this.length = Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
        this.frontHigh = frontHigh;
        this.lengthPx = this.length * PX;
        int n = this.length - MIN_LENGTH;
        compressor = new Span(PLENUM_U1, PLENUM_U1 + 34.0F + n * 1.5F);
        float generatorLength = 40.0F + n * 1.2F;
        exciter = new Span(lengthPx - 14.0F, lengthPx - 2.0F);
        slipRing = new Span(lengthPx - 22.0F, exciter.u0());
        generator = new Span(slipRing.u0() - 1.5F - generatorLength, slipRing.u0() - 1.5F);
        // The power turbines share what's left between the compressor and the generator.
        int count = turbineCount();
        float from = compressor.u1() + GAP, to = generator.u0() - GAP;
        float each = (to - from - (count - 1) * GAP) / count;
        gaps.add(new Span(compressor.u1(), from));
        for (int i = 0; i < count; i++) {
            float u0 = from + i * (each + GAP);
            Span turbine = new Span(u0, u0 + each);
            turbines.add(turbine);
            gaps.add(new Span(u0 + each, u0 + each + GAP));
            inletU.add(blockMiddle(u0 + 6.0F));
        }
        // The recuperator along the compressor and turbines' back, from the plenum to the last turbine's exhaust end.
        recuperator = new Span(PLENUM_U0 + 4.0F, turbines.get(count - 1).u1() - 2.0F);
        precooler = new Span(PLENUM_U0, compressor.u1() - 3.0F);
        dischargeU = blockMiddle(compressor.u1() - 6.0F);
        busU = blockMiddle(generator.u0() + 10.0F);
    }

    // The middle of the block holding u.
    private static float blockMiddle(float u) {
        return (float) Math.floor(u / PX) * PX + 8.0F;
    }

    // --- Making one ---

    public static HeliumTurbineLayout of(int length, boolean frontHigh) {
        return new HeliumTurbineLayout(length, frontHigh);
    }

    // The layout of a formed shell whose front (the side with the walkway) faces front.
    public static HeliumTurbineLayout of(ShellStructure.Shell shell, Direction front) {
        return new HeliumTurbineLayout(shell.length(), TurbineLayout.frontIsHigh(shell.axis(), front));
    }

    // --- The frame ---

    // A canonical x (the front at high x) in this layout's frame (its own inverse).
    public float x(float canonical) {
        return frontHigh ? canonical : WIDTH_PX - canonical;
    }

    public float canonical(float x) {
        return x(x);
    }

    public boolean frontHigh() {
        return frontHigh;
    }

    public int length() {
        return length;
    }

    public float lengthPx() {
        return lengthPx;
    }

    public float shaftX() {
        return x(SHAFT_X);
    }

    public float shaftY() {
        return SHAFT_Y;
    }

    public float shaftRadius() {
        return SHAFT_RADIUS;
    }

    // One per 8 blocks of length (15 -> 1, 16 -> 2, 24 -> 3, 31 -> 3).
    public int turbineCount() {
        return length / 8;
    }

    public Span compressor() {
        return compressor;
    }

    public List<Span> turbines() {
        return turbines;
    }

    // The gaps between casings, from the compressor's to the generator's (a pedestal pair and a coupling in each).
    public List<Span> gaps() {
        return gaps;
    }

    public Span generator() {
        return generator;
    }

    public Span slipRing() {
        return slipRing;
    }

    public Span exciter() {
        return exciter;
    }

    public Span recuperator() {
        return recuperator;
    }

    public Span precooler() {
        return precooler;
    }

    public List<Float> inletU() {
        return inletU;
    }

    public float dischargeU() {
        return dischargeU;
    }

    public float busU() {
        return busU;
    }

    // The terminal box under the stator's near end, round the cable bus.
    public Span terminalBox() {
        return new Span(busU - 7.0F, busU + 7.0F);
    }

    // --- Blocks ---

    // The part a block of the shell shows (for Jade).
    public Part partAt(ShellStructure.Shell shell, BlockPos pos) {
        int bu = TurbineLayout.blockU(shell, pos), bx = TurbineLayout.blockX(shell, pos), by = shell.offset(pos, Direction.Axis.Y);
        float u = (bu + 0.5F) * PX, cx = canonical((bx + 0.5F) * PX), y = (by + 0.5F) * PX;
        if (y < DECK_TOP || cx >= WALKWAY_X) {
            return Part.DECK;
        }
        if (recuperator.contains(u) && cx < SHAFT_X - 14.0F) {
            return Part.RECUPERATOR;
        }
        if (precooler.contains(u) && cx >= PRECOOLER_X0 && y < PRECOOLER_TOP + 4.0F) {
            return Part.PRECOOLER;
        }
        if (u < STANDARD_U1 + 4.0F) {
            return Part.FRONT_STANDARD;
        }
        if (u < compressor.u1()) {
            return Part.COMPRESSOR;
        }
        for (Span turbine : turbines) {
            if (turbine.contains(u)) {
                return Part.POWER_TURBINE;
            }
        }
        if (generator.contains(u)) {
            return Part.GENERATOR;
        }
        if (u >= slipRing.u0()) {
            return Part.EXCITER;
        }
        return Part.BEARING;
    }

    // Which power turbine a block's slice is in (1 from the compressor's end), or 0.
    public int turbineNumberAt(int blockU) {
        float u = (blockU + 0.5F) * PX;
        for (int i = 0; i < turbines.size(); i++) {
            if (turbines.get(i).contains(u)) {
                return i + 1;
            }
        }
        return 0;
    }

    // The fitted spot a block of the shell is, if any.
    public Spot spotAt(ShellStructure.Shell shell, BlockPos pos) {
        if (inletSpots(shell).contains(pos)) {
            return Spot.HOT_INLET;
        }
        if (outletSpot(shell).equals(pos)) {
            return Spot.OUTLET;
        }
        return energySpot(shell).equals(pos) ? Spot.GENERATOR : Spot.NONE;
    }

    // The bottom-face blocks under the inlet ducts' risers (port face DOWN).
    public List<BlockPos> inletSpots(ShellStructure.Shell shell) {
        List<BlockPos> spots = new ArrayList<>();
        int bx = (int) Math.floor(x(INLET_X) / PX);
        for (float u : inletU) {
            spots.add(TurbineLayout.blockAt(shell, (int) Math.floor(u / PX), 0, bx));
        }
        return spots;
    }

    // The bottom-face block under the compressor's discharge pipe (port face DOWN).
    public BlockPos outletSpot(ShellStructure.Shell shell) {
        return TurbineLayout.blockAt(shell, (int) Math.floor(dischargeU / PX), 0, (int) Math.floor(x(SHAFT_X) / PX));
    }

    // The bottom-face block under the cable bus from the terminal box (port face DOWN).
    public BlockPos energySpot(ShellStructure.Shell shell) {
        return TurbineLayout.blockAt(shell, (int) Math.floor(busU / PX), 0, (int) Math.floor(x(BUS_X) / PX));
    }
}
