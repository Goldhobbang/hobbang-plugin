package com.hobbang.enhance.boss;

import com.hobbang.enhance.arena.Arenas;
import com.hobbang.enhance.arena.Site;
import com.hobbang.enhance.arena.StormArena;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** 보스 핵 → 제단 우클릭 시 5초 의식 후 보스 소환 (v1.4.0: 구조물 제단 / 폭풍 제단에서만 시작됨) */
public final class Ritual {
	private static final int DURATION = 100, CLEAR_PER_TICK = 400;
	private static final Random RNG = new Random();

	private static final class R {
		final Block pos; final BossKind kind; final Location spawn; final java.util.function.Consumer<org.bukkit.entity.Mob> after;
		final List<Block> clearPlan; int clearIdx = 0; int t = 0;
		/** 보스가 나오기 전에 의식이 끊기면(서버 종료·리로드) 돌려줄 핵과 주인 */
		ItemStack refund; java.util.UUID owner;
		R(Block pos, BossKind kind, Location spawn, java.util.function.Consumer<org.bukkit.entity.Mob> after) {
			this.pos = pos; this.kind = kind; this.spawn = spawn; this.after = after;
			Site site = Site.byBoss(kind.id);
			this.clearPlan = (site != null && site.structure != null) ? Arenas.interiorClearTargets(site, pos) : List.of();
			com.hobbang.enhance.arena.ArenaDress.startRitual(kind, pos, spawn);
		}
	}

	private static final List<R> RITUALS = new ArrayList<>();

	private Ritual() {}

	public static boolean active(Block pos) {
		for (R r : RITUALS) if (r.pos.equals(pos)) return true;
		return false;
	}

	/** 핵을 이미 소비한 뒤(폭풍 제단) 의식만 진행 */
	public static void startConsumed(Block pos, BossKind kind, Location spawn, java.util.function.Consumer<org.bukkit.entity.Mob> after) {
		RITUALS.add(new R(pos, kind, spawn, after));
	}

	public static boolean start(Block pos, Player player, ItemStack core, BossKind kind, Location spawn, java.util.function.Consumer<org.bukkit.entity.Mob> after) {
		if (kind == null) return false;
		for (R r : RITUALS) {
			if (r.pos.equals(pos)) {
				player.sendMessage(Component.text("[홉방] 이미 의식이 진행 중입니다!", NamedTextColor.RED));
				return false;
			}
		}
		if (BossManager.bossNear(pos.getLocation().add(0.5, 0.5, 0.5), 64)) {
			player.sendMessage(Component.text("[홉방] 근처에 이미 보스가 있습니다! 먼저 처치하세요.", NamedTextColor.RED));
			return false;
		}
		R ritual = new R(pos, kind, spawn, after);
		if (player.getGameMode() != GameMode.CREATIVE) {
			ItemStack one = core.clone();
			one.setAmount(1);
			ritual.refund = one;
			ritual.owner = player.getUniqueId();
			core.setAmount(core.getAmount() - 1);
		}
		RITUALS.add(ritual);
		BU.sound(pos.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 2.0F, 0.5F);
		Bukkit.broadcast(Component.text("🔥 ", NamedTextColor.DARK_PURPLE).append(player.displayName())
			.append(Component.text("님이 ", NamedTextColor.DARK_PURPLE))
			.append(Component.text(kind.name, NamedTextColor.LIGHT_PURPLE))
			.append(Component.text(" 소환 의식을 시작했습니다...", NamedTextColor.DARK_PURPLE)));
		return true;
	}

	static void tick() {
		if (RITUALS.isEmpty()) return;
		Iterator<R> it = RITUALS.iterator();
		while (it.hasNext()) {
			R r = it.next();
			World level = r.pos.getWorld();
			if (!r.pos.getChunk().isLoaded()) { refund(r); it.remove(); continue; }
			int t = ++r.t;
			if (r.kind == BossKind.ABYSSAL_SMITH) {
				tickSmithRitual(r, level, t);
			} else {
				if (r.clearIdx < r.clearPlan.size()) {
					int end = Math.min(r.clearPlan.size(), r.clearIdx + CLEAR_PER_TICK);
					for (int i = r.clearIdx; i < end; i++) {
						Block b = r.clearPlan.get(i);
						if (b.getType().isAir() || StormArena.keepBlock(b) || b.equals(r.pos)) continue;
						if (i % 16 == 0) level.spawnParticle(Particle.BLOCK, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 4, 0.25, 0.25, 0.25, 0.02, b.getBlockData());
						b.setType(org.bukkit.Material.AIR, false);
					}
					if (t % 4 == 1) BU.sound(r.pos.getLocation().add(0.5, 1.0, 0.5), Sound.BLOCK_DEEPSLATE_BREAK, SoundCategory.BLOCKS, 0.8F, 0.8F);
					r.clearIdx = end;
				}
				double cx = r.pos.getX() + 0.5, cy = r.pos.getY() + 1.0, cz = r.pos.getZ() + 0.5;
				double progress = Math.min(1.0, (double) t / DURATION);
				double radius = 4.0 - 3.0 * progress;
				if (t % 2 == 0) for (int i = 0; i < 4; i++) {
					double a = t * 0.25 + i * Math.PI / 2;
					level.spawnParticle(r.kind.particle, cx + Math.cos(a) * radius, cy + Math.min(t, DURATION) * 0.02, cz + Math.sin(a) * radius, 1, 0, 0, 0, 0);
					level.spawnParticle(Particle.REVERSE_PORTAL, cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius, 2, 0.1, 0.1, 0.1, 0.02);
				}
				if (t % 20 == 0 && t < DURATION) {
					int left = (DURATION - t) / 20;
					Location c = new Location(level, cx, cy, cz);
					BU.sound(c, Sound.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 1.5F, 0.5F);
					Component bar = Component.text("봉인이 풀리기까지 " + left + "...", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD);
					for (Player sp : level.getPlayers())
						if (sp.getLocation().distanceSquared(c) < 48 * 48) sp.sendActionBar(bar);
					BU.lightning(level, cx + (RNG.nextDouble() - 0.5) * 10, cy, cz + (RNG.nextDouble() - 0.5) * 10);
				}
			}
			if (t >= DURATION && r.clearIdx >= r.clearPlan.size()) {
				it.remove();
				Location spawnAt = r.spawn != null ? r.spawn.clone() : r.pos.getRelative(0, 1, 0).getLocation();
				if (r.kind == BossKind.ABYSSAL_SMITH) spawnAt.subtract(0, 2, 0);
				org.bukkit.entity.Mob m = BossManager.spawn(r.kind, spawnAt);
				if (r.after != null && m != null) r.after.accept(m);
			}
		}
	}

	private static final org.bukkit.Particle.DustOptions WHITE_WAVE =
		new org.bukkit.Particle.DustOptions(org.bukkit.Color.WHITE, 2.2F);

	/** 대장장이 전용 소환 시퀀스: 2.제단 제거 후 중앙→바깥 빈공간 정리(t=3..46) → 3.하단 아레나 전개(t=12..65) → 4.속이 빈 하얀 고리/구 충격파 + 화면 흔들림(t=70..96) */
	private static void tickSmithRitual(R r, World level, int t) {
		if (t >= 3 && r.clearIdx < r.clearPlan.size()) {
			int remTicks = Math.max(1, 46 - t);
			int batch = Math.max(24, (r.clearPlan.size() - r.clearIdx + remTicks - 1) / remTicks);
			int end = Math.min(r.clearPlan.size(), r.clearIdx + batch);
			for (int i = r.clearIdx; i < end; i++) {
				Block b = r.clearPlan.get(i);
				if (b.getType().isAir() || StormArena.keepBlock(b) || b.equals(r.pos)) continue;
				b.setType(org.bukkit.Material.AIR, false);
			}
			if (t % 4 == 1) BU.sound(r.pos.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_DEEPSLATE_BREAK, SoundCategory.BLOCKS, 0.9F, 0.65F);
			r.clearIdx = end;
		}
		if (t < 70 || t >= DURATION) return;
		double cx = r.pos.getX() + 0.5, cy = r.pos.getY() - 0.35, cz = r.pos.getZ() + 0.5;
		Location c = new Location(level, cx, cy, cz);
		if (t == 70) {
			BU.sound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 2.4F, 0.45F);
			BU.sound(c, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 2.0F, 0.45F);
		}
		double rad = 1.2 + (t - 70) / 26.0 * 27.0;
		int ringPts = Math.max(36, Math.min(96, (int) (rad * 4.2)));
		for (int i = 0; i < ringPts; i++) {
			double a = (Math.PI * 2 * i) / ringPts;
			level.spawnParticle(Particle.DUST, cx + Math.cos(a) * rad, cy + 0.15, cz + Math.sin(a) * rad, 1, 0, 0, 0, 0, WHITE_WAVE);
		}
		// 속이 빈 하얀 반구/구 껍질(내부는 비어 있음)
		for (int lat = 1; lat <= 3; lat++) {
			double phi = (Math.PI * 0.5) * lat / 3.5;
			double ringR = rad * Math.cos(phi);
			double y = cy + 0.15 + rad * Math.sin(phi);
			int pts = Math.max(12, (int) (ringR * 2.2));
			for (int i = 0; i < pts; i++) {
				double a = (Math.PI * 2 * i) / pts + lat * 0.25;
				level.spawnParticle(Particle.DUST, cx + Math.cos(a) * ringR, y, cz + Math.sin(a) * ringR, 1, 0, 0, 0, 0, WHITE_WAVE);
			}
		}
		if (t % 2 == 0) {
			float tilt = (t % 4 == 0) ? 14F : -14F;
			for (Player sp : level.getPlayers()) {
				if (sp.getLocation().distanceSquared(c) < 38 * 38) {
					sp.sendHurtAnimation(tilt);
					if (sp.isOnGround() && t % 4 == 0) {
						sp.setVelocity(sp.getVelocity().add(new org.bukkit.util.Vector(0, 0.045, 0)));
					}
				}
			}
		}
	}

	/** 서버 종료/플러그인 비활성화: 아직 보스가 나오지 않은 의식의 핵을 돌려준다 (예전: 핵만 사라짐) */
	static void clear() {
		for (R r : RITUALS) refund(r);
		RITUALS.clear();
	}

	private static void refund(R r) {
		if (r.refund == null) return;
		ItemStack it = r.refund;
		r.refund = null;
		try {
			Player p = r.owner == null ? null : Bukkit.getPlayer(r.owner);
			if (p != null && p.isOnline()) {
				for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItem(p.getLocation(), left);
			} else {
				r.pos.getWorld().dropItem(r.pos.getLocation().add(0.5, 1.2, 0.5), it);
			}
		} catch (Exception ex) {
			com.hobbang.enhance.HobbangPlugin.get().getLogger().warning("의식 핵 환불 실패 (" + r.kind.id + "): " + ex);
		}
	}
}
