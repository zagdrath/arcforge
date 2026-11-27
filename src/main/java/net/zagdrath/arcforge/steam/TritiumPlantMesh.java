/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Tritium Plant's model (see FusionPlantRenderer), sized to its box (3 to 7 blocks across, 3 to 5 tall): a walkway of
// grating along the front, then
//  - the glovebox line along the front: stainless boxes (the light generator steel) on stands, each with a window into
//    it (a lamp inside lit while the plant runs, a light tint on the glass) and two glove ports under the window, the
//    process header along their tops with a drop into each;
//  - behind them, the palladium permeator: a drum lying across on saddles, flanged at both ends;
//  - the hydride storage beds in rows along the back (as many as leave the permeator room), small upright vessels, their header across their tops;
//  - the getter beds, two small vessels, in front of the column;
//  - the isotope separation column at the back corner, a slim plated shell in flanged sections with a sight glass;
//  - a low shield wall of lead-grey footing blocks along the back between the beds and the wall.
public final class TritiumPlantMesh extends FusionPlantMesh {
    static final float MIN_BOX_W = 12.0F;
    static final float BOX_W = 20.0F, BOX_D = 13.0F, GAP = 2.0F, STAND = 8.0F, BOX_H = 18.0F;
    static final float GB_U0 = 7.0F;

    public TritiumPlantMesh(ModelFrame frame, int panelX, int panelY) {
        super(frame, panelX, panelY);
    }

    @Override
    void parts() {
        box(2.0F, PLINTH, 1.0F, width - 2.0F, PLINTH + 1.0F, 5.0F, Paint.MID, Paint.GRATE, null, SKIP_DOWN);
        float boxTop = gloveboxes();
        float back0 = GB_U0 + BOX_D + 3.0F, back1 = length - 3.0F;
        // The column in a back corner.
        float r = clamp(Math.min(width, length) * 0.08F, 3.5F, 6.0F);
        float colX = width - 3.0F - r - 2.0F, colU = back1 - r - 2.0F;
        vessel(colX, colU, r, PLINTH, height - 4.0F, 12.0F, Math.min(3.0F, r * 0.6F), 12);
        sightGlass(colX, colU, r, (float) (-Math.PI / 2), PLINTH + 8.0F, 2.0F, 5.0F);
        // The shield wall and the storage beds along the back, beside the column.
        float bedsX1 = colX - r - 4.0F;
        box(3.0F, PLINTH, back1 - 2.0F, bedsX1 + 2.0F, PLINTH + 10.0F, back1, Paint.FOOTING, Paint.PLAIN, null, SKIP_DOWN);
        solidBoxes.add(new float[] { 3.0F, PLINTH, back1 - 2.0F, bedsX1 + 2.0F, PLINTH + 10.0F, back1 });
        float bedR = 2.5F, bedU = back1 - 2.0F - bedR - 1.5F, bedTop = PLINTH + Math.min(16.0F, height - 20.0F);
        // Rows of beds from the back toward the middle, as many as leave the permeator its room in front of them.
        int rows = Math.max(1, Math.min(3, (int) Math.floor((bedU - back0 - 12.0F) / (2.0F * bedR + 6.0F))));
        for (int row = 0; row < rows; row++, bedU -= 2.0F * bedR + 6.0F) {
            float firstBed = -1.0F, lastBed = -1.0F;
            for (float bx = 3.0F + bedR + 2.0F; bx + bedR < bedsX1; bx += 2.0F * bedR + 3.0F) {
                vessel(bx, bedU, bedR, PLINTH, bedTop, 0.0F, 1.5F, 8);
                prismY(bx, bedU, bedTop - 0.5F, bedTop + 2.5F, 0.6F, Paint.BANDED, Paint.MID);
                if (firstBed < 0.0F) {
                    firstBed = bx;
                }
                lastBed = bx;
            }
            if (lastBed > firstBed) {
                tube(new float[] { firstBed - 1.0F, bedTop + 2.5F, bedU }, new float[] { colX - r + 0.5F, bedTop + 2.5F, bedU }, 0.9F, 8, Paint.BANDED);
            }
        }
        bedU += 2.0F * bedR + 6.0F;
        // The permeator, across behind the gloveboxes.
        float pr = 3.0F, pu = back0 + pr + 1.0F;
        if (pu + pr < bedU - bedR - 1.0F) {
            float px0 = 6.0F, px1 = Math.min(px0 + width * 0.45F, colX - r - 8.0F);
            float py = PLINTH + pr + 3.0F;
            if (px1 - px0 > 10.0F) {
                for (float s : new float[] { px0 + 2.0F, px1 - 3.5F }) {
                    box(s, PLINTH, pu - pr + 0.5F, s + 1.5F, py - pr + 1.0F, pu + pr - 0.5F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
                }
                tube(new float[] { px0, py, pu }, new float[] { px1, py, pu }, pr, 10, Paint.POLISHED);
                for (float[] end : new float[][] { { px0, -1 }, { px1, 1 } }) {
                    tube(new float[] { end[0], py, pu }, new float[] { end[0] + end[1], py, pu }, pr + 1.0F, 10, Paint.BANDED);
                    disc(new float[] { end[0] + end[1], py, pu }, new float[] { end[1], 0, 0 }, pr + 1.0F, 10, Paint.MID, Paint.SHADE);
                }
                solidCylinders.add(new float[] { px0, py, pr, pu - pr, pu + pr });
                // Its line up and across to the column.
                line(new float[] { px1 + 1.0F, py, pu }, new float[] { colX - r - 3.0F, py, pu }, new float[] { colX - r - 3.0F, py, colU },
                        new float[] { colX - r + 0.5F, py, colU });
            }
        }
        // The getter beds in front of the column.
        float gu = colU - r - 4.5F;
        if (gu - 2.0F > back0) {
            for (float gx : new float[] { colX - 3.0F, colX + 3.0F }) {
                vessel(gx, gu, 2.0F, PLINTH, PLINTH + 11.0F, 0.0F, 1.2F, 8);
            }
        }
        // The process header over the gloveboxes, back to the column.
        if (boxTop > 0.0F) {
            float hy = boxTop + 4.0F, hu = GB_U0 + BOX_D - 3.0F;
            if (hy < height - 3.0F) {
                line(new float[] { 4.0F, hy, hu }, new float[] { colX, hy, hu }, new float[] { colX, hy, colU - r + 0.5F });
            }
        }
    }

    // The glovebox line along the front, leaving the controller's panel clear where it would stand in front of them:
    // in each run of free width, as many full-width boxes as fit, centred, or one narrower box when none does; the
    // boxes' top, or -1 when none fits.
    private float gloveboxes() {
        float y0 = PLINTH + STAND, y1 = y0 + BOX_H, u0 = GB_U0, u1 = GB_U0 + BOX_D;
        float[] cabinet = panelBox(panelX, panelY);
        List<float[]> runs = new ArrayList<>();
        if (cabinet != null && cabinet[1] < y1 && cabinet[4] > y0) {
            runs.add(new float[] { 3.0F, cabinet[0] - 1.5F });
            runs.add(new float[] { cabinet[3] + 1.5F, width - 3.0F });
        } else {
            runs.add(new float[] { 3.0F, width - 3.0F });
        }
        boolean any = false;
        for (float[] run : runs) {
            float free = run[1] - run[0];
            int count = (int) Math.floor((free + GAP) / (BOX_W + GAP));
            float w = BOX_W;
            if (count == 0 && free >= MIN_BOX_W) {
                count = 1;
                w = free;
            }
            float total = count * w + (count - 1) * GAP;
            float x = run[0] + (free - total) / 2.0F;
            for (int i = 0; i < count; i++, x += w + GAP) {
                glovebox(x, x + w, y0, y1, u0, u1);
                any = true;
            }
        }
        return any ? y1 : -1.0F;
    }

    private void glovebox(float x0, float x1, float y0, float y1, float u0, float u1) {
        // The stand: four legs and a frame under the box.
        for (float[] leg : new float[][] { { x0 + 1.0F, u0 + 1.0F }, { x1 - 2.5F, u0 + 1.0F }, { x0 + 1.0F, u1 - 2.5F }, { x1 - 2.5F, u1 - 2.5F } }) {
            box(leg[0], PLINTH, leg[1], leg[0] + 1.5F, y0, leg[1] + 1.5F, Paint.PLAIN, null, null, SKIP_DOWN | SKIP_UP);
        }
        box(x0 + 0.5F, y0 - 1.5F, u0 + 0.5F, x1 - 0.5F, y0, u1 - 0.5F, Paint.MID, null, Paint.SHADE, SKIP_UP);
        // The box: its sides, top and back, its front built round the window.
        box(x0, y0, u0, x1, y1, u1, Paint.GEN_PLATE, Paint.GEN_PLAIN, Paint.GEN_DARK, SKIP_NEG_U);
        solidBoxes.add(new float[] { x0, y0, u0, x1, y1, u1 });
        float wx0 = x0 + 2.0F, wx1 = x1 - 2.0F, wy0 = y0 + 6.0F, wy1 = y1 - 2.0F;
        front(x0, x1, y0, wy0, u0);
        front(x0, x1, wy1, y1, u0);
        front(x0, wx0, wy0, wy1, u0);
        front(wx1, x1, wy0, wy1, u0);
        window(new float[] { (wx0 + wx1) / 2.0F, (wy0 + wy1) / 2.0F, u0 }, new float[] { 0, 0, -1 }, wx1 - wx0, wy1 - wy0, Math.min(8.0F, u1 - u0 - 3.0F));
        // The glove ports: a ring proud of the front, its dark glove inside.
        for (float gx : new float[] { x0 + (x1 - x0) * 0.3F, x0 + (x1 - x0) * 0.7F }) {
            float gy = y0 + 3.0F;
            tube(new float[] { gx, gy, u0 }, new float[] { gx, gy, u0 - 1.0F }, 2.0F, 10, Paint.BANDED);
            disc(new float[] { gx, gy, u0 - 1.0F }, new float[] { 0, 0, -1 }, 2.0F, 10, Paint.MID, Paint.SHADE);
            disc(new float[] { gx, gy, u0 - 1.05F }, new float[] { 0, 0, -1 }, 1.3F, 10, Paint.DEEP, Paint.DEEP);
        }
        // A drop from the header into its top.
        prismY((x0 + x1) / 2.0F, u1 - 3.0F, y1, y1 + 4.0F, 0.9F, Paint.BANDED, Paint.MID);
    }

    // A piece of a glovebox's front (facing -u at u), x0..x1 across, y0..y1 up.
    private void front(float x0, float x1, float y0, float y1, float u) {
        face(new float[] { x1, y0, u }, new float[] { -1, 0, 0 }, new float[] { 0, 1, 0 }, x1 - x0, y1 - y0, Paint.GEN_PLAIN, false);
    }
}
