"""Render the original mesh, the previous pack (v2) and the current pack (v3) from the same views,
plus an enlarged atlas sheet per weapon.

  python -B obj2mc/render_weapons_detail.py [--previous work/detail-v2/resourcepack] [--out docs/img/weapons-detail-v3]

The previous pack is the v2 resourcepack (git archive e4e4599 resourcepack | tar -x -C work/detail-v2).
Original OBJ/textures are looked up under work/models (unzipped models-3d/).
"""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from obj2mc import load_mesh
from preview import pack_tris
from render import grid, label, render

ROOT = Path(__file__).resolve().parent.parent
WEAPONS = ('stormcaller', 'crystal_greatsword', 'chrono_dagger')
VIEWS = (('Front', 0, 6), ('3/4', -40, 13), ('Side', 90, 3))
BG = (32, 34, 43)


def load_model(resources, weapon, hand=False):
    manifest = json.loads((resources / 'manifests' / f'weapon__{weapon}.json').read_bytes())
    pack = resources / 'hobbang-pack'
    overrides = None
    if hand:
        file = pack / 'assets' / 'hobbang' / 'models' / 'item' / 'weapon' / f'{weapon}_hand.json'
        overrides = {(manifest['id'], 'held'): file}
    return pack_tris(pack, manifest, model_paths=overrides)[:3]


def compare(weapon, previous, current, out, names=('v2', 'v3')):
    cfg_path = ROOT / 'obj2mc' / 'configs' / f'weapon_{weapon}.json'
    cfg = json.loads(cfg_path.read_bytes())
    vertices, indices, uvs, texture = load_mesh(cfg, cfg_path.parent)
    uv = uvs[indices].copy()
    uv[..., 1] = 1 - uv[..., 1]
    height = cfg['height_blocks']
    center = np.array([0, height / 2, 0])
    extent = max(height, 2 * np.abs(vertices[:, [0, 2]]).max()) * 1.16

    def tiles(mesh, title, cutout):
        views = []
        for view, yaw, pitch in VIEWS:
            image, _ = render(*mesh, yaw, pitch, 520, center, extent, nearest=cutout, alpha_test=cutout)
            views.append(label(image, f'{title} | {view}'))
        return views

    original = tiles((vertices[indices], uv, texture), 'Original mesh', False)
    for hand, suffix, model in ((False, '', 'Detailed model'), (True, '_hand', 'In-hand model')):
        images = original + tiles(load_model(previous, weapon, hand), f'{model} {names[0]}', True)
        images += tiles(load_model(current, weapon, hand), f'{model} {names[1]}', True)
        path = out / f'{weapon}{suffix}_compare.jpg'
        grid(images, len(VIEWS)).save(path, quality=94, subsampling=0)
        print(path)


def texture_sheet(weapon, previous, current, out, names=('v2', 'v3')):
    """v2 and v3 atlases side by side, enlarged to the same width, on a dark background."""
    sheets = []
    for name, root in zip(names, (previous, current)):
        path = root / 'hobbang-pack' / 'assets' / 'hobbang' / 'textures' / 'item' / 'weapon' / weapon / 'held.png'
        atlas = Image.open(path).convert('RGBA')
        scale = max(1, 1024 // atlas.width)
        big = atlas.resize((atlas.width * scale, atlas.height * scale), Image.Resampling.NEAREST)
        tile = Image.new('RGBA', (big.width, big.height + 24), BG + (255,))
        tile.alpha_composite(big, (0, 24))
        ImageDraw.Draw(tile).text((6, 5), f'{weapon} {name} atlas {atlas.width}x{atlas.height}, shown {scale}x', fill='white')
        sheets.append(tile)
    sheet = Image.new('RGB', (sum(s.width for s in sheets) + 16, max(s.height for s in sheets)), BG)
    x = 0
    for s in sheets:
        sheet.paste(s.convert('RGB'), (x, 0))
        x += s.width + 16
    path = out / f'{weapon}_textures.png'
    sheet.save(path, optimize=True)
    print(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--previous', type=Path, default=ROOT / 'work' / 'detail-v2' / 'resourcepack')
    parser.add_argument('--out', type=Path, default=ROOT / 'docs' / 'img' / 'weapons-detail-v3')
    parser.add_argument('--weapons', nargs='+', default=WEAPONS, choices=WEAPONS)
    parser.add_argument('--names', nargs=2, default=('v2', 'v3'), help='labels for previous and current')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for weapon in args.weapons:
        compare(weapon, args.previous, ROOT / 'resourcepack', args.out, args.names)
        texture_sheet(weapon, args.previous, ROOT / 'resourcepack', args.out, args.names)


if __name__ == '__main__':
    main()
