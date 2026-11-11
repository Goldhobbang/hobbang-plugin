package com.hobbang.enhance.forge;

import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.Keys;
import com.hobbang.enhance.boss.BossKind;
import com.hobbang.enhance.enhance.EnhanceConfig;
import com.hobbang.enhance.enhance.EnhanceLogic;
import com.hobbang.enhance.item.HItem;
import com.hobbang.enhance.item.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
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
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 강화대 = 바닐라 대장장이 작업대 + 청크 PDC 에 기록된 위치.
 * 우클릭(장비)=강화 / 좌클릭=미리보기 / 웅크리고 좌클릭=부수기 / 핵 우클릭=의식 / 빈손=도움말
 */
public final class ForgeManager implements Listener {
	/** 로드된 강화대 (주변 연출용). 청크 로드/언로드로만 갱신 → 월드 스캔 없음 */
	private static final Set<Block> LOADED = new HashSet<>();

	public static void init(HobbangPlugin plugin) {
		Bukkit.getPluginManager().registerEvents(new ForgeManager(), plugin);
		for (World w : Bukkit.getWorlds()) for (Chunk c : w.getLoadedChunks()) loadChunk(c);
		Bukkit.getScheduler().runTaskTimer(plugin, ForgeManager::ambient, 20L, 5L);
	}

	// ───────── 저장 ─────────
	private static long pack(Block b) {
		return ((long) (b.getX() & 15) << 60) | ((long) (b.getZ() & 15) << 56) | ((b.getY() + 2048L) & 0xFFFFL);
	}

	private static long[] get(Chunk c) {
		long[] a = c.getPersistentDataContainer().get(Keys.FORGES, PersistentDataType.LONG_ARRAY);
		return a == null ? new long[0] : a;
	}

	private static void set(Chunk c, long[] a) {
		if (a.length == 0) c.getPersistentDataContainer().remove(Keys.FORGES);
		else c.getPersistentDataContainer().set(Keys.FORGES, PersistentDataType.LONG_ARRAY, a);
	}

	public static int loadedCount() {
		return LOADED.size();
	}

	public static boolean isForge(Block b) {
		if (b == null || b.getType() != Material.SMITHING_TABLE) return false;
		long k = pack(b);
		for (long v : get(b.getChunk())) if (v == k) return true;
		return false;
	}

	public static void add(Block b) {
		if (isForge(b)) return;
		long[] a = get(b.getChunk());
		long[] n = java.util.Arrays.copyOf(a, a.length + 1);
		n[a.length] = pack(b);
		set(b.getChunk(), n);
		LOADED.add(b);
	}

	public static void remove(Block b) {
		long k = pack(b);
		long[] a = get(b.getChunk());
		set(b.getChunk(), java.util.Arrays.stream(a).filter(v -> v != k).toArray());
		LOADED.remove(b);
	}

	private static void loadChunk(Chunk c) {
		long[] a = get(c);
		if (a.length == 0) return;
		for (long v : a) {
			int lx = (int) ((v >>> 60) & 15), lz = (int) ((v >>> 56) & 15), y = (int) ((v & 0xFFFFL) - 2048);
			Block b = c.getBlock(lx, y, lz);
			if (b.getType() == Material.SMITHING_TABLE) LOADED.add(b);
		}
	}

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent e) {
		loadChunk(e.getChunk());
	}

	@EventHandler
	public void onChunkUnload(ChunkUnloadEvent e) {
		if (LOADED.isEmpty()) return;
		Chunk c = e.getChunk();
		LOADED.removeIf(b -> b.getWorld() == c.getWorld() && (b.getX() >> 4) == c.getX() && (b.getZ() >> 4) == c.getZ());
	}

	// ───────── 설치/파괴 ─────────
	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onPlace(BlockPlaceEvent e) {
		if (HItem.ENHANCE_FORGE.is(e.getItemInHand())) add(e.getBlockPlaced());
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBreak(BlockBreakEvent e) {
		Block b = e.getBlock();
		if (!isForge(b)) return;
		remove(b);
		ForgeGui.closeAt(b);
		e.setDropItems(false);
		if (e.getPlayer().getGameMode() != GameMode.CREATIVE)
			b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), HItem.ENHANCE_FORGE.create());
	}

	@EventHandler(ignoreCancelled = true)
	public void onBurn(BlockBurnEvent e) {
		if (isForge(e.getBlock())) e.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onExplode(EntityExplodeEvent e) {
		e.blockList().removeIf(ForgeManager::isForge);
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent e) {
		e.blockList().removeIf(ForgeManager::isForge);
	}

	@EventHandler(ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent e) {
		if (anyForge(e.getBlocks())) e.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent e) {
		if (anyForge(e.getBlocks())) e.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityChange(EntityChangeBlockEvent e) {
		if (isForge(e.getBlock())) e.setCancelled(true);
	}

	private static boolean anyForge(List<Block> bs) {
		for (Block b : bs) if (b.getType() == Material.SMITHING_TABLE && isForge(b)) return true;
		return false;
	}

	// ───────── 상호작용 ─────────
	@EventHandler(priority = EventPriority.LOW)
	public void onInteract(PlayerInteractEvent e) {
		Block b = e.getClickedBlock();
		if (b == null || b.getType() != Material.SMITHING_TABLE || !isForge(b)) return;
		Player p = e.getPlayer();
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
			if (e.getHand() != EquipmentSlot.HAND) {
				e.setCancelled(true);
				return;
			}
			ItemStack held = p.getInventory().getItemInMainHand();
			BossKind k = BossKind.byCore(held);
			if (k != null) {
				e.setCancelled(true);
				com.hobbang.enhance.arena.Arenas.hint(p, k);   // v1.4.0: 강화대에서는 소환하지 않음
				return;
			}
			// 웅크리고 무언가 들고 있으면 바닐라처럼 (블록 설치 등)
			if (p.isSneaking() && !held.getType().isAir()) return;
			e.setCancelled(true);
			if (EnhanceConfig.CFG.legacyRightClick && ItemUtil.canEnhance(held)) {
				EnhanceLogic.attempt(p, b);   // 예전 방식 (설정 legacyRightClick=true 일 때만)
				return;
			}
			ForgeGui.open(p, b);
		} else if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
			if (p.isSneaking()) return;   // 웅크리면 정상 파괴
			ItemStack held = p.getInventory().getItemInMainHand();
			if (held.getType().isAir()) sendHelp(p);
			else EnhanceLogic.preview(p, held);
			if (p.getGameMode() == GameMode.CREATIVE) e.setCancelled(true);
		}
	}

	/** 혹시 다른 경로로 강화대의 작업대 GUI가 열리면 닫는다 */
	@EventHandler(ignoreCancelled = true)
	public void onOpen(InventoryOpenEvent e) {
		if (e.getInventory().getType() != org.bukkit.event.inventory.InventoryType.SMITHING) return;
		org.bukkit.Location l = e.getInventory().getLocation();
		if (l != null && isForge(l.getBlock())) e.setCancelled(true);
	}

	public static void sendHelp(Player p) {
		p.sendMessage(Component.text("━━━━ 강화대 사용법 ━━━━", NamedTextColor.GOLD));
		p.sendMessage(Component.text("• 우클릭 → 강화 창: 장비 칸에 장비를 넣고 모루 버튼으로 강화", NamedTextColor.WHITE));
		p.sendMessage(Component.text("• 장비를 들고 좌클릭 → 확률·재료 미리보기", NamedTextColor.WHITE));
		p.sendMessage(Component.text("• +0~+9: 강화석 / +10~+15: 빛나는 강화석 + 경험치 레벨", NamedTextColor.GRAY));
		p.sendMessage(Component.text("• 부적·주문서는 창 아래 보조 칸에 넣은 것만 사용", NamedTextColor.GRAY));
		p.sendMessage(Component.text("• 심연의 핵을 들고 우클릭 → 보스 '심연의 대장장이' 소환 의식", NamedTextColor.DARK_PURPLE));
		p.sendMessage(Component.text("• 웅크리고 좌클릭 → 강화대 부수기", NamedTextColor.GRAY));
		p.sendMessage(Component.text("• 자세한 내용: /hobbang help", NamedTextColor.AQUA));
	}

	// ───────── 연출 (Fabric animateTick 대체, 근처에 플레이어가 있을 때만) ─────────
	private static void ambient() {
		if (LOADED.isEmpty()) return;
		ThreadLocalRandom r = ThreadLocalRandom.current();
		LOADED.removeIf(b -> b.getType() != Material.SMITHING_TABLE);
		for (Block b : LOADED) {
			World w = b.getWorld();
			double x = b.getX(), y = b.getY(), z = b.getZ();
			boolean near = false;
			for (Player p : w.getPlayers()) {
				if (p.getLocation().distanceSquared(b.getLocation()) < 24 * 24) { near = true; break; }
			}
			if (!near) continue;
			w.spawnParticle(Particle.ENCHANT, x + r.nextDouble(), y + 1.2, z + r.nextDouble(), 0, 0, 0.3, 0, 1);
			if (r.nextInt(6) == 0) w.spawnParticle(Particle.SMALL_FLAME, x + 0.5, y + 0.7, z + 0.5, 0, 0, 0.01, 0, 1);
		}
	}
}
