# obj2mc: Meshy OBJ to Minecraft 1.21.11 cuboid boss models

This converts a textured high-poly OBJ (Meshy AI export: `.obj`, `.mtl` and texture `.png`) into
Minecraft Java **1.21.11** item models made only of cuboids. Each body part becomes its own item
model, so the plugin can put it on an `item_display` and animate it.

```
obj2mc.py            pipeline (voxelise, split into parts, greedy cuboids, bake textures, write pack + manifest)
render.py            headless numpy software rasteriser (no GPU/GL needed)
preview.py           reads the emitted JSON + PNG back with Minecraft's FaceInfo/UV rules and renders it next to the source mesh
partdebug.py         helper for writing configs: part colours overlaid on the source mesh, 0.25-block grid
validate_pack.py     static checks: pack format vs the real 1.21.11 client jar, items->models->textures, element bounds, uv
make_zip.py          deterministic zip + sha1 (for server.properties)
make_test_function.py  optional datapack: /function obj2mc_preview:all summons every boss in rest pose (for in-game checks)
configs/*.json       one file per boss
.venv/               python venv (numpy, pillow, scipy, trimesh, embreex)
```

## How it works

1. **Normalise.** The script scales the mesh to `height_blocks`, puts the feet at y=0 and centres it on X/Z.
   The model's front must face **+Z**. If a model faces another way, set `source_yaw_deg`.
2. **Voxelise.** It samples the triangles densely, marks every voxel that gets a sample at
   `resolution` voxels per block, and fills enclosed interiors (`binary_fill_holes`).
3. **Split into parts.** Each part has ordered `regions`, which are AABBs in blocks (`null` means unbounded),
   with an optional cylinder or sphere (`center`, `radius`, `axis`). Each part can also have `exclude` boxes.
   The first matching part wins, so put a catch-all part (`"regions":[{}]`) last.
4. **Greedy cuboids.** For each part, voxels are merged into disjoint boxes by **shape only**, ignoring colour.
   All six axis orders are tried and the one with the fewest boxes is kept. A face is dropped when the
   same part fully covers it. Faces covered by a different part are kept, because they show when parts move.
5. **Bake textures.** Every visible face gets its own rectangle in a per-part atlas, with `texels_per_voxel`
   pixels per voxel edge and 1 px of edge padding. Each texel is coloured by **ray-casting**
   (embree) from just outside the face back onto the mesh and sampling the Meshy texture at the hit UV,
   which is what you would see from that direction. Texels whose ray misses (cut faces between parts) use the
   colour of the nearest surface sample instead. Detail is therefore close to per-voxel, even though
   one big cuboid can carry many colours.
6. **Emit.** For namespace `hobbang` and id `boss/<boss>`, the script writes:
   - `assets/hobbang/items/boss/<boss>/<part>.json` → `{"model":{"type":"minecraft:model","model":"hobbang:item/boss/<boss>/<part>"}}`
   - `assets/hobbang/models/item/boss/<boss>/<part>.json` (elements, `"t"` texture, identity `display.fixed`)
   - `assets/hobbang/textures/item/boss/<boss>/<part>.png`. It goes under `textures/item/` so the
     1.21.11 **items** atlas (directory source `item/`, no mipmaps) picks it up.
   - `../manifests/boss__<boss>.json` with the pivot, translation, scale, parent, counts and spin axis for every part
7. **Fit the coordinates.** The pivot of each part is placed at model point (8,8,8). One uniform `model_units_per_block`
   is chosen so that every part stays inside Minecraft's **-16..32** element limit with 0.5 unit of margin.
   The model is pre-rotated 180° about Y, because `ItemDisplayRenderer` rotates the item by π about Y.
   So the rest pose is exactly **translation = pivot, left_rotation = identity, scale = item_display_scale**.

## Running a new boss (Tempest Wyvern, and re-runs)

```bash
cd /workspace/mc/obj2mc
# 1. unzip the Meshy export somewhere, e.g. /workspace/mc/models/tempest_wyvern/<dir>/
# 2. copy a config (configs/prism_colossus.json is the simplest) and set id/obj/texture/height_blocks,
#    then start with ONE catch-all part to see the model:
#    "parts":[{"name":"body","parent":null,"pivot":[0,1.5,0],"regions":[{}]}]
.venv/bin/python obj2mc.py configs/tempest_wyvern.json --resolution 12      # fast first pass
.venv/bin/python partdebug.py configs/tempest_wyvern.json                   # previews/<boss>_partdebug.png (grid in blocks)
# 3. read the grid image, add parts (wing_l/wing_r/tail/head/jaw/...), with pivots at the joints
#    ("pivot":"centroid" and "spin":true for things that rotate, as for the sentinel ring and clock)
.venv/bin/python obj2mc.py configs/tempest_wyvern.json --preview            # writes the pack + previews/<boss>_compare.png
.venv/bin/python validate_pack.py && .venv/bin/python make_zip.py           # prints sha1
```

Tuning:
- `resolution` (voxels/block) sets the detail. It was 24 for the 4.5-block golems and 40 for the thin, 2.8-block sentinel.
  The cuboid count grows by about res² (surface). Aim for 300–1,500 cuboids per part.
- `texels_per_voxel` (default 2) only sets texture sharpness. Geometry stays the same, and atlases grow by its square.
- `ray_start_vox` and `ray_depth_vox` control the colour ray-cast. `color_knn` is the fallback blur.
- `min_speck_voxels` and `per_part_speck_voxels` drop floating crumbs.
- Rough timings: about 10–25 s per boss on the box.

## Results (current pack)

Pack: `/workspace/mc/resourcepack/hobbang-pack/` (zip: `../hobbang-pack.zip`, sha1 in `hobbang-pack.zip.sha1`).
`pack.mcmeta` has `min_format = max_format = [75,0]`, which matches `pack_version.resource_major/minor`
read from the 1.21.11 client and server jars' `version.json`.

Previews are in `/workspace/mc/resourcepack/previews/`:
- `<boss>_compare.png`: top row is the source mesh, bottom row is the cuboid pack, shown front, 3/4, side and back 3/4.
- `<boss>_parts.png`: colour per part, with cuboid counts.
- `<boss>_partdebug.png`: part overlay on a block grid.
- `<boss>_cuboid_<view>.png`: single views.
- `ingame_1.21.11_client*.png`: real 1.21.11 client (Fabric dev client under Xvfb with the zip enabled and the
  test datapack). All parts load with no missing-model or missing-texture warnings, and the assembled bosses
  match the previews. One shot rotates the sentinel's ring and clock with `left_rotation`.

## v1.3.0 update (applied)

- Tempest Wyvern added: `configs/tempest_wyvern.json`, parts body / head (with neck) / wing_l / wing_r (pivot at the
  shoulder) / tail / leg_l / leg_r. Only concept image 6a5676b5….jpg was available.
- Sizes changed with `height_blocks`; `layout_height` keeps the part regions and pivots in the original layout units
  (they are scaled by height_blocks / layout_height), so a resize needs no region edits.
  Smith 9.0 (res 12), Colossus 7.0 (res 16), Sentinel 5.0 (res 24), Wyvern 6.0 (res 15).
- `make_java_rig.py` writes `hobbang-enhance-paper/.../boss/BossModels.java` (pivot, item_display_scale, spin axis,
  cuboid count per part) from the manifests. Re-run it after any pack rebuild.
- `assets/hobbang/items/empty.json` (vanilla `item/air` model) hides the Smith's held hammer in model mode.
- `validate_pack.py` now accepts vanilla model references found in the client jar.
- In-game shots (paper-test + 1.21.11 client + pack): `previews/ingame_v130/`.

## Integration plan (HobbangEnhance Paper plugin) — original plan, now implemented in plugin 1.3.0

**Item for each part.** Use any vanilla item, for example `Material.PAPER`, plus the model component:
```java
ItemStack s = new ItemStack(Material.PAPER);
s.setData(DataComponentTypes.ITEM_MODEL, Key.key("hobbang", "boss/abyssal_smith/torso"));
// or: ItemMeta m = s.getItemMeta(); m.setItemModel(new NamespacedKey("hobbang","boss/abyssal_smith/torso")); s.setItemMeta(m);
```
Without the pack, a client sees the base item (paper), so require the pack (see below).

**Display entities.** `DisplayBoss.item(stack, 0,0,0, scale, bright)` already spawns a FIXED `item_display` passenger.
Use `offset = (0,0,0)` and `size = item_display_scale` from the manifest. Every part's model origin is its pivot.
To pose a part, build the rig chain and call `place(part, rig)`:
```java
Rigid root  = Rigid.yaw(bodyYaw);                                  // boss frame: +Z forward, origin = feet
Rigid torso = root.at(0f, 1.3f, -0.5f).rotX(breath);               // pivot from manifest
Rigid armR  = torso.at(-0.9f-0f, 3.45f-1.3f, -0.1f+0.5f).rotX(swing);   // child pivot minus parent pivot
Rigid hammer= armR.at(-1.05f+0.9f, 1.7f-3.45f, 0.3f+0.1f);
place(torsoPart, torso); place(armRPart, armR); place(hammerPart, hammer);
```
So a child uses `parent.at(childPivot - parentPivot)`. Rotations apply around the child's pivot, because the pivot is
the item_display origin. `place()` already subtracts `riderOff` and skips unchanged poses (v1.5.1).

| boss | scale | parts (pivot in blocks, from the feet; +Z = front, -X = the boss's right) |
|---|---|---|
| abyssal_smith (4.5 tall) | 1.7297 | torso (0,1.3,-0.5) root · head (0,3.35,-0.8) · arm_r (-0.9,3.45,-0.1) · arm_l (1.0,3.45,-0.1) · hammer (-1.05,1.7,0.3) child of arm_r · leg_r (-0.5,1.25,-0.3) · leg_l (0.5,1.25,-0.3) |
| prism_colossus (4.6 tall) | 2.0645 | torso (0,1.55,0) root · head (0,3.8,0) · arm_r (-1.3,3.5,0) · arm_l (1.3,3.5,0) · leg_r (-0.55,1.55,0) · leg_l (0.55,1.55,0) |
| chrono_sentinel (2.8 tall) | 1.2800 | body (0,1.4,0) root · ring (0,2.379,0.063) spin · clock (0,1.753,0.320) spin about Z · arm_r (-0.55,2.05,0) · arm_l (0.55,2.05,0) |

Notes for the plugin port:
- **Abyssal Smith.** `SmithBoss` currently `extends Boss`: a visible Wither Skeleton with a netherite helmet and the hammer item.
  To use the model, port it to `DisplayBoss` the way `ColossusBoss`, `SentinelBoss` and `WyvernBoss` are done:
  an invisible hitbox, no helmet or hammer render, and parts built in `buildParts()` and posed in `poseParts()`.
  The hitbox is 1.5× Wither Skeleton (about 3.6 tall) while the model is 4.5 tall. Bump the scale to about 1.85, or accept the overhang.
- **Prism Colossus.** It is 4.6 tall, which fits the iron golem at ×1.714 (about 4.63) almost exactly.
- **Chrono Sentinel.** Add the existing lift (2 blocks) to every translation. The clock spins cleanly about
  its `spin_axis` (about +Z). The "ring" is two interlocked gear rings plus a top bracket, not one flat ring.
  Spinning it about the manifest `spin_axis` changes its silhouette, so spin it about **Y** through its pivot
  for a clean orbit, or split it into two ring parts in the config.
- **Glow and brightness.** `Display.Brightness(15,15)` makes soul-fire and crystal areas pop. `setGlowing` outlines the whole
  part silhouette.
- **Interpolation.** The existing `ANIM_INTERVAL` interpolation works unchanged.
- **Cost.** About 4k cuboids per boss and 6–7 entities. Item models are baked once on the client, so the
  render cost per boss is about one or two chunk sections. The network cost is only the transformation updates.

**Serving the pack** (`server.properties` of the Paper server):
```properties
resource-pack=https://github.com/Goldhobbang/<repo>/releases/download/<tag>/hobbang-pack.zip
resource-pack-sha1=<sha1 printed by make_zip.py>
require-resource-pack=true
resource-pack-prompt={"text":"홉방 보스 모델 리소스팩","color":"gold"}
```
- Hosting suggestion: attach the zip to a **GitHub Release** on one of Goldhobbang's repos (a new
  `hobbang-resourcepack` repo, or the plugin repo). The `/releases/download/...` URL is a direct download. GitHub Pages
  (for example `https://goldhobbang.dev/packs/hobbang-pack.zip` from the portfolio repo) also works, but it puts about 5 MB of
  binaries into the portfolio. Do not use links that need a login or open an HTML page (Google Drive or Dropbox preview).
- After every rebuild, update `resource-pack-sha1`, because the client caches by hash. Use a new tag or file name per version.
- The pack is resources only, so vanilla clients still need nothing installed. They accept the prompt on join.

## Limitations
- The result is a voxel (staircase) approximation. Thin diagonal parts such as the sentinel rings, chains and blade edges come out
  about 1–2 voxels thicker than in the source. Raise `resolution` for thin models.
- Parts are split by hand-written boxes, not by skinning weights. A cut can go through a shoulder pad or crystal,
  and when the parts move apart, the cut faces show the nearest surface colour.
- Lighting is Minecraft's flat per-face shading. There is no normal smoothing, unlike the source render.
- Element rotation isn't used. All cuboids are axis-aligned in model space, and rotation comes only from the display entity.

## v1.4.0 update (applied)
- New config options: `source_rot` ([[axis, deg], ...] applied to the mesh before normalisation) and `attach`
  ({boss, part, pivot_blocks}) so a separate mesh can ride on a boss part (the Smith's hammer on `arm_r`; `make_java_rig.py` emits it as a child part).
- Smith: `configs/abyssal_smith.json` (new body mesh, 2,744 cuboids, height 9) + `configs/smith_hammer.json` (259 cuboids, attached to arm_r).
- Held weapons: `configs/weapon_smith_hammer.json` (58 cuboids) and `configs/weapon_stormcaller.json` (battleaxe, res 24, 203 cuboids) -> `models/item/weapon/<id>/held.json`.
  `/workspace/mc/weapons/make_weapons.py` turns them into `<id>_hand.json` (+ display transforms from `<id>_display.json`), writes the 2D icons
  (`icons.py`; the battleaxe icon is rendered from the mesh by `mesh_icon.py`) and wraps the vanilla item definitions in a custom_model_data -> display_context select.
- Pipeline order: obj2mc (all boss + weapon configs) -> make_java_rig.py -> weapons/make_weapons.py -> validate_pack.py -> make_zip.py.
- To add a 3D model for crystal_greatsword or chrono_dagger (now the Chrono Scythe): copy `configs/weapon_stormcaller.json`, set id `weapon/<id>`, obj/texture/height,
  run obj2mc, add `'<id>': '<id>_display.json'` to `MODEL3D` in make_weapons.py and tune the display file in-game (F3+T).

## v1.5.0 update: OBB mode (tilted cuboids)

Per-part `mode` (`voxel` default | `obb` | `mixed` + `obb_regions`) fits a few single-axis-rotated cuboids
(`obb.py`, rotation 0/±22.5/±45) instead of axis-aligned voxel boxes. Details, parameters, before/after table and
rendered comparisons: `docs/CONVERSION-1.5.0.md`. `units_per_block` pins the model scale so Java offsets and
weapon display transforms stay valid. After converting weapons run `weapons/rebuild_hands.py` (not `make_weapons.py`).
Mesh paths in old configs are resolved by file name under `work/models/` (unzip `models-3d/*.zip` there).

## v1.5.0 update: rigs

Joint subdivision (`joint_cap`, `cap_with`, null pivot coordinates, `sector` regions, `gen_humanoid_rigs.py`), the offline pose checker
`pose.py`, and the hand-designed box wyvern (`make_wyvern.py`, `wyvern_compare.py`, `wyvern_palette.py`) are described in
`docs/RIG-1.5.0.md`. `configs/tempest_wyvern.json` (Meshy voxel wyvern) is kept for reference only; the pack now uses the box model.
Cuboid counts in `docs/CONVERSION-1.5.0.md` are pre-rig; the current ones are in `docs/RIG-1.5.0.md`.
