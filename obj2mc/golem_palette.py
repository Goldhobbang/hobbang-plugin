"""Palette extraction for the Prism Colossus / Chrono Sentinel remodel: samples the ORIGINAL Meshy texture on the mesh
surface (models-3d/, unzipped to work/models/) and prints luminance percentiles per colour family.
make_golems.py uses only these colours (copied in as constants). usage: python golem_palette.py"""
import json, os, sys
import numpy as np
import obj2mc

HERE = os.path.dirname(os.path.abspath(__file__))
PCTS = (5, 15, 30, 50, 70, 85, 95, 99)


def samples(cfg_name):
    cfg = json.load(open(os.path.join(HERE, 'configs', cfg_name + '.v144-voxel.json')))
    np.random.seed(1)
    v, f, uv, tex = obj2mc.load_mesh(cfg, os.path.join(HERE, 'configs'))
    pts, col, nrm = obj2mc.surface_samples(v, f, uv, tex, 0.03)
    return pts, col[:, :3]


def family(name, C, mask):
    c = C[mask]
    if len(c) == 0:
        print(f'{name:18s} none'); return {}
    lum = c @ [0.299, 0.587, 0.114]
    order = np.argsort(lum)
    out = {}
    for p in PCTS:
        lo = int(len(c) * max(0, p - 3) / 100); hi = max(lo + 1, int(len(c) * min(100, p + 3) / 100))
        rgb = np.median(c[order[lo:hi]], 0).round().astype(int)
        out[p] = tuple(int(x) for x in rgb)
    print(f'{name:18s} n={len(c):7d} ' + ' '.join(f'p{p}=#{r:02x}{g:02x}{b:02x}' for p, (r, g, b) in out.items()))
    return out


def main():
    res = {}
    P, C = samples('prism_colossus')
    r, g, b = C[:, 0], C[:, 1], C[:, 2]
    sat = C.max(1) - C.min(1)
    crystal = (b > r * 0.9) & (sat > 55) & (b > 90)
    stone = sat < 34
    res['colossus_stone'] = family('colossus stone', C, stone)
    res['colossus_crystal'] = family('colossus crystal', C, crystal)
    core = crystal & (np.abs(P[:, 0]) < 0.6) & (P[:, 1] > 4.0) & (P[:, 1] < 5.2) & (P[:, 2] > 0.2)
    res['colossus_core'] = family('colossus chest core', C, core)
    eye = (np.abs(P[:, 0]) < 0.5) & (P[:, 1] > 5.6) & (P[:, 1] < 6.0) & (P[:, 2] > 0.3) & (C.sum(1) > 380)
    res['colossus_eye'] = family('colossus eye', C, eye)

    P, C = samples('chrono_sentinel')
    r, g, b = C[:, 0], C[:, 1], C[:, 2]
    brass = (r > g) & (g > b) & (r - b > 45)
    teal = (g > r) & (b > r * 0.9) & (g - r > 8)
    dark = C.max(1) < 45
    res['sentinel_brass'] = family('sentinel brass', C, brass)
    res['sentinel_teal'] = family('sentinel teal', C, teal)
    res['sentinel_dark'] = family('sentinel dark', C, dark)
    dial = (np.abs(P[:, 0]) < 0.42) & (np.abs(P[:, 1] - 3.13) < 0.42) & (P[:, 2] > 0.5)
    res['sentinel_dial'] = family('sentinel dial face', C, dial & (C.sum(1) > 420))
    hands = dial & (C.sum(1) < 200)
    res['sentinel_dial_hands'] = family('sentinel dial hands', C, hands)
    glass = (np.abs(P[:, 0]) < 0.3) & (P[:, 1] > 1.1) & (P[:, 1] < 2.0)
    res['sentinel_hourglass'] = family('sentinel hourglass', C, glass & (C.sum(1) > 330))
    json.dump(res, open(os.path.join(HERE, '..', 'work', 'golem_palette.json'), 'w'), indent=1)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
