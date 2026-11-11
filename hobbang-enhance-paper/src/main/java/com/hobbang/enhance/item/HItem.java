package com.hobbang.enhance.item;

import com.hobbang.enhance.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.DamageResistant;
import io.papermc.paper.datacomponent.item.UseCooldown;
import io.papermc.paper.registry.keys.tags.DamageTypeTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * 홉방 아이템 = 바닐라 아이템 + 컴포넌트(custom_data/item_name/item_model/lore…)
 * 바닐라 클라이언트에서도 바닐라 모델로 보이고, 선택 리소스팩은 custom_model_data 문자열("hobbang:id")로 덮어쓸 수 있다.
 */
public enum HItem {
	ENHANCE_STONE("enhance_stone", "강화석", Material.KNOWLEDGE_BOOK, "prismarine_crystals", ItemRarity.COMMON, 64, false, false,
		"강화대에서 +0~+9 강화에 쓰는 재료", null),
	RADIANT_STONE("radiant_stone", "빛나는 강화석", Material.KNOWLEDGE_BOOK, "glowstone_dust", ItemRarity.RARE, 64, true, false,
		"강화대에서 +10~+15 강화에 쓰는 재료", null),
	PROTECTION_SCROLL("protection_scroll", "파괴 방지 주문서", Material.KNOWLEDGE_BOOK, "paper", ItemRarity.UNCOMMON, 16, false, false,
		"강화대 창 보조 칸: 장비 파괴를 1단계 하락으로 대신함", "파괴될 때만 1개 소모"),
	BLESSING_SCROLL("blessing_scroll", "축복 주문서", Material.KNOWLEDGE_BOOK, "map", ItemRarity.UNCOMMON, 16, false, false,
		"강화대 창 보조 칸: 실패해도 단계가 내려가지 않음", "하락할 때만 1개 소모"),
	LUCK_CHARM("luck_charm", "행운의 부적", Material.KNOWLEDGE_BOOK, "rabbit_foot", ItemRarity.UNCOMMON, 16, false, false,
		"강화대 창 보조 칸: 강화 성공률 +10%p", "강화할 때마다 1개 자동 소모"),
	ABYSS_ESSENCE("abyss_essence", "심연의 정수", Material.KNOWLEDGE_BOOK, "echo_shard", ItemRarity.EPIC, 64, true, true,
		"유물 무기(대장장이의 망치) 단조 재료", "프리즘의 핵을 만드는 데도 쓰임"),
	ABYSS_CORE("abyss_core", "심연의 핵", Material.KNOWLEDGE_BOOK, "ender_eye", ItemRarity.EPIC, 1, true, true,
		"들고 강화대를 우클릭: 심연의 대장장이 소환", "5초 의식 뒤 시련 시작 · 1개 소모"),
	PRISM_CRYSTAL("prism_crystal", "프리즘 수정", Material.KNOWLEDGE_BOOK, "amethyst_shard", ItemRarity.EPIC, 64, true, false,
		"유물 무기(수정 대검) 단조 재료", "폭풍의 핵을 만드는 데도 쓰임"),
	STORM_FEATHER("storm_feather", "폭풍 깃털", Material.KNOWLEDGE_BOOK, "feather", ItemRarity.EPIC, 64, true, false,
		"유물 무기(뇌운 전투도끼) 단조 재료", "시간의 핵을 만드는 데도 쓰임"),
	CHRONO_GEAR("chrono_gear", "시간의 톱니", Material.KNOWLEDGE_BOOK, "clock", ItemRarity.EPIC, 64, true, false,
		"유물 무기(크로노 낫) 단조 재료", "역행 모래시계를 만드는 데도 쓰임"),
	PRISM_SCROLL("prism_scroll", "프리즘 주문서", Material.KNOWLEDGE_BOOK, "globe_banner_pattern", ItemRarity.RARE, 16, false, false,
		"강화대 창 보조 칸: 강화 성공률 +20%p", "강화할 때마다 1개 자동 소모 · 행운의 부적과 중첩"),
	IMMORTAL_SCROLL("immortal_scroll", "불멸 주문서", Material.KNOWLEDGE_BOOK, "flow_banner_pattern", ItemRarity.EPIC, 16, false, false,
		"강화대 창 보조 칸: 장비 파괴를 막고 단계를 유지", "파괴될 때만 1개 소모 · 파괴 방지 주문서보다 먼저 쓰임"),
	REWIND_HOURGLASS("rewind_hourglass", "역행 모래시계", Material.KNOWLEDGE_BOOK, "honey_bottle", ItemRarity.EPIC, 16, false, false,
		"강화대 창 보조 칸: 하락·파괴 결과를 되감음", "재료와 경험치 환불 · 주문서보다 먼저 쓰임"),
	PRISM_CORE("prism_core", "프리즘 핵", Material.KNOWLEDGE_BOOK, "end_crystal", ItemRarity.EPIC, 1, true, false,
		"들고 강화대를 우클릭: 프리즘 콜로서스 소환", "5초 의식 뒤 시련 시작 · 1개 소모"),
	STORM_CORE("storm_core", "폭풍의 핵", Material.KNOWLEDGE_BOOK, "heart_of_the_sea", ItemRarity.EPIC, 1, true, false,
		"들고 강화대를 우클릭: 폭풍의 와이번 소환", "5초 의식 뒤 시련 시작 · 1개 소모"),
	CHRONO_CORE("chrono_core", "시간의 핵", Material.KNOWLEDGE_BOOK, "recovery_compass", ItemRarity.EPIC, 1, true, false,
		"들고 강화대를 우클릭: 크로노 센티넬 소환", "5초 의식 뒤 시련 시작 · 1개 소모"),
	RELIC_TEMPLATE("relic_template", "유물 단조 형판", Material.KNOWLEDGE_BOOK, "netherite_upgrade_smithing_template", ItemRarity.EPIC, 64, false, false,
		"대장장이 작업대: 네더라이트 장비 + 보스 재료 → 유물 무기", "강화 단계와 인첸트는 그대로 옮겨짐"),
	SUMMON_ALTAR("summon_altar", "소환대", Material.SCULK_SENSOR, null, ItemRarity.EPIC, 64, true, false,
		"설치 후 우클릭: 보스 소환 창", "보스의 핵을 바쳐 네 시련을 부른다"),
	ENHANCE_FORGE("enhance_forge", "강화대", Material.SMITHING_TABLE, null, ItemRarity.RARE, 64, false, false,
		"장비 들고 우클릭: 강화 / 좌클릭: 확률 미리보기", "보스의 핵을 들고 우클릭: 시련 소환"),
	GUIDE("guide", "홉방 강화 가이드", Material.WRITTEN_BOOK, null, ItemRarity.COMMON, 1, false, false,
		"우클릭: 장인의 기록을 펼친다", null),
	// ── 유물 무기 (네더라이트 기반, 공격력/속도는 Fabric 판과 동일) ──
	SMITH_HAMMER("smith_hammer", "대장장이의 망치", Material.NETHERITE_AXE, "mace", ItemRarity.EPIC, 1, true, true,
		"우클릭: 지진 - 반경 5 적에게 피해, 밀쳐냄 (쿨타임 8초)", "심연에 남은 장인의 망치", NamedTextColor.DARK_PURPLE, 12.0, -3.0, 160),
	CRYSTAL_GREATSWORD("crystal_greatsword", "수정 대검", Material.NETHERITE_SWORD, "diamond_sword", ItemRarity.EPIC, 1, true, true,
		"우클릭: 전방으로 수정 가시 8연발 (쿨타임 10초)", "완벽한 형태를 담은 수정 검", NamedTextColor.LIGHT_PURPLE, 13.0, -3.2, 200),
	// v1.4.0: id 는 그대로(기존 아이템 호환), 모양·이름·기반 아이템만 바뀜 → ItemUtil.migrateIdentity
	STORMCALLER("stormcaller", "뇌운 전투도끼", Material.NETHERITE_AXE, "netherite_axe", ItemRarity.EPIC, 1, true, true,
		"우클릭: 바라보는 쪽으로 돌진 후 낙뢰 폭발 (쿨타임 6초)", "폭풍 와이번의 날개로 벼린 전투도끼", NamedTextColor.AQUA, 11.0, -2.9, 120),
	CHRONO_DAGGER("chrono_dagger", "크로노 낫", Material.NETHERITE_SWORD, "netherite_hoe", ItemRarity.EPIC, 1, true, true,
		"우클릭: 전방 낫 베기 + 반경 8 시간 정지 (쿨타임 20초)", "휩쓸기 · 사거리 +1 · 타격 시 구속 — 시간을 거두는 거대한 낫", NamedTextColor.GOLD, 9.0, -2.7, 400);

	public final String id;
	public final String name;
	public final Material base;
	public final String model;
	public final ItemRarity rarity;
	public final int maxStack;
	public final boolean glint;
	public final boolean fireRes;
	public final String desc, desc2;
	public final TextColor descColor, desc2Color;
	/** 무기일 때만: 기본 공격력 모디파이어 / 공격 속도 모디파이어 / 스킬 쿨타임(틱) */
	public final double damage, speed;
	public final int cooldown;

	HItem(String id, String name, Material base, String model, ItemRarity rarity, int maxStack, boolean glint, boolean fireRes, String desc, String desc2) {
		this(id, name, base, model, rarity, maxStack, glint, fireRes, desc, desc2, NamedTextColor.GRAY, Double.NaN, Double.NaN, 0);
	}

	HItem(String id, String name, Material base, String model, ItemRarity rarity, int maxStack, boolean glint, boolean fireRes, String desc, String desc2,
		  TextColor descColor, double damage, double speed, int cooldown) {
		this.id = id; this.name = name; this.base = base; this.model = model; this.rarity = rarity; this.maxStack = maxStack;
		this.glint = glint; this.fireRes = fireRes; this.desc = desc; this.desc2 = desc2;
		this.descColor = descColor;
		this.desc2Color = Double.isNaN(damage) ? NamedTextColor.DARK_GRAY : NamedTextColor.GRAY;
		this.damage = damage; this.speed = speed; this.cooldown = cooldown;
	}

	public boolean isWeapon() {
		return !Double.isNaN(damage);
	}

	public Key cooldownGroup() {
		return Key.key(Keys.NS, id);
	}

	public ItemStack create() {
		return create(1);
	}

	@SuppressWarnings("UnstableApiUsage")
	public ItemStack create(int amount) {
		ItemStack s = ItemStack.of(base);
		s.editPersistentDataContainer(pdc -> { pdc.set(Keys.ID, PersistentDataType.STRING, id); if (!Double.isNaN(damage)) pdc.set(ItemUtil.ITEM_REV, PersistentDataType.INTEGER, ItemUtil.ITEM_REVISION); });
		s.setData(DataComponentTypes.ITEM_NAME, Component.text(name));
		if (model != null) s.setData(DataComponentTypes.ITEM_MODEL, Key.key("minecraft", model));
		s.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(Keys.NS + ":" + id).build());
		s.setData(DataComponentTypes.RARITY, rarity);
		s.setData(DataComponentTypes.MAX_STACK_SIZE, maxStack);
		if (glint) s.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		if (fireRes) s.setData(DataComponentTypes.DAMAGE_RESISTANT, DamageResistant.damageResistant(DamageTypeTagKeys.IS_FIRE));
		if (isWeapon()) {
			s.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(cooldown / 20f).cooldownGroup(cooldownGroup()).build());
			s.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemUtil.baseModifiers(this, base));
		}
		if (this == GUIDE) Guide.write(s);
		ItemUtil.refreshLore(s);
		s.setAmount(Math.max(1, Math.min(amount, maxStack)));
		return s;
	}

	public static HItem byId(String id) {
		if (id == null) return null;
		for (HItem h : values()) if (h.id.equals(id)) return h;
		return null;
	}

	public static HItem of(ItemStack s) {
		return ItemUtil.hitem(s);
	}

	public boolean is(ItemStack s) {
		return ItemUtil.hitem(s) == this;
	}
}
