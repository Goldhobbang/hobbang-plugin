"""Static checks for a 1.21.11 resource pack produced by obj2mc:
pack.mcmeta format vs the real client version.json, items/ definitions -> models,
model element bounds (-16..32), face names, uv ranges, texture refs -> PNG files
(and that they live under textures/item so the items atlas picks them up)."""
import json, os, sys, zipfile
from PIL import Image
pack = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'resourcepack', 'hobbang-pack')
client = '/home/box/.gradle/caches/fabric-loom/1.21.11/minecraft-client.jar'
err = []; warn = []
meta = json.load(open(os.path.join(pack, 'pack.mcmeta')))['pack']
if os.path.exists(client):
    ver = json.loads(zipfile.ZipFile(client).read('version.json'))
    rp = [ver['pack_version']['resource_major'], ver['pack_version']['resource_minor']]
    lo = meta['min_format'] if isinstance(meta['min_format'], list) else [meta['min_format'], 0]
    hi = meta['max_format'] if isinstance(meta['max_format'], list) else [meta['max_format'], 1 << 30]
    if not (lo <= rp <= hi): err.append(f'pack format {lo}..{hi} does not include client {ver["id"]} = {rp}')
    else: print(f'pack.mcmeta OK for {ver["id"]} (resource {rp[0]}.{rp[1]})')
FACES = {'down', 'up', 'north', 'south', 'west', 'east'}
def res(idf, kind, ext):
    ns, p = idf.split(':', 1) if ':' in idf else ('minecraft', idf)
    return os.path.join(pack, 'assets', ns, kind, p + ext)
n_items = n_el = 0
CLIENT_NAMES = set(zipfile.ZipFile(client).namelist()) if os.path.exists(client) else set()
CONTEXTS = {'none', 'thirdperson_lefthand', 'thirdperson_righthand', 'firstperson_lefthand', 'firstperson_righthand', 'head', 'gui', 'ground', 'fixed', 'on_shelf'}
def model_ref(mref, rel):
    mid = mref if ':' in mref else 'minecraft:' + mref
    vanilla = mid.startswith('minecraft:') and ('assets/minecraft/models/' + mid.split(':', 1)[1] + '.json') in CLIENT_NAMES
    if not vanilla and not os.path.exists(res(mref, 'models', '.json')): err.append(f'{rel}: missing model {mref}')
def walk(m, rel):
    """1.21.4+ item model tree: model / select / condition / special / composite / range_dispatch"""
    t = m.get('type', '')
    if t == 'minecraft:model': model_ref(m['model'], rel)
    elif t == 'minecraft:select':
        if m.get('property') == 'minecraft:display_context':
            for c in m['cases']:
                for w_ in (c['when'] if isinstance(c['when'], list) else [c['when']]):
                    if w_ not in CONTEXTS: err.append(f'{rel}: unknown display_context {w_}')
        for c in m['cases']: walk(c['model'], rel)
        if 'fallback' in m: walk(m['fallback'], rel)
    elif t == 'minecraft:condition': walk(m['on_true'], rel); walk(m['on_false'], rel)
    elif t == 'minecraft:special': model_ref(m['base'], rel)
    elif t == 'minecraft:composite': [walk(x, rel) for x in m['models']]
    elif t == 'minecraft:range_dispatch': [walk(e['model'], rel) for e in m['entries']]; ('fallback' in m) and walk(m['fallback'], rel)
    else: err.append(f'{rel}: model.type {t}')
for root, _, files in os.walk(os.path.join(pack, 'assets')):
    for fn in files:
        path = os.path.join(root, fn)
        rel = os.path.relpath(path, pack)
        if fn.endswith('.json'):
            try: j = json.load(open(path))
            except Exception as e: err.append(f'{rel}: bad json {e}'); continue
        parts = rel.split(os.sep)
        if len(parts) > 3 and parts[2] == 'items':
            n_items += 1
            walk(j.get('model', {}), rel)
        elif len(parts) > 3 and parts[2] == 'models':
            for k, t in j.get('textures', {}).items():
                if t.startswith('#'): continue
                tp = res(t, 'textures', '.png')
                if not os.path.exists(tp): err.append(f'{rel}: missing texture {t}')
                elif not t.split(':')[-1].startswith(('item/', 'block/')): err.append(f'{rel}: texture {t} not in an atlas dir')
                else:
                    im = Image.open(tp)
                    if im.mode not in ('RGBA', 'RGB'): warn.append(f'{tp}: mode {im.mode}')
            for i, el in enumerate(j.get('elements', [])):
                n_el += 1
                for c in el['from'] + el['to']:
                    if not (-16 <= c <= 32): err.append(f'{rel} el{i}: coord {c} out of -16..32')
                if any(a > b for a, b in zip(el['from'], el['to'])): err.append(f'{rel} el{i}: from>to')
                for fn_, fd in el['faces'].items():
                    if fn_ not in FACES: err.append(f'{rel} el{i}: face {fn_}')
                    if not fd['texture'].startswith('#') or fd['texture'][1:] not in j['textures']: err.append(f'{rel} el{i}: tex ref {fd["texture"]}')
                    if any(not (0 <= u <= 16) for u in fd.get('uv', [])): err.append(f'{rel} el{i}: uv out of 0..16')
            for ctx, d in j.get('display', {}).items():
                if any(abs(s) > 4 for s in d.get('scale', [])): err.append(f'{rel}: display {ctx} scale > 4')
                if any(abs(s) > 80 for s in d.get('translation', [])): err.append(f'{rel}: display {ctx} translation > 80')
                if ctx not in CONTEXTS: err.append(f'{rel}: display context {ctx}')
        if fn.endswith('.png.mcmeta'):
            png = path[:-7]
            if not os.path.exists(png): err.append(f'{rel}: mcmeta without png')
            else:
                im = Image.open(png)
                if im.height % im.width: err.append(f'{rel}: animated strip height {im.height} not a multiple of width {im.width}')
print(f'items {n_items}, elements {n_el}, errors {len(err)}, warnings {len(warn)}')
for e in err[:40]: print('ERROR', e)
for w in warn[:20]: print('WARN', w)
sys.exit(1 if err else 0)
