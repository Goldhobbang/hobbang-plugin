package com.hobbang.enhance.boss;

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
	private float orbCd = 60, diveCd = 160, gustCd = 100, tornadoCd = 120, stormCd = 100;
	private float breathCd = 160, vortexCd = 260;
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
	private final java.util.Map<String, Part> mRig = new java.util.HashMap<>();
	private Part mp(String n) { return mRig.get(n); }
	private Rigid headRig;
	private float yoff = -0.45F, flyBlend = 1F, lookY = 0, lookP = 0, deathU = 0;
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
	@Override protected float damageMultiplier() { return mode == M_REST ? 1.5F : 1.0F; }
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
		mRig.clear();
		body = belly = neck = head = jaw = eyeL = eyeR = hornL = hornR = fin = null;
		java.util.Arrays.fill(tail, null);
		java.util.Arrays.fill(wing, null);
		if (modelMode) {
			mRig.clear();
			for (String n : W_PARTS) mRig.put(n, model(n, false));
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
		float amp, base, flapSpeed;
		if (mode == M_DIVE) { amp = 6; base = -25; flapSpeed = 0.1F; }
		else if (mode == M_REST) { amp = 8; base = -40; flapSpeed = 0.15F; }
		else if (mode == M_DIVE_AIM) { amp = 45; base = 5; flapSpeed = 0.55F; }
		else { amp = 35; base = 0; flapSpeed = phase >= 3 ? 0.45F : 0.32F; }
		flap += flapSpeed * ANIM_INTERVAL;
		if (modelMode) { poseModel(t, amp, base); return; }
		Rigid root = Rigid.yaw(modelYaw());
		float bob = mode == M_REST ? 0 : sin(flap) * -0.15F;
		Rigid b = root.at(0, 0.8F + bob, 0).rotX(-flyPitch).rotZ(flyRoll);
		place(body, b);
		place(belly, b);
		Rigid nk = b.at(0, 0.1F, 1.15F).rotX(-15 + sin(t * 0.07F) * 6);
		place(neck, nk);
		float jawOpen = (mode == M_DIVE_AIM || mode == M_DIVE) ? 35 : Math.max(0, sin(t * 0.05F)) * 10;
		Rigid hd = nk.at(0, 0, 0.95F).rotX(20 + sin(t * 0.07F + 1) * 6);
		headRig = hd.at(0, -0.1F, 1.0F);
		place(head, hd);
		place(jaw, hd.at(0, -0.35F, 0.1F).rotX(jawOpen));
		place(eyeL, hd.at(0.46F, 0.1F, 0.65F));
		place(eyeR, hd.at(-0.46F, 0.1F, 0.65F));
		place(hornL, hd.at(0.3F, 0.35F, 0.15F).rotX(-35).rotY(-15));
		place(hornR, hd.at(-0.3F, 0.35F, 0.15F).rotX(-35).rotY(15));
		Rigid tl = b.at(0, 0, -1.15F);
		float[] lens = {1.1F, 1.0F, 1.0F};
		for (int i = 0; i < 3; i++) {
			tl = tl.rotY(sin(t * 0.12F - i * 0.8F) * 16).rotX(sin(t * 0.09F - i) * 6 + 4);
			place(tail[i], tl);
			tl = tl.at(0, 0, -lens[i]);
		}
		place(fin, tl.rotY(sin(t * 0.12F - 3) * 20));
		for (int s = 0; s < 2; s++) {
			float sx = s == 0 ? 1 : -1;
			float a1 = base + sin(flap) * amp;
			float a2 = base * 0.5F + sin(flap - 0.7F) * amp * 0.8F;
			Rigid sh = b.at(sx * 0.6F, 0.3F, 0.3F).rotZ(sx * a1).rotY(sx * -8);
			place(wing[s * 4], sh);
			place(wing[s * 4 + 1], sh);
			Rigid out = sh.at(sx * 2.15F, 0, 0).rotZ(sx * a2).rotY(sx * -12);
			place(wing[s * 4 + 2], out);
			place(wing[s * 4 + 3], out);
		}
	}

	/**
	 * v1.5.0: 박스 리그 36파트 (obj2mc/make_wyvern.py). 관절 rest 자세는 joint() 가 걸고, 여기서는 그 위에 움직임만 더한다.
	 * 목·꼬리는 마디마다 위상을 늦춰 물결치고, 날개는 어깨(퍼덕임) → 팔꿈치 → 손가락 3개가 순서대로 접혔다 펴진다.
	 */
	private void poseModel(float t, float amp, float base) {
		boolean rest = mode == M_REST;
		boolean dive = mode == M_DIVE || mode == M_DIVE_AIM;
		yoff = lerp(0.25F, yoff, rest ? 0F : -0.2F);
		flyBlend = lerp(0.18F, flyBlend, rest ? 0F : 1F);
		float breath = sin(t * 0.06F);
		float sway = sin(flap);

		// look-at: 목 마디와 머리에 나눠 걸고, 급하게 튀지 않게 보간
		float wantY = 0, wantP = dive ? 16 : sin(t * 0.07F + 1) * 4;
		LivingEntity tgt = mob.getTarget();
		if (tgt != null && tgt.isValid()) {
			org.bukkit.util.Vector to = tgt.getEyeLocation().toVector().subtract(mob.getLocation().toVector().add(new org.bukkit.util.Vector(0, 2.6, 0)));
			double hz = Math.sqrt(to.getX() * to.getX() + to.getZ() * to.getZ()) + 1e-3;
			float yawTo = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
			wantY = clamp(wrap(yawTo - flyYaw), -48, 48);
			float pitTo = (float) Math.toDegrees(Math.atan2(-to.getY(), hz));
			wantP = clamp(pitTo + flyPitch * 0.35F, -30, 36);
		}
		lookY = lerp(0.22F, lookY, wantY);
		lookP = lerp(0.22F, lookP, wantP);

		Rigid root = Rigid.yaw(modelYaw());
		float bob = rest ? breath * 0.04F : sway * -0.22F + breath * 0.06F;
		float dRoll = 0, dPitch = 0, dSink = 0, dScale = 1F;
		if (dying) {
			float fall = deathU < 0.32F ? deathU / 0.32F : 1F;
			float side = deathU > 0.28F ? Math.min(1F, (deathU - 0.28F) / 0.42F) : 0F;
			float fade = deathU > 0.72F ? Math.min(1F, (deathU - 0.72F) / 0.28F) : 0F;
			flyBlend = lerp(0.35F, flyBlend, 0F);
			dPitch = 28 * fall + 18 * side;
			dRoll = 78 * side;
			dSink = 0.3F * side + 1.8F * fade;
			dScale = Math.max(0.05F, 1F - fade);
			lookP = lerp(0.3F, lookP, 40 * side);
			lookY = lerp(0.25F, lookY, 0);
			yoff = lerp(0.3F, yoff, -0.1F);
		}
		Rigid b = joint(root.at(0, yoff + bob - dSink, 0), "body")
			.rotX(-flyPitch + spawnPitch + breath * 2.5F + dPitch)
			.rotZ(flyRoll + dRoll);
		place(mp("body"), b, (1F + breath * 0.015F) * dScale);

		// 척추 전·중·후: 허리와 엉덩이가 가슴에 대해 차례로 늦게 출렁인다
		float spA = 4 + 8 * flyBlend, spB = 3 + 6 * flyBlend;
		Rigid waist = joint(b, "waist").rotY(sin(t * 0.08F) * spA * 0.45F).rotX(sin(t * 0.07F - 0.6F) * spB * 0.45F - breath * 0.7F);
		place(mp("waist"), waist, dScale);
		Rigid hips = joint(waist, "hips").rotY(sin(t * 0.08F - 0.35F) * spA * 0.55F).rotX(sin(t * 0.07F - 0.95F) * spB * 0.55F - breath * 0.8F);
		place(mp("hips"), hips, dScale);

		// 목 3단 + 머리 + 턱. 비행 중엔 목을 앞으로 뻗고(rest 는 서 있는 S자), 시선은 마디에 나눠 건다.
		float nSwayY = sin(t * 0.05F) * 4, nSwayP = sin(t * 0.07F) * 3;
		Rigid n1 = joint(b, "neck_1").rotY(lookY * 0.24F + nSwayY).rotX(lookP * 0.20F + nSwayP + 24 * flyBlend);
		place(mp("neck_1"), n1, dScale);
		Rigid n2 = joint(n1, "neck_2").rotY(lookY * 0.26F + sin(t * 0.05F - 0.45F) * 3).rotX(lookP * 0.24F + sin(t * 0.07F - 0.45F) * 2.2F + 8 * flyBlend);
		place(mp("neck_2"), n2, dScale);
		Rigid n3 = joint(n2, "neck_3").rotY(lookY * 0.25F + sin(t * 0.05F - 0.9F) * 2).rotX(lookP * 0.24F + sin(t * 0.07F - 0.9F) * 1.6F - 8 * flyBlend);
		place(mp("neck_3"), n3, dScale);
		Rigid hd = joint(n3, "head").rotY(lookY * 0.25F).rotX(lookP * 0.32F - 20 * flyBlend + (dive ? 8 : 0));
		headRig = hd.at(0, -0.1F, 1.05F);
		place(mp("head"), hd, dScale);
		float jawOpen = dying ? 30 * Math.min(1F, deathU * 3) : dive ? 26 : Math.max(0, sin(t * 0.05F)) * 7;
		place(mp("jaw"), joint(hd, "jaw").rotX(jawOpen), dScale);

		// 꼬리 6단, 위상 지연 follow-through
		Rigid tl = hips;
		for (int i = 0; i < 6; i++) {
			float lag = i * 0.6F;
			tl = joint(tl, "tail_" + (i + 1))
				.rotY(sin(t * 0.12F - lag) * (6 + 3 * flyBlend) + (dying ? 12 * deathU * (i + 1) : 0))
				.rotX(sin(t * 0.09F - lag) * (3 + 1.5F * flyBlend) + 2 * flyBlend + (dying ? 8 * deathU : 0));
			place(mp("tail_" + (i + 1)), tl, dScale);
		}

		// 날개: fold 0 = rest(활짝), 1 = 접힘. 업스트로크에서 반쯤 접고 다운스트로크에서 편다.
		// 왼쪽(+X) 기준: rotZ + = 들어올림, rotY + = 뒤로 젖힘. 오른쪽은 부호 반대.
		float a = rest ? -40 : -30 + base * 0.6F + sway * amp;
		float up = rest || dive ? 0 : Math.max(0, (float) Math.cos(flap));
		float fold = clamp((rest ? 0.8F : dive ? 0.6F : 0.3F * up) + spawnFold, 0, 1);
		float dWing = dying ? 40 * Math.min(1F, Math.max(0, deathU - 0.28F) / 0.42F) : 0;
		if (dying) fold = Math.max(fold, Math.min(1F, deathU * 1.6F));
		float[] fClose = {70, 38, 6};
		for (int sd = 0; sd < 2; sd++) {
			float sx = sd == 0 ? 1 : -1;
			String q = sd == 0 ? "_l" : "_r";
			Rigid arm = joint(b, "wing_arm" + q).rotZ(sx * (a - dWing)).rotY(sx * 30 * fold);
			place(mp("wing_arm" + q), arm, dScale);
			Rigid fore = joint(arm, "wing_fore" + q).rotY(sx * 62 * fold).rotZ(sx * sin(flap - 0.5F) * 6 * (1 - fold));
			place(mp("wing_fore" + q), fore, dScale);
			for (int f = 0; f < 3; f++) {
				float ripple = sin(flap - 0.9F - f * 0.35F) * (4 + 2 * f) * (1 - fold);   // 날개막 출렁임 (뒤 손가락일수록 늦게)
				place(mp("wing_f" + (f + 1) + q), joint(fore, "wing_f" + (f + 1) + q).rotY(sx * fClose[f] * fold).rotZ(sx * ripple), dScale);
			}
		}

		// 다리. flyBlend 0 = 서기(rest), 1 = 몸 아래로 접어 넣기 (rotX + = 뒤로)
		float fsw = sway * 6 * flyBlend;
		poseLeg(b, "fl", 70 * flyBlend + fsw * 0.6F, 35 * flyBlend, 45 * flyBlend, dScale);
		poseLeg(b, "fr", 70 * flyBlend + fsw * 0.6F, 35 * flyBlend, 45 * flyBlend, dScale);
		poseLeg(hips, "hl", 85 * flyBlend + fsw, 10 * flyBlend, 50 * flyBlend, dScale);
		poseLeg(hips, "hr", 85 * flyBlend + fsw, 10 * flyBlend, 50 * flyBlend, dScale);
	}

	private void poseLeg(Rigid parent, String tag, float thighX, float shinX, float footX, float sc) {
		Rigid th = joint(parent, "thigh_" + tag).rotX(thighX);
		place(mp("thigh_" + tag), th, sc);
		Rigid sh = joint(th, "shin_" + tag).rotX(shinX);
		place(mp("shin_" + tag), sh, sc);
		place(mp("foot_" + tag), joint(sh, "foot_" + tag).rotX(footX), sc);
	}

	// ───────── 비행 ─────────
	private Vector pos() {
		return mob.getLocation().toVector();
	}

	/**
	 * v1.5.0: 이동 상태는 직접 들고 있는다. 1.21.11 좀벌레(AI/중력 끔)는 setVelocity 의 Y 가 먹지 않아
	 * 히트박스가 높이 고정이었다 → XZ 는 속도로, Y 는 승객(파츠)을 유지한 채 텔레포트로 옮긴다.
	 */
	private final Vector vel = new Vector();

	private void drive(Vector v) {
		vel.copy(v);
		mob.setVelocity(new Vector(v.getX(), 0, v.getZ()));
		double dy = v.getY();
		if (Math.abs(dy) < 1e-4) return;
		Location l = mob.getLocation();
		l.add(0, dy, 0);
		// 땅/블록을 뚫고 내려가지 않게 (아래로 갈 때만 검사)
		if (dy < 0 && BU.solidAt(mob.getWorld(), new Vector(l.getX(), l.getY(), l.getZ()))) { vel.setY(0); return; }
		mob.teleport(l, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
	}

	private void steer(Vector desired, double accel, double maxSpeed) {
		Vector d = desired.clone().subtract(pos());
		double len = d.length();
		Vector v = vel.clone().multiply(0.88);
		if (len > 1e-6) v.add(d.multiply(1 / len).multiply(Math.min(len, 6) * accel));
		if (v.length() > maxSpeed) v.normalize().multiply(maxSpeed);
		drive(v);
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
		float wantPitch = (float) Math.toDegrees(Math.atan2(v.getY(), Math.sqrt(hz) + 1e-3));
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

	private boolean collided(Vector dir) {
		World w = mob.getWorld();
		Vector p = pos().add(new Vector(0, mob.getHeight() * 0.5, 0));
		return BU.solidAt(w, p.clone().add(dir.clone().multiply(mob.getWidth() * 0.6 + 0.4))) || BU.solidAt(w, pos().add(new Vector(0, -0.2, 0)));
	}

	@Override
	protected void bossTick(List<Player> near, LivingEntity target) {
		World w = mob.getWorld();
		if (home == null) home = pos();
		modeTimer--;
		if (target == null) {
			steer(home.clone().add(new Vector(Math.cos(tickCount * 0.02) * 6, 2, Math.sin(tickCount * 0.02) * 6)), 0.02, 0.35);
			faceVelocity(6);
			return;
		}
		float spd = phase >= 3 ? 1.4F : 1.0F;
		Location here = mob.getLocation();
		switch (mode) {
			case M_CIRCLE -> {
				circleAngle += 0.025 * spd;
				double r = 12.5;
				Vector desired = target.getLocation().toVector().add(new Vector(Math.cos(circleAngle) * r, 8.5 + Math.sin(tickCount * 0.05) * 1.5, Math.sin(circleAngle) * r));
				steer(desired, 0.03, 0.65);
				faceVelocity(8);
				if (invulTicks > 0) break;
				orbCd -= spd; diveCd -= spd; gustCd -= spd;
				if (phase >= 2) tornadoCd -= spd;
				if (phase >= 3) stormCd -= spd;
				if (orbCd <= 0) { orbs(target, phase >= 2 ? 5 : 3); orbCd = 80; }
				if (gustCd <= 0) { gust(target); gustCd = 120; }
				if (phase >= 2 && tornadoCd <= 0) { tornado(target); tornadoCd = 300; }
				if (phase >= 3 && stormCd <= 0) { storm(); stormCd = 120; }
				// v1.2.0: 번개 브레스 / 폭풍 소용돌이
				breathCd -= spd;
				if (phase >= 2) vortexCd -= spd;
				if (breathCd <= 0) {
					Vector o = mouth();
					Vector aim = target.getLocation().toVector().add(new Vector(0, 0.8, 0)).subtract(o);
					double blen = Math.min(34, aim.length() + 8);
					BU.sound(here, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2F, 1.6F);
					BossPatterns.beam(this, () -> o, aim, blen, 26, 22, 9, Material.LIGHT_BLUE_STAINED_GLASS, 0x9FE8FF);
					breathCd = phase >= 3 ? 150 : 190;
				}
				if (phase >= 2 && vortexCd <= 0) {
					int[] col = BossPatterns.colors(kind);
					BossPatterns.warnAll(this, "폭풍 소용돌이! 원 밖으로 달리세요!");
					BossPatterns.vortex(this, target.getLocation().toVector(), 8, 70, 11, col[0], col[1]);
					vortexCd = 300;
				}
				if (diveCd <= 0) {
					mode = M_DIVE_AIM; modeTimer = 30; diveCd = phase >= 3 ? 150 : 220;
					Location tl = target.getLocation();
					diveTarget = new Vector(tl.getX(), BU.groundY(w, tl.getX(), tl.getY() + 1, tl.getZ()), tl.getZ());
					BU.sound(here, Sound.ENTITY_PHANTOM_SWOOP, SoundCategory.HOSTILE, 3F, 0.5F);
					BU.say(mob, Component.text("⚠ 와이번이 급강하를 준비합니다! 붉은 원에서 벗어나세요!", NamedTextColor.RED));
				}
			}
			case M_DIVE_AIM -> {
				steer(diveTarget.clone().add(new Vector(0, 12, 0)), 0.05, 0.6);
				faceVelocity(12);
				if (modeTimer % 2 == 0) BU.ring(w, Particle.DUST, BU.dust(0xFF2020, 1.6F), diveTarget, 5.5 + mob.getWidth() / 2, 44);
				if (modeTimer <= 0) { mode = M_DIVE; modeTimer = 40; }
			}
			case M_DIVE -> {
				Vector d = diveTarget.clone().subtract(pos());
				double len = d.length();
				Vector dir = len > 1e-6 ? d.clone().multiply(1 / len) : new Vector(0, -1, 0);
				drive(dir.clone().multiply(phase >= 3 ? 2.0 : 1.6));
				faceVelocity(30);
				w.spawnParticle(Particle.CLOUD, here.getX(), here.getY() + 0.5, here.getZ(), 3, 0.5, 0.3, 0.5, 0.01);
				if (len < 2.0 || modeTimer <= 0 || (modeTimer < 38 && collided(dir))) {
					drive(new Vector());
					BU.sound(here, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.5F, 0.7F);
					w.spawnParticle(Particle.EXPLOSION_EMITTER, here.getX(), here.getY(), here.getZ(), 1, 0, 0, 0, 0);
					w.spawnParticle(Particle.GUST_EMITTER_LARGE, here.getX(), here.getY(), here.getZ(), 1, 0, 0, 0, 0);
					for (Player p : BU.victims(w, mob.getBoundingBox().expand(5.5, 3, 5.5))) BU.hit(p, mob, 12, pos(), 1.2, 0.8);
					mode = M_REST; modeTimer = 60;
					int[] col = BossPatterns.colors(kind);
					BossPatterns.shockRing(this, pos(), 10, 0.5, 7, 12, col[0], col[1]);   // v1.2.0 폭풍 고리 (점프로 피하기)
					BU.say(mob, Component.text("✦ 와이번이 지쳐 땅에 내려앉았습니다! (3초간 받는 피해 +50%)", NamedTextColor.GREEN));
				}
			}
			case M_REST -> {
				// 착지 후엔 남은 속도를 죽이고, 공중이면 천천히 내려앉는다 (블록 검사는 drive 가 함)
				drive(vel.clone().multiply(0.5).setY(-0.12));
				if (modeTimer % 10 == 0) w.spawnParticle(Particle.CLOUD, here.getX(), here.getY() + 1, here.getZ(), 4, 0.6, 0.3, 0.6, 0.01);
				if (modeTimer <= 0) { mode = M_CLIMB; modeTimer = 30; BU.sound(here, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 3F, 0.8F); }
			}
			case M_CLIMB -> {
				double yr = Math.toRadians(flyYaw);
				Vector look = new Vector(-Math.sin(yr), 0, Math.cos(yr));
				steer(pos().add(new Vector(look.getX() * 4, 8, look.getZ() * 4)), 0.05, 0.6);
				faceVelocity(8);
				if (modeTimer <= 0) mode = M_CIRCLE;
			}
			default -> mode = M_CIRCLE;
		}
		if (tickCount % 20 == 0 && mode != M_REST) BU.sound(here, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 1.2F, 1.3F);
	}

	private Vector mouth() {
		if (headRig != null) return worldPoint(headRig, 0, 0, 0);
		double yr = Math.toRadians(flyYaw);
		Vector look = new Vector(-Math.sin(yr), 0, Math.cos(yr));
		return pos().add(look.multiply(2.6)).add(new Vector(0, 0.9, 0));
	}

	private void orbs(LivingEntity target, int n) {
		World world = mob.getWorld();
		BU.sound(mob.getLocation(), Sound.ENTITY_BREEZE_SHOOT, SoundCategory.HOSTILE, 2F, 0.6F);
		Vector m = mouth();
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
				boolean burst = BU.solidAt(w, p0);
				if (!burst && !BU.victims(w, BU.box(p0, 1.2, 1.2, 1.2)).isEmpty()) burst = true;
				if (burst && f.age > 3) {
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
		Vector m = mouth();
		World w = mob.getWorld();
		for (int i = 0; i < 2; i++) {
			Vector dir = target.getLocation().toVector().add(new Vector(0, 1, 0)).subtract(m).normalize()
				.add(new Vector((random.nextDouble() - 0.5) * 0.2, 0, (random.nextDouble() - 0.5) * 0.2));
			w.spawn(new Location(w, m.getX(), m.getY(), m.getZ()), WindCharge.class, wc -> {
				wc.setShooter(mob);
				wc.setVelocity(dir.clone().normalize().multiply(0.8));
			});
		}
		BU.sound(mob.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, SoundCategory.HOSTILE, 2F, 0.6F);
	}

	private void tornado(LivingEntity target) {
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
					if (f.age == 30) {
						BU.lightning(w, g.getX(), g.getY(), g.getZ());
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
		mob.setGravity(false);   // v1.5.0: 낙하는 drive() 로 직접 (중력/속도 Y 가 안 먹는다)
		BU.sound(mob.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 3F, 0.5F);
	}

	@Override
	protected void poseDeath(float u) {
		deathU = u;
		// 처음 0.32 구간엔 땅에 닿을 때까지 떨어진다 (3틱마다 호출되므로 틱당 0.55 × 3)
		if (u < 0.32F && mode != M_REST) drive(new Vector(vel.getX() * 0.7, -1.65, vel.getZ() * 0.7));
		else drive(new Vector());
		if (modelMode) poseModel(animTick, 8, -30);
		else poseParts(animTick); // 레거시 블록 모델: 기존 목/꼬리 + 기울어짐은 poseParts가 flyRoll을 씀
	}

	@Override
	protected void deathFx(int tick, float u) {
		org.bukkit.Location l = mob.getLocation();
		if (tick % 4 == 0) l.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, l.getX(), l.getY() + 0.8, l.getZ(), 3, 0.8, 0.4, 0.8, 0.01);
		if (u > 0.72F && tick % 3 == 0) l.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, l.getX(), l.getY(), l.getZ(), 8, 0.7, 0.3, 0.7, 0.02);
	}

}
