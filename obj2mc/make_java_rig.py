"""manifests/boss__*.json -> BossModels.java (pivot/scale/spin-axis table for the plugin)."""
import json, glob, os, sys
man_dir = os.path.join(os.path.dirname(__file__), '..', 'resourcepack', 'manifests')
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', 'hobbang-enhance-paper', 'src', 'main', 'java', 'com', 'hobbang', 'enhance', 'boss', 'BossModels.java')
f = lambda v: '%.4fF' % v
lines = []
for path in sorted(glob.glob(os.path.join(man_dir, 'boss__*.json'))):
    m = json.load(open(path))
    boss = os.path.basename(path)[6:-5]
    att = m.get('attach')   # separate mesh attached to another boss's rig (e.g. the smith's hammer)
    for p in m['parts']:
        px, py, pz = att['pivot_blocks'] if att else p['pivot_blocks']
        ax = p.get('spin_axis') or [0, 1, 0]
        lines.append('\t\tput("%s", "%s", %s, %s, %s, %s, %s, %s, %s, %d, "%s");' % (
            att['boss'] if att else boss, att['part'] if att else p['name'], f(px), f(py), f(pz), f(m['item_display_scale']),
            f(ax[0]), f(ax[1]), f(ax[2]), p['cuboids'], 'boss/%s/%s' % (boss, p['name'])))
    for p in m['parts']:
        if 'local_offset' in p and not att:
            o, r = p['local_offset'], p['rest_rot']
            lines.append('\t\trig("%s", "%s", %s, %s, %s, %s, %s, %s);' % (boss, p['name'], f(o[0]), f(o[1]), f(o[2]), f(r[0]), f(r[1]), f(r[2])))
    if not att:
        lines.append('\t\tHEIGHT.put("%s", %s);' % (boss, f(m.get('height_blocks', 0))))
src = '''package com.hobbang.enhance.boss;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** 자동 생성 (obj2mc/make_java_rig.py) — 리소스팩 hobbang-pack 의 보스 모델 피벗/스케일 표. 손으로 고치지 말 것 */
public final class BossModels {
	public record Spec(String boss, String part, Vector3f pivot, float scale, Vector3f spinAxis, int cuboids, String key) {
	}

	private static final Map<String, Spec> SPECS = new HashMap<>();
	private static final Map<String, Float> HEIGHT = new HashMap<>();
	private static final Map<String, Vector3f[]> RIG = new HashMap<>();

	private BossModels() {}

	private static void put(String b, String p, float x, float y, float z, float s, float ax, float ay, float az, int n, String key) {
		SPECS.put(b + "/" + p, new Spec(b, p, new Vector3f(x, y, z), s, new Vector3f(ax, ay, az).normalize(), n, key));
	}

	public static Spec get(String boss, String part) {
		Spec s = SPECS.get(boss + "/" + part);
		if (s == null) throw new IllegalArgumentException("no boss model " + boss + "/" + part);
		return s;
	}

	private static void rig(String b, String p, float ox, float oy, float oz, float rx, float ry, float rz) {
		RIG.put(b + "/" + p, new Vector3f[]{new Vector3f(ox, oy, oz), new Vector3f(rx, ry, rz)});
	}

	/** v1.5.0 박스 리그 관절: {부모 피벗 기준 오프셋(부모 좌표계), rest 회전(도, X→Y→Z 순)}. 복셀 모델 파츠는 null */
	public static Vector3f[] rig(String boss, String part) {
		return RIG.get(boss + "/" + part);
	}

	static Iterable<Spec> all() {
		return SPECS.values();
	}

	public static float height(String boss) {
		return HEIGHT.getOrDefault(boss, 0F);
	}

	static {
%s
	}
}
''' % '\n'.join(lines)
open(out, 'w', encoding='utf-8', newline='\n').write(src)
print('wrote', out, len(lines), 'lines')
