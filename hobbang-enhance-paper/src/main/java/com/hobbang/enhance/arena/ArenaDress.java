package com.hobbang.enhance.arena;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.boss.Boss;
import com.hobbang.enhance.boss.BossKind;
import com.hobbang.enhance.boss.BossManager;
import com.hobbang.enhance.boss.BU;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * v1.4.3: 대장장이·센티넬 소환 시 아레나 바닥이 중심에서 바깥으로 물결치며 무늬가 바뀐다.
 * 블록 변경은 틱당 소수만 적용해 스파이크를 피하고, 원래 블록은 floor-dress.json 에 남겨
 * 사망/디스폰/리셋/플러그인 종료 때 복구한다 (크래시 후에도 복구 가능).
 */
public final class ArenaDress {
	private static final Gson GSON = new GsonBuilder().create();
	private static final int RADIUS = 12;
	private static final int APPLY_BATCH = 14;
	private static final int RESTORE_BATCH = 16;
	private static final List<Job> JOBS = new ArrayList<>();
	private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Hobbang-DressIO");
		t.setDaemon(true);
		return t;
	});
	private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();

	enum Theme { FORGE, TEMPLE, GROTTO }

	static final class Cell {
		int x, y, z;
		String data;
		/** true = 보스 제단 블록 (아레나가 펼쳐질 때 테마 블록으로 덮이고, 복구 때 제단 표식까지 되살린다) */
		boolean altar;
	}

	static final class Job {
		String boss;
		String world;
		int cx, cy, cz;
		Theme theme;
		boolean restoring;
		int cursor;
		List<Cell> cells = new ArrayList<>();
		transient int tick;
	}

	private static final Set<Material> PAVEMENT = EnumSet.of(
		Material.BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS,
		Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE,
		Material.GILDED_BLACKSTONE, Material.BLACKSTONE_SLAB, Material.POLISHED_BLACKSTONE_SLAB,
		Material.BASALT, Material.POLISHED_BASALT, Material.SMOOTH_BASALT,
		Material.NETHERRACK, Material.NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS, Material.CHISELED_NETHER_BRICKS,
		Material.RED_NETHER_BRICKS, Material.MAGMA_BLOCK, Material.SOUL_SAND, Material.SOUL_SOIL,
		Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM, Material.ANCIENT_DEBRIS,
		Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS,
		Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.SMOOTH_STONE,
		Material.ANDESITE, Material.POLISHED_ANDESITE, Material.GRANITE, Material.POLISHED_GRANITE,
		Material.DIORITE, Material.POLISHED_DIORITE, Material.TUFF, Material.POLISHED_TUFF, Material.TUFF_BRICKS,
		Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_BRICKS,
		Material.DEEPSLATE_TILES, Material.CHISELED_DEEPSLATE, Material.REINFORCED_DEEPSLATE,
		Material.CALCITE, Material.TERRACOTTA, Material.PACKED_MUD, Material.MUD_BRICKS,
		Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.RED_SANDSTONE,
		Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER, Material.WEATHERED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER,
		Material.WAXED_CUT_COPPER, Material.WAXED_EXPOSED_CUT_COPPER, Material.WAXED_WEATHERED_CUT_COPPER, Material.WAXED_OXIDIZED_CUT_COPPER,
		Material.COPPER_BLOCK, Material.EXPOSED_COPPER, Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER,
		Material.WAXED_COPPER_BLOCK, Material.CHISELED_COPPER, Material.EXPOSED_CHISELED_COPPER,
		Material.WEATHERED_CHISELED_COPPER, Material.OXIDIZED_CHISELED_COPPER,
		Material.COPPER_GRATE, Material.EXPOSED_COPPER_GRATE, Material.WEATHERED_COPPER_GRATE, Material.OXIDIZED_COPPER_GRATE,
		Material.COPPER_BULB, Material.EXPOSED_COPPER_BULB, Material.WEATHERED_COPPER_BULB, Material.OXIDIZED_COPPER_BULB,
		Material.GOLD_BLOCK, Material.RAW_GOLD_BLOCK, Material.LIGHTNING_ROD,
		Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST, Material.SMOOTH_QUARTZ, Material.QUARTZ_BLOCK, Material.QUARTZ_BRICKS,
		Material.PURPUR_BLOCK, Material.PURPUR_PILLAR, Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE,
		Material.BRICKS, Material.MUD, Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH, Material.COARSE_DIRT,
		Material.PODZOL, Material.MYCELIUM, Material.ROOTED_DIRT, Material.MOSS_BLOCK,
		Material.BLACK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE
	);

	private ArenaDress() {}

	public static void init(HobbangPlugin plugin) {
		load();
		Bukkit.getScheduler().runTaskTimer(plugin, ArenaDress::tick, 1, 1);
		// 플러그인 재시작 후 보스가 없으면 남은 무늬를 되돌린다
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			boolean any = false;
			for (Job j : JOBS) {
				if (living(j) == null) { j.restoring = true; any = true; }
			}
			if (any) save();
		}, 60L);
	}

	public static void shutdown() {
		for (Job j : JOBS) j.restoring = true;
		// 종료 전에 가능한 만큼 동기 복구
		int guard = 0;
		while (!JOBS.isEmpty() && guard++ < 2000) restoreBatch(true);
		saveSync();
		IO.shutdown();
	}

	/** 대장장이·센티넬 소환 시 호출 (명령어 소환도 포함, 스폰 주변 원판) */
	public static void start(Boss boss) {
		if (boss == null || boss.mob == null || !boss.mob.isValid()) return;
		BossKind k = boss.kind;
		if (k != BossKind.ABYSSAL_SMITH && k != BossKind.CHRONO_SENTINEL && k != BossKind.PRISM_COLOSSUS) return;
		Theme theme = k == BossKind.ABYSSAL_SMITH ? Theme.FORGE : k == BossKind.CHRONO_SENTINEL ? Theme.TEMPLE : Theme.GROTTO;
		Location at = boss.mob.getLocation();
		World w = at.getWorld();
		int cx, cz, cy;
		if (boss.arena != null && boss.arena.world() == w) {
			cx = boss.arena.x();
			cz = boss.arena.z();
			cy = floorY(w, cx, boss.arena.y(), cz);
		} else {
			cx = at.getBlockX();
			cz = at.getBlockZ();
			cy = floorY(w, cx, at.getBlockY(), cz);
		}
		// 제단 위에서 소환되므로 소환 위치 바로 아래(또는 아레나 기록)에서 제단 블록을 찾는다
		Block altar = findAltar(boss, w, cx, cz, at.getBlockY());
		if (altar != null && theme != Theme.GROTTO) cy = floorAround(w, altar);   // 바닥 무늬는 제단 옆 실제 바닥 높이에
		// 같은 자리에 남은 작업이 있으면 먼저 되돌린 뒤 새로 입힌다
		for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
			Job old = it.next();
			if (old.world.equals(w.getName()) && old.cx == cx && old.cz == cz && Math.abs(old.cy - cy) <= 2) {
				restoreJobNow(old);
				it.remove();
			}
		}
		Job job = new Job();
		job.boss = boss.mob.getUniqueId().toString();
		job.world = w.getName();
		job.cx = cx;
		job.cy = cy;
		job.cz = cz;
		job.theme = theme;
		job.cursor = 0;
		job.restoring = false;
		if (altar != null) {   // 0번 칸 = 제단: 펼쳐질 때 가장 먼저 테마 블록으로 바뀜
			Cell c = new Cell();
			c.x = altar.getX();
			c.y = altar.getY();
			c.z = altar.getZ();
			c.data = altar.getBlockData().getAsString();
			c.altar = true;
			job.cells.add(c);
		}
		List<int[]> order = new ArrayList<>();
		for (int dx = -RADIUS; dx <= RADIUS && theme != Theme.GROTTO; dx++) {   // 정동은 바닥 무늬 없이 제단만
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
				order.add(new int[]{dx, dz, dx * dx + dz * dz});
			}
		}
		order.sort((a, b) -> a[2] != b[2] ? Integer.compare(a[2], b[2]) : Integer.compare(a[0] * 31 + a[1], b[0] * 31 + b[1]));
		for (int[] o : order) {
			Block b = w.getBlockAt(cx + o[0], cy, cz + o[1]);
			if (!replaceable(b)) continue;
			Material want = pattern(theme, o[0], o[1], o[2]);
			if (b.getType() == want) continue;
			Cell c = new Cell();
			c.x = b.getX();
			c.y = b.getY();
			c.z = b.getZ();
			c.data = b.getBlockData().getAsString();
			job.cells.add(c);
		}
		if (job.cells.isEmpty()) return;
		JOBS.add(job);
		save();
	}

	public static void restore(Boss boss) {
		if (boss == null || boss.mob == null) return;
		String id = boss.mob.getUniqueId().toString();
		boolean any = false;
		for (Job j : JOBS) {
			if (id.equals(j.boss) && !j.restoring) { j.restoring = true; any = true; }
		}
		if (any) save();
	}

	private static void tick() {
		if (JOBS.isEmpty()) return;
		boolean dirty = false;
		for (int i = 0; i < JOBS.size(); i++) {
			Job j = JOBS.get(i);
			if (j.restoring) continue;
			if (applyBatch(j)) dirty = true;
		}
		if (restoreBatch(false)) dirty = true;
		if (dirty) save();
	}

	private static boolean applyBatch(Job j) {
		World w = Bukkit.getWorld(j.world);
		if (w == null) return false;
		int n = 0;
		while (j.cursor < j.cells.size() && n < APPLY_BATCH) {
			Cell c = j.cells.get(j.cursor++);
			Block b = w.getBlockAt(c.x, c.y, c.z);
			if (c.altar) {   // 보스 제단: 사라지고 아레나 테마 블록으로 덮임
				b.setType(altarFloor(j.theme), false);
				fx(w, c, j.theme, false);
				BU.sound(new Location(w, c.x + 0.5, c.y + 0.5, c.z + 0.5), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 1.2F, 0.7F);
				n++;
				continue;
			}
			if (!replaceable(b) && b.getType() != Material.AIR) { n++; continue; }
			int dx = c.x - j.cx, dz = c.z - j.cz;
			Material want = pattern(j.theme, dx, dz, dx * dx + dz * dz);
			b.setType(want, false);
			fx(w, c, j.theme, false);
			n++;
		}
		j.tick++;
		if (j.tick % 4 == 0 && j.cursor > 0 && j.cursor <= j.cells.size()) {
			Cell last = j.cells.get(Math.min(j.cursor, j.cells.size()) - 1);
			Location at = new Location(w, last.x + 0.5, last.y + 1, last.z + 0.5);
			if (j.theme == Theme.FORGE) BU.sound(at, Sound.BLOCK_LAVA_POP, SoundCategory.AMBIENT, 0.7F, 0.7F);
			else BU.sound(at, Sound.BLOCK_COPPER_PLACE, SoundCategory.AMBIENT, 0.6F, 1.1F);
		}
		return n > 0;
	}

	private static boolean restoreBatch(boolean all) {
		if (JOBS.isEmpty()) return false;
		boolean dirty = false;
		Iterator<Job> it = JOBS.iterator();
		while (it.hasNext()) {
			Job j = it.next();
			if (!j.restoring) continue;
			World w = Bukkit.getWorld(j.world);
			int budget = all ? j.cells.size() : RESTORE_BATCH;
			int n = 0;
			while (j.cursor > 0 && n < budget) {
				Cell c = j.cells.get(--j.cursor);
				if (w != null) {
					try {
						BlockData data = Bukkit.createBlockData(c.data);
						Block b = w.getBlockAt(c.x, c.y, c.z);
						if (!(b.getState(false) instanceof TileState)) {
							b.setBlockData(data, false);
							if (c.altar) Arenas.markAltar(b, siteOf(j.theme));   // 제단 복구: 소환 표식(PDC)까지
							fx(w, c, j.theme, true);
						}
					} catch (Exception ignored) {}
				}
				n++;
			}
			if (n > 0) dirty = true;
			if (j.cursor <= 0) { it.remove(); dirty = true; }
		}
		return dirty;
	}

	private static void restoreJobNow(Job j) {
		World w = Bukkit.getWorld(j.world);
		if (w == null) return;
		for (int i = j.cursor - 1; i >= 0; i--) {
			Cell c = j.cells.get(i);
			try {
				BlockData data = Bukkit.createBlockData(c.data);
				Block b = w.getBlockAt(c.x, c.y, c.z);
				if (!(b.getState(false) instanceof TileState)) {
					b.setBlockData(data, false);
					if (c.altar) Arenas.markAltar(b, siteOf(j.theme));
				}
			} catch (Exception ignored) {}
		}
		j.cursor = 0;
	}

	private static void fx(World w, Cell c, Theme theme, boolean restore) {
		double x = c.x + 0.5, y = c.y + 1.05, z = c.z + 0.5;
		if (theme == Theme.FORGE) {
			w.spawnParticle(Particle.FLAME, x, y, z, 3, 0.2, 0.08, 0.2, 0.01);
			w.spawnParticle(Particle.BLOCK, x, y, z, 6, 0.2, 0.1, 0.2, 0.05, Material.MAGMA_BLOCK.createBlockData());
			if (!restore) w.spawnParticle(Particle.LAVA, x, y, z, 1, 0.1, 0.0, 0.1, 0);
		} else {
			w.spawnParticle(Particle.WAX_ON, x, y, z, 3, 0.2, 0.08, 0.2, 0.01);
			w.spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, 4, 0.25, 0.1, 0.25, 0.02);
			w.spawnParticle(Particle.BLOCK, x, y, z, 5, 0.2, 0.1, 0.2, 0.04, Material.CUT_COPPER.createBlockData());
		}
	}

	private static boolean replaceable(Block b) {
		if (b.getState(false) instanceof TileState) return false;
		if (Arenas.altarId(b) != null) return false;
		Material t = b.getType();
		if (t.isAir() || !t.isSolid()) return false;
		if (t == Material.LAVA || t == Material.WATER || t == Material.FIRE || t == Material.SOUL_FIRE) return false;
		return PAVEMENT.contains(t);
	}

	private static int floorY(World w, int x, int startY, int z) {
		Block feet = w.getBlockAt(x, startY, z);
		if (feet.isSolid() && PAVEMENT.contains(feet.getType())) return startY;
		for (int i = 0; i <= 6; i++) {
			Block b = w.getBlockAt(x, startY - 1 - i, z);
			if (b.isSolid()) return b.getY();
		}
		return startY - 1;
	}

	private static Material pattern(Theme theme, int dx, int dz, int dist2) {
		double d = Math.sqrt(dist2);
		if (theme == Theme.FORGE) return forge(dx, dz, d);
		return temple(dx, dz, d);
	}

	/** 흑암 + 마그마 핏줄 룬 */
	private static Material forge(int dx, int dz, double d) {
		if (d < 1.5) return Material.MAGMA_BLOCK;
		if (d < 2.6) return Material.GILDED_BLACKSTONE;
		double ang = Math.atan2(dz, dx);
		double slice = Math.PI / 4.0;
		double off = Math.abs(((ang / slice) % 1.0 + 1.0) % 1.0 - 0.5);
		boolean vein = off < (0.07 / Math.max(0.45, d / 9.0));
		if (vein && d < 11.5) return Material.MAGMA_BLOCK;
		int h = Math.floorMod(dx * 5 + dz * 11, 11);
		if (h == 0 && d > 3 && d < 10) return Material.GILDED_BLACKSTONE;
		int ring = (int) Math.floor(d);
		return switch (ring % 3) {
			case 0 -> Material.POLISHED_BLACKSTONE;
			case 1 -> Material.BLACKSTONE;
			default -> Material.POLISHED_BLACKSTONE_BRICKS;
		};
	}

	/** 구리 고리 + 시계 눈금 / 바늘 */
	private static Material temple(int dx, int dz, double d) {
		if (d < 1.2) return Material.GOLD_BLOCK;
		if (d < 2.2) return Material.CUT_COPPER;
		boolean cardinal = (dx == 0 && Math.abs(dz) <= RADIUS) || (dz == 0 && Math.abs(dx) <= RADIUS);
		boolean diagonal = Math.abs(Math.abs(dx) - Math.abs(dz)) <= 0 && d <= RADIUS;
		if (cardinal && d <= 11) return (int) d == 6 ? Material.GOLD_BLOCK : Material.WAXED_CUT_COPPER;
		if (diagonal && d <= 8) return Material.EXPOSED_CUT_COPPER;
		// 12시 눈금
		for (int h = 0; h < 12; h++) {
			double a = h * Math.PI / 6.0;
			int hx = (int) Math.round(Math.sin(a) * 10);
			int hz = (int) Math.round(-Math.cos(a) * 10);
			if (dx == hx && dz == hz) return Material.GOLD_BLOCK;
			int ix = (int) Math.round(Math.sin(a) * 7);
			int iz = (int) Math.round(-Math.cos(a) * 7);
			if (dx == ix && dz == iz) return Material.COPPER_BULB;
		}
		int ring = (int) Math.round(d);
		if (ring == 5 || ring == 9) return Material.WEATHERED_CUT_COPPER;
		return switch (ring % 4) {
			case 0 -> Material.CUT_COPPER;
			case 1 -> Material.EXPOSED_CUT_COPPER;
			case 2 -> Material.WAXED_EXPOSED_CUT_COPPER;
			default -> Material.OXIDIZED_CUT_COPPER;
		};
	}

	private static Site siteOf(Theme t) {
		return t == Theme.FORGE ? Site.FORGE : t == Theme.TEMPLE ? Site.TEMPLE : Site.GROTTO;
	}

	/** 제단 블록이 사라진 자리를 덮는 아레나 테마 블록 (대장간 = 금박 흑암, 신전 = 시계 한가운데 금, 정동 = 자수정) */
	private static Material altarFloor(Theme t) {
		return switch (t) {
			case FORGE -> Material.GILDED_BLACKSTONE;
			case TEMPLE -> Material.GOLD_BLOCK;
			case GROTTO -> Material.AMETHYST_BLOCK;
		};
	}

	/** 보스 발밑 근처(최대 4칸 아래)에서 보스 제단 블록을 찾는다 */
	private static Block findAltar(Boss boss, World w, int cx, int cz, int feetY) {
		if (boss.arena != null && boss.arena.world() == w) {
			Block b = w.getBlockAt(boss.arena.x(), boss.arena.y(), boss.arena.z());
			if (Arenas.altarId(b) != null) return b;
		}
		for (int i = 0; i <= 4; i++) {
			Block b = w.getBlockAt(cx, feetY - i, cz);
			if (Arenas.altarId(b) != null) return b;
		}
		return null;
	}

	/** 제단 둘레(3칸 밖) 여섯 곳의 바닥 높이 중 가장 흔한 값 = 아레나 바닥 층 */
	private static int floorAround(World w, Block altar) {
		int[][] off = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {3, 3}, {-3, -3}};
		java.util.Map<Integer, Integer> count = new java.util.HashMap<>();
		int best = altar.getY() - 1, bestN = 0;
		for (int[] o : off) {
			int x = altar.getX() + o[0], z = altar.getZ() + o[1];
			if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
			int y = floorY(w, x, altar.getY(), z);
			int n = count.merge(y, 1, Integer::sum);
			if (n > bestN) { bestN = n; best = y; }
		}
		return best;
	}

	private static Boss living(Job j) {
		try {
			UUID id = UUID.fromString(j.boss);
			for (Boss b : BossManager.all().values()) {
				if (b.mob != null && b.mob.getUniqueId().equals(id) && !b.dead && b.mob.isValid()) return b;
			}
		} catch (Exception ignored) {}
		return null;
	}

	private static File file() { return new File(HobbangPlugin.get().getDataFolder(), "floor-dress.json"); }

	public static void save() { save(false); }

	public static void saveSync() { save(true); }

	private static void save(boolean sync) {
		final File f = file();
		final String json = JOBS.isEmpty() ? null : GSON.toJson(JOBS.toArray(new Job[0]));
		final long id = SEQ.incrementAndGet();
		Runnable job = () -> {
			if (!sync && id != SEQ.get()) return;
			try {
				f.getParentFile().mkdirs();
				if (json == null) { if (f.exists()) f.delete(); return; }
				File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
				try (Writer wr = new FileWriter(tmp, java.nio.charset.StandardCharsets.UTF_8)) { wr.write(json); }
				java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception ex) {
				HobbangPlugin.get().getLogger().warning("floor-dress.json 저장 실패: " + ex);
			}
		};
		if (sync) {
			job.run();
		} else if (!IO.isShutdown()) IO.execute(job);
		else job.run();
	}

	private static void load() {
		File f = file();
		if (!f.exists()) return;
		try (Reader r = new FileReader(f, java.nio.charset.StandardCharsets.UTF_8)) {
			Job[] arr = GSON.fromJson(r, Job[].class);
			if (arr != null) {
				for (Job j : arr) {
					if (j != null && j.cells != null) JOBS.add(j);
				}
			}
		} catch (Exception ex) {
			HobbangPlugin.get().getLogger().warning("floor-dress.json 읽기 실패: " + ex);
		}
	}
}
