/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;

import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.SteamCrackerLayout;

// The Steam Cracker's model, from SteamCrackerLayout (see SteamCrackerRenderer), on the refinery tower's sheet: built
// once, and again when its ports change, with the fire seen through its peep holes in the glow list (drawn as bright as
// the furnace is hot). A large model (docs/TEXTURE_STYLE.md, "Large model texturing"): its walls plated, the
// buckstays in the girder flange, the round parts carrying the ramp, the peep holes' reveals in the refractory brick.
public final class SteamCrackerMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 256;
    private static final int VESSEL_SIDES = 16;

    public SteamCrackerMesh(ModelFrame frame) {
        super(frame.alongX(), frame.flip(), SteamCrackerLayout.WIDTH, SteamCrackerLayout.LENGTH, 0.0F, 0.0F, SHEET_W, SHEET_H);
        this.modules = true;
    }

    // The still parts and the nozzles of these ports: {solid, cutout, glow}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        deck();
        furnace();
        roof();
        drum();
        transferLine();
        for (Port port : ports) {
            nozzle(port);
        }
        return new float[][] { solid.toArray(), cutout.toArray(), glow.toArray() };
    }

    // The deck: plates over the footprint on a footing.
    private void deck() {
        float w = SteamCrackerLayout.WIDTH, l = SteamCrackerLayout.LENGTH, top = SteamCrackerLayout.DECK_TOP;
        box(0.0F, 0.0F, 0.0F, w, top - 1.0F, l, Paint.FOOTING, null, Paint.FOOTING, 0);
        box(0.0F, top - 1.0F, 0.0F, w, top, l, Paint.MID, Paint.DECK, null, SKIP_DOWN);
        solidBoxes.add(new float[] { 0.0F, 0.0F, 0.0F, w, top, l });
    }

    // The furnace box: welded wall plates, a plinth lip at its foot and a cornice at its top, buckstays up the walls
    // and a tie band round them, the peep holes, and the burners along the left side with their fuel line.
    private void furnace() {
        float[] f = SteamCrackerLayout.FURNACE;
        float x0 = f[0], y0 = f[1], u0 = f[2], x1 = f[3], y1 = f[4], u1 = f[5];
        box(x0, y0, u0, x1, y1, u1, Paint.PLAIN, Paint.PLAIN, null, SKIP_DOWN);
        solidBoxes.add(f);
        // The plinth lip and the cornice.
        box(x0 - 1.0F, y0, u0 - 1.0F, x1 + 1.0F, y0 + 2.0F, u1 + 1.0F, Paint.FACE_SHADE, Paint.MID, null, SKIP_DOWN);
        box(x0 - 0.75F, y1 - 1.5F, u0 - 0.75F, x1 + 0.75F, y1, u1 + 0.75F, Paint.MID, Paint.LIT, Paint.DARK, 0);
        // Buckstays: girder columns standing 1 px proud of the walls, and the tie band round them.
        for (float u : SteamCrackerLayout.SIDE_STAYS) {
            box(x0 - 1.0F, y0 + 2.0F, u - 1.0F, x0, y1 - 1.5F, u + 1.0F, Paint.STRAP, null, null, SKIP_UP | SKIP_DOWN | SKIP_POS_X);
            box(x1, y0 + 2.0F, u - 1.0F, x1 + 1.0F, y1 - 1.5F, u + 1.0F, Paint.STRAP, null, null, SKIP_UP | SKIP_DOWN | SKIP_NEG_X);
        }
        for (float x : SteamCrackerLayout.FRONT_STAYS) {
            box(x - 1.0F, y0 + 2.0F, u0 - 1.0F, x + 1.0F, y1 - 1.5F, u0, Paint.STRAP, null, null, SKIP_UP | SKIP_DOWN | SKIP_POS_U);
            box(x - 1.0F, y0 + 2.0F, u1, x + 1.0F, y1 - 1.5F, u1 + 1.0F, Paint.STRAP, null, null, SKIP_UP | SKIP_DOWN | SKIP_NEG_U);
        }
        float tie = SteamCrackerLayout.TIE_Y;
        box(x0 - 1.4F, tie - 1.0F, u0 - 1.4F, x1 + 1.4F, tie + 1.0F, u1 + 1.4F, Paint.MID, Paint.LIT, Paint.DARK, 0);
        // The peep holes: on the front and down the left side.
        for (float[] p : SteamCrackerLayout.FRONT_PEEPS) {
            peep(new float[] { p[0], p[1], u0 }, new float[] { 0, 0, -1 });
        }
        for (float[] p : SteamCrackerLayout.SIDE_PEEPS) {
            peep(new float[] { x0, p[1], p[0] }, new float[] { -1, 0, 0 });
        }
        // The burners: a squat housing under each side peep hole column, fed by a fuel line along the wall's foot.
        float line = y0 + 3.5F;
        for (float u : new float[] { 18.0F, 30.0F }) {
            box(x0 - 2.5F, y0 + 2.0F, u - 2.0F, x0, y0 + 5.0F, u + 2.0F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN | SKIP_POS_X);
        }
        tube(new float[] { x0 - 3.2F, line, u0 + 1.0F }, new float[] { x0 - 3.2F, line, u1 - 2.0F }, 0.8F, PIPE_SIDES, Paint.BANDED);
    }

    // A peep hole on the wall at centre (its middle, on the wall's face) facing out: a steel frame standing proud, the
    // refractory lining showing round its reveal, and the fire inside (in the glow list).
    private void peep(float[] centre, float[] out) {
        float w = SteamCrackerLayout.PEEP_W, h = SteamCrackerLayout.PEEP_H, proud = 1.2F;
        boolean front = out[2] != 0;
        float[][] axes = plane(out);
        float[] right = axes[0], up = axes[1];
        // The frame.
        if (front) {
            box(centre[0] - w / 2.0F - 1.0F, centre[1] - h / 2.0F - 1.0F, centre[2] - proud, centre[0] + w / 2.0F + 1.0F, centre[1] + h / 2.0F + 1.0F,
                    centre[2], Paint.MID, Paint.LIT, Paint.DARK, SKIP_POS_U);
        } else {
            box(centre[0] - proud, centre[1] - h / 2.0F - 1.0F, centre[2] - w / 2.0F - 1.0F, centre[0], centre[1] + h / 2.0F + 1.0F,
                    centre[2] + w / 2.0F + 1.0F, Paint.MID, Paint.LIT, Paint.DARK, SKIP_POS_X);
        }
        float[] face = { centre[0] + out[0] * (proud + 0.05F), centre[1], centre[2] + out[2] * (proud + 0.05F) };
        float[] origin = at(face, right, up, -w / 2.0F, -h / 2.0F);
        face(origin, right, up, w, h, Paint.M_BRICK, false);
        glowing = true;
        float[] lit = { face[0] + out[0] * 0.05F, face[1], face[2] + out[2] * 0.05F };
        face(at(lit, right, up, 1.0F - w / 2.0F, 1.0F - h / 2.0F), right, up, w - 2.0F, h - 2.0F, Paint.M_FIRE, false);
        glowing = false;
    }

    // The roof: the radiant tubes rising out of it into two banded headers, the collector across their front ends, and
    // the convection hood with its stack.
    private void roof() {
        float roof = SteamCrackerLayout.FURNACE[4], y = SteamCrackerLayout.HEADER_Y, r = SteamCrackerLayout.HEADER_R;
        float hu0 = SteamCrackerLayout.HEADER_U0, hu1 = SteamCrackerLayout.HEADER_U1;
        for (float x : SteamCrackerLayout.HEADER_X) {
            prismU(x, y, new float[][] { { hu0 - 1.0F, r }, { hu1, r } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, Paint.MID, Paint.SHADE, null);
            // Flanged ends.
            prismU(x, y, new float[][] { { hu1 - 1.0F, r + 0.6F }, { hu1, r + 0.6F } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, Paint.MID,
                    Paint.SHADE, null);
            for (float u = hu0 + 1.0F; u < hu1 - 1.0F; u += SteamCrackerLayout.TUBE_STEP) {
                tube(new float[] { x, roof, u }, new float[] { x, y - r + 0.2F, u }, SteamCrackerLayout.TUBE_R, 4, Paint.BANDED);
                // Each tube's collar where it leaves the roof.
                box(x - 1.0F, roof, u - 1.0F, x + 1.0F, roof + 0.6F, u + 1.0F, Paint.MID, Paint.LIT, null, SKIP_DOWN);
            }
            solidCylinders.add(new float[] { x, y, r, hu0 - 1.0F, hu1 });
        }
        // The collector across the headers' front ends.
        float cu = hu0 - 1.0F, xa = SteamCrackerLayout.HEADER_X[0], xb = SteamCrackerLayout.HEADER_X[1];
        tube(new float[] { xa - r, y, cu }, new float[] { xb + r, y, cu }, r + 0.4F, PIPE_SIDES, Paint.BANDED);
        // The convection hood and the stack.
        float[] h = SteamCrackerLayout.HOOD;
        box(h[0], h[1], h[2], h[3], h[4], h[5], Paint.PLATE, Paint.PLAIN, null, SKIP_DOWN);
        box(h[0] - 0.6F, h[4] - 1.0F, h[2] - 0.6F, h[3] + 0.6F, h[4], h[5] + 0.6F, Paint.MID, Paint.LIT, Paint.DARK, 0);
        solidBoxes.add(h);
        float sx = SteamCrackerLayout.STACK_X, su = SteamCrackerLayout.STACK_U, sr = SteamCrackerLayout.STACK_R, top = SteamCrackerLayout.STACK_TOP;
        uprightTube(sx, su, new float[][] { { h[4], sr }, { top - 1.5F, sr } }, 12, Math.PI / 12, Paint.BANDED, null);
        uprightTube(sx, su, new float[][] { { top - 1.5F, sr + 0.6F }, { top, sr + 0.6F } }, 12, Math.PI / 12, Paint.BANDED, Paint.DEEP);
        uprightTube(sx, su, new float[][] { { h[4] + 1.5F, sr + 0.6F }, { h[4] + 3.0F, sr + 0.6F } }, 12, Math.PI / 12, Paint.BANDED, Paint.MID);
        uprights.add(new float[] { sx, su, sr, h[4], top });
    }

    // The quench drum: a skirt on the deck, its shell in two courses with a flange between, the head, and a level
    // gauge's stand-offs down its front.
    private void drum() {
        float x = SteamCrackerLayout.DRUM_X, u = SteamCrackerLayout.DRUM_U, r = SteamCrackerLayout.DRUM_R;
        float deck = SteamCrackerLayout.DECK_TOP, bottom = SteamCrackerLayout.DRUM_BOTTOM, top = SteamCrackerLayout.DRUM_TOP;
        float middle = (bottom + top) / 2.0F;
        uprightTube(x, u, new float[][] { { deck, r + 0.5F }, { bottom, r + 0.5F } }, VESSEL_SIDES, 0.0, Paint.BANDED, Paint.MID);
        uprightTube(x, u, new float[][] { { bottom, r }, { middle - 1.0F, r } }, VESSEL_SIDES, 0.0, Paint.CASING, null);
        uprightTube(x, u, new float[][] { { middle + 1.0F, r }, { top, r } }, VESSEL_SIDES, 0.0, Paint.CASING, null);
        uprightTube(x, u, new float[][] { { middle - 1.0F, r + 0.8F }, { middle + 1.0F, r + 0.8F } }, VESSEL_SIDES, 0.0, Paint.BANDED, Paint.MID);
        float[][] head = new float[5][];
        for (int i = 0; i <= 4; i++) {
            double t = Math.PI / 2.0 * i / 4.0;
            head[i] = new float[] { top + SteamCrackerLayout.DRUM_HEAD * (float) Math.sin(t), r * (float) Math.cos(t) };
        }
        uprightTube(x, u, head, VESSEL_SIDES, 0.0, Paint.BANDED, null);
        uprights.add(new float[] { x, u, r + 0.5F, deck, top + SteamCrackerLayout.DRUM_HEAD });
        // The level gauge: a glass column on two stand-offs down its front.
        float gu = u - r - 1.6F;
        box(x - 0.6F, bottom + 3.0F, gu - 0.6F, x + 0.6F, top - 3.0F, gu + 0.6F, Paint.LIT, Paint.MID, Paint.DARK, 0);
        for (float gy : new float[] { bottom + 4.0F, top - 4.0F }) {
            box(x - 0.4F, gy - 0.4F, gu + 0.6F, x + 0.4F, gy + 0.4F, u - r + 0.2F, Paint.SHADE, Paint.MID, null, 0);
        }
    }

    // The transfer line from the collector to the quench drum: a banded pipe with a flange at each turn.
    private void transferLine() {
        float[][] path = SteamCrackerLayout.transferLine();
        float r = SteamCrackerLayout.HEADER_R;
        for (int i = 0; i + 1 < path.length; i++) {
            tube(path[i], path[i + 1], r, PIPE_SIDES, Paint.BANDED);
        }
        // Each turn: an elbow (a short drum round the corner) with a bolted flange ring each side of it.
        for (int i = 1; i + 1 < path.length; i++) {
            float[] p = path[i];
            box(p[0] - r - 0.2F, p[1] - r - 0.2F, p[2] - r - 0.2F, p[0] + r + 0.2F, p[1] + r + 0.2F, p[2] + r + 0.2F, Paint.SHADE, Paint.FACE, Paint.DARK, 0);
            for (float[] other : new float[][] { path[i - 1], path[i + 1] }) {
                float[] dir = normalize(sub(other, p));
                float[] a = { p[0] + dir[0] * (r + 0.6F), p[1] + dir[1] * (r + 0.6F), p[2] + dir[2] * (r + 0.6F) };
                float[] b = { p[0] + dir[0] * (r + 1.4F), p[1] + dir[1] * (r + 1.4F), p[2] + dir[2] * (r + 1.4F) };
                tube(a, b, r + 0.6F, PIPE_SIDES, Paint.BANDED);
            }
        }
    }
}
