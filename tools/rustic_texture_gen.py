"""Regenerates the rustic farming machines' texture sheets and UVs (docs/TEXTURE_STYLE.md, "Rustic farming machines").

Every element of each model is given a material below; every face is then repainted from scratch on that material's
ramp (flat tones, 1 px bevels, light from the top left) and packed into a fresh 64x64 sheet, identical faces sharing one
region. The Planter's seed tray uses the Compost Bin heap texture and the Fertilizer Spreader's top uses Loam.

It also carries three geometry fixes, applied every run (they're idempotent): the Planter's and Harvester's corner
angles stand 0.1 px proud of the iron band (they were coplanar and z-fought), the Scarecrow's straw hands are wider than
the arm stick they sit on, and the Spreader's legs reach its hopper.

Run from the repo root: python tools/rustic_texture_gen.py
"""
import json
import math
import os

from PIL import Image

ASSETS = 'src/main/resources/assets/arcforge'
MODELS = ASSETS + '/models/block/'
TEX = ASSETS + '/textures/block/'

# --- Ramps ---
def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)

WOOD = [rgb(c) for c in ('#120C07', '#20160D', '#2C1C11', '#3A2616', '#47301C', '#553A22', '#634429', '#725033')]
STEEL = {k: rgb(v) for k, v in dict(deep='#16181B', dark='#2B2F34', shade='#383C42', face2='#40454C', face='#474C53',
                                    mid='#575D65', lit='#727982', spec='#959DA6').items()}
COPPER = [rgb(c) for c in ('#3C1C0C', '#8A4822', '#AE6632', '#C98244', '#E0A062', '#F6C898')]
LINEN = [rgb(c) for c in ('#4A3934', '#9D7860', '#D2B192', '#EDCFB4', '#FDE8D0')]
SACK = rgb('#B89574')
STRAW = [rgb(c) for c in ('#5C4318', '#8A6A26', '#B48E36', '#D4AE4A', '#ECD072')]


def new(w, h, c=(0, 0, 0, 0)):
    return Image.new('RGBA', (max(1, w), max(1, h)), c)


def bevel(im, lit, dark, corner=None):
    """1 px lit top/left, 1 px dark bottom/right, on faces at least 3 px both ways."""
    w, h = im.size
    if w < 3 or h < 3:
        return
    for x in range(w):
        im.putpixel((x, 0), lit)
        im.putpixel((x, h - 1), dark)
    for y in range(h):
        im.putpixel((0, y), lit)
        im.putpixel((w - 1, y), dark)
    if corner:
        im.putpixel((w - 1, 0), corner)
        im.putpixel((0, h - 1), corner)


def thin(im, lit, face, dark):
    """A strip 1 or 2 px across: lit over dark (horizontal), or lit then dark (vertical)."""
    w, h = im.size
    for y in range(h):
        for x in range(w):
            if h <= 2 and w > h:
                c = lit if y == 0 else dark
            elif w <= 2 and h > w:
                c = lit if x == 0 else dark
            else:
                c = face
            im.putpixel((x, y), c)
    if w == 1 and h == 1:
        im.putpixel((0, 0), face)


# --- Materials ---
def planks(w, h, along_y=False):
    """Treated planks: boards 4 px tall (a gap row between), lit top row, lit left and dark right edge, a nail at each
    end of a board 9 px or wider."""
    if along_y:
        return planks(h, w).transpose(Image.Transpose.ROTATE_90)
    im = new(w, h, WOOD[6])
    if w < 3 or h < 3:
        thin(im, WOOD[7], WOOD[6], WOOD[4])
        return im
    y = 0
    board = 0
    while y < h:
        tone = WOOD[6] if board % 2 == 0 else WOOD[5]
        for dy in range(4):
            yy = y + dy
            if yy >= h:
                break
            for x in range(w):
                if dy == 3 and yy != h - 1:
                    c = WOOD[1]  # the gap between boards
                elif dy == 0:
                    c = WOOD[7]
                else:
                    c = tone
                im.putpixel((x, yy), c)
            if dy != 3 or yy == h - 1:
                im.putpixel((0, yy), WOOD[7] if dy < 3 else tone)
                im.putpixel((w - 1, yy), WOOD[3])
        if w >= 9 and y + 1 < h:
            for nx in (1, w - 2):
                im.putpixel((nx, y + 1), STEEL['spec'])
                if y + 2 < h:
                    im.putpixel((nx, y + 2), STEEL['dark'])
        y += 4
        board += 1
    im.putpixel((w - 1, h - 1), WOOD[2])
    return im


def log(w, h, dark=False):
    """Posts, legs and rims: two lengthwise tones, lit edge on top/left, dark on bottom/right."""
    lit, a, b, shade = (WOOD[5], WOOD[4], WOOD[3], WOOD[2]) if dark else (WOOD[7], WOOD[6], WOOD[5], WOOD[3])
    im = new(w, h)
    horizontal = w >= h
    for y in range(h):
        for x in range(w):
            if horizontal:
                c = lit if y == 0 else shade if y == h - 1 and h > 2 else a if y < max(1, h // 2) + (0 if h > 2 else 1) else b
            else:
                c = lit if x == 0 else shade if x == w - 1 and w > 2 else a if x < max(1, w // 2) + (0 if w > 2 else 1) else b
            im.putpixel((x, y), c)
    return im


def end_grain(w, h, dark=False):
    """The end of a post: a ring and a lit core pixel."""
    ring, core, lit = (WOOD[3], WOOD[4], WOOD[5]) if dark else (WOOD[4], WOOD[5], WOOD[7])
    im = new(w, h, core)
    if w >= 3 and h >= 3:
        for x in range(w):
            im.putpixel((x, 0), ring)
            im.putpixel((x, h - 1), ring)
        for y in range(h):
            im.putpixel((0, y), ring)
            im.putpixel((w - 1, y), ring)
    im.putpixel((0, 0), lit)
    return im


def steel(w, h, rivets=True):
    im = new(w, h, STEEL['face'])
    if w < 3 or h < 3:
        thin(im, STEEL['lit'], STEEL['mid'], STEEL['dark'])
        return im
    split = max(1, round(w * 10 / 16))
    for y in range(h):
        for x in range(split, w):
            im.putpixel((x, y), STEEL['face2'])
    bevel(im, STEEL['lit'], STEEL['dark'], STEEL['shade'])
    if rivets and w >= 9 and h >= 5:
        for rx, ry in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
            im.putpixel((rx, ry), STEEL['spec'])
    return im


def copper(w, h):
    im = new(w, h, COPPER[3])
    if w < 3 or h < 3:
        thin(im, COPPER[4], COPPER[3], COPPER[1])
        return im
    split = max(1, round(w * 10 / 16))
    for y in range(h):
        for x in range(split, w):
            im.putpixel((x, y), COPPER[2])
    bevel(im, COPPER[4], COPPER[1], COPPER[2])
    im.putpixel((1, 1), COPPER[5])
    return im


def linen(w, h, seam=True, patch=False):
    im = new(w, h, LINEN[2])
    if w < 3 or h < 3:
        thin(im, LINEN[3], LINEN[2], LINEN[1])
        return im
    bevel(im, LINEN[3], LINEN[1], LINEN[2])
    if seam and w >= 6:
        sx = w // 2
        for y in range(1, h - 1, 2):
            im.putpixel((sx, y), LINEN[1])
    if patch and w >= 7 and h >= 7:
        px, py = 1 + (w - 1) // 2 + 1, h - 5
        for y in range(py, py + 3):
            for x in range(px, px + 3):
                im.putpixel((x, y), LINEN[4] if (x + y) % 2 == 0 and (x == px or y == py) else LINEN[3])
        for x in range(px - 1, px + 4):
            im.putpixel((x, py - 1), LINEN[1]) if (x - px) % 2 == 0 else None
    return im


def sack(w, h, face=False):
    im = new(w, h, SACK)
    bevel(im, LINEN[2], LINEN[1], SACK)
    if face and w >= 6 and h >= 6:
        # Stitched eyes (a slash each, mirrored) and a row of mouth stitches.
        for x, y in ((1, 1), (2, 2), (w - 2, 1), (w - 3, 2)):
            im.putpixel((x, y), LINEN[0])
        for mx in range(1, w - 1):
            im.putpixel((mx, h - 2), LINEN[0] if mx % 2 == 1 else LINEN[1])
    return im


def straw(w, h):
    """Straw in 3 px strands, running down the face."""
    im = new(w, h, STRAW[2])
    for x in range(w):
        offset = (x * 2) % 3
        for y in range(h):
            seg = (y + offset) // 3
            im.putpixel((x, y), STRAW[3] if seg % 2 == 0 else STRAW[2])
        if x % 3 == 2:
            for y in range(h):
                if (y + offset) % 3 == 2:
                    im.putpixel((x, y), STRAW[1])
    if w >= 3 and h >= 3:
        for x in range(w):
            im.putpixel((x, 0), STRAW[4])
            im.putpixel((x, h - 1), STRAW[1])
    return im


def tile(src, w, h):
    s = Image.open(TEX + src + '.png').convert('RGBA')
    im = new(w, h)
    for y in range(h):
        for x in range(w):
            im.putpixel((x, y), s.getpixel((x % 16, y % 16)))
    return im


# --- Special faces ---
def drum_end(w, h):
    """A copper seed drum's end: copper with an iron hub."""
    im = copper(w, h)
    cx, cy = w // 2 - 1, h // 2 - 1
    for dx in range(2):
        for dy in range(2):
            im.putpixel((cx + dx, cy + dy), STEEL['mid'])
    im.putpixel((cx, cy), STEEL['spec'])
    im.putpixel((cx + 1, cy + 1), STEEL['dark'])
    return im


def chute(w, h):
    """The Planter's seed chute: an iron hood with the dark mouth low in the middle."""
    im = steel(w, h, rivets=False)
    for y in range(h - 3, h - 1):
        for x in range(2, w - 2):
            im.putpixel((x, y), STEEL['deep'])
    return im


def reel_plate(w, h):
    im = steel(w, h, rivets=False)
    cx, cy = w // 2, h // 2
    im.putpixel((cx, cy), STEEL['spec'])
    im.putpixel((cx, cy - 1), STEEL['mid'])
    im.putpixel((cx, cy + 1), STEEL['dark'])
    return im


def cutter_front(w, h):
    """The cutter bar seen from the front: a row of teeth over the bar."""
    im = new(w, h, STEEL['face'])
    for x in range(w):
        im.putpixel((x, 0), STEEL['spec'] if x % 2 == 0 else STEEL['deep'])
        for y in range(1, h):
            im.putpixel((x, y), STEEL['dark'] if y == h - 1 else STEEL['mid'])
    return im


def cutter_top(w, h):
    """The cutter bar from above: teeth along the front (north) edge, the bar behind."""
    im = steel(w, h, rivets=False)
    for x in range(w):
        im.putpixel((x, 0), STEEL['spec'] if x % 2 == 0 else STEEL['deep'])
        im.putpixel((x, 1), STEEL['lit'] if x % 2 == 0 else STEEL['dark'])
    return im


def spinner(w, h):
    im = steel(w, h, rivets=False)
    cx, cy = w // 2 - 1, h // 2 - 1
    for dx in range(2):
        for dy in range(2):
            im.putpixel((cx + dx, cy + dy), STEEL['mid'])
    im.putpixel((cx, cy), STEEL['spec'])
    return im


def spray_head(w, h):
    """The Sprinkler head from above: copper with a ring of spray holes."""
    im = copper(w, h)
    for x, y in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3), (w // 2 - 1, 1), (w // 2, h - 2), (1, h // 2), (w - 2, h // 2 - 1)):
        if 0 < x < w - 1 and 0 < y < h - 1:
            im.putpixel((x, y), COPPER[0])
    return im


def hat_brim(w, h):
    """The straw hat's brim from above: straw round a darker crown footprint."""
    im = straw(w, h)
    return im


# --- The models: each element's material (and per-face overrides) ---
# Materials: planks, log, leg, rim, steel, copper, linen, sack, straw, compost, loam, and callables for special faces.
M = {
    'planter': {
        'tex': 'planter',
        'elements': {0: 'leg', 1: 'leg', 2: 'leg', 3: 'leg', 4: 'planks', 5: 'compost', 6: 'rim', 7: 'rim', 8: 'rim',
                     9: 'rim', 10: 'steel', 11: 'steel', 12: 'steel', 13: 'steel', 14: 'steel', 15: 'copper', 16: 'copper',
                     17: 'steel', 18: 'steel', 19: 'compost'},
        'faces': {(15, 'east'): drum_end, (15, 'west'): drum_end, (16, 'east'): drum_end, (16, 'west'): drum_end,
                  (17, 'north'): chute},
    },
    'harvester': {
        'tex': 'harvester',
        'elements': {0: 'leg', 1: 'leg', 2: 'leg', 3: 'leg', 4: 'planks', 5: 'planks_dark', 6: 'rim', 7: 'rim', 8: 'rim',
                     9: 'rim', 10: 'steel', 11: 'steel', 12: 'steel', 13: 'steel', 14: 'steel', 15: 'steel', 16: 'steel',
                     17: 'log', 18: 'planks', 19: 'planks', 20: 'planks', 21: 'planks', 22: 'steel', 23: 'copper'},
        'faces': {(15, 'east'): reel_plate, (15, 'west'): reel_plate, (16, 'east'): reel_plate, (16, 'west'): reel_plate,
                  (22, 'north'): cutter_front, (22, 'up'): cutter_top},
    },
    'fertilizer_spreader': {
        'tex': 'fertilizer_spreader',
        'elements': {0: 'leg', 1: 'leg', 2: 'leg', 3: 'leg', 4: 'planks', 5: 'loam', 6: 'rim', 7: 'rim', 8: 'rim', 9: 'rim',
                     10: 'planks', 11: 'steel', 12: 'loam', 13: 'copper', 14: 'steel', 15: 'steel', 16: 'steel', 17: 'steel'},
        'faces': {(15, 'up'): spinner, (15, 'down'): spinner},
    },
    'copper_sprinkler': {
        'tex': 'copper_sprinkler',
        'elements': {0: 'copper', 1: 'copper', 2: 'steel', 3: 'copper', 4: 'copper', 5: 'copper', 6: 'copper', 7: 'copper',
                     8: 'copper'},
        'faces': {(3, 'up'): spray_head},
    },
    'scarecrow_lower': {
        'tex': 'scarecrow',
        'elements': {0: 'leg', 1: 'linen_patch', 2: 'straw'},
        'faces': {},
    },
    'scarecrow_upper': {
        'tex': 'scarecrow',
        'elements': {0: 'linen', 1: 'log', 2: 'linen', 3: 'linen', 4: 'straw', 5: 'straw', 6: 'sack', 7: 'straw', 8: 'straw',
                     9: 'log_dark'},
        'faces': {(6, 'north'): lambda w, h: sack(w, h, face=True), (7, 'up'): hat_brim},
    },
}

AXES = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}


def face_size(e, face):
    a, b = AXES[face]
    return abs(e['to'][a] - e['from'][a]), abs(e['to'][b] - e['from'][b])


def long_axis(e):
    d = [abs(e['to'][i] - e['from'][i]) for i in range(3)]
    return d.index(max(d))


def paint(material, e, face, w, h):
    la = long_axis(e)
    a, b = AXES[face]
    is_end = la not in (a, b)  # the face looks down the element's long axis
    along_y = b == la and a != la  # the long axis runs up the face
    if material in ('leg', 'log', 'rim', 'log_dark'):
        dark = material in ('leg', 'log_dark')
        if is_end and w <= 4 and h <= 4:
            return end_grain(w, h, dark)
        im = log(w, h, dark)
        if along_y and w > h:
            im = log(h, w, dark).transpose(Image.Transpose.ROTATE_90)
        return im
    if material == 'planks':
        return planks(w, h, along_y=False)
    if material == 'planks_dark':
        im = planks(w, h)
        px = im.load()
        for y in range(h):
            for x in range(w):
                c = px[x, y]
                if c in WOOD:
                    px[x, y] = WOOD[max(0, WOOD.index(c) - 2)]
        return im
    if material == 'steel':
        return steel(w, h)
    if material == 'copper':
        return copper(w, h)
    if material == 'linen':
        return linen(w, h, seam=face in ('north', 'south'))
    if material == 'linen_patch':
        return linen(w, h, seam=face in ('north', 'south'), patch=face == 'north')
    if material == 'sack':
        return sack(w, h)
    if material == 'straw':
        return straw(w, h)
    if material == 'compost':
        return tile('compost_bin_compost', w, h)
    if material == 'loam':
        return tile('loam', w, h)
    raise ValueError(material)


def fix_geometry(name, d):
    els = d['elements']
    if name in ('planter', 'harvester'):
        # The corner angles (11-14) stand 0.1 px proud of the iron band (10), which shared their outer faces.
        band = els[10]
        for i in (11, 12, 13, 14):
            e = els[i]
            for ax in (0, 2):
                lo, hi = e['from'][ax], e['to'][ax]
                if math.isclose(lo, band['from'][ax]):
                    e['from'][ax] = round(lo - 0.1, 2)
                if math.isclose(hi, band['to'][ax]):
                    e['to'][ax] = round(hi + 0.1, 2)
    if name == 'scarecrow_upper':
        # The straw hands (4, 5) are wider than the arm stick (1) they cover, so their faces aren't coplanar with it.
        arm = els[1]
        for i in (4, 5):
            e = els[i]
            if math.isclose(e['from'][2], arm['from'][2]):
                e['from'][2] = round(arm['from'][2] - 0.2, 2)
                e['to'][2] = round(arm['to'][2] + 0.2, 2)
    if name == 'fertilizer_spreader':
        # The legs (0-3) reach the hopper's underside (element 4).
        for i in range(4):
            els[i]['to'][1] = els[4]['from'][1]


def build(texture, names):
    sheet = new(64, 64)
    regions = {}  # signature -> (x, y)
    shelf_x, shelf_y, shelf_h = 0, 0, 0
    models = {}
    for name in names:
        path = MODELS + name + '.json'
        raw = open(path, encoding='utf-8').read()
        d = json.loads(raw)
        fix_geometry(name, d)
        spec = M[name]
        for i, e in enumerate(d['elements']):
            material = spec['elements'][i]
            for face, f in e['faces'].items():
                fw, fh = face_size(e, face)
                w, h = max(1, math.ceil(fw - 1e-6)), max(1, math.ceil(fh - 1e-6))
                special = spec['faces'].get((i, face))
                sig = (material, special.__name__ if special else None, w, h, face in ('up', 'down'), long_axis(e), face in ('east', 'west'))
                if sig not in regions:
                    im = special(w, h) if special else paint(material, e, face, w, h)
                    if shelf_x + w > 64:
                        shelf_x, shelf_y, shelf_h = 0, shelf_y + shelf_h, 0
                    if shelf_y + h > 64:
                        raise RuntimeError(texture + ' sheet is full')
                    sheet.alpha_composite(im, (shelf_x, shelf_y))
                    regions[sig] = (shelf_x, shelf_y)
                    shelf_x += w
                    shelf_h = max(shelf_h, h)
                x, y = regions[sig]
                f['uv'] = [round(x / 4, 4), round(y / 4, 4), round((x + fw) / 4, 4), round((y + fh) / 4, 4)]
        models[name] = (path, d, '\r\n' if '\r\n' in raw else '\n')
    sheet.save(TEX + texture + '.png')
    for path, d, nl in models.values():
        with open(path, 'w', encoding='utf-8', newline=nl) as out:
            out.write(json.dumps(d, indent=2) + '\n')
    print(texture, len(regions), 'regions, up to row', shelf_y + shelf_h)


if __name__ == '__main__':
    build('planter', ['planter'])
    build('harvester', ['harvester'])
    build('fertilizer_spreader', ['fertilizer_spreader'])
    build('copper_sprinkler', ['copper_sprinkler'])
    build('scarecrow', ['scarecrow_lower', 'scarecrow_upper'])
