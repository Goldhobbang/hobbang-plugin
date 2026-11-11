package com.hobbang.enhance.boss;

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
	private static final int DURATION = 100;
	private static final Random RNG = new Random();

	private static final class R {
		final Block pos; final BossKind kind; final Location spawn; final java.util.function.Consumer<org.bukkit.entity.Mob> after; int t = 0;
		R(Block pos, BossKind kind, Location spawn, java.util.function.Consumer<org.bukkit.entity.Mob> after) {
			this.pos = pos; this.kind = kind; this.spawn = spawn; this.after = after;
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
		if (player.getGameMode() != GameMode.CREATIVE) core.setAmount(core.getAmount() - 1);
		RITUALS.add(new R(pos, kind, spawn, after));
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
			if (!r.pos.getChunk().isLoaded()) { it.remove(); continue; }
			int t = ++r.t;
			double cx = r.pos.getX() + 0.5, cy = r.pos.getY() + 1.0, cz = r.pos.getZ() + 0.5;
			double radius = 4.0 - 3.0 * t / DURATION;
			if (t % 2 == 0) for (int i = 0; i < 4; i++) {
				double a = t * 0.25 + i * Math.PI / 2;
				level.spawnParticle(r.kind.particle, cx + Math.cos(a) * radius, cy + t * 0.02, cz + Math.sin(a) * radius, 1, 0, 0, 0, 0);
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
			if (t >= DURATION) {
				it.remove();
				org.bukkit.entity.Mob m = BossManager.spawn(r.kind, r.spawn != null ? r.spawn : r.pos.getRelative(0, 1, 0).getLocation());
				if (r.after != null && m != null) r.after.accept(m);
			}
		}
	}

	static void clear() {
		RITUALS.clear();
	}
}
