"""16x16 vanilla-style weapon icons (hand-pixelated 2D sprites matching the 1.5.2 3D Blockbench models
and extracted original palettes).
Diagonal coordinate helpers:
  u = x - y (-15 at bottom-left to +15 at top-right, along the shaft/blade axis)
  w = x + y - 15 (across the main diagonal, 0 = main bottom-left to top-right diagonal)
"""
import json
import os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PACK_ICONS = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack', 'assets', 'hobbang', 'textures', 'item', 'weapon'))
PREVIEW_OUT = os.path.normpath(os.path.join(HERE, '..', 'work', 'previews', 'weapon_icons.png'))

FLICK = [0.0, 0.15, 1.0, 0.25, 0.0, 0.0, 0.6, 0.1]


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mixc(a, b, k):
    ca = hexc(a) if isinstance(a, str) else a
    cb = hexc(b) if isinstance(b, str) else b
    return tuple(int(round(x + (y - x) * k)) for x, y in zip(ca[:3], cb[:3])) + (255,)


class Icon:
    def __init__(s):
        s.px = {}

    def put(s, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            s.px[(int(x), int(y))] = hexc(c) if isinstance(c, str) else c

    def band(s, u0, u1, w0, w1, c):
        """u = x - y (-15 at bottom-left to +15 at top-right), w = x + y - 15 (across diagonal)."""
        for x in range(16):
            for y in range(16):
                u, w = x - y, x + y - 15
                if u0 <= u <= u1 and w0 <= w <= w1:
                    s.put(x, y, c)

    def outline(s, c):
        o = {}
        col = hexc(c) if isinstance(c, str) else c
        for (x, y) in list(s.px):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in s.px and 0 <= q[0] < 16 and 0 <= q[1] < 16:
                    o[q] = col
        s.px.update(o)

    def image(s):
        a = np.zeros((16, 16, 4), np.uint8)
        for (x, y), c in s.px.items():
            a[y, x] = c
        return Image.fromarray(a, 'RGBA')


def hammer(phase=0.0):
    """1.5.2 Magma Forge Hammer (마그마 단조 망치):
    Dark volcanic basalt head (#1c1010, #2a1818, #39211e, #563630) split by glowing molten
    red-orange-gold lava fissures (#9b0e00, #d62608, #fe7039, #ffa318, #ffd670),
    charred basalt haft with 3 stone rings, and blocky lava-core pommel."""
    i = Icon()
    k = 0.5 + 0.5 * np.sin(phase * 2 * np.pi)
    lav_deep = mixc('#9b0e00', '#d62608', k)
    lav_mid  = mixc('#d62608', '#fe7039', k)
    lav_hot  = mixc('#ffa318', '#ffd670', k)

    # Blocky volcanic basalt + molten-core pommel at bottom-left (u = -14..-12)
    i.band(-14, -12, -1, 1, '#2a1818')
    i.band(-14, -13, -1, 0, '#563630')
    i.band(-13, -13, 0, 0, lav_mid)
    # Dark charred basalt haft (u = -11..1) with 3 volcanic stone grip rings & glowing lava seam
    i.band(-11, 1, 0, 0, '#2a1818')
    i.band(-11, 1, -1, -1, '#39211e')
    for ru in (-8, -4, 0):
        i.band(ru, ru, -1, 0, '#563630')
        i.band(ru + 1, ru + 1, 0, 0, lav_deep)
    # Massive rectangular volcanic basalt hammer head (u = 2..10, w = -6..6)
    i.band(2, 10, -6, 6, '#2a1818')       # dark volcanic basalt body
    i.band(2, 3, -5, 5, '#563630')        # upper-left basalt rim highlight
    i.band(9, 10, -5, 5, '#1c1010')       # lower-right basalt shadow
    # Stepped volcanic stone end-collars at w = -6..-5 and w = 5..6
    i.band(2, 10, -6, -5, '#39211e')
    i.band(3, 8, -6, -6, '#563630')
    i.band(2, 10, 5, 6, '#2a1818')
    i.band(3, 8, 5, 5, '#39211e')
    # Molten red-orange-gold lava bed & branching fissures across the hammer head!
    i.band(4, 8, -5, 5, lav_deep)
    i.band(5, 7, -4, 4, lav_mid)
    # Staggered dark-basalt corner & edge blocks framing the molten lava
    i.band(2, 4, -4, -2, '#2a1818')
    i.band(2, 3, -4, -3, '#563630')
    i.band(8, 10, 2, 4, '#2a1818')
    i.band(8, 9, 2, 3, '#39211e')
    i.band(2, 4, 2, 4, '#2a1818')
    i.band(8, 10, -4, -2, '#2a1818')
    # Connected white-hot golden lava fissure channels reaching both striking ends
    i.band(5, 7, -5, 5, lav_mid)
    i.band(3, 9, -1, 1, lav_mid)
    i.band(6, 6, -4, 4, lav_hot)
    i.band(4, 8, 0, 0, lav_hot)
    i.outline('#120808')
    return i.image()


def greatsword():
    """1.5.2 Crystal Greatsword (크리스탈 대검):
    Extracted violet-magenta-pink crystal palette (#6900fa, #9100ff, #bb33ff, #df71ff, #f6a2ff, #ffd3ff)
    with dark bronze-violet crossguard (#230025, #644037, #836043)."""
    i = Icon()
    # Faceted amethyst pommel at bottom-left (u = -14..-13)
    i.band(-14, -13, -1, 0, '#df71ff')
    i.put(1, 14, '#ffd3ff')
    # Wrapped dark bronze-violet hilt grip (u = -12..-7)
    i.band(-12, -7, 0, 0, '#230025')
    i.band(-12, -7, -1, -1, '#42242a')
    for gu in (-11, -9, -7):
        i.band(gu, gu, -1, 0, '#836043')
    # Swept-wing amethyst & bronze crossguard (u = -6..-4, w = -5..5)
    i.band(-6, -5, -2, 2, '#644037')
    i.band(-6, -5, -1, 1, '#836043')
    # Winged crystal spikes flaring out at w = -5..-3 and w = 3..5
    i.band(-5, -3, -5, -3, '#df71ff')
    i.band(-4, -3, -5, -4, '#f6a2ff')
    i.band(-5, -3, 3, 5, '#bb33ff')
    i.band(-4, -3, 4, 5, '#df71ff')
    # Central glowing heart crystal at guard (u = -5, w = 0)
    i.band(-5, -5, -1, 0, '#ffd3ff')
    # Main faceted amethyst blade (u = -3..11, w = -2..2)
    i.band(-3, 11, -2, 2, '#9100ff')      # vibrant violet base
    i.band(-3, 11, -2, -2, '#df71ff')     # upper-left bright crystal edge
    i.band(-3, 11, 2, 2, '#6900fa')       # lower-right deep violet shadow
    i.band(-3, 12, -1, 0, '#bb33ff')      # raised inner fuller
    # Crystalline serration facets & glowing fuller nodes along blade
    for u in (-2, 1, 4, 7, 10):
        i.band(u, u, 0, 0, '#ffd3ff')
        i.band(u, u, -1, -1, '#f6a2ff')
        i.band(u, u, -2, -2, '#ffd3ff')
    # Razor-sharp tapered amethyst tip (u = 12..14)
    i.band(12, 13, -1, 1, '#df71ff')
    i.band(12, 13, -1, -1, '#ffd3ff')
    i.band(14, 14, -1, 0, '#ffd3ff')
    i.put(15, 0, '#ffffff')
    i.outline('#1a0022')
    return i.image()


def battleaxe(k=0.0):
    """1.5.2 Stormcaller / Spread-Wing Dragon Battleaxe (뇌운 전투도끼):
    Hand-pixelated 16x16 sprite matching the original spread-wing dragon battleaxe silhouette
    (deep V-valleys next to the central dragon head, pitch-black #030b00/#141802 wing membrane,
    branching golden lightning veins, and connected serrated golden wing-feather cutting edges)."""
    i = Icon()
    edge_hi  = mixc('#ffe45e', '#e0fcff', 0.75 * k)
    edge_md  = mixc('#ffcc48', '#b8f2ff', 0.75 * k)
    vein_col = mixc('#e69c41', '#dcfbff', 0.80 * k)

    # 1. Diamond storm-lantern pommel at bottom-left (u = -14..-12)
    i.band(-14, -12, -1, 1, '#57482a')
    i.band(-13, -13, 0, 0, edge_hi)
    # 2. Pitch-black spiral-wrapped haft from (2,13) to (10,5)
    i.band(-11, 5, 0, 0, '#030b00')
    i.band(-11, 5, -1, -1, '#141802')
    for hu in (-9, -6, -3, 0, 3):
        i.band(hu, hu, -1, 0, '#e69c41')

    # 3. Central fierce horned dragon head at top-right (around x=12..14, y=1..3), leaving V-valleys open!
    for px, py in ((10, 4), (11, 3), (12, 2), (13, 2), (12, 3), (11, 4)):
        i.put(px, py, '#141802')
    i.put(11, 2, edge_md)                 # fierce upper dragon snout pointing upper-left
    i.put(12, 1, edge_hi)                 # glowing dragon eye / crown
    i.put(14, 2, '#e69c41')               # swept-back dragon horn
    i.put(10, 5, vein_col)                # central golden breastplate core

    # 4. Upper-Left Spread Dragon Wing (symmetric about main diagonal x + y == 15 with lower-right wing!)
    # Pitch-black wing membrane (leaves V-valley at (9,2),(10,2),(10,3) open!)
    ul_black = [
        (7, 1), (8, 1),
        (5, 2), (6, 2), (7, 2), (8, 2),
        (4, 3), (5, 3), (6, 3), (7, 3), (8, 3), (9, 3),
        (4, 4), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4),
        (4, 5), (5, 5), (6, 5), (7, 5),
        (4, 6), (5, 6),
    ]
    # Branching golden lightning vein inside upper-left black wing membrane
    ul_vein = [(9, 4), (8, 3), (7, 3), (6, 2), (6, 4), (5, 5)]
    # Connected serrated golden dragon-wing feather rim (top wingtip horn -> 3 stepped feather tips -> bottom beard tip!)
    ul_gold = [
        (8, 0), (9, 1),                   # Sharp upward-flaring top wingtip horn!
        (6, 0), (6, 1),                   # Feather 1 tip + notch
        (4, 1), (5, 1),                   # Feather 2 tip
        (3, 2), (3, 3),                   # Feather 3 widest center tip
        (3, 4), (3, 5),                   # Feather 4 lower-mid tip
        (3, 6), (4, 7), (5, 7),           # Feather 5 inward-curving bottom wing-beard tip!
    ]
    for (x, y) in ul_black:
        i.put(x, y, '#141802')
        i.put(15 - y, 15 - x, '#030b00')
    for (x, y) in ul_vein:
        i.put(x, y, vein_col)
        i.put(15 - y, 15 - x, vein_col)
    for (x, y) in ul_gold:
        i.put(x, y, edge_hi)
        i.put(15 - y, 15 - x, edge_md)

    i.outline('#030b00')
    return i.image()


def chrono_scythe():
    """1.5.2 Chrono Scythe (크로노 낫, id chrono_dagger):
    Extracted antique-bronze & burnished-gold palette (#341d00, #4e2a00, #6b4200, #905706, #ae6c11, #c98123, #f2b038),
    cream-ivory Roman clock dial (#fffed6), and oxidized chrono-teal rune channel (#44938a, #6ac4b8)."""
    i = Icon()
    # Pendulum pommel at bottom-left
    i.put(0, 15, '#c98123')
    i.put(1, 15, '#6b4200')
    i.put(1, 14, '#fffed6')
    # Slender dark-bronze snath from (2,13) to (11,4) with burnished brass rings
    for t in range(1, 11):
        x, y = 1 + t, 14 - t
        ring = t in (2, 5, 8)
        i.put(x, y, '#c98123' if ring else '#4e2a00')
        i.put(x + 1, y, '#905706' if ring else '#6b4200')
    # Clockwork gear bezel & bright cream-ivory clock dial at top-right joint (around x=12, y=3)
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            if abs(dx) + abs(dy) <= 3:
                i.put(12 + dx, 3 + dy, '#905706')
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            i.put(12 + dx, 3 + dy, '#fffed6')
    i.put(12, 2, '#341d00')               # 12 o'clock hand
    i.put(13, 3, '#341d00')               # 3 o'clock hand
    i.put(12, 3, '#44938a')               # Oxidized Chrono-Teal center pivot
    i.put(14, 1, '#f2b038')               # upper-right secondary gear finial
    # Sweeping Crescent Scythe Blade arching from clock housing (x=11, y=1) to sharp tip at (x=0, y=6)
    # 1. Upper antique-bronze spine + dorsal fin barbs
    for x in range(3, 12):
        i.put(x, 1, '#905706')
    for bx in (4, 6, 8, 10):
        i.put(bx, 0, '#c98123')
    i.put(2, 2, '#905706')
    i.put(1, 3, '#905706')
    # 2. Middle dark rune channel with oxidized Chrono-Teal & gold glyphs
    for x in range(3, 11):
        i.put(x, 2, '#f2b038' if x % 2 == 1 else '#44938a')
    i.put(2, 3, '#6ac4b8')
    i.put(1, 4, '#44938a')
    # 3. Lower burnished gold crescent cutting edge + razor point
    for x in range(4, 10):
        i.put(x, 3, '#c98123')
    i.put(3, 3, '#f2b038')
    i.put(2, 4, '#f2b038')
    i.put(1, 5, '#f2b038')
    i.put(0, 5, '#c98123')
    i.put(0, 6, '#f2b038')               # sharp downward tip
    i.outline('#241200')
    return i.image()


def chrono_dagger():
    return chrono_scythe()


def write_all(out=PACK_ICONS):
    os.makedirs(out, exist_ok=True)
    frames = [hammer(k / 8.0) for k in range(8)]
    strip = Image.new('RGBA', (16, 16 * len(frames)))
    for k, f in enumerate(frames):
        strip.paste(f, (0, 16 * k))
    strip.save(os.path.join(out, 'smith_hammer_icon.png'))
    with open(os.path.join(out, 'smith_hammer_icon.png.mcmeta'), 'w') as fh:
        json.dump({'animation': {'frametime': 3}}, fh, indent=1)

    ax = [battleaxe(k) for k in FLICK]
    strip = Image.new('RGBA', (16, 16 * len(ax)))
    for k, f in enumerate(ax):
        strip.paste(f, (0, 16 * k))
    strip.save(os.path.join(out, 'stormcaller_icon.png'))
    with open(os.path.join(out, 'stormcaller_icon.png.mcmeta'), 'w') as fh:
        json.dump({'animation': {'frametime': 2}}, fh, indent=1)

    for nm, fn in (('crystal_greatsword', greatsword), ('chrono_dagger', chrono_scythe)):
        fn().save(os.path.join(out, f'{nm}_icon.png'))

    os.makedirs(os.path.dirname(PREVIEW_OUT), exist_ok=True)
    sheet = Image.new('RGBA', (16 * 5 + 6 * 2, 20), (42, 45, 54, 255))
    for k, im in enumerate([frames[0], greatsword(), battleaxe(0.0), chrono_scythe()]):
        sheet.alpha_composite(im, (2 + k * 18, 2))
    sheet.alpha_composite(battleaxe(1.0), (2 + 4 * 18, 2))
    sheet.resize((sheet.width * 12, sheet.height * 12), Image.NEAREST).save(PREVIEW_OUT)


if __name__ == '__main__':
    write_all()
