package com.hobbang.enhance.item;

import com.hobbang.enhance.Keys;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.2.0 강화 단계 효과표 — 장비 종류별 속성(attribute modifier)과 +5/+10/+15 마일스톤 특수 효과.
 * 속성은 ItemUtil.applyStats 가 매번 처음부터 다시 계산한다(키 고정: hobbang:enhance_*), 그래서 강화·하락 어느 쪽이든 중복이 생기지 않는다.
 * 특수 효과(출혈 등)는 이벤트에서 현재 단계만 읽으므로 따로 저장하지 않는다(→ EnhanceEffects).
 */
@SuppressWarnings("UnstableApiUsage")
public final class EnhanceTier {
	private EnhanceTier() {}

	public enum Gear { SWORD, TOOL, MACE, TRIDENT, BOW, CROSSBOW, SHIELD, HEAD, CHEST, LEGS, FEET, BOSS, WEAPON }

	public static Gear gear(ItemStack s) {
		if (s == null || s.getType().isAir()) return null;
		HItem h = ItemUtil.hitem(s);
		if (h != null) return h.isWeapon() ? Gear.BOSS : null;
		Material m = s.getType();
		EquipmentSlot slot = ItemUtil.armorSlot(s);
		if (slot != null) return switch (slot) { case HEAD -> Gear.HEAD; case CHEST -> Gear.CHEST; case LEGS -> Gear.LEGS; default -> Gear.FEET; };
		String n = m.name();
		if (m == Material.MACE) return Gear.MACE;
		if (m == Material.TRIDENT) return Gear.TRIDENT;
		if (m == Material.BOW) return Gear.BOW;
		if (m == Material.CROSSBOW) return Gear.CROSSBOW;
		if (m == Material.SHIELD) return Gear.SHIELD;
		if (n.endsWith("_SWORD") || n.endsWith("_SPEAR")) return Gear.SWORD;
		if (ItemUtil.isTool(s)) return Gear.TOOL;
		if (ItemUtil.isWeapon(s)) return Gear.WEAPON;
		return null;
	}

	/** 마일스톤 단계 수: +5 → 1, +10 → 2, +15 → 3 */
	public static int tier(int l) { return l >= 15 ? 3 : l >= 10 ? 2 : l >= 5 ? 1 : 0; }

	// ───────── 수치 ─────────
	/** v1.2.0 하향: 단계당 +0.3(1~5) / +0.5(6~10) / +0.7(11~15) → +15 = 7.5 (이전 15) */
	public static double weaponDamage(int l) {
		double d = 0;
		for (int i = 1; i <= l; i++) d += i <= 5 ? 0.3 : i <= 10 ? 0.5 : 0.7;
		return Math.round(d * 10) / 10.0;
	}
	public static double armorValue(int l) { double d = 0; for (int i = 1; i <= l; i++) d += i <= 10 ? 0.3 : 0.6; return Math.round(d * 10) / 10.0; }
	public static double armorToughness(int l) { return Math.max(0, l - 5) * 0.25; }
	public static double toolEfficiency(int l) { return l * 1.5; }

	static final double[] CHEST_HP = {0, 2, 6, 10};
	static final double[] HELMET_OXY = {0, 1, 3, 9};
	static final double[] LEGS_SPEED = {0, 0.03, 0.06, 0.10};
	static final double[] BOOTS_SAFE = {0, 1, 2, 4};
	static final double[] BOOTS_FALL = {0, 0.10, 0.20, 0.35};
	static final double[] SHIELD_KB = {0, 0.1, 0.2, 0.3};
	static final double[] TRIDENT_SWIM = {0, 0.33, 0.66, 1.0};
	public static final double[] TOOL_SAVE = {0, 0.10, 0.25, 0.40};
	public static final double[] ARROW_SAVE = {0, 0.20, 0.35, 0.50};
	public static final double[] REFLECT = {0, 0, 0.15, 0.30};
	public static final double BLEED_CHANCE = 0.20, PIERCE_CHANCE = 0.15, PIERCE_RATIO = 0.25, SIGNATURE_RATIO = 0.35;

	/** 보스 무기 스킬 배율 */
	public static double bossDamageMult(int l) { return 1 + 0.04 * l; }          // +15 = ×1.6
	public static double bossRadiusAdd(int l) { return 0.1 * l; }                // +15 = +1.5칸
	public static double bossCooldownMult(int l) { return 1 - 0.02 * l; }        // +15 = −30%

	private static NamespacedKey k(String p) { return Keys.k(p); }
	private static AttributeModifier mod(String key, double v, AttributeModifier.Operation op, EquipmentSlotGroup g) { return new AttributeModifier(k(key), v, op, g); }

	/** 강화 보너스 속성 추가 (ItemUtil.applyStats 에서 기본값 다음에) */
	public static void addModifiers(ItemStack s, int l, ItemAttributeModifiers.Builder b) {
		if (l <= 0) return;
		Gear g = gear(s);
		if (g == null) return;
		int t = tier(l);
		var ADD = AttributeModifier.Operation.ADD_NUMBER;
		var BASE = AttributeModifier.Operation.ADD_SCALAR;            // = add_multiplied_base
		var TOTAL = AttributeModifier.Operation.MULTIPLY_SCALAR_1;    // = add_multiplied_total
		EquipmentSlotGroup MH = EquipmentSlotGroup.MAINHAND, HAND = EquipmentSlotGroup.HAND;
		if (ItemUtil.isWeapon(s)) {
			b.addModifier(Attribute.ATTACK_DAMAGE, mod("enhance_damage", weaponDamage(l), ADD, MH), MH);
			if (l >= 10) b.addModifier(Attribute.ATTACK_SPEED, mod("enhance_speed", l >= 15 ? 0.10 : 0.05, TOTAL, MH), MH);
		}
		switch (g) {
			case HEAD, CHEST, LEGS, FEET -> {
				EquipmentSlot slot = ItemUtil.armorSlot(s);
				EquipmentSlotGroup sg = slot.getGroup();
				String n = switch (g) { case HEAD -> "head"; case CHEST -> "chest"; case LEGS -> "legs"; default -> "feet"; };
				b.addModifier(Attribute.ARMOR, mod("enhance_armor_" + n, armorValue(l), ADD, sg), sg);
				if (armorToughness(l) > 0) b.addModifier(Attribute.ARMOR_TOUGHNESS, mod("enhance_toughness_" + n, armorToughness(l), ADD, sg), sg);
				if (l >= 15) b.addModifier(Attribute.KNOCKBACK_RESISTANCE, mod("enhance_kb_" + n, 0.1, ADD, sg), sg);
				if (t > 0) switch (g) {
					case CHEST -> b.addModifier(Attribute.MAX_HEALTH, mod("enhance_health_chest", CHEST_HP[t], ADD, sg), sg);
					case HEAD -> {
						b.addModifier(Attribute.OXYGEN_BONUS, mod("enhance_oxygen_head", HELMET_OXY[t], ADD, sg), sg);
						if (t >= 3) b.addModifier(Attribute.SUBMERGED_MINING_SPEED, mod("enhance_submerged_head", 0.8, ADD, sg), sg);
					}
					case LEGS -> b.addModifier(Attribute.MOVEMENT_SPEED, mod("enhance_speed_legs", LEGS_SPEED[t], BASE, sg), sg);
					default -> {
						b.addModifier(Attribute.SAFE_FALL_DISTANCE, mod("enhance_safe_fall_feet", BOOTS_SAFE[t], ADD, sg), sg);
						b.addModifier(Attribute.FALL_DAMAGE_MULTIPLIER, mod("enhance_fall_feet", -BOOTS_FALL[t], TOTAL, sg), sg);
					}
				}
			}
			case TOOL -> {
				b.addModifier(Attribute.MINING_EFFICIENCY, mod("enhance_mining", toolEfficiency(l), ADD, MH), MH);
				if (t >= 3) b.addModifier(Attribute.BLOCK_BREAK_SPEED, mod("enhance_break_speed", 0.10, BASE, MH), MH);
			}
			case SWORD -> { if (t >= 1) b.addModifier(Attribute.SWEEPING_DAMAGE_RATIO, mod("enhance_sweep", 0.25, ADD, MH), MH); }
			case MACE -> {
				if (t >= 1) b.addModifier(Attribute.SAFE_FALL_DISTANCE, mod("enhance_mace_safe_fall", 3, ADD, MH), MH);
				if (t >= 2) b.addModifier(Attribute.FALL_DAMAGE_MULTIPLIER, mod("enhance_mace_fall", t >= 3 ? -1.0 : -0.5, TOTAL, MH), MH);
			}
			case TRIDENT -> {
				if (t >= 1) b.addModifier(Attribute.WATER_MOVEMENT_EFFICIENCY, mod("enhance_swim", TRIDENT_SWIM[t], ADD, MH), MH);
				if (t >= 2) b.addModifier(Attribute.OXYGEN_BONUS, mod("enhance_trident_oxygen", 2, ADD, MH), MH);
				if (t >= 3) b.addModifier(Attribute.SUBMERGED_MINING_SPEED, mod("enhance_trident_submerged", 0.8, ADD, MH), MH);
			}
			case SHIELD -> { if (t >= 1) b.addModifier(Attribute.KNOCKBACK_RESISTANCE, mod("enhance_shield_kb", SHIELD_KB[t], ADD, HAND), HAND); }
			default -> {}
		}
	}

	private static String pc(double v) { return Math.round(v * 100) + "%"; }
	private static String num(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

	/** 마일스톤 t(1~3)의 효과 설명 (해당 단계에서 "갖게 되는 것" 전체) */
	public static String milestone(Gear g, int t) {
		if (t <= 0) return null;
		return switch (g) {
			case CHEST -> "최대 체력 +" + num(CHEST_HP[t]);
			case HEAD -> "산소 보너스 +" + num(HELMET_OXY[t]) + (t >= 3 ? " · 물속 채굴 속도 정상" : "");
			case LEGS -> "이동 속도 +" + pc(LEGS_SPEED[t]);
			case FEET -> "안전 낙하 +" + num(BOOTS_SAFE[t]) + "칸 · 낙하 피해 −" + pc(BOOTS_FALL[t]);
			case SWORD -> switch (t) {
				case 1 -> "휩쓸기 피해 +25% · 출혈 20% (3초간 3 피해)";
				case 2 -> "+ 관통 15%: 피해 25% 추가(방어 무시)";
				default -> "+ 장인의 일격: 풀차지 공격 시 주변 적에게 35% (3초)";
			};
			case TOOL -> "내구도 보존 " + pc(TOOL_SAVE[t]) + (t >= 3 ? " · 블록 파괴 속도 +10%" : "");
			case MACE -> switch (t) { case 1 -> "들고 있을 때 안전 낙하 +3칸"; case 2 -> "+ 낙하 피해 −50%"; default -> "+ 낙하 피해 없음"; };
			case TRIDENT -> switch (t) { case 1 -> "들고 있을 때 수영 속도 +33%"; case 2 -> "수영 +66% · 산소 보너스 +2"; default -> "수영 +100% · 산소 +2 · 물속 채굴 정상"; };
			case BOW, CROSSBOW -> "화살 보존 " + pc(ARROW_SAVE[t]);
			case SHIELD -> "밀려남 저항 +" + pc(SHIELD_KB[t]) + (REFLECT[t] > 0 ? " · 막을 때 발사체 반사 " + pc(REFLECT[t]) : "");
			case BOSS -> switch (t) {
				case 1 -> "스킬 강화 (아래 수치)";
				case 2 -> "+ 스킬 추가 효과";
				default -> "+ 스킬 최종 효과";
			};
			default -> null;
		};
	}

	/** 보스 무기 단계별 스킬 설명 */
	public static String bossSkill(HItem h, int l) {
		String common = "스킬 피해 ×" + String.format("%.2f", bossDamageMult(l)) + " · 범위 +" + String.format("%.1f", bossRadiusAdd(l)) + "칸 · 재사용 −" + Math.round((1 - bossCooldownMult(l)) * 100) + "%";
		String extra = switch (h) {
			case SMITH_HAMMER -> l >= 15 ? "여진 2회 + 구속" : l >= 10 ? "여진 1회" : null;
			case CRYSTAL_GREATSWORD -> "수정 가시 " + (8 + l / 5) + "개";
			case STORMCALLER -> l >= 15 ? "낙뢰 연쇄(주변 2마리)" : l >= 10 ? "낙뢰 연쇄(주변 1마리)" : null;
			case CHRONO_DAGGER -> "정지 " + String.format("%.1f", (80 + 4 * l) / 20.0) + "초" + (l >= 10 ? " · 정지 시 피해" : "");
			default -> null;
		};
		return extra == null ? common : common + " · " + extra;
	}

	/** 장비 설명(lore)용: 마일스톤 3줄 (달성 ✔ / 미달성 🔒) */
	public static List<String[]> loreLines(ItemStack s, int l) {
		List<String[]> out = new ArrayList<>();
		Gear g = gear(s);
		if (g == null || g == Gear.WEAPON) return out;
		if (g == Gear.BOSS) {
			HItem h = ItemUtil.hitem(s);
			if (l > 0 && h != null) out.add(new String[]{"on", "◆ " + bossSkill(h, l)});
			return out;
		}
		int[] ms = {5, 10, 15};
		for (int i = 0; i < 3; i++) {
			String d = milestone(g, i + 1);
			if (d == null) continue;
			out.add(new String[]{l >= ms[i] ? "on" : "off", (l >= ms[i] ? "◆ +" : "◇ +") + ms[i] + " " + d});
		}
		return out;
	}

	/** 강화 창: 성공하면 새로 생기는 효과 (없으면 null) */
	public static String gainAt(ItemStack s, int next) {
		Gear g = gear(s);
		if (g == null) return null;
		if (g == Gear.BOSS) { HItem h = ItemUtil.hitem(s); return h == null ? null : bossSkill(h, next); }
		if (next == 5 || next == 10 || next == 15) return milestone(g, tier(next));
		return null;
	}

	/** 다음 마일스톤 단계 (없으면 -1) */
	public static int nextMilestone(int l) { return l < 5 ? 5 : l < 10 ? 10 : l < 15 ? 15 : -1; }

	public static boolean ranged(ItemStack s) { Material m = s.getType(); return m == Material.BOW || m == Material.CROSSBOW || m == Material.SHIELD; }

	static boolean isPickTag(Material m) { return Tag.ITEMS_PICKAXES.isTagged(m); }
}
