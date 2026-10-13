/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

import javax.imageio.ImageIO;

// Paints the large models' plate modules, ramps and edges (docs/TEXTURE_STYLE.md, "Large model texturing") into rows
// 96-233 of each large model's sheet (the Pumpjack's, which the Diesel Generator shares; the refinery tower's, which the
// Cracking Unit and the Flare Stack share; and the Steam Turbine Array's), keeping rows 0-95: the shared model sheet
// layout of flat tones and panels (the Oil Derrick's drill string still uses the Pumpjack's). Run from the repository
// root: java tools/LargeModelTextureGen.java
//
// Large faces aren't tiled: ModelMesh paints each as a few big plates, sampling a window of a grain field per plate and
// laying edge strips and rivets round it (see ModelMesh.plating). This paints what that samples, and the small parts'
// tiles:
//   rows 96-111: the motor's ribbed plate (64, 96), a seamless grating (80, 96), the girder flange (96, 104: seamless,
//     a rivet every 4 px) and the hazard edge (112, 104);
//   rows 112-119: the round parts' ramps, 2 to 14 px across, side by side, then the polished rod's bright ramp (104);
//   rows 120-125: the burner fire (8x6), from the sheet's glow ramp at (40, 48);
//   rows 128-191: the machine steel's grain field (116x64), then its edge columns (x 116-118: lit, dark, seam) and the
//     generator steel's (x 119-121), 64 tall;
//   rows 192-223: the generator steel's grain field (116x32);
//   rows 224-229: the edge rows, 128 long: machine steel lit, dark, seam, then generator steel lit, dark, seam;
//   rows 232-233: the rivet heads, 2x2: machine steel (0, 232), generator steel (2, 232).
public class LargeModelTextureGen {
    static final String[] SHEETS = { "src/main/resources/assets/arcforge/textures/block/pumpjack/pumpjack.png",
            "src/main/resources/assets/arcforge/textures/block/distillation_array/refinery.png",
            "src/main/resources/assets/arcforge/textures/block/steam_turbine_array/turbine.png" };
    static final int W = 128, H = 256;
    // Each material's edges (luminance): lit, dark, seam.
    static final int[] EDGES = { 84, 54, 37 }, GEN_EDGES = { 100, 70, 54 };

    // The machine steel ramp (luminance, colour) the tones are blended from, and the generator steel's.
    static final int[][] STEEL = { { 24, 0x16181B }, { 47, 0x2B2F34 }, { 60, 0x383C42 }, { 68, 0x40454C }, { 75, 0x474C53 },
            { 92, 0x575D65 }, { 120, 0x727982 }, { 156, 0x959DA6 } };
    static final int[][] GEN = { { 47, 0x2E3338 }, { 67, 0x3E444B }, { 83, 0x4E545C }, { 105, 0x646A72 }, { 131, 0x7E848C }, { 160, 0x9AA0A8 } };

    // Grain tones (luminance) for the machine steel field and the generator steel's: neighbouring steps of 3.
    static final int[] FIELD = { 63, 66, 69, 72 }, GEN_FIELD = { 79, 82, 85, 88 };

    static BufferedImage out;

    public static void main(String[] args) throws Exception {
        for (String sheet : SHEETS) {
            paint(sheet);
        }
    }

    static void paint(String sheet) throws Exception {
        BufferedImage old = ImageIO.read(new File(sheet));
        out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < Math.min(96, old.getHeight()); y++) {
            for (int x = 0; x < W; x++) {
                out.setRGB(x, y, old.getRGB(x, y));
            }
        }
        motor(64, 96);
        grating(80, 96);
        flange(96, 104);
        hazard(112, 104);
        for (int p = 2; p <= 14; p++) {
            ramp(rampX(p), 112, p, STEEL, new int[] { 50, 57, 64, 71, 78, 85, 92 });
        }
        // The polished rod: bright steel.
        ramp(104, 112, 4, STEEL, new int[] { 120, 132, 144, 156, 168, 180, 190 });
        fire(0, 120, old);
        field(0, 128, 116, 64, STEEL, FIELD, 31);
        field(0, 192, 116, 32, GEN, GEN_FIELD, 32);
        int[][][] ramps = { STEEL, STEEL, STEEL, GEN, GEN, GEN };
        int[] lums = { EDGES[0], EDGES[1], EDGES[2], GEN_EDGES[0], GEN_EDGES[1], GEN_EDGES[2] };
        for (int i = 0; i < 6; i++) {
            edge(116 + i, 128, 1, 64, ramps[i], lums[i], 40 + i);
            edge(0, 224 + i, 128, 1, ramps[i], lums[i], 50 + i);
        }
        rivet(0, 232, STEEL, true);
        set(2, 232, GEN, 140);
        set(3, 232, GEN, 106);
        set(2, 233, GEN, 106);
        set(3, 233, GEN, 60);
        ImageIO.write(out, "png", new File(sheet));
        System.out.println("Wrote " + sheet);
    }

    // A burner's fire 8x6, seen through its window: the glow ramp's rising half (at (40, 48): deep red, red, orange,
    // yellow) over a pale yellow bed at the bottom, its tongues reaching up unevenly across the width. Drawn full-bright,
    // so it stays a fire in the dark.
    static void fire(int x0, int y0, BufferedImage sheet) {
        int[] ramp = new int[7];
        for (int i = 0; i < 4; i++) {
            ramp[i] = sheet.getRGB(42, 48 + i) & 0xFFFFFF;
        }
        // The hottest end: yellow, then the bed.
        ramp[4] = ramp[3];
        ramp[5] = 0xFFC85A;
        ramp[6] = 0xFFE08A;
        // How far up each column's flame reaches (rows), so the bands step unevenly across.
        int[] reach = { 4, 5, 3, 5, 4, 6, 4, 3 };
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 8; x++) {
                int up = 5 - y;
                // Bottom rows are the bed (the ramp's brightest end); a column's tongue fades through the ramp to its
                // reach, then the dark top.
                double t = Math.min(1.0, (double) up / reach[x]);
                int index = (int) Math.round((1.0 - t) * 6);
                out.setRGB(x0 + x, y0 + y, 0xFF000000 | ramp[Math.max(0, Math.min(6, index))]);
            }
        }
    }

    // Where the ramp p px across starts on row 112 (2 + 3 + ... + (p - 1)).
    static int rampX(int p) {
        return (p - 1) * p / 2 - 1;
    }

    // A grain field w x h: the machine (or generator) steel's grain, one pattern with no repeat inside it. A plate samples
    // its own window of it at 1:1, so a big face's grain doesn't repeat block by block.
    static void field(int x0, int y0, int w, int h, int[][] ramp, int[] tones, long seed) {
        int[][] grain = grain(seed, w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                set(x0 + x, y0 + y, ramp, tones[grain[y][x]]);
            }
        }
    }

    // An edge strip w x h (1 px across, along a row or down a column): its tone with a faint grain of its own (a step
    // either side of it, in runs of 1-3 px), so a long plate edge isn't one flat line.
    static void edge(int x0, int y0, int w, int h, int[][] ramp, int lum, long seed) {
        java.util.Random random = new java.util.Random(seed);
        int length = Math.max(w, h), step = 0, run = 0;
        for (int i = 0; i < length; i++) {
            if (run <= 0) {
                step = random.nextInt(3) - 1;
                run = 1 + random.nextInt(3);
            }
            set(w > 1 ? x0 + i : x0, w > 1 ? y0 : y0 + i, ramp, lum + step * 2);
            run--;
        }
    }

    // A rivet: a 2x2 head lit at its top left with its shadow at the bottom right, or a 1 px head on a half module.
    static void rivet(int x, int y, int[][] ramp, boolean big) {
        if (!big) {
            set(x, y, ramp, 120);
            return;
        }
        set(x, y, ramp, 128);
        set(x + 1, y, ramp, 96);
        set(x, y + 1, ramp, 96);
        set(x + 1, y + 1, ramp, 47);
    }

    // The motor's plate: generator steel ribbed with cooling fins (a lit edge, the fin and its shadow, every 3 px),
    // between the module's edges.
    static void motor(int x0, int y0) {
        int[][] grain = grain(21, 16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int lum = GEN_FIELD[grain[y][x]];
                if (y >= 2 && y <= 12 && x >= 1 && x <= 13) {
                    int phase = (x - 1) % 3;
                    lum = phase == 0 ? 96 + grain[y][x] * 2 : phase == 1 ? GEN_FIELD[grain[y][x]] : 66;
                }
                if (x == 15 || y == 15) {
                    lum = 54;
                } else if (x == 14 || y == 14) {
                    lum = 70;
                } else if (x == 0 || y == 0) {
                    lum = 100;
                }
                set(x0 + x, y0 + y, GEN, lum);
            }
        }
    }

    // A grating: bars across with a lit top edge and a shadow under them, dark gaps, and a bearer every 8 px; seamless,
    // so it runs on across a deck with no frame at the block lines.
    static void grating(int x0, int y0) {
        int[][] grain = grain(22, 16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int lum = switch (y % 4) {
                    case 0 -> 84;
                    case 1 -> FIELD[grain[y][x]];
                    case 2 -> 54;
                    default -> 30;
                };
                if (x % 8 == 3 && y % 4 == 3) {
                    lum = 60;
                }
                set(x0 + x, y0 + y, STEEL, lum);
            }
        }
    }

    // A girder flange 16x8, seamless along its length: a lit top edge, the field in the grain with a row of rivets every
    // 4 px, a darker bottom edge and the seam.
    static void flange(int x0, int y0) {
        int[][] grain = grain(23, 16, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                int lum = FIELD[grain[y][x]];
                if (y == 7) {
                    lum = 37;
                } else if (y == 6) {
                    lum = 54;
                } else if (y == 0) {
                    lum = 84;
                }
                set(x0 + x, y0 + y, STEEL, lum);
            }
        }
        for (int x = 1; x < 16; x += 4) {
            rivet(x0 + x, y0 + 2, STEEL, true);
        }
    }

    // The hazard edge 8x4: yellow and near-black in 2 px diagonal bands, with the round edge's ramp across it (its
    // middle rows lighter, lightest just off centre, its edge rows darker).
    static void hazard(int x0, int y0) {
        double[] shade = { 0.8, 1.06, 1.0, 0.82 };
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 8; x++) {
                int c = (x + y) % 4 < 2 ? 0xE0B020 : 0x1A1A1A;
                out.setRGB(x0 + x, y0 + y, 0xFF000000 | scale(c, shade[y]));
            }
        }
    }

    // A round part's ramp p px across (8 rows, the same down each column): 7 close tones from the darkest at both edges
    // to the lightest just off centre, in steps of 1-2 px (the machine steel's, or the polished rod's bright steel).
    static void ramp(int x0, int y0, int p, int[][] steel, int[] tones) {
        for (int x = 0; x < p; x++) {
            double pos = (x + 0.5) / p, peak = 0.42;
            double d = pos < peak ? (peak - pos) / peak : (pos - peak) / (1 - peak);
            int tone = (int) Math.round((1 - Math.pow(d, 1.4)) * (tones.length - 1));
            for (int y = 0; y < 8; y++) {
                set(x0 + x, y0 + y, steel, tones[Math.max(0, Math.min(tones.length - 1, tone))]);
            }
        }
    }

    // A brushed grain w x h: indices 0-3 into the field tones, laid in short runs along each row stepping by one tone
    // at a time, with no lone pixel lighter or darker than all four neighbours and no 2x2 checkers; the first seed
    // from `seed` whose neighbouring pixels differ 68-75% of the time (the top of the range, toward IE's busier grain).
    static int[][] grain(long seed, int w, int h) {
        // Bounded: failing that, the checker-free grain nearest the range's middle.
        int[][] best = null;
        double bestMiss = Double.MAX_VALUE;
        for (long s = seed; s < seed + 20_000; s++) {
            int[][] g = brushed(s, w, h);
            if (checkered(g)) {
                continue;
            }
            double differ = differ(g);
            if (differ >= 0.68 && differ <= 0.75) {
                return g;
            }
            double miss = Math.abs(differ - 0.715);
            if (miss < bestMiss) {
                bestMiss = miss;
                best = g;
            }
        }
        return best != null ? best : brushed(seed, w, h);
    }

    static int[][] brushed(long seed, int w, int h) {
        java.util.Random random = new java.util.Random(seed);
        int[][] g = new int[h][w];
        for (int y = 0; y < h; y++) {
            int tone = 1 + random.nextInt(2), run = 0;
            for (int x = 0; x < w; x++) {
                if (run <= 0) {
                    tone = Math.max(0, Math.min(3, tone + (random.nextBoolean() ? 1 : -1)));
                    run = 1 + random.nextInt(2);
                }
                g[y][x] = tone;
                run--;
            }
        }
        // No pixel more than two steps from the one above it (or, round the tile's edge, below it), so the grain's
        // steps stay small.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int above = g[(y + h - 1) % h][x];
                g[y][x] = Math.max(above - 2, Math.min(above + 2, g[y][x]));
            }
        }
        // A lone pixel lighter or darker than all its neighbours takes the step toward them.
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int[] n = { g[(y + h - 1) % h][x], g[(y + 1) % h][x], g[y][(x + w - 1) % w], g[y][(x + 1) % w] };
                    int max = Arrays.stream(n).max().getAsInt(), min = Arrays.stream(n).min().getAsInt();
                    if (g[y][x] > max) {
                        g[y][x] = max;
                    } else if (g[y][x] < min) {
                        g[y][x] = min;
                    }
                }
            }
        }
        return g;
    }

    // The share of horizontally and vertically neighbouring pixels (wrapping, as the tile repeats) that differ.
    static double differ(int[][] g) {
        int h = g.length, w = g[0].length, diff = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                diff += (g[y][x] != g[y][(x + 1) % w] ? 1 : 0) + (g[y][x] != g[(y + 1) % h][x] ? 1 : 0);
            }
        }
        return diff / (2.0 * w * h);
    }

    static boolean checkered(int[][] g) {
        int h = g.length, w = g[0].length;
        for (int y = 0; y + 1 < h; y++) {
            for (int x = 0; x + 1 < w; x++) {
                if (g[y][x] == g[y + 1][x + 1] && g[y][x + 1] == g[y + 1][x] && g[y][x] != g[y][x + 1]) {
                    return true;
                }
            }
        }
        return false;
    }

    // A pixel at luminance lum on a ramp, blended between its two nearest stops.
    static void set(int x, int y, int[][] ramp, int lum) {
        int c = ramp[ramp.length - 1][1];
        if (lum <= ramp[0][0]) {
            c = ramp[0][1];
        } else {
            for (int i = 0; i + 1 < ramp.length; i++) {
                if (lum <= ramp[i + 1][0]) {
                    double t = (lum - ramp[i][0]) / (double) (ramp[i + 1][0] - ramp[i][0]);
                    c = mix(ramp[i][1], ramp[i + 1][1], t);
                    break;
                }
            }
        }
        out.setRGB(x, y, 0xFF000000 | c);
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    static int scale(int c, double f) {
        int r = (int) Math.min(255, Math.round(((c >> 16) & 255) * f)), g = (int) Math.min(255, Math.round(((c >> 8) & 255) * f));
        int b = (int) Math.min(255, Math.round((c & 255) * f));
        return r << 16 | g << 8 | b;
    }
}
