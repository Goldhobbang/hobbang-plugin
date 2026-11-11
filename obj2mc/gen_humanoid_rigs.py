"""v1.5.0 rigs for the three upright bosses: rewrites the `parts` list of their configs (layout units, first match wins).
smith: torso(pelvis, root) / chest / head, arm-fore-hand x2, leg-shin-foot x2 (+ hammer on hand_r via smith_hammer.json)
colossus: torso(pelvis) / chest / head, arm-fore-fist x2, leg-shin x2
sentinel: body / pendulum, ring, clock, arm-blade x2
NOTE: joint_cap values written here were later scaled x0.6 in the configs (cube caps instead of spheres)."""
import json, os
HERE = os.path.dirname(os.path.abspath(__file__))
def load(n): return json.load(open(os.path.join(HERE, 'configs', n + '.json')))
for _n, _new in (('abyssal_smith', 'chest'), ('prism_colossus', 'chest'), ('chrono_sentinel', 'pendulum')):
    if any(p['name'] == _new for p in load(_n)['parts']):
        raise SystemExit(_n + ': rig already applied (this script converts the 1.4.4 6-part layout once)')
def save(n, c): json.dump(c, open(os.path.join(HERE, 'configs', n + '.json'), 'w'), indent=1)
def side(s, x_edge):   # x bound for the right (-X, s=-1) or left (+X, s=+1) side
    return ({'max_x': -x_edge} if s < 0 else {'min_x': x_edge})
def arm_reg0(): pass
def reg(s, x_edge, ylo, yhi):
    mn = [x_edge if s > 0 else None, ylo, None]; mx = [-x_edge if s < 0 else None, yhi, None]
    return {'min': mn, 'max': mx}

# ---------------- abyssal smith (layout = blocks, 9 tall)
c = load('abyssal_smith'); old = {p['name']: p for p in c['parts']}
back = [{'min': [None, 3.0, None], 'max': [None, 7.5, -0.05]}]
leg_obb = {'fill': 0.88, 'min_dim': 3}
parts = [dict(old['head'], parent='chest', joint_cap=0.3)]
for s, q in ((-1, 'r'), (1, 'l')):
    parts += [
        {'name': 'hand_' + q, 'parent': 'fore_' + q, 'pivot': [None, 4.45, None], 'joint_cap': 0.22, 'regions': [reg(s, 1.55, 2.9, 4.45)]},
        {'name': 'fore_' + q, 'parent': 'arm_' + q, 'pivot': [None, 5.3, None], 'joint_cap': 0.28, 'mode': 'obb', 'regions': [reg(s, 1.55, 4.45, 5.3)]},
        {'name': 'arm_' + q, 'parent': 'chest', 'pivot': [s * 2.0, 6.8, 0], 'joint_cap': 0.4, 'mode': 'obb', 'regions': [reg(s, 1.55, 5.3, 7.6)]},
    ]
for s, q in ((-1, 'r'), (1, 'l')):
    parts += [
        {'name': 'foot_' + q, 'parent': 'shin_' + q, 'pivot': [None, 0.65, None], 'joint_cap': 0.22, 'mode': 'obb', 'obb': leg_obb, 'regions': [reg(s, 0.0, None, 0.65)]},
        {'name': 'shin_' + q, 'parent': 'leg_' + q, 'pivot': [None, 1.55, None], 'joint_cap': 0.28, 'mode': 'obb', 'obb': leg_obb, 'regions': [reg(s, 0.0, None, 1.55)]},
        {'name': 'leg_' + q, 'parent': None, 'cap_with': 'torso', 'pivot': [s * 0.9, 2.6, 0], 'joint_cap': 0.4, 'mode': 'obb', 'obb': leg_obb, 'regions': [reg(s, 0.0, None, 2.6)]},
    ]
parts += [
    {'name': 'chest', 'parent': 'torso', 'pivot': [0, 4.5, 0], 'joint_cap': 0.5, 'mode': 'mixed', 'obb_regions': back, 'regions': [{'min': [None, 4.5, None]}]},
    {'name': 'torso', 'parent': None, 'pivot': [0, 2.7, 0], 'mode': 'mixed', 'obb_regions': back, 'regions': [{}]},
]
c['parts'] = parts; save('abyssal_smith', c)

# ---------------- prism colossus (layout 4.6 tall; blocks / 1.5217)
c = load('prism_colossus'); old = {p['name']: p for p in c['parts']}
tor = old['torso']; leg = old['leg_r']
parts = [dict(old['head'], parent='chest', joint_cap=0.25)]
ar = old['arm_r']['regions'][0]; al = old['arm_l']['regions'][0]
def arm_reg(base, ylo, yhi):
    r = json.loads(json.dumps(base))
    mn = r.get('min', [None] * 3); mx = r.get('max', [None] * 3)
    if ylo is not None: mn[1] = ylo if mn[1] is None else max(mn[1], ylo)
    if yhi is not None: mx[1] = yhi if mx[1] is None else min(mx[1], yhi)
    r['min'] = mn; r['max'] = mx
    return r
for s, q, base in ((-1, 'r', ar), (1, 'l', al)):
    parts += [
        {'name': 'fist_' + q, 'parent': 'fore_' + q, 'pivot': [None, 2.17, None], 'joint_cap': 0.2, 'regions': [arm_reg(base, None, 2.17)]},
        {'name': 'fore_' + q, 'parent': 'arm_' + q, 'pivot': [None, 2.83, None], 'joint_cap': 0.25, 'regions': [arm_reg(base, 2.17, 2.83)]},
        dict(old['arm_' + q], parent='chest', joint_cap=0.32),
    ]
for q in ('r', 'l'):
    o = old['leg_' + q]
    parts += [
        {'name': 'shin_' + q, 'parent': 'leg_' + q, 'pivot': [None, 0.92, None], 'joint_cap': 0.25, 'mode': 'mixed', 'obb': o.get('obb', {}),
         'obb_regions': o['obb_regions'], 'regions': [arm_reg(o['regions'][0], None, 0.92)]},
        dict(o, cap_with='torso', joint_cap=0.32),
    ]
parts += [
    {'name': 'chest', 'parent': 'torso', 'pivot': [0, 2.69, 0], 'joint_cap': 0.4, 'mode': 'mixed', 'obb': tor.get('obb', {}), 'obb_regions': tor['obb_regions'],
     'regions': [{'min': [None, 2.69, None]}]},
    tor,
]
c['parts'] = parts; save('prism_colossus', c)

# ---------------- chrono sentinel (layout 2.8 tall; blocks / 1.7857)
c = load('chrono_sentinel'); old = {p['name']: p for p in c['parts']}
parts = [old['ring'], old['clock']]
for q in ('r', 'l'):
    o = old['arm_' + q]
    parts += [
        {'name': 'blade_' + q, 'parent': 'arm_' + q, 'pivot': [None, 1.43, None], 'joint_cap': 0.14, 'mode': 'mixed', 'obb_regions': o['obb_regions'],
         'regions': [arm_reg(o['regions'][0], None, 1.43)]},
        {k: v for k, v in dict(o, joint_cap=0.18).items() if k not in ('mode', 'obb_regions')},
    ]
parts += [
    {'name': 'pendulum', 'parent': 'body', 'pivot': [0, 1.23, None], 'joint_cap': 0.15, 'regions': [{'max': [None, 1.23, None]}]},
    old['body'],
]
c['parts'] = parts; save('chrono_sentinel', c)
for n in ('abyssal_smith', 'prism_colossus', 'chrono_sentinel'):
    print(n, [p['name'] for p in load(n)['parts']])
