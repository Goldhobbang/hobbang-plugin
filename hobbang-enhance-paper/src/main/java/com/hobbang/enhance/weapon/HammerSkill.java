package com.hobbang.enhance.weapon;

import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.boss.BU;
import com.hobbang.enhance.boss.Fx;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 대장장이의 망치 지진.
 * - 3강부터 해금. 콤보(여진 1·2회)는 없다: 충격파 범위는 해금(3강) 후 5강화당 +1칸. 15강은 맞은 적 구속.
 * - 공중에서 우클릭하면 바라보는 방향으로 가속 자유낙하 → 낙하 피해는 무시, 범위·피해는 낙하 거리에 비례(30칸에서 최대 ×2, 그 이상은 30과 동일).
 * - 영혼 수집(Souls)만큼 피해·범위가 커진다.
 */
public final class HammerSkill implements Listener {
	/** 낙하 급강하 중이거나 막 착지한 플레이어 → 낙하 피해 무시 (만료 시각 ms) */
	private static final Map<UUID, Long> NO_FALL = new HashMap<>();
	/** 거리 30칸에서 범위·피해 최대 ×2. 그 이상은 30과 동일, 이하는 비례 */
	static final double SLAM_FULL_DISTANCE = 30.0;

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onFall(EntityDamageEvent e) {
		if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
		Long until = NO_FALL.get(p.getUniqueId());
		if (until == null) return;
		if (until < System.currentTimeMillis()) { NO_FALL.remove(p.getUniqueId()); return; }
		e.setCancelled(true);
	}

	private static double groundDistance(Player p) {
		RayTraceResult r = p.getWorld().rayTraceBlocks(p.getLocation(), new Vector(0, -1, 0), 80, FluidCollisionMode.NEVER, true);
		return r == null ? 0 : p.getLocation().getY() - r.getHitPosition().getY();
	}

	static void cast(Player p, ItemStack stack, int lv, double mult, double rad, int tier) {
		double radius = 5.0 + rad + (com.hobbang.enhance.item.EnhanceTier.bossSkillLv(lv) / 5) + Souls.radiusAdd(stack, lv);   // 3강 해금부터 5강화당 +1칸
		double dmg = 8.0 * mult * Souls.dmgMult(stack, lv);
		if (!p.isOnGround() && !p.isGliding() && !p.isSwimming() && !p.isInWater() && groundDistance(p) >= 2.5) slam(p, lv, radius, dmg, tier);
		else quake(p, lv, radius, dmg, tier);
	}

	/** 일반 지진: 반경 안의 적에게 피해 + 밀치기(뜨는 높이는 0.8) */
	private static void quake(Player p, int lv, double radius, double dmg, int tier) {
		World sl = p.getWorld();
		Vector c = p.getLocation().toVector();
		WeaponFx.hammerCast(sl, p);
		WeaponFx.earthquake(sl, p, c, tier, radius);
		for (LivingEntity t : WeaponListener.around(p, radius + 1)) {
			Vector flat = t.getLocation().toVector().subtract(c).setY(0);
			if (flat.length() > radius + t.getWidth() / 2 || Math.abs(t.getLocation().getY() - c.getY()) > 4) continue;
			WeaponListener.skillDamage(t, p, dmg);
			Vector push = flat;
			if (push.lengthSquared() > 1e-6) push.normalize().multiply(0.6);
			t.setVelocity(t.getVelocity().add(new Vector(push.getX(), 0.8, push.getZ())));   // 띄움 2배
			if (lv >= 15) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
		}
	}

	/** 공중 사용: 시선 방향으로 가속 급강하(하강각 최소 45°, 더 아래를 보면 그 각도 그대로). 착지·벽·적 충돌 시 낙하 거리 비례 충격파 */
	private static void slam(Player p, int lv, double radius, double dmg, int tier) {
		final double startY = p.getLocation().getY();
		final UUID id = p.getUniqueId();
		NO_FALL.put(id, Long.MAX_VALUE);
		BU.sound(p.getLocation(), Sound.ITEM_MACE_SMASH_AIR, SoundCategory.PLAYERS, 1.6F, 0.7F);
		new BukkitRunnable() {
			int t = 0;

			@Override public void run() {
				if (!p.isOnline() || p.isDead() || p.getWorld() == null) { NO_FALL.remove(id); cancel(); return; }
				t++;
				double speed = Math.min(3.2, 0.8 + 0.4 * t);   // 가속: 틱마다 빨라짐
				Vector dir = slamDir(p);
				boolean landed = t > 1 && (p.isOnGround() || groundDistance(p) < 0.2);
				boolean crashed = t > 1 && slamCrash(p, dir, speed);
				if (landed || crashed || t > 100) {
					cancel();
					NO_FALL.put(id, System.currentTimeMillis() + 1500);   // 착지 직후 낙하 피해 처리까지 여유
					p.setFallDistance(0);
					if (crashed) p.setVelocity(new Vector(0, 0, 0));
					double dist = Math.max(0, startY - p.getLocation().getY());
					double k = 1 + Math.min(1.0, dist / SLAM_FULL_DISTANCE);   // 0칸 1배 … 30칸+ 2배 (범위·피해)
					quake(p, lv, radius * k, dmg * k, tier);
					return;
				}
				p.setVelocity(dir.multiply(speed));
				p.setFallDistance(0);
				World w = p.getWorld();
				var l = p.getLocation();
				w.spawnParticle(Particle.CLOUD, l.getX(), l.getY() + 1.0, l.getZ(), 3, 0.25, 0.4, 0.25, 0.02);
				w.spawnParticle(Particle.FLAME, l.getX(), l.getY() + 0.6, l.getZ(), 2, 0.2, 0.3, 0.2, 0.02);
			}
		}.runTaskTimer(HobbangPlugin.get(), 0L, 1L);
	}

	/** t398: 최소 하강각. 45°보다 완만하게(수평·위 포함) 보면 45°로 판정, 더 아래(~90°)를 보면 시선 각도 그대로 */
	static final float SLAM_MIN_DIVE = 45f;

	/** 수평 방향 = 시선 yaw, 하강각 = max(45°, 시선 pitch) (최대 90° = 수직). */
	private static Vector slamDir(Player p) {
		Location eye = p.getEyeLocation();
		double angle = Math.toRadians(Math.max(SLAM_MIN_DIVE, Math.min(90f, eye.getPitch())));   // pitch + = 아래
		double yaw = Math.toRadians(eye.getYaw());
		double h = Math.cos(angle);
		return new Vector(-Math.sin(yaw) * h, -Math.sin(angle), Math.cos(yaw) * h);
	}

	/** 급강하 경로의 벽(바닥면 제외)·천장·엔티티에 부딪히면 true */
	private static boolean slamCrash(Player p, Vector dir, double speed) {
		World w = p.getWorld();
		Vector d = dir.clone();
		if (d.lengthSquared() < 1e-6) d = new Vector(0, -1, 0);
		else d.normalize();
		Location start = p.getLocation().add(0, p.getHeight() * 0.5, 0);
		double dist = Math.max(0.7, speed / 20.0 + 0.55);
		RayTraceResult br = w.rayTraceBlocks(start, d, dist, FluidCollisionMode.NEVER, true);
		if (br != null && br.getHitBlockFace() != BlockFace.UP) return true;
		RayTraceResult er = w.rayTraceEntities(start, d, dist, 0.45, e ->
			e instanceof LivingEntity le && e != p && !le.isDead()
				&& !(e instanceof ArmorStand as && as.isMarker()));
		return er != null && er.getHitEntity() != null;
	}
}
