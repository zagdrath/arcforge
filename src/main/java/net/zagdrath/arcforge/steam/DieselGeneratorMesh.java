/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;

import net.zagdrath.arcforge.multiblock.DieselEngine;
import net.zagdrath.arcforge.multiblock.DieselGeneratorLayout;
import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Diesel Generator's model, from DieselGeneratorLayout for its engine (see DieselGeneratorRenderer), on the
// Pumpjack's sheet (it has the generator steel, the FE band and the louvres): the still parts once (and again when its
// ports change), with the control panel's screen in the glow list; the fan and the coupling every frame at the
// flywheel's angle. A large model (docs/TEXTURE_STYLE.md, "Large model texturing"): its big faces plated, the base
// frame's rails in the girder flange, its round parts (the headers, manifolds, turbochargers, silencer, flywheel
// housing) carrying the ramp across them. How it's built and painted: ModelMesh.
public final class DieselGeneratorMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 256;
    private static final float CX = DieselGeneratorLayout.CX, SY = DieselGeneratorLayout.SHAFT_Y;

    private final DieselGeneratorLayout layout;
    // While building a turning part: its turn about the shaft line (along u).
    private boolean spinning;
    private double spinCos = 1.0, spinSin = 0.0;

    public DieselGeneratorMesh(ModelFrame frame) {
        this(frame, DieselGeneratorLayout.of(DieselEngine.of(frame.length())));
    }

    private DieselGeneratorMesh(ModelFrame frame, DieselGeneratorLayout layout) {
        super(frame.alongX(), frame.flip(), DieselGeneratorLayout.WIDTH, layout.length, 0.0F, 0.0F, SHEET_W, SHEET_H);
        this.layout = layout;
        this.modules = true;
    }

    // The still parts and the nozzles of these ports: {solid, cutout, glow}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        base();
        radiator();
        engine();
        turbochargers();
        generator();
        panel();
        for (Port port : ports) {
            nozzle(port);
        }
        return new float[][] { solid.toArray(), cutout.toArray(), glow.toArray() };
    }

    // --- The base ---

    // The sub-base fuel tank (its filler neck and level gauge), the base frame's two rails on it and the engine's
    // mounts.
    private void base() {
        float length = layout.length, top = DieselGeneratorLayout.TANK_TOP, rail = DieselGeneratorLayout.RAIL_TOP;
        box(2.0F, 0.0F, 1.0F, 46.0F, top, length - 1.0F, Paint.PLAIN, Paint.PLAIN, Paint.FOOTING, 0);
        // A welded seam band round the tank's top edge: four strips proud of its sides, closed on top.
        box(1.6F, top - 1.0F, 0.6F, 2.0F, top, length - 0.6F, Paint.MID, null, Paint.DARK, SKIP_POS_X);
        box(46.0F, top - 1.0F, 0.6F, 46.4F, top, length - 0.6F, Paint.MID, null, Paint.DARK, SKIP_NEG_X);
        box(2.0F, top - 1.0F, 0.6F, 46.0F, top, 1.0F, Paint.MID, null, Paint.DARK, SKIP_POS_U | SKIP_NEG_X | SKIP_POS_X);
        box(2.0F, top - 1.0F, length - 1.0F, 46.0F, top, length - 0.6F, Paint.MID, null, Paint.DARK, SKIP_NEG_U | SKIP_NEG_X | SKIP_POS_X);
        solidBoxes.add(new float[] { 2.0F, 0.0F, 1.0F, 46.0F, top, length - 1.0F });
        moduleOrigin = new float[] { 0, top, 0 };
        for (float[] r : DieselGeneratorLayout.RAILS) {
            box(r[0], top, 0.0F, r[1], rail, length, Paint.STRAP, Paint.STRAP, null, 0);
            solidBoxes.add(new float[] { r[0], top, 0.0F, r[1], rail, length });
        }
        moduleOrigin = new float[] { 0, 0, 0 };
        // The filler neck at the right front corner, and the level gauge on the left side.
        uprightTube(44.0F, 3.0F, new float[][] { { top, 1.0F }, { top + 3.0F, 1.0F } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, null);
        uprightTube(44.0F, 3.0F, new float[][] { { top + 3.0F, 1.6F }, { top + 4.0F, 1.6F } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, Paint.MID);
        disc(new float[] { 44.0F, top + 3.0F, 3.0F }, new float[] { 0, -1, 0 }, 1.6F, PIPE_SIDES, Paint.SHADE, Paint.SHADE);
        float gu = layout.length / 2.0F;
        box(1.4F, 1.5F, gu - 4.0F, 2.0F, top - 1.5F, gu + 4.0F, Paint.LIT, null, null, SKIP_POS_X);
        box(1.2F, 2.5F, gu - 3.0F, 1.5F, top - 2.5F, gu + 3.0F, Paint.DEEP, null, null, SKIP_POS_X);
        // The engine's mounts: brackets from the rails in under the crankcase's pan rail to the oil pan's sides (up to the
        // rail's underside, so no face shares a plane with it; closed underneath, where they bridge the gap off the rails).
        float[][] rails = DieselGeneratorLayout.RAILS;
        for (float u : new float[] { layout.engineU0 + 4.0F, layout.engineU1 - 4.0F }) {
            box(rails[0][0] + 1.0F, rail, u - 2.0F, DieselGeneratorLayout.PAN_X0, DieselGeneratorLayout.CASE_Y0, u + 2.0F, Paint.PLAIN, Paint.MID, Paint.SHADE,
                    SKIP_POS_X);
            box(DieselGeneratorLayout.PAN_X1, rail, u - 2.0F, rails[1][1] - 1.0F, DieselGeneratorLayout.CASE_Y0, u + 2.0F, Paint.PLAIN, Paint.MID, Paint.SHADE,
                    SKIP_NEG_X);
        }
        // The alternator's feet.
        for (float u : new float[] { layout.altU0 + 3.0F, layout.altU1 - 3.0F }) {
            box(rails[0][0] + 1.0F, rail, u - 2.0F, rails[1][1] - 1.0F, rail + 1.0F, u + 2.0F, Paint.SHADE, Paint.MID, null, SKIP_DOWN);
        }
    }

    // --- The radiator ---

    // The radiator: its core (fins facing forward) between two side channels, a header tank across its top and
    // bottom, the filler cap, and behind it the fan's shroud with the guard: rings and spokes, so the fan shows through.
    private void radiator() {
        float[] r = DieselGeneratorLayout.RADIATOR;
        float x0 = r[0], y0 = r[1], u0 = r[2], x1 = r[3], y1 = r[4], u1 = r[5];
        // The core: fins on its front, plates on its back, closed top and bottom (the header tanks are narrower than it).
        box(x0 + 2.0F, y0 + 4.0F, u0 + 1.0F, x1 - 2.0F, y1 - 2.0F, u1, Paint.PLAIN, null, null, SKIP_NEG_U);
        face(new float[] { x1 - 2.0F, y0 + 4.0F, u0 + 1.0F }, new float[] { -1, 0, 0 }, new float[] { 0, 1, 0 }, x1 - x0 - 4.0F, y1 - y0 - 6.0F, Paint.GRATE,
                false);
        // The side channels.
        for (float x : new float[] { x0, x1 - 2.0F }) {
            box(x, y0, u0, x + 2.0F, y1 + 4.0F, u1 + 1.0F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        }
        // The header tanks, across.
        prismX((u0 + u1) / 2.0F + 0.5F, y1 + 1.5F, x0 + 2.0F, x1 - 2.0F, 2.5F, Paint.BANDED, Paint.MID);
        prismX((u0 + u1) / 2.0F + 0.5F, y0 + 2.5F, x0 + 2.0F, x1 - 2.0F, 2.2F, Paint.BANDED, Paint.MID);
        solidBoxes.add(new float[] { x0, y0, u0, x1, y1 + 4.0F, u1 + 1.0F });
        // The filler cap on the top header.
        float cu = (u0 + u1) / 2.0F + 0.5F;
        uprightTube(x1 - 8.0F, cu, new float[][] { { y1 + 3.5F, 0.9F }, { y1 + 5.0F, 0.9F } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, null);
        uprightTube(x1 - 8.0F, cu, new float[][] { { y1 + 5.0F, 1.7F }, { y1 + 6.0F, 1.7F } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, Paint.LIT);
        disc(new float[] { x1 - 8.0F, y1 + 5.0F, cu }, new float[] { 0, -1, 0 }, 1.7F, PIPE_SIDES, Paint.SHADE, Paint.SHADE);
        // The shroud: a short drum round the fan, plated outside and in.
        float fr = DieselGeneratorLayout.FAN_R + 1.0F, s0 = DieselGeneratorLayout.SHROUD_U0, s1 = DieselGeneratorLayout.SHROUD_U1;
        tube(new float[] { CX, SY, s0 }, new float[] { CX, SY, s1 }, fr, 20, Paint.BANDED);
        for (int f = 0; f < 20; f++) {
            double a0 = 2.0 * Math.PI * f / 20.0, a1 = 2.0 * Math.PI * (f + 1) / 20.0, mid = (a0 + a1) / 2.0;
            float c0 = (float) Math.cos(a0) * (fr - 0.4F), d0 = (float) Math.sin(a0) * (fr - 0.4F);
            float c1 = (float) Math.cos(a1) * (fr - 0.4F), d1 = (float) Math.sin(a1) * (fr - 0.4F);
            orient(new float[] { CX + c0, SY + d0, s0 }, new float[] { CX + c1, SY + d1, s0 }, new float[] { CX + c1, SY + d1, s1 },
                    new float[] { CX + c0, SY + d0, s1 }, new float[] { -(float) Math.cos(mid), -(float) Math.sin(mid), 0 }, Paint.SHADE);
        }
        // The shroud's back flange and the guard: three rings and eight spokes.
        ring(s1, fr + 0.8F, fr - 0.4F, 20);
        ringU(CX, SY, s1, fr + 0.8F, fr, 20, 0.0, false, Paint.MID, Paint.SHADE);
        ringU(CX, SY, s0, fr, fr - 0.4F, 20, 0.0, false, Paint.MID, Paint.SHADE);
        float gu = s1 - 0.4F;
        for (float rr : new float[] { 4.0F, 7.5F, 11.0F }) {
            for (int i = 0; i < 16; i++) {
                double a0 = 2.0 * Math.PI * i / 16.0, a1 = 2.0 * Math.PI * (i + 1) / 16.0;
                tube(new float[] { CX + (float) Math.cos(a0) * rr, SY + (float) Math.sin(a0) * rr, gu },
                        new float[] { CX + (float) Math.cos(a1) * rr, SY + (float) Math.sin(a1) * rr, gu }, 0.35F, 4, Paint.BANDED);
            }
        }
        // The spokes a little thinner than the rings and a hair behind them, so where they cross no faces share a plane.
        for (int i = 0; i < 8; i++) {
            double a = 2.0 * Math.PI * (i + 0.5) / 8.0;
            tube(new float[] { CX + (float) Math.cos(a) * 2.5F, SY + (float) Math.sin(a) * 2.5F, gu - 0.1F },
                    new float[] { CX + (float) Math.cos(a) * fr, SY + (float) Math.sin(a) * fr, gu - 0.1F }, 0.3F, 4, Paint.BANDED);
        }
        solidCylinders.add(new float[] { CX, SY, fr, s0, s1 });
        // The radiator's stays to the engine's front, along the top.
        for (float x : new float[] { x0 + 1.0F, x1 - 1.0F }) {
            tube(new float[] { x, y1 + 3.0F, u1 + 1.0F }, new float[] { x < CX ? 15.0F : 33.0F, 32.0F, layout.engineU0 + 1.0F }, 0.6F, 4, Paint.BANDED);
        }
    }

    // A flat ring across u at u, facing back (+u), from inner to outer.
    private void ring(float u, float outer, float inner, int sides) {
        ringU(CX, SY, u, outer, inner, sides, 0.0, true, Paint.MID, Paint.SHADE);
    }

    // --- The engine ---

    // The crankcase and oil pan, the two banks with their rocker covers, the intake manifold in the V and an exhaust
    // manifold along the outside of each bank.
    private void engine() {
        float u0 = layout.engineU0, u1 = layout.engineU1;
        float cx0 = DieselGeneratorLayout.CASE_X0, cx1 = DieselGeneratorLayout.CASE_X1, cy0 = DieselGeneratorLayout.CASE_Y0,
                cy1 = DieselGeneratorLayout.CASE_Y1;
        box(DieselGeneratorLayout.PAN_X0, DieselGeneratorLayout.PAN_Y0, u0 + 2.0F, DieselGeneratorLayout.PAN_X1, cy0, u1 - 2.0F, Paint.PLAIN, null,
                Paint.DARK, SKIP_UP);
        // Its bottom is the pan rail's (below), which covers it.
        box(cx0, cy0, u0, cx1, cy1, u1, Paint.PLAIN, Paint.PLAIN, null, SKIP_DOWN);
        solidBoxes.add(new float[] { cx0, cy0, u0, cx1, cy1, u1 });
        // The pan rail: a flange where the pan meets the crankcase.
        box(cx0 - 0.5F, cy0, u0 - 0.5F, cx1 + 0.5F, cy0 + 1.0F, u1 + 0.5F, Paint.MID, Paint.LIT, Paint.DARK, 0);
        // The front cover (the timing gear case) and the crankshaft's nose to the fan.
        box(cx0 + 2.0F, cy0 + 1.0F, u0 - 1.5F, cx1 - 2.0F, cy1 + 4.0F, u0, Paint.PLAIN, Paint.MID, null, SKIP_POS_U);
        for (int side = -1; side <= 1; side += 2) {
            bank(side);
            exhaustManifold(side);
        }
        // The intake manifold down the middle of the V, with a runner to each pair of cylinders.
        float[] in = DieselGeneratorLayout.INTAKE;
        box(in[0], in[1], u0 + 1.0F, in[2], in[3] - 1.0F, u1 - 1.0F, Paint.PLAIN, null, null, SKIP_DOWN | SKIP_UP);
        box(in[0] - 0.4F, in[3] - 1.0F, u0 + 0.6F, in[2] + 0.4F, in[3], u1 - 0.6F, Paint.MID, Paint.LIT, Paint.DARK, 0);
        solidBoxes.add(new float[] { in[0], in[1], u0 + 1.0F, in[2], in[3], u1 - 1.0F });
    }

    // One cylinder bank (side -1 left, 1 right), leaning out of the V: the block and head as one leaning box, a flange
    // line where the head meets it, and a rocker cover for each cylinder with its bolts.
    private void bank(int side) {
        float[] b = DieselGeneratorLayout.bank(side);
        float[] out = { b[2], b[3], 0 }, across = { b[3] * side, -b[2] * side, 0 }, along = { 0, 0, 1 };
        float depth = DieselGeneratorLayout.BANK_DEPTH, height = DieselGeneratorLayout.BANK_HEIGHT;
        float u0 = layout.engineU0 + 1.0F, u1 = layout.engineU1 - 1.0F;
        float[] foot = { b[0] - across[0] * depth / 2.0F, b[1] - across[1] * depth / 2.0F, u0 };
        obox(foot, across, out, along, depth, height, u1 - u0, Paint.PLAIN, Paint.PLAIN);
        // The head joint, 3 px down from the bank's top.
        float[] joint = add(foot, scale(out, height - 3.0F));
        obox(add(add(joint, scale(across, -0.4F)), new float[] { 0, 0, -0.4F }), across, out, along, depth + 0.8F, 0.8F, u1 - u0 + 0.8F, Paint.MID, Paint.LIT);
        float inset = DieselGeneratorLayout.COVER_INSET, coverH = DieselGeneratorLayout.COVER_HEIGHT;
        float[] top = add(foot, scale(out, height));
        for (float[] c : layout.cylinders()) {
            float[] at = { top[0] + across[0] * inset, top[1] + across[1] * inset, c[0] };
            obox(at, across, out, along, depth - 2.0F * inset, coverH, c[1] - c[0], Paint.MID, Paint.LIT);
            // Two bolts on its top, down its middle.
            float[] mid = add(at, add(scale(across, (depth - 2.0F * inset) / 2.0F - 0.4F), scale(out, coverH)));
            for (float bu : new float[] { c[0] + 1.0F, c[1] - 1.8F }) {
                obox(new float[] { mid[0], mid[1], bu }, across, out, along, 0.8F, 0.5F, 0.8F, Paint.RIVET, Paint.RIVET);
            }
        }
    }

    // The exhaust manifold along the outside of a bank: a banded pipe, a short runner from each cylinder into it.
    private void exhaustManifold(int side) {
        float[] m = manifold(side), b = DieselGeneratorLayout.bank(side);
        float r = DieselGeneratorLayout.EXHAUST_R;
        prismU(m[0], m[1], new float[][] { { layout.engineU0 + 1.5F, r }, { layout.engineU1 - 1.0F, r } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED,
                Paint.MID, Paint.SHADE, null);
        float[] across = { b[3] * side, -b[2] * side };
        for (float[] c : layout.cylinders()) {
            float u = (c[0] + c[1]) / 2.0F;
            // From the bank's outer face, half way up it, into the manifold.
            float fx = b[0] - across[0] * DieselGeneratorLayout.BANK_DEPTH / 2.0F + b[2] * 6.5F;
            float fy = b[1] - across[1] * DieselGeneratorLayout.BANK_DEPTH / 2.0F + b[3] * 6.5F;
            tube(new float[] { fx, fy, u }, new float[] { m[0], m[1], u }, 0.8F, 4, Paint.BANDED);
        }
        solidCylinders.add(new float[] { m[0], m[1], r, layout.engineU0 + 1.5F, layout.engineU1 - 1.0F });
    }

    // Where a bank's exhaust manifold runs {x, y}: out from its outer face, below the head.
    private static float[] manifold(int side) {
        float[] b = DieselGeneratorLayout.bank(side);
        float[] across = { b[3] * side, -b[2] * side };
        float half = DieselGeneratorLayout.BANK_DEPTH / 2.0F, reach = DieselGeneratorLayout.EXHAUST_R + 1.6F;
        return new float[] { b[0] - across[0] * (half + reach) + b[2] * 5.0F, b[1] - across[1] * (half + reach) + b[3] * 5.0F };
    }

    // --- The turbochargers, aftercooler and silencer ---

    // Each turbocharger over its bank (a turbine scroll fed up from the manifold, and the compressor ahead of it piped
    // into the aftercooler), the aftercooler across the V, the risers up into the silencer, and the silencer along the
    // top with its outlet and rain cap.
    private void turbochargers() {
        float tr = DieselGeneratorLayout.TURBO_R, ty = DieselGeneratorLayout.TURBO_Y, tl = DieselGeneratorLayout.TURBO_LENGTH;
        float[] ac = layout.aftercooler();
        box(ac[0], ac[1], ac[2], ac[3], ac[4] - 1.0F, ac[5], Paint.PLATE, null, null, SKIP_DOWN | SKIP_UP);
        box(ac[0] - 0.5F, ac[4] - 1.0F, ac[2] - 0.5F, ac[3] + 0.5F, ac[4], ac[5] + 0.5F, Paint.MID, Paint.LIT, Paint.DARK, 0);
        solidBoxes.add(ac);
        float sy = DieselGeneratorLayout.SILENCER_Y, sr = DieselGeneratorLayout.SILENCER_R;
        for (float[] t : layout.turbos()) {
            float x = t[0], u = t[1];
            int side = x < CX ? -1 : 1;
            // The turbine scroll (back) and the compressor (front), on one shaft.
            prismU(x, ty, new float[][] { { u, tr }, { u + tl / 2.0F, tr } }, 12, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
            prismU(x, ty, new float[][] { { u - tl / 2.0F, tr - 0.8F }, { u, tr - 0.8F } }, 12, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
            prismU(x, ty, new float[][] { { u - 0.4F, tr + 0.4F }, { u + 0.4F, tr + 0.4F } }, 12, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
            solidCylinders.add(new float[] { x, ty, tr, u - tl / 2.0F, u + tl / 2.0F });
            // Fed up from the manifold under it. The pipes come in at a slant, so each runs on to the turbo's middle: an
            // end stopped just inside its round shell would poke out of it, open.
            float[] m = manifold(side);
            tube(new float[] { m[0], m[1], u + tl / 4.0F }, new float[] { x, ty, u + tl / 4.0F }, 1.2F, PIPE_SIDES, Paint.BANDED);
            // The compressor's outlet into the aftercooler's side.
            float acx = (side < 0 ? ac[0] : ac[3]) - side * 1.5F;
            tube(new float[] { x, ty - 1.0F, u - tl / 4.0F }, new float[] { acx, ty - 1.0F, Math.min(u - tl / 4.0F, ac[5] - 2.0F) }, 0.9F, PIPE_SIDES,
                    Paint.BANDED);
            // The riser: from the turbine's top up and in to the silencer's side.
            tube(new float[] { x, ty + tr - 0.3F, u + tl / 4.0F }, new float[] { x, sy, u + tl / 4.0F }, 1.0F, PIPE_SIDES, Paint.BANDED);
            tube(new float[] { x, sy, u + tl / 4.0F }, new float[] { CX + side * (sr - 0.3F), sy, u + tl / 4.0F }, 1.0F, PIPE_SIDES, Paint.BANDED);
            // The elbow where it turns: a knuckle round both pipes' open ends.
            box(x - 1.2F, sy - 1.2F, u + tl / 4.0F - 1.2F, x + 1.2F, sy + 1.2F, u + tl / 4.0F + 1.2F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        }
        float[] s = layout.silencer();
        prismU(CX, sy, new float[][] { { s[0], sr }, { s[1], sr } }, 16, 0.0, Paint.BANDED, Paint.PLAIN, Paint.SHADE, null);
        for (float u : new float[] { s[0] + 1.5F, s[1] - 1.5F }) {
            prismU(CX, sy, new float[][] { { u - 0.6F, sr + 0.5F }, { u + 0.6F, sr + 0.5F } }, 16, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
        }
        solidCylinders.add(new float[] { CX, sy, sr, s[0], s[1] });
        // Its saddles down to the aftercooler and intake.
        for (float u : new float[] { s[0] + 4.0F, s[1] - 4.0F }) {
            box(CX - 1.0F, DieselGeneratorLayout.INTAKE[3], u - 0.8F, CX + 1.0F, sy - sr + 0.4F, u + 0.8F, Paint.FACE, Paint.MID, null, SKIP_DOWN | SKIP_UP);
        }
        // The outlet near its front end, up to the rain cap (a flap hinged at the back, which the exhaust lifts: see
        // buildMoving): its mouth open, sooted inside, and the hinge's lug at the back.
        // The pipe and its collar start well down in the silencer: it's round, so its top falls away under the collar's
        // sides, and a collar set on its top line would stand clear of it there.
        float ou = layout.outletU(), or = DieselGeneratorLayout.OUTLET_R, otop = DieselGeneratorLayout.OUTLET_TOP, foot = sy + sr - 2.5F;
        uprightTube(CX, ou, new float[][] { { foot, or }, { otop, or } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, null);
        uprightTube(CX, ou, new float[][] { { foot, or + 0.6F }, { sy + sr + 1.0F, or + 0.6F } }, PIPE_SIDES, Math.PI / PIPE_SIDES, Paint.BANDED, Paint.MID);
        mouth(CX, ou, otop, or, or - 0.35F, 3.0F);
        // The hinge's lug, its top a little under the flap lying shut.
        box(CX - 0.6F, otop - 1.0F, ou + or - 0.2F, CX + 0.6F, otop + 0.1F, ou + or + 0.6F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        uprights.add(new float[] { CX, ou, or, sy, otop });
    }

    // An upright pipe's open mouth at (x, u) down from top: the rim from outer to inner, the wall inside, facing in, and
    // a sooted floor depth px down.
    private void mouth(float x, float u, float top, float outer, float inner, float depth) {
        double step = 2.0 * Math.PI / PIPE_SIDES, start = Math.PI / PIPE_SIDES;
        for (int f = 0; f < PIPE_SIDES; f++) {
            double a0 = start + f * step, a1 = a0 + step, mid = (a0 + a1) / 2.0;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[] o0 = { x + c0 * outer, top, u + s0 * outer }, o1 = { x + c1 * outer, top, u + s1 * outer };
            float[] i0 = { x + c0 * inner, top, u + s0 * inner }, i1 = { x + c1 * inner, top, u + s1 * inner };
            orient(o0, o1, i1, i0, new float[] { 0, 1, 0 }, Paint.MID);
            float[] b0 = { i0[0], top - depth, i0[2] }, b1 = { i1[0], top - depth, i1[2] };
            orient(i0, i1, b1, b0, new float[] { -(float) Math.cos(mid), 0, -(float) Math.sin(mid) }, Paint.DARK);
            orient(new float[] { x, top - depth, u }, b0, b1, new float[] { x, top - depth, u }, new float[] { 0, 1, 0 }, Paint.DEEP);
        }
    }

    // --- The generator end ---

    // The flywheel housing, the alternator (louvred generator steel with its FE band), its end bell and terminal box.
    private void generator() {
        float r = DieselGeneratorLayout.BELL_R, u0 = layout.bellU0, u1 = layout.bellU1;
        prismU(CX, SY, new float[][] { { u0, r - 2.0F }, { u0 + 1.5F, r }, { u1, r } }, 20, 0.0, Paint.BANDED, null, null, null);
        ringU(CX, SY, u0, r - 2.0F, 0.0F, 20, 0.0, false, Paint.PLAIN, Paint.SHADE);
        ringU(CX, SY, u1, r, 0.0F, 20, 0.0, true, Paint.PLAIN, Paint.SHADE);
        for (int i = 0; i < 12; i++) {
            double a = 2.0 * Math.PI * (i + 0.5) / 12.0;
            float bx = CX + (float) Math.cos(a) * (r + 0.3F), by = SY + (float) Math.sin(a) * (r + 0.3F);
            box(bx - 0.4F, by - 0.4F, u1 - 1.0F, bx + 0.4F, by + 0.4F, u1 - 0.2F, Paint.RIVET, Paint.RIVET, Paint.DARK, 0);
        }
        solidCylinders.add(new float[] { CX, SY, r, u0, u1 });
        float x0 = DieselGeneratorLayout.ALT_X0, x1 = DieselGeneratorLayout.ALT_X1, y0 = DieselGeneratorLayout.RAIL_TOP + 1.0F,
                y1 = DieselGeneratorLayout.ALT_Y1;
        float a0 = layout.altU0, a1 = layout.altU1;
        // Its ends are the lips' (below), which cover them.
        box(x0, y0, a0, x1, y1, a1, Paint.GEN_PLATE, Paint.GEN_PLAIN, null, SKIP_DOWN | SKIP_NEG_U | SKIP_POS_U);
        solidBoxes.add(new float[] { x0, y0, a0, x1, y1, a1 });
        // The louvres down both sides, in two banks.
        float span = (a1 - a0 - 10.0F) / 2.0F;
        for (int side = 0; side < 2; side++) {
            float[] normal = { side == 0 ? -1 : 1, 0, 0 };
            float[][] axes = plane(normal);
            float[] right = axes[0], up = axes[1];
            float x = side == 0 ? x0 - 0.05F : x1 + 0.05F;
            for (int i = 0; i < 2; i++) {
                float start = right[2] > 0 ? a0 + 3.0F + i * (span + 4.0F) : a1 - 3.0F - i * (span + 4.0F);
                face(new float[] { x, y0 + 7.0F, start }, right, up, Math.min(12.0F, span - 1.0F), 12.0F, Paint.VENT, false);
            }
        }
        // The FE band round its middle, a lip round its front and back.
        float band = (a0 + a1) / 2.0F;
        box(x0 - 0.3F, y0 + 1.0F, band - 1.0F, x1 + 0.3F, y1 + 0.3F, band + 1.0F, Paint.FE, Paint.FE_LIT, null, SKIP_DOWN);
        for (float u : new float[] { a0, a1 - 1.0F }) {
            box(x0 - 0.5F, y0, u, x1 + 0.5F, y1 + 0.5F, u + 1.0F, Paint.GEN_MID, Paint.GEN_LIT, null, SKIP_DOWN);
        }
        // The end bell, with its bearing cap.
        float bu = layout.altBellU;
        box(x0 + 3.0F, y0 + 2.0F, a1, x1 - 3.0F, y1 - 3.0F, bu, Paint.GEN_PLAIN, Paint.GEN_PLAIN, Paint.GEN_DARK, 0);
        prismU(CX, SY, new float[][] { { bu, 3.0F }, { bu + 1.0F, 3.0F } }, 12, 0.0, Paint.BANDED, Paint.MID, Paint.SHADE, null);
        // The terminal box on top, its cable glands in FE red out of its right side.
        float t0 = a0 + 3.0F, t1 = Math.min(a1 - 3.0F, a0 + 13.0F);
        box(16.0F, y1, t0, 32.0F, y1 + 4.0F, t1, Paint.GEN_PLAIN, null, null, SKIP_DOWN | SKIP_UP);
        box(15.6F, y1 + 4.0F, t0 - 0.4F, 32.4F, y1 + 5.0F, t1 + 0.4F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
        for (int i = 0; i < 3; i++) {
            float gu = t0 + 2.0F + i * (t1 - t0 - 4.0F) / 2.0F;
            box(32.0F, y1 + 1.0F, gu - 1.0F, 33.5F, y1 + 3.0F, gu + 1.0F, Paint.FE, Paint.FE_LIT, Paint.FE_DARK, 0);
            box(33.5F, y1 + 1.5F, gu - 0.5F, 36.0F, y1 + 2.5F, gu + 0.5F, Paint.DARK, null, null, SKIP_NEG_X);
        }
        solidBoxes.add(new float[] { 16.0F, y1, t0, 32.0F, y1 + 5.0F, t1 });
    }

    // The control panel on a bracket at the alternator's right side: a box in the generator steel with its door's
    // frame, a small lit screen (in the glow list) and three buttons, on two arms.
    private void panel() {
        float x0 = DieselGeneratorLayout.PANEL_X0, x1 = DieselGeneratorLayout.PANEL_X1, y0 = DieselGeneratorLayout.PANEL_Y0,
                y1 = DieselGeneratorLayout.PANEL_Y1;
        float u0 = layout.altU0 + 3.0F, u1 = layout.altU0 + 13.0F;
        for (float y : new float[] { y0 + 2.0F, y1 - 3.0F }) {
            box(DieselGeneratorLayout.ALT_X1, y, u0 + 2.0F, x0, y + 1.0F, u1 - 2.0F, Paint.STRAP, Paint.MID, Paint.SHADE, 0);
        }
        box(x0, y0, u0, x1, y1, u1, Paint.GEN_PLAIN, Paint.GEN_MID, Paint.GEN_DARK, 0);
        solidBoxes.add(new float[] { x0, y0, u0, x1, y1, u1 });
        // The door's frame on its face.
        float fx = x1 + 0.3F;
        box(x1, y0 + 0.5F, u0 + 0.5F, fx, y1 - 0.5F, u0 + 1.0F, Paint.GEN_LIT, null, null, SKIP_NEG_X);
        box(x1, y0 + 0.5F, u1 - 1.0F, fx, y1 - 0.5F, u1 - 0.5F, Paint.GEN_DARK, null, null, SKIP_NEG_X);
        // The screen, lit, and three buttons under it (green, amber, red).
        float[] normal = { 1, 0, 0 };
        float[][] axes = plane(normal);
        float[] right = axes[0], up = axes[1];
        float sw = 6.0F, sh = 4.0F, su = (u0 + u1) / 2.0F;
        box(x1, y1 - 7.0F, su - sw / 2.0F - 0.6F, x1 + 0.4F, y1 - 7.0F + sh + 1.2F, su + sw / 2.0F + 0.6F, Paint.DEEP, Paint.MID, null, SKIP_NEG_X);
        glowing = true;
        face(at(new float[] { x1 + 0.45F, y1 - 6.4F, su }, right, up, -sw / 2.0F, 0.0F), right, up, sw, sh, Paint.M_SCREEN, false);
        glowing = false;
        Paint[] buttons = { Paint.LIT, Paint.FE_LIT, Paint.FE };
        for (int i = 0; i < 3; i++) {
            float bu = su - 2.5F + i * 2.5F;
            box(x1, y0 + 2.0F, bu - 0.6F, x1 + 0.6F, y0 + 3.2F, bu + 0.6F, buttons[i], buttons[i], null, SKIP_NEG_X);
        }
    }

    // --- Helpers ---

    // A box from corner o along a (w px), b (h px) and c (l px): three unit axes at right angles, any way turned; its
    // sides in sides, its face along b in top.
    private void obox(float[] o, float[] a, float[] b, float[] c, float w, float h, float l, Paint sides, Paint top) {
        float[][] p = new float[8][];
        for (int i = 0; i < 8; i++) {
            float fa = (i & 1) != 0 ? w : 0, fb = (i & 2) != 0 ? h : 0, fc = (i & 4) != 0 ? l : 0;
            p[i] = new float[] { o[0] + a[0] * fa + b[0] * fb + c[0] * fc, o[1] + a[1] * fa + b[1] * fb + c[1] * fc, o[2] + a[2] * fa + b[2] * fb + c[2] * fc };
        }
        float[] na = { -a[0], -a[1], -a[2] }, nb = { -b[0], -b[1], -b[2] }, nc = { -c[0], -c[1], -c[2] };
        orient(p[0], p[4], p[6], p[2], na, sides);
        orient(p[1], p[3], p[7], p[5], a, sides);
        orient(p[2], p[6], p[7], p[3], b, top);
        orient(p[0], p[1], p[5], p[4], nb, sides);
        orient(p[0], p[2], p[3], p[1], nc, sides);
        orient(p[4], p[5], p[7], p[6], c, sides);
    }

    private static float[] add(float[] a, float[] b) {
        return new float[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
    }

    private static float[] scale(float[] v, float s) {
        return new float[] { v[0] * s, v[1] * s, v[2] * s };
    }

    // --- Each frame ---

    // The fan's blades and hub (and the shaft from it to the engine's nose), and the coupling, turned to angle degrees;
    // and the outlet's rain flap, lifted flap degrees.
    public float[] buildMoving(float angle, float flap) {
        solid = new Floats();
        glowing = false;
        spinning = true;
        spinCos = Math.cos(Math.toRadians(angle));
        spinSin = Math.sin(Math.toRadians(angle));
        float u = DieselGeneratorLayout.FAN_U, reach = DieselGeneratorLayout.FAN_R - 0.5F;
        for (int blade = 0; blade < 6; blade++) {
            double a = 2.0 * Math.PI * blade / 6.0;
            float c = (float) Math.cos(a), s = (float) Math.sin(a), w = 2.0F;
            float[] p0 = { CX + c * 2.5F - s * w, SY + s * 2.5F + c * w, u - 0.6F };
            float[] p1 = { CX + c * reach - s * w, SY + s * reach + c * w, u - 0.6F };
            float[] p2 = { CX + c * reach + s * w, SY + s * reach - c * w, u + 0.6F };
            float[] p3 = { CX + c * 2.5F + s * w, SY + s * 2.5F - c * w, u + 0.6F };
            orient(p0, p1, p2, p3, new float[] { 0, 0, -1 }, Paint.BLADE);
            orient(p0, p1, p2, p3, new float[] { 0, 0, 1 }, Paint.BLADE_BACK);
        }
        tube(new float[] { CX, SY, u - 1.2F }, new float[] { CX, SY, u + 1.2F }, 2.6F, 8, Paint.BANDED);
        tube(new float[] { CX, SY, u + 1.2F }, new float[] { CX, SY, layout.engineU0 - 1.5F }, 1.0F, 6, Paint.SHADE);
        // The coupling: a short drum with bolt heads round it.
        float r = DieselGeneratorLayout.COUPLING_R, c0 = layout.couplingU0, c1 = layout.couplingU1;
        tube(new float[] { CX, SY, c0 }, new float[] { CX, SY, c1 }, r, 10, Paint.BANDED);
        for (int i = 0; i < 6; i++) {
            double a = 2.0 * Math.PI * i / 6.0;
            float bx = CX + (float) Math.cos(a) * (r + 0.3F), by = SY + (float) Math.sin(a) * (r + 0.3F), bu = (c0 + c1) / 2.0F;
            box(bx - 0.5F, by - 0.5F, bu - 0.5F, bx + 0.5F, by + 0.5F, bu + 0.5F, Paint.RIVET, Paint.RIVET, Paint.DARK, 0);
        }
        spinning = false;
        rainFlap(flap);
        return solid.toArray();
    }

    // The rain flap over the outlet: a round plate a little wider than the pipe, hinged at its back edge on the lug,
    // lifted lift degrees from lying shut, with its hinge pin.
    private void rainFlap(float lift) {
        float or = DieselGeneratorLayout.OUTLET_R, otop = DieselGeneratorLayout.OUTLET_TOP, ou = layout.outletU();
        float r = or + 0.5F, thick = 0.4F;
        double a = Math.toRadians(lift);
        float[] across = { 1, 0, 0 }, along = { 0, (float) Math.sin(a), -(float) Math.cos(a) }, normal = { 0, (float) Math.cos(a), (float) Math.sin(a) };
        float[] hinge = { CX, otop + 0.2F, ou + or + 0.4F };
        roundPlate(new float[] { hinge[0] + along[0] * r, hinge[1] + along[1] * r, hinge[2] + along[2] * r }, across, along, normal, r, thick, Paint.MID,
                Paint.SHADE, Paint.DARK);
        tube(new float[] { CX - 1.2F, otop + 0.4F, ou + or + 0.4F }, new float[] { CX + 1.2F, otop + 0.4F, ou + or + 0.4F }, 0.35F, 4, Paint.BANDED);
    }

    // A round plate thick px thick on centre (its underside), across and along it, normal up out of it: its top, its
    // underside and its rim, each corner shared, so it's closed all round.
    private void roundPlate(float[] centre, float[] across, float[] along, float[] normal, float r, float thick, Paint top, Paint under, Paint rim) {
        int sides = PIPE_SIDES * 2;
        double step = 2.0 * Math.PI / sides;
        float[] down = { -normal[0], -normal[1], -normal[2] };
        float[] lid = { centre[0] + normal[0] * thick, centre[1] + normal[1] * thick, centre[2] + normal[2] * thick };
        for (int f = 0; f < sides; f++) {
            double a0 = f * step, a1 = a0 + step, mid = (a0 + a1) / 2.0;
            float[] p0 = rimPoint(centre, across, along, r, a0), p1 = rimPoint(centre, across, along, r, a1);
            float[] q0 = rimPoint(lid, across, along, r, a0), q1 = rimPoint(lid, across, along, r, a1);
            orient(lid, q0, q1, lid, normal, top);
            orient(centre, p0, p1, centre, down, under);
            float c = (float) Math.cos(mid), s = (float) Math.sin(mid);
            orient(p0, p1, q1, q0, new float[] { across[0] * c + along[0] * s, across[1] * c + along[1] * s, across[2] * c + along[2] * s }, rim);
        }
    }

    private static float[] rimPoint(float[] centre, float[] across, float[] along, float r, double a) {
        float c = (float) Math.cos(a) * r, s = (float) Math.sin(a) * r;
        return new float[] { centre[0] + across[0] * c + along[0] * s, centre[1] + across[1] * c + along[1] * s, centre[2] + across[2] * c + along[2] * s };
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
