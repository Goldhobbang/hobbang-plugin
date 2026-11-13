"""Emit the v7 Abyssal Smith body from the v6 rig and the original body OBJ/PNG."""
from pathlib import Path

import numpy as np
from scipy.ndimage import map_coordinates
from scipy.spatial import cKDTree

import make_smith as M
import make_smith_v6 as V6
import smith_analyze as source
from make_smith_v3 import canvas
from obb import rot
from obj2mc import FACE_NORMAL


STONE_LEVELS = (7, 24, 45, 64, 78, 87, 93, 97, 99)
LEATHER_LEVELS = (8, 23, 42, 65, 84, 96)
ROOT = Path(__file__).resolve().parents[1]


def tonal_steps(palette, value):
    return palette[np.clip(value * len(palette), 0, len(palette) - 1).astype(int)]


class MaterialSamples:
    """Project original texels near the box face and retain the source palette."""

    def __init__(self, points, colors, normals, levels):
        self.tree = cKDTree(np.column_stack((points, normals * 0.16)))
        self.lightness = colors @ [0.25, 0.55, 0.20]
        self.palette = np.clip(np.percentile(colors, levels, axis=0) * 1.10 + 7, 0, 255).astype(np.uint8)

    def grain(self, points, normal):
        samples = np.concatenate((points, np.broadcast_to(normal * 0.16, points.shape)), axis=-1)
        distances, indices = self.tree.query(samples, k=4)
        weights = (distances + 0.04)**-3
        brightness = np.average(self.lightness[indices], weights=weights, axis=-1)
        return brightness, np.clip(1.14 - 2.0 * distances[..., 0], 0, 1)


class SmithMaterials:
    def __init__(self):
        _, _, _, _, _, points, colors, normals = source.load('abyssal_smith', 0.055)
        features = source.feature_masks(colors)
        x, y, z = points.T
        leather = ((abs(x) < 0.66) & (z > 0.57) & (y > 1.25)
                   & ((y < 4.22) | ((y > 4.66) & (y < 5.65))) & features['brown'])
        stone = (np.ptp(colors, axis=1) < 39) & (colors.max(axis=1) > 19) & ~leather
        self.stone = MaterialSamples(points[stone], colors[stone], normals[stone], STONE_LEVELS)
        self.leather = MaterialSamples(points[leather], colors[leather], normals[leather], LEATHER_LEVELS)
        self.stone.palette = ((self.stone.palette.astype(float) * 0.85
                               + self.stone.palette[:, :1] * 0.15) * [1.04, 1.00, 0.96]).astype(np.uint8)
        self.cyan = cKDTree(points[features['cyan']])
        self.cyan_colors = np.percentile(colors[features['cyan']], [28, 66, 95], axis=0).astype(np.uint8)

        rng = np.random.default_rng(14207)
        self.grain = rng.uniform(-1, 1, (53, 59, 47))
        nodes = np.stack(np.meshgrid(np.arange(-4, 4.6, 0.42), np.arange(-1, 10, 0.42),
                                    np.arange(-2, 2.6, 0.42), indexing='ij'), axis=-1).reshape(-1, 3)
        self.sites = nodes + rng.uniform(-0.15, 0.15, nodes.shape)
        self.seams = cKDTree(self.sites)

    def noise(self, points, frequency, phase=0):
        coords = np.moveaxis(points * frequency + 23 + phase, -1, 0)
        return map_coordinates(self.grain, coords, order=1, mode='grid-wrap')

    def fractures(self, points):
        x, y, z = np.moveaxis(points, -1, 0)
        warped = points + 0.035 * np.stack((np.sin(15 * z + 12 * y), np.sin(16 * x - 9 * z),
                                            np.sin(13 * y + 10 * x)), axis=-1)
        distances, sites = self.seams.query(warped, k=2)
        a, b = sites[..., 0], sites[..., 1]
        separation = np.linalg.norm(self.sites[a] - self.sites[b], axis=-1)
        depth = (distances[..., 1]**2 - distances[..., 0]**2) / (2 * separation)
        pair = np.minimum(a, b) * 53 + np.maximum(a, b) * 97
        return depth, pair % 13

    def armor(self, part, center, size, rotation, seams):
        mirrored = part.endswith('_r')
        heated = part in ('fore_r', 'hand_r')

        def paint(face, w, h):
            points = V6.face_pixels(center, size, rotation, face, w, h)
            normal = np.array(FACE_NORMAL[face]) * V6.REFLECT
            if rotation:
                axis, angle = rotation
                normal = rot('xyz'.index(axis), angle) @ normal
            if mirrored:
                points = points * [-1, 1, 1]
                normal = normal * [-1, 1, 1]
            yy, xx = np.mgrid[:h, :w]
            edge = np.minimum.reduce((xx, yy, w - 1 - xx, h - 1 - yy))
            coarse = self.noise(points, 4.8)
            strata = self.noise(points * [0.57, 1.48, 0.72], 9.5, 11)
            chips = self.noise(points, 13.5, 17)
            grit = self.noise(points, 27, 31)
            source_light, weight = self.stone.grain(points, normal)
            source_relief = np.clip((source_light - 53) / 80, -0.34, 0.70) * weight
            light = 0.58 + 0.26 * coarse + 0.16 * strata + 0.12 * chips + 0.08 * grit + 0.44 * source_relief
            # Small chipped ledges catch the light above pitted grooves.
            ledge = self.noise(points + [0, 0.040, 0], 13.5, 17) - chips
            light += 0.14 * np.maximum(ledge, 0) - 0.18 * np.maximum(-ledge, 0)
            light -= 0.12 * (face == 'down')
            if min(w, h) >= 4:
                bevel = (yy < 2 + (strata > 0.18)) & (edge > 0)
                light[bevel] += 0.12 + 0.15 * grit[bevel]
                light[edge == 0] -= 0.14
                light[(yy >= h - 3) & (edge == 1)] -= 0.08

            depth, segment = self.fractures(points)
            light[(depth < 0.026) & (segment < 10)] -= 0.22
            light[(depth > 0.026) & (depth < 0.058) & (ledge > 0.05)] += 0.11
            img = tonal_steps(self.stone.palette, light)

            if seams and min(w, h) >= 4:
                source_vein = self.cyan.query(points)[0] < 0.28
                lit = ((segment < 2) | ((segment < 6) & source_vein)
                       | ((edge < 2) & (segment < 8))) & (coarse + 0.6 * chips > -0.38)
                halo = (depth < 0.065) & lit
                vein = (depth < 0.032) & lit
                filament = vein & ((depth < 0.011) | (grit + chips > 0.30))
                img[halo] = ((img[halo].astype(float) + [15, 75, 99]) * 0.5).astype(np.uint8)
                img[vein] = self.cyan_colors[0]
                img[filament] = self.cyan_colors[1]
                img[filament & (chips > 0.41)] = self.cyan_colors[2]
                if heated:
                    x, y, z = np.moveaxis(points, -1, 0)
                    heat = V6.heat_field(x, y, z)
                    warm = heat > 0.46 + 0.25 * np.sin(9 * x - 7 * z + 4 * y)
                    img[halo & warm] = (116, 42, 26)
                    img[vein & warm] = (236, 111, 44)
                    img[vein & warm & (heat > 0.78)] = (227, 59, 30)
                    img[filament & warm] = (255, 155, 67)
            return img
        return paint

    def apron(self, center, size, skirt):
        def paint(face, w, h):
            points = V6.face_pixels(center, size, None, face, w, h)
            normal = np.array(FACE_NORMAL[face]) * V6.REFLECT
            source_light, weight = self.leather.grain(points, normal)
            yy, xx = np.mgrid[:h, :w]
            grain = self.noise(points, 17, 8)
            u, v = (xx + 0.5) / w, (yy + 0.5) / h
            fold = self.noise(points * [1.5, 0.29, 1], 8.5, 19)
            edge = np.minimum.reduce((xx, yy, w - 1 - xx, h - 1 - yy))
            fold_shadow = self.noise((points + [0.034, 0, 0]) * [1.5, 0.29, 1], 8.5, 19) - fold
            light = 0.43 + 0.36 * fold + 0.13 * grain + 0.33 * fold_shadow
            light += 0.25 * np.clip((source_light - 70) / 42, -0.6, 0.9) * weight
            light -= np.maximum(0, 3 - edge) * 0.16
            light += 0.14 * ((grain > 0.48) & (edge < 4))
            img = tonal_steps(self.leather.palette, light)
            if face == 'north' and min(w, h) >= 10:
                stitch = (((xx == 2) | (xx == w - 3)) & ((yy + (xx == 2)) % 5 < 2)
                          | ((yy == 3) | (yy == h - 3)) & (xx % 5 < 2)) & (edge >= 2)
                img[stitch & (grain > -0.41)] = (133, 105, 81)
                # Rubbed diamond stamp from the original apron, with missing pigment at folds.
                diamond = abs((u - 0.50) * w) + abs((v - 0.47) * h) * 0.83
                radius = min(w, h) * (0.24 if skirt else 0.28)
                rim = abs(diamond - radius - 0.75 * grain)
                stamp = (rim < 0.93) | (diamond < 1.65)
                img[(rim < 2.0) & (grain > -0.36)] = (69, 45, 34)
                img[stamp & (grain > -0.32)] = (157, 128, 101)
                img[stamp & (grain > 0.31)] = (181, 150, 117)
            return img
        return paint

    def chain(self, belt, face, w, h):
        # Alternate broad oval links with side-on connectors and paint dark apertures.
        swapped = not belt and h > w
        length, width = (h, w) if swapped else (w, h)
        yy, xx = np.mgrid[:width, :length]
        x = (xx % 8 - 3.5) / 3.5
        radius = max(1.2, min(width * 0.5, 3.4))
        offset = yy - (width - 1) * 0.5
        if belt and face in ('up', 'down'):
            offset = np.minimum(yy, width - 1 - yy) - 1.5
        ellipse = x*x + (offset / radius)**2
        rim = (ellipse < 1.27) & (ellipse > 0.45)
        connector = (abs(offset - 0.5) < 0.85) & (abs(x) > 0.41)
        img = canvas(length, width, (38, 30, 31))
        img[rim & (offset > 0)] = self.stone.palette[4]
        img[rim & (offset <= 0)] = self.stone.palette[6]
        img[connector] = self.stone.palette[5]
        img[(rim | connector) & ((xx + 3 * yy) % 13 == 0)] = self.stone.palette[3]
        return img.transpose(1, 0, 2) if swapped else img


def orbit_light(face, w, h):
    img = canvas(w, h, (13, 22, 28))
    if face == 'north':
        yy, xx = np.mgrid[:h, :w]
        depth = (yy + 0.20 * xx) / h
        img[depth > 0.44] = (19, 43, 51)
        img[depth > 0.56] = (25, 82, 97)
        img[depth > 0.68] = (39, 135, 152)
        img[depth > 0.79] = (59, 177, 190)
    return img


def orbit_floor(face, w, h):
    img = canvas(w, h, V6.BONE[0])
    if face == 'up':
        yy, xx = np.mgrid[:h, :w]
        rear = (yy + 0.20 * xx) / h
        img[rear > 0.46] = (40, 79, 90)
        img[rear > 0.77] = (45, 105, 116)
    return img


class Smith(V6.Smith):
    def __init__(self, materials):
        super().__init__()
        self.materials = materials

    def plate(self, part, center, size, rotation=None, *, trim=True):
        painter = self.materials.armor(part, center, size, rotation, seams=trim)
        self._box(part, center, size, painter, rotation, light=6 if trim else 0)

    def apron(self, part, center, size, *, skirt=None):
        self.raw(part, center, size, self.materials.apron(center, size, skirt))

    def chain(self, part, center, size, rotation=None):
        def paint(face, w, h):
            return self.materials.chain(part == 'torso', face, w, h)
        self.raw(part, center, size, paint, rotation)

    def eye_socket(self, part, side):
        self._box(part, (side * 0.239, 7.884, 0.245), (0.356, 0.322, 0.08),
                  orbit_light, None, light=11)


def orbital_walls(smith):
    for side in (-1, 1):
        # The existing brow and zygomatic rim sit ahead of this chamfered inner wall.
        smith.bone('head', (side * 0.393, 7.902, 0.399), (0.064, 0.233, 0.176),
                   ('y', side * 45), tone=1)
        smith.bone('head', (side * 0.104, 7.904, 0.388), (0.052, 0.205, 0.172),
                   ('y', -side * 45), tone=1)
        smith.bone('head', (side * 0.246, 8.006, 0.398), (0.254, 0.065, 0.192),
                   ('x', -22.5), tone=0)
        smith.raw('head', (side * 0.253, 7.773, 0.393), (0.262, 0.051, 0.205),
                  orbit_floor, ('x', 22.5))
        smith.bone('head', (side * 0.290, 7.779, 0.309), (0.193, 0.038, 0.096),
                   ('z', side * 22.5), tone=0)


def build_smith(builder=Smith):
    smith = builder(SmithMaterials())
    V6.torso_and_chest(smith, flared_hips=False)
    V6.skull(smith)
    orbital_walls(smith)
    V6.limbs(smith, outer_thighs=False)
    return smith


def define_smith():
    return build_smith().T


def emit_body(body, *, height_blocks=9.0):
    density = {part: 32 if part in ('head', 'torso', 'chest') else 24 for part in body}
    M.emit_model_set(str(ROOT / 'resourcepack' / 'hobbang-pack'), 'boss/abyssal_smith', body, height_blocks, 4.25,
                     credit='make_smith_v7 (HobbangEnhance)', part_ppu=density)


if __name__ == '__main__':
    emit_body(define_smith())
