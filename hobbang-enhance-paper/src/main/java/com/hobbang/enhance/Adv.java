package com.hobbang.enhance;

import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

/** 데이터팩 발전과제 중 플러그인이 직접 주는 것 (강화 단계, 보스 처치) */
public final class Adv {
	private Adv() {}

	public static void award(Player p, String id) {
		Advancement a = Bukkit.getAdvancement(Keys.k(id));
		if (a == null) return;
		AdvancementProgress pr = p.getAdvancementProgress(a);
		if (pr.isDone()) return;
		for (String c : pr.getRemainingCriteria()) pr.awardCriteria(c);
	}

	/** master 처럼 기준이 여러 개인 과제의 한 기준만 */
	public static void criterion(Player p, String id, String criterion) {
		Advancement a = Bukkit.getAdvancement(Keys.k(id));
		if (a == null) return;
		AdvancementProgress pr = p.getAdvancementProgress(a);
		if (!pr.isDone()) pr.awardCriteria(criterion);
	}
}
