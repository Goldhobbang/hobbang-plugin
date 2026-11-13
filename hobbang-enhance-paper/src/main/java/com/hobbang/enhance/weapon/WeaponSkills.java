package com.hobbang.enhance.weapon;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** 보스 무기 스킬 보조 리스너(영혼 수집 · 망치 낙하 · 크로노 타이머/스턴) 등록과 정리 */
public final class WeaponSkills {
	private WeaponSkills() {}

	public static void init(JavaPlugin plugin) {
		var pm = Bukkit.getPluginManager();
		pm.registerEvents(new Souls(), plugin);
		pm.registerEvents(new HammerSkill(), plugin);
		pm.registerEvents(new ChronoSkill(), plugin);
	}

	public static void shutdown() {
		ChronoSkill.shutdown();
	}
}
