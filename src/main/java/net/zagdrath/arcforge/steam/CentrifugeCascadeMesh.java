/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;
import java.util.Map;

import net.zagdrath.arcforge.multiblock.CentrifugeCascadeLayout;
import net.zagdrath.arcforge.multiblock.CentrifugeCascadeLayout.Cell;
import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Gas Centrifuge Cascade's model, from CentrifugeCascadeLayout (see CentrifugeCascadeRenderer), on the refinery
// tower's sheet: the still parts built once (and again when its ports or centrifuges change), the bands' spinning marks
// each frame in the glow list. A large model (docs/TEXTURE_STYLE.md, "Large model texturing"): the deck plated, the
// perforated tiles in the seamless grating, the casings carrying the ramp across them, the panel's screen lit.
public final class CentrifugeCascadeMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 256;
    private static final int CASING_SIDES = 12;
    // The spinning mark's width round the band (px) and how far out of it it stands.
    private static final float MARK_W = 1.6F, MARK_OUT = 0.08F;

    private final CentrifugeCascadeLayout layout;

    public CentrifugeCascadeMesh(ModelFrame frame, CentrifugeCascadeLayout layout) {
        super(frame.alongX(), frame.flip(), layout.widthPx(), layout.lengthPx(), 0.0F, 0.0F, SHEET_W, SHEET_H);
        this.layout = layout;
        this.modules = true;
    }

    // The still parts and the nozzles of these ports: {solid, cutout, glow}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        floor();
        for (Cell cell : layout.cells) {
            centrifuge(cell);
        }
        headers();
        panel();
        for (Port port : ports) {
            nozzle(port);
        }
        return new float[][] { solid.toArray(), cutout.toArray(), glow.toArray() };
    }

    // The bands' marks at this angle (degrees), each centrifuge a little out of step with the next: glow quads.
    public float[] buildMarks(float angle) {
        startStatic();
        glowing = true;
        int i = 0;
        for (Cell cell : layout.cells) {
            double a = Math.toRadians(angle + i * 137.0);
            float cx = cell.centreX(), cu = cell.centreU(), r = CentrifugeCascadeLayout.BAND_R + MARK_OUT;
            double half = MARK_W / 2.0 / r;
            float[] p0 = { cx + (float) Math.cos(a - half) * r, CentrifugeCascadeLayout.BAND_Y0, cu + (float) Math.sin(a - half) * r };
            float[] p1 = { cx + (float) Math.cos(a + half) * r, CentrifugeCascadeLayout.BAND_Y0, cu + (float) Math.sin(a + half) * r };
            float[] p2 = { p1[0], CentrifugeCascadeLayout.BAND_Y1, p1[2] };
            float[] p3 = { p0[0], CentrifugeCascadeLayout.BAND_Y1, p0[2] };
            orient(p0, p1, p2, p3, new float[] { (float) Math.cos(a), 0.0F, (float) Math.sin(a) }, Paint.M_SCREEN);
            i++;
        }
        glowing = false;
        return glow.toArray();
    }

    // The raised floor: a deep plinth under a 2 px deck, plated, with a perforated tile under each centrifuge.
    private void floor() {
        float w = layout.widthPx(), l = layout.lengthPx(), top = CentrifugeCascadeLayout.FLOOR_TOP, deck = top - CentrifugeCascadeLayout.DECK;
        box(0.0F, 0.0F, 0.0F, w, deck, l, Paint.FOOTING, null, Paint.FOOTING, 0);
        box(0.0F, deck, 0.0F, w, top, l, Paint.MID, Paint.DECK, null, SKIP_DOWN);
        solidBoxes.add(new float[] { 0.0F, 0.0F, 0.0F, w, top, l });
        for (Cell cell : layout.cells) {
            float x0 = cell.x() * 16.0F + 1.0F, u0 = cell.u() * 16.0F + 1.0F;
            face(new float[] { x0, top + 0.02F, u0 + 14.0F }, new float[] { 1, 0, 0 }, new float[] { 0, 0, -1 }, 14.0F, 14.0F, Paint.M_GRATING, false);
        }
    }

    // One centrifuge on its cell: the foot, the motor housing, the casing, the band and the cap with its three
    // connections.
    private void centrifuge(Cell cell) {
        float cx = cell.centreX(), cu = cell.centreU(), h = CentrifugeCascadeLayout.FOOT_HALF;
        box(cx - h, CentrifugeCascadeLayout.FLOOR_TOP, cu - h, cx + h, CentrifugeCascadeLayout.FOOT_TOP, cu + h, Paint.PLAIN, Paint.PLAIN, null, SKIP_DOWN);
        double start = Math.PI / CASING_SIDES;
        uprightTube(cx, cu, new float[][] { { CentrifugeCascadeLayout.FOOT_TOP, CentrifugeCascadeLayout.MOTOR_R },
                { CentrifugeCascadeLayout.MOTOR_TOP, CentrifugeCascadeLayout.MOTOR_R } }, CASING_SIDES, start, Paint.BANDED, Paint.MID);
        uprightTube(cx, cu, new float[][] { { CentrifugeCascadeLayout.MOTOR_TOP, CentrifugeCascadeLayout.CASING_R },
                { CentrifugeCascadeLayout.CASING_TOP, CentrifugeCascadeLayout.CASING_R } }, CASING_SIDES, start, Paint.POLISHED, null);
        uprightTube(cx, cu, new float[][] { { CentrifugeCascadeLayout.BAND_Y0, CentrifugeCascadeLayout.BAND_R },
                { CentrifugeCascadeLayout.BAND_Y1, CentrifugeCascadeLayout.BAND_R } }, CASING_SIDES, start, Paint.BANDED, Paint.DARK);
        uprightTube(cx, cu, new float[][] { { CentrifugeCascadeLayout.CASING_TOP, CentrifugeCascadeLayout.CAP_R },
                { CentrifugeCascadeLayout.CAP_TOP, CentrifugeCascadeLayout.CAP_R } }, CASING_SIDES, start, Paint.BANDED, Paint.MID);
        uprights.add(new float[] { cx, cu, CentrifugeCascadeLayout.MOTOR_R, CentrifugeCascadeLayout.FLOOR_TOP, CentrifugeCascadeLayout.CAP_TOP });
        for (float du : CentrifugeCascadeLayout.HEADER_U) {
            tube(new float[] { cx, CentrifugeCascadeLayout.CAP_TOP, cu + du }, new float[] { cx, CentrifugeCascadeLayout.HEADER_Y, cu + du },
                    CentrifugeCascadeLayout.RISER_R, 4, Paint.BANDED);
        }
    }

    // Over each row, the three headers across x with capped ends.
    private void headers() {
        for (Map.Entry<Integer, List<Integer>> row : layout.rows().entrySet()) {
            float[] span = CentrifugeCascadeLayout.headerSpan(row.getValue());
            float cu = row.getKey() * 16.0F + 8.0F;
            for (float du : CentrifugeCascadeLayout.HEADER_U) {
                prismX(cu + du, CentrifugeCascadeLayout.HEADER_Y, span[0], span[1], CentrifugeCascadeLayout.HEADER_R, Paint.BANDED, Paint.MID);
            }
        }
    }

    // The control panel on the controller's block: a cabinet, a sill, louvres low on its front and the lit screen.
    private void panel() {
        float[] p = CentrifugeCascadeLayout.PANEL;
        float ox = layout.panelX * 16.0F;
        float x0 = ox + p[0], x1 = ox + p[3], y0 = p[1], y1 = p[4], u0 = p[2], u1 = p[5];
        box(x0, y0, u0, x1, y1, u1, Paint.GEN_PLAIN, Paint.GEN_PLAIN, null, SKIP_DOWN);
        box(x0 - 0.5F, y1 - 1.0F, u0 - 0.5F, x1 + 0.5F, y1, u1 + 0.5F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
        solidBoxes.add(new float[] { x0, y0, u0, x1, y1, u1 });
        // Facing -u (out of the hall's front): right runs toward -x.
        float[] right = { -1, 0, 0 }, up = { 0, 1, 0 };
        face(new float[] { x1 - 2.0F, y0 + 2.0F, u0 - 0.05F }, right, up, x1 - x0 - 4.0F, 6.0F, Paint.VENT, false);
        float[] s = CentrifugeCascadeLayout.SCREEN;
        // The bezel, then the screen in the glow list.
        box(ox + s[0] - 1.0F, s[1] - 1.0F, u0 - 0.6F, ox + s[2] + 1.0F, s[3] + 1.0F, u0, Paint.DARK, Paint.MID, Paint.DEEP, SKIP_POS_U);
        glowing = true;
        face(new float[] { ox + s[2], s[1], u0 - 0.65F }, right, up, s[2] - s[0], s[3] - s[1], Paint.M_SCREEN, false);
        glowing = false;
    }
}
