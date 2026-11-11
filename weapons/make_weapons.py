"""Boss-weapon item models for hobbang-pack (run AFTER obj2mc configs/weapon_smith_hammer.json, weapon_stormcaller.json + smith_hammer.json).
- 2D 16x16 icons (icons.py) for gui/ground/fixed/on_shelf
- in-hand model per weapon: smith_hammer, stormcaller (battleaxe) = 3D obj2mc cuboid model + display transforms; others = 2D handheld
  placeholder (drop a 3D model into models/item/weapon/<id>_hand.json later, see README)
- vanilla item definitions (mace, diamond_sword, trident, golden_sword) are wrapped in a
  custom_model_data select so only HobbangEnhance items (cmd string "hobbang:<id>") change.
- lava-pulse animation (.png.mcmeta) on the hammer textures."""
import json, os, zipfile, colorsys
import numpy as np
from PIL import Image
import icons

PACK = '/workspace/mc/resourcepack/hobbang-pack'
A = PACK + '/assets'
CLIENT = '/home/box/.gradle/caches/fabric-loom/1.21.11/minecraft-client.jar'
# id -> vanilla item_model the plugin puts on it (HItem.model); v1.4.0: stormcaller = battleaxe (netherite_axe), chrono_dagger = scythe (netherite_hoe)
WEAPONS = {'smith_hammer': 'mace', 'crystal_greatsword': 'diamond_sword', 'stormcaller': 'netherite_axe', 'chrono_dagger': 'netherite_hoe'}
OLD_WRAPPED = ['trident', 'golden_sword']   # wrapped by v1.4.0 drafts; no longer used -> removed so vanilla is untouched
FLAT = ['gui', 'ground', 'fixed', 'on_shelf']

HERE = os.path.dirname(os.path.abspath(__file__))
# weapons with a 3D in-hand model built by obj2mc (configs/weapon_<id>.json -> models/item/weapon/<id>/held.json)
MODEL3D = {'smith_hammer': 'hammer_display.json', 'stormcaller': 'stormcaller_display.json', 'crystal_greatsword': 'crystal_greatsword_display.json', 'chrono_dagger': 'chrono_dagger_display.json'}

# v1.4.1: 2D in-hand placeholders are drawn ~1.45x vanilla handheld so they match the bigger 3D weapons
BIG_HANDHELD = {
    'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 5.5, 1.5], 'scale': [1.2, 1.2, 1.2]},
    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 5.5, 1.5], 'scale': [1.2, 1.2, 1.2]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [3, 3, -2], 'scale': [0.85, 0.85, 0.85]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [3, 3, -2], 'scale': [0.85, 0.85, 0.85]}}

def wj(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(obj, open(path, 'w'), indent=1)

def lava_pulse(png, frames=8, frametime=3):
    """stack N frames; hot (orange/yellow) texels pulse brighter/yellower, dark texels untouched.
    v1.4.1: no 'interpolate' -- an interpolated animation is re-blended on the client CPU and re-uploaded EVERY tick for the
    whole frame (512x512 for the boss hammer), even when nothing is on screen; stepped frames only upload on a frame change."""
    im = np.array(Image.open(png).convert('RGBA')).astype(np.float32)
    if os.path.exists(png + '.mcmeta'):
        im = im[:im.shape[1]]           # already animated: take frame 0
    r, g, b = im[..., 0], im[..., 1], im[..., 2]
    hot = np.clip(((r - 120) / 100) * ((r - b) / 120) * (g > 40), 0, 1)[..., None]   # 0..1 lava mask
    out = []
    for k in range(frames):
        t = 0.5 - 0.5 * np.cos(2 * np.pi * k / frames)
        f = im.copy()
        boost = np.stack([1 + 0.18 * t, 1 + 0.55 * t, 1 + 0.6 * t], -1)
        dim = np.stack([0.85 + 0.15 * t] * 3, -1)
        f[..., :3] = np.clip(f[..., :3] * (hot * boost + (1 - hot) * 1.0) * (hot * dim + (1 - hot)) + hot * t * np.array([10, 25, 5]), 0, 255)
        out.append(f)
    Image.fromarray(np.concatenate(out, 0).astype(np.uint8), 'RGBA').save(png, optimize=True)
    wj(png + '.mcmeta', {'animation': {'frametime': frametime}})

def lightning_flicker(png, frametime=2):
    """irregular flicker (same sequence as the icon): the gold lightning veins flash toward cyan-white"""
    im = np.array(Image.open(png).convert('RGBA')).astype(np.float32)
    if os.path.exists(png + '.mcmeta'):
        im = im[:im.shape[1]]
    r, g, b = im[..., 0], im[..., 1], im[..., 2]
    vein = np.clip((r - 140) / 80, 0, 1) * np.clip((g - 90) / 80, 0, 1) * (b < 130)
    vein = vein[..., None]
    out = []
    for k in icons.FLICK:
        f = im.copy()
        f[..., :3] = np.clip(f[..., :3] * (1 - 0.7 * k * vein) + np.array([215, 250, 255]) * 0.7 * k * vein, 0, 255)
        out.append(f)
    Image.fromarray(np.concatenate(out, 0).astype(np.uint8), 'RGBA').save(png, optimize=True)
    wj(png + '.mcmeta', {'animation': {'frametime': frametime}})

def main():
    icons.write_all()
    mdl = A + '/hobbang/models/item/weapon'
    for wid in WEAPONS:
        wj(f'{mdl}/{wid}_icon.json', {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'hobbang:item/weapon/{wid}_icon'}})
        hand = f'{mdl}/{wid}_hand.json'
        if wid in MODEL3D:
            m = json.load(open(f'{mdl}/{wid}/held.json'))
            m['display'] = json.load(open(os.path.join(HERE, MODEL3D[wid])))
            m['textures']['particle'] = f'hobbang:item/weapon/{wid}_icon'
            json.dump(m, open(hand, 'w'), separators=(',', ':'))
        elif not os.path.exists(hand) or json.load(open(hand)).get('parent') == 'minecraft:item/handheld':  # placeholder (re)written
            # 2D placeholder until a 3D model is dropped in (a custom 3D _hand.json is never overwritten)
            wj(hand, {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'hobbang:item/weapon/{wid}_icon'}, 'display': BIG_HANDHELD})
    # drop the obj2mc helper item definition for the held hammer (the model is used via the select below)
    for wid in MODEL3D:
        p = f'{A}/hobbang/items/weapon/{wid}/held.json'
        if os.path.exists(p): os.remove(p)
        try: os.rmdir(f'{A}/hobbang/items/weapon/{wid}')
        except OSError: pass
    try: os.rmdir(A + '/hobbang/items/weapon')
    except OSError: pass
    lava_pulse(A + '/hobbang/textures/item/weapon/smith_hammer/held.png')
    lava_pulse(A + '/hobbang/textures/item/boss/smith_hammer/hammer.png')
    lightning_flicker(A + '/hobbang/textures/item/weapon/stormcaller/held.png')
    z = zipfile.ZipFile(CLIENT)
    for wid, vanilla in WEAPONS.items():
        base = json.loads(z.read(f'assets/minecraft/items/{vanilla}.json'))
        sel = {'type': 'minecraft:select', 'property': 'minecraft:display_context',
               'cases': [{'when': FLAT, 'model': {'type': 'minecraft:model', 'model': f'hobbang:item/weapon/{wid}_icon'}}],
               'fallback': {'type': 'minecraft:model', 'model': f'hobbang:item/weapon/{wid}_hand'}}
        wrapped = {'model': {'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 0,
                             'cases': [{'when': f'hobbang:{wid}', 'model': sel}], 'fallback': base['model']}}
        if 'hand_animation_on_swap' in base: wrapped['hand_animation_on_swap'] = base['hand_animation_on_swap']
        wj(f'{A}/minecraft/items/{vanilla}.json', wrapped)
    for v in OLD_WRAPPED:
        p = f'{A}/minecraft/items/{v}.json'
        if v not in WEAPONS.values() and os.path.exists(p): os.remove(p)
    print('weapons ok')

if __name__ == '__main__':
    main()
