/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Cryogenic Distillation Column's model (see FusionPlantRenderer): always 3 blocks square and 6 to 16 tall, a cold
// tower in the Distillation Array's manner (RefineryMesh) on the plinth:
//  - the column filling the footprint's middle: a tall plated shell on its skirt, a flange band at each tray section's
//    seam (every block and a half: the insulation cladding's joints), its head, and the condenser on its crown (a
//    narrower drum with its own head, a vent on top);
//  - grating platforms round it every four blocks, decks over the footprint (a well where the ladder comes up
//    through) with a kick plate, posts and rails along the edge (open over the well), and a top deck on the head round
//    the condenser;
//  - a ladder a block wide up the middle of the left side, from the plinth to the top deck. Formed, the casing's blocks
//    take the shape of these parts (see solids), so a player can step into the well, climb the ladder (the blocks it
//    runs through are climbable, see isLadderBlock) and walk the top deck;
//  - a sight glass on the front of every section between two platforms, lit while it runs;
//  - the three product draw-offs down the right side (Nitrogen off the top, Argon from the middle, Oxygen off the bottom,
//    as the air recipe gives them), each a nozzle and a vacuum-jacketed line down to the plinth.
public final class CryogenicColumnMesh extends FusionPlantMesh {
    // Platforms every PLATFORM_PITCH px up, the railings' height and the platform plate's thickness.
    static final float PLATFORM_PITCH = 4.0F * PX, RAIL = 11.0F, PLATE = 1.5F;
    // The column's radius in the 48 px footprint: 3 px in from the middle of each side, where the ladder stands.
    public static final float RADIUS = 18.0F;
    // The ladder: up the left side (x = 0) of the middle block along u, its stiles LADDER_HALF px either side of the
    // middle and LADDER_X px out from the footprint's edge. The well it climbs through (the platforms' gap, and the
    // part of its blocks a player can stand in) is the footprint's edge to WELL px in, across the middle block.
    public static final float LADDER_HALF = 7.0F, LADDER_X = 3.5F, WELL = 24.0F - RADIUS;

    public CryogenicColumnMesh(ModelFrame frame, int panelX, int panelY) {
        super(frame, panelX, panelY);
    }

    @Override
    void parts() {
        float r = RADIUS, x = cx, u = cu;
        float top = height - 1.0F;
        // The condenser's drum and head over the column's head, and its vent.
        float rc = 10.0F, ch = condenser(height), hh = HEAD, hc = 3.5F;
        float colTop = columnTop(height);
        vessel(x, u, r, PLINTH, colTop, 24.0F, hh, SHELL_SIDES);
        vessel(x, u, rc, colTop - hh * 0.5F, colTop + ch + hc, 0.0F, hc, SHELL_SIDES / 2);
        prismY(x, u, colTop + ch + hc - 0.5F, top, 1.0F, Paint.BANDED, Paint.MID);
        // The platforms (every four blocks), the top deck on the head, and the ladder up to it.
        for (float y : platforms(height)) {
            platform(x, u, r, y);
        }
        platform(x, u, r, topDeck(height));
        ladder(u, ladderTop(height));
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

    // A grating platform round the column at y: a deck PLATE thick over the footprint (half a pixel in from its edge;
    // the column stands through it), with a well over the ladder that it comes up through, and the railings along the
    // edge, open over the well.
    private void platform(float x, float u, float r, float y) {
        float s0 = 0.5F, sx = width - 0.5F, su = length - 0.5F;
        float w0 = u - LADDER_HALF - 1.0F, w1 = u + LADDER_HALF + 1.0F, wx = x - r + 1.0F;
        // In front of the well, behind it, and beside it between the well and the shell (the decks meet along the
        // well's ends, so those faces are left out).
        box(s0, y - PLATE, s0, sx, y, w0, Paint.MID, Paint.GRATE, Paint.SHADE, 0);
        box(s0, y - PLATE, w1, sx, y, su, Paint.MID, Paint.GRATE, Paint.SHADE, 0);
        box(wx, y - PLATE, w0, sx, y, w1, Paint.MID, Paint.GRATE, Paint.SHADE, SKIP_NEG_U | SKIP_POS_U | SKIP_POS_X);
        solidBoxes.add(new float[] { s0, y - PLATE, s0, sx, y, w0 });
        solidBoxes.add(new float[] { s0, y - PLATE, w1, sx, y, su });
        solidBoxes.add(new float[] { wx, y - PLATE, w0, sx, y, w1 });
        // Front, back and right full; the left side open over the well.
        rail(s0, s0, sx, s0, y);
        rail(s0, su, sx, su, y);
        rail(sx, s0 + 1.0F, sx, su - 1.0F, y);
        rail(s0, s0 + 1.0F, s0, w0, y);
        rail(s0, w1, s0, su - 1.0F, y);
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
        // Out far enough to clear the nozzle's flange, its bellows still inside the footprint.
        float out = r + 3.5F;
        float[] from = { x + ca * (r + 2.5F), y, u + sa * (r + 2.5F) };
        float[] elbow = { x + ca * out, y, u + sa * out };
        float[] foot = { elbow[0], PLINTH + 0.5F, elbow[2] };
        line(from, elbow, foot);
    }

    // The ladder up the column's left side, from the plinth to y1: two stiles a block apart (LADDER_HALF either side of
    // u) LADDER_X px in from the footprint's edge, a rung every 4 px between them, and a stand-off bracket back to the
    // shell every two blocks. Not a solid: a player climbs in front of it.
    private void ladder(float u, float y1) {
        float lx = LADDER_X, y0 = PLINTH;
        for (float side : new float[] { -LADDER_HALF, LADDER_HALF }) {
            box(lx - 0.5F, y0, u + side - 0.5F, lx + 0.5F, y1, u + side + 0.5F, Paint.MID, Paint.LIT, null, SKIP_DOWN);
            // The brackets: from the stile's back to the shell (into it a little, so no gap shows).
            float back = cx - (float) Math.sqrt(RADIUS * RADIUS - side * side) + 0.5F;
            for (float y = y0 + 8.0F; y < y1 - 4.0F; y += 2.0F * PX) {
                box(lx + 0.5F, y, u + side - 0.4F, back, y + 1.0F, u + side + 0.4F, Paint.FACE, Paint.LIT, Paint.SHADE, SKIP_NEG_X | SKIP_POS_X);
            }
        }
        for (float y = y0 + 3.0F; y < y1 - 1.0F; y += 4.0F) {
            box(lx - 0.35F, y, u - LADDER_HALF + 0.5F, lx + 0.35F, y + 0.7F, u + LADDER_HALF - 0.5F, Paint.FACE, Paint.LIT, Paint.SHADE,
                    SKIP_NEG_U | SKIP_POS_U);
        }
    }

    // The column's head height, and the condenser drum's height for a column h px tall.
    static final float HEAD = 5.0F;

    static float condenser(float h) {
        return Math.min(14.0F, h * 0.06F + 6.0F);
    }

    // Where the column's shell ends and its head begins, for a column h px tall (under the condenser and its vent).
    static float columnTop(float h) {
        return h - 1.0F - 2.0F - condenser(h) - 3.5F;
    }

    // The platforms' heights (px up the frame) under the top deck for a column h px tall: every PLATFORM_PITCH px up to
    // 6 px under the head.
    public static List<Float> platforms(float h) {
        float limit = columnTop(h) - HEAD - 6.0F;
        List<Float> out = new ArrayList<>();
        for (float y = PLINTH + PLATFORM_PITCH; y < limit; y += PLATFORM_PITCH) {
            out.add(y);
        }
        return out;
    }

    // The top deck's height (px up the frame) for a column h px tall: over the head's crown, the condenser standing
    // through it.
    public static float topDeck(float h) {
        return columnTop(h) + 0.25F;
    }

    // The top of the ladder (px up the frame) for a column h px tall: the top deck's hand rail.
    public static float ladderTop(float h) {
        return topDeck(h) + RAIL;
    }

    // Whether the frame block (bx, by, bu) of a column h px tall is one the ladder runs through (so it can be
    // climbed): the middle block of the left side, from the plinth to the top deck's hand rail.
    public static boolean isLadderBlock(int bx, int by, int bu, float h) {
        return bx == 0 && bu == 1 && by * PX < ladderTop(h);
    }

    // What a player stands on and bumps into on a formed column h px tall, as boxes {x0, y0, u0, x1, y1, u1} px in the
    // frame (see ModelFrame.shapes): the plinth, the column's shell and head (each round part as a stack of boxes inside
    // its circle), the condenser, the platform and top decks with their railings (open over the ladder's well), and
    // the well itself kept clear: the shell's box nearest the ladder stops WELL px in from the footprint's edge.
    public static List<float[]> solids(float h) {
        List<float[]> out = new ArrayList<>();
        float c = 24.0F, r = RADIUS, colTop = columnTop(h), deck = topDeck(h);
        out.add(new float[] { 0.0F, 0.0F, 0.0F, 2.0F * c, PLINTH, 2.0F * c });
        round(out, c, r, PLINTH, colTop - HEAD);
        round(out, c, r - 2.0F, colTop - HEAD, deck - PLATE);
        round(out, c, 10.0F, deck, h - 3.0F);
        for (float y : platforms(h)) {
            deckSolids(out, c, y);
        }
        deckSolids(out, c, deck);
        return out;
    }

    // A round part about (c, c) radius r from y0 to y1 as boxes inside its circle.
    private static void round(List<float[]> out, float c, float r, float y0, float y1) {
        float[][] halves = { { 0.97F, 0.26F }, { 0.87F, 0.5F }, { 0.71F, 0.71F }, { 0.5F, 0.87F }, { 0.26F, 0.97F } };
        for (float[] k : halves) {
            float a = r * k[0], b = r * k[1];
            out.add(new float[] { c - a, y0, c - b, c + a, y1, c + b });
        }
    }

    // A platform's deck at y (its well over the ladder left open) and its railings, as platform() lays them.
    private static void deckSolids(List<float[]> out, float c, float y) {
        float s0 = 0.5F, s1 = 2.0F * c - 0.5F;
        float w0 = c - LADDER_HALF - 1.0F, w1 = c + LADDER_HALF + 1.0F, wx = c - RADIUS + 1.0F;
        out.add(new float[] { s0, y - PLATE, s0, s1, y, w0 });
        out.add(new float[] { s0, y - PLATE, w1, s1, y, s1 });
        out.add(new float[] { wx, y - PLATE, w0, s1, y, w1 });
        out.add(new float[] { s0, y, s0, s1, y + RAIL, s0 + 1.0F });
        out.add(new float[] { s0, y, s1 - 1.0F, s1, y + RAIL, s1 });
        out.add(new float[] { s1 - 1.0F, y, s0, s1, y + RAIL, s1 });
        out.add(new float[] { s0, y, s0, s0 + 1.0F, y + RAIL, w0 });
        out.add(new float[] { s0, y, w1, s0 + 1.0F, y + RAIL, s1 });
    }
}
