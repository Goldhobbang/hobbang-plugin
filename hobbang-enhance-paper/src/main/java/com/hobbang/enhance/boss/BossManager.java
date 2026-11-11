package com.hobbang.enhance.boss;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** 보스 컨트롤러 관리: 소환/부착/틱/이벤트 라우팅. 엔티티 전수 스캔 없이 add/remove 이벤트로만 추적 */
public final class BossManager implements Listener {
	private static final Map<UUID, Boss> BOSSES = new HashMap<>();
	private static final java.util.List<Boss> DYING = new java.util.ArrayList<>();
	private static final Random RNG = new Random();

	private BossManager() {}

	public static void init(HobbangPlugin plugin) {
		Bukkit.getPluginManager().registerEvents(new BossManager(), plugin);
		// 이미 로드된 보스(리로드 직후) 부착 — 활성화 시 1회만
		for (World w : Bukkit.getWorlds())
			for (Mob m : w.getEntitiesByClass(Mob.class)) if (kindOf(m) != null) attach(m);
		Bukkit.getScheduler().runTaskTimer(plugin, BossManager::tick, 1, 1);
	}

	public static void shutdown() {
		for (Boss b : BOSSES.values()) b.unload();
		BOSSES.clear();
		for (Boss b : DYING) b.unload();
		DYING.clear();
		Fx.clearAll();
		Ritual.clear();
	}

	private static void tick() {
		if (!BOSSES.isEmpty()) {
			for (Boss b : new ArrayList<>(BOSSES.values())) {
				boolean dying = b instanceof DisplayBoss db && db.dying && !b.dead;
				if (!b.mob.isValid() && !dying) continue;
				try {
					b.tick();
				} catch (Exception ex) {
					HobbangPlugin.get().getLogger().warning("boss tick error (" + b.kind.id + "): " + ex);
				}
			}
		}
		if (!DYING.isEmpty()) {
			for (Boss b : new ArrayList<>(DYING)) {
				if (b.dead) { DYING.remove(b); continue; }
				try { b.tick(); }
				catch (Exception ex) {
					HobbangPlugin.get().getLogger().warning("boss death tick error (" + b.kind.id + "): " + ex);
				}
			}
		}
		Fx.tickAll();
		Ritual.tick();
	}

	public static BossKind kindOf(Entity e) {
		if (!(e instanceof Mob m)) return null;
		String id = m.getPersistentDataContainer().get(Keys.BOSS, PersistentDataType.STRING);
		return id == null ? null : BossKind.byId(id);
	}

	public static boolean isBoss(Entity e) {
		return e != null && (BOSSES.containsKey(e.getUniqueId()) || kindOf(e) != null);
	}

	public static Boss get(Entity e) {
		return e == null ? null : BOSSES.get(e.getUniqueId());
	}

	public static Map<UUID, Boss> all() {
		return BOSSES;
	}

	public static boolean bossNear(Location loc, double r) {
		for (Boss b : BOSSES.values()) {
			if (b.dead || !b.mob.isValid() || b.mob.getWorld() != loc.getWorld()) continue;
			Location m = b.mob.getLocation();
			if (Math.abs(m.getX() - loc.getX()) <= r && Math.abs(m.getY() - loc.getY()) <= r && Math.abs(m.getZ() - loc.getZ()) <= r) return true;
		}
		return false;
	}

	/** 히트박스 몹 공통 설정 (AI/목표는 저장되지 않으므로 부착 때마다 적용) */
	private static void configure(Mob m, BossKind kind) {
		m.setPersistent(true);
		m.setRemoveWhenFarAway(false);
		m.setCanPickupItems(false);
		m.setVisualFire(false);
		// v1.3.0: 보스 크기 변경 — 이미 소환돼 있던 1.2.0 보스도 붙을 때 새 크기로
		attr(m, Attribute.SCALE, kind.scale);
		if (kind == BossKind.ABYSSAL_SMITH) attr(m, Attribute.STEP_HEIGHT, 2.5);
		m.setInvisible(true);   // v1.4.2: 대장장이도 소환 직후 숨김 (조립 연출 전 위더 스켈레톤이 한 틱 비치지 않게)
		if (kind != BossKind.ABYSSAL_SMITH) {
			m.setSilent(true);
			Bukkit.getMobGoals().removeAllGoals(m);
		} else {
			m.setSilent(false);
		}
		if (kind == BossKind.TEMPEST_WYVERN || kind == BossKind.CHRONO_SENTINEL) {
			m.setAware(false);
			m.setGravity(false);
		}
		if (m instanceof Wolf w) {
			w.setAngry(false);
			w.setSitting(false);
		}
	}

	private static Boss create(Mob m, BossKind kind) {
		return switch (kind) {
			case ABYSSAL_SMITH -> new SmithBoss(m);
			case PRISM_COLOSSUS -> new ColossusBoss(m);
			case TEMPEST_WYVERN -> new WyvernBoss(m);
			case CHRONO_SENTINEL -> new SentinelBoss(m);
		};
	}

	private static Boss attach(Mob m) {
		Boss existing = BOSSES.get(m.getUniqueId());
		if (existing != null) return existing;
		BossKind kind = kindOf(m);
		if (kind == null || m.isDead()) return null;
		configure(m, kind);
		Boss b = create(m, kind);
		b.arena = com.hobbang.enhance.arena.ArenaRef.read(m);
		BOSSES.put(m.getUniqueId(), b);
		return b;
	}

	private static void attr(Mob m, Attribute a, double v) {
		AttributeInstance i = m.getAttribute(a);
		if (i != null) i.setBaseValue(v);
	}

	/** 보스 소환 (의식 / 명령어). pos = 블록 위 좌표 */
	public static Mob spawn(BossKind kind, Location pos) {
		World w = pos.getWorld();
		Location at = pos.getBlock().getLocation().add(0.5, kind == BossKind.ABYSSAL_SMITH ? 0 : kind.lift, 0.5);
		at.setYaw(kind == BossKind.ABYSSAL_SMITH ? 0 : RNG.nextFloat() * 360);
		at.setPitch(0);
		Entity raw = w.spawnEntity(at, kind.hitbox, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
			Mob m = (Mob) e;
			m.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.STRING, kind.id);
			m.getPersistentDataContainer().set(Keys.PHASE, PersistentDataType.INTEGER, 1);
			m.customName(Component.text(kind.name));
			m.setCustomNameVisible(false);
			attr(m, Attribute.SCALE, kind.scale);
			attr(m, Attribute.KNOCKBACK_RESISTANCE, 1.0);
			attr(m, Attribute.FOLLOW_RANGE, 48.0);
			switch (kind) {
				case ABYSSAL_SMITH -> {
					attr(m, Attribute.MAX_HEALTH, 600); attr(m, Attribute.ARMOR, 12); attr(m, Attribute.ARMOR_TOUGHNESS, 8);
					attr(m, Attribute.ATTACK_DAMAGE, 14); attr(m, Attribute.ATTACK_KNOCKBACK, 1.5);
					attr(m, Attribute.MOVEMENT_SPEED, 0.3); attr(m, Attribute.STEP_HEIGHT, 2.5);
					SmithBoss.equip(m);
				}
				case PRISM_COLOSSUS -> {
					attr(m, Attribute.MAX_HEALTH, 900); attr(m, Attribute.ARMOR, 16); attr(m, Attribute.ARMOR_TOUGHNESS, 10);
					attr(m, Attribute.ATTACK_DAMAGE, 16); attr(m, Attribute.ATTACK_KNOCKBACK, 2.0);
					attr(m, Attribute.MOVEMENT_SPEED, 0.24); attr(m, Attribute.STEP_HEIGHT, 2.5);
				}
				case TEMPEST_WYVERN -> {
					attr(m, Attribute.MAX_HEALTH, 700); attr(m, Attribute.ARMOR, 8); attr(m, Attribute.ARMOR_TOUGHNESS, 6);
					attr(m, Attribute.ATTACK_DAMAGE, 12); attr(m, Attribute.MOVEMENT_SPEED, 0.3); attr(m, Attribute.FOLLOW_RANGE, 64.0);
				}
				case CHRONO_SENTINEL -> {
					attr(m, Attribute.MAX_HEALTH, 800); attr(m, Attribute.ARMOR, 10); attr(m, Attribute.ARMOR_TOUGHNESS, 8);
					attr(m, Attribute.ATTACK_DAMAGE, 10); attr(m, Attribute.MOVEMENT_SPEED, 0.25);
				}
			}
			m.setHealth(m.getAttribute(Attribute.MAX_HEALTH).getValue());
			configure(m, kind);
		});
		Mob m = (Mob) raw;
		Boss spawned = attach(m);
		if (spawned instanceof DisplayBoss db) db.startAssemble();
		com.hobbang.enhance.arena.ArenaDress.start(spawned);
		int x = at.getBlockX(), y = at.getBlockY(), z = at.getBlockZ();
		if (kind == BossKind.ABYSSAL_SMITH) {
			BU.sound(at, Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 3.0F, 0.7F);
			Bukkit.broadcast(Component.text("☠ 심연의 대장장이가 깨어났습니다! ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
				.append(Component.text("(" + x + ", " + y + ", " + z + ")", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
		} else {
			BU.sound(at, Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 3.0F, 0.9F);
			w.spawnParticle(Particle.EXPLOSION_EMITTER, at.getX(), at.getY() + 1, at.getZ(), 2, 0.5, 0.5, 0.5, 0);
			Bukkit.broadcast(Component.text("☠ ", NamedTextColor.DARK_PURPLE)
				.append(Component.text(kind.name, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
				.append(Component.text("이(가) 깨어났습니다! ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
				.append(Component.text("(" + x + ", " + y + ", " + z + ")", NamedTextColor.GRAY)));
		}
		return m;
	}

	// ───────── 이벤트 ─────────
	@EventHandler
	public void onAdd(EntityAddToWorldEvent e) {
		if (e.getEntity() instanceof Mob m && kindOf(m) != null) {
			// 엔티티 추가 도중 디스플레이를 소환하지 않도록 다음 틱에 부착
			Bukkit.getScheduler().runTask(HobbangPlugin.get(), () -> { if (m.isValid()) attach(m); });
		}
	}

	@EventHandler
	public void onRemove(EntityRemoveFromWorldEvent e) {
		Boss b = BOSSES.remove(e.getEntity().getUniqueId());
		if (b instanceof DisplayBoss db && db.dying && !db.dead) {
			DYING.add(b);
			return; // 사망 연출 동안 파츠를 유지한다
		}
		if (b != null) b.unload();
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent e) {
		Entity ent = e.getEntity();
		Boss b = BOSSES.get(ent.getUniqueId());
		if (b == null) return;
		switch (e.getCause()) {
			case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE -> { e.setCancelled(true); ent.setFireTicks(0); return; }
			default -> {}
		}
		b.onDamage(e);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onAttack(EntityDamageByEntityEvent e) {
		Boss b = BOSSES.get(e.getDamager().getUniqueId());
		if (b != null) b.onAttack(e);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onDeath(EntityDeathEvent e) {
		Boss b = BOSSES.get(e.getEntity().getUniqueId());
		if (b == null) return;
		b.onDeath(e);
	}

	@EventHandler(ignoreCancelled = true)
	public void onTarget(EntityTargetEvent e) {
		Entity src = e.getEntity(), tgt = e.getTarget();
		if (tgt == null) return;
		if (isBoss(src) && !(tgt instanceof Player)) { e.setCancelled(true); return; }
		String owner = src.getPersistentDataContainer().get(Keys.MINION, PersistentDataType.STRING);
		if (owner != null && (isBoss(tgt) || tgt.getPersistentDataContainer().has(Keys.MINION))) e.setCancelled(true);
		if (isBoss(tgt) && src instanceof Mob && !(src instanceof org.bukkit.entity.IronGolem)) {
			// 하수인/주변 몹이 보스를 노리지 않게
			if (owner != null) e.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onCombust(EntityCombustEvent e) {
		if (isBoss(e.getEntity())) e.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onInteract(PlayerInteractEntityEvent e) {
		Entity t = e.getRightClicked();
		if (isBoss(t) || (t instanceof Display && t.getScoreboardTags().contains(Fx.PART_TAG))) e.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onInteractAt(PlayerInteractAtEntityEvent e) {
		if (isBoss(e.getRightClicked())) e.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onPortal(EntityPortalEvent e) {
		if (isBoss(e.getEntity())) e.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onTransform(EntityTransformEvent e) {
		if (isBoss(e.getEntity())) e.setCancelled(true);
	}

	/** 남은 디스플레이 정리 (명령어). 파츠/FX는 비영구라 원래 남지 않지만 수동 정리 수단 제공 */
	public static int cleanDisplays() {
		int n = 0;
		for (World w : Bukkit.getWorlds()) {
			for (Display d : w.getEntitiesByClass(Display.class)) {
				if (!(d.getScoreboardTags().contains(Fx.FX_TAG) || d.getScoreboardTags().contains(Fx.PART_TAG))) continue;
				if (d.getVehicle() instanceof LivingEntity v && BOSSES.containsKey(v.getUniqueId())) continue;
				d.remove();
				n++;
			}
		}
		return n;
	}

	public static int countDisplays() {
		int n = 0;
		for (World w : Bukkit.getWorlds())
			for (Display d : w.getEntitiesByClass(Display.class))
				if (d.getScoreboardTags().contains(Fx.FX_TAG) || d.getScoreboardTags().contains(Fx.PART_TAG)) n++;
		return n;
	}
}
