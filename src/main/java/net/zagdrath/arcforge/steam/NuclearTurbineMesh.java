/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;

import org.jspecify.annotations.Nullable;

// A formed Nuclear Steam Turbine as one turbine-generator set on its raised deck (see NuclearTurbineLayout), on the
// sheet block/nuclear_steam_turbine/turbine: a large model (docs/TEXTURE_STYLE.md, "Large model texturing" and "Nuclear
// Steam Turbine model"). The casings, hoods, reheaters, crossovers and generator are in the pale sage turbine enamel
// (EN_PLAIN and EN_WORN plates, the ENAMEL ramp on round parts); the deck, columns, pedestals, railings, stairs and pipe
// flanges in machine steel; the generator's terminal box alone in FE red.
//
// Built in three lists, all about the shaft: the still parts (buildStatic), the parts that turn with the shaft (the
// exposed shaft, the couplings and the turning-gear wheel: buildRotor, built once at angle 0 and turned as a whole about
// rotorAxis()), and the last-stage blade hints behind each low-pressure cylinder's sight port (buildBlades, sharp or
// blurred at speed), with a few-quad stand-in for the turning parts at a distance (buildRotorLod).
//
// Everything is placed in the layout's canonical frame (the front at high x) and mirrored across through x(...); boxes
// swap their x faces with it, and hand-built quads are given with the way they face, so they wind outward either way.
public final class NuclearTurbineMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 384;
    private static final int PIPE = 8;

    private final NuclearTurbineLayout layout;

    public NuclearTurbineMesh(NuclearTurbineLayout layout, boolean alongX) {
        super(alongX, false, NuclearTurbineLayout.WIDTH_PX, layout.lengthPx(), layout.shaftX(), layout.shaftY(), SHEET_W, SHEET_H);
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
        highPressure();
        steamChest(NuclearTurbineLayout.CHEST_BACK_X, -1.0F);
        steamChest(NuclearTurbineLayout.CHEST_FRONT_X, 1.0F);
        for (NuclearTurbineLayout.Span lp : layout.lowPressure()) {
            lowPressure(lp);
        }
        for (NuclearTurbineLayout.Span gap : layout.gaps()) {
            pedestal(gap.u0(), gap.u0() + NuclearTurbineLayout.PEDESTAL);
            pedestal(gap.u1() - NuclearTurbineLayout.PEDESTAL, gap.u1());
        }
        reheater(NuclearTurbineLayout.MSR_BACK_X);
        reheater(NuclearTurbineLayout.MSR_FRONT_X);
        for (NuclearTurbineLayout.Arch arch : layout.arches()) {
            crossover(arch);
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
        prismU(sx, sy, new float[][] { { NuclearTurbineLayout.STANDARD_U0 + 2.0F, r }, { layout.lengthPx() - 4.0F, r } }, PIPE, 0.0, Paint.BANDED,
                Paint.MID, Paint.MID, null);
        wheel(sx, sy);
        for (NuclearTurbineLayout.Span gap : layout.gaps()) {
            coupling(sx, sy, gap.middle());
        }
        return solid.toArray();
    }

    // The last-stage blades behind each low-pressure cylinder's sight port: a ring of blades with tip shrouds (sharp), or
    // the shroud band streaked as it looks at speed (blurred). Built at rest, about the shaft.
    public float[] buildBlades(boolean blurred) {
        startRotor(0.0F);
        for (NuclearTurbineLayout.Span lp : layout.lowPressure()) {
            float u = layout.portU(lp);
            if (blurred) {
                blurBand(u);
            } else {
                bladeRing(u);
            }
        }
        return solid.toArray();
    }

    // A few quads for the turning parts far off: a plain shaft, the wheel and the couplings as plain discs.
    public float[] buildRotorLod() {
        boolean was = modules;
        modules = false;
        startRotor(0.0F);
        float sx = layout.shaftX(), sy = layout.shaftY();
        prismU(sx, sy, new float[][] { { NuclearTurbineLayout.STANDARD_U0 + 2.0F, 3.5F }, { layout.lengthPx() - 4.0F, 3.5F } }, 6, 0.0, Paint.BANDED, null,
                null, null);
        prismU(sx, sy, new float[][] { { NuclearTurbineLayout.WHEEL_U0, NuclearTurbineLayout.WHEEL_RADIUS },
                { NuclearTurbineLayout.WHEEL_U1, NuclearTurbineLayout.WHEEL_RADIUS } }, 8, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
        for (NuclearTurbineLayout.Span gap : layout.gaps()) {
            prismU(sx, sy, new float[][] { { gap.middle() - 2.0F, 7.0F }, { gap.middle() + 2.0F, 7.0F } }, 8, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
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

    // A flat quad of canonical points facing out along canonical (nx, ny, nu).
    private void cquad(float[] a, float[] b, float[] c, float[] d, float nx, float ny, float nu, Paint paint) {
        orient(p(a[0], a[1], a[2]), p(b[0], b[1], b[2]), p(c[0], c[1], c[2]), p(d[0], d[1], d[2]),
                new float[] { layout.frontHigh() ? nx : -nx, ny, nu }, paint);
    }

    private static float[] v(float x, float y, float u) {
        return new float[] { x, y, u };
    }

    // --- The deck, stairs and walkway ---

    // The deck slab (its edge a girder flange, its top deck plates), open over the staircase, on stepped columns.
    private void deck() {
        float t = layout.lengthPx(), w = NuclearTurbineLayout.WIDTH_PX, walk = NuclearTurbineLayout.WALKWAY_X;
        float bottom = NuclearTurbineLayout.DECK_BOTTOM, top = NuclearTurbineLayout.DECK_TOP;
        cbox(0.0F, bottom, 0.0F, walk, top, t, Paint.STRAP, Paint.DECK, Paint.FOOTING, 0);
        cbox(walk, bottom, NuclearTurbineLayout.STAIR_U1, w, top, t, Paint.STRAP, Paint.DECK, Paint.FOOTING, SKIP_NEG_X);
        solidBox(0.0F, bottom, 0.0F, w, top, t);
        // Columns along the back and front edges, the front row clear of the staircase.
        int count = Math.max(2, Math.round(t / 48.0F));
        for (int i = 0; i < count; i++) {
            float u = 6.0F + (t - 18.0F) * i / (count - 1);
            column(4.0F, u, bottom);
            if (u > NuclearTurbineLayout.STAIR_U1 + 4.0F) {
                column(102.0F, u, bottom);
            }
        }
    }

    // A stepped column from the ground to under the deck (canonical x0): a foot, the column and a cap.
    private void column(float x0, float u0, float top) {
        float cw = 6.0F;
        cbox(x0 - 1.0F, 0.0F, u0 - 1.0F, x0 + cw + 1.0F, 2.0F, u0 + cw + 1.0F, Paint.FOOTING, Paint.FOOTING, Paint.FOOTING, 0);
        cbox(x0, 2.0F, u0, x0 + cw, top - 1.0F, u0 + cw, Paint.PLAIN, null, null, SKIP_DOWN);
        cbox(x0 - 0.5F, top - 1.0F, u0 - 0.5F, x0 + cw + 0.5F, top, u0 + cw + 0.5F, Paint.MID, Paint.MID, Paint.MID, SKIP_UP);
    }

    // The staircase up the front strip at the front standard's end: two stringers, gratings for treads and a handrail.
    private void stairs() {
        float u0 = NuclearTurbineLayout.STAIR_U0, u1 = NuclearTurbineLayout.STAIR_U1, top = NuclearTurbineLayout.DECK_TOP;
        int steps = 8;
        float run = (u1 - u0) / steps, rise = top / steps;
        for (int k = 1; k <= steps; k++) {
            cbox(99.25F, rise * k - 1.0F, u0 + run * (k - 1), 108.75F, rise * k, u0 + run * k, Paint.DARK, Paint.GRATE, Paint.DARK, 0);
        }
        for (float sx : new float[] { 98.5F, 109.5F }) {
            float[] a = p(sx, 0.0F, u0 - 1.0F), b = p(sx, top, u1);
            tube(a, b, 0.75F, 4, Paint.PLAIN);
        }
        // The handrail beside it: a post at the foot and a sloping rail up to the deck's railing.
        cbox(110.0F, 0.0F, u0, 111.0F, 15.0F, u0 + 1.0F, Paint.LIT, Paint.LIT, Paint.LIT, 0);
        tube(p(110.5F, 15.0F, u0 + 0.5F), p(110.5F, top + 15.0F, u1 + 1.0F), 0.6F, 4, Paint.LIT);
    }

    // The walkway grating along the front edge of the deck, and its handrail: posts every block, a top and a knee rail.
    private void walkway() {
        float t = layout.lengthPx(), floor = NuclearTurbineLayout.DECK_TOP, u0 = NuclearTurbineLayout.STAIR_U1;
        cbox(98.0F, floor, u0, 111.0F, floor + 1.0F, t - 1.0F, Paint.DARK, Paint.GRATE, Paint.DARK, SKIP_DOWN);
        float top = floor + 16.0F;
        for (float u = u0 + 1.0F; u <= t - 3.0F; u += 16.0F) {
            cbox(110.0F, floor + 1.0F, u, 111.0F, top - 1.0F, u + 1.0F, Paint.LIT, Paint.LIT, Paint.LIT, SKIP_DOWN);
        }
        cbox(110.0F, floor + 1.0F, t - 3.0F, 111.0F, top - 1.0F, t - 2.0F, Paint.LIT, Paint.LIT, Paint.LIT, SKIP_DOWN);
        cbox(109.75F, top - 1.0F, u0, 111.25F, top, t - 2.0F, Paint.LIT, Paint.LIT, Paint.SHADE, 0);
        float knee = floor + 8.5F;
        // It stops inside the last post, so its end never lies on the post's face.
        cbox(110.25F, knee - 0.5F, u0, 110.75F, knee + 0.5F, t - 2.5F, Paint.MID, Paint.MID, Paint.SHADE, 0);
    }

    // --- Along the shaft ---

    // The front standard: a squat bearing pedestal, its bearing cap, two oil lines down its front, and the turning-gear
    // motor on its back side on a ribbed gearbox.
    private void frontStandard() {
        float u0 = NuclearTurbineLayout.STANDARD_U0, u1 = NuclearTurbineLayout.STANDARD_U1, deck = NuclearTurbineLayout.DECK_TOP;
        cbox(36.0F, deck, u0, 64.0F, 62.0F, u1, Paint.PLATE, Paint.PLAIN, null, SKIP_DOWN);
        cbox(40.0F, 62.0F, u0 + 2.0F, 60.0F, 66.0F, u1 - 2.0F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        solidBox(36.0F, deck, u0, 64.0F, 66.0F, u1);
        for (float u : new float[] { u0 + 6.0F, u0 + 10.0F }) {
            prismY(x(65.5F), u, deck, 60.0F, 0.75F, Paint.BANDED, Paint.BANDED);
            float a = x(64.0F), b = x(66.25F);
            prismX(u, 60.0F, Math.min(a, b), Math.max(a, b), 0.75F, Paint.BANDED, Paint.BANDED);
        }
        // The gearbox and its motor.
        cbox(29.0F, 40.0F, u0 + 3.0F, 36.0F, 58.0F, u1 - 3.0F, Paint.M_MOTOR, Paint.PLAIN, null, SKIP_POS_X);
        float a = x(19.0F), b = x(29.0F);
        prismX((u0 + u1) / 2.0F, 49.0F, Math.min(a, b), Math.max(a, b), 5.0F, Paint.BANDED, Paint.PLAIN);
        cbox(21.0F, 54.0F, (u0 + u1) / 2.0F - 2.5F, 26.0F, 57.0F, (u0 + u1) / 2.0F + 2.5F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
    }

    // The high-pressure cylinder: a compact 12-sided casing tapering at its ends, split along the shaft by a heavy
    // flange with a row of bolt heads along each lip, and a gland boss at the front.
    private void highPressure() {
        NuclearTurbineLayout.Span hp = layout.highPressure();
        float sx = layout.shaftX(), sy = layout.shaftY(), r = NuclearTurbineLayout.HP_RADIUS;
        prismU(sx, sy, new float[][] { { hp.u0(), 9.0F }, { hp.u0() + 5.0F, r }, { hp.u1() - 5.0F, r }, { hp.u1(), 9.0F } }, 12, 0.0, Paint.ENAMEL,
                Paint.EN_PLAIN, Paint.EN_PLAIN, null);
        solidCylinders.add(new float[] { sx, sy, r, hp.u0(), hp.u1() });
        float lip = NuclearTurbineLayout.SHAFT_X - r - 2.0F, far = NuclearTurbineLayout.SHAFT_X + r + 2.0F;
        cbox(lip, sy - 1.5F, hp.u0() + 5.0F, far, sy + 1.5F, hp.u1() - 5.0F, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, 0);
        for (float u = hp.u0() + 7.0F; u <= hp.u1() - 7.0F; u += 4.0F) {
            bolt(lip + 0.4F, sy + 1.5F, u, 1.3F);
            bolt(far - 1.7F, sy + 1.5F, u, 1.3F);
        }
        prismU(sx, sy, new float[][] { { hp.u0() - 2.0F, 5.5F }, { hp.u0(), 5.5F } }, 12, 0.0, Paint.BANDED, Paint.PLAIN, Paint.PLAIN, null);
    }

    // A bolt head s px across standing on a face at y (canonical x0 its low corner).
    private void bolt(float x0, float y, float u, float s) {
        cbox(x0, y, u - s / 2.0F, x0 + s, y + s * 0.8F, u + s / 2.0F, Paint.RIVET, Paint.RIVET, Paint.RIVET, SKIP_DOWN);
    }

    // A steam chest beside the high-pressure cylinder (side -1 back, +1 front): the main steam pipe rising through the
    // deck into its stop valve (a flanged body under a boxy hydraulic actuator), a pipe on into the chest (a tall
    // cylinder with a domed top), and the chest's outlet into the cylinder.
    private void steamChest(float cx, float side) {
        float pu = layout.steamPipeU(), cu = layout.chestU(), deck = NuclearTurbineLayout.DECK_TOP, rc = NuclearTurbineLayout.CHEST_RADIUS;
        float pipe = NuclearTurbineLayout.MAIN_PIPE_RADIUS;
        prismY(x(cx), pu, 0.0F, 40.0F, pipe, Paint.BANDED, Paint.PLAIN);
        uprights.add(new float[] { x(cx), pu, pipe, 0.0F, 40.0F });
        prismY(x(cx), pu, deck, deck + 1.5F, pipe + 1.5F, Paint.PLAIN, Paint.MID);
        // The stop valve and its actuator.
        uprightTube(x(cx), pu, new float[][] { { 40.0F, 5.0F }, { 52.0F, 5.0F } }, 12, 0.0, Paint.ENAMEL, null);
        prismY(x(cx), pu, 39.0F, 41.0F, 6.0F, Paint.PLAIN, Paint.MID);
        prismY(x(cx), pu, 50.5F, 52.0F, 6.0F, Paint.PLAIN, Paint.MID);
        cbox(cx - 4.0F, 52.0F, pu - 4.0F, cx + 4.0F, 61.0F, pu + 4.0F, Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN);
        prismY(x(cx), pu, 61.0F, 65.0F, 1.5F, Paint.POLISHED, Paint.PLAIN);
        // On into the chest.
        prismU(x(cx), 46.0F, new float[][] { { pu + 4.5F, 2.5F }, { cu - rc + 0.5F, 2.5F } }, PIPE, Math.PI / PIPE, Paint.BANDED, null, null, null);
        uprightTube(x(cx), cu, new float[][] { { deck, rc }, { 70.0F, rc }, { 74.0F, 5.0F }, { 77.0F, 3.2F }, { 78.0F, 2.0F } }, 12, 0.0, Paint.ENAMEL,
                Paint.EN_PLAIN);
        prismY(x(cx), cu, deck, deck + 2.0F, rc + 1.0F, Paint.PLAIN, Paint.MID);
        uprights.add(new float[] { x(cx), cu, rc, deck, 78.0F });
        // The outlet, from the chest's side into the cylinder's.
        float hp = (float) Math.sqrt(NuclearTurbineLayout.HP_RADIUS * NuclearTurbineLayout.HP_RADIUS - 36.0F);
        float from = cx - side * (rc - 0.5F), to = NuclearTurbineLayout.SHAFT_X + side * (hp - 1.0F);
        float a = x(Math.min(from, to)), b = x(Math.max(from, to));
        prismX(cu, 62.0F, Math.min(a, b), Math.max(a, b), 2.5F, Paint.BANDED, Paint.BANDED);
    }

    // A low-pressure cylinder: its exhaust hood flaring out to the deck over the condenser neck under it, the lower half
    // (worn at handrail height) and the upper half on a bolted horizontal joint, stiffening ribs down both sides, the
    // stepped top cover with the rupture-disc hatch and the two crossover inlets, and the small square sight port in its
    // front wall (a dark recess, the last-stage blades turning in it).
    private void lowPressure(NuclearTurbineLayout.Span lp) {
        float a = lp.u0(), b = lp.u1(), c = layout.portU(lp);
        float x0 = NuclearTurbineLayout.LP_X0, x1 = NuclearTurbineLayout.LP_X1, f = NuclearTurbineLayout.HOOD_FLARE;
        float deck = NuclearTurbineLayout.DECK_TOP, hood = NuclearTurbineLayout.HOOD_TOP, s0 = NuclearTurbineLayout.SPLIT_Y0, s1 = NuclearTurbineLayout.SPLIT_Y1;
        float top = NuclearTurbineLayout.LP_TOP;
        // The hood's flare: its sides slope out to the deck.
        cquad(v(x1 + f, deck, a), v(x1 + f, deck, b), v(x1, hood, b), v(x1, hood, a), 1.0F, 0.25F, 0.0F, Paint.EN_WORN);
        cquad(v(x0 - f, deck, b), v(x0 - f, deck, a), v(x0, hood, a), v(x0, hood, b), -1.0F, 0.25F, 0.0F, Paint.EN_WORN);
        cquad(v(x1 + f, deck, a), v(x0 - f, deck, a), v(x0, hood, a), v(x1, hood, a), 0.0F, 0.0F, -1.0F, Paint.EN_WORN);
        cquad(v(x0 - f, deck, b), v(x1 + f, deck, b), v(x1, hood, b), v(x0, hood, b), 0.0F, 0.0F, 1.0F, Paint.EN_WORN);
        // The lower half, the joint's flange and its bolts, the upper half (its front wall cut round the sight port).
        cbox(x0, hood, a, x1, s0, b, Paint.EN_WORN, null, null, SKIP_UP | SKIP_DOWN);
        cbox(x0 - 2.5F, s0, a, x1 + 2.5F, s1, b, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, 0);
        for (float u = a + 4.0F; u <= b - 4.0F; u += 8.0F) {
            bolt(x0 - 2.1F, s1, u, 1.2F);
            bolt(x1 + 0.9F, s1, u, 1.2F);
        }
        cbox(x0, s1, a, x1, top, b, Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN | SKIP_POS_X);
        // The front wall is one plated surface with the port cut out of it: its pieces share the whole wall's plates.
        float p0 = NuclearTurbineLayout.PORT_Y0, p1 = NuclearTurbineLayout.PORT_Y1, h = NuclearTurbineLayout.PORT_HALF;
        PlateFrame frame = plateFrame(wallPoints(x1, s1, a, top, b), new Plating(Steel.ENAMEL, false, false));
        wall(frame, x1, s1, a, p0, b);
        wall(frame, x1, p1, a, top, b);
        wall(frame, x1, p0, a, p1, c - h);
        wall(frame, x1, p0, c + h, p1, b);
        solidBox(x0 - f, deck, a, x1 + f, top, b);
        sightPort(x1, c);
        // Stiffening ribs down both sides, either side of the sight port.
        for (float k = 8.0F; ; k += 16.0F) {
            boolean any = false;
            for (float u : new float[] { c - k, c + k }) {
                if (u < a + 3.0F || u > b - 3.0F) {
                    continue;
                }
                any = true;
                cbox(x1, hood, u - 0.75F, x1 + 1.5F, s0, u + 0.75F, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, SKIP_NEG_X);
                cbox(x1, s1, u - 0.75F, x1 + 1.5F, top, u + 0.75F, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, SKIP_NEG_X);
                cbox(x0 - 1.5F, hood, u - 0.75F, x0, s0, u + 0.75F, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, SKIP_POS_X);
                cbox(x0 - 1.5F, s1, u - 0.75F, x0, top, u + 0.75F, Paint.EN_PLAIN, Paint.EN_PLAIN, Paint.EN_PLAIN, SKIP_POS_X);
            }
            if (!any) {
                break;
            }
        }
        // The stepped top cover, the rupture-disc hatch with its bolt ring, and the crossover inlets' flanges.
        cbox(x0 + 3.0F, top, a + 3.0F, x1 - 3.0F, NuclearTurbineLayout.COVER_1, b - 3.0F, Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN);
        cbox(x0 + 8.0F, NuclearTurbineLayout.COVER_1, a + 7.0F, x1 - 8.0F, NuclearTurbineLayout.COVER_2, b - 7.0F, Paint.EN_PLAIN, Paint.EN_PLAIN, null,
                SKIP_DOWN);
        float roof = NuclearTurbineLayout.COVER_2, hx = x(NuclearTurbineLayout.SHAFT_X);
        uprightTube(hx, lp.middle(), new float[][] { { roof - 0.5F, 9.0F }, { roof + 2.0F, 9.0F } }, 16, 0.0, Paint.ENAMEL, Paint.EN_PLAIN);
        for (int i = 0; i < 10; i++) {
            double angle = 2.0 * Math.PI * (i + 0.5) / 10.0;
            float bx = NuclearTurbineLayout.SHAFT_X + (float) Math.cos(angle) * 7.5F, bu = lp.middle() + (float) Math.sin(angle) * 7.5F;
            bolt(bx - 0.5F, roof + 2.0F, bu, 1.0F);
        }
        // The neck under the deck down to the condenser, with its flange under the deck.
        cbox(NuclearTurbineLayout.NECK_X0, 0.0F, a + 4.0F, NuclearTurbineLayout.NECK_X1, NuclearTurbineLayout.DECK_BOTTOM - 3.0F, b - 4.0F, Paint.EN_PLAIN, null,
                Paint.EN_PLAIN, SKIP_UP);
        cbox(NuclearTurbineLayout.NECK_X0 - 1.0F, NuclearTurbineLayout.DECK_BOTTOM - 3.0F, a + 3.0F, NuclearTurbineLayout.NECK_X1 + 1.0F,
                NuclearTurbineLayout.DECK_BOTTOM, b - 3.0F, Paint.PLAIN, null, Paint.PLAIN, SKIP_UP);
        solidBox(NuclearTurbineLayout.NECK_X0 - 1.0F, 0.0F, a + 3.0F, NuclearTurbineLayout.NECK_X1 + 1.0F, NuclearTurbineLayout.DECK_BOTTOM, b - 3.0F);
    }

    // A piece of a low-pressure cylinder's front wall at canonical x, from (y0, u0) to (y1, u1), facing out, laid in the
    // whole wall's plates.
    private void wall(PlateFrame frame, float x, float y0, float u0, float y1, float u1) {
        if (y1 - y0 <= 0.01F || u1 - u0 <= 0.01F) {
            return;
        }
        plateIn(frame, wallPoints(x, y0, u0, y1, u1));
    }

    // The corners of a front-facing rectangle at canonical x, wound counter-clockwise seen from outside either way.
    private List<float[]> wallPoints(float x, float y0, float u0, float y1, float u1) {
        List<float[]> points = new java.util.ArrayList<>(List.of(p(x, y0, u1), p(x, y0, u0), p(x, y1, u0), p(x, y1, u1)));
        if (!layout.frontHigh()) {
            java.util.Collections.reverse(points);
        }
        return points;
    }

    // The sight port in the front wall at canonical x: a machine-steel frame round the opening, and a dark recess
    // PORT_DEPTH deep behind it, which the blade ring's tips pass through.
    private void sightPort(float x, float u) {
        float y0 = NuclearTurbineLayout.PORT_Y0, y1 = NuclearTurbineLayout.PORT_Y1, h = NuclearTurbineLayout.PORT_HALF, back = x - NuclearTurbineLayout.PORT_DEPTH;
        cbox(x, y0 - 1.0F, u - h - 1.0F, x + 1.0F, y0, u + h + 1.0F, Paint.PLAIN, Paint.MID, Paint.PLAIN, SKIP_NEG_X);
        cbox(x, y1, u - h - 1.0F, x + 1.0F, y1 + 1.0F, u + h + 1.0F, Paint.PLAIN, Paint.MID, Paint.PLAIN, SKIP_NEG_X);
        cbox(x, y0, u - h - 1.0F, x + 1.0F, y1, u - h, Paint.PLAIN, Paint.MID, Paint.PLAIN, SKIP_NEG_X | SKIP_UP | SKIP_DOWN);
        cbox(x, y0, u + h, x + 1.0F, y1, u + h + 1.0F, Paint.PLAIN, Paint.MID, Paint.PLAIN, SKIP_NEG_X | SKIP_UP | SKIP_DOWN);
        cquad(v(back, y0, u - h), v(back, y0, u + h), v(x, y0, u + h), v(x, y0, u - h), 0.0F, 1.0F, 0.0F, Paint.DEEP);
        cquad(v(back, y1, u + h), v(back, y1, u - h), v(x, y1, u - h), v(x, y1, u + h), 0.0F, -1.0F, 0.0F, Paint.DEEP);
        cquad(v(back, y0, u - h), v(x, y0, u - h), v(x, y1, u - h), v(back, y1, u - h), 0.0F, 0.0F, 1.0F, Paint.DEEP);
        cquad(v(x, y0, u + h), v(back, y0, u + h), v(back, y1, u + h), v(x, y1, u + h), 0.0F, 0.0F, -1.0F, Paint.DEEP);
        cquad(v(back, y0, u + h), v(back, y0, u - h), v(back, y1, u - h), v(back, y1, u + h), 1.0F, 0.0F, 0.0F, Paint.DEEP);
    }

    // A bearing pedestal across the shaft from u0 to u1, with its bearing cap on top.
    private void pedestal(float u0, float u1) {
        float deck = NuclearTurbineLayout.DECK_TOP, top = NuclearTurbineLayout.SHAFT_Y + 6.0F;
        cbox(41.0F, deck, u0, 59.0F, top, u1, Paint.PLATE, Paint.PLAIN, null, SKIP_DOWN);
        cbox(44.0F, top, u0 + 0.5F, 56.0F, top + 2.0F, u1 - 0.5F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        solidBox(41.0F, deck, u0, 59.0F, top + 2.0F, u1);
    }

    // A moisture separator reheater along the low-pressure section at canonical x: a long vessel with domed ends on
    // machine-steel saddles, a flanged manway on each end, drain lines down through the deck, and a flanged collar on
    // its top for each crossover leaving it.
    private void reheater(float cx) {
        NuclearTurbineLayout.Span span = layout.reheaters();
        float u0 = span.u0(), u1 = span.u1(), y = NuclearTurbineLayout.MSR_Y, r = NuclearTurbineLayout.MSR_RADIUS, deck = NuclearTurbineLayout.DECK_TOP;
        float x = x(cx);
        prismU(x, y, new float[][] { { u0, 5.0F }, { u0 + 4.0F, 8.5F }, { u0 + 7.0F, r }, { u1 - 7.0F, r }, { u1 - 4.0F, 8.5F }, { u1, 5.0F } }, 16, 0.0,
                Paint.ENAMEL, Paint.EN_PLAIN, Paint.EN_PLAIN, null);
        solidCylinders.add(new float[] { x, y, r, u0, u1 });
        for (boolean end : new boolean[] { false, true }) {
            float face = end ? u1 : u0, out = end ? 1.0F : -1.0F;
            float m0 = Math.min(face, face + out * 3.0F), m1 = Math.max(face, face + out * 3.0F);
            prismU(x, y, new float[][] { { m0 - (end ? 1.0F : 0.0F), 3.5F }, { m1 + (end ? 0.0F : 1.0F), 3.5F } }, 12, 0.0, Paint.ENAMEL, Paint.EN_PLAIN,
                    Paint.EN_PLAIN, null);
            // The flange short of the manway's cover, so their faces never share a plane.
            float f0 = end ? face + 1.5F : face - 2.5F;
            prismU(x, y, new float[][] { { f0, 4.75F }, { f0 + 1.0F, 4.75F } }, 12, 0.0, Paint.BANDED, Paint.PLAIN, Paint.PLAIN, null);
        }
        int saddles = span.length() > 140.0F ? 3 : 2;
        for (int i = 0; i < saddles; i++) {
            float s = u0 + 16.0F + (span.length() - 32.0F) * i / (saddles - 1);
            cbox(cx - 7.0F, deck, s - 3.0F, cx + 7.0F, y - r + 3.5F, s + 3.0F, Paint.PLAIN, Paint.PLAIN, null, SKIP_DOWN);
            cbox(cx - 8.0F, deck, s - 4.0F, cx + 8.0F, deck + 1.5F, s + 4.0F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        }
        for (float d : new float[] { u0 + 26.0F, u1 - 26.0F }) {
            prismY(x, d, NuclearTurbineLayout.DECK_BOTTOM - 4.0F, y - r + 1.0F, 0.9F, Paint.BANDED, Paint.BANDED);
        }
    }

    // A crossover pipe from a reheater's top, rising and arching over the set into the far side of a low-pressure
    // cylinder's roof: a cubic curve (straight up out of the reheater, straight down into the roof) cut into short
    // straight lengths swept as one tube (each joint's ring shared by the lengths either side, square to the curve there),
    // a flange at every fourth joint, and a flanged collar at each end.
    private void crossover(NuclearTurbineLayout.Arch arch) {
        int segments = 16;
        float r = NuclearTurbineLayout.CROSSOVER_RADIUS;
        float y0 = NuclearTurbineLayout.MSR_Y + NuclearTurbineLayout.MSR_RADIUS + 1.0F, y1 = NuclearTurbineLayout.COVER_2 + 1.0F;
        float crown = NuclearTurbineLayout.CROWN_Y;
        float control = (crown - (y0 + y1) / 8.0F) / 0.75F;
        float[] s = p(arch.x0(), y0, arch.u0()), e = p(arch.x1(), y1, arch.u1());
        float[] c1 = { s[0], control, s[2] }, c2 = { e[0], control, e[2] };
        float[][] points = new float[segments + 1][];
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments, a = (1 - t) * (1 - t) * (1 - t), b = 3 * (1 - t) * (1 - t) * t, c = 3 * (1 - t) * t * t, d = t * t * t;
            points[i] = new float[] { a * s[0] + b * c1[0] + c * c2[0] + d * e[0], a * s[1] + b * c1[1] + c * c2[1] + d * e[1],
                    a * s[2] + b * c1[2] + c * c2[2] + d * e[2] };
        }
        sweep(points, r);
        for (int i = 4; i < segments; i += 4) {
            float[] dir = normalize(sub(points[i + 1], points[i - 1]));
            flange(points[i], dir, r + 1.2F, r, 1.0F);
        }
        // The collars where it leaves the reheater and enters the roof.
        prismY(s[0], s[2], y0 - 3.0F, y0 + 1.5F, r + 1.25F, Paint.PLAIN, Paint.MID);
        prismY(e[0], e[2], NuclearTurbineLayout.COVER_2, NuclearTurbineLayout.COVER_2 + 2.0F, r + 1.25F, Paint.PLAIN, Paint.MID);
    }

    // A tube of radius r through points, its open ends at the first and last: a ring of PIPE corners at each point,
    // square to the curve there (across the bend at a joint), carried along the curve without twisting, and the facets
    // between neighbouring rings in the enamel ramp.
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

    // A flat ring facing normal at centre, from inner out to outer, its corners on a tube's (see ModelMesh.tube).
    private void annulus(float[] centre, float[] normal, float outer, float inner) {
        float[] helper = Math.abs(normal[1]) < 0.9F ? new float[] { 0, 1, 0 } : new float[] { 1, 0, 0 };
        float[] b1 = normalize(cross(normal, helper)), b2 = cross(normal, b1);
        // The same basis as tube() builds along an axis pointing the other way round, so the corners meet its facets.
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

    // The generator: the stator with domed end shields on two feet, two hydrogen coolers on top, the terminal box (FE
    // red) under its steam end with the cable bus down from it, then the slip-ring housing and the exciter.
    private void generator() {
        NuclearTurbineLayout.Span g = layout.generator();
        float sx = layout.shaftX(), sy = layout.shaftY(), r = NuclearTurbineLayout.STATOR_RADIUS, e = NuclearTurbineLayout.STATOR_END_RADIUS;
        float shield = NuclearTurbineLayout.SHIELD, deck = NuclearTurbineLayout.DECK_TOP;
        prismU(sx, sy, new float[][] { { g.u0(), e }, { g.u0() + shield * 0.5F, e + (r - e) * 0.7F }, { g.u0() + shield, r }, { g.u1() - shield, r },
                { g.u1() - shield * 0.5F, e + (r - e) * 0.7F }, { g.u1(), e } }, 16, 0.0, Paint.ENAMEL, Paint.EN_PLAIN, Paint.EN_PLAIN, null);
        solidCylinders.add(new float[] { sx, sy, r, g.u0(), g.u1() });
        // Two feet clear of the terminal box (a short stator stands on the far one and the box).
        float far = g.u1() - shield - 10.0F, near = layout.terminalBox().u1() + 2.0F;
        for (float u : near + 12.0F <= far ? new float[] { near, far } : new float[] { far }) {
            cbox(33.0F, deck, u, 67.0F, sy - r + 4.0F, u + 8.0F, Paint.PLAIN, Paint.PLAIN, null, SKIP_DOWN);
        }
        float length = g.length() - 2.0F * shield;
        for (int k = 0; k < 2; k++) {
            float c0 = g.u0() + shield + length * (0.06F + 0.5F * k), c1 = c0 + length * 0.38F;
            cbox(39.0F, sy + 14.0F, c0, 61.0F, sy + 28.0F, c1, Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN);
            cbox(40.5F, sy + 28.0F, c0 + 1.5F, 59.5F, sy + 30.0F, c1 - 1.5F, Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN);
            // The coolant water's header and return on the cooler's back end.
            float a = x(33.0F), b = x(39.0F);
            prismX(c1 - 3.0F, sy + 18.0F, Math.min(a, b), Math.max(a, b), 1.5F, Paint.BANDED, Paint.PLAIN);
            prismX(c1 - 3.0F, sy + 24.0F, Math.min(a, b), Math.max(a, b), 1.5F, Paint.BANDED, Paint.PLAIN);
            solidBox(39.0F, sy + 14.0F, c0, 61.0F, sy + 30.0F, c1);
        }
        NuclearTurbineLayout.Span box = layout.terminalBox();
        cbox(44.0F, 16.0F, box.u0(), 66.0F, 40.0F, box.u1(), Paint.FE, Paint.FE_LIT, Paint.FE_DARK, 0);
        float bus = NuclearTurbineLayout.BUS_X, half = NuclearTurbineLayout.BUS_HALF;
        cbox(bus - half, 0.0F, layout.busU() - half, bus + half, 16.0F, layout.busU() + half, Paint.PLAIN, null, Paint.PLAIN, SKIP_UP);
        solidBox(bus - half, 0.0F, layout.busU() - half, bus + half, 40.0F, layout.busU() + half);
        // The slip-ring housing and the exciter beyond it.
        NuclearTurbineLayout.Span slip = layout.slipRing(), ex = layout.exciter();
        cbox(42.0F, deck, slip.u0(), 58.0F, 68.0F, slip.u1(), Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN | SKIP_POS_U);
        cbox(39.0F, deck, ex.u0(), 61.0F, 72.0F, ex.u1(), Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN);
        cbox(41.0F, 72.0F, ex.u0() + 1.5F, 59.0F, 74.0F, ex.u1() - 1.5F, Paint.EN_PLAIN, Paint.EN_PLAIN, null, SKIP_DOWN);
        cbox(42.5F, 46.0F, ex.u1(), 57.5F, 62.0F, ex.u1() + 0.5F, Paint.VENT, Paint.PLAIN, Paint.PLAIN, SKIP_NEG_U);
        solidBox(39.0F, deck, slip.u0(), 61.0F, 74.0F, ex.u1());
    }

    // --- The turning parts ---

    // The turning-gear wheel behind the front standard: a 12-sided disc with a tooth on each facet.
    private void wheel(float sx, float sy) {
        float u0 = NuclearTurbineLayout.WHEEL_U0, u1 = NuclearTurbineLayout.WHEEL_U1, r = NuclearTurbineLayout.WHEEL_RADIUS;
        int teeth = 12;
        prismU(sx, sy, new float[][] { { u0, r }, { u1, r } }, teeth, 0.0, Paint.BANDED, Paint.PLAIN, Paint.PLAIN, null);
        double step = 2.0 * Math.PI / teeth;
        for (int k = 0; k < teeth; k++) {
            double mid = k * step + step / 2.0, half = step * 0.22;
            float apothem = r * (float) Math.cos(step / 2.0) - 0.3F, out = r + 1.2F;
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
        float r = 7.0F, c0 = u - 2.0F, c1 = u + 2.0F;
        prismU(sx, sy, new float[][] { { c0, r }, { u - 0.25F, r } }, 12, 0.0, Paint.BANDED, Paint.PLAIN, Paint.SHADE, null);
        prismU(sx, sy, new float[][] { { u + 0.25F, r }, { c1, r } }, 12, 0.0, Paint.BANDED, Paint.PLAIN, Paint.SHADE, null);
        prismU(sx, sy, new float[][] { { u - 0.25F, r - 1.5F }, { u + 0.25F, r - 1.5F } }, 12, 0.0, Paint.BANDED, null, null, null);
        for (int b = 0; b < 6; b++) {
            double a = 2.0 * Math.PI * (b + 0.5) / 6.0;
            float bx = sx + (float) Math.cos(a) * 4.5F, by = sy + (float) Math.sin(a) * 4.5F;
            box(bx - 0.6F, by - 0.6F, c0 - 0.7F, bx + 0.6F, by + 0.6F, c0, Paint.RIVET, Paint.RIVET, Paint.RIVET, SKIP_POS_U);
            box(bx - 0.6F, by - 0.6F, c1, bx + 0.6F, by + 0.6F, c1 + 0.7F, Paint.RIVET, Paint.RIVET, Paint.RIVET, SKIP_NEG_U);
        }
    }

    // A ring of last-stage blades at u: each a flat blade at the axis's 35° from the hub out to a tip shroud, the
    // shrouds' outer faces sweeping past the sight port.
    private void bladeRing(float u) {
        float sx = layout.shaftX(), sy = layout.shaftY(), hub = NuclearTurbineLayout.BLADE_HUB, tip = NuclearTurbineLayout.BLADE_TIP;
        int blades = 20;
        double pitch = Math.toRadians(35.0);
        float half = 1.6F;
        for (int k = 0; k < blades; k++) {
            double angle = 2.0 * Math.PI * k / blades;
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            float[] chord = { -sin * (float) Math.sin(pitch) * half, cos * (float) Math.sin(pitch) * half, (float) Math.cos(pitch) * half };
            float inner = tip - 0.8F;
            float[] root = { sx + cos * hub, sy + sin * hub, u }, top = { sx + cos * inner, sy + sin * inner, u };
            float[] p0 = { root[0] - chord[0], root[1] - chord[1], root[2] - chord[2] }, p1 = { root[0] + chord[0], root[1] + chord[1], root[2] + chord[2] };
            float[] p2 = { top[0] + chord[0], top[1] + chord[1], top[2] + chord[2] }, p3 = { top[0] - chord[0], top[1] - chord[1], top[2] - chord[2] };
            flat(p0, p1, p2, p3, Paint.BLADE, false);
            flat(p3, p2, p1, p0, Paint.BLADE_BACK, false);
            shroud(sx, sy, u, angle, tip, inner, 2.2F / tip, half);
        }
    }

    // A tip shroud: a curved tile round the ring from angle - spread to angle + spread, between inner and outer radius,
    // u +- half along.
    private void shroud(float sx, float sy, float u, double angle, float outer, float inner, double spread, float half) {
        double a0 = angle - spread, a1 = angle + spread;
        float[] o0 = { sx + (float) Math.cos(a0) * outer, sy + (float) Math.sin(a0) * outer }, o1 = { sx + (float) Math.cos(a1) * outer, sy + (float) Math.sin(a1) * outer };
        float[] i0 = { sx + (float) Math.cos(a0) * inner, sy + (float) Math.sin(a0) * inner }, i1 = { sx + (float) Math.cos(a1) * inner, sy + (float) Math.sin(a1) * inner };
        float ua = u - half, ub = u + half;
        float[] out = { (float) Math.cos(angle), (float) Math.sin(angle), 0.0F };
        orient(new float[] { o0[0], o0[1], ua }, new float[] { o1[0], o1[1], ua }, new float[] { o1[0], o1[1], ub }, new float[] { o0[0], o0[1], ub }, out, Paint.BLADE);
        orient(new float[] { i0[0], i0[1], ua }, new float[] { o0[0], o0[1], ua }, new float[] { o0[0], o0[1], ub }, new float[] { i0[0], i0[1], ub },
                new float[] { (float) Math.sin(a0), (float) -Math.cos(a0), 0.0F }, Paint.BLADE_BACK);
        orient(new float[] { i1[0], i1[1], ua }, new float[] { o1[0], o1[1], ua }, new float[] { o1[0], o1[1], ub }, new float[] { i1[0], i1[1], ub },
                new float[] { (float) -Math.sin(a1), (float) Math.cos(a1), 0.0F }, Paint.BLADE_BACK);
        orient(new float[] { i0[0], i0[1], ua }, new float[] { i1[0], i1[1], ua }, new float[] { o1[0], o1[1], ua }, new float[] { o0[0], o0[1], ua },
                new float[] { 0, 0, -1 }, Paint.BLADE_BACK);
        orient(new float[] { i0[0], i0[1], ub }, new float[] { i1[0], i1[1], ub }, new float[] { o1[0], o1[1], ub }, new float[] { o0[0], o0[1], ub },
                new float[] { 0, 0, 1 }, Paint.BLADE_BACK);
    }

    // The shroud ring as it looks at speed: a band round the ring at the tips, streaked along the way it turns.
    private void blurBand(float u) {
        float sx = layout.shaftX(), sy = layout.shaftY(), tip = NuclearTurbineLayout.BLADE_TIP, half = 1.6F;
        prismU(sx, sy, new float[][] { { u - half, tip }, { u + half, tip } }, 24, 0.0, Paint.M_BLUR, null, null, null);
    }
}
