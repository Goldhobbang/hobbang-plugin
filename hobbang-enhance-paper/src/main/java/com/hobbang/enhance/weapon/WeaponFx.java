package com.hobbang.enhance.weapon;

import com.hobbang.enhance.boss.BU;
import com.hobbang.enhance.boss.Fx;
import com.hobbang.enhance.item.ItemUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/** 무기 연출 (타격 파티클/사운드, 스킬 이펙트). 전부 바닐라 파티클·디스플레이 */
public final class WeaponFx {
	private WeaponFx() {}

	public enum Theme { ABYSS, CRYSTAL, STORM, CHRONO }

	public static int tierOf(ItemStack s) {
		int l = ItemUtil.getLevel(s);
		return l >= 15 ? 2 : l >= 10 ? 1 : 0;
	}

	private static Location loc(World w, Vector v) {
		return new Location(w, v.getX(), v.getY(), v.getZ());
	}

	/** 근접 타격 시 테마 파티클 */
	public static void onHit(ItemStack stack, LivingEntity target, Theme theme) {
		World sl = target.getWorld();
		Location tl = target.getLocation();
		double x = tl.getX(), y = tl.getY() + target.getHeight() * 0.55, z = tl.getZ();
		int t = tierOf(stack);
		switch (theme) {
			case ABYSS -> {
				sl.spawnParticle(Particle.SOUL_FIRE_FLAME, x, y, z, 10, 0.3, 0.3, 0.3, 0.04);
				sl.spawnParticle(Particle.LAVA, x, y, z, 3, 0.2, 0.2, 0.2, 0);
				sl.spawnParticle(Particle.DUST, x, y, z, 8, 0.35, 0.35, 0.35, 0, BU.dust(0xB050FF, 1.4F));
				BU.sound(tl, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.35F, 1.7F);
			}
			case CRYSTAL -> {
				sl.spawnParticle(Particle.BLOCK, x, y, z, 14, 0.3, 0.3, 0.3, 0.15, Material.AMETHYST_BLOCK.createBlockData());
				sl.spawnParticle(Particle.END_ROD, x, y, z, 4, 0.2, 0.2, 0.2, 0.06);
				BU.sound(tl, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 1.0F, 0.8F);
				BU.sound(tl, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8F, 1.2F);
			}
			case STORM -> {
				sl.spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, 18, 0.35, 0.4, 0.35, 0.25);
				sl.spawnParticle(Particle.DUST, x, y, z, 6, 0.3, 0.3, 0.3, 0, BU.dust(0x8FE8FF, 1.0F));
				BU.sound(tl, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.35F, 1.9F);
			}
			case CHRONO -> {
				sl.spawnParticle(Particle.WAX_ON, x, y, z, 8, 0.3, 0.3, 0.3, 0.3);
				sl.spawnParticle(Particle.DUST, x, y, z, 6, 0.3, 0.3, 0.3, 0, BU.dust(0x60F0D8, 1.0F));
				BU.sound(tl, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.7F, 2.0F);
			}
		}
		if (t >= 1) {
			sl.spawnParticle(t == 2 ? Particle.TOTEM_OF_UNDYING : Particle.ENCHANTED_HIT, x, y, z, t == 2 ? 10 : 8, 0.3, 0.3, 0.3, 0.3);
			if (t == 2) sl.spawnParticle(Particle.END_ROD, x, y, z, 4, 0.1, 0.1, 0.1, 0.08);
		}
	}

	private static BlockData ground(World w, Vector at) {
		Block b = w.getBlockAt((int) Math.floor(at.getX()), (int) Math.floor(at.getY() - 0.5), (int) Math.floor(at.getZ()));
		if (b.getType().isAir()) b = b.getRelative(0, -1, 0);
		if (b.getType().isAir() || b.isLiquid()) return Material.DEEPSLATE.createBlockData();
		return b.getBlockData();
	}

	/** 지진 타일 키프레임: {틱 오프셋, 올라간 비율, 기울어진 비율, 보간 틱} */
	private static final float[][] QUAKE_KEYS = {
		{0, 0.82F, 0.70F, 2}, {2, 1.00F, 1.00F, 2}, {4, 1.04F, 1.00F, 2}, {6, 0.80F, 0.60F, 2}, {8, 0.00F, 0.00F, 2}, {10, 0.07F, 0.00F, 2}, {12, 0.00F, 0.00F, 2}
	};
	private static final int QUAKE_WAVE = 8, QUAKE_TILE_TICKS = 14, QUAKE_MAX_TILES = 150;

	private record QuakeTile(org.bukkit.entity.BlockDisplay d, Vector3f base, int delay, float lift, Quaternionf tilt, Vector3f scale, Vector land, BlockData data) {}

	/**
	 * 망치 지진: 반경 안 땅이 안쪽 → 바깥쪽 순서로 들썩인다.
	 * 타일은 촘촘하게 겹쳐 빈 틈이 보이지 않고(칸 수가 많으면 타일을 키움), 위치·높이·기울기만 조금씩 무작위로 어긋난다.
	 */
	public static void earthquake(World sl, Entity owner, Vector c, int tier, double radius) {
		final double R = Math.max(2.5, radius);
		Location cl = loc(sl, c);
		BU.sound(cl, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.4F, 0.6F);
		BU.sound(cl, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.9F, 0.5F);
		BU.sound(cl, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.6F, 0.7F);
		sl.spawnParticle(Particle.EXPLOSION, c.getX(), c.getY() + 0.3, c.getZ(), 3, 0.6, 0.1, 0.6, 0);
		BlockData gs = ground(sl, c);
		// 바깥으로 퍼지는 혼불 고리 (반경에 비례)
		for (int r = 0; r < 3; r++) {
			final float rad = (float) (R * (r + 1) / 3.0);
			final int n = (int) Math.max(8, Math.round(Math.PI * 2 * rad / 1.1));
			Fx.later(1 + r * 3, () -> {
				BU.sound(cl, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.35F, 0.6F + rad * 0.05F);
				for (int i = 0; i < n; i++) {
					double ang = Math.PI * 2 * i / n;
					double x = c.getX() + Math.cos(ang) * rad, z = c.getZ() + Math.sin(ang) * rad;
					sl.spawnParticle(Particle.SOUL_FIRE_FLAME, x, c.getY() + 0.2, z, 1, 0.1, 0.2, 0.1, 0.03);
					if (i % 2 == 0) sl.spawnParticle(Particle.BLOCK, x, c.getY() + 0.1, z, 2, 0.2, 0.05, 0.2, 0.15, gs);
					if (tier >= 1 && i % 3 == 0) sl.spawnParticle(Particle.LAVA, x, c.getY() + 0.2, z, 1, 0, 0, 0, 0);
				}
			});
		}
		final int life = 2 + QUAKE_WAVE + QUAKE_TILE_TICKS + 1;
		final java.util.List<QuakeTile> tiles = new java.util.ArrayList<>();
		Fx.spawn(sl, owner.getUniqueId(), c, life, (fx, w) -> {
			if (fx.age == 1) { buildQuake(fx, w, c, R, tiles); return; }
			for (QuakeTile t : tiles) {
				int local = fx.age - 2 - t.delay();
				if (local < 0 || local >= QUAKE_TILE_TICKS || !t.d().isValid()) continue;
				for (float[] k : QUAKE_KEYS) {
					if ((int) k[0] != local) continue;
					Quaternionf rot = new Quaternionf().slerp(t.tilt(), k[2]);
					Vector3f pivot = new Vector3f(t.scale()).mul(0.5F);
					Vector3f tr = new Vector3f(t.base()).add(0, t.lift() * k[1], 0).add(pivot.sub(rot.transform(new Vector3f(pivot))));
					if (k[1] == 0 && k[2] == 0) { tr.set(t.base()); rot.identity(); }
					Fx.shape(t.d(), tr, rot, t.scale(), (int) k[3]);
					if (local == 8) w.spawnParticle(Particle.BLOCK, t.land().getX(), t.land().getY(), t.land().getZ(), 1, 0.3, 0.05, 0.3, 0.06, t.data());
					break;
				}
			}
		});
	}

	private static void buildQuake(Fx fx, World w, Vector c, double R, java.util.List<QuakeTile> out) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		double area = Math.PI * R * R;
		double step = Math.max(1.0, Math.sqrt(area / QUAKE_MAX_TILES));   // 타일 한 칸 간격 (칸 수가 많으면 넓힘)
		float size = (float) (step * 1.3);                                 // 간격보다 크게 → 서로 겹쳐서 틈이 안 보임
		Display.Brightness gl = new Display.Brightness(
			w.getBlockAt((int) Math.floor(c.getX()), (int) Math.floor(c.getY() + 0.5), (int) Math.floor(c.getZ())).getLightFromBlocks(),
			w.getBlockAt((int) Math.floor(c.getX()), (int) Math.floor(c.getY() + 0.5), (int) Math.floor(c.getZ())).getLightFromSky());
		int n = (int) Math.ceil(R / step);
		for (int i = -n; i <= n; i++) {
			for (int j = -n; j <= n; j++) {
				if (out.size() >= QUAKE_MAX_TILES + 40) return;
				double px = c.getX() + i * step + rnd.nextDouble(-0.22, 0.22) * step;
				double pz = c.getZ() + j * step + rnd.nextDouble(-0.22, 0.22) * step;
				double dx = px - c.getX(), dz = pz - c.getZ();
				double dist = Math.hypot(dx, dz);
				if (dist > R) continue;
				int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
				if (!w.isChunkLoaded(bx >> 4, bz >> 4)) continue;
				int by = (int) BU.groundY(w, px, c.getY() + 2.0, pz) - 1;
				if (by < w.getMinHeight() || Math.abs(by + 1 - c.getY()) > 2.5) continue;
				Block b = w.getBlockAt(bx, by, bz);
				if (!b.getType().isSolid() || w.getBlockAt(bx, by + 1, bz).isCollidable()) continue;
				double f = Math.min(1.0, dist / R);
				float lift = (float) (1.15 - 0.55 * f + rnd.nextDouble(-0.18, 0.18));
				lift = Math.max(0.35F, Math.min(1.5F, lift));
				int delay = (int) Math.round(f * QUAKE_WAVE);   // 안쪽부터 바깥쪽으로
				Vector3f tiltAxis = new Vector3f((float) rnd.nextDouble(-1, 1), 0, (float) rnd.nextDouble(-1, 1));
				if (tiltAxis.lengthSquared() < 1e-3) tiltAxis.set(1, 0, 0);
				tiltAxis.normalize();
				Quaternionf tilt = new Quaternionf().rotateAxis((float) Math.toRadians(4 + 9 * rnd.nextDouble()), tiltAxis.x, 0, tiltAxis.z)
					.rotateY((float) Math.toRadians(rnd.nextDouble(-18, 18)));
				float h = 1.0F;
				Vector3f scale = new Vector3f(size, h, size);
				// 윗면이 땅 위(+0.02~0.05, 타일끼리 겹쳐도 깜빡이지 않게 약간씩 다르게)에 오도록
				Vector3f base = new Vector3f((float) (px - c.getX()) - size / 2, (float) (by + 1 - c.getY()) - h + 0.02F + rnd.nextFloat() * 0.03F, (float) (pz - c.getZ()) - size / 2);
				BlockData data = b.getBlockData();
				org.bukkit.entity.BlockDisplay d = fx.addBlock(data, false);
				d.setBrightness(gl);
				Fx.shape(d, base, new Quaternionf(), scale, 0);
				out.add(new QuakeTile(d, base, delay, lift, tilt, scale, new Vector(px, by + 1.05, pz), data));
			}
		}
	}

	/** v1.4.0: 대장장이의 망치 — 손에 든 동안 용암 불씨 (10틱마다, 들고 있는 플레이어만) */
	public static void hammerAmbient(org.bukkit.entity.Player p) {
		Location l = p.getLocation();
		Vector fw = l.getDirection().setY(0);
		if (fw.lengthSquared() < 1e-6) fw = new Vector(0, 0, 1);
		fw.normalize();
		Vector right = new Vector(-fw.getZ(), 0, fw.getX());
		Vector h = l.toVector().add(right.multiply(0.45)).add(fw.multiply(0.35)).add(new Vector(0, 1.05, 0));
		World w = p.getWorld();
		w.spawnParticle(Particle.DUST, h.getX(), h.getY(), h.getZ(), 1, 0.12, 0.12, 0.12, 0, BU.dust(0xFF7A1A, 0.8F));
		if (p.getTicksLived() % 20 < 10) w.spawnParticle(Particle.FALLING_LAVA, h.getX(), h.getY() - 0.1, h.getZ(), 1, 0.05, 0, 0.05, 0);
		else w.spawnParticle(Particle.SMALL_FLAME, h.getX(), h.getY() + 0.1, h.getZ(), 1, 0.08, 0.05, 0.08, 0.004);
	}

	/** v1.4.0: 망치 스킬 시전 — 망치에서 용암이 터져 나옴 */
	public static void hammerCast(World w, org.bukkit.entity.Player p) {
		Location l = p.getEyeLocation();
		Vector c = l.toVector().add(l.getDirection().multiply(0.8)).add(new Vector(0, 0.4, 0));
		w.spawnParticle(Particle.LAVA, c.getX(), c.getY(), c.getZ(), 10, 0.3, 0.3, 0.3, 0);
		w.spawnParticle(Particle.FLAME, c.getX(), c.getY(), c.getZ(), 24, 0.25, 0.25, 0.25, 0.08);
		w.spawnParticle(Particle.DUST, c.getX(), c.getY(), c.getZ(), 20, 0.4, 0.4, 0.4, 0, BU.dust(0xFFB040, 1.4F));
		BU.ring(w, Particle.DUST, BU.dust(0xFF5A10, 1.6F), p.getLocation().toVector().add(new Vector(0, 0.15, 0)), 2.0, 24);
	}

	/** 대검 스킬 시전 연출 */
	public static void crystalCast(World sl, LivingEntity p, int tier) {
		Vector look = p.getLocation().getDirection();
		Vector o = p.getLocation().toVector().add(new Vector(0, 1.1, 0));
		sl.spawnParticle(Particle.SWEEP_ATTACK, o.getX() + look.getX() * 1.2, o.getY(), o.getZ() + look.getZ() * 1.2, 1, 0, 0, 0, 0);
		for (int i = 0; i < 16; i += 2) {
			Vector at = o.clone().add(look.clone().multiply(0.6 * i));
			sl.spawnParticle(Particle.END_ROD, at.getX(), at.getY() - i * 0.05, at.getZ(), 1, 0.05, 0.05, 0.05, 0.01);
		}
		Location pl = p.getLocation();
		BU.sound(pl, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.6F, 0.7F);
		BU.sound(pl, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.8F);
		if (tier >= 1) BU.sound(pl, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 0.6F, 1.6F);
	}

	/** 돌진 중 번개 잔상 */
	public static void stormTrail(World sl, LivingEntity p, int ticks) {
		for (int i = 0; i < ticks; i++) {
			Fx.later(i, () -> {
				if (p.isDead() || !p.isValid()) return;
				Location l = p.getLocation();
				sl.spawnParticle(Particle.ELECTRIC_SPARK, l.getX(), l.getY() + 0.9, l.getZ(), 6, 0.35, 0.5, 0.35, 0.05);
				sl.spawnParticle(Particle.DUST, l.getX(), l.getY() + 0.9, l.getZ(), 4, 0.25, 0.4, 0.25, 0, BU.dust(0x9FEFFF, 1.6F));
				sl.spawnParticle(Particle.CLOUD, l.getX(), l.getY() + 0.3, l.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
			});
		}
	}

	/** 낙뢰 착지 충격 */
	public static void stormImpact(World sl, Entity owner, Vector c, int tier) {
		Location cl = loc(sl, c);
		BU.sound(cl, Sound.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 1.2F, 1.1F);
		BU.sound(cl, Sound.ENTITY_BREEZE_WIND_BURST, SoundCategory.PLAYERS, 1.0F, 0.8F);
		sl.spawnParticle(Particle.GUST_EMITTER_SMALL, c.getX(), c.getY() + 0.3, c.getZ(), 1, 0, 0, 0, 0);
		for (int i = 0; i < 16; i++) {
			double ang = Math.PI * 2 * i / 16;
			sl.spawnParticle(Particle.ELECTRIC_SPARK, c.getX() + Math.cos(ang) * 3.2, c.getY() + 0.2, c.getZ() + Math.sin(ang) * 3.2, 3, 0.1, 0.2, 0.1, 0.1);
		}
		Fx.spawn(sl, owner.getUniqueId(), c, 10, (fx, lv) -> {
			if (fx.age == 1) {
				BlockDisplay d = fx.addBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), true);
				BlockDisplay d2 = fx.addBlock(Material.WHITE_STAINED_GLASS.createBlockData(), true);
				d.setGlowing(true); d.setGlowColorOverride(Color.fromRGB(tier >= 2 ? 0xFFF080 : 0x80E8FF));
				d2.setGlowing(true); d2.setGlowColorOverride(Color.fromRGB(0xFFFFFF));
			}
			float k = fx.age < 3 ? 1 : Math.max(0, 1 - (fx.age - 3) / 6F);
			for (int i = 0; i < fx.displays.size(); i++) {
				float wdt = (i == 0 ? 0.55F : 0.22F) * k;
				Quaternionf q = new Quaternionf().rotationY(fx.age * 0.6F);
				Fx.shape(fx.displays.get(i), new Vector3f(-wdt / 2, 0, -wdt / 2).rotate(q), q, new Vector3f(wdt, 14, wdt), 1);
			}
		});
	}

	/** 크로노 낫: 바닥에 시계판이 펼쳐지고 바늘이 돈다 */
	/** 크로노 낫 베기: 전방 150° 호를 따라 휩쓸기 + 금색/청록 먼지 (점 ~45개) */
	public static void scytheArc(World sl, org.bukkit.entity.Player p, Vector fw, double r, int tier) {
		Location pl = p.getLocation();
		BU.sound(pl, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.4F, 0.6F);
		BU.sound(pl, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 0.8F, 1.8F);
		Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(tier >= 2 ? 0xFFE070 : 0xE0B848), 1.3F);
		Particle.DustOptions teal = new Particle.DustOptions(Color.fromRGB(0x40E0C8), 1.0F);
		double base = Math.atan2(-fw.getX(), fw.getZ());
		for (int k = 0; k <= 10; k++) {
			double a = base + Math.toRadians(-75 + 15 * k);
			double x = -Math.sin(a), z = Math.cos(a);
			double y = pl.getY() + 1.1 + 0.25 * Math.sin(k / 10.0 * Math.PI);
			sl.spawnParticle(Particle.DUST, pl.getX() + x * r * 0.85, y, pl.getZ() + z * r * 0.85, 2, 0.1, 0.05, 0.1, 0, gold);
			sl.spawnParticle(Particle.DUST, pl.getX() + x * r * 0.55, y - 0.1, pl.getZ() + z * r * 0.55, 1, 0.1, 0.05, 0.1, 0, teal);
			if (k % 3 == 1) sl.spawnParticle(Particle.SWEEP_ATTACK, pl.getX() + x * r * 0.7, y, pl.getZ() + z * r * 0.7, 1, 0, 0, 0, 0);
		}
	}

	public static void clockField(World sl, Entity owner, Vector c, int tier, float radius) {
		Location cl = loc(sl, c);
		BU.sound(cl, Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.2F, 0.7F);
		BU.sound(cl, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.4F, 1.5F);
		final float R = Math.max(3.6F, radius);   // 법진 = 시간 정지 범위
		final float k = R / 3.6F;
		Fx.spawn(sl, owner.getUniqueId(), c.clone().add(new Vector(0, 0.05, 0)), 44, (fx, lv) -> {
			if (fx.age == 1) {
				for (int i = 0; i < 12; i++) fx.addBlock((i % 3 == 0 ? Material.GOLD_BLOCK : Material.WAXED_CUT_COPPER).createBlockData(), true);
				fx.addBlock(Material.BLACK_CONCRETE.createBlockData(), true);
				fx.addBlock(Material.RED_CONCRETE.createBlockData(), true);
				for (Display d : fx.displays) { d.setGlowing(true); d.setGlowColorOverride(Color.fromRGB(tier >= 2 ? 0xFFE070 : 0x60F0D8)); }
			}
			if (fx.age > 1 && fx.age % 2 == 1) return;
			if (fx.displays.size() < 14) return;
			float open = Math.min(1, fx.age / 6F) * (fx.age > 36 ? Math.max(0, 1 - (fx.age - 36) / 8F) : 1);
			for (int i = 0; i < 12; i++) {
				float ang = (float) (Math.PI * 2 * i / 12) + fx.age * 0.02F;
				Quaternionf q = new Quaternionf().rotationY(-ang);
				float sz = (i % 3 == 0 ? 0.5F : 0.3F) * (float) Math.sqrt(k) * open;
				Vector3f t = new Vector3f(-sz / 2, 0, R * open - sz / 2).rotate(q);
				Fx.shape(fx.displays.get(i), t, q, new Vector3f(sz, 0.08F, sz), 2);
			}
			float hour = fx.age * 0.15F, minute = fx.age * 0.6F;
			Quaternionf qh = new Quaternionf().rotationY(-hour), qm = new Quaternionf().rotationY(-minute);
			Fx.shape(fx.displays.get(12), new Vector3f(-0.08F * (float) Math.sqrt(k), 0.02F, 0).rotate(qh), qh, new Vector3f(0.16F * (float) Math.sqrt(k), 0.06F, 2.0F * k * open), 2);
			Fx.shape(fx.displays.get(13), new Vector3f(-0.06F * (float) Math.sqrt(k), 0.04F, 0).rotate(qm), qm, new Vector3f(0.12F * (float) Math.sqrt(k), 0.06F, 3.0F * k * open), 2);
			if (fx.age % 10 == 0) BU.sound(cl, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.9F, 1.4F);
			if (fx.age % 4 == 0) {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();
				for (int i = 0; i < 4; i++) {
					double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * R;
					lv.spawnParticle(Particle.REVERSE_PORTAL, c.getX() + Math.cos(a) * r, c.getY() + 0.3, c.getZ() + Math.sin(a) * r, 1, 0, 0.1, 0, 0.02);
				}
			}
		});
	}
}
