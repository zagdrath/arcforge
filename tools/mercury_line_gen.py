"""The mercury line's textures and the Mercury Vapour Lamp's models (docs/TEXTURE_STYLE.md, "Mercury line").

- item/cinnabar_dust.png: Cody's dust (the Iron Dust heap) recoloured by brightness rank onto the vermilion ramp;
- item/gold_amalgam.png, item/silver_amalgam.png: Cody's lump (the Pine Resin lump) re-shaped to a silhouette of each
  one's own, its facets and outline kept, recoloured by rank onto a dull amalgam silver and flecked with gold or bright
  silver on a few lit pixels;
- block/fluid/mercury_still.png / _flow.png: drawn here as rolling, looping swells on a mirror-steel gradient;
- gui/sprites/container/induction_furnace/mercury_fill.png: the tank's fallback fill;
- block/mercury_vapour_lamp.png, mercury_vapour_lamp_bulb(_on).png and the lamp's block models and blockstate.

Run from the repository root: python3 tools/mercury_line_gen.py
"""
import json
import os

from PIL import Image

A = 'src/main/resources/assets/arcforge/'
T = A + 'textures/'


def rgb(c):
    return (c >> 16) & 255, (c >> 8) & 255, c & 255


def ranks(im):
    """The image's opaque colours, darkest first."""
    colours = {px[:3] for px in im.getdata() if px[3]}
    return sorted(colours, key=lambda c: (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2], c))


def by_rank(im, ramp):
    """Every opaque colour moved to the same rank of the ramp (darkest to lightest)."""
    order = ranks(im)
    assert len(order) == len(ramp), (len(order), len(ramp))
    table = {c: rgb(ramp[i]) for i, c in enumerate(order)}
    out = im.copy()
    px = out.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = table[(r, g, b)] + (a,)
    return out


# Vermilion (mercury sulfide ground to a pigment): a deep brick-red outline to a muted orange-red top, kept earthy (a
# mineral powder, not a dye) so it sits with the mod's other dusts.
VERMILION = [0x5E2420, 0x8C3A30, 0xA4483A, 0xBC5C48, 0xD48670]
# Amalgam: a dull grey silver in close steps, darker and flatter than any ingot (a paste, not a metal), so the flecks
# are its only bright pixels.
AMALGAM = [0x26292D, 0x363B40, 0x474D53, 0x575D63, 0x666C72, 0x747A7F, 0x82878C, 0x8F9499]
GOLD_FLECK = (0xE8C04A, 0xB8892A)
SILVER_FLECK = (0xF6F9FC, 0xD8DFE6)
# Mercury: mirror-bright, the Lead-Bismuth bands moved up to a light steel with near-white highlights.
MERCURY = [0x4E5760, 0x808A93, 0xAEB7BF, 0xDCE3E9, 0xFFFFFF]
# The flowing frames have one more tone, a deeper trough under the darkest.
MERCURY_FLOW = [0x3E464E] + MERCURY

# The amalgams are Cody's lump (the Pine Resin lump) re-shaped: each has a silhouette of its own (MASKS), filled with the
# lump's own facets (its brightness ranks read off at the same place across the lump, scaled to the new outline), on the
# lump's outline rule (its lighter outline tone on the top and left edges, its darkest on the bottom and right), all
# moved by rank onto AMALGAM. So they keep the lump's texturing and colours, in shapes nobody else uses:
# - Gold Amalgam: a wide, low lump with two knobs on top;
# - Silver Amalgam: a tall lump, narrower and leaning, with one knob on top.
MASKS = {
    'gold': [
        '................',
        '................',
        '................',
        '.....####.###...',
        '...###########..',
        '..#############.',
        '.##############.',
        '.##############.',
        '.##############.',
        '.##############.',
        '..#############.',
        '..############..',
        '...##########...',
        '.....######.....',
        '................',
        '................',
    ],
    'silver': [
        '................',
        '.......###......',
        '.....######.....',
        '....########....',
        '...#########....',
        '...##########...',
        '..###########...',
        '..############..',
        '..############..',
        '..############..',
        '...###########..',
        '...##########...',
        '....#########...',
        '.....#######....',
        '......####......',
        '................',
    ],
}
FLECK_SPACING = 3


def outline(cells):
    """The cells on a shape's edge (a 4-neighbour outside it)."""
    return {(x, y) for x, y in cells if any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def rank_map(im):
    order = ranks(im)
    return {(x, y): order.index(im.getpixel((x, y))[:3]) for y in range(im.height) for x in range(im.width) if im.getpixel((x, y))[3]}


def reshaped(lump, mask, fleck):
    src = rank_map(lump)
    edge_src = outline(set(src))
    inner = {p: r for p, r in src.items() if p not in edge_src}
    sx0, sx1 = min(x for x, _ in inner), max(x for x, _ in inner)
    sy0, sy1 = min(y for _, y in inner), max(y for _, y in inner)
    cells = {(x, y) for y, row in enumerate(mask) for x, ch in enumerate(row) if ch == '#'}
    edge = outline(cells)
    body = cells - edge
    bx0, bx1 = min(x for x, _ in body), max(x for x, _ in body)
    by0, by1 = min(y for _, y in body), max(y for _, y in body)
    rank = {}
    for x, y in cells:
        if (x, y) in edge:
            # The lump's outline: its lighter outline tone where the light falls (top, left), its darkest elsewhere.
            rank[(x, y)] = 1 if (x - 1, y) not in cells or (x, y - 1) not in cells else 0
            continue
        u = sx0 + round((x - bx0) * (sx1 - sx0) / max(1, bx1 - bx0))
        v = sy0 + round((y - by0) * (sy1 - sy0) / max(1, by1 - by0))
        if (u, v) not in inner:
            u, v = min(inner, key=lambda p: (p[0] - u) ** 2 + (p[1] - v) ** 2)
        rank[(x, y)] = inner[(u, v)]
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for p, r in rank.items():
        im.putpixel(p, rgb(AMALGAM[r]) + (255,))
    # Flecks on the lit grains (rank 4 and up), kept apart, each with its shadow down-right.
    lit, shade = fleck
    # Spread over the lump: the first lit grain from the top left, then each next the one farthest from those taken.
    candidates = sorted(p for p in body if rank[p] >= 4 and (p[0] + 1, p[1] + 1) in body)
    candidates.sort(key=lambda p: (p[1], p[0]))
    flecks = candidates[:1]
    while candidates and len(flecks) < 5:
        best = max(candidates, key=lambda p: min(abs(p[0] - f[0]) + abs(p[1] - f[1]) for f in flecks))
        if min(abs(best[0] - f[0]) + abs(best[1] - f[1]) for f in flecks) < FLECK_SPACING:
            break
        flecks.append(best)
    for x, y in flecks:
        im.putpixel((x, y), rgb(lit) + (255,))
        if (x + 1, y + 1) in body and (x + 1, y + 1) not in flecks:
            im.putpixel((x + 1, y + 1), rgb(shade) + (255,))
    return im


# Mercury's frames, in the vanilla fluid language (water's and lava's soft, irregular swells, many close tones, a seamless
# looping animation) rather than flat bands. Drawn here from a seeded value noise that tiles in x and y and loops in time
# (its lattice wraps on all three), two octaves, so the swells are organic and never a grid. Still (16 px): broad swells
# slowly changing shape. Flowing (32 px, as vanilla's flow): the swells stretched four to one along the flow and carried
# down a whole tile per loop. The height is read along a mirror-steel gradient: dark reflections in the troughs, a broad
# bright silver, near-white highlights only on the highest crests, in 12 close steps (low contrast, as vanilla water).
MERCURY_GRADIENT = [(0.0, 0x66707A), (0.3, 0x87919A), (0.55, 0xA5AFB8), (0.78, 0xC2CAD1), (0.92, 0xDCE2E7),
                    (1.0, 0xF4F7F9)]
MERCURY_STEPS = 12
FRAMES = 32
SEED = 263


def gradient(t):
    t = round(t * (MERCURY_STEPS - 1)) / (MERCURY_STEPS - 1)
    for (t0, c0), (t1, c1) in zip(MERCURY_GRADIENT, MERCURY_GRADIENT[1:]):
        if t <= t1:
            k = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return tuple(round(a + (b - a) * k) for a, b in zip(rgb(c0), rgb(c1)))
    return rgb(MERCURY_GRADIENT[-1][1])


class LoopNoise:
    """Value noise on a lattice of nx x ny x nt cells that wraps on every axis (smoothstep between lattice values)."""

    def __init__(self, nx, ny, nt, seed):
        import random
        rnd = random.Random(seed)
        self.n = (nx, ny, nt)
        self.v = [[[rnd.random() for _ in range(nt)] for _ in range(ny)] for _ in range(nx)]

    def __call__(self, u, v, w):
        """u, v, w in 0..1 (one tile, one loop)."""
        nx, ny, nt = self.n
        fx, fy, ft = u * nx, v * ny, w * nt
        x0, y0, t0 = int(fx // 1), int(fy // 1), int(ft // 1)
        sx, sy, st = (fx - x0), (fy - y0), (ft - t0)
        sx, sy, st = (k * k * (3 - 2 * k) for k in (sx, sy, st))
        total = 0.0
        for dx, wx in ((0, 1 - sx), (1, sx)):
            for dy, wy in ((0, 1 - sy), (1, sy)):
                for dt, wt in ((0, 1 - st), (1, st)):
                    total += wx * wy * wt * self.v[(x0 + dx) % nx][(y0 + dy) % ny][(t0 + dt) % nt]
        return total


def frames(size, height):
    """FRAMES frames of size x size, stacked, from height(x, y, phase 0..1), normalised frame by frame."""
    values = [[height(x, y, f / FRAMES) for y in range(size) for x in range(size)] for f in range(FRAMES)]
    # Softened with a wrapping 3x3 blur, so the noise's lattice never shows as squares.
    def soft(frame):
        return [sum(frame[((y + dy) % size) * size + (x + dx) % size] for dx in (-1, 0, 1) for dy in (-1, 0, 1)) / 9
                for y in range(size) for x in range(size)]
    values = [soft(frame) for frame in values]
    out = Image.new('RGBA', (size, size * FRAMES))
    for f, frame in enumerate(values):
        # Normalised per frame, so every frame has the same spread of tones and none flashes brighter.
        flat = sorted(frame)
        lo, hi = flat[int(len(flat) * 0.02)], flat[int(len(flat) * 0.99)]
        for i, v in enumerate(frame):
            t = min(1.0, max(0.0, (v - lo) / (hi - lo)))
            # Crests sharpen into highlights, as light catches a metal surface.
            out.putpixel((i % size, f * size + i // size), gradient(t ** 1.4) + (255,))
    return out


def mercury_fluid():
    broad, fine = LoopNoise(6, 6, 6, SEED), LoopNoise(12, 12, 6, SEED + 1)
    long_broad, long_fine = LoopNoise(8, 2, 4, SEED + 2), LoopNoise(16, 4, 4, SEED + 3)

    def still(x, y, p):
        u, v = (x + 0.5) / 16, (y + 0.5) / 16
        return broad(u, v, p) + 0.5 * fine(u, v, p)

    def flow(x, y, p):
        # Carried down a whole tile per loop (the lattice wraps in y), and changing slowly as it goes.
        u, v = (x + 0.5) / 32, ((y + 0.5) / 32 - p) % 1.0
        return long_broad(u, v, p) + 0.45 * long_fine(u, v, p)

    for kind, size, height, frametime in (('still', 16, still, 3), ('flow', 32, flow, 2)):
        frames(size, height).save(T + 'block/fluid/mercury_' + kind + '.png')
        with open(T + 'block/fluid/mercury_' + kind + '.png.mcmeta', 'w') as f:
            json.dump({'animation': {'frametime': frametime, 'interpolate': True}}, f, indent=2)
            f.write('\n')


def mercury_fill():
    """12x50: the mercury tones in 2 px diagonal bands, as the other tanks' fallback fills."""
    im = Image.new('RGBA', (12, 50))
    tones = [rgb(c) for c in (MERCURY[1], MERCURY[2], MERCURY[2], MERCURY[3])]
    for y in range(50):
        for x in range(12):
            im.putpixel((x, y), tones[((x + y) // 2) % 4] + (255,))
    for x in range(12):
        im.putpixel((x, 0), rgb(MERCURY[4]) + (255,))
    im.save(T + 'gui/sprites/container/induction_furnace/mercury_fill.png')


# --- The Mercury Vapour Lamp ---

# The machine steel ramp.
DEEP, DARK, SHADE, FACE_SHADE, FACE, MID, LIT, RIVET = (0x16181B, 0x2B2F34, 0x383C42, 0x40454C, 0x474C53, 0x575D65,
                                                       0x727982, 0x959DA6)
# The bulb's glass: off, a cool dull grey-blue; on, blue-white (mercury's spectral blue-green, washed to white).
BULB_OFF = (0x3E4852, 0x56626E, 0x6E7C89, 0x8896A2)
BULB_ON = (0x8EB8E0, 0xB8D6F2, 0xDCEEFF, 0xF6FBFF)

# The 32x32 sheet's regions (x, y, w, h): each face painted to its own size.
SHEET = {
    'plate_top': (0, 0, 10, 10),
    'plate_side': (0, 10, 10, 2),
    'socket_top': (10, 0, 6, 6),
    'socket_side': (10, 6, 6, 2),
    'bar': (16, 0, 1, 8),
    'rail': (0, 12, 8, 1),
    'rail_top': (0, 13, 8, 1),
    'cap_top': (17, 0, 8, 8),
    'cap_side': (0, 14, 8, 1),
}
# The bulb texture's regions (16x16 each): the side 4x7 and the top 4x4.
BULB = {'side': (0, 0, 4, 7), 'top': (4, 0, 4, 4)}


def bevelled(im, region, face, lit, dark, rivets=False):
    x0, y0, w, h = region
    for y in range(h):
        for x in range(w):
            c = face
            if y == 0 or x == 0:
                c = lit
            if y == h - 1 or x == w - 1:
                c = dark
            if (x == 0 and y == h - 1) or (x == w - 1 and y == 0):
                c = SHADE
            im.putpixel((x0 + x, y0 + y), rgb(c) + (255,))
    if rivets and w >= 9 and h >= 9:
        for rx, ry in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
            im.putpixel((x0 + rx, y0 + ry), rgb(RIVET) + (255,))
            sx, sy = rx + (1 if rx < w // 2 else -1), ry + (1 if ry < h // 2 else -1)
            im.putpixel((x0 + sx, y0 + sy), rgb(DARK) + (255,))


def lamp_sheet():
    im = Image.new('RGBA', (32, 32), rgb(FACE) + (255,))
    bevelled(im, SHEET['plate_top'], FACE, LIT, DEEP, rivets=True)
    # The plate's edge: lit top row, shade below.
    x0, y0, w, h = SHEET['plate_side']
    for x in range(w):
        im.putpixel((x0 + x, y0), rgb(MID) + (255,))
        im.putpixel((x0 + x, y0 + 1), rgb(DARK) + (255,))
    # The lamp holder: a darker ring round the bulb's neck.
    bevelled(im, SHEET['socket_top'], SHADE, MID, DEEP)
    x0, y0, w, h = SHEET['socket_side']
    for x in range(w):
        im.putpixel((x0 + x, y0), rgb(SHADE) + (255,))
        im.putpixel((x0 + x, y0 + 1), rgb(DARK) + (255,))
    # Cage bars: lit on their top px, then mid with a shade foot.
    x0, y0, w, h = SHEET['bar']
    for y in range(h):
        im.putpixel((x0, y0 + y), rgb(LIT if y == 0 else SHADE if y == h - 1 else MID) + (255,))
    for name, tone in (('rail', MID), ('rail_top', LIT), ('cap_side', MID)):
        x0, y0, w, h = SHEET[name]
        for x in range(w):
            im.putpixel((x0 + x, y0), rgb(DARK if x == w - 1 else tone) + (255,))
    # The cap: a riveted plate with a vent ring round its middle.
    bevelled(im, SHEET['cap_top'], FACE, LIT, DEEP)
    x0, y0, w, h = SHEET['cap_top']
    for x, y in ((3, 2), (4, 2), (2, 3), (5, 3), (2, 4), (5, 4), (3, 5), (4, 5)):
        im.putpixel((x0 + x, y0 + y), rgb(DARK) + (255,))
    for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
        im.putpixel((x0 + x, y0 + y), rgb(DEEP) + (255,))
    im.save(T + 'block/mercury_vapour_lamp.png')


def bulb(tones, name):
    """The glass bulb: shaded across its side (lit just left of centre), a lighter arc-tube line down its middle, a
    rounded top; on, the same in blue-white."""
    d, m, l, h = (rgb(c) for c in tones)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    x0, y0, w, hh = BULB['side']
    across = [m, l, h, m]
    for y in range(hh):
        for x in range(w):
            c = across[x]
            if y == 0 or y == hh - 1:
                c = d if x in (0, 3) else m
            im.putpixel((x0 + x, y0 + y), c + (255,))
    # The arc tube's bright core, rows 2..4.
    for y in (2, 3, 4):
        im.putpixel((x0 + 2, y0 + y), h + (255,))
        im.putpixel((x0 + 1, y0 + y), l + (255,))
    x0, y0, w, hh = BULB['top']
    for y in range(hh):
        for x in range(w):
            edge = x in (0, 3) or y in (0, 3)
            corner = x in (0, 3) and y in (0, 3)
            c = d if corner else m if edge else l
            im.putpixel((x0 + x, y0 + y), c + (255,))
    im.putpixel((x0 + 1, y0 + 1), h + (255,))
    im.save(T + 'block/' + name + '.png')


def uv(region, scale):
    x, y, w, h = region
    return [x / scale, y / scale, (x + w) / scale, (y + h) / scale]


def cube(frm, to, side, top, tex='#t', emissive=False, cull_down=False, bottom=None):
    faces = {}
    for f in ('north', 'south', 'east', 'west'):
        faces[f] = {'texture': tex, 'uv': side}
    faces['up'] = {'texture': tex, 'uv': top}
    faces['down'] = {'texture': tex, 'uv': bottom or top}
    if cull_down:
        faces['down']['cullface'] = 'down'
    element = {'from': frm, 'to': to, 'faces': faces}
    if emissive:
        element['light_emission'] = 15
    return element


def lamp_model(lit):
    s = 2  # the 32 px sheet: 2 px a UV unit
    sh = {k: uv(v, s) for k, v in SHEET.items()}
    b = {k: uv(v, 1) for k, v in BULB.items()}
    elements = [
        # Mounting plate and lamp holder.
        cube([3, 0, 3], [13, 2, 13], sh['plate_side'], sh['plate_top'], cull_down=True),
        cube([5, 2, 5], [11, 4, 11], sh['socket_side'], sh['socket_top']),
        # The bulb.
        cube([6, 4, 6], [10, 11, 10], b['side'], b['top'], tex='#bulb', emissive=lit),
    ]
    # Four cage bars at the corners, the rails, the cap.
    for x, z in ((4, 4), (11, 4), (4, 11), (11, 11)):
        elements.append(cube([x, 4, z], [x + 1, 12, z + 1], sh['bar'], uv((16, 0, 1, 1), s)))
    # Rails round the cage: one at its foot (closing it onto the holder) and one round its middle.
    for y in (4, 8):
        for frm, to in (([4, y, 4], [12, y + 1, 5]), ([4, y, 11], [12, y + 1, 12]), ([4, y, 5], [5, y + 1, 11]), ([11, y, 5], [12, y + 1, 11])):
            elements.append(cube(frm, to, sh['rail'], sh['rail_top']))
    elements.append(cube([4, 12, 4], [12, 13, 12], sh['cap_side'], sh['cap_top']))
    return {
        'parent': 'minecraft:block/block',
        'texture_size': [32, 32],
        'textures': {
            't': 'arcforge:block/mercury_vapour_lamp',
            'bulb': 'arcforge:block/mercury_vapour_lamp_bulb' + ('_on' if lit else ''),
            'particle': 'arcforge:block/mercury_vapour_lamp',
        },
        'elements': elements,
        'render_type': 'minecraft:cutout',
    }


# Pointing up as modelled; turned as the End Rod is.
ROTATIONS = {'up': {}, 'down': {'x': 180}, 'north': {'x': 90}, 'east': {'x': 90, 'y': 90}, 'south': {'x': 90, 'y': 180},
             'west': {'x': 90, 'y': 270}}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def lamp_assets():
    write_json(A + 'models/block/mercury_vapour_lamp.json', lamp_model(False))
    write_json(A + 'models/block/mercury_vapour_lamp_on.json', lamp_model(True))
    variants = {}
    for facing, rotation in ROTATIONS.items():
        for lit in (False, True):
            for powered in (False, True):
                model = {'model': 'arcforge:block/mercury_vapour_lamp' + ('_on' if lit else '')}
                model.update(rotation)
                variants['facing=%s,lit=%s,powered=%s' % (facing, str(lit).lower(), str(powered).lower())] = model
    write_json(A + 'blockstates/mercury_vapour_lamp.json', {'variants': variants})
    write_json(A + 'items/mercury_vapour_lamp.json', {'model': {'type': 'minecraft:model', 'model': 'arcforge:block/mercury_vapour_lamp_on'}})


def main():
    dust = Image.open(T + 'item/iron_dust.png').convert('RGBA')
    by_rank(dust, VERMILION).save(T + 'item/cinnabar_dust.png')
    lump = Image.open(T + 'item/pine_resin.png').convert('RGBA')
    reshaped(lump, MASKS['gold'], GOLD_FLECK).save(T + 'item/gold_amalgam.png')
    reshaped(lump, MASKS['silver'], SILVER_FLECK).save(T + 'item/silver_amalgam.png')
    mercury_fluid()
    mercury_fill()
    lamp_sheet()
    bulb(BULB_OFF, 'mercury_vapour_lamp_bulb')
    bulb(BULB_ON, 'mercury_vapour_lamp_bulb_on')
    lamp_assets()


if __name__ == '__main__':
    main()
