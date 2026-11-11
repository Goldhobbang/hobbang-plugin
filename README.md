# 홉방 강화 (hobbang-plugin)

Paper 1.21.11 플러그인 **HobbangEnhance** 1.4.4와 전용 리소스팩, obj2mc 변환 파이프라인의 소스 트리입니다.
바닐라 클라이언트로 접속할 수 있습니다. 커스텀 아이템 ID는 쓰지 않고, 바닐라 아이템에 PDC(`hobbang:id`)와 데이터 컴포넌트를 붙입니다.

이 폴더를 이미 쓰던 작업본 위에 덮어쓸 때 **`models-3d/`는 건드리지 마세요.** 원본 Meshy zip은 여기만 보관합니다. obj2mc가 읽던 `/workspace/mc/models` 풀린 메시는 이 핸드오프에 넣지 않았습니다.

커밋·푸시는 하지 않은 상태입니다. 비밀값·`.env`는 넣지 않았습니다.

## 폴더 구조

```
hobbang-plugin/
  README.md                 이 파일
  ARCHITECTURE.md           플러그인·팩·obj2mc 구조
  models-3d/                원본 Meshy zip (이미 이름을 정리한 보관본. 이 핸드오프가 만들거나 덮어쓰지 않음)
  hobbang-enhance-paper/    Paper 플러그인 소스 + Gradle wrapper (`build/`, `.gradle` 제외)
  resourcepack/
    hobbang-pack/           팩 소스 (pack format 75.0)
    manifests/              obj2mc가 쓴 파트·피벗·큐보이드 수 매니페스트
    hobbang-pack.zip        1.4.4 공식 팩 (결정적 zip)
    hobbang-pack.zip.sha1   위 zip의 SHA-1 (소문자 40자, BOM 없음)
  obj2mc/                   Meshy OBJ → 큐보이드 아이템 모델 파이프라인 (`.venv` 제외)
  weapons/                  든 무기 display JSON과 `make_weapons.py`
  docs/                     PROGRESS-1.4.x.md
  dist/                     미리 빌드한 `hobbang-enhance-paper-1.4.4.jar`
```

설계 메모의 구버전은 `hobbang-enhance-paper/DESIGN.md`에도 있습니다. 현재(1.4.4) 기준은 `ARCHITECTURE.md`를 보세요.

## 플러그인 빌드

필요: **JDK 21**, 인터넷(Paper API). Gradle은 wrapper(9.8.2)를 씁니다. `shadowJar`가 아니라 **`./gradlew jar`** 입니다.

Linux / macOS (이 소스를 박스가 빌드할 때 쓰던 것과 동일):

```bash
cd hobbang-enhance-paper
export JAVA_HOME=/path/to/jdk-21    # 박스에서는 /home/box/jdk/jdk-21.0.13+9
./gradlew jar
# 결과: build/libs/hobbang-enhance-paper-1.4.4.jar
```

Windows (PowerShell):

```powershell
cd hobbang-enhance-paper
$env:JAVA_HOME = "C:\Path\To\jdk-21"
.\gradlew.bat jar
```

1.4.4 미리 빌드된 jar:

- 경로: `dist/hobbang-enhance-paper-1.4.4.jar`
- 크기: 539762 bytes
- SHA-1: `814d17da95d5a1149e7cc999af7f4348f24e8d87`

## 리소스팩 zip + SHA-1

`obj2mc/make_zip.py`가 타임스탬프를 2026-01-01로 고정한 결정적 zip과 `.sha1`을 만듭니다.

```bash
cd obj2mc
python3 make_zip.py ../resourcepack/hobbang-pack ../resourcepack/hobbang-pack.zip
# 함께 쓰여짐: ../resourcepack/hobbang-pack.zip.sha1
```

1.4.4 공식 팩 (무기 facing / 도끼 chopping grip / 와이번 꼬리 overlap 0.30 반영, 2026-11-11 11:46 Asia/Seoul 재빌드):

- 파일: `resourcepack/hobbang-pack.zip`
- 크기: 7705622 bytes
- SHA-1: `eda4401fcb408ab27a49c6c9fd29aa723aac4945`

팩 소스에서 무기를 손본 뒤에는 `weapons/*.json`의 display를 `hobbang-pack` 안의 해당 `held.json` / `*_hand.json`과 맞춰 두고 zip을 다시 만드세요. 와이번·보스 메시를 다시 쓰려면 아래 obj2mc를 돌린 뒤 pack 소스가 갱신됐는지 확인하고 zip을 다시 만듭니다.

## 팩 배포 (`gh release create`)

리포: [Goldhobbang/hobbang-plugin](https://github.com/Goldhobbang/hobbang-plugin). 태그는 `pack-vX.Y.Z` 형식입니다.

```bash
gh release create pack-v1.4.4 ../resourcepack/hobbang-pack.zip \
  --repo Goldhobbang/hobbang-plugin \
  --title "pack-v1.4.4" \
  --notes "Hobbang pack 1.4.4 (held-weapon facing, stormcaller chopping grip, wyvern 0.30 tail overlap). sha1 eda4401fcb408ab27a49c6c9fd29aa723aac4945"
```

에셋 파일 이름을 `hobbang-pack.zip`으로 두면 다운로드 URL은 다음이 됩니다.

`https://github.com/Goldhobbang/hobbang-plugin/releases/download/pack-v1.4.4/hobbang-pack.zip`

게시한 뒤 SHA-1이 zip과 같은지 확인합니다.

```bash
curl -sI "https://github.com/Goldhobbang/hobbang-plugin/releases/download/pack-v1.4.4/hobbang-pack.zip"
curl -sL "https://github.com/Goldhobbang/hobbang-plugin/releases/download/pack-v1.4.4/hobbang-pack.zip" | sha1sum
# 기대값: eda4401fcb408ab27a49c6c9fd29aa723aac4945 , HTTP 200
```

## 홈 서버 설치

경로 (오타 `Deskktoptop`이 실제 폴더명입니다):

- Paper 서버: `C:\Users\diamo\Deskktoptop\Desktop\홉방마크\PaperServer`
- 플러그인: `C:\Users\diamo\Deskktoptop\Desktop\홉방마크\PaperServer\plugins`
- Prism 팩: `D:\PrismLauncher\instances\1.21.11\minecraft\resourcepacks\hobbang-pack.zip`

### server.properties

UTF-8, **BOM 없음**. 기존 파일을 열어 다음 두 줄만 맞춥니다.

```
resource-pack=https://github.com/Goldhobbang/hobbang-plugin/releases/download/pack-v1.4.4/hobbang-pack.zip
resource-pack-sha1=eda4401fcb408ab27a49c6c9fd29aa723aac4945
```

### 플러그인 jar

1. 지금 들어 있는 1.4.3 jar를 `C:\Users\diamo\hobbang-enhance-paper-1.4.3.jar.old`로 백업합니다.
2. `dist/hobbang-enhance-paper-1.4.4.jar`를 `plugins\hobbang-enhance-paper-1.4.4.jar`로 복사합니다.
3. `plugins` 안에 HobbangEnhance jar는 **하나만** 둡니다 (1.4.3과 1.4.4를 같이 두지 마세요).
4. 이 문서를 따른 설치만으로 **사용자 Paper 서버를 시작하지는 마세요.** 서버는 직접 켜면 됩니다. Java 21, Paper 1.21.11.

처음 기동 때 콘솔에 데이터팩 자동 로드와 `홉방 인첸트 20종 로드됨`이 보이면 정상입니다. 설정은 `plugins/HobbangEnhance/hobbang-enhance.json`.

### Prism

공식 `hobbang-pack.zip`을 위 Prism `resourcepacks` 경로에 같은 이름으로 복사합니다.

## obj2mc

Python 3 가상환경(`.venv`는 핸드오프에 없음):

```bash
cd obj2mc
python3 -m venv .venv
# numpy, pillow, scipy, trimesh, embreex
.venv/bin/pip install numpy pillow scipy trimesh embreex
.venv/bin/python obj2mc.py configs/tempest_wyvern.json --preview
```

설정은 `obj2mc/configs/<id>.json`. 출력은 `resourcepack/hobbang-pack/assets/hobbang/...`와 `resourcepack/manifests/`. 자세한 파이프라인은 `obj2mc/README.md`와 `ARCHITECTURE.md`.

와이번을 다시 돌린 뒤에는 필요하면 `make_java_rig.py`를 쓰고, Java 오프셋이 바뀌지 않게 피벗은 유지하세요. 무기 전체를 다시 쓰는 `make_weapons.py`는 함부로 돌리지 마세요 (display 값이 덮일 수 있음).

## models-3d ↔ obj2mc

`models-3d/`의 zip은 사용자가 받은 Meshy 내보내기를 이름만 정리한 원본입니다. **이 폴더를 복사·삭제·덮어쓰지 마세요.** 아래는 각 zip이 어떤 설정·매니페스트·파트 세트로 변환됐는지, 그리고 1.4.4 팩에 들어 있는 해상도·큐보이드 수·피벗입니다.

공통 기본값(각 config에 명시): `texels_per_voxel=2`, `color_knn=4`, `min_speck_voxels=6`, `per_part_speck_voxels=3`. 무기와 대장장이 망치는 `sample_offset_vox=0.25`. 좌표는 모델 앞이 **+Z**, `source_yaw_deg=0` (망치만 `source_rot`로 보정).

피벗은 두 종류입니다.

- **config pivot**: `obj2mc/configs/*.json`의 레이아웃 단위 (`layout_height` 기준). 파티션·조인트를 여기서 고칩니다.
- **pivot_blocks**: `resourcepack/manifests/*.json`의 블록 단위. Java 리그가 쓰는 값입니다. `pivot_blocks = config_pivot * (height_blocks / layout_height)`.

`item_display_scale`과 `model_units_per_block`도 매니페스트에 있습니다. 레스트 포즈는 `translation = pivot_blocks`, `left_rotation = identity`, `scale = item_display_scale`입니다 (item_display의 Y 180°를 모델이 미리 보정).

### 보스

| models-3d zip | config | manifest | 해상도 | height_blocks | layout_height | 총 큐보이드 | 파트 |
|---|---|---|---:|---:|---:|---:|---|
| `boss_abyssal-smith_body.zip` | `abyssal_smith.json` | `boss__abyssal_smith.json` | 12 | 9.0 | 9.0 | 2744 | head, arm_r, arm_l, leg_r, leg_l, torso |
| `boss_chrono-sentinel.zip` | `chrono_sentinel.json` | `boss__chrono_sentinel.json` | 24 | 5.0 | 2.8 | 3898 | ring, clock, arm_r, arm_l, body |
| `boss_prism-colossus_amethyst-golem.zip` | `prism_colossus.json` | `boss__prism_colossus.json` | 16 | 7.0 | 4.6 | 3749 | arm_r, arm_l, head, leg_r, leg_l, torso |
| `boss_tempest-wyvern.zip` | `tempest_wyvern.json` | `boss__tempest_wyvern.json` | 15 | 6.0 | 4.0 | 3811 | 23파트 (아래) |

`legacy_abyssal-smith_with-hammer_combined.zip`은 몸+망치가 한 메시에 들어 있는 예전 Meshy 내보내기입니다. 현재 파이프라인은 쓰지 않습니다. 몸은 `boss_abyssal-smith_body.zip`, 손에 든/보스 장착 망치는 `weapon_magma-forge-hammer.zip`을 씁니다.

#### 심연의 수습 대장장이 — config pivot = pivot_blocks (배율 1.0)

| part | parent | cuboids | pivot |
|---|---|---:|---|
| head | torso | 180 | `[0, 7.2, 0]` |
| arm_r | torso | 427 | `[-2.0, 6.8, 0]` |
| arm_l | torso | 445 | `[2.0, 6.8, 0]` |
| leg_r | — | 304 | `[-0.9, 2.6, 0]` |
| leg_l | — | 305 | `[0.9, 2.6, 0]` |
| torso | — | 1083 | `[0, 2.7, 0]` |

보스 장착 망치는 같은 Meshy 망치 zip에서 **별도** config로 뽑습니다. `smith_hammer.json` → `boss__smith_hammer.json`. 해상도 12, `height_blocks=layout_height=4.2`, `source_rot: [[x,-90],[y,90]]`, 큐보이드 259, 파츠 `hammer`, config/pivot_blocks `[0.3, 2.9, 0]`. 장착: `attach.boss=abyssal_smith`, `attach.part=hammer`, 손목 `attach.pivot_blocks=[-2.35, 3.5, 0.45]` (`arm_r` 자식). `item_display_scale=2.0`.

#### 크로노 센티넬 — 배율 5.0/2.8 ≈ 1.7857

| part | parent | cuboids | config pivot (layout) | pivot_blocks |
|---|---|---:|---|---|
| ring | body | 538 | `centroid` (spin) | `[0.0041, 4.2407, 0.1224]` |
| clock | body | 73 | `centroid` (spin) | `[0.0013, 3.1295, 0.5763]` |
| arm_r | body | 865 | `[-0.55, 2.05, 0]` | `[-0.982, 3.661, 0]` |
| arm_l | body | 861 | `[0.55, 2.05, 0]` | `[0.982, 3.661, 0]` |
| body | — | 1561 | `[0, 1.4, 0]` | `[0, 2.5, 0]` |

#### 프리즘 콜로서스 — 배율 7.0/4.6 ≈ 1.5217

| part | parent | cuboids | config pivot (layout) | pivot_blocks |
|---|---|---:|---|---|
| arm_r | torso | 958 | `[-1.3, 3.5, 0]` | `[-1.978, 5.326, 0]` |
| arm_l | torso | 955 | `[1.3, 3.5, 0]` | `[1.978, 5.326, 0]` |
| head | torso | 282 | `[0, 3.8, 0]` | `[0, 5.783, 0]` |
| leg_r | — | 337 | `[-0.55, 1.55, 0]` | `[-0.837, 2.359, 0]` |
| leg_l | — | 321 | `[0.55, 1.55, 0]` | `[0.837, 2.359, 0]` |
| torso | — | 896 | `[0, 1.55, 0]` | `[0, 2.359, 0]` |

#### 폭풍의 와이번 — 배율 6.0/4.0 = 1.5, 해상도 15, `item_display_scale` ≈ 2.3704, 총 3811

23파트. 1.4.4에서 꼬리 관절 틈을 줄이려고 **피벗은 그대로 두고** 각 꼬리 파츠 `regions.max.z`만 피벗보다 몸 쪽으로 약 0.30 레이아웃 단위 늘렸습니다 (`tail_4 -1.50`, `tail_3 -0.97`, `tail_2 -0.57`, `tail_1 -0.15`). 이전 설정 백업: `tempest_wyvern.v130.json`, `tempest_wyvern.v144-prepolish.json`, `tempest_wyvern.v144-overlap015.json`.

| part | parent | cuboids | config pivot (layout) | pivot_blocks |
|---|---|---:|---|---|
| foot_fl | shin_fl | 43 | `[0.4, 0.22, 1.55]` | `[0.60, 0.33, 2.325]` |
| foot_fr | shin_fr | 40 | `[-0.4, 0.22, 1.55]` | `[-0.60, 0.33, 2.325]` |
| foot_hl | shin_hl | 48 | `[0.4, 0.22, -0.1]` | `[0.60, 0.33, -0.15]` |
| foot_hr | shin_hr | 55 | `[-0.4, 0.22, -0.1]` | `[-0.60, 0.33, -0.15]` |
| shin_fl | thigh_fl | 46 | `[0.4, 0.48, 1.48]` | `[0.60, 0.72, 2.22]` |
| shin_fr | thigh_fr | 39 | `[-0.4, 0.48, 1.48]` | `[-0.60, 0.72, 2.22]` |
| shin_hl | thigh_hl | 41 | `[0.4, 0.48, -0.08]` | `[0.60, 0.72, -0.12]` |
| shin_hr | thigh_hr | 38 | `[-0.4, 0.48, -0.08]` | `[-0.60, 0.72, -0.12]` |
| thigh_fl | body | 84 | `[0.42, 0.92, 1.25]` | `[0.63, 1.38, 1.875]` |
| thigh_fr | body | 57 | `[-0.42, 0.92, 1.25]` | `[-0.63, 1.38, 1.875]` |
| thigh_hl | hips | 134 | `[0.42, 0.92, -0.05]` | `[0.63, 1.38, -0.075]` |
| thigh_hr | hips | 28 | `[-0.42, 0.92, -0.05]` | `[-0.63, 1.38, -0.075]` |
| head | neck_2 | 521 | `[0.0, 2.1, 1.42]` | `[0, 3.15, 2.13]` |
| neck_2 | neck_1 | 225 | `[0.0, 1.95, 0.9]` | `[0, 2.925, 1.35]` |
| neck_1 | body | 89 | `[0.0, 1.75, 0.4]` | `[0, 2.625, 0.60]` |
| wing_l | body | 373 | `[0.55, 2.0, 0.4]` | `[0.825, 3.0, 0.60]` |
| wing_r | body | 352 | `[-0.55, 2.0, 0.4]` | `[-0.825, 3.0, 0.60]` |
| tail_4 | tail_3 | 211 | `[0.0, 1.35, -1.8]` | `[0, 2.025, -2.7]` |
| tail_3 | tail_2 | 94 | `[0.0, 1.05, -1.25]` | `[0, 1.575, -1.875]` |
| tail_2 | tail_1 | 123 | `[0.0, 1.0, -0.85]` | `[0, 1.5, -1.275]` |
| tail_1 | hips | 155 | `[0.0, 1.1, -0.5]` | `[0, 1.65, -0.75]` |
| hips | body | 132 | `[0.0, 1.15, -0.2]` | `[0, 1.725, -0.30]` |
| body | — | 883 | `[0.0, 1.2, 0.15]` | `[0, 1.80, 0.225]` |

### 무기 (든 아이템, 파트는 모두 `held` 하나)

같은 망치 zip이 보스 장착 메시(`smith_hammer.json`)와 플레이어 든 메시(`weapon_smith_hammer.json`) 둘 다에 쓰입니다. 낫 Meshy 이름은 scythe이지만 플러그인/팩 id는 예전 그대로 `chrono_dagger`입니다.

| models-3d zip | config | manifest | 해상도 | height_blocks | cuboids | config / pivot_blocks | 비고 |
|---|---|---|---:|---:|---:|---|---|
| `weapon_magma-forge-hammer.zip` | `weapon_smith_hammer.json` | `weapon__smith_hammer.json` | 16 | 2.0 | 58 | `[-0.147, 0.62, 0]` | `source_rot: [[x,90],[y,90]]`, scale 1.0 |
| `weapon_tempest-battleaxe.zip` | `weapon_stormcaller.json` | `weapon__stormcaller.json` | 24 | 2.2 | 203 | `[0, 0.6, 0]` | 뇌운 전투도끼 |
| `weapon_crystal-greatsword.zip` | `weapon_crystal_greatsword.json` | `weapon__crystal_greatsword.json` | 32 | 2.3 | 49 | `[0, 0.52, 0]` | |
| `weapon_chrono-scythe.zip` | `weapon_chrono_dagger.json` | `weapon__chrono_dagger.json` | 24 | 2.5 | 176 | `[0.54, 0.55, 0]` | id는 chrono_dagger |

든 무기 손 변환(1.4.4, 크기 유지). 왼손(LH)은 Y·Z만 부호 반전. 핸들은 모델 +Y, 원점은 그립.

- 망치 / 수정 대검 / 크로노 낫: TP `rot[-18, 0, 0]` trans `[0, 1.0, 0.8]` scale `0.8`; FP `rot[-8, 0, 16]` trans `[2.0, 1.3, -2.0]` scale `0.48`
- 전투도끼(chopping grip): TP-R `rot[20, 90, 0]` trans `[0, 1.0, 0.8]` scale `0.8`; FP-R `rot[-8, -90, 16]` trans `[2.0, 1.3, -2.0]` scale `0.45`

소스: `weapons/stormcaller_display.json`, `weapons/hammer_display.json`, `weapons/crystal_greatsword_display.json`, `weapons/chrono_dagger_display.json`.

## 관리자 명령 (참고)

보스 id: `abyssal_smith`, `prism_colossus`, `tempest_wyvern`, `chrono_sentinel`.  
`/hobbang boss <id>` 는 바라보는 방향 8칸 앞에 소환.  
무기: `execute as <player> run hobbang give stormcaller` (콘솔에서 바로 `give`하면 "플레이어만 사용할 수 있습니다").
