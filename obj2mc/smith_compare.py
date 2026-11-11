"""Abyssal Smith before / after sheet: original mesh, previous pack model, current pack model in the Idle pose that
BossPoses.smith produces (hammer attached), front / 3-4 / side / rear 3-4. Also writes a 3x texture sheet.
usage: python smith_compare.py POSES.tsv OUT_DIR LABEL=ROOT [LABEL=ROOT ...]   (ROOT holds resourcepack/{hobbang-pack,manifests};
       the last row is always the current pack, labelled "current" unless given as LABEL=.)
Also: head / chest / hammer close-ups, the docs/reference armour images next to the current model, metrics."""
import csv, json, sys
from pathlib import Path
import numpy as np
from PIL import Image
from obj2mc import load_mesh
from preview import pack_tris
from render import grid, label, render
from render_boss_poses import rotation

ROOT = Path(__file__).resolve().parent.parent
VIEWS = ((0, 6, 'front'), (-40, 14, '3/4'), (90, 6, 'side'), (145, 14, 'rear 3/4'))
S = 520


def posed(base, frame):
    man = lambda b: json.loads((base / 'manifests' / f'boss__{b}.json').read_text(encoding='utf-8'))
    model, hammer = man('abyssal_smith'), man('smith_hammer')
    parts = model['parts'] + hammer['parts']
    tris, uv, tex, pid = pack_tris(base / 'hobbang-pack', model, [hammer])
    out = tris.copy()
    for i, part in enumerate(parts):
        m = pid == i
        x, y, z, qx, qy, qz, qw, sc = frame[part['name']]
        out[m] = ((tris[m] - np.array(part['translation'])) * sc) @ rotation(qx, qy, qz, qw).T + [x, y, z]
    return out, uv, tex, model['total_cuboids'] + hammer['total_cuboids']


def main(tsv, out, *rows_spec):
    frame = {}
    for boss, title, part, *v in csv.reader(open(tsv, encoding='utf-8'), delimiter='\t'):
        if boss == 'abyssal_smith' and title == 'Idle': frame[part] = [float(x) for x in v]
    cfgp = ROOT / 'obj2mc' / 'configs' / 'abyssal_smith.json'
    cfg = json.loads(cfgp.read_text(encoding='utf-8'))
    v, f, muv, mtex = load_mesh(cfg, cfgp.parent)
    muv = muv[f].copy(); muv[..., 1] = 1 - muv[..., 1]
    H = cfg['height_blocks']; c = np.array([0, H / 2, 0]); e = H * 1.3
    rows = [(f'original mesh ({len(f):,} tris)', (v[f], muv, mtex), False)]
    specs = [r.split('=', 1) for r in rows_spec] or [['current', '']]
    for name, base in specs:
        base = Path(base) / 'resourcepack' if base else ROOT / 'resourcepack'
        t, u, x, n = posed(base, frame)
        rows.append((f'{name} ({n} cuboids)', (t, u, x), True))
    tiles = []
    for title, (t, u, x), pixel in rows:
        for yaw, pitch, vn in VIEWS:
            im, _ = render(t, u, x, yaw, pitch, S, c, e, nearest=pixel)
            tiles.append(label(im, f'{title} - {vn}'))
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    # numbers: silhouette overlap of the REST model (body only, no pose, no hammer) against the mesh, and overall bbox
    man = json.loads((ROOT / 'resourcepack' / 'manifests' / 'boss__abyssal_smith.json').read_text(encoding='utf-8'))
    rt, ru, rx, _ = pack_tris(ROOT / 'resourcepack' / 'hobbang-pack', man)
    bg = np.array([40, 44, 52])
    lines = []
    for yaw, name in ((0, 'front'), (90, 'side'), (-40, '3/4')):
        a = np.abs(np.asarray(render(v[f], muv, mtex, yaw, 0, 400, c, e, nearest=False)[0], int) - bg).sum(-1) > 12
        b = np.abs(np.asarray(render(rt, ru, rx, yaw, 0, 400, c, e, nearest=True)[0], int) - bg).sum(-1) > 12
        lines.append(f'silhouette {name}: IoU {(a & b).sum() / (a | b).sum():.3f}, model area / mesh area {b.sum() / a.sum():.3f}')
    for p_ in man['parts']: lines.append(f"  cuboids {p_['name']:7s} {p_['cuboids']:4d}  atlas {p_['texture_px']}px")
    hm = json.loads((ROOT / 'resourcepack' / 'manifests' / 'boss__smith_hammer.json').read_text(encoding='utf-8'))
    lines.append(f"  cuboids body {man['total_cuboids']}  hammer {hm['total_cuboids']} (atlas {hm['parts'][0]['texture_px']}px)")
    mb = np.array([v.min(0), v.max(0)]); pb = np.array([rt.reshape(-1, 3).min(0), rt.reshape(-1, 3).max(0)])
    lines.append('mesh  bbox w/h/d ' + ' / '.join(f'{x:.2f}' for x in mb[1] - mb[0]))
    lines.append('model bbox w/h/d ' + ' / '.join(f'{x:.2f}' for x in pb[1] - pb[0]))
    (out / 'abyssal_smith_metrics.txt').write_text(chr(10).join(lines) + chr(10), encoding='utf-8')
    print(chr(10).join(lines))
    grid(tiles, len(VIEWS)).save(out / 'abyssal_smith_compare.jpg', quality=93)
    close = []
    t, u, x, n = posed(ROOT / 'resourcepack', frame)
    for cc, ee, name in ((np.array([0, 7.6, 0]), 3.6, 'head'), (np.array([0, 5.6, 0]), 5.2, 'chest')):
        for yaw, pitch, vn in ((0, 4, 'front'), (-35, 10, '3/4'), (90, 4, 'side'), (150, 12, 'rear 3/4')):
            close.append(label(render(t, u, x, yaw, pitch, S, cc, ee, nearest=True)[0], f'{name} - {vn}'))
    ht, hu, hx_, _ = pack_tris(ROOT / 'resourcepack' / 'hobbang-pack', hm)
    for yaw, pitch, vn in ((90, 4, 'side'), (35, 14, '3/4'), (0, 4, 'end'), (-130, 20, 'rear 3/4')):
        close.append(label(render(ht, hu, hx_, yaw, pitch, S, np.array([0.1, 2.0, 0]), 5.0, nearest=True)[0], f'hammer - {vn}'))
    grid(close, 4).save(out / 'abyssal_smith_closeup.jpg', quality=93)
    # the armour references next to the model
    refs = [Image.open(ROOT / 'docs' / 'reference' / n).convert('RGB') for n in ('smith_armor_ref_1.png', 'smith_armor_ref_2.png')]
    refs = [r.resize((int(r.width * S / r.height), S), Image.NEAREST) for r in refs]
    mine = [label(render(t, u, x, yaw, pitch, S, c, e, nearest=True)[0], f'current - {vn}') for yaw, pitch, vn in ((0, 6, 'front'), (-35, 12, '3/4'))]
    ims = [label(r, f'reference {i + 1}') for i, r in enumerate(refs)] + mine
    side = Image.new('RGB', (sum(i.width for i in ims), S), (20, 20, 20)); xo = 0
    for i in ims: side.paste(i, (xo, 0)); xo += i.width
    side.save(out / 'abyssal_smith_vs_reference.jpg', quality=93)
    # texture sheet, 3x nearest
    tex_dir = ROOT / 'resourcepack' / 'hobbang-pack' / 'assets' / 'hobbang' / 'textures' / 'item' / 'boss'
    ims = [(p.parent.name + '/' + p.stem, Image.open(p).convert('RGBA')) for d in ('abyssal_smith', 'smith_hammer')
           for p in sorted((tex_dir / d).glob('*.png'))]
    cell = 3 * max(i.width for _, i in ims) + 8
    sheet = Image.new('RGB', (cell * 4, (cell + 14) * ((len(ims) + 3) // 4)), (30, 32, 38))
    for k, (name, im) in enumerate(ims):
        big = im.resize((im.width * 3, im.height * 3), Image.NEAREST)
        tile = Image.new('RGB', (cell, cell + 14), (30, 32, 38)); tile.paste(big, (4, 18), big)
        sheet.paste(label(tile, name), ((k % 4) * cell, (k // 4) * (cell + 14)))
    sheet.save(out / 'abyssal_smith_textures.png')
    print(out / 'abyssal_smith_compare.jpg')


if __name__ == '__main__':
    main(*sys.argv[1:])
