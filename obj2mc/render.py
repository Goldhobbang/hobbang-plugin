"""Tiny headless software rasterizer (numpy) for previews.

Renders textured triangle soups with an orthographic camera and simple
directional shading. Used for both the source OBJ and generated Minecraft
cuboid models (parsed back from the emitted JSON so UVs are validated too).
"""
import numpy as np
from PIL import Image, ImageDraw


def view_matrix(yaw_deg, pitch_deg):
    """Rotation that maps world -> camera. Camera looks along -Z of camera space.
    yaw=0 looks at the model's front from +Z (world), pitch tilts down."""
    y, p = np.radians(yaw_deg), np.radians(pitch_deg)
    ry = np.array([[np.cos(y), 0, -np.sin(y)], [0, 1, 0], [np.sin(y), 0, np.cos(y)]])
    rx = np.array([[1, 0, 0], [0, np.cos(p), -np.sin(p)], [0, np.sin(p), np.cos(p)]])
    return rx @ ry


def rasterize(tris_cam, W, H):
    """tris_cam: (N,3,3) in pixel coords (x right, y down, z = depth, larger = nearer).
    Returns tri id per pixel (-1 none) and barycentrics (H,W,3)."""
    depth = np.full((H, W), -np.inf, dtype=np.float64)
    tid = np.full((H, W), -1, dtype=np.int64)
    bary = np.zeros((H, W, 3), dtype=np.float64)
    for i, t in enumerate(tris_cam):
        x0, y0 = t[:, 0].min(), t[:, 1].min()
        x1, y1 = t[:, 0].max(), t[:, 1].max()
        ix0, iy0 = max(int(np.floor(x0)), 0), max(int(np.floor(y0)), 0)
        ix1, iy1 = min(int(np.ceil(x1)), W - 1), min(int(np.ceil(y1)), H - 1)
        if ix1 < ix0 or iy1 < iy0:
            continue
        (ax, ay, az), (bx, by, bz), (cx, cy, cz) = t
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-12:
            continue
        xs = np.arange(ix0, ix1 + 1) + 0.5
        ys = np.arange(iy0, iy1 + 1) + 0.5
        X, Y = np.meshgrid(xs, ys)
        w0 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
        w1 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any():
            continue
        z = w0 * az + w1 * bz + w2 * cz
        sub = depth[iy0:iy1 + 1, ix0:ix1 + 1]
        m &= z > sub
        if not m.any():
            continue
        sub[m] = z[m]
        tid[iy0:iy1 + 1, ix0:ix1 + 1][m] = i
        bsub = bary[iy0:iy1 + 1, ix0:ix1 + 1]
        bsub[m] = np.stack([w0[m], w1[m], w2[m]], -1)
    return tid, bary


def render(tris, uvs, tex, yaw, pitch, size=512, center=None, extent=None,
           bg=(40, 44, 52), cull_back=True, nearest=True):
    """tris: (N,3,3) world coords (Y up). uvs: (N,3,2) in [0,1] (v down = image row).
    tex: HxWx3/4 uint8 array. Returns PIL image."""
    R = view_matrix(yaw, pitch)
    if center is None:
        lo, hi = tris.reshape(-1, 3).min(0), tris.reshape(-1, 3).max(0)
        center = (lo + hi) / 2
        extent = (hi - lo).max() * 1.15
    cam = (tris - center) @ R.T
    s = size / extent
    px = np.empty_like(cam)
    px[..., 0] = cam[..., 0] * s + size / 2
    px[..., 1] = -cam[..., 1] * s + size / 2
    px[..., 2] = cam[..., 2]
    n_world = np.cross(tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0])
    n_cam = n_world @ R.T
    keep = np.ones(len(tris), bool)
    if cull_back:
        keep = n_cam[:, 2] > 0
    idx = np.nonzero(keep)[0]
    tid, bary = rasterize(px[idx], size, size)
    img = np.zeros((size, size, 3), np.float64)
    img[:] = bg
    hit = tid >= 0
    t = idx[tid[hit]]
    b = bary[hit]
    uv = (uvs[t] * b[:, :, None]).sum(1)
    th, tw = tex.shape[:2]
    if nearest:
        u = np.clip((uv[:, 0] * tw).astype(int), 0, tw - 1)
        v = np.clip((uv[:, 1] * th).astype(int), 0, th - 1)
    else:
        u = np.clip(np.round(uv[:, 0] * tw - 0.5).astype(int), 0, tw - 1)
        v = np.clip(np.round(uv[:, 1] * th - 0.5).astype(int), 0, th - 1)
    col = tex[v, u, :3].astype(np.float64)
    nw = n_world[t]
    nw /= np.linalg.norm(nw, axis=1, keepdims=True) + 1e-12
    light = np.array([0.35, 0.8, 0.5]); light /= np.linalg.norm(light)
    shade = 0.45 + 0.55 * np.clip(np.abs(nw @ light) if not cull_back else nw @ light, 0, 1)
    img[hit] = col * shade[:, None]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)), (center, extent)


def label(img, text):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, img.width, 18], fill=(0, 0, 0))
    d.text((5, 3), text, fill=(255, 255, 255))
    return img


def grid(images, cols):
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    out = Image.new('RGB', (w * cols, h * rows), (20, 20, 20))
    for i, im in enumerate(images):
        out.paste(im, ((i % cols) * w, (i // cols) * h))
    return out
