/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;

import net.zagdrath.arcforge.multiblock.DieselGeneratorLayout;
import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Diesel Generator's model, from DieselGeneratorLayout (see DieselGeneratorRenderer), on the Pumpjack's sheet (it
// has the generator steel, the FE band and the louvres): the still parts once (and again when its ports change); the
// fan and the coupling every frame at the flywheel's angle. How it's built and painted: ModelMesh.
public final class DieselGeneratorMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 256;
    private static final float CX = DieselGeneratorLayout.CX, SY = DieselGeneratorLayout.SHAFT_Y;

    // While building a turning part: its turn about the shaft line (along u).
    private boolean spinning;
    private double spinCos = 1.0, spinSin = 0.0;

    public DieselGeneratorMesh(ModelFrame frame) {
        super(frame.alongX(), frame.flip(), DieselGeneratorLayout.WIDTH, DieselGeneratorLayout.LENGTH, 0.0F, 0.0F, SHEET_W, SHEET_H);
        this.modules = true;
    }

    // The still parts and the nozzles of these ports: {solid, cutout, glow}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        skid();
        radiator();
        engine();
        generator();
        for (Port port : ports) {
            nozzle(port);
        }
        return new float[][] { solid.toArray(), cutout.toArray(), glow.toArray() };
    }

    // The skid: two rails, cross members and the engine's mounts.
    private void skid() {
        float top = DieselGeneratorLayout.SKID_TOP, length = DieselGeneratorLayout.LENGTH;
        for (float[] rail : DieselGeneratorLayout.RAILS) {
            box(rail[0], 0.0F, 0.0F, rail[1], top, length, Paint.STRAP, Paint.MID, Paint.DARK, 0);
            solidBoxes.add(new float[] { rail[0], 0.0F, 0.0F, rail[1], top, length });
        }
        float[][] rails = DieselGeneratorLayout.RAILS;
        for (float[] cross : DieselGeneratorLayout.CROSS) {
            box(rails[0][1], 0.0F, cross[0], rails[1][0], 3.0F, cross[1], Paint.FACE, Paint.MID, Paint.DARK, 0);
        }
        float[] c = DieselGeneratorLayout.CRANKCASE;
        for (float u : new float[] { c[2] + 3.0F, c[5] - 3.0F }) {
            for (float x : new float[] { c[0] - 3.0F, c[3] + 3.0F }) {
                float x0 = Math.min(x, x < DieselGeneratorLayout.CX ? c[0] : c[3]), x1 = Math.max(x, x < DieselGeneratorLayout.CX ? c[0] : c[3]);
                box(x0, top, u - 2.0F, x1, c[1] + 2.0F, u + 2.0F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
            }
        }
    }

    // The radiator: a plated core with a louvred grille across its front, header tanks top and bottom, and the fan's
    // shroud.
    private void radiator() {
        float[] r = DieselGeneratorLayout.RADIATOR;
        box(r[0], r[1], r[2], r[3], r[4], r[5], Paint.PLATE, Paint.DECK, null, SKIP_DOWN);
        solidBoxes.add(r);
        float[] normal = { 0, 0, -1 };
        float[][] axes = plane(normal);
        float[] right = axes[0], up = axes[1];
        float inset = 2.0F;
        float x0 = right[0] > 0 ? r[0] + inset : r[3] - inset;
        face(new float[] { x0, r[1] + inset + 2.0F, r[2] - 0.05F }, right, up, r[3] - r[0] - 2 * inset, r[4] - r[1] - 2 * inset - 2.0F, Paint.VENT, false);
        box(r[0] + 1.0F, r[4], r[2] + 1.0F, r[3] - 1.0F, r[4] + 2.0F, r[5] - 1.0F, Paint.BANDED, Paint.MID, null, SKIP_DOWN);
        // The filler cap.
        box(r[3] - 7.0F, r[4] + 2.0F, (r[2] + r[5]) / 2.0F - 1.0F, r[3] - 5.0F, r[4] + 3.0F, (r[2] + r[5]) / 2.0F + 1.0F, Paint.MID, Paint.LIT, null,
                SKIP_DOWN);
        float fr = DieselGeneratorLayout.FAN_R + 1.0F;
        tube(new float[] { CX, SY, DieselGeneratorLayout.SHROUD_U0 }, new float[] { CX, SY, DieselGeneratorLayout.SHROUD_U1 }, fr, 12, Paint.BANDED);
        solidCylinders.add(new float[] { CX, SY, fr, DieselGeneratorLayout.SHROUD_U0, DieselGeneratorLayout.SHROUD_U1 });
    }

    // The engine block: the oil pan, crankcase, head and rocker covers, manifolds, air filter, turbocharger and stack.
    private void engine() {
        float[] pan = DieselGeneratorLayout.OIL_PAN, c = DieselGeneratorLayout.CRANKCASE, h = DieselGeneratorLayout.HEAD;
        box(pan[0], pan[1], pan[2], pan[3], pan[4], pan[5], Paint.FACE_SHADE, null, Paint.DARK, SKIP_UP);
        box(c[0], c[1], c[2], c[3], c[4], c[5], Paint.PLATE, null, Paint.SHADE, SKIP_UP);
        box(h[0], h[1], h[2], h[3], h[4], h[5], Paint.PLAIN, Paint.MID, Paint.DARK, 0);
        solidBoxes.add(c);
        solidBoxes.add(h);
        // A flange line where the head meets the block, with head bolts along both sides.
        box(c[0] - 0.4F, c[4] - 1.0F, c[2] - 0.4F, c[3] + 0.4F, c[4], c[5] + 0.4F, Paint.MID, Paint.LIT, Paint.DARK, 0);
        for (float[] cover : DieselGeneratorLayout.covers()) {
            box(DieselGeneratorLayout.COVER_X0, h[4], cover[0], DieselGeneratorLayout.COVER_X1, DieselGeneratorLayout.COVER_TOP, cover[1], Paint.MID, Paint.LIT,
                    null, SKIP_DOWN);
            float mid = (cover[0] + cover[1]) / 2.0F;
            for (float x : new float[] { DieselGeneratorLayout.COVER_X0 + 1.5F, DieselGeneratorLayout.COVER_X1 - 1.5F }) {
                box(x - 0.5F, DieselGeneratorLayout.COVER_TOP, mid - 0.5F, x + 0.5F, DieselGeneratorLayout.COVER_TOP + 0.5F, mid + 0.5F, Paint.RIVET,
                        Paint.RIVET, null, SKIP_DOWN);
            }
            // The injector line into each cylinder.
            tube(new float[] { DieselGeneratorLayout.COVER_X1 + 0.5F, h[4] - 0.5F, mid }, new float[] { h[3] + 0.6F, h[4] - 0.5F, mid }, 0.35F, 4, Paint.MID);
        }
        float[] in = DieselGeneratorLayout.INTAKE, ex = DieselGeneratorLayout.EXHAUST, t = DieselGeneratorLayout.TURBO;
        box(in[0], in[1], in[2], in[3], in[4], in[5], Paint.BANDED, Paint.MID, Paint.SHADE, 0);
        box(ex[0], ex[1], ex[2], ex[3], ex[4], ex[5], Paint.FACE_SHADE, Paint.FACE, Paint.DARK, 0);
        box(t[0], t[1], t[2], t[3], t[4], t[5], Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
        solidBoxes.add(in);
        solidBoxes.add(t);
        // The air filter: a canister standing at the front left, piped into the intake.
        float fx = DieselGeneratorLayout.FILTER_X, fu = DieselGeneratorLayout.FILTER_U, fr = DieselGeneratorLayout.FILTER_R;
        uprightTube(fx, fu, new float[][] { { DieselGeneratorLayout.FILTER_Y0, fr }, { DieselGeneratorLayout.FILTER_Y1, fr } }, 12, 0.0, Paint.BANDED, Paint.MID);
        uprightTube(fx, fu, new float[][] { { DieselGeneratorLayout.FILTER_Y1 - 3.0F, fr + 0.4F }, { DieselGeneratorLayout.FILTER_Y1 - 2.0F, fr + 0.4F } }, 12,
                0.0, Paint.BANDED, Paint.MID);
        box(fx - 1.0F, DieselGeneratorLayout.FILTER_Y0 - 3.0F, fu - 1.0F, fx + 1.0F, DieselGeneratorLayout.FILTER_Y0, fu + 1.0F, Paint.SHADE, null, null,
                SKIP_UP | SKIP_DOWN);
        tube(new float[] { fx, 19.0F, fu }, new float[] { in[0], 19.0F, fu }, 1.2F, PIPE_SIDES, Paint.BANDED);
        uprights.add(new float[] { fx, fu, fr, DieselGeneratorLayout.FILTER_Y0, DieselGeneratorLayout.FILTER_Y1 });
        // The exhaust stack with its rain cap.
        float sx = DieselGeneratorLayout.STACK_X, su = DieselGeneratorLayout.STACK_U, sr = DieselGeneratorLayout.STACK_R, top = DieselGeneratorLayout.STACK_TOP;
        uprightTube(sx, su, new float[][] { { t[4], sr }, { top, sr } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, null);
        uprightTube(sx, su, new float[][] { { t[4] + 3.0F, sr + 0.8F }, { t[4] + 9.0F, sr + 0.8F } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED,
                Paint.MID);
        uprightTube(sx, su, new float[][] { { top - 0.5F, sr + 0.4F }, { top, sr + 0.4F } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, Paint.DEEP);
        box(sx - sr - 0.6F, top + 0.5F, su - sr - 0.6F, sx + sr + 0.6F, top + 1.0F, su + sr + 0.6F, Paint.MID, Paint.LIT, Paint.DARK, 0);
        uprights.add(new float[] { sx, su, sr, t[4], top });
    }

    // The flywheel housing, the alternator (louvred generator steel with its FE band), its end bell and terminal box.
    private void generator() {
        float r = DieselGeneratorLayout.BELL_R, u0 = DieselGeneratorLayout.BELL_U0, u1 = DieselGeneratorLayout.BELL_U1;
        tube(new float[] { CX, SY, u0 }, new float[] { CX, SY, u1 }, r, 16, Paint.BANDED);
        disc(new float[] { CX, SY, u0 }, new float[] { 0, 0, -1 }, r, 16, Paint.PLAIN, Paint.SHADE);
        disc(new float[] { CX, SY, u1 }, new float[] { 0, 0, 1 }, r, 16, Paint.PLAIN, Paint.SHADE);
        solidCylinders.add(new float[] { CX, SY, r, u0, u1 });
        float[] a = DieselGeneratorLayout.ALTERNATOR;
        box(a[0], a[1], a[2], a[3], a[4], a[5], Paint.GEN_PLATE, Paint.GEN_PLAIN, null, SKIP_DOWN);
        solidBoxes.add(a);
        // Louvres down both sides.
        for (int side = 0; side < 2; side++) {
            float[] normal = { side == 0 ? -1 : 1, 0, 0 };
            float[][] axes = plane(normal);
            float[] right = axes[0], up = axes[1];
            float x = side == 0 ? a[0] - 0.05F : a[3] + 0.05F;
            float w = 8.0F;
            float start = right[2] > 0 ? a[2] + 3.0F : a[5] - 3.0F;
            face(new float[] { x, a[1] + 6.0F, start }, right, up, w, 12.0F, Paint.VENT, false);
        }
        // The FE band round its middle.
        float band = (a[2] + a[5]) / 2.0F + 3.0F;
        box(a[0] - 0.25F, a[1] + 2.0F, band - 1.0F, a[3] + 0.25F, a[4] + 0.25F, band + 1.0F, Paint.FE, Paint.FE_LIT, null, SKIP_DOWN);
        // The end bell.
        float bu = DieselGeneratorLayout.ALT_BELL_U;
        box(a[0] + 2.0F, a[1] + 2.0F, a[5], a[3] - 2.0F, a[4] - 2.0F, bu, Paint.GEN_PLAIN, Paint.GEN_PLAIN, Paint.SHADE, 0);
        // The terminal box, with its cable glands in FE red.
        float[] t = DieselGeneratorLayout.TERMINAL;
        box(t[0], t[1], t[2], t[3], t[4], t[5], Paint.PLATE, Paint.MID, null, SKIP_DOWN);
        for (int i = 0; i < 3; i++) {
            float gx = t[0] + 3.0F + i * 4.0F;
            box(gx - 1.0F, t[1] + 1.0F, t[5], gx + 1.0F, t[1] + 3.0F, t[5] + 1.5F, Paint.FE, Paint.FE_LIT, null, 0);
        }
        solidBoxes.add(t);
    }

    // --- Each frame ---

    // The fan's blades and hub, and the coupling, turned to angle degrees.
    public float[] buildMoving(float angle) {
        solid = new Floats();
        glowing = false;
        spinning = true;
        spinCos = Math.cos(Math.toRadians(angle));
        spinSin = Math.sin(Math.toRadians(angle));
        float u = DieselGeneratorLayout.FAN_U, reach = DieselGeneratorLayout.FAN_R - 0.5F;
        for (int blade = 0; blade < 5; blade++) {
            double a = 2.0 * Math.PI * blade / 5.0;
            float c = (float) Math.cos(a), s = (float) Math.sin(a), w = 1.5F;
            float[] p0 = { CX + c * 2.0F - s * w, SY + s * 2.0F + c * w, u };
            float[] p1 = { CX + c * reach - s * w, SY + s * reach + c * w, u };
            float[] p2 = { CX + c * reach + s * w, SY + s * reach - c * w, u };
            float[] p3 = { CX + c * 2.0F + s * w, SY + s * 2.0F - c * w, u };
            orient(p0, p1, p2, p3, new float[] { 0, 0, -1 }, Paint.BLADE);
            orient(p0, p1, p2, p3, new float[] { 0, 0, 1 }, Paint.BLADE_BACK);
        }
        tube(new float[] { CX, SY, u - 1.0F }, new float[] { CX, SY, u + 1.0F }, 2.2F, 8, Paint.MID);
        tube(new float[] { CX, SY, DieselGeneratorLayout.SHROUD_U1 }, new float[] { CX, SY, DieselGeneratorLayout.CRANKCASE[2] }, 1.0F, 6, Paint.SHADE);
        // The coupling: a short drum with bolt heads round it.
        float r = DieselGeneratorLayout.COUPLING_R, c0 = DieselGeneratorLayout.COUPLING_U0, c1 = DieselGeneratorLayout.COUPLING_U1;
        tube(new float[] { CX, SY, c0 }, new float[] { CX, SY, c1 }, r, 8, Paint.BANDED);
        for (int i = 0; i < 6; i++) {
            double a = 2.0 * Math.PI * i / 6.0;
            float bx = CX + (float) Math.cos(a) * (r + 0.3F), by = SY + (float) Math.sin(a) * (r + 0.3F), bu = (c0 + c1) / 2.0F;
            box(bx - 0.5F, by - 0.5F, bu - 0.5F, bx + 0.5F, by + 0.5F, bu + 0.5F, Paint.RIVET, Paint.RIVET, Paint.DARK, 0);
        }
        spinning = false;
        return solid.toArray();
    }

    // Turning parts turn about the shaft line.
    @Override
    float[] turn(float[] p) {
        if (!spinning) {
            return p;
        }
        float dx = p[0] - CX, dy = p[1] - SY;
        return new float[] { CX + (float) (dx * spinCos - dy * spinSin), SY + (float) (dx * spinSin + dy * spinCos), p[2] };
    }
}
