"""Abyssal Smith v3: sculpted from cuboids. A skull and layered armour around a core of blue energy.

Depth comes from geometry, not from painted detail: every body part is an inner ENERGY box (glowing cyan) wrapped in
separate armour plates that are tilted (one axis, 22.5 / 45 deg) and deliberately leave gaps - the sternum slit, the
spaces between ribs, pauldron tiers, joints, eye sockets - so the light shows from inside at every angle.
Proportions are the original mesh's: each part's envelope is the bbox measured by smith_analyze.py (table MESH below).
Part names, parents and joint pivots are the 1.5.x ones, so BossPoses.smith drives it unchanged.

  boss/abyssal_smith  15 parts        boss/smith_hammer  1 part (same grip pivot)        weapon model untouched
Run:  python make_smith_v3.py
"""
import os
import numpy as np
import make_smith as M
from make_smith import (STONE_VOID, STONE_DARK, STONE_BASE, STONE_MID, STONE_LIGHT, STONE_HI,
                        APRON_VOID, APRON_DARK, APRON_BASE, APRON_LIGHT, APRON_RUNE, APRON_HI,
                        CYAN_DEEP, CYAN_MID, CYAN_GLOW, CYAN_BRIGHT, CYAN_WHITE, CORE_VIOLET, CORE_MAGENTA,
                        BASALT_VOID, BASALT_DARK, BASALT_BASE, BASALT_EDGE,
                        MAGMA_DARK, MAGMA_CRIMSON, MAGMA_RED, MAGMA_ORANGE, MAGMA_GOLD, MAGMA_BRIGHT)

HERE = os.path.dirname(os.path.abspath(__file__))
SIDE = ('north', 'south', 'east', 'west')

# envelope of every part on the original mesh: centre, size (blocks): x/z 3rd..97th percentile of the part surface,
# y its cut planes (smith_analyze.py prints the bboxes these come from)
MESH = {
    'torso': ((0.00, 3.55, 0.13), (2.68, 1.89, 1.51)), 'chest': ((0.00, 5.77, -0.03), (2.83, 2.50, 1.98)),
    'head': ((0.00, 7.91, 0.13), (1.48, 1.20, 1.35)),
    'arm': ((1.98, 6.37, -0.25), (0.82, 2.13, 1.36)), 'fore': ((2.14, 4.87, 0.08), (1.03, 0.84, 1.05)),
    'hand': ((2.33, 3.69, 0.29), (1.01, 1.52, 1.08)),
    'leg': ((0.89, 2.07, 0.10), (1.44, 1.04, 1.56)), 'shin': ((1.20, 1.10, -0.06), (1.22, 0.90, 1.60)),
    'foot': ((1.36, 0.32, 0.19), (1.43, 0.64, 2.25)),
}


# ----------------------------------------------------------------------------- pixel tools (drawn shapes only)
def canvas(W, H, c):
    img = np.empty((H, W, 3), np.uint8); img[:] = c; return img


def px(img, x, y, c):
    H, W = img.shape[:2]
    if 0 <= x < W and 0 <= y < H: img[y, x] = c


def rect(img, x0, y0, x1, y1, c):
    H, W = img.shape[:2]
    x0, x1 = max(0, x0), min(W - 1, x1); y0, y1 = max(0, y0), min(H - 1, y1)
    if x1 >= x0 and y1 >= y0: img[y0:y1 + 1, x0:x1 + 1] = c


def rim(img, hi, lo):
    H, W = img.shape[:2]
    if W >= 3 and H >= 3:
        img[0, :] = hi; img[:, 0] = hi; img[-1, :] = lo; img[:, -1] = lo


def metal(pal, rivets=True, seam=False):
    """armour plate: flat tone by face direction, light rim top/left, dark rim bottom/right, rivets, optional seam"""
    void, dark, base, mid, light, hi = pal
    def f(fn, W, H):
        flat = {'up': mid, 'down': dark, 'south': dark}.get(fn, base)
        img = canvas(W, H, flat)
        rim(img, hi if fn == 'up' else light, void)
        if W >= 6 and H >= 6:
            rect(img, 1, 1, W - 2, 1, mid if flat is not mid else light)        # second highlight row: a rolled edge
            if seam and fn in SIDE and H >= 10:
                rect(img, 1, H // 2, W - 2, H // 2, void); rect(img, 1, H // 2 + 1, W - 2, H // 2 + 1, light)
            if rivets and fn in SIDE and W >= 8 and H >= 8:
                for rx, ry in ((2, 3), (W - 3, 3), (2, H - 3), (W - 3, H - 3)):
                    px(img, rx, ry, hi); px(img, rx, ry + 1, void)
        return img
    return f


IRONP = (STONE_VOID, STONE_DARK, STONE_BASE, STONE_MID, STONE_LIGHT, STONE_HI)
BONEP = (STONE_BASE, STONE_MID, STONE_LIGHT, STONE_HI, (150, 143, 140), (178, 171, 166))  # skull: the light end of the stone family
LEATHERP = (APRON_VOID, APRON_DARK, APRON_BASE, APRON_LIGHT, APRON_RUNE, APRON_HI)
BASALTP = (BASALT_VOID, BASALT_VOID, BASALT_DARK, BASALT_BASE, BASALT_EDGE, BASALT_EDGE)
HOTP = (MAGMA_DARK, MAGMA_DARK, MAGMA_CRIMSON, MAGMA_RED, MAGMA_ORANGE, MAGMA_GOLD)
iron = metal(IRONP); iron_s = metal(IRONP, seam=True); iron_plain = metal(IRONP, rivets=False)
bone = metal(BONEP, rivets=False); leather = metal(LEATHERP, rivets=False); basalt = metal(BASALTP, rivets=False)


def energy(pal=(CYAN_DEEP, CYAN_MID, CYAN_GLOW, CYAN_BRIGHT, CYAN_WHITE)):
    """the inside: deep base, bright currents running up it, white nodes. Brightest in the middle of each face."""
    deep, mid, glow_, bright, white = pal
    def f(fn, W, H):
        img = canvas(W, H, mid)
        if W >= 4 and H >= 4:
            rect(img, 0, 0, W - 1, 0, deep); rect(img, 0, H - 1, W - 1, H - 1, deep)
            rect(img, 0, 0, 0, H - 1, deep); rect(img, W - 1, 0, W - 1, H - 1, deep)
            rect(img, 2, 2, W - 3, H - 3, glow_)
        step = 4
        for i, x in enumerate(range(2, W - 2, step)):                 # currents: stepped vertical lines, alternate phase
            for y in range(1, H - 1):
                xx = x + ((y // 3 + i) % 2)
                px(img, xx, y, bright)
                if (y + 2 * i) % 6 == 0: px(img, xx, y, white)
        return img
    return f


blue = energy()
violet = energy((CORE_VIOLET, CORE_VIOLET, CORE_MAGENTA, CYAN_BRIGHT, CYAN_WHITE))
fire = energy((MAGMA_CRIMSON, MAGMA_RED, MAGMA_ORANGE, MAGMA_GOLD, MAGMA_BRIGHT))


def crystal(fn, W, H):
    img = canvas(W, H, CYAN_GLOW)
    if fn in SIDE:
        rect(img, 0, 0, W - 1, max(0, H // 3), CYAN_BRIGHT); rect(img, 0, 0, W - 1, 0, CYAN_WHITE)
        if W >= 3: rect(img, 0, 0, 0, H - 1, CYAN_BRIGHT); rect(img, W - 1, 0, W - 1, H - 1, CYAN_MID)
        rect(img, 0, H - 1, W - 1, H - 1, CYAN_DEEP)
    elif fn == 'up': img[:] = CYAN_WHITE
    else: img[:] = CYAN_DEEP
    return img


def teeth(fn, W, H):
    img = canvas(W, H, BONEP[3])
    rim(img, BONEP[5], BONEP[0])
    if fn in SIDE and W >= 4:
        for x in range(1, W - 1, 3): rect(img, x, 1, x, H - 1, STONE_VOID)
    return img


def socket(fn, W, H):
    """eye socket backing: black with a cyan ember (the eye)"""
    img = canvas(W, H, STONE_VOID)
    if fn == 'north' and W >= 4 and H >= 4:
        cx, cy = W // 2, H // 2
        rect(img, cx - 2, cy - 1, cx + 1, cy + 1, CYAN_MID); rect(img, cx - 1, cy - 1, cx, cy, CYAN_BRIGHT); px(img, cx - 1, cy - 1, CYAN_WHITE)
    return img


def apron(skirt):
    def f(fn, W, H):
        img = leather(fn, W, H)
        if fn == 'north' and W >= 10 and H >= 10:
            for x in range(3, W - 3):
                if x % 4 < 2: px(img, x, 2, APRON_HI); px(img, x, H - 3, APRON_RUNE)
            for y in range(3, H - 3):
                if y % 4 < 2: px(img, 2, y, APRON_HI); px(img, W - 3, y, APRON_RUNE)
            cx, cy = W // 2, H // 2
            r = min(W, H) // 4                                         # the original's diamond forge rune
            for d in range(r + 1):
                for sx, sy in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
                    px(img, cx + sx * d, cy + sy * (r - d), APRON_HI)
                    if r - d - 2 > 0: px(img, cx + sx * d, cy + sy * (r - d - 2), APRON_DARK)
            px(img, cx, cy, APRON_HI)
        return img
    return f


def chain(fn, W, H):
    img = canvas(W, H, STONE_LIGHT)
    if W >= H:
        for x in range(W):
            c = STONE_HI if x % 4 == 0 else STONE_VOID if x % 4 == 2 else STONE_LIGHT
            rect(img, x, 0, x, H - 1, c)
        rect(img, 0, H - 1, W - 1, H - 1, STONE_VOID)
    else:
        for y in range(H):
            c = STONE_HI if y % 4 == 0 else STONE_VOID if y % 4 == 2 else STONE_LIGHT
            rect(img, 0, y, W - 1, y, c)
    return img


def shaft(fn, W, H):
    img = canvas(W, H, APRON_DARK)
    if H > W:
        rect(img, 0, 0, 0, H - 1, APRON_LIGHT); rect(img, W - 1, 0, W - 1, H - 1, APRON_VOID)
        for y in range(0, H, 6): rect(img, 0, y, W - 1, y + 1, APRON_BASE); rect(img, 0, y + 2, W - 1, y + 2, APRON_VOID)
    else:
        rect(img, 0, 0, W - 1, 0, APRON_LIGHT); rect(img, 0, H - 1, W - 1, H - 1, APRON_VOID)
    return img


def _paint(kind, fn, W, H, color):
    return kind(fn, max(1, int(W)), max(1, int(H))) if callable(kind) else _old_paint(kind, fn, W, H, color)


_old_paint = M.paint_face
M.paint_face = _paint


def box(P, part, c, s, kind, rot=None):
    M.add_box(P, part, c, s, STONE_BASE, rot, kind)


# ----------------------------------------------------------------------------- the smith
def define_smith():
    P = M.new_part_table()
    add = M.add_part

    # ---------------- torso (root): energy pelvis, spine, tilted hip tassets, chain belt, leather apron
    (tx, ty, tz), (tw, th, td) = MESH['torso']
    add(P, 'torso', None, (0.0, 2.7, 0.0))
    box(P, 'torso', (0, ty, tz - 0.05), (tw * 0.62, th, td * 0.62), blue)                      # energy core
    box(P, 'torso', (0, ty - 0.1, tz - td / 2 + 0.12), (0.62, th * 0.95, 0.30), iron_s)        # lumbar spine
    for i in range(3):                                                                         # vertebra knobs
        box(P, 'torso', (0, 2.95 + i * 0.55, tz - td / 2 - 0.02), (0.34, 0.26, 0.20), bone, ('x', 22.5))
    box(P, 'torso', (0, 4.28, tz), (tw * 0.92, 0.34, td * 0.98), chain)                        # chain belt
    box(P, 'torso', (0, 4.20, tz + td / 2 + 0.02), (0.56, 0.50, 0.16), iron)                   # buckle
    for sx in (1, -1):
        box(P, 'torso', (sx * 1.02, 3.62, tz), (0.42, 1.25, td * 0.92), iron_s, ('z', sx * 22.5))      # outer tasset
        box(P, 'torso', (sx * 0.74, 3.30, tz + td / 2 - 0.12), (0.62, 1.05, 0.22), iron, ('y', sx * 22.5))   # front hip plate
        box(P, 'torso', (sx * 0.70, 3.40, tz - td / 2 + 0.16), (0.70, 1.15, 0.22), iron, ('y', -sx * 22.5))  # rear hip plate
        box(P, 'torso', (sx * 1.12, 2.86, tz), (0.36, 0.60, td * 0.70), iron_plain, ('z', sx * 45))    # flared lower tasset
    box(P, 'torso', (0, 2.72, tz + td / 2 + 0.10), (1.20, 2.55, 0.12), apron(True))            # apron skirt (to the knees)
    box(P, 'torso', (0, 1.36, tz + td / 2 + 0.10), (0.62, 0.34, 0.12), leather)                # its pointed tip
    box(P, 'torso', (0, 1.14, tz + td / 2 + 0.10), (0.26, 0.20, 0.12), leather)

    # ---------------- chest: energy heart behind a split breastplate, ribs with gaps, shoulder yoke, back plates
    (cx, cy, cz), (cw, ch, cd) = MESH['chest']
    add(P, 'chest', 'torso', (0.0, 4.5, 0.0))
    box(P, 'chest', (0, cy - 0.05, cz), (cw * 0.66, ch * 0.92, cd * 0.62), blue)               # energy core
    box(P, 'chest', (0, 6.30, cz + cd * 0.31 + 0.02), (0.74, 1.10, 0.10), violet)              # the V heart, behind the slit
    for sx in (1, -1):
        # breastplate halves: turned outward about y so the sternum gap is a real V-groove
        box(P, 'chest', (sx * 0.84, 6.32, cz + cd / 2 - 0.20), (0.98, 1.20, 0.30), iron, ('y', -sx * 22.5))
        box(P, 'chest', (sx * 0.42, 6.86, cz + cd / 2 - 0.10), (0.70, 0.30, 0.34), iron_plain, ('z', sx * 22.5))   # collar bone
        for i in range(3):                                                                     # ribs: bars with gaps
            y = 5.50 - i * 0.36
            box(P, 'chest', (sx * (0.62 - i * 0.05), y, cz + cd / 2 - 0.22), (0.95 - i * 0.12, 0.20, 0.26), bone, ('z', sx * 22.5))
            box(P, 'chest', (sx * (cw / 2 - 0.20), y + 0.08, cz), (0.24, 0.20, cd * 0.86), bone)        # rib wrapping the flank
        box(P, 'chest', (sx * (cw / 2 - 0.16), 6.35, cz), (0.30, 1.15, cd * 0.90), iron_s)              # flank plate
        box(P, 'chest', (sx * 0.98, 7.02, cz - 0.02), (0.95, 0.36, cd * 0.86), iron, ('z', -sx * 22.5))  # shoulder yoke
        box(P, 'chest', (sx * 0.72, 6.25, cz - cd / 2 + 0.16), (1.05, 1.45, 0.30), iron_s, ('y', sx * 22.5))   # shoulder blade
        box(P, 'chest', (sx * 0.66, 5.10, cz - cd / 2 + 0.20), (0.90, 0.75, 0.24), iron, ('y', sx * 22.5))     # lower back plate
        box(P, 'chest', (sx * 0.30, 5.60, cz + cd / 2 + 0.06), (0.14, 1.70, 0.14), chain)               # apron suspender chain
        box(P, 'chest', (sx * 1.16, 7.40, cz - 0.05), (0.22, 0.62, 0.26), crystal, ('z', -sx * 22.5))   # trapezius crystal
    for i in range(5):                                                                          # spine up the back, light in the gaps
        box(P, 'chest', (0, 4.80 + i * 0.50, cz - cd / 2 + 0.02), (0.40, 0.34, 0.30), bone, ('x', 22.5))
    box(P, 'chest', (0, 7.10, cz + 0.05), (0.72, 0.30, 0.72), iron_plain)                       # neck ring
    box(P, 'chest', (0, 5.00, cz + cd / 2 + 0.10), (1.25, 1.10, 0.12), apron(False))            # apron bib with the rune

    # ---------------- head: a skull around the light
    (hx, hy, hz), (hw, hh, hd) = MESH['head']
    add(P, 'head', 'chest', (0.0, 7.2, 0.0))
    Z = hz + hd / 2                                                                             # face plane
    box(P, 'head', (0, 7.90, hz - 0.02), (hw * 0.62, hh * 0.72, hd * 0.66), blue)               # light inside the skull
    box(P, 'head', (0, 8.34, hz - 0.06), (hw * 0.92, 0.42, hd * 0.96), bone)                    # cranium dome, lower tier
    box(P, 'head', (0, 8.60, hz - 0.10), (hw * 0.70, 0.22, hd * 0.74), bone)                    # cranium dome, top tier
    box(P, 'head', (0, 8.16, Z - 0.10), (hw * 0.96, 0.24, 0.30), bone, ('x', 22.5))             # brow ridge, overhanging
    box(P, 'head', (0, 7.86, Z - 0.02), (0.22, 0.46, 0.20), bone, ('x', -22.5))                 # nasal bone
    box(P, 'head', (0, 7.96, Z - 0.30), (hw * 0.78, 0.36, 0.08), socket)                        # socket backing (eyes glow)
    for sx in (1, -1):
        box(P, 'head', (sx * 0.50, 7.70, Z - 0.12), (0.46, 0.22, 0.30), bone, ('z', sx * 22.5)) # cheekbone, raked
        box(P, 'head', (sx * (hw / 2 - 0.09), 7.96, hz + 0.05), (0.18, 0.70, hd * 0.70), bone)  # temple wall
        box(P, 'head', (sx * (hw / 2 - 0.12), 7.52, hz + 0.16), (0.20, 0.50, 0.46), bone, ('x', -22.5))   # jaw hinge
        box(P, 'head', (sx * 0.56, 8.72, hz - 0.05), (0.20, 0.62, 0.26), crystal, ('z', -sx * 22.5))      # horn crystals
    box(P, 'head', (0, 8.90, hz - 0.02), (0.22, 0.60, 0.28), crystal)                           # crown crystal
    box(P, 'head', (0, 7.56, Z - 0.08), (0.86, 0.16, 0.26), teeth)                              # upper teeth
    box(P, 'head', (0, 7.34, Z - 0.14), (0.78, 0.22, 0.36), bone, ('x', 22.5))                  # lower jaw, hanging open
    box(P, 'head', (0, 7.43, Z - 0.06), (0.70, 0.10, 0.20), teeth)                              # lower teeth
    box(P, 'head', (0, 7.86, hz - hd / 2 + 0.06), (hw * 0.80, 0.80, 0.16), bone)                # occiput

    # ---------------- arms
    (ax, ay, az), (aw, ah, ad) = MESH['arm']
    (fx, fy, fz), (fw, fh, fd) = MESH['fore']
    (gx, gy, gz), (gw, gh, gd) = MESH['hand']
    for sx, q in ((1, 'l'), (-1, 'r')):
        hot = q == 'r'
        glow_ = fire if hot else blue
        a = add(P, f'arm_{q}', 'chest', (sx * 2.0, 6.8, 0.0))
        box(P, a, (sx * ax, ay - 0.25, az + 0.20), (aw * 0.55, ah * 0.74, ad * 0.50), blue)             # energy humerus
        for i, (dy, w_, d_) in enumerate(((0.62, 1.30, 1.50), (0.22, 1.16, 1.36), (-0.16, 1.02, 1.22))):   # 3 pauldron tiers
            box(P, a, (sx * (ax + 0.16 + i * 0.07), ay + dy, az + 0.14), (w_, 0.30, d_), iron_s, ('z', -sx * 22.5))
        box(P, a, (sx * (ax + 0.02), 7.36, az + 0.14), (0.26, 0.60, 0.30), crystal, ('z', -sx * 22.5))   # shoulder crystal
        box(P, a, (sx * (ax + aw / 2 - 0.02), 5.80, az + 0.20), (0.18, 0.90, ad * 0.62), iron)           # outer arm plate
        box(P, a, (sx * ax, 5.80, az + 0.20 + ad * 0.30), (aw * 0.56, 0.80, 0.16), iron_plain)           # front plate
        box(P, a, (sx * ax, 5.80, az + 0.20 - ad * 0.30), (aw * 0.56, 0.80, 0.16), iron_plain)           # back plate
        fo = add(P, f'fore_{q}', a, (sx * 2.0278, 5.3, 0.0278))
        box(P, fo, (sx * fx, fy, fz), (fw * 0.56, fh * 1.05, fd * 0.56), glow_)                          # energy forearm
        box(P, fo, (sx * (fx - 0.02), 5.24, fz - 0.08), (0.62, 0.40, 0.62), iron, ('x', 45))             # elbow cop (diamond)
        box(P, fo, (sx * (fx + fw / 2 - 0.06), fy, fz), (0.20, fh * 0.92, fd * 0.96), metal(HOTP if hot else IRONP), ('z', -sx * 22.5))  # flared bracer
        box(P, fo, (sx * fx, fy - 0.04, fz + fd / 2 - 0.08), (fw * 0.80, fh * 0.70, 0.16), metal(HOTP if hot else IRONP, rivets=False))
        box(P, fo, (sx * fx, fy - 0.04, fz - fd / 2 + 0.08), (fw * 0.80, fh * 0.70, 0.16), iron_plain)
        ha = add(P, f'hand_{q}', fo, (sx * 2.2917, 4.45, 0.2083))
        hp = metal(HOTP, rivets=False) if hot else iron_plain
        box(P, ha, (sx * gx, 4.25, gz), (gw * 0.60, 0.40, gd * 0.60), glow_)                             # wrist, glowing
        box(P, ha, (sx * gx, 3.72, gz), (gw * 1.00, 0.78, gd * 0.94), hp)                                # palm block
        box(P, ha, (sx * (gx + 0.10), 3.98, gz), (gw * 1.06, 0.26, gd * 1.02), metal(HOTP if hot else IRONP), ('z', -sx * 22.5))  # knuckle guard
        for i, dz in enumerate((-0.36, -0.12, 0.12, 0.36)):                                              # four fingers, curled
            box(P, ha, (sx * gx, 3.22, gz + dz), (gw * 0.88, 0.40, 0.20), hp, ('z', sx * 22.5))
        box(P, ha, (sx * (gx - gw / 2 + 0.02), 3.60, gz + 0.42), (0.26, 0.56, 0.28), hp, ('x', 22.5))    # thumb

    # ---------------- legs
    (lx, ly, lz), (lw, lh, ld) = MESH['leg']
    (sx_, sy_, sz_), (sw, shh, sd) = MESH['shin']
    (ox, oy, oz), (ow, oh, od) = MESH['foot']
    leg_pivs = {
        'l': ((0.9, 2.6, 0.0), (1.0833, 1.55, -0.0833), (1.3056, 0.65, 0.1944)),
        'r': ((-0.9, 2.6, 0.0), (-1.1389, 1.55, -0.0833), (-1.3056, 0.65, 0.25)),
    }
    for sx, q in ((1, 'l'), (-1, 'r')):
        lp, sp, fp = leg_pivs[q]
        lg = add(P, f'leg_{q}', None, lp)
        box(P, lg, (sx * lx, ly, lz), (lw * 0.52, lh * 1.05, ld * 0.52), blue)                           # energy femur
        box(P, lg, (sx * lx, ly + 0.04, lz + ld / 2 - 0.10), (lw * 0.78, lh * 0.86, 0.20), iron_s, ('x', -22.5))   # cuisse, raked
        box(P, lg, (sx * (lx + lw / 2 - 0.10), ly, lz), (0.20, lh * 0.90, ld * 0.84), iron, ('z', -sx * 22.5))     # outer thigh
        box(P, lg, (sx * lx, ly, lz - ld / 2 + 0.10), (lw * 0.74, lh * 0.80, 0.18), iron_plain)
        sh = add(P, f'shin_{q}', lg, sp)
        box(P, sh, (sx * sx_, sy_, sz_), (sw * 0.52, shh * 1.05, sd * 0.50), blue)                       # energy tibia
        box(P, sh, (sx * sx_, 1.52, sz_ + sd / 2 - 0.16), (0.70, 0.44, 0.44), iron, ('x', 45))           # knee cop (diamond)
        box(P, sh, (sx * sx_, sy_ - 0.06, sz_ + sd / 2 - 0.22), (sw * 0.82, shh * 0.84, 0.22), iron_s, ('x', 22.5))   # greave
        box(P, sh, (sx * (sx_ + sw / 2 - 0.10), sy_, sz_), (0.20, shh * 0.92, sd * 0.80), iron, ('z', -sx * 22.5))
        box(P, sh, (sx * sx_, sy_, sz_ - sd / 2 + 0.14), (sw * 0.78, shh * 0.88, 0.20), iron_plain, ('x', -22.5))     # calf plate
        ft = add(P, f'foot_{q}', sh, fp)
        box(P, ft, (sx * ox, 0.42, oz - 0.30), (ow * 0.50, 0.44, od * 0.36), blue)                       # ankle glow
        box(P, ft, (sx * ox, 0.16, oz), (ow, 0.32, od * 0.86), iron_plain)                               # sole
        box(P, ft, (sx * ox, 0.44, oz - od * 0.30), (ow * 0.92, 0.40, od * 0.30), iron)                  # heel cup
        box(P, ft, (sx * ox, 0.46, oz + 0.30), (ow * 0.90, 0.30, od * 0.40), iron_s, ('x', 22.5))        # instep plate
        for dx in (-0.46, 0.0, 0.46):                                                                    # three toes
            box(P, ft, (sx * ox + dx, 0.22, oz + od / 2 - 0.16), (0.40, 0.44, 0.50), iron_plain)
    return P


def define_hammer():
    """mesh envelope: head x -0.70..0.62, y 0..1.2, z -1.10..1.08; haft x 0.09..0.45; pommel y 3.3..4.2 (grip pivot 0.3, 2.9, 0).
    A molten core seen through the slots between basalt plates."""
    P = M.new_part_table()
    M.add_part(P, 'hammer', None, (0.3, 2.9, 0.0))
    X, Y = -0.04, 0.60
    box(P, 'hammer', (X, Y, 0), (1.00, 0.92, 1.80), fire)                                       # molten core
    for sy in (1, -1):                                                                          # top / bottom slabs
        box(P, 'hammer', (X, Y + sy * 0.50, 0), (1.20, 0.20, 1.70), basalt)
        box(P, 'hammer', (X, Y + sy * 0.30, 0), (1.14, 0.10, 0.30), basalt)                    # centre strap
    for sx in (1, -1):                                                                          # side slabs: two per side, a slot between
        for sz in (1, -1):
            box(P, 'hammer', (X + sx * 0.58, Y, sz * 0.50), (0.18, 0.84, 0.52), basalt)
    for sz in (1, -1):
        box(P, 'hammer', (X, Y, sz * 0.96), (1.32, 1.20, 0.28), metal(BASALTP, rivets=True))    # striking caps
        box(P, 'hammer', (X, Y, sz * 1.12), (0.80, 0.70, 0.10), fire)                           # hot face
        box(P, 'hammer', (X, Y, sz * 0.74), (1.38, 1.26, 0.12), iron_plain)                     # band
    box(P, 'hammer', (0.26, 1.34, 0), (0.56, 0.30, 0.50), iron)                                 # socket
    box(P, 'hammer', (0.27, 2.36, 0), (0.30, 1.90, 0.30), shaft)                                # haft
    for y in (1.84, 2.44, 3.04):
        box(P, 'hammer', (0.27, y, 0), (0.42, 0.14, 0.42), iron_plain)                          # grip rings
    box(P, 'hammer', (0.42, 3.74, 0), (0.62, 0.80, 0.56), basalt)                               # pommel
    box(P, 'hammer', (0.42, 3.74, 0), (0.44, 0.50, 0.66), fire)                                 # its core showing at the sides
    return P


if __name__ == '__main__':
    pack = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    credit = 'make_smith_v3 (HobbangEnhance)'
    M.emit_model_set(pack, 'boss/abyssal_smith', define_smith(), height_blocks=9.0, k_units_per_block=4.25, credit=credit)
    M.emit_model_set(pack, 'boss/smith_hammer', define_hammer(), height_blocks=4.2, k_units_per_block=8.0,
                     attach={'boss': 'abyssal_smith', 'part': 'hammer', 'pivot_blocks': [-2.35, 3.5, 0.45]}, credit=credit)
