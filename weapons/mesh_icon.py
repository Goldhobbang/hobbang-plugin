"""16x16 icon rendered from a source mesh (front view, rotated onto the vanilla 45deg tool diagonal)."""
import sys, json, numpy as np
from PIL import Image
sys.path.insert(0, '/workspace/mc/obj2mc')
import render, obj2mc as preview

def mesh_icon(cfg_path, size=16, ss=32, spin=-45):
    cfg = json.load(open(cfg_path))
    v, f, uv, tex = preview.load_mesh(cfg, "/")
    tris = v[f]; tuv = uv[f].copy(); tuv[..., 1] = 1 - tuv[..., 1]
    N = size * ss
    col, (c, e) = render.render(tris, tuv, tex, 0, 0, N, bg=(0, 0, 0), cull_back=False, nearest=False)
    white = np.full((2, 2, 3), 255, np.uint8)
    msk, _ = render.render(tris, tuv, white, 0, 0, N, c, e, bg=(0, 0, 0), cull_back=False)
    rgba = np.dstack([np.array(col), np.array(msk)[..., 0]])
    im = Image.fromarray(rgba.astype(np.uint8), 'RGBA').rotate(spin, expand=True, resample=Image.BICUBIC)
    im = im.crop(im.getbbox())
    w, h = im.size; s = max(w, h)
    sq = Image.new('RGBA', (s, s)); sq.paste(im, ((s - w) // 2, (s - h) // 2))
    a = np.array(sq.resize((size - 1, size - 1), Image.BOX)).astype(float)   # 15x15 leaves room for outline
    out = np.zeros((size, size, 4))
    out[0:size - 1, 1:size] = a
    al = out[..., 3] > 70
    rgb = np.where(al[..., None], out[..., :3] / np.maximum(out[..., 3:4] / 255, 1e-3), 0)
    rgb = np.clip(rgb * 1.15, 0, 255)
    res = np.zeros((size, size, 4), np.uint8); res[al, :3] = rgb[al]; res[al, 3] = 255
    return res

if __name__ == '__main__':
    r = mesh_icon(sys.argv[1]); Image.fromarray(r).resize((256, 256), Image.NEAREST).save('/tmp/mi.png')
