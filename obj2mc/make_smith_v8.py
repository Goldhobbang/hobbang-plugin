"""Fit the v8 hip shell and align the leg armor on a common curve."""

import numpy as np

import make_smith as M
import make_smith_v6 as V6
import make_smith_v7 as V7
from make_smith_v3 import canvas


LEG_SCALE = 1.30
HAFT_SCALE = 1.30
HEAD_SCALE = 0.90


def link_tex(palette, broad_faces):
    """Paint a rectangular metal link with a cutout in its two broad faces."""
    def paint(face, w, h):
        color = canvas(w, h, palette[5])
        color[0, :] = palette[7]
        color[-1, :] = palette[2]
        color[:, 0] = palette[4]
        color[:, -1] = palette[3]
        rgba = np.full((h, w, 4), 255, np.uint8)
        rgba[:, :, :3] = color
        if face in broad_faces and min(w, h) >= 4:
            inset_x, inset_y = max(1, w // 5), max(1, h // 5)
            rgba[inset_y:h - inset_y, inset_x:w - inset_x, 3] = 0
            rgba[inset_y - 1, inset_x:w - inset_x, :3] = palette[7]
            rgba[h - inset_y, inset_x:w - inset_x, :3] = palette[3]
        return rgba
    return paint


class Smith(V7.Smith):
    def chain(self, part, center, size, rotation=None):
        if part == 'torso':
            self.waist_chain(part)
        else:
            self.chest_chain(part, center, size[1], rotation)

    def waist_chain(self, part):
        # Rounded corners connect the front/rear and side runs of the belt.
        front = [(x, 4.219 + abs(x) * 0.065, 0.80, 0.36, False, 0)
                 for x in (-0.61, -0.305, 0.0, 0.305, 0.61)]
        right = [(0.974, 4.263, 0.61, 0.50, False, 45),
                 (1.17, 4.263, 0.285, 0.385, True, 0),
                 (1.17, 4.263, -0.065, 0.385, True, 0),
                 (0.974, 4.263, -0.426, 0.50, False, -45)]
        rear = [(x, 4.262, -0.625, 0.36, False, 0)
                for x in (0.61, 0.305, 0.0, -0.305, -0.61)]
        left = [(-x, y, z, length, along_z, -yaw) for x, y, z, length, along_z, yaw in reversed(right)]
        for i, (x, y, z, length, along_z, yaw) in enumerate(front + right + rear + left):
            flat = i % 2 == 1
            height, cross = (0.088, 0.215) if flat else (0.218, 0.087)
            dims = (cross, height, length) if along_z else (length, height, cross)
            broad = ('up', 'down') if flat else ('west', 'east') if along_z else ('north', 'south')
            turn = ('y', yaw) if yaw else None
            self.raw(part, (x, y, z), dims, link_tex(self.materials.stone.palette, broad), turn)

    def chest_chain(self, part, center, length, turn):
        x, y, _ = center
        angle = np.radians(turn[1])
        for i, t in enumerate(np.linspace(-0.42, 0.42, 6) * length):
            # The lower ends follow the slope of the front breastplate.
            point = (x - np.sin(angle) * t, y + np.cos(angle) * t, 1.055 - 0.39 * t)
            edge_on = i % 2 == 1
            width, depth = (0.086, 0.190) if edge_on else (0.19, 0.086)
            broad = ('west', 'east') if edge_on else ('north', 'south')
            self.raw(part, point, (width, 0.252, depth), link_tex(self.materials.stone.palette, broad), turn)


def pelvis_armor(smith):
    part = 'torso'
    # The recessed energy bridge overlaps both hips and sits inside the stone.
    smith.core(part, (0, 2.91, 0.01), (2.04, 0.61, 0.97))
    # The apron meets the front of the hip armor just beneath the chain belt.
    smith.plate(part, (0, 3.31, 0.685), (0.97, 1.44, 0.33))
    smith.plate(part, (0, 3.09, -0.54), (0.48, 0.64, 0.23), ('x', -22.5))
    for side in (-1, 1):
        # Vertical faces of the hip shell taper inward at the joint.
        smith.plate(part, (side * 0.85, 3.035, 0.555), (0.55, 0.72, 0.18), ('z', -side * 22.5))
        smith.plate(part, (side * 1.19, 3.185, 0.318), (0.37, 0.95, 0.17), ('y', side * 45))
        smith.plate(part, (side * 1.277, 3.415, -0.006), (0.296, 1.294, 0.55))
        smith.plate(part, (side * 1.178, 3.17, -0.329), (0.37, 0.94, 0.17), ('y', -side * 45))
        smith.plate(part, (side * 0.76, 3.05, -0.51), (0.62, 0.75, 0.19), ('z', -side * 22.5))


def leg_axis(y):
    """Lateral distance from the body center at height y, in blocks."""
    from_knee = y - 1.55
    return 1.151 - 0.166 * from_knee + 0.0185 * from_knee**2


def thigh_armor(smith):
    for side, part in ((1, 'leg_l'), (-1, 'leg_r')):
        smith.core(part, (side * leg_axis(2.694), 2.694, 0.043), (0.78, 0.468, 0.69))
        # The outer and inner edges follow the same curve. Narrow, staggered
        # joints between the two courses reveal the core without a broad rim.
        for low, high, depth in ((1.78, 2.325, -0.014), (2.338, 2.91, 0.012)):
            y = (low + high) / 2
            h = high - low
            x = leg_axis(y)
            smith.plate(part, (side * (x - 0.022), y, 0.506 + depth), (0.60, h, 0.15))
            smith.plate(part, (side * (x + 0.377), y + 0.009, 0.407 + depth),
                        (0.297, h + 0.012, 0.146), ('y', side * 45))
            smith.plate(part, (side * (x + 0.505), y + 0.021, 0.048 + depth), (0.154, h + 0.01, 0.49))
            smith.plate(part, (side * (x + 0.366), y + 0.008, -0.364 - depth),
                        (0.281, h + 0.016, 0.148), ('y', -side * 45))
            smith.plate(part, (side * (x - 0.009), y - 0.007, -0.482 - depth), (0.612, h, 0.147))


def align_shin_armor(smith):
    for side, part in ((1, 'shin_l'), (-1, 'shin_r')):
        core, *plates = smith.T[part]['boxes']
        smith.T[part]['boxes'] = []

        def center(box):
            x, y, z = box['c']
            # The v7 calf plates are offset from the 1.20-block shin axis.
            return x + side * (leg_axis(y) - 1.20), y, z

        smith.core(part, center(core), core['s'])
        for box in plates:
            smith.plate(part, center(box), box['s'], box['rot'], trim=bool(box.get('light')))


def spine(smith):
    # One energy column and two stone vertebrae bridge the torso/chest joint.
    smith.core('torso', (0, 4.59, 0.015), (0.56, 0.76, 0.59))
    smith.plate('torso', (0, 4.48, -0.035), (0.66, 0.23, 0.69), ('x', 22.5))
    smith.plate('chest', (0, 4.73, -0.045), (0.71, 0.29, 0.67), ('x', -22.5))


def stretch_legs(smith):
    for part in ('leg_l', 'leg_r', 'shin_l', 'shin_r'):
        pivot_y = smith.T[part]['pivot'][1]
        boxes = smith.T[part]['boxes']
        smith.T[part]['boxes'] = []
        for box in boxes:
            x, y, z = box['c']
            w, h, d = box['s']
            center = (x, pivot_y + (y - pivot_y) * LEG_SCALE, z)
            # Keep the diamond knee cap square when lengthening the greave.
            size = (w, h if box['rot'] == ('z', 45) else h * LEG_SCALE, d)
            if box.get('light') == 15:
                smith.core(part, center, size)
            else:
                smith.plate(part, center, size, box['rot'], trim=bool(box.get('light')))


def define_smith():
    smith = V7.build_smith(Smith)
    pelvis_armor(smith)
    thigh_armor(smith)
    align_shin_armor(smith)
    spine(smith)
    stretch_legs(smith)
    return smith.T


def define_hammer():
    hammer = V6.define_hammer()
    grip_x, grip_y, _ = hammer['hammer']['pivot']
    head_y = grip_y + (V6.HAMMER_Y - grip_y) * HAFT_SCALE
    for index, box in enumerate(hammer['hammer']['boxes']):
        x, y, z = box['c']
        w, h, d = box['s']
        if index < 27 or index in (28, 29):  # forged head and the sockets that sit in it
            box['c'] = (grip_x + (x - grip_x) * HEAD_SCALE,
                        head_y + (y - V6.HAMMER_Y) * HEAD_SCALE, z * HEAD_SCALE)
            box['s'] = (w * HEAD_SCALE, h * HEAD_SCALE, d * HEAD_SCALE)
        else:
            box['c'] = (x, grip_y + (y - grip_y) * HAFT_SCALE, z)
            box['s'] = (w, h * HAFT_SCALE, d)
    return hammer


if __name__ == '__main__':
    body = define_smith()
    leg_length = body['leg_l']['pivot'][1] - body['foot_l']['pivot'][1]
    V7.emit_body(body, height_blocks=9.0 + leg_length * (LEG_SCALE - 1))
    M.emit_model_set(str(V7.ROOT / 'resourcepack' / 'hobbang-pack'), 'boss/smith_hammer', define_hammer(),
                     height_blocks=5.2, k_units_per_block=6.75,
                     attach={'boss': 'abyssal_smith', 'part': 'hammer', 'pivot_blocks': [-2.35, 3.5, 0.45]},
                     credit='make_smith_v8 (HobbangEnhance)')
