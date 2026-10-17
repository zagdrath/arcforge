"""Draws the Fission Reactor's GUI background (300x288) and its 36-px RESET/SCRAM buttons.

The frame is the 176-px machine panel stretched (its 4-px corners and edges kept), the desk screen an inset across the
top, under it the rod input slots in a 3x3 grid over the player inventory's left end and the output slots in one over
its right end, the coolant tanks either side of them, and the player inventory at the bottom. Positions match FissionReactorScreen and FissionReactorMenu. Run from the repo root: python tools/fission_reactor_gui_gen.py
"""

from PIL import Image

GUI = "src/main/resources/assets/arcforge/textures/gui/"
W, H = 300, 288
FILL, DARK, LIGHT, INSET = (49, 49, 49, 255), (14, 14, 14, 255), (92, 92, 92, 255), (31, 31, 31, 255)
SCREEN = (7, 15, W - 8, 118)  # dark top-left corner, light bottom-right corner
INVENTORY_X, INVENTORY_Y = 70, 206
SLOT_X, SLOT_Y, OUTPUT_X = INVENTORY_X, 134, INVENTORY_X + 6 * 18
# The four 12x52 tanks either side of the rod grids: intake and vessel on the left, hot leg and steam on the right.
TANK_XS, TANK_Y, TANK_W, TANK_H = (20, 40, 248, 268), 134, 12, 52


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


def inset(img, x0, y0, x1, y1):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            if x == x0 or y == y0:
                colour = DARK
            elif x == x1 or y == y1:
                colour = LIGHT
            else:
                colour = INSET
            img.putpixel((x, y), colour)
    # The corners where the bevels meet stay the panel's.
    img.putpixel((x1, y0), FILL)
    img.putpixel((x0, y1), FILL)


def slot(img, x, y):
    inset(img, x - 1, y - 1, x + 16, y + 16)


def main():
    source = Image.open(GUI + "container/fission_reactor.png").convert("RGBA")
    # Only the frame is read: the old 176x254 panel on a 256 sheet, the 300x216 one on a 512x256 sheet, or this one.
    source = source.crop((0, 0, 176, 254) if source.width == 256 else (0, 0, W, 216) if source.height == 256 else (0, 0, W, H))
    img = panel(source)
    inset(img, *SCREEN)
    for row in range(3):
        for col in range(3):
            slot(img, SLOT_X + col * 18, SLOT_Y + row * 18)
            slot(img, OUTPUT_X + col * 18, SLOT_Y + row * 18)
    for row in range(3):
        for col in range(9):
            slot(img, INVENTORY_X + col * 18, INVENTORY_Y + row * 18)
    for col in range(9):
        slot(img, INVENTORY_X + col * 18, INVENTORY_Y + 58)
    for x in TANK_XS:
        inset(img, x - 1, TANK_Y - 1, x + TANK_W, TANK_Y + TANK_H)
    sheet = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    sheet.paste(img, (0, 0))
    sheet.save(GUI + "container/fission_reactor.png")

    # The wide buttons: the 28-px sprites with their middle stretched to 36.
    for name in ("reset", "reset_hover", "scram", "scram_hover"):
        path = GUI + "sprites/container/fission_reactor/" + name + ".png"
        src = Image.open(path).convert("RGBA")
        if src.width == 36:
            continue
        out = Image.new("RGBA", (36, src.height))
        for x in range(36):
            sx = x if x < 2 else src.width - (36 - x) if x >= 34 else 2 + (x - 2) * (src.width - 4) // 32
            for y in range(src.height):
                out.putpixel((x, y), src.getpixel((sx, y)))
        out.save(path)


if __name__ == "__main__":
    main()
