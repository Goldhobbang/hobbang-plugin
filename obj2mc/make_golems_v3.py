"""Author the two golem rigs with 16 texels per block and Minecraft cuboid elements."""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from make_golems import BR, CR, DARK, GOLD, HANDS, PATINA, SAND, SHINE, TEAL, TEAL_DIM, hx
from obj2mc import FACE_VERTS, shelf_pack

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / 'resourcepack' / 'hobbang-pack'
SIDES = ('north', 'west', 'south', 'east')  # UV direction wraps around the four walls
MODEL_SIGN = np.array([-1, 1, -1])
STONE = tuple(map(hx, (
    '15111b', '201a27', '2a2332', '342b3c', '413749', '4d4355', '605368',
)))


def canvas(size, color):
    return Image.new('RGB', size, color)


def glow(draw, points, colors):
    """Square-pixel halos, with the bright core following the authored path."""
    for width, color in zip((5, 3, 1), colors):
        draw.line(points, fill=color, width=width)


def pixel_size(box):
    return tuple(max(1, round(d * 16)) for d in box)


def face_sizes(size):
    x, y, z = pixel_size(size)
    return {'north': (x, y), 'south': (x, y), 'west': (z, y), 'east': (z, y),
            'up': (x, z), 'down': (x, z)}


class Rig:
    def __init__(self, boss, pack):
        self.pack = pack
        self.manifest_path = pack.parent / 'manifests' / f'boss__{boss}.json'
        self.manifest = json.loads(self.manifest_path.read_text(encoding='utf-8'))
        self.k = self.manifest['model_units_per_block']
        self.parts = {p['name']: p for p in self.manifest['parts']}
        self.boxes = {p: [] for p in self.parts}

    def box(self, part, center, size, faces, rotation=None):
        pivot = np.asarray(self.parts[part]['pivot_blocks'])
        mid = 8 + self.k * MODEL_SIGN * (np.asarray(center) - pivot)
        half = self.k * np.asarray(size) / 2
        lo, hi = mid - half, mid + half
        if np.any(lo < -16) or np.any(hi > 32) or np.any(hi <= lo):
            raise ValueError(f'{part}: element outside model bounds: {lo}, {hi}')
        element = {'from': lo.round(5).tolist(), 'to': hi.round(5).tolist(), 'faces': {}}
        if rotation:
            axis, angle = rotation
            if axis not in ('x', 'y', 'z') or angle not in (-45, -22.5, 22.5, 45):
                raise ValueError(rotation)
            element['rotation'] = {'axis': axis, 'angle': angle if axis == 'y' else -angle,
                                   'origin': mid.round(5).tolist()}
        self.boxes[part].append((element, faces))

    def emit(self):
        ns, model = self.manifest['id'].split(':')
        assets = self.pack / 'assets' / ns
        for name, part in self.parts.items():
            boxes = self.boxes[name]
            if not boxes:
                raise ValueError(f'no geometry for {name}')
            # Reuse identical tiles. All tiles have an extruded border for atlas sampling.
            atlas_tiles = {}
            for _, faces in boxes:
                for tile in faces.values():
                    atlas_tiles.setdefault((tile.size, tile.tobytes()), tile)
            positions, edge = shelf_pack([tile.size for tile in atlas_tiles.values()], pad=1)
            atlas = Image.new('RGBA', (edge, edge))
            uv = {}
            for (key, tile), (x, y) in zip(atlas_tiles.items(), positions):
                tile = np.asarray(tile.convert('RGBA'))
                padded = Image.fromarray(np.pad(tile, ((1, 1), (1, 1), (0, 0)), mode='edge'))
                atlas.paste(padded, (x - 1, y - 1))
                h, w = tile.shape[:2]
                uv[key] = [round(v * 16 / edge, 6) for v in (x, y, x + w, y + h)]
            elements = []
            for element, faces in boxes:
                mapped = {face: {'uv': uv[tile.size, tile.tobytes()], 'texture': '#t'}
                          for face, tile in faces.items()}
                elements.append({**element, 'faces': mapped})
            stem = f'{model}/{name}'
            texture = f'{ns}:item/{stem}'
            payload = {'credit': 'HobbangEnhance golems v3b', 'texture_size': [edge, edge],
                       'textures': {'t': texture, 'particle': texture}, 'elements': elements,
                       'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0],
                                             'scale': [1, 1, 1]}}}
            path = assets / 'models' / 'item' / f'{stem}.json'
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(payload, separators=(',', ':')) + '\n', encoding='utf-8', newline='\n')
            item = assets / 'items' / f'{stem}.json'
            definition = {'model': {'type': 'minecraft:model', 'model': texture}}
            if not item.exists() or json.loads(item.read_bytes()) != definition:
                item.parent.mkdir(parents=True, exist_ok=True)
                item.write_text(json.dumps(definition, indent=2), encoding='utf-8', newline='\n')
            path = assets / 'textures' / 'item' / f'{stem}.png'
            path.parent.mkdir(parents=True, exist_ok=True)
            atlas.save(path, optimize=True)
            n = len(elements)
            rotated = sum('rotation' in element for element in elements)
            part.update(cuboids=n, cuboids_voxel=n - rotated, cuboids_obb=rotated,
                        faces=sum(len(e['faces']) for e in elements), texture_px=edge, voxels=0)
            print(f'  {name:<10} {n:>3} elements  {edge}x{edge}')
        total = sum(p['cuboids'] for p in self.parts.values())
        self.manifest.update(total_cuboids=total, note='make_golems_v3.py, 16 texels per block, v3b textures and rig.')
        self.manifest_path.write_text(json.dumps(self.manifest, indent=2) + '\n', encoding='utf-8', newline='\n')
        print(f'{model}: {total} elements, {len(self.parts)} parts')


# Six irregular plates share joints on a 32-pixel layout. The two joints
# at each vertical edge keep their height when a face is mirrored or resized.
ROCK_PLATES = (
    ((0, 0), (14, 0), (12, 6), (10, 13), (4, 11), (0, 10)),
    ((14, 0), (31, 0), (31, 10), (27, 10), (22, 7), (17, 8), (12, 6)),
    ((0, 10), (4, 11), (10, 13), (12, 19), (8, 24), (3, 23), (0, 24)),
    ((10, 13), (12, 6), (17, 8), (22, 7), (26, 13), (23, 19),
     (24, 24), (18, 26), (8, 24), (12, 19)),
    ((22, 7), (27, 10), (31, 10), (31, 24), (27, 24), (24, 24), (23, 19), (26, 13)),
    ((0, 24), (3, 23), (8, 24), (18, 26), (24, 24), (27, 24), (31, 24), (31, 31), (0, 31)),
)
ROCK_JOINTS = (
    ((0, 10), (4, 11), (10, 13), (12, 6), (17, 8), (22, 7), (27, 10), (31, 10)),
    ((14, 0), (12, 6)),
    ((0, 24), (3, 23), (8, 24), (12, 19), (10, 13)),
    ((22, 7), (26, 13), (23, 19), (24, 24)),
    ((8, 24), (18, 26), (24, 24), (31, 24)),
)


def rock_surface(size, face, phase, entrances=(False, False), core=False):
    w, h = size
    light = {'up': 1, 'north': 0, 'west': 0, 'south': -1, 'east': -1, 'down': -2}[face]
    img = canvas(size, STONE[3 + light])
    d = ImageDraw.Draw(img)

    def pixels(points):
        result = []
        for x, y in points:
            inside = min(x, 31 - x) / 15.5
            offset_x, offset_y = ((0, 0), (-5, 3), (4, -3), (2, 4), (-3, -4))[phase % 5]
            u = x + inside * offset_x
            v = y + inside * min(y, 31 - y) / 15.5 * offset_y
            if phase % 2:
                u = 31 - u
            result.append((round(u * (w - 1) / 31), round(v * (h - 1) / 31)))
        return result

    if min(w, h) < 5:
        d.line([(0, 0), (w - 1, 0)], fill=STONE[4 + light])
        return img

    for i, plate in enumerate(ROCK_PLATES):
        shade = 3 + light + (0, 1, 0, 1, 0, 1)[(i + phase) % 6]
        d.polygon(pixels(plate), fill=STONE[shade])
    for points in (
        ((2, 3), (8, 3), (10, 2), (9, 5), (5, 6), (2, 5)),
        ((18, 12), (21, 11), (24, 14), (22, 16), (17, 16), (14, 18), (15, 14)),
        ((25, 19), (27, 15), (30, 14), (30, 18), (27, 21)),
        ((10, 27), (16, 28), (19, 29), (17, 30), (7, 30), (6, 28)),
    ):
        d.polygon(pixels(points), fill=STONE[4 + light])

    joints = [pixels(points) for points in ROCK_JOINTS]
    for joint in joints:
        d.line([(x, y - 1) for x, y in joint], fill=STONE[2 + light])
        d.line([(x, y + 1) for x, y in joint], fill=STONE[5 + light])
        d.line(joint, fill=STONE[0])
    d.line([(0, 0), (w - 1, 0)], fill=STONE[5 + light])

    enter, leave = entrances[::-1] if phase % 2 else entrances
    if enter or leave or core:
        # Extend the two outside endpoints before cropping each face. The halo
        # meets the same pixels on its neighboring face, including east/north.
        main = joints[0]
        a, b = main[0], main[-1]
        if enter:
            main = [(a[0] + (-2 if a[0] == 0 else 2), a[1]), *main]
        else:
            main = main[2:]
        if leave:
            main = [*main, (b[0] + (-2 if b[0] == 0 else 2), b[1])]
        else:
            main = main[:-2]
        branches = [pixels(((22, 7), (26, 13), (23, 19), (24, 24))),
                    pixels(((10, 13), (12, 19), (8, 24), (5, 24)))]
        if core:
            branches.extend(pixels(points) for points in (
                ((10, 13), (12, 19), (16, 19), (18, 24), (18, 26), (16, 29), (16, 31)),
                ((23, 19), (19, 17), (17, 18), (15, 15), (12, 6), (14, 0)),
                ((18, 26), (24, 24), (27, 24)),
            ))
        for branch in branches:
            glow(d, branch, (CR[0], CR[3], CR[7]))
        glow(d, main, (CR[0], CR[4], CR[9]))
        for point in pixels(((12, 6), (22, 7))):
            d.point(point, fill=CR[10])
    return img


def stone_faces(size, phase, lit=False, motif=None, core=False):
    dims = face_sizes(size)
    joins = (False, lit, lit and phase % 2 == 0, lit and phase % 2 != 0)
    result = {face: rock_surface(dims[face], face, phase + i,
                                 (joins[i], joins[(i + 1) % len(SIDES)]), core and i == 0)
              for i, face in enumerate(SIDES)}
    for face in ('up', 'down'):
        result[face] = rock_surface(dims[face], face, phase + 2)
    if motif:
        motif(result['north'])
    return result


def face_of_colossus(img):
    w, h = img.size
    d = ImageDraw.Draw(img)
    y = round(h * .44)
    for x, direction in ((w // 2 - 3, -1), (w // 2 + 2, 1)):
        d.polygon([(x - 3, y - 2), (x + 3, y - 2), (x + 2, y + 2), (x - 2, y + 2)],
                  fill=STONE[0])
        d.line([(x, y), (x + 2 * direction, y - 1)], fill=CR[7], width=2)
        d.point((x + direction, y), fill=CR[10])
    d.line([(w // 2 - 3, h - 4), (w // 2 + 2, h - 4)], fill=STONE[0], width=2)


def knuckles(img):
    w, h = img.size
    d = ImageDraw.Draw(img)
    for i in range(3):
        x = (i + 1) * w // 4
        y = h * 3 // 5 - (i % 2)
        d.line([(x + 1, y), (x - 1, y + 4), (x - 1, h - 1)], fill=STONE[0])
        d.line([(x + 1, y + 3), (x + 1, h - 2)], fill=STONE[5])


def core_faces(size):
    result = {}
    for side, (w, h) in face_sizes(size).items():
        tile = canvas((w, h), CR[2])
        d = ImageDraw.Draw(tile)
        d.polygon([(0, 0), (w - 1, 0), (w // 2, h // 2), (0, h - 1)], fill=CR[5])
        d.polygon([(0, 0), (w - 1, 0), (w // 2, h // 2)], fill=CR[8])
        d.polygon([(w // 2, h // 2), (w - 1, 0), (w - 1, h - 1)], fill=CR[4])
        d.line([(0, 0), (w // 2, h // 2), (w - 1, h - 1)], fill=CR[7])
        d.line([(w // 4, 1), (w // 2, 1), (w - 2, h // 4)], fill=CR[9])
        if w >= 6 and h >= 6:
            cx, cy = w // 2, h // 2
            d.polygon([(cx, cy - 2), (cx + 2, cy), (cx, cy + 1), (cx - 2, cy)], fill=CR[9])
            d.point((cx, cy), fill=CR[10])
        result[side] = tile
    return result


def crystal_faces(size, axis, sign, start, end, facet):
    faces = {}
    for face, (w, h) in face_sizes(size).items():
        vertices = np.array(FACE_VERTS[face]) * MODEL_SIGN
        u = int((vertices[3, axis] - vertices[0, axis]) * sign)
        v = int((vertices[1, axis] - vertices[0, axis]) * sign)
        shade = {'up': 5, 'north': 4, 'west': 5, 'south': 3, 'east': 2, 'down': 2}[face]
        if facet % 3 == 1 and face in SIDES:
            shade += 1
        if not (u or v):
            tile = canvas((w, h), CR[shade])
            d = ImageDraw.Draw(tile)
            d.polygon([(0, 0), (w - 1, 0), (0, h - 1)], fill=CR[shade + 1])
            d.line([(0, 0), (w - 1, 0)], fill=CR[shade + 2])
            faces[face] = tile
            continue

        length, width = (w, h) if u else (h, w)
        along = np.linspace(start, end, length)
        across = np.linspace(0, 1, width)
        # One broad diagonal facet across the whole shard. Sample it in shared
        # length coordinates so the shaft sections retain the same facet.
        ridge = .38 + .51 * across[:, None]
        levels = shade + 2 * (along > ridge)
        levels[0, :] = shade + 3
        if width > 2:
            levels[-1, :] = shade - 1
        if u < 0 or v < 0:
            levels = levels[:, ::-1]
        if v:
            levels = levels.T
        faces[face] = Image.fromarray(np.asarray(CR, dtype=np.uint8)[levels])
    return faces


def shard(rig, part, root, axis, angle, length, width, facet):
    """Build a thick prism and a chisel tip with one-axis rotations."""
    a = math.radians(angle)
    direction = np.array([0, math.cos(a), math.sin(a)] if axis == 'x'
                         else [-math.sin(a), math.cos(a), 0])
    long_axis = 1
    if abs(angle) > 45:
        long_axis = 2 if axis == 'x' else 0
        angle -= math.copysign(90, angle)
    sign = 1 if direction[long_axis] >= 0 else -1
    rotation = (axis, angle) if angle else None
    cap_radius = width * .48
    shoulder = 1 - cap_radius / length
    bands = ((0, shoulder * .2, .87), (shoulder * .2, shoulder * .78, 1),
             (shoulder * .78, shoulder + .006, .97))
    cross_axis = (2 if axis == 'x' else 0) if long_axis == 1 else 1
    depth_axis = 'xyz'.index(axis)
    for start, end, taper in bands:
        size = np.zeros(3)
        size[cross_axis] = width * taper
        size[depth_axis] = width * .84 * taper
        size[long_axis] = (end - start) * length
        center = root + direction * (start + end) * .5 * length
        rig.box(part, center, size, crystal_faces(size, long_axis, sign, start, end, facet), rotation)

    # A rotated square leaves a true chisel point above the flat shaft.
    # The base half of the diamond is hidden inside the shaft.
    edge = cap_radius * math.sqrt(2)
    size = np.full(3, edge)
    size[depth_axis] = width * .78
    cap_angle = (angle + 90) % 90 - 45
    cap_faces = {}
    for face, (w, h) in face_sizes(size).items():
        tile = canvas((w, h), CR[5 if face in ('north', 'up', 'west') else 3])
        draw = ImageDraw.Draw(tile)
        draw.polygon([(0, 0), (w - 1, 0), (0, h - 1)], fill=CR[7 if face in ('north', 'up', 'west') else 4])
        draw.line([(0, 0), (w - 1, 0)], fill=CR[8 if face in ('north', 'up') else 5])
        cap_faces[face] = tile
    rig.box(part, root + direction * shoulder * length, size, cap_faces,
            (axis, cap_angle) if cap_angle else None)


def colossus(rig):
    def rock(part, center, size, phase, lit=False, motif=None, rotation=None, core=False):
        rig.box(part, center, size, stone_faces(size, phase, lit, motif, core), rotation)

    rock('torso', (0, 2.61, -.17), (1.42, .92, 1.13), 2, True)
    rock('torso', (0, 2.22, .04), (.81, .72, .89), 4)
    rock('chest', (0, 4.27, -.18), (2.07, 1.72, 1.52), 3, True, core=True)
    rock('chest', (0, 5.16, -.22), (2.30, .50, 1.50), 1)
    rock('chest', (0, 3.31, -.10), (1.51, .64, 1.16), 5, True)
    for depth, edge in ((.56, .93), (.71, .74), (.83, .60)):
        size = (edge, edge, .22)
        faces = stone_faces(size, 3, True, core=True) if edge > .8 else core_faces(size)
        rig.box('chest', (0, 4.45, depth), size, faces, ('z', 45))
    rock('head', (0, 5.74, .11), (1.12, .87, .92), 5, False, face_of_colossus)
    rock('head', (0, 5.43, .29), (.79, .39, .73), 2)
    for part, crystals in (
        ('chest', ((.45, 5.18, -.77, 'x', -22.5, 1.47, .41),
                   (-.43, 5.24, -.70, 'x', -22.5, 1.53, .48),
                   (.03, 5.09, -.84, 'x', -22.5, 1.32, .38),
                   (-.72, 4.66, -.86, 'x', -45, .75, .31),
                   (.70, 4.79, -.81, 'x', -45, .83, .34))),
        ('head', ((0, 5.94, .34, 'x', 0, 1.10, .46),
                  (.39, 5.97, -.04, 'z', -22.5, 1.11, .33),
                  (-.36, 5.97, .01, 'z', 22.5, 1.13, .36),
                  (.19, 5.96, -.24, 'x', -22.5, 1.03, .30),
                  (-.23, 5.94, -.20, 'x', -22.5, .89, .27))),
    ):
        for facet, (x, y, z, axis, angle, length, width) in enumerate(crystals):
            shard(rig, part, np.array([x, y, z]), axis, angle, length, width, facet)

    for side, q in ((1, 'l'), (-1, 'r')):
        upper, fore, fist, leg, shin = (f'{p}_{q}' for p in ('arm', 'fore', 'fist', 'leg', 'shin'))
        phase = 1 if q == 'l' else 4
        rock(upper, (side * 1.65, 5.03, -.28), (1.05, 1.13, 1.39), phase, True, rotation=('z', side * 22.5))
        rock(upper, (side * 1.78, 4.59, -.18), (.77, 1.04, .92), phase + 1, rotation=('z', side * 22.5))
        rock(fore, (side * 2.05, 3.89, .17), (.86, 1.02, 1.04), phase + 2, True, rotation=('z', side * 22.5))
        rock(fist, (side * 2.28, 2.76, .43), (1.20, 1.62, 1.41), phase, True, knuckles,
             rotation=('z', side * 22.5))
        rock(fist, (side * 1.87, 2.78, .70), (.43, .91, .70), phase + 2, rotation=('z', side * 22.5))
        rock(leg, (side * .86, 1.95, -.14), (.89, 1.29, 1.05), phase + 3, rotation=('z', side * 22.5))
        rock(shin, (side * 1.03, .85, -.27), (1.19, 1.54, 1.52), phase + 1)
        rock(shin, (side * 1.06, .31, -.10), (1.38, .62, 2.12), phase, False, knuckles)

        clusters = {
            upper: ((1.60, 5.43, -.29, 'z', -22.5, 1.65, .66),
                    (1.81, 5.10, -.75, 'x', -22.5, 1.33, .47),
                    (1.99, 5.02, -.16, 'z', -45, .99, .37),
                    (2.05, 4.70, -.34, 'z', -67.5, .65, .31),
                    (1.69, 5.07, .37, 'x', 22.5, .86, .42)),
            fore: ((2.33, 3.83, -.13, 'z', -22.5, 1.29, .43),
                   (2.37, 3.61, .16, 'z', -45, 1.05, .40),
                   (2.04, 3.66, .52, 'x', 22.5, .82, .36),
                   (2.13, 3.58, -.40, 'x', -22.5, .93, .35)),
            fist: ((2.61, 2.89, -.23, 'z', -22.5, 1.12, .48),
                   (2.51, 2.44, .08, 'z', -45, .96, .39),
                   (2.41, 2.50, .76, 'x', 22.5, .99, .48),
                   (2.12, 2.49, .74, 'x', 22.5, .94, .42),
                   (2.65, 2.43, .46, 'z', -45, .72, .34),
                   (2.34, 2.61, -.26, 'x', -45, .79, .38)),
            shin: ((1.02, 1.17, .43, 'x', 22.5, 1.03, .44),),
        }
        for part, crystals in clusters.items():
            for facet, (x, y, z, axis, angle, length, width) in enumerate(crystals):
                adjust = 1 if q == 'l' else (.92 if facet % 2 else 1.04)
                reach = .16 if part == fist else .09 if part == fore else 0
                shard(rig, part, np.array([side * x, y, z + reach]), axis,
                      side * angle if axis == 'z' else angle, length * adjust, width, facet + phase)


def brass_tile(size, face, motif=None):
    w, h = size
    lit = face in ('north', 'up', 'west')
    img = canvas(size, BR[5 if lit else 4])
    d = ImageDraw.Draw(img)
    d.line([(0, h - 1), (0, 0), (w - 1, 0)], fill=BR[7 if lit else 6])
    d.line([(1, h - 1), (w - 1, h - 1), (w - 1, 1)], fill=BR[2])
    if w >= 6 and h >= 6:
        x, y = w - 3, h - 3
        panel = [(3, 2), (x, 2), (x, y - 1), (x - 1, y), (2, y), (2, 3)]
        d.polygon(panel, fill=BR[5 if lit else 4], outline=BR[2])
        if w >= 9 and h >= 9:
            d.line([(3, 3), (x - 1, 3)], fill=BR[7 if lit else 6])
            d.line([(3, y + 1), (x, y + 1)], fill=BR[6])
            for cx, cy in ((2, 2), (x, 2), (2, y), (x, y)):
                d.line([(cx, cy + 1), (cx + 1, cy + 1)], fill=BR[2])
                d.point((cx, cy), fill=GOLD[1 if lit else 0])
            if w >= 13 and h >= 10:
                # Short clusters of exposed metal where the panel lip wears.
                d.line([(4, 0), (7, 0), (8, 1), (10, 1)], fill=GOLD[0])
                d.line([(w - 5, y), (w - 4, y), (w - 3, y - 1)], fill=BR[7])
                d.line([(3, h // 2), (w - 4, h // 2)], fill=BR[3])
                d.line([(4, h // 2 + 1), (w - 5, h // 2 + 1)], fill=BR[6])
    if motif == 'conduit' and w >= 9 and h >= 9:
        x, y = w // 3, h // 2
        path = [(x, 4), (x, y - 2), (x + 2, y), (w - 5, y), (w - 5, h - 5)]
        glow(d, path, (BR[3], TEAL_DIM[0], TEAL[0]))
        d.line([(x + 2, y), (min(x + 5, w - 5), y)], fill=TEAL[1])
    if motif == 'lens' and w >= 7 and h >= 7:
        x, y = w // 2, h // 2
        d.polygon([(x, y - 3), (x + 3, y), (x, y + 3), (x - 3, y)], fill=DARK[2])
        d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=TEAL[0])
        d.point((x, y - 1), fill=TEAL[1])
    if motif == 'strut' and w >= 4 and h >= 9:
        x = w // 2
        d.line([(x, 3), (x, h // 3), (x - 1, h // 3 + 1), (x - 1, h - 4)], fill=TEAL_DIM[0], width=2)
        d.line([(x, 4), (x, h // 3), (x - 1, h // 3 + 1), (x - 1, h - 5)], fill=TEAL[0])
        d.point((x, 4), fill=TEAL[1])
    return img


def hourglass(img):
    w, h = img.size
    d = ImageDraw.Draw(img)
    x = w // 2
    rim = [(2, 3), (w - 3, 3), (w - 4, 7), (x + 1, h // 2), (w - 4, h - 7),
           (w - 3, h - 4), (2, h - 4), (3, h - 7), (x - 1, h // 2), (3, 7)]
    d.rectangle((2, 2, w - 3, h - 3), fill=DARK[2])
    d.polygon(rim, fill=TEAL_DIM[0])
    d.polygon([(3, 5), (w - 4, 5), (x, h // 2 - 1)], fill=SAND[0])
    d.polygon([(x, h // 2 + 2), (w - 4, h - 5), (3, h - 5)], fill=SAND[2])
    d.polygon([(x, h // 2 + 2), (x, h - 5), (3, h - 5)], fill=SAND[3])
    d.line([(x, h // 2 - 2), (x, h // 2 + 3)], fill=SAND[3])
    d.line(rim[:4], fill=TEAL[0])
    d.line([(2, 3), (w - 3, 3)], fill=GOLD[1])
    d.line([(2, h - 4), (w - 3, h - 4)], fill=GOLD[0])


def hand_faces(size):
    faces = {}
    for face, (w, h) in face_sizes(size).items():
        tile = canvas((w, h), HANDS[0])
        draw = ImageDraw.Draw(tile)
        if w > 1:
            draw.line([(0, 0), (0, h - 1)], fill=HANDS[2])
        if w >= 3:
            draw.line([(1, 0), (1, h * 2 // 3)], fill=GOLD[0])
        faces[face] = tile
    return faces


def xy(point, center, angle):
    c, s = math.cos(angle), math.sin(angle)
    x, y = point
    return round(center + x * c - y * s), round(center + x * s + y * c)


def dial_texture(edge):
    mid = (edge - 1) / 2
    img = canvas((edge, edge), BR[3])
    d = ImageDraw.Draw(img)
    for inset, color in ((1, BR[6]), (2, GOLD[1]), (4, BR[4]), (6, BR[2]), (7, GOLD[0]), (9, GOLD[4])):
        d.ellipse((inset, inset, edge - 1 - inset, edge - 1 - inset), fill=color)
    d.pieslice((9, 9, edge - 10, edge - 10), 190, 355, fill=GOLD[5])
    d.arc((2, 2, edge - 3, edge - 3), 200, 305, fill=SHINE[0], width=2)
    d.arc((3, 3, edge - 4, edge - 4), 15, 145, fill=BR[6])
    for i in range(60):
        angle = i * math.tau / 60
        if i % 5 == 0:
            r = mid - 8
            d.polygon([xy(p, mid, angle) for p in ((-1, -r), (1, -r), (1, -r + 4), (0, -r + 5), (-1, -r + 4))],
                      fill=HANDS[0])
            if i % 15 == 0:
                cx, cy = xy((0, -mid + 3), mid, angle)
                d.point((cx, cy), fill=SHINE[2])
        else:
            d.point(xy((0, -mid + 6), mid, angle), fill=BR[6])
    for inset, color in ((mid - 7, GOLD[0]), (mid - 4, GOLD[6])):
        d.ellipse((inset, inset, edge - 1 - inset, edge - 1 - inset), outline=color)
    return img


def span_rectangles(rows):
    """Merge identical runs on consecutive scanlines without splitting the painted face."""
    active = {}
    for y, spans in enumerate([*rows, ()]):
        for start, end in sorted(active.keys() - set(spans)):
            yield start, active.pop((start, end)), end, y
        for span in spans:
            active.setdefault(span, y)


def circle_rows(edge, hole=0):
    midpoint = edge / 2
    rows = []
    for y in range(edge):
        outer = math.sqrt(midpoint**2 - (y + .5 - midpoint)**2)
        a, b = round(midpoint - outer), round(midpoint + outer)
        gap = math.sqrt(max(0, hole**2 - (y + .5 - midpoint)**2))
        c, d = round(midpoint - gap), round(midpoint + gap)
        rows.append(((a, c), (d, b)) if c != d else ((a, b),))
    return rows


def disk(rig, part, center, thickness, px_per_block, art, wall):
    edge = art.width
    for x0, y0, x1, y1 in span_rectangles(circle_rows(edge)):
        size = ((x1 - x0) / px_per_block, (y1 - y0) / px_per_block, thickness)
        faces = {face: canvas(dim, wall) for face, dim in face_sizes(size).items()}
        plate = art.crop((x0, y0, x1, y1))
        faces['north'] = plate
        faces['south'] = plate.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        dx = ((x0 + x1) / 2 - edge / 2) / px_per_block
        dy = (edge / 2 - (y0 + y1) / 2) / px_per_block
        rig.box(part, (center[0] + dx, center[1] + dy, center[2]), size, faces)


def cog(rig, part, center, radius, thick):
    edge = max(8, round(radius * 1.65 * 24))
    art = canvas((edge, edge), BR[5])
    d = ImageDraw.Draw(art)
    d.ellipse((0, 0, edge - 1, edge - 1), fill=BR[4])
    d.arc((0, 0, edge - 1, edge - 1), 200, 335, fill=GOLD[0], width=2)
    d.ellipse((2, 2, edge - 3, edge - 3), fill=PATINA[0])
    d.ellipse((edge // 2 - 2, edge // 2 - 2, edge // 2 + 1, edge // 2 + 1), fill=BR[7])
    d.point((edge // 2 - 1, edge // 2 - 1), fill=SHINE[0])
    disk(rig, part, center, thick, edge / (1.66 * radius), art, BR[4])
    for i in range(8):
        angle = i * 45
        a = math.radians(angle)
        x = center[0] + radius * .93 * math.cos(a)
        y = center[1] + radius * .93 * math.sin(a)
        spin = (angle + 45) % 90 - 45
        length, width = radius * .42, radius * .39
        size = (length, width, thick * .84) if (angle - spin) % 180 == 0 else (width, length, thick * .84)
        faces = {face: brass_tile(dim, face) for face, dim in face_sizes(size).items()}
        rig.box(part, (x, y, center[2]), size, faces, ('z', spin) if spin else None)


def tilted(center, offset, angle):
    """Rotate the local X-aligned slab assembly around a shared hinge."""
    x, y, z = offset
    a = math.radians(angle)
    c, s = math.cos(a), math.sin(a)
    return center[0] + x, center[1] + y * c - z * s, center[2] + y * s + z * c


def halo(rig, center, edge, height, angle):
    mid = edge / 2
    rim = canvas((edge, edge), BR[6])
    draw = ImageDraw.Draw(rim)
    draw.ellipse((1, 1, edge - 2, edge - 2), fill=GOLD[1])
    draw.ellipse((2, 2, edge - 3, edge - 3), fill=BR[5])
    for i in range(16):
        a = i * math.tau / 16
        x, y = xy((0, -mid + 2), mid - .5, a)
        draw.point((x, y), fill=SHINE[0] if i % 2 else TEAL[1])
    for x0, z0, x1, z1 in span_rectangles(circle_rows(edge, mid - 3)):
        size = ((x1 - x0) / 16, .10, (z1 - z0) / 16)
        local = ((mid - (x0 + x1) / 2) / 16, height, (mid - (z0 + z1) / 2) / 16)
        pos = tilted(center, local, angle)
        faces = {face: canvas(dim, BR[6 if face in ('north', 'west') else 4])
                 for face, dim in face_sizes(size).items()}
        tile = rim.crop((x0, z0, x1, z1))
        faces['up'] = tile
        faces['down'] = tile.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        rig.box('ring', pos, size, faces, ('x', angle))


def leaf(rig, part, hinge, side):
    # One authored panel. Each strip takes its pixels from the same face.
    w, h = 22, 36
    shape = [(0, 3), (4, 5), (8, 7), (14, 8), (19, 7), (24, 5), (30, 3), (34, .5)]
    rows = []
    art = canvas((w, h), BR[2])
    d = ImageDraw.Draw(art)
    for y, width in enumerate(np.interp(np.arange(h), *zip(*shape))):
        t = y / (h - 1)
        cx = w // 2 + side * round(5 * t)
        left, right = cx - round(width), cx + round(width) + 1
        rows.append(((left, right),))
        d.line([(left, y), (right - 1, y)], fill=GOLD[0 if y < 9 else 1])
        if right - left >= 5:
            d.line([(left + 1, y), (right - 2, y)], fill=BR[6])
            bevel = min(cx - 1, left + 2 + (1 if y < 15 else 0))
            d.line([(left + 2, y), (cx, y)], fill=PATINA[1])
            d.line([(bevel, y), (cx - 1, y)], fill=PATINA[2 if 6 < y < 23 else 3])
            d.line([(cx + 1, y), (right - 3, y)], fill=PATINA[0])
            if right - cx >= 6 and y < 25:
                d.point((right - 3, y), fill=PATINA[2])
            d.point((cx, y), fill=GOLD[2 if 10 < y < 20 else 0])
            if 6 <= y <= 27:
                vein = cx - (2 if y in (12, 13, 20, 21) else 1)
                d.point((vein, y), fill=TEAL[1 if y in (10, 11, 19, 20, 21) else 0])
        if y in (8, 14, 20, 25):
            d.point((left + 1, y), fill=SHINE[0])
            d.point((right - 2, y), fill=BR[2])
            d.line([(left + 3, y + 1), (cx - 2, y + 1)], fill=PATINA[0])
    d.line([(w // 2, 2), (w // 2, 8)], fill=GOLD[4], width=2)
    for x0, y0, x1, y1 in span_rectangles(rows):
        t = (y0 + y1) / (2 * h)
        size = ((x1 - x0) / 16, (y1 - y0) / 16, .32 - t * .23)
        local = (((x0 + x1) / 2 - w / 2) / 16, -(y0 + y1) / 32 + .06, 0)
        pos = tilted(hinge, local, -22.5)
        faces = {face: brass_tile(dim, face) for face, dim in face_sizes(size).items()}
        tile = art.crop((x0, y0, x1, y1))
        faces['north'] = tile
        faces['south'] = tile.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        rig.box(part, pos, size, faces, ('x', -22.5))


def sentinel(rig):
    def metal(part, center, size, motif=None, rotation=None):
        faces = {face: brass_tile(dim, face, motif if face in SIDES else None)
                 for face, dim in face_sizes(size).items()}
        if motif == 'sand':
            for face in ('north', 'south'):
                hourglass(faces[face])
        rig.box(part, center, size, faces, rotation)

    metal('body', (0, 3.20, -.34), (1.39, 1.29, 1.21), 'conduit')
    metal('body', (0, 3.70, -.14), (2.08, .29, .91), 'strut')
    metal('body', (0, 4.10, -.09), (.61, .57, .62), 'lens')
    metal('body', (0, 2.45, -.08), (.87, .46, .63), 'strut')
    cx, cy, _ = rig.parts['clock']['pivot_blocks']
    disk(rig, 'body', (cx, cy, .391), .328, 32, dial_texture(46), BR[5])
    cog(rig, 'body', (0, 4.83, -.16), .17, .13)
    for side, q in ((1, 'l'), (-1, 'r')):
        gear, arm, blade = (f'{p}_{q}' for p in ('gear', 'arm', 'blade'))
        cog(rig, gear, rig.parts[gear]['pivot_blocks'], .28, .16)
        cog(rig, 'body', (side * .67, 2.60, .22), .22, .13)
        cog(rig, 'body', (side * .62, 3.37, -.98), .31, .15)
        cog(rig, 'body', (side * .97, 4.43, -.11), .13, .10)
        metal(arm, (side * 1.10, 3.52, -.05), (.67, .59, .71), 'strut', ('z', side * 22.5))
        metal(arm, (side * 1.34, 3.12, .02), (.42, .78, .56), 'strut', ('z', side * 22.5))
        metal(arm, (side * 1.62, 2.78, .11), (.38, .59, .51), 'strut', ('z', side * 45))
        cog(rig, arm, (side * 1.16, 3.51, .35), .24, .14)
        # Four new gears on each side, mounted to the existing body and arm parts.
        cog(rig, 'body', (side * .43, 3.76, -.97), .20, .12)
        cog(rig, 'body', (side * .59, 2.84, -.97), .24, .13)
        cog(rig, arm, (side * 1.15, 3.66, -.43), .26, .14)
        cog(rig, arm, (side * 1.37, 3.27, -.27), .15, .10)
        hinge = rig.parts[blade]['pivot_blocks']
        leaf(rig, blade, hinge, side)
        cog(rig, blade, (hinge[0], hinge[1] - .09, hinge[2] + .17), .21, .14)

    ring_pivot = rig.parts['ring']['pivot_blocks']
    halo(rig, ring_pivot, 35, .03, 22.5)
    halo(rig, ring_pivot, 29, .40, 22.5)
    for center, size, angle in (
        ((cx - .125, cy + .125, .610), (.085, .40, .048), 45),
        ((cx + .103, cy + .103, .647), (.075, .34, .047), -45),
        ((cx + .16, cy - .16, .670), (.032, .48, .027), 45),
        ((cx, cy, .680), (.125, .125, .08), 45),
    ):
        rig.box('clock', center, size, hand_faces(size), ('z', angle))
    metal('pendulum', (0, 1.31, .14), (.76, 1.18, .61), 'sand')
    metal('pendulum', (0, 1.97, .12), (1.01, .22, .80), 'strut')
    metal('pendulum', (0, 2.15, .16), (.24, .31, .31))
    metal('pendulum', (0, .66, .12), (.96, .23, .77), 'strut')
    cog(rig, 'pendulum', (0, .39, .24), .20, .13)
    cog(rig, 'pendulum', (0, .11, .24), .10, .09)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bosses', nargs='*', choices=('prism_colossus', 'chrono_sentinel'))
    parser.add_argument('--pack', type=Path, default=PACK)
    args = parser.parse_args()
    designs = {'prism_colossus': colossus, 'chrono_sentinel': sentinel}
    for boss in args.bosses or designs:
        rig = Rig(boss, args.pack)
        designs[boss](rig)
        rig.emit()


if __name__ == '__main__':
    main()
