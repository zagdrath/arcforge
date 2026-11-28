"""Stone chemistry item textures (docs/TEXTURE_STYLE.md, "Natural materials" and "Stone chemistry"), all in the vanilla
texture language of the natural materials:

- Dusts: Cody's dust heap as the Iron Dust carries it (its shadow side already softened by tools/dust_shading_gen.py),
  each tone placed by its brightness onto the dust's own ramp, so every dust keeps the heap's shading and soft shadow.
  Agricultural Lime adds a few deliberate coarse grains (a dark grain with a lit pixel under it). The ramps are chosen
  to read apart from Salt, Quicklime, Silica Dust and Wood Ash (see the preview sheet).
- Magnesium Ingot and Block: Cody's ingot and banded block as Silver's, by rank onto a bright silver-white lighter than
  silver's and with none of its blue.
- Magnesium Nugget, Calcium Carbide, Mica, Olivine and Garnet: Cody's picks (tools/stone_minerals_refs/, sampled from
  his screenshots onto their 16 px grid), each put back on flat brightness levels and recoloured by rank: the nugget
  (his purple one) onto the ingot's tones, Carbide (his grey wedge) onto a dark grey-blue, Mica (his green sheet) onto
  pale bronze, Olivine (his red twin gem) onto bottle green, and
  Garnet (his white twin crystal) onto pyrope.
- Sandpaper: a sheet with a garnet-red grit face (the grit an even, ordered grain), its top-right corner curled to show
  the paper back.
- Foundry Sand: the dust heap in olive-tan.

Every dust it writes is listed in tools/dust_shading_done.txt, so dust_shading_gen.py never lifts it a second time.

Run from the repository root: python3 tools/stone_minerals_texture_gen.py
"""
import os

from PIL import Image

T = 'src/main/resources/assets/arcforge/textures/'
I = T + 'item/'
DONE = 'tools/dust_shading_done.txt'
OUTLINE = (0x0C, 0x0D, 0x0F, 255)


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


# Dust ramps, darkest to lightest (6 anchors; each heap tone lands between them by its brightness).
DUSTS = {
    'soda_ash': ['#C4C7CA', '#D3D6D9', '#E1E3E5', '#EDEFF0', '#F7F8F9', '#FFFFFF'],           # pure white, faintly cool: flatter and brighter than Salt
    'calcium_chloride': ['#8696A8', '#A0AEBE', '#B9C5D2', '#CFD8E3', '#E2E8F0', '#F2F6FB'],   # off-white, faintly blue
    'slaked_lime': ['#B3AEA3', '#C3BFB5', '#D2CEC6', '#DFDCD5', '#EBE9E3', '#F6F5F1'],        # white, softer than Quicklime
    'gypsum': ['#A08E86', '#B8A79F', '#CCBEB6', '#DDD2CB', '#ECE4DE', '#F8F3EF'],             # warm pearl white
    'magnesia': ['#A6A7A7', '#BBBCBB', '#CDCECC', '#DDDEDB', '#EBEBE8', '#F7F7F4'],           # chalk white, a hint of grey
    'magnesium_dust': ['#646A73', '#7E858E', '#989FA8', '#B1B7BF', '#C9CED4', '#E0E3E7'],     # light silver-grey
    'potassium_hydroxide': ['#9A8EB2', '#B2A8C8', '#C7BFDA', '#DAD4E8', '#EAE6F3', '#F9F7FD'],  # white, lilac tint
    'magnesite': ['#A69A72', '#BDB28A', '#D1C8A3', '#E2DABA', '#EFE9D0', '#FAF6E6'],          # pale cream
    'chromite': ['#17110F', '#271D19', '#382A24', '#4B3A31', '#685649', '#A2948A'],           # near-black brown, sheen
    'volcanic_ash': ['#4A4D52', '#5C5F64', '#6E7176', '#808388', '#94979C', '#AAADB1'],       # mid grey, cool
    'agricultural_lime': ['#5E605A', '#7C7E77', '#989A93', '#B1B3AC', '#C7C9C2', '#D9DBD4'],  # grey-white, coarser
    'foundry_sand': ['#4C482C', '#615C38', '#767048', '#8A855A', '#A09B70', '#B8B48A'],       # olive-tan
}
# Agricultural Lime's coarse grains: (x, y) of each dark grain; the pixel under it is lit.
GRAINS = [(7, 5), (9, 7), (5, 8), (10, 9), (7, 10)]

MAGNESIUM = ['#4A4F55', '#62686F', '#6E747B', '#7C828A', '#9CA2A9', '#BEC3C9', '#E2E6EA', '#FFFFFF']
GARNET = ['#2A0610', '#47091C', '#6A0F27', '#8E1A33', '#B32A43', '#D2566A', '#F6C4CC']
OLIVINE = ['#0F2A12', '#163B1A', '#1E4D22', '#2A6430', '#3B7D3E', '#5E9C55', '#B8D9A0']
CARBIDE = ['#15181E', '#22272F', '#2C323B', '#363D48', '#3E4652', '#47505D', '#5A6572', '#66717F', '#737F8D']
BRONZE = ['#5A4426', '#7A5E36', '#9C7C4C', '#BC9C68', '#D8BC8A', '#F0DDB2']
GRIT = ['#4A0E18', '#6E1A26', '#922A36', '#B4464C']
PAPER = ['#8C7A5C', '#B9A684', '#DCCCAA']

# Cody's picked textures (his screenshots, sampled back onto their 16 px grid and cut out of the background):
# name: (brightness levels, ramp, ramp index for each level, smooth the shape).
REFS_DIR = 'tools/stone_minerals_refs/'
REFS = {
    'magnesium_nugget': (6, MAGNESIUM, [0, 2, 3, 4, 6, 7], False),   # his purple nugget, in the ingot's tones
    'calcium_carbide': (7, CARBIDE, [0, 1, 3, 4, 5, 7, 8], False),   # his grey wedge lump, dark grey-blue
    'mica': (6, BRONZE, [0, 1, 2, 3, 4, 5], False),                  # his green sheet, pale bronze
    'olivine': (6, OLIVINE, [0, 1, 2, 3, 4, 5], False),              # his red twin gem, bottle green
    'garnet': (7, GARNET, [0, 1, 2, 3, 4, 5, 6], False),             # his white twin crystal, pyrope
}
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def ref_levels(src, k):
    """Each opaque pixel's brightness level (0 darkest .. k-1): k 1-D k-means clusters of the sampled brightness, which
    puts the screenshot's soft, rescaled tones back on k flat ones. -1 where transparent."""
    import numpy as np
    a = np.asarray(src).astype(float)
    m = a[..., 3] > 0
    L = a[..., 0] * 0.299 + a[..., 1] * 0.587 + a[..., 2] * 0.114
    v = L[m]
    c = np.quantile(v, (np.arange(k) + 0.5) / k)
    for _ in range(50):
        idx = np.argmin(np.abs(v[:, None] - c[None]), 1)
        c = np.array([v[idx == i].mean() if (idx == i).any() else c[i] for i in range(k)])
    c = np.sort(c)
    lev = np.full(L.shape, -1)
    lev[m] = np.argmin(np.abs(L[m][:, None] - c[None]), 1)
    return lev


def neighbours(lev, y, x):
    return [lev[y + dy, x + dx] for dy, dx in N4 if 0 <= y + dy < 16 and 0 <= x + dx < 16]


def despeckle(lev):
    """No lone level inside the sprite: a pixel none of whose (three or four) neighbours shares its level takes their
    most common one."""
    out = lev.copy()
    for y in range(16):
        for x in range(16):
            n = [v for v in neighbours(lev, y, x) if v >= 0]
            if lev[y, x] >= 0 and len(n) >= 3 and lev[y, x] not in n:
                out[y, x] = max(set(n), key=n.count)
    return out


def smooth_shape(lev):
    """Spikes off, notches filled: a pixel with one sprite neighbour or none goes; an empty one with three or more is
    filled with their commonest level. Repeated until nothing changes."""
    for _ in range(4):
        changed = False
        for y in range(16):
            for x in range(16):
                n = neighbours(lev, y, x)
                inside = sum(v >= 0 for v in n)
                if lev[y, x] >= 0 and inside <= 1:
                    lev[y, x] = -1
                    changed = True
                elif lev[y, x] < 0 and inside >= 3:
                    vals = [v for v in n if v >= 0]
                    lev[y, x] = max(set(vals), key=vals.count)
                    changed = True
        if not changed:
            break
    return lev


def outline(lev):
    """Every pixel on the sprite's edge in the darkest level: a clean outline round the smoothed shape."""
    out = lev.copy()
    for y in range(16):
        for x in range(16):
            n = neighbours(lev, y, x)
            if lev[y, x] >= 0 and (len(n) < 4 or any(v < 0 for v in n)):
                out[y, x] = 0
    return out


def paint(lev, ramp, idx):
    out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if lev[y, x] >= 0:
                out.putpixel((x, y), rgb(ramp[idx[lev[y, x]]]) + (255,))
    return out


def load(path):
    return Image.open(path).convert('RGBA')


def tones(im):
    return sorted({p[:3] for p in im.get_flattened_data() if p[3]}, key=lambda c: c[0] * 0.299 + c[1] * 0.587 + c[2] * 0.114)


def lum(c):
    return c[0] * 0.299 + c[1] * 0.587 + c[2] * 0.114


def by_rank(src, ramp):
    """Each tone (darkest to lightest) onto the ramp's tone of the same rank; the ramp must have as many tones."""
    t = tones(src)
    assert len(t) == len(ramp), (len(t), len(ramp))
    to = {c: rgb(ramp[i]) for i, c in enumerate(t)}
    out = src.copy()
    for y in range(src.height):
        for x in range(src.width):
            p = src.getpixel((x, y))
            if p[3]:
                out.putpixel((x, y), to[p[:3]] + (p[3],))
    return out


def interpolate(ramp, t):
    """The colour t (0..1) along the ramp's anchors."""
    cs = [rgb(h) for h in ramp]
    f = t * (len(cs) - 1)
    i = min(int(f), len(cs) - 2)
    u = f - i
    return tuple(round(a + (b - a) * u) for a, b in zip(cs[i], cs[i + 1]))


def by_brightness(src, ramp):
    """Each tone placed by its brightness (between the darkest and lightest tone of src) along the ramp."""
    t = tones(src)
    lo, hi = lum(t[0]), lum(t[-1])
    to = {c: interpolate(ramp, (lum(c) - lo) / (hi - lo)) for c in t}
    out = src.copy()
    for y in range(src.height):
        for x in range(src.width):
            p = src.getpixel((x, y))
            if p[3]:
                out.putpixel((x, y), to[p[:3]] + (p[3],))
    return out


def dusts():
    heap = load(I + 'iron_dust.png')
    made = []
    for name, ramp in DUSTS.items():
        out = by_brightness(heap, ramp)
        if name == 'agricultural_lime':
            dark, lit = rgb(ramp[0]), rgb(ramp[5])
            for x, y in GRAINS:
                if out.getpixel((x, y))[3] and out.getpixel((x, y + 1))[3]:
                    out.putpixel((x, y), dark + (255,))
                    out.putpixel((x, y + 1), lit + (255,))
        out.save(I + name + '.png')
        made.append(name)
    # The heap came softened from the Iron Dust: never lift these again.
    done = set(open(DONE).read().split()) if os.path.exists(DONE) else set()
    with open(DONE, 'w') as f:
        f.write(''.join(n + '\n' for n in sorted(done | {name + '.png' for name in made})))


def magnesium():
    by_rank(load(I + 'silver_ingot.png'), MAGNESIUM).save(I + 'magnesium_ingot.png')
    block = load(T + 'block/silver_block.png')
    t = tones(block)
    lo, hi = lum(t[0]), lum(t[-1])
    ramp = MAGNESIUM[3:]  # the block's face sits in the light half of the ingot's tones
    out = block.copy()
    for y in range(16):
        for x in range(16):
            p = block.getpixel((x, y))
            out.putpixel((x, y), interpolate(ramp, (lum(p[:3]) - lo) / (hi - lo)) + (255,))
    out.save(T + 'block/magnesium_block.png')


def crystals():
    # Cody's picks (tools/stone_minerals_refs/), each worked as its comment in REFS says.
    for name, (k, ramp, idx, clean) in REFS.items():
        lev = ref_levels(load(REFS_DIR + name + '.png'), k)
        if clean:
            lev = outline(despeckle(smooth_shape(lev)))
        elif name in ('calcium_carbide', 'mica'):
            lev = despeckle(lev)
        paint(lev, ramp, idx).save(I + name + '.png')


def draw(rows, palette):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                im.putpixel((x, y), palette[ch])
    return im


def sandpaper():
    # A square sheet, its grit face an ordered grain (g light grain on G ground, d dark grain), the top-right corner
    # turned over to show the paper back (p, P, q), outlined.
    rows = [
        '................',
        '................',
        '..ooooooooo.....',
        '..oGgGdGgGqo....',
        '..oGdGgGdGPqo...',
        '..ogGgGdGgPPqo..',
        '..oGgGdGgGooooo.',
        '..oGdGgGdGgGdGo.',
        '..ogGgGdGgGdGgo.',
        '..oGgGdGgGdGgGo.',
        '..oGdGgGdGgGdGo.',
        '..ogGgGdGgGdGgo.',
        '..oddddddddddd' + 'o.',
        '..ooooooooooooo.',
        '................',
        '................',
    ]
    pal = {'G': rgb(GRIT[2]) + (255,), 'g': rgb(GRIT[3]) + (255,), 'd': rgb(GRIT[1]) + (255,), 'o': OUTLINE,
           'q': rgb(PAPER[2]) + (255,), 'P': rgb(PAPER[1]) + (255,), 'p': rgb(PAPER[0]) + (255,)}
    draw(rows, pal).save(I + 'sandpaper.png')


def main():
    dusts()
    magnesium()
    crystals()
    sandpaper()


if __name__ == '__main__':
    main()
