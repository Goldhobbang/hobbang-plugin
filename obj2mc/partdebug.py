"""Config-authoring helper: front/side part-colour views with a 0.25-block grid, plus
horizontal (XZ) slices of the part labels. Usage: python partdebug.py configs/x.json"""
import sys, json, os
import numpy as np
from PIL import Image, ImageDraw
import obj2mc, preview
from render import render, label, grid

def gridify(im, center, ext, horiz_axis_sign=1):
    S = im.width; sc = S / ext; d = ImageDraw.Draw(im)
    for k in np.arange(-5, 5.01, 0.25):
        x = horiz_axis_sign * (k - 0) * sc + S / 2
        d.line([x, 0, x, S], fill=(255, 255, 0) if abs(k % 1) < 1e-6 else (110, 110, 110))
        if abs(k * 2 % 1) < 1e-6: d.text((x + 2, S - 14), f'{k:g}', fill=(255, 255, 0))
    for k in np.arange(0, 10.01, 0.25):
        y = -(k - center[1]) * sc + S / 2
        d.line([0, y, S, y], fill=(255, 255, 0) if abs(k % 1) < 1e-6 else (110, 110, 110))
        if abs(k * 2 % 1) < 1e-6: d.text((2, y + 2), f'{k:g}', fill=(255, 255, 0))
    return im

cfg = json.load(open(sys.argv[1])); base = os.path.dirname(os.path.abspath(sys.argv[1]))
pack = os.environ.get('PACK', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'resourcepack', 'hobbang-pack'))
mid = cfg['id']
man = json.load(open(os.path.join(os.path.dirname(os.path.abspath(pack)), 'manifests', mid.replace('/', '__') + '.json')))
v, f, uv, tex = obj2mc.load_mesh(cfg, base)
ot = v[f]; ou = uv[f].copy(); ou[..., 1] = 1 - ou[..., 1]
ct, cu, cx, pid = preview.pack_tris(pack, man)
pal = np.array([preview.PART_COLORS[i % 8] + (255,) for i in range(len(man['parts']))], np.uint8)[None]
puv = np.zeros(ct.shape[:2] + (2,)); puv[..., 0] = ((pid + .5) / len(man['parts']))[:, None]; puv[..., 1] = .5
H = cfg['height_blocks']; c = np.array([0, H / 2, 0]); e = max(H, 2 * np.abs(v[:, [0, 2]]).max()) * 1.1
ims = []
for yaw, nm, sg in [(0, 'front (horiz=+X)', 1), (90, 'side from +X (horiz=-Z)', 1)]:
    a, _ = render(ot, ou, tex, yaw, 0, 700, c, e, nearest=False)
    b, _ = render(ct, puv, pal, yaw, 0, 700, c, e)
    blend = Image.blend(a, b, 0.45)
    dr = ImageDraw.Draw(blend)
    for i, p in enumerate(man['parts']):
        dr.rectangle([5, 25 + 14 * i, 15, 35 + 14 * i], fill=tuple(int(x) for x in pal[0, i, :3])); dr.text((20, 24 + 14 * i), p['name'], fill=(255, 255, 255))
    ims.append(label(gridify(blend, c, e), nm))
out = os.path.join(os.environ.get('PREVIEWS', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'work', 'previews')), mid.split('/')[-1] + '_partdebug.png')
grid(ims, 2).save(out); print(out)
