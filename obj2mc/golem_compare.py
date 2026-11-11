"""Golem remodel deliverables in docs/img/golems-remodel/:
  <boss>_compare.jpg   rows: original Meshy mesh / 1.5.0 voxel model (git HEAD before the remodel, extracted to
                       work/old/) / remodel — same camera, columns front, 3/4, side
  palette_swatches.png every colour make_golems.py uses, grouped as in the source texture families
usage: python golem_compare.py   (run make_golems.py first; work/old/ holds the 1.5.0 pack files)"""
import json, os
import numpy as np
from PIL import Image, ImageDraw
import obj2mc, preview, make_golems as G
from render import render, label

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
OUT = os.path.join(ROOT, 'docs', 'img', 'golems-remodel')
VIEWS = [(0, 6, 'front'), (-40, 14, '3/4'), (90, 6, 'side')]
S = 520


def rows_for(boss):
    cfg = json.load(open(os.path.join(HERE, 'configs', boss + '.v144-voxel.json')))
    v, f, uv, tex = obj2mc.load_mesh(cfg, os.path.join(HERE, 'configs'))
    ot = v[f]; ou = uv[f].copy(); ou[..., 1] = 1 - ou[..., 1]
    H = cfg['height_blocks']
    c = np.array([0, H / 2, 0]); e = H * 1.22
    old_man = json.load(open(os.path.join(ROOT, 'work', 'old', 'resourcepack', 'manifests', f'boss__{boss}.json')))
    new_man = json.load(open(os.path.join(ROOT, 'resourcepack', 'manifests', f'boss__{boss}.json')))
    old = preview.pack_tris(os.path.join(ROOT, 'work', 'old', 'resourcepack', 'hobbang-pack'), old_man)
    new = preview.pack_tris(os.path.join(ROOT, 'resourcepack', 'hobbang-pack'), new_man)
    out = []
    for title, src in ((f'original mesh ({len(f):,} tris)', None),
                       (f'1.5.0 voxel ({old_man["total_cuboids"]:,} cuboids)', old),
                       (f'remodel ({new_man["total_cuboids"]:,} cuboids)', new)):
        row = []
        for y, p, name in VIEWS:
            if src is None:
                im, _ = render(ot, ou, tex, y, p, S, c, e, nearest=False)
            else:
                im, _ = render(src[0], src[1], src[2], y, p, S, c, e, nearest=True)
            row.append(label(im, f'{title} - {name}'))
        out.append(row)
    return out


def sheet(boss):
    rows = rows_for(boss)
    img = Image.new('RGB', (S * 3, S * 3))
    for r, row in enumerate(rows):
        for c_, im in enumerate(row):
            img.paste(im, (c_ * S, r * S))
    img.save(os.path.join(OUT, f'{boss}_compare.jpg'), quality=90)
    print(os.path.join(OUT, f'{boss}_compare.jpg'))


def swatches():
    groups = [
        ('Colossus stone (texture: surface p30-p99 + light stone family)', [G.S_VOID, G.S_DARK, G.S_BASE, G.S_MID, G.S_LIGHT, G.S_HI, G.S_EDGE]),
        ('Colossus amethyst (crystal family p5-p99 + bright family)', G.CR),
        ('Sentinel brass (brass family p5-p99)', G.BR),
        ('Sentinel dial gold (dial face family)', G.GOLD),
        ('Sentinel shine / dark / hands', G.SHINE + G.DARK + G.HANDS),
        ('Sentinel patina / hourglass sand / own teal', G.PATINA + G.SAND + G.TEAL_DIM),
        ('Time-rune teal (chrono scythe original texture)', G.TEAL),
    ]
    sw, pad = 64, 8
    W = pad + max(len(c) for _, c in groups) * (sw + pad)
    H = len(groups) * (sw + 34) + pad
    img = Image.new('RGB', (max(W, 760), H), (24, 24, 28))
    d = ImageDraw.Draw(img)
    y = pad
    for title, cols in groups:
        d.text((pad, y), title, fill=(255, 255, 255))
        for i, c in enumerate(cols):
            x = pad + i * (sw + pad)
            d.rectangle([x, y + 16, x + sw, y + 16 + sw - 12], fill=tuple(c))
            d.text((x, y + 16 + sw - 10), '#%02x%02x%02x' % tuple(c), fill=(220, 220, 220))
        y += sw + 34
    img.save(os.path.join(OUT, 'palette_swatches.png'))
    print(os.path.join(OUT, 'palette_swatches.png'))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for b in ('prism_colossus', 'chrono_sentinel'):
        sheet(b)
    swatches()
