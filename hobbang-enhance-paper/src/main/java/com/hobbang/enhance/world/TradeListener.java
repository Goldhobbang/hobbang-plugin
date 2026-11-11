package com.hobbang.enhance.world;

import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.Keys;
import com.hobbang.enhance.item.HItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 주민 / 떠돌이 상인 거래 (Fabric ModTrades 와 같은 가격).
 * Fabric 은 해당 레벨의 후보 풀에 거래를 추가했으므로, 같은 확률(2 / (바닐라 후보 수 + 1))로 그 레벨에 거래가 하나 더 생긴다.
 */
public final class TradeListener implements Listener {
	private record Trade(Villager.Profession prof, int level, HItem item, int price, int count, int maxUses, int xp, double chance) {}

	private static final List<Trade> TRADES = List.of(
		new Trade(Villager.Profession.TOOLSMITH, 1, HItem.ENHANCE_STONE, 2, 4, 12, 2, 2.0 / 5),
		new Trade(Villager.Profession.WEAPONSMITH, 1, HItem.ENHANCE_STONE, 2, 4, 12, 2, 2.0 / 4),
		new Trade(Villager.Profession.LIBRARIAN, 1, HItem.GUIDE, 1, 1, 3, 2, 2.0 / 4),
		new Trade(Villager.Profession.CLERIC, 2, HItem.LUCK_CHARM, 5, 1, 6, 10, 2.0 / 3),
		new Trade(Villager.Profession.LIBRARIAN, 3, HItem.BLESSING_SCROLL, 14, 1, 3, 15, 2.0 / 4),
		new Trade(Villager.Profession.LIBRARIAN, 4, HItem.PROTECTION_SCROLL, 24, 1, 2, 20, 2.0 / 5),
		new Trade(Villager.Profession.TOOLSMITH, 5, HItem.RADIANT_STONE, 32, 1, 3, 30, 1.0),
		new Trade(Villager.Profession.WEAPONSMITH, 5, HItem.RELIC_TEMPLATE, 48, 1, 1, 30, 1.0)
	);

	private static MerchantRecipe recipe(HItem item, int price, int count, int maxUses, int xp) {
		MerchantRecipe r = new MerchantRecipe(item.create(count), 0, maxUses, true, xp, 0.05F);
		r.addIngredient(new ItemStack(Material.EMERALD, price));
		return r;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onAcquire(VillagerAcquireTradeEvent e) {
		if (!(e.getEntity() instanceof Villager v)) return;
		int level = v.getVillagerLevel();
		int done = v.getPersistentDataContainer().getOrDefault(Keys.k("trade_lv"), PersistentDataType.INTEGER, 0);
		if (done >= level) return;   // 레벨당 1회만 판정 (거래가 2개 생겨도)
		v.getPersistentDataContainer().set(Keys.k("trade_lv"), PersistentDataType.INTEGER, level);
		for (Trade t : TRADES) {
			if (!t.prof().getKey().equals(v.getProfession().getKey()) || t.level() != level) continue;
			if (ThreadLocalRandom.current().nextDouble() >= t.chance()) continue;
			MerchantRecipe ours = recipe(t.item(), t.price(), t.count(), t.maxUses(), t.xp());
			if (t.chance() < 1.0) {
				// Fabric: 후보 풀에 섞여 뽑힘 = 바닐라 거래 하나 대신 들어감 (확률 동일)
				e.setRecipe(ours);
			} else {
				// 후보가 2개 미만인 레벨(5레벨)은 바닐라 거래와 함께 추가
				Bukkit.getScheduler().runTask(HobbangPlugin.get(), () -> {
					if (!v.isValid()) return;
					List<MerchantRecipe> list = new ArrayList<>(v.getRecipes());
					list.add(ours);
					v.setRecipes(list);
				});
			}
			break;
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onTrader(EntitySpawnEvent e) {
		if (!(e.getEntity() instanceof WanderingTrader w)) return;
		Bukkit.getScheduler().runTask(HobbangPlugin.get(), () -> {
			if (!w.isValid()) return;
			List<MerchantRecipe> list = new ArrayList<>(w.getRecipes());
			ThreadLocalRandom r = ThreadLocalRandom.current();
			if (r.nextDouble() < 0.15) list.add(recipe(HItem.LUCK_CHARM, 6, 1, 4, 1));
			if (r.nextDouble() < 0.16) list.add(recipe(HItem.ENHANCE_STONE, 4, 6, 6, 1));
			w.setRecipes(list);
		});
	}
}
