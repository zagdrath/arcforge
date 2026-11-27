"""Draws the fusion tier's GUI backgrounds and their sprites (TEXTURE_STYLE "GUIs": the machine panel's frame, dark recesses
with a 1 px bevel, vanilla-style slots, blue input slots and orange output slots, the arrow).

  - remote_handling_arm (176x186): the FE gauge, a 3x3 blue input grid, the arrow, a 3x3 orange output grid, the status
    line under them, the player inventory at the bottom.
  - fusion_reactor (300x270, on a 512 sheet): the desk's screen across the top (its controls along its bottom row), the
    four input tanks left of the player inventory and the two output tanks and the FE gauge right of it.
  - cryogenic_plant, cryogenic_column, tritium_plant (176x166, the Heavy Water Tower's layout): the FE gauge, the input
    tank, the house screen, the output tanks.

Positions match the screens and menus. Run from the repo root: python tools/fusion_gui_gen.py
"""

import os
import shutil

from PIL import Image

GUI = "src/main/resources/assets/arcforge/textures/gui/"
FILL, DARK, LIGHT, INSET = (49, 49, 49, 255), (14, 14, 14, 255), (92, 92, 92, 255), (31, 31, 31, 255)
BLUE = ((30, 63, 110, 255), (23, 34, 48, 255), (94, 155, 232, 255))
ORANGE = ((14, 14, 14, 255), (42, 31, 22, 255), (200, 110, 34, 255))
ARROW = (26, 26, 26, 255)


def frame_source():
    """A 176x166 machine panel to take the frame's corners and edges from."""
    return Image.open(GUI + "container/induction_furnace.png").convert("RGBA").crop((0, 0, 176, 166))


def panel(w, h):
    """A w x h panel from a machine panel's 4-px corners and edges."""
    source = frame_source()
    out = Image.new("RGBA", (w, h), FILL)
    sw, sh = source.size
    c = 4
    for x in range(w):
        sx = x if x < c else sw - (w - x) if x >= w - c else c
        for y in range(h):
            sy = y if y < c else sh - (h - y) if y >= h - c else c
            if sx == c and sy == c:
                continue
            out.putpixel((x, y), source.getpixel((sx, sy)))
    return out


def inset(img, x0, y0, x1, y1):
    """A recess: dark top-left bevel, light bottom-right, the corners where they meet the panel's."""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            if x == x0 or y == y0:
                colour = DARK
            elif x == x1 or y == y1:
                colour = LIGHT
            else:
                colour = INSET
            img.putpixel((x, y), colour)
    img.putpixel((x1, y0), FILL)
    img.putpixel((x0, y1), FILL)


def slot(img, x, y):
    inset(img, x - 1, y - 1, x + 16, y + 16)


def coloured_slot(img, x, y, colours):
    """A slot in an input (blue) or output (orange) grid: its own top-left and bottom-right ring tones round its fill."""
    top_left, fill, bottom_right = colours
    for px in range(x - 1, x + 17):
        for py in range(y - 1, y + 17):
            if px == x - 1 or py == y - 1:
                colour = top_left
            elif px == x + 16 or py == y + 16:
                colour = bottom_right
            else:
                colour = fill
            img.putpixel((px, py), colour)


def arrow(img, x, y):
    """The progress arrow (21x13), as the PUREX's."""
    rows = ["..............#......", "..............##.....", "..............###....", "..............####...", "###################..",
            "####################.", "#####################", "####################.", "###################..", "..............####...",
            "..............###....", "..............##.....", "..............#......"]
    for dy, row in enumerate(rows):
        for dx, c in enumerate(row):
            if c == "#":
                img.putpixel((x + dx, y + dy), ARROW)


def inventory(img, x, y):
    for row in range(3):
        for col in range(9):
            slot(img, x + col * 18, y + row * 18)
    for col in range(9):
        slot(img, x + col * 18, y + 58)


def copy_sprites(target, source, names):
    folder = GUI + "sprites/container/"
    os.makedirs(folder + target, exist_ok=True)
    for name in names:
        shutil.copy(folder + source + "/" + name + ".png", folder + target + "/" + name + ".png")


def save(img, name, sheet=256):
    out = Image.new("RGBA", (sheet, sheet), (0, 0, 0, 0))
    out.paste(img, (0, 0))
    out.save(GUI + "container/" + name + ".png")


def remote_handling_arm():
    img = panel(176, 186)
    inset(img, 8, 18, 19, 69)
    for row in range(3):
        for col in range(3):
            coloured_slot(img, 30 + col * 18, 18 + row * 18, BLUE)
            coloured_slot(img, 116 + col * 18, 18 + row * 18, ORANGE)
    arrow(img, 89, 38)
    inventory(img, 8, 104)
    save(img, "remote_handling_arm")
    copy_sprites("remote_handling_arm", "waste_compactor", ["energy_bar", "progress", "led_blocked", "led_idle", "led_off", "led_running"])


def fusion_reactor():
    w, h = 300, 270
    img = panel(w, h)
    inset(img, 7, 15, w - 8, 148)
    # The tanks (12x52) and the FE gauge (18x52), level with the player inventory.
    for x in (8, 23, 38, 53, 238, 253):
        inset(img, x - 1, 187, x + 12, 240)
    inset(img, 271, 187, 290, 240)
    inventory(img, 70, 188)
    save(img, "fusion_reactor", 512)
    copy_sprites("fusion_reactor", "fission_reactor", ["button_minus", "button_minus_hover", "button_plus", "button_plus_hover", "page", "page_hover",
                                                       "toggle_on", "toggle_off", "silence", "silence_hover", "tank_gauge", "liquid_fill", "gas_fill",
                                                       "led_blocked", "led_idle", "led_off", "led_running"])
    folder = GUI + "sprites/container/fusion_reactor/"
    # Run (the Reset button's look) and Fast Shutdown (SCRAM's, stretched to 66).
    shutil.copy(GUI + "sprites/container/fission_reactor/reset.png", folder + "run.png")
    shutil.copy(GUI + "sprites/container/fission_reactor/reset_hover.png", folder + "run_hover.png")
    for name, out in (("scram", "shutdown"), ("scram_hover", "shutdown_hover")):
        src = Image.open(GUI + "sprites/container/fission_reactor/" + name + ".png").convert("RGBA")
        width = 66
        wide = Image.new("RGBA", (width, src.height))
        for x in range(width):
            sx = x if x < 2 else src.width - (width - x) if x >= width - 2 else 2 + (x - 2) * (src.width - 4) // (width - 4)
            for y in range(src.height):
                wide.putpixel((x, y), src.getpixel((sx, y)))
        wide.save(folder + out + ".png")
    # The FE gauge: the Diesel Generator's energy bar stripes, 18x52.
    bar = Image.open(GUI + "sprites/container/waste_compactor/energy_bar.png").convert("RGBA")
    energy = Image.new("RGBA", (18, 52))
    for x in range(18):
        for y in range(52):
            energy.putpixel((x, y), bar.getpixel((min(bar.width - 1, x * bar.width // 18), y % bar.height)))
    energy.save(folder + "energy_bar.png")


def small(name, tanks_left, tanks_right, screen):
    """A 176x166 cryogenic or tritium plant (the Heavy Water Tower's layout): the FE gauge, the input tank after it, the
    house screen, the output tanks on the right; every inset spans y 16..69."""
    img = panel(176, 166)
    inset(img, 8, 16, 19, 69)
    for x in tanks_left + tanks_right:
        inset(img, x - 1, 16, x + 12, 69)
    inset(img, screen[0], 16, screen[1], 69)
    inventory(img, 8, 84)
    save(img, name)
    copy_sprites(name, "heavy_water_tower", ["energy_bar", "tank_gauge", "liquid_fill", "progress", "led_blocked", "led_idle", "led_off", "led_running"])
    copy_sprites(name, "fission_reactor", ["gas_fill"])


def main():
    remote_handling_arm()
    fusion_reactor()
    # Cryogenic Plant: the gas in, the screen, the liquid out.
    small("cryogenic_plant", [24], [155], (39, 150))
    # Cryogenic Distillation Column: the feed in, the screen, three products out.
    small("cryogenic_column", [24], [125, 140, 155], (39, 120))
    # Tritium Plant: the exhaust in, the screen, four products out (deuterium, tritium, helium-3, helium).
    small("tritium_plant", [24], [110, 125, 140, 155], (39, 105))

if __name__ == "__main__":
    main()
