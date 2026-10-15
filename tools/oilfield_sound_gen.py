# Usage: python3 oilfield_sound_gen.py <repo root>  (needs numpy and soundfile). Safe to re-run.
# The oilfield machines' running loops (MachineLoopSound plays them while each runs), mono, 44.1 kHz, seamless:
#   - sounds/oil_derrick_run.ogg (3 s): the engine house's drive humming, the rotary table grinding with a clunk each
#     quarter turn of the square kelly (it turns once a second), and the drawworks' chain rattling;
#   - sounds/pumpjack_run.ogg (4 s): the electric motor's hum and the belt's whir, the gearbox's drone, and the beam's
#     bearing creaking and the rod string thumping once a stroke (two strokes a loop);
#   - sounds/distillation_array_run.ogg (4 s): the reboiler's low boiling rumble, bubbles popping up through the trays,
#     and a quiet hiss of vapour.
import sys
import numpy as np
import soundfile as sf

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = ROOT + '/src/main/resources/assets/arcforge/sounds/'
RATE = 44100


def lowpass(x, cutoff):
    # A one-pole low-pass, run over the loop twice so its start carries on from its end (no click at the seam).
    a = np.exp(-2.0 * np.pi * cutoff / RATE)
    xx = np.concatenate([x, x])
    y = np.empty_like(xx)
    acc = 0.0
    for i in range(len(xx)):
        acc = (1.0 - a) * xx[i] + a * acc
        y[i] = acc
    return y[len(x):]


def bandpass(x, low, high):
    return lowpass(x, high) - lowpass(x, low)


def norm(x):
    return x / max(1e-9, np.abs(x).max())


def place(buf, at, sound):
    # Adds sound at sample at, wrapped round the loop's end.
    idx = (at + np.arange(len(sound))) % len(buf)
    np.add.at(buf, idx, sound)


def finish(name, mix, peak=0.6):
    mix = mix - mix.mean()
    mix = np.tanh(mix * 1.3)
    mix *= peak / np.abs(mix).max()
    sf.write(OUT + name + '.ogg', mix.astype(np.float32), RATE, format='OGG', subtype='VORBIS')
    print('Wrote ' + OUT + name + '.ogg')


def derrick(rng):
    seconds = 3.0
    n = int(RATE * seconds)
    t = np.arange(n) / RATE
    # The drive: a 30 Hz engine note with its harmonics (whole cycles in the loop).
    drive = np.sin(2 * np.pi * 30 * t) + 0.5 * np.sin(2 * np.pi * 60 * t) + 0.25 * np.sin(2 * np.pi * 90 * t)
    # The table's grind: mid noise swelling and easing four times a turn.
    grind = bandpass(rng.normal(0, 1, n), 250, 1400) * (0.6 + 0.4 * np.sin(2 * np.pi * 4.0 * t) ** 2)
    clunks = np.zeros(n)
    for k in range(int(seconds * 4)):
        m = int(0.08 * RATE)
        tt = np.arange(m) / RATE
        hit = np.sin(2 * np.pi * 140 * tt) * np.exp(-tt / 0.02) + 0.4 * rng.normal(0, 1, m) * np.exp(-tt / 0.006)
        place(clunks, int((k / 4.0 + rng.normal(0, 0.004)) * RATE) % n, hit * (0.8 + 0.2 * rng.random()))
    # The chain: little ticks of bright noise, many a second.
    chain = np.zeros(n)
    for k in range(int(seconds * 22)):
        m = int(0.012 * RATE)
        tt = np.arange(m) / RATE
        place(chain, int(rng.random() * n), rng.normal(0, 1, m) * np.exp(-tt / 0.002) * (0.3 + 0.7 * rng.random()))
    chain = bandpass(chain, 1800, 6000)
    rumble = lowpass(rng.normal(0, 1, n), 120)
    mix = 0.45 * norm(drive) + 0.35 * norm(grind) + 0.55 * norm(clunks) + 0.12 * norm(chain) + 0.25 * norm(rumble)
    finish('oil_derrick_run', lowpass(mix, 7000))


def pumpjack(rng):
    seconds = 4.0
    n = int(RATE * seconds)
    t = np.arange(n) / RATE
    # The motor: a 100 Hz hum with a little 200 and 300, and the belt's whir.
    hum = np.sin(2 * np.pi * 100 * t) + 0.35 * np.sin(2 * np.pi * 200 * t) + 0.15 * np.sin(2 * np.pi * 300 * t)
    whir = bandpass(rng.normal(0, 1, n), 600, 2200) * (0.8 + 0.2 * np.sin(2 * np.pi * 6.0 * t))
    drone = lowpass(rng.normal(0, 1, n), 160)
    # Two strokes a loop: a creak sweeping up as the beam rises, and a soft thump at the bottom of each stroke.
    creak = np.zeros(n)
    thump = np.zeros(n)
    for s in range(2):
        start = int((s * 2.0 + 0.3) * RATE)
        m = int(0.7 * RATE)
        tt = np.arange(m) / RATE
        freq = 380 + 160 * tt / tt[-1]
        phase = 2 * np.pi * np.cumsum(freq) / RATE
        env = np.sin(np.pi * tt / tt[-1]) ** 2
        # A creak is a buzz: the tone gated by a fast, uneven pulse.
        gate = (np.sin(2 * np.pi * 34 * tt + 3 * np.sin(2 * np.pi * 3 * tt)) > 0.3).astype(float)
        place(creak, start, np.sin(phase) * gate * env)
        m2 = int(0.25 * RATE)
        t2 = np.arange(m2) / RATE
        place(thump, int((s * 2.0 + 1.6) * RATE), np.sin(2 * np.pi * 55 * t2) * np.exp(-t2 / 0.06))
    creak = bandpass(creak, 250, 2500)
    mix = 0.4 * norm(hum) + 0.18 * norm(whir) + 0.3 * norm(drone) + 0.3 * norm(creak) + 0.5 * norm(thump)
    finish('pumpjack_run', lowpass(mix, 7000))


def distillation(rng):
    seconds = 4.0
    n = int(RATE * seconds)
    rumble = lowpass(rng.normal(0, 1, n), 140)
    boil = bandpass(rng.normal(0, 1, n), 80, 500)
    # Bubbles: short rising blips, a few dozen a second, some bigger.
    bubbles = np.zeros(n)
    for k in range(int(seconds * 45)):
        f0 = 250 + 650 * rng.random() ** 2
        m = int(RATE * (0.02 + 0.03 * rng.random()))
        tt = np.arange(m) / RATE
        blip = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 2.5 * tt / tt[-1])) / RATE) * np.exp(-tt / (tt[-1] / 3))
        place(bubbles, int(rng.random() * n), blip * (0.25 + 0.75 * rng.random() ** 2))
    hiss = bandpass(rng.normal(0, 1, n), 2500, 8000)
    mix = 0.5 * norm(rumble) + 0.35 * norm(boil) + 0.45 * norm(bubbles) + 0.06 * norm(hiss)
    finish('distillation_array_run', lowpass(mix, 9000), peak=0.55)


derrick(np.random.default_rng(7))
pumpjack(np.random.default_rng(11))
distillation(np.random.default_rng(13))
