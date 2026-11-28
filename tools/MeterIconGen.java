/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

// The meters' small buttons and the arrow on their fronts. Run from the repo root: java tools/MeterIconGen.java
//  - widget/arcforge/small_meter_above, small_meter_below (10x10): the Above / Below mode icons at the Chunk Loader's
//    small button size, the 16 px icons' teal arrow (outlined) over or under their bar;
//  - widget/arcforge/small_flip (10x10): two arrows each way, in the small icons' light grey with a shade row;
//  - block/meter/<kind>_front[_on].png: the strip under the screen redrawn as an arrow in the kind's colour pointing
//    from the input (left, facing the screen) to the output, and <kind>_front_flipped[_on].png pointing the other way
//    (a flipped meter takes from its right). Only the arrow's pixels change: the LED beside it is kept.
public final class MeterIconGen {
    private static final String SPRITES = "src/main/resources/assets/arcforge/textures/gui/sprites/widget/arcforge/";
    private static final String BLOCKS = "src/main/resources/assets/arcforge/textures/block/meter/";
    private static final int TEAL = 0xFF5FD4C4, OUTLINE = 0xFF1F4E48, BAR_DARK = 0xFF3A3A3A, BAR_LIGHT = 0xFFA0A0A0;
    private static final int WHITE = 0xFFE0E0E0, SHADE = 0xFF8C8C8C;
    // The strip's slot background (its shadowed inside).
    private static final int SLOT = 0xFF2B2F34;

    public static void main(String[] args) throws Exception {
        icon("small_meter_above", new String[] {
                "....OO....",
                "...OTTO...",
                "..OTTTTO..",
                ".OTTTTTTO.",
                "..OOTTOO..",
                "...OTTO...",
                "....OO....",
                "DDDDDDDDDD",
                "DLLLLLLLLD",
                "DDDDDDDDDD" });
        icon("small_meter_below", new String[] {
                "DDDDDDDDDD",
                "DLLLLLLLLD",
                "DDDDDDDDDD",
                "....OO....",
                "...OTTO...",
                "..OOTTOO..",
                ".OTTTTTTO.",
                "..OTTTTO..",
                "...OTTO...",
                "....OO...." });
        icon("small_flip", new String[] {
                ".....W....",
                "......W...",
                "WWWWWWWWW.",
                "SSSSSSW...",
                ".....W....",
                "....W.....",
                "...W......",
                ".WWWWWWWWW",
                "...WSSSSSS",
                "....W....." });
        // Each kind's light and mid tone (its rate bar's colours).
        String[] kinds = { "energy", "heat", "fluid", "gas" };
        int[][] tones = { { 0xFFFF8577, 0xFFE5483C }, { 0xFFFFB02E, 0xFFF26A16 }, { 0xFF7FB3F0, 0xFF4A8FE0 }, { 0xFFDDF3FD, 0xFF7FC8F0 } };
        for (int k = 0; k < kinds.length; k++) {
            String kind = kinds[k];
            int light = tones[k][0], mid = tones[k][1];
            for (String lit : new String[] { "", "_on" }) {
                File file = new File(BLOCKS + kind + "_front" + lit + ".png");
                BufferedImage front = ImageIO.read(file);
                BufferedImage right = copy(front), left = copy(front);
                arrow(right, light, mid, true);
                arrow(left, light, mid, false);
                ImageIO.write(right, "png", file);
                ImageIO.write(left, "png", new File(BLOCKS + kind + "_front_flipped" + lit + ".png"));
            }
        }
    }

    private static void icon(String name, String[] rows) throws Exception {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                int c = switch (rows[y].charAt(x)) {
                    case 'T' -> TEAL;
                    case 'O' -> OUTLINE;
                    case 'D' -> BAR_DARK;
                    case 'L' -> BAR_LIGHT;
                    case 'W' -> WHITE;
                    case 'S' -> SHADE;
                    default -> 0;
                };
                image.setRGB(x, y, c);
            }
        }
        ImageIO.write(image, "png", new File(SPRITES + name + ".png"));
    }

    // The arrow in the strip's slot (x 3 to 10, the head over rows 11 to 13): its shaft in the mid tone, the tip lit, the
    // head's upper pixel lit and its lower one dark (top-left light); toRight points it at x 10.
    private static void arrow(BufferedImage image, int light, int mid, boolean toRight) {
        int dark = darken(mid, 0.6);
        int frame = image.getRGB(4, 11);
        // Clear the slot, and the head's rows where the head goes, to the strip as it is round it.
        for (int x = 3; x <= 10; x++) {
            image.setRGB(x, 12, SLOT);
        }
        for (int x : new int[] { 5, 8 }) {
            image.setRGB(x, 11, frame);
            image.setRGB(x, 13, frame);
        }
        int tip = toRight ? 9 : 4, head = toRight ? 8 : 5;
        int from = toRight ? 4 : 5, to = toRight ? 8 : 9;
        for (int x = from; x <= to; x++) {
            image.setRGB(x, 12, mid);
        }
        image.setRGB(tip, 12, light);
        image.setRGB(head, 11, light);
        image.setRGB(head, 13, dark);
    }

    private static int darken(int argb, double k) {
        int r = (int) (((argb >> 16) & 255) * k), g = (int) (((argb >> 8) & 255) * k), b = (int) ((argb & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(source, 0, 0, null);
        return out;
    }
}
