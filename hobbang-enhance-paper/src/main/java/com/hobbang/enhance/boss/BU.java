package com.hobbang.enhance.boss;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** 보스/무기 공용 유틸 (Fabric DisplayBoss 의 정적 헬퍼 포팅, v1.5.1 파티클 감량 그대로) */
public final class BU {
	private BU() {}

	public static Particle.DustOptions dust(int rgb, float size) {
		return new Particle.DustOptions(Color.fromRGB(rgb), size);
	}

	public static void ring(World w, Particle p, Object data, Vector c, double r, int pts) {
		pts = Math.max(8, pts * 3 / 5);   // v1.5.1: 점 수 40% 감소
		for (int i = 0; i < pts; i++) {
			double a = Math.PI * 2 * i / pts;
			w.spawnParticle(p, c.getX() + Math.cos(a) * r, c.getY() + 0.1, c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, data);
		}
	}

	public static void line(World w, Particle p, Object data, Vector a, Vector b, double step) {
		Vector d = b.clone().subtract(a);
		double len = d.length();
		if (len < 1e-3) return;
		Vector u = d.multiply(1 / len);
		step *= 1.6;   // v1.5.1: 선 파티클 간격 넓힘
		for (double s = 0; s <= len; s += step) {
			w.spawnParticle(p, a.getX() + u.getX() * s, a.getY() + u.getY() * s, a.getZ() + u.getZ() * s, 1, 0, 0, 0, 0, data);
		}
	}

	public static void particle(World w, Particle p, double x, double y, double z, int n, double dx, double dy, double dz, double speed) {
		w.spawnParticle(p, x, y, z, n, dx, dy, dz, speed);
	}

	public static void sound(Location at, Sound s, SoundCategory c, float v, float p) {
		at.getWorld().playSound(at, s, c, v, p);
	}

	public static double distToSegment(Vector p, Vector a, Vector b) {
		Vector ab = b.clone().subtract(a);
		double t = ab.lengthSquared() < 1e-6 ? 0 : Math.max(0, Math.min(1, p.clone().subtract(a).dot(ab) / ab.lengthSquared()));
		return p.distance(a.clone().add(ab.multiply(t)));
	}

	/** 아래쪽 지면 높이 */
	public static double groundY(World w, double x, double y, double z) {
		int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
		// v1.3.0: 로드 안 된 청크를 패턴이 동기 로드/생성하지 않게 (센티넬 3페이즈 등 순간 렉 원인 후보)
		if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return y;
		for (int i = 0; i < 24; i++) {
			Block b = w.getBlockAt(bx, by - i, bz);
			if (b.isCollidable()) return by - i + 1;
		}
		return y - 24;
	}

	public static boolean solidAt(World w, Vector p) {
		if (!w.isChunkLoaded(p.getBlockX() >> 4, p.getBlockZ() >> 4)) return false;
		return w.getBlockAt(p.getBlockX(), p.getBlockY(), p.getBlockZ()).isCollidable();
	}

	public static boolean valid(Player p) {
		return p.isValid() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE;
	}

	/** 생존/모험 모드 플레이어 (Fabric victims) */
	public static List<Player> victims(World w, BoundingBox box) {
		List<Player> out = new ArrayList<>();
		for (Entity e : w.getNearbyEntities(box, e -> e instanceof Player)) if (valid((Player) e)) out.add((Player) e);
		return out;
	}

	public static BoundingBox box(Vector c, double rx, double ry, double rz) {
		return new BoundingBox(c.getX() - rx, c.getY() - ry, c.getZ() - rz, c.getX() + rx, c.getY() + ry, c.getZ() + rz);
	}

	public static List<Player> nearbyPlayers(Entity me, double r) {
		List<Player> out = new ArrayList<>();
		Location l = me.getLocation();
		for (Player p : me.getWorld().getPlayers()) {
			if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(l) < r * r) out.add(p);
		}
		return out;
	}

	public static void say(Entity me, Component msg) {
		for (Player p : nearbyPlayers(me, 64)) p.sendMessage(msg);
	}

	public static DamageSource mob(Entity src) {
		return DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(src).withDirectEntity(src).build();
	}

	public static DamageSource magic(Entity src) {
		return DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(src).withDirectEntity(src).build();
	}

	public static DamageSource thorns(Entity src) {
		return DamageSource.builder(DamageType.THORNS).withCausingEntity(src).withDirectEntity(src).build();
	}

	public static DamageSource player(Player p) {
		return DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(p).withDirectEntity(p).build();
	}

	/** 피해가 들어갈 수 있는 상태인지 (무적 시간 절반 이하) */
	public static boolean hurtable(LivingEntity e) {
		return e.getNoDamageTicks() <= e.getMaximumNoDamageTicks() / 2;
	}

	/** 피해 + 밀치기 (Fabric DisplayBoss.hit) */
	public static void hit(LivingEntity e, Entity src, double dmg, Vector from, double push, double up) {
		boolean ok = hurtable(e);
		e.damage(dmg, mob(src));
		if (ok && !e.isDead()) {
			Vector d = e.getLocation().toVector().subtract(from);
			d.setY(0);
			if (d.lengthSquared() > 1e-4) d.normalize().multiply(push);
			e.setVelocity(e.getVelocity().add(new Vector(d.getX(), up, d.getZ())));
		}
	}

	public static void lightning(World w, double x, double y, double z) {
		w.strikeLightningEffect(new Location(w, x, y, z));
	}
}
