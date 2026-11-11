"""Offline pose check: rotate rig parts about their pivots through the parent chain (same maths as the plugin's
Rigid: child = parent.at(childPivot - parentPivot).rotX().rotY().rotZ()) and render, to look for joint gaps.
usage: python pose.py <manifest id e.g. boss__tempest_wyvern> <pose.json or inline json {"part":[rx,ry,rz],...}> out.png [yaw,pitch ...]"""
import json, os, sys
import numpy as np
import preview
from obb import rot
from render import render, label, grid

HERE = os.path.dirname(os.path.abspath(__file__))


def posed_tris(pack, man, pose):
    tris, uv, tex, pid = preview.pack_tris(pack, man)
    parts = man['parts']; names = [p['name'] for p in parts]
    piv = {p['name']: np.array(p['pivot_blocks'], float) for p in parts}
    par = {p['name']: p.get('parent') for p in parts}
    spec = {p['name']: p for p in parts}
    cache = {}
    def rx(r):
        # [rx, ry, rz] (applied X then Y then Z like Rigid.rotX().rotY().rotZ()) or an ordered list [["z", 30], ["y", -10], ...]
        if r and isinstance(r[0], (list, tuple)):
            R = np.eye(3)
            for ax, deg in r: R = R @ rot('xyz'.index(ax), deg)
            return R
        return rot(0, r[0]) @ rot(1, r[1]) @ rot(2, r[2])
    def xf(n):
        if n in cache: return cache[n]
        sp = spec[n]
        Rl = rx(pose.get(n, [0, 0, 0]))
        p = par[n]
        if 'local_offset' in sp:      # box rigs: chain of local offsets + rest rotations (DisplayBoss.joint)
            Rl = rx(sp['rest_rot']) @ Rl
            off = np.array(sp['local_offset'], float)
            if p is None or p not in piv:
                cache[n] = (off, Rl)
            else:
                pp, pr = xf(p)
                cache[n] = (pp + pr @ off, pr @ Rl)
        elif p is None or p not in piv:
            cache[n] = (piv[n].copy(), Rl)
        else:
            pp, pr = xf(p)
            cache[n] = (pp + pr @ (piv[n] - piv[p]), pr @ Rl)
        return cache[n]
    out = tris.copy()
    for i, n in enumerate(names):
        pos, R = xf(n)
        m = pid == i
        out[m] = pos + (tris[m] - piv[n]) @ R.T
    return out, uv, tex


def main():
    mid = sys.argv[1]
    pose = json.load(open(sys.argv[2])) if os.path.exists(sys.argv[2]) else json.loads(sys.argv[2])
    out = sys.argv[3]
    views = [tuple(map(float, a.split(','))) for a in sys.argv[4:]] or [(0, 5), (-40, 15), (90, 5), (150, 20)]
    pack = os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack')
    man = json.load(open(os.path.join(HERE, '..', 'resourcepack', 'manifests', mid + '.json')))
    t, uv, tex = posed_tris(pack, man, pose)
    H = man['height_blocks']
    lo, hi = t.reshape(-1, 3).min(0), t.reshape(-1, 3).max(0)
    c = (lo + hi) / 2; e = (hi - lo).max() * 1.08
    ims = [label(render(t, uv, tex, y, p, 640, c, e, nearest=True)[0], f'yaw {y:g} pitch {p:g}') for y, p in views]
    grid(ims, min(len(ims), 4)).save(out); print(out)


if __name__ == '__main__':
    main()
