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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;

// Builds the Remote Handling Arm's block models and their textures together, as tools/WellHeadModelGen.java does the
// Well Head's (docs/TEXTURE_STYLE.md, "Large model texturing", rule 6): each visible face gets its own region painted to
// its exact size, packed into 16x16 sheets (textures/block/remote_handling_arm/sheet_N), and the models' UVs point at
// them. Facing north (the Vessel Port it serves is the block to the north):
//  - the mount (remote_handling_arm, and remote_handling_arm_on with its status lamp lit): a frame against the cryostat
//    wall round a 6 px opening, the cradle behind it the arm slides in (its base, cheeks and end plate) and the drive's
//    housings, which carry its port plates at the block's edges: Input on the top and both sides, Output underneath,
//    Energy on the back (the default side configuration);
//  - the telescoping arm's three standalone parts, at rest inside the cradle (RemoteHandlingArmRenderer slides them out
//    north through the opening, into the port): stage 1 (hazard yellow, its front a steel collar), stage 2 (bright
//    machined steel) and the gripper (its wrist and two jaws);
//  - remote_handling_arm_item: the mount with the arm at rest, for the item.
// Flat faces are grain plates (a 1 px lit top and left edge, a dark bottom and right edge); the stages' long sides carry
// their own tone's plate; port plates are the 8x8 single-block design (rows and columns 3-6 and 9-12 of the 10x10 port
// texture). Run from the repository root: java tools/RemoteHandlingArmModelGen.java
public class RemoteHandlingArmModelGen {
    static final String MODELS = "src/main/resources/assets/arcforge/models/block/fusion/";
    static final String TEXTURES = "src/main/resources/assets/arcforge/textures/block/remote_handling_arm";
    static final String PORTS = "src/main/resources/assets/arcforge/textures/block/port/";

    static final int[][] STEEL = { { 24, 0x16181B }, { 47, 0x2B2F34 }, { 60, 0x383C42 }, { 68, 0x40454C }, { 75, 0x474C53 },
            { 92, 0x575D65 }, { 120, 0x727982 }, { 156, 0x959DA6 } };
    static final int[] FIELD = { 63, 66, 69, 72 }, BRIGHT = { 96, 100, 104, 108 };
    static final int LIT = 84, DARK = 54, BRIGHT_LIT = 124, BRIGHT_DARK = 80;
    // Hazard yellow (the stripe's #E0B020) as a plate: its field tones, lit and dark edges.
    static final int[] YELLOW = { 0xD0A41E, 0xD8AA20, 0xE0B020, 0xE6B828 };
    static final int YELLOW_LIT = 0xF0CC50, YELLOW_DARK = 0x9A7614;
    // The status lamp, off and lit (the cyan LED ramp).
    static final int LAMP_OFF = 0x2F6E68, LAMP_RIM = 0x1C3A38, LAMP_ON = 0x5FD4C4, LAMP_GLINT = 0xB8F2EA;

    // An element: name, from, to (px), faces left out, its paint ('p' steel, 'b' bright steel, 'y' yellow, 'l' lamp),
    // and the port painted on some of its faces (face -> port texture).
    record Element(String name, double[] from, double[] to, String skip, char paint, Map<String, String> ports) {
        Element(String name, double[] from, double[] to, String skip, char paint) {
            this(name, from, to, skip, paint, Map.of());
        }
    }

    static double[] v(double x, double y, double z) {
        return new double[] { x, y, z };
    }

    static final List<Element> MOUNT = List.of(
            new Element("frame top", v(1, 11, 0), v(15, 15, 3), "north", 'p'),
            new Element("frame bottom", v(1, 1, 0), v(15, 5, 3), "north", 'p'),
            new Element("frame west", v(1, 5, 0), v(5, 11, 3), "north", 'p'),
            new Element("frame east", v(11, 5, 0), v(15, 11, 3), "north", 'p'),
            new Element("cradle base", v(4, 4, 3), v(12, 5, 14), "down", 'p'),
            new Element("cradle west cheek", v(3, 4, 3), v(4, 12, 14), "", 'p'),
            new Element("cradle east cheek", v(12, 4, 3), v(13, 12, 14), "", 'p'),
            new Element("end plate", v(3, 4, 14), v(13, 12, 16), "", 'p', Map.of("south", "energy")),
            new Element("drive housing", v(4, 12, 6), v(12, 16, 14), "down", 'p', Map.of("up", "input")),
            new Element("skid", v(4, 0, 6), v(12, 4, 14), "up", 'p', Map.of("down", "output")),
            new Element("west motor", v(0, 4, 6), v(3, 12, 14), "east", 'p', Map.of("west", "input")),
            new Element("east motor", v(13, 4, 6), v(16, 12, 14), "west", 'p', Map.of("east", "input")));

    static final List<Element> STAGE_1 = List.of(
            new Element("stage 1", v(5, 5, 4.5), v(11, 11, 14), "south", 'y'),
            new Element("stage 1 collar", v(4.5, 4.5, 4), v(11.5, 11.5, 5), "south", 'p'));
    static final List<Element> STAGE_2 = List.of(new Element("stage 2", v(6, 6, 5), v(10, 10, 13), "south", 'b'));
    static final List<Element> GRIPPER = List.of(
            new Element("wrist", v(6.5, 6.5, 2.5), v(9.5, 9.5, 5), "south", 'p'),
            new Element("west jaw", v(6, 6.5, 0.5), v(7, 9.5, 2.5), "south", 'b'),
            new Element("east jaw", v(9, 6.5, 0.5), v(10, 9.5, 2.5), "south", 'b'));

    static final String[] FACES = { "north", "south", "west", "east", "up", "down" };

    static final List<BufferedImage> SHEETS = new ArrayList<>();
    // Shelf packing: the current sheet, its shelf's top and height, and how far along it is.
    static final int[] AT = { 0, 0, 0, 0 };
    static long seed = 1;
    static final Map<String, int[]> REGIONS = new java.util.HashMap<>();
    // The first pass only collects each region's size, so they can be packed tallest first.
    static boolean collecting;
    static final Map<String, Object[]> WANTED = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        Element lampOff = new Element("status lamp", v(12, 15, 0.5), v(14, 16, 2.5), "down", 'l');
        Element lampOn = new Element("status lamp", v(12, 15, 0.5), v(14, 16, 2.5), "down", 'L');
        List<Element> mount = new ArrayList<>(MOUNT);
        mount.add(lampOff);
        List<Element> mountOn = new ArrayList<>(MOUNT);
        mountOn.add(lampOn);
        List<Element> item = new ArrayList<>(mount);
        item.addAll(STAGE_1);
        item.addAll(GRIPPER);
        List<List<Element>> all = List.of(mount, mountOn, STAGE_1, STAGE_2, GRIPPER, item);
        collecting = true;
        for (List<Element> list : all) {
            elements(list);
        }
        collecting = false;
        List<Map.Entry<String, Object[]>> wanted = new ArrayList<>(WANTED.entrySet());
        wanted.sort((a, b) -> Integer.compare((int) b.getValue()[1], (int) a.getValue()[1]));
        for (Map.Entry<String, Object[]> region : wanted) {
            Object[] r = region.getValue();
            int[] at = place((int) r[0], (int) r[1]);
            paint(SHEETS.get(at[0]), at[1], at[2], (int) r[0], (int) r[1], (char) r[2], (String) r[3], seed++);
            REGIONS.put(region.getKey(), at);
        }
        Map<String, String> models = new LinkedHashMap<>();
        models.put("remote_handling_arm", elements(mount));
        models.put("remote_handling_arm_on", elements(mountOn));
        models.put("remote_handling_arm_stage_1", elements(STAGE_1));
        models.put("remote_handling_arm_stage_2", elements(STAGE_2));
        models.put("remote_handling_arm_gripper", elements(GRIPPER));
        models.put("remote_handling_arm_item", elements(item));
        Files.createDirectories(Path.of(TEXTURES));
        for (int s = 0; s < SHEETS.size(); s++) {
            ImageIO.write(SHEETS.get(s), "png", new File(TEXTURES + "/sheet_" + s + ".png"));
        }
        for (Map.Entry<String, String> model : models.entrySet()) {
            StringBuilder json = new StringBuilder();
            json.append("{\n  \"parent\": \"minecraft:block/block\",\n  \"textures\": {\n    \"particle\": \"arcforge:block/remote_handling_arm/sheet_0\",\n");
            for (int s = 0; s < SHEETS.size(); s++) {
                json.append("    \"").append(s).append("\": \"arcforge:block/remote_handling_arm/sheet_").append(s).append("\"")
                        .append(s < SHEETS.size() - 1 ? ",\n" : "\n");
            }
            json.append("  },\n  \"elements\": [\n").append(model.getValue()).append("\n  ]\n}\n");
            Files.writeString(Path.of(MODELS + model.getKey() + ".json"), json.toString(), StandardCharsets.UTF_8);
        }
        System.out.println("Wrote " + models.size() + " models and " + SHEETS.size() + " sheets");
    }

    static String num(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    // The elements' JSON, each face given its own painted region.
    static String elements(List<Element> list) throws Exception {
        StringBuilder out = new StringBuilder();
        for (int e = 0; e < list.size(); e++) {
            Element element = list.get(e);
            out.append(e == 0 ? "" : ",\n").append("    {\n      \"name\": \"").append(element.name()).append("\",\n");
            out.append("      \"from\": [").append(num(element.from()[0])).append(", ").append(num(element.from()[1])).append(", ")
                    .append(num(element.from()[2])).append("],\n");
            out.append("      \"to\": [").append(num(element.to()[0])).append(", ").append(num(element.to()[1])).append(", ")
                    .append(num(element.to()[2])).append("],\n");
            out.append("      \"faces\": {\n");
            double dx = element.to()[0] - element.from()[0], dy = element.to()[1] - element.from()[1], dz = element.to()[2] - element.from()[2];
            boolean first = true;
            for (String face : FACES) {
                if ((" " + element.skip() + " ").contains(" " + face + " ")) {
                    continue;
                }
                double w, h;
                if (face.equals("north") || face.equals("south")) {
                    w = dx;
                    h = dy;
                } else if (face.equals("west") || face.equals("east")) {
                    w = dz;
                    h = dy;
                } else {
                    w = dx;
                    h = dz;
                }
                // A half-px face is painted a whole texel and its UV spans half of it.
                int pw = (int) Math.ceil(w - 1.0E-6), ph = (int) Math.ceil(h - 1.0E-6);
                // A part shared between models (the mount in the item) shows the same region in each.
                String key = element + " " + face;
                if (collecting) {
                    WANTED.putIfAbsent(key, new Object[] { pw, ph, element.paint(), element.ports().get(face) });
                    continue;
                }
                int[] at = REGIONS.get(key);
                out.append(first ? "" : ",\n").append("        \"").append(face).append("\": {\"uv\": [").append(at[1]).append(", ").append(at[2])
                        .append(", ").append(num(at[1] + w)).append(", ").append(num(at[2] + h)).append("], \"texture\": \"#").append(at[0]).append("\"}");
                first = false;
            }
            out.append("\n      }\n    }");
        }
        return out.toString();
    }

    // Packs a w x h region: {sheet, x, y}.
    static int[] place(int w, int h) {
        if (AT[2] + w > 16) {
            AT[1] += AT[3];
            AT[2] = 0;
            AT[3] = 0;
        }
        if (AT[1] + h > 16 || SHEETS.isEmpty()) {
            SHEETS.add(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
            AT[0] = SHEETS.size() - 1;
            AT[1] = 0;
            AT[2] = 0;
            AT[3] = 0;
        }
        int[] at = { AT[0], AT[2], AT[1] };
        AT[2] += w;
        AT[3] = Math.max(AT[3], h);
        return at;
    }

    // Paints a region: a grain plate in its paint's tones (its edges lit top and left, dark bottom and right, where it's
    // 4 px or more that way), the lamp, and a port plate centred on it.
    static void paint(BufferedImage sheet, int x0, int y0, int w, int h, char paint, String port, long seed) throws Exception {
        Random random = new Random(seed * 7919L);
        for (int y = 0; y < h; y++) {
            int tone = 1 + random.nextInt(2), run = 0;
            for (int x = 0; x < w; x++) {
                if (run <= 0) {
                    tone = Math.max(0, Math.min(3, tone + (random.nextBoolean() ? 1 : -1)));
                    run = 1 + random.nextInt(2);
                }
                run--;
                boolean lit = h >= 4 && y == 0 || w >= 4 && x == 0;
                boolean dark = !lit && (h >= 4 && y == h - 1 || w >= 4 && x == w - 1);
                int rgb;
                switch (paint) {
                    case 'y' -> rgb = lit ? YELLOW_LIT : dark ? YELLOW_DARK : YELLOW[tone];
                    case 'b' -> rgb = steel(lit ? BRIGHT_LIT : dark ? BRIGHT_DARK : BRIGHT[tone]);
                    case 'l' -> rgb = x == 0 && y == 0 ? LAMP_OFF : (x + y) % 2 == 0 && w > 1 ? LAMP_OFF : LAMP_RIM;
                    case 'L' -> rgb = x == 0 && y == 0 ? LAMP_GLINT : LAMP_ON;
                    default -> rgb = steel(lit ? LIT : dark ? DARK : FIELD[tone]);
                }
                sheet.setRGB(x0 + x, y0 + y, 0xFF000000 | rgb);
            }
        }
        if (paint == 'l') {
            // The unlit lamp: one flat dark cyan with a darker rim corner, no pattern.
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    sheet.setRGB(x0 + x, y0 + y, 0xFF000000 | (x == w - 1 || y == h - 1 ? LAMP_RIM : LAMP_OFF));
                }
            }
        }
        if (port != null && w >= 8 && h >= 8) {
            BufferedImage src = ImageIO.read(new File(PORTS + port + ".png"));
            int[] rows = { 3, 4, 5, 6, 9, 10, 11, 12 };
            int ox = x0 + (w - 8) / 2, oy = y0 + (h - 8) / 2;
            for (int j = 0; j < 8; j++) {
                for (int i = 0; i < 8; i++) {
                    int argb = src.getRGB(rows[i], rows[j]);
                    if ((argb >>> 24) > 128) {
                        sheet.setRGB(ox + i, oy + j, argb | 0xFF000000);
                    }
                }
            }
        }
    }

    // A luminance on the machine steel ramp, as its colour.
    static int steel(int lum) {
        int c = STEEL[STEEL.length - 1][1];
        if (lum <= STEEL[0][0]) {
            return STEEL[0][1];
        }
        for (int i = 0; i + 1 < STEEL.length; i++) {
            if (lum <= STEEL[i + 1][0]) {
                double t = (lum - STEEL[i][0]) / (double) (STEEL[i + 1][0] - STEEL[i][0]);
                int a = STEEL[i][1], b = STEEL[i + 1][1];
                int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
                int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
                int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
                return r << 16 | g << 8 | bl;
            }
        }
        return c;
    }
}
