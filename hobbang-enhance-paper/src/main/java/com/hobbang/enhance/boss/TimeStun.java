package com.hobbang.enhance.boss;

import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 시간 정지 스턴: 걷기·달리기·점프·낙하를 묶고 시점 회전은 그대로 둔다. 스턴 중에는 어떤 공격도 하지 못한다. 플레이어 UUID 단위.
 * 속도·점프·중력 속성을 ×0 으로 만들고(클라이언트도 안 움직임), 넉백 등 남은 위치 변화는 PlayerMoveEvent 에서 되돌린다.
 */
public final class TimeStun implements Listener {
	private static final NamespacedKey KEY = Keys.k("time_stun");
	private static final Attribute[] LOCKED = {Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH, Attribute.GRAVITY};
	private static final Map<UUID, BukkitTask> ACTIVE = new HashMap<>();

	private TimeStun() {}

	public static void init(HobbangPlugin plugin) {
		Bukkit.getPluginManager().registerEvents(new TimeStun(), plugin);
		for (Player p : Bukkit.getOnlinePlayers()) strip(p);   // 리로드 직후 남은 수식어 정리
	}

	public static void shutdown() {
		for (UUID id : new ArrayList<>(ACTIVE.keySet())) release(id);
	}

	public static boolean isActive(Player p) {
		return p != null && ACTIVE.containsKey(p.getUniqueId());
	}

	static void apply(Player p, int ticks) {
		UUID id = p.getUniqueId();
		release(id);
		for (Attribute a : LOCKED) {
			AttributeInstance ai = p.getAttribute(a);
			if (ai != null) ai.addModifier(new AttributeModifier(KEY, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
		}
		p.setVelocity(new Vector());
		ACTIVE.put(id, Bukkit.getScheduler().runTaskLater(HobbangPlugin.get(), () -> release(id), Math.max(1, ticks)));
	}

	static void release(UUID id) {
		BukkitTask t = ACTIVE.remove(id);
		if (t != null) t.cancel();
		Player p = Bukkit.getPlayer(id);
		if (p != null) strip(p);
	}

	private static void strip(Player p) {
		for (Attribute a : LOCKED) {
			AttributeInstance ai = p.getAttribute(a);
			if (ai != null) ai.removeModifier(KEY);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onMove(PlayerMoveEvent e) {
		if (!ACTIVE.containsKey(e.getPlayer().getUniqueId())) return;
		Location from = e.getFrom(), to = e.getTo();
		if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
		Location keep = from.clone();
		keep.setYaw(to.getYaw());
		keep.setPitch(to.getPitch());
		e.setTo(keep);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		BukkitTask t = ACTIVE.remove(e.getPlayer().getUniqueId());
		if (t != null) t.cancel();
		strip(e.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onAttack(EntityDamageByEntityEvent e) {
		Player p = e.getDamager() instanceof Player pl ? pl
			: e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player ps ? ps : null;
		if (p != null && isActive(p)) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onShoot(ProjectileLaunchEvent e) {
		if (e.getEntity().getShooter() instanceof Player p && isActive(p)) e.setCancelled(true);
	}

	/** 스킬·휘두르기를 다른 리스너보다 먼저 막는다 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onInteract(PlayerInteractEvent e) {
		if (!isActive(e.getPlayer())) return;
		Action a = e.getAction();
		if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK
			|| a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
			e.setCancelled(true);
			e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent e) {
		if (isActive(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onInteractAtEntity(org.bukkit.event.player.PlayerInteractAtEntityEvent e) {
		if (isActive(e.getPlayer())) e.setCancelled(true);
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent e) { release(e.getPlayer().getUniqueId()); }

	/** 서버가 스턴 도중 꺼졌을 때 플레이어 데이터에 남은 수식어 제거 */
	@EventHandler
	public void onJoin(PlayerJoinEvent e) { strip(e.getPlayer()); }
}
