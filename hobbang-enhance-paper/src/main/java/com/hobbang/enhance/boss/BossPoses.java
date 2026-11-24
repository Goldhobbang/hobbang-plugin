package com.hobbang.enhance.boss;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.hobbang.enhance.boss.BossMotion.*;

/** Pack-model joint transforms shared by the live display and the offline renderer. */
final class BossPoses {
	private static final String SMITH = "abyssal_smith";
	static final float SMITH_LEG_SCALE = 1.56F;
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

	static SmithRig smith(Sink sink, Idle idle, float t, float yaw, float walk, float lookYaw, float lookPitch,
	                      Frame strike, Frame follow, Frame swing, Frame cast, Frame sweep, Frame phase) {
		return smith(sink, idle, t, yaw, walk, lookYaw, lookPitch, strike, follow, swing, cast, sweep, phase, Leap.NONE);
	}

	static SmithRig smith(Sink sink, Idle idle, float t, float yaw, float walk, float lookYaw, float lookPitch,
	                      Frame strike, Frame follow, Frame swing, Frame cast, Frame sweep, Frame phase, Leap leap) {
		return smith(sink, idle, t, yaw, walk, lookYaw, lookPitch, strike, follow, swing, cast, sweep, phase, leap, false);
	}

	static SmithRig smith(Sink sink, Idle idle, float t, float yaw, float walk, float lookYaw, float lookPitch,
	                      Frame strike, Frame follow, Frame swing, Frame cast, Frame sweep, Frame phase, Leap leap, boolean bareHand) {
		float stride = sin(t * 0.18F);
		float crouch = leap.crouch();
		float load = strike.prepare(), hit = strike.release();
		float swingHit = swing.release();
		float other = 1 - Math.max(strike.strength(), swingHit);
		float weight = stride * walk;
		float strikeMask = 1 - Math.max(strike.strength(), leap.grip());
		float stomp = hit + 0.65F * sweep.release() * other + 0.70F * swingHit * strikeMask;
		float shift = -0.34F * load + 0.20F * hit + 0.18F * weight - 0.16F * sweep.prepare() * other;
		float rise = idle.wave(0.067F, 0) * 0.095F + Math.abs(weight) * 0.16F
			- walk * Math.max(0, 1 - 5 * Math.abs(stride)) * 0.075F;
		float slamLean = -26 * load + 76 * hit + 64 * swingHit * strikeMask;
		float lean = slamLean + other * (18 * sweep.release() - 12 * sweep.prepare() - 16 * cast.prepare() + 14 * cast.release() - 18 * phase.prepare() + 12 * phase.release()) + 28 * crouch;
		Rigid root = Rigid.yaw(yaw);
		float brace = ease(Math.max(hit, swingHit * strikeMask * 0.85F));
		float lift = smithLegLift();
		Rigid base = root.at(shift, rise + 0.10F * stomp + lift - (lift + SMITH_HAMMER_RIM_REDUCTION) * brace - 1.25F * crouch,
			-0.28F * load + 0.95F * hit + 0.60F * swingHit * strikeMask - 0.18F * sweep.prepare() * other + 0.22F * sweep.release() * other);
		Rigid pelvis = pivot(SMITH, base.at(0, -0.68F * brace, 0), "torso").rotX(0.34F * lean).rotZ(-5 * weight + 5 * load - 4 * hit);
		sink.place("torso", pelvis, 1);
		Rigid chest = child(SMITH, pelvis, "torso", "chest")
			.rotX(0.66F * lean + idle.wave(0.067F, 0.28F) * 2.4F)
			.rotY(-9 * weight - 10 * load + 10 * hit + other * (26 * sweep.prepare() - 22 * sweep.release()));
		sink.place("chest", chest, 1);
		smithLeg(sink, base, "_l", -(SMITH_STRIDE + 5) * weight - 9 * load, stomp, brace, crouch);
		smithLeg(sink, base, "_r", (SMITH_STRIDE + 5) * weight + 12 * load, stomp, brace, crouch);
		float headYaw = -lookYaw * (1 - 0.58F * Math.max(strike.strength(), swingHit * strikeMask));
		float headPitch = lookPitch - 14 * load + 10 * hit - 16 * cast.prepare() + 10 * cast.release() - 18 * phase.release();
		Rigid head = child(SMITH, chest, "chest", "head").rotY(headYaw).rotX(headPitch);
		sink.place("head", head, 1);
		float jaw = idle.weight() * (SMITH_JAW_REST + SMITH_JAW_OPEN * smithBreathOpen(idle.ticks()));   // t392 김 분사와 동기
		sink.place("jaw", child(SMITH, head, "head", "jaw").rotX(jaw), 1);

		// 망치 기준 그립: 1페이즈 맨손(bareHand)일 땐 양손 맨손 전투 모션 + 망치 숨김, 2페이즈+ 평소 오른손·SLAM/도약 때만 양손 그립
		float twoHand = bareHand ? 0F : clamp(Math.max(strike.strength(), leap.grip()), 0F, 1F);
		float castLift = cast.prepare() * other, castPush = cast.release() * other;
		float sweepWind = sweep.prepare() * other, sweepRel = sweep.release() * other;
		float phaseWind = phase.prepare() * other, phaseRel = phase.release() * other;

		float bareSlam = bareHand ? strike.strength() : 0F;
		float bareArmX = bareSlam > 0.01F ? (-152F * load - 108F * hit) : 0F;
		float bareArmZ = bareSlam > 0.01F ? (18F * load - 6F * hit) : 0F;
		float bareForeX = bareSlam > 0.01F ? (-20F * load - 14F * hit) : 0F;

		Rigid fkArmR = child(SMITH, chest, "chest", "arm_r")
			.rotX((bareHand
				? (13 * weight * other - other * (98 * swingHit + 60 * sweepWind + 20 * sweepRel + 25 * castLift + 90 * phaseRel)) * (1 - bareSlam) + bareArmX
				: 13 * weight * other - other * (91 * swingHit + 62 * sweepWind + 19 * sweepRel + 95 * phaseRel))
				+ idle.wave(0.067F, 0.70F) * 1.3F)
			.rotZ((bareHand ? (4 + 22 * sweepWind) * (1 - bareSlam) - bareArmZ : 24 * sweepWind));
		Rigid fkForeR = child(SMITH, fkArmR, "arm_r", "fore_r")
			.rotX(bareHand
				? (-8 - 8 * walk - other * (22 * swingHit + 12 * sweepWind)) * (1 - bareSlam) + bareForeX
				: -10 - other * (21 * swingHit + 13 * sweepWind));
		Rigid fkHandR = child(SMITH, fkForeR, "fore_r", "hand_r")
			.rotX(bareHand ? -8 * swingHit * other : -13 * swingHit * other * 0.35F);
		Rigid fkHammer = child(SMITH, fkHandR, "hand_r", "hammer")
			.rotX(-13 * swingHit * other * 0.65F - 90
				+ idle.wave(0.14F, 1.1F) * (1 + walk) + idle.wave(0.067F, 1.2F) * 0.9F);

		Rigid fkArmL = child(SMITH, chest, "chest", "arm_l")
			.rotX((-13 * weight * other - other * (25 * castLift + 72 * phaseRel) - 18 * crouch) * (1 - bareSlam) + bareArmX
				+ idle.wave(0.067F, 0.5F) * 2.5F)
			.rotZ((-4 - 10 * sweepRel - 4 * crouch) * (1 - bareSlam) + bareArmZ);
		Rigid fkForeL = child(SMITH, fkArmL, "arm_l", "fore_l")
			.rotX((-8 - 8 * walk - 14 * crouch) * (1 - bareSlam) + bareForeX);
		Rigid fkHandL = child(SMITH, fkForeL, "fore_l", "hand_l").rotX(-4 * walk);

		Rigid hammer;
		// 기본적으로 주먹을 쥔 상태 유지 (망치 파지·맨손 전투 모두 주먹)
		float curlR = bareHand ? 76F : 68F;
		float curlL = twoHand > 0.01F ? 68F : 76F;
		if (twoHand <= 0.01F) {
			hammer = fkHammer;
			sink.place("hammer", hammer, bareHand ? 0F : 1F);
			sink.place("arm_r", fkArmR, 1);
			sink.place("fore_r", fkForeR, 1);
			placeSmithHand(sink, "r", fkHandR, curlR);
			sink.place("arm_l", fkArmL, 1);
			sink.place("fore_l", fkForeL, 1);
			placeSmithHand(sink, "l", fkHandL, curlL);
		} else {
			float downWeight = clamp(Math.max(hit, swingHit * strikeMask), 0F, 1.18F);
			float over = clamp(downWeight, 0F, 1F);
			float carry = clamp(1 - load - over, 0F, 1F);

			// SLAM/도약용 양손 망치 포물선(원호) 궤적: 팔 길이(3.33) 이내 반지름에서 어깨 중심 호를 따라 팔과 함께 크게 휘두름
			float thetaDeg = -18F * carry + 142F * load - 54F * over;
			float thetaRad = thetaDeg * ((float) Math.PI / 180F);
			float reach = 2.15F * carry + 2.42F * load + 2.25F * over;
			float shoulderY = 2.30F;
			float hx = -0.35F * carry - 0.72F * (float) Math.sin(Math.PI * load) * (1F - over);
			float hy = shoulderY + reach * (float) Math.sin(thetaRad) - 0.25F * crouch * (1 - load);
			float hz = reach * (float) Math.cos(thetaRad);
			float hRotX = -90F - thetaDeg - 22F * over;
			float hRotY = -14F * carry;
			float hRotZ = -32F * carry;

			Rigid ikHammer = chest.at(hx, hy, hz).rotY(hRotY).rotZ(hRotZ).rotX(hRotX);
			if (downWeight > 0) {
				float align = ease(Math.min(1F, downWeight));
				Quaternionf impactRot = root.rotX(-72F).rot();
				ikHammer = new Rigid(ikHammer.pos(), new Quaternionf(ikHammer.rot()).slerp(impactRot, align));
			}
			float w = ease(clamp(twoHand * 2.2F, 0F, 1F));
			if (w >= 0.99F) {
				hammer = ikHammer;
			} else {
				Vector3f hp = new Vector3f(fkHammer.pos()).lerp(ikHammer.pos(), w);
				Quaternionf hq = new Quaternionf(fkHammer.rot()).slerp(ikHammer.rot(), w);
				hammer = new Rigid(hp, hq);
			}
			sink.place("hammer", hammer, 1);

			float upArc = clamp((float) Math.sin(Math.max(0F, thetaRad)), 0F, 1F);
			Vector3f gripR = hammer.at(0F, 0F, 0F).pos();
			Vector3f poleR = new Vector3f(-0.95F - 0.35F * upArc, -0.20F + 0.85F * upArc, -0.70F + 1.05F * upArc).rotate(chest.rot());
			Rigid[] ikR = computeSmithArm(chest, "_r", gripR, poleR);

			Vector3f gripL = hammer.at(0F, 0.65F, 0F).pos();
			Vector3f poleL = new Vector3f(0.95F + 0.35F * upArc, -0.20F + 0.85F * upArc, -0.70F + 1.05F * upArc).rotate(chest.rot());
			Rigid[] ikL = computeSmithArm(chest, "_l", gripL, poleL);

			if (w >= 0.99F) {
				sink.place("arm_r", ikR[0], 1);
				sink.place("fore_r", ikR[1], 1);
				placeSmithHand(sink, "r", ikR[2], 66F);
				sink.place("arm_l", ikL[0], 1);
				sink.place("fore_l", ikL[1], 1);
				placeSmithHand(sink, "l", ikL[2], 64F);
			} else {
				blendArm(sink, "r", fkArmR, fkForeR, fkHandR, ikR, w, mix(curlR, 66F, w));
				blendArm(sink, "l", fkArmL, fkForeL, fkHandL, ikL, w, mix(curlL, 64F, w));
			}
		}
		// Projectiles retain the original animation anchor, regardless of idle visibility.
		float aimStride = weight * other;
		Rigid aimBase = root.at(-0.34F * load + 0.20F * hit + 0.18F * aimStride,
			sin(t * 0.067F) * 0.055F + Math.abs(aimStride) * 0.12F + 0.10F * stomp
				+ lift - (lift + SMITH_HAMMER_RIM_REDUCTION) * brace - 0.56F * brace,
			-0.28F * load + 0.95F * hit);
		Rigid aimPelvis = pivot(SMITH, aimBase, "torso").rotX(0.34F * lean).rotZ(-5 * aimStride + 5 * load);
		Rigid aimChest = child(SMITH, aimPelvis, "torso", "chest")
			.rotX(0.66F * lean + sin(t * 0.067F - 0.28F) * 1.4F)
			.rotY(-9 * aimStride - 22 * load + 16 * hit + 26 * sweep.prepare() * other);
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

	private static void placeSmithHand(Sink sink, String s, Rigid hand, float curl) {
		String handName = "hand_" + s;
		sink.place(handName, hand, 1);
		float curlSign = "l".equals(s) ? -1F : 1F;
		for (int i = 1; i <= 5; i++) {
			float fCurl = i == 1 ? curl * 0.76F : curl * (0.94F + 0.03F * i);
			float tCurl = i == 1 ? curl * 0.85F : Math.min(92F, curl * 1.10F);
			float splay = i == 1 ? -10F : (3 - i) * 2.5F;
			String fn = "finger_" + s + i;
			Rigid f = child(SMITH, hand, handName, fn).rotZ(curlSign * fCurl).rotX(splay);
			sink.place(fn, f, 1);
			Rigid tip = child(SMITH, f, fn, "tip_" + s + i).rotZ(curlSign * tCurl);
			sink.place("tip_" + s + i, tip, 1);
		}
	}

	private static void blendArm(Sink sink, String s, Rigid fkArm, Rigid fkFore, Rigid fkHand, Rigid[] ik, float w, float curl) {
		Rigid bArm = new Rigid(fkArm.pos(), new Quaternionf(fkArm.rot()).slerp(ik[0].rot(), w));
		Rigid bFore = child(SMITH, bArm, "arm_" + s, "fore_" + s);
		bFore = new Rigid(bFore.pos(), new Quaternionf(fkFore.rot()).slerp(ik[1].rot(), w));
		Rigid bHand = child(SMITH, bFore, "fore_" + s, "hand_" + s);
		bHand = new Rigid(bHand.pos(), new Quaternionf(fkHand.rot()).slerp(ik[2].rot(), w));
		sink.place("arm_" + s, bArm, 1);
		sink.place("fore_" + s, bFore, 1);
		placeSmithHand(sink, s, bHand, curl);
	}

	/** 2본 해석적 IK: 어깨(chest 자식)에서 망치 자루 위 손바닥 목표점(palmTarget)까지 팔·전완·손을 역산 */
	private static void solveSmithArm(Sink sink, Rigid chest, String side, Vector3f palmTarget, Vector3f poleDir) {
		Rigid[] rig = computeSmithArm(chest, side, palmTarget, poleDir);
		sink.place("arm" + side, rig[0], 1);
		sink.place("fore" + side, rig[1], 1);
		placeSmithHand(sink, side.substring(1), rig[2], 60F);
	}

	private static Rigid[] computeSmithArm(Rigid chest, String side, Vector3f palmTarget, Vector3f poleDir) {
		String armName = "arm" + side, foreName = "fore" + side, handName = "hand" + side;
		Vector3f pArm = BossModels.get(SMITH, armName).pivot();
		Vector3f pFore = BossModels.get(SMITH, foreName).pivot();
		Vector3f pHand = BossModels.get(SMITH, handName).pivot();
		Vector3f pHam = BossModels.get(SMITH, "hammer").pivot();
		Vector3f pHandR = BossModels.get(SMITH, "hand_r").pivot();
		// hand → palm(자루 중심) 오프셋은 오른손 기준(hammer - hand_r)을 좌우 대칭으로 사용
		float palmSign = "_l".equals(side) ? -1F : 1F;
		Vector3f palmOff = new Vector3f(palmSign * (pHam.x - pHandR.x), pHam.y - pHandR.y, pHam.z - pHandR.z);
		Vector3f v1 = new Vector3f(pFore).sub(pArm);
		Vector3f v2 = new Vector3f(pHand).sub(pFore).add(palmOff);
		float l1 = v1.length(), l2 = v2.length();

		Vector3f shoulder = child(SMITH, chest, "chest", armName).pos();
		Vector3f delta = new Vector3f(palmTarget).sub(shoulder);
		float rawDist = delta.length();
		float dist = clamp(rawDist, Math.abs(l1 - l2) + 0.05F, l1 + l2 - 0.05F);
		Vector3f u = rawDist > 1e-6F ? new Vector3f(delta).div(rawDist) : new Vector3f(0, -1, 0);

		float cosA = clamp((l1 * l1 + dist * dist - l2 * l2) / (2 * l1 * dist), -1F, 1F);
		float sinA = (float) Math.sqrt(Math.max(0F, 1F - cosA * cosA));
		Vector3f bend = new Vector3f(poleDir).fma(-poleDir.dot(u), u);
		if (bend.lengthSquared() < 1e-6F) {
			bend = new Vector3f(u).cross(Math.abs(u.z) < 0.9F ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0));
		}
		bend.normalize();

		Vector3f elbow = new Vector3f(shoulder).fma(l1 * cosA, u).fma(l1 * sinA, bend);
		Vector3f upperDir = new Vector3f(elbow).sub(shoulder).normalize();
		Vector3f foreDir = new Vector3f(shoulder).fma(dist, u).sub(elbow).normalize();

		Vector3f yAxis = new Vector3f(upperDir).negate();
		Vector3f xAxis = new Vector3f(u).cross(bend);
		if (xAxis.lengthSquared() < 1e-6F) {
			xAxis = new Vector3f(yAxis).cross(0, 0, 1);
			if (xAxis.lengthSquared() < 1e-6F) xAxis = new Vector3f(yAxis).cross(1, 0, 0);
		}
		xAxis.normalize();
		Vector3f zAxis = new Vector3f(xAxis).cross(yAxis).normalize();
		xAxis.set(yAxis).cross(zAxis).normalize();
		Quaternionf armRot = new Quaternionf().setFromNormalized(new Matrix3f(xAxis, yAxis, zAxis));
		armRot.mul(new Quaternionf().rotationTo(new Vector3f(v1).normalize(), new Vector3f(0, -1, 0)));
		Rigid arm = new Rigid(shoulder, armRot);

		Rigid forePivot = child(SMITH, arm, armName, foreName);
		Vector3f v2World = new Vector3f(v2).rotate(armRot).normalize();
		Quaternionf foreRot = new Quaternionf().rotationTo(v2World, foreDir).mul(armRot);
		Rigid fore = new Rigid(forePivot.pos(), foreRot);

		return new Rigid[]{arm, fore, child(SMITH, fore, foreName, handName)};
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

	private static final float WYVERN_WING_SCALE = 1.30F;

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
		// 드래곤 비행/유휴: 날개 다운스트로크 직후 몸통 부상 + 피치 미세 리듬
		float bob = -sin(f.flap() - 0.55F) * 0.34F * f.lift() + breath * 0.06F;
		float bodyPitchWave = -sin(f.flap() - 0.25F) * 4.2F * f.lift();
		Rigid root = Rigid.yaw(yaw).at(0, bob - 0.20F * tuck - 0.05F * land - 0.55F * side - 1.8F * fade, 0);
		Rigid body = joint(root, "body").rotX(f.pitch() + bodyPitchWave + breath * 1.8F + land + 22 * fall + 14 * side - 7 * phase.prepare())
			.rotZ(f.bank() + 22 * side);
		sink.place("body", body, sc);
		Rigid waist = joint(body, "waist").rotY(idle.wave(0.08F, 0.28F) * (2.2F + 3.0F * tuck))
			.rotX(sin(f.flap() - 0.55F) * 4.5F * f.lift() - 3 * land);
		sink.place("waist", waist, sc);
		Rigid hips = joint(waist, "hips").rotY(idle.wave(0.08F, 0.62F) * (2.8F + 3.8F * tuck))
			.rotX(sin(f.flap() - 0.95F) * 5.2F * f.lift() - 2 * land);
		sink.place("hips", hips, sc);
		float lookY = -f.lookYaw() * (1 - side), lookP = f.lookPitch() + 18 * side;
		// 목 S자 파동 전파 + 머리 시선 안정화 (사망 시 목/머리를 옆으로 기대듯 꺾음)
		float neckWave1 = sin(f.flap() - 0.40F) * 4.0F * f.lift();
		float neckWave2 = sin(f.flap() - 0.90F) * 4.5F * f.lift();
		float neckWave3 = sin(f.flap() - 1.40F) * 3.8F * f.lift();
		Rigid n1 = joint(body, "neck_1").rotY(lookY * 0.24F + idle.wave(0.05F, 0) * 5.0F + 10 * side)
			.rotX(lookP * 0.20F + neckWave1 + idle.wave(0.07F, 0) * 3.5F + 24 * tuck - 12 * charge + 8 * exhale);
		sink.place("neck_1", n1, sc);
		Rigid n2 = joint(n1, "neck_2").rotY(lookY * 0.26F + idle.wave(0.05F, 0.45F) * 4.0F + 14 * side)
			.rotX(lookP * 0.24F + neckWave2 + idle.wave(0.07F, 0.45F) * 2.6F + 8 * tuck - 7 * charge);
		sink.place("neck_2", n2, sc);
		Rigid n3 = joint(n2, "neck_3").rotY(lookY * 0.25F + idle.wave(0.05F, 0.9F) * 3.0F + 16 * side)
			.rotX(lookP * 0.24F + neckWave3 + idle.wave(0.07F, 0.9F) * 2.0F - 8 * tuck + 6 * exhale);
		sink.place("neck_3", n3, sc);
		Rigid head = joint(n3, "head").rotY(lookY * 0.25F + 28 * side).rotZ(48 * side)
			.rotX(lookP * 0.32F - neckWave3 * 0.85F - 20 * tuck + 8 * dive - 8 * phase.release());
		sink.place("head", head, sc);
		float jaw = 22 * dive + 11 * charge + 34 * exhale + 20 * phase.release();
		sink.place("jaw", joint(head, "jaw").rotX(death > 0 ? 30 * ease(death * 3) : Math.min(48, jaw) + (idle.weight() + idle.wave(0.11F, 0.9F)) * 1.8F), sc);
		Rigid tail = hips;
		for (int i = 0; i < TAIL.length; i++) {
			float lag = i * 0.62F;
			float ampScale = 1.0F + 0.22F * i;
			tail = joint(tail, TAIL[i])
				.rotY(idle.wave(0.08F, lag) * (4.2F + 2.4F * tuck) * ampScale - f.bank() * 0.12F + 12 * death * (i + 1))
				.rotX(sin(f.flap() - 1.65F - lag) * 5.0F * ampScale * f.lift() * idle.weight()
					+ idle.wave(0.07F, lag + 0.5F) * 1.6F + 2 * tuck + 8 * death);
			sink.place(TAIL[i], tail, sc);
		}
		float wingSc = sc * WYVERN_WING_SCALE;
		for (int i = 0; i < SIDES.length; i++) {
			float sign = i == 0 ? 1 : -1;
			String q = SIDES[i], armName = "wing_arm" + q, foreName = "wing_fore" + q;
			float a = f.wingAngle() + sway * f.wingPower() + 17 * flare + 12 * land - 38 * side
				+ idle.wave(0.06F, 0.35F) * 3.0F * fold;
			float wingSweep = -sin(f.flap() + 0.35F) * 14.0F * (1 - fold) * f.lift();
			float wingTwist = (float) Math.cos(f.flap()) * 9.0F * (1 - fold) * f.lift();
			Rigid arm = joint(body, armName).rotZ(sign * a).rotY(sign * (24 * fold + wingSweep)).rotX(wingTwist);
			sink.place(armName, arm, wingSc);
			Rigid fore = wyvernWingChild(arm, armName, foreName).rotY(sign * 54 * fold)
				.rotZ(sign * (sin(f.flap() - 0.65F) * 18.0F * (1 - fold) * f.lift()
					+ idle.wave(0.06F, 0.8F) * 2.5F * fold - 24 * side));
			sink.place(foreName, fore, wingSc);
			for (int finger = 0; finger < WING_FINGERS.length; finger++) {
				String name = WING_FINGERS[finger] + q;
				float ripple = sin(f.flap() - 1.05F - finger * 0.42F) * (7.0F + 3.0F * finger) * (1 - fold) * f.lift()
					+ idle.wave(0.06F, 1.15F + finger * 0.24F) * 2.2F * fold - 12 * side;
				sink.place(name, wyvernWingChild(fore, foreName, name).rotY(sign * FINGER_FOLD[finger] * fold).rotZ(sign * ripple), wingSc);
			}
		}
		float toes = sway * 5 * tuck;
		for (String sideTag : SIDES) {
			char legSide = sideTag.charAt(1);
			wyvernLeg(sink, body, "f" + legSide, 32 * tuck + toes, 18 * tuck + 12 * land, 28 * tuck - 27 * land, sc);
			wyvernLeg(sink, hips, "h" + legSide, 42 * tuck + toes - 7 * land, 14 * tuck + 12 * land, 30 * tuck - 11 * land, sc);
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

	private static Rigid wyvernWingChild(Rigid parent, String from, String to) {
		Vector3f[] joint = BossModels.rig(WYVERN, to);
		Vector3f offset = joint[0], rest = joint[1];
		return parent.at(offset.x * WYVERN_WING_SCALE, offset.y * WYVERN_WING_SCALE, offset.z * WYVERN_WING_SCALE)
			.rotX(rest.x).rotY(rest.y).rotZ(rest.z);
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
