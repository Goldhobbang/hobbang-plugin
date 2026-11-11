"""v1.5.0: after obj2mc rebuilt weapon/<id>/held (obb), refresh the hand models without running make_weapons.main()
(which also rewrites icons and wrapper item definitions). Writes weapon/<id>_hand.json = held.json + display from
weapons/*_display.json, re-applies the lava pulse / lightning flicker animations, drops obj2mc's helper item defs.
Usage: python rebuild_hands.py [pack_dir]"""
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import make_weapons as mw

pack = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..', 'resourcepack', 'hobbang-pack')
A = os.path.join(pack, 'assets')
mdl = A + '/hobbang/models/item/weapon'
for wid, disp in mw.MODEL3D.items():
    m = json.load(open(f'{mdl}/{wid}/held.json'))
    m['display'] = json.load(open(os.path.join(HERE, disp)))
    m['textures']['particle'] = f'hobbang:item/weapon/{wid}_icon'
    json.dump(m, open(f'{mdl}/{wid}_hand.json', 'w'), separators=(',', ':'))
    p = f'{A}/hobbang/items/weapon/{wid}/held.json'
    if os.path.exists(p): os.remove(p)
    try: os.rmdir(f'{A}/hobbang/items/weapon/{wid}')
    except OSError: pass
try: os.rmdir(A + '/hobbang/items/weapon')
except OSError: pass
mw.lava_pulse(A + '/hobbang/textures/item/weapon/smith_hammer/held.png')
mw.lava_pulse(A + '/hobbang/textures/item/boss/smith_hammer/hammer.png')
mw.lightning_flicker(A + '/hobbang/textures/item/weapon/stormcaller/held.png')
print('hands ok')
