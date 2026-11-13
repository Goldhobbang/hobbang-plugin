"""HobbangEnhance boss weapons, texture pass v3 (weapons-texture-v3).

  weapon/stormcaller        height 2.2, k=14.5,  pivot (0, 0.6, 0),       96 texels/block
  weapon/crystal_greatsword height 2.3, k=13.0,  pivot (0, 0.52, 0),     128 texels/block
  weapon/chrono_dagger      height 2.5, k=11.75, pivot (0.54, 0.55, 0),   96 texels/block
(weapon/smith_hammer is not built here.)

Shapes are a few long plates and bars (single-axis z rotations in 22.5 degree steps).
The detail lives in the textures:
  1. The original mesh is ray-cast into front (+Z) and back (-Z) orthographic paintings,
     4 samples per texel, median-smoothed so the Meshy texture noise turns into clean areas.
  2. Weapon line art (edge bevels, lightning, crystal facets, runes, clock, grip wrap) is
     painted on top of those paintings in world X/Y.
  3. Every face samples the shared painting, so neighbouring plates continue one picture.
     Square bars (hafts, grips) wrap the front painting around their sides.
  4. Colours snap to a palette taken from the original texture plus the extracted constants.
Plates that need an outline (dragon, pommel, clock head) cut it out of the painting
with 0/255 alpha. The in-hand model reuses the held geometry and atlas with its own display.
"""
import json
import math
import os
from pathlib import Path

import numpy as np
import trimesh
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

from obb import rot
from obj2mc import FACE_NORMAL, FACE_VERTS, load_mesh, shelf_pack
from make_smith import HERE, NS, add_box, add_part, new_part_table

CONFIGS = os.path.join(HERE, 'configs')
SS = 4                                   # painting samples per texel edge
FLIP = np.array([-1.0, 1.0, -1.0])       # world <-> model axes
CREDIT = 'make_weapon_models (weapons-3d-v4)'

# ----------------------------------------------------------------------------- extracted original palettes (models-3d/)
CRYS_VOID    = ( 35,   0,  37)
CRYS_DEEP    = (105,   0, 250)
CRYS_BASE    = (145,   0, 255)
CRYS_MID     = (187,  51, 255)
CRYS_BRIGHT  = (223, 113, 255)
CRYS_GLOW    = (246, 162, 255)
CRYS_WHITE   = (255, 211, 255)
OBS_DARK     = ( 35,   0,  37)
OBS_BASE     = ( 66,  36,  42)
OBS_GOLD     = (100,  64,  55)
OBS_GOLD_HI  = (131,  96,  67)

STORM_VOID   = (  3,  11,   0)
STORM_DARK   = ( 20,  24,   2)
DRAGON_DARK  = ( 30,  33,  37)
DRAGON_STEEL = ( 59,  60,  67)
DRAGON_RIM   = ( 86,  81,  89)
GOLD_AMBER   = (184, 108,  34)
STORM_IRON   = ( 43,  43,  25)
BRONZE_MUTE  = ( 87,  72,  42)
BRONZE_M_HI  = (128,  98,  48)
GOLD_EDGE    = (230, 156,  65)
GOLD_BRIGHT  = (255, 204,  72)
GOLD_WHITE   = (255, 228,  94)

BRASS_VOID   = ( 52,  29,   0)
BRASS_DARK   = ( 78,  42,   0)
BRASS_BASE   = (107,  66,   0)
BRASS_MID    = (144,  87,   6)
BRASS_GOLD   = (174, 108,  17)
BRASS_HI     = (201, 129,  35)
BRASS_BRIGHT = (242, 176,  56)
DIAL_IVORY   = (255, 254, 214)
DIAL_RIM     = (196, 168, 112)
DIAL_HAND    = ( 52,  29,   0)
CHRONO_TEAL  = ( 68, 147, 138)
CHRONO_GLOW  = (106, 196, 184)


# ----------------------------------------------------------------------------- painting of the original mesh
def fill_empty(rgb, known):
    idx = ndimage.distance_transform_edt(~known, return_distances=False, return_indices=True)
    return rgb[tuple(idx)]


def kmeans(x, k, steps=25):
    """Deterministic k-means: centres start at luminance quantiles."""
    order = np.argsort(x @ [.299, .587, .114])
    c = x[order[((np.arange(k) + .5) / k * len(x)).astype(int)]].astype(float)
    for _ in range(steps):
        a = ((x[:, None] - c[None]) ** 2).sum(-1).argmin(1)
        for j in range(k):
            if (a == j).any():
                c[j] = x[a == j].mean(0)
    return np.unique(np.rint(c).astype(int), axis=0)


class Canvas:
    """Front (side +1, seen from +Z) and back (side -1) paintings of the original mesh in world X/Y."""

    def __init__(self, weapon, tpb, pad=0.06, x_min=None):
        cfg = json.loads(Path(CONFIGS, f'weapon_{weapon}.json').read_bytes())
        v, f, uv, tex = load_mesh(cfg, CONFIGS)
        self.tpb, self.ppb = tpb, tpb * SS
        lo, hi = v.min(0) - pad, v.max(0) + pad
        if x_min is not None:                    # room for painted parts beyond the original mesh
            lo[0] = min(lo[0], x_min)
        self.x0, self.y1 = lo[0], hi[1]
        self.w = math.ceil((hi[0] - lo[0]) * self.ppb)
        self.h = math.ceil((hi[1] - lo[1]) * self.ppb)
        self.X, self.Y = np.meshgrid(self.x0 + (np.arange(self.w) + .5) / self.ppb,
                                     self.y1 - (np.arange(self.h) + .5) / self.ppb)
        mesh = trimesh.Trimesh(v, f, process=False)
        th, tw = tex.shape[:2]
        self.arr, self.mask = {}, {}
        for side in (1, -1):
            origin = np.stack([self.X.ravel(), self.Y.ravel(), np.full(self.X.size, 2.0 * side)], 1)
            way = np.tile([0.0, 0.0, -side], (self.X.size, 1))
            loc, ray, tri = mesh.ray.intersects_location(origin, way, multiple_hits=False)
            bary = trimesh.triangles.points_to_barycentric(v[f[tri]], loc)
            t = (uv[f[tri]] * bary[..., None]).sum(1)
            rgb = np.zeros((self.X.size, 3), np.uint8)
            rgb[ray] = tex[np.clip(((1 - t[:, 1]) * th).astype(int), 0, th - 1),
                           np.clip((t[:, 0] * tw).astype(int), 0, tw - 1), :3]
            hit = np.zeros(self.X.size, bool)
            hit[ray] = True
            hit = hit.reshape(self.h, self.w)
            rgb = fill_empty(rgb.reshape(self.h, self.w, 3), hit)
            self.arr[side] = np.array(Image.fromarray(rgb).filter(ImageFilter.MedianFilter(5)))
            self.mask[side] = ndimage.median_filter(hit.astype(np.uint8), 5).astype(bool)
        self.solid = self.mask[1] | self.mask[-1]
        self.closed = ndimage.binary_fill_holes(ndimage.binary_closing(self.solid, iterations=2 * SS))

    # -- coordinates
    def px(self, x, y):
        return (x - self.x0) * self.ppb, (self.y1 - y) * self.ppb

    def pts(self, coords):
        return [self.px(x, y) for x, y in coords]

    def index(self, x, y):
        i = np.clip(((self.y1 - y) * self.ppb).astype(int), 0, self.h - 1)
        j = np.clip(((x - self.x0) * self.ppb).astype(int), 0, self.w - 1)
        return i, j

    # -- masks and painting
    def polygon(self, *polys):
        im = Image.new('L', (self.w, self.h))
        d = ImageDraw.Draw(im)
        for poly in polys:
            d.polygon(self.pts(poly), fill=255)
        return np.asarray(im) > 0

    def alpha(self, mask):
        """Alpha callable for a box: world X/Y -> 0/1 from a canvas mask."""
        return lambda x, y: mask[self.index(x, y)].astype(float)

    def draw(self, fn):
        for side in (1, -1):
            im = Image.fromarray(self.arr[side])
            fn(ImageDraw.Draw(im), side)
            self.arr[side] = np.array(im)

    def put(self, mask, color, sides=(1, -1)):
        for side in sides:
            self.arr[side][mask] = color

    def line(self, draw, coords, color, texels):
        draw.line(self.pts(coords), fill=color, width=max(1, round(texels * SS)), joint='curve')

    def edge_bands(self, solid, region, center, outer=(), inner=()):
        """Colour bands along the outline of `solid`, inside `region`. A pixel is on the inner
        edge when its nearest outside pixel is closer to `center` than the pixel itself."""
        dist, (iy, ix) = ndimage.distance_transform_edt(solid, return_indices=True)
        cx, cy = center
        near_out = np.hypot(self.X[iy, ix] - cx, self.Y[iy, ix] - cy)
        is_inner = near_out < np.hypot(self.X - cx, self.Y - cy)
        band = dist / SS
        for colors, sel in ((outer, ~is_inner), (inner, is_inner)):
            for i, color in enumerate(colors):
                if color is not None:
                    self.put(region & solid & sel & (band > i) & (band <= i + 1), color)

    def metal_ramp(self, solid, region, center, ramp, metal, step=2.2, contrast=5.0):
        """Repaint metal texels near the outer outline of `solid`: the ramp level grows with the
        distance (texels) from the edge and follows the smoothed brightness of the original, so the
        feather shapes stay while the speckle goes. Every fifth texel band adds a brushed grain."""
        dist, (iy, ix) = ndimage.distance_transform_edt(solid, return_indices=True)
        cx, cy = center
        outer = np.hypot(self.X[iy, ix] - cx, self.Y[iy, ix] - cy) >= np.hypot(self.X - cx, self.Y - cy)
        d = dist / SS
        for side in (1, -1):
            a = self.arr[side].astype(float)
            lum = ndimage.gaussian_filter(a @ [.299, .587, .114], SS) / 255
            level = d / step + (.62 - lum) * contrast + ((d >= 5) & (d.astype(int) % 5 == 0))
            idx = np.clip(np.rint(level), 0, len(ramp) - 1).astype(int)
            sel = region & solid & outer & metal(a[..., 0], a[..., 1], a[..., 2])
            self.arr[side][sel] = np.asarray(ramp, np.uint8)[idx][sel]

    def palette(self, k, extra):
        pixels = np.concatenate([self.arr[s][self.mask[s]] for s in (1, -1)]).astype(float)
        sample = pixels[np.linspace(0, len(pixels) - 1, 30000).astype(int)]
        return np.vstack([kmeans(sample, k), np.asarray(extra, int)]).astype(float)


# ----------------------------------------------------------------------------- geometry helpers
def box(P, center, size, kind, angle=0.0, **extra):
    b = dict(c=tuple(float(x) for x in center), s=tuple(float(x) for x in size),
             rot=('z', float(angle)) if angle else None, kind=kind, color=None, **extra)
    P['held']['boxes'].append(b)
    return b


def bar(P, p0, p1, width, thick, kind, extend=0.0, **extra):
    """Long bar from p0 to p1 (world X/Y), direction snapped to 22.5 degrees."""
    (x0, y0), (x1, y1) = p0, p1
    a = (math.degrees(math.atan2(y1 - y0, x1 - x0)) + 90) % 180 - 90
    snap = round(a / 22.5) * 22.5
    length = math.hypot(x1 - x0, y1 - y0) + 2 * extend
    if abs(snap) <= 45:
        size, turn = (length, width, thick), snap
    else:
        size, turn = (width, length, thick), snap - 90 if snap > 0 else snap + 90
    return box(P, ((x0 + x1) / 2, (y0 + y1) / 2, 0.0), size, kind, turn, axis=0 if abs(snap) <= 45 else 1, **extra)


def wedge_bar(P, p0, p1, width, steps, kind, inner, scale=1.0, **extra):
    """Bar with a wedge section: nested layers, widest and thinnest first. `steps` = ((width fraction,
    thickness), ...); every narrower layer hugs the side facing `inner` (the spine), so the section
    runs from a razor edge on the far side up to the thick spine."""
    base = bar(P, p0, p1, width, steps[0][1] * scale, kind, **extra)
    out = [base]
    a = math.radians(base['rot'][1]) if base['rot'] else 0.0
    k = 1 - base['axis']                                  # local axis across the width
    way = np.array([math.cos(a), math.sin(a)]) if k == 0 else np.array([-math.sin(a), math.cos(a)])
    way = way * (1 if (np.asarray(inner) - base['c'][:2]) @ way >= 0 else -1)
    for frac, thick in steps[1:]:
        size = list(base['s'])
        size[k] = width * frac
        size[2] = thick * scale
        c = np.asarray(base['c'][:2]) + way * (width - size[k]) / 2
        out.append(box(P, (c[0], c[1], 0.0), size, kind, base['rot'][1] if base['rot'] else 0.0, axis=base['axis'], **extra))
    return out


def cover_bar(P, pts, direction, thick, kind, margin=0.004, **extra):
    """Smallest bar along `direction` (snapped to 22.5 degrees) that covers the X/Y points."""
    a = (math.degrees(math.atan2(direction[1], direction[0])) + 90) % 180 - 90
    snap = round(a / 22.5) * 22.5
    u = np.array([math.cos(math.radians(snap)), math.sin(math.radians(snap))])
    n = np.array([-u[1], u[0]])
    pu, pn = pts @ u, pts @ n
    lu, hu, ln, hn = pu.min() - margin, pu.max() + margin, pn.min() - margin, pn.max() + margin
    center = u * (lu + hu) / 2 + n * (ln + hn) / 2
    if abs(snap) <= 45:
        size, turn = (hu - lu, hn - ln, thick), snap
    else:
        size, turn = (hn - ln, hu - lu, thick), snap - 90 if snap > 0 else snap + 90
    return box(P, (center[0], center[1], 0.0), size, kind, turn, axis=0 if abs(snap) <= 45 else 1, **extra)


def plate(P, x0, x1, y0, y1, thick, kind, **extra):
    return box(P, ((x0 + x1) / 2, (y0 + y1) / 2, 0.0), (x1 - x0, y1 - y0, thick), kind, **extra)


def outline(b):
    """World X/Y corners of a z-rotated box, counter-clockwise from local (-x, -y)."""
    sx, sy = b['s'][:2]
    a = math.radians(b['rot'][1]) if b['rot'] else 0.0
    c, s = math.cos(a), math.sin(a)
    return [(b['c'][0] + c * x - s * y, b['c'][1] + s * x + c * y)
            for x, y in ((-sx / 2, -sy / 2), (sx / 2, -sy / 2), (sx / 2, sy / 2), (-sx / 2, sy / 2))]


def long_edges(b):
    q = outline(b)
    return ((q[0], q[1]), (q[3], q[2])) if b['s'][0] >= b['s'][1] else ((q[1], q[2]), (q[0], q[3]))


def chain_outer(bars, center):
    """Outer long edge of each bar in a chain, ordered along the chain."""
    out = []
    for b in bars:
        edge = max(long_edges(b), key=lambda e: math.dist(((e[0][0] + e[1][0]) / 2, (e[0][1] + e[1][1]) / 2), center))
        if out and math.dist(edge[1], out[-1][1]) < math.dist(edge[0], out[-1][1]):
            edge = edge[::-1]
        elif not out and len(bars) > 1:
            nxt = bars[1]['c'][:2]
            if math.dist(edge[0], nxt) < math.dist(edge[1], nxt):
                edge = edge[::-1]
        out.append(edge)
    return out


def wedges(bars, center):
    """Triangles closing the gaps on the outer side of the joints in a bar chain."""
    edges = chain_outer(bars, center)
    tris = []
    for (_, end), (start, _), pb, nb in zip(edges, edges[1:], bars, bars[1:]):
        tris.append((end, start, ((pb['c'][0] + nb['c'][0]) / 2, (pb['c'][1] + nb['c'][1]) / 2)))
    return tris


# ----------------------------------------------------------------------------- face baking
def face_grid(b, face, tpb):
    corners = (np.asarray(FACE_VERTS[face], float) - .5) * b['s'] * FLIP
    w = max(1, round(np.linalg.norm(corners[3] - corners[0]) * tpb))
    h = max(1, round(np.linalg.norm(corners[1] - corners[0]) * tpb))
    u = ((np.arange(w * SS) + .5) / (w * SS))[None, :, None]
    v = ((np.arange(h * SS) + .5) / (h * SS))[:, None, None]
    local = corners[0] + u * (corners[3] - corners[0]) + v * (corners[1] - corners[0])
    return local, np.asarray(FACE_NORMAL[face], float) * FLIP, w, h


def bake_face(b, face, canvas):
    tpb = b.get('tpb') or canvas.tpb
    local, n, w, h = face_grid(b, face, tpb)
    R = rot('xyz'.index(b['rot'][0]), b['rot'][1]) if b['rot'] else np.eye(3)
    nudge = 0.75 / canvas.ppb
    long_axis = b.get('axis', int(np.argmax(b['s'][:2])))
    if b.get('open_ends') and abs(n[long_axis]) > .5:     # joint walls inside a bar chain
        return np.zeros((h, w, 4))
    if b.get('shade'):
        return b['shade'](n, R, w, h)
    if abs(n[2]) > .5:
        if b.get('art'):
            return b['art'](int(n[2]), w, h)
        q, side = local, np.full(local.shape[:2], int(n[2]))
    elif b.get('wrap') and abs(n[0]) > .5:
        q = local.copy()
        q[..., 0] = local[..., 2] * b['s'][0] / b['s'][2] * np.sign(n[0])
        q[..., 2] = b['s'][2] / 2
        side = np.ones(local.shape[:2], int)
    else:
        q = local - n * nudge
        side = np.where(local[..., 2] >= 0, 1, -1)
    world = q @ R.T + b['c']
    i, j = canvas.index(world[..., 0], world[..., 1])
    rgb = np.where((side == 1)[..., None], canvas.arr[1][i, j], canvas.arr[-1][i, j]).astype(float)
    if b.get('side_paint') and abs(n[2]) <= .5:
        rgb = np.asarray(b['side_paint'](world, local), float)
    alpha = b['alpha'](world[..., 0], world[..., 1]) if b.get('alpha') else np.ones(local.shape[:2])
    rgb = rgb.reshape(h, SS, w, SS, 3).mean((1, 3))
    alpha = alpha.reshape(h, SS, w, SS).mean((1, 3))
    return np.dstack([rgb, np.where(alpha >= .5, 255, 0)])


def quantize(tile, pal):
    rgb = tile[..., :3].astype(float)
    q = pal[((rgb[..., None, :] - pal) ** 2).sum(-1).argmin(-1)]
    out = np.dstack([q, tile[..., 3]]).astype(np.uint8)
    out[out[..., 3] == 0, :3] = 0
    return Image.fromarray(out, 'RGBA')


def paint_atlas(boxes, canvas, pal):
    tiles, face_keys = {}, []
    for b in boxes:
        faces = {}
        for face in FACE_NORMAL:
            tile = quantize(np.asarray(bake_face(b, face, canvas), float), pal)
            if not np.asarray(tile)[..., 3].any():      # fully cut out: leave the face off
                continue
            key = (tile.size, tile.tobytes())
            tiles.setdefault(key, tile)
            faces[face] = key
        face_keys.append(faces)
    positions, edge = shelf_pack([t.size for t in tiles.values()], pad=1)
    edge = max(edge, 256)
    if edge > 512:
        raise ValueError(f'weapon atlas exceeds 512 pixels: {edge}')
    atlas = Image.new('RGBA', (edge, edge))
    uvs = {}
    for (key, tile), (x, y) in zip(tiles.items(), positions):
        w, h = tile.size
        atlas.paste(Image.fromarray(np.pad(np.asarray(tile), ((1, 1), (1, 1), (0, 0)), mode='edge')), (x - 1, y - 1))
        uvs[key] = [round(n * 16 / edge, 5) for n in (x, y, x + w, y + h)]
    faces = [{face: {'texture': '#t', 'uv': uvs[key]} for face, key in entry.items()} for entry in face_keys]
    return atlas, faces


# ----------------------------------------------------------------------------- emit
def emit_weapon(pack, model_id, design, height_blocks, k):
    """design() -> (part table, canvas, palette). Writes held + hand models, atlases, manifest, icon."""
    P, canvas, pal = design()
    disp = 16.0 / k
    pname, part = 'held', P['held']
    piv = np.array(part['pivot'], float)
    atlas, faces = paint_atlas(part['boxes'], canvas, pal)
    elements = []
    for b, mapped in zip(part['boxes'], faces, strict=True):
        center = 8.0 + k * FLIP * (np.asarray(b['c']) - piv)
        half = k * np.asarray(b['s']) / 2.0
        start, end = center - half, center + half
        if (start < -16).any() or (end > 32).any():
            raise ValueError(f'{model_id}: element outside -16..32: {start} {end}')
        element = {'from': [round(float(x), 4) for x in start], 'to': [round(float(x), 4) for x in end]}
        if b['rot']:
            axis, angle = b['rot']
            element['rotation'] = {'angle': float(angle if axis == 'y' else -angle), 'axis': axis,
                                   'origin': [round(float(x), 4) for x in center]}
        element['faces'] = mapped
        elements.append(element)

    assets = Path(pack) / 'assets' / NS
    weapon = model_id.rsplit('/', 1)[-1]
    tref = f'{NS}:item/{model_id}/{pname}'
    for sub in ('models/item', 'textures/item', 'items'):
        (assets / sub / model_id).mkdir(parents=True, exist_ok=True)
    model = {'credit': CREDIT, 'texture_size': list(atlas.size), 'textures': {'t': tref, 'particle': tref},
             'elements': elements,
             'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}
    write_json(assets / 'models' / 'item' / model_id / f'{pname}.json', model)
    atlas.save(assets / 'textures' / 'item' / model_id / f'{pname}.png', optimize=True)
    item = assets / 'items' / model_id / f'{pname}.json'
    if not item.exists():
        item.write_text(json.dumps({'model': {'type': 'minecraft:model', 'model': tref}}, indent=2),
                        encoding='utf-8', newline='\n')

    # In-hand model: same geometry and atlas, its own display and particle.
    hand_path = assets / 'models' / 'item' / f'{model_id}_hand.json'
    hand = json.loads(hand_path.read_bytes())
    hand.update(credit=CREDIT, texture_size=list(atlas.size), elements=elements)
    hand['textures']['t'] = f'{NS}:item/{model_id}/hand'
    write_json(hand_path, {key: hand[key] for key in ('credit', 'texture_size', 'textures', 'elements', 'display')})
    atlas.save(assets / 'textures' / 'item' / model_id / 'hand.png', optimize=True)

    n = len(elements)
    n_rot = sum(1 for b in part['boxes'] if b['rot'])
    man = {
        'id': f'{NS}:{model_id}', 'height_blocks': height_blocks, 'resolution': canvas.tpb,
        'texels_per_voxel': 1, 'model_units_per_block': k, 'item_display_scale': disp,
        'front': '+Z (boss local, yaw 0 = south)',
        'note': 'weapons-texture-v3: long plates and bars, detail painted into large faces.',
        'parts': [{'name': pname, 'item_model': f'{NS}:{model_id}/{pname}', 'parent': None,
                   'pivot_blocks': list(part['pivot']), 'translation': list(part['pivot']), 'scale': disp,
                   'spin_axis': None, 'mode': 'box', 'cuboids': n, 'cuboids_voxel': n - n_rot,
                   'cuboids_obb': n_rot, 'faces': n * 6, 'texture_px': atlas.width, 'voxels': 0}],
        'total_cuboids': n,
    }
    man_dir = Path(pack).parent / 'manifests'
    man_dir.mkdir(exist_ok=True)
    (man_dir / (model_id.replace('/', '__') + '.json')).write_text(json.dumps(man, indent=2) + '\n', encoding='utf-8', newline='\n')
    write_icon(pack, man, weapon, pal)
    print(f'[{model_id}] {n} cuboids ({n_rot} rotated), atlas {atlas.width}x{atlas.height}, '
          f'{canvas.tpb} texels/block, palette {len(pal)}')
    return man


def write_json(path, obj):
    Path(path).write_text(json.dumps(obj, separators=(',', ':')) + '\n', encoding='utf-8', newline='\n')


# ----------------------------------------------------------------------------- 2D icon (gui / ground / fixed)
ICON = 32
ICON_SPIN = {'stormcaller': -45, 'crystal_greatsword': 135, 'chrono_dagger': -45}
FLICK = [0.0, 0.15, 1.0, 0.25, 0.0, 0.0, 0.6, 0.1]   # same flicker sequence as weapons/icons.py


def write_icon(pack, man, weapon, pal):
    from preview import pack_tris
    from render import render
    tris, uvs, tex = pack_tris(pack, man)[:3]
    big = ICON * 24
    flat = np.dstack([tex[..., :3], tex[..., 3]])
    col, _ = render(tris, uvs, flat, 0, 0, big, bg=(0, 0, 0), cull_back=True, alpha_test=True, shade=False)
    white = flat.copy()
    white[..., :3] = 255
    msk, _ = render(tris, uvs, white, 0, 0, big, bg=(0, 0, 0), cull_back=True, alpha_test=True, shade=False)
    rgba = np.dstack([np.asarray(col), np.asarray(msk)[..., 0]])
    im = Image.fromarray(rgba.astype(np.uint8), 'RGBA').rotate(ICON_SPIN[weapon], expand=True, resample=Image.BICUBIC)
    im = im.crop(im.getbbox())
    side = max(im.size)
    square = Image.new('RGBA', (side, side))
    square.paste(im, ((side - im.width) // 2, (side - im.height) // 2))
    a = np.asarray(square.resize((ICON - 2, ICON - 2), Image.BOX)).astype(float)
    body = a[..., 3] > 60
    rgb = a[..., :3] / np.maximum(a[..., 3:] / 255, 1e-3)
    icon = np.zeros((ICON, ICON, 4))
    icon[1:-1, 1:-1, :3] = np.where(body[..., None], rgb, 0)
    icon[1:-1, 1:-1, 3] = body * 255
    filled = icon[..., 3] > 0
    ring = ndimage.binary_dilation(filled) & ~filled
    icon = np.asarray(quantize(icon, pal)).copy()
    dark = pal[np.argmin(pal @ [.299, .587, .114])]
    icon[ring] = [*dark.astype(np.uint8), 255]
    path = Path(pack) / 'assets' / NS / 'textures' / 'item' / 'weapon' / f'{weapon}_icon.png'
    if (path.parent / (path.name + '.mcmeta')).exists():   # stormcaller: lightning flicker frames
        frames = []
        f = icon.astype(float)
        r, g, bl = f[..., 0], f[..., 1], f[..., 2]
        vein = (np.clip((r - 140) / 80, 0, 1) * np.clip((g - 90) / 80, 0, 1) * (bl < 130))[..., None]
        for kf in FLICK:
            frame = f.copy()
            frame[..., :3] = np.clip(f[..., :3] * (1 - .7 * kf * vein) + np.array([215, 250, 255]) * .7 * kf * vein, 0, 255)
            frames.append(frame)
        icon = np.concatenate(frames, 0)
    Image.fromarray(icon.astype(np.uint8), 'RGBA').save(path, optimize=True)


# ============================================================================= 1. STORMCALLER
STORM_TPB = 96


def define_stormcaller():
    """Tempest dragon-wing battleaxe, 3D: every blade bar has a wedge section (thin outer edge up to a
    thick inner spine), the wing membranes are thin sheets with a raised bone layer, the dragon is
    nested layers (body, head, snout) so head and jaw stand out in side view, the haft is one wrapped
    bar and the pommel is a thin silhouette with a thick diamond core."""
    P = new_part_table()
    add_part(P, 'held', None, (0.0, 0.6, 0.0))
    cv = Canvas('stormcaller', STORM_TPB)
    pal = cv.palette(20, [GOLD_WHITE, GOLD_BRIGHT, GOLD_EDGE, GOLD_AMBER, BRONZE_M_HI, BRONZE_MUTE, STORM_IRON, STORM_VOID, DRAGON_RIM])

    # Blade bars of the right wing (top claw -> outer arc -> lower tip); the left wing mirrors them.
    right = (((0.400, 2.044), (0.700, 1.744), 0.150),
             ((0.730, 1.860), (0.730, 1.420), 0.150),
             ((0.760, 1.446), (0.460, 1.146), 0.150),
             ((0.580, 1.186), (0.240, 1.045), 0.130))
    scales = (0.80, 1.0, 1.0, 0.70)                    # claw and lower tip get thinner
    steps = ((1.0, 0.020), (0.62, 0.046), (0.30, 0.074))
    blades, regions = [], []
    boxes_before = len(P['held']['boxes'])
    for sx in (1, -1):
        center = (sx * 0.30, 1.58)
        bars = []
        for (a, b, w), k in zip(right, scales):
            bars += wedge_bar(P, (sx * a[0], a[1]), (sx * b[0], b[1]), w, steps, 'storm_blade', center, scale=k)
        main = [b for b in bars if b is not None]
        blades += main
        chain = [bars[i] for i in range(0, len(bars), len(steps))]
        edges = chain_outer(chain, center)
        hull = [pt for e in edges for pt in e] + [(sx * 0.02, 1.0), (sx * 0.02, 2.2), (sx * 0.52, 2.22)]
        regions.append((cv.closed & cv.polygon(hull)) | cv.polygon(*wedges(chain, center)))
    blade_mask = cv.polygon(*[outline(b) for b in blades])

    # Thin membrane sheet between the blade and the dragon, with a raised bone layer on top.
    a = cv.arr[1].astype(int)
    dark = (a[..., 0] < 95) & (a[..., 0] - a[..., 2] < 45)
    dark = ndimage.binary_opening(ndimage.binary_closing(dark, iterations=2 * SS), iterations=SS // 2)
    for sx, region in zip((1, -1), regions):
        half = cv.X * sx > 0.02
        sheet = region & ~blade_mask & half
        plate(P, *sorted((sx * 0.02, sx * 0.83)), 0.98, 2.20, 0.030, 'storm_membrane', alpha=cv.alpha(sheet))
        plate(P, *sorted((sx * 0.02, sx * 0.83)), 0.98, 2.20, 0.070, 'storm_bone', alpha=cv.alpha(sheet & dark))

    # The dragon: base silhouette, then body, head and snout layers stacked in depth.
    dragon = cv.closed & ndimage.binary_opening(cv.solid, iterations=SS // 2)

    def reg(x0, x1, y0, y1):
        return dragon & (cv.X >= x0) & (cv.X <= x1) & (cv.Y >= y0) & (cv.Y <= y1)

    plate(P, -0.275, 0.135, 1.40, 2.11, 0.060, 'storm_dragon', alpha=cv.alpha(dragon))
    plate(P, -0.12, 0.14, 1.40, 1.80, 0.100, 'storm_dragon', alpha=cv.alpha(reg(-0.12, 0.14, 1.40, 1.80)))
    plate(P, -0.275, 0.02, 1.72, 2.11, 0.130, 'storm_dragon', alpha=cv.alpha(reg(-0.275, 0.02, 1.72, 2.11)))
    plate(P, -0.275, -0.10, 1.76, 1.96, 0.160, 'storm_dragon', alpha=cv.alpha(reg(-0.275, -0.10, 1.76, 1.96)))
    plate(P, -0.070, 0.070, 1.10, 1.48, 0.100, 'storm_torso')
    box(P, (0.0, 0.59, 0.0), (0.074, 1.10, 0.074), 'storm_haft', wrap=True)
    plate(P, -0.135, 0.135, 0.0, 0.420, 0.050, 'storm_pommel', alpha=cv.alpha(cv.solid))
    core = cv.solid & (np.abs(cv.X) <= 0.075) & (cv.Y > 0.12) & (cv.Y < 0.40)
    plate(P, -0.075, 0.075, 0.12, 0.40, 0.110, 'storm_pommel', alpha=cv.alpha(core))

    # Line art: crisp golden bevel on the outer edge of every blade.
    solid = blade_mask | regions[0] | regions[1]
    for sx in (1, -1):
        half = cv.X * sx > 0
        cv.metal_ramp(solid, blade_mask & half, (sx * 0.30, 1.58),
                      (GOLD_WHITE, GOLD_BRIGHT, GOLD_EDGE, GOLD_AMBER, BRONZE_M_HI, BRONZE_MUTE, STORM_IRON),
                      metal=lambda r, g, b: (r > 110) & (r - b > 50))
    return P, cv, pal


# ============================================================================= 2. CRYSTAL GREATSWORD
CRYS_TPB = 128


def define_crystal_greatsword():
    """Amethyst greatsword: the blade is one painted plate, the guard keeps its v2 cuboids,
    the grip is one wrapped bar and the pommel keeps three cuboids. Grip axis is +Y."""
    P = new_part_table()
    add_part(P, 'held', None, (0.0, 0.52, 0.0))
    cv = Canvas('crystal_greatsword', CRYS_TPB)
    pal = cv.palette(18, [CRYS_VOID, CRYS_DEEP, CRYS_BASE, CRYS_MID, CRYS_BRIGHT, CRYS_GLOW, CRYS_WHITE,
                          OBS_BASE, OBS_GOLD, OBS_GOLD_HI])

    half_w, tip = 0.072, 0.30
    shape = cv.polygon([(-half_w, 1.75), (-half_w, tip), (0.0, 0.0), (half_w, tip), (half_w, 1.75)])

    def blade_layer(x_hw, y0, y1, thick):
        mask = shape & (np.abs(cv.X) <= x_hw)
        plate(P, -x_hw, x_hw, y0, y1, thick, 'crys_blade', alpha=cv.alpha(mask),
              side_paint=lambda w_, l_, t_=thick: crystal_edge(w_, l_, t_))

    # Wedge section: razor edge layer, a mid bevel and a raised ridge; everything thins toward the tip.
    blade_layer(half_w, 0.0, 1.72, 0.022)
    blade_layer(0.046, 0.12, 0.35, 0.032)
    blade_layer(0.046, 0.35, 1.72, 0.044)
    blade_layer(0.018, 0.20, 0.50, 0.044)
    blade_layer(0.018, 0.50, 1.72, 0.060)

    # --- guard: v2 geometry, then 90 deg around Y (handle) so the cross sits left-right in-hand.
    # Live pack models already have this rotation applied to crys_guard / crys_wing / crys_heart
    # boxes with mid-Y in the guard cluster (not the pommel). If you regenerate, re-apply:
    #   Ry(90) around the handle: (x,y,z)->(z,y,-x) in design space (c_x,c_z)=(c_z,-c_x),
    #   swap s_x/s_z, remap rot axis z->x (same angle) and x->z (negated angle). ---
    add_box(P, 'held', ( 0.000, 1.755, 0.0), (0.118, 0.130, 0.054), OBS_DARK,    None,              'crys_guard')
    add_box(P, 'held', ( 0.000, 1.710, 0.0), (0.024, 0.090, 0.066), OBS_GOLD_HI, None,              'crys_guard')
    for sx in (1, -1):
        add_box(P, 'held', (sx * 0.036, 1.735, 0.0), (0.024, 0.100, 0.064), OBS_GOLD,    ('z',  sx * 22.5), 'crys_guard')
        add_box(P, 'held', (sx * 0.068, 1.775, 0.0), (0.072, 0.024, 0.062), OBS_GOLD_HI, ('z', -sx * 22.5), 'crys_guard')
        add_box(P, 'held', (sx * 0.102, 1.815, 0.0), (0.105, 0.038, 0.056), OBS_DARK,    None,              'crys_guard')
        add_box(P, 'held', (sx * 0.095, 1.815, 0.0), (0.088, 0.020, 0.062), OBS_GOLD_HI, None,              'crys_guard')
    add_box(P, 'held', ( 0.000, 1.835, 0.0), (0.128, 0.128, 0.058), OBS_DARK,    ('z', 45.0),       'crys_guard')
    add_box(P, 'held', ( 0.000, 1.835, 0.0), (0.110, 0.110, 0.062), OBS_GOLD_HI, ('z', 45.0),       'crys_guard')
    add_box(P, 'held', ( 0.000, 1.835, 0.0), (0.080, 0.080, 0.066), CRYS_BASE,   ('z', 45.0),       'crys_heart')
    add_box(P, 'held', ( 0.000, 1.835, 0.0), (0.052, 0.052, 0.070), CRYS_MID,    ('z', 45.0),       'crys_heart')
    add_box(P, 'held', ( 0.000, 1.835, 0.0), (0.026, 0.026, 0.074), CRYS_WHITE,  ('z', 45.0),       'crys_heart')
    add_box(P, 'held', ( 0.000, 1.925, 0.0), (0.036, 0.050, 0.064), OBS_GOLD_HI, None,              'crys_guard')
    for sx in (1, -1):
        add_box(P, 'held', (sx * 0.140, 1.815, 0.0), (0.075, 0.062, 0.054), OBS_DARK,    None,              'crys_guard')
        add_box(P, 'held', (sx * 0.165, 1.795, 0.0), (0.058, 0.034, 0.058), OBS_GOLD_HI, ('z',  sx * 22.5), 'crys_guard')
        add_box(P, 'held', (sx * 0.106, 1.885, 0.0), (0.042, 0.082, 0.050), CRYS_BASE,   ('z', -sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.118, 1.928, 0.0), (0.028, 0.056, 0.046), CRYS_MID,    ('z', -sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.125, 1.950, 0.0), (0.018, 0.028, 0.042), CRYS_BRIGHT, None,              'crys_wing')
        add_box(P, 'held', (sx * 0.148, 1.865, 0.0), (0.060, 0.082, 0.052), CRYS_BASE,   ('z', -sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.172, 1.898, 0.0), (0.044, 0.062, 0.048), CRYS_MID,    ('z', -sx * 45.0), 'crys_wing')
        add_box(P, 'held', (sx * 0.192, 1.922, 0.0), (0.022, 0.034, 0.044), CRYS_GLOW,   ('z', -sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.195, 1.822, 0.0), (0.070, 0.048, 0.050), CRYS_DEEP,   None,              'crys_wing')
        add_box(P, 'held', (sx * 0.228, 1.824, 0.0), (0.042, 0.036, 0.046), CRYS_MID,    None,              'crys_wing')
        add_box(P, 'held', (sx * 0.244, 1.825, 0.0), (0.020, 0.022, 0.042), CRYS_BRIGHT, None,              'crys_wing')
        add_box(P, 'held', (sx * 0.168, 1.765, 0.0), (0.052, 0.060, 0.048), CRYS_DEEP,   ('z',  sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.188, 1.738, 0.0), (0.034, 0.046, 0.044), CRYS_BASE,   ('z',  sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.200, 1.720, 0.0), (0.018, 0.028, 0.040), CRYS_MID,    None,              'crys_wing')

    # Crystals grown out of the guard: thicker facets, plus prisms jutting forward and back.
    for sx in (1, -1):
        for cx, cy, size, tilt in ((0.095, 1.855, (0.040, 0.060, 0.190), 0.0),
                                   (0.150, 1.800, (0.036, 0.052, 0.170), 22.5),
                                   (0.195, 1.860, (0.032, 0.050, 0.140), -22.5),
                                   (0.060, 1.900, (0.034, 0.058, 0.150), -22.5)):
            b = box(P, (sx * cx, cy, 0.0), size, 'crys_wing', shade=crystal_shade)
            if tilt:
                b['rot'] = ('x', tilt * sx)
        box(P, (sx * 0.125, 1.765, 0.0), (0.026, 0.040, 0.130), 'crys_wing', 0.0, shade=crystal_shade)

    # Grip: one bar; pommel: calyx, crystal and its diamond facet.
    box(P, (0.0, 2.045, 0.0), (0.050, 0.200, 0.050), 'crys_grip', wrap=True, tpb=256)
    add_box(P, 'held', (0.000, 2.148, 0.0), (0.050, 0.032, 0.050), OBS_DARK, None, 'crys_guard')
    add_box(P, 'held', (0.000, 2.205, 0.0), (0.062, 0.080, 0.058), CRYS_BASE, None, 'crys_wing')
    add_box(P, 'held', (0.000, 2.212, 0.0), (0.056, 0.056, 0.062), CRYS_MID, ('z', 45.0), 'crys_heart')

    for b in P['held']['boxes']:
        if b['kind'] == 'crys_wing' and 'shade' not in b:
            b['s'] = (b['s'][0], b['s'][1], b['s'][2] * 1.45)
            b['shade'] = crystal_shade
    paint_crystal_blade(cv, half_w, tip)
    paint_grip(cv, -0.025, 0.025, 1.945, 2.145)
    return P, cv, pal


def crystal_shade(n, R, w, h):
    """Flat-lit amethyst facet for one face of a crystal: level from the face normal, a diagonal
    gradient, a lit corner facet and a bright top/left bevel."""
    wn = R @ n
    light = np.array([.35, .8, .5])
    level = 2.4 + 2.8 * float(wn @ (light / np.linalg.norm(light)))
    u = ((np.arange(w) + .5) / w)[None, :]
    v = ((np.arange(h) + .5) / h)[:, None]
    lv = level + (u - .5) * .9 + (.5 - v) * .9 + ((u + v) < .55) * .9
    lv[0, :] += 1
    lv[:, 0] += .6
    lv[-1, :] -= .8
    lv[:, -1] -= .5
    idx = np.clip(np.rint(lv), 1, 6).astype(int)
    return np.dstack([CRYSTAL_RAMP[idx], np.full((h, w), 255)])


def hash2(i, j, k=0):
    """Fixed per-cell value in [0, 1) (facet layout, not per-pixel noise)."""
    return (np.sin(i * 12.9898 + j * 78.233 + k * 37.719) * 43758.5453) % 1


CRYSTAL_RAMP = np.asarray((CRYS_VOID, CRYS_DEEP, CRYS_BASE, CRYS_MID, CRYS_BRIGHT, CRYS_GLOW, CRYS_WHITE), np.uint8)
TOOTH = 0.11          # crystal tooth period along the blade edges (front and sides agree)


def paint_crystal_blade(cv, half_w, tip):
    """Amethyst blade drawn as a cut gem: a mosaic of flat facets lit from the right, ridge lines
    between them, a glowing lavender core, crystal points along both edges, hairline fractures
    and glints, a brighter pointed tip."""
    inside = (np.abs(cv.X) <= half_w) & (cv.Y >= 0) & (cv.Y <= 1.75)
    x, y = cv.X[inside], cv.Y[inside]
    u = x / half_w
    texel = 1 / cv.tpb
    # facet mosaic: jittered brick grid, nearest seed wins
    cw, ch = 0.67, 0.11
    best = np.full(x.shape, np.inf)
    second = np.full(x.shape, np.inf)
    cell = np.zeros((len(x), 3))
    r0 = np.floor(y / ch).astype(int)
    for dr in (-1, 0, 1):
        r = r0 + dr
        off = (r % 2) * .5
        c0 = np.floor((u + 1) / cw - off).astype(int)
        for dc in (-1, 0, 1):
            c = c0 + dc
            su = (c + off + .5 + (hash2(c, r) - .5) * .7) * cw - 1
            sy = (r + .5 + (hash2(c, r, 1) - .5) * .6) * ch
            d = np.hypot((u - su) * half_w, y - sy)
            closer = d < best
            second = np.where(closer, best, np.minimum(second, d))
            best = np.where(closer, d, best)
            cell[closer] = np.c_[su, sy, hash2(c, r, 2)][closer]
    tilt = (cell[:, 2] - .5) * 1.7 + .9 * np.clip(cell[:, 0], -1, 1)
    level = 2.2 + 1.2 * tilt + (y - cell[:, 1]) / ch * .45
    level += .6 * np.clip(1 - y / 0.45, 0, 1)                      # lighter toward the tip
    level -= .8 * np.exp(-((u - .05 * np.sin(y * 17)) / .35) ** 2)  # deep violet heart under the core
    ridge = (second - best) < 1.1 * texel
    idx = np.clip(np.rint(level), 1, 6).astype(int)
    idx = np.where(ridge, np.where(tilt > 0, 5, 1), idx)
    # glowing core
    core = np.abs(u - .05 * np.sin(y * 17)) * half_w
    idx = np.where((core < 2.6 * texel) & (idx < 4), 4, idx)
    idx = np.where(core < 1.1 * texel, 5, idx)
    idx = np.where((core < 1.1 * texel) & ((y / .11) % 1 < .16), 6, idx)
    # crystal points along both edges
    edge = half_w - np.abs(x)
    ph = (y / TOOTH + (x < 0) * .5) % 1
    amp = 0.030 * (1 - np.abs(2 * ph - 1))
    tooth = edge < amp
    idx = np.where(tooth, np.where(x > 0, 4, 3), idx)
    idx = np.where(tooth & (edge > amp - 1.2 * texel), np.where(x > 0, 6, 4), idx)
    idx = np.where(edge < 1.0 * texel, np.where(x > 0, 5, 3), idx)
    # hairline fractures with a shadow, and glints on the brightest facets
    for k in range(10):
        y0 = .22 + k * .145
        x0 = (hash2(k, 7) - .5) * 1.1 * half_w
        dx, dy = math.cos(.6 + hash2(k, 8)), math.sin(.6 + hash2(k, 8))
        along = np.clip((x - x0) * dx + (y - y0) * dy, 0, .045)
        dist = np.hypot(x - x0 - along * dx, y - y0 - along * dy)
        shadow = np.hypot(x - x0 - along * dx + dy * 1.4 * texel, y - y0 - along * dy - dx * 1.4 * texel)
        idx = np.where((shadow < .6 * texel) & ~tooth, 1, idx)
        idx = np.where((dist < .6 * texel) & ~tooth, 6, idx)
    glint = (cell[:, 2] > .8) & (cell[:, 0] > 0) & ((np.abs(u - cell[:, 0]) * half_w < .6 * texel) & (np.abs(y - cell[:, 1]) < 2 * texel)
                                                    | (np.abs(y - cell[:, 1]) < .6 * texel) & (np.abs(u - cell[:, 0]) * half_w < 2 * texel))
    idx = np.where(glint & ~tooth, 6, idx)
    # pointed tip: bright slanted edges
    idx = np.where((y < tip) & (np.abs(x) > half_w * y / tip - 2.0 * texel), 5, idx)
    for side in (1, -1):
        cv.arr[side][inside] = CRYSTAL_RAMP[idx]


def crystal_edge(world, local, thick=0.050):
    """Blade side walls: a lit crystal ridge along the middle of the edge, the same crystal points
    as the front (bright where a point meets the edge, a groove between points)."""
    x, y = world[..., 0], world[..., 1]
    dz = np.clip(np.abs(local[..., 2]) / (thick / 2), 0, 1)
    ph = (y / TOOTH + (x < 0) * .5) % 1
    level = 3.6 - 1.6 * dz + np.where(ph < .5, (.5 - ph) * 3.0, -(ph - .5) * 1.6) + (x > 0) * .5
    level = np.where((dz < .3) & (ph < .07), 6, level)
    level = np.where((ph > .93) & (dz > .4), 1, level)
    return CRYSTAL_RAMP[np.clip(np.rint(level), 1, 6).astype(int)]


def paint_grip(cv, x0, x1, y0, y1):
    """Leather wrap in diagonal bands with bronze cord and studs."""
    X, Y = cv.X, cv.Y
    inside = (X >= x0) & (X <= x1) & (Y >= y0) & (Y <= y1)
    t = ((Y + .8 * (X - x0)) / 0.024) % 1
    idx = np.select([t < .14, t < .26, t < .78], [0, 1, 2], 3)
    colors = np.asarray((OBS_GOLD_HI, OBS_GOLD, OBS_BASE, OBS_DARK), np.uint8)[idx]
    for side in (1, -1):
        cv.arr[side][inside] = colors[inside]
    stud = inside & (np.abs(X - (x0 + x1) / 2) < 1.6 / 256) & (((Y - y0) / 0.048) % 1 < .07)
    cv.put(stud, OBS_GOLD_HI)
    cap = inside & ((Y < y0 + 2.0 / 256) | (Y > y1 - 2.0 / 256))
    cv.put(cap, OBS_GOLD)


# ============================================================================= 3. CHRONO SCYTHE
CHRONO_TPB = 128
DIAL_TPB = 256


# Blade root on the clockwork head (unchanged from v3): spine point, root tangent and root width.
SCYTHE_ROOT = (0.517, 2.430)          # spine at the root
SCYTHE_ROOT_DIR = 165.4               # degrees, blade direction leaving the head
SCYTHE_ROOT_W = 0.40


class Crescent:
    """Scythe blade, open sickle: the spine is one large convex arc that starts at the v3 root with
    the v3 root direction and runs `length` blocks; the cutting edge sits `width(t)` inside it.
    The width falls in one smooth curve from the root width to a needle tip that points along
    the arc tangent (outward, not back at the staff)."""

    def __init__(self, root=SCYTHE_ROOT, direction=SCYTHE_ROOT_DIR, radius=2.0, length=1.75,
                 root_w=SCYTHE_ROOT_W, taper_pow=0.9):
        d = math.radians(direction)
        normal = np.array([-math.sin(d), math.cos(d)])         # left of travel: centre below the spine
        self.R = radius
        self.c = np.asarray(root, float) + radius * normal
        self.a0 = math.atan2(root[1] - self.c[1], root[0] - self.c[0])
        self.a1 = self.a0 + length / radius
        self.root_w, self.taper_pow = root_w, taper_pow

    def width(self, t):
        return self.root_w * np.clip(1 - t, 0, 1) ** self.taper_pow

    def coords(self, x, y):
        """t along the blade (0 root, 1 tip), f across it (0 cutting edge, 1 spine), width, inside."""
        r = np.hypot(x - self.c[0], y - self.c[1])
        t = (np.arctan2(y - self.c[1], x - self.c[0]) - self.a0) / (self.a1 - self.a0)
        w = self.width(np.clip(t, 0, 1))
        f = (r - self.R + w) / np.maximum(w, 1e-6)
        return t, f, w, (t >= -.03) & (t <= 1) & (f >= 0) & (f <= 1) & (w > 0)

    def point(self, t):
        a = self.a0 + t * (self.a1 - self.a0)
        return self.c + self.R * np.array([math.cos(a), math.sin(a)])

    def arc(self, t):
        return np.abs(t * (self.a1 - self.a0)) * self.R


def BossEase(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def define_chrono_dagger():
    """Chrono scythe: the blade is a smooth crescent with a long sharp taper, carried by six long
    bars (alpha cuts the curve); the clockwork head and the finial are painted plates, the clock
    is one dial face and the staff is one wrapped bar."""
    P = new_part_table()
    add_part(P, 'held', None, (0.54, 0.55, 0.0))
    cv = Canvas('chrono_dagger', CHRONO_TPB, x_min=-1.25)
    pal = cv.palette(20, [BRASS_VOID, BRASS_DARK, BRASS_MID, BRASS_GOLD, BRASS_HI, BRASS_BRIGHT,
                          DIAL_IVORY, DIAL_RIM, CHRONO_TEAL, CHRONO_GLOW])

    sx0 = 0.558
    box(P, (sx0, 1.03, 0.0), (0.060, 2.06, 0.060), 'chrono_shaft', wrap=True)
    blade = Crescent()
    t, f, _, inside = blade.coords(cv.X, cv.Y)
    pts = np.c_[cv.X[inside], cv.Y[inside]]
    tt, ff = t[inside], f[inside]
    # Wedge section across the blade: thin cutting edge (f = 0), mid bevel, thick spine (f = 1);
    # all layers thin out toward the needle tip.
    splits = (-0.03, .20, .40, .58, .74, .88, 1.0)
    for f0, thick in ((0.0, 0.024), (0.28, 0.046), (0.58, 0.072)):
        layer = inside & (f >= f0)
        for t0, t1 in zip(splits, splits[1:]):
            sel = (tt >= t0 - .01) & (tt <= t1 + .01) & (ff >= f0)
            if sel.sum() < 40:
                continue
            k = 1 - 0.58 * BossEase((t0 + t1) / 2)
            cover_bar(P, pts[sel], blade.point(t1) - blade.point(max(t0, 0)), thick * k,
                      'chrono_blade', alpha=cv.alpha(layer), open_ends=True)
    head = cv.closed & ndimage.binary_opening(cv.solid, iterations=SS // 2) & (cv.X > 0.40) & ~inside
    plate(P, 0.40, 0.84, 1.93, 2.50, 0.075, 'chrono_head', alpha=cv.alpha(head))
    # Clock: octagonal bezel (two squares) with a hole, the dial recessed inside it.
    dial_c, dial_r = (0.620, 2.130), 0.078
    ring = lambda x, y: (np.hypot(x - dial_c[0], y - dial_c[1]) > dial_r).astype(float)
    box(P, (*dial_c, 0.0), (0.20, 0.20, 0.150), 'chrono_bezel', alpha=ring)
    box(P, (*dial_c, 0.0), (0.20, 0.20, 0.150), 'chrono_bezel', 45.0, alpha=ring)
    box(P, (*dial_c, 0.0), (2 * dial_r, 2 * dial_r, 0.115), 'chrono_dial', tpb=DIAL_TPB,
        alpha=lambda x, y: (np.hypot(x - dial_c[0], y - dial_c[1]) <= dial_r).astype(float),
        art=lambda side, w, h: clock_art(w, h))
    # Upper lantern dial and the lower finial: thin silhouettes with a thick diamond on top.
    box(P, (0.735, 2.365, 0.0), (0.10, 0.10, 0.100), 'chrono_lantern', 45.0)
    plate(P, 0.45, 0.67, 0.12, 0.47, 0.070, 'chrono_finial', alpha=cv.alpha(cv.solid))
    box(P, (0.558, 0.305, 0.0), (0.105, 0.105, 0.125), 'chrono_finial', 45.0)

    paint_scythe_blade(cv, blade)
    paint_staff(cv, sx0)
    return P, cv, pal


def paint_scythe_blade(cv, blade):
    """Paint the crescent by its fraction across the width f (0 = cutting edge, 1 = spine) and its
    arc length s: bright cutting edge, brushed bronze body with a jagged golden fissure and forks,
    a teal rune channel with brass glyphs, and a plated spine."""
    t, f, w, inside = blade.coords(cv.X, cv.Y)
    s = blade.arc(t)
    texel = 1 / (cv.tpb * np.maximum(w, 1e-3))      # one texel in f units
    stops = ((.04, BRASS_BRIGHT), (.10, BRASS_HI), (.18, BRASS_GOLD), (.26, BRASS_MID), (.42, BRASS_MID),
             (.58, BRASS_BASE), (.62, BRASS_VOID), (.74, CHRONO_TEAL), (.77, CHRONO_GLOW), (.80, BRASS_VOID),
             (.92, BRASS_GOLD), (.97, BRASS_HI), (9, BRASS_VOID))
    idx = np.searchsorted(np.array([e for e, _ in stops]), f)
    colors = np.asarray([c for _, c in stops], np.uint8)[np.clip(idx, 0, len(stops) - 1)]
    body = (f > .26) & (f < .58)
    grain = body & ((f * 46) % 1 < .16)
    colors[grain & (f < .42)] = BRASS_BASE
    colors[grain & (f >= .42)] = BRASS_DARK
    # jagged golden fissure and forks
    target = .40 + .045 * np.sin(s * 37) + .02 * np.sin(s * 89 + 1)
    gap = np.abs(f - target) / texel
    colors[(gap < 1.6) & body] = BRASS_HI
    colors[(gap < .6) & body] = BRASS_BRIGHT
    k = s % .17
    fork = (k < .05) & (np.abs(f - (target - k * 4.5)) / texel < .7) & (f > .27)
    colors[fork] = BRASS_HI
    # rune glyphs (3 x 4 texels) in the channel
    glyphs = ('010111010101', '110101110101', '111010010111', '101111101101', '011100100011')
    step = 7 / cv.tpb
    gi = np.floor(s / step).astype(int)
    col = np.floor((s - gi * step) * cv.tpb).astype(int) - 2
    row = np.floor((f - .625) / .115 * 4).astype(int)
    on = np.zeros(f.shape, bool)
    for g, bits in enumerate(glyphs):
        mask = np.array([b == '1' for b in bits]).reshape(4, 3)
        sel = (gi % len(glyphs) == g) & (col >= 0) & (col < 3) & (row >= 0) & (row < 4)
        on[sel] = mask[row[sel], col[sel]]
    colors[on & (f > .62) & (f < .74)] = BRASS_BRIGHT
    colors[inside & (w < 6 / cv.tpb) & (f < .5)] = BRASS_BRIGHT   # tip: bright edge runs to the point
    for side in (1, -1):
        cv.arr[side][inside] = colors[inside]


def paint_staff(cv, sx0):
    X, Y = cv.X, cv.Y
    staff = (np.abs(X - sx0) <= 0.031) & (Y >= 0.47) & (Y <= 2.0)
    body = np.where(np.abs(X - sx0) < 0.011, 1, 0)
    cv.put(staff & (body == 0), BRASS_DARK)
    cv.put(staff & (body == 1), BRASS_BASE)
    cv.put(staff & (np.abs(X - sx0) < 1.0 / cv.tpb), BRASS_HI)
    spiral = staff & (((Y + 1.6 * (X - sx0)) / 0.09) % 1 < .09)
    cv.put(spiral, CHRONO_TEAL)
    for ny in (0.90, 1.21, 1.52, 1.85):
        ring = staff & (np.abs(Y - ny) < 0.022)
        cv.put(ring, BRASS_GOLD)
        cv.put(ring & (np.abs(Y - ny - .016) < 1.0 / cv.tpb), BRASS_BRIGHT)
        cv.put(ring & (np.abs(Y - ny + .016) < 1.0 / cv.tpb), BRASS_VOID)
        cv.put(ring & (np.abs(X - sx0) < 1.6 / cv.tpb) & (np.abs(Y - ny) < 1.6 / cv.tpb), CHRONO_TEAL)


def clock_art(w, h):
    """Ivory dial, brass bezel, twelve ticks, XII/III/VI/IX, hands at 10:10, teal pin."""
    im = Image.new('RGBA', (w, h))
    d = ImageDraw.Draw(im)
    d.ellipse((0, 0, w - 1, h - 1), fill=BRASS_GOLD)
    d.arc((0, 0, w - 1, h - 1), 200, 330, fill=BRASS_BRIGHT, width=2)
    d.arc((0, 0, w - 1, h - 1), 20, 150, fill=BRASS_MID, width=2)
    d.ellipse((3, 3, w - 4, h - 4), fill=BRASS_VOID)
    d.ellipse((4, 4, w - 5, h - 5), fill=DIAL_RIM)
    d.ellipse((6, 6, w - 7, h - 7), fill=DIAL_IVORY)
    cx, cy, r = (w - 1) / 2, (h - 1) / 2, (w - 1) / 2 - 7
    for i in range(12):
        a = math.radians(i * 30)
        x0, y0 = cx + math.sin(a) * r, cy - math.cos(a) * r
        x1, y1 = cx + math.sin(a) * (r - (3 if i % 3 == 0 else 1.5)), cy - math.cos(a) * (r - (3 if i % 3 == 0 else 1.5))
        d.line((x0, y0, x1, y1), fill=DIAL_HAND if i % 3 == 0 else DIAL_RIM)
    roman = {'XII': ('10101010', '10101010', '01001010', '10101010', '10101010'),
             'III': ('10101', '10101', '10101', '10101', '10101'),
             'VI': ('101010', '101010', '101010', '101010', '010010'),
             'IX': ('10101', '10101', '10010', '10101', '10101')}
    for label, (gx, gy) in (('XII', (0, -1)), ('III', (1, 0)), ('VI', (0, 1)), ('IX', (-1, 0))):
        rows = roman[label]
        gw = len(rows[0])
        ox = round(cx + gx * (r - 6) - gw / 2 + .5)
        oy = round(cy + gy * (r - 6) - 2)
        for ry, row in enumerate(rows):
            for rx, bit in enumerate(row):
                if bit == '1':
                    d.point((ox + rx, oy + ry), fill=DIAL_HAND)
    d.line((cx, cy, cx - r * .55, cy - r * .32), fill=DIAL_HAND, width=2)
    d.line((cx, cy, cx + r * .70, cy - r * .45), fill=DIAL_HAND, width=1)
    d.ellipse((cx - 1.5, cy - 1.5, cx + 1.5, cy + 1.5), fill=CHRONO_TEAL)
    a = np.asarray(im)
    return np.dstack([a[..., :3], np.where(a[..., 3] > 0, 255, 0)])


def main():
    pack = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    emit_weapon(pack, 'weapon/crystal_greatsword', define_crystal_greatsword, 2.3, 13.0)
    emit_weapon(pack, 'weapon/stormcaller', define_stormcaller, 2.2, 14.5)
    emit_weapon(pack, 'weapon/chrono_dagger', define_chrono_dagger, 2.5, 11.75)


if __name__ == '__main__':
    main()
