/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;

// Where the Steam Cracker's parts are (see SteamCrackerRenderer, which draws it, and PlantHitbox, whose shapes follow
// it): a cracking furnace in the 3x3x3 box. In the model's frame (ModelFrame): x across (0 to 48 px), y up, u back from
// the controller's end (0 to 48), in px.
//   - the deck, and on it the furnace box: a refractory-lined steel box with buckstays up its walls, peep holes onto
//     the fire along its front and left side, and the burners' fuel line along its foot;
//   - on its roof, the radiant tubes coming up out of it into two banded headers along it, joined by a collector at the
//     front, and at the back the convection hood with a short stack;
//   - the transfer line from the collector across to the quench drum standing at the right, on its skirt.
public final class SteamCrackerLayout {
    public static final float WIDTH = 48.0F, HEIGHT = 48.0F, LENGTH = 48.0F;
    public static final float DECK_TOP = 3.0F;
    // The furnace box {x0, y0, u0, x1, y1, u1}.
    public static final float[] FURNACE = { 3.0F, DECK_TOP, 4.0F, 31.0F, 34.0F, 44.0F };
    // The buckstays up its walls (u on the sides, x on the front) and the tie band round it (y).
    public static final float[] SIDE_STAYS = { 12.0F, 24.0F, 36.0F }, FRONT_STAYS = { 10.0F, 24.0F };
    public static final float TIE_Y = 19.0F;
    // The peep holes: {along the face, height}; on the front (x) and the left side (u). Each is PEEP_W x PEEP_H.
    public static final float[][] FRONT_PEEPS = { { 17.0F, 11.0F }, { 17.0F, 26.0F } };
    public static final float[][] SIDE_PEEPS = { { 18.0F, 11.0F }, { 30.0F, 11.0F }, { 18.0F, 26.0F }, { 30.0F, 26.0F } };
    public static final float PEEP_W = 5.0F, PEEP_H = 4.0F;
    // The radiant tubes' headers along the roof (x of each, their height, radius, u from and to), and the collector
    // across their front ends.
    public static final float[] HEADER_X = { 11.0F, 23.0F };
    public static final float HEADER_Y = 39.0F, HEADER_R = 1.6F, HEADER_U0 = 7.0F, HEADER_U1 = 28.0F;
    public static final float TUBE_STEP = 4.0F, TUBE_R = 0.6F;
    // The convection hood on the roof's back and the stack on it {x, u, radius, top}.
    public static final float[] HOOD = { 9.0F, 34.0F, 30.0F, 25.0F, 39.0F, 42.0F };
    public static final float STACK_X = 17.0F, STACK_U = 36.0F, STACK_R = 3.0F, STACK_TOP = 48.0F;
    // The quench drum: a vertical vessel on a skirt {x, u, radius}, its shell from DRUM_BOTTOM to DRUM_TOP and a head.
    public static final float DRUM_X = 39.5F, DRUM_U = 22.0F, DRUM_R = 6.0F, DRUM_BOTTOM = 8.0F, DRUM_TOP = 30.0F, DRUM_HEAD = 4.0F;

    private SteamCrackerLayout() {}

    // The transfer line's path: from the collector's right end, across to over the drum, back over its middle and down
    // into its head.
    public static float[][] transferLine() {
        float u = HEADER_U0 - 1.0F;
        return new float[][] { { HEADER_X[1], HEADER_Y, u }, { DRUM_X, HEADER_Y, u }, { DRUM_X, HEADER_Y, DRUM_U }, { DRUM_X, DRUM_TOP + DRUM_HEAD - 1.0F, DRUM_U } };
    }

    // The still parts as boxes {x0, y0, u0, x1, y1, u1}, for the blocks' shapes.
    public static List<float[]> solids() {
        List<float[]> boxes = new ArrayList<>();
        boxes.add(new float[] { 0.0F, 0.0F, 0.0F, WIDTH, DECK_TOP, LENGTH });
        boxes.add(FURNACE);
        boxes.add(HOOD);
        for (float x : HEADER_X) {
            boxes.add(new float[] { x - HEADER_R, FURNACE[4], HEADER_U0 - 2.0F, x + HEADER_R, HEADER_Y + HEADER_R, HEADER_U1 });
        }
        boxes.add(new float[] { STACK_X - STACK_R, HOOD[4], STACK_U - STACK_R, STACK_X + STACK_R, STACK_TOP, STACK_U + STACK_R });
        boxes.add(new float[] { DRUM_X - DRUM_R, DECK_TOP, DRUM_U - DRUM_R, DRUM_X + DRUM_R, DRUM_TOP + DRUM_HEAD, DRUM_U + DRUM_R });
        return boxes;
    }
}
