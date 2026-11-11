"""Abyssal Smith remodel built FROM THE ORIGINAL MESHES ("vanilla mob" box budget).

  boss/abyssal_smith   15 parts, same names / parents / joint pivots as 1.5.x
  boss/smith_hammer    1 part 'hammer' attached to hand_r, same grip pivot

Nothing here is free-hand design. For every rig part (regions of configs/abyssal_smith.json):
  box      = the part's bbox on the original mesh (x/z: 3rd..97th percentile of its surface, y: the part's cut planes)
  3D extra = only what sticks out of that box on the mesh: cyan crystals (voxelised coarse), the leather apron plate
  texture  = the original texture seen from each box face (outermost surface sample per texel, 16 px/block), snapped to
             the palette extracted from that texture, speckle removed by a majority filter, thin glow lines kept, then a
             light top/left rim and dark bottom/right rim drawn on. Result: the original's cyan fissures, violet V core,
             chains, apron runes and magma fist stay where they are, as clean pixels.
The hammer is four boxes cut from the hammer mesh by height (head / neck / haft / pommel).
The held weapon model (weapon/smith_hammer) is NOT regenerated.      Run:  python make_smith_v2.py
"""
import json, os
import numpy as np
from scipy import ndimage
import obj2mc
from obj2mc import FACE_VERTS, FACE_NORMAL
import make_smith as M
import smith_analyze as A

HERE = os.path.dirname(os.path.abspath(__file__))
PPU = M.PPU
S_REF = np.array([-1.0, 1.0, -1.0])
GROUPS = {
    'stone': [M.STONE_VOID, M.STONE_DARK, M.STONE_BASE, M.STONE_MID, M.STONE_LIGHT, M.STONE_HI],
    'apron': [M.APRON_VOID, M.APRON_DARK, M.APRON_BASE, M.APRON_LIGHT, M.APRON_RUNE, M.APRON_HI],
    'cyan': [M.CYAN_DEEP, M.CYAN_MID, M.CYAN_GLOW, M.CYAN_BRIGHT, M.CYAN_WHITE],
    'violet': [M.CORE_VIOLET, M.CORE_MAGENTA],
    'magma': [M.MAGMA_DARK, M.MAGMA_CRIMSON, M.MAGMA_RED, M.MAGMA_ORANGE, M.MAGMA_GOLD, M.MAGMA_BRIGHT],
    'basalt': [M.BASALT_VOID, M.BASALT_DARK, M.BASALT_BASE, M.BASALT_EDGE],
}
GLOW = ('cyan', 'violet', 'magma')


def palette(names):
    cols = np.array([c for n in names for c in GROUPS[n]], float)
    grp = np.array([gi for gi, n in enumerate(names) for _ in GROUPS[n]])
    return cols, grp, [n in GLOW for n in names]


def snap(img, names):
    """nearest palette colour per texel (plain RGB distance) -> (palette index image, cols, group id per index, glow flags)"""
    cols, grp, glow = palette(names)
    d = ((img[:, :, None, :] - cols[None, None]) ** 2).sum(-1)
    return d.argmin(-1), cols, grp, glow


def clean(idx, grp, glow):
    """majority filter (3x3) on non-glow texels; glow texels survive only in runs of >= 3 connected texels"""
    H, W = idx.shape
    out = idx.copy()
    is_glow = np.array(glow)[grp[idx]]
    lab, n = ndimage.label(is_glow, structure=np.ones((3, 3)))
    if n:
        sizes = np.bincount(lab.ravel())
        is_glow &= sizes[lab] >= 3
    pad = np.pad(idx, 1, mode='edge')
    padg = np.pad(is_glow, 1, mode='edge')
    K = int(idx.max()) + 1
    votes = np.zeros((K, H, W), np.int16)
    for dy in range(3):
        for dx in range(3):
            w = 2 if (dy == 1 and dx == 1) else 1
            sub = pad[dy:dy + H, dx:dx + W]; ok = ~padg[dy:dy + H, dx:dx + W]
            for k in range(K):
                votes[k] += w * ((sub == k) & ok)
    maj = votes.argmax(0)
    out[~is_glow] = maj[~is_glow]
    return out


class Source:
    """surface samples of one original mesh, labelled by rig part"""
    def __init__(self, name, spacing=0.02):
        self.cfg, self.v, self.f, self.uv, self.tex, self.pts, self.col, self.nrm = A.load(name, spacing)
        self.parts, self.lab = A.part_labels(self.cfg, self.pts)
        self.index = {p['name']: i for i, p in enumerate(self.parts)}

    def sel(self, part):
        return self.lab == self.index[part]


def face_texture(src, mask, c, s, fn, names, depth=None):
    """the original texture as seen from face `fn` of the box (centre c, size s, world blocks), W x H texels"""
    vs = np.array(FACE_VERTS[fn], float)
    w = lambda t: np.array(c) + S_REF * (t - 0.5) * np.array(s)          # unit-cube corner -> world
    p0, p1, p3 = w(vs[0]), w(vs[1]), w(vs[3])
    eu, ev = p3 - p0, p1 - p0
    W = max(2, int(round(np.linalg.norm(eu) * PPU))); H = max(2, int(round(np.linalg.norm(ev) * PPU)))
    n = np.array(FACE_NORMAL[fn], float) * S_REF
    m = mask & (src.nrm @ n > 0.15)
    P = src.pts[m]; C = src.col[m]
    rel = P - p0
    u = rel @ eu / (eu @ eu); v_ = rel @ ev / (ev @ ev); dpt = rel @ n
    ok = (u >= 0) & (u < 1) & (v_ >= 0) & (v_ < 1)
    if depth is not None: ok &= dpt > -depth
    img = np.zeros((H, W, 3)); filled = np.zeros((H, W), bool)
    if ok.sum() > 10:
        ix = np.minimum((u[ok] * W).astype(int), W - 1); iy = np.minimum((v_[ok] * H).astype(int), H - 1)
        d = dpt[ok]; cc = C[ok]
        top = np.full((H, W), -1e9); np.maximum.at(top, (iy, ix), d)
        near = d >= top[iy, ix] - 0.07                                   # the outermost skin only
        acc = np.zeros((H, W, 3)); cnt = np.zeros((H, W))
        np.add.at(acc, (iy[near], ix[near]), cc[near]); np.add.at(cnt, (iy[near], ix[near]), 1)
        filled = cnt > 0
        img[filled] = acc[filled] / cnt[filled][:, None]
    if not filled.any():
        img[:] = GROUPS[names[0]][2]
    elif not filled.all():                                              # holes take the nearest filled texel
        _, (jy, jx) = ndimage.distance_transform_edt(~filled, return_indices=True)
        img = img[jy, jx]
    idx, cols, grp, glow = snap(img, names)
    for _ in range(3):                                                  # three majority passes: tone patches, not speckle
        idx = clean(idx, grp, glow)
    out = cols[idx].astype(np.uint8)
    # drawn rims: light catch on top/left, dark on bottom/right (only over non-glow texels)
    g0 = [c_ for c_ in GROUPS[names[0]]]
    hi, lo = np.array(g0[-2]), np.array(g0[0])
    ng = ~np.array(glow)[grp[idx]]
    if W >= 4 and H >= 4:
        for (sl, colr) in (((0, slice(None)), hi), ((slice(None), 0), hi), ((H - 1, slice(None)), lo), ((slice(None), W - 1), lo)):
            sub = out[sl]; sub[ng[sl]] = colr; out[sl] = sub
    if fn == 'down': out = (out * 0.8).astype(np.uint8)
    return out


def projected(src, mask, c, s, names, depth=None):
    cache = {}
    def f(fn, W, H):
        if fn not in cache: cache[fn] = face_texture(src, mask, c, s, fn, names, depth)
        img = cache[fn]
        if img.shape[:2] != (H, W):                                      # emit rounds sizes the same way; guard anyway
            yy = np.minimum((np.arange(H) * img.shape[0] / H).astype(int), img.shape[0] - 1)
            xx = np.minimum((np.arange(W) * img.shape[1] / W).astype(int), img.shape[1] - 1)
            img = img[yy][:, xx]
        return img
    return f


def crystal(fn, W, H):
    """cyan crystal: bright body, white top / lit edge, deep base"""
    img = np.empty((H, W, 3), np.uint8); img[:] = M.CYAN_GLOW
    if fn in ('north', 'south', 'east', 'west'):
        img[:max(1, H // 3)] = M.CYAN_BRIGHT
        img[0] = M.CYAN_WHITE
        if W >= 3: img[:, 0] = M.CYAN_BRIGHT; img[:, -1] = M.CYAN_MID
        if H >= 3: img[-1] = M.CYAN_DEEP
    elif fn == 'up': img[:] = M.CYAN_WHITE
    else: img[:] = M.CYAN_DEEP
    return img


def _paint(kind, fn, W, H, color):
    return kind(fn, max(1, int(W)), max(1, int(H))) if callable(kind) else _old_paint(kind, fn, W, H, color)


_old_paint = M.paint_face
M.paint_face = _paint


def pct_box(P, ylo, yhi, lo=3, hi=97):
    a = np.percentile(P, lo, axis=0); b = np.percentile(P, hi, axis=0)
    a[1], b[1] = ylo, yhi
    return (a + b) / 2, np.maximum(b - a, 0.2)


def add_crystals(T, src, part, mask, c, s, res=4, min_vox=3, cap=10):
    """cyan surface that sticks out of the part's box on the mesh -> a few coarse boxes"""
    fm = A.feature_masks(src.col)['cyan'] & mask
    P = src.pts[fm]
    out = (np.abs(P - c) - np.array(s) / 2).max(1) > 0.10
    P = P[out]
    if len(P) < 40: return 0
    lo = np.floor(P.min(0) * res).astype(int)
    ijk = np.floor(P * res).astype(int) - lo
    occ = np.zeros(ijk.max(0) + 1, bool); occ[tuple(ijk.T)] = True
    cnt = np.zeros(occ.shape, int); np.add.at(cnt, tuple(ijk.T), 1)
    occ &= cnt >= 6
    lab, n = ndimage.label(occ, structure=np.ones((3, 3, 3)))
    if n == 0: return 0
    sizes = np.bincount(lab.ravel())[1:]
    keep = np.isin(lab, 1 + np.nonzero(sizes >= min_vox)[0])
    boxes = obj2mc.best_boxes(keep) if keep.any() else []
    boxes = sorted(boxes, key=lambda b: -np.prod(b[1] - b[0]))[:cap]
    for blo, bhi in boxes:
        M.add_box(T, part, ((blo + bhi) / 2 + lo) / res, (bhi - blo) / res, M.STONE_BASE, None, crystal)
    return len(boxes)


# ----------------------------------------------------------------------------- smith body
BODY_PAL = ('stone', 'apron', 'cyan', 'violet', 'magma')


def define_smith(report):
    src = Source('abyssal_smith')
    T = M.new_part_table()
    ycut = {}                                                           # the part's y extent = its region cut planes
    for p in src.parts:
        P = src.pts[src.sel(p['name'])]
        ycut[p['name']] = (float(np.percentile(P[:, 1], 0.5)), float(np.percentile(P[:, 1], 99.5)))
    # rig order / parents / pivots exactly as before (pivots with None components were frozen in the old manifest)
    old = json.load(open(os.path.join(HERE, '..', 'resourcepack', 'manifests', 'boss__abyssal_smith.json')))
    pivots = {q['name']: (q['parent'], q['pivot_blocks']) for q in old['parts']}
    front = src.nrm[:, 2] > 0.5
    brown = A.feature_masks(src.col)['brown']
    for name in [q['name'] for q in old['parts']]:
        parent, piv = pivots[name]
        M.add_part(T, name, parent, piv)
        mask = src.sel(name)
        P = src.pts[mask]
        y0, y1 = ycut[name]
        if name == 'head':                                              # skull only: the crown crystals are 3D extras
            y1 = float(np.percentile(P[~A.feature_masks(src.col)['cyan'][mask]][:, 1], 97))
        if name == 'chest':
            y1 = float(np.percentile(P[np.abs(P[:, 0]) < 1.0][:, 1], 90))  # shoulder line, not the neck crystals
        c, s = pct_box(P, y0, y1)
        M.add_box(T, name, c, s, M.STONE_BASE, None, projected(src, mask, c, s, BODY_PAL))
        n_cr = add_crystals(T, src, name, mask, c, s, cap=6) if name in ('head', 'chest', 'arm_l', 'arm_r') else 0
        report['parts'][name] = {'mesh_bbox': [np.percentile(P, 1, axis=0).round(2).tolist(), np.percentile(P, 99, axis=0).round(2).tolist()],
                                 'box_centre': np.round(c, 2).tolist(), 'box_size': np.round(s, 2).tolist(), 'crystal_boxes': n_cr}
        if name in ('chest', 'torso'):                                  # leather apron: the brown plate in front of the box
            am = mask & front & brown & (np.abs(src.pts[:, 0]) < 1.0) & (src.pts[:, 2] > c[2] + s[2] / 2 - 0.12)
            if name == 'torso':                                         # the apron tip hangs between the legs (leg regions)
                low = front & brown & (np.abs(src.pts[:, 0]) < 0.62) & (src.pts[:, 1] < 2.65) & (src.pts[:, 1] > 1.0) \
                      & (src.pts[:, 2] > 0.55)
                am = am | low
            if am.sum() > 200:
                A_ = src.pts[am]
                a0 = np.percentile(A_, 4, axis=0); a1 = np.percentile(A_, 96, axis=0)
                zf = float(np.percentile(A_[:, 2], 80))
                ac = np.array([(a0[0] + a1[0]) / 2, (a0[1] + a1[1]) / 2, zf - 0.06]); as_ = np.array([a1[0] - a0[0], a1[1] - a0[1], 0.16])
                pm = (front & (np.abs(src.pts[:, 0] - ac[0]) < as_[0] / 2) & (np.abs(src.pts[:, 1] - ac[1]) < as_[1] / 2)
                      & (src.pts[:, 2] > 0.45))
                M.add_box(T, name, ac, as_, M.APRON_BASE, None, projected(src, pm, ac, as_, ('apron', 'stone', 'cyan', 'violet'), depth=0.5))
                report['parts'][name]['apron_box'] = [np.round(ac, 2).tolist(), np.round(as_, 2).tolist()]
    return T


# ----------------------------------------------------------------------------- hammer
def define_hammer(report):
    src = Source('smith_hammer')
    T = M.new_part_table()
    M.add_part(T, 'hammer', None, (0.3, 2.9, 0.0))
    allm = np.ones(len(src.pts), bool)
    for label_, y0, y1 in (('head', 0.0, 1.2), ('neck', 1.2, 1.5), ('haft', 1.5, 3.3), ('pommel', 3.3, 4.2)):
        m = (src.pts[:, 1] >= y0) & (src.pts[:, 1] < y1)
        c, s = pct_box(src.pts[m], y0, y1, 2, 98)
        M.add_box(T, 'hammer', c, s, M.BASALT_DARK, None, projected(src, allm & m, c, s, ('basalt', 'magma', 'stone')))
        report['hammer'][label_] = {'box_centre': np.round(c, 2).tolist(), 'box_size': np.round(s, 2).tolist()}
    return T


if __name__ == '__main__':
    pack = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    credit = 'make_smith_v2 (HobbangEnhance, from the original meshes)'
    rep = {'parts': {}, 'hammer': {}}
    M.emit_model_set(pack, 'boss/abyssal_smith', define_smith(rep), height_blocks=9.0, k_units_per_block=4.25, credit=credit)
    M.emit_model_set(pack, 'boss/smith_hammer', define_hammer(rep), height_blocks=4.2, k_units_per_block=8.0,
                     attach={'boss': 'abyssal_smith', 'part': 'hammer', 'pivot_blocks': [-2.35, 3.5, 0.45]}, credit=credit)
    json.dump(rep, open(os.path.join(HERE, '..', 'work', 'smith_boxes.json'), 'w'), indent=1)
    for k, v in rep['parts'].items():
        print(f'{k:8s} mesh {v["mesh_bbox"]}  box c={v["box_centre"]} s={v["box_size"]} crystals={v["crystal_boxes"]}')
