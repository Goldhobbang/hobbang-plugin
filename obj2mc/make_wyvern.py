#!/usr/bin/env python3
"""Tempest Wyvern 1.5.0 — hand-designed box rig (no Meshy voxels).

Every part is a short list of cuboids in the part's own frame (pivot = origin, +Z forward, +Y up, +X = the
wyvern's left). A cuboid may carry ONE element rotation (axis, +-22.5 / +-45, about its own centre) which is all
Minecraft allows; anything steeper is done with the part's `rest` rotation, i.e. the item_display transformation
(the plugin applies it through DisplayBoss.joint()). Shape follows the blocky reference dragon; the TEXTURE follows the original Meshy Tempest Wyvern (navy plated
hide, yellow lightning, gold spikes/claws/belly) — colours were measured from its texture, patterns are procedural.

Writes assets/hobbang/{items,models/item,textures/item}/boss/tempest_wyvern/<part>.* and
resourcepack/manifests/boss__tempest_wyvern.json (same format as obj2mc + local_offset / rest_rot per part).

usage: python make_wyvern.py [--pack DIR]
"""
import argparse, json, math, os, shutil
import numpy as np
from PIL import Image
from obj2mc import FACE_VERTS, FACE_NORMAL, shelf_pack
from obb import rot

HERE = os.path.dirname(os.path.abspath(__file__))
SCALE = 0.68          # design units -> blocks
PPU = 16              # texels per design unit (~21 px / block)
MODEL_ID = 'boss/tempest_wyvern'
NS = 'hobbang'

# ---------------------------------------------------------------- palette
# colours measured from the original Meshy Tempest Wyvern texture (wyvern_palette.py): navy plated hide,
# yellow lightning cracks, gold spikes / claws / belly scales, slate membrane laced with lightning.
BODY = (46, 55, 70)
BODY_D = (30, 38, 50)
BELLY = (204, 168, 70)
BONE = (250, 206, 48)         # spikes, horns, claws, wing-bone tips
WBONE = (26, 33, 44)          # wing bones
MEMB = (60, 72, 92)           # membrane
EYE = (255, 226, 90)
TOOTH = (236, 230, 214)
MOUTH = (150, 36, 44)
BOLT = (240, 198, 56)         # lightning
BOLT_HOT = (255, 236, 150)

PARTS = {}                    # name -> dict(parent, offset, rest, boxes)


def part(name, parent, offset, rest=(0, 0, 0)):
    PARTS[name] = dict(name=name, parent=parent, offset=tuple(float(x) for x in offset), rest=tuple(float(x) for x in rest), boxes=[])
    return name


def box(p, c, size, color, r=None, kind='plate'):
    """c = centre, size = full size (design units, part frame). r = (axis 'x'|'y'|'z', degrees)"""
    PARTS[p]['boxes'].append(dict(c=tuple(float(x) for x in c), s=tuple(float(x) for x in size), color=color, rot=r, kind=kind))


def spike(p, z, y, w=0.14, h=0.36, l=0.34):
    box(p, (0, y + h * 0.42, z), (w, h, l), BONE, ('x', -22.5), 'solid')


# ---------------------------------------------------------------- spine: chest (root) -> waist -> hips -> tail x6
part('body', None, (0, 2.3, 0.55))
box('body', (0, 0, 0), (1.5, 1.4, 1.7), BODY)
box('body', (0, -0.72, 0), (1.05, 0.1, 1.6), BELLY)
spike('body', 0.45, 0.7); spike('body', -0.4, 0.7)

part('waist', 'body', (0, 0, -0.8))
box('waist', (0, -0.02, -0.6), (1.3, 1.25, 1.35), BODY)
box('waist', (0, -0.67, -0.6), (0.92, 0.1, 1.25), BELLY)
spike('waist', -0.3, 0.6); spike('waist', -0.95, 0.6)

part('hips', 'waist', (0, 0, -1.25))
box('hips', (0, -0.03, -0.55), (1.2, 1.15, 1.25), BODY)
box('hips', (0, -0.63, -0.55), (0.84, 0.1, 1.15), BELLY)
spike('hips', -0.25, 0.54); spike('hips', -0.9, 0.54)

TW = [0.95, 0.8, 0.66, 0.52, 0.4, 0.28]
TL = [1.05, 1.0, 1.0, 0.95, 0.9, 0.9]
TREST = [-8, -4, 2, 4, 4, 4]
prev, off = 'hips', (0, -0.05, -1.15)
for i in range(6):
    n = part(f'tail_{i + 1}', prev, off, (TREST[i], 0, 0))
    w, L = TW[i], TL[i]
    h = w * 0.95
    box(n, (0, 0, -L / 2 + 0.05), (w, h, L + 0.1), BODY)
    if i < 3:
        box(n, (0, -h / 2 - 0.02, -L / 2 + 0.05), (w * 0.7, 0.08, L), BELLY)
    sw = max(0.07, 0.14 - i * 0.012)
    if i < 2:
        spike(n, -L * 0.25, h / 2, sw, 0.3, 0.28); spike(n, -L * 0.75, h / 2, sw, 0.3, 0.28)
    else:
        spike(n, -L * 0.5, h / 2, sw, 0.3 - i * 0.02, 0.26)
    prev, off = n, (0, 0, -L)
box('tail_6', (0, 0, -TL[5] - 0.12), (0.1, 0.34, 0.34), BONE, ('x', 45), 'solid')      # tail-tip barb

# ---------------------------------------------------------------- neck x3 -> head -> jaw
NW = [(0.95, 0.95), (0.8, 0.82), (0.68, 0.7)]
NL = [0.95, 0.9, 0.85]
NREST = [-38, -12, 12]
prev, off = 'body', (0, 0.25, 0.8)
for i in range(3):
    n = part(f'neck_{i + 1}', prev, off, (NREST[i], 0, 0))
    (w, h), L = NW[i], NL[i]
    box(n, (0, 0, L / 2 - 0.05), (w, h, L + 0.1), BODY)
    box(n, (0, -h / 2 - 0.02, L / 2 - 0.05), (w * 0.7, 0.08, L), BELLY)
    spike(n, L * 0.5, h / 2, 0.12, 0.32, 0.3)
    prev, off = n, (0, 0, L)

part('head', 'neck_3', (0, 0, 0.85), (40, 0, 0))
box('head', (0, 0.05, 0.3), (0.8, 0.62, 0.75), BODY)                 # skull
box('head', (0, -0.03, 1.0), (0.62, 0.4, 0.85), BODY)                # snout
box('head', (0, -0.08, 1.5), (0.5, 0.3, 0.2), BODY_D)                # nose
for sx in (1, -1):
    box('head', (sx * 0.3, 0.33, 0.45), (0.2, 0.12, 0.5), BODY_D)                                   # brow ridge
    box('head', (sx * 0.405, 0.12, 0.5), (0.04, 0.14, 0.2), EYE, None, 'glow')                      # eye
    box('head', (sx * 0.27, 0.42, -0.4), (0.16, 0.16, 1.0), BONE, ('x', 22.5), 'solid')             # long horn, back and up
    box('head', (sx * 0.42, 0.12, -0.28), (0.12, 0.12, 0.62), BONE, ('y', -22.5 * sx), 'solid')     # short horn, back and out
    for i in range(4):
        box('head', (sx * 0.27, -0.27, 0.72 + 0.2 * i), (0.06, 0.1, 0.08), TOOTH, None, 'solid')
    box('head', (sx * 0.12, -0.28, 1.56), (0.06, 0.1, 0.06), TOOTH, None, 'solid')

part('jaw', 'head', (0, -0.2, 0.25), (6, 0, 0))
box('jaw', (0, -0.08, 0.65), (0.52, 0.16, 1.25), BODY)
box('jaw', (0, 0.005, 0.7), (0.4, 0.02, 1.0), MOUTH, None, 'solid')
box('jaw', (0, -0.17, 0.6), (0.36, 0.05, 1.0), BELLY)
for sx in (1, -1):
    for i in range(4):
        box('jaw', (sx * 0.22, 0.05, 0.5 + 0.2 * i), (0.05, 0.09, 0.07), TOOTH, None, 'solid')


# ---------------------------------------------------------------- legs: thigh -> shin -> foot (3 toes + claws)
def leg(tag, parent, at, lt, ls, rests):
    for sx, q in ((1, 'l'), (-1, 'r')):
        t = part(f'thigh_{tag}{q}', parent, (sx * at[0], at[1], at[2]), (rests[0], 0, 0))
        box(t, (0, -lt / 2 + 0.05, 0), (0.5, lt + 0.2, 0.62), BODY)
        s = part(f'shin_{tag}{q}', t, (0, -lt, 0), (rests[1], 0, 0))
        box(s, (0, -ls / 2, 0), (0.36, ls + 0.16, 0.42), BODY)
        f = part(f'foot_{tag}{q}', s, (0, -ls, 0), (rests[2], 0, 0))
        box(f, (0, -0.06, 0.2), (0.46, 0.2, 0.6), BODY)
        for dx in (-0.16, 0, 0.16):
            box(f, (dx, -0.09, 0.6), (0.12, 0.14, 0.3), BODY_D)
            box(f, (dx, -0.1, 0.81), (0.08, 0.1, 0.16), BONE, None, 'solid')


leg('f', 'body', (0.72, -0.35, 0.45), 1.0, 0.95, (-25, 45, -20))
leg('h', 'hips', (0.68, -0.3, -0.55), 1.1, 1.0, (-30, 60, -30))

# ---------------------------------------------------------------- wings: arm -> forearm -> 3 fingers, membrane plates ride the bones
FL = [3.2, 3.0, 2.5]            # finger lengths
FA = [8, 40, 74]                # finger fan angles (about the wing normal, 0 = straight out, 90 = pointing back)
for sx, q in ((1, 'l'), (-1, 'r')):
    a = part(f'wing_arm_{q}', 'body', (sx * 0.62, 0.6, 0.35), (0, sx * 12, sx * 32))
    box(a, (sx * 0.75, 0, 0), (1.6, 0.26, 0.3), WBONE)
    box(a, (sx * 0.8, -0.036, -0.95), (1.5, 0.05, 1.8), MEMB, None, 'memb')
    fo = part(f'wing_fore_{q}', a, (sx * 1.5, 0, 0), (0, sx * -30, sx * -14))
    box(fo, (sx * 0.85, 0, 0), (1.8, 0.22, 0.26), WBONE)
    box(fo, (sx * 0.9, -0.024, -1.1), (1.75, 0.05, 2.1), MEMB, None, 'memb')
    box(fo, (sx * 1.7, 0.02, 0.2), (0.12, 0.12, 0.36), BONE, None, 'solid')            # wrist claw
    for i in range(3):
        L = FL[i]
        f = part(f'wing_f{i + 1}_{q}', fo, (sx * 1.7, 0, 0), (0, sx * FA[i], 0))
        box(f, (sx * L / 2, 0, 0), (L, 0.14, 0.16), WBONE)
        box(f, (sx * (L + 0.08), 0, 0), (0.2, 0.1, 0.12), BONE, None, 'solid')
        if i > 0:      # membrane wedge toward the previous finger: 4 stepped plates, the outer one shorter (scalloped edge)
            d = math.radians(FA[i] - FA[i - 1])
            for (x0, x1), fac in zip(((0.04, 0.3), (0.3, 0.55), (0.55, 0.8), (0.8, 0.97)), (1.08, 1.04, 0.96, 0.6)):
                wz = math.tan(d) * (x0 + x1) / 2 * L * fac
                box(f, (sx * (x0 + x1) / 2 * L, -0.012 * i, wz / 2), ((x1 - x0) * L + 0.02, 0.04, wz), MEMB, None, 'memb')
        if i == 2:     # trailing wedge behind the last finger, down to the forearm membrane
            for (x0, x1), fac in zip(((0.04, 0.34), (0.34, 0.64), (0.64, 0.9)), (1.0, 1.0, 0.6)):
                wz = math.tan(math.radians(24)) * (x0 + x1) / 2 * L * fac
                box(f, (sx * (x0 + x1) / 2 * L, -0.03, -wz / 2), ((x1 - x0) * L + 0.02, 0.04, wz), MEMB, None, 'memb')


# ---------------------------------------------------------------- maths
def zlib_seed(name, bi, fn):
    import zlib
    return zlib.crc32(f'{name}/{bi}/{fn}'.encode())


def rxyz(r):
    return rot(0, r[0]) @ rot(1, r[1]) @ rot(2, r[2])


def fk(pose=None):
    """rest (or posed) world transform per part: name -> (pos, R). same chain as the plugin's Rigid"""
    out = {}
    def go(n):
        if n in out: return out[n]
        p = PARTS[n]
        Rl = rxyz(p['rest']) @ rxyz((pose or {}).get(n, (0, 0, 0)))
        if p['parent'] is None:
            out[n] = (np.array(p['offset']) * SCALE, Rl)
        else:
            pp, pr = go(p['parent'])
            out[n] = (pp + pr @ (np.array(p['offset']) * SCALE), pr @ Rl)
        return out[n]
    for n in PARTS: go(n)
    return out


def bolt(img, rng, n, glow=0.35):
    """n jagged lightning lines across the longer side of the face, with an occasional fork and a soft glow"""
    H, W = img.shape[:2]
    mask = np.zeros((H, W), bool)
    long_x = W >= H
    L, T = (W, H) if long_x else (H, W)
    for _ in range(n):
        walkers = [(0, int(rng.integers(0, T)), L)]
        forks = 0
        while walkers:
            a, t, end = walkers.pop()
            while a < end:
                t = int(np.clip(t, 0, T - 1))
                if long_x: mask[t, a] = True
                else: mask[a, t] = True
                step = int(rng.choice([-1, 0, 0, 1]))
                if step and 0 <= t + step < T:      # fill the corner so the line stays connected
                    if long_x: mask[t + step, a] = True
                    else: mask[a, t + step] = True
                t += step
                if forks < 1 and rng.random() < 0.05:
                    forks += 1
                    walkers.append((a + 1, t + int(rng.choice([-2, 2])), min(L, a + 2 + int(rng.integers(3, 9)))))
                a += 1
    near = np.zeros_like(mask)
    near[1:, :] |= mask[:-1, :]; near[:-1, :] |= mask[1:, :]; near[:, 1:] |= mask[:, :-1]; near[:, :-1] |= mask[:, 1:]
    near &= ~mask
    img[near] = img[near] * (1 - glow) + np.array(BOLT) * glow
    img[mask] = np.array(BOLT)
    core = mask & (rng.random((H, W)) < 0.25)
    img[core] = np.array(BOLT_HOT)


def face_pixels(b, fn, W, H, seed):
    """the original wyvern's look on flat box faces: plated navy hide with a few lightning cracks, gold belly scales,
    gold spikes, slate membrane laced with lightning. deterministic (seeded per face). rows run v0->v1."""
    rng = np.random.default_rng(seed)
    base = np.array(b['color'], float)
    kind = b['kind']
    if kind == 'glow':
        return np.tile(base, (H, W, 1))
    v = (np.arange(H) + 0.5) / H
    if fn == 'up': shade = np.full(H, 1.12)
    elif fn == 'down': shade = np.full(H, 0.82)
    else: shade = 1.08 - 0.18 * v
    if fn in ('east', 'west'): shade = shade * 0.96
    img = base[None, None, :] * shade[:, None, None] * np.ones((H, W, 1))
    col = tuple(b['color'])
    if kind in ('plate', 'memb') and col != BELLY:
        # plated hide: 3 px scale cells, each a slightly different navy
        cs = 3
        cells = rng.choice([0.72, 0.86, 0.94, 1.0, 1.0, 1.08, 1.2, 1.42], size=(H // cs + 1, W // cs + 1))
        if kind == 'memb': cells = 1 + (cells - 1) * 0.35
        img *= np.kron(cells, np.ones((cs, cs)))[:H, :W, None]
    if col == BELLY:
        img[(np.arange(H) % 4 == 3)] *= 0.72            # belly scale bands
        if W >= 4: img[:, [0, -1]] *= 0.8
    elif kind == 'plate' and W >= 6 and H >= 6:
        edge = np.ones((H, W)); edge[0, :] = edge[-1, :] = 0.8; edge[:, 0] = edge[:, -1] = 0.8
        img *= edge[..., None]
        if col == BODY and W * H >= 260 and fn != 'down':
            bolt(img, rng, 1, 0.25)
    elif kind == 'memb':
        if fn in ('up', 'down') and W >= 6 and H >= 6:
            bolt(img, rng, max(1, int(round(min(W, H) / 14))), 0.3)
    elif kind == 'solid' and col == BONE and fn not in ('up', 'down'):
        img *= (0.78 + 0.3 * (1 - v))[:, None, None]      # spikes: darker gold toward the base
    return np.clip(img, 0, 255)


def build(pack):
    S = np.array([-1.0, 1.0, -1.0])
    ext = 0.0
    for p in PARTS.values():
        for b in p['boxes']:
            c = np.abs(np.array(b['c'])) * SCALE; h = np.array(b['s']) / 2 * SCALE
            ext = max(ext, float((c + h * (1.42 if b['rot'] else 1.0)).max()))
    k = min(16.0, math.floor((24.0 - 0.5) / ext * 4) / 4)
    disp = 16.0 / k
    for sub in ('models/item', 'textures/item', 'items'):
        shutil.rmtree(os.path.join(pack, 'assets', NS, *sub.split('/'), *MODEL_ID.split('/')), ignore_errors=True)
    rest = fk()
    man = {'id': f'{NS}:{MODEL_ID}', 'height_blocks': 6.0, 'resolution': 0, 'texels_per_voxel': 0, 'model_units_per_block': k,
           'item_display_scale': disp, 'front': '+Z (boss local, yaw 0 = south)',
           'note': 'hand-designed box rig (make_wyvern.py). rest pose = chain of local_offset + rest_rot (deg, applied X then Y then Z '
                   'in the parent frame); pivot_blocks is the resulting rest-pose pivot in boss space.',
           'parts': []}
    total = 0
    for p in PARTS.values():
        rects, jobs = [], []
        for bi, b in enumerate(p['boxes']):
            c = np.array(b['c']) * SCALE; h = np.array(b['s']) / 2 * SCALE
            cm = 8 + k * S * c; hm = k * h
            b['_from'] = cm - hm; b['_to'] = cm + hm
            for fn in FACE_NORMAL:
                vs = np.array(FACE_VERTS[fn], float)
                cn = lambda t: b['_from'] + (b['_to'] - b['_from']) * t
                v0, v1, v3 = cn(vs[0]), cn(vs[1]), cn(vs[3])
                W = max(1, int(round(np.linalg.norm(v3 - v0) / k / SCALE * PPU)))
                H = max(1, int(round(np.linalg.norm(v1 - v0) / k / SCALE * PPU)))
                rects.append((W, H)); jobs.append((bi, fn))
        pos, size = shelf_pack(rects, pad=1)
        atlas = np.zeros((size, size, 4), np.uint8)
        elements = [None] * len(p['boxes'])
        for (bi, fn), (W, H), (px, py) in zip(jobs, rects, pos):
            b = p['boxes'][bi]
            atlas[py:py + H, px:px + W, :3] = face_pixels(b, fn, W, H, zlib_seed(p['name'], bi, fn)).astype(np.uint8)
            atlas[py:py + H, px:px + W, 3] = 255
            atlas[py - 1, px:px + W] = atlas[py, px:px + W]; atlas[py + H, px:px + W] = atlas[py + H - 1, px:px + W]
            atlas[py - 1:py + H + 1, px - 1] = atlas[py - 1:py + H + 1, px]; atlas[py - 1:py + H + 1, px + W] = atlas[py - 1:py + H + 1, px + W - 1]
            if elements[bi] is None:
                el = {'from': [round(float(x), 4) for x in b['_from']], 'to': [round(float(x), 4) for x in b['_to']]}
                if b['rot']:
                    ax, deg = b['rot']
                    el['rotation'] = {'angle': deg if ax == 'y' else -deg, 'axis': ax,
                                      'origin': [round(float(x), 4) for x in (b['_from'] + b['_to']) / 2]}
                el['faces'] = {}
                elements[bi] = el
            elements[bi]['faces'][fn] = {'uv': [round(px * 16.0 / size, 5), round(py * 16.0 / size, 5),
                                                 round((px + W) * 16.0 / size, 5), round((py + H) * 16.0 / size, 5)], 'texture': '#t'}
        tex_ref = f'{NS}:item/{MODEL_ID}/{p["name"]}'
        model = {'credit': 'make_wyvern (HobbangEnhance)', 'texture_size': [size, size], 'textures': {'t': tex_ref, 'particle': tex_ref},
                 'elements': elements, 'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}
        for sub, ext_, data in (('models/item', '.json', model), ('items', '.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{MODEL_ID}/{p["name"]}'}})):
            d = os.path.join(pack, 'assets', NS, *sub.split('/'), *MODEL_ID.split('/'))
            os.makedirs(d, exist_ok=True)
            with open(os.path.join(d, p['name'] + ext_), 'w') as fh:
                json.dump(data, fh, separators=(',', ':') if sub.startswith('models') else None, indent=None if sub.startswith('models') else 2)
        d = os.path.join(pack, 'assets', NS, 'textures', 'item', *MODEL_ID.split('/'))
        os.makedirs(d, exist_ok=True)
        Image.fromarray(atlas).save(os.path.join(d, p['name'] + '.png'), optimize=True)
        piv = rest[p['name']][0]
        total += len(elements)
        man['parts'].append({'name': p['name'], 'item_model': f'{NS}:{MODEL_ID}/{p["name"]}', 'parent': p['parent'],
                             'pivot_blocks': [round(float(x), 4) for x in piv], 'translation': [round(float(x), 4) for x in piv],
                             'scale': disp, 'spin_axis': None, 'mode': 'box',
                             'local_offset': [round(x * SCALE, 4) for x in p['offset']], 'rest_rot': list(p['rest']),
                             'cuboids': len(elements), 'cuboids_voxel': 0, 'cuboids_obb': len(elements),
                             'faces': len(jobs), 'texture_px': size, 'voxels': 0})
    man['total_cuboids'] = total
    md = os.path.join(os.path.dirname(os.path.abspath(pack).rstrip('/\\')), 'manifests')
    os.makedirs(md, exist_ok=True)
    json.dump(man, open(os.path.join(md, MODEL_ID.replace('/', '__') + '.json'), 'w'), indent=2)
    print(f'[wyvern] {len(PARTS)} parts, {total} cuboids, {k} units/block, item_display scale {disp:.4f}, max extent {ext:.2f} blocks')
    return man


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--pack', default=os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    build(ap.parse_args().pack)
