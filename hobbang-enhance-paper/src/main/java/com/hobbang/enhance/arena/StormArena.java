package com.hobbang.enhance.arena;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.boss.Boss;
import com.hobbang.enhance.boss.BossKind;
import com.hobbang.enhance.boss.BossManager;
import com.hobbang.enhance.boss.Ritual;
import com.hobbang.enhance.item.HItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * v1.4.0 폭풍의 와이번 소환: 플레이어가 쌓는 폭풍 제단 + 천둥번개 + 폭풍의 핵.
 *
 * 제단 (5×5) — 다이아몬드 십자 + 구리 중심 + 모서리 촛불 기둥
 *             P · D · P      P = 심층암(아무 종류), 위에 촛불(아무 색) (대각선 2칸)
 *             · D C D ·      D = 다이아몬드 블록 (3×3 모서리 + 십자 끝)
 *             D C C C D      C = 구리 블록(아무 산화/밀랍 단계), 가운데 위에 피뢰침
 *             · D C D ·
 *             P · D · P
 * 의식: 천둥번개가 칠 때 폭풍의 핵을 들고 피뢰침(또는 가운데 구리)을 우클릭.
 * → 촛불이 켜지고, 아레나보다 조금 큰 범위의 지형(제단 y 위만)을 잘라낸 뒤 하늘에 가운데 섬 + 나선 점프맵 + 바깥 섬 고리가 지어지고
 *   (t385) 15초 번개 세례(고리 바깥 가장자리, 입구 제외) → 2초 뒤 엔드 수정 빔 → 2초 뒤 와이번.
 * 잘라낸 지형과 투기장 블록은 전부 기록 → 전투가 끝나면 무너지며 원래대로 복구 (재시작해도 arenas.json 으로 이어짐).
 * 틱 비용: 건설 40블록/틱, 복구 40블록/틱, 절삭 96블록/틱, 감시 20틱마다 1회.
 */
public final class StormArena implements Listener {
	/** t385: 바깥 섬 고리(블록 ≈ 6~8천 개)가 생겨 건설·복구를 틱당 40 블록으로 (예전 5 / 8) */
	private static final int BUILD_PER_TICK = 40, CRUMBLE_PER_TICK = 40, CLEAR_PER_TICK = 96;
	/** 지형 절삭: 아레나(반경 half, 높이 yLo~yHi)보다 조금 더 크게 */
	private static final int CLEAR_PAD = 4;
	private static final int DISC_Y = 9;

	// ───────── t385: 바깥 섬 고리 투기장 + 나선 점프맵 ─────────
	/** 고리 윗면 높이(제단 y 기준). 나선 디딤돌 0..SPIRAL_STEPS-1 = cy..cy+16, 마지막 칸에서 +1 올라 고리 입구 */
	static final int RING_TOP = 17;
	/** 고리 안쪽/바깥 반지름 기준값 (가장자리는 각도별로 흔들림: 안쪽 15.7~16.9, 바깥 29.3~31.8 → 폭 ≈ 13~16) */
	static final double RING_IN = 16.3, RING_OUT = 30.5, ENTRANCE_IN = 15.0;
	/** 입구(나선 끝) 앞 고리 안쪽 가장자리를 15.0 으로 당겨 착지판을 만드는 각도 범위 / 번개를 빼는 각도 범위 (라디안) */
	static final double ENTRANCE_HALF = Math.toRadians(10), LIGHTNING_GAP_HALF = Math.toRadians(22);
	/** 지형 절삭 반경 (고리 바깥 + 2) */
	static final int CARVE_R = (int) Math.ceil(RING_OUT + 1.3) + 2;
	/** 나선: 반지름 5.5(발판 가장자리에서 2칸) → 14.0, 17칸, 칸마다 +1, 칸 사이 간격 2칸 (탐욕 배치) */
	static final double SPIRAL_R0 = 5.5, SPIRAL_R1 = 14.0, SPIRAL_A0 = Math.PI;
	static final int SPIRAL_STEPS = 17;
	/** 디딤돌 2×2 의 기준 칸 {dx, dz, dy} (제단 기준) */
	static final List<int[]> SPIRAL = spiral();
	/** 입구 각도 (마지막 디딤돌 방향, 라디안 0..2π) */
	static final double ENTRANCE = entranceAngle();
	/** 소환 연출 타이밍 (틱): 번개 세례 15초 → 2초 뒤 엔드 수정 빔 → 2초 뒤 와이번 */
	static final int STORM_TICKS = 300, BEAM_AT = STORM_TICKS + 40, SPAWN_AT = BEAM_AT + 40;

	private static int[][] stepCells(double r, double th) {
		int a = (int) Math.floor(r * Math.cos(th)), b = (int) Math.floor(r * Math.sin(th));
		return new int[][]{{a, b}, {a + 1, b}, {a, b + 1}, {a + 1, b + 1}};
	}

	private static double cellGap(int[][] p, int[][] q) {
		double best = Double.MAX_VALUE;
		for (int[] a : p) for (int[] b : q)
			best = Math.min(best, Math.hypot(Math.max(0, Math.abs(a[0] - b[0]) - 1), Math.max(0, Math.abs(a[1] - b[1]) - 1)));
		return best;
	}

	private static double[] spiralAngles() {
		double[] out = new double[SPIRAL_STEPS];
		double th = SPIRAL_A0;
		out[0] = th;
		int[][] prev = stepCells(SPIRAL_R0, th);
		for (int k = 1; k < SPIRAL_STEPS; k++) {
			double r = SPIRAL_R0 + (SPIRAL_R1 - SPIRAL_R0) * k / (SPIRAL_STEPS - 1);
			double t = th;
			int[][] c;
			do { t += 0.002; c = stepCells(r, t); } while (cellGap(prev, c) < 2.0);   // 바로 앞 칸과 빈칸 2칸이 될 때까지 돈다
			th = t; out[k] = th; prev = c;
		}
		return out;
	}

	private static List<int[]> spiral() {
		List<int[]> out = new ArrayList<>();
		double[] ang = spiralAngles();
		for (int k = 0; k < SPIRAL_STEPS; k++) {
			double r = SPIRAL_R0 + (SPIRAL_R1 - SPIRAL_R0) * k / (SPIRAL_STEPS - 1);
			int[][] c = stepCells(r, ang[k]);
			out.add(new int[]{c[0][0], c[0][1], k});
		}
		return out;
	}

	private static double entranceAngle() {
		double[] ang = spiralAngles();
		double a = ang[ang.length - 1] % (2 * Math.PI);
		return a < 0 ? a + 2 * Math.PI : a;
	}

	static double wrapRad(double a) {
		a %= 2 * Math.PI;
		if (a > Math.PI) a -= 2 * Math.PI;
		if (a < -Math.PI) a += 2 * Math.PI;
		return a;
	}

	/** 바깥 고리 모양 (위치별 고정 시드): 가장자리 흔들림·두께 */
	static final class Ring {
		final double[] ph = new double[8];
		Ring(int cx, int cz) {
			Random r = new Random(cx * 734287671L ^ cz * 912931137L ^ 0x5EEDL);
			for (int i = 0; i < ph.length; i++) ph[i] = r.nextDouble() * Math.PI * 2;
		}
		double rin(double th) {
			if (Math.abs(wrapRad(th - ENTRANCE)) < ENTRANCE_HALF) return ENTRANCE_IN;   // 나선 끝 착지판
			return RING_IN + 0.35 * Math.sin(5 * th + ph[0]) + 0.25 * Math.sin(11 * th + ph[1]);
		}
		double rout(double th) {
			return RING_OUT + 0.6 * Math.sin(3 * th + ph[2]) + 0.4 * Math.sin(7 * th + ph[3]) + 0.3 * Math.sin(13 * th + ph[4]);
		}
		/** 아랫면 두께(윗면 포함 블록 수): 고리 폭 가운데가 가장 두껍고 가장자리로 갈수록 얇다 (떠 있는 섬 렌즈 모양) */
		int depth(double r, double th, Random rnd) {
			double u = Math.max(0, Math.min(1, (r - rin(th)) / (rout(th) - rin(th))));
			int d = 1 + (int) Math.round((2.6 + 1.4 * Math.sin(4 * th + ph[5])) * Math.pow(Math.sin(Math.PI * u), 0.7));
			if (u > 0.15 && u < 0.85 && rnd.nextInt(14) == 0) d += 1 + rnd.nextInt(2);   // 아래로 늘어진 바위 끝
			return d;
		}
	}

	/** 번개 문양 (고리 위 8곳): 행 = 바깥(+3) → 안쪽(-3), 열 = 접선 -2..+2 */
	private static final String[] BOLT = {"...##", "..##.", ".##..", "#####", "..##.", ".##..", "##..."};
	static final double EMBLEM_R = 25.5, PLATE_U = 3.6, PLATE_V = 3.2;
	private static final Random RNG = new Random();
	private static final Gson GSON = new GsonBuilder().create();

	enum State { CARVING, BUILDING, COUNTDOWN, SUMMONING, ACTIVE, CRUMBLING }

	/** 바뀐 블록 1개: 좌표 + 원래 상태 + 놓은 상태 */
	static final class Change {
		int x, y, z; String old, now;
		Change(int x, int y, int z, String old, String now) { this.x = x; this.y = y; this.z = z; this.old = old; this.now = now; }
	}

	static final class Plan {
		final int x, y, z; final BlockData data;
		/** true = 제단 블록 철거 (아레나가 뜰 때 제단이 사라진다): free() 검사 없이 data(= 아래 땅/공기)로 덮는다 */
		final boolean remove;
		Plan(int x, int y, int z, BlockData data) { this(x, y, z, data, false); }
		Plan(int x, int y, int z, BlockData data, boolean remove) { this.x = x; this.y = y; this.z = z; this.data = data; this.remove = remove; }
	}

	static final class Arena {
		String world; int cx, cy, cz;
		State state = State.BUILDING;
		String boss;
		List<Change> placed = new ArrayList<>();
		List<Change> altar = new ArrayList<>();
		transient List<Plan> plan = new ArrayList<>();
		transient int cursor, t, idle;
		transient Set<Long> keys = new HashSet<>();
		/** 소환 연출용 엔드 수정 (저장 안 함, 엔티티도 비영속) */
		transient org.bukkit.entity.EnderCrystal crystal;
		transient Ring ring;
		/** t389: 번개가 떨어질 문양 칸 {dx, dz} (메달 8개 + 금 점선 고리 + 남색 띠, 입구 창 제외). 위치에서 다시 계산 → 저장 안 함 */
		transient List<int[]> patternCells;
		List<int[]> patternCells() { if (patternCells == null) patternCells = StormArena.patternCells(ring()); return patternCells; }
		Ring ring() { if (ring == null) ring = new Ring(cx, cz); return ring; }
		World w() { return Bukkit.getWorld(world); }
		ArenaRef ref() { World w = w(); return w == null ? null : new ArenaRef(Site.STORM, w, cx, cy, cz); }
	}

	private static final List<Arena> ARENAS = new ArrayList<>();

	private StormArena() {}

	public static void init(HobbangPlugin plugin) {
		Bukkit.getPluginManager().registerEvents(new StormArena(), plugin);
		load();
		Bukkit.getScheduler().runTaskTimer(plugin, StormArena::tick, 1, 1);
	}

	public static boolean busy() { return !ARENAS.isEmpty(); }

	/** 건설/카운트다운/복구 중인 투기장 (보스가 아직/이미 없을 때도 보호) */
	public static List<ArenaRef> zones() {
		if (ARENAS.isEmpty()) return List.of();
		List<ArenaRef> out = new ArrayList<>();
		for (Arena a : ARENAS) { ArenaRef r = a.ref(); if (r != null) out.add(r); }
		return out;
	}

	public static boolean owns(Block b) {
		if (ARENAS.isEmpty()) return false;
		long k = Block.getBlockKey(b.getX(), b.getY(), b.getZ());
		for (Arena a : ARENAS) if (a.keys.contains(k) && b.getWorld().getName().equals(a.world)) return true;
		return false;
	}

	// ───────── 제단 패턴 ─────────
	static boolean copperBlock(Material m) { return m.name().matches("(WAXED_)?((EXPOSED|WEATHERED|OXIDIZED)_)?COPPER(_BLOCK)?") && m != Material.COPPER_ORE; }
	static boolean cutCopper(Material m) { return m.name().matches("(WAXED_)?((EXPOSED|WEATHERED|OXIDIZED)_)?CUT_COPPER"); }
	static boolean rod(Material m) { return m.name().endsWith("LIGHTNING_ROD"); }
	static boolean copperAny(Material m) { return copperBlock(m) || cutCopper(m); }
	static boolean pillar(Material m) {
		String n = m.name();
		if (!n.contains("DEEPSLATE") || n.contains("ORE") || n.contains("COPPER")) return false;
		return !n.contains("SLAB") && !n.contains("STAIR") && !n.contains("WALL") && !n.contains("FENCE")
			&& !n.contains("SIGN") && !n.contains("BUTTON") && !n.contains("PRESSURE")
			&& !n.contains("DOOR") && !n.contains("GATE") && !n.contains("CHEST");
	}
	static boolean candle(Material m) {
		String n = m.name();
		return n.endsWith("CANDLE") && !n.contains("CAKE");
	}

	/** 문제가 없으면 null, 있으면 어디가 틀렸는지 */
	static String check(Block c) {
		if (!copperBlock(c.getType())) return "가운데 블록이 구리 블록이 아닙니다";
		if (!rod(c.getRelative(0, 1, 0).getType())) return "가운데 구리 블록 위에 피뢰침이 없습니다";
		int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] a : arms)
			if (!copperAny(c.getRelative(a[0], 0, a[1]).getType()))
				return "가운데 구리의 동서남북 4칸은 구리 블록이어야 합니다";
		int[][] corners = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
		for (int[] a : corners)
			if (c.getRelative(a[0], 0, a[1]).getType() != Material.DIAMOND_BLOCK)
				return "구리 십자 안쪽 모서리(대각선 1칸)는 다이아몬드 블록이어야 합니다";
		int[][] tips = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
		for (int[] a : tips)
			if (c.getRelative(a[0], 0, a[1]).getType() != Material.DIAMOND_BLOCK)
				return "십자 끝(동서남북 2칸)은 다이아몬드 블록이어야 합니다";
		for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4) {
			if (!pillar(c.getRelative(sx, 0, sz).getType()))
				return "네 모서리(대각선 2칸)에 심층암(아무 종류)을 놓고 그 위에 촛불을 올려야 합니다";
			if (!candle(c.getRelative(sx, 1, sz).getType()))
				return "네 모서리 심층암 위에 촛불(아무 색)이 없습니다";
		}
		return null;
	}

	static Block centerFor(Block clicked) {
		if (rod(clicked.getType()) && copperBlock(clicked.getRelative(0, -1, 0).getType())) return clicked.getRelative(0, -1, 0);
		if (copperBlock(clicked.getType()) && rod(clicked.getRelative(0, 1, 0).getType())) return clicked;
		return null;
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onInteract(PlayerInteractEvent e) {
		if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
		ItemStack held = e.getPlayer().getInventory().getItemInMainHand();
		if (!HItem.STORM_CORE.is(held)) return;
		Block c = centerFor(e.getClickedBlock());
		if (c == null) return;
		e.setCancelled(true);
		if (e.getHand() != EquipmentSlot.HAND) return;
		tryStart(e.getPlayer(), c, held);
	}

	static void tryStart(Player p, Block c, ItemStack core) {
		String bad = check(c);
		if (bad != null) {
			p.sendMessage(Component.text("[홉방] 폭풍 제단이 완성되지 않았습니다: " + bad, NamedTextColor.RED));
			p.sendMessage(Component.text(" (가이드북 '폭풍 제단' 쪽 / 소환대 → 폭풍의 와이번 참고)", NamedTextColor.GRAY));
			return;
		}
		World w = c.getWorld();
		if (w.getEnvironment() != World.Environment.NORMAL || !w.isThundering() || !w.hasStorm()) {
			p.sendMessage(Component.text("[홉방] 하늘이 조용합니다… 오버월드에 천둥번개가 칠 때만 와이번을 부를 수 있습니다.", NamedTextColor.AQUA));
			return;
		}
		for (Arena a : ARENAS) if (a.world.equals(w.getName()) && Math.abs(a.cx - c.getX()) < 2 * CARVE_R && Math.abs(a.cz - c.getZ()) < 2 * CARVE_R) {
			p.sendMessage(Component.text("[홉방] 근처에 이미 폭풍 투기장이 있습니다!", NamedTextColor.RED));
			return;
		}
		if (BossManager.bossNear(c.getLocation(), 64) || Ritual.active(c)) {
			p.sendMessage(Component.text("[홉방] 근처에 이미 보스가 있습니다! 먼저 처치하세요.", NamedTextColor.RED));
			return;
		}
		if (c.getY() + Site.STORM.yHi + 2 > w.getMaxHeight()) {   // t385: 고리 윗면 +17, 투기장 높이 +28
			p.sendMessage(Component.text("[홉방] 너무 높은 곳입니다.", NamedTextColor.RED));
			return;
		}
		Arena a = new Arena();
		a.world = w.getName(); a.cx = c.getX(); a.cy = c.getY(); a.cz = c.getZ();
		if (p.getGameMode() != GameMode.CREATIVE) core.setAmount(core.getAmount() - 1);
		transformAltar(a, w);
		a.plan = carvePlan(a, w);
		a.state = State.CARVING;
		a.cursor = 0;
		ARENAS.add(a);
		save();
		BU_sound(c.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 3.0F, 0.6F);
		w.strikeLightningEffect(c.getLocation().add(0.5, 1, 0.5));
		Bukkit.broadcast(Component.text("⚡ ", NamedTextColor.AQUA).append(p.displayName())
			.append(Component.text("님이 폭풍 제단을 깨웠습니다! 하늘에 투기장이 솟아오릅니다…", NamedTextColor.AQUA, TextDecoration.BOLD)));
	}

	static boolean free(Block b) {
		Material m = b.getType();
		return m.isAir() || (b.isReplaceable() && !b.isLiquid() && m != Material.WATER && m != Material.LAVA);
	}

	/** 잘라내면 안 되는 블록: 기반암·장벽·포탈·명령·상자류 */
	static boolean keepBlock(Block b) {
		Material m = b.getType();
		if (m.isAir()) return true;
		if (m == Material.BEDROCK || m == Material.BARRIER || m == Material.STRUCTURE_VOID
			|| m == Material.STRUCTURE_BLOCK || m == Material.JIGSAW
			|| m == Material.COMMAND_BLOCK || m == Material.CHAIN_COMMAND_BLOCK || m == Material.REPEATING_COMMAND_BLOCK
			|| m == Material.END_PORTAL || m == Material.END_PORTAL_FRAME || m == Material.END_GATEWAY
			|| m == Material.MOVING_PISTON) return true;
		String n = m.name();
		if (n.contains("SPAWNER") || m == Material.VAULT || m == Material.BEACON || m == Material.LECTERN) return true;
		if (m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.ENDER_CHEST || m == Material.BARREL
			|| n.endsWith("SHULKER_BOX") || m == Material.HOPPER || m == Material.FURNACE
			|| m == Material.BLAST_FURNACE || m == Material.SMOKER || m == Material.BREWING_STAND
			|| m == Material.DISPENSER || m == Material.DROPPER || m == Material.CRAFTER || m == Material.DECORATED_POT)
			return true;
		return false;
	}

	/** 아레나보다 조금 큰 원기둥 안에서, 제단·보호 블록을 뺀 지형을 위에서부터 공기로.
	 *  제단 층(가운데 구리 y = cy) 이하는 절대 파지 않는다 → 절삭은 cy+1 부터 위로만. */
	private static List<Plan> carvePlan(Arena a, World w) {
		List<Plan> out = new ArrayList<>();
		int r = CARVE_R;   // t385: 바깥 고리까지 (예전 half 20 + 4)
		int y0 = Math.max(w.getMinHeight(), a.cy + 1);   // 제단 y 바로 위부터 (예전: cy + yLo - 2 = cy-5 까지 파 내려갔음)
		int y1 = Math.min(w.getMaxHeight() - 1, a.cy + Site.STORM.yHi + CLEAR_PAD);
		int r2 = r * r;
		for (int y = y1; y >= y0; y--) {
			for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r2) continue;
				int dy = y - a.cy;
				if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && dy >= 0 && dy <= 2) continue;   // 제단은 별도 연출
				Block b = w.getBlockAt(a.cx + dx, y, a.cz + dz);
				if (keepBlock(b) || b.getType().isAir()) continue;
				out.add(new Plan(b.getX(), b.getY(), b.getZ(), Material.AIR.createBlockData(), true));
			}
		}
		return out;
	}

	private static void BU_sound(Location l, Sound s, float v, float pitch) {
		l.getWorld().playSound(l, s, SoundCategory.BLOCKS, v, pitch);
	}

	/** 제단 변신: 모서리 촛불을 켠다. (아레나가 뜰 때 제단이 사라지는 연출은 별도) */
	private static void transformAltar(Arena a, World w) {
		Block c = w.getBlockAt(a.cx, a.cy, a.cz);
		for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4) {
			Block top = c.getRelative(sx, 1, sz);
			if (top.getBlockData() instanceof org.bukkit.block.data.type.Candle cd) {
				cd.setLit(true);
				swap(a, top, cd, true);
			}
		}
	}

	/** 제단 철거 계획: 5×5 안에서 제단을 이루는 블록(구리·다이아몬드·광택 심층암·촛불·피뢰침)만, 위에서 아래로.
	 *  제단 블록은 전부 공기가 된다(아래 땅을 끌어올리지 않음 — 제단 y 아래는 건드리지 않는다). 대신 바로 아래 cy-1 에 5×5 발판(padPlan).
	 *  전투가 끝나 투기장이 무너질 때 제단도 그대로 복구된다 */
	private static List<Plan> altarRemoval(World w, Block c) {
		List<Plan> out = new ArrayList<>();
		for (int dy = 2; dy >= 0; dy--) {
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
				Block b = c.getRelative(dx, dy, dz);
				Material m = b.getType();
				boolean altarBlock = copperAny(m) || rod(m) || candle(m) || m == Material.DIAMOND_BLOCK || pillar(m);
				if (!altarBlock) continue;
				out.add(new Plan(b.getX(), b.getY(), b.getZ(), Material.AIR.createBlockData(), true));
			}
		}
		return out;
	}

	/** 제단 y(다이아몬드 층) 1칸 아래 cy-1 에 5×5 발판 — 투기장 작업 중 제단 y 아래를 바꾸는 유일한 예외.
	 *  원래 블록은 a.altar 에 기록돼 투기장이 무너질 때 되돌린다. 보호 블록(상자·기반암 등)은 건너뛴다. */
	private static List<Plan> padPlan(Arena a) {
		List<Plan> out = new ArrayList<>();
		BlockData pad = Material.POLISHED_DEEPSLATE.createBlockData(), rim = Material.DEEPSLATE_TILES.createBlockData();
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
			out.add(new Plan(a.cx + dx, a.cy - 1, a.cz + dz, (Math.abs(dx) == 2 || Math.abs(dz) == 2) ? rim : pad, true));
		return out;
	}

	private static void swap(Arena a, Block b, BlockData now, boolean altar) {
		Change ch = new Change(b.getX(), b.getY(), b.getZ(), b.getBlockData().getAsString(), now.getAsString());
		b.setBlockData(now, false);
		(altar ? a.altar : a.placed).add(ch);
		a.keys.add(Block.getBlockKey(b.getX(), b.getY(), b.getZ()));
	}

	/** 떠 있는 발판 투기장 설계 (제단 기준): 가운데 원반(반지름 6.5, +9) + 아래 뿔, 위성 발판 5개, 나선 디딤돌 9개.
	 *  v1.4.1: 블록 섞기(금 간 타일·심층암 벽돌·산화 단계가 다른 구리), 테두리 구리 창살, 위성 발판 구리 랜턴·피뢰침,
	 *  원반 아래 사슬+랜턴. 위치별 고정 시드라 매번 같은 모양 (블록 수 ≈ +40, 비용 무시 가능). */
	private static List<Plan> plan(Arena a) {
		List<Plan> out = new ArrayList<>();
		Random r0 = new Random(a.cx * 341873128712L + a.cz * 132897987541L);
		BlockData tiles = Material.DEEPSLATE_TILES.createBlockData(), ctiles = Material.CRACKED_DEEPSLATE_TILES.createBlockData(),
			bricks = Material.DEEPSLATE_BRICKS.createBlockData(), pol = Material.POLISHED_DEEPSLATE.createBlockData(),
			cob = Material.COBBLED_DEEPSLATE.createBlockData(), tuff = Material.TUFF.createBlockData(), calc = Material.CALCITE.createBlockData(),
			glass = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData(), ptuff = Material.POLISHED_TUFF.createBlockData(),
			ctuff = Material.CHISELED_TUFF_BRICKS.createBlockData(), grate = Material.WAXED_OXIDIZED_COPPER_GRATE.createBlockData(),
			rodUp = Bukkit.createBlockData("minecraft:waxed_lightning_rod[facing=up]"), rodOld = Bukkit.createBlockData("minecraft:end_rod[facing=up]");
		BlockData[] rims = {Material.WAXED_OXIDIZED_CUT_COPPER.createBlockData(), Material.WAXED_WEATHERED_CUT_COPPER.createBlockData(),
			Material.WAXED_EXPOSED_CUT_COPPER.createBlockData(), Material.WAXED_OXIDIZED_CHISELED_COPPER.createBlockData()};
		BlockData lantern = Bukkit.createBlockData("minecraft:waxed_oxidized_copper_lantern[hanging=false]"),
			hangL = Bukkit.createBlockData("minecraft:waxed_oxidized_copper_lantern[hanging=true]"),
			chain = Bukkit.createBlockData("minecraft:waxed_oxidized_copper_chain[axis=y]");
		int cx = a.cx, cy = a.cy, cz = a.cz;
		// 가운데 원반: 안쪽부터 바깥으로 (반지름 순)
		List<int[]> disc = new ArrayList<>();
		for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) if (Math.hypot(dx, dz) <= 6.5) disc.add(new int[]{dx, dz});
		disc.sort((p, q) -> Double.compare(Math.hypot(p[0], p[1]) + Math.atan2(p[1], p[0]) * 0.01, Math.hypot(q[0], q[1]) + Math.atan2(q[1], q[0]) * 0.01));
		for (int[] d : disc) {
			double r = Math.hypot(d[0], d[1]);
			BlockData bd = r > 5.4 ? rims[r0.nextInt(10) < 6 ? 0 : 1 + r0.nextInt(3)] : r <= 1.5 ? glass : (r >= 3.5 && r < 4.5) ? (r0.nextInt(5) == 0 ? ctuff : pol)
				: (r0.nextInt(6) == 0 ? ctiles : r0.nextInt(8) == 0 ? bricks : tiles);
			out.add(new Plan(cx + d[0], cy + DISC_Y, cz + d[1], bd));
		}
		for (int[] d : disc) {
			double r = Math.hypot(d[0], d[1]);
			if (r <= 4.6) out.add(new Plan(cx + d[0], cy + DISC_Y - 1, cz + d[1], r0.nextInt(4) == 0 ? tuff : cob));
			else if (r <= 5.6 && r0.nextInt(3) == 0) out.add(new Plan(cx + d[0], cy + DISC_Y - 1, cz + d[1], grate));
		}
		for (int[] d : disc) {
			double r = Math.hypot(d[0], d[1]);
			if (r <= 2.6) out.add(new Plan(cx + d[0], cy + DISC_Y - 2, cz + d[1], r <= 1 ? calc : (r0.nextBoolean() ? cob : tuff)));
		}
		out.add(new Plan(cx, cy + DISC_Y - 3, cz, tuff));
		// 원반 아래로 늘어진 구리 사슬 + 랜턴 4개 (뿔 둘레)
		for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
			int len = 1 + r0.nextInt(2);
			for (int k = 1; k <= len; k++) out.add(new Plan(cx + d[0], cy + DISC_Y - 1 - k, cz + d[1], chain));
			out.add(new Plan(cx + d[0], cy + DISC_Y - 2 - len, cz + d[1], hangL));
		}
		for (int[] d : new int[][]{{6, 0}, {-6, 0}, {0, 6}, {0, -6}}) out.add(new Plan(cx + d[0], cy + DISC_Y + 1, cz + d[1], rodOld));
		for (int[] d : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) out.add(new Plan(cx + d[0], cy + DISC_Y + 1, cz + d[1], lantern));
		// t385: 위성 발판 5개·연결 디딤돌·옛 나선(9칸)은 없앴다 — 가운데 섬과 바깥 고리 사이는 비워 두고(빈 고리) 그 안을 새 나선이 지난다.
		// 가운데 엔드 막대기: 소환 연출의 엔드 수정 빔이 여기로 수직으로 꽂힌다
		out.add(new Plan(cx, cy + DISC_Y + 1, cz, rodOld));
		// 나선 디딤돌 (2×2) 17칸: 발판(5×5, cy-1) 서쪽 가장자리에서 2칸 띄고 cy 부터 시작해 칸마다 +1, 반지름 5.5 → 14 로 벌어지며 약 1.2바퀴.
		// 마지막 칸(cy+16)에서 +1 올라 바깥 고리 입구(cy+17)로.
		for (int[] st : SPIRAL) {
			int k = st[2];
			for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++)
				out.add(new Plan(cx + st[0] + dx, cy + k, cz + st[1] + dz, k == SPIRAL_STEPS - 1 ? rims[3] : (dx + dz + k) % 3 == 0 ? bricks : pol));
		}
		out.addAll(ringPlan(a));
		return out;
	}

	/** t385: 바깥 섬 고리 (윗면 cy+17, 반지름 ≈16 → ≈30.5, 폭 ≈ 14). 입구 쪽부터 각도 순으로 한 기둥씩 (윗면 → 아래) 지어진다.
	 *  윗면: 심층암 타일 바탕 + 안쪽 남색 띠(r 17.6~18.4) + 금 점선 고리(r 20.6~21.5) + 번개 문양 메달 8개(r 25.5, 45° 간격, 남색 바탕·금 번개·청록 구리 테두리)
	 *        + 바깥 가장자리 산화 구리 테. 아랫면: 가운데가 두껍게 처진 렌즈 모양(최대 ≈ 5~7칸) → 떠 있는 섬.
	 *  장식: 바깥 테 위 피뢰침/구리 랜턴 번갈아 15° 마다, 입구 양옆 피뢰침. */
	private static List<Plan> ringPlan(Arena a) {
		Ring ring = a.ring();
		Random rnd = new Random(a.cx * 49979687L + a.cz * 87178291199L);
		int cx = a.cx, cz = a.cz, top = a.cy + RING_TOP;
		BlockData tiles = Material.DEEPSLATE_TILES.createBlockData(), ctiles = Material.CRACKED_DEEPSLATE_TILES.createBlockData(),
			bricks = Material.DEEPSLATE_BRICKS.createBlockData(), pol = Material.POLISHED_DEEPSLATE.createBlockData(),
			chis = Material.CHISELED_DEEPSLATE.createBlockData(), cob = Material.COBBLED_DEEPSLATE.createBlockData(),
			deep = Material.DEEPSLATE.createBlockData(), tuff = Material.TUFF.createBlockData(), calc = Material.CALCITE.createBlockData(),
			navy = Material.BLUE_TERRACOTTA.createBlockData(), gold = Material.GOLD_BLOCK.createBlockData(), rawGold = Material.RAW_GOLD_BLOCK.createBlockData(),
			teal = Material.WAXED_OXIDIZED_CUT_COPPER.createBlockData(), chisCu = Material.WAXED_OXIDIZED_CHISELED_COPPER.createBlockData(),
			grate = Material.WAXED_OXIDIZED_COPPER_GRATE.createBlockData(),
			rodUp = Bukkit.createBlockData("minecraft:waxed_lightning_rod[facing=up]"),
			lantern = Bukkit.createBlockData("minecraft:waxed_oxidized_copper_lantern[hanging=false]");
		BlockData[] rims = {teal, Material.WAXED_WEATHERED_CUT_COPPER.createBlockData(), Material.WAXED_EXPOSED_CUT_COPPER.createBlockData(), chisCu};
		int R = (int) Math.ceil(RING_OUT + 1.3);
		List<int[]> cells = new ArrayList<>();
		for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
			double r = Math.hypot(dx, dz), th = Math.atan2(dz, dx);
			if (r >= ring.rin(th) && r <= ring.rout(th)) cells.add(new int[]{dx, dz});
		}
		// 입구에서 시작해 한 바퀴 돌며 지어진다
		cells.sort((p, q) -> Double.compare(ang0(Math.atan2(p[1], p[0])), ang0(Math.atan2(q[1], q[0]))));
		List<Plan> out = new ArrayList<>();
		for (int[] c : cells) {
			double r = Math.hypot(c[0], c[1]), th = Math.atan2(c[1], c[0]);
			double rin = ring.rin(th), rout = ring.rout(th);
			// ── 윗면 ──
			BlockData face;
			BlockData emb = emblem(r, th, navy, gold, teal);
			if (emb != null) face = emb;
			else if (r > rout - 1.2) face = rims[rnd.nextInt(10) < 6 ? 0 : 1 + rnd.nextInt(3)];   // 바깥 테
			else if (r < rin + 0.9) face = rnd.nextInt(4) == 0 ? chis : pol;                     // 안쪽 테
			else if (r >= 17.6 && r < 18.4) face = navy;                                         // 남색 띠
			else if (r >= 20.6 && r < 21.5) face = ((int) Math.floor(ang0(th) * 21.0)) % 5 == 0 ? chisCu : (rnd.nextInt(9) == 0 ? rawGold : gold);   // 금 점선 고리
			else face = rnd.nextInt(8) == 0 ? ctiles : rnd.nextInt(10) == 0 ? bricks : rnd.nextInt(12) == 0 ? pol : tiles;
			out.add(new Plan(cx + c[0], top, cz + c[1], face));
			// ── 아랫면 (떠 있는 섬) ──
			int d = ring.depth(r, th, rnd);
			for (int L = 1; L < d; L++) {
				BlockData bd;
				if (L == 1) bd = r > rout - 1.5 && rnd.nextInt(3) == 0 ? grate : (rnd.nextInt(4) == 0 ? tuff : cob);
				else if (L == d - 1 && d >= 3) bd = rnd.nextInt(3) == 0 ? calc : tuff;
				else bd = rnd.nextInt(2) == 0 ? deep : (rnd.nextBoolean() ? tuff : cob);
				out.add(new Plan(cx + c[0], top - L, cz + c[1], bd));
			}
		}
		// ── 장식: 바깥 테 위 피뢰침/랜턴 (15° 마다 번갈아), 입구 양옆 피뢰침 ──
		for (int i = 0; i < 24; i++) {
			double th = Math.toRadians(i * 15 + 7.5);
			double r = ring.rout(th) - 0.8;
			int dx = (int) Math.floor(r * Math.cos(th) + 0.5), dz = (int) Math.floor(r * Math.sin(th) + 0.5);
			if (Math.hypot(dx, dz) > ring.rout(Math.atan2(dz, dx))) continue;
			out.add(new Plan(cx + dx, top + 1, cz + dz, i % 2 == 0 ? rodUp : lantern));
		}
		for (int s = -1; s <= 1; s += 2) {
			double th = ENTRANCE + s * (ENTRANCE_HALF + Math.toRadians(2));
			double r = ENTRANCE_IN + 1.0;
			out.add(new Plan(cx + (int) Math.floor(r * Math.cos(th) + 0.5), top + 1, cz + (int) Math.floor(r * Math.sin(th) + 0.5), rodUp));
		}
		return out;
	}

	/** 입구 각도에서 잰 0..2π 각도 (고리 건설 순서용) */
	private static double ang0(double th) {
		double d = (th - ENTRANCE) % (2 * Math.PI);
		return d < 0 ? d + 2 * Math.PI : d;
	}

	/** 번개 문양 메달 (r 25.5, 45° 간격 8개, 22.5° 에서 시작): 금 번개 / 남색 바탕 / 청록 구리 테두리. 메달 밖이면 null */
	private static BlockData emblem(double r, double th, BlockData navy, BlockData gold, BlockData teal) {
		double step = Math.PI / 4, off = Math.PI / 8;
		double c = Math.round((th - off) / step) * step + off;
		double u = r - EMBLEM_R, v = r * wrapRad(th - c);
		double e = (u / PLATE_U) * (u / PLATE_U) + (v / PLATE_V) * (v / PLATE_V);
		if (e > 1.35) return null;
		if (e > 1.0) return teal;
		int iu = (int) Math.round(u), iv = (int) Math.round(v);
		if (Math.abs(iu) <= 3 && Math.abs(iv) <= 2 && BOLT[3 - iu].charAt(iv + 2) == '#') return gold;
		return navy;
	}

	/** t389: ringPlan 과 같은 우선순위로 윗면이 문양(메달·금 점선 고리·남색 띠)인 칸만. 바깥/안쪽 테로 덮인 칸은 제외 */
	static List<int[]> patternCells(Ring ring) {
		List<int[]> out = new ArrayList<>();
		BlockData m = Material.STONE.createBlockData();   // 메달 판정용 표식 (모양만 본다)
		int R = (int) Math.ceil(RING_OUT + 1.3);
		for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
			double r = Math.hypot(dx, dz), th = Math.atan2(dz, dx);
			double rin = ring.rin(th), rout = ring.rout(th);
			if (r < rin || r > rout) continue;
			if (Math.abs(wrapRad(th - ENTRANCE)) < LIGHTNING_GAP_HALF) continue;   // 점프맵 입구 쪽은 비운다
			boolean pat;
			if (emblem(r, th, m, m, m) != null) pat = true;
			else if (r > rout - 1.2 || r < rin + 0.9) pat = false;
			else pat = (r >= 17.6 && r < 18.4) || (r >= 20.6 && r < 21.5);
			if (pat) out.add(new int[]{dx, dz});
		}
		return out;
	}

	// ───────── 틱 ─────────
	private static void tick() {
		if (ARENAS.isEmpty()) return;
		boolean dirty = false;
		for (Arena a : new ArrayList<>(ARENAS)) {
			World w = a.w();
			if (w == null) continue;
			a.t++;
			switch (a.state) {
				case CARVING -> {
					if (!w.isChunkLoaded(a.cx >> 4, a.cz >> 4)) break;
					for (int n = 0; n < CLEAR_PER_TICK && a.cursor < a.plan.size(); n++) {
						Plan pl = a.plan.get(a.cursor++);
						if (!w.isChunkLoaded(pl.x >> 4, pl.z >> 4)) continue;
						Block b = w.getBlockAt(pl.x, pl.y, pl.z);
						if (b.getType().isAir() || keepBlock(b)) continue;
						if (n % 3 == 0) w.spawnParticle(Particle.BLOCK, pl.x + 0.5, pl.y + 0.5, pl.z + 0.5, 10, 0.35, 0.35, 0.35, 0.05, b.getBlockData());
						if (n == 0 && a.t % 2 == 0) w.playSound(b.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, SoundCategory.BLOCKS, 0.7F, 0.7F + RNG.nextFloat() * 0.3F);
						swap(a, b, pl.data, false);   // placed: 전투 후 지형 복구
					}
					if (a.cursor >= a.plan.size()) {
						a.plan = plan(a);
						a.plan.addAll(0, padPlan(a));
						a.plan.addAll(0, altarRemoval(w, w.getBlockAt(a.cx, a.cy, a.cz)));   // 순서: 제단 철거 → cy-1 발판 → 투기장
						a.cursor = 0;
						a.state = State.BUILDING;
						a.t = 0;
						dirty = true;
					}
				}
				case BUILDING -> {
					if (!w.isChunkLoaded(a.cx >> 4, a.cz >> 4)) break;
					for (int n = 0; n < BUILD_PER_TICK && a.cursor < a.plan.size(); n++) {
						Plan pl = a.plan.get(a.cursor++);
						if (!w.isChunkLoaded(pl.x >> 4, pl.z >> 4)) continue;
						Block b = w.getBlockAt(pl.x, pl.y, pl.z);
						if (pl.remove) {   // 제단 블록: 번개 불꽃과 함께 사라진다
							if (b.getType().isAir() && pl.data.getMaterial().isAir()) continue;
							if (!b.getType().isAir() && keepBlock(b)) continue;   // 상자·기반암 등은 발판으로도 덮지 않는다
							if (b.getBlockData().matches(pl.data)) continue;
							if (!b.getType().isAir()) w.spawnParticle(Particle.BLOCK, pl.x + 0.5, pl.y + 0.5, pl.z + 0.5, 8, 0.3, 0.3, 0.3, 0.05, b.getBlockData());
							w.spawnParticle(Particle.ELECTRIC_SPARK, pl.x + 0.5, pl.y + 1.0, pl.z + 0.5, 4, 0.3, 0.3, 0.3, 0.1);
							if (n == 0) w.playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.BLOCKS, 1.0F, 0.6F);
							swap(a, b, pl.data, true);
							continue;
						}
						if (!free(b)) continue;   // 플레이어 건물·지형은 건드리지 않는다
						swap(a, b, pl.data, false);
						if (n % 4 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, pl.x + 0.5, pl.y + 1.0, pl.z + 0.5, 3, 0.3, 0.2, 0.3, 0.05);   // t385: 틱당 40개라 파티클은 4개 중 1개만
						if (n == 0 && a.t % 3 == 0) w.playSound(b.getLocation(), Sound.BLOCK_COPPER_PLACE, SoundCategory.BLOCKS, 1.0F, 0.6F + RNG.nextFloat() * 0.6F);
					}
					if (a.t % 12 == 0 && a.cursor > 0) {
						Plan pl = a.plan.get(Math.max(0, a.cursor - 1));
						w.strikeLightningEffect(new Location(w, pl.x + 0.5, pl.y + 1, pl.z + 0.5));
					}
					if (a.t % 4 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, a.cx + 0.5, a.cy + 2, a.cz + 0.5, 6, 0.2, 3.0, 0.2, 0.1);
					if (a.cursor >= a.plan.size()) {
						a.plan = new ArrayList<>();
						// t385: 바로 소환하지 않는다 → 번개 세례 15초 → 2초 뒤 엔드 수정 빔 → 2초 뒤 와이번 (SUMMONING)
						a.state = State.SUMMONING; a.t = 0; dirty = true;
						w.strikeLightningEffect(new Location(w, a.cx + 0.5, a.cy + DISC_Y + 1, a.cz + 0.5));
						Bukkit.broadcast(Component.text("⚡ 섬의 가장자리에 번개가 쏟아집니다… 폭풍의 와이번이 깨어나려 합니다!", NamedTextColor.AQUA, TextDecoration.BOLD));
					}
				}
				case SUMMONING -> {
					if (!w.isChunkLoaded(a.cx >> 4, a.cz >> 4)) { a.t--; break; }   // 아무도 없으면 연출을 멈춰 둔다
					summonTick(a, w);
					if (a.t >= SPAWN_AT) {
						removeCrystal(a);
						Location spawn = new Location(w, a.cx + Site.STORM.spawnDx, a.cy + Site.STORM.spawnDy, a.cz + Site.STORM.spawnDz);
						org.bukkit.entity.Mob m = BossManager.spawn(BossKind.TEMPEST_WYVERN, spawn);
						if (m == null) { a.state = State.CRUMBLING; a.t = 0; dirty = true; break; }
						ArenaRef ref = a.ref();
						ref.write(m);
						Boss bb = BossManager.get(m);
						if (bb != null) bb.arena = ref;
						a.boss = m.getUniqueId().toString();
						a.state = State.ACTIVE; a.t = 0; dirty = true;
						w.strikeLightningEffect(spawn);
						w.spawnParticle(Particle.EXPLOSION_EMITTER, spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5, 1, 0, 0, 0, 0);
						w.playSound(spawn, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 3.0F, 1.3F);
					}
				}
				case COUNTDOWN -> { if (a.t > 400) { a.state = State.CRUMBLING; a.t = 0; dirty = true; } }
				case ACTIVE -> {
					if (a.t % 20 != 0) break;
					Entity e = a.boss == null ? null : Bukkit.getEntity(UUID.fromString(a.boss));
					if (e != null && !e.isDead()) { a.idle = 0; break; }
					if (!w.isChunkLoaded(a.cx >> 4, a.cz >> 4)) break;   // 아무도 없으면 기다린다 (청크를 억지로 불러오지 않음)
					a.idle += 20;
					if (a.idle >= 1200) { a.state = State.CRUMBLING; a.t = 0; dirty = true; }   // 보스가 60초 넘게 사라짐
				}
				case CRUMBLING -> {
					removeCrystal(a);
					if (!w.isChunkLoaded(a.cx >> 4, a.cz >> 4)) break;
					if (a.t < 40) break;   // 2초 뒤부터 (전리품이 떨어질 시간)
					for (int n = 0; n < CRUMBLE_PER_TICK; n++) {
						List<Change> list = !a.placed.isEmpty() ? a.placed : a.altar;
						if (list.isEmpty()) break;
						Change ch = list.remove(list.size() - 1);
						a.keys.remove(Block.getBlockKey(ch.x, ch.y, ch.z));
						if (!w.isChunkLoaded(ch.x >> 4, ch.z >> 4)) continue;
						Block b = w.getBlockAt(ch.x, ch.y, ch.z);
						BlockData now = Bukkit.createBlockData(ch.now);
						if (b.getType() == now.getMaterial()) {
							if (n % 2 == 0) w.spawnParticle(Particle.BLOCK, ch.x + 0.5, ch.y + 0.5, ch.z + 0.5, 8, 0.3, 0.3, 0.3, 0, now);
							b.setBlockData(Bukkit.createBlockData(ch.old), false);
						}
						if (n == 0 && a.t % 4 == 0) w.playSound(b.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, SoundCategory.BLOCKS, 1.0F, 0.7F);
					}
					if (a.placed.isEmpty() && a.altar.isEmpty()) { ARENAS.remove(a); dirty = true; }
					else if (a.t % 100 == 0) dirty = true;
				}
			}
		}
		if (dirty) save();
	}

	/** t385 소환 연출: 0~15초 바깥 고리 가장자리(입구 쪽 제외)에 번개 세례(시각 효과만) → 17초 엔드 수정 빔이 가운데 엔드 막대기로 수직으로 → 19초 와이번 */
	private static void summonTick(Arena a, World w) {
		Ring ring = a.ring();
		double top = a.cy + RING_TOP + 1;
		if (a.t <= STORM_TICKS) {
			int n = 1 + (RNG.nextBoolean() ? 1 : 0) + (a.t > STORM_TICKS / 2 && RNG.nextInt(3) == 0 ? 1 : 0);
			for (int i = 0; i < n; i++) {
				// t389: 바깥 가장자리 대신 문양 칸(메달·금 고리·남색 띠) 위에서만 친다
				List<int[]> pc = a.patternCells();
				if (pc.isEmpty()) break;
				int[] cell = pc.get(RNG.nextInt(pc.size()));
				w.strikeLightningEffect(new Location(w, a.cx + cell[0] + 0.5, top, a.cz + cell[1] + 0.5));   // 피해·불 없음
			}
			if (a.t % 4 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, a.cx + 0.5, a.cy + DISC_Y + 2, a.cz + 0.5, 10, 0.4, 1.2, 0.4, 0.1);
			if (a.t % 20 == 0) {
				int left = (SPAWN_AT - a.t) / 20;
				Component bar = Component.text("⚡ 폭풍이 모여든다… " + left, NamedTextColor.AQUA, TextDecoration.BOLD);
				Location c = new Location(w, a.cx + 0.5, top, a.cz + 0.5);
				for (Player sp : w.getPlayers()) if (sp.getLocation().distanceSquared(c) < 64 * 64) sp.sendActionBar(bar);
			}
			return;
		}
		Location rod = new Location(w, a.cx + 0.5, a.cy + DISC_Y + 1, a.cz + 0.5);
		if (a.t == BEAM_AT) {
			removeCrystal(a);
			a.crystal = w.spawn(rod.clone().add(0, 1.5, 0), org.bukkit.entity.EnderCrystal.class, ec -> {
				ec.setShowingBottom(false);
				ec.setInvulnerable(true);
				ec.setPersistent(false);   // 서버가 꺼져도 저장되지 않는다
				ec.setGlowing(true);
				ec.setBeamTarget(new Location(w, a.cx, a.cy + DISC_Y + 1, a.cz));
			});
			w.playSound(rod, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 3.0F, 0.6F);
			w.playSound(rod, Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1.2F, 1.4F);
		}
		if (a.t >= BEAM_AT && a.crystal != null && a.crystal.isValid()) {
			// 0.5초 동안 수정이 위로 솟으며 빔이 쭉 뻗는다 (빔 = 수정 → 엔드 막대기, 수직)
			double rise = Math.min(1.0, (a.t - BEAM_AT) / 10.0) * 48 + 1.5;
			Location at = rod.clone().add(0, rise, 0);
			if (a.crystal.getLocation().getY() < at.getY() - 0.05) a.crystal.teleport(at);
			if (a.t % 2 == 0) for (double y = 0.5; y < rise; y += 1.5)
				w.spawnParticle(Particle.END_ROD, rod.getX(), rod.getY() + y, rod.getZ(), 1, 0.08, 0.3, 0.08, 0.01);
		}
	}

	private static void removeCrystal(Arena a) {
		if (a.crystal != null) { if (a.crystal.isValid()) a.crystal.remove(); a.crystal = null; }
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(EntityDeathEvent e) {
		if (ARENAS.isEmpty()) return;
		String id = e.getEntity().getUniqueId().toString();
		for (Arena a : ARENAS) if (id.equals(a.boss) && a.state == State.ACTIVE) {
			a.state = State.CRUMBLING; a.t = 0;
			World w = a.w();
			if (w != null) Bukkit.broadcast(Component.text("⚡ 폭풍이 걷히며 하늘의 투기장이 무너집니다…", NamedTextColor.AQUA));
			save();
		}
	}

	/** 관리자: 진행 중인 폭풍 투기장을 즉시 무너뜨림 */
	public static int crumbleAll() {
		int n = 0;
		for (Arena a : ARENAS) if (a.state != State.CRUMBLING) { a.state = State.CRUMBLING; a.t = 40; n++; }
		save();
		return n;
	}

	public static int count() { return ARENAS.size(); }

	// ───────── 저장 (서버가 꺼져도 복구 정보가 남도록) ─────────
	private static File file() { return new File(HobbangPlugin.get().getDataFolder(), "arenas.json"); }

	// 틱 도중 저장은 JSON 문자열만 메인 스레드에서 만들고, 디스크 쓰기는 비동기 단일 스레드로 넘긴다 (1.4.1: 붕괴 중 26ms 스파이크 제거)
	private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Hobbang-ArenaIO"); t.setDaemon(true); return t; });
	private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();

	public static void save() { save(false); }

	public static void saveSync() { save(true); }

	private static void save(boolean sync) {
		final File f = file();
		final String json = ARENAS.isEmpty() ? null : GSON.toJson(ARENAS.toArray(new Arena[0]));
		final long id = SEQ.incrementAndGet();
		Runnable job = () -> {
			if (!sync && id != SEQ.get()) return;   // 더 최신 저장이 대기 중이면 건너뜀
			try {
				f.getParentFile().mkdirs();
				if (json == null) { if (f.exists()) f.delete(); return; }
				File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
				try (Writer wr = new FileWriter(tmp, java.nio.charset.StandardCharsets.UTF_8)) { wr.write(json); }
				java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			} catch (Exception ex) {
				HobbangPlugin.get().getLogger().warning("arenas.json 저장 실패: " + ex);
			}
		};
		if (sync) {
			IO.shutdown();
			try { IO.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
			job.run();
		} else if (!IO.isShutdown()) IO.execute(job);
		else job.run();
	}

	private static void load() {
		File f = file();
		if (!f.exists()) return;
		try (Reader r = new FileReader(f, java.nio.charset.StandardCharsets.UTF_8)) {
			Arena[] arr = GSON.fromJson(r, Arena[].class);
			if (arr == null) return;
			for (Arena a : arr) {
				if (a.placed == null) a.placed = new ArrayList<>();
				if (a.altar == null) a.altar = new ArrayList<>();
				a.plan = new ArrayList<>(); a.keys = new HashSet<>();
				for (Change c : a.placed) a.keys.add(Block.getBlockKey(c.x, c.y, c.z));
				for (Change c : a.altar) a.keys.add(Block.getBlockKey(c.x, c.y, c.z));
				if (a.state != State.ACTIVE) { a.state = State.CRUMBLING; a.t = 40; }   // 건설/카운트다운 도중 꺼졌으면 정리
				ARENAS.add(a);
			}
			HobbangPlugin.get().getLogger().info("폭풍 투기장 " + ARENAS.size() + "곳 복원 정보 불러옴");
		} catch (Exception ex) {
			HobbangPlugin.get().getLogger().warning("arenas.json 읽기 실패: " + ex);
		}
	}
}
