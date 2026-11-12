"""Render v5 and v6 from emitted packs and offline BossPosePreview TSVs.

Usage: render_smith_v6.py V5_RESOURCEPACK V5_TSV V6_TSV OUT
V5_RESOURCEPACK is the v5 directory containing hobbang-pack and manifests.
"""
import argparse
import csv
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

from preview import pack_tris
from render import grid, label, render
from render_boss_poses import rotation

ROOT = Path(__file__).resolve().parents[1]
SIZE = 510
VIEWS = ((0, 5, 'front'), (-38, 12, '3/4'), (-90, 5, 'side'), (180, 5, 'back'))
RIGHT_ARM = ('arm_r', 'fore_r', 'hand_r')


class SmithPreview:
    def __init__(self, base, tsv, *, alpha_test=False):
        self.alpha_test = alpha_test
        manifests = [json.loads((base / 'manifests' / f'boss__{name}.json').read_text(encoding='utf-8'))
                     for name in ('abyssal_smith', 'smith_hammer')]
        self.parts = manifests[0]['parts'] + manifests[1]['parts']
        self.vertices, self.uv, self.texture, part_ids = pack_tris(
            base / 'hobbang-pack', manifests[0], manifests[1:])
        self.names = np.array([self.parts[i]['name'] for i in part_ids])
        self.poses = {}
        with tsv.open(encoding='utf-8-sig', newline='') as stream:
            for boss, title, name, *values in csv.reader(stream, delimiter='\t'):
                if boss == 'abyssal_smith':
                    self.poses.setdefault(title, {})[name] = list(map(float, values))

    def tile(self, title, view, center, extent, names=None, pose='Idle'):
        mask = np.ones(len(self.names), bool) if names is None else np.isin(self.names, names)
        vertices = self.vertices.copy()
        if pose:
            for part in self.parts:
                local_mask = self.names == part['name']
                x, y, z, qx, qy, qz, qw, scale = self.poses[pose][part['name']]
                local = (vertices[local_mask] - np.array(part['translation'])) * scale
                vertices[local_mask] = local @ rotation(qx, qy, qz, qw).T + [x, y, z]
        image, _ = render(vertices[mask], self.uv[mask], self.texture, *view, SIZE, np.array(center), extent,
                          bg=(32, 39, 48), alpha_test=self.alpha_test)
        return label(image, title)


def reference_tile(filename, title):
    with Image.open(ROOT / 'docs' / 'reference' / filename) as source:
        image = ImageOps.contain(source.convert('RGB'), (SIZE, SIZE - 19), Image.Resampling.LANCZOS)
    tile = Image.new('RGB', (SIZE, SIZE), (32, 39, 48))
    tile.paste(image, ((SIZE - image.width) // 2, (SIZE + 19 - image.height) // 2))
    return label(tile, title)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ('v5_resourcepack', 'v5_tsv', 'v6_tsv', 'out'):
        parser.add_argument(arg, type=Path)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    v5 = SmithPreview(args.v5_resourcepack, args.v5_tsv)
    v6 = SmithPreview(ROOT / 'resourcepack', args.v6_tsv)
    versions = [('v5', v5), ('v6', v6)]

    views = [model.tile(f'{version} | {name}', (yaw, pitch), (0, 4.35, 0), 10.1)
             for version, model in versions for yaw, pitch, name in VIEWS]
    grid(views, len(VIEWS)).save(args.out / 'v5-v6-views.jpg', quality=96, subsampling=0)

    close = []
    for version, model in versions:
        close += [
            model.tile(f'{version} | skull', (-24, 9), (0, 7.94, 0), 2.05, ('head',)),
            model.tile(f'{version} | chest', (-26, 9), (0, 5.87, 0), 3.55, ('chest', 'torso')),
            model.tile(f'{version} | right arm', (-65, 5), (-2.30, 5.20, 0), 5.05, RIGHT_ARM),
            model.tile(f'{version} | forge hammer', (-33, 10), (0.30, 2.04, 0), 5.35, ('hammer',), pose=None),
        ]
    grid(close, 4).save(args.out / 'v5-v6-details.jpg', quality=96, subsampling=0)

    skull = [reference_tile('smith_skull_ref.png', 'Reference | low-poly skull')]
    skull += [v6.tile(f'v6 | skull {name}', (yaw, 6), (0, 7.96, 0), 2.15, ('head',))
              for yaw, name in ((0, 'front'), (-48, '3/4'))]
    grid(skull, 3).save(args.out / 'skull-reference.jpg', quality=96, subsampling=0)

    body = tuple(part['name'] for part in v6.parts if part['name'] != 'hammer')
    armor = [reference_tile('smith_armor_ref_1.png', 'Previous reference | plate armor')]
    original = ROOT / 'docs' / 'img' / 'smith-remodel' / 'original_body.jpg'
    with Image.open(original) as source:
        tile_width = source.width // 4
        armor.append(label(source.crop((tile_width, 0, tile_width * 2, source.height))
                           .resize((SIZE, SIZE), Image.Resampling.LANCZOS), 'Project original | Smith 3/4'))
    armor += [v6.tile(f'v6 | armor {name}', (yaw, 8), (0, 4.39, 0), 9.6, body)
              for yaw, name in ((0, 'front'), (-38, '3/4'))]
    grid(armor, 4).save(args.out / 'armor-reference.jpg', quality=96, subsampling=0)

    impact = [model.tile(f'{version} | {pose}', (-65, 8), (0, 5.44, 0.28), 13.4, pose=pose)
              for version, model in versions for pose in ('Hammer raised', 'Impact', 'Recovery')]
    grid(impact, 3).save(args.out / 'v5-v6-impact.jpg', quality=96, subsampling=0)


if __name__ == '__main__':
    main()
