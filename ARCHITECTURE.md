# 아키텍처 (HobbangEnhance 1.4.4)

Paper 1.21.11 플러그인과 전용 리소스팩이 한 세트로 동작합니다. 클라이언트는 바닐라입니다. 모드나 별도 데이터팩 설치는 필요 없고, 데이터팩은 플러그인 jar 안에 들어 서버가 자동 등록합니다. 리소스팩은 서버 `resource-pack` URL로 받거나 Prism `resourcepacks`에 넣습니다.

더 오래된 설계 메모(강화 창·인첸트 공식 등)는 `hobbang-enhance-paper/DESIGN.md`에 있습니다. 아래는 1.4.4 기준의 현재 구조입니다.

## 플러그인 모듈

루트: `hobbang-enhance-paper/src/main/java/com/hobbang/enhance/`

| 패키지 / 클래스 | 역할 |
|---|---|
| `HobbangPlugin`, `HobbangBootstrap` | 기동, 데이터팩 디스커버리(`LifecycleEvents.DATAPACK_DISCOVERY` → jar 안 `/hobbang_datapack`, pack format 94.1) |
| `enhance/`, `forge/` | +1~+15 강화, 강화대 블록, 54칸 `ForgeGui` |
| `item/`, `recipe/`, `weapon/` | 홉방 아이템(PDC `hobbang:id`), 레시피 가드, 보스 무기 스킬 |
| `boss/` | 보스 런타임 |
| `arena/`, `world/` | 시련의 장소 보호, 구조물 설치, 폭풍 투기장 |
| `command/` | `/hobbang` (`hobbang.admin`, 기본 OP) |

빌드: Gradle 9.8.2 wrapper, Java 21 toolchain, `paper-api:1.21.11-R0.1-SNAPSHOT`, 태스크는 `jar` (음영 포함 없음). 버전은 `build.gradle.kts`의 `version`이 `paper-plugin.yml`로 확장됩니다.

### 아이템 규칙

커스텀 아이템 ID를 만들지 않습니다. 바닐라 아이템에 PDC와 컴포넌트(`item_name`, `item_model`, `custom_model_data` 문자열 `hobbang:<id>` 등)를 붙입니다. 강화 단계는 `hobbang:level`. 보스 무기 예:

- `STORMCALLER` — 네더라이트 도끼, item_model 경로상 `stormcaller`, 이름 뇌운 전투도끼
- `SMITH_HAMMER` — 네더라이트 도끼 기반, item_model `smith_hammer` (메이스 외형 경로), 대장장이의 망치
- 수정 대검 · 크로노 낫(내부 id `chrono_dagger`) — 네더라이트 검/괭이 기반

콘솔에서 `/hobbang give`는 플레이어 실행이 필요합니다: `execute as <player> run hobbang give <id>`.

## 보스: DisplayBoss

보스는 **보이지 않는 바닐라 히트박스**와, 그 위에 탑승한 **`item_display` 파츠**로 그립니다. 컨트롤러는 `DisplayBoss`와 종류별 서브클래스입니다. 종류·페이즈는 히트박스 PDC에 남아, 청크 언로드 후 다시 붙습니다.

| 보스 | 클래스 | 히트박스 | 비고 |
|---|---|---|---|
| 심연의 수습 대장장이 | `SmithBoss` | WITHER_SKELETON | 조립 연출 `ASSEMBLE` 90+12=102틱 무적 |
| 프리즘 콜로서스 | `ColossusBoss` | IRON_GOLEM | `EMERGE` 58+12=70틱 |
| 폭풍의 와이번 | `WyvernBoss` | SILVERFISH (lift 4) | `DIVE` 소환 56+12=68틱. NoGravity |
| 크로노 센티넬 | `SentinelBoss` | WOLF | `COMPRESS` 52+12=64틱 |

파츠 정의와 레스트 오프셋은 obj2mc 매니페스트를 `BossModels`가 읽습니다. `item_display`는 비영속(`setPersistent(false)`)이라 월드에 고아 디스플레이가 남지 않게 합니다. 변환이 같으면 패킷을 보내지 않고, 포즈는 보간을 켭니다.

소환: `/hobbang boss <id>` 는 바라보는 방향 8칸 앞. id는 `abyssal_smith`, `prism_colossus`, `tempest_wyvern`, `chrono_sentinel`. 월드젠 구조물의 제단을 통한 소환은 `Ritual` / `arena` 쪽입니다.

관리 목록은 `BossManager`가 엔티티 추가·제거 이벤트로 유지합니다. 전체 스캔은 활성화 시 1회입니다.

### 와이번 AI (요지)

모드: `M_CIRCLE` → `M_DIVE_AIM`(30) → `M_DIVE`(40) → `M_REST`(60) → `M_CLIMB`(30). `diveCd`는 무적 이후 `M_CIRCLE`에서만 감소하며 시작값 160. 리그는 23파트(머리·목 2·몸·엉덩이·꼬리 4·날개 2·다리 12). `poseModel`이 look-at, 척추, 숨, 비행/착지 다리, 날개, 소환 접힘을 돌립니다.

알려진 물리 한계: DisplayBoss가 히트박스에 `NoGravity`와 `setVelocity`를 쓰는데, 1.21.11 좀벌레에서는 **Y 속도가 적용되지 않는** 경우가 있습니다. 관측된 히트박스 Y가 `160.7606113819401`에 고정되고 Motion Y=0, XZ만 선회. 그래서 전투 급강하가 내려가지 않고, 바닥 착지(`M_REST`) 포즈를 찍지 못했습니다. 1.4.4 jar에는 이 물리 수정이 들어가 있지 않습니다.

### 죽음

치명타(또는 그에 해당하는 피해)는 `DisplayBoss.onDamage`가 취소하고 `startDeath()`로 넘깁니다. `/kill`만 믿지 말고 실제 치명 피해로 확인해야 합니다. `serverTick` → `tickDeath()` → `finishDeath()`(보상, `ArenaDress.restore`, `discardParts`). 히트박스가 먼저 사라져도 `BossManager.DYING`이 남은 틱을 돌리게 되어 있습니다.

연출 길이(틱): 대장장이 100, 와이번 110, 센티넬 150, 콜로서스 100.

알려진 문제: `poseDeath`는 시작하는 경우가 많지만 `finishDeath` / 파츠 discard / 처치 방송이 다음 `/kill @e` 때까지 미뤄지는 경우가 있습니다. `deathKiller`가 비면 방송이 "누군가"로 나갑니다. 원인 후보는 `tickDeath` 조기 종료, `onDeath`가 `dead`만 세우고 discard 전에 `finishDeath`가 반환, DYING 추가/제거 경쟁, 삼켜진 예외 등이며 1.4.4에서 완전히 닫히지 않았습니다.

첫 대장장이 스폰 워밍업 스파이크(약 42 파츠 / 31 블록, ~104 ms)는 남아 있습니다.

## 리소스팩

- 포맷: pack format **75.0** (`min_format`/`max_format` `[75, 0]`)
- 소스: `resourcepack/hobbang-pack/`
- 공식 zip: `obj2mc/make_zip.py` (결정적 타임스탬프, 동반 `.sha1`)
- 보스·무기 3D: `assets/hobbang/items/...`, `models/item/...`, `textures/item/...` (1.21.11 items 아틀라스, 디렉터리 소스 `item/`, 밉맵 없음)
- 든 무기: 바닐라 아이템 정의를 `custom_model_data` 문자열 select로 감싸고, 그 안에서 `display_context`로 1인칭/3인칭/왼손을 나눕니다. display 수치의 소스 오브 트루스는 `weapons/*_display.json`이며, 팩의 `held.json` / `*_hand.json`과 맞춰 둡니다.

개발 중에는 테스트 서버의 `hobbang-pack-dev`에 복사한 뒤 클라이언트에서 F3+T로 다시 읽습니다. 공식 zip과 홈 서버 URL은 따로 다시 묶어야 합니다.

## obj2mc 파이프라인

`obj2mc/obj2mc.py`가 텍스처 있는 Meshy OBJ를 큐보이드만 있는 아이템 모델로 바꿉니다.

1. `height_blocks`로 정규화, 발을 y=0, XZ 중앙. 앞은 +Z.
2. `resolution` voxels/block으로 샘플링 후 내부 채움.
3. `parts`의 region AABB(또는 원기둥/구)로 분할. **먼저 맞는 파트가 이깁니다.** 마지막에 catch-all.
4. 색을 무시하고 형태만으로 greedy cuboid. 같은 파트가 가린 면은 버리고, 다른 파트가 가린 면은 남깁니다(움직일 때 보이므로).
5. 파트별 아틀라스에 면 텍스처를 굽습니다. embree 레이캐스트 + `texels_per_voxel`.
6. 피벗을 모델 (8,8,8)에 두고, 모든 파트가 마인크래프트 요소 한도(-16..32, 여유 0.5) 안에 들어가게 `model_units_per_block`을 고릅니다. 모델은 Y 180°를 미리 돌립니다.
7. 매니페스트에 parent, pivot_blocks, translation, scale, cuboid 수를 기록합니다.

꼬리처럼 관절 틈이 보이면 피벗을 옮기지 말고, 자식 region을 부모 쪽으로 overlap 시키는 편이 Java 오프셋을 유지합니다. 와이번 1.4.4는 이 방식으로 약 0.30 레이아웃 단위를 겹쳤습니다.

zip ↔ config 대응, 해상도, 큐보이드 수, 피벗 표는 `README.md`의 models-3d 절을 보세요.

## 구조물 · 투기장

데이터팩 worldgen(jigsaw + `random_spread`)으로 시련의 장소가 새 청크에만 생깁니다. NBT 템플릿은 플러그인 리소스의 `hobbang_datapack`에 들어 있습니다. `StructurePlacer`는 관리자 설치용으로 틱당 블록을 나눠 놓습니다. 활성 보스 arena만 이벤트 기반으로 보호하고, `ArenaDress` / `StormArena`가 바닥을 바꿨다가 보스 종료 때 원상 복구합니다.

## 알려진 1.4.4 한계

- 와이번 히트박스 Y 고정 → 착지 스틸을 확보하지 못함. 비행 중 몸·목·꼬리(0.30 overlap)는 촬영한 포즈에서 이어져 보였음.
- 죽음: 연출은 시작되나 `finishDeath`가 종종 지연. 촬영본에 이전 보스/불 잔여가 섞일 수 있음. 대장장이 무릎 꿇기가 카메라에서 분명히 확인되지 않음.
- 첫 대장장이 워밍업 틱 스파이크.
