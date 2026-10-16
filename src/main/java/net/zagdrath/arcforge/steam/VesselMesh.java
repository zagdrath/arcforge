/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// What the walled vessels' large models share (the Fission Reactor, the Steam Generator and the Heavy Water Tower; see
// VesselRenderer), on the refinery tower's sheet: the plinth they stand on, the casing frame round them, round shells
// and heads built up from their profiles, a part standing out of a round shell square to it, and the control panel on
// the controller's block. A large model (docs/TEXTURE_STYLE.md, "Large model texturing"). Their walls' blocks hide when
// formed, so this is what's seen, the port plates and nozzles aside.
public abstract class VesselMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 256;
    // The plinth's height (px) under its 2 px deck, and the casing frame's posts and beams across.
    public static final float PLINTH = 5.0F, DECK = 2.0F, POST = 4.0F;
    static final int SHELL_SIDES = 24;

    final ModelFrame frame;
    final float width, length, height, cx, cu;

    VesselMesh(ModelFrame frame) {
        super(frame.alongX(), frame.flip(), frame.width() * PX, frame.length() * PX, 0.0F, 0.0F, SHEET_W, SHEET_H);
        this.frame = frame;
        this.width = frame.width() * PX;
        this.length = frame.length() * PX;
        this.height = frame.height() * PX;
        this.cx = width / 2.0F;
        this.cu = length / 2.0F;
        this.modules = true;
    }

    // The plinth over the whole footprint: a FOOTING base under a 2 px plain deck (its top the tread deck).
    void plinth() {
        float deck = PLINTH - DECK;
        box(0.0F, 0.0F, 0.0F, width, deck, length, Paint.FOOTING, null, Paint.FOOTING, 0);
        box(0.0F, deck, 0.0F, width, PLINTH, length, Paint.MID, Paint.DECK, null, SKIP_DOWN);
        solidBoxes.add(new float[] { 0.0F, 0.0F, 0.0F, width, PLINTH, length });
    }

    // The casing frame: a post up each corner from the plinth and a beam along each top edge, plain steel.
    void casingFrame() {
        float top = height, p = POST;
        float[][] corners = { { 0, 0 }, { width - p, 0 }, { 0, length - p }, { width - p, length - p } };
        for (float[] c : corners) {
            box(c[0], PLINTH, c[1], c[0] + p, top - p, c[1] + p, Paint.PLAIN, null, null, SKIP_DOWN | SKIP_UP);
            solidBoxes.add(new float[] { c[0], PLINTH, c[1], c[0] + p, top, c[1] + p });
        }
        // Along x at the front and back, then along u between them at the sides.
        box(0.0F, top - p, 0.0F, width, top, p, Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, 0);
        box(0.0F, top - p, length - p, width, top, length, Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, 0);
        box(0.0F, top - p, p, p, top, length - p, Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, SKIP_NEG_U | SKIP_POS_U);
        box(width - p, top - p, p, width, top, length - p, Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, SKIP_NEG_U | SKIP_POS_U);
        solidBoxes.add(new float[] { 0.0F, top - p, 0.0F, width, top, p });
        solidBoxes.add(new float[] { 0.0F, top - p, length - p, width, top, length });
        solidBoxes.add(new float[] { 0.0F, top - p, 0.0F, p, top, length });
        solidBoxes.add(new float[] { width - p, top - p, 0.0F, width, top, length });
    }

    // A round shell standing on the vessel's axis: its outline as (y, radius) points from the bottom. A straight
    // section is plated unrolled (CASING), a tapering one is bare grain; it stops nozzles as a cylinder of its widest
    // radius.
    void shell(float[][] profile, int sides) {
        uprightTube(cx, cu, profile, sides, Math.PI / sides, Paint.CASING, null);
        float r = 0.0F;
        for (float[] point : profile) {
            r = Math.max(r, point[1]);
        }
        uprights.add(new float[] { cx, cu, r, profile[0][0], profile[profile.length - 1][0] });
    }

    // A flange ring round the shell: a band radius r from y0 to y1, its top a plain ring out from inner.
    void flange(float y0, float y1, float r, float inner, int sides) {
        uprightTube(cx, cu, new float[][] { { y0, r }, { y1, r } }, sides, Math.PI / sides, Paint.BANDED, null);
        ring(y1, r, inner, sides, true);
        ring(y0, r, inner, sides, false);
    }

    // A flat ring about the axis at y from inner out to outer, facing up or down: plain steel, plated as one surface
    // across its wedges.
    void ring(float y, float outer, float inner, int sides, boolean up) {
        double step = 2.0 * Math.PI / sides, start = Math.PI / sides;
        float[] n = { 0, up ? 1 : -1, 0 };
        List<float[]> rim = new java.util.ArrayList<>();
        for (int f = 0; f < sides; f++) {
            double a = start + f * step;
            rim.add(new float[] { cx + (float) Math.cos(a) * outer, y, cu + (float) Math.sin(a) * outer });
        }
        PlateFrame plates = roundFace(rim, n, Paint.PLAIN, 2.0F * outer);
        for (int f = 0; f < sides; f++) {
            double a0 = start + f * step, a1 = a0 + step;
            float[] o0 = { cx + (float) Math.cos(a0) * outer, y, cu + (float) Math.sin(a0) * outer };
            float[] o1 = { cx + (float) Math.cos(a1) * outer, y, cu + (float) Math.sin(a1) * outer };
            float[] i1 = { cx + (float) Math.cos(a1) * inner, y, cu + (float) Math.sin(a1) * inner };
            float[] i0 = { cx + (float) Math.cos(a0) * inner, y, cu + (float) Math.sin(a0) * inner };
            if (plates == null) {
                orient(o0, o1, i1, i0, n, Paint.MID);
            } else if (dot(cross(sub(i1, o0), sub(i0, o1)), n) >= 0) {
                plateIn(plates, List.of(o0, o1, i1, i0));
            } else {
                plateIn(plates, List.of(i0, i1, o1, o0));
            }
        }
    }

    // An elliptical head on the shell, radius r at y0 rising h to its crown: its outline in the profile's form, closed
    // by a small plain disc on top.
    static float[][] head(float y0, float r, float h, boolean up) {
        double[] angles = { 0, 22, 40, 56, 70, 82 };
        float[][] profile = new float[angles.length][];
        for (int i = 0; i < angles.length; i++) {
            double a = Math.toRadians(angles[i]);
            float y = y0 + (up ? 1 : -1) * h * (float) Math.sin(a);
            profile[i] = new float[] { y, r * (float) Math.cos(a) };
        }
        if (!up) {
            // From the bottom up.
            java.util.Collections.reverse(java.util.Arrays.asList(profile));
        }
        return profile;
    }

    // The height of an elliptical head (radius r, h tall, from y0) above the point dr out from the axis.
    static float headAt(float y0, float r, float h, float dr) {
        float t = Math.min(1.0F, dr / r);
        return y0 + h * (float) Math.sqrt(Math.max(0.0F, 1.0F - t * t));
    }

    // A part standing out of a round shell at angle a (radians round the axis from +x toward +u): a block w across and
    // h up centred at y, from depth in px inside radius r out to out px beyond it, its outer face painted face and its
    // sides sides.
    void boss(float a, float r, float y, float w, float h, float in, float out, Paint face, Paint sides) {
        float[] n = { (float) Math.cos(a), 0.0F, (float) Math.sin(a) };
        float[][] axes = plane(n);
        float[] right = axes[0], up = axes[1];
        float[] centre = { cx + n[0] * (r + out), y, cu + n[2] * (r + out) };
        float hw = w / 2.0F, hh = h / 2.0F, depth = out + in;
        float[] origin = at(centre, right, up, -hw, -hh);
        face(origin, right, up, w, h, face, false);
        // The sides, back from the outer face's edges toward the axis.
        float[] back = { -n[0] * depth, 0.0F, -n[2] * depth };
        float[][] rim = { at(centre, right, up, -hw, -hh), at(centre, right, up, hw, -hh), at(centre, right, up, hw, hh), at(centre, right, up, -hw, hh) };
        float[][] outward = { { -up[0], -up[1], -up[2] }, right, up, { -right[0], -right[1], -right[2] } };
        for (int i = 0; i < 4; i++) {
            float[] a0 = rim[i], a1 = rim[(i + 1) % 4];
            float[] b0 = { a0[0] + back[0], a0[1], a0[2] + back[2] }, b1 = { a1[0] + back[0], a1[1], a1[2] + back[2] };
            // Bottom edge, right edge, top edge, left edge: each side faces away from the boss's middle.
            float[] side = i == 0 ? outward[0] : i == 1 ? outward[1] : i == 2 ? outward[2] : outward[3];
            orient(b0, b1, a1, a0, side, sides);
        }
    }

    // A face flush on a boss's outer face (see boss), out a little further, w x h, to the glow list when lit.
    void bossFace(float a, float r, float y, float w, float h, float out, Paint paint, boolean lit) {
        float[] n = { (float) Math.cos(a), 0.0F, (float) Math.sin(a) };
        float[][] axes = plane(n);
        float[] centre = { cx + n[0] * (r + out), y, cu + n[2] * (r + out) };
        boolean was = glowing;
        glowing = lit;
        face(at(centre, axes[0], axes[1], -w / 2.0F, -h / 2.0F), axes[0], axes[1], w, h, paint, false);
        glowing = was;
    }

    // The control panel on the controller's block (bx, by on the front wall, u = 0): a GEN_PLAIN cabinet on a sill
    // standing just inside the wall's face, VENT louvres low on its front and a lit screen (M_SCREEN, in the glow list)
    // facing out.
    void panel(int bx, int by) {
        float x0 = bx * PX + 3.0F, x1 = bx * PX + 13.0F;
        float y0 = Math.max(by * PX + 1.0F, PLINTH), y1 = Math.min(by * PX + 15.0F, height - POST);
        if (y1 - y0 < 10.0F) {
            return;
        }
        float u0 = 0.5F, u1 = 3.5F;
        box(x0, y0, u0, x1, y1, u1, Paint.GEN_PLAIN, Paint.GEN_PLAIN, Paint.GEN_PLAIN, SKIP_UP);
        box(x0 - 0.5F, y1 - 1.0F, u0 - 0.4F, x1 + 0.5F, y1, u1 + 0.5F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
        solidBoxes.add(new float[] { x0, y0, u0, x1, y1, u1 });
        float[] right = { -1, 0, 0 }, up = { 0, 1, 0 };
        face(new float[] { x1 - 2.0F, y0 + 1.0F, u0 - 0.05F }, right, up, x1 - x0 - 4.0F, 4.0F, Paint.VENT, false);
        float sx0 = x0 + 1.0F, sx1 = sx0 + 8.0F, sy0 = y1 - 9.0F, sy1 = sy0 + 6.0F;
        box(sx0 - 1.0F, sy0 - 1.0F, u0 - 0.6F, sx1 + 1.0F, sy1 + 1.0F, u0, Paint.DARK, Paint.MID, Paint.DEEP, SKIP_POS_U);
        glowing = true;
        face(new float[] { sx1, sy0, u0 - 0.65F }, right, up, sx1 - sx0, sy1 - sy0, Paint.M_SCREEN, false);
        glowing = false;
    }

    // The port nozzles, then the lists: {solid, cutout, glow}.
    float[][] finish(List<Port> ports) {
        for (Port port : ports) {
            nozzle(port);
        }
        return new float[][] { solid.toArray(), cutout.toArray(), glow.toArray() };
    }
}
