/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

// The formed Steam Turbine Array's model, built from its TurbineLayout (see SteamTurbineArrayRenderer) on the sheet
// block/steam_turbine_array/turbine: the still parts once (and again when its ports change), the rotor every frame at
// its angle. How it's built and painted: ModelMesh, as a large model (docs/TEXTURE_STYLE.md, "Large model texturing"):
// its big faces in a few continuous plates, the casings' shells in riveted plates wrapped round them, and ramps across
// its round parts.
// Each window is glazed facet by facet, a pane on each of the casing's own facets just inside its outer surface, so the
// glass follows the round casing; behind it the casing's inner wall, stage rings between the blade rows, and the
// bladed rotor (discs carrying dense rows of angled blades) kept inside the inner wall.
public final class TurbineMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 256;
    private static final int SIDES_CASING = TurbineLayout.CASING_SIDES;
    // The inspection window takes these two facets on each side (counted from the flange line at angle 0).
    private static final int WINDOW_FIRST = 1, WINDOW_LAST = 2;
    // How far the stage rings stand in from the inner wall (px).
    private static final float RING_DEPTH = 1.5F;
    // Blade pitch against the axis.
    private static final double PITCH = Math.toRadians(35.0);

    private final TurbineLayout layout;

    public TurbineMesh(TurbineLayout layout, boolean alongX) {
        super(alongX, false, layout.widthPx(), layout.lengthPx(), layout.shaftX(), layout.shaftY(), SHEET_W, SHEET_H);
        this.layout = layout;
        this.modules = true;
    }

    // --- The model ---

    // The still parts and the nozzles of these ports: {solid, cutout}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        plinth();
        if (layout.walkway() != null) {
            walkway(layout.walkway());
        }
        for (TurbineLayout.Box pedestal : layout.pedestals()) {
            pedestalAt(pedestal.u0(), pedestal.u1(), pedestal.x1() - pedestal.x0());
        }
        for (TurbineLayout.Casing casing : layout.casings()) {
            casing(casing);
        }
        steamEnd();
        for (TurbineLayout.Box neck : layout.exhaustNecks()) {
            exhaustNeck(neck);
        }
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
        startRotor(angle);
        float sx = layout.shaftX(), sy = layout.shaftY(), r = layout.shaftRadius();
        // The shaft, from the front standard into the generator, and on into the exciter.
        prismU(sx, sy, new float[][] { { 1.5F, r }, { layout.generator().u0() + 3.0F, r } }, PIPE_SIDES, 0.0, Paint.BANDED, Paint.MID, Paint.MID, null);
        TurbineLayout.Span exciter = layout.exciter();
        if (exciter != null) {
            prismU(sx, sy, new float[][] { { layout.generator().u1() - 1.0F, r }, { exciter.u0() + 1.0F, r } }, PIPE_SIDES, 0.0, Paint.BANDED, Paint.MID, Paint.MID,
                    null);
        }
        // A coupling in each gap after the first, between its two bearing pedestals.
        List<TurbineLayout.Span> gaps = layout.gaps();
        for (int i = 1; i < gaps.size(); i++) {
            TurbineLayout.Span gap = gaps.get(i);
            boolean generator = i == gaps.size() - 1;
            float cr = r * (generator ? 2.8F : 2.3F);
            float half = layout.couplingHalf(i);
            float c0 = gap.middle() - half, c1 = gap.middle() + half;
            prismU(sx, sy, new float[][] { { c0, cr }, { c1, cr } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, Paint.MID, Paint.SHADE, null);
            // Bolt heads on the coupling's face toward the pedestal, so its turning shows.
            int bolts = generator ? 6 : 4;
            for (int b = 0; b < bolts; b++) {
                double a = 2.0 * Math.PI * b / bolts;
                float bx = sx + (float) Math.cos(a) * cr * 0.65F, by = sy + (float) Math.sin(a) * cr * 0.65F;
                box(bx - 0.5F, by - 0.5F, c0 - 0.75F, bx + 0.5F, by + 0.5F, c0, Paint.RIVET, Paint.RIVET, Paint.RIVET, 0);
            }
        }
        for (TurbineLayout.Casing casing : layout.casings()) {
            for (TurbineLayout.Span window : casing.windows()) {
                rotorInWindow(casing, window);
            }
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
            for (TurbineLayout.Box column : layout.columns()) {
                column(column.x0(), column.u0(), column.x1() - column.x0(), column.u1() - column.u0(), foot);
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

    // A piece of the deck slab: its edge a girder flange, its top deck plates (as few as keep each within 4 blocks).
    private void deckSlab(float x0, float u0, float x1, float u1) {
        if (x1 - x0 <= 0.01F || u1 - u0 <= 0.01F) {
            return;
        }
        float foot = layout.footingTop(), deck = layout.deckTop();
        box(x0, foot, u0, x1, deck, u1, Paint.STRAP, Paint.DECK, Paint.FOOTING, 0);
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

    // A bearing pedestal (see TurbineLayout.pedestals), with its bearing cap on top.
    private void pedestalAt(float u0, float u1, float pw) {
        float sx = layout.shaftX(), sy = layout.shaftY();
        float top = sy + layout.shaftRadius() + 2.0F;
        box(sx - pw / 2.0F, layout.deckTop(), u0, sx + pw / 2.0F, top, u1, Paint.PLATE, Paint.PLAIN, null, 0);
        float cap = pw * 0.6F;
        box(sx - cap / 2.0F, top, u0 + 0.5F, sx + cap / 2.0F, top + 2.0F, u1 - 0.5F, Paint.PLAIN, Paint.MID, null, 0);
        solidBoxes.add(new float[] { sx - pw / 2.0F, layout.deckTop(), u0, sx + pw / 2.0F, top + 2.0F, u1 });
    }

    // A casing: the round shell split along the middle by a bolted flange, hoops where its ends taper, its end rings,
    // and its inspection windows on both upper sides (glazed facet by facet over its inside; the rotor in them is drawn
    // with the rotor).
    private void casing(TurbineLayout.Casing casing) {
        float sx = layout.shaftX(), sy = layout.shaftY();
        float[][] profile = casing.profile();
        // Cut the full-radius part at each window's ends.
        List<float[]> points = new ArrayList<>(Arrays.asList(profile));
        List<Integer> windowSegments = new ArrayList<>();
        int at = 2;
        for (TurbineLayout.Span window : casing.windows()) {
            points.add(at, new float[] { window.u0(), casing.radius() });
            points.add(at + 1, new float[] { window.u1(), casing.radius() });
            windowSegments.add(at);
            at += 2;
        }
        float[][] cut = points.toArray(new float[0][]);
        prismU(sx, sy, cut, SIDES_CASING, 0.0, Paint.CASING, null, null,
                (segment, facet) -> windowSegments.contains(segment) && isWindowFacet(facet));
        // The end rings round the shaft gland.
        float gland = layout.shaftRadius() + 1.5F;
        ringU(sx, sy, profile[0][0], profile[0][1], gland * 1.8F, SIDES_CASING, 0.0, false, Paint.FACE, Paint.SHADE);
        ringU(sx, sy, profile[3][0], profile[3][1], gland * 1.8F, SIDES_CASING, 0.0, true, Paint.FACE, Paint.SHADE);
        prismU(sx, sy, new float[][] { { profile[0][0] - 1.0F, gland * 1.8F }, { profile[0][0], gland * 1.8F } }, SIDES_CASING, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
        prismU(sx, sy, new float[][] { { profile[3][0], gland * 1.8F }, { profile[3][0] + 1.0F, gland * 1.8F } }, SIDES_CASING, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
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
            prismU(sx, sy, new float[][] { { u - 1.0F, casing.radius() + 0.75F }, { u + 1.0F, casing.radius() + 0.75F } }, SIDES_CASING, 0.0,
                    Paint.BANDED, Paint.MID, Paint.SHADE, null);
        }
        flange(casing);
        for (TurbineLayout.Span window : casing.windows()) {
            windowCavity(casing, window);
        }
        for (int i = 0; i + 1 < profile.length; i++) {
            float r = Math.min(profile[i][1], profile[i + 1][1]) * (float) Math.cos(Math.PI / SIDES_CASING);
            solidCylinders.add(new float[] { sx, sy, r, profile[i][0], profile[i + 1][0] });
        }
    }

    private static boolean isWindowFacet(int facet) {
        int half = SIDES_CASING / 2;
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

    // Behind a window: the casing's inside wall and the window's two end walls, dark; a stage ring between each pair of
    // blade rows (a lip standing in from the lower half of the wall); and the glass, a pane on each of the window's facets
    // just inside the outer surface, each with its own frame and glints.
    private void windowCavity(TurbineLayout.Casing casing, TurbineLayout.Span window) {
        float sx = layout.shaftX(), sy = layout.shaftY();
        float inner = casing.innerRadius();
        double step = 2.0 * Math.PI / SIDES_CASING;
        // The inside wall, facing in.
        for (int facet = 0; facet < SIDES_CASING; facet++) {
            double a0 = facet * step, a1 = (facet + 1) * step;
            float[] p0 = { sx + (float) Math.cos(a0) * inner, sy + (float) Math.sin(a0) * inner, window.u0() };
            float[] p1 = { sx + (float) Math.cos(a1) * inner, sy + (float) Math.sin(a1) * inner, window.u0() };
            float[] p2 = { p1[0], p1[1], window.u1() };
            float[] p3 = { p0[0], p0[1], window.u1() };
            flat(p3, p2, p1, p0, Paint.DARK, false);
        }
        // The end walls, facing into the window.
        ringU(sx, sy, window.u0(), inner, 0.0F, SIDES_CASING, 0.0, true, Paint.DEEP, Paint.DEEP);
        ringU(sx, sy, window.u1(), inner, 0.0F, SIDES_CASING, 0.0, false, Paint.DEEP, Paint.DEEP);
        // The stage rings, on the lower half only: on the upper half they stand between the glass and the blade tips,
        // and at a slant their lips lined up into slats that hid the rotor.
        int rows = rowsIn(casing, window);
        float pitch = window.length() / rows;
        FacetFilter upperHalf = (segment, facet) -> facet < SIDES_CASING / 2;
        for (int i = 1; i < rows; i++) {
            float u = window.u0() + pitch * i;
            float tip = Math.max(casing.tipAt(u - pitch / 2.0F), casing.tipAt(u + pitch / 2.0F));
            // Only a lip at the wall: rings reaching in to the tips hid the blades behind them from any slant.
            float in = Math.max(tip / TurbineLayout.CASING_APOTHEM + 0.5F, inner - RING_DEPTH), out = inner - 0.05F;
            if (out - in < 0.4F) {
                continue;
            }
            ringU(sx, sy, u - 0.25F, out, in, SIDES_CASING, 0.0, false, Paint.MID, Paint.SHADE, upperHalf);
            ringU(sx, sy, u + 0.25F, out, in, SIDES_CASING, 0.0, true, Paint.MID, Paint.SHADE, upperHalf);
        }
        // The glass: one pane per facet, at the glass radius, its texture's top toward the higher corner.
        float g = casing.glassRadius();
        for (int facet = 0; facet < SIDES_CASING; facet++) {
            if (!isWindowFacet(facet)) {
                continue;
            }
            double a0 = facet * step, a1 = (facet + 1) * step;
            boolean rising = Math.sin(a1) > Math.sin(a0);
            double lowA = rising ? a0 : a1, highA = rising ? a1 : a0;
            float[] low = { sx + (float) Math.cos(lowA) * g, sy + (float) Math.sin(lowA) * g };
            float[] high = { sx + (float) Math.cos(highA) * g, sy + (float) Math.sin(highA) * g };
            float[] up = { high[0] - low[0], high[1] - low[1], 0.0F };
            float height = (float) Math.hypot(up[0], up[1]);
            up[0] /= height;
            up[1] /= height;
            // The pane faces out from the shaft: right = up x normal.
            float[] normal = { (float) Math.cos((a0 + a1) / 2.0), (float) Math.sin((a0 + a1) / 2.0), 0.0F };
            float[] right = cross(up, normal);
            float[] origin = right[2] > 0 ? new float[] { low[0], low[1], window.u0() } : new float[] { low[0], low[1], window.u1() };
            face(origin, right, up, window.length(), height, Paint.GLASS, true);
        }
    }

    // How many blade rows a window shows: the casing's rows, shared among its windows by length.
    private static int rowsIn(TurbineLayout.Casing casing, TurbineLayout.Span window) {
        float total = 0.0F;
        for (TurbineLayout.Span each : casing.windows()) {
            total += each.length();
        }
        return Math.max(2, Math.round(casing.bladeRows() * window.length() / Math.max(total, 0.01F)));
    }

    // The bladed rotor seen through a window: the hub drum, and in each row a disc carrying a dense ring of blades, each
    // row's blades a little longer as the pressure drops (to the casing's tipAt), neighbouring rows offset by half a
    // blade.
    private void rotorInWindow(TurbineLayout.Casing casing, TurbineLayout.Span window) {
        float sx = layout.shaftX(), sy = layout.shaftY();
        float hub = casing.hubRadius();
        float disc = Math.max(hub + 0.75F, hub * 1.15F);
        prismU(sx, sy, new float[][] { { window.u0(), hub }, { window.u1(), hub } }, 12, 0.0, Paint.BANDED, Paint.MID, Paint.MID, null);
        int rows = rowsIn(casing, window);
        float pitch = window.length() / rows;
        float chord = Math.clamp(pitch * 0.55F, 0.75F, 2.5F);
        float discHalf = Math.min(0.5F, pitch * 0.15F);
        int blades = casing.blades();
        for (int row = 0; row < rows; row++) {
            float u = window.u0() + pitch * (row + 0.5F);
            prismU(sx, sy, new float[][] { { u - discHalf, disc }, { u + discHalf, disc } }, 12, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
            float tip = casing.tipAt(u);
            if (tip - disc < 1.0F) {
                continue;
            }
            double offset = (row % 2) * Math.PI / blades;
            for (int b = 0; b < blades; b++) {
                double a = 2.0 * Math.PI * b / blades + offset;
                blade(sx, sy, u, (float) a, disc, tip, chord);
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

    // An exhaust neck under a low-pressure casing, down to the deck (or on between the columns, with a flange at its
    // foot): built of 2 px columns, each up to where the casing's underside is lowest over it, so it meets the casing
    // without entering it.
    private void exhaustNeck(TurbineLayout.Box neck) {
        float sx = layout.shaftX(), sy = layout.shaftY();
        TurbineLayout.Casing over = casingAt((neck.u0() + neck.u1()) / 2.0F);
        float r = over != null ? over.radius() : 0.0F;
        int columns = Math.max(1, Math.round((neck.x1() - neck.x0()) / 2.0F));
        float cw = (neck.x1() - neck.x0()) / columns;
        for (int c = 0; c < columns; c++) {
            float x0 = neck.x0() + c * cw, x1 = x0 + cw;
            float nearest = x0 <= sx && x1 >= sx ? 0.0F : Math.min(Math.abs(x0 - sx), Math.abs(x1 - sx));
            float top = over != null ? sy + TurbineLayout.underside(r, nearest) : neck.y1();
            int skip = (c > 0 ? SKIP_NEG_X : 0) | (c < columns - 1 ? SKIP_POS_X : 0);
            box(x0, neck.y0(), neck.u0(), x1, top, neck.u1(), Paint.PLATE, null, Paint.PLAIN, skip);
        }
        float flangeY = layout.hasColumns() ? neck.y0() : layout.deckTop();
        box(neck.x0() - 1.0F, flangeY, neck.u0() - 1.0F, neck.x1() + 1.0F, flangeY + 2.0F, neck.u1() + 1.0F, Paint.PLAIN, Paint.MID, Paint.PLAIN, 0);
        solidBoxes.add(new float[] { neck.x0() - 1.0F, neck.y0(), neck.u0() - 1.0F, neck.x1() + 1.0F, neck.y1(), neck.u1() + 1.0F });
    }

    private TurbineLayout.@Nullable Casing casingAt(float u) {
        for (TurbineLayout.Casing casing : layout.casings()) {
            if (casing.span().contains(u)) {
                return casing;
            }
        }
        return null;
    }

    // The crossover: up out of the intermediate casing, along over the low-pressure casings and down into each, with an
    // elbow at each end of the run, a tee over each drop between, and flanged joints.
    private void crossover(TurbineLayout.Crossover pipe) {
        float sx = layout.shaftX(), r = pipe.diameter() / 2.0F, flange = r + 1.0F;
        float last = pipe.dropU();
        prismY(sx, pipe.riseU(), pipe.baseY(), pipe.runY() - r, r, Paint.BANDED, Paint.MID);
        prismU(sx, pipe.runY(), new float[][] { { pipe.riseU() + r, r }, { last - r, r } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, Paint.MID, Paint.SHADE, null);
        List<Float> joints = new ArrayList<>();
        joints.add(pipe.riseU());
        for (float drop : pipe.dropUs()) {
            joints.add(drop);
        }
        for (float u : joints) {
            boolean rise = u == pipe.riseU();
            float base = rise ? pipe.baseY() : pipe.dropY();
            if (!rise) {
                prismY(sx, u, base, pipe.runY() - r, r, Paint.BANDED, Paint.MID);
            }
            box(sx - r - 0.5F, pipe.runY() - r - 0.5F, u - r - 0.5F, sx + r + 0.5F, pipe.runY() + r + 0.5F, u + r + 0.5F, Paint.PLATE, Paint.PLATE, Paint.PLAIN, 0);
            // Flanged joints: just above the casing, and under the elbow or tee.
            prismY(sx, u, base + 2.0F, base + 3.5F, flange, Paint.MID, Paint.LIT);
            prismY(sx, u, pipe.runY() - r - 2.5F, pipe.runY() - r - 1.0F, flange, Paint.MID, Paint.LIT);
            solidBoxes.add(new float[] { sx - r, base, u - r, sx + r, pipe.runY() + r, u + r });
        }
        solidBoxes.add(new float[] { sx - r, pipe.runY() - r, pipe.riseU(), sx + r, pipe.runY() + r, last });
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
        TurbineLayout.Span exciter = layout.exciter();
        if (exciter != null) {
            // The exciter: a smaller housing on its own sole plate beyond the generator, ribbed like it.
            TurbineLayout.Box housing = Objects.requireNonNull(layout.exciterHousing());
            float eh = housing.x1() - sx, etop = housing.y1();
            box(sx - eh - 1.0F, deck, exciter.u0(), sx + eh + 1.0F, deck + 2.0F, exciter.u1(), Paint.FOOTING, Paint.FOOTING, null, 0);
            box(sx - eh, deck + 2.0F, exciter.u0() + 0.5F, sx + eh, etop, exciter.u1(), Paint.GEN_PLATE, Paint.GEN_PLATE, null, 0);
            for (float u = exciter.u0() + 3.0F; u <= exciter.u1() - 2.0F; u += 4.0F) {
                box(sx - eh - 0.75F, deck + 4.0F, u, sx - eh, etop, u + 1.0F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
                box(sx + eh, deck + 4.0F, u, sx + eh + 0.75F, etop, u + 1.0F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
            }
            solidBoxes.add(new float[] { sx - eh - 1.0F, deck, exciter.u0(), sx + eh + 1.0F, etop, exciter.u1() });
        }
    }
}
