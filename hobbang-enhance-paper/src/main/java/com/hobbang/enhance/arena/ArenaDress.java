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
 * t368: 대장간·신전·정동은 무늬 전에 투기장 안(제단 기준 반지름 Site.half, 바닥 층 위 ~ 제단 y + Site.yHi)을
 * 상자류(StormArena.keepBlock)만 남기고 가운데에서 바깥으로 촤라라락 비운다. 다 비운 뒤 3초(FLOOR_DELAY) 기다렸다가
 * 무늬가 예전보다 2배(FLOOR_SPEED) 빠르게 퍼진다. 비운 블록은 기록하지 않는다 → 전투 뒤에는 제단만 돌아온다.
 */
public final class ArenaDress {
	private static final Gson GSON = new GsonBuilder().create();
	private static final int RADIUS = 12;
	private static final int TEMPLE_RADIUS = 13;
	private static final int APPLY_BATCH = 22;
	private static final int RESTORE_BATCH = 16;
	/** t368: 비우기 — 틱당 지우는 블록 수 / 틱당 훑는 기둥 수 / 다 비운 뒤 무늬 시작까지 (60틱 = 3초) / 무늬 전개 배속 */
	private static final int CLEAR_PER_TICK = 220, CLEAR_COLS_PER_TICK = 160, FLOOR_DELAY = 60, FLOOR_SPEED = 2;
	private static final List<Job> JOBS = new ArrayList<>();
	private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Hobbang-DressIO");
		t.setDaemon(true);
		return t;
	});
	private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();

	enum Theme { FORGE, TEMPLE, GROTTO, STORM }

	static final class Cell {
		int x, y, z;
		String data;
		/** true = 보스 제단 본체 (소환 시 평지로 비우고 종료 시 제단만 되살린다) */
		boolean altar;
		boolean altarCore;
		/** true = 소환 시 평지(AIR)로 지우는 받침·용암 등 (종료 시 주변 지형 복구 안 함) */
		boolean clearAir;
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
		/** t368: 비우기 진행 — 남은 기둥 {x, z, d²} (가까운 순), 높이 범위. null = 비우기 없음/끝 */
		transient List<int[]> clearCols;
		transient int clearIdx, clearY0, clearY1, clearTick;
		/** t368: 비우기가 끝난 뒤 무늬 시작까지 남은 틱 */
		transient int floorWait;
		/** t368: 비우기 동안 무늬 칸 계산을 미뤄 둔다 (비운 뒤 계산해야 기둥 자리까지 통째로 바뀜) */
		transient boolean floorPending;
	}

	private static final Set<Material> PAVEMENT = EnumSet.of(
		Material.BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS,
		Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE,
		Material.GILDED_BLACKSTONE, Material.BLACKSTONE_SLAB, Material.POLISHED_BLACKSTONE_SLAB,
		Material.BASALT, Material.POLISHED_BASALT, Material.SMOOTH_BASALT,
		Material.NETHERRACK, Material.NETHER_BRICKS, Material.CRACKED_NETHER_BRICKS, Material.CHISELED_NETHER_BRICKS,
		Material.RED_NETHER_BRICKS, Material.MAGMA_BLOCK, Material.SOUL_SAND, Material.SOUL_SOIL,
		Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM, Material.ANCIENT_DEBRIS,
		Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE_BRICKS, Material.CHISELED_STONE_BRICKS,
		Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.SMOOTH_STONE,
		Material.ANDESITE, Material.POLISHED_ANDESITE, Material.GRANITE, Material.POLISHED_GRANITE,
		Material.DIORITE, Material.POLISHED_DIORITE, Material.TUFF, Material.POLISHED_TUFF, Material.TUFF_BRICKS, Material.CHISELED_TUFF,
		Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_BRICKS,
		Material.DEEPSLATE_TILES, Material.CHISELED_DEEPSLATE, Material.REINFORCED_DEEPSLATE,
		Material.CALCITE, Material.TERRACOTTA, Material.PACKED_MUD, Material.MUD_BRICKS,
		Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.RED_SANDSTONE,
		Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER, Material.WEATHERED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER,
		Material.WAXED_CUT_COPPER, Material.WAXED_EXPOSED_CUT_COPPER, Material.WAXED_WEATHERED_CUT_COPPER, Material.WAXED_OXIDIZED_CUT_COPPER,
		Material.COPPER_BLOCK, Material.EXPOSED_COPPER, Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER,
		Material.WAXED_COPPER_BLOCK, Material.CHISELED_COPPER, Material.EXPOSED_CHISELED_COPPER,
		Material.WAXED_CHISELED_COPPER,
		Material.WEATHERED_CHISELED_COPPER, Material.OXIDIZED_CHISELED_COPPER,
		Material.COPPER_GRATE, Material.EXPOSED_COPPER_GRATE, Material.WEATHERED_COPPER_GRATE, Material.OXIDIZED_COPPER_GRATE,
		Material.COPPER_BULB, Material.WAXED_COPPER_BULB, Material.EXPOSED_COPPER_BULB, Material.WEATHERED_COPPER_BULB, Material.OXIDIZED_COPPER_BULB,
		Material.GOLD_BLOCK, Material.RAW_GOLD_BLOCK, Material.LIGHTNING_ROD, Material.OCHRE_FROGLIGHT,
		Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST, Material.SMOOTH_QUARTZ, Material.QUARTZ_BLOCK, Material.QUARTZ_BRICKS, Material.CHISELED_QUARTZ_BLOCK,
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

	private static Theme themeOf(BossKind k) {
		return switch (k) {
			case ABYSSAL_SMITH -> Theme.FORGE;
			case CHRONO_SENTINEL -> Theme.TEMPLE;
			case PRISM_COLOSSUS -> Theme.GROTTO;
			case TEMPEST_WYVERN -> Theme.STORM;
		};
	}

	/** 보스 소환 의식 시작 시 아레나 전환을 미리 시작한다 */
	public static void startRitual(BossKind k, Block altar, Location spawn) {
		if (k == null || altar == null) return;
		Theme theme = themeOf(k);
		World w = altar.getWorld();
		Location ref = spawn != null ? spawn : altar.getLocation();
		int cx = altar.getX(), cz = altar.getZ();
		int cy = (theme != Theme.TEMPLE)
			? Math.min(altar.getY() - 1, floorAround(w, altar, theme))
			: floorAround(w, altar, theme);
		if (theme == Theme.GROTTO) cy = Math.min(cy, altar.getY() - 1);
		if (theme == Theme.STORM) cy = Math.min(cy, altar.getY() - 1);
		String key = "ritual:" + k.id + ":" + altar.getX() + "," + altar.getY() + "," + altar.getZ();
		startInternal(key, theme, w, cx, cy, cz, altar);
	}

	/** 소환 완료 후 생성된 보스 UUID 를 진행 중인 의식 아레나 작업에 연결 (명령어 소환 등은 새로 시작) */
	public static void bindBoss(Boss boss) {
		if (boss == null || boss.mob == null || !boss.mob.isValid()) return;
		Location at = boss.mob.getLocation();
		String wName = at.getWorld().getName();
		for (Job j : JOBS) {
			if (!j.restoring && j.boss != null && j.boss.startsWith("ritual:" + boss.kind.id + ":")
				&& wName.equals(j.world) && Math.abs(j.cx - at.getBlockX()) <= 20 && Math.abs(j.cz - at.getBlockZ()) <= 20) {
				j.boss = boss.mob.getUniqueId().toString();
				save();
				return;
			}
		}
		start(boss);
	}

	/** 소환 시 호출 (제단+받침 평탄화 + 아레나 문양 적용) */
	public static void start(Boss boss) {
		if (boss == null || boss.mob == null || !boss.mob.isValid()) return;
		BossKind k = boss.kind;
		Theme theme = themeOf(k);
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
		Block altar = findAltar(boss, w, cx, cz, at.getBlockY());
		if (altar != null) {
			cx = altar.getX();
			cz = altar.getZ();
			cy = (theme != Theme.TEMPLE) ? Math.min(altar.getY() - 1, floorAround(w, altar, theme)) : floorAround(w, altar, theme);
		}
		startInternal(boss.mob.getUniqueId().toString(), theme, w, cx, cy, cz, altar);
	}

	private static void startInternal(String bossKey, Theme theme, World w, int cx, int cy, int cz, Block altar) {
		// 같은 자리에 남은 작업이 있으면 먼저 되돌린 뒤 새로 입힌다
		for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
			Job old = it.next();
			if (old.world.equals(w.getName()) && old.cx == cx && old.cz == cz && Math.abs(old.cy - cy) <= 3) {
				restoreJobNow(old);
				it.remove();
			}
		}
		Job job = new Job();
		job.boss = bossKey;
		job.world = w.getName();
		job.cx = cx;
		job.cy = cy;
		job.cz = cz;
		job.theme = theme;
		job.cursor = 0;
		job.restoring = false;
		if (altar != null) {   // 소환대(제단) 본체 + 받침대(크로노 신전 7x7, 자수정 정동은 1칸 아래 바닥까지, 그 외 3x3)를 비우고 종료 시 전체 복구
			int topY = altar.getY();
			int ax = altar.getX(), az = altar.getZ();
			int rAltar = theme == Theme.TEMPLE ? 3 : 1;
			int minY = theme == Theme.GROTTO ? cy : cy + 1;
			for (int y = topY; y >= minY; y--) {
				for (int dx = -rAltar; dx <= rAltar; dx++) {
					for (int dz = -rAltar; dz <= rAltar; dz++) {
						Block pb = w.getBlockAt(ax + dx, y, az + dz);
						boolean isCore = pb.equals(altar);
						if (!shouldIncludeAltarArea(pb.getType().isAir(), isCore, StormArena.keepBlock(pb))) continue;
						Cell c = new Cell();
						c.x = pb.getX();
						c.y = pb.getY();
						c.z = pb.getZ();
						c.data = pb.getBlockData().getAsString();
						c.altar = true;
						c.altarCore = isCore;
						c.clearAir = false;
						job.cells.add(c);
					}
				}
			}
			if (altar.getY() <= cy && !altar.getType().isAir()) {
				Cell c = new Cell();
				c.x = altar.getX();
				c.y = altar.getY();
				c.z = altar.getZ();
				c.data = altar.getBlockData().getAsString();
				c.altar = true;
				c.altarCore = true;
				job.cells.add(c);
			}
		}
		Site site = theme == Theme.STORM ? null : siteOf(theme);
		if (site != null && planClear(job, w, site, altar, cx, cy, cz)) {
			job.floorPending = true;   // 무늬 칸은 비우기가 끝난 뒤 planFloor 에서
			JOBS.add(job);
			save();
			return;
		}
		planFloor(job, w);
		if (job.cells.isEmpty()) return;
		JOBS.add(job);
		save();
	}

	/** t368: 투기장 비우기 계획 — 제단(없으면 중심) 기준 원기둥, 바닥 층(cy) 위만, 가운데 → 바깥 */
	private static boolean planClear(Job job, World w, Site site, Block altar, int cx, int cy, int cz) {
		int ay = altar != null ? altar.getY() : cy + 1;
		job.clearY0 = Math.max(w.getMinHeight(), cy + 1);   // 바닥 층과 그 아래는 절대 건드리지 않음
		job.clearY1 = Math.min(w.getMaxHeight() - 1, ay + site.yHi);
		if (job.clearY1 < job.clearY0) return false;
		int r = site.half, r2 = r * r;
		List<int[]> cols = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
			int d2 = dx * dx + dz * dz;
			if (d2 <= r2) cols.add(new int[]{cx + dx, cz + dz, d2});
		}
		cols.sort((a, b) -> a[2] != b[2] ? Integer.compare(a[2], b[2]) : Integer.compare(a[0] * 31 + a[1], b[0] * 31 + b[1]));
		job.clearCols = cols;
		job.clearIdx = 0;
		return true;
	}

	/** t368: 비우기 한 틱. 상자류·기반암·포탈 등(StormArena.keepBlock)과 제단은 남긴다. 제단 칸(cells)은 이미 기록돼 있어 복구된다 */
	private static boolean clearBatch(Job j, World w) {
		int removed = 0, cols = 0;
		Block last = null;
		while (j.clearIdx < j.clearCols.size() && removed < CLEAR_PER_TICK && cols < CLEAR_COLS_PER_TICK) {
			int[] o = j.clearCols.get(j.clearIdx++);
			cols++;
			if (!w.isChunkLoaded(o[0] >> 4, o[1] >> 4)) continue;
			for (int y = j.clearY1; y >= j.clearY0; y--) {   // 위에서 아래로 (모래·자갈이 떨어지지 않게)
				Block b = w.getBlockAt(o[0], y, o[1]);
				if (b.getType().isAir() || StormArena.keepBlock(b) || Arenas.altarId(b) != null) continue;
				if (removed % 7 == 0) w.spawnParticle(Particle.BLOCK, o[0] + 0.5, y + 0.5, o[1] + 0.5, 6, 0.3, 0.3, 0.3, 0.05, b.getBlockData());
				b.setType(Material.AIR, false);
				last = b;
				removed++;
			}
		}
		j.clearTick++;
		if (last != null && j.clearTick % 2 == 0) {
			BU.sound(last.getLocation().add(0.5, 0.5, 0.5), j.theme == Theme.FORGE ? Sound.BLOCK_BASALT_BREAK : j.theme == Theme.GROTTO ? Sound.BLOCK_AMETHYST_BLOCK_BREAK : Sound.BLOCK_STONE_BREAK,
				SoundCategory.BLOCKS, 1.0F, 0.6F + (j.clearTick % 5) * 0.1F);
		}
		if (j.clearIdx < j.clearCols.size()) return false;   // 비운 블록은 저장하지 않으므로 JSON 갱신 불필요
		j.clearCols = null;   // 다 비웠다 → 무늬 칸 계산 후 3초 대기
		if (j.floorPending) { j.floorPending = false; planFloor(j, w); }
		j.floorWait = FLOOR_DELAY;
		BU.sound(new Location(w, j.cx + 0.5, j.cy + 1, j.cz + 0.5), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.AMBIENT, 1.5F, 0.6F);
		return true;
	}

	/** 바닥 무늬 칸 (대장간 반경 36, 신전 13). t368: 비우기가 있으면 비운 뒤에 계산 */
	private static void planFloor(Job job, World w) {
		Theme theme = job.theme;
		int cx = job.cx, cy = job.cy, cz = job.cz;
		Block altar = null;
		for (Cell c : job.cells) if (c.altarCore) { altar = w.getBlockAt(c.x, c.y, c.z); break; }
		int rad = switch (theme) {
			case FORGE -> Site.FORGE.half;
			case TEMPLE -> TEMPLE_RADIUS;
			default -> 0;
		};
		List<int[]> order = new ArrayList<>();
		for (int dx = -rad; dx <= rad && rad > 0; dx++) {
			for (int dz = -rad; dz <= rad; dz++) {
				if (dx * dx + dz * dz > rad * rad) continue;
				order.add(new int[]{dx, dz, dx * dx + dz * dz});
			}
		}
		order.sort((a, b) -> a[2] != b[2] ? Integer.compare(a[2], b[2]) : Integer.compare(a[0] * 31 + a[1], b[0] * 31 + b[1]));
		for (int[] o : order) {
			int bx = cx + o[0], bz = cz + o[1];
			if (theme == Theme.TEMPLE && Math.sqrt(o[2]) > 4.6
				&& w.getBlockAt(bx, cy + 1, bz).getType().isSolid()
				&& w.getBlockAt(bx, cy + 2, bz).getType().isSolid()) {
				continue;   // 신전 외곽 기둥·아치 기초 보호
			}
			Block b = w.getBlockAt(bx, cy, bz);
			if (altar != null && b.equals(altar)) continue;
			if (Arenas.altarId(b) != null || StormArena.keepBlock(b) || b.getState(false) instanceof TileState) continue;
			Material want = pattern(theme, o[0], o[1], o[2]);
			if (b.getType() != want) {
				Cell c = new Cell();
				c.x = b.getX();
				c.y = b.getY();
				c.z = b.getZ();
				c.data = b.getBlockData().getAsString();
				job.cells.add(c);
			}
			for (int dy = 1; dy <= 2; dy++) {
				Block ab = w.getBlockAt(bx, cy + dy, bz);
				Material mt = ab.getType();
				if (mt == Material.LAVA || mt == Material.WATER || mt == Material.FIRE || mt == Material.SOUL_FIRE) {
					Cell ac = new Cell();
					ac.x = ab.getX();
					ac.y = ab.getY();
					ac.z = ab.getZ();
					ac.data = ab.getBlockData().getAsString();
					ac.clearAir = true;
					job.cells.add(ac);
				}
			}
		}
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
		if (j.clearCols != null) {                               // t368: 1단계 — 투기장 비우기 (제단 본체는 예전처럼 즉시 제거)
			boolean altarDone = false;
			while (j.cursor < j.cells.size() && j.cells.get(j.cursor).altar) {
				Cell c = j.cells.get(j.cursor++);
				removeAltarCell(j, w, c);
				altarDone = true;
			}
			return clearBatch(j, w) || altarDone;
		}
		if (j.floorWait > 0) { j.floorWait--; return false; }    // t368: 2단계 — 3초 대기
		if (j.floorPending) { j.floorPending = false; planFloor(j, w); }   // (재시작 등으로 비우기 상태가 사라진 경우)
		int batch = (j.cells.size() > 1000 ? 72 : APPLY_BATCH) * FLOOR_SPEED;   // t368: 2배 빠르게
		if (j.theme == Theme.FORGE && j.tick >= 12 / FLOOR_SPEED) {
			int rem = Math.max(0, j.cells.size() - j.cursor);
			int remTicks = Math.max(1, 65 / FLOOR_SPEED - j.tick);
			batch = Math.max(32 * FLOOR_SPEED, (rem + remTicks - 1) / remTicks);
		}
		int n = 0;
		while (j.cursor < j.cells.size() && n < batch) {
			Cell peek = j.cells.get(j.cursor);
			// 대장장이: 제단 본체(altar)는 즉시 제거하고, 하단 아레나는 중앙 비우기가 시작한 뒤 뒤따라 전개
			if (j.theme == Theme.FORGE && !peek.altar && j.tick < 12 / FLOOR_SPEED) break;
			Cell c = j.cells.get(j.cursor++);
			Block b = w.getBlockAt(c.x, c.y, c.z);
			if (c.clearAir) {
				b.setType(Material.AIR, false);
				n++;
				continue;
			}
			if (c.altar) {   // 보스 제단 본체: 평지(AIR)로 제거하고 바닥 높이면 테마 블록으로 덮음
				removeAltarCell(j, w, c);
				n++;
				continue;
			}
			if (Arenas.altarId(b) != null || StormArena.keepBlock(b) || b.getState(false) instanceof TileState) { n++; continue; }
			int dx = c.x - j.cx, dz = c.z - j.cz;
			Material want = pattern(j.theme, dx, dz, dx * dx + dz * dz);
			b.setType(want, false);
			if (batch <= APPLY_BATCH * FLOOR_SPEED || (n & 3) == 0) fx(w, c, j.theme, false);
			n++;
		}
		j.tick++;
		if (j.tick % 4 == 0 && j.cursor > 0 && j.cursor <= j.cells.size()) {
			Cell last = j.cells.get(Math.min(j.cursor, j.cells.size()) - 1);
			Location at = new Location(w, last.x + 0.5, last.y + 1, last.z + 0.5);
			if (j.theme == Theme.FORGE) BU.sound(at, Sound.BLOCK_LAVA_POP, SoundCategory.AMBIENT, 0.7F, 0.7F);
			else if (j.theme == Theme.GROTTO) BU.sound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 0.8F, 1.1F);
			else if (j.theme == Theme.STORM) BU.sound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 0.45F, 1.6F);
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
				if (c.altar && w != null) {
					try {
						BlockData data = Bukkit.createBlockData(c.data);
						Block b = w.getBlockAt(c.x, c.y, c.z);
						if (!(b.getState(false) instanceof TileState)) {
							b.setBlockData(data, false);
							if (c.altarCore || data.getMaterial() == siteOf(j.theme).altarBlock && c.x == j.cx && c.z == j.cz) {
								Arenas.markAltar(b, siteOf(j.theme));   // 제단 복구: 소환 표식(PDC)까지
							}
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

	static boolean shouldIncludeAltarArea(boolean isAir, boolean isCore, boolean keep) {
		if (isAir) return false;
		return isCore || !keep;
	}

	private static void restoreJobNow(Job j) {
		World w = Bukkit.getWorld(j.world);
		if (w == null) return;
		for (int i = j.cursor - 1; i >= 0; i--) {
			Cell c = j.cells.get(i);
			if (!c.altar) continue;
			try {
				BlockData data = Bukkit.createBlockData(c.data);
				Block b = w.getBlockAt(c.x, c.y, c.z);
				if (!(b.getState(false) instanceof TileState)) {
					b.setBlockData(data, false);
					if (c.altarCore || data.getMaterial() == siteOf(j.theme).altarBlock && c.x == j.cx && c.z == j.cz) {
						Arenas.markAltar(b, siteOf(j.theme));
					}
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
		} else if (theme == Theme.GROTTO) {
			w.spawnParticle(Particle.END_ROD, x, y, z, 2, 0.2, 0.1, 0.2, 0.01);
			w.spawnParticle(Particle.ENCHANT, x, y + 0.3, z, 4, 0.25, 0.2, 0.25, 0.2);
			w.spawnParticle(Particle.BLOCK, x, y, z, 5, 0.2, 0.1, 0.2, 0.04, Material.AMETHYST_BLOCK.createBlockData());
		} else if (theme == Theme.STORM) {
			w.spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, 5, 0.25, 0.15, 0.25, 0.03);
			w.spawnParticle(Particle.CLOUD, x, y, z, 3, 0.2, 0.1, 0.2, 0.02);
		} else {
			w.spawnParticle(Particle.WAX_ON, x, y, z, 3, 0.2, 0.08, 0.2, 0.01);
			w.spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, 4, 0.25, 0.1, 0.25, 0.02);
			w.spawnParticle(Particle.BLOCK, x, y, z, 5, 0.2, 0.1, 0.2, 0.04, Material.CUT_COPPER.createBlockData());
		}
	}

	private static int floorY(World w, int x, int startY, int z) {
		Block feet = w.getBlockAt(x, startY, z);
		if (feet.isSolid() && PAVEMENT.contains(feet.getType())) return startY;
		for (int i = 0; i <= 6; i++) {
			Block b = w.getBlockAt(x, startY - 1 - i, z);
			if (b.isSolid() || b.getType() == Material.LAVA || b.getType() == Material.WATER) return b.getY();
		}
		return startY - 1;
	}

	private static Material pattern(Theme theme, int dx, int dz, int dist2) {
		double d = Math.sqrt(dist2);
		return switch (theme) {
			case FORGE -> forge(dx, dz, d);
			case GROTTO -> grotto(dx, dz, d);
			case TEMPLE -> temple(dx, dz, d);
			case STORM -> Material.CHISELED_TUFF;
		};
	}

	/** 흑암 + 마그마 핏줄 룬 + 우는 흑요석/금박 액센트 (대장간 구조물 전체 반경 36 기준) */
	private static Material forge(int dx, int dz, double d) {
		if (d < 2.2) return Material.MAGMA_BLOCK;
		if (d < 4.0) return Material.GILDED_BLACKSTONE;
		if (Math.abs(d - 8.5) < 0.65 || Math.abs(d - 20.0) < 0.70 || Math.abs(d - 32.0) < 0.75) {
			return (Math.abs(dx) + Math.abs(dz)) % 2 == 0 ? Material.CRYING_OBSIDIAN : Material.CHISELED_POLISHED_BLACKSTONE;
		}
		double ang = Math.atan2(dz, dx);
		double slice = Math.PI / 4.0;
		double off = Math.abs(((ang / slice) % 1.0 + 1.0) % 1.0 - 0.5);
		boolean vein = off < (0.075 / Math.max(0.45, d / 16.0));
		if (vein && d < 34.5) return Material.MAGMA_BLOCK;
		int h = Math.floorMod(dx * 5 + dz * 11, 13);
		if (h == 0 && d > 4 && d < 33) return Material.GILDED_BLACKSTONE;
		int ring = (int) Math.floor(d / 1.5);
		return switch (ring % 3) {
			case 0 -> Material.POLISHED_BLACKSTONE;
			case 1 -> Material.BLACKSTONE;
			default -> Material.POLISHED_BLACKSTONE_BRICKS;
		};
	}

	/** 자수정 + 프리즈마린 + 바다 랜턴 결정 광맥 */
	private static Material grotto(int dx, int dz, double d) {
		if (d < 1.5) return Material.SEA_LANTERN;
		if (d < 2.8) return Material.AMETHYST_BLOCK;
		double ang = Math.atan2(dz, dx);
		double slice = Math.PI / 3.0;
		double off = Math.abs(((ang / slice) % 1.0 + 1.0) % 1.0 - 0.5);
		if (off < (0.065 / Math.max(0.45, d / 9.0)) && d < 11.5) {
			return (int) Math.round(d) % 3 == 0 ? Material.SEA_LANTERN : Material.AMETHYST_BLOCK;
		}
		int ring = (int) Math.round(d);
		if (ring == 5 || ring == 9) return (dx + dz) % 2 == 0 ? Material.PRISMARINE_BRICKS : Material.CALCITE;
		return switch (ring % 3) {
			case 0 -> Material.DARK_PRISMARINE;
			case 1 -> Material.DEEPSLATE_TILES;
			default -> Material.POLISHED_DEEPSLATE;
		};
	}

	/** 청동·금박·심층암 타일 해시계 다이얼 + 12시 빛 비콘 + 시침·분침 */
	private static Material temple(int dx, int dz, double d) {
		if (d < 1.5) return Material.GOLD_BLOCK;
		if (d <= 3.55) {
			boolean star = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
			if (star && d >= 2.2) return Material.OCHRE_FROGLIGHT;
			if (star) return Material.WAXED_CHISELED_COPPER;
			return (Math.abs(dx) + Math.abs(dz)) % 2 == 0 ? Material.GOLD_BLOCK : Material.WAXED_COPPER_BLOCK;
		}
		double ux = Math.sin(Math.PI / 3.0), uz = -Math.cos(Math.PI / 3.0); // 2시 방향 시침
		if (d <= 4.75) {
			for (int h = 0; h < 12; h++) {
				double a = h * Math.PI / 6.0;
				if (dx == (int) Math.round(Math.sin(a) * 4.0) && dz == (int) Math.round(-Math.cos(a) * 4.0)) return Material.GOLD_BLOCK;
			}
			return Material.WAXED_CHISELED_COPPER;
		}
		if (d >= 12.15) {
			int tooth = Math.floorMod((int) Math.round(Math.atan2(dz, dx) * 12.0 / Math.PI), 2);
			return tooth == 0 ? Material.GOLD_BLOCK : Material.WAXED_CUT_COPPER;
		}
		// 시계 분침(12시)·시침(2시)
		if (dx == 0 && dz < 0 && d <= 11.4) return Material.GOLD_BLOCK;
		if (dx * ux + dz * uz > 0 && d <= 8.8 && Math.abs(dx * uz - dz * ux) < 0.62) return Material.GOLD_BLOCK;
		// 12시간 빛 표식 (바깥 10.5칸 야광 · 안쪽 6.5칸 금블록)
		for (int h = 0; h < 12; h++) {
			double a = h * Math.PI / 6.0;
			int hx = (int) Math.round(Math.sin(a) * 10.5), hz = (int) Math.round(-Math.cos(a) * 10.5);
			if (dx == hx && dz == hz) return Material.OCHRE_FROGLIGHT;
			int ix = (int) Math.round(Math.sin(a) * 6.5), iz = (int) Math.round(-Math.cos(a) * 6.5);
			if (dx == ix && dz == iz) return Material.GOLD_BLOCK;
		}
		if (Math.abs(d - 6.5) < 0.48) return Material.WAXED_COPPER_BLOCK;
		if (Math.abs(d - 10.5) < 0.48) return Material.WAXED_CUT_COPPER;
		double ang = Math.atan2(dz, dx);
		double spoke = Math.abs(((ang / (Math.PI / 6.0)) % 1.0 + 1.0) % 1.0 - 0.5);
		if (spoke < (0.09 / Math.max(0.5, d / 7.5))) return Material.WAXED_CUT_COPPER;
		return (Math.floorMod(dx, 2) ^ Math.floorMod(dz, 2)) == 0 ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE;
	}

	/** 보스 제단 본체 칸 제거: 평지(AIR)로, 바닥 높이면 테마 블록으로 덮음 */
	private static void removeAltarCell(Job j, World w, Cell c) {
		Block b = w.getBlockAt(c.x, c.y, c.z);
		b.setType(c.y <= j.cy ? altarFloor(j.theme) : Material.AIR, false);
		if (c.altarCore) {
			fx(w, c, j.theme, false);
			BU.sound(new Location(w, c.x + 0.5, c.y + 0.5, c.z + 0.5), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 1.2F, 0.7F);
		}
	}

	private static Site siteOf(Theme t) {
		return switch (t) {
			case FORGE -> Site.FORGE;
			case TEMPLE -> Site.TEMPLE;
			case GROTTO -> Site.GROTTO;
			case STORM -> Site.STORM;
		};
	}

	/** 제단 블록이 사라진 자리를 덮는 아레나 테마 블록 */
	private static Material altarFloor(Theme t) {
		return switch (t) {
			case FORGE -> Material.GILDED_BLACKSTONE;
			case TEMPLE -> Material.GOLD_BLOCK;
			case GROTTO -> Material.AMETHYST_BLOCK;
			case STORM -> Material.CHISELED_TUFF;
		};
	}

	/** 보스 발밑 근처(최대 반경 24칸)에서 보스 제단 블록을 찾는다 */
	private static Block findAltar(Boss boss, World w, int cx, int cz, int feetY) {
		if (boss.arena != null && boss.arena.world() == w) {
			Block b = w.getBlockAt(boss.arena.x(), boss.arena.y(), boss.arena.z());
			if (Arenas.altarId(b) != null) return b;
		}
		for (int i = 0; i <= 4; i++) {
			Block b = w.getBlockAt(cx, feetY - i, cz);
			if (Arenas.altarId(b) != null) return b;
		}
		for (int r = 1; r <= 24; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
					for (int dy = -6; dy <= 6; dy++) {
						Block b = w.getBlockAt(cx + dx, feetY + dy, cz + dz);
						if (Arenas.altarId(b) != null) return b;
					}
				}
			}
		}
		Site site = Site.of(boss.kind);
		if (site != null && site.structure != null) {
			for (int r = 0; r <= 12; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
						for (int dy = -6; dy <= 6; dy++) {
							Block b = w.getBlockAt(cx + dx, feetY + dy, cz + dz);
							if (b.getType() == site.altarBlock) return b;
						}
					}
				}
			}
		}
		return null;
	}

	/** 제단 둘레의 실제 아레나 바닥 높이를 찾는다 (신전은 단상 밖 r=7 중정 바닥 우선) */
	private static int floorAround(World w, Block altar, Theme theme) {
		int[][] off = theme == Theme.TEMPLE
			? new int[][]{{7, 0}, {-7, 0}, {0, 7}, {0, -7}, {5, 5}, {-5, -5}, {5, -5}, {-5, 5}}
			: new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {3, 3}, {-3, -3}};
		java.util.Map<Integer, Integer> count = new java.util.HashMap<>();
		int best = altar.getY() - (theme == Theme.TEMPLE ? 3 : 1), bestN = 0;
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
			// 대기 중/실행 중인 비동기 저장(옛 JSON, 같은 .tmp 파일)이 동기 저장과 겹치거나 뒤에 덮어쓰지 않게 먼저 비운다
			IO.shutdown();
			try { IO.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
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
