package com.hobbang.enhance.boss;

import static com.hobbang.enhance.boss.BossMotion.Move.*;

import com.hobbang.enhance.item.HItem;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** 크로노 센티넬 — 시간을 지키는 태엽 감시자 (부유형). 히트박스 = 투명 늑대(AI 끔, 중력 끔) */
public class SentinelBoss extends DisplayBoss {
	private static final int BOLT_CD = 140, SWEEP_CD = 200, RING_CD = 240, ECHO_CD = 360, BLADE_CD = 400;
	private static final int REWIND_CD = 640, STOP_CD = 1200;
	private float sweepCd = SWEEP_CD, boltCd = BOLT_CD;
	private int stopCd = STOP_CD, rewindCd = REWIND_CD, ringCd = RING_CD, echoCd = ECHO_CD, bladeCd = BLADE_CD;
	private int rewindUnlockedAt = -1;
	private float orbitAngle = Float.NaN;
	private int stopTimer = 0, rewindTimer = 0, stunTimer = 0;
	private float rewindDamage = 0;
	private static final float REWIND_BREAK = 80;

	private Part core, face, hourHand, minHand, bell, pendRod, pendBob, label;
	private final Part[] ringA = new Part[8];
	private final Part[] ringB = new Part[10];
	/** v1.3.0 리소스팩 모델 파츠 (톱니 고리 / 시계 코어는 따로 회전) */
	private Part mBody, mRing, mClock, mArmL, mArmR;
	/** v1.5.0: 하부 진자(모래시계)와 팔 칼날을 따로 움직인다 */
	private Part mPend, mBladeL, mBladeR;
	/** golem remodel: cogs beside the dial, meshing with the clock (spin with the gear angle; stop / reverse with it) */
	private Part mGearL, mGearR;
	private Rigid coreRig;
	private float gearAngle, gearSpeed, deathU, tilt;
	private int gearHoldUntil, lastDeathFrame;
	private int phaseClockUntil;
	private Fx skillClock;
	private static final float LEGACY = 5.6F / (2.0F / 0.6F);

	public SentinelBoss(Mob mob) {
		super(mob, BossKind.CHRONO_SENTINEL);
	}

	@Override protected float[] phaseThresholds() { return new float[]{0.6F, 0.3F}; }

	@Override
	protected BossBar.Color phaseColor(int p) {
		return p == 1 ? BossBar.Color.YELLOW : p == 2 ? BossBar.Color.GREEN : BossBar.Color.RED;
	}

	@Override protected boolean glowParts() { return phase >= 3; }
	@Override protected int glowColor() { return 0x40FFD0; }
	@Override protected float damageMultiplier() { return stunTimer > 0 ? 1.5F : 1.0F; }
	@Override protected int xpReward() { return 1000; }
	@Override protected float legacyScale() { return LEGACY; }

	private static BlockData bd(Material m) { return m.createBlockData(); }
	private static float sin(double f) { return (float) Math.sin(f); }

	private BlockData copper(int ring) {
		return switch (phase) {
			case 1 -> ring == 0 ? bd(Material.CUT_COPPER) : bd(Material.EXPOSED_CUT_COPPER);
			case 2 -> ring == 0 ? bd(Material.WEATHERED_CUT_COPPER) : bd(Material.EXPOSED_CUT_COPPER);
			default -> bd(Material.OXIDIZED_CUT_COPPER);
		};
	}

	// ───────── 모델 ─────────
	@Override
	protected void buildParts() {
		core = face = hourHand = minHand = bell = pendRod = pendBob = label = null;
		mBody = mRing = mClock = mArmL = mArmR = mPend = mBladeL = mBladeR = mGearL = mGearR = null;
		java.util.Arrays.fill(ringA, null);
		java.util.Arrays.fill(ringB, null);
		if (modelMode) {
			mBody = energyModel("body", false, true); mArmL = model("arm_l", false); mArmR = model("arm_r", false);
			mRing = energyModel("ring", false, false); mClock = model("clock", true);
			mPend = model("pendulum", false); mBladeL = model("blade_l", false); mBladeR = model("blade_r", false);
			mGearL = model("gear_l", false); mGearR = model("gear_r", false);
			label = text(Component.empty(), 0, 0, 0, 1.6F);
			return;
		}
		core = block(bd(Material.GOLD_BLOCK), -0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, false);
		face = item(new ItemStack(Material.CLOCK), 0, 0, 0.53F, 1.3F, true);
		hourHand = block(bd(Material.BLACK_CONCRETE), -0.04F, 0F, 0F, 0.08F, 0.35F, 0.03F, false);
		minHand = block(bd(Material.RED_CONCRETE), -0.03F, 0F, 0F, 0.06F, 0.5F, 0.03F, true);
		bell = block(bd(Material.GOLD_BLOCK), -0.3F, 0F, -0.3F, 0.6F, 0.35F, 0.6F, false);
		pendRod = block(bd(Material.IRON_BARS), -0.5F, -1.3F, -0.5F, 1.0F, 1.3F, 1.0F, false);
		pendBob = block(bd(Material.GOLD_BLOCK), -0.25F, -1.7F, -0.1F, 0.5F, 0.5F, 0.2F, true);
		for (int i = 0; i < ringA.length; i++) ringA[i] = block(copper(0), -0.3F, -0.14F, -0.14F, 0.6F, 0.28F, 0.28F, false);
		for (int i = 0; i < ringB.length; i++) ringB[i] = block(copper(1), -0.25F, -0.12F, -0.12F, 0.5F, 0.24F, 0.24F, false);
		label = text(Component.empty(), 0, 0, 0, 1.2F);
	}

	@Override
	protected void onPartsRebuilt() {
		refreshGlow();
	}

	@Override
	protected void poseParts(float t) {
		float tooth = (float) Math.floor(gearAngle / 12);
		float gear = (tooth + BossMotion.ease(gearAngle / 12 - tooth)) * 12;
		if (modelMode) { poseModel(gear); return; }
		Rigid root = Rigid.yaw(modelYaw());
		float cycle = (float) Math.toRadians(gear);
		Rigid c = root.at(0, 1.6F + idle.wave(0.064F, 0) * 0.13F, 0);
		coreRig = root.at(0, 1.6F, 0);
		place(core, c.rotY(gear * 0.5F));
		place(face, c);
		Rigid fc = c.at(0, 0, 0.62F);
		place(hourHand, fc.rotZ(-gear * 0.5F));
		place(minHand, fc.rotZ(-gear * 4));
		place(bell, c.at(0, 0.5F, 0));
		Rigid pend = c.at(0, -0.5F, 0).rotX(sin(cycle * 2) * 28);
		place(pendRod, pend);
		place(pendBob, pend);
		for (int i = 0; i < ringA.length; i++) {
			float a = gear + i * 360F / ringA.length;
			place(ringA[i], c.rotY(a).at(0, 0, 1.6F).rotY(90));
		}
		for (int i = 0; i < ringB.length; i++) {
			float a = -gear * 4 / 3 + i * 360F / ringB.length;
			place(ringB[i], c.rotX(65).rotZ(20).rotY(a).at(0, 0, 2.0F).rotY(90));
		}
		place(label, root.at(0, 3.6F, 0));
	}

	private void poseModel(float gear) {
		aimModel(3.5F, 35, 30, 0.28F);
		tilt = BossMotion.mix(tilt, stunTimer > 0 ? 12 : idle.wave(0.071F, 0.42F) * 2.5F, 0.27F);
		BossMotion.Frame channel = motion(CHANNEL);
		if (!dying && rewindTimer > 0) {
			channel = channel.blend(BossMotion.frame(160 - rewindTimer, 160, 0, 12));
		} else if (!dying && stopTimer > 0) {
			channel = channel.blend(BossMotion.frame(STOP_TOTAL - stopTimer, STOP_WARN + 1, STOP_GAZE, 12));
		}
		coreRig = BossPoses.sentinel(this::placeModel, idle, modelYaw(), gear, tilt, lookYaw, lookPitch, deathU,
			motion(CAST), motion(CAST, 2), motion(SWEEP), channel, motion(PHASE));
		place(label, Rigid.yaw(modelYaw()).at(0, 5.7F, 0), Math.max(0.05F, 1 - BossMotion.ease((deathU - 0.92F) / 0.08F)));
	}

	private void advanceGears() {
		if ((!idle.active() && rewindTimer <= 0) || stunTimer > 0 || tickCount < gearHoldUntil) {
			gearSpeed = 0;
			return;
		}
		float desired = rewindTimer > 0 ? -2.4F : phase >= 3 ? 1.45F : 1;
		if (stopTimer > STOP_GAZE) {
			desired *= BossMotion.ease((stopTimer - STOP_GAZE) / (float) STOP_WARN);
		}
		gearSpeed = BossMotion.mix(gearSpeed, desired, 0.22F);
		gearAngle = (gearAngle + 3.8F * gearSpeed) % 2160; // common period of all gear ratios
	}

	@Override
	protected boolean energySurge(String part) {
		return motion(CAST).prepare() > 0.4F || motion(CAST).release() > 0.85F;
	}

	/** 패턴 시작점 = 시계 코어 */
	private Vector coreAt() {
		if (coreRig != null) return worldPoint(coreRig, 0, 0, 0);
		return mob.getLocation().toVector().add(new Vector(0, 1.6 * vs, 0));
	}

	private boolean clockActive() {
		return rewindTimer > 0 || stopTimer > 0 || tickCount < phaseClockUntil;
	}

	private void showClock() {
		if (skillClock == null || skillClock.dead) {
			skillClock = Fx.clock(this, this::clockAnchor, this::clockAngle, this::clockActive);
		}
	}

	private Vector clockAnchor() {
		double yaw = Math.toRadians(modelYaw());
		Vector dial = modelPoint("clock", 0, 0, 0);
		Vector at = (dial == null ? coreAt() : dial).add(new Vector(-Math.sin(yaw) * 2.15, 1.55, Math.cos(yaw) * 2.15));
		if (mob.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return at;
		return mob.getLocation().toVector().add(new Vector(0, mob.getHeight() + 1.55, 0));
	}

	private double clockAngle() {
		if (rewindTimer > 0) return -(160 - rewindTimer) * Math.PI / 20;
		if (stopTimer > 0) return (STOP_TOTAL - Math.max(stopTimer, STOP_GAZE)) * Math.PI / 30;   // 주시 구간엔 바늘 정지
		return Math.toRadians(gearAngle * 2);
	}

	private void setLabel(Component c) {
		if (label != null && label.display instanceof TextDisplay td && td.isValid()) td.text(c);
	}

	@Override
	protected void onPhaseChange(int p) {
		phaseClockUntil = tickCount + 21;
		showClock();
		for (Part r : ringA) if (r != null) r.setBlock(copper(0));
		for (Part r : ringB) if (r != null) r.setBlock(copper(1));
		if (p == 2) {
			BU.say(mob, Component.text("크로노 센티넬: \"시간은 나의 것. 너희의 1초는 이제 내 허락이 필요하다.\"", NamedTextColor.GOLD, TextDecoration.ITALIC));
			BU.say(mob, Component.text("⚠ 2페이즈: 시간 정지 - 경고 3초 뒤 10초 동안 센티넬을 계속 바라보세요! 시선을 떼면 기절·실명·나약함", NamedTextColor.GRAY));
		} else if (p == 3) {
			BU.say(mob, Component.text("크로노 센티넬: \"되감는다... 너희가 이긴 순간마저도!\"", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
			BU.say(mob, Component.text("⚠ 3페이즈: 시간 역행 - 채널링 중 " + (int) REWIND_BREAK + " 피해를 주면 저지! 실패 시 체력 회복", NamedTextColor.GRAY));
			rewindCd = REWIND_CD;
			rewindUnlockedAt = tickCount;
			if (stopTimer <= 0) startTimeStop();
		}
		refreshGlow();
	}

	@Override
	protected void onHurtBy(EntityDamageEvent e, double amount) {
		if (rewindTimer > 0) {
			rewindDamage += (float) Math.min(amount, 40);
			if (rewindDamage >= REWIND_BREAK) {
				rewindTimer = 0;
				stunTimer = 70;
				cue(CHANNEL, 0, 0, 11);
				Location l = mob.getLocation();
				Vector cc = coreAt();
				BU.sound(l, Sound.BLOCK_ANVIL_BREAK, SoundCategory.HOSTILE, 2F, 0.5F);
				BU.sound(l, "hobbang:sentinel.gears", SoundCategory.HOSTILE, 1.5F, 1.0F);
				mob.getWorld().spawnParticle(Particle.EXPLOSION, cc.getX(), cc.getY(), cc.getZ(), 4, 1.5, 1.5, 1.5, 0);
				Fx.burst(l.getWorld(), clockAnchor(), kind, 22, 0.75);
				Fx.ripple(this, cc, 3.4, 10);
				BU.say(mob, Component.text("✦ 시간 역행 저지! 센티넬이 과부하 상태입니다 (받는 피해 +50%)", NamedTextColor.GREEN, TextDecoration.BOLD));
				setLabel(Component.text("과부하!", NamedTextColor.RED, TextDecoration.BOLD));
			}
		}
	}

	@Override
	protected void bossTick(List<Player> near, LivingEntity target) {
		World w = mob.getWorld();
		Location here = mob.getLocation();
		float spd = phase >= 3 ? 1.4F : 1.0F;
		sweepCd -= spd;
		boltCd -= spd;
		ringCd -= 1;
		if (phase >= 2) { stopCd -= 1; echoCd -= 1; }
		if (phase >= 3) {
			bladeCd -= 1;
			if (tickCount != rewindUnlockedAt) rewindCd -= 1;
		}
		advanceGears();
		if (clockActive()) showClock();
		// 부유 이동: 플레이어 주위를 돌며, 좌우 +40% / 상하 -50%
		if (target != null) {
			Vector to = target.getLocation().toVector().subtract(here.toVector());
			Vector flat = new Vector(to.getX(), 0, to.getZ());
			double dist = flat.length();
			if (Float.isNaN(orbitAngle)) {
				orbitAngle = dist > 1e-6 ? (float) Math.atan2(flat.getZ(), flat.getX()) : 0F;
			}
			orbitAngle += 0.042F;   // 반지름 7 기준 약 7.5초/바퀴, 좌우 +40% 반영
			double radius = 7;
			Vector playerPos = target.getLocation().toVector();
			double gy = BU.groundY(w, here.getX(), here.getY(), here.getZ());
			double hoverY = Math.max(gy + 2.5, target.getLocation().getY() + 2.5);
			Vector desired = new Vector(
				playerPos.getX() + Math.cos(orbitAngle) * radius,
				hoverY,
				playerPos.getZ() + Math.sin(orbitAngle) * radius);
			Vector delta = desired.subtract(here.toVector());
			Vector accel = new Vector(delta.getX() * 0.028, delta.getY() * 0.007, delta.getZ() * 0.028);
			Vector v = mob.getVelocity().multiply(0.8).add(accel);
			if (stunTimer > 0) v = new Vector(0, -0.04, 0);
			mob.setVelocity(v);
			if (tickCount % 3 == 0) {
				float yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
				mob.setRotation(yaw, 0);
				mob.setBodyYaw(yaw);
			}
		} else {
			mob.setVelocity(mob.getVelocity().multiply(0.8));
		}
		if (stunTimer > 0) {
			stunTimer--;
			if (stunTimer % 5 == 0) { Vector cc = coreAt(); w.spawnParticle(Particle.SMOKE, cc.getX(), cc.getY(), cc.getZ(), 8, 1.0, 1.0, 1.0, 0.02); }
			if (stunTimer == 0) setLabel(Component.empty());
			return;
		}
		if (rewindTimer > 0) {
			tickRewind();
			return;
		}
		if (stopTimer > 0) tickTimeStop();
		if (invulTicks > 0 || target == null) return;
		if (tickWindup()) return;
		// 쿨이 끝난 스킬 하나만 골라 대기 뒤 발동 (우선순위 = 예전 판정 순서)
		if (phase >= 3 && rewindCd <= 0 && stopTimer <= 0) queue(() -> { startRewind(); rewindCd = REWIND_CD; });
		else if (phase >= 2 && stopCd <= 0 && stopTimer <= 0) queue(this::startTimeStop);
		else if (sweepCd <= 0) queue(() -> { sweep(phase >= 3); sweepCd = SWEEP_CD; });
		else if (boltCd <= 0) queue(() -> { bolts(phase >= 2 ? 8 : 6); boltCd = BOLT_CD; });
		// v1.2.0: 시간 고리 / 잔상 폭발 / 자정의 칼날
		else if (ringCd <= 0) queue(() -> {
			int[] col = BossPatterns.colors(kind);
			BossPatterns.shockRing(this, mob.getLocation().toVector(), 11, 0.4, 7, 20, col[0], col[1]);
			ringCd = RING_CD;
		});
		else if (phase >= 2 && echoCd <= 0) queue(() -> {
			BossPatterns.warnAll(this, "잔상 폭발! 2초 뒤 지금 자리가 터집니다 — 계속 움직이세요!");
			BossPatterns.meteorsOnPlayers(this, 24, 2.5, 40, 8, Material.YELLOW_STAINED_GLASS, false);
			echoCd = ECHO_CD;
		});
		else if (phase >= 3 && bladeCd <= 0) queue(() -> {
			int[] col = BossPatterns.colors(kind);
			Vector c = mob.getLocation().toVector();
			BossPatterns.warnAll(this, "자정의 칼날! 빛나는 예고선에서 벗어나세요!");
			BossPatterns.groundBeams(this, c, 4, 0, 13, 30, 24, 9, Material.YELLOW_STAINED_GLASS, col[0]);
			BossPatterns.later(this, 40, () -> BossPatterns.groundBeams(this, c, 4, Math.PI / 4, 13, 26, 24, 9, Material.WHITE_STAINED_GLASS, col[1]));
			bladeCd = BLADE_CD;
		});
	}

	/** 스킬 전 전역 대기: 1페 30 / 2페 20 / 3페 12틱. 대기 중엔 다른 스킬 없음. */
	private Runnable queued;
	private int windup;

	private void queue(Runnable skill) {
		queued = skill;
		windup = phase == 1 ? 30 : phase == 2 ? 20 : 12;
	}

	/** 대기 중이면 true (발동 틱 포함) */
	private boolean tickWindup() {
		if (queued == null) return false;
		if (--windup <= 0) {
			Runnable r = queued;
			queued = null;
			r.run();
		}
		return true;
	}

	// ───────── 시간 정지: 3초 경고 → 10초 주시. 시선 이탈 0.5초 뒤 스턴 + 실명II + 나약함II (창마다 1회). 쿨 1분 ─────────
	private static final int STOP_WARN = 60, STOP_GAZE = 200, STOP_TOTAL = STOP_WARN + STOP_GAZE + 1;
	private static final int GAZE_GRACE = 10;         // 시선 떼고 0.5초
	private static final double GAZE_DOT = 0.3;
	/** 이번 시전 창에서 이미 처벌된 플레이어 (다음 시전에서 초기화) */
	private final Set<UUID> gazeHit = new HashSet<>();
	/** 시선 이탈 누적 틱. 다시 보면 리셋, GAZE_GRACE에 도달하면 스턴 */
	private final Map<UUID, Integer> lookAway = new HashMap<>();
	/** 이 보스가 건 스턴 — 보스 사망/언로드 시 해제 */
	private final Set<UUID> stunned = new HashSet<>();

	private void startTimeStop() {
		stopTimer = STOP_TOTAL;
		stopCd = STOP_CD;
		gazeHit.clear();
		lookAway.clear();
		showClock();
		net.kyori.adventure.title.Title t = net.kyori.adventure.title.Title.title(
			Component.text("시간 정지", NamedTextColor.GOLD, TextDecoration.BOLD),
			Component.text("3초 후 센티넬을 주시하세요!", NamedTextColor.YELLOW),
			net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(150), java.time.Duration.ofSeconds(3), java.time.Duration.ofMillis(300)));
		for (Player p : nearbyPlayers(32)) if (BU.valid(p)) p.showTitle(t);
	}

	private void tickTimeStop() {
		stopTimer--;
		Location here = mob.getLocation();
		if (stopTimer > STOP_GAZE) {
			int left = stopTimer - STOP_GAZE;
			if (left % 20 == 0) {
				Fx.puff(here.getWorld(), Particle.WAX_ON, clockAnchor(), 8, 0.64, 0.06);
				setLabel(Component.text("⏳ " + (left / 20), NamedTextColor.GOLD, TextDecoration.BOLD));
				BU.sound(here, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.HOSTILE, 3F, 0.5F + left / 60F);
				if (left == STOP_WARN) BU.sound(here, "hobbang:sentinel.clockwork", SoundCategory.HOSTILE, 2.0F, 1.0F);
			}
			return;
		}
		if (stopTimer == STOP_GAZE) {
			gearHoldUntil = tickCount + STOP_GAZE;
			cue(CHANNEL, 0, 8, 12);
			BU.sound(here, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 3F, 0.5F);
			BU.sound(here, "hobbang:sentinel.gaze.drone", SoundCategory.HOSTILE, 0.72F, 1.0F);
			BU.sound(here, "hobbang:sentinel.gears", SoundCategory.HOSTILE, 1.5F, 1.0F);
			Fx.burst(here.getWorld(), clockAnchor(), kind, 19, 0.72);
			Fx.ripple(this, coreAt(), 4.4, 10);
		}
		if (stopTimer % 20 == 0) {
			int sec = stopTimer / 20;
			setLabel(sec > 0 ? Component.text("👁 " + sec, NamedTextColor.AQUA, TextDecoration.BOLD) : Component.empty());
			Component bar = Component.text("👁 센티넬을 계속 바라보세요 — " + sec + "초", NamedTextColor.AQUA);
			for (Player p : nearbyPlayers(32)) if (BU.valid(p) && !gazeHit.contains(p.getUniqueId())) p.sendActionBar(bar);
		}
		Vector me = coreAt();
		for (Player p : nearbyPlayers(32)) {
			if (!BU.valid(p) || gazeHit.contains(p.getUniqueId())) continue;
			Vector toMe = me.clone().subtract(p.getEyeLocation().toVector());
			boolean looking = toMe.lengthSquared() < 1e-6
				|| p.getEyeLocation().getDirection().dot(toMe.normalize()) > GAZE_DOT;
			UUID id = p.getUniqueId();
			if (looking) {
				lookAway.remove(id);
				continue;
			}
			int away = lookAway.getOrDefault(id, 0) + 1;
			if (away >= GAZE_GRACE) {
				lookAway.remove(id);
				punishGaze(p);
			} else {
				lookAway.put(id, away);
			}
		}
		if (stopTimer <= 0) lookAway.clear();
	}

	private void punishGaze(Player p) {
		gazeHit.add(p.getUniqueId());
		stunned.add(p.getUniqueId());
		int stun = 100 + 40 * (phase - 1);   // 5초 + 2초 × (페이즈 - 1)
		TimeStun.apply(p, stun);
		p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 600, 1));
		p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 600, 1));
		p.sendMessage(Component.text("⏸ 시선을 뗐습니다 — " + stun / 20 + "초간 시간이 멈춥니다!", NamedTextColor.AQUA));
		p.playSound(p.getLocation(), "hobbang:sentinel.gaze.stun", SoundCategory.HOSTILE, 0.85F, 1.0F);
		Fx.puff(p.getWorld(), Particle.WAX_ON, p.getLocation().toVector().add(new Vector(0, 1, 0)), 10, 0.4, 0.05);
	}

	private void releaseStuns() {
		for (UUID id : stunned) TimeStun.release(id);
		stunned.clear();
	}

	@Override
	public void unload() {
		releaseStuns();
		super.unload();
	}

	private void startRewind() {
		rewindTimer = 160;
		rewindDamage = 0;
		showClock();
		Location here = mob.getLocation();
		BU.sound(here, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.HOSTILE, 2F, 1.5F);
		BU.sound(here, "hobbang:sentinel.clockwork", SoundCategory.HOSTILE, 2.0F, 1.0F);
		BU.say(mob, Component.text("⚠ 시간 역행 시작! 8초 안에 " + (int) REWIND_BREAK + " 피해를 주세요!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
	}

	private void tickRewind() {
		rewindTimer--;
		Location here = mob.getLocation();
		if (rewindTimer % 5 == 0) {
			setLabel(Component.text("되감는 중… 저지까지 " + Math.max(0, (int) (REWIND_BREAK - rewindDamage)), NamedTextColor.AQUA));
			Vector cc = coreAt();
			mob.getWorld().spawnParticle(Particle.REVERSE_PORTAL, cc.getX(), cc.getY(), cc.getZ(), 24, 2.2, 2.2, 2.2, 0.05);
			Fx.puff(here.getWorld(), Particle.WAX_OFF, clockAnchor(), 5, 0.7, 0.06);
		}
		if (rewindTimer == 0) {
			cue(CHANNEL, 0, 0, 16);
			heal(maxHealth() * 0.12);
			Fx.burst(here.getWorld(), clockAnchor(), kind, 22, 0.75);
			Fx.ripple(this, coreAt(), 3.6, 10);
			BU.sound(here, Sound.ITEM_TOTEM_USE, SoundCategory.HOSTILE, 2F, 0.6F);
			BU.sound(here, "hobbang:sentinel.gears", SoundCategory.HOSTILE, 1.5F, 1.0F);
			BU.say(mob, Component.text("✖ 시간 역행 성공... 센티넬의 체력이 회복되었습니다", NamedTextColor.RED));
			setLabel(Component.empty());
		}
	}

	private void sweep(boolean dbl) {
		World world = mob.getWorld();
		Location c0 = mob.getLocation();
		double gy = BU.groundY(world, c0.getX(), c0.getY(), c0.getZ());
		Vector c = new Vector(c0.getX(), gy, c0.getZ());
		float startAngle = random.nextFloat() * 360;
		float dir = random.nextBoolean() ? 1 : -1;
		int hands = dbl ? 2 : 1;
		final float len = 12;
		cue(SWEEP, 25, 50, 12);
		BU.sound(c0, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.HOSTILE, 3F, 0.6F);
		BU.sound(c0, "hobbang:sentinel.clockwork", SoundCategory.HOSTILE, 2.0F, 1.0F);
		BU.say(mob, Component.text("⚠ 시계침이 돕니다! 점프하거나 범위 밖으로!", NamedTextColor.YELLOW));
		Fx.sweepDial(this, c, len + 0.5F, 25, 80);
		Fx.spawn(world, uuid, c, 80, (f, w) -> {
			if (f.age == 1) {
				for (int h = 0; h < hands; h++) {
					BlockDisplay d = f.addBlock(bd(Material.RED_STAINED_GLASS), true);
					d.setGlowing(true); d.setGlowColorOverride(Color.fromRGB(0xFF3030));
				}
			}
			int warm = 25;
			float angle = startAngle + (f.age <= warm ? 0 : (f.age - warm) * 7.2F * dir);
			float prev = startAngle + (f.age - 1 <= warm ? 0 : (f.age - 1 - warm) * 7.2F * dir);
			if (f.age == warm) {
				for (Display d : f.displays) {
					if (d instanceof BlockDisplay bdd) bdd.setBlock(bd(Material.BLACK_CONCRETE));
					d.setGlowColorOverride(Color.fromRGB(0xFFD040));
				}
				Location at = new Location(w, c.getX(), c.getY(), c.getZ());
				BU.sound(at, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2F, 0.6F);
				BU.sound(at, "hobbang:sentinel.gears", SoundCategory.HOSTILE, 1.5F, 1.0F);
			}
			for (int h = 0; h < f.displays.size(); h++) {
				float a = angle + h * 180;
				Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(a));
				Fx.shape(f.displays.get(h), new Vector3f(-0.2F, 0.05F, 0.5F).rotate(q), q, new Vector3f(0.4F, f.age < warm ? 0.1F : 0.6F, len), 1);
			}
			if (f.age > warm && f.age <= warm + 50) {
				if (f.age % 3 == 0) {
					for (int h = 0; h < hands; h++) {
						double a = Math.toRadians(angle + h * 180);
						Vector tip = c.clone().add(new Vector(Math.sin(a) * len, 0.35, Math.cos(a) * len));
						Fx.puff(w, Particle.ELECTRIC_SPARK, tip, 6, 0.22, 0.05);
					}
				}
				for (Player p : BU.victims(w, BU.box(c, len + 1, 3, len + 1))) {
					Location pl = p.getLocation();
					double dx = pl.getX() - c.getX(), dz = pl.getZ() - c.getZ();
					double dist = Math.sqrt(dx * dx + dz * dz);
					if (dist < 0.5 || dist > len + 0.5 || pl.getY() > c.getY() + 1.1) continue;
					float pa = (float) Math.toDegrees(Math.atan2(dx, dz));
					for (int h = 0; h < hands; h++) {
						float a0 = prev + h * 180, a1 = angle + h * 180;
						float lo = Math.min(a0, a1) - 4, hi = Math.max(a0, a1) + 4;
						float rel = lo + (((pa - lo) % 360) + 360) % 360;
						if (rel <= hi && p.getNoDamageTicks() == 0) {
							BU.hit(p, mob, 10, c, 1.0, 0.5);
							w.spawnParticle(Particle.CRIT, pl.getX(), pl.getY() + 1, pl.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
						}
					}
				}
			}
			if (f.age == warm + 52) for (Display d : f.displays) Fx.shape(d, new Vector3f(), new Quaternionf(), new Vector3f(0, 0, 0), 6);
		});
	}

	private void bolts(int n) {
		World world = mob.getWorld();
		Vector c = coreAt();
		cue(CAST, 19, (n - 1) * 2 + 1, 12);
		double br = 1.2 + mob.getWidth() / 2;
		BU.sound(mob.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 3F, 1.6F);
		BU.sound(mob.getLocation(), "hobbang:sentinel.engine", SoundCategory.HOSTILE, 1.5F, 0.9F);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			Vector start = c.clone().add(new Vector(Math.cos(a) * br, Math.sin(i) * 0.5, Math.sin(a) * br));
			final int idx = i;
			Fx.spawn(world, uuid, start, 90, (f, w) -> {
				if (f.age == 1) {
					BlockDisplay d = f.addBlock(bd(Material.YELLOW_STAINED_GLASS), true);
					d.setGlowing(true); d.setGlowColorOverride(Color.fromRGB(0xFFE070));
				}
				for (Display d : f.displays)
					Fx.shape(d, new Vector3f(-0.25F), new Quaternionf().rotateXYZ(f.age * 0.4F, f.age * 0.3F, idx), new Vector3f(0.5F), 1);
				if (f.age <= 20 + idx * 2 && f.age % 4 == 0) Fx.puff(w, Particle.WAX_ON, f.pos, 2, 0.24, 0.02);
				if (f.age == 20 + idx * 2) {
					Player tgt = null;
					double best = 1e9;
					for (Player p : nearbyPlayers(40)) {
						if (!BU.valid(p)) continue;
						double d = p.getLocation().toVector().distanceSquared(f.pos);
						if (d < best) { best = d; tgt = p; }
					}
					if (tgt == null) { f.kill(); return; }
					f.vel = tgt.getLocation().toVector().add(new Vector(0, 1, 0)).subtract(f.pos).normalize().multiply(0.7);
					Fx.puff(w, Particle.ELECTRIC_SPARK, f.pos, 5, 0.24, 0.05);
					Location bl = new Location(w, f.pos.getX(), f.pos.getY(), f.pos.getZ());
			BU.sound(bl, Sound.BLOCK_DISPENSER_DISPENSE, SoundCategory.HOSTILE, 0.9F, 1.85F);
			BU.sound(bl, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 0.7F, 1.65F);
				}
				if (f.age > 20) {
					w.spawnParticle(Particle.WAX_ON, f.pos.getX(), f.pos.getY(), f.pos.getZ(), 1, 0, 0, 0, 0);
					boolean burst = BU.solidAt(w, f.pos);
					for (Player p : BU.victims(w, BU.box(f.pos, 0.9, 0.9, 0.9))) {
						p.damage(6, BU.magic(mob));
						p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
						burst = true;
					}
					if (burst) {
						Fx.burst(w, f.pos, kind, 8, 0.35);
						w.spawnParticle(Particle.END_ROD, f.pos.getX(), f.pos.getY(), f.pos.getZ(), 8, 0.2, 0.2, 0.2, 0.08);
						f.kill();
					}
				}
			});
		}
	}

	@Override
	protected void dropLoot(List<ItemStack> drops, Random r) {
		drops.add(HItem.CHRONO_GEAR.create(4 + r.nextInt(5)));
		drops.add(HItem.RADIANT_STONE.create(10 + r.nextInt(7)));
		drops.add(HItem.REWIND_HOURGLASS.create(1 + r.nextInt(2)));
		drops.add(HItem.BLESSING_SCROLL.create(3));
		if (r.nextFloat() < 0.3F) drops.add(HItem.CHRONO_DAGGER.create());
	}

	@Override
	protected int deathDuration() { return 150; } // ~2s 감속 + 5s 정지 + 사라짐

	@Override
	protected void onDeathStart() {
		releaseStuns();
		stopTimer = 0;
		queued = null;
		deathU = 0;
		lastDeathFrame = 0;
		Fx.burst(mob.getWorld(), coreAt(), kind, 20, 0.95);
		BU.sound(mob.getLocation(), Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 2F, 0.6F);
		BU.sound(mob.getLocation(), "hobbang:sentinel.engine", SoundCategory.HOSTILE, 1.5F, 0.9F);
	}

	@Override
	protected void poseDeath(float u) {
		deathU = u;
		// 0-0.28 톱니·시계가 느려지고 불이 꺼짐, 0.28-0.92 정지 홀드(~5초), 이후 사라짐
		float slow = u < 0.28F ? 1F - u / 0.28F : 0F;
		gearAngle = (gearAngle + 3 * gearSpeed * slow * slow * (deathTick - lastDeathFrame)) % 2160;
		lastDeathFrame = deathTick;
		applyDeathLights(u);
		if (modelMode || u < 0.92F) poseParts(animTick);
		else super.poseDeath((u - 0.92F) / 0.08F);
	}

	private void applyDeathLights(float u) {
		int br = u < 0.20F ? Math.max(0, (int) (15 * (1F - u / 0.20F))) : 0;
		org.bukkit.entity.Display.Brightness dim = new org.bukkit.entity.Display.Brightness(br, br);
		for (Part p : new Part[]{mBody, mRing, mClock, mArmL, mArmR, mPend, mBladeL, mBladeR, mGearL, mGearR}) {
			if (p != null && p.display.isValid()) p.display.setBrightness(dim);
		}
		if (core != null && core.display.isValid()) core.display.setBrightness(dim);
		if (face != null && face.display.isValid()) face.display.setBrightness(dim);
		if (label != null && label.display instanceof org.bukkit.entity.TextDisplay td && u > 0.15F) {
			td.text(net.kyori.adventure.text.Component.empty());
		}
	}

	@Override
	protected void deathFx(int tick, float u) {
		org.bukkit.Location l = mob.getLocation();
		if (u < 0.28F && tick % 5 == 0) {
			l.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, l.getX(), l.getY() + 1.6, l.getZ(), 4, 0.5, 0.5, 0.5, 0.01);
		} else if (u > 0.92F && tick % 3 == 0) {
			l.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, l.getX(), l.getY() + 0.8, l.getZ(), 6, 0.4, 0.4, 0.4, 0.02);
		}
	}

}
