package com.hobbang.enhance.enhance;

import com.hobbang.enhance.Adv;
import com.hobbang.enhance.item.HItem;
import com.hobbang.enhance.item.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

/** 강화 확률/시도 (Fabric EnhanceLogic 그대로) */
public final class EnhanceLogic {
	/** 소수 둘째 자리까지, 뒤의 0 제거 (24.75 → 24.75%, 70.00 → 70%) */
	public static String pctStr(double d) {
		java.math.BigDecimal b = java.math.BigDecimal.valueOf(d + 1e-9).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
		if (b.scale() < 0) b = b.setScale(0);
		return b.toPlainString() + "%";
	}

	private EnhanceLogic() {}

	public static final int MAX_LEVEL = ItemUtil.MAX_LEVEL;
	public static final double PRISM_BONUS = 20;

	public record Odds(double success, double down, double destroy, HItem stone, int stones, int xp, boolean guaranteed, boolean luck,
					   int affinity, int fragility, boolean prism) {}

	/**
	 * 강화 대상 장비와 보조 아이템(부적·주문서)이 있는 곳.
	 * v1.2.0: 강화대 창(GUI)의 칸 / legacyRightClick 이면 주 손 + 가방.
	 * 강화석·경험치는 어느 쪽이든 플레이어 가방에서 쓴다.
	 */
	public interface Source {
		ItemStack gear();
		void setGear(ItemStack s);
		int helpers(HItem h);
		boolean useHelper(HItem h);
	}

	/** 예전 방식: 주 손 장비 + 가방 속 보조 아이템 */
	public static Source inventory(Player p) {
		return new Source() {
			public ItemStack gear() { return p.getInventory().getItemInMainHand(); }
			public void setGear(ItemStack s) { p.getInventory().setItemInMainHand(s); }
			public int helpers(HItem h) { return count(p, h); }
			public boolean useHelper(HItem h) { return consume(p, h, 1); }
		};
	}

	/** 절대 확률(합 100%) — 성공 / 유지 / 하락 / 파괴. o.down·o.destroy 는 "실패했을 때" 확률이므로 (1-성공)을 곱한다 */
	public record Abs(double success, double keep, double down, double destroy) {}

	public static Abs absolute(Odds o, int level) {
		double fail = (100 - o.success()) / 100.0;
		double des = Math.max(0, Math.min(100, o.destroy()));
		double dn = level > 0 ? Math.max(0, Math.min(o.down(), 100 - des)) : 0;
		double destroy = fail * des, down = fail * dn;
		return new Abs(o.success(), Math.max(0, 100 - o.success() - destroy - down), down, destroy);
	}

	/** 보조 아이템(역행 모래시계 > 불멸 > 파괴 방지, 축복)이 발동했을 때 장비에 실제로 일어나는 결과 (attempt 와 같은 우선순위) */
	public static Abs effective(Abs a, Source src) {
		if (src == null) return a;
		if (src.helpers(HItem.REWIND_HOURGLASS) > 0) return new Abs(a.success(), a.keep() + a.down() + a.destroy(), 0, 0);
		double keep = a.keep(), down = a.down(), destroy = a.destroy();
		if (src.helpers(HItem.IMMORTAL_SCROLL) > 0) { keep += destroy; destroy = 0; }
		else if (src.helpers(HItem.PROTECTION_SCROLL) > 0) { down += destroy; destroy = 0; }
		if (src.helpers(HItem.BLESSING_SCROLL) > 0) { keep += a.down(); down -= a.down(); }
		return new Abs(a.success(), keep, Math.max(0, down), destroy);
	}

	public static Odds odds(Player p, ItemStack s) {
		return odds(p == null ? null : inventory(p), s);
	}

	public static Odds odds(Source src, ItemStack s) {
		int l = ItemUtil.getLevel(s);
		EnhanceConfig.Stage st = EnhanceConfig.CFG.stages[Math.min(l, MAX_LEVEL - 1)];
		int aff = Enchants.level(Enchants.FORGE_AFFINITY, s);
		int frag = Enchants.level(Enchants.FRAGILITY_CURSE, s);
		boolean luck = src != null && src.helpers(HItem.LUCK_CHARM) > 0;
		double succ = st.success + aff * EnhanceConfig.CFG.affinityPerLevel + (luck ? EnhanceConfig.CFG.luckCharmBonus : 0);
		boolean prism = src != null && src.helpers(HItem.PRISM_SCROLL) > 0;
		if (prism) succ += PRISM_BONUS;
		boolean guaranteed = ItemUtil.getArtisan(s) >= 1000;
		if (guaranteed) succ = 100;
		succ = Math.max(0, Math.min(100, succ));
		double des = Math.min(100, st.destroy + (st.destroy > 0 || l >= 5 ? frag * EnhanceConfig.CFG.fragilityDestroyBonus : 0));
		HItem stone = l >= EnhanceConfig.CFG.radiantFrom ? HItem.RADIANT_STONE : HItem.ENHANCE_STONE;
		return new Odds(succ, st.downgrade, des, stone, st.stones, st.xpLevels, guaranteed, luck, aff, frag, prism);
	}

	public static int count(Player p, HItem item) {
		int c = 0;
		for (ItemStack st : p.getInventory().getContents()) if (st != null && item.is(st)) c += st.getAmount();
		return c;
	}

	public static boolean consume(Player p, HItem item, int amount) {
		if (count(p, item) < amount) return false;
		PlayerInventory inv = p.getInventory();
		ItemStack[] cont = inv.getContents();
		for (int i = 0; i < cont.length && amount > 0; i++) {
			ItemStack st = cont[i];
			if (st != null && item.is(st)) {
				int take = Math.min(amount, st.getAmount());
				st.setAmount(st.getAmount() - take);
				inv.setItem(i, st.getAmount() <= 0 ? null : st);
				amount -= take;
			}
		}
		return true;
	}

	public static void give(Player p, ItemStack s) {
		HashMap<Integer, ItemStack> left = p.getInventory().addItem(s);
		for (ItemStack r : left.values()) p.getWorld().dropItem(p.getLocation(), r);
	}

	private static String pct(double d) {
		return pctStr(d);
	}

	private static Component t(String s, NamedTextColor c) {
		return Component.text(s, c);
	}

	public static void preview(Player p, ItemStack s) {
		if (!ItemUtil.canEnhance(s)) {
			p.sendMessage(t("[홉방] 강화할 수 없는 아이템입니다. (무기/방어구/도구만 가능)", NamedTextColor.RED));
			return;
		}
		int l = ItemUtil.getLevel(s);
		p.sendMessage(t("━━━━ 강화 미리보기 ━━━━", NamedTextColor.GOLD));
		p.sendMessage(Component.empty().append(ItemUtil.levelTag(l)).append(ItemUtil.plainName(s)));
		if (l >= MAX_LEVEL) {
			p.sendMessage(t("이미 최대 강화(+15)입니다!", NamedTextColor.LIGHT_PURPLE));
			return;
		}
		// 기본은 보조 아이템 없이 (보조 아이템은 강화대 창의 보조 칸에 넣어야 쓰인다). 예전 방식이면 가방 기준
		Source src = EnhanceConfig.CFG.legacyRightClick ? inventory(p) : null;
		Odds o = odds(src, s);
		Abs a = absolute(o, l);
		p.sendMessage(t("+" + l + " → +" + (l + 1) + "  성공 " + pct(a.success()) + (o.guaranteed() ? " (장인의 기운 MAX!)" : ""), NamedTextColor.GREEN)
			.append(t("  유지 " + pct(a.keep()), NamedTextColor.GRAY))
			.append(t("  하락 " + pct(a.down()), NamedTextColor.YELLOW))
			.append(t("  파괴 " + pct(a.destroy()), NamedTextColor.RED)));
		p.sendMessage(t("(실패했을 때: 파괴 " + pct(o.destroy()) + " / 하락 " + pct(l > 0 ? Math.min(o.down(), 100 - o.destroy()) : 0) + " — /hobbang rates 와 같은 기준)", NamedTextColor.DARK_GRAY));
		p.sendMessage(t("재료: ", NamedTextColor.YELLOW).append(Component.text(o.stone().name))
			.append(Component.text(" x" + o.stones() + " (보유 " + count(p, o.stone()) + "), 경험치 " + o.xp() + "레벨")));
		p.sendMessage(t("장인의 기운: " + pct(ItemUtil.getArtisan(s) / 10.0)
			+ (o.affinity() > 0 ? " | 강화 친화 +" + (o.affinity() * EnhanceConfig.CFG.affinityPerLevel) + "%p" : "")
			+ (o.fragility() > 0 ? " | 깨짐의 저주" : "")
			+ (o.luck() ? " | 행운의 부적 +" + (int) EnhanceConfig.CFG.luckCharmBonus + "%p" : "")
			+ (o.prism() ? " | 프리즘 주문서 +" + (int) PRISM_BONUS + "%p" : ""), NamedTextColor.AQUA));
		if (src == null) p.sendMessage(t("부적·주문서는 강화대를 우클릭해 연 창의 보조 칸에 넣으면 적용됩니다.", NamedTextColor.GRAY));
	}

	private static void sound(Location at, Sound s, SoundCategory c, float v, float pi) {
		at.getWorld().playSound(at, s, c, v, pi);
	}

	/** 강화 1회 시도. hand 의 아이템을 직접 수정한 뒤 다시 넣는다 */
	public static void attempt(Player p, Block forge) {
		attempt(p, forge, inventory(p));
	}

	/** 강화 1회 시도 (src 의 장비를 수정해 다시 넣는다). 강화석·경험치는 플레이어 가방에서 */
	public static void attempt(Player p, Block forge, Source src) {
		ItemStack s = src.gear();
		if (!ItemUtil.canEnhance(s)) {
			p.sendMessage(t("[홉방] 주 손에 무기/방어구/도구를 들고 우클릭하세요. (좌클릭 = 확률 미리보기)", NamedTextColor.RED));
			return;
		}
		int l = ItemUtil.getLevel(s);
		if (l >= MAX_LEVEL) {
			p.sendMessage(t("[홉방] 이미 최대 강화(+15)입니다!", NamedTextColor.LIGHT_PURPLE));
			return;
		}
		Odds o = odds(src, s);
		boolean creative = p.getGameMode() == GameMode.CREATIVE;
		if (!creative) {
			if (count(p, o.stone()) < o.stones()) {
				p.sendMessage(t("[홉방] 재료 부족: ", NamedTextColor.RED).append(Component.text(o.stone().name))
					.append(Component.text(" " + o.stones() + "개 필요 (보유 " + count(p, o.stone()) + ")")));
				return;
			}
			if (p.getLevel() < o.xp()) {
				p.sendMessage(t("[홉방] 경험치 부족: " + o.xp() + "레벨 필요", NamedTextColor.RED));
				return;
			}
			consume(p, o.stone(), o.stones());
			p.giveExpLevels(-o.xp());
			if (o.luck()) src.useHelper(HItem.LUCK_CHARM);
			if (o.prism()) src.useHelper(HItem.PRISM_SCROLL);
		}
		// 재료 소모 뒤 손 아이템을 다시 읽는다 (같은 슬롯 객체)
		s = src.gear();
		World w = forge.getWorld();
		Location fl = forge.getLocation().add(0.5, 0.5, 0.5);
		double x = forge.getX() + 0.5, y = forge.getY() + 1.2, z = forge.getZ() + 0.5;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double roll = rnd.nextDouble() * 100;
		Component name = ItemUtil.plainName(s);
		if (roll < o.success()) {
			int nl = l + 1;
			ItemUtil.setLevel(s, nl);
			ItemUtil.setArtisan(s, 0);
			src.setGear(s);
			sound(fl, Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 1.0F, 1.2F);
			sound(fl, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8F, 1.0F + nl * 0.03F);
			w.spawnParticle(Particle.HAPPY_VILLAGER, x, y, z, 20, 0.4, 0.3, 0.4, 0.1);
			w.spawnParticle(Particle.ENCHANT, x, y, z, 40, 0.5, 0.5, 0.5, 0.5);
			if (nl >= 10) w.spawnParticle(Particle.TOTEM_OF_UNDYING, x, y, z, 60, 0.3, 0.5, 0.3, 0.4);
			p.sendMessage(Component.text("✦ 강화 성공! ", NamedTextColor.GREEN, TextDecoration.BOLD).append(ItemUtil.levelTag(nl)).append(name));
			Adv.award(p, "first");
			if (nl >= 10) Adv.award(p, "ten");
			if (nl >= 15) Adv.award(p, "fifteen");
			if (nl >= EnhanceConfig.CFG.broadcastFrom) {
				sound(fl, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
				Bukkit.broadcast(t("📢 [홉방 강화] ", NamedTextColor.GOLD).append(p.displayName().colorIfAbsent(NamedTextColor.YELLOW))
					.append(t("님이 ", NamedTextColor.WHITE)).append(name).append(Component.text(" ")).append(ItemUtil.levelTag(nl))
					.append(t("강화에 성공했습니다!", NamedTextColor.WHITE)));
			}
			return;
		}

		// 실패: 장인의 기운 누적
		EnhanceConfig.Stage st = EnhanceConfig.CFG.stages[l];
		int art = Math.min(1000, ItemUtil.getArtisan(s) + (int) Math.round(st.success * EnhanceConfig.CFG.artisanRatio * 10));
		double r2 = rnd.nextDouble() * 100;
		boolean bad = r2 < o.destroy() || (r2 < o.destroy() + o.down() && l > 0);
		if (bad && src.useHelper(HItem.REWIND_HOURGLASS)) {
			s = src.gear();
			ItemUtil.setArtisan(s, art);
			src.setGear(s);
			if (!creative) {
				give(p, o.stone().create(o.stones()));
				p.giveExpLevels(o.xp());
			}
			sound(fl, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 0.5F, 2.0F);
			w.spawnParticle(Particle.REVERSE_PORTAL, x, y, z, 50, 0.4, 0.4, 0.4, 0.05);
			p.sendMessage(t("⏪ 역행 모래시계 발동! " + (r2 < o.destroy() ? "파괴" : "하락") + " 결과를 되감았습니다. (재료 환불, 단계 유지) ", NamedTextColor.GOLD)
				.append(t("장인의 기운 " + pct(art / 10.0), NamedTextColor.AQUA)));
			return;
		}
		if (r2 < o.destroy()) {
			if (src.useHelper(HItem.IMMORTAL_SCROLL)) {
				s = src.gear();
				ItemUtil.setArtisan(s, art);
				src.setGear(s);
				sound(fl, Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6F, 1.8F);
				p.sendMessage(t("✖ 파괴될 뻔! 불멸 주문서가 대신 사라졌습니다. (단계 유지) ", NamedTextColor.LIGHT_PURPLE).append(ItemUtil.levelTag(l)));
			} else if (src.useHelper(HItem.PROTECTION_SCROLL)) {
				s = src.gear();
				ItemUtil.setLevel(s, l - 1);
				ItemUtil.setArtisan(s, art);
				src.setGear(s);
				sound(fl, Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6F, 1.4F);
				p.sendMessage(t("✖ 파괴될 뻔! 파괴 방지 주문서가 대신 사라졌습니다. ", NamedTextColor.YELLOW)
					.append(ItemUtil.levelTag(l - 1)).append(t("(1단계 하락)", NamedTextColor.GRAY)));
			} else {
				sound(fl, Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 0.6F);
				sound(fl, Sound.BLOCK_ANVIL_BREAK, SoundCategory.BLOCKS, 1.0F, 0.8F);
				w.spawnParticle(Particle.EXPLOSION, x, y, z, 3, 0.3, 0.3, 0.3, 0.0);
				w.spawnParticle(Particle.LARGE_SMOKE, x, y, z, 30, 0.3, 0.3, 0.3, 0.05);
				p.sendMessage(Component.text("💥 강화 실패... 장비가 파괴되었습니다: ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
					.append(ItemUtil.levelTag(l)).append(name));
				if (l >= EnhanceConfig.CFG.broadcastFrom) {
					Bukkit.broadcast(t("💀 [홉방 강화] ", NamedTextColor.DARK_RED).append(p.displayName().colorIfAbsent(NamedTextColor.YELLOW))
						.append(t("님의 ", NamedTextColor.WHITE)).append(ItemUtil.levelTag(l)).append(name)
						.append(t(" 이(가) 산산조각 났습니다... 묵념 🙏", NamedTextColor.GRAY)));
				}
				src.setGear(null);
			}
			return;
		}
		if (r2 < o.destroy() + o.down() && l > 0) {
			if (src.useHelper(HItem.BLESSING_SCROLL)) {
				s = src.gear();
				ItemUtil.setArtisan(s, art);
				src.setGear(s);
				sound(fl, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.0F);
				p.sendMessage(t("✖ 강화 실패 - 축복 주문서로 하락을 막았습니다. (유지) ", NamedTextColor.YELLOW)
					.append(t("장인의 기운 " + pct(art / 10.0), NamedTextColor.AQUA)));
			} else {
				ItemUtil.setLevel(s, l - 1);
				ItemUtil.setArtisan(s, art);
				src.setGear(s);
				sound(fl, Sound.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.0F, 0.6F);
				w.spawnParticle(Particle.SMOKE, x, y, z, 20, 0.3, 0.2, 0.3, 0.02);
				p.sendMessage(t("▼ 강화 실패 - 단계 하락: ", NamedTextColor.RED).append(ItemUtil.levelTag(l - 1))
					.append(t(" 장인의 기운 " + pct(art / 10.0), NamedTextColor.AQUA)));
			}
			return;
		}
		ItemUtil.setArtisan(s, art);
		src.setGear(s);
		sound(fl, Sound.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.7F, 1.0F);
		w.spawnParticle(Particle.SMOKE, x, y, z, 12, 0.3, 0.2, 0.3, 0.02);
		p.sendMessage(t("✖ 강화 실패 (단계 유지) ", NamedTextColor.GRAY).append(t("장인의 기운 " + pct(art / 10.0), NamedTextColor.AQUA)));
	}
}
