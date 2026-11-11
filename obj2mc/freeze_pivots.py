"""Write the pivots (and spin axes) that the current manifest holds back into a config as explicit numbers, so a
re-conversion (e.g. island cleanup) cannot move a joint: auto pivots (`null` coordinates, "centroid") depend on the voxels.
usage: python freeze_pivots.py prism_colossus chrono_sentinel"""
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
for name in sys.argv[1:]:
    cp = os.path.join(HERE, 'configs', name + '.json')
    cfg = json.load(open(cp))
    man = json.load(open(os.path.join(HERE, '..', 'resourcepack', 'manifests', 'boss__' + name + '.json')))
    k = cfg['height_blocks'] / cfg.get('layout_height', cfg['height_blocks'])
    mp = {p['name']: p for p in man['parts']}
    n = 0
    for p in cfg['parts']:
        m = mp[p['name']]
        new = [x / k for x in m["pivot_blocks"]]          # full precision: the manifest must come back bit-identical
        if p['pivot'] != new:
            p['pivot'] = new; n += 1
        if p.get('spin') and m.get('spin_axis'):
            p['spin_axis'] = m['spin_axis']
    json.dump(cfg, open(cp, 'w'), indent=1)
    print(name, 'pivots frozen:', n)
