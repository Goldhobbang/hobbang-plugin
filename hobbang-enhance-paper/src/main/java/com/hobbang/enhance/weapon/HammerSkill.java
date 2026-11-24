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
import org.bukkit.event.player.PlayerQuitEvent;
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
	private static final Map<UUID, RedSky> RED_SKIES = new HashMap<>();

	private static final class RedSky {
		final net.kyori.adventure.bossbar.BossBar bar;
		final java.util.List<Player> viewers;
		RedSky(net.kyori.adventure.bossbar.BossBar bar, java.util.List<Player> viewers) {
			this.bar = bar;
			this.viewers = viewers;
		}
		void clear() {
			for (Player v : viewers) {
				if (!v.isOnline()) continue;
				v.hideBossBar(bar);
				v.setWorldBorder(null);
			}
		}
	}

	private static void beginRedSky(Player p) {
		endRedSky(p.getUniqueId());
		World w = p.getWorld();
		org.bukkit.WorldBorder wb = org.bukkit.Bukkit.createWorldBorder();
		wb.setCenter(p.getLocation());
		wb.setSize(120.0);
		wb.setWarningDistance(100000);
		net.kyori.adventure.bossbar.BossBar bar = net.kyori.adventure.bossbar.BossBar.bossBar(
			net.kyori.adventure.text.Component.text("☠ 천공 강림", net.kyori.adventure.text.format.NamedTextColor.RED),
			1.0F,
			net.kyori.adventure.bossbar.BossBar.Color.RED,
			net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS,
			java.util.EnumSet.of(net.kyori.adventure.bossbar.BossBar.Flag.DARKEN_SCREEN, net.kyori.adventure.bossbar.BossBar.Flag.CREATE_WORLD_FOG)
		);
		java.util.List<Player> viewers = new java.util.ArrayList<>();
		for (Player near : w.getPlayers()) {
			if (near.getLocation().distanceSquared(p.getLocation()) <= 56 * 56) {
				near.showBossBar(bar);
				near.setWorldBorder(wb);
				viewers.add(near);
			}
		}
		RED_SKIES.put(p.getUniqueId(), new RedSky(bar, viewers));
	}

	private static void endRedSky(UUID id) {
		RedSky sky = RED_SKIES.remove(id);
		if (sky != null) sky.clear();
	}
	/** 거리 45칸(20강 천공 내려찍기 최대 높이)에서 낙하 보너스 최대 +31.5, 20강 최대 피해 50.0 */
	static final double SLAM_FULL_DISTANCE = 45.0;
	public static final double BASE_DMG = 10.0;
	public static final double ENHANCE_DMG_STEP = 0.05;
	public static final double FALL_DMG_PER_BLOCK = 0.7;

	public static double groundDamage(int lv) {
		return BASE_DMG * (1.0 + ENHANCE_DMG_STEP * com.hobbang.enhance.item.EnhanceTier.bossSkillLv(lv));
	}

	public static double fallBonus(double dist) {
		return Math.min(SLAM_FULL_DISTANCE, Math.max(0.0, dist)) * FALL_DMG_PER_BLOCK;
	}

	/** 20강 최대 높이(45칸) 낙하만 50.0에 도달 가능; 저강화·평지·천장 막힘 저고도는 영혼 중첩으로도 낮게 상한 */
	public static double capDamage(int lv, double dist) {
		int slv = com.hobbang.enhance.item.EnhanceTier.bossSkillLv(lv);
		double gCap = 14.0 + 0.7 * slv;                       // +3: 14.0, +15: 22.4, +20: 25.9
		double fullCap = lv >= 20 ? 50.0 : 20.0 + 1.5 * slv;  // +3: 20.0, +15: 38.0, +19: 44.0, +20: 50.0
		double hFrac = Math.min(1.0, Math.max(0.0, dist / SLAM_FULL_DISTANCE));
		double cap = gCap + (fullCap - gCap) * hFrac;
		if (lv < 20 || dist < 44.0) cap = Math.min(48.0, cap);
		return cap;
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		Player p = e.getPlayer();
		NO_FALL.remove(p.getUniqueId());
		endRedSky(p.getUniqueId());
		if (!p.hasGravity()) p.setGravity(true);
	}

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

	public static double baseRadius(int lv) {
		int slv = com.hobbang.enhance.item.EnhanceTier.bossSkillLv(lv);
		return 2.5 + 0.05 * slv + 0.5 * (slv / 5);
	}

	static void cast(Player p, ItemStack stack, int lv, double mult, double rad, int tier) {
		double radius = baseRadius(lv) + Souls.radiusAdd(stack, lv);   // 3강 2.5칸, 이후 강화당 +0.05칸, 5강화당 +0.5칸
		double soulsMult = Souls.dmgMult(stack, lv);
		if (!p.isOnGround() && !p.isGliding() && !p.isSwimming() && !p.isInWater() && groundDistance(p) >= 2.5) slam(p, lv, radius, soulsMult, tier, p.getLocation().getY());
		else if (lv >= 15) skySlam(p, lv, radius, soulsMult, tier);
		else quake(p, lv, radius, Math.min(capDamage(lv, 0), groundDamage(lv) * soulsMult), tier);
	}

	private static double jumpVelocityForHeight(double targetHeight) {
		double lo = 0.0, hi = 6.0;
		for (int iter = 0; iter < 24; iter++) {
			double mid = (lo + hi) * 0.5;
			double y = 0, vy = mid;
			while (vy > 0) {
				y += vy;
				vy = (vy - 0.08) * 0.98;
			}
			if (y < targetHeight) lo = mid;
			else hi = mid;
		}
		return (lo + hi) * 0.5;
	}

	/** 15강~ 지상 지진: 위더 소환 소리와 함께 20칸(+15강 초과당 +5칸) 포물선 도약 → 최고점 지나 자연 낙하 시작 시 바라보는 방향으로 다이브 */
	private static void skySlam(Player p, int lv, double radius, double soulsMult, int tier) {
		World sl = p.getWorld();
		double wantUp = 20.0 + 5.0 * (lv - 15);
		RayTraceResult ceil = sl.rayTraceBlocks(p.getEyeLocation(), new Vector(0, 1, 0), wantUp + 1.0, FluidCollisionMode.NEVER, true);
		if (ceil != null) {
			double maxUp = ceil.getHitPosition().getY() - p.getLocation().getY() - p.getHeight() - 0.2;
			if (maxUp < 1.0) {
				quake(p, lv, radius, Math.min(capDamage(lv, 0), groundDamage(lv) * soulsMult), tier);
				return;
			}
			wantUp = Math.min(wantUp, maxUp);
		}
		final UUID id = p.getUniqueId();
		NO_FALL.put(id, Long.MAX_VALUE);
		beginRedSky(p);
		p.setGravity(true);
		p.setFallDistance(0);
		double v0 = jumpVelocityForHeight(wantUp + 0.2);
		p.setVelocity(new Vector(0, v0, 0));
		BU.sound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.4F, 0.9F);
		sl.spawnParticle(Particle.EXPLOSION, p.getLocation().getX(), p.getLocation().getY() + 0.3, p.getLocation().getZ(), 4, 0.8, 0.2, 0.8, 0);
		sl.spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().getX(), p.getLocation().getY() + 0.5, p.getLocation().getZ(), 24, 0.6, 0.3, 0.6, 0.08);
		new BukkitRunnable() {
			int t = 0;
			int fallTicks = 0;
			double prevY = p.getLocation().getY();
			double peakY = prevY;

			@Override public void run() {
				if (!p.isOnline() || p.isDead() || p.getWorld() != sl) {
					NO_FALL.remove(id);
					endRedSky(id);
					cancel();
					return;
				}
				t++;
				p.setFallDistance(0);
				Location cur = p.getLocation();
				if (cur.getY() > peakY) peakY = cur.getY();
				double vy = cur.getY() - prevY;
				prevY = cur.getY();
				if (t % 2 == 0) {
					sl.spawnParticle(Particle.CRIMSON_SPORE, cur.getX(), cur.getY() + 6.0, cur.getZ(), 28, 14.0, 4.0, 14.0, 0.02);
					sl.spawnParticle(Particle.SOUL_FIRE_FLAME, cur.getX(), cur.getY() + 0.2, cur.getZ(), 4, 0.25, 0.2, 0.25, 0.02);
					sl.spawnParticle(Particle.CLOUD, cur.getX(), cur.getY(), cur.getZ(), 3, 0.3, 0.1, 0.3, 0.03);
				}
				if (t > 4 && (vy < -0.05 || p.getVelocity().getY() < -0.15)) {
					fallTicks++;
					if (fallTicks >= 3) {
						BU.sound(cur, Sound.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 1.0F, 0.65F);
						cancel();
						slam(p, lv, radius, soulsMult, tier, peakY);
						return;
					}
				}
				if ((t > 3 && p.isOnGround()) || t >= 80) {
					cancel();
					slam(p, lv, radius, soulsMult, tier, peakY);
				}
			}
		}.runTaskTimer(HobbangPlugin.get(), 1L, 1L);
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
	private static void slam(Player p, int lv, double radius, double soulsMult, int tier, double peakY) {
		final double startY = Math.max(peakY, p.getLocation().getY());
		final Vector fixedDir = slamDir(p);
		final UUID id = p.getUniqueId();
		NO_FALL.put(id, Long.MAX_VALUE);
		BU.sound(p.getLocation(), Sound.ITEM_MACE_SMASH_AIR, SoundCategory.PLAYERS, 1.6F, 0.7F);
		new BukkitRunnable() {
			int t = 0;

			@Override public void run() {
				if (!p.isOnline() || p.isDead() || p.getWorld() == null) { NO_FALL.remove(id); endRedSky(id); cancel(); return; }
				t++;
				double speed = Math.min(3.2, 0.8 + 0.4 * t);   // 가속: 틱마다 빨라짐
				Vector dir = fixedDir.clone();
				boolean landed = t > 1 && (p.isOnGround() || groundDistance(p) < 0.2);
				boolean crashed = t > 1 && slamCrash(p, dir, speed);
				if (landed || crashed || t > 100) {
					cancel();
					endRedSky(id);
					NO_FALL.put(id, System.currentTimeMillis() + 1500);   // 착지 직후 낙하 피해 처리까지 여유
					p.setFallDistance(0);
					if (crashed) p.setVelocity(new Vector(0, 0, 0));
					double dist = Math.max(0, startY - p.getLocation().getY());
					double k = 1 + Math.min(1.0, dist / SLAM_FULL_DISTANCE);   // 0칸 1배 … 45칸+ 2배 (범위)
					double hitDmg = Math.min(capDamage(lv, dist), (groundDamage(lv) + fallBonus(dist)) * soulsMult);
					quake(p, lv, radius * k, hitDmg, tier);
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
