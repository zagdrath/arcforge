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

// Builds the Gas Wellhead's block model and its textures together (docs/TEXTURE_STYLE.md, "Large model texturing" and
// "Gas Wellhead model"), as tools/WellHeadModelGen.java builds the Well Head's: a flanged casing head on the Well Head
// under it, a valve tree with a master valve, a swab cap and its stem on top, a wing running north to the flow line's
// flanged port (an output ring round a Natural Gas chip) with an adjustable choke on it (its red handwheel on top), and
// a pressure gauge on the east side (a white dial with its needle). Facing north; the blockstate turns it. A block
// model's UVs span a whole texture, so only a 16x16 texture keeps 1 texel per px: each visible face gets its own region
// painted to its exact size, packed into 16x16 sheets (textures/block/gas_wellhead/sheet_N), and the model's UVs point
// at them. Round parts' sides carry the stepped ramp across them (across their thickness on a thin band); flat faces are
// grain plates with a 1 px lit top and left edge and a dark bottom and right edge. All in the machine steel, but the
// handwheel (red), the gauge and the port.
// Run from the repository root: java tools/GasWellheadModelGen.java
public class GasWellheadModelGen {
    static final String MODEL = "src/main/resources/assets/arcforge/models/block/gas_wellhead.json";
    static final String TEXTURES = "src/main/resources/assets/arcforge/textures/block/gas_wellhead";

    // The machine steel ramp (luminance, colour), its grain tones, its edges and the round parts' ramp tones.
    static final int[][] STEEL = { { 24, 0x16181B }, { 47, 0x2B2F34 }, { 60, 0x383C42 }, { 68, 0x40454C }, { 75, 0x474C53 },
            { 92, 0x575D65 }, { 120, 0x727982 }, { 156, 0x959DA6 } };
    static final int[] FIELD = { 63, 66, 69, 72 }, RAMP = { 50, 57, 64, 71, 78, 85, 92 };
    static final int LIT = 84, DARK = 54;

    // An element: from, to (block px), whether it's round about y (its sides ramped) or about x (the outlet pipe),
    // and which faces are left out (hidden).
    record Element(String name, int[] from, int[] to, char round, String skip) {}

    // round: 'y' about y, 'x' along x, 'z' along z, '-' a box; 'r' the red handwheel (round about y), 'g' the gauge's
    // case (its east face the dial), 'o' the flow line's flange (its north face the port).
    static final List<Element> ELEMENTS = List.of(
            new Element("casing head flange", new int[] { 3, 0, 3 }, new int[] { 13, 2, 13 }, 'y', "down"),
            new Element("casing spool", new int[] { 5, 2, 5 }, new int[] { 11, 5, 11 }, 'y', "down up"),
            new Element("master valve body", new int[] { 4, 5, 4 }, new int[] { 12, 10, 12 }, 'y', "down"),
            new Element("swab cap", new int[] { 6, 10, 6 }, new int[] { 10, 13, 10 }, 'y', "down up"),
            new Element("tree cap flange", new int[] { 5, 13, 5 }, new int[] { 11, 14, 11 }, 'y', "down"),
            new Element("swab stem", new int[] { 7, 14, 7 }, new int[] { 9, 16, 9 }, 'y', "down"),
            new Element("wing", new int[] { 6, 6, 1 }, new int[] { 10, 9, 4 }, 'z', "south"),
            new Element("flow line flange", new int[] { 5, 5, 0 }, new int[] { 11, 10, 1 }, 'o', "south"),
            new Element("choke body", new int[] { 6, 9, 1 }, new int[] { 10, 11, 4 }, '-', "down"),
            new Element("choke stem", new int[] { 7, 11, 2 }, new int[] { 9, 12, 3 }, 'y', "down"),
            new Element("choke handwheel", new int[] { 5, 12, 0 }, new int[] { 11, 13, 5 }, 'r', ""),
            new Element("gauge stem", new int[] { 12, 7, 7 }, new int[] { 13, 8, 9 }, 'x', "west"),
            new Element("gauge case", new int[] { 13, 5, 6 }, new int[] { 15, 10, 10 }, 'g', "west"));

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
        json.append("{\n  \"parent\": \"minecraft:block/block\",\n  \"textures\": {\n    \"particle\": \"arcforge:block/gas_wellhead/sheet_0\",\n");
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
                if ((element.round() == 'y' || element.round() == 'r') && side) {
                    // Round about y: the ramp across the face, or across its height on a thin band (a flange, 2 px or less).
                    paint = w >= 3 && h <= 2 ? 'v' : 'u';
                } else if (element.round() == 'x' && !face.equals("east") && !face.equals("west")) {
                    // Round about x (along the outlet): the ramp across the face's other way.
                    paint = 'v';
                } else if (element.round() == 'z' && !face.equals("north") && !face.equals("south")) {
                    // Round about z (along the wing): across the height on its sides, across the width on top and under.
                    paint = side ? 'v' : 'u';
                }
                if (element.round() == 'r') {
                    paint = Character.toUpperCase(paint);
                } else if (element.round() == 'g' && face.equals("east")) {
                    paint = 'g';
                } else if (element.round() == 'o' && face.equals("north")) {
                    paint = 'o';
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
            json.append("    \"").append(s).append("\": \"arcforge:block/gas_wellhead/sheet_").append(s).append("\"").append(s < sheets.size() - 1 ? ",\n" : "\n");
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
        if (region.paint == 'g') {
            gauge(sheet, region);
            return;
        }
        if (region.paint == 'o') {
            port(sheet, region);
            return;
        }
        if (region.paint == 'U' || region.paint == 'V' || region.paint == 'P') {
            red(sheet, region);
            return;
        }
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

    // The choke's handwheel: the red ramp across its rim on the sides; seen from above or below, a red rim (lit on its far
    // edge, dark on its near one) round dark gaps, with spokes in from the rim's middles to a steel hub.
    static final int[] RED = { 0x5E1712, 0x7A2018, 0x96281E, 0xB43426, 0xC8432F, 0xD8573E, 0xC8432F };

    static void red(BufferedImage sheet, Region region) {
        for (int y = 0; y < region.h; y++) {
            for (int x = 0; x < region.w; x++) {
                int c;
                if (region.paint == 'P') {
                    boolean rim = x == 0 || y == 0 || x == region.w - 1 || y == region.h - 1;
                    boolean hub = Math.abs(2 * x - (region.w - 1)) <= 1 && Math.abs(2 * y - (region.h - 1)) <= 1;
                    boolean spoke = Math.abs(2 * x - (region.w - 1)) <= 1 || Math.abs(2 * y - (region.h - 1)) <= 1;
                    if (rim) {
                        c = y == 0 ? RED[5] : y == region.h - 1 ? RED[1] : x == 0 ? RED[4] : RED[2];
                    } else if (hub) {
                        c = 0x5A6068;
                    } else if (spoke) {
                        c = RED[0];
                    } else {
                        c = 0x16181B;
                    }
                } else {
                    int size = region.paint == 'U' ? region.w : region.h, i = region.paint == 'U' ? x : y;
                    c = RED[Math.max(0, Math.min(RED.length - 1, (ramp(i, size) - RAMP[0]) * (RED.length - 1) / (RAMP[RAMP.length - 1] - RAMP[0])))];
                }
                sheet.setRGB(region.x + x, region.y + y, 0xFF000000 | c);
            }
        }
    }

    // The pressure gauge's dial: a steel bezel round a white face, its needle dark, pointing up and right.
    static void gauge(BufferedImage sheet, Region region) {
        for (int y = 0; y < region.h; y++) {
            for (int x = 0; x < region.w; x++) {
                boolean rim = x == 0 || y == 0 || x == region.w - 1 || y == region.h - 1;
                int c = rim ? (x == 0 || y == 0 ? 0x727982 : 0x2B2F34) : 0xE8E6DC;
                sheet.setRGB(region.x + x, region.y + y, 0xFF000000 | c);
            }
        }
        if (region.w >= 4 && region.h >= 4) {
            sheet.setRGB(region.x + region.w / 2 - 1, region.y + region.h / 2, 0xFF16181B);
            sheet.setRGB(region.x + region.w / 2, region.y + region.h / 2 - 1, 0xFF16181B);
            sheet.setRGB(region.x + 1, region.y + region.h - 2, 0xFFC0392B);
        }
    }

    // The flow line's port: the flange's steel lip round an output ring (orange) and a Natural Gas chip in its bore.
    static void port(BufferedImage sheet, Region region) {
        for (int y = 0; y < region.h; y++) {
            for (int x = 0; x < region.w; x++) {
                boolean lip = x == 0 || y == 0 || x == region.w - 1 || y == region.h - 1;
                boolean ring = !lip && (x == 1 || y == 1 || x == region.w - 2 || y == region.h - 2);
                int c;
                if (lip) {
                    c = x == 0 || y == 0 ? 0x727982 : 0x16181B;
                } else if (ring) {
                    c = x == 1 || y == 1 ? 0xF5B574 : 0x9E5A1C;
                } else {
                    c = (x + y) % 2 == 0 ? 0xDCEEFA : 0xA8C8E2;
                }
                sheet.setRGB(region.x + x, region.y + y, 0xFF000000 | c);
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
