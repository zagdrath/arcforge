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
// tiles: rows 96-111: the furnace's refractory brick (0, 96, 16x8: buff firebrick in staggered courses, recessed
// mortar), the control panel's lit screen (16, 96, 8x6: cyan readouts on a dark glass), the motor's ribbed plate (64,
// 96), a seamless grating (80, 96), the girder flange (96, 104: seamless, a rivet every 4 px) and the hazard edge (112,
// 104); a fission core seen through a viewport, running (24, 96: the Cherenkov glow, full-bright) and shut down (40,
// 96: dark water between the fuel channels), both 16x16 and seamless; rows 112-119: the round parts' ramps, 2 to 14 px
// across, side by side, then the polished rod's bright ramp (104); rows 120-125: the burner fire (8x6), from the
// sheet's glow ramp at (40, 48); rows 128-191: the machine steel's grain field (116x64), then its edge columns (x
// 116-118: lit, dark, seam) and the generator steel's (x 119-121), 64 tall; rows 192-223: the generator steel's grain
// field (116x32); rows 224-229: the edge rows, 128 long: machine steel lit, dark, seam, then generator steel lit, dark,
// seam; rows 232-233: the rivet heads, 2x2: machine steel (0, 232), generator steel (2, 232); rows 240-255: a reactor's
// core seen through its windows, 16x16 and seamless: the fuel assemblies' tops (0, 240) and graphite moderator (16, 240).
//
// The Nuclear Steam Turbine's sheet is the same 256 rows (its rows 0-95 the Steam Turbine Array's) and 128 more for the
// pale sage turbine enamel (ModelMesh.Steel.ENAMEL, ENAMEL_WORN and the EN_ paints): its edge columns at x 122-124
// (rows 128-191, beside the machine and generator steels') and its edge rows at 234-236; rows 256-319: the enamel's
// grain field (116x64); rows 320-351: the same grain with a few worn, darker scuffs (116x32, for the casings' lower
// halves at handrail height); rows 352-359: the enamel's round-part ramps, 2 to 14 px across, laid out as row 112's;
// rows 360-367: its flat tone (0, 360) and the last-stage blades' blur at speed (16, 360: 8x8, streaks running up).
public class LargeModelTextureGen {
    static final String[] SHEETS = { "src/main/resources/assets/arcforge/textures/block/pumpjack/pumpjack.png",
            "src/main/resources/assets/arcforge/textures/block/distillation_array/refinery.png",
            "src/main/resources/assets/arcforge/textures/block/steam_turbine_array/turbine.png" };
    static final String NUCLEAR = "src/main/resources/assets/arcforge/textures/block/nuclear_steam_turbine/turbine.png";
    static final int W = 128, H = 256, NUCLEAR_H = 384;
    // Each material's edges (luminance): lit, dark, seam.
    static final int[] EDGES = { 84, 54, 37 }, GEN_EDGES = { 100, 70, 54 };

    // The machine steel ramp (luminance, colour) the tones are blended from, and the generator steel's.
    static final int[][] STEEL = { { 24, 0x16181B }, { 47, 0x2B2F34 }, { 60, 0x383C42 }, { 68, 0x40454C }, { 75, 0x474C53 },
            { 92, 0x575D65 }, { 120, 0x727982 }, { 156, 0x959DA6 } };
    static final int[][] GEN = { { 47, 0x2E3338 }, { 67, 0x3E444B }, { 83, 0x4E545C }, { 105, 0x646A72 }, { 131, 0x7E848C }, { 160, 0x9AA0A8 } };

    // Grain tones (luminance) for the machine steel field and the generator steel's: neighbouring steps of 3.
    static final int[] FIELD = { 63, 66, 69, 72 }, GEN_FIELD = { 79, 82, 85, 88 };

    // The turbine enamel: a pale sage-grey ramp (luminance, colour), its six tones from a mid sage-grey to a near-white
    // sage with two darker steps under them for its seams and scuffs; the grain's tones, and its edges (lit, dark, seam).
    static final int[][] ENAMEL = { { 74, 0x444C46 }, { 97, 0x5A635B }, { 120, 0x727C73 }, { 139, 0x858F86 }, { 158, 0x98A299 },
            { 176, 0xABB4AC }, { 195, 0xBEC6BE }, { 213, 0xD1D8D0 } };
    static final int[] EN_FIELD = { 162, 165, 168, 171 }, EN_EDGES = { 186, 140, 118 };

    static BufferedImage out;

    public static void main(String[] args) throws Exception {
        for (String sheet : SHEETS) {
            paint(sheet, sheet, H);
        }
        paint(NUCLEAR, SHEETS[2], NUCLEAR_H);
    }

    // Paints sheet (h rows), its rows 0-95 kept from base.
    static void paint(String sheet, String base, int h) throws Exception {
        BufferedImage old = ImageIO.read(new File(base));
        out = new BufferedImage(W, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < Math.min(96, old.getHeight()); y++) {
            for (int x = 0; x < W; x++) {
                out.setRGB(x, y, old.getRGB(x, y));
            }
        }
        brick(0, 96);
        screen(16, 96);
        lamp(96, 96);
        core(24, 96, true);
        core(40, 96, false);
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
        fuelTops(0, 240);
        graphite(16, 240);
        if (h > H) {
            enamel();
        }
        new File(sheet).getParentFile().mkdirs();
        ImageIO.write(out, "png", new File(sheet));
        System.out.println("Wrote " + sheet);
    }

    // The turbine enamel's rows (see the class comment).
    static void enamel() {
        for (int i = 0; i < 3; i++) {
            edge(122 + i, 128, 1, 64, ENAMEL, EN_EDGES[i], 60 + i);
            edge(0, 234 + i, 128, 1, ENAMEL, EN_EDGES[i], 70 + i);
        }
        rivet(4, 232, ENAMEL, true);
        field(0, 256, 116, 64, ENAMEL, EN_FIELD, 33);
        field(0, 320, 116, 32, ENAMEL, EN_FIELD, 34);
        scuffs(0, 320, 116, 32, 35);
        for (int p = 2; p <= 14; p++) {
            ramp(rampX(p), 352, p, ENAMEL, new int[] { 140, 148, 156, 164, 172, 180, 188 });
        }
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                set(x, 360 + y, ENAMEL, EN_FIELD[1 + (x + y) % 2]);
            }
        }
        blur(16, 360);
    }

    // A few worn scuffs on the enamel w x h: short smears along the rows, a darker core with a rim a step lighter,
    // where boots and tools rub the casings; well apart, and never on the field's edge rows (a plate's edge strip covers
    // its outer pixels anyway).
    static void scuffs(int x0, int y0, int w, int h, long seed) {
        java.util.Random random = new java.util.Random(seed);
        int count = 7;
        for (int i = 0; i < count; i++) {
            int length = 3 + random.nextInt(5), rows = 1 + random.nextInt(2);
            int sx = 2 + (w - length - 4) * i / (count - 1) + random.nextInt(3), sy = 3 + random.nextInt(h - rows - 6);
            for (int r = -1; r <= rows; r++) {
                for (int c = -1; c <= length; c++) {
                    boolean core = r >= 0 && r < rows && c >= 0 && c < length && !(r == 0 && c == length - 1 && rows == 1);
                    boolean rim = !core && (r >= 0 && r < rows || c >= 0 && c < length);
                    if (core) {
                        set(x0 + sx + c, y0 + sy + r, ENAMEL, 144 + random.nextInt(2) * 3);
                    } else if (rim) {
                        set(x0 + sx + c, y0 + sy + r, ENAMEL, 154);
                    }
                }
            }
        }
    }

    // The last-stage blades at speed, 8x8, seamless: streaks of the blade steel running up the tile (the way they
    // move past a sight port), each column its own tone, stepping a little every few rows.
    static void blur(int x0, int y0) {
        int[] columns = { 118, 146, 168, 150, 112, 134, 160, 138 };
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                int step = (y + x * 3) / 3 % 2 == 0 ? 0 : 6;
                set(x0 + x, y0 + y, STEEL, columns[x] - step);
            }
        }
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

    // Refractory firebrick 16x8, seen at a furnace's peep holes and openings: two courses of bricks 8x4, the second
    // offset by half a brick, each brick in one of three close buff tones with its own lit top edge, and the mortar
    // joints darker (sunk between them). Tiles seamlessly both ways.
    static void brick(int x0, int y0) {
        int[] tones = { 0x9C7A58, 0x927052, 0x8A684A };
        int lit = 0xAE8C68, mortar = 0x4E3C2E;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                int course = y / 4, row = y % 4;
                int bx = (x + (course == 1 ? 4 : 0)) % 16;
                int c;
                if (row == 3 || bx % 8 == 7) {
                    c = mortar;
                } else {
                    int brick = (bx / 8 + course * 2 + x0) % 3;
                    c = row == 0 ? lit : tones[brick];
                    if (row == 2 && bx % 8 == 6) {
                        c = scale(tones[brick], 0.9);
                    }
                }
                out.setRGB(x0 + x, y0 + y, 0xFF000000 | c);
            }
        }
    }

    // A hot cell's ceiling lamp 16x8, drawn full-bright, laid on a 10x8 face from its top-left: two warm-white tubes
    // (rows 1-2 and 5-6, each a bright core row over a softer one) between the reflector's light grey rows, a dark rim at
    // the top and bottom and the tube holders (darker) at columns 0 and 9; past them the bare reflector.
    static void lamp(int x0, int y0) {
        int rim = 0x6E7276, reflector = 0xC8C6BE, holder = 0x8A8E92, core = 0xFFFBEA, soft = 0xF2E2B8;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                int c;
                if (y == 0 || y == 7) {
                    c = rim;
                } else if (x == 0 || x == 9) {
                    c = holder;
                } else if (x < 9 && (y == 1 || y == 5)) {
                    c = core;
                } else if (x < 9 && (y == 2 || y == 6)) {
                    c = soft;
                } else {
                    c = reflector;
                }
                out.setRGB(x0 + x, y0 + y, 0xFF000000 | c);
            }
        }
    }

    // A control panel's screen 8x6, drawn full-bright: dark glass with a cyan bar graph and two readout lines.
    static void screen(int x0, int y0) {
        int glass = 0x10181C, lit = 0xB8F2EA, mid = 0x5FD4C4, dim = 0x2F6E68;
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 8; x++) {
                out.setRGB(x0 + x, y0 + y, 0xFF000000 | glass);
            }
        }
        int[] bars = { 3, 4, 2, 4 };
        for (int i = 0; i < bars.length; i++) {
            for (int y = 0; y < bars[i]; y++) {
                out.setRGB(x0 + 1 + i, y0 + 4 - y, 0xFF000000 | (y == bars[i] - 1 ? lit : mid));
            }
        }
        for (int x = 5; x < 7; x++) {
            out.setRGB(x0 + x, y0 + 1, 0xFF000000 | mid);
            out.setRGB(x0 + x, y0 + 3, 0xFF000000 | dim);
        }
    }

    // A fission core 16x16 seen through a reactor viewport, seamless both ways: two fuel channels 2 px wide upright in
    // the water at x 2-3 and 10-11. Running, the water glows Cherenkov blue-cyan (drawn full-bright), stepping one tone at a
    // time from the channels' edges up to a pale middle, never dithered; the channels stay a deeper blue, a step lighter
    // where their spacer grids sit (rows 4 and 12). Shut down it is the same core dark: deep blue-black water, the
    // channels a few steps lighter with a lit left edge.
    static void core(int x0, int y0, boolean running) {
        int[] glow = { 0x0A6FB8, 0x0E8AD2, 0x16A4E6, 0x2ABCF2, 0x4CD2FA, 0x7EE4FF, 0xB8F4FF };
        int[] dark = { 0x0C121A, 0x0F1620, 0x121B27, 0x16212F, 0x1B2838, 0x223246, 0x2A3C52 };
        // By column, within each 8 px repeat starting at a channel's left edge: the channel, then the water out to its
        // middle and back.
        int[] glowSteps = { 1, 0, 2, 3, 5, 6, 5, 3 }, darkSteps = { 5, 4, 1, 0, 0, 0, 0, 1 };
        for (int y = 0; y < 16; y++) {
            boolean grid = y % 8 == 4;
            for (int x = 0; x < 16; x++) {
                int c = (x - 2 + 16) % 8;
                int step = running ? glowSteps[c] : darkSteps[c];
                if (grid && c < 2) {
                    step = Math.min(6, step + 1);
                }
                out.setRGB(x0 + x, y0 + y, 0xFF000000 | (running ? glow : dark)[step]);
            }
        }
    }

    // The fuel assemblies' tops 16x16, seamless, on the same 8 px pitch as the core's fuel channels: each a 7x7 top
    // nozzle plate (lit top and left edge, dark bottom and right) with its flow hole recessed in the middle (shadowed top
    // and left lip, lit bottom and right, the water in it), the dark water of the core between them.
    static void fuelTops(int x0, int y0) {
        int water = 0x0F1620, hole = 0x16212F;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int cx = x % 8, cy = y % 8, c;
                if (cx == 7 || cy == 7) {
                    c = water;
                } else if (cy == 0 || cx == 0) {
                    c = cx == 6 || cy == 6 ? 0x383C42 : 0x575D65;
                } else if (cy == 6 || cx == 6) {
                    c = 0x2B2F34;
                } else if (cx >= 2 && cx <= 4 && cy >= 2 && cy <= 4) {
                    c = cx == 3 && cy == 3 ? hole : cy == 2 || cx == 2 ? (cx == 4 || cy == 4 ? 0x383C42 : 0x16181B) : 0x727982;
                } else {
                    c = cx < 4 ? 0x474C53 : 0x40454C;
                }
                out.setRGB(x0 + x, y0 + y, 0xFF000000 | c);
            }
        }
    }

    // Graphite moderator 16x16, seamless: dark matte blocks 8x4 in courses, each offset half a block from the one
    // below, a 1 px seam above and left of each block and a lighter top edge under it; no grain (graphite isn't steel).
    static void graphite(int x0, int y0) {
        for (int y = 0; y < 16; y++) {
            int course = y / 4, row = y % 4, shift = course % 2 == 1 ? 4 : 0;
            for (int x = 0; x < 16; x++) {
                int col = (x + shift) % 8, c;
                if (row == 0 || col == 0) {
                    c = 0x16181B;
                } else if (row == 1) {
                    c = 0x383C42;
                } else {
                    c = col < 5 ? 0x2B2F34 : 0x272A2F;
                }
                out.setRGB(x0 + x, y0 + y, 0xFF000000 | c);
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
