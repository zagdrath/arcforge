"""The Fusion Reactor model's sheet (textures/block/fusion/tokamak_model.png): the refinery tower's large-model sheet
(textures/block/distillation_array/refinery.png, whose plating rows tools/LargeModelTextureGen.java paints) with the
tokamak's own tiles in its free bottom row. Rerun after the refinery sheet changes. Run from the repo root.

  - (32, 240) First Wall armour, 16x16: four castellated tungsten tiles 8x8, each lit on its top and left edge and dark
    on its bottom and right, two close tones of grain each, a 1 px dark gap between them (block-sized tile for the
    plasma chamber's walls, seen through the viewports);
  - (48, 240) Divertor targets, 16x16: tungsten monoblocks in four slats 4 px wide, each lit down its left edge and dark
    down its right, a dark joint every 8 px down them;
  - (64, 240) plasma, 16x16: neutral greys (tinted in code), lightest along the middle rows and fainter toward the top
    and bottom edge, two faint streaks running along it.
"""

from PIL import Image

T = "src/main/resources/assets/arcforge/textures/block/"


def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def first_wall(img, x0, y0):
    gap, lit, dark = hexc("#1E2023"), hexc("#7E858E"), hexc("#34383D")
    tones = [hexc("#5C626A"), hexc("#565C64")]
    for ty in range(2):
        for tx in range(2):
            for y in range(8):
                for x in range(8):
                    px, py = x0 + tx * 8 + x, y0 + ty * 8 + y
                    if x == 7 or y == 7:
                        c = gap
                    elif x == 6 or y == 6:
                        c = dark
                    elif x == 0 or y == 0:
                        c = lit
                    else:
                        # A brushed grain: rows alternate in pairs, offset per tile.
                        c = tones[((y + tx + ty) // 2 + (x // 3)) % 2]
                    img.putpixel((px, py), c)


def divertor(img, x0, y0):
    joint, lit, dark = hexc("#1A1C1F"), hexc("#6A7078"), hexc("#2B2F34")
    tones = [hexc("#4A4F56"), hexc("#454A51")]
    for y in range(16):
        for x in range(16):
            sx, sy = x % 4, y % 8
            if sy == 7:
                c = joint
            elif sx == 0:
                c = lit
            elif sx == 3:
                c = dark
            else:
                c = tones[(y // 2 + x // 4) % 2]
            img.putpixel((x0 + x, y0 + y), c)


def plasma(img, x0, y0):
    rows = [176, 190, 202, 214, 224, 232, 240, 246, 246, 240, 232, 224, 214, 202, 190, 176]
    for y in range(16):
        for x in range(16):
            v = rows[y]
            # Two faint streaks along it (rows 5 and 10), a step brighter.
            if y in (5, 10):
                v = min(255, v + 8)
            img.putpixel((x0 + x, y0 + y), (v, v, v, 255))


def main():
    img = Image.open(T + "distillation_array/refinery.png").convert("RGBA")
    first_wall(img, 32, 240)
    divertor(img, 48, 240)
    plasma(img, 64, 240)
    img.save(T + "fusion/tokamak_model.png")


if __name__ == "__main__":
    main()
