"""Every Arcforge fluid's and gas's block textures, in the vanilla fluid language (docs/TEXTURE_STYLE.md, "Fluid
textures"): soft, irregular swells in many close tones and a seamless looping animation, like vanilla water and lava,
instead of a few flat bands re-toned from one shared set of frames.

Each liquid keeps its colours (its old frames' tones, darkest to lightest, become its gradient) and its translucency,
but gets a pattern of its own: a seeded value noise (the seed from its name) that tiles in x and y and loops in time,
shaped by its kind (KINDS: how big its swells are, how fast they change, how sharp its highlights are). The gases share
one greyscale texture that the game tints per gas (block/fluid/steam_*): wisps of vapour, mostly see-through, thickening
and thinning as they drift.

Corium is left alone (it is Cody's own lava texture, recoloured), as is anything that draws vanilla water.

Run from the repository root: python3 tools/fluid_texture_gen.py
"""
import json
import math
import random
import zlib

from PIL import Image

T = 'src/main/resources/assets/arcforge/textures/block/fluid/'
FRAMES = 32
STEPS = 12


# How each kind of liquid moves:
#   cells: the two noise octaves' cells across a 16 px tile (bigger swells with fewer cells), and the fine octave's weight;
#   turns: how many times the noise's time lattice wraps over the loop (more is livelier);
#   gamma: >1 keeps the light tones for the crests (a sheen), <1 a flatter, milkier surface;
#   ridged: crusted veins (molten salts and metals), as lava's;
#   still / flow: frametimes.
KINDS = {
    # Water-like: acids, brines, solutions, heavy water.
    'thin': dict(cells=(6, 12), fine=0.5, turns=6, gamma=1.3, ridged=False, still=2, flow=2),
    # Boiling-hot water-like coolants: smaller, livelier swells.
    'hot': dict(cells=(8, 16), fine=0.55, turns=10, gamma=1.3, ridged=False, still=2, flow=1),
    # Milky and cloudy (latex, sodium silicate, lye): soft, flat, little sheen.
    'milky': dict(cells=(4, 8), fine=0.35, turns=4, gamma=0.9, ridged=False, still=3, flow=2),
    # Light fuels and oils: broad, smooth swells with bright sheen on the crests.
    'oil': dict(cells=(4, 10), fine=0.4, turns=4, gamma=1.6, ridged=False, still=3, flow=2),
    # Heavy, slow, near-black: big lazy swells, barely changing, a rare dull sheen.
    'thick': dict(cells=(3, 6), fine=0.3, turns=3, gamma=1.8, ridged=False, still=6, flow=4),
    # Molten salts and metals: lava's crusted veins, slow.
    'molten': dict(cells=(4, 8), fine=0.45, turns=4, gamma=1.1, ridged=True, still=4, flow=3),
    # A suspension of ground ore (the slurries, tinted per metal): swells with a fine grain through them.
    'slurry': dict(cells=(5, 16), fine=0.6, turns=4, gamma=1.0, ridged=False, still=3, flow=2),
    # Liquid Experience: bright and lively, glowing crests.
    'glow': dict(cells=(5, 10), fine=0.5, turns=8, gamma=1.4, ridged=False, still=2, flow=1),
}

LIQUIDS = {
    'brine': 'thin', 'lithium_brine': 'thin', 'hydrochloric_acid': 'thin', 'hydrofluoric_acid': 'thin',
    'nitric_acid': 'thin', 'sulfuric_acid': 'thin', 'uranyl_sulfate_solution': 'thin', 'trichlorosilane': 'thin',
    'heavy_water': 'thin', 'nutrient_solution': 'thin', 'ethanol': 'thin', 'naphtha': 'thin',
    'hot_coolant': 'hot', 'hot_heavy_water': 'hot',
    'latex': 'milky', 'sodium_silicate': 'milky', 'lye': 'milky',
    'light_oil': 'oil', 'kerosene': 'oil', 'sour_kerosene': 'oil', 'diesel': 'oil', 'sour_diesel': 'oil',
    'gasoline': 'oil', 'lubricant': 'oil', 'seed_oil': 'oil', 'biodiesel': 'oil',
    'heavy_oil': 'thick', 'crude_oil': 'thick', 'sour_crude_oil': 'thick', 'bitumen': 'thick', 'creosote': 'thick',
    'high_level_waste': 'thick',
    'lead_bismuth': 'molten', 'hot_lead_bismuth': 'molten', 'fuel_salt': 'molten', 'hot_fuel_salt': 'molten',
    'slurry': 'slurry',
    'liquid_experience': 'glow',
}


# Each liquid's colours (its old still frames' tones, darkest to lightest) and alpha, kept as they were.
PALETTES = {
    'brine': ([0x5E7A8A, 0x7C98A8, 0x9CB6C4, 0xBCD2DC, 0xD8E8EE], 210),
    'lithium_brine': ([0x4E7C70, 0x6C9A8C, 0x8CB8A8, 0xAED2C4, 0xCEE6DC], 210),
    'hydrochloric_acid': ([0x9C9450, 0xB4AC62, 0xCCC47A, 0xDEDA96, 0xEEEAB6], 210),
    'hydrofluoric_acid': ([0xAEB8BC, 0xBCC6CA, 0xC8D2D6, 0xE2EAEE, 0xF0F6F8], 210),
    'nitric_acid': ([0xC8B460, 0xD4C070, 0xDECC82, 0xF0E4AE, 0xF8F0CC], 210),
    'sulfuric_acid': ([0x8E9C30, 0xA0AE40, 0xB2C052, 0xC2D066, 0xDEEA98, 0xF2F9C6], 170),
    'uranyl_sulfate_solution': ([0xB8A008, 0xD0BA10, 0xE2CE1C, 0xF8EC6A, 0xFFF8A8], 210),
    'trichlorosilane': ([0x8E9890, 0xA7B2AA, 0xBEC9C1, 0xD0D9D2, 0xDCE4DE], 210),
    'heavy_water': ([0x1A3A86, 0x24489C, 0x2E58B0, 0x5486D6, 0x7CA6E6], 210),
    'nutrient_solution': ([0x1E4A18, 0x2E6A22, 0x468C30, 0x62AA40, 0x86C858], 210),
    'ethanol': ([0xD8C688, 0xE2D29A, 0xEADDAC, 0xF7EFD2, 0xFCF8E6], 190),
    'naphtha': ([0xC79A4C, 0xD4AA5C, 0xDEB86C, 0xE6C47C, 0xF6E0AA, 0xFFF0CC], 150),
    'hot_coolant': ([0x7EA4C0, 0x8CB2CC, 0x9CC0D8, 0xC2DCEC, 0xDCECF6], 210),
    'hot_heavy_water': ([0x4E74B4, 0x5C82C0, 0x6C92CC, 0x94B6E0, 0xB4CEEC], 210),
    'latex': ([0xA89E88, 0xC4BCA8, 0xD8D2C2, 0xE8E4D8, 0xF4F2EA], 210),
    'sodium_silicate': ([0xA8A496, 0xC2BEB0, 0xD6D3C6, 0xE5E2D7, 0xEEECE3], 210),
    'lye': ([0x7C8078, 0x9A9E94, 0xB8BCB2, 0xD2D6CC, 0xE6E8E0], 210),
    'light_oil': ([0xB4861A, 0xC69A24, 0xD6AC30, 0xE2BC40, 0xF6DC80, 0xFFEEB0], 215),
    'kerosene': ([0xC8C6AA, 0xD4D2B6, 0xDEDCC2, 0xE6E4CC, 0xF2F0DC, 0xFBFAEE], 215),
    'sour_kerosene': ([0xC6C38B, 0xCECC98, 0xD6D4A5, 0xDCDAAF, 0xE5E4BF, 0xEDECD0], 215),
    'diesel': ([0x9C8418, 0xAE9420, 0xC0A42C, 0xCCB238, 0xE8D27A, 0xF6E6AA], 215),
    'sour_diesel': ([0x827521, 0x938528, 0xA59532, 0xB2A33B, 0xD4C76E, 0xE2D999], 215),
    'gasoline': ([0xB27428, 0xCC8E39, 0xE1A84E, 0xEEBE6E, 0xF6D392, 0xFBE6B8], 215),
    'lubricant': ([0x7A4E10, 0x9A6515, 0xB87C1C, 0xD09226, 0xE2AA3D, 0xF2C25A], 215),
    'seed_oil': ([0x7A5010, 0xA8761A, 0xC8961E, 0xE4B43A, 0xF4CE62], 220),
    'biodiesel': ([0x8C7E1E, 0xAA9C2C, 0xC4B63E, 0xD8CC58, 0xE8E07C], 220),
    'heavy_oil': ([0x24150A, 0x2C1A0D, 0x352010, 0x402714], 255),
    'crude_oil': ([0x0D0C0B, 0x131210, 0x1A1815, 0x2E2A25], 255),
    'sour_crude_oil': ([0x100E09, 0x17140D, 0x1F1B11, 0x36301E], 255),
    'bitumen': ([0x060607, 0x0A0A0C, 0x0E0E11, 0x141418], 255),
    'creosote': ([0x0C0705, 0x120A06, 0x180E09, 0x1E120B, 0x25160D, 0x2D1B10, 0x4E3018, 0x6E4622], 255),
    'high_level_waste': ([0x2A1A0E, 0x3A2614, 0x4C321A, 0x74522E, 0x8C683E], 255),
    'lead_bismuth': ([0x3A3C46, 0x4E505C, 0x646674, 0x9A9CA8, 0xC0C2CC], 255),
    'hot_lead_bismuth': ([0x5A2E26, 0x7A3E2C, 0x9A5434, 0xD49056, 0xECB87A], 255),
    'fuel_salt': ([0x3E6A3A, 0x4E7E48, 0x62925A, 0x96C08A, 0xB8D8A8], 210),
    'hot_fuel_salt': ([0x7A5A1A, 0x987020, 0xB48A2A, 0xE0C050, 0xF2DC7A], 210),
    'slurry': ([0x8A8A8A, 0x9A9A9A, 0xABABAB, 0xBDBDBD, 0xD9D9D9, 0xF0F0F0], 235),
    'liquid_experience': ([0x5E9A2C, 0x7DBA3C, 0x9CD24E, 0xBDE86C, 0xDCF89A], 230),
}


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def palette(name):
    colours, alpha = PALETTES[name]
    return [((c >> 16) & 255, (c >> 8) & 255, c & 255) for c in colours], alpha


def gradient(colours, t):
    """t (0..1) along the colours, evenly spaced, in STEPS flat steps."""
    t = round(t * (STEPS - 1)) / (STEPS - 1)
    f = t * (len(colours) - 1)
    i = min(int(f), len(colours) - 2)
    k = f - i
    return tuple(round(a + (b - a) * k) for a, b in zip(colours[i], colours[i + 1]))


class LoopNoise:
    """Value noise on an nx x ny x nt lattice that wraps on every axis (smoothstep between lattice values)."""

    def __init__(self, nx, ny, nt, seed):
        rnd = random.Random(seed)
        self.n = (nx, ny, nt)
        self.v = [[[rnd.random() for _ in range(nt)] for _ in range(ny)] for _ in range(nx)]

    def __call__(self, u, v, w):
        nx, ny, nt = self.n
        fx, fy, ft = u * nx, v * ny, w * nt
        x0, y0, t0 = int(math.floor(fx)), int(math.floor(fy)), int(math.floor(ft))
        sx, sy, st = (k * k * (3 - 2 * k) for k in (fx - x0, fy - y0, ft - t0))
        total = 0.0
        for dx, wx in ((0, 1 - sx), (1, sx)):
            for dy, wy in ((0, 1 - sy), (1, sy)):
                for dt, wt in ((0, 1 - st), (1, st)):
                    total += wx * wy * wt * self.v[(x0 + dx) % nx][(y0 + dy) % ny][(t0 + dt) % nt]
        return total


def heights(size, height):
    """FRAMES frames of heights, each softened with a wrapping 3x3 blur so the lattice never shows as squares."""
    out = []
    for f in range(FRAMES):
        frame = [height(x, y, f / FRAMES) for y in range(size) for x in range(size)]
        out.append([sum(frame[((y + dy) % size) * size + (x + dx) % size] for dx in (-1, 0, 1) for dy in (-1, 0, 1)) / 9
                    for y in range(size) for x in range(size)])
    return out


def normalised(frame):
    """Each frame between its 2nd and 99th percentiles, so every frame has the same spread and none flashes."""
    ordered = sorted(frame)
    lo, hi = ordered[int(len(ordered) * 0.02)], ordered[int(len(ordered) * 0.99)]
    return [min(1.0, max(0.0, (v - lo) / (hi - lo))) for v in frame]


def seed_of(name, salt):
    return zlib.crc32((name + salt).encode())


def liquid_height(name, kind, flowing):
    k = KINDS[kind]
    big, small = k['cells']
    if flowing:
        # Stretched four to one along the flow (the 32 px tile has twice the cells across, half down) and carried down a
        # whole tile per loop.
        broad = LoopNoise(big * 2, max(1, big // 2), k['turns'], seed_of(name, 'flow0'))
        fine = LoopNoise(small * 2, max(2, small // 2), k['turns'], seed_of(name, 'flow1'))

        def h(x, y, p):
            u, v = (x + 0.5) / 32, ((y + 0.5) / 32 - p) % 1.0
            return mix(broad(u, v, p), fine(u, v, p), k)
    else:
        broad = LoopNoise(big, big, k['turns'], seed_of(name, 'still0'))
        fine = LoopNoise(small, small, k['turns'], seed_of(name, 'still1'))

        def h(x, y, p):
            u, v = (x + 0.5) / 16, (y + 0.5) / 16
            return mix(broad(u, v, p), fine(u, v, p), k)
    return h


def mix(broad, fine, k):
    value = broad + k['fine'] * fine
    if k['ridged']:
        # Lava's crust: bright where the two octaves cross their middles, dark plates between.
        value = 1.0 - abs(broad - 0.5) * 2 + 0.6 * (1.0 - abs(fine - 0.5) * 2)
    return value


def write(path, img, frametime):
    img.save(path)
    with open(path + '.mcmeta', 'w') as f:
        json.dump({'animation': {'frametime': frametime, 'interpolate': True}}, f, indent=2)
        f.write('\n')


def liquid(name, kind):
    colours, alpha = palette(name)
    k = KINDS[kind]
    for flowing, size, frametime in ((False, 16, k['still']), (True, 32, k['flow'])):
        frames = heights(size, liquid_height(name, kind, flowing))
        img = Image.new('RGBA', (size, size * FRAMES))
        for f, frame in enumerate(frames):
            for i, t in enumerate(normalised(frame)):
                img.putpixel((i % size, f * size + i // size), gradient(colours, t ** k['gamma']) + (alpha,))
        write(T + name + ('_flow' if flowing else '_still') + '.png', img, frametime)


# The shared gas texture (tinted per gas): near-white vapour whose opacity carries the shape, wisps from 25 to 70% opaque.
GAS_ALPHA = (64, 178)
GAS_TONES = ((0xD8, 0xD8, 0xD8), (0xFA, 0xFA, 0xFA))


def gas():
    # Billowing: a broad, slow octave domain-warped by a second, so the wisps curl rather than blob.
    for flowing, size, frametime in ((False, 16, 3), (True, 32, 2)):
        n = 'gas_flow' if flowing else 'gas_still'
        broad = LoopNoise(4 if not flowing else 8, 4 if not flowing else 2, 4, seed_of(n, '0'))
        warp = LoopNoise(6 if not flowing else 12, 6 if not flowing else 3, 4, seed_of(n, '1'))
        fine = LoopNoise(10 if not flowing else 20, 10 if not flowing else 5, 6, seed_of(n, '2'))

        def h(x, y, p, broad=broad, warp=warp, fine=fine, size=size, flowing=flowing):
            u, v = (x + 0.5) / size, (y + 0.5) / size
            if flowing:
                # Gas drifts up the flowing face.
                v = (v + p) % 1.0
            du = (warp(u, v, p) - 0.5) * 0.35
            dv = (warp(v, u, p) - 0.5) * 0.35
            return broad((u + du) % 1.0, (v + dv) % 1.0, p) + 0.4 * fine(u, v, p)

        frames = heights(size, h)
        img = Image.new('RGBA', (size, size * FRAMES))
        for f, frame in enumerate(frames):
            for i, t in enumerate(normalised(frame)):
                a = round(GAS_ALPHA[0] + (GAS_ALPHA[1] - GAS_ALPHA[0]) * t ** 1.3)
                q = round(t * (STEPS - 1)) / (STEPS - 1)
                c = tuple(round(lo + (hi - lo) * q) for lo, hi in zip(*GAS_TONES))
                img.putpixel((i % size, f * size + i // size), c + (a,))
        write(T + 'steam_' + ('flow' if flowing else 'still') + '.png', img, frametime)


def main():
    for name, kind in LIQUIDS.items():
        liquid(name, kind)
    gas()


if __name__ == '__main__':
    main()
