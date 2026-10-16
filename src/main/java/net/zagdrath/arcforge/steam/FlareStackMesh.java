/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import net.zagdrath.arcforge.multiblock.ModelFrame;

// The Flare Stack's stack, standing on its base block (see FlareStackRenderer), on the refinery tower's sheet: a banded
// pipe four blocks tall in flanged lengths, a railed service platform near the top on struts with a ladder up to it,
// and the flare tip with its wind shield and the pilot line up the side. In px over the block (x and u 0 to 16), y from
// the base block's bottom.
public final class FlareStackMesh extends ModelMesh {
    public static final int SHEET_W = 128, SHEET_H = 256;
    public static final float CX = 8.0F, CU = 8.0F, R = 3.0F;
    public static final float BOTTOM = 16.0F, TIP = 74.0F, TIP_TOP = 80.0F, PLATFORM = 62.0F;
    // The ladder's middle across the south side, and half the gap between its stiles.
    private static final float LADDER_X = 9.0F, LADDER_HALF = 4.5F;

    public FlareStackMesh() {
        super(false, false, 16.0F, 16.0F, 0.0F, 0.0F, SHEET_W, SHEET_H);
        this.modules = true;
    }

    // Built once: {solid}.
    public float[] build() {
        startStatic();
        // A square base flange over the base block's socket (a round seat alone left the socket's corners showing),
        // bolted at its corners; on it the round seat, the stack in three lengths with flanges between, and the tip.
        box(1.5F, BOTTOM, 1.5F, 14.5F, BOTTOM + 1.0F, 14.5F, Paint.MID, Paint.PLAIN, null, SKIP_DOWN);
        for (float bx : new float[] { 2.5F, 12.5F }) {
            for (float bu : new float[] { 2.5F, 12.5F }) {
                box(bx, BOTTOM + 1.0F, bu, bx + 1.0F, BOTTOM + 1.6F, bu + 1.0F, Paint.RIVET, Paint.RIVET, null, SKIP_DOWN);
            }
        }
        uprightTube(CX, CU, new float[][] { { BOTTOM + 1.0F, R + 2.0F }, { BOTTOM + 2.5F, R + 2.0F } }, 12, 0.0, Paint.BANDED, Paint.MID);
        uprightTube(CX, CU, new float[][] { { BOTTOM + 2.0F, R }, { TIP, R } }, 12, 0.0, Paint.BANDED, null);
        for (float y : new float[] { 34.0F, 52.0F }) {
            uprightTube(CX, CU, new float[][] { { y - 0.75F, R + 0.8F }, { y + 0.75F, R + 0.8F } }, 12, 0.0, Paint.BANDED, Paint.MID);
        }
        uprightTube(CX, CU, new float[][] { { TIP, R + 0.6F }, { TIP + 1.0F, R + 1.2F }, { TIP_TOP, R + 1.2F } }, 12, 0.0, Paint.BANDED, null);
        uprightInner(CX, CU, TIP + 1.0F, TIP_TOP, R + 0.9F, 12, Paint.DEEP);
        disc(new float[] { CX, TIP_TOP - 3.0F, CU }, new float[] { 0, 1, 0 }, R + 0.9F, 12, Paint.DEEP, Paint.DEEP);
        // The wind shield: a band of plates round the tip.
        uprightTube(CX, CU, new float[][] { { TIP_TOP - 2.0F, R + 1.5F }, { TIP_TOP, R + 1.5F } }, 12, 0.0, Paint.CASING, Paint.DEEP);
        // The pilot line up the north side to the tip.
        tube(new float[] { CX, BOTTOM + 2.0F, CU - R - 0.8F }, new float[] { CX, TIP_TOP - 1.0F, CU - R - 0.8F }, 0.4F, 4, Paint.MID);
        // The service platform: a grating square with a kick plate and railings, its ladder up the south side.
        float p0 = 1.0F, p1 = 15.0F, d = 1.5F;
        box(p0, PLATFORM - d, p0, p1, PLATFORM, p1, Paint.MID, Paint.GRATE, Paint.SHADE, 0);
        // The front and back rails (the back only to the ladder's gap) run to the corners; the sides run between them
        // (the right one on to the back corner, where the gap leaves no back rail), so no two overlap.
        for (float[] rail : new float[][] { { p0, p0, p1, p0 + 0.8F }, { p0, p1 - 0.8F, LADDER_X - LADDER_HALF - 0.5F, p1 }, { p0, p0 + 0.8F, p0 + 0.8F, p1 - 0.8F },
                { p1 - 0.8F, p0 + 0.8F, p1, p1 } }) {
            box(rail[0], PLATFORM + 7.0F, rail[1], rail[2], PLATFORM + 8.0F, rail[3], Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(rail[0], PLATFORM, rail[1], rail[2], PLATFORM + 1.5F, rail[3], Paint.FACE, Paint.LIT, null, SKIP_DOWN);
        }
        for (float[] post : new float[][] { { p0, p0 }, { p1 - 0.8F, p0 }, { p0, p1 - 0.8F }, { p1 - 0.8F, p1 - 0.8F }, { LADDER_X - LADDER_HALF - 1.3F, p1 - 0.8F } }) {
            box(post[0], PLATFORM + 1.5F, post[1], post[0] + 0.8F, PLATFORM + 7.0F, post[1] + 0.8F, Paint.FACE, null, null, SKIP_UP | SKIP_DOWN);
        }
        // Struts from the stack out to the platform's corners. Their open ends are buried: low down well inside the stack
        // (a slanted pipe ended near its skin pokes out of it), up top inside the platform's deck.
        for (float[] corner : new float[][] { { p0 + 1.0F, p0 + 1.0F }, { p1 - 1.0F, p0 + 1.0F }, { p0 + 1.0F, p1 - 1.0F }, { p1 - 1.0F, p1 - 1.0F } }) {
            tube(new float[] { CX + Math.signum(corner[0] - CX) * R * 0.4F, PLATFORM - 9.0F, CU + Math.signum(corner[1] - CU) * R * 0.4F },
                    new float[] { corner[0], PLATFORM - d / 2.0F, corner[1] }, 0.45F, 4, Paint.SHADE);
        }
        // The ladder: two 0.8 px square stiles 9 px apart, closed at the top, and a rung every 4 px, from the base's top
        // to the platform, on the south side. The rungs are thinner than the stiles and end inside them, so no face of
        // one shares a plane with the other.
        float lu = 14.0F, stile = 0.4F, rung = 0.3F;
        for (float x : new float[] { LADDER_X - LADDER_HALF, LADDER_X + LADDER_HALF }) {
            box(x - stile, BOTTOM, lu - stile, x + stile, PLATFORM + 8.0F, lu + stile, Paint.MID, Paint.LIT, null, SKIP_DOWN);
        }
        for (float y = BOTTOM + 3.0F; y < PLATFORM; y += 4.0F) {
            box(LADDER_X - LADDER_HALF, y - rung, lu - rung, LADDER_X + LADDER_HALF, y + rung, lu + rung, Paint.MID, Paint.LIT, Paint.SHADE,
                    SKIP_NEG_X | SKIP_POS_X);
        }
        // Ladder stand-offs from inside the stack to the middle of a rung (rungs every 4 px from BOTTOM + 3).
        for (float y : new float[] { BOTTOM + 15.0F, BOTTOM + 31.0F }) {
            tube(new float[] { CX + R * 0.3F, y, CU + R * 0.4F }, new float[] { LADDER_X, y, lu }, 0.25F, 4, Paint.SHADE);
        }
        return solid.toArray();
    }
}
