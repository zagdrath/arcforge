/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;

import net.zagdrath.arcforge.multiblock.DerrickLayout;
import net.zagdrath.arcforge.multiblock.ModelFrame;

// A formed Oil Derrick's model, from DerrickLayout (see OilDerrickRenderer), on the Pumpjack's sheet (it has the house
// steel and the FE band): the still parts once (and again when its ports change); every frame the drill string (the
// hoist lines from the crown block, the travelling block at `hoist`, 0 at the top of its run and 1 at the bottom, and
// the square kelly from it down through the rotary table into the hole, turned to the kelly's angle) and the
// drawworks drum, turning as the line pays out. A large model (docs/TEXTURE_STYLE.md, "Large model texturing"): the
// legs and girts are thin parts laid in the girder flange at 1:1, the deck and the engine house continuous plated
// surfaces, and the drum, sheaves and rotary table carry the ramp across them.
public final class DerrickMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 256;
    private static final float C = DerrickLayout.C, W = DerrickLayout.WIDTH;

    // While building a turning part: the kelly (about the bore's upright axis) or the drum (about its axis along x).
    private boolean kellyTurning, drumTurning;
    private double turnCos = 1.0, turnSin = 0.0;

    public DerrickMesh(ModelFrame frame) {
        super(frame.alongX(), frame.flip(), W, W, 0.0F, 0.0F, SHEET_W, SHEET_H);
        this.modules = true;
    }

    // The still parts and the nozzles of these ports: {solid, cutout, glow}.
    public float[][] buildStatic(List<Port> ports) {
        startStatic();
        deck();
        rotaryTable();
        tower();
        crown();
        drawworks();
        engineHouse();
        ladder();
        for (Port port : ports) {
            nozzle(port);
        }
        return new float[][] { solid.toArray(), cutout.toArray(), glow.toArray() };
    }

    // --- The rig floor ---

    // The deck: plated, on short legs and girders over the well, open over the hole; a stair of two steps up its front
    // and a railing round its edge (open at the stair and the ladder).
    private void deck() {
        float top = DerrickLayout.DECK_TOP, plate = DerrickLayout.DECK_PLATE, u0 = DerrickLayout.DECK_U0, hole = DerrickLayout.HOLE;
        float under = top - plate;
        // The deck plate in four pieces round the hole.
        box(0.0F, under, u0, W, top, C - hole, Paint.MID, Paint.DECK, Paint.SHADE, 0);
        box(0.0F, under, C + hole, W, top, W, Paint.MID, Paint.DECK, Paint.SHADE, 0);
        box(0.0F, under, C - hole, C - hole, top, C + hole, Paint.MID, Paint.DECK, Paint.SHADE, SKIP_NEG_U | SKIP_POS_U);
        box(C + hole, under, C - hole, W, top, C + hole, Paint.MID, Paint.DECK, Paint.SHADE, SKIP_NEG_U | SKIP_POS_U);
        // The apron either side of the stair.
        box(0.0F, under, 0.0F, DerrickLayout.STAIR_X0, top, u0, Paint.MID, Paint.DECK, Paint.SHADE, SKIP_POS_U);
        box(DerrickLayout.STAIR_X1, under, 0.0F, W, top, u0, Paint.MID, Paint.DECK, Paint.SHADE, SKIP_POS_U);
        // The substructure: short legs at the corners and mid sides, girders under the deck's edges.
        moduleOrigin = new float[] { 0, 0, 0 };
        for (float x : new float[] { 0.5F, C - 1.5F, W - 3.5F }) {
            for (float u : new float[] { 0.5F, C - 1.5F, W - 3.5F }) {
                if (Math.abs(x - (C - 1.5F)) < 0.1F && Math.abs(u - (C - 1.5F)) < 0.1F) {
                    continue;
                }
                box(x, 0.0F, u, x + 3.0F, under, u + 3.0F, Paint.STRAP, null, null, SKIP_UP | SKIP_DOWN);
            }
        }
        for (float u : new float[] { 1.0F, W - 3.0F }) {
            box(0.5F, under - 2.0F, u, W - 0.5F, under, u + 2.0F, Paint.STRAP, Paint.MID, Paint.SHADE, SKIP_UP);
        }
        for (float x : new float[] { 1.0F, W - 3.0F }) {
            box(x, under - 2.0F, 3.0F, x + 2.0F, under, W - 3.0F, Paint.STRAP, Paint.MID, Paint.SHADE, SKIP_UP);
        }
        // The stair: two steps up the front, between stringers.
        float sx0 = DerrickLayout.STAIR_X0, sx1 = DerrickLayout.STAIR_X1;
        box(sx0 + 1.0F, 2.0F, 0.0F, sx1 - 1.0F, 3.0F, u0 / 2.0F, Paint.MID, Paint.GRATE, Paint.SHADE, 0);
        box(sx0 + 1.0F, 5.0F, u0 / 2.0F, sx1 - 1.0F, 6.0F, u0, Paint.MID, Paint.GRATE, Paint.SHADE, 0);
        for (float x : new float[] { sx0, sx1 - 1.0F }) {
            box(x, 0.0F, 0.0F, x + 1.0F, top, u0, Paint.FACE, Paint.MID, null, SKIP_DOWN);
        }
        solidBoxes.add(new float[] { 0.0F, 0.0F, 0.0F, W, top, W });
        // The railing: along the back and both sides, and the front either side of the stair and the ladder.
        float lx = DerrickLayout.ladder(top)[0], half = DerrickLayout.LADDER_W / 2.0F + 1.0F;
        railingX(0.5F, lx - half, 0.5F, top);
        railingX(lx + half, sx0 - 1.0F, 0.5F, top);
        railingX(sx1 + 1.0F, W - 0.5F, 0.5F, top);
        railingX(0.5F, W - 0.5F, W - 1.0F, top);
        railingU(0.5F, 0.5F, W - 0.5F, top);
        railingU(W - 1.0F, 0.5F, W - 0.5F, top);
    }

    // A railing along x at u from x0 to x1 (a post at most every 8 px, a knee and a hand rail), on a floor at y.
    private void railingX(float x0, float x1, float u, float y) {
        if (x1 - x0 < 2.0F) {
            return;
        }
        float h = DerrickLayout.RAIL_H;
        box(x0, y + h - 0.6F, u, x1, y + h, u + 0.6F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        box(x0, y + h / 2.0F, u, x1, y + h / 2.0F + 0.6F, u + 0.6F, Paint.FACE, Paint.MID, Paint.SHADE, 0);
        int posts = Math.max(1, (int) Math.ceil((x1 - x0) / 8.0F));
        for (int i = 0; i <= posts; i++) {
            float x = x0 + Math.min(x1 - x0 - 0.6F, (x1 - x0) * i / posts);
            box(x, y, u, x + 0.6F, y + h - 0.6F, u + 0.6F, Paint.FACE, null, null, SKIP_UP | SKIP_DOWN);
        }
    }

    // The same along u at x.
    private void railingU(float x, float u0, float u1, float y) {
        if (u1 - u0 < 2.0F) {
            return;
        }
        float h = DerrickLayout.RAIL_H;
        box(x, y + h - 0.6F, u0, x + 0.6F, y + h, u1, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        box(x, y + h / 2.0F, u0, x + 0.6F, y + h / 2.0F + 0.6F, u1, Paint.FACE, Paint.MID, Paint.SHADE, 0);
        int posts = Math.max(1, (int) Math.ceil((u1 - u0) / 8.0F));
        for (int i = 0; i <= posts; i++) {
            float u = u0 + Math.min(u1 - u0 - 0.6F, (u1 - u0) * i / posts);
            box(x, y, u, x + 0.6F, y + h - 0.6F, u + 0.6F, Paint.FACE, null, null, SKIP_UP | SKIP_DOWN);
        }
    }

    // The rotary table over the hole: a round table on its base, the master bushing's square in its middle.
    private void rotaryTable() {
        float top = DerrickLayout.DECK_TOP, r = DerrickLayout.TABLE_R, t = DerrickLayout.TABLE_TOP;
        box(C - r - 1.0F, top, C - r - 1.0F, C + r + 1.0F, top + 1.0F, C + r + 1.0F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        uprightTube(C, C, new float[][] { { top + 1.0F, r }, { t, r } }, 16, 0.0, Paint.BANDED, null);
        // The table's top: a ring round the bushing, and the bushing's square socket.
        ringY(C, C, t, r, 2.5F, 16);
        box(C - 2.5F, t - 0.5F, C - 2.5F, C + 2.5F, t + 0.3F, C + 2.5F, Paint.DARK, Paint.SHADE, null, SKIP_DOWN);
        uprights.add(new float[] { C, C, r, top, t });
    }

    // A flat ring facing up at y round (x, u), from inner to outer (on a large model, plated as one surface).
    private void ringY(float x, float u, float y, float outer, float inner, int sides) {
        for (int f = 0; f < sides; f++) {
            double a0 = 2.0 * Math.PI * f / sides, a1 = 2.0 * Math.PI * (f + 1) / sides;
            float[] p0 = { x + (float) Math.cos(a0) * inner, y, u + (float) Math.sin(a0) * inner };
            float[] p1 = { x + (float) Math.cos(a1) * inner, y, u + (float) Math.sin(a1) * inner };
            float[] p2 = { x + (float) Math.cos(a1) * outer, y, u + (float) Math.sin(a1) * outer };
            float[] p3 = { x + (float) Math.cos(a0) * outer, y, u + (float) Math.sin(a0) * outer };
            orient(p0, p1, p2, p3, new float[] { 0, 1, 0 }, Paint.MID);
        }
    }

    // --- The tower ---

    // The four legs from the deck to the crown, the girts round all four sides, and X-bracing in every bay (the bottom
    // bay from the deck to the first girt).
    private void tower() {
        float top = DerrickLayout.DECK_TOP, crown = DerrickLayout.CROWN_Y;
        for (int corner = 0; corner < 4; corner++) {
            leg(DerrickLayout.leg(corner, top), DerrickLayout.leg(corner, crown));
            // Its base plate on the deck.
            float[] foot = DerrickLayout.leg(corner, top);
            box(foot[0] - 2.5F, top, foot[2] - 2.5F, foot[0] + 2.5F, top + 0.8F, foot[2] + 2.5F, Paint.MID, Paint.LIT, null, SKIP_DOWN);
        }
        float[] girts = DerrickLayout.girts();
        // The sides, as pairs of corners: front, right, back, left.
        int[][] sides = { { 0, 1 }, { 1, 3 }, { 3, 2 }, { 2, 0 } };
        float g = DerrickLayout.GIRT_W / 2.0F;
        for (float y : girts) {
            for (int[] side : sides) {
                girt(DerrickLayout.leg(side[0], y), DerrickLayout.leg(side[1], y), g);
            }
        }
        float[] levels = new float[girts.length + 2];
        levels[0] = top + 1.0F;
        System.arraycopy(girts, 0, levels, 1, girts.length);
        levels[levels.length - 1] = crown - 1.0F;
        for (int i = 0; i + 1 < levels.length; i++) {
            for (int[] side : sides) {
                float[] a0 = DerrickLayout.leg(side[0], levels[i]), a1 = DerrickLayout.leg(side[1], levels[i]);
                float[] b0 = DerrickLayout.leg(side[0], levels[i + 1]), b1 = DerrickLayout.leg(side[1], levels[i + 1]);
                // Leave the front's bottom bay open over the stair (the way onto the floor).
                if (i == 0 && side[0] == 0 && side[1] == 1) {
                    continue;
                }
                tube(inward(a0, a1), inward(b1, b0), DerrickLayout.BRACE_R, 4, Paint.BANDED);
                tube(inward(a1, a0), inward(b0, b1), DerrickLayout.BRACE_R, 4, Paint.BANDED);
            }
        }
    }

    // A leg from a (its foot) to b (its top): a square bar with its faces square to the tower's sides, leaning in, all
    // four plated alike (each a long plate with its own lit and dark edges, so its corners read square), and one row of
    // rivet heads down the middle of its face on the tower's front (or back), none running round a corner.
    private void leg(float[] a, float[] b) {
        float h = DerrickLayout.LEG_W / 2.0F;
        float su = Math.signum(a[2] - C);
        for (int axis = 0; axis < 2; axis++) {
            for (int side = -1; side <= 1; side += 2) {
                float[][] p = new float[4][];
                for (int i = 0; i < 4; i++) {
                    float[] end = i < 2 ? a : b;
                    float along = (i == 0 || i == 3) ? -h : h;
                    p[i] = axis == 0 ? new float[] { end[0] + side * h, end[1], end[2] + along } : new float[] { end[0] + along, end[1], end[2] + side * h };
                }
                float[] out = axis == 0 ? new float[] { side, 0, 0 } : new float[] { 0, 0, side };
                orient(p[0], p[1], p[2], p[3], out, Paint.PLAIN);
            }
        }
        // The rivet row down the middle of its front (or back) face, a head every 8 px.
        for (float y = a[1] + 4.0F; y < b[1] - 2.0F; y += 8.0F) {
            float t = (y - a[1]) / (b[1] - a[1]);
            float x = a[0] + (b[0] - a[0]) * t, u = a[2] + (b[2] - a[2]) * t + su * h;
            box(x - 0.5F, y - 0.5F, Math.min(u, u + su * 0.4F), x + 0.5F, y + 0.5F, Math.max(u, u + su * 0.4F), Paint.RIVET, Paint.RIVET, Paint.DARK, 0);
        }
        // Its cap under the crown deck.
        box(b[0] - h, b[1] - 0.5F, b[2] - h, b[0] + h, b[1], b[2] + h, Paint.MID, Paint.LIT, null, SKIP_DOWN);
    }

    // A girt from leg a to leg b at the same height: a bar in the girder flange, its ends meeting the legs' faces.
    private void girt(float[] a, float[] b, float g) {
        float inset = DerrickLayout.LEG_W / 2.0F;
        if (Math.abs(a[2] - b[2]) < 0.01F) {
            float x0 = Math.min(a[0], b[0]) + inset, x1 = Math.max(a[0], b[0]) - inset;
            box(x0, a[1] - g, a[2] - g, x1, a[1] + g, a[2] + g, Paint.STRAP, Paint.MID, Paint.SHADE, SKIP_NEG_X | SKIP_POS_X);
        } else {
            float u0 = Math.min(a[2], b[2]) + inset, u1 = Math.max(a[2], b[2]) - inset;
            box(a[0] - g, a[1] - g, u0, a[0] + g, a[1] + g, u1, Paint.STRAP, Paint.MID, Paint.SHADE, SKIP_NEG_U | SKIP_POS_U);
        }
    }

    // A point at leg a, moved toward leg b by the leg's half width (where a brace meets the leg's side).
    private static float[] inward(float[] a, float[] b) {
        float[] d = normalize(sub(b, a));
        float s = DerrickLayout.LEG_W / 2.0F + 0.3F;
        return new float[] { a[0] + d[0] * s, a[1] + d[1] * s, a[2] + d[2] * s };
    }

    // --- The crown ---

    // The crown platform (grating on a plated frame, over the legs' tops) with its railing, the crown block's sheaves on
    // their frame over the bore, and the gin pole standing at its back.
    private void crown() {
        float y = DerrickLayout.CROWN_Y, d = DerrickLayout.CROWN_DECK, h = DerrickLayout.CROWN_HALF;
        float x0 = C - h - 1.0F, x1 = C + h + 1.0F;
        box(x0, y, x0, x1, y + d, x1, Paint.STRAP, Paint.GRATE, Paint.SHADE, 0);
        solidBoxes.add(new float[] { x0, y, x0, x1, y + d, x1 });
        float top = y + d;
        // Its railing (open at the ladder's top, on the front left).
        float lx = DerrickLayout.ladder(y)[0];
        railingX(lx + DerrickLayout.LADDER_W / 2.0F + 0.5F, x1 - 0.6F, x0, top);
        railingX(x0, x1 - 0.6F, x1 - 0.6F, top);
        railingU(x0, x0 + 3.0F, x1 - 0.6F, top);
        railingU(x1 - 0.6F, x0, x1 - 0.6F, top);
        // The crown block: two beams across the platform, the sheaves between them on one shaft.
        float sy = DerrickLayout.SHEAVE_Y, sr = DerrickLayout.SHEAVE_R;
        for (float u : new float[] { C - 4.0F, C + 2.0F }) {
            box(x0 + 2.0F, top, u, x1 - 2.0F, top + 2.0F, u + 2.0F, Paint.STRAP, Paint.MID, null, SKIP_DOWN);
        }
        for (float x : new float[] { C - 6.0F, C + 5.0F }) {
            box(x, top + 2.0F, C - 3.0F, x + 1.0F, sy + 1.0F, C + 3.0F, Paint.PLAIN, Paint.MID, null, SKIP_DOWN);
        }
        for (int i = 0; i < 4; i++) {
            float x = C - 4.5F + i * 2.5F;
            prismX(C, sy, x, x + 1.6F, sr, Paint.BANDED, Paint.MID);
        }
        prismX(C, sy, C - 6.5F, C + 6.5F, 0.6F, Paint.BANDED, Paint.LIT);
        // The gin pole: leaning back a little from the platform's back edge, a small sheave at its tip.
        float gu = x1 - 2.0F;
        tube(new float[] { C, top, gu }, new float[] { C, DerrickLayout.GIN_TOP, gu + 1.5F }, 0.9F, 6, Paint.BANDED);
        prismX(gu + 1.5F, DerrickLayout.GIN_TOP - 1.0F, C - 0.8F, C + 0.8F, 1.2F, Paint.BANDED, Paint.MID);
    }

    // --- The drawworks and engine house ---

    // The drawworks: a plated frame on the deck at the back, its side plates holding the drum's shaft, and the drum
    // (the still frame; the drum itself turns, see buildMoving).
    private void drawworks() {
        float[] f = DerrickLayout.DRAWWORKS;
        float top = DerrickLayout.DECK_TOP, dy = DerrickLayout.DRUM_Y, du = DerrickLayout.DRUM_U, fr = DerrickLayout.DRUM_FLANGE_R;
        box(f[0], top, f[2], f[1], top + 2.5F, f[3], Paint.PLATE, Paint.PLAIN, null, SKIP_DOWN);
        for (float x : new float[] { f[0], f[1] - 2.0F }) {
            box(x, top + 2.5F, f[2] + 1.0F, x + 2.0F, dy + fr, f[3] - 1.0F, Paint.PLATE, Paint.MID, null, SKIP_DOWN);
            // The shaft's bearing housing, outside the side plate.
            prismX(du, dy, x < C ? x - 1.0F : x + 2.0F, x < C ? x : x + 3.0F, 2.0F, Paint.BANDED, Paint.MID);
        }
        // The brake band's guard over the drum's top back.
        box(DerrickLayout.DRUM_X0, dy + fr + 0.5F, du + 1.0F, DerrickLayout.DRUM_X1, dy + fr + 1.5F, f[3] - 1.0F, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
        solidBoxes.add(new float[] { f[0], top, f[2], f[1], dy + fr, f[3] });
    }

    // The engine house beside the drawworks: walls of house steel with louvres, a roof with a lip, the FE band round it
    // under the roof, and the chain guard from it to the drum's shaft.
    private void engineHouse() {
        float[] e = DerrickLayout.ENGINE_HOUSE;
        box(e[0], e[1], e[2], e[3], e[4], e[5], Paint.GEN_PLATE, Paint.GEN_PLAIN, null, SKIP_DOWN);
        box(e[0] - 0.6F, e[4], e[2] - 0.6F, e[3] + 0.6F, e[4] + 1.0F, e[5] + 0.6F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
        box(e[0] - 0.3F, e[4] - 3.0F, e[2] - 0.3F, e[3] + 0.3F, e[4] - 1.5F, e[5] + 0.3F, Paint.FE, Paint.FE_LIT, Paint.FE_DARK, SKIP_UP);
        // Louvres in its front and right walls.
        face(new float[] { e[3] - 1.5F, e[1] + 4.0F, e[2] - 0.05F }, new float[] { -1, 0, 0 }, new float[] { 0, 1, 0 }, e[3] - e[0] - 3.0F, 8.0F, Paint.VENT,
                false);
        face(new float[] { e[3] + 0.05F, e[1] + 4.0F, e[5] - 2.0F }, new float[] { 0, 0, -1 }, new float[] { 0, 1, 0 }, 9.0F, 8.0F, Paint.VENT, false);
        solidBoxes.add(e);
        // The chain guard: from the house's left wall to the drawworks' right bearing.
        float dy = DerrickLayout.DRUM_Y, du = DerrickLayout.DRUM_U;
        box(DerrickLayout.DRAWWORKS[1] + 1.0F, dy - 3.0F, du - 3.0F, e[0], dy + 3.0F, du + 3.0F, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
    }

    // --- The ladder ---

    // The ladder up the front left leg: two stiles on stand-offs from the leg and a rung every RUNG_STEP.
    private void ladder() {
        float y0 = DerrickLayout.DECK_TOP, y1 = DerrickLayout.CROWN_Y + DerrickLayout.CROWN_DECK, half = DerrickLayout.LADDER_W / 2.0F;
        for (float side : new float[] { -half, half - 0.8F }) {
            float[] a = DerrickLayout.ladder(y0), b = DerrickLayout.ladder(DerrickLayout.CROWN_Y);
            tube(new float[] { a[0] + side + 0.4F, y0, a[2] }, new float[] { b[0] + side + 0.4F, y1 + 4.0F, b[2] + (b[2] - a[2]) * 4.0F / (y1 - y0) },
                    0.5F, 4, Paint.MID);
        }
        for (float y = y0 + 3.0F; y < y1; y += DerrickLayout.RUNG_STEP) {
            float[] at = DerrickLayout.ladder(y);
            box(at[0] - half + 0.6F, y, at[2] - 0.35F, at[0] + half - 0.6F, y + 0.7F, at[2] + 0.35F, Paint.FACE, Paint.LIT, Paint.SHADE, 0);
        }
        // Stand-offs from the leg every 16 px.
        for (float y = y0 + 8.0F; y < y1; y += 16.0F) {
            float[] at = DerrickLayout.ladder(y);
            box(at[0] - 0.4F, y, at[2], at[0] + 0.4F, y + 0.8F, at[2] + DerrickLayout.LADDER_OUT - 1.0F, Paint.SHADE, Paint.MID, null, 0);
        }
    }

    // --- Each frame ---

    // The drill string and the drawworks drum: the hoist lines from the crown block to the travelling block at hoist
    // (0 at the top of its run, 1 at the bottom), the hook, and the kelly from it through the rotary table into the hole
    // turned to kellyAngle degrees; the fast line from the drum to the crown; the drum turned to drumAngle degrees.
    public float[] buildMoving(float hoist, float kellyAngle, float drumAngle) {
        solid = new Floats();
        glowing = false;
        float block = DerrickLayout.TRAVEL_TOP - (DerrickLayout.TRAVEL_TOP - DerrickLayout.TRAVEL_BOTTOM) * Math.max(0.0F, Math.min(1.0F, hoist));
        float top = DerrickLayout.SHEAVE_Y - DerrickLayout.SHEAVE_R;
        for (int i = 0; i < 4; i++) {
            float x = C - 3.75F + 2.5F * i;
            tube(new float[] { x, top, C }, new float[] { x, block + 6.0F, C }, 0.25F, 4, Paint.DARK);
        }
        float[][] fast = DerrickLayout.fastLine();
        tube(fast[0], fast[1], 0.3F, 4, Paint.DARK);
        // The travelling block and the hook under it.
        box(C - 3.5F, block, C - 2.5F, C + 3.5F, block + 6.0F, C + 2.5F, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
        prismX(C, block + 3.0F, C - 3.8F, C + 3.8F, 1.2F, Paint.BANDED, Paint.MID);
        box(C - 1.5F, block - 3.0F, C - 1.5F, C + 1.5F, block, C + 1.5F, Paint.BANDED, Paint.MID, Paint.SHADE, 0);
        kellyTurning = true;
        turnCos = Math.cos(Math.toRadians(kellyAngle));
        turnSin = Math.sin(Math.toRadians(kellyAngle));
        tube(new float[] { C, block - 3.0F, C }, new float[] { C, -8.0F, C }, 1.3F, 4, Paint.BANDED);
        kellyTurning = false;
        drumTurning = true;
        turnCos = Math.cos(Math.toRadians(drumAngle));
        turnSin = Math.sin(Math.toRadians(drumAngle));
        float dy = DerrickLayout.DRUM_Y, du = DerrickLayout.DRUM_U, x0 = DerrickLayout.DRUM_X0, x1 = DerrickLayout.DRUM_X1;
        prismX(du, dy, x0 + 1.0F, x1 - 1.0F, DerrickLayout.DRUM_R, Paint.BANDED, Paint.MID);
        prismX(du, dy, x0, x0 + 1.0F, DerrickLayout.DRUM_FLANGE_R, Paint.BANDED, Paint.MID);
        prismX(du, dy, x1 - 1.0F, x1, DerrickLayout.DRUM_FLANGE_R, Paint.BANDED, Paint.MID);
        prismX(du, dy, DerrickLayout.DRAWWORKS[0], DerrickLayout.DRAWWORKS[1], 0.9F, Paint.BANDED, Paint.LIT);
        // The wraps of line on the drum (a few turns standing proud), and bolt heads round each flange's face, so its
        // turning shows.
        for (int i = 0; i < 3; i++) {
            float x = x0 + 3.0F + i * 5.0F;
            prismX(du, dy, x, x + 1.0F, DerrickLayout.DRUM_R + 0.3F, Paint.DARK, Paint.DARK);
        }
        for (float x : new float[] { x0 - 0.5F, x1 }) {
            for (int i = 0; i < 6; i++) {
                double a = 2.0 * Math.PI * i / 6.0;
                float by = dy + (float) Math.cos(a) * 4.5F, bu = du + (float) Math.sin(a) * 4.5F;
                box(x, by - 0.5F, bu - 0.5F, x + 0.5F, by + 0.5F, bu + 0.5F, Paint.RIVET, Paint.RIVET, Paint.DARK, 0);
            }
        }
        drumTurning = false;
        return solid.toArray();
    }

    // The kelly turns about the bore's upright axis; the drum about its own axis along x.
    @Override
    float[] turn(float[] p) {
        if (kellyTurning) {
            float dx = p[0] - C, du = p[2] - C;
            return new float[] { C + (float) (dx * turnCos - du * turnSin), p[1], C + (float) (dx * turnSin + du * turnCos) };
        }
        if (drumTurning) {
            float dy = p[1] - DerrickLayout.DRUM_Y, du = p[2] - DerrickLayout.DRUM_U;
            return new float[] { p[0], DerrickLayout.DRUM_Y + (float) (dy * turnCos - du * turnSin), DerrickLayout.DRUM_U + (float) (dy * turnSin + du * turnCos) };
        }
        return p;
    }
}
