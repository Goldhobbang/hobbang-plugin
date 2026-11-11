package com.hobbang.enhance.enhance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hobbang.enhance.HobbangPlugin;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** plugins/HobbangEnhance/hobbang-enhance.json 로 조정 가능한 밸런스 표 (Fabric 판과 같은 기본값) */
public final class EnhanceConfig {
	public static final class Stage {
		public double success;     // 성공 확률 (%)
		public double downgrade;   // 실패 시 하락 확률 (%)
		public double destroy;     // 실패 시 파괴 확률 (%)
		public int stones;         // 재료 개수
		public int xpLevels;       // 경험치 레벨 비용

		Stage(double s, double down, double des, int st, int xp) {
			success = s; downgrade = down; destroy = des; stones = st; xpLevels = xp;
		}
	}

	public static final class Data {
		public Stage[] stages = {
			new Stage(100, 0, 0, 1, 1),
			new Stage(95, 0, 0, 1, 1),
			new Stage(90, 0, 0, 1, 2),
			new Stage(85, 0, 0, 1, 2),
			new Stage(80, 0, 0, 2, 3),
			new Stage(70, 20, 0, 2, 3),
			new Stage(60, 30, 0, 2, 4),
			new Stage(50, 40, 0, 2, 4),
			new Stage(40, 40, 0, 3, 5),
			new Stage(35, 50, 5, 3, 6),
			new Stage(30, 50, 10, 1, 7),
			new Stage(25, 50, 15, 1, 8),
			new Stage(20, 50, 20, 2, 9),
			new Stage(15, 50, 25, 2, 10),
			new Stage(10, 50, 30, 3, 12),
		};
		public int radiantFrom = 10;
		public double luckCharmBonus = 10;
		public double affinityPerLevel = 3;
		public double fragilityDestroyBonus = 5;
		public double artisanRatio = 0.5;
		public int broadcastFrom = 10;
		public double mobStoneChance = 3;
		public double oreBonusStoneChance = 10;
		public double deepslateRadiantChance = 3;
		/**
		 * Paper 판 전용: 강화석 광석 블록 대신, Y32 아래의 돌/심층암/응회암을 캘 때 강화석이 나올 확률(%).
		 * Fabric 광맥(청크당 6개, 크기 6)의 평균 산출량에 맞춘 값. 나오면 돌 1~2개 / 심층암 1~3개(+행운 보너스).
		 */
		public double stoneDropChance = 0.4;
		public int stoneDropMaxY = 32;
		/** v1.2.0: true 면 예전처럼 장비를 들고 강화대를 우클릭하면 바로 강화 (부적·주문서는 가방에서). 기본 false = 강화 창 */
		public boolean legacyRightClick = false;
		/**
		 * v1.3.0: 보스 외형. "auto" = server.properties 에 resource-pack 이 설정돼 있으면 리소스팩 모델(hobbang-pack), 없으면 예전 블록 모델.
		 * "on" = 항상 리소스팩 모델 (모든 플레이어가 팩을 직접 적용한 경우), "off" = 항상 블록 모델
		 */
		public String bossModels = "auto";
	}

	public static Data CFG = new Data();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static Path path() {
		return HobbangPlugin.get().getDataFolder().toPath().resolve("hobbang-enhance.json");
	}

	public static String load() {
		Path p = path();
		try {
			Files.createDirectories(p.getParent());
			if (Files.exists(p)) {
				try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
					Data d = GSON.fromJson(r, Data.class);
					if (d == null || d.stages == null || d.stages.length != 15) {
						HobbangPlugin.get().getLogger().warning("hobbang-enhance.json 의 stages 는 15개여야 합니다. 기본값 사용.");
						CFG = new Data();
						return "설정 오류 - 기본값 사용";
					}
					CFG = d;
				}
			} else {
				CFG = new Data();
			}
			try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
				GSON.toJson(CFG, w);
			}
			return "설정을 불러왔습니다: " + p.getFileName();
		} catch (Exception e) {
			HobbangPlugin.get().getLogger().warning("설정 읽기 실패: " + e);
			CFG = new Data();
			return "설정 읽기 실패 - 기본값 사용 (" + e.getMessage() + ")";
		}
	}
}
