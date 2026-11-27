/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Cryogenic Plant's model (see FusionPlantRenderer), sized to its box (3 to 7 blocks each way): a walkway of
// grating along the front, then
//  - the cold box at the back, to one side: a tall box in the light generator steel (the insulated casing of a liquefier,
//    plated and riveted), its edges angle posts, girder bands round it and a relief stack on its roof;
//  - the heat exchanger and expander column at the back beside it: a slim plated shell in flanged sections on its skirt,
//    the turbo-expander's cartridge on top (a short drum on its side) piped down into the cold box;
//  - the compressor skid in front of the column: a footing, the compressor's barrel and its motor, the discharge line up
//    and into the cold box;
//  - the Liquid Nitrogen dewar in front of the cold box: a small domed vessel with a sight glass that lights while the plant
//    runs, its fill line from the cold box;
//  - the transfer lines between them vacuum-jacketed, bellows along them.
public final class CryogenicPlantMesh extends FusionPlantMesh {
    public CryogenicPlantMesh(ModelFrame frame, int panelX, int panelY) {
        super(frame, panelX, panelY);
    }

    @Override
    void parts() {
        float top = height - 3.0F;
        // The walkway.
        box(2.0F, PLINTH, 1.0F, width - 2.0F, PLINTH + 1.0F, 5.0F, Paint.MID, Paint.GRATE, null, SKIP_DOWN);
        // The cold box.
        float bx0 = 3.0F, bx1 = bx0 + Math.round((width - 6.0F) * 0.52F);
        float bu0 = Math.max(19.0F, Math.round(length * 0.4F)), bu1 = length - 3.0F;
        float boxTop = Math.min(top, PLINTH + 4.5F * PX);
        coldBox(bx0, bu0, bx1, bu1, PLINTH, boxTop);
        // The column, in the space beside the cold box at the back.
        float zone = width - 3.0F - bx1;
        float r = clamp(Math.min(zone, length) * 0.24F, 4.0F, 8.0F);
        float colX = bx1 + zone / 2.0F + 0.5F, colU = bu1 - r - 2.0F;
        float colTop = top - 7.0F;
        vessel(colX, colU, r, PLINTH, colTop, 16.0F, Math.min(4.0F, r * 0.6F), SHELL_SIDES / 2);
        sightGlass(colX, colU, r, (float) (-Math.PI / 2), PLINTH + 10.0F, 2.0F, 6.0F);
        expander(colX, colU, colTop, bx1, boxTop);
        // The compressor skid in front of the column.
        float skidU0 = 7.0F, skidU1 = colU - r - 3.0F;
        if (skidU1 - skidU0 >= 14.0F) {
            float skidEnd = Math.min(skidU1, skidU0 + 32.0F);
            compressor(bx1 + 2.0F, skidU0, width - 3.0F, skidEnd, bx1, bu0);
            // A big plant's gas buffer vessel behind the skid, where there's room.
            float br = clamp((width - 5.0F - bx1) / 2.0F - 3.0F, 4.0F, 8.0F);
            if (skidU1 - skidEnd >= 2.0F * br + 4.0F) {
                float bu = skidEnd + 2.0F + br, bxc = (bx1 + width - 3.0F) / 2.0F;
                vessel(bxc, bu, br, PLINTH, Math.min(boxTop - 4.0F, PLINTH + 36.0F), 16.0F, Math.min(4.0F, br * 0.6F), 16);
            }
        }
        // The dewar in front of the cold box.
        float dr = clamp((bu0 - 7.0F) / 2.0F - 1.0F, 4.0F, 7.0F);
        float dx = bx0 + dr + 2.0F, du = 6.0F + dr + 1.0F;
        if (du + dr < bu0 - 1.0F) {
            float dTop = Math.min(PLINTH + 24.0F, top - 8.0F);
            vessel(dx, du, dr, PLINTH, dTop, 0.0F, Math.min(3.0F, dr * 0.6F), 12);
            sightGlass(dx, du, dr, (float) (-Math.PI / 2), PLINTH + 9.0F, 2.0F, 7.0F);
            // Its fill line: up from its crown and back into the cold box's front.
            float lineY = dTop + 3.0F;
            line(new float[] { dx, dTop - 0.5F, du }, new float[] { dx, lineY, du }, new float[] { dx, lineY, bu0 + 0.5F });
        }
    }

    // The cold box: the insulated casing (riveted generator-steel plates), angle posts up its edges, girder bands round
    // it every two blocks, and a small relief stack on its roof.
    private void coldBox(float x0, float u0, float x1, float u1, float y0, float y1) {
        box(x0, y0, u0, x1, y1, u1, Paint.GEN_PLAIN, Paint.GEN_PLAIN, null, SKIP_DOWN);
        solidBoxes.add(new float[] { x0, y0, u0, x1, y1, u1 });
        float p = 1.5F;
        float[][] corners = { { x0, u0 }, { x1, u0 }, { x0, u1 }, { x1, u1 } };
        for (float[] c : corners) {
            box(c[0] - p, y0, c[1] - p, c[0] + p, y1 + 0.5F, c[1] + p, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        }
        for (float y = y0 + 2.0F * PX; y < y1 - 6.0F; y += 2.0F * PX) {
            box(x0 - 0.5F, y - 1.0F, u0 - 0.5F, x1 + 0.5F, y + 1.0F, u1 + 0.5F, Paint.STRAP, Paint.MID, Paint.SHADE, 0);
        }
        // The relief stack and its rain cap.
        float sx = x0 + (x1 - x0) * 0.3F, su = u0 + (u1 - u0) * 0.6F;
        prismY(sx, su, y1, y1 + 2.5F, 1.25F, Paint.BANDED, Paint.MID);
        box(sx - 2.0F, y1 + 2.5F, su - 2.0F, sx + 2.0F, y1 + 3.0F, su + 2.0F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
    }

    // The turbo-expander's cartridge on top of the column, lying along x, and the line from it into the cold box (across
    // into its side, or over its roof and down into it when the column stands taller).
    private void expander(float x, float u, float y, float boxX1, float boxTop) {
        float r = 2.5F, cy = y + r + 1.0F;
        // A saddle on the column's crown.
        box(x - 2.0F, y - 1.0F, u - 2.0F, x + 2.0F, cy - r + 0.5F, u + 2.0F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        float x0 = x - 5.0F, x1 = x + 4.0F;
        tube(new float[] { x0, cy, u }, new float[] { x1, cy, u }, r, 10, Paint.BANDED);
        disc(new float[] { x1, cy, u }, new float[] { 1, 0, 0 }, r, 10, Paint.MID, Paint.SHADE);
        // Its flange and the line off its far end, down into the cold box's side.
        tube(new float[] { x0 - 1.0F, cy, u }, new float[] { x0, cy, u }, r + 1.0F, 10, Paint.BANDED);
        disc(new float[] { x0 - 1.0F, cy, u }, new float[] { -1, 0, 0 }, r + 1.0F, 10, Paint.MID, Paint.SHADE);
        if (x0 - 1.0F > boxX1 + 2.0F) {
            if (cy <= boxTop - 3.0F) {
                line(new float[] { x0 - 1.0F, cy, u }, new float[] { boxX1 - 0.5F, cy, u });
            } else {
                // Over the cold box's roof and down into it.
                line(new float[] { x0 - 1.0F, cy, u }, new float[] { boxX1 - 5.0F, cy, u }, new float[] { boxX1 - 5.0F, boxTop - 0.5F, u });
            }
        }
    }

    // The compressor skid in the area x0..x1, u0..u1: its footing, the compressor's barrel along u with its motor
    // behind it, and the discharge line up off the barrel and across into the cold box's side.
    private void compressor(float x0, float u0, float x1, float u1, float boxX1, float boxU0) {
        float motorLen = Math.min(9.0F, (u1 - u0) * 0.4F);
        float b1 = u1 - motorLen - 1.5F, b0 = Math.max(u0 + 1.5F, b1 - 18.0F);
        float y = PLINTH, f0 = Math.max(u0, b0 - 3.0F);
        box(x0, y, f0, x1, y + 2.0F, u1, Paint.FOOTING, Paint.DECK, null, SKIP_DOWN);
        solidBoxes.add(new float[] { x0, y, f0, x1, y + 2.0F, u1 });
        y += 2.0F;
        float r = clamp((x1 - x0) / 2.0F - 3.0F, 3.0F, 5.0F);
        float cx = (x0 + x1) / 2.0F, cy = y + r + 1.0F;
        // The barrel on two saddles, a flange at each end.
        for (float s : new float[] { b0 + 2.0F, b1 - 3.0F }) {
            box(cx - r + 1.0F, y, s, cx + r - 1.0F, cy - r + 1.0F, s + 1.5F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        }
        tube(new float[] { cx, cy, b0 }, new float[] { cx, cy, b1 }, r, 12, Paint.BANDED);
        disc(new float[] { cx, cy, b0 - 1.0F }, new float[] { 0, 0, -1 }, r + 1.0F, 12, Paint.MID, Paint.SHADE);
        tube(new float[] { cx, cy, b0 - 1.0F }, new float[] { cx, cy, b0 }, r + 1.0F, 12, Paint.BANDED);
        solidCylinders.add(new float[] { cx, cy, r, b0, b1 });
        // The motor: a ribbed box behind it, its coupling guard between.
        float m0 = b1 + 1.5F, mh = 2.0F * r;
        box(cx - 1.0F, cy - 1.0F, b1, cx + 1.0F, cy + 1.0F, m0, Paint.HAZARD, Paint.HAZARD, null, 0);
        box(cx - r, y, m0, cx + r, y + mh, u1 - 0.5F, Paint.M_MOTOR, Paint.PLAIN, null, SKIP_DOWN);
        solidBoxes.add(new float[] { cx - r, y, m0, cx + r, y + mh, u1 - 0.5F });
        // The discharge line: up off the barrel, back over the skid, then across into the cold box.
        float lineY = cy + r + 5.0F, lu = Math.min(boxU0 + 6.0F, length - 6.0F);
        float lu0 = (b0 + b1) / 2.0F;
        line(new float[] { cx, cy + r - 0.5F, lu0 }, new float[] { cx, lineY, lu0 }, new float[] { cx, lineY, lu }, new float[] { boxX1 + 0.5F, lineY, lu });
    }
}
