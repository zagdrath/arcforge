"""Potash (docs/TEXTURE_STYLE.md, "Farming", fertilizer items): the Wood Ash heap recoloured tone for tone (by rank, darkest
to lightest) onto a pale lilac-white, so it keeps the dust heap's silhouette and Wood Ash's softened shadow but reads
apart from it (warm grey) and from Basic Slag (slate blue). The lilac is potassium's flame colour.

Run from the repository root: python3 tools/potash_texture_gen.py
"""
from PIL import Image

T = 'src/main/resources/assets/arcforge/textures/item/'
RAMP = ['#8A8492', '#A19AA8', '#B4AEBB', '#C6C1CC', '#DCD8E1', '#F2F0F5']


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def main():
    src = Image.open(T + 'wood_ash.png').convert('RGBA')
    pixels = [src.getpixel((x, y)) for y in range(src.height) for x in range(src.width)]
    tones = sorted({p[:3] for p in pixels if p[3]}, key=sum)
    assert len(tones) == len(RAMP), tones
    to = {tone: rgb(RAMP[i]) for i, tone in enumerate(tones)}
    out = Image.new('RGBA', src.size)
    for i, p in enumerate(pixels):
        out.putpixel((i % src.width, i // src.width), to[p[:3]] + (p[3],) if p[3] else p)
    out.save(T + 'potash.png')


if __name__ == '__main__':
    main()
