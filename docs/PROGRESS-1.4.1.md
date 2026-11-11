# HobbangEnhance 1.4.1 progress (box, KST)
## Done
- Held weapons: hammer fp [-30,0,0] s0.52 / tp [90,0,0] s0.8 ; battleaxe fp [0,-90,-30] s0.45 / tp [90,90,0] s0.8 (~1.45-1.5x, head/blade forward). 2D placeholders (greatsword/scythe) BIG_HANDHELD ~1.25-1.4x. Template: weapons/held_display_template.json
- Structures (structures/gen.py, iso.py preview): forge 61x30x61, temple 57x27x57, grotto 47x27x47 natural cave. Lava containment pass, wall/bars/lantern state post-pass.
- Storm arena plan enriched (StormArena.plan, seeded RNG).
- Template preload at startup (first place 60.7ms -> fix).
- Animated textures: interpolate removed (client CPU per-tick blend).
- Version 1.4.1 (build.gradle.kts), Site.looks + README sizes updated.
- Screenshots: /workspace/mc/v141_*.png (worldgen temple/forge/grotto, placed x3, wyvern altar/arena/restored, held weapons, fps)
- Perf before (1.4.0): bot/perf_before.log
## In progress / todo
- perf after (1.4.1): bot/perf_after.log (t19.js running)
- rebuild pack zip + sha1 (make_zip.py, validate_pack.py), sync dev pack to client
- relaunch visible client on DISPLAY=:1 (cd hobbang-enhance; JAVA_HOME jdk-25; ./gradlew runClient --args="--username Tester2 --width 1900 --height 1100 --quickPlayMultiplayer 127.0.0.1:25599")
- revert paper-test bossModels "on" -> "auto"
- DESIGN.md / obj2mc README notes for 1.4.1
- install on home PC (bb9b6a87-...) with 1.4.0 .old backup in C:\Users\diamo; user said PC may sleep -> if unreachable leave jar
- pack rebuilt 03:24: hobbang-pack.zip 7,572,063 B sha1 8375de0fae633df343ccd2225385d774dfccacce

## 체크포인트 (03:33 KST)
- perf_after.log 완료. 폭풍 투기장 build/crumble 스파이크(39.6/26.7ms) → 원인: arenas.json 동기 저장(커진 plan). 저장을 비동기 IO 스레드로 변경, onDisable은 saveSync. jar 재빌드 03:33 (496,851 B).
- 홈PC 연결됨(connected:true) → 재측정 후 설치 예정.
- 다음: 서버 재시작 후 t19.js after2 재측정.
- 03:35~03:38 서버/박스 무응답(박스 일시정지 추정, load 0.09, Can't keep up 없음) → after2 중단, after3 재실행

## 체크포인트 (03:55 KST) — 완료
- perf_after3.log (비동기 저장 반영) 완료: 투기장 build 10.8ms / crumble 8.1ms (수정 전 39.6/26.7).
- paper-test bossModels "auto" 복구, 서버 재시작, 클라이언트 DISPLAY=:1 접속 유지 (v141_client_live.png).
- 홈PC 설치 완료: plugins\hobbang-enhance-paper-1.4.1.jar (sha1 888b0108…a231), 1.4.0 → C:\Users\diamo\hobbang-enhance-paper-1.4.0.jar.old. 서버 미실행, server.properties 미변경.
- before 무기 스샷 /workspace/mc/v141_held_*_before.png 로 복사.
- 정리: 오래된 Gradle 데몬 4개 종료.
