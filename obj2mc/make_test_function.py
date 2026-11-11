"""Write a datapack with /function obj2mc_preview:<boss> that summons the assembled parts
as item_display entities (rest pose) in front of the executor. For in-game checks only."""
import json, os, sys
out = sys.argv[1]
mans = sys.argv[2:]
os.makedirs(os.path.join(out, 'data/obj2mc_preview/function'), exist_ok=True)
json.dump({'pack': {'description': 'obj2mc preview', 'min_format': [94, 1], 'max_format': [94, 1]}}, open(os.path.join(out, 'pack.mcmeta'), 'w'))
allf = ['kill @e[type=item_display,tag=obj2mc]']
for i, mp in enumerate(mans):
    man = json.load(open(mp)); name = man['id'].split('/')[-1]
    ox = (i - (len(mans) - 1) / 2) * 6.0
    lines = [f'# {man["id"]} rest pose, base at ~{ox} ~ ~-9']
    for p in man['parts']:
        x, y, z = p['translation']; s = p['scale']
        lines.append(f'summon item_display ~{ox + x:.4f} ~{y:.4f} ~{-9 + z:.4f} {{Tags:["obj2mc","o2m_{name}_{p["name"]}"],brightness:{{sky:15,block:15}},'
                     f'item_display:"fixed",item:{{id:"minecraft:stone",components:{{"minecraft:item_model":"{p["item_model"]}"}}}},'
                     f'transformation:{{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],scale:[{s:.4f}f,{s:.4f}f,{s:.4f}f],right_rotation:[0f,0f,0f,1f]}}}}')
    open(os.path.join(out, f'data/obj2mc_preview/function/{name}.mcfunction'), 'w').write('\n'.join(lines) + '\n')
    allf.append(f'function obj2mc_preview:{name}')
open(os.path.join(out, 'data/obj2mc_preview/function/all.mcfunction'), 'w').write('\n'.join(allf) + '\n')
print('ok', out)
