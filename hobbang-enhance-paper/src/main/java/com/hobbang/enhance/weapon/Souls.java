package com.hobbang.enhance.weapon;

import com.hobbang.enhance.Keys;
import com.hobbang.enhance.item.HItem;
import com.hobbang.enhance.item.ItemUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 대장장이의 망치 10강 패시브 [영혼 수집]: 그 망치로 잡은 몹 한 마리마다 영혼 하나 = 그 몹 최대 체력의 5%만큼이
 * 망치에 쌓인다 (11강 6% … 15강 10%: 10강 이후 강화마다 +1%p). 쌓인 가중치에 비례해 지진의 피해와 범위가 커진다.
 */
public final class Souls implements Listener {
	private static final NamespacedKey WEIGHT = Keys.k("soul_weight"), COUNT = Keys.k("soul_count");

	/** 안전 상한 (무한정 커지는 것을 막음): 피해 최대 ×4, 범위 최대 +3칸 */
	static final double DMG_PER_WEIGHT = 0.01, RADIUS_PER_WEIGHT = 0.015, DMG_CAP = 4.0, RADIUS_CAP = 3.0;

	/** 영혼 1개의 가중치 비율(%): 10강 5, 11강 6 … 15강 10 (10강 미만은 0 = 패시브 없음) */
	public static double pct(int lv) {
		return lv >= 10 ? 5 + (lv - 10) : 0;
	}

	public static double weight(ItemStack s) {
		return s == null ? 0 : s.getPersistentDataContainer().getOrDefault(WEIGHT, PersistentDataType.DOUBLE, 0.0);
	}

	public static int count(ItemStack s) {
		return s == null ? 0 : s.getPersistentDataContainer().getOrDefault(COUNT, PersistentDataType.INTEGER, 0);
	}

	/** 지진 피해 배율 (10강 미만이면 ×1) */
	public static double dmgMult(ItemStack s, int lv) {
		return lv < 10 ? 1 : Math.min(DMG_CAP, 1 + DMG_PER_WEIGHT * weight(s));
	}

	/** 지진 범위 추가(칸) */
	public static double radiusAdd(ItemStack s, int lv) {
		return lv < 10 ? 0 : Math.min(RADIUS_CAP, RADIUS_PER_WEIGHT * weight(s));
	}

	/** 무기 설명(lore)용 한 줄 — 수집량이 수치로 보인다 */
	public static String lore(ItemStack s, int lv) {
		double w = weight(s);
		return String.format("[영혼 수집] 영혼 %d · 가중치 %.1f (지진 피해 +%.0f%% · 범위 +%.1f칸) · 처치당 최대 체력의 %.0f%%",
			count(s), w, (dmgMult(s, lv) - 1) * 100, radiusAdd(s, lv), pct(lv));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDeath(EntityDeathEvent e) {
		LivingEntity dead = e.getEntity();
		if (!(dead instanceof Mob)) return;
		Player p = dead.getKiller();
		if (p == null) return;
		ItemStack hand = p.getInventory().getItemInMainHand();
		if (!HItem.SMITH_HAMMER.is(hand)) return;
		int lv = ItemUtil.getLevel(hand);
		double pct = pct(lv);
		if (pct <= 0) return;
		var maxHp = dead.getAttribute(Attribute.MAX_HEALTH);
		if (maxHp == null) return;
		double add = maxHp.getValue() * pct / 100.0;
		ItemStack s = hand.clone();
		double w = weight(s) + add;
		int n = count(s) + 1;
		s.editPersistentDataContainer(pdc -> {
			pdc.set(WEIGHT, PersistentDataType.DOUBLE, w);
			pdc.set(COUNT, PersistentDataType.INTEGER, n);
		});
		ItemUtil.refreshLore(s);
		p.getInventory().setItemInMainHand(s);
	}
}
