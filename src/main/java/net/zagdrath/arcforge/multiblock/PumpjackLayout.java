/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;

// Where the Pumpjack's parts are (see PumpjackRenderer, which draws it, and PlantHitbox, whose shapes follow it),
// and how its walking beam rocks as the crank turns. In the model's frame (ModelFrame): x across (0 to 48 px, the well
// in the middle), y up, u along from the well (0 at the well's end, 80 at the far end), in px.
//
// Built like a conventional (class I) beam pump. From the ground up: a steel skid of two rails past the Well Head, and
// on it the base, a deep plinth under everything but the well; the Samson post, a tall A-frame of two splayed front
// legs and two rear legs meeting under the saddle bearing at PIVOT, braced across, with a ladder up its side to a
// railed platform round the saddle; the walking beam on it, its big curved horse head over the well (an arc about the
// pivot, so the bridle always hangs straight over the well, deepest where the beam joins it and tapering to both tips)
// and the equalizer near its far end; the pitman arms from the equalizer down to the crank pins; the gear reducer, a
// round-topped housing at the far end with its crankshaft through it; the two cranks, each with its big counterweight
// on the pin's side (it falls as the rods are lifted); and the motor behind the reducer, driving it through a belt
// guard.
public final class PumpjackLayout {
    public static final float WIDTH = 48.0F, LENGTH = 80.0F, CX = 24.0F;
    // The saddle bearing (y, u), the beam's half width and depth, its ends, and the horse head: its radius (so its face
    // hangs over the well's middle), half width, and how far it reaches below and above the beam (far enough up to
    // cover the beam's end). Its back is a flat plate across the beam's front end, which runs into it, rounding in to
    // the face over its bottom HEAD_ROUND to a tip HEAD_TIP deep under the bridle.
    public static final float PIVOT_Y = 58.0F, PIVOT_U = 40.0F;
    public static final float BEAM_HALF = 3.0F, BEAM_DEPTH = 12.0F, BEAM_FRONT = 20.0F, BEAM_BACK = 72.0F;
    public static final float HEAD_RADIUS = PIVOT_U - 8.0F, HEAD_HALF = 4.5F, HEAD_TIP = 2.0F;
    public static final double HEAD_BELOW = Math.toRadians(36.0), HEAD_ABOVE = Math.toRadians(32.0), HEAD_ROUND = Math.toRadians(14.0);
    // The equalizer, its distance back from the pivot along the beam; the crankshaft (y, u), crank radius and the
    // pitman arms' length (set so the beam rests level with the crank pins toward the far end).
    public static final float EQUALIZER = 24.0F, CRANK_Y = 28.0F, CRANK_U = 62.0F, CRANK_RADIUS = 9.0F;
    public static final float PITMAN = (float) Math.hypot(PIVOT_Y - CRANK_Y, PIVOT_U + EQUALIZER - (CRANK_U + CRANK_RADIUS));
    // The carrier bar's height with the beam level. The cranks' middle x (the near side; the far one mirrors it), their
    // counterweights (a sector of a ring about the shaft, centred on the pin's side: inner and outer radius, half
    // angle, half thickness), and the pitman arms' x.
    public static final float CARRIER_Y = 32.0F, CRANK_X = 12.5F, PITMAN_X = 9.0F;
    public static final float WEIGHT_INNER = 3.0F, WEIGHT_OUTER = 17.0F, WEIGHT_HALF = 2.0F;
    public static final double WEIGHT_SPAN = Math.toRadians(38.0);
    // The base: rails, a cross member just behind the Well Head (the skid is open at the well end, so it sits round the
    // well head's base plate), and the plinth (its top is what the rest stands on).
    public static final float SKID_TOP = 4.0F, BASE_TOP = 9.0F;
    public static final float[][] RAILS = { { 3.0F, 7.0F }, { 41.0F, 45.0F } };
    public static final float[][] CROSS = { { 17.0F, 20.0F } };
    public static final float[] PLINTH = { 5.0F, SKID_TOP, 22.0F, 43.0F, BASE_TOP, 79.0F };
    // The gear reducer (a box with a half-round top about the crankshaft), the motor and the belt guard.
    public static final float GEARBOX_X0 = 15.0F, GEARBOX_X1 = 33.0F, GEARBOX_TOP = 24.0F, GEARBOX_ROUND = 8.0F;
    public static final float[] GEARBOX = { GEARBOX_X0, BASE_TOP, CRANK_U - 8.0F, GEARBOX_X1, GEARBOX_TOP, CRANK_U + 8.0F };
    public static final float[] MOTOR = { 18.0F, BASE_TOP, 71.0F, 30.0F, 19.0F, 78.0F };
    public static final float[] GUARD = { 30.0F, 10.0F, 60.0F, 33.0F, 21.0F, 77.0F };
    public static final float[] SADDLE = { CX - 5.0F, PIVOT_Y - 4.0F, PIVOT_U - 3.0F, CX + 5.0F, PIVOT_Y - 1.0F, PIVOT_U + 3.0F };
    // The Samson post's legs: foot (x, u) and top (x, u), all meeting under the saddle; the front pair splayed wide.
    public static final float LEG_TOP = PIVOT_Y - 4.0F, LEG_RADIUS = 1.8F;
    public static final float[][] LEGS = {
            { 6.0F, 26.0F, CX - 4.0F, PIVOT_U - 2.0F }, { 42.0F, 26.0F, CX + 4.0F, PIVOT_U - 2.0F },
            { 15.5F, 51.0F, CX - 4.0F, PIVOT_U + 2.0F }, { 32.5F, 51.0F, CX + 4.0F, PIVOT_U + 2.0F } };
    // The platform round the saddle (x0, deck top, u0, x1, u1) and its railings' height; the ladder up the far side,
    // from the rail to the platform (x of its middle, u0, u1: as wide as a ladder block's rungs).
    public static final float[] PLATFORM = { 13.0F, 50.0F, 33.0F, 43.0F, 47.0F };
    public static final float RAIL_HEIGHT = 8.0F;
    public static final float LADDER_X = 44.5F, LADDER_U0 = 34.0F, LADDER_U1 = 46.0F;

    private PumpjackLayout() {}

    // The beam's angle (radians, positive when the horse head is up) with the crank at theta (radians, 0 with the pins
    // straight up, turning toward the well): where the equalizer is the pitman arm's length from the crank pin.
    public static double beamAngle(double theta) {
        double pinY = CRANK_Y + CRANK_RADIUS * Math.cos(theta), pinU = CRANK_U + CRANK_RADIUS * Math.sin(theta);
        double low = -0.6, high = 0.6;
        // The arm's reach falls as the beam's far end drops (the head rises), so bisect on it.
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2.0;
            double ey = PIVOT_Y - EQUALIZER * Math.sin(mid), eu = PIVOT_U + EQUALIZER * Math.cos(mid);
            if (Math.hypot(ey - pinY, eu - pinU) > PITMAN) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2.0;
    }

    // The equalizer bearing (y, u) with the beam at phi.
    public static float[] equalizer(double phi) {
        return new float[] { PIVOT_Y - EQUALIZER * (float) Math.sin(phi), PIVOT_U + EQUALIZER * (float) Math.cos(phi) };
    }

    // A crank pin (y, u) with the crank at theta.
    public static float[] crankPin(double theta) {
        return new float[] { CRANK_Y + CRANK_RADIUS * (float) Math.cos(theta), CRANK_U + CRANK_RADIUS * (float) Math.sin(theta) };
    }

    // The carrier bar's height with the beam at phi: the bridle unwraps from the horse head as it drops.
    public static float carrierY(double phi) {
        return CARRIER_Y + HEAD_RADIUS * (float) phi;
    }

    // The still parts, as boxes {x0, y0, u0, x1, y1, u1}: what the blocks' shapes are made of (the beam, cranks and
    // arms move, so they're left out; the legs as steps along them).
    public static List<float[]> solids() {
        List<float[]> boxes = new ArrayList<>();
        for (float[] rail : RAILS) {
            boxes.add(new float[] { rail[0], 0.0F, 1.0F, rail[1], SKID_TOP, LENGTH - 1.0F });
        }
        for (float[] cross : CROSS) {
            boxes.add(new float[] { RAILS[0][0], 0.0F, cross[0], RAILS[1][1], 3.0F, cross[1] });
        }
        boxes.add(PLINTH);
        boxes.add(GEARBOX);
        boxes.add(new float[] { GEARBOX_X0, GEARBOX_TOP, CRANK_U - GEARBOX_ROUND * 0.7F, GEARBOX_X1, GEARBOX_TOP + GEARBOX_ROUND * 0.9F,
                CRANK_U + GEARBOX_ROUND * 0.7F });
        boxes.add(MOTOR);
        boxes.add(GUARD);
        boxes.add(SADDLE);
        boxes.add(new float[] { PLATFORM[0], PLATFORM[1] - 1.5F, PLATFORM[2], PLATFORM[3], PLATFORM[1], PLATFORM[4] });
        boxes.add(new float[] { LADDER_X - 0.75F, SKID_TOP, LADDER_U0 - 0.5F, LADDER_X + 0.75F, PLATFORM[1] + RAIL_HEIGHT, LADDER_U1 + 0.5F });
        for (float[] leg : LEGS) {
            int steps = 10;
            for (int i = 0; i < steps; i++) {
                float t = (i + 0.5F) / steps;
                float x = leg[0] + (leg[2] - leg[0]) * t, u = leg[1] + (leg[3] - leg[1]) * t;
                float y0 = BASE_TOP + (LEG_TOP - BASE_TOP) * i / steps, y1 = BASE_TOP + (LEG_TOP - BASE_TOP) * (i + 1) / steps;
                boxes.add(new float[] { x - LEG_RADIUS, y0, u - LEG_RADIUS, x + LEG_RADIUS, y1, u + LEG_RADIUS });
            }
        }
        return boxes;
    }

    // How far from the pivot the horse head's back plate is at angle a (radians up from level, about the pivot): the
    // plate is flat, square to the beam at its front end, so the head is deepest at level and thins toward its tips.
    public static float headBack(double a) {
        return (PIVOT_U - BEAM_FRONT) / (float) Math.cos(a);
    }

    // The horse head's inner edge at angle a: the back plate, rounding in to the face (as a quarter circle in its depth)
    // over the bottom HEAD_ROUND, so the head ends in a rounded tip HEAD_TIP deep.
    public static float headInner(double a) {
        float back = headBack(a);
        double into = (a + HEAD_BELOW) / HEAD_ROUND;
        if (into >= 1.0) {
            return back;
        }
        float depth = HEAD_RADIUS - back;
        double round = Math.sqrt(Math.max(0.0, 1.0 - (1.0 - into) * (1.0 - into)));
        return HEAD_RADIUS - (float) (HEAD_TIP + (depth - HEAD_TIP) * round);
    }
}
