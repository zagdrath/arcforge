/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.oil;

import net.zagdrath.arcforge.config.ArcforgeConfig;

// The shape of a deposit's cavern (see OilCaverns): a flattened, roughly round ellipsoid about twice as wide as it is
// tall, its edge made uneven by a few low waves round it, under the middle of the deposit's patch of chunks and between
// world.oilDeposits.minY and maxY. Sized so the cells below its gas cap (the top fifth of its height, when it has one)
// roughly number the buckets the deposit holds. Its 2-block shell is the cells within SHELL of it. The same deposit
// always gives the same cavern, so nothing about it is stored.
public final class OilCavern {
    // How far the stone shell reaches out from the cavern, in blocks (a little over 2, so it's 2 thick everywhere).
    public static final double SHELL = 2.6;
    // The share of the cavern's height its gas cap (an empty dome at the top) takes, when it has one.
    public static final double CAP_SHARE = 0.4 / 2.0;
    // The most the edge waves can add to the cavern's reach (the sum of their largest amplitudes).
    private static final double MAX_WAVE = 0.10 + 0.06 + 0.05;
    // Below this, the cavern is too small to be worth carving.
    private static final int MIN_CELLS = 20;

    public final int centreX, centreY, centreZ;
    // Horizontal and vertical semi-axes, in blocks.
    public final double across, high;
    private final double wave1, wave2, wave3, phase1, phase2, phase3;
    // The lowest y of the gas cap's dome (cells this high and up hold gas, not oil); above the top for a cavern with no cap.
    public final int domeBottom;
    public final int minY, maxY;
    // Per y from minY: the cells inside the cavern, and of those the ones that can hold oil (below the dome).
    private final int[] layerCells;
    private final int capacity;

    private OilCavern(int centreX, int centreY, int centreZ, double across, double high, long seed, boolean gasCap) {
        this.centreX = centreX;
        this.centreY = centreY;
        this.centreZ = centreZ;
        this.across = across;
        this.high = high;
        long state = seed;
        this.wave1 = 0.05 + 0.05 * unit(state = mix(state));
        this.wave2 = 0.03 + 0.03 * unit(state = mix(state));
        this.wave3 = 0.02 + 0.03 * unit(state = mix(state));
        this.phase1 = Math.PI * 2 * unit(state = mix(state));
        this.phase2 = Math.PI * 2 * unit(state = mix(state));
        this.phase3 = Math.PI * 2 * unit(mix(state));
        this.minY = centreY - (int) Math.ceil(high);
        this.maxY = centreY + (int) Math.ceil(high);
        this.domeBottom = gasCap ? (int) Math.ceil(centreY + high - 2 * high * CAP_SHARE) : maxY + 1;
        this.layerCells = new int[maxY - minY + 1];
        int reach = (int) Math.ceil(across * (1 + wave1 + wave2 + wave3)) + 1;
        int oil = 0;
        for (int y = minY; y <= maxY; y++) {
            int count = 0;
            for (int x = centreX - reach; x <= centreX + reach; x++) {
                for (int z = centreZ - reach; z <= centreZ + reach; z++) {
                    if (inside(x, y, z)) {
                        count++;
                    }
                }
            }
            layerCells[y - minY] = count;
            if (y < domeBottom) {
                oil += count;
            }
        }
        this.capacity = oil;
    }

    // The cavern of a deposit, or null when caverns are off or it would be too small.
    public static OilCavern of(OilDeposits.Deposit deposit, long seed) {
        int minY = ArcforgeConfig.OIL_MIN_Y.getAsInt(), maxY = Math.max(minY, ArcforgeConfig.OIL_MAX_Y.getAsInt());
        // Cells below the cap: a share of the ellipsoid's volume (4/3 pi a^2 c with a = 2c); the cap is the top fifth of
        // its height, about 10% of its volume.
        double cells = Math.max(MIN_CELLS, deposit.amount() / 1_000.0);
        double oilShare = deposit.gasCap() ? 0.896 : 1.0;
        double high = Math.cbrt(cells / oilShare / (4.0 / 3.0 * Math.PI * 4.0));
        // It must fit, with its shell, between minY and maxY, and inside the deposit's region.
        double maxHigh = Math.max(2.0, (maxY - minY) / 2.0 - SHELL - 1);
        // Its edge waves reach out up to MAX_WAVE further, and its shell beyond that.
        double maxAcross = Math.max(4.0, (deposit.radius() * 16 + 8 - SHELL - 2) / (1 + MAX_WAVE));
        high = Math.min(high, Math.min(maxHigh, maxAcross / 2.0));
        double across = Math.min(2.0 * high, maxAcross);
        int span = (int) Math.ceil(high + SHELL);
        int lowest = minY + span, highest = maxY - span;
        int centreY = lowest <= highest ? Math.max(lowest, Math.min(highest, deposit.y())) : (minY + maxY) / 2;
        long cavernSeed = mix(seed ^ deposit.key() * 0x9E3779B97F4A7C15L ^ 0x636176657261L);
        return new OilCavern(deposit.centreChunkX() * 16 + 8, centreY, deposit.centreChunkZ() * 16 + 8, across, high, cavernSeed, deposit.gasCap());
    }

    // For the GameTests: a small cavern of this size at this centre (a test deposit has no patch to size one by).
    public static OilCavern test(int centreX, int centreY, int centreZ, double across, double high, boolean gasCap) {
        return new OilCavern(centreX, centreY, centreZ, across, high, 0x7E57L, gasCap);
    }

    // How far out the edge reaches at this angle and height (1 is the plain ellipsoid).
    private double reach(double angle, int y) {
        return 1.0 + wave1 * Math.sin(3 * angle + phase1) + wave2 * Math.sin(5 * angle + phase2) + wave3 * Math.sin(2 * angle + y * 0.35 + phase3);
    }

    // The ellipsoid's measure of a cell (its middle) grown by grow blocks each way: 1 or less is inside.
    private double measure(int x, int y, int z, double grow) {
        double dx = x + 0.5 - (centreX + 0.5), dy = y - centreY, dz = z + 0.5 - (centreZ + 0.5);
        double r = (across * reach(Math.atan2(dz, dx), y)) + grow, h = high + grow;
        return (dx * dx + dz * dz) / (r * r) + dy * dy / (h * h);
    }

    public boolean inside(int x, int y, int z) {
        return y >= minY && y <= maxY && measure(x, y, z, 0) <= 1.0;
    }

    // In the stone shell round the cavern (not in the cavern itself).
    public boolean inShell(int x, int y, int z) {
        return !inside(x, y, z) && measure(x, y, z, SHELL) <= 1.0;
    }

    // Whether a cell of the cavern can hold oil (it's below the gas cap's dome).
    public boolean holdsOil(int y) {
        return y < domeBottom;
    }

    // The cells below the dome: the most oil source blocks the cavern holds.
    public int capacity() {
        return capacity;
    }

    // The cells of the dome, which hold the gas cap's Natural Gas (0 with no cap).
    public int domeCells() {
        int cells = 0;
        for (int y = Math.max(minY, domeBottom); y <= maxY; y++) {
            cells += layerCells[y - minY];
        }
        return cells;
    }

    // The oil's level for this much left in the deposit: cells below the returned y hold oil. Whole layers only, from the
    // bottom up, as many as the oil fills (so it falls a layer at a time as the deposit drains).
    public int levelFor(long remainingMb) {
        long blocks = remainingMb / 1_000;
        int level = minY;
        long filled = 0;
        for (int y = minY; y < Math.min(domeBottom, maxY + 1); y++) {
            int cells = layerCells[y - minY];
            if (cells == 0) {
                // The bottom layer of a cavern whose height isn't whole holds no cells: it's no level of its own.
                continue;
            }
            if (filled + cells > blocks) {
                break;
            }
            filled += cells;
            level = y + 1;
        }
        return level;
    }

    // The highest cell of the cavern in this column, or Integer.MIN_VALUE when the column misses it.
    public int topAt(int x, int z) {
        for (int y = maxY; y >= minY; y--) {
            if (inside(x, y, z)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    // The block bounds of the cavern with its shell: {minX, minY, minZ, maxX, maxY, maxZ}.
    public int[] bounds() {
        int reach = (int) Math.ceil(across * (1 + wave1 + wave2 + wave3) + SHELL) + 1;
        int down = (int) Math.ceil(high + SHELL) + 1;
        return new int[] { centreX - reach, centreY - down, centreZ - reach, centreX + reach, centreY + down, centreZ + reach };
    }

    // Whether the cavern (with its shell) reaches into this chunk.
    public boolean touches(int chunkX, int chunkZ) {
        int[] b = bounds();
        return chunkX * 16 + 15 >= b[0] && chunkX * 16 <= b[3] && chunkZ * 16 + 15 >= b[2] && chunkZ * 16 <= b[5];
    }

    private static long mix(long z) {
        z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
        z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
        return z ^ z >>> 31;
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }
}
