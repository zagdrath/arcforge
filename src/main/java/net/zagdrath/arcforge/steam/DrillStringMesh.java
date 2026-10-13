/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

// The Oil Derrick's drill string, on the Pumpjack's sheet (see OilDerrickRenderer): the crown block's sheaves under the
// crown, the hoist lines down to the travelling block (at `hoist`, 0 at the top of its run, 1 at the bottom), and the
// square kelly from it through the rotary table into the hole, turned to `angle` degrees about its axis. Built every
// frame. In the rig's frame: x and u across (0 to 48 px, the bore in the middle), y up from the floor's bottom.
public final class DrillStringMesh extends ModelMesh {
    private static final float C = 24.0F, CROWN = 78.0F, TOP = 66.0F, BOTTOM = 26.0F, FLOOR = 16.0F;

    private boolean spinning;
    private double spinCos = 1.0, spinSin = 0.0;

    public DrillStringMesh() {
        super(false, false, 48.0F, 48.0F, 0.0F, 0.0F, PumpjackMesh.SHEET_W, PumpjackMesh.SHEET_H);
    }

    public float[] build(float hoist, float angle) {
        solid = new Floats();
        glowing = false;
        // The crown block's sheaves and the rotary table round the hole.
        box(C - 5.0F, CROWN - 3.0F, C - 3.0F, C + 5.0F, CROWN, C + 3.0F, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
        prismX(C, CROWN - 1.5F, C - 6.0F, C + 6.0F, 1.2F, Paint.BANDED, Paint.MID);
        box(C - 6.0F, FLOOR - 4.0F, C - 6.0F, C + 6.0F, FLOOR, C + 6.0F, Paint.PLATE, Paint.DECK, Paint.SHADE, 0);
        float block = TOP - (TOP - BOTTOM) * Math.max(0.0F, Math.min(1.0F, hoist));
        for (int i = 0; i < 4; i++) {
            float x = C - 3.0F + 2.0F * i;
            tube(new float[] { x, CROWN - 3.0F, C }, new float[] { x, block + 6.0F, C }, 0.25F, 4, Paint.DARK);
        }
        box(C - 3.5F, block, C - 2.5F, C + 3.5F, block + 6.0F, C + 2.5F, Paint.PLAIN, Paint.MID, Paint.SHADE, 0);
        box(C - 1.5F, block - 3.0F, C - 1.5F, C + 1.5F, block, C + 1.5F, Paint.BANDED, Paint.MID, Paint.SHADE, 0);
        spinning = true;
        spinCos = Math.cos(Math.toRadians(angle));
        spinSin = Math.sin(Math.toRadians(angle));
        tube(new float[] { C, block - 3.0F, C }, new float[] { C, -8.0F, C }, 1.3F, 4, Paint.BANDED);
        spinning = false;
        return solid.toArray();
    }

    @Override
    float[] turn(float[] p) {
        if (!spinning) {
            return p;
        }
        float dx = p[0] - C, du = p[2] - C;
        return new float[] { C + (float) (dx * spinCos - du * spinSin), p[1], C + (float) (dx * spinSin + du * spinCos) };
    }
}
