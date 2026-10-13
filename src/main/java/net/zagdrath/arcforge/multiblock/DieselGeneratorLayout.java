/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;

// Where the Diesel Generator's parts are (see DieselGeneratorRenderer, which draws it, and PlantHitbox, whose shapes
// follow it): an engine-generator set on a skid. In the model's frame (ModelFrame): x across (0 to 48 px), y up (0 to
// 32, the exhaust stack standing higher), u back from the controller's end (0 to 80), in px. From the front:
//   - the radiator: a core behind a louvred grille, with its header tanks, and the cooling fan in its shroud behind it;
//   - the engine, an inline six: crankcase and oil pan on mounts, the cylinder head with six rocker covers, the intake
//     manifold and air filter on the left, the exhaust manifold on the right into the turbocharger, and the exhaust
//     stack with its rain cap;
//   - the flywheel housing, and the coupling to the alternator (turning while it runs);
//   - the alternator in the generator steel, louvred, with its FE band and the terminal box on top.
public final class DieselGeneratorLayout {
    public static final float WIDTH = 48.0F, HEIGHT = 32.0F, LENGTH = 80.0F, CX = 24.0F;
    public static final float SKID_TOP = 4.0F;
    public static final float[][] RAILS = { { 2.0F, 6.0F }, { 42.0F, 46.0F } };
    public static final float[][] CROSS = { { 1.0F, 4.0F }, { 38.0F, 41.0F }, { 76.0F, 79.0F } };
    // The crankshaft's line (y), shared by the fan, flywheel housing, coupling and alternator.
    public static final float SHAFT_Y = 15.0F;
    // The radiator core and its grille, the header tanks, and the fan (centre on the shaft line, radius) in its shroud.
    public static final float[] RADIATOR = { 5.0F, SKID_TOP, 2.0F, 43.0F, 29.0F, 8.0F };
    public static final float FAN_U = 10.5F, FAN_R = 9.5F, SHROUD_U0 = 8.0F, SHROUD_U1 = 12.0F;
    // The engine: crankcase, oil pan, cylinder head, the six rocker covers (each {u0, u1}) and the mounts.
    public static final float[] CRANKCASE = { 14.0F, 8.0F, 14.0F, 34.0F, 22.0F, 50.0F };
    public static final float[] OIL_PAN = { 16.0F, SKID_TOP, 16.0F, 32.0F, 8.0F, 48.0F };
    public static final float[] HEAD = { 15.0F, 22.0F, 15.0F, 33.0F, 25.0F, 49.0F };
    public static final int CYLINDERS = 6;
    public static final float COVER_X0 = 17.0F, COVER_X1 = 31.0F, COVER_TOP = 28.0F;
    // The intake manifold (left) and air filter, the exhaust manifold (right) and turbocharger, the stack.
    public static final float[] INTAKE = { 10.0F, 17.0F, 16.0F, 14.0F, 21.0F, 48.0F };
    public static final float FILTER_X = 8.0F, FILTER_U = 18.0F, FILTER_R = 3.5F, FILTER_Y0 = 12.0F, FILTER_Y1 = 25.0F;
    public static final float[] EXHAUST = { 34.0F, 17.0F, 17.0F, 37.0F, 21.0F, 47.0F };
    public static final float[] TURBO = { 35.0F, 17.0F, 42.0F, 42.0F, 26.0F, 50.0F };
    public static final float STACK_X = 39.0F, STACK_U = 46.0F, STACK_R = 2.0F, STACK_TOP = 44.0F;
    // The flywheel housing (a short drum along u), the coupling and the alternator, its end bell and terminal box.
    public static final float BELL_R = 11.0F, BELL_U0 = 50.0F, BELL_U1 = 56.0F;
    public static final float COUPLING_R = 4.0F, COUPLING_U0 = 56.0F, COUPLING_U1 = 59.0F;
    public static final float[] ALTERNATOR = { 12.0F, SKID_TOP, 59.0F, 36.0F, 27.0F, 75.0F };
    public static final float ALT_BELL_U = 78.0F;
    public static final float[] TERMINAL = { 17.0F, 27.0F, 63.0F, 31.0F, 31.0F, 71.0F };

    private DieselGeneratorLayout() {}

    // The rocker covers' spans along u, one per cylinder.
    public static float[][] covers() {
        float[][] covers = new float[CYLINDERS][];
        float span = (HEAD[5] - HEAD[2]) / CYLINDERS;
        for (int i = 0; i < CYLINDERS; i++) {
            covers[i] = new float[] { HEAD[2] + span * i + 0.75F, HEAD[2] + span * (i + 1) - 0.75F };
        }
        return covers;
    }

    // Where the exhaust smoke leaves (the stack's top), {x, y, u}.
    public static float[] stackTop() {
        return new float[] { STACK_X, STACK_TOP + 1.0F, STACK_U };
    }

    // The still parts as boxes {x0, y0, u0, x1, y1, u1}, for the blocks' shapes.
    public static List<float[]> solids() {
        List<float[]> boxes = new ArrayList<>();
        for (float[] rail : RAILS) {
            boxes.add(new float[] { rail[0], 0.0F, 0.0F, rail[1], SKID_TOP, LENGTH });
        }
        for (float[] cross : CROSS) {
            boxes.add(new float[] { RAILS[0][1], 0.0F, cross[0], RAILS[1][0], 3.0F, cross[1] });
        }
        boxes.add(RADIATOR);
        boxes.add(new float[] { CX - FAN_R - 1.0F, SHAFT_Y - FAN_R - 1.0F, SHROUD_U0, CX + FAN_R + 1.0F, SHAFT_Y + FAN_R + 1.0F, SHROUD_U1 });
        boxes.add(CRANKCASE);
        boxes.add(OIL_PAN);
        boxes.add(HEAD);
        boxes.add(new float[] { COVER_X0, HEAD[4], HEAD[2], COVER_X1, COVER_TOP, HEAD[5] });
        boxes.add(INTAKE);
        boxes.add(new float[] { FILTER_X - FILTER_R, FILTER_Y0, FILTER_U - FILTER_R, FILTER_X + FILTER_R, FILTER_Y1, FILTER_U + FILTER_R });
        boxes.add(EXHAUST);
        boxes.add(TURBO);
        boxes.add(new float[] { CX - BELL_R, SHAFT_Y - BELL_R, BELL_U0, CX + BELL_R, SHAFT_Y + BELL_R, BELL_U1 });
        boxes.add(ALTERNATOR);
        boxes.add(new float[] { ALTERNATOR[0] + 2.0F, ALTERNATOR[1] + 2.0F, ALTERNATOR[5], ALTERNATOR[3] - 2.0F, ALTERNATOR[4] - 2.0F, ALT_BELL_U });
        boxes.add(TERMINAL);
        return boxes;
    }
}
