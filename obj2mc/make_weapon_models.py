"""Hand-crafted Blockbench-style cuboid models and pixel-art textures
for HobbangEnhance 1.5.2 boss weapons:
  - weapon/crystal_greatsword (29 cuboids <= 30, height 2.3, k=13.0, pivot [0.0, 0.52, 0.0])
  - weapon/stormcaller        (52 cuboids <= 140, height 2.2, k=14.5, pivot [0.0, 0.6, 0.0])
  - weapon/chrono_dagger      (48 cuboids <= 108, height 2.5, k=11.75, pivot [0.54, 0.55, 0.0])
(weapon/smith_hammer is emitted by obj2mc/make_smith.py in 13 cuboids.)

All cuboids strictly obey Minecraft's single-axis rotation limit (±45 deg in 22.5 deg steps)
and minimum thickness >= 1px (~0.0625 blocks). Textures are procedural noise-free pixel art
using palettes extracted directly from the original models-3d/ weapon textures.
"""
import os
import numpy as np
import make_smith
from make_smith import (
    HERE, new_part_table, add_part, add_box, emit_model_set,
    paint_bevel_plate, draw_pixel_line,
)


def shade(rgb, mul):
    return np.clip(np.round(np.array(rgb, float) * mul), 0, 255).astype(np.uint8)


# ----------------------------------------------------------------------------- Extracted Original Palettes (models-3d/)
# 1. Crystal Greatsword (extracted from weapon_crystal_greatsword.png)
CRYS_VOID    = ( 35,   0,  37)  # #230025 guard dark / void
CRYS_DEEP    = (105,   0, 250)  # #6900fa blade p10
CRYS_BASE    = (145,   0, 255)  # #9100ff blade p25
CRYS_MID     = (187,  51, 255)  # #bb33ff blade p50
CRYS_BRIGHT  = (223, 113, 255)  # #df71ff blade p75
CRYS_GLOW    = (246, 162, 255)  # #f6a2ff blade p90
CRYS_WHITE   = (255, 211, 255)  # #ffd3ff blade p98
OBS_DARK     = ( 35,   0,  37)  # #230025 guard p10
OBS_BASE     = ( 66,  36,  42)  # #42242a guard dark bronze-violet
OBS_GOLD     = (100,  64,  55)  # #644037 guard p50
OBS_GOLD_HI  = (131,  96,  67)  # #836043 guard p75

# 2. Stormcaller (Tempest Dragon-Wing Battleaxe: extracted from weapon_stormcaller.png)
STORM_VOID   = (  3,  11,   0)  # #030b00 p10 pitch black-green void
STORM_DARK   = ( 20,  24,   2)  # #141802 p25 dark obsidian-slate
STORM_IRON   = ( 43,  43,  25)  # #2b2b19 p45 dark iron-slate
BRONZE_MUTE  = ( 87,  72,  42)  # #57482a p65 dark bronze trim
BRONZE_M_HI  = (128,  98,  48)  # #806230 p74 warm bronze highlight (kept <= R=134 so non-animated)
GOLD_EDGE    = (230, 156,  65)  # #e69c41 p82 golden wing-feather bevel
GOLD_BRIGHT  = (255, 204,  72)  # #ffcc48 p90 bright lightning gold
GOLD_WHITE   = (255, 228,  94)  # #ffe45e p95 white-gold lightning core

# 3. Chrono Dagger / Scythe (extracted from weapon_chrono_dagger.png)
BRASS_VOID   = ( 52,  29,   0)  # #341d00 p10 deep umber-bronze
BRASS_DARK   = ( 78,  42,   0)  # #4e2a00 p25 dark bronze
BRASS_BASE   = (107,  66,   0)  # #6b4200 p45 antique bronze
BRASS_MID    = (144,  87,   6)  # #905706 p65 warm brass
BRASS_GOLD   = (174, 108,  17)  # #ae6c11 p82 burnished gold
BRASS_HI     = (201, 129,  35)  # #c98123 p95 bright amber-gold
BRASS_BRIGHT = (242, 176,  56)  # #f2b038 p99 golden edge highlight
DIAL_IVORY   = (255, 254, 214)  # #fffed6 clock dial face
DIAL_RIM     = (196, 168, 112)  # #c4a870 clock dial corner shading
DIAL_HAND    = ( 52,  29,   0)  # #341d00 clock hands
CHRONO_TEAL  = ( 68, 147, 138)  # #44938a oxidized chrono-teal rune channel
CHRONO_GLOW  = (106, 196, 184)  # #6ac4b8 bright chrono-teal highlight


# ----------------------------------------------------------------------------- Weapon Face Pixel-Art Painter
def paint_weapon_face(kind, fn, W, H, color):
    W = max(1, int(W))
    H = max(1, int(H))
    base = np.array(color, float)
    f_mul = {'up': 1.12, 'north': 1.04, 'south': 0.96, 'east': 0.90, 'west': 0.90, 'down': 0.76}[fn]
    b_col = shade(base, f_mul)
    hi_col = shade(base, f_mul * 1.22)
    sh_col = shade(base, f_mul * 0.72)

    # --- 1. CRYSTAL GREATSWORD ---
    if kind == 'crys_blade':
        img = paint_bevel_plate(W, H, CRYS_BASE, CRYS_MID, CRYS_DEEP, border=True)
        if W >= 3 and H >= 6:
            cx = W // 2
            img[:, 0] = CRYS_MID
            img[:, -1] = CRYS_MID
            img[1:H - 1, cx] = CRYS_BRIGHT
            # Diagonal crystalline chevron facets along blade length (matches Orig crystal_greatsword!)
            for y in range(2, H - 2, 4):
                img[y, 1:W - 1] = CRYS_DEEP
                if y + 1 < H - 1:
                    img[y + 1, 0] = CRYS_GLOW
                    img[y + 1, -1] = CRYS_GLOW
                    img[y + 1, cx] = CRYS_WHITE
        return np.clip(img, 0, 255)

    if kind == 'crys_fuller':
        img = paint_bevel_plate(W, H, CRYS_MID, CRYS_WHITE, CRYS_DEEP, border=True)
        if H >= 3:
            cx = W // 2
            img[1:H - 1, cx] = CRYS_WHITE
            for y in range(2, H - 2, 3):
                img[y, :] = CRYS_GLOW
        return np.clip(img, 0, 255)

    if kind == 'crys_shard':
        img = paint_bevel_plate(W, H, CRYS_MID, CRYS_WHITE, CRYS_DEEP, border=True)
        if W >= 2 and H >= 2:
            img[0, :] = CRYS_WHITE
            img[1:H - 1, W // 2] = CRYS_GLOW
        return np.clip(img, 0, 255)

    if kind == 'crys_guard':
        img = paint_bevel_plate(W, H, OBS_BASE, OBS_GOLD_HI, OBS_DARK, border=True)
        if W >= 3 and H >= 3:
            img[0, :] = OBS_GOLD_HI
            img[-1, :] = OBS_GOLD
            cx, cy = W // 2, H // 2
            img[cy, cx] = CRYS_WHITE
            if W >= 5 and H >= 5:
                img[max(1, cy - 1):min(H - 1, cy + 2), max(1, cx - 1):min(W - 1, cx + 2)] = CRYS_GLOW
                img[cy, cx] = CRYS_WHITE
        return np.clip(img, 0, 255)

    if kind == 'crys_grip':
        img = paint_bevel_plate(W, H, OBS_DARK, OBS_GOLD, (20, 0, 22), border=True)
        for y in range(1, H - 1, 2):
            img[y, :] = OBS_GOLD_HI
        return np.clip(img, 0, 255)

    # --- 2. STORMCALLER (SPREAD-WING DRAGON BATTLEAXE) ---
    if kind == 'storm_blade_web':
        # Pitch-black dragon wing membrane (#030b00 / #141802) with dark iron bevel (lightning veins are 3D cuboids!)
        img = paint_bevel_plate(W, H, STORM_VOID, STORM_IRON, STORM_VOID, border=False)
        if W >= 4 and H >= 4:
            img[1:H - 1, 1:W - 1] = STORM_VOID
            img[H // 2, W // 2] = GOLD_EDGE
        return np.clip(img, 0, 255)

    if kind == 'storm_feather':
        # Golden scalloped dragon-wing feather blade: dark bronze inner root, blazing gold outer tip!
        img = paint_bevel_plate(W, H, GOLD_EDGE, GOLD_WHITE, BRONZE_MUTE, border=True)
        if W >= 3 and H >= 2:
            img[0, :] = GOLD_WHITE
            img[-1, :] = GOLD_BRIGHT
        return np.clip(img, 0, 255)

    if kind == 'storm_edge':
        # Bright golden lightning rib / cutting bevel
        img = paint_bevel_plate(W, H, GOLD_BRIGHT, GOLD_WHITE, GOLD_EDGE, border=True)
        if W >= 2 and H >= 2:
            img[0, :] = GOLD_WHITE
            img[-1, :] = GOLD_EDGE
        return np.clip(img, 0, 255)

    if kind == 'storm_dragon':
        # Pitch-black dragon armor & inner wing bone (#141802 / #2b2b19) with subtle bronze/gold top rim
        img = paint_bevel_plate(W, H, STORM_DARK, STORM_IRON, STORM_VOID, border=False)
        if W >= 3 and H >= 3:
            img[0, :] = BRONZE_M_HI
            img[-1, :] = STORM_VOID
        return np.clip(img, 0, 255)

    if kind == 'storm_haft':
        # Pitch-black haft with crisp diagonal golden spiral bands (matches Orig stormcaller!)
        img = paint_bevel_plate(W, H, STORM_VOID, STORM_DARK, STORM_VOID, border=True)
        for y in range(1, H - 1):
            if (y % 3) == 0:
                img[y, :] = GOLD_EDGE
                img[y, (y // 3) % max(1, W)] = GOLD_WHITE
            elif (y % 3) == 1:
                img[y, (y // 3) % max(1, W)] = BRONZE_MUTE
        return np.clip(img, 0, 255)

    if kind == 'storm_core':
        img = paint_bevel_plate(W, H, GOLD_BRIGHT, GOLD_WHITE, GOLD_EDGE, border=True)
        if W >= 2 and H >= 2:
            img[1:H - 1, 1:W - 1] = GOLD_WHITE
        return np.clip(img, 0, 255)

    # --- 3. CHRONO DAGGER / SCYTHE ---
    if kind == 'chrono_dial':
        # Bright cream-ivory clock face with dark hands & oxidized-teal pivot pin
        if fn in ('north', 'south'):
            img = np.full((H, W, 3), DIAL_IVORY, dtype=np.uint8)
            cx, cy = W // 2, H // 2
            if H >= 3 and W >= 3:
                img[0, 0] = DIAL_RIM
                img[0, -1] = DIAL_RIM
                img[-1, 0] = DIAL_RIM
                img[-1, -1] = DIAL_RIM
                img[0:cy + 1, cx] = DIAL_HAND
                if cx + 1 < W:
                    img[cy, cx + 1] = DIAL_HAND
                img[cy, cx] = CHRONO_TEAL
            return np.clip(img, 0, 255)
        return np.clip(paint_bevel_plate(W, H, BRASS_GOLD, BRASS_BRIGHT, BRASS_BASE, border=True), 0, 255)

    if kind == 'chrono_gear':
        img = paint_bevel_plate(W, H, BRASS_MID, BRASS_BRIGHT, BRASS_DARK, border=True, rivets=True)
        if W >= 3 and H >= 3:
            cx, cy = W // 2, H // 2
            img[cy, 1:W - 1] = BRASS_GOLD
            img[1:H - 1, cx] = BRASS_GOLD
            img[cy, cx] = CHRONO_GLOW
        return np.clip(img, 0, 255)

    if kind == 'chrono_blade_rune':
        # Dark umber-bronze inner blade channel with golden time runes & oxidized chrono-teal glow
        img = paint_bevel_plate(W, H, BRASS_VOID, BRASS_GOLD, BRASS_VOID, border=True)
        if W >= 3 and H >= 2:
            img[0, :] = BRASS_HI
            img[-1, :] = BRASS_MID
            cy = H // 2
            img[cy, 1:W - 1] = CHRONO_TEAL
            for x in range(1, W - 1, 2):
                img[cy, x] = BRASS_BRIGHT
                if x + 1 < W - 1:
                    img[cy, x + 1] = CHRONO_GLOW
        return np.clip(img, 0, 255)

    if kind == 'chrono_blade_edge':
        # Burnished amber-gold inner crescent cutting edge
        img = paint_bevel_plate(W, H, BRASS_GOLD, BRASS_BRIGHT, BRASS_BASE, border=True)
        if H >= 2 and W >= 2:
            img[-1, :] = BRASS_BRIGHT
            img[0, :] = BRASS_HI
        return np.clip(img, 0, 255)

    if kind == 'chrono_spine':
        img = paint_bevel_plate(W, H, BRASS_BASE, BRASS_HI, BRASS_DARK, border=True, rivets=True)
        if W >= 3 and H >= 2:
            img[0, :] = BRASS_HI
            img[-1, :] = BRASS_DARK
            if H >= 3:
                img[H // 2, 1:W - 1] = CHRONO_TEAL
        return np.clip(img, 0, 255)

    if kind == 'chrono_shaft':
        img = paint_bevel_plate(W, H, BRASS_DARK, BRASS_MID, BRASS_VOID, border=True)
        for y in range(1, H - 1, 3):
            img[y, :] = BRASS_GOLD
            if y + 1 < H - 1 and W >= 2:
                img[y + 1, W // 2] = CHRONO_TEAL
        return np.clip(img, 0, 255)

    return np.clip(paint_bevel_plate(W, H, b_col, hi_col, sh_col, border=True), 0, 255)


# ----------------------------------------------------------------------------- 1. Crystal Greatsword (29 cuboids <= 30)
def define_crystal_greatsword():
    """height = 2.3, k = 13.0, pivot = [0.0, 0.52, 0.0].
    Matches original mesh silhouette & extracted palette:
      - Sleek violet-magenta-pink crystal blade (y = 0.00..1.66) with 3 pairs of crystalline side serration barbs
      - Winged amethyst & dark-bronze crossguard (y = 1.64..1.98, x = -0.252..+0.252)
      - Wrapped grip (y = 1.92..2.18) & faceted crystal pommel (y = 2.16..2.30).
    Total cuboids: 29 (<= 30).
    """
    P = new_part_table()
    add_part(P, 'held', None, (0.0, 0.52, 0.0))

    # 1-4: Razor-sharp stepped & angled crystal tip (y = 0.00..0.38)
    add_box(P, 'held', ( 0.000, 0.08, 0.0), (0.065, 0.16, 0.065), CRYS_WHITE,  None,          'crys_fuller')
    add_box(P, 'held', ( 0.000, 0.22, 0.0), (0.112, 0.22, 0.065), CRYS_BRIGHT, None,          'crys_blade')
    add_box(P, 'held', ( 0.024, 0.22, 0.0), (0.065, 0.26, 0.066), CRYS_BRIGHT, ('z', -22.5),  'crys_blade')
    add_box(P, 'held', (-0.024, 0.22, 0.0), (0.065, 0.26, 0.066), CRYS_BRIGHT, ('z',  22.5),  'crys_blade')

    # 5-6: Main faceted amethyst blade + raised central glowing crystal fuller (y = 0.30..1.66)
    add_box(P, 'held', (0.00, 0.98, 0.0), (0.148, 1.34, 0.065), CRYS_BASE,   None,            'crys_blade')
    add_box(P, 'held', (0.00, 0.94, 0.0), (0.072, 1.44, 0.078), CRYS_GLOW,   None,            'crys_fuller')

    # 7-14: Four pairs of sleek crystalline side serration facets along the blade (matches Orig crystal_greatsword!)
    for sy_pos, col_b in ((0.52, CRYS_BRIGHT), (0.86, CRYS_MID), (1.20, CRYS_MID), (1.50, CRYS_BASE)):
        for sx in (1, -1):
            add_box(P, 'held', (sx * 0.046, sy_pos, 0.0), (0.065, 0.19, 0.068), col_b, ('z', -sx * 22.5), 'crys_blade')

    # 15-17: Dark bronze-violet ricasso & diamond heart crystal at crossguard center (y = 1.58..1.94)
    add_box(P, 'held', (0.00, 1.65, 0.0), (0.184, 0.16, 0.080), OBS_BASE,    None,            'crys_guard')
    add_box(P, 'held', (0.00, 1.78, 0.0), (0.168, 0.168, 0.088), OBS_GOLD,   ('z', 45.0),     'crys_guard')
    add_box(P, 'held', (0.00, 1.78, 0.0), (0.092, 0.092, 0.098), CRYS_WHITE, ('z', 45.0),     'crys_fuller')

    # 18-25: Swept-wing amethyst crystal crossguard (x = -0.252..+0.252, y = 1.66..1.98)
    for sx in (1, -1):
        add_box(P, 'held', (sx * 0.12, 1.77, 0.0), (0.16, 0.10, 0.078), OBS_GOLD,    ('z', -sx * 22.5), 'crys_guard')
        add_box(P, 'held', (sx * 0.16, 1.87, 0.0), (0.16, 0.09, 0.074), CRYS_GLOW,   ('z', -sx * 22.5), 'crys_shard')
        add_box(P, 'held', (sx * 0.19, 1.78, 0.0), (0.14, 0.08, 0.070), CRYS_BRIGHT, ('z',  0.0),       'crys_shard')
        add_box(P, 'held', (sx * 0.14, 1.69, 0.0), (0.13, 0.08, 0.068), CRYS_MID,    ('z',  sx * 22.5), 'crys_shard')

    # 26-27: Wrapped dark-bronze hilt grip (y = 1.90..2.18)
    add_box(P, 'held', (0.00, 2.04, 0.0), (0.082, 0.26, 0.072), OBS_DARK,    None,            'crys_grip')
    add_box(P, 'held', (0.00, 2.15, 0.0), (0.114, 0.07, 0.080), OBS_GOLD,    None,            'crys_guard')

    # 28-29: Faceted diamond amethyst pommel (y = 2.16..2.30)
    add_box(P, 'held', (0.00, 2.22, 0.0), (0.106, 0.106, 0.078), CRYS_BRIGHT, ('z', 45.0),    'crys_shard')
    add_box(P, 'held', (0.00, 2.23, 0.0), (0.072, 0.130, 0.084), CRYS_WHITE,  None,           'crys_fuller')
    return P


# ----------------------------------------------------------------------------- 2. Stormcaller (Spread-Wing Dragon Battleaxe, 52 cuboids <= 140)
def define_stormcaller():
    """height = 2.2, k = 14.5, pivot = [0.0, 0.6, 0.0].
    1.5.2 Spread-Wing Dragon Battleaxe (faithful to weapon_stormcaller.obj):
      - Central horned dragon head & S-neck facing -X (y = 1.38..2.12, x = -0.22..+0.19)
      - Deep open V-valleys between the central dragon neck and the raised inner wing leading-edges
        (|x| = 0.14..0.44, y = 1.66..2.18 is open sky!)
      - Deep concave under-wing arches next to the haft (|x| = 0.08..0.30, y = 0.96..1.42 is open sky!)
      - 5 distinct scalloped golden dragon-wing feather blades along each outer edge (y = 0.96..2.18, max |x| = 0.814)
      - Pitch-black wing membrane with branching golden lightning veins.
    Total cuboids: 52 (<= 140).
    """
    P = new_part_table()
    add_part(P, 'held', None, (0.0, 0.6, 0.0))

    # 1-4: Diamond Storm-Lantern Pommel (y = 0.00..0.45)
    add_box(P, 'held', (0.00, 0.09, 0.0), (0.070, 0.18, 0.070), STORM_DARK,  None,        'storm_dragon')
    add_box(P, 'held', (0.00, 0.25, 0.0), (0.186, 0.186, 0.090), STORM_IRON,  ('z', 45.0), 'storm_dragon')
    add_box(P, 'held', (0.00, 0.25, 0.0), (0.106, 0.106, 0.102), GOLD_BRIGHT, ('z', 45.0), 'storm_core')
    add_box(P, 'held', (0.00, 0.42, 0.0), (0.128, 0.075, 0.092), BRONZE_MUTE, None,       'storm_dragon')

    # 5-8: Pitch-Black Spiral-Wrapped Haft + Forged Rings (y = 0.44..1.46)
    add_box(P, 'held', (0.00, 0.95, 0.0), (0.094, 1.00, 0.090), STORM_VOID,  None,        'storm_haft')
    add_box(P, 'held', (0.00, 0.68, 0.0), (0.124, 0.07, 0.100), BRONZE_MUTE, None,        'storm_dragon')
    add_box(P, 'held', (0.00, 1.02, 0.0), (0.128, 0.07, 0.102), BRONZE_MUTE, None,        'storm_dragon')
    add_box(P, 'held', (0.00, 1.32, 0.0), (0.162, 0.09, 0.112), GOLD_EDGE,   None,        'storm_edge')

    # 9-18: Central Horned Dragon Neck & Head facing -X (y = 1.38..2.10, kept slim so V-valleys stay wide open!)
    add_box(P, 'held', ( 0.00, 1.54, 0.0), (0.22, 0.38, 0.122), STORM_DARK,  None,        'storm_dragon') # central breast socket
    add_box(P, 'held', ( 0.00, 1.54, 0.0), (0.10, 0.34, 0.134), GOLD_BRIGHT, None,        'storm_edge')   # central golden lightning spine
    add_box(P, 'held', (-0.01, 1.78, 0.0), (0.15, 0.26, 0.112), STORM_DARK,  ('z',  22.5),'storm_dragon') # slender S-curved dragon neck
    add_box(P, 'held', (-0.07, 1.94, 0.0), (0.20, 0.14, 0.108), STORM_DARK,  None,        'storm_dragon') # dragon skull
    add_box(P, 'held', (-0.17, 1.90, 0.0), (0.18, 0.08, 0.096), GOLD_BRIGHT, ('z',  22.5),'storm_edge')   # fierce upper snout (-X)
    add_box(P, 'held', (-0.15, 1.81, 0.0), (0.16, 0.07, 0.090), BRONZE_MUTE, ('z',  22.5),'storm_dragon') # open lower jaw (-X)
    add_box(P, 'held', (-0.09, 1.93, 0.0), (0.07, 0.07, 0.116), GOLD_WHITE,  None,        'storm_core')   # glowing golden dragon eye
    add_box(P, 'held', ( 0.09, 2.01, 0.0), (0.20, 0.08, 0.092), GOLD_BRIGHT, ('z', -22.5),'storm_edge')   # swept-back crown horn (+X)
    add_box(P, 'held', ( 0.11, 1.91, 0.0), (0.17, 0.07, 0.088), BRONZE_MUTE, ('z', -22.5),'storm_dragon') # lower neck horn (+X)
    add_box(P, 'held', (-0.02, 2.02, 0.0), (0.12, 0.09, 0.092), GOLD_EDGE,   ('z', -22.5),'storm_edge')   # top crest fin

    # 19-54: Spread Dragon Wings (18 cuboids per side, symmetric ±X)
    for sx in (1, -1):
        # A. Inner Pitch-Black Wing Bone Arch (leaves wide open V-valley at |x|=0.14..0.44, y=1.66..2.18!)
        add_box(P, 'held', (sx * 0.22, 1.54, 0.0), (0.24, 0.13, 0.106), STORM_DARK,  ('z', -sx * 22.5), 'storm_dragon')
        add_box(P, 'held', (sx * 0.38, 1.78, 0.0), (0.11, 0.40, 0.102), STORM_DARK,  ('z', -sx * 22.5), 'storm_dragon')
        add_box(P, 'held', (sx * 0.48, 2.06, 0.0), (0.09, 0.24, 0.096), GOLD_BRIGHT, ('z', -sx * 22.5), 'storm_edge')
        add_box(P, 'held', (sx * 0.28, 1.36, 0.0), (0.11, 0.28, 0.098), STORM_DARK,  ('z',  sx * 22.5), 'storm_dragon')
        add_box(P, 'held', (sx * 0.30, 1.12, 0.0), (0.10, 0.26, 0.092), STORM_DARK,  ('z', -sx * 22.5), 'storm_dragon')

        # B. Pitch-Black Dragon Wing Membrane Web (#030b00 interior)
        add_box(P, 'held', (sx * 0.50, 1.84, 0.0), (0.22, 0.34, 0.082), STORM_VOID,  ('z',  sx * 22.5), 'storm_blade_web')
        add_box(P, 'held', (sx * 0.53, 1.58, 0.0), (0.30, 0.34, 0.082), STORM_VOID,  None,              'storm_blade_web')
        add_box(P, 'held', (sx * 0.46, 1.32, 0.0), (0.24, 0.30, 0.080), STORM_VOID,  ('z', -sx * 22.5), 'storm_blade_web')
        add_box(P, 'held', (sx * 0.36, 1.12, 0.0), (0.16, 0.24, 0.078), STORM_VOID,  ('z', -sx * 22.5), 'storm_blade_web')

        # C. Slim Golden Lightning Veins branching from shoulder socket across the pitch-black wing membrane
        add_box(P, 'held', (sx * 0.34, 1.58, 0.0), (0.34, 0.065, 0.110), GOLD_BRIGHT, None,              'storm_edge')
        add_box(P, 'held', (sx * 0.47, 1.76, 0.0), (0.065, 0.32, 0.108), GOLD_BRIGHT, ('z', -sx * 22.5), 'storm_edge')
        add_box(P, 'held', (sx * 0.43, 1.36, 0.0), (0.065, 0.32, 0.108), GOLD_BRIGHT, ('z',  sx * 22.5), 'storm_edge')

        # D. 6 Distinct Pointed Golden Dragon-Wing Feather Tips jutting out along the wing arc (notched silhouette!)
        add_box(P, 'held', (sx * 0.58, 2.02, 0.0), (0.18, 0.088, 0.090), GOLD_BRIGHT, ('z',  sx * 22.5), 'storm_feather')
        add_box(P, 'held', (sx * 0.65, 1.84, 0.0), (0.22, 0.092, 0.090), GOLD_BRIGHT, ('z',  sx * 22.5), 'storm_feather')
        add_box(P, 'held', (sx * 0.68, 1.64, 0.0), (0.24, 0.095, 0.090), GOLD_BRIGHT, ('z',  sx * 22.5), 'storm_feather')
        add_box(P, 'held', (sx * 0.61, 1.44, 0.0), (0.22, 0.092, 0.088), GOLD_BRIGHT, ('z',  sx * 22.5), 'storm_feather')
        add_box(P, 'held', (sx * 0.49, 1.24, 0.0), (0.18, 0.088, 0.086), GOLD_BRIGHT, ('z',  sx * 22.5), 'storm_feather')
        add_box(P, 'held', (sx * 0.34, 1.05, 0.0), (0.095, 0.26, 0.084), GOLD_BRIGHT, ('z', -sx * 45.0), 'storm_feather')

    return P


# ----------------------------------------------------------------------------- 3. Chrono Dagger / Scythe (48 cuboids <= 108)
def define_chrono_dagger():
    """height = 2.5, k = 11.75, pivot = [0.54, 0.55, 0.0].
    1.5.2 Chrono Scythe using exact extracted antique-bronze, burnished gold, ivory dial,
    and oxidized chrono-teal (#44938a) palette + 4 dorsal spine barbs matching weapon_chrono_dagger.obj.
    Total cuboids: 48 (<= 108).
    """
    P = new_part_table()
    add_part(P, 'held', None, (0.54, 0.55, 0.0))

    sx0 = 0.556  # Exact staff axis X in world blocks

    # 1-4: Bottom Pendulum / Astrolabe Pommel (y = 0.00..0.44)
    add_box(P, 'held', (sx0, 0.09, 0.0), (0.068, 0.18, 0.068), BRASS_GOLD,  None,        'chrono_blade_edge')
    add_box(P, 'held', (sx0, 0.25, 0.0), (0.184, 0.184, 0.084), BRASS_MID,  ('z', 45.0), 'chrono_gear')
    add_box(P, 'held', (sx0, 0.25, 0.0), (0.108, 0.108, 0.096), DIAL_IVORY, None,        'chrono_dial')
    add_box(P, 'held', (sx0, 0.40, 0.0), (0.128, 0.08, 0.086), BRASS_GOLD,  None,        'chrono_gear')

    # 5-11: Slender Dark-Bronze Staff (Snath) + Ornate Brass Node Rings (y = 0.42..1.88)
    add_box(P, 'held', (sx0, 1.14, 0.0), (0.082, 1.46, 0.080), BRASS_DARK,  None,        'chrono_shaft')
    for ry in (0.56, 0.84, 1.12, 1.40, 1.66):
        add_box(P, 'held', (sx0, ry, 0.0), (0.128, 0.075, 0.094), BRASS_GOLD, None,      'chrono_gear')
    add_box(P, 'held', (sx0 - 0.05, 1.54, 0.0), (0.08, 0.28, 0.072), BRASS_MID, ('z', -22.5), 'chrono_spine')

    # 12-23: Clockwork Dial & Astrolabe Gear Housing (x = +0.36..+0.90, y = 1.82..2.48)
    add_box(P, 'held', (0.57, 2.02, 0.0), (0.30, 0.30, 0.092), BRASS_MID,   None,        'chrono_gear')
    add_box(P, 'held', (0.57, 2.02, 0.0), (0.30, 0.30, 0.096), BRASS_GOLD,  ('z', 45.0), 'chrono_gear')
    add_box(P, 'held', (0.57, 2.02, 0.0), (0.22, 0.22, 0.114), DIAL_IVORY,  None,        'chrono_dial')
    # Secondary upper-right cogwheel & ivory dial at (0.71, 2.24)
    add_box(P, 'held', (0.71, 2.24, 0.0), (0.21, 0.21, 0.088), BRASS_GOLD,  ('z', 22.5), 'chrono_gear')
    add_box(P, 'held', (0.71, 2.24, 0.0), (0.13, 0.13, 0.106), DIAL_IVORY,  None,        'chrono_dial')
    # Outer right gear arc & sun-ray finials (reaching x = +0.90, y = 2.49)
    add_box(P, 'held', (0.79, 2.04, 0.0), (0.16, 0.26, 0.078), BRASS_MID,   ('z',  22.5),'chrono_gear')
    add_box(P, 'held', (0.72, 1.86, 0.0), (0.16, 0.18, 0.078), BRASS_BASE,  ('z', -22.5),'chrono_gear')
    add_box(P, 'held', (0.57, 2.38, 0.0), (0.09, 0.22, 0.078), BRASS_HI,    None,        'chrono_blade_edge')
    add_box(P, 'held', (0.81, 2.35, 0.0), (0.08, 0.18, 0.074), BRASS_HI,    ('z',  22.5),'chrono_blade_edge')
    add_box(P, 'held', (0.84, 2.12, 0.0), (0.13, 0.08, 0.074), BRASS_HI,    None,        'chrono_blade_edge')
    # Lower scroll bracket connecting clock dial to staff & inner blade
    add_box(P, 'held', (0.38, 1.96, 0.0), (0.28, 0.11, 0.082), BRASS_MID,   ('z', -22.5),'chrono_gear')
    add_box(P, 'held', (0.45, 1.82, 0.0), (0.22, 0.10, 0.078), BRASS_BASE,  ('z',  22.5),'chrono_gear')

    # 24-48: Seamless Sweeping Crescent Scythe Blade
    # Layer A: Upper Antique-Bronze Spine + 4 Dorsal Fin Barbs (9 cuboids)
    add_box(P, 'held', ( 0.30, 2.34, 0.0), (0.42, 0.11, 0.088), BRASS_BASE, ('z',  0.0), 'chrono_spine')
    add_box(P, 'held', (-0.02, 2.27, 0.0), (0.44, 0.11, 0.086), BRASS_BASE, ('z', 22.5), 'chrono_spine')
    add_box(P, 'held', (-0.34, 2.13, 0.0), (0.44, 0.10, 0.084), BRASS_BASE, ('z', 22.5), 'chrono_spine')
    add_box(P, 'held', (-0.61, 1.91, 0.0), (0.42, 0.10, 0.080), BRASS_BASE, ('z', 45.0), 'chrono_spine')
    add_box(P, 'held', (-0.80, 1.66, 0.0), (0.34, 0.09, 0.076), BRASS_GOLD, ('z', 45.0), 'chrono_blade_edge')
    # 4 swept-back dorsal fin barbs along top of spine (matches Orig chrono_dagger!)
    add_box(P, 'held', ( 0.38, 2.43, 0.0), (0.22, 0.08, 0.074), BRASS_HI,   ('z', -22.5),'chrono_blade_edge')
    add_box(P, 'held', ( 0.16, 2.41, 0.0), (0.22, 0.08, 0.074), BRASS_HI,   ('z',  0.0), 'chrono_blade_edge')
    add_box(P, 'held', (-0.14, 2.34, 0.0), (0.20, 0.08, 0.074), BRASS_HI,   ('z', 22.5), 'chrono_blade_edge')
    add_box(P, 'held', (-0.46, 2.19, 0.0), (0.18, 0.08, 0.072), BRASS_HI,   ('z', 22.5), 'chrono_blade_edge')

    # Layer B: Middle Recessed Time-Rune Channel with Oxidized Chrono-Teal & Gold Runes (7 cuboids)
    add_box(P, 'held', ( 0.28, 2.22, 0.0), (0.42, 0.16, 0.076), BRASS_VOID, ('z',  0.0), 'chrono_blade_rune')
    add_box(P, 'held', (-0.01, 2.15, 0.0), (0.44, 0.16, 0.076), BRASS_VOID, ('z', 22.5), 'chrono_blade_rune')
    add_box(P, 'held', (-0.31, 2.01, 0.0), (0.44, 0.15, 0.074), BRASS_VOID, ('z', 22.5), 'chrono_blade_rune')
    add_box(P, 'held', (-0.56, 1.81, 0.0), (0.40, 0.13, 0.072), BRASS_VOID, ('z', 45.0), 'chrono_blade_rune')
    add_box(P, 'held', (-0.75, 1.59, 0.0), (0.30, 0.11, 0.070), BRASS_VOID, ('z', 45.0), 'chrono_blade_rune')
    add_box(P, 'held', ( 0.24, 2.18, 0.0), (0.16, 0.10, 0.088), CHRONO_TEAL,('z', 22.5), 'chrono_gear')
    add_box(P, 'held', ( 0.06, 2.18, 0.0), (0.14, 0.09, 0.086), CHRONO_GLOW,('z', 22.5), 'chrono_gear')

    # Layer C: Lower Burnished Gold Crescent Cutting Edge + Razor Tip at (-0.914, 1.41) (7 cuboids)
    add_box(P, 'held', ( 0.25, 2.10, 0.0), (0.40, 0.10, 0.068), BRASS_HI,   ('z',  0.0), 'chrono_blade_edge')
    add_box(P, 'held', (-0.01, 2.04, 0.0), (0.42, 0.10, 0.068), BRASS_HI,   ('z', 22.5), 'chrono_blade_edge')
    add_box(P, 'held', (-0.28, 1.90, 0.0), (0.42, 0.10, 0.068), BRASS_HI,   ('z', 22.5), 'chrono_blade_edge')
    add_box(P, 'held', (-0.52, 1.72, 0.0), (0.38, 0.09, 0.068), BRASS_HI,   ('z', 45.0), 'chrono_blade_edge')
    add_box(P, 'held', (-0.71, 1.53, 0.0), (0.28, 0.09, 0.066), BRASS_HI,   ('z', 45.0), 'chrono_blade_edge')
    # Sharp downward-pointing scythe point reaching (-0.914, 1.41)
    add_box(P, 'held', (-0.84, 1.50, 0.0), (0.10, 0.22, 0.068), BRASS_HI,   ('z', -22.5),'chrono_blade_edge')
    add_box(P, 'held', (-0.88, 1.44, 0.0), (0.07, 0.14, 0.066), BRASS_HI,   None,        'chrono_blade_edge')

    return P


def main():
    pack = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    orig_painter = make_smith.paint_face
    try:
        make_smith.paint_face = paint_weapon_face
        emit_model_set(pack, 'weapon/crystal_greatsword', define_crystal_greatsword(),
                       height_blocks=2.3, k_units_per_block=13.0,
                       credit='make_weapon_models (HobbangEnhance 1.5.2)')
        emit_model_set(pack, 'weapon/stormcaller', define_stormcaller(),
                       height_blocks=2.2, k_units_per_block=14.5,
                       credit='make_weapon_models (HobbangEnhance 1.5.2)')
        emit_model_set(pack, 'weapon/chrono_dagger', define_chrono_dagger(),
                       height_blocks=2.5, k_units_per_block=11.75,
                       credit='make_weapon_models (HobbangEnhance 1.5.2)')
    finally:
        make_smith.paint_face = orig_painter


if __name__ == '__main__':
    main()
