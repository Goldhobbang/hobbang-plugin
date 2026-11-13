"""Render the v7 Smith comparisons using the original body mesh and v6/v7 packs.

Usage: render_smith_v7.py V6_RESOURCEPACK POSES_TSV OUT
Both versions use the same offline BossPosePreview transforms.
"""
import argparse
import json
from pathlib import Path

import numpy as np

from obj2mc import load_mesh
from render import grid, label, render
from render_smith_v6 import ROOT, SIZE, SmithPreview, VIEWS


def original_tiles(shots):
    config = ROOT / 'obj2mc' / 'configs' / 'abyssal_smith.json'
    vertices, faces, uvs, texture = load_mesh(json.loads(config.read_text(encoding='utf-8')), config.parent)
    triangles = vertices[faces]
    uv = uvs[faces].copy()
    uv[..., 1] = 1 - uv[..., 1]
    return [label(render(triangles, uv, texture, *view, SIZE, np.asarray(center), extent,
                         bg=(32, 39, 48), nearest=False)[0], f'Original OBJ | {name}')
            for name, view, center, extent in shots]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ('v6_resourcepack', 'poses_tsv', 'out'):
        parser.add_argument(arg, type=Path)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    versions = [('v6', SmithPreview(args.v6_resourcepack, args.poses_tsv)),
                ('v7', SmithPreview(ROOT / 'resourcepack', args.poses_tsv))]

    def save(tiles, cols, filename):
        grid(tiles, cols).save(args.out / filename, quality=96, subsampling=0)

    views = [model.tile(f'{version} | {name}', (yaw, pitch), (0, 4.35, 0), 10.1)
             for version, model in versions for yaw, pitch, name in VIEWS]
    save(views, 4, 'v6-v7-views.jpg')

    lower = ('torso', 'leg_l', 'leg_r', 'shin_l', 'shin_r', 'foot_l', 'foot_r')
    legs = [model.tile(f'{version} | {name}', (yaw, 4), (0, 2.75, 0.05), 4.10, lower)
            for version, model in versions for yaw, name in ((0, 'front'), (-90, 'side'), (180, 'back'))]
    save(legs, 3, 'v6-v7-legs.jpg')

    shots = [('stone and chain', (-29, 8), (0, 5.66, 0.02), 5.42),
             ('leather and waist', (-17, 5), (0, 3.65, 0.30), 3.33)]
    body = tuple(part['name'] for part in versions[0][1].parts if part['name'] != 'hammer')
    original = original_tiles(shots)
    surfaces = []
    for index, (name, view, center, extent) in enumerate(shots):
        surfaces.append(original[index])
        surfaces.extend(model.tile(f'{version} | {name}', view, center, extent, body, pose=None)
                        for version, model in versions)
    save(surfaces, 3, 'v7-armor-source.jpg')

    eyes = [model.tile(f'{version} | {name}', (yaw, pitch), (0, 7.91, 0.46), 1.43, ('head',), pose=None)
            for version, model in versions for yaw, pitch, name in ((0, 2, 'front'), (-37, 10, '3/4'))]
    save(eyes, 2, 'v6-v7-orbits.jpg')


if __name__ == '__main__':
    main()
