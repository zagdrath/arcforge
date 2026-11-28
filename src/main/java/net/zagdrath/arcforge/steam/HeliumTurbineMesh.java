/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;

import org.jspecify.annotations.Nullable;

// A formed Helium Gas Turbine as one closed-cycle gas turbine-generator set on its raised deck (see HeliumTurbineLayout),
// on the sheet block/helium_gas_turbine/turbine: a large model in the Nuclear Steam Turbine's manner (docs/TEXTURE_STYLE.md,
// "Helium Gas Turbine model"), its sheet the Nuclear Steam Turbine's with the enamel in a cold steel blue. The plenum,
// compressor, power turbines, recuperator, crossovers, precooler and generator are in that enamel (EN_PLAIN plates, the
// ENAMEL ramp on round parts); the deck, columns, pedestals, railings, stairs, the insulated Hot Helium ducts and every
// flange in machine steel; the precooler's finned front in the vent tile; the generator's terminal box alone in FE red.
//
// Built as NuclearTurbineMesh is: the still parts (buildStatic), and the parts that turn with the shaft (the exposed
// shaft, the couplings and the turning-gear wheel: buildRotor, built at rest and turned as a whole about rotorAxis()),
// with a few-quad stand-in for them at a distance (buildRotorLod). Its casings are closed: nothing else turns in view.
//
// Everything is placed in the layout's canonical frame (the front at high x) and mirrored across through x(...).
public final class HeliumTurbineMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 384;
    private static final int PIPE = 8;

    private final HeliumTurbineLayout layout;

    public HeliumTurbineMesh(HeliumTurbineLayout layout, boolean alongX) {
        super(alongX, false, HeliumTurbineLayout.WIDTH_PX, layout.lengthPx(), layout.shaftX(), layout.shaftY(), SHEET_W, SHEET_H);
        this.layout = layout;
        this.modules = true;
    }

    // --- The model ---

    // The still parts and the nozzles of these ports: {solid, cutout} (nothing is glazed: the cutout list is empty).
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        deck();
        stairs();
        walkway();
        frontStandard();
        plenum();
        compressor();
        precooler();
        dischargePipe();
        for (HeliumTurbineLayout.Span turbine : layout.turbines()) {
            powerTurbine(turbine);
        }
        for (float u : layout.inletU()) {
            inletDuct(u);
        }
        for (HeliumTurbineLayout.Span gap : layout.gaps()) {
            pedestal(gap.u0(), gap.u0() + HeliumTurbineLayout.PEDESTAL);
            pedestal(gap.u1() - HeliumTurbineLayout.PEDESTAL, gap.u1());
        }
        recuperator();
        for (HeliumTurbineLayout.Span turbine : layout.turbines()) {
            crossover(turbine);
        }
        generator();
        for (Port port : ports) {
            nozzle(port);
        }
        return new float[][] { solid.toArray(), cutout.toArray() };
    }

    // The parts that turn with the shaft, at rest: the shaft from the front standard to the exciter, the turning-gear
    // wheel and a coupling (with bolt heads, so its turning shows) in each gap between casings.
    public float[] buildRotor() {
        startRotor(0.0F);
        float sx = layout.shaftX(), sy = layout.shaftY(), r = layout.shaftRadius();
        prismU(sx, sy, new float[][] { { HeliumTurbineLayout.STANDARD_U0 + 2.0F, r }, { layout.lengthPx() - 3.0F, r } }, PIPE, 0.0, Paint.BANDED,
                Paint.MID, Paint.MID, null);
        wheel(sx, sy);
        for (HeliumTurbineLayout.Span gap : layout.gaps()) {
            coupling(sx, sy, gap.middle());
        }
        return solid.toArray();
    }

    // A few quads for the turning parts far off: a plain shaft, the wheel and the couplings as plain discs.
    public float[] buildRotorLod() {
        boolean was = modules;
        modules = false;
        startRotor(0.0F);
        float sx = layout.shaftX(), sy = layout.shaftY();
        prismU(sx, sy, new float[][] { { HeliumTurbineLayout.STANDARD_U0 + 2.0F, 3.5F }, { layout.lengthPx() - 3.0F, 3.5F } }, 6, 0.0, Paint.BANDED, null,
                null, null);
        prismU(sx, sy, new float[][] { { HeliumTurbineLayout.WHEEL_U0, HeliumTurbineLayout.WHEEL_RADIUS },
                { HeliumTurbineLayout.WHEEL_U1, HeliumTurbineLayout.WHEEL_RADIUS } }, 8, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
        for (HeliumTurbineLayout.Span gap : layout.gaps()) {
            prismU(sx, sy, new float[][] { { gap.middle() - 1.5F, 7.0F }, { gap.middle() + 1.5F, 7.0F } }, 8, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
        }
        modules = was;
        return solid.toArray();
    }

    // --- Placing in the canonical frame ---

    private float x(float canonical) {
        return layout.x(canonical);
    }

    // A box from canonical x0 to x1 (skip given as for the canonical frame).
    private void cbox(float x0, float y0, float u0, float x1, float y1, float u1, Paint sides, @Nullable Paint top, @Nullable Paint bottom, int skip) {
        float a = x(x0), b = x(x1);
        if (!layout.frontHigh()) {
            int neg = skip & SKIP_NEG_X, pos = skip & SKIP_POS_X;
            skip = skip & ~(SKIP_NEG_X | SKIP_POS_X) | (neg != 0 ? SKIP_POS_X : 0) | (pos != 0 ? SKIP_NEG_X : 0);
        }
        box(Math.min(a, b), y0, u0, Math.max(a, b), y1, u1, sides, top, bottom, skip);
    }

    // A box the nozzles stop at (canonical x).
    private void solidBox(float x0, float y0, float u0, float x1, float y1, float u1) {
        float a = x(x0), b = x(x1);
        solidBoxes.add(new float[] { Math.min(a, b), y0, u0, Math.max(a, b), y1, u1 });
    }

    // A point from canonical (x, y, u).
    private float[] p(float x, float y, float u) {
        return new float[] { x(x), y, u };
    }

    // A pipe across (along x) at (u, y) between canonical x0 and x1.
    private void pipeX(float u, float y, float x0, float x1, float r, Paint side, Paint cap) {
        float a = x(x0), b = x(x1);
        prismX(u, y, Math.min(a, b), Math.max(a, b), r, side, cap);
    }

    // A bolt head s px across standing on a face at y (canonical x0 its low corner).
    private void bolt(float x0, float y, float u, float s) {
        cbox(x0, y, u - s / 2.0F, x0 + s, y + s * 0.8F, u + s / 2.0F, Paint.RIVET, Paint.RIVET, Paint.RIVET, SKIP_DOWN);
    }

    // --- The deck, stairs and walkway ---

    // The deck slab (its edge a girder flange, its top deck plates), open over the staircase, on stepped columns.
    private void deck() {
        float t = layout.lengthPx(), w = HeliumTurbineLayout.WIDTH_PX, walk = HeliumTurbineLayout.WALKWAY_X;
        float bottom = HeliumTurbineLayout.DECK_BOTTOM, top = HeliumTurbineLayout.DECK_TOP;
        cbox(0.0F, bottom, 0.0F, walk, top, t, Paint.STRAP, Paint.DECK, Paint.FOOTING, 0);
        cbox(walk, bottom, HeliumTurbineLayout.STAIR_U1, w, top, t, Paint.STRAP, Paint.DECK, Paint.FOOTING, SKIP_NEG_X);
        solidBox(0.0F, bottom, 0.0F, w, top, t);
        // Columns along the back and front edges, the front row clear of the staircase.
        int count = Math.max(2, Math.round(t / 40.0F));
        for (int i = 0; i < count; i++) {
            float u = 4.0F + (t - 13.0F) * i / (count - 1);
            column(3.0F, u, bottom);
            if (u > HeliumTurbineLayout.STAIR_U1 + 3.0F) {
                column(72.0F, u, bottom);
            }
        }
    }

    // A stepped column from the ground to under the deck (canonical x0): a foot, the column and a cap.
    private void column(float x0, float u0, float top) {
        float cw = 5.0F;
        cbox(x0 - 1.0F, 0.0F, u0 - 1.0F, x0 + cw + 1.0F, 1.5F, u0 + cw + 1.0F, Paint.FOOTING, Paint.FOOTING, Paint.FOOTING, 0);
        cbox(x0, 1.5F, u0, x0 + cw, top - 1.0F, u0 + cw, Paint.PLAIN, null, null, SKIP_DOWN);
        cbox(x0 - 0.5F, top - 1.0F, u0 - 0.5F, x0 + cw + 0.5F, top, u0 + cw + 0.5F, Paint.MID, Paint.MID, Paint.MID, SKIP_UP);
    }

    // The staircase up the front strip at the front standard's end: two stringers, gratings for treads and a handrail.
    private void stairs() {
        float u0 = HeliumTurbineLayout.STAIR_U0, u1 = HeliumTurbineLayout.STAIR_U1, top = HeliumTurbineLayout.DECK_TOP;
        int steps = 6;
        float run = (u1 - u0) / steps, rise = top / steps;
        for (int k = 1; k <= steps; k++) {
            cbox(HeliumTurbineLayout.WALKWAY_X + 1.25F, rise * k - 1.0F, u0 + run * (k - 1), 78.75F, rise * k, u0 + run * k, Paint.DARK, Paint.GRATE,
                    Paint.DARK, 0);
        }
        for (float sx : new float[] { HeliumTurbineLayout.WALKWAY_X + 0.5F, 79.25F }) {
            tube(p(sx, 0.0F, u0 - 1.0F), p(sx, top, u1), 0.6F, 4, Paint.PLAIN);
        }
        cbox(78.5F, 0.0F, u0 - 0.25F, 79.5F, 14.0F, u0 + 0.75F, Paint.LIT, Paint.LIT, Paint.LIT, 0);
        tube(p(79.0F, 14.0F, u0 + 0.5F), p(79.0F, top + 14.0F, u1 + 1.0F), 0.5F, 4, Paint.LIT);
    }

    // The walkway grating along the front edge of the deck, and its handrail: posts every block, a top and a knee rail.
    private void walkway() {
        float t = layout.lengthPx(), floor = HeliumTurbineLayout.DECK_TOP, u0 = HeliumTurbineLayout.STAIR_U1;
        cbox(HeliumTurbineLayout.WALKWAY_X, floor, u0, 79.5F, floor + 1.0F, t - 1.0F, Paint.DARK, Paint.GRATE, Paint.DARK, SKIP_DOWN);
        float top = floor + 14.0F;
        for (float u = u0 + 1.0F; u <= t - 3.0F; u += 16.0F) {
            cbox(78.5F, floor + 1.0F, u, 79.5F, top - 1.0F, u + 1.0F, Paint.LIT, Paint.LIT, Paint.LIT, SKIP_DOWN);
        }
        cbox(78.5F, floor + 1.0F, t - 3.0F, 79.5F, top - 1.0F, t - 2.0F, Paint.LIT, Paint.LIT, Paint.LIT, SKIP_DOWN);
        cbox(78.25F, top - 1.0F, u0, 79.75F, top, t - 2.0F, Paint.LIT, Paint.LIT, Paint.SHADE, 0);
        float knee = floor + 7.5F;
        cbox(78.75F, knee - 0.5F, u0, 79.25F, knee + 0.5F, t - 2.5F, Paint.MID, Paint.MID, Paint.SHADE, 0);
    }

    // --- Along the shaft ---

    // The front standard: a squat bearing pedestal, its bearing cap, an oil line down its front, and the starting motor
    // on its back side.
    private void frontStandard() {
        float u0 = HeliumTurbineLayout.STANDARD_U0, u1 = HeliumTurbineLayout.STANDARD_U1, deck = HeliumTurbineLayout.DECK_TOP;
        float sy = HeliumTurbineLayout.SHAFT_Y;
        cbox(26.0F, deck, u0, 46.0F, sy + 5.0F, u1, Paint.PLATE, Paint.PLAIN, null, SKIP_DOWN);
        cbox(29.0F, sy + 5.0F, u0 + 1.5F, 43.0F, sy + 8.0F, u1 - 1.5F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        solidBox(26.0F, deck, u0, 46.0F, sy + 8.0F, u1);
        prismY(x(47.0F), u0 + 5.0F, deck, sy + 3.0F, 0.55F, Paint.BANDED, Paint.BANDED);
        pipeX(u0 + 5.0F, sy + 3.0F, 46.0F, 47.6F, 0.6F, Paint.BANDED, Paint.BANDED);
        // The starting motor (a closed cycle has to be spun up to self-sustaining speed) on its gearbox.
        cbox(19.0F, 30.0F, u0 + 2.0F, 26.0F, 46.0F, u1 - 2.0F, Paint.M_MOTOR, Paint.PLAIN, null, SKIP_POS_X);
    }

    // The compressor's intake plenum: a square box round the shaft, with a flanged lid and its stiffeners, open on its
    // front side into the precooler beside it.
    private void plenum() {
        float x0 = HeliumTurbineLayout.PLENUM_X0, x1 = HeliumTurbineLayout.PLENUM_X1, u0 = HeliumTurbineLayout.PLENUM_U0, u1 = HeliumTurbineLayout.PLENUM_U1;
        float deck = HeliumTurbineLayout.DECK_TOP, top = HeliumTurbineLayout.PLENUM_TOP;
        cbox(x0, deck, u0, x1, top, u1, Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN);
        cbox(x0 - 1.0F, top, u0 - 1.0F, x1, top + 1.5F, u1 + 1.0F, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, 0);
        for (float u = u0 + 2.5F; u <= u1 - 2.0F; u += 4.0F) {
            bolt(x0, top + 1.5F, u, 1.0F);
        }
        for (float y = deck + 8.0F; y < top - 4.0F; y += 10.0F) {
            cbox(x0 - 1.0F, y, u0, x0, y + 1.5F, u1, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, SKIP_POS_X);
        }
        solidBox(x0 - 1.0F, deck, u0 - 1.0F, x1, top + 1.5F, u1 + 1.0F);
    }

    // The compressor: a 12-sided drum from the plenum, narrowing stage by stage toward its discharge end, a flange round
    // every stage's joint, and the split line's flange along both sides.
    private void compressor() {
        HeliumTurbineLayout.Span c = layout.compressor();
        float sx = layout.shaftX(), sy = layout.shaftY(), rin = HeliumTurbineLayout.COMPRESSOR_IN, rout = HeliumTurbineLayout.COMPRESSOR_OUT;
        prismU(sx, sy, new float[][] { { c.u0(), rin }, { c.u1() - 3.0F, rout }, { c.u1(), rout - 1.5F } }, 12, 0.0, Paint.ENAMEL, Paint.EN_PLAIN,
                Paint.EN_PLAIN, null);
        solidCylinders.add(new float[] { sx, sy, rin, c.u0(), c.u1() });
        float span = c.length() - 3.0F;
        for (float u = c.u0() + HeliumTurbineLayout.STAGE; u < c.u1() - 3.0F; u += HeliumTurbineLayout.STAGE) {
            float r = rin + (rout - rin) * (u - c.u0()) / span;
            prismU(sx, sy, new float[][] { { u - 0.5F, r + 1.0F }, { u + 0.5F, r + 1.0F } }, 12, 0.0, Paint.BANDED, Paint.PLAIN, Paint.PLAIN, null);
        }
        splitFlange(c.u0(), c.u1() - 6.0F, c.u0(), rin, c.u1() - 3.0F, rout, sy, 2.0F, false);
    }

    // The precooler: a finned box along the front of the compressor (the cooler's fins on its front face), its top
    // panel, two water headers along it and their risers down through the deck, and a foot rail.
    private void precooler() {
        HeliumTurbineLayout.Span s = layout.precooler();
        float x0 = HeliumTurbineLayout.PRECOOLER_X0, x1 = HeliumTurbineLayout.PRECOOLER_X1, deck = HeliumTurbineLayout.DECK_TOP;
        float top = HeliumTurbineLayout.PRECOOLER_TOP;
        cbox(x0, deck + 2.0F, s.u0(), x1 - 0.5F, top, s.u1(), Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN | SKIP_POS_X);
        // The finned front, framed by the box's edges.
        cbox(x1 - 0.5F, deck + 4.0F, s.u0() + 2.0F, x1, top - 2.0F, s.u1() - 2.0F, Paint.VENT, null, null, SKIP_NEG_X | SKIP_UP | SKIP_DOWN | SKIP_NEG_U
                | SKIP_POS_U);
        cbox(x1 - 0.5F, deck + 2.0F, s.u0(), x1 + 0.5F, deck + 4.0F, s.u1(), Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, SKIP_NEG_X);
        cbox(x1 - 0.5F, top - 2.0F, s.u0(), x1 + 0.5F, top, s.u1(), Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, SKIP_NEG_X);
        cbox(x1 - 0.5F, deck + 4.0F, s.u0(), x1 + 0.5F, top - 2.0F, s.u0() + 2.0F, Paint.EN_PLAIN, null, null, SKIP_NEG_X | SKIP_UP | SKIP_DOWN);
        cbox(x1 - 0.5F, deck + 4.0F, s.u1() - 2.0F, x1 + 0.5F, top - 2.0F, s.u1(), Paint.EN_PLAIN, null, null, SKIP_NEG_X | SKIP_UP | SKIP_DOWN);
        // The foot rail it stands on.
        cbox(x0 - 0.5F, deck, s.u0() - 0.5F, x1 + 1.0F, deck + 2.0F, s.u1() + 0.5F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        solidBox(x0, deck, s.u0() - 0.5F, x1 + 1.0F, top, s.u1() + 0.5F);
        // The water headers on top, and their risers at the far end.
        for (float hx : new float[] { 56.5F, 60.5F }) {
            prismU(x(hx), top + 2.0F, new float[][] { { s.u0() + 2.0F, 1.6F }, { s.u1() - 2.0F, 1.6F } }, PIPE, Math.PI / PIPE, Paint.BANDED, Paint.PLAIN,
                    Paint.PLAIN, null);
            prismY(x(hx), s.u1() - 4.0F, top, top + 2.0F, 1.0F, Paint.BANDED, Paint.BANDED);
            prismY(x(hx), s.u0() + 4.0F, top, top + 2.0F, 1.0F, Paint.BANDED, Paint.BANDED);
        }
    }

    // The compressor's discharge: a pipe down from under its discharge end through the deck, where the cooled Helium
    // leaves for the reactor, with its flange on the deck.
    private void dischargePipe() {
        float u = layout.dischargeU(), deck = HeliumTurbineLayout.DECK_TOP, r = 3.5F;
        float bottom = layout.shaftY() - HeliumTurbineLayout.COMPRESSOR_OUT - 0.5F;
        prismY(x(HeliumTurbineLayout.SHAFT_X), u, 0.0F, bottom, r, Paint.BANDED, Paint.PLAIN);
        uprights.add(new float[] { x(HeliumTurbineLayout.SHAFT_X), u, r, 0.0F, bottom });
        prismY(x(HeliumTurbineLayout.SHAFT_X), u, deck, deck + 1.5F, r + 1.5F, Paint.PLAIN, Paint.MID);
        prismY(x(HeliumTurbineLayout.SHAFT_X), u, bottom - 3.0F, bottom - 2.0F, r + 0.9F, Paint.PLAIN, Paint.MID);
    }

    // A power turbine: a 16-sided casing flaring from its inlet ring out to its exhaust end, the split flange along both
    // sides with its bolts, a flange at the exhaust end and the exhaust neck rising out of its top into the crossover.
    private void powerTurbine(HeliumTurbineLayout.Span t) {
        float sx = layout.shaftX(), sy = layout.shaftY(), rin = HeliumTurbineLayout.TURBINE_IN, rout = HeliumTurbineLayout.TURBINE_OUT;
        prismU(sx, sy, new float[][] { { t.u0(), rin - 2.0F }, { t.u0() + 3.0F, rin }, { t.u1() - 4.0F, rout }, { t.u1(), rout - 2.0F } }, 16, 0.0,
                Paint.ENAMEL, Paint.EN_PLAIN, Paint.EN_PLAIN, null);
        solidCylinders.add(new float[] { sx, sy, rout, t.u0(), t.u1() });
        // The inlet ring (the Hot Helium's toroidal manifold round the first stage).
        float ring = HeliumTurbineLayout.RING_RADIUS;
        prismU(sx, sy, new float[][] { { t.u0() + 2.0F, ring - 1.0F }, { t.u0() + 4.0F, ring }, { t.u0() + 8.0F, ring }, { t.u0() + 10.0F, ring - 1.0F } }, 16,
                0.0, Paint.ENAMEL, Paint.EN_PLAIN, Paint.EN_PLAIN, null);
        // The exhaust-end flange.
        prismU(sx, sy, new float[][] { { t.u1() - 4.5F, rout + 1.0F }, { t.u1() - 3.5F, rout + 1.0F } }, 16, 0.0, Paint.BANDED, Paint.PLAIN, Paint.PLAIN, null);
        // The split flange and its bolts.
        splitFlange(t.u0() + 10.5F, t.u1() - 5.0F, t.u0() + 3.0F, rin, t.u1() - 4.0F, rout, sy, 3.0F, true);
        // The exhaust neck on top, into the crossover.
        float nu = neckU(t);
        uprightTube(sx, nu, new float[][] { { sy + rout - 2.0F, 5.4F }, { sy + rout + 3.0F, 5.4F } }, 12, 0.0, Paint.ENAMEL, null);
        prismY(sx, nu, sy + rout + 2.0F, sy + rout + 3.5F, 6.4F, Paint.PLAIN, Paint.MID);
    }

    // Where a power turbine's exhaust neck stands along u.
    private static float neckU(HeliumTurbineLayout.Span t) {
        return t.u1() - 9.0F;
    }

    // An insulated Hot Helium duct: up from the deck's port under it at the front, a flanged elbow, and across into the
    // power turbine's inlet ring, lagged (the banded steel) with a flange at each joint.
    private void inletDuct(float u) {
        float r = HeliumTurbineLayout.INLET_RADIUS, y = HeliumTurbineLayout.INLET_Y, deck = HeliumTurbineLayout.DECK_TOP;
        float rx = HeliumTurbineLayout.INLET_X;
        prismY(x(rx), u, 0.0F, y + r, r, Paint.BANDED, Paint.PLAIN);
        uprights.add(new float[] { x(rx), u, r, 0.0F, y + r });
        prismY(x(rx), u, deck, deck + 1.5F, r + 1.5F, Paint.PLAIN, Paint.MID);
        prismY(x(rx), u, y - r - 3.0F, y - r - 2.0F, r + 1.0F, Paint.PLAIN, Paint.MID);
        float into = HeliumTurbineLayout.SHAFT_X + HeliumTurbineLayout.RING_RADIUS - 1.0F;
        pipeX(u, y, into, rx - r + 0.5F, r, Paint.BANDED, Paint.BANDED);
        pipeX(u, y, rx - r - 2.0F, rx - r - 1.0F, r + 1.0F, Paint.PLAIN, Paint.MID);
    }

    // A bearing pedestal across the shaft from u0 to u1, with its bearing cap on top.
    private void pedestal(float u0, float u1) {
        float deck = HeliumTurbineLayout.DECK_TOP, top = HeliumTurbineLayout.SHAFT_Y + 4.5F;
        cbox(29.0F, deck, u0, 43.0F, top, u1, Paint.PLATE, Paint.PLAIN, null, SKIP_DOWN);
        cbox(31.0F, top, u0 + 0.4F, 41.0F, top + 1.5F, u1 - 0.4F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        solidBox(29.0F, deck, u0, 43.0F, top + 1.5F, u1);
    }

    // The recuperator along the back: a long vessel with domed ends on machine-steel saddles, a girth flange at its
    // middle (two shells bolted together, the way a tube-bundle exchanger opens), a manway on each end, and a collar on
    // its top for each crossover coming in.
    private void recuperator() {
        HeliumTurbineLayout.Span span = layout.recuperator();
        float u0 = span.u0(), u1 = span.u1(), y = HeliumTurbineLayout.RECUPERATOR_Y, r = HeliumTurbineLayout.RECUPERATOR_RADIUS;
        float deck = HeliumTurbineLayout.DECK_TOP, cx = HeliumTurbineLayout.RECUPERATOR_X, x = x(cx);
        prismU(x, y, new float[][] { { u0, 3.2F }, { u0 + 3.0F, r - 1.2F }, { u0 + 6.0F, r }, { u1 - 6.0F, r }, { u1 - 3.0F, r - 1.2F }, { u1, 3.2F } }, 16, 0.0,
                Paint.ENAMEL, Paint.EN_PLAIN, Paint.EN_PLAIN, null);
        solidCylinders.add(new float[] { x, y, r, u0, u1 });
        float mid = span.middle();
        prismU(x, y, new float[][] { { mid - 1.0F, r + 1.2F }, { mid + 1.0F, r + 1.2F } }, 16, 0.0, Paint.BANDED, Paint.PLAIN, Paint.PLAIN, null);
        for (boolean end : new boolean[] { false, true }) {
            float face = end ? u1 : u0, out = end ? 1.0F : -1.0F;
            float m0 = Math.min(face, face + out * 2.5F), m1 = Math.max(face, face + out * 2.5F);
            prismU(x, y, new float[][] { { m0 - (end ? 1.0F : 0.0F), 2.8F }, { m1 + (end ? 0.0F : 1.0F), 2.8F } }, 12, 0.0, Paint.ENAMEL, Paint.EN_PLAIN,
                    Paint.EN_PLAIN, null);
            float f0 = end ? face + 1.2F : face - 2.2F;
            prismU(x, y, new float[][] { { f0, 3.8F }, { f0 + 1.0F, 3.8F } }, 12, 0.0, Paint.BANDED, Paint.PLAIN, Paint.PLAIN, null);
        }
        int saddles = span.length() > 150.0F ? 3 : 2;
        for (int i = 0; i < saddles; i++) {
            float s = u0 + 12.0F + (span.length() - 24.0F) * i / (saddles - 1);
            cbox(cx - 5.0F, deck, s - 2.5F, cx + 5.0F, y - r + 3.0F, s + 2.5F, Paint.PLAIN, Paint.PLAIN, null, SKIP_DOWN);
            cbox(cx - 6.0F, deck, s - 3.5F, cx + 6.0F, deck + 1.2F, s + 3.5F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        }
    }

    // A casing's split flange along both sides of it at the shaft's height, from u0 to u1, standing off the casing whose
    // radius runs from rA at uA to rB at uB: short lengths each at the casing's radius where it is (so it follows a
    // taper), w px deep, and bolts on its top every 4 px when bolted.
    private void splitFlange(float u0, float u1, float uA, float rA, float uB, float rB, float sy, float w, boolean bolted) {
        float step = 2.0F;
        for (float s0 = u0; s0 < u1 - 0.01F; s0 += step) {
            float s1 = Math.min(u1, s0 + step), r = radiusAt((s0 + s1) / 2.0F, uA, rA, uB, rB);
            float lip = HeliumTurbineLayout.SHAFT_X - r - 1.5F, far = HeliumTurbineLayout.SHAFT_X + r + 1.5F;
            cbox(lip, sy - 1.0F, s0, lip + w, sy + 1.0F, s1, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, 0);
            cbox(far - w, sy - 1.0F, s0, far, sy + 1.0F, s1, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, 0);
        }
        if (bolted) {
            for (float u = u0 + 2.0F; u <= u1 - 1.0F; u += 4.0F) {
                float r = radiusAt(u, uA, rA, uB, rB);
                float lip = HeliumTurbineLayout.SHAFT_X - r - 1.5F, far = HeliumTurbineLayout.SHAFT_X + r + 1.5F;
                bolt(lip + 0.3F, sy + 1.0F, u, 1.0F);
                bolt(far - 1.3F, sy + 1.0F, u, 1.0F);
            }
        }
    }

    // A casing's radius at u, running straight from rA at uA to rB at uB.
    private static float radiusAt(float u, float uA, float rA, float uB, float rB) {
        float k = Math.max(0.0F, Math.min(1.0F, (u - uA) / (uB - uA)));
        return rA + (rB - rA) * k;
    }

    // A crossover duct from a power turbine's exhaust neck, arching back over into the recuperator's top: a cubic curve
    // (straight up out of the neck, straight down into the vessel) swept as one tube, a flange at every fourth joint, and
    // a collar where it lands.
    private void crossover(HeliumTurbineLayout.Span t) {
        int segments = 12;
        float r = HeliumTurbineLayout.CROSSOVER_RADIUS, rout = HeliumTurbineLayout.TURBINE_OUT;
        float y0 = layout.shaftY() + rout + 3.0F;
        float y1 = HeliumTurbineLayout.RECUPERATOR_Y + HeliumTurbineLayout.RECUPERATOR_RADIUS + 1.0F;
        float crown = HeliumTurbineLayout.CROWN_Y;
        float control = (crown - (y0 + y1) / 8.0F) / 0.75F;
        HeliumTurbineLayout.Span rec = layout.recuperator();
        float nu = neckU(t), land = Math.max(rec.u0() + 12.0F, Math.min(rec.u1() - 12.0F, nu - 4.0F));
        float[] s = p(HeliumTurbineLayout.SHAFT_X, y0, nu), e = p(HeliumTurbineLayout.RECUPERATOR_X, y1, land);
        float[] c1 = { s[0], control, s[2] }, c2 = { e[0], control, e[2] };
        float[][] points = new float[segments + 1][];
        for (int i = 0; i <= segments; i++) {
            float k = (float) i / segments, a = (1 - k) * (1 - k) * (1 - k), b = 3 * (1 - k) * (1 - k) * k, c = 3 * (1 - k) * k * k, d = k * k * k;
            points[i] = new float[] { a * s[0] + b * c1[0] + c * c2[0] + d * e[0], a * s[1] + b * c1[1] + c * c2[1] + d * e[1],
                    a * s[2] + b * c1[2] + c * c2[2] + d * e[2] };
        }
        sweep(points, r);
        for (int i = 4; i < segments; i += 4) {
            float[] dir = normalize(sub(points[i + 1], points[i - 1]));
            flange(points[i], dir, r + 1.0F, r, 0.8F);
        }
        prismY(e[0], e[2], y1 - 2.5F, y1 + 1.2F, r + 1.0F, Paint.PLAIN, Paint.MID);
    }

    // A tube of radius r through points (see NuclearTurbineMesh.sweep).
    private void sweep(float[][] points, float r) {
        int n = points.length;
        float[][][] rings = new float[n][][];
        float[] normal = null;
        double step = 2.0 * Math.PI / PIPE;
        for (int i = 0; i < n; i++) {
            float[] tangent = normalize(sub(points[Math.min(n - 1, i + 1)], points[Math.max(0, i - 1)]));
            if (normal == null) {
                float[] helper = Math.abs(tangent[1]) < 0.9F ? new float[] { 0, 1, 0 } : new float[] { 1, 0, 0 };
                normal = normalize(cross(tangent, helper));
            } else {
                float along = dot(normal, tangent);
                normal = normalize(new float[] { normal[0] - tangent[0] * along, normal[1] - tangent[1] * along, normal[2] - tangent[2] * along });
            }
            float[] binormal = cross(tangent, normal);
            rings[i] = new float[PIPE][];
            for (int f = 0; f < PIPE; f++) {
                rings[i][f] = ring(points[i], normal, binormal, (f + 0.5) * step, r);
            }
        }
        float fw = 2.0F * r * (float) Math.sin(Math.PI / PIPE);
        for (int i = 0; i + 1 < n; i++) {
            float length = length(sub(points[i + 1], points[i]));
            for (int f = 0; f < PIPE; f++) {
                int g = (f + 1) % PIPE;
                float[] p0 = rings[i][f], p1 = rings[i][g], p2 = rings[i + 1][g], p3 = rings[i + 1][f];
                float[] mid = { (p0[0] + p1[0] + p2[0] + p3[0]) / 4.0F, (p0[1] + p1[1] + p2[1] + p3[1]) / 4.0F, (p0[2] + p1[2] + p2[2] + p3[2]) / 4.0F };
                float[] centre = { (points[i][0] + points[i + 1][0]) / 2.0F, (points[i][1] + points[i + 1][1]) / 2.0F, (points[i][2] + points[i + 1][2]) / 2.0F };
                roundFacet(p0, p1, p2, p3, normalize(sub(mid, centre)), Paint.ENAMEL, f, PIPE, fw, length);
            }
        }
    }

    // A pipe flange: a short band of radius outer round centre along dir, w px long, its two faces closed down to inner.
    private void flange(float[] centre, float[] dir, float outer, float inner, float w) {
        float[] a = { centre[0] - dir[0] * w / 2.0F, centre[1] - dir[1] * w / 2.0F, centre[2] - dir[2] * w / 2.0F };
        float[] b = { centre[0] + dir[0] * w / 2.0F, centre[1] + dir[1] * w / 2.0F, centre[2] + dir[2] * w / 2.0F };
        tube(a, b, outer, PIPE, Paint.BANDED);
        annulus(a, new float[] { -dir[0], -dir[1], -dir[2] }, outer, inner);
        annulus(b, dir, outer, inner);
    }

    private void annulus(float[] centre, float[] normal, float outer, float inner) {
        float[] helper = Math.abs(normal[1]) < 0.9F ? new float[] { 0, 1, 0 } : new float[] { 1, 0, 0 };
        float[] b1 = normalize(cross(normal, helper)), b2 = cross(normal, b1);
        double step = 2.0 * Math.PI / PIPE;
        for (int f = 0; f < PIPE; f++) {
            double a0 = f * step + step / 2.0, a1 = a0 + step;
            float[] o0 = ring(centre, b1, b2, a0, outer), o1 = ring(centre, b1, b2, a1, outer);
            float[] i1 = ring(centre, b1, b2, a1, inner), i0 = ring(centre, b1, b2, a0, inner);
            orient(o0, o1, i1, i0, normal, Paint.PLAIN);
        }
    }

    private static float[] ring(float[] centre, float[] b1, float[] b2, double angle, float radius) {
        float c = (float) Math.cos(angle) * radius, s = (float) Math.sin(angle) * radius;
        return new float[] { centre[0] + b1[0] * c + b2[0] * s, centre[1] + b1[1] * c + b2[1] * s, centre[2] + b1[2] * c + b2[2] * s };
    }

    // The generator: the stator with domed end shields on two feet, one cooler on top, the terminal box (FE red) under its
    // near end with the cable bus down from it, then the slip-ring housing and the exciter.
    private void generator() {
        HeliumTurbineLayout.Span g = layout.generator();
        float sx = layout.shaftX(), sy = layout.shaftY(), r = HeliumTurbineLayout.STATOR_RADIUS, e = HeliumTurbineLayout.STATOR_END_RADIUS;
        float shield = HeliumTurbineLayout.SHIELD, deck = HeliumTurbineLayout.DECK_TOP;
        prismU(sx, sy, new float[][] { { g.u0(), e }, { g.u0() + shield * 0.5F, e + (r - e) * 0.7F }, { g.u0() + shield, r }, { g.u1() - shield, r },
                { g.u1() - shield * 0.5F, e + (r - e) * 0.7F }, { g.u1(), e } }, 16, 0.0, Paint.ENAMEL, Paint.EN_PLAIN, Paint.EN_PLAIN, null);
        solidCylinders.add(new float[] { sx, sy, r, g.u0(), g.u1() });
        float far = g.u1() - shield - 7.0F, near = layout.terminalBox().u1() + 1.5F;
        for (float u : near + 9.0F <= far ? new float[] { near, far } : new float[] { far }) {
            cbox(21.0F, deck, u, 51.0F, sy - r + 3.0F, u + 6.0F, Paint.PLAIN, Paint.PLAIN, null, SKIP_DOWN);
        }
        // The cooler on top, and its water lines on the back.
        float length = g.length() - 2.0F * shield, c0 = g.u0() + shield + length * 0.12F, c1 = g.u1() - shield - length * 0.12F;
        cbox(26.0F, sy + 16.0F, c0, 46.0F, sy + 26.0F, c1, Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN);
        cbox(27.0F, sy + 26.0F, c0 + 1.2F, 45.0F, sy + 27.5F, c1 - 1.2F, Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN);
        // Heat-sink fins down both its long sides: thin plates every 2 px, 2 px out from it.
        for (float u = c0 + 1.5F; u + 0.5F <= c1 - 1.0F; u += 2.0F) {
            cbox(24.0F, sy + 17.0F, u, 26.0F, sy + 25.0F, u + 0.5F, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, SKIP_POS_X);
            cbox(46.0F, sy + 17.0F, u, 48.0F, sy + 25.0F, u + 0.5F, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, SKIP_NEG_X);
        }
        solidBox(26.0F, sy + 16.0F, c0, 46.0F, sy + 27.5F, c1);
        HeliumTurbineLayout.Span box = layout.terminalBox();
        // Up from under the deck into the stator (its top hidden in it), in the darker red, with a raised panel on each face
        // under the deck and a collar where it comes up through the deck, both in the brighter red.
        float bx0 = 31.0F, bx1 = 49.0F, bu0 = box.u0(), bu1 = box.u1(), btop = sy - 6.0F, low = 11.0F, high = HeliumTurbineLayout.DECK_BOTTOM - 1.0F;
        cbox(bx0, 10.0F, bu0, bx1, btop, bu1, Paint.FE_DARK, Paint.FE_DARK, Paint.FE_DARK, SKIP_UP);
        cbox(bx0 - 0.4F, low, bu0 + 1.5F, bx0, high, bu1 - 1.5F, Paint.FE, Paint.FE_LIT, Paint.FE_DARK, SKIP_POS_X);
        cbox(bx1, low, bu0 + 1.5F, bx1 + 0.4F, high, bu1 - 1.5F, Paint.FE, Paint.FE_LIT, Paint.FE_DARK, SKIP_NEG_X);
        cbox(bx0 + 1.5F, low, bu0 - 0.4F, bx1 - 1.5F, high, bu0, Paint.FE, Paint.FE_LIT, Paint.FE_DARK, SKIP_POS_U);
        cbox(bx0 + 1.5F, low, bu1, bx1 - 1.5F, high, bu1 + 0.4F, Paint.FE, Paint.FE_LIT, Paint.FE_DARK, SKIP_NEG_U);
        float deckTop = HeliumTurbineLayout.DECK_TOP;
        cbox(bx0 - 0.6F, deckTop, bu0 - 0.6F, bx1 + 0.6F, deckTop + 1.5F, bu1 + 0.6F, Paint.FE, Paint.FE_LIT, Paint.FE_DARK, SKIP_DOWN);
        float bus = HeliumTurbineLayout.BUS_X, half = HeliumTurbineLayout.BUS_HALF;
        cbox(bus - half, 0.0F, layout.busU() - half, bus + half, 10.0F, layout.busU() + half, Paint.PLAIN, null, Paint.PLAIN, SKIP_UP);
        solidBox(bus - half, 0.0F, layout.busU() - half, bus + half, sy - 6.0F, layout.busU() + half);
        // The slip-ring housing and the exciter beyond it.
        HeliumTurbineLayout.Span slip = layout.slipRing(), ex = layout.exciter();
        cbox(28.0F, deck, slip.u0(), 44.0F, 58.0F, slip.u1(), Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN | SKIP_POS_U);
        cbox(26.0F, deck, ex.u0(), 46.0F, 61.0F, ex.u1(), Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN);
        cbox(27.5F, 61.0F, ex.u0() + 1.2F, 44.5F, 62.5F, ex.u1() - 1.2F, Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN);
        cbox(30.0F, 36.0F, ex.u1(), 42.0F, 52.0F, ex.u1() + 0.5F, Paint.VENT, Paint.PLAIN, Paint.PLAIN, SKIP_NEG_U);
        solidBox(26.0F, deck, slip.u0(), 46.0F, 62.5F, ex.u1());
    }

    // --- The turning parts ---

    // The turning-gear wheel behind the front standard: a 12-sided disc with a tooth on each facet.
    private void wheel(float sx, float sy) {
        float u0 = HeliumTurbineLayout.WHEEL_U0, u1 = HeliumTurbineLayout.WHEEL_U1, r = HeliumTurbineLayout.WHEEL_RADIUS;
        int teeth = 12;
        prismU(sx, sy, new float[][] { { u0, r }, { u1, r } }, teeth, 0.0, Paint.BANDED, Paint.PLAIN, Paint.PLAIN, null);
        double step = 2.0 * Math.PI / teeth;
        for (int k = 0; k < teeth; k++) {
            double mid = k * step + step / 2.0, half = step * 0.22;
            float apothem = r * (float) Math.cos(step / 2.0) - 0.3F, out = r + 1.0F;
            float[] dirA = { (float) Math.cos(mid - half), (float) Math.sin(mid - half) }, dirB = { (float) Math.cos(mid + half), (float) Math.sin(mid + half) };
            float[] ia = { sx + dirA[0] * apothem, sy + dirA[1] * apothem }, ib = { sx + dirB[0] * apothem, sy + dirB[1] * apothem };
            float[] oa = { sx + dirA[0] * out, sy + dirA[1] * out }, ob = { sx + dirB[0] * out, sy + dirB[1] * out };
            float um = u0 + 0.25F, uM = u1 - 0.25F;
            float[] normal = { (float) Math.cos(mid), (float) Math.sin(mid), 0.0F };
            orient(new float[] { oa[0], oa[1], um }, new float[] { ob[0], ob[1], um }, new float[] { ob[0], ob[1], uM }, new float[] { oa[0], oa[1], uM }, normal,
                    Paint.MID);
            float[] side = { (float) -Math.sin(mid - half), (float) Math.cos(mid - half), 0.0F };
            orient(new float[] { ia[0], ia[1], um }, new float[] { oa[0], oa[1], um }, new float[] { oa[0], oa[1], uM }, new float[] { ia[0], ia[1], uM },
                    new float[] { -side[0], -side[1], 0.0F }, Paint.SHADE);
            float[] other = { (float) -Math.sin(mid + half), (float) Math.cos(mid + half), 0.0F };
            orient(new float[] { ib[0], ib[1], um }, new float[] { ob[0], ob[1], um }, new float[] { ob[0], ob[1], uM }, new float[] { ib[0], ib[1], uM }, other,
                    Paint.SHADE);
            orient(new float[] { ia[0], ia[1], um }, new float[] { ib[0], ib[1], um }, new float[] { ob[0], ob[1], um }, new float[] { oa[0], oa[1], um },
                    new float[] { 0, 0, -1 }, Paint.SHADE);
            orient(new float[] { ia[0], ia[1], uM }, new float[] { ib[0], ib[1], uM }, new float[] { ob[0], ob[1], uM }, new float[] { oa[0], oa[1], uM },
                    new float[] { 0, 0, 1 }, Paint.MID);
        }
    }

    // A coupling at u: a 12-sided disc pair with six bolt heads on each face.
    private void coupling(float sx, float sy, float u) {
        float r = 7.0F, c0 = u - 1.5F, c1 = u + 1.5F;
        prismU(sx, sy, new float[][] { { c0, r }, { u - 0.25F, r } }, 12, 0.0, Paint.BANDED, Paint.PLAIN, Paint.SHADE, null);
        prismU(sx, sy, new float[][] { { u + 0.25F, r }, { c1, r } }, 12, 0.0, Paint.BANDED, Paint.PLAIN, Paint.SHADE, null);
        prismU(sx, sy, new float[][] { { u - 0.25F, r - 1.2F }, { u + 0.25F, r - 1.2F } }, 12, 0.0, Paint.BANDED, null, null, null);
        for (int b = 0; b < 6; b++) {
            double a = 2.0 * Math.PI * (b + 0.5) / 6.0;
            float bx = sx + (float) Math.cos(a) * 4.8F, by = sy + (float) Math.sin(a) * 4.8F;
            box(bx - 0.5F, by - 0.5F, c0 - 0.6F, bx + 0.5F, by + 0.5F, c0, Paint.RIVET, Paint.RIVET, Paint.RIVET, SKIP_POS_U);
            box(bx - 0.5F, by - 0.5F, c1, bx + 0.5F, by + 0.5F, c1 + 0.6F, Paint.RIVET, Paint.RIVET, Paint.RIVET, SKIP_NEG_U);
        }
    }
}
