/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.zagdrath.arcforge.multiblock.ShellStructure;

// How a formed Steam Turbine Array is laid out as one turbine-generator set (drawn by SteamTurbineArrayRenderer, named
// by Jade, checked by the GameTests). Everything is in pixels in the turbine's own frame: u along the axis from the
// bearing (steam) end at 0 to the generator end at length x 16, x across from one long side to the other, y up from
// the bottom of the structure.
//
// - A steel plinth: a deck slab on footings (a stepped base, or stepped columns when the structure is taller than the
//   turbine needs). A cross-section wider than tall gives the extra width to the deck and a walkway with railings along
//   the front.
// - The turbine casings on one shaft, with bearing pedestals between them: length 3-5 one turbine casing; 6-9 a small
//   high-pressure and a large low-pressure casing; 10-15 high-, intermediate- and a double-flow low-pressure casing (wide
//   in the middle, tapering to each end) with a crossover pipe from the intermediate casing's top into the
//   low-pressure casing's. Diameters follow the cross-section's shorter side (the low-pressure casing nearly fills it,
//   the high-pressure about half); lengths share the array's length (about high 15%, intermediate 20%, low 30%,
//   generator 25%, the rest bearings and couplings).
// - The generator at the far end: a housing with cooling ribs and a terminal box on top.
// - Three fitted spots for ports: the top face above the high-pressure steam chest (steam), the generator end's face
//   (energy) and the bottom face under the low-pressure exhaust neck (exhaust, where a Condenser Array can sit).
public final class TurbineLayout {
    public static final float PX = 16.0F;
    // Blocks long up to which the turbine has one casing, then two; longer ones have three.
    public static final int SINGLE_MAX = 5, DOUBLE_MAX = 9;
    // The deck slab, the least footing under it, and the gap between the deck and the casings' bottoms.
    public static final float DECK = 3.0F, FOOTING = 3.0F, STANDOFF = 2.0F;
    // Footings taller than this become columns.
    public static final float COLUMN_FOOTING = 6.0F;
    // The shortest bearing gap between sections, and the shortest casing and generator.
    public static final float MIN_GAP = 8.0F, MAX_GAP = 16.0F, MIN_CASING = 12.0F, MIN_GENERATOR = 16.0F;

    public enum Part {
        HIGH_PRESSURE("high_pressure"),
        INTERMEDIATE("intermediate"),
        LOW_PRESSURE("low_pressure"),
        TURBINE("turbine"),
        GENERATOR("generator"),
        BEARING("bearing");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        public String getSerializedName() {
            return name;
        }

        public String translationKey() {
            return "arcforge.steam_turbine_array.part." + name;
        }
    }

    // The fitted port spots.
    public enum Spot {
        NONE, STEAM_INLET, GENERATOR, EXHAUST;

        public String translationKey() {
            return "jade.arcforge.turbine.spot." + name().toLowerCase(java.util.Locale.ROOT);
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

    // A turbine casing: its stretch, its largest and end diameters, whether it's double-flow (widest in the middle),
    // its inspection window's stretch (on both sides, on the full-diameter part) and its blading: rows seen through the
    // window, blades per row, and the hub and last row's tip radius as shares of the casing's radius.
    public record Casing(Part part, Span span, float diameter, float endDiameter, boolean doubleFlow, Span window,
            int bladeRows, int blades, float hub, float tip) {
        public float radius() {
            return diameter / 2.0F;
        }

        // The outline from end to end: (u, radius) points, the full radius between the middle two.
        public float[][] profile() {
            float r = diameter / 2.0F, e = endDiameter / 2.0F, length = span.length();
            float taper = doubleFlow ? length * 0.25F : Math.min(3.0F, length * 0.15F);
            return new float[][] { { span.u0(), e }, { span.u0() + taper, r }, { span.u1() - taper, r }, { span.u1(), e } };
        }

        public float radiusAt(float u) {
            float[][] profile = profile();
            for (int i = 0; i + 1 < profile.length; i++) {
                if (u <= profile[i + 1][0]) {
                    float share = profile[i + 1][0] > profile[i][0] ? (u - profile[i][0]) / (profile[i + 1][0] - profile[i][0]) : 1.0F;
                    return profile[i][1] + (profile[i + 1][1] - profile[i][1]) * Math.clamp(share, 0.0F, 1.0F);
                }
            }
            return endDiameter / 2.0F;
        }

        // Row i's tip radius share: the rows get longer as the pressure drops (outward from the middle of a double-flow
        // casing, toward the generator otherwise).
        public float tipShare(int row) {
            int fromInlet = doubleFlow ? Math.abs(2 * row - (bladeRows - 1)) / 2 : row;
            int last = doubleFlow ? (bladeRows - 1) / 2 : bladeRows - 1;
            return tip - (last - fromInlet) * 0.05F;
        }
    }

    // A box in the turbine's frame (pixels).
    public record Box(float x0, float y0, float u0, float x1, float y1, float u1) {}

    // The crossover pipe: up from the intermediate casing at riseU, along at runY, down into the low-pressure casing at
    // dropU; base and drop are where it enters the casings (a little inside them).
    public record Crossover(float riseU, float dropU, float baseY, float runY, float dropY, float diameter) {}

    // The walkway along one long side: its stretch across, its floor, and how tall its railings are.
    public record Walkway(float x0, float x1, float floorY, float railHeight, boolean high) {}

    // A stop-valve housing beside the steam end's casing: an upright cylinder at (x, u) from bottom (on a stand from the
    // deck) to top, under a bonnet and handwheel; its inlet pipe at pipeY runs across into the casing to pipeEnd.
    public record Valve(float x, float u, float radius, float bottom, float top, float pipeY, float pipeEnd) {}

    private final int length, width, height;
    private final int tier;
    private final float shaftX, shaftY, deckTop, footingTop;
    private final float regionX0, regionX1;
    private final List<Casing> casings;
    private final List<Span> gaps;
    private final Span generator;
    private final float generatorWidth, generatorTop;
    private final Box terminalBox;
    private final Box steamChest;
    private final List<Valve> valves;
    private final Box exhaustNeck;
    private final @Nullable Crossover crossover;
    private final @Nullable Walkway walkway;
    private final float shaftRadius;
    // The fitted spots, as (along, across) block indices on the top / bottom faces, and (across, up) on the generator end.
    private final int steamU, steamX, energyX, energyY, exhaustU, exhaustX;

    private TurbineLayout(int length, int width, int height, boolean walkwayHigh) {
        this.length = length;
        this.width = width;
        this.height = height;
        float total = length * PX, w16 = width * PX, h16 = height * PX;
        float n16 = Math.min(width, height) * PX;
        this.tier = length <= SINGLE_MAX ? 1 : length <= DOUBLE_MAX ? 2 : 3;

        // Across: a cross-section wider than tall gives its extra width to the walkway.
        float walkX0 = 0.0F, walkX1 = 0.0F, railHeight = 0.0F;
        boolean hasWalkway = width > height;
        if (hasWalkway) {
            float walk = Math.clamp(even((w16 - h16) * 0.5F), 12.0F, 28.0F);
            railHeight = Math.clamp(Math.round(n16 * 0.3F), 10.0F, 16.0F);
            if (walkwayHigh) {
                regionX0 = 0.0F;
                regionX1 = w16 - walk - 2.0F;
                walkX0 = w16 - walk;
                walkX1 = w16 - 1.0F;
            } else {
                regionX0 = walk + 2.0F;
                regionX1 = w16;
                walkX0 = 1.0F;
                walkX1 = walk;
            }
        } else {
            regionX0 = 0.0F;
            regionX1 = w16;
        }
        float region = regionX1 - regionX0;
        this.shaftX = (regionX0 + regionX1) / 2.0F;

        // Up: the plinth, the low-pressure casing and room above it (for the crossover pipe).
        float crossoverDiameter = tier == 3 ? Math.max(6.0F, even(n16 * 0.13F)) : 0.0F;
        float top = tier == 3 ? crossoverDiameter + 5.0F : 3.0F;
        float lpShare = tier == 1 ? 0.78F : 0.86F;
        float lp = even(Math.min(Math.min(Math.round(lpShare * n16), h16 - FOOTING - DECK - STANDOFF - top), region - 6.0F));
        float hp = even(Math.min(n16 * 0.5F, lp * 0.62F));
        float ip = even(Math.clamp(n16 * 0.66F, hp + 4.0F, lp - 4.0F));
        float slack = Math.max(0.0F, h16 - (FOOTING + DECK + STANDOFF + lp + top));
        this.footingTop = FOOTING + slack;
        this.deckTop = footingTop + DECK;
        this.shaftY = deckTop + STANDOFF + lp / 2.0F;
        this.shaftRadius = Math.max(1.5F, Math.round(n16 * 0.035F * 2.0F) / 2.0F);

        // Along: bearing gaps between the sections, then the sections by their shares.
        Part[] parts = tier == 1 ? new Part[] { Part.TURBINE }
                : tier == 2 ? new Part[] { Part.HIGH_PRESSURE, Part.LOW_PRESSURE }
                : new Part[] { Part.HIGH_PRESSURE, Part.INTERMEDIATE, Part.LOW_PRESSURE };
        float[] weights = tier == 1 ? new float[] { 0.55F, 0.30F } : tier == 2 ? new float[] { 0.20F, 0.38F, 0.27F } : new float[] { 0.15F, 0.20F, 0.30F, 0.25F };
        float shareSum = 0.0F;
        for (float weight : weights) {
            shareSum += weight;
        }
        int gapCount = parts.length + 1;
        float gap = Math.clamp((float) Math.floor((1.0F - shareSum) * total / gapCount), MIN_GAP, MAX_GAP);
        float[] lengths = share(total - gapCount * gap - 1.0F, weights, parts.length);

        List<Casing> builtCasings = new ArrayList<>();
        List<Span> builtGaps = new ArrayList<>();
        float u = 0.0F;
        for (int i = 0; i < parts.length; i++) {
            builtGaps.add(new Span(u, u + gap));
            u += gap;
            Span span = new Span(u, u + lengths[i]);
            u += lengths[i];
            builtCasings.add(casing(parts[i], span, parts[i] == Part.HIGH_PRESSURE ? hp : parts[i] == Part.INTERMEDIATE ? ip : lp, ip, tier));
        }
        builtGaps.add(new Span(u, u + gap));
        u += gap;
        this.casings = List.copyOf(builtCasings);
        this.gaps = List.copyOf(builtGaps);
        this.generator = new Span(u, total - 1.0F);

        // The generator: a housing a little narrower than the low-pressure casing, its top under the terminal box.
        this.generatorWidth = even(Math.min(lp * 0.86F, region - 4.0F));
        this.generatorTop = Math.max(shaftY + 4.0F, Math.min(shaftY + lp * 0.42F, h16 - 7.0F));
        float terminalLength = Math.clamp(even(generator.length() * 0.22F), 6.0F, 14.0F);
        float terminalWidth = even(generatorWidth * 0.45F);
        this.terminalBox = new Box(shaftX - terminalWidth / 2.0F, generatorTop, generator.u1() - 3.0F - terminalLength,
                shaftX + terminalWidth / 2.0F, Math.min(generatorTop + 4.0F, h16 - 3.0F), generator.u1() - 3.0F);

        // The steam chest on top of the steam end's casing, under the steam spot's block.
        Casing first = casings.get(0);
        this.steamU = (int) Math.floor(first.span().middle() / PX);
        this.steamX = (int) Math.floor(shaftX / PX);
        float firstRadius = first.radius();
        float chestSize = Math.clamp(even(first.diameter() * 0.5F), 8.0F, 14.0F);
        float chestAlong = Math.min(chestSize, first.span().length() - 2.0F);
        float chestU = Math.clamp((steamU + 0.5F) * PX, first.span().u0() + 1.0F + chestAlong / 2.0F, first.span().u1() - 1.0F - chestAlong / 2.0F);
        float chestX = Math.clamp((steamX + 0.5F) * PX, shaftX - firstRadius * 0.5F, shaftX + firstRadius * 0.5F);
        float dx = Math.abs(chestX - shaftX) + chestSize / 2.0F;
        float surface = shaftY + (float) Math.sqrt(Math.max(0.0F, firstRadius * firstRadius - dx * dx));
        float chestBottom = Math.min(surface, shaftY + firstRadius) - 1.5F;
        float room = h16 - 1.0F - 3.0F - chestBottom;
        float chestHeight = Math.max(2.0F, Math.min(Math.clamp(Math.round(first.diameter() * 0.25F), 4.0F, 7.0F), room));
        this.steamChest = new Box(chestX - chestSize / 2.0F, chestBottom, chestU - chestAlong / 2.0F,
                chestX + chestSize / 2.0F, chestBottom + chestHeight, chestU + chestAlong / 2.0F);

        // The valve housings beside the steam end's casing, where they fit.
        List<Valve> builtValves = new ArrayList<>();
        float valveRadius = Math.clamp(Math.round(first.diameter() * 0.16F), 3.0F, 6.0F);
        float valveOffset = firstRadius + 3.0F + valveRadius;
        if (shaftX - valveOffset - valveRadius >= regionX0 + 1.0F && shaftX + valveOffset + valveRadius <= regionX1 - 1.0F
                && first.span().length() >= 2 * valveRadius + 4.0F) {
            float valveBottom = Math.max(deckTop + 3.0F, shaftY - firstRadius * 0.4F);
            float valveTop = Math.min(shaftY + firstRadius * 0.55F, h16 - 8.0F);
            float pipeY = Math.min(shaftY + firstRadius * 0.35F, valveTop - 2.0F);
            if (valveTop - valveBottom >= 4.0F) {
                for (int side = -1; side <= 1; side += 2) {
                    builtValves.add(new Valve(shaftX + side * valveOffset, first.span().middle(), valveRadius, valveBottom, valveTop, pipeY,
                            shaftX + side * firstRadius * 0.6F));
                }
            }
        }
        this.valves = List.copyOf(builtValves);

        // The exhaust neck under the low-pressure (last) casing: down to the deck, or on down between the columns.
        Casing last = casings.get(casings.size() - 1);
        float neckLength = even(Math.clamp(last.span().length() * 0.4F, 8.0F, Math.max(8.0F, last.span().length() - 6.0F)));
        float neckWidth = even(last.diameter() * 0.6F);
        float neckBottom = hasColumns() ? 1.0F : deckTop;
        this.exhaustNeck = new Box(shaftX - neckWidth / 2.0F, neckBottom, last.span().middle() - neckLength / 2.0F,
                shaftX + neckWidth / 2.0F, shaftY, last.span().middle() + neckLength / 2.0F);
        this.exhaustU = (int) Math.floor(last.span().middle() / PX);
        this.exhaustX = steamX;
        this.energyX = steamX;
        this.energyY = (int) Math.floor((deckTop + generatorTop) / 2.0F / PX);

        if (tier == 3) {
            Casing intermediate = casings.get(1);
            float runY = shaftY + last.radius() + 2.0F + crossoverDiameter / 2.0F;
            this.crossover = new Crossover(intermediate.span().middle(), last.span().middle(), shaftY + intermediate.radius() - 2.0F, runY,
                    shaftY + last.radius() - 2.0F, crossoverDiameter);
        } else {
            this.crossover = null;
        }
        this.walkway = hasWalkway ? new Walkway(walkX0, walkX1, deckTop, railHeight, walkwayHigh) : null;
    }

    private static Casing casing(Part part, Span span, float diameter, float intermediate, int tier) {
        boolean doubleFlow = part == Part.LOW_PRESSURE && tier == 3;
        float end = doubleFlow ? even(Math.max(intermediate, diameter * 0.62F)) : even(diameter * 0.7F);
        float length = span.length();
        float taper = doubleFlow ? length * 0.25F : Math.min(3.0F, length * 0.15F);
        float a = span.u0() + taper, b = span.u1() - taper;
        float margin = Math.max(1.0F, (b - a) * 0.12F);
        Span window = b - a >= 6.0F ? new Span(a + margin, b - margin) : new Span(a, a);
        return switch (part) {
            case HIGH_PRESSURE -> new Casing(part, span, diameter, end, false, window, 4, 10, 0.42F, 0.86F);
            case INTERMEDIATE -> new Casing(part, span, diameter, end, false, window, 5, 12, 0.36F, 0.88F);
            case LOW_PRESSURE -> new Casing(part, span, diameter, end, doubleFlow, window, 6, 14, 0.30F, 0.92F);
            default -> new Casing(part, span, diameter, end, false, window, 5, 12, 0.36F, 0.88F);
        };
    }

    // Splits total among count casings and the generator (the last weight) by their weights, keeping each at its least.
    private static float[] share(float total, float[] weights, int count) {
        float[] lengths = new float[weights.length];
        boolean[] fixed = new boolean[weights.length];
        for (int pass = 0; pass < weights.length; pass++) {
            float free = total, weight = 0.0F;
            for (int i = 0; i < weights.length; i++) {
                if (fixed[i]) {
                    free -= lengths[i];
                } else {
                    weight += weights[i];
                }
            }
            boolean changed = false;
            for (int i = 0; i < weights.length; i++) {
                if (fixed[i]) {
                    continue;
                }
                float least = i < count ? MIN_CASING : MIN_GENERATOR;
                lengths[i] = (float) Math.floor(free * weights[i] / weight);
                if (lengths[i] < least) {
                    lengths[i] = least;
                    fixed[i] = true;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        // Whatever rounding left over goes to the generator.
        float used = 0.0F;
        for (float value : lengths) {
            used += value;
        }
        lengths[weights.length - 1] += total - used;
        return lengths;
    }

    private static float even(float value) {
        return 2.0F * Math.round(value / 2.0F);
    }

    // --- Making one ---

    public static TurbineLayout of(int length, int width, int height, boolean walkwayHigh) {
        return new TurbineLayout(length, width, height, walkwayHigh);
    }

    // The layout of a formed shell whose front (the side with the walkway) faces front.
    public static TurbineLayout of(ShellStructure.Shell shell, Direction front) {
        Direction.Axis across = shell.axis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return new TurbineLayout(shell.length(), shell.size(across), shell.size(Direction.Axis.Y), frontIsHigh(shell.axis(), front));
    }

    // Whether the front is the side at the high end of x in the turbine's frame. Along Z, x is world X; along X, x runs
    // toward world -Z (so the frame keeps its handedness).
    public static boolean frontIsHigh(Direction.Axis axis, Direction front) {
        return axis == Direction.Axis.X ? front == Direction.NORTH : front == Direction.EAST;
    }

    // --- Blocks and the turbine's frame ---

    // A block's index along the axis (0 at the bearing end).
    public static int blockU(ShellStructure.Shell shell, BlockPos pos) {
        return shell.offset(pos, shell.axis());
    }

    // A block's index across (0 at x = 0).
    public static int blockX(ShellStructure.Shell shell, BlockPos pos) {
        return shell.axis() == Direction.Axis.X ? shell.size(Direction.Axis.Z) - 1 - shell.offset(pos, Direction.Axis.Z) : shell.offset(pos, Direction.Axis.X);
    }

    public static BlockPos blockAt(ShellStructure.Shell shell, int u, int y, int x) {
        return shell.axis() == Direction.Axis.X
                ? shell.min().offset(u, y, shell.size(Direction.Axis.Z) - 1 - x)
                : shell.min().offset(x, y, u);
    }

    // The part a block's slice of the turbine belongs to: what's under its middle along the axis.
    public Part partAt(int blockU) {
        float u = (blockU + 0.5F) * PX;
        for (Casing casing : casings) {
            if (casing.span().contains(u)) {
                return casing.part();
            }
        }
        return generator.contains(u) || u >= generator.u1() ? Part.GENERATOR : Part.BEARING;
    }

    // The fitted spot a block of the shell is, if any.
    public Spot spotAt(ShellStructure.Shell shell, BlockPos pos) {
        int u = blockU(shell, pos), x = blockX(shell, pos), y = shell.offset(pos, Direction.Axis.Y);
        if (y == height - 1 && u == steamU && x == steamX) {
            return Spot.STEAM_INLET;
        }
        if (y == 0 && u == exhaustU && x == exhaustX) {
            return Spot.EXHAUST;
        }
        if (u == length - 1 && x == energyX && y == energyY) {
            return Spot.GENERATOR;
        }
        return Spot.NONE;
    }

    // The block of each fitted spot, and the face of it the port goes on.
    public BlockPos steamSpot(ShellStructure.Shell shell) {
        return blockAt(shell, steamU, height - 1, steamX);
    }

    public BlockPos energySpot(ShellStructure.Shell shell) {
        return blockAt(shell, length - 1, energyY, energyX);
    }

    public BlockPos exhaustSpot(ShellStructure.Shell shell) {
        return blockAt(shell, exhaustU, 0, exhaustX);
    }

    public static Direction energyFace(ShellStructure.Shell shell) {
        return Direction.fromAxisAndDirection(shell.axis(), Direction.AxisDirection.POSITIVE);
    }

    // --- Getters ---

    public int length() {
        return length;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    // 1: one casing, 2: high and low pressure, 3: high, intermediate and double-flow low pressure.
    public int tier() {
        return tier;
    }

    public float shaftX() {
        return shaftX;
    }

    public float shaftY() {
        return shaftY;
    }

    public float shaftRadius() {
        return shaftRadius;
    }

    public float deckTop() {
        return deckTop;
    }

    // The top of the footings: the deck slab's underside.
    public float footingTop() {
        return footingTop;
    }

    // Footings tall enough to be columns, with open space under the deck.
    public boolean hasColumns() {
        return footingTop > COLUMN_FOOTING;
    }

    // The stretch across the turbine sits in (the deck runs the whole width).
    public float regionX0() {
        return regionX0;
    }

    public float regionX1() {
        return regionX1;
    }

    public List<Casing> casings() {
        return casings;
    }

    // The bearing gaps: before the first casing, between casings, and before the generator.
    public List<Span> gaps() {
        return gaps;
    }

    public Span generator() {
        return generator;
    }

    public float generatorWidth() {
        return generatorWidth;
    }

    public float generatorTop() {
        return generatorTop;
    }

    public Box terminalBox() {
        return terminalBox;
    }

    public Box steamChest() {
        return steamChest;
    }

    public List<Valve> valves() {
        return valves;
    }

    public Box exhaustNeck() {
        return exhaustNeck;
    }

    public @Nullable Crossover crossover() {
        return crossover;
    }

    public @Nullable Walkway walkway() {
        return walkway;
    }

    public float widthPx() {
        return width * PX;
    }

    public float heightPx() {
        return height * PX;
    }

    public float lengthPx() {
        return length * PX;
    }
}
