package com.hobbang.enhance.boss;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * v1.2.0 보스 신규 패턴 공용 부품. 전부 Fx(비영구 디스플레이 + 수명) 위에서 돌아가므로 보스가 죽거나 언로드되면 같이 사라진다.
 * 모든 공격은 예고(텔레그래프)가 먼저 나오고, 피해는 예고가 끝난 뒤에만 들어간다.
 * 파티클은 2틱 간격, 점 수 상한을 두어 보스 1마리당 틱 비용을 낮게 유지한다.
 */
public final class BossPatterns {
	private BossPatterns() {}

	public static Particle.DustTransition dt(int from, int to, float size) {
		return new Particle.DustTransition(Color.fromRGB(from), Color.fromRGB(to), size);
	}

	private static void warn(Boss b, String msg) {
		BU.say(b.mob, Component.text("⚠ " + msg, NamedTextColor.GOLD));
	}

	private static double ground(World w, Vector at) {
		return BU.groundY(w, at.getX(), at.getY() + 1.5, at.getZ());
	}

	/** 지면 원 (예고용) */
	public static void circle(World w, Vector c, double r, Object data, Particle p) {
		int pts = (int) Math.max(10, Math.min(40, r * 6));
		for (int i = 0; i < pts; i++) {
			double a = Math.PI * 2 * i / pts;
			w.spawnParticle(p, c.getX() + Math.cos(a) * r, c.getY() + 0.12, c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, data);
		}
	}

	// ───────── 1. 충격파 고리: 퍼져 나가는 고리. 점프로 피한다 ─────────
	public static void shockRing(Boss b, Vector center0, double maxR, double speed, double dmg, int warnTicks, int from, int to) {
		World w = b.mob.getWorld();
		Vector c = center0.clone();
		c.setY(ground(w, c));
		Set<UUID> hit = new HashSet<>();
		Particle.DustTransition data = dt(from, to, 1.8F);
		Fx.spawn(w, b.uuid, c, warnTicks + (int) Math.ceil(maxR / speed) + 2, (fx, world) -> {
			int t = fx.age;
			if (t <= warnTicks) {
				if (t % 3 == 0) {
					circle(world, c, 1.2 + 0.6 * Math.sin(t * 0.5), data, Particle.DUST_COLOR_TRANSITION);
					world.spawnParticle(Particle.END_ROD, c.getX(), c.getY() + 0.3, c.getZ(), 2, 0.4, 0.1, 0.4, 0.02);
				}
				if (t == warnTicks) {
					BU.sound(new Location(world, c.getX(), c.getY(), c.getZ()), Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.5F, 1.4F);
					world.spawnParticle(Particle.SONIC_BOOM, c.getX(), c.getY() + 0.5, c.getZ(), 1, 0, 0, 0, 0);
				}
				return;
			}
			double r = (t - warnTicks) * speed;
			if (t % 2 == 0) circle(world, c, r, data, Particle.DUST_COLOR_TRANSITION);
			for (Player p : BU.victims(world, BU.box(c, r + 1, 2.5, r + 1))) {
				if (hit.contains(p.getUniqueId())) continue;
				Location pl = p.getLocation();
				double d = Math.hypot(pl.getX() - c.getX(), pl.getZ() - c.getZ());
				if (Math.abs(d - r) < 0.9 && pl.getY() - c.getY() < 0.8) {
					hit.add(p.getUniqueId());
					BU.hit(p, b.mob, dmg, c, 0.9, 0.45);
				}
			}
		});
	}

	// ───────── 2. 광선: 예고선 → 발사 (디스플레이 빔 + END_ROD) ─────────
	public static void beam(Boss b, Supplier<Vector> origin, Vector dir0, double len, int warn, int fire, double dmg, Material look, int rgb) {
		World w = b.mob.getWorld();
		Vector dir = dir0.clone().normalize();
		Particle.DustOptions dust = BU.dust(rgb, 0.9F);
		final BlockDisplay[] beam = {null};
		Set<UUID> hit = new HashSet<>();
		Fx.spawn(w, b.uuid, origin.get(), warn + fire, (fx, world) -> {
			int t = fx.age;
			Vector o = origin.get();
			Vector end = o.clone().add(dir.clone().multiply(len));
			if (t < warn) {
				if (t % 3 == 0) BU.line(world, Particle.DUST, dust, o, end, 0.9);
				if (t == 1) BU.sound(new Location(world, o.getX(), o.getY(), o.getZ()), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.5F, 1.6F);
				return;
			}
			if (beam[0] == null) {
				fx.pos = o.clone();
				beam[0] = fx.addBlock(look.createBlockData(), true);
				Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()));
				float wdt = 0.5F;
				Vector3f tr = rot.transform(new Vector3f(-wdt / 2, -wdt / 2, 0));
				Fx.shape(beam[0], tr, rot, new Vector3f(wdt, wdt, (float) len), 2);
				world.spawnParticle(Particle.SONIC_BOOM, o.getX(), o.getY(), o.getZ(), 1, 0, 0, 0, 0);
				BU.sound(new Location(world, o.getX(), o.getY(), o.getZ()), Sound.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 2F, 1.6F);
			}
			if (t % 2 == 0) BU.line(world, Particle.END_ROD, null, o, end, 1.4);
			if ((t - warn) % 10 == 0) hit.clear();
			for (Player p : BU.victims(world, BU.box(o.clone().add(end).multiply(0.5), len / 2 + 1, len / 2 + 2, len / 2 + 1))) {
				if (hit.contains(p.getUniqueId())) continue;
				Vector pc = p.getLocation().toVector().add(new Vector(0, 0.9, 0));
				if (BU.distToSegment(pc, o, end) < 1.1) {
					hit.add(p.getUniqueId());
					BU.hit(p, b.mob, dmg, o, 0.6, 0.2);
				}
			}
		});
	}

	/** 지면을 따라 보스 중심에서 n방향으로 뻗는 광선 (십자/엑스) */
	public static void groundBeams(Boss b, Vector center, int n, double angle0, double len, int warn, int fire, double dmg, Material look, int rgb) {
		World w = b.mob.getWorld();
		Vector c = center.clone();
		c.setY(ground(w, c) + 0.6);
		for (int i = 0; i < n; i++) {
			double a = angle0 + Math.PI * 2 * i / n;
			beam(b, () -> c, new Vector(Math.cos(a), 0, Math.sin(a)), len, warn, fire, dmg, look, rgb);
		}
	}

	// ───────── 3. 운석: 붉은 원 예고 → 떨어지는 블록 → 폭발 ─────────
	public static void meteor(Boss b, Vector at0, double radius, int warn, double dmg, Material rock, boolean fire) {
		World w = b.mob.getWorld();
		Vector at = at0.clone();
		at.setY(ground(w, at));
		Particle.DustOptions red = BU.dust(0xFF3010, 1.4F);
		final BlockDisplay[] rockD = {null};
		Fx fxm = Fx.spawn(w, b.uuid, at.clone().add(new Vector(0, 14, 0)), warn + 1, null);
		Fx.spawn(w, b.uuid, at, warn + 1, (fx, world) -> {
			int t = fx.age;
			if (t % 3 == 0) circle(world, at, radius * Math.min(1, 0.4 + t / (double) warn), red, Particle.DUST);
			if (t == warn - 14 && rockD[0] == null) {
				rockD[0] = fxm.addBlock(rock.createBlockData(), true);
				Fx.shape(rockD[0], new Vector3f(-0.6F, -0.6F, -0.6F), new Quaternionf(), new Vector3f(1.2F, 1.2F, 1.2F), 2);
				fxm.vel = new Vector(0, -1.0, 0);
			}
			if (rockD[0] != null && t % 2 == 0) {
				Vector rp = fxm.pos;
				world.spawnParticle(fire ? Particle.FLAME : Particle.END_ROD, rp.getX(), rp.getY() + 0.5, rp.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
			}
			if (t == warn) {
				Location l = new Location(world, at.getX(), at.getY(), at.getZ());
				world.spawnParticle(Particle.EXPLOSION, l, 2, 0.4, 0.2, 0.4, 0);
				world.spawnParticle(fire ? Particle.LAVA : Particle.END_ROD, l, 10, radius / 2, 0.2, radius / 2, 0.1);
				BU.sound(l, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.4F, 0.9F);
				for (Player p : BU.victims(world, BU.box(at, radius, 2.5, radius))) {
					if (p.getLocation().toVector().setY(at.getY()).distance(at) > radius) continue;
					BU.hit(p, b.mob, dmg, at, 0.6, 0.5);
					if (fire) p.setFireTicks(Math.max(p.getFireTicks(), 60));
				}
			}
		});
	}

	/** 지금 플레이어가 서 있는 자리마다 운석 (잔상 폭발 등에 재사용) */
	public static void meteorsOnPlayers(Boss b, double range, double radius, int warn, double dmg, Material rock, boolean fire) {
		for (Player p : BU.victims(b.mob.getWorld(), b.mob.getBoundingBox().expand(range))) meteor(b, p.getLocation().toVector(), radius, warn, dmg, rock, fire);
	}

	// ───────── 4. 화염 나선: 회전하는 팔. 처음 1초는 예고(피해 없음) ─────────
	public static void spiral(Boss b, int arms, double radius, int warn, int duration, double dmg, Particle armParticle, int rgb) {
		World w = b.mob.getWorld();
		Set<UUID> hit = new HashSet<>();
		Particle.DustOptions d = BU.dust(rgb, 1.2F);
		double dir = Math.random() < 0.5 ? 1 : -1;
		Fx.spawn(w, b.uuid, b.mob.getLocation().toVector(), warn + duration, (fx, world) -> {
			int t = fx.age;
			Vector c = b.mob.getLocation().toVector();
			c.setY(ground(world, c) + 0.4);
			double base = dir * t * 0.07;
			boolean live = t > warn;
			if (t % 2 == 0) for (int a = 0; a < arms; a++) {
				double ang = base + Math.PI * 2 * a / arms;
				for (double r = 1.2; r <= radius; r += live ? 0.9 : 1.6) {
					double sa = ang + r * 0.18;
					world.spawnParticle(live ? armParticle : Particle.DUST, c.getX() + Math.cos(sa) * r, c.getY(), c.getZ() + Math.sin(sa) * r, 1, 0, 0, 0, 0, live ? null : d);
				}
			}
			if (!live) return;
			if (t % 20 == 0) hit.clear();
			for (Player p : BU.victims(world, BU.box(c, radius, 2, radius))) {
				if (hit.contains(p.getUniqueId())) continue;
				Location pl = p.getLocation();
				double dx = pl.getX() - c.getX(), dz = pl.getZ() - c.getZ();
				double r = Math.hypot(dx, dz);
				if (r < 1.0 || r > radius || pl.getY() - c.getY() > 1.6) continue;
				double pa = Math.atan2(dz, dx);
				for (int a = 0; a < arms; a++) {
					double sa = base + Math.PI * 2 * a / arms + r * 0.18;
					double diff = Math.abs(Math.atan2(Math.sin(pa - sa), Math.cos(pa - sa)));
					if (diff * r < 0.9) {
						hit.add(p.getUniqueId());
						BU.hit(p, b.mob, dmg, c, 0.5, 0.3);
						if (armParticle == Particle.FLAME || armParticle == Particle.SOUL_FIRE_FLAME) p.setFireTicks(Math.max(p.getFireTicks(), 40));
						break;
					}
				}
			}
			if (t % 20 == 0) BU.sound(b.mob.getLocation(), Sound.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1F, 0.6F);
		});
	}

	// ───────── 5. 소용돌이/중력장: 끌어당긴 뒤 폭발. 바깥으로 달리면 피한다 ─────────
	public static void vortex(Boss b, Vector center0, double radius, int pull, double dmg, int from, int to) {
		World w = b.mob.getWorld();
		Vector c = center0.clone();
		c.setY(ground(w, c) + 0.2);
		Particle.DustTransition data = dt(from, to, 1.5F);
		Fx.spawn(w, b.uuid, c, pull + 2, (fx, world) -> {
			int t = fx.age;
			if (t % 3 == 0) circle(world, c, radius, data, Particle.DUST_COLOR_TRANSITION);
			if (t % 2 == 0) for (int i = 0; i < 3; i++) {
				double a = t * 0.3 + i * Math.PI * 2 / 3;
				double r = radius * (1 - (t % 20) / 20.0);
				world.spawnParticle(Particle.REVERSE_PORTAL, c.getX() + Math.cos(a) * r, c.getY() + 0.5 + (t % 20) * 0.08, c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
			if (t > 10 && t < pull) {
				for (Player p : BU.victims(world, BU.box(c, radius, 3, radius))) {
					Vector to2 = c.clone().subtract(p.getLocation().toVector()).setY(0);
					double d = to2.length();
					if (d < 0.5 || d > radius) continue;
					p.setVelocity(p.getVelocity().add(to2.multiply(0.045 / Math.max(0.6, d / radius))));
				}
				if (t % 10 == 0) BU.sound(new Location(world, c.getX(), c.getY(), c.getZ()), Sound.BLOCK_PORTAL_AMBIENT, SoundCategory.HOSTILE, 1.2F, 1.8F);
			}
			if (t == pull) {
				Location l = new Location(world, c.getX(), c.getY() + 0.5, c.getZ());
				world.spawnParticle(Particle.EXPLOSION_EMITTER, l, 1, 0, 0, 0, 0);
				world.spawnParticle(Particle.END_ROD, l, 30, 1.5, 0.6, 1.5, 0.25);
				BU.sound(l, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2F, 0.7F);
				for (Player p : BU.victims(world, BU.box(c, 3.5, 3, 3.5)))
					if (p.getLocation().toVector().setY(c.getY()).distance(c) < 3.5) BU.hit(p, b.mob, dmg, c, 1.3, 0.7);
			}
		});
	}

	// ───────── 페이즈 전환 연출 (피해 없이 밀쳐내기) ─────────
	public static void phaseBurst(Boss b, int from, int to) {
		World w = b.mob.getWorld();
		Vector c = b.mob.getLocation().toVector();
		c.setY(ground(w, c));
		Particle.DustTransition data = dt(from, to, 2.2F);
		BU.sound(b.mob.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.HOSTILE, 1.2F, 1.5F);
		w.spawnParticle(Particle.SONIC_BOOM, c.getX(), c.getY() + 1.2, c.getZ(), 1, 0, 0, 0, 0);
		Set<UUID> pushed = new HashSet<>();
		Fx.spawn(w, b.uuid, c, 24, (fx, world) -> {
			double r = fx.age * 0.5;
			if (fx.age % 2 == 0) {
				circle(world, c, r, data, Particle.DUST_COLOR_TRANSITION);
				world.spawnParticle(Particle.END_ROD, c.getX(), c.getY() + 1 + fx.age * 0.15, c.getZ(), 2, 0.3, 0.1, 0.3, 0.02);
			}
			for (Player p : BU.victims(world, BU.box(c, r + 1, 3, r + 1))) {
				if (!pushed.add(p.getUniqueId())) continue;
				Vector d = p.getLocation().toVector().subtract(c).setY(0);
				if (d.lengthSquared() > 1e-4) d.normalize();
				p.setVelocity(p.getVelocity().add(d.multiply(0.9).setY(0.35)));
			}
		});
	}

	/** 보스별 색 (from → to) */
	public static int[] colors(BossKind k) {
		return switch (k) {
			case ABYSSAL_SMITH -> new int[]{0xFF6A00, 0x5A00A0};
			case PRISM_COLOSSUS -> new int[]{0xFF7AF5, 0x7AD7FF};
			case TEMPEST_WYVERN -> new int[]{0x9FE8FF, 0x3050FF};
			case CHRONO_SENTINEL -> new int[]{0xFFD84A, 0xFFFFFF};
		};
	}

	/** 보스가 살아 있을 때만 나중에 실행 */
	public static void later(Boss b, int ticks, Runnable r) {
		Fx.later(ticks, () -> { if (!b.dead && b.mob.isValid()) r.run(); });
	}

	public static List<Player> targets(Boss b, double r) {
		List<Player> out = new ArrayList<>();
		for (Player p : b.nearbyPlayers(r)) if (BU.valid(p)) out.add(p);
		return out;
	}

	public static void warnAll(Boss b, String msg) { warn(b, msg); }

}
