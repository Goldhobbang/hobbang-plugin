# 커스텀 보스 SFX 연결 프롬프트

작업 트리: `hobbang-plugin-golems` (`feat/colossus-sentinel-remodel`), 현재 스미스 모델 커밋은 `61637ac`.
스미스 v8 모델·텍스처·포즈는 **일체 수정 금지**. 시각 FX(충격파, 시계 본체 등)는 이번 작업 범위가 아니다. 푸시 금지, 메인 워크트리 금지, Paper 서버 실행 금지.

음원과 `sounds.json`은 이미 팩에 들어가 있다. 네 일은 플러그인에서 그 이벤트를 재생하는 것이다.

## 이미 들어간 파일

- `resourcepack/hobbang-pack/assets/hobbang/sounds.json` — 이벤트 16개
- `resourcepack/hobbang-pack/assets/hobbang/sounds/boss/{smith,colossus,sentinel,wyvern}/*.ogg`
- 크레딧: `docs/SFX-CREDITS.md` (또는 팩 옆 `SFX-CREDITS.md`를 `docs/`로 옮겨도 됨)
- 팩 zip 경로 구분자는 반드시 `/` (`CreateEntryFromFile`에서 `.Replace('\\','/')`). 팩 전체를 그대로 묶으면 새 ogg·sounds.json이 포함돼야 한다.

## BU.sound 오버로드

지금 `BU.sound(Location, Sound, SoundCategory, float, float)`는 바닐라 `Sound` enum만 받는다. 커스텀 키용 오버로드를 추가하라.

```java
public static void sound(Location at, String key, SoundCategory c, float v, float p) {
    if (at == null || at.getWorld() == null) return;
    at.getWorld().playSound(at, key, c, v, p);
}
```

호출 예: `BU.sound(here, "hobbang:smith.slam", SoundCategory.HOSTILE, 2.0F, 1.0F);`

커스텀 포효·레이저는 이미 음색이 있다. 피치를 1.5 이상으로 올리지 마라 (바닐라 엔더드래곤을 1.6로 올리던 자리는 0.85~1.05). 볼륨은 기존과 비슷한 1.5~3.0.

같은 순간에 레이어로 두 이벤트를 겹쳐 재생하는 것은 괜찮다 (예: 슬램 = 바닐라 메이스/모루 + `hobbang:smith.slam`). `sounds.json`의 여러 파일은 랜덤 변형이지 레이어가 아니다.

## 이벤트 목록

| 키 | 용도 |
|---|---|
| `hobbang:smith.slam` | 해머 임팩트 (랜덤: slam/anvil/hammer/thud) |
| `hobbang:smith.scrape` | 쇠 마찰 “끼이이이ㅣㄱ” (슬램 예고, 페이즈) |
| `hobbang:smith.ping` | 짧은 쇳소리 핑 |
| `hobbang:smith.grind` | 그라인더 |
| `hobbang:colossus.beam.charge` | 빔 예고 (차징 와인) |
| `hobbang:colossus.beam.fire` | 빔 발사 (레이저 캐논) |
| `hobbang:colossus.beam.hum` | 빔이 유지되는 동안 (짧게 반복) |
| `hobbang:sentinel.clockwork` | 태엽 / 리와인드 / 타임스톱 (8초짜리 있음) |
| `hobbang:sentinel.gears` | 톱니·라쳇·산업 기계 |
| `hobbang:sentinel.engine` | 오래된 엔진 험 (상시·준비) |
| `hobbang:wyvern.roar` | 전투 포효 (Breviceps 실녹음 + 커스텀, **엔더드래곤 아님**) |
| `hobbang:wyvern.roar.awaken` | 각성·페이즈·스톰 시작 |
| `hobbang:wyvern.roar.distant` | 원거리 앰비언트 |
| `hobbang:wyvern.breath` | 숨결/브레스 공격 |
| `hobbang:wyvern.flap` | 날갯짓 (**엔더드래곤 플랩 아님**) |
| `hobbang:wyvern.growl` | 짧은 그르렁 |

`boss/wyvern/roar_sandworm.ogg`는 파일만 있고 이벤트에 안 묶여 있다. 건드리지 마라.

## 교체 맵 (정확한 호출 지점)

바닐라가 이미 어울리는 자리는 **유지**하고, 그 위에 커스텀을 얹거나, 테마가 어긋난 자리만 교체한다.

### SmithBoss.java
- `startSlam` 예고 (`ENTITY_WITHER_SKELETON_AMBIENT`, ~374): **교체** → `hobbang:smith.scrape` vol 2.0 pitch 0.95. 테마 밖 위더 스켈레톤 소리를 빼라.
- `hammerSlam` 임팩트 (`ITEM_MACE_SMASH_GROUND_HEAVY` + `BLOCK_ANVIL_LAND`, ~395-396): **유지**하고 **추가** `hobbang:smith.slam` vol 2.0 pitch 1.0. 원하면 `hobbang:smith.ping`을 약한 볼륨으로 한 겹 더.
- `enterPhase` (`ENTITY_ENDER_DRAGON_GROWL`, ~355): **교체** → `hobbang:smith.scrape` + `hobbang:smith.slam`. 대장장이에게 드래곤 포효를 쓰지 마라.
- `fireVolley` (`ENTITY_BLAZE_SHOOT`, ~413): 바닐라 유지. 포탄 발사 순간에 `hobbang:smith.ping`을 약하게 추가해도 됨.
- `startWave` (`ITEM_FIRECHARGE_USE`, ~440): 바닐라 유지.
- `randomTeleport` (`ENTITY_ENDERMAN_TELEPORT`, ~262): 바닐라 유지.
- `summonApprentices` / `summonVex` (`ENTITY_EVOKER_PREPARE_SUMMON`, ~507): 바닐라 유지. 소환 직전에 `hobbang:smith.grind` 또는 `scrape`를 얹어도 됨.
- `onDeathStart` (`ENTITY_WITHER_SPAWN`, ~566): 바닐라 유지 + `hobbang:smith.slam`.

### ColossusBoss.java / BossPatterns.java
- 빔 시작 (`BLOCK_BEACON_POWER_SELECT`, ColossusBoss ~286, BossPatterns ~113): **교체** → `hobbang:colossus.beam.charge` vol 2.0 pitch 1.0.
- 빔 발사 (`BLOCK_BEACON_ACTIVATE`, ColossusBoss ~382; `ENTITY_GUARDIAN_ATTACK`, BossPatterns ~124): **교체** → `hobbang:colossus.beam.fire` vol 2.5 pitch 1.0. 빔이 유지되는 동안 몇 틱마다 `hobbang:colossus.beam.hum`을 약하게 반복해도 됨.
- 슬램 예고 (`ENTITY_IRON_GOLEM_ATTACK`, ~287) / 임팩트 (`ITEM_MACE_SMASH_GROUND_HEAVY`, ~345): 바닐라 유지. (바위 충돌 테마. 쇠 스크레이프는 쓰지 마라.)
- 샤드·스파이크·페이즈 (`BLOCK_AMETHYST_*`, ~361, 418, 477, 501, 580): 바닐라 자수정 **유지**. 레이저가 아닌 결정 파열에 레이저를 넣지 마라.
- BossPatterns 소닉/폭발 등 비빔 패턴: 해당 바닐라가 맞으면 유지.

### SentinelBoss.java
- 타임스톱 시작 (`BLOCK_NOTE_BLOCK_BELL`, ~289, ~350): 바닐라 벨 **유지** + `hobbang:sentinel.clockwork` vol 2.0 pitch 1.0.
- 타임스톱 해제 (`BLOCK_BEACON_DEACTIVATE`, ~296): 유지 + `hobbang:sentinel.gears`.
- `startRewind` (`BLOCK_PORTAL_TRIGGER`, ~319): 유지 + `hobbang:sentinel.clockwork`.
- 리와인드 완료 / 토템 (`ITEM_TOTEM_USE`, ~334): 유지 + `hobbang:sentinel.gears`.
- 스윕 임팩트 (`BLOCK_ANVIL_BREAK` ~206, `BLOCK_ANVIL_LAND` ~367): 바닐라 유지 + `hobbang:sentinel.gears`.
- 볼트 (`ENTITY_ARROW_SHOOT`, ~424): 바닐라 유지.
- 페이즈 / 벨 (`BLOCK_AMETHYST_BLOCK_CHIME` ~401, `BLOCK_BELL_RESONATE` ~459): 유지 + 페이즈에는 `hobbang:sentinel.engine` 또는 `clockwork`.
- 상시 앰비언트로 20~40틱마다 `hobbang:sentinel.engine`을 낮은 볼륨(0.4~0.7) 피치 0.9로 재생해도 좋다. 전투 사운드를 덮지 않게.

### WyvernBoss.java — 엔더드래곤 소리를 전부 빼라
- 포효 (`ENTITY_ENDER_DRAGON_GROWL`, ~330, ~545): **교체** → `hobbang:wyvern.roar`. 피치 0.9~1.05, vol 2.0~3.0. ~545(스톰/페이즈)는 `hobbang:wyvern.roar.awaken`이 더 맞다.
- 날갯짓 (`ENTITY_ENDER_DRAGON_FLAP`, ~383 클라임, ~394 주기 20틱): **교체** → `hobbang:wyvern.flap`. 주기 플랩은 vol 1.2 pitch 1.0.
- 다이브 (`ENTITY_PHANTOM_SWOOP`, ~344): 바닐라 스웁은 유지해도 되고, `hobbang:wyvern.flap`을 한 겹 추가하라. 다이브 시작에 `hobbang:wyvern.roar`를 짧게 한 번.
- 착지/폭발 (`ENTITY_GENERIC_EXPLODE`, ~363): 바닐라 유지.
- 오브 (`ENTITY_BREEZE_SHOOT`, ~407): 바닐라 유지 + 원하면 `hobbang:wyvern.breath`.
- 썬더 볼트 (`ITEM_TRIDENT_THUNDER`, ~433) / 윈드 (`ENTITY_WIND_CHARGE_WIND_BURST` ~454, `ENTITY_BREEZE_IDLE_AIR` ~460) / 라이트닝 (`ENTITY_LIGHTNING_BOLT_THUNDER`, ~510): 바닐라 **유지**.
- `ENTITY_ENDER_DRAGON_DEATH`는 쓰지 마라.

### DisplayBoss.java / AssembleAnim.java
공유 보스 코드다. **보스 종류 분기를 확인**하고 웨이번 자리에만 드래곤 커스텀을 넣어라. 대장장이 분기에 드래곤 소리를 남기거나 넣지 마라.
- ~453 `ENTITY_ENDER_DRAGON_FLAP` + 윈드: 웨이번이면 `hobbang:wyvern.flap`으로 교체. 윈드 버스트는 유지.
- ~893 / ~988 `ENTITY_ENDER_DRAGON_GROWL`: 웨이번이면 `hobbang:wyvern.roar` 또는 `wyvern.roar.awaken`. 스미스 분기는 `hobbang:smith.scrape`(+ slam). 센티넬은 `hobbang:sentinel.clockwork`.
- ~895 `ENTITY_WITHER_AMBIENT`, ~899 `BLOCK_BELL_RESONATE`: 각 보스 테마에 맞게 위와 같이 정렬.
- AssembleAnim 웨이번 조립 (`ENTITY_PHANTOM_FLAP`, `ENTITY_ENDER_DRAGON_FLAP`, ~121): 플랩을 `hobbang:wyvern.flap`으로. 라이트닝(~122-123)은 유지.
- AssembleAnim 스미스 조립의 `BLOCK_ANVIL_*`는 유지, 한 겹으로 `hobbang:smith.slam` / `smith.ping` 가능.
- AssembleAnim 센티넬의 구리/벨: 유지 + `hobbang:sentinel.gears`.

## 하지 말 것
- 스미스 모델, 텍스처, 릭, `BossModels` 생성 결과 수정 금지.
- 엔더드래곤 바닐라 사운드(`ENTITY_ENDER_DRAGON_*`)를 웨이번·스미스 어느 쪽에도 남기지 마라 (플랩·그로우 모두).
- 자수정 골렘의 결정 파열에 레이저를 넣지 마라. 레이저는 빔 스킬만.
- 저작권 있는 게임 음원으로 교체하지 마라.
- 푸시하지 마라. 서버를 켜지 마라.

끝나면 바꾼 Java 파일과 호출 목록만 보고하라. 팩을 다시 묶었다면 zip 경로에 백슬래시가 없는지, sha1은 얼마인지 적어라. 배포·릴리스는 하지 마라.
