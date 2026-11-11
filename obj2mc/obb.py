"""OBB mode for obj2mc: approximate a solid voxel group with a few tilted cuboids.

Minecraft model elements can be rotated about ONE axis by -45..45 deg in 22.5 deg steps
(`rotation: {origin, axis, angle}`), so a box here is: axis in {x,y,z}, angle in ANGLES, a
centre and half sizes along the (rotated) local axes. Everything in this file works in WORLD
blocks (boss frame: +Z front, Y up); obj2mc.py converts to model space when it emits JSON.

fit_boxes(): recursive split. Fit the min-volume box (over axis/angle choices) to the points,
stop when it is full enough (fill >= `fill`), otherwise cut along its longest axis (at the largest
gap, else at the cut that minimises the summed child volume) and fit both halves again.
"""
import numpy as np
from scipy import ndimage

ANGLES = (0.0, 22.5, -22.5, 45.0, -45.0)


def rot(axis, deg):
    """standard right-handed rotation matrix about world axis 0/1/2 (same maths as JOML
    Quaternionf.rotationAxis, which is what the client uses for element rotation)"""
    a = np.radians(deg)
    c, s = np.cos(a), np.sin(a)
    if axis == 0:
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 1:
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def fit_box(P, vox, pad=0.5):
    """min-volume box over the allowed (axis, angle) choices. returns dict(axis, ang, R, lo, hi, vol)
    lo/hi are in the LOCAL frame (local = world @ R)."""
    best = None
    for ax in range(3):
        for ang in ANGLES:
            if ang == 0.0 and ax != 0:
                continue
            R = rot(ax, ang)
            L = P @ R
            lo = L.min(0) - pad * vox
            hi = L.max(0) + pad * vox
            vol = float(np.prod(hi - lo))
            if best is None or vol < best['vol'] * 0.999:     # prefer axis-aligned on ties (listed first)
                best = dict(axis=ax, ang=ang, R=R, lo=lo, hi=hi, vol=vol)
    return best


def fit_boxes(P, vox, fill=0.8, min_pts=10, max_depth=8, pad=0.5, min_dim=4.0):
    """P: (N,3) voxel-centre points (blocks). returns list of box dicts (see fit_box)"""
    out = []

    def rec(Q, depth):
        b = fit_box(Q, vox, pad)
        n = len(Q)
        cells = b['vol'] / vox ** 3
        f = n / cells
        dims = b['hi'] - b['lo']
        if f >= fill or n < min_pts or depth >= max_depth or dims.max() <= min_dim * vox:
            out.append(b)
            return
        ax = int(np.argmax(dims))
        proj = (Q @ b['R'])[:, ax]
        order = np.argsort(proj)
        sp = proj[order]
        gaps = np.diff(sp)
        cands = []
        if len(gaps) and gaps.max() >= 1.5 * vox:
            g = int(np.argmax(gaps))
            cands.append(g + 1)
        else:
            for q in (0.25, 0.375, 0.5, 0.625, 0.75):
                cands.append(int(len(sp) * q))
        best = None
        for c in cands:
            if c < 1 or c >= len(sp):
                continue
            A, B = Q[order[:c]], Q[order[c:]]
            if len(A) < 2 or len(B) < 2:
                continue
            v = fit_box(A, vox, pad)['vol'] + fit_box(B, vox, pad)['vol']
            if best is None or v < best[0]:
                best = (v, c)
        if best is None:
            out.append(b)
            return
        if f >= 0.6 and best[0] > 0.9 * b['vol'] and depth >= 2:     # splitting barely helps
            out.append(b)
            return
        c = best[1]
        rec(Q[order[:c]], depth + 1)
        rec(Q[order[c:]], depth + 1)

    # separate connected pieces first (26-connectivity on the point set's own grid)
    lo = np.floor(P.min(0) / vox).astype(int)
    idx = np.floor(P / vox).astype(int) - lo
    g = np.zeros(idx.max(0) + 1, bool)
    g[idx[:, 0], idx[:, 1], idx[:, 2]] = True
    lab, n = ndimage.label(g, structure=np.ones((3, 3, 3)))
    L = lab[idx[:, 0], idx[:, 1], idx[:, 2]]
    for k in range(1, n + 1):
        Q = P[L == k]
        if len(Q):
            rec(Q, 0)
    return out


def world_box(b):
    """centre (world), half sizes (local axes), R"""
    cl = (b['lo'] + b['hi']) / 2
    return b['R'] @ cl, (b['hi'] - b['lo']) / 2, b['R']


def inside(b, X, slack=0.0):
    """(N,3) world points -> bool inside box b (expanded by slack blocks)"""
    L = X @ b['R']
    return ((L >= b['lo'] - slack) & (L <= b['hi'] + slack)).all(1)
