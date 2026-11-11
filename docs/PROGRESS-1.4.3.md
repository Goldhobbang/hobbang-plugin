# HobbangEnhance 1.4.3 progress (box, Asia/Seoul)

## Goal
Per-boss spawn anims (wyvern dive, sentinel compress-expand, colossus emerge; smith keeps 1.4.2 assembly), smith+sentinel arena floor wave with persist/restore, hammer-style grip on all four held boss weapons, 3D held models for crystal greatsword + chrono scythe (Meshy → obj2mc), first-summon warmup. Pack changes; do not upload or edit user server.properties. Install 1.4.3 jar on the home PC; do not start the user's server.

## Done

### Plugin (1.4.3)
- Version 1.4.3 in `build.gradle.kts`. Jar: `/workspace/mc/hobbang-enhance-paper-1.4.3.jar` (532048 B, SHA1 `f03f3390be7950ee174a8914621e28a1c7b11b85`).
- `AssembleAnim.Style`: ASSEMBLE / DIVE / EMERGE / COMPRESS.
  - DIVE: hang 8 + flight 28 + spread 16 = **56 ticks**. Start 22 blocks up, rotX 70°, lightning on land, then wing spread.
  - EMERGE: rise 32 + tail = **58 ticks**. Starts below ground, staggered rise, 8 amethyst shard displays, block-break particles.
  - COMPRESS: in 16 + hold 8 + out 20 = **52 ticks**. Halo → scale ~0.16 + spin → expand to rest.
  - Smith keeps ASSEMBLE (1.4.2, ~4.5s).
- `ArenaDress`: RADIUS 12, apply 14 blocks/tick, restore 16/tick. Forge = magma/gilded/blackstone radial veins. Temple = gold center, copper rings, gold cardinals, copper-bulb hour marks. Persist `floor-dress.json`. `start` on `BossManager.spawn`, `restore` on `Boss.onDeath` and plugin disable. Pavement only; no flowing lava; no tile entities.
- `BossWarmup`: +40 ticks after enable (with structure template preload), dummy item_displays for `BossModels.all()` + smith hammer + theme blocks, 2/tick, viewRange 0, removed immediately.
- Smith boss hammer rig: `rotX(wrist - 90F)` so the head sits forward, perpendicular to the arm.

### Resource pack
- Zip: `/workspace/mc/resourcepack/hobbang-pack.zip`
- Size: **7671327** bytes
- SHA1: **`a85ad5e8b04ff89c43ba9387b566107af8c930ef`**
- Not uploaded. `server.properties` not edited. Previous hosted pack SHA1 was `8375de0fae633df343ccd2225385d774dfccacce` (pack-v1.4.1).
- Grip (all four): handle in the fist, perpendicular to the forearm, head/blade forward/up (same composition as a normal hammer). Blade weapons (axe, greatsword, scythe) use Y=180 vs hammer Y=90 because their blade lies in the XY plane.
  - Hammer TP-R `[-18, 90, 0]`, trans `[0, 1.0, 0.8]`, scale 0.8; FP-R `[-8, 90, 16]`, trans `[2.0, 1.3, -2.0]`, scale 0.48.
  - Axe / greatsword / scythe: same with Y=180 (FP greatsword/scythe scale 0.48; axe FP 0.45).
- Crystal greatsword: obj2mc from `/workspace/mc/models/crystal_greatsword/` (height 2.3, res 32, pivot `[0, 0.52, 0]`, 49 cuboids). No source_rot (mesh already upright).
- Chrono scythe: obj2mc from `/workspace/mc/models/chrono_scythe/Meshy_AI_chrono_long_scythe_20261110_texture_obj/` (height 2.5, res 24, pivot `[0.54, 0.55, 0]`, 176 cuboids). No source_rot. Handle offset after AABB centering because the blade extends −X.
- gui/ground/fixed/on_shelf stay 2D icons. Item wrappers already selected `*_hand` for hands. `make_weapons.py` MODEL3D updated; full script was **not** rerun (lava_pulse/lightning_flicker would rewrite animated textures). Display + hand JSON patched only.
- obj2mc helper `items/weapon/<id>/held.json` defs were dropped so they do not shadow the wrappers.

### Media (box test, DISPLAY=:1, Tester2, creative, floor y=150 / feet y=151)
- Spawns + arena transforms: `/workspace/mc/v143_spawns.mp4` (41.6s, 5366813 B). Order: smith assembly + forge floor → kill/restore → sentinel compress + temple floor → kill/restore → colossus emerge → wyvern dive. Stills: `v143_{smith,sentinel,colossus,wyvern}_{mid,done}.png`.
- Held before/after, first and third person, all four weapons (24 files):
  - `/workspace/mc/v143_held_{hammer,axe,sword,scythe}_{fp,tp_back,tp_front}_{before,after}.png`
- Before: hammer/axe used the old along-the-arm 3D grip; sword/scythe were flat 2D sprites. After: all four are 3D with the hammer grip. Sword/scythe TP-after shots use a slight yaw so the blade reads in 3rd person (at yaw 0 the long axis points forward and foreshortens).

### Perf (box test, after warmup, first Smith post-restart)
- Idle 5s: 1.6 / 1.2 / 4.8 (avg / min / max mspt).
- First Smith 5s: 5.1 / 1.1 / **104.2**; 10s max **104.2**. Warmup logged `보스 모델 워밍업 완료 (26 파츠, 31 블록)` but did **not** fully remove the one-time first-summon spike (1.4.2 was 5s max 61.8 and 10s max 127.8). Later bosses: sentinel 5s max 9.5, colossus 20.3, wyvern 7.3. No "Can't keep up" during the film.

### Home PC install
- Done 2026-11-11 09:55 Asia/Seoul on Diamond_Hobbang (`bb9b6a87-5988-4c26-a0c0-2e0f1c5af2f1`).
- Backed up `C:\Users\diamo\Deskktoptop\Desktop\홉방마크\PaperServer\plugins\hobbang-enhance-paper-1.4.2.jar` to `C:\Users\diamo\hobbang-enhance-paper-1.4.2.jar.old` (508623 B).
- Installed `hobbang-enhance-paper-1.4.3.jar` in plugins (532048 B, SHA1 `f03f3390be7950ee174a8914621e28a1c7b11b85`). It is the only jar in that folder.
- User Paper server was not running and was not started. Pack was not uploaded. `server.properties` was not edited.

## Not done / leftover
- First Smith summon still spikes to ~104 ms once after a restart, even with warmup. A heavier off-screen dummy spawn was considered and not implemented.
- Pack is built but not hosted; the user's home client will keep the old pack until they (or the parent) publish the new zip and set `resource-pack-sha1`.
