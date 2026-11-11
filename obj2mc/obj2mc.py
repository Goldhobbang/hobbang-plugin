#!/usr/bin/env python3
"""obj2mc — textured high-poly OBJ -> Minecraft Java (1.21.11) cuboid item models.

Pipeline
  1. load OBJ (+MTL texture), normalise to block units (feet at y=0, centred in X/Z,
     front = +Z, height = config.height_blocks)
  2. dense surface sampling -> coloured point cloud (texture sampled by UV)
  3. voxelise at `resolution` voxels per block, fill the interior
  4. split voxels into parts by ordered region rules (first match wins)
  5. per part: greedy-merge voxels into disjoint cuboids (shape only, colour ignored),
     drop faces fully covered by the same part, bake each visible face into a texture
     atlas (`texels_per_voxel` px per voxel edge, colour = nearest surface sample)
  6. emit assets/<ns>/items/..., models/item/..., textures/item/... and a manifest
     with pivot / scale / translation for item_display parts.

Usage:  python obj2mc.py configs/abyssal_smith.json [--resolution 16] [--preview]
"""
import argparse, json, math, os, sys, time
import numpy as np
import trimesh
from PIL import Image
from scipy import ndimage
from scipy.spatial import cKDTree

HERE = os.path.dirname(os.path.abspath(__file__))

# Minecraft FaceInfo vertex order (from net.minecraft.client.renderer.FaceInfo, 1.21.11).
# Each vertex: (x,y,z) with 0=min, 1=max. UV rule (BlockElementFace.UVs):
#   U = minU for vertex 0,1 else maxU ; V = minV for vertex 0,3 else maxV
FACE_VERTS = {
    'down':  [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    'up':    [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    'north': [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    'south': [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    'west':  [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    'east':  [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}
FACE_NORMAL = {'down': (0, -1, 0), 'up': (0, 1, 0), 'north': (0, 0, -1),
               'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}


def log(*a):
    print(*a, flush=True)


# ----------------------------------------------------------------------------- mesh
MODELS_ROOT = os.path.join(HERE, '..', 'work', 'models')


def find_asset(base, p):
    """config paths were written for the build box (/workspace/mc/models/...). If the path does not
    exist, look for the same file name under MODELS_ROOT (zips from models-3d/ unpacked there)."""
    q = os.path.join(base, p)
    if os.path.exists(q):
        return q
    name = os.path.basename(p)
    for root, _, fs in os.walk(MODELS_ROOT):
        if name in fs:
            return os.path.join(root, name)
    raise FileNotFoundError(p)


def load_mesh(cfg, base):
    path = find_asset(base, cfg['obj'])
    m = trimesh.load(path, process=False, force='mesh')
    tex_path = cfg.get('texture')
    if tex_path:
        tex = np.array(Image.open(find_asset(base, tex_path)).convert('RGBA'))
    else:
        tex = np.array(m.visual.material.image.convert('RGBA'))
    v = np.asarray(m.vertices, np.float64)
    # optional axis fix: rotate about Y so the model faces +Z
    yaw = math.radians(cfg.get('source_yaw_deg', 0))
    if yaw:
        c, s = math.cos(yaw), math.sin(yaw)
        v = v @ np.array([[c, 0, -s], [0, 1, 0], [s, 0, c]]).T
    # optional general re-orientation: list of [axis, degrees] applied in order (right-handed),
    # e.g. [["x", 90]] stands a mesh lying along -Z upright (the -Z end goes to +Y)
    for ax, deg in cfg.get('source_rot', []):
        a = math.radians(deg); c, s = math.cos(a), math.sin(a)
        R = {'x': [[1, 0, 0], [0, c, -s], [0, s, c]],
             'y': [[c, 0, s], [0, 1, 0], [-s, 0, c]],
             'z': [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[ax]
        v = v @ np.array(R).T
    lo, hi = v.min(0), v.max(0)
    scale = cfg['height_blocks'] / (hi[1] - lo[1])
    ctr = (lo + hi) / 2
    v = (v - [ctr[0], lo[1], ctr[2]]) * scale
    uv = np.asarray(m.visual.uv, np.float64)
    return v, np.asarray(m.faces), uv, tex


def sample_tex(tex, uv):
    """bilinear sample, uv in OBJ convention (v up). returns float rgba"""
    h, w = tex.shape[:2]
    x = (uv[:, 0] % 1.0) * w - 0.5
    y = (1 - (uv[:, 1] % 1.0)) * h - 0.5
    x0 = np.floor(x).astype(int); y0 = np.floor(y).astype(int)
    fx = (x - x0)[:, None]; fy = (y - y0)[:, None]
    x0c, x1c = np.clip(x0, 0, w - 1), np.clip(x0 + 1, 0, w - 1)
    y0c, y1c = np.clip(y0, 0, h - 1), np.clip(y0 + 1, 0, h - 1)
    t = tex.astype(np.float64)
    return (t[y0c, x0c] * (1 - fx) * (1 - fy) + t[y0c, x1c] * fx * (1 - fy)
            + t[y1c, x0c] * (1 - fx) * fy + t[y1c, x1c] * fx * fy)


def surface_samples(v, f, uv, tex, spacing):
    """stratified samples on every triangle so that spacing <= `spacing` blocks"""
    tri = v[f]
    area = 0.5 * np.linalg.norm(np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0]), axis=1)
    n = np.maximum(1, np.ceil(area / (spacing ** 2) * 2.0)).astype(int)
    n = np.minimum(n, 4000)
    idx = np.repeat(np.arange(len(f)), n)
    r1 = np.sqrt(np.random.rand(len(idx), 1)); r2 = np.random.rand(len(idx), 1)
    b = np.hstack([1 - r1, r1 * (1 - r2), r1 * r2])
    # include vertices + edge midpoints so thin slivers are always covered
    pts = (tri[idx] * b[:, :, None]).sum(1)
    tuv = (uv[f][idx] * b[:, :, None]).sum(1)
    nrm = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    nrm /= np.linalg.norm(nrm, axis=1, keepdims=True) + 1e-12
    extra_b = np.array([[1, 0, 0], [0, 1, 0], [0, 0, 1], [.5, .5, 0], [0, .5, .5], [.5, 0, .5], [1/3, 1/3, 1/3]])
    e_pts = np.einsum('kj,tjd->tkd', extra_b, tri).reshape(-1, 3)
    e_uv = np.einsum('kj,tjd->tkd', extra_b, uv[f]).reshape(-1, 2)
    e_idx = np.repeat(np.arange(len(f)), len(extra_b))
    pts = np.vstack([pts, e_pts]); tuv = np.vstack([tuv, e_uv]); idx = np.concatenate([idx, e_idx])
    col = sample_tex(tex, tuv)
    return pts, col, nrm[idx]


# ----------------------------------------------------------------------------- voxels
def voxelize(pts, res, pad=2):
    lo = np.floor(pts.min(0) * res) - pad
    hi = np.ceil(pts.max(0) * res) + pad
    dims = (hi - lo).astype(int)
    ijk = np.floor(pts * res - lo).astype(int)
    surf = np.zeros(dims, bool)
    surf[ijk[:, 0], ijk[:, 1], ijk[:, 2]] = True
    solid = ndimage.binary_fill_holes(surf)
    # close 1-voxel cracks between Meshy's overlapping shells, then fill again
    return solid, surf, lo


def remove_specks(occ, min_size):
    lab, n = ndimage.label(occ)
    if n <= 1:
        return occ
    sizes = ndimage.sum(occ, lab, range(1, n + 1))
    keep = np.zeros(n + 1, bool); keep[1:] = sizes >= min_size
    return keep[lab]


def scale_layout(cfg):
    """Regions/pivots are written for `layout_height` (default = height_blocks). Changing
    height_blocks later rescales them, so a boss can be resized without editing its parts."""
    import copy
    k = cfg['height_blocks'] / cfg.get('layout_height', cfg['height_blocks'])
    parts = copy.deepcopy(cfg['parts'])
    sc = lambda v: None if v is None else v * k
    for p in parts:
        if isinstance(p.get('pivot'), list):
            p['pivot'] = [x * k for x in p['pivot']]
        for key in ('regions', 'exclude'):
            for r in p.get(key, []):
                for f in ('min', 'max', 'center'):
                    if f in r: r[f] = [sc(x) for x in r[f]]
                if 'radius' in r: r['radius'] *= k
    return parts


def in_region(c, r):
    """axis-aligned box (min/max, null = unbounded), optionally intersected with a
    cylinder: {"center":[x,y,z], "radius":r, "axis":"x|y|z"} (distance measured
    perpendicular to the axis) or a sphere (axis omitted)."""
    mn = r.get('min', [None] * 3); mx = r.get('max', [None] * 3)
    m = np.ones(len(c), bool)
    if 'radius' in r:
        d = c - np.array(r['center'], float)
        ax = r.get('axis')
        if ax:
            d[:, 'xyz'.index(ax)] = 0
        m &= np.einsum('ij,ij->i', d, d) <= r['radius'] ** 2
    for a in range(3):
        if mn[a] is not None: m &= c[:, a] >= mn[a]
        if mx[a] is not None: m &= c[:, a] < mx[a]
    return m


def assign_parts(occ, lo, res, parts):
    ijk = np.argwhere(occ)
    c = (ijk + lo + 0.5) / res        # voxel centres in blocks
    label = np.full(len(ijk), -1)
    for pi, p in enumerate(parts):
        m = np.zeros(len(ijk), bool)
        for r in p.get('regions', [{}]):
            m |= in_region(c, r)
        for r in p.get('exclude', []):
            m &= ~in_region(c, r)
        m &= label < 0
        label[m] = pi
    grid = np.full(occ.shape, -1, np.int16)
    grid[ijk[:, 0], ijk[:, 1], ijk[:, 2]] = label
    return grid


# ----------------------------------------------------------------------------- texel colours
def make_ray_engine(v, f):
    mesh = trimesh.Trimesh(v, f, process=False)
    try:
        from trimesh.ray.ray_pyembree import RayMeshIntersector
        return RayMeshIntersector(mesh)
    except Exception:
        log('[obj2mc] embreex not available -> slow python ray caster')
        return mesh.ray


def color_texels(P, N, tree, rgb, rays, v, f, uv, tex, vox, cfg):
    """Colour = what you'd see looking at the face texel from outside: cast a ray from
    `ray_start_vox` voxels outside the face inward and sample the texture at the first hit.
    Texels whose ray misses (cut faces between parts, deep cavities) fall back to the
    k nearest surface samples."""
    start = cfg.get('ray_start_vox', 1.5) * vox
    maxd = start + cfg.get('ray_depth_vox', 2.5) * vox
    out = np.zeros((len(P), 3))
    got = np.zeros(len(P), bool)
    origins = P + N * start
    locs, ri, ti = rays.intersects_location(origins, -N, multiple_hits=False)
    if len(ri):
        d = np.einsum('ij,ij->i', locs - origins[ri], -N[ri])
        ok = d <= maxd
        ri, ti, locs = ri[ok], ti[ok], locs[ok]
        tri = v[f[ti]]
        bc = trimesh.triangles.points_to_barycentric(tri, locs)
        huv = (uv[f[ti]] * bc[:, :, None]).sum(1)
        out[ri] = sample_tex(tex, huv)[:, :3]
        got[ri] = True
    miss = ~got
    if miss.any():
        kn = cfg.get('color_knn', 4)
        d, ii = tree.query(P[miss] + N[miss] * vox * 0.25, k=kn)
        if kn == 1:
            out[miss] = rgb[ii]
        else:
            w = 1.0 / (d + vox * 0.05); w /= w.sum(1, keepdims=True)
            out[miss] = (rgb[ii] * w[..., None]).sum(1)
    log(f'[obj2mc]   texels {len(P)}: ray hit {got.mean() * 100:.1f}%')
    return out


# ----------------------------------------------------------------------------- merging
def greedy_boxes(occ, order):
    """disjoint boxes covering occ. order = axis extension order e.g. (0,1,2)."""
    occ = occ.copy()
    boxes = []
    idx = np.argwhere(occ)
    # iterate in lexicographic order of the reverse extension order
    key = np.lexsort([idx[:, order[0]], idx[:, order[1]], idx[:, order[2]]])
    for p in idx[key]:
        if not occ[tuple(p)]:
            continue
        lo = p.copy(); hi = p + 1
        for ax in order:
            while True:
                if hi[ax] >= occ.shape[ax]:
                    break
                sl = [slice(lo[i], hi[i]) for i in range(3)]
                sl[ax] = slice(hi[ax], hi[ax] + 1)
                if occ[tuple(sl)].all():
                    hi[ax] += 1
                else:
                    break
        occ[lo[0]:hi[0], lo[1]:hi[1], lo[2]:hi[2]] = False
        boxes.append((lo, hi))
    return boxes


def best_boxes(occ):
    best = None
    for order in [(0, 1, 2), (0, 2, 1), (1, 0, 2), (1, 2, 0), (2, 0, 1), (2, 1, 0)]:
        b = greedy_boxes(occ, order)
        if best is None or len(b) < len(best):
            best = b
    return best


def visible_faces(box, occ):
    lo, hi = box
    out = []
    pad = np.pad(occ, 1)
    L, Hh = lo + 1, hi + 1
    for name, n in FACE_NORMAL.items():
        ax = int(np.nonzero(n)[0][0]); d = n[ax]
        sl = [slice(L[i], Hh[i]) for i in range(3)]
        sl[ax] = slice(Hh[ax], Hh[ax] + 1) if d > 0 else slice(L[ax] - 1, L[ax])
        if not pad[tuple(sl)].all():
            out.append(name)
    return out


# ----------------------------------------------------------------------------- atlas
def shelf_pack(rects, pad):
    """rects: list of (w,h). returns positions, atlas size (square pow2)"""
    area = sum((w + 2 * pad) * (h + 2 * pad) for w, h in rects)
    maxw = max(w for w, h in rects) + 2 * pad
    size = 16
    while True:
        while size * size < area or size < maxw:
            size *= 2
        order = sorted(range(len(rects)), key=lambda i: (-rects[i][1], -rects[i][0]))
        pos = [None] * len(rects)
        x = y = shelf = 0
        ok = True
        for i in order:
            w, h = rects[i][0] + 2 * pad, rects[i][1] + 2 * pad
            if x + w > size:
                x, y = 0, y + shelf; shelf = 0
            if y + h > size:
                ok = False; break
            pos[i] = (x + pad, y + pad)
            x += w; shelf = max(shelf, h)
        if ok:
            return pos, size
        size *= 2


# ----------------------------------------------------------------------------- main build
def build(cfg, base, out_pack, res=None, tpv=None, quiet=False):
    t0 = time.time()
    res = res or cfg.get('resolution', 16)
    tpv = tpv or cfg.get('texels_per_voxel', 2)
    ns = cfg.get('namespace', 'hobbang')
    model_id = cfg['id']                       # e.g. boss/abyssal_smith
    v, f, uv, tex = load_mesh(cfg, base)
    vox = 1.0 / res
    pts, col, nrm = surface_samples(v, f, uv, tex, vox / 3)
    log(f'[obj2mc] {len(f)} tris -> {len(pts)} samples, res {res} vox/block, {tpv} texel/vox')
    occ, surf, lo = voxelize(pts, res)
    occ = remove_specks(occ, cfg.get('min_speck_voxels', 4))
    log(f'[obj2mc] grid {occ.shape}, solid voxels {occ.sum()}, surface {surf.sum()}')
    parts = scale_layout(cfg)
    grid = assign_parts(occ, lo, res, parts)
    tree = cKDTree(pts)
    rgb = col[:, :3]
    rays = make_ray_engine(v, f)

    # pivots, uniform model-units-per-voxel k so every part fits -16..32 around its pivot (8,8,8)
    ext_max = 0
    part_boxes = []
    for pi, p in enumerate(parts):
        pocc = grid == pi
        if not pocc.any():
            part_boxes.append(None); continue
        if cfg.get('per_part_speck_voxels'):
            pocc = remove_specks(pocc, cfg['per_part_speck_voxels'])
        ijk = np.argwhere(pocc)
        bmin = (ijk.min(0) + lo) / res; bmax = (ijk.max(0) + 1 + lo) / res
        cen = (ijk + lo + 0.5) / res
        centroid = cen.mean(0)
        if p['pivot'] == 'centroid':
            p['pivot'] = [round(float(x), 4) for x in centroid]
        elif p['pivot'] == 'bbox_center':
            p['pivot'] = [round(float(x), 4) for x in (bmin + bmax) / 2]
        if p.get('spin'):
            # spin axis = direction of least spread (normal of a ring / disc), via PCA
            w, vec = np.linalg.eigh(np.cov((cen - centroid).T))
            ax_ = vec[:, 0] * (1 if vec[np.argmax(np.abs(vec[:, 0])), 0] > 0 else -1)
            p['_spin_axis'] = [round(float(x), 4) for x in ax_]
        piv = np.array(p['pivot'], float)
        ext_max = max(ext_max, np.abs(bmin - piv).max(), np.abs(bmax - piv).max())
        part_boxes.append(pocc)
    margin = cfg.get('bound_margin_units', 0.5)
    k_units_per_block = min(16.0 * cfg.get('max_units_per_pixel', 1.0) * res / 16.0,
                            (24.0 - margin) / ext_max)
    # quantise to a friendly value so translation math is exact-ish
    k_units_per_block = math.floor(k_units_per_block * 4) / 4
    disp_scale = 16.0 / k_units_per_block     # item_display scale (blocks per 16 units)
    log(f'[obj2mc] max part extent from pivot {ext_max:.3f} blocks -> {k_units_per_block} model units/block, item_display scale {disp_scale:.4f}')

    import shutil
    for sub in ('models/item', 'textures/item', 'items'):   # drop stale parts from earlier runs
        shutil.rmtree(os.path.join(out_pack, 'assets', ns, *sub.split('/'), *model_id.split('/')), ignore_errors=True)
    manifest = {'id': f'{ns}:{model_id}', 'height_blocks': cfg['height_blocks'], 'resolution': res,
                'texels_per_voxel': tpv, 'model_units_per_block': k_units_per_block,
                'item_display_scale': disp_scale, 'front': '+Z (boss local, yaw 0 = south)',
                'note': 'item_display renders the model rotated 180deg about Y; the emitted model is pre-rotated so that '
                        'translation=pivot, left_rotation=identity, scale=item_display_scale reproduces the rest pose.',
                'parts': []}
    total = 0
    for pi, p in enumerate(parts):
        pocc = part_boxes[pi]
        if pocc is None:
            log(f'[obj2mc] part {p["name"]}: EMPTY'); continue
        boxes = best_boxes(pocc)
        faces = []   # (box index, face name, w_texels, h_texels)
        for bi, b in enumerate(boxes):
            for fn in visible_faces(b, pocc):
                faces.append((bi, fn))
        # face rect sizes in texels
        rects = []
        for bi, fn in faces:
            lo_b, hi_b = boxes[bi]
            vs = np.array(FACE_VERTS[fn], float)
            corner = lambda t: lo_b + (hi_b - lo_b) * t
            v0, v1, v3 = corner(vs[0]), corner(vs[1]), corner(vs[3])
            wu = int(round(np.abs(v3 - v0).sum())); hv = int(round(np.abs(v1 - v0).sum()))
            rects.append((wu * tpv, hv * tpv))
        pos, size = shelf_pack(rects, pad=1)
        atlas = np.zeros((size, size, 4), np.uint8)
        # bake texels
        qpts, qdst, qn = [], [], []
        for (bi, fn), (W, H), (px, py) in zip(faces, rects, pos):
            lo_b, hi_b = boxes[bi]
            vs = np.array(FACE_VERTS[fn], float)
            corner = lambda t: (lo_b + (hi_b - lo_b) * t + lo) / res
            v0, v1, v3 = corner(vs[0]), corner(vs[1]), corner(vs[3])
            a = (np.arange(W) + 0.5) / W; bb = (np.arange(H) + 0.5) / H
            A, B = np.meshgrid(a, bb)
            P = v0 + A[..., None] * (v3 - v0) + B[..., None] * (v1 - v0)
            qpts.append(P.reshape(-1, 3))
            qn.append(np.tile(np.array(FACE_NORMAL[fn], float), (W * H, 1)))
            yy, xx = np.meshgrid(np.arange(H) + py, np.arange(W) + px, indexing='ij')
            qdst.append(np.stack([yy.ravel(), xx.ravel()], 1))
        qpts = np.vstack(qpts); qdst = np.vstack(qdst); qn = np.vstack(qn)
        c = color_texels(qpts, qn, tree, rgb, rays, v, f, uv, tex, vox, cfg)
        c = np.clip(c * cfg.get('brightness', 1.0), 0, 255)
        atlas[qdst[:, 0], qdst[:, 1], :3] = c.astype(np.uint8)
        atlas[qdst[:, 0], qdst[:, 1], 3] = 255
        # extrude 1px border so nearest sampling at rect edges never bleeds
        for (W, H), (px, py) in zip(rects, pos):
            atlas[py - 1, px:px + W] = atlas[py, px:px + W]
            atlas[py + H, px:px + W] = atlas[py + H - 1, px:px + W]
            atlas[py - 1:py + H + 1, px - 1] = atlas[py - 1:py + H + 1, px]
            atlas[py - 1:py + H + 1, px + W] = atlas[py - 1:py + H + 1, px + W - 1]
        # elements in model units: m = 8 + k * Ry180(X - pivot)
        piv = np.array(p['pivot'], float)
        kk = k_units_per_block
        def to_model(Xb):  # Xb (...,3) blocks -> model units
            d_ = Xb - piv
            return np.stack([8 - kk * d_[..., 0], 8 + kk * d_[..., 1], 8 - kk * d_[..., 2]], -1)
        elements = []
        face_by_box = {}
        for fi, (bi, fn) in enumerate(faces):
            face_by_box.setdefault(bi, []).append(fi)
        # Ry180 swaps faces: world +X face becomes model -X (west) etc.
        swap = {'east': 'west', 'west': 'east', 'north': 'south', 'south': 'north', 'up': 'up', 'down': 'down'}
        # After the 180° turn the per-face UV orientation must be re-derived in model space.
        for bi, b in enumerate(boxes):
            if bi not in face_by_box:
                continue
            lo_b = (b[0] + lo) / res; hi_b = (b[1] + lo) / res
            m1, m2 = to_model(lo_b), to_model(hi_b)
            mfrom = np.minimum(m1, m2); mto = np.maximum(m1, m2)
            el = {'from': [round(float(x), 4) for x in mfrom], 'to': [round(float(x), 4) for x in mto], 'faces': {}}
            for fi in face_by_box[bi]:
                _, fn = faces[fi]
                (W, H), (px, py) = rects[fi], pos[fi]
                mf = swap[fn]
                # texel rect was painted in WORLD face orientation (v0->v3 = +u, v0->v1 = +v).
                # Find which model-space corners those world corners map to, and choose uv
                # (possibly flipped) so the same texel lands on the same spot.
                vs_w = np.array(FACE_VERTS[fn], float)
                Wc = lambda t: to_model(lo_b + (hi_b - lo_b) * t)
                w0, w1, w3 = Wc(vs_w[0]), Wc(vs_w[1]), Wc(vs_w[3])
                vs_m = np.array(FACE_VERTS[mf], float)
                Mc = lambda t: mfrom + (mto - mfrom) * t
                m0, m1_, m3 = Mc(vs_m[0]), Mc(vs_m[1]), Mc(vs_m[3])
                u_dir_m = m3 - m0; v_dir_m = m1_ - m0
                u_dir_w = w3 - w0; v_dir_w = w1 - w0
                su = np.sign(np.dot(u_dir_m, u_dir_w)); sv = np.sign(np.dot(v_dir_m, v_dir_w))
                if np.dot(u_dir_m, u_dir_w) == 0:
                    raise RuntimeError('uv axis mismatch')
                u1, u2 = px * 16.0 / size, (px + W) * 16.0 / size
                v1, v2 = py * 16.0 / size, (py + H) * 16.0 / size
                if su < 0: u1, u2 = u2, u1
                if sv < 0: v1, v2 = v2, v1
                el['faces'][mf] = {'uv': [round(u1, 5), round(v1, 5), round(u2, 5), round(v2, 5)], 'texture': '#t'}
            elements.append(el)
        tex_ref = f'{ns}:item/{model_id}/{p["name"]}'
        model = {'credit': 'obj2mc (HobbangEnhance)', 'texture_size': [size, size],
                 'textures': {'t': tex_ref, 'particle': tex_ref}, 'elements': elements,
                 'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}
        mdir = os.path.join(out_pack, 'assets', ns, 'models', 'item', *model_id.split('/'))
        tdir = os.path.join(out_pack, 'assets', ns, 'textures', 'item', *model_id.split('/'))
        idir = os.path.join(out_pack, 'assets', ns, 'items', *model_id.split('/'))
        for dd in (mdir, tdir, idir):
            os.makedirs(dd, exist_ok=True)
        with open(os.path.join(mdir, p['name'] + '.json'), 'w') as fh:
            json.dump(model, fh, separators=(',', ':'))
        Image.fromarray(atlas).save(os.path.join(tdir, p['name'] + '.png'), optimize=True)
        with open(os.path.join(idir, p['name'] + '.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': f'{ns}:item/{model_id}/{p["name"]}'}}, fh, indent=2)
        nfaces = len(faces)
        total += len(elements)
        log(f'[obj2mc] part {p["name"]:10s}: voxels {int(pocc.sum()):6d}  cuboids {len(elements):5d}  faces {nfaces:5d}  atlas {size}x{size}')
        manifest['parts'].append({'name': p['name'], 'item_model': f'{ns}:{model_id}/{p["name"]}',
                                  'parent': p.get('parent'), 'pivot_blocks': list(map(float, piv)),
                                  'translation': list(map(float, piv)), 'scale': disp_scale,
                                  'spin_axis': p.get('_spin_axis'),
                                  'cuboids': len(elements), 'faces': nfaces, 'texture_px': size,
                                  'voxels': int(pocc.sum())})
    manifest['total_cuboids'] = total
    if cfg.get('attach'):
        manifest['attach'] = cfg['attach']
    man_dir = os.path.join(os.path.dirname(out_pack.rstrip('/')), 'manifests')
    os.makedirs(man_dir, exist_ok=True)
    with open(os.path.join(man_dir, model_id.replace('/', '__') + '.json'), 'w') as fh:
        json.dump(manifest, fh, indent=2)
    log(f'[obj2mc] total cuboids {total}  ({time.time() - t0:.1f}s)')
    return manifest


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('config')
    ap.add_argument('--pack', default=os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    ap.add_argument('--resolution', type=float)
    ap.add_argument('--texels', type=int)
    ap.add_argument('--preview', action='store_true')
    a = ap.parse_args()
    cfg = json.load(open(a.config))
    base = os.path.dirname(os.path.abspath(a.config))
    np.random.seed(cfg.get('seed', 1))
    man = build(cfg, base, a.pack, a.resolution, a.texels)
    if a.preview:
        import preview
        preview.make_previews(cfg, base, a.pack, man)


if __name__ == '__main__':
    main()
