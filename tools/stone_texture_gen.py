#!/usr/bin/env python3
# Copyright (c) 2026 Zagdrath
# SPDX-License-Identifier: MIT
"""The natural stone families' block textures (TEXTURE_STYLE.md "Natural stones"): Cody's own stone textures, recoloured
per stone. tools/stone_refs/ holds his 16x16 tiles, recovered from his reference sheets on their pixel grids; each stone
takes a set of them (PLAN, no tile used twice), and every pixel is kept and recoloured by its brightness onto the stone's
ramp, as his ores, raw blocks and dusts are.

    python3 tools/stone_texture_gen.py                 # writes src/main/resources/assets/arcforge/textures/block/stone/
    python3 tools/stone_texture_gen.py --preview out.png"""

import argparse
import os

import numpy as np
from PIL import Image

REFS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "stone_refs")
OUT = "src/main/resources/assets/arcforge/textures/block/stone"


def hexes(*h):
    return np.array([[int(x[i:i + 2], 16) for i in (1, 3, 5)] for x in h], float)


# Seven tones each, darkest to lightest.
RAMPS = {
    "limestone": hexes("#4E4A3E", "#6C6757", "#88826E", "#A29C85", "#B8B29A", "#CCC7B0", "#E0DCC8"),
    "dolomite": hexes("#5A4A48", "#7A6764", "#98837E", "#B29D96", "#C8B4AC", "#DACAC2", "#EADFD8"),
    "slate": hexes("#14171D", "#1C2129", "#252B35", "#2F3641", "#3A424E", "#47505D", "#58626F"),
    "serpentine": hexes("#17221B", "#202F24", "#2A3D2F", "#364D3B", "#445E49", "#57745A", "#738D6F"),
    "sylvinite": hexes("#2A0F0F", "#3E1616", "#531E1C", "#6A2824", "#82332D", "#9A4037", "#B04F43"),
    "kimberlite": hexes("#151B1C", "#1D2627", "#263233", "#2F3E3E", "#3B4C4B", "#4B5E5B", "#5F726D"),
    "pumice": hexes("#5E5C58", "#76746F", "#8E8C86", "#A3A19B", "#B7B5AF", "#C9C7C1", "#DAD8D2"),
    "scoria": hexes("#0D0A09", "#141010", "#1B1615", "#231C1A", "#2C2320", "#372B27", "#443430"),
    "travertine": hexes("#594A35", "#746348", "#8F7B5C", "#A79472", "#BCAA8A", "#CEBEA2", "#DFD3BB"),
}
# Limestone's rough faces (natural, cobbled) sit on a greyer ramp than its worked ones, as a weathered rock does.
ROUGH_LIMESTONE = hexes("#4A4842", "#67645C", "#827E75", "#9B988C", "#B0AEA2", "#C5C2B7", "#DAD8CE")

# Which of Cody's tiles each face of each stone is (tools/stone_refs/<id>.png), with a few worked over:
#   "half:<id>"           his large bricks at half size, laid 2x2: the small bricks of a stone whose sheet had none
#   "frame:<id>:<frame>"  the tile with the bevelled border of another of his tiles (its outer 2 px) laid on it
#   "noface:<id>"         the Kimberlite chiseled panel with its carved face filled with the panel's own fluting
#   "joints:<spec>"       small bricks with their grout drawn in: a mortar row under every 4 px course and a vertical
#                         joint every 8 px, staggered half a brick course to course, the brick right of it lit
#   "grout:<spec>"        a tile whose light grout is taken down below its darkest brick
#   "bond:<spec>"         a grid of small 4 px tiles laid in bond (every other course slid 2 px) with its grout drawn
#                         crisp on the new joints
#   "mortar:<max>:<spec>" the tile's mortar (brightness at or under max) pushed well below its bricks, so it reads crisp
#   "rings:<spec>"        a column end cut into the tile: a bevelled rim, a recessed ring 3 px in, the centre raised
#   "column:<spec>"       a pillar side cut into the tile: a capital and a base band, a lit left edge and shaded right
#                         edge, and two flutes down the shaft
#   "border:<spec>"       the same pillar side without the flutes, for a tile that already has its own vertical lines
#   "seamless:<spec>"     a pillar tile's own edge rows (0-1, 14-15) replaced by the rows inside them, so a stack shows
#                         no line at its joins
#   "shaft:<spec>"        a pillar tile with its own top band (rows 0-2) replaced by the shaft below it
#   "inside:<spec>"       Travertine's pillar with the bright slots between its bars (rows 4-11) taken down
#   "polish:<spec>"       a rough tile ground smooth: blended with its neighbourhood, its contrast halved, a 1 px bevel
# Bricks are always a large-brick tile and small bricks a smaller course than the stone's own bricks.
PLAN = {
    "limestone": dict(natural="k13", cobbled="k40", polished="k23", bricks="k33", small="e_g", pillar="border:mv_b", pillar_top="x0603", chiseled="lt_d"),
    "dolomite": dict(natural="f00", cobbled="f21", polished="f12", bricks="f20", small="bond:m44", pillar="column:k30", pillar_top="rings:a0_3", chiseled="f02"),
    "slate": dict(natural="b01", cobbled="m02", polished="b11", bricks="b21", small="half:b21", pillar="border:bl_pillar", pillar_top="rings:f05", chiseled="bl_chiseled"),
    "serpentine": dict(natural="k12", cobbled="e_e", polished="k22", bricks="k32", small="grout:m41", pillar="border:g_c", pillar_top="g_e", chiseled="x0112"),
    "sylvinite": dict(natural="p_salt", cobbled="a1_1", polished="frame:f01:f12", bricks="mortar:112:b23", small="grout:e_c", pillar="column:seamless:f10", pillar_top="rings:s_d", chiseled="d_3"),
    "kimberlite": dict(natural="p_d2", cobbled="r0_0", polished="b14", bricks="b24", small="joints:half:b24", pillar="column:t_p2", pillar_top="d_2", chiseled="x1014"),
    "pumice": dict(natural="gr_a", cobbled="k05", polished="gr_c", bricks="gr_b", small="half:gr_b", pillar="border:shaft:x0402", pillar_top="rings:k10", chiseled="gr_d"),
    "scoria": dict(natural="b05", cobbled="f35", polished="mv_c", bricks="b25", small="joints:half:b25", pillar="border:dk_pillar", pillar_top="rings:k25", chiseled="mv_d"),
    "travertine": dict(natural="r0_3", cobbled="e_a", polished="polish:b32", bricks="b33", small="joints:half:b33", pillar="border:inside:d_1", pillar_top="b42", chiseled="b34"),
}
# A few tiles need turning or repeating to be a block face: the 8-wide pillar halves are laid twice across, and two
# layered faces are stood on end to read as a column's grain.
# Blocks made from the stones' chemistry (tools/stone_minerals_data_gen.py), not stones: Cody's own picks, sampled from his
# screenshots onto their 16 px grid. Plaster: his soft pink tile, on a warm pearl white. Magnesia Bricks: his blue-green
# bricks, on a chalky grey-white with a buff cast.
EXTRA = {
    "plaster": ("pick_plaster", hexes("#8E877C", "#A9A296", "#C0BAAE", "#D4CFC4", "#E3DFD6", "#EFECE5", "#F8F6F1")),
    "magnesia_bricks": ("pick_bricks", hexes("#6A6660", "#86827A", "#A09C93", "#B7B3AA", "#CAC7BF", "#DBD8D1", "#EAE8E2")),
}

TRANSFORM = {"t_p1": "twice", "t_p2": "twice", "f10": "rot90", "b15": "rot90"}
# Faces given softer contrast and a light smoothing (no lone pixels, a soft blend), and the ramp they use instead.
# Limestone's cobble keeps its crisp stones (only its ramp changes); its natural face is softened too.
SOFT = {("limestone", "natural"): ROUGH_LIMESTONE}
RAMP_FOR = {("limestone", "cobbled"): ROUGH_LIMESTONE}
# Faces whose darkest level (the grout) is lifted this many half tones, so the grout is a touch less dark.
# Every small-brick grout is lifted two half tones (Limestone's three, its tile's grout being darkest), and Sylvinite's
# large-brick mortar two, so the grout reads as grout, not a black line.
LIFT = {(stone, "small"): 2 for stone in ("dolomite", "slate", "serpentine", "sylvinite", "kimberlite", "pumice", "scoria",
                                          "travertine")}
LIFT[("limestone", "small")] = 3
LIFT[("sylvinite", "bricks")] = 2
# Faces moved down the ramp this many half tones (Sylvinite's large bricks, so they sit with the rest of the family
# rather than reading bright red), and faces whose darkest levels are held at least this far up the ramp (Sylvinite's
# natural rock, its deepest reds a touch lighter).
SHIFT = {("sylvinite", "bricks"): -3}
FLOOR = {("sylvinite", "natural"): 4}
PILLAR_JOINS = {"": (True, True), "_upper": (True, False), "_lower": (False, True), "_middle": (False, False)}

FACES = {
    "natural": "{}", "cobbled": "cobbled_{}", "polished": "polished_{}", "bricks": "{}_bricks",
    "small": "small_{}_bricks", "pillar": "{}_pillar", "pillar_top": "{}_pillar_top", "chiseled": "chiseled_{}",
}

MAX_LEVELS = 8


def raw(tile_id):
    a = np.asarray(Image.open(os.path.join(REFS, tile_id + ".png")).convert("RGB")).astype(float)
    t = TRANSFORM.get(tile_id)
    if t == "twice":
        a = np.concatenate([a, a], axis=1)[:, :16]
    elif t == "rot90":
        a = np.rot90(a, -1)
    return a


def luminance(a):
    return a[..., 0] * 0.299 + a[..., 1] * 0.587 + a[..., 2] * 0.114


def half(a):
    """Large bricks at half size: each 2x2 cell takes its darkest pixel where it holds mortar (so the joints survive)
    and its top-left pixel elsewhere; the 8x8 result is laid 2x2."""
    lum = luminance(a)
    mortar = lum <= np.percentile(lum, 18)
    small = np.zeros((8, 8, 3))
    for y in range(8):
        for x in range(8):
            cell = a[2 * y:2 * y + 2, 2 * x:2 * x + 2].reshape(-1, 3)
            cl = lum[2 * y:2 * y + 2, 2 * x:2 * x + 2].reshape(-1)
            cm = mortar[2 * y:2 * y + 2, 2 * x:2 * x + 2].reshape(-1)
            small[y, x] = cell[np.argmin(cl)] if cm.any() else cell[0]
    return np.tile(small, (2, 2, 1))


def frame(a, src):
    """Lays src's bevelled border (its outer two rows and columns, as brightness offsets from its field) on a."""
    out = luminance(a).copy()
    sl = luminance(src)
    field = np.median(sl[3:13, 3:13])
    ring = np.zeros((16, 16), bool)
    ring[:2, :] = ring[-2:, :] = ring[:, :2] = ring[:, -2:] = True
    out[ring] = np.median(out[3:13, 3:13]) + (sl[ring] - field)
    return out


def noface(a):
    """The carved face cleared from the panel (rows 4-10, columns 3-12), the fluting below it carried up in its place."""
    a = a.copy()
    rows = [11, 12, 13, 12]
    for k, y in enumerate(range(4, 11)):
        a[y, 3:13] = a[rows[k % len(rows)], 3:13]
    return a


def crisp(lum):
    """A mortar value well under the bricks: the darkest pixel less a third of the tile's spread."""
    return lum.min() - 0.3 * (np.percentile(lum, 95) - np.percentile(lum, 5))


def joints(lum):
    """Grout for small bricks: the courses laid in bond (every other 4 px course slid 4 px, so the bricks' own ends
    stagger too), the bottom row of every course mortar, and each course a vertical joint every 8 px (at x 0, 8 on even
    courses and 4, 12 on odd ones), the pixel right of a joint lit as a brick's left edge."""
    lum = lum.copy()
    for course in (1, 3):       # lay the courses in bond: every other one slid half a brick
        lum[course * 4:course * 4 + 4] = np.roll(lum[course * 4:course * 4 + 4], 4, axis=1)
    mortar = crisp(lum)
    lit = np.percentile(lum, 80)
    for course in range(4):
        y0 = course * 4
        lum[y0 + 3, :] = mortar
        for x in range((course % 2) * 4, 16, 8):
            lum[y0:y0 + 3, x] = mortar
            lum[y0:y0 + 3, (x + 1) % 16] = np.maximum(lum[y0:y0 + 3, (x + 1) % 16], lit)
    return lum


def grout(lum):
    """Light grout turned dark: the levels above the widest brightness gap in the tile's upper half are the grout; they're
    moved just under the darkest brick, keeping their own small differences, so the grout reads as a dark joint."""
    values = np.unique(np.round(lum))
    upper = values[values >= np.median(lum)]
    gaps = np.diff(upper)
    threshold = upper[np.argmax(gaps) + 1] if len(gaps) else upper[-1]
    is_grout = lum >= threshold - 0.5
    bricks = lum[~is_grout]
    base = bricks.min() - 10
    out = lum.copy()
    out[is_grout] = base + (lum[is_grout] - threshold) * 0.25
    return out


def bond(lum):
    """A grid of 4 px tiles (grout on rows and columns 3, 7, 11, 15) in bond: courses 2 and 4 slid 2 px, then the grout
    drawn crisp where it now lies (each course's bottom row, its joints at x = 3 mod 4, or 1 mod 4 on slid courses)."""
    lum = lum.copy()
    mortar = crisp(lum)
    for course in (1, 3):
        lum[course * 4:course * 4 + 4] = np.roll(lum[course * 4:course * 4 + 4], 2, axis=1)
    for course in range(4):
        y0 = course * 4
        lum[y0 + 3, :] = mortar
        for x in range(1 if course % 2 else 3, 16, 4):
            lum[y0:y0 + 3, x] = mortar
    return lum


def mortar(lum, limit):
    """The tile's mortar (brightness at or under limit) set crisp, well under its bricks."""
    lum = lum.copy()
    lum[lum <= limit + 0.5] = crisp(lum)
    return lum


def rings(lum):
    """A column end, as vanilla's pillar tops: a 1 px rim lit top/left and dark bottom/right, a recessed ring 3 px in
    (shadow on its top/left lip, light on its bottom/right lip), and the centre inside it raised a little."""
    lum = lum.copy()
    spread = np.percentile(lum, 95) - np.percentile(lum, 5)
    lit, dark = max(0.45 * spread, 24.0), min(-0.5 * spread, -26.0)   # at least about two half tones, on a soft tile too
    lum[0, :] += lit
    lum[:, 0] += lit
    lum[15, 1:] += dark
    lum[1:, 15] += dark
    for i in range(3, 13):
        lum[3, i] += dark
        lum[i, 3] += dark
        lum[12, i] += lit
        lum[i, 12] += lit
    lum[4:12, 4:12] += max(0.15 * spread, 8.0)
    return lum


def _spread(lum):
    return np.percentile(lum, 95) - np.percentile(lum, 5)


def column(lum, flutes=True, top=True, bottom=True):
    """A pillar's side, as vanilla's: a capital (rows 0-1, lit over shade) and a base (rows 14-15, lit over darkest), a lit
    left edge (columns 0-1) and shaded right edge (14-15), and two flutes (a shaded groove with a lit edge right of it)
    at columns 5 and 10, each step at least about two half tones."""
    lum = lum.copy()
    d = max(0.5 * _spread(lum), 28.0)
    lum[:, 0] += d
    lum[:, 1] += d * 0.5
    lum[:, 14] -= d * 0.6
    lum[:, 15] -= d
    for x in ((5, 10) if flutes else ()):
        lum[2:14, x] -= d
        lum[2:14, x + 1] += d * 0.6
    if top:
        lum[0, :] += d
        lum[1, :] -= d * 0.7
    if bottom:
        lum[14, :] += d * 0.6
        lum[15, :] -= d * 1.1
    return lum


def inside(lum):
    """The bright slots between the Travertine pillar's bars (rows 4-11, brightness 120 and over) taken down by 55."""
    lum = lum.copy()
    band = lum[4:12]
    band[band >= 120] -= 55
    return lum


def polish(lum):
    """A rough tile ground smooth: half blended with its 3x3 neighbourhood (twice), its contrast halved about its mean,
    and a 1 px bevel (lit top and left, shaded bottom and right) round the block."""
    d = max(0.35 * _spread(lum), 16.0)
    for _ in range(2):
        blur = sum(np.roll(np.roll(lum, dy, 0), dx, 1) for dy in (-1, 0, 1) for dx in (-1, 0, 1)) / 9
        lum = lum * 0.5 + blur * 0.5
    lum = lum.mean() + (lum - lum.mean()) * 0.5
    lum[0, :] += d
    lum[:, 0] += d
    lum[15, 1:] -= d
    lum[1:, 15] -= d
    return lum


def load(spec, caps=(True, True)):
    """The tile's brightness, worked as the spec says. caps: whether a pillar side shows its capital (top) and its
    base (bottom); a pillar joined to the next one along its axis leaves that end plain."""
    parts = spec.split(":")
    if parts[0] == "flip":
        return load(spec[len("flip:"):], caps)[:, ::-1].copy()
    if parts[0] == "smooth":
        return smooth(load(spec[len("smooth:"):], caps))
    if parts[0] == "seamless":
        lum = load(spec[len("seamless:"):], caps)
        lum[0:2] = lum[2:4]
        lum[14:16] = lum[12:14]
        return lum
    if parts[0] == "shaft":
        lum = load(spec[len("shaft:"):], caps)
        lum[0:3] = lum[3:6]
        return lum
    if parts[0] == "bond":
        return bond(load(spec[len("bond:"):]))
    if parts[0] == "mortar":
        return mortar(load(":".join(parts[2:])), float(parts[1]))
    if parts[0] == "rings":
        return rings(load(spec[len("rings:"):]))
    if parts[0] in ("column", "border"):
        return column(load(spec[len(parts[0]) + 1:], caps), flutes=parts[0] == "column", top=caps[0], bottom=caps[1])
    if parts[0] in ("inside", "polish"):
        return {"inside": inside, "polish": polish}[parts[0]](load(spec[len(parts[0]) + 1:], caps))
    if parts[0] == "joints":
        return joints(load(spec[len("joints:"):]))
    if parts[0] == "grout":
        return grout(load(spec[len("grout:"):]))
    if parts[0] == "half":
        return luminance(half(raw(parts[1])))
    if parts[0] == "frame":
        return frame(raw(parts[1]), raw(parts[2]))
    if parts[0] == "noface":
        return luminance(noface(raw(parts[1])))
    return luminance(raw(spec))


def smooth(lum):
    """Ground smooth as polish(), but seamless: no bevel, so a wall of it reads as one surface (plaster)."""
    for _ in range(2):
        blur = sum(np.roll(np.roll(lum, dy, 0), dx, 1) for dy in (-1, 0, 1) for dx in (-1, 0, 1)) / 9
        lum = lum * 0.5 + blur * 0.5
    return lum.mean() + (lum - lum.mean()) * 0.5


def soften(lum):
    """A light, wrapping blend with the 3x3 neighbourhood (a third), for a smoother rock."""
    blur = sum(np.roll(np.roll(lum, dy, 0), dx, 1) for dy in (-1, 0, 1) for dx in (-1, 0, 1)) / 9
    return lum * 0.65 + blur * 0.35


def levels(lum):
    """The tile's brightness levels: its own distinct values, or (from a JPEG sheet) at most MAX_LEVELS 1-D k-means
    clusters of them. Returns each pixel's level and each level's mean brightness."""
    flat = lum.reshape(-1)
    values = np.unique(np.round(flat))
    if len(values) <= MAX_LEVELS:
        centres = values
    else:
        centres = np.quantile(flat, (np.arange(MAX_LEVELS) + 0.5) / MAX_LEVELS)
        for _ in range(30):
            idx = np.argmin(np.abs(flat[:, None] - centres[None, :]), axis=1)
            centres = np.array([flat[idx == k].mean() if np.any(idx == k) else centres[k] for k in range(len(centres))])
        centres = np.unique(np.round(centres))
    idx = np.argmin(np.abs(flat[:, None] - centres[None, :]), axis=1)
    return idx.reshape(lum.shape), centres


def despeckle(pos):
    """No lone level: a pixel none of whose four neighbours shares its level takes their most common one."""
    out = pos.copy()
    for y in range(16):
        for x in range(16):
            nb = [pos[y, (x + 1) % 16], pos[y, (x - 1) % 16], pos[(y + 1) % 16, x], pos[(y - 1) % 16, x]]
            if pos[y, x] not in nb:
                out[y, x] = max(set(nb), key=nb.count)
    return out


def recolour(lum, ramp, soft=False, lift=0, shift=0, floor=0):
    """Every pixel kept, its brightness level placed on the stone's ramp (stepped to 13 half tones), centred on the
    ramp's middle with the tile's own contrast: a soft tile stays soft, a crisp one uses the whole ramp. A soft face is
    blended a little first, its contrast taken down by a third and its lone pixels merged."""
    if soft:
        lum = soften(lum)
    idx, centres = levels(lum)
    half_tones = np.array([ramp[i // 2] if i % 2 == 0 else (ramp[i // 2] + ramp[i // 2 + 1]) / 2 for i in range(13)])
    lo, hi = centres.min(), centres.max()
    span = max(hi - lo, 1.0)
    per_step = max(span / 12.0, 7.0) * (1.5 if soft else 1.0)
    mid = (lo + hi) / 2
    pos = np.clip(np.round(6 + (centres - mid) / per_step), 0, 12).astype(int)[idx]
    if soft:
        pos = despeckle(pos)
    if shift:
        pos = np.clip(pos + shift, 0, 12)
    if lift:
        pos = np.where(pos == pos.min(), pos + lift, pos)
    if floor:
        pos = np.maximum(pos, floor)
    return np.clip(np.round(half_tones[pos]), 0, 255).astype(np.uint8)


def build():
    out = {}
    for stone, faces in PLAN.items():
        for face, tile in faces.items():
            soft = SOFT.get((stone, face))
            ramp = soft if soft is not None else RAMP_FOR.get((stone, face), RAMPS[stone])
            key = (stone, face)
            if face == "pillar":
                # The pillar side in its four joins: alone (capital and base), the top of a stack (capital only), the
                # bottom of a stack (base only) and the middle (neither); see StonePillarBlock.
                for suffix, caps in PILLAR_JOINS.items():
                    out[FACES[face].format(stone) + suffix] = Image.fromarray(
                            recolour(load(tile, caps), ramp, soft is not None, LIFT.get(key, 0), SHIFT.get(key, 0), FLOOR.get(key, 0)))
                continue
            out[FACES[face].format(stone)] = Image.fromarray(
                    recolour(load(tile), ramp, soft is not None, LIFT.get(key, 0), SHIFT.get(key, 0), FLOOR.get(key, 0)))
    for name, (tile, ramp) in EXTRA.items():
        out[name] = Image.fromarray(recolour(load(tile), ramp))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()
    textures = build()
    os.makedirs(args.out, exist_ok=True)
    for name, im in textures.items():
        im.save(os.path.join(args.out, name + ".png"))
    print(f"{len(textures)} textures")


if __name__ == "__main__":
    main()
