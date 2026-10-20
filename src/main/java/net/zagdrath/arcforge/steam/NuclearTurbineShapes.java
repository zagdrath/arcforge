/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

// The formed Nuclear Steam Turbine's collision shape, taken from its model (NuclearTurbineMesh) as TurbineShapes takes
// the Steam Turbine Array's: every pixel the model's still parts and shaft pass through, and every pixel they close off
// from the air outside, so its parts are solid (px; x across, y up, u along), cut up by blocks. The hollow core is
// air, so the shell's blocks carry it all: each its own block's pieces, and the first layer of the core is shared out
// among the shell blocks beside it (a shape may reach one block past its own, which entity collision honours for a
// block without a cached shape). Deeper in the core no shape can reach, so what's there that could be walked into
// from above or from the sides (the casings' middles, the pedestals) is covered over instead: a 1 px lid at the
// bottom of the core's top layer over it, and a 1 px wall at the inside of the side layers beside it. The deck, the
// walkway along the front and the staircase are walkable: they're in the bottom layers and the front side, which the
// shell's own blocks carry.
public final class NuclearTurbineShapes {
    private static final int PX = 16;
    // The mesh's floats per vertex and per quad.
    private static final int VERTEX = 8, QUAD = 4 * VERTEX;
    // How far apart (px) a quad is sampled, and how far in from its surface each sample is taken.
    private static final float SAMPLE = 0.5F, INSET = 0.05F;

    private final int length, width, height;
    // The structure's size in px.
    private final int sizeX, sizeY, sizeU;
    // The model's solid pixels (see pixel).
    private final BitSet solid;
    // Per block (see index): {x0, y0, u0, x1, y1, u1} in the block's own px (0 to 16), or null for the core.
    private final int[][][] boxes;

    private NuclearTurbineShapes(NuclearTurbineLayout layout) {
        this.length = layout.length();
        this.width = NuclearTurbineLayout.WIDTH;
        this.height = NuclearTurbineLayout.HEIGHT;
        this.sizeX = width * PX;
        this.sizeY = height * PX;
        this.sizeU = length * PX;
        this.solid = voxels(layout);
        BitSet[] grids = split(solid);
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

    public static NuclearTurbineShapes of(NuclearTurbineLayout layout) {
        return new NuclearTurbineShapes(layout);
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

    // --- The model, in pixels ---

    // The model's solid pixels over the whole structure: every pixel its surfaces pass through (the still parts and the
    // glass, without ports, and the rotor at rest), then every pixel the air outside can't reach, so its parts come out
    // solid rather than as skins.
    private BitSet voxels(NuclearTurbineLayout layout) {
        BitSet surface = new BitSet(sizeX * sizeY * sizeU);
        NuclearTurbineMesh mesh = new NuclearTurbineMesh(layout, false);
        float[][] still = mesh.buildStatic(List.of());
        for (float[] quads : new float[][] { still[0], still[1], mesh.buildRotor() }) {
            for (int i = 0; i + QUAD <= quads.length; i += QUAD) {
                surface(surface, quads, i);
            }
        }
        return inside(surface);
    }

    // Marks the pixels a quad of the mesh (four vertices of x, y, z in blocks, u, v, normal; built along Z, so z is u)
    // passes through, sampled every SAMPLE px across it, each sample a little in from the surface so a face on a pixel
    // boundary marks the pixel of the part it belongs to.
    private void surface(BitSet grid, float[] quads, int i) {
        float x0 = quads[i] * PX, y0 = quads[i + 1] * PX, u0 = quads[i + 2] * PX;
        float x1 = quads[i + VERTEX] * PX, y1 = quads[i + VERTEX + 1] * PX, u1 = quads[i + VERTEX + 2] * PX;
        float x2 = quads[i + 2 * VERTEX] * PX, y2 = quads[i + 2 * VERTEX + 1] * PX, u2 = quads[i + 2 * VERTEX + 2] * PX;
        float x3 = quads[i + 3 * VERTEX] * PX, y3 = quads[i + 3 * VERTEX + 1] * PX, u3 = quads[i + 3 * VERTEX + 2] * PX;
        float nx = quads[i + 5] * INSET, ny = quads[i + 6] * INSET, nu = quads[i + 7] * INSET;
        int across = steps(Math.max(distance(x0, y0, u0, x1, y1, u1), distance(x3, y3, u3, x2, y2, u2)));
        int up = steps(Math.max(distance(x0, y0, u0, x3, y3, u3), distance(x1, y1, u1, x2, y2, u2)));
        for (int a = 0; a <= across; a++) {
            float s = (float) a / across;
            float lx = x0 + (x1 - x0) * s - nx, ly = y0 + (y1 - y0) * s - ny, lu = u0 + (u1 - u0) * s - nu;
            float hx = x3 + (x2 - x3) * s - nx, hy = y3 + (y2 - y3) * s - ny, hu = u3 + (u2 - u3) * s - nu;
            for (int b = 0; b <= up; b++) {
                float t = (float) b / up;
                int x = (int) Math.floor(lx + (hx - lx) * t), y = (int) Math.floor(ly + (hy - ly) * t), u = (int) Math.floor(lu + (hu - lu) * t);
                if (x >= 0 && x < sizeX && y >= 0 && y < sizeY && u >= 0 && u < sizeU) {
                    grid.set(pixel(x, y, u));
                }
            }
        }
    }

    private static int steps(float length) {
        return Math.max(1, (int) Math.ceil(length / SAMPLE));
    }

    private static float distance(float x0, float y0, float u0, float x1, float y1, float u1) {
        return (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (u1 - u0) * (u1 - u0));
    }

    // Everything but the air reachable from the structure's outside without crossing a surface.
    private BitSet inside(BitSet surface) {
        int total = sizeX * sizeY * sizeU;
        BitSet outside = new BitSet(total);
        Queue queue = new Queue();
        for (int u = 0; u < sizeU; u++) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    if (u == 0 || u == sizeU - 1 || y == 0 || y == sizeY - 1 || x == 0 || x == sizeX - 1) {
                        reach(surface, outside, queue, pixel(x, y, u));
                    } else if (x == 1) {
                        // Only the two ends of a row inside the faces are on the outside.
                        x = sizeX - 2;
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % sizeX, y = i / sizeX % sizeY, u = i / sizeX / sizeY;
            if (x > 0) {
                reach(surface, outside, queue, i - 1);
            }
            if (x < sizeX - 1) {
                reach(surface, outside, queue, i + 1);
            }
            if (y > 0) {
                reach(surface, outside, queue, i - sizeX);
            }
            if (y < sizeY - 1) {
                reach(surface, outside, queue, i + sizeX);
            }
            if (u > 0) {
                reach(surface, outside, queue, i - sizeX * sizeY);
            }
            if (u < sizeU - 1) {
                reach(surface, outside, queue, i + sizeX * sizeY);
            }
        }
        outside.flip(0, total);
        return outside;
    }

    private static void reach(BitSet surface, BitSet outside, Queue queue, int i) {
        if (!surface.get(i) && !outside.get(i)) {
            outside.set(i);
            queue.add(i);
        }
    }

    // A growable queue of pixel indices, its spent front dropped as it grows.
    private static final class Queue {
        private int[] items = new int[4096];
        private int head, tail;

        void add(int value) {
            if (tail == items.length) {
                if (head > items.length / 2) {
                    System.arraycopy(items, head, items, 0, tail - head);
                } else {
                    items = Arrays.copyOf(items, items.length * 2);
                    System.arraycopy(items, head, items, 0, tail - head);
                }
                tail -= head;
                head = 0;
            }
            items[tail++] = value;
        }

        int poll() {
            return items[head++];
        }

        boolean isEmpty() {
            return head == tail;
        }
    }

    private int pixel(int x, int y, int u) {
        return (u * sizeY + y) * sizeX + x;
    }

    // --- Into blocks ---

    // The solid pixels cut up by block, each block's in its own grid (see bit); null for a block with none.
    private BitSet[] split(BitSet solid) {
        BitSet[] grids = new BitSet[length * height * width];
        for (int i = solid.nextSetBit(0); i >= 0; i = solid.nextSetBit(i + 1)) {
            int x = i % sizeX, y = i / sizeX % sizeY, u = i / sizeX / sizeY;
            int index = index(u / PX, y / PX, x / PX);
            BitSet grid = grids[index];
            if (grid == null) {
                grid = grids[index] = new BitSet(PX * PX * PX);
            }
            grid.set(bit(x % PX, y % PX, u % PX));
        }
        return grids;
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
