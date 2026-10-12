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
// by Jade, given its block shapes by TurbineShapes, checked by the GameTests). Everything is in pixels in the turbine's
// own frame: u along the axis from the bearing (steam) end at 0 to the generator end at length x 16, x across from one
// long side to the other, y up from the bottom of the structure.
//
// - A steel plinth: a deck slab on footings (a stepped base, or stepped columns when the structure is taller than the
//   turbine needs). A cross-section wider than tall gives the extra width to the deck and a walkway with railings along
//   the front.
// - The turbine casings on one shaft, the front standard in the first gap and a pair of bearing pedestals either side
//   of a coupling in each gap after it (pedestals): length 3-5 one turbine casing; 6-9 a small high-pressure and a
//   large low-pressure casing; 10 and up high-, intermediate- and double-flow low-pressure casings (wide in the middle,
//   tapering to each end), as many low-pressure casings (up to three) as the length holds at a real turbine's
//   proportions (squeezed a little for one more on a long set), with a crossover pipe from the intermediate casing's
//   top into each low-pressure casing's. Diameters follow the cross-section's shorter side (the low-pressure casings
//   nearly fill it, the high-pressure about half). Each section is laid out at its natural length for its diameter
//   (high about 1.1 diameters, intermediate 1.2, low 1.15, the generator 1.6 of its width), then all are scaled
//   together to fill the length, so a long set gets more casings rather than stretched ones.
// - The generator at the far end: a housing with cooling ribs and a terminal box on top, and on a long set an exciter
//   beyond it.
// - Inspection windows on both upper sides of each casing's full-diameter part, kept clear of everything that enters
//   the casing (the steam chest, the stop-valve pipes, the crossover), so nothing passes through the glass; the blading
//   in them stays inside the casing's inner wall (tipLimit). A short steam end with no room for a window beside the
//   stop valves goes without them.
// - Three fitted spots for ports: the top face above the high-pressure steam chest (steam), the generator end's face
//   (energy) and the bottom face under the last low-pressure exhaust neck (exhaust, where a Condenser Array can sit).
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
    // The most low-pressure casings, the shortest window, and the generator length from which it has an exciter.
    public static final int MAX_LOW_PRESSURE = 3;
    public static final float MIN_WINDOW = 4.0F, EXCITER_FROM = 56.0F;
    // How far the sections may be squeezed below their natural lengths to fit one more low-pressure casing (on a set
    // long enough that the high-pressure casing stays two blocks long).
    private static final float LOW_SQUEEZE = 1.3F;
    // The casings are 16-sided; their inner wall (behind the windows) is WALL px inside the outer surface, the glass
    // GLASS px, and blade tips stay BLADE_CLEARANCE px inside the inner wall's flats. What enters a casing from the top
    // (the steam chest, the crossover) ends between the two walls, so it never shows inside a window.
    public static final int CASING_SIDES = 16;
    // How much of a double-flow casing's length each tapering end takes.
    public static final float DOUBLE_FLOW_TAPER = 0.18F;
    public static final float WALL = 1.5F, GLASS = 0.25F, BLADE_CLEARANCE = 1.0F;
    public static final float CASING_APOTHEM = (float) Math.cos(Math.PI / CASING_SIDES);

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
    // its inspection windows' stretches (on both upper sides, on the full-diameter part, clear of what enters the
    // casing), its blading (rows seen through the windows, blades per row, and the hub and the outlet rows' tip radius
    // as shares of the casing's radius), and where the steam comes in and leaves along it (the blades lengthen from
    // inlet to outlet; a double-flow casing's inlet is its middle and the steam leaves toward both ends).
    public record Casing(Part part, Span span, float diameter, float endDiameter, boolean doubleFlow, List<Span> windows,
            int bladeRows, int blades, float hub, float tip, float inlet, float outlet) {
        public float radius() {
            return diameter / 2.0F;
        }

        // The first window (empty when it has none).
        public Span window() {
            return windows.isEmpty() ? new Span(span.u0(), span.u0()) : windows.get(0);
        }

        // The outline from end to end: (u, radius) points, the full radius between the middle two.
        public float[][] profile() {
            float r = diameter / 2.0F, e = endDiameter / 2.0F, length = span.length();
            float taper = doubleFlow ? length * DOUBLE_FLOW_TAPER : Math.min(3.0F, length * 0.15F);
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

        // The inner wall's radius (its corners), the glass's, and how far the blade tips may reach: inside the inner
        // wall's flats with BLADE_CLEARANCE to spare, whatever the rotor's angle.
        public float innerRadius() {
            return radius() - WALL;
        }

        public float glassRadius() {
            return radius() - GLASS;
        }

        public float tipLimit() {
            return innerRadius() * CASING_APOTHEM - BLADE_CLEARANCE;
        }

        public float hubRadius() {
            return radius() * hub;
        }

        // The tip radius of a blade row at u: from 85% of the outlet tip at the inlet to the full tip at the outlet,
        // never past tipLimit.
        public float tipAt(float u) {
            float reach = doubleFlow ? Math.abs(u - inlet) / Math.max(1.0F, outlet - inlet) : (u - inlet) / Math.max(1.0F, outlet - inlet);
            float share = tip * (0.85F + 0.15F * Math.clamp(reach, 0.0F, 1.0F));
            return Math.min(radius() * share, tipLimit());
        }

        // Row i's tip radius share, for checks: the share at the outlet rows is tip.
        public float tipShare(int row) {
            return tip;
        }
    }

    // A box in the turbine's frame (pixels).
    public record Box(float x0, float y0, float u0, float x1, float y1, float u1) {}

    // The crossover pipe: up from the intermediate casing at riseU, along at runY, down into each low-pressure casing
    // at dropUs; base and drop are where it enters the casings (just inside their outer surface, never into the hollow
    // behind a window).
    public record Crossover(float riseU, float[] dropUs, float baseY, float runY, float dropY, float diameter) {
        public float dropU() {
            return dropUs[dropUs.length - 1];
        }
    }

    // The walkway along one long side: its stretch across, its floor, and how tall its railings are.
    public record Walkway(float x0, float x1, float floorY, float railHeight, boolean high) {}

    // A stop-valve housing beside the steam end's casing: an upright cylinder at (x, u) from bottom (on a stand from the
    // deck) to top, under a bonnet and handwheel; its inlet pipe at pipeY runs across into the casing to pipeEnd.
    public record Valve(float x, float u, float radius, float bottom, float top, float pipeY, float pipeEnd) {}

    private final int length, width, height;
    private final int tier, lowCount;
    private final float shaftX, shaftY, deckTop, footingTop;
    private final float regionX0, regionX1;
    private final List<Casing> casings;
    private final List<Span> gaps;
    private final Span generator;
    private final @Nullable Span exciter;
    private final float generatorWidth, generatorTop;
    private final Box terminalBox;
    private final Box steamChest;
    private final List<Valve> valves;
    private final List<Box> exhaustNecks;
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
        if (width >= 5 && height >= 5) {
            // The deck no higher than the core's first layer, as deeper in the core it couldn't be stood on (see
            // TurbineShapes): the rest of the room goes over the turbine.
            slack = Math.min(slack, 2.0F * PX - FOOTING - DECK);
        }
        this.footingTop = FOOTING + slack;
        this.deckTop = footingTop + DECK;
        this.shaftY = deckTop + STANDOFF + lp / 2.0F;
        this.shaftRadius = Math.max(1.5F, Math.round(n16 * 0.035F * 2.0F) / 2.0F);
        float generatorWidthPx = even(Math.min(lp * 0.86F, region - 4.0F));

        // Along: the sections at their natural lengths for their diameters, as many low-pressure casings as fit (up to
        // three), a bearing gap before each section; then everything scaled together to fill the length.
        float gap = Math.clamp(even(lp * 0.18F), MIN_GAP, MAX_GAP);
        float generatorNatural = generatorWidthPx * 1.6F;
        int lows = 1;
        if (tier == 3) {
            for (int n = MAX_LOW_PRESSURE; n >= 1; n--) {
                float sections = hp * 1.1F + ip * 1.2F + n * lp * 1.15F + generatorNatural, gaps = (n + 3) * gap + 1.0F;
                // Squeezed to fit, the high-pressure casing still at least two blocks long.
                float squeeze = (sections + gaps) / total, scale = (total - gaps) / sections;
                if (squeeze <= 1.0F || squeeze <= LOW_SQUEEZE && hp * 1.1F * scale >= 2.0F * PX || n == 1) {
                    lows = n;
                    break;
                }
            }
        }
        this.lowCount = tier == 3 ? lows : tier == 2 ? 1 : 0;
        List<Part> partList = new ArrayList<>();
        List<Float> naturals = new ArrayList<>();
        if (tier == 1) {
            partList.add(Part.TURBINE);
            naturals.add(lp * 1.2F);
        } else {
            partList.add(Part.HIGH_PRESSURE);
            naturals.add(hp * 1.1F);
            if (tier == 3) {
                partList.add(Part.INTERMEDIATE);
                naturals.add(ip * 1.2F);
            }
            for (int i = 0; i < (tier == 3 ? lows : 1); i++) {
                partList.add(Part.LOW_PRESSURE);
                naturals.add(lp * (tier == 3 ? 1.15F : 1.0F));
            }
        }
        naturals.add(generatorNatural);
        int gapCount = partList.size() + 1;
        // Short sets can't have full gaps: the gaps give way first, down to MIN_GAP.
        float least = partList.size() * MIN_CASING + MIN_GENERATOR;
        gap = Math.max(MIN_GAP, Math.min(gap, (float) Math.floor((total - 1.0F - least) / gapCount)));
        float[] weights = new float[naturals.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = naturals.get(i);
        }
        float[] lengths = share(total - gapCount * gap - 1.0F, weights, partList.size());

        // The steam end's and crossover's sizes, so the windows can keep clear of them.
        float pipeRadius = crossoverDiameter / 2.0F;
        List<Span> builtGaps = new ArrayList<>();
        List<Span> spans = new ArrayList<>();
        float u = 0.0F;
        for (int i = 0; i < partList.size(); i++) {
            builtGaps.add(new Span(u, u + gap));
            u += gap;
            spans.add(new Span(u, u + lengths[i]));
            u += lengths[i];
        }
        builtGaps.add(new Span(u, u + gap));
        u += gap;
        this.gaps = List.copyOf(builtGaps);
        Span generatorSpan = new Span(u, total - 1.0F);

        // The steam chest on top of the steam end's casing, under the steam spot's block.
        Span firstSpan = spans.get(0);
        float firstDiameter = partList.get(0) == Part.HIGH_PRESSURE ? hp : lp;
        float firstRadius = firstDiameter / 2.0F;
        this.steamU = (int) Math.floor(firstSpan.middle() / PX);
        this.steamX = (int) Math.floor(shaftX / PX);
        float chestSize = Math.clamp(even(firstDiameter * 0.5F), 8.0F, 14.0F);
        float chestAlong = Math.min(chestSize, firstSpan.length() - 2.0F);
        float chestU = Math.clamp((steamU + 0.5F) * PX, firstSpan.u0() + 1.0F + chestAlong / 2.0F, firstSpan.u1() - 1.0F - chestAlong / 2.0F);
        // On the shaft's line, its foot between the casing's outer surface and its inner wall (so it never shows in a
        // window).
        float chestX = shaftX;
        // No wider than the top facets between the two windows, so it never reaches down over them.
        float chestWidth = Math.min(chestSize, Math.max(4.0F, 2.0F * (float) Math.floor((firstRadius - GLASS) * Math.cos(3.0 * Math.PI / 8.0) - 0.5)));
        float chestBottom = shaftY + firstRadius - WALL + 0.05F;
        float room = h16 - 1.0F - 3.0F - chestBottom;
        float chestHeight = Math.max(2.0F, Math.min(Math.clamp(Math.round(firstDiameter * 0.25F), 4.0F, 7.0F), room));
        this.steamChest = new Box(chestX - chestWidth / 2.0F, chestBottom, chestU - chestAlong / 2.0F,
                chestX + chestWidth / 2.0F, chestBottom + chestHeight, chestU + chestAlong / 2.0F);

        // The valve housings beside the steam end's casing, where they fit.
        List<Valve> builtValves = new ArrayList<>();
        float valveRadius = Math.clamp(Math.round(firstDiameter * 0.16F), 3.0F, 6.0F);
        float valveOffset = firstRadius + 3.0F + valveRadius;
        if (shaftX - valveOffset - valveRadius >= regionX0 + 1.0F && shaftX + valveOffset + valveRadius <= regionX1 - 1.0F
                && firstSpan.length() >= 2 * valveRadius + 4.0F) {
            float valveBottom = Math.max(deckTop + 3.0F, shaftY - firstRadius * 0.4F);
            float valveTop = Math.min(shaftY + firstRadius * 0.55F, h16 - 8.0F);
            float pipeY = Math.min(shaftY + firstRadius * 0.35F, valveTop - 2.0F);
            if (valveTop - valveBottom >= 4.0F) {
                // At the inlet end, where the steam comes in, so the windows have the rest of the casing.
                float pipe = Math.max(1.5F, valveRadius * 0.5F);
                float valveU = firstSpan.u0() + Math.min(3.0F, firstSpan.length() * 0.15F) + pipe + 1.5F;
                for (int side = -1; side <= 1; side += 2) {
                    builtValves.add(new Valve(shaftX + side * valveOffset, valveU, valveRadius, valveBottom, valveTop, pipeY,
                            shaftX + side * firstRadius * 0.6F));
                }
            }
        }

        // The crossover: up from near the intermediate casing's outlet end, along over the low-pressure casings, and
        // down into the middle of each.
        float riseU = 0.0F;
        if (tier == 3) {
            Span ipSpan = spans.get(1);
            float taper = Math.min(3.0F, ipSpan.length() * 0.15F);
            riseU = ipSpan.u1() - taper - pipeRadius - 2.0F;
        }

        // The casings, their windows clear of what enters them.
        List<Casing> builtCasings = new ArrayList<>();
        List<Float> drops = new ArrayList<>();
        for (int i = 0; i < partList.size(); i++) {
            Part part = partList.get(i);
            Span span = spans.get(i);
            float diameter = part == Part.HIGH_PRESSURE ? hp : part == Part.INTERMEDIATE ? ip : lp;
            List<Span> blocked = new ArrayList<>();
            if (i == 0) {
                for (Valve valve : builtValves) {
                    float pr = Math.max(1.5F, valve.radius() * 0.5F);
                    blocked.add(new Span(valve.u() - pr - 1.0F, valve.u() + pr + 1.0F));
                }
                // The chest too, where it reaches down over the windows' top edges (wider than the top facets between
                // them, and its bottom below their top corners).
                float glass = firstRadius - GLASS, corner = (float) (3.0 * Math.PI / 8.0);
                if (chestWidth / 2.0F > glass * (float) Math.cos(corner) - 0.5F && chestBottom - shaftY < glass * (float) Math.sin(corner) + 0.05F) {
                    blocked.add(new Span(steamChest.u0() - 1.0F, steamChest.u1() + 1.0F));
                }
            }
            if (tier == 3 && part == Part.LOW_PRESSURE) {
                drops.add(span.middle());
            }
            Casing built = casing(part, span, diameter, ip, tier, blocked);
            if (i == 0 && built.windows().isEmpty() && !builtValves.isEmpty()) {
                // A short steam end with no room for a window beside the valve pipes: it goes without the valves.
                blocked.subList(0, builtValves.size()).clear();
                builtValves.clear();
                built = casing(part, span, diameter, ip, tier, blocked);
            }
            builtCasings.add(built);
        }
        this.casings = List.copyOf(builtCasings);
        this.valves = List.copyOf(builtValves);

        // The generator: a housing a little narrower than the low-pressure casing, its top under the terminal box; on a
        // long set an exciter beyond it.
        this.generatorWidth = generatorWidthPx;
        this.generatorTop = Math.max(shaftY + 4.0F, Math.min(shaftY + lp * 0.42F, h16 - 7.0F));
        if (generatorSpan.length() >= EXCITER_FROM) {
            float exciterLength = Math.clamp(even(generatorSpan.length() * 0.16F), 8.0F, 16.0F);
            this.exciter = new Span(generatorSpan.u1() - exciterLength, generatorSpan.u1());
            this.generator = new Span(generatorSpan.u0(), generatorSpan.u1() - exciterLength - 2.0F);
        } else {
            this.exciter = null;
            this.generator = generatorSpan;
        }
        float terminalLength = Math.clamp(even(generator.length() * 0.22F), 6.0F, 14.0F);
        float terminalWidth = even(generatorWidth * 0.45F);
        this.terminalBox = new Box(shaftX - terminalWidth / 2.0F, generatorTop, generator.u1() - 3.0F - terminalLength,
                shaftX + terminalWidth / 2.0F, Math.min(generatorTop + 4.0F, h16 - 3.0F), generator.u1() - 3.0F);

        // An exhaust neck under each low-pressure casing (the only casing of a one-casing set), down to the deck or on
        // down between the columns; its top meets the casing's underside.
        List<Box> necks = new ArrayList<>();
        for (Casing casing : casings) {
            if (casing.part() != Part.LOW_PRESSURE && casing.part() != Part.TURBINE) {
                continue;
            }
            float neckLength = even(Math.clamp(casing.span().length() * 0.4F, 8.0F, Math.max(8.0F, casing.span().length() - 6.0F)));
            float neckWidth = even(casing.diameter() * 0.6F);
            float neckBottom = hasColumns() ? 1.0F : deckTop;
            necks.add(new Box(shaftX - neckWidth / 2.0F, neckBottom, casing.span().middle() - neckLength / 2.0F,
                    shaftX + neckWidth / 2.0F, shaftY + underside(casing.radius(), neckWidth / 2.0F), casing.span().middle() + neckLength / 2.0F));
        }
        this.exhaustNecks = List.copyOf(necks);
        Casing last = casings.get(casings.size() - 1);
        this.exhaustU = (int) Math.floor(last.span().middle() / PX);
        this.exhaustX = steamX;
        this.energyX = steamX;
        this.energyY = (int) Math.floor((deckTop + generatorTop) / 2.0F / PX);

        if (tier == 3) {
            Casing intermediate = casings.get(1);
            float runY = shaftY + last.radius() + 2.0F + crossoverDiameter / 2.0F;
            float[] dropUs = new float[drops.size()];
            for (int i = 0; i < dropUs.length; i++) {
                dropUs[i] = drops.get(i);
            }
            this.crossover = new Crossover(riseU, dropUs, shaftY + intermediate.radius() - WALL + 0.05F, runY,
                    shaftY + last.radius() - WALL + 0.05F, crossoverDiameter);
        } else {
            this.crossover = null;
        }
        this.walkway = hasWalkway ? new Walkway(walkX0, walkX1, deckTop, railHeight, walkwayHigh) : null;
    }

    private static Casing casing(Part part, Span span, float diameter, float intermediate, int tier, List<Span> blocked) {
        boolean doubleFlow = part == Part.LOW_PRESSURE && tier == 3;
        float end = doubleFlow ? even(Math.max(intermediate, diameter * 0.62F)) : even(diameter * 0.7F);
        float length = span.length();
        float taper = doubleFlow ? length * DOUBLE_FLOW_TAPER : Math.min(3.0F, length * 0.15F);
        float a = span.u0() + taper, b = span.u1() - taper;
        // Clear of the hoops at the taper points (1 px each way), with a pixel of casing between.
        float margin = Math.max(2.0F, (b - a) * 0.08F);
        List<Span> windows = cut(new Span(a + margin, b - margin), blocked);
        float windowLength = 0.0F;
        for (Span window : windows) {
            windowLength += window.length();
        }
        float pitch = switch (part) {
            case HIGH_PRESSURE -> 3.0F;
            case INTERMEDIATE -> 3.25F;
            default -> 3.5F;
        };
        int rows = Math.max(2, Math.round(windowLength / pitch));
        float inlet = doubleFlow ? span.middle() : span.u0();
        float outlet = doubleFlow ? span.u1() : span.u1();
        return switch (part) {
            case HIGH_PRESSURE -> new Casing(part, span, diameter, end, false, windows, rows, 24, 0.50F, 0.84F, inlet, outlet);
            case INTERMEDIATE -> new Casing(part, span, diameter, end, false, windows, Math.max(rows, 3), 30, 0.44F, 0.88F, inlet, outlet);
            case LOW_PRESSURE -> new Casing(part, span, diameter, end, doubleFlow, windows, Math.max(rows, 4), 36, 0.36F, 0.94F, inlet, outlet);
            default -> new Casing(part, span, diameter, end, false, windows, rows, 30, 0.42F, 0.90F, inlet, outlet);
        };
    }

    // The parts of a stretch not blocked, at least MIN_WINDOW long.
    private static List<Span> cut(Span window, List<Span> blocked) {
        List<Span> pieces = new ArrayList<>();
        pieces.add(window);
        for (Span block : blocked) {
            List<Span> next = new ArrayList<>();
            for (Span piece : pieces) {
                if (block.u1() <= piece.u0() || block.u0() >= piece.u1()) {
                    next.add(piece);
                    continue;
                }
                if (block.u0() > piece.u0()) {
                    next.add(new Span(piece.u0(), block.u0()));
                }
                if (block.u1() < piece.u1()) {
                    next.add(new Span(block.u1(), piece.u1()));
                }
            }
            pieces = next;
        }
        pieces.removeIf(piece -> piece.length() < MIN_WINDOW);
        return List.copyOf(pieces);
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

    // How far below the shaft (negative) a casing's outer surface is dx across from it: the bottom of its 16-sided
    // outline (a corner straight down).
    public static float underside(float radius, float dx) {
        float best = 0.0F;
        boolean found = false;
        double step = 2.0 * Math.PI / CASING_SIDES;
        for (int f = 0; f < CASING_SIDES; f++) {
            float x0 = radius * (float) Math.cos(f * step), y0 = radius * (float) Math.sin(f * step);
            float x1 = radius * (float) Math.cos((f + 1) * step), y1 = radius * (float) Math.sin((f + 1) * step);
            if (dx < Math.min(x0, x1) - 1.0E-4F || dx > Math.max(x0, x1) + 1.0E-4F || Math.abs(x1 - x0) < 1.0E-5F) {
                continue;
            }
            float y = y0 + (y1 - y0) * (dx - x0) / (x1 - x0);
            if (!found || y < best) {
                best = y;
                found = true;
            }
        }
        return found ? best : 0.0F;
    }

    private static float even(float value) {
        return 2.0F * Math.round(value / 2.0F);
    }

    // --- Parts the model and the hitbox share ---

    // Half the length of the coupling in gap i (gap 0, the front standard's, has none): the generator's is longer.
    public float couplingHalf(int gap) {
        return gap == gaps.size() - 1 ? 1.5F : 1.0F;
    }

    // The bearing pedestals, from the deck to the top of their bearing caps: the front standard filling the first gap;
    // in each gap after it one for each rotor's bearing, either side of the coupling (one on the near side when the gap
    // is too short for two).
    public List<Box> pedestals() {
        List<Box> out = new ArrayList<>();
        float top = shaftY + shaftRadius + 2.0F + 2.0F;
        for (int i = 0; i < gaps.size(); i++) {
            Span gap = gaps.get(i);
            float neighbour = casings.get(Math.min(i, casings.size() - 1)).diameter();
            if (i > 0) {
                neighbour = Math.min(neighbour, casings.get(i - 1).diameter());
            }
            float pw = Math.clamp(2.0F * Math.round(neighbour * 0.45F / 2.0F), 8.0F, 20.0F);
            float x0 = shaftX - pw / 2.0F, x1 = shaftX + pw / 2.0F;
            if (i == 0) {
                out.add(new Box(x0, deckTop, gap.u0() + 1.0F, x1, top, gap.u1() - 1.0F));
                continue;
            }
            float coupling = couplingHalf(i);
            float room = (gap.length() - 2.0F * coupling - 2.0F) / 2.0F - 1.0F;
            if (room < 3.0F) {
                out.add(new Box(x0, deckTop, gap.u0() + 1.0F, x1, top, gap.u0() + 1.0F + Math.max(3.0F, Math.round(gap.length() * 0.4F))));
                continue;
            }
            float p = Math.min(room, 7.0F);
            out.add(new Box(x0, deckTop, gap.middle() - coupling - 1.0F - p, x1, top, gap.middle() - coupling - 1.0F));
            out.add(new Box(x0, deckTop, gap.middle() + coupling + 1.0F, x1, top, gap.middle() + coupling + 1.0F + p));
        }
        return out;
    }

    // The columns under a raised deck (none on a deck on a solid base), from the ground to under the deck, without
    // their feet and caps: a pair at each bearing gap and one under the generator's far end.
    public List<Box> columns() {
        List<Box> out = new ArrayList<>();
        if (!hasColumns()) {
            return out;
        }
        float w = widthPx(), t = lengthPx();
        float cw = Math.clamp(2.0F * Math.round(w * 0.075F), 6.0F, 12.0F), cl = Math.min(8.0F, cw);
        List<Float> stations = new ArrayList<>();
        for (Span gap : gaps) {
            stations.add(gap.middle());
        }
        stations.add(generator.u1() - 5.0F);
        for (float station : stations) {
            float u0 = Math.clamp(station - cl / 2.0F, 2.0F, t - 2.0F - cl);
            for (float x0 : new float[] { 2.0F, w - 2.0F - cw }) {
                out.add(new Box(x0, 0.0F, u0, x0 + cw, footingTop, u0 + cl));
            }
        }
        return out;
    }

    // The exciter's housing, without its sole plate and ribs, or null.
    public @Nullable Box exciterHousing() {
        if (exciter == null) {
            return null;
        }
        float eh = Math.max(4.0F, (float) Math.floor(generatorWidth / 2.0F * 0.62F));
        float top = Math.min(generatorTop - 2.0F, shaftY + eh * 0.9F);
        return new Box(shaftX - eh, deckTop + 2.0F, exciter.u0() + 0.5F, shaftX + eh, top, exciter.u1());
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
        return generator.contains(u) || u >= generator.u1() || exciter != null && exciter.contains(u) ? Part.GENERATOR : Part.BEARING;
    }

    // Which of several low-pressure casings a block's slice is in (1 from the steam end), or 0 when there's only one or
    // it isn't in one.
    public int lowNumberAt(int blockU) {
        if (lowCount < 2) {
            return 0;
        }
        float u = (blockU + 0.5F) * PX;
        int number = 0;
        for (Casing casing : casings) {
            if (casing.part() == Part.LOW_PRESSURE) {
                number++;
                if (casing.span().contains(u)) {
                    return number;
                }
            }
        }
        return 0;
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

    // 1: one casing, 2: high and low pressure, 3: high, intermediate and one to three double-flow low pressure.
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

    // The last low-pressure casing's exhaust neck (under the exhaust spot).
    public Box exhaustNeck() {
        return exhaustNecks.get(exhaustNecks.size() - 1);
    }

    public List<Box> exhaustNecks() {
        return exhaustNecks;
    }

    public @Nullable Span exciter() {
        return exciter;
    }

    // How many low-pressure casings it has (0 for a one-casing set).
    public int lowCount() {
        return lowCount;
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
