/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;

// Where a formed Oil Derrick's parts are (see OilDerrickRenderer, which draws it, and PlantHitbox, whose shapes follow
// it): a classic four-legged lattice derrick over the rig's 3x3 footprint, nine blocks tall. In the model's frame
// (ModelFrame, along the way the rig's back runs from its controller): x across (0 to 48 px), y up (0 to 144), u back
// from the controller's side (0 to 48), in px.
//   - the rig floor: a plated deck on short legs over the well, the rotary table at its middle, a short stair up its
//     front, and a railing round its edge;
//   - the tower: four corner legs leaning in from the deck's corners to the crown, horizontal girts every GIRT_STEP up
//     all four sides and X-bracing in every bay, open so you see through it;
//   - the crown: a small square platform with a railing, the crown block's sheaves on it and a short gin pole;
//   - at the back of the deck the drawworks (a drum on its frame, the fast line running up to the crown) and the engine
//     house driving it, in the house steel with the FE band;
//   - a ladder up the front of the left front leg to the crown.
// The drill string (the hoist lines, the travelling block and the kelly) hangs inside the tower over the bore.
public final class DerrickLayout {
    public static final float WIDTH = 48.0F, HEIGHT = 144.0F, C = 24.0F;
    // The deck's top and its plate's thickness, and where the bore's hole through it is (C +- HOLE).
    public static final float DECK_TOP = 9.0F, DECK_PLATE = 2.0F, HOLE = 5.0F;
    // The front apron (the stair) runs from u 0 to DECK_U0, the deck from there to the back.
    public static final float DECK_U0 = 4.0F, STAIR_X0 = 29.0F, STAIR_X1 = 41.0F;
    // The legs: from the deck's corners (LEG_FOOT in from the edges) to the crown platform's corners (C +- CROWN_HALF)
    // at CROWN_Y; each LEG_W across.
    public static final float LEG_FOOT = 2.5F, LEG_W = 3.0F, CROWN_Y = 130.0F, CROWN_HALF = 8.5F, CROWN_DECK = 2.0F;
    // The girts' heights (every GIRT_STEP from GIRT_Y0) and their size; the braces' radius.
    public static final float GIRT_Y0 = 25.0F, GIRT_STEP = 16.0F, GIRT_W = 2.0F, BRACE_R = 0.5F;
    public static final float RAIL_H = 6.0F;
    // The rotary table (round, on the deck over the bore).
    public static final float TABLE_R = 6.5F, TABLE_TOP = DECK_TOP + 3.0F;
    // The drawworks: its frame {x0, x1, u0, u1} on the deck, the drum's axis (y, u), radius, flanges' radius, and span.
    public static final float[] DRAWWORKS = { 11.0F, 35.0F, 33.0F, 45.0F };
    public static final float DRUM_Y = 17.5F, DRUM_U = 39.0F, DRUM_R = 4.0F, DRUM_FLANGE_R = 6.0F, DRUM_X0 = 14.0F, DRUM_X1 = 32.0F;
    // The engine house {x0, y0, u0, x1, y1, u1}, beside the drawworks at the back right.
    // (under the bottom girts).
    public static final float[] ENGINE_HOUSE = { 36.5F, DECK_TOP, 29.0F, 43.0F, 22.0F, 43.0F };
    // The crown block: its sheaves along x on a frame over the platform's middle; the gin pole's top.
    public static final float SHEAVE_Y = 135.0F, SHEAVE_R = 2.5F, GIN_TOP = 143.0F;
    // The ladder: on the front left leg, LADDER_OUT in front of it and LADDER_SHIFT to its right (so it stays over the
    // deck at the foot), LADDER_W wide (stile to stile), a rung every RUNG_STEP.
    public static final float LADDER_OUT = 2.5F, LADDER_SHIFT = 3.0F, LADDER_W = 11.0F, RUNG_STEP = 4.0F;
    // The drill string's run: the travelling block from TRAVEL_TOP (hoist 0) to TRAVEL_BOTTOM (hoist 1); the lines hang
    // from under the crown.
    public static final float TRAVEL_TOP = 112.0F, TRAVEL_BOTTOM = 30.0F, LINES_TOP = CROWN_Y;

    private DerrickLayout() {}

    // A leg's centre at height y: corner 0 front left, 1 front right, 2 back left, 3 back right.
    public static float[] leg(int corner, float y) {
        float t = Math.max(0.0F, Math.min(1.0F, (y - DECK_TOP) / (CROWN_Y - DECK_TOP)));
        float foot = LEG_FOOT, top = C - CROWN_HALF + 0.5F;
        float near = foot + (top - foot) * t, far = WIDTH - near;
        boolean right = corner == 1 || corner == 3, back = corner >= 2;
        return new float[] { right ? far : near, y, back ? far : near };
    }

    // The girts' heights, bottom up.
    public static float[] girts() {
        List<Float> ys = new ArrayList<>();
        for (float y = GIRT_Y0; y < CROWN_Y - 4.0F; y += GIRT_STEP) {
            ys.add(y);
        }
        float[] out = new float[ys.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ys.get(i);
        }
        return out;
    }

    // The ladder's middle at height y: in front of the front left leg.
    public static float[] ladder(float y) {
        float[] leg = leg(0, y);
        return new float[] { leg[0] + LADDER_SHIFT, y, leg[2] - LADDER_OUT };
    }

    // The fast line: from the drum's top to the crown block.
    public static float[][] fastLine() {
        return new float[][] { { C + 4.0F, DRUM_Y + DRUM_R, DRUM_U }, { C + 4.0F, SHEAVE_Y - SHEAVE_R, C + 2.0F } };
    }

    // The frame blocks (x, y, u, as in ModelFrame.frameBlock) the ladder runs through: the ones a player climbs.
    public static List<int[]> ladderBlocks() {
        List<int[]> blocks = new ArrayList<>();
        for (float y = DECK_TOP; y <= CROWN_Y + CROWN_DECK; y += 1.0F) {
            float[] at = ladder(Math.min(y, CROWN_Y));
            for (float dx : new float[] { -LADDER_W / 2.0F, 0.0F, LADDER_W / 2.0F }) {
                int[] block = { (int) Math.floor((at[0] + dx) / 16.0F), (int) Math.floor(y / 16.0F), (int) Math.floor(Math.max(0.0F, at[2]) / 16.0F) };
                if (blocks.stream().noneMatch(b -> b[0] == block[0] && b[1] == block[1] && b[2] == block[2])) {
                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

    // The still parts as boxes {x0, y0, u0, x1, y1, u1}, for the blocks' shapes: the deck and stair, the legs (in steps
    // up their lean), the crown platform, the drawworks and the engine house, and the ladder.
    public static List<float[]> solids() {
        List<float[]> boxes = new ArrayList<>();
        // The deck round the hole (the bore's own block has no shape).
        boxes.add(new float[] { 0.0F, 0.0F, DECK_U0, WIDTH, DECK_TOP, WIDTH });
        boxes.add(new float[] { 0.0F, 0.0F, 0.0F, STAIR_X0, DECK_TOP, DECK_U0 });
        boxes.add(new float[] { STAIR_X1, 0.0F, 0.0F, WIDTH, DECK_TOP, DECK_U0 });
        boxes.add(new float[] { STAIR_X0, 0.0F, 0.0F, STAIR_X1, 3.0F, DECK_U0 });
        boxes.add(new float[] { STAIR_X0, 0.0F, DECK_U0 / 2.0F, STAIR_X1, 6.0F, DECK_U0 });
        for (int corner = 0; corner < 4; corner++) {
            for (float y = DECK_TOP; y < CROWN_Y; y += 4.0F) {
                float[] mid = leg(corner, y + 2.0F);
                float h = LEG_W / 2.0F;
                boxes.add(new float[] { mid[0] - h, y, mid[2] - h, mid[0] + h, Math.min(CROWN_Y, y + 4.0F), mid[2] + h });
            }
        }
        boxes.add(new float[] { C - CROWN_HALF, CROWN_Y, C - CROWN_HALF, C + CROWN_HALF, CROWN_Y + CROWN_DECK, C + CROWN_HALF });
        boxes.add(new float[] { DRAWWORKS[0], DECK_TOP, DRAWWORKS[2], DRAWWORKS[1], DRUM_Y + DRUM_FLANGE_R, DRAWWORKS[3] });
        boxes.add(ENGINE_HOUSE);
        // The ladder, a thin slab in front of its leg, in steps.
        for (float y = DECK_TOP; y < CROWN_Y; y += 4.0F) {
            float[] mid = ladder(y + 2.0F);
            boxes.add(new float[] { mid[0] - LADDER_W / 2.0F, y, Math.max(0.0F, mid[2] - 0.5F), mid[0] + LADDER_W / 2.0F, y + 4.0F, mid[2] + 0.5F });
        }
        return boxes;
    }

    // Where a player on the ladder is, as boxes {x0, y0, u0, x1, y1, u1} in steps up it: the ladder's width, from the
    // ground to the crown platform, and CLIMB_REACH out in front of the rungs (the ladder's foot stands at the rig's
    // edge, so a player climbing it from the ground is outside the rig's blocks; see DerrickLadders).
    public static final float CLIMB_REACH = 6.0F;

    public static List<float[]> climbBoxes() {
        List<float[]> boxes = new ArrayList<>();
        for (float y = 0.0F; y < CROWN_Y + CROWN_DECK; y += 8.0F) {
            float[] mid = ladder(Math.min(CROWN_Y, y + 4.0F));
            boxes.add(new float[] { mid[0] - LADDER_W / 2.0F, y, mid[2] - CLIMB_REACH, mid[0] + LADDER_W / 2.0F, y + 8.0F, mid[2] + 0.5F });
        }
        return boxes;
    }
}
