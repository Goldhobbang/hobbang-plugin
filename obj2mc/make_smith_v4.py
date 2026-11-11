"""Abyssal Smith v4: the v3 silhouette, rebuilt rough and heavy from the original mesh.

Body (everything but the head), per rig part, straight from the original OBJ voxelised at RES voxels/block:
  armour  = the mesh's outer shell, split by the original texture into dark / mid / light stone, leather (apron) and
            molten (right hand) chunks. Each chunk is its own cuboid with a drawn rim, pushed out by a fixed amount taken
            from its position, so the surface is broken and uneven like the original's stacked rock.
  cracks  = shell voxels the original paints cyan / violet are NOT armoured. They stay open.
  energy  = the body's interior, filled with glowing boxes, plus a bright vein sunk into every crack. Light is only
            ever seen THROUGH the armour: the cyan crystals that stood up from head and shoulders are removed
            (any volume whose surrounding surface is mostly cyan is dropped).
Overlays kept from v3 for weight: three pauldron tiers, elbow / knee cops. Head: the v3 skull with more pieces (split
cranium with a glowing suture, separate teeth, heavier brow), no crystals.
Hammer: same shell / crack / core treatment on the hammer mesh.
Part names, parents, joint pivots unchanged.          Run:  python make_smith_v4.py
"""
import json, os, zlib
import numpy as np
from scipy import ndimage
from scipy.spatial import cKDTree
import obj2mc
import make_smith as M
import make_smith_v3 as V3
import smith_analyze as A
from make_smith import (STONE_VOID, STONE_DARK, STONE_BASE, STONE_MID, STONE_LIGHT, STONE_HI,
                        CYAN_DEEP, CYAN_MID, CYAN_GLOW, CYAN_BRIGHT, CYAN_WHITE,
                        MAGMA_DARK, MAGMA_CRIMSON, MAGMA_RED, MAGMA_ORANGE, MAGMA_GOLD, MAGMA_BRIGHT)

HERE = os.path.dirname(os.path.abspath(__file__))
RES = 6                 # body voxels per block (0.167 block chunks)
HAMMER_RES = 8
box = V3.box

# chunk painters: three stone tones, leather, molten, basalt
DARKP = (STONE_VOID, STONE_VOID, STONE_DARK, STONE_BASE, STONE_MID, STONE_LIGHT)
CHUNK = {
    'dark': V3.metal(V3.IRONP, rivets=False), 'mid': V3.metal(V3.STEELP, rivets=False), 'light': V3.metal(V3.BONEP, rivets=False),
    'leather': V3.metal(V3.LEATHERP, rivets=False), 'hot': V3.metal(V3.HOTP, rivets=False),
    'basalt': V3.metal(V3.BASALTP, rivets=False), 'basalt_hi': V3.metal((M.BASALT_VOID, M.BASALT_DARK, M.BASALT_BASE, M.BASALT_EDGE, M.BASALT_EDGE, (110, 72, 62)), rivets=False),
}


def vein(pal):
    """the light in a crack: bright core, white sparks at a fixed pitch"""
    deep, mid, glow_, bright, white = pal
    def f(fn, W, H):
        img = V3.canvas(W, H, bright)
        if W >= 3 and H >= 3:
            V3.rect(img, 0, 0, W - 1, 0, glow_); V3.rect(img, 0, H - 1, W - 1, H - 1, glow_)
            V3.rect(img, 0, 0, 0, H - 1, glow_); V3.rect(img, W - 1, 0, W - 1, H - 1, glow_)
        for i in range(max(W, H)):
            if i % 4 == 1: V3.px(img, min(W - 1, i if W >= H else W // 2), min(H - 1, i if H > W else H // 2), white)
        return img
    return f


VEIN_BLUE = vein((CYAN_DEEP, CYAN_MID, CYAN_GLOW, CYAN_BRIGHT, CYAN_WHITE))
VEIN_FIRE = vein((MAGMA_CRIMSON, MAGMA_RED, MAGMA_ORANGE, MAGMA_GOLD, MAGMA_BRIGHT))


def grow_of(blo):
    """fixed outward push per chunk (0 / 0.025 / 0.055 block) from its grid position: uneven, repeatable, no RNG"""
    return (0.0, 0.025, 0.055)[zlib.crc32(bytes(int(x) & 255 for x in blo)) % 3]


def rough(T, name, res, classify, energy_kind, vein_kind, only=None, skip=()):
    """fill the part table T from the mesh `name`. classify(rgb, feature masks) -> array of chunk-class names or '' for
    a crack. Returns counts."""
    cfg, v, f, uv, tex, pts, col, nrm = A.load(name, 0.02)
    occ, surf, lo = obj2mc.voxelize(pts, res)
    occ = obj2mc.remove_specks(occ, 8)
    parts = obj2mc.scale_layout(cfg)
    grid = obj2mc.assign_parts(occ, lo, res, parts)
    inner = ndimage.binary_erosion(occ)
    shell = occ & ~inner
    ijk = np.argwhere(shell)
    c = (ijk + lo + 0.5) / res
    _, idx = cKDTree(pts).query(c)
    rgb = col[idx]
    cls = classify(rgb, A.feature_masks(rgb), c)
    crack = cls == ''
    # volumes wrapped mostly in glow colour are crystals standing out of the body: drop them entirely
    cg = np.zeros(occ.shape, np.float32); sg = np.zeros(occ.shape, np.float32)
    cg[tuple(ijk[crack].T)] = 1; sg[tuple(ijk[~crack].T)] = 1
    cy = ndimage.uniform_filter(cg, 5, mode='constant'); st = ndimage.uniform_filter(sg, 5, mode='constant')
    crystal = cy > 0.55 * (cy + st)
    keep = occ & ~crystal
    n = {'armour': 0, 'energy': 0, 'vein': 0}
    cgrid = np.full(occ.shape, '', dtype=object); cgrid[tuple(ijk.T)] = cls
    for pi, p in enumerate(parts):
        pn = p['name']
        if pn in skip or (only and pn not in only): continue
        mine = (grid == pi) & keep
        for (blo, bhi) in obj2mc.best_boxes(mine & inner) if (mine & inner).any() else []:        # the inside
            M.add_box(T, pn, ((blo + bhi) / 2 + lo) / res, (bhi - blo) / res - 0.02, STONE_BASE, None, energy_kind)
            n['energy'] += 1
        sh = mine & shell
        for k in sorted(set(cls) - {''}):                                                         # armour chunks per tone
            m = sh & (cgrid == k)
            for (blo, bhi) in obj2mc.best_boxes(m) if m.any() else []:
                g = grow_of(blo)
                M.add_box(T, pn, ((blo + bhi) / 2 + lo) / res, (bhi - blo) / res + 2 * g, STONE_BASE, None, CHUNK[k])
                n['armour'] += 1
        m = sh & (cgrid == '')                                                                    # veins, sunk in the cracks
        for (blo, bhi) in obj2mc.best_boxes(m) if m.any() else []:
            M.add_box(T, pn, ((blo + bhi) / 2 + lo) / res, np.maximum((bhi - blo) / res - 0.07, 0.07), STONE_BASE, None, vein_kind)
            n['vein'] += 1
    return n


def body_classes(rgb, fm, c):
    lum = rgb @ [0.299, 0.587, 0.114]
    out = np.full(len(rgb), 'mid', dtype=object)
    stone = ~(fm['cyan'] | fm['violet'] | fm['magma'])
    lo_, hi_ = np.percentile(lum[stone], [38, 78])
    out[lum < lo_] = 'dark'; out[lum > hi_] = 'light'
    # leather: the apron's warm brown, clearly redder than the stone
    r, g, b = rgb[:, 0], rgb[:, 1], rgb[:, 2]
    apron_zone = (c[:, 2] > 0.55) & (np.abs(c[:, 0]) < 1.05) & (c[:, 1] > 1.0) & (c[:, 1] < 6.3)     # where the mesh wears it
    out[(r > b + 14) & (r > g + 5) & (lum > 40) & stone & apron_zone] = 'leather'
    out[fm['magma']] = 'hot'
    out[fm['cyan'] | fm['violet']] = ''
    return out


def hammer_classes(rgb, fm, c):
    lum = rgb @ [0.299, 0.587, 0.114]
    out = np.full(len(rgb), 'basalt', dtype=object)
    out[lum > np.percentile(lum[~fm['magma']], 70)] = 'basalt_hi'
    out[fm['magma'] & (lum > 70)] = ''                      # the bright lava seams stay open
    return out


def define_smith(stats):
    T = V3.define_smith()                                   # skull + rig (names, parents, pivots)
    for name, p in T.items():
        if name == 'head':
            p['boxes'] = [b for b in p['boxes'] if b['kind'] not in (V3.crystal, V3.teeth)
                          and b['s'] not in ((1.00, 0.50, 1.16), (0.70, 0.16, 0.86))]
        else:
            p['boxes'] = []
    stats['body'] = rough(T, 'abyssal_smith', RES, body_classes, V3.blue, VEIN_BLUE, skip=('head',))
    # ---- overlays for weight (no crystals anywhere)
    (ax, ay, az), (aw, ah, ad) = V3.MESH['arm']
    (fx, fy, fz), _ = V3.MESH['fore']
    (sx_, sy_, sz_), (sw, shh, sd) = V3.MESH['shin']
    for sx, q in ((1, 'l'), (-1, 'r')):
        for i, (dy, w_, d_) in enumerate(((0.70, 1.50, 1.66), (0.30, 1.36, 1.52), (-0.08, 1.20, 1.38))):
            box(T, f'arm_{q}', (sx * (ax + 0.20 + i * 0.08), ay + dy, az + 0.14), (w_, 0.30, d_), V3.iron_inlay, ('z', -sx * 22.5))
            box(T, f'arm_{q}', (sx * (ax + 0.20 + i * 0.08), ay + dy + 0.16, az + 0.14), (w_ * 0.72, 0.12, d_ * 0.80), CHUNK['dark'], ('z', -sx * 22.5))
        for dz in (-0.52, 0.0, 0.52):                                                        # iron studs, not crystals
            box(T, f'arm_{q}', (sx * (ax + 0.62), ay + 0.92, az + 0.14 + dz), (0.20, 0.26, 0.20), V3.iron_plain, ('z', -sx * 22.5))
        box(T, f'fore_{q}', (sx * (fx - 0.02), 5.24, fz - 0.10), (0.70, 0.44, 0.70), V3.iron, ('x', 45))
        box(T, f'shin_{q}', (sx * sx_, 1.52, sz_ + sd / 2 - 0.10), (0.80, 0.48, 0.48), V3.iron, ('x', 45))
    # the neck opening (the head is a separate part): close it so no light stands above the shoulders
    box(T, 'chest', (0, 7.27, 0.0), (2.05, 0.16, 1.80), CHUNK['dark'])
    box(T, 'chest', (0, 7.40, 0.02), (0.90, 0.30, 0.90), CHUNK['mid'])
    # ---- skull, more pieces
    (hx, hy, hz), (hw, hh, hd) = V3.MESH['head']
    Z = hz + hd / 2
    for sx in (1, -1):                                                                       # cranium halves: a glowing suture between
        box(T, 'head', (sx * 0.27, 8.42, hz - 0.08), (0.46, 0.50, 1.16), V3.bone)
        box(T, 'head', (sx * 0.20, 8.70, hz - 0.12), (0.32, 0.16, 0.86), V3.bone)
        box(T, 'head', (sx * 0.62, 8.22, Z - 0.02), (0.30, 0.26, 0.34), V3.bone, ('z', sx * 45))          # heavy outer brow knot
        box(T, 'head', (sx * 0.30, 8.62, Z - 0.34), (0.30, 0.14, 0.30), V3.bone, ('x', -22.5))            # frontal bosses
        box(T, 'head', (sx * 0.56, 7.56, Z - 0.20), (0.18, 0.30, 0.30), V3.bone, ('z', -sx * 22.5))       # jaw corner
    for i in range(6):                                                                       # separate teeth, uneven lengths
        x = -0.275 + i * 0.11
        box(T, 'head', (x, 7.50 - (0.02 if i % 2 else 0), Z - 0.05), (0.09, 0.14 + (0.04 if i % 2 else 0), 0.16), V3.bone)
        box(T, 'head', (x * 0.86, 7.36, Z - 0.07), (0.08, 0.12, 0.14), V3.bone)
    box(T, 'head', (0, 7.44, Z - 0.16), (0.60, 0.26, 0.10), V3.void)                          # the dark of the mouth
    box(T, 'head', (0, 8.44, hz - 0.08), (0.06, 0.40, 1.04), VEIN_BLUE)                       # light in the suture
    return T


def define_hammer(stats):
    T = M.new_part_table()
    M.add_part(T, 'hammer', None, (0.3, 2.9, 0.0))
    stats['hammer'] = rough(T, 'smith_hammer', HAMMER_RES, hammer_classes, V3.fire, VEIN_FIRE)
    return T


if __name__ == '__main__':
    pack = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    credit = 'make_smith_v4 (HobbangEnhance)'
    stats = {}
    M.emit_model_set(pack, 'boss/abyssal_smith', define_smith(stats), height_blocks=9.0, k_units_per_block=4.25, credit=credit)
    M.emit_model_set(pack, 'boss/smith_hammer', define_hammer(stats), height_blocks=4.2, k_units_per_block=8.0,
                     attach={'boss': 'abyssal_smith', 'part': 'hammer', 'pivot_blocks': [-2.35, 3.5, 0.45]}, credit=credit)
    print(json.dumps(stats))
