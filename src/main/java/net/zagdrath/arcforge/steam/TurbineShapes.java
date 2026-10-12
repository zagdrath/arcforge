/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

// The formed Steam Turbine Array's hitbox: the solid parts of its model (the plinth and deck, the walkway and its
// railing, the bearing pedestals and couplings, the casings, the steam chest and stop valves, the exhaust necks, the
// crossover, the generator and exciter) as boxes in the turbine's frame (px; x across, y up, u along), cut up by blocks
// and snapped to whole pixels. The hollow core is air, so the shell's blocks carry it all: each its own block's pieces,
// and the first layer of the core is shared out among the shell blocks beside it (a shape may reach one block past its
// own, which entity collision honours for a block without a cached shape; block picking finds such a piece along a
// sight line through the block that carries it). Deeper in the core
// (a cross-section 5 or more each way) no shape can reach, so what's there that could be walked into from above or
// from the sides (the top of a small casing, the pedestals between casings) is covered over instead: a 1 px lid at
// the bottom of the core's top layer over it, and a 1 px wall at the inside of the side layers beside it.
public final class TurbineShapes {
    private static final int PX = 16;
    // Round parts are stepped in bands this tall, and their tapers in steps this long.
    private static final float BAND = 2.0F, STEP = 4.0F;

    private final int length, width, height;
    // Per block (see index): {x0, y0, u0, x1, y1, u1} in the block's own px (0 to 16), or null for the core.
    private final int[][][] boxes;

    private TurbineShapes(TurbineLayout layout) {
        this.length = layout.length();
        this.width = layout.width();
        this.height = layout.height();
        List<float[]> parts = parts(layout);
        BitSet[] grids = new BitSet[length * height * width];
        for (float[] part : parts) {
            fill(grids, part);
        }
        cover(grids);
        List<List<int[]>> pieces = new ArrayList<>();
        for (int i = 0; i < grids.length; i++) {
            pieces.add(isShell(i) ? new ArrayList<>() : null);
        }
        for (int u = 0; u < length; u++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = index(u, y, x);
                    if (grids[index] == null) {
                        continue;
                    }
                    int[] owner = isShell(index) ? new int[] { u, y, x } : shellBeside(u, y, x);
                    if (owner == null) {
                        continue;
                    }
                    int ox = (x - owner[2]) * PX, oy = (y - owner[1]) * PX, ou = (u - owner[0]) * PX;
                    for (int[] box : merge(grids[index])) {
                        pieces.get(index(owner[0], owner[1], owner[2]))
                                .add(new int[] { box[0] + ox, box[1] + oy, box[2] + ou, box[3] + ox, box[4] + oy, box[5] + ou });
                    }
                }
            }
        }
        this.boxes = new int[grids.length][][];
        for (int i = 0; i < grids.length; i++) {
            boxes[i] = pieces.get(i) != null ? pieces.get(i).toArray(new int[0][]) : null;
        }
    }

    // The shell block a block of the core's first layer is shared out to: the one under it, else over it, else beside
    // it across, else along; null deeper in the core.
    private int @Nullable [] shellBeside(int u, int y, int x) {
        int[][] steps = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 } };
        for (int[] step : steps) {
            int nu = u + step[0], ny = y + step[1], nx = x + step[2];
            if (nu >= 0 && nu < length && ny >= 0 && ny < height && nx >= 0 && nx < width && isShell(index(nu, ny, nx))) {
                return new int[] { nu, ny, nx };
            }
        }
        return null;
    }

    public static TurbineShapes of(TurbineLayout layout) {
        return new TurbineShapes(layout);
    }

    // The boxes of the shell block at (u, y, x) in the turbine's frame (see TurbineLayout.blockU and blockX), in its own
    // px in that frame (from -16 to 32: some reach into the core block beside it); empty where no part reaches into it
    // or its share of the core, null in the core.
    public int @Nullable [][] at(int u, int y, int x) {
        if (u < 0 || u >= length || y < 0 || y >= height || x < 0 || x >= width) {
            return null;
        }
        return boxes[index(u, y, x)];
    }

    private int index(int u, int y, int x) {
        return (u * height + y) * width + x;
    }

    private boolean isShell(int index) {
        int x = index % width, y = index / width % height, u = index / width / height;
        return u == 0 || u == length - 1 || y == 0 || y == height - 1 || x == 0 || x == width - 1;
    }

    // --- The parts ---

    private static List<float[]> parts(TurbineLayout layout) {
        List<float[]> out = new ArrayList<>();
        float w = layout.widthPx(), t = layout.lengthPx(), foot = layout.footingTop(), deck = layout.deckTop();
        float sx = layout.shaftX(), sy = layout.shaftY();
        // The plinth: the deck on columns, or on a solid base.
        if (layout.hasColumns()) {
            out.add(new float[] { 0, foot, 0, w, deck, t });
            for (TurbineLayout.Box column : layout.columns()) {
                out.add(new float[] { column.x0() - 1.0F, 0, column.u0() - 1.0F, column.x1() + 1.0F, 2.0F, column.u1() + 1.0F });
                out.add(new float[] { column.x0(), 0, column.u0(), column.x1(), column.y1(), column.u1() });
            }
        } else {
            out.add(new float[] { 0, 0, 0, w, deck, t });
        }
        // The walkway's grating and its railing.
        TurbineLayout.Walkway walk = layout.walkway();
        if (walk != null) {
            float floor = walk.floorY();
            out.add(new float[] { walk.x0(), floor, 1.0F, walk.x1(), floor + 1.0F, t - 1.0F });
            float rx = walk.high() ? walk.x1() - 1.0F : walk.x0();
            out.add(new float[] { rx, floor + 1.0F, 2.0F, rx + 1.0F, floor + 1.0F + walk.railHeight(), t - 2.0F });
        }
        // The bearing pedestals, the shaft and the couplings.
        for (TurbineLayout.Box pedestal : layout.pedestals()) {
            out.add(box(pedestal));
        }
        float r = layout.shaftRadius();
        out.add(new float[] { sx - r, sy - r, 1.5F, sx + r, sy + r, layout.generator().u0() + 3.0F });
        List<TurbineLayout.Span> gaps = layout.gaps();
        for (int i = 1; i < gaps.size(); i++) {
            float cr = r * (i == gaps.size() - 1 ? 2.8F : 2.3F), half = layout.couplingHalf(i), middle = gaps.get(i).middle();
            out.add(new float[] { sx - cr, sy - cr, middle - half, sx + cr, sy + cr, middle + half });
        }
        // The casings, their flanges and hoops.
        for (TurbineLayout.Casing casing : layout.casings()) {
            float[][] profile = casing.profile();
            for (int i = 0; i + 1 < profile.length; i++) {
                float ua = profile[i][0], ub = profile[i + 1][0];
                int steps = Math.max(1, (int) Math.ceil((ub - ua) / STEP));
                for (int s = 0; s < steps; s++) {
                    float u0 = ua + (ub - ua) * s / steps, u1 = ua + (ub - ua) * (s + 1) / steps;
                    float k = (s + 0.5F) / steps;
                    round(out, sx, sy, profile[i][1] + (profile[i + 1][1] - profile[i][1]) * k, u0, u1);
                }
                float flange = Math.max(profile[i][1], profile[i + 1][1]) + 2.0F;
                if (ub - ua > 0.01F) {
                    out.add(new float[] { sx - flange, sy - 1.0F, ua, sx + flange, sy + 1.0F, ub });
                }
            }
            for (float u : new float[] { profile[1][0], profile[2][0] }) {
                round(out, sx, sy, casing.radius() + 0.75F, u - 1.0F, u + 1.0F);
            }
        }
        // The steam chest and its bonnet, and the stop valves with their inlet pipes.
        TurbineLayout.Box chest = layout.steamChest();
        float bonnet = Math.min(chest.y1() + 2.0F, layout.heightPx() - 1.5F);
        out.add(new float[] { chest.x0(), chest.y0(), chest.u0(), chest.x1(), Math.max(chest.y1(), bonnet), chest.u1() });
        for (TurbineLayout.Valve valve : layout.valves()) {
            float b = valve.radius() + 0.5F, pr = Math.max(1.5F, valve.radius() * 0.5F);
            out.add(new float[] { valve.x() - b, deck, valve.u() - b, valve.x() + b, valve.top() + 5.0F, valve.u() + b });
            out.add(new float[] { Math.min(valve.x(), valve.pipeEnd()), valve.pipeY() - pr, valve.u() - pr,
                    Math.max(valve.x(), valve.pipeEnd()), valve.pipeY() + pr, valve.u() + pr });
        }
        // The exhaust necks, each with its flange at the foot.
        for (TurbineLayout.Box neck : layout.exhaustNecks()) {
            out.add(box(neck));
            float flangeY = layout.hasColumns() ? neck.y0() : deck;
            out.add(new float[] { neck.x0() - 1.0F, flangeY, neck.u0() - 1.0F, neck.x1() + 1.0F, flangeY + 2.0F, neck.u1() + 1.0F });
        }
        // The crossover: the rise, the run and each drop, with the elbows and tees.
        TurbineLayout.Crossover pipe = layout.crossover();
        if (pipe != null) {
            float pr = pipe.diameter() / 2.0F;
            out.add(new float[] { sx - pr, pipe.runY() - pr, pipe.riseU(), sx + pr, pipe.runY() + pr, pipe.dropU() });
            List<Float> joints = new ArrayList<>();
            joints.add(pipe.riseU());
            for (float drop : pipe.dropUs()) {
                joints.add(drop);
            }
            for (float u : joints) {
                float base = u == pipe.riseU() ? pipe.baseY() : pipe.dropY();
                out.add(new float[] { sx - pr, base, u - pr, sx + pr, pipe.runY() + pr, u + pr });
                out.add(new float[] { sx - pr - 0.5F, pipe.runY() - pr - 0.5F, u - pr - 0.5F, sx + pr + 0.5F, pipe.runY() + pr + 0.5F, u + pr + 0.5F });
                out.add(new float[] { sx - pr - 1.0F, base + 2.0F, u - pr - 1.0F, sx + pr + 1.0F, base + 3.5F, u + pr + 1.0F });
            }
        }
        // The generator with its ribs and terminal box, and the exciter.
        TurbineLayout.Span span = layout.generator();
        float half = layout.generatorWidth() / 2.0F;
        out.add(new float[] { sx - half - 1.0F, deck, span.u0(), sx + half + 1.0F, layout.generatorTop(), span.u1() });
        out.add(box(layout.terminalBox()));
        TurbineLayout.Box exciter = layout.exciterHousing();
        TurbineLayout.Span exciterSpan = layout.exciter();
        if (exciter != null && exciterSpan != null) {
            float eh = exciter.x1() - sx;
            out.add(new float[] { sx - eh - 1.0F, deck, exciterSpan.u0(), sx + eh + 1.0F, deck + 2.0F, exciterSpan.u1() });
            out.add(new float[] { sx - eh - 0.75F, deck + 2.0F, exciter.u0(), sx + eh + 0.75F, exciter.y1(), exciter.u1() });
        }
        return out;
    }

    private static float[] box(TurbineLayout.Box box) {
        return new float[] { box.x0(), box.y0(), box.u0(), box.x1(), box.y1(), box.u1() };
    }

    // A round part along u (radius r about the shaft), as bands across it, each as wide as the part at its middle.
    private static void round(List<float[]> out, float sx, float sy, float r, float u0, float u1) {
        if (r <= 0.0F || u1 - u0 <= 0.01F) {
            return;
        }
        int bands = Math.max(1, Math.round(2.0F * r / BAND));
        float band = 2.0F * r / bands;
        for (int b = 0; b < bands; b++) {
            float y0 = sy - r + b * band, y1 = y0 + band;
            float d = Math.abs((y0 + y1) / 2.0F - sy);
            float half = (float) Math.sqrt(Math.max(0.0F, r * r - d * d));
            if (half > 0.25F) {
                out.add(new float[] { sx - half, y0, u0, sx + half, y1, u1 });
            }
        }
    }

    // --- Into blocks ---

    // Marks the whole pixels a part covers (rounded to the nearest pixel) in the grid of each block it reaches.
    private void fill(BitSet[] grids, float[] part) {
        int x0 = Math.round(part[0]), y0 = Math.round(part[1]), u0 = Math.round(part[2]);
        int x1 = Math.round(part[3]), y1 = Math.round(part[4]), u1 = Math.round(part[5]);
        x0 = Math.max(x0, 0);
        y0 = Math.max(y0, 0);
        u0 = Math.max(u0, 0);
        x1 = Math.min(x1, width * PX);
        y1 = Math.min(y1, height * PX);
        u1 = Math.min(u1, length * PX);
        if (x1 <= x0 || y1 <= y0 || u1 <= u0) {
            return;
        }
        for (int bu = u0 / PX; bu <= (u1 - 1) / PX; bu++) {
            for (int by = y0 / PX; by <= (y1 - 1) / PX; by++) {
                for (int bx = x0 / PX; bx <= (x1 - 1) / PX; bx++) {
                    int index = index(bu, by, bx);
                    BitSet grid = grids[index];
                    if (grid == null) {
                        grid = grids[index] = new BitSet(PX * PX * PX);
                    }
                    int lx0 = Math.max(x0 - bx * PX, 0), lx1 = Math.min(x1 - bx * PX, PX);
                    int ly0 = Math.max(y0 - by * PX, 0), ly1 = Math.min(y1 - by * PX, PX);
                    int lu0 = Math.max(u0 - bu * PX, 0), lu1 = Math.min(u1 - bu * PX, PX);
                    for (int y = ly0; y < ly1; y++) {
                        for (int x = lx0; x < lx1; x++) {
                            int row = (y * PX + x) * PX;
                            grid.set(row + lu0, row + lu1);
                        }
                    }
                }
            }
        }
    }

    // Covers what's solid deeper in the core than a shape can reach (see the class comment).
    private void cover(BitSet[] grids) {
        int top = height - 2;
        for (int u = 2; u < length - 2; u++) {
            for (int y = 2; y < height - 2; y++) {
                for (int x = 2; x < width - 2; x++) {
                    BitSet deep = grids[index(u, y, x)];
                    if (deep == null) {
                        continue;
                    }
                    for (int ly = 0; ly < PX; ly++) {
                        for (int lx = 0; lx < PX; lx++) {
                            for (int lu = 0; lu < PX; lu++) {
                                if (!deep.get(bit(lx, ly, lu))) {
                                    continue;
                                }
                                // A lid over it, where nothing in the top layer is over it already.
                                lid(grids, index(u, top, x), lx, 0, lu, 1);
                                // Walls beside it, the same.
                                lid(grids, index(u, y, 1), PX - 1, ly, lu, 0);
                                lid(grids, index(u, y, width - 2), 0, ly, lu, 0);
                            }
                        }
                    }
                }
            }
        }
    }

    // Sets the pixel at (x, y, u) of a block of the core's first layer unless that block has something in the same
    // column (along y for a lid, across x for a wall).
    private static void lid(BitSet[] grids, int index, int x, int y, int u, int alongY) {
        BitSet grid = grids[index];
        if (grid == null) {
            grid = grids[index] = new BitSet(PX * PX * PX);
        }
        for (int k = 0; k < PX; k++) {
            if (grid.get(alongY == 1 ? bit(x, k, u) : bit(k, y, u))) {
                return;
            }
        }
        grid.set(bit(x, y, u));
    }

    // The filled pixels of a block as few boxes: runs along u, grown across x, then up y.
    private static int[][] merge(BitSet grid) {
        BitSet left = (BitSet) grid.clone();
        List<int[]> out = new ArrayList<>();
        for (int start = left.nextSetBit(0); start >= 0; start = left.nextSetBit(start + 1)) {
            int u0 = start % PX, x0 = start / PX % PX, y0 = start / PX / PX;
            int u1 = u0 + 1;
            while (u1 < PX && left.get(bit(x0, y0, u1))) {
                u1++;
            }
            int x1 = x0 + 1;
            while (x1 < PX && full(left, x1, x1 + 1, y0, y0 + 1, u0, u1)) {
                x1++;
            }
            int y1 = y0 + 1;
            while (y1 < PX && full(left, x0, x1, y1, y1 + 1, u0, u1)) {
                y1++;
            }
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    left.clear(bit(x, y, u0), bit(x, y, u1 - 1) + 1);
                }
            }
            out.add(new int[] { x0, y0, u0, x1, y1, u1 });
        }
        return out.toArray(new int[0][]);
    }

    private static int bit(int x, int y, int u) {
        return (y * PX + x) * PX + u;
    }

    private static boolean full(BitSet grid, int x0, int x1, int y0, int y1, int u0, int u1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int from = bit(x, y, u0);
                if (grid.nextClearBit(from) < from + (u1 - u0)) {
                    return false;
                }
            }
        }
        return true;
    }
}
