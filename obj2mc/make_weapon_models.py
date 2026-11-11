"""Hand-crafted Blockbench-style cuboid models and pixel-art textures
for HobbangEnhance boss weapons (weapons-detail remodeling):
  - weapon/stormcaller        (<= 500 cuboids, height 2.2, k=14.5, pivot [0.0, 0.6, 0.0])
  - weapon/crystal_greatsword (<= 300 cuboids, height 2.3, k=13.0, pivot [0.0, 0.52, 0.0])
  - weapon/chrono_dagger      (<= 350 cuboids, height 2.5, k=11.75, pivot [0.54, 0.55, 0.0])
(weapon/smith_hammer is untouched.)

Cuboid geometry keeps Minecraft's single-axis rotations and -16..32 bounds.
Faces are painted from the original palettes at a 16 texel/block baseline.
Narrow engraved faces use additional samples to preserve legible pixel clusters.
The 1.5.2 hand models keep their own geometry, display settings and atlases.
"""
import json
import math
import os
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from obb import rot
from obj2mc import FACE_NORMAL, FACE_VERTS, shelf_pack
from make_smith import HERE, NS, PPU, new_part_table, add_part, add_box


# ----------------------------------------------------------------------------- Extracted Original Palettes (models-3d/)
# 1. Crystal Greatsword (extracted from weapon_crystal_greatsword.png)
CRYS_VOID    = ( 35,   0,  37)  # #230025 guard dark / void
CRYS_DEEP    = (105,   0, 250)  # #6900fa blade p10 deep royal violet shadow
CRYS_BASE    = (145,   0, 255)  # #9100ff blade p25 royal amethyst
CRYS_MID     = (187,  51, 255)  # #bb33ff blade p50 vibrant magenta-violet
CRYS_BRIGHT  = (223, 113, 255)  # #df71ff blade p75 luminous orchid
CRYS_GLOW    = (246, 162, 255)  # #f6a2ff blade p90 pink-violet reflection
CRYS_WHITE   = (255, 211, 255)  # #ffd3ff blade p98 specular crystal highlight
OBS_DARK     = ( 35,   0,  37)  # #230025 guard p10 dark obsidian-violet
OBS_BASE     = ( 66,  36,  42)  # #42242a guard dark bronze-violet
OBS_GOLD     = (100,  64,  55)  # #644037 guard p50 antique bronze
OBS_GOLD_HI  = (131,  96,  67)  # #836043 guard p75 warm bronze highlight

# 2. Stormcaller (Tempest Dragon-Wing Battleaxe: extracted from weapon_stormcaller.png)
STORM_VOID   = (  3,  11,   0)  # #030b00 p10 pitch-black wing membrane
STORM_DARK   = ( 20,  24,   2)  # #141802 p25 dark obsidian-slate
STORM_IRON   = ( 43,  43,  25)  # #2b2b19 p45 dark iron-slate
DRAGON_DARK  = ( 30,  33,  37)  # #1e2125 dragon head/bone p50 dark gunmetal
DRAGON_STEEL = ( 59,  60,  67)  # #3b3c43 dragon head/bone p75 gunmetal steel (R<=125: non-animated)
DRAGON_RIM   = ( 86,  81,  89)  # #565159 dragon head/bone p90 steel bevel (R<=125: non-animated)
DRAGON_HI    = (105,  96, 102)  # #696066 dragon head/bone p96 steel highlight (R<=125: non-animated)
BRONZE_MUTE  = ( 87,  72,  42)  # #57482a p65 dark bronze inner blade bevel
BRONZE_M_HI  = (128,  98,  48)  # #806230 p74 warm bronze highlight (R<=134: non-animated)
GOLD_AMBER   = (184, 108,  34)  # #b86c22 inner golden feather transition
GOLD_EDGE    = (230, 156,  65)  # #e69c41 p82 golden wing-feather bevel (animated by lightning_flicker)
GOLD_BRIGHT  = (255, 204,  72)  # #ffcc48 p90 bright lightning gold (animated by lightning_flicker)
GOLD_WHITE   = (255, 228,  94)  # #ffe45e p95 white-gold lightning core (animated by lightning_flicker)

# 3. Chrono Dagger / Scythe (extracted from weapon_chrono_dagger.png)
BRASS_VOID   = ( 52,  29,   0)  # #341d00 p10 deep umber-bronze recess
BRASS_DARK   = ( 78,  42,   0)  # #4e2a00 p25 dark antique bronze
BRASS_BASE   = (107,  66,   0)  # #6b4200 p45 aged bronze plate
BRASS_MID    = (144,  87,   6)  # #905706 p65 warm clockwork brass
BRASS_GOLD   = (174, 108,  17)  # #ae6c11 p82 burnished gold
BRASS_HI     = (201, 129,  35)  # #c98123 p95 bright amber-gold
BRASS_BRIGHT = (242, 176,  56)  # #f2b038 p99 golden edge & rune highlight
DIAL_IVORY   = (255, 254, 214)  # #fffed6 clock dial face
DIAL_RIM     = (196, 168, 112)  # #c4a870 clock dial rim shading
DIAL_HAND    = ( 52,  29,   0)  # #341d00 clock hands & numerals
CHRONO_TEAL  = ( 68, 147, 138)  # #44938a oxidized chrono-teal rune channel
CHRONO_GLOW  = (106, 196, 184)  # #6ac4b8 bright chrono-teal highlight


FRONT_BACK = ('north', 'south')
WALLS = (*FRONT_BACK, 'east', 'west')
CRYSTAL = (CRYS_VOID, CRYS_DEEP, CRYS_BASE, CRYS_MID, CRYS_BRIGHT, CRYS_GLOW, CRYS_WHITE)
STEEL = (STORM_VOID, DRAGON_DARK, DRAGON_STEEL, DRAGON_RIM, DRAGON_HI)
STORM_GOLD = (BRONZE_MUTE, BRONZE_M_HI, GOLD_AMBER, GOLD_EDGE, GOLD_BRIGHT, GOLD_WHITE)
BRASS = (BRASS_VOID, BRASS_DARK, BRASS_BASE, BRASS_MID, BRASS_GOLD, BRASS_HI, BRASS_BRIGHT)
OBSIDIAN = (OBS_DARK, OBS_BASE, OBS_GOLD, OBS_GOLD_HI)
FACE_LIGHT = {'up': 1, 'north': 0, 'south': 0, 'west': 0, 'east': -1, 'down': -2}
PROJECTION_PPU = 3 * PPU


def stepped_path(draw, points, colors, widths=(5, 3, 1)):
    for width, color in zip(widths, colors):
        draw.line(points, fill=color, width=width)


class FaceArt:
    """A common pixel drawing sampled by neighboring cuboids in world X/Y."""
    def __init__(self, bounds, background):
        self.x, self.top, width, height = bounds
        self.image = Image.new('RGB', (width, height), background)
        self.draw = ImageDraw.Draw(self.image)

    def points(self, coords):
        return [(round((x - self.x) * PROJECTION_PPU), round((self.top - y) * PROJECTION_PPU))
                for x, y in coords]

    def sample(self, box, face, width, height):
        corners = (np.array(FACE_VERTS[face]) - .5) * box['s'] * [-1, 1, -1]
        if box['rot']:
            axis, angle = box['rot']
            corners = corners @ rot('xyz'.index(axis), angle).T
        corners += box['c']
        u = ((np.arange(width) + .5) / width)[None, :, None]
        v = ((np.arange(height) + .5) / height)[:, None, None]
        xy = corners[0] + u * (corners[3] - corners[0]) + v * (corners[1] - corners[0])
        x = np.rint((xy[..., 0] - self.x) * PROJECTION_PPU).astype(int)
        y = np.rint((self.top - xy[..., 1]) * PROJECTION_PPU).astype(int)
        art = np.asarray(self.image)
        return Image.fromarray(art[y.clip(0, art.shape[0] - 1), x.clip(0, art.shape[1] - 1)])


def storm_web():
    art = FaceArt((-.90, 2.24, 88, 64), STORM_DARK)
    facets = (
        (((.35, 2.08), (.57, 2.11), (.62, 1.93), (.51, 1.98), (.42, 1.90)), STORM_IRON),
        (((.42, 1.89), (.55, 1.85), (.68, 1.76), (.59, 1.66), (.41, 1.76)), STORM_VOID),
        (((.35, 1.61), (.58, 1.61), (.72, 1.55), (.65, 1.43), (.47, 1.53)), STORM_IRON),
        (((.30, 1.42), (.49, 1.44), (.63, 1.28), (.46, 1.31), (.35, 1.16)), STORM_VOID),
    )
    branches = (
        ((.43, 1.98), (.47, 1.95), (.51, 1.96), (.55, 1.90), (.64, 1.89)),
        ((.49, 1.80), (.54, 1.82), (.57, 1.76), (.64, 1.77), (.71, 1.69)),
        ((.48, 1.64), (.54, 1.63), (.58, 1.57), (.65, 1.57), (.71, 1.50)),
        ((.45, 1.49), (.49, 1.43), (.54, 1.44), (.60, 1.36)),
        ((.44, 1.34), (.48, 1.34), (.50, 1.29), (.53, 1.29)),
    )
    main = ((.47, 2.13), (.45, 2.04), (.43, 1.98), (.45, 1.91), (.43, 1.86), (.49, 1.80),
            (.46, 1.74), (.48, 1.64), (.43, 1.59), (.45, 1.49), (.40, 1.45),
            (.44, 1.34), (.39, 1.30), (.40, 1.22), (.35, 1.16), (.35, 1.10), (.29, 1.03))
    for sign in (-1, 1):
        def points(path):
            return art.points([(sign * x, y) for x, y in path])

        for polygon, color in facets:
            art.draw.polygon(points(polygon), fill=color)
        for branch in branches:
            stepped_path(art.draw, points(branch), (STORM_VOID, GOLD_AMBER, GOLD_BRIGHT))
        stepped_path(art.draw, points(main), (STORM_VOID, GOLD_AMBER, GOLD_WHITE))
    return art


def chrono_art(hand):
    bounds = (-1.02, 2.55, 100, 68)
    fuller = FaceArt(bounds, BRASS_DARK)
    runes = FaceArt(bounds, BRASS_VOID)
    inner = ((.47, 2.127), (.35, 2.14), (.27, 2.12), (.18, 2.15), (.09, 2.14),
             (.01, 2.10), (-.08, 2.115), (-.15, 2.075), (-.23, 2.08), (-.32, 2.01),
             (-.38, 2.00), (-.47, 1.95), (-.54, 1.94), (-.63, 1.86), (-.68, 1.85),
             (-.74, 1.77), (-.80, 1.71), (-.85, 1.61), (-.88, 1.57), (-.91, 1.46))
    if hand:
        inner = ((.45, 2.12), (.32, 2.10), (.25, 2.12), (.16, 2.085), (.08, 2.09),
                 (.00, 2.045), (-.08, 2.045), (-.16, 1.97), (-.26, 1.91),
                 (-.36, 1.89), (-.47, 1.77), (-.53, 1.74), (-.62, 1.64),
                 (-.69, 1.55), (-.75, 1.48), (-.83, 1.49), (-.88, 1.44))
    forks = (
        ((.27, 2.12), (.24, 2.17), (.18, 2.18)),
        ((-.15, 2.075), (-.20, 2.035), (-.25, 2.04)),
        ((-.47, 1.95), (-.53, 1.995), (-.59, 1.96)),
        ((-.74, 1.77), (-.76, 1.81), (-.80, 1.79)),
    ) if not hand else (
        ((.25, 2.12), (.20, 2.16), (.16, 2.16)),
        ((.00, 2.045), (-.04, 2.01), (-.09, 2.01)),
        ((-.26, 1.91), (-.31, 1.95), (-.36, 1.95)),
        ((-.53, 1.74), (-.58, 1.76), (-.61, 1.71)),
        ((-.75, 1.48), (-.77, 1.55), (-.81, 1.56)),
    )
    for path in forks:
        stepped_path(fuller.draw, fuller.points(path), (BRASS_VOID, BRASS_MID, BRASS_HI), (4, 2, 1))
    stepped_path(fuller.draw, fuller.points(inner), (BRASS_VOID, BRASS_HI, BRASS_BRIGHT))

    glyphs = (
        (((-1, -2), (1, 0), (-1, 2)), ((0, 0), (-2, 0))),
        (((-1, 2), (-1, -2), (1, 0), (-1, 0)),),
        (((0, -2), (1, 0), (0, 2), (-1, 0), (0, -2)),),
        (((-1, -2), (-1, 2)), ((-1, 0), (1, -2)), ((-1, 1), (1, 1))),
    )
    placements = ((.37, 2.29), (.24, 2.27), (.11, 2.25), (-.02, 2.23),
                  (-.15, 2.20), (-.27, 2.15), (-.39, 2.10), (-.51, 2.04),
                  (-.62, 1.98), (-.71, 1.91), (-.79, 1.82))
    if hand:
        placements = ((.37, 2.22), (.24, 2.21), (.11, 2.20), (-.02, 2.16),
                      (-.15, 2.11), (-.27, 2.04), (-.39, 1.98), (-.50, 1.88),
                      (-.60, 1.79), (-.70, 1.64), (-.78, 1.55))
    for i, (x, y) in enumerate(runes.points(placements)):
        for stroke in glyphs[i % len(glyphs)]:
            runes.draw.line([(x + dx, y + dy) for dx, dy in stroke], fill=CHRONO_TEAL)
        runes.draw.point((x, y), fill=CHRONO_GLOW)
    return fuller, runes


def beveled(size, face, palette, index):
    level = min(len(palette) - 1, max(0, index + FACE_LIGHT[face]))
    tile = Image.new('RGB', size, palette[level])
    w, h = size
    d = ImageDraw.Draw(tile)
    if w >= 2 or h >= 2:
        d.line((0, 0, w - 1, 0) if w >= 2 else (0, 0, 0, 0),
               fill=palette[min(len(palette) - 1, level + 1)])
    if w >= 3 and h >= 3:
        d.line((1, h - 1, w - 1, h - 1), fill=palette[max(0, level - 1)])
    return tile, d


def crystal_face(size, face, kind, color):
    w, h = size
    level = CRYSTAL.index(color)
    if kind in ('crys_blade_main', 'crys_energy') and face in FRONT_BACK:
        tile = Image.new('RGB', size, CRYS_BASE)
        d = ImageDraw.Draw(tile)
        cx = w // 2
        if kind == 'crys_energy':
            d.rectangle((0, 0, w - 1, h - 1), fill=CRYS_MID)
            for a, b in ((0, .19), (.27, .54), (.61, .85), (.90, 1)):
                d.line((cx, round(a * (h - 1)), cx, round(b * (h - 1))), fill=CRYS_GLOW)
            for y in (.09, .44, .73, .97):
                d.point((cx, round(y * (h - 1))), fill=CRYS_WHITE)
            return tile
        for points, fill in (
            (((0, 0), (cx, 0), (cx, h - 1), (0, h - 1)), CRYS_DEEP),
            (((0, h // 4), (cx, 0), (cx, h // 2)), CRYS_MID),
            (((cx, h // 4), (w - 1, 0), (w - 1, h // 2), (cx, 2 * h // 3)), CRYS_BRIGHT),
            (((0, 2 * h // 3), (cx, h // 2), (cx, h - 1)), CRYS_MID),
            (((cx, h - 1), (w - 1, h // 2), (w - 1, h - 1)), CRYS_MID),
        ):
            d.polygon(points, fill=fill)
        d.line((0, h // 5, 0, h // 2), fill=CRYS_BRIGHT)
        d.line((w - 1, h // 2, w - 1, h - 1), fill=CRYS_GLOW)
        d.line((cx, 0, cx, h - 1), fill=CRYS_GLOW)
        return tile
    level = min(level, len(CRYSTAL) - 2)
    tile, d = beveled(size, face, CRYSTAL, level)
    if w >= 2 and h >= 2:
        light = min(len(CRYSTAL) - 1, max(1, level + FACE_LIGHT[face] + 1))
        d.polygon(((0, 0), (w - 1, 0), (0, h - 1)), fill=CRYSTAL[light])
        if w >= 3 and h >= 3:
            d.line((0, 0, 0, h - 2), fill=CRYSTAL[min(light + 1, len(CRYSTAL) - 1)])
    return tile


def grip(size, face, palette, period):
    tile, d = beveled(size, face, palette, 1)
    w, h = size
    if face in WALLS:
        offset = WALLS.index(face)
        for y in range(-period + offset, h, period):
            d.line((0, y + 1, w - 1, y - 1), fill=palette[0], width=2)
            d.line((0, y, w - 1, y - 2), fill=palette[2])
            d.point((0, y), fill=palette[3])
    return tile


def clock_face(size, small):
    w, h = size
    tile = Image.new('RGB', size, BRASS_DARK)
    d = ImageDraw.Draw(tile)
    d.ellipse((0, 0, w - 1, h - 1), fill=BRASS_GOLD)
    d.arc((0, 0, w - 1, h - 1), 185, 310, fill=BRASS_BRIGHT)
    d.ellipse((1, 1, w - 2, h - 2), fill=DIAL_RIM)
    d.ellipse((2, 2, w - 3, h - 3), fill=DIAL_IVORY)
    cx, cy = w // 2, h // 2
    if not small:
        digits = {
            '1': ('010', '110', '010', '010', '111'),
            '2': ('110', '001', '010', '100', '111'),
            '3': ('110', '001', '010', '001', '110'),
            '6': ('011', '100', '110', '101', '010'),
            '9': ('010', '101', '011', '001', '110'),
        }
        for glyph, x, y in (('1', 6, 2), ('2', 10, 2), ('3', 15, 8), ('6', 9, 13), ('9', 2, 8)):
            for row, pixels in enumerate(digits[glyph]):
                for col, mark in enumerate(pixels):
                    if mark == '1':
                        d.point((x + col, y + row), fill=DIAL_HAND)
        d.point(((5, 4), (14, 4), (16, 6), (16, 14), (14, 16), (5, 16), (3, 14), (3, 6)),
                fill=DIAL_HAND)
    d.line((cx - (2 if small else 3), cy - 3, cx, cy), fill=DIAL_HAND)
    d.line((cx, cy, cx + (1 if small else 4), cy - (2 if small else 3)), fill=DIAL_HAND)
    d.point((cx, cy), fill=CHRONO_TEAL)
    return tile


def paint_weapon_face(box, face, projections):
    kind, color = box['kind'], tuple(box['color'])
    vertices = np.asarray(FACE_VERTS[face]) * box['s']
    w = np.linalg.norm(vertices[3] - vertices[0])
    h = np.linalg.norm(vertices[1] - vertices[0])
    size = (max(1, round(w * PPU)), max(1, round(h * PPU)))
    if face in FRONT_BACK and kind in projections:
        # Narrow surfaces need enough samples to resolve the shared line art.
        width, height = (max(1, math.ceil(n * PROJECTION_PPU)) for n in (w, h))
        return projections[kind].sample(box, face, width, height)
    if kind == 'storm_haft':
        return grip((3, size[1]), face, (STORM_VOID, DRAGON_DARK, BRONZE_M_HI, GOLD_EDGE), 3) if face in WALLS else beveled(size, face, STEEL, 1)[0]
    if kind == 'crys_grip':
        return grip((3, max(4, size[1])), face, OBSIDIAN, 2) if face in WALLS else beveled(size, face, OBSIDIAN, 1)[0]
    if kind == 'crys_guard':
        return beveled(size, face, OBSIDIAN, OBSIDIAN.index(color))[0]
    if kind.startswith('crys_'):
        if face in FRONT_BACK:
            size = {'crys_blade_main': (7, size[1]), 'crys_energy': (3, size[1])}.get(
                kind, (max(3, size[0]), max(3, size[1])) if kind == 'crys_heart'
                else (max(2, size[0]), max(2, size[1])))
        return crystal_face(size, face, kind, color)
    if kind == 'storm_eye' and face in FRONT_BACK:
        tile = Image.new('RGB', (3, 2), GOLD_AMBER)
        tile.putpixel((1, 0), GOLD_WHITE)
        tile.putpixel((2 if face == 'north' else 0, 0), GOLD_BRIGHT)
        return tile
    if kind in ('storm_dragon', 'storm_bone', 'storm_horn', 'storm_snout'):
        steel = STEEL if color in STEEL else STORM_GOLD
        level = steel.index(color) if color in steel else 1
        if kind == 'storm_dragon' and face in FRONT_BACK and max(w, h) >= .095:
            size = max(4, size[0]), max(3, size[1])
            a, b = size
            dark, mid, rim = (steel[min(len(steel) - 1, max(0, level + offset))] for offset in (-1, 0, 1))
            tile = Image.new('RGB', size, dark)
            d = ImageDraw.Draw(tile)
            d.polygon(((0, 0), (a - 1, 0), (a - 2, 1), (a // 2, b - 1), (1, b - 2)), fill=mid)
            d.line((0, 0, a - 2, 0), fill=rim)
            if b >= 4:
                d.point((0, b - 2), fill=rim)
                d.point((a - 1, b - 1), fill=mid)
            return tile
        if face in FRONT_BACK and kind in ('storm_horn', 'storm_snout', 'storm_bone'):
            size = max(2, size[0]), max(2, size[1])
        if kind in ('storm_horn', 'storm_snout'):
            level = min(level, len(steel) - 2)
        return beveled(size, face, steel, level)[0]
    if kind == 'storm_blade_web':
        return beveled(size, face, (STORM_VOID, STORM_DARK, STORM_IRON), 1)[0]
    if kind.startswith('storm_'):
        index = STORM_GOLD.index(color) if color in STORM_GOLD else 0
        if kind in ('storm_edge', 'storm_feather', 'storm_vein') and face in FRONT_BACK:
            tile, d = beveled((max(2, size[0]), max(2, size[1])), face, STORM_GOLD, max(0, index - 2))
            x, y = tile.size
            d.line((0, 0, x - 1, 0), fill=STORM_GOLD[min(index, 4)])
            d.point((0, 0), fill=STORM_GOLD[min(index + 1, 5)])
            return tile
        return beveled(size, face, STORM_GOLD, index)[0]
    if kind in ('chrono_dial', 'chrono_subdial') and face in FRONT_BACK:
        small = kind == 'chrono_subdial'
        return clock_face((8, 8) if small else (20, 20), small)
    if kind == 'chrono_shaft' and face in WALLS:
        tile, d = beveled((3, size[1]), face, BRASS, 2)
        for y in range(3, size[1] - 1, 5):
            d.line((0, y, 2, y), fill=BRASS_VOID)
            d.line((0, y + 1, 2, y + 1), fill=BRASS_GOLD)
            d.point((1, y + 2), fill=CHRONO_TEAL)
        return tile
    if color in (CHRONO_TEAL, CHRONO_GLOW):
        return beveled(size, face, (BRASS_VOID, CHRONO_TEAL, CHRONO_GLOW), 1)[0]
    index = BRASS.index(color) if color in BRASS else 2
    if face in FRONT_BACK and kind in ('chrono_gear', 'chrono_spine', 'chrono_blade_edge'):
        minimum = 3 if kind != 'chrono_blade_edge' and min(w, h) >= .075 else 2
        size = max(minimum, size[0]), max(minimum, size[1])
        tile, d = beveled(size, face, BRASS, min(index, 5))
        w, h = size
        if w >= 3 and h >= 3:
            d.line((0, h - 1, w - 1, h - 1), fill=BRASS_DARK)
            d.point((w - 2, 1), fill=BRASS_HI if index < 4 else BRASS_BRIGHT)
            if w >= 4:
                d.point((1, h - 2), fill=BRASS_HI)
            if h >= 4:
                d.line((1, h - 2, w - 1, h - 2), fill=BRASS_BASE)
        return tile
    return beveled(size, face, BRASS, index)[0]


def paint_atlas(boxes, projections):
    tiles = {}
    face_keys = []
    for box in boxes:
        faces = {}
        for face in FACE_NORMAL:
            tile = paint_weapon_face(box, face, projections)
            key = (tile.size, tile.tobytes())
            tiles.setdefault(key, tile)
            faces[face] = key
        face_keys.append(faces)
    positions, edge = shelf_pack([tile.size for tile in tiles.values()], pad=1)
    if edge > 256:
        raise ValueError(f'Weapon texture atlas exceeds 256 pixels: {edge}')
    atlas = Image.new('RGBA', (edge, edge))
    uvs = {}
    for (key, tile), (x, y) in zip(tiles.items(), positions):
        w, h = tile.size
        padded = Image.fromarray(np.pad(np.asarray(tile), ((1, 1), (1, 1), (0, 0)), mode='edge'))
        atlas.paste(padded, (x - 1, y - 1))
        uvs[key] = [round(n * 16 / edge, 5) for n in (x, y, x + w, y + h)]
    faces = [{face: {'texture': '#t', 'uv': uvs[key]} for face, key in entry.items()} for entry in face_keys]
    return atlas, faces


# Element groups from the checked-in 1.5.2 hand models. Their existing
# geometry differs from the detailed held models and has its own atlas.
def hand_materials(weapon):
    if weapon == 'stormcaller':
        return [
            ('storm_dragon', DRAGON_DARK), ('storm_dragon', DRAGON_STEEL),
            ('storm_core', GOLD_BRIGHT), ('storm_bone', BRONZE_MUTE),
            ('storm_haft', STORM_VOID), *[('storm_bone', BRONZE_MUTE)] * 2,
            ('storm_edge', GOLD_EDGE), ('storm_dragon', DRAGON_STEEL),
            ('storm_vein', GOLD_WHITE), *[('storm_dragon', DRAGON_STEEL)] * 2,
            ('storm_snout', DRAGON_RIM), ('storm_snout', DRAGON_DARK),
            ('storm_eye', GOLD_WHITE), ('storm_horn', DRAGON_HI),
            ('storm_horn', DRAGON_STEEL), ('storm_horn', DRAGON_RIM),
        ] + [
            *[('storm_bone', DRAGON_STEEL)] * 2, ('storm_edge', GOLD_BRIGHT),
            *[('storm_bone', DRAGON_STEEL)] * 2, *[('storm_blade_web', STORM_VOID)] * 4,
            *[('storm_vein', GOLD_BRIGHT)] * 3, *[('storm_feather', GOLD_BRIGHT)] * 6,
        ] * 2
    if weapon == 'crystal_greatsword':
        return [
            ('crys_shard', CRYS_WHITE), *[('crys_shard', CRYS_BRIGHT)] * 3,
            ('crys_blade_main', CRYS_BASE), ('crys_energy', CRYS_GLOW),
            *[('crys_shard', CRYS_BRIGHT)] * 2, *[('crys_shard', CRYS_MID)] * 4,
            *[('crys_shard', CRYS_BASE)] * 2,
            ('crys_guard', OBS_BASE), ('crys_guard', OBS_GOLD), ('crys_heart', CRYS_GLOW),
            *[('crys_guard', OBS_GOLD), ('crys_shard', CRYS_GLOW),
              ('crys_shard', CRYS_BRIGHT), ('crys_shard', CRYS_MID)] * 2,
            ('crys_grip', OBS_DARK), ('crys_guard', OBS_GOLD), ('crys_shard', CRYS_BRIGHT),
            ('crys_heart', CRYS_GLOW),
        ]
    return [
        ('chrono_blade_edge', BRASS_GOLD), ('chrono_gear', BRASS_MID),
        ('chrono_subdial', DIAL_IVORY), ('chrono_gear', BRASS_GOLD),
        ('chrono_shaft', BRASS_DARK), *[('chrono_gear', BRASS_GOLD)] * 5,
        ('chrono_spine', BRASS_MID), ('chrono_gear', BRASS_MID), ('chrono_gear', BRASS_GOLD),
        ('chrono_dial', DIAL_IVORY), ('chrono_gear', BRASS_GOLD), ('chrono_subdial', DIAL_IVORY),
        ('chrono_gear', BRASS_MID), ('chrono_gear', BRASS_BASE),
        *[('chrono_blade_edge', BRASS_HI)] * 3, ('chrono_gear', BRASS_MID), ('chrono_gear', BRASS_BASE),
        *[('chrono_spine', BRASS_BASE)] * 4, ('chrono_blade_edge', BRASS_GOLD),
        *[('chrono_blade_edge', BRASS_HI)] * 4,
        *[('chrono_runes', BRASS_VOID)] * 7, *[('chrono_fuller', BRASS_DARK)] * 7,
    ]


def repaint_hand(pack, model_id, manifest, projections):
    weapon = model_id.rsplit('/', 1)[-1]
    assets = Path(pack) / 'assets' / NS
    path = assets / 'models' / 'item' / f'{model_id}_hand.json'
    model = json.loads(path.read_bytes())
    pivot = np.asarray(manifest['parts'][0]['pivot_blocks'])
    scale = manifest['model_units_per_block']
    boxes = []
    for element, (kind, color) in zip(model['elements'], hand_materials(weapon), strict=True):
        start, end = (np.asarray(element[key]) for key in ('from', 'to'))
        center = pivot + ((start + end) / 2 - 8) * [-1, 1, -1] / scale
        rotation = element.get('rotation')
        axis = rotation['axis'] if rotation else None
        turn = (axis, rotation['angle'] if axis == 'y' else -rotation['angle']) if rotation else None
        boxes.append({'c': center, 's': (end - start) / scale, 'rot': turn, 'kind': kind, 'color': color})
    atlas, faces = paint_atlas(boxes, projections)
    for element, mapped in zip(model['elements'], faces, strict=True):
        element['faces'] = mapped
    model['credit'] = 'make_weapon_models (weapons-detail-v2)'
    model['texture_size'] = list(atlas.size)
    model['textures']['t'] = f'{NS}:item/{model_id}/hand'
    atlas.save(assets / 'textures' / 'item' / model_id / 'hand.png', optimize=True)
    path.write_text(json.dumps(model, separators=(',', ':')) + '\n', encoding='utf-8', newline='\n')
    print(f'  hand: {len(boxes)} existing cuboids, atlas {atlas.width}x{atlas.height}')


def weapon_projections(model_id, hand=False):
    if model_id.endswith('/stormcaller'):
        return {'storm_blade_web': storm_web()}
    if model_id.endswith('/chrono_dagger'):
        fuller, runes = chrono_art(hand)
        return {'chrono_fuller': fuller, 'chrono_runes': runes}
    return {}




def emit_weapon_model_set(pack, model_id, parts_table, height_blocks, k_units_per_block,
                          credit='make_weapon_models (weapons-detail-v2)'):
    """Emit the detailed model with its repainted faces."""
    S_REF = np.array([-1.0, 1.0, -1.0])
    disp = 16.0 / k_units_per_block
    projections = weapon_projections(model_id)

    man = {
        'id': f'{NS}:{model_id}',
        'height_blocks': height_blocks,
        'resolution': PPU,
        'texels_per_voxel': 1,
        'model_units_per_block': k_units_per_block,
        'item_display_scale': disp,
        'front': '+Z (boss local, yaw 0 = south)',
        'note': 'Hand-designed weapons-detail geometry with v2 pixel textures from the original palette.',
        'parts': [],
    }

    total_cuboids = 0
    for pname, p in parts_table.items():
        piv = np.array(p['pivot'], float)
        boxes = p['boxes']
        atlas, faces = paint_atlas(boxes, projections)
        size = atlas.width
        elements = []
        for box, mapped in zip(boxes, faces, strict=True):
            center = 8.0 + k_units_per_block * S_REF * (np.asarray(box['c']) - piv)
            half = k_units_per_block * np.asarray(box['s']) / 2.0
            start = np.clip(center - half, -16.0, 32.0)
            end = np.clip(center + half, -16.0, 32.0)
            element = {'from': [round(float(x), 4) for x in start],
                       'to': [round(float(x), 4) for x in end]}
            if box['rot']:
                axis, angle = box['rot']
                element['rotation'] = {
                    'angle': float(angle if axis == 'y' else -angle),
                    'axis': axis,
                    'origin': [round(float(x), 4) for x in (start + end) / 2.0],
                }
            element['faces'] = mapped
            elements.append(element)

        tref = f'{NS}:item/{model_id}/{pname}'
        model_json = {
            'credit': credit,
            'texture_size': [size, size],
            'textures': {'t': tref, 'particle': tref},
            'elements': elements,
            'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}},
        }
        for sub in ('models/item', 'textures/item', 'items'):
            os.makedirs(os.path.join(pack, 'assets', NS, *sub.split('/'), *model_id.split('/')), exist_ok=True)
        with open(os.path.join(pack, 'assets', NS, 'models', 'item', *model_id.split('/'), f'{pname}.json'), 'w') as fh:
            json.dump(model_json, fh, separators=(',', ':'))
        atlas.save(
            os.path.join(pack, 'assets', NS, 'textures', 'item', *model_id.split('/'), f'{pname}.png'),
            optimize=True,
        )
        with open(os.path.join(pack, 'assets', NS, 'items', *model_id.split('/'), f'{pname}.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{model_id}/{pname}'}}, fh, indent=2)

        nc = len(elements)
        n_rot = sum(1 for b in p['boxes'] if b['rot'])
        total_cuboids += nc
        man['parts'].append({
            'name': pname,
            'item_model': f'{NS}:{model_id}/{pname}',
            'parent': p['parent'],
            'pivot_blocks': list(p['pivot']),
            'translation': list(p['pivot']),
            'scale': disp,
            'spin_axis': None,
            'mode': 'box',
            'cuboids': nc,
            'cuboids_voxel': nc - n_rot,
            'cuboids_obb': n_rot,
            'faces': nc * 6,
            'texture_px': size,
            'voxels': 0,
        })

    man['total_cuboids'] = total_cuboids
    man_dir = os.path.join(os.path.dirname(pack.rstrip('/\\')), 'manifests')
    os.makedirs(man_dir, exist_ok=True)
    manifest_path = Path(man_dir) / (model_id.replace('/', '__') + '.json')
    manifest_path.write_text(json.dumps(man, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'[{model_id}] {len(man["parts"])} parts, {total_cuboids} cuboids, atlas {size}x{size}, {k_units_per_block} u/block')
    repaint_hand(pack, model_id, man, weapon_projections(model_id, hand=True))
    return man


# ============================================================================= 1. STORMCALLER (<= 500 cuboids)
def define_stormcaller():
    """height = 2.2, k = 14.5, pivot = [0.0, 0.6, 0.0].
    High-detail Spread-Wing Dragon Battleaxe (faithful to weapon_tempest-battleaxe.zip):
      1. Open-frame gunmetal-iron & gold diamond storm-lantern pommel (y = 0.00..0.48)
      2. Pitch-black haft (y = 0.48..1.18) with diagonal golden spiral wrap & coiled dragon tail
      3. Sculpted 3D central dragon in gunmetal-steel & dark obsidian (y = 1.18..2.11):
         - Pelvis/torso clasps, muscular chest with V-lightning core, S-curved armored neck with dorsal spines
         - Fierce 3D dragon head facing -X: snout, nostrils, steel brow ridge, recessed glowing eye, roaring
           upper/lower jaws with golden mouth interior & fangs, 3D cheek plates, 2 swept-back crown horns & frills
      4. Spread dragon wings (symmetric ±X):
         - Thick 3D gunmetal/obsidian wing-arm bone arch + upper thumb claw + lower inner bone arch + 4 finger bones
         - Recessed pitch-black wing membrane panels filling the wing interior
         - Thin, crisp 3D golden lightning veins running along the dragon neck, chest, wing arms & 4 finger bones
         - Layered (amber-bronze -> gold -> white-gold) continuous serrated axe blade edge with 6 downward-swept
           sawtooth serrations along the outer arc.
    """
    P = new_part_table()
    add_part(P, 'held', None, (0.0, 0.6, 0.0))

    # --- A. Open-Frame Gunmetal-Iron & Gold Storm-Lantern Pommel (y = 0.00..0.48) ---
    # Stepped bottom needle spike (y = 0.00..0.21): dark gunmetal outer steps + thin golden core
    add_box(P, 'held', ( 0.000, 0.035, 0.0), (0.016, 0.070, 0.024), DRAGON_DARK,  None, 'storm_dragon')
    add_box(P, 'held', ( 0.000, 0.095, 0.0), (0.036, 0.065, 0.056), DRAGON_STEEL, None, 'storm_dragon')
    add_box(P, 'held', ( 0.000, 0.148, 0.0), (0.074, 0.058, 0.088), DRAGON_DARK,  None, 'storm_dragon')
    add_box(P, 'held', ( 0.000, 0.188, 0.0), (0.108, 0.042, 0.118), DRAGON_STEEL, None, 'storm_dragon')
    add_box(P, 'held', ( 0.000, 0.135, 0.0), (0.018, 0.135, 0.124), GOLD_BRIGHT,  None, 'storm_core')
    # Glowing golden triangular core recessed inside bottom of lantern window (y = 0.21..0.27, dz = 0.112)
    add_box(P, 'held', ( 0.000, 0.240, 0.0), (0.056, 0.044, 0.112), GOLD_EDGE,    None, 'storm_edge')
    add_box(P, 'held', ( 0.000, 0.232, 0.0), (0.032, 0.034, 0.120), GOLD_WHITE,   None, 'storm_core')
    for sx in (1, -1):
        # Thick gunmetal-obsidian outer diamond struts (dz = 0.132, framing the hollow cross window at y = 0.27..0.36)
        add_box(P, 'held', (sx * 0.060, 0.240, 0.0), (0.036, 0.105, 0.132), DRAGON_DARK,  ('z', -sx * 45.0), 'storm_dragon')
        add_box(P, 'held', (sx * 0.060, 0.378, 0.0), (0.036, 0.105, 0.132), DRAGON_DARK,  ('z',  sx * 45.0), 'storm_dragon')
        # Thin golden inner diamond liner recessed at dz = 0.116 so dark outer frame stays prominent in 3/4 view!
        add_box(P, 'held', (sx * 0.040, 0.252, 0.0), (0.016, 0.080, 0.116), GOLD_BRIGHT,  ('z', -sx * 45.0), 'storm_edge')
        add_box(P, 'held', (sx * 0.040, 0.365, 0.0), (0.016, 0.080, 0.116), GOLD_BRIGHT,  ('z',  sx * 45.0), 'storm_edge')
        # Lateral gunmetal-steel diamond corner finials at |x| = 0.105..0.135, y = 0.308
        add_box(P, 'held', (sx * 0.108, 0.308, 0.0), (0.052, 0.058, 0.136), DRAGON_STEEL, None,              'storm_dragon')
        add_box(P, 'held', (sx * 0.128, 0.308, 0.0), (0.022, 0.036, 0.110), DRAGON_RIM,   None,              'storm_dragon')
        add_box(P, 'held', (sx * 0.078, 0.308, 0.0), (0.018, 0.022, 0.122), GOLD_BRIGHT,  None,              'storm_core')
    # Top lantern cap & gunmetal cross-collar at y = 0.42..0.48
    add_box(P, 'held', ( 0.000, 0.422, 0.0), (0.112, 0.038, 0.136), DRAGON_DARK,  None, 'storm_dragon')
    add_box(P, 'held', ( 0.000, 0.456, 0.0), (0.172, 0.036, 0.144), DRAGON_STEEL, None, 'storm_dragon')
    add_box(P, 'held', ( 0.000, 0.438, 0.0), (0.022, 0.070, 0.150), GOLD_BRIGHT,  None, 'storm_core')

    # --- B. Pitch-Black Haft + 3D Coiled Dragon Tail & Diagonal Golden Spiral (y = 0.47..1.18) ---
    # Haft core with a spiral wrap painted across the four side faces.
    add_box(P, 'held', ( 0.000, 0.825, 0.0), (0.068, 0.720, 0.068), STORM_VOID,   None, 'storm_haft')
    # Subtle 3D relief ribs along the haft (alternating gunmetal tail-scale rings & thin golden spiral nodes)
    for idx, sy in enumerate(np.linspace(0.52, 1.10, 11)):
        if idx % 2 == 0:
            add_box(P, 'held', (0.000, sy, 0.0), (0.076, 0.022, 0.074), DRAGON_DARK, ('z', -22.5), 'storm_dragon')
        else:
            add_box(P, 'held', (0.000, sy, 0.0), (0.074, 0.016, 0.076), GOLD_EDGE,   ('z',  22.5), 'storm_edge')

    # --- C. Upper Haft Tiered Dragon Torso/Pelvis Clasps (y = 1.15..1.46) ---
    for cy, cw, cd, col_c in (
        (1.175, 0.128, 0.112, DRAGON_STEEL),
        (1.235, 0.138, 0.120, DRAGON_DARK),
        (1.305, 0.168, 0.134, DRAGON_STEEL),
        (1.365, 0.132, 0.122, DRAGON_DARK),
        (1.425, 0.256, 0.140, DRAGON_STEEL),
    ):
        add_box(P, 'held', (0.000, cy, 0.0), (cw, 0.044, cd), col_c, None, 'storm_dragon')
    for sx in (1, -1):
        add_box(P, 'held', (sx * 0.066, 1.295, 0.0), (0.042, 0.065, 0.126), DRAGON_RIM, ('z', -sx * 22.5), 'storm_dragon')
        add_box(P, 'held', (sx * 0.092, 1.415, 0.0), (0.062, 0.050, 0.130), DRAGON_RIM, ('z', -sx * 22.5), 'storm_dragon')
    # Thin central vertical golden lightning vein up the dark torso clasps (y = 1.16..1.50)
    add_box(P, 'held', ( 0.000, 1.330, 0.0), (0.018, 0.340, 0.148), GOLD_BRIGHT, None, 'storm_core')

    # --- D. Sculpted 3D Central Dragon Chest, S-Neck & Fierce Roaring Head (y = 1.44..2.11) ---
    # 1. Dragon Chest / Breastplate (y = 1.44..1.66) + crisp V-shaped golden lightning heart
    add_box(P, 'held', ( 0.000, 1.540, 0.0), (0.175, 0.195, 0.144), DRAGON_DARK,  None, 'storm_dragon')
    add_box(P, 'held', ( 0.000, 1.545, 0.0), (0.115, 0.175, 0.160), DRAGON_STEEL, None, 'storm_dragon')
    add_box(P, 'held', ( 0.000, 1.490, 0.0), (0.024, 0.070, 0.170), GOLD_WHITE,   None, 'storm_core')
    for sx in (1, -1):
        add_box(P, 'held', (sx * 0.036, 1.535, 0.0), (0.018, 0.100, 0.168), GOLD_BRIGHT, ('z', -sx * 22.5), 'storm_core')
        add_box(P, 'held', (sx * 0.088, 1.515, 0.0), (0.076, 0.018, 0.154), GOLD_EDGE,   ('z',  sx * 22.5), 'storm_edge')

    # 2. Dragon S-Curved Neck (y = 1.60..1.88, gunmetal-steel dorsal scales + thin golden throat lightning)
    neck_segs = [
        ( 0.015, 1.630, 0.138, 0.072, 0.150, -22.5),
        ( 0.032, 1.685, 0.132, 0.072, 0.156, -22.5),
        ( 0.040, 1.740, 0.128, 0.072, 0.162,   0.0),
        ( 0.024, 1.795, 0.126, 0.072, 0.168,  22.5),
        (-0.005, 1.845, 0.122, 0.068, 0.172,  22.5),
    ]
    for nx, ny, nw, nh, nd, nrot in neck_segs:
        rot_t = ('z', nrot) if nrot != 0.0 else None
        add_box(P, 'held', (nx, ny, 0.0), (nw, nh, nd), DRAGON_DARK, rot_t, 'storm_dragon')
        add_box(P, 'held', (nx - 0.010, ny + 0.006, 0.0), (nw * 0.65, nh * 0.80, nd + 0.016), DRAGON_STEEL, rot_t, 'storm_dragon')
    # Thin blazing golden lightning artery winding up the dragon's throat (width = 0.018)
    for vx, vy, vrot in (
        ( 0.008, 1.605, -22.5),
        ( 0.026, 1.660, -22.5),
        ( 0.042, 1.718,   0.0),
        ( 0.032, 1.772,  22.5),
        ( 0.002, 1.818,  45.0),
    ):
        add_box(P, 'held', (vx, vy, 0.0), (0.018, 0.060, 0.182), GOLD_WHITE, ('z', vrot) if vrot else None, 'storm_core')

    # Swept-back dorsal neck mane/frill spikes along the right (+X) back of the neck (y = 1.65..1.92)
    for fy, fx, fw, fh, frot in (
        (1.660, 0.088, 0.088, 0.034, -22.5),
        (1.725, 0.108, 0.098, 0.036, -22.5),
        (1.790, 0.124, 0.110, 0.038, -22.5),
        (1.855, 0.134, 0.116, 0.040,   0.0),
        (1.915, 0.130, 0.120, 0.040, -22.5),
    ):
        add_box(P, 'held', (fx, fy, 0.0), (fw, fh, 0.122), DRAGON_STEEL, ('z', frot) if frot else None, 'storm_dragon')
        add_box(P, 'held', (fx + 0.026, fy + 0.008, 0.0), (fw * 0.52, fh * 0.58, 0.130), DRAGON_RIM, ('z', -22.5), 'storm_dragon')

    # 3. Sculpted 3D Dragon Head facing -X (y = 1.73..2.11, x = -0.252..+0.168)
    # Main cranium & 3D gunmetal-steel cheek armor ridges
    add_box(P, 'held', (-0.055, 1.910, 0.0), (0.138, 0.098, 0.184), DRAGON_DARK,  None,         'storm_dragon')
    add_box(P, 'held', (-0.035, 1.895, 0.0), (0.115, 0.065, 0.208), DRAGON_STEEL, ('z',  22.5), 'storm_dragon')
    add_box(P, 'held', (-0.015, 1.882, 0.0), (0.085, 0.038, 0.218), DRAGON_RIM,   ('z',  22.5), 'storm_dragon')
    # Upper snout bridge & nasal horn (sloping down-left to x = -0.248, y = 1.86..1.94)
    add_box(P, 'held', (-0.132, 1.912, 0.0), (0.125, 0.058, 0.156), DRAGON_STEEL, ('z',  22.5), 'storm_snout')
    add_box(P, 'held', (-0.192, 1.892, 0.0), (0.098, 0.048, 0.132), DRAGON_RIM,   ('z',  22.5), 'storm_snout')
    add_box(P, 'held', (-0.228, 1.876, 0.0), (0.044, 0.040, 0.108), DRAGON_STEEL, None,         'storm_snout')
    add_box(P, 'held', (-0.212, 1.908, 0.0), (0.032, 0.030, 0.118), DRAGON_HI,    ('z', -22.5), 'storm_snout')
    # Fierce angled steel brow ridge & recessed blazing golden dragon eye at (-0.105, 1.908)
    add_box(P, 'held', (-0.102, 1.938, 0.0), (0.108, 0.030, 0.202), DRAGON_HI,    ('z',  22.5), 'storm_dragon')
    add_box(P, 'held', (-0.106, 1.908, 0.0), (0.046, 0.022, 0.196), GOLD_WHITE,   ('z',  22.5), 'storm_eye')
    # Upper jaw glowing golden palate & sharp downward upper fangs (x = -0.22..-0.09, y = 1.83..1.87)
    add_box(P, 'held', (-0.162, 1.860, 0.0), (0.120, 0.022, 0.122), GOLD_BRIGHT,  None,         'storm_edge')
    for tx, ty, th in ((-0.218, 1.840, 0.026), (-0.188, 1.842, 0.024), (-0.158, 1.844, 0.022), (-0.128, 1.846, 0.022)):
        add_box(P, 'held', (tx, ty, 0.0), (0.014, th, 0.112), GOLD_WHITE, None, 'storm_core')
    # Roaring lower jaw / mandible (angled down-left from (-0.06, 1.83) to chin tip at (-0.185, 1.745))
    add_box(P, 'held', (-0.115, 1.790, 0.0), (0.135, 0.042, 0.140), DRAGON_STEEL, ('z',  22.5), 'storm_dragon')
    add_box(P, 'held', (-0.155, 1.765, 0.0), (0.088, 0.036, 0.118), DRAGON_RIM,   ('z',  22.5), 'storm_dragon')
    add_box(P, 'held', (-0.125, 1.808, 0.0), (0.100, 0.018, 0.126), GOLD_EDGE,    ('z',  22.5), 'storm_edge')
    for tx, ty in ((-0.168, 1.788), (-0.138, 1.800), (-0.108, 1.812)):
        add_box(P, 'held', (tx, ty, 0.0), (0.014, 0.022, 0.108), GOLD_WHITE, ('z', 22.5), 'storm_core')
    # Two multi-segment swept-back gunmetal-steel crown horns (y = 1.95..2.11)
    add_box(P, 'held', (-0.025, 1.982, 0.0), (0.090, 0.036, 0.142), DRAGON_RIM,   ('z', -22.5), 'storm_horn')
    add_box(P, 'held', ( 0.022, 2.022, 0.0), (0.070, 0.030, 0.118), DRAGON_HI,    ('z', -22.5), 'storm_horn')
    add_box(P, 'held', ( 0.048, 2.058, 0.0), (0.030, 0.042, 0.092), DRAGON_HI,    None,         'storm_horn')
    add_box(P, 'held', ( 0.045, 1.962, 0.0), (0.105, 0.040, 0.146), DRAGON_STEEL, ('z', -22.5), 'storm_horn')
    add_box(P, 'held', ( 0.105, 2.002, 0.0), (0.090, 0.034, 0.122), DRAGON_RIM,   ('z', -22.5), 'storm_horn')
    add_box(P, 'held', ( 0.145, 2.046, 0.0), (0.038, 0.058, 0.096), DRAGON_HI,    ('z', -22.5), 'storm_horn')
    add_box(P, 'held', ( 0.158, 2.086, 0.0), (0.022, 0.042, 0.076), DRAGON_HI,    None,         'storm_horn')

    # --- E. Spread Dragon Wings (Symmetric ±X: Gunmetal Bones, Black Membrane, Thin Veins, Serrated Golden Blade) ---
    for sx in (1, -1):
        # 1. Inner Wing Bone Arch (Wing Arm + Upper Wrist/Thumb Bone + Valley Spurs + Lower Inner Arch)
        add_box(P, 'held', (sx * 0.185, 1.545, 0.0), (0.145, 0.080, 0.126), DRAGON_DARK,  ('z', -sx * 22.5), 'storm_bone')
        add_box(P, 'held', (sx * 0.285, 1.605, 0.0), (0.125, 0.086, 0.130), DRAGON_STEEL, ('z', -sx * 22.5), 'storm_bone')
        add_box(P, 'held', (sx * 0.215, 1.635, 0.0), (0.050, 0.060, 0.132), DRAGON_RIM,   None,              'storm_bone')
        add_box(P, 'held', (sx * 0.295, 1.725, 0.0), (0.062, 0.060, 0.134), DRAGON_RIM,   ('z',  sx * 22.5), 'storm_bone')
        add_box(P, 'held', (sx * 0.305, 2.025, 0.0), (0.066, 0.088, 0.134), DRAGON_RIM,   None,              'storm_bone')
        for bx, by, bw, bh, brot, bcol in (
            (0.355, 1.710, 0.084, 0.145,   0.0, DRAGON_STEEL),
            (0.365, 1.830, 0.082, 0.145,   0.0, DRAGON_DARK),
            (0.362, 1.950, 0.084, 0.145, -22.5, DRAGON_STEEL),
            (0.378, 2.060, 0.086, 0.135, -22.5, DRAGON_RIM),
            (0.415, 2.130, 0.074, 0.085, -22.5, DRAGON_STEEL),
        ):
            add_box(P, 'held', (sx * bx, by, 0.0), (bw, bh, 0.128), bcol, ('z', sx * brot) if brot else None, 'storm_bone')
        # Sharp golden apex wing-claw horn at top of wing (|x| = 0.44..0.47, y = 2.14..2.20)
        add_box(P, 'held', (sx * 0.448, 2.168, 0.0), (0.034, 0.056, 0.096), GOLD_BRIGHT, None, 'storm_edge')
        add_box(P, 'held', (sx * 0.456, 2.188, 0.0), (0.020, 0.032, 0.076), GOLD_WHITE,  None, 'storm_vein')

        # Lower inner wing bone arch framing the concave under-wing cutout (|x|=0.22..0.34, y=1.01..1.50)
        for bx, by, bw, bh, brot, bcol in (
            (0.285, 1.460, 0.080, 0.115,  22.5, DRAGON_STEEL),
            (0.335, 1.375, 0.078, 0.120,   0.0, DRAGON_DARK),
            (0.338, 1.275, 0.074, 0.120,   0.0, DRAGON_STEEL),
            (0.308, 1.175, 0.070, 0.120, -22.5, DRAGON_RIM),
            (0.262, 1.085, 0.064, 0.110, -22.5, DRAGON_STEEL),
        ):
            add_box(P, 'held', (sx * bx, by, 0.0), (bw, bh, 0.120), bcol, ('z', sx * brot) if brot else None, 'storm_bone')
        add_box(P, 'held', (sx * 0.238, 1.425, 0.0), (0.044, 0.060, 0.124), DRAGON_RIM, None, 'storm_bone')

        # 2. Four Radiating 3D Gunmetal-Obsidian Finger Bones across the Wing Interior (dz = 0.108)
        finger_bones = [
            (0.455, 1.945, 0.145, 0.048, 22.5, DRAGON_STEEL), (0.555, 1.875, 0.145, 0.044, 22.5, DRAGON_DARK),
            (0.465, 1.745, 0.160, 0.052, 22.5, DRAGON_STEEL), (0.585, 1.655, 0.165, 0.048, 22.5, DRAGON_DARK),
            (0.455, 1.525, 0.155, 0.050, 22.5, DRAGON_STEEL), (0.565, 1.425, 0.155, 0.046, 22.5, DRAGON_DARK),
            (0.425, 1.315, 0.135, 0.046, 45.0, DRAGON_STEEL), (0.485, 1.215, 0.115, 0.042, 45.0, DRAGON_DARK),
        ]
        for fx, fy, fw, fh, frot, fcol in finger_bones:
            add_box(P, 'held', (sx * fx, fy, 0.0), (fw, fh, 0.108), fcol, ('z', sx * frot), 'storm_bone')

        # 3. Recessed Pitch-Black Wing Membrane Panels (distinct Z per panel 0.052/0.056)
        web_panels = [
            (0.450, 2.020, 0.140, 0.155, 0.052),
            (0.495, 1.900, 0.215, 0.155, 0.056),
            (0.535, 1.780, 0.275, 0.155, 0.052),
            (0.555, 1.660, 0.305, 0.155, 0.056),
            (0.555, 1.540, 0.310, 0.155, 0.052),
            (0.515, 1.420, 0.270, 0.155, 0.056),
            (0.465, 1.300, 0.210, 0.145, 0.052),
            (0.405, 1.190, 0.175, 0.135, 0.056),
            (0.325, 1.095, 0.135, 0.115, 0.052),
        ]
        for wx, wy, ww, wh, wd in web_panels:
            add_box(P, 'held', (sx * wx, wy, 0.0), (ww, wh, wd), STORM_VOID, None, 'storm_blade_web')

        # 4. Layered Outer Golden Serrated Wing-Pinion Blades (6 overlapping downward-swept sawtooth feather-blades!)
        # Inner dark-bronze / amber transition bevel (dz = 0.072, recessed behind dark finger bones dz = 0.108!)
        inner_bronze_arc = [
            (0.485, 2.075, 0.065, 0.075,  22.5, BRONZE_M_HI),
            (0.565, 1.975, 0.075, 0.095,  22.5, BRONZE_M_HI),
            (0.635, 1.855, 0.082, 0.110,  22.5, GOLD_AMBER),
            (0.685, 1.725, 0.085, 0.120,   0.0, GOLD_AMBER),
            (0.695, 1.595, 0.085, 0.125,   0.0, GOLD_AMBER),
            (0.665, 1.465, 0.082, 0.120,   0.0, GOLD_AMBER),
            (0.595, 1.345, 0.078, 0.110, -22.5, BRONZE_M_HI),
            (0.505, 1.235, 0.072, 0.105, -22.5, BRONZE_M_HI),
            (0.415, 1.135, 0.068, 0.095, -22.5, GOLD_AMBER),
            (0.325, 1.055, 0.062, 0.080, -45.0, GOLD_AMBER),
        ]
        for bx, by, bw, bh, brot, bcol in inner_bronze_arc:
            add_box(P, 'held', (sx * bx, by, 0.0), (bw, bh, 0.072), bcol, ('z', sx * brot) if brot else None, 'storm_feather_inner')

        # Mid Golden Pinion Blade Layer (dz = 0.084) + Razor Sawtooth Outer Edge (dz = 0.092)
        outer_blade_steps = [
            # Pinion 1 (y = 2.13 -> sharp tip at (0.695, 1.91), notch at y = 1.89)
            (0.495, 2.110, 0.052, 0.048, GOLD_EDGE,   GOLD_BRIGHT, 0.084),
            (0.545, 2.065, 0.064, 0.048, GOLD_EDGE,   GOLD_BRIGHT, 0.082),
            (0.595, 2.020, 0.074, 0.048, GOLD_EDGE,   GOLD_WHITE,  0.084),
            (0.640, 1.972, 0.078, 0.048, GOLD_BRIGHT, GOLD_WHITE,  0.082),
            (0.668, 1.925, 0.062, 0.044, GOLD_BRIGHT, GOLD_WHITE,  0.084),
            # Pinion 2 (y = 1.88 -> sharp tip at (0.785, 1.75), notch at y = 1.73)
            (0.678, 1.878, 0.078, 0.048, GOLD_EDGE,   GOLD_BRIGHT, 0.082),
            (0.720, 1.830, 0.088, 0.048, GOLD_BRIGHT, GOLD_WHITE,  0.084),
            (0.752, 1.782, 0.068, 0.046, GOLD_BRIGHT, GOLD_WHITE,  0.082),
            # Pinion 3 (y = 1.73 -> widest sharp tip at (0.814, 1.51), notch at y = 1.49)
            (0.732, 1.732, 0.088, 0.048, GOLD_EDGE,   GOLD_BRIGHT, 0.084),
            (0.758, 1.682, 0.088, 0.048, GOLD_EDGE,   GOLD_WHITE,  0.082),
            (0.772, 1.632, 0.082, 0.048, GOLD_BRIGHT, GOLD_WHITE,  0.084),
            (0.780, 1.582, 0.072, 0.048, GOLD_BRIGHT, GOLD_WHITE,  0.082),
            (0.788, 1.532, 0.054, 0.046, GOLD_BRIGHT, GOLD_WHITE,  0.084),
            # Pinion 4 (y = 1.48 -> sharp tip at (0.732, 1.35), notch at y = 1.34)
            (0.698, 1.482, 0.074, 0.048, GOLD_EDGE,   GOLD_BRIGHT, 0.082),
            (0.708, 1.432, 0.068, 0.048, GOLD_BRIGHT, GOLD_WHITE,  0.084),
            (0.706, 1.380, 0.054, 0.046, GOLD_BRIGHT, GOLD_WHITE,  0.082),
            # Pinion 5 & 6 (y = 1.33 -> smooth crescent sweep to inward-pointing beard tip at (0.25, 1.00))
            (0.625, 1.330, 0.078, 0.048, GOLD_EDGE,   GOLD_BRIGHT, 0.084),
            (0.592, 1.280, 0.078, 0.048, GOLD_BRIGHT, GOLD_WHITE,  0.082),
            (0.550, 1.230, 0.078, 0.048, GOLD_BRIGHT, GOLD_WHITE,  0.084),
            (0.502, 1.180, 0.076, 0.046, GOLD_EDGE,   GOLD_BRIGHT, 0.082),
            (0.450, 1.132, 0.074, 0.046, GOLD_BRIGHT, GOLD_WHITE,  0.084),
            (0.390, 1.085, 0.072, 0.044, GOLD_BRIGHT, GOLD_WHITE,  0.082),
            (0.322, 1.042, 0.068, 0.040, GOLD_BRIGHT, GOLD_WHITE,  0.084),
            (0.260, 1.010, 0.054, 0.034, GOLD_WHITE,  GOLD_WHITE,  0.082),
        ]
        for ox, oy, ow, oh, col_mid, col_tip, dz_m in outer_blade_steps:
            add_box(P, 'held', (sx * (ox - 0.014), oy, 0.0), (ow, oh, dz_m), col_mid, None, 'storm_feather')
            add_box(P, 'held', (sx * (ox + ow * 0.24), oy, 0.0), (ow * 0.48, oh * 0.90, dz_m + 0.008), col_tip, None, 'storm_edge')

        # 5. Thin, Crisp 3D Golden Lightning Veins across Shoulder, Wing Artery & All 4 Finger Bones (width ~ 0.014)
        wing_veins = [
            (0.165, 1.545, 0.088, 0.015, -22.5, GOLD_WHITE,  0.136),
            (0.245, 1.585, 0.094, 0.015, -22.5, GOLD_WHITE,  0.138),
            (0.335, 1.625, 0.094, 0.016, -22.5, GOLD_WHITE,  0.136),
            (0.285, 1.485, 0.068, 0.014,  22.5, GOLD_BRIGHT, 0.134),
            (0.382, 1.695, 0.016, 0.125,   0.0, GOLD_WHITE,  0.138),
            (0.392, 1.815, 0.016, 0.125,   0.0, GOLD_WHITE,  0.136),
            (0.392, 1.935, 0.015, 0.125, -22.5, GOLD_WHITE,  0.138),
            (0.408, 2.025, 0.014, 0.075, -22.5, GOLD_BRIGHT, 0.136),
            (0.375, 1.555, 0.016, 0.125,   0.0, GOLD_WHITE,  0.138),
            (0.382, 1.435, 0.015, 0.115,  22.5, GOLD_BRIGHT, 0.136),
            (0.455, 1.935, 0.105, 0.014,  22.5, GOLD_BRIGHT, 0.116),
            (0.545, 1.875, 0.105, 0.014,  22.5, GOLD_EDGE,   0.114),
            (0.465, 1.745, 0.115, 0.015,  22.5, GOLD_WHITE,  0.116),
            (0.565, 1.675, 0.115, 0.014,  22.5, GOLD_BRIGHT, 0.114),
            (0.645, 1.615, 0.085, 0.014,  22.5, GOLD_EDGE,   0.116),
            (0.455, 1.545, 0.115, 0.015,  22.5, GOLD_WHITE,  0.116),
            (0.545, 1.465, 0.115, 0.014,  22.5, GOLD_BRIGHT, 0.114),
            (0.615, 1.395, 0.085, 0.014,  22.5, GOLD_EDGE,   0.116),
            (0.425, 1.345, 0.014, 0.105, -22.5, GOLD_BRIGHT, 0.116),
            (0.465, 1.245, 0.014, 0.095, -22.5, GOLD_EDGE,   0.114),
            (0.395, 1.195, 0.014, 0.085,  22.5, GOLD_BRIGHT, 0.116),
        ]
        for vx, vy, vw, vh, vrot, vcol, vdz in wing_veins:
            add_box(P, 'held', (sx * vx, vy, 0.0), (vw, vh, vdz), vcol, ('z', sx * vrot) if vrot else None, 'storm_vein')

    return P


# ============================================================================= 2. CRYSTAL GREATSWORD (<= 300 cuboids)
def define_crystal_greatsword():
    """height = 2.3, k = 13.0, pivot = [0.0, 0.52, 0.0].
    High-detail Amethyst Crystal Greatsword (faithful to weapon_crystal-greatsword.zip):
      - Unrotated high-res central blade core (dz = 0.052) painted with broad crystal facets and a glowing spine
      - Irregularly protruding jagged crystal serration teeth along both edges (y = 0.14..1.70, dz = 0.040 / 0.032)
        using alias-free directional face shading
      - Ornate dark-obsidian & antique-bronze crossguard with central diamond heart crystal & 4 pairs of winged
        amethyst crystals matching the exact original silhouette
      - Original-proportion slim grip (width 0.042) with diagonal bronze-gold spiral wrap & diamond-spear pommel.
    """
    P = new_part_table()
    add_part(P, 'held', None, (0.0, 0.52, 0.0))

    # --- A. Razor-Sharp Stepped Crystal Blade Tip (y = 0.00..0.32, strictly distinct Z per step) ---
    tip_steps = [
        (0.020, 0.020, 0.040, 0.028, CRYS_WHITE,  'crys_shard'),
        (0.055, 0.036, 0.038, 0.034, CRYS_GLOW,   'crys_shard'),
        (0.095, 0.050, 0.046, 0.038, CRYS_BRIGHT, 'crys_shard'),
        (0.145, 0.064, 0.058, 0.042, CRYS_MID,    'crys_shard'),
        (0.205, 0.078, 0.066, 0.046, CRYS_BASE,   'crys_shard'),
        (0.270, 0.092, 0.068, 0.048, CRYS_BASE,   'crys_shard'),
    ]
    for ty, tw, th, td, tcol, tkind in tip_steps:
        add_box(P, 'held', (0.000, ty, 0.0), (tw, th, td), tcol, None, tkind)
    for sx in (1, -1):
        add_box(P, 'held', (sx * 0.018, 0.095, 0.0), (0.022, 0.088, 0.032), CRYS_WHITE,  ('z', -sx * 22.5), 'crys_shard')
        add_box(P, 'held', (sx * 0.030, 0.205, 0.0), (0.028, 0.120, 0.036), CRYS_BRIGHT, ('z', -sx * 22.5), 'crys_shard')

    # --- B. Continuous Faceted Royal-Violet Blade Core (dz = 0.052, with broad facets) ---
    add_box(P, 'held', (0.000, 1.010, 0.0), (0.104, 1.410, 0.052), CRYS_BASE,   None, 'crys_blade_main')
    # Raised central specular crystal ridge (dz = 0.062)
    add_box(P, 'held', (0.000, 0.960, 0.0), (0.020, 1.520, 0.062), CRYS_GLOW,   None, 'crys_energy')

    # --- C. Irregular, Organically Jagged Crystal Serration Teeth along Both Blade Edges (y = 0.16..1.70) ---
    serration_clusters = [
        # (y_right, y_left, x_reach_r, x_reach_l, h_main, rot_deg, col_main, col_tip, has_sub)
        (0.175, 0.188, 0.046, 0.048, 0.055, 22.5, CRYS_MID,    CRYS_WHITE,  False),
        (0.285, 0.270, 0.060, 0.058, 0.072, 22.5, CRYS_BASE,   CRYS_GLOW,   True),
        (0.420, 0.436, 0.076, 0.072, 0.088, 22.5, CRYS_MID,    CRYS_WHITE,  True),
        (0.515, 0.498, 0.065, 0.068, 0.058, 45.0, CRYS_DEEP,   CRYS_BRIGHT, False),
        (0.605, 0.620, 0.076, 0.078, 0.092, 22.5, CRYS_BASE,   CRYS_GLOW,   True),
        (0.710, 0.692, 0.066, 0.064, 0.060, 22.5, CRYS_DEEP,   CRYS_MID,    False),
        (0.805, 0.822, 0.078, 0.074, 0.090, 22.5, CRYS_MID,    CRYS_WHITE,  True),
        (0.905, 0.888, 0.065, 0.068, 0.058, 45.0, CRYS_DEEP,   CRYS_BRIGHT, False),
        (1.010, 1.026, 0.076, 0.078, 0.094, 22.5, CRYS_BASE,   CRYS_GLOW,   True),
        (1.120, 1.105, 0.066, 0.064, 0.060, 22.5, CRYS_DEEP,   CRYS_MID,    False),
        (1.235, 1.250, 0.078, 0.075, 0.092, 22.5, CRYS_MID,    CRYS_WHITE,  True),
        (1.340, 1.325, 0.066, 0.068, 0.058, 45.0, CRYS_DEEP,   CRYS_BRIGHT, False),
        (1.425, 1.410, 0.077, 0.075, 0.088, 22.5, CRYS_BASE,   CRYS_GLOW,   True),
        (1.560, 1.575, 0.085, 0.082, 0.096, 22.5, CRYS_MID,    CRYS_WHITE,  True),
        (1.685, 1.675, 0.094, 0.092, 0.084, 22.5, CRYS_BASE,   CRYS_BRIGHT, True),
    ]
    for yr, yl, xr, xl, hm, rdeg, cm, ct, has_sub in serration_clusters:
        for sx, sy, x_reach in ((1, yr, xr), (-1, yl, xl)):
            rot_m = ('z', -sx * rdeg) if rdeg != 0.0 else None
            add_box(P, 'held', (sx * (x_reach - 0.016), sy, 0.0), (0.034, hm, 0.042), cm, rot_m, 'crys_shard')
            add_box(P, 'held', (sx * (x_reach - 0.005), sy + 0.012, 0.0), (0.018, hm * 0.55, 0.036), ct, ('z', sx * 22.5), 'crys_shard')
            if has_sub:
                add_box(P, 'held', (sx * (x_reach - 0.012), sy - hm * 0.42, 0.0), (0.024, hm * 0.46, 0.032), CRYS_DEEP, ('z', -sx * 45.0), 'crys_shard')

    # --- D. Ornate Dark-Bronze Ricasso & Central Diamond Heart Crystal (y = 1.66..1.96) ---
    add_box(P, 'held', ( 0.000, 1.755, 0.0), (0.118, 0.130, 0.054), OBS_DARK,    None,              'crys_guard')
    add_box(P, 'held', ( 0.000, 1.710, 0.0), (0.024, 0.090, 0.066), OBS_GOLD_HI, None,              'crys_guard')
    for sx in (1, -1):
        add_box(P, 'held', (sx * 0.036, 1.735, 0.0), (0.024, 0.100, 0.064), OBS_GOLD,    ('z',  sx * 22.5), 'crys_guard')
        add_box(P, 'held', (sx * 0.068, 1.775, 0.0), (0.072, 0.024, 0.062), OBS_GOLD_HI, ('z', -sx * 22.5), 'crys_guard')
        add_box(P, 'held', (sx * 0.102, 1.815, 0.0), (0.105, 0.038, 0.056), OBS_DARK,    None,              'crys_guard')
        add_box(P, 'held', (sx * 0.095, 1.815, 0.0), (0.088, 0.020, 0.062), OBS_GOLD_HI, None,              'crys_guard')
    # Raised 3D bronze-gold diamond bezel at (0.00, 1.835) + deep violet-magenta heart crystal inside
    add_box(P, 'held', ( 0.000, 1.835, 0.0), (0.128, 0.128, 0.058), OBS_DARK,    ('z', 45.0),       'crys_guard')
    add_box(P, 'held', ( 0.000, 1.835, 0.0), (0.110, 0.110, 0.062), OBS_GOLD_HI, ('z', 45.0),       'crys_guard')
    add_box(P, 'held', ( 0.000, 1.835, 0.0), (0.080, 0.080, 0.066), CRYS_BASE,   ('z', 45.0),       'crys_heart')
    add_box(P, 'held', ( 0.000, 1.835, 0.0), (0.052, 0.052, 0.070), CRYS_MID,    ('z', 45.0),       'crys_heart')
    add_box(P, 'held', ( 0.000, 1.835, 0.0), (0.026, 0.026, 0.074), CRYS_WHITE,  ('z', 45.0),       'crys_heart')
    add_box(P, 'held', ( 0.000, 1.925, 0.0), (0.036, 0.050, 0.064), OBS_GOLD_HI, None,              'crys_guard')

    # --- E. Swept-Wing Amethyst Crystal Crossguard Clusters (|x| = 0.09..0.252, y = 1.68..1.96) ---
    for sx in (1, -1):
        add_box(P, 'held', (sx * 0.140, 1.815, 0.0), (0.075, 0.062, 0.054), OBS_DARK,    None,              'crys_guard')
        add_box(P, 'held', (sx * 0.165, 1.795, 0.0), (0.058, 0.034, 0.058), OBS_GOLD_HI, ('z',  sx * 22.5), 'crys_guard')
        # Wing Crystal 1 (Inner-Upper): royal violet -> bright magenta tip at (0.125, 1.96)
        add_box(P, 'held', (sx * 0.106, 1.885, 0.0), (0.042, 0.082, 0.050), CRYS_BASE,   ('z', -sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.118, 1.928, 0.0), (0.028, 0.056, 0.046), CRYS_MID,    ('z', -sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.125, 1.950, 0.0), (0.018, 0.028, 0.042), CRYS_BRIGHT, None,              'crys_wing')
        # Wing Crystal 2 (Main Mid-Upper Wing): sweeps from (0.135, 1.84) to (0.198, 1.935)
        add_box(P, 'held', (sx * 0.148, 1.865, 0.0), (0.060, 0.082, 0.052), CRYS_BASE,   ('z', -sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.172, 1.898, 0.0), (0.044, 0.062, 0.048), CRYS_MID,    ('z', -sx * 45.0), 'crys_wing')
        add_box(P, 'held', (sx * 0.192, 1.922, 0.0), (0.022, 0.034, 0.044), CRYS_GLOW,   ('z', -sx * 22.5), 'crys_wing')
        # Wing Crystal 3 (Outer Horizontal Wingtip): juts from (0.175, 1.815) to extreme tip at (0.252, 1.825)
        add_box(P, 'held', (sx * 0.195, 1.822, 0.0), (0.070, 0.048, 0.050), CRYS_DEEP,   None,              'crys_wing')
        add_box(P, 'held', (sx * 0.228, 1.824, 0.0), (0.042, 0.036, 0.046), CRYS_MID,    None,              'crys_wing')
        add_box(P, 'held', (sx * 0.244, 1.825, 0.0), (0.020, 0.022, 0.042), CRYS_BRIGHT, None,              'crys_wing')
        # Wing Crystal 4 (Lower Drooping Wing): sweeps down-outward from (0.15, 1.78) to sharp point at (0.205, 1.715)
        add_box(P, 'held', (sx * 0.168, 1.765, 0.0), (0.052, 0.060, 0.048), CRYS_DEEP,   ('z',  sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.188, 1.738, 0.0), (0.034, 0.046, 0.044), CRYS_BASE,   ('z',  sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.200, 1.720, 0.0), (0.018, 0.028, 0.040), CRYS_MID,    None,              'crys_wing')

    # --- F. Slim Hilt Grip with Crisp Diagonal Bronze-Gold Spiral Cord Wrap (y = 1.94..2.14) ---
    add_box(P, 'held', ( 0.000, 2.040, 0.0), (0.040, 0.200, 0.038), OBS_DARK,    None,          'crys_grip')
    for gy in (1.972, 2.015, 2.058, 2.100):
        add_box(P, 'held', (0.000, gy, 0.0), (0.044, 0.015, 0.042), OBS_GOLD_HI, ('z', 22.5), 'crys_guard')

    # --- G. Bronze V-Calyx & Multi-Faceted Diamond-Spear Pommel Crystal (y = 2.13..2.30) ---
    add_box(P, 'held', ( 0.000, 2.148, 0.0), (0.050, 0.032, 0.050), OBS_DARK,    None,          'crys_guard')
    for sx in (1, -1):
        add_box(P, 'held', (sx * 0.024, 2.172, 0.0), (0.020, 0.054, 0.056), OBS_GOLD_HI, ('z', -sx * 22.5), 'crys_guard')
        add_box(P, 'held', (sx * 0.046, 2.182, 0.0), (0.030, 0.042, 0.044), CRYS_DEEP,   ('z', -sx * 22.5), 'crys_wing')
        add_box(P, 'held', (sx * 0.058, 2.196, 0.0), (0.016, 0.026, 0.040), CRYS_MID,    ('z', -sx * 22.5), 'crys_wing')
    # Central tall faceted diamond-spear amethyst pommel crystal (y = 2.15..2.30)
    add_box(P, 'held', ( 0.000, 2.205, 0.0), (0.062, 0.080, 0.058), CRYS_BASE,   None,          'crys_wing')
    add_box(P, 'held', ( 0.000, 2.212, 0.0), (0.056, 0.056, 0.062), CRYS_MID,    ('z', 45.0),   'crys_heart')
    add_box(P, 'held', ( 0.000, 2.252, 0.0), (0.038, 0.050, 0.052), CRYS_BRIGHT, None,          'crys_wing')
    add_box(P, 'held', ( 0.000, 2.282, 0.0), (0.018, 0.034, 0.042), CRYS_WHITE,  None,          'crys_shard')
    add_box(P, 'held', ( 0.000, 2.220, 0.0), (0.016, 0.110, 0.066), CRYS_GLOW,   None,          'crys_shard')

    return P


# ============================================================================= 3. CHRONO DAGGER / SCYTHE (<= 350 cuboids)
def define_chrono_dagger():
    """height = 2.5, k = 11.75, pivot = [0.54, 0.55, 0.0].
    High-detail Chrono Scythe (faithful to weapon_chrono-scythe.zip):
      - High-contrast dark-umber (#341d00) & oxidized chrono-teal (#44938a) recessed blade channels
        with prominent Z thickness so they never get occluded by adjacent gold ribs
      - Pixel-art clock dial at (+0.61, 2.13) with crisp 10:10 hands & teal center pin,
        surrounded by a 12-tooth sunburst bezel, upper-right secondary lattice dial, upper-left hollow
        cogwheel ring, and right-side open crescent gear wheel
      - Smooth, straight slender antique-bronze & chrono-teal staff between 6 ornate multi-tiered node collars,
        ending in the stepped diamond astrolabe pommel and bottom pendulum needle spike.
    """
    P = new_part_table()
    add_part(P, 'held', None, (0.54, 0.55, 0.0))

    sx0 = 0.560  # Exact staff axis X in world blocks matching chrono_dagger_ortho_grid.png

    # --- A. Bottom Astrolabe / Pendulum Finial Pommel (y = 0.00..0.44, center = (0.560, 0.305)) ---
    add_box(P, 'held', (sx0, 0.035, 0.0), (0.016, 0.070, 0.026), BRASS_BRIGHT, None,        'chrono_blade_edge')
    add_box(P, 'held', (sx0, 0.090, 0.0), (0.028, 0.065, 0.048), BRASS_GOLD,   None,        'chrono_gear')
    add_box(P, 'held', (sx0, 0.145, 0.0), (0.064, 0.034, 0.072), BRASS_MID,    None,        'chrono_gear')
    add_box(P, 'held', (sx0, 0.185, 0.0), (0.090, 0.046, 0.092), BRASS_DARK,   None,        'chrono_gear')
    # Ornate diamond/octagonal astrolabe pommel housing at (sx0, 0.305) (x = 0.450..0.670)
    add_box(P, 'held', (sx0, 0.305, 0.0), (0.146, 0.146, 0.106), BRASS_VOID,   None,        'chrono_spine')
    add_box(P, 'held', (sx0, 0.305, 0.0), (0.146, 0.146, 0.112), BRASS_MID,    ('z', 45.0), 'chrono_gear')
    add_box(P, 'held', (sx0, 0.305, 0.0), (0.118, 0.118, 0.118), CHRONO_TEAL,  None,        'chrono_teal_inlay')
    # Raised circular/octagonal golden-ivory mini-dial at center (sx0, 0.305)
    add_box(P, 'held', (sx0, 0.305, 0.0), (0.092, 0.092, 0.128), BRASS_GOLD,   None,        'chrono_gear')
    add_box(P, 'held', (sx0, 0.305, 0.0), (0.092, 0.092, 0.132), BRASS_HI,     ('z', 45.0), 'chrono_gear')
    add_box(P, 'held', (sx0, 0.305, 0.0), (0.068, 0.068, 0.140), DIAL_IVORY,   None,        'chrono_subdial')
    add_box(P, 'held', (sx0, 0.305, 0.0), (0.218, 0.032, 0.092), BRASS_HI,     None,        'chrono_blade_edge')
    add_box(P, 'held', (sx0, 0.405, 0.0), (0.084, 0.040, 0.102), BRASS_GOLD,   None,        'chrono_gear')

    # --- B. Straight Slender Dark-Bronze & Chrono-Teal Staff + 6 Multi-Tiered Node Collars (y = 0.42..1.92) ---
    add_box(P, 'held', (sx0, 1.160, 0.0), (0.048, 1.480, 0.068), BRASS_DARK,   None,        'chrono_shaft')
    for sy_sp in (0.60, 0.72, 0.80, 1.02, 1.14, 1.38, 1.46, 1.68, 1.75):
        add_box(P, 'held', (sx0, sy_sp, 0.0), (0.036, 0.020, 0.074), BRASS_HI, ('z', 22.5), 'chrono_blade_edge')

    # 6 Ornate Multi-Tiered Brass Node Collars at exact Y positions from chrono_dagger_ortho_grid.png
    for ny in (0.480, 0.900, 1.260, 1.560, 1.825, 1.900):
        add_box(P, 'held', (sx0, ny, 0.0), (0.084, 0.068, 0.114), BRASS_BASE,   None, 'chrono_spine')
        add_box(P, 'held', (sx0, ny, 0.0), (0.120, 0.032, 0.128), BRASS_GOLD,   None, 'chrono_gear')
        add_box(P, 'held', (sx0, ny, 0.0), (0.036, 0.036, 0.138), BRASS_BRIGHT, None, 'chrono_blade_edge')

    # --- C. 3D Root Clockwork Astrolabe Assembly (x = +0.36..+0.90, y = 1.80..2.49) ---
    # 1. Main Central Roman Clock Dial & 12-Tooth Sunburst Bezel at (0.610, 2.130)
    cx_m, cy_m = 0.610, 2.130
    add_box(P, 'held', (cx_m, cy_m, 0.0), (0.198, 0.198, 0.116), BRASS_BASE,   None,        'chrono_gear')
    add_box(P, 'held', (cx_m, cy_m, 0.0), (0.198, 0.198, 0.120), BRASS_GOLD,   ('z', 45.0), 'chrono_gear')
    add_box(P, 'held', (cx_m, cy_m, 0.0), (0.190, 0.190, 0.124), BRASS_HI,     ('z', 22.5), 'chrono_gear')
    # Ivory clock dial with cardinal numerals, two hands, and a teal center pin.
    add_box(P, 'held', (cx_m, cy_m, 0.0), (0.148, 0.148, 0.138), DIAL_IVORY,   None,        'chrono_dial')
    # 12 Sunburst gear teeth radiating around main clock bezel
    for ang_deg, r_rot in ((0, 0.0), (30, -22.5), (60, -45.0), (90, 0.0), (120, 45.0), (150, 22.5),
                           (180, 0.0), (210, -22.5), (240, -45.0), (270, 0.0), (300, 45.0), (330, 22.5)):
        rad = math.radians(ang_deg)
        tx = cx_m + 0.122 * math.cos(rad)
        ty = cy_m + 0.122 * math.sin(rad)
        w_t = 0.044 if ang_deg in (0, 180) else 0.026
        h_t = 0.044 if ang_deg in (90, 270) else 0.026
        add_box(P, 'held', (tx, ty, 0.0), (w_t, h_t, 0.106), BRASS_HI, ('z', r_rot) if r_rot else None, 'chrono_blade_edge')

    # 2. Upper-Right Secondary Cogwheel & Golden Lattice Dial at (0.730, 2.340)
    cx_u, cy_u = 0.730, 2.340
    add_box(P, 'held', (cx_u, cy_u, 0.0), (0.138, 0.138, 0.102), BRASS_BASE,   None,        'chrono_gear')
    add_box(P, 'held', (cx_u, cy_u, 0.0), (0.138, 0.138, 0.106), BRASS_GOLD,   ('z', 45.0), 'chrono_gear')
    add_box(P, 'held', (cx_u, cy_u, 0.0), (0.096, 0.096, 0.116), BRASS_VOID,   None,        'chrono_subdial')
    add_box(P, 'held', (cx_u, 2.448, 0.0), (0.044, 0.074, 0.094), BRASS_HI,     None,        'chrono_gear')
    add_box(P, 'held', (cx_u, 2.465, 0.0), (0.068, 0.024, 0.090), BRASS_BRIGHT, None,        'chrono_blade_edge')
    add_box(P, 'held', (0.820, cy_u, 0.0), (0.048, 0.038, 0.094), BRASS_HI,     None,        'chrono_gear')
    add_box(P, 'held', (0.795, 2.405, 0.0), (0.038, 0.038, 0.090), BRASS_GOLD,  ('z', 45.0), 'chrono_gear')

    # 3. Upper-Left Hollow Cogwheel Ring with Internal Cross-Spokes at (0.535, 2.310)
    cx_l, cy_l = 0.535, 2.310
    for dx, dy, rw, rh, rrot in (
        ( 0.000,  0.076, 0.084, 0.026,   0.0),
        ( 0.000, -0.076, 0.084, 0.026,   0.0),
        (-0.076,  0.000, 0.026, 0.084,   0.0),
        ( 0.076,  0.000, 0.026, 0.084,   0.0),
        (-0.054,  0.054, 0.026, 0.062,  45.0),
        ( 0.054,  0.054, 0.026, 0.062, -45.0),
        (-0.054, -0.054, 0.026, 0.062, -45.0),
        ( 0.054, -0.054, 0.026, 0.062,  45.0),
    ):
        add_box(P, 'held', (cx_l + dx, cy_l + dy, 0.0), (rw, rh, 0.086), BRASS_GOLD, ('z', rrot) if rrot else None, 'chrono_gear')
    add_box(P, 'held', (cx_l, cy_l, 0.0), (0.130, 0.022, 0.080), BRASS_MID,  None, 'chrono_gear')
    add_box(P, 'held', (cx_l, cy_l, 0.0), (0.022, 0.130, 0.082), BRASS_MID,  None, 'chrono_gear')
    add_box(P, 'held', (cx_l, cy_l, 0.0), (0.038, 0.038, 0.092), BRASS_HI,   None, 'chrono_blade_edge')

    # 4. Right-Side Large Open Crescent Gear Wheel (center = (0.775, 2.085), reaching x = +0.895, y = 1.94..2.23)
    cx_r, cy_r = 0.775, 2.085
    for idx_g, ang_deg in enumerate(range(-75, 85, 25)):
        rad = math.radians(ang_deg)
        rx = cx_r + 0.095 * math.cos(rad)
        ry = cy_r + 0.105 * math.sin(rad)
        dz_g = 0.080 if idx_g % 2 == 0 else 0.084
        add_box(P, 'held', (rx, ry, 0.0), (0.034, 0.050, dz_g), BRASS_MID, None, 'chrono_gear')
        tx = cx_r + 0.122 * math.cos(rad)
        ty = cy_r + 0.128 * math.sin(rad)
        add_box(P, 'held', (tx, ty, 0.0), (0.026, 0.024, 0.074), BRASS_HI, None, 'chrono_blade_edge')

    # 5. Lower Scrollwork & Crescent Brackets bridging Clock Housing to Staff & Blade Root (y = 1.82..2.12)
    add_box(P, 'held', (0.465, 1.985, 0.0), (0.092, 0.036, 0.090), BRASS_GOLD, ('z', -22.5), 'chrono_gear')
    add_box(P, 'held', (0.435, 1.935, 0.0), (0.034, 0.076, 0.084), BRASS_MID,  ('z',  22.5), 'chrono_gear')
    add_box(P, 'held', (0.665, 1.955, 0.0), (0.092, 0.036, 0.088), BRASS_GOLD, ('z',  22.5), 'chrono_gear')
    add_box(P, 'held', (0.705, 1.895, 0.0), (0.034, 0.080, 0.082), BRASS_MID,  ('z', -22.5), 'chrono_gear')
    add_box(P, 'held', (0.415, 2.185, 0.0), (0.044, 0.044, 0.116), BRASS_HI,   None,         'chrono_subdial')
    add_box(P, 'held', (0.375, 2.265, 0.0), (0.042, 0.042, 0.114), BRASS_HI,   None,         'chrono_subdial')

    # --- D. Smoothly Stepped Crescent Scythe Blade (x = -0.914..+0.58, y = 1.40..2.49) ---
    blade_profile = [
        # (x,     y_bot, y_top, rot_deg)
        ( 0.42,  2.020, 2.475,   0.0),
        ( 0.35,  2.035, 2.475,   0.0),
        ( 0.28,  2.015, 2.465,   0.0),
        ( 0.21,  2.042, 2.452,   0.0),
        ( 0.14,  2.048, 2.438,   0.0),
        ( 0.07,  2.042, 2.420,  22.5),
        ( 0.00,  2.025, 2.398,  22.5),
        (-0.07,  2.020, 2.378,  22.5),
        (-0.14,  2.002, 2.355,  22.5),
        (-0.21,  1.982, 2.330,  22.5),
        (-0.28,  1.958, 2.298,  22.5),
        (-0.35,  1.925, 2.265,  22.5),
        (-0.42,  1.905, 2.230,  22.5),
        (-0.49,  1.872, 2.190,  22.5),
        (-0.56,  1.832, 2.145,  22.5),
        (-0.63,  1.785, 2.092,  22.5),
        (-0.69,  1.732, 2.035,  22.5),
        (-0.75,  1.675, 1.970,  45.0),
        (-0.80,  1.610, 1.895,  45.0),
        (-0.84,  1.545, 1.810,  45.0),
        (-0.87,  1.480, 1.715,   0.0),
        (-0.895, 1.430, 1.615,   0.0),
    ]

    for idx, (bx, yb, yt, rdeg) in enumerate(blade_profile):
        span = yt - yb
        rot_b = ('z', rdeg) if rdeg != 0.0 else None
        w_seg = 0.082 if idx < 17 else 0.056

        # 1. Upper Antique-Bronze Spine (top 20% of blade height, dz = 0.084)
        y_sp = yt - span * 0.11
        h_sp = max(0.030, span * 0.20)
        add_box(P, 'held', (bx, y_sp, 0.0), (w_seg, h_sp, 0.084), BRASS_BASE, rot_b, 'chrono_spine')
        # Crisp golden upper ridge separating spine and rune channel (dz = 0.098)
        add_box(P, 'held', (bx, yt - span * 0.24, 0.0), (w_seg, 0.018, 0.098), BRASS_BRIGHT, rot_b, 'chrono_blade_edge')

        # 2. Middle Deep Dark-Umber & Chrono-Teal Time-Rune Channel (dz = 0.092 so it stays clearly visible!)
        if idx <= 17:
            y_ru = yt - span * 0.42
            h_ru = max(0.036, span * 0.30)
            add_box(P, 'held', (bx, y_ru, 0.0), (w_seg, h_ru, 0.090), BRASS_VOID, rot_b, 'chrono_runes')
            # Alternating golden time-rune glyph & oxidized chrono-teal glow inlay inside the dark channel (dz = 0.096)
            col_inlay = BRASS_BRIGHT if (idx % 2 == 0) else CHRONO_TEAL
            add_box(P, 'held', (bx, y_ru, 0.0), (0.026, h_ru * 0.55, 0.096), col_inlay, rot_b, 'chrono_runes')
            # Crisp golden lower border rib of rune channel (dz = 0.100)
            add_box(P, 'held', (bx, yt - span * 0.58, 0.0), (w_seg, 0.016, 0.100), BRASS_GOLD, rot_b, 'chrono_blade_edge')

        # 3. Inner Blade Dark-Umber Fuller with Oxidized Teal Seam (dz = 0.086)
        if idx <= 18:
            y_wb = yb + span * 0.25
            h_wb = max(0.034, span * 0.28)
            add_box(P, 'held', (bx, y_wb, 0.0), (w_seg, h_wb, 0.086), BRASS_VOID, rot_b, 'chrono_fuller')
            if idx % 2 == 1:
                add_box(P, 'held', (bx, y_wb + h_wb * 0.22, 0.0), (0.044, 0.016, 0.092), CHRONO_TEAL, rot_b, 'chrono_fuller')

        # 4. Lower Burnished-Gold Crescent Cutting Edge (bottom 13% of blade height, dz = 0.094)
        y_ed = yb + span * 0.07
        h_ed = max(0.022, span * 0.13)
        add_box(P, 'held', (bx, y_ed, 0.0), (w_seg, h_ed, 0.094), BRASS_BRIGHT, rot_b, 'chrono_blade_edge')

        # 5. Sawtooth Micro-Serrations along Upper-Mid Spine (x = -0.07 down to x = -0.63, matching Orig!)
        if 7 <= idx <= 15:
            add_box(P, 'held', (bx - 0.010, yt + 0.010, 0.0), (0.042, 0.022, 0.088), BRASS_HI, ('z', 22.5), 'chrono_blade_edge')

    # Sharp vertical downward-pointing scythe needle tip at (-0.908, 1.40..1.52)
    add_box(P, 'held', (-0.902, 1.485, 0.0), (0.032, 0.085, 0.068), BRASS_HI,     None, 'chrono_blade_edge')
    add_box(P, 'held', (-0.908, 1.425, 0.0), (0.020, 0.055, 0.058), BRASS_BRIGHT, None, 'chrono_blade_edge')

    # 4 Major Swept-Back Dorsal Blade Barbs + Recessed Chrono-Teal Cutouts underneath (x = -0.30..+0.55)
    dorsal_barbs = [
        ( 0.46, 2.478, 0.175,  0.39, 2.432, 0.115,   0.0),
        ( 0.22, 2.458, 0.170,  0.16, 2.402, 0.115,   0.0),
        (-0.02, 2.405, 0.160, -0.08, 2.342, 0.110,  22.5),
        (-0.26, 2.315, 0.150, -0.31, 2.252, 0.100,  22.5),
    ]
    for xb, yb, wb, xt, yt_c, wt, r_d in dorsal_barbs:
        rot_d = ('z', r_d) if r_d != 0.0 else None
        add_box(P, 'held', (xb, yb, 0.0), (wb, 0.026, 0.098), BRASS_BRIGHT, rot_d, 'chrono_blade_edge')
        add_box(P, 'held', (xt, yt_c, 0.0), (wt, 0.036, 0.102), CHRONO_TEAL, rot_d, 'chrono_teal_inlay')

    # 4 Signature Downward Inner-Blade Cusps / Thorns + Crisp Golden Zigzag Filigree Struts
    inner_cusps = [
        ( 0.275, 1.990, 2.095),
        (-0.015, 1.995, 2.110),
        (-0.345, 1.895, 2.015),
        (-0.675, 1.695, 1.815),
    ]
    for xc, yc_tip, yc_web in inner_cusps:
        add_box(P, 'held', (xc, yc_tip, 0.0), (0.028, 0.052, 0.096), BRASS_BRIGHT, None, 'chrono_blade_edge')
        add_box(P, 'held', (xc - 0.038, yc_web, 0.0), (0.082, 0.015, 0.094), BRASS_BRIGHT, ('z', -22.5), 'chrono_blade_edge')
        add_box(P, 'held', (xc + 0.038, yc_web, 0.0), (0.082, 0.015, 0.094), BRASS_BRIGHT, ('z',  22.5), 'chrono_blade_edge')

    return P


def main():
    pack = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    emit_weapon_model_set(
        pack, 'weapon/crystal_greatsword', define_crystal_greatsword(),
        height_blocks=2.3, k_units_per_block=13.0,
    )
    emit_weapon_model_set(
        pack, 'weapon/stormcaller', define_stormcaller(),
        height_blocks=2.2, k_units_per_block=14.5,
    )
    emit_weapon_model_set(
        pack, 'weapon/chrono_dagger', define_chrono_dagger(),
        height_blocks=2.5, k_units_per_block=11.75,
    )


if __name__ == '__main__':
    main()
