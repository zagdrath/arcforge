/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;

import net.zagdrath.arcforge.multiblock.CrackingUnitLayout;
import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Cracking Unit's model, from CrackingUnitLayout (see CrackingUnitRenderer), on the refinery's sheet
// (block/distillation_array/refinery): built once, and again when its ports change, with the regenerator's sight port
// in a glow list of its own (drawn as bright as the unit is hot). How it's built and painted: ModelMesh.
public final class CrackingUnitMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 256;
    private static final int VESSEL_SIDES = 16, SMALL_SIDES = 12;

    public CrackingUnitMesh(ModelFrame frame) {
        super(frame.alongX(), frame.flip(), CrackingUnitLayout.WIDTH, CrackingUnitLayout.LENGTH, 0.0F, 0.0F, SHEET_W, SHEET_H);
        this.modules = true;
    }

    // The still parts and the nozzles of these ports: {solid, cutout, glow}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        deck();
        regenerator();
        reactor();
        riser();
        standpipes();
        hopper();
        vapourLine();
        for (Port port : ports) {
            nozzle(port);
        }
        return new float[][] { solid.toArray(), cutout.toArray(), glow.toArray() };
    }

    // The base deck: plates over the footprint on a footing, with a lip round the top.
    private void deck() {
        float w = CrackingUnitLayout.WIDTH, l = CrackingUnitLayout.LENGTH, top = CrackingUnitLayout.DECK_TOP;
        box(0.0F, 0.0F, 0.0F, w, top - 1.0F, l, Paint.FOOTING, null, Paint.FOOTING, 0);
        box(0.0F, top - 1.0F, 0.0F, w, top, l, Paint.MID, Paint.DECK, null, SKIP_DOWN);
        solidBoxes.add(new float[] { 0.0F, 0.0F, 0.0F, w, top, l });
    }

    // A round vessel's elliptical head on top of its shell (radius r at y), banded.
    private void head(float x, float u, float y, float r, float height) {
        float[][] head = new float[5][];
        for (int i = 0; i <= 4; i++) {
            double t = Math.PI / 2.0 * i / 4.0;
            head[i] = new float[] { y + height * (float) Math.sin(t), r * (float) Math.cos(t) };
        }
        uprightTube(x, u, head, VESSEL_SIDES, 0.0, Paint.BANDED, null);
    }

    // A bolted flange ring round a vessel at y.
    private void flange(float x, float u, float y, float r, int bolts) {
        uprightTube(x, u, new float[][] { { y - 1.0F, r + 0.8F }, { y + 1.0F, r + 0.8F } }, VESSEL_SIDES, 0.0, Paint.BANDED, Paint.MID);
        for (int i = 0; i < bolts; i++) {
            double a = 2.0 * Math.PI * (i + 0.5) / bolts;
            float bx = x + (float) Math.cos(a) * (r + 1.1F), bu = u + (float) Math.sin(a) * (r + 1.1F);
            box(bx - 0.4F, y - 0.4F, bu - 0.4F, bx + 0.4F, y + 0.4F, bu + 0.4F, Paint.RIVET, Paint.RIVET, Paint.DARK, 0);
        }
    }

    // The regenerator: a base ring, its riveted shell in two courses with a flange between, the head, the flue stack
    // and the sight port onto the burning catalyst bed.
    private void regenerator() {
        float x = CrackingUnitLayout.REGEN_X, u = CrackingUnitLayout.REGEN_U, r = CrackingUnitLayout.REGEN_R;
        float bottom = CrackingUnitLayout.REGEN_BOTTOM, top = CrackingUnitLayout.REGEN_TOP, middle = (bottom + top) / 2.0F;
        float deck = CrackingUnitLayout.DECK_TOP;
        uprightTube(x, u, new float[][] { { deck, r + 1.0F }, { bottom, r + 1.0F } }, VESSEL_SIDES, 0.0, Paint.BANDED, Paint.MID);
        uprightTube(x, u, new float[][] { { bottom, r }, { middle - 1.0F, r } }, VESSEL_SIDES, 0.0, Paint.CASING, null);
        uprightTube(x, u, new float[][] { { middle + 1.0F, r }, { top, r } }, VESSEL_SIDES, 0.0, Paint.CASING, null);
        flange(x, u, middle, r, VESSEL_SIDES);
        head(x, u, top, r, CrackingUnitLayout.REGEN_HEAD);
        uprights.add(new float[] { x, u, r + 1.0F, deck, top + CrackingUnitLayout.REGEN_HEAD });
        // The flue stack, with a lip at the top.
        float apex = top + CrackingUnitLayout.REGEN_HEAD - 0.5F, fr = CrackingUnitLayout.FLUE_R, ftop = CrackingUnitLayout.FLUE_TOP;
        uprightTube(x, u, new float[][] { { apex, fr }, { ftop - 2.0F, fr } }, SMALL_SIDES, Math.PI / SMALL_SIDES, Paint.BANDED, null);
        uprightTube(x, u, new float[][] { { ftop - 2.0F, fr + 0.6F }, { ftop, fr + 0.6F } }, SMALL_SIDES, Math.PI / SMALL_SIDES, Paint.BANDED, Paint.DEEP);
        flange(x, u, apex + 3.0F, fr, 6);
        uprights.add(new float[] { x, u, fr + 0.6F, apex, ftop });
        sightPort();
    }

    // The sight port on the regenerator's front: a steel frame standing proud of the wall, a dark pane, and the bed's
    // glow in front of it (in the glow list).
    private void sightPort() {
        float[] at = CrackingUnitLayout.sightPort();
        float w = 3.0F, h = 2.0F, front = at[2] - 0.6F;
        box(at[0] - w - 1.0F, at[1] - h - 1.0F, front, at[0] + w + 1.0F, at[1] + h + 1.0F, at[2] + 2.5F, Paint.MID, Paint.LIT, Paint.DARK, SKIP_POS_U);
        float[] normal = { 0, 0, -1 };
        float[][] axes = plane(normal);
        float[] right = axes[0], up = axes[1];
        float[] origin = { at[0] - right[0] * w, at[1] - h, front - 0.05F };
        face(origin, right, up, 2 * w, 2 * h, Paint.DEEP, false);
        glowing = true;
        face(new float[] { origin[0], origin[1], origin[2] - 0.05F }, right, up, 2 * w, 2 * h, Paint.GLOW, false);
        glowing = false;
    }

    // The reactor: a skirt, the stripper, a cone up to the disengager, its head; flanges at the joints.
    private void reactor() {
        float x = CrackingUnitLayout.REACTOR_X, u = CrackingUnitLayout.REACTOR_U;
        float deck = CrackingUnitLayout.DECK_TOP, skirt = CrackingUnitLayout.SKIRT_TOP, stripTop = CrackingUnitLayout.STRIPPER_TOP;
        float sr = CrackingUnitLayout.STRIPPER_R, dr = CrackingUnitLayout.DISENGAGER_R;
        float dBottom = CrackingUnitLayout.DISENGAGER_BOTTOM, dTop = CrackingUnitLayout.DISENGAGER_TOP;
        uprightTube(x, u, new float[][] { { deck, CrackingUnitLayout.SKIRT_R }, { skirt, CrackingUnitLayout.SKIRT_R } }, SMALL_SIDES, 0.0, Paint.BANDED, null);
        uprightTube(x, u, new float[][] { { skirt, sr }, { stripTop, sr } }, SMALL_SIDES, 0.0, Paint.CASING, null);
        uprightTube(x, u, new float[][] { { stripTop, sr }, { dBottom, dr } }, SMALL_SIDES, 0.0, Paint.BANDED, null);
        uprightTube(x, u, new float[][] { { dBottom, dr }, { dTop, dr } }, SMALL_SIDES, 0.0, Paint.CASING, null);
        flange(x, u, skirt, sr, 8);
        flange(x, u, dBottom + 1.0F, dr, 10);
        head(x, u, dTop, dr, CrackingUnitLayout.REACTOR_HEAD);
        uprights.add(new float[] { x, u, dr, skirt, dTop + CrackingUnitLayout.REACTOR_HEAD });
        uprights.add(new float[] { x, u, sr, deck, dBottom });
    }

    // The riser: its wye at the foot, the pipe up the front and its crossover into the disengager's side.
    private void riser() {
        float x = CrackingUnitLayout.RISER_X, u = CrackingUnitLayout.RISER_U, r = CrackingUnitLayout.RISER_R, top = CrackingUnitLayout.RISER_TOP;
        float[] wye = CrackingUnitLayout.WYE;
        box(wye[0], wye[1], wye[2], wye[3], wye[4], wye[5], Paint.PLATE, Paint.MID, Paint.SHADE, 0);
        solidBoxes.add(wye);
        tube(new float[] { x, wye[4], u }, new float[] { x, top, u }, r, PIPE_SIDES, Paint.BANDED);
        flange(x, u, wye[4] + 1.0F, r, 6);
        box(x - r - 0.4F, top - r - 0.4F, u - r - 0.4F, x + r + 0.4F, top + r + 0.4F, u + r + 0.4F, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
        float[] wall = CrackingUnitLayout.wallToward(CrackingUnitLayout.REACTOR_X, CrackingUnitLayout.REACTOR_U, CrackingUnitLayout.DISENGAGER_R - 0.5F, x, u);
        tube(new float[] { x, top, u }, new float[] { wall[0], top, wall[1] }, r, PIPE_SIDES, Paint.BANDED);
        uprights.add(new float[] { x, u, r, wye[4], top + r });
    }

    // The standpipes and the hopper's feed line, each with a slide valve (a body and its actuator) halfway down.
    private void standpipes() {
        float[][] pipes = CrackingUnitLayout.pipes();
        for (int i = 0; i < pipes.length; i++) {
            float[] p = pipes[i];
            float r = i == 2 ? 1.0F : 1.5F;
            float[] a = { p[0], p[1], p[2] }, b = { p[3], p[4], p[5] };
            tube(a, b, r, PIPE_SIDES, Paint.BANDED);
            if (i < 2) {
                float mx = (a[0] + b[0]) / 2.0F, my = (a[1] + b[1]) / 2.0F, mu = (a[2] + b[2]) / 2.0F;
                box(mx - 2.2F, my - 2.2F, mu - 2.2F, mx + 2.2F, my + 2.2F, mu + 2.2F, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
                box(mx - 0.8F, my + 2.2F, mu - 0.8F, mx + 0.8F, my + 5.0F, mu + 0.8F, Paint.MID, Paint.LIT, null, SKIP_DOWN);
                box(mx - 1.5F, my + 5.0F, mu - 1.5F, mx + 1.5F, my + 6.5F, mu + 1.5F, Paint.FACE, Paint.LIT, Paint.SHADE, 0);
            }
        }
    }

    // The fresh catalyst hopper: four legs, braced, its body with a lid and a stepped cone down to the feed line.
    private void hopper() {
        float[] h = CrackingUnitLayout.HOPPER;
        float deck = CrackingUnitLayout.DECK_TOP;
        for (float[] leg : CrackingUnitLayout.hopperLegs()) {
            box(leg[0] - 0.75F, deck, leg[1] - 0.75F, leg[0] + 0.75F, h[1], leg[1] + 0.75F, Paint.SHADE, null, null, SKIP_UP | SKIP_DOWN);
        }
        float braceY = (deck + h[1]) / 2.0F;
        float[][] legs = CrackingUnitLayout.hopperLegs();
        tube(new float[] { legs[0][0], braceY, legs[0][1] }, new float[] { legs[1][0], braceY, legs[1][1] }, 0.4F, 4, Paint.MID);
        tube(new float[] { legs[2][0], braceY, legs[2][1] }, new float[] { legs[3][0], braceY, legs[3][1] }, 0.4F, 4, Paint.MID);
        tube(new float[] { legs[0][0], braceY, legs[0][1] }, new float[] { legs[2][0], braceY, legs[2][1] }, 0.4F, 4, Paint.MID);
        tube(new float[] { legs[1][0], braceY, legs[1][1] }, new float[] { legs[3][0], braceY, legs[3][1] }, 0.4F, 4, Paint.MID);
        box(h[0], h[1], h[2], h[3], h[4], h[5], Paint.PLATE, null, Paint.SHADE, SKIP_UP);
        box(h[0] - 0.5F, h[4], h[2] - 0.5F, h[3] + 0.5F, h[4] + 1.0F, h[5] + 0.5F, Paint.MID, Paint.DECK, Paint.DARK, 0);
        // The cone, in three steps.
        float cone = CrackingUnitLayout.HOPPER_CONE, cx = (h[0] + h[3]) / 2.0F, cu = (h[2] + h[5]) / 2.0F;
        for (int step = 0; step < 3; step++) {
            float half = (h[3] - h[0]) / 2.0F - 1.5F * (step + 1);
            float y1 = h[1] - cone / 3.0F * step, y0 = y1 - cone / 3.0F;
            box(cx - half, y0, cu - half, cx + half, y1, cu + half, Paint.BANDED, null, step == 2 ? Paint.DARK : null, SKIP_UP);
        }
        solidBoxes.add(h);
    }

    // The vapour line: up from the reactor's head, across to the right edge and down to the deck, flanged at each turn.
    private void vapourLine() {
        float x = CrackingUnitLayout.REACTOR_X, u = CrackingUnitLayout.REACTOR_U, r = CrackingUnitLayout.VAPOUR_R;
        float y = CrackingUnitLayout.VAPOUR_Y, edge = CrackingUnitLayout.VAPOUR_X, deck = CrackingUnitLayout.DECK_TOP;
        float apex = CrackingUnitLayout.DISENGAGER_TOP + CrackingUnitLayout.REACTOR_HEAD - 0.5F;
        tube(new float[] { x, apex, u }, new float[] { x, y, u }, r, PIPE_SIDES, Paint.BANDED);
        elbow(x, y, u, r + 0.3F);
        tube(new float[] { x, y, u }, new float[] { edge, y, u }, r, PIPE_SIDES, Paint.BANDED);
        elbow(edge, y, u, r + 0.3F);
        tube(new float[] { edge, y, u }, new float[] { edge, deck, u }, r, PIPE_SIDES, Paint.BANDED);
        flange(edge, u, deck + 1.5F, r, 6);
        flange(edge, u, (deck + y) / 2.0F, r, 6);
        uprights.add(new float[] { edge, u, r, deck, y + r });
    }

    private void elbow(float x, float y, float u, float h) {
        box(x - h, y - h, u - h, x + h, y + h, u + h, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
    }
}
