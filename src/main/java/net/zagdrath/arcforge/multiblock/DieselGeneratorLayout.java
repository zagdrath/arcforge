/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;

// Where the Diesel Generator's parts are (see DieselGeneratorRenderer, which draws it, and PlantHitbox, whose shapes
// follow it), for its engine (DieselEngine: 5, 7 or 9 blocks long): a large diesel genset. In the model's frame
// (ModelFrame): x across (0 to 48 px), y up (0 to 48, the exhaust's rain cap standing a little higher), u back from the
// controller's end (0 to the length), in px. Front to back, on a heavy base frame over a sub-base fuel tank:
//   - the radiator: a deep core (its fins facing forward), header tanks above and below, a filler cap, and behind it
//     the fan in its shroud, seen through a guard of rings and spokes;
//   - the V engine: the crankcase and oil pan, two cylinder banks leaning out in a V with a rocker cover per cylinder,
//     the intake manifold down the middle of the V, an exhaust manifold along the outside of each bank, the
//     turbochargers on top at the flywheel end (two, or four on the V16), the aftercooler between them, and exhaust
//     risers up into the silencer along the top, its outlet capped with a rain flap;
//   - the flywheel housing and the coupling (turning while it runs);
//   - the alternator in the generator steel, louvred, with its FE band, end bell and terminal box (FE red cable
//     glands), and the control panel on a bracket at its right side with a small lit screen.
public final class DieselGeneratorLayout {
    public static final float WIDTH = 48.0F, HEIGHT = 48.0F, CX = 24.0F;
    // The sub-base fuel tank and the base frame's rails on it.
    public static final float TANK_TOP = 7.0F, RAIL_TOP = 11.0F;
    public static final float[][] RAILS = { { 5.0F, 10.0F }, { 38.0F, 43.0F } };
    // The crankshaft's line (y), shared by the fan, the flywheel housing, the coupling and the alternator.
    public static final float SHAFT_Y = 22.0F;
    // The radiator {x0, y0, u0, x1, y1, u1} (its core), the fan (on the shaft line, radius) and its shroud behind.
    public static final float[] RADIATOR = { 5.0F, RAIL_TOP, 2.0F, 43.0F, 41.0F, 9.0F };
    public static final float FAN_U = 11.0F, FAN_R = 12.0F, SHROUD_U0 = 9.0F, SHROUD_U1 = 14.0F;
    // The engine block's cross-section: the crankcase (x, y), the oil pan, and the banks: each leans BANK_ANGLE out of
    // upright from its foot on the crankcase's top, BANK_DEPTH thick across and BANK_HEIGHT tall along its lean.
    public static final float CASE_X0 = 13.0F, CASE_X1 = 35.0F, CASE_Y0 = 13.0F, CASE_Y1 = 25.0F;
    public static final float PAN_X0 = 16.0F, PAN_X1 = 32.0F, PAN_Y0 = 8.0F;
    public static final float BANK_ANGLE = 30.0F, BANK_DEPTH = 9.0F, BANK_HEIGHT = 10.0F, BANK_FOOT = 5.5F;
    public static final float COVER_HEIGHT = 2.5F, COVER_INSET = 1.25F;
    // The intake manifold in the V {x0, y0, x1, y1}, the exhaust manifolds' radius and the turbochargers' housings.
    public static final float[] INTAKE = { 20.5F, 25.0F, 27.5F, 31.0F };
    public static final float EXHAUST_R = 1.6F, TURBO_R = 3.5F, TURBO_LENGTH = 6.0F, TURBO_Y = 38.0F;
    // The silencer along the top {x, y, radius} and its outlet with the rain cap.
    public static final float SILENCER_Y = 43.5F, SILENCER_R = 3.5F, OUTLET_R = 1.8F, OUTLET_TOP = 51.0F;
    // The flywheel housing and coupling (along u from the engine's end), the alternator's cross-section.
    public static final float BELL_R = 13.0F, BELL_LENGTH = 6.0F, COUPLING_R = 4.5F, COUPLING_LENGTH = 3.0F;
    public static final float ALT_X0 = 10.0F, ALT_X1 = 38.0F, ALT_Y1 = 37.0F;
    // The control panel on its bracket (x out to, height {y0, y1}).
    public static final float PANEL_X0 = 41.0F, PANEL_X1 = 46.5F, PANEL_Y0 = 20.0F, PANEL_Y1 = 33.0F;

    public final DieselEngine engine;
    public final float length;
    // Along u: the engine block, each cylinder's pitch, the flywheel housing, the coupling, the alternator and its bell.
    public final float engineU0, engineU1, pitch, bellU0, bellU1, couplingU0, couplingU1, altU0, altU1, altBellU;

    private DieselGeneratorLayout(DieselEngine engine) {
        this.engine = engine;
        this.length = engine.length * 16.0F;
        float altLength = switch (engine) {
            case V8 -> 22.0F;
            case V12 -> 32.0F;
            case V16 -> 40.0F;
        };
        this.engineU0 = SHROUD_U1 + 1.0F;
        this.bellU1 = length - 3.0F - altLength - 3.0F - COUPLING_LENGTH;
        this.bellU0 = bellU1 - BELL_LENGTH;
        this.engineU1 = bellU0;
        this.pitch = (engineU1 - engineU0 - 4.0F) / engine.perBank();
        this.couplingU0 = bellU1;
        this.couplingU1 = bellU1 + COUPLING_LENGTH;
        this.altU0 = couplingU1;
        this.altU1 = altU0 + altLength;
        this.altBellU = altU1 + 3.0F;
    }

    private static final DieselGeneratorLayout[] LAYOUTS = { new DieselGeneratorLayout(DieselEngine.V8), new DieselGeneratorLayout(DieselEngine.V12),
            new DieselGeneratorLayout(DieselEngine.V16) };

    public static DieselGeneratorLayout of(DieselEngine engine) {
        return LAYOUTS[engine.ordinal()];
    }

    // The cylinders' spans along u on each bank, {u0, u1} each.
    public float[][] cylinders() {
        int n = engine.perBank();
        float[][] spans = new float[n][];
        for (int i = 0; i < n; i++) {
            float u0 = engineU0 + 2.0F + pitch * i;
            spans[i] = new float[] { u0 + 0.5F, u0 + pitch - 0.5F };
        }
        return spans;
    }

    // A bank's frame: {foot x, foot y, out x, out y} (out: the unit vector along its lean, up and away from the middle),
    // side -1 for the left bank and 1 for the right.
    public static float[] bank(int side) {
        double a = Math.toRadians(BANK_ANGLE);
        return new float[] { CX + side * BANK_FOOT, CASE_Y1, (float) Math.sin(a) * side, (float) Math.cos(a) };
    }

    // The turbochargers' centres {x, u}: one over each bank near the flywheel end, and on the V16 a second pair ahead.
    public float[][] turbos() {
        List<float[]> at = new ArrayList<>();
        float u = engineU1 - 6.0F;
        for (int pair = 0; pair < engine.turbos / 2; pair++) {
            at.add(new float[] { 13.0F, u - pair * 18.0F });
            at.add(new float[] { 35.0F, u - pair * 18.0F });
        }
        return at.toArray(float[][]::new);
    }

    // The silencer's span along u: from near the engine's front to just past the turbochargers' risers.
    public float[] silencer() {
        float back = engineU0 + 5.0F;
        for (float[] t : turbos()) {
            back = Math.max(back, t[1] + TURBO_LENGTH / 4.0F + 3.0F);
        }
        return new float[] { engineU0 + 5.0F, back };
    }

    // The aftercooler {x0, y0, u0, x1, y1, u1}: across the V, just ahead of the (first pair of) turbochargers.
    public float[] aftercooler() {
        float u1 = engineU1 - 10.0F;
        return new float[] { 17.0F, 31.0F, u1 - 8.0F, 31.0F, 36.0F, u1 };
    }

    // The still parts as boxes {x0, y0, u0, x1, y1, u1}, for the blocks' shapes (the banks by their reach).
    public List<float[]> solids() {
        List<float[]> boxes = new ArrayList<>();
        boxes.add(new float[] { 2.0F, 0.0F, 1.0F, 46.0F, TANK_TOP, length - 1.0F });
        for (float[] rail : RAILS) {
            boxes.add(new float[] { rail[0], TANK_TOP, 0.0F, rail[1], RAIL_TOP, length });
        }
        boxes.add(RADIATOR);
        boxes.add(new float[] { CX - FAN_R - 1.0F, SHAFT_Y - FAN_R - 1.0F, SHROUD_U0, CX + FAN_R + 1.0F, SHAFT_Y + FAN_R + 1.0F, SHROUD_U1 });
        boxes.add(new float[] { CASE_X0, CASE_Y0, engineU0, CASE_X1, CASE_Y1, engineU1 });
        boxes.add(new float[] { PAN_X0, PAN_Y0, engineU0 + 2.0F, PAN_X1, CASE_Y0, engineU1 - 2.0F });
        // Each bank as the box it reaches across.
        for (int side = -1; side <= 1; side += 2) {
            float[] b = bank(side);
            float outX = b[0] + b[2] * BANK_HEIGHT, outY = b[1] + b[3] * BANK_HEIGHT;
            float half = BANK_DEPTH / 2.0F;
            float x0 = Math.min(b[0] - half * b[3], outX + half * b[3]), x1 = Math.max(b[0] + half * b[3], outX - half * b[3]);
            boxes.add(new float[] { Math.min(x0, x1), b[1], engineU0 + 1.0F, Math.max(x0, x1), outY + half * Math.abs(b[2]), engineU1 - 1.0F });
        }
        boxes.add(new float[] { CX - BELL_R, SHAFT_Y - BELL_R, bellU0, CX + BELL_R, SHAFT_Y + BELL_R, bellU1 });
        boxes.add(new float[] { ALT_X0, RAIL_TOP, altU0, ALT_X1, ALT_Y1, altU1 });
        boxes.add(new float[] { ALT_X0 + 3.0F, RAIL_TOP + 2.0F, altU1, ALT_X1 - 3.0F, ALT_Y1 - 3.0F, altBellU });
        boxes.add(new float[] { PANEL_X0, PANEL_Y0, altU0 + 3.0F, PANEL_X1, PANEL_Y1, altU0 + 13.0F });
        float[] s = silencer();
        boxes.add(new float[] { CX - SILENCER_R, SILENCER_Y - SILENCER_R, s[0], CX + SILENCER_R, SILENCER_Y + SILENCER_R, s[1] });
        return boxes;
    }
}
