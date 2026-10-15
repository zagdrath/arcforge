/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Where a formed Gas Centrifuge Cascade's parts are (see CentrifugeCascadeRenderer, which draws it), for its size and
// where its centrifuges stand. In the model's frame (ModelFrame, along the way the hall runs back from its controller):
// x across (0 to width x 16 px), y up (0 to 48), u back from the controller's edge (0 to length x 16), in px.
//  - the raised floor: deck plates on the floor blocks' tops (a 2 px deck over a deep plinth), with a perforated tile
//    under each centrifuge;
//  - each centrifuge: a square foot, the motor housing, the slim rotor casing up to its cap, and a band round its foot
//    that shows a spinning mark while the cascade runs;
//  - over each row (centrifuges at the same u), three header pipes running across x, feed, product and tails, with a
//    riser from each cap up into each; they stop a little past the row's end columns;
//  - the control panel standing on the controller's block at the front edge: a cabinet with its lit screen facing
//    out.
public final class CentrifugeCascadeLayout {
    public static final float FLOOR_TOP = 16.0F, DECK = 2.0F;
    // A centrifuge, about its cell's middle: its foot (half-width, height), the motor housing, the casing and its cap,
    // and the band.
    public static final float FOOT_HALF = 6.0F, FOOT_TOP = FLOOR_TOP + 2.0F;
    public static final float MOTOR_R = 5.0F, MOTOR_TOP = FOOT_TOP + 4.0F;
    public static final float CASING_R = 4.0F, CASING_TOP = 42.0F;
    public static final float CAP_R = 4.6F, CAP_TOP = 43.5F;
    public static final float BAND_R = 4.35F, BAND_Y0 = 25.0F, BAND_Y1 = 27.0F;
    // The headers: their height, radius, offsets in u from the row's middle, and how far past the end columns they run.
    public static final float HEADER_Y = 46.0F, HEADER_R = 0.9F, RISER_R = 0.5F, HEADER_OVERHANG = 3.0F;
    public static final float[] HEADER_U = { -2.5F, 0.0F, 2.5F };
    // The control panel cabinet in the controller's cell {x0, y0, u0, x1, y1, u1} (relative to the cell's corner), and
    // its screen on the front {x0, y0, x1, y1}.
    public static final float[] PANEL = { 2.0F, FLOOR_TOP, 3.0F, 14.0F, 40.0F, 11.0F };
    public static final float[] SCREEN = { 4.0F, 30.0F, 12.0F, 36.0F };

    // A centrifuge's cell (frame blocks across and back).
    public record Cell(int x, int u) {
        public float centreX() {
            return x * 16.0F + 8.0F;
        }

        public float centreU() {
            return u * 16.0F + 8.0F;
        }
    }

    public final int width, length, panelX;
    public final List<Cell> cells;

    public CentrifugeCascadeLayout(int width, int length, int panelX, List<Cell> cells) {
        this.width = width;
        this.length = length;
        this.panelX = panelX;
        this.cells = List.copyOf(cells);
    }

    // The rows: for each u, its centrifuges' x, in order.
    public Map<Integer, List<Integer>> rows() {
        Map<Integer, List<Integer>> rows = new TreeMap<>();
        for (Cell cell : cells) {
            rows.computeIfAbsent(cell.u(), u -> new ArrayList<>()).add(cell.x());
        }
        rows.values().forEach(xs -> xs.sort(Integer::compare));
        return rows;
    }

    // A row's headers' span along x: from its first column to its last, overhanging.
    public static float[] headerSpan(List<Integer> xs) {
        return new float[] { xs.get(0) * 16.0F + 8.0F - HEADER_OVERHANG - CAP_R, xs.get(xs.size() - 1) * 16.0F + 8.0F + HEADER_OVERHANG + CAP_R };
    }

    public float widthPx() {
        return width * 16.0F;
    }

    public float lengthPx() {
        return length * 16.0F;
    }
}
