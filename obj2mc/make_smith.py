"""Hand-crafted Blockbench-style cuboid models and 16px/block pixel-art textures
for HobbangEnhance 1.5.2:
  - boss/abyssal_smith  (15 parts, exact 1.5.0/1.5.1 pivots & hierarchy, k=4.25)
  - boss/smith_hammer   (1 part 'hammer', attached to hand_r, k=8.0)
  - weapon/smith_hammer (1 part 'held', k=16.0)

1.5.2 Design Principles:
  - Modeling method ONLY from reference (clean layered cuboid armor plates, flat faces,
    16px/block hand-pixelated textures, noise-free 2-3 tone shading + border highlights,
    zero tiny fragment cuboids).
  - Shape, proportions, silhouette, motifs, and color palette come 100% from the ORIGINAL
    Abyssal Smith & Magma Forge Hammer meshes/textures in models-3d/:
      * Dark volcanic stone/iron layered armor slabs (#221a1a .. #756f6e)
      * Glowing Abyssal Cyan energy fissures & crystal spikes on head/shoulders/limbs (#007fad .. #2ce9ff)
      * Exposed V-shaped Cyan + Violet (#4c34c0) forge core on upper chest
      * Weathered brown blacksmith apron (#311e19 .. #725b50) with diamond/flame rune emblems,
        suspended by heavy iron chains over the shoulders and waist
      * Superheated molten red-orange magma right forearm & gauntlet (#540000 .. #ffad70)
      * Heavy 3-toed stone/iron boots
      * Rectangular volcanic basalt & molten-lava Magma Forge Hammer
"""
import json
import os
import shutil
import numpy as np
from PIL import Image
from obj2mc import FACE_NORMAL, FACE_VERTS, shelf_pack

HERE = os.path.dirname(os.path.abspath(__file__))
NS = 'hobbang'
PPU = 16  # 16 px per world block across all parts

# ----------------------------------------------------------------------------- Original Extracted Palette (models-3d/)
# 1. Dark Volcanic Stone & Forged Iron Armor (extracted from abyssal_smith texture stone percentiles)
STONE_VOID    = ( 34,  26,  26)  # #221a1a (p15 deep recess / crevice)
STONE_DARK    = ( 38,  31,  32)  # #261f20 (p35 shadow slab)
STONE_BASE    = ( 48,  42,  46)  # #302a2e (p58 main armor slab)
STONE_MID     = ( 66,  60,  62)  # #423c3e (p80 upper bevel)
STONE_LIGHT   = ( 88,  82,  82)  # #585252 (p90 stone edge highlight / chain base)
STONE_HI      = (117, 111, 110)  # #756f6e (p97 sharp rim / iron chain highlight)

# 2. Blacksmith Apron & Rune Emblem (extracted from front apron region)
APRON_VOID    = ( 36,  22,  18)  # #241612 (apron outline / fold shadow)
APRON_DARK    = ( 49,  30,  25)  # #311e19 (p20 dark leather-slate)
APRON_BASE    = ( 74,  57,  49)  # #4a3931 (p55 main apron body)
APRON_LIGHT   = ( 96,  75,  65)  # #604b41 (p75 apron bevel)
APRON_RUNE    = (114,  91,  80)  # #725b50 (p85 diamond/flame forge rune)
APRON_HI      = (142, 116, 102)  # #8e7466 (p96 rune center highlight)

# 3. Abyssal Cyan Crystals & Cracks + Chest Violet Core (extracted from cyan/violet regions)
CYAN_DEEP     = (  0,  92, 138)  # #005c8a (deep crack edge)
CYAN_MID      = (  0, 127, 173)  # #007fad (p25 cyan crystal base)
CYAN_GLOW     = ( 15, 159, 211)  # #0f9fd3 (p65 cyan glow)
CYAN_BRIGHT   = ( 44, 233, 255)  # #2ce9ff (p75 bright cyan core)
CYAN_WHITE    = (148, 248, 255)  # #94f8ff (p96 crystal tip highlight)
CORE_VIOLET   = ( 76,  52, 192)  # #4c34c0 (upper chest V-core indigo-violet)
CORE_MAGENTA  = (124,  68, 228)  # #7c44e4 (upper chest V-core inner transition)

# 4. Molten Magma Right Gauntlet & Forge Hammer Lava (extracted from right hand & hammer textures)
# Note: lava_pulse in weapons/make_weapons.py animates pixels with R > 120, R - B > 0, G > 40.
# Basalt/stone pixels stay at R <= 115 so only the molten fissures pulse!
BASALT_VOID   = ( 28,  16,  16)  # #1c1010 (hammer deep crevice)
BASALT_DARK   = ( 42,  24,  24)  # #2a1818 (hammer basalt body)
BASALT_BASE   = ( 57,  33,  30)  # #39211e (hammer basalt bevel)
BASALT_EDGE   = ( 86,  54,  48)  # #563630 (hammer stone rim, R<=115)
MAGMA_DARK    = ( 84,   4,   0)  # #540400 (cooled magma crust)
MAGMA_CRIMSON = (155,  14,   0)  # #9b0e00 (deep molten red)
MAGMA_RED     = (214,  38,   8)  # #d62608 (molten red-orange)
MAGMA_ORANGE  = (254, 112,  57)  # #fe7039 (bright magma orange, triggers lava_pulse)
MAGMA_GOLD    = (255, 163,  24)  # #ffa318 (white-hot golden lava core)
MAGMA_BRIGHT  = (255, 214, 112)  # #ffd670 (peak fissure center)


# ----------------------------------------------------------------------------- Pixel-Art Surface Painters (No Noise!)
def shade(rgb, mul):
    return np.clip(np.round(np.array(rgb, float) * mul), 0, 255).astype(np.uint8)


def lerp_rgb(c0, c1, t):
    a = np.array(c0, float)
    b = np.array(c1, float)
    return np.clip(np.round(a + (b - a) * float(t)), 0, 255).astype(np.uint8)


def paint_bevel_plate(W, H, base_rgb, hi_rgb, sh_rgb, border=True, rivets=False):
    """Clean Blockbench-style flat plate with 2-3 tone bevel shading and crisp 1px border."""
    img = np.empty((H, W, 3), dtype=np.uint8)
    img[:, :] = base_rgb
    if H >= 3:
        img[0, :] = hi_rgb
        img[-1, :] = sh_rgb
    if W >= 3:
        img[:, 0] = shade(hi_rgb, 0.92)
        img[:, -1] = sh_rgb
    if border and H >= 6 and W >= 6:
        img[1, 1:W - 1] = shade(base_rgb, 1.12)
        img[H - 2, 1:W - 1] = shade(base_rgb, 0.84)
        img[1:H - 1, 1] = shade(base_rgb, 1.08)
        img[1:H - 1, W - 2] = shade(base_rgb, 0.84)
    if rivets and H >= 7 and W >= 7:
        for ry in (2, H - 3):
            for rx in (2, W - 3):
                img[ry, rx] = hi_rgb
    return img


def draw_pixel_line(img, x0, y0, x1, y1, col_core, col_edge=None):
    """Clean stepped pixel line (no anti-aliased blur or noise). Two-pass so edge never overwrites core."""
    H, W = img.shape[:2]
    steps = int(max(abs(x1 - x0), abs(y1 - y0), 1))
    pts = []
    for s in range(steps + 1):
        t = s / max(1, steps)
        x = int(round(x0 + (x1 - x0) * t))
        y = int(round(y0 + (y1 - y0) * t))
        if 0 <= x < W and 0 <= y < H:
            pts.append((x, y))
    if col_edge is not None:
        for x, y in pts:
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < W and 0 <= ny < H:
                    img[ny, nx] = col_edge
    for x, y in pts:
        img[y, x] = col_core


def paint_face(kind, fn, W, H, color):
    W = max(1, int(W))
    H = max(1, int(H))
    f_mul = {'up': 1.12, 'north': 1.04, 'south': 0.96, 'east': 0.90, 'west': 0.90, 'down': 0.76}[fn]
    base = shade(color, f_mul)
    hi = shade(color, f_mul * 1.25)
    sh = shade(color, f_mul * 0.70)

    if kind == 'stone_plate':
        # Clean beveled dark stone/iron armor plate with crisp highlight rim
        top_hi = STONE_HI if fn == 'up' else STONE_LIGHT
        img = paint_bevel_plate(W, H, base, top_hi, STONE_VOID, border=True, rivets=(W >= 7 and H >= 7))
        return np.clip(img, 0, 255)

    if kind == 'stone_tier':
        # Layered pauldron / vambrace / greave armor plate with glowing cyan seam at bottom edge
        img = paint_bevel_plate(W, H, STONE_MID, STONE_HI, STONE_VOID, border=True, rivets=(W >= 8 and H >= 5))
        if H >= 4 and W >= 4 and fn in ('north', 'south', 'east', 'west'):
            img[-2, 1:W - 1] = CYAN_GLOW
            if W >= 6:
                img[-2, W // 3:W - W // 3] = CYAN_BRIGHT
        return np.clip(img, 0, 255)

    if kind == 'stone_cyan_crack':
        # Dark stone/iron inner core / plate with clean stepped glowing Abyssal Cyan fissure
        img = paint_bevel_plate(W, H, STONE_DARK, STONE_LIGHT, STONE_VOID, border=True, rivets=(W >= 8 and H >= 8))
        if W >= 4 and H >= 4 and fn in ('north', 'south', 'east', 'west'):
            cx, cy = W // 2, H // 2
            # Vertical-stepped glowing cyan energy vein through the stone
            draw_pixel_line(img, max(1, cx - 1), 1, cx, cy, CYAN_BRIGHT, CYAN_MID)
            draw_pixel_line(img, cx, cy, min(W - 2, cx + 1), H - 2, CYAN_BRIGHT, CYAN_MID)
            if W >= 6 and H >= 6:
                draw_pixel_line(img, max(1, cx - 2), cy + 1, min(W - 2, cx + 2), cy - 1, CYAN_GLOW, CYAN_DEEP)
                img[cy, cx] = CYAN_WHITE
        return np.clip(img, 0, 255)

    if kind == 'cyan_crystal':
        # Pure Abyssal Cyan crystal shard (deep cyan base -> bright cyan -> white-cyan tip, NO uint8 overflow!)
        img = np.empty((H, W, 3), dtype=np.uint8)
        for y in range(H):
            t = 1.0 - (y / max(1, H - 1))  # t=1 at top tip (y=0), t=0 at bottom base
            c_row = lerp_rgb(CYAN_MID, CYAN_BRIGHT, min(1.0, t * 1.25))
            if t > 0.65:
                c_row = lerp_rgb(CYAN_BRIGHT, CYAN_WHITE, (t - 0.65) / 0.35)
            img[y, :] = c_row
        if W >= 3:
            img[:, 0] = CYAN_WHITE
            img[:, -1] = CYAN_MID
        if H >= 3:
            img[0, :] = CYAN_WHITE
            img[-1, :] = CYAN_DEEP
        return np.clip(img, 0, 255)

    if kind == 'helmet_face':
        # Original Abyssal Smith forge-helm face: dark stone brow, horizontal cyan eyes, mandible grill
        img = paint_bevel_plate(W, H, STONE_BASE, STONE_LIGHT, STONE_VOID, border=True)
        if fn == 'north' and W >= 8 and H >= 8:  # model 'north' = world +Z (front face)
            cx = W // 2
            img[0:2, :] = STONE_LIGHT
            img[0, :] = STONE_HI
            ey = max(3, int(H * 0.38))
            img[ey - 1:ey + 2, 1:W - 1] = STONE_VOID
            # Two fierce horizontal glowing cyan eyes
            img[ey, 2:max(3, cx - 1)] = CYAN_BRIGHT
            img[ey, min(W - 3, cx + 1):W - 2] = CYAN_BRIGHT
            img[ey, max(2, cx - 2)] = CYAN_WHITE
            img[ey, min(W - 3, cx + 2)] = CYAN_WHITE
            if ey - 1 >= 2:
                img[ey - 1, 2:max(3, cx - 1)] = CYAN_MID
                img[ey - 1, min(W - 3, cx + 1):W - 2] = CYAN_MID
            # Central vertical stone nose-bridge
            img[1:ey + 2, max(1, cx - 1):min(W - 1, cx + 1)] = STONE_LIGHT
            # Lower mouth/beard grill with glowing cyan energy slots
            for x_ in range(2, W - 2, 2):
                img[ey + 2:H - 1, x_] = STONE_VOID
                if x_ + 1 < W - 2:
                    img[ey + 3:H - 2, x_ + 1] = CYAN_GLOW
        elif fn == 'south' and W >= 6 and H >= 6:
            draw_pixel_line(img, W // 2, 1, W // 2, H - 2, CYAN_GLOW, CYAN_DEEP)
        return np.clip(img, 0, 255)

    if kind == 'chest_v_core':
        # Original Abyssal Smith upper chest: exposed V-shaped Violet (#4c34c0) + Cyan (#2ce9ff) Forge Core!
        img = paint_bevel_plate(W, H, STONE_DARK, STONE_LIGHT, STONE_VOID, border=True)
        if fn in ('north', 'south') and W >= 8 and H >= 8:
            cx = W // 2
            # True V-shaped glowing energy cavity (wide at top shoulders, narrowing to point at bottom sternum)
            for y in range(1, H - 1):
                frac = 1.0 - (y - 1) / max(1, H - 3)  # 1.0 at top, 0.0 at bottom
                hw = max(1, int(round((W * 0.44) * (0.20 + 0.80 * frac))))
                x0, x1 = max(1, cx - hw), min(W - 1, cx + hw)
                img[y, x0:x1] = CORE_VIOLET
                if x1 - x0 >= 4:
                    img[y, x0 + 1:x1 - 1] = CORE_MAGENTA
            # Bright glowing Cyan V-arms & central forge fissure
            draw_pixel_line(img, 2, 1, cx, H - 2, CYAN_BRIGHT, CYAN_GLOW)
            draw_pixel_line(img, W - 3, 1, cx, H - 2, CYAN_BRIGHT, CYAN_GLOW)
            draw_pixel_line(img, cx, 2, cx, H - 2, CYAN_WHITE, CYAN_BRIGHT)
        return np.clip(img, 0, 255)

    if kind == 'apron_bib':
        # Upper blacksmith apron bib: weathered brown leather-slate + pixel-art diamond forge rune
        img = paint_bevel_plate(W, H, APRON_BASE, APRON_LIGHT, APRON_VOID, border=True, rivets=True)
        if fn == 'north' and W >= 8 and H >= 8:
            cx, cy = W // 2, H // 2
            r_diam = min(W, H) // 3
            for y in range(2, H - 2):
                for x in range(2, W - 2):
                    d = abs(x - cx) + abs(y - cy)
                    if d == r_diam or d == r_diam - 1:
                        img[y, x] = APRON_HI if d == r_diam - 1 else APRON_RUNE
                    elif d < r_diam - 1:
                        img[y, x] = APRON_DARK
                    if d <= 1:
                        img[y, x] = APRON_HI
        return np.clip(img, 0, 255)

    if kind == 'apron_skirt':
        # Lower pointed blacksmith apron skirt: weathered brown + flame/diamond forge rune motif
        img = paint_bevel_plate(W, H, APRON_BASE, APRON_LIGHT, APRON_VOID, border=True)
        if fn == 'north' and W >= 6 and H >= 8:
            cx = W // 2
            img[1:H - 1, 1] = APRON_DARK
            img[1:H - 1, W - 2] = APRON_DARK
            y_top, y_bot = max(2, H // 6), min(H - 2, (H * 5) // 6)
            for y in range(y_top, y_bot):
                span = max(1, (min(y - y_top, y_bot - 1 - y) * (W // 3)) // max(1, (y_bot - y_top) // 2))
                for dx in range(-span, span + 1):
                    px = cx + dx
                    if 1 < px < W - 1:
                        if abs(dx) in (span, max(0, span - 1)):
                            img[y, px] = APRON_HI if (y + dx) % 2 == 0 else APRON_RUNE
                        elif dx == 0:
                            img[y, px] = APRON_HI
                        else:
                            img[y, px] = APRON_DARK
        return np.clip(img, 0, 255)

    if kind == 'iron_chain':
        # Heavy dark-iron chain links (alternating bright iron rims and dark hollow centers)
        img = paint_bevel_plate(W, H, STONE_LIGHT, STONE_HI, STONE_VOID, border=True)
        if H >= W and H >= 4:
            for y in range(1, H - 1):
                if (y % 3) == 1 and W >= 3:
                    img[y, 1:W - 1] = STONE_VOID
                else:
                    img[y, 0:W] = STONE_HI if (y % 3 == 0) else STONE_LIGHT
        elif W > H and W >= 4:
            for x in range(1, W - 1):
                if (x % 3) == 1 and H >= 3:
                    img[1:H - 1, x] = STONE_VOID
                else:
                    img[0:H, x] = STONE_HI if (x % 3 == 0) else STONE_LIGHT
        return np.clip(img, 0, 255)

    if kind == 'magma_gauntlet':
        # Superheated molten red-orange right gauntlet (dark magma crust + crisp glowing orange-gold tier seam)
        img = paint_bevel_plate(W, H, MAGMA_CRIMSON, MAGMA_ORANGE, MAGMA_DARK, border=True, rivets=False)
        if W >= 4 and H >= 4:
            cx, cy = W // 2, H // 2
            img[-2, 1:W - 1] = MAGMA_ORANGE
            if W >= 6:
                img[-2, W // 4:W - W // 4] = MAGMA_GOLD
            if W >= 6 and H >= 6 and fn in ('north', 'south', 'east', 'west'):
                draw_pixel_line(img, cx, 1, cx, H - 2, MAGMA_GOLD, MAGMA_RED)
                img[cy, cx] = MAGMA_BRIGHT
        return np.clip(img, 0, 255)

    if kind == 'hammer_head_lava':
        # Original Magma Forge Hammer head: dark volcanic basalt slabs split by glowing red-orange-gold lava cracks!
        img = paint_bevel_plate(W, H, BASALT_DARK, BASALT_EDGE, BASALT_VOID, border=True)
        if W >= 4 and H >= 4:
            cx, cy = W // 2, H // 2
            # 1. Deep crimson-red magma bed inside the dark basalt rim
            img[1:H - 1, 1:W - 1] = MAGMA_CRIMSON
            if W >= 6 and H >= 6:
                img[2:H - 2, 2:W - 2] = MAGMA_RED
            # 2. Stamp staggered dark volcanic basalt rock slabs across the face, leaving lava channels between them
            cw, ch = max(1, (W - 4) // 2), max(1, (H - 4) // 2)
            for (y0, x0) in ((1, 1), (1, W - 1 - cw), (H - 1 - ch, 1), (H - 1 - ch, W - 1 - cw)):
                img[y0:y0 + ch, x0:x0 + cw] = BASALT_DARK
                if cw >= 2 and ch >= 2:
                    img[y0, x0:x0 + cw] = BASALT_EDGE
                    img[y0 + ch - 1, x0:x0 + cw] = BASALT_VOID
            # 3. Stepped glowing orange-gold lava fissures winding between the basalt slabs
            draw_pixel_line(img, 1, max(1, cy - 1), cx, cy, MAGMA_GOLD, MAGMA_ORANGE)
            draw_pixel_line(img, cx, cy, W - 2, min(H - 2, cy + 1), MAGMA_GOLD, MAGMA_ORANGE)
            draw_pixel_line(img, max(1, cx - 1), 1, cx, cy, MAGMA_ORANGE, MAGMA_RED)
            draw_pixel_line(img, cx, cy, min(W - 2, cx + 1), H - 2, MAGMA_ORANGE, MAGMA_RED)
            img[cy, cx] = MAGMA_BRIGHT
            if W >= 8 and H >= 8:
                # Secondary glowing lava pockets near opposite corners
                img[2, max(2, cx - 1):min(W - 2, cx + 2)] = MAGMA_ORANGE
                img[H - 3, max(2, cx - 1):min(W - 2, cx + 2)] = MAGMA_ORANGE
                img[cy, max(1, cx - 1):min(W - 1, cx + 2)] = MAGMA_BRIGHT
        return np.clip(img, 0, 255)

    if kind == 'hammer_collar':
        # Dark basalt stone collar / band with glowing molten seam
        img = paint_bevel_plate(W, H, BASALT_BASE, BASALT_EDGE, BASALT_VOID, border=True)
        if W >= 4 and H >= 3:
            cx, cy = W // 2, H // 2
            if W >= H:
                img[cy, 1:W - 1] = MAGMA_ORANGE
                img[cy, max(1, cx - 1):min(W - 1, cx + 2)] = MAGMA_GOLD
            else:
                img[1:H - 1, cx] = MAGMA_ORANGE
                img[max(1, cy - 1):min(H - 1, cy + 2), cx] = MAGMA_GOLD
        return np.clip(img, 0, 255)

    if kind == 'hammer_shaft':
        # Dark charred basalt/wood shaft with glowing lava cracks & stone rings
        img = paint_bevel_plate(W, H, BASALT_DARK, BASALT_BASE, BASALT_VOID, border=True)
        if H >= 4 and W >= 2:
            cx = W // 2
            img[1:H - 1, cx] = MAGMA_CRIMSON
            for y in range(2, H - 2, 4):
                img[y, :] = BASALT_EDGE
                if y + 1 < H - 1:
                    img[y + 1, cx] = MAGMA_ORANGE
                if y + 2 < H - 1:
                    img[y + 2, cx] = MAGMA_GOLD
        return np.clip(img, 0, 255)

    return np.clip(paint_bevel_plate(W, H, base, hi, sh, border=True, rivets=(W >= 8 and H >= 8)), 0, 255)


# ----------------------------------------------------------------------------- Part & Box Builder
def new_part_table():
    return {}


def add_part(table, name, parent, pivot):
    table[name] = dict(name=name, parent=parent, pivot=[round(float(x), 4) for x in pivot], boxes=[])
    return name


def add_box(table, part_name, center, size, color=STONE_BASE, rot_spec=None, kind='stone_plate'):
    """center, size in world blocks. Enforce minimum thickness >= 1/16 block (0.0625)."""
    sx, sy, sz = (max(0.065, float(x)) for x in size)
    table[part_name]['boxes'].append(dict(
        c=tuple(float(x) for x in center),
        s=(sx, sy, sz),
        color=color,
        rot=rot_spec,
        kind=kind,
    ))


# ----------------------------------------------------------------------------- 1.5.2 Abyssal Smith Rig (124 cuboids <= 300)
def define_abyssal_smith():
    """Exact 15 parts and pivots of 1.5.0/1.5.1, combining the clean layered-plate cuboid
    modeling technique of smith_style_ref.png with the 100% authentic silhouette, proportions,
    motifs, and extracted palette of the original Abyssal Smith mesh."""
    P = new_part_table()

    # 1. HEAD (pivot [0, 7.2, 0]) — Tapered dark-stone forge helm + horizontal cyan eyes + 3 cyan crystal flames
    add_part(P, 'head', 'chest', (0.0, 7.2, 0.0))
    add_box(P, 'head', (0.0, 7.24, -0.02), (0.86, 0.46, 0.88), STONE_DARK,  None,        'stone_plate')      # armored neck column
    add_box(P, 'head', (0.0, 7.86,  0.00), (1.26, 1.14, 1.28), STONE_BASE,  None,        'helmet_face')      # main forge-helm skull
    add_box(P, 'head', (0.0, 8.44, -0.02), (0.96, 0.24, 1.06), STONE_MID,   None,        'stone_plate')      # stepped upper helm crown
    add_box(P, 'head', (0.0, 7.96,  0.66), (0.22, 0.56, 0.18), STONE_LIGHT, None,        'stone_plate')      # central stone nose guard
    # Central crown cyan crystal flame (matches original mesh top crystal!)
    add_box(P, 'head', (0.0, 8.68,  0.10), (0.20, 0.64, 0.28), CYAN_BRIGHT, None,        'cyan_crystal')
    for sx in (1, -1):
        # V-angled stone brow plates above the glowing cyan eyes
        add_box(P, 'head', (sx * 0.36, 8.20,  0.60), (0.62, 0.24, 0.32), STONE_LIGHT, ('z', -sx * 22.5), 'stone_plate')
        # Tapered cheek/mandible armor plates flanking the mouth grill
        add_box(P, 'head', (sx * 0.48, 7.52,  0.46), (0.38, 0.68, 0.58), STONE_MID,   ('z',  sx * 22.5), 'stone_tier')
        # Upright temple cyan crystal flames on left & right sides of the helm
        add_box(P, 'head', (sx * 0.64, 8.38,  0.06), (0.20, 0.76, 0.28), CYAN_BRIGHT, None,              'cyan_crystal')
        add_box(P, 'head', (sx * 0.74, 8.08, -0.06), (0.18, 0.48, 0.26), CYAN_GLOW,   ('z', -sx * 22.5), 'cyan_crystal')

    # 2. CHEST (pivot [0, 4.5, 0]) — V-shaped Cyan/Violet Forge Core + Iron Suspender Chains + Brown Apron Bib
    add_part(P, 'chest', 'torso', (0.0, 4.5, 0.0))
    add_box(P, 'chest', (0.0, 5.88, -0.06), (2.48, 2.28, 1.56), STONE_DARK,  None, 'stone_cyan_crack')       # main inner chest block
    add_box(P, 'chest', (0.0, 6.24,  0.34), (1.32, 1.38, 0.92), STONE_DARK,  None, 'chest_v_core')           # exposed V-shaped cyan/violet forge core
    add_box(P, 'chest', (0.0, 4.96,  0.76), (1.38, 1.26, 0.26), APRON_BASE,  None, 'apron_bib')              # upper brown apron bib + diamond rune
    add_box(P, 'chest', (0.0, 5.96, -0.58), (1.56, 1.82, 0.64), STONE_DARK,  None, 'chest_v_core')           # back glowing spine core
    add_box(P, 'chest', (0.0, 5.92, -0.90), (1.88, 0.36, 0.22), STONE_LIGHT, None, 'iron_chain')             # back iron strap
    for sx in (1, -1):
        # Layered stone pectoral & rib plates flanking the glowing V-core
        add_box(P, 'chest', (sx * 0.94, 6.28,  0.46), (0.82, 1.24, 0.62), STONE_BASE,  ('z',  sx * 22.5), 'stone_tier')
        add_box(P, 'chest', (sx * 1.06, 7.04, -0.04), (0.78, 0.56, 1.38), STONE_MID,   ('z', -sx * 22.5), 'stone_plate')
        # Upright cyan crystal spikes erupting from top of inner shoulders (matches orig_smith_mesh.png!)
        add_box(P, 'chest', (sx * 1.22, 7.42, -0.02), (0.24, 0.66, 0.32), CYAN_BRIGHT, None,              'cyan_crystal')
        # Heavy vertical iron suspender chains running from shoulders to apron bib corners
        add_box(P, 'chest', (sx * 0.54, 6.24,  0.76), (0.22, 1.56, 0.22), STONE_HI,    None,              'iron_chain')
        # Back shoulder-blade armor slabs
        add_box(P, 'chest', (sx * 0.88, 6.22, -0.78), (0.82, 1.48, 0.36), STONE_BASE,  ('z', -sx * 22.5), 'stone_tier')

    # 3. TORSO (pivot [0, 2.7, 0]) — Chain Waist-Belt, Pointed Brown Apron Skirt, Tiered Stone Hip Tassets
    add_part(P, 'torso', None, (0.0, 2.7, 0.0))
    add_box(P, 'torso', (0.0, 3.60, -0.04), (2.36, 1.72, 1.42), STONE_DARK,  None, 'stone_cyan_crack')       # main waist/pelvis core
    add_box(P, 'torso', (0.0, 4.04,  0.00), (2.64, 0.36, 1.62), STONE_LIGHT, None, 'iron_chain')             # main waist chain belt
    add_box(P, 'torso', (0.0, 3.86,  0.80), (0.84, 0.34, 0.24), STONE_HI,    None, 'iron_chain')             # front center chain clasp
    # Pointed brown blacksmith apron skirt (y = 1.22..3.92, z = 0.64..0.84)
    add_box(P, 'torso', (0.0, 2.90,  0.72), (1.28, 1.98, 0.22), APRON_BASE,  None, 'apron_skirt')            # main apron skirt with flame rune
    add_box(P, 'torso', (0.0, 1.56,  0.71), (0.52, 0.68, 0.20), APRON_DARK,  None, 'apron_skirt')            # pointed apron bottom tip
    add_box(P, 'torso', (0.0, 3.08, -0.68), (1.68, 1.46, 0.28), STONE_BASE,  None, 'stone_tier')             # rear hip armor plate
    for sx in (1, -1):
        # Drooping front chain belt links meeting at center clasp
        add_box(P, 'torso', (sx * 0.78, 3.96,  0.78), (0.88, 0.28, 0.24), STONE_HI,   ('z', -sx * 22.5), 'iron_chain')
        # Tapered V-point bottom hems of front apron skirt (+sx * 22.5 -> top wider, bottom narrower)
        add_box(P, 'torso', (sx * 0.32, 1.66,  0.71), (0.44, 0.74, 0.20), APRON_BASE, ('z',  sx * 22.5), 'apron_skirt')
        # Layered stone hip tasset plates + hanging side chains
        add_box(P, 'torso', (sx * 1.26, 3.44,  0.02), (0.42, 0.88, 1.38), STONE_MID,  ('z',  sx * 22.5), 'stone_tier')
        add_box(P, 'torso', (sx * 1.38, 2.78,  0.02), (0.40, 0.86, 1.32), STONE_BASE, ('z',  sx * 22.5), 'stone_tier')
        add_box(P, 'torso', (sx * 1.48, 3.22,  0.24), (0.22, 1.34, 0.28), STONE_HI,   ('z',  sx * 22.5), 'iron_chain')

    # 4. ARMS, FOREARMS, HANDS (Left = +X Stone/Cyan, Right = -X Stone/Cyan Upper + Molten Magma Gauntlet!)
    for sx, q in ((1, 'l'), (-1, 'r')):
        # Upper Arm + 3-Tiered Layered Stone Pauldron (pivot [±2.0, 6.8, 0])
        a = add_part(P, f'arm_{q}', 'chest', (sx * 2.0, 6.8, 0.0))
        add_box(P, a, (sx * 1.98, 6.14, -0.04), (0.94, 1.56, 1.18), STONE_DARK,  None,             'stone_cyan_crack') # glowing inner arm core
        add_box(P, a, (sx * 2.06, 6.86, -0.06), (1.22, 0.56, 1.54), STONE_MID,   ('z', sx * 22.5), 'stone_tier')       # top pauldron tier
        add_box(P, a, (sx * 2.14, 6.32, -0.04), (1.20, 0.52, 1.46), STONE_BASE,  ('z', sx * 22.5), 'stone_tier')       # middle pauldron tier
        add_box(P, a, (sx * 2.10, 5.78, -0.02), (1.12, 0.48, 1.36), STONE_MID,   ('z', sx * 22.5), 'stone_tier')       # lower pauldron tier
        add_box(P, a, (sx * 1.82, 7.36, -0.04), (0.24, 0.54, 0.30), CYAN_BRIGHT, None,             'cyan_crystal')     # shoulder cyan crystal

        # Forearm + Layered Vambrace Plates (pivot [±2.0278, 5.3, 0.0278])
        fo = add_part(P, f'fore_{q}', a, (sx * 2.0278, 5.3, 0.0278))
        if q == 'l':
            # Left forearm: dark stone core + 2 layered stone vambrace cuffs + cyan crystal shard
            add_box(P, fo, (sx * 2.14, 4.88, 0.08), (1.06, 0.92, 1.16), STONE_DARK,  None,             'stone_cyan_crack')
            add_box(P, fo, (sx * 2.34, 5.10, 0.08), (0.92, 0.46, 1.24), STONE_MID,   ('z', sx * 22.5), 'stone_tier')
            add_box(P, fo, (sx * 2.40, 4.66, 0.10), (0.94, 0.46, 1.26), STONE_BASE,  ('z', sx * 22.5), 'stone_tier')
            add_box(P, fo, (sx * 2.66, 5.18, 0.08), (0.20, 0.46, 0.26), CYAN_BRIGHT, ('z', sx * 22.5), 'cyan_crystal')
        else:
            # Right forearm: upper stone-cyan cuff transitioning into superheated molten red-orange magma!
            add_box(P, fo, (sx * 2.14, 4.88, 0.08), (1.06, 0.92, 1.16), MAGMA_DARK,    None,             'magma_gauntlet')
            add_box(P, fo, (sx * 2.32, 5.12, 0.08), (0.92, 0.46, 1.24), STONE_MID,     ('z', sx * 22.5), 'stone_tier')
            add_box(P, fo, (sx * 2.40, 4.66, 0.10), (0.96, 0.48, 1.28), MAGMA_CRIMSON, ('z', sx * 22.5), 'magma_gauntlet')
            add_box(P, fo, (sx * 2.64, 5.18, 0.08), (0.20, 0.44, 0.26), CYAN_BRIGHT,   ('z', sx * 22.5), 'cyan_crystal')

        # Gauntlet Hand (pivot [±2.2917, 4.45, 0.2083])
        ha = add_part(P, f'hand_{q}', fo, (sx * 2.2917, 4.45, 0.2083))
        if q == 'l':
            add_box(P, ha, (sx * 2.28, 3.98, 0.22), (1.18, 0.88, 1.22), STONE_BASE, None,         'stone_cyan_crack') # back of hand
            add_box(P, ha, (sx * 2.52, 4.02, 0.20), (0.76, 0.74, 1.26), STONE_MID,  ('z',  22.5), 'stone_tier')       # outer gauntlet shield
            add_box(P, ha, (sx * 2.34, 3.34, 0.30), (1.08, 0.64, 1.12), STONE_DARK, ('x', -22.5), 'stone_plate')      # curled armored fingers
            add_box(P, ha, (sx * 1.88, 3.58, 0.58), (0.46, 0.62, 0.52), STONE_MID,  None,         'stone_plate')      # inner thumb
        else:
            # Right hand: massive molten red-orange magma gauntlet gripping the hammer!
            add_box(P, ha, (sx * 2.28, 3.98, 0.22), (1.22, 0.90, 1.26), MAGMA_CRIMSON, None,         'magma_gauntlet')
            add_box(P, ha, (sx * 2.54, 4.02, 0.20), (0.78, 0.76, 1.30), MAGMA_RED,     ('z', -22.5), 'magma_gauntlet')
            add_box(P, ha, (sx * 2.34, 3.34, 0.30), (1.12, 0.66, 1.16), MAGMA_CRIMSON, ('x', -22.5), 'magma_gauntlet')
            add_box(P, ha, (sx * 1.88, 3.58, 0.58), (0.48, 0.64, 0.54), MAGMA_ORANGE,  None,         'magma_gauntlet')

    # 5. LEGS, LAYERED GREAVES, 3-TOED BOOTS (exact 1.5.0/1.5.1 pivots)
    leg_pivs = {
        'l': ((0.9, 2.6, 0.0), (1.0833, 1.55, -0.0833), (1.3056, 0.65, 0.1944)),
        'r': ((-0.9, 2.6, 0.0), (-1.1389, 1.55, -0.0833), (-1.3056, 0.65, 0.25)),
    }
    for sx, q in ((1, 'l'), (-1, 'r')):
        lp, sp, fp = leg_pivs[q]
        # Thigh (leg_l / leg_r)
        lg = add_part(P, f'leg_{q}', None, lp)
        add_box(P, lg, (sx * 1.12, 2.12,  0.00), (1.18, 1.10, 1.42), STONE_DARK, None,             'stone_cyan_crack')
        add_box(P, lg, (sx * 1.16, 2.16,  0.42), (1.08, 0.88, 0.72), STONE_BASE, None,             'stone_tier')
        add_box(P, lg, (sx * 1.44, 2.16,  0.02), (0.64, 0.92, 1.34), STONE_MID,  ('z', sx * 22.5), 'stone_tier')

        # Shin / Layered Greaves (shin_l / shin_r)
        sh = add_part(P, f'shin_{q}', lg, sp)
        add_box(P, sh, (sx * 1.24, 1.12, -0.04), (1.36, 0.96, 1.54), STONE_DARK, None,             'stone_cyan_crack') # inner shin core
        add_box(P, sh, (sx * 1.24, 1.54,  0.46), (1.24, 0.42, 0.74), STONE_LIGHT,None,             'stone_tier')       # knee armor cap
        add_box(P, sh, (sx * 1.24, 1.18,  0.44), (1.28, 0.44, 0.76), STONE_MID,  None,             'stone_tier')       # upper shin greave tier
        add_box(P, sh, (sx * 1.24, 0.82,  0.42), (1.26, 0.42, 0.74), STONE_BASE, None,             'stone_tier')       # lower shin greave tier
        add_box(P, sh, (sx * 1.58, 1.12, -0.02), (0.72, 0.84, 1.42), STONE_MID,  None,             'stone_tier')       # outer shin guard

        # Heavy 3-Toed Stone/Iron Boot (foot_l / foot_r, y = 0.00..0.65)
        ft = add_part(P, f'foot_{q}', sh, fp)
        add_box(P, ft, (sx * 1.26, 0.30,  0.04), (1.56, 0.60, 1.78), STONE_DARK, None,             'stone_plate')      # main boot sole & heel
        add_box(P, ft, (sx * 1.26, 0.50,  0.38), (1.42, 0.32, 1.12), STONE_MID,  ('x', 22.5),      'stone_tier')       # sloped instep armor plate
        # 3 distinct blocky armored toes across the front of the boot (matches orig_smith_mesh.png!)
        for tx_off in (-0.48, 0.0, 0.48):
            add_box(P, ft, (sx * 1.26 + tx_off, 0.22, 0.98), (0.44, 0.44, 0.62), STONE_BASE, None, 'stone_plate')

    return P


# ----------------------------------------------------------------------------- 1.5.2 Magma Forge Hammer (13 cuboids)
def define_hammer_boxes(is_weapon=False):
    """Original Magma Forge Hammer:
    Massive rectangular dark-basalt head with stepped volcanic collars and glowing red-orange-gold
    lava fissures, sturdy dark charred haft with 3 stone rings, and blocky lava-core pommel.
    boss/smith_hammer: height 4.2, pivot [0.3, 2.9, 0.0], bounds [-0.78..0.67, 0..4.2, -1.13..1.13].
    weapon/smith_hammer: height 2.0, pivot [-0.147, 0.62, 0.0].
    """
    raw = [
        # 1: Main rectangular volcanic basalt & molten-lava hammer head core
        ((-0.05, 0.66,  0.00), (1.32, 1.24, 1.86), BASALT_DARK, None,      'hammer_head_lava'),
        # 2-3: Stepped volcanic stone striking ends at -Z and +Z (z = ±0.98, total Z = -1.13..+1.13)
        ((-0.05, 0.66, -0.98), (1.22, 1.14, 0.30), BASALT_BASE, None,      'hammer_head_lava'),
        ((-0.05, 0.66,  0.98), (1.22, 1.14, 0.30), BASALT_BASE, None,      'hammer_head_lava'),
        # 4-5: Heavy dark-basalt end collars wrapping the hammer head near z = ±0.76
        ((-0.05, 0.66, -0.76), (1.38, 1.30, 0.22), BASALT_EDGE, None,      'hammer_collar'),
        ((-0.05, 0.66,  0.76), (1.38, 1.30, 0.22), BASALT_EDGE, None,      'hammer_collar'),
        # 6: Top volcanic basalt crown slab on hammer head (y = 0.08)
        ((-0.05, 0.08,  0.00), (1.16, 0.16, 1.44), BASALT_EDGE, None,      'hammer_collar'),
        # 7: Neck socket mount where the haft enters the hammer head (y = 1.28..1.56)
        (( 0.25, 1.40,  0.00), (0.56, 0.32, 0.48), BASALT_EDGE, None,      'hammer_collar'),
        # 8: Main dark charred ironwood/basalt haft (x = 0.26, y = 1.40..3.48)
        (( 0.26, 2.44,  0.00), (0.34, 2.10, 0.34), BASALT_DARK, None,      'hammer_shaft'),
        # 9-11: Three dark-stone grip rings along the haft (matches original hammer mesh!)
        (( 0.26, 1.84,  0.00), (0.44, 0.18, 0.44), BASALT_EDGE, None,      'hammer_collar'),
        (( 0.26, 2.44,  0.00), (0.44, 0.18, 0.44), BASALT_EDGE, None,      'hammer_collar'),
        (( 0.26, 3.04,  0.00), (0.44, 0.18, 0.44), BASALT_EDGE, None,      'hammer_collar'),
        # 12-13: Heavy blocky volcanic pommel with glowing molten lava core (y = 3.45..4.20)
        (( 0.38, 3.82,  0.00), (0.68, 0.72, 0.58), BASALT_BASE, None,      'hammer_head_lava'),
        (( 0.38, 3.52,  0.00), (0.54, 0.20, 0.48), BASALT_EDGE, None,      'hammer_collar'),
    ]
    P = new_part_table()
    if not is_weapon:
        add_part(P, 'hammer', None, (0.3, 2.9, 0.0))
        for c, s, col, r, k in raw:
            add_box(P, 'hammer', c, s, col, r, k)
    else:
        sc = 2.0 / 4.2
        add_part(P, 'held', None, (-0.147, 0.62, 0.0))
        for (cx, cy, cz), (sx, sy, sz), col, r, k in raw:
            wc = (-cx * sc, (4.2 - cy) * sc, cz * sc)
            ws = (sx * sc, sy * sc, sz * sc)
            add_box(P, 'held', wc, ws, col, r, k)
    return P


# ----------------------------------------------------------------------------- Model & Manifest Emitter
MAX_ATLAS = 128   # px; a part whose faces need more at 16 px/block gets more 128x128 pages (#t, #t1, ...), never less density


def paged_pack(rects, max_size, pad=1):
    """shelf-pack face rects into one power-of-two atlas if it fits in max_size, else into several max_size pages.
    returns [(page, x, y)] per rect and the page sizes"""
    pos, size = shelf_pack(rects, pad=pad)
    if size <= max_size:
        return [(0, x, y) for x, y in pos], [size]
    out = [None] * len(rects)
    todo = sorted(range(len(rects)), key=lambda i: (-rects[i][1], -rects[i][0]))
    pages = 0
    while todo:
        x = y = shelf = 0
        left = []
        for i in todo:
            w, h = rects[i][0] + 2 * pad, rects[i][1] + 2 * pad
            if w > max_size or h > max_size:
                raise ValueError(f'face {rects[i]} larger than a {max_size} px page')
            if x + w > max_size:
                x, y, shelf = 0, y + shelf, 0
            if y + h > max_size:
                left.append(i); continue
            out[i] = (pages, x + pad, y + pad)
            x += w; shelf = max(shelf, h)
        todo = left; pages += 1
    return out, [max_size] * pages

def emit_model_set(pack, model_id, parts_table, height_blocks, k_units_per_block, attach=None, credit='make_smith (HobbangEnhance 1.5.2)', *, part_ppu=None):
    S_REF = np.array([-1.0, 1.0, -1.0])
    disp = 16.0 / k_units_per_block
    for sub in ('models/item', 'textures/item', 'items'):
        shutil.rmtree(os.path.join(pack, 'assets', NS, *sub.split('/'), *model_id.split('/')), ignore_errors=True)

    man = {
        'id': f'{NS}:{model_id}',
        'height_blocks': height_blocks,
        'resolution': PPU,
        'texels_per_voxel': 1,
        'model_units_per_block': k_units_per_block,
        'item_display_scale': disp,
        'front': '+Z (boss local, yaw 0 = south)',
        'note': 'hand-designed Blockbench-style cuboid model (1.5.2); 16px/block noise-free pixel textures from original mesh palette.',
        'parts': [],
    }
    if attach:
        man['attach'] = attach

    if part_ppu:
        man['part_texture_resolution'] = part_ppu

    total_cuboids = 0
    total_tex_px = 0

    for pname, p in parts_table.items():
        ppu = (part_ppu or {}).get(pname, PPU)
        piv = np.array(p['pivot'], float)
        rects = []
        jobs = []
        for bi, b in enumerate(p['boxes']):
            c = np.array(b['c'], float)
            s = np.array(b['s'], float)
            cm = 8.0 + k_units_per_block * S_REF * (c - piv)
            sm = k_units_per_block * s
            b['_from'] = np.clip(cm - sm / 2.0, -16.0, 32.0)
            b['_to'] = np.clip(cm + sm / 2.0, -16.0, 32.0)
            for fn in FACE_NORMAL:
                vs = b['_from'] + (b['_to'] - b['_from']) * np.array(FACE_VERTS[fn], float)
                u_len = np.linalg.norm(vs[3] - vs[0]) / k_units_per_block
                v_len = np.linalg.norm(vs[1] - vs[0]) / k_units_per_block
                W = max(2, int(round(u_len * ppu)))
                H = max(2, int(round(v_len * ppu)))
                rects.append((W, H))
                jobs.append((bi, fn))

        pos, sizes = paged_pack(rects, MAX_ATLAS, pad=1)
        atlases = [np.zeros((sz, sz, 4), np.uint8) for sz in sizes]
        elements = [None] * len(p['boxes'])
        for (bi, fn), (W, H), (pg, px, py) in zip(jobs, rects, pos):
            b = p['boxes'][bi]
            atlas, size = atlases[pg], sizes[pg]
            atlas[py:py + H, px:px + W, :3] = paint_face(b['kind'], fn, W, H, b['color']).astype(np.uint8)
            atlas[py:py + H, px:px + W, 3] = 255
            atlas[py - 1, px:px + W] = atlas[py, px:px + W]
            atlas[py + H, px:px + W] = atlas[py + H - 1, px:px + W]
            atlas[py - 1:py + H + 1, px - 1] = atlas[py - 1:py + H + 1, px]
            atlas[py - 1:py + H + 1, px + W] = atlas[py - 1:py + H + 1, px + W - 1]
            if elements[bi] is None:
                el = {
                    'from': [round(float(x), 4) for x in b['_from']],
                    'to': [round(float(x), 4) for x in b['_to']],
                }
                if b['rot']:
                    ax, deg = b['rot']
                    el['rotation'] = {
                        'angle': float(deg if ax == 'y' else -deg),
                        'axis': ax,
                        'origin': [round(float(x), 4) for x in (b['_from'] + b['_to']) / 2.0],
                    }
                if b.get('light'):
                    el['light_emission'] = int(b['light'])   # 1.21.2+ element field: minimum light level (glow parts)
                el['faces'] = {}
                elements[bi] = el
            elements[bi]['faces'][fn] = {
                'uv': [
                    round(px * 16.0 / size, 5),
                    round(py * 16.0 / size, 5),
                    round((px + W) * 16.0 / size, 5),
                    round((py + H) * 16.0 / size, 5),
                ],
                'texture': '#t' if pg == 0 else f'#t{pg}',
            }

        tref = f'{NS}:item/{model_id}/{pname}'
        texrefs = {'t': tref, 'particle': tref}
        for pg in range(1, len(sizes)):
            texrefs[f't{pg}'] = f'{tref}_{pg}'
        model_json = {
            'credit': credit,
            'texture_size': [sizes[0], sizes[0]],
            'textures': texrefs,
            'elements': elements,
            'display': {'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}},
        }
        for sub in ('models/item', 'textures/item', 'items'):
            os.makedirs(os.path.join(pack, 'assets', NS, *sub.split('/'), *model_id.split('/')), exist_ok=True)
        with open(os.path.join(pack, 'assets', NS, 'models', 'item', *model_id.split('/'), f'{pname}.json'), 'w') as fh:
            json.dump(model_json, fh, separators=(',', ':'))
        for pg, atlas in enumerate(atlases):
            Image.fromarray(atlas).save(
                os.path.join(pack, 'assets', NS, 'textures', 'item', *model_id.split('/'), f'{pname}{"" if pg == 0 else f"_{pg}"}.png'),
                optimize=True,
            )
        with open(os.path.join(pack, 'assets', NS, 'items', *model_id.split('/'), f'{pname}.json'), 'w') as fh:
            json.dump({'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{model_id}/{pname}'}}, fh, indent=2)

        nc = len(elements)
        n_rot = sum(1 for b in p['boxes'] if b['rot'])
        total_cuboids += nc
        total_tex_px += sum(sz * sz for sz in sizes)
        size = max(sizes)
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
            'texture_pages': len(sizes),
            'voxels': 0,
        })

    man['total_cuboids'] = total_cuboids
    man_dir = os.path.join(os.path.dirname(pack.rstrip('/\\')), 'manifests')
    os.makedirs(man_dir, exist_ok=True)
    with open(os.path.join(man_dir, model_id.replace('/', '__') + '.json'), 'w') as fh:
        json.dump(man, fh, indent=2)
    print(f'[{model_id}] {len(man["parts"])} parts, {total_cuboids} cuboids, {k_units_per_block} u/block, tex_px_sum={total_tex_px:,}')
    return man


if __name__ == '__main__':
    pack = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    emit_model_set(
        pack, 'boss/abyssal_smith', define_abyssal_smith(),
        height_blocks=9.0, k_units_per_block=4.25,
    )
    emit_model_set(
        pack, 'boss/smith_hammer', define_hammer_boxes(is_weapon=False),
        height_blocks=4.2, k_units_per_block=8.0,
        attach={'boss': 'abyssal_smith', 'part': 'hammer', 'pivot_blocks': [-2.35, 3.5, 0.45]},
    )
    emit_model_set(
        pack, 'weapon/smith_hammer', define_hammer_boxes(is_weapon=True),
        height_blocks=2.0, k_units_per_block=16.0,
    )
