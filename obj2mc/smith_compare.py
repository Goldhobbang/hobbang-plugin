"""Abyssal Smith before / after sheet: original mesh, previous pack model, current pack model in the Idle pose that
BossPoses.smith produces (hammer attached), front / 3-4 / side / rear 3-4. Also writes a 3x texture sheet.
usage: python smith_compare.py POSES.tsv OLD_PACK_ROOT OUT_DIR   (OLD_PACK_ROOT holds resourcepack/{hobbang-pack,manifests})"""
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


def main(tsv, old_root, out):
    frame = {}
    for boss, title, part, *v in csv.reader(open(tsv, encoding='utf-8'), delimiter='\t'):
        if boss == 'abyssal_smith' and title == 'Idle': frame[part] = [float(x) for x in v]
    cfgp = ROOT / 'obj2mc' / 'configs' / 'abyssal_smith.json'
    cfg = json.loads(cfgp.read_text(encoding='utf-8'))
    v, f, muv, mtex = load_mesh(cfg, cfgp.parent)
    muv = muv[f].copy(); muv[..., 1] = 1 - muv[..., 1]
    H = cfg['height_blocks']; c = np.array([0, H / 2, 0]); e = H * 1.3
    rows = [(f'original mesh ({len(f):,} tris)', (v[f], muv, mtex), False)]
    for name, base in (('before', Path(old_root) / 'resourcepack'), ('after', ROOT / 'resourcepack')):
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
    mb = np.array([v.min(0), v.max(0)]); pb = np.array([rt.reshape(-1, 3).min(0), rt.reshape(-1, 3).max(0)])
    lines.append('mesh  bbox w/h/d ' + ' / '.join(f'{x:.2f}' for x in mb[1] - mb[0]))
    lines.append('model bbox w/h/d ' + ' / '.join(f'{x:.2f}' for x in pb[1] - pb[0]))
    (out / 'abyssal_smith_metrics.txt').write_text(chr(10).join(lines) + chr(10), encoding='utf-8')
    print(chr(10).join(lines))
    grid(tiles, len(VIEWS)).save(out / 'abyssal_smith_compare.jpg', quality=93)
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
    main(*sys.argv[1:4])
