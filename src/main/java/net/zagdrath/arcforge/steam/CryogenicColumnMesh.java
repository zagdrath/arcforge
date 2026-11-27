/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Cryogenic Distillation Column's model (see FusionPlantRenderer): always 3 blocks square and 6 to 16 tall, a cold
// tower in the Distillation Array's manner (RefineryMesh) on the plinth:
//  - the column filling the footprint's middle: a tall plated shell on its skirt, a flange band at each tray section's
//    seam (every block and a half: the insulation cladding's joints), its head, and the condenser on its crown (a
//    narrower drum with its own head, a vent on top);
//  - grating platforms round it every four blocks, each to the footprint's square edge with a kick plate, posts and
//    rails along it (open on the ladder's side), the top one over the head;
//  - a caged ladder up the left side, from the plinth to the top platform;
//  - a sight glass on the front of every section between two platforms, lit while it runs;
//  - the three product draw-offs down the right side (Nitrogen off the top, Argon from the middle, Oxygen off the bottom,
//    as the air recipe gives them), each a nozzle and a vacuum-jacketed line down to the plinth.
public final class CryogenicColumnMesh extends FusionPlantMesh {
    // Platforms every PLATFORM_PITCH px up, the railings' height and the platform plate's thickness.
    static final float PLATFORM_PITCH = 4.0F * PX, RAIL = 11.0F, PLATE = 1.5F;
    // The column's radius in the 48 px footprint.
    static final float RADIUS = 13.0F;
    // The ladder's side (angle round from +x toward +u): the left (-x).
    static final double LADDER_ANGLE = Math.PI;

    public CryogenicColumnMesh(ModelFrame frame, int panelX, int panelY) {
        super(frame, panelX, panelY);
    }

    @Override
    void parts() {
        float r = RADIUS, x = cx, u = cu;
        float top = height - 1.0F;
        // The condenser's drum and head over the column's head, and its vent.
        float rc = 7.0F, ch = Math.min(14.0F, height * 0.06F + 6.0F), hh = 5.0F, hc = 3.5F;
        float colTop = top - 2.0F - ch - hc;
        vessel(x, u, r, PLINTH, colTop, 24.0F, hh, SHELL_SIDES);
        vessel(x, u, rc, colTop - hh * 0.5F, colTop + ch + hc, 0.0F, hc, SHELL_SIDES / 2);
        prismY(x, u, colTop + ch + hc - 0.5F, top, 1.0F, Paint.BANDED, Paint.MID);
        // The platforms (every four blocks; the last one under the head is the top deck).
        float last = PLINTH;
        for (float y = PLINTH + PLATFORM_PITCH; y < colTop - hh - 6.0F; y += PLATFORM_PITCH) {
            platform(x, u, r, y);
            last = y;
        }
        ladder(x, u, r, Math.max(last, PLINTH + 2.0F * PX) + RAIL);
        // A sight glass on the front of each section between two platforms.
        for (float y = PLINTH + PLATFORM_PITCH / 2.0F; y < colTop - hh - 4.0F; y += PLATFORM_PITCH) {
            sightGlass(x, u, r, (float) (-Math.PI / 2 + 0.35), y, 2.0F, 5.0F);
        }
        // The draw-offs down the right side: Nitrogen high, Argon in the middle, Oxygen low.
        float span = colTop - hh - PLINTH;
        float[] heights = { PLINTH + span * 0.86F, PLINTH + span * 0.52F, PLINTH + span * 0.18F };
        double[] angles = { -0.55, 0.0, 0.55 };
        for (int i = 0; i < 3; i++) {
            drawOff(x, u, r, (float) angles[i], heights[i]);
        }
    }

    // How far from the column's axis the footprint's square edge is at angle a (half a pixel in from the edge).
    private float edge(double a) {
        float half = Math.min(cx, cu) - 0.5F;
        double c = Math.abs(Math.cos(a)), s = Math.abs(Math.sin(a));
        return (float) (half / Math.max(c, s));
    }

    // A grating platform round the column at y: plates from the shell out to the square edge (round the ladder's side
    // too: the ladder comes up through a gap in the railing), and the railings along the edge.
    private void platform(float x, float u, float r, float y) {
        int sides = SHELL_SIDES;
        double step = 2.0 * Math.PI / sides;
        for (int f = 0; f < sides; f++) {
            double a0 = f * step, a1 = a0 + step;
            float e0 = edge(a0), e1 = edge(a1);
            float[] i0 = { x + (float) Math.cos(a0) * r, y, u + (float) Math.sin(a0) * r };
            float[] i1 = { x + (float) Math.cos(a1) * r, y, u + (float) Math.sin(a1) * r };
            float[] o0 = { x + (float) Math.cos(a0) * e0, y, u + (float) Math.sin(a0) * e0 };
            float[] o1 = { x + (float) Math.cos(a1) * e1, y, u + (float) Math.sin(a1) * e1 };
            orient(i0, o0, o1, i1, new float[] { 0, 1, 0 }, Paint.GRATE);
            float[] j0 = { i0[0], y - PLATE, i0[2] }, j1 = { i1[0], y - PLATE, i1[2] }, p0 = { o0[0], y - PLATE, o0[2] }, p1 = { o1[0], y - PLATE, o1[2] };
            orient(j0, p0, p1, j1, new float[] { 0, -1, 0 }, Paint.SHADE);
        }
        solidBoxes.add(new float[] { 0.5F, y - PLATE, 0.5F, width - 0.5F, y, length - 0.5F });
        float s0 = 0.5F, sx = width - 0.5F, su = length - 0.5F;
        // Front, back and right full; the left side open over the ladder's middle.
        rail(s0, s0, sx, s0, y);
        rail(s0, su, sx, su, y);
        rail(sx, s0 + 1.0F, sx, su - 1.0F, y);
        rail(s0, s0 + 1.0F, s0, u - 5.0F, y);
        rail(s0, u + 5.0F, s0, su - 1.0F, y);
    }

    // A railing along the footprint's edge from (x0, u0) to (x1, u1) on a platform at y: a kick plate, posts at most 8 px
    // apart, a knee rail and a hand rail (as the refinery tower's).
    private void rail(float x0, float u0, float x1, float u1, float y) {
        boolean alongX = Math.abs(x1 - x0) > Math.abs(u1 - u0);
        float len = alongX ? Math.abs(x1 - x0) : Math.abs(u1 - u0);
        if (len < 2.0F) {
            return;
        }
        float lo = alongX ? Math.min(x0, x1) : Math.min(u0, u1), at = alongX ? u0 : x0;
        float centre = alongX ? cu : cx;
        float inward = at < centre ? 1.0F : -1.0F;
        float t0 = Math.min(at, at + inward), t1 = Math.max(at, at + inward);
        if (alongX) {
            box(lo, y, t0, lo + len, y + 2.0F, t1, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(lo, y + RAIL - 1.0F, t0, lo + len, y + RAIL, t1, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(lo, y + RAIL / 2.0F, t0 + 0.1F, lo + len, y + RAIL / 2.0F + 1.0F, t1 - 0.1F, Paint.FACE, Paint.MID, Paint.SHADE, SKIP_NEG_X | SKIP_POS_X);
        } else {
            box(t0, y, lo, t1, y + 2.0F, lo + len, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(t0, y + RAIL - 1.0F, lo, t1, y + RAIL, lo + len, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(t0 + 0.1F, y + RAIL / 2.0F, lo, t1 - 0.1F, y + RAIL / 2.0F + 1.0F, lo + len, Paint.FACE, Paint.MID, Paint.SHADE, SKIP_NEG_U | SKIP_POS_U);
        }
        int posts = Math.max(1, (int) Math.ceil(len / 8.0F));
        for (int i = 0; i <= posts; i++) {
            float p = lo + Math.min(len - 1.0F, len * i / posts);
            if (alongX) {
                box(p, y + 2.0F, t0, p + 1.0F, y + RAIL - 1.0F, t1, Paint.FACE, null, null, SKIP_UP | SKIP_DOWN);
            } else {
                box(t0, y + 2.0F, p, t1, y + RAIL - 1.0F, p + 1.0F, Paint.FACE, null, null, SKIP_UP | SKIP_DOWN);
            }
        }
    }

    // A product draw-off: a nozzle out of the shell at angle a and height y, a flange on it, and the line down from it
    // to the plinth.
    private void drawOff(float x, float u, float r, float a, float y) {
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        stub(x, u, a, r, y, 3.0F, 3.0F, 1.0F, 2.5F, Paint.MID, Paint.FACE);
        float out = r + 5.0F;
        float[] from = { x + ca * (r + 2.5F), y, u + sa * (r + 2.5F) };
        float[] elbow = { x + ca * out, y, u + sa * out };
        float[] foot = { elbow[0], PLINTH + 0.5F, elbow[2] };
        line(from, elbow, foot);
    }

    // A caged ladder up the column's left side, from the plinth to y1.
    private void ladder(float x, float u, float r, float y1) {
        float lx = x - r - 2.5F;
        float y0 = PLINTH;
        for (float side : new float[] { -1.5F, 1.5F }) {
            box(lx - 0.4F, y0, u + side - 0.4F, lx + 0.4F, y1, u + side + 0.4F, Paint.MID, Paint.LIT, null, SKIP_DOWN);
        }
        for (float y = y0 + 3.0F; y < y1 - 1.0F; y += 4.0F) {
            box(lx - 0.3F, y, u - 1.1F, lx + 0.3F, y + 0.5F, u + 1.1F, Paint.FACE, Paint.LIT, Paint.SHADE, SKIP_NEG_U | SKIP_POS_U);
        }
        // The cage's hoops from a man's height up, every two blocks.
        for (float y = y0 + 40.0F; y < y1 - 2.0F; y += 2.0F * PX) {
            box(lx - 3.0F, y, u - 2.5F, lx - 2.4F, y + 0.6F, u + 2.5F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(lx - 2.4F, y, u - 2.5F, lx, y + 0.6F, u - 1.9F, Paint.MID, Paint.LIT, Paint.SHADE, SKIP_NEG_X | SKIP_POS_X);
            box(lx - 2.4F, y, u + 1.9F, lx, y + 0.6F, u + 2.5F, Paint.MID, Paint.LIT, Paint.SHADE, SKIP_NEG_X | SKIP_POS_X);
        }
        solidBoxes.add(new float[] { lx - 3.0F, y0, u - 2.5F, lx + 0.4F, y1, u + 2.5F });
    }
}
