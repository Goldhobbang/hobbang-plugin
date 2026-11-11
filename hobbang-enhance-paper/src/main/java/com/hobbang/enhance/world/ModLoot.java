package com.hobbang.enhance.world;

import com.hobbang.enhance.item.HItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.hobbang.enhance.item.HItem.*;

/**
 * 야생 획득 경로 (Fabric ModLoot 와 동일한 표).
 * 상자형 = 독립 추첨 (확률, 개수) / 단일 결과형(낚시 보물·고고학) = 바닐라 풀에 가중치 추가와 같은 확률로 교체
 */
public final class ModLoot {
	public record Drop(HItem item, float chance, int min, int max) {}
	public record Entry(HItem item, int weight, int min, int max) {}

	public static final Map<String, List<Drop>> CHESTS = new LinkedHashMap<>();
	public static final Map<String, List<Entry>> SINGLE = new LinkedHashMap<>();
	/** 바닐라 단일 결과형 표의 가중치 합 (1.21.11 기준) */
	public static final Map<String, Integer> VANILLA_WEIGHT = new LinkedHashMap<>();

	private static void chest(String k, Drop... d) { CHESTS.computeIfAbsent("minecraft:chests/" + k, x -> new ArrayList<>()).addAll(List.of(d)); }
	private static void single(String k, int vanillaWeight, Entry... e) {
		SINGLE.computeIfAbsent(k, x -> new ArrayList<>()).addAll(List.of(e));
		VANILLA_WEIGHT.put(k, vanillaWeight);
	}
	private static Drop d(HItem i, float c, int min, int max) { return new Drop(i, c, min, max); }

	static {
		HItem ES = ENHANCE_STONE, RS = RADIANT_STONE, LC = LUCK_CHARM, BS = BLESSING_SCROLL, PS = PROTECTION_SCROLL, RT = RELIC_TEMPLATE, GD = GUIDE;
		chest("simple_dungeon", d(ES, 0.7F, 2, 5), d(LC, 0.2F, 1, 1), d(BS, 0.1F, 1, 1));
		chest("abandoned_mineshaft", d(ES, 0.6F, 2, 4), d(BS, 0.08F, 1, 1));
		chest("desert_pyramid", d(ES, 0.6F, 2, 5), d(LC, 0.25F, 1, 1));
		chest("jungle_temple", d(ES, 0.6F, 2, 5), d(LC, 0.2F, 1, 1));
		chest("shipwreck_treasure", d(ES, 0.5F, 1, 4), d(LC, 0.1F, 1, 1));
		chest("buried_treasure", d(ES, 1.0F, 3, 6), d(LC, 0.3F, 1, 1), d(PS, 0.1F, 1, 1));
		chest("village/village_weaponsmith", d(ES, 0.5F, 1, 3), d(GD, 0.15F, 1, 1));
		chest("village/village_toolsmith", d(ES, 0.5F, 1, 3), d(GD, 0.15F, 1, 1));
		chest("ruined_portal", d(ES, 0.4F, 1, 3));
		chest("pillager_outpost", d(ES, 0.4F, 1, 3), d(LC, 0.1F, 1, 1));
		chest("trial_chambers/reward", d(ES, 0.5F, 2, 4), d(LC, 0.2F, 1, 1));
		chest("stronghold_library", d(PS, 0.3F, 1, 1), d(BS, 0.3F, 1, 1), d(GD, 0.2F, 1, 1));
		chest("stronghold_corridor", d(ES, 0.5F, 2, 4), d(RS, 0.25F, 1, 2));
		chest("woodland_mansion", d(PS, 0.25F, 1, 1), d(BS, 0.25F, 1, 1), d(RS, 0.2F, 1, 2));
		chest("nether_bridge", d(ES, 0.5F, 2, 5), d(RS, 0.15F, 1, 1));
		chest("bastion_other", d(ES, 0.5F, 2, 5), d(RS, 0.1F, 1, 1));
		chest("bastion_treasure", d(RS, 0.6F, 1, 3), d(RT, 0.25F, 1, 1), d(PS, 0.25F, 1, 1));
		chest("ancient_city", d(RS, 0.4F, 1, 2), d(PS, 0.2F, 1, 1), d(RT, 0.1F, 1, 1), d(ABYSS_ESSENCE, 0.06F, 1, 1), d(CHRONO_GEAR, 0.06F, 1, 1));
		chest("end_city_treasure", d(RS, 0.4F, 1, 3), d(RT, 0.1F, 1, 1), d(STORM_FEATHER, 0.08F, 1, 1), d(IMMORTAL_SCROLL, 0.06F, 1, 1));
		chest("trial_chambers/reward_ominous", d(RS, 0.4F, 1, 2), d(RT, 0.1F, 1, 1), d(PRISM_SCROLL, 0.12F, 1, 1), d(PRISM_CRYSTAL, 0.08F, 1, 1));
		single("minecraft:gameplay/fishing/treasure", 6, new Entry(ES, 2, 2, 4), new Entry(LC, 1, 1, 1));
		single("minecraft:archaeology/trail_ruins_rare", 12, new Entry(LC, 2, 1, 1), new Entry(BS, 1, 1, 1));
		single("minecraft:archaeology/desert_pyramid", 8, new Entry(ES, 2, 1, 1));
	}

	/**
	 * 상위 인첸트(상위 날카로움/보호) 책: 아주 낮은 확률로 최상급 상자·금고·보스에서만.
	 * 인첸트 테이블·바닐라 무작위 인첸트·사서 거래에는 나오지 않는다.
	 */
	public static final Map<String, Float> SUPERIOR = new LinkedHashMap<>();
	public static final float BOSS_SUPERIOR_CHANCE = 0.05F;
	static {
		SUPERIOR.put("minecraft:chests/ancient_city", 0.02F);
		SUPERIOR.put("minecraft:chests/end_city_treasure", 0.02F);
		SUPERIOR.put("minecraft:chests/bastion_treasure", 0.02F);
		SUPERIOR.put("minecraft:chests/trial_chambers/reward_ominous", 0.015F);
		SUPERIOR.put("minecraft:chests/woodland_mansion", 0.01F);
		SUPERIOR.put("minecraft:chests/stronghold_library", 0.01F);
	}

	/** 레벨: I 80% · II 17% · III 3% */
	public static int superiorLevel() {
		float f = ThreadLocalRandom.current().nextFloat();
		return f < 0.03F ? 3 : f < 0.20F ? 2 : 1;
	}

	/** 상위 날카로움/보호 중 하나(반반)가 담긴 책 */
	public static org.bukkit.inventory.ItemStack superiorBook() {
		boolean sharp = ThreadLocalRandom.current().nextBoolean();
		return com.hobbang.enhance.enhance.Enchants.book(sharp ? com.hobbang.enhance.enhance.Enchants.SUPERIOR_SHARPNESS
			: com.hobbang.enhance.enhance.Enchants.SUPERIOR_PROTECTION, superiorLevel());
	}

	public static void rollSuperior(String lootKey, java.util.Collection<org.bukkit.inventory.ItemStack> out) {
		Float c = SUPERIOR.get(lootKey);
		if (c != null && ThreadLocalRandom.current().nextFloat() < c) out.add(superiorBook());
	}

	public static void rollSuperiorBoss(java.util.Collection<org.bukkit.inventory.ItemStack> out) {
		if (ThreadLocalRandom.current().nextFloat() < BOSS_SUPERIOR_CHANCE) out.add(superiorBook());
	}

	/**
	 * v1.2.0 상자 전용 희귀 인첸트 책 (인첸트 테이블·주민 거래·낚시·바닐라 무작위 인첸트에서는 안 나옴).
	 * 상위 날카로움/보호는 위의 SUPERIOR 표로 따로, 더 낮은 확률.
	 */
	public static final String[] CHEST_ONLY = {"telekinesis", "auto_smelt", "life_mending", "thunderstrike", "explosive", "excavation", "timber", "forge_affinity"};
	public static final Map<String, Float> RARE = new LinkedHashMap<>();
	static {
		for (Object[] r : new Object[][]{
			{"simple_dungeon", 0.10F}, {"abandoned_mineshaft", 0.07F}, {"desert_pyramid", 0.10F}, {"jungle_temple", 0.10F},
			{"shipwreck_treasure", 0.06F}, {"buried_treasure", 0.10F}, {"ruined_portal", 0.05F}, {"pillager_outpost", 0.06F},
			{"nether_bridge", 0.06F}, {"bastion_other", 0.06F}, {"bastion_treasure", 0.15F}, {"stronghold_library", 0.25F},
			{"stronghold_corridor", 0.08F}, {"woodland_mansion", 0.18F}, {"ancient_city", 0.18F}, {"end_city_treasure", 0.15F},
			{"trial_chambers/reward", 0.06F}, {"trial_chambers/reward_ominous", 0.12F}})
			RARE.put("minecraft:chests/" + r[0], (Float) r[1]);
	}

	public static boolean hasExtra(String lootKey) {
		return SUPERIOR.containsKey(lootKey) || RARE.containsKey(lootKey);
	}

	/** 상자 전용 희귀 인첸트 책 1권 (종류 균등, 레벨 1~최대 균등) */
	public static org.bukkit.inventory.ItemStack rareBook() {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		org.bukkit.enchantments.Enchantment e = com.hobbang.enhance.enhance.Enchants.get(CHEST_ONLY[r.nextInt(CHEST_ONLY.length)]);
		int max = e == null ? 1 : e.getMaxLevel();
		return com.hobbang.enhance.enhance.Enchants.book(e, 1 + r.nextInt(max));
	}

	/** 상자/금고 하나에 붙는 추가 인첸트 책 추첨 (희귀 + 상위) */
	public static void rollBooks(String lootKey, java.util.Collection<org.bukkit.inventory.ItemStack> out) {
		Float c = RARE.get(lootKey);
		if (c != null && ThreadLocalRandom.current().nextFloat() < c) out.add(rareBook());
		rollSuperior(lootKey, out);
	}

	private ModLoot() {}

	/** 단일 결과형 표에서 모드 항목이 뽑혔는지 추첨 (null = 바닐라 결과 유지) */
	public static Entry pickSingle(String key) {
		List<Entry> es = SINGLE.get(key);
		if (es == null) return null;
		int mod = 0;
		for (Entry e : es) mod += e.weight();
		int total = mod + VANILLA_WEIGHT.getOrDefault(key, 10);
		int roll = ThreadLocalRandom.current().nextInt(total);
		for (Entry e : es) {
			if (roll < e.weight()) return e;
			roll -= e.weight();
		}
		return null;
	}
}
