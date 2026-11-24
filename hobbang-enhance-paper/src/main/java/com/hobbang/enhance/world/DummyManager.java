package com.hobbang.enhance.world;

import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.Keys;
import com.hobbang.enhance.weapon.WeaponListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 훈련용 더미 (Villager 외형):
 * - AI 없음, 이동 없음, 거래 UI 차단, 맞으면 즉시 체력 회복 (죽지 않음)
 * - 머리 위 TextDisplay 에 DPS, 이번 타격 데미지, 데미지 유형(근접/투사체/스킬/마법/낙뢰 등), 누적 데미지 표시
 * - 5초간 피격이 없으면 DPS 및 누적 데미지 초기화
 */
public final class DummyManager implements Listener {
	private static final NamespacedKey DUMMY_KEY = Keys.k("dummy");
	private static final NamespacedKey DUMMY_DISP = Keys.k("dummy_display");
	private static final long RESET_MS = 5000L;

	private static final class Stats {
		long firstHitMs;
		long lastHitMs;
		double total;
		double last;
		String type = "-";
	}

	private static final Map<UUID, Stats> STATS = new HashMap<>();

	private DummyManager() {}

	public static void init(HobbangPlugin plugin) {
		Bukkit.getPluginManager().registerEvents(new DummyManager(), plugin);
		Bukkit.getScheduler().runTaskTimer(plugin, DummyManager::tickIdleReset, 10L, 10L);
	}

	public static boolean isDummy(Entity e) {
		return e != null && e.getPersistentDataContainer().has(DUMMY_KEY, PersistentDataType.BYTE);
	}

	public static Villager spawn(Player p) {
		Location eye = p.getEyeLocation();
		Vector dir = eye.getDirection().setY(0);
		if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
		dir.normalize();
		Location at = p.getLocation().add(dir.multiply(2.5));
		float yaw = (float) Math.toDegrees(Math.atan2(p.getLocation().getZ() - at.getZ(), p.getLocation().getX() - at.getX())) - 90F;
		at.setYaw(yaw);
		at.setPitch(0F);
		World w = at.getWorld();
		Villager v = w.spawn(at, Villager.class, vil -> {
			vil.setAI(false);
			vil.setSilent(true);
			vil.setCollidable(false);
			vil.setRemoveWhenFarAway(false);
			vil.setCustomNameVisible(false);
			vil.getPersistentDataContainer().set(DUMMY_KEY, PersistentDataType.BYTE, (byte) 1);
			AttributeInstance hp = vil.getAttribute(Attribute.MAX_HEALTH);
			if (hp != null) { hp.setBaseValue(1024.0); vil.setHealth(1024.0); }
			AttributeInstance kb = vil.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
			if (kb != null) kb.setBaseValue(1.0);
		});
		TextDisplay td = w.spawn(at.clone().add(0, 2.35, 0), TextDisplay.class, d -> {
			d.setBillboard(Display.Billboard.CENTER);
			d.setBackgroundColor(Color.fromARGB(140, 10, 10, 20));
			d.setShadowed(true);
			d.getPersistentDataContainer().set(DUMMY_DISP, PersistentDataType.STRING, v.getUniqueId().toString());
			d.setTransformation(new Transformation(new Vector3f(0, 0.35F, 0), new Quaternionf(), new Vector3f(1F), new Quaternionf()));
			d.text(idleText());
		});
		v.addPassenger(td);
		return v;
	}

	public static int removeNear(Player p, double radius) {
		int count = 0;
		for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
			if (isDummy(e)) {
				for (Entity pass : e.getPassengers()) pass.remove();
				STATS.remove(e.getUniqueId());
				e.remove();
				count++;
			} else if (e instanceof TextDisplay && e.getPersistentDataContainer().has(DUMMY_DISP, PersistentDataType.STRING)) {
				e.remove();
			}
		}
		return count;
	}

	private static Component idleText() {
		return Component.text("훈련용 더미", NamedTextColor.GOLD)
			.appendNewline()
			.append(Component.text("타격하여 DPS 측정", NamedTextColor.GRAY));
	}

	private static Component statsText(Stats s, double dps) {
		return Component.text("훈련용 더미", NamedTextColor.GOLD)
			.appendNewline()
			.append(Component.text(String.format("DPS: %.1f  |  누적: %.1f", dps, s.total), NamedTextColor.YELLOW))
			.appendNewline()
			.append(Component.text(String.format("타격: %.1f (%s)", s.last, s.type), NamedTextColor.AQUA));
	}

	private static TextDisplay findDisplay(Villager v) {
		for (Entity e : v.getPassengers()) {
			if (e instanceof TextDisplay td) return td;
		}
		return null;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInteract(PlayerInteractEntityEvent e) {
		if (isDummy(e.getRightClicked())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent e) {
		if (!(e.getEntity() instanceof Villager v) || !isDummy(v)) return;
		if (e.getCause() == EntityDamageEvent.DamageCause.VOID || e.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
			e.setCancelled(true);
			return;
		}
		double finalDmg = e.getFinalDamage();
		if (finalDmg >= v.getHealth()) {
			e.setDamage(Math.max(0, v.getHealth() - 1.0));
		}
		String type = classify(e);
		long now = System.currentTimeMillis();
		Stats s = STATS.computeIfAbsent(v.getUniqueId(), k -> new Stats());
		if (now - s.lastHitMs > RESET_MS || s.firstHitMs == 0) {
			s.firstHitMs = now;
			s.total = 0;
		}
		s.lastHitMs = now;
		s.last = finalDmg;
		s.total += finalDmg;
		s.type = type;
		double sec = Math.max(1.0, (now - s.firstHitMs) / 1000.0);
		double dps = s.total / sec;
		TextDisplay td = findDisplay(v);
		if (td != null) td.text(statsText(s, dps));
		Bukkit.getScheduler().runTask(HobbangPlugin.get(), () -> {
			if (v.isValid() && !v.isDead()) {
				AttributeInstance hp = v.getAttribute(Attribute.MAX_HEALTH);
				v.setHealth(hp != null ? hp.getValue() : 1024.0);
				v.setNoDamageTicks(0);
			}
		});
	}

	private static String classify(EntityDamageEvent e) {
		if (WeaponListener.isSkillDamage()) return "스킬";
		EntityDamageEvent.DamageCause cause = e.getCause();
		if (cause == EntityDamageEvent.DamageCause.LIGHTNING) return "낙뢰";
		if (cause == EntityDamageEvent.DamageCause.MAGIC || cause == EntityDamageEvent.DamageCause.THORNS
			|| cause == EntityDamageEvent.DamageCause.SONIC_BOOM || cause == EntityDamageEvent.DamageCause.WITHER
			|| cause == EntityDamageEvent.DamageCause.POISON) return "마법";
		if (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK
			|| cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) return "화염";
		if (e instanceof EntityDamageByEntityEvent be) {
			if (be.getDamager() instanceof Projectile) return "투사체";
			if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return "근접";
		}
		return e.getDamageSource().getDamageType().getKey().getKey();
	}

	private static void tickIdleReset() {
		if (STATS.isEmpty()) return;
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<UUID, Stats>> it = STATS.entrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			if (now - entry.getValue().lastHitMs <= RESET_MS) continue;
			Entity e = Bukkit.getEntity(entry.getKey());
			if (e instanceof Villager v && isDummy(v)) {
				TextDisplay td = findDisplay(v);
				if (td != null) td.text(idleText());
			}
			it.remove();
		}
	}
}
