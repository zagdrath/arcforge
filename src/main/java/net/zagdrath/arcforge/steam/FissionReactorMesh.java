/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Fission Reactor's model (see FissionReactorRenderer), on the refinery tower's sheet: a heavy cylindrical pressure
// vessel standing in the casing frame on its plinth. From the bottom: a skirt, the bottom head, the plated shell, the
// bolted flange pair where the closure head meets it (a gasket closing the gap between them), the domed closure head,
// and the control-rod drive housings standing in a grid on the head, tied by a support plate below their latch housings.
// Sized to the structure: the vessel as wide as the footprint's narrower side allows and as tall as its height.
//
// The Reactor Glass in the side walls opens windows in the shell: each piece of glass marks the shell's facets facing it
// at its row's height (the rows of glass spread over the straight shell), and glass that touches, across or up, opens
// one window with one raised frame round it and no bars between. The glass is the glass tile's rim and glints over a
// light grey tint pane (drawn translucent), so the inside shows through it (seen from inside, a window is the inner
// wall, so none shows out through another):
// the shell's inner wall, the core support plate and the upper core plate, and the
// core between them built from what's in the vessel (see Core): fuel assemblies, control rod channels with their drive
// lines up to the upper plate, graphite and reflector, packed in a square lattice scaled to fit the shell. While the
// reactor runs the fuel glows Cherenkov blue (full-bright, in its own list, drawn only then). Nothing inside is built
// without a window to see it through.
public final class FissionReactorMesh extends VesselMesh {
    // The drive housings' pitch on the head and their radius (px), and how far out the grid reaches (of the vessel's).
    static final float CRDM_PITCH = 6.0F, CRDM_R = 1.25F, CRDM_LATCH_R = 2.0F, CRDM_REACH = 0.62F;
    // The shell's wall, the window frames' width round an opening and how far they stand out, and the gap between the
    // vessel flange and the head flange (px).
    static final float WALL = 2.0F, FRAME = 2.0F, FRAME_OUT = 1.5F, FLANGE_GAP = 0.25F;
    // How much of the inside's height the core takes (the upper plenum above it), and its lattice's widest pitch (px).
    static final float CORE_HEIGHT = 0.68F, CORE_PITCH = 14.0F;
    // How far the glass's tint pane sits in behind its rim, and how far the control panel's mount reaches into the
    // vessel's skin behind it (px).
    static final float TINT_IN = 0.1F, MOUNT_IN = 0.5F;
    // What a piece of the windowed shell is.
    private static final int SOLID = 0, FRAMED = 1, OPEN = 2;

    // A glass block in the side walls: its block {x, y, u} in the frame.
    public record Viewport(int x, int y, int u) {}

    // What's inside the vessel, block by block: the inside's size and a letter for each block (see index): F a Fuel
    // Channel, C a Control Rod Channel, G Graphite Moderator, R a Steel Reflector, anything else coolant.
    public record Core(int width, int height, int length, String cells) {
        public static final char FUEL = 'F', CONTROL = 'C', GRAPHITE = 'G', REFLECTOR = 'R', COOLANT = '.';

        // Where block (x, y, u) of the inside is in cells: x fastest, then u, then y up.
        public static int index(int width, int length, int x, int y, int u) {
            return (y * length + u) * width + x;
        }

        char at(int x, int y, int u) {
            if (x < 0 || y < 0 || u < 0 || x >= width || y >= height || u >= length) {
                return COOLANT;
            }
            int i = index(width, length, x, y, u);
            return i < cells.length() ? cells.charAt(i) : COOLANT;
        }

        boolean solid(int x, int y, int u) {
            char c = at(x, y, u);
            return c == FUEL || c == CONTROL || c == GRAPHITE || c == REFLECTOR;
        }

        // A full core of fuel with a control rod channel in every other column of every other row, for a vessel whose
        // inside isn't known.
        public static Core generic(ModelFrame frame) {
            int w = Math.max(0, frame.width() - 2), h = Math.max(0, frame.height() - 2), l = Math.max(0, frame.length() - 2);
            StringBuilder cells = new StringBuilder();
            for (int y = 0; y < h; y++) {
                for (int u = 0; u < l; u++) {
                    for (int x = 0; x < w; x++) {
                        cells.append(x % 2 == 1 && u % 2 == 1 ? CONTROL : FUEL);
                    }
                }
            }
            return new Core(w, h, l, cells.toString());
        }
    }

    final float radius, skirtTop, bottomHeadTop, flangeY, headY, headHeight, crdmTop, crown;
    final List<Viewport> viewports;
    final Core core;
    final int panelX, panelY;
    // The windows: which of the straight shell's facets are open at each row of glass, the rows' span up the shell
    // (lo to hi, rowH each), the core support plate's and the upper core plate's heights, and a facet's width.
    private final int rows;
    private final boolean[][] open;
    private final boolean windowed;
    private final float lo, hi, rowH, floorY, ceilY, facetW;
    // The fuel's faces seen through the windows {origin, right, up, {w, h}, normal}, for the running glow.
    private final List<float[][]> fuelFaces = new ArrayList<>();
    // The glass's tint panes {p0, p1, p2, p3} (counter-clockwise seen from outside), for the translucent list.
    private final List<float[][]> tintPanes = new ArrayList<>();

    public FissionReactorMesh(ModelFrame frame, List<Viewport> viewports, int panelX, int panelY) {
        this(frame, viewports, Core.generic(frame), panelX, panelY);
    }

    public FissionReactorMesh(ModelFrame frame, List<Viewport> viewports, Core core, int panelX, int panelY) {
        super(frame);
        this.viewports = viewports;
        this.core = core;
        this.panelX = panelX;
        this.panelY = panelY;
        this.radius = Math.min(width, length) / 2.0F - 6.0F;
        this.skirtTop = PLINTH + 4.0F;
        this.bottomHeadTop = skirtTop + Math.max(4.0F, radius * 0.22F);
        this.headHeight = radius * 0.45F;
        this.crdmTop = height - 3.0F;
        float crdm = Math.max(8.0F, Math.min(28.0F, height * 0.18F));
        this.headY = crdmTop - crdm - headHeight;
        this.flangeY = headY - 2.0F;
        this.crown = headY + headHeight;
        float shellTop = flangeY - 2.0F;
        this.rows = Math.max(0, frame.height() - 2);
        this.lo = bottomHeadTop + FRAME + 2.0F;
        this.hi = shellTop - FRAME - 2.0F;
        this.rowH = rows > 0 ? (hi - lo) / rows : 0.0F;
        this.floorY = bottomHeadTop + 1.0F;
        this.ceilY = shellTop - 1.0F;
        this.facetW = 2.0F * (float) Math.sin(Math.PI / SHELL_SIDES) * radius;
        this.open = new boolean[SHELL_SIDES][Math.max(1, rows)];
        boolean any = false;
        if (rows > 0 && hi - lo >= 4.0F) {
            for (Viewport viewport : viewports) {
                any |= mark(viewport);
            }
        }
        this.windowed = any;
    }

    // The still parts and the nozzles of these ports: {solid, cutout (the windows' glass), glow (the panel's screen),
    // running (the fuel's Cherenkov glow, drawn only while the reactor runs), tint (the glass's light grey tint, drawn
    // translucent)}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        fuelFaces.clear();
        tintPanes.clear();
        plinth();
        casingFrame();
        vessel();
        drives();
        if (windowed) {
            inside();
        }
        panel(panelX, panelY);
        panelMount(panelX, panelY);
        float[][] parts = finish(ports);
        // The Cherenkov glow over the fuel, a hair out from it.
        glow = new Floats();
        glowing = true;
        for (float[][] f : fuelFaces) {
            float[] n = f[4], origin = { f[0][0] + n[0] * 0.1F, f[0][1] + n[1] * 0.1F, f[0][2] + n[2] * 0.1F };
            face(origin, f[1], f[2], f[3][0], f[3][1], Paint.M_CORE_LIT, false);
        }
        float[] running = glow.toArray();
        // The tint: each pane one flat tone (the light steel of a rivet head), its see-through set by the renderer.
        glow = new Floats();
        float tu = (Paint.RIVET.x + 2.0F) / SHEET_W, tv = (Paint.RIVET.y + 2.0F) / SHEET_H;
        for (float[][] p : tintPanes) {
            quad(p[0], p[1], p[2], p[3], new float[] { tu, tv, tu, tv, tu, tv, tu, tv }, false);
        }
        glowing = false;
        return new float[][] { parts[0], parts[1], parts[2], running, glow.toArray() };
    }

    // The control panel's mount: a block from the cabinet's back into the vessel's skin behind it, so the panel stands
    // on the vessel instead of floating in front of it. It's cut where the vessel's outline changes (the skirt, the
    // bottom head, the shell, the flanges, the closure head), each piece reaching MOUNT_IN into the deepest point of the
    // skin behind it; where the skin comes further forward the piece ends inside the bottom head, the wall or the
    // flanges, never through the shell's wall into the inside the windows show.
    private void panelMount(int bx, int by) {
        float[] cabinet = panelBox(bx, by);
        if (cabinet == null) {
            return;
        }
        float x0 = cabinet[0] + 1.0F, x1 = cabinet[3] - 1.0F, ya = cabinet[1] + 1.0F, yb = cabinet[4] - 2.0F, u0 = cabinet[5] - 0.25F;
        float dx = Math.max(Math.abs(x0 - cx), Math.abs(x1 - cx));
        float mid = skirtTop + (bottomHeadTop - skirtTop) * 0.55F;
        float[] bands = { PLINTH, skirtTop, mid, bottomHeadTop, flangeY - 2.0F, headY, crown };
        float[][] head = head(headY, radius, headHeight, true);
        // The pieces, bottom up {y0, y1, depth}; null where nothing of the vessel is behind it.
        List<float[]> pieces = new ArrayList<>();
        for (int i = 0; i + 1 < bands.length; i++) {
            float p = Math.max(ya, bands[i]), q = Math.min(yb, bands[i + 1]);
            if (q - p < 0.01F) {
                continue;
            }
            float least = Float.MAX_VALUE;
            for (int k = 0; k <= 16; k++) {
                least = Math.min(least, outerRadius(p + (q - p) * k / 16.0F, mid, head));
            }
            // The facets' middles are the nearest the skin comes in to the axis.
            float rin = least * (float) Math.cos(Math.PI / SHELL_SIDES);
            if (rin - dx < 1.0F) {
                continue;
            }
            float depth = cu - (float) Math.sqrt(rin * rin - dx * dx) + MOUNT_IN;
            if (depth > u0 + 0.01F) {
                pieces.add(new float[] { p, q, depth });
            }
        }
        for (int i = 0; i < pieces.size(); i++) {
            float[] piece = pieces.get(i);
            // Faces shared with the piece above or below are inside the two, and the back is inside the vessel.
            boolean below = i > 0 && pieces.get(i - 1)[1] >= piece[0] - 0.01F, above = i + 1 < pieces.size() && pieces.get(i + 1)[0] <= piece[1] + 0.01F;
            int skip = SKIP_NEG_U | SKIP_POS_U | (below ? SKIP_DOWN : 0) | (above ? SKIP_UP : 0);
            box(x0, piece[0], u0, x1, piece[1], piece[2], Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, skip);
        }
    }

    // The vessel's outer radius at height y (px), mid where the bottom head's taper bends and head its closure head's
    // outline: the skirt, the bottom head, the shell, the gasket between the flanges (their narrowest) and the head's
    // facets; 0 under the skirt and over the crown.
    private float outerRadius(float y, float mid, float[][] head) {
        float r = radius;
        if (y < PLINTH || y > crown) {
            return 0.0F;
        }
        if (y <= skirtTop) {
            return r - 4.0F;
        }
        if (y <= mid) {
            return r - 4.0F + 3.0F * (y - skirtTop) / (mid - skirtTop);
        }
        if (y <= bottomHeadTop) {
            return r - 1.0F + (y - mid) / (bottomHeadTop - mid);
        }
        if (y <= flangeY - 2.0F) {
            return r;
        }
        if (y <= headY) {
            return r + 1.0F;
        }
        for (int i = 0; i + 1 < head.length; i++) {
            if (y <= head[i + 1][0]) {
                float t = (y - head[i][0]) / Math.max(1.0E-4F, head[i + 1][0] - head[i][0]);
                return head[i][1] + (head[i + 1][1] - head[i][1]) * t;
            }
        }
        return 0.0F;
    }

    // The skirt, the bottom head, the shell up to the flange (windowed when there's glass), the flange pair with the
    // gasket between them and the closure head.
    private void vessel() {
        float r = radius;
        shell(new float[][] { { PLINTH, r - 4.0F }, { skirtTop, r - 4.0F } }, SHELL_SIDES);
        ring(skirtTop, r - 4.0F, 0.0F, SHELL_SIDES, false);
        shell(new float[][] { { skirtTop, r - 4.0F }, { skirtTop + (bottomHeadTop - skirtTop) * 0.55F, r - 1.0F }, { bottomHeadTop, r } }, SHELL_SIDES);
        if (windowed) {
            windowedShell();
        } else {
            shell(new float[][] { { bottomHeadTop, r }, { flangeY - 2.0F, r } }, SHELL_SIDES);
        }
        // The vessel flange and the head flange bolted to it, a little apart, the gasket set back between them: the
        // shell is open inside, so without it the gap showed the sky through the vessel.
        lining();
        flange(flangeY - 2.0F, flangeY, r + 2.0F, r, SHELL_SIDES);
        uprightTube(cx, cu, new float[][] { { flangeY, r + 1.0F }, { flangeY + FLANGE_GAP, r + 1.0F } }, SHELL_SIDES, Math.PI / SHELL_SIDES, Paint.DEEP, null);
        flange(flangeY + FLANGE_GAP, headY, r + 2.0F, r, SHELL_SIDES);
        uprights.add(new float[] { cx, cu, r + 2.0F, flangeY - 2.0F, headY });
        float[][] head = head(headY, r, headHeight, true);
        uprightTube(cx, cu, head, SHELL_SIDES, Math.PI / SHELL_SIDES, Paint.CASING, Paint.PLAIN);
        uprights.add(new float[] { cx, cu, r * 0.75F, headY, crown });
    }

    // The shell's lining: its inner wall (bare grain, facing in) from the bottom head up to the closure head, closed by
    // a plate across the bottom and one under the head. Through the windows it's the inside's wall, and a window seen
    // from inside is wall too (facing in, it's culled seen from outside through its own window), so no window shows out
    // through another. It also backs every seam of the vessel round it: the vessel is hollow, and a hairline crack where
    // two of its parts meet (the shell's top and the flange) showed the sky through it; now it shows steel.
    private void lining() {
        float ri = radius - WALL, y0 = bottomHeadTop, y1 = headY;
        double start = Math.PI / SHELL_SIDES;
        for (int f = 0; f < SHELL_SIDES; f++) {
            // A hair past its edges into the next facet and past the end plates, out of sight behind them, so no
            // hairline opens along a seam where their corners round differently.
            float e = 0.05F;
            List<float[]> points = List.of(at(f, facetW + e, y0 - e, ri), at(f, -e, y0 - e, ri), at(f, -e, y1 + e, ri), at(f, facetW + e, y1 + e, ri));
            plateIn(plateFrame(points, new Plating(Steel.MACHINE, false, false, false)), points);
        }
        uprightDisc(cx, y0, cu, ri, SHELL_SIDES, start, true, Paint.PLAIN);
        uprightDisc(cx, y1, cu, ri, SHELL_SIDES, start, false, Paint.PLAIN);
    }

    // The control-rod drive housings in a square grid on the head (as many as fit within CRDM_REACH of the vessel's
    // radius), each a slim pressure housing from the head up to a wider latch housing and a capped top; the seismic
    // support plate round them all near the top.
    private void drives() {
        float reach = radius * CRDM_REACH;
        int n = (int) Math.floor(reach / CRDM_PITCH);
        float top = crdmTop, latch = top - 4.0F;
        float plateY = latch - 3.0F;
        boolean any = false;
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                float dx = i * CRDM_PITCH, du = j * CRDM_PITCH;
                float dr = (float) Math.hypot(dx, du);
                if (dr > reach) {
                    continue;
                }
                any = true;
                float x = cx + dx, u = cu + du, base = headAt(headY, radius, headHeight, dr) - 1.0F;
                uprightTube(x, u, new float[][] { { base, CRDM_R + 0.75F }, { base + 2.0F, CRDM_R + 0.75F } }, 6, Math.PI / 6, Paint.BANDED, null);
                uprightTube(x, u, new float[][] { { base + 2.0F, CRDM_R }, { latch, CRDM_R } }, 6, Math.PI / 6, Paint.BANDED, null);
                uprightTube(x, u, new float[][] { { latch, CRDM_LATCH_R }, { top, CRDM_LATCH_R } }, 6, Math.PI / 6, Paint.POLISHED, Paint.MID);
            }
        }
        if (!any) {
            return;
        }
        // The support plate: a ring round the grid, its inside left open between the housings.
        float outer = reach + CRDM_LATCH_R + 2.0F;
        uprightTube(cx, cu, new float[][] { { plateY - 1.5F, outer }, { plateY, outer } }, 16, Math.PI / 16, Paint.BANDED, null);
        ring(plateY, outer, outer - 2.5F, 16, true);
        ring(plateY - 1.5F, outer, outer - 2.5F, 16, false);
        uprights.add(new float[] { cx, cu, outer, crown, top });
    }

    // --- The windows ---

    // Opens the shell's facets facing a glass block at its row: from the facet edge nearest one side of the block's
    // outer face round to the one nearest the other, so glass side by side opens facets side by side. False when it's
    // not in a side wall or too narrow to open a facet.
    private boolean mark(Viewport v) {
        int row = v.y() - 1;
        if (row < 0 || row >= rows) {
            return false;
        }
        float x0, u0, x1, u1;
        if (v.u() == 0 || v.u() == frame.length() - 1) {
            x0 = v.x() * PX;
            x1 = x0 + PX;
            u0 = u1 = v.u() == 0 ? 0.0F : length;
        } else if (v.x() == 0 || v.x() == frame.width() - 1) {
            u0 = v.u() * PX;
            u1 = u0 + PX;
            x0 = x1 = v.x() == 0 ? 0.0F : width;
        } else {
            return false;
        }
        double a = Math.atan2(u0 - cu, x0 - cx), b = Math.atan2(u1 - cu, x1 - cx);
        double d = Math.atan2(Math.sin(b - a), Math.cos(b - a));
        if (d < 0) {
            a = b;
            d = -d;
        }
        double step = 2.0 * Math.PI / SHELL_SIDES, start = Math.PI / SHELL_SIDES;
        long k0 = Math.round((a - start) / step), k1 = Math.round((a + d - start) / step);
        for (long k = k0; k < k1; k++) {
            open[(int) Math.floorMod(k, SHELL_SIDES)][row] = true;
        }
        return k1 > k0;
    }

    // Whether facet f is open at height t.
    private boolean opening(int f, float t) {
        if (t <= lo || t >= hi) {
            return false;
        }
        return open[f][Math.min(rows - 1, (int) ((t - lo) / rowH))];
    }

    // What the shell is at s across facet f (from its left edge seen from outside) and t up: open, the frame round an
    // opening (within FRAME of one, across or up, the corners too), or solid.
    private int kind(int f, float s, float t) {
        if (opening(f, t)) {
            return OPEN;
        }
        for (int df = -1; df <= 1; df++) {
            // The facet to the left (seen from outside) is the next one round.
            float across = df == 0 ? 0.0F : df == 1 ? s : facetW - s;
            if (across >= FRAME) {
                continue;
            }
            boolean[] column = open[Math.floorMod(f + df, SHELL_SIDES)];
            for (int j = 0; j < rows; j++) {
                float b0 = lo + j * rowH, b1 = b0 + rowH;
                if (column[j] && (t < b0 ? b0 - t : t > b1 ? t - b1 : 0.0F) < FRAME) {
                    return FRAMED;
                }
            }
        }
        return SOLID;
    }

    // A point on facet f of the polygon radius r round the axis: s across it from its left edge (seen from outside),
    // at height t.
    private float[] at(int f, float s, float t, float r) {
        double step = 2.0 * Math.PI / SHELL_SIDES, a0 = Math.PI / SHELL_SIDES + f * step, a1 = a0 + step;
        float k = s / facetW;
        float xl = cx + (float) Math.cos(a1) * r, ul = cu + (float) Math.sin(a1) * r;
        float xr = cx + (float) Math.cos(a0) * r, ur = cu + (float) Math.sin(a0) * r;
        return new float[] { xl + (xr - xl) * k, t, ul + (ur - ul) * k };
    }

    // Facet f's direction across it, left to right seen from outside.
    private float[] across(int f) {
        return normalize(sub(at(f, facetW, 0.0F, radius), at(f, 0.0F, 0.0F, radius)));
    }

    // The straight shell with its windows cut in it. Each facet is cut into three columns (the frame's width at either
    // edge) and into rows at the windows' and frames' edges; each piece is solid (the shell plating, laid out as the
    // whole shell's so its plates carry on round it), frame (raised FRAME_OUT, plain plate) or open. Pieces of a kind
    // are merged within each facet, and walls stand where kinds meet: the frame's edge, and the jamb from the inner
    // wall up to the frame's face round each opening (the lining is the inner wall), and the glass's rim and glints across
    // each opening.
    private void windowedShell() {
        float r = radius, ri = r - WALL, rf = r + FRAME_OUT, y0 = bottomHeadTop, y1 = flangeY - 2.0F, fw = facetW;
        int sides = SHELL_SIDES;
        List<float[]> plates = plateFrame(new float[] { cx, y0, cu }, new float[] { 1, 0, 0 }, new float[] { 0, 1, 0 }, fw * sides, y1 - y0,
                new Plating(Steel.MACHINE, true, false)).rects();
        uprights.add(new float[] { cx, cu, r, y0, y1 });
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
            // Walls along the rows' edges, merged across the facet.
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
            // Walls along the columns' edges: inside the facet, and between it and the next facet to its right.
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
        glass();
    }

    // The rows the windowed shell is cut at: its ends, the core plates, and each window row's edges and the frames'.
    private float[] cuts(float y0, float y1) {
        List<Float> at = new ArrayList<>(List.of(y0, y1, floorY, ceilY, lo - FRAME, hi + FRAME));
        for (int j = 0; j <= rows; j++) {
            float b = lo + j * rowH;
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

    // The wall between a piece of kind a and its neighbour of kind b, coded with the side it faces (0 for none): the
    // frame's edge, from the shell's face out to the frame's (facing the solid shell), or a jamb from the inner wall out
    // to the frame's face, or the shell's (facing into the opening).
    private static int wall(int a, int b) {
        if (a == b) {
            return 0;
        }
        // 1 + 3 x the outer kind + the inner kind, negated when it faces a's side.
        int low = Math.min(a, b), high = Math.max(a, b);
        boolean towardA = high == OPEN ? a == OPEN : a == SOLID;
        int code = 1 + 3 * high + low;
        return towardA ? -code : code;
    }

    // A wall's radii: from the shell's inner face (jambs) or its outer face, out to the frame's face or the shell's.
    private float[] wallRadii(int code) {
        int c = Math.abs(code) - 1, high = c / 3, low = c % 3;
        float inner = high == OPEN ? radius - WALL : radius, outer = high == OPEN && low == SOLID ? radius : radius + FRAME_OUT;
        return new float[] { inner, outer };
    }

    // A wall along a row's edge at t on facet f from sa to sb across it, facing down (toward the piece below) for a
    // negative code, else up.
    private void horizontalWall(int f, float sa, float sb, float t, int code) {
        float[] radii = wallRadii(code);
        float[] p0 = at(f, sa, t, radii[0]), p1 = at(f, sb, t, radii[0]), p2 = at(f, sb, t, radii[1]), p3 = at(f, sa, t, radii[1]);
        orient(p0, p1, p2, p3, new float[] { 0, code < 0 ? -1 : 1, 0 }, Paint.BANDED);
    }

    // A wall up a column's edge at s on facet f from ta to tb, facing left (toward the piece left of it) for a negative
    // code, else right (along dir).
    private void verticalWall(int f, float s, float ta, float tb, float[] dir, int code) {
        float[] radii = wallRadii(code);
        float[] p0 = at(f, s, ta, radii[0]), p1 = at(f, s, ta, radii[1]), p2 = at(f, s, tb, radii[1]), p3 = at(f, s, tb, radii[0]);
        float sign = code < 0 ? -1.0F : 1.0F;
        orient(p0, p1, p2, p3, new float[] { dir[0] * sign, dir[1] * sign, dir[2] * sign }, Paint.BANDED);
    }

    // The windows' glass, in the cutout list, halfway through the wall: the glass tile's 1 px rim (lit top and left,
    // dark bottom and right, in runs of up to 10 px) only round each window's outside, never between its facets or
    // rows, and the glints once, in each window's top-left corner; behind them the tint panes (see buildStatic).
    private void glass() {
        int sides = SHELL_SIDES, gx = Paint.GLASS.x, gy = Paint.GLASS.y;
        float fw = facetW, h = rowH, rg = radius - WALL / 2.0F;
        boolean[][] glinted = glints();
        for (int f = 0; f < sides; f++) {
            for (int j = 0; j < rows; j++) {
                if (!open[f][j]) {
                    continue;
                }
                boolean top = j + 1 >= rows || !open[f][j + 1], bottom = j == 0 || !open[f][j - 1];
                boolean left = !open[Math.floorMod(f + 1, sides)][j], right = !open[Math.floorMod(f - 1, sides)][j];
                List<float[]> rects = new ArrayList<>();
                for (float a = 0; a < fw - 1.0E-4F; a += 10.0F) {
                    if (top) {
                        rects.add(new float[] { a, Math.min(fw, a + 10.0F), h - 1.0F, h, gx, gy });
                    }
                    if (bottom) {
                        rects.add(new float[] { a, Math.min(fw, a + 10.0F), 0.0F, 1.0F, gx + 1, gy + 11 });
                    }
                }
                float t0 = bottom ? 1.0F : 0.0F, t1 = top ? h - 1.0F : h;
                for (float t = t1; t > t0 + 1.0E-4F; t -= 10.0F) {
                    if (left) {
                        rects.add(new float[] { 0.0F, 1.0F, Math.max(t0, t - 10.0F), t, gx, gy + 1 });
                    }
                    if (right) {
                        rects.add(new float[] { fw - 1.0F, fw, Math.max(t0, t - 10.0F), t, gx + 11, gy + 1 });
                    }
                }
                if (glinted[f][j] && fw >= 8.0F && h >= 8.0F) {
                    rects.add(new float[] { 2.0F, 7.0F, h - 7.0F, h - 2.0F, gx + 2, gy + 2 });
                }
                List<float[]> st = List.of(new float[] { 0, 0 }, new float[] { fw, 0 }, new float[] { fw, h }, new float[] { 0, h });
                paintPlane(st, at(f, 0.0F, lo + j * h, rg), across(f), new float[] { 0, 1, 0 }, rects, true);
            }
            // The tint: one pane up each run of open rows, TINT_IN behind the rim so the rim and glints stay crisp over
            // it, meeting the next facet's at their shared edge.
            float rt = rg - TINT_IN;
            for (int j = 0; j < rows;) {
                if (!open[f][j]) {
                    j++;
                    continue;
                }
                int j1 = j + 1;
                while (j1 < rows && open[f][j1]) {
                    j1++;
                }
                float t0 = lo + j * h, t1 = lo + j1 * h;
                tintPanes.add(new float[][] { at(f, 0.0F, t0, rt), at(f, fw, t0, rt), at(f, fw, t1, rt), at(f, 0.0F, t1, rt) });
                j = j1;
            }
        }
    }

    // Each window's top-left piece (seen from outside): of each run of open facets touching across and up, its top row's
    // leftmost.
    private boolean[][] glints() {
        int sides = SHELL_SIDES;
        boolean[][] seen = new boolean[sides][rows], glint = new boolean[sides][rows];
        for (int f0 = 0; f0 < sides; f0++) {
            for (int j0 = 0; j0 < rows; j0++) {
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
                        if (q[1] >= 0 && q[1] < rows && open[q[0]][q[1]] && !seen[q[0]][q[1]]) {
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

    // Rectangles {c0, c1, k0, k1} (ends exclusive) covering the set cells of a grid [column][row], merged across, then
    // up.
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

    // --- The inside ---

    // The core support plate and the upper core plate closing the inside top and bottom (the shell's inner wall is
    // the lining's), and the core between them: each block of the inside a cell of a square lattice as wide as fits
    // in the shell, the core's height spread over CORE_HEIGHT of the inside. Only the faces onto coolant are drawn, merged
    // where they meet in one plane and paint; each control rod column's drive line rises from its top to the upper plate.
    private void inside() {
        double start = Math.PI / SHELL_SIDES;
        float ri = radius - WALL;
        uprightDisc(cx, floorY, cu, ri, SHELL_SIDES, start, true, Paint.PLAIN);
        uprightDisc(cx, ceilY, cu, ri, SHELL_SIDES, start, false, Paint.PLAIN);
        int w = core.width(), h = core.height(), l = core.length();
        if (w <= 0 || h <= 0 || l <= 0) {
            return;
        }
        float pitch = Math.min(CORE_PITCH, 2.0F * (ri - 2.0F) / (float) Math.hypot(w, l));
        float coreTop = floorY + (ceilY - floorY) * CORE_HEIGHT, step = (coreTop - floorY) / h;
        float[][] edges = new float[3][];
        edges[0] = lattice(cx - w * pitch / 2.0F, pitch, w);
        edges[1] = lattice(floorY, step, h);
        edges[2] = lattice(cu - l * pitch / 2.0F, pitch, l);
        int[] size = { w, h, l };
        // Each face direction: along axis, toward + or -. Its slices' cells are (b, c) across the other two axes.
        for (int axis = 0; axis < 3; axis++) {
            int ab = axis == 0 ? 2 : 0, ac = axis == 1 ? 2 : 1;
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int a = 0; a < size[axis]; a++) {
                    if (axis == 1 && sign < 0 && a == 0) {
                        // Standing on the support plate.
                        continue;
                    }
                    char[][] face = new char[size[ab]][size[ac]];
                    for (int b = 0; b < size[ab]; b++) {
                        for (int c = 0; c < size[ac]; c++) {
                            int[] cell = new int[3], next = new int[3];
                            cell[axis] = a;
                            cell[ab] = b;
                            cell[ac] = c;
                            next[axis] = a + sign;
                            next[ab] = b;
                            next[ac] = c;
                            boolean shows = core.solid(cell[0], cell[1], cell[2]) && !core.solid(next[0], next[1], next[2]);
                            face[b][c] = shows ? core.at(cell[0], cell[1], cell[2]) : 0;
                        }
                    }
                    for (int[] m : merged(face)) {
                        float[] min = new float[3], max = new float[3];
                        min[axis] = edges[axis][a];
                        max[axis] = edges[axis][a + 1];
                        min[ab] = edges[ab][m[0]];
                        max[ab] = edges[ab][m[1]];
                        min[ac] = edges[ac][m[2]];
                        max[ac] = edges[ac][m[3]];
                        coreFace(axis, sign, min, max, face[m[0]][m[2]]);
                    }
                }
            }
        }
        // The drive lines, from each control rod column's top to the upper plate.
        for (int x = 0; x < w; x++) {
            for (int u = 0; u < l; u++) {
                int top = -1;
                boolean control = false;
                for (int y = 0; y < h; y++) {
                    if (core.solid(x, y, u)) {
                        top = y;
                    }
                    control |= core.at(x, y, u) == Core.CONTROL;
                }
                float from = edges[1][top + 1];
                if (control && ceilY - from > 1.0F) {
                    uprightTube((edges[0][x] + edges[0][x + 1]) / 2.0F, (edges[2][u] + edges[2][u + 1]) / 2.0F, new float[][] { { from, 1.25F }, { ceilY, 1.25F } },
                            6, Math.PI / 6, Paint.POLISHED, null);
                }
            }
        }
    }

    // The n + 1 edges of n cells size px each from first.
    private static float[] lattice(float first, float size, int n) {
        float[] at = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            at[i] = first + i * size;
        }
        return at;
    }

    // Rectangles {b0, b1, c0, c1} covering the cells of each letter of a slice, merged across, then up (a letter only
    // with its own kind).
    private static List<int[]> merged(char[][] face) {
        List<int[]> out = new ArrayList<>();
        for (char kind : new char[] { Core.FUEL, Core.CONTROL, Core.GRAPHITE, Core.REFLECTOR }) {
            boolean[][] cells = new boolean[face.length][face[0].length];
            boolean any = false;
            for (int b = 0; b < face.length; b++) {
                for (int c = 0; c < face[0].length; c++) {
                    cells[b][c] = face[b][c] == kind;
                    any |= cells[b][c];
                }
            }
            if (any) {
                out.addAll(merged(cells));
            }
        }
        return out;
    }

    // A face of the core's box from min to max, facing sign along axis, painted for what it is: the fuel's channels in
    // the dark water (its tops the assemblies' nozzles), a control rod channel's plain steel, graphite, or a
    // reflector's riveted plate.
    private void coreFace(int axis, int sign, float[] min, float[] max, char kind) {
        float x0 = min[0], y0 = min[1], u0 = min[2], x1 = max[0], y1 = max[1], u1 = max[2];
        float[] origin, right, up, n;
        float w, h;
        if (axis == 1) {
            right = new float[] { 1, 0, 0 };
            w = x1 - x0;
            h = u1 - u0;
            if (sign > 0) {
                origin = new float[] { x0, y1, u1 };
                up = new float[] { 0, 0, -1 };
            } else {
                origin = new float[] { x0, y0, u0 };
                up = new float[] { 0, 0, 1 };
            }
        } else if (axis == 0) {
            up = new float[] { 0, 1, 0 };
            w = u1 - u0;
            h = y1 - y0;
            origin = sign > 0 ? new float[] { x1, y0, u1 } : new float[] { x0, y0, u0 };
            right = sign > 0 ? new float[] { 0, 0, -1 } : new float[] { 0, 0, 1 };
        } else {
            up = new float[] { 0, 1, 0 };
            w = x1 - x0;
            h = y1 - y0;
            origin = sign > 0 ? new float[] { x0, y0, u1 } : new float[] { x1, y0, u0 };
            right = sign > 0 ? new float[] { 1, 0, 0 } : new float[] { -1, 0, 0 };
        }
        n = new float[3];
        n[axis] = sign;
        Paint paint = switch (kind) {
            case Core.FUEL -> axis == 1 && sign > 0 ? Paint.M_FUEL_TOP : Paint.M_CORE_DARK;
            case Core.GRAPHITE -> Paint.M_GRAPHITE;
            case Core.REFLECTOR -> Paint.PLATE;
            default -> Paint.PLAIN;
        };
        face(origin, right, up, w, h, paint, false);
        if (kind == Core.FUEL) {
            fuelFaces.add(new float[][] { origin, right, up, { w, h }, n });
        }
    }

    private static float[] add(float[] a, float[] b) {
        return new float[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
    }
}
