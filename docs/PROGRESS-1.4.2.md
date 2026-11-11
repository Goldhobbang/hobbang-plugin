# HobbangEnhance 1.4.2 progress (box, KST) — complete 09:10

## Goal
Boss spawn assembly animation (v1.4.1 → 1.4.2). Pack unchanged. User server not started. server.properties not edited.

## Done
- AssembleAnim + DisplayBoss: parts fly in (legs → body → arms/wings → head → weapon) over 90 ticks (~4.5s) using interpolation_duration only.
- Invulnerable, AI frozen until assembly ends. Replay only on BossManager.spawn(), not chunk attach.
- Themed particles/sounds: Smith lava/anvil, Colossus amethyst, Sentinel copper/bell, Wyvern lightning (visual-only strikeLightningEffect).
- Block-model fallback: parts assemble by height. Smith (no block parts) hides the wither skeleton while magma/basalt shards + hammer assemble, then applyLook reveals it.
- riderOff wait (3 ticks / fallback) so parts assemble on the ground, not in the sky.
- Version 1.4.2, README note. Pack not changed.

## Artifacts
- Jar: /workspace/mc/hobbang-enhance-paper-1.4.2.jar (508,623 B) sha1 44a0ec0415ca3f87226c48459f81a0096052a580
- Video: /workspace/mc/v142_assemble.mp4 (~57s)
- Shots: /workspace/mc/v142_assemble_{smith,colossus,sentinel,wyvern}_{mid,done}.png
- Perf: /workspace/mc/bot/perf_assemble_142.log
- Pack still sha1 8375de0fae633df343ccd2225385d774dfccacce (unchanged)

## Perf (paper-test, /mspt 5s avg/max then 10s)
- idle: avg 2.7 / max 11.1 (10s 3.0/22.9)
- smith assemble: avg 7.5 / max 61.8 (10s 6.5/127.8) — first summon after restart, one-off
- colossus: avg 2.1 / max 6.0 (10s 2.7/30.1)
- sentinel: avg 2.1 / max 10.6 (10s 2.2/18.2)
- wyvern: avg 2.0 / max 4.6 (10s 2.0/9.8)
- No "Can't keep up"

## Install
- Home PC Diamond_Hobbang connected.
- plugins\hobbang-enhance-paper-1.4.2.jar
- 1.4.1 backup: C:\Users\diamo\hobbang-enhance-paper-1.4.1.jar.old
- User server not started. server.properties not edited.

## Test server
- paper-test running 1.4.2, bossModels reverted to auto, Tester2 on DISPLAY=:1.
