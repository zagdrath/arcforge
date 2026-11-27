# Usage: python3 fusion_sound_gen.py <repo root>  (needs numpy and soundfile). Safe to re-run.
# The fusion tier's running loops, mono, 44.1 kHz, seamless (every tone a whole number of cycles in the loop):
#   - sounds/fusion_reactor_run.ogg (4 s, FusionReactorSound while a plasma burns): the coil power supplies' deep 50 Hz
#     hum and its 100/300 Hz harmonics, the gyrotrons' and beams' high whine (a 2 kHz tone with a slow shimmer), and a
#     soft electrical crackle of noise under it.
#   - sounds/cryogenic_plant_run.ogg (4 s, the Cryogenic Plant and Column while they run): the compressors' pulsing
#     thump at 25 Hz, the turbo-expander's whistle and a cold, steady hiss of gas.
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


def finish(name, mix, peak=0.55):
    mix = mix - mix.mean()
    mix = np.tanh(mix * 1.2)
    mix *= peak / np.abs(mix).max()
    sf.write(OUT + name + '.ogg', mix.astype(np.float32), RATE, format='OGG', subtype='VORBIS')
    print('Wrote ' + OUT + name + '.ogg')


def fusion(rng):
    n = int(RATE * 4.0)
    t = np.arange(n) / RATE
    hum = np.sin(2 * np.pi * 50 * t) + 0.8 * np.sin(2 * np.pi * 100 * t) + 0.3 * np.sin(2 * np.pi * 300 * t)
    # The heating's whine, shimmering at 0.75 Hz (three times in the loop).
    whine = (np.sin(2 * np.pi * 2000 * t) + 0.4 * np.sin(2 * np.pi * 2003 * t)) * (0.7 + 0.3 * np.sin(2 * np.pi * 0.75 * t))
    crackle = norm(bandpass(rng.normal(0, 1, n), 800, 5000)) * (rng.random(n) < 0.02)
    rumble = norm(lowpass(rng.normal(0, 1, n), 120))
    mix = 0.45 * norm(hum) + 0.10 * norm(whine) + 0.25 * norm(lowpass(crackle, 3000)) + 0.35 * rumble
    finish('fusion_reactor_run', mix)


def cryogenic(rng):
    n = int(RATE * 4.0)
    t = np.arange(n) / RATE
    # The compressors: a thump at 25 Hz, shaped by its own pulse.
    pulse = np.maximum(0.0, np.sin(2 * np.pi * 25 * t)) ** 3
    thump = lowpass(pulse, 150)
    whistle = np.sin(2 * np.pi * 1250 * t) * (0.8 + 0.2 * np.sin(2 * np.pi * 0.5 * t))
    hiss = norm(bandpass(rng.normal(0, 1, n), 2000, 7000))
    mix = 0.6 * norm(thump) + 0.12 * whistle + 0.30 * hiss + 0.2 * norm(lowpass(rng.normal(0, 1, n), 200))
    finish('cryogenic_plant_run', mix)


if __name__ == '__main__':
    fusion(np.random.default_rng(1707))
    cryogenic(np.random.default_rng(77))
