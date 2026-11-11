package com.hobbang.enhance.arena;

import com.hobbang.enhance.boss.BossKind;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

/**
 * v1.4.0 보스 투기장 4곳. 투기장 범위 = 제단 블록 기준 가로 ±half, 세로 yLo..yHi (구조물은 제단이 정중앙).
 * spawnDx/Dz/Dy = 제단 기준 보스 소환 위치 (발밑). 구조물은 회전돼 생성될 수 있지만 거리만 쓰므로 무관.
 */
public enum Site {
	FORGE(BossKind.ABYSSAL_SMITH, "abyssal_forge", "심연의 대장간", Material.BLAST_FURNACE, 18, -3, 22, 0, -1, -8,
		World.Environment.NETHER, "네더 · 현무암 삼각주", "61×30×61 거대한 대장간 (부벽·성문탑·용암 수로·대장간 마당·무너진 성벽, 굴뚝 4개)"),
	GROTTO(BossKind.PRISM_COLOSSUS, "prism_grotto", "프리즘 정동", Material.BEACON, 16, -3, 15, 0, -1, -7,
		World.Environment.NORMAL, "오버월드 지하 · 무성한 동굴 (y -24 ~ 16)", "47×27×47 천연 자수정 정동 동굴 (종유석·이끼·발광 이끼·포자 꽃, 수정 첨탑 6개, 굽은 굴 4개)"),
	TEMPLE(BossKind.CHRONO_SENTINEL, "chrono_temple", "크로노 신전", Material.ENCHANTING_TABLE, 19, -4, 18, 0, -2, -9,
		World.Environment.NORMAL, "오버월드 지상 · 평원/초원/사바나/사막/눈 평원/벚나무 숲/숲/자작나무 숲/타이가", "57×27×57 원형 시계 광장 (구리 톱니바퀴 아치문 4개, 무너진 외곽 열주, 바닥 톱니 모자이크)"),
	STORM(BossKind.TEMPEST_WYVERN, null, "폭풍 제단", Material.LIGHTNING_ROD, 20, -3, 28, 0, 10, 0,
		World.Environment.NORMAL, "어디서나 · 천둥번개가 칠 때 (직접 쌓는 제단)", "제단 위 하늘에 떠다니는 발판 투기장이 생겨나고, 전투 뒤 무너져 원래대로 돌아감");

	public final BossKind kind;
	public final String structure;
	public final String title;
	public final Material altarBlock;
	public final int half, yLo, yHi, spawnDx, spawnDy, spawnDz;
	public final World.Environment env;
	public final String where;
	public final String looks;

	Site(BossKind kind, String structure, String title, Material altarBlock, int half, int yLo, int yHi, int sdx, int sdy, int sdz,
		World.Environment env, String where, String looks) {
		this.kind = kind; this.structure = structure; this.title = title; this.altarBlock = altarBlock;
		this.half = half; this.yLo = yLo; this.yHi = yHi; this.spawnDx = sdx; this.spawnDy = sdy; this.spawnDz = sdz;
		this.env = env; this.where = where; this.looks = looks;
	}

	public NamespacedKey key() {
		return structure == null ? null : new NamespacedKey("hobbang", structure);
	}

	public static Site of(BossKind k) {
		for (Site s : values()) if (s.kind == k) return s;
		return null;
	}

	public static Site byName(String n) {
		for (Site s : values()) if (s.name().equalsIgnoreCase(n) || (s.structure != null && s.structure.equalsIgnoreCase(n))) return s;
		return switch (n.toLowerCase()) {
			case "smith" -> FORGE;
			case "colossus" -> GROTTO;
			case "sentinel" -> TEMPLE;
			case "wyvern" -> STORM;
			default -> null;
		};
	}

	public static Site byBoss(String bossId) {
		BossKind k = BossKind.byId(bossId);
		return k == null ? null : of(k);
	}
}
