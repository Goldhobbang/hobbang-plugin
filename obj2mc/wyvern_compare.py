"""docs/wyvern_redesign_compare.png: style reference next to offline renders of the 1.5.0 box wyvern
(side / front / top in the rest pose, and a flight pose). usage: python wyvern_compare.py"""
import json, os, sys
import numpy as np
from PIL import Image, ImageDraw
import pose
from render import render, label

HERE = os.path.dirname(os.path.abspath(__file__))
pack = os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack')
man = json.load(open(os.path.join(HERE, '..', 'resourcepack', 'manifests', 'boss__tempest_wyvern.json')))


def wings(a, fold, rip=0):
    d = {}
    for sx, q in ((1, '_l'), (-1, '_r')):
        d['wing_arm' + q] = [['z', sx * a], ['y', sx * 30 * fold]]
        d['wing_fore' + q] = [['y', sx * 62 * fold]]
        for f, c in enumerate((70, 38, 6)):
            d[f'wing_f{f + 1}{q}'] = [['y', sx * c * fold], ['z', sx * rip * (1 - fold)]]
    return d


fly = dict(wings(-30, 0.0, 4))
for t in ('fl', 'fr'): fly.update({'thigh_' + t: [70, 0, 0], 'shin_' + t: [35, 0, 0], 'foot_' + t: [45, 0, 0]})
for t in ('hl', 'hr'): fly.update({'thigh_' + t: [85, 0, 0], 'shin_' + t: [10, 0, 0], 'foot_' + t: [50, 0, 0]})
fly.update({'neck_1': [24, 0, 0], 'neck_2': [8, 0, 0], 'neck_3': [-8, 0, 0], 'head': [-20, 0, 0], 'jaw': [20, 0, 0]})
fly.update({f'tail_{i + 1}': [3, [8, 9, 6, -2, -8, -10][i], 0] for i in range(6)})

S = 640
def shot(p, yaw, pitch, text):
    t, uv, tex = pose.posed_tris(pack, man, p)
    lo, hi = t.reshape(-1, 3).min(0), t.reshape(-1, 3).max(0)
    im, _ = render(t, uv, tex, yaw, pitch, S, (lo + hi) / 2, (hi - lo).max() * 1.06, bg=(232, 226, 210), nearest=True)
    return label(im, text)

tiles = [shot({}, 90, 4, 'side (rest pose)'), shot({}, 0, 4, 'front (rest pose)'), shot({}, 0, 89, 'top (rest pose)'),
         shot(fly, 115, 14, 'flight pose')]
ref = Image.open(os.path.join(HERE, '..', 'docs', 'reference', 'wyvern_style_ref.png')).convert('RGB')
W = S * 4
ref = ref.resize((W // 2, int(ref.height * (W // 2) / ref.width)))
out = Image.new('RGB', (W, ref.height + S + 40), (24, 24, 28))
out.paste(ref, (W // 4, 30))
d = ImageDraw.Draw(out)
d.text((W // 4, 8), 'style reference (docs/reference/wyvern_style_ref.png) - box structure only; colours follow the original Tempest Wyvern', fill=(255, 255, 255))
for i, t in enumerate(tiles):
    out.paste(t, (i * S, ref.height + 40))
dst = os.path.join(HERE, '..', 'docs', 'wyvern_redesign_compare.png')
out.save(dst, optimize=True); print(dst, man['total_cuboids'], 'cuboids', len(man['parts']), 'parts')
