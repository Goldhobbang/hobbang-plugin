"""Render v7/v8 comparisons from emitted pack models and BossPosePreview poses.

Usage: render_smith_v8.py V7_RESOURCEPACK V7_POSES_TSV V8_POSES_TSV OUT
"""
import argparse
from pathlib import Path

from render import grid
from render_smith_v6 import ROOT, SmithPreview, VIEWS


LOWER_BODY = ('torso', 'leg_l', 'leg_r', 'shin_l', 'shin_r', 'foot_l', 'foot_r')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ('v7_resourcepack', 'v7_poses_tsv', 'v8_poses_tsv', 'out'):
        parser.add_argument(arg, type=Path)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    v7 = SmithPreview(args.v7_resourcepack, args.v7_poses_tsv)
    v8 = SmithPreview(ROOT / 'resourcepack', args.v8_poses_tsv, alpha_test=True)
    versions = [('v7', v7), ('v8', v8)]

    def save(tiles, columns, filename):
        grid(tiles, columns).save(args.out / filename, quality=96, subsampling=0)

    views = [model.tile(f'{version} | {name}', (yaw, pitch), (0, 4.70, 0), 10.9)
             for version, model in versions for yaw, pitch, name in VIEWS]
    save(views, 4, 'v7-v8-views.jpg')

    angles = ((0, 'front'), (-90, 'side'), (180, 'back'))
    legs = [model.tile(f'{version} | {name}', (yaw, 4), (0, 2.54, 0.12), 5.60, LOWER_BODY)
            for version, model in versions for yaw, name in angles]
    save(legs, 3, 'v7-v8-pelvis-legs.jpg')

    joints = [v8.tile(f'v8 idle | {name}', view, (0, 3.22, 0.11), 3.92, LOWER_BODY)
              for view, name in (((0, 4), 'front'), ((-29, 9), 'front 3/4'),
                                 ((-90, 5), 'side'), ((145, 8), 'rear 3/4'))]
    save(joints, 4, 'v8-idle-hip-joints.jpg')

    chains = [model.tile(f'{version} | {label}', view, center, extent, parts, pose=None)
              for version, model in versions
              for label, view, center, extent, parts in (
                  ('chest chains', (-22, 6), (0, 6.30, 0.55), 3.32, ('chest',)),
                  ('waist chain', (-32, 12), (0, 4.20, 0.05), 2.93, ('torso', 'chest')))]
    save(chains, 2, 'v7-v8-chains.jpg')

    spine_hammer = [tile for version, model in versions for tile in (
        model.tile(f'{version} | torso joint', (-150, 7), (0, 4.75, -0.05), 2.95, ('torso', 'chest')),
        model.tile(f'{version} | forge hammer', (-36, 10), (0.30, 2.01, 0), 5.88, ('hammer',), pose=None),
        model.tile(f'{version} | hammer impact', (-68, 7), (0, 4.51, 1.48), 11.6, pose='Impact'),
    )]
    save(spine_hammer, 3, 'v7-v8-spine-hammer.jpg')


if __name__ == '__main__':
    main()
