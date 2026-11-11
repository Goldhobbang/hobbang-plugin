package com.hobbang.enhance.weapon;

import com.hobbang.enhance.boss.BU;
import com.hobbang.enhance.boss.ColossusBoss;
import com.hobbang.enhance.boss.Fx;
import com.hobbang.enhance.item.HItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** 보스 무기 4종: 우클릭 스킬 / 근접 타격 연출 */
public final class WeaponListener implements Listener {
	private static boolean skill = false;

	/** 스킬 피해 (근접 타격 연출이 다시 터지지 않도록 표시) */
	public static void skillDamage(LivingEntity e, Player p, double dmg) {
		boolean prev = skill;
		skill = true;
		try {
			e.damage(dmg, BU.player(p));
		} finally {
			skill = prev;
		}
	}

	private static List<LivingEntity> around(Player p, double r) {
		List<LivingEntity> out = new ArrayList<>();
		BoundingBox box = p.getBoundingBox().expand(r);
		for (Entity e : p.getWorld().getNearbyEntities(box)) {
			if (e instanceof LivingEntity le && e != p && !(e instanceof Player) && !le.isDead() && !(e instanceof org.bukkit.entity.ArmorStand as && as.isMarker()))
				out.add(le);
		}
		return out;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onUse(PlayerInteractEvent e) {
		if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		if (e.useItemInHand() == Event.Result.DENY && e.getAction() == Action.RIGHT_CLICK_BLOCK) return;   // 강화대 등에서 처리됨
		ItemStack stack = e.getItem();
		HItem h = HItem.of(stack);
		if (h == null) return;
		Player p = e.getPlayer();
		if (stack.getType() == Material.KNOWLEDGE_BOOK) {   // 재료 아이템: 지식의 책 사용(소모) 방지
			e.setUseItemInHand(Event.Result.DENY);
			return;
		}
		if (!h.isWeapon()) return;
		// 상호작용 블록(상자 등) 우클릭은 원래 동작 유지 (웅크리면 스킬)
		Block cb = e.getClickedBlock();
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK && cb != null && cb.getType().isInteractable() && !p.isSneaking()) {
			if (h.base == Material.NETHERITE_AXE) e.setUseItemInHand(Event.Result.DENY);   // 망치·전투도끼: 껍질 벗기기 막기
			return;
		}
		e.setUseItemInHand(Event.Result.DENY);   // 도끼 껍질 벗기기 등 막기
		if (p.getCooldown(h.cooldownGroup()) > 0) return;
		EquipmentSlot slot = e.getHand() == null ? EquipmentSlot.HAND : e.getHand();
		World sl = p.getWorld();
		int tier = WeaponFx.tierOf(stack);
		final int lv = com.hobbang.enhance.item.ItemUtil.getLevel(stack);
		final double mult = com.hobbang.enhance.item.EnhanceTier.bossDamageMult(lv), rad = com.hobbang.enhance.item.EnhanceTier.bossRadiusAdd(lv);
		switch (h) {
			case SMITH_HAMMER -> {
				Vector c = p.getLocation().toVector();
				WeaponFx.hammerCast(sl, p);
				WeaponFx.earthquake(sl, p, c, tier);
				for (LivingEntity t : around(p, 5.0 + rad)) {
					skillDamage(t, p, 8.0 * mult);
					Vector push = t.getLocation().toVector().subtract(c);
					if (push.lengthSquared() > 1e-6) push.normalize().multiply(0.6);
					t.setVelocity(t.getVelocity().add(new Vector(push.getX(), 0.8, push.getZ())));
				}
				// +10 여진 1회 / +15 여진 2회 + 구속
				int shocks = lv >= 15 ? 2 : lv >= 10 ? 1 : 0;
				for (int k = 1; k <= shocks; k++) {
					Fx.later(12 * k, () -> {
						if (!p.isOnline() || p.isDead()) return;
						WeaponFx.earthquake(p.getWorld(), p, c, 0);
						for (LivingEntity t : around(p, (5.0 + rad) * 0.8)) {
							skillDamage(t, p, 4.0 * mult);
							if (lv >= 15) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
						}
					});
				}
				damage(p, slot, 2);
			}
			case CRYSTAL_GREATSWORD -> {
				Vector look = p.getLocation().getDirection();
				Vector dir = new Vector(look.getX(), 0, look.getZ());
				if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
				dir.normalize();
				int spikes = 8 + lv / 5;
				for (int i = 1; i <= spikes; i++) {
					Vector at = p.getLocation().toVector().add(dir.clone().multiply(i * 1.2));
					ColossusBoss.spike(sl, at, i, (tier >= 1 ? 1.35F : 1.1F) + i * 0.04F, p.getUniqueId(), p, 10 * mult);
				}
				WeaponFx.crystalCast(sl, p, tier);
				damage(p, slot, 2);
			}
			case STORMCALLER -> {
				Vector look = p.getLocation().getDirection();
				p.setVelocity(new Vector(look.getX() * 2.4, Math.max(0.25, look.getY() * 1.2), look.getZ() * 2.4));
				p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, false));
				Location pl = p.getLocation();
				BU.sound(pl, Sound.ENTITY_BREEZE_JUMP, SoundCategory.PLAYERS, 1.5F, 1.2F);
				BU.sound(pl, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.6F, 1.6F);
				WeaponFx.stormTrail(sl, p, 7);
				sl.spawnParticle(Particle.GUST, pl.getX(), pl.getY() + 0.5, pl.getZ(), 3, 0.3, 0.3, 0.3, 0);
				Fx.later(7, () -> {
					if (p.isDead() || !p.isOnline()) return;
					Location l = p.getLocation();
					WeaponFx.stormImpact(p.getWorld(), p, l.toVector(), tier);
					BU.lightning(p.getWorld(), l.getX(), l.getY(), l.getZ());
					List<LivingEntity> hit = around(p, 3.5 + rad);
					for (LivingEntity t : hit) skillDamage(t, p, 9 * mult);
					// +10/+15 낙뢰 연쇄: 범위 밖 가장 가까운 적 1/2마리
					int chain = lv >= 15 ? 2 : lv >= 10 ? 1 : 0;
					if (chain > 0) {
						List<LivingEntity> far = around(p, 10);
						far.removeAll(hit);
						far.sort(java.util.Comparator.comparingDouble(t -> t.getLocation().distanceSquared(l)));
						for (int k = 0; k < Math.min(chain, far.size()); k++) {
							LivingEntity t = far.get(k);
							Location tl = t.getLocation();
							BU.lightning(p.getWorld(), tl.getX(), tl.getY(), tl.getZ());
							skillDamage(t, p, 6 * mult);
						}
					}
				});
				damage(p, slot, 1);
			}
			case CHRONO_DAGGER -> {
				// 크로노 낫: 전방 150° 부채꼴 베기 → 이어서 시간 정지
				Vector look = p.getLocation().getDirection();
				Vector fw = new Vector(look.getX(), 0, look.getZ());
				if (fw.lengthSquared() < 1e-6) fw = new Vector(0, 0, 1);
				fw.normalize();
				double reach = 5.5 + rad;
				Vector pc = p.getLocation().toVector();
				int cut = 0;
				for (LivingEntity t : around(p, reach)) {
					Vector to = t.getLocation().toVector().subtract(pc).setY(0);
					if (to.lengthSquared() > reach * reach) continue;
					if (to.lengthSquared() > 0.5 && Math.toDegrees(to.angle(fw)) > 75) continue;
					skillDamage(t, p, 7.0 * mult);
					t.setVelocity(t.getVelocity().add(fw.clone().multiply(0.35).setY(0.15)));
					cut++;
				}
				WeaponFx.scytheArc(sl, p, fw, reach, tier);
				int n = 0;
				int dur = 80 + 4 * lv;
				for (LivingEntity t : around(p, 8 + rad * 2)) {
					t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 9));
					t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, dur, 2));
					t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, dur, 0));
					if (lv >= 10) skillDamage(t, p, 4 * mult);
					Location tl = t.getLocation();
					sl.spawnParticle(Particle.REVERSE_PORTAL, tl.getX(), tl.getY() + 1, tl.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
					n++;
				}
				WeaponFx.clockField(sl, p, p.getLocation().toVector(), tier);
				p.sendActionBar(Component.text("☽ 낫 베기 " + cut + "마리 · ⏸ 시간 정지 " + n + "마리", NamedTextColor.AQUA));
				damage(p, slot, 2);
			}
			default -> { return; }
		}
		p.setCooldown(h.cooldownGroup(), (int) Math.round(h.cooldown * com.hobbang.enhance.item.EnhanceTier.bossCooldownMult(lv)));
		p.swingHand(slot);
	}

	private static void damage(Player p, EquipmentSlot slot, int amount) {
		if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
		p.damageItemStack(slot, amount);
	}

	/** 근접 타격 시 테마 연출 (+ 크로노 낫 구속) */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onMelee(EntityDamageByEntityEvent e) {
		if (skill || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
		if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) return;
		ItemStack w = p.getInventory().getItemInMainHand();
		HItem h = HItem.of(w);
		if (h == null || !h.isWeapon()) return;
		WeaponFx.Theme theme = switch (h) {
			case SMITH_HAMMER -> WeaponFx.Theme.ABYSS;
			case CRYSTAL_GREATSWORD -> WeaponFx.Theme.CRYSTAL;
			case STORMCALLER -> WeaponFx.Theme.STORM;
			default -> WeaponFx.Theme.CHRONO;
		};
		if (h == HItem.CHRONO_DAGGER) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
		WeaponFx.onHit(w, target, theme);
	}
}
