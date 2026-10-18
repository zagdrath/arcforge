/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Chemical Reactor Array's model (see ChemicalReactorArrayRenderer), on the refinery tower's sheet: a large model
// (docs/TEXTURE_STYLE.md, "Large model texturing"). Over a 3x3x3 cube, in px in the vessel frame (x across the front, u
// back from it), after a jacketed stirred-tank reactor:
//  - the plinth and a slim steel frame round the cube (posts up the corners, beams round the top), and a gantry beam
//    across the back at mid height that the vessels' lugs rest on;
//  - three upright reaction vessels in a row, one per lane, each on four round legs: a dished bottom head, a straight
//    shell with two jacket bands round its lower half and a body flange at its top, a dished top head with a manway
//    and two nozzles, and a lug bracket out of its back onto the gantry;
//  - on each vessel's front a sight-glass housing: a frame of bars round a pane, the vessel's liquid drawn behind it by
//    the renderer in the lane's fluid colour (window());
//  - on each top head a lantern (four posts round the agitator shaft) under a gearbox and the agitator motor; the shaft's
//    coupling and the motor's cooling fan turn while the lane runs (built apart, about the vessel's axis: agitator());
//  - pipework: a feed header low behind the vessels on stands, out to the cube's sides at a flange each, with a branch
//    into each bottom head through a valve; a vapour header hung from the top beam along the back, fed from a nozzle on
//    each top head;
//  - the control panel low on the front of the middle block.
// The ports' plates and collars come from the casings' own models (arcforge:port_nozzle), the shape they stop at
// from block/chemical_reactor_array/shape.
public final class ChemicalReactorArrayMesh extends VesselMesh {
    public static final int LANES = 3;
    // Each vessel's axis across the front, its shell's radius.
    public static final float[] VESSEL_X = { 9.0F, 24.0F, 39.0F };
    public static final float VESSEL_U = 25.0F, VESSEL_R = 6.8F;
    static final float FRAME = 3.0F;
    // Up the vessel: the legs to the bottom head's rim, the shell, the top head.
    static final float LEG_TOP = 12.0F, BOTTOM_H = 3.5F, SHELL_TOP = 33.0F, HEAD_H = 3.5F;
    static final float CROWN = SHELL_TOP + HEAD_H;
    static final float LANTERN_Y1 = CROWN + 3.5F, GEAR_Y1 = LANTERN_Y1 + 2.0F;
    static final float MOTOR_Y0 = GEAR_Y1, MOTOR_Y1 = MOTOR_Y0 + 4.0F, MOTOR_R = 2.4F;
    // The sight glass: its pane, and the liquid behind it (renderer), in px on the vessel's front.
    public static final float WINDOW_W = 4.0F, WINDOW_Y0 = 16.0F, WINDOW_Y1 = 30.0F;
    public static final float WINDOW_U = VESSEL_U - VESSEL_R - 2.0F;
    // The liquid's back: the dark plate just in front of the shell's flat.
    public static final float LIQUID_BACK = VESSEL_U - VESSEL_R;
    static final float FEED_Y = 8.0F, VAPOUR_Y = 39.0F, HEADER_U = 39.5F, HEADER_R = 1.2F, GANTRY_Y = 24.0F;
    static final int SIDES = 24;

    public ChemicalReactorArrayMesh(ModelFrame frame) {
        super(frame);
    }

    // {solid, cutout, glow}: everything that stands still.
    public float[][] build() {
        startStatic();
        plinth();
        frame();
        for (float x : VESSEL_X) {
            vessel(x);
            window(x);
        }
        pipework();
        panel(1, 0);
        return new float[][] { solid.toArray(), cutout.toArray(), glow.toArray() };
    }

    // One vessel's turning parts at angle 0 (the renderer turns them about its axis): the shaft and its coupling in the
    // lantern, and the fan on top of the motor. {solid}.
    public float[] agitator(int lane) {
        startStatic();
        float x = VESSEL_X[lane], u = VESSEL_U;
        // The shaft down the lantern into the head, and the coupling round it with two bolt lugs.
        uprightTube(x, u, new float[][] { { CROWN - 0.5F, 0.6F }, { LANTERN_Y1, 0.6F } }, 6, Math.PI / 6, Paint.POLISHED, null);
        float cy = (CROWN + LANTERN_Y1) / 2.0F;
        uprightTube(x, u, new float[][] { { cy - 0.7F, 1.3F }, { cy + 0.7F, 1.3F } }, 8, Math.PI / 8, Paint.BANDED, Paint.MID);
        box(x - 1.9F, cy - 0.4F, u - 0.35F, x + 1.9F, cy + 0.4F, u + 0.35F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        // The fan: a hub and four blades, each tipped up a little, over the motor's cap.
        float hubY = MOTOR_Y1 + 0.2F;
        uprightTube(x, u, new float[][] { { hubY, 0.9F }, { hubY + 1.2F, 0.9F } }, 8, Math.PI / 8, Paint.BANDED, Paint.MID);
        for (int blade = 0; blade < 4; blade++) {
            double a = blade * Math.PI / 2.0 + Math.PI / 4.0;
            float dx = (float) Math.cos(a), du = (float) Math.sin(a);
            tube(new float[] { x + dx * 0.8F, hubY + 0.5F, u + du * 0.8F }, new float[] { x + dx * (MOTOR_R - 0.1F), hubY + 0.8F, u + du * (MOTOR_R - 0.1F) },
                    0.4F, 4, Paint.BANDED);
        }
        return solid.toArray();
    }

    // The slim frame: a post up each corner, beams round the top, and the gantry across the back at mid height.
    private void frame() {
        float top = height, p = FRAME;
        float[][] corners = { { 0, 0 }, { width - p, 0 }, { 0, length - p }, { width - p, length - p } };
        for (float[] c : corners) {
            box(c[0], PLINTH, c[1], c[0] + p, top - p, c[1] + p, Paint.PLAIN, null, null, SKIP_DOWN | SKIP_UP);
            solidBoxes.add(new float[] { c[0], PLINTH, c[1], c[0] + p, top, c[1] + p });
        }
        box(0.0F, top - p, 0.0F, width, top, p, Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, 0);
        box(0.0F, top - p, length - p, width, top, length, Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, 0);
        box(0.0F, top - p, p, p, top, length - p, Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, SKIP_NEG_U | SKIP_POS_U);
        box(width - p, top - p, p, width, top, length - p, Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, SKIP_NEG_U | SKIP_POS_U);
        // The gantry: posts up from the deck behind the vessels' gaps, a beam across them at mid height.
        float gu0 = VESSEL_U + VESSEL_R + 1.0F, gu1 = gu0 + 3.0F;
        for (float gx : new float[] { p, (VESSEL_X[0] + VESSEL_X[1]) / 2.0F - 1.0F, (VESSEL_X[1] + VESSEL_X[2]) / 2.0F - 1.0F, width - p - 2.0F }) {
            box(gx, PLINTH, gu0 + 0.5F, gx + 2.0F, GANTRY_Y, gu1 - 0.5F, Paint.PLAIN, null, null, SKIP_DOWN | SKIP_UP);
        }
        box(p, GANTRY_Y, gu0, width - p, GANTRY_Y + 2.5F, gu1, Paint.PLAIN, Paint.PLAIN, Paint.PLAIN, 0);
    }

    // A ring band round a vessel at (x, u), its top and bottom plain rings.
    private void band(float x, float u, float y0, float y1, float r) {
        uprightTube(x, u, new float[][] { { y0, r }, { y1, r } }, SIDES, Math.PI / SIDES, Paint.BANDED, Paint.PLAIN);
        uprightDisc(x, y0, u, r, SIDES, Math.PI / SIDES, false, Paint.SHADE);
    }

    private void vessel(float x) {
        float u = VESSEL_U, r = VESSEL_R;
        // Four legs out on the diagonals, each with a foot pad, up to the bottom head's rim.
        for (int leg = 0; leg < 4; leg++) {
            double a = Math.PI / 4.0 + leg * Math.PI / 2.0;
            float lx = x + (float) Math.cos(a) * (r - 1.2F), lu = u + (float) Math.sin(a) * (r - 1.2F);
            uprightTube(lx, lu, new float[][] { { PLINTH, 0.8F }, { LEG_TOP + 1.0F, 0.8F } }, 8, Math.PI / 8, Paint.BANDED, null);
            box(lx - 1.2F, PLINTH, lu - 1.2F, lx + 1.2F, PLINTH + 0.6F, lu + 1.2F, Paint.MID, Paint.LIT, null, SKIP_DOWN);
        }
        // The dished bottom head, the shell, the top head.
        // Stainless: the polished ramp round the shell and both heads.
        uprightTube(x, u, head(LEG_TOP, r, BOTTOM_H, false), SIDES, Math.PI / SIDES, Paint.POLISHED, null);
        uprightTube(x, u, new float[][] { { LEG_TOP, r }, { SHELL_TOP, r } }, SIDES, Math.PI / SIDES, Paint.POLISHED, null);
        uprightTube(x, u, head(SHELL_TOP, r, HEAD_H, true), SIDES, Math.PI / SIDES, Paint.POLISHED, Paint.LIT);
        uprights.add(new float[] { x, u, r + 0.5F, LEG_TOP - BOTTOM_H, CROWN });
        // The jacket's two bands round the lower shell, and the body flange under the top head.
        band(x, u, LEG_TOP + 0.5F, LEG_TOP + 1.5F, r + 0.5F);
        band(x, u, LEG_TOP + 2.5F, LEG_TOP + 3.5F, r + 0.5F);
        band(x, u, SHELL_TOP - 1.2F, SHELL_TOP + 0.3F, r + 0.6F);
        // The lug out of its back, resting on the gantry: a plate on two gussets.
        float lu0 = u + r - 0.5F, lu1 = u + r + 2.5F;
        box(x - 2.0F, GANTRY_Y + 2.5F, lu0, x + 2.0F, GANTRY_Y + 3.3F, lu1, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        for (float side : new float[] { -1.6F, 1.6F }) {
            box(x + side - 0.3F, GANTRY_Y + 3.3F, lu0, x + side + 0.3F, GANTRY_Y + 6.0F, u + r + 1.0F, Paint.MID, Paint.LIT, Paint.SHADE, SKIP_NEG_U);
        }
        // On the top head: a flanged manway toward the front left, two nozzles to the right and back.
        manway(x - 2.6F, u - 1.8F);
        nozzle(x + 3.0F, u - 1.4F, 2.2F);
        nozzle(x + 2.4F, u + 3.4F, 1.6F);
        // The lantern: four posts round the shaft from the crown to the gearbox, then the gearbox and the motor.
        float c = 1.6F;
        box(x - 2.4F, CROWN - 0.3F, u - 2.4F, x + 2.4F, CROWN + 0.6F, u + 2.4F, Paint.MID, Paint.LIT, Paint.SHADE, SKIP_DOWN);
        for (float[] post : new float[][] { { -c, -c }, { c, -c }, { -c, c }, { c, c } }) {
            box(x + post[0] - 0.4F, CROWN + 0.6F, u + post[1] - 0.4F, x + post[0] + 0.4F, LANTERN_Y1, u + post[1] + 0.4F, Paint.MID, null, null,
                    SKIP_DOWN | SKIP_UP);
        }
        box(x - 2.4F, LANTERN_Y1, u - 2.4F, x + 2.4F, GEAR_Y1, u + 2.4F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
        uprightTube(x, u, new float[][] { { MOTOR_Y0, MOTOR_R }, { MOTOR_Y1, MOTOR_R } }, 12, Math.PI / 12, Paint.BANDED, Paint.DARK);
        // The motor's terminal box, on the side toward the back.
        box(x - 1.0F, MOTOR_Y0 + 0.8F, u + MOTOR_R - 0.3F, x + 1.0F, MOTOR_Y0 + 2.8F, u + MOTOR_R + 1.0F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
    }

    // A manway on the top head: a short neck and a bolted cover.
    private void manway(float x, float u) {
        float y = headAt(SHELL_TOP, VESSEL_R, HEAD_H, (float) Math.hypot(x - xOf(x), u - VESSEL_U));
        uprightTube(x, u, new float[][] { { y - 1.0F, 1.5F }, { y + 0.8F, 1.5F } }, 10, Math.PI / 10, Paint.BANDED, null);
        uprightTube(x, u, new float[][] { { y + 0.8F, 2.0F }, { y + 1.5F, 2.0F } }, 10, Math.PI / 10, Paint.BANDED, Paint.PLAIN);
    }

    // A nozzle on the top head: a pipe stub with a flange.
    private void nozzle(float x, float u, float tall) {
        float y = headAt(SHELL_TOP, VESSEL_R, HEAD_H, (float) Math.hypot(x - xOf(x), u - VESSEL_U));
        uprightTube(x, u, new float[][] { { y - 1.0F, 0.5F }, { y + tall, 0.5F } }, 6, Math.PI / 6, Paint.BANDED, null);
        uprightTube(x, u, new float[][] { { y + tall, 0.9F }, { y + tall + 0.5F, 0.9F } }, 8, Math.PI / 8, Paint.BANDED, Paint.MID);
    }

    // The axis x of the vessel nearest x.
    private static float xOf(float x) {
        float best = VESSEL_X[0];
        for (float v : VESSEL_X) {
            if (Math.abs(v - x) < Math.abs(best - x)) {
                best = v;
            }
        }
        return best;
    }

    // The sight-glass housing on the vessel's front: bars round a WINDOW_W wide pane from WINDOW_Y0 to WINDOW_Y1, a dark
    // back plate set into the shell, and the pane at the housing's face. The liquid goes between them.
    private void window(float x) {
        float half = WINDOW_W / 2.0F, front = WINDOW_U, back = VESSEL_U - VESSEL_R + 0.6F;
        float y0 = WINDOW_Y0, y1 = WINDOW_Y1;
        box(x - half - 1.0F, y0 - 1.0F, front, x - half, y1 + 1.0F, back, Paint.MID, Paint.LIT, Paint.SHADE, SKIP_POS_U);
        box(x + half, y0 - 1.0F, front, x + half + 1.0F, y1 + 1.0F, back, Paint.MID, Paint.LIT, Paint.SHADE, SKIP_POS_U);
        box(x - half, y0 - 1.0F, front, x + half, y0, back, Paint.MID, Paint.LIT, Paint.SHADE, SKIP_POS_U);
        box(x - half, y1, front, x + half, y1 + 1.0F, back, Paint.MID, Paint.LIT, Paint.SHADE, SKIP_POS_U);
        // The back plate, facing out through the pane.
        face(new float[] { x + half, y0, LIQUID_BACK }, new float[] { -1, 0, 0 }, new float[] { 0, 1, 0 }, WINDOW_W, y1 - y0, Paint.DARK, false);
        // The pane, a hair inside the housing's face: seen from the front (-u), right runs toward -x.
        float pu = front + 0.15F;
        glassPane(new float[] { x + half, y0, pu }, new float[] { x - half, y0, pu }, new float[] { x + half, y1, pu }, WINDOW_W, y1 - y0);
    }

    private void pipework() {
        float left = FRAME, right = width - FRAME;
        float top = height - FRAME, vu = length - FRAME / 2.0F;
        // The feed header low behind the vessels, out to the cube's sides at a flange each, on stands off the deck.
        tube(new float[] { left, FEED_Y, HEADER_U }, new float[] { right, FEED_Y, HEADER_U }, HEADER_R, 8, Paint.BANDED);
        tube(new float[] { 0.5F, FEED_Y, HEADER_U }, new float[] { left, FEED_Y, HEADER_U }, HEADER_R + 0.6F, 8, Paint.BANDED);
        tube(new float[] { right, FEED_Y, HEADER_U }, new float[] { width - 0.5F, FEED_Y, HEADER_U }, HEADER_R + 0.6F, 8, Paint.BANDED);
        // The vapour header high along the back, hung from the top beam between the back posts.
        tube(new float[] { left, VAPOUR_Y, vu }, new float[] { right, VAPOUR_Y, vu }, HEADER_R, 8, Paint.BANDED);
        for (float x = 16.0F; x < width - 10.0F; x += 16.0F) {
            box(x - 1.5F, FEED_Y - HEADER_R - 0.5F, HEADER_U - 1.0F, x + 1.5F, FEED_Y - HEADER_R, HEADER_U + 1.0F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(x - 0.5F, PLINTH, HEADER_U - 0.5F, x + 0.5F, FEED_Y - HEADER_R - 0.5F, HEADER_U + 0.5F, Paint.FACE, null, null, SKIP_DOWN | SKIP_UP);
            box(x - 0.4F, VAPOUR_Y + HEADER_R - 0.2F, vu - 0.4F, x + 0.4F, top, vu + 0.4F, Paint.FACE, null, null, SKIP_UP);
            box(x - 1.2F, VAPOUR_Y - HEADER_R - 0.4F, vu - 1.6F, x + 1.2F, VAPOUR_Y - HEADER_R, vu + 1.6F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        }
        for (float x : VESSEL_X) {
            // Feed: forward from the header under the vessel, through a valve, and up into the bottom head's outlet.
            float bottom = LEG_TOP - BOTTOM_H;
            tube(new float[] { x, FEED_Y, HEADER_U }, new float[] { x, FEED_Y, VESSEL_U }, 0.8F, 8, Paint.BANDED);
            tube(new float[] { x, FEED_Y - 0.7F, VESSEL_U }, new float[] { x, bottom + 0.6F, VESSEL_U }, 0.7F, 8, Paint.BANDED);
            float valve = VESSEL_U + VESSEL_R + 4.5F;
            box(x - 1.3F, FEED_Y - 1.3F, valve - 1.0F, x + 1.3F, FEED_Y + 1.3F, valve + 1.0F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(x - 0.4F, FEED_Y + 1.3F, valve - 0.4F, x + 0.4F, FEED_Y + 3.0F, valve + 0.4F, Paint.FE, Paint.FE_LIT, Paint.FE_DARK, SKIP_DOWN);
            // Vapour: up from the back nozzle, then back to the header.
            float nx = x + 2.4F, nu = VESSEL_U + 3.4F;
            float rise = headAt(SHELL_TOP, VESSEL_R, HEAD_H, (float) Math.hypot(2.4F, 3.4F)) + 2.1F;
            tube(new float[] { nx, rise, nu }, new float[] { nx, VAPOUR_Y, nu }, 0.6F, 6, Paint.BANDED);
            tube(new float[] { nx, VAPOUR_Y, nu - 0.6F }, new float[] { nx, VAPOUR_Y, vu }, 0.6F, 6, Paint.BANDED);
        }
    }
}
