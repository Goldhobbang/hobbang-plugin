#!/usr/bin/env python3
"""Prism Colossus + Chrono Sentinel remodel (same method as the 1.5.2 smith, obj2mc/make_smith.py):
large hand-placed cuboid plates + 16 px/block hand-pixelled textures, colours ONLY from the original Meshy textures
(obj2mc/golem_palette.py prints the extraction), no random noise — every texel comes from a designed pattern.

  boss/prism_colossus   13 parts, 1.5.0 pivots / hierarchy, k = 5.0   keyword: overbearing (위압감)
  boss/chrono_sentinel   8 parts + gear_l / gear_r (new spinning cogs), 1.5.0 pivots / spin axes, k = 7.0
                         keyword: mechanical (기계적인)

Coordinates are world blocks in the boss frame (feet at y=0, +Z front, +X = the boss's left), rest pose.
A box may carry ONE rotation (axis, +-22.5 / +-45 deg) about its own centre — the Minecraft element limit.
Steeper directions use helpers that swap the box's long axis (67.5 / 90 deg) or stack stepped boxes.

usage: python make_golems.py [--pack DIR]
"""
import argparse, json, math, os, shutil, zlib
import numpy as np
from PIL import Image
from obj2mc import FACE_NORMAL, FACE_VERTS, shelf_pack

HERE = os.path.dirname(os.path.abspath(__file__))
NS = 'hobbang'
PPU = 16


def hx(s):
    s = s.lstrip('#'); return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


# ============================================================================ palettes (original textures only)
# Prism Colossus — models-3d/boss_prism-colossus_amethyst-golem.zip texture
#   stone: surface-sample percentiles p30..p99 + the light stone family of the texture image (#352d39..#5e5862)
S_VOID, S_DARK, S_BASE, S_MID, S_LIGHT, S_HI, S_EDGE = (hx(c) for c in
    ('#050907', '#161118', '#2a242d', '#3a343c', '#433d45', '#544d57', '#5e5862'))
#   amethyst: crystal-family percentiles p5..p99 + bright family (#f07bfc, #f4bfff, #ffefff)
CR = [hx(c) for c in ('#370067', '#45057f', '#511092', '#6514bd', '#801fe5', '#a933f6', '#d254fa', '#e469fb',
                      '#f07bfc', '#f4bfff', '#ffefff')]

# Chrono Sentinel — models-3d/boss_chrono-sentinel.zip texture
BR = [hx(c) for c in ('#371400', '#401c00', '#4b2300', '#5b3000', '#724503', '#8e590f', '#b17316', '#d18b1c')]
GOLD = [hx(c) for c in ('#e8a328', '#f1af2c', '#faba30', '#fdc538', '#ffcb3b', '#ffd551', '#ffd957')]
SHINE = [hx(c) for c in ('#ffea73', '#fff37e', '#ffffa6')]
DARK = [hx(c) for c in ('#220000', '#2a0d00', '#2b261f')]
HANDS = [hx(c) for c in ('#3b1500', '#4d2100', '#6d3b00')]
PATINA = [hx(c) for c in ('#3e4b3e', '#404f40', '#455e4a', '#4e5540', '#5a5c46')]
SAND = [hx(c) for c in ('#c37a18', '#e09e2a', '#fdc94b', '#fce26f')]
TEAL_DIM = [hx(c) for c in ('#366055', '#3e6a54')]                 # the sentinel's own (dim) teal family
# The sentinel texture has no bright teal; the time-rune glow uses the teal of the same chrono set's original texture
# (models-3d/weapon_chrono-scythe.zip, see docs/PROGRESS-1.5.2.md: CHRONO_TEAL / CHRONO_GLOW).
TEAL = [hx(c) for c in ('#44938a', '#6ac4b8')]


# ============================================================================ pixel helpers (no noise)
def fill(W, H, c):
    img = np.empty((H, W, 3), np.uint8); img[:] = c; return img


def bevel(img, hi, hi2, sh, sh2):
    """2-px bevel: light from the top-left. hi/hi2 = top rows / left cols, sh/sh2 = bottom rows / right cols"""
    H, W = img.shape[:2]
    if H >= 4:
        img[0, :] = hi; img[-1, :] = sh
        if H >= 7: img[1, 1:W - 1] = hi2; img[-2, 1:W - 1] = sh2
    if W >= 4:
        img[1:H - 1, 0] = hi2; img[1:H - 1, -1] = sh
        if W >= 7: img[2:H - 2, 1] = hi2 if H >= 7 else img[2:H - 2, 1]; img[2:H - 2, -2] = sh2
    return img


def put(img, x, y, c):
    H, W = img.shape[:2]
    if 0 <= x < W and 0 <= y < H: img[y, x] = c


def path(x0, y0, steps, rng, dx_choices=(-1, 0, 0, 1)):
    """deterministic stepped crack path going down (one texel per row, sideways steps chosen by a seeded generator)"""
    pts = [(x0, y0)]
    x = x0
    for i in range(steps):
        x += int(dx_choices[int(rng.integers(0, len(dx_choices)))])
        pts.append((x, y0 + i + 1))
    return pts


def glow_line(img, pts, tiers):
    """tiers: [outer, middle, core] — 3-step light bleed around a 1-px core line"""
    for dist, col in ((2, tiers[0]), (1, tiers[1])):
        for x, y in pts:
            for dx in range(-dist, dist + 1):
                for dy in range(-dist, dist + 1):
                    if abs(dx) + abs(dy) == dist: put(img, x + dx, y + dy, col)
    for x, y in pts: put(img, x, y, tiers[2])


def dark_line(img, pts, core, edge):
    for x, y in pts:
        put(img, x + 1, y, edge)
    for x, y in pts: put(img, x, y, core)


# ============================================================================ painters
def paint(kind, fn, W, H, tone, seed):
    rng = np.random.default_rng(seed)       # only picks crack START points / step directions: patterns, not noise
    side = fn in ('north', 'south', 'east', 'west')

    # ------------------------------------------------ colossus
    if kind in ('rock', 'rock_glow', 'rock_dark'):
        base = {'rock': S_BASE, 'rock_glow': S_BASE, 'rock_dark': S_DARK}[kind] if tone is None else tone
        img = fill(W, H, base)
        if fn == 'up':
            img[:] = S_MID if base != S_DARK else S_BASE
        bevel(img, S_HI, S_LIGHT, S_VOID, S_DARK)
        if fn == 'up' and W >= 6 and H >= 6:
            img[0, :] = S_EDGE; img[:, 0] = S_HI
        # chipped corners (worn edges)
        for (cx, cy, sx, sy) in ((0, 0, 1, 1), (W - 1, 0, -1, 1)):
            if W >= 8 and H >= 8:
                put(img, cx, cy, S_VOID); put(img, cx + sx, cy, S_DARK); put(img, cx, cy + sy, S_DARK)
        # strata: one ledge line with a highlight lip above it
        if side and H >= 10 and W >= 8:
            ly = int(H * (0.38 + 0.2 * rng.random()))
            x0 = int(W * 0.15); x1 = int(W * (0.62 + 0.25 * rng.random()))
            img[ly, x0:x1] = S_DARK; img[ly - 1, x0 + 1:x1 - 1] = S_LIGHT
        # cracks: dark fractures, or amethyst light leaking through on rock_glow
        if side and W >= 6 and H >= 8:
            n = 1 if W * H < 600 else 2
            for i in range(n):
                x0 = int(W * (0.25 + 0.5 * (i + rng.random()) / n))
                pts = path(x0, 1, int(H * (0.55 + 0.35 * rng.random())), rng)
                if kind == 'rock_glow':
                    glow_line(img, pts, (CR[1], CR[4], CR[7]))
                    for x, y in pts[::5]: put(img, x, y, CR[9])          # hot spots in the seam
                else:
                    dark_line(img, pts, S_VOID, S_DARK)
                    if len(pts) > 6:                                     # a short branch
                        bx, by = pts[len(pts) // 2]
                        dark_line(img, [(bx + k, by + k // 2) for k in range(1, 4)], S_VOID, S_DARK)
        return img

    if kind == 'crystal':
        t0, t1 = tone                     # gradient range along the crystal (0 = root, 1 = tip)
        img = np.empty((H, W, 3), np.uint8)
        ramp = CR[2:]                     # 9 tones #511092 .. #ffefff
        for y in range(H):
            t = t0 + (t1 - t0) * (1 - y / max(1, H - 1)) if side else t1
            img[y, :] = ramp[min(len(ramp) - 1, int(t * (len(ramp) - 1) + 0.5))]
        if side and W >= 3:
            q = max(1, W // 4)
            for y in range(H):                                            # lit facet left, shadow facet right
                li = ramp.index(tuple(img[y, W // 2])) if tuple(img[y, W // 2]) in ramp else 3
                img[y, :q] = ramp[min(len(ramp) - 1, li + 1)]
                img[y, W - q:] = ramp[max(0, li - 2)]
            img[:, q] = CR[9]                                             # facet edge highlight
            if H >= 6:
                img[-1, :] = CR[1]                                        # root in shadow
        if fn == 'up':
            img[:] = CR[10] if t1 > 0.9 else ramp[min(len(ramp) - 1, int(t1 * (len(ramp) - 1)) + 1)]
        if fn == 'down':
            img[:] = CR[1]
        return img

    if kind == 'core':                     # chest heart-crystal: concentric diamond light
        img = np.empty((H, W, 3), np.uint8)
        cx, cy = (W - 1) / 2, (H - 1) / 2
        rings = [CR[10], CR[9], CR[8], CR[6], CR[5], CR[4], CR[3], CR[1]]
        for y in range(H):
            for x in range(W):
                d = (abs(x - cx) / max(1, cx) + abs(y - cy) / max(1, cy)) / 2
                img[y, x] = rings[min(len(rings) - 1, int(d * len(rings) * 1.1))]
        return img

    if kind == 'eye':
        img = fill(W, H, CR[9])
        if W >= 3 and H >= 3: img[1:-1, 1:-1] = CR[10]
        return img

    # ------------------------------------------------ sentinel
    if kind in ('brass', 'brass_dark', 'frame'):
        b, hi, hi2, sh, sh2 = {'brass': (BR[5], BR[7], BR[6], BR[2], BR[3]),
                               'brass_dark': (BR[3], BR[5], BR[4], BR[1], BR[2]),
                               'frame': (BR[2], BR[4], BR[3], DARK[1], BR[1])}[kind]
        img = fill(W, H, b)
        bevel(img, hi, hi2, sh, sh2)
        if W >= 10 and H >= 10:                                           # panel seam + 4 rivets
            img[3, 3:W - 3] = sh2; img[H - 4, 3:W - 3] = hi2
            img[3:H - 3, 3] = sh2; img[3:H - 3, W - 4] = hi2
            for rx, ry in ((2, 2), (W - 3, 2), (2, H - 3), (W - 3, H - 3)):
                put(img, rx, ry, GOLD[5]); put(img, rx + 1, ry + 1, BR[1])
        elif W >= 6 and H >= 6:
            for rx, ry in ((1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)):
                put(img, rx, ry, GOLD[4])
        return img

    if kind == 'rune':                     # brass panel with an engraved time-rune channel glowing teal
        img = paint('brass', fn, W, H, None, seed)
        if side and W >= 6 and H >= 6:
            cx = W // 2
            pts = [(cx, y) for y in range(2, H - 2)]
            if H >= 10:                                                   # hour-glass glyph: two chevrons on the channel
                m = H // 2
                pts += [(cx - k, m - 3 + k) for k in range(1, 3)] + [(cx + k, m - 3 + k) for k in range(1, 3)]
                pts += [(cx - k, m + 3 - k) for k in range(1, 3)] + [(cx + k, m + 3 - k) for k in range(1, 3)]
            glow_line(img, pts, (TEAL_DIM[0], TEAL[0], TEAL[1]))
        return img

    if kind == 'gear':                     # gear disc: rim, recessed web, 4 spokes, hub
        img = paint('brass', fn, W, H, None, seed) if not side or fn in ('north', 'south') else fill(W, H, BR[6])
        if fn in ('north', 'south') and W >= 8 and H >= 8:
            cx, cy = (W - 1) / 2, (H - 1) / 2
            R = min(cx, cy)
            for y in range(H):
                for x in range(W):
                    r = math.hypot(x - cx, y - cy) / max(1, R)
                    if r > 1.05: continue
                    if r > 0.82: img[y, x] = BR[7] if (x - cx) + (y - cy) < 0 else BR[4]
                    elif r > 0.30:
                        spoke = min(abs(x - cx), abs(y - cy)) <= max(0.6, R * 0.09)
                        img[y, x] = BR[5] if spoke else BR[2]
                    elif r > 0.18: img[y, x] = GOLD[3]
                    else: img[y, x] = DARK[1]
        elif side and fn not in ('north', 'south'):
            img[:, ::3] = BR[5]                                           # milled rim
        return img

    if kind == 'tooth':
        img = fill(W, H, BR[6])
        bevel(img, GOLD[2], BR[7], BR[3], BR[4])
        return img

    if kind == 'dial':                     # gold clock face with 12 hour marks (4 long), ivory-hot centre
        img = fill(W, H, GOLD[3])
        if fn in ('north', 'south') and W >= 10:
            cx, cy = (W - 1) / 2, (H - 1) / 2
            R = min(cx, cy)
            for y in range(H):
                for x in range(W):
                    r = math.hypot(x - cx, y - cy) / R
                    if r > 0.92: img[y, x] = BR[6] if r < 1.0 else BR[4]
                    elif r < 0.55: img[y, x] = GOLD[5] if r > 0.2 else SHINE[2]
            for h in range(12):
                a = math.radians(h * 30)
                for rr in ((0.62, 0.86) if h % 3 == 0 else (0.72, 0.86)):
                    pass
                r0 = 0.6 if h % 3 == 0 else 0.72
                for k in range(int(r0 * R), int(0.86 * R) + 1):
                    put(img, int(round(cx + math.sin(a) * k)), int(round(cy - math.cos(a) * k)), HANDS[1])
        else:
            bevel(img, GOLD[6], GOLD[5], BR[5], BR[6])
        return img

    if kind == 'hand':
        img = fill(W, H, HANDS[1])
        if W >= 3: img[:, 0] = HANDS[2]; img[:, -1] = HANDS[0]
        return img

    if kind == 'lens':                     # teal eye lens in a brass socket
        img = fill(W, H, BR[2])
        if W >= 5 and H >= 5:
            cx, cy = (W - 1) / 2, (H - 1) / 2
            R = min(cx, cy)
            for y in range(H):
                for x in range(W):
                    r = math.hypot(x - cx, y - cy) / R
                    img[y, x] = TEAL[1] if r < 0.35 else TEAL[0] if r < 0.65 else TEAL_DIM[1] if r < 0.9 else BR[4]
            put(img, int(cx) - 1, int(cy) - 1, SHINE[2])
        return img

    if kind == 'glass':                    # hourglass bulb: tinted glass + amber sand + streak highlight
        img = fill(W, H, PATINA[4])
        if side:
            top_sand = tone == 'bottom'
            for y in range(H):
                fy = y / max(1, H - 1)
                if (top_sand and fy > 0.45) or (not top_sand and fy > 0.8):
                    img[y, :] = SAND[1] if fy < 0.9 else SAND[0]
                    if W >= 3: img[y, 1] = SAND[2]
            if W >= 4: img[1:H - 1, W // 3] = SAND[3]
            bevel(img, BR[7], BR[6], BR[3], BR[4]) if H >= 10 else None
        return img

    if kind == 'blade':                    # patina blade core with brass edges, centre time-rune, bright cutting edge
        img = fill(W, H, PATINA[2])
        if side:
            if W >= 4:
                img[:, 0] = SHINE[0]; img[:, 1] = BR[7]
                img[:, -1] = BR[3]; img[:, -2] = BR[6]
            if fn in ('north', 'south') and W >= 8:
                for y in range(H):
                    img[y, 2:W - 2] = PATINA[1] if y % 6 < 3 else PATINA[2]
                glow_line(img, [(W // 2, y) for y in range(1, H - 1)], (TEAL_DIM[1], TEAL[0], TEAL[1]))
        else:
            img[:] = BR[6]
        return img

    if kind == 'piston':                   # cylinder shading in stripes + collar rings
        img = fill(W, H, BR[5])
        if side and W >= 4:
            cols = [BR[3], BR[5], BR[7], SHINE[0], BR[6], BR[4], BR[2]]
            for x in range(W):
                img[:, x] = cols[min(len(cols) - 1, int(x / W * len(cols)))]
            for y in range(0, H, 6):
                img[y, :] = BR[2]
        return img

    if kind == 'axle':
        img = fill(W, H, DARK[2])
        if W >= 3: img[:, 1] = BR[3]
        return img

    return fill(W, H, (255, 0, 255))


# ============================================================================ rig builder
class Rig:
    def __init__(self, name):
        self.name = name; self.parts = {}

    def part(self, name, parent, pivot, spin_axis=None):
        self.parts[name] = dict(name=name, parent=parent, pivot=[float(x) for x in pivot], spin_axis=spin_axis, boxes=[])
        return name

    def box(self, part, c, s, kind, rot=None, tone=None):
        s = tuple(max(1 / 16, float(x)) for x in s)
        self.parts[part]['boxes'].append(dict(c=tuple(float(x) for x in c), s=s, kind=kind, rot=rot, tone=tone))

    # --- steeper directions ------------------------------------------------
    def spike(self, part, root, axis, angle, length, width, kind='crystal', segs=4, depth=None, tone=(0.05, 1.0)):
        """crystal / spike growing from `root` toward R_axis(angle)·(+Y). angle in multiples of 22.5 within [-90, 90].
        |angle| > 45 swaps the long axis onto the horizontal so the element rotation stays within +-45."""
        a = math.radians(angle)
        d = {'z': np.array([-math.sin(a), math.cos(a), 0.0]), 'x': np.array([0.0, math.cos(a), math.sin(a)])}[axis]
        if abs(angle) > 45:
            rot = (axis, angle - 90 * np.sign(angle)); long_ax = 0 if axis == 'z' else 2
        else:
            rot = (axis, angle) if angle else None; long_ax = 1
        depth = depth or width
        fr = {4: [(0.0, 0.42, 1.0), (0.42, 0.28, 0.74), (0.70, 0.18, 0.46), (0.88, 0.12, 0.22)],
              3: [(0.0, 0.5, 1.0), (0.5, 0.33, 0.62), (0.83, 0.17, 0.28)],
              2: [(0.0, 0.62, 1.0), (0.62, 0.38, 0.45)]}[segs]
        t0, t1 = tone
        for i, (off, ln, wf) in enumerate(fr):
            ctr = np.array(root, float) + d * length * (off + ln / 2)
            size = [width * wf, 0, depth * wf]
            if long_ax == 1: size = [width * wf, length * ln, depth * wf]
            elif long_ax == 0: size = [length * ln, width * wf, depth * wf]
            else: size = [depth * wf, width * wf, length * ln]
            seg_t = (t0 + (t1 - t0) * off, t0 + (t1 - t0) * min(1, off + ln))
            self.box(part, ctr, size, kind, rot if rot and rot[1] else None, seg_t)
        return np.array(root, float) + d * length

    def ring_y(self, part, centre, radius, n, seg_h, seg_w, kind, teeth=None):
        """horizontal ring of n tangential segments (n = 8 or 16) + optional outward teeth; rotation about Y"""
        cxyz = np.array(centre, float)
        for i in range(n):
            phi = 2 * math.pi * i / n
            pos = cxyz + radius * np.array([math.cos(phi), 0, math.sin(phi)])
            theta = -math.degrees(phi) - 90
            self._ybox(part, pos, 2 * radius * math.tan(math.pi / n) + 0.02, seg_h, seg_w, theta, kind)
            if teeth:
                tl, tw, th = teeth
                tphi = phi + math.pi / n
                tpos = cxyz + (radius + seg_w / 2 + tl / 2 - 0.01) * np.array([math.cos(tphi), 0, math.sin(tphi)])
                self._ybox(part, tpos, tw, th, tl, -math.degrees(tphi) - 90, 'tooth')

    def _ybox(self, part, pos, along, h, across, theta, kind):
        """box whose long ('along') axis points at world angle theta about Y (theta multiple of 22.5)"""
        t = ((theta + 90) % 180) - 90                     # long axis is direction-less: fold into (-90, 90]
        if t > 45: t -= 90; sx, sz = across, along
        elif t < -45: t += 90; sx, sz = across, along
        else: sx, sz = along, across
        t = round(t / 22.5) * 22.5
        self.box(part, pos, (sx, h, sz), kind, ('y', t) if t else None)

    def gear_z(self, part, centre, radius, thick, teeth=8, tooth=(0.09, 0.08), kind='gear', hub=True):
        """gear facing +Z: octagon disc (square + 45 deg square) with radial teeth (rotation about Z)"""
        c = np.array(centre, float)
        s = radius * 1.48                               # octagon = square + 45-degree square (apothem 0.74 r)
        self.box(part, c, (s, s, thick), kind)
        self.box(part, c, (s, s, max(thick * 0.6, thick - 0.04)), kind, ('z', 45))   # thinner: no coplanar faces
        tl, tw = tooth
        for i in range(teeth):
            phi = 2 * math.pi * i / teeth + (math.pi / teeth if teeth == 8 else 0)
            pos = c + (radius + tl / 2 - 0.02) * np.array([math.cos(phi), math.sin(phi), 0])
            ang = math.degrees(phi) - 90                  # radial: long axis along (cos, sin)
            t = ((ang + 90) % 180) - 90
            if t > 45: t -= 90; sx, sy = tl, tw
            elif t < -45: t += 90; sx, sy = tl, tw
            else: sx, sy = tw, tl
            t = round(t / 22.5) * 22.5
            self.box(part, pos, (sx, sy, thick * 0.9), 'tooth', ('z', t) if t else None)
        if hub:
            self.box(part, c + [0, 0, thick / 2 + 0.03], (radius * 0.42, radius * 0.42, 0.06), 'brass_dark')


# ============================================================================ Prism Colossus
def colossus():
    R = Rig('prism_colossus')
    # ---- pelvis (root)
    R.part('torso', None, (0, 2.3587, 0))
    R.box('torso', (0, 2.62, -0.05), (2.30, 0.95, 1.55), 'rock_dark')                        # hip block
    R.box('torso', (0, 3.20, -0.05), (2.55, 0.42, 1.70), 'rock')                             # belt ledge
    R.box('torso', (0, 2.20, 0.05), (0.95, 0.55, 1.05), 'rock_dark')                         # crotch stone
    for sx in (1, -1):
        R.box('torso', (sx * 0.62, 2.45, 0.72), (0.95, 0.85, 0.30), 'rock', ('z', sx * 22.5))     # front fauld slabs
        R.box('torso', (sx * 1.22, 2.70, -0.02), (0.36, 1.05, 1.40), 'rock_glow', ('z', -sx * 22.5))  # hip plates, lit seam
    R.box('torso', (0, 2.62, -0.88), (1.80, 0.85, 0.28), 'rock')                             # rear plate

    # ---- chest: huge barrel, shoulders wide, head sunk between them
    R.part('chest', 'torso', (0, 4.0935, 0))
    R.box('chest', (0, 4.30, -0.08), (2.75, 1.85, 1.95), 'rock_dark')                        # rib cage core
    R.box('chest', (0, 5.32, -0.10), (3.55, 0.72, 2.10), 'rock')                             # shoulder yoke
    R.box('chest', (0, 3.62, -0.02), (2.35, 0.55, 1.70), 'rock_glow')                         # lower ribs, lit seams
    for sx in (1, -1):
        R.box('chest', (sx * 0.78, 4.70, 0.86), (1.05, 1.25, 0.34), 'rock_glow', ('z', -sx * 22.5))   # pectoral slabs
        R.box('chest', (sx * 0.86, 3.92, 0.84), (0.95, 0.62, 0.30), 'rock', ('z', sx * 22.5))         # lower ribs
        R.box('chest', (sx * 1.30, 4.40, -0.10), (0.42, 1.55, 1.75), 'rock', ('z', sx * 22.5))         # flank slabs
        R.box('chest', (sx * 0.95, 4.65, -1.02), (1.15, 1.45, 0.34), 'rock_glow', ('z', sx * 22.5))   # back plates
    # heart crystal: a 45-degree diamond set in a dark socket, light bleeding into the frame
    R.box('chest', (0, 4.58, 0.98), (1.05, 1.05, 0.14), 'rock_dark', ('z', 45))
    R.box('chest', (0, 4.58, 1.06), (0.72, 0.72, 0.22), 'core', ('z', 45))
    R.box('chest', (0, 4.58, 1.16), (0.32, 0.32, 0.12), 'eye', ('z', 45))
    # back / shoulder amethyst eruption: different sizes, angles and lengths, nothing aligned
    for (x, y, z, ax, ang, L, w) in (
            (0.55, 5.62, -0.55, 'x', -22.5, 1.25, 0.42), (-0.35, 5.60, -0.70, 'x', -45, 1.05, 0.36),
            (0.05, 5.55, -0.92, 'x', -45, 1.45, 0.48), (-0.90, 5.55, -0.40, 'z', 22.5, 1.10, 0.40),
            (0.95, 5.50, -0.75, 'z', -22.5, 0.80, 0.30), (0.30, 4.90, -1.10, 'x', -67.5, 0.75, 0.30),
            (-0.60, 4.35, -1.12, 'x', -67.5, 0.62, 0.26), (0.62, 4.10, -1.10, 'x', -90, 0.55, 0.24)):
        R.spike('chest', (x, y, z), ax, ang, L, w)

    # ---- head: low brow, glowing eyes, crown of crystals
    R.part('head', 'chest', (0, 5.7826, 0))
    R.box('head', (0, 5.90, 0.22), (1.15, 0.80, 1.05), 'rock_dark')                          # skull block
    R.box('head', (0, 6.20, 0.52), (1.25, 0.30, 0.55), 'rock', ('x', 22.5))                   # heavy brow
    R.box('head', (0, 5.58, 0.62), (0.95, 0.30, 0.45), 'rock')                                # jaw slab
    for sx in (1, -1):
        R.box('head', (sx * 0.27, 5.93, 0.76), (0.22, 0.10, 0.06), 'eye')
        R.box('head', (sx * 0.62, 5.95, 0.15), (0.24, 0.62, 0.85), 'rock', ('z', -sx * 22.5))  # cheek stones
    for (x, z, ax, ang, L, w) in ((0, 0.05, 'x', 0, 1.15, 0.40), (0.38, -0.05, 'z', -22.5, 0.85, 0.32),
                                   (-0.40, 0.10, 'z', 22.5, 0.95, 0.30), (0.15, -0.35, 'x', -22.5, 0.70, 0.28),
                                   (-0.20, -0.30, 'z', 45, 0.55, 0.24)):
        R.spike('head', (x, 6.28, z), ax, ang, L, w)

    # ---- arms: boulder shoulders bristling with crystals, forearm, giant fist
    for sx, q in ((1, 'l'), (-1, 'r')):
        a = R.part('arm_' + q, 'chest', (sx * 1.9783, 5.3261, 0))
        R.box(a, (sx * 2.20, 5.42, -0.05), (1.45, 1.25, 1.65), 'rock')                         # shoulder boulder
        R.box(a, (sx * 2.12, 4.82, -0.05), (0.95, 0.85, 1.05), 'rock_dark')                    # upper arm
        R.box(a, (sx * 2.62, 5.25, 0.00), (0.40, 0.95, 1.35), 'rock_glow', ('z', sx * 22.5))   # outer pauldron slab
        for (dx, dz, ax, ang, L, w) in ((0.10, 0.25, 'z', -22.5, 1.35, 0.45), (0.45, -0.25, 'z', -45, 1.10, 0.38),
                                         (-0.30, -0.35, 'x', -22.5, 0.95, 0.34), (0.70, 0.30, 'z', -67.5, 0.70, 0.28),
                                         (-0.15, 0.45, 'x', 22.5, 0.60, 0.24)):
            R.spike(a, (sx * (2.20 + dx), 5.95, dz), ax, sx * ang if ax == 'z' else ang, L, w)
        fo = R.part('fore_' + q, a, (sx * (1.7188 if q == 'l' else 1.7812), 4.3065, -0.1562))
        R.box(fo, (sx * 2.18, 3.88, 0.02), (1.05, 1.05, 1.15), 'rock_dark')
        R.box(fo, (sx * 2.22, 4.30, 0.00), (1.20, 0.38, 1.28), 'rock')                         # elbow cap
        R.box(fo, (sx * 2.66, 3.80, 0.05), (0.32, 0.95, 1.10), 'rock_glow', ('z', sx * 22.5))  # outer bracer
        for (dx, y, dz, ax, ang, L, w) in ((0.60, 3.95, -0.25, 'z', -67.5, 0.80, 0.30), (0.52, 3.60, 0.30, 'z', -45, 0.65, 0.26),
                                            (0.10, 4.05, -0.55, 'x', -45, 0.55, 0.24), (0.25, 3.70, 0.55, 'x', 45, 0.50, 0.22)):
            R.spike(fo, (sx * (2.18 + dx), y, dz), ax, sx * ang if ax == 'z' else ang, L, w)
        fi = R.part('fist_' + q, fo, (sx * 2.0938, 3.3022, 0.2188))
        R.box(fi, (sx * 2.30, 2.62, 0.22), (1.50, 1.30, 1.55), 'rock')                         # fist
        R.box(fi, (sx * 2.30, 3.30, 0.18), (1.25, 0.32, 1.30), 'rock_dark')                    # wrist band
        for i, dz in enumerate((0.62, 0.22, -0.18, -0.58)):                                    # knuckles
            R.box(fi, (sx * 2.30 + sx * 0.02 * i, 1.95, dz + 0.22), (1.30, 0.32, 0.36), 'rock_dark' if i % 2 else 'rock')
        R.box(fi, (sx * 1.58, 2.70, 0.70), (0.38, 0.70, 0.45), 'rock')                         # thumb
        for (dx, y, dz, ax, ang, L, w) in (
                (0.55, 3.05, -0.30, 'z', -45, 1.00, 0.40), (0.75, 2.55, 0.35, 'z', -67.5, 0.90, 0.36),
                (0.45, 2.20, -0.55, 'z', -90, 0.70, 0.30), (0.20, 3.20, 0.55, 'z', -22.5, 0.75, 0.30),
                (0.65, 2.85, 0.70, 'x', 45, 0.65, 0.30), (0.10, 2.40, 0.95, 'x', 67.5, 0.60, 0.28),
                (0.70, 2.30, -0.20, 'z', -90, 0.55, 0.26), (-0.20, 2.95, -0.70, 'x', -45, 0.60, 0.28),
                (0.40, 3.30, 0.10, 'z', -22.5, 0.55, 0.24)):
            R.spike(fi, (sx * (2.30 + dx), y, 0.22 + dz), ax, sx * ang if ax == 'z' else ang, L, w)

    # ---- legs: thick and short, knee crystals, broad feet
    for sx, q in ((1, 'l'), (-1, 'r')):
        lg = R.part('leg_' + q, None, (sx * 0.837, 2.3587, 0))
        R.box(lg, (sx * 0.90, 1.90, -0.08), (1.25, 1.10, 1.35), 'rock')                         # thigh
        R.box(lg, (sx * 1.40, 1.95, -0.05), (0.32, 0.95, 1.20), 'rock_glow', ('z', sx * 22.5))  # outer thigh slab
        R.spike(lg, (sx * 0.82, 1.55, 0.62), 'x', 22.5, 0.95, 0.40)                             # knee crystal
        R.spike(lg, (sx * 1.45, 2.10, -0.30), 'z', -sx * 67.5, 0.55, 0.24)
        sh = R.part('shin_' + q, lg, (sx * (0.8958 if q == 'l' else 0.9062), 1.4, -0.3542))
        R.box(sh, (sx * 0.92, 0.98, -0.12), (1.30, 0.95, 1.30), 'rock_dark')                    # shin column
        R.box(sh, (sx * 0.95, 0.28, 0.05), (1.65, 0.56, 2.05), 'rock')                          # foot slab
        R.box(sh, (sx * 0.95, 0.66, 0.40), (1.40, 0.30, 1.05), 'rock', ('x', 22.5))             # instep
        for dx in (-0.48, 0.0, 0.48):                                                           # toes
            R.box(sh, (sx * 0.95 + dx, 0.20, 1.18), (0.44, 0.40, 0.42), 'rock_dark')
        R.box(sh, (sx * 0.95, 0.30, -1.05), (1.20, 0.50, 0.30), 'rock_dark')                    # heel
    return R


# ============================================================================ Chrono Sentinel
SEN_CLOCK = (0.0013, 3.1295, 0.5763)


def sentinel():
    R = Rig('chrono_sentinel')
    R.part('body', None, (0, 2.5, 0))
    # chassis: spine + back plate + shoulder yoke
    R.box('body', (0, 3.20, -0.28), (0.95, 2.20, 0.30), 'brass_dark')                         # back plate
    R.box('body', (0, 2.70, -0.02), (0.42, 1.10, 0.42), 'frame')                              # spine column
    R.box('body', (0, 3.76, -0.05), (2.05, 0.26, 0.40), 'brass')                              # shoulder yoke
    R.box('body', (0, 3.62, -0.05), (1.60, 0.10, 0.34), 'frame')
    # clock housing: octagonal bezel, gold dial (static), hands live in the spinning 'clock' part
    cx, cy, cz = SEN_CLOCK
    R.box('body', (cx, cy, 0.34), (1.06, 1.06, 0.34), 'brass')                                # bezel case (octagon)
    R.box('body', (cx, cy, 0.33), (1.06, 1.06, 0.30), 'brass', ('z', 45))
    R.box('body', (cx, cy, 0.53), (0.92, 0.92, 0.10), 'dial')                                 # dial plate (octagon)
    R.box('body', (cx, cy, 0.51), (0.92, 0.92, 0.08), 'dial', ('z', 45))
    R.box('body', (cx, cy, 0.20), (1.30, 1.30, 0.10), 'frame', ('z', 22.5))                  # rear gear plate (16-point star behind the bezel)
    R.box('body', (cx, cy, 0.19), (1.30, 1.30, 0.08), 'frame', ('z', -22.5))
    for i in range(12):                                                                       # bezel studs
        a = 2 * math.pi * i / 12
        R.box('body', (cx + 0.57 * math.sin(a), cy + 0.57 * math.cos(a), 0.52), (0.08, 0.08, 0.06), 'tooth')
    # head unit with a teal lens and an axle up to the crown gear
    R.box('body', (0, 4.08, 0.0), (0.62, 0.52, 0.58), 'brass')
    R.box('body', (0, 4.10, 0.30), (0.30, 0.30, 0.06), 'lens')
    R.box('body', (0, 4.38, 0.0), (0.44, 0.10, 0.44), 'frame')
    R.box('body', (0, 4.66, 0.0), (0.08, 0.50, 0.08), 'axle')
    R.gear_z('body', (0, 4.95, 0.0), 0.22, 0.10, teeth=8, tooth=(0.08, 0.07))
    # pistons from the yoke to the waist
    for sx in (1, -1):
        R.box('body', (sx * 0.40, 3.20, -0.05), (0.16, 0.70, 0.16), 'piston')                  # cylinder
        R.box('body', (sx * 0.40, 2.68, -0.05), (0.08, 0.40, 0.08), 'axle')                     # rod
        R.box('body', (sx * 0.40, 2.46, -0.05), (0.20, 0.06, 0.20), 'brass_dark')               # foot clamp
        R.box('body', (sx * 0.98, 3.66, 0.0), (0.40, 0.40, 0.48), 'brass_dark')                 # shoulder socket
        R.box('body', (sx * 0.98, 3.66, 0.0), (0.40, 0.40, 0.40), 'brass_dark', ('z', 45))
        # satellite cogs on struts (were floating in the original)
        R.box('body', (sx * 0.86, 4.30, -0.05), (0.06, 0.22, 0.06), 'axle')
        R.gear_z('body', (sx * 0.86, 4.42, -0.05), 0.13, 0.07, teeth=8, tooth=(0.06, 0.05), hub=False)
        R.box('body', (sx * 0.60, 2.70, 0.05), (0.40, 0.05, 0.05), 'axle')
        R.gear_z('body', (sx * 0.86, 2.68, 0.05), 0.14, 0.07, teeth=8, tooth=(0.06, 0.05), hub=False)
        R.box('body', (sx * 0.30, 3.40, 0.32), (0.22, 0.05, 0.05), 'axle')                      # axle out to gear_x
    # waist ring + neck down to the pendulum mount
    R.box('body', (0, 2.30, 0.0), (0.70, 0.12, 0.70), 'brass')
    R.box('body', (0, 2.30, 0.0), (0.70, 0.08, 0.70), 'brass', ('y', 45))

    # ---- side cogs beside the dial (new spinning parts, mesh with the clock)
    for sx, q in ((1, 'l'), (-1, 'r')):
        g = R.part('gear_' + q, 'body', (sx * 0.78, 3.40, 0.30))
        R.gear_z(g, (sx * 0.78, 3.40, 0.30), 0.24, 0.10, teeth=8, tooth=(0.09, 0.08))

    # ---- halo ring (spins about Y): flat, 16 segments, 16 outward teeth, teal rune on the segments
    R.part('ring', 'body', (0.0041, 4.2407, 0.1224), spin_axis=[0.0041, 0.9326, 0.3608])
    R.ring_y('ring', (0.0041, 4.2407, 0.1224), 0.92, 16, 0.12, 0.10, 'rune', teeth=(0.09, 0.08, 0.08))
    for i in range(4):                                                                        # 4 spokes to a hub
        phi = math.pi / 4 + i * math.pi / 2
        R._ybox('ring', (0.0041 + 0.62 * math.cos(phi), 4.2407, 0.1224 + 0.62 * math.sin(phi)), 0.56, 0.05, 0.05,
                -math.degrees(phi), 'axle')

    # ---- clock hands (spin with the gear angle about the original spin axis)
    R.part('clock', 'body', SEN_CLOCK, spin_axis=[-0.0033, -0.0311, 0.9995])
    R.box('clock', (cx, cy + 0.16, 0.61), (0.08, 0.34, 0.04), 'hand')                         # hour hand (12)
    R.box('clock', (cx + 0.15, cy + 0.15, 0.645), (0.06, 0.44, 0.04), 'hand', ('z', -45))     # minute hand (1:30)
    R.box('clock', (cx, cy, 0.66), (0.12, 0.12, 0.06), 'brass_dark')                          # centre cap
    R.box('clock', (cx, cy, 0.655), (0.12, 0.12, 0.04), 'brass_dark', ('z', 45))
    R.box('clock', (cx, cy - 0.10, 0.62), (0.03, 0.18, 0.03), 'hand')                         # counterweight tail

    # ---- arms: two-stage strut (22.5 / 45 deg) with an elbow cog and a piston, then the blade
    for sx, q in ((1, 'l'), (-1, 'r')):
        a = R.part('arm_' + q, 'body', (sx * 0.9821, 3.6607, 0))
        R.box(a, (sx * 1.13, 3.33, 0.0), (0.26, 0.72, 0.30), 'rune', ('z', sx * 22.5))          # upper arm
        R.box(a, (sx * 1.25, 3.37, 0.0), (0.06, 0.62, 0.20), 'piston', ('z', sx * 22.5))
        R.box(a, (sx * 1.50, 2.80, 0.05), (0.22, 0.64, 0.26), 'brass', ('z', sx * 45))          # forearm
        R.box(a, (sx * 1.60, 2.90, 0.05), (0.06, 0.55, 0.18), 'piston', ('z', sx * 45))
        R.gear_z(a, (sx * 1.33, 3.00, 0.18), 0.17, 0.07, teeth=8, tooth=(0.06, 0.06))           # elbow cog
        R.box(a, (sx * 1.78, 2.64, 0.10), (0.36, 0.20, 0.36), 'brass_dark')                     # wrist collar
        R.box(a, (sx * 1.78, 2.64, 0.10), (0.36, 0.16, 0.36), 'brass_dark', ('y', 45))
        b = R.part('blade_' + q, a, (sx * 1.7708, 2.5536, 0.1875))
        # leaf blade leaning forward 22.5 deg, outward by small steps; widths swell then taper
        widths = (0.42, 0.72, 0.80, 0.66, 0.46, 0.22)
        L = 0.34
        for i, w in enumerate(widths):
            t = i * L + L / 2
            ctr = (sx * (1.80 + 0.045 * i), 2.42 - t * math.cos(math.radians(22.5)), 0.22 + t * math.sin(math.radians(22.5)))
            R.box(b, ctr, (w, L + 0.02, 0.12), 'blade', ('x', -22.5))
            if 1 <= i <= 4:                                                                   # spine ridge
                R.box(b, ctr, (0.10, L + 0.02, 0.20), 'brass', ('x', -22.5))
        tip_t = len(widths) * L + 0.08
        R.box(b, (sx * (1.80 + 0.27), 2.42 - tip_t * 0.924, 0.22 + tip_t * 0.383), (0.08, 0.18, 0.08), 'tooth', ('x', -22.5))
        R.gear_z(b, (sx * 1.78, 2.50, 0.30), 0.15, 0.07, teeth=8, tooth=(0.06, 0.05))          # blade hub cog

    # ---- pendulum: hourglass in a riveted cage, base plate, pendant cog
    pz = 0.2292
    R.part('pendulum', 'body', (0, 2.1964, pz))
    R.box('pendulum', (0, 2.12, pz), (0.30, 0.16, 0.30), 'brass_dark')                       # hanger
    R.box('pendulum', (0, 1.98, pz), (0.70, 0.10, 0.50), 'brass')                            # top plate
    R.box('pendulum', (0, 1.07, pz), (0.80, 0.12, 0.56), 'brass')                            # bottom plate
    for sx in (1, -1):
        for sz in (1, -1):
            R.box('pendulum', (sx * 0.30, 1.52, pz + sz * 0.18), (0.07, 0.86, 0.07), 'piston')  # cage posts
    R.box('pendulum', (0, 1.76, pz), (0.36, 0.34, 0.30), 'glass', None, 'top')                # upper bulb
    R.box('pendulum', (0, 1.52, pz), (0.10, 0.14, 0.10), 'glass', None, 'top')                # neck
    R.box('pendulum', (0, 1.28, pz), (0.36, 0.34, 0.30), 'glass', None, 'bottom')             # lower bulb (sand)
    for sx in (1, -1):
        R.box('pendulum', (sx * 0.30, 1.52, pz + 0.28), (0.12, 0.30, 0.04), 'rune')            # front rune plates
    R.box('pendulum', (0, 0.90, pz), (0.40, 0.22, 0.40), 'brass_dark')
    R.box('pendulum', (0, 0.90, pz), (0.40, 0.18, 0.40), 'brass_dark', ('y', 45))
    R.box('pendulum', (0, 0.66, pz), (0.12, 0.30, 0.12), 'axle')
    R.gear_z('pendulum', (0, 0.36, pz), 0.14, 0.07, teeth=8, tooth=(0.06, 0.05))
    return R


# ============================================================================ emitter
def emit(pack, R, height, k):
    model_id = 'boss/' + R.name
    S = np.array([-1.0, 1.0, -1.0])
    disp = 16.0 / k
    for sub in ('models/item', 'textures/item', 'items'):
        shutil.rmtree(os.path.join(pack, 'assets', NS, *sub.split('/'), *model_id.split('/')), ignore_errors=True)
    man = {'id': f'{NS}:{model_id}', 'height_blocks': height, 'resolution': PPU, 'texels_per_voxel': 1,
           'model_units_per_block': k, 'item_display_scale': disp, 'front': '+Z (boss local, yaw 0 = south)',
           'note': 'hand-designed cuboid model (make_golems.py, 1.5.x golem remodel); 16px/block pixel textures, original palette only.',
           'parts': []}
    # exact 1.5.0 pivots: the frozen pivots in configs/<boss>.json (layout units) x height/layout, so BossPoses offsets
    # stay bit-identical. Values typed above are 4-decimal roundings and must agree within 0.001 block.
    cfg = json.load(open(os.path.join(HERE, 'configs', R.name + '.json')))
    kk = cfg['height_blocks'] / cfg.get('layout_height', cfg['height_blocks'])
    exact = {q['name']: [x * kk for x in q['pivot']] for q in cfg['parts'] if isinstance(q.get('pivot'), list)}
    for pname, p in R.parts.items():
        if pname in exact:
            d = max(abs(a_ - b_) for a_, b_ in zip(p['pivot'], exact[pname]))
            assert d < 1e-3, (pname, p['pivot'], exact[pname])
            p['pivot'] = exact[pname]
    total = 0; tex_sum = 0; clipped = []
    for pname, p in R.parts.items():
        piv = np.array(p['pivot'])
        rects, jobs = [], []
        for bi, b in enumerate(p['boxes']):
            cm = 8 + k * S * (np.array(b['c']) - piv); sm = k * np.array(b['s'])
            fr, to = cm - sm / 2, cm + sm / 2
            if (fr < -16).any() or (to > 32).any():
                clipped.append((pname, bi))
            b['_from'], b['_to'] = np.clip(fr, -16, 32), np.clip(to, -16, 32)
            for fn in FACE_NORMAL:
                vs = np.array(FACE_VERTS[fn], float)
                cn = lambda t: b['_from'] + (b['_to'] - b['_from']) * t
                v0, v1, v3 = cn(vs[0]), cn(vs[1]), cn(vs[3])
                W = max(1, int(round(np.linalg.norm(v3 - v0) / k * PPU)))
                H = max(1, int(round(np.linalg.norm(v1 - v0) / k * PPU)))
                rects.append((W, H)); jobs.append((bi, fn))
        pos, size = shelf_pack(rects, pad=1)
        atlas = np.zeros((size, size, 4), np.uint8)
        els = [None] * len(p['boxes'])
        for (bi, fn), (W, H), (px, py) in zip(jobs, rects, pos):
            b = p['boxes'][bi]
            seed = zlib.crc32(f'{R.name}/{pname}/{bi}/{fn}'.encode())
            atlas[py:py + H, px:px + W, :3] = paint(b['kind'], fn, W, H, b['tone'], seed)
            atlas[py:py + H, px:px + W, 3] = 255
            atlas[py - 1, px:px + W] = atlas[py, px:px + W]; atlas[py + H, px:px + W] = atlas[py + H - 1, px:px + W]
            atlas[py - 1:py + H + 1, px - 1] = atlas[py - 1:py + H + 1, px]
            atlas[py - 1:py + H + 1, px + W] = atlas[py - 1:py + H + 1, px + W - 1]
            if els[bi] is None:
                el = {'from': [round(float(x), 4) for x in b['_from']], 'to': [round(float(x), 4) for x in b['_to']]}
                if b['rot']:
                    ax, deg = b['rot']
                    el['rotation'] = {'angle': float(deg if ax == 'y' else -deg), 'axis': ax,
                                      'origin': [round(float(x), 4) for x in (b['_from'] + b['_to']) / 2]}
                el['faces'] = {}
                els[bi] = el
            els[bi]['faces'][fn] = {'uv': [round(px * 16 / size, 5), round(py * 16 / size, 5),
                                           round((px + W) * 16 / size, 5), round((py + H) * 16 / size, 5)], 'texture': '#t'}
        tref = f'{NS}:item/{model_id}/{pname}'
        model = {'credit': 'make_golems (HobbangEnhance)', 'texture_size': [size, size], 'textures': {'t': tref, 'particle': tref},
                 'elements': els, 'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}
        for sub in ('models/item', 'textures/item', 'items'):
            os.makedirs(os.path.join(pack, 'assets', NS, *sub.split('/'), *model_id.split('/')), exist_ok=True)
        json.dump(model, open(os.path.join(pack, 'assets', NS, 'models', 'item', *model_id.split('/'), pname + '.json'), 'w'), separators=(',', ':'))
        Image.fromarray(atlas).save(os.path.join(pack, 'assets', NS, 'textures', 'item', *model_id.split('/'), pname + '.png'), optimize=True)
        json.dump({'model': {'type': 'minecraft:model', 'model': tref}},
                  open(os.path.join(pack, 'assets', NS, 'items', *model_id.split('/'), pname + '.json'), 'w'), indent=2)
        n_rot = sum(1 for b in p['boxes'] if b['rot'])
        total += len(els); tex_sum += size * size
        man['parts'].append({'name': pname, 'item_model': f'{NS}:{model_id}/{pname}', 'parent': p['parent'],
                             'pivot_blocks': list(p['pivot']), 'translation': list(p['pivot']), 'scale': disp,
                             'spin_axis': p['spin_axis'], 'mode': 'box', 'cuboids': len(els), 'cuboids_voxel': len(els) - n_rot,
                             'cuboids_obb': n_rot, 'faces': len(els) * 6, 'texture_px': size, 'voxels': 0})
    man['total_cuboids'] = total
    md = os.path.join(os.path.dirname(os.path.abspath(pack).rstrip('/\\')), 'manifests')
    json.dump(man, open(os.path.join(md, model_id.replace('/', '__') + '.json'), 'w'), indent=2)
    print(f'[{model_id}] {len(R.parts)} parts, {total} cuboids, texture px {tex_sum:,}, k {k}' + (f', CLIPPED {clipped}' if clipped else ''))
    return man


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--pack', default=os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    a = ap.parse_args()
    emit(a.pack, colossus(), 7.0, 5.0)
    emit(a.pack, sentinel(), 5.0, 7.0)
