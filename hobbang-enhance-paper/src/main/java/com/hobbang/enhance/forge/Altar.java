package com.hobbang.enhance.forge;

import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.Keys;
import com.hobbang.enhance.boss.BossKind;
import com.hobbang.enhance.boss.BossManager;
import com.hobbang.enhance.arena.Site;
import com.hobbang.enhance.enhance.EnhanceLogic;
import com.hobbang.enhance.item.HItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 소환대 (v1.2.0 → v1.4.0 보스 안내판): 바닐라 스컬크 감지체 블록 + 청크 PDC 로 위치 기억 (강화대와 같은 방식).
 * - 진동 감지(BlockReceiveGameEvent)·레드스톤 출력을 막아 일반 스컬크 감지체처럼 동작하지 않는다.
 * - 우클릭: 보스 4종 안내 창. v1.4.0부터 여기서는 소환하지 않는다 — 보스마다 정해진 구조물 제단(와이번은 폭풍 제단)에서만 소환.
 *   보스를 클릭하면 구조물 위치를 찾아 방향·거리·대략 좌표를 알려 주고(나침반도 그쪽을 가리킴), 와이번은 제단 짓는 법을 보여 준다.
 * - 창은 버튼만 있는 서버 전용 인벤토리(아이템을 넣는 칸 없음) → 모든 위 칸 클릭 취소.
 */
public final class Altar implements Listener {
	private static final org.bukkit.NamespacedKey ALTARS = Keys.k("altars");
	public static final int[] BOSS_SLOTS = {10, 12, 14, 16};
	public static final int INFO = 4, CLOSE = 22, SIZE = 27;
	private static final Material[] ICONS = {Material.WITHER_SKELETON_SKULL, Material.AMETHYST_CLUSTER, Material.LIGHTNING_ROD, Material.CLOCK};
	private static final String[][] DESC = {
		{"첫째 시련 · 체력 600", "망치 내려찍기, 화염 나선, 용암 파동,", "운석 단조, 낙뢰 (3페이즈)"},
		{"둘째 시련 · 체력 900", "내려찍기, 프리즘 광선, 십자 광선,", "수정 충격파, 중력장, 수정비"},
		{"셋째 시련 · 체력 700 · 비행", "폭풍 구슬, 급강하, 번개 브레스,", "폭풍 고리, 소용돌이, 낙뢰 폭풍"},
		{"넷째 시련 · 체력 800", "시계침, 시간 정지, 시간 고리,", "잔상 폭발, 자정의 칼날, 시간 역행"}};
	private static final String[] CORE_RECIPE = {
		"우는 흑요석 4 + 빛나는 강화석 4 + 다이아몬드 블록",
		"자수정 블록 4 + 빛나는 강화석 4 + 심연의 정수",
		"팬텀 막 4 + 빛나는 강화석 4 + 프리즘 수정",
		"시계 4 + 빛나는 강화석 4 + 폭풍 깃털"};

	public static void init(HobbangPlugin plugin) {
		Bukkit.getPluginManager().registerEvents(new Altar(), plugin);
	}

	// ───────── 위치 저장 (강화대와 같은 포맷, 키만 다름) ─────────
	private static long pack(Block b) {
		return ((long) (b.getX() & 15) << 60) | ((long) (b.getZ() & 15) << 56) | ((b.getY() + 2048L) & 0xFFFFL);
	}

	private static long[] get(Chunk c) {
		long[] a = c.getPersistentDataContainer().get(ALTARS, PersistentDataType.LONG_ARRAY);
		return a == null ? new long[0] : a;
	}

	private static void set(Chunk c, long[] a) {
		if (a.length == 0) c.getPersistentDataContainer().remove(ALTARS);
		else c.getPersistentDataContainer().set(ALTARS, PersistentDataType.LONG_ARRAY, a);
	}

	public static boolean isAltar(Block b) {
		if (b == null || b.getType() != Material.SCULK_SENSOR) return false;
		long k = pack(b);
		for (long v : get(b.getChunk())) if (v == k) return true;
		return false;
	}

	static void add(Block b) {
		if (isAltar(b)) return;
		long[] a = get(b.getChunk());
		long[] n = java.util.Arrays.copyOf(a, a.length + 1);
		n[a.length] = pack(b);
		set(b.getChunk(), n);
	}

	static void remove(Block b) {
		long k = pack(b);
		set(b.getChunk(), java.util.Arrays.stream(get(b.getChunk())).filter(v -> v != k).toArray());
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onPlace(BlockPlaceEvent e) {
		if (HItem.SUMMON_ALTAR.is(e.getItemInHand())) add(e.getBlockPlaced());
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBreak(BlockBreakEvent e) {
		Block b = e.getBlock();
		if (!isAltar(b)) return;
		remove(b);
		closeAt(b);
		e.setDropItems(false);
		e.setExpToDrop(0);
		if (e.getPlayer().getGameMode() != GameMode.CREATIVE)
			b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), HItem.SUMMON_ALTAR.create());
	}

	/** 진동을 받지 않음 = 스컬크 감지체로서 작동하지 않음 (레드스톤·비명체 연동 없음) */
	@EventHandler(ignoreCancelled = true)
	public void onVibration(BlockReceiveGameEvent e) {
		if (e.getBlock().getType() == Material.SCULK_SENSOR && isAltar(e.getBlock())) e.setCancelled(true);
	}

	@EventHandler
	public void onRedstone(BlockRedstoneEvent e) {
		if (e.getBlock().getType() == Material.SCULK_SENSOR && e.getNewCurrent() > 0 && isAltar(e.getBlock())) e.setNewCurrent(0);
	}

	@EventHandler(ignoreCancelled = true)
	public void onBurn(BlockBurnEvent e) { if (isAltar(e.getBlock())) e.setCancelled(true); }

	@EventHandler(ignoreCancelled = true)
	public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(Altar::isAltar); }

	@EventHandler(ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(Altar::isAltar); }

	@EventHandler(ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent e) { if (e.getBlocks().stream().anyMatch(Altar::isAltar)) e.setCancelled(true); }

	@EventHandler(ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent e) { if (e.getBlocks().stream().anyMatch(Altar::isAltar)) e.setCancelled(true); }

	@EventHandler(ignoreCancelled = true)
	public void onEntityChange(EntityChangeBlockEvent e) { if (isAltar(e.getBlock())) e.setCancelled(true); }

	@EventHandler(priority = EventPriority.LOW)
	public void onInteract(PlayerInteractEvent e) {
		if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		Block b = e.getClickedBlock();
		if (b == null || !isAltar(b)) return;
		if (e.getHand() != EquipmentSlot.HAND) { e.setCancelled(true); return; }
		Player p = e.getPlayer();
		if (p.isSneaking() && !p.getInventory().getItemInMainHand().getType().isAir()) return;   // 웅크리고 블록 설치
		e.setCancelled(true);
		open(p, b);
	}

	// ───────── 소환 창 ─────────
	public static final class Gui implements InventoryHolder {
		final Player player;
		final Block altar;
		final Inventory inv;
		long lastPress = 0;

		Gui(Player p, Block altar) {
			this.player = p;
			this.altar = altar;
			this.inv = Bukkit.createInventory(this, SIZE, Component.text("소환대", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
		}

		@Override public Inventory getInventory() { return inv; }
	}

	private static Component plain(String s, NamedTextColor c) {
		return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
	}

	private static ItemStack icon(Material m, Component name, List<Component> lore) {
		ItemStack s = new ItemStack(m);
		ItemMeta meta = s.getItemMeta();
		meta.displayName(name.decoration(TextDecoration.ITALIC, false));
		meta.lore(lore);
		meta.getPersistentDataContainer().set(Keys.k("gui"), PersistentDataType.BYTE, (byte) 1);
		s.setItemMeta(meta);
		return s;
	}

	public static void open(Player p, Block altar) {
		Gui g = new Gui(p, altar);
		render(g);
		p.openInventory(g.inv);
		p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.8F, 0.8F);
	}

	static void render(Gui g) {
		ItemStack border = icon(Material.BLACK_STAINED_GLASS_PANE, plain(" ", NamedTextColor.GRAY), List.of());
		for (int i = 0; i < SIZE; i++) g.inv.setItem(i, border);
		g.inv.setItem(INFO, icon(Material.SCULK_SENSOR, plain("소환대 — 보스 안내", NamedTextColor.DARK_AQUA), List.of(
			plain("보스는 정해진 제단에서만 소환된다 (v1.4.0).", NamedTextColor.WHITE),
			plain("보스를 클릭하면 제단이 있는 구조물의 방향을 찾아 준다.", NamedTextColor.WHITE),
			plain("구조물은 새로 생성되는 지역에만 나타난다.", NamedTextColor.GRAY))));
		BossKind[] ks = BossKind.values();
		for (int i = 0; i < ks.length && i < BOSS_SLOTS.length; i++) {
			BossKind k = ks[i];
			Site site = Site.of(k);
			int have = EnhanceLogic.count(g.player, k.core);
			List<Component> lore = new ArrayList<>();
			for (String d : DESC[i]) lore.add(plain(d, NamedTextColor.GRAY));
			lore.add(Component.empty());
			lore.add(plain("장소: " + site.title, NamedTextColor.AQUA));
			lore.add(plain(" " + site.where, NamedTextColor.DARK_AQUA));
			lore.add(plain(" " + site.looks, NamedTextColor.DARK_GRAY));
			if (site == Site.STORM) {
				lore.add(plain("소환: 천둥번개 중 폭풍 제단의 피뢰침에 핵 우클릭", NamedTextColor.YELLOW));
				lore.add(plain("클릭: 폭풍 제단 짓는 법 보기", NamedTextColor.GOLD));
			} else {
				lore.add(plain("소환: 구조물 한가운데 제단에 핵 들고 우클릭", NamedTextColor.YELLOW));
				lore.add(plain("클릭: 가장 가까운 " + site.title + " 찾기", NamedTextColor.GOLD));
			}
			lore.add(plain("필요: " + k.core.name + " x1 (보유 " + have + "개)", have > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
			lore.add(plain("핵 조합: " + CORE_RECIPE[i], NamedTextColor.DARK_GRAY));
			ItemStack ic = icon(ICONS[i], plain((i + 1) + ". " + k.name, NamedTextColor.LIGHT_PURPLE), lore);
			if (have > 0) ic.editMeta(m -> m.setEnchantmentGlintOverride(true));
			g.inv.setItem(BOSS_SLOTS[i], ic);
		}
		g.inv.setItem(CLOSE, icon(Material.OAK_DOOR, plain("닫기", NamedTextColor.RED), List.of()));
	}

	static void closeAt(Block b) {
		for (Player p : Bukkit.getOnlinePlayers())
			if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Gui g && g.altar.equals(b)) p.closeInventory();
	}

	private static final java.util.Map<java.util.UUID, Long> LOCATE_CD = new java.util.HashMap<>();
	private static final String[] DIRS = {"남", "남서", "서", "북서", "북", "북동", "동", "남동"};

	/** v1.4.0: 소환 대신 안내 — 구조물 찾기 / 폭풍 제단 설계도 */
	private static void guide(Gui g, int idx) {
		long now = System.currentTimeMillis();
		if (now - g.lastPress < 500) return;
		g.lastPress = now;
		Player p = g.player;
		BossKind k = BossKind.values()[idx];
		Site site = Site.of(k);
		p.closeInventory();
		if (site == Site.STORM) { stormHelp(p); return; }
		Long cd = LOCATE_CD.get(p.getUniqueId());
		if (cd != null && now - cd < 15_000 && p.getGameMode() != GameMode.CREATIVE) {
			p.sendMessage(Component.text("[홉방] 소환대가 아직 기운을 모으는 중입니다 (" + (15 - (now - cd) / 1000) + "초)", NamedTextColor.GRAY));
			return;
		}
		LOCATE_CD.put(p.getUniqueId(), now);
		org.bukkit.World target = null;
		for (org.bukkit.World w : Bukkit.getWorlds()) if (w.getEnvironment() == site.env) { target = w; break; }
		var reg = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.STRUCTURE);
		org.bukkit.generator.structure.Structure st = reg.get(site.key());
		if (target == null || st == null) { p.sendMessage(Component.text("[홉방] 구조물 정보를 찾을 수 없습니다 (데이터팩 확인).", NamedTextColor.RED)); return; }
		org.bukkit.Location from = p.getLocation().clone();
		double f = 1;
		if (p.getWorld() != target) {
			if (p.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL && site.env == org.bukkit.World.Environment.NETHER) f = 1 / 8.0;
			else if (p.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER && site.env == org.bukkit.World.Environment.NORMAL) f = 8;
			from = new org.bukkit.Location(target, from.getX() * f, 64, from.getZ() * f);
		}
		org.bukkit.util.StructureSearchResult res = target.locateNearestStructure(from, st, 64, false);
		p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.8F, 1.2F);
		if (res == null) {
			p.sendMessage(Component.text("[홉방] 근처(약 1,000칸)에서 " + site.title + "을(를) 찾지 못했습니다. 새 지역을 탐험해 보세요. (" + site.where + ")", NamedTextColor.YELLOW));
			return;
		}
		org.bukkit.Location l = res.getLocation();
		int rx = Math.round(l.getBlockX() / 16f) * 16, rz = Math.round(l.getBlockZ() / 16f) * 16;
		if (p.getWorld() == target) {
			double dx = rx - p.getX(), dz = rz - p.getZ();
			int dist = (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10;
			double yaw = Math.toDegrees(Math.atan2(-dx, dz));
			int di = (int) Math.floorMod(Math.round(yaw / 45.0), 8);
			p.sendMessage(Component.text("[홉방] " + site.title + ": ", NamedTextColor.GOLD)
				.append(Component.text(DIRS[di] + "쪽 약 " + dist + "칸 (대략 X " + rx + ", Z " + rz + ")", NamedTextColor.YELLOW)));
			if (target.getEnvironment() == org.bukkit.World.Environment.NORMAL) {
				p.setCompassTarget(new org.bukkit.Location(target, rx, 64, rz));
				p.sendMessage(Component.text(" 나침반이 그쪽을 가리킵니다." + (site == Site.GROTTO ? " (지하 무성한 동굴 속 — 아래로 파고 들어가세요)" : ""), NamedTextColor.GRAY));
			}
		} else {
			p.sendMessage(Component.text("[홉방] " + site.title + "은(는) " + (site.env == org.bukkit.World.Environment.NETHER ? "네더" : "오버월드") + "에 있습니다: ", NamedTextColor.GOLD)
				.append(Component.text("그 차원 기준 대략 X " + rx + ", Z " + rz, NamedTextColor.YELLOW)));
		}
	}

	public static void stormHelp(Player p) {
		NamedTextColor g = NamedTextColor.GRAY, y = NamedTextColor.YELLOW, a = NamedTextColor.AQUA;
		p.sendMessage(Component.text("⚡ 폭풍 제단 (위에서 본 모습, 5×5)", a, TextDecoration.BOLD));
		p.sendMessage(Component.text("  W · · · W     W = 조약돌 심층암 담장 2단", y));
		p.sendMessage(Component.text("  · · X · ·     X = 깎인 구리 (산화·밀랍 무관) ×4", y));
		p.sendMessage(Component.text("  · X C X ·     C = 구리 블록 + 그 위에 피뢰침", y));
		p.sendMessage(Component.text("  · · X · ·", y));
		p.sendMessage(Component.text("  W · · · W", y));
		p.sendMessage(Component.text(" 재료: 구리 블록 1, 깎인 구리 4, 피뢰침 1, 조약돌 심층암 담장 8", g));
		p.sendMessage(Component.text(" 오버월드에 천둥번개가 칠 때 폭풍의 핵을 들고 피뢰침을 우클릭.", g));
		p.sendMessage(Component.text(" 제단 위 하늘(반지름 20칸, 높이 15칸)이 비어 있어야 하며, 전투가 끝나면 투기장은 무너지고 원래대로 돌아갑니다.", g));
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onClick(InventoryClickEvent e) {
		if (!(e.getView().getTopInventory().getHolder(false) instanceof Gui g)) return;
		int raw = e.getRawSlot();
		boolean top = raw >= 0 && raw < SIZE;
		if (!top) {
			if (e.isShiftClick() || e.getClick() == ClickType.DOUBLE_CLICK || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true);
			return;
		}
		e.setCancelled(true);
		if (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT) return;
		if (raw == CLOSE) { Bukkit.getScheduler().runTask(HobbangPlugin.get(), () -> g.player.closeInventory()); return; }
		for (int i = 0; i < BOSS_SLOTS.length; i++) if (BOSS_SLOTS[i] == raw) {
			int idx = i;
			Bukkit.getScheduler().runTask(HobbangPlugin.get(), () -> guide(g, idx));
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDrag(InventoryDragEvent e) {
		if (!(e.getView().getTopInventory().getHolder(false) instanceof Gui)) return;
		for (int raw : e.getRawSlots()) if (raw < SIZE) { e.setCancelled(true); return; }
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onMove(InventoryMoveItemEvent e) {
		if (e.getSource().getHolder(false) instanceof Gui || e.getDestination().getHolder(false) instanceof Gui) e.setCancelled(true);
	}
}
