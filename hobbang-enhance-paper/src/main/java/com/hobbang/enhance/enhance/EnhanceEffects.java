package com.hobbang.enhance.enhance;

import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.item.EnhanceTier;
import com.hobbang.enhance.item.EnhanceTier.Gear;
import com.hobbang.enhance.item.ItemUtil;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v1.2.0 강화 마일스톤 특수 효과 (틱 작업 없음 — 모두 이벤트 시점에 손에 든 장비의 현재 단계만 읽는다).
 * 검/창: +5 출혈, +10 관통, +15 장인의 일격 · 도구: 내구도 보존 · 활/쇠뇌: 화살 보존 · 방패: 발사체 반사
 */
public final class EnhanceEffects implements Listener {
	private static boolean inner = false;
	private static final Map<UUID, Long> SIGNATURE_CD = new HashMap<>();
	private static final Set<UUID> CHARGED = new HashSet<>();
	private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(0xA00000), 1.0F);

	private static ThreadLocalRandom r() { return ThreadLocalRandom.current(); }

	private static void extra(LivingEntity t, Player p, double dmg) {
		if (t.isDead() || !t.isValid() || dmg <= 0) return;
		inner = true;
		try {
			t.setNoDamageTicks(0);
			t.damage(dmg, DamageSource.builder(DamageType.MAGIC).withCausingEntity(p).withDirectEntity(p).build());
		} finally { inner = false; }
	}

	/** 공격 직전(쿨다운 초기화 전)에 풀차지 여부 기록 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPreAttack(PrePlayerAttackEntityEvent e) {
		if (e.getPlayer().getAttackCooldown() >= 0.9F) CHARGED.add(e.getPlayer().getUniqueId());
		else CHARGED.remove(e.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent e) {
		if (inner || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
		if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity t)) return;
		ItemStack w = p.getInventory().getItemInMainHand();
		if (EnhanceTier.gear(w) != Gear.SWORD) return;
		int tier = EnhanceTier.tier(ItemUtil.getLevel(w));
		if (tier <= 0) return;
		double dealt = e.getFinalDamage();
		var plugin = HobbangPlugin.get();
		if (r().nextDouble() < EnhanceTier.BLEED_CHANCE) {
			for (int i = 1; i <= 3; i++) Bukkit.getScheduler().runTaskLater(plugin, () -> {
				if (!t.isValid() || t.isDead()) return;
				Location l = t.getLocation();
				t.getWorld().spawnParticle(Particle.DUST, l.getX(), l.getY() + t.getHeight() * 0.6, l.getZ(), 6, 0.25, 0.3, 0.25, 0, BLOOD);
				extra(t, p, 1.0);
			}, 20L * i);
		}
		if (tier >= 2 && r().nextDouble() < EnhanceTier.PIERCE_CHANCE) {
			double d = dealt * EnhanceTier.PIERCE_RATIO;
			Bukkit.getScheduler().runTask(plugin, () -> {
				extra(t, p, d);
				Location l = t.getLocation();
				t.getWorld().spawnParticle(Particle.CRIT, l.getX(), l.getY() + t.getHeight() * 0.6, l.getZ(), 10, 0.2, 0.3, 0.2, 0.3);
				t.getWorld().playSound(l, Sound.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 0.8F, 1.6F);
			});
		}
		boolean charged = CHARGED.remove(p.getUniqueId());
		if (tier >= 3 && charged) {
			long now = System.currentTimeMillis();
			Long last = SIGNATURE_CD.get(p.getUniqueId());
			if (last == null || now - last >= 3000) {
				SIGNATURE_CD.put(p.getUniqueId(), now);
				double d = dealt * EnhanceTier.SIGNATURE_RATIO;
				Location c = t.getLocation();
				Bukkit.getScheduler().runTask(plugin, () -> {
					c.getWorld().spawnParticle(Particle.SWEEP_ATTACK, c.getX(), c.getY() + 1, c.getZ(), 6, 1.2, 0.2, 1.2, 0);
					c.getWorld().spawnParticle(Particle.END_ROD, c.getX(), c.getY() + 0.3, c.getZ(), 16, 1.5, 0.1, 1.5, 0.05);
					c.getWorld().playSound(c, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.6F, 1.8F);
					for (Entity n : c.getWorld().getNearbyEntities(c, 3, 2, 3)) {
						if (n == t || n == p || !(n instanceof LivingEntity le) || n instanceof Player) continue;
						if (n instanceof Tameable tm && tm.isTamed()) continue;
						if (n instanceof org.bukkit.entity.ArmorStand) continue;
						extra(le, p, d);
					}
				});
			}
		}
	}

	/** 도구: 내구도 보존 */
	@EventHandler(ignoreCancelled = true)
	public void onItemDamage(PlayerItemDamageEvent e) {
		ItemStack s = e.getItem();
		if (EnhanceTier.gear(s) != Gear.TOOL) return;
		int tier = EnhanceTier.tier(ItemUtil.getLevel(s));
		if (tier > 0 && r().nextDouble() < EnhanceTier.TOOL_SAVE[tier]) e.setCancelled(true);
	}

	/** 활/쇠뇌: 화살 보존 — 쏜 화살은 줍기 불가로 바꾸고 같은 화살 1개를 돌려준다(복사 없음). 무한 인첸트 화살은 원래 줍기 불가라 제외 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onShoot(EntityShootBowEvent e) {
		if (!(e.getEntity() instanceof Player p) || p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
		ItemStack bow = e.getBow();
		Gear g = EnhanceTier.gear(bow);
		if (g != Gear.BOW && g != Gear.CROSSBOW) return;
		if (!(e.getProjectile() instanceof AbstractArrow a) || a.getPickupStatus() != AbstractArrow.PickupStatus.ALLOWED) return;
		int tier = EnhanceTier.tier(ItemUtil.getLevel(bow));
		if (tier <= 0 || r().nextDouble() >= EnhanceTier.ARROW_SAVE[tier]) return;
		ItemStack ammo = e.getConsumable();
		if (ammo == null || ammo.getType().isAir()) return;
		ItemStack back = ammo.clone();
		back.setAmount(1);
		a.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
		Bukkit.getScheduler().runTask(HobbangPlugin.get(), () -> {
			if (!p.isOnline()) return;
			for (ItemStack left : p.getInventory().addItem(back).values()) p.getWorld().dropItem(p.getLocation(), left);
		});
	}

	/** 방패: 막는 중 발사체 반사 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onProjectile(ProjectileHitEvent e) {
		if (!(e.getHitEntity() instanceof Player p) || !p.isBlocking()) return;
		ItemStack sh = p.getActiveItem();
		if (sh == null || EnhanceTier.gear(sh) != Gear.SHIELD) return;
		int tier = EnhanceTier.tier(ItemUtil.getLevel(sh));
		if (EnhanceTier.REFLECT[tier] <= 0 || r().nextDouble() >= EnhanceTier.REFLECT[tier]) return;
		Projectile pr = e.getEntity();
		Vector toShooter = pr.getVelocity().multiply(-0.9);
		if (pr.getShooter() instanceof Entity sh2 && sh2.isValid()) {
			Vector d = sh2.getLocation().add(0, 1, 0).toVector().subtract(pr.getLocation().toVector());
			if (d.lengthSquared() > 1e-4) toShooter = d.normalize().multiply(Math.max(1.2, pr.getVelocity().length()));
		}
		e.setCancelled(true);
		pr.setVelocity(toShooter);
		pr.setShooter(p);
		p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1F, 1.6F);
		p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, pr.getLocation(), 8, 0.2, 0.2, 0.2, 0.2);
	}

	// ───────── 예전(v1.1 이하) 속성표로 강화된 장비 다시 계산 ─────────
	private static void migrate(Inventory inv) {
		ItemStack[] c = inv.getContents();
		for (int i = 0; i < c.length; i++) {
			if (c[i] == null) continue;
			ItemStack n = ItemUtil.migrateIdentity(c[i]);   // v1.4.0 무기 모양 변경 (뇌운 전투도끼 / 크로노 낫)
			boolean ch = n != null;
			if (ch) c[i] = n;
			if (ItemUtil.migrate(c[i])) ch = true;
			if (ch) inv.setItem(i, c[i]);
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) { migrate(e.getPlayer().getInventory()); }

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onOpen(InventoryOpenEvent e) {
		if (e.getInventory().getHolder(false) instanceof com.hobbang.enhance.forge.ForgeGui) return;
		migrate(e.getInventory());
	}
}
