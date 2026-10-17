/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// A containment's Equipment Hatch (see EquipmentHatchRenderer), on the refinery tower's sheet: a large model 3x3 blocks
// across a block-thick wall. In px over its 3x3 (x across, y up, u through the wall from its outside face, 0 to 16).
//  - The frame: a plated steel sleeve filling the square round the opening, a flange ring proud of it on each face
//    round the round opening (plated as one surface, with a bolt circle outside), the plated bore lining the opening
//    from flange to flange, and the hinge's fixed knuckles and brackets on the right (seen from outside).
//  - The door (drawn swung about the hinge's axis, HINGE_X, HINGE_U): a thick round door, its outer face plated, a
//    handwheel on a boss in its middle, the hinge arms out to its moving knuckles.
public final class EquipmentHatchMesh extends ModelMesh {
    public static final float SIZE = 48.0F, FRAME = 7.2F, CENTRE = 24.0F;
    public static final float OPENING = 15.0F, DOOR_R = 17.0F, RING_R = 23.5F;
    // The hinge's axis (upright) and the door's thickness in front of the flange.
    public static final float HINGE_X = 44.0F, HINGE_U = -3.5F, DOOR_FRONT = -5.0F, DOOR_BACK = -1.2F;
    static final int ROUND = 32;

    public EquipmentHatchMesh(ModelFrame frame) {
        super(frame.alongX(), frame.flip(), SIZE, PX, 0.0F, 0.0F, VesselMesh.SHEET_W, VesselMesh.SHEET_H);
        this.modules = true;
    }

    // The frame: {solid}.
    public float[] buildFrame() {
        startStatic();
        float s = SIZE, f = FRAME, c = CENTRE;
        box(0.0F, 0.0F, 0.0F, s, f, PX, Paint.PLATE, Paint.PLATE, Paint.PLATE, 0);
        box(0.0F, s - f, 0.0F, s, s, PX, Paint.PLATE, Paint.PLATE, Paint.PLATE, 0);
        box(0.0F, f, 0.0F, f, s - f, PX, Paint.PLATE, null, null, SKIP_UP | SKIP_DOWN);
        box(s - f, f, 0.0F, s, s - f, PX, Paint.PLATE, null, null, SKIP_UP | SKIP_DOWN);
        // The flange rings, a px proud of each face, round the opening.
        ringU(c, c, -1.0F, RING_R, OPENING, ROUND, 0.0, false, Paint.PLAIN, Paint.PLAIN);
        tube(new float[] { c, c, -1.0F }, new float[] { c, c, 0.0F }, RING_R, ROUND, Paint.BANDED);
        ringU(c, c, PX + 1.0F, RING_R, OPENING, ROUND, 0.0, true, Paint.PLAIN, Paint.PLAIN);
        tube(new float[] { c, c, PX }, new float[] { c, c, PX + 1.0F }, RING_R, ROUND, Paint.BANDED);
        // The bore through the wall from flange to flange, facing in: the round opening's lining.
        bore(c, c, -1.0F, PX + 1.0F, OPENING);
        // The bolt circle outside the door.
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2.0 / 16 + Math.PI / 16;
            float bx = c + (float) Math.cos(a) * 20.5F, by = c + (float) Math.sin(a) * 20.5F;
            box(bx - 0.75F, by - 0.75F, -1.8F, bx + 0.75F, by + 0.75F, -1.0F, Paint.RIVET, Paint.RIVET, Paint.RIVET, SKIP_POS_U);
        }
        // The hinge's fixed knuckles, on brackets from the flange.
        for (float y : new float[] { 9.0F, 31.0F }) {
            uprightTube(HINGE_X, HINGE_U, new float[][] { { y, 1.6F }, { y + 4.0F, 1.6F } }, 8, Math.PI / 8, Paint.BANDED, Paint.MID);
            box(HINGE_X - 1.0F, y + 0.5F, HINGE_U, HINGE_X + 1.0F, y + 3.5F, -1.0F, Paint.MID, Paint.LIT, Paint.SHADE, SKIP_POS_U);
        }
        uprightTube(HINGE_X, HINGE_U, new float[][] { { 8.0F, 0.7F }, { 40.0F, 0.7F } }, 6, Math.PI / 6, Paint.POLISHED, Paint.LIT);
        return solid.toArray();
    }

    // A round lining from u0 to u1, its facets facing its axis (seen from inside the opening).
    private void bore(float cx, float cy, float u0, float u1, float r) {
        double step = 2.0 * Math.PI / ROUND;
        float fw = 2.0F * r * (float) Math.sin(Math.PI / ROUND);
        for (int f = 0; f < ROUND; f++) {
            double a0 = f * step + step / 2.0, a1 = a0 + step, mid = (a0 + a1) / 2.0;
            float x0 = cx + (float) Math.cos(a0) * r, y0 = cy + (float) Math.sin(a0) * r;
            float x1 = cx + (float) Math.cos(a1) * r, y1 = cy + (float) Math.sin(a1) * r;
            float[] in = { -(float) Math.cos(mid), -(float) Math.sin(mid), 0.0F };
            roundFacet(new float[] { x0, y0, u0 }, new float[] { x1, y1, u0 }, new float[] { x1, y1, u1 }, new float[] { x0, y0, u1 }, in, Paint.PLATE,
                    f, ROUND, fw, u1 - u0);
        }
    }

    // The door, shut: {solid}.
    public float[] buildDoor() {
        startStatic();
        float c = CENTRE;
        tube(new float[] { c, c, DOOR_FRONT }, new float[] { c, c, DOOR_BACK }, DOOR_R, ROUND, Paint.BANDED);
        ringU(c, c, DOOR_FRONT, DOOR_R, 0.0F, ROUND, 0.0, false, Paint.PLATE, Paint.PLATE);
        ringU(c, c, DOOR_BACK, DOOR_R, 0.0F, ROUND, 0.0, true, Paint.PLAIN, Paint.PLAIN);
        // The boss and the handwheel: a hub, four spokes and the rim (short straight tubes round a circle).
        float hub = DOOR_FRONT - 2.0F, wheel = DOOR_FRONT - 3.0F;
        tube(new float[] { c, c, hub }, new float[] { c, c, DOOR_FRONT }, 3.0F, 8, Paint.BANDED);
        ringU(c, c, hub, 3.0F, 0.0F, 8, Math.PI / 8, false, Paint.MID, Paint.SHADE);
        tube(new float[] { c, c, wheel }, new float[] { c, c, hub }, 1.0F, 6, Paint.POLISHED);
        int rim = 12;
        float wr = 7.0F;
        for (int i = 0; i < rim; i++) {
            double a0 = i * Math.PI * 2.0 / rim, a1 = (i + 1) * Math.PI * 2.0 / rim;
            tube(new float[] { c + (float) Math.cos(a0) * wr, c + (float) Math.sin(a0) * wr, wheel },
                    new float[] { c + (float) Math.cos(a1) * wr, c + (float) Math.sin(a1) * wr, wheel }, 0.6F, 6, Paint.POLISHED);
        }
        // The spokes start inside the stem (not at its axis, where they'd cross each other and its end).
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0 + Math.PI / 4.0;
            tube(new float[] { c + (float) Math.cos(a) * 0.8F, c + (float) Math.sin(a) * 0.8F, wheel }, new float[] { c + (float) Math.cos(a) * wr, c + (float) Math.sin(a) * wr, wheel }, 0.45F, 6, Paint.BANDED);
        }
        // The hinge arms from the door's edge to the moving knuckles.
        for (float y : new float[] { 13.0F, 35.0F }) {
            // Half a px inside the knuckle's ends, so the arm's top and bottom aren't in the plane of its caps.
            box(c + DOOR_R - 3.0F, y + 0.5F, DOOR_FRONT + 0.5F, HINGE_X, y + 2.5F, DOOR_BACK - 0.5F, Paint.MID, Paint.LIT, Paint.SHADE, 0);
            uprightTube(HINGE_X, HINGE_U, new float[][] { { y, 1.6F }, { y + 3.0F, 1.6F } }, 8, Math.PI / 8, Paint.BANDED, Paint.MID);
        }
        return solid.toArray();
    }
}
