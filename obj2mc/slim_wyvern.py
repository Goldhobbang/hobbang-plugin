#!/usr/bin/env python3
"""Tempest Wyvern slim pass (idempotent).

1. body / waist / hips model elements are squeezed (X about the part axis, Y a little) -> slimmer trunk.
   The head (v4, -20 %), neck, tail and wing parts are NOT touched, so the head does not read bigger.
   A marker in the model "credit" keeps a second run from squeezing again.
2. thigh/shin/foot (x4 legs) are rebuilt as thin, jointed, reptilian legs: tapered thigh + knee spike, long thin shin with
   a front crest and a hock spur, a short heel, three splayed toes with talons and a dew-claw. Built with make_wyvern's own
   box/texture pipeline into a scratch pack, then ONLY the 12 leg parts are copied over, so every other part stays as it is.
3. resourcepack/manifests/boss__tempest_wyvern.json local_offset of the legs / wing roots is moved inward with the body
   (BossModels.java rig() lines must carry the same numbers - see RIG below).

usage: python slim_wyvern.py [--pack DIR]
Part names (36) and rest rotations are unchanged.
"""
import argparse, json, os, shutil, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import make_wyvern as W  # noqa: E402

MARK = ' + slim'
# X squeeze about the part axis (model x = 8), Y: lower half compressed, upper part shifted down by the same amount
TRUNK = {'body': 0.78, 'waist': 0.80, 'hips': 0.82}
Y_SQUEEZE = 0.88
Y_PIVOT, Y_KNEE = 8.0, 16.0

# new attach points (design units, before SCALE) -> rig() x offsets in blocks = value * 0.68
LEG_AT = {'f': (0.58, -0.25, 0.45), 'h': (0.55, -0.20, -0.55)}   # hips sit inside the trunk (was -0.35 / -0.30)
WING_ARM_X = 0.52        # was 0.62
BOSSMODELS_RIG = {       # for the BossModels.java patch (blocks)
    'thigh_f': round(0.58 * 0.68, 4), 'thigh_h': round(0.55 * 0.68, 4), 'wing_arm': round(WING_ARM_X * 0.68, 4)}


def fy(y):
    return Y_PIVOT + (y - Y_PIVOT) * Y_SQUEEZE if y <= Y_KNEE else y - (Y_KNEE - Y_PIVOT) * (1 - Y_SQUEEZE)


def squeeze_trunk(pack):
    d = os.path.join(pack, 'assets', 'hobbang', 'models', 'item', 'boss', 'tempest_wyvern')
    for name, sx in TRUNK.items():
        path = os.path.join(d, name + '.json')
        m = json.load(open(path, encoding='utf-8'))
        if m.get('credit', '').endswith(MARK):
            print(f'[slim] {name}: already squeezed')
            continue
        for e in m['elements']:
            for key in ('from', 'to'):
                e[key][0] = round(8 + (e[key][0] - 8) * sx, 4)
                e[key][1] = round(fy(e[key][1]), 4)
            if 'rotation' in e:
                o = e['rotation']['origin']
                o[0] = round(8 + (o[0] - 8) * sx, 4)
                o[1] = round(fy(o[1]), 4)
            for k in (0, 1):                      # keep from <= to after the squeeze
                if e['from'][k] > e['to'][k]:
                    e['from'][k], e['to'][k] = e['to'][k], e['from'][k]
        m['credit'] = m.get('credit', '') + MARK
        with open(path, 'w', encoding='utf-8', newline='\n') as fh:
            json.dump(m, fh, indent=1, ensure_ascii=False)
            fh.write('\n')
        print(f'[slim] {name}: x*{sx}, y*{Y_SQUEEZE}')


# ---------------------------------------------------------------- legs
def leg(tag, parent, at, lt, ls, rests):
    P, B, BD, BONE, BELLY = W.PARTS, W.BODY, W.BODY_D, W.BONE, W.BELLY
    for sx, q in ((1, 'l'), (-1, 'r')):
        t = W.part(f'thigh_{tag}{q}', parent, (sx * at[0], at[1], at[2]), (rests[0], 0, 0))
        W.box(t, (0, 0.0, 0.0), (0.34, 0.34, 0.42), BD)                                   # hip ball
        W.box(t, (0, -0.30, 0.02), (0.36, 0.62, 0.44), B)                                 # thigh muscle
        W.box(t, (0, -0.74, 0.0), (0.25, 0.5, 0.32), B)                                   # tapering lower thigh
        W.box(t, (sx * 0.19, -0.3, 0.0), (0.04, 0.4, 0.3), BD)                            # outer scale plate
        W.box(t, (0, -lt, 0.12), (0.27, 0.24, 0.3), BD)                                   # knee cap
        W.box(t, (0, -lt + 0.02, 0.36), (0.08, 0.1, 0.3), BONE, ('x', -22.5), 'solid')    # knee spike
        s = W.part(f'shin_{tag}{q}', t, (0, -lt, 0), (rests[1], 0, 0))
        W.box(s, (0, -0.2, -0.03), (0.27, 0.4, 0.3), B)                                   # calf
        W.box(s, (0, -0.58, -0.02), (0.17, 0.68, 0.2), BD)                                # thin shaft
        W.box(s, (0, -0.5, 0.13), (0.06, 0.6, 0.09), BONE, None, 'solid')                 # front crest
        W.box(s, (0, -ls, -0.02), (0.24, 0.2, 0.26), B)                                   # ankle (hock) joint
        W.box(s, (0, -ls + 0.04, -0.25), (0.07, 0.08, 0.32), BONE, ('x', 22.5), 'solid')  # hock spur
        f = W.part(f'foot_{tag}{q}', s, (0, -ls, 0), (rests[2], 0, 0))
        W.box(f, (0, -0.08, 0.22), (0.2, 0.14, 0.55), B)                                  # heel / metatarsus
        W.box(f, (0, -0.1, 0.7), (0.1, 0.1, 0.4), BD)                                     # centre toe
        W.box(f, (0, -0.12, 1.0), (0.06, 0.08, 0.24), BONE, ('x', 22.5), 'solid')         # centre talon
        for dx in (-1, 1):
            W.box(f, (dx * 0.17, -0.1, 0.62), (0.09, 0.09, 0.4), BD, ('y', -22.5 * dx))   # splayed toes
            W.box(f, (dx * 0.27, -0.12, 0.89), (0.06, 0.08, 0.22), BONE, ('y', -22.5 * dx), 'solid')
        W.box(f, (0, -0.1, -0.2), (0.07, 0.07, 0.26), BONE, None, 'solid')                # dew-claw / back toe


def rebuild_legs(pack):
    for n in [n for n in W.PARTS if n.startswith(('thigh_', 'shin_', 'foot_'))]:
        del W.PARTS[n]
    # moving the shoulders/hips in -> wing roots follow the slimmer trunk
    for q, sx in (('l', 1), ('r', -1)):
        o = W.PARTS[f'wing_arm_{q}']['offset']
        W.PARTS[f'wing_arm_{q}']['offset'] = (sx * WING_ARM_X, o[1], o[2])
    leg('f', 'body', LEG_AT['f'], 1.1, 0.95, (-25, 45, -20))   # thigh +0.1 so the feet stay where they were
    leg('h', 'hips', LEG_AT['h'], 1.2, 1.0, (-30, 60, -30))
    scratch = tempfile.mkdtemp(prefix='wyvern_legs_')
    try:
        os.makedirs(os.path.join(scratch, 'pack'))
        man = W.build(os.path.join(scratch, 'pack'))
        if abs(man['model_units_per_block'] - 10.0) > 1e-6:
            raise SystemExit(f'units/block changed to {man["model_units_per_block"]}: legs must not extend the model')
        names = [n for n in W.PARTS if n.startswith(('thigh_', 'shin_', 'foot_'))]
        for n in names:
            for rel in (f'assets/hobbang/models/item/boss/tempest_wyvern/{n}.json', f'assets/hobbang/items/boss/tempest_wyvern/{n}.json',
                        f'assets/hobbang/textures/item/boss/tempest_wyvern/{n}.png'):
                dst = os.path.join(pack, rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copyfile(os.path.join(scratch, 'pack', rel), dst)
        print(f'[legs] rebuilt {len(names)} leg parts')
        return man
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


def patch_manifest(pack, man):
    path = os.path.join(os.path.dirname(os.path.abspath(pack).rstrip('/\\')), 'manifests', 'boss__tempest_wyvern.json')
    cur = json.load(open(path, encoding='utf-8'))
    new = {p['name']: p for p in man['parts']}
    for p in cur['parts']:
        n = p['name']
        if n.startswith(('thigh_', 'shin_', 'foot_')) or n.startswith('wing_arm_'):
            p['local_offset'] = new[n]['local_offset']
            if n.startswith(('thigh_', 'shin_', 'foot_')):
                for k in ('cuboids', 'cuboids_obb', 'faces', 'texture_px'):
                    p[k] = new[n][k]
    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        json.dump(cur, fh, indent=2)
        fh.write('\n')
    print('[manifest] leg / wing_arm local_offset updated;', 'BossModels rig x:', BOSSMODELS_RIG)


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--pack', default=os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack'))
    a = ap.parse_args()
    squeeze_trunk(a.pack)
    patch_manifest(a.pack, rebuild_legs(a.pack))
