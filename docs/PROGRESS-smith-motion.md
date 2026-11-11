# 대장장이 리모델 · 버그 · 모션 진행 보고

브랜치 `feat/colossus-sentinel-remodel`. push·merge·서버 실행은 하지 않았습니다.

## 커밋

| 커밋 | 내용 |
|---|---|
| `2b0b5dc` | 재귀 언로드 수정, `bossModels=auto` 폴백 로그 |
| `8c360ad` | 대장장이: 원본 메시 분석 도구와 메시 기반 박스 모델(이후 v3로 대체) |
| `ee04da8` | 대장장이 v3: 해골·겹갑주·내부 파란 에너지 |
| (이 문서 커밋) | 타격 오버슛/반동, 팩 zip, 이 문서 |

## 0. 버그

- **재귀 언로드**: `DisplayBoss.unload`에 `unloading` 가드를 넣었고, `discardParts`는 파트 목록을 먼저 비운 뒤 유효한 디스플레이만 제거합니다. `BossManager.onRemove`는 제거 콜백 안에서 파트를 지우지 않고 다음 틱에 `unload`합니다. "prevented from being removed" 경고의 원인이 이 콜백 안 제거였습니다.
- **`modelsEnabled()`**: `auto`인데 `resource-pack`이 비어 블록 모델로 떨어질 때 이유를 로그에 한 번 남깁니다. `hobbang-enhance-paper/README.md`에 "로컬 팩 테스트 시 on"을 적었습니다.

## 1. 심연 대장장이 v3

생성기 `obj2mc/make_smith_v3.py`, 분석 `obj2mc/smith_analyze.py`, 비교 `obj2mc/smith_compare.py`.

- 방식: 파트마다 안쪽에 발광하는 에너지 박스를 두고, 그 바깥을 22.5°/45°로 기울인 갑주 판이 틈을 남기며 감쌉니다. 흉골 V 틈(보라 심장), 갈비뼈 사이, 3단 견갑, 팔꿈치·무릎 보호대, 눈구멍에서 안쪽 빛이 보입니다.
- 머리는 해골입니다: 두개골 2단, 돌출 눈썹뼈, 코뼈, 기운 광대뼈, 윗니, 벌어진 아래턱, 뿔 결정 3개.
- 비율은 원본 메시의 파트별 bbox(`MESH` 표)를 따릅니다. 파트 이름·부모·관절 피벗은 그대로라 `BossPoses.smith`를 수정하지 않았습니다. `BossModels.java`는 `make_java_rig.py`로 재생성했습니다.
- 큐보이드: 109 → **170** (몸 148 + 해머 22). 손에 든 무기 모델(`weapon/smith_hammer`)은 건드리지 않았습니다.

| 항목 | 원본 메시 | v3 모델 |
|---|---|---|
| bbox 폭 / 높이 / 깊이 | 6.03 / 9.00 / 3.00 | 5.95 / 9.20 / 2.61 |
| 실루엣 IoU 정면 | – | 0.737 (면적비 0.876) |
| 실루엣 IoU 측면 | – | 0.802 (면적비 0.958) |
| 실루엣 IoU 3/4 | – | 0.853 (면적비 1.081) |

높이 +0.20은 정수리 결정, 깊이 −0.39는 발끝·등 돌출을 판 두께로 줄인 차이입니다. 정면 IoU가 낮은 것은 갑주 틈과 원본의 울퉁불퉁한 암석 외곽 때문입니다.

이미지(`docs/img/smith-remodel/`): [원본·이전·v3 비교](img/smith-remodel/abyssal_smith_compare.jpg) · [머리·가슴 확대](img/smith-remodel/abyssal_smith_closeup.jpg) · [텍스처](img/smith-remodel/abyssal_smith_textures.png) · [모션](img/smith-remodel/abyssal_smith_motion.jpg) · [원본 몸](img/smith-remodel/original_body.jpg) · [원본 해머](img/smith-remodel/original_hammer.jpg)

## 2. 모션

기존 코드에 이미 있던 것: 예비동작(prepare)·타격(release) 프레임과 smoothstep 이징, 타격 직전 4틱 이내의 빠른 전환, 걷기 체중 이동과 호흡, `Frame.blend`를 통한 패턴 간 블렌딩, `PHASE` 전용 모션.

이번에 바꾼 것은 한 가지입니다.

| 대상 | 전 | 후 |
|---|---|---|
| 모든 보스의 모든 타격(`BossMotion.frame`) | 타격 틱에 release = 1.0에서 그대로 감쇠 | 타격 틱에 1.16까지 넘어갔다가 약 −0.05 반동 후 8틱 안에 안정(`recoil`) |
| `Frame.blend` | release 합이 1을 넘으면 정규화되어 오버슛이 사라짐 | 오버슛을 보존 |

보스별 시그니처 연출, 텔레그래프 보강, 페이즈 전환 추가 모션은 **하지 않았습니다**.

디스플레이 수와 갱신 빈도(코드 기준, 인게임 미측정):

| 보스 | 모델 파트(item_display) | 틱당 변환 갱신 상한 |
|---|---:|---:|
| 심연 대장장이 | 16 (몸 15 + 해머) | 5.3 |
| 프리즘 콜로서스 | 13 | 4.3 |
| 크로노 센티넬 | 10 | 3.3 |
| 템페스트 와이번 | 36 | 12 |

`ANIM_INTERVAL = 3`틱마다 포즈를 계산하고 보간 3틱으로 잇습니다. 직전 변환과 같은 파트는 패킷을 보내지 않습니다. 보스별 추가 디스플레이(궤도 수정, 라벨 등)는 위 수에 넣지 않았습니다.

모션 시트: `docs/img/motion/*_motion.jpg`.

## 3. 소환대 기준 소환 연출

**시작하지 않았습니다.**

## 배포물 (`dist/smith-remodel/`, 릴리스하지 않음)

- `hobbang-pack.zip` 324,958 bytes, sha1 `01c82ad52111835306b7b85d5bff02fbaaf9cee0`. 경로는 모두 `/`, zip 무결성 확인.
- `hobbang-enhance-paper-1.5.2.jar`, sha1 `9f0753ad774f7215833d1ecbcd200d0917b2706d`.
- `validate_pack.py`: items 80, elements 1,269, errors 0, warnings 0. Gradle `jar` 성공.

## 인게임 체크리스트

- 청크 언로드·`/kill`·서버 종료 때 "prevented from being removed" 경고와 스택 오버플로가 사라졌는지.
- 언로드 후 다시 로드했을 때 파트가 중복으로 남지 않는지.
- 대장장이: 기운 판의 회전 방향, 눈구멍·갈비 틈의 발광이 실제 조명에서 보이는지, 해머가 손에 맞게 잡히는지.
- 타격 오버슛이 과하지 않은지(대장장이 내려찍기, 콜로서스 슬램, 센티넬 휘두르기).
- `/mspt`: 대장장이 큐보이드가 109 → 170으로 늘었습니다.

## 남은 일

- 2번의 나머지(시그니처, 텔레그래프, 페이즈 전환)와 3번 전체.
- `obj2mc/source/`(원본 OBJ 12MB)는 커밋하지 않았습니다. configs는 이 경로를 가리킵니다.
- 골렘 v2는 다른 세션의 v3/v3b로 대체되어 중단했습니다.
