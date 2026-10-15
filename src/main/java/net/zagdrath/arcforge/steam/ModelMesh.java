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

    public enum Kind { FLAT, PANEL, TILED, BANDED, MODULE }

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
        // The Pumpjack's sheet only: the hazard stripe, as a tile.
        HAZARD(16, 4, 4, 4, Kind.TILED),
        // Large model sheets only (the Pumpjack's and the refinery tower's), rows 96-127 (tools/LargeModelTextureGen.java):
        // the small parts' tiles laid at 1:1 (the furnace brick, a screen, the motor's ribs, a seamless grating, the
        // girder flange, the hazard edge, the burner fire) and the round parts' ramps, 2 to 14 px across, side by side (see rampFacet). Big faces are
        // plated instead (see plating). POLISHED marks the polished rod: its own bright ramp.
        M_MOTOR(64, 96, 16, 16, Kind.MODULE), M_GRATING(80, 96, 16, 16, Kind.MODULE), M_FLANGE(96, 104, 16, 8, Kind.MODULE),
        M_HAZARD(112, 104, 8, 4, Kind.TILED), RAMPS(0, 112, 104, 8, Kind.TILED), M_FIRE(0, 120, 8, 6, Kind.MODULE),
        // A furnace's refractory firebrick and a control panel's lit screen (drawn in the glow list).
        M_BRICK(0, 96, 16, 8, Kind.MODULE), M_SCREEN(16, 96, 8, 6, Kind.MODULE),
        POLISHED(104, 112, 4, 8, Kind.BANDED),
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
    // Whether this is a large model (the Pumpjack and the refinery tower; docs/TEXTURE_STYLE.md, "Large model
    // texturing"): its big faces are plated as a few continuous plates sized to the part (see plating), its small parts'
    // faces laid in their tiles on a grid from moduleOrigin, no face over FLAT_MOST px each way is one flat tone, and
    // round parts carry a ramp across them (see rampFacet). Nothing is stretched, and no light is painted across a face:
    // the game's face shading lights it.
    boolean modules;
    float[] moduleOrigin = { 0, 0, 0 };
    static final float FLAT_MOST = 4.5F;
    // While building the rotor: its angle about the pivot (the shaft).
    boolean rotating;
    double cos = 1.0, sin = 0.0;
    // What the nozzles stop at: boxes {x0, y0, u0, x1, y1, u1}, cylinders along u {cx, cy, r, u0, u1} and upright
    // cylinders {x, u, radius, y0, y1}.
    final List<float[]> solidBoxes = new ArrayList<>();
    final List<float[]> solidCylinders = new ArrayList<>();
    final List<float[]> uprights = new ArrayList<>();

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
        uprights.clear();
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
        // Upright cylinders (round vessels and stacks).
        for (float[] c : uprights) {
            float cx = c[0], cu = c[1], r = c[2], y0 = c[3], y1 = c[4];
            if (axis == 1) {
                float dx = p[0] - cx, du = p[2] - cu;
                if (dx * dx + du * du < r * r) {
                    float near = sign > 0 ? y0 - p[1] : p[1] - y1;
                    float far = sign > 0 ? y1 - p[1] : p[1] - y0;
                    if (far > 0.0F) {
                        best = Math.min(best, Math.max(0.0F, near));
                    }
                }
                continue;
            }
            if (p[1] < y0 || p[1] > y1) {
                continue;
            }
            float offset = axis == 0 ? p[2] - cu : p[0] - cx;
            if (Math.abs(offset) >= r) {
                continue;
            }
            float reach = (float) Math.sqrt(r * r - offset * offset);
            float centre = axis == 0 ? cx : cu;
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
        if (modules && glass && (paint == Paint.GLASS || paint == Paint.GLASS_SMALL)) {
            glassPane(p0, p1, p3, w, h);
            return;
        }
        if (modules && !glass) {
            Plating plating = plating(paint, w, h);
            if (plating != null) {
                List<float[]> points = List.of(p0, p1, p2, p3);
                plateIn(plateFrame(points, plating), points);
                return;
            }
            Paint module = module(paint);
            if (module != null) {
                modulePolygon(List.of(p0, p1, p2, p3), module);
                return;
            }
        }
        Paint p = paint.kind == Kind.BANDED ? Paint.FACE : paint;
        // A large model stretches nothing: a panel not plated (the FE band) drops to its flat colour.
        while (p.kind == Kind.PANEL && (modules && !glass || w < p.least(true) || h < p.least(false) || p.riveted && Math.min(w, h) < 9.0F)) {
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
            default -> {
                if (modules && !glass && p.kind == Kind.FLAT && Math.min(w, h) > FLAT_MOST) {
                    // A large model's flat colour more than FLAT_MOST px each way is laid in 2x2 texel cells at 1:1; a
                    // thinner face (a blade, a rib, an FE band) may be one flat tone, which has nothing to stretch.
                    for (float x = 0; x < w - 0.001F; x += 2.0F) {
                        for (float y = 0; y < h - 0.001F; y += 2.0F) {
                            float x1 = Math.min(w, x + 2.0F), y1 = Math.min(h, y + 2.0F);
                            cell(p0, p1, p2, p3, x / w, x1 / w, y / h, y1 / h, p, 1, 1, 1 + x1 - x, 1 + y1 - y, false);
                        }
                    }
                } else {
                    cell(p0, p1, p2, p3, 0, 1, 0, 1, p, 1, 1, 3, 3, glass);
                }
            }
        }
    }

    // --- Large model plating (docs/TEXTURE_STYLE.md, "Large model texturing") ---

    // A large model's steel, as its sheet holds it (tools/LargeModelTextureGen.java): its grain field, its edge columns
    // (lit, dark and seam side by side, 64 tall from EDGE_COLUMN_Y), its edge rows (lit, dark and seam one above the
    // next, 128 long) and its 2x2 rivet head at RIVET_Y.
    enum Steel {
        MACHINE(0, 128, 116, 64, 116, 224, 0),
        GENERATOR(0, 192, 116, 32, 119, 227, 2);

        final int fieldX, fieldY, fieldW, fieldH, edgeColumn, edgeRow, rivetX;

        Steel(int fieldX, int fieldY, int fieldW, int fieldH, int edgeColumn, int edgeRow, int rivetX) {
            this.fieldX = fieldX;
            this.fieldY = fieldY;
            this.fieldW = fieldW;
            this.fieldH = fieldH;
            this.edgeColumn = edgeColumn;
            this.edgeRow = edgeRow;
            this.rivetX = rivetX;
        }
    }

    static final int EDGE_COLUMN_Y = 128, EDGE_LENGTH = 128, RIVET_Y = 232;
    // The most a plate spans (4 blocks); a bigger face is cut into as few plates as keep each within it.
    static final float PLATE_MOST = 64.0F;

    // How a face is plated: its steel, whether its plates are riveted along their edges, whether each has a recessed
    // panel (a deck plate), and whether its plates have edges at all (a pressed part, like a domed head, is bare grain).
    record Plating(Steel steel, boolean riveted, boolean deck, boolean edged) {
        Plating(Steel steel, boolean riveted, boolean deck) {
            this(steel, riveted, deck, true);
        }
    }

    // The plating a face painted paint, w x h px, gets, or null: the plated paints, and a plain plate for a face in a
    // band tone more than FLAT_MOST px either way (a thin strip shows a slice of a plate at 1:1).
    static @Nullable Plating plating(Paint paint, float w, float h) {
        return switch (paint) {
            case PLATE -> new Plating(Steel.MACHINE, true, false);
            case PLAIN, FOOTING -> new Plating(Steel.MACHINE, false, false);
            case DECK -> new Plating(Steel.MACHINE, false, true);
            case GEN_PLATE -> new Plating(Steel.GENERATOR, true, false);
            case GEN_PLAIN -> new Plating(Steel.GENERATOR, false, false);
            case DARK, SHADE, FACE_SHADE, FACE, MID, LIT, BANDED -> Math.max(w, h) > FLAT_MOST ? new Plating(Steel.MACHINE, false, false) : null;
            case GEN_DARK, GEN_FACE, GEN_MID, GEN_LIT -> Math.max(w, h) > FLAT_MOST ? new Plating(Steel.GENERATOR, false, false) : null;
            default -> null;
        };
    }

    // The tile a small part's face is laid in at 1:1 (a grating, a girder flange, the motor's ribs, a burner's fire),
    // or null.
    static @Nullable Paint module(Paint paint) {
        return switch (paint) {
            case GRATE -> Paint.M_GRATING;
            case GLOW -> Paint.M_FIRE;
            case STRAP -> Paint.M_FLANGE;
            default -> paint.kind == Kind.MODULE ? paint : null;
        };
    }

    // A face w x h px (s across from its left, t up from its bottom) as plates: as few as keep each within PLATE_MOST
    // (and within its steel's grain field), each with a 1 px lit top and left edge and a dark bottom and right edge,
    // the darkest seam only where two of its plates meet, rivets along its edges every 4 px (riveted, on plates 9 px or
    // more each way) and a recessed panel with its edges inverted (deck). Each plate shows its own window of the grain
    // field and of the edge strips, picked from seed, so the grain doesn't repeat across the face. Returned as rectangles
    // {s0, s1, t0, t1, u, v}, each showing the sheet at 1:1 with texel (u, v) at its top left (s0, t1): runs of texels
    // that read on along the sheet are merged, across and down.
    static List<float[]> plateLayout(float w, float h, Plating plating, long seed) {
        Steel steel = plating.steel();
        int across = Math.max(1, (int) Math.ceil(w / PLATE_MOST - 1.0E-4F));
        int down = Math.max(1, (int) Math.ceil(h / Math.min(PLATE_MOST, steel.fieldH) - 1.0E-4F));
        float pw = w / across, ph = h / down;
        int cols = Math.max(1, (int) Math.ceil(pw - 1.0E-4F)), rows = Math.max(1, (int) Math.ceil(ph - 1.0E-4F));
        List<float[]> rects = new ArrayList<>();
        for (int i = 0; i < across; i++) {
            for (int j = 0; j < down; j++) {
                java.util.Random random = new java.util.Random(seed * 31L + i * 131L + j * 17L);
                int fx = steel.fieldX + random.nextInt(Math.max(1, steel.fieldW - cols + 1));
                int fy = steel.fieldY + random.nextInt(Math.max(1, steel.fieldH - rows + 1));
                int ex = random.nextInt(Math.max(1, EDGE_LENGTH - cols + 1));
                int ey = EDGE_COLUMN_Y + random.nextInt(Math.max(1, 64 - rows + 1));
                boolean seamRight = i < across - 1, seamBottom = j < down - 1;
                boolean edgesAcross = plating.edged() && pw >= 4.0F, edgesDown = plating.edged() && ph >= 4.0F;
                int darkCol = edgesAcross ? cols - 1 - (seamRight ? 1 : 0) : cols, darkRow = edgesDown ? rows - 1 - (seamBottom ? 1 : 0) : rows;
                boolean rivets = plating.riveted() && pw >= 9.0F && ph >= 9.0F;
                boolean deck = plating.deck() && pw >= 10.0F && ph >= 10.0F;
                // Each texel's source on the sheet.
                int[][] u = new int[rows][cols], v = new int[rows][cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        int su = fx + c, sv = fy + r;
                        if (edgesDown && r == 0) {
                            su = ex + c;
                            sv = steel.edgeRow;
                        } else if (edgesDown && seamBottom && r == rows - 1) {
                            su = ex + c;
                            sv = steel.edgeRow + 2;
                        } else if (edgesDown && r == darkRow) {
                            su = ex + c;
                            sv = steel.edgeRow + 1;
                        } else if (edgesAcross && c == 0) {
                            su = steel.edgeColumn;
                            sv = ey + r;
                        } else if (edgesAcross && seamRight && c == cols - 1) {
                            su = steel.edgeColumn + 2;
                            sv = ey + r;
                        } else if (edgesAcross && c == darkCol) {
                            su = steel.edgeColumn + 1;
                            sv = ey + r;
                        } else if (rivets && rivetAt(c, r, darkCol, darkRow) != null) {
                            int[] at = rivetAt(c, r, darkCol, darkRow);
                            su = steel.rivetX + at[0];
                            sv = RIVET_Y + at[1];
                        } else if (deck && c >= 3 && c <= darkCol - 3 && r >= 3 && r <= darkRow - 3
                                && (c == 3 || r == 3 || c == darkCol - 3 || r == darkRow - 3)) {
                            // The recess: its top and left lip in shadow, its bottom and right lip lit.
                            if (r == 3) {
                                su = ex + c;
                                sv = steel.edgeRow + 1;
                            } else if (c == 3) {
                                su = steel.edgeColumn + 1;
                                sv = ey + r;
                            } else if (r == darkRow - 3) {
                                su = ex + c;
                                sv = steel.edgeRow;
                            } else {
                                su = steel.edgeColumn;
                                sv = ey + r;
                            }
                        }
                        u[r][c] = su;
                        v[r][c] = sv;
                    }
                }
                mergeTexels(u, v, i * pw, (i + 1) * pw, h - (j + 1) * ph, h - j * ph, rects);
            }
        }
        return rects;
    }

    // Where texel (c, r) of a riveted plate (its dark column and row given) falls in a rivet head ({x, y} in the 2x2
    // head), or null: a row of heads 2 px in from each edge, one every 4 px.
    private static int @Nullable [] rivetAt(int c, int r, int darkCol, int darkRow) {
        int[] rowsAt = { 2, darkRow - 3 }, colsAt = { 2, darkCol - 3 };
        for (int y0 : rowsAt) {
            if (r >= y0 && r < y0 + 2 && c >= 2 && c + 1 < darkCol - 1 && (c - 2) % 4 < 2 && (c - 2) / 4 * 4 + 3 < darkCol - 1) {
                return new int[] { (c - 2) % 4, r - y0 };
            }
        }
        for (int x0 : colsAt) {
            if (c >= x0 && c < x0 + 2 && r >= 2 && r + 1 < darkRow - 1 && (r - 2) % 4 < 2 && (r - 2) / 4 * 4 + 3 < darkRow - 1) {
                return new int[] { c - x0, (r - 2) % 4 };
            }
        }
        return null;
    }

    // A plate's texel sources (u, v per texel, rows down from its top) merged into as few 1:1 rectangles as read on
    // along the sheet, in the face's px (the plate from s0 to s1 across and t0 to t1 up; its last column and row may be
    // cut).
    private static void mergeTexels(int[][] u, int[][] v, float s0, float s1, float t0, float t1, List<float[]> rects) {
        int rows = u.length, cols = u[0].length;
        // Runs along each row: {c0, c1 (exclusive), u at c0, v}; then runs that line up in the next row, with v one on,
        // extend down.
        List<int[]> open = new ArrayList<>();
        for (int r = 0; r <= rows; r++) {
            List<int[]> runs = new ArrayList<>();
            if (r < rows) {
                int c0 = 0;
                for (int c = 1; c <= cols; c++) {
                    if (c == cols || u[r][c] != u[r][c - 1] + 1 || v[r][c] != v[r][c - 1]) {
                        runs.add(new int[] { c0, c, u[r][c0], v[r][c0] });
                        c0 = c;
                    }
                }
            }
            List<int[]> next = new ArrayList<>();
            for (int[] run : runs) {
                int[] extended = null;
                for (int[] o : open) {
                    // {c0, c1, u, v at its top, r0, r1 (exclusive)}
                    if (o[0] == run[0] && o[1] == run[1] && o[2] == run[2] && o[3] + (o[5] - o[4]) == run[3]) {
                        extended = o;
                        break;
                    }
                }
                if (extended != null) {
                    open.remove(extended);
                    extended[5] = r + 1;
                    next.add(extended);
                } else {
                    next.add(new int[] { run[0], run[1], run[2], run[3], r, r + 1 });
                }
            }
            for (int[] o : open) {
                rects.add(new float[] { s0 + o[0], Math.min(s1, s0 + o[1]), Math.max(t0, t1 - o[5]), t1 - o[4], o[2], o[3] });
            }
            open = next;
        }
    }

    // A face's plane: its points' place in it (s across, t up, from base), with base at the face's bottom-left
    // corner, so s and t run from 0.
    record PlateFrame(float[] base, float[] right, float[] up, float w, float h, List<float[]> rects) {}

    // The frame a flat face (its points wound counter-clockwise seen from outside) is plated in: across runs level (or
    // along u on a face looking up or down), up runs up it; its plates from plating, seeded by where the face is.
    PlateFrame plateFrame(List<float[]> points, Plating plating) {
        float[] n = normalize(cross(sub(points.get(2), points.get(0)), sub(points.get(3), points.get(1))));
        float[] level = Math.abs(n[1]) > 0.9F ? new float[] { 0, 0, -1 } : new float[] { 0, 1, 0 };
        float[] right = normalize(cross(level, n)), up = cross(n, right);
        float sMin = Float.MAX_VALUE, sMax = -Float.MAX_VALUE, tMin = Float.MAX_VALUE, tMax = -Float.MAX_VALUE;
        for (float[] p : points) {
            float s = dot(p, right), t = dot(p, up);
            sMin = Math.min(sMin, s);
            sMax = Math.max(sMax, s);
            tMin = Math.min(tMin, t);
            tMax = Math.max(tMax, t);
        }
        float depth = dot(points.get(0), n);
        float[] base = { n[0] * depth + right[0] * sMin + up[0] * tMin, n[1] * depth + right[1] * sMin + up[1] * tMin,
                n[2] * depth + right[2] * sMin + up[2] * tMin };
        return plateFrame(base, right, up, sMax - sMin, tMax - tMin, plating);
    }

    // The frame for a plate laid from base across right and up, w x h px (a curved part's side plate, shared by all its
    // facets).
    PlateFrame plateFrame(float[] base, float[] right, float[] up, float w, float h, Plating plating) {
        long seed = 1469598103934665603L;
        for (float value : new float[] { base[0], base[1], base[2], right[0], right[1], right[2], up[0], up[1], up[2] }) {
            seed = (seed ^ Math.round(value * 8.0F)) * 1099511628211L;
        }
        seed = (seed ^ plating.steel().ordinal() ^ (plating.riveted() ? 2 : 0) ^ (plating.deck() ? 4 : 0)) * 1099511628211L;
        LayoutKey key = new LayoutKey(Math.round(w * 64.0F), Math.round(h * 64.0F), plating, seed);
        List<float[]> rects;
        synchronized (LAYOUTS) {
            rects = LAYOUTS.get(key);
        }
        if (rects == null) {
            rects = plateLayout(w, h, plating, seed);
            synchronized (LAYOUTS) {
                LAYOUTS.put(key, rects);
            }
        }
        return new PlateFrame(base, right, up, w, h, rects);
    }

    // Plate layouts already worked out (the parts that turn are built every frame, the same each time), the most
    // recently used kept.
    private record LayoutKey(int w, int h, Plating plating, long seed) {}

    private static final java.util.Map<LayoutKey, List<float[]>> LAYOUTS = new java.util.LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<LayoutKey, List<float[]>> eldest) {
            return size() > 8192;
        }
    };

    // A flat convex polygon (points in the frame's plane, wound counter-clockwise seen from outside) plated as its frame
    // lays it out.
    void plateIn(PlateFrame frame, List<float[]> points) {
        List<float[]> st = new ArrayList<>();
        for (float[] p : points) {
            float[] d = sub(p, frame.base());
            st.add(new float[] { dot(d, frame.right()), dot(d, frame.up()) });
        }
        paintPlane(st, frame.base(), frame.right(), frame.up(), frame.rects(), false);
    }

    // A strip of a curved shell (p0 p1 across it, s0 to s1 round the shell; p0 p3 up it, 0 to height) plated as the
    // shell's unrolled layout lays it out (rects in the shell's s and t), so its plates carry on from facet to facet.
    void plateStrip(float[] p0, float[] p1, float[] p2, float[] p3, float s0, float s1, float height, List<float[]> rects) {
        for (float[] rect : rects) {
            float a = Math.max(s0, rect[0]), b = Math.min(s1, rect[1]);
            if (b - a <= 1.0E-4F) {
                continue;
            }
            float c = rect[2], d = rect[3];
            float fa = (a - s0) / (s1 - s0), fb = (b - s0) / (s1 - s0), fc = c / height, fd = d / height;
            float u0 = (rect[4] + a - rect[0]) / sheetW, u1 = (rect[4] + b - rect[0]) / sheetW;
            float vTop = (rect[5] + rect[3] - d) / sheetH, vBottom = (rect[5] + rect[3] - c) / sheetH;
            quad(bilinear(p0, p1, p2, p3, fa, fc), bilinear(p0, p1, p2, p3, fb, fc), bilinear(p0, p1, p2, p3, fb, fd),
                    bilinear(p0, p1, p2, p3, fa, fd), new float[] { u0, vBottom, u1, vBottom, u1, vTop, u0, vTop }, false);
        }
    }

    // A flat convex polygon (its points in the frame) laid in a small part's tile at 1 texel per px, on a grid of the
    // tile's size from moduleOrigin across the face's plane (across level, or along u on a face looking up or down).
    // The girder flange lies along the face's longer side.
    void modulePolygon(List<float[]> points, Paint tile) {
        float[] n = normalize(cross(sub(points.get(2), points.get(0)), sub(points.get(3), points.get(1))));
        float[] level = Math.abs(n[1]) > 0.9F ? new float[] { 0, 0, -1 } : new float[] { 0, 1, 0 };
        float[] right = normalize(cross(level, n)), up = cross(n, right);
        float sMin = Float.MAX_VALUE, sMax = -Float.MAX_VALUE, tMin = Float.MAX_VALUE, tMax = -Float.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            sMin = Float.MAX_VALUE;
            sMax = -Float.MAX_VALUE;
            tMin = Float.MAX_VALUE;
            tMax = -Float.MAX_VALUE;
            for (float[] p : points) {
                float[] d = sub(p, moduleOrigin);
                float s = dot(d, right), t = dot(d, up);
                sMin = Math.min(sMin, s);
                sMax = Math.max(sMax, s);
                tMin = Math.min(tMin, t);
                tMax = Math.max(tMax, t);
            }
            if (pass == 0 && tile == Paint.M_FLANGE && tMax - tMin > sMax - sMin) {
                // Turned a quarter: across runs up the face, up runs back (right x up still points out).
                float[] turned = up;
                up = new float[] { -right[0], -right[1], -right[2] };
                right = turned;
            } else {
                break;
            }
        }
        // The plane's own offset from moduleOrigin along its normal.
        float depth = dot(sub(points.get(0), moduleOrigin), n);
        float[] base = { moduleOrigin[0] + n[0] * depth, moduleOrigin[1] + n[1] * depth, moduleOrigin[2] + n[2] * depth };
        List<float[]> st = new ArrayList<>(), rects = new ArrayList<>();
        for (float[] p : points) {
            float[] d = sub(p, base);
            st.add(new float[] { dot(d, right), dot(d, up) });
        }
        int i0 = (int) Math.floor(sMin / tile.w + 1.0E-4F), i1 = (int) Math.ceil(sMax / tile.w - 1.0E-4F);
        int j0 = (int) Math.floor(tMin / tile.h + 1.0E-4F), j1 = (int) Math.ceil(tMax / tile.h - 1.0E-4F);
        for (int i = i0; i < i1; i++) {
            for (int j = j0; j < j1; j++) {
                rects.add(new float[] { i * tile.w, (i + 1) * tile.w, j * tile.h, (j + 1) * tile.h, tile.x, tile.y });
            }
        }
        paintPlane(st, base, right, up, rects, false);
    }

    // A large model's pane of glass, w x h px (p0 its bottom-left, p1 along its bottom, p3 up its left), at 1:1: the
    // glass tile's 1 px frame round its edge (lit top and left, dark bottom and right) in runs of up to 10 px, and its
    // glints in the top-left corner where there's room. Its clear middle needs nothing drawn.
    void glassPane(float[] p0, float[] p1, float[] p3, float w, float h) {
        int gx = Paint.GLASS.x, gy = Paint.GLASS.y;
        List<float[]> rects = new ArrayList<>();
        for (float a = 0; a < w - 1.0F - 1.0E-4F; a += 10.0F) {
            rects.add(new float[] { a, Math.min(w - 1.0F, a + 10.0F), h - 1.0F, h, gx, gy });
        }
        for (float a = 1.0F; a < w - 1.0E-4F; a += 10.0F) {
            rects.add(new float[] { a, Math.min(w, a + 10.0F), 0.0F, 1.0F, gx + 1, gy + 11 });
        }
        for (float t = h - 1.0F; t > 1.0F + 1.0E-4F; t -= 10.0F) {
            rects.add(new float[] { 0.0F, 1.0F, Math.max(1.0F, t - 10.0F), t, gx, gy + 1 });
            rects.add(new float[] { w - 1.0F, w, Math.max(1.0F, t - 10.0F), t, gx + 11, gy + 1 });
        }
        rects.add(new float[] { w - 1.0F, w, h - 1.0F, h, gx + 11, gy });
        rects.add(new float[] { 0.0F, 1.0F, 0.0F, 1.0F, gx, gy + 11 });
        if (w >= 8.0F && h >= 8.0F) {
            rects.add(new float[] { 2.0F, 7.0F, h - 7.0F, h - 2.0F, gx + 2, gy + 2 });
        }
        List<float[]> st = List.of(new float[] { 0, 0 }, new float[] { w, 0 }, new float[] { w, h }, new float[] { 0, h });
        paintPlane(st, p0, normalize(sub(p1, p0)), normalize(sub(p3, p0)), rects, true);
    }

    // A flat convex polygon (st: its points across and up a plane from base) cut by rectangles {s0, s1, t0, t1, u, v},
    // each piece showing the sheet at 1:1 with texel (u, v) at its rectangle's top left (s0, t1); to the glass list when
    // glass is set.
    private void paintPlane(List<float[]> st, float[] base, float[] right, float[] up, List<float[]> rects, boolean glass) {
        float area = 0;
        for (int i = 0; i < st.size(); i++) {
            float[] a = st.get(i), b = st.get((i + 1) % st.size());
            area += a[0] * b[1] - b[0] * a[1];
        }
        if (Math.abs(area) < 1.0E-6F) {
            return;
        }
        if (area < 0) {
            // Counter-clockwise across and up is counter-clockwise seen from outside.
            st = new ArrayList<>(st);
            java.util.Collections.reverse(st);
        }
        float sMin = Float.MAX_VALUE, sMax = -Float.MAX_VALUE, tMin = Float.MAX_VALUE, tMax = -Float.MAX_VALUE;
        for (float[] p : st) {
            sMin = Math.min(sMin, p[0]);
            sMax = Math.max(sMax, p[0]);
            tMin = Math.min(tMin, p[1]);
            tMax = Math.max(tMax, p[1]);
        }
        for (float[] rect : rects) {
            if (rect[1] <= sMin || rect[0] >= sMax || rect[3] <= tMin || rect[2] >= tMax) {
                continue;
            }
            List<float[]> clipped = clip(clip(clip(clip(st, 1, 0, -rect[0]), -1, 0, rect[1]), 0, 1, -rect[2]), 0, -1, rect[3]);
            // Corners the clipping repeats would fan out into quads with no area.
            List<float[]> piece = new ArrayList<>();
            for (float[] p : clipped) {
                float[] last = piece.isEmpty() ? null : piece.get(piece.size() - 1);
                if (last == null || Math.abs(p[0] - last[0]) + Math.abs(p[1] - last[1]) > 1.0E-3F) {
                    piece.add(p);
                }
            }
            if (piece.size() > 1 && Math.abs(piece.get(0)[0] - piece.get(piece.size() - 1)[0]) + Math.abs(piece.get(0)[1] - piece.get(piece.size() - 1)[1]) <= 1.0E-3F) {
                piece.remove(piece.size() - 1);
            }
            if (piece.size() < 3) {
                continue;
            }
            float[][] p3 = new float[piece.size()][];
            float[] uv = new float[piece.size() * 2];
            for (int k = 0; k < piece.size(); k++) {
                float s = piece.get(k)[0], t = piece.get(k)[1];
                p3[k] = new float[] { base[0] + right[0] * s + up[0] * t, base[1] + right[1] * s + up[1] * t, base[2] + right[2] * s + up[2] * t };
                // Texel rows run down from the rectangle's top.
                uv[2 * k] = (rect[4] + s - rect[0]) / sheetW;
                uv[2 * k + 1] = (rect[5] + rect[3] - t) / sheetH;
            }
            // A fan of quads (a triangle repeats its last point); slivers with no area to draw are left out.
            for (int k = 1; k + 1 < piece.size(); k += 2) {
                int c = k + 2 < piece.size() ? k + 2 : k + 1;
                float fanArea = length(cross(sub(p3[k], p3[0]), sub(p3[k + 1], p3[0]))) + length(cross(sub(p3[k + 1], p3[0]), sub(p3[c], p3[0])));
                if (fanArea < 0.02F) {
                    continue;
                }
                quad(p3[0], p3[k], p3[k + 1], p3[c], new float[] { uv[0], uv[1], uv[2 * k], uv[2 * k + 1], uv[2 * k + 2], uv[2 * k + 3], uv[2 * c],
                        uv[2 * c + 1] }, glass);
            }
        }
    }

    // Where the ramp period px across starts on RAMPS (2 + 3 + ... + (period - 1)).
    static int rampX(int period) {
        return (period - 1) * period / 2 - 1;
    }

    // A round part's facet f of sides, fw px round and length px along (p0 p1 at its start, p3 p2 at its end), facing
    // out: with modules, the ramp across it, never along it. A facet 3 px or more round carries the whole ramp across
    // itself; narrower ones share one ramp that carries on round the part (in as many repeats as keep it at most 14 px).
    // Otherwise banded as usual.
    void roundFacet(float[] p0, float[] p1, float[] p2, float[] p3, float[] out, Paint side, int f, int sides, float fw, float length) {
        if (!modules || side.kind != Kind.BANDED) {
            orient(p0, p1, p2, p3, out, side);
            return;
        }
        int period;
        float s0;
        if (fw >= 3.0F) {
            period = Math.round(fw);
            s0 = 0.0F;
        } else {
            float round = fw * sides;
            period = Math.round(round / (float) Math.ceil(round / 14.0F));
            s0 = f * fw;
        }
        period = side == Paint.POLISHED ? 4 : Math.max(2, Math.min(14, period));
        // The polished rod has its own bright ramp; everything else the steel ramp its period wide.
        int rx = side == Paint.POLISHED ? Paint.POLISHED.x : rampX(period);
        boolean flipped = dot(cross(sub(p2, p0), sub(p3, p1)), out) < 0;
        if (fw >= 3.0F && length < fw) {
            // A band wider round than it is long (a flange, a ring): its width is its length, so the ramp runs across
            // that, the same all the way round. The quad turned a quarter, still wound outward.
            int across = Math.max(2, Math.min(14, Math.round(length)));
            if (flipped) {
                rampFacet(p0, p3, p2, p1, 0.0F, length, across, fw, rampX(across));
            } else {
                rampFacet(p1, p2, p3, p0, 0.0F, length, across, fw, rampX(across));
            }
        } else if (flipped) {
            rampFacet(p1, p0, p3, p2, -(s0 + fw), -s0, period, length, rx);
        } else {
            rampFacet(p0, p1, p2, p3, s0, s0 + fw, period, length, rx);
        }
    }

    // A strip (p0 p1 across it, p0 p3 along it, length px long) showing s0 to s1 of a ramp period px across that repeats
    // round the part, at 1 texel per px both ways (the ramp's rows repeat every 8 px along).
    void rampFacet(float[] p0, float[] p1, float[] p2, float[] p3, float s0, float s1, int period, float length, int rx) {
        for (float s = s0; s < s1 - 1.0E-5F;) {
            float k = (float) Math.floor(s / period + 1.0E-5F) * period, end = Math.min(s1, k + period);
            for (float t = 0; t < length - 1.0E-5F; t += 8.0F) {
                float t1 = Math.min(length, t + 8.0F);
                cell(p0, p1, p2, p3, (s - s0) / (s1 - s0), (end - s0) / (s1 - s0), t / length, t1 / length, Paint.RAMPS, rx + s - k, 0,
                        rx + end - k, t1 - t, false);
            }
            s = end;
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
        if (paint.kind == Kind.FLAT && !modules) {
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
    // on each end (lit / shade halves; none when they're null). skip leaves out facets (segment, facet). On a large
    // model a casing's shell is plated as the refinery tower's is (see uprightTube), a tapering ring is bare grain, and
    // other round parts carry the ramp across them.
    void prismU(float cx, float cy, float[][] profile, int sides, double start, Paint side, @Nullable Paint capLit,
            @Nullable Paint capShade, @Nullable FacetFilter skip) {
        double step = 2.0 * Math.PI / sides;
        for (int i = 0; i + 1 < profile.length; i++) {
            float ua = profile[i][0], ub = profile[i + 1][0], ra = profile[i][1], rb = profile[i + 1][1];
            if (ub - ua <= 0.001F) {
                continue;
            }
            float fw = 2.0F * (float) Math.sin(step / 2.0) * (ra + rb) / 2.0F, length = (float) Math.hypot(ub - ua, rb - ra);
            List<float[]> shellPlates = modules && side == Paint.CASING && ra == rb
                    ? plateFrame(new float[] { cx, cy, ua }, new float[] { 0, 1, 0 }, new float[] { 0, 0, 1 }, fw * sides, ub - ua,
                            new Plating(Steel.MACHINE, true, false)).rects()
                    : null;
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
                float[] out = { (float) Math.cos(mid), (float) Math.sin(mid), 0.0F };
                if (modules && side.kind == Kind.BANDED) {
                    if (ra != rb) {
                        List<float[]> points = List.of(p0, p1, p2, p3);
                        plateIn(plateFrame(points, new Plating(Steel.MACHINE, false, false, false)), points);
                    } else if (shellPlates != null) {
                        plateStrip(p0, p1, p2, p3, f * fw, (f + 1) * fw, length, shellPlates);
                    } else {
                        roundFacet(p0, p1, p2, p3, out, side, f, sides, fw, length);
                    }
                    continue;
                }
                flat(p0, p1, p2, p3, side.kind == Kind.BANDED ? band(side, out) : side, false);
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
    // outside are lit, the rest shade (on a large model, plated as one surface across its facets).
    void ringU(float cx, float cy, float u, float outer, float inner, int sides, double start, boolean facingPositive, Paint lit, Paint shade) {
        ringU(cx, cy, u, outer, inner, sides, start, facingPositive, lit, shade, null);
    }

    // The same, leaving out the facets skip names (its segment is always 0).
    void ringU(float cx, float cy, float u, float outer, float inner, int sides, double start, boolean facingPositive, Paint lit, Paint shade,
            @Nullable FacetFilter skip) {
        double step = 2.0 * Math.PI / sides;
        // Seen from +u, right is +x; from -u, right is -x. Up is +y either way.
        float rightSign = facingPositive ? 1.0F : -1.0F;
        List<float[]> rim = new ArrayList<>();
        for (int f = 0; f < sides; f++) {
            rim.add(new float[] { cx + (float) Math.cos(start + f * step) * outer, cy + (float) Math.sin(start + f * step) * outer, u });
        }
        PlateFrame frame = roundFace(rim, new float[] { 0, 0, rightSign }, lit, 2.0F * outer);
        if (frame != null && inner <= 0.0F && skip == null) {
            // A whole disc is one polygon: its plates cut it into far fewer pieces than its wedges would.
            plateIn(frame, rim);
            return;
        }
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
            if (frame != null) {
                plateIn(frame, List.of(o0, o1, i1, i0));
            } else if (facingPositive) {
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
        // A large model's caps are plated each as one surface across its wedges.
        List<float[]> rimTop = new ArrayList<>(), rimBottom = new ArrayList<>();
        for (int f = 0; f < PIPE_SIDES; f++) {
            double a = start + f * step;
            rimTop.add(new float[] { x + (float) Math.sin(a) * r, y1, u + (float) Math.cos(a) * r });
            rimBottom.add(new float[] { x + (float) Math.sin(a) * r, y0, u + (float) Math.cos(a) * r });
        }
        PlateFrame top = roundFace(rimTop, new float[] { 0, 1, 0 }, cap, 2.0F * r);
        PlateFrame bottom = roundFace(rimBottom, new float[] { 0, -1, 0 }, side, 2.0F * r);
        for (int f = 0; f < PIPE_SIDES; f++) {
            double a0 = start + f * step, a1 = a0 + step;
            // In the (u, x) plane, so (u, x, y) keeps the frame's handedness.
            float[] p0 = { x + (float) Math.sin(a0) * r, y0, u + (float) Math.cos(a0) * r };
            float[] p1 = { x + (float) Math.sin(a1) * r, y0, u + (float) Math.cos(a1) * r };
            float[] p2 = { p1[0], y1, p1[2] };
            float[] p3 = { p0[0], y1, p0[2] };
            roundFacet(p0, p1, p2, p3, new float[] { (float) Math.sin((a0 + a1) / 2.0), 0, (float) Math.cos((a0 + a1) / 2.0) }, side, f, PIPE_SIDES,
                    2.0F * r * (float) Math.sin(Math.PI / PIPE_SIDES), y1 - y0);
            float[] c0 = { x, y1, u }, b0 = { x, y0, u };
            if (top == null) {
                orient(c0, p3, p2, c0, new float[] { 0, 1, 0 }, cap);
            }
            if (bottom == null) {
                orient(b0, p0, p1, b0, new float[] { 0, -1, 0 }, side);
            }
        }
        if (top != null) {
            plateIn(top, rimTop);
        }
        if (bottom != null) {
            plateIn(bottom, rimBottom);
        }
    }

    // The frame a large model's flat round face (its rim's points in order round it, either way) is plated in as one
    // surface across its wedges, facing outward; null when paint isn't plated, or this isn't a large model.
    @Nullable PlateFrame roundFace(List<float[]> rim, float[] outward, Paint paint, float diameter) {
        Plating plating = modules ? plating(paint, diameter, diameter) : null;
        if (plating == null) {
            return null;
        }
        if (dot(cross(sub(rim.get(2), rim.get(0)), sub(rim.get(3), rim.get(1))), outward) < 0) {
            rim = new ArrayList<>(rim);
            java.util.Collections.reverse(rim);
        }
        return plateFrame(rim, plating);
    }

    // An octagonal pipe along x at (u, y) from x0 to x1, with flat caps.
    void prismX(float u, float y, float x0, float x1, float r, Paint side, Paint cap) {
        if (x1 - x0 <= 0.001F) {
            return;
        }
        double step = 2.0 * Math.PI / PIPE_SIDES, start = Math.PI / PIPE_SIDES;
        // A large model's cap is plated as one surface across its wedges, facing out of each end (roundFace winds it).
        List<float[]> far = new ArrayList<>(), near = new ArrayList<>();
        for (int f = 0; f < PIPE_SIDES; f++) {
            double a = start + f * step;
            far.add(new float[] { x1, y + (float) Math.cos(a) * r, u + (float) Math.sin(a) * r });
            near.add(new float[] { x0, y + (float) Math.cos(a) * r, u + (float) Math.sin(a) * r });
        }
        PlateFrame capFar = roundFace(far, new float[] { 1, 0, 0 }, cap, 2.0F * r), capNear = roundFace(near, new float[] { -1, 0, 0 }, cap, 2.0F * r);
        for (int f = 0; f < PIPE_SIDES; f++) {
            double a0 = start + f * step, a1 = a0 + step;
            float[] p0 = { x0, y + (float) Math.cos(a0) * r, u + (float) Math.sin(a0) * r };
            float[] p1 = { x0, y + (float) Math.cos(a1) * r, u + (float) Math.sin(a1) * r };
            float[] p2 = { x1, p1[1], p1[2] };
            float[] p3 = { x1, p0[1], p0[2] };
            roundFacet(p0, p1, p2, p3, new float[] { 0, (float) Math.cos((a0 + a1) / 2.0), (float) Math.sin((a0 + a1) / 2.0) }, side, f, PIPE_SIDES,
                    2.0F * r * (float) Math.sin(Math.PI / PIPE_SIDES), x1 - x0);
            if (capFar != null) {
                plateIn(capFar, List.of(new float[] { x1, y, u }, p3, p2));
                plateIn(capNear, List.of(new float[] { x0, y, u }, p0, p1));
            } else {
                orient(new float[] { x1, y, u }, p3, p2, new float[] { x1, y, u }, new float[] { 1, 0, 0 }, cap);
                orient(new float[] { x0, y, u }, p0, p1, new float[] { x0, y, u }, new float[] { -1, 0, 0 }, cap);
            }
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
            roundFacet(p0, p1, p2, p3, out, side, f, sides, 2.0F * r * (float) Math.sin(Math.PI / sides), length(sub(b, a)));
        }
    }

    // --- Building blocks for upright parts (round vessels, stacks) ---

    // A polygon tube standing upright about (x, u): its outline as (y, radius) points from the bottom, its facets
    // painted side (banded by facing for BANDED and CASING), with a flat cap on top when cap is set.
    void uprightTube(float x, float u, float[][] profile, int sides, double start, Paint side, @Nullable Paint cap) {
        double step = 2.0 * Math.PI / sides;
        for (int i = 0; i + 1 < profile.length; i++) {
            float ya = profile[i][0], yb = profile[i + 1][0], ra = profile[i][1], rb = profile[i + 1][1];
            if (yb - ya <= 0.001F) {
                continue;
            }
            // The shell's plates, laid out once over the section unrolled (round it by up it), so they carry on from
            // facet to facet: riveted plates as tall as the section, seams only where they meet.
            float facetWidth = 2.0F * (float) Math.sin(step / 2.0) * ra;
            List<float[]> shellPlates = modules && side == Paint.CASING && ra == rb
                    ? plateFrame(new float[] { x, ya, u }, new float[] { 1, 0, 0 }, new float[] { 0, 1, 0 }, facetWidth * sides, yb - ya,
                            new Plating(Steel.MACHINE, true, false)).rects()
                    : null;
            for (int f = 0; f < sides; f++) {
                double a0 = start + f * step, a1 = a0 + step, mid = (a0 + a1) / 2.0;
                float[] n = { (float) Math.cos(mid), (rb < ra ? (ra - rb) / (yb - ya) : 0.0F), (float) Math.sin(mid) };
                // Seen from outside: bottom-left, bottom-right, top-right, top-left (angle grows to the left seen from
                // outside).
                float[] p0 = { x + (float) Math.cos(a1) * ra, ya, u + (float) Math.sin(a1) * ra };
                float[] p1 = { x + (float) Math.cos(a0) * ra, ya, u + (float) Math.sin(a0) * ra };
                float[] p2 = { x + (float) Math.cos(a0) * rb, yb, u + (float) Math.sin(a0) * rb };
                float[] p3 = { x + (float) Math.cos(a1) * rb, yb, u + (float) Math.sin(a1) * rb };
                boolean swapped = dot(cross(sub(p2, p0), sub(p3, p1)), n) < 0;
                if (swapped) {
                    float[] t = p0;
                    p0 = p1;
                    p1 = t;
                    t = p2;
                    p2 = p3;
                    p3 = t;
                }
                if (modules && side.kind == Kind.BANDED) {
                    float chord = 2.0F * (float) Math.sin(step / 2.0), fw = chord * (ra + rb) / 2.0F;
                    float height = (float) Math.hypot(yb - ya, rb - ra);
                    if (ra != rb) {
                        // A tapering ring (the elliptical head) narrows to a point, so no strip ramp keeps 1:1 across
                        // it; it's one pressed piece, so its facets are bare grain (no plate edges on every facet).
                        List<float[]> points = List.of(p0, p1, p2, p3);
                        plateIn(plateFrame(points, new Plating(Steel.MACHINE, false, false, false)), points);
                    } else if (shellPlates != null) {
                        float s0 = (swapped ? f : sides - 1 - f) * fw;
                        plateStrip(p0, p1, p2, p3, s0, s0 + fw, height, shellPlates);
                    } else {
                        roundFacet(p0, p1, p2, p3, n, side, swapped ? f : sides - 1 - f, sides, fw, height);
                    }
                    continue;
                }
                flat(p0, p1, p2, p3, side.kind == Kind.BANDED ? band(side, n) : side, false);
            }
        }
        if (cap != null) {
            float[] last = profile[profile.length - 1];
            disc(new float[] { x, last[0], u }, new float[] { 0, 1, 0 }, last[1], sides, cap, cap);
        }
    }

    // The inside of an upright ring (a fan shroud), its facets facing in, in one paint.
    void uprightInner(float x, float u, float y0, float y1, float r, int sides, Paint paint) {
        double step = 2.0 * Math.PI / sides;
        for (int f = 0; f < sides; f++) {
            double a0 = f * step, a1 = a0 + step, mid = (a0 + a1) / 2.0;
            float[] p0 = { x + (float) Math.cos(a0) * r, y0, u + (float) Math.sin(a0) * r };
            float[] p1 = { x + (float) Math.cos(a1) * r, y0, u + (float) Math.sin(a1) * r };
            float[] p2 = { p1[0], y1, p1[2] };
            float[] p3 = { p0[0], y1, p0[2] };
            orient(p0, p1, p2, p3, new float[] { -(float) Math.cos(mid), 0, -(float) Math.sin(mid) }, paint);
        }
    }

    // A flat disc facing normal (unit, any way), its facets on the top-left half (seen from outside) in lit, the rest
    // in shade.
    void disc(float[] centre, float[] normal, float radius, int sides, Paint lit, Paint shade) {
        float[][] axes = plane(normal);
        float[] right = axes[0], up = axes[1];
        double step = 2.0 * Math.PI / sides;
        // A large model's disc is plated as one surface (no painted light on it).
        List<float[]> rim = new ArrayList<>();
        for (int f = 0; f < sides; f++) {
            rim.add(at(centre, right, up, (float) Math.cos(f * step) * radius, (float) Math.sin(f * step) * radius));
        }
        PlateFrame frame = roundFace(rim, normal, lit, 2.0F * radius);
        if (frame != null) {
            plateIn(frame, rim);
            return;
        }
        for (int f = 0; f < sides; f++) {
            double a0 = f * step, a1 = a0 + step, mid = (a0 + a1) / 2.0;
            float[] e0 = at(centre, right, up, (float) Math.cos(a0) * radius, (float) Math.sin(a0) * radius);
            float[] e1 = at(centre, right, up, (float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius);
            boolean topLeft = Math.sin(mid) - Math.cos(mid) > 0;
            orient(centre, e0, e1, centre, normal, topLeft ? lit : shade);
        }
    }

    // Right and up across a face facing normal, seen from outside (up is world up unless the face looks up or down).
    static float[][] plane(float[] normal) {
        float[] up = Math.abs(normal[1]) > 0.9F ? new float[] { 0, 0, -Math.signum(normal[1]) } : new float[] { 0, 1, 0 };
        float[] right = normalize(cross(up, normal));
        up = cross(normal, right);
        return new float[][] { right, up };
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
