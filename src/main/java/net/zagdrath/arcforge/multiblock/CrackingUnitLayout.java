/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;

// Where the Cracking Unit's parts are (see CrackingUnitRenderer, which draws it, and PlantHitbox, whose shapes follow
// it): a fluid catalytic cracker. In the model's frame (ModelFrame): x across (0 to 48 px), y up (0 to 80), u back from
// the controller's end (0 to 48), in px. From the ground up:
//   - a plated base deck over the whole footprint;
//   - the regenerator at the back, a big squat vessel where the spent catalyst's coke burns off (its sight port glows
//     with the unit's heat), with a tall flue stack from its head;
//   - the reactor at the front right: a slim stripper on a skirt, widening into the disengager above it, where the
//     cracked vapour leaves the catalyst; the vapour line runs from its head over and down the right side;
//   - the riser at the front, a pipe from the regenerated catalyst's slide valve at its foot up into the disengager's
//     side; the two standpipes carry catalyst between the vessels, each with a slide valve;
//   - the fresh catalyst hopper on legs at the front left, feeding the regenerator.
public final class CrackingUnitLayout {
    public static final float WIDTH = 48.0F, HEIGHT = 80.0F, LENGTH = 48.0F;
    public static final float DECK_TOP = 4.0F;
    // The regenerator: centre (x, u), radius, shell bottom and top (its head above).
    public static final float REGEN_X = 24.0F, REGEN_U = 31.0F, REGEN_R = 14.0F, REGEN_BOTTOM = 6.0F, REGEN_TOP = 36.0F, REGEN_HEAD = 7.0F;
    // The flue stack: radius and top.
    public static final float FLUE_R = 2.2F, FLUE_TOP = 78.0F;
    // The reactor: centre, the skirt, stripper and disengager radii and heights.
    public static final float REACTOR_X = 35.0F, REACTOR_U = 9.0F;
    public static final float SKIRT_R = 4.0F, SKIRT_TOP = 16.0F;
    public static final float STRIPPER_R = 5.0F, STRIPPER_TOP = 34.0F;
    public static final float DISENGAGER_R = 7.0F, DISENGAGER_BOTTOM = 38.0F, DISENGAGER_TOP = 62.0F, REACTOR_HEAD = 5.0F;
    // The riser: centre, radius, its foot (the wye box) and where it turns into the disengager.
    public static final float RISER_X = 22.0F, RISER_U = 6.0F, RISER_R = 2.0F, RISER_TOP = 54.0F;
    public static final float[] WYE = { RISER_X - 3.5F, DECK_TOP, RISER_U - 3.5F, RISER_X + 3.5F, 12.0F, RISER_U + 3.5F };
    // The vapour line's run: up from the reactor's head, across to the right edge, down to the deck.
    public static final float VAPOUR_R = 1.6F, VAPOUR_Y = 72.0F, VAPOUR_X = 45.0F;
    // The catalyst hopper: its body {x0, y0, u0, x1, y1, u1} on four legs, and its cone.
    public static final float[] HOPPER = { 3.0F, 30.0F, 2.0F, 14.0F, 42.0F, 13.0F };
    public static final float HOPPER_CONE = 6.0F;

    private CrackingUnitLayout() {}

    // The point on a vessel's wall (centre cx, cu, radius r) facing (x, u): {x, u}.
    public static float[] wallToward(float cx, float cu, float r, float x, float u) {
        float dx = x - cx, du = u - cu, d = (float) Math.sqrt(dx * dx + du * du);
        return new float[] { cx + dx / d * r, cu + du / d * r };
    }

    // The standpipes, each {x0, y0, u0, x1, y1, u1} from end to end: the regenerated catalyst's from the regenerator
    // down to the riser's wye, and the spent catalyst's from the stripper down into the regenerator; and the hopper's
    // feed.
    public static float[][] pipes() {
        float[] regenToRiser = wallToward(REGEN_X, REGEN_U, REGEN_R - 0.5F, RISER_X, RISER_U);
        float[] stripperOut = wallToward(REACTOR_X, REACTOR_U, STRIPPER_R - 0.5F, REGEN_X, REGEN_U);
        float[] regenFromStripper = wallToward(REGEN_X, REGEN_U, REGEN_R - 0.5F, REACTOR_X, REACTOR_U);
        float[] hopperFeed = wallToward(REGEN_X, REGEN_U, REGEN_R - 0.5F, (HOPPER[0] + HOPPER[3]) / 2.0F, (HOPPER[2] + HOPPER[5]) / 2.0F);
        return new float[][] {
                { regenToRiser[0], 14.0F, regenToRiser[1], RISER_X, 9.0F, RISER_U + 2.0F },
                { stripperOut[0], 22.0F, stripperOut[1], regenFromStripper[0], 16.0F, regenFromStripper[1] },
                { (HOPPER[0] + HOPPER[3]) / 2.0F, HOPPER[1] - HOPPER_CONE + 0.5F, (HOPPER[2] + HOPPER[5]) / 2.0F, hopperFeed[0], 24.0F, hopperFeed[1] } };
    }

    // The hopper's legs, {x, u}.
    public static float[][] hopperLegs() {
        return new float[][] { { HOPPER[0] + 1.0F, HOPPER[2] + 1.0F }, { HOPPER[3] - 1.0F, HOPPER[2] + 1.0F }, { HOPPER[0] + 1.0F, HOPPER[5] - 1.0F },
                { HOPPER[3] - 1.0F, HOPPER[5] - 1.0F } };
    }

    // The sight port on the front of the regenerator, where its wall comes nearest the front: its middle {x, y, u}.
    public static float[] sightPort() {
        return new float[] { REGEN_X, 27.0F, REGEN_U - REGEN_R };
    }

    // The still parts as boxes {x0, y0, u0, x1, y1, u1}, for the blocks' shapes: round vessels as a cross of boxes.
    public static List<float[]> solids() {
        List<float[]> boxes = new ArrayList<>();
        boxes.add(new float[] { 0.0F, 0.0F, 0.0F, WIDTH, DECK_TOP, LENGTH });
        cylinder(boxes, REGEN_X, REGEN_U, REGEN_R, DECK_TOP, REGEN_TOP + REGEN_HEAD * 0.6F);
        cylinder(boxes, REGEN_X, REGEN_U, FLUE_R, REGEN_TOP, FLUE_TOP);
        cylinder(boxes, REACTOR_X, REACTOR_U, SKIRT_R, DECK_TOP, SKIRT_TOP);
        cylinder(boxes, REACTOR_X, REACTOR_U, STRIPPER_R, SKIRT_TOP, DISENGAGER_BOTTOM);
        cylinder(boxes, REACTOR_X, REACTOR_U, DISENGAGER_R, DISENGAGER_BOTTOM, DISENGAGER_TOP + REACTOR_HEAD * 0.6F);
        cylinder(boxes, RISER_X, RISER_U, RISER_R, DECK_TOP, RISER_TOP + RISER_R);
        boxes.add(WYE);
        boxes.add(HOPPER);
        boxes.add(new float[] { HOPPER[0] + 2.5F, HOPPER[1] - HOPPER_CONE, HOPPER[2] + 2.5F, HOPPER[3] - 2.5F, HOPPER[1], HOPPER[5] - 2.5F });
        for (float[] leg : hopperLegs()) {
            boxes.add(new float[] { leg[0] - 0.75F, DECK_TOP, leg[1] - 0.75F, leg[0] + 0.75F, HOPPER[1], leg[1] + 0.75F });
        }
        // The vapour line down the right side.
        boxes.add(new float[] { VAPOUR_X - VAPOUR_R, DECK_TOP, REACTOR_U - VAPOUR_R, VAPOUR_X + VAPOUR_R, VAPOUR_Y + VAPOUR_R, REACTOR_U + VAPOUR_R });
        boxes.add(new float[] { REACTOR_X, VAPOUR_Y - VAPOUR_R, REACTOR_U - VAPOUR_R, VAPOUR_X + VAPOUR_R, VAPOUR_Y + VAPOUR_R, REACTOR_U + VAPOUR_R });
        return boxes;
    }

    // A round vessel from y0 to y1 as a cross of two boxes and a middle box.
    private static void cylinder(List<float[]> boxes, float x, float u, float r, float y0, float y1) {
        float a = r * 0.92F, b = r * 0.45F, c = r * 0.72F;
        boxes.add(new float[] { x - a, y0, u - b, x + a, y1, u + b });
        boxes.add(new float[] { x - b, y0, u - a, x + b, y1, u + a });
        boxes.add(new float[] { x - c, y0, u - c, x + c, y1, u + c });
    }
}
