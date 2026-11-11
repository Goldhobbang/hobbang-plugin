import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np, obj2mc
cfgp = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'configs', 'tempest_wyvern.v144-final.json')
cfg = json.load(open(cfgp)); np.random.seed(1)
v, f, uv, tex = obj2mc.load_mesh(cfg, os.path.dirname(cfgp))
pts, col, nrm = obj2mc.surface_samples(v, f, uv, tex, 0.03)
k = cfg['height_blocks'] / cfg['layout_height']
P = pts / k; C = col[:, :3]
lum = C @ [0.3, 0.59, 0.11]
yel = (C[:, 0] > 150) & (C[:, 0] - C[:, 2] > 70)
def rep(name, m):
    c = C[m]; y = yel[m]
    if len(c) == 0: print(name, 'none'); return
    d = c[~y]; b = c[y]
    q = lambda a: [int(x) for x in np.median(a, 0)] if len(a) else None
    lo = d[(d @ [0.3, 0.59, 0.11]) < np.percentile(d @ [0.3, 0.59, 0.11], 30)] if len(d) else d
    hi = d[(d @ [0.3, 0.59, 0.11]) > np.percentile(d @ [0.3, 0.59, 0.11], 70)] if len(d) else d
    print(f'{name:10s} n={len(c):7d} yellow {y.mean() * 100:4.1f}%  dark-med {q(d)} dark-lo {q(lo)} dark-hi {q(hi)}  yellow-med {q(b)}')
x, y, z = P[:, 0], P[:, 1], P[:, 2]
rep('membrane', (x > 1.35) & (x < 2.3) & (y > 2.2) & (y < 3.3))
rep('wingbone', (x > 0.9) & (x < 1.1) & (y > 2.35) & (y < 2.95))
rep('body side', (np.abs(x) > 0.3) & (np.abs(x) < 0.7) & (y > 1.0) & (y < 1.6) & (z > 0.2) & (z < 0.9))
rep('back top', (np.abs(x) < 0.25) & (y > 1.5) & (y < 2.0) & (z > -0.4) & (z < 0.3))
rep('belly', (np.abs(x) < 0.25) & (y > 0.9) & (y < 1.7) & (z > 0.9) & (z < 1.5) & (nrm[:, 2] > 0.5))
rep('tail', (z < -1.2) & (z > -2.2))
rep('head', (np.abs(x) < 0.3) & (y > 2.2) & (y < 2.6) & (z > 1.9))
rep('legs', (y < 0.8) & (y > 0.25))
rep('claws', (y < 0.15))
