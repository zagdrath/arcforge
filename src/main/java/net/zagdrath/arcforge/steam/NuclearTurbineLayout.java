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

// How a formed Nuclear Steam Turbine is laid out as one turbine-generator set (drawn by its renderer from
// NuclearTurbineMesh, named by Jade, given its block shapes by NuclearTurbineShapes). Everything is in pixels in the
// turbine's own frame, as TurbineLayout's: u along the shaft from the front standard (0, the negative end of the shell's
// axis) to the exciter (length x 16), x across from one long side to the other, y up from the bottom. The cross-section
// is always 7 x 7.
//
// It's laid out for the front (the walkway and the staircase) at high x and mirrored across when the front is at low x:
// the canonical positions below go through x(...).
// - A raised turbine deck (top at DECK_TOP) on columns, the condenser necks, the main steam pipes, the drains and the
//   generator's cable bus under it; the walkway along the front edge of the deck with a handrail, and a short
//   staircase up to it at the front standard's end.
// - Along the shaft (SHAFT_X, SHAFT_Y): the front standard (a squat bearing pedestal with oil lines and the
//   turning-gear motor on its back side) and the turning-gear wheel behind it; the compact high-pressure cylinder with
//   its two steam chests (one each side, each fed through a stop valve with a hydraulic actuator by a main steam pipe
//   rising through the deck); one low-pressure cylinder per 9 blocks of length (wide angular boxes, their exhaust hoods
//   flaring down through the deck into the condenser necks); the generator (a stator with domed end shields, two
//   hydrogen coolers on top and the terminal box under its steam end); the slip-ring housing and the exciter.
// - A bearing pedestal pair and a coupling in the gap between every two casings.
// - The two moisture separator reheaters lying on saddles one each side of the low-pressure section, about half the
//   set's length, and from each a crossover pipe arching over into the top of every low-pressure cylinder.
// - Fitted port spots: a steam inlet under each main steam pipe, the cable bus's foot (energy) and the foot of each
//   condenser neck (exhaust), all on the bottom face.
public final class NuclearTurbineLayout {
    public static final int WIDTH = 7, HEIGHT = 7, MIN_LENGTH = 21, MAX_LENGTH = 45;
    public static final float PX = 16.0F, WIDTH_PX = WIDTH * PX, HEIGHT_PX = HEIGHT * PX;
    // The deck slab, and the walkway's inner edge (canonical x) from there to the front.
    public static final float DECK_BOTTOM = 26.0F, DECK_TOP = 32.0F, WALKWAY_X = 97.0F;
    // The shaft (canonical x).
    public static final float SHAFT_X = 50.0F, SHAFT_Y = 56.0F, SHAFT_RADIUS = 3.5F;
    // The gap between two casings: a bearing pedestal, a coupling, a pedestal.
    public static final float GAP = 12.0F, PEDESTAL = 3.5F;
    // The staircase runs from the ground at STAIR_U0 up to the deck at STAIR_U1 along the front strip.
    public static final float STAIR_U0 = 2.0F, STAIR_U1 = 34.0F;
    // The front standard, and the turning-gear wheel behind it.
    public static final float STANDARD_U0 = 4.0F, STANDARD_U1 = 24.0F, WHEEL_U0 = 25.5F, WHEEL_U1 = 28.5F, WHEEL_RADIUS = 9.0F;
    // The high-pressure cylinder.
    public static final float HP_U0 = 34.0F, HP_RADIUS = 13.0F;
    // The low-pressure cylinders' box (canonical x, y), the flare of their hoods at the deck, the necks under it.
    public static final float LP_X0 = 26.0F, LP_X1 = 74.0F, LP_TOP = 84.0F, HOOD_TOP = 44.0F, HOOD_FLARE = 3.0F, NECK_X0 = 30.0F, NECK_X1 = 70.0F;
    // The split line's flange, the cover steps and the roof.
    public static final float SPLIT_Y0 = 54.0F, SPLIT_Y1 = 57.0F, COVER_1 = 88.0F, COVER_2 = 91.0F;
    // The sight port in each low-pressure cylinder's front wall (y), and the blade ring behind it (radii).
    public static final float PORT_Y0 = 60.0F, PORT_Y1 = 66.0F, PORT_HALF = 3.0F, PORT_DEPTH = 10.0F, BLADE_HUB = 13.0F, BLADE_TIP = 22.5F;
    // The reheaters (canonical x of each, back then front), their axis height and radius.
    public static final float MSR_BACK_X = 13.0F, MSR_FRONT_X = 87.0F, MSR_Y = 46.0F, MSR_RADIUS = 10.0F;
    // The crossover pipes: radius (its facets 4 px round), where each lands on a low-pressure roof (canonical x: the
    // back reheater's across the axis on the front half, the front one's on the back half), and the arches' crown.
    public static final float CROSSOVER_RADIUS = 5.2F, INLET_BACK_X = 58.0F, INLET_FRONT_X = 42.0F, CROWN_Y = 103.0F;
    // The generator's stator, its hydrogen coolers, the slip-ring housing and exciter at the far end.
    public static final float STATOR_RADIUS = 19.0F, STATOR_END_RADIUS = 12.0F, SHIELD = 6.0F;
    // The steam chests (canonical x, back then front), their radius, and their main steam pipes' radius.
    public static final float CHEST_BACK_X = 24.0F, CHEST_FRONT_X = 72.0F, CHEST_RADIUS = 6.0F, MAIN_PIPE_RADIUS = 4.0F;
    // The cable bus under the terminal box (canonical x: the middle block's centre, so mirroring keeps it there).
    public static final float BUS_X = 56.0F, BUS_HALF = 5.0F;

    public enum Part {
        FRONT_STANDARD, HIGH_PRESSURE, REHEATER, LOW_PRESSURE, GENERATOR, EXCITER, BEARING, DECK;

        public String translationKey() {
            return "arcforge.nuclear_steam_turbine.part." + name().toLowerCase(Locale.ROOT);
        }
    }

    // The fitted port spots.
    public enum Spot {
        NONE, STEAM_INLET, GENERATOR, EXHAUST;

        public String translationKey() {
            return "jade.arcforge.turbine.spot." + name().toLowerCase(Locale.ROOT);
        }
    }

    // The faces the fitted spots' ports go on.
    public static final Direction STEAM_FACE = Direction.DOWN, EXHAUST_FACE = Direction.DOWN, ENERGY_FACE = Direction.DOWN;

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

    // A crossover pipe: from the reheater's top at (x0, u0) to the roof inlet at (x1, u1), canonical x.
    public record Arch(float x0, float u0, float x1, float u1, boolean front) {}

    private final int length;
    private final boolean frontHigh;
    private final float lengthPx;
    private final Span highPressure, generator, slipRing, exciter, reheaters;
    private final List<Span> lowPressure = new ArrayList<>(), gaps = new ArrayList<>();
    private final List<Arch> arches = new ArrayList<>();
    // The main steam pipes' u (each under a steam chest's stop valve), and the chests' own u.
    private final float steamPipeU, chestU;
    // The cable bus's u.
    private final float busU;

    private NuclearTurbineLayout(int length, boolean frontHigh) {
        this.length = Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
        this.frontHigh = frontHigh;
        this.lengthPx = this.length * PX;
        int n = this.length - MIN_LENGTH;
        float hpLength = 40.0F + n * 0.5F;
        highPressure = new Span(HP_U0, HP_U0 + hpLength);
        float generatorLength = 56.0F + n * 1.5F;
        exciter = new Span(lengthPx - 20.0F, lengthPx - 3.0F);
        slipRing = new Span(lengthPx - 30.0F, exciter.u0());
        generator = new Span(slipRing.u0() - 2.0F - generatorLength, slipRing.u0() - 2.0F);
        // The low-pressure cylinders share what's left between the high-pressure cylinder and the generator.
        int count = lowPressureCount();
        float from = highPressure.u1() + GAP, to = generator.u0() - GAP;
        float each = (to - from - (count - 1) * GAP) / count;
        gaps.add(new Span(highPressure.u1(), from));
        for (int i = 0; i < count; i++) {
            float u0 = from + i * (each + GAP);
            lowPressure.add(new Span(u0, u0 + each));
            gaps.add(new Span(u0 + each, u0 + each + GAP));
        }
        // The reheaters: about half the set long, centred on the low-pressure section, never longer than it.
        float middle = (from + to) / 2.0F, half = Math.min((to - from) / 2.0F + 4.0F, lengthPx / 4.0F);
        reheaters = new Span(middle - half, middle + half);
        for (Span lp : lowPressure) {
            float d = Math.min(20.0F, lp.length() / 2.0F - 14.0F);
            float back = lp.middle() - d, front = lp.middle() + d;
            arches.add(new Arch(MSR_BACK_X, clamp(back, reheaters.u0() + 14.0F, reheaters.u1() - 14.0F), INLET_BACK_X, back, false));
            arches.add(new Arch(MSR_FRONT_X, clamp(front, reheaters.u0() + 14.0F, reheaters.u1() - 14.0F), INLET_FRONT_X, front, true));
        }
        // The steam pipes on a block's middle, so their ports sit under them.
        steamPipeU = (float) Math.floor((HP_U0 + 8.0F) / PX) * PX + 8.0F;
        chestU = steamPipeU + 16.0F;
        busU = (float) Math.floor((generator.u0() + 14.0F) / PX) * PX + 8.0F;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // --- Making one ---

    public static NuclearTurbineLayout of(int length, boolean frontHigh) {
        return new NuclearTurbineLayout(length, frontHigh);
    }

    // The layout of a formed shell whose front (the side with the walkway) faces front.
    public static NuclearTurbineLayout of(ShellStructure.Shell shell, Direction front) {
        return new NuclearTurbineLayout(shell.length(), TurbineLayout.frontIsHigh(shell.axis(), front));
    }

    // --- The frame ---

    // A canonical x (the front at high x) in this layout's frame.
    public float x(float canonical) {
        return frontHigh ? canonical : WIDTH_PX - canonical;
    }

    // The canonical x of a frame x (its own inverse).
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

    // One per 9 blocks of length (21 -> 2, 27 -> 3, 36 -> 4, 45 -> 5).
    public int lowPressureCount() {
        return length / 9;
    }

    public Span highPressure() {
        return highPressure;
    }

    public List<Span> lowPressure() {
        return lowPressure;
    }

    // The gaps between casings, from the high-pressure cylinder's to the generator's (a pedestal pair and a coupling in each).
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

    public Span reheaters() {
        return reheaters;
    }

    public List<Arch> arches() {
        return arches;
    }

    public float steamPipeU() {
        return steamPipeU;
    }

    public float chestU() {
        return chestU;
    }

    public float busU() {
        return busU;
    }

    // The terminal box under the stator's steam end, round the cable bus.
    public Span terminalBox() {
        return new Span(busU - 9.0F, busU + 9.0F);
    }

    // Where each low-pressure cylinder's sight port is along u: its middle.
    public float portU(Span lp) {
        return lp.middle();
    }

    // The block along u each low-pressure cylinder's condenser neck port sits under: the one holding its middle.
    private int neckBlock(Span lp) {
        return (int) Math.floor(lp.middle() / PX);
    }

    // --- Blocks ---

    // The part a block of the shell shows (for Jade).
    public Part partAt(ShellStructure.Shell shell, BlockPos pos) {
        int bu = TurbineLayout.blockU(shell, pos), bx = TurbineLayout.blockX(shell, pos), by = shell.offset(pos, Direction.Axis.Y);
        float u = (bu + 0.5F) * PX, cx = canonical((bx + 0.5F) * PX), y = (by + 0.5F) * PX;
        if (y < DECK_TOP || cx >= WALKWAY_X) {
            return Part.DECK;
        }
        if (reheaters.contains(u) && y < MSR_Y + MSR_RADIUS + 8.0F
                && (Math.abs(cx - MSR_BACK_X) <= MSR_RADIUS + 4.0F || Math.abs(cx - MSR_FRONT_X) <= MSR_RADIUS + 4.0F)) {
            return Part.REHEATER;
        }
        if (u < STANDARD_U1 + 2.0F) {
            return Part.FRONT_STANDARD;
        }
        if (highPressure.contains(u) || u >= STANDARD_U1 + 2.0F && u < highPressure.u0()) {
            return Part.HIGH_PRESSURE;
        }
        for (Span lp : lowPressure) {
            if (lp.contains(u)) {
                return Part.LOW_PRESSURE;
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

    // Which low-pressure cylinder a block's slice is in (1 from the steam end), or 0.
    public int lowNumberAt(int blockU) {
        float u = (blockU + 0.5F) * PX;
        for (int i = 0; i < lowPressure.size(); i++) {
            if (lowPressure.get(i).contains(u)) {
                return i + 1;
            }
        }
        return 0;
    }

    // The fitted spot a block of the shell is, if any.
    public Spot spotAt(ShellStructure.Shell shell, BlockPos pos) {
        if (steamSpots(shell).contains(pos)) {
            return Spot.STEAM_INLET;
        }
        if (exhaustSpots(shell).contains(pos)) {
            return Spot.EXHAUST;
        }
        return energySpot(shell).equals(pos) ? Spot.GENERATOR : Spot.NONE;
    }

    // The bottom-face blocks under the two main steam pipes (port face DOWN).
    public List<BlockPos> steamSpots(ShellStructure.Shell shell) {
        int bu = (int) Math.floor(steamPipeU / PX);
        return List.of(TurbineLayout.blockAt(shell, bu, 0, (int) Math.floor(x(CHEST_BACK_X) / PX)),
                TurbineLayout.blockAt(shell, bu, 0, (int) Math.floor(x(CHEST_FRONT_X) / PX)));
    }

    // The bottom-face block under the cable bus from the terminal box (port face DOWN).
    public BlockPos energySpot(ShellStructure.Shell shell) {
        return TurbineLayout.blockAt(shell, (int) Math.floor(busU / PX), 0, (int) Math.floor(x(BUS_X) / PX));
    }

    public Direction energyFace(ShellStructure.Shell shell) {
        return ENERGY_FACE;
    }

    // The bottom-face block under each low-pressure cylinder's condenser neck (port face DOWN).
    public List<BlockPos> exhaustSpots(ShellStructure.Shell shell) {
        List<BlockPos> spots = new ArrayList<>();
        int bx = (int) Math.floor(x(SHAFT_X + 6.0F) / PX);
        for (Span lp : lowPressure) {
            spots.add(TurbineLayout.blockAt(shell, neckBlock(lp), 0, bx));
        }
        return spots;
    }
}
