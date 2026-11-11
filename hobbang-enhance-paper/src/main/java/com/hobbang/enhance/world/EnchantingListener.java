package com.hobbang.enhance.world;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * v1.2.0 인첸트 비용 개편.
 * - 인첸트 테이블: 바닐라가 고른 인첸트(책장 수 기준 1~30)는 그대로 두고, 필요 레벨만 ⌈비용/3⌉ (최대 10) 으로 낮춘다.
 *   → 책장 15개면 플레이어 10레벨로 최고 인첸트. 소모 레벨은 바닐라처럼 버튼 1/2/3.
 *   바닐라는 낮춘 비용으로 인첸트를 다시 고르므로, 원래 비용 + 같은 시드로 다시 뽑아 미리보기와 결과를 맞춘다.
 * - 모루: 비용 ⌈비용/3⌉, 최대 10레벨. "너무 비쌉니다" 제한 해제.
 */
@SuppressWarnings("UnstableApiUsage")
public final class EnchantingListener implements Listener {
	public static final int TABLE_DIVISOR = 3, TABLE_MAX = 10, ANVIL_MAX = 10;
	private static final Map<UUID, int[]> ORIGINAL = new HashMap<>();

	public static int tableCost(int vanilla) {
		return Math.max(1, Math.min(TABLE_MAX, (vanilla + TABLE_DIVISOR - 1) / TABLE_DIVISOR));
	}

	public static int anvilCost(int vanilla) {
		return vanilla <= 0 ? vanilla : Math.max(1, Math.min(ANVIL_MAX, (vanilla + 2) / 3));
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPrepare(PrepareItemEnchantEvent e) {
		EnchantmentOffer[] offers = e.getOffers();
		int[] orig = new int[3];
		for (int i = 0; i < offers.length && i < 3; i++) {
			EnchantmentOffer o = offers[i];
			if (o == null) continue;
			orig[i] = o.getCost();
			o.setCost(tableCost(o.getCost()));
		}
		ORIGINAL.put(e.getEnchanter().getUniqueId(), orig);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEnchant(EnchantItemEvent e) {
		Player p = e.getEnchanter();
		int btn = e.whichButton();
		int[] orig = ORIGINAL.get(p.getUniqueId());
		e.setExpLevelCost(Math.min(e.getExpLevelCost(), btn + 1));
		if (orig == null || btn < 0 || btn > 2 || orig[btn] <= 0) return;
		ItemStack item = e.getItem();
		boolean book = item.getType() == Material.BOOK;
		RegistryKeySet<Enchantment> table = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).getTag(EnchantmentTagKeys.IN_ENCHANTING_TABLE);
		Random rnd = new Random(p.getEnchantmentSeed() + btn);
		ItemStack sample = Bukkit.getItemFactory().enchantWithLevels(new ItemStack(item.getType()), orig[btn], table, rnd);
		Map<Enchantment, Integer> picked = new java.util.LinkedHashMap<>();
		if (sample.getItemMeta() instanceof EnchantmentStorageMeta sm) picked.putAll(sm.getStoredEnchants());
		else picked.putAll(sample.getEnchantments());
		if (picked.isEmpty()) return;
		if (book && picked.size() > 1) {
			List<Enchantment> keys = new java.util.ArrayList<>(picked.keySet());
			picked.remove(keys.get(rnd.nextInt(keys.size())));
		}
		// 미리보기로 보여 준 인첸트는 반드시 들어가게
		if (!picked.containsKey(e.getEnchantmentHint())) picked.put(e.getEnchantmentHint(), e.getLevelHint());
		e.getEnchantsToAdd().clear();
		e.getEnchantsToAdd().putAll(picked);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		ORIGINAL.remove(e.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onAnvilOpen(InventoryOpenEvent e) {
		if (e.getView() instanceof AnvilView v) v.setMaximumRepairCost(Integer.MAX_VALUE);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onAnvil(PrepareAnvilEvent e) {
		AnvilView v = e.getView();
		if (v.getMaximumRepairCost() < 1000) v.setMaximumRepairCost(Integer.MAX_VALUE);
		int c = v.getRepairCost();
		if (c > 0 && e.getResult() != null && !e.getResult().getType().isAir()) v.setRepairCost(anvilCost(c));
	}
}
