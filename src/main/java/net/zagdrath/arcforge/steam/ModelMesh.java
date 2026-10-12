/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

// What the formed turbine arrays' models are built with (TurbineMesh, GasTurbineMesh): boxes, polygon tubes and rings,
// faces painted from a model sheet, port nozzles, and the output.
//
// Texturing follows docs/TEXTURE_STYLE.md "3D models": one sheet per model at 1 texel per px, laid out alike (Paint
// gives where each painted area is; a model's sheet holds the areas it uses). A model whose size follows its structure
// can't have a fixed unwrap, so each face is cut from a panel at its own size: fixed columns and rows (bevels, rivet
// corners, seam bands) at 1 texel per px, bands of one tone stretching to fill the rest. Faces too small drop to the
// plain panel, then a flat tone. Round parts are banded per facet by how they face the light (BANDED, CASING); caps are
// flat concentric rings; repeating surfaces (grating, strap, louvres) are tiles at 1:1.
//
// The model's frame: x across, y up, u along the axis, in px. Output: per vertex x, y, z (blocks from the structure's
// minimum corner), u, v (0-1 across the sheet), nx, ny, nz; four vertices a quad, in solid, cutout (glass) and glow
// (full-bright) lists.
public abstract class ModelMesh {
    static final float PX = 16.0F;
    static final int SIDES = 16, PIPE_SIDES = 8;
    // The collar of a port nozzle, across (px): the port plate's collar (x3..12).
    static final float COLLAR = 10.0F;

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
        GLASS(112, 8, 12, 12, false, new int[][] { { 7, 0 }, { 1, 100 }, { 4, 0 } }, new int[][] { { 7, 0 }, { 1, 100 }, { 4, 0 } }),
        GLASS_SMALL(124, 8, 4, 4, false, new int[][] { { 1, 0 }, { 2, 100 }, { 1, 0 } }, new int[][] { { 1, 0 }, { 2, 100 }, { 1, 0 } }),
        CASING_0(0, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        CASING_1(8, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        CASING_2(16, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        CASING_3(24, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        CASING_4(32, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        CASING_5(40, 24, 8, 12, false, Cuts.STRIP_ACROSS, Cuts.STRIP_ALONG),
        // The Gas Turbine Array's sheet only (rows 48-63): the Hardened purple ramp, the light-steel compressor and
        // Hardened turbine blades, and the combustor glow (blue-white core to orange, in steps).
        HARD_0(0, 48), HARD_1(4, 48), HARD_2(8, 48), HARD_3(12, 48), HARD_4(16, 48), HARD_5(20, 48),
        COMPRESSOR_BLADE(24, 48), COMPRESSOR_BLADE_BACK(28, 48), TURBINE_BLADE(32, 48), TURBINE_BLADE_BACK(36, 48),
        GLOW(40, 48, 4, 7, false, new int[][] { { 4, 100 } }, new int[][] { { 1, 1 }, { 1, 1 }, { 1, 1 }, { 1, 1 }, { 1, 1 }, { 1, 1 }, { 1, 1 } }),
        GLOW_CORE(44, 48),
        GLOW_SMALL(48, 48, 4, 3, false, new int[][] { { 4, 100 } }, new int[][] { { 1, 1 }, { 1, 1 }, { 1, 1 } }),
        // The Steam Turbine Array's sheet only (rows 64-87): broad shading (docs/TEXTURE_STYLE.md rule 2) for its big
        // faces, eight flat tones each for the machine steel and the generator's lighter steel, lightest first, and the
        // panels' corners and bevels to lay round them (see broadSlab).
        BROAD_0(0, 64), BROAD_1(4, 64), BROAD_2(8, 64), BROAD_3(12, 64), BROAD_4(16, 64), BROAD_5(20, 64), BROAD_6(24, 64), BROAD_7(28, 64),
        GEN_BROAD_0(32, 64), GEN_BROAD_1(36, 64), GEN_BROAD_2(40, 64), GEN_BROAD_3(44, 64), GEN_BROAD_4(48, 64), GEN_BROAD_5(52, 64),
        GEN_BROAD_6(56, 64), GEN_BROAD_7(60, 64),
        B_PLATE(0, 72, 16, 16, true, Cuts.CORNERS, Cuts.CORNERS),
        B_PLAIN(16, 72, 16, 16, false, Cuts.CORNERS, Cuts.CORNERS),
        B_FOOTING(32, 72, 16, 16, false, Cuts.CORNERS, Cuts.CORNERS),
        B_GEN_PLATE(48, 72, 16, 16, true, Cuts.CORNERS, Cuts.CORNERS),
        B_GEN_PLAIN(64, 72, 16, 16, false, Cuts.CORNERS, Cuts.CORNERS),
        // Markers: tones by facing (pipes, bosses, valve bodies), the casing strips by facing (the casings' shells), or
        // the Hardened purple tones by facing (the gas turbine's heat-blued band).
        BANDED(0, 0, 0, 0, Kind.BANDED),
        CASING(0, 0, 0, 0, Kind.BANDED),
        HARDENED(0, 0, 0, 0, Kind.BANDED);

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
                case GLASS -> GLASS_SMALL;
                case GLASS_SMALL -> LIT;
                case CASING_0 -> DARK;
                case CASING_1 -> SHADE;
                case CASING_2 -> FACE_SHADE;
                case CASING_3 -> FACE;
                case CASING_4 -> MID;
                case CASING_5 -> LIT;
                case GLOW -> GLOW_SMALL;
                case GLOW_SMALL -> GLOW_CORE;
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

    static final Paint[] BROAD = { Paint.BROAD_0, Paint.BROAD_1, Paint.BROAD_2, Paint.BROAD_3, Paint.BROAD_4, Paint.BROAD_5, Paint.BROAD_6, Paint.BROAD_7 };
    static final Paint[] GEN_BROAD = { Paint.GEN_BROAD_0, Paint.GEN_BROAD_1, Paint.GEN_BROAD_2, Paint.GEN_BROAD_3, Paint.GEN_BROAD_4, Paint.GEN_BROAD_5,
            Paint.GEN_BROAD_6, Paint.GEN_BROAD_7 };
    // Broad shading's weights across and down a face (tone = 8 x (0.55 x across + 0.45 x down), as fractions of it),
    // and the least a face needs each way for it; and the facet length from which round parts step along their length.
    static final float BROAD_ACROSS = 0.55F, BROAD_DOWN = 0.45F, BROAD_LEAST = 12.0F, BROAD_LONG = 16.0F, STEP_FROM = 24.0F;

    // The band tones, darkest first, and the casing strips in the same order.
    static final Paint[] TONES = { Paint.DARK, Paint.SHADE, Paint.FACE_SHADE, Paint.FACE, Paint.MID, Paint.LIT };
    static final Paint[] HARD = { Paint.HARD_0, Paint.HARD_1, Paint.HARD_2, Paint.HARD_3, Paint.HARD_4, Paint.HARD_5 };
    static final Paint[] STRIPS = { Paint.CASING_0, Paint.CASING_1, Paint.CASING_2, Paint.CASING_3, Paint.CASING_4, Paint.CASING_5 };
    // The light the bands are painted for (in the turbine's frame): from above, a little toward x = 0 and the steam end.
    static final float[] LIGHT = normalize(new float[] { -0.35F, 0.85F, -0.4F });

    // The panel cuts (in a class of their own, so Paint can use them while it's being set up).
    static final class Cuts {
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


    private final boolean alongX, flip;
    final float widthPx, lengthPx;
    private final int sheetW, sheetH;
    private final float pivotX, pivotY;
    Floats solid = new Floats(), cutout = new Floats(), glow = new Floats();
    // Quads go to the glow list while this is set.
    boolean glowing;
    // Whether this model's sheet has the broad-shading areas (the Steam Turbine Array's): its big plated faces are then
    // broad-shaded and its long round facets step along their length (see broadSlab, stepped).
    boolean broadShading;
    // While building the rotor: its angle about the pivot (the shaft).
    boolean rotating;
    double cos = 1.0, sin = 0.0;
    // What the nozzles stop at: boxes {x0, y0, u0, x1, y1, u1} and cylinders along u {cx, cy, r, u0, u1}.
    final List<float[]> solidBoxes = new ArrayList<>();
    final List<float[]> solidCylinders = new ArrayList<>();

    // alongX: the structure runs along world X (x then runs toward -Z); flip: the model's u = 0 is at the structure's
    // positive end (turned end for end, x mirrored with it so the frame keeps its handedness).
    ModelMesh(boolean alongX, boolean flip, float widthPx, float lengthPx, float pivotX, float pivotY, int sheetW, int sheetH) {
        this.alongX = alongX;
        this.flip = flip;
        this.widthPx = widthPx;
        this.lengthPx = lengthPx;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.sheetW = sheetW;
        this.sheetH = sheetH;
    }

    // Starts the still parts.
    void startStatic() {
        solid = new Floats();
        cutout = new Floats();
        glow = new Floats();
        rotating = false;
        glowing = false;
        solidBoxes.clear();
        solidCylinders.clear();
    }

    // Starts the parts that turn, at angle degrees.
    void startRotor(float angle) {
        solid = new Floats();
        cutout = new Floats();
        glow = new Floats();
        glowing = false;
        rotating = true;
        cos = Math.cos(Math.toRadians(angle));
        sin = Math.sin(Math.toRadians(angle));
    }

    // --- Port nozzles ---

    // A port's collar: from the model out to the port's face (where the block model draws its plate), straight in along
    // the face's normal to the first part it meets. One that would meet nothing runs in a block and turns down.
    void nozzle(Port port) {
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
    float collarDepth(float[] point, float[] dir) {
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
    float ray(float[] p, float[] dir) {
        int axis = dir[0] != 0 ? 0 : dir[1] != 0 ? 1 : 2;
        float sign = dir[axis];
        float best = Float.MAX_VALUE;
        for (float[] b : solidBoxes) {
            boolean inside = true;
            for (int k = 0; k < 3; k++) {
                if (k != axis && !(p[k] >= b[k] && p[k] <= b[k + 3])) {
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
            if (p[2] < c[3] || p[2] > c[4]) {
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
    static float[][] across(float[] dir) {
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
    void collar(float[] point, float[] dir, float length, boolean openStart) {
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
    static final int SKIP_DOWN = 1, SKIP_UP = 2, SKIP_NEG_X = 4, SKIP_POS_X = 8, SKIP_NEG_U = 16, SKIP_POS_U = 32;

    static int faceBit(int axis, boolean positive) {
        return switch (axis) {
            case 0 -> positive ? SKIP_POS_X : SKIP_NEG_X;
            case 1 -> positive ? SKIP_UP : SKIP_DOWN;
            default -> positive ? SKIP_POS_U : SKIP_NEG_U;
        };
    }

    // An axis-aligned box, its sides, top and bottom painted (null: the sides' paint; skip: faces left out).
    void box(float x0, float y0, float u0, float x1, float y1, float u1, Paint sides, @Nullable Paint top, @Nullable Paint bottom, int skip) {
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
    void face(float[] origin, float[] right, float[] up, float w, float h, Paint paint, boolean glass) {
        if (w <= 0.001F || h <= 0.001F) {
            return;
        }
        slab(origin, at(origin, right, up, w, 0), at(origin, right, up, w, h), at(origin, right, up, 0, h), w, h, paint, glass);
    }

    // A quad p0 p1 p2 p3 (bottom-left, bottom-right, top-right, top-left seen from outside; it may be a trapezoid, as a
    // tapering facet is) about w px across and h px up, painted to its size: a panel cut into its columns and rows (fixed
    // ones at 1 texel per px, stretching bands sharing the rest), dropping to its fallback when the face is too small
    // (and from riveted to plain under 9 px); a flat tone; or a tile laid at 1:1 from the top-left.
    void slab(float[] p0, float[] p1, float[] p2, float[] p3, float w, float h, Paint paint, boolean glass) {
        Paint broad = broadShading && !glass ? broadPanel(paint) : null;
        if (broad != null && Math.min(w, h) >= BROAD_LEAST && Math.max(w, h) >= BROAD_LONG) {
            broadSlab(p0, p1, p2, p3, w, h, broad);
            return;
        }
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

    // --- Broad shading (docs/TEXTURE_STYLE.md rule 2) ---

    // The broad-shaded panel for a plated paint, or null.
    static @Nullable Paint broadPanel(Paint paint) {
        return switch (paint) {
            case PLATE -> Paint.B_PLATE;
            case PLAIN, DECK -> Paint.B_PLAIN;
            case FOOTING -> Paint.B_FOOTING;
            case GEN_PLATE -> Paint.B_GEN_PLATE;
            case GEN_PLAIN -> Paint.B_GEN_PLAIN;
            default -> null;
        };
    }

    // A big face, broad-shaded: its four 4 px corners (bevel and, on a riveted panel 9 px or more each way, a rivet) and
    // its 2 px bevels from the panel at 1 texel per px (only the bevels' one-tone rows stretch along the edges), and
    // everything inside them in eight flat tones in wide diagonal bands, lightest at the top left: each pixel's band is
    // 8 x (0.55 x across + 0.45 x down) as fractions of the face. Each band is its own polygon in its flat tone, so the
    // steps are hard and nothing is blended.
    void broadSlab(float[] p0, float[] p1, float[] p2, float[] p3, float w, float h, Paint panel) {
        Paint src = panel.riveted && Math.min(w, h) < 9.0F ? (panel == Paint.B_GEN_PLATE ? Paint.B_GEN_PLAIN : Paint.B_PLAIN) : panel;
        Paint[] tones = panel == Paint.B_GEN_PLATE || panel == Paint.B_GEN_PLAIN ? GEN_BROAD : BROAD;
        // Corners (texels 0..4 / 12..16 each way).
        for (int cx = 0; cx < 2; cx++) {
            for (int cy = 0; cy < 2; cy++) {
                float s0 = cx == 0 ? 0 : 1 - 4 / w, s1 = cx == 0 ? 4 / w : 1;
                // cy 0: the top corners (texture rows 0..4).
                float t0 = cy == 0 ? 1 - 4 / h : 0, t1 = cy == 0 ? 1 : 4 / h;
                cell(p0, p1, p2, p3, s0, s1, t0, t1, src, cx == 0 ? 0 : 12, cy == 0 ? 0 : 12, cx == 0 ? 4 : 16, cy == 0 ? 4 : 16, false);
            }
        }
        // The bevels between the corners.
        cell(p0, p1, p2, p3, 4 / w, 1 - 4 / w, 1 - 2 / h, 1, src, 4, 0, 12, 2, false);
        cell(p0, p1, p2, p3, 4 / w, 1 - 4 / w, 0, 2 / h, src, 4, 14, 12, 16, false);
        cell(p0, p1, p2, p3, 0, 2 / w, 4 / h, 1 - 4 / h, src, 0, 4, 2, 12, false);
        cell(p0, p1, p2, p3, 1 - 2 / w, 1, 4 / h, 1 - 4 / h, src, 14, 4, 16, 12, false);
        // The field, as three rectangles (x0, y0 down from the top, x1, y1) between the corners and inside the bevels.
        float[][] rects = { { 4, 2, w - 4, h - 2 }, { 2, 4, 4, h - 4 }, { w - 4, 4, w - 2, h - 4 } };
        for (float[] rect : rects) {
            if (rect[2] - rect[0] <= 1.0E-4F || rect[3] - rect[1] <= 1.0E-4F) {
                continue;
            }
            for (int band = 0; band < tones.length; band++) {
                // Corners in (across, up) face fractions, counter-clockwise.
                List<float[]> poly = new ArrayList<>(List.of(
                        new float[] { rect[0] / w, 1 - rect[3] / h }, new float[] { rect[2] / w, 1 - rect[3] / h },
                        new float[] { rect[2] / w, 1 - rect[1] / h }, new float[] { rect[0] / w, 1 - rect[1] / h }));
                // tone(s, t) = 8 x (0.55 s + 0.45 (1 - t)); keep band <= tone < band + 1.
                if (band > 0) {
                    poly = clip(poly, BROAD_ACROSS, -BROAD_DOWN, BROAD_DOWN - band / (float) tones.length);
                }
                if (band < tones.length - 1) {
                    poly = clip(poly, -BROAD_ACROSS, BROAD_DOWN, (band + 1) / (float) tones.length - BROAD_DOWN);
                }
                polygon(p0, p1, p2, p3, poly, tones[band]);
            }
        }
    }

    // The part of a polygon (in face fractions) where a s + b t + c >= 0.
    static List<float[]> clip(List<float[]> poly, float a, float b, float c) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < poly.size(); i++) {
            float[] p = poly.get(i), q = poly.get((i + 1) % poly.size());
            float dp = a * p[0] + b * p[1] + c, dq = a * q[0] + b * q[1] + c;
            if (dp >= 0) {
                out.add(p);
            }
            if (dp >= 0 != dq >= 0) {
                float k = dp / (dp - dq);
                out.add(new float[] { p[0] + (q[0] - p[0]) * k, p[1] + (q[1] - p[1]) * k });
            }
        }
        return out;
    }

    // A convex polygon (face fractions, counter-clockwise) in one flat tone, as a fan of quads (a triangle repeats its
    // last point).
    void polygon(float[] p0, float[] p1, float[] p2, float[] p3, List<float[]> poly, Paint tone) {
        if (poly.size() < 3) {
            return;
        }
        float u = (tone.x + 2.0F) / sheetW, v = (tone.y + 2.0F) / sheetH;
        float[] uv = { u, v, u, v, u, v, u, v };
        float[] first = bilinear(p0, p1, p2, p3, poly.get(0)[0], poly.get(0)[1]);
        for (int i = 1; i + 1 < poly.size(); i += 2) {
            float[] a = bilinear(p0, p1, p2, p3, poly.get(i)[0], poly.get(i)[1]);
            float[] b = bilinear(p0, p1, p2, p3, poly.get(i + 1)[0], poly.get(i + 1)[1]);
            float[] c = i + 2 < poly.size() ? bilinear(p0, p1, p2, p3, poly.get(i + 2)[0], poly.get(i + 2)[1]) : b;
            quad(first, a, b, c, uv, false);
        }
    }

    // A long round facet (p0 p1 at its start, p2 p3 at its end, along u) in broad shading's steps: its band tone over its
    // middle third, a band lighter over the third toward the light (the start) and a band darker over the last, hard
    // steps; a casing strip keeps its seam bands (with their rivets) at both ends at 1 texel per px.
    void stepped(float[] p0, float[] p1, float[] p2, float[] p3, float length, Paint band, boolean strip) {
        int tone = 1;
        for (int i = 0; i < TONES.length; i++) {
            if (TONES[i] == band || STRIPS[i] == band) {
                tone = i;
            }
        }
        float seam = strip ? 4.0F / length : 0.0F;
        if (strip) {
            // Across, the strip's columns as slab cuts them (the rivet column at 1 texel per px); its seam rows 8..12 at
            // the start (the deepest row at the very end) and 0..4 at the end.
            float w = (length(sub(p1, p0)) + length(sub(p2, p3))) / 2.0F;
            float[] xs = cuts(band.cols, w);
            float tx = 0;
            for (int i = 0; i < band.cols.length; i++) {
                cell(p0, p1, p2, p3, xs[i] / w, xs[i + 1] / w, 0, seam, band, tx, 8, tx + band.cols[i][0], 12, false);
                cell(p0, p1, p2, p3, xs[i] / w, xs[i + 1] / w, 1 - seam, 1, band, tx, 0, tx + band.cols[i][0], 4, false);
                tx += band.cols[i][0];
            }
        }
        Paint[] steps = { TONES[Math.min(5, tone + 1)], TONES[tone], TONES[Math.max(1, tone - 1)] };
        float span = (1 - 2 * seam) / 3.0F;
        for (int i = 0; i < 3; i++) {
            Paint step = steps[i];
            float t0 = seam + span * i, t1 = seam + span * (i + 1);
            cell(p0, p1, p2, p3, 0, 1, t0, t1, step, 1, 1, 3, 3, false);
        }
    }

    // Where each column (or row) of a panel starts and ends across a face size px long: fixed ones at their texels, the
    // stretching ones sharing the rest by weight.
    static float[] cuts(int[][] segments, float size) {
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
    void cell(float[] p0, float[] p1, float[] p2, float[] p3, float s0, float s1, float t0, float t1, Paint paint,
            float tu0, float tv0, float tu1, float tv1, boolean glass) {
        if (s1 - s0 <= 1.0E-5F || t1 - t0 <= 1.0E-5F) {
            return;
        }
        float[] bl = bilinear(p0, p1, p2, p3, s0, t0), br = bilinear(p0, p1, p2, p3, s1, t0);
        float[] tr = bilinear(p0, p1, p2, p3, s1, t1), tl = bilinear(p0, p1, p2, p3, s0, t1);
        float u0 = (paint.x + tu0) / sheetW, u1 = (paint.x + tu1) / sheetW, v0 = (paint.y + tv0) / sheetH, v1 = (paint.y + tv1) / sheetH;
        quad(bl, br, tr, tl, new float[] { u0, v1, u1, v1, u1, v0, u0, v0 }, glass);
    }

    static float[] bilinear(float[] p0, float[] p1, float[] p2, float[] p3, float s, float t) {
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) {
            float bottom = p0[k] + (p1[k] - p0[k]) * s, top = p3[k] + (p2[k] - p3[k]) * s;
            out[k] = bottom + (top - bottom) * t;
        }
        return out;
    }

    static float[] at(float[] origin, float[] right, float[] up, float x, float y) {
        return new float[] { origin[0] + right[0] * x + up[0] * y, origin[1] + right[1] * x + up[1] * y, origin[2] + right[2] * x + up[2] * y };
    }

    // A quad in one paint, wound counter-clockwise seen from outside, its size taken from its edges.
    void flat(float[] p0, float[] p1, float[] p2, float[] p3, Paint paint, boolean glass) {
        if (paint.kind == Kind.FLAT) {
            cell(p0, p1, p2, p3, 0, 1, 0, 1, paint, 1, 1, 3, 3, glass);
            return;
        }
        float w = (length(sub(p1, p0)) + length(sub(p2, p3))) / 2.0F, h = (length(sub(p3, p0)) + length(sub(p2, p1))) / 2.0F;
        slab(p0, p1, p2, p3, w, h, paint, glass);
    }

    // The band tone (or casing strip) for a facet facing n.
    static Paint band(Paint marker, float[] n) {
        float d = dot(normalize(n), LIGHT);
        // The game shades faces turned away from the light as well, so the darkest band is never used on round parts.
        int tone = d > 0.85F ? 5 : d > 0.55F ? 4 : d > 0.2F ? 3 : d > -0.2F ? 2 : 1;
        return marker == Paint.CASING ? STRIPS[tone] : marker == Paint.HARDENED ? HARD[tone] : TONES[tone];
    }

    static float length(float[] v) {
        return (float) Math.sqrt(dot(v, v));
    }

    static float[] normalize(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1.0E-9F ? v : new float[] { v[0] / l, v[1] / l, v[2] / l };
    }

    // A flat quad wound to face along outward (whichever way its points were given).
    void orient(float[] p0, float[] p1, float[] p2, float[] p3, float[] outward, Paint given) {
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
    void prismU(float cx, float cy, float[][] profile, int sides, double start, Paint side, @Nullable Paint capLit,
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
                Paint paint = side.kind == Kind.BANDED ? band(side, new float[] { (float) Math.cos(mid), (float) Math.sin(mid), 0.0F }) : side;
                if (broadShading && side.kind == Kind.BANDED && side != Paint.HARDENED && ub - ua >= STEP_FROM) {
                    // The facet runs along u from p0 p1 to p3 p2: stepped wants its start at the bottom (p0 p1).
                    stepped(p0, p1, p2, p3, ub - ua, paint, side == Paint.CASING);
                } else {
                    flat(p0, p1, p2, p3, paint, false);
                }
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
    interface FacetFilter {
        boolean skip(int segment, int facet);
    }

    // A flat ring (a disc when inner is 0) across u at u, facing +u or -u; its facets on the top-left half seen from
    // outside are lit, the rest shade.
    void ringU(float cx, float cy, float u, float outer, float inner, int sides, double start, boolean facingPositive, Paint lit, Paint shade) {
        ringU(cx, cy, u, outer, inner, sides, start, facingPositive, lit, shade, null);
    }

    // The same, leaving out the facets skip names (its segment is always 0).
    void ringU(float cx, float cy, float u, float outer, float inner, int sides, double start, boolean facingPositive, Paint lit, Paint shade,
            @Nullable FacetFilter skip) {
        double step = 2.0 * Math.PI / sides;
        // Seen from +u, right is +x; from -u, right is -x. Up is +y either way.
        float rightSign = facingPositive ? 1.0F : -1.0F;
        for (int f = 0; f < sides; f++) {
            if (skip != null && skip.skip(0, f)) {
                continue;
            }
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
    void prismY(float x, float u, float y0, float y1, float r, Paint side, Paint cap) {
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
    void prismX(float u, float y, float x0, float x1, float r, Paint side, Paint cap) {
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

    // A polygon tube from a to b (any direction) of radius r, its facets banded or painted side; open ends (they run
    // into what they join).
    void tube(float[] a, float[] b, float r, int sides, Paint side) {
        float[] axis = normalize(sub(b, a));
        float[] helper = Math.abs(axis[1]) < 0.9F ? new float[] { 0, 1, 0 } : new float[] { 1, 0, 0 };
        float[] b1 = normalize(cross(axis, helper));
        float[] b2 = cross(axis, b1);
        double step = 2.0 * Math.PI / sides;
        for (int f = 0; f < sides; f++) {
            double a0 = f * step + step / 2.0, a1 = a0 + step, mid = (a0 + a1) / 2.0;
            float[] d0 = { b1[0] * (float) Math.cos(a0) + b2[0] * (float) Math.sin(a0), b1[1] * (float) Math.cos(a0) + b2[1] * (float) Math.sin(a0),
                    b1[2] * (float) Math.cos(a0) + b2[2] * (float) Math.sin(a0) };
            float[] d1 = { b1[0] * (float) Math.cos(a1) + b2[0] * (float) Math.sin(a1), b1[1] * (float) Math.cos(a1) + b2[1] * (float) Math.sin(a1),
                    b1[2] * (float) Math.cos(a1) + b2[2] * (float) Math.sin(a1) };
            float[] out = { b1[0] * (float) Math.cos(mid) + b2[0] * (float) Math.sin(mid), b1[1] * (float) Math.cos(mid) + b2[1] * (float) Math.sin(mid),
                    b1[2] * (float) Math.cos(mid) + b2[2] * (float) Math.sin(mid) };
            float[] p0 = { a[0] + d0[0] * r, a[1] + d0[1] * r, a[2] + d0[2] * r };
            float[] p1 = { a[0] + d1[0] * r, a[1] + d1[1] * r, a[2] + d1[2] * r };
            float[] p2 = { b[0] + d1[0] * r, b[1] + d1[1] * r, b[2] + d1[2] * r };
            float[] p3 = { b[0] + d0[0] * r, b[1] + d0[1] * r, b[2] + d0[2] * r };
            orient(p0, p1, p2, p3, out, side);
        }
    }

    // --- Output ---

    // Appends a quad: its points in the model's frame (px), turned with the rotor while building it, mapped to blocks
    // from the minimum corner (mirrored end for end when flipped), with its normal; to the glass list, or the glow
    // list while glowing is set.
    void quad(float[] p0, float[] p1, float[] p2, float[] p3, float[] uv, boolean glass) {
        float[][] points = { turn(p0), turn(p1), turn(p2), turn(p3) };
        // Across the diagonals, so a quad with two points the same (a triangle) still gets its normal.
        float[] n = cross(sub(points[2], points[0]), sub(points[3], points[1]));
        float length = (float) Math.sqrt(dot(n, n));
        if (length < 1.0E-6F) {
            return;
        }
        float nx = n[0] / length, ny = n[1] / length, nu = n[2] / length;
        if (flip) {
            nx = -nx;
            nu = -nu;
        }
        Floats out = glass ? cutout : glowing ? glow : solid;
        for (int i = 0; i < 4; i++) {
            float[] p = points[i];
            float x = flip ? widthPx - p[0] : p[0], u = flip ? lengthPx - p[2] : p[2];
            if (alongX) {
                out.add(u / PX, p[1] / PX, (widthPx - x) / PX, uv[2 * i], uv[2 * i + 1], nu, ny, -nx);
            } else {
                out.add(x / PX, p[1] / PX, u / PX, uv[2 * i], uv[2 * i + 1], nx, ny, nu);
            }
        }
    }

    float[] turn(float[] p) {
        if (!rotating) {
            return p;
        }
        float dx = p[0] - pivotX, dy = p[1] - pivotY;
        return new float[] { pivotX + (float) (dx * cos - dy * sin), pivotY + (float) (dx * sin + dy * cos), p[2] };
    }

    static float[] sub(float[] a, float[] b) {
        return new float[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }

    static float[] cross(float[] a, float[] b) {
        return new float[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
    }

    static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    // A growable float array.
    static final class Floats {
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
