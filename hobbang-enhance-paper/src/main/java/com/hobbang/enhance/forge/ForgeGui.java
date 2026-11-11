package com.hobbang.enhance.forge;

import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.Keys;
import com.hobbang.enhance.enhance.EnhanceConfig;
import com.hobbang.enhance.enhance.EnhanceLogic;
import com.hobbang.enhance.item.HItem;
import com.hobbang.enhance.item.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 강화대 창 (v1.2.0). 바닐라 6줄 상자 창 + 바닐라 아이템 버튼 → 리소스팩 없이 바닐라 클라이언트에서 동작.
 * 플레이어마다 따로 열리는 서버 전용 인벤토리(블록 컨테이너가 아니므로 호퍼·다른 플레이어가 접근할 수 없다).
 *
 * <pre>
 *  0  1  2  3 [4 안내] 5  6  7  8
 *  9[10 ▶][11 장비] 12 [13 확률] 14 [15 재료][16 경험치] 17
 * 18 19 20 21 [22 강화 버튼] 23 24 25 26
 * 27[28~33 보조 칸 이름표] 34 35
 * 36[37 행운의 부적][38 프리즘][39 축복][40 파괴 방지][41 불멸][42 역행] 43 44
 * 45 46 47 48 [49 닫기] 50 51 52 53
 * </pre>
 */
public final class ForgeGui implements InventoryHolder, EnhanceLogic.Source {
	public static final int SIZE = 54, GUIDE = 4, ARROW = 10, EQUIP = 11, INFO = 13, MAT = 15, XP = 16, BUTTON = 22, CLOSE = 49;
	public static final HItem[] HELPERS = {HItem.LUCK_CHARM, HItem.PRISM_SCROLL, HItem.BLESSING_SCROLL,
		HItem.PROTECTION_SCROLL, HItem.IMMORTAL_SCROLL, HItem.REWIND_HOURGLASS};
	public static final int[] HELPER_SLOTS = {37, 38, 39, 40, 41, 42};
	private static final String[] HELPER_DESC = {"성공률 +" + "%L%p · 강화마다 1개", "성공률 +20%p · 강화마다 1개",
		"하락을 막아 유지 · 하락할 때 1개", "파괴를 1단계 하락으로 · 파괴될 때 1개",
		"파괴를 막고 유지 · 파괴될 때 1개 (파괴 방지보다 먼저)", "하락·파괴를 되감고 재료 환불 · 그때 1개 (가장 먼저)"};
	private static final Material[] LABEL = {Material.LIME_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
		Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE};
	public static final long PRESS_COOLDOWN_MS = 500;
	public static final double MAX_DISTANCE = 8.0;

	private static final Map<UUID, ForgeGui> OPEN = new HashMap<>();

	final Player player;
	final Block forge;
	final Inventory inv;
	boolean closed = false;
	private long lastPress = 0;
	private boolean busy = false;

	private ForgeGui(Player p, Block forge) {
		this.player = p;
		this.forge = forge;
		this.inv = Bukkit.createInventory(this, SIZE, Component.text("강화대", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
	}

	@Override
	public Inventory getInventory() { return inv; }

	public static int openCount() { return OPEN.size(); }

	public static void init(HobbangPlugin plugin) {
		Bukkit.getPluginManager().registerEvents(new Events(), plugin);
		// 열린 창만: 재료 보유량 갱신 + 강화대가 사라졌거나 멀어지면 닫기
		Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			if (OPEN.isEmpty()) return;
			for (ForgeGui g : new ArrayList<>(OPEN.values())) {
				if (!g.valid()) g.forceClose();
				else g.refresh();
			}
		}, 10L, 10L);
	}

	public static void open(Player p, Block forge) {
		ForgeGui old = OPEN.get(p.getUniqueId());
		if (old != null) old.forceClose();
		ForgeGui g = new ForgeGui(p, forge);
		g.refresh();
		OPEN.put(p.getUniqueId(), g);
		p.openInventory(g.inv);
		p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.6F, 1.2F);
	}

	/** 강화대가 부서지면 그 강화대의 창을 모두 닫는다 (아이템은 돌려준다) */
	public static void closeAt(Block b) {
		for (ForgeGui g : new ArrayList<>(OPEN.values())) if (g.forge.equals(b)) g.forceClose();
	}

	/** 서버 종료·플러그인 비활성화 */
	public static void closeAll() {
		for (ForgeGui g : new ArrayList<>(OPEN.values())) g.forceClose();
		OPEN.clear();
	}

	boolean valid() {
		return player.isOnline() && !player.isDead() && ForgeManager.isForge(forge) && player.getWorld() == forge.getWorld()
			&& player.getLocation().distanceSquared(forge.getLocation().add(0.5, 0.5, 0.5)) <= MAX_DISTANCE * MAX_DISTANCE
			&& player.getOpenInventory().getTopInventory() == inv;
	}

	void forceClose() {
		returnItems(this::give);
		if (player.getOpenInventory().getTopInventory() == inv) player.closeInventory();
	}

	private void give(ItemStack s) {
		for (ItemStack left : player.getInventory().addItem(s).values())
			player.getWorld().dropItem(player.getLocation(), left);
	}

	/** 장비·보조 칸의 아이템을 돌려주고 창을 비운다 (한 번만) */
	void returnItems(Consumer<ItemStack> sink) {
		if (closed) return;
		closed = true;
		OPEN.remove(player.getUniqueId(), this);
		List<ItemStack> back = new ArrayList<>();
		ItemStack g = inv.getItem(EQUIP);
		if (g != null && !g.getType().isAir()) back.add(g.clone());
		for (int s : HELPER_SLOTS) {
			ItemStack h = inv.getItem(s);
			if (h != null && !h.getType().isAir()) back.add(h.clone());
		}
		inv.clear();
		for (ItemStack s : back) sink.accept(s);
	}

	// ───────── 칸 규칙 ─────────
	static int helperIndex(int slot) {
		for (int i = 0; i < HELPER_SLOTS.length; i++) if (HELPER_SLOTS[i] == slot) return i;
		return -1;
	}

	static boolean isInput(int slot) { return slot == EQUIP || helperIndex(slot) >= 0; }

	static boolean accepts(int slot, ItemStack s) {
		if (s == null || s.getType().isAir()) return true;
		if (slot == EQUIP) return ItemUtil.canEnhance(s);
		int i = helperIndex(slot);
		return i >= 0 && HELPERS[i].is(s);
	}

	// ───────── EnhanceLogic.Source ─────────
	@Override public ItemStack gear() {
		ItemStack s = inv.getItem(EQUIP);
		return s == null ? new ItemStack(Material.AIR) : s;
	}

	@Override public void setGear(ItemStack s) { inv.setItem(EQUIP, s); }

	@Override public int helpers(HItem h) {
		for (int i = 0; i < HELPERS.length; i++) {
			if (HELPERS[i] != h) continue;
			ItemStack s = inv.getItem(HELPER_SLOTS[i]);
			return s != null && h.is(s) ? s.getAmount() : 0;
		}
		return 0;
	}

	@Override public boolean useHelper(HItem h) {
		for (int i = 0; i < HELPERS.length; i++) {
			if (HELPERS[i] != h) continue;
			ItemStack s = inv.getItem(HELPER_SLOTS[i]);
			if (s == null || !h.is(s) || s.getAmount() <= 0) return false;
			s.setAmount(s.getAmount() - 1);
			inv.setItem(HELPER_SLOTS[i], s.getAmount() <= 0 ? null : s);
			return true;
		}
		return false;
	}

	// ───────── 강화 버튼 ─────────
	void press() {
		long now = System.currentTimeMillis();
		if (busy || now - lastPress < PRESS_COOLDOWN_MS) return;
		lastPress = now;
		if (!valid()) { forceClose(); return; }
		String why = blocked();
		if (why != null) {
			player.sendMessage(Component.text("[홉방] " + why, NamedTextColor.RED));
			player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6F, 1.0F);
			return;
		}
		busy = true;
		try {
			EnhanceLogic.attempt(player, forge, this);
		} finally {
			busy = false;
		}
		refresh();
	}

	/** 강화할 수 없는 이유 (null = 가능) */
	String blocked() {
		ItemStack s = inv.getItem(EQUIP);
		if (!ItemUtil.canEnhance(s)) return "장비 칸에 무기/방어구/도구를 넣으세요.";
		int l = ItemUtil.getLevel(s);
		if (l >= EnhanceLogic.MAX_LEVEL) return "이미 최대 강화(+15)입니다!";
		if (player.getGameMode() == GameMode.CREATIVE) return null;
		EnhanceLogic.Odds o = EnhanceLogic.odds(this, s);
		int have = EnhanceLogic.count(player, o.stone());
		if (have < o.stones()) return "재료 부족: " + o.stone().name + " " + o.stones() + "개 필요 (보유 " + have + ")";
		if (player.getLevel() < o.xp()) return "경험치 부족: " + o.xp() + "레벨 필요 (보유 " + player.getLevel() + ")";
		return null;
	}

	// ───────── 화면 ─────────
	private static Component plain(String s, NamedTextColor c) {
		return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
	}

	private static String pct(double d) { return com.hobbang.enhance.enhance.EnhanceLogic.pctStr(d); }

	private static ItemStack icon(Material m, int amount, Component name, List<Component> lore) {
		ItemStack s = new ItemStack(m, Math.max(1, Math.min(99, amount)));
		ItemMeta meta = s.getItemMeta();
		meta.displayName(name.decoration(TextDecoration.ITALIC, false));
		if (lore != null) meta.lore(lore);
		meta.getPersistentDataContainer().set(Keys.k("gui"), PersistentDataType.BYTE, (byte) 1);
		meta.setMaxStackSize(99);
		s.setItemMeta(meta);
		return s;
	}

	private static ItemStack pane(Material m, String name) {
		return icon(m, 1, plain(name, NamedTextColor.GRAY), null);
	}

	void refresh() {
		if (closed) return;
		ItemStack border = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
		for (int i = 0; i < SIZE; i++) if (!isInput(i)) inv.setItem(i, border);
		inv.setItem(GUIDE, icon(Material.SMITHING_TABLE, 1, plain("강화대", NamedTextColor.GOLD), List.of(
			plain("① 장비 칸에 무기/방어구/도구를 넣는다", NamedTextColor.WHITE),
			plain("② (선택) 아래 보조 칸에 부적·주문서를 넣는다", NamedTextColor.WHITE),
			plain("③ 모루 버튼을 누르면 강화 1회", NamedTextColor.WHITE),
			plain("강화석·경험치는 가방에서 쓴다", NamedTextColor.GRAY),
			plain("창을 닫으면 칸의 아이템은 가방으로 돌아온다", NamedTextColor.GRAY))));
		inv.setItem(ARROW, icon(Material.LIME_STAINED_GLASS_PANE, 1, plain("장비 칸 ▶", NamedTextColor.GREEN),
			List.of(plain("무기·방어구·도구 1개", NamedTextColor.GRAY))));
		for (int i = 0; i < HELPERS.length; i++) {
			String d = HELPER_DESC[i].replace("%L%", String.valueOf((int) EnhanceConfig.CFG.luckCharmBonus));
			inv.setItem(HELPER_SLOTS[i] - 9, icon(LABEL[i], 1, plain("▼ " + HELPERS[i].name, NamedTextColor.AQUA),
				List.of(plain(d, NamedTextColor.GRAY), plain("이 칸에 넣어야 적용 (가방에 있는 것은 쓰지 않음)", NamedTextColor.DARK_GRAY))));
		}
		inv.setItem(CLOSE, icon(Material.OAK_DOOR, 1, plain("닫기", NamedTextColor.RED), List.of(plain("칸의 아이템은 가방으로 돌아온다", NamedTextColor.GRAY))));

		ItemStack s = inv.getItem(EQUIP);
		if (!ItemUtil.canEnhance(s)) {
			inv.setItem(INFO, icon(Material.PAPER, 1, plain("강화 확률", NamedTextColor.YELLOW), List.of(plain("장비 칸에 장비를 넣으면 확률이 보입니다.", NamedTextColor.GRAY))));
			inv.setItem(MAT, icon(Material.PRISMARINE_CRYSTALS, 1, plain("필요 재료", NamedTextColor.YELLOW), List.of(plain("-", NamedTextColor.GRAY))));
			inv.setItem(XP, icon(Material.EXPERIENCE_BOTTLE, 1, plain("필요 경험치", NamedTextColor.YELLOW), List.of(plain("-", NamedTextColor.GRAY))));
			inv.setItem(BUTTON, icon(Material.BARRIER, 1, plain("강화하기", NamedTextColor.DARK_GRAY), List.of(plain(blocked(), NamedTextColor.RED))));
			return;
		}
		int l = ItemUtil.getLevel(s);
		if (l >= EnhanceLogic.MAX_LEVEL) {
			inv.setItem(INFO, icon(Material.NETHER_STAR, 1, plain("+15 최대 강화", NamedTextColor.LIGHT_PURPLE), List.of(plain("더 강화할 수 없습니다.", NamedTextColor.GRAY))));
			inv.setItem(MAT, icon(Material.PRISMARINE_CRYSTALS, 1, plain("필요 재료", NamedTextColor.YELLOW), List.of(plain("-", NamedTextColor.GRAY))));
			inv.setItem(XP, icon(Material.EXPERIENCE_BOTTLE, 1, plain("필요 경험치", NamedTextColor.YELLOW), List.of(plain("-", NamedTextColor.GRAY))));
			inv.setItem(BUTTON, icon(Material.BARRIER, 1, plain("강화하기", NamedTextColor.DARK_GRAY), List.of(plain("이미 최대 강화(+15)입니다!", NamedTextColor.RED))));
			return;
		}
		EnhanceLogic.Odds o = EnhanceLogic.odds(this, s);
		EnhanceLogic.Abs a = EnhanceLogic.absolute(o, l);
		EnhanceLogic.Abs ef = EnhanceLogic.effective(a, this);
		EnhanceConfig.Stage st = EnhanceConfig.CFG.stages[l];
		List<Component> lore = new ArrayList<>();
		lore.add(Component.empty().append(ItemUtil.levelTag(l)).append(plain("→ ", NamedTextColor.WHITE)).append(ItemUtil.levelTag(l + 1)).decoration(TextDecoration.ITALIC, false));
		String gain = com.hobbang.enhance.item.EnhanceTier.gainAt(s, l + 1);
		if (gain != null) lore.add(plain("성공 시 +" + (l + 1) + ": " + gain, NamedTextColor.LIGHT_PURPLE));
		else {
			int nm = com.hobbang.enhance.item.EnhanceTier.nextMilestone(l);
			String nd = nm < 0 ? null : com.hobbang.enhance.item.EnhanceTier.gainAt(s, nm);
			if (nd != null) lore.add(plain("다음 마일스톤 +" + nm + ": " + nd, NamedTextColor.DARK_PURPLE));
		}
		if (ItemUtil.isWeapon(s)) lore.add(plain("공격력 +" + com.hobbang.enhance.item.EnhanceTier.weaponDamage(l) + " → +" + com.hobbang.enhance.item.EnhanceTier.weaponDamage(l + 1), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
		lore.add(plain("─ 결과 확률 (합계 100%) ─", NamedTextColor.GOLD));
		lore.add(plain("✦ 성공  " + pct(a.success()) + (o.guaranteed() ? "  (장인의 기운 MAX!)" : ""), NamedTextColor.GREEN));
		lore.add(plain("✖ 실패(유지)  " + pct(a.keep()), NamedTextColor.GRAY));
		lore.add(plain("▼ 하락  " + pct(a.down()), NamedTextColor.YELLOW));
		lore.add(plain("☠ 파괴  " + pct(a.destroy()), NamedTextColor.RED));
		boolean changed = Math.abs(ef.keep() - a.keep()) > 1e-9 || Math.abs(ef.down() - a.down()) > 1e-9 || Math.abs(ef.destroy() - a.destroy()) > 1e-9;
		if (changed) {
			lore.add(plain("─ 보조 칸 주문서 적용 시 실제 결과 ─", NamedTextColor.AQUA));
			lore.add(plain("유지 " + pct(ef.keep()) + " · 하락 " + pct(ef.down()) + " · 파괴 " + pct(ef.destroy()), NamedTextColor.AQUA));
		}
		lore.add(plain("─ 계산 ─", NamedTextColor.DARK_AQUA));
		StringBuilder sb = new StringBuilder("기본 성공 " + pct(st.success));
		if (o.affinity() > 0) sb.append(" +강화 친화 ").append(pct(o.affinity() * EnhanceConfig.CFG.affinityPerLevel));
		if (o.luck()) sb.append(" +부적 ").append(pct(EnhanceConfig.CFG.luckCharmBonus));
		if (o.prism()) sb.append(" +프리즘 ").append(pct(EnhanceLogic.PRISM_BONUS));
		lore.add(plain(sb.toString(), NamedTextColor.GRAY));
		lore.add(plain("실패했을 때: 하락 " + pct(l > 0 ? Math.min(o.down(), 100 - o.destroy()) : 0) + " · 파괴 " + pct(o.destroy())
			+ (o.fragility() > 0 && o.destroy() > st.destroy ? " (깨짐의 저주 +" + pct(o.destroy() - st.destroy) + ")" : ""), NamedTextColor.GRAY));
		lore.add(plain("장인의 기운 " + pct(ItemUtil.getArtisan(s) / 10.0) + " (100%면 다음 강화 확정 성공)", NamedTextColor.DARK_AQUA));
		inv.setItem(INFO, icon(o.guaranteed() ? Material.NETHER_STAR : Material.ENCHANTED_BOOK, 1, plain("강화 확률", NamedTextColor.YELLOW), lore));

		int have = EnhanceLogic.count(player, o.stone());
		boolean radiant = o.stone() == HItem.RADIANT_STONE;
		boolean creative = player.getGameMode() == GameMode.CREATIVE;
		inv.setItem(MAT, icon(radiant ? Material.GLOWSTONE_DUST : Material.PRISMARINE_CRYSTALS, o.stones(),
			plain("필요 재료: " + o.stone().name + " x" + o.stones(), radiant ? NamedTextColor.AQUA : NamedTextColor.GREEN),
			List.of(plain("보유 " + have + "개" + (have >= o.stones() || creative ? "  ✔" : "  ✘ 부족"), have >= o.stones() || creative ? NamedTextColor.GREEN : NamedTextColor.RED),
				plain("가방에서 자동으로 꺼내 쓴다", NamedTextColor.GRAY))));
		inv.setItem(XP, icon(Material.EXPERIENCE_BOTTLE, o.xp(), plain("필요 경험치: " + o.xp() + "레벨", NamedTextColor.YELLOW),
			List.of(plain("보유 " + player.getLevel() + "레벨" + (player.getLevel() >= o.xp() || creative ? "  ✔" : "  ✘ 부족"),
				player.getLevel() >= o.xp() || creative ? NamedTextColor.GREEN : NamedTextColor.RED))));
		String why = blocked();
		if (why == null) inv.setItem(BUTTON, icon(Material.ANVIL, 1, plain("⚒ 강화하기", NamedTextColor.GOLD), List.of(
			plain("클릭: 강화 1회 시도", NamedTextColor.WHITE),
			plain("성공 " + pct(a.success()) + " · 유지 " + pct(ef.keep()) + " · 하락 " + pct(ef.down()) + " · 파괴 " + pct(ef.destroy()), NamedTextColor.GRAY))));
		else inv.setItem(BUTTON, icon(Material.BARRIER, 1, plain("강화하기", NamedTextColor.DARK_GRAY), List.of(plain(why, NamedTextColor.RED))));
	}

	private void refreshLater() {
		Bukkit.getScheduler().runTask(HobbangPlugin.get(), this::refresh);
	}

	/** 가방에서 쉬프트 클릭: 알맞은 칸으로 옮긴다 */
	private void shiftIn(Inventory from, int slot, ItemStack cur) {
		if (cur == null || cur.getType().isAir()) return;
		if (ItemUtil.canEnhance(cur)) {
			ItemStack e = inv.getItem(EQUIP);
			if (e == null || e.getType().isAir()) {
				inv.setItem(EQUIP, cur.clone());
				from.setItem(slot, null);
			}
			return;
		}
		for (int i = 0; i < HELPERS.length; i++) {
			if (!HELPERS[i].is(cur)) continue;
			ItemStack h = inv.getItem(HELPER_SLOTS[i]);
			int max = cur.getMaxStackSize();
			if (h == null || h.getType().isAir()) {
				inv.setItem(HELPER_SLOTS[i], cur.clone());
				from.setItem(slot, null);
			} else if (h.isSimilar(cur) && h.getAmount() < max) {
				int move = Math.min(max - h.getAmount(), cur.getAmount());
				h.setAmount(h.getAmount() + move);
				inv.setItem(HELPER_SLOTS[i], h);
				ItemStack rest = cur.clone();
				rest.setAmount(cur.getAmount() - move);
				from.setItem(slot, rest.getAmount() <= 0 ? null : rest);
			}
			return;
		}
	}

	// ───────── 이벤트 ─────────
	static final class Events implements Listener {
		private static ForgeGui of(Inventory top) {
			return top != null && top.getHolder(false) instanceof ForgeGui g ? g : null;
		}

		@EventHandler(priority = EventPriority.LOWEST)
		public void onClick(InventoryClickEvent e) {
			ForgeGui g = of(e.getView().getTopInventory());
			if (g == null) return;
			if (g.closed || !(e.getWhoClicked() instanceof Player)) { e.setCancelled(true); return; }
			int raw = e.getRawSlot();
			InventoryAction act = e.getAction();
			ClickType ct = e.getClick();
			// 더블클릭 모으기는 위 창의 칸에서도 끌어오므로 어디서든 막는다
			if (ct == ClickType.DOUBLE_CLICK || act == InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
			boolean top = raw >= 0 && raw < SIZE;
			if (!top) {
				if (act == InventoryAction.MOVE_TO_OTHER_INVENTORY || e.isShiftClick()) {
					e.setCancelled(true);
					if (e.getClickedInventory() != null) g.shiftIn(e.getClickedInventory(), e.getSlot(), e.getCurrentItem());
					g.refreshLater();
				}
				return;   // 가방 안에서의 일반 클릭·숫자키·버리기는 그대로
			}
			e.setCancelled(true);
			if (raw == BUTTON) {
				if (ct == ClickType.LEFT || ct == ClickType.RIGHT) g.press();
				return;
			}
			if (raw == CLOSE) {
				Bukkit.getScheduler().runTask(HobbangPlugin.get(), g::forceClose);
				return;
			}
			if (!isInput(raw)) return;
			boolean ok = switch (act) {
				case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY, DROP_ONE_SLOT, DROP_ALL_SLOT -> true;
				case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> accepts(raw, e.getCursor());
				default -> false;   // 숫자키·F 교체·가운데 클릭 등
			};
			if (ok) {
				e.setCancelled(false);
				g.refreshLater();
			}
		}

		@EventHandler(priority = EventPriority.LOWEST)
		public void onDrag(InventoryDragEvent e) {
			if (of(e.getView().getTopInventory()) == null) return;
			for (int raw : e.getRawSlots()) if (raw < SIZE) { e.setCancelled(true); return; }
		}

		@EventHandler(priority = EventPriority.MONITOR)
		public void onClose(InventoryCloseEvent e) {
			ForgeGui g = of(e.getInventory());
			if (g == null) return;
			g.returnItems(g::give);
		}

		@EventHandler(priority = EventPriority.LOWEST)
		public void onQuit(PlayerQuitEvent e) {
			ForgeGui g = OPEN.get(e.getPlayer().getUniqueId());
			if (g != null) g.returnItems(g::give);
		}

		/** 죽으면 칸의 아이템도 함께 떨어지거나(일반) 가방에 남는다(keepInventory) */
		@EventHandler(priority = EventPriority.LOWEST)
		public void onDeath(PlayerDeathEvent e) {
			ForgeGui g = OPEN.get(e.getPlayer().getUniqueId());
			if (g == null) return;
			if (e.getKeepInventory()) g.returnItems(g::give);
			else g.returnItems(e.getDrops()::add);
		}

		@EventHandler(priority = EventPriority.LOWEST)
		public void onMove(InventoryMoveItemEvent e) {
			if (of(e.getSource()) != null || of(e.getDestination()) != null) e.setCancelled(true);
		}

		@EventHandler(priority = EventPriority.LOWEST)
		public void onPickup(InventoryPickupItemEvent e) {
			if (of(e.getInventory()) != null) e.setCancelled(true);
		}
	}
}
