/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Fusion Reactor's model (see FusionReactorRenderer), on its own sheet (textures/block/fusion/tokamak_model.png: the
// refinery tower's large-model sheet with the tokamak's tiles added, tools/tokamak_sheet_gen.py). A large model
// (docs/TEXTURE_STYLE.md, "Large model texturing"). Formed, every block of the reactor hides, so this is the whole
// machine as it's seen: the cryostat, a plated faceted cylinder over the ring's footprint, standing on a skirt and a
// base flange, closed by a bolted top flange and a shallow pressed head with the central solenoid's access hub on its
// crown. Raised plates down it mark the toroidal field coils inside (sixteen or eighteen, evenly round it; on a
// stellarator they wind round it in steps, its modular coils' helical sweep), and two girth bands mark the poloidal field
// coils' layers. Each Vessel Port is a riveted port flange with its collar, the control desk stands on the front wall at
// the controller, and the ports set to a side and the modules on the Vessel Ports get nozzles to their block faces.
//
// The Reactor Glass in the wall opens viewports, as the Fission Reactor's windows do: each glass block opens the facets
// facing it at its row, glass that touches makes one viewport with one raised frame round it, and the glass is the glass
// tile's rim and glints over a light grey tint pane. Behind each viewport a port duct runs in toward the axis (plated,
// facing in): at the plasma chamber's rows it opens into the chamber, elsewhere a blanket module's plate closes it. Inside
// (built only when a viewport looks into it), the chamber is lined with the First Wall's tungsten armour (its outer wall
// facing in, the centre column's wall facing out, its roof) over the Divertor's targets on its floor; and while the
// reactor burns, the plasma: a D-shaped torus (a stellarator's twisting five times round), a bright core in a fainter
// halo, both full-bright and tinted by the renderer for its temperature.
//
// Its lists: {solid, cutout (the glass), glow (the desk's screen), plasma core, plasma halo, tint}.
public final class TokamakMesh extends VesselMesh {
    // Sheet areas of the tokamak's own tiles (see tools/tokamak_sheet_gen.py), 16 x 16 each.
    static final int FW_X = 32, DIVERTOR_X = 48, PLASMA_X = 64, TILE_Y = 240, TILE = 16;
    // The cryostat's wall, the viewports' frames (width round an opening, how far they stand out), the tint pane's
    // depth behind the glass rim, the girth bands and coil ribs (how far out, how tall or wide).
    static final float WALL = 2.0F, FRAME = 2.0F, FRAME_OUT = 1.5F, TINT_IN = 0.1F;
    // Where a viewport spans a chamber row and a blanket row, the blanket's duct floor (or ceiling) stands this far off
    // the chamber's roof (or floor), a ledge with a lip at the shell's inner face.
    static final float LEDGE = 2.0F;
    static final float BAND_OUT = 2.0F, BAND_H = 3.0F, RIB_OUT = 1.5F, RIB_W = 6.0F, HELIX_W = 9.0F;
    // The skirt's top, the base flange's top (the shell starts there), the head's rise.
    static final float SKIRT = 9.0F, BASE = 12.0F, FLANGE_H = 3.0F;
    // The plasma's shape: its minor radius of the chamber's half-width, elongation, triangularity, the core's share of
    // it, and its facets round and along.
    static final float PLASMA_FILL = 0.78F, KAPPA = 1.7F, DELTA = 0.33F, CORE = 0.55F;
    static final int POLOIDAL = 16;
    // The rounded liner round the plasma (see liner): its facets round the cross-section, and its superellipse's power
    // (2 a circle's ellipse, higher a squarer box with round corners).
    static final int LINER_ROUND = 24;
    static final double LINER_POWER = 2.6;
    private static final int SOLID = 0, FRAMED = 1, OPEN = 2;

    // A Reactor Glass block in the wall: its block {x, y, u} in the frame.
    public record Viewport(int x, int y, int u) {}

    // A Vessel Port in the wall: its block {x, y, u} in the frame.
    public record PortBlock(int x, int y, int u) {}

    final int radius, hp, top, outerBlocks, blocksHigh;
    final boolean stellarator;
    final int sides;
    final double step, start;
    final float rs, facetW, shellTop, chamberIn, chamberOut, chamberLow, chamberHigh;
    final List<PortBlock> portBlocks;
    final int[] controller;
    private final boolean[][] open;
    private final boolean windowed, intoChamber;
    // Places on the wall the coil ribs and girth bands keep clear of {angle, y, half width, half height} (px): the port
    // flanges and the control desk.
    private final List<float[]> features = new ArrayList<>();
    private final List<float[][]> tintPanes = new ArrayList<>();
    // The walls of the ducts into the chamber {p0, p1, p2, p3, facing}: the plasma's light falls on them (see spill).
    private final List<float[][]> spillFaces = new ArrayList<>();

    // frame: over the reactor's bounding box, u back from the front (the controller's side); radius its major radius;
    // controller its block {x, y, u} in the frame.
    public TokamakMesh(ModelFrame frame, int radius, boolean stellarator, List<Viewport> viewports, List<PortBlock> portBlocks, int[] controller) {
        super(frame);
        this.radius = radius;
        this.stellarator = stellarator;
        // TokamakLayout's numbers (kept here so the mesh needs no Minecraft-free import beyond the frame).
        this.hp = (radius - 1) / 2;
        this.top = hp + 3;
        this.outerBlocks = radius + 1;
        this.blocksHigh = frame.height();
        this.portBlocks = portBlocks;
        this.controller = controller;
        // About a block of wall a facet, four-fold so the front and sides fall on facets' middles.
        this.sides = Math.max(32, 4 * Math.round((float) (2.0 * Math.PI * (outerBlocks + 0.5) / 4.0)));
        this.step = 2.0 * Math.PI / sides;
        this.start = Math.PI / sides;
        // Within the footprint with the flanges' and the desk's reach to spare.
        this.rs = (outerBlocks + 0.5F) * PX - 4.5F;
        this.facetW = 2.0F * (float) Math.sin(step / 2.0) * rs;
        this.shellTop = (blocksHigh - 1) * PX + 2.0F;
        // The plasma chamber: 2 <= d <= r - 1 across, |y| <= hp up from the midplane.
        this.chamberIn = 1.5F * PX;
        this.chamberOut = (radius - 0.5F) * PX;
        this.chamberLow = (top - hp) * PX;
        this.chamberHigh = (top + hp + 1) * PX;
        this.open = new boolean[sides][blocksHigh];
        boolean any = false, chamber = false;
        for (Viewport v : viewports) {
            if (v.y() < 1 || v.y() > blocksHigh - 2) {
                continue;
            }
            for (int f : facets(v.x(), v.u())) {
                open[f][v.y()] = true;
                any = true;
                chamber |= inChamber(v.y());
            }
        }
        this.windowed = any;
        this.intoChamber = chamber;
        for (PortBlock p : portBlocks) {
            features.add(new float[] { blockAngle(p.x(), p.u()), (p.y() + 0.5F) * PX, 9.0F, 9.0F });
        }
        features.add(new float[] { frontAngle(), (controller[1] + 0.5F) * PX, 8.0F, 10.0F });
    }

    // --- Where things are ---

    // The angle round the axis of a block's middle (from +x toward +u).
    float blockAngle(int bx, int bu) {
        return (float) Math.atan2((bu + 0.5F) * PX - cu, (bx + 0.5F) * PX - cx);
    }

    // The front (u = 0, the controller's side).
    static float frontAngle() {
        return (float) (-Math.PI / 2.0);
    }

    // The facets a wall block faces: those whose middles lie within its corners' sweep round the axis, or the one its
    // middle is on.
    private List<Integer> facets(int bx, int bu) {
        double centre = Math.atan2((bu + 0.5) * PX - cu, (bx + 0.5) * PX - cx);
        double lo = 0.0, hi = 0.0;
        for (int i = 0; i < 4; i++) {
            double x = (bx + (i & 1)) * PX - cx, u = (bu + (i >> 1)) * PX - cu;
            double d = Math.atan2(Math.sin(Math.atan2(u, x) - centre), Math.cos(Math.atan2(u, x) - centre));
            lo = Math.min(lo, d);
            hi = Math.max(hi, d);
        }
        List<Integer> out = new ArrayList<>();
        for (int k = (int) Math.floor((centre + lo - start) / step) - 1; k <= (int) Math.ceil((centre + hi - start) / step) + 1; k++) {
            double mid = start + (k + 0.5) * step;
            double d = Math.atan2(Math.sin(mid - centre), Math.cos(mid - centre));
            if (d >= lo && d <= hi) {
                out.add(Math.floorMod(k, sides));
            }
        }
        if (out.isEmpty()) {
            out.add(Math.floorMod((int) Math.floor((centre - start) / step), sides));
        }
        return out;
    }

    boolean inChamber(int row) {
        return row * PX >= chamberLow - 0.01F && (row + 1) * PX <= chamberHigh + 0.01F;
    }

    // Facet f's middle angle.
    double facetMid(int f) {
        return start + (f + 0.5) * step;
    }

    // A point on facet f of the polygon radius r round the axis: s across it from its left edge (seen from outside),
    // at height t.
    float[] at(int f, float s, float t, float r) {
        double a0 = start + f * step, a1 = a0 + step;
        float k = s / facetW;
        float xl = cx + (float) Math.cos(a1) * r, ul = cu + (float) Math.sin(a1) * r;
        float xr = cx + (float) Math.cos(a0) * r, ur = cu + (float) Math.sin(a0) * r;
        return new float[] { xl + (xr - xl) * k, t, ul + (ur - ul) * k };
    }

    // A point at angle a, radius r, height t.
    float[] polar(double a, float r, float t) {
        return new float[] { cx + (float) Math.cos(a) * r, t, cu + (float) Math.sin(a) * r };
    }

    // Facet f's direction across it, left to right seen from outside.
    float[] across(int f) {
        return normalize(sub(at(f, facetW, 0.0F, rs), at(f, 0.0F, 0.0F, rs)));
    }

    private boolean opening(int f, float t) {
        int row = (int) Math.floor(t / PX);
        return row >= 0 && row < blocksHigh && open[f][row];
    }

    // What the shell is at s across facet f and t up: open, the frame round an opening, or solid.
    private int kind(int f, float s, float t) {
        if (opening(f, t)) {
            return OPEN;
        }
        for (int df = -1; df <= 1; df++) {
            float sideways = df == 0 ? 0.0F : df == 1 ? s : facetW - s;
            if (sideways >= FRAME) {
                continue;
            }
            boolean[] column = open[Math.floorMod(f + df, sides)];
            for (int j = 0; j < blocksHigh; j++) {
                float b0 = j * PX, b1 = b0 + PX;
                if (column[j] && (t < b0 ? b0 - t : t > b1 ? t - b1 : 0.0F) < FRAME) {
                    return FRAMED;
                }
            }
        }
        return SOLID;
    }

    // Whether the wall at facet f from t0 to t1 is plain shell, clear of the viewports, their frames, the port flanges
    // and the desk (the ribs and bands keep off them).
    private boolean clear(int f, float t0, float t1) {
        for (float t = t0; t <= t1 + 1.0E-4F; t += Math.max(0.5F, Math.min(2.0F, t1 - t0))) {
            for (float s : new float[] { 0.5F, facetW / 2.0F, facetW - 0.5F }) {
                if (kind(f, s, t) != SOLID) {
                    return false;
                }
            }
        }
        double mid = facetMid(f);
        for (float[] feature : features) {
            double d = Math.abs(Math.atan2(Math.sin(mid - feature[0]), Math.cos(mid - feature[0]))) * rs;
            if (d < feature[2] + facetW / 2.0F + 1.0F && t1 > feature[1] - feature[3] - 1.0F && t0 < feature[1] + feature[3] + 1.0F) {
                return false;
            }
        }
        return true;
    }

    // --- Building ---

    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        tintPanes.clear();
        spillFaces.clear();
        base();
        windowedShell();
        uprights.add(new float[] { cx, cu, rs, BASE, shellTop });
        lid();
        bands();
        ribs();
        portFlanges();
        desk();
        if (windowed) {
            ducts();
            if (intoChamber) {
                chamber();
            }
        }
        float[][] parts = finish(ports);
        glowing = true;
        float[] core = new float[0], halo = new float[0];
        if (intoChamber) {
            glow = new Floats();
            plasma(CORE);
            core = glow.toArray();
            glow = new Floats();
            plasma(1.0F);
            spill();
            halo = glow.toArray();
        }
        glow = new Floats();
        float tu = (Paint.RIVET.x + 2.0F) / SHEET_W, tv = (Paint.RIVET.y + 2.0F) / SHEET_H;
        for (float[][] p : tintPanes) {
            quad(p[0], p[1], p[2], p[3], new float[] { tu, tv, tu, tv, tu, tv, tu, tv }, false);
        }
        float[] tint = glow.toArray();
        glowing = false;
        return new float[][] { parts[0], parts[1], parts[2], core, halo, tint };
    }

    // The skirt the cryostat stands on and the base flange the shell rises from.
    private void base() {
        uprightTube(cx, cu, new float[][] { { 0.0F, rs - 6.0F }, { SKIRT, rs - 6.0F } }, sides, start, Paint.CASING, null);
        uprights.add(new float[] { cx, cu, rs - 6.0F, 0.0F, SKIRT });
        flange(SKIRT, BASE, rs + 2.0F, rs - 6.0F, sides);
        uprights.add(new float[] { cx, cu, rs + 2.0F, SKIRT, BASE });
    }

    // The top flange, the pressed head and the solenoid's access hub on its crown.
    private void lid() {
        flange(shellTop, shellTop + FLANGE_H, rs + 2.0F, rs - 4.0F, sides);
        float headY = shellTop + FLANGE_H, crownMost = blocksHigh * PX - 1.0F;
        float hubH = 3.0F, rise = Math.max(3.0F, crownMost - headY - hubH);
        uprightTube(cx, cu, head(headY, rs, rise, true), sides, start, Paint.CASING, Paint.PLAIN);
        uprights.add(new float[] { cx, cu, rs + 2.0F, shellTop, shellTop + FLANGE_H });
        uprights.add(new float[] { cx, cu, rs * 0.75F, headY, headY + rise });
        float hubR = Math.min(1.5F * PX, rs * 0.12F), hubY = headAt(headY, rs, rise, hubR) - 1.0F;
        uprightTube(cx, cu, new float[][] { { hubY, hubR }, { headY + rise + hubH, hubR } }, 12, Math.PI / 12, Paint.BANDED, Paint.PLAIN);
        uprightTube(cx, cu, new float[][] { { headY + rise + hubH - 1.0F, hubR + 1.0F }, { headY + rise + hubH, hubR + 1.0F } }, 12, Math.PI / 12,
                Paint.BANDED, null);
    }

    // The shell from the base flange to the top flange, its viewports cut in it (see FissionReactorMesh's windowed shell,
    // whose scheme this follows): each facet is cut into three columns (a frame's width at either edge) and into rows
    // at the viewports' and frames' edges; each piece is shell (plated as the whole shell's unrolled layout lays it out),
    // frame (raised, plain) or open, merged within each facet, with walls where kinds meet and the glass across each
    // opening.
    private void windowedShell() {
        float r = rs, rf = r + FRAME_OUT, y0 = BASE, y1 = shellTop, fw = facetW;
        List<float[]> plates = plateFrame(new float[] { cx, y0, cu }, new float[] { 1, 0, 0 }, new float[] { 0, 1, 0 }, fw * sides, y1 - y0,
                new Plating(Steel.MACHINE, true, false)).rects();
        float[] ss = { 0.0F, FRAME, fw - FRAME, fw };
        float[] ts = cuts(y0, y1);
        int nk = ts.length - 1;
        int[][][] kinds = new int[sides][3][nk];
        for (int f = 0; f < sides; f++) {
            for (int c = 0; c < 3; c++) {
                for (int k = 0; k < nk; k++) {
                    kinds[f][c][k] = kind(f, (ss[c] + ss[c + 1]) / 2.0F, (ts[k] + ts[k + 1]) / 2.0F);
                }
            }
        }
        for (int f = 0; f < sides; f++) {
            int[][] kd = kinds[f];
            float s0 = (sides - 1 - f) * fw;
            float[] b0 = at(f, 0, y0, r), b1 = at(f, fw, y0, r), b2 = at(f, fw, y1, r), b3 = at(f, 0, y1, r);
            boolean[][] solid = new boolean[3][nk], framed = new boolean[3][nk];
            for (int c = 0; c < 3; c++) {
                for (int k = 0; k < nk; k++) {
                    solid[c][k] = kd[c][k] == SOLID;
                    framed[c][k] = kd[c][k] == FRAMED;
                }
            }
            for (int[] m : merged(solid)) {
                plateStrip(b0, b1, b2, b3, s0, s0 + fw, y1 - y0, plates, s0 + ss[m[0]], s0 + ss[m[1]], ts[m[2]] - y0, ts[m[3]] - y0);
            }
            for (int[] m : merged(framed)) {
                float sa = ss[m[0]], sb = ss[m[1]], ta = ts[m[2]], tb = ts[m[3]];
                flat(at(f, sa, ta, rf), at(f, sb, ta, rf), at(f, sb, tb, rf), at(f, sa, tb, rf), Paint.PLAIN, false);
            }
            for (int k = 0; k + 1 < nk; k++) {
                for (int c = 0; c < 3;) {
                    int code = wall(kd[c][k], kd[c][k + 1]);
                    int c1 = c + 1;
                    while (c1 < 3 && wall(kd[c1][k], kd[c1][k + 1]) == code) {
                        c1++;
                    }
                    if (code != 0) {
                        horizontalWall(f, ss[c], ss[c1], ts[k + 1], code);
                    }
                    c = c1;
                }
            }
            int[][] right = kinds[Math.floorMod(f - 1, sides)];
            for (int c = 0; c < 3; c++) {
                int[] left = kd[c], next = c < 2 ? kd[c + 1] : right[0];
                float[] dir = c < 2 ? across(f) : normalize(add(across(f), across(Math.floorMod(f - 1, sides))));
                for (int k = 0; k < nk;) {
                    int code = wall(left[k], next[k]);
                    int k1 = k + 1;
                    while (k1 < nk && wall(left[k1], next[k1]) == code) {
                        k1++;
                    }
                    if (code != 0) {
                        verticalWall(f, ss[c + 1], ts[k], ts[k1], dir, code);
                    }
                    k = k1;
                }
            }
        }
        if (windowed) {
            glass();
        }
    }

    // The rows the shell is cut at: its ends and each block row's edges and the frames' round them.
    private float[] cuts(float y0, float y1) {
        List<Float> at = new ArrayList<>(List.of(y0, y1));
        for (int j = 0; j <= blocksHigh; j++) {
            float b = j * PX;
            at.add(b);
            at.add(b - FRAME);
            at.add(b + FRAME);
        }
        at.sort(null);
        List<Float> kept = new ArrayList<>();
        for (float t : at) {
            float clamped = Math.max(y0, Math.min(y1, t));
            if (kept.isEmpty() || clamped - kept.get(kept.size() - 1) > 1.0E-3F) {
                kept.add(clamped);
            }
        }
        float[] out = new float[kept.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = kept.get(i);
        }
        return out;
    }

    // The wall between pieces of kinds a and b, coded with the side it faces (0 for none).
    private static int wall(int a, int b) {
        if (a == b) {
            return 0;
        }
        int low = Math.min(a, b), high = Math.max(a, b);
        boolean towardA = high == OPEN ? a == OPEN : a == SOLID;
        int code = 1 + 3 * high + low;
        return towardA ? -code : code;
    }

    private float[] wallRadii(int code) {
        int c = Math.abs(code) - 1, high = c / 3, low = c % 3;
        float inner = high == OPEN ? rs - WALL : rs, outer = high == OPEN && low == SOLID ? rs : rs + FRAME_OUT;
        return new float[] { inner, outer };
    }

    private void horizontalWall(int f, float sa, float sb, float t, int code) {
        float[] radii = wallRadii(code);
        float[] p0 = at(f, sa, t, radii[0]), p1 = at(f, sb, t, radii[0]), p2 = at(f, sb, t, radii[1]), p3 = at(f, sa, t, radii[1]);
        orient(p0, p1, p2, p3, new float[] { 0, code < 0 ? -1 : 1, 0 }, Paint.BANDED);
    }

    private void verticalWall(int f, float s, float ta, float tb, float[] dir, int code) {
        float[] radii = wallRadii(code);
        float[] p0 = at(f, s, ta, radii[0]), p1 = at(f, s, ta, radii[1]), p2 = at(f, s, tb, radii[1]), p3 = at(f, s, tb, radii[0]);
        float sign = code < 0 ? -1.0F : 1.0F;
        orient(p0, p1, p2, p3, new float[] { dir[0] * sign, dir[1] * sign, dir[2] * sign }, Paint.BANDED);
    }

    // The viewports' glass, halfway through the wall: the glass tile's rim round each viewport's outside (runs of up to
    // 10 px), the glints once in its top-left corner, and the tint panes behind (see buildStatic).
    private void glass() {
        int gx = Paint.GLASS.x, gy = Paint.GLASS.y;
        float fw = facetW, h = PX, rg = rs - WALL / 2.0F;
        boolean[][] glinted = glints();
        for (int f = 0; f < sides; f++) {
            for (int j = 0; j < blocksHigh; j++) {
                if (!open[f][j]) {
                    continue;
                }
                boolean topEdge = j + 1 >= blocksHigh || !open[f][j + 1], bottom = j == 0 || !open[f][j - 1];
                boolean left = !open[Math.floorMod(f + 1, sides)][j], right = !open[Math.floorMod(f - 1, sides)][j];
                List<float[]> rects = new ArrayList<>();
                for (float a = 0; a < fw - 1.0E-4F; a += 10.0F) {
                    if (topEdge) {
                        rects.add(new float[] { a, Math.min(fw, a + 10.0F), h - 1.0F, h, gx, gy });
                    }
                    if (bottom) {
                        rects.add(new float[] { a, Math.min(fw, a + 10.0F), 0.0F, 1.0F, gx + 1, gy + 11 });
                    }
                }
                float t0 = bottom ? 1.0F : 0.0F, t1 = topEdge ? h - 1.0F : h;
                for (float t = t1; t > t0 + 1.0E-4F; t -= 10.0F) {
                    if (left) {
                        rects.add(new float[] { 0.0F, 1.0F, Math.max(t0, t - 10.0F), t, gx, gy + 1 });
                    }
                    if (right) {
                        rects.add(new float[] { fw - 1.0F, fw, Math.max(t0, t - 10.0F), t, gx + 11, gy + 1 });
                    }
                }
                if (glinted[f][j] && fw >= 8.0F) {
                    rects.add(new float[] { 2.0F, 7.0F, h - 7.0F, h - 2.0F, gx + 2, gy + 2 });
                }
                List<float[]> st = List.of(new float[] { 0, 0 }, new float[] { fw, 0 }, new float[] { fw, h }, new float[] { 0, h });
                paintPlane(st, at(f, 0.0F, j * h, rg), across(f), new float[] { 0, 1, 0 }, rects, true);
            }
            float rt = rg - TINT_IN;
            for (int j = 0; j < blocksHigh;) {
                if (!open[f][j]) {
                    j++;
                    continue;
                }
                int j1 = j + 1;
                while (j1 < blocksHigh && open[f][j1]) {
                    j1++;
                }
                float t0 = j * h, t1 = j1 * h;
                tintPanes.add(new float[][] { at(f, 0.0F, t0, rt), at(f, fw, t0, rt), at(f, fw, t1, rt), at(f, 0.0F, t1, rt) });
                j = j1;
            }
        }
    }

    // Each viewport's top-left piece seen from outside.
    private boolean[][] glints() {
        boolean[][] seen = new boolean[sides][blocksHigh], glint = new boolean[sides][blocksHigh];
        for (int f0 = 0; f0 < sides; f0++) {
            for (int j0 = 0; j0 < blocksHigh; j0++) {
                if (!open[f0][j0] || seen[f0][j0]) {
                    continue;
                }
                List<int[]> stack = new ArrayList<>(List.of(new int[] { f0, j0 }));
                seen[f0][j0] = true;
                int[] best = null;
                while (!stack.isEmpty()) {
                    int[] p = stack.remove(stack.size() - 1);
                    boolean leftmost = !open[Math.floorMod(p[0] + 1, sides)][p[1]];
                    if (best == null || p[1] > best[1] || p[1] == best[1] && leftmost) {
                        best = p;
                    }
                    int[][] next = { { Math.floorMod(p[0] + 1, sides), p[1] }, { Math.floorMod(p[0] - 1, sides), p[1] }, { p[0], p[1] + 1 }, { p[0], p[1] - 1 } };
                    for (int[] q : next) {
                        if (q[1] >= 0 && q[1] < blocksHigh && open[q[0]][q[1]] && !seen[q[0]][q[1]]) {
                            seen[q[0]][q[1]] = true;
                            stack.add(q);
                        }
                    }
                }
                glint[best[0]][best[1]] = true;
            }
        }
        return glint;
    }

    // Rectangles {c0, c1, k0, k1} covering a grid's set cells, merged across, then up.
    private static List<int[]> merged(boolean[][] cells) {
        int nc = cells.length, nk = cells[0].length;
        boolean[][] used = new boolean[nc][nk];
        List<int[]> out = new ArrayList<>();
        for (int k = 0; k < nk; k++) {
            for (int c = 0; c < nc; c++) {
                if (!cells[c][k] || used[c][k]) {
                    continue;
                }
                int c1 = c + 1;
                while (c1 < nc && cells[c1][k] && !used[c1][k]) {
                    c1++;
                }
                int k1 = k + 1;
                while (k1 < nk) {
                    boolean all = true;
                    for (int i = c; i < c1 && all; i++) {
                        all = cells[i][k1] && !used[i][k1];
                    }
                    if (!all) {
                        break;
                    }
                    k1++;
                }
                for (int i = c; i < c1; i++) {
                    for (int j = k; j < k1; j++) {
                        used[i][j] = true;
                    }
                }
                out.add(new int[] { c, c1, k, k1 });
            }
        }
        return out;
    }

    // --- On the wall ---

    // The girth bands over the poloidal field coils' layers (the bottom and top block rows: at the first row's top and the
    // top row's foot), each
    // facet's piece where the wall is clear, ended with a face where it stops.
    private void bands() {
        for (float y : new float[] { 1.0F * PX, (blocksHigh - 1) * PX }) {
            float y0 = y - BAND_H / 2.0F, y1 = y + BAND_H / 2.0F;
            if (y0 < BASE + 1.0F || y1 > shellTop - 1.0F) {
                continue;
            }
            boolean[] on = new boolean[sides];
            for (int f = 0; f < sides; f++) {
                on[f] = clear(f, y0, y1);
            }
            float ro = rs + BAND_OUT, fw = facetW * ro / rs;
            for (int f = 0; f < sides; f++) {
                if (!on[f]) {
                    continue;
                }
                float[] p0 = at(f, 0, y0, ro), p1 = at(f, facetW, y0, ro), p2 = at(f, facetW, y1, ro), p3 = at(f, 0, y1, ro);
                double mid = facetMid(f);
                roundFacet(p0, p1, p2, p3, new float[] { (float) Math.cos(mid), 0, (float) Math.sin(mid) }, Paint.BANDED, f, sides, fw, y1 - y0);
                orient(at(f, 0, y1, rs), at(f, facetW, y1, rs), at(f, facetW, y1, ro), at(f, 0, y1, ro), new float[] { 0, 1, 0 }, Paint.PLAIN);
                orient(at(f, 0, y0, rs), at(f, facetW, y0, rs), at(f, facetW, y0, ro), at(f, 0, y0, ro), new float[] { 0, -1, 0 }, Paint.PLAIN);
                // Its ends: where the next facet to the left (seen from outside) or right has none.
                if (!on[Math.floorMod(f + 1, sides)]) {
                    orient(at(f, 0, y0, rs), at(f, 0, y0, ro), at(f, 0, y1, ro), at(f, 0, y1, rs), neg(across(f)), Paint.BANDED);
                }
                if (!on[Math.floorMod(f - 1, sides)]) {
                    orient(at(f, facetW, y0, rs), at(f, facetW, y0, ro), at(f, facetW, y1, ro), at(f, facetW, y1, rs), across(f), Paint.BANDED);
                }
            }
        }
    }

    // The coil ribs: raised plates down the wall over the toroidal field coils, each on a facet's middle, cut where the
    // wall isn't clear. A stellarator's are helical bands instead: ten, each rising from the base flange to the top one
    // while it turns a fifth of the way round (its coils' five periods).
    private void ribs() {
        int coils = stellarator ? 10 : radius <= 4 ? 16 : 18;
        double first = frontAngle() + Math.PI / coils;
        for (int k = 0; k < coils; k++) {
            int f0 = Math.floorMod((int) Math.floor((first + k * 2.0 * Math.PI / coils - start) / step), sides);
            if (stellarator) {
                helix(f0);
            } else {
                rib(f0, BASE, shellTop);
            }
        }
    }

    // A helical band from facet f0 at the base flange, turning toward smaller angles (to the right seen from outside) a
    // fifth of the way round as it rises: a sloped plate on each facet it crosses where the wall is clear, its upper and
    // lower edges stepping off the wall, ended where it stops.
    private void helix(int f0) {
        int span = Math.max(4, sides / 5);
        float y0 = BASE + 2.0F, y1 = shellTop - 2.0F, rise = (y1 - y0) / span, half = HELIX_W / 2.0F;
        float ro = rs + RIB_OUT;
        boolean last = false;
        for (int i = 0; i < span; i++) {
            int f = Math.floorMod(f0 - i, sides);
            // Seen from outside, the facet's left edge (s = 0) is where the band comes from, lower.
            float yl = y0 + i * rise, yr = yl + rise;
            float lo = Math.max(y0, Math.min(yl, yr) - half), hi = Math.min(y1, Math.max(yl, yr) + half);
            boolean on = clear(f, lo, hi);
            if (on) {
                float[] p0 = at(f, 0, yl - half, ro), p1 = at(f, facetW, yr - half, ro), p2 = at(f, facetW, yr + half, ro), p3 = at(f, 0, yl + half, ro);
                double mid = facetMid(f);
                orient(p0, p1, p2, p3, new float[] { (float) Math.cos(mid), 0, (float) Math.sin(mid) }, Paint.PLAIN);
                orient(at(f, 0, yl + half, rs), at(f, facetW, yr + half, rs), p2, p3, new float[] { 0, 1, 0 }, Paint.BANDED);
                orient(at(f, 0, yl - half, rs), at(f, facetW, yr - half, rs), p1, p0, new float[] { 0, -1, 0 }, Paint.BANDED);
                if (!last) {
                    orient(at(f, 0.0625F, yl - half, rs), at(f, 0.0625F, yl - half, ro), at(f, 0.0625F, yl + half, ro), at(f, 0.0625F, yl + half, rs), neg(across(f)), Paint.BANDED);
                }
                int nf = Math.floorMod(f - 1, sides);
                boolean nextOn = i + 1 < span && clear(nf, Math.max(y0, yr - half), Math.min(y1, yr + rise + half));
                if (!nextOn) {
                    orient(at(f, facetW - 0.0625F, yr - half, rs), at(f, facetW - 0.0625F, yr - half, ro), at(f, facetW - 0.0625F, yr + half, ro), at(f, facetW - 0.0625F, yr + half, rs), across(f), Paint.BANDED);
                }
            }
            last = on;
        }
    }

    // A rib on facet f from y0 to y1, in runs where the wall is clear.
    private void rib(int f, float y0, float y1) {
        double a = facetMid(f);
        float runStart = -1.0F;
        for (float t = y0; t <= y1 + 1.0E-4F; t += 1.0F) {
            boolean ok = t < y1 && clear(f, t, Math.min(y1, t + 1.0F));
            if (ok && runStart < 0.0F) {
                runStart = t;
            } else if (!ok && runStart >= 0.0F) {
                float end = Math.min(y1, t);
                if (end - runStart >= 4.0F) {
                    boss((float) a, rs, (runStart + end) / 2.0F, RIB_W, end - runStart, 0.5F, RIB_OUT, Paint.PLAIN, Paint.BANDED);
                }
                runStart = -1.0F;
            }
        }
    }

    // Each Vessel Port's flange: a riveted plate on the wall and its collar standing off it.
    private void portFlanges() {
        for (PortBlock p : portBlocks) {
            float a = blockAngle(p.x(), p.u()), y = (p.y() + 0.5F) * PX;
            boss(a, rs, y, 14.0F, 14.0F, 0.5F, 1.5F, Paint.PLATE, Paint.BANDED);
            boss(a, rs, y, 10.0F, 10.0F, 0.5F, 3.0F, Paint.PLAIN, Paint.BANDED);
        }
    }

    // The control desk on the front wall at the controller: a cabinet standing off the wall, louvres low on its front,
    // a lit screen in a recess above them.
    private void desk() {
        float a = frontAngle(), y = (controller[1] + 0.5F) * PX;
        boss(a, rs, y, 12.0F, 14.0F, 1.0F, 3.0F, Paint.GEN_PLAIN, Paint.GEN_PLAIN);
        // Its lid half a pixel under the block's top, clear of a viewport's sill right over the controller.
        boss(a, rs, y + 7.0F, 13.0F, 1.0F, 1.0F, 3.5F, Paint.GEN_MID, Paint.GEN_LIT);
        bossFace(a, rs, y - 4.0F, 10.0F, 3.0F, 3.05F, Paint.VENT, false);
        boss(a, rs, y + 2.0F, 10.0F, 8.0F, 0.2F, 3.4F, Paint.DARK, Paint.MID);
        bossFace(a, rs, y + 2.0F, 8.0F, 6.0F, 3.45F, Paint.M_SCREEN, true);
    }

    // --- Inside ---

    // A port duct behind each viewport, from the shell's inner face in toward the axis: its walls (plated, facing into
    // it) where it meets no more opening, to the chamber's outer wall at the chamber's rows, else to a blanket module's
    // plate across its end.
    private void ducts() {
        float rin = rs - WALL;
        for (int f = 0; f < sides; f++) {
            double a0 = start + f * step, a1 = a0 + step;
            for (int j = 0; j < blocksHigh; j++) {
                if (!open[f][j]) {
                    continue;
                }
                boolean chamber = inChamber(j);
                // The blanket is the chamber's outer wall (d = r): off the chamber's rows the duct ends at its outer face.
                float rend = chamber ? chamberOut : (radius + 0.5F) * PX;
                float t0 = j * PX, t1 = t0 + PX;
                float[] in = across(f);
                // Into the chamber, the port runs in to the vessel's liner (see linerR): its side walls in steps that follow
                // the liner's curve, its floor and ceiling to where the liner is at their height.
                float steps = chamber ? Math.max(1, Math.round((t1 - t0) / 2.0F)) : 1;
                for (int k = 0; k < steps; k++) {
                    float ya = t0 + (t1 - t0) * k / steps, yb = t0 + (t1 - t0) * (k + 1) / steps;
                    float ea = chamber ? linerR(ya) : rend, eb = chamber ? linerR(yb) : rend;
                    if (!open[Math.floorMod(f + 1, sides)][j]) {
                        ductWall(new float[][] { polar(a1, rin, ya), polar(a1, ea, ya), polar(a1, eb, yb), polar(a1, rin, yb), in }, chamber);
                    }
                    if (!open[Math.floorMod(f - 1, sides)][j]) {
                        ductWall(new float[][] { polar(a0, rin, ya), polar(a0, ea, ya), polar(a0, eb, yb), polar(a0, rin, yb), neg(in) }, chamber);
                    }
                }
                // Against a duct into the chamber in the next row, a blanket row's floor (or ceiling) stands LEDGE off the
                // chamber's roof (or floor), so the two read as a ledge with a lip, not one sheet seen from both sides.
                boolean ledgeBelow = !chamber && j > 0 && open[f][j - 1] && inChamber(j - 1);
                boolean ledgeAbove = !chamber && j + 1 < blocksHigh && open[f][j + 1] && inChamber(j + 1);
                float floor = ledgeBelow ? t0 + LEDGE : t0, ceiling = ledgeAbove ? t1 - LEDGE : t1;
                if (j == 0 || !open[f][j - 1] || inChamber(j - 1) != chamber) {
                    float end = chamber ? portEnd(floor) : rend;
                    ductWall(new float[][] { polar(a0, rin, floor), polar(a1, rin, floor), polar(a1, end, floor), polar(a0, end, floor), { 0, 1, 0 } },
                            chamber);
                }
                if (j + 1 >= blocksHigh || !open[f][j + 1] || inChamber(j + 1) != chamber) {
                    float end = chamber ? portEnd(ceiling) : rend;
                    ductWall(new float[][] { polar(a0, rin, ceiling), polar(a1, rin, ceiling), polar(a1, end, ceiling), polar(a0, end, ceiling),
                            { 0, -1, 0 } }, chamber);
                }
                double out = (a0 + a1) / 2.0;
                float[] facing = { (float) Math.cos(out), 0, (float) Math.sin(out) };
                if (ledgeBelow) {
                    orient(polar(a0, rin, t0), polar(a1, rin, t0), polar(a1, rin, floor), polar(a0, rin, floor), facing, Paint.MID);
                }
                if (ledgeAbove) {
                    orient(polar(a0, rin, ceiling), polar(a1, rin, ceiling), polar(a1, rin, t1), polar(a0, rin, t1), facing, Paint.MID);
                }
                if (!chamber) {
                    double mid = (a0 + a1) / 2.0;
                    orient(polar(a0, rend, t0), polar(a1, rend, t0), polar(a1, rend, t1), polar(a0, rend, t1),
                            new float[] { (float) Math.cos(mid), 0, (float) Math.sin(mid) }, Paint.PLATE);
                }
            }
        }
    }

    // A duct's wall {p0, p1, p2, p3, facing}: plain steel in the blanket; into the chamber, the port's walls are the
    // vessel's own, in the First Wall's armour laid on the block grid (so they carry on from step to step and meet the
    // liner's tiles), the Divertor's targets on a floor.
    private void ductWall(float[][] w, boolean chamber) {
        if (!chamber) {
            orient(w[0], w[1], w[2], w[3], w[4], Paint.PLAIN);
            return;
        }
        float[] n = w[4];
        if (Math.abs(n[1]) > 0.5F) {
            tiledFlat(List.of(w[0], w[1], w[2], w[3]), n, n[1] > 0 ? DIVERTOR_X : FW_X);
        } else {
            tiledUpright(List.of(w[0], w[1], w[2], w[3]), n, FW_X);
        }
    }

    // A wall standing on a radius of the ring (its points round it), facing n, laid in a tile at 1:1: across it by
    // distance from the axis, up it by height, so walls on the same radius carry on from one to the next.
    private void tiledUpright(List<float[]> points, float[] n, int tileX) {
        float[] first = points.get(0);
        float[] right = normalize(new float[] { first[0] - cx, 0.0F, first[2] - cu });
        float[] up = { 0, 1, 0 };
        if (dot(cross(right, up), n) < 0) {
            right = new float[] { -right[0], 0.0F, -right[2] };
        }
        float[] base = { cx, 0.0F, cu };
        List<float[]> st = new ArrayList<>();
        float sMin = Float.MAX_VALUE, sMax = -Float.MAX_VALUE, tMin = Float.MAX_VALUE, tMax = -Float.MAX_VALUE;
        for (float[] p : points) {
            float[] d = sub(p, base);
            float sv = dot(d, right), tv = p[1];
            st.add(new float[] { sv, tv });
            sMin = Math.min(sMin, sv);
            sMax = Math.max(sMax, sv);
            tMin = Math.min(tMin, tv);
            tMax = Math.max(tMax, tv);
        }
        // Counter-clockwise in its own s and t (seen from the side it faces).
        float area = 0.0F;
        for (int i = 0; i < st.size(); i++) {
            float[] a = st.get(i), b = st.get((i + 1) % st.size());
            area += a[0] * b[1] - b[0] * a[1];
        }
        if (area < 0) {
            java.util.Collections.reverse(st);
        }
        List<float[]> rects = new ArrayList<>();
        for (float gs = (float) Math.floor(sMin / TILE) * TILE; gs < sMax; gs += TILE) {
            for (float gt = (float) Math.floor(tMin / TILE) * TILE; gt < tMax; gt += TILE) {
                rects.add(new float[] { gs, gs + TILE, gt, gt + TILE, tileX, TILE_Y });
            }
        }
        paintPlane(st, base, right, up, rects, false);
    }

    // The plasma's light on the walls of the ducts into the chamber: each a hair in front of its wall, in the plasma
    // tile's faint edge rows (so it's drawn and tinted with the halo, and shows through a viewport at a slant too).
    private void spill() {
        float u0 = PLASMA_X / (float) SHEET_W, u1 = (PLASMA_X + TILE) / (float) SHEET_W, v0 = TILE_Y / (float) SHEET_H, v1 = (TILE_Y + 2) / (float) SHEET_H;
        for (float[][] w : spillFaces) {
            float[] n = w[4];
            float[][] p = new float[4][];
            for (int k = 0; k < 4; k++) {
                p[k] = new float[] { w[k][0] + n[0] * 0.05F, w[k][1] + n[1] * 0.05F, w[k][2] + n[2] * 0.05F };
            }
            float[] uv = { u0, v1, u1, v1, u1, v0, u0, v0 };
            if (dot(cross(sub(p[2], p[0]), sub(p[3], p[1])), n) >= 0) {
                quad(p[0], p[1], p[2], p[3], uv, false);
            } else {
                quad(p[3], p[2], p[1], p[0], uv, false);
            }
        }
    }

    // The plasma chamber: the vacuum vessel's rounded liner (see liner), all round the ring, and over and under it the
    // chamber's roof and floor (a port reaching the chamber's top or bottom row looks over or under the liner at them),
    // run in past the centre column's facets.
    private void chamber() {
        int innerSides = Math.max(12, sides / 4);
        float rc = chamberIn * (float) Math.cos(Math.PI / innerSides) - 0.5F;
        for (int f = 0; f < sides; f++) {
            double a0 = start + f * step, a1 = a0 + step;
            tiledFlat(List.of(polar(a0, chamberOut, chamberHigh), polar(a1, chamberOut, chamberHigh), polar(a1, rc, chamberHigh), polar(a0, rc, chamberHigh)),
                    new float[] { 0, -1, 0 }, FW_X);
            tiledFlat(List.of(polar(a0, chamberOut, chamberLow), polar(a1, chamberOut, chamberLow), polar(a1, rc, chamberLow), polar(a0, rc, chamberLow)),
                    new float[] { 0, 1, 0 }, DIVERTOR_X);
            liner(f, a0, a1);
        }
    }

    // The liner's cross-section: its middle (radius, height) and half-width and half-height, filling the chamber.
    private float linerCentre() {
        return (chamberIn + chamberOut) / 2.0F;
    }

    private float linerMid() {
        return (chamberLow + chamberHigh) / 2.0F;
    }

    private float linerHalfW() {
        return (chamberOut - chamberIn) / 2.0F - 0.5F;
    }

    private float linerHalfH() {
        return (chamberHigh - chamberLow) / 2.0F - 0.5F;
    }

    // How far out the liner's outer side is at height y (over or under it, its middle), a little in from its curve so a
    // port's walls meet its facets with no gap.
    private float linerR(float y) {
        float k = Math.min(1.0F, Math.abs(y - linerMid()) / linerHalfH());
        float across = (float) Math.pow(Math.max(0.0, 1.0 - Math.pow(k, LINER_POWER)), 1.0 / LINER_POWER);
        return linerCentre() + Math.max(0.0F, linerHalfW() * across - 0.4F);
    }

    // Where a port's floor or ceiling at height y ends: at the liner, or in the chamber's roof or floor plane at the
    // chamber's outer edge, where the roof or floor carries on.
    private float portEnd(float y) {
        return y >= chamberHigh - 0.01F || y <= chamberLow + 0.01F ? chamberOut : linerR(y);
    }

    // The vacuum vessel's rounded liner round the plasma, inside the chamber's box: its cross-section a rounded square
    // (a superellipse filling the chamber, LINER_POWER) swept round the axis on the outer wall's facets, facing in (so a
    // viewport looks through its near side, culled, at its far side and the plasma within), in the First Wall's armour
    // with the Divertor's targets round its bottom. It faces in only: a port looks straight through its near side (culled)
    // at its far side and the plasma, nothing of it in the way; the port's walls run in to meet it, and the chamber's
    // roof and floor close the space over and under it.
    //
    // Its tiles are laid on the surface, not facet by facet: a whole number of tile columns round the ring and of tile
    // rows round the cross-section, each tile a fixed share of each, so they run on straight across the facets both
    // ways (stretched a little toward the outside of the ring, as the surface is wider there). Every facet is cut at the
    // tiles' edges, and a whole tile is one tile or the other (Divertor rows are whole rows).
    private void liner(int f, double a0, double a1) {
        float centre = linerCentre(), mid = linerMid(), halfW = linerHalfW(), halfH = linerHalfH();
        float[][] ring = new float[LINER_ROUND + 1][];
        float[] arc = new float[LINER_ROUND + 1];
        for (int j = 0; j <= LINER_ROUND; j++) {
            double theta = -Math.PI / 2.0 + 2.0 * Math.PI * j / LINER_ROUND;
            double c = Math.cos(theta), sn = Math.sin(theta);
            ring[j] = new float[] { centre + halfW * (float) (Math.signum(c) * Math.pow(Math.abs(c), 2.0 / LINER_POWER)),
                    mid + halfH * (float) (Math.signum(sn) * Math.pow(Math.abs(sn), 2.0 / LINER_POWER)) };
            if (j > 0) {
                arc[j] = arc[j - 1] + (float) Math.hypot(ring[j][0] - ring[j - 1][0], ring[j][1] - ring[j - 1][1]);
            }
        }
        // Tiles round the ring (at the cross-section's middle) and round the cross-section, whole numbers of each.
        float around = Math.max(4, Math.round((float) (2.0 * Math.PI * centre) / TILE));
        float rows = Math.max(2, Math.round(arc[LINER_ROUND] / TILE));
        float u0 = f * around / sides, u1 = (f + 1) * around / sides;
        for (int j = 0; j < LINER_ROUND; j++) {
            float v0 = arc[j] / arc[LINER_ROUND] * rows, v1 = arc[j + 1] / arc[LINER_ROUND] * rows;
            // The facet's corners: a0 / a1 round the ring, ring point j / j + 1 round the cross-section.
            float[] c00 = polar(a0, ring[j][0], ring[j][1]), c10 = polar(a1, ring[j][0], ring[j][1]);
            float[] c11 = polar(a1, ring[j + 1][0], ring[j + 1][1]), c01 = polar(a0, ring[j + 1][0], ring[j + 1][1]);
            // The plasma's glow on its inside.
            float rm = (ring[j][0] + ring[j + 1][0]) / 2.0F, ym = (ring[j][1] + ring[j + 1][1]) / 2.0F;
            double am = a0 + (a1 - a0) / 2.0;
            spillFaces.add(new float[][] { c00, c10, c11, c01, normalize(sub(polar(am, centre, mid), polar(am, rm, ym))) });
            for (float ua = u0; ua < u1 - 1.0E-4F; ua = Math.min(u1, (float) Math.floor(ua + 1.0E-4F) + 1.0F)) {
                float ub = Math.min(u1, (float) Math.floor(ua + 1.0E-4F) + 1.0F);
                for (float va = v0; va < v1 - 1.0E-4F; va = Math.min(v1, (float) Math.floor(va + 1.0E-4F) + 1.0F)) {
                    float vb = Math.min(v1, (float) Math.floor(va + 1.0E-4F) + 1.0F);
                    int row = (int) Math.floor(va + 1.0E-4F);
                    // The Divertor's targets: the rows whose middle lies round the bottom (the cross-section starts there).
                    float rowMiddle = (row + 0.5F) / rows;
                    int tileX = rowMiddle < 0.14F || rowMiddle > 0.86F ? DIVERTOR_X : FW_X;
                    linerTile(c00, c10, c11, c01, (ua - u0) / (u1 - u0), (ub - u0) / (u1 - u0), (va - v0) / (v1 - v0), (vb - v0) / (v1 - v0),
                            ua - (float) Math.floor(ua + 1.0E-4F), ub - (float) Math.floor(ua + 1.0E-4F), va - row, vb - row, tileX, polar(a0 + (a1 - a0) / 2.0, centre, mid));
                }
            }
        }
    }

    // One piece of a liner facet: from fa to fb across it and fb0 to fb1 round the cross-section (0..1 of the facet
    // c00 c10 c11 c01), showing tile tileX from s0 to s1 and t0 to t1 (0..1 of the tile), facing toward the point given
    // (the middle of the cross-section).
    private void linerTile(float[] c00, float[] c10, float[] c11, float[] c01, float fa, float fb, float ga, float gb, float s0, float s1, float t0,
            float t1, int tileX, float[] toward) {
        float[] p0 = bilinear(c00, c10, c11, c01, fa, ga), p1 = bilinear(c00, c10, c11, c01, fb, ga);
        float[] p2 = bilinear(c00, c10, c11, c01, fb, gb), p3 = bilinear(c00, c10, c11, c01, fa, gb);
        float uA = (tileX + s0 * TILE) / SHEET_W, uB = (tileX + s1 * TILE) / SHEET_W;
        float vA = (TILE_Y + (1.0F - t0) * TILE) / SHEET_H, vB = (TILE_Y + (1.0F - t1) * TILE) / SHEET_H;
        float[] n = cross(sub(p1, p0), sub(p3, p0));
        float[] middle = { (p0[0] + p2[0]) / 2.0F, (p0[1] + p2[1]) / 2.0F, (p0[2] + p2[2]) / 2.0F };
        if (dot(n, sub(toward, middle)) >= 0) {
            quad(p0, p1, p2, p3, new float[] { uA, vA, uB, vA, uB, vB, uA, vB }, false);
        } else {
            quad(p3, p2, p1, p0, new float[] { uA, vB, uB, vB, uB, vA, uA, vA }, false);
        }
    }

    // A flat polygon facing up or down laid in a tile at 1:1 on the block grid.
    private void tiledFlat(List<float[]> points, float[] n, int tileX) {
        float[] right = { 1, 0, 0 }, up = n[1] > 0 ? new float[] { 0, 0, -1 } : new float[] { 0, 0, 1 };
        float y = points.get(0)[1];
        float[] base = { 0, y, 0 };
        List<float[]> st = new ArrayList<>();
        float sMin = Float.MAX_VALUE, sMax = -Float.MAX_VALUE, tMin = Float.MAX_VALUE, tMax = -Float.MAX_VALUE;
        for (float[] p : points) {
            float[] d = sub(p, base);
            float s = dot(d, right), t = dot(d, up);
            st.add(new float[] { s, t });
            sMin = Math.min(sMin, s);
            sMax = Math.max(sMax, s);
            tMin = Math.min(tMin, t);
            tMax = Math.max(tMax, t);
        }
        List<float[]> rects = new ArrayList<>();
        for (float s = (float) Math.floor(sMin / TILE) * TILE; s < sMax; s += TILE) {
            for (float t = (float) Math.floor(tMin / TILE) * TILE; t < tMax; t += TILE) {
                rects.add(new float[] { s, s + TILE, t, t + TILE, tileX, TILE_Y });
            }
        }
        paintPlane(st, base, right, up, rects, false);
    }

    // The plasma, scale of its full size: a D-shaped torus in the chamber (a stellarator's an ellipse turning five
    // half-turns round the ring, its centre swinging with it), in the plasma tile, its streaks running round the ring.
    private void plasma(float scale) {
        float r0 = (chamberIn + chamberOut) / 2.0F, mid = (chamberLow + chamberHigh) / 2.0F;
        float a = (chamberOut - chamberIn) / 2.0F * PLASMA_FILL;
        float b = Math.min(KAPPA * a, (chamberHigh - chamberLow) / 2.0F * 0.82F);
        int around = Math.max(48, sides * 2);
        float[][][] ring = new float[around + 1][POLOIDAL + 1][];
        for (int i = 0; i <= around; i++) {
            double phi = 2.0 * Math.PI * i / around;
            for (int j = 0; j <= POLOIDAL; j++) {
                double th = 2.0 * Math.PI * j / POLOIDAL;
                double dr, dy;
                if (stellarator) {
                    double turn = 2.5 * phi;
                    double ea = a * 1.1 * scale, eb = Math.min(b, a * 1.4) * 0.62 * scale;
                    double x = ea * Math.cos(th), y = eb * Math.sin(th);
                    dr = x * Math.cos(turn) - y * Math.sin(turn) + 0.12 * a * Math.cos(5.0 * phi);
                    dy = x * Math.sin(turn) + y * Math.cos(turn) + 0.12 * a * Math.sin(5.0 * phi);
                } else {
                    dr = a * scale * Math.cos(th + DELTA * Math.sin(th));
                    dy = b * scale * Math.sin(th);
                }
                ring[i][j] = polar(phi, r0 + (float) dr, mid + (float) dy);
            }
        }
        float u0 = PLASMA_X / (float) SHEET_W, du = TILE / (float) SHEET_W, v0 = TILE_Y / (float) SHEET_H, dv = TILE / (float) SHEET_H;
        for (int i = 0; i < around; i++) {
            float ua = u0 + du * ((i % 4) / 4.0F), ub = ua + du / 4.0F;
            for (int j = 0; j < POLOIDAL; j++) {
                // Round the tube the tile's rows, lightest on the outboard and inboard sides.
                float va = v0 + dv * (0.5F + 0.45F * (float) Math.sin(2.0 * Math.PI * j / POLOIDAL)), vb = v0 + dv * (0.5F + 0.45F * (float) Math.sin(2.0 * Math.PI * (j + 1) / POLOIDAL));
                // Wound to face out of the tube: round it first, then along the ring.
                quad(ring[i][j], ring[i][j + 1], ring[i + 1][j + 1], ring[i + 1][j], new float[] { ua, va, ua, vb, ub, vb, ub, va }, false);
            }
        }
    }

    private static float[] neg(float[] v) {
        return new float[] { -v[0], -v[1], -v[2] };
    }

    private static float[] add(float[] a, float[] b) {
        return new float[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
    }
}
