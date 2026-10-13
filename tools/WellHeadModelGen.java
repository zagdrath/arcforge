/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

// Builds the Well Head's block model and its textures together (docs/TEXTURE_STYLE.md, "Large model texturing" and
// "Pumpjack model"): a flanged casing head on the ground, a short valve tree with a flanged body, a side outlet with a
// valve and handwheel, and a stuffing box on top where the Pumpjack's polished rod passes through. A block model's UVs
// span a whole texture, so only a 16x16 texture keeps 1 texel per px: each visible face gets its own region painted to
// its exact size, packed into 16x16 sheets (textures/block/well_head/sheet_N), and the model's UVs point at them. Round
// parts' sides carry the stepped ramp across them (across the height on a thin band, a flange); flat
// faces are grain plates with a 1 px lit top and left edge and a dark bottom and right edge. All in the machine steel.
// Run from the repository root: java tools/WellHeadModelGen.java
public class WellHeadModelGen {
    static final String MODEL = "src/main/resources/assets/arcforge/models/block/well_head.json";
    static final String TEXTURES = "src/main/resources/assets/arcforge/textures/block/well_head";

    // The machine steel ramp (luminance, colour), its grain tones, its edges and the round parts' ramp tones.
    static final int[][] STEEL = { { 24, 0x16181B }, { 47, 0x2B2F34 }, { 60, 0x383C42 }, { 68, 0x40454C }, { 75, 0x474C53 },
            { 92, 0x575D65 }, { 120, 0x727982 }, { 156, 0x959DA6 } };
    static final int[] FIELD = { 63, 66, 69, 72 }, RAMP = { 50, 57, 64, 71, 78, 85, 92 };
    static final int LIT = 84, DARK = 54;

    // An element: from, to (block px), whether it's round about y (its sides ramped) or about x (the outlet pipe),
    // and which faces are left out (hidden).
    record Element(String name, int[] from, int[] to, char round, String skip) {}

    static final List<Element> ELEMENTS = List.of(
            new Element("casing head flange", new int[] { 2, 0, 2 }, new int[] { 14, 2, 14 }, 'y', "down"),
            new Element("casing spool", new int[] { 4, 2, 4 }, new int[] { 12, 6, 12 }, 'y', "down up"),
            new Element("casing head top flange", new int[] { 3, 6, 3 }, new int[] { 13, 7, 13 }, 'y', "down"),
            new Element("valve tree body", new int[] { 5, 7, 5 }, new int[] { 11, 11, 11 }, 'y', "down up"),
            new Element("valve tree flange", new int[] { 4, 11, 4 }, new int[] { 12, 12, 12 }, 'y', "down"),
            new Element("stuffing box", new int[] { 6, 12, 6 }, new int[] { 10, 15, 10 }, 'y', "down up"),
            new Element("gland nut", new int[] { 5, 15, 5 }, new int[] { 11, 16, 11 }, 'y', "down"),
            new Element("side outlet", new int[] { 11, 8, 7 }, new int[] { 14, 10, 9 }, 'x', "west"),
            new Element("outlet flange", new int[] { 14, 7, 6 }, new int[] { 15, 11, 10 }, '-', ""),
            new Element("valve bonnet", new int[] { 12, 10, 7 }, new int[] { 14, 12, 9 }, '-', "down"),
            new Element("valve stem", new int[] { 13, 12, 8 }, new int[] { 14, 14, 9 }, 'y', "down"),
            new Element("handwheel rim north", new int[] { 11, 14, 6 }, new int[] { 16, 15, 7 }, '-', ""),
            new Element("handwheel rim south", new int[] { 11, 14, 10 }, new int[] { 16, 15, 11 }, '-', ""),
            new Element("handwheel rim west", new int[] { 11, 14, 7 }, new int[] { 12, 15, 10 }, '-', "north south"),
            new Element("handwheel rim east", new int[] { 15, 14, 7 }, new int[] { 16, 15, 10 }, '-', "north south"),
            new Element("handwheel spoke", new int[] { 12, 14, 8 }, new int[] { 15, 15, 9 }, '-', "west east"));

    static final String[] FACES = { "north", "south", "west", "east", "up", "down" };

    // A face's painted region: its size, how it's painted ('p' plate, 'u' ramp across, 'v' ramp down), its seed, and
    // where it was packed (sheet, x, y).
    static final class Region {
        final int w, h, sheet, x, y;
        final char paint;
        final long seed;

        Region(int w, int h, char paint, long seed, int sheet, int x, int y) {
            this.w = w;
            this.h = h;
            this.paint = paint;
            this.seed = seed;
            this.sheet = sheet;
            this.x = x;
            this.y = y;
        }
    }

    public static void main(String[] args) throws Exception {
        List<BufferedImage> sheets = new ArrayList<>();
        // Shelf packing: the current sheet, its shelf's top and height, and how far along it is.
        int[] at = { 0, 0, 0, 0 };
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"parent\": \"minecraft:block/block\",\n  \"textures\": {\n    \"particle\": \"arcforge:block/well_head/sheet_0\",\n");
        StringBuilder elements = new StringBuilder();
        long seed = 1;
        for (int e = 0; e < ELEMENTS.size(); e++) {
            Element element = ELEMENTS.get(e);
            elements.append(e == 0 ? "" : ",\n").append("    {\n      \"name\": \"").append(element.name()).append("\",\n");
            elements.append("      \"from\": [").append(element.from()[0]).append(", ").append(element.from()[1]).append(", ").append(element.from()[2]).append("],\n");
            elements.append("      \"to\": [").append(element.to()[0]).append(", ").append(element.to()[1]).append(", ").append(element.to()[2]).append("],\n");
            elements.append("      \"faces\": {\n");
            int dx = element.to()[0] - element.from()[0], dy = element.to()[1] - element.from()[1], dz = element.to()[2] - element.from()[2];
            boolean first = true;
            for (String face : FACES) {
                if ((" " + element.skip() + " ").contains(" " + face + " ")) {
                    continue;
                }
                int w, h;
                boolean side = face.equals("north") || face.equals("south") || face.equals("west") || face.equals("east");
                if (face.equals("north") || face.equals("south")) {
                    w = dx;
                    h = dy;
                } else if (side) {
                    w = dz;
                    h = dy;
                } else {
                    w = dx;
                    h = dz;
                }
                char paint = 'p';
                if (element.round() == 'y' && side) {
                    // Round about y: the ramp across the face, or across its height on a thin band (a flange, 2 px or less).
                    paint = w >= 3 && h <= 2 ? 'v' : 'u';
                } else if (element.round() == 'x' && !face.equals("east") && !face.equals("west")) {
                    // Round about x (along the outlet): the ramp across the face's other way.
                    paint = 'v';
                }
                if (at[2] + w > 16) {
                    at[1] += at[3];
                    at[2] = 0;
                    at[3] = 0;
                }
                if (at[1] + h > 16 || sheets.isEmpty()) {
                    sheets.add(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
                    at[0] = sheets.size() - 1;
                    at[1] = 0;
                    at[2] = 0;
                    at[3] = 0;
                }
                Region region = new Region(w, h, paint, seed++, at[0], at[2], at[1]);
                paint(sheets.get(region.sheet), region);
                at[2] += w;
                at[3] = Math.max(at[3], h);
                elements.append(first ? "" : ",\n").append("        \"").append(face).append("\": {\"uv\": [").append(region.x).append(", ").append(region.y)
                        .append(", ").append(region.x + w).append(", ").append(region.y + h).append("], \"texture\": \"#").append(region.sheet).append("\"}");
                first = false;
            }
            elements.append("\n      }\n    }");
        }
        for (int s = 0; s < sheets.size(); s++) {
            json.append("    \"").append(s).append("\": \"arcforge:block/well_head/sheet_").append(s).append("\"").append(s < sheets.size() - 1 ? ",\n" : "\n");
        }
        json.append("  },\n  \"elements\": [\n").append(elements).append("\n  ]\n}\n");
        Files.createDirectories(Path.of(TEXTURES));
        for (int s = 0; s < sheets.size(); s++) {
            ImageIO.write(sheets.get(s), "png", new File(TEXTURES + "/sheet_" + s + ".png"));
        }
        Files.writeString(Path.of(MODEL), json.toString().replace("\n", "\r\n"), StandardCharsets.UTF_8);
        System.out.println("Wrote " + MODEL + " and " + sheets.size() + " sheets");
    }

    // Paints a region: a grain plate (its edges lit top and left, dark bottom and right, where it's 4 px or more that
    // way), or the round parts' ramp across it ('u') or down it ('v').
    static void paint(BufferedImage sheet, Region region) {
        Random random = new Random(region.seed * 7919L);
        for (int y = 0; y < region.h; y++) {
            int tone = 1 + random.nextInt(2), run = 0;
            for (int x = 0; x < region.w; x++) {
                if (run <= 0) {
                    tone = Math.max(0, Math.min(3, tone + (random.nextBoolean() ? 1 : -1)));
                    run = 1 + random.nextInt(2);
                }
                run--;
                int lum;
                if (region.paint == 'u' || region.paint == 'v') {
                    int size = region.paint == 'u' ? region.w : region.h, i = region.paint == 'u' ? x : y;
                    lum = ramp(i, size);
                } else {
                    lum = FIELD[tone];
                    if (region.h >= 4 && y == 0 || region.w >= 4 && x == 0) {
                        lum = LIT;
                    } else if (region.h >= 4 && y == region.h - 1 || region.w >= 4 && x == region.w - 1) {
                        lum = DARK;
                    }
                }
                set(sheet, region.x + x, region.y + y, lum);
            }
        }
    }

    // The round parts' ramp: darkest at both edges, lightest just off centre, in steps of 1-2 px.
    static int ramp(int i, int size) {
        double pos = (i + 0.5) / size, peak = 0.42;
        double d = pos < peak ? (peak - pos) / peak : (pos - peak) / (1 - peak);
        int tone = (int) Math.round((1 - Math.pow(d, 1.4)) * (RAMP.length - 1));
        return RAMP[Math.max(0, Math.min(RAMP.length - 1, tone))];
    }

    static void set(BufferedImage sheet, int x, int y, int lum) {
        int c = STEEL[STEEL.length - 1][1];
        if (lum <= STEEL[0][0]) {
            c = STEEL[0][1];
        } else {
            for (int i = 0; i + 1 < STEEL.length; i++) {
                if (lum <= STEEL[i + 1][0]) {
                    double t = (lum - STEEL[i][0]) / (double) (STEEL[i + 1][0] - STEEL[i][0]);
                    int a = STEEL[i][1], b = STEEL[i + 1][1];
                    int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
                    int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
                    int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
                    c = r << 16 | g << 8 | bl;
                    break;
                }
            }
        }
        sheet.setRGB(x, y, 0xFF000000 | c);
    }
}
