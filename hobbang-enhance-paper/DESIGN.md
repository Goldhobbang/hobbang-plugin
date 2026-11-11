# 설계 메모 (Fabric 1.5.1 → Paper 1.21.11)

## 바닐라 클라이언트 호환 원칙
- **커스텀 아이템/블록 ID를 쓰지 않습니다.** 모든 홉방 아이템은 바닐라 아이템에 PDC(`hobbang:id`)와 데이터 컴포넌트를 붙인 것입니다.
  - 사용하는 컴포넌트: item_name, item_model, custom_model_data `"hobbang:<id>"`, rarity, lore, max_stack_size, glint, damage_resistant(불), use_cooldown.
  - 재료와 주문서는 `knowledge_book` 기반입니다. 바닐라 레시피에 쓰이지 않고 사용(소모)도 막아 두었으며, 외형은 item_model로 바닐라 아이템을 빌려 씁니다.
  - 강화대는 `smithing_table`, 가이드는 `written_book`, 보스 무기는 네더라이트 도끼/검 기반입니다.
- 강화 단계는 PDC `hobbang:level`에 저장되고, 이름 앞에 `[+N]` 태그(custom_name)로 표시됩니다.
  - 이름표로 바꾼 이름은 `hobbang:base_name`에 기억했다가 다시 붙입니다.
  - 모루로 이름을 바꿔도 태그가 유지됩니다.
- 리소스팩이 없어도 바닐라 모델로 보입니다. 나중에 리소스팩을 만든다면 `custom_model_data` 문자열 `hobbang:<id>`로 전용 텍스처를 입힐 수 있습니다(선택 사항).

## 데이터팩 자동 등록
- `HobbangBootstrap`이 `LifecycleEvents.DATAPACK_DISCOVERY`에서 jar 안의 `/hobbang_datapack` 폴더를 등록합니다(pack format 94.1).
- 들어 있는 것:
  - 인첸트 20종(설명은 한국어 텍스트)
  - 아이템 태그와 인첸트 태그
  - 발전과제 11개
- 발전과제 판정 방식:
  - 아이템 획득형: `custom_data` 술어로 판정합니다.
  - 강화 단계·보스 처치형: 플러그인 코드가 직접 부여합니다.

## 보스 구조 (성능 교훈 반영)
- 보스는 바닐라 히트박스 몹 위에 컨트롤러(`Boss`)가 붙는 구조입니다. 종류와 페이즈는 몹 PDC에 저장되어, 재시작이나 청크 재로드 후에도 다시 붙습니다(검증 완료).
  - 대장장이: 보이는 위더 스켈레톤(크기 ×1.5), 바닐라 AI 유지.
  - 콜로서스: 투명 철 골렘, AI 목표를 제거하고 추적·근접을 직접 구현.
  - 와이번: 투명 좀벌레(크기 ×5.5), AI·중력 끔.
  - 센티넬: 투명 늑대(크기 ×3.33), AI·중력 끔.
- 모델은 블록·아이템·텍스트 디스플레이 파츠로 만들며, 히트박스에 탑승시킵니다.
  - **비영구(setPersistent(false))**라서 월드에 저장되지 않으므로 고아 디스플레이가 생기지 않습니다.
  - 탑승 오프셋은 매 포즈마다 실제 위치 차이로 보정합니다.
  - 변환 값이 같으면 패킷을 보내지 않습니다. 포즈는 3틱마다 갱신하고 보간을 켭니다.
- 엔티티 전수 스캔을 하지 않습니다. 보스 목록은 EntityAddToWorld/RemoveFromWorld 이벤트로 관리하고, 전체 스캔은 활성화할 때 1회만 합니다.
- 파티클은 1.5.1에서 줄인 양을 그대로 씁니다. 의식 파티클은 2틱마다, 강화대 주변 효과는 24블록 안에 플레이어가 있을 때만 5틱마다 냅니다.

## Fabric과 다른 점 (의도된 근사)
- **강화석 광석 블록** → 커스텀 블록을 쓸 수 없어서, Y32 아래의 돌·심층암·응회암을 캘 때 0.4% 확률로 "광맥" 드롭으로 바꿨습니다.
  - 개수, 행운 공식, 경험치, 심층암의 빛나는 강화석 3%는 원작 광석과 같습니다.
- **주민 거래**: Fabric은 후보 풀에 거래를 추가했습니다. Paper에서는 해당 레벨의 거래가 처음 생길 때 같은 확률(2/(후보 수+1))로 홉방 거래가 바닐라 거래 하나를 대신합니다.
  - 5레벨(후보 1개)은 바닐라 거래에 더해서 추가됩니다.
  - 떠돌이 상인은 생성 시 15~16% 확률로 홉방 거래가 추가됩니다.
- **낚시 보물과 고고학**: 바닐라 가중치 합(보물 6, 오솔길 희귀 12, 사막 8)에 원작과 같은 가중치를 더한 확률로, 결과를 홉방 아이템으로 바꿉니다.
- **selftest**: Fabric의 가짜 플레이어 테스트 대신 상태 점검 명령으로 바꿨습니다(목업·테스트 코드 없음).

## 가드
- 바닐라 레시피, 제작기, 지도 제작대, 베틀에 홉방 아이템을 섞으면 결과가 나오지 않습니다. 숫돌은 보스 무기를 합치는 것만 막습니다.
- 유물 단조(대장장이 작업대)에서는 결과를 다시 만듭니다. 기반 장비의 인첸트, 내구도, 강화 단계, 장인의 기운을 이어받고 무기 고유 속성 기준으로 강화 수치를 다시 계산합니다.
- 바닐라 네더라이트 강화를 한 강화 장비도 속성을 다시 계산합니다.
- 보스 무기는 다른 변환의 재료로 쓸 수 없습니다.

## v1.1.0 인첸트 9종 추가
- **상위 날카로움 (max 방식, 데이터팩)**: `minecraft:damage` 효과 11개로 구현합니다. 공격자 주 손의 바닐라 날카로움 레벨 v(0~10)마다 조건을 걸고, `clamped(4 + (L-1) − (0.5v+0.5), min 0)`만큼 더합니다.
  - 결과적으로 총 보너스는 max(바닐라, 상위)입니다. 공격 쿨다운 보정도 바닐라와 똑같이 받습니다.
  - 날카로움 11 이상(명령어로만 가능)이면 바닐라가 항상 더 높아서 상위 쪽은 0입니다.
- **상위 보호 (max 방식, 플러그인)**: JSON 효과는 비워 두고, `EntityDamageEvent`의 MAGIC 모디파이어에서 처리합니다.
  - 현재 EPF는 MAGIC ÷ (MAGIC 앞 모디파이어 합) × 25로 역산합니다.
  - 장비마다 max(0, (4+L) − 보호 Lv)를 더한 뒤 바닐라처럼 20에서 자릅니다.
  - 역산값은 이미 20으로 잘린 값이어도 결과가 같아서 정확합니다(20 이상이면 어차피 20).
  - `bypasses_enchantments`와 `bypasses_invulnerability` 피해는 바닐라 보호처럼 건너뜁니다.
- **희귀도**: 상위 2종은 treasure 태그에만 넣었습니다. on_random_loot와 tradeable에서 빼서 바닐라 경로로는 나오지 않고, `ModLoot.SUPERIOR` 표와 보스 드롭만으로 나옵니다.
- **급소**: 데이터팩 `multiply`는 인첸트 적용 순서에 따라 결과가 달라져서 플러그인에서 처리합니다. 근접 공격(ENTITY_ATTACK)만 해당하며, 쿨다운이 반영된 최종 피해에 ×1.5를 합니다.
- **염동력과 자동 제련**: `BlockDropItemEvent`에서 처리합니다. 자동 제련은 NORMAL, 염동력은 HIGH 단계에서 돕니다. 광역 채굴과 벌목꾼(`Player#breakBlock`)에도 적용됩니다.
  - 플러그인이 직접 떨구는 강화석 광맥과 광석 보너스는 `EnchantListener.dropOrGive`로 처리합니다.
  - 화로 레시피 표는 시작할 때 한 번 만들고, 숯 레시피는 뺍니다.
- **짓밟기와 생명 수선**: 이벤트 하나에서 한 번만 계산합니다. 틱마다 도는 작업은 없습니다.
- **검증 (box paper-test, mineflayer)**
  - 다이아 검으로 측정한 피해: 기본 7, 날카로움 V 10, 상위 I 11, 상위 III 13, 날V+상위I 11, 날V+상위III 13, 날I+상위II 12, 날X+상위I 12.5.
  - magic 10 피해 기준: 보호 IV 신발 8.4, 상위 I 8.0, 보호IV+상위I 8.0, 보호IV+상위III 7.2, 보호 IV 풀세트 3.6, 보호IV+상위I 2벌 6.0, 상위 I 풀세트 2.0(상한).

## v1.2.0 강화 창 · 소환대 · 보스 패턴 · 인첸트 개편
- **강화 창**: `ForgeGui`(커스텀 `InventoryHolder`, 54칸)가 `EnhanceLogic.Source`를 구현해 장비/보조 아이템을 창 칸에서 읽고 소모한다. 실제 굴림은 기존 `EnhanceLogic.attempt(player, block, source)` 하나. 확률 표시는 `EnhanceLogic.absolute()`(성공 / (1−s)·유지 / (1−s)·min(하락,100−파괴) / (1−s)·파괴) + `effective()`(주문서 우선순위: 역행 > 불멸 > 파괴 방지 > 축복).
  - 복사 방지: 표시 칸 클릭·숫자키·더블클릭(COLLECT_TO_CURSOR)·드래그 취소, 쉬프트 입력은 직접 처리, InventoryMove/Pickup 취소. 반환은 닫기·퇴장·사망·onDisable·강화대 파괴에서 한 번만(closed 플래그), 가방이 차면 발밑 드롭. 10틱 점검으로 강화대가 사라지거나 8칸 밖이면 강제 종료. 버튼 0.5초 쿨다운 + busy 가드.
  - `legacyRightClick`(기본 false) = 예전 우클릭 즉시 강화.
- **소환대**: `Altar` — 스컬크 감지체 + 청크 PDC(`hobbang:altars`)로 위치 추적(강화대와 같은 방식). `BlockReceiveGameEvent` 취소 + `BlockRedstoneEvent` 0으로 진동/신호 차단. 창은 모든 클릭 취소, 보스 선택 시 `Ritual.start()` 재사용.
- **보스 패턴**: `BossPatterns` = 예고형 공용 부품(충격파 고리, 광선, 운석, 나선, 소용돌이, 페이즈 연출) — 모두 `Fx`(비영구 디스플레이 + 수명, 보스 언로드 시 `discardOwned`) 위에서 동작. 파티클은 2~3틱 간격·점 수 상한, 피해 판정은 BoundingBox 내 플레이어만. 각 보스의 기존 쿨다운 구조(대장장이 serverTick, 콜로서스 ACT_NONE 대기, 와이번 M_CIRCLE/M_REST, 센티넬 쿨다운)에 쿨다운 변수를 추가해 끼움. 페이즈 연출은 `Boss.setPhase()`에서.
- **보상 발광**: `BossLoot.drop()` — 보스 드롭을 EntityDeathEvent 드롭에서 빼서 직접 Item 엔티티로 생성(setGlowing, setUnlimitedLifetime, setInvulnerable, PDC 태그). 색은 메인 스코어보드 팀 `hb_glow_<색>`, 아이템이 월드에서 빠지면(`EntityRemoveFromWorldEvent`) 팀 항목 삭제.
- **인첸트 비용**: `EnchantingListener` — Prepare 단계에서 원래 비용을 저장하고 표시 비용을 ceil(c/3)(1~10)로. EnchantItemEvent에서 소모 레벨 = min(표시, 버튼+1), 결과는 원래 비용으로 `ItemFactory.enchantWithLevels(…, #in_enchanting_table, seed+버튼)` 재계산, 힌트 인첸트는 항상 포함. 모루는 최대 비용 무제한 + ceil(c/3)(1~10).
- **상자 전용**: 8종을 `non_treasure`/`on_random_loot`/`tradeable`에서 빼고 `treasure`에만 둠 → 바닐라 테이블·거래·낚시·무작위 인첸트에서 제외. `ModLoot.RARE`(상자별 확률)로 LootGenerateEvent/금고에서만 책 추가.
- **강화 마일스톤**: `EnhanceTier`(종류 판별 + 수치표 + 설명) / `EnhanceEffects`(이벤트 전용: PrePlayerAttackEntityEvent로 풀차지 기록 → EntityDamageByEntityEvent에서 출혈·관통·장인의 일격(MAGIC 피해, 재귀 가드), PlayerItemDamageEvent 도구 내구도, EntityShootBowEvent 화살 보존(줍기 불가 + 1개 반환), ProjectileHitEvent 방패 반사). `ItemUtil.applyStats`는 우리 키(hobbang:)와 바닐라 기본값을 지우고 다시 쌓으므로 강화/하락 모두 중복 없음. `stat_ver` PDC로 예전 장비를 접속/상자 열기 때 재계산. 보스 무기는 `WeaponListener`에서 단계로 피해·범위·쿨다운·추가 효과를 계산.

## v1.4.0 시련의 장소 · 폭풍 제단 · 무기 2D/3D
- **구조물**: `structures/gen.py` 가 바닐라 블록 템플릿(NBT)을 생성 → `hobbang_datapack/data/hobbang/structure/*.nbt`. worldgen 은 jigsaw(단일 풀 요소) + `random_spread` structure_set + 바이옴 태그.
  `step = top_layer_modification` 이라 현무암 기둥 같은 지형 장식이 투기장 안을 채우지 않음. 대장간 = beard_box(공중이면 받침), 신전 = beard_thin + WORLD_SURFACE_WG 투영, 정동 = 지하 uniform 높이.
  제단 블록의 TileState PDC(`hobbang:altar`=보스 id)가 템플릿에 들어 있어 worldgen 후에도 유지(검증: `data get block`).
- **Arenas**: `Site` enum(제단 블록, 반경, y 범위, 스폰 오프셋, 차원) + `ArenaRef`(보스 PDC `hobbang:arena` = "SITE;world;x;y;z"). 보스가 붙을 때 `b.arena` 로 읽음.
  보호는 이벤트 전용(설치/파괴/양동이/폭발/피스톤/EntityChangeBlock, `activeAt` 은 살아 있는 보스의 arena 만 순회). 40틱마다 보스 위치만 확인(leash).
- **StructurePlacer**: 관리자 설치. `StructureManager.loadStructure` → 팔레트 블록 목록을 틱당 1,000개씩 `setBlockData(false)`. 측정: 정동 23틱/크로노 신전 24틱, 설치 중 max tick 15.3 ms.
- **StormArena**: 제단 5×5 패턴 검사 → 천둥 확인 → 계획(원판·원뿔·위성·나선 계단) 중 막힌 칸 비율 15% 초과 시 거절 → BUILDING(틱당 5블록, 12틱마다 번개 효과) → `Ritual.startConsumed` → ACTIVE.
  각 칸에 (원래 BlockData, 놓은 BlockData)를 기록하고, 무너질 때 현재 블록이 놓은 것과 같을 때만 원래대로(플레이어가 바꾼 칸은 건드리지 않음). Gson 으로 arenas.json 저장/복구.
- **소환대**: `Altar` 창은 안내만. `World.locateNearestStructure(loc, Structure, 64, false)` 를 15초 쿨다운으로(동기 탐색 비용 제한). 다른 차원 구조물은 그 차원 좌표로 변환.
- **무기 표시**: 바닐라 아이템 정의를 `custom_model_data` strings[0] select 로 감싸 홉방 무기만 바꿈(일반 철퇴·삼지창 등은 원래 모델). 그 안에서 `display_context` select.
  망치·도끼 3D 는 obj2mc 큐보이드 모델(망치 58개, 도끼 203개) + display 변환, 텍스처 애니메이션은 `.png.mcmeta`(망치 8프레임 보간 맥동, 도끼 8프레임 불규칙 번쩍임 frametime 2).
- **대장장이 모델**: 몸(2,744 큐보이드, 6파츠) + 별도 망치 파츠(259 큐보이드)를 `arm_r` 자식 `hammer` 리그에 붙임. 손목 각(wrist)으로 내려찍기 들어올림/충격/휘두르기 연출, 망치 머리 위치는 리그에서 계산(`worldPoint`).
- **무기 정체성 변경(v1.4.0)**: `HItem.STORMCALLER` = 네더라이트 도끼 기반(item_model `minecraft:netherite_axe`), `CHRONO_DAGGER` = 네더라이트 검 기반(item_model `minecraft:netherite_hoe`, ENTITY_INTERACTION_RANGE +1). id 는 유지.
  `ItemUtil.migrateIdentity`: PDC `item_rev` < 3 인 보스 무기를 `ItemStack.withType(base)` 로 바꾸고(컴포넌트·PDC 유지) item_name/item_model 갱신 → `reapply`(속성·이름·설명). 호출은 기존 `migrate` 와 같은 곳(접속, InventoryOpen).
  낫 베기는 `around()` 결과를 수평 각도(±75°)로 거르는 것뿐이라 추가 틱 작업 없음. 파티클 ~45점.

## v1.4.1 들고 있는 무기 · 구조물 외관 · 최적화
- 손에 든 3D 무기 ~1.5배: 망치 1인칭 rot[-30,0,0] scale 0.52 / 3인칭 rot[90,0,0] 0.8, 전투도끼 1인칭 rot[0,-90,-30] 0.45 / 3인칭 rot[90,90,0] 0.8
  (도끼날·망치머리가 바라보는 방향=앞). 2D 자리표시(수정 대검·크로노 낫)도 vanilla handheld 의 ~1.3배. 새 3D 무기는 `weapons/held_display_template.json` 을 복사해 시작.
- 구조물 (structures/gen.py): 대장간 61×30×61(부벽·성문탑·용암 수로 길·대장간 마당 2곳·무너진 북동 성벽·굴뚝 띠/처마),
  신전 57×27×57(응회암/심층암 기단·구리 줄 박힌 대로·무너진 외곽 열주 8개·바닥 톱니 모자이크·아치 위 세로 톱니바퀴·진달래 덤불),
  정동 47×27×47(노이즈로 깎은 천연 동굴 껍질: 자수정→방해석→매끄러운 현무암→섞인 동굴 암석, 종유석·이끼·발광 이끼·늘어진 뿌리·포자 꽃, 굽은 굴 4개).
  생성 후처리: 담장/창살 연결 상태·매달린 랜턴 계산, 열린 면이 있는 용암은 마그마로 바꿔 월드젠 때 용암이 흐르지 않음. 투기장 안쪽은 그대로.
- 폭풍 투기장 발판: 고정 시드로 블록 섞기(금 간 타일·심층암 벽돌·산화 단계 다른 구리), 원반 아래 구리 사슬+랜턴, 위성 발판 피뢰침/랜턴 (+≈40블록).
- 최적화: 서버 시작 2초 뒤 구조물 템플릿 미리 읽기(첫 설치 때 1회성 렉 제거), 애니메이션 텍스처 interpolate 제거(클라이언트가 매 틱 512² 프레임을 섞어 다시 올리던 비용 제거).
