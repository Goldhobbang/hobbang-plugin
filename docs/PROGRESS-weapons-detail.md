# 무기 디테일 v2

구현 커밋은 02d6851입니다. aa84e12의 상세 모델과 실제 손 모델은 지오메트리가 달랐으며, 손 모델의 UV가 상세 모델용 아틀라스를 가리켜 일부 면이 어긋났습니다. 각각의 지오메트리를 보존하고 별도 아틀라스에 맞춰 UV를 배치했습니다.

| 무기 | 상세 모델 / 손 모델 | 큐보이드 상한 | 아틀라스 | 이미지 |
|---|---:|---:|---|---|
| 폭풍의 도끼 | 333 / 54 | 500 | 128×128 | [상세 비교](img/weapons-detail-v2/stormcaller_compare.jpg) · [손 모델 비교](img/weapons-detail-v2/stormcaller_hand_compare.jpg) · [확대 텍스처](img/weapons-detail-v2/stormcaller_textures.png) |
| 자수정 대검 | 151 / 29 | 300 | 64×64 | [상세 비교](img/weapons-detail-v2/crystal_greatsword_compare.jpg) · [손 모델 비교](img/weapons-detail-v2/crystal_greatsword_hand_compare.jpg) · [확대 텍스처](img/weapons-detail-v2/crystal_greatsword_textures.png) |
| 크로노 낫 | 273 / 46 | 350 | 128×128 | [상세 비교](img/weapons-detail-v2/chrono_dagger_compare.jpg) · [손 모델 비교](img/weapons-detail-v2/chrono_dagger_hand_compare.jpg) · [확대 텍스처](img/weapons-detail-v2/chrono_dagger_textures.png) |

- 도끼는 날개 막을 가로지르는 번개와 어두운 금속 뼈대, 용의 비늘·안광·뿔, 감긴 손잡이를 그렸습니다.
- 대검은 큰 결정면, 라벤더 에너지 심, 어두운 금속 가드, 보석 반사광, 감긴 그립을 그렸습니다.
- 낫은 금빛 안쪽 균열, 청록 룬, 황동 리벳, 시계 숫자·눈금·바늘, 자루 마디와 톱니를 그렸습니다.
- 원본 팔레트와 블록당 16텍셀을 기본으로 삼았습니다. 작은 장식과 문자판, 좁은 균열·룬 채널은 픽셀 모양을 유지하도록 국소적으로 텍셀 밀도를 높였습니다.

팩 검증은 아이템 81개, 요소 7,735개, 회전 요소 904개, 오류·경고 0건입니다. 상세·손 모델의 위치, 크기, 회전과 display 값, display_context 아이콘 분기, 아이콘 이미지와 점멸 메타데이터를 이전 커밋과 대조했습니다. 새 모델의 모든 UV가 불투명한 텍셀을 가리키는 것도 확인했습니다.

재생성은 python -B obj2mc/make_weapon_models.py로 실행합니다. 비교 렌더는 aa84e12의 resourcepack을 work/detail-v1에 풀고 원본 OBJ와 텍스처를 work/models에 준비한 뒤 python -B obj2mc/render_weapons_detail.py로 실행합니다.

WEAPONS_DETAIL_V2_DONE
