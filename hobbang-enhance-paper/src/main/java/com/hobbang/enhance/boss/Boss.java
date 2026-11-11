package com.hobbang.enhance.boss;

import com.hobbang.enhance.Adv;
import com.hobbang.enhance.Keys;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 보스 컨트롤러 공통: 바닐라 몹(히트박스) 하나에 붙어 매 틱 AI/패턴을 돌린다.
 * 상태(종류/페이즈)는 몹 PDC 에 저장 → 청크 리로드/재시작 후 다시 붙는다.
 */
public abstract class Boss {
	public final Mob mob;
	public final BossKind kind;
	public final UUID uuid;
	protected final BossBar bar;
	private final Set<Player> viewers = new HashSet<>();
	protected int phase;
	protected int invulTicks = 0;
	protected int regenTick = 0;
	protected int tickCount = 0;
	protected final Random random = new Random();
	public boolean dead = false;
	/** v1.4.0: 묶인 투기장 (구조물 제단/폭풍 제단으로 소환됐을 때) */
	public com.hobbang.enhance.arena.ArenaRef arena;

	protected Boss(Mob mob, BossKind kind) {
		this.mob = mob;
		this.kind = kind;
		this.uuid = mob.getUniqueId();
		this.phase = mob.getPersistentDataContainer().getOrDefault(Keys.PHASE, PersistentDataType.INTEGER, 1);
		this.bar = BossBar.bossBar(Component.text(kind.name), 1f, phaseColor(phase), BossBar.Overlay.NOTCHED_10);
		if (phase > phaseThresholds().length) bar.addFlag(BossBar.Flag.DARKEN_SCREEN);
	}

	protected abstract float[] phaseThresholds();
	protected abstract BossBar.Color phaseColor(int phase);
	protected abstract void dropLoot(List<ItemStack> drops, Random r);

	public int getPhase() {
		return phase;
	}

	protected void setPhase(int p) {
		if (p > phase) { int[] c = BossPatterns.colors(kind); BossPatterns.phaseBurst(this, c[0], c[1]); }
		phase = p;
		mob.getPersistentDataContainer().set(Keys.PHASE, PersistentDataType.INTEGER, p);
	}

	public double health() {
		return mob.getHealth();
	}

	public double maxHealth() {
		AttributeInstance a = mob.getAttribute(Attribute.MAX_HEALTH);
		return a == null ? 1 : a.getValue();
	}

	public void heal(double amount) {
		mob.setHealth(Math.min(maxHealth(), mob.getHealth() + amount));
	}

	/** 매 틱 (BossManager) */
	public void tick() {
		tickCount++;
		if (mob.isValid()) {
			if (tickCount % 10 == 1) updateViewers();
			if (!dead) bar.progress((float) Math.max(0, Math.min(1, health() / maxHealth())));
		}
		serverTick();
	}

	protected abstract void serverTick();

	private void updateViewers() {
		Location l = mob.getLocation();
		for (Player p : mob.getWorld().getPlayers()) {
			boolean in = p.getLocation().distanceSquared(l) < 96 * 96;
			if (in && viewers.add(p)) p.showBossBar(bar);
			else if (!in && viewers.remove(p)) p.hideBossBar(bar);
		}
		viewers.removeIf(p -> {
			if (!p.isOnline() || p.getWorld() != mob.getWorld()) { p.hideBossBar(bar); return true; }
			return false;
		});
	}

	public void hideBar() {
		for (Player p : viewers) p.hideBossBar(bar);
		viewers.clear();
	}

	/** 청크 언로드 / 플러그인 종료 */
	public void unload() {
		hideBar();
		Fx.discardOwned(uuid);
	}

	// ───────── 피해 ─────────
	public abstract void onDamage(EntityDamageEvent e);

	/** 보스가 때린 뒤 (Fabric doHurtTarget) */
	public void onAttack(EntityDamageByEntityEvent e) {}

	public void onDeath(EntityDeathEvent e) {
		if (dead) return;
		dead = true;
		bar.progress(0);
		com.hobbang.enhance.arena.ArenaDress.restore(this);
		Fx.discardOwned(uuid);
		e.getDrops().clear();
		e.setDroppedExp(0);
		settleRewards(mob.getLocation(), killerOf(e.getEntity().getKiller(), e));
		hideBar();
	}

	protected static Player killerOf(Player direct, EntityDeathEvent e) {
		if (direct != null) return direct;
		return e.getEntity().getKiller();
	}

	protected abstract int xpReward();

	/** 보상: 바닥에 빛나는(팀 색 윤곽선) 영구 아이템으로 떨군다 */
	protected void spawnLoot(EntityDeathEvent e) {
		e.getDrops().clear();
		spawnLootAt(mob.getLocation());
	}

	protected void spawnLootAt(org.bukkit.Location at) {
		List<ItemStack> loot = new java.util.ArrayList<>();
		dropLoot(loot, random);
		com.hobbang.enhance.world.ModLoot.rollSuperiorBoss(loot);
		BossLoot.drop(at, loot);
	}

	protected Component deathLine(Component who) {
		return Component.text("⚔ [홉방] ", NamedTextColor.GOLD).append(who.colorIfAbsent(NamedTextColor.YELLOW))
			.append(Component.text("님이 ", NamedTextColor.WHITE)).append(Component.text(kind.name, NamedTextColor.LIGHT_PURPLE))
			.append(Component.text("을(를) 쓰러뜨렸습니다!", NamedTextColor.WHITE));
	}

	protected void awardKiller(Player killer) {
		if (killer == null) return;
		Adv.award(killer, kind.advancement);
		Adv.criterion(killer, "master", kind.id);
	}

	/** 연출이 끝난 뒤(또는 즉시 사망 시) 루트·경험치·방송·업적. 투기장 복구는 호출 측에서. */
	protected void settleRewards(org.bukkit.Location at, Player killer) {
		spawnLootAt(at);
		int xp = xpReward();
		if (xp > 0 && at.getWorld() != null) {
			at.getWorld().spawn(at, org.bukkit.entity.ExperienceOrb.class, o -> o.setExperience(xp));
		}
		Component who = killer != null ? killer.displayName() : Component.text("누군가");
		Bukkit.broadcast(deathLine(who));
		awardKiller(killer);
	}

	// ───────── 대상 ─────────
	public List<Player> nearbyPlayers(double r) {
		return BU.nearbyPlayers(mob, r);
	}

	protected boolean isTargetable(LivingEntity t) {
		if (t == null || t.isDead() || !t.isValid() || t.getWorld() != mob.getWorld()) return false;
		if (t instanceof Player pl && (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR)) return false;
		return t.getLocation().distanceSquared(mob.getLocation()) < 64 * 64;
	}

	protected static boolean fromSelf(EntityDamageEvent e, Entity self) {
		if (e instanceof EntityDamageByEntityEvent be) {
			Entity d = be.getDamager();
			if (d == self) return true;
			if (d instanceof org.bukkit.entity.Projectile pr && pr.getShooter() == self) return true;
		}
		return e.getDamageSource().getCausingEntity() == self;
	}
}
