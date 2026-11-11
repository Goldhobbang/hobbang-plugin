"""docs/RIG-1.5.0.md 의 파츠·pivot 표를 매니페스트에서 다시 만든다. usage: python rig_report.py > ../docs/RIG-1.5.0.parts.md"""
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
names = {'abyssal_smith': '심연의 대장장이', 'prism_colossus': '프리즘 콜로서스', 'chrono_sentinel': '크로노 센티넬', 'tempest_wyvern': '템페스트 와이번'}
out = []
for b, kr in names.items():
    m = json.load(open(os.path.join(HERE, '..', 'resourcepack', 'manifests', f'boss__{b}.json')))
    box = 'local_offset' in m['parts'][0]
    out.append(f'### {kr} — {len(m["parts"])}파트, cuboid {m["total_cuboids"]}\n')
    if box:
        out.append('| part | parent | 부모 기준 오프셋 | rest 회전 (X,Y,Z°) | rest pivot (블록) | cuboid |\n|---|---|---|---|---|---:|')
        for p in m['parts']:
            out.append(f"| {p['name']} | {p['parent'] or '—'} | `{p['local_offset']}` | `{p['rest_rot']}` | `{[round(x, 2) for x in p['pivot_blocks']]}` | {p['cuboids']} |")
    else:
        out.append('| part | parent | pivot (블록) | 방식 | cuboid |\n|---|---|---|---|---:|')
        for p in m['parts']:
            out.append(f"| {p['name']} | {p['parent'] or '—'} | `{[round(x, 3) for x in p['pivot_blocks']]}` | {p.get('mode', 'voxel')} | {p['cuboids']} |")
    out.append('')
sys.stdout.reconfigure(encoding='utf-8')
print('\n'.join(out))
