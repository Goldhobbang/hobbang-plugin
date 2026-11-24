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

    # Abyssal Smith emerges from central basalt altar (heavy stone breach + molten surge)
    t = time(1.45)
    emerge_boom = 0.85 * thud(t, 44, 7.5) + 0.50 * thud(np.maximum(0, t - 0.14), 38, 4.5)
    emerge_rock = 0.62 * noise(t, 95, 760) * np.exp(-t * 4.2) * (1.0 - np.exp(-t * 95))
    emerge_magma = 0.34 * noise(t, 1100, 3200) * np.exp(-np.maximum(0, t - 0.06) * 2.8) * np.minimum(1, t / 0.08)
    emerge_groan = 0.48 * sweep(t, 148, 46, 2.2) * np.exp(-t * 2.6) * np.minimum(1, t / 0.05)
    yield 'smith.emerge', 'smith/emerge', 48, 0.86, emerge_boom + emerge_rock + emerge_magma + emerge_groan

    # Abyssal Smith skyward roar (colossal forge-titan guttural roar + abyssal resonance)
    t = time(3.40)
    roar_attack = np.minimum(1.0, t / 0.18) ** 1.4
    roar_tail = np.where(t < 1.85, 1.0, np.exp(-(t - 1.85) * 1.9))
    roar_env = roar_attack * roar_tail
    f0 = np.where(t < 0.55, 78 + 72 * np.sin(np.pi * 0.5 * (t / 0.55)),
                  150 - 92 * (np.maximum(0.0, t - 0.55) / 2.85) ** 0.85)
    f0 = f0 * (1.0 + 0.045 * np.sin(2 * np.pi * 17.5 * t) + 0.02 * np.sin(2 * np.pi * 5.1 * t))
    phase = 2 * np.pi * np.cumsum(f0) / RATE
    vocal_raw = (1.0 * np.sin(phase) + 0.72 * np.sin(2 * phase + 0.4)
                 + 0.48 * np.sin(3 * phase) + 0.30 * np.sin(4 * phase) + 0.18 * np.sin(5 * phase))
    vocal = np.tanh(2.3 * vocal_raw) * (0.76 + 0.24 * np.sin(2 * np.pi * 23.0 * t))
    throat_noise = 0.55 * noise(t, 260, 1450) * (0.7 + 0.3 * np.sin(2 * np.pi * 21.0 * t))
    furnace_hiss = 0.28 * noise(t, 1600, 3900) * np.exp(-np.maximum(0, t - 0.3) * 0.6)
    blast_sub = 0.65 * thud(np.maximum(0, t - 0.16), 46, 3.2)
    abyss_res = 0.26 * chime(t, (112, 168, 281), 1.4, 0.12)
    yield 'smith.roar', 'smith/roar', 56, 0.92, (0.78 * vocal + throat_noise + furnace_hiss) * roar_env + blast_sub + abyss_res

    # Abyssal Smith Phase 3 sky beam awakening (charge -> colossal impact -> purple plasma hum)
    t = time(3.60)
    charge = np.where(t < 0.72, (t / 0.72) ** 1.6, 0.0) * (
        0.55 * sweep(t, 130, 820, 1.4) + 0.45 * noise(t, 350, 2200))
    impact_t = np.maximum(0.0, t - 0.66)
    impact_mask = (t >= 0.66).astype(float)
    beam_boom = impact_mask * (0.95 * thud(impact_t, 39, 3.8) + 0.68 * noise(t, 240, 2900) * np.exp(-impact_t * 3.6))
    plasma_env = impact_mask * np.minimum(1.0, impact_t / 0.08) * np.where(t < 2.55, 1.0, np.exp(-(t - 2.55) * 2.1))
    mod = 0.85 * np.sin(2 * np.pi * 3.8 * t)
    plasma = (0.48 * np.sin(2 * np.pi * 72 * t) + 0.38 * np.sin(2 * np.pi * 108.5 * t + mod)
              + 0.28 * np.sin(2 * np.pi * 216.0 * t) + 0.18 * np.sin(2 * np.pi * 325.0 * t - mod)
              + 0.22 * noise(t, 450, 1950)) * plasma_env
    yield 'smith.awaken_beam', 'smith/awaken_beam', 60, 0.90, charge + beam_boom + 0.72 * plasma

    # Chrono Scythe Karma Settlement heavy mechanical gear turn (zero anvil clang/ring)
    t = time(0.86)
    grind = 0.38 * noise(t, 110, 560) * (0.55 + 0.45 * np.maximum(0.0, np.sin(2 * np.pi * 24.0 * t))) * np.sin(np.pi * np.minimum(1.0, t / 0.78)) ** 0.7
    motor_hum = 0.32 * (np.sin(2 * np.pi * 58 * t) + 0.55 * np.sin(2 * np.pi * 87 * t)) * np.sin(np.pi * np.minimum(1.0, t / 0.82))
    teeth = np.zeros_like(t)
    for dt_k, pitch_k, amp_k in ((0.01, 1.05, 0.62), (0.13, 0.96, 0.72), (0.27, 0.88, 0.80), (0.44, 0.80, 0.90), (0.63, 0.68, 1.00)):
        tk = np.maximum(0.0, t - dt_k)
        mk = (t >= dt_k).astype(float)
        # Damped low iron block thump (fast decay 38-55 so it clunks mechanically instead of ringing like an anvil)
        teeth += mk * amp_k * (
            0.85 * thud(tk, 64 * pitch_k, 26)
            + 0.46 * noise(t, 130, 520) * np.exp(-tk * 42)
            + 0.24 * np.sin(2 * np.pi * 145 * pitch_k * tk) * np.exp(-tk * 44))
    yield 'weapon.chrono.gear', 'weapon/chrono_gear', 28, 0.88, 0.78 * teeth + grind + motor_hum


def main():
    ffmpeg = shutil.which('ffmpeg')
    if ffmpeg is None:
        raise SystemExit('FFmpeg with libvorbis is required')
    path = ASSETS / 'sounds.json'
    original = path.read_text(encoding='utf-8')
    events = json.loads(original)
    for name, file, distance, volume, signal in effects():
        rel = file if file.startswith('weapon/') else 'boss/' + file
        write_ogg(ASSETS / 'sounds' / (rel + '.ogg'), signal, volume, ffmpeg)
        events[name] = {'sounds': [{'name': 'hobbang:' + rel,
                                   'attenuation_distance': distance, 'preload': len(signal) < RATE}]}
    path.write_text(json.dumps(events, indent=2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')


if __name__ == '__main__':
    main()
