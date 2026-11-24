package com.hobbang.enhance.weapon;

import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.boss.BossManager;
import com.hobbang.enhance.Keys;
import com.hobbang.enhance.boss.BU;
import com.hobbang.enhance.boss.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 크로노 낫. 강화에 따른 "늘어나는 양"만 바꾼다 (0강 기본값은 그대로):
 * 범위 증가 ×2, 정지 시간 증가 ×1.5. 스턴은 발광 → 0.2초 뒤 중력 정지, 끝나면 중력 복구.
 * 15강 웅크리고 우클릭: 5초 타이머(머리 위 시계) → 그동안 때린 몹과 피해 합 → 띵과 함께 1초 스턴·톱니 생성 → 1.5초 뒤 톱니 일격.
 * 시간 정지는 보스에게 적용되지 않는다.
 */
public final class ChronoSkill implements Listener {
	static final int TIMER_TICKS = 100, DING_DELAY = 30, SETTLE_STUN = 20;
	private static final NamespacedKey NOGRAV = Keys.k("chrono_nograv");
	private static final NamespacedKey NOAI = Keys.k("chrono_noai");

	/** 업보청산 추가 피해 비율: 15강 +10% → 20강 +50% (강화당 +8%p) */
	public static double karmaBonus(int lv) { return 0.10 + 0.08 * Math.max(0, lv - 15); }

	/** 범위: 기본 + (스킬 강화 단계 × 0.1 × 2). 기존 bossRadiusAdd(0.1/단계)의 2배. 3강 해금부터 증가 */
	static double radiusAdd(int lv) { return 0.2 * com.hobbang.enhance.item.EnhanceTier.bossSkillLv(lv); }
	/** 낫 베기 사거리 / 시간 정지 반경 (정지 반경은 원래 베기 증가량의 2배 → 여기서 다시 ×2) */
	static double slashReach(int lv) { return 5.5 + radiusAdd(lv); }
	static double fieldRadius(int lv) { return 8 + 2 * radiusAdd(lv); }
	/** 정지 시간(틱): 기본 80 + 단계당 4 → 6 (증가량 ×1.5). 3강 해금부터 증가 */
	static int stunTicks(int lv) { return 80 + 6 * com.hobbang.enhance.item.EnhanceTier.bossSkillLv(lv); }

	// ───────── 일반 스킬: 부채꼴 베기 + 시간 정지 ─────────
	static void slashAndStop(Player p, int lv, double mult, int tier) {
		World sl = p.getWorld();
		Vector look = p.getLocation().getDirection();
		Vector fw = new Vector(look.getX(), 0, look.getZ());
		if (fw.lengthSquared() < 1e-6) fw = new Vector(0, 0, 1);
		fw.normalize();
		double reach = slashReach(lv);
		Vector pc = p.getLocation().toVector();
		int cut = 0;
		for (LivingEntity t : WeaponListener.around(p, reach)) {
			Vector to = t.getLocation().toVector().subtract(pc).setY(0);
			if (to.lengthSquared() > reach * reach) continue;
			if (to.lengthSquared() > 0.5 && Math.toDegrees(to.angle(fw)) > 75) continue;
			WeaponListener.skillDamage(t, p, 7.0 * mult);
			t.setVelocity(t.getVelocity().add(fw.clone().multiply(0.35).setY(0.15)));
			cut++;
		}
		WeaponFx.scytheArc(sl, p, fw, reach, tier);
		int n = 0;
		int dur = stunTicks(lv);
		double fr = fieldRadius(lv);
		for (LivingEntity t : WeaponListener.around(p, fr)) {
			if (!BossManager.isBoss(t)) {
				stun(t, dur);
				n++;
				Location tl = t.getLocation();
				sl.spawnParticle(Particle.REVERSE_PORTAL, tl.getX(), tl.getY() + 1, tl.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
			}
			if (lv >= 10) WeaponListener.skillDamage(t, p, 4 * mult);
		}
		for (Entity en : sl.getNearbyEntities(p.getBoundingBox().expand(fr))) {
			if (en instanceof Projectile pr && pr.isValid() && !(pr.getShooter() instanceof Player pl && pl == p)) {
				freezeProjectile(pr, dur);
			}
		}
		WeaponFx.clockField(sl, p, p.getLocation().toVector(), tier, (float) fr);   // 법진 = 정지 범위에 딱 맞게
		p.sendActionBar(Component.text("☽ 낫 베기 " + cut + "마리 · ⏸ 시간 정지 " + n + "마리", NamedTextColor.AQUA));
	}

	// ───────── 스턴: AI + 중력 + 속도 완전 정지 → 끝나면 복구 ─────────
	private static final Map<UUID, BukkitTask> RESTORE = new HashMap<>();
	private static final Map<UUID, BukkitTask> LOCK = new HashMap<>();

	static void stun(LivingEntity t, int dur) {
		if (BossManager.isBoss(t)) return;
		t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, dur, 0));
		t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 9));
		t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, dur, 2));
		UUID id = t.getUniqueId();
		BukkitTask old = RESTORE.remove(id);
		if (old != null) old.cancel();
		BukkitTask oldLock = LOCK.remove(id);
		if (oldLock != null) oldLock.cancel();

		if (t instanceof org.bukkit.entity.Mob m) {
			if (!m.getPersistentDataContainer().has(NOAI, PersistentDataType.BYTE) && m.hasAI()) {
				m.setAI(false);
				m.getPersistentDataContainer().set(NOAI, PersistentDataType.BYTE, (byte) 1);
			}
			m.setTarget(null);
			m.getPathfinder().stopPathfinding();
		}
		if (!t.getPersistentDataContainer().has(NOGRAV, PersistentDataType.BYTE) && t.hasGravity()) {
			t.setGravity(false);
			t.getPersistentDataContainer().set(NOGRAV, PersistentDataType.BYTE, (byte) 1);
		}
		t.setVelocity(new Vector());
		t.setFallDistance(0);
		final Location pin = t.getLocation().clone();
		LOCK.put(id, Bukkit.getScheduler().runTaskTimer(HobbangPlugin.get(), () -> {
			if (!t.isValid() || t.isDead()) { BukkitTask lk = LOCK.remove(id); if (lk != null) lk.cancel(); return; }
			t.setVelocity(new Vector());
			t.setFallDistance(0);
			if (t.getLocation().distanceSquared(pin) > 0.04) t.teleport(pin);
		}, 1L, 1L));
		RESTORE.put(id, Bukkit.getScheduler().runTaskLater(HobbangPlugin.get(), () -> release(id), Math.max(5, dur)));
	}

	private static void freezeProjectile(Projectile pr, int dur) {
		final Vector savedVel = pr.getVelocity().clone();
		final boolean hadGrav = pr.hasGravity();
		pr.setGravity(false);
		pr.setVelocity(new Vector());
		final Location pin = pr.getLocation().clone();
		BukkitTask hold = Bukkit.getScheduler().runTaskTimer(HobbangPlugin.get(), () -> {
			if (!pr.isValid() || pr.isDead()) return;
			pr.setVelocity(new Vector());
			if (pr.getLocation().distanceSquared(pin) > 0.02) pr.teleport(pin);
		}, 1L, 1L);
		Bukkit.getScheduler().runTaskLater(HobbangPlugin.get(), () -> {
			hold.cancel();
			if (pr.isValid() && !pr.isDead()) {
				pr.setGravity(hadGrav);
				pr.setVelocity(savedVel);
			}
		}, Math.max(5, dur));
	}

	/** 이미 더 긴 크로노 스턴이 있으면 줄이지 않는다 */
	static void stunAtLeast(LivingEntity t, int dur) {
		if (RESTORE.containsKey(t.getUniqueId())) return;
		stun(t, dur);
	}

	private static void release(UUID id) {
		RESTORE.remove(id);
		BukkitTask lk = LOCK.remove(id);
		if (lk != null) lk.cancel();
		Entity e = Bukkit.getEntity(id);
		if (e instanceof LivingEntity le) restore(le);
	}

	private static void restore(LivingEntity le) {
		if (le.getPersistentDataContainer().has(NOGRAV, PersistentDataType.BYTE)) {
			le.setGravity(true);
			le.getPersistentDataContainer().remove(NOGRAV);
		}
		if (le instanceof org.bukkit.entity.Mob m && m.getPersistentDataContainer().has(NOAI, PersistentDataType.BYTE)) {
			m.setAI(true);
			m.getPersistentDataContainer().remove(NOAI);
		}
	}

	/** 서버 종료/플러그인 해제: 정지 중인 몹의 중력을 돌려놓는다 */
	static void shutdown() {
		for (UUID id : new ArrayList<>(RESTORE.keySet())) {
			BukkitTask t = RESTORE.get(id);
			if (t != null) t.cancel();
			release(id);
		}
		TIMERS.clear();
	}

	/** 정지 도중 청크가 언로드/서버가 꺼졌다면, 다시 로드될 때 중력을 복구 (스턴이 진행 중이면 제외) */
	@EventHandler
	public void onLoad(EntitiesLoadEvent e) {
		for (Entity en : e.getEntities())
			if (en instanceof LivingEntity le && !RESTORE.containsKey(le.getUniqueId())) restore(le);
	}

	// ───────── 15강: 5초 타이머 ─────────
	private static final class Timer {
		final Map<UUID, Double> mobs = new LinkedHashMap<>();
		double total;
	}

	private static final Map<UUID, Timer> TIMERS = new HashMap<>();
	private static boolean settling = false;

	/** 타이머 5초 동안 실제 피해는 0으로 만들고, 몹별로 들어갈 피해(스킬 포함)를 누적한다 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent e) {
		if (settling || TIMERS.isEmpty()) return;
		Player p = e.getDamager() instanceof Player pl ? pl
			: e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player ps ? ps : null;
		if (p == null) return;
		Timer t = TIMERS.get(p.getUniqueId());
		if (t == null || !(e.getEntity() instanceof LivingEntity le) || e.getEntity() instanceof Player) return;
		if (e.getFinalDamage() <= 0) return;
		double dmg = e.getDamage();
		if (dmg <= 0) return;
		t.mobs.merge(le.getUniqueId(), dmg, Double::sum);
		t.total += dmg;
		for (org.bukkit.event.entity.EntityDamageEvent.DamageModifier m : org.bukkit.event.entity.EntityDamageEvent.DamageModifier.values()) {
			if (m != org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE && e.isApplicable(m)) e.setDamage(m, 0);
		}
		e.setDamage(0);
		Location tl = le.getLocation();
		le.getWorld().spawnParticle(Particle.WAX_ON, tl.getX(), tl.getY() + le.getHeight() * 0.5, tl.getZ(), 4, 0.25, 0.35, 0.25, 0.15);
	}

	static void timer(Player p, int lv, int tier) {
		UUID pid = p.getUniqueId();
		if (TIMERS.containsKey(pid)) return;
		final double karmaMult = 1.0 + karmaBonus(lv);
		Timer state = new Timer();
		TIMERS.put(pid, state);
		World sl = p.getWorld();
		BU.sound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2F, 1.8F);
		Fx.spawn(sl, pid, clockPos(p), TIMER_TICKS + 10, (fx, w) -> {
			if (!p.isOnline() || p.isDead() || p.getWorld() != w) {   // 중도 이탈: 결과 없이 끝
				TIMERS.remove(pid);
				fx.kill();
				return;
			}
			if (fx.age == 1) buildClock(fx);
			// 머리 위를 따라다닌다
			Vector at = clockPos(p);
			fx.pos = at;
			Location l = new Location(w, at.getX(), at.getY(), at.getZ());
			for (Display d : fx.displays) if (d.isValid()) d.teleport(l);
			int age = Math.min(fx.age, TIMER_TICKS);
			if (fx.age <= TIMER_TICKS + 1 && (fx.age % 2 == 1 || fx.age >= TIMER_TICKS)) clockHand(fx.displays.get(2), (float) (Math.PI * 2 * age / TIMER_TICKS));
			if (fx.age <= TIMER_TICKS && fx.age % 20 == 0) BU.sound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.7F, 1.4F + 0.1F * (fx.age / 20));
			if (fx.age == TIMER_TICKS) {
				TIMERS.remove(pid);
				BU.sound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 2.0F, 1.5F);   // 띵
				BU.sound(p.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.2F, 1.9F);
				w.spawnParticle(Particle.END_ROD, at.getX(), at.getY(), at.getZ(), 14, 0.25, 0.25, 0.25, 0.06);
				w.spawnParticle(Particle.WAX_ON, at.getX(), at.getY(), at.getZ(), 14, 0.35, 0.35, 0.35, 0.3);
				p.sendActionBar(Component.text("⏱ 업보청산 — " + state.mobs.size() + "마리 · 정산 피해 " + String.format("%.1f", state.total * karmaMult) + " (+" + Math.round(karmaBonus(lv) * 100) + "%)", NamedTextColor.GOLD));
				settle(p, state, karmaMult, tier);
			}
		});
	}

	private static Vector clockPos(Player p) {
		return p.getLocation().toVector().add(new Vector(0, 2.55, 0));
	}

	/** 머리만 한(0.55) 시계: 금테 + 흰 판 + 4개 눈금 + 빨간 바늘 (바늘 인덱스 2). 전부 중심 기준 얇은 판이라 앞뒤 어느 쪽에서도 보인다 */
	private static void buildClock(Fx fx) {
		BlockDisplay rim = fx.addBlock(Material.GOLD_BLOCK.createBlockData(), true);
		BlockDisplay dial = fx.addBlock(Material.WHITE_CONCRETE.createBlockData(), true);
		BlockDisplay hand = fx.addBlock(Material.RED_CONCRETE.createBlockData(), true);
		BlockDisplay[] ticks = new BlockDisplay[4];
		for (int i = 0; i < 4; i++) ticks[i] = fx.addBlock(Material.BLACK_CONCRETE.createBlockData(), true);
		for (Display d : fx.displays) d.setBillboard(Display.Billboard.CENTER);
		Fx.shape(rim, new Vector3f(-0.275F, -0.275F, -0.02F), new Quaternionf(), new Vector3f(0.55F, 0.55F, 0.04F), 0);
		Fx.shape(dial, new Vector3f(-0.235F, -0.235F, -0.03F), new Quaternionf(), new Vector3f(0.47F, 0.47F, 0.06F), 0);
		for (int i = 0; i < 4; i++) {
			float a = (float) (i * Math.PI / 2);
			Quaternionf q = new Quaternionf().rotationZ(-a);
			Vector3f c = new Vector3f((float) Math.sin(a) * 0.19F, (float) Math.cos(a) * 0.19F, 0);
			Vector3f t = c.sub(new Vector3f(0.02F, 0.035F, 0.04F).rotate(q));
			Fx.shape(ticks[i], t, q, new Vector3f(0.04F, 0.07F, 0.08F), 0);
		}
		clockHand(hand, 0);
	}

	/** 바늘: 12시에서 시계 방향 angle (라디안). 한 바퀴 = 5초 */
	private static void clockHand(Display hand, float angle) {
		Quaternionf q = new Quaternionf().rotationZ(-angle);
		Vector3f t = new Vector3f(-0.018F, 0, -0.05F).rotate(q);
		Fx.shape(hand, t, q, new Vector3f(0.036F, 0.2F, 0.1F), 2);
	}

	/** 업보청산 종료: 기록된 모든 몹 1초 스턴, 그 순간 톱니 생성. 1.5초 뒤 각 몹에 누적된 피해를 한 번에 입힌다 */
	private static void settle(Player p, Timer t, double karmaMult, int tier) {
		for (UUID id : t.mobs.keySet()) {
			Entity e = Bukkit.getEntity(id);
			if (e instanceof LivingEntity le && !le.isDead() && le.isValid()) stunAtLeast(le, SETTLE_STUN);
		}
		if (t.total <= 0) return;
		int n = 0;
		for (var entry : t.mobs.entrySet()) {
			double mobDmg = entry.getValue();
			if (mobDmg <= 0) continue;
			Entity e = Bukkit.getEntity(entry.getKey());
			if (!(e instanceof LivingEntity le) || le.isDead() || !le.isValid()) continue;
			gear(p, le, mobDmg * karmaMult, tier);
			n++;
		}
		final int count = n;
		Fx.later(DING_DELAY, () -> {
			if (p.isOnline()) p.sendActionBar(Component.text("⚙ 업보청산 완료 (" + count + "마리)", NamedTextColor.GOLD));
		});
	}

	/** 몹 허리에 톱니가 바로 생기고, DING_DELAY(1.5초) 뒤에 반 바퀴 찰칵 — 돌기 시작하는 그 순간에 피해 */
	private static void gear(Player p, LivingEntity le, double dmg, int tier) {
		World sl = le.getWorld();
		double h = Math.max(0.4, le.getHeight() * 0.5);
		float r = (float) Math.max(0.9, le.getWidth() * 0.8 + 0.45);
		Color glow = Color.fromRGB(tier >= 2 ? 0xFFE070 : 0x60F0D8);
		Vector start = le.getLocation().toVector().add(new Vector(0, h, 0));
		double[] pos = { start.getX(), start.getY(), start.getZ() };
		int turn = DING_DELAY;
		Fx.spawn(sl, p.getUniqueId(), start, turn + 16, (fx, w) -> {
			if (le.isValid() && !le.isDead()) {
				Vector now = le.getLocation().toVector().add(new Vector(0, h, 0));
				pos[0] = now.getX(); pos[1] = now.getY(); pos[2] = now.getZ();
				fx.pos = now;
				Location l = new Location(w, pos[0], pos[1], pos[2]);
				for (Display d : fx.displays) if (d.isValid()) d.teleport(l);
			}
			if (fx.age == 1) {
				for (int i = 0; i < 8; i++) fx.addBlock((i % 2 == 0 ? Material.GOLD_BLOCK : Material.WAXED_CUT_COPPER).createBlockData(), true);
				fx.addBlock(Material.WAXED_CUT_COPPER.createBlockData(), true);
				fx.addBlock(Material.WAXED_CUT_COPPER.createBlockData(), true);
				for (Display d : fx.displays) { d.setGlowing(true); d.setGlowColorOverride(glow); }
				poseGear(fx, r, 0, 1);
				Location lockLoc = new Location(w, pos[0], pos[1], pos[2]);
				BU.sound(lockLoc, Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1.3F, 0.7F);
				BU.sound(lockLoc, Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.PLAYERS, 1.1F, 0.65F);
			} else if (fx.age >= turn && fx.age <= turn + 6 && (fx.age - turn) % 2 == 0) {
				int step = (fx.age - turn) / 2 + 1;
				poseGear(fx, r, (float) (Math.PI / 4 * step), 1);
				Location slLoc = new Location(w, pos[0], pos[1], pos[2]);
				BU.sound(slLoc, Sound.BLOCK_PISTON_EXTEND, SoundCategory.PLAYERS, 1.3F, 0.45F + 0.06F * step);
				BU.sound(slLoc, "hobbang:weapon.chrono.gear", SoundCategory.PLAYERS, 1.25F, 0.92F + 0.05F * step);
				if (step == 1 || step == 3) BU.sound(slLoc, Sound.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 1.1F, 0.48F + 0.04F * step);
				if (fx.age == turn) {
					BU.sound(slLoc, "hobbang:sentinel.gears", SoundCategory.PLAYERS, 1.3F, 0.85F);
					BU.sound(slLoc, Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.PLAYERS, 1.25F, 0.48F);
					if (le.isValid() && !le.isDead()) {
						settling = true;
						try {
							WeaponListener.skillHit(le, p, dmg);
						} finally {
							settling = false;
						}
						w.spawnParticle(Particle.CRIT, pos[0], pos[1], pos[2], 16, 0.4, 0.3, 0.4, 0.35);
					}
				}
			} else if (fx.age == turn + 9) {
				poseGear(fx, r, (float) Math.PI, 0);
			}
		});
	}

	/** 수평 톱니: 이빨 8개 + 허브. angle = Y축 회전, scale = 0/1 (나타남) */
	private static void poseGear(Fx fx, float r, float angle, float scale) {
		Quaternionf q = new Quaternionf().rotationY(angle);
		for (int i = 0; i < 8; i++) {
			float a = (float) (Math.PI * 2 * i / 8);
			float s = 0.34F * r * scale;
			Vector3f c = new Vector3f((float) Math.cos(a) * r * 0.82F, 0, (float) Math.sin(a) * r * 0.82F).rotate(q);
			Quaternionf tq = new Quaternionf(q).rotateY(-a);
			Vector3f t = c.sub(new Vector3f(s / 2, 0.06F * scale, s / 2).rotate(tq));
			Fx.shape(fx.displays.get(i), t, tq, new Vector3f(s, 0.12F * scale, s), 2);
		}
		float hub = 1.45F * r * 0.62F * scale;
		for (int k = 0; k < 2; k++) {
			Quaternionf hq = new Quaternionf(q).rotateY((float) (k * Math.PI / 4));
			Vector3f ht = new Vector3f(-hub / 2, -0.04F * scale, -hub / 2).rotate(hq);
			Fx.shape(fx.displays.get(8 + k), ht, hq, new Vector3f(hub, 0.08F * scale, hub), 2);
		}
	}
}
