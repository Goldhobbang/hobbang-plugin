"""16x16 vanilla-style weapon icons (procedural pixel art on the 45deg tool diagonal).
u = x - y (along the blade, +toward top-right), w = x + y - 15 (across, 0 = main diagonal)."""
import numpy as np, os, json
from PIL import Image

def hexc(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

class Icon:
    def __init__(s): s.px = {}
    def put(s, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16: s.px[(x, y)] = hexc(c) if isinstance(c, str) else c
    def band(s, u0, u1, w0, w1, c):
        for x in range(16):
            for y in range(16):
                u, w = x - y, x + y - 15
                if u0 <= u <= u1 and w0 <= w <= w1: s.put(x, y, c)
    def outline(s, c):
        o = {}
        for (x, y) in list(s.px):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in s.px and 0 <= q[0] < 16 and 0 <= q[1] < 16: o[q] = hexc(c)
        s.px.update(o)
    def image(s):
        a = np.zeros((16, 16, 4), np.uint8)
        for (x, y), c in s.px.items(): a[y, x] = c
        return Image.fromarray(a, 'RGBA')

def hammer(phase=0.0):
    i = Icon()
    # handle: dark wood/iron, lighter upper-left edge
    i.band(-13, 1, 0, 0, '#4a2c1c'); i.band(-13, 1, -1, -1, '#7a4c30')
    i.band(-7, -5, -1, 0, '#8e1c10'); i.band(-6, -6, -1, 0, '#c2381c')      # grip wrap
    i.band(-14, -13, -1, 1, '#2b2b30'); i.band(-14, -14, -1, -1, '#55555e')  # pommel
    # head: basalt block perpendicular to the handle
    i.band(2, 9, -6, 6, '#3a302e'); i.band(2, 3, -6, 6, '#56463f'); i.band(8, 9, -6, 6, '#241c1b')
    i.band(2, 9, -6, -6, '#625048'); i.band(2, 9, 6, 6, '#1c1514')
    # lava cracks (pulse)
    k = 0.5 + 0.5 * np.sin(phase * 2 * np.pi)
    lav = tuple(int(a + (b - a) * k) for a, b in zip((230, 90, 20), (255, 200, 60))) + (255,)
    hot = tuple(int(a + (b - a) * k) for a, b in zip((255, 160, 40), (255, 245, 170))) + (255,)
    crack = {(5, -5), (5, -4), (6, -3), (6, -2), (5, -1), (4, 0), (5, 1), (6, 2), (6, 3), (5, 4), (4, 5), (7, -2), (8, -1), (3, 1)}
    hotp = {(6, -2), (4, 0), (6, 3)}
    for x in range(16):
        for y in range(16):
            u, w = x - y, x + y - 15
            if 2 <= u <= 9 and -6 <= w <= 6 and (u in (5, 6) and abs(w) <= 4 and (w + u) % 4 != 0 or (u, w) in ((4, 3), (7, -3), (3, 2), (8, -4))):
                i.put(x, y, hot if abs(w) <= 1 else lav)
    i.outline('#140c0a')
    return i.image()

def sword(blade, edge, dark, tip_len, blade_w, guard, guard_hi, grip, pommel, u_guard=-5, u_tip=13, extra=None):
    i = Icon()
    i.band(-13, u_guard - 1, 0, 0, grip[0]); i.band(-13, u_guard - 1, -1, -1, grip[1])
    i.band(-14, -13, -1, 0, pommel)
    i.band(u_guard, u_guard + 1, -4, 4, guard); i.band(u_guard, u_guard, -4, 4, guard_hi)
    i.band(u_guard + 2, u_tip - tip_len, -blade_w, blade_w, blade)
    i.band(u_guard + 2, u_tip - tip_len, -blade_w, -blade_w, edge)
    i.band(u_guard + 2, u_tip - tip_len, blade_w, blade_w, dark)
    for t in range(tip_len + 1):
        ww = max(0, blade_w - (t * blade_w + tip_len - 1) // max(tip_len, 1))
        i.band(u_tip - tip_len + t, u_tip - tip_len + t, -ww, ww, blade)
        i.band(u_tip - tip_len + t, u_tip - tip_len + t, -ww, -ww, edge)
    if extra: extra(i)
    return i

def greatsword():
    def gems(i):
        for u in range(-1, 10, 3): i.band(u, u, 0, 0, '#ff8cf0')
        i.band(-5, -5, 0, 0, '#ff40d0')
    i = sword('#6fd8ff', '#d8f8ff', '#2f7fc8', 3, 2, '#7a3fb0', '#b07ae0', ('#2a2038', '#4a3a60'), '#b07ae0', u_guard=-6, u_tip=14, extra=gems)
    i.outline('#1a1838'); return i.image()

def stormcaller():
    def bolt(i):
        for (u, w) in [(-1, 0), (0, 1), (1, 0), (2, -1), (3, 0), (4, 1), (5, 0), (6, -1), (7, 0), (8, 1), (9, 0)]:
            i.band(u, u, w, w, '#fff27a')
    i = sword('#3d5a8a', '#9fd4ff', '#22304e', 3, 2, '#c8a030', '#ffe070', ('#1e2433', '#3a4660'), '#8fe0ff', u_guard=-5, u_tip=13, extra=bolt)
    i.outline('#0e1220'); return i.image()

def chrono_scythe():
    """크로노 낫 (id chrono_dagger): long dark haft on the diagonal, clock gear at the head, wide gold crescent blade over the top"""
    i = Icon()
    for t in range(0, 11):                       # haft (1,14) -> (11,4), 2 px wide
        i.put(1 + t, 14 - t, '#3a2a1a'); i.put(2 + t, 14 - t, '#5a4228')
    for t in (2, 5, 8): i.put(1 + t, 14 - t, '#e0b848'); i.put(2 + t, 14 - t, '#c99a2e')   # gold rings
    i.put(0, 15, '#e0b848'); i.put(1, 15, '#c99a2e')                                     # butt cap
    for x in range(3, 12): i.put(x, 1, '#fff4c0')                                         # blade back edge (light)
    for x in range(1, 12): i.put(x, 2, '#f0d070')
    for x in range(4, 11): i.put(x, 3, '#b08020')                                         # inner shade
    i.put(2, 1, '#fff4c0'); i.put(1, 2, '#fff4c0'); i.put(0, 3, '#f0d070'); i.put(1, 3, '#f0d070'); i.put(2, 3, '#b08020')
    i.put(0, 4, '#f0d070'); i.put(0, 5, '#fff4c0')                                        # hooked tip
    for x in range(5, 10, 2): i.put(x, 2, '#2fb8a8')                                      # teal time runes
    for dx in range(-1, 2):                                                               # clock gear at the joint
        for dy in range(-1, 2): i.put(12 + dx, 3 + dy, '#c99a2e')
    i.put(12, 3, '#2fb8a8'); i.put(12, 1, '#e0b848'); i.put(14, 3, '#e0b848'); i.put(12, 5, '#e0b848'); i.put(13, 2, '#fff4c0')
    i.outline('#2a1a08'); return i.image()

def chrono_dagger():   # v1.3 icon kept for reference only (the item is now the scythe)
    return chrono_scythe()

FLICK = [0.0, 0.15, 1.0, 0.25, 0.0, 0.0, 0.6, 0.1]   # irregular lightning flicker (shared with the 3D texture)

def mixc(a, b, k):
    a, b = hexc(a), hexc(b); return tuple(int(x + (y - x) * k) for x, y in zip(a, b))

_AXE = None
def battleaxe(k=0.0):
    """stormcaller (뇌운검) icon rendered from the user's Meshy battleaxe mesh; gold veins flicker toward cyan-white"""
    global _AXE
    if _AXE is None:
        import mesh_icon
        _AXE = mesh_icon.mesh_icon('/workspace/mc/obj2mc/configs/weapon_stormcaller.json').astype(float)
    a = _AXE.copy()
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    on = a[..., 3] > 0
    gold = on & (r > 150) & (g > 100) & (b < 120)
    dark = on & ~gold
    a[dark, :3] *= 0.7                                   # deepen the black metal so gold pops like vanilla shading
    tgt = np.array([215, 250, 255.])
    a[gold, :3] = a[gold, :3] * (1 - 0.75 * k) + tgt * 0.75 * k
    i = Icon()
    for y in range(16):
        for x in range(16):
            if on[y, x]: i.put(x, y, tuple(int(c) for c in a[y, x, :3]) + (255,))
    i.outline('#0b0a0e'); return i.image()

def write_all():
    out = '/workspace/mc/resourcepack/hobbang-pack/assets/hobbang/textures/item/weapon'
    os.makedirs(out, exist_ok=True)
    frames = [hammer(k / 8) for k in range(8)]
    strip = Image.new('RGBA', (16, 16 * len(frames)))
    for k, f in enumerate(frames): strip.paste(f, (0, 16 * k))
    strip.save(f'{out}/smith_hammer_icon.png')
    json.dump({'animation': {'frametime': 3, 'interpolate': True}}, open(f'{out}/smith_hammer_icon.png.mcmeta', 'w'), indent=1)
    ax = [battleaxe(k) for k in FLICK]
    strip = Image.new('RGBA', (16, 16 * len(ax)))
    for k, f in enumerate(ax): strip.paste(f, (0, 16 * k))
    strip.save(f'{out}/stormcaller_icon.png')
    json.dump({'animation': {'frametime': 2}}, open(f'{out}/stormcaller_icon.png.mcmeta', 'w'), indent=1)
    for nm, fn in (('crystal_greatsword', greatsword), ('chrono_dagger', chrono_scythe)):
        fn().save(f'{out}/{nm}_icon.png')
    sheet = Image.new('RGBA', (16 * 5 + 6 * 2, 20), (60, 60, 70, 255))
    for k, im in enumerate([frames[0], greatsword(), battleaxe(0.0), chrono_scythe()]):
        sheet.alpha_composite(im, (2 + k * 18, 2))
    sheet.alpha_composite(battleaxe(1.0), (2 + 4 * 18, 2))
    sheet.resize((sheet.width * 12, sheet.height * 12), Image.NEAREST).save('/workspace/mc/resourcepack/previews/weapon_icons.png')


if __name__ == '__main__':
    write_all()
