"""Render matching views of the OBJ, previous pack, and repainted weapon pack."""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from make_weapon_models import (
    define_chrono_dagger, define_crystal_greatsword, define_stormcaller, weapon_projections,
)
from obj2mc import load_mesh
from preview import pack_tris
from render import grid, label, render

ROOT = Path(__file__).resolve().parent.parent
WEAPONS = {
    'stormcaller': (define_stormcaller, (
        ('storm_blade_web', 'Wing membrane'), ('storm_bone', 'Wing bone'),
        ('storm_dragon', 'Dragon scales'), ('storm_eye', 'Dragon eye'),
        ('storm_horn', 'Crown horn'), ('storm_haft', 'Wrapped haft'),
    )),
    'crystal_greatsword': (define_crystal_greatsword, (
        ('crys_blade_main', 'Blade facets'), ('crys_energy', 'Energy core'),
        ('crys_shard', 'Shard'), ('crys_guard', 'Dark metal'),
        ('crys_heart', 'Guard gem'), ('crys_grip', 'Wrapped grip'),
    )),
    'chrono_dagger': (define_chrono_dagger, (
        ('chrono_fuller', 'Gold fissure'), ('chrono_runes', 'Teal rune channel'),
        ('chrono_spine', 'Brass plate'), ('chrono_dial', 'Main dial'),
        ('chrono_gear', 'Gear face'), ('chrono_shaft', 'Staff'),
    )),
}
VIEWS = (('Front', 0, 6), ('3/4', -40, 13))
BG = (32, 34, 43)


def load_model(resources, weapon, hand=False):
    manifest = json.loads((resources / 'manifests' / f'weapon__{weapon}.json').read_bytes())
    pack = resources / 'hobbang-pack'
    overrides = None
    if hand:
        file = pack / 'assets' / 'hobbang' / 'models' / 'item' / 'weapon' / f'{weapon}_hand.json'
        overrides = {(manifest['id'], 'held'): file}
    return pack_tris(pack, manifest, model_paths=overrides)[:3]


def compare(weapon, previous, current, out):
    cfg_path = ROOT / 'obj2mc' / 'configs' / f'weapon_{weapon}.json'
    cfg = json.loads(cfg_path.read_bytes())
    vertices, indices, uvs, texture = load_mesh(cfg, cfg_path.parent)
    uv = uvs[indices].copy()
    uv[..., 1] = 1 - uv[..., 1]
    height = cfg['height_blocks']
    center = np.array([0, height / 2, 0])
    extent = max(height, 2 * np.abs(vertices[:, [0, 2]]).max()) * 1.16

    def tiles(mesh, title, nearest):
        views = []
        for view, yaw, pitch in VIEWS:
            image, _ = render(*mesh, yaw, pitch, 520, center, extent, nearest=nearest)
            views.append(label(image, f'{title} | {view}'))
        return views

    original = tiles((vertices[indices], uv, texture), 'Original mesh', False)
    for hand, suffix, model in ((False, '', 'Detailed model'), (True, '_hand', 'In-hand model')):
        images = original + tiles(load_model(previous, weapon, hand), f'{model} v1', True)
        images += tiles(load_model(current, weapon, hand), f'{model} v2', True)
        path = out / f'{weapon}{suffix}_compare.jpg'
        grid(images, len(VIEWS)).save(path, quality=94, subsampling=0)
        print(path)


def used_tile(atlas, uv):
    return atlas.crop(tuple(round(coord * atlas.width / 16) for coord in uv))


def texture_sheet(weapon, current, out):
    design, samples = WEAPONS[weapon]
    assets = current / 'hobbang-pack' / 'assets' / 'hobbang'
    atlas_path = assets / 'textures' / 'item' / 'weapon' / weapon
    atlas = Image.open(atlas_path / 'held.png').convert('RGBA')
    hand_atlas = Image.open(atlas_path / 'hand.png').convert('RGBA')
    model_path = assets / 'models' / 'item' / 'weapon' / weapon / 'held.json'
    elements = json.loads(model_path.read_bytes())['elements']
    samples_by_kind = {}
    for box, element in zip(design()['held']['boxes'], elements, strict=True):
        patch = used_tile(atlas, element['faces']['north']['uv'])
        score = (len(patch.getcolors()), patch.width * patch.height)
        current_sample = samples_by_kind.get(box['kind'])
        if current_sample is None or score > current_sample[0]:
            samples_by_kind[box['kind']] = score, patch

    sample_zoom, atlas_zoom, margin, cell = 8, 4, 16, 180
    tiles = [(text, samples_by_kind[kind][1]) for kind, text in samples]
    art = weapon_projections(f'weapon/{weapon}', hand=True)
    drawings = [(name, layer.image) for name, layer in (
        ('Wing membrane', art.get('storm_blade_web')),
        ('Inner gold fissure', art.get('chrono_fuller')),
        ('Teal rune channel', art.get('chrono_runes')),
    ) if layer is not None]
    row_height = max(tile.height for _, tile in tiles) * sample_zoom + 32
    atlases = [('Detailed model', atlas), ('In-hand model', hand_atlas)]
    crops = [(name, image.size, image.crop(image.getbbox())) for name, image in atlases]
    width = max(cell * len(samples), sum(crop.width * atlas_zoom + margin for _, _, crop in crops)) + margin * 2
    art_height = max((image.height * atlas_zoom + 42 for _, image in drawings), default=0)
    height = row_height + art_height + max(crop.height for _, _, crop in crops) * atlas_zoom + 85
    sheet = Image.new('RGB', (width, height), BG)
    draw = ImageDraw.Draw(sheet)
    draw.text((margin, margin), f'{weapon} | Selected face textures, 8x', fill='white')
    for i, (text, tile) in enumerate(tiles):
        x = margin + cell * i
        draw.text((x, 42), text, fill='white')
        enlarged = tile.resize((tile.width * sample_zoom, tile.height * sample_zoom), Image.Resampling.NEAREST)
        sheet.paste(enlarged, (x, 62), enlarged)
    x = margin
    for name, drawing in drawings:
        y = row_height + 48
        draw.text((x, y), f'{name} | Shared face art, 4x', fill='white')
        enlarged = drawing.resize((drawing.width * atlas_zoom, drawing.height * atlas_zoom), Image.Resampling.NEAREST)
        sheet.paste(enlarged, (x, y + 20))
        x += enlarged.width + margin
    x = margin
    for name, size, crop in crops:
        y = row_height + art_height + 48
        draw.text((x, y), f'{name} | {size[0]}x{size[1]} atlas, 4x crop', fill='white')
        enlarged = crop.resize((crop.width * atlas_zoom, crop.height * atlas_zoom), Image.Resampling.NEAREST)
        sheet.paste(enlarged, (x, y + 20), enlarged)
        x += enlarged.width + margin
    path = out / f'{weapon}_textures.png'
    sheet.save(path, optimize=True)
    print(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--previous', type=Path, default=ROOT / 'work' / 'detail-v1' / 'resourcepack')
    parser.add_argument('--out', type=Path, default=ROOT / 'docs' / 'img' / 'weapons-detail-v2')
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for weapon in WEAPONS:
        compare(weapon, args.previous, ROOT / 'resourcepack', args.out)
        texture_sheet(weapon, ROOT / 'resourcepack', args.out)


if __name__ == '__main__':
    main()
