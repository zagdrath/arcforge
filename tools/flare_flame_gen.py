# Usage: python3 flare_flame_gen.py <repo root>  (needs Pillow). Safe to re-run.
# The Flare Stack's flame (textures/block/flare_stack/flame.png): 8 frames of 16x32, a tall tongue of flame on a clear
# background (FlareStackRenderer draws it on two crossed sheets, full-bright). Flat tones, no dithering
# (docs/TEXTURE_STYLE.md): a pale core low in the flame, yellow, orange and a deep red rim, the tip swaying and splitting
# into tongues from frame to frame, and a loose wisp breaking off the top.
import math, sys
from PIL import Image

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = ROOT + '/src/main/resources/assets/arcforge/textures/block/flare_stack/flame.png'
W, H, FRAMES = 16, 32, 8
# Rim to core.
TONES = [(0xB0, 0x2C, 0x18), (0xE2, 0x5A, 0x1E), (0xF7, 0x92, 0x28), (0xFF, 0xCC, 0x44), (0xFF, 0xF2, 0xB0)]


def half_width(t, phase):
    # A teardrop: narrow at the nozzle, widest a fifth of the way up, tapering to the tip; the tip breathes with the
    # frame and the edges lick upward.
    reach = 0.88 + 0.12 * math.sin(phase * 2.0)
    if t >= reach:
        return 0.0
    s = t / reach
    body = (s + 0.3) ** 0.5 * (1.0 - s) ** 1.15 / 0.62
    lick = 1.0 + 0.14 * math.sin(s * 11.0 - phase * 2.0)
    return 7.4 * min(1.0, body) * lick


def frame(img, f):
    phase = 2.0 * math.pi * f / FRAMES
    for y in range(H):
        t = (H - 1 - y) / (H - 1)  # 0 at the bottom row, 1 at the top
        hw = half_width(t, phase)
        # The flame sways more the higher it goes.
        centre = 7.5 + 1.8 * t ** 1.5 * math.sin(phase + t * 3.0)
        for x in range(W):
            dx = abs(x - centre)
            if hw <= 0.0 or dx > hw:
                continue
            d = dx / hw
            # Hotter low and in the middle: the core only in the lower half, the rim thicker toward the tip.
            heat = (1.0 - d) * 1.15 - t * 0.75
            tone = 4 if heat > 0.62 else 3 if heat > 0.36 else 2 if heat > 0.12 else 1 if heat > -0.2 else 0
            img.putpixel((x, f * H + y), TONES[tone] + (255,))
    # A wisp breaking off the top, rising a row each frame.
    wy = H - 1 - int(round(0.88 * (H - 1) + (f % 4) * 1.0))
    wx = int(round(7.5 + 2.0 * math.sin(phase + 2.5)))
    for px, py, tone in ((wx, wy, 1), (wx, wy - 1, 0), (wx + 1, wy, 0)):
        if 0 <= px < W and 0 <= py < H and img.getpixel((px, f * H + py))[3] == 0:
            img.putpixel((px, f * H + py), TONES[tone] + (255,))


img = Image.new('RGBA', (W, H * FRAMES), (0, 0, 0, 0))
for f in range(FRAMES):
    frame(img, f)
img.save(OUT)
print('Wrote ' + OUT)
