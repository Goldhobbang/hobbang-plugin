"""Generate flowing Phase 3 purple energy texture, model, and item variants for Abyssal Smith and his hammer."""

from pathlib import Path
import json
import math

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'resourcepack/hobbang-pack/assets/hobbang'

SMITH_PARTS = (
    'arm_l', 'arm_r', 'chest', 'foot_l', 'foot_r', 'fore_l', 'fore_r',
    'hand_l', 'hand_r', 'head', 'jaw', 'leg_l', 'leg_r', 'shin_l', 'shin_r', 'torso',
)
HAMMER_PARTS = ('hammer',)
FRAME_COUNT = 12


def load_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=True) + '\n', encoding='utf-8', newline='\n')


def ramp_rgb(lum, c0, c1, c2):
    t1 = np.clip(lum / 0.55, 0.0, 1.0)[:, None]
    t2 = np.clip((lum - 0.55) / 0.45, 0.0, 1.0)[:, None]
    mid = c0 * (1.0 - t1) + c1 * t1
    return mid * (1.0 - t2) + c2 * t2


def convert_texture(tex_ref):
    rel = tex_ref.split(':', 1)[1]
    base_path = ASSETS / 'textures' / f'{rel}.png'
    source_path = (
        base_path.with_name(base_path.stem + '_still.png')
        if base_path.with_suffix('.png.mcmeta').exists()
        else base_path
    )
    with Image.open(source_path) as image:
        base = np.asarray(image.convert('RGBA'))

    r, g, b, a = (base[:, :, c].astype(np.int16) for c in range(4))
    cyan = (a > 0) & (b - r >= 35) & (g - r >= 20) & (b >= 60)
    magma = (a > 0) & (~cyan) & (r - b >= 55) & (r >= 95)
    energy = cyan | magma
    if not energy.any():
        return False

    height, width = energy.shape
    row, col = np.nonzero(energy)
    is_magma = magma[row, col]
    pr = r[row, col].astype(np.float32)
    pg = g[row, col].astype(np.float32)
    pb = b[row, col].astype(np.float32)

    lum_cyan = np.clip((0.20 * pr + 0.55 * pg + 0.25 * pb) / 255.0, 0.0, 1.0)
    lum_magma = np.clip((0.65 * pr + 0.30 * pg + 0.05 * pb) / 255.0, 0.0, 1.0)

    purple_cyan = ramp_rgb(
        lum_cyan,
        np.array([92.0, 22.0, 165.0]),
        np.array([185.0, 62.0, 255.0]),
        np.array([244.0, 200.0, 255.0]),
    )
    purple_magma = ramp_rgb(
        lum_magma,
        np.array([110.0, 20.0, 155.0]),
        np.array([215.0, 58.0, 255.0]),
        np.array([255.0, 190.0, 255.0]),
    )
    purple_base = np.where(is_magma[:, None], purple_magma, purple_cyan)

    phase = (row / height * 2.2 - col / width * 1.1) * math.tau
    phase2 = (row / height * 1.1 + col / width * 1.9) * math.tau
    frames = []
    for frame in range(FRAME_COUNT):
        angle = math.tau * frame / FRAME_COUNT
        flow = 0.65 * np.sin(phase - angle) + 0.35 * np.sin(phase2 - 2 * angle)
        pulse = 0.5 + 0.5 * math.sin(angle)
        boost = (0.08 + 0.16 * pulse + 0.22 * flow)[:, None]
        colored = np.where(
            boost >= 0,
            purple_base + (np.array([252.0, 225.0, 255.0]) - purple_base) * np.clip(boost, 0.0, 0.85),
            purple_base * np.clip(1.0 + boost * 0.65, 0.45, 1.0),
        )
        out = base.copy()
        out[row, col, :3] = np.clip(np.round(colored), 0, 255).astype(np.uint8)
        frames.append(out)

    target_png = base_path.with_name(base_path.stem + '_purple.png')
    Image.fromarray(np.concatenate(frames)).save(target_png, optimize=True)
    write_json(
        target_png.with_suffix('.png.mcmeta'),
        {'animation': {'width': width, 'height': height, 'frametime': 2, 'interpolate': True}},
    )
    return True


def build_group(group, parts):
    converted = {}
    for part in parts:
        model_file = ASSETS / 'models/item/boss' / group / f'{part}.json'
        model = load_json(model_file)
        overrides = {}
        for key, tex_ref in model['textures'].items():
            if tex_ref.startswith('#'):
                continue
            if tex_ref not in converted:
                converted[tex_ref] = convert_texture(tex_ref)
            if converted[tex_ref]:
                overrides[key] = tex_ref + '_purple'
        if not overrides:
            raise ValueError(f'No energy pixels converted for {group}/{part}')
        write_json(
            ASSETS / 'models/item/boss' / group / f'{part}_purple.json',
            {'parent': f'hobbang:item/boss/{group}/{part}', 'textures': overrides},
        )
        write_json(
            ASSETS / 'items/boss' / group / f'{part}_purple.json',
            {'model': {'type': 'minecraft:model', 'model': f'hobbang:item/boss/{group}/{part}_purple'}},
        )


def main():
    build_group('abyssal_smith', SMITH_PARTS)
    build_group('smith_hammer', HAMMER_PARTS)
    print(f'Generated _purple variants for {len(SMITH_PARTS)} smith parts and {len(HAMMER_PARTS)} hammer parts.')


if __name__ == '__main__':
    main()
