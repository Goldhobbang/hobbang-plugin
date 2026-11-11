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

	/** 망치 지진: 땅이 들썩이는 원형 충격파 3겹 + 혼불 고리 */
	public static void earthquake(World sl, Entity owner, Vector c, int tier) {
		BlockData gs = ground(sl, c);
		Block lb = sl.getBlockAt((int) Math.floor(c.getX()), (int) Math.floor(c.getY() + 0.5), (int) Math.floor(c.getZ()));
		Display.Brightness gl = new Display.Brightness(lb.getLightFromBlocks(), lb.getLightFromSky());
		Location cl = loc(sl, c);
		BU.sound(cl, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.4F, 0.6F);
		BU.sound(cl, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.9F, 0.5F);
		BU.sound(cl, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.6F, 0.7F);
		sl.spawnParticle(Particle.EXPLOSION, c.getX(), c.getY() + 0.3, c.getZ(), 3, 0.6, 0.1, 0.6, 0);
		float[] radii = {1.6F, 3.2F, 4.8F};
		for (int r = 0; r < radii.length; r++) {
			final float rad = radii[r];
			final int n = 6 + r * 4;
			Fx.later(1 + r * 3, () -> {
				BU.sound(cl, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.35F, 0.6F + rad * 0.1F);
				for (int i = 0; i < n; i++) {
					double ang = Math.PI * 2 * i / n;
					Vector at = new Vector(c.getX() + Math.cos(ang) * rad, c.getY(), c.getZ() + Math.sin(ang) * rad);
					sl.spawnParticle(Particle.BLOCK, at.getX(), at.getY() + 0.1, at.getZ(), 4, 0.2, 0.05, 0.2, 0.15, gs);
					sl.spawnParticle(Particle.SOUL_FIRE_FLAME, at.getX(), at.getY() + 0.2, at.getZ(), 2, 0.1, 0.2, 0.1, 0.03);
					if (tier >= 1) sl.spawnParticle(Particle.LAVA, at.getX(), at.getY() + 0.2, at.getZ(), 1, 0, 0, 0, 0);
					final float yaw = (float) -ang;
					Fx.spawn(sl, owner.getUniqueId(), at.clone().add(new Vector(0, -0.15, 0)), 14, (fx, lv) -> {
						if (fx.age == 1) fx.addBlock(gs, false).setBrightness(gl);
						else if (fx.age % 2 == 1) return;
						float h = fx.age <= 4 ? fx.age / 4F : Math.max(0, 1 - (fx.age - 4) / 8F);
						Quaternionf q = new Quaternionf().rotationY(yaw).rotateX((float) Math.toRadians(-18 * h));
						for (Display d : fx.displays)
							Fx.shape(d, new Vector3f(-0.45F, -0.35F, -0.45F).rotate(q).add(0, h * 0.55F, 0), q, new Vector3f(0.9F, 0.9F, 0.9F), 2);
					});
				}
			});
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

	public static void clockField(World sl, Entity owner, Vector c, int tier) {
		Location cl = loc(sl, c);
		BU.sound(cl, Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.2F, 0.7F);
		BU.sound(cl, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.4F, 1.5F);
		final float R = 3.6F;
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
				float sz = (i % 3 == 0 ? 0.5F : 0.3F) * open;
				Vector3f t = new Vector3f(-sz / 2, 0, R * open - sz / 2).rotate(q);
				Fx.shape(fx.displays.get(i), t, q, new Vector3f(sz, 0.08F, sz), 2);
			}
			float hour = fx.age * 0.15F, minute = fx.age * 0.6F;
			Quaternionf qh = new Quaternionf().rotationY(-hour), qm = new Quaternionf().rotationY(-minute);
			Fx.shape(fx.displays.get(12), new Vector3f(-0.08F, 0.02F, 0).rotate(qh), qh, new Vector3f(0.16F, 0.06F, 2.0F * open), 2);
			Fx.shape(fx.displays.get(13), new Vector3f(-0.06F, 0.04F, 0).rotate(qm), qm, new Vector3f(0.12F, 0.06F, 3.0F * open), 2);
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
