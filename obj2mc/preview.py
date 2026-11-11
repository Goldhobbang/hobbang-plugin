"""Preview renderer: re-reads the emitted pack (models + textures) and renders the
assembled cuboid boss next to the source mesh. Uses Minecraft's own FaceInfo/UV rules
so a wrong UV or face mapping in the JSON shows up in the image."""
import json, os, sys
import numpy as np
from PIL import Image, ImageDraw
from render import render, label, grid
from obj2mc import FACE_VERTS, FACE_NORMAL, load_mesh

PART_COLORS = [(230, 80, 80), (80, 200, 90), (80, 140, 240), (240, 200, 60), (200, 90, 220), (70, 210, 210), (240, 140, 60), (160, 160, 160)]


def pack_tris(pack, man):
    ns, mid = man['id'].split(':')
    k = man['model_units_per_block']
    all_t, all_uv, texs, pid = [], [], [], []
    for pi, p in enumerate(man['parts']):
        name = p['name']
        model = json.load(open(os.path.join(pack, 'assets', ns, 'models', 'item', *mid.split('/'), name + '.json')))
        tref = model['textures']['t']
        tns, tpath = tref.split(':')
        tex = np.array(Image.open(os.path.join(pack, 'assets', tns, 'textures', tpath + '.png')).convert('RGBA'))
        piv = np.array(p['translation']); s = p['scale']
        tris, uvs = [], []
        for el in model['elements']:
            fr, to = np.array(el['from']), np.array(el['to'])
            for fn, fd in el['faces'].items():
                u1, v1, u2, v2 = fd['uv']
                vs = np.array(FACE_VERTS[fn], float)
                P = fr + (to - fr) * vs                        # model units
                UV = np.array([[u1 if i in (0, 1) else u2, v1 if i in (0, 3) else v2] for i in range(4)]) / 16.0
                # item_display: translation + scale * Ry(180) * (m/16 - 0.5)
                q = (P / 16.0 - 0.5) * s
                q = np.stack([-q[:, 0], q[:, 1], -q[:, 2]], 1) + piv
                n_exp = np.array(FACE_NORMAL[fn], float) * [-1, 1, -1]
                for tri in ((0, 1, 2), (0, 2, 3)):
                    T = q[list(tri)]; U = UV[list(tri)]
                    if np.dot(np.cross(T[1] - T[0], T[2] - T[0]), n_exp) < 0:
                        T = T[[0, 2, 1]]; U = U[[0, 2, 1]]
                    tris.append(T); uvs.append(U)
        all_t.append(np.array(tris)); all_uv.append(np.array(uvs)); texs.append(tex); pid.append(np.full(len(tris), pi))
    # stack textures into one image for the single-texture renderer
    W = max(t.shape[1] for t in texs); H = sum(t.shape[0] for t in texs)
    big = np.zeros((H, W, 4), np.uint8); y = 0; out_uv = []
    for t, u in zip(texs, all_uv):
        big[y:y + t.shape[0], :t.shape[1]] = t
        uu = u.copy(); uu[..., 0] = u[..., 0] * t.shape[1] / W; uu[..., 1] = (u[..., 1] * t.shape[0] + y) / H
        out_uv.append(uu); y += t.shape[0]
    return np.vstack(all_t), np.vstack(out_uv), big, np.concatenate(pid)


VIEWS = [('front', 0, 8), ('3/4', -40, 15), ('side', 90, 5), ('back 3/4', 145, 15)]


def make_previews(cfg, base, pack, man, outdir='/workspace/mc/resourcepack/previews', size=480):
    os.makedirs(outdir, exist_ok=True)
    tag = man['id'].split('/')[-1]
    v, f, uv, tex = load_mesh(cfg, base)
    otris = v[f]; ouv = uv[f].copy(); ouv[..., 1] = 1 - ouv[..., 1]
    ctris, cuv, ctex, pid = pack_tris(pack, man)
    H = cfg['height_blocks']
    center = np.array([0, H / 2, 0]); ext = max(H, 2 * np.abs(v[:, [0, 2]]).max()) * 1.08
    top, bot, parts = [], [], []
    for name, yaw, pitch in VIEWS:
        a, _ = render(otris, ouv, tex, yaw, pitch, size, center, ext, nearest=False)
        b, _ = render(ctris, cuv, ctex, yaw, pitch, size, center, ext, nearest=True)
        top.append(label(a, f'source mesh ({len(f)} tris) - {name}'))
        bot.append(label(b, f'cuboids ({man["total_cuboids"]}) @ {man["resolution"]:g} vox/block - {name}'))
        b.save(os.path.join(outdir, f'{tag}_cuboid_{name.replace("/", "-").replace(" ", "_")}.png'))
    # part map (flat colour per part)
    pal = np.array([PART_COLORS[i % len(PART_COLORS)] + (255,) for i in range(len(man['parts']))], np.uint8)
    ptex = pal[None, :, :]
    puv = np.zeros(ctris.shape[:2] + (2,)); puv[..., 0] = ((pid + 0.5) / len(man['parts']))[:, None]; puv[..., 1] = 0.5
    for name, yaw, pitch in VIEWS[:3]:
        c, _ = render(ctris, puv, ptex, yaw, pitch, size, center, ext, nearest=True)
        d = ImageDraw.Draw(c)
        for i, p in enumerate(man['parts']):
            d.rectangle([5, 25 + 14 * i, 15, 35 + 14 * i], fill=tuple(int(x) for x in pal[i][:3]))
            d.text((20, 24 + 14 * i), f"{p['name']} ({p['cuboids']})", fill=(255, 255, 255))
        parts.append(label(c, f'parts - {name}'))
    cmp_img = grid(top + bot, len(VIEWS))
    cmp_img.save(os.path.join(outdir, f'{tag}_compare.png'))
    grid(parts, 3).save(os.path.join(outdir, f'{tag}_parts.png'))
    print('[preview] wrote', os.path.join(outdir, f'{tag}_compare.png'), 'and', f'{tag}_parts.png')
