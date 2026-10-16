"""Draws the Distillation Array's GUI background (232x172) and its 8-px product tank sprites.

The frame is the machine panel stretched (its 4-px corners and edges kept); the feed and steam tanks, the screen, the
Pitch slot (an orange slot under the screen, clear of the inventory label), seven product tanks 8 px wide 3 px apart
and the player inventory centred. Positions match DistillationArrayScreen and DistillationArrayMenu.
Run from the repo root: python tools/distillation_array_gui_gen.py
"""

from PIL import Image

GUI = "src/main/resources/assets/arcforge/textures/gui/"
SPRITES = GUI + "sprites/container/distillation_array/"
W, H = 232, 172
FILL, DARK, LIGHT, INSET = (49, 49, 49, 255), (14, 14, 14, 255), (92, 92, 92, 255), (31, 31, 31, 255)
# The orange (fuel/by-product) slot: dark lip, floor, light lip.
ORANGE_DARK, ORANGE_FLOOR, ORANGE_LIGHT = (122, 66, 16, 255), (42, 31, 22, 255), (240, 168, 96, 255)

TANK_Y, TANK_H = 19, 50
FEED_X, STEAM_X, TANK_W = 9, 25, 12
SCREEN = (40, 18, 133, 52)
PITCH_X, PITCH_Y = 41, 55
PRODUCT_X, PRODUCT_PITCH, PRODUCT_W, PRODUCTS = 137, 13, 8, 7
INVENTORY_X, INVENTORY_Y = (W - 176) // 2 + 8, H - 82


def panel(source):
    """A W x H panel from a machine panel's 4-px corners and edges."""
    out = Image.new("RGBA", (W, H), FILL)
    sw, sh = source.size
    c = 4
    for x in range(W):
        sx = x if x < c else sw - (W - x) if x >= W - c else c
        for y in range(H):
            sy = y if y < c else sh - (H - y) if y >= H - c else c
            if sx == c and sy == c:
                continue
            out.putpixel((x, y), source.getpixel((sx, sy)))
    return out


def inset(img, x0, y0, x1, y1, dark=DARK, floor=INSET, light=LIGHT):
    """A recess from (x0, y0) to (x1, y1) inclusive: dark top/left lip, light bottom/right, the two odd corners floor."""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            if (x, y) in ((x1, y0), (x0, y1)):
                colour = floor
            elif x == x0 or y == y0:
                colour = dark
            elif x == x1 or y == y1:
                colour = light
            else:
                colour = floor
            img.putpixel((x, y), colour)


def tank(img, x, w):
    inset(img, x - 1, TANK_Y - 1, x + w, TANK_Y + TANK_H)


def slot(img, x, y, **colours):
    inset(img, x - 1, y - 1, x + 16, y + 16, **colours)


def widen(path, width, pad_left):
    """Widens a 6-px product sprite to width: empty columns on the left (pad_left) or its middle column repeated."""
    src = Image.open(path).convert("RGBA")
    if src.width == width:
        return
    out = Image.new("RGBA", (width, src.height), (0, 0, 0, 0))
    extra = width - src.width
    if pad_left:
        out.paste(src, (extra, 0))
    else:
        for x in range(width):
            sx = x if x < 3 else x - extra if x >= 3 + extra else 2
            for y in range(src.height):
                out.putpixel((x, y), src.getpixel((sx, y)))
    out.save(path)


def locked_sprite():
    """A locked product tank: the 12-px locked sprite's columns 2..9, its hatching and its whole padlock (x 1..6)."""
    src = Image.open(SPRITES + "locked.png").convert("RGBA")
    src.crop((2, 0, 2 + PRODUCT_W, src.height)).save(SPRITES + "product_locked.png")


def main():
    source = Image.open(GUI + "container/distillation_array.png").convert("RGBA")
    box = source.getbbox()
    source = source.crop((0, 0, box[2], box[3]))
    img = panel(source)
    tank(img, FEED_X, TANK_W)
    tank(img, STEAM_X, TANK_W)
    inset(img, *SCREEN)
    slot(img, PITCH_X, PITCH_Y, dark=ORANGE_DARK, floor=ORANGE_FLOOR, light=ORANGE_LIGHT)
    for i in range(PRODUCTS):
        tank(img, PRODUCT_X + i * PRODUCT_PITCH, PRODUCT_W)
    for row in range(3):
        for col in range(9):
            slot(img, INVENTORY_X + col * 18, INVENTORY_Y + row * 18)
    for col in range(9):
        slot(img, INVENTORY_X + col * 18, INVENTORY_Y + 58)
    sheet = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    sheet.paste(img, (0, 0))
    sheet.save(GUI + "container/distillation_array.png")

    widen(SPRITES + "product_gauge.png", PRODUCT_W, True)
    widen(SPRITES + "product_fill.png", PRODUCT_W, False)
    locked_sprite()


if __name__ == "__main__":
    main()
