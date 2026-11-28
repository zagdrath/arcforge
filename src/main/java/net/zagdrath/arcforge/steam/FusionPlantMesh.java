/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// What the fusion tier's plant vessels' models share (the Cryogenic Plant, the Cryogenic Distillation Column and the
// Tritium Plant; see FusionPlantRenderer), on the refinery tower's sheet like the other vessels: round vessels standing
// anywhere on the plinth with their flange bands and heads, vacuum-jacketed transfer lines (a jacket with bellows along
// it and a flanged elbow at each bend), sight glasses that light up while the plant runs, and glass panes with a light
// tint behind them. A large model (docs/TEXTURE_STYLE.md, "Large model texturing").
public abstract class FusionPlantMesh extends VesselMesh {
    // A vacuum jacket's radius, and its bellows': a wider short ring every BELLOWS_PITCH px along it.
    static final float JACKET_R = 1.5F, BELLOWS_R = 2.1F, BELLOWS_PITCH = 9.0F, BELLOWS_L = 1.5F;

    final int panelX, panelY;
    // Faces drawn full-bright only while the plant runs (a sight glass's lit level, a glovebox's lamp):
    // {origin, right, up, {w, h}} and their paint.
    private final List<float[][]> runningFaces = new ArrayList<>();
    private final List<Paint> runningPaints = new ArrayList<>();
    // The glass's tint panes {p0, p1, p2, p3} (counter-clockwise seen from outside), for the translucent list.
    final List<float[][]> tintPanes = new ArrayList<>();

    FusionPlantMesh(ModelFrame frame, int panelX, int panelY) {
        super(frame);
        this.panelX = panelX;
        this.panelY = panelY;
    }

    // The still parts and the nozzles of these ports: {solid, cutout, glow, running, tint}.
    public final float[][] buildStatic(List<Port> ports) {
        startStatic();
        runningFaces.clear();
        runningPaints.clear();
        tintPanes.clear();
        plinth();
        parts();
        panel(panelX, panelY);
        float[][] built = finish(ports);
        glow = new Floats();
        glowing = true;
        for (int i = 0; i < runningFaces.size(); i++) {
            float[][] f = runningFaces.get(i);
            face(f[0], f[1], f[2], f[3][0], f[3][1], runningPaints.get(i), false);
        }
        float[] running = glow.toArray();
        glow = new Floats();
        float tu = (Paint.RIVET.x + 2.0F) / SHEET_W, tv = (Paint.RIVET.y + 2.0F) / SHEET_H;
        for (float[][] p : tintPanes) {
            quad(p[0], p[1], p[2], p[3], new float[] { tu, tv, tu, tv, tu, tv, tu, tv }, false);
        }
        float[] tint = glow.toArray();
        glowing = false;
        return new float[][] { built[0], built[1], built[2], running, tint };
    }

    // What stands on the plinth.
    abstract void parts();

    // The control panel's cabinet, run back from the wall's face to what stands behind it (a round vessel sits well
    // inside the footprint, so a cabinet at the wall's face alone would float in front of it). Nothing within a block
    // behind it: it stands on the plinth as it is.
    @Override
    float @Nullable [] panelBox(int bx, int by) {
        float[] cabinet = super.panelBox(bx, by);
        if (cabinet == null) {
            return null;
        }
        float reach = 0.0F;
        boolean hit = false;
        float[] xs = { cabinet[0] + 0.25F, (cabinet[0] + cabinet[3]) / 2.0F, cabinet[3] - 0.25F };
        float[] ys = { cabinet[1] + 1.0F, (cabinet[1] + cabinet[4]) / 2.0F, cabinet[4] - 1.0F };
        for (float x : xs) {
            for (float y : ys) {
                float d = ray(new float[] { x, y, cabinet[5] }, new float[] { 0, 0, 1 });
                if (d >= 0.0F && d <= PX) {
                    hit = true;
                    reach = Math.max(reach, d);
                }
            }
        }
        if (hit && reach > 0.01F) {
            // Half a pixel into it, so no gap shows round a curved face.
            cabinet[5] += reach + 0.5F;
        }
        return cabinet;
    }

    // A face drawn only while the plant runs, a hair out from where it's given.
    void running(float[] origin, float[] right, float[] up, float w, float h, Paint paint) {
        float[] n = normalize(cross(right, up));
        float[] o = { origin[0] + n[0] * 0.05F, origin[1] + n[1] * 0.05F, origin[2] + n[2] * 0.05F };
        runningFaces.add(new float[][] { o, right, up, { w, h } });
        runningPaints.add(paint);
    }

    // A round vessel standing at (x, u) of radius r from y0 to y1: a short skirt under it, the shell plated in sections
    // with a flange band at each seam (every pitch px), and a head on top (elliptical, hh tall) or a flat cap.
    void vessel(float x, float u, float r, float y0, float y1, float pitch, float hh, int sides) {
        double start = Math.PI / sides;
        float skirt = Math.min(3.0F, (y1 - y0) / 6.0F);
        uprightTube(x, u, new float[][] { { y0, r - 1.0F }, { y0 + skirt, r - 1.0F } }, sides, start, Paint.BANDED, null);
        float bottom = y0 + skirt;
        float top = hh > 0.0F ? y1 - hh : y1;
        uprightTube(x, u, new float[][] { { bottom, r }, { top, r } }, sides, start, Paint.CASING, hh > 0.0F ? null : Paint.PLAIN);
        if (pitch > 0.0F) {
            for (float y = bottom + pitch; y < top - 3.0F; y += pitch) {
                band(x, u, r + 0.75F, y - 0.75F, y + 0.75F, sides);
            }
        }
        band(x, u, r + 0.75F, bottom, bottom + 1.5F, sides);
        if (hh > 0.0F) {
            band(x, u, r + 0.75F, top - 1.5F, top, sides);
            uprightTube(x, u, head(top, r, hh, true), sides, start, Paint.CASING, Paint.PLAIN);
        }
        uprights.add(new float[] { x, u, r, y0, y1 });
    }

    // A band round an upright part at (x, u): radius r from y0 to y1, its top a plain ring and its underside a shaded
    // ring back in to the part (so it can't be seen into from below).
    void band(float x, float u, float r, float y0, float y1, int sides) {
        double start = Math.PI / sides, step = 2.0 * Math.PI / sides;
        uprightTube(x, u, new float[][] { { y0, r }, { y1, r } }, sides, start, Paint.BANDED, Paint.PLAIN);
        float inner = Math.max(0.0F, r - 1.5F);
        float[] down = { 0, -1, 0 };
        for (int f = 0; f < sides; f++) {
            double a0 = start + f * step, a1 = a0 + step;
            float[] o0 = { x + (float) Math.cos(a0) * r, y0, u + (float) Math.sin(a0) * r };
            float[] o1 = { x + (float) Math.cos(a1) * r, y0, u + (float) Math.sin(a1) * r };
            float[] i0 = { x + (float) Math.cos(a0) * inner, y0, u + (float) Math.sin(a0) * inner };
            float[] i1 = { x + (float) Math.cos(a1) * inner, y0, u + (float) Math.sin(a1) * inner };
            orient(o0, o1, i1, i0, down, Paint.SHADE);
        }
    }

    // A vacuum-jacketed line through these points (px, in the frame): the jacket between them with bellows along it, a
    // flanged elbow block at each bend. Its ends run into what they join.
    void line(float[]... points) {
        for (int i = 0; i + 1 < points.length; i++) {
            float[] a = points[i], b = points[i + 1];
            tube(a, b, JACKET_R, PIPE_SIDES, Paint.BANDED);
            float[] d = sub(b, a);
            float len = length(d);
            float[] dir = normalize(d);
            for (float s = BELLOWS_PITCH / 2.0F + 2.0F; s + BELLOWS_L < len - 2.5F; s += BELLOWS_PITCH) {
                float[] p0 = { a[0] + dir[0] * s, a[1] + dir[1] * s, a[2] + dir[2] * s };
                float[] p1 = { p0[0] + dir[0] * BELLOWS_L, p0[1] + dir[1] * BELLOWS_L, p0[2] + dir[2] * BELLOWS_L };
                tube(p0, p1, BELLOWS_R, PIPE_SIDES, Paint.BANDED);
                tubeCap(p0, p1, BELLOWS_R, PIPE_SIDES, false, Paint.MID, Paint.SHADE);
                tubeCap(p0, p1, BELLOWS_R, PIPE_SIDES, true, Paint.MID, Paint.SHADE);
            }
            if (i > 0) {
                float h = JACKET_R + 0.6F;
                box(a[0] - h, a[1] - h, a[2] - h, a[0] + h, a[1] + h, a[2] + h, Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, 0);
            }
        }
    }

    // A sight glass on a round part at (x, u) radius r, facing angle a (radians round from +x toward +u): a boss with
    // a dark window, its level lit while the plant runs.
    void sightGlass(float x, float u, float r, float a, float y, float w, float h) {
        float[] n = { (float) Math.cos(a), 0.0F, (float) Math.sin(a) };
        float[][] axes = plane(n);
        float[] right = axes[0], up = axes[1];
        float[] centre = { x + n[0] * (r + 1.0F), y, u + n[2] * (r + 1.0F) };
        // The frame round it, then the window just in front of it.
        float fw = w + 2.0F, fh = h + 2.0F;
        float[] base = { x + n[0] * (r - 0.5F), y, u + n[2] * (r - 0.5F) };
        float[] b0 = at(base, right, up, -fw / 2, -fh / 2), b1 = at(base, right, up, fw / 2, -fh / 2), b2 = at(base, right, up, fw / 2, fh / 2),
                b3 = at(base, right, up, -fw / 2, fh / 2);
        float[] c0 = at(centre, right, up, -fw / 2, -fh / 2), c1 = at(centre, right, up, fw / 2, -fh / 2), c2 = at(centre, right, up, fw / 2, fh / 2),
                c3 = at(centre, right, up, -fw / 2, fh / 2);
        orient(b0, b1, c1, c0, new float[] { 0, -1, 0 }, Paint.SHADE);
        orient(b3, b2, c2, c3, new float[] { 0, 1, 0 }, Paint.MID);
        orient(b0, b3, c3, c0, new float[] { -right[0], -right[1], -right[2] }, Paint.FACE);
        orient(b1, b2, c2, c1, right, Paint.FACE_SHADE);
        face(at(centre, right, up, -fw / 2, -fh / 2), right, up, fw, fh, Paint.MID, false);
        float[] win = { centre[0] + n[0] * 0.1F, centre[1], centre[2] + n[2] * 0.1F };
        face(at(win, right, up, -w / 2, -h / 2), right, up, w, h, Paint.M_CORE_DARK, false);
        running(at(win, right, up, -w / 2, -h / 2), right, up, w, h * 0.7F, Paint.M_CORE_LIT);
    }

    // A window into a box facing n (axis-aligned, in the xz plane) at its face point: a recess depth deep, w x h, its
    // inside dark, a lamp over it lit while the plant runs, a glass pane at its mouth and a tint pane just behind.
    void window(float[] centre, float[] n, float w, float h, float depth) {
        float[][] axes = plane(n);
        float[] right = axes[0], up = axes[1];
        float[] back = { centre[0] - n[0] * depth, centre[1], centre[2] - n[2] * depth };
        // The back and the four sides of the recess, facing into it.
        face(at(back, right, up, -w / 2, -h / 2), right, up, w, h, Paint.DARK, false);
        float[] m0 = at(centre, right, up, -w / 2, -h / 2), m1 = at(centre, right, up, w / 2, -h / 2), m2 = at(centre, right, up, w / 2, h / 2),
                m3 = at(centre, right, up, -w / 2, h / 2);
        float[] k0 = at(back, right, up, -w / 2, -h / 2), k1 = at(back, right, up, w / 2, -h / 2), k2 = at(back, right, up, w / 2, h / 2),
                k3 = at(back, right, up, -w / 2, h / 2);
        orient(k0, k1, m1, m0, new float[] { 0, 1, 0 }, Paint.SHADE);
        orient(k3, k2, m2, m3, new float[] { 0, -1, 0 }, Paint.DEEP);
        orient(k0, k3, m3, m0, right, Paint.DARK);
        orient(k1, k2, m2, m1, new float[] { -right[0], -right[1], -right[2] }, Paint.SHADE);
        // The lamp along the top of the recess, facing down.
        float lw = Math.min(w - 2.0F, 16.0F), ld = Math.min(depth - 1.0F, 8.0F);
        float[] lamp = { centre[0] - n[0] * (depth - 0.5F), centre[1] + h / 2 - 0.1F, centre[2] - n[2] * (depth - 0.5F) };
        float[] across = right, toward = { n[0], 0, n[2] };
        // Seen from below: across, then toward the window (its up).
        float[] origin = { lamp[0] - across[0] * lw / 2, lamp[1], lamp[2] - across[2] * lw / 2 };
        face(origin, across, toward, lw, ld, Paint.DARK, false);
        running(origin, across, toward, lw, ld, Paint.M_LAMP);
        // The glass at the mouth and its tint a hair behind.
        float[] mouth = { centre[0] + n[0] * 0.02F, centre[1], centre[2] + n[2] * 0.02F };
        glassPane(at(mouth, right, up, -w / 2, -h / 2), at(mouth, right, up, w / 2, -h / 2), at(mouth, right, up, -w / 2, h / 2), w, h);
        float[] tint = { centre[0] - n[0] * 0.3F, centre[1], centre[2] - n[2] * 0.3F };
        tintPanes.add(new float[][] { at(tint, right, up, -w / 2, -h / 2), at(tint, right, up, w / 2, -h / 2), at(tint, right, up, w / 2, h / 2),
                at(tint, right, up, -w / 2, h / 2) });
    }

    // A part standing out of a round part at (x, u) radius r at angle a (radians round from +x toward +u): a block w
    // across and h up centred at y, from in px inside the radius out to out px beyond it (see VesselMesh.boss).
    void stub(float x, float u, float a, float r, float y, float w, float h, float in, float out, Paint face, Paint sides) {
        float[] n = { (float) Math.cos(a), 0.0F, (float) Math.sin(a) };
        float[][] axes = plane(n);
        float[] right = axes[0], up = axes[1];
        float[] centre = { x + n[0] * (r + out), y, u + n[2] * (r + out) };
        float hw = w / 2.0F, hh = h / 2.0F, depth = out + in;
        face(at(centre, right, up, -hw, -hh), right, up, w, h, face, false);
        float[] back = { -n[0] * depth, 0.0F, -n[2] * depth };
        float[][] rim = { at(centre, right, up, -hw, -hh), at(centre, right, up, hw, -hh), at(centre, right, up, hw, hh), at(centre, right, up, -hw, hh) };
        float[][] outward = { { -up[0], -up[1], -up[2] }, right, up, { -right[0], -right[1], -right[2] } };
        for (int i = 0; i < 4; i++) {
            float[] a0 = rim[i], a1 = rim[(i + 1) % 4];
            float[] b0 = { a0[0] + back[0], a0[1], a0[2] + back[2] }, b1 = { a1[0] + back[0], a1[1], a1[2] + back[2] };
            orient(b0, b1, a1, a0, outward[i], sides);
        }
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
