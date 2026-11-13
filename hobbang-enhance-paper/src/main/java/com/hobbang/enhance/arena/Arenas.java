package com.hobbang.enhance.arena;

import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.Keys;
import com.hobbang.enhance.boss.Boss;
import com.hobbang.enhance.boss.BossKind;
import com.hobbang.enhance.boss.BossManager;
import com.hobbang.enhance.boss.Ritual;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.4.0 소환 규칙: 보스는 자기 구조물의 제단에서만 (와이번은 폭풍 제단 의식).
 * - 제단 블록 = 구조물 템플릿 속 블록 엔티티 (용광로/신호기/마법부여대) + PDC hobbang:altar = 보스 id.
 * - 보스전 중 (보스가 살아 있고 투기장에 묶여 있으면) 투기장 안 블록 설치/파괴/양동이 금지 → 기둥 쌓기·땅굴 꼼수 차단.
 * - 보스가 투기장 밖으로 나가면 2초마다 확인해 소환 지점으로 되돌린다.
 * 이벤트 때만 계산 (보스 수 ≤ 몇 마리) → 틱 비용 없음. 리시 확인은 40틱마다 보스 수만큼.
 */
public final class Arenas implements Listener {
	private Arenas() {}

	public static void init(HobbangPlugin plugin) {
		Bukkit.getPluginManager().registerEvents(new Arenas(), plugin);
		Bukkit.getScheduler().runTaskTimer(plugin, Arenas::leash, 40, 40);
	}

	/** 블록 엔티티의 제단 PDC → 보스 id (아니면 null) */
	public static String altarId(Block b) {
		if (b == null) return null;
		Site guess = null;
		for (Site s : Site.values()) if (s.structure != null && s.altarBlock == b.getType()) guess = s;
		if (guess == null) return null;
		BlockState st = b.getState(false);
		if (!(st instanceof TileState ts)) return null;
		return ts.getPersistentDataContainer().get(Keys.ALTAR, PersistentDataType.STRING);
	}

	public static void markAltar(Block b, Site s) {
		if (b.getState(false) instanceof TileState ts) {
			ts.getPersistentDataContainer().set(Keys.ALTAR, PersistentDataType.STRING, s.kind.id);
			ts.update(true, false);
		}
	}

	/** 지금 진행 중인 보스전 투기장들 */
	public static List<ArenaRef> active() {
		List<ArenaRef> out = new ArrayList<>(2);
		for (Boss b : BossManager.all().values()) if (b.arena != null && !b.dead && b.mob.isValid()) out.add(b.arena);
		out.addAll(StormArena.zones());
		return out;
	}

	public static ArenaRef activeAt(Block b) {
		if (BossManager.all().isEmpty() && !StormArena.busy()) return null;
		for (ArenaRef a : active()) if (a.contains(b)) return a;
		return null;
	}

	private static boolean exempt(Player p) {
		return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
	}

	private static void deny(Player p, String msg) {
		p.sendActionBar(Component.text(msg, NamedTextColor.RED));
	}

	// ───────── 보스전 중 투기장 보호 ─────────
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent e) {
		if (exempt(e.getPlayer())) return;
		if (activeAt(e.getBlockPlaced()) != null) { e.setCancelled(true); deny(e.getPlayer(), "보스전 중에는 투기장에 블록을 놓을 수 없습니다"); }
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent e) {
		Block b = e.getBlock();
		if (altarId(b) != null) {
			if (e.getPlayer().getGameMode() == GameMode.CREATIVE && e.getPlayer().isSneaking()) return;
			e.setCancelled(true);
			deny(e.getPlayer(), "보스 제단은 부술 수 없습니다" + (e.getPlayer().getGameMode() == GameMode.CREATIVE ? " (크리에이티브: 웅크리고 파괴)" : ""));
			return;
		}
		if (exempt(e.getPlayer())) return;
		if (activeAt(b) != null || StormArena.owns(b)) { e.setCancelled(true); deny(e.getPlayer(), "보스전 중에는 투기장 블록을 부술 수 없습니다"); }
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBucket(PlayerBucketEmptyEvent e) {
		if (exempt(e.getPlayer())) return;
		if (activeAt(e.getBlockClicked().getRelative(e.getBlockFace())) != null) { e.setCancelled(true); deny(e.getPlayer(), "보스전 중에는 투기장에 액체를 부을 수 없습니다"); }
	}

	private static boolean protectedBlock(Block b) {
		return altarId(b) != null || StormArena.owns(b) || activeAt(b) != null;
	}

	@EventHandler(ignoreCancelled = true)
	public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(Arenas::protectedBlock); }

	@EventHandler(ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(Arenas::protectedBlock); }

	@EventHandler(ignoreCancelled = true)
	public void onPiston(BlockPistonExtendEvent e) { if (e.getBlocks().stream().anyMatch(b -> altarId(b) != null || StormArena.owns(b))) e.setCancelled(true); }

	@EventHandler(ignoreCancelled = true)
	public void onPistonR(BlockPistonRetractEvent e) { if (e.getBlocks().stream().anyMatch(b -> altarId(b) != null || StormArena.owns(b))) e.setCancelled(true); }

	@EventHandler(ignoreCancelled = true)
	public void onEntityChange(EntityChangeBlockEvent e) { if (altarId(e.getBlock()) != null || StormArena.owns(e.getBlock())) e.setCancelled(true); }

	// ───────── 제단 우클릭 = 소환 ─────────
	@EventHandler(priority = EventPriority.LOW)
	public void onInteract(PlayerInteractEvent e) {
		if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		Block b = e.getClickedBlock();
		String id = altarId(b);
		if (id == null) return;
		e.setCancelled(true);   // 용광로/신호기/마법부여대 창을 열지 않는다
		if (e.getHand() != EquipmentSlot.HAND) return;
		Player p = e.getPlayer();
		Site site = Site.byBoss(id);
		if (site == null) return;
		ItemStack held = p.getInventory().getItemInMainHand();
		BossKind k = BossKind.byCore(held);
		if (k == null) {
			p.sendMessage(Component.text("[홉방] " + site.title + "의 제단 — ", NamedTextColor.GOLD)
				.append(Component.text(site.kind.core.name + "을(를) 들고 우클릭하면 " + site.kind.name + " 소환 의식이 시작됩니다.", NamedTextColor.YELLOW)));
			p.playSound(b.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.8F, 0.6F);
			return;
		}
		if (k != site.kind) {
			p.sendMessage(Component.text("[홉방] 이 제단은 " + site.kind.name + " 전용입니다. " + k.name + " 소환 장소: " + Site.of(k).where, NamedTextColor.RED));
			return;
		}
		ArenaRef ref = ArenaRef.at(site, b);
		for (ArenaRef a : active()) if (a.world() == ref.world() && a.x() == ref.x() && a.y() == ref.y() && a.z() == ref.z()) {
			p.sendMessage(Component.text("[홉방] 이 투기장에서는 이미 보스전이 진행 중입니다!", NamedTextColor.RED));
			return;
		}
		if (Ritual.start(b, p, held, k, ref.spawn(), m -> { ref.write(m); Boss bb = BossManager.get(m); if (bb != null) bb.arena = ref; })) {
			b.getWorld().spawnParticle(Particle.FLASH, b.getX() + 0.5, b.getY() + 1.5, b.getZ() + 0.5, 1, 0, 0, 0, 0, org.bukkit.Color.WHITE);
		}
	}

	/** 다른 곳(강화대·소환대)에 핵을 쓰면 안내 */
	public static void hint(Player p, BossKind k) {
		Site s = Site.of(k);
		p.sendMessage(Component.text("[홉방] v1.4.0부터 보스는 정해진 제단에서만 소환됩니다.", NamedTextColor.GOLD));
		p.sendMessage(Component.text(" " + k.name + " → " + s.title + " (" + s.where + ")", NamedTextColor.YELLOW));
		if (s == Site.STORM) p.sendMessage(Component.text(" 폭풍 제단 짓는 법: 가이드북 / 소환대(보스 안내) 참고", NamedTextColor.GRAY));
		else p.sendMessage(Component.text(" 구조물 한가운데 제단(" + s.altarBlock.name().toLowerCase() + ")에 핵을 들고 우클릭. 소환대로 방향을 찾을 수 있습니다.", NamedTextColor.GRAY));
	}

	// ───────── 투기장 이탈 방지 ─────────
	private static void leash() {
		if (BossManager.all().isEmpty()) return;
		for (Boss b : BossManager.all().values()) {
			ArenaRef a = b.arena;
			if (a == null || b.dead || !b.mob.isValid() || b.mob.getWorld() != a.world()) continue;
			Location l = b.mob.getLocation();
			double dx = l.getX() - (a.x() + 0.5), dz = l.getZ() - (a.z() + 0.5);
			double lim = a.site().half + (a.site() == Site.STORM ? 14 : 1.5);
			boolean out = Math.abs(dx) > lim || Math.abs(dz) > lim || l.getY() < a.y() + a.site().yLo - 4 || l.getY() > a.y() + a.site().yHi + 20;
			if (!out) continue;
			Location to = a.spawn().add(0.5, b.kind.lift, 0.5);
			to.setYaw(l.getYaw());
			b.mob.getWorld().spawnParticle(Particle.REVERSE_PORTAL, l.getX(), l.getY() + 2, l.getZ(), 40, 1, 2, 1, 0.05);
			b.mob.teleport(to, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
		}
	}
}
