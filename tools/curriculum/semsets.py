"""Semantic sets and the Minecraft-only rules for tag_lexicon.py.

semSet is the unit for "at most one new word per semantic set per session" (roadmap A4), so the sets are
broad enough to be useful and narrow enough that two items in one set can be confused:

  nouns   ore metal gem stone earth wood plant flower crop food animal mob person tool weapon armor light
          workstation furniture container building decor redstone material colour vehicle magic
          weather time sky place element body abstract number counter direction sound
  verbs   verb_motion verb_make verb_gather verb_consume verb_perceive verb_rest verb_social verb_trade verb_life
  adjectives  adjective_size adjective_feel
The everyday words of core-gap.csv are pinned by concept id in overrides.CONCEPTS, so adding them moved no older tag.

The head of a phrase decides (the last word that is not "블록"/"반"): 빨간색 양털 is a material, 참나무 계단 is a
building, 철 곡괭이 a tool. Exceptions are pinned by whole phrase (SEM_WHOLE) or by the English concept id
(SEM_ID, first match wins).
"""
from __future__ import annotations


def _group(spec: dict[str, str]) -> dict[str, str]:
    out = {}
    for sem, words in spec.items():
        for word in words.split():
            out[word] = sem
    return out


SEM_HEADS = _group({
    "ore": "광석 원석 유황 진사 석탄 잔해 숯",
    "metal": "철 금 구리 네더라이트",
    "gem": "다이아몬드 에메랄드 자수정 석영 청금석",
    "stone": "돌 조약돌 화강암 섬록암 안산암 응회암 현무암 심층암 흑암 사암 석재 흑요석 점적석 자석석 석",
    "earth": "흙 모래 자갈 점토 진흙 회백토 균사체",
    "wood": "나무 원목 통나무 판자 껍질",
    "plant": "풀 잔디 덩굴 이끼 고사리 덤불 관목 선인장 켈프 해초 버섯 대나무 묘목 잎 뿌리 수련잎 흘림잎 "
             "벌레잡이풀 나뭇잎 포자 균 균사 싹 산호 후렴초 봉오리 꼬투리 낙엽 식물",
    "flower": "꽃 장미 튤립 민들레 데이지 알리움 난초 라일락 모란 은방울꽃 수레국화 선애기별꽃 해바라기 진달래 "
              "눈망울꽃 횃불꽃 들꽃",
    "crop": "밀 당근 감자 호박 수박 비트 씨앗 콩 더미 열매",
    "food": "사과 빵 쿠키 케이크 고기 소고기 돼지고기 닭고기 양고기 우유 꿀 설탕 달걀 스튜 파이 연어 대구 후렴과",
    "animal": "소 돼지 양 닭 말 늑대 고양이 토끼 여우 벌 거북 오징어 거미 개구리 박쥐 염소 앵무새 낙타 라마 판다 "
              "북극곰 돌고래 당나귀 노새 복어 올챙이 아홀로틀 아르마딜로 오실롯 좀벌레 스니퍼 앵무조개 물고기 "
              "알 불우렁쉥이 곰",
    "mob": "좀비 스켈레톤 크리퍼 엔더맨 마녀 슬라임 가스트 블레이즈 위더 벡스 셜커 피글린 호글린 조글린 "
           "스트라이더 워든 브리즈 알레이 무시룸 보그드 스트레이 드라운드 허스크 팬텀 가디언 엔더마이트 크리킹 "
           "소환사 약탈자 변명자 파괴수 골렘 드래곤 큐브",
    "person": "주민 상인 친구",
    "tool": "곡괭이 도끼 삽 괭이 낚싯대 가위 솔 양동이 부시 나침반 시계 지도 안장 끈 피뢰침 폭죽 로켓",
    "weapon": "검 활 화살 창 철퇴 삼지창 쇠뇌 화염구 과녁",
    "armor": "투구 흉갑 레깅스 부츠 갑옷 방패 하네스",
    "light": "횃불 랜턴 초 모닥불 전구 조명 햇빛",
    "workstation": "제작대 제작기 화로 용광로 훈연기 모루 숫돌 베틀 양조기 부여대 절단기 작업대 독서대 가마솥 "
                   "퇴비통 형판",
    "furniture": "침대 책장 선반 방석 화분 액자 사다리",
    "container": "상자 통 병 그릇 주머니 꾸러미 유리병 꿀병",
    "building": "벽돌 계단 담장 울타리 기둥 문 다락문 창살 격자 사슬 유리 유리판 색유리 콘크리트 테라코타 타일 벽 "
                "건물 집 마을",
    "decor": "양탄자 현수막 표지판 도자기 머리 해골 동상 음반 무늬 왕관 조각 심장 보물 지도 만세",
    "redstone": "레드스톤 피스톤 호퍼 레버 버튼 압력판 관측기 발사기 공급기 중계기 레일 TNT 감지기 감지체 "
                "인스턴스",
    "material": "실 가죽 양털 깃털 뼈 화약 종이 막대기 인갑 껍데기 수지 덩어리 먹물 가루 거미줄 밀짚 숨결 끈끈이 "
                "콧바람 등딱지",
    "colour": "염료",
    "vehicle": "보트",
    "magic": "물약 토템 진주 신호기 수정 별 경험치 코어 촉매 전달체 눈물 마그마",
    "weather": "비 천둥 눈 바람",
    "time": "밤 낮",
    "sky": "하늘 해 달",
    "place": "숲 사막 해변 동굴 마을 평원 늪 산림 황무지 들판 언덕 봉우리 비탈 골짜기 고원 삼각주 섬 해안 바다 "
             "강 원시림 불모지 악지 목초지 정글 타이가 사바나 네더 엔드 폐광 피라미드 비탈 중지 고지 "
             "어둠",
    "element": "물 용암 얼음 불 눈덩이 거품",
    "abstract": "영원 창조 의식 세계 높이 시련",
    "verb_motion": "걷다 수영하다 올라가다",
    "verb_make": "만들다 짓다 요리하다",
    "verb_gather": "캐다 베다 파다 모으다 놓다 열다",
    "verb_consume": "먹다 마시다",
    "verb_perceive": "보다",
    "verb_rest": "자다",
    "verb_social": "싸우다 이야기하다",
})
# a few heads are only right as the last word of the phrase; the rest of the table covers compounds via suffixes
SEM_SUFFIX = {
    "알": "animal", "얼음": "element", "나무": "wood", "광석": "ore", "원석": "ore", "암": "stone", "석": "stone", "산호": "plant", "꽃": "flower",
    "벽돌": "building", "블록": "building",
}

SEM_WHOLE = {
    "먹물 주머니": "material", "발광 먹물 주머니": "material", "발광석 가루": "material", "벌집 조각": "material", "벌집 조각 블록": "material", "프리즈머린": "stone",
    "자수정 조각": "gem", "금 조각": "metal", "거미 눈": "material", "발효된 거미 눈": "magic", "엔더의 눈": "magic",
    "엔더 진주": "magic", "네더의 별": "magic", "경험치 병": "magic", "엔드 수정": "magic", "드래곤의 숨결": "magic",
    "불사의 토템": "magic", "바다의 심장": "magic", "마법이 부여된 책": "magic", "마법이 부여된 황금 사과": "magic",
    "시련 열쇠": "tool", "불길한 시련 열쇠": "tool", "황금 사과": "food", "황금 당근": "food", "구운 감자": "food",
    "독이 있는 감자": "food", "꿀병": "food", "익힌 대구": "food", "익힌 연어": "food", "익힌 양고기": "food",
    "달콤한 열매": "food", "발광 열매": "food", "코코아 콩": "crop", "건초 더미": "crop", "소리 블록": "redstone",
    "마그마 블록": "element", "눈 덮인 마을 지도": "tool", "눈": "weather", "뼈 블록": "material",
    "꿀 블록": "material", "버섯 블록": "plant", "빨간색 버섯 블록": "plant", "석탄 블록": "ore", "숯": "material",
    "화약": "material", "먹물": "material", "진흙 벽돌": "building", "문": "building",
    "경험치": "magic", "물약": "magic", "영혼 횃불": "light", "영혼 모닥불": "light", "영혼 랜턴": "light",
    "영혼 모래": "earth", "영혼 흙": "earth", "마그마": "element", "마그마 큐브": "mob", "눈 골렘": "mob",
    "철 골렘": "mob", "엔더 드래곤": "mob", "드래곤 알": "animal", "벌집": "material", "벌통": "material",
    "모닥불": "light", "초": "light", "벌": "animal", "가위": "tool", "솔": "tool", "시계": "tool", "나침반": "tool",
    "지도": "tool", "종": "decor", "별": "magic", "책": "material", "판자": "wood", "눈덩이": "element",
    "침대": "furniture", "철사 덫 갈고리": "redstone", "레드스톤 중계기": "redstone",
    "화살 제작대": "workstation", "가마솥": "workstation", "물 가마솥": "workstation", "조각된 책장": "furniture",
    "독서대": "workstation",
}

SEM_ID = [
    (r"pottery_sherd$", "decor"),
    (r"armor_trim_smithing_template$|smithing_template$", "decor"),
    (r"banner$|banner_pattern$", "decor"),
    (r"sign$|hanging_sign$", "decor"),
    (r"_head$|_skull$|_wall_head$", "decor"),
    (r"^gen_music_disc", "decor"),
    (r"_boat$", "vehicle"),
    (r"_bed$", "furniture"),
    (r"_shulker_box$|_chest$|_barrel$", "container"),
    (r"_candle_cake$", "food"),
    (r"_candle$", "light"),
    (r"_bundle$", "container"),
    (r"_cushion$", "furniture"),
    (r"_carpet$", "decor"),
    (r"_golem_statue$", "decor"),
    (r"_copper_(bulb|lantern)$|copper_torch$", "light"),
    (r"_lantern$", "light"),
    (r"_copper_(chain|bars|grate)$|_iron_chain$", "building"),
    (r"_(pressure_plate|button)$", "redstone"),
    (r"_(door|trapdoor|fence|fence_gate)$", "building"),
    (r"_leaves$|_sapling$|_roots$|_vines$|_propagule$|_azalea$", "plant"),
    (r"_(planks|log|wood|hyphae)$|stripped_", "wood"),
    (r"shelf$", "furniture"),
    (r"_map$", "tool"),
    (r"harness$", "armor"),
    (r"_(stairs|slab|wall)$", "building"),
    (r"_ore$", "ore"),
    (r"_(pickaxe|axe|shovel|hoe)$", "tool"),
    (r"_(sword|spear)$", "weapon"),
    (r"_(helmet|chestplate|leggings|boots)$|_armor$", "armor"),
    (r"coral", "plant"),
    (r"amethyst", "gem"),
    (r"nylium$", "earth"),
    (r"wart", "plant"),
    (r"^gen_end_rod$", "light"),
    (r"^gen_test_", "redstone"),
    (r"_glazed_terracotta$|_terracotta$|_concrete$|_concrete_powder$|_stained_glass(_pane)?$", "building"),
    (r"_wool(_slab|_stairs)?$", "material"),
    (r"_dye$", "colour"),
]

CATEGORY_SEM = {
    "BLOCKS": "building", "ITEMS": "material", "MISC": "abstract", "ENVIRONMENT": "place", "ANIMALS": "animal",
    "MATERIALS": "material", "ACTIONS": "verb_gather", "FOOD": "food", "TOOLS": "tool", "COMBAT": "mob",
    "BUILDING": "building",
}

# --------------------------------------------------------------------------- mcOnly
# modifiers that only make sense as Minecraft block variants (copper rust stages, waxing, block shapes)
MC_MODIFIERS = {"밀랍칠한", "녹슨", "산화된", "유광", "벌레 먹은", "껍질 벗긴"}
MC_PHRASES: set[str] = set()
# concept ids (English) of Minecraft-only game objects that are named with ordinary words
MC_ID = [
    r"waxed_|weathered_|exposed_|oxidized_",
    r"pottery_sherd$|smithing_template$|banner_pattern$",
    r"_glazed_terracotta$|_concrete_powder$",
    r"_shulker_box$|_bundle$|_harness$|_cushion$|_candle_cake$",
    r"^infested|_infested|^gen_infested",
    r"_chest_boat$",
    r"^gen_test_|_test_instance",
    r"golem|magma_cube|_head$|_skull$",
    r"copper_golem_statue$",
    r"_slab$",
    r"suspicious_|trial_|ominous_|creaking|sculk|froglight|pale_|resin|cinnabar|sulfur",
    r"^adv_item_(wind_charge|crafter|decorated_pot|totem|respawn|ender_eye|fire_charge|dragon_breath)",
    r"heavy_core|glow_berries|spore_blossom|trapped_chest|sticky_piston|tripwire_hook|detector_rail",
    r"daylight_detector|ender_chest|experience_bottle",
]
