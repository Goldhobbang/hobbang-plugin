"""Generate One-Punch Glove 2D icon, 3D boxing glove model/texture, and hit SFX."""

import io
import json
from pathlib import Path
import shutil
import subprocess
import wave

import numpy as np
from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / 'resourcepack/hobbang-pack/assets/hobbang'
MC_ITEMS = Path(__file__).resolve().parent.parent / 'resourcepack/hobbang-pack/assets/minecraft/items'
RATE = 32000
RNG = np.random.default_rng(1999)

# 16x16 pixel-art icon matching the attached red boxing glove reference
# Palette:
# . = transparent
# K = #000000 (outline)
# D = #8A0000 (deep shadow)
# S = #B80000 (mid shadow)
# R = #EE0000 (main crimson)
# H = #FF2A00 (bright red highlight)
# O = #FF6A00 (orange knuckle)
# Y = #FFAE00 (gold knuckle highlight)
ICON_GRID = [
    "................",
    "................",
    "..KKKKK.........",
    ".KRRRRRK........",
    ".KRRRRRRKKKKKK..",
    "KRRRRRRRRRRRYYK.",
    "KRRRRRRRRRRSOOK.",
    "KRRRRRRRRRRSYYK.",
    "KRRRRRRRRRRSOK..",
    "KRRRRRRSSRRYYK..",
    "KRRSSSRSSSRSSSK.",
    ".KKSSSSKKKRRSSK.",
    "...KKKK..KSRSSK.",
    "..........KKKK..",
    "................",
    "................",
]

PALETTE = {
    '.': (0, 0, 0, 0),
    'K': (10, 10, 12, 255),
    'D': (138, 0, 0, 255),
    'S': (184, 0, 0, 255),
    'R': (238, 0, 0, 255),
    'H': (255, 48, 0, 255),
    'O': (255, 110, 0, 255),
    'Y': (255, 178, 12, 255),
}


def build_icon():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(ICON_GRID):
        for x, ch in enumerate(row):
            px[x, y] = PALETTE[ch]
    out = ASSETS / 'textures/item/weapon/onepunch_glove_icon.png'
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)

    icon_model = {
        'parent': 'minecraft:item/handheld',
        'textures': {
            'layer0': 'hobbang:item/weapon/onepunch_glove_icon',
        },
    }
    mpath = ASSETS / 'models/item/weapon/onepunch_glove_icon.json'
    mpath.parent.mkdir(parents=True, exist_ok=True)
    mpath.write_text(json.dumps(icon_model, indent=1) + '\n', encoding='utf-8', newline='\n')


def build_hand_texture_and_model():
    # 64x64 texture atlas with clean 16x16 regions (in MC UV, 0..16 maps 0..64 so 1 UV unit = 4px)
    # Region (0,0)-(32,32): main crimson glove leather
    # Region (32,0)-(64,32): dark crimson cuff / wrist shadow
    # Region (0,32)-(32,64): front knuckle cap with orange-gold highlights
    # Region (32,32)-(64,64): thumb / bottom shadow
    tex = Image.new('RGBA', (64, 64), (210, 0, 0, 255))
    px = tex.load()

    for y in range(64):
        for x in range(64):
            # Subtle leather grain
            grain = int(RNG.integers(-8, 9))
            if x < 32 and y < 32:
                # Main bright red leather with slight top-to-bottom shading
                shade = int((16 - y) * 0.9)
                r = max(0, min(255, 232 + shade + grain))
                g = max(0, min(255, 10 + max(0, shade // 2)))
                b = max(0, min(255, 8))
                # Dark seam border at edges
                if x in (0, 31) or y in (0, 31):
                    r, g, b = 135, 0, 0
                px[x, y] = (r, g, b, 255)
            elif x >= 32 and y < 32:
                # Cuff: deeper crimson with ribbed wrist band
                rib = -18 if (y % 6 < 2) else 6
                r = max(0, min(255, 165 + rib + grain))
                g = max(0, min(255, 6))
                b = max(0, min(255, 6))
                if x in (32, 63) or y in (0, 31):
                    r, g, b = 95, 0, 0
                px[x, y] = (r, g, b, 255)
            elif x < 32 and y >= 32:
                # Knuckle front: crimson base + 3 bright orange-gold knuckle highlight bars
                r, g, b = 220 + grain, 8, 6
                ly = y - 32
                lx = x
                if 5 <= ly <= 11 and (4 <= lx <= 9 or 13 <= lx <= 18 or 22 <= lx <= 27):
                    r, g, b = 255, 128, 12
                    if 6 <= ly <= 9:
                        r, g, b = 255, 185, 25
                elif ly > 24:
                    r, g, b = 155, 0, 0
                if x in (0, 31) or y in (32, 63):
                    r, g, b = 120, 0, 0
                px[x, y] = (max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)), 255)
            else:
                # Thumb / under-shadow
                r = max(0, min(255, 185 + grain))
                px[x, y] = (r, 4, 4, 255)

    tpath = ASSETS / 'textures/item/weapon/onepunch_glove/hand.png'
    tpath.parent.mkdir(parents=True, exist_ok=True)
    tex.save(tpath)

    uv_main = [0.25, 0.25, 7.75, 7.75]
    uv_cuff = [8.25, 0.25, 15.75, 7.75]
    uv_knuckle = [0.25, 8.25, 7.75, 15.75]
    uv_thumb = [8.25, 8.25, 15.75, 15.75]

    def box(frm, to, faces):
        return {'from': frm, 'to': to, 'faces': {k: {'texture': '#t', 'uv': v} for k, v in faces.items()}}

    elements = [
        # Wrist cuff
        box([4.4, 4.4, 9.8], [11.6, 11.6, 15.0], {
            'north': uv_cuff, 'south': uv_cuff, 'east': uv_cuff, 'west': uv_cuff, 'up': uv_cuff, 'down': uv_cuff,
        }),
        # Main fist body
        box([3.3, 3.6, 2.4], [12.7, 12.2, 10.2], {
            'north': uv_knuckle, 'south': uv_cuff, 'east': uv_main, 'west': uv_main, 'up': uv_main, 'down': uv_thumb,
        }),
        # Front rounded knuckle cap
        box([3.9, 4.2, 1.2], [12.1, 11.6, 2.6], {
            'north': uv_knuckle, 'south': uv_main, 'east': uv_knuckle, 'west': uv_knuckle, 'up': uv_knuckle, 'down': uv_thumb,
        }),
        # Top knuckle bevel
        box([4.1, 11.8, 2.8], [11.9, 12.8, 8.8], {
            'north': uv_knuckle, 'south': uv_main, 'east': uv_main, 'west': uv_main, 'up': uv_main, 'down': uv_main,
        }),
        # Tucked thumb
        box([1.8, 3.8, 3.2], [3.6, 8.4, 8.8], {
            'north': uv_thumb, 'south': uv_thumb, 'east': uv_thumb, 'west': uv_main, 'up': uv_main, 'down': uv_thumb,
        }),
    ]

    hand_model = {
        'credit': 'make_onepunch (HobbangEnhance)',
        'texture_size': [64, 64],
        'textures': {
            't': 'hobbang:item/weapon/onepunch_glove/hand',
            'particle': 'hobbang:item/weapon/onepunch_glove_icon',
        },
        'elements': elements,
        'display': {
            'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0.0, 2.0, 2.2], 'scale': [0.56, 0.56, 0.56]},
            'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0.0, 2.0, 2.2], 'scale': [0.56, 0.56, 0.56]},
            'firstperson_righthand': {'rotation': [-8, 10, 0], 'translation': [1.5, 2.0, -1.0], 'scale': [0.55, 0.55, 0.55]},
            'firstperson_lefthand': {'rotation': [-8, -10, 0], 'translation': [1.5, 2.0, -1.0], 'scale': [0.55, 0.55, 0.55]},
            'head': {'rotation': [0, 0, 0], 'translation': [0, 10, 0], 'scale': [0.65, 0.65, 0.65]},
            'fixed': {'rotation': [0, 90, 0], 'translation': [0, 0, 0], 'scale': [0.65, 0.65, 0.65]},
        },
    }
    hpath = ASSETS / 'models/item/weapon/onepunch_glove_hand.json'
    hpath.parent.mkdir(parents=True, exist_ok=True)
    hpath.write_text(json.dumps(hand_model, indent=1) + '\n', encoding='utf-8', newline='\n')


def register_item_dispatch():
    MC_ITEMS.mkdir(parents=True, exist_ok=True)
    ns_path = MC_ITEMS / 'netherite_sword.json'
    onepunch_case = {
        'when': 'hobbang:onepunch_glove',
        'model': {
            'type': 'minecraft:select',
            'property': 'minecraft:display_context',
            'cases': [
                {
                    'when': ['gui', 'ground', 'fixed', 'on_shelf'],
                    'model': {
                        'type': 'minecraft:model',
                        'model': 'hobbang:item/weapon/onepunch_glove_icon',
                    },
                }
            ],
            'fallback': {
                'type': 'minecraft:model',
                'model': 'hobbang:item/weapon/onepunch_glove_hand',
            },
        },
    }
    if ns_path.exists():
        data = json.loads(ns_path.read_text(encoding='utf-8'))
        cases = data['model']['cases']
        cases = [c for c in cases if c.get('when') != 'hobbang:onepunch_glove']
        cases.append(onepunch_case)
        data['model']['cases'] = cases
    else:
        data = {
            'model': {
                'type': 'minecraft:select',
                'property': 'minecraft:custom_model_data',
                'index': 0,
                'cases': [onepunch_case],
                'fallback': {
                    'type': 'minecraft:model',
                    'model': 'minecraft:item/netherite_sword',
                },
            }
        }
    ns_path.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8', newline='\n')


def band_noise(t, low, high):
    spectrum = np.fft.rfft(RNG.normal(size=len(t)))
    freq = np.fft.rfftfreq(len(t), d=1 / RATE)
    band = np.exp(-0.5 * ((freq - (low + high) / 2) / ((high - low) / 3)) ** 2)
    sig = np.fft.irfft(spectrum * band, n=len(t))
    return sig / max(np.max(np.abs(sig)), 1e-10)


def build_hit_sound():
    ffmpeg = shutil.which('ffmpeg')
    if ffmpeg is None:
        raise SystemExit('FFmpeg with libvorbis is required')

    dur = 1.45
    t = np.arange(round(dur * RATE)) / RATE

    # 1. Supersonic air-snap crack (0..50ms)
    crack_env = np.exp(-t * 62)
    crack_sweep = np.sin(2 * np.pi * (120 * t + (1900 - 120) * (1 - np.exp(-t * 48)) / 48))
    crack = (0.65 * crack_sweep + 0.75 * band_noise(t, 900, 6200)) * crack_env

    # 2. Colossal sub-bass chest thump (58Hz -> 24Hz) with soft overdrive
    sub_phase = 24 * t + (62 - 24) * (1 - np.exp(-t * 14)) / 14
    sub = np.tanh(2.1 * np.sin(2 * np.pi * sub_phase)) * np.exp(-t * 4.2)

    # 3. Heavy low-mid shockwave punch + delayed echo tail
    mid_boom = band_noise(t, 60, 420) * np.exp(-t * 6.0)
    blast_wind = band_noise(t, 180, 2400) * np.minimum(1.0, t / 0.03) * np.exp(-t * 2.5)
    t_echo1 = np.maximum(0.0, t - 0.13)
    echo1 = (t >= 0.13) * 0.40 * band_noise(t, 140, 1400) * np.exp(-t_echo1 * 4.2)
    t_echo2 = np.maximum(0.0, t - 0.28)
    echo2 = (t >= 0.28) * 0.24 * band_noise(t, 90, 900) * np.exp(-t_echo2 * 3.1)

    sig = 1.05 * crack + 1.25 * sub + 0.75 * mid_boom + 0.65 * blast_wind + echo1 + echo2
    sig = np.tanh(sig * 1.15)  # warm limiting for maximum punchiness

    fade_in = np.minimum(1.0, np.arange(len(sig)) / (RATE * 0.002))
    fade_out = np.minimum(1.0, np.arange(len(sig) - 1, -1, -1) / (RATE * 0.08))
    sig = sig * fade_in * fade_out
    pcm = np.round(sig / np.max(np.abs(sig)) * 0.96 * 32767).astype('<i2')

    buf = io.BytesIO()
    with wave.open(buf, 'wb') as wav:
        wav.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        wav.writeframes(pcm.tobytes())

    ogg_path = ASSETS / 'sounds/weapon/onepunch_hit.ogg'
    ogg_path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([ffmpeg, '-v', 'error', '-y', '-i', 'pipe:0', '-c:a', 'libvorbis', '-q:a', '6', str(ogg_path)],
                   input=buf.getvalue(), check=True)

    sounds_json = ASSETS / 'sounds.json'
    events = json.loads(sounds_json.read_text(encoding='utf-8'))
    events['weapon.onepunch.hit'] = {
        'sounds': [{
            'name': 'hobbang:weapon/onepunch_hit',
            'attenuation_distance': 48,
            'preload': True,
        }]
    }
    sounds_json.write_text(json.dumps(events, indent=2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')


if __name__ == '__main__':
    build_icon()
    build_hand_texture_and_model()
    register_item_dispatch()
    build_hit_sound()
    print('One-Punch Glove assets + SFX generated.')
