"""Synthesize original boss detail effects and register them alongside the existing pack events."""

import io
import json
from pathlib import Path
import shutil
import subprocess
import wave

import numpy as np

ASSETS = Path(__file__).resolve().parent.parent / 'resourcepack/hobbang-pack/assets/hobbang'
RATE = 32000
RANDOM = np.random.default_rng(8317)


def time(seconds):
    return np.arange(round(seconds * RATE)) / RATE


def noise(t, low, high):
    spectrum = np.fft.rfft(RANDOM.normal(size=len(t)))
    frequency = np.fft.rfftfreq(len(t), d=1 / RATE)
    band = np.exp(-0.5 * ((frequency - (low + high) / 2) / ((high - low) / 3)) ** 2)
    signal = np.fft.irfft(spectrum * band, n=len(t))
    return signal / max(np.max(np.abs(signal)), 1e-10)


def thud(t, hz, decay):
    return np.sin(2 * np.pi * hz * (t + 0.014 * (1 - np.exp(-t * 46)))) * np.exp(-t * decay)


def chime(t, tones, decay, delay=0):
    attack = np.maximum(t - delay, 0)
    signal = sum(np.sin(2 * np.pi * tone * attack) / (1 + i * 0.44)
                 * np.exp(-attack * (decay + i * 2)) for i, tone in enumerate(tones))
    return signal * (1 - np.exp(-attack * 440))


def sweep(t, start, end, curve=3):
    phase = end * t + (start - end) * (1 - np.exp(-t * curve)) / curve
    return np.sin(2 * np.pi * phase)


def write_ogg(path, signal, volume, ffmpeg):
    fade_in = np.minimum(1, np.arange(len(signal)) / (RATE * 0.006))
    fade_out = np.minimum(1, np.arange(len(signal) - 1, -1, -1) / (RATE * 0.045))
    signal = signal * fade_in * fade_out
    pcm = np.round(signal / np.max(np.abs(signal)) * volume * 32767).astype('<i2')
    buf = io.BytesIO()
    with wave.open(buf, 'wb') as wav:
        wav.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        wav.writeframes(pcm.tobytes())
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([ffmpeg, '-v', 'error', '-y', '-i', 'pipe:0', '-c:a', 'libvorbis', '-q:a', '5', str(path)],
                   input=buf.getvalue(), check=True)


def effects():
    t = time(0.31)
    yield 'smith.step', 'smith/step', 32, 0.68, (
        0.85 * thud(t, 58, 27) + 0.50 * noise(t, 120, 750) * np.exp(-t * 24)
        + 0.24 * chime(t, (415, 637, 1170), 29, 0.014))
    t = time(0.31)
    yield 'smith.exhale', 'smith/exhale', 14, 0.42, (
        noise(t, 520, 2900) * np.sin(np.pi * t / 0.31) ** 1.5 * np.exp(-t * 4))
    t = time(0.39)
    yield 'colossus.step', 'colossus/step', 36, 0.68, (
        0.85 * thud(t, 44, 21) + 0.47 * noise(t, 160, 610) * np.exp(-t * 23)
        + 0.28 * chime(t, (980, 1591, 2570), 16, 0.020))
    t = time(0.72)
    yield 'colossus.slam', 'colossus/slam', 48, 0.86, (
        0.68 * thud(t, 46, 15) + 0.70 * noise(t, 220, 1560) * np.exp(-t * 12)
        + 0.42 * chime(t, (450, 1040, 1910, 2540), 11)
        + 0.27 * chime(t, (725, 1491, 2433), 17, 0.038))
    t = time(0.46)
    yield 'colossus.spike', 'colossus/spike', 42, 0.74, (
        0.40 * sweep(t, 260, 790, 8) * np.exp(-t * 9)
        + 0.33 * noise(t, 550, 2950) * np.exp(-t * 13)
        + 0.38 * chime(t, (884, 1453, 2420), 10, 0.028))
    t = time(0.79)
    yield 'colossus.rain', 'colossus/rain', 42, 0.63, sum(
        chime(t, (tone, tone * 1.54, tone * 2.39), 12, delay)
        for tone, delay in ((1020, 0), (1410, 0.08), (1760, 0.16), (1220, 0.26)))
    t = time(0.92)
    yield 'colossus.gravity', 'colossus/gravity', 44, 0.70, (
        (0.68 * sweep(t, 292, 43, 5) + 0.29 * noise(t, 80, 485)) * np.sin(np.pi * t / 0.92) ** 1.4
        + 0.25 * thud(np.maximum(0, t - 0.56), 46, 13))
    t = time(0.55)
    yield 'colossus.ring', 'colossus/ring', 42, 0.69, (
        0.40 * sweep(t, 388, 97, 6) * np.exp(-t * 6)
        + 0.26 * chime(t, (593, 984, 1559), 10) + 0.25 * noise(t, 410, 1860) * np.exp(-t * 12))
    t = time(0.62)
    yield 'colossus.cross', 'colossus/cross', 42, 0.67, (
        (0.38 * sweep(t, 382, 1080, 6) + 0.20 * sweep(t, 1300, 640, 8)) * np.exp(-t * 5)
        + 0.32 * chime(t, (808, 1307, 2080), 10, 0.045))
    t = time(10.0)
    envelope = np.minimum(1, t / 0.34) * np.minimum(1, (10 - t) / 0.73)
    yield 'sentinel.gaze.drone', 'sentinel/gaze_drone', 38, 0.39, (
        (0.42 * np.sin(2 * np.pi * 73 * t) + 0.29 * np.sin(2 * np.pi * 110.6 * t)
         + 0.16 * np.sin(2 * np.pi * 147.2 * t + 0.9 * np.sin(2 * np.pi * 0.57 * t))
         + 0.12 * noise(t, 160, 390)) * envelope * (0.82 + 0.18 * np.cos(2 * np.pi * 0.39 * t)))
    t = time(0.54)
    yield 'sentinel.gaze.stun', 'sentinel/gaze_stun', 18, 0.65, (
        0.60 * sweep(t, 763, 102, 12) * np.exp(-t * 13)
        + 0.28 * chime(t, (350, 701, 903), 13, 0.02)
        + 0.35 * thud(t, 57, 22))


def main():
    ffmpeg = shutil.which('ffmpeg')
    if ffmpeg is None:
        raise SystemExit('FFmpeg with libvorbis is required')
    path = ASSETS / 'sounds.json'
    original = path.read_text(encoding='utf-8')
    events = json.loads(original)
    for name, file, distance, volume, signal in effects():
        write_ogg(ASSETS / 'sounds/boss' / (file + '.ogg'), signal, volume, ffmpeg)
        events[name] = {'sounds': [{'name': 'hobbang:boss/' + file,
                                   'attenuation_distance': distance, 'preload': len(signal) < RATE}]}
    path.write_text(json.dumps(events, indent=2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')


if __name__ == '__main__':
    main()
