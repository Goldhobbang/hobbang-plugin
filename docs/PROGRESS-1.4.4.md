# HobbangEnhance 1.4.4 progress (box, Asia/Seoul)

## Goal
Weapon facing fix (pack) + Tempest Wyvern 23-part rig + boss death anims in one pack-v1.4.4 / plugin 1.4.4 release. Then source handoff. Do not start the user's Paper server.

## Status (2026-11-11 11:51 Asia/Seoul) — pack published, home installed, handoff copied

### Done
- Weapon facing locked in pack source / pack-dev **and in the official zip**.
  - Hammer, sword, scythe: FP+TP Y=0 (TP `[-18,0,0]` / FP `[-8,0,16]`, trans/scale unchanged).
  - Battleaxe (stormcaller) chopping grip, size unchanged:
    - TP-R `[20, 90, 0]` trans `[0, 1.0, 0.8]` scale `0.8`
    - FP-R `[-8, -90, 16]` trans `[2.0, 1.3, -2.0]` scale `0.45`
    - LH: Y and Z negated.
  - True side profiles (mannequin facing south, spectator west yaw 270, `tp Tester2 9196.6 152.55 9200.5 270 6`):
    - `/workspace/mc/v144_held_axe_{fp,tp_front,tp_side}.png` (tp_side 396250 B, 11:16–11:17)
    - `/workspace/mc/v144_held_hammer_tp_side.png` (363846 B)
  - vs-ref plate skipped (reference PNG was never on the box).
- Official combined zip (facing + chopping axe + wyvern 0.30-overlap):
  - `/workspace/mc/resourcepack/hobbang-pack.zip` 7705622 B, sha1 `eda4401fcb408ab27a49c6c9fd29aa723aac4945` (11:46 Asia/Seoul)
- **Published** `pack-v1.4.4` on Goldhobbang/hobbang-plugin (11:50 Asia/Seoul).
  - Tag: https://github.com/Goldhobbang/hobbang-plugin/releases/tag/pack-v1.4.4
  - Asset: https://github.com/Goldhobbang/hobbang-plugin/releases/download/pack-v1.4.4/hobbang-pack.zip
  - curl -sL: HTTP 200, 7705622 B, sha1 `eda4401fcb408ab27a49c6c9fd29aa723aac4945`
- Home PaperServer `server.properties` updated (UTF-8, no BOM, `https\://` escaping kept). Backup: `server.properties.bak-pre-1.4.4`.
- Prism: `D:\PrismLauncher\instances\1.21.11\minecraft\resourcepacks\hobbang-pack.zip` 7705622 B, same sha1.
- Plugin jar installed. 1.4.3 backed up as `C:\Users\diamo\hobbang-enhance-paper-1.4.3.jar.old` (sha1 `f03f3390be7950ee174a8914621e28a1c7b11b85`). plugins has only `hobbang-enhance-paper-1.4.4.jar` (539762 B, sha1 `814d17da95d5a1149e7cc999af7f4348f24e8d87`). User Paper server was not started.
- Source handoff copied to `C:\Users\diamo\Deskktoptop\Desktop\School\Projects\노리터\hobbang-plugin` (README.md, ARCHITECTURE.md, plugin src, pack, obj2mc, weapons, docs, dist). `models-3d` was already there (9 Meshy zips) and was not touched. `/workspace/mc/models` was not copied. No commit/push. Staging zip/jar/dir under `C:\Users\diamo` deleted after expand.

- Wyvern mesh: tail overlap ~0.30 layout units, pivots unchanged. Total 3811 cuboids. pack-dev synced.
  - Fly reel: `/workspace/mc/v144_wyvern_fly.mp4` (20.0s post-fix, ~4.8 MB)
  - Stills: `v144_wyvern_fly_front.png`, `v144_wyvern_fly_side.png`, `v144_wyvern_fly_close.png`; dive still is pre-0.30 mesh but same body.
  - Body/neck connected in captured poses; 0.15 high-wave tail gap gone in post-0.30 stills.
  - mspt `/workspace/mc/v144_wyvern_mspt.txt`: idle 1.6/1.0/10.1 (5s), 1.6/1.0/10.1 (10s), 1.8/0.9/29.0 (1m); fly 5s 2.3/1.4/5.8, 2.2/1.2/6.6, 1.9/0.9/29.0; later 10s 1.8/1.2/4.7, 2.0/1.2/12.8, 2.1/1.0/26.5.
  - Landing stills **not signed off**: silverfish Y locked at 160.7606113819401, NoGravity=1b, Motion Y=0. No physics fix in this jar.
- Deaths: jar reloaded in paper-test. Lethal 10000 player_attack after invul on all four.
  - Official reel: `/workspace/mc/v144_deaths.mp4` (medium cam, ~88s / 32059788 B, 11:40).
  - Stills: `/workspace/mc/v144_death_{wyvern,smith,sentinel,colossus}.png`. Wyvern collapse and colossus shatter visible; smith kneel not clearly confirmed. Leftover displays/fire in frame.
  - Death durations (code): Smith 100, Wyvern 110, Sentinel 150, Colossus 100. Assemble invul: Smith 102, Wyvern 68, Colossus 70, Sentinel 64.

### Remaining issues
- Wyvern landing / dive descent: Y-lock on silverfish (NoGravity + setVelocity Y ignored).
- Death `finishDeath` linger and delayed broadcasts; smith kneel unconfirmed on camera; official death media includes leftover bosses/fire.
- First-Smith warmup spike (~104 ms, 42 parts / 31 blocks).
