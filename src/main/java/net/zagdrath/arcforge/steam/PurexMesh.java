/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.List;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The PUREX Plant's model (see PurexRenderer), on the refinery tower's sheet: what's inside its hot cell, seen through
// its lead-glass windows, and the master arms of its manipulators outside them. Its walls are blocks and stay in view.
//  - Inside (drawn full-bright: the cell's own lighting): a stainless floor deck; a lamp in a housing on the ceiling
//    over each block of floor (M_LAMP, in the glow list); against the back wall the dissolver (a plated vessel with a
//    domed head), a bank of centrifugal contactors and the pipework rack over them, a pipe dropping into each contactor
//    and the dissolver.
//  - At each window in a side wall a master-slave manipulator: a through-wall tube above the window, the slave arm
//    hanging inside with its tongs, the master arm and its grip hanging outside (in the outside list).
// In px over the whole box, in the vessel frame (x across the front, u back from it).
public final class PurexMesh extends VesselMesh {
    static final float LAMP_W = 10.0F, LAMP_D = 8.0F;
    static final float TUBE_R = 1.25F, ARM_R = 0.9F;

    // A window's block {x, y, u} in the frame and its wall's outward normal {nx, nu} (one is 0).
    public record Window(int x, int y, int u, int nx, int nu) {}

    private final List<Window> windows;
    final float floor, ceiling, x0, x1, u0, u1;

    public PurexMesh(ModelFrame frame, List<Window> windows) {
        super(frame);
        this.windows = windows;
        this.floor = PX + 1.0F;
        this.ceiling = height - PX;
        this.x0 = PX;
        this.x1 = width - PX;
        this.u0 = PX;
        this.u1 = length - PX;
    }

    // {inside (full-bright), outside, glow}.
    public float[][] build() {
        startStatic();
        interior();
        for (Window window : windows) {
            manipulator(window, true);
        }
        float[] inside = solid.toArray();
        float[] glowing = glow.toArray();
        startStatic();
        for (Window window : windows) {
            manipulator(window, false);
        }
        return new float[][] { inside, solid.toArray(), glowing };
    }

    private void interior() {
        // The deck over the floor's blocks.
        box(x0, PX, u0, x1, floor, u1, Paint.MID, Paint.DECK, null, SKIP_DOWN);
        float room = ceiling - floor;
        // A lamp over each block of floor, its housing round it.
        glowing = false;
        for (float x = x0; x < x1 - 1.0F; x += PX) {
            for (float u = u0; u < u1 - 1.0F; u += PX) {
                float lx = x + (PX - LAMP_W) / 2.0F, lu = u + (PX - LAMP_D) / 2.0F;
                box(lx - 1.0F, ceiling - 1.5F, lu - 1.0F, lx + LAMP_W + 1.0F, ceiling, lu + LAMP_D + 1.0F, Paint.PLAIN, null, Paint.PLAIN, SKIP_UP);
                glowing = true;
                face(new float[] { lx, ceiling - 1.55F, lu }, new float[] { 1, 0, 0 }, new float[] { 0, 0, 1 }, LAMP_W, LAMP_D, Paint.M_LAMP, false);
                glowing = false;
            }
        }
        if (room < 8.0F) {
            return;
        }
        // The dissolver in the back left corner.
        float dr = Math.min(5.0F, (u1 - u0) / 4.0F);
        float dx = x0 + dr + 2.0F, du = u1 - dr - 2.0F;
        float dTop = floor + Math.min(18.0F, room - 4.0F);
        float head = Math.min(3.0F, dr * 0.6F);
        uprightTube(dx, du, new float[][] { { floor, dr - 1.0F }, { floor + 2.0F, dr - 1.0F } }, 12, Math.PI / 12, Paint.BANDED, null);
        uprightTube(dx, du, new float[][] { { floor + 2.0F, dr }, { dTop - head, dr } }, 12, Math.PI / 12, Paint.CASING, null);
        uprightTube(dx, du, head(dTop - head, dr, head, true), 12, Math.PI / 12, Paint.CASING, Paint.PLAIN);
        // The contactor bank along the back wall, and the pipework rack over it.
        float cr = 2.5F, cu = u1 - cr - 1.5F;
        float cTop = floor + Math.min(9.0F, room - 6.0F);
        float rackY = Math.min(ceiling - 4.0F, cTop + 6.0F);
        float start = dx + dr + 4.0F;
        for (float cx = start; cx + cr < x1 - 2.0F; cx += 7.0F) {
            uprightTube(cx, cu, new float[][] { { floor, cr }, { cTop, cr } }, 8, Math.PI / 8, Paint.BANDED, Paint.MID);
            uprightTube(cx, cu, new float[][] { { cTop, 1.0F }, { cTop + 1.5F, 1.0F } }, 6, Math.PI / 6, Paint.BANDED, Paint.MID);
            tube(new float[] { cx, cTop + 1.0F, cu }, new float[] { cx, rackY, cu }, 0.6F, 6, Paint.BANDED);
        }
        // Three lines along the back wall, held off it on brackets.
        for (int i = 0; i < 3; i++) {
            float y = rackY + i * 2.2F, u = u1 - 1.5F - i * 0.4F;
            if (y > ceiling - 2.0F) {
                break;
            }
            tube(new float[] { x0 + 0.5F, y, u - 1.0F }, new float[] { x1 - 0.5F, y, u - 1.0F }, 0.7F, 8, Paint.BANDED);
        }
        tube(new float[] { dx, dTop - 0.5F, du }, new float[] { dx, rackY, du }, 0.8F, 6, Paint.BANDED);
        for (float x = x0 + 6.0F; x < x1 - 2.0F; x += PX) {
            box(x, rackY - 1.5F, u1 - 1.0F, x + 1.0F, rackY + 5.0F, u1, Paint.FACE, Paint.LIT, Paint.SHADE, SKIP_POS_U);
        }
    }

    // A master-slave manipulator through the wall over a window: its tube through the wall above the window, and on
    // the side asked for its arm hanging down to the tongs (inside) or the grip (outside).
    private void manipulator(Window w, boolean inside) {
        float cx = w.x() * PX + PX / 2.0F, cu = w.u() * PX + PX / 2.0F;
        float y = Math.min((w.y() + 1) * PX + 3.0F, height - 3.0F);
        float nx = w.nx(), nu = w.nu();
        float sign = inside ? -1.0F : 1.0F;
        // From the wall's face out 4 px and down to the hand.
        float faceX = cx + nx * PX / 2.0F * sign, faceU = cu + nu * PX / 2.0F * sign;
        float outX = faceX + nx * 4.0F * sign, outU = faceU + nu * 4.0F * sign;
        tube(new float[] { faceX - nx * 2.0F * sign, y, faceU - nu * 2.0F * sign }, new float[] { outX, y, outU }, TUBE_R, 8, Paint.BANDED);
        // The elbow, then the arm (the slave's telescoping tube is polished).
        uprightTube(outX, outU, new float[][] { { y - TUBE_R - 0.5F, TUBE_R + 0.4F }, { y + TUBE_R + 0.5F, TUBE_R + 0.4F } }, 8, Math.PI / 8, Paint.BANDED,
                Paint.MID);
        float hand = y - (inside ? 14.0F : 11.0F);
        if (inside) {
            hand = Math.max(hand, floor + 3.0F);
        }
        uprightTube(outX, outU, new float[][] { { hand, ARM_R }, { y - TUBE_R, ARM_R } }, 6, Math.PI / 6, inside ? Paint.POLISHED : Paint.BANDED, null);
        // Across the arm, along the wall.
        float ax = nu, au = -nx;
        if (inside) {
            // The tongs: two jaws either side of the arm's end, their outer faces just inside the palm's sides.
            for (float side : new float[] { -1.0F, 1.0F }) {
                float jx = outX + ax * 0.8F * side, ju = outU + au * 0.8F * side;
                box(jx - 0.3F, hand - 2.5F, ju - 0.3F, jx + 0.3F, hand + 0.5F, ju + 0.3F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            }
            box(outX - 1.2F, hand - 0.2F, outU - 1.2F, outX + 1.2F, hand + 0.6F, outU + 1.2F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
        } else {
            // The grip: a block in the hand's place, its red trigger toward the wall.
            box(outX - 1.2F, hand - 3.0F, outU - 1.2F, outX + 1.2F, hand, outU + 1.2F, Paint.GEN_MID, Paint.GEN_LIT, Paint.GEN_DARK, 0);
            float tx = outX - nx * 1.5F, tu = outU - nu * 1.5F;
            box(tx - 0.4F, hand - 2.0F, tu - 0.4F, tx + 0.4F, hand - 0.8F, tu + 0.4F, Paint.FE, Paint.FE_LIT, Paint.FE_DARK, 0);
        }
    }
}
