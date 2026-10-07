/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.arcforge.steam.TurbineLayout;

// The formed Steam Turbine Array's model, built from its TurbineLayout (see SteamTurbineArrayRenderer): the still parts
// once (and again when its ports change), the rotor every frame at its angle.
//
// Texturing follows docs/TEXTURE_STYLE.md "3D models": one sheet (block/steam_turbine_array/turbine, SHEET_W x SHEET_H)
// for the whole model at 1 texel per px. The model's size follows its structure, so it can't have a fixed unwrap:
// each face is cut into nine at its own size from a painted 9-slice panel (corners and edges at 1 texel per px, so every
// face keeps its own 1 px lit top/left and dark bottom/right edge and, at 9 px and up, its corner rivets; the middles
// are one flat tone, which can stretch). Faces under 9 px drop the rivets, faces under 3 px are one flat tone. Round
// parts are polygons in one flat tone, so the game's face shading gives the curve; their caps are flat concentric rings
// (lit on the top-left half, a step darker on the bottom-right). The walkway's grating is a 4 px tile laid at 1:1.
//
// Output: per vertex x, y, z (blocks from the structure's minimum corner), u, v (0-1 across the sheet), nx, ny, nz;
// four vertices a quad. Solid and cutout (the window glass) quads go in separate lists.
public final class TurbineMesh {
    public static final int SHEET_W = 128, SHEET_H = 48;
    private static final float PX = TurbineLayout.PX;
    private static final int SIDES = 16, PIPE_SIDES = 8;
    // The inspection window takes these two facets on each side (counted from the flange line at angle 0).
    private static final int WINDOW_FIRST = 1, WINDOW_LAST = 2;
    // The collar of a port nozzle, across (px): the port plate's collar (x3..12).
    private static final float COLLAR = 10.0F;
    // Blade pitch against the axis.
    private static final double PITCH = Math.toRadians(35.0);

    public enum Kind { FLAT, PANEL, TILED, BANDED }

    // A painted area of the sheet: a flat tone (FLAT, its middle used); a panel (PANEL) cut into columns and rows,
    // each either fixed (drawn at 1 texel per px) or stretching (a band of one tone along it, sharing what's left of
    // the face by weight); or a tile laid at 1:1 (TILED). BANDED paints aren't on the sheet: a round part painted with
    // one gets each facet in the band tone (or the casing strip) for how that facet faces the light.
    public enum Paint {
        DEEP(0, 0), DARK(4, 0), SHADE(8, 0), FACE_SHADE(12, 0), FACE(16, 0), MID(20, 0), LIT(24, 0), RIVET(28, 0),
        GEN_DARK(32, 0), GEN_FACE(36, 0), GEN_MID(40, 0), GEN_LIT(44, 0), FE_LIT(48, 0), FE(52, 0), FE_DARK(56, 0), BLADE(60, 0),
        BLADE_BACK(0, 4),
        GRATE(4, 4, 4, 4, Kind.TILED),
        STRAP(8, 4, 4, 3, Kind.TILED),
        VENT(12, 4, 4, 4, Kind.TILED),
        PLATE(0, 8, 16, 16, true, Cuts.SPLIT, Cuts.CORNERS),
        PLAIN(16, 8, 16, 16, false, Cuts.SPLIT_PLAIN, Cuts.EDGES),
        DECK(32, 8, 16, 16, true, Cuts.RECESS, Cuts.RECESS),
        FOOTING(48, 8, 16, 16, false, Cuts.SPLIT_PLAIN, Cuts.EDGES),
        GEN_PLATE(64, 8, 16, 16, true, Cuts.SPLIT, Cuts.CORNERS),
        GEN_PLAIN(80, 8, 16, 16, false, Cuts.SPLIT_PLAIN, Cuts.EDGES),
        FE_BAND(96, 8, 16, 16, false, Cuts.THIN, Cuts.THIN),
        GLASS(112, 8, 12, 12, false, new int[][] { { 5, 0 }, { 2, 100 }, { 5, 0 } }, new int[][] { { 5, 0 }, { 2, 100 }, { 5, 0 } }),
        CASING_0(0, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        CASING_1(8, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        CASING_2(16, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        CASING_3(24, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        CASING_4(32, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        CASING_5(40, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        // Markers: tones by facing (pipes, bosses, valve bodies), or the casing strips by facing (the casings' shells).
        BANDED(0, 0, 0, 0, Kind.BANDED),
        CASING(0, 0, 0, 0, Kind.BANDED);

        public final int x, y, w, h;
        public final Kind kind;
        // Riveted panels drop to their plain panel on faces under 9 px.
        public final boolean riveted;
        // Columns and rows (rows top down): {texels, stretch weight}, weight 0 for fixed.
        final int[][] cols, rows;

        Paint(int x, int y) {
            this(x, y, 4, 4, Kind.FLAT);
        }

        Paint(int x, int y, int w, int h, Kind kind) {
            this(x, y, w, h, kind, false, new int[0][], new int[0][]);
        }

        Paint(int x, int y, int w, int h, boolean riveted, int[][] cols, int[][] rows) {
            this(x, y, w, h, Kind.PANEL, riveted, cols, rows);
        }

        Paint(int x, int y, int w, int h, Kind kind, boolean riveted, int[][] cols, int[][] rows) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.kind = kind;
            this.riveted = riveted;
            this.cols = cols;
            this.rows = rows;
        }

        // What a face too small for this panel uses instead: the plain panel, then a flat tone.
        Paint fallback() {
            return switch (this) {
                case PLATE, DECK -> PLAIN;
                case PLAIN -> FACE;
                case FOOTING -> SHADE;
                case GEN_PLATE -> GEN_PLAIN;
                case GEN_PLAIN -> GEN_FACE;
                case FE_BAND -> FE;
                case GLASS -> LIT;
                case CASING_0 -> DARK;
                case CASING_1 -> SHADE;
                case CASING_2 -> FACE_SHADE;
                case CASING_3 -> FACE;
                case CASING_4 -> MID;
                case CASING_5 -> LIT;
                default -> FACE;
            };
        }

        // The least a face needs across (or down) for this panel: its fixed texels and a pixel for each stretch.
        float least(boolean across) {
            float least = 0.0F;
            for (int[] segment : across ? cols : rows) {
                least += segment[1] == 0 ? segment[0] : 1.0F;
            }
            return least;
        }
    }

    // The band tones, darkest first, and the casing strips in the same order.
    private static final Paint[] TONES = { Paint.DARK, Paint.SHADE, Paint.FACE_SHADE, Paint.FACE, Paint.MID, Paint.LIT };
    private static final Paint[] STRIPS = { Paint.CASING_0, Paint.CASING_1, Paint.CASING_2, Paint.CASING_3, Paint.CASING_4, Paint.CASING_5 };
    // The light the bands are painted for (in the turbine's frame): from above, a little toward x = 0 and the steam end.
    private static final float[] LIGHT = normalize(new float[] { -0.35F, 0.85F, -0.4F });

    // The panel cuts (in a class of their own, so Paint can use them while it's being set up).
    private static final class Cuts {
        private Cuts() {}

        static final int[][] SPLIT = { { 4, 0 }, { 6, 60 }, { 2, 40 }, { 4, 0 } };
        static final int[][] CORNERS = { { 4, 0 }, { 8, 100 }, { 4, 0 } };
        static final int[][] SPLIT_PLAIN = { { 2, 0 }, { 8, 60 }, { 4, 40 }, { 2, 0 } };
        static final int[][] EDGES = { { 2, 0 }, { 12, 100 }, { 2, 0 } };
        static final int[][] RECESS = { { 5, 0 }, { 6, 100 }, { 5, 0 } };
        static final int[][] THIN = { { 1, 0 }, { 14, 100 }, { 1, 0 } };
        static final int[][] STRIP_ACROSS = { { 3, 50 }, { 1, 0 }, { 4, 50 } };
        static final int[][] STRIP_ALONG = { { 4, 0 }, { 4, 100 }, { 4, 0 } };
    }

    // A port on the structure: its block (along, up, across) and the face it's on, as a unit normal in the turbine's
    // frame (x, y, u).
    public record Port(int u, int y, int x, int nx, int ny, int nu) {}

    private final TurbineLayout layout;
    private final boolean alongX;
    private final float widthPx;
    private Floats solid = new Floats(), cutout = new Floats();
    // While building the rotor: its angle (radians) about the shaft.
    private boolean rotating;
    private double cos = 1.0, sin = 0.0;
    // What the nozzles stop at: boxes {x0, y0, u0, x1, y1, u1} and cylinders along u {cx, cy, r, u0, u1}.
    private final List<float[]> solidBoxes = new ArrayList<>();
    private final List<float[]> solidCylinders = new ArrayList<>();

    public TurbineMesh(TurbineLayout layout, boolean alongX) {
        this.layout = layout;
        this.alongX = alongX;
        this.widthPx = layout.widthPx();
    }

    // --- The model ---

    // The still parts and the nozzles of these ports: {solid, cutout}.
    public float[][] buildStatic(List<Port> ports) {
        solid = new Floats();
        cutout = new Floats();
        rotating = false;
        solidBoxes.clear();
        solidCylinders.clear();
        plinth();
        if (layout.walkway() != null) {
            walkway(layout.walkway());
        }
        for (int i = 0; i < layout.gaps().size(); i++) {
            pedestal(i);
        }
        for (TurbineLayout.Casing casing : layout.casings()) {
            casing(casing);
        }
        steamEnd();
        exhaustNeck();
        if (layout.crossover() != null) {
            crossover(layout.crossover());
        }
        generator();
        for (Port port : ports) {
            nozzle(port);
        }
        return new float[][] { solid.toArray(), cutout.toArray() };
    }

    // The parts that turn with the rotor (shaft, couplings, the drums and blades seen through the windows), at angle
    // degrees.
    public float[] buildRotor(float angle) {
        solid = new Floats();
        cutout = new Floats();
        rotating = true;
        cos = Math.cos(Math.toRadians(angle));
        sin = Math.sin(Math.toRadians(angle));
        float sx = layout.shaftX(), sy = layout.shaftY(), r = layout.shaftRadius();
        // The shaft, from the front standard into the generator.
        prismU(sx, sy, new float[][] { { 1.5F, r }, { layout.generator().u0() + 3.0F, r } }, PIPE_SIDES, 0.0, Paint.MID, Paint.MID, Paint.MID, null);
        // A coupling in each gap after the first, on the side toward the next section.
        List<TurbineLayout.Span> gaps = layout.gaps();
        for (int i = 1; i < gaps.size(); i++) {
            TurbineLayout.Span gap = gaps.get(i);
            boolean generator = i == gaps.size() - 1;
            float cr = r * (generator ? 2.8F : 2.3F);
            float c1 = gap.u1() - 1.0F, c0 = c1 - (generator ? 3.0F : 2.0F);
            prismU(sx, sy, new float[][] { { c0, cr }, { c1, cr } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.FACE, Paint.MID, Paint.SHADE, null);
            // Bolt heads on the coupling's face toward the pedestal, so its turning shows.
            int bolts = generator ? 6 : 4;
            for (int b = 0; b < bolts; b++) {
                double a = 2.0 * Math.PI * b / bolts;
                float bx = sx + (float) Math.cos(a) * cr * 0.65F, by = sy + (float) Math.sin(a) * cr * 0.65F;
                box(bx - 0.5F, by - 0.5F, c0 - 0.75F, bx + 0.5F, by + 0.5F, c0, Paint.RIVET, Paint.RIVET, Paint.RIVET, 0);
            }
        }
        for (TurbineLayout.Casing casing : layout.casings()) {
            rotorInWindow(casing);
        }
        return solid.toArray();
    }

    // --- The plinth and walkway ---

    private void plinth() {
        float w = widthPx, t = layout.lengthPx(), foot = layout.footingTop(), deck = layout.deckTop();
        TurbineLayout.Box neck = layout.exhaustNeck();
        if (layout.hasColumns()) {
            // The deck with a hole for the exhaust neck, on stepped columns.
            deckSlab(0.0F, 0.0F, w, neck.u0());
            deckSlab(0.0F, neck.u1(), w, t);
            deckSlab(0.0F, neck.u0(), neck.x0(), neck.u1());
            deckSlab(neck.x1(), neck.u0(), w, neck.u1());
            float cw = Math.clamp(2.0F * Math.round(w * 0.075F), 6.0F, 12.0F);
            List<Float> stations = new ArrayList<>();
            for (TurbineLayout.Span gap : layout.gaps()) {
                stations.add(gap.middle());
            }
            stations.add(layout.generator().u1() - 5.0F);
            for (float station : stations) {
                float cl = Math.min(8.0F, cw);
                float u0 = Math.clamp(station - cl / 2.0F, 2.0F, t - 2.0F - cl);
                for (float x0 : new float[] { 2.0F, w - 2.0F - cw }) {
                    column(x0, u0, cw, cl, foot);
                }
            }
            solidBoxes.add(new float[] { 0, foot, 0, w, deck, t });
        } else {
            // A stepped base: the full footprint, a recessed band over it, then the deck.
            float lower = (float) Math.ceil(foot / 2.0F);
            box(0.0F, 0.0F, 0.0F, w, lower, t, Paint.FOOTING, Paint.FOOTING, Paint.FOOTING, 0);
            box(1.0F, lower, 1.0F, w - 1.0F, foot, t - 1.0F, Paint.FOOTING, Paint.FOOTING, Paint.FOOTING, 0);
            deckSlab(0.0F, 0.0F, w, t);
            solidBoxes.add(new float[] { 0, 0, 0, w, deck, t });
        }
    }

    // A piece of the deck slab, its top in one-block plates.
    private void deckSlab(float x0, float u0, float x1, float u1) {
        if (x1 - x0 <= 0.01F || u1 - u0 <= 0.01F) {
            return;
        }
        float foot = layout.footingTop(), deck = layout.deckTop();
        box(x0, foot, u0, x1, deck, u1, Paint.STRAP, null, Paint.FOOTING, SKIP_UP);
        for (float u = u0; u < u1 - 0.01F; u += PX) {
            float end = Math.min(u1, (float) Math.floor(u / PX) * PX + PX);
            face(new float[] { x0, deck, end }, new float[] { 1, 0, 0 }, new float[] { 0, 0, -1 }, x1 - x0, end - u, Paint.DECK, false);
            u = end - PX;
        }
    }

    // A stepped column from the ground to under the deck: a wide foot, the column, a cap.
    private void column(float x0, float u0, float cw, float cl, float top) {
        box(x0 - 1.0F, 0.0F, u0 - 1.0F, x0 + cw + 1.0F, 2.0F, u0 + cl + 1.0F, Paint.FOOTING, Paint.FOOTING, Paint.FOOTING, 0);
        box(x0, 2.0F, u0, x0 + cw, top - 1.0F, u0 + cl, Paint.PLAIN, null, null, 0);
        box(x0 - 0.5F, top - 1.0F, u0 - 0.5F, x0 + cw + 0.5F, top, u0 + cl + 0.5F, Paint.MID, Paint.MID, Paint.MID, 0);
        solidBoxes.add(new float[] { x0 - 1.0F, 0.0F, u0 - 1.0F, x0 + cw + 1.0F, top, u0 + cl + 1.0F });
    }

    private void walkway(TurbineLayout.Walkway walk) {
        float t = layout.lengthPx(), floor = walk.floorY();
        // The grating, on the deck.
        box(walk.x0(), floor, 1.0F, walk.x1(), floor + 1.0F, t - 1.0F, Paint.DARK, Paint.GRATE, Paint.DARK, 0);
        // The railing on its outer edge: posts every block, a top and a middle rail.
        float x = walk.high() ? walk.x1() - 1.0F : walk.x0();
        float top = floor + 1.0F + walk.railHeight();
        for (float u = 2.0F; u <= t - 3.0F; u += PX) {
            box(x, floor + 1.0F, u, x + 1.0F, top - 1.0F, u + 1.0F, Paint.LIT, Paint.LIT, Paint.LIT, 0);
        }
        box(x, floor + 1.0F, t - 3.0F, x + 1.0F, top - 1.0F, t - 2.0F, Paint.LIT, Paint.LIT, Paint.LIT, 0);
        box(x - 0.25F, top - 1.0F, 2.0F, x + 1.25F, top, t - 2.0F, Paint.LIT, Paint.LIT, Paint.SHADE, 0);
        float mid = floor + 1.0F + walk.railHeight() / 2.0F;
        box(x, mid - 0.5F, 2.0F, x + 1.0F, mid + 0.5F, t - 2.0F, Paint.MID, Paint.MID, Paint.SHADE, 0);
        solidBoxes.add(new float[] { walk.x0(), floor, 1.0F, walk.x1(), floor + 1.0F, t - 1.0F });
    }

    // --- The turbine ---

    // A bearing pedestal in gap i, with its bearing cap on top.
    private void pedestal(int i) {
        TurbineLayout.Span gap = layout.gaps().get(i);
        List<TurbineLayout.Casing> casings = layout.casings();
        float neighbour = casings.get(Math.min(i, casings.size() - 1)).diameter();
        if (i > 0) {
            neighbour = Math.min(neighbour, casings.get(i - 1).diameter());
        }
        float pw = Math.clamp(2.0F * Math.round(neighbour * 0.45F / 2.0F), 8.0F, 20.0F);
        float u0, u1;
        if (i == 0) {
            u0 = gap.u0() + 1.0F;
            u1 = gap.u1() - 1.0F;
        } else {
            float p = Math.max(4.0F, Math.round(gap.length() * 0.45F));
            u0 = gap.u0() + 1.0F;
            u1 = u0 + p;
        }
        float sx = layout.shaftX(), sy = layout.shaftY();
        float top = sy + layout.shaftRadius() + 2.0F;
        box(sx - pw / 2.0F, layout.deckTop(), u0, sx + pw / 2.0F, top, u1, Paint.PLATE, Paint.PLAIN, null, 0);
        float cap = pw * 0.6F;
        box(sx - cap / 2.0F, top, u0 + 0.5F, sx + cap / 2.0F, top + 2.0F, u1 - 0.5F, Paint.PLAIN, Paint.MID, null, 0);
        solidBoxes.add(new float[] { sx - pw / 2.0F, layout.deckTop(), u0, sx + pw / 2.0F, top + 2.0F, u1 });
    }

    // A casing: the round shell split along the middle by a bolted flange, hoops where its ends taper, its end rings,
    // and an inspection window on each side (glass over a dark inside; the rotor in it is drawn with the rotor).
    private void casing(TurbineLayout.Casing casing) {
        float sx = layout.shaftX(), sy = layout.shaftY();
        float[][] profile = casing.profile();
        TurbineLayout.Span window = casing.window();
        boolean hasWindow = window.length() > 0.0F;
        // Cut the full-radius part at the window's ends.
        List<float[]> points = new ArrayList<>(Arrays.asList(profile));
        if (hasWindow) {
            points.add(2, new float[] { window.u0(), casing.radius() });
            points.add(3, new float[] { window.u1(), casing.radius() });
        }
        float[][] cut = points.toArray(new float[0][]);
        int windowSegment = hasWindow ? 2 : -1;
        prismU(sx, sy, cut, SIDES, 0.0, Paint.CASING, null, null,
                (segment, facet) -> segment == windowSegment && isWindowFacet(facet));
        // The end rings round the shaft gland.
        float gland = layout.shaftRadius() + 1.5F;
        ringU(sx, sy, profile[0][0], profile[0][1], gland * 1.8F, SIDES, 0.0, false, Paint.FACE, Paint.SHADE);
        ringU(sx, sy, profile[3][0], profile[3][1], gland * 1.8F, SIDES, 0.0, true, Paint.FACE, Paint.SHADE);
        prismU(sx, sy, new float[][] { { profile[0][0] - 1.0F, gland * 1.8F }, { profile[0][0], gland * 1.8F } }, SIDES, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
        prismU(sx, sy, new float[][] { { profile[3][0], gland * 1.8F }, { profile[3][0] + 1.0F, gland * 1.8F } }, SIDES, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
        // A bolt circle on each end ring.
        float ringIn = gland * 1.8F;
        for (int end = 0; end < 2; end++) {
            float[] point = profile[end == 0 ? 0 : 3];
            if (point[1] - ringIn < 3.0F) {
                continue;
            }
            float rb = (point[1] + ringIn) / 2.0F;
            for (int b = 0; b < 8; b++) {
                double a = Math.PI / 8.0 + b * Math.PI / 4.0;
                float bx = sx + (float) Math.cos(a) * rb, by = sy + (float) Math.sin(a) * rb;
                float u0 = end == 0 ? point[0] - 0.75F : point[0], u1 = end == 0 ? point[0] : point[0] + 0.75F;
                box(bx - 0.5F, by - 0.5F, u0, bx + 0.5F, by + 0.5F, u1, Paint.RIVET, Paint.RIVET, Paint.DARK, 0);
            }
        }
        // Hoops where the ends taper.
        for (float u : new float[] { profile[1][0], profile[2][0] }) {
            prismU(sx, sy, new float[][] { { u - 1.0F, casing.radius() + 0.75F }, { u + 1.0F, casing.radius() + 0.75F } }, SIDES, 0.0,
                    Paint.BANDED, Paint.MID, Paint.SHADE, null);
        }
        flange(casing);
        if (hasWindow) {
            windowCavity(casing);
        }
        for (int i = 0; i + 1 < profile.length; i++) {
            float r = Math.min(profile[i][1], profile[i + 1][1]) * (float) Math.cos(Math.PI / SIDES);
            solidCylinders.add(new float[] { sx, sy, r, profile[i][0], profile[i + 1][0] });
        }
    }

    private static boolean isWindowFacet(int facet) {
        int half = SIDES / 2;
        return facet >= WINDOW_FIRST && facet <= WINDOW_LAST || facet >= half - 1 - WINDOW_LAST && facet <= half - 1 - WINDOW_FIRST;
    }

    // The bolted flange along each side, at the split line, following the casing's outline, with a bolt every 4 px.
    private void flange(TurbineLayout.Casing casing) {
        float sx = layout.shaftX(), sy = layout.shaftY();
        float[][] profile = casing.profile();
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i + 1 < profile.length; i++) {
                float ua = profile[i][0], ub = profile[i + 1][0];
                if (ub - ua <= 0.01F) {
                    continue;
                }
                float ia = sx + side * (profile[i][1] - 0.5F), oa = sx + side * (profile[i][1] + 2.0F);
                float ib = sx + side * (profile[i + 1][1] - 0.5F), ob = sx + side * (profile[i + 1][1] + 2.0F);
                float y0 = sy - 1.0F, y1 = sy + 1.0F;
                // Top and bottom, then the outer edge.
                orient(new float[] { ia, y1, ua }, new float[] { oa, y1, ua }, new float[] { ob, y1, ub }, new float[] { ib, y1, ub }, new float[] { 0, 1, 0 }, Paint.MID);
                orient(new float[] { ia, y0, ua }, new float[] { oa, y0, ua }, new float[] { ob, y0, ub }, new float[] { ib, y0, ub }, new float[] { 0, -1, 0 }, Paint.SHADE);
                orient(new float[] { oa, y0, ua }, new float[] { oa, y1, ua }, new float[] { ob, y1, ub }, new float[] { ob, y0, ub }, new float[] { side, 0, 0 }, Paint.FACE);
            }
            // The flange's ends.
            for (int end = 0; end < 2; end++) {
                float[] point = profile[end == 0 ? 0 : profile.length - 1];
                float in = sx + side * (point[1] - 0.5F), out = sx + side * (point[1] + 2.0F);
                orient(new float[] { in, sy - 1.0F, point[0] }, new float[] { out, sy - 1.0F, point[0] }, new float[] { out, sy + 1.0F, point[0] },
                        new float[] { in, sy + 1.0F, point[0] }, new float[] { 0, 0, end == 0 ? -1 : 1 }, Paint.SHADE);
            }
            for (float u = casing.span().u0() + 2.0F; u <= casing.span().u1() - 2.5F; u += 4.0F) {
                float x = sx + side * (casing.radiusAt(u + 0.5F) + 1.0F);
                box(x - 0.5F, sy + 1.0F, u, x + 0.5F, sy + 1.75F, u + 1.0F, Paint.RIVET, Paint.RIVET, Paint.DARK, 0);
            }
        }
    }

    // Behind each window: the casing's inside wall and the window's two end walls, dark, and the glass on the chord
    // across the two missing facets.
    private void windowCavity(TurbineLayout.Casing casing) {
        float sx = layout.shaftX(), sy = layout.shaftY();
        float r = casing.radius();
        float inner = r - 0.5F;
        TurbineLayout.Span window = casing.window();
        double step = 2.0 * Math.PI / SIDES;
        // The inside wall, facing in.
        for (int facet = 0; facet < SIDES; facet++) {
            double a0 = facet * step, a1 = (facet + 1) * step;
            float[] p0 = { sx + (float) Math.cos(a0) * inner, sy + (float) Math.sin(a0) * inner, window.u0() };
            float[] p1 = { sx + (float) Math.cos(a1) * inner, sy + (float) Math.sin(a1) * inner, window.u0() };
            float[] p2 = { p1[0], p1[1], window.u1() };
            float[] p3 = { p0[0], p0[1], window.u1() };
            flat(p3, p2, p1, p0, Paint.DARK, false);
        }
        // The end walls, facing into the window.
        ringU(sx, sy, window.u0(), inner, 0.0F, SIDES, 0.0, true, Paint.DEEP, Paint.DEEP);
        ringU(sx, sy, window.u1(), inner, 0.0F, SIDES, 0.0, false, Paint.DEEP, Paint.DEEP);
        // The glass.
        for (int side = 0; side < 2; side++) {
            double a0 = side == 0 ? WINDOW_FIRST * step : (SIDES / 2 - 1 - WINDOW_LAST) * step;
            double a1 = side == 0 ? (WINDOW_LAST + 1) * step : (SIDES / 2 - WINDOW_FIRST) * step;
            float[] low = { sx + (float) Math.cos(side == 0 ? a0 : a1) * r, sy + (float) Math.sin(side == 0 ? a0 : a1) * r };
            float[] high = { sx + (float) Math.cos(side == 0 ? a1 : a0) * r, sy + (float) Math.sin(side == 0 ? a1 : a0) * r };
            float[] up = { high[0] - low[0], high[1] - low[1], 0.0F };
            float height = (float) Math.hypot(up[0], up[1]);
            up[0] /= height;
            up[1] /= height;
            // The window faces out from the shaft: right = up x normal.
            float[] normal = { (float) Math.cos((a0 + a1) / 2.0), (float) Math.sin((a0 + a1) / 2.0), 0.0F };
            float[] right = cross(up, normal);
            float[] origin = right[2] > 0 ? new float[] { low[0], low[1], window.u0() } : new float[] { low[0], low[1], window.u1() };
            face(origin, right, up, window.length(), height, Paint.GLASS, true);
        }
    }

    // The blades and drum seen through a casing's windows: rows across the window, each a little longer than the last
    // as the pressure drops, neighbouring rows offset by half a blade.
    private void rotorInWindow(TurbineLayout.Casing casing) {
        TurbineLayout.Span window = casing.window();
        if (window.length() <= 0.0F) {
            return;
        }
        float sx = layout.shaftX(), sy = layout.shaftY(), r = casing.radius() - 0.5F;
        float hub = r * casing.hub();
        prismU(sx, sy, new float[][] { { window.u0(), hub }, { window.u1(), hub } }, 12, 0.0, Paint.MID, Paint.MID, Paint.MID, null);
        int rows = casing.bladeRows();
        float pitch = window.length() / rows;
        float chord = Math.clamp(pitch * 0.55F, 1.0F, 3.0F);
        for (int row = 0; row < rows; row++) {
            float u = window.u0() + pitch * (row + 0.5F);
            float tip = r * casing.tipShare(row);
            double offset = (row % 2) * Math.PI / casing.blades();
            for (int b = 0; b < casing.blades(); b++) {
                double a = 2.0 * Math.PI * b / casing.blades() + offset;
                blade(sx, sy, u, (float) a, hub, tip, chord);
            }
        }
    }

    // One blade: a flat plate from the hub to the tip, turned PITCH from the axis; lit in front, darker behind.
    private void blade(float sx, float sy, float u, float a, float hub, float tip, float chord) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float[] radial = { c, s, 0.0F };
        float[] across = { -s * (float) Math.sin(PITCH), c * (float) Math.sin(PITCH), (float) Math.cos(PITCH) };
        float h = chord / 2.0F;
        float[] p0 = { sx + radial[0] * hub - across[0] * h, sy + radial[1] * hub - across[1] * h, u - across[2] * h };
        float[] p1 = { sx + radial[0] * tip - across[0] * h, sy + radial[1] * tip - across[1] * h, u - across[2] * h };
        float[] p2 = { sx + radial[0] * tip + across[0] * h, sy + radial[1] * tip + across[1] * h, u + across[2] * h };
        float[] p3 = { sx + radial[0] * hub + across[0] * h, sy + radial[1] * hub + across[1] * h, u + across[2] * h };
        flat(p0, p1, p2, p3, Paint.BLADE, false);
        flat(p3, p2, p1, p0, Paint.BLADE_BACK, false);
    }

    // The steam end: the steam chest on top of the first casing with its control-valve bonnet, and the stop-valve
    // housings beside it with their inlet pipes.
    private void steamEnd() {
        TurbineLayout.Box chest = layout.steamChest();
        box(chest.x0(), chest.y0(), chest.u0(), chest.x1(), chest.y1(), chest.u1(), Paint.PLATE, Paint.PLATE, null, 0);
        float inset = Math.min(2.0F, (chest.x1() - chest.x0()) / 4.0F);
        float bonnetTop = Math.min(chest.y1() + 2.0F, layout.heightPx() - 1.5F);
        if (bonnetTop > chest.y1() + 0.5F) {
            box(chest.x0() + inset, chest.y1(), chest.u0() + inset, chest.x1() - inset, bonnetTop, chest.u1() - inset, Paint.PLAIN, Paint.MID, null, 0);
        }
        solidBoxes.add(new float[] { chest.x0(), chest.y0(), chest.u0(), chest.x1(), Math.max(chest.y1(), bonnetTop), chest.u1() });
        for (TurbineLayout.Valve valve : layout.valves()) {
            float vr = valve.radius(), deck = layout.deckTop();
            // The stand, the body, the bonnet, the spindle and the handwheel.
            box(valve.x() - vr + 1.0F, deck, valve.u() - vr + 1.0F, valve.x() + vr - 1.0F, valve.bottom(), valve.u() + vr - 1.0F, Paint.PLAIN, null, null, 0);
            prismY(valve.x(), valve.u(), valve.bottom(), valve.top(), vr, Paint.BANDED, Paint.MID);
            float b = vr + 0.5F;
            box(valve.x() - b, valve.top(), valve.u() - b, valve.x() + b, valve.top() + 2.0F, valve.u() + b, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
            box(valve.x() - 0.5F, valve.top() + 2.0F, valve.u() - 0.5F, valve.x() + 0.5F, valve.top() + 4.0F, valve.u() + 0.5F, Paint.LIT, Paint.LIT, null, 0);
            prismY(valve.x(), valve.u(), valve.top() + 4.0F, valve.top() + 5.0F, vr, Paint.LIT, Paint.LIT);
            float x0 = Math.min(valve.x(), valve.pipeEnd()), x1 = Math.max(valve.x(), valve.pipeEnd());
            prismX(valve.u(), valve.pipeY(), x0, x1, Math.max(1.5F, vr * 0.5F), Paint.BANDED, Paint.MID);
            solidBoxes.add(new float[] { valve.x() - b, deck, valve.u() - b, valve.x() + b, valve.top() + 5.0F, valve.u() + b });
        }
    }

    // The exhaust neck under the last casing, down to the deck (or on between the columns, with a flange at its foot).
    private void exhaustNeck() {
        TurbineLayout.Box neck = layout.exhaustNeck();
        box(neck.x0(), neck.y0(), neck.u0(), neck.x1(), neck.y1(), neck.u1(), Paint.PLATE, null, Paint.PLAIN, 0);
        float flangeY = layout.hasColumns() ? neck.y0() : layout.deckTop();
        box(neck.x0() - 1.0F, flangeY, neck.u0() - 1.0F, neck.x1() + 1.0F, flangeY + 2.0F, neck.u1() + 1.0F, Paint.PLAIN, Paint.MID, Paint.PLAIN, 0);
        solidBoxes.add(new float[] { neck.x0() - 1.0F, neck.y0(), neck.u0() - 1.0F, neck.x1() + 1.0F, neck.y1(), neck.u1() + 1.0F });
    }

    // The crossover: up out of the intermediate casing, along over the low-pressure casing and down into it, with
    // elbows and flanged joints.
    private void crossover(TurbineLayout.Crossover pipe) {
        float sx = layout.shaftX(), r = pipe.diameter() / 2.0F, flange = r + 1.0F;
        prismY(sx, pipe.riseU(), pipe.baseY(), pipe.runY() - r, r, Paint.BANDED, Paint.MID);
        prismY(sx, pipe.dropU(), pipe.dropY(), pipe.runY() - r, r, Paint.BANDED, Paint.MID);
        prismU(sx, pipe.runY(), new float[][] { { pipe.riseU() + r, r }, { pipe.dropU() - r, r } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, Paint.MID, Paint.SHADE, null);
        for (float u : new float[] { pipe.riseU(), pipe.dropU() }) {
            box(sx - r - 0.5F, pipe.runY() - r - 0.5F, u - r - 0.5F, sx + r + 0.5F, pipe.runY() + r + 0.5F, u + r + 0.5F, Paint.PLATE, Paint.PLATE, Paint.PLAIN, 0);
            // Flanged joints: where it leaves the casing, under the elbow, and beside it along the run.
            float base = u == pipe.riseU() ? pipe.baseY() + 2.0F : pipe.dropY() + 2.0F;
            prismY(sx, u, base, base + 1.5F, flange, Paint.MID, Paint.LIT);
            prismY(sx, u, pipe.runY() - r - 2.5F, pipe.runY() - r - 1.0F, flange, Paint.MID, Paint.LIT);
            float side = u == pipe.riseU() ? 1.0F : -1.0F;
            float joint = u + side * (r + 1.0F);
            prismU(sx, pipe.runY(), new float[][] { { Math.min(joint, joint + side * 1.5F), flange }, { Math.max(joint, joint + side * 1.5F), flange } },
                    PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.MID, Paint.LIT, Paint.SHADE, null);
        }
        solidBoxes.add(new float[] { sx - r, pipe.baseY(), pipe.riseU() - r, sx + r, pipe.runY() + r, pipe.riseU() + r });
        solidBoxes.add(new float[] { sx - r, pipe.dropY(), pipe.dropU() - r, sx + r, pipe.runY() + r, pipe.dropU() + r });
        solidBoxes.add(new float[] { sx - r, pipe.runY() - r, pipe.riseU(), sx + r, pipe.runY() + r, pipe.dropU() });
    }

    // The generator: a sole plate, the housing in the lighter steel with cooling ribs up its sides and over its top, an
    // FE-red band along each side, and the terminal box with three FE-red bushings.
    private void generator() {
        TurbineLayout.Span span = layout.generator();
        float sx = layout.shaftX(), half = layout.generatorWidth() / 2.0F, deck = layout.deckTop(), top = layout.generatorTop();
        box(sx - half - 1.0F, deck, span.u0(), sx + half + 1.0F, deck + 2.0F, span.u1(), Paint.FOOTING, Paint.FOOTING, null, 0);
        box(sx - half, deck + 2.0F, span.u0() + 1.0F, sx + half, top, span.u1(), Paint.GEN_PLATE, Paint.GEN_PLATE, null, 0);
        TurbineLayout.Box terminal = layout.terminalBox();
        // The FE band, low on each side.
        float bandY = deck + 3.0F;
        box(sx - half - 0.5F, bandY, span.u0() + 2.0F, sx - half, bandY + 3.0F, span.u1() - 1.0F, Paint.FE_BAND, Paint.FE_LIT, Paint.FE_DARK, 0);
        box(sx + half, bandY, span.u0() + 2.0F, sx + half + 0.5F, bandY + 3.0F, span.u1() - 1.0F, Paint.FE_BAND, Paint.FE_LIT, Paint.FE_DARK, 0);
        // A louvred vent on each end of the housing.
        float vent = Math.max(2.0F, half * 0.6F), ventTop = top - 3.0F, ventBottom = deck + 5.0F;
        if (ventTop - ventBottom >= 4.0F) {
            box(sx - vent, ventBottom, span.u1(), sx + vent, ventTop, span.u1() + 0.5F, Paint.GEN_PLAIN, Paint.GEN_LIT, Paint.GEN_DARK, SKIP_POS_U);
            face(new float[] { sx - vent, ventBottom, span.u1() + 0.5F }, new float[] { 1, 0, 0 }, new float[] { 0, 1, 0 }, 2.0F * vent, ventTop - ventBottom, Paint.VENT, false);
            box(sx - vent, ventBottom, span.u0() + 0.5F, sx + vent, ventTop, span.u0() + 1.0F, Paint.GEN_PLAIN, Paint.GEN_LIT, Paint.GEN_DARK, SKIP_NEG_U);
            face(new float[] { sx + vent, ventBottom, span.u0() + 0.5F }, new float[] { -1, 0, 0 }, new float[] { 0, 1, 0 }, 2.0F * vent, ventTop - ventBottom, Paint.VENT, false);
        }
        for (float u = span.u0() + 4.0F; u <= span.u1() - 3.0F; u += 4.0F) {
            float y0 = bandY + 3.0F;
            box(sx - half - 1.0F, y0, u, sx - half, top, u + 1.0F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
            box(sx + half, y0, u, sx + half + 1.0F, top, u + 1.0F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
            if (u + 1.0F < terminal.u0() - 1.0F || u > terminal.u1() + 1.0F) {
                box(sx - half - 1.0F, top, u, sx + half + 1.0F, top + 1.0F, u + 1.0F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
            }
        }
        box(terminal.x0(), terminal.y0(), terminal.u0(), terminal.x1(), terminal.y1(), terminal.u1(), Paint.GEN_PLAIN, Paint.GEN_PLAIN, null, 0);
        float length = terminal.u1() - terminal.u0();
        float bushingTop = Math.min(terminal.y1() + 2.0F, layout.heightPx() - 0.5F);
        if (bushingTop > terminal.y1() + 0.5F) {
            for (int i = 0; i < 3; i++) {
                float u = terminal.u0() + length * (i + 1) / 4.0F;
                box(sx - 0.5F, terminal.y1(), u - 0.5F, sx + 0.5F, bushingTop, u + 0.5F, Paint.FE, Paint.FE_LIT, null, 0);
            }
        }
        solidBoxes.add(new float[] { sx - half - 1.0F, deck, span.u0(), sx + half + 1.0F, top, span.u1() });
        solidBoxes.add(new float[] { terminal.x0(), terminal.y0(), terminal.u0(), terminal.x1(), terminal.y1(), terminal.u1() });
    }

    // --- Port nozzles ---

    // A port's collar: from the model out to the port's face (where the block model draws its plate), straight in along
    // the face's normal to the first part it meets. One that would meet nothing runs in a block and turns down.
    private void nozzle(Port port) {
        float[] n = { port.nx(), port.ny(), port.nu() };
        float[] centre = { (port.x() + 0.5F + port.nx() * 0.5F) * PX, (port.y() + 0.5F + port.ny() * 0.5F) * PX, (port.u() + 0.5F + port.nu() * 0.5F) * PX };
        float[] in = { -n[0], -n[1], -n[2] };
        float depth = collarDepth(centre, in);
        if (depth >= 0.0F) {
            if (depth > 0.01F) {
                collar(centre, in, depth, true);
            }
            return;
        }
        if (port.ny() < 0 || port.ny() > 0) {
            return;
        }
        float run = PX;
        collar(centre, in, run + COLLAR / 2.0F, true);
        float[] elbow = { centre[0] + in[0] * run, centre[1], centre[2] + in[2] * run };
        float h = COLLAR / 2.0F + 0.5F;
        box(elbow[0] - h, elbow[1] - h, elbow[2] - h, elbow[0] + h, elbow[1] + h, elbow[2] + h, Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, 0);
        float[] down = { 0, -1, 0 };
        float[] start = { elbow[0], elbow[1] - h, elbow[2] };
        float drop = collarDepth(start, down);
        if (drop > 0.01F) {
            collar(start, down, drop, false);
        }
    }

    // How far along dir from the face point the model's surface is under the whole collar (the farthest of its corners,
    // so no edge ends in the air), or under its middle when a corner misses; -1 when the middle misses too.
    private float collarDepth(float[] point, float[] dir) {
        float middle = ray(point, dir);
        if (middle < 0.0F) {
            return -1.0F;
        }
        float[][] axes = across(dir);
        float deepest = middle;
        float h = COLLAR / 2.0F - 0.5F;
        for (int i = 0; i < 4; i++) {
            float su = (i & 1) == 0 ? -h : h, sv = (i & 2) == 0 ? -h : h;
            float[] corner = { point[0] + axes[0][0] * su + axes[1][0] * sv, point[1] + axes[0][1] * su + axes[1][1] * sv, point[2] + axes[0][2] * su + axes[1][2] * sv };
            float hit = ray(corner, dir);
            if (hit < 0.0F) {
                return middle;
            }
            deepest = Math.max(deepest, hit);
        }
        return Math.min(deepest + 0.5F, middle + 6.0F);
    }

    // The nearest solid along an axis-aligned ray, or -1.
    private float ray(float[] p, float[] dir) {
        int axis = dir[0] != 0 ? 0 : dir[1] != 0 ? 1 : 2;
        float sign = dir[axis];
        float best = Float.MAX_VALUE;
        for (float[] b : solidBoxes) {
            boolean inside = true;
            for (int k = 0; k < 3; k++) {
                if (k != axis && !(p[k] > b[k] && p[k] < b[k + 3])) {
                    inside = false;
                }
            }
            if (!inside) {
                continue;
            }
            float near = sign > 0 ? b[axis] - p[axis] : p[axis] - b[axis + 3];
            float far = sign > 0 ? b[axis + 3] - p[axis] : p[axis] - b[axis];
            if (far > 0.0F) {
                best = Math.min(best, Math.max(0.0F, near));
            }
        }
        for (float[] c : solidCylinders) {
            float cx = c[0], cy = c[1], r = c[2];
            if (axis == 2) {
                float dx = p[0] - cx, dy = p[1] - cy;
                if (dx * dx + dy * dy < r * r) {
                    float near = sign > 0 ? c[3] - p[2] : p[2] - c[4];
                    float far = sign > 0 ? c[4] - p[2] : p[2] - c[3];
                    if (far > 0.0F) {
                        best = Math.min(best, Math.max(0.0F, near));
                    }
                }
                continue;
            }
            if (p[2] <= c[3] || p[2] >= c[4]) {
                continue;
            }
            float offset = axis == 0 ? p[1] - cy : p[0] - cx;
            if (Math.abs(offset) >= r) {
                continue;
            }
            float reach = (float) Math.sqrt(r * r - offset * offset);
            float centre = axis == 0 ? cx : cy;
            float near = sign > 0 ? centre - reach - p[axis] : p[axis] - (centre + reach);
            float far = sign > 0 ? centre + reach - p[axis] : p[axis] - (centre - reach);
            if (far > 0.0F) {
                best = Math.min(best, Math.max(0.0F, near));
            }
        }
        return best == Float.MAX_VALUE ? -1.0F : best;
    }

    // The two directions across an axis-aligned direction.
    private static float[][] across(float[] dir) {
        if (dir[0] != 0) {
            return new float[][] { { 0, 1, 0 }, { 0, 0, 1 } };
        }
        if (dir[1] != 0) {
            return new float[][] { { 1, 0, 0 }, { 0, 0, 1 } };
        }
        return new float[][] { { 1, 0, 0 }, { 0, 1, 0 } };
    }

    // A collar COLLAR px across from point along dir for length px; its end at point is left open when that's the port's
    // face (the block model's plate covers it).
    private void collar(float[] point, float[] dir, float length, boolean openStart) {
        float h = COLLAR / 2.0F;
        float[] min = new float[3], max = new float[3];
        for (int k = 0; k < 3; k++) {
            float a = point[k], b = point[k] + dir[k] * length;
            min[k] = dir[k] != 0 ? Math.min(a, b) : a - h;
            max[k] = dir[k] != 0 ? Math.max(a, b) : a + h;
        }
        int skip = 0;
        if (openStart) {
            int axis = dir[0] != 0 ? 0 : dir[1] != 0 ? 1 : 2;
            // The face at point: the min face when dir points to +, else the max face.
            skip = faceBit(axis, dir[axis] < 0);
        }
        box(min[0], min[1], min[2], max[0], max[1], max[2], Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, skip);
    }

    // --- Building blocks ---

    // Bits for box(): which faces to leave out.
    private static final int SKIP_DOWN = 1, SKIP_UP = 2, SKIP_NEG_X = 4, SKIP_POS_X = 8, SKIP_NEG_U = 16, SKIP_POS_U = 32;

    private static int faceBit(int axis, boolean positive) {
        return switch (axis) {
            case 0 -> positive ? SKIP_POS_X : SKIP_NEG_X;
            case 1 -> positive ? SKIP_UP : SKIP_DOWN;
            default -> positive ? SKIP_POS_U : SKIP_NEG_U;
        };
    }

    // An axis-aligned box, its sides, top and bottom painted (null: the sides' paint; skip: faces left out).
    private void box(float x0, float y0, float u0, float x1, float y1, float u1, Paint sides, @Nullable Paint top, @Nullable Paint bottom, int skip) {
        if (x1 - x0 <= 0.001F || y1 - y0 <= 0.001F || u1 - u0 <= 0.001F) {
            return;
        }
        Paint t = top != null ? top : sides, b = bottom != null ? bottom : sides;
        float w = x1 - x0, h = y1 - y0, l = u1 - u0;
        if ((skip & SKIP_UP) == 0) {
            face(new float[] { x0, y1, u1 }, new float[] { 1, 0, 0 }, new float[] { 0, 0, -1 }, w, l, t, false);
        }
        if ((skip & SKIP_DOWN) == 0) {
            face(new float[] { x0, y0, u0 }, new float[] { 1, 0, 0 }, new float[] { 0, 0, 1 }, w, l, b, false);
        }
        if ((skip & SKIP_POS_X) == 0) {
            face(new float[] { x1, y0, u1 }, new float[] { 0, 0, -1 }, new float[] { 0, 1, 0 }, l, h, sides, false);
        }
        if ((skip & SKIP_NEG_X) == 0) {
            face(new float[] { x0, y0, u0 }, new float[] { 0, 0, 1 }, new float[] { 0, 1, 0 }, l, h, sides, false);
        }
        if ((skip & SKIP_POS_U) == 0) {
            face(new float[] { x0, y0, u1 }, new float[] { 1, 0, 0 }, new float[] { 0, 1, 0 }, w, h, sides, false);
        }
        if ((skip & SKIP_NEG_U) == 0) {
            face(new float[] { x1, y0, u0 }, new float[] { -1, 0, 0 }, new float[] { 0, 1, 0 }, w, h, sides, false);
        }
    }

    // A face w x h px from origin (its bottom-left seen from outside) along right and up (outside is right x up),
    // painted to its size (see slab).
    private void face(float[] origin, float[] right, float[] up, float w, float h, Paint paint, boolean glass) {
        if (w <= 0.001F || h <= 0.001F) {
            return;
        }
        slab(origin, at(origin, right, up, w, 0), at(origin, right, up, w, h), at(origin, right, up, 0, h), w, h, paint, glass);
    }

    // A quad p0 p1 p2 p3 (bottom-left, bottom-right, top-right, top-left seen from outside; it may be a trapezoid, as a
    // tapering facet is) about w px across and h px up, painted to its size: a panel cut into its columns and rows (fixed
    // ones at 1 texel per px, stretching bands sharing the rest), dropping to its fallback when the face is too small
    // (and from riveted to plain under 9 px); a flat tone; or a tile laid at 1:1 from the top-left.
    private void slab(float[] p0, float[] p1, float[] p2, float[] p3, float w, float h, Paint paint, boolean glass) {
        Paint p = paint.kind == Kind.BANDED ? Paint.FACE : paint;
        while (p.kind == Kind.PANEL && (w < p.least(true) || h < p.least(false) || p.riveted && Math.min(w, h) < 9.0F)) {
            p = p.fallback();
        }
        switch (p.kind) {
            case TILED -> {
                for (float x = 0; x < w - 0.001F; x += p.w) {
                    for (float y = 0; y < h - 0.001F; y += p.h) {
                        float x1 = Math.min(w, x + p.w), y1 = Math.min(h, y + p.h);
                        // Laid up from the bottom edge; a cut tile at the top keeps its top rows.
                        float top0 = h - y1, top1 = h - y;
                        cell(p0, p1, p2, p3, x / w, x1 / w, 1.0F - top1 / h, 1.0F - top0 / h, p, 0, 0, x1 - x, y1 - y, glass);
                    }
                }
            }
            case PANEL -> {
                float[] xs = cuts(p.cols, w), ys = cuts(p.rows, h);
                float tx = 0;
                for (int i = 0; i < p.cols.length; i++) {
                    float ty = 0;
                    for (int j = 0; j < p.rows.length; j++) {
                        // Rows run down from the top: row j spans ys[j]..ys[j + 1] from the top edge.
                        cell(p0, p1, p2, p3, xs[i] / w, xs[i + 1] / w, 1.0F - ys[j + 1] / h, 1.0F - ys[j] / h, p,
                                tx, ty, tx + p.cols[i][0], ty + p.rows[j][0], glass);
                        ty += p.rows[j][0];
                    }
                    tx += p.cols[i][0];
                }
            }
            default -> cell(p0, p1, p2, p3, 0, 1, 0, 1, p, 1, 1, 3, 3, glass);
        }
    }

    // Where each column (or row) of a panel starts and ends across a face size px long: fixed ones at their texels, the
    // stretching ones sharing the rest by weight.
    private static float[] cuts(int[][] segments, float size) {
        float fixed = 0, weight = 0;
        for (int[] segment : segments) {
            if (segment[1] == 0) {
                fixed += segment[0];
            } else {
                weight += segment[1];
            }
        }
        float spare = Math.max(0.0F, size - fixed);
        float[] at = new float[segments.length + 1];
        for (int i = 0; i < segments.length; i++) {
            float length = segments[i][1] == 0 ? segments[i][0] : (weight > 0 ? spare * segments[i][1] / weight : 0);
            at[i + 1] = at[i] + length;
        }
        // Rounding: the last edge is the face's.
        at[segments.length] = size;
        return at;
    }

    // One cell of a slab: the part between s0..s1 across and t0..t1 up (fractions, bilinear over the quad), showing
    // the paint's texels tu0..tu1 across and tv0..tv1 down (tv0 at the cell's top).
    private void cell(float[] p0, float[] p1, float[] p2, float[] p3, float s0, float s1, float t0, float t1, Paint paint,
            float tu0, float tv0, float tu1, float tv1, boolean glass) {
        if (s1 - s0 <= 1.0E-5F || t1 - t0 <= 1.0E-5F) {
            return;
        }
        float[] bl = bilinear(p0, p1, p2, p3, s0, t0), br = bilinear(p0, p1, p2, p3, s1, t0);
        float[] tr = bilinear(p0, p1, p2, p3, s1, t1), tl = bilinear(p0, p1, p2, p3, s0, t1);
        float u0 = (paint.x + tu0) / SHEET_W, u1 = (paint.x + tu1) / SHEET_W, v0 = (paint.y + tv0) / SHEET_H, v1 = (paint.y + tv1) / SHEET_H;
        quad(bl, br, tr, tl, new float[] { u0, v1, u1, v1, u1, v0, u0, v0 }, glass);
    }

    private static float[] bilinear(float[] p0, float[] p1, float[] p2, float[] p3, float s, float t) {
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) {
            float bottom = p0[k] + (p1[k] - p0[k]) * s, top = p3[k] + (p2[k] - p3[k]) * s;
            out[k] = bottom + (top - bottom) * t;
        }
        return out;
    }

    private static float[] at(float[] origin, float[] right, float[] up, float x, float y) {
        return new float[] { origin[0] + right[0] * x + up[0] * y, origin[1] + right[1] * x + up[1] * y, origin[2] + right[2] * x + up[2] * y };
    }

    // A quad in one paint, wound counter-clockwise seen from outside, its size taken from its edges.
    private void flat(float[] p0, float[] p1, float[] p2, float[] p3, Paint paint, boolean glass) {
        if (paint.kind == Kind.FLAT) {
            cell(p0, p1, p2, p3, 0, 1, 0, 1, paint, 1, 1, 3, 3, glass);
            return;
        }
        float w = (length(sub(p1, p0)) + length(sub(p2, p3))) / 2.0F, h = (length(sub(p3, p0)) + length(sub(p2, p1))) / 2.0F;
        slab(p0, p1, p2, p3, w, h, paint, glass);
    }

    // The band tone (or casing strip) for a facet facing n.
    private static Paint band(Paint marker, float[] n) {
        float d = dot(normalize(n), LIGHT);
        // The game shades faces turned away from the light as well, so the darkest band is never used on round parts.
        int tone = d > 0.85F ? 5 : d > 0.55F ? 4 : d > 0.2F ? 3 : d > -0.2F ? 2 : 1;
        return marker == Paint.CASING ? STRIPS[tone] : TONES[tone];
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(dot(v, v));
    }

    private static float[] normalize(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1.0E-9F ? v : new float[] { v[0] / l, v[1] / l, v[2] / l };
    }

    // A flat quad wound to face along outward (whichever way its points were given).
    private void orient(float[] p0, float[] p1, float[] p2, float[] p3, float[] outward, Paint given) {
        Paint paint = given.kind == Kind.BANDED ? band(given, outward) : given;
        float[] n = cross(sub(p2, p0), sub(p3, p1));
        if (dot(n, outward) >= 0) {
            flat(p0, p1, p2, p3, paint, false);
        } else {
            flat(p3, p2, p1, p0, paint, false);
        }
    }

    // A polygon tube along u about (cx, cy): its outline as (u, radius) points, the facets painted side, and a ring cap
    // on each end (lit / shade halves; none when they're null). skip leaves out facets (segment, facet).
    private void prismU(float cx, float cy, float[][] profile, int sides, double start, Paint side, @Nullable Paint capLit,
            @Nullable Paint capShade, @Nullable FacetFilter skip) {
        double step = 2.0 * Math.PI / sides;
        for (int i = 0; i + 1 < profile.length; i++) {
            float ua = profile[i][0], ub = profile[i + 1][0], ra = profile[i][1], rb = profile[i + 1][1];
            if (ub - ua <= 0.001F) {
                continue;
            }
            for (int f = 0; f < sides; f++) {
                if (skip != null && skip.skip(i, f)) {
                    continue;
                }
                double a0 = start + f * step, a1 = a0 + step;
                float[] p0 = { cx + (float) Math.cos(a0) * ra, cy + (float) Math.sin(a0) * ra, ua };
                float[] p1 = { cx + (float) Math.cos(a1) * ra, cy + (float) Math.sin(a1) * ra, ua };
                float[] p2 = { cx + (float) Math.cos(a1) * rb, cy + (float) Math.sin(a1) * rb, ub };
                float[] p3 = { cx + (float) Math.cos(a0) * rb, cy + (float) Math.sin(a0) * rb, ub };
                double mid = (a0 + a1) / 2.0;
                flat(p0, p1, p2, p3, side.kind == Kind.BANDED ? band(side, new float[] { (float) Math.cos(mid), (float) Math.sin(mid), 0.0F }) : side, false);
            }
        }
        if (capLit == null || capShade == null) {
            return;
        }
        float[] first = profile[0], last = profile[profile.length - 1];
        ringU(cx, cy, first[0], first[1], 0.0F, sides, start, false, capLit, capShade);
        ringU(cx, cy, last[0], last[1], 0.0F, sides, start, true, capLit, capShade);
    }

    @FunctionalInterface
    private interface FacetFilter {
        boolean skip(int segment, int facet);
    }

    // A flat ring (a disc when inner is 0) across u at u, facing +u or -u; its facets on the top-left half seen from
    // outside are lit, the rest shade.
    private void ringU(float cx, float cy, float u, float outer, float inner, int sides, double start, boolean facingPositive, Paint lit, Paint shade) {
        double step = 2.0 * Math.PI / sides;
        // Seen from +u, right is +x; from -u, right is -x. Up is +y either way.
        float rightSign = facingPositive ? 1.0F : -1.0F;
        for (int f = 0; f < sides; f++) {
            double a0 = start + f * step, a1 = a0 + step, mid = (a0 + a1) / 2.0;
            boolean topLeft = Math.sin(mid) - rightSign * Math.cos(mid) > 0;
            Paint paint = topLeft ? lit : shade;
            float[] o0 = { cx + (float) Math.cos(a0) * outer, cy + (float) Math.sin(a0) * outer, u };
            float[] o1 = { cx + (float) Math.cos(a1) * outer, cy + (float) Math.sin(a1) * outer, u };
            float[] i1 = { cx + (float) Math.cos(a1) * inner, cy + (float) Math.sin(a1) * inner, u };
            float[] i0 = { cx + (float) Math.cos(a0) * inner, cy + (float) Math.sin(a0) * inner, u };
            if (facingPositive) {
                flat(o0, o1, i1, i0, paint, false);
            } else {
                flat(i0, i1, o1, o0, paint, false);
            }
        }
    }

    // An upright octagonal pipe at (x, u) from y0 to y1, with flat caps.
    private void prismY(float x, float u, float y0, float y1, float r, Paint side, Paint cap) {
        if (y1 - y0 <= 0.001F) {
            return;
        }
        double step = 2.0 * Math.PI / PIPE_SIDES, start = Math.PI / PIPE_SIDES;
        for (int f = 0; f < PIPE_SIDES; f++) {
            double a0 = start + f * step, a1 = a0 + step;
            // In the (u, x) plane, so (u, x, y) keeps the frame's handedness.
            float[] p0 = { x + (float) Math.sin(a0) * r, y0, u + (float) Math.cos(a0) * r };
            float[] p1 = { x + (float) Math.sin(a1) * r, y0, u + (float) Math.cos(a1) * r };
            float[] p2 = { p1[0], y1, p1[2] };
            float[] p3 = { p0[0], y1, p0[2] };
            orient(p0, p1, p2, p3, new float[] { (float) Math.sin((a0 + a1) / 2.0), 0, (float) Math.cos((a0 + a1) / 2.0) }, side);
            float[] c0 = { x, y1, u };
            orient(c0, p3, p2, c0, new float[] { 0, 1, 0 }, cap);
            float[] b0 = { x, y0, u };
            orient(b0, p0, p1, b0, new float[] { 0, -1, 0 }, side);
        }
    }

    // An octagonal pipe along x at (u, y) from x0 to x1, with flat caps.
    private void prismX(float u, float y, float x0, float x1, float r, Paint side, Paint cap) {
        if (x1 - x0 <= 0.001F) {
            return;
        }
        double step = 2.0 * Math.PI / PIPE_SIDES, start = Math.PI / PIPE_SIDES;
        for (int f = 0; f < PIPE_SIDES; f++) {
            double a0 = start + f * step, a1 = a0 + step;
            float[] p0 = { x0, y + (float) Math.cos(a0) * r, u + (float) Math.sin(a0) * r };
            float[] p1 = { x0, y + (float) Math.cos(a1) * r, u + (float) Math.sin(a1) * r };
            float[] p2 = { x1, p1[1], p1[2] };
            float[] p3 = { x1, p0[1], p0[2] };
            orient(p0, p1, p2, p3, new float[] { 0, (float) Math.cos((a0 + a1) / 2.0), (float) Math.sin((a0 + a1) / 2.0) }, side);
            orient(new float[] { x1, y, u }, p3, p2, new float[] { x1, y, u }, new float[] { 1, 0, 0 }, cap);
            orient(new float[] { x0, y, u }, p0, p1, new float[] { x0, y, u }, new float[] { -1, 0, 0 }, cap);
        }
    }

    // --- Output ---

    // Appends a quad: its points in the turbine's frame (px), turned with the rotor while building it, mapped to blocks
    // from the minimum corner, with its normal.
    private void quad(float[] p0, float[] p1, float[] p2, float[] p3, float[] uv, boolean glass) {
        float[][] points = { turn(p0), turn(p1), turn(p2), turn(p3) };
        // Across the diagonals, so a quad with two points the same (a triangle) still gets its normal.
        float[] n = cross(sub(points[2], points[0]), sub(points[3], points[1]));
        float length = (float) Math.sqrt(dot(n, n));
        if (length < 1.0E-6F) {
            return;
        }
        float nx = n[0] / length, ny = n[1] / length, nu = n[2] / length;
        Floats out = glass ? cutout : solid;
        for (int i = 0; i < 4; i++) {
            float[] p = points[i];
            if (alongX) {
                out.add(p[2] / PX, p[1] / PX, (widthPx - p[0]) / PX, uv[2 * i], uv[2 * i + 1], nu, ny, -nx);
            } else {
                out.add(p[0] / PX, p[1] / PX, p[2] / PX, uv[2 * i], uv[2 * i + 1], nx, ny, nu);
            }
        }
    }

    private float[] turn(float[] p) {
        if (!rotating) {
            return p;
        }
        float sx = layout.shaftX(), sy = layout.shaftY();
        float dx = p[0] - sx, dy = p[1] - sy;
        return new float[] { sx + (float) (dx * cos - dy * sin), sy + (float) (dx * sin + dy * cos), p[2] };
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    // A growable float array.
    private static final class Floats {
        private float[] data = new float[4096];
        private int size;

        void add(float... values) {
            if (size + values.length > data.length) {
                data = Arrays.copyOf(data, Math.max(data.length * 2, size + values.length));
            }
            System.arraycopy(values, 0, data, size, values.length);
            size += values.length;
        }

        float[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
