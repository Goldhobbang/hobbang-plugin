"""Render matched views and nearest-neighbor texture sheets for the golem rigs.

Unpack the original archives into work/models (obj2mc.load_mesh's fallback).
Use --previous to select the archived resourcepack directory for the middle row.
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
BOSSES = ('prism_colossus', 'chrono_sentinel')
VIEWS = ((0, 6, 'Front'), (-40, 14, '3/4'), (90, 6, 'Side'))
SIZE = 520


def pack_model(folder, boss):
    path = folder / 'manifests' / f'boss__{boss}.json'
    manifest = json.loads(path.read_text(encoding='utf-8'))
    pack = folder / 'hobbang-pack'
    return manifest, pack_tris(pack, manifest)[:3]


def comparison(boss, old, current, out, previous_label, current_label, rear):
    config_path = ROOT / 'obj2mc' / 'configs' / f'{boss}.json'
    config = json.loads(config_path.read_text(encoding='utf-8'))
    vertices, indices, uvs, texture = load_mesh(config, config_path.parent)
    mesh_uv = uvs[indices].copy()
    mesh_uv[..., 1] = 1 - mesh_uv[..., 1]
    old_manifest, old_model = pack_model(old, boss)
    manifest, model = pack_model(current, boss)
    height = config['height_blocks']
    center = np.array([0, height / 2, 0])
    extent = height * 1.24
    angles = (*VIEWS, (145, 14, 'Rear 3/4')) if rear else VIEWS
    views = []
    for title, (tris, uv, tex), pixel in (
        (f'Original mesh ({len(indices):,} triangles)', (vertices[indices], mesh_uv, texture), False),
        (f'{previous_label} ({old_manifest["total_cuboids"]:,} cuboids)', old_model, True),
        (f'{current_label} ({manifest["total_cuboids"]:,} cuboids)', model, True),
    ):
        for yaw, pitch, view in angles:
            image, _ = render(tris, uv, tex, yaw, pitch, SIZE, center, extent, nearest=pixel)
            views.append(label(image, f'{title} | {view}'))
    path = out / f'{boss}_compare.jpg'
    grid(views, len(angles)).save(path, quality=94, subsampling=0)
    print(path)
    return manifest


def texture_sheet(boss, manifest, current, out):
    assets = current / 'hobbang-pack' / 'assets' / 'hobbang'
    folder = assets / 'textures' / 'item' / 'boss' / boss
    pad, caption, width = 16, 26, 1296
    x = y = pad
    row_height = 0
    tiles = []
    for part in sorted(manifest['parts'], key=lambda p: -p['texture_px']):
        name = part['name']
        source = Image.open(folder / f'{name}.png').convert('RGBA')
        used = source.crop(source.getbbox())
        tile = used.resize((used.width * 3, used.height * 3), Image.Resampling.NEAREST)
        cell_width = max(155, tile.width)
        if x + cell_width + pad > width:
            x, y = pad, y + row_height + pad
            row_height = 0
        tiles.append((x, y, name, source.size, tile))
        x += cell_width + pad
        row_height = max(row_height, tile.height + caption)
    image = Image.new('RGB', (width, y + row_height + pad), (31, 28, 39))
    draw = ImageDraw.Draw(image)
    for x, y, name, (w, h), tile in tiles:
        draw.text((x, y + 4), f'{name} | {w}x{h} atlas, 3x crop', fill=(241, 233, 250))
        draw.rectangle((x - 1, y + caption - 1, x + tile.width, y + caption + tile.height), fill=(49, 44, 57))
        image.paste(tile, (x, y + caption), tile)
    path = out / f'{boss}_textures.png'
    image.save(path, optimize=True)
    print(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bosses', nargs='*', metavar='BOSS')
    parser.add_argument('--previous', '--v1', type=Path,
                        default=ROOT / 'work' / 'v1' / 'resourcepack')
    parser.add_argument('--previous-label', default='v1')
    parser.add_argument('--current-label', default='v3')
    parser.add_argument('--rear', action='store_true')
    parser.add_argument('--out', type=Path, default=ROOT / 'docs' / 'img' / 'golems-remodel-v3')
    args = parser.parse_args()
    for boss in args.bosses:
        if boss not in BOSSES:
            parser.error(f'unknown boss: {boss}')
    args.out.mkdir(parents=True, exist_ok=True)
    current = ROOT / 'resourcepack'
    for boss in args.bosses or BOSSES:
        manifest = comparison(boss, args.previous, current, args.out, args.previous_label, args.current_label, args.rear)
        texture_sheet(boss, manifest, current, args.out)


if __name__ == '__main__':
    main()
