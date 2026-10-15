# Usage: python3 diesel_sound_gen.py <repo root>  (needs numpy and soundfile). Safe to re-run.
# The Diesel Generator's running loop (sounds/diesel_generator_run.ogg): 3 s, mono, 44.1 kHz, seamless. A V8 at a
# steady speed: 60 firings a second (an eight-cylinder cycle 7.5 times a second, so the loop holds a whole number of
# cycles), each a low thump and a short knock of filtered noise, the cylinders a little uneven so it lopes; under it the
# crankshaft's rumble, and over it a faint turbo whistle. DieselGeneratorSound bends its pitch (0.6 to 1.25) and volume
# with the engine's speed.
import sys
import numpy as np
import soundfile as sf

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = ROOT + '/src/main/resources/assets/arcforge/sounds/diesel_generator_run.ogg'
RATE, SECONDS, FIRING = 44100, 3.0, 60.0
N = int(RATE * SECONDS)
rng = np.random.default_rng(26)
t = np.arange(N) / RATE


def lowpass(x, cutoff):
    # A one-pole low-pass, run over the loop twice so its start carries on from its end (no click at the seam).
    a = np.exp(-2.0 * np.pi * cutoff / RATE)
    y = np.zeros(2 * len(x))
    xx = np.concatenate([x, x])
    acc = 0.0
    for i in range(len(xx)):
        acc = (1.0 - a) * xx[i] + a * acc
        y[i] = acc
    return y[len(x):]


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


# Each firing: where it falls, how hard (eight cylinders, a little uneven, the same each cycle), and a touch of jitter.
strength = np.array([1.0, 0.86, 0.95, 0.8, 0.98, 0.84, 0.92, 0.88])
thump = np.zeros(N)
knock = np.zeros(N)
firings = int(SECONDS * FIRING)
for k in range(firings):
    at = k / FIRING + rng.normal(0.0, 0.0006)
    s = strength[k % 8]
    start = int(round(at * RATE))
    n = int(0.03 * RATE)
    tt = np.arange(n) / RATE
    # The thump: a falling 70 Hz pulse; the knock: a fast-decaying burst of noise.
    pulse = s * np.sin(2.0 * np.pi * 70.0 * tt * (1.0 - 6.0 * tt)) * np.exp(-tt / 0.009)
    burst = s * rng.normal(0.0, 1.0, n) * np.exp(-tt / 0.0035)
    idx = (start + np.arange(n)) % N  # wrapped, so the last firings' tails ring into the start
    thump[idx] += pulse
    knock[idx] += burst

knock = highpass(lowpass(knock, 2600.0), 500.0)
rumble = lowpass(rng.normal(0.0, 1.0, N), 90.0)
# The crank's second harmonic, a whole number of cycles in the loop.
crank = np.sin(2.0 * np.pi * 30.0 * t) * 0.25 + np.sin(2.0 * np.pi * 120.0 * t) * 0.08
whistle = np.sin(2.0 * np.pi * 2210.0 * t + 0.4 * np.sin(2.0 * np.pi * (1.0 / SECONDS) * t)) * 0.012

mix = thump / np.abs(thump).max() * 0.75 + knock / np.abs(knock).max() * 0.35 + rumble / np.abs(rumble).max() * 0.25 + crank + whistle
mix = lowpass(mix, 5000.0)
mix -= mix.mean()
mix = np.tanh(mix * 1.4)
mix *= 0.6 / np.abs(mix).max()
sf.write(OUT, mix.astype(np.float32), RATE, format='OGG', subtype='VORBIS')
print('Wrote ' + OUT)
