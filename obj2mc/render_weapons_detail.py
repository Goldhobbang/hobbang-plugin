"""Render Original mesh vs 1.5.2 vs New (weapons-detail) comparison images and 2D icon comparison."""
import json
import os
import sys
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..'))
sys.path.insert(0, HERE)
from obj2mc import load_mesh
from preview import pack_tris
from render import render, label, grid


def render_3d_comparisons():
    outdir = os.path.join(ROOT, 'docs', 'img', 'weapons-detail')
    os.makedirs(outdir, exist_ok=True)
    views = [('Front', 0, 8), ('3/4', -40, 15)]
    all_rows = []
    for wid, cfg_fn in [
        ('stormcaller', 'weapon_stormcaller.json'),
        ('crystal_greatsword', 'weapon_crystal_greatsword.json'),
        ('chrono_dagger', 'weapon_chrono_dagger.json'),
    ]:
        cfg = json.load(open(os.path.join(HERE, 'configs', cfg_fn)))
        v, f, uv, tex = load_mesh(cfg, HERE)
        otris = v[f]
        ouv = uv[f].copy()
        ouv[..., 1] = 1 - ouv[..., 1]

        man152 = json.load(open(os.path.join(ROOT, 'work', 'baseline_152', f'weapon__{wid}.json')))
        tris152, uv152, tex152, _ = pack_tris(os.path.join(ROOT, 'work', 'baseline_152', 'pack'), man152)

        man_new = json.load(open(os.path.join(ROOT, 'resourcepack', 'manifests', f'weapon__{wid}.json')))
        tris_new, uv_new, tex_new, _ = pack_tris(os.path.join(ROOT, 'resourcepack', 'hobbang-pack'), man_new)

        H = cfg['height_blocks']
        center = np.array([0, H / 2, 0])
        ext = max(H, 2 * np.abs(v[:, [0, 2]]).max()) * 1.06

        tiles = []
        row_tiles = []
        c152 = man152['total_cuboids']
        cnew = man_new['total_cuboids']
        for vname, yaw, pitch in views:
            im_orig, _ = render(otris, ouv, tex, yaw, pitch, 520, center, ext, nearest=False)
            im_152, _  = render(tris152, uv152, tex152, yaw, pitch, 520, center, ext, nearest=True)
            im_new, _  = render(tris_new, uv_new, tex_new, yaw, pitch, 520, center, ext, nearest=True)
            t0 = label(im_orig, f'{wid} Orig - {vname}')
            t1 = label(im_152,  f'{wid} 1.5.2 ({c152}c) - {vname}')
            t2 = label(im_new,  f'{wid} Detail ({cnew}c) - {vname}')
            tiles.extend([t0, t1, t2])
            row_tiles.extend([t0, t1, t2])

        grid(tiles, 3).save(os.path.join(outdir, f'{wid}_compare.jpg'), quality=95)
        all_rows.extend(row_tiles)
        print('wrote', os.path.join(outdir, f'{wid}_compare.jpg'))

    grid(all_rows, 6).save(os.path.join(outdir, 'weapons_compare.jpg'), quality=95)
    print('wrote', os.path.join(outdir, 'weapons_compare.jpg'))


if __name__ == '__main__':
    render_3d_comparisons()
