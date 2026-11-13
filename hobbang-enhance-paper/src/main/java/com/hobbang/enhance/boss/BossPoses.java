package com.hobbang.enhance.boss;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.hobbang.enhance.boss.BossMotion.*;

/** Pack-model joint transforms shared by the live display and the offline renderer. */
final class BossPoses {
	private static final String SMITH = "abyssal_smith";
	static final float SMITH_LEG_SCALE = 1.30F;
	/** 걸음 보폭(걸을 때 허벅지가 앞뒤로 흔들리는 각도 ±), 앞다리 허벅지 추가 들기 비율, 무릎 굽힘 배율 (이전: 22 / 0 / 0.9) */
	private static final float SMITH_STRIDE = 36F, SMITH_KNEE_RAISE = 0.35F, SMITH_KNEE_BEND = 1.5F;
	// Keep the smaller hammer's flat striking face at its original contact height.
	private static final float SMITH_HAMMER_RIM_REDUCTION = 0.1695F;

	/* t392 대장장이 숨결: 턱과 하얀 김 분사가 같은 시계(idle.ticks)를 쓴다 — 어긋날 수 없음.
	 * 한 주기 56틱(2.8초): 0~4틱 턱 벌림 → 4~13틱 벌린 채 유지(이때 김 분사 3~13틱) → 13~19틱 닫힘 → 쉼 */
	static final int SMITH_BREATH_PERIOD = 56, SMITH_JET_START = 3, SMITH_JET_END = 13;
	private static final float SMITH_JAW_REST = 1.2F, SMITH_JAW_OPEN = 14F;

	/** 0(닫힘)..1(최대로 벌림) */
	static float smithBreathOpen(int ticks) {
		int p = Math.floorMod(ticks, SMITH_BREATH_PERIOD);
		if (p < 4) return ease(p / 4F);
		if (p < 13) return 1;
		if (p < 19) return 1 - ease((p - 13) / 6F);
		return 0;
	}

	/** 분사 중이면 0부터 세는 분사 틱, 아니면 -1 */
	static int smithJetTick(int ticks) {
		int p = Math.floorMod(ticks, SMITH_BREATH_PERIOD);
		return p >= SMITH_JET_START && p < SMITH_JET_END ? p - SMITH_JET_START : -1;
	}
	private static final String COLOSSUS = "prism_colossus";
	private static final String SENTINEL = "chrono_sentinel";
	private static final String WYVERN = "tempest_wyvern";
	private static final String[] SIDES = {"_l", "_r"};
	private static final String[] WING_FINGERS = {"wing_f1", "wing_f2", "wing_f3"};
	private static final String[] TAIL = {"tail_1", "tail_2", "tail_3", "tail_4", "tail_5", "tail_6"};
	private static final float[] FINGER_FOLD = {58, 33, 8};

	@FunctionalInterface
	interface Sink {
		void place(String name, Rigid rig, float scale);
	}

	private static Rigid pivot(String boss, Rigid root, String part) {
		Vector3f p = BossModels.get(boss, part).pivot();
		return root.at(p.x, p.y, p.z);
	}

	private static Rigid child(String boss, Rigid parent, String from, String to) {
		Vector3f a = BossModels.get(boss, from).pivot(), b = BossModels.get(boss, to).pivot();
		return parent.at(b.x - a.x, b.y - a.y, b.z - a.z);
	}

	private static Rigid joint(Rigid parent, String part) {
		Vector3f[] joint = BossModels.rig(WYVERN, part);
		Vector3f offset = joint[0], rest = joint[1];
		return parent.at(offset.x, offset.y, offset.z).rotX(rest.x).rotY(rest.y).rotZ(rest.z);
	}

	static float smithLegLift() {
		float hip = BossModels.get(SMITH, "leg_l").pivot().y;
		float ankle = BossModels.get(SMITH, "foot_l").pivot().y;
		return (hip - ankle) * (SMITH_LEG_SCALE - 1);
	}

	static Rigid smithLegChild(Rigid parent, String from, String to) {
		Vector3f a = BossModels.get(SMITH, from).pivot(), b = BossModels.get(SMITH, to).pivot();
		return parent.at(b.x - a.x, (b.y - a.y) * SMITH_LEG_SCALE, b.z - a.z);
	}

	record SmithRig(Rigid head, Rigid mouth, Rigid hammer) {}

	/** 점프 내려찍기 보조 포즈. crouch 0..1 = 무릎·상체 깊게 굽힘 + 망치 든 팔을 뒤로, grip 0..1 = 왼손도 망치 손잡이로(두 손 잡기).
	 *  망치 치켜듦(load)/내려찍기(hit)는 기존 strike 프레임을 그대로 쓴다. */
	record Leap(float crouch, float grip) {
		static final Leap NONE = new Leap(0, 0);
	}

	/** 두 손 잡기에서 왼팔이 안쪽(몸 중심)으로 모이는 각도 — 손이 손잡이에 안 닿으면 여기를 조정 */
	private static final float LEAP_GRIP_Z = 28F;

	static SmithRig smith(Sink sink, Idle idle, float t, float yaw, float walk, float lookYaw, float lookPitch,
	                      Frame strike, Frame follow, Frame swing, Frame cast, Frame sweep, Frame phase) {
		return smith(sink, idle, t, yaw, walk, lookYaw, lookPitch, strike, follow, swing, cast, sweep, phase, Leap.NONE);
	}

	static SmithRig smith(Sink sink, Idle idle, float t, float yaw, float walk, float lookYaw, float lookPitch,
	                      Frame strike, Frame follow, Frame swing, Frame cast, Frame sweep, Frame phase, Leap leap) {
		float stride = sin(t * 0.18F);
		float crouch = leap.crouch(), grip = leap.grip();
		float load = strike.prepare(), hit = strike.release();
		float other = 1 - strike.strength();
		float weight = stride * walk;
		float stomp = hit + 0.5F * sweep.release() * other;
		float shift = -0.26F * load + 0.17F * hit + 0.12F * weight;
		float rise = idle.wave(0.067F, 0) * 0.085F + Math.abs(weight) * 0.13F
			- walk * Math.max(0, 1 - 5 * Math.abs(stride)) * 0.045F;
		float lean = -12 * load + 55 * hit + other * (9 * sweep.release() - 10 * phase.prepare()) + 22 * crouch;
		Rigid root = Rigid.yaw(yaw);
		float brace = ease(hit);
		float lift = smithLegLift();
		Rigid base = root.at(shift, rise + 0.08F * stomp + lift - (lift + SMITH_HAMMER_RIM_REDUCTION) * brace - 1.1F * crouch,
			-0.16F * load + 0.80F * hit);
		Rigid pelvis = pivot(SMITH, base.at(0, -0.50F * ease(hit), 0), "torso").rotX(0.32F * lean).rotZ(-3 * weight + 3 * load);
		sink.place("torso", pelvis, 1);
		Rigid chest = child(SMITH, pelvis, "torso", "chest")
			.rotX(0.68F * lean + idle.wave(0.067F, 0.28F) * 2.1F)
			.rotY(-5 * weight - 13 * load + 9 * hit + 18 * sweep.prepare() * other);
		sink.place("chest", chest, 1);
		smithLeg(sink, base, "_l", -SMITH_STRIDE * weight - 5 * load, stomp, brace, crouch);
		smithLeg(sink, base, "_r", SMITH_STRIDE * weight + 8 * load, stomp, brace, crouch);
		float headYaw = -lookYaw * (1 - 0.58F * strike.strength());
		float headPitch = lookPitch - 9 * load - 12 * cast.prepare() + 8 * cast.release() - 13 * phase.release();
		Rigid head = child(SMITH, chest, "chest", "head").rotY(headYaw).rotX(headPitch);
		sink.place("head", head, 1);
		float jaw = idle.weight() * (SMITH_JAW_REST + SMITH_JAW_OPEN * smithBreathOpen(idle.ticks()));   // t392 김 분사와 동기
		sink.place("jaw", child(SMITH, head, "head", "jaw").rotX(jaw), 1);
		Rigid left = child(SMITH, chest, "chest", "arm_l")
			.rotX(-13 * weight - 22 * load - 18 * hit - other * (25 * cast.prepare() + 72 * phase.release())
				- 30 * crouch - grip * (118 * load + 31 * hit))   // grip: 오른팔(-140·load, -49·hit)과 같은 각도로 → 두 손 잡기
			.rotZ(-4 - 10 * sweep.release() - LEAP_GRIP_Z * grip);
		sink.place("arm_l", left, 1);
		Rigid foreL = child(SMITH, left, "arm_l", "fore_l").rotX(-8 - 14 * load - 8 * walk - 24 * grip);
		sink.place("fore_l", foreL, 1);
		sink.place("hand_l", child(SMITH, foreL, "fore_l", "hand_l").rotX(-4 * walk), 1);
		Rigid right = child(SMITH, chest, "chest", "arm_r")
			.rotX(-140 * load - 49 * hit + 45 * crouch * (1 - load) + 13 * weight * other - other * (91 * swing.release()
					+ 62 * sweep.prepare() + 19 * sweep.release() + 95 * phase.release())
				+ idle.wave(0.067F, 0.70F) * 1.3F)
			.rotZ(10 * load + 18 * hit + 24 * sweep.prepare() * other);
		sink.place("arm_r", right, 1);
		float drawBack = follow.prepare() * (1 - hit);
		Rigid foreR = child(SMITH, right, "arm_r", "fore_r")
			.rotX(-10 - 24 * drawBack + 4 * hit - other * (21 * swing.release() + 13 * sweep.prepare()));
		sink.place("fore_r", foreR, 1);
		float wrist = -26 * drawBack + 80 * hit - 13 * swing.release() * other;
		Rigid hand = child(SMITH, foreR, "fore_r", "hand_r").rotX(wrist * 0.35F);
		sink.place("hand_r", hand, 1);
		Rigid hammer = child(SMITH, hand, "hand_r", "hammer").rotX(wrist * 0.65F - 90);
		if (hit > 0) {
			// At contact the local -Z striking face is flat against the ground.
			float align = ease(hit);
			Quaternionf rotation = new Quaternionf(hammer.rot()).slerp(root.rotX(-90).rot(), align);
			hammer = new Rigid(hammer.pos(), rotation);
		}
		sink.place("hammer", hammer.rotX(idle.wave(0.14F, 1.1F) * (1 + walk)
			+ idle.wave(0.067F, 1.2F) * 0.9F), 1);
		// Projectiles retain the original animation anchor, regardless of idle visibility.
		float aimStride = weight * other;
		Rigid aimBase = root.at(-0.26F * load + 0.17F * hit + 0.12F * aimStride,
			sin(t * 0.067F) * 0.055F + Math.abs(aimStride) * 0.10F + 0.08F * stomp
				+ lift - (lift + SMITH_HAMMER_RIM_REDUCTION) * brace - 0.50F * brace,
			-0.16F * load + 0.80F * hit);
		Rigid aimPelvis = pivot(SMITH, aimBase, "torso").rotX(0.32F * lean).rotZ(-3 * aimStride + 3 * load);
		Rigid aimChest = child(SMITH, aimPelvis, "torso", "chest")
			.rotX(0.68F * lean + sin(t * 0.067F - 0.28F) * 1.4F)
			.rotY(-5 * aimStride - 13 * load + 9 * hit + 18 * sweep.prepare() * other);
		Rigid mouth = child(SMITH, aimChest, "chest", "head").rotY(headYaw).rotX(headPitch);
		return new SmithRig(head, mouth, hammer);
	}

	private static void smithLeg(Sink sink, Rigid root, String side, float swing, float stomp, float brace, float crouch) {
		String leg = "leg" + side, shin = "shin" + side, foot = "foot" + side;
		// 걸음: 보폭(SMITH_STRIDE)을 키우고, 앞으로 나가는 다리는 허벅지를 더 들고 무릎을 많이 굽혀 걸음 모션에 비해 빨라 보이지 않게
		float forward = Math.max(0, -swing);
		float thigh = swing - SMITH_KNEE_RAISE * forward - 11 * stomp - 43 * brace - 55 * crouch;
		float knee = 6 + forward * SMITH_KNEE_BEND + 17 * stomp + 70 * brace + 105 * crouch;
		Rigid l = pivot(SMITH, root, leg).rotX(thigh);
		Rigid s = smithLegChild(l, leg, shin).rotX(knee);
		sink.place(leg, l, 1);
		sink.place(shin, s, 1);
		sink.place(foot, smithLegChild(s, shin, foot).rotX(-(thigh + knee) * (0.65F + 0.35F * brace)), 1);
	}

	static Rigid colossus(Sink sink, Idle idle, float t, float yaw, float walk, float lookYaw, float lookPitch,
	                     Frame slam, Frame spike, Frame beam, Frame rain, Frame swing, Frame phase) {
		float step = t * 0.074F;
		float cog = (float) Math.floor(step);
		float stride = sin((cog + ease(step - cog)) * 2.8F);
		float impact = Math.max(slam.release(), spike.release() * 0.6F);
		float brace = Math.max(slam.strength(), spike.strength());
		float w = walk * (1 - 0.55F * brace);
		float sw = stride * 23 * w;
		float charge = Math.max(beam.prepare(), rain.prepare());
		float tremor = sin(t * 1.05F) * (0.6F * w + 2.6F * Math.max(impact, charge)
			+ 1.4F * phase.strength());
		float lean = -13 * slam.prepare() + 25 * slam.release() - 6 * spike.prepare()
			+ 15 * spike.release() - 7 * beam.strength() - 7 * phase.prepare();
		Rigid root = Rigid.yaw(yaw);
		Rigid base = root.at(0.16F * stride * w + idle.wave(0.039F, 0) * 0.06F,
			idle.wave(0.058F, 0) * 0.075F + Math.abs(sw) * 0.004F - impact * 0.20F, 0.12F * impact);
		Rigid pelvis = pivot(COLOSSUS, base, "torso").rotX(lean * 0.34F);
		sink.place("torso", pelvis, 1);
		Rigid chest = child(COLOSSUS, pelvis, "torso", "chest").rotX(lean * 0.66F).rotY(sw * 0.16F + idle.wave(0.039F, 0.5F) * 1.4F).rotZ(tremor * 0.40F);
		sink.place("chest", chest, 1);
		Rigid head = child(COLOSSUS, chest, "chest", "head").rotY(-lookYaw).rotX(lookPitch - 8 * phase.release());
		sink.place("head", head, 1);
		for (int side = 0; side < SIDES.length; side++) {
			String q = SIDES[side];
			float sign = side == 0 ? 1 : -1;
			String leg = "leg" + q, arm = "arm" + q, fore = "fore" + q;
			float swingLeg = sign * sw;
			float delayed = sign * sin((cog + ease(step - cog)) * 2.8F - 0.32F) * 23 * w;
			Rigid l = pivot(COLOSSUS, base, leg).rotX(swingLeg - 7 * impact);
			sink.place(leg, l, 1);
			sink.place("shin" + q, child(COLOSSUS, l, leg, "shin" + q)
				.rotX(3 + Math.max(0, -delayed) * 0.75F + 13 * impact - sign * tremor), 1);
			float armPitch = -0.52F * delayed - 146 * slam.prepare() - 26 * slam.release()
				- 36 * beam.release() - 52 * rain.release() - 120 * rain.prepare() - 24 * phase.prepare() - 56 * phase.release();
			float elbow = -10 - 21 * slam.prepare() + 4 * impact - 25 * beam.prepare() + 18 * beam.release();
			if (side == 1) {
				armPitch -= 128 * spike.prepare() + 33 * spike.release() + 86 * swing.release() * (1 - brace);
				elbow -= 28 * spike.prepare() + 15 * swing.release() * (1 - brace);
			}
			Rigid a = child(COLOSSUS, chest, "chest", arm)
				.rotX(clamp(armPitch, -167, 42)).rotZ(sign * (4 + 42 * beam.prepare() + 68 * beam.release() + 18 * phase.release()));
			sink.place(arm, a, 1);
			Rigid f = child(COLOSSUS, a, arm, fore).rotX(elbow + sign * tremor);
			sink.place(fore, f, 1);
			sink.place("fist" + q, child(COLOSSUS, f, fore, "fist" + q)
				.rotX(0.42F * elbow - 0.13F * delayed + sin(t * 1.05F - 0.8F) * 2 * impact), 1);
		}
		// Preserve the beam's original launch point and tracking geometry.
		float aimWalk = walk * (1 - brace);
		float aimSwing = stride * 23 * aimWalk;
		float aimTremor = sin(t * 1.05F) * (0.6F * aimWalk + 2.6F * Math.max(impact, charge));
		Rigid aimBase = root.at(0.16F * stride * aimWalk,
			sin(t * 0.058F) * 0.045F + Math.abs(aimSwing) * 0.004F - impact * 0.20F, 0.12F * impact);
		float aimLean = lean + 7 * phase.prepare();
		Rigid aimPelvis = pivot(COLOSSUS, aimBase, "torso").rotX(aimLean * 0.34F);
		Rigid aimChest = child(COLOSSUS, aimPelvis, "torso", "chest")
			.rotX(aimLean * 0.66F).rotY(aimSwing * 0.16F).rotZ(aimTremor * 0.40F);
		return child(COLOSSUS, aimChest, "chest", "head").rotY(-lookYaw).rotX(lookPitch)
			.at(0, 0.45F, 0.6F);
	}

	static Rigid sentinel(Sink sink, Idle idle, float yaw, float gear, float tilt, float lookYaw, float lookPitch,
	                     float death, Frame cast, Frame follow, Frame sweep, Frame channel, Frame phase) {
		float cycle = (float) Math.toRadians(gear);
		float fade = ease((death - 0.92F) / 0.08F);
		float sc = Math.max(0.05F, 1 - fade);
		float power = Math.max(channel.strength(), phase.strength());
		float breath = death > 0 ? 0 : idle.wave(0.064F, 0) * 0.13F;
		Rigid body = pivot(SENTINEL, Rigid.yaw(yaw).at(0, breath - fade * 1.2F, 0), "body")
			.rotY(-lookYaw * 0.45F).rotX(tilt + lookPitch * 0.42F - 8 * phase.prepare());
		sink.place("body", body, sc);
		sink.place("ring", child(SENTINEL, body, "body", "ring").rotX(17 * sweep.prepare() - 23 * sweep.release()).rotY(gear), sc);
		Rigid clock = child(SENTINEL, body, "body", "clock");
		sink.place("clock", clock.rotAxis(-2 * gear, BossModels.get(SENTINEL, "clock").spinAxis()), sc);
		// cogs beside the dial mesh with the clock: opposite direction, faster (smaller wheel)
		sink.place("gear_l", child(SENTINEL, body, "body", "gear_l").rotZ(3 * gear), sc);
		sink.place("gear_r", child(SENTINEL, body, "body", "gear_r").rotZ(-3 * gear), sc);
		for (int side = 0; side < SIDES.length; side++) {
			String q = SIDES[side], arm = "arm" + q;
			float sign = side == 0 ? 1 : -1;
			float sway = sign * idle.wave(0.071F, 0.28F) * 6;
			float wind = -35 * cast.prepare() - 58 * sweep.prepare() - 49 * channel.prepare() - 25 * phase.prepare();
			float release = -68 * cast.release() - 25 * sweep.release() - 32 * channel.release() - 40 * phase.release();
			Rigid a = child(SENTINEL, body, "body", arm).rotX(clamp(wind + release + sway, -120, 15))
				.rotZ(sign * (4 + 28 * sweep.release() + 16 * power));
			sink.place(arm, a, sc);
			sink.place("blade" + q, child(SENTINEL, a, arm, "blade" + q)
				.rotX(-29 * follow.release() + sign * idle.wave(0.071F, 0.85F) * 5), sc);
		}
		sink.place("pendulum", child(SENTINEL, body, "body", "pendulum").rotZ(sin(2 * cycle - 0.42F) * 12), sc);
		Rigid aimBody = pivot(SENTINEL, Rigid.yaw(yaw), "body")
			.rotY(-lookYaw * 0.45F).rotX(lookPitch * 0.42F - 8 * phase.prepare());
		return child(SENTINEL, aimBody, "body", "clock");
	}

	record Flight(float flap, float lift, float tuck, float fold, float wingAngle, float wingPower,
	              float pitch, float bank, float landing, float lookYaw, float lookPitch, float dive, float death) {}

	static Rigid wyvern(Sink sink, Idle idle, float t, float yaw, Flight f, Frame cast, Frame sweep, Frame channel, Frame phase) {
		float breath = idle.wave(0.06F, 0);
		float sway = sin(f.flap()), tuck = f.tuck(), fold = f.fold();
		float land = f.landing(), dive = f.dive(), death = f.death();
		float flare = Math.max(sweep.release(), phase.release());
		float charge = Math.max(cast.prepare(), channel.prepare());
		float exhale = Math.max(cast.release(), channel.release());
		float fall = ease(death / 0.32F), side = ease((death - 0.28F) / 0.42F), fade = ease((death - 0.72F) / 0.28F);
		float sc = Math.max(0.05F, 1 - fade);
		float bob = -sin(f.flap() - 0.48F) * 0.23F * f.lift() + breath * 0.045F;
		Rigid root = Rigid.yaw(yaw).at(0, bob - 0.20F * tuck - 0.05F * land - 0.30F * side - 1.8F * fade, 0);
		Rigid body = joint(root, "body").rotX(f.pitch() + breath * 1.4F + land + 28 * fall + 18 * side - 7 * phase.prepare())
			.rotZ(f.bank() + 78 * side);
		sink.place("body", body, sc);
		Rigid waist = joint(body, "waist").rotY(idle.wave(0.08F, 0.28F) * (1.8F + 2.8F * tuck))
			.rotX(sin(f.flap() - 0.55F) * 3.2F * f.lift() - 3 * land);
		sink.place("waist", waist, sc);
		Rigid hips = joint(waist, "hips").rotY(idle.wave(0.08F, 0.62F) * (2.2F + 3.5F * tuck))
			.rotX(sin(f.flap() - 0.92F) * 3.8F * f.lift() - 2 * land);
		sink.place("hips", hips, sc);
		float lookY = -f.lookYaw() * (1 - side), lookP = f.lookPitch() + 40 * side;
		Rigid n1 = joint(body, "neck_1").rotY(lookY * 0.24F + idle.wave(0.05F, 0) * 4)
			.rotX(lookP * 0.20F + idle.wave(0.07F, 0) * 3 + 24 * tuck - 12 * charge + 8 * exhale);
		sink.place("neck_1", n1, sc);
		Rigid n2 = joint(n1, "neck_2").rotY(lookY * 0.26F + idle.wave(0.05F, 0.45F) * 3)
			.rotX(lookP * 0.24F + idle.wave(0.07F, 0.45F) * 2.2F + 8 * tuck - 7 * charge);
		sink.place("neck_2", n2, sc);
		Rigid n3 = joint(n2, "neck_3").rotY(lookY * 0.25F + idle.wave(0.05F, 0.9F) * 2)
			.rotX(lookP * 0.24F + idle.wave(0.07F, 0.9F) * 1.6F - 8 * tuck + 6 * exhale);
		sink.place("neck_3", n3, sc);
		Rigid head = joint(n3, "head").rotY(lookY * 0.25F).rotX(lookP * 0.32F - 20 * tuck + 8 * dive - 8 * phase.release());
		sink.place("head", head, sc);
		float jaw = 22 * dive + 11 * charge + 34 * exhale + 20 * phase.release();
		sink.place("jaw", joint(head, "jaw").rotX(death > 0 ? 30 * ease(death * 3) : Math.min(48, jaw) + (idle.weight() + idle.wave(0.11F, 0.9F)) * 1.5F), sc);
		Rigid tail = hips;
		for (int i = 0; i < TAIL.length; i++) {
			float lag = i * 0.57F;
			tail = joint(tail, TAIL[i])
				.rotY(idle.wave(0.08F, lag) * (3.6F + 2 * tuck) - f.bank() * 0.07F + 12 * death * (i + 1))
				.rotX(sin(f.flap() - 1.65F - lag) * 3.6F * f.lift() * idle.weight()
					+ idle.wave(0.07F, lag + 0.5F) * 1.2F + 2 * tuck + 8 * death);
			sink.place(TAIL[i], tail, sc);
		}
		for (int i = 0; i < SIDES.length; i++) {
			float sign = i == 0 ? 1 : -1;
			String q = SIDES[i], armName = "wing_arm" + q, foreName = "wing_fore" + q;
			float a = f.wingAngle() + sway * f.wingPower() + 17 * flare + 12 * land - 40 * side
				+ idle.wave(0.06F, 0.35F) * 2.5F * fold;
			Rigid arm = joint(body, armName).rotZ(sign * a).rotY(sign * 24 * fold);
			sink.place(armName, arm, sc);
			Rigid fore = joint(arm, foreName).rotY(sign * 54 * fold)
				.rotZ(sign * (sin(f.flap() - 0.52F) * 7 * (1 - fold) * f.lift()
					+ idle.wave(0.06F, 0.8F) * 2 * fold));
			sink.place(foreName, fore, sc);
			for (int finger = 0; finger < WING_FINGERS.length; finger++) {
				String name = WING_FINGERS[finger] + q;
				float ripple = sin(f.flap() - 0.94F - finger * 0.36F) * (4 + 2 * finger) * (1 - fold) * f.lift()
					+ idle.wave(0.06F, 1.15F + finger * 0.24F) * 1.8F * fold;
				sink.place(name, joint(fore, name).rotY(sign * FINGER_FOLD[finger] * fold).rotZ(sign * ripple), sc);
			}
		}
		float toes = sway * 5 * tuck;
		for (String sideTag : SIDES) {
			char legSide = sideTag.charAt(1);
			wyvernLeg(sink, body, "f" + legSide, 70 * tuck + toes, 35 * tuck + 12 * land, 45 * tuck - 27 * land, sc);
			wyvernLeg(sink, hips, "h" + legSide, 85 * tuck + toes - 7 * land, 10 * tuck + 12 * land, 50 * tuck - 11 * land, sc);
		}
		float aimBreath = sin(t * 0.06F);
		float aimBob = bob + (aimBreath - breath) * 0.045F;
		Rigid aimRoot = Rigid.yaw(yaw).at(0, aimBob - 0.20F * tuck - 0.05F * land - 0.30F * side - 1.8F * fade, 0);
		Rigid aimBody = joint(aimRoot, "body").rotX(f.pitch() + aimBreath * 1.4F + land + 28 * fall + 18 * side - 7 * phase.prepare())
			.rotZ(f.bank() + 78 * side);
		Rigid aimNeck = joint(aimBody, "neck_1").rotY(lookY * 0.24F + sin(t * 0.05F) * 4)
			.rotX(lookP * 0.20F + sin(t * 0.07F) * 3 + 24 * tuck - 12 * charge + 8 * exhale);
		aimNeck = joint(aimNeck, "neck_2").rotY(lookY * 0.26F + sin(t * 0.05F - 0.45F) * 3)
			.rotX(lookP * 0.24F + sin(t * 0.07F - 0.45F) * 2.2F + 8 * tuck - 7 * charge);
		aimNeck = joint(aimNeck, "neck_3").rotY(lookY * 0.25F + sin(t * 0.05F - 0.9F) * 2)
			.rotX(lookP * 0.24F + sin(t * 0.07F - 0.9F) * 1.6F - 8 * tuck + 6 * exhale);
		return joint(aimNeck, "head").rotY(lookY * 0.25F).rotX(lookP * 0.32F - 20 * tuck + 8 * dive - 8 * phase.release())
			.at(0, -0.1F, 1.05F);
	}

	private static void wyvernLeg(Sink sink, Rigid root, String tag, float thigh, float shin, float foot, float scale) {
		Rigid t = joint(root, "thigh_" + tag).rotX(thigh);
		Rigid s = joint(t, "shin_" + tag).rotX(shin);
		sink.place("thigh_" + tag, t, scale);
		sink.place("shin_" + tag, s, scale);
		sink.place("foot_" + tag, joint(s, "foot_" + tag).rotX(foot), scale);
	}

	private BossPoses() {}
}
