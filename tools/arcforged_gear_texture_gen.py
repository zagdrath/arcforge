"""Arcforged tools and armour textures (docs/TEXTURE_STYLE.md, "Arcforged tools and armour"), made from the steel ones so
they keep their exact shapes, outlines and shading layout:

- item/arcforged_<piece>.png: the steel sprite with its steel ramp moved, tone for tone, onto the darker Arcforged steel
  ramp (the wooden handles kept exactly) and a 1 px Arcite inlay (INLAY) in the dull Arcforged cyan, shaded by the
  steel under it (DULL_TONES): along the blade edge or head of each tool, the fuller of the sword, the seams of each
  armour piece;
- item/arcforged_<piece>_glow.png: the inlay alone, a step brighter (LIT_TONES), the emissive layer the item models add
  while the piece has charge;
- entity/equipment/{humanoid,humanoid_leggings}/arcforged.png: the steel armour's two worn layers on the same ramp, the
  visor, chest and shoulder seams (SEAMS) in the shaded dull cyan; entity/arcforged_glow.png: those seams alone, lit and
  partly transparent with a faint spill either side (drawn translucent and full-bright over charged armour by
  ArcforgedGlowLayer);
- item/arcforged_upgrade_smithing_template.png: drawn here, a smithing template's plaque in dark Arcforged steel with a
  shaded cyan glyph.

Run from the repository root: python3 tools/arcforged_gear_texture_gen.py
"""
import os

from PIL import Image

T = 'src/main/resources/assets/arcforge/textures/'

# The steel item ramp (outline, then dark to light) onto the Arcforged steel ramp: darker, a little bluer.
RAMP = {
    0x16181B: 0x0C0D0F,
    0x3C3F45: 0x1C2129,
    0x44484E: 0x222830,
    0x565A61: 0x2C333D,
    0x6B7078: 0x39424E,
    0x7D8289: 0x46515F,
    0x9CA1A8: 0x5A6776,
    0xC4C9CF: 0x7E8C9B,
}
# The inlay's tones, steps of the Arcforged tier palette. Each inlay pixel takes the step matching the steel tone it is
# painted over (STEPS: the steel ramp's dark, middle and light thirds), so the line keeps the steel's own shading rather
# than reading as one flat colour. Dull (painted into the sprite): the palette's three darkest steps, mixed DULL_MIX
# toward the cyan over the Arcforged steel beneath, so the line sits in the metal. Lit (the glow layer, while charged):
# one step up, topping out at the bevel tone #40B6D8 rather than the palette's brightest, so the charge reads as a soft
# light in the seam and not a neon stripe.
DULL_TONES = (0x0A2A38, 0x1E6A88, 0x2E96B6)
LIT_TONES = (0x1E6A88, 0x2E96B6, 0x40B6D8)
DULL_MIX = 0.7
STEEL_ORDER = list(RAMP)  # outline, then dark to light
# Worn glow alpha (drawn translucent): the seam itself, the pixel at each end of a run, and the 1 px spill above and
# below a run (the light bleeding onto the plate round it).
GLOW_ALPHA, GLOW_END_ALPHA, GLOW_SPILL_ALPHA = 215, 150, 55
GLOW_SPILL = 0x1E6A88

# Each sprite's inlay, (x, y) pixels: a 1 px line on the steel.
INLAY = {
    'sword': [(13, 2), (12, 3), (11, 4), (10, 5), (9, 6), (8, 7)],
    'pickaxe': [(7, 3), (8, 3), (9, 3), (10, 3), (11, 4), (12, 5), (13, 6), (13, 7), (13, 8), (13, 9)],
    'axe': [(9, 2), (10, 2), (8, 3), (7, 4), (7, 5)],
    'shovel': [(11, 3), (12, 3), (13, 4), (13, 5), (12, 6)],
    'hoe': [(7, 2), (8, 2), (9, 2), (10, 3)],
    'hammer': [(10, 2), (11, 3), (12, 4), (13, 5), (14, 6)],
    'excavator': [(11, 1), (12, 2), (13, 3), (14, 4), (13, 5)],
    'helmet': [(x, 6) for x in range(5, 11)],
    'chestplate': [(x, 9) for x in range(5, 11)] + [(3, 4), (4, 4), (11, 4), (12, 4)],
    'leggings': [(x, 4) for x in range(5, 11)],
    'boots': [(4, 6), (5, 6), (10, 6), (11, 6)],
}

# The worn humanoid layer's seams: the visor (head front), across the chest (body front) and round the shoulder pads
# (every face of both arms' pads; the left arm mirrors the right's UV).
# Listed as the face-wide segments the light runs along (the shoulder line is four faces of four).
SEAM_SEGMENTS = [[(x, 10) for x in range(9, 15)], [(x, 24) for x in range(21, 27)]] + \
    [[(x, 23) for x in range(x0, x0 + 4)] for x0 in range(40, 56, 4)]
SEAMS = [p for segment in SEAM_SEGMENTS for p in segment]


def segment_light(segments):
    """How far each seam pixel's tone moves up or down the cyan steps: the plate under a seam is often one flat tone, so
    each segment carries its own light, a highlight a little left of centre falling to shadow at its right end (as the
    plates are lit from the upper left), so the seam reads as a rounded inlay, not a flat stripe."""
    light = {}
    for segment in segments:
        n = len(segment)
        for i, p in enumerate(segment):
            u = (i + 0.5) / n
            light[p] = 0.6 * (1 - abs(u - 0.4) * 2.2) - 0.2
    return light


def item_light(mask):
    """segment_light over a sprite inlay's horizontal runs of four or more (the armour pieces' seams)."""
    cells = sorted(set(mask), key=lambda p: (p[1], p[0]))
    segments, run = [], []
    for p in cells:
        if run and (p[1] != run[-1][1] or p[0] != run[-1][0] + 1):
            segments.append(run)
            run = []
        run.append(p)
    if run:
        segments.append(run)
    return segment_light([r for r in segments if len(r) >= 4])


def rgb(c):
    return (c >> 16) & 255, (c >> 8) & 255, c & 255


def shade(tones, colour, bias=0.0):
    """The inlay tone for a steel tone: its place on the steel ramp (dark to light; off the ramp, the middle) read along
    the three tones, so every step of the steel gives a different cyan."""
    i = STEEL_ORDER.index(colour) if colour in RAMP else 4
    t = max(0, i - 1) / (len(STEEL_ORDER) - 2) * (len(tones) - 1)
    t = min(max(t + bias, 0), len(tones) - 1)
    lo = min(int(t), len(tones) - 2)
    return mix(rgb(tones[lo]), rgb(tones[lo + 1]), t - lo)


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def runs(mask):
    """The mask's pixels with, for each, whether it ends a horizontal or diagonal run (no masked neighbour on one side)."""
    cells = set(mask)
    out = []
    for x, y in mask:
        left = any((x - 1, y + d) in cells for d in (-1, 0, 1))
        right = any((x + 1, y + d) in cells for d in (-1, 0, 1))
        vert = (x, y - 1) in cells or (x, y + 1) in cells
        out.append(((x, y), not vert and not (left and right)))
    return out


def recolour(im, mask, light=None):
    """The image on the Arcforged ramp (pixels off the steel ramp, the handles, kept), the mask in the dull inlay, each
    pixel shaded by the steel tone under it and mixed into the Arcforged steel; a run's end pixels a little fainter."""
    out = im.copy()
    px = out.load()
    src = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            c = (r << 16) | (g << 8) | b
            if c in RAMP:
                px[x, y] = rgb(RAMP[c]) + (a,)
    for (x, y), end in runs(mask):
        r, g, b, a = src[x, y]
        if not a:
            continue
        steel = (r << 16) | (g << 8) | b
        under = px[x, y][:3]
        tone = shade(DULL_TONES, steel, (light or {}).get((x, y), 0.0))
        px[x, y] = mix(under, tone, DULL_MIX * (0.75 if end else 1.0)) + (255,)
    return out


def glow(size, mask, base, translucent, light=None):
    """The lit inlay alone: each pixel one step above its dull tone. Item glow layers are cut out, so opaque; the worn
    glow is drawn translucent, its run ends fainter and a faint spill on the rows above and below each seam."""
    out = Image.new('RGBA', size, (0, 0, 0, 0))
    src = base.load()
    cells = set(mask)
    if translucent:
        for x, y in mask:
            for dy in (-1, 1):
                q = (x, y + dy)
                if q not in cells and 0 <= q[1] < size[1] and src[q][3]:
                    out.putpixel(q, rgb(GLOW_SPILL) + (GLOW_SPILL_ALPHA,))
    for (x, y), end in runs(mask):
        r, g, b, a = src[x, y]
        if not a:
            continue
        tone = shade(LIT_TONES, (r << 16) | (g << 8) | b, (light or {}).get((x, y), 0.0))
        alpha = 255 if not translucent else GLOW_END_ALPHA if end else GLOW_ALPHA
        out.putpixel((x, y), tone + (alpha,))
    return out


def template():
    """A smithing template's plaque (16x16): a slab narrowing to a bevelled top, dark Arcforged steel with a 1 px bevel
    lit top/left and dark bottom/right, an inset panel and a cyan Arcite glyph (an arc's zigzag)."""
    o, d, s, f, m, l = 0x0C0D0F, 0x1C2129, 0x2C333D, 0x39424E, 0x46515F, 0x7E8C9B
    rows = [
        '................',
        '....oooooooo....',
        '...ollllllllo...',
        '..olmmmmmmmmso..',
        '..olmffffffmso..',
        '..olmf..HHfmso..',
        '..olmf.HG.fmso..',
        '..olmfHGGGfmso..',
        '..olmf..GGfmso..',
        '..olmf.GG.fmso..',
        '..olmfGG..fmso..',
        '..olmffffffmso..',
        '..olmmmmmmmmso..',
        '...ossssssssd...',
        '....oddddddo....',
        '................',
    ]
    tones = {'o': o, 'd': d, 's': s, 'f': f, 'm': m, 'l': l, '.': None, 'G': 0x2E96B6, 'H': 0x40B6D8}
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.' and 5 <= x <= 10 and 5 <= y <= 10:
                im.putpixel((x, y), rgb(d) + (255,))
            elif tones.get(ch) is not None:
                im.putpixel((x, y), rgb(tones[ch]) + (255,))
    return im


def main():
    for piece, mask in INLAY.items():
        steel = Image.open(T + 'item/steel_' + piece + '.png').convert('RGBA')
        light = item_light(mask)
        recolour(steel, mask, light).save(T + 'item/arcforged_' + piece + '.png')
        glow(steel.size, mask, steel, False, light).save(T + 'item/arcforged_' + piece + '_glow.png')
    os.makedirs(T + 'entity/equipment/humanoid', exist_ok=True)
    os.makedirs(T + 'entity/equipment/humanoid_leggings', exist_ok=True)
    body = Image.open(T + 'entity/equipment/humanoid/steel.png').convert('RGBA')
    recolour(body, SEAMS, segment_light(SEAM_SEGMENTS)).save(T + 'entity/equipment/humanoid/arcforged.png')
    legs = Image.open(T + 'entity/equipment/humanoid_leggings/steel.png').convert('RGBA')
    recolour(legs, []).save(T + 'entity/equipment/humanoid_leggings/arcforged.png')
    glow(body.size, SEAMS, body, True, segment_light(SEAM_SEGMENTS)).save(T + 'entity/arcforged_glow.png')
    template().save(T + 'item/arcforged_upgrade_smithing_template.png')


if __name__ == '__main__':
    main()
