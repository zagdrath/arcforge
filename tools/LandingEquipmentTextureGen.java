/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Native pixel grids and vanilla armor UVs, following docs/TEXTURE_STYLE.md. No resampling. */
public class LandingEquipmentTextureGen {
    static final String ROOT = "src/main/resources/assets/arcforge/";
    static final int OUT = 0x0C0D0F, DARK = 0x2B2F34, STEEL = 0x727982, IVORY = 0xD6D3C8,
            SPRING = 0xE8913A, CYAN = 0x5FD4C4;

    public static void main(String[] args) throws Exception {
        // A valid equipment asset keeps the boots in humanoid render states; geometry is drawn
        // by LongFallBootsLayer, so the vanilla armor texture must remain transparent.
        save(new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB),
                "textures/entity/equipment/humanoid/long_fall_boots.png");
        for (boolean armored : new boolean[] {false, true}) {
            String name = armored ? "armored_long_fall_boots" : "shock_boots";
            sprite(name, armored);
            atlas(armored);
        }
        // Other equipment is regenerated only when explicitly requested.
        if (java.util.Arrays.asList(args).contains("--hazmat")) hazmat();
    }

    static void pixel(BufferedImage im, int x, int y, int rgb) { im.setRGB(x, y, 0xFF000000 | rgb); }
    static void rect(BufferedImage im, int x, int y, int w, int h, int rgb) {
        for (int iy = y; iy < y + h; iy++) for (int ix = x; ix < x + w; ix++) pixel(im, ix, iy, rgb);
    }
    static void save(BufferedImage im, String path) throws Exception {
        File out = new File(ROOT + path); out.getParentFile().mkdirs(); ImageIO.write(im, "PNG", out);
    }

    static void sprite(String name, boolean armored) throws Exception {
        // Filled calf uppers and broad forward toes give each boot a clear footwear silhouette.
        // The orange heel damper and cyan battery retain the landing-equipment identity.
        String[] boot = {
            "  OOOO ", "  OHFO ", "  OFCO ", "  OF.SO", "  OH.SO", "  O..SO",
            "  OF.SO", " OHHFSO", "OHFFFSO", "OFFFF.O", "O....OO", "OOOOOO "
        };
        BufferedImage im = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int side = 0; side < 2; side++) {
            int ox = side == 0 ? 1 : 8, oy = side == 0 ? 4 : 2;
            for (int y = 0; y < boot.length; y++) for (int x = 0; x < boot[y].length(); x++) {
                int color = switch (boot[y].charAt(x)) {
                    case 'O' -> OUT; case '.' -> DARK; case 'H' -> STEEL; case 'F' -> armored ? 0x575D65 : IVORY;
                    case 'S' -> SPRING; case 'C' -> CYAN; default -> -1;
                };
                if (color >= 0) pixel(im, ox + x, oy + y, color);
            }
        }
        save(im, "textures/item/" + name + ".png");
        // Enlarged review-only image; the shipped sprite above remains exactly 16x16.
        BufferedImage preview = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) preview.setRGB(x, y, im.getRGB(x / 16, y / 16));
        new File("build/equipment-previews").mkdirs();
        ImageIO.write(preview, "PNG", new File("build/equipment-previews/" + name + ".png"));
    }

    // A CubeListBuilder box uses d rows of top/bottom and h rows of four sides. One texel per model pixel.
    static void cube(BufferedImage im, int u, int v, int w, int h, int d, int face, boolean trim) {
        rect(im, u + d, v, w, d, face);
        rect(im, u + d + w, v, w, d, DARK);
        int x = u;
        for (int size : new int[] {d, w, d, w}) {
            rect(im, x, v + d, size, h, face);
            if (trim && size > 1 && h > 1) {
                rect(im, x, v + d, size, 1, STEEL);
                rect(im, x, v + d, 1, h, STEEL);
                rect(im, x + size - 1, v + d + 1, 1, h - 1, DARK);
                rect(im, x + 1, v + d + h - 1, size - 1, 1, DARK);
            }
            x += size;
        }
    }

    static void atlas(boolean armored) throws Exception {
        BufferedImage im = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int shell = armored ? 0x575D65 : IVORY;
        cube(im, 0, 0, 6, 2, 8, DARK, false);    // sole
        cube(im, 0, 12, 6, 2, 4, shell, true);   // broad toe cap
        cube(im, 0, 20, 5, 6, 5, shell, true);   // closed boot upper
        cube(im, 0, 32, 6, 2, 6, DARK, true);    // calf cuff
        cube(im, 30, 0, 1, 6, 1, STEEL, false); // shock pistons
        cube(im, 36, 0, 2, 3, 1, SPRING, false);
        cube(im, 42, 0, 2, 3, 1, SPRING, false);
        cube(im, 48, 0, 2, 2, 2, SPRING, false);
        cube(im, 36, 8, 2, 1, 4, SPRING, false);
        cube(im, 0, 42, 6, 4, 1, shell, true);   // armored shin guard
        // Two front fastening straps and a side-mounted charge indicator.
        rect(im, 5, 26, 5, 1, DARK);
        rect(im, 5, 28, 5, 1, DARK);
        rect(im, 6, 26, 1, 1, STEEL);
        rect(im, 6, 28, 1, 1, STEEL);
        rect(im, 2, 26, 1, 2, CYAN);
        rect(im, 11, 26, 1, 2, CYAN);
        rect(im, 3, 44, 1, 2, CYAN);
        save(im, "textures/entity/equipment/long_fall/" + (armored ? "armored" : "standard") + ".png");
    }

    static void hazmat() throws Exception {
        String path = "textures/entity/equipment/humanoid/hazmat.png";
        BufferedImage im = ImageIO.read(new File(ROOT + path));
        // Extend the existing arm unwrap, retaining the original shoulder pixels and chest/hood exactly.
        for (int y = 25; y < 32; y++) for (int x = 40; x < 56; x++) {
            int faceX = (x - 40) % 4;
            int cloth = faceX == 0 ? 0xD4862E : faceX == 3 ? 0x94501A : 0xC27426;
            int rubber = faceX == 0 ? 0x383C42 : faceX == 3 ? 0x16181B : 0x2B2F34;
            pixel(im, x, y, y >= 28 ? rubber : cloth);
        }
        // Boots use the leg unwrap in the humanoid atlas; leggings use their own untouched atlas.
        for (int y = 26; y < 32; y++) for (int x = 0; x < 16; x++) {
            int edge = x % 4;
            pixel(im, x, y, y == 31 ? 0x16181B : edge == 0 ? 0x383C42 : edge == 3 ? 0x16181B : 0x2B2F34);
        }
        save(im, path);
        BufferedImage item = ImageIO.read(new File(ROOT + "textures/item/steel_boots.png"));
        for (int y = 0; y < item.getHeight(); y++) for (int x = 0; x < item.getWidth(); x++) {
            int c = item.getRGB(x, y);
            if ((c >>> 24) == 0) continue;
            int brightness = ((c >> 16 & 255) * 3 + (c >> 8 & 255) * 6 + (c & 255)) / 10;
            pixel(item, x, y, brightness >= 220 ? 0x575D65 : brightness >= 160 ? 0x383C42 : brightness >= 100 ? 0x2B2F34 : 0x16181B);
        }
        save(item, "textures/item/hazmat_boots.png");
    }
}
