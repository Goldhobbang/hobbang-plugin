package com.hobbang.enhance.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.WrittenBookContent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 가이드북 (Fabric v1.5 장인의 기록 - 스토리 기반) 포팅.
 * - 장별 구성 + 클릭 가능한 목차, 글자는 책 기본 검은색, 제목만 굵게
 * - 본문 쪽마다 13번째 줄에 클릭 가능한 "← 목차"
 */
@SuppressWarnings("UnstableApiUsage")
public final class Guide {
	private Guide() {}

	private static final String B = "\u00a7l", R = "\u00a7r";
	private static String h(String t) { return B + t + R + "\n"; }

	private record Chapter(String title, String... pages) {}

	private static final Chapter[] CHAPTERS = {
		new Chapter("전설",
			h("1. 전설") + "네더라이트가 끝이라\n믿지 않은 대장장이가\n있었다. 그는 쇠를\n더 깊이 두드리는 법을\n찾았고, 그것을\n강화라 불렀다.\n\n망치가 빗나갈 때마다\n돌에 금이 갔다.\n그 금이 굳은 것이\n지금의 강화석이다.",
			h("전설 (2)") + "+10의 문은 무거웠다.\n그는 빛을 머금은\n돌을 빚어 문을 열고,\n스스로 네 개의\n시련을 세웠다.\n\n첫째, 그 자신.\n 심연의 대장장이.\n둘째, 완벽한 형태.\n 프리즘 콜로서스.",
			h("전설 (3)") + "셋째, 망치 대신 부린\n폭풍. 폭풍의 와이번.\n넷째, 실패를 되돌리려\n만든 시간의 파수꾼.\n 크로노 센티넬.\n\n장인은 시련을 다\n넘지 못하고 심연에\n남았다. 남은 것은 이\n기록과, 그의 모루에\n아직 응답하는 강화대."),
		new Chapter("기초: 강화석과 강화대",
			h("2. 기초: 강화석") + "실패의 금이 굳은 돌.\n+0에서 +9까지의\n강화 재료다.\n\n· Y32 아래 돌·심층암을\n 캐다 보면 가끔\n· 적대 몹 3%\n· 다이아·에메랄드\n 광석을 캘 때 10%\n· 낚시 (보물)",
			h("강화석 (계속)") + "· 사막 사원 고고학\n· 던전, 폐광, 사원,\n 난파선, 보물 상자\n· 도구·무기 대장장이\n 주민과의 거래\n\n흔한 돌이지만,\n모든 강화는 여기서\n시작된다.",
			h("빛나는 강화석") + "장인이 +10의 문을\n열려고 빚은 돌.\n+10에서 +15까지 쓴다.\n\n· 조합: 강화석 8로\n 다이아몬드 1을 감싼다\n· 심층암에서 강화석이\n 나올 때 3%\n· 피글린 야수 15%\n· 바닐라 보스 처치",
			h("빛나는 강화석 (계속)") + "· 요새, 대저택,\n 네더 요새, 보루,\n 고대 도시, 엔드 시티\n 의 상자\n· 도구 대장장이 달인\n (32 에메랄드)",
			h("강화대") + "장인의 모루에 아직\n응답하는 작업대.\n모든 강화는 이 위에서\n이루어진다.\n\n조합\n 위: 강화석 3\n 가운데: 모루\n 아래: 흑요석 3",
			h("강화대 다루기") + "우클릭 -> 강화 창\n장비 칸에 장비를,\n보조 칸에 부적·\n주문서를 넣고 모루를\n누른다. 창에 성공·\n유지·하락·파괴 확률이\n보인다.\n장비 들고 좌클릭\n -> 확률 미리보기\n웅크리고 좌클릭\n -> 강화대 부수기"),
		new Chapter("강화의 법칙",
			h("3. 강화의 법칙") + "쇠는 쉽게 허락하지\n않는다.\n\n+0∼4: 실패해도\n 그대로 남는다.\n+5∼8: 실패하면\n 단계가 내려갈 수\n 있다.\n+9∼15: 실패하면\n 장비가 부서질 수\n 있다.",
			h("성공 확률") + "+0->1    100%\n+4->5     80%\n+8->9     40%\n+10->11   30%\n+14->15   10%\n\n/hobbang rates 로\n전체 표를 볼 수 있다.",
			h("장인의 기운") + "실패한 망치질은\n헛되지 않다. 실패할\n때마다 장인의 기운이\n쌓이고, 100%가 되면\n다음 강화는 반드시\n성공한다.\n\n장인을 돕는 부적과\n주문서도 전해진다.\n강화 창의 보조 칸에\n넣은 것만 쓰인다.",
			h("재료와 대가") + "+0∼9: 강화석 1∼3개\n+10∼15: 빛나는\n 강화석 1∼3개\n\n경험치 레벨도 함께\n바쳐야 한다.\n(1레벨 ∼ 12레벨)",
			h("강화의 결실") + "무기: 공격력 +7.5까지\n (+15), +10부터 공속\n방어구: 방어력·강도\n도구: 채굴 속도\n\n+5, +10, +15에는\n종류마다 특별한\n효과가 붙는다.\n(장비 설명에 ◆ 표시)\n+10 이상 성공은\n서버 전체에 울린다.",
			h("마일스톤 (1)") + "흉갑: 최대 체력\n +2 / +6 / +10\n투구: 산소 보너스\n +1 / +3 / +9,\n +15 물속 채굴 정상\n각반: 이동 속도\n +3% / +6% / +10%\n신발: 안전 낙하\n +1/+2/+4칸, 낙하\n 피해 −10/20/35%",
			h("마일스톤 (2)") + "검·창: +5 휩쓸기\n +25%, 출혈 20%\n+10 관통 15%(피해\n 25% 추가, 방어 무시)\n+15 장인의 일격:\n 풀차지 공격 때 주변\n 적에게 35% (3초)\n도구: 내구도 보존\n 10/25/40%,\n +15 파괴 속도 +10%",
			h("마일스톤 (3)") + "철퇴: 안전 낙하 +3,\n 낙하 피해 −50%/없음\n삼지창: 수영 속도\n +33/66/100%, 산소\n활·쇠뇌: 화살 보존\n 20/35/50%\n방패: 밀려남 저항,\n +10부터 발사체 반사\n보스 무기: 단계마다\n 스킬 피해·범위·쿨↓"),
		new Chapter("장인의 인첸트",
			h("4. 장인의 인첸트") + "그는 쇠뿐 아니라\n마법에도 손을 댔다.\n\n흡혈: 피해 일부 회복\n뇌격: 확률 낙뢰\n서리: 적 구속\n영혼 수확: 처치 시\n 체력과 강화석\n생명력: 최대 체력\n질풍: 이동 속도",
			h("인첸트 (계속)") + "폭렬: 폭발 화살\n광역 채굴: 3x3 채굴\n벌목꾼: 나무 통째로\n강화 친화: 성공률↑\n깨짐의 저주: 파괴율↑\n\n웅크리면 광역 채굴과\n벌목꾼이 꺼진다.",
			h("새 인첸트") + "맹독: 적 중독\n급소: 5%/Lv 확률로\n 피해 1.5배\n사거리: 닿는 거리\n +0.5칸/Lv\n짓밟기: 낙하 피해의\n 25%/Lv를 주변 적에게\n\n인첸트 테이블에서도\n나온다.",
			h("숨은 인첸트") + "구조물 상자에서만:\n염동력, 자동 제련,\n생명 수선, 뇌격,\n폭렬, 광역 채굴,\n벌목꾼, 강화 친화\n\n테이블·거래·낚시로는\n안 나온다. 요새 서재\n25%, 대저택·고대 도시\n18%, 던전 10% 등.",
			h("인첸트 테이블") + "장인은 마법의 값을\n깎았다.\n\n테이블 비용은 원래의\n1/3, 최대 10레벨.\n실제로 드는 레벨은\n1∼3. 10레벨이면\n바닐라 30레벨과 같은\n인첸트가 나온다.\n모루도 1/3, 최대 10.\n'너무 비쌈'은 없다.",
			h("상위 인첸트") + "상위 날카로움 I∼III\n 공격력 +4/+5/+6\n (날카로움 V = +3)\n상위 보호 I∼III\n 한 칸 보호 5/6/7\n (보호 IV = 4)\n\n바닐라와 같이 붙지만\n합산되지 않고 더\n높은 쪽만 적용된다.",
			h("상위 인첸트 (계속)") + "장인도 몇 번 보지\n못한 전설의 책.\n\n고대 도시, 엔드 시티,\n보루 보물, 불길한\n금고: 2% 안팎\n대저택, 요새 서재: 1%\n네 시련의 보스: 5%\n\n인첸트 테이블과\n거래로는 얻을 수 없다."),
		new Chapter("네 개의 시련",
			h("5. 네 개의 시련") + "v1.4.0부터 시련은\n정해진 제단에서만\n깨어난다. 핵을 들고\n제단을 우클릭하면\n5초 의식 뒤 소환.\n그가 세운 순서대로:\n1) 심연의 대장장이\n2) 프리즘 콜로서스\n3) 폭풍의 와이번\n4) 크로노 센티넬",
			h("시련의 장소 (1)") + "심연의 대장간\n 네더 현무암 삼각주.\n 거대한 대장간의\n 한가운데 용광로가\n 제단이다.\n프리즘 정동\n 오버월드 지하,\n 무성한 동굴 속의\n 자수정 돔.\n 신호기가 제단.",
			h("시련의 장소 (2)") + "크로노 신전\n 오버월드 지상\n (평원·초원·사바나·\n 사막·숲 등)의 원형\n 시계 광장.\n 마법부여대가 제단.\n\n구조물은 새로 생기는\n지역에만 나타난다.\n소환대로 찾자.",
			h("폭풍 제단") + "와이번은 어디서나,\n천둥번개가 칠 때.\n(위에서 본 5×5)\n W · · · W\n · · X · ·\n · X C X ·\n · · X · ·\n W · · · W\nC: 구리 블록+피뢰침\nX: 깎인 구리\nW: 심층암 담장 2단",
			h("폭풍 의식") + "재료: 구리 블록 1,\n깎인 구리 4, 피뢰침 1,\n조약돌 심층암 담장 8\n(구리 산화 무관)\n\n폭풍의 핵을 들고\n피뢰침을 우클릭하면\n하늘에 발판 투기장이\n솟는다. 싸움이 끝나면\n무너져 원래대로.",
			h("투기장") + "보스전 중에는 투기장\n안에 블록을 놓거나\n부술 수 없다. 기둥\n쌓기·땅굴 꼼수는\n통하지 않는다.\n보스가 밖으로 나가면\n제자리로 돌아간다.\n\n제단 블록은 부서지지\n않는다.",
			h("소환대") + "스컬크 감지체 모양의\n보스 안내판. 누르면\n제단 구조물의 방향과\n거리를 알려 준다.\n(소환은 하지 않음)\n조합\n 위: 메아리 조각,\n 빛나는 강화석, 메아리\n 가운데: 강화석,\n 스컬크 감지체, 강화석\n 아래: 스컬크 3",
			h("심연의 대장장이") + "첫째 시련. 심연에\n남은 장인 그 자신.\n\n1페: 망치 내려찍기와\n 불씨\n2페: 수습생을 부르고\n 용암 파동을 일으킴\n (점프로 피하기)\n3페: 광폭화, 낙뢰\n (바닥 표시 피하기)",
			h("프리즘 콜로서스") + "둘째 시련. 장인이\n꿈꾼 완벽한 형태.\n\n1페: 내려찍기\n (빨간 원), 가시줄\n2페: 프리즘 광선\n (옆으로 피하기)\n3페: 수정비\n (분홍 원 피하기)",
			h("폭풍의 와이번") + "셋째 시련. 망치 대신\n부린 폭풍. 하늘을\n도니 활을 챙겨라.\n\n1페: 폭풍 구슬, 돌풍,\n 급강하 뒤 기절\n (이때 공격)\n2페: 토네이도 추격\n3페: 낙뢰 폭풍\n (노란 원 피하기)",
			h("크로노 센티넬") + "넷째 시련. 실패를\n되돌리려 만든 시간의\n파수꾼.\n\n1페: 시계침 회전\n (점프로 피하기)\n2페: 시간 정지 3·2·1\n -> 보스를 등지기\n3페: 시간 역행\n 8초 안에 80 피해를\n 주면 막을 수 있다",
			h("새 기술 (1)") + "모든 공격은 예고가\n먼저 보인다.\n대장장이: 화염 나선\n (팔 사이로), 운석\n 단조(붉은 원), 3페\n 연속 충격파(점프)\n콜로서스: 수정 충격파\n (점프), 십자 광선\n (예고선), 3페 중력장\n (원 밖으로 달리기)",
			h("새 기술 (2)") + "와이번: 번개 브레스\n (예고선), 착지 때\n 폭풍 고리(점프),\n 2페 소용돌이\n센티넬: 시간 고리\n (점프), 2페 잔상\n 폭발(계속 움직이기),\n 3페 자정의 칼날\n\n페이즈가 바뀌면\n충격파가 밀어낸다.",
			h("시련의 보상") + "보스가 쓰러지면\n보상이 등급 색으로\n빛난다(벽 너머로도\n보임). 사라지지 않고\n불에도 타지 않는다.\n\n금색: 보스 무기,\n 상위 인첸트 책\n보라: 에픽, 하늘: 레어\n노랑: 언커먼",
			h("보스 무기") + "망치: 지진(반경 5)\n수정 대검: 수정 가시\n뇌운 전투도끼: 돌진 후\n 낙뢰 폭발 (도끼라서\n 방패를 깬다)\n크로노 낫: 휩쓸기,\n 사거리 +1, 우클릭은\n 전방 낫 베기 + 반경 8\n 시간 정지\n\n예전 뇌운검·크로노\n단검은 접속하면 새\n모양으로 바뀐다."),
		new Chapter("맺음말",
			h("6. 맺음말") + "장인은 끝내 네 시련을\n다 넘지 못했다.\n그러나 강화대는 아직\n그의 모루에 응답한다.\n\n그 망치질을 이어 갈\n이가 너이기를.\n\n즐거운 강화를.\n- 홉방"),
		new Chapter("명령어",
			h("7. 명령어") + "/hobbang help\n/hobbang rates\n/hobbang info\n/hobbang guide\n/hobbang storm\n\n관리자(OP) 전용:\nset, kit, kit2, boss,\nstructure, displays,\nselftest, reload, book"),
	};

	static final String TITLE = h("홉방 강화 가이드") + "\n심연으로 사라진\n한 대장장이의\n기록이 여기 있다.\n\n그가 남긴 망치질을\n이어 갈 이에게.\n\n다음 쪽: 목차\n(제목을 누르면\n그 장으로 이동)\n\nPaper 1.4";

	
	public static final int TOC_PAGE = 2;
	public static final int MAX_BODY_LINES = 12, LINK_LINE = 13;
	public static final String BACK = "← 목차";
	public static final List<String> PAGES = new ArrayList<>();

	/** "§l…§r" 표기를 굵은 글씨 컴포넌트로 변환 (색 지정 없음 = 검은색) */
	private static Component styled(String src) {
		Component root = Component.empty();
		boolean bold = false;
		for (String part : src.split("(?=\u00a7[lr])|(?<=\u00a7[lr])")) {
			if (part.equals(B)) { bold = true; continue; }
			if (part.equals(R)) { bold = false; continue; }
			if (part.isEmpty()) continue;
			root = root.append(Component.text(part).decoration(TextDecoration.BOLD, bold));
		}
		return root;
	}

	public static WrittenBookContent content() {
		WrittenBookContent.Builder b = WrittenBookContent.writtenBookContent("홉방 강화 가이드", "홉방").resolved(true);
		PAGES.clear();
		b.addPage(styled(TITLE));
		PAGES.add(TITLE);
		int page = TOC_PAGE + 1;
		Component toc = styled(h("목차"));
		StringBuilder tocText = new StringBuilder(h("목차"));
		for (Chapter c : CHAPTERS) {
			String line = String.format("%2d쪽  %s\n", page, c.title());
			toc = toc.append(Component.text(line).clickEvent(ClickEvent.changePage(page)));
			tocText.append(line);
			page += c.pages().length;
		}
		b.addPage(toc);
		PAGES.add(tocText.toString());
		for (Chapter c : CHAPTERS) for (String p : c.pages()) {
			int lines = p.split("\n", -1).length;
			String pad = "\n".repeat(Math.max(1, LINK_LINE - lines));
			Component pc = styled(p + pad).append(Component.text(BACK).clickEvent(ClickEvent.changePage(TOC_PAGE)));
			b.addPage(pc);
			PAGES.add(p + pad + BACK);
		}
		return b.build();
	}

	public static void write(ItemStack s) {
		s.setData(DataComponentTypes.WRITTEN_BOOK_CONTENT, content());
	}
}
