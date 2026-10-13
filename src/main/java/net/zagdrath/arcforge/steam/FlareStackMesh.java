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
        // The seat on the base, the stack in three lengths with flanges between, and the tip.
        uprightTube(CX, CU, new float[][] { { BOTTOM, R + 2.0F }, { BOTTOM + 2.0F, R + 2.0F } }, 12, 0.0, Paint.BANDED, Paint.MID);
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
        for (float[] rail : new float[][] { { p0, p0, p1, p0 + 0.8F }, { p0, p1 - 0.8F, LADDER_X - LADDER_HALF - 0.5F, p1 }, { p0, p0, p0 + 0.8F, p1 }, { p1 - 0.8F, p0, p1, p1 } }) {
            box(rail[0], PLATFORM + 7.0F, rail[1], rail[2], PLATFORM + 8.0F, rail[3], Paint.MID, Paint.LIT, Paint.SHADE, 0);
            box(rail[0], PLATFORM, rail[1], rail[2], PLATFORM + 1.5F, rail[3], Paint.FACE, Paint.LIT, null, SKIP_DOWN);
        }
        for (float[] post : new float[][] { { p0, p0 }, { p1 - 0.8F, p0 }, { p0, p1 - 0.8F }, { p1 - 0.8F, p1 - 0.8F }, { LADDER_X - LADDER_HALF - 1.3F, p1 - 0.8F } }) {
            box(post[0], PLATFORM + 1.5F, post[1], post[0] + 0.8F, PLATFORM + 7.0F, post[1] + 0.8F, Paint.FACE, null, null, SKIP_UP | SKIP_DOWN);
        }
        // Struts from the stack out to the platform's corners.
        for (float[] corner : new float[][] { { p0 + 1.0F, p0 + 1.0F }, { p1 - 1.0F, p0 + 1.0F }, { p0 + 1.0F, p1 - 1.0F }, { p1 - 1.0F, p1 - 1.0F } }) {
            tube(new float[] { CX + Math.signum(corner[0] - CX) * R * 0.7F, PLATFORM - 9.0F, CU + Math.signum(corner[1] - CU) * R * 0.7F },
                    new float[] { corner[0], PLATFORM - d, corner[1] }, 0.45F, 4, Paint.SHADE);
        }
        // The ladder: two 1 px stiles 9 px apart and a 1 px rung every 4 px, from the base's top to the platform, on the
        // south side.
        float lu = 14.0F;
        for (float x : new float[] { LADDER_X - LADDER_HALF, LADDER_X + LADDER_HALF }) {
            tube(new float[] { x, BOTTOM, lu }, new float[] { x, PLATFORM + 8.0F, lu }, 0.5F, 4, Paint.BANDED);
        }
        for (float y = BOTTOM + 3.0F; y < PLATFORM; y += 4.0F) {
            tube(new float[] { LADDER_X - LADDER_HALF, y, lu }, new float[] { LADDER_X + LADDER_HALF, y, lu }, 0.5F, 4, Paint.BANDED);
        }
        // Ladder stand-offs to the stack.
        for (float y : new float[] { 30.0F, 48.0F }) {
            tube(new float[] { CX + R * 0.5F, y, CU + R * 0.8F }, new float[] { LADDER_X, y, lu }, 0.35F, 4, Paint.SHADE);
        }
        return solid.toArray();
    }
}
