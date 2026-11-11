"""Render TSV poses from BossPosePreview with the emitted pack geometry."""
import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path

import numpy as np

from preview import pack_tris
from render import grid, label, render


ROOT = Path(__file__).resolve().parent.parent
VIEWS = ((-35, 11), (90, 5))


def rotation(x, y, z, w):
    return np.array([
        [1 - 2 * (y*y + z*z), 2 * (x*y - z*w), 2 * (x*z + y*w)],
        [2 * (x*y + z*w), 1 - 2 * (x*x + z*z), 2 * (y*z - x*w)],
        [2 * (x*z - y*w), 2 * (y*z + x*w), 1 - 2 * (x*x + y*y)],
    ])


def manifest(boss):
    path = ROOT / 'resourcepack' / 'manifests' / f'boss__{boss}.json'
    return json.loads(path.read_text(encoding='utf-8'))


def render_boss(boss, frames, out):
    model = manifest(boss)
    attachments = [manifest('smith_hammer')] if boss == 'abyssal_smith' else []
    parts = [p for group in [model, *attachments] for p in group['parts']]
    tris, uv, texture, part_ids = pack_tris(ROOT / 'resourcepack' / 'hobbang-pack', model, attachments)
    posed = []
    for title, transforms in frames.items():
        vertices = tris.copy()
        for i, part in enumerate(parts):
            mask = part_ids == i
            x, y, z, qx, qy, qz, qw, scale = transforms[part['name']]
            local = (tris[mask] - np.array(part['translation'])) * scale
            vertices[mask] = local @ rotation(qx, qy, qz, qw).T + [x, y, z]
        posed.append((title, vertices))
    all_vertices = np.concatenate([verts for _, verts in posed]).reshape(-1, 3)
    lo, hi = all_vertices.min(0), all_vertices.max(0)
    center = (lo + hi) * 0.5
    extent = np.linalg.norm(hi - lo) * 0.92
    tiles = []
    for yaw, pitch in VIEWS:
        for title, vertices in posed:
            image, _ = render(vertices, uv, texture, yaw, pitch, 420, center, extent, nearest=True)
            tiles.append(label(image, title))
    path = out / f'{boss}_motion.jpg'
    grid(tiles, len(posed)).save(path, quality=94)
    print(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('poses', type=Path)
    parser.add_argument('out', type=Path)
    args = parser.parse_args()
    poses = defaultdict(lambda: defaultdict(dict))
    with args.poses.open(encoding='utf-8') as source:
        for boss, title, part, *values in csv.reader(source, delimiter='\t'):
            poses[boss][title][part] = [float(value) for value in values]
    args.out.mkdir(parents=True, exist_ok=True)
    for boss, frames in poses.items():
        render_boss(boss, frames, args.out)


if __name__ == '__main__':
    main()
