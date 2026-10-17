# Usage: python3 reactor_sound_gen.py <repo root>  (needs numpy and soundfile). Safe to re-run.
# The Fission Reactor's running loop (FissionReactorSound plays it while the core is critical, louder with its power),
# mono, 44.1 kHz, seamless:
#   - sounds/fission_reactor_run.ogg (4 s): the reactor coolant pumps' motors humming at mains frequency, the pumps'
#     blade-pass tone with a slow beat between the two pumps, and the rush of water through the loop. Every tone has a
#     whole number of cycles in the loop, so it has no seam.
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


def reactor(rng):
    seconds = 4.0
    n = int(RATE * seconds)
    t = np.arange(n) / RATE
    # The motors: mains hum at 60 Hz with its 120 Hz magnetostriction and a little 180 Hz.
    hum = np.sin(2 * np.pi * 60 * t) + 0.7 * np.sin(2 * np.pi * 120 * t) + 0.2 * np.sin(2 * np.pi * 180 * t)
    # The pumps: two impellers at nearly the same blade-pass tone (140 and 140.5 Hz), beating every two seconds, and
    # their shaft turning at 20 Hz under it.
    blades = np.sin(2 * np.pi * 140 * t) + np.sin(2 * np.pi * 140.5 * t) + 0.3 * np.sin(2 * np.pi * 280 * t)
    shaft = np.sin(2 * np.pi * 20 * t)
    # The water: low rushing noise, swelling gently with the pumps' beat.
    rush = norm(lowpass(rng.normal(0, 1, n), 400)) * (0.85 + 0.15 * np.sin(2 * np.pi * 0.5 * t))
    hiss = norm(bandpass(rng.normal(0, 1, n), 1500, 4000))
    mix = 0.35 * norm(hum) + 0.30 * norm(blades) + 0.25 * shaft + 0.55 * rush + 0.05 * hiss
    finish('fission_reactor_run', mix)


if __name__ == '__main__':
    reactor(np.random.default_rng(235))
