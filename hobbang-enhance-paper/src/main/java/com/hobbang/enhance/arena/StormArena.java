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
 * 제단 (5×5, 2단) — C = 구리 블록(아무 산화/밀랍 단계), 위에 피뢰침
 *   1단(y0):  W . . . W      W = 조약돌 심층암 담장 (2단 기둥: y0, y1)
 *             . . X . .      X = 깎인 구리 (아무 산화/밀랍 단계) ×4
 *             . X C X .      C = 구리 블록, 바로 위(y1) 피뢰침
 *             . . X . .
 *             W . . . W
 * 의식: 천둥번개가 칠 때 폭풍의 핵을 들고 피뢰침(또는 가운데 구리)을 우클릭.
 * → 제단이 변하고(구리 전구 점등·산화), 하늘에 떠 있는 발판 투기장이 한 블록씩 지어진 뒤(번개 연출) 5초 의식 → 와이번.
 * 공기/대체 가능한 블록(풀·눈 등)에만 놓고, 바꾼 블록은 전부 기록 → 전투가 끝나면 무너지며 원래대로 복구 (재시작해도 arenas.json 으로 이어짐).
 * 틱 비용: 건설 5블록/틱, 복구 8블록/틱, 감시 20틱마다 1회.
 */
public final class StormArena implements Listener {
	private static final int BUILD_PER_TICK = 5, CRUMBLE_PER_TICK = 8;
	private static final int DISC_Y = 9;
	private static final Random RNG = new Random();
	private static final Gson GSON = new GsonBuilder().create();

	enum State { BUILDING, COUNTDOWN, ACTIVE, CRUMBLING }

	/** 바뀐 블록 1개: 좌표 + 원래 상태 + 놓은 상태 */
	static final class Change {
		int x, y, z; String old, now;
		Change(int x, int y, int z, String old, String now) { this.x = x; this.y = y; this.z = z; this.old = old; this.now = now; }
	}

	static final class Plan {
		final int x, y, z; final BlockData data;
		Plan(int x, int y, int z, BlockData data) { this.x = x; this.y = y; this.z = z; this.data = data; }
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

	/** 문제가 없으면 null, 있으면 어디가 틀렸는지 */
	static String check(Block c) {
		if (!copperBlock(c.getType())) return "가운데 블록이 구리 블록이 아닙니다";
		if (!rod(c.getRelative(0, 1, 0).getType())) return "가운데 구리 블록 위에 피뢰침이 없습니다";
		int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] a : arms) if (!cutCopper(c.getRelative(a[0], 0, a[1]).getType())) return "가운데 구리의 동서남북 4칸은 깎인 구리여야 합니다";
		for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4)
			for (int dy = 0; dy <= 1; dy++)
				if (c.getRelative(sx, dy, sz).getType() != Material.COBBLED_DEEPSLATE_WALL) return "네 모서리(대각선 2칸)에 조약돌 심층암 담장을 2단으로 쌓아야 합니다";
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
		for (Arena a : ARENAS) if (a.world.equals(w.getName()) && Math.abs(a.cx - c.getX()) < 48 && Math.abs(a.cz - c.getZ()) < 48) {
			p.sendMessage(Component.text("[홉방] 근처에 이미 폭풍 투기장이 있습니다!", NamedTextColor.RED));
			return;
		}
		if (BossManager.bossNear(c.getLocation(), 64) || Ritual.active(c)) {
			p.sendMessage(Component.text("[홉방] 근처에 이미 보스가 있습니다! 먼저 처치하세요.", NamedTextColor.RED));
			return;
		}
		if (c.getY() + DISC_Y + 6 > w.getMaxHeight()) {
			p.sendMessage(Component.text("[홉방] 너무 높은 곳입니다.", NamedTextColor.RED));
			return;
		}
		Arena a = new Arena();
		a.world = w.getName(); a.cx = c.getX(); a.cy = c.getY(); a.cz = c.getZ();
		a.plan = plan(a);
		int blocked = 0;
		for (Plan pl : a.plan) if (!free(w.getBlockAt(pl.x, pl.y, pl.z))) blocked++;
		if (blocked > a.plan.size() * 0.15) {
			p.sendMessage(Component.text("[홉방] 제단 위 하늘이 막혀 있습니다 (반경 20칸, 높이 15칸 안을 비워 주세요).", NamedTextColor.RED));
			return;
		}
		if (p.getGameMode() != GameMode.CREATIVE) core.setAmount(core.getAmount() - 1);
		transformAltar(a, w);
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

	private static void BU_sound(Location l, Sound s, float v, float pitch) {
		l.getWorld().playSound(l, s, SoundCategory.BLOCKS, v, pitch);
	}

	/** 제단 변신: 가운데 → 켜진 구리 전구, 팔 → 산화된 깎인 구리, 담장 위 → 막대기 빛 */
	private static void transformAltar(Arena a, World w) {
		Block c = w.getBlockAt(a.cx, a.cy, a.cz);
		swap(a, c, Bukkit.createBlockData("minecraft:waxed_copper_bulb[lit=true]"), true);
		for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) swap(a, c.getRelative(d[0], 0, d[1]), Material.WAXED_OXIDIZED_CUT_COPPER.createBlockData(), true);
		for (int sx = -2; sx <= 2; sx += 4) for (int sz = -2; sz <= 2; sz += 4) {
			Block top = c.getRelative(sx, 2, sz);
			if (free(top)) swap(a, top, Bukkit.createBlockData("minecraft:end_rod[facing=up]"), true);
		}
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
		// 위성 발판 5개 (반지름 14, 높이 7~11): 가운데 구리 피뢰침 + 랜턴
		int[] hs = {7, 10, 8, 11, 9};
		for (int i = 0; i < 5; i++) {
			double ang = Math.toRadians(i * 72 + 18);
			int sx = cx + (int) Math.round(14 * Math.cos(ang)), sz = cz + (int) Math.round(14 * Math.sin(ang)), sy = cy + hs[i];
			for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
				double r = Math.hypot(dx, dz);
				if (r <= 2.5) out.add(new Plan(sx + dx, sy, sz + dz, r > 1.6 ? rims[r0.nextInt(4)] : (r0.nextInt(3) == 0 ? ctuff : ptuff)));
			}
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (Math.hypot(dx, dz) <= 1.5) out.add(new Plan(sx + dx, sy - 1, sz + dz, r0.nextBoolean() ? cob : tuff));
			out.add(new Plan(sx, sy - 2, sz, tuff));
			out.add(new Plan(sx, sy - 3, sz, calc));
			int[] off = {(i % 2 == 0) ? 1 : -1, (i % 3 == 0) ? 1 : -1};
			out.add(new Plan(sx + off[0], sy + 1, sz + off[1], i % 2 == 0 ? rodUp : lantern));
		}
		// 나선 디딤돌 (2×2) : 땅 높이부터 +8 까지, 반지름 9.5, 24° 간격
		for (int k = 0; k <= 8; k++) {
			double ang = Math.toRadians(200 + k * 24);
			int sx = cx + (int) Math.floor(9.5 * Math.cos(ang)), sz = cz + (int) Math.floor(9.5 * Math.sin(ang));
			for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) out.add(new Plan(sx + dx, cy + k, sz + dz, (dx + dz + k) % 3 == 0 ? bricks : pol));
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
				case BUILDING -> {
					if (!w.isChunkLoaded(a.cx >> 4, a.cz >> 4)) break;
					for (int n = 0; n < BUILD_PER_TICK && a.cursor < a.plan.size(); n++) {
						Plan pl = a.plan.get(a.cursor++);
						if (!w.isChunkLoaded(pl.x >> 4, pl.z >> 4)) continue;
						Block b = w.getBlockAt(pl.x, pl.y, pl.z);
						if (!free(b)) continue;   // 플레이어 건물·지형은 건드리지 않는다
						swap(a, b, pl.data, false);
						w.spawnParticle(Particle.ELECTRIC_SPARK, pl.x + 0.5, pl.y + 1.0, pl.z + 0.5, 3, 0.3, 0.2, 0.3, 0.05);
						if (n == 0 && a.t % 3 == 0) w.playSound(b.getLocation(), Sound.BLOCK_COPPER_PLACE, SoundCategory.BLOCKS, 1.0F, 0.6F + RNG.nextFloat() * 0.6F);
					}
					if (a.t % 12 == 0 && a.cursor > 0) {
						Plan pl = a.plan.get(Math.max(0, a.cursor - 1));
						w.strikeLightningEffect(new Location(w, pl.x + 0.5, pl.y + 1, pl.z + 0.5));
					}
					if (a.t % 4 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, a.cx + 0.5, a.cy + 2, a.cz + 0.5, 6, 0.2, 3.0, 0.2, 0.1);
					if (a.cursor >= a.plan.size()) {
						a.plan = new ArrayList<>();
						a.state = State.COUNTDOWN; a.t = 0; dirty = true;
						w.strikeLightningEffect(new Location(w, a.cx + 0.5, a.cy + DISC_Y + 1, a.cz + 0.5));
						Location spawn = new Location(w, a.cx + Site.STORM.spawnDx, a.cy + Site.STORM.spawnDy, a.cz + Site.STORM.spawnDz);
						ArenaRef ref = a.ref();
						Ritual.startConsumed(w.getBlockAt(a.cx, a.cy + DISC_Y, a.cz), BossKind.TEMPEST_WYVERN, spawn, m -> {
							ref.write(m);
							Boss bb = BossManager.get(m);
							if (bb != null) bb.arena = ref;
							a.boss = m.getUniqueId().toString();
							a.state = State.ACTIVE; a.t = 0;
							save();
						});
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
