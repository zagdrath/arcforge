/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.RefineryLayout;

// The formed Distillation Array's refinery tower, built from its RefineryLayout (see DistillationArrayRenderer) on the
// sheet block/distillation_array/refinery: the still parts once (and again when its ports or draw-offs change), with
// the reboiler's burner windows in a glow list of their own; the condenser fans every frame at their angle; and each
// sight glass's pool and vapour every frame, in UVs across a fluid's own sprite. How it's built and painted: ModelMesh,
// in plate modules (modules): riveted plates wrapped round the shell, plates and gratings laid at 1:1 on the block grid
// elsewhere, and ramps across its round parts; the game's face shading lights it.
//
// Its fans turn about their own upright axis.
public final class RefineryMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 256;
    private static final float PIPE = 0.9F, PIPE_RING = 1.8F;

    private final RefineryLayout layout;
    // The liquids drawn off, lightest first (each gets a pipe from its tray down to the base).
    private final int drawOffs;
    // Each sight glass's cavity: {x0, y0, front u, x1, y1, back u, section}.
    public final List<float[]> windows = new ArrayList<>();
    // While building a fan: its angle and axis.
    private boolean spinning;
    private double spinCos = 1.0, spinSin = 0.0;
    private float spinX, spinU;

    public RefineryMesh(RefineryLayout layout, ModelFrame frame, int drawOffs) {
        super(frame.alongX(), frame.flip(), layout.size, layout.size, 0.0F, 0.0F, SHEET_W, SHEET_H);
        this.layout = layout;
        this.drawOffs = drawOffs;
        this.modules = true;
    }

    // --- The model ---

    // The still parts and the nozzles of these ports: {solid, cutout, glow}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        windows.clear();
        reboiler();
        shell();
        for (int i = 0; i < layout.sections.size(); i++) {
            sightGlass(i);
            manway(layout.sections.get(i));
        }
        for (float y : layout.platforms) {
            platform(y, false);
        }
        platform(layout.deckTop, true);
        ladder();
        condenser();
        drawOffPipes();
        stack();
        for (Port port : ports) {
            nozzle(port);
        }
        return new float[][] { solid.toArray(), cutout.toArray(), glow.toArray() };
    }

    // --- The reboiler: a plated box over the footprint, cut away for the ladder, with burner windows ---

    private void reboiler() {
        float s = layout.size, n = layout.notch, top = RefineryLayout.REBOILER_TOP;
        box(1.0F, 0.0F, n, s - 1.0F, top, s - 1.0F, Paint.PLATE, Paint.DECK, Paint.FOOTING, 0);
        box(n, 0.0F, 1.0F, s - 1.0F, top, n, Paint.PLATE, Paint.DECK, Paint.FOOTING, SKIP_POS_U);
        solidBoxes.add(new float[] { 1.0F, 0.0F, n, s - 1.0F, top, s - 1.0F });
        solidBoxes.add(new float[] { n, 0.0F, 1.0F, s - 1.0F, top, n });
        // The base deck plate the shell stands on.
        box(0.5F, top, n, s - 0.5F, RefineryLayout.BASE_DECK, s - 0.5F, Paint.FACE_SHADE, Paint.DECK, null, 0);
        box(n, top, 0.5F, s - 0.5F, RefineryLayout.BASE_DECK, n, Paint.FACE_SHADE, Paint.DECK, null, SKIP_POS_U);
        solidBoxes.add(new float[] { 0.5F, top, 0.5F, s - 0.5F, RefineryLayout.BASE_DECK, s - 0.5F });
        // Burner windows: on the front (beside the ladder's corner), the back and both sides.
        int front = layout.width == 2 ? 1 : 2;
        for (int i = 0; i < front; i++) {
            float x = n + (s - 1.0F - n) * (i + 1) / (front + 1);
            window(new float[] { x, 0, 1.0F }, new float[] { 0, 0, -1 });
        }
        window(new float[] { s / 2.0F, 0, s - 1.0F }, new float[] { 0, 0, 1 });
        window(new float[] { 1.0F, 0, (n + s - 1.0F) / 2.0F }, new float[] { -1, 0, 0 });
        window(new float[] { s - 1.0F, 0, s / 2.0F }, new float[] { 1, 0, 0 });
    }

    // A burner window on a reboiler face (its middle at `at`, the face's outward normal): a steel frame standing half a
    // px proud, a dark pane, and the fire's glow in front of it (in the glow list).
    private void window(float[] at, float[] normal) {
        float w = 4.0F, h = 3.0F, y = 6.5F;
        float[] right = plane(normal)[0], up = { 0, 1, 0 };
        float[] proud = { at[0] + normal[0] * 0.5F, y, at[2] + normal[2] * 0.5F };
        float[] min = new float[3], max = new float[3];
        for (int k = 0; k < 3; k += 2) {
            float a = at[k] - right[k] * (w + 1), b = proud[k] + right[k] * (w + 1);
            min[k] = Math.min(Math.min(a, b), Math.min(at[k] + right[k] * (w + 1), proud[k] - right[k] * (w + 1)));
            max[k] = Math.max(Math.max(a, b), Math.max(at[k] + right[k] * (w + 1), proud[k] - right[k] * (w + 1)));
        }
        box(min[0], y - h - 1, min[2], max[0], y + h + 1, max[2], Paint.MID, Paint.LIT, Paint.DARK, 0);
        float[] origin = { proud[0] + normal[0] * 0.05F - right[0] * w, y - h, proud[2] + normal[2] * 0.05F - right[2] * w };
        face(origin, right, up, 2 * w, 2 * h, Paint.DEEP, false);
        glowing = true;
        face(new float[] { origin[0] + normal[0] * 0.05F, y - h, origin[2] + normal[2] * 0.05F }, right, up, 2 * w, 2 * h, Paint.GLOW, false);
        glowing = false;
    }

    // --- The shell ---

    private void shell() {
        float r = layout.radius, x = layout.cx, u = layout.cu;
        double start = 0.0;
        // The base ring, the shell in its sections (each a set of riveted plates, stepped along its length), the
        // flanges between them and the elliptical head.
        uprightTube(x, u, new float[][] { { layout.shellBottom, r + 1.0F }, { layout.shellBottom + 2.0F, r + 1.0F } }, layout.sides, start,
                Paint.BANDED, Paint.MID);
        for (float[] section : layout.sections) {
            uprightTube(x, u, new float[][] { { section[0], r }, { section[1], r } }, layout.sides, start, Paint.CASING, null);
        }
        for (float seam : layout.seams) {
            uprightTube(x, u, new float[][] { { seam - 1.0F, r + 0.8F }, { seam + 1.0F, r + 0.8F } }, layout.sides, start, Paint.BANDED, Paint.MID);
            bolts(seam, r + 0.8F);
        }
        float h = layout.headHeight, top = layout.shellTop;
        float[][] head = new float[5][];
        for (int i = 0; i <= 4; i++) {
            double t = Math.PI / 2.0 * i / 4.0;
            head[i] = new float[] { top + h * (float) Math.sin(t), r * (float) Math.cos(t) };
        }
        uprightTube(x, u, head, layout.sides, start, Paint.BANDED, null);
        uprights.add(new float[] { x, u, r, layout.shellBottom, top + h });
    }

    // Bolt heads round a flange at height y, radius r out.
    private void bolts(float y, float r) {
        int count = layout.sides;
        for (int i = 0; i < count; i++) {
            double a = 2.0 * Math.PI * (i + 0.5) / count;
            float bx = layout.cx + (float) Math.cos(a) * (r + 0.3F), bu = layout.cu + (float) Math.sin(a) * (r + 0.3F);
            box(bx - 0.5F, y - 0.5F, bu - 0.5F, bx + 0.5F, y + 0.5F, bu + 0.5F, Paint.RIVET, Paint.RIVET, Paint.DARK, 0);
        }
    }

    // A section's sight glass: a flat boss standing out of the front of the shell with a 12 x 12 window (one pane in
    // the boss's face, just inside it) onto a shallow cavity: dark walls, the tray's pool and its vapour (drawn each
    // frame).
    private void sightGlass(int index) {
        float[] boss = layout.sightGlass(layout.sections.get(index));
        float x0 = boss[0], y0 = boss[1], u0 = boss[2], x1 = boss[3], y1 = boss[4], u1 = boss[5];
        float cx = (x0 + x1) / 2.0F, cy = (y0 + y1) / 2.0F;
        // The boss's sides, top and bottom; its face is a frame round the opening.
        box(x0, y0, u0, x1, y1, u1, Paint.PLAIN, Paint.MID, Paint.SHADE, SKIP_NEG_U);
        float[] right = { -1, 0, 0 }, up = { 0, 1, 0 };
        face(new float[] { x1, y0, u0 }, right, up, x1 - x0, 1.0F, Paint.FACE_SHADE, false);
        face(new float[] { x1, y1 - 1.0F, u0 }, right, up, x1 - x0, 1.0F, Paint.MID, false);
        face(new float[] { x1, y0 + 1.0F, u0 }, right, up, 1.0F, y1 - y0 - 2.0F, Paint.FACE_SHADE, false);
        face(new float[] { x0 + 1.0F, y0 + 1.0F, u0 }, right, up, 1.0F, y1 - y0 - 2.0F, Paint.MID, false);
        // The cavity, seen through the glass: walls facing in.
        float front = u0 + 0.5F, back = layout.cu - layout.radius - 0.25F;
        float wx0 = cx - 6.0F, wx1 = cx + 6.0F, wy0 = cy - 6.0F, wy1 = cy + 6.0F;
        orient(new float[] { wx0, wy0, back }, new float[] { wx1, wy0, back }, new float[] { wx1, wy1, back }, new float[] { wx0, wy1, back },
                new float[] { 0, 0, -1 }, Paint.DARK);
        orient(new float[] { wx0, wy0, u0 }, new float[] { wx1, wy0, u0 }, new float[] { wx1, wy0, back }, new float[] { wx0, wy0, back },
                new float[] { 0, 1, 0 }, Paint.DARK);
        orient(new float[] { wx0, wy1, u0 }, new float[] { wx1, wy1, u0 }, new float[] { wx1, wy1, back }, new float[] { wx0, wy1, back },
                new float[] { 0, -1, 0 }, Paint.DEEP);
        orient(new float[] { wx0, wy0, u0 }, new float[] { wx0, wy1, u0 }, new float[] { wx0, wy1, back }, new float[] { wx0, wy0, back },
                new float[] { 1, 0, 0 }, Paint.SHADE);
        orient(new float[] { wx1, wy0, u0 }, new float[] { wx1, wy1, u0 }, new float[] { wx1, wy1, back }, new float[] { wx1, wy0, back },
                new float[] { -1, 0, 0 }, Paint.SHADE);
        // The pane, a quarter px inside the boss's face.
        face(new float[] { wx1, wy0, u0 + 0.25F }, right, up, 12.0F, 12.0F, Paint.GLASS, true);
        solidBoxes.add(boss);
        windows.add(new float[] { wx0, wy0, front, wx1, wy1, back, index });
    }

    // A section's manway on the left: a short nozzle with a bolted blind flange and the davit that swings it.
    private void manway(float[] section) {
        float y = layout.fittingsY(section), u = layout.cu, shell = layout.cx - layout.radius;
        prismX(u, y, shell - 3.0F, shell + 1.0F, 4.0F, Paint.BANDED, Paint.MID);
        prismX(u, y, shell - 4.2F, shell - 3.0F, 5.2F, Paint.BANDED, Paint.MID);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * (i + 0.5) / 8.0;
            float by = y + (float) Math.sin(a) * 4.4F, bu = u + (float) Math.cos(a) * 4.4F;
            box(shell - 4.7F, by - 0.5F, bu - 0.5F, shell - 4.2F, by + 0.5F, bu + 0.5F, Paint.RIVET, Paint.RIVET, Paint.DARK, 0);
        }
        float arm = y + 7.0F;
        tube(new float[] { shell - 3.6F, y + 4.6F, u }, new float[] { shell - 3.6F, arm, u }, 0.45F, 4, Paint.BANDED);
        tube(new float[] { shell - 3.6F, arm, u }, new float[] { shell + 0.6F, arm, u }, 0.45F, 4, Paint.BANDED);
        solidBoxes.add(new float[] { shell - 4.2F, y - 5.2F, u - 5.2F, shell + 1.0F, y + 5.2F, u + 5.2F });
    }

    // --- Platforms and the ladder ---

    // A platform (or the top deck) whose deck's top is at y: grating out from the shell to the footprint's edge (on the
    // top deck, plates over the head), a kick plate round its edge and railings, open at the ladder's corner.
    private void platform(float y, boolean top) {
        float r = layout.radius, x = layout.cx, u = layout.cu, d = RefineryLayout.DECK;
        float s = layout.size, n = layout.notch;
        if (top) {
            // The top deck covers the head: grating plates over the footprint, with a hatch at the ladder's corner.
            box(0.5F, y - d, n, s - 0.5F, y, s - 0.5F, Paint.MID, Paint.GRATE, Paint.SHADE, 0);
            box(n, y - d, 0.5F, s - 0.5F, y, n, Paint.MID, Paint.GRATE, Paint.SHADE, SKIP_POS_U);
            railings(y);
            return;
        }
        int sides = layout.sides;
        double step = 2.0 * Math.PI / sides;
        for (int f = 0; f < sides; f++) {
            double a0 = f * step, a1 = a0 + step, mid = (a0 + a1) / 2.0;
            if (nearLadder(mid, step)) {
                continue;
            }
            float e0 = layout.edge(a0), e1 = layout.edge(a1);
            float[] i0 = { x + (float) Math.cos(a0) * r, y, u + (float) Math.sin(a0) * r };
            float[] i1 = { x + (float) Math.cos(a1) * r, y, u + (float) Math.sin(a1) * r };
            float[] o0 = { x + (float) Math.cos(a0) * e0, y, u + (float) Math.sin(a0) * e0 };
            float[] o1 = { x + (float) Math.cos(a1) * e1, y, u + (float) Math.sin(a1) * e1 };
            orient(i0, o0, o1, i1, new float[] { 0, 1, 0 }, Paint.GRATE);
            float[] j0 = { i0[0], y - d, i0[2] }, j1 = { i1[0], y - d, i1[2] }, p0 = { o0[0], y - d, o0[2] }, p1 = { o1[0], y - d, o1[2] };
            orient(j0, p0, p1, j1, new float[] { 0, -1, 0 }, Paint.SHADE);
        }
        railings(y);
    }

    // The kick plate and railings along each edge of a deck at y (the front and left stop short of the ladder's
    // corner). The front and back run the full width; the sides run between them, so no two overlap at a corner.
    private void railings(float y) {
        float s = layout.size, n = layout.notch;
        rail(n, 0.5F, s - 0.5F, 0.5F, y);
        rail(0.5F, s - 0.5F, s - 0.5F, s - 0.5F, y);
        rail(0.5F, n, 0.5F, s - 1.5F, y);
        rail(s - 0.5F, 1.5F, s - 0.5F, s - 1.5F, y);
    }

    private boolean nearLadder(double angle, double step) {
        double diff = Math.abs(Math.atan2(Math.sin(angle - RefineryLayout.LADDER_ANGLE), Math.cos(angle - RefineryLayout.LADDER_ANGLE)));
        return diff < step * 1.01;
    }

    // A railing along the footprint's edge from (x0, u0) to (x1, u1) on a deck at y: a kick plate, posts at most 8 px
    // apart, a knee rail and a hand rail.
    private void rail(float x0, float u0, float x1, float u1, float y) {
        boolean alongX = Math.abs(x1 - x0) > Math.abs(u1 - u0);
        float length = alongX ? Math.abs(x1 - x0) : Math.abs(u1 - u0);
        float lo = alongX ? Math.min(x0, x1) : Math.min(u0, u1), at = alongX ? u0 : x0;
        float inward = at < layout.size / 2.0F ? 1.0F : -1.0F;
        float a0 = at, a1 = at + inward * 1.0F;
        float t0 = Math.min(a0, a1), t1 = Math.max(a0, a1);
        if (alongX) {
            box(lo, y, t0, lo + length, y + 2.0F, t1, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(lo, y + RefineryLayout.RAIL - 1.0F, t0, lo + length, y + RefineryLayout.RAIL, t1, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            // The knee rail a little thinner than the posts it runs through, so its faces sit just inside theirs.
            box(lo, y + RefineryLayout.RAIL / 2.0F, t0 + 0.1F, lo + length, y + RefineryLayout.RAIL / 2.0F + 1.0F, t1 - 0.1F,
                    Paint.FACE, Paint.MID, Paint.SHADE, 0);
        } else {
            box(t0, y, lo, t1, y + 2.0F, lo + length, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(t0, y + RefineryLayout.RAIL - 1.0F, lo, t1, y + RefineryLayout.RAIL, lo + length, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(t0 + 0.1F, y + RefineryLayout.RAIL / 2.0F, lo, t1 - 0.1F, y + RefineryLayout.RAIL / 2.0F + 1.0F, lo + length,
                    Paint.FACE, Paint.MID, Paint.SHADE, 0);
        }
        int posts = Math.max(1, (int) Math.ceil(length / 8.0F));
        for (int i = 0; i <= posts; i++) {
            float p = lo + Math.min(length - 1.0F, length * i / posts);
            if (alongX) {
                box(p, y + 2.0F, t0, p + 1.0F, y + RefineryLayout.RAIL - 1.0F, t1, Paint.FACE, null, null, SKIP_UP | SKIP_DOWN);
            } else {
                box(t0, y + 2.0F, p, t1, y + RefineryLayout.RAIL - 1.0F, p + 1.0F, Paint.FACE, null, null, SKIP_UP | SKIP_DOWN);
            }
        }
    }

    // The caged ladder up the front left corner, as wide as a ladder block: two 1 px stiles 11 px apart and a 1 px rung
    // every 4 px, from the ground to above the top deck, and from a block and a half up, a cage of hoops and bars bowed
    // out round it.
    private void ladder() {
        float[] c = layout.ladder();
        double a = RefineryLayout.LADDER_ANGLE;
        float ox = (float) Math.cos(a), ou = (float) Math.sin(a), tx = -ou, tu = ox, half = RefineryLayout.LADDER_HALF;
        float top = layout.deckTop + RefineryLayout.RAIL;
        // The stiles: square bars, closed at the top. The rungs are thinner and end inside them, so no face of one
        // shares a plane with the other.
        float stile = 0.4F;
        for (int side = -1; side <= 1; side += 2) {
            float rx = c[0] + tx * half * side, ru = c[1] + tu * half * side;
            box(rx - stile, 0.0F, ru - stile, rx + stile, top, ru + stile, Paint.MID, Paint.LIT, null, SKIP_DOWN);
        }
        for (float y = 2.0F; y < top - 1.0F; y += 4.0F) {
            tube(new float[] { c[0] - tx * half, y, c[1] - tu * half }, new float[] { c[0] + tx * half, y, c[1] + tu * half }, 0.3F, 4, Paint.BANDED);
        }
        float cageX = c[0] + ox * 1.0F, cageU = c[1] + ou * 1.0F, cageR = half + 1.0F, cageBottom = 24.0F;
        int segments = 8;
        float[][] hoop = new float[segments + 1][];
        for (int i = 0; i <= segments; i++) {
            double b = Math.atan2(tu, tx) - Math.PI * i / segments;
            hoop[i] = new float[] { cageX + (float) Math.cos(b) * cageR, cageU + (float) Math.sin(b) * cageR };
        }
        // The hoops' ends face along the tangent; flip them so the cage bows outward (away from the shell).
        float[] mid = hoop[segments / 2];
        if ((mid[0] - c[0]) * ox + (mid[1] - c[1]) * ou < 0) {
            for (float[] point : hoop) {
                point[0] = 2 * cageX - point[0];
                point[1] = 2 * cageU - point[1];
            }
        }
        // Each hoop: its segments, a knuckle over each joint (the segments' open ends meet at an angle there), and a
        // bracket from each end back to its stile (the hoop stands a px clear of it).
        float lastHoop = cageBottom;
        for (float y = cageBottom; y <= top - 0.5F; y += 10.0F) {
            for (int i = 0; i < segments; i++) {
                tube(new float[] { hoop[i][0], y, hoop[i][1] }, new float[] { hoop[i + 1][0], y, hoop[i + 1][1] }, 0.5F, 4, Paint.BANDED);
            }
            for (float[] point : hoop) {
                knuckle(point[0], y, point[1]);
            }
            for (int end : new int[] { 0, segments }) {
                float[] point = hoop[end];
                float near = (point[0] - c[0]) * tx + (point[1] - c[1]) * tu < 0 ? -1.0F : 1.0F;
                tube(new float[] { point[0], y, point[1] }, new float[] { c[0] + tx * half * near, y, c[1] + tu * half * near }, 0.3F, 4, Paint.BANDED);
            }
            lastHoop = y;
        }
        // The bars up the cage, from the lowest hoop to the highest, so both ends are in its knuckles.
        for (int i = 1; i < segments; i += 2) {
            tube(new float[] { hoop[i][0], cageBottom, hoop[i][1] }, new float[] { hoop[i][0], lastHoop, hoop[i][1] }, 0.5F, 4, Paint.BANDED);
        }
    }

    // A small block over a joint in the ladder's cage, closing the pipes' ends that meet there.
    private void knuckle(float x, float y, float u) {
        box(x - 0.55F, y - 0.55F, u - 0.55F, x + 0.55F, y + 0.55F, u + 0.55F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
    }

    // --- The top: vapour line, condenser bank and reflux ---

    private void condenser() {
        float[] b = layout.bundle, lh = layout.leftHeader, rh = layout.rightHeader;
        // Legs from the deck to the bundle.
        for (float[] leg : new float[][] { { b[0] + 1.0F, b[2] + 1.0F }, { b[3] - 1.0F, b[2] + 1.0F }, { b[0] + 1.0F, b[5] - 1.0F }, { b[3] - 1.0F, b[5] - 1.0F } }) {
            box(leg[0] - 0.75F, layout.deckTop, leg[1] - 0.75F, leg[0] + 0.75F, b[1], leg[1] + 0.75F, Paint.SHADE, null, null, SKIP_UP | SKIP_DOWN);
        }
        // The tube bundle (finned tubes behind louvres), a grating over it, and the headers at both ends.
        box(b[0], b[1], b[2], b[3], b[4], b[5], Paint.VENT, Paint.GRATE, Paint.DARK, 0);
        box(lh[0], lh[1], lh[2], lh[3], lh[4], lh[5], Paint.PLATE, Paint.MID, Paint.SHADE, 0);
        box(rh[0], rh[1], rh[2], rh[3], rh[4], rh[5], Paint.PLATE, Paint.MID, Paint.SHADE, 0);
        solidBoxes.add(b);
        solidBoxes.add(lh);
        solidBoxes.add(rh);
        solidBoxes.add(new float[] { 0.5F, layout.deckBottom, 0.5F, layout.size - 0.5F, layout.deckTop, layout.size - 0.5F });
        // A fan shroud on top of the bundle for each fan, with its guard bars.
        for (float[] fan : layout.fans) {
            float fy = b[4];
            uprightTube(fan[0], fan[1], new float[][] { { fy, fan[2] }, { fy + 2.5F, fan[2] } }, 12, 0.0, Paint.BANDED, null);
            uprightInner(fan[0], fan[1], fy, fy + 3.0F, fan[2] - 0.3F, 12, Paint.DARK);
            uprightTube(fan[0], fan[1], new float[][] { { fy + 2.5F, fan[2] + 0.5F }, { fy + 3.0F, fan[2] + 0.5F } }, 12, 0.0, Paint.BANDED, null);
            tube(new float[] { fan[0] - fan[2], fy + 3.0F, fan[1] }, new float[] { fan[0] + fan[2], fy + 3.0F, fan[1] }, 0.35F, 4, Paint.MID);
            tube(new float[] { fan[0], fy + 3.0F, fan[1] - fan[2] }, new float[] { fan[0], fy + 3.0F, fan[1] + fan[2] }, 0.35F, 4, Paint.MID);
            disc(new float[] { fan[0], fy + 0.05F, fan[1] }, new float[] { 0, 1, 0 }, fan[2], 12, Paint.DEEP, Paint.DEEP);
        }
        // The vapour line: up out of the head, through the deck, across under the bundle and up into its left header.
        float x = layout.cx, u = layout.cu, line = layout.deckTop + 2.5F, apex = layout.shellTop + layout.headHeight;
        float hx = (lh[0] + lh[3]) / 2.0F;
        tube(new float[] { x, apex - 0.5F, u }, new float[] { x, line, u }, 1.6F, PIPE_SIDES, Paint.BANDED);
        elbow(x, line, u, 1.8F);
        tube(new float[] { x, line, u }, new float[] { hx, line, u }, 1.6F, PIPE_SIDES, Paint.BANDED);
        elbow(hx, line, u, 1.8F);
        tube(new float[] { hx, line, u }, new float[] { hx, lh[1], u }, 1.6F, PIPE_SIDES, Paint.BANDED);
        // The reflux: down from the right header to the top section's side.
        float rx = (rh[0] + rh[3]) / 2.0F, ru = rh[5] - 3.0F, low = layout.shellTop - 4.0F;
        tube(new float[] { rx, rh[1], ru }, new float[] { rx, low, ru }, 1.0F, PIPE_SIDES, Paint.BANDED);
        elbow(rx, low, ru, 1.2F);
        float dx = layout.cx - rx, du = layout.cu - ru, length = (float) Math.sqrt(dx * dx + du * du);
        float into = length - layout.radius + 0.5F;
        tube(new float[] { rx, low, ru }, new float[] { rx + dx / length * into, low, ru + du / length * into }, 1.0F, PIPE_SIDES, Paint.BANDED);
    }

    private void elbow(float x, float y, float u, float h) {
        box(x - h, y - h, u - h, x + h, y + h, u + h, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
    }

    // --- Draw-offs and the stack ---

    // A pipe from each product's tray, round the right side: out of the shell through a flanged nozzle, then down to
    // the base deck.
    private void drawOffPipes() {
        float r = layout.radius, ring = r + PIPE_RING;
        for (int i = 0; i < drawOffs; i++) {
            double a = RefineryLayout.drawOffAngle(i, drawOffs);
            float[] section = layout.sections.get(layout.drawOffSection(i, drawOffs));
            float y = section[0] + 6.0F, cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            float[] in = { layout.cx + cos * (r - 0.5F), y, layout.cu + sin * (r - 0.5F) };
            float[] out = { layout.cx + cos * ring, y, layout.cu + sin * ring };
            tube(in, out, PIPE, PIPE_SIDES, Paint.BANDED);
            tube(new float[] { layout.cx + cos * (r + 0.3F), y, layout.cu + sin * (r + 0.3F) },
                    new float[] { layout.cx + cos * (r + 0.9F), y, layout.cu + sin * (r + 0.9F) }, PIPE + 0.8F, PIPE_SIDES, Paint.MID);
            elbow(out[0], y, out[2], PIPE + 0.2F);
            tube(out, new float[] { out[0], RefineryLayout.BASE_DECK, out[2] }, PIPE, PIPE_SIDES, Paint.BANDED);
        }
    }

    private void stack() {
        float[] s = layout.stack;
        uprightTube(s[0], s[1], new float[][] { { RefineryLayout.REBOILER_TOP, s[2] }, { s[3] - 2.0F, s[2] } }, PIPE_SIDES, Math.PI / PIPE_SIDES,
                Paint.BANDED, null);
        uprightTube(s[0], s[1], new float[][] { { s[3] - 2.0F, s[2] + 0.6F }, { s[3], s[2] + 0.6F } }, PIPE_SIDES, Math.PI / PIPE_SIDES,
                Paint.BANDED, Paint.DEEP);
        uprights.add(new float[] { s[0], s[1], s[2], RefineryLayout.REBOILER_TOP, s[3] });
    }

    // --- Each frame: the fans and the sight glasses' contents ---

    // The condenser fans' blades and hubs at angle degrees.
    public float[] buildFans(float angle) {
        solid = new Floats();
        glowing = false;
        for (float[] fan : layout.fans) {
            fan(fan, angle);
        }
        return solid.toArray();
    }

    // Each fan built once at rest (see DistillationArrayRenderer), and the upright axis through its hub it spins
    // about: turned by the fans' angle about it, it's the fan buildFans builds.
    public float[][] buildFanParts() {
        float[][] parts = new float[layout.fans.size()][];
        for (int i = 0; i < parts.length; i++) {
            solid = new Floats();
            glowing = false;
            fan(layout.fans.get(i), 0.0F);
            parts[i] = solid.toArray();
        }
        return parts;
    }

    public float[][] fanAxes() {
        float y = layout.bundle[4] + 1.6F;
        float[][] axes = new float[layout.fans.size()][];
        for (int i = 0; i < axes.length; i++) {
            float[] fan = layout.fans.get(i);
            // The spin turns x toward u: about the downward axis, right-handed.
            axes[i] = axis(fan[0], y, fan[1], 0.0F, -1.0F, 0.0F);
        }
        return axes;
    }

    // One fan over the cooler bundle, turned to angle degrees.
    private void fan(float[] fan, float angle) {
        float y = layout.bundle[4] + 1.6F;
        spinning = true;
        spinCos = Math.cos(Math.toRadians(angle));
        spinSin = Math.sin(Math.toRadians(angle));
        spinX = fan[0];
        spinU = fan[1];
        float reach = fan[2] - 0.6F;
        for (int blade = 0; blade < 4; blade++) {
            double a = Math.PI / 2.0 * blade;
            float c = (float) Math.cos(a), s = (float) Math.sin(a), w = 1.2F;
            float[] p0 = { fan[0] + c * 1.0F - s * w, y, fan[1] + s * 1.0F + c * w };
            float[] p1 = { fan[0] + c * reach - s * w, y, fan[1] + s * reach + c * w };
            float[] p2 = { fan[0] + c * reach + s * w, y, fan[1] + s * reach - c * w };
            float[] p3 = { fan[0] + c * 1.0F + s * w, y, fan[1] + s * 1.0F - c * w };
            orient(p0, p1, p2, p3, new float[] { 0, 1, 0 }, Paint.BLADE);
            orient(p0, p1, p2, p3, new float[] { 0, -1, 0 }, Paint.BLADE_BACK);
        }
        disc(new float[] { fan[0], y + 0.4F, fan[1] }, new float[] { 0, 1, 0 }, 1.4F, 8, Paint.MID, Paint.SHADE);
        spinning = false;
    }

    // A sight glass's pool, fill (0-1) of its cavity's lower half, in UVs across a fluid sprite (0-1): its surface and
    // the face behind the glass.
    public float[] buildPool(float[] window, float fill) {
        solid = new Floats();
        glowing = false;
        float x0 = window[0], y0 = window[1], front = window[2] + 0.1F, x1 = window[3], back = window[5];
        float top = y0 + 0.5F + 5.5F * Math.max(0.0F, Math.min(1.0F, fill));
        float v = (top - y0) / 16.0F;
        quad(new float[] { x1, y0, front }, new float[] { x0, y0, front }, new float[] { x0, top, front }, new float[] { x1, top, front },
                new float[] { 0, v, 0.75F, v, 0.75F, 0, 0, 0 }, false);
        float depth = (back - front) / 16.0F;
        quad(new float[] { x1, top, front }, new float[] { x0, top, front }, new float[] { x0, top, back }, new float[] { x1, top, back },
                new float[] { 0, 0, 0.75F, 0, 0.75F, depth, 0, depth }, false);
        return solid.toArray();
    }

    // Vapour rising above a sight glass's pool (fill as in buildPool), in UVs across the vapour sprite: a sheet across
    // the cavity's middle.
    public float[] buildVapour(float[] window, float fill) {
        solid = new Floats();
        glowing = false;
        float x0 = window[0], y0 = window[1], x1 = window[3], y1 = window[4];
        float u = (window[2] + window[5]) / 2.0F;
        float bottom = y0 + 0.5F + 5.5F * Math.max(0.0F, Math.min(1.0F, fill));
        if (y1 - bottom < 0.5F) {
            return new float[0];
        }
        quad(new float[] { x1, bottom, u }, new float[] { x0, bottom, u }, new float[] { x0, y1, u }, new float[] { x1, y1, u },
                new float[] { 0, 1, 0.75F, 1, 0.75F, 1 - (y1 - bottom) / 16.0F, 0, 1 - (y1 - bottom) / 16.0F }, false);
        return solid.toArray();
    }

    // The fans turn about their own upright axis while they're built.
    @Override
    float[] turn(float[] p) {
        if (!spinning) {
            return p;
        }
        float dx = p[0] - spinX, du = p[2] - spinU;
        return new float[] { spinX + (float) (dx * spinCos - du * spinSin), p[1], spinU + (float) (dx * spinSin + du * spinCos) };
    }
}
