/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.PumpjackLayout;

// The Pumpjack's model, from PumpjackLayout (see PumpjackRenderer), on the sheet block/pumpjack/pumpjack: the still
// parts once (and again when its ports change); the moving ones every frame for the crank's angle: the walking beam
// with its horse head and equalizer (rocking about the saddle bearing), the pitman arms, the cranks with their big
// counterweights (hazard-striped along their curved edge), and the bridle, carrier bar and polished rod. How it's
// built and painted: ModelMesh.
public final class PumpjackMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 96;
    private static final float CX = PumpjackLayout.CX, PY = PumpjackLayout.PIVOT_Y, PU = PumpjackLayout.PIVOT_U;

    // While building a moving part: its turn in the y-u plane (positive lifts what's toward u = 0) about (pivotY,
    // pivotU).
    private boolean turning;
    private double turnCos = 1.0, turnSin = 0.0;
    private float pivotY, pivotU;

    public PumpjackMesh(ModelFrame frame) {
        super(frame.alongX(), frame.flip(), PumpjackLayout.WIDTH, PumpjackLayout.LENGTH, 0.0F, 0.0F, SHEET_W, SHEET_H);
        this.broadShading = true;
    }

    // The still parts and the nozzles of these ports: {solid, cutout, glow}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        base();
        samsonPost();
        platform();
        gearbox();
        for (Port port : ports) {
            nozzle(port);
        }
        return new float[][] { solid.toArray(), cutout.toArray(), glow.toArray() };
    }

    // The skid rails (past the well) and the deep plinth the rest stands on.
    private void base() {
        float top = PumpjackLayout.SKID_TOP;
        for (float[] rail : PumpjackLayout.RAILS) {
            box(rail[0], 0.0F, 1.0F, rail[1], top, PumpjackLayout.LENGTH - 1.0F, Paint.STRAP, Paint.MID, Paint.DARK, 0);
        }
        for (float[] cross : PumpjackLayout.CROSS) {
            box(PumpjackLayout.RAILS[0][1], 0.0F, cross[0], PumpjackLayout.RAILS[1][0], 3.0F, cross[1], Paint.FACE, Paint.MID, Paint.DARK, 0);
        }
        float[] p = PumpjackLayout.PLINTH;
        box(p[0], p[1], p[2], p[3], p[4], p[5], Paint.PLATE, Paint.DECK, Paint.FOOTING, 0);
        // A lip round the plinth's top edge.
        box(p[0] - 0.5F, p[4] - 1.0F, p[2] - 0.5F, p[3] + 0.5F, p[4], p[5] + 0.5F, Paint.MID, null, Paint.DARK, SKIP_UP);
        solidBoxes.addAll(PumpjackLayout.solids());
    }

    // The A-frame: four legs from the plinth to the saddle bearing, braced along each side and across the front and
    // back low down, and the saddle.
    private void samsonPost() {
        float base = PumpjackLayout.BASE_TOP, top = PumpjackLayout.LEG_TOP;
        for (float[] leg : PumpjackLayout.LEGS) {
            tube(new float[] { leg[0], base, leg[1] }, new float[] { leg[2], top, leg[3] }, PumpjackLayout.LEG_RADIUS, 4, Paint.BANDED);
            box(leg[0] - 2.5F, base, leg[1] - 2.5F, leg[0] + 2.5F, base + 1.0F, leg[1] + 2.5F, Paint.MID, Paint.LIT, null, SKIP_DOWN);
        }
        // Along each side, two braces; across the front and the back, one low down (clear of the horse head's swing).
        for (int side = 0; side < 2; side++) {
            float[] front = PumpjackLayout.LEGS[side], back = PumpjackLayout.LEGS[side + 2];
            for (float t : new float[] { 0.3F, 0.65F }) {
                tube(along(front, t), along(back, t), 0.9F, 4, Paint.BANDED);
            }
        }
        tube(along(PumpjackLayout.LEGS[0], 0.25F), along(PumpjackLayout.LEGS[1], 0.25F), 0.9F, 4, Paint.BANDED);
        tube(along(PumpjackLayout.LEGS[2], 0.25F), along(PumpjackLayout.LEGS[3], 0.25F), 0.9F, 4, Paint.BANDED);
        float[] s = PumpjackLayout.SADDLE;
        box(s[0], s[1], s[2], s[3], s[4], s[5], Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
        prismX(PU, PY - 1.0F, s[0] - 1.0F, s[3] + 1.0F, 1.5F, Paint.BANDED, Paint.MID);
    }

    // A point t of the way up a leg.
    private static float[] along(float[] leg, float t) {
        float base = PumpjackLayout.BASE_TOP, top = PumpjackLayout.LEG_TOP;
        return new float[] { leg[0] + (leg[2] - leg[0]) * t, base + (top - base) * t, leg[1] + (leg[3] - leg[1]) * t };
    }

    // The platform round the saddle (grating, open over the beam), railings along both sides (open at the ladder) and
    // the ladder up the far side from the rail.
    private void platform() {
        float[] p = PumpjackLayout.PLATFORM;
        float x0 = p[0], y = p[1], u0 = p[2], x1 = p[3], u1 = p[4], rail = PumpjackLayout.RAIL_HEIGHT;
        box(x0, y - 1.5F, u0, x1, y, u1, Paint.MID, Paint.GRATE, Paint.SHADE, 0);
        float lu0 = PumpjackLayout.LADDER_U0, lu1 = PumpjackLayout.LADDER_U1;
        railing(x0 + 0.5F, u0, u1, y);
        railing(x1 - 0.5F, u0, lu0 - 1.0F, y);
        railing(x1 - 0.5F, lu1 + 1.0F, u1, y);
        float lx = PumpjackLayout.LADDER_X, low = PumpjackLayout.SKID_TOP, high = y + rail;
        for (float u : new float[] { lu0, lu1 }) {
            tube(new float[] { lx, low, u }, new float[] { lx, high, u }, 0.5F, 4, Paint.BANDED);
        }
        for (float ry = low + 3.0F; ry < high - 1.0F; ry += 4.0F) {
            tube(new float[] { lx, ry, lu0 }, new float[] { lx, ry, lu1 }, 0.35F, 4, Paint.MID);
        }
    }

    // A railing along u at x, from u0 to u1, on a deck at y: posts at most 7 px apart, a knee rail and a hand rail.
    private void railing(float x, float u0, float u1, float y) {
        float rail = PumpjackLayout.RAIL_HEIGHT;
        box(x - 0.5F, y + rail - 1.0F, u0, x + 0.5F, y + rail, u1, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        box(x - 0.4F, y + rail / 2.0F, u0, x + 0.4F, y + rail / 2.0F + 0.75F, u1, Paint.FACE, Paint.MID, Paint.SHADE, 0);
        int posts = Math.max(1, (int) Math.ceil((u1 - u0) / 7.0F));
        for (int i = 0; i <= posts; i++) {
            float u = u0 + Math.min(u1 - u0 - 1.0F, (u1 - u0) * i / posts);
            box(x - 0.5F, y, u, x + 0.5F, y + rail - 1.0F, u + 1.0F, Paint.FACE, null, null, SKIP_UP | SKIP_DOWN);
        }
    }

    // The gear reducer (a box with a half-round top about the crankshaft, on a footing), the crankshaft's ends, the
    // motor (with its FE band) and the belt guard between them.
    private void gearbox() {
        float[] g = PumpjackLayout.GEARBOX, m = PumpjackLayout.MOTOR, b = PumpjackLayout.GUARD;
        float cu = PumpjackLayout.CRANK_U, cy = PumpjackLayout.CRANK_Y;
        box(g[0], g[1], g[2], g[3], g[4], g[5], Paint.PLATE, null, Paint.FOOTING, SKIP_UP);
        box(g[0] - 1.0F, g[1], g[2] - 1.0F, g[3] + 1.0F, g[1] + 1.5F, g[5] + 1.0F, Paint.MID, Paint.LIT, null, SKIP_DOWN);
        prismX(cu, PumpjackLayout.GEARBOX_TOP, g[0], g[3], PumpjackLayout.GEARBOX_ROUND, Paint.BANDED, Paint.PLAIN);
        // The split line round the housing at the shaft, and the bearing caps either side.
        prismX(cu, cy, g[0] - 1.0F, g[3] + 1.0F, 3.0F, Paint.BANDED, Paint.MID);
        prismX(cu, cy, PumpjackLayout.CRANK_X - 2.0F, PumpjackLayout.WIDTH - PumpjackLayout.CRANK_X + 2.0F, 1.6F, Paint.BANDED, Paint.MID);
        box(m[0], m[1], m[2], m[3], m[4], m[5], Paint.GEN_PLATE, Paint.GEN_PLAIN, Paint.SHADE, 0);
        float band = (m[2] + m[5]) / 2.0F;
        box(m[0] - 0.25F, m[1] + 2.0F, band - 1.0F, m[3] + 0.25F, m[4] + 0.25F, band + 1.0F, Paint.FE, Paint.FE_LIT, null, SKIP_DOWN);
        box(b[0], b[1], b[2], b[3], b[4], b[5], Paint.PLATE, Paint.MID, Paint.SHADE, 0);
    }

    // A sector of a ring in the y-u plane about (centreY, centreU), from x0 to x1, between angles a0 and a1 (radians;
    // 0 points toward u = 0, positive turns up): its inner radius at each angle, its outer radius. Its two sides, the
    // outer face, the inner face (skipped where the inner radius is under 0.5 px) and both ends.
    private void ringSector(float x0, float x1, float centreY, float centreU, double a0, double a1, DoubleUnaryOperator inner,
            float outer, int steps, Paint side, Paint face, Paint back) {
        for (int i = 0; i < steps; i++) {
            double b0 = a0 + (a1 - a0) * i / steps, b1 = a0 + (a1 - a0) * (i + 1) / steps, mid = (b0 + b1) / 2.0;
            float r0 = (float) inner.applyAsDouble(b0), r1 = (float) inner.applyAsDouble(b1);
            float[] o0 = at(centreY, centreU, b0, outer), o1 = at(centreY, centreU, b1, outer);
            float[] i0 = at(centreY, centreU, b0, r0), i1 = at(centreY, centreU, b1, r1);
            orient(xyz(x0, i0), xyz(x0, o0), xyz(x0, o1), xyz(x0, i1), new float[] { -1, 0, 0 }, side);
            orient(xyz(x1, i0), xyz(x1, o0), xyz(x1, o1), xyz(x1, i1), new float[] { 1, 0, 0 }, side);
            float[] n = { 0, (float) Math.sin(mid), -(float) Math.cos(mid) };
            orient(xyz(x0, o0), xyz(x1, o0), xyz(x1, o1), xyz(x0, o1), n, face);
            if (r0 > 0.5F || r1 > 0.5F) {
                orient(xyz(x0, i0), xyz(x1, i0), xyz(x1, i1), xyz(x0, i1), new float[] { 0, -n[1], -n[2] }, back);
            }
        }
        for (int end = 0; end < 2; end++) {
            double a = end == 0 ? a0 : a1;
            float[] o = at(centreY, centreU, a, outer), in = at(centreY, centreU, a, (float) inner.applyAsDouble(a));
            // Outward along the turn: back past a0, on past a1.
            float sign = end == 0 ? -1.0F : 1.0F;
            float[] n = { 0, sign * (float) Math.cos(a), sign * (float) Math.sin(a) };
            orient(xyz(x0, in), xyz(x1, in), xyz(x1, o), xyz(x0, o), n, Paint.MID);
        }
    }

    // The point (y, u) at radius r and angle a about (centreY, centreU).
    private static float[] at(float centreY, float centreU, double a, float r) {
        return new float[] { centreY + (float) Math.sin(a) * r, centreU - (float) Math.cos(a) * r };
    }

    private static float[] xyz(float x, float[] yu) {
        return new float[] { x, yu[0], yu[1] };
    }

    // --- Each frame ---

    // The moving parts with the crank at theta (radians): the beam (rocked to match), the arms, the cranks and the
    // bridle.
    public float[] buildMoving(double theta) {
        solid = new Floats();
        glowing = false;
        double phi = PumpjackLayout.beamAngle(theta);
        beam(phi);
        cranks(theta);
        float[] pin = PumpjackLayout.crankPin(theta), eq = PumpjackLayout.equalizer(phi);
        for (float arm : new float[] { PumpjackLayout.PITMAN_X, PumpjackLayout.WIDTH - PumpjackLayout.PITMAN_X }) {
            tube(new float[] { arm, pin[0], pin[1] }, new float[] { arm, eq[0] - 2.5F, eq[1] }, 1.1F, 4, Paint.BANDED);
        }
        bridle(phi);
        return solid.toArray();
    }

    private void turnAbout(float y, float u, double angle) {
        turning = true;
        pivotY = y;
        pivotU = u;
        turnCos = Math.cos(angle);
        turnSin = Math.sin(angle);
    }

    // The walking beam, its horse head and the equalizer, turned by phi about the saddle bearing.
    private void beam(double phi) {
        turnAbout(PY, PU, phi);
        float h = PumpjackLayout.BEAM_HALF;
        box(CX - h, PY, PumpjackLayout.BEAM_FRONT, CX + h, PY + PumpjackLayout.BEAM_DEPTH, PumpjackLayout.BEAM_BACK, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
        // Flanges along the top and bottom of the girder.
        box(CX - h - 1.0F, PY + PumpjackLayout.BEAM_DEPTH - 1.0F, PumpjackLayout.BEAM_FRONT, CX + h + 1.0F, PY + PumpjackLayout.BEAM_DEPTH,
                PumpjackLayout.BEAM_BACK, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        box(CX - h - 1.0F, PY, PumpjackLayout.BEAM_FRONT, CX + h + 1.0F, PY + 1.0F, PumpjackLayout.BEAM_BACK, Paint.FACE_SHADE, Paint.MID, Paint.DARK, 0);
        horseHead();
        // The equalizer: a crossbar under the beam's far end, out to the arms on both sides.
        float eu = PU + PumpjackLayout.EQUALIZER;
        float ex = PumpjackLayout.PITMAN_X - 1.5F;
        box(ex, PY - 3.0F, eu - 1.5F, PumpjackLayout.WIDTH - ex, PY, eu + 1.5F, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
        // The equalizer bearing on the beam's underside.
        box(CX - 3.5F, PY - 0.5F, eu - 2.5F, CX + 3.5F, PY + 0.5F, eu + 2.5F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        turning = false;
    }

    // The horse head: a ring sector about the pivot, its curved face (where the bridle runs) at HEAD_RADIUS, deepest
    // where the beam joins it and tapering to both tips, with a row of lightening holes in each side plate.
    private void horseHead() {
        float outer = PumpjackLayout.HEAD_RADIUS, h = PumpjackLayout.HEAD_HALF;
        ringSector(CX - h, CX + h, PY, PU, -PumpjackLayout.HEAD_BELOW, PumpjackLayout.HEAD_ABOVE,
                a -> outer - PumpjackLayout.headDepth(a), outer, 14, Paint.FACE, Paint.DARK, Paint.SHADE);
        for (double deg : new double[] { -24.0, -12.0, 0.0, 12.0 }) {
            double a = Math.toRadians(deg);
            float r = outer - PumpjackLayout.headDepth(a) * 0.45F;
            float[] c = at(PY, PU, a, r);
            for (int side = -1; side <= 1; side += 2) {
                float x = CX + side * (h + 0.05F);
                orient(new float[] { x, c[0] - 0.8F, c[1] - 0.8F }, new float[] { x, c[0] + 0.8F, c[1] - 0.8F }, new float[] { x, c[0] + 0.8F, c[1] + 0.8F },
                        new float[] { x, c[0] - 0.8F, c[1] + 0.8F }, new float[] { side, 0, 0 }, Paint.DEEP);
            }
        }
    }

    // The two cranks, turned by theta about the crankshaft: each an arm out past its pin, with a big counterweight on
    // the pin's side (a ring sector about the shaft, hazard-striped along its curved edge) and the pin on its outside.
    private void cranks(double theta) {
        turnAbout(PumpjackLayout.CRANK_Y, PumpjackLayout.CRANK_U, theta);
        float cy = PumpjackLayout.CRANK_Y, cu = PumpjackLayout.CRANK_U, r = PumpjackLayout.CRANK_RADIUS;
        float half = PumpjackLayout.WEIGHT_HALF, outer = PumpjackLayout.WEIGHT_OUTER;
        double span = PumpjackLayout.WEIGHT_SPAN;
        for (float x : new float[] { PumpjackLayout.CRANK_X, PumpjackLayout.WIDTH - PumpjackLayout.CRANK_X }) {
            float outward = x < CX ? -1.0F : 1.0F;
            // The arm: a plate from the shaft hub out past the pin.
            box(x - 1.0F, cy - 3.0F, cu - 2.5F, x + 1.0F, cy + r + 2.5F, cu + 2.5F, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
            ringSector(x - half, x + half, cy, cu, Math.PI / 2.0 - span, Math.PI / 2.0 + span, a -> PumpjackLayout.WEIGHT_INNER, outer,
                    8, Paint.PLAIN, Paint.HAZARD, Paint.DARK);
            prismX(cu, cy, x - 1.5F, x + 1.5F, 3.0F, Paint.BANDED, Paint.RIVET);
            float pin0 = outward < 0 ? PumpjackLayout.PITMAN_X - 1.5F : x + half, pin1 = outward < 0 ? x - half : PumpjackLayout.WIDTH - PumpjackLayout.PITMAN_X + 1.5F;
            prismX(cu, cy + r, pin0, pin1, 1.0F, Paint.BANDED, Paint.RIVET);
        }
        turning = false;
    }

    // The bridle: two wire lines hanging off the horse head's face straight over the well, the carrier bar they hold
    // and the polished rod down into the Well Head.
    private void bridle(double phi) {
        float u = PU - PumpjackLayout.HEAD_RADIUS - 0.4F, carrier = PumpjackLayout.carrierY(phi);
        for (int side = -1; side <= 1; side += 2) {
            tube(new float[] { CX + side * 2.0F, PY, u }, new float[] { CX + side * 2.0F, carrier + 0.5F, u }, 0.3F, 4, Paint.DARK);
        }
        box(CX - 4.0F, carrier - 1.0F, u - 1.2F, CX + 4.0F, carrier + 0.5F, u + 1.2F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        tube(new float[] { CX, carrier, u }, new float[] { CX, 12.0F, u }, 0.6F, PIPE_SIDES, Paint.BLADE);
    }

    @Override
    float[] turn(float[] p) {
        if (!turning) {
            return p;
        }
        float dy = p[1] - pivotY, du = p[2] - pivotU;
        return new float[] { p[0], pivotY + (float) (dy * turnCos - du * turnSin), pivotU + (float) (du * turnCos + dy * turnSin) };
    }
}
