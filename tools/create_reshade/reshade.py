"""Re-shade existing Arcforge textures with Create's *technique* only.

Shapes, layouts, materials and colour families stay exactly as they are. What changes is how light
is rendered on them, following what Create does:
  1. hue-shifted, wider ramps (shadows cooler/deeper, highlights paler, up to near-white spec);
  2. flat face regions get a cylinder profile (dark at both edges, lit just left of centre, 2 px steps)
     instead of the flat two-tone split;
  3. top-edge highlight rows become specular rows with a glint every 5 px;
  4. coloured shapes (ports, rings, LEDs, coils, flames) are embossed one step on their outer boundary:
     lit top/left, deeper hue-shifted shadow bottom/right;
  5. large faces get sparse vertical grain streaks (deliberate columns, not noise).

Two entry points: reshade() for art on the machine-steel ramp, reshade_generic() for everything else
(it keeps each texture's own colours). Both leave alpha, outline pixels and any `keep` colours untouched,
and treat the tile edge as continuing, so tiling blocks and connected-texture variants get no edge emboss.
"""
import colorsys
from PIL import Image


def H(s):
    s = s.lstrip('#')
    return (int(s[:2], 16), int(s[2:4], 16), int(s[4:], 16))


# Extended machine-steel ramp: same blue-grey family, hue drifts bluer/deeper in shadow and
# paler/cooler toward the top, and the top end reaches near-white for spec.
X = [H(c) for c in ['#0F1217', '#161A20', '#1D2229', '#252B33', '#2E353D', '#384048', '#434B53',
                    '#4F5860', '#5E6870', '#737E86', '#909BA2', '#B4BEC3', '#DDE5E8']]
# Arcforge ramp role -> index in X
ROLE = {H('#16181B'): 1, H('#2B2F34'): 3, H('#383C42'): 5, H('#40454C'): 6, H('#474C53'): 7,
        H('#575D65'): 8, H('#727982'): 9, H('#959DA6'): 10}   # lit bevels stay near their original brightness
FACE = {H('#40454C'), H('#474C53')}
LIT = H('#727982')
SPEC, GLINT = 10, 11          # spec-row tone and the glint every 5 px; 12 (near-white) is left unused
FACE_LO, FACE_HI = 5, 8       # cylinder profile range: one ramp step per 2 px column pair at most
STEEL_HUE = 212


def lum(c): return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
def chroma(c): return max(c[:3]) - min(c[:3])
def sat(c): return colorsys.rgb_to_hsv(*[q / 255 for q in c[:3]])[1]


def is_outline(c):
    """Item/sprite outline pixels (#0C0D0F and near-black neutrals) are never changed."""
    return c[3] > 0 and lum(c) < 22 and chroma(c) < 14


def is_grey(c):
    return chroma(c) < 14 or (sat(c) < 0.2 and lum(c) > 40 and chroma(c) < 22)


def grey_index(c):
    if c[:3] in ROLE:
        return ROLE[c[:3]]
    L = lum(c)                                     # off-ramp greys: nearest by luminance
    return min(range(len(X)), key=lambda i: abs(lum(X[i]) - L))


def shift(c, up):
    """Create-style hue-shifted step: highlights toward yellow (warm) / cyan (cool) and paler;
    shadows toward red-magenta (warm) / blue-violet (cool) and more saturated."""
    h, s, v = colorsys.rgb_to_hsv(*[q / 255 for q in c[:3]])
    hd = h * 360
    warm = hd < 100 or hd > 300
    if up > 0:
        hd += (8 if hd < 55 or hd > 300 else -8) if warm else (-10 if hd > 190 else 10)
        s *= 0.72; v = min(1, v * 1.2 + 0.06)
    else:
        hd += (-10 if warm else 10)
        s = min(1, s * 1.15 + 0.05); v *= 0.72
    r, g, b = colorsys.hsv_to_rgb((hd % 360) / 360, s, v)
    return (round(r * 255), round(g * 255), round(b * 255))


def profile(n, lo, hi, peak=0.4):
    """Cylinder profile across n columns: lo at both edges, hi just left of centre, in 2 px steps."""
    out = []
    for i in range(n):
        t = (i // 2 * 2 + 0.5) / max(1, n - 1)
        out.append(lo + round((1 - abs(t - peak) / max(peak, 1 - peak)) * (hi - lo)))
    return out


def regions(w, h, px, pred):
    """4-connected regions of pixels matching pred."""
    seen = set(); out = []
    for y in range(h):
        for x in range(w):
            if (x, y) in seen or not pred(px[x, y]):
                continue
            stack = [(x, y)]; seen.add((x, y)); reg = []
            while stack:
                p = stack.pop(); reg.append(p)
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    q = (p[0] + dx, p[1] + dy)
                    if 0 <= q[0] < w and 0 <= q[1] < h and q not in seen and pred(px[q]):
                        seen.add(q); stack.append(q)
            out.append(reg)
    return out


def frames(img, fn):
    """Apply fn to each square frame of an animation strip (height a multiple of width), or to the image."""
    img = img.convert('RGBA'); w, h = img.size
    if h > w and h % w == 0:
        out = Image.new('RGBA', (w, h))
        for f in range(h // w):
            out.paste(fn(img.crop((0, f * w, w, (f + 1) * w))), (0, f * w))
        return out
    return fn(img)


# ---- Machine mode: art on the machine-steel ramp.

def reshade_tile(src, top=False, grain=True, keep=frozenset()):
    src = src.convert('RGBA'); w, h = src.size
    out = src.copy(); px = src.load(); po = out.load()
    NONE = (0, 0, 0, 0)
    get = lambda x, y: px[x, y] if 0 <= x < w and 0 <= y < h else NONE
    fixed = lambda c: c[3] == 0 or is_outline(c) or c[:3] in keep

    # 1. greys onto the extended ramp
    idx = {}
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if not fixed(c) and is_grey(c):
                idx[(x, y)] = grey_index(c)

    # 2. face regions: cylinder profile + grain
    is_face = lambda c: c[3] and c[:3] in FACE
    infc = lambda x, y: not (0 <= x < w and 0 <= y < h) or is_face(px[x, y])   # tile edge = continues (CTM)
    for reg in regions(w, h, px, is_face):
        if len(reg) < 6:
            continue
        xs = [p[0] for p in reg]; ys = [p[1] for p in reg]
        x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
        # 16 px tiles roll across the whole tile width, so every connected-texture variant shares the same
        # columns and seams line up. Bigger textures are model UV atlases: each patch rolls on its own.
        if w <= 16:
            x0 = 0; prof = profile(w, FACE_LO, FACE_HI)
        else:
            prof = profile(x1 - x0 + 1, FACE_LO, FACE_HI)
        for (x, y) in reg:
            if top:   # tops/roofs: no column roll, pillow the plate instead (concentric read)
                i = ROLE[px[x, y][:3]]
                if not infc(x, y - 1) or not infc(x - 1, y): i += 1
                elif not infc(x, y + 1) or not infc(x + 1, y): i -= 1
                idx[(x, y)] = i
                continue
            i = prof[x - x0]
            if grain and x1 - x0 >= 9 and y1 - y0 >= 5 and (x - x0) % 5 == 3 and y0 < y < y1:
                i -= 1
            if not infc(x, y - 1) and lum(get(x, y - 1)) < lum(H('#40454C')):
                i = min(i + 1, 9)   # soft emboss rim
            idx[(x, y)] = i

    # 3. spec rows: lit pixels on a top boundary, in runs of 3+. Above row 0 is what tiling puts there, the
    #    bottom row, so a lit band split across a seam doesn't get a spec line down the seam.
    is_lit = lambda c: c[3] and c[:3] == LIT
    for y in range(h):
        run = []
        for x in range(w + 1):
            c = get(x, y)
            if x < w and is_lit(c) and not is_lit(px[x, (y - 1) % h]):
                run.append(x)
                continue
            if len(run) >= 3:
                for k, rx in enumerate(run):
                    idx[(rx, y)] = GLINT if k % 5 == 2 else SPEC
            run = []

    for (x, y), i in idx.items():
        po[x, y] = X[i] + (px[x, y][3],)

    # 4. accents: one-step emboss on the outer boundary of each coloured shape. Any non-grey pixel belongs to
    #    a shape, so pale hot spots inside a glow panel don't ring their neighbours; only saturated pixels are
    #    embossed, so muted materials (brick) keep their art. Outside the tile counts as the same shape, so
    #    tile edges get no emboss.
    def coloured(x, y):
        if not (0 <= x < w and 0 <= y < h):
            return None
        c = px[x, y]
        return not (fixed(c) or is_grey(c))
    for y in range(h):
        for x in range(w):
            if not coloured(x, y) or sat(px[x, y]) < 0.45:
                continue
            c = px[x, y]
            edge = lambda dx, dy: coloured(x + dx, y + dy) is False
            lit_edge, dark_edge = edge(0, -1) or edge(-1, 0), edge(0, 1) or edge(1, 0)
            n = c[:3]
            if lit_edge and not dark_edge: n = shift(n, 1)
            elif dark_edge and not lit_edge: n = shift(n, -1)
            po[x, y] = n + (c[3],)
    return out


def reshade(img, top=False, grain=True, keep=frozenset()):
    return frames(img, lambda t: reshade_tile(t, top, grain, keep))


# ---- Generic mode: for art that isn't on the machine-steel ramp (items, natural blocks, crops, wood,
# conduits, cylinders, fluids). Keeps every pixel's own colour family and applies only Create's
# technique: more contrast, hue-shifted lights and darks, and a 1-step emboss on shape edges.

def _hsv(c): return colorsys.rgb_to_hsv(*[q / 255 for q in c[:3]])


def _toward(h, target, amount):
    d = ((target - h + 180) % 360) - 180
    return (h + max(-abs(d), min(abs(d), amount)) * (1 if d >= 0 else -1)) % 360


def generic_pivot(img):
    """Contrast pivot: the median luminance of the drawn (non-outline) pixels, over every frame, so an
    animation's frames all shade alike."""
    Ls = sorted(lum(c) for c in img.convert('RGBA').get_flattened_data() if c[3] and not is_outline(c))
    return Ls[len(Ls) // 2] / 255 if Ls else None


def _generic_tile(src, emboss=True, contrast=1.15, keep=frozenset(), pivot=None):
    src = src.convert('RGBA'); w, h = src.size
    out = src.copy(); px = src.load(); po = out.load()

    def inside(x, y):
        if not (0 <= x < w and 0 <= y < h):
            return None                                          # tile edge: neither (keeps tiling)
        c = px[x, y]
        return c[3] > 0 and not is_outline(c)
    m = generic_pivot(src) if pivot is None else pivot
    if m is None:
        return out
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if not inside(x, y) or c[:3] in keep:
                continue
            hh, s, v = _hsv(c); hd = hh * 360
            grey = s < 0.08
            if grey and chroma(c) < 6: hd = STEEL_HUE   # true neutrals shift like steel; faint tints keep their hue
            warm = not grey and (hd < 100 or hd > 300)
            t = max(-1, min(1, (v - m) / 0.4))
            v = max(0, min(1, m + (v - m) * contrast))
            if t > 0:
                hd = _toward(hd, 55 if warm else 195, 8 * t); s *= (1 - 0.22 * t)
            else:
                hd = _toward(hd, 350 if warm else 235, 10 * -t); s = min(1, s + (0.05 if grey else 0.12) * -t)
            if emboss:   # a boundary on one side only; past the tile edge the texture continues
                up = inside(x, y - 1) is False and inside(x, y + 1) is not False
                lf = inside(x - 1, y) is False and inside(x + 1, y) is not False
                dn = inside(x, y + 1) is False and inside(x, y - 1) is not False
                rt = inside(x + 1, y) is False and inside(x - 1, y) is not False
                if (up or lf) and not (dn or rt):
                    v = min(1, v * 1.14 + 0.03); s *= 0.85; hd = _toward(hd, 55 if warm else 195, 6)
                elif (dn or rt) and not (up or lf):
                    v *= 0.8; s = min(1, s * 1.1 + 0.02); hd = _toward(hd, 350 if warm else 235, 6)
            r, g, b = colorsys.hsv_to_rgb(hd / 360, s, v)
            po[x, y] = (round(r * 255), round(g * 255), round(b * 255), c[3])
    return out


def reshade_generic(img, emboss=True, keep=frozenset()):
    m = generic_pivot(img)
    return frames(img, lambda t: _generic_tile(t, emboss, keep=keep, pivot=m))
