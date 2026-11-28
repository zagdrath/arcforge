/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

// The Pressurized Cylinders' tier bands (rows 5 and 6 of each block/pressurized_cylinder/<tier>_body), shaded with the
// body's own ramp across them: each column the tier's colour scaled to the body's tone there (darker at the edges, a
// highlight down the middle mixed toward white), the top row a step lighter (the lit edge the body's rims have). Run
// from the repo root: java tools/CylinderBandGen.java. The tier's colour is read from the band's middle tone (column 8
// of row 6, where the body is at its mid tone), which this keeps, so it can be rerun.
public final class CylinderBandGen {
    private static final String DIR = "src/main/resources/assets/arcforge/textures/block/pressurized_cylinder/";
    // A plain row of the body (its ramp across), its lighter rim row, and the body's mid tone.
    private static final int BODY_ROW = 8, RIM_ROW = 3, MID_COLUMN = 8;

    public static void main(String[] args) throws Exception {
        for (String tier : new String[] { "wrought", "tempered", "hardened", "arcforged", "creative" }) {
            File file = new File(DIR + tier + "_body.png");
            BufferedImage image = ImageIO.read(file);
            int mid = image.getRGB(MID_COLUMN, 6) & 0xFFFFFF;
            double midLum = lum(image.getRGB(MID_COLUMN, BODY_ROW));
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, 5, 0xFF000000 | shade(mid, lum(image.getRGB(x, RIM_ROW)) / midLum));
                image.setRGB(x, 6, 0xFF000000 | shade(mid, lum(image.getRGB(x, BODY_ROW)) / midLum));
            }
            ImageIO.write(image, "png", file);
        }
    }

    // The colour at k of its own tone: darker below 1, toward white above it.
    private static int shade(int rgb, double k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        if (k <= 1.0) {
            r = (int) Math.round(r * k);
            g = (int) Math.round(g * k);
            b = (int) Math.round(b * k);
        } else {
            double w = Math.min(1.0, (k - 1.0) * 0.9);
            r = (int) Math.round(r + (255 - r) * w);
            g = (int) Math.round(g + (255 - g) * w);
            b = (int) Math.round(b + (255 - b) * w);
        }
        return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    private static double lum(int argb) {
        return 0.299 * ((argb >> 16) & 255) + 0.587 * ((argb >> 8) & 255) + 0.114 * (argb & 255);
    }
}
