"""Step 1 of the Abyssal Smith remodel: read the ORIGINAL meshes and report what the simplified model must follow.
Prints the bbox of the whole body, of every rig part (config regions) and of the colour features (cyan crystals, violet
core, apron, chains, magma hand), writes work/smith_analysis.json, and saves front / 3-4 / side / rear renders of both
meshes to docs/img/smith-remodel/original_{body,hammer}.jpg.   usage: python smith_analyze.py"""
import json, os
import numpy as np
from scipy.spatial import cKDTree
import obj2mc
from render import render, label, grid

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(HERE, 'configs')
OUT = os.path.join(HERE, '..', 'docs', 'img', 'smith-remodel')
VIEWS = ((0, 6, 'front'), (-40, 14, '3/4'), (90, 6, 'side'), (145, 14, 'rear 3/4'))


def feature_masks(rgb):
    r, g, b = rgb[:, 0], rgb[:, 1], rgb[:, 2]
    sat = rgb.max(1) - rgb.min(1)
    return {
        'cyan': (b > 120) & (g > 90) & (b - r > 60),
        'violet': (b > 110) & (r > 55) & (g < 0.75 * b) & (b - g > 60) & (r < b),
        'magma': (r > 110) & (r - b > 70),
        'brown': (r > g + 6) & (g > b + 3) & (sat > 12) & (sat < 70) & (r > 45) & (r < 170),
    }


def bbox(p, lo=1, hi=99):
    if len(p) < 20: return None
    return [[round(float(x), 2) for x in np.percentile(p, lo, axis=0)], [round(float(x), 2) for x in np.percentile(p, hi, axis=0)]]


def load(name, spacing=0.03):
    cfg = json.load(open(os.path.join(BASE, name + '.json')))
    v, f, uv, tex = obj2mc.load_mesh(cfg, BASE)
    np.random.seed(3)
    pts, col, nrm = obj2mc.surface_samples(v, f, uv, tex, spacing)
    return cfg, v, f, uv, tex, pts, col[:, :3], nrm


def part_labels(cfg, pts):
    parts = obj2mc.scale_layout(cfg)
    lab = np.full(len(pts), -1)
    for pi, p in enumerate(parts):
        m = np.zeros(len(pts), bool)
        for r in p.get('regions', [{}]): m |= obj2mc.in_region(pts, r)
        m &= lab < 0
        lab[m] = pi
    return parts, lab


def renders(name, v, f, uv, tex, H):
    ou = uv[f].copy(); ou[..., 1] = 1 - ou[..., 1]
    c = np.array([0, H / 2, 0]); e = H * 1.3
    tiles = [label(render(v[f], ou, tex, y, p, 520, c, e, nearest=False)[0], f'original {name} - {n}') for y, p, n in VIEWS]
    os.makedirs(OUT, exist_ok=True)
    grid(tiles, 4).save(os.path.join(OUT, f'original_{name}.jpg'), quality=92)


if __name__ == '__main__':
    rep = {}
    cfg, v, f, uv, tex, pts, col, nrm = load('abyssal_smith')
    renders('body', v, f, uv, tex, cfg['height_blocks'])
    parts, lab = part_labels(cfg, pts)
    fm = feature_masks(col)
    print('body bbox', v.min(0).round(2), v.max(0).round(2), 'size', (v.max(0) - v.min(0)).round(2))
    rep['body'] = {'bbox': [v.min(0).round(3).tolist(), v.max(0).round(3).tolist()], 'parts': {}, 'features': {}}
    print(f'{"part":8s} {"x":>14s} {"y":>14s} {"z":>14s}   size(w,h,d)        cyan% magma% brown% violet%')
    for pi, p in enumerate(parts):
        m = lab == pi
        bb = bbox(pts[m])
        core = bbox(pts[m], 8, 92)
        frac = {k: float(fm[k][m].mean()) for k in fm}
        size = np.array(bb[1]) - np.array(bb[0])
        print(f'{p["name"]:8s} ' + ' '.join(f'{bb[0][i]:6.2f}..{bb[1][i]:5.2f}' for i in range(3)) + '   '
              + ' '.join(f'{s:5.2f}' for s in size) + '   ' + ' '.join(f'{100 * frac[k]:5.1f}' for k in ('cyan', 'magma', 'brown', 'violet')))
        rep['body']['parts'][p['name']] = {'bbox': bb, 'core': core, 'frac': frac, 'pivot': p['pivot']}
    for k, m in fm.items():
        for pi, p in enumerate(parts):
            mm = m & (lab == pi)
            if mm.sum() > 300:
                bb = bbox(pts[mm], 3, 97)
                rep['body']['features'][f'{k}/{p["name"]}'] = bb
                print(f'  feature {k:6s} on {p["name"]:8s} n={mm.sum():6d} bbox {bb}')
    cfg, v, f, uv, tex, pts, col, nrm = load('smith_hammer')
    renders('hammer', v, f, uv, tex, cfg['height_blocks'])
    print('hammer bbox', v.min(0).round(2), v.max(0).round(2))
    rep['hammer'] = {'bbox': [v.min(0).round(3).tolist(), v.max(0).round(3).tolist()], 'slices': []}
    for y0 in np.arange(0, 4.2, 0.3):
        m = (pts[:, 1] >= y0) & (pts[:, 1] < y0 + 0.3)
        if m.sum() < 20: continue
        bb = bbox(pts[m], 2, 98)
        rep['hammer']['slices'].append({'y': round(float(y0), 2), 'bbox': bb})
        print(f'  y {y0:4.1f}..{y0 + 0.3:4.1f}  x {bb[0][0]:5.2f}..{bb[1][0]:5.2f}  z {bb[0][2]:5.2f}..{bb[1][2]:5.2f}  magma% {100 * feature_masks(col)["magma"][m].mean():4.1f}')
    json.dump(rep, open(os.path.join(HERE, '..', 'work', 'smith_analysis.json'), 'w'), indent=1)
