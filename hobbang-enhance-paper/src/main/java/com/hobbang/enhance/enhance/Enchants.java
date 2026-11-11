package com.hobbang.enhance.enhance;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/** 데이터팩(hobbang:*) 인첸트 조회. 효과가 JSON으로 안 되는 것은 플러그인이 직접 처리 */
public final class Enchants {
	private Enchants() {}

	/** 내장 데이터팩의 인첸트 전체 (v1.1.0: 20종) */
	public static final String[] IDS = {
		"lifesteal", "soul_harvest", "excavation", "timber", "forge_affinity", "fragility_curse",
		"swiftness", "vitality", "frostbite", "thunderstrike", "explosive",
		"superior_sharpness", "superior_protection",
		"telekinesis", "auto_smelt", "life_mending", "stomping", "critical", "venom", "reach"};
	public static final int TOTAL = IDS.length;

	public static Enchantment LIFESTEAL, SOUL_HARVEST, EXCAVATION, TIMBER, FORGE_AFFINITY, FRAGILITY_CURSE;
	public static Enchantment SUPERIOR_SHARPNESS, SUPERIOR_PROTECTION, TELEKINESIS, AUTO_SMELT, LIFE_MENDING, STOMPING, CRITICAL;
	public static int loaded = 0;

	public static void init() {
		LIFESTEAL = get("lifesteal");
		SOUL_HARVEST = get("soul_harvest");
		EXCAVATION = get("excavation");
		TIMBER = get("timber");
		FORGE_AFFINITY = get("forge_affinity");
		FRAGILITY_CURSE = get("fragility_curse");
		SUPERIOR_SHARPNESS = get("superior_sharpness");
		SUPERIOR_PROTECTION = get("superior_protection");
		TELEKINESIS = get("telekinesis");
		AUTO_SMELT = get("auto_smelt");
		LIFE_MENDING = get("life_mending");
		STOMPING = get("stomping");
		CRITICAL = get("critical");
		loaded = 0;
		for (String id : IDS) if (get(id) != null) loaded++;
	}

	public static Enchantment get(String id) {
		try {
			return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(Key.key("hobbang", id));
		} catch (Exception e) {
			return null;
		}
	}

	public static int level(Enchantment e, ItemStack s) {
		if (e == null || s == null || s.getType().isAir()) return 0;
		return s.getEnchantmentLevel(e);
	}

	/** 인첸트 책 (hobbang 인첸트 하나가 저장된 바닐라 enchanted_book) */
	public static ItemStack book(Enchantment e, int level) {
		ItemStack b = new ItemStack(Material.ENCHANTED_BOOK);
		if (e != null && b.getItemMeta() instanceof EnchantmentStorageMeta m) {
			m.addStoredEnchant(e, Math.max(1, level), true);
			b.setItemMeta(m);
		}
		return b;
	}
}
