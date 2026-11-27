"""The Helium Gas Turbine's textures, derived from the Nuclear Steam Turbine's (docs/TEXTURE_STYLE.md, "Helium Gas Turbine
model"): the same sheet and loose casing with the pale sage turbine enamel moved to a cold steel blue (each pixel keeps
its value, so the grain, plates, ramps and scuffs carry over), and the casing's Hardened tier pips moved to the same
step of the Arcforged ramp.

    python3 tools/helium_turbine_sheet_gen.py
"""
import colorsys
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'arcforge', 'textures', 'block')
HUE = 0.585  # the enamel's new hue (about 211 degrees)
HARDENED = ['#1E0E36', '#52307E', '#7042A4', '#8A55C2', '#A476DA', '#CEAAF2']
ARCFORGED = ['#0A2A38', '#1E6A88', '#2E96B6', '#40B6D8', '#6AD2EE', '#B4F2FF']


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def is_enamel(p):
    r, g, b = p[:3]
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return s > 0.015 and 0.22 < h < 0.47


def blue(p):
    r, g, b = p[:3]
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    s = min(0.25, s * 1.5 + 0.06)
    r, g, b = colorsys.hsv_to_rgb(HUE, s, v)
    return (round(r * 255), round(g * 255), round(b * 255)) + tuple(p[3:])


def enamel_region(x, y):
    # Rows 234-236 (the enamel's edge rows), columns 122-124 of rows 128-191 (its edge columns), and rows 256 down.
    return 234 <= y <= 236 or 122 <= x <= 124 and 128 <= y < 192 or y >= 256


def sheet():
    im = Image.open(os.path.join(ROOT, 'nuclear_steam_turbine', 'turbine.png')).convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            if enamel_region(x, y) and px[x, y][3] and is_enamel(px[x, y]):
                px[x, y] = blue(px[x, y])
    im.save(os.path.join(ROOT, 'helium_gas_turbine', 'turbine.png'))


def casing():
    im = Image.open(os.path.join(ROOT, 'nuclear_steam_turbine', 'casing.png')).convert('RGBA')
    px = im.load()
    tiers = {rgb(a): rgb(b) for a, b in zip(HARDENED, ARCFORGED)}
    for y in range(im.height):
        for x in range(im.width):
            p = px[x, y]
            if p[:3] in tiers:
                px[x, y] = tiers[p[:3]] + (p[3],)
            elif p[3] and is_enamel(p):
                px[x, y] = blue(p)
    im.save(os.path.join(ROOT, 'helium_gas_turbine', 'casing.png'))


if __name__ == '__main__':
    sheet()
    casing()
