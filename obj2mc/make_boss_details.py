"""Split the v8 smith jaw and animate only existing energy colors in the pack."""

from copy import deepcopy
from pathlib import Path
import json
import math

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'resourcepack/hobbang-pack/assets/hobbang'
MANIFESTS = ROOT / 'resourcepack/manifests'
SURFACES = {
    'abyssal_smith': ('torso chest', 'torso chest'),
    'prism_colossus': ('torso chest head arm_l arm_r fore_l fore_r fist_l fist_r',
                      'torso chest head arm_l arm_r fore_l fore_r fist_l fist_r'),
    'chrono_sentinel': ('body ring', 'body'),
    'tempest_wyvern': ('body waist head ' + ' '.join(
        f'wing_{part}_{side}' for part in ('arm', 'fore', 'f1', 'f2', 'f3') for side in ('l', 'r')), ''),
}
FRAME_COUNT = 12


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, obj, *, compact=False):
    path.write_text(json.dumps(obj, separators=(',', ':') if compact else None,
                               indent=None if compact else 2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')


def model_path(boss, part):
    return ASSETS / 'models/item/boss' / boss / f'{part}.json'


def item_model(boss, part, suffix=''):
    name = f'{part}{suffix}'
    write(ASSETS / 'items/boss' / boss / f'{name}.json',
          {'model': {'type': 'minecraft:model', 'model': f'hobbang:item/boss/{boss}/{name}'}})


def split_jaw():
    boss = 'abyssal_smith'
    manifest_path = MANIFESTS / f'boss__{boss}.json'
    manifest = load(manifest_path)
    if any(part['name'] == 'jaw' for part in manifest['parts']):
        return

    head_path = model_path(boss, 'head')
    head = load(head_path)
    jaw = deepcopy(head)
    jaw_indices = {8, 19, 20, 24, 34, 35, 39}  # v8 chin, paired rami, mandibular plates and lower teeth
    original = head['elements']
    assert len(original) == 50
    jaw['elements'] = [deepcopy(original[i]) for i in sorted(jaw_indices)]
    head['elements'] = [element for i, element in enumerate(original) if i not in jaw_indices]
    head_part = next(part for part in manifest['parts'] if part['name'] == 'head')

    # Hinge at the upper rear of the two rami. Convert from the authored item-model axes.
    rami = [original[i] for i in (19, 34)]
    assert all(element['rotation']['axis'] == 'x' for element in rami)
    ramus_center = np.mean([element['rotation']['origin'] for element in rami], axis=0)
    upper_rear = ramus_center + np.array([0, 0.55, 0.30])
    k = manifest['model_units_per_block']
    shift = 8 - upper_rear
    axis_sign = np.array([-1, 1, -1])
    pivot = (np.array(head_part['pivot_blocks']) - shift / k * axis_sign).round(6).tolist()

    for element in jaw['elements']:
        for key in ('from', 'to'):
            element[key] = (np.array(element[key]) + shift).round(4).tolist()
        if 'rotation' in element:
            rotation = element['rotation']
            rotation['origin'] = (np.array(rotation['origin']) + shift).round(4).tolist()

    def counts(part, elements):
        rotated = sum('rotation' in element for element in elements)
        return dict(part, cuboids=len(elements), cuboids_voxel=len(elements) - rotated,
                    cuboids_obb=rotated, faces=sum(len(element['faces']) for element in elements))

    jaw_part = counts(head_part, jaw['elements'])
    jaw_part.update(name='jaw', item_model='hobbang:boss/abyssal_smith/jaw', parent='head',
                    pivot_blocks=pivot, translation=pivot)
    index = manifest['parts'].index(head_part)
    manifest['parts'][index:index + 1] = [counts(head_part, head['elements']), jaw_part]
    write(head_path, head, compact=True)
    write(model_path(boss, 'jaw'), jaw, compact=True)
    item_model(boss, 'jaw')
    write(manifest_path, manifest)


def energy_mask(boss, part, image):
    r, g, b = (image[:, :, channel].astype(np.int16) for channel in range(3))
    if boss == 'prism_colossus':
        energy = ((b - g > 65) & (b - r > 25) & (b >= 95)) | ((b >= 248) & (r >= 226) & (b - g >= 8))
    elif boss == 'abyssal_smith':
        energy = (b - r >= 50) & (g - r >= 32)
    elif boss == 'chrono_sentinel':
        energy = (r >= 232) & (g >= 175) & (r - b >= 70) & (g - b >= 70)
    else:
        # Keep the ordinary ochre trim and navy scales. Pulse the saturated lightning
        # highlights and the very bright amber eye, including its pale center.
        energy = (r >= 248) & (g >= 198) & (b <= 115) & (r - b >= 135)
        if part == 'head':
            energy |= (r == 255) & (g >= 210) & ((b <= 103) | ((g == 255) & (b == 255)))
    return energy & (image[:, :, 3] > 0)


def render_texture(boss, part, path, surge):
    still = path.with_name(path.stem + '_still.png')
    source = still if path.with_suffix('.png.mcmeta').exists() else path
    with Image.open(source) as image:
        base = np.asarray(image.convert('RGBA'))
    mask = energy_mask(boss, part, base)
    if not mask.any():
        return False
    if source == path:
        Image.fromarray(base).save(still, optimize=True)
    height, width = mask.shape
    row, col = np.nonzero(mask)
    phase = (row / height * 1.7 + col / width * 0.8) * math.tau
    rgb = base[row, col, :3].astype(np.float32)

    for strong in (False, True) if surge else (False,):
        frames = []
        for frame in range(FRAME_COUNT):
            angle = math.tau * frame / FRAME_COUNT
            envelope = 0.5 - 0.5 * math.cos(angle)
            band = np.sin(phase - angle)
            light = envelope * (0.044 + 0.105 * band)
            if strong:
                light = 0.15 + 0.10 * envelope + 0.11 * band
            result = base.copy()
            # Lift the existing hue toward light while retaining shadow gradients.
            brightened = np.where(light[:, None] >= 0, rgb + (255 - rgb) * light[:, None], rgb * (1 + light[:, None]))
            result[row, col, :3] = np.clip(np.round(brightened), 0, 255).astype(np.uint8)
            frames.append(result)
        target = path.with_name(path.stem + '_surge.png') if strong else path
        Image.fromarray(np.concatenate(frames)).save(target, optimize=True)
        write(target.with_suffix('.png.mcmeta'), {'animation': {
            'width': width, 'height': height, 'frametime': 3 if strong else 6, 'interpolate': True}})
    return True


def animate_surfaces():
    for boss, (parts, surges) in SURFACES.items():
        for part in parts.split():
            model = load(model_path(boss, part))
            surge = part in surges.split()
            animated = set()
            for texture in sorted(set(model['textures'].values())):
                path = ASSETS / 'textures' / (texture.split(':', 1)[1] + '.png')
                if render_texture(boss, part, path, surge):
                    animated.add(texture)
            if not animated:
                raise ValueError(f'No energy pixels in {boss}/{part}')
            for variant in ('_still', '_surge') if surge else ('_still',):
                write(model_path(boss, part + variant), {
                    'parent': f'hobbang:item/boss/{boss}/{part}',
                    'textures': {key: value + variant for key, value in model['textures'].items() if value in animated},
                })
                item_model(boss, part, variant)


if __name__ == '__main__':
    split_jaw()
    animate_surfaces()
