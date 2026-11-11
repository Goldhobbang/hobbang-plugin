# HobbangEnhance 1.5.1 완료 보고

1.5.0에서 OBJ 자동 복셀·OBB 분할로 생성되어 표면 노이즈와 경계 복셀 파편이 남아 있던 **심연의 대장장이(`boss/abyssal_smith` + `boss/smith_hammer`)** 및 **보스 무기 4종(`weapon/smith_hammer`, `weapon/crystal_greatsword`, `weapon/stormcaller`, `weapon/chrono_dagger`)**을 레퍼런스([smith_style_ref.png](reference/smith_style_ref.png)) 스타일의 수작업 블록벤치식 직육면체 판금 모델과 16px/블록 노이즈 없는 픽셀 아트 텍스처로 재모델링했습니다. 프리즘 콜로서스·크로노 센티넬·템페스트 와이번은 변경하지 않았습니다.

## 1. 오프라인 렌더 및 비교 이미지 (`docs/img/1.5.1/`)

| 구분 | 설명 | 이미지 링크 |
|---|---|---|
| 레퍼런스 비교 | `smith_style_ref.png` · 1.5.0 (1,932 큐보이드) · 1.5.1 (126 큐보이드, 3/4 및 정면) 나란히 비교 | [abyssal_smith_ref_compare.jpg](img/1.5.1/abyssal_smith_ref_compare.jpg) |
| 대장장이 4방향 뷰 | 1.5.1 대장장이 + 망치 정면(`Front`) · 측면(`Side`) · 후면(`Back`) · 반측면(`3/4 Front`) | [abyssal_smith_views.jpg](img/1.5.1/abyssal_smith_views.jpg) |
| 대장장이 4개 포즈 | `BossPoses.smith` 런타임 관절 계산 기반 보행(`Stride`) · 망치 들기(`Hammer raised`) · 타격(`Impact`) · 회복(`Recovery`) 2각도 렌더 | [abyssal_smith_motion.jpg](img/1.5.1/abyssal_smith_motion.jpg) |
| 무기 4종 종합 비교 | 마그마 단조 망치 · 크리스탈 대검 · 템페스트 전투도끼 · 크로노 낫 원본 메쉬 · 1.5.0 · 1.5.1 동일 각도 비교 | [weapons_compare.jpg](img/1.5.1/weapons_compare.jpg) |
| 무기별 3각도 상세 비교 | 정면 · 3/4 · 측면 원본 메쉬 vs 1.5.0 vs 1.5.1 | [망치](img/1.5.1/smith_hammer_compare.jpg) · [대검](img/1.5.1/crystal_greatsword_compare.jpg) · [전투도끼](img/1.5.1/stormcaller_compare.jpg) · [낫](img/1.5.1/chrono_dagger_compare.jpg) |
| 무기 2D 아이콘 (16×16) | `gui` / `ground` / `fixed` / `on_shelf`용 신규 16×16 픽셀 스프라이트 시트 | [weapon_icons.png](img/1.5.1/weapon_icons.png) |

## 2. 큐보이드 수 및 텍스처 크기 비교 (1.5.0 → 1.5.1)

모든 모델은 16px/블록 텍셀 밀도(`resolution: 16`, `texels_per_voxel: 1`)로 통일했으며, 마인크래프트 요소 회전 제한(단일 축 `x`/`y`/`z`, `±45°`, `22.5°` 단위)과 최소 두께(`>= 0.0625` 블록, 약 1px)를 지킵니다.

| 모델 ID | 구분 | 큐보이드 1.5.0 → 1.5.1 | 아틀라스 크기 1.5.0 → 1.5.1 | 텍스처 px 합 (기본 프레임) 1.5.0 → 1.5.1 | 생성 스크립트 |
|---|---|---:|---|---:|---|
| `hobbang:boss/abyssal_smith` | 심연의 대장장이 본체 (15 파츠) | 1,856 → **114** | 파츠당 128²~512² → **128²~256²** | 1,179,648 → **344,064** | `obj2mc/make_smith.py` |
| `hobbang:boss/smith_hammer` | 보스 부착 망치 (`hammer`) | 76 → **12** | 256×256 → **256×256** (8프레임 용암 맥동) | 65,536 → **65,536** | `obj2mc/make_smith.py` |
| **대장장이 + 망치 합계** | **16 디스플레이 파츠** | **1,932 → 126 (-93.5%)** | — | **1,245,184 → 409,600 (-67.1%)** | — |
| `hobbang:weapon/smith_hammer` | 마그마 단조 망치 (3D 손 모델) | 13 → **12** | 128×128 → **128×128** (8프레임 용암 맥동) | 16,384 → **16,384** | `obj2mc/make_smith.py` |
| `hobbang:weapon/crystal_greatsword` | 크리스탈 대검 (3D 손 모델) | 30 → **23** | 128×128 → **128×128** | 16,384 → **16,384** | `obj2mc/make_weapon_models.py` |
| `hobbang:weapon/stormcaller` | 템페스트 전투도끼 (3D 손 모델) | 140 → **48** | 256×256 → **128×128** (8프레임 번개 점멸) | 65,536 → **16,384** | `obj2mc/make_weapon_models.py` |
| `hobbang:weapon/chrono_dagger` | 크로노 낫 (3D 손 모델) | 108 → **44** | 256×256 → **128×128** | 65,536 → **16,384** | `obj2mc/make_weapon_models.py` |

## 3. 대장장이 파츠별 큐보이드·텍스처·피벗 표

기존 1.5.0 리그(`BossModels.java`, `BossPoses.smith`, `SmithBoss.hammerHead()`)의 파츠 이름, 부모-자식 계층, 피벗 좌표를 그대로 유지해 Java 포즈 수식 변경 없이 동작합니다.

| 파츠 (`part`) | 부모 (`parent`) | 피벗 `pivot_blocks` `(x, y, z)` | 큐보이드 1.5.0 → 1.5.1 | 아틀라스 1.5.0 → 1.5.1 | 주요 갑주 구성 |
|---|---|---|---:|---|---|
| `torso` | `null` | `(0.0, 2.7, 0.0)` | 43 → **19** | 128² → 256² | 허리 벨트, 사선 금장 버클, 2열 룬 앞치마, A자형 외측 타셋, 후면 스커트 |
| `chest` | `torso` | `(0.0, 4.5, 0.0)` | 185 → **20** | 256² → 256² | V자형 흑요석 흉갑, 청록 발광 흉골 코어, 내측 어깨 칼라, 등판 용암·청록 척추 |
| `head` | `chest` | `(0.0, 7.2, 0.0)` | 181 → **13** | 256² → 128² | 뿔 장식 단조 투구, V자형 심연 청록 바이저, 측면 뿔 마운트 |
| `arm_l` | `chest` | `(2.0, 6.8, 0.0)` | 109 → **6** | 256² → 128² | 3단 층진 청동·흑철 견갑, 상완 갑주 |
| `fore_l` | `arm_l` | `(2.0278, 5.3, 0.0278)` | 191 → **6** | 256² → 128² | 사선 금장 커프 링 2열, 곡면 외측 방패형 전완 갑주(청록 발광 띠) |
| `hand_l` | `fore_l` | `(2.2917, 4.45, 0.2083)` | 53 → **5** | 128² → 128² | 왼손 건틀릿 및 외측 방패 하단 팁 |
| `arm_r` | `chest` | `(-2.0, 6.8, 0.0)` | 93 → **6** | 256² → 128² | 3단 층진 청동·흑철 견갑, 상완 갑주 |
| `fore_r` | `arm_r` | `(-2.0278, 5.3, 0.0278)` | 5 → **6** | 128² → 128² | 사선 금장 커프 링 2열, 곡면 외측 방패형 전완 갑주(용암 주황 발광 띠) |
| `hand_r` | `fore_r` | `(-2.2917, 4.45, 0.2083)` | 57 → **5** | 256² → 128² | 용암 건틀릿 및 망치 그립 |
| `leg_l` | `null` | `(0.9, 2.6, 0.0)` | 35 → **4** | 256² → 128² | 좌측 대퇴부 판금 |
| `shin_l` | `leg_l` | `(1.0833, 1.55, -0.0833)` | 5 → **5** | 128² → 128² | 무릎 보호대, 전면·외측 청록 발광 각반 |
| `foot_l` | `shin_l` | `(1.3056, 0.65, 0.1944)` | 54 → **5** | 256² → 128² | 청동 발등 판금 및 철제 부츠 (`y = 0.0` 접지) |
| `leg_r` | `null` | `(-0.9, 2.6, 0.0)` | 37 → **4** | 256² → 128² | 우측 대퇴부 판금 |
| `shin_r` | `leg_r` | `(-1.1389, 1.55, -0.0833)` | 447 → **5** | 512² → 128² | 무릎 보호대, 전면·외측 청록 발광 각반 (512² 복셀 파편 제거) |
| `foot_r` | `shin_r` | `(-1.3056, 0.65, 0.25)` | 361 → **5** | 512² → 128² | 청동 발등 판금 및 철제 부츠 (512² 복셀 파편 제거) |
| `hammer` | `hand_r` (부착) | `(-2.35, 3.5, 0.45)` (메쉬 `[0.3, 2.9, 0.0]`) | 76 → **12** | 256² → 256² | 양단 금장 띠, 용암 균열 링 2열, 정·측면 45° 마름모 장식, 타격면 용암 십자 룬, 묵직한 자루·폼멜 |

- **타격 정렬 검증**: `Impact` 포즈에서 `SmithBoss.hammerHead()`(`(-0.3, -2.3, 0)` 오프셋)는 보스 전방 `4.581`블록(충격파 중심 `4.60`블록과 일치), 망치 하단 최저점은 지면 위 약 `0.01`블록입니다.
- **무기 `display_context` 및 그립 유지**: `weapons/*_display.json`의 1·3인칭 손 그립 변환과 `minecraft:display_context` 분기(손 3D `*_hand.json`, `gui`/`ground`/`fixed`/`on_shelf` 2D `*_icon.json`)는 그대로 유지하고, `weapons/icons.py` 및 `weapons/rebuild_hands.py`로 2D 아이콘 4종과 `.png.mcmeta` 애니메이션(`lava_pulse`, `lightning_flicker`)을 갱신했습니다.

## 4. 빌드 및 리소스팩 검증 결과

- **Gradle 빌드**: JDK 21 / Gradle 9.8.2 `jar` 태스크 성공 (`BUILD SUCCESSFUL`).
- **리소스팩 검증 (`obj2mc/validate_pack.py`)**: `items 78, elements 7094, rotated elements 541, errors 0, warnings 0`.
- **산출물 및 SHA-1 ([dist/SHA1SUMS.txt](../dist/SHA1SUMS.txt))**:
  - [dist/hobbang-enhance-paper-1.5.1.jar](../dist/hobbang-enhance-paper-1.5.1.jar) (`564,061` bytes, SHA-1: `146b181b10d27d522c26638ff22617f4acc4dee0`)
  - [dist/hobbang-pack-1.5.1.zip](../dist/hobbang-pack-1.5.1.zip) (`3,026,902` bytes, SHA-1: `20056334bd1ac008cc8d2ffd34a88b7639e3bdf7`)

## 5. 남은 이슈 및 인게임 확인 항목

- 오프라인 렌더 및 리소스팩 정적 검증 기준 남은 오류는 없습니다.
- **인게임 확인 항목 (껌냥이 테스트)**:
  1. 심연의 대장장이 소환 시 판금 갑주 실루엣·발광 텍스처 시인성 및 보행·망치 타격·충격파 포즈 동기화 확인.
  2. 보스 무기 4종 인벤토리/바닥/액자 2D 아이콘 및 1·3인칭 손 3D 모델 그립·애니메이션(`smith_hammer` 용암 맥동, `stormcaller` 번개 점멸) 확인.
  3. 콜로서스(궤도 수정 경계 복셀 조각은 1.5.0 그대로 유지)·센티넬·와이번의 기존 동작 회귀 여부 확인.
