"""Abyssal Smith v5 + forge hammer v5: precise shapes, roughness in the texture, an energy body under plate armour.

Principle: geometry is a small number of designed plates and boxes (no mesh chunks); everything rough - scratches,
chipped corners, stone grain, scorch, cracks - is hand-pixelled in a 4-tone palette (no noise, no gradients).

Two layers per rig part
  1. energy body  one box a step smaller than the part, cyan / violet (molten for the right hand and the hammer)
                  connected lightning grain with bright filaments, element light_emission 15
  2. armour       breastplate halves, abdominal lames, 3-tier pauldrons, rerebrace / vambrace / gauntlet, cuisse /
                  greave / sabaton plates, chain belt, leather apron with the diamond rune. Plates are tilted 22.5 / 45
                  deg where armour is angled and leave gaps (sternum, lames, joints, plate corners) where the body shows.
Armour texture: obsidian / basalt plate (original stone family), 1 px dark outline, cyan trim along the edges, lightning
runes drawn as CONNECTED lines along the original texture's cyan cracks for that face (projected from the mesh,
reduced to cells and joined by a minimum spanning tree), drawn fallback bolt on plates where the original has none.
Head: a skull (dome, eye sockets, cheekbones, nasal cavity, upper / lower teeth, jaw) lit from inside. No crystals.
Proportions: each part's envelope is the original mesh's per-part bbox (MESH, from smith_analyze.py).
Part names, parents and joint pivots are the 1.5.x ones.                     Run:  python make_smith_v5.py
"""
import os, zlib
import numpy as np
from scipy.sparse import coo_matrix
from scipy.sparse.csgraph import minimum_spanning_tree
import obj2mc
from obj2mc import FACE_VERTS, FACE_NORMAL
import make_smith as M
import make_smith_v3 as V3            # pixel tools, palettes, apron / chain / teeth painters, callable-kind hook
import smith_analyze as A
from make_smith import (STONE_VOID, STONE_DARK, STONE_BASE, STONE_MID, STONE_LIGHT, STONE_HI,
                        APRON_VOID, APRON_DARK, APRON_BASE, APRON_LIGHT, APRON_RUNE, APRON_HI,
                        CYAN_DEEP, CYAN_MID, CYAN_GLOW, CYAN_BRIGHT, CYAN_WHITE, CORE_VIOLET, CORE_MAGENTA,
                        BASALT_VOID, BASALT_DARK, BASALT_BASE, BASALT_EDGE,
                        MAGMA_DARK, MAGMA_CRIMSON, MAGMA_RED, MAGMA_ORANGE, MAGMA_GOLD, MAGMA_BRIGHT)
from make_smith_v3 import canvas, px, rect

HERE = os.path.dirname(os.path.abspath(__file__))
PPU = M.PPU
S_REF = np.array([-1.0, 1.0, -1.0])
SIDE = ('north', 'south', 'east', 'west')

# ----------------------------------------------------------------------------- palettes (original texture families)
OBSIDIAN = dict(void=STONE_VOID, dark=STONE_DARK, base=STONE_BASE, mid=STONE_MID, light=STONE_LIGHT, hi=STONE_HI)
MOLTEN_PLATE = dict(void=MAGMA_DARK, dark=(110, 10, 0), base=MAGMA_CRIMSON, mid=MAGMA_RED, light=MAGMA_ORANGE, hi=MAGMA_GOLD)
BASALT = dict(void=BASALT_VOID, dark=BASALT_DARK, base=BASALT_BASE, mid=BASALT_EDGE, light=(110, 72, 62), hi=(132, 92, 80))
BONE = dict(void=STONE_DARK, dark=STONE_MID, base=STONE_LIGHT, mid=STONE_HI, light=(150, 143, 140), hi=(178, 171, 166))
CYAN_RUNE = dict(halo=CYAN_DEEP, core=CYAN_BRIGHT, node=CYAN_WHITE, trim=CYAN_GLOW, trim_hi=CYAN_BRIGHT)
LAVA_RUNE = dict(halo=MAGMA_CRIMSON, core=MAGMA_ORANGE, node=MAGMA_BRIGHT, trim=MAGMA_RED, trim_hi=MAGMA_GOLD)
GOLD_RUNE = dict(halo=MAGMA_DARK, core=MAGMA_GOLD, node=MAGMA_BRIGHT, trim=MAGMA_ORANGE, trim_hi=MAGMA_GOLD)


class _Rng:
    """seeded placement of drawn marks (where a scratch / chip / strata line goes), never per-texel colour noise"""
    def __init__(self, seed): self.g = np.random.default_rng(seed)
    def integers(self, lo, hi): return int(lo) if hi <= lo else int(self.g.integers(lo, hi))
    def random(self): return self.g.random()
    def choice(self, a): return a[int(self.g.integers(0, len(a)))]


def rng_for(*key):
    return _Rng(zlib.crc32('/'.join(map(str, key)).encode()))


def line(img, x0, y0, x1, y1, c):
    n = int(max(abs(x1 - x0), abs(y1 - y0), 1))
    for i in range(n + 1):
        px(img, int(round(x0 + (x1 - x0) * i / n)), int(round(y0 + (y1 - y0) * i / n)), c)


# ----------------------------------------------------------------------------- mesh crack sampling (original texture)
class Cracks:
    """surface samples of an original mesh with a per-sample 'glow' flag (cyan/violet, or magma for the hammer).
    mask(part, c, s, fn, W, H) -> boolean texel mask of the glowing cracks the original shows on that box face."""
    def __init__(self, mesh, glow):
        self.cfg, self.v, self.f, self.uv, self.tex, self.pts, self.col, self.nrm = A.load(mesh, 0.02)
        self.parts, self.lab = A.part_labels(self.cfg, self.pts)
        self.index = {p['name']: i for i, p in enumerate(self.parts)}
        fm = A.feature_masks(self.col)
        self.glow = np.zeros(len(self.pts), bool)
        for k in glow: self.glow |= fm[k]

    def mask(self, part, c, s, fn, W, H, depth=0.35):
        vs = np.array(FACE_VERTS[fn], float)
        w = lambda t: np.array(c) + S_REF * (t - 0.5) * np.array(s)
        p0, p1, p3 = w(vs[0]), w(vs[1]), w(vs[3])
        eu, ev = p3 - p0, p1 - p0
        n = np.array(FACE_NORMAL[fn], float) * S_REF
        sel = self.lab == self.index[part] if part in self.index else np.ones(len(self.pts), bool)
        sel &= self.nrm @ n > 0.1
        P = self.pts[sel]; G = self.glow[sel]
        rel = P - p0
        u = rel @ eu / (eu @ eu); v_ = rel @ ev / (ev @ ev); d = rel @ n
        ok = (u >= 0) & (u < 1) & (v_ >= 0) & (v_ < 1) & (np.abs(d) < depth)
        out = np.zeros((H, W)); cnt = np.zeros((H, W))
        if ok.sum() < 20: return out > 1
        ix = np.minimum((u[ok] * W).astype(int), W - 1); iy = np.minimum((v_[ok] * H).astype(int), H - 1)
        np.add.at(out, (iy, ix), G[ok]); np.add.at(cnt, (iy, ix), 1)
        return out / np.maximum(cnt, 1) > 0.5


def rune_paths(mask, cell=4, min_frac=0.20):
    """crack mask -> connected line segments: mask reduced to cells, cells joined by a minimum spanning tree (8-neighbour)
    so every crack becomes one continuous branching bolt, not loose dots"""
    H, W = mask.shape
    gh, gw = H // cell, W // cell
    if gh < 1 or gw < 1: return []
    m = mask[:gh * cell, :gw * cell].reshape(gh, cell, gw, cell).mean((1, 3)) >= min_frac
    if m.mean() > 0.45:                                  # a face that is mostly glow: keep only the densest cells
        m &= mask[:gh * cell, :gw * cell].reshape(gh, cell, gw, cell).mean((1, 3)) >= 0.75
    on = np.argwhere(m)
    if len(on) < 2: return []
    idx = {tuple(p): i for i, p in enumerate(on)}
    r, c_, w = [], [], []
    for i, (y, x) in enumerate(on):
        for dy, dx, wt in ((0, 1, 1.0), (1, 0, 1.0), (1, 1, 1.45), (1, -1, 1.45)):
            j = idx.get((y + dy, x + dx))
            if j is not None: r.append(i); c_.append(j); w.append(wt)
    if not r: return []
    mst = minimum_spanning_tree(coo_matrix((w, (r, c_)), shape=(len(on), len(on)))).tocoo()
    ctr = lambda k: (int(on[k][1] * cell + cell // 2), int(on[k][0] * cell + cell // 2))
    return [(ctr(a), ctr(b)) for a, b in zip(mst.row, mst.col)]


def draw_runes(img, segs, pal):
    deg = {}
    for a, b in segs:
        for p in (a, b): deg[p] = deg.get(p, 0) + 1
    zz = []
    for (x0, y0), (x1, y1) in segs:                      # kink every segment sideways at its middle: lightning, not a grid
        k = 1 if zlib.crc32(f'{x0},{y0},{x1},{y1}'.encode()) & 1 else -1
        mx, my = (x0 + x1) // 2 + k * (1 if y1 != y0 else 0), (y0 + y1) // 2 + k * (1 if x1 != x0 else 0)
        zz += [((x0, y0), (mx, my)), ((mx, my), (x1, y1))]
    for (x0, y0), (x1, y1) in zz:                        # halo first so the core is never overwritten
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)): line(img, x0 + dx, y0 + dy, x1 + dx, y1 + dy, pal['halo'])
    for (x0, y0), (x1, y1) in zz: line(img, x0, y0, x1, y1, pal['core'])
    for p, k in deg.items():
        if k != 2: px(img, p[0], p[1], pal['node'])      # ends and forks flare white


def fallback_bolt(W, H, rng):
    """a drawn lightning rune for plates where the original has no crack: a zig-zag down the long axis + one branch"""
    segs = []
    if H >= W:
        x = W // 2 + int(rng.integers(-W // 6 - 1, W // 6 + 1)); y = 2; pts = [(x, y)]
        while y < H - 3:
            y = min(H - 3, y + int(rng.integers(3, 5))); x = int(np.clip(x + rng.choice((-2, 2)), 3, W - 4)); pts.append((x, y))
    else:
        y = H // 2 + int(rng.integers(-H // 6 - 1, H // 6 + 1)); x = 2; pts = [(x, y)]
        while x < W - 3:
            x = min(W - 3, x + int(rng.integers(3, 5))); y = int(np.clip(y + rng.choice((-2, 2)), 3, H - 4)); pts.append((x, y))
    segs = list(zip(pts[:-1], pts[1:]))
    if len(pts) > 3:
        bx, by = pts[len(pts) // 2]
        segs.append(((bx, by), (int(np.clip(bx + 3, 2, W - 3)), int(np.clip(by + 3, 2, H - 3)))))
    return segs


# ----------------------------------------------------------------------------- painters
def plate_tex(pal, rune_pal, key, cracks=None, part=None, c=None, s=None, trim=True, rune=True, scorch=True, chips=True):
    """armour plate. Order: tone -> grain -> scorch -> scratches -> chips -> outline -> trim -> runes"""
    def f(fn, W, H):
        r = rng_for(*key, fn)
        flat = {'up': pal['mid'], 'down': pal['dark'], 'south': pal['dark']}.get(fn, pal['base'])
        img = canvas(W, H, flat)
        side = fn in SIDE
        if side and H >= 8:                                                 # stone grain: broken strata lines
            for y in range(4 + int(r.integers(0, 3)), H - 3, 5 + int(r.integers(0, 3))):
                x0 = int(r.integers(1, max(2, W // 3))); x1 = int(r.integers(max(x0 + 2, W // 2), W))
                rect(img, x0, y, x1 - 1, y, pal['dark']); rect(img, x0 + 1, y - 1, x1 - 2, y - 1, pal['mid'])
        if scorch and side and H >= 10:                                     # soot creeping up from the lower edge
            for x in range(1, W - 1, 2):
                hgt = 2 + (zlib.crc32(f'{key}{fn}{x // 3}'.encode()) % 3)
                rect(img, x, H - 1 - hgt, x, H - 2, pal['dark'])
        if W >= 6 and H >= 6:                                               # scratches: short diagonal gouges
            for _ in range(max(1, W * H // 140)):
                x = int(r.integers(2, W - 4)); y = int(r.integers(2, H - 4)); L = int(r.integers(2, 5)); sgn = 1 if r.random() < 0.5 else -1
                for k in range(L):
                    px(img, x + k, y + sgn * k, pal['light']); px(img, x + k, y + sgn * k + 1, pal['void'])
        if chips and W >= 6 and H >= 6:                                     # chipped corners
            for cx, cy, dx, dy in ((0, 0, 1, 1), (W - 1, 0, -1, 1), (0, H - 1, 1, -1), (W - 1, H - 1, -1, -1)):
                if r.random() < 0.55:
                    rect(img, min(cx, cx + dx), min(cy, cy + dy), max(cx, cx + dx), max(cy, cy + dy), pal['void'])
                    px(img, cx + 2 * dx, cy + dy, pal['mid']); px(img, cx + dx, cy + 2 * dy, pal['mid'])
        if W >= 3 and H >= 3:                                               # 1 px outline separates plates
            img[0, :] = pal['void']; img[-1, :] = pal['void']; img[:, 0] = pal['void']; img[:, -1] = pal['void']
        if trim and W >= 6 and H >= 6:
            if side:                                                        # trim: bright along the lower edge (the plate's
                rect(img, 1, H - 2, W - 2, H - 2, rune_pal['trim_hi'])      # rim catching the light from inside), a dim
                rect(img, 1, H // 2, 1, H - 2, rune_pal['halo'])            # line running up the sides, none on top
                rect(img, W - 2, H // 2, W - 2, H - 2, rune_pal['halo'])
                px(img, 1, H - 2, rune_pal['node']); px(img, W - 2, H - 2, rune_pal['node'])
                rect(img, 1, 1, W - 2, 1, pal['light'])                     # lit bevel along the top
            elif fn == 'up':
                rect(img, 1, 1, W - 2, 1, pal['hi']); rect(img, 1, 1, 1, H - 2, pal['hi'])
        if rune and side and W >= 8 and H >= 8:
            segs = []
            if cracks is not None and part is not None:
                m = cracks.mask(part, c, s, fn, W, H)
                m[:3, :] = m[-3:, :] = False; m[:, :3] = m[:, -3:] = False
                segs = rune_paths(m)
            if not segs and W * H >= 220:
                segs = fallback_bolt(W, H, r)
            draw_runes(img, segs, rune_pal)
        return img
    return f


def energy_tex(pal, key):
    """energy body: deep base, mid field, connected bright lightning filaments (vertical walks with forks), white nodes"""
    deep, mid, bright, white = pal
    def f(fn, W, H):
        r = rng_for(*key, fn)
        img = canvas(W, H, mid)
        for y in range(0, H, 4):                                            # dark banding = depth between filaments
            rect(img, 0, y, W - 1, y, deep)
        for x0 in range(1 + int(r.integers(0, 3)), W, 4):
            x = x0
            for y in range(H):
                if y % 3 == 0: x = int(np.clip(x + r.choice((-1, 0, 1)), 0, W - 1))
                px(img, x, y, bright)
                if y % 7 == 3: px(img, x, y, white); px(img, x + 1, y, bright)
        return img
    return f


BLUE = (CYAN_DEEP, CYAN_MID, CYAN_BRIGHT, CYAN_WHITE)
VIOLET = (CORE_VIOLET, CORE_MAGENTA, CYAN_BRIGHT, CYAN_WHITE)
FIRE = (MAGMA_CRIMSON, MAGMA_RED, MAGMA_GOLD, MAGMA_BRIGHT)


def bone_tex(key):
    """skull bone: light stone, 1 px soft outline, hairline cracks, no trim, no runes (smooth)"""
    def f(fn, W, H):
        r = rng_for(*key, fn)
        p = BONE
        img = canvas(W, H, {'up': p['mid'], 'down': p['dark'], 'south': p['dark']}.get(fn, p['base']))
        if W >= 3 and H >= 3:
            img[0, :] = p['light']; img[:, -1] = p['dark']
            if fn in ('up', 'down'): img[-1, :] = p['dark']
        if fn in SIDE and W >= 8 and H >= 6:
            x = int(r.integers(2, W - 3)); y = 1
            for _ in range(int(r.integers(2, 4))):
                nx = int(np.clip(x + r.choice((-2, -1, 1, 2)), 1, W - 2)); ny = min(H - 2, y + int(r.integers(2, 4)))
                line(img, x, y, nx, ny, p['void']); x, y = nx, ny
        return img
    return f


def leather_apron(skirt):
    base = V3.apron(skirt)
    def f(fn, W, H):
        img = base(fn, W, H)
        if fn == 'north' and W >= 10 and H >= 14:                          # forge burns, drawn
            for bx, by in ((3, H - 7), (W - 7, H // 3), (W // 2 + 3, H - 12)):
                rect(img, bx, by, bx + 2, by + 1, APRON_VOID); px(img, bx + 1, by, MAGMA_CRIMSON); px(img, bx + 3, by + 1, APRON_DARK)
        return img
    return f


def shaft_wrap(fn, W, H):
    """leather wrap spiralling down the haft: alternating diagonal bands"""
    img = canvas(W, H, APRON_BASE)
    for y in range(H):
        for x in range(W):
            k = (y + x // 2) % 5
            img[y, x] = APRON_LIGHT if k == 0 else APRON_VOID if k == 4 else APRON_BASE if k < 3 else APRON_DARK
    if W >= 3: img[:, 0] = APRON_DARK; img[:, -1] = APRON_VOID
    return img


# ----------------------------------------------------------------------------- builder
class Smith:
    def __init__(self, cracks, rune_pal=CYAN_RUNE):
        self.T = M.new_part_table()
        self.cr = cracks
        self.rp = rune_pal

    def part(self, name, parent, pivot):
        M.add_part(self.T, name, parent, pivot)
        return name

    def _box(self, part, c, s, kind, rot, light=0):
        M.add_box(self.T, part, c, s, STONE_BASE, rot, kind)
        if light: self.T[part]['boxes'][-1]['light'] = light

    def core(self, part, c, s, pal=BLUE):
        self._box(part, c, s, energy_tex(pal, (part, len(self.T[part]['boxes']))), None, light=15)

    def plate(self, part, c, s, rot=None, pal=OBSIDIAN, trim=True, rune=True, rune_pal=None):
        key = (part, len(self.T[part]['boxes']))
        self._box(part, c, s, plate_tex(pal, rune_pal or self.rp, key, self.cr, part, c, s, trim=trim, rune=rune), rot)

    def plain(self, part, c, s, rot=None, pal=OBSIDIAN):
        self.plate(part, c, s, rot, pal, trim=False, rune=False)

    def bone(self, part, c, s, rot=None):
        self._box(part, c, s, bone_tex((part, len(self.T[part]['boxes']))), rot)

    def eye(self, part, c, s):
        self._box(part, c, s, energy_tex((CYAN_BRIGHT, CYAN_BRIGHT, CYAN_WHITE, CYAN_WHITE), (part, 'eye')), None, light=15)

    def raw(self, part, c, s, kind, rot=None):
        self._box(part, c, s, kind, rot)


MESH = V3.MESH


def define_smith():
    B = Smith(Cracks('abyssal_smith', ('cyan',)))
    P = B.part

    # ---------------- torso (root, waist pivot)
    P('torso', None, (0.0, 2.7, 0.0))
    B.core('torso', (0, 3.52, 0.05), (1.70, 1.80, 0.95))
    B.raw('torso', (0, 4.28, 0.10), (2.40, 0.28, 1.46), V3.chain)                                   # chain belt
    B.plate('torso', (0, 4.28, 0.86), (0.46, 0.42, 0.10), rune=False)                               # buckle
    for sx in (1, -1):
        B.plate('torso', (sx * 1.12, 3.68, 0.10), (0.46, 1.20, 1.36), ('z', sx * 22.5))             # tasset
        B.plate('torso', (sx * 1.26, 2.90, 0.10), (0.42, 0.70, 1.24), ('z', sx * 45), rune=False)   # flared lower tasset
        B.plate('torso', (sx * 0.80, 3.52, 0.68), (0.62, 1.06, 0.22), ('y', sx * 22.5))             # front fauld beside the apron
        B.plate('torso', (sx * 0.54, 3.55, -0.58), (1.00, 1.36, 0.24), ('y', -sx * 22.5))           # rear plate
    for i in range(3):                                                                               # lumbar vertebrae in the rear gap
        B.bone('torso', (0, 3.00 + i * 0.48, -0.68), (0.26, 0.24, 0.20), ('x', 22.5))
    B.raw('torso', (0, 2.82, 0.90), (1.22, 2.46, 0.10), leather_apron(True))                         # apron skirt
    B.raw('torso', (0, 1.47, 0.90), (0.66, 0.26, 0.10), V3.leather)
    B.raw('torso', (0, 1.28, 0.90), (0.30, 0.14, 0.10), V3.leather)

    # ---------------- chest
    P('chest', 'torso', (0.0, 4.5, 0.0))
    B.core('chest', (0, 5.80, -0.05), (1.86, 2.16, 1.24))
    B.core('chest', (0, 6.30, 0.62), (0.56, 1.10, 0.16), VIOLET)                                    # the violet V heart
    B.core('chest', (0, 7.24, 0.0), (0.52, 0.36, 0.52))                                             # neck
    for sx in (1, -1):
        B.plate('chest', (sx * 0.68, 6.30, 0.82), (1.14, 1.18, 0.30), ('y', -sx * 22.5))            # breastplate halves (V slit)
        B.plate('chest', (sx * 0.56, 6.96, 0.58), (0.96, 0.28, 0.70), ('x', -22.5), rune=False)     # collar
        B.plate('chest', (sx * 1.24, 5.86, -0.05), (0.40, 1.70, 1.56))                              # flank
        B.plate('chest', (sx * 1.18, 7.00, -0.05), (0.86, 0.32, 1.46), ('z', -sx * 22.5), rune=False)   # shoulder yoke
        B.plate('chest', (sx * 0.58, 6.12, -0.94), (1.00, 1.56, 0.30), ('y', sx * 22.5))            # back plate halves
        B.raw('chest', (sx * 0.44, 6.16, 1.07), (0.12, 1.02, 0.12), V3.chain)                       # apron chains
    for i in range(3):                                                                               # abdominal lames, gaps between
        B.plate('chest', (0, 5.52 - 0.30 * i, 0.78 - 0.03 * i), (2.00 - 0.12 * i, 0.24, 0.34))
    B.plate('chest', (0, 5.02, -0.90), (1.80, 0.56, 0.30))                                          # lower back
    for i in range(4):                                                                               # spine in the back gap
        B.bone('chest', (0, 5.40 + i * 0.40, -1.02), (0.30, 0.24, 0.22), ('x', 22.5))
    B.plate('chest', (0, 7.12, 0.0), (1.14, 0.20, 1.04), rune=False)                                # gorget ring
    B.plate('chest', (0, 7.02, 0.49), (0.90, 0.28, 0.18), ('x', 22.5), rune=False)
    B.raw('chest', (0, 5.12, 1.02), (1.20, 1.00, 0.10), leather_apron(False))                       # apron bib (diamond rune)

    # ---------------- head: a skull, rounded by three dome tiers, lit from inside (all sizes x K about the jaw)
    P('head', 'chest', (0.0, 7.2, 0.0))
    K = 1.15
    hc = lambda c: (c[0] * K, 7.22 + (c[1] - 7.22) * K, 0.10 + (c[2] - 0.10) * K)
    hs = lambda s_: tuple(x * K for x in s_)
    core = lambda c, s_: B.core('head', hc(c), hs(s_))
    bone = lambda c, s_, rot=None: B.bone('head', hc(c), hs(s_), rot)
    core((0, 7.92, 0.08), (0.80, 0.74, 0.76))
    core((0, 7.42, 0.30), (0.44, 0.20, 0.26))                                                       # light in the mouth
    bone((0, 8.17, -0.02), (1.12, 0.30, 1.10))                                                      # cranium
    bone((0, 8.40, -0.04), (1.00, 0.16, 0.98))                                                      # dome
    bone((0, 8.52, -0.07), (0.72, 0.08, 0.74))                                                      # crown
    bone((0, 8.22, 0.50), (0.90, 0.24, 0.12), ('x', -22.5))                                         # forehead, sloping back
    bone((0, 7.88, -0.46), (0.86, 0.36, 0.14))                                                      # back of the skull
    bone((0, 7.90, 0.50), (0.12, 0.28, 0.10))                                                       # nasal bridge
    bone((0, 7.54, 0.44), (0.70, 0.10, 0.24))                                                       # upper jaw
    B.raw('head', hc((0, 7.46, 0.50)), hs((0.58, 0.08, 0.10)), V3.teeth)                            # upper teeth
    B.raw('head', hc((0, 7.33, 0.48)), hs((0.50, 0.07, 0.10)), V3.teeth)                            # lower teeth
    bone((0, 7.26, 0.42), (0.56, 0.10, 0.22), ('x', 22.5))                                          # chin, jaw dropped
    for sx in (1, -1):
        bone((sx * 0.26, 8.05, 0.52), (0.38, 0.08, 0.12))                                           # brow arc over each socket
        bone((sx * 0.50, 7.90, 0.40), (0.14, 0.30, 0.30))                                           # outer orbit wall
        B.eye('head', hc((sx * 0.25, 7.91, 0.47)), hs((0.12, 0.10, 0.04)))                          # the eyes
        bone((sx * 0.30, 7.74, 0.48), (0.34, 0.10, 0.16))                                           # cheekbone under the socket
        bone((sx * 0.56, 7.74, 0.15), (0.10, 0.10, 0.50))                                           # zygomatic arch
        bone((sx * 0.53, 7.92, -0.05), (0.08, 0.30, 0.84))                                          # temple
        bone((sx * 0.15, 7.65, 0.48), (0.16, 0.12, 0.14))                                           # nasal cavity walls
        bone((sx * 0.31, 7.40, 0.20), (0.08, 0.28, 0.40), ('x', -22.5))                             # jaw ramus

    # ---------------- arms
    for sx, q in ((1, 'l'), (-1, 'r')):
        hot = q == 'r'
        a = P(f'arm_{q}', 'chest', (sx * 2.0, 6.8, 0.0))
        B.core(a, (sx * 1.98, 6.25, -0.15), (0.42, 1.66, 0.48))
        B.plate(a, (sx * 2.04, 7.27, -0.20), (0.96, 0.18, 1.24), ('z', -sx * 22.5), rune=False)     # pauldron cap
        for i in range(3):                                                                           # three tiers, gaps between
            B.plate(a, (sx * (2.14 + 0.06 * i), 7.04 - 0.32 * i, -0.20), (1.36 - 0.08 * i, 0.26, 1.56 - 0.10 * i), ('z', -sx * 22.5))
        B.plate(a, (sx * 2.30, 5.95, -0.15), (0.18, 0.90, 0.70))                                    # rerebrace: outer
        B.plate(a, (sx * 1.98, 5.95, 0.18), (0.56, 0.90, 0.12))                                     # front
        B.plate(a, (sx * 1.98, 5.95, -0.48), (0.56, 0.90, 0.12), rune=False)                        # back
        fo = P(f'fore_{q}', a, (sx * 2.0278, 5.3, 0.0278))
        B.core(fo, (sx * 2.14, 4.88, 0.06), (0.48, 0.86, 0.52))
        B.plate(fo, (sx * 2.02, 5.24, -0.20), (0.44, 0.44, 0.44), ('x', 45), rune=False)            # couter
        B.plate(fo, (sx * 2.30, 5.22, -0.12), (0.10, 0.40, 0.40), ('x', 45), rune=False)            # couter wing
        B.plate(fo, (sx * 2.48, 4.88, 0.06), (0.18, 0.84, 0.88), ('z', sx * 22.5))                  # flared outer vambrace
        B.plate(fo, (sx * 2.14, 4.84, 0.42), (0.70, 0.74, 0.12))
        B.plate(fo, (sx * 2.14, 4.84, -0.30), (0.70, 0.74, 0.12), rune=False)
        B.plate(fo, (sx * 1.80, 4.84, 0.06), (0.12, 0.70, 0.66), rune=False)
        B.plate(fo, (sx * 2.14, 4.52, 0.06), (0.76, 0.12, 0.80), rune=False)                        # wrist cuff
        ha = P(f'hand_{q}', fo, (sx * 2.2917, 4.45, 0.2083))
        gp, gr = (MOLTEN_PLATE, GOLD_RUNE) if hot else (OBSIDIAN, CYAN_RUNE)
        B.core(ha, (sx * 2.33, 3.85, 0.29), (0.50, 0.70, 0.56), FIRE if hot else BLUE)
        B.plate(ha, (sx * 2.33, 4.30, 0.29), (0.72, 0.18, 0.80), pal=gp, rune=False, rune_pal=gr)   # gauntlet cuff
        B.plate(ha, (sx * 2.62, 3.80, 0.29), (0.14, 0.70, 0.82), pal=gp, rune_pal=gr)               # back of the hand
        B.plate(ha, (sx * 2.04, 3.80, 0.29), (0.10, 0.62, 0.72), pal=gp, rune=False, rune_pal=gr)   # palm
        B.plate(ha, (sx * 2.40, 3.45, 0.29), (0.76, 0.16, 0.88), pal=gp, rune=False, rune_pal=gr)   # knuckle guard
        for dz in (-0.30, -0.10, 0.10, 0.30):                                                        # fingers, two joints, curled
            B.plain(ha, (sx * 2.38, 3.22, 0.29 + dz), (0.60, 0.24, 0.17), pal=gp)
            B.plain(ha, (sx * 2.18, 3.04, 0.29 + dz), (0.30, 0.20, 0.15), ('z', sx * 45), pal=gp)
        B.plain(ha, (sx * 2.00, 3.56, 0.70), (0.18, 0.40, 0.20), ('x', 22.5), pal=gp)               # thumb

    # ---------------- legs
    pivs = {'l': ((0.9, 2.6, 0.0), (1.0833, 1.55, -0.0833), (1.3056, 0.65, 0.1944)),
            'r': ((-0.9, 2.6, 0.0), (-1.1389, 1.55, -0.0833), (-1.3056, 0.65, 0.25))}
    for sx, q in ((1, 'l'), (-1, 'r')):
        lp, sp, fp = pivs[q]
        lg = P(f'leg_{q}', None, lp)
        B.core(lg, (sx * 0.92, 2.05, 0.05), (0.62, 0.98, 0.66))
        B.plate(lg, (sx * 0.90, 2.08, 0.56), (1.20, 0.92, 0.20), ('x', -22.5))                      # cuisse
        B.plate(lg, (sx * 1.44, 2.10, 0.05), (0.18, 0.86, 1.02), ('z', -sx * 22.5))                 # outer thigh
        B.plate(lg, (sx * 0.92, 2.10, -0.44), (0.92, 0.80, 0.16), rune=False)                       # back of the thigh
        B.plate(lg, (sx * 0.46, 2.06, 0.05), (0.12, 0.70, 0.70), rune=False)                        # inner thigh
        sh = P(f'shin_{q}', lg, sp)
        B.core(sh, (sx * 1.20, 1.10, -0.05), (0.56, 0.88, 0.62))
        B.plate(sh, (sx * 1.18, 1.50, 0.45), (0.50, 0.50, 0.30), ('x', 45), rune=False)             # poleyn
        B.plate(sh, (sx * 1.48, 1.50, 0.30), (0.10, 0.36, 0.36), ('x', 45), rune=False)             # poleyn wing
        B.plate(sh, (sx * 1.20, 1.04, 0.45), (1.02, 0.84, 0.20))                                    # greave
        B.plate(sh, (sx * 1.64, 1.05, -0.05), (0.18, 0.80, 0.92))                                   # outer greave
        B.plate(sh, (sx * 1.20, 1.10, -0.52), (0.86, 0.74, 0.18), ('x', -22.5), rune=False)         # calf
        B.plate(sh, (sx * 0.78, 1.05, -0.05), (0.12, 0.74, 0.76), rune=False)                       # inner
        ft = P(f'foot_{q}', sh, fp)
        B.core(ft, (sx * 1.36, 0.50, -0.10), (0.56, 0.28, 0.56))
        B.plain(ft, (sx * 1.36, 0.08, 0.19), (1.40, 0.16, 2.10), pal=BASALT)                        # sole
        B.plate(ft, (sx * 1.36, 0.36, -0.62), (1.10, 0.40, 0.56), rune=False)                       # heel cup
        for i in range(3):                                                                           # sabaton lames
            B.plate(ft, (sx * 1.36, 0.52 - 0.10 * i, 0.08 + 0.30 * i), (1.10 - 0.06 * i, 0.16, 0.36), ('x', 22.5), rune=False)
        B.plate(ft, (sx * 1.36, 0.25, 1.02), (1.22, 0.34, 0.50))                                    # toe cap
        for dx in (-0.40, 0.0, 0.40):                                                                # claws
            B.plain(ft, (sx * 1.36 + dx, 0.16, 1.33), (0.30, 0.24, 0.18), pal=BASALT)
    return B.T


def define_hammer():
    """local frame of the 1.5.x hammer: grip pivot (0.3, 2.9, 0), head y 0..1.2 along z, haft up to the pommel at 4.2.
    Element y must stay >= -0.1 (model limit), so the spike is on the -z end, the +z end is the striking face."""
    B = Smith(Cracks('smith_hammer', ('magma',)), LAVA_RUNE)
    B.part('hammer', None, (0.3, 2.9, 0.0))
    H, X, Y = 'hammer', -0.04, 0.62
    B.core(H, (X, Y, 0), (0.90, 0.84, 1.60), FIRE)                                                 # molten core
    B.plate(H, (X, 1.06, 0), (0.98, 0.14, 1.40), pal=BASALT, trim=False)                            # top plate
    B.plate(H, (X, 0.18, 0), (0.98, 0.14, 1.40), pal=BASALT, trim=False)                            # bottom plate
    for sx in (1, -1):
        for sz in (1, -1):
            B.plate(H, (X + sx * 0.52, Y, sz * 0.40), (0.14, 0.70, 0.62), pal=BASALT, trim=False)   # cheek plates, slot between
        for y in (0.22, 1.02):
            B.plain(H, (X + sx * 0.46, y, 0), (0.20, 0.20, 1.44), ('z', 45), pal=BASALT)            # chamfered long edges
    for sz in (1, -1):
        B.plate(H, (X, Y, sz * 0.68), (1.16, 1.10, 0.14), pal=OBSIDIAN, trim=False, rune=False)     # iron bands
    B.plate(H, (X, Y, 0.94), (1.42, 1.36, 0.28), pal=BASALT)                                        # striking end (trimmed)
    B.core(H, (X, Y, 1.11), (0.86, 0.80, 0.06), FIRE)                                               # white-hot face
    B.plate(H, (X, Y, -0.92), (1.34, 1.28, 0.26), pal=BASALT, trim=False)                           # back end
    B.plate(H, (X, Y, -1.12), (0.74, 0.74, 0.16), pal=BASALT, trim=False, rune=False)               # spike, stepped
    B.plain(H, (X, Y, -1.28), (0.48, 0.48, 0.16), ('z', 45), pal=BASALT)
    B.plain(H, (X, Y, -1.42), (0.26, 0.26, 0.14), ('z', 45), pal=OBSIDIAN)
    B.plain(H, (X, Y, -1.54), (0.12, 0.12, 0.12), pal=OBSIDIAN)                                     # spike tip
    B.plate(H, (0.26, 1.34, 0), (0.56, 0.26, 0.56), pal=OBSIDIAN, trim=False, rune=False)           # socket
    for sz in (1, -1):
        B.plain(H, (0.27, 1.68, sz * 0.17), (0.12, 0.52, 0.06), pal=OBSIDIAN)                       # langets
    B.raw(H, (0.27, 2.36, 0), (0.30, 1.90, 0.30), shaft_wrap)                                       # wrapped haft
    for y in (1.78, 2.46, 3.12):
        B.plain(H, (0.27, y, 0), (0.40, 0.12, 0.40), pal=OBSIDIAN)                                  # grip rings
    B.plate(H, (0.42, 3.74, 0), (0.62, 0.72, 0.54), pal=BASALT, trim=False)                         # pommel
    B.core(H, (0.42, 3.74, 0), (0.42, 0.46, 0.66), FIRE)                                            # its core, showing at the sides
    B.plain(H, (0.42, 4.14, 0), (0.40, 0.10, 0.40), pal=OBSIDIAN)                                   # cap
    return B.T


if __name__ == '__main__':
    pack = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    credit = 'make_smith_v5 (HobbangEnhance)'
    M.emit_model_set(pack, 'boss/abyssal_smith', define_smith(), height_blocks=9.0, k_units_per_block=4.25, credit=credit)
    M.emit_model_set(pack, 'boss/smith_hammer', define_hammer(), height_blocks=4.2, k_units_per_block=8.0,
                     attach={'boss': 'abyssal_smith', 'part': 'hammer', 'pivot_blocks': [-2.35, 3.5, 0.45]}, credit=credit)
