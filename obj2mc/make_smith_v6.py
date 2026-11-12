"""Abyssal Smith v6 and its double-faced forge hammer.

Run from this directory with Python. Geometry uses the boss frame (+Z front)
and the v5 joint pivots. The pixel painters bake directly into 128px atlases.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy.spatial import ConvexHull

import make_smith as M
import make_smith_v5 as V5
from make_smith_v3 import canvas, chain, leather
from obj2mc import FACE_VERTS
from obb import rot


# The original Smith uses warm volcanic stone against cold energy.
ARMOR = ((30, 25, 29), (55, 45, 46), (83, 70, 67), (112, 98, 90), (151, 134, 121))
TRIM_DARK = (37, 137, 152)
TRIM_LIGHT = (58, 217, 228)
BONE = ((44, 62, 75), (76, 99, 112), (116, 142, 153), (164, 184, 192))
REFLECT = np.array([-1, 1, -1])
HAMMER_X = 0.3
HAMMER_Y = 0.74


def face_pixels(center, size, rotation, face, width, height):
    """Sample texel centers in the same world frame across box faces and parts."""
    a, b, _, d = np.asarray(FACE_VERTS[face], float) - 0.5
    u = (np.arange(width) + 0.5)[None, :, None] / width
    v = (np.arange(height) + 0.5)[:, None, None] / height
    local = (a + (d - a) * u + (b - a) * v) * size * REFLECT
    if rotation:
        axis, angle = rotation
        local = local @ rot('xyz'.index(axis), angle).T
    return np.asarray(center) + local


def crack_distances(x, y, z):
    """Two intersecting fracture families shared by adjoining surfaces."""
    along = (z + 0.29 * x + 0.23 * y + 0.055 * np.sin(18 * x - 12 * y)) / 0.43
    across = (x - 0.36 * y - 0.22 * z + 0.05 * np.sin(19 * z + 9 * y)) / 0.37
    return np.minimum(abs(along - np.rint(along)), abs(across - np.rint(across)))


def heat_field(x, y, z):
    return np.clip((5.63 - y) / 1.96 + 0.09 * np.sin(17 * x + 11 * z + 6 * y), 0, 1)


def energy_tex(center, size, heated):
    def paint(face, w, h):
        x, y, z = np.moveaxis(face_pixels(center, size, None, face, w, h), -1, 0)
        img = canvas(w, h, (13, 54, 66))
        heat = heat_field(x, y, z) if heated else np.zeros_like(y)
        dist = crack_distances(x, y, z)
        img[heat > 0.50] = (105, 32, 18)
        img[heat > 0.80] = (128, 24, 16)
        img[dist < 0.20] = (23, 113, 138)
        warm = (heat > 0.82) | (heat > 0.47 + 0.26 * np.sin(9 * x - 7 * z + 4 * y))
        img[(dist < 0.24) & warm] = (207, 53, 24)
        img[dist < 0.082] = (72, 218, 234)
        img[(dist < 0.105) & warm] = (255, 158, 50)
        img[(dist < 0.105) & warm & (heat > 0.74)] = (255, 89, 43)
        return img
    return paint


def armor_tex(center, size, rotation, key, trim=True, heated=False):
    def paint(face, w, h):
        dark, shadow, mid, bevel, shine = ARMOR
        img = canvas(w, h, shadow if face in ('down', 'south') else mid)
        if w >= 5 and h >= 5:
            yy, xx = np.mgrid[:h, :w]
            # Broad angular planes read at distance, with a narrow raised bevel.
            u, v = xx / (w - 1), yy / (h - 1)
            rng = V5.rng_for(key, face)
            if key == ('chest', 3) and face == 'north':
                ridge = abs(u - 0.5)
                img[:] = mid
                img[(v < 0.39 - 0.66 * ridge) | (v < 0.68) & (u < 0.5 - 0.42 * v)] = bevel
                img[v > 0.90 - 0.55 * ridge] = shadow
                img[(u < 0.50) & (u > 0.44) & (v < 0.35)] = shine
            else:
                slope = 0.37 + 0.32 * rng.random()
                img[u + slope * v < 0.52] = bevel
                img[u + 0.72 * v > 1.40 - 0.22 * rng.random()] = shadow
            if min(w, h) >= 10:
                for _ in range(1 + w * h // 120):
                    x, y = rng.integers(3, w - 4), rng.integers(3, h - 3)
                    length = rng.integers(2, 6)
                    V5.line(img, x, y, min(x + length, w - 3), y - 1, shadow)
                    V5.line(img, x + 1, y + 1, min(x + length, w - 3), y, bevel)
                for x in (3, w - 4):
                    V5.px(img, x, 3, shine)
                    V5.px(img, x, 4, dark)
            if heated:
                x, y, z = np.moveaxis(face_pixels(center, size, rotation, face, w, h), -1, 0)
                heat = heat_field(x, y, z)
                dist = crack_distances(x, y, z)
                warm = heat > 0.49 + 0.24 * np.sin(9 * x - 7 * z + 4 * y)
                cracks = (dist < 0.12) & (heat > 0.14)
                img[cracks & ~warm] = (58, 209, 201)
                img[cracks & warm] = (255, 127, 50)
                img[cracks & warm & (heat > 0.79)] = (245, 59, 32)
        if trim:
            # Outline each piece even on its thickness faces.
            edge = 1 if min(w, h) >= 7 else 0
            img[edge, edge:w - edge] = TRIM_DARK
            img[h - 1 - edge, edge:w - edge] = TRIM_LIGHT
            img[edge:h - edge, edge] = TRIM_DARK
            img[edge:h - edge, w - 1 - edge] = TRIM_DARK
            if edge:
                img[h // 2:h - edge, edge] = TRIM_LIGHT
                img[h // 2:h - edge, w - 1 - edge] = TRIM_LIGHT
                img[0, :] = bevel
                img[-1, :] = dark
                img[:, 0] = shadow
                img[:, -1] = shadow
        return img
    return paint


# Bake the planes of a triangulated vault into the cuboid faces. Projecting
# from the vault center keeps the color facets coherent across box edges.
VAULT_CENTER = np.array([0, 8.13, -0.14])
VAULT_RINGS = ((7.91, 0.40, 0.47, 0.10), (8.15, 0.59, 0.64, 0.00),
               (8.40, 0.53, 0.59, 0.28), (8.60, 0.33, 0.41, 0.05))
VAULT_POINTS = [[0, 8.70, -0.17], [0, 7.86, -0.16]]
for y, radius_x, radius_z, turn in VAULT_RINGS:
    for index in range(8):
        angle = index * np.pi / 4 + turn
        z = np.cos(angle)
        VAULT_POINTS.append((np.sin(angle) * radius_x * (1 - 0.12 * z),
                             y + 0.032 * np.sin(3 * angle), z * radius_z - 0.14))
VAULT_PLANES = ConvexHull(np.asarray(VAULT_POINTS) - VAULT_CENTER).equations
VAULT_NORMALS = VAULT_PLANES[:, :3]
VAULT_LIGHT = np.array([-0.56, 0.61, 0.48])
VAULT_TONES = np.digitize(VAULT_NORMALS @ VAULT_LIGHT, [-0.37, 0.17, 0.58])


def bone_tex(center, size, rotation, key, tone, nasal=False, vault=False):
    def paint(face, w, h):
        tones = [BONE[max(0, min(3, tone + delta))] for delta in (-1, 0, 1)]
        if face == 'down':
            tones = [BONE[max(0, tone - 2)], tones[0], tones[1]]
        img = Image.new('RGB', (w, h), tones[1])
        draw = ImageDraw.Draw(img)
        if vault:
            points = face_pixels(center, size, rotation, face, w, h) - VAULT_CENTER
            facet = np.argmax(points @ VAULT_NORMALS.T / -VAULT_PLANES[:, 3], axis=-1)
            img = Image.fromarray(np.asarray(BONE, np.uint8)[VAULT_TONES[facet]])
            draw = ImageDraw.Draw(img)
            if face == 'north' and center[0] == 0 and center[2] > 0.15:
                draw.line([(w // 3, 1), (w // 3 + 2, h // 5), (w // 3, h // 3),
                           (w // 3 + 3, h * 5 // 12)], fill=BONE[0], width=1)
        elif min(w, h) >= 5:
            # Unequal facets through the cranium, orbit and jaw planes.
            ridge = (w * 3 // 5, h * 2 // 5)
            draw.polygon([(0, 0), (w - 1, 0), ridge, (w // 5, h * 2 // 3), (0, h // 2)], fill=tones[2])
            draw.polygon([ridge, (w - 1, h // 5), (w - 1, h - 1), (w // 3, h - 1)], fill=tones[0])
            if w >= 9 and h >= 6:
                r = V5.rng_for('bone', key, face)
                x = r.integers(2, w - 3)
                draw.line([(x, 1), (x - 1, h // 3), (x + 1, h // 2)], fill=BONE[0])
                draw.point((x + 2, h // 2), fill=BONE[3])
        if nasal and face == 'north':
            draw.polygon([(w // 2, 0), (0, h - 2), (w // 2, h - 1), (w - 1, h - 2)], fill=(13, 22, 30))
        return np.asarray(img)
    return paint


def socket_tex(face, w, h):
    img = canvas(w, h, (13, 26, 34))
    if face == 'north':
        # Light reflected off the lower cavity wall, with a dark overhang.
        yy, xx = np.mgrid[:h, :w]
        depth = yy / max(h - 1, 1) - 0.50 * abs(xx / max(w - 1, 1) - 0.45)
        img[depth > 0.40] = (18, 43, 52)
        img[depth > 0.63] = (24, 76, 86)
        img[depth > 0.77] = (36, 117, 124)
    return img


class Smith(V5.Smith):
    def __init__(self):
        super().__init__(None)

    def core(self, part, center, size):
        self._box(part, center, size, energy_tex(center, size, part in ('fore_r', 'hand_r')), None, light=15)

    def plate(self, part, center, size, rotation=None, *, trim=True):
        # Mirrored limbs use the same base palette and decoration seed.
        key = (part.removesuffix('_l').removesuffix('_r'), len(self.T[part]['boxes']))
        heated = part in ('fore_r', 'hand_r')
        painter = armor_tex(center, size, rotation, key, trim, heated)
        self._box(part, center, size, painter, rotation, light=7 if trim else 0)

    def chain(self, part, center, size, rotation=None):
        self.raw(part, center, size, chain, rotation)

    def apron(self, part, center, size, *, skirt=None):
        self.raw(part, center, size, leather if skirt is None else V5.leather_apron(skirt))

    def eye_socket(self, part, side):
        self._box(part, (side * 0.25, 7.91, 0.31), (0.40, 0.37, 0.09), socket_tex, None, light=9)

    def bone(self, part, center, size, rotation=None, *, tone=2, nasal=False, vault=False):
        key = len(self.T[part]['boxes'])
        self.raw(part, center, size, bone_tex(center, size, rotation, key, tone, nasal, vault), rotation)


def skull(b):
    p = b.part('head', 'chest', (0.0, 7.2, 0.0))

    def bone(center, size, rotation=None, **kwargs):
        b.bone(p, center, size, rotation, **kwargs)

    # Cranial vault: shallow roof, sloping forehead and widening rear.
    bone((0, 8.23, -0.16), (0.86, 0.55, 0.94), vault=True)
    bone((0, 8.53, -0.15), (0.62, 0.22, 0.77), vault=True)
    bone((0, 8.33, 0.26), (0.73, 0.50, 0.29), ('x', -22.5), vault=True)
    bone((0, 8.37, -0.48), (0.77, 0.38, 0.47), ('x', 22.5), vault=True)
    bone((0, 8.08, -0.47), (0.75, 0.34, 0.48), ('x', -22.5), vault=True)
    # Midface and angular jaw.
    bone((0, 7.86, 0.45), (0.13, 0.32, 0.23), ('x', -22.5))
    bone((0, 7.61, 0.50), (0.24, 0.24, 0.19), ('x', -22.5), tone=1, nasal=True)
    bone((0, 7.46, 0.47), (0.52, 0.15, 0.24), ('x', 22.5))
    bone((0, 7.23, 0.43), (0.42, 0.14, 0.26), ('x', 22.5), tone=1)
    b._box(p, (0, 7.36, 0.26), (0.42, 0.12, 0.16), socket_tex, None, light=6)
    for side in (-1, 1):
        bone((side * 0.31, 8.43, -0.17), (0.39, 0.25, 0.76), ('z', -side * 45), vault=True)
        bone((side * 0.42, 8.21, -0.19), (0.21, 0.40, 0.77), ('y', -side * 22.5), vault=True)
        bone((side * 0.34, 8.29, 0.18), (0.29, 0.38, 0.25), ('y', side * 45), vault=True)
        bone((side * 0.25, 8.10, 0.50), (0.44, 0.16, 0.28), ('z', side * 22.5), tone=2)
        bone((side * 0.45, 7.94, 0.34), (0.13, 0.34, 0.28), ('z', side * 22.5), tone=1)
        b.eye_socket(p, side)
        bone((side * 0.35, 7.68, 0.49), (0.36, 0.13, 0.28), ('z', side * 22.5))
        bone((side * 0.46, 7.71, 0.22), (0.17, 0.16, 0.55), ('y', side * 22.5), tone=1)
        bone((side * 0.28, 7.51, 0.20), (0.14, 0.27, 0.31), ('z', -side * 45), tone=0)
        bone((side * 0.31, 7.35, 0.13), (0.13, 0.39, 0.25), ('x', -22.5), tone=1)
        bone((side * 0.22, 7.26, 0.34), (0.17, 0.15, 0.34), ('y', -side * 22.5), tone=1)
        for i in range(3):
            bone((side * (0.052 + i * 0.10), 7.38 + 0.012 * i, 0.60 - 0.035 * i),
                 (0.08, 0.11 - 0.01 * i, 0.10), tone=2)
        bone((side * 0.086, 7.31, 0.54), (0.12, 0.06, 0.08), tone=1)


def torso_and_chest(b, *, flared_hips=True):
    p = b.part('torso', None, (0.0, 2.7, 0.0))
    b.core(p, (0, 3.58, 0.0), (1.66, 1.69, 1.03))
    b.chain(p, (0, 4.26, 0.09), (2.34, 0.24, 1.45))
    b.plate(p, (0, 4.28, 0.86), (0.46, 0.36, 0.14))
    for side in (-1, 1):
        b.plate(p, (side * 0.94, 3.61, 0.56), (0.56, 1.16, 0.26), ('y', side * 22.5))
        if flared_hips:
            b.plate(p, (side * 1.08, 3.35, 0.08), (0.34, 1.38, 0.88), ('z', side * 22.5))
        b.plate(p, (side * 0.72, 3.48, -0.58), (0.84, 1.18, 0.27), ('y', -side * 22.5))
    b.plate(p, (0, 3.94, -0.67), (0.65, 0.35, 0.27), ('x', 22.5))
    b.plate(p, (0, 3.36, -0.65), (0.48, 0.42, 0.23), ('x', -22.5))
    b.apron(p, (0, 2.82, 0.90), (1.22, 2.46, 0.10), skirt=True)
    b.apron(p, (0, 1.47, 0.90), (0.66, 0.26, 0.10))
    b.apron(p, (0, 1.28, 0.90), (0.30, 0.14, 0.10))

    p = b.part('chest', 'torso', (0.0, 4.5, 0.0))
    b.core(p, (0, 6.11, 0.0), (2.07, 1.61, 1.39))
    b.core(p, (0, 5.18, 0.02), (1.39, 0.75, 1.10))
    b.core(p, (0, 7.21, 0.0), (0.60, 0.44, 0.57))
    b.plate(p, (0, 6.28, 0.83), (1.91, 1.30, 0.44), ('x', -22.5))
    b.plate(p, (0, 5.38, 0.77), (1.52, 0.61, 0.38), ('x', 22.5))
    b.plate(p, (0, 6.29, -0.80), (1.76, 1.25, 0.32), ('x', 22.5))
    b.plate(p, (0, 5.28, -0.70), (1.32, 0.63, 0.31), ('x', -22.5))
    b.plate(p, (0, 7.05, 0.10), (0.97, 0.19, 0.94), trim=False)
    for side in (-1, 1):
        b.plate(p, (side * 1.07, 6.32, 0.58), (0.64, 1.22, 0.35), ('y', side * 45))
        b.chain(p, (side * 1.00, 6.36, 0.90), (0.12, 1.19, 0.13), ('z', -side * 22.5))
        b.plate(p, (side * 1.18, 6.31, -0.51), (0.56, 1.18, 0.31), ('y', -side * 45))
        b.plate(p, (side * 1.30, 5.98, 0.0), (0.29, 1.65, 0.67), ('z', -side * 22.5))
        b.plate(p, (side * 0.75, 6.99, 0.19), (0.76, 0.25, 1.25), ('z', -side * 22.5))
        b.plate(p, (side * 0.80, 5.28, 0.41), (0.45, 0.60, 0.26), ('y', side * 45))
        b.plate(p, (side * 0.75, 5.30, -0.41), (0.44, 0.59, 0.26), ('y', -side * 45))
    b.apron(p, (0, 4.97, 0.99), (1.13, 0.70, 0.10), skirt=False)


def limbs(b, *, outer_thighs=True):
    for side, suffix in ((1, 'l'), (-1, 'r')):
        p = b.part(f'arm_{suffix}', 'chest', (side * 2.0, 6.8, 0.0))
        b.core(p, (side * 2.02, 6.25, -0.06), (0.73, 1.88, 0.83))
        b.plate(p, (side * 2.07, 7.05, -0.07), (1.16, 0.43, 1.24), ('z', -side * 22.5))
        b.plate(p, (side * 2.57, 6.70, -0.06), (0.36, 0.88, 0.97), ('z', -side * 22.5))
        for front in (-1, 1):
            b.plate(p, (side * 1.99, 6.68, front * 0.56 - 0.06), (0.91, 0.77, 0.31), ('x', -front * 22.5))
            b.plate(p, (side * 2.44, 6.65, front * 0.47 - 0.06), (0.46, 0.75, 0.27), ('y', front * side * 45))
            b.plate(p, (side * 2.00, 5.93, front * 0.40 - 0.06), (0.65, 0.83, 0.24), ('y', front * side * 22.5))
        b.plate(p, (side * 2.37, 5.91, -0.06), (0.23, 0.72, 0.66), ('z', side * 22.5))

        p = b.part(f'fore_{suffix}', p, (side * 2.0278, 5.3, 0.0278))
        b.core(p, (side * 2.17, 4.90, 0.08), (0.71, 1.00, 0.69))
        b.plate(p, (side * 2.12, 5.29, -0.34), (0.49, 0.44, 0.26), ('z', side * 45))
        b.plate(p, (side * 2.53, 4.93, 0.08), (0.23, 0.83, 0.69), ('z', side * 22.5))
        b.plate(p, (side * 1.81, 4.93, 0.08), (0.18, 0.77, 0.58))
        for front in (-1, 1):
            b.plate(p, (side * 2.20, 4.90, 0.08 + front * 0.36), (0.62, 0.76, 0.23), ('y', front * side * 22.5))
        b.plate(p, (side * 2.24, 4.49, 0.12), (0.64, 0.13, 0.65), trim=False)

        p = b.part(f'hand_{suffix}', p, (side * 2.2917, 4.45, 0.2083))
        b.core(p, (side * 2.33, 3.87, 0.29), (0.59, 0.88, 0.68))
        b.plate(p, (side * 2.33, 4.28, 0.29), (0.70, 0.15, 0.77))
        b.plate(p, (side * 2.64, 3.88, 0.29), (0.24, 0.67, 0.87), ('z', side * 22.5))
        b.plate(p, (side * 2.02, 3.85, 0.29), (0.17, 0.58, 0.69), trim=False)
        b.plate(p, (side * 2.46, 3.49, 0.29), (0.63, 0.20, 0.88), ('z', -side * 22.5))
        for z in (-0.01, 0.19, 0.39, 0.59):
            b.plate(p, (side * 2.39, 3.26, z), (0.59, 0.22, 0.17), trim=False)
            b.plate(p, (side * 2.17, 3.10, z), (0.30, 0.19, 0.15), ('z', side * 45), trim=False)
        b.plate(p, (side * 2.02, 3.62, 0.71), (0.19, 0.42, 0.20), ('x', 22.5), trim=False)

    pivots = {'l': ((0.9, 2.6, 0.0), (1.0833, 1.55, -0.0833), (1.3056, 0.65, 0.1944)),
              'r': ((-0.9, 2.6, 0.0), (-1.1389, 1.55, -0.0833), (-1.3056, 0.65, 0.25))}
    for side, suffix in ((1, 'l'), (-1, 'r')):
        hip, knee, ankle = pivots[suffix]
        p = b.part(f'leg_{suffix}', None, hip)
        b.core(p, (side * 0.99, 2.09, 0.04), (0.78, 1.06, 0.75))
        if outer_thighs:
            b.plate(p, (side * 0.93, 2.10, 0.52), (0.98, 0.89, 0.26), ('x', -22.5))
            b.plate(p, (side * 1.38, 2.09, 0.08), (0.25, 0.83, 0.73), ('z', -side * 22.5))
            b.plate(p, (side * 1.02, 2.10, -0.39), (0.88, 0.85, 0.25), ('y', -side * 22.5))
        b.plate(p, (side * 0.62, 2.09, 0.03), (0.20, 0.70, 0.65), trim=False)

        p = b.part(f'shin_{suffix}', p, knee)
        b.core(p, (side * 1.20, 1.11, -0.02), (0.68, 0.94, 0.66))
        b.plate(p, (side * 1.20, 1.55, 0.43), (0.46, 0.46, 0.26), ('z', 45))
        b.plate(p, (side * 1.13, 1.02, 0.43), (0.69, 0.81, 0.32), ('x', 22.5))
        b.plate(p, (side * 1.40, 1.02, 0.38), (0.34, 0.77, 0.24), ('y', side * 45))
        b.plate(p, (side * 1.58, 1.04, 0.14), (0.25, 0.86, 0.60), ('y', side * 22.5))
        b.plate(p, (side * 1.24, 1.08, -0.42), (0.85, 0.80, 0.23), ('x', -22.5))
        b.plate(p, (side * 0.85, 1.06, 0.0), (0.20, 0.73, 0.68), trim=False)

        p = b.part(f'foot_{suffix}', p, ankle)
        b.core(p, (side * 1.34, 0.48, 0.10), (0.84, 0.36, 0.97))
        b.plate(p, (side * 1.34, 0.10, 0.22), (1.31, 0.20, 1.98), trim=False)
        b.plate(p, (side * 1.34, 0.37, -0.57), (0.98, 0.42, 0.45), ('x', -22.5))
        b.plate(p, (side * 1.34, 0.45, 0.40), (0.98, 0.25, 0.92), ('x', 22.5))
        b.plate(p, (side * 1.34, 0.28, 0.98), (1.12, 0.34, 0.49))
        for offset in (-1, 1):
            b.plate(p, (side * 1.34 + offset * 0.46, 0.31, 0.46), (0.22, 0.32, 0.71), ('y', offset * 22.5))
        for offset in (-0.35, 0, 0.35):
            b.plate(p, (side * 1.34 + offset, 0.19, 1.23), (0.26, 0.24, 0.18), trim=False)


def define_smith():
    b = Smith()
    torso_and_chest(b)
    skull(b)
    limbs(b)
    return b.T


# Sites in one quadrant, mirrored across the haft. Voronoi edges are shared
# by all faces of the head, including where a vein crosses a plate seam.
FORGE_SITES = np.array([
    (0.06, -0.69, 0.13), (0.46, -0.57, 0.45), (0.85, -0.37, 0.86),
    (0.53, -0.64, 1.34), (0.18, -0.51, 1.74), (0.38, -0.17, 1.06),
    (0.77, -0.06, 1.74), (0.17, 0.03, 0.52), (0.71, 0.06, 0.14),
    (0.03, 0.18, 1.50), (0.48, 0.44, 1.25), (0.72, 0.50, 0.68),
    (0.29, 0.62, 0.27), (0.30, 0.63, 1.76), (0.78, 0.55, 1.68),
])


def hammer_tex(center, size, rotation, kind):
    def paint(face, w, h):
        x, y, z = np.moveaxis(face_pixels(center, size, rotation, face, w, h), -1, 0)
        x, y, z = abs(x - HAMMER_X), y - HAMMER_Y, abs(z)
        warped = np.stack((x + 0.032 * np.sin(15 * z - 8 * y),
                           y + 0.043 * np.sin(12 * z + 14 * x),
                           z + 0.03 * np.sin(17 * y + 7 * x)), axis=-1)
        distance = np.linalg.norm(warped[..., None, :] - FORGE_SITES, axis=-1)
        nearest = np.partition(distance, 1, axis=-1)[..., :2]
        crack = nearest[..., 1] - nearest[..., 0]
        if kind == 'core':
            img = canvas(w, h, (151, 37, 16))
            img[crack < 0.18] = (222, 62, 16)
            img[crack < 0.10] = (255, 129, 27)
            img[crack < 0.042] = (255, 203, 91)
            return img
        iron = kind == 'iron'
        img = canvas(w, h, (99, 93, 96) if iron else (73, 55, 59))
        img[(x + z * 0.31 + 0.18 * y) % 0.53 < 0.18] = (62, 51, 57) if iron else (48, 38, 43)
        if face in ('north', 'south') and abs(center[2]) > 1.50:
            # Forge bevel on both equally broad, flat striking faces.
            rim = np.maximum.reduce([x / 0.75, abs(y) / 0.73, (x + abs(y)) / 1.23])
            img[rim > 0.80] = (116, 92, 85)
            img[rim > 0.94] = (66, 51, 55)
        elif min(w, h) >= 4:
            img[0, :] = (128, 112, 109)
            img[-1, :] = (47, 39, 45)
        if y.min() < 0.78:
            img[crack < 0.095] = (99, 37, 29)
            img[crack < 0.055] = (203, 63, 26)
            img[crack < 0.025] = (255, 149, 46)
        return img
    return paint


def define_hammer():
    b = Smith()
    p = b.part('hammer', None, (HAMMER_X, 2.9, 0.0))

    def box(x, y, z, size, rotation=None, kind='stone'):
        center = (HAMMER_X + x, y, z)
        painter = hammer_tex(center, size, rotation, kind)
        b._box(p, center, size, painter, rotation, light=15 if kind == 'core' else 5)

    box(0, HAMMER_Y, 0, (1.12, 1.09, 2.89), kind='core')
    for sign in (-1, 1):
        y = HAMMER_Y + sign * 0.55
        for z in (-0.52, 0.52):
            box(0, y, z, (1.16, 0.15, 0.86))
        for side in (-1, 1):
            box(side * 0.53, y, 0, (0.22, 0.22, 1.97), ('z', side * sign * 45), 'iron')
    for side in (-1, 1):
        for z in (-0.52, 0.52):
            box(side * 0.61, HAMMER_Y, z, (0.20, 0.92, 0.88))
    for end in (-1, 1):
        box(0, HAMMER_Y, end * 1.12, (1.41, 1.36, 0.24), kind='iron')
        box(0, HAMMER_Y, end * 1.36, (1.22, 1.21, 0.31))
        box(0, HAMMER_Y, end * 1.56, (1.50, 1.46, 0.25))
        for sx in (-1, 1):
            for sy in (-1, 1):
                box(sx * 0.64, HAMMER_Y + sy * 0.60, end * 1.54,
                    (0.22, 0.22, 0.31), ('z', sx * sy * 45), 'iron')
    # The haft runs through the core. Matching sockets show on both sides.
    b.raw(p, (HAMMER_X, 2.13, 0), (0.33, 3.81, 0.33), V5.shaft_wrap)
    for y in (0.13, 1.37):
        box(0, y, 0, (0.62, 0.22, 0.62), kind='iron')
    for y in (1.63, 2.16, 3.18):
        box(0, y, 0, (0.45, 0.13, 0.45), kind='iron')
    box(0, 3.87, 0, (0.56, 0.53, 0.56), kind='iron')
    box(0, 4.17, 0, (0.42, 0.11, 0.42))
    return b.T


if __name__ == '__main__':
    pack = Path(__file__).resolve().parents[1] / 'resourcepack' / 'hobbang-pack'
    credit = 'make_smith_v6 (HobbangEnhance)'
    M.emit_model_set(str(pack), 'boss/abyssal_smith', define_smith(), 9.0, 4.25,
                     credit=credit, part_ppu={'head': 32})
    M.emit_model_set(str(pack), 'boss/smith_hammer', define_hammer(), 4.2, 8.0,
                     attach={'boss': 'abyssal_smith', 'part': 'hammer', 'pivot_blocks': [-2.35, 3.5, 0.45]},
                     credit=credit)
