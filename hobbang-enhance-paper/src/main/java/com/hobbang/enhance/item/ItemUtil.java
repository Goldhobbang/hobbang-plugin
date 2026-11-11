package com.hobbang.enhance.item;

import com.hobbang.enhance.Keys;
import com.hobbang.enhance.enhance.EnhanceConfig;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** 아이템 판별 / 강화 단계 저장 / 스탯·이름·설명 갱신 (Fabric EnhanceLogic 의 아이템 부분 포팅) */
@SuppressWarnings("UnstableApiUsage")
public final class ItemUtil {
	private ItemUtil() {}

	public static final int MAX_LEVEL = 15;

	// ───────── 판별 ─────────
	public static HItem hitem(ItemStack s) {
		if (s == null || s.getType().isAir()) return null;
		String id = s.getPersistentDataContainer().get(Keys.ID, PersistentDataType.STRING);
		return id == null ? null : HItem.byId(id);
	}

	public static boolean isHobbang(ItemStack s) {
		if (s == null || s.getType().isAir()) return false;
		return s.getPersistentDataContainer().has(Keys.ID);
	}

	/** 바닐라 기본 속성 중 공격력이 있으면 무기 (Fabric: 아이템 기본 컴포넌트 기준) */
	public static boolean isWeapon(ItemStack s) {
		HItem h = hitem(s);
		if (h != null) return h.isWeapon();
		ItemAttributeModifiers m = ItemStack.of(s.getType()).getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
		if (m == null) return false;
		for (ItemAttributeModifiers.Entry e : m.modifiers()) if (e.attribute() == Attribute.ATTACK_DAMAGE) return true;
		return false;
	}

	public static EquipmentSlot armorSlot(ItemStack s) {
		Equippable eq = s.getData(DataComponentTypes.EQUIPPABLE);
		if (eq == null) return null;
		return switch (eq.slot()) {
			case HEAD, CHEST, LEGS, FEET -> eq.slot();
			default -> null;
		};
	}

	public static boolean isTool(ItemStack s) {
		Material m = s.getType();
		return s.hasData(DataComponentTypes.TOOL) && (Tag.ITEMS_PICKAXES.isTagged(m) || Tag.ITEMS_SHOVELS.isTagged(m)
			|| Tag.ITEMS_HOES.isTagged(m) || Tag.ITEMS_AXES.isTagged(m));
	}

	public static boolean canEnhance(ItemStack s) {
		if (s == null || s.getType().isAir() || s.getAmount() != 1) return false;
		if (!s.hasData(DataComponentTypes.MAX_DAMAGE)) return false;
		HItem h = hitem(s);
		if (h != null && !h.isWeapon()) return false;
		return isWeapon(s) || armorSlot(s) != null || isTool(s) || EnhanceTier.ranged(s);
	}

	public static int getLevel(ItemStack s) {
		if (s == null || s.getType().isAir()) return 0;
		return s.getPersistentDataContainer().getOrDefault(Keys.LEVEL, PersistentDataType.INTEGER, 0);
	}

	public static int getArtisan(ItemStack s) {
		if (s == null || s.getType().isAir()) return 0;
		return s.getPersistentDataContainer().getOrDefault(Keys.ARTISAN, PersistentDataType.INTEGER, 0);
	}

	public static void setArtisan(ItemStack s, int v) {
		s.editPersistentDataContainer(p -> {
			if (v <= 0) p.remove(Keys.ARTISAN);
			else p.set(Keys.ARTISAN, PersistentDataType.INTEGER, Math.min(1000, v));
		});
		refreshLore(s);
	}

	// ───────── 스탯 ─────────
	public static double weaponDamage(int l) { return EnhanceTier.weaponDamage(l); }
	public static double armorValue(int l) { return EnhanceTier.armorValue(l); }
	public static double armorToughness(int l) { return EnhanceTier.armorToughness(l); }
	public static double toolEfficiency(int l) { return EnhanceTier.toolEfficiency(l); }

	/** 유물 무기의 고유 공격력/속도 (Fabric .sword(NETHERITE, dmg, speed) 와 같은 값) */
	public static ItemAttributeModifiers baseModifiers(HItem h, Material base) {
		ItemAttributeModifiers.Builder b = ItemAttributeModifiers.itemAttributes();
		b.addModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(NamespacedKey.minecraft("base_attack_damage"), h.damage,
			AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
		b.addModifier(Attribute.ATTACK_SPEED, new AttributeModifier(NamespacedKey.minecraft("base_attack_speed"), h.speed,
			AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
		if (h == HItem.CHRONO_DAGGER)   // 크로노 낫: 긴 자루 = 사거리 +1
			b.addModifier(Attribute.ENTITY_INTERACTION_RANGE, new AttributeModifier(NamespacedKey.minecraft("base_entity_interaction_range"), 1.0,
				AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
		return b.build();
	}

	/** 아이템 고유(강화 전) 속성 */
	private static List<ItemAttributeModifiers.Entry> defaults(ItemStack s) {
		HItem h = hitem(s);
		ItemAttributeModifiers m = h != null && h.isWeapon() ? baseModifiers(h, s.getType())
			: ItemStack.of(s.getType()).getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
		return m == null ? List.of() : m.modifiers();
	}

	/** 강화 단계를 설정하고 속성/광택/이름/설명을 다시 계산 */
	public static void setLevel(ItemStack s, int level) {
		final int lv = Math.max(0, Math.min(MAX_LEVEL, level));
		s.editPersistentDataContainer(p -> {
			if (lv == 0) p.remove(Keys.LEVEL);
			else p.set(Keys.LEVEL, PersistentDataType.INTEGER, lv);
		});
		applyStats(s, lv);
		HItem h = hitem(s);
		if (lv >= 10) s.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		else if (h == null || !h.glint) s.resetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
		refreshName(s);
		refreshLore(s);
	}

	/** 결과 아이템의 고유 속성으로 되돌린 뒤 강화 보너스 재적용 (대장장이 작업대 변환용) */
	public static void reapply(ItemStack s) {
		setLevel(s, getLevel(s));
	}

	private static NamespacedKey mk(String p) {
		return Keys.k(p);
	}

	private static void applyStats(ItemStack s, int l) {
		List<ItemAttributeModifiers.Entry> base = defaults(s);
		ItemAttributeModifiers cur = s.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
		ItemAttributeModifiers.Builder b = ItemAttributeModifiers.itemAttributes();
		// 다른 플러그인/명령어가 붙인 모디파이어는 보존, 우리 것과 기본값은 새로 계산
		if (cur != null) for (ItemAttributeModifiers.Entry e : cur.modifiers()) {
			NamespacedKey k = e.modifier().getKey();
			if (k.getNamespace().equals(Keys.NS)) continue;
			if (k.getNamespace().equals("minecraft")) continue;
			b.addModifier(e.attribute(), e.modifier(), e.getGroup());
		}
		for (ItemAttributeModifiers.Entry e : base) b.addModifier(e.attribute(), e.modifier(), e.getGroup());
		EnhanceTier.addModifiers(s, l, b);
		s.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, b.build());
		s.editPersistentDataContainer(p -> { if (l > 0) p.set(STAT_VER, PersistentDataType.INTEGER, STAT_VERSION); else p.remove(STAT_VER); });
	}

	// ───────── 이름 / 설명 ─────────
	public static NamedTextColor tierColor(int l) {
		if (l >= 15) return NamedTextColor.DARK_RED;
		if (l >= 12) return NamedTextColor.RED;
		if (l >= 10) return NamedTextColor.GOLD;
		if (l >= 8) return NamedTextColor.LIGHT_PURPLE;
		if (l >= 5) return NamedTextColor.AQUA;
		return NamedTextColor.GREEN;
	}

	public static Component levelTag(int l) {
		Component c = Component.text((l >= 15 ? "★" : "") + "[+" + l + "] ", tierColor(l));
		if (l >= 15) c = c.decorate(TextDecoration.BOLD);
		return c;
	}

	public static String stars(int l) {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= MAX_LEVEL; i++) {
			sb.append(i <= l ? '★' : '☆');
			if (i % 5 == 0 && i < MAX_LEVEL) sb.append(' ');
		}
		return sb.toString();
	}

	/** 강화 태그 없는 원래 이름 */
	public static Component baseName(ItemStack s) {
		String json = s.getPersistentDataContainer().get(Keys.BASE_NAME, PersistentDataType.STRING);
		if (json != null) {
			try { return GsonComponentSerializer.gson().deserialize(json); } catch (Exception ignored) {}
		}
		Component n = s.getData(DataComponentTypes.ITEM_NAME);
		if (n != null) return n;
		return Component.translatable(s.getType().translationKey());
	}

	/** 채팅용 이름 (흰색) */
	public static Component plainName(ItemStack s) {
		return baseName(s).colorIfAbsent(NamedTextColor.WHITE);
	}

	/** 강화 단계가 있으면 custom_name = [+N] + 원래 이름 (Fabric 의 getStyledHoverName 믹스인 대체) */
	public static void refreshName(ItemStack s) {
		int l = getLevel(s);
		PersistentDataContainerView pdc = s.getPersistentDataContainer();
		Component custom = s.getData(DataComponentTypes.CUSTOM_NAME);
		boolean hadBase = pdc.has(Keys.BASE_NAME);
		if (l > 0) {
			// 처음 강화될 때 이름표로 바꾼 이름이 있으면 기억해 둔다
			if (!hadBase && custom != null && !looksTagged(custom)) {
				String json = GsonComponentSerializer.gson().serialize(custom);
				s.editPersistentDataContainer(p -> p.set(Keys.BASE_NAME, PersistentDataType.STRING, json));
			}
			Component base = baseName(s);
			Component name = Component.empty().decoration(TextDecoration.ITALIC, false).append(levelTag(l));
			Component bn = base;
			if (bn.color() == null) bn = bn.color(rarityColor(s));
			s.setData(DataComponentTypes.CUSTOM_NAME, name.append(bn));
		} else {
			if (hadBase) {
				Component base = baseName(s);
				s.editPersistentDataContainer(p -> p.remove(Keys.BASE_NAME));
				s.setData(DataComponentTypes.CUSTOM_NAME, base);
			} else if (custom != null && pdc.has(Keys.ID) && hitem(s) != null && looksTagged(custom)) {
				s.resetData(DataComponentTypes.CUSTOM_NAME);
			} else if (custom != null && looksTagged(custom)) {
				s.resetData(DataComponentTypes.CUSTOM_NAME);
			}
		}
	}

	private static boolean looksTagged(Component c) {
		String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
		return plain.startsWith("[+") || plain.startsWith("★[+");
	}

	private static NamedTextColor rarityColor(ItemStack s) {
		org.bukkit.inventory.ItemRarity r = s.getData(DataComponentTypes.RARITY);
		if (r == null) return NamedTextColor.WHITE;
		return switch (r) {
			case UNCOMMON -> NamedTextColor.YELLOW;
			case RARE -> NamedTextColor.AQUA;
			case EPIC -> NamedTextColor.LIGHT_PURPLE;
			default -> NamedTextColor.WHITE;
		};
	}

	private static Component line(String text, net.kyori.adventure.text.format.TextColor color) {
		return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
	}

	/** 설명(lore) 다시 쓰기: 강화 별/다음 확률 + 아이템 고유 설명 */
	public static void refreshLore(ItemStack s) {
		HItem h = hitem(s);
		int l = getLevel(s);
		List<Component> lines = new ArrayList<>();
		boolean gear = canEnhanceType(s, h);
		if (gear && l > 0) {
			lines.add(line(stars(l), tierColor(l)));
			if (l < MAX_LEVEL) {
				EnhanceConfig.Stage st = EnhanceConfig.CFG.stages[l];
				lines.add(line(String.format("다음 강화 %.0f%%  ·  장인의 기운 %.1f%%", st.success, getArtisan(s) / 10.0), NamedTextColor.DARK_AQUA));
			} else {
				lines.add(line("최대 강화 달성!", NamedTextColor.LIGHT_PURPLE));
			}
		} else if (gear && h != null) {
			lines.add(line("강화 가능 · 강화대에서 강화", NamedTextColor.DARK_GRAY));
		} else if (gear && getArtisan(s) > 0) {
			lines.add(line(String.format("장인의 기운 %.1f%%", getArtisan(s) / 10.0), NamedTextColor.DARK_AQUA));
		}
		if (gear && l > 0) for (String[] e : EnhanceTier.loreLines(s, l))
			lines.add(line(e[1], e[0].equals("on") ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
		if (h != null) {
			if (h.desc != null) lines.add(line(h.desc, h.descColor));
			if (h.desc2 != null) lines.add(line(h.desc2, h.desc2Color));
		}
		if (lines.isEmpty()) {
			if (s.hasData(DataComponentTypes.LORE) && l == 0 && h == null) {
				// 우리가 붙였던 설명만 지운다 (별 줄로 시작)
				ItemLore cur = s.getData(DataComponentTypes.LORE);
				if (cur != null && !cur.lines().isEmpty()) {
					String first = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(cur.lines().get(0));
					if (first.startsWith("★") || first.startsWith("☆") || first.startsWith("장인의 기운")) s.resetData(DataComponentTypes.LORE);
				}
			}
			return;
		}
		s.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
	}

	private static boolean canEnhanceType(ItemStack s, HItem h) {
		if (h != null) return h.isWeapon();
		if (!s.hasData(DataComponentTypes.MAX_DAMAGE)) return false;
		return isWeapon(s) || armorSlot(s) != null || isTool(s) || EnhanceTier.ranged(s);
	}

	/** v1.2.0 속성표 버전. 예전 버전으로 강화된 장비는 접속·상자 열기 때 다시 계산된다 */
	public static final int STAT_VERSION = 2;
	public static final NamespacedKey STAT_VER = Keys.k("stat_ver");

	/** v1.4.0 무기 정체성 버전: 3 = 뇌운검→뇌운 전투도끼(네더라이트 도끼), 크로노 단검→크로노 낫 */
	public static final int ITEM_REVISION = 3;
	public static final NamespacedKey ITEM_REV = Keys.k("item_rev");

	/** 예전 보스 무기(기반 아이템/이름/모델이 바뀐 것)를 새 정체성으로. 강화 단계·인첸트·내구도·이름표·PDC 는 그대로. 바뀐 새 스택 또는 null */
	@SuppressWarnings("UnstableApiUsage")
	public static ItemStack migrateIdentity(ItemStack s) {
		if (s == null || s.getType().isAir()) return null;
		HItem h = hitem(s);
		if (h == null || !h.isWeapon()) return null;
		Integer r = s.getPersistentDataContainer().get(ITEM_REV, PersistentDataType.INTEGER);
		if (r != null && r >= ITEM_REVISION) return null;
		ItemStack n = s.getType() == h.base ? s : s.withType(h.base);
		n.setData(DataComponentTypes.ITEM_NAME, net.kyori.adventure.text.Component.text(h.name));
		if (h.model != null) n.setData(DataComponentTypes.ITEM_MODEL, net.kyori.adventure.key.Key.key("minecraft", h.model));
		n.setData(DataComponentTypes.CUSTOM_MODEL_DATA, io.papermc.paper.datacomponent.item.CustomModelData.customModelData().addString(Keys.NS + ":" + h.id).build());
		n.editPersistentDataContainer(p -> p.set(ITEM_REV, PersistentDataType.INTEGER, ITEM_REVISION));
		reapply(n);   // 속성(공격력/속도/사거리) + 이름 + 설명 다시 계산
		return n;
	}

	/** 강화된 장비인데 속성표가 예전 것이면 다시 계산. 바뀌었으면 true */
	public static boolean migrate(ItemStack s) {
		if (s == null || s.getType().isAir() || getLevel(s) <= 0) return false;
		Integer v = s.getPersistentDataContainer().get(STAT_VER, PersistentDataType.INTEGER);
		if (v != null && v >= STAT_VERSION) return false;
		reapply(s);
		return true;
	}
}
