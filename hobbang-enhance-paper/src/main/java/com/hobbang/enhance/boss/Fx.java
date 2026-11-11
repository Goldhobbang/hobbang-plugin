package com.hobbang.enhance.boss;

import com.hobbang.enhance.HobbangPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
	public final int life;
	public Vector pos;
	public Vector vel = new Vector();
	public final FxTick tick;
	public boolean dead = false;

	private Fx(World world, UUID owner, Vector pos, int life, FxTick tick) {
		this.world = world; this.owner = owner; this.pos = pos; this.life = life; this.tick = tick;
	}

	public BlockDisplay addBlock(BlockData data, boolean bright) {
		BlockDisplay d = spawnBlockDisplay(new Location(world, pos.getX(), pos.getY(), pos.getZ()), data, bright, FX_TAG, 2);
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

	public static void later(int ticks, Runnable r) {
		Bukkit.getScheduler().runTaskLater(HobbangPlugin.get(), () -> {
			try { r.run(); } catch (Exception e) { HobbangPlugin.get().getLogger().warning("fx task: " + e); }
		}, Math.max(1, ticks));
	}

	public static void discardOwned(UUID owner) {
		for (Fx fx : ACTIVE) if (owner.equals(fx.owner)) fx.dead = true;
	}

	public static void tickAll() {
		if (ACTIVE.isEmpty()) return;
		for (int i = 0; i < ACTIVE.size(); i++) {
			Fx fx = ACTIVE.get(i);
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
				if (fx.age >= fx.life) fx.dead = true;
			}
			if (fx.dead) {
				for (Display d : fx.displays) { forget(d); d.remove(); LIVE--; }
				fx.displays.clear();
				Iterator<Fx> ignored = null;
				ACTIVE.remove(i--);
			}
		}
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
