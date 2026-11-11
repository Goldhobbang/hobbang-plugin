"""Fixed-camera rest-pose renders of a boss for before/after comparisons (parallel cleanup work).
usage: python render_cleanup.py <boss id> <tag: before|after> ['{pose json}']       -> ../work/cleanup/<boss>_<tag>.png (+ .json stats)
       python render_cleanup.py --join <boss id>                       -> ../docs/img/parallel/<boss>_before_after.jpg
The camera (centre, extent) depends only on the manifest height, so both renders line up pixel for pixel."""
import json, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageChops
import pose
from render import render, label

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'work', 'cleanup')
VIEWS = [(0, 6, 'front'), (-40, 14, '3/4'), (90, 6, 'side'), (150, 14, 'back 3/4')]
S = 560


def shots(boss, pz=None):
    pack = os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack')
    man = json.load(open(os.path.join(HERE, '..', 'resourcepack', 'manifests', f'boss__{boss}.json')))
    t, uv, tex = pose.posed_tris(pack, man, pz or {})
    H = man['height_blocks']
    c = np.array([0, H / 2, 0]); e = H * 1.25
    ims = [render(t, uv, tex, y, p, S, c, e, nearest=True)[0] for y, p, _ in VIEWS]
    return ims, man


def main():
    os.makedirs(OUT, exist_ok=True)
    if sys.argv[1] == '--join':
        boss = sys.argv[2]            # '<boss>' or '<boss>_posed'
        rows = []
        stats = {}
        for tag in ('before', 'after'):
            im = Image.open(os.path.join(OUT, f'{boss}_{tag}.png')).convert('RGB')
            stats[tag] = json.load(open(os.path.join(OUT, f'{boss}_{tag}.json')))
            rows.append(im)
        diff = ImageChops.difference(rows[0], rows[1]).convert('L').point(lambda v: 255 if v > 24 else 0)
        mark = rows[0].copy()
        red = Image.new('RGB', mark.size, (255, 40, 40))
        mark.paste(red, mask=diff)          # removed / changed pixels in red on top of the before render
        W, Hh = rows[0].size
        out = Image.new('RGB', (W, Hh * 3 + 66), (16, 16, 20))
        d = ImageDraw.Draw(out)
        for i, (im, txt) in enumerate(((rows[0], f"BEFORE (1.5.0)  cuboids {stats['before']['cuboids']}"),
                                       (rows[1], f"AFTER (cleanup)  cuboids {stats['after']['cuboids']}"),
                                       (mark, 'DIFFERENCE: pixels that changed, painted red over the before render'))):
            y = i * (Hh + 22)
            d.text((6, y + 5), txt, fill=(255, 255, 255))
            out.paste(im, (0, y + 22))
        dst = os.path.join(HERE, '..', 'docs', 'img', 'parallel')
        os.makedirs(dst, exist_ok=True)
        out.save(os.path.join(dst, f'{boss}_before_after.jpg'), quality=88)
        print(os.path.join(dst, f'{boss}_before_after.jpg'), 'changed px', int(np.array(diff).astype(bool).sum()))
        return
    boss, tag = sys.argv[1], sys.argv[2]
    pz = json.loads(sys.argv[3]) if len(sys.argv) > 3 else None      # optional pose -> files get a _posed suffix
    ims, man = shots(boss, pz)
    if pz: boss += '_posed'
    row = Image.new('RGB', (S * len(ims), S))
    for i, (im, (_, _, name)) in enumerate(zip(ims, VIEWS)):
        row.paste(label(im, name), (i * S, 0))
    row.save(os.path.join(OUT, f'{boss}_{tag}.png'))
    json.dump({'cuboids': man['total_cuboids'], 'parts': {p['name']: p['cuboids'] for p in man['parts']},
               'pivots': {p['name']: p['pivot_blocks'] for p in man['parts']}, 'parents': {p['name']: p['parent'] for p in man['parts']}},
              open(os.path.join(OUT, f'{boss}_{tag}.json'), 'w'), indent=1)
    print(boss, tag, man['total_cuboids'])


if __name__ == '__main__':
    main()
