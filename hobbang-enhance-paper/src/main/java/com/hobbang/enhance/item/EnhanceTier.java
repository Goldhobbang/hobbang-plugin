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

	/** 마일스톤 단계 수: +5 → 1, +10 → 2, +15 → 3, +20 → 4 */
	public static int tier(int l) { return l >= 20 ? 4 : l >= 15 ? 3 : l >= 10 ? 2 : l >= 5 ? 1 : 0; }

	// ───────── 수치 ─────────
	/** 단계당 +0.3(1~5) / +0.5(6~10) / +0.7(11~15) / +0.9(16~20) → +15 = 7.5, +20 = 12.0 */
	public static double weaponDamage(int l) {
		double d = 0;
		for (int i = 1; i <= l; i++) d += i <= 5 ? 0.3 : i <= 10 ? 0.5 : i <= 15 ? 0.7 : 0.9;
		return Math.round(d * 10) / 10.0;
	}
	/** 수정 대검 공격력 증가: 1~14강 = 0, 15강부터 강화당 +2 */
	public static double crystalDamage(int l) { return l >= 15 ? (l - 14) * 2.0 : 0.0; }
	public static double weaponDamage(ItemStack s, int l) {
		return ItemUtil.hitem(s) == HItem.CRYSTAL_GREATSWORD ? crystalDamage(l) : weaponDamage(l);
	}
	public static double armorValue(int l) { double d = 0; for (int i = 1; i <= l; i++) d += i <= 10 ? 0.3 : i <= 15 ? 0.6 : 0.8; return Math.round(d * 10) / 10.0; }
	public static double armorToughness(int l) { return Math.max(0, l - 5) * 0.25; }
	public static double toolEfficiency(int l) { return l * 1.5; }

	static final double[] CHEST_HP = {0, 2, 6, 10, 14};
	static final double[] HELMET_OXY = {0, 1, 3, 9, 12};
	static final double[] LEGS_SPEED = {0, 0.03, 0.06, 0.10, 0.14};
	static final double[] BOOTS_SAFE = {0, 1, 2, 4, 6};
	static final double[] BOOTS_FALL = {0, 0.10, 0.20, 0.35, 0.50};
	static final double[] SHIELD_KB = {0, 0.1, 0.2, 0.3, 0.4};
	static final double[] TRIDENT_SWIM = {0, 0.33, 0.66, 1.0, 1.33};
	public static final double[] TOOL_SAVE = {0, 0.10, 0.25, 0.40, 0.55};
	public static final double[] ARROW_SAVE = {0, 0.20, 0.35, 0.50, 0.65};
	public static final double[] REFLECT = {0, 0, 0.15, 0.30, 0.45};
	public static final double BLEED_CHANCE = 0.20, PIERCE_CHANCE = 0.15, PIERCE_RATIO = 0.25, SIGNATURE_RATIO = 0.35;

	/** 보스 무기 기본 스킬이 열리는 강화 단계 (t384: 모든 보스 무기 3강 해금) */
	public static final int BOSS_SKILL_UNLOCK = 3;
	public static boolean bossSkillUnlocked(int l) { return l >= BOSS_SKILL_UNLOCK; }
	/** 스킬 강화 단계: 해금(3강) = 0, 그 위로 강화마다 +1 (3강 미만 0) */
	public static int bossSkillLv(int l) { return Math.max(0, l - BOSS_SKILL_UNLOCK); }

	/** 보스 무기 스킬 배율 — 3강(해금) 기본값에서 시작해 강화마다 오른다 */
	public static double bossDamageMult(int l) { return 1 + 0.04 * bossSkillLv(l); }          // +15 = ×1.48
	public static double bossRadiusAdd(int l) { return 0.1 * bossSkillLv(l); }                // +15 = +1.2칸
	public static double bossCooldownMult(int l) { return Math.max(0.50, 1 - 0.02 * bossSkillLv(l)); }        // +15 = −24%, +20 = −34%
	public static double bossCooldownMult(HItem h, int l) {
		double step = h == HItem.SMITH_HAMMER ? 0.01 : 0.02;
		return Math.max(0.50, 1 - step * bossSkillLv(l));
	}
	/** 수정 대검 관통 베기(돌진) 거리: 3강 10칸, 이후 강화당 +1칸 (최대 20칸) */
	public static double crystalPierceRange(int l) { return Math.min(20.0, 10.0 + bossSkillLv(l)); }

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
			double dmg = weaponDamage(s, l);
			if (dmg > 0) b.addModifier(Attribute.ATTACK_DAMAGE, mod("enhance_damage", dmg, ADD, MH), MH);
			if (l >= 10) b.addModifier(Attribute.ATTACK_SPEED, mod("enhance_speed", l >= 20 ? 0.15 : l >= 15 ? 0.10 : 0.05, TOTAL, MH), MH);
		}
		switch (g) {
			case HEAD, CHEST, LEGS, FEET -> {
				EquipmentSlot slot = ItemUtil.armorSlot(s);
				EquipmentSlotGroup sg = slot.getGroup();
				String n = switch (g) { case HEAD -> "head"; case CHEST -> "chest"; case LEGS -> "legs"; default -> "feet"; };
				b.addModifier(Attribute.ARMOR, mod("enhance_armor_" + n, armorValue(l), ADD, sg), sg);
				if (armorToughness(l) > 0) b.addModifier(Attribute.ARMOR_TOUGHNESS, mod("enhance_toughness_" + n, armorToughness(l), ADD, sg), sg);
				if (l >= 15) b.addModifier(Attribute.KNOCKBACK_RESISTANCE, mod("enhance_kb_" + n, l >= 20 ? 0.15 : 0.1, ADD, sg), sg);
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

	private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v); }

	/** 보스 무기 단계별 스킬 요약 (강화 창 미리보기용) */
	public static String bossSkill(HItem h, int l) {
		double range = h == HItem.CHRONO_DAGGER ? bossRadiusAdd(l) * 2
			: h == HItem.SMITH_HAMMER ? 0.05 * bossSkillLv(l) : bossRadiusAdd(l);
		double dmgMult = h == HItem.SMITH_HAMMER ? (1.0 + com.hobbang.enhance.weapon.HammerSkill.ENHANCE_DMG_STEP * bossSkillLv(l)) : bossDamageMult(l);
		String common = "스킬 피해 ×" + String.format("%.2f", dmgMult) + " · 범위 +" + String.format("%.2f", range) + "칸 · 재사용 −" + Math.round((1 - bossCooldownMult(h, l)) * 100) + "%";
		if (!bossSkillUnlocked(l)) return "스킬 잠김 — +" + BOSS_SKILL_UNLOCK + "강에서 해금";
		String extra = switch (h) {
			case SMITH_HAMMER -> (bossSkillLv(l) / 5 > 0 ? "지진 범위 +" + fmt(0.5 * (bossSkillLv(l) / 5)) + "칸" : "")
				+ (l >= 15 ? " · 천공 내려찍기(" + (20 + 5 * (l - 15)) + "칸) · 구속" : "")
				+ (l >= 10 ? " · [영혼 수집] 처치당 최대 체력의 " + (5 + l - 10) + "%" : "");
			case CRYSTAL_GREATSWORD -> "관통 베기 " + num(crystalPierceRange(l)) + "칸(50%)" + (l >= 7 ? " + 대검 낙하 150%" : "")
				+ (l >= 15 ? " · 수정 가시 " + com.hobbang.enhance.weapon.CrystalSkill.spikeCount(l) + "개 × " + (l >= 13 ? 4 : l >= 10 ? 3 : 1) + "갈래" : "");
			case STORMCALLER -> l >= 10 ? "낙뢰 연쇄 " + (l - 9) + "마리 · 심판: 범위 " + fmt(com.hobbang.enhance.weapon.StormSkill.judgeRadius(l))
				+ "칸 · 대기 " + fmt(com.hobbang.enhance.weapon.StormSkill.judgeMarkTicks(l) / 20.0) + "초 · 낙뢰 " + com.hobbang.enhance.weapon.StormSkill.judgeBolts(l) + "발"
				+ (l >= 15 ? " · 돌진 2연 · 삼지창 30 고정" : "") : null;
			case CHRONO_DAGGER -> "정지 " + String.format("%.1f", (80 + 6 * bossSkillLv(l)) / 20.0) + "초" + (l >= 10 ? " · 정지 시 피해" : "")
				+ (l >= 15 ? " · 업보청산(+" + Math.round(com.hobbang.enhance.weapon.ChronoSkill.karmaBonus(l) * 100) + "%)" : "");
			default -> null;
		};
		return extra == null || extra.isEmpty() ? common : common + " · " + extra;
	}

	/** 11번 아이템 설명(lore)용: 1) 작은 글씨 스킬 설명(회색) 2) 맨 아래 적용된 스킬 이름과 현재 강화 기준 수치 */
	public static List<String> bossSkillDescriptions(HItem h) {
		return switch (h) {
			case SMITH_HAMMER -> List.of(
				"+3 우클릭: 지진 — 주변 적 피해 · 밀쳐냄 (낙하 1칸당 +" + fmt(com.hobbang.enhance.weapon.HammerSkill.FALL_DMG_PER_BLOCK) + " 피해, 최대 45칸)",
				"+10 패시브: 영혼 수집 — 처치 시 지진 피해/범위 누적",
				"+15 웅크림/지상: 천공 내려찍기(도약 후 급강하) · 맞은 적 구속"
			);
			case CRYSTAL_GREATSWORD -> List.of(
				"+3 우클릭: 관통 베기 — 시선 방향 돌진 베기 (처치 시 쿨 초기화)",
				"+7 패시브: 벤 적 머리 위로 거대 수정 대검 낙하",
				"+15 웅크리고 우클릭: 시선 방향으로 수정 가시 발사"
			);
			case STORMCALLER -> List.of(
				"+3 우클릭: 돌진 낙뢰 — 경로의 적에게 번개 폭발 (+10 연쇄 / +15 2연타)",
				"+10 웅크리고 우클릭: 심판 — 시선 지점에 번개 세례 (+15 삼지창 낙하)"
			);
			case CHRONO_DAGGER -> List.of(
				"+3 우클릭: 낫 베기 + 주변 비보스 시간 정지 (+10 정지 시 추가 피해)",
				"+15 웅크리고 우클릭: 업보청산 — 5초간 누적 피해에 추가 피해(+10%~+50%) 일괄 정산"
			);
			default -> List.of();
		};
	}

	public static List<String> bossAppliedStats(ItemStack s, HItem h, int l) {
		if (!bossSkillUnlocked(l)) return List.of("◆ 스킬 잠김 (+" + BOSS_SKILL_UNLOCK + "강 해금)");
		double mult = bossDamageMult(l);
		double cd = h.cooldown * bossCooldownMult(h, l) / 20.0;
		List<String> out = new ArrayList<>();
		switch (h) {
			case SMITH_HAMMER -> {
				double souls = com.hobbang.enhance.weapon.Souls.dmgMult(s, l);
				double dmg = Math.min(com.hobbang.enhance.weapon.HammerSkill.capDamage(l, 0), com.hobbang.enhance.weapon.HammerSkill.groundDamage(l) * souls);
				double rad = com.hobbang.enhance.weapon.HammerSkill.baseRadius(l) + com.hobbang.enhance.weapon.Souls.radiusAdd(s, l);
				out.add("◆ 지진: 기본 피해 " + fmt(dmg) + " (낙하 1칸당 +" + fmt(com.hobbang.enhance.weapon.HammerSkill.FALL_DMG_PER_BLOCK * souls) + ") · 범위 " + fmt(rad) + "칸 · 쿨 " + fmt(cd) + "초");
				if (l >= 15) {
					double up = 20.0 + 5.0 * (l - 15);
					double skyDmg = Math.min(com.hobbang.enhance.weapon.HammerSkill.capDamage(l, up), (com.hobbang.enhance.weapon.HammerSkill.groundDamage(l) + com.hobbang.enhance.weapon.HammerSkill.fallBonus(up)) * souls);
					out.add("◆ 천공 내려찍기: 상승 " + fmt(up) + "칸 · 최대 피해 " + fmt(skyDmg) + " · 구속");
				}
			}
			case CRYSTAL_GREATSWORD -> {
				double swordDmg = 1.0 + h.damage + weaponDamage(s, l);
				out.add("◆ 관통 베기: 거리 " + num(crystalPierceRange(l)) + "칸 · 피해 " + fmt(swordDmg * 0.5) + " · 쿨 " + fmt(cd) + "초");
				if (l >= 7) out.add("◆ 대검 낙하: 피해 " + fmt(swordDmg * 1.5));
				if (l >= 15) out.add("◆ 수정 가시: " + com.hobbang.enhance.weapon.CrystalSkill.spikeCount(l) + "개 × " + (l >= 13 ? 4 : l >= 10 ? 3 : 1) + "갈래 · 피해 " + fmt(10.0 * mult));
			}
			case STORMCALLER -> {
				String dashLine = "◆ 돌진 낙뢰: 피해 " + fmt(9.0 * mult) + (l >= 15 ? " ×2" : "") + (l >= 10 ? " · 연쇄 " + (l - 9) + "마리(" + fmt(6.0 * mult) + ")" : "") + " · 쿨 " + fmt(cd) + "초";
				out.add(dashLine);
				if (l >= 10) {
					double jr = com.hobbang.enhance.weapon.StormSkill.judgeRadius(l);
					double wait = com.hobbang.enhance.weapon.StormSkill.judgeMarkTicks(l) / 20.0;
					int bolts = com.hobbang.enhance.weapon.StormSkill.judgeBolts(l);
					out.add("◆ 심판: 범위 " + fmt(jr) + "칸 · 대기 " + fmt(wait) + "초 · 낙뢰 " + bolts + "발(" + fmt(5.0 * mult) + ") · 쿨 " + fmt(cd * 2) + "초");
				}
				if (l >= 15) out.add("◆ 심판 삼지창: 고정 피해 30 (직격 필중 + 반경 4.5칸)");
			}
			case CHRONO_DAGGER -> {
				double reach = 5.5 + 0.2 * bossSkillLv(l);
				double fr = 8.0 + 0.4 * bossSkillLv(l);
				double dur = (80 + 6 * bossSkillLv(l)) / 20.0;
				out.add("◆ 낫 베기: 피해 " + fmt(7.0 * mult) + " · 사거리 " + fmt(reach) + "칸 · 쿨 " + fmt(cd) + "초");
				out.add("◆ 시간 정지: 반경 " + fmt(fr) + "칸 · " + fmt(dur) + "초" + (l >= 10 ? " · 추가 피해 " + fmt(4.0 * mult) : ""));
				if (l >= 15) out.add("◆ 업보청산: 누적 피해 +" + Math.round(com.hobbang.enhance.weapon.ChronoSkill.karmaBonus(l) * 100) + "% 일괄 정산");
			}
			default -> {}
		}
		return out;
	}

	/** 장비 설명(lore)용: 마일스톤 3줄 (달성 ✔ / 미달성 🔒) */
	public static List<String[]> loreLines(ItemStack s, int l) {
		List<String[]> out = new ArrayList<>();
		Gear g = gear(s);
		if (g == null || g == Gear.WEAPON) return out;
		if (g == Gear.BOSS) {
			HItem h = ItemUtil.hitem(s);
			if (h != null) out.add(bossSkillUnlocked(l) ? new String[]{"on", "◆ " + bossSkill(h, l)}
				: new String[]{"off", "◇ +" + BOSS_SKILL_UNLOCK + " 스킬 해금 (우클릭 스킬 사용 가능)"});
			if (h == HItem.CRYSTAL_GREATSWORD) {   // t388: 수정 대검 마일스톤
				out.add(new String[]{l >= 7 ? "on" : "off", (l >= 7 ? "◆" : "◇") + " +7 관통 베기 뒤 거대 대검 낙하 (검 피해 150%)"});
				out.add(new String[]{l >= 10 ? "on" : "off", (l >= 10 ? "◆" : "◇") + " +10 공격 시 대상 무적시간 제거"});
				out.add(new String[]{l >= 15 ? "on" : "off", (l >= 15 ? "◆" : "◇") + " +15 웅크리고 우클릭: 바라보는 방향으로 수정 가시 · 공격력 +2/강"});
			}
			return out;
		}
		int[] ms = {5, 10, 15, 20};
		for (int i = 0; i < ms.length; i++) {
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
		if (g == Gear.BOSS) {
			HItem h = ItemUtil.hitem(s);
			if (h == null || !bossSkillUnlocked(next)) return null;
			return (next == BOSS_SKILL_UNLOCK ? "스킬 해금! " : h == HItem.CRYSTAL_GREATSWORD && next == 7 ? "대검 낙하 해금! " : "") + bossSkill(h, next);
		}
		if (next == 5 || next == 10 || next == 15 || next == 20) return milestone(g, tier(next));
		return null;
	}

	/** 다음 마일스톤 단계 (없으면 -1) */
	public static int nextMilestone(int l) { return l < 5 ? 5 : l < 10 ? 10 : l < 15 ? 15 : l < 20 ? 20 : -1; }

	public static boolean ranged(ItemStack s) { Material m = s.getType(); return m == Material.BOW || m == Material.CROSSBOW || m == Material.SHIELD; }

	static boolean isPickTag(Material m) { return Tag.ITEMS_PICKAXES.isTagged(m); }
}
