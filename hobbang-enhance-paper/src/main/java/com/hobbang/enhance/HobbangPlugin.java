package com.hobbang.enhance;

import com.hobbang.enhance.boss.BossManager;
import com.hobbang.enhance.command.HobbangCommand;
import com.hobbang.enhance.enhance.Enchants;
import com.hobbang.enhance.enhance.EnhanceConfig;
import com.hobbang.enhance.forge.ForgeManager;
import com.hobbang.enhance.recipe.Recipes;
import com.hobbang.enhance.weapon.WeaponListener;
import com.hobbang.enhance.world.EnchantListener;
import com.hobbang.enhance.world.TradeListener;
import com.hobbang.enhance.world.WorldListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** 홉방 강화 (Paper 1.21.11) — Fabric 모드 1.5.1 포팅. 바닐라 클라이언트로 접속 가능 */
public final class HobbangPlugin extends JavaPlugin {
	private static HobbangPlugin INSTANCE;

	public static HobbangPlugin get() {
		return INSTANCE;
	}

	@Override
	public void onEnable() {
		INSTANCE = this;
		getLogger().info(EnhanceConfig.load());
		Enchants.init();
		if (Enchants.loaded < Enchants.TOTAL)
			getLogger().warning("홉방 인첸트 " + Enchants.loaded + "/" + Enchants.TOTAL + " 만 로드됨 — 내장 데이터팩이 비활성화되었는지 확인하세요 (/datapack list)");
		else getLogger().info("홉방 인첸트 " + Enchants.TOTAL + "종 로드됨 (내장 데이터팩)");
		PluginManager pm = Bukkit.getPluginManager();
		ForgeManager.init(this);
		com.hobbang.enhance.forge.ForgeGui.init(this);
		com.hobbang.enhance.forge.Altar.init(this);
		pm.registerEvents(new com.hobbang.enhance.world.EnchantingListener(), this);
		pm.registerEvents(new com.hobbang.enhance.boss.BossLoot(), this);
		pm.registerEvents(new WeaponListener(), this);
		// v1.4.0: 대장장이의 망치를 든 플레이어에게만 용암 불씨 (10틱마다)
		Bukkit.getScheduler().runTaskTimer(this, () -> {
			for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers())
				if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR && com.hobbang.enhance.item.HItem.SMITH_HAMMER.is(p.getInventory().getItemInMainHand())) com.hobbang.enhance.weapon.WeaponFx.hammerAmbient(p);
		}, 20, 10);
		pm.registerEvents(new com.hobbang.enhance.enhance.EnhanceEffects(), this);
		pm.registerEvents(new WorldListener(), this);
		pm.registerEvents(new TradeListener(), this);
		pm.registerEvents(new EnchantListener(), this);
		pm.registerEvents(new Recipes(), this);
		Recipes.register();
		EnchantListener.initSmelting();
		BossManager.init(this);
		com.hobbang.enhance.arena.Arenas.init(this);
		com.hobbang.enhance.arena.StormArena.init(this);
		com.hobbang.enhance.arena.ArenaDress.init(this);
		// v1.4.1: 구조물 템플릿을 서버 시작 직후 미리 읽어 둔다 (첫 /hobbang structure place 때 ~55ms 렉 제거)
		// v1.4.3: 같은 타이밍에 보스 파츠 item_display 워밍업 (첫 대장장이 소환 스파이크 완화)
		Bukkit.getScheduler().runTaskLater(this, () -> {
			for (com.hobbang.enhance.arena.Site st : com.hobbang.enhance.arena.Site.values())
				if (st.structure != null) try { com.hobbang.enhance.arena.StructurePlacer.template(st); } catch (Exception ex) { getLogger().warning("구조물 템플릿 미리 읽기 실패: " + st.structure); }
			com.hobbang.enhance.boss.BossWarmup.start();
		}, 40L);
		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ev ->
			ev.registrar().register(HobbangCommand.build(), "홉방 강화 명령어", List.of("hb")));
		getLogger().info("홉방 강화 " + getPluginMeta().getVersion() + " 활성화 (레시피 " + Recipes.KEYS.size() + "개)");
	}

	@Override
	public void onDisable() {
		com.hobbang.enhance.forge.ForgeGui.closeAll();
		BossManager.shutdown();
		com.hobbang.enhance.arena.ArenaDress.shutdown();
		com.hobbang.enhance.arena.StormArena.saveSync();
		Recipes.unregister();
	}
}
