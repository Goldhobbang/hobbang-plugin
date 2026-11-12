# 프리즘 콜로서스 · 크로노 센티넬 리모델링 보고

1.5.0의 OBJ 자동 복셀 모델을 버리고, 대장장이 1.5.2와 같은 방식으로 다시 만들었습니다. 큼직한 큐보이드 판에 16px/블록 손픽셀 텍스처를 입혔고, 색은 원본 텍스처에서 추출한 팔레트만 썼습니다.

- 브랜치: `feat/colossus-sentinel-remodel` (작업 폴더 `../hobbang-plugin-golems`)
- 베이스: `c06c442` (main 작업 트리의 1.5.2 상태 복사본, feat/weapons-detail과 같은 베이스) + `0934dce`(파편 정리) cherry-pick
- push·merge: 하지 않음
- 서버·클라이언트: 실행하지 않음
- 건드리지 않은 것: 대장장이·망치·무기 파일, `BossPoses.smith`, 버전 번호

생성기는 `obj2mc/make_golems.py`, 팔레트 추출은 `obj2mc/golem_palette.py`, 비교 이미지는 `obj2mc/golem_compare.py`입니다.

## 결과

| 보스 | 큐보이드 1.5.0 → 신규 | 파츠 텍스처 px 합 1.5.0 → 신규 | 아틀라스 (파츠당) | 상한 |
|---|---:|---:|---|---|
| 프리즘 콜로서스 | 3,089 → **285** | 2,424,832 → **409,600** | 128²~256² | 4,000 |
| 크로노 센티넬 | 3,456 → **262** | 2,686,976 → **123,904** | 32²~256² | 4,000 |

이미지 (`docs/img/golems-remodel/`):
- 원본 메시 · 1.5.0 · 신규 비교 (정면, 3/4, 측면): [콜로서스](img/golems-remodel/prism_colossus_compare.jpg) · [센티넬](img/golems-remodel/chrono_sentinel_compare.jpg)
- 4포즈 모션: [콜로서스](img/golems-remodel/prism_colossus_motion.jpg) · [센티넬](img/golems-remodel/chrono_sentinel_motion.jpg). 실제 `BossPoses` 코드로 포즈를 계산했습니다(`BossPosePreview` → `render_boss_poses.py`).
- 팔레트 스와치: [palette_swatches.png](img/golems-remodel/palette_swatches.png)

## 프리즘 콜로서스: 위압감

- **비율**: 어깨 바위 1.45×1.25×1.65블록, 어깨 요크 폭 3.55블록으로 상체를 크게 잡았습니다. 머리는 어깨선 사이로 낮게 묻었고, 다리는 굵고 짧게 했습니다(허벅지 1.25×1.1블록, 발판 1.65×2.05블록).
- **결정**: 4단으로 가늘어지는 기둥으로, 크기·각도·길이가 전부 다릅니다. 22.5°·45°는 요소 회전으로, 67.5°·90°는 긴 축을 바꾼 박스로 냈습니다. 등·어깨·머리 크라운·전완·주먹·무릎에서 뚫고 나옵니다. 원본처럼 주먹은 결정이 가장 많습니다.
- **몸체**: 갈라진 암석 판입니다.
  - `rock` 판: 2px 베벨, 깎인 모서리, 지층 턱선, 계단식 균열
  - `rock_glow` 판: 균열 틈으로 자수정 빛이 3단(#45057f → #801fe5 → #e469fb)으로 번지고, 핫스팟은 #f4bfff
- **유지한 것**:
  - 가슴 코어: 45° 다이아 소켓 + 동심 다이아 발광
  - 거대한 주먹: 너클 4줄, 엄지
  - 궤도 수정 3개: 기존대로 Java 블록 디스플레이

## 크로노 센티넬: 기계적인

- **시계부**:
  - 팔각 베젤(정사각 + 45° 정사각)과 금색 다이얼(12시 눈금, 3시간마다 긴 눈금)
  - 뒤판은 16각 별 모양
  - 베젤 둘레 스터드 12개
- **회전 부품**은 이빨이 보이는 큐보이드로 만들었습니다(팔각 디스크 + 방사형 이빨 8개).
  - 머리 위 크라운 기어
  - 위성 톱니 4개: 원본에서는 떠 있던 것을 축으로 연결
  - 팔꿈치·칼날 허브·진자 끝 톱니
  - 다이얼 양옆 톱니 `gear_l` / `gear_r`: **새 회전 파츠**
- **조립 구조**:
  - 등판과 척추 기둥
  - 어깨 요크와 어깨 소켓
  - 요크에서 허리로 내려가는 피스톤 2개: 실린더 + 로드 + 클램프
  - 팔은 2단 스트럿(22.5° → 45°)에 피스톤을 달았습니다.
  - 칼날은 앞으로 22.5° 기울어진 잎 모양 6마디로, 황동 테두리, 녹청 몸체, 청록 룬이 들어갑니다.
  - 진자는 모래시계, 리벳 기둥 4개, 받침판, 펜던트 톱니로 구성했습니다.
- **대칭과 정밀함**:
  - 좌우 완전 대칭
  - 황동 판 텍스처: 4톤 베벨, 패널 이음선, 모서리 리벳 4개
  - 시간 룬 발광: 3단, 모래시계 문양

## 리그 호환

- **기존 파츠**: 이름, 부모, 피벗(`pivot_blocks`), 회전축(`spin_axis`)이 1.5.0 매니페스트와 비트 단위로 같습니다. 생성기가 config의 고정 피벗을 그대로 쓰고, 다르면 assert로 멈춥니다.
- **콜로서스**: `BossPoses.colossus`, `ColossusBoss`를 수정하지 않았습니다.
- **센티넬 변경점**
  - **새 파츠 `gear_l`, `gear_r`**: 부모 `body`, 피벗 `(±0.78, 3.40, 0.30)`. 시계와 맞물리도록 `rotZ(±3 × gear)`로 반대 방향, 1.5배 속도로 돕니다. gear 각도는 기존 `gear` 값을 그대로 받으므로 시간 정지(멈춤)와 역행(역회전)을 따라갑니다.
    - 수정한 코드: `BossPoses.sentinel` 2줄, `SentinelBoss` 파츠 생성, 사망 조명 목록
    - 이유: 요청한 "톱니바퀴를 회전 파츠로 분리"
  - **`clock` 파츠 = 시계 바늘**: 다이얼판은 고정된 `body`로 옮겼습니다. 기존 회전(`rotAxis(-2·gear, spinAxis)`)이 이제 다이얼 전체가 아니라 바늘만 돌려서 실제 시계처럼 보입니다. 피벗과 회전축은 그대로입니다.
  - **`ring` 수평화**: 원본 링은 21° 기울어 있었지만 코드는 Y축(`rotY(gear)`)으로 돌려서, 기울어진 채 돌면 흔들려 보였습니다. 요소 회전이 축 하나만 허용되기도 해서 수평 링(16마디 + 바깥 이빨 16개 + 바큇살 4개)으로 만들었습니다. 피벗과 매니페스트 `spin_axis` 값은 유지했습니다(코드에서 쓰지 않음).
- **폴백 블록 모델과 텍스트 라벨**: 수정하지 않았습니다.
- **`BossModels.java`**: `make_java_rig.py`로 다시 생성했습니다. 대장장이 줄은 베이스 그대로이고, 콜로서스·센티넬 큐보이드 수와 gear 2줄이 바뀌었습니다.

## 팔레트 (원본 텍스처 추출만 사용)

| 계열 | 값 | 출처 |
|---|---|---|
| 콜로서스 암석 | #050907 #161118 #2a242d #3a343c #433d45 #544d57 #5e5862 | 표면 샘플 p30·p85·p95 + 텍스처 밝은 암석 계열 |
| 콜로서스 자수정 | #370067 … #e469fb, #f07bfc #f4bfff #ffefff | 결정 계열 p5~p99 + 밝은 계열 |
| 센티넬 황동 | #371400 … #d18b1c (8톤) | 황동 계열 p5~p99 |
| 다이얼 금색 / 광택 | #e8a328 … #ffd957, #ffea73 #fff37e #ffffa6 | 다이얼 면, 밝은 계열 |
| 어둠 / 바늘 / 녹청 / 모래 | #220000…, #3b1500 #4d2100 #6d3b00, #3e4b3e…#5a5c46, #c37a18…#fce26f | 각 계열 |
| 청록(희미) | #366055 #3e6a54 | 센티넬 자체 청록 계열 |
| 시간 룬 발광 | #44938a #6ac4b8 | **크로노 낫 원본 텍스처**(같은 크로노 세트). 센티넬 텍스처에는 밝은 청록이 없어서 가져왔습니다. |

## 검증

- **Gradle**: `./gradlew clean jar` 성공, 오류 0.
  - 텔레포트 제거 예정 **경고 6건**은 그대로 남아 있습니다. 이 수정(`51952a5`)은 `parallel/colossus-stability` 브랜치에 있고, 지시대로 `0934dce`만 가져왔기 때문입니다.
- **`validate_pack.py`**: items 80, elements 1,091, rotated 636, errors 0, warnings 0.
  - items가 78 → 80인 것은 `gear_l/r` 때문입니다.
  - elements 1,091은 팩 전체(대장장이·와이번·무기 포함) 합계입니다.
- **dist/golems-remodel/** (`SHA1SUMS.txt`)
  - `hobbang-enhance-paper-1.5.2-golems.jar`: 564,232 bytes, sha1 `8ae1f5d27c9a488042c160069196c003c6d64243`
  - `hobbang-pack-golems.zip`: 327,330 bytes, sha1 `ece44c4aca8ae0dd841d3f26b5fb78b72fe27396`

## 남은 이슈

- **인게임 미확인** (껌냥이 테스트): 요소 회전 방향, 결정 끝 실루엣, 다이얼·바늘 겹침(z 간격 0.03~0.1블록), 날 끝 관통 여부.
- **`gear_l/r`이 늘어난 영향**: 센티넬 디스플레이 9 → 11개, 3틱당 변환 갱신 상한 3.0 → 3.67/틱. `/mspt` 확인이 필요합니다.
- **원본과의 차이**
  - 콜로서스는 원본보다 어깨폭이 넓고 머리가 작습니다(위압감 의도).
  - 센티넬 다이얼은 원형이 아니라 팔각입니다. 원형은 큐보이드로 근사해야 해서 판 수가 늘어납니다.
  - 센티넬 칼날은 매끈한 잎 모양이 아니라 6마디 계단입니다.
- **합칠 때 순서**
  1. 대장장이(1.5.3)·무기 브랜치를 합친 뒤 `make_java_rig.py`로 `BossModels.java`를 다시 생성합니다. 파일 전체가 매니페스트에서 생성되므로 충돌하면 재생성이 맞습니다.
  2. 팩 zip과 SHA1을 다시 만듭니다.
- **콜로서스 파편 정리 커밋(`0934dce`)의 복셀 결과**: 이 리모델로 대체됐습니다. 그 커밋의 `clean_islands`·`freeze_pivots`는 도구로 남아 있습니다.

GOLEMS_REMODEL_DONE

## v3 시작: 프로젝트 이해

v2 작업 트리는 `e2f533b82cb8d7e2f943a6a566ce8687e07d3a55` stash에 보존했습니다. v1 기준은 `5b14879`입니다. 이전 지시와 v1/v2 생성기, 비교 렌더, 두 원본 OBJ/PNG, 현재 아이템 모델과 매니페스트, `BossPoses`를 확인했습니다. v2 콜로서스 비교는 1,830개 요소로 복셀화한 암석 표면이 잘게 끊기고 결정의 큰 면이 흐려졌습니다. `docs/reference/vanilla_style_ref_*.png`는 이 worktree에 없어 요청에 명시된 큰 상자와 손픽셀 방식으로 작업합니다.

- 프리즘 콜로서스 (13 파츠). 루트 `torso` → `chest` → `head`, `arm_l`/`arm_r`. 각 `arm_*` → `fore_*` → `fist_*`. 별도 루트 `leg_l`/`leg_r` → 각각 `shin_l`/`shin_r`. 원본은 높이 7블록, 최대 폭 6.25블록이며 어깨·왕관·등과 낮게 내려온 주먹에 결정이 집중됩니다.
- 크로노 센티넬 (10 파츠). 루트 `body` → `ring`, `clock`, `gear_l`, `gear_r`, `pendulum`, `arm_l`/`arm_r`. 각 `arm_*` → `blade_*`. 고정 다이얼은 `body`에 있고 `clock`이 바늘을 Z에 가까운 기존 축으로 회전시킵니다. `ring`은 포즈에서 Y축 회전, 양쪽 `gear_*`는 Z축 역방향 회전입니다. 원본은 높이 5블록이며 둥근 다이얼, 기울어진 두 링과 넓고 앞으로 숙인 잎 칼날이 특징입니다.
- 생성기는 파츠별 `assets/hobbang/items/boss/` 정의, `models/item/boss/` 요소 JSON, `textures/item/boss/` PNG를 기록합니다. 매니페스트의 이름·부모·월드 피벗·회전축·스케일을 유지하고 요소 수와 아틀라스 크기를 갱신합니다. `make_java_rig.py`가 매니페스트에서 `BossModels.java`를 생성합니다. `BossMotion`의 타이밍과 `BossPoses`의 부모 상대 변환을 여러 `item_display`에 적용합니다.
- 비교 렌더는 OBJ와 팩 JSON/PNG를 동일 카메라에서 읽습니다. 모션 렌더는 `BossPosePreview.java`가 실제 `BossPoses`에서 뽑은 TSV를 `render_boss_poses.py`로 읽습니다.
- 검증과 배포는 `python -B obj2mc/make_java_rig.py`, `python -B obj2mc/validate_pack.py`, `hobbang-enhance-paper/`에서 Gradle `clean jar`, `python -B obj2mc/make_zip.py resourcepack/hobbang-pack dist/golems-remodel/hobbang-pack-golems.zip` 순으로 진행합니다.

## v3 구현 및 검증

| 보스 | 파츠 | 큐보이드 v1 → v3 | PNG 아틀라스 분포 | 전체 아틀라스 픽셀 |
|---|---:|---:|---|---:|
| 프리즘 콜로서스 | 13 | 285 → **194** | 64² × 2, 128² × 10, 256² × 1 | 237,568 |
| 크로노 센티넬 | 10 | 262 → **366** | 16² × 1, 32² × 2, 64² × 3, 128² × 3, 256² × 1 | 129,280 |

- 콜로서스는 파츠당 암석 큐보이드 1–3개, 결정 42개, 가슴 코어 3개로 구성했습니다. 가장 큰 결정은 약 1.7블록이며, 작은 결정은 주먹과 등 군집에 배치했습니다. 16px/블록 암석면에 판마다 윗면의 밝은 턱과 어두운 하단을 그렸습니다. 자수정 균열은 네 옆면을 펼친 한 스트립에서 그려 모서리를 이어갑니다. 첫 렌더를 확인하고 결정 끝의 계단을 쐐기형 면으로 바꾸고 주먹을 앞으로 옮겼습니다.
- 센티넬은 황동 상자 면에 리벳, 눌러 찍은 판, 녹청과 청록 발광을 그렸습니다. 원형 다이얼은 46×46px로 제작해 시각 눈금 12개와 분 눈금을 담았습니다. 링 두 개를 22.5° 기울였고, 칼날은 끝으로 좁아지는 16px/블록 단면으로 만들었습니다. 측면 렌더를 보고 링 방향을 원본에 맞추고 진자 상단에 회전 피벗을 감싸는 축을 추가했습니다.
- 두 매니페스트의 이름, 부모, 피벗, 이동, 스케일, 회전축, 아이템 키가 v1과 일치합니다. `BossModels.java`의 해당 파츠 요소 수를 다시 생성했습니다. `BossPoses`에서 포즈를 직접 샘플링해 콜로서스의 보행·양손 들어올리기·내리찍기·프리즘 발사와 센티넬의 추적·시간 정지 준비·톱니 정지·칼날 휘두르기를 확인했습니다.

### 이미지

| 보스 | 원본 · v1 · v3 (정면 · 3/4 · 측면) | 확대 텍스처 | 4포즈 (3/4 · 측면) |
|---|---|---|---|
| 프리즘 콜로서스 | [비교](img/golems-remodel-v3/prism_colossus_compare.jpg) | [아틀라스 3배](img/golems-remodel-v3/prism_colossus_textures.png) | [모션](img/golems-remodel-v3/prism_colossus_motion.jpg) |
| 크로노 센티넬 | [비교](img/golems-remodel-v3/chrono_sentinel_compare.jpg) | [아틀라스 3배](img/golems-remodel-v3/chrono_sentinel_textures.png) | [모션](img/golems-remodel-v3/chrono_sentinel_motion.jpg) |

`work/models`에 원본 ZIP 두 개를 풀고, `git archive --format=zip -o work/v1-pack.zip 5b14879 resourcepack` 결과를 `work/v1`에 풀면 비교 렌더를 재현할 수 있습니다. 생성과 렌더는 worktree 루트에서 실행합니다.

```powershell
python -B obj2mc/make_golems_v3.py
python -B obj2mc/make_java_rig.py
python -B obj2mc/validate_pack.py
python -B obj2mc/golem_compare_v3.py
python -B obj2mc/render_boss_poses.py work/v3/poses.tsv docs/img/golems-remodel-v3
python -B obj2mc/make_zip.py resourcepack/hobbang-pack work/v3/hobbang-pack-golems.zip
```

포즈 TSV는 Java 21로 `BossPosePreview.java`를 빌드 결과 및 JOML과 함께 컴파일한 뒤 `com.hobbang.enhance.boss.BossPosePreview`의 표준 출력에서 두 보스 행을 저장한 것입니다.

- 팩 검증 통과. 아이템 80개, 팩 전체 요소 1,104개(회전 604개), 오류 0건, 경고 0건.
- Java 21과 Gradle 9.8.2에서 `--offline --no-daemon -p hobbang-enhance-paper clean jar` 성공. 기존 Paper 텔레포트 API 경고 6건과 Gradle deprecation 경고가 남았습니다.
- [배포 JAR](../dist/golems-remodel/hobbang-enhance-paper-1.5.2-golems.jar) 564,234바이트와 [팩 ZIP](../dist/golems-remodel/hobbang-pack-golems.zip) 337,289바이트를 갱신했습니다. 두 파일의 ZIP 무결성 및 [SHA1](../dist/golems-remodel/SHA1SUMS.txt)을 확인했고, 팩 ZIP을 소스 팩과 바이트 단위로 대조했습니다.

### 원본과의 차이

콜로서스의 결정은 축 하나로 회전 가능한 쐐기형이며 원본의 다각형 단면보다 넓고 평평합니다. 센티넬의 모래시계는 판에 그린 픽셀 아트이고, 다이얼·링·칼날의 외곽은 가까이 보면 1텍셀 단위로 꺾입니다. 기존 `ring`의 Y축 애니메이션을 사용해 기울어진 두 링이 함께 선회합니다. 검증 범위는 정적 팩, 빌드, 오프라인 렌더까지입니다.

GOLEMS_REMODEL_V3_DONE

## v3b

- 구현 커밋 **f841b7e**. 콜로서스는 194개 요소를 유지하면서 불규칙한 돌판, 면을 잇는 자수정 균열, 넓은 결정면을 적용했습니다. 가슴 코어는 원본 정면 렌더에서 잰 폭 약 1블록에 맞췄습니다.
- 센티넬은 몸통 두께를 0.81 → 1.21블록으로 늘리고 팔을 굵게 했습니다. 몸통과 어깨에 톱니 8개를 달고 황동판에 리벳, 이음선, 마모와 청록 도관을 추가했습니다. 366 → 470개 요소입니다.
- v3와 대조해 두 보스의 파츠 이름, 부모, 피벗, 회전축 및 기타 리그 메타데이터가 일치함을 확인했습니다. 팩 검증은 아이템 80개, 요소 1,208개, 회전 요소 636개, 오류·경고 0건입니다. Java 21에서 Gradle 9.8.2 오프라인 clean jar 빌드가 성공했습니다. 기존 Paper API 경고 6건과 Gradle deprecation 경고가 남았습니다.
- 비교는 원본 메시·v3·v3b를 동일한 정면, 3/4, 측면, 후면 3/4에서 렌더링했습니다. 지정된 vanilla_style_ref_*.png는 찾지 못해 원본 메시의 렌더와 요청에 적힌 픽셀 규칙을 사용했습니다.
- 콜로서스 [비교](img/golems-remodel-v3b/prism_colossus_compare.jpg) · [텍스처 3배](img/golems-remodel-v3b/prism_colossus_textures.png)
- 센티넬 [비교](img/golems-remodel-v3b/chrono_sentinel_compare.jpg) · [텍스처 3배](img/golems-remodel-v3b/chrono_sentinel_textures.png)

GOLEMS_REMODEL_V3B_DONE

## 보스 스킬 시각 FX

- 대장장이와 콜로서스의 슬램 및 웨이번 급강하에 실제 바닥 블록의 외형을 복제한 14틱 융기와 확장 충격파를 추가했습니다.
- 콜로서스 주포와 공용 빔에 회전하는 세 겹 광선, 파편, 발사점 충전 및 접촉점 섬광을 추가했습니다.
- 센티넬의 시간 정지·역행·페이즈에 독립 시계판을, 스윕에 바닥의 팔각 테두리와 12개 눈금을 추가했습니다.
- 네 보스의 페이즈 및 웨이번 폭풍에 준비 모션과 테마 방출을 연결하고, 나머지 공격과 사망 연출에 테마 입자를 추가했습니다. 상시 입자는 28블록 안에 플레이어가 있을 때 8틱 간격으로 표시합니다.
