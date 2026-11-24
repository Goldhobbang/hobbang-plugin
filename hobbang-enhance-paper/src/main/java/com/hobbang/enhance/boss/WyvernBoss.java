package com.hobbang.enhance.boss;

import static com.hobbang.enhance.boss.BossMotion.Move.*;

import com.hobbang.enhance.Keys;
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
import org.bukkit.entity.WindCharge;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** 폭풍의 와이번 — 폭풍을 부리는 비룡 (비행형). 히트박스 = 투명 좀벌레(AI 끔, 중력 끔) */
public class WyvernBoss extends DisplayBoss {
	private static final int M_CIRCLE = 0, M_DIVE_AIM = 1, M_DIVE = 2, M_REST = 3, M_CLIMB = 4;
	private int mode = M_CIRCLE, modeTimer = 0;
	private double circleAngle = 0;
	private float flyYaw, flyPitch = 0, flyRoll = 0, flap = 0;
	private Vector diveTarget = new Vector();
	private static final int ORB_CD = 160, GUST_CD = 200, BREATH_CD = 280, BREATH_CD_LATE = 240;
	private static final int DIVE_CD = 320, DIVE_CD_LATE = 280, TORNADO_CD = 400, VORTEX_CD = 440, STORM_CD = 560;
	private float orbCd = ORB_CD, gustCd = GUST_CD;
	private int diveCd = diveCooldown(), breathCd = breathCooldown(), diveAimTotal = 30;
	private int tornadoCd = TORNADO_CD, vortexCd = VORTEX_CD, stormCd = STORM_CD;

	private int breathCooldown() { return phase >= 3 ? BREATH_CD_LATE : BREATH_CD; }
	private int diveCooldown() { return phase >= 3 ? DIVE_CD_LATE : DIVE_CD; }
	private Vector home = null;

	private Part body, belly, neck, head, jaw, eyeL, eyeR, hornL, hornR, fin;
	private final Part[] tail = new Part[3];
	private final Part[] wing = new Part[8];
	/** v1.4.3 급강하 소환: 1 = 날개 접힘, 착지 후 0 으로 펼친다 */
	public float spawnFold = 0;
	public float spawnPitch = 0;
	/** v1.4.4 관절 리그 (목/척추/꼬리/다리 분절) */
	/** v1.5.0 박스 리그 36파트: 목 3 + 머리 + 턱, 척추 body/waist/hips, 꼬리 6, 날개 = 상박/전완/손가락 3(날개막 판 포함), 다리 4×3 */
	private static final String[] W_PARTS = {
		"body", "waist", "hips", "neck_1", "neck_2", "neck_3", "head", "jaw",
		"wing_arm_l", "wing_fore_l", "wing_f1_l", "wing_f2_l", "wing_f3_l",
		"wing_arm_r", "wing_fore_r", "wing_f1_r", "wing_f2_r", "wing_f3_r",
		"tail_1", "tail_2", "tail_3", "tail_4", "tail_5", "tail_6",
		"thigh_fl", "shin_fl", "foot_fl", "thigh_fr", "shin_fr", "foot_fr",
		"thigh_hl", "shin_hl", "foot_hl", "thigh_hr", "shin_hr", "foot_hr"
	};
	private Rigid headRig;
	private float lift = 1, tuck = 1, fold, wingAngle = -21, wingPower = 35, flapSpeed = 0.32F;
	private float visualPitch, bank, diveWeight, deathU;
	private int landingAt = -20;
	private boolean restContact;
	private static final float LEGACY = 9.0F / 5.5F;

	public WyvernBoss(Mob mob) {
		super(mob, BossKind.TEMPEST_WYVERN);
		flyYaw = mob.getPersistentDataContainer().getOrDefault(Keys.YAW, PersistentDataType.FLOAT, mob.getLocation().getYaw());
	}

	@Override protected float[] phaseThresholds() { return new float[]{0.6F, 0.3F}; }

	@Override
	protected BossBar.Color phaseColor(int p) {
		return p == 1 ? BossBar.Color.BLUE : p == 2 ? BossBar.Color.WHITE : BossBar.Color.YELLOW;
	}

	@Override protected boolean glowParts() { return phase >= 3; }
	@Override protected int glowColor() { return 0x60F0FF; }
	@Override protected float modelYaw() { return flyYaw; }
	@Override protected float damageMultiplier() { return 1.0F; }
	@Override protected boolean overrideEnergyModels() { return dying; }
	@Override protected int xpReward() { return 900; }
	@Override protected float legacyScale() { return LEGACY; }

	private static BlockData bd(Material m) { return m.createBlockData(); }
	private static float sin(double f) { return (float) Math.sin(f); }
	private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
	private static float lerp(float k, float a, float b) { return a + k * (b - a); }

	private static float wrap(float d) {
		d %= 360F;
		if (d >= 180F) d -= 360F;
		if (d < -180F) d += 360F;
		return d;
	}

	private BlockData membrane() {
		return phase >= 3 ? bd(Material.YELLOW_STAINED_GLASS) : phase == 2 ? bd(Material.LIGHT_BLUE_STAINED_GLASS) : bd(Material.CYAN_STAINED_GLASS);
	}

	// ───────── 모델 ─────────
	@Override
	protected void buildParts() {
		body = belly = neck = head = jaw = eyeL = eyeR = hornL = hornR = fin = null;
		java.util.Arrays.fill(tail, null);
		java.util.Arrays.fill(wing, null);
		if (modelMode) {
			for (String n : W_PARTS) {
				Part p;
				if (n.equals("body") || n.equals("head") || n.equals("waist") || n.startsWith("wing_")) {
					p = energyModel(n, false, false);
				} else {
					p = model(n, false);
				}
				if (n.equals("body")) {
					p.size.x *= 0.78F;
					p.size.y *= 0.86F;
				}
				if (n.equals("head")) head = p;
			}
			return;
		}
		BlockData dp = bd(Material.DARK_PRISMARINE), pb = bd(Material.PRISMARINE_BRICKS), q = bd(Material.SMOOTH_QUARTZ), lan = bd(Material.SEA_LANTERN);
		body = block(dp, -0.6F, -0.45F, -1.2F, 1.2F, 0.9F, 2.4F, false);
		belly = block(q, -0.45F, -0.5F, -1.0F, 0.9F, 0.1F, 2.0F, false);
		neck = block(dp, -0.3F, -0.3F, 0F, 0.6F, 0.6F, 1.0F, false);
		head = block(pb, -0.45F, -0.35F, 0F, 0.9F, 0.7F, 1.0F, false);
		jaw = block(dp, -0.35F, -0.2F, 0F, 0.7F, 0.2F, 0.9F, false);
		eyeL = block(lan, -0.03F, -0.08F, -0.1F, 0.06F, 0.16F, 0.2F, true);
		eyeR = block(lan, -0.03F, -0.08F, -0.1F, 0.06F, 0.16F, 0.2F, true);
		hornL = block(q, -0.06F, -0.06F, -0.7F, 0.12F, 0.12F, 0.7F, false);
		hornR = block(q, -0.06F, -0.06F, -0.7F, 0.12F, 0.12F, 0.7F, false);
		float[][] ts = {{0.5F, 0.45F, 1.1F}, {0.38F, 0.35F, 1.0F}, {0.26F, 0.25F, 1.0F}};
		for (int i = 0; i < 3; i++) tail[i] = block(dp, -ts[i][0] / 2, -ts[i][1] / 2, -ts[i][2], ts[i][0], ts[i][1], ts[i][2], false);
		fin = block(membrane(), -0.6F, -0.05F, -0.7F, 1.2F, 0.1F, 0.7F, true);
		for (int s = 0; s < 2; s++) {
			float sx = s == 0 ? 1 : -1;
			wing[s * 4] = block(membrane(), sx > 0 ? 0 : -2.2F, -0.05F, -1.0F, 2.2F, 0.1F, 1.6F, true);
			wing[s * 4 + 1] = block(dp, sx > 0 ? 0 : -2.2F, -0.1F, 0.45F, 2.2F, 0.2F, 0.2F, false);
			wing[s * 4 + 2] = block(membrane(), sx > 0 ? 0 : -2.0F, -0.05F, -0.9F, 2.0F, 0.1F, 1.3F, true);
			wing[s * 4 + 3] = block(dp, sx > 0 ? 0 : -2.0F, -0.08F, 0.35F, 2.0F, 0.16F, 0.16F, false);
		}
	}

	@Override
	protected void onPartsRebuilt() {
		refreshGlow();
	}

	@Override
	protected void poseParts(float t) {
		if (modelMode) { poseModel(t); return; }
		float amp, base, flapSpeed;
		if (mode == M_DIVE) { amp = 6; base = -25; flapSpeed = 0.1F; }
		else if (mode == M_REST) { amp = 8; base = -40; flapSpeed = 0.15F; }
		else if (mode == M_DIVE_AIM) { amp = 45; base = 5; flapSpeed = 0.55F; }
		else { amp = 35; base = 0; flapSpeed = phase >= 3 ? 0.45F : 0.32F; }
		flap += flapSpeed * ANIM_INTERVAL;
		Rigid root = Rigid.yaw(modelYaw());
		float bob = idle.wave(0.06F, 0) * 0.06F + (mode == M_REST ? 0 : sin(flap) * -0.15F);
		float wingMotion = mode == M_REST ? amp * idle.weight() : amp;
		Rigid b = root.at(0, 0.8F + bob, 0).rotX(-flyPitch).rotZ(flyRoll);
		place(body, b);
		place(belly, b);
		Rigid nk = b.at(0, 0.1F, 1.15F).rotX(-15 + idle.wave(0.07F, 0) * 6);
		place(neck, nk);
		float jawOpen = (mode == M_DIVE_AIM || mode == M_DIVE) ? 35 : (idle.weight() + idle.wave(0.11F, 0.9F)) * 2;
		Rigid hd = nk.at(0, 0, 0.95F).rotX(20 + idle.wave(0.07F, -1) * 6);
		headRig = root.at(0, 0.8F + (mode == M_REST ? 0 : sin(flap) * -0.15F), 0)
			.rotX(-flyPitch).rotZ(flyRoll).at(0, 0.1F, 1.15F).rotX(-15 + sin(t * 0.07F) * 6)
			.at(0, 0, 0.95F).rotX(20 + sin(t * 0.07F + 1) * 6).at(0, -0.1F, 1.0F);
		place(head, hd);
		place(jaw, hd.at(0, -0.35F, 0.1F).rotX(jawOpen));
		place(eyeL, hd.at(0.46F, 0.1F, 0.65F));
		place(eyeR, hd.at(-0.46F, 0.1F, 0.65F));
		place(hornL, hd.at(0.3F, 0.35F, 0.15F).rotX(-35).rotY(-15));
		place(hornR, hd.at(-0.3F, 0.35F, 0.15F).rotX(-35).rotY(15));
		Rigid tl = b.at(0, 0, -1.15F);
		float[] lens = {1.1F, 1.0F, 1.0F};
		for (int i = 0; i < 3; i++) {
			tl = tl.rotY(idle.wave(0.08F, i * 0.8F) * 11).rotX(idle.wave(0.09F, i) * 6 + 4);
			place(tail[i], tl);
			tl = tl.at(0, 0, -lens[i]);
		}
		place(fin, tl.rotY(idle.wave(0.08F, 3) * 14));
		BossMotion.Frame stormPose = motion(CHANNEL).blend(motion(PHASE));
		for (int s = 0; s < 2; s++) {
			float sx = s == 0 ? 1 : -1;
			float a1 = base + sin(flap) * wingMotion + 22 * stormPose.prepare() - 34 * stormPose.release();
			float a2 = base * 0.5F + sin(flap - 0.7F) * wingMotion * 0.8F;
			Rigid sh = b.at(sx * 0.6F, 0.3F, 0.3F).rotZ(sx * a1).rotY(sx * -8);
			place(wing[s * 4], sh);
			place(wing[s * 4 + 1], sh);
			Rigid out = sh.at(sx * 2.15F, 0, 0).rotZ(sx * a2).rotY(sx * -12);
			place(wing[s * 4 + 2], out);
			place(wing[s * 4 + 3], out);
		}
	}

	private void poseModel(float t) {
		boolean rest = mode == M_REST;
		boolean diving = mode == M_DIVE;
		boolean aiming = mode == M_DIVE_AIM;
		boolean takeoff = mode == M_CLIMB;
		float landing = rest ? 1 - BossMotion.ease((tickCount - landingAt) / 12F) : 0;
		float extend = diving ? 1 - BossMotion.ease((float) (pos().getY() - diveTarget.getY() - 1.5) / 4.5F) : 0;
		float speed = rest ? 0.10F : diving ? 0.14F : aiming ? 0.48F : takeoff ? 0.50F : phase >= 3 ? 0.45F : 0.32F;
		flapSpeed = lerp(0.30F, flapSpeed, speed);
		if (!dying) flap = (float) ((flap + flapSpeed * ANIM_INTERVAL) % (2 * Math.PI));
		float up = BossMotion.ease(Math.max(0, (float) Math.cos(flap - 0.24F)));
		float targetFold = rest ? 0.86F : diving ? 0.64F : aiming ? 0.18F + 0.26F * up : 0.33F * up;
		fold = lerp(0.44F, fold, targetFold);
		lift = lerp(0.33F, lift, dying || rest ? 0 : diving ? 0.25F : 1);
		tuck = lerp(0.42F, tuck, dying || rest ? 0 : 1 - extend * 0.86F);
		diveWeight = lerp(0.38F, diveWeight, aiming || diving ? 1 : 0);
		wingAngle = lerp(0.40F, wingAngle, dying ? -40 : rest ? -32 : diving ? -34 : aiming ? -12 : -21);
		wingPower = lerp(0.38F, wingPower, dying ? 0 : rest ? 2 * idle.weight() : diving ? 6 : aiming ? 42 : 35);
		visualPitch = lerp(0.33F, visualPitch, dying || rest ? 0 : -flyPitch);
		bank = lerp(0.40F, bank, dying || rest ? 0 : flyRoll);
		aimModel(2.6F, 48, 33, 0.29F);
		float wingFold = clamp(fold + spawnFold, 0, 1);
		if (dying) wingFold = wingFold * (1 - BossMotion.ease(deathU * 2.5F));
		BossPoses.Flight flight = new BossPoses.Flight(flap, lift, tuck, wingFold, wingAngle, wingPower,
			visualPitch * (1 - 0.75F * landing) + spawnPitch, bank, landing, lookYaw, lookPitch, diveWeight, deathU);
		BossMotion.Frame cast = motion(CAST);
		BossMotion.Frame sweep = motion(SWEEP);
		if (!dying && !assembling() && mode == M_CIRCLE && invulTicks <= 0 && isTargetable(mob.getTarget())) {
			float rate = phase >= 3 ? 1.4F : 1;
			cast = cast.blend(BossMotion.forecast(orbCd, rate, 9));
			sweep = sweep.blend(BossMotion.forecast(gustCd, rate, 10));
			if (phase >= 2) sweep = sweep.blend(BossMotion.forecast(tornadoCd, 1, 10));
		}
		headRig = BossPoses.wyvern(this::placeModel, idle, t, modelYaw(), flight, cast, sweep, motion(CHANNEL), motion(PHASE));
	}

	private Vector pos() {
		return mob.getLocation().toVector();
	}

	/**
	 * v1.5.0: 이동 상태는 직접 들고 있는다. 1.21.11 좀벌레(AI/중력 끔)는 setVelocity 의 Y 가 먹지 않아
	 * 히트박스가 높이 고정이었다 → XZ 는 속도로, Y 는 승객(파츠)을 유지한 채 텔레포트로 옮긴다.
	 */
	private final Vector vel = new Vector();
	/** 상승·하강·고도 변경 속도 배율 (수평 비행 속도는 그대로, 상하만 절반) */
	private static final double VERT = 0.5;
	/** 급강하 중에는 경로의 블록을 무시한다 (지형에 걸려 멈추지 않음) */
	private boolean noClip;

	private void drive(Vector v) {
		drive(v, true);
	}

	private void drive(Vector v, boolean slowVertical) {
		vel.copy(v);
		double dy = v.getY() * (slowVertical ? VERT : 1);
		if (noClip) {
			// 블록 충돌 없이 통째로 이동 (XZ 도 텔레포트: 속도로 밀면 벽에 막힌다)
			mob.setVelocity(new Vector());
			Location at = mob.getLocation().add(v.getX(), dy, v.getZ());
			mob.teleport(at, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
			return;
		}
		mob.setVelocity(new Vector());
		Location l = mob.getLocation();
		double nx = l.getX() + v.getX();
		double ny = l.getY() + dy;
		double nz = l.getZ() + v.getZ();
		if (dy < 0 && BU.solidAt(mob.getWorld(), new Vector(l.getX(), ny, l.getZ()))) {
			ny = l.getY();
			vel.setY(0);
		}
		if (BU.solidAt(mob.getWorld(), new Vector(nx, ny, nz))) {
			nx = l.getX();
			nz = l.getZ();
		}
		if (Math.abs(nx - l.getX()) > 1e-4 || Math.abs(ny - l.getY()) > 1e-4 || Math.abs(nz - l.getZ()) > 1e-4) {
			Location to = new Location(mob.getWorld(), nx, ny, nz, l.getYaw(), l.getPitch());
			mob.teleport(to, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
		}
	}

	private void steer(Vector desired, double accel, double maxSpeed) {
		Vector d = desired.clone().subtract(pos());
		double len = d.length();
		Vector v = vel.clone().multiply(0.88);
		if (len > 1e-6) v.add(d.multiply(1 / len).multiply(Math.min(len, 6) * accel));
		if (v.length() > maxSpeed) v.normalize().multiply(maxSpeed);
		drive(v);
	}

	/** t385: 플레이어 추격(선회) 중 수평(XZ) 속도 배율. 상하 속도·다른 이동은 그대로 */
	private static final double CHASE_H = 1.5;

	/** steer 와 같은 계산(관성·가속·최대 속도 그대로)에, 실제 이동만 XZ ×CHASE_H. 내부 속도 상태는 원래 값으로 유지해 누적되지 않는다 */
	private void steerChase(Vector desired, double accel, double maxSpeed) {
		Vector d = desired.clone().subtract(pos());
		double len = d.length();
		Vector v = vel.clone().multiply(0.88);
		if (len > 1e-6) v.add(d.multiply(1 / len).multiply(Math.min(len, 6) * accel));
		if (v.length() > maxSpeed) v.normalize().multiply(maxSpeed);
		drive(new Vector(v.getX() * CHASE_H, v.getY(), v.getZ() * CHASE_H));
		vel.copy(v);
	}

	private void faceVelocity(float maxTurn) {
		Vector v = vel;
		double hz = v.getX() * v.getX() + v.getZ() * v.getZ();
		if (hz > 1e-3) {
			float want = (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
			float diff = wrap(want - flyYaw);
			float turn = clamp(diff, -maxTurn, maxTurn);
			flyYaw += turn;
			flyRoll = lerp(0.2F, flyRoll, clamp(-diff * 0.6F, -35, 35));
		}
		float wantPitch = (float) Math.toDegrees(Math.atan2(v.getY() * VERT, Math.sqrt(hz) + 1e-3));
		flyPitch = lerp(0.25F, flyPitch, clamp(wantPitch, -60, 45));
		if (tickCount % 5 == 0) {
			mob.setRotation(flyYaw, 0);
			mob.getPersistentDataContainer().set(Keys.YAW, PersistentDataType.FLOAT, flyYaw);
		}
	}

	@Override
	protected void onPhaseChange(int p) {
		if (!modelMode) {
			for (int s = 0; s < 2; s++) { if (wing[s * 4] != null) wing[s * 4].setBlock(membrane()); if (wing[s * 4 + 2] != null) wing[s * 4 + 2].setBlock(membrane()); }
			if (fin != null) fin.setBlock(membrane());
		}
		if (p == 2) {
			BU.say(mob, Component.text("템페스트 와이번: \"하늘이 울부짖는다... 폭풍의 눈 속으로 들어와라!\"", NamedTextColor.AQUA, TextDecoration.ITALIC));
			BU.say(mob, Component.text("⚠ 2페이즈: 회오리 등장 - 휘말리면 하늘로 날아갑니다!", NamedTextColor.GRAY));
		} else if (p == 3) {
			BU.say(mob, Component.text("템페스트 와이번: \"뇌운이여, 강림하라!!\"", NamedTextColor.YELLOW, TextDecoration.BOLD));
			BU.say(mob, Component.text("⚠ 3페이즈: 뇌운 강림 - 노란 원 밖으로 피하세요!", NamedTextColor.GRAY));
		}
		refreshGlow();
	}

	@Override
	protected void bossTick(List<Player> near, LivingEntity target) {
		World w = mob.getWorld();
		if (home == null) home = pos();
		noClip = mode == M_DIVE;
		modeTimer--;
		float spd = phase >= 3 ? 1.4F : 1.0F;
		orbCd -= spd;
		gustCd -= spd;
		diveCd -= 1;
		breathCd -= 1;
		if (phase >= 2) { tornadoCd -= 1; vortexCd -= 1; }
		if (phase >= 3) stormCd -= 1;

		if (target == null) {
			if (mode == M_DIVE || mode == M_DIVE_AIM) { mode = M_CIRCLE; noClip = false; }
			steer(home.clone().add(new Vector(Math.cos(tickCount * 0.02) * 6, 2, Math.sin(tickCount * 0.02) * 6)), 0.02, 0.35);
			faceVelocity(6);
			return;
		}
		Location here = mob.getLocation();
		switch (mode) {
			case M_CIRCLE -> {
				circleAngle += 0.025 * spd;
				double r = 5.0;
				Vector desired = target.getLocation().toVector().add(new Vector(Math.cos(circleAngle) * r, 8.5 + Math.sin(tickCount * 0.05) * 1.5, Math.sin(circleAngle) * r));
				steerChase(desired, 0.03, 0.65);   // t385: 추격 수평 속도 ×1.5 (최대 0.65 → XZ 0.975)
				faceVelocity(8);
				if (tickCount % 8 == 0 && !near.isEmpty()) Fx.windWake(w, pos(), flyYaw, 3.1, false);
				if (invulTicks > 0) break;
				if (orbCd <= 0) { orbs(target, phase >= 2 ? 5 : 3); orbCd = ORB_CD; }
				if (gustCd <= 0) { gust(target); gustCd = GUST_CD; }
				if (phase >= 2 && tornadoCd <= 0) { tornado(target); tornadoCd = TORNADO_CD; }
				if (phase >= 3 && stormCd <= 0) { storm(); stormCd = STORM_CD; }
				// v1.2.0: 번개 브레스 / 폭풍 소용돌이
				if (breathCd <= 0) {
					Vector o = mouth();
					Vector aim = target.getLocation().toVector().add(new Vector(0, 0.8, 0)).subtract(o);
					double blen = Math.min(34, aim.length() + 8);
					BU.sound(here, "hobbang:wyvern.roar", SoundCategory.HOSTILE, 2.0F, 1.0F);
					BossPatterns.beam(this, () -> o, aim, blen, 26, 16, 9, Material.LIGHT_BLUE_STAINED_GLASS, 0x9FE8FF);
					breathCd = breathCooldown();
				}
				if (phase >= 2 && vortexCd <= 0) {
					int[] col = BossPatterns.colors(kind);
					BossPatterns.warnAll(this, "폭풍 소용돌이! 원 밖으로 달리세요!");
					BossPatterns.vortex(this, target.getLocation().toVector(), 8, 70, 11, col[0], col[1]);
					vortexCd = VORTEX_CD;
				}
				if (diveCd <= 0) {
					diveAimTotal = phase >= 3 ? 15 : 30;
					mode = M_DIVE_AIM; modeTimer = diveAimTotal; diveCd = diveCooldown();
					Fx.discardBeams(uuid);
					Location tl = target.getLocation();
					diveTarget = new Vector(tl.getX(), BU.groundY(w, tl.getX(), tl.getY() + 1, tl.getZ()), tl.getZ());
					BU.sound(here, Sound.ENTITY_PHANTOM_SWOOP, SoundCategory.HOSTILE, 3F, 0.5F);
					BU.sound(here, "hobbang:wyvern.flap", SoundCategory.HOSTILE, 1.5F, 1.0F);
					BU.sound(here, "hobbang:wyvern.roar", SoundCategory.HOSTILE, 2.0F, 1.05F);
					BU.say(mob, Component.text("⚠ 와이번이 급강하를 준비합니다! 붉은 원에서 벗어나세요!", NamedTextColor.RED));
				}
			}
			case M_DIVE_AIM -> {
				steer(diveTarget.clone().add(new Vector(0, 12, 0)), 0.05, 0.6);
				faceVelocity(12);
				double radius = 5.5 + mob.getWidth() / 2;
				if (modeTimer % 2 == 0) BU.ring(w, Particle.DUST, BU.dust(0xFF2020, 1.6F), diveTarget, radius, 44);
				if (modeTimer % 4 == 0) Fx.groundMark(w, diveTarget, radius, 1 - modeTimer / (double) diveAimTotal, kind);
				if (modeTimer <= 0) { mode = M_DIVE; modeTimer = 70; Fx.discardBeams(uuid); }
			}
			case M_DIVE -> {
				// 급강하: 블록을 무시하고 빨간 원 한가운데로 곧장 내려꽂힌다. 원 위치가 어긋나 있어도 원 중심에 정확히 붙는다
				Vector d = diveTarget.clone().subtract(pos());
				double len = d.length();
				Vector dir = len > 1e-6 ? d.clone().multiply(1 / len) : new Vector(0, -1, 0);
				double speed = phase >= 3 ? 2.0 : 1.6;
				drive(dir.clone().multiply(Math.min(speed, len)));
				faceVelocity(30);
				w.spawnParticle(Particle.CLOUD, here.getX(), here.getY() + 0.5, here.getZ(), 3, 0.5, 0.3, 0.5, 0.01);
				if (modeTimer % 3 == 0) Fx.windWake(w, here.toVector(), flyYaw, 1.25, false);
				// 상하 속도가 절반이라 수직 성분이 남으면 계속 내려온다. 원 중심에 닿거나 시간이 다 되면 착지
				boolean arrived = Math.hypot(pos().getX() - diveTarget.getX(), pos().getZ() - diveTarget.getZ()) < 0.6 && pos().getY() - diveTarget.getY() < 0.8;
				if (arrived || modeTimer <= 0) {
					noClip = false;
					Location land = new Location(w, diveTarget.getX(), diveTarget.getY(), diveTarget.getZ(), mob.getLocation().getYaw(), 0);
					mob.setVelocity(new Vector());
					mob.teleport(land, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
					vel.zero();
					Location at = mob.getLocation();
					BU.sound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.5F, 0.7F);
					BU.sound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.6F, 0.7F);
					w.spawnParticle(Particle.EXPLOSION_EMITTER, at.getX(), at.getY(), at.getZ(), 1, 0, 0, 0, 0);
					w.spawnParticle(Particle.GUST_EMITTER_LARGE, at.getX(), at.getY(), at.getZ(), 1, 0, 0, 0, 0);
					Fx.slam(this, pos(), 5.5 + mob.getWidth() / 2);
					var victims = BU.victims(w, mob.getBoundingBox().expand(5.5, 3, 5.5));
					for (Player p : victims) BU.hit(p, mob, 12, pos(), 1.2, 0.8);
					int[] col = BossPatterns.colors(kind);
					BossPatterns.shockRing(this, pos(), 10, 0.5, 7, 12, col[0], col[1]);   // v1.2.0 폭풍 고리 (점프로 피하기)
					if (victims.isEmpty()) {
						mode = M_REST; modeTimer = 120;
						restContact = false;
						landingAt = tickCount;
						BU.say(mob, Component.text("✦ 와이번이 지쳐 땅에 내려앉았습니다!", NamedTextColor.GREEN));
					} else {
						mode = M_CLIMB; modeTimer = 30;
						BU.sound(at, "hobbang:wyvern.flap", SoundCategory.HOSTILE, 3.0F, 1.0F);
					}
				}
			}
			case M_REST -> {
				// 착지 후엔 남은 속도를 죽이고, 공중이면 천천히 내려앉는다 (블록 검사는 drive 가 함)
				drive(vel.clone().multiply(0.5).setY(-0.12));
				if (!restContact && vel.getY() == 0) {
					restContact = true;
					if (tickCount - landingAt > 5) landingAt = tickCount;
				}
				if (modeTimer % 10 == 0) w.spawnParticle(Particle.CLOUD, here.getX(), here.getY() + 1, here.getZ(), 4, 0.6, 0.3, 0.6, 0.01);
				if (modeTimer <= 0) { mode = M_CLIMB; modeTimer = 30; BU.sound(here, "hobbang:wyvern.flap", SoundCategory.HOSTILE, 3.0F, 1.0F); }
			}
			case M_CLIMB -> {
				double yr = Math.toRadians(flyYaw);
				Vector look = new Vector(-Math.sin(yr), 0, Math.cos(yr));
				steer(pos().add(new Vector(look.getX() * 4, 8, look.getZ() * 4)), 0.05, 0.6);
				faceVelocity(8);
				if (modeTimer % 4 == 0 && !near.isEmpty()) Fx.windWake(w, pos(), flyYaw, 3.3, true);
				if (modeTimer <= 0) mode = M_CIRCLE;
			}
			default -> mode = M_CIRCLE;
		}
		if (idle.active() && tickCount % 20 == 0 && mode != M_REST) {
			BU.sound(here, "hobbang:wyvern.flap", SoundCategory.HOSTILE, 1.3F, 0.92F);
			BU.sound(here, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 1.1F, 0.72F);
		}
	}

	private Vector mouth() {
		if (headRig != null) return worldPoint(headRig, 0, 0, 0);
		double yr = Math.toRadians(flyYaw);
		Vector look = new Vector(-Math.sin(yr), 0, Math.cos(yr));
		return pos().add(look.multiply(2.6)).add(new Vector(0, 0.9, 0));
	}

	private void orbs(LivingEntity target, int n) {
		cue(CAST, 0, 0, 13);
		World world = mob.getWorld();
		BU.sound(mob.getLocation(), Sound.ENTITY_BREEZE_SHOOT, SoundCategory.HOSTILE, 2F, 0.6F);
		BU.sound(mob.getLocation(), "hobbang:wyvern.breath", SoundCategory.HOSTILE, 1.5F, 1.0F);
		Vector m = mouth();
		Fx.burst(world, m, kind, 13, 0.46);
		for (int i = 0; i < n; i++) {
			Vector aim = target.getLocation().toVector().add(new Vector((i - n / 2) * 1.8, 1, (random.nextDouble() - 0.5) * 2));
			Vector vel = aim.subtract(m).normalize().multiply(0.55);
			Fx fx = Fx.spawn(world, uuid, m, 80, (f, w) -> {
				if (f.age == 1) {
					BlockDisplay d = f.addBlock(bd(Material.LIGHT_BLUE_STAINED_GLASS), true);
					BlockDisplay core = f.addBlock(bd(Material.SEA_LANTERN), true);
					d.setGlowing(true); d.setGlowColorOverride(Color.fromRGB(0x80FFFF));
					Fx.shape(d, new Vector3f(-0.35F), new Quaternionf(), new Vector3f(0.7F), 1);
					Fx.shape(core, new Vector3f(-0.18F), new Quaternionf(), new Vector3f(0.36F), 1);
				}
				if (f.age % 2 == 0) {
					for (Display d : f.displays) {
						float s = d instanceof BlockDisplay bdd && bdd.getBlock().getMaterial() == Material.SEA_LANTERN ? 0.36F : 0.7F;
						Fx.shape(d, new Vector3f(-s / 2), new Quaternionf().rotateXYZ(f.age * 0.3F, f.age * 0.4F, 0), new Vector3f(s), 2);
					}
				}
				Vector p0 = f.pos;
				w.spawnParticle(Particle.ELECTRIC_SPARK, p0.getX(), p0.getY(), p0.getZ(), 2, 0.1, 0.1, 0.1, 0.02);
				if (f.age % 3 == 0) Fx.puff(w, Particle.SNOWFLAKE, p0.clone().subtract(f.vel), 3, 0.22, 0.03);
				boolean burst = BU.solidAt(w, p0);
				if (!burst && !BU.victims(w, BU.box(p0, 1.2, 1.2, 1.2)).isEmpty()) burst = true;
				if (burst && f.age > 3) {
					Fx.burst(w, p0, kind, 12, 0.68);
					Fx.ripple(this, p0, 2.5, 8);
					w.spawnParticle(Particle.ELECTRIC_SPARK, p0.getX(), p0.getY(), p0.getZ(), 30, 0.8, 0.8, 0.8, 0.3);
					w.spawnParticle(Particle.EXPLOSION, p0.getX(), p0.getY(), p0.getZ(), 1, 0, 0, 0, 0);
					BU.sound(new Location(w, p0.getX(), p0.getY(), p0.getZ()), Sound.ITEM_TRIDENT_THUNDER, SoundCategory.HOSTILE, 0.8F, 1.6F);
					for (Player p : BU.victims(w, BU.box(p0, 2.5, 2.5, 2.5))) BU.hit(p, mob, 8, p0, 0.4, 0.3);
					f.kill();
				}
			});
			fx.vel = vel;
		}
	}

	private void gust(LivingEntity target) {
		cue(SWEEP, 0, 0, 14);
		Vector m = mouth();
		World w = mob.getWorld();
		Fx.puff(w, Particle.GUST, m, 4, 0.46, 0.02);
		Fx.puff(w, Particle.CLOUD, m, 10, 0.58, 0.045);
		for (int i = 0; i < 2; i++) {
			Vector dir = target.getLocation().toVector().add(new Vector(0, 1, 0)).subtract(m).normalize()
				.add(new Vector((random.nextDouble() - 0.5) * 0.2, 0, (random.nextDouble() - 0.5) * 0.2));
			WindCharge charge = w.spawn(new Location(w, m.getX(), m.getY(), m.getZ()), WindCharge.class, wc -> {
				wc.setShooter(mob);
				wc.setVelocity(dir.clone().normalize().multiply(0.8));
			});
			Fx.projectileTrail(this, charge, 40);
		}
		BU.sound(mob.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, SoundCategory.HOSTILE, 2F, 0.6F);
	}

	private void tornado(LivingEntity target) {
		cue(SWEEP, 0, 12, 13);
		World world = mob.getWorld();
		BU.sound(target.getLocation(), Sound.ENTITY_BREEZE_IDLE_AIR, SoundCategory.HOSTILE, 3F, 0.4F);
		for (int i = 0; i < 2; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			Vector start = target.getLocation().toVector().add(new Vector(Math.cos(a) * 7, 0, Math.sin(a) * 7));
			start.setY(BU.groundY(world, start.getX(), start.getY() + 3, start.getZ()));
			Map<UUID, Integer> cd = new HashMap<>();
			Fx.spawn(world, uuid, start, 200, (f, w) -> {
				if (f.age == 1) {
					for (int k = 0; k < 5; k++) f.addBlock(k % 2 == 0 ? bd(Material.WHITE_STAINED_GLASS) : bd(Material.LIGHT_GRAY_STAINED_GLASS), false);
				}
				if (f.age % 2 == 0) {
					for (int k = 0; k < f.displays.size(); k++) {
						float wd = 0.7F + k * 0.45F;
						float grow = Math.min(1F, f.age / 20F) * (f.age > 185 ? Math.max(0, (200 - f.age) / 15F) : 1);
						Quaternionf q = new Quaternionf().rotateY(f.age * (0.35F + k * 0.08F) * (k % 2 == 0 ? 1 : -1));
						Fx.shape(f.displays.get(k), new Vector3f(-wd / 2, k * 0.9F, -wd / 2).rotate(q).mul(grow), q, new Vector3f(wd, 0.85F, wd).mul(grow), 2);
					}
				}
				Player chase = null;
				double best = 24 * 24;
				for (Player p : w.getPlayers()) {
					if (!BU.valid(p)) continue;
					double d = p.getLocation().toVector().distanceSquared(f.pos);
					if (d < best) { best = d; chase = p; }
				}
				if (chase != null) {
					Vector d = chase.getLocation().toVector().subtract(f.pos).setY(0);
					if (d.lengthSquared() > 0.01) f.vel = d.normalize().multiply(0.13);
				}
				double gy = BU.groundY(w, f.pos.getX(), f.pos.getY() + 2, f.pos.getZ());
				f.pos.setY(gy);
				w.spawnParticle(Particle.CLOUD, f.pos.getX(), f.pos.getY() + 0.3, f.pos.getZ(), 3, 0.8, 0.1, 0.8, 0.02);
				if (f.age % 4 == 0) {
					for (int band = 0; band < 4; band++) {
						double angle = f.age * 0.33 + band * 1.4;
						double r = 0.6 + band * 0.42;
						Vector swirl = f.pos.clone().add(new Vector(Math.cos(angle) * r, 0.35 + band, Math.sin(angle) * r));
						Fx.puff(w, Particle.SNOWFLAKE, swirl, 2, 0.14, 0.02);
					}
				}
				org.bukkit.util.BoundingBox box = new org.bukkit.util.BoundingBox(f.pos.getX() - 1.8, f.pos.getY(), f.pos.getZ() - 1.8,
					f.pos.getX() + 1.8, f.pos.getY() + 4.5, f.pos.getZ() + 1.8);
				for (Player p : BU.victims(w, box)) {
					int last = cd.getOrDefault(p.getUniqueId(), -100);
					if (f.age - last > 20) {
						cd.put(p.getUniqueId(), f.age);
						p.damage(4, BU.mob(mob));
						Vector v = p.getVelocity();
						p.setVelocity(new Vector(v.getX() * 0.3, 1.3, v.getZ() * 0.3));
					}
				}
			});
		}
	}

	private void storm() {
		Fx.awaken(this, CHANNEL, 29, 0, 15);
		World world = mob.getWorld();
		BU.sound(mob.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2F, 1.4F);
		for (Player p : nearbyPlayers(40)) {
			if (!BU.valid(p)) continue;
			for (int k = 0; k < 4; k++) {
				double a = k * Math.PI * 2 / 3 + random.nextDouble();
				Vector at = p.getLocation().toVector();
				if (k != 3) at.add(new Vector(Math.cos(a) * 2.8, 0, Math.sin(a) * 2.8));
				Vector g = new Vector(at.getX(), BU.groundY(world, at.getX(), at.getY() + 2, at.getZ()), at.getZ());
				Fx.spawn(world, uuid, g, 36, (f, w) -> {
					if (f.age < 30 && f.age % 3 == 0) BU.ring(w, Particle.DUST, BU.dust(0xFFE040, 1.4F), g, 1.8, 16);
					if (f.age < 30 && f.age % 6 == 0) Fx.groundMark(w, g, 1.8, f.age / 30.0, kind);
					if (f.age == 30) {
						BU.lightning(w, g.getX(), g.getY(), g.getZ());
						Fx.burst(w, g.clone().add(new Vector(0, 0.6, 0)), kind, 12, 0.65);
						Fx.puff(w, Particle.ELECTRIC_SPARK, g.clone().add(new Vector(0, 1.0, 0)), 8, 0.45, 0.15);
						Fx.ripple(this, g, 1.8, 6);
						for (Player v : BU.victims(w, BU.box(g, 1.8, 3, 1.8))) v.damage(10, BU.mob(mob));
					}
				});
			}
		}
	}

	@Override
	protected void dropLoot(List<ItemStack> drops, Random r) {
		drops.add(HItem.STORM_FEATHER.create(4 + r.nextInt(5)));
		drops.add(HItem.RADIANT_STONE.create(8 + r.nextInt(7)));
		drops.add(HItem.IMMORTAL_SCROLL.create(1 + r.nextInt(2)));
		drops.add(HItem.LUCK_CHARM.create(3));
		if (r.nextFloat() < 0.3F) drops.add(HItem.STORMCALLER.create());
	}

	@Override
	protected int deathDuration() { return 110; }

	@Override
	protected void onDeathStart() {
		deathU = 0;
		noClip = false;
		mob.setGravity(false);   // v1.5.0: 낙하는 drive() 로 직접 (중력/속도 Y 가 안 먹는다)
		if (modelMode) {
			if (head != null && head.display instanceof org.bukkit.entity.ItemDisplay idsp && idsp.isValid()) {
				ItemStack st = new ItemStack(Material.PAPER);
				st.editMeta(m -> m.setItemModel(new org.bukkit.NamespacedKey("hobbang", "boss/tempest_wyvern/head_off")));
				idsp.setItemStack(st);
				idsp.setBrightness(new Display.Brightness(0, 0));
			}
			for (Part p : parts) {
				if (p != null && p.display.isValid()) p.display.setGlowing(false);
			}
		}
		Fx.burst(mob.getWorld(), mouth(), kind, 24, 1.25);
		BU.sound(mob.getLocation(), "hobbang:wyvern.roar.awaken", SoundCategory.HOSTILE, 3.0F, 1.0F);
	}

	@Override
	protected void poseDeath(float u) {
		deathU = u;
		// 처음 0.32 구간엔 땅에 닿을 때까지 떨어진다 (3틱마다 호출되므로 틱당 0.55 × 3)
		if (u < 0.32F && mode != M_REST) drive(new Vector(vel.getX() * 0.7, -1.65, vel.getZ() * 0.7), false);   // 죽을 때 추락은 상하 감속 없이
		else drive(new Vector(), false);
		if (modelMode) poseModel(animTick);
		else poseParts(animTick); // 레거시 블록 모델: 기존 목/꼬리 + 기울어짐은 poseParts가 flyRoll을 씀
	}

	@Override
	protected void deathFx(int tick, float u) {
		org.bukkit.Location l = mob.getLocation();
		if (tick % 4 == 0) l.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, l.getX(), l.getY() + 0.8, l.getZ(), 3, 0.8, 0.4, 0.8, 0.01);
		if (u > 0.72F && tick % 3 == 0) l.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, l.getX(), l.getY(), l.getZ(), 8, 0.7, 0.3, 0.7, 0.02);
	}

}
