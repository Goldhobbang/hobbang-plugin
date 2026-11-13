package com.hobbang.enhance.boss;

import com.hobbang.enhance.HobbangPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * 임시 연출용 디스플레이(가시, 레이저, 회오리, 구체 등).
 * - 디스플레이는 전부 setPersistent(false): 청크 저장/서버 종료 시 디스크에 남지 않으므로 고아가 생길 수 없다
 * - 수명이 끝나거나 주인 보스가 사라지면 즉시 삭제
 * - 같은 모양이면 패킷을 보내지 않음 (InterpolationDelay 는 강제 dirty)
 */
public final class Fx {
	public static final String FX_TAG = "hobbang_fx";
	public static final String PART_TAG = "hobbang_part";

	public interface FxTick {
		void tick(Fx fx, World world);
	}

	public final World world;
	public final List<Display> displays = new ArrayList<>();
	public final UUID owner;
	public int age = 0;
	public int life;
	public String group;
	public Vector pos;
	public Vector vel = new Vector();
	public final FxTick tick;
	public boolean dead = false;
	private Boss visualOwner;

	private Fx(World world, UUID owner, Vector pos, int life, FxTick tick) {
		this.world = world; this.owner = owner; this.pos = pos; this.life = life; this.tick = tick;
	}

	public BlockDisplay addBlock(BlockData data, boolean bright) {
		BlockDisplay d = spawnBlockDisplay(new Location(world, pos.getX(), pos.getY(), pos.getZ()), data, bright, FX_TAG, 2);
		displays.add(d);
		LIVE++;
		return d;
	}

	/** 아이템 디스플레이 (무기 스킬 연출용: 삼지창 등). 다른 디스플레이와 똑같이 Fx 수명이 끝나면 지워진다 */
	public org.bukkit.entity.ItemDisplay addItem(org.bukkit.inventory.ItemStack stack, org.bukkit.entity.ItemDisplay.ItemDisplayTransform mode, boolean bright) {
		org.bukkit.entity.ItemDisplay d = world.spawn(new Location(world, pos.getX(), pos.getY(), pos.getZ()), org.bukkit.entity.ItemDisplay.class, e -> {
			e.setPersistent(false);
			e.setItemStack(stack);
			e.setItemDisplayTransform(mode);
			e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf()));
			e.setInterpolationDuration(2);
			e.setTeleportDuration(2);
			if (bright) e.setBrightness(new Display.Brightness(15, 15));
			e.addScoreboardTag(FX_TAG);
		});
		displays.add(d);
		LIVE++;
		return d;
	}

	public static BlockDisplay spawnBlockDisplay(Location at, BlockData data, boolean bright, String tag, int interp) {
		return at.getWorld().spawn(at, BlockDisplay.class, d -> {
			d.setPersistent(false);
			d.setBlock(data);
			d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf()));
			d.setInterpolationDuration(interp);
			d.setTeleportDuration(2);
			if (bright) d.setBrightness(new Display.Brightness(15, 15));
			d.addScoreboardTag(tag);
		});
	}

	/** 디스플레이 모양 설정 (바뀐 게 없으면 아무것도 보내지 않음) */
	public static void shape(Display d, Vector3f translation, Quaternionf rot, Vector3f scale, int interp) {
		Transformation t = new Transformation(new Vector3f(translation), new Quaternionf(rot), new Vector3f(scale), new Quaternionf());
		Transformation prev = LAST.put(d.getEntityId(), t);
		if (t.equals(prev)) return;
		if (d.getInterpolationDuration() != interp) d.setInterpolationDuration(interp);
		d.setTransformation(t);
		d.setInterpolationDelay(0);
	}

	public static void forget(Entity d) {
		LAST.remove(d.getEntityId());
	}

	public void kill() {
		dead = true;
	}

	// ───────── 관리 ─────────
	private static final List<Fx> ACTIVE = new ArrayList<>();
	private static final Map<Integer, Transformation> LAST = new HashMap<>();
	public static int LIVE = 0;

	public static Fx spawn(World world, UUID owner, Vector pos, int life, FxTick tick) {
		Fx fx = new Fx(world, owner, pos.clone(), life, tick);
		ACTIVE.add(fx);
		return fx;
	}

	static Fx visual(Boss boss, Vector pos, int life, FxTick tick) {
		Fx fx = spawn(boss.mob.getWorld(), boss.uuid, pos, life, tick);
		fx.visualOwner = boss;
		return fx;
	}

	private void moveTo(Vector at) {
		pos = at.clone();
		Location l = new Location(world, pos.getX(), pos.getY(), pos.getZ());
		for (Display d : displays) if (d.isValid()) d.teleport(l);
	}

	public static void later(int ticks, Runnable r) {
		Bukkit.getScheduler().runTaskLater(HobbangPlugin.get(), () -> {
			try { r.run(); } catch (Exception e) { HobbangPlugin.get().getLogger().warning("fx task: " + e); }
		}, Math.max(1, ticks));
	}


	public static final String BEAM_GROUP = "beam";
	/** Ticks a beam takes to collapse back into its origin. */
	public static final int BEAM_RETRACT = 5;

	public Fx group(String group) {
		this.group = group;
		return this;
	}

	/** Collapse a beam toward its origin over BEAM_RETRACT ticks, then remove it. Controllers without a visual die immediately. */
	void retract() {
		if (this.visualOwner == null) {
			this.kill();
		} else if (this.life - this.age > BEAM_RETRACT) {
			this.life = this.age + BEAM_RETRACT;
		}
	}

	/** Retract only this owner's beam effects. Marks, slams, orbs and clocks are left alone. */
	public static void discardBeams(UUID owner) {
		for (Fx fx : ACTIVE) {
			if (!fx.dead && owner.equals(fx.owner) && BEAM_GROUP.equals(fx.group)) {
				fx.retract();
			}
		}
	}

	public static void discardOwned(UUID owner) {
		discardOwned(owner, false);
	}

	static void discardOwned(UUID owner, boolean visualOnly) {
		for (Fx fx : ACTIVE) {
			if (owner.equals(fx.owner) && (!visualOnly || fx.visualOwner != null)) discardOwned(owner, fx);
		}
	}

	private static void discardOwned(UUID owner, Fx effect) {
		if (Objects.equals(owner, effect.owner)) effect.kill();
	}

	public static void tickAll() {
		if (ACTIVE.isEmpty()) return;
		for (int i = 0; i < ACTIVE.size(); i++) {
			Fx fx = ACTIVE.get(i);
			Boss boss = fx.visualOwner;
			if (boss != null && (boss.dead || !boss.mob.isValid() || boss.mob.getWorld() != fx.world
				|| (boss instanceof DisplayBoss display && display.dying))) discardOwned(fx.owner, fx);
			if (!fx.dead) {
				fx.age++;
				if (fx.vel.lengthSquared() > 0) {
					fx.pos.add(fx.vel);
					Location l = new Location(fx.world, fx.pos.getX(), fx.pos.getY(), fx.pos.getZ());
					for (Display d : fx.displays) if (d.isValid()) d.teleport(l);
				}
				try {
					if (fx.tick != null) fx.tick.tick(fx, fx.world);
				} catch (Exception e) {
					fx.dead = true;
					HobbangPlugin.get().getLogger().warning("fx error: " + e);
				}
				if (fx.age >= fx.life) discardOwned(fx.owner, fx);
			}
			if (fx.dead) {
				for (Display d : fx.displays) { forget(d); d.remove(); LIVE--; }
				fx.displays.clear();
				ACTIVE.remove(i--);
			}
		}
	}


	private enum Theme {
		SMITH(0xFF7B32, 0x32D8BC, Particle.SMALL_FLAME, Particle.ASH, Material.IRON_BLOCK),
		PRISM(0xDC81FF, 0x80E9FF, Particle.END_ROD, Particle.GLOW, Material.AMETHYST_BLOCK),
		CLOCK(0xFFD166, 0x5FE4D6, Particle.ELECTRIC_SPARK, Particle.SMOKE, Material.CUT_COPPER),
		WIND(0xC5F6FF, 0x539CFF, Particle.CLOUD, Particle.GUST, Material.PACKED_ICE);

		final Particle accent, trail;
		final BlockData debris;
		final Particle.DustTransition dust;

		Theme(int from, int to, Particle accent, Particle trail, Material debris) {
			this.accent = accent;
			this.trail = trail;
			this.debris = debris.createBlockData();
			this.dust = BossPatterns.dt(from, to, 1.15F);
		}

		static Theme of(BossKind kind) {
			return switch (kind) {
				case ABYSSAL_SMITH -> SMITH;
				case PRISM_COLOSSUS -> PRISM;
				case CHRONO_SENTINEL -> CLOCK;
				case TEMPEST_WYVERN -> WIND;
			};
		}
	}

	static Particle ringWake(BossKind kind) {
		return Theme.of(kind).trail;
	}

	static void puff(World world, Particle particle, Vector at, int count, double spread, double speed) {
		world.spawnParticle(particle, at.getX(), at.getY(), at.getZ(), count, spread, spread * 0.6, spread, speed);
	}

	static void burst(World world, Vector at, BossKind kind, int count, double spread) {
		Theme theme = Theme.of(kind);
		puff(world, theme.accent, at, count, spread, 0.07);
		world.spawnParticle(Particle.BLOCK, at.getX(), at.getY(), at.getZ(), count / 2, spread, spread * 0.45, spread, 0.12, theme.debris);
	}

	static void ambient(DisplayBoss boss) {
		Location at = boss.mob.getLocation();
		World w = at.getWorld();
		Vector body = at.toVector().add(new Vector(0, boss.mob.getHeight() * 0.58, 0));
		Theme theme = Theme.of(boss.kind);
		double spread = Math.min(1.4, 0.3 + boss.mob.getWidth() * 0.37);
		puff(w, theme.accent, body, 2, spread, 0.015);
		puff(w, theme.trail, body, 1, spread, 0.015);
		if (boss.idle.ticks() % 16 == 0) {
			if (theme == Theme.SMITH) puff(w, Particle.SOUL_FIRE_FLAME, body, 1, spread, 0.02);
			w.spawnParticle(Particle.BLOCK, body.getX(), body.getY(), body.getZ(), 1, spread, 0.5, spread, 0.025, theme.debris);
		}
	}

	/** Segments inside the existing warning boundary converge on the impact. */
	static void groundMark(World w, Vector c, double radius, double progress, BossKind kind) {
		Theme theme = Theme.of(kind);
		double r = radius * (1 - 0.74 * progress);
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			double x = Math.cos(a), z = Math.sin(a);
			w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.getX() + x * r, c.getY() + 0.14, c.getZ() + z * r, 1, 0, 0, 0, 0, theme.dust);
			w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c.getX() + x * (r - 0.2), c.getY() + 0.14, c.getZ() + z * (r - 0.2),
				1, 0, 0, 0, 0, theme.dust);
		}
	}

	static void ripple(Boss boss, Vector center, double radius, int ticks) {
		Theme theme = Theme.of(boss.kind);
		visual(boss, center, ticks, (fx, w) -> {
			if (fx.age % 2 == 0) BossPatterns.shockRing(w, fx.pos, radius * fx.age / ticks, theme.dust, theme.trail);
		});
	}

	private static final int HEAVE_START = 2;
	/** Ticks for the heave wave to travel from the impact to the edge of the radius. */
	private static final int HEAVE_WAVE = 9;
	/** Per-tile keyframes: {tick offset, lift fraction, tilt fraction, interpolation ticks}. */
	private static final float[][] HEAVE_KEYS = {
		{0, 0.82F, 0.70F, 2}, // fast kick up (ease-out: 82% in 2 ticks...)
		{2, 1.00F, 1.00F, 2}, // ...last 18% in 2 ticks
		{4, 1.04F, 1.00F, 2}, // brief hang, slight drift
		{6, 0.80F, 0.60F, 2}, // fall starts slow (ease-in)...
		{8, 0.00F, 0.00F, 2}, // ...then slams back flat
		{10, 0.07F, 0.00F, 2}, // small settle bounce
		{12, 0.00F, 0.00F, 2}  // exact rest pose
	};
	private static final int HEAVE_TILE_TICKS = 14;
	private static final int HEAVE_LIFE = HEAVE_START + HEAVE_WAVE + HEAVE_TILE_TICKS + 1; // 26
	private static final int HEAVE_MAX_TILES = 70;
	private static final float HEAVE_LIFT_CENTER = 1.7F;
	private static final float HEAVE_LIFT_EDGE = 0.72F;
	private static final Vector3f TILE_PIVOT = new Vector3f(0.5F, 0.5F, 0.5F);

	private record HeaveTile(BlockDisplay display, Vector3f base, int delay, float lift, Quaternionf tilt, Vector landing, BlockData data) {
	}

	static void slam(Boss boss, Vector impact, double radius) {
		World w = boss.mob.getWorld();
		Vector center = impact.clone();
		center.setY(BU.groundY(w, center.getX(), center.getY() + 1.0, center.getZ()));
		int rippleTicks = HEAVE_START + HEAVE_WAVE + 3;
		if (!BU.solidAt(w, center.clone().add(new Vector(0.0, -0.01, 0.0)))) {
			ripple(boss, impact, radius, rippleTicks);
		} else {
			ripple(boss, center, radius, rippleTicks);
			List<HeaveTile> tiles = new ArrayList<>();
			visual(boss, center, HEAVE_LIFE, (fx, world) -> {
				if (!world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
					fx.kill();
					return;
				}

				if (fx.age == 1) {
					burst(world, center.clone().add(new Vector(0.0, 0.4, 0.0)), boss.kind, 26, radius * 0.25);
					tiles.addAll(slamTiles(fx, radius));
					return;
				}

				for (HeaveTile tile : tiles) {
					int local = fx.age - HEAVE_START - tile.delay();
					if (local < 0 || local >= HEAVE_TILE_TICKS || !tile.display().isValid()) {
						continue;
					}

					for (float[] key : HEAVE_KEYS) {
						if ((int)key[0] == local) {
							heavePose(tile, key[1], key[2], (int)key[3]);
							if (local == 8) {
								Vector l = tile.landing();
								world.spawnParticle(Particle.BLOCK, l.getX(), l.getY(), l.getZ(), 3, 0.3, 0.05, 0.3, 0.08, tile.data());
							}
							break;
						}
					}
				}
			});
		}
	}

	private static void heavePose(HeaveTile tile, float liftFrac, float tiltFrac, int interp) {
		Quaternionf rot = new Quaternionf().slerp(tile.tilt(), tiltFrac);
		// rotate around the tile's own centre, not the display origin corner
		Vector3f pivotFix = new Vector3f(TILE_PIVOT).sub(rot.transform(new Vector3f(TILE_PIVOT)));
		Vector3f t = new Vector3f(tile.base()).add(0.0F, tile.lift() * liftFrac, 0.0F).add(pivotFix);
		if (liftFrac == 0.0F && tiltFrac == 0.0F) {
			t.set(tile.base());
			rot.identity();
		}

		shape(tile.display(), t, rot, new Vector3f(1.0F), interp);
	}

	private static List<HeaveTile> slamTiles(Fx uplift, double radius) {
		World w = uplift.world;
		Vector center = uplift.pos;
		List<HeaveTile> tiles = new ArrayList<>();
		java.util.Set<Long> used = new java.util.HashSet<>();
		java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
		double r0 = Math.max(1.0, radius);
		// spacing along each ring so the whole disc lands near the cap
		int rings = (int)Math.floor(r0);
		double circumference = 0.0;
		for (int k = 1; k <= rings; k++) {
			circumference += Math.PI * 2.0 * Math.min(k, r0 - 0.15);
		}

		double spacing = Math.max(1.05, circumference / (HEAVE_MAX_TILES - 6));

		for (int k = 0; k <= rings && tiles.size() < HEAVE_MAX_TILES; k++) {
			double r = k == 0 ? 0.0 : Math.min(k, r0 - 0.15);
			int count = k == 0 ? 1 : Math.max(4, (int)Math.round(Math.PI * 2.0 * r / spacing));
			double phase = k * 2.39996; // golden angle: no straight spokes

			for (int i = 0; i < count && tiles.size() < HEAVE_MAX_TILES; i++) {
				double a = phase + i * Math.PI * 2.0 / count;
				int x = (int)Math.floor(center.getX() + Math.cos(a) * r);
				int z = (int)Math.floor(center.getZ() + Math.sin(a) * r);
				if (!used.add(((long)x << 32) ^ (z & 0xFFFFFFFFL)) || !w.isChunkLoaded(x >> 4, z >> 4)) {
					continue;
				}

				int y = (int)BU.groundY(w, x, center.getY() + 2.0, z) - 1;
				if (y < w.getMinHeight() || y + 1 >= w.getMaxHeight() || Math.abs(y + 1 - center.getY()) > 2.0) {
					continue;
				}

				Block block = w.getBlockAt(x, y, z);
				if (!block.getType().isSolid() || w.getBlockAt(x, y + 1, z).isCollidable()) {
					continue;
				}

				double dx = x + 0.5 - center.getX();
				double dz = z + 0.5 - center.getZ();
				double dist = Math.sqrt(dx * dx + dz * dz);
				double f = Math.min(1.0, dist / r0);
				float lift = (float)(HEAVE_LIFT_CENTER + (HEAVE_LIFT_EDGE - HEAVE_LIFT_CENTER) * f + rnd.nextDouble(-0.12, 0.12));
				lift = Math.max(0.55F, Math.min(1.85F, lift));
				int delay = (int)Math.round(f * HEAVE_WAVE);
				// tilt the top away from the impact (radial heave) with a little jitter
				float ux = dist < 0.3 ? (float)Math.cos(a) : (float)(dx / dist);
				float uz = dist < 0.3 ? (float)Math.sin(a) : (float)(dz / dist);
				float angle = (float)Math.toRadians(7.0 + 7.0 * f + rnd.nextDouble(-3.0, 3.0));
				Quaternionf tilt = new Quaternionf()
					.rotateAxis(angle, uz, 0.0F, -ux)
					.rotateY((float)Math.toRadians(rnd.nextDouble(-6.0, 6.0)));
				BlockData data = block.getBlockData();
				BlockDisplay display = uplift.addBlock(data, false);
				Vector3f base = new Vector3f((float)(x - center.getX()), (float)(y - center.getY()) - 0.035F, (float)(z - center.getZ()));
				shape(display, base, new Quaternionf(), new Vector3f(1.0F), 0);
				tiles.add(new HeaveTile(display, base, delay, lift, tilt, new Vector(x + 0.5, y + 1.05, z + 0.5), data));
			}
		}

		return tiles;
	}

	static void awaken(DisplayBoss boss, BossMotion.Move move, int hit, int live, int recovery) {
		boss.cue(move, hit, live, recovery);
		Theme theme = Theme.of(boss.kind);
		visual(boss, boss.mob.getLocation().toVector(), hit + 13, (fx, w) -> {
			int elapsed = fx.age - 1;
			Vector c = boss.mob.getLocation().toVector();
			double body = Math.max(1.4, boss.mob.getHeight());
			double width = Math.max(1.3, boss.mob.getWidth());
			if (elapsed < hit) {
				if (elapsed % 2 != 0) return;
				double u = elapsed / (double) hit;
				for (int i = 0; i < 10; i++) {
					double a = fx.age * 0.42 + i * Math.PI / 5;
					double r = width * (1.8 - u);
					Vector mote = c.clone().add(new Vector(Math.cos(a) * r, body * (0.15 + 0.6 * u) + (i % 3) * 0.24, Math.sin(a) * r));
					puff(w, theme.accent, mote, 1, 0, 0);
				}
			} else {
				Vector release = c.add(new Vector(0, body * 0.52, 0));
				if (elapsed == hit) {
					burst(w, release, boss.kind, 26, width * 0.7);
					w.spawnParticle(Particle.FLASH, release.getX(), release.getY(), release.getZ(), 1, 0, 0, 0, 0, theme.dust.getColor());
				}
				int after = elapsed - hit;
				if (after % 3 == 0) {
					double r = width * 0.6 + after * 0.43;
					BossPatterns.shockRing(w, release, r, theme.dust, theme.trail);
				}
			}
		});
	}

	static void projectileTrail(Boss boss, Projectile projectile, int life) {
		Theme theme = Theme.of(boss.kind);
		visual(boss, projectile.getLocation().toVector(), life, (fx, w) -> {
			if (!projectile.isValid() || projectile.getWorld() != w) {
				if (w.isChunkLoaded(fx.pos.getBlockX() >> 4, fx.pos.getBlockZ() >> 4)) puff(w, theme.accent, fx.pos, 6, 0.26, 0.05);
				fx.kill();
				return;
			}
			fx.pos = projectile.getLocation().toVector();
			if (fx.age % 2 == 0) puff(w, theme.trail, fx.pos, 2, 0.18, 0.018);
		});
	}

	static void windWake(World w, Vector at, float yaw, double span, boolean climb) {
		double a = Math.toRadians(yaw);
		for (int side = -1; side <= 1; side += 2) {
			Vector tip = at.clone().add(new Vector(Math.cos(a) * span * side + Math.sin(a), climb ? -0.4 : 0.55,
				Math.sin(a) * span * side - Math.cos(a)));
			puff(w, Particle.CLOUD, tip, climb ? 2 : 3, 0.22, 0.01);
		}
	}

	static void beamCharge(World w, Vector origin, Vector direction, int age, BossKind kind) {
		if (!(direction.lengthSquared() > 1e-6)) return;
		Theme theme = Theme.of(kind);
		Quaternionf axis = beamAxis(direction);
		for (int i = 0; i < 4; i++) {
			double a = age * 0.42 + i * Math.PI / 2;
			Vector3f p = axis.transform(new Vector3f((float) Math.cos(a) * 0.63F, (float) Math.sin(a) * 0.63F, 0.55F));
			puff(w, theme.accent, origin.clone().add(new Vector(p.x, p.y, p.z)), 1, 0, 0);
		}
	}

	private static Quaternionf beamAxis(Vector dir) {
		return new Quaternionf().rotationTo(0, 0, 1, (float) dir.getX(), (float) dir.getY(), (float) dir.getZ());
	}

	static void beam(Boss boss, Supplier<Vector> origin, Vector direction, float length, int life, Material shell, int rgb) {
		if (direction.lengthSquared() > 1.0E-6) {
			Vector fixed = direction.clone();
			beam(boss, origin, () -> fixed, length, life, shell, rgb);
		}
	}

	/**
	 * Beam whose direction is re-read every tick (axis recomputed each update), so it can track.
	 * The last {@link #BEAM_RETRACT} ticks of its life collapse it back into the origin; a call to
	 * {@link #discardBeams(UUID)} triggers the same retract early.
	 */
	static Fx beam(Boss boss, Supplier<Vector> origin, Supplier<Vector> directionSource, float length, int life, Material shell, int rgb) {
		Vector first = directionSource.get();
		if (first != null && first.lengthSquared() > 1.0E-6) {
			Fx.Theme theme = Fx.Theme.of(boss.kind);
			Vector start = origin.get();
			Vector[] lastDir = new Vector[]{first.clone().normalize()};
			if (boss.mob.getWorld().isChunkLoaded(start.getBlockX() >> 4, start.getBlockZ() >> 4)) {
				return visual(boss, start, life, (fx, w) -> {
					Vector o = origin.get();
					Vector nd = directionSource.get();
					if (nd != null && nd.lengthSquared() > 1.0E-6) {
						lastDir[0] = nd.clone().normalize();
					}

					Vector direction = lastDir[0];
					Quaternionf axis = beamAxis(direction);
					int left = fx.life - fx.age;
					boolean retracting = left < BEAM_RETRACT;
					if (!w.isChunkLoaded(o.getBlockX() >> 4, o.getBlockZ() >> 4)) {
						fx.kill();
					} else {
						if (fx.pos.distanceSquared(o) > 0.025) {
							fx.moveTo(o);
						}

						if (fx.age == 1) {
							fx.addBlock(shell.createBlockData(), true);
							fx.addBlock(Material.WHITE_STAINED_GLASS.createBlockData(), true);
							BlockDisplay core = fx.addBlock(Material.SEA_LANTERN.createBlockData(), true);
							core.setGlowing(true);
							core.setGlowColorOverride(Color.fromRGB(rgb));
						}

						if (retracting && left == BEAM_RETRACT - 1) {
							// "빠용": snap shut with a short zap and a puff at the mouth.
							Location at = new Location(w, o.getX(), o.getY(), o.getZ());
							w.playSound(at, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, org.bukkit.SoundCategory.HOSTILE, 1.6F, 1.9F);
							w.playSound(at, org.bukkit.Sound.ENTITY_BREEZE_WIND_BURST, org.bukkit.SoundCategory.HOSTILE, 0.9F, 1.7F);
							burst(w, o, boss.kind, 6, 0.3);
						}

						if (retracting || fx.age % 2 == 1) {
							// k: 1 while firing, then 4/5, 3/5 ... 0 over the retract (length collapses into the origin).
							float k = retracting ? Math.max(0.0F, (left - 1) / (float)(BEAM_RETRACT - 1)) : 1.0F;
							float thin = retracting ? 0.35F + 0.65F * k : 1.0F;
							int interp = retracting ? 1 : 2;

							for (int layer = 0; layer < fx.displays.size(); layer++) {
								float width = (layer == 0 ? 1.04F : (layer == 1 ? 0.66F : 0.36F)) * (1.0F + 0.18F * BossMotion.sin(fx.age * 1.16F + layer)) * thin * (k > 0.0F ? 1.0F : 0.0F);
								Quaternionf rot = new Quaternionf(axis).rotateZ(fx.age * (0.13F + 0.065F * layer));
								float jitter = layer == 2 || retracting ? 0.0F : 0.14F * BossMotion.sin(fx.age * 0.82F + layer * 2);
								Vector3f offset = new Vector3f(-width / 2.0F, -width / 2.0F, 0.0F).rotate(rot).add(new Vector3f(jitter, -jitter, 0.0F).rotate(axis));
								shape(fx.displays.get(layer), offset, rot, new Vector3f(width, width, length * k), interp);
							}
						}

						if (!retracting && fx.age % 3 == 1) {
							for (int i = 0; i < 4; i++) {
								double t = (i + 0.5 + 0.35 * Math.sin(fx.age + i)) / 4.0;
								burst(w, o.clone().add(direction.clone().multiply(length * t)), boss.kind, 2, 0.38);
							}
						}

						if (!retracting && (fx.age == 1 || fx.age % 12 == 0)) {
							Vector end = beamContact(w, o, direction, length);
							beamHit(boss, end);
							if (fx.age == 1) {
								w.spawnParticle(Particle.FLASH, end.getX(), end.getY(), end.getZ(), 1, 0.0, 0.0, 0.0, 0.0, theme.dust.getColor());
							}
						}
					}
				}).group(BEAM_GROUP);
			}
		}

		return null;
	}

	private static Vector beamContact(World w, Vector start, Vector dir, float length) {
		Vector point = start.clone();
		Vector step = dir.clone().multiply(0.65);
		for (double d = 0.65; d < length; d += 0.65) {
			point.add(step);
			if (!w.isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4) || BU.solidAt(w, point)) return point.subtract(step);
		}
		return start.clone().add(dir.clone().multiply(length));
	}

	static void beamHit(Boss boss, Vector at) {
		World w = boss.mob.getWorld();
		if (!w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
		burst(w, at, boss.kind, 9, 0.46);
		puff(w, Particle.END_ROD, at, 6, 0.34, 0.11);
		ripple(boss, at, 1.4, 6);
	}

	/** Upright billboard assembled from blocks, with independently animated hands. */
	static Fx clock(Boss boss, Supplier<Vector> anchor, DoubleSupplier angle, BooleanSupplier active) {
		return visual(boss, anchor.get(), Integer.MAX_VALUE, (fx, w) -> {
			if (!active.getAsBoolean()) { discardOwned(boss.uuid, fx); return; }
			if (fx.age % 3 == 1) {
				Vector at = anchor.get();
				if (!w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) { fx.kill(); return; }
				if (fx.pos.distanceSquared(at) > 0.01) fx.moveTo(at);
			}
			if (fx.age == 1) buildClock(fx);
			if (fx.age % 2 == 1) {
				float minute = (float) angle.getAsDouble();
				clockHand(fx.displays.getFirst(), minute, 0.99F, 0.085F, 0.06F);
				clockHand(fx.displays.get(1), minute / 4, 0.68F, 0.135F, 0.115F);
			}
		});
	}

	private static void buildClock(Fx dial) {
		clockBlock(dial, Material.SEA_LANTERN);
		clockBlock(dial, Material.GOLD_BLOCK);
		for (int row = -4; row <= 4; row++) {
			float y = row * 0.275F;
			float width = 2 * Math.min(1.24F, 1.75F - Math.abs(y) - 0.08F);
			clockPiece(dial, Material.POLISHED_DEEPSLATE, 0, y, -0.1F, width, 0.275F, 0.12F, 0);
		}
		for (int i = 0; i < 8; i++) {
			float a = (float) (i * Math.PI / 4);
			clockPiece(dial, Material.WAXED_CUT_COPPER, -BossMotion.sin(a) * 1.24F, (float) Math.cos(a) * 1.24F,
				-0.04F, 1.04F, 0.15F, 0.16F, a);
		}
		for (int i = 0; i < 12; i++) {
			float a = (float) (i * Math.PI / 6);
			boolean major = i % 3 == 0;
			clockPiece(dial, major ? Material.GOLD_BLOCK : Material.WAXED_OXIDIZED_CUT_COPPER,
				-BossMotion.sin(a) * 1.04F, (float) Math.cos(a) * 1.04F, 0.015F, 0.075F, major ? 0.25F : 0.14F, 0.065F, a);
		}
		clockPiece(dial, Material.GOLD_BLOCK, 0, 0, 0.14F, 0.22F, 0.22F, 0.085F, 0);
	}

	private static BlockDisplay clockBlock(Fx dial, Material material) {
		BlockDisplay block = dial.addBlock(material.createBlockData(), true);
		block.setBillboard(Display.Billboard.VERTICAL);
		return block;
	}

	private static void clockPiece(Fx dial, Material material, float x, float y, float z, float width, float height, float depth, float angle) {
		Quaternionf rot = new Quaternionf().rotateZ(angle);
		Vector3f offset = new Vector3f(-width / 2, -height / 2, 0).rotate(rot).add(x, y, z);
		shape(clockBlock(dial, material), offset, rot, new Vector3f(width, height, depth), 0);
	}

	private static void clockHand(Display hand, float angle, float length, float width, float depth) {
		Quaternionf rot = new Quaternionf().rotateZ(-angle);
		shape(hand, new Vector3f(-width / 2, -0.08F, depth).rotate(rot), rot, new Vector3f(width, length, 0.055F), 2);
	}

	static void sweepDial(Boss boss, Vector center, float radius, int warm, int life) {
		visual(boss, center, life, (fx, w) -> {
			if (fx.age == 1) {
				if (!w.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) { fx.kill(); return; }
				BlockDisplay hub = fx.addBlock(Material.WAXED_CUT_COPPER.createBlockData(), true);
				Quaternionf rot = new Quaternionf().rotateY((float) Math.PI / 4);
				shape(hub, new Vector3f(-0.46F, 0.06F, -0.46F).rotate(rot), rot, new Vector3f(0.92F, 0.11F, 0.92F), 0);
			}
			if (fx.age % 5 != 1 || fx.age > life - 4) return;
			Theme theme = Theme.CLOCK;
			for (int side = 0; side < 8; side++) {
				double a = Math.PI * side / 4;
				double b = a + Math.PI / 4;
				for (int i = 0; i < 5; i++) {
					double u = i / 5.0;
					double x = center.getX() + radius * (Math.sin(a) * (1 - u) + Math.sin(b) * u);
					double z = center.getZ() + radius * (Math.cos(a) * (1 - u) + Math.cos(b) * u);
					w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, center.getY() + 0.13, z, 1, 0, 0, 0, 0, theme.dust);
				}
			}
			for (int i = 0; i < 12; i++) {
				double a = Math.PI * i / 6;
				double r = radius * (i % 3 == 0 ? 0.83 : 0.87);
				Vector tick = center.clone().add(new Vector(Math.sin(a) * r, 0.16, Math.cos(a) * r));
				puff(w, fx.age <= warm ? Particle.WAX_ON : Particle.ELECTRIC_SPARK, tick, 1, 0, 0);
			}
			puff(w, Particle.GLOW, center.clone().add(new Vector(0, 0.28, 0)), 5, 0.26, 0.01);
		});
	}

	public static int active() {
		return ACTIVE.size();
	}

	public static void clearAll() {
		for (Fx fx : ACTIVE) for (Display d : fx.displays) d.remove();
		ACTIVE.clear();
		LAST.clear();
		LIVE = 0;
	}
}
