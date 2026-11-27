"""Softens the shadow side of every dust (docs/TEXTURE_STYLE.md, "Dusts"): the dust heap's bottom-right is its darkest
tone in a broad mass and along its outline, which read too dark and cut off the heap's shading. This lifts it, on every
item drawn on the dust heap (the same silhouette as the Iron Dust), keeping each dust's own colours:

- the darkest tone inside the heap (not on its outline) moves 65% of the way to the dust's next tone up, so the
  shading steps down into the shadow instead of dropping into it;
- the darkest tone on the bottom and right outline moves 30% of the way, so the edge stays the darkest line round the
  heap but no longer reads as black.

The structure (which pixels are the shadow and which the outline) comes from the Iron Dust's tone map, so every dust
changes the same pixels. It remembers every dust it has lifted in DONE (tools/dust_shading_done.txt) so it never lifts
one twice; Cinnabar Dust is listed there already, since the mercury line's generator lifts it itself. A dust added
later is lifted the first time this is run after it.

Run from the repository root: python3 tools/dust_shading_gen.py
"""
import os

from PIL import Image

T = 'src/main/resources/assets/arcforge/textures/item/'
DONE = 'tools/dust_shading_done.txt'
INNER_LIFT, EDGE_LIFT = 0.65, 0.30

# The Iron Dust heap's tone map (0 darkest): where its shadow (0 inside) and its dark outline (0 on the edge) are.
HEAP = [
    '................',
    '................',
    '................',
    '.......11.......',
    '......1231......',
    '.....134300.....',
    '....13432400....',
    '...1333430100...',
    '..124320420300..',
    '..132343200100..',
    '..003432030000..',
    '...0032010000...',
    '....00000000....',
    '......0000......',
    '................',
    '................',
]


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def cells():
    return {(x, y): int(ch) for y, row in enumerate(HEAP) for x, ch in enumerate(row) if ch != '.'}


def is_dust(im):
    heap = cells()
    return im.size == (16, 16) and all((im.getpixel((x, y))[3] > 0) == ((x, y) in heap) for y in range(16) for x in range(16))


def soften(im):
    """The dust with its shadow lifted (see the module notes)."""
    heap = cells()
    edge = {p for p in heap if any((p[0] + dx, p[1] + dy) not in heap for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    colours = sorted({im.getpixel(p)[:3] for p in heap}, key=lum)
    darkest = colours[0]
    # The dust's next tone up: what its own rank-1 pixels are, else the next lighter colour it has.
    ones = [im.getpixel(p)[:3] for p, r in heap.items() if r == 1 and im.getpixel(p)[:3] != darkest]
    up = max(set(ones), key=ones.count) if ones else (colours[1] if len(colours) > 1 else darkest)
    out = im.copy()
    for p, r in heap.items():
        c = im.getpixel(p)
        if r != 0 or c[:3] != darkest:
            continue
        out.putpixel(p, mix(darkest, up, EDGE_LIFT if p in edge else INNER_LIFT) + (c[3],))
    return out


def main():
    done = set(open(DONE).read().split()) if os.path.exists(DONE) else set()
    for name in sorted(os.listdir(T)):
        if not name.endswith('.png') or name in done:
            continue
        im = Image.open(T + name).convert('RGBA')
        if is_dust(im):
            soften(im).save(T + name)
            done.add(name)
    with open(DONE, 'w') as f:
        f.write('\n'.join(sorted(done)) + '\n')


if __name__ == '__main__':
    main()
