package com.hobbang.enhance.boss;

import org.joml.Vector3f;

import static com.hobbang.enhance.boss.BossMotion.*;

/** Pack-model joint transforms shared by the live display and the offline renderer. */
final class BossPoses {
	private static final String SMITH = "abyssal_smith";
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

	record SmithRig(Rigid head, Rigid hammer) {}

	static SmithRig smith(Sink sink, float t, float yaw, float walk, float lookYaw, float lookPitch,
	                      Frame strike, Frame follow, Frame swing, Frame cast, Frame sweep, Frame phase) {
		float stride = sin(t * 0.18F);
		float load = strike.prepare(), hit = strike.release();
		float other = 1 - strike.strength();
		float weight = stride * walk * other;
		float stomp = hit + 0.5F * sweep.release() * other;
		float shift = -0.26F * load + 0.17F * hit + 0.12F * weight;
		float rise = sin(t * 0.067F) * 0.055F + Math.abs(weight) * 0.10F;
		float lean = -12 * load + 35 * hit + other * (9 * sweep.release() - 10 * phase.prepare());
		Rigid root = Rigid.yaw(yaw);
		Rigid base = root.at(shift, rise + 0.08F * stomp, -0.16F * load + 0.80F * hit);
		Rigid pelvis = pivot(SMITH, base, "torso").rotX(0.32F * lean).rotZ(-3 * weight + 3 * load);
		sink.place("torso", pelvis, 1);
		Rigid chest = child(SMITH, pelvis, "torso", "chest")
			.rotX(0.68F * lean + sin(t * 0.067F - 0.28F) * 1.4F)
			.rotY(-5 * weight - 13 * load + 9 * hit + 18 * sweep.prepare() * other);
		sink.place("chest", chest, 1);
		smithLeg(sink, base, "_l", -22 * weight - 5 * load, stomp);
		smithLeg(sink, base, "_r", 22 * weight + 8 * load, stomp);
		Rigid head = child(SMITH, chest, "chest", "head")
			.rotY(-lookYaw * (1 - 0.58F * strike.strength()))
			.rotX(lookPitch - 9 * load - 12 * cast.prepare() + 8 * cast.release() - 13 * phase.release());
		sink.place("head", head, 1);
		Rigid left = child(SMITH, chest, "chest", "arm_l")
			.rotX(-13 * weight - 22 * load - 18 * hit - other * (25 * cast.prepare() + 72 * phase.release()))
			.rotZ(-4 - 10 * sweep.release());
		sink.place("arm_l", left, 1);
		Rigid foreL = child(SMITH, left, "arm_l", "fore_l").rotX(-8 - 14 * load - 8 * walk);
		sink.place("fore_l", foreL, 1);
		sink.place("hand_l", child(SMITH, foreL, "fore_l", "hand_l").rotX(-4 * walk), 1);
		Rigid right = child(SMITH, chest, "chest", "arm_r")
			.rotX(-140 * load - 49 * hit + other * (13 * weight - 91 * swing.release()
				- 62 * sweep.prepare() - 19 * sweep.release() - 95 * phase.release()))
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
		sink.place("hammer", hammer, 1);
		return new SmithRig(head, hammer);
	}

	private static void smithLeg(Sink sink, Rigid root, String side, float swing, float stomp) {
		String leg = "leg" + side, shin = "shin" + side, foot = "foot" + side;
		float thigh = swing - 11 * stomp;
		float knee = 4 + Math.max(0, -swing) * 0.9F + 17 * stomp;
		Rigid l = pivot(SMITH, root, leg).rotX(thigh);
		Rigid s = child(SMITH, l, leg, shin).rotX(knee);
		sink.place(leg, l, 1);
		sink.place(shin, s, 1);
		sink.place(foot, child(SMITH, s, shin, foot).rotX(-(thigh + knee) * 0.65F), 1);
	}

	static Rigid colossus(Sink sink, float t, float yaw, float walk, float lookYaw, float lookPitch,
	                     Frame slam, Frame spike, Frame beam, Frame rain, Frame swing, Frame phase) {
		float step = t * 0.074F;
		float cog = (float) Math.floor(step);
		float stride = sin((cog + ease(step - cog)) * 2.8F);
		float impact = Math.max(slam.release(), spike.release() * 0.6F);
		float brace = Math.max(slam.strength(), spike.strength());
		float w = walk * (1 - brace);
		float sw = stride * 23 * w;
		float charge = Math.max(beam.prepare(), rain.prepare());
		float tremor = sin(t * 1.05F) * (0.6F * w + 2.6F * Math.max(impact, charge));
		float lean = -13 * slam.prepare() + 25 * slam.release() - 6 * spike.prepare()
			+ 15 * spike.release() - 7 * beam.strength() - 7 * phase.prepare();
		Rigid root = Rigid.yaw(yaw);
		Rigid base = root.at(0.16F * stride * w,
			sin(t * 0.058F) * 0.045F + Math.abs(sw) * 0.004F - impact * 0.20F, 0.12F * impact);
		Rigid pelvis = pivot(COLOSSUS, base, "torso").rotX(lean * 0.34F);
		sink.place("torso", pelvis, 1);
		Rigid chest = child(COLOSSUS, pelvis, "torso", "chest").rotX(lean * 0.66F).rotY(sw * 0.16F).rotZ(tremor * 0.40F);
		sink.place("chest", chest, 1);
		Rigid head = child(COLOSSUS, chest, "chest", "head").rotY(-lookYaw).rotX(lookPitch - 8 * phase.release());
		sink.place("head", head, 1);
		for (int side = 0; side < SIDES.length; side++) {
			String q = SIDES[side];
			float sign = side == 0 ? 1 : -1;
			String leg = "leg" + q, arm = "arm" + q, fore = "fore" + q;
			float swingLeg = sign * sw;
			Rigid l = pivot(COLOSSUS, base, leg).rotX(swingLeg - 7 * impact);
			sink.place(leg, l, 1);
			sink.place("shin" + q, child(COLOSSUS, l, leg, "shin" + q)
				.rotX(3 + Math.max(0, -swingLeg) * 0.75F + 13 * impact - sign * tremor), 1);
			float armPitch = -0.52F * swingLeg - 146 * slam.prepare() - 26 * slam.release()
				- 18 * beam.release() - 52 * rain.release() - 120 * rain.prepare() - 43 * phase.release();
			float elbow = -10 - 21 * slam.prepare() + 4 * impact - 8 * beam.prepare();
			if (side == 1) {
				armPitch -= 128 * spike.prepare() + 33 * spike.release() + 86 * swing.release() * (1 - brace);
				elbow -= 28 * spike.prepare() + 15 * swing.release() * (1 - brace);
			}
			Rigid a = child(COLOSSUS, chest, "chest", arm)
				.rotX(clamp(armPitch, -167, 42)).rotZ(sign * (4 + 57 * beam.strength() + 13 * phase.release()));
			sink.place(arm, a, 1);
			Rigid f = child(COLOSSUS, a, arm, fore).rotX(elbow + sign * tremor);
			sink.place(fore, f, 1);
			sink.place("fist" + q, child(COLOSSUS, f, fore, "fist" + q)
				.rotX(0.42F * elbow + sin(t * 1.05F - 0.8F) * 2 * impact), 1);
		}
		return head.at(0, 0.45F, 0.6F);
	}

	static Rigid sentinel(Sink sink, float yaw, float gear, float tilt, float lookYaw, float lookPitch,
	                     float death, Frame cast, Frame follow, Frame sweep, Frame channel, Frame phase) {
		float cycle = (float) Math.toRadians(gear);
		float fade = ease((death - 0.92F) / 0.08F);
		float sc = Math.max(0.05F, 1 - fade);
		float power = Math.max(channel.strength(), phase.strength());
		float breath = death > 0 ? 0 : sin(cycle) * 0.15F;
		Rigid body = pivot(SENTINEL, Rigid.yaw(yaw).at(0, breath - fade * 1.2F, 0), "body")
			.rotY(-lookYaw * 0.45F).rotX(tilt + lookPitch * 0.42F - 8 * phase.prepare());
		sink.place("body", body, sc);
		sink.place("ring", child(SENTINEL, body, "body", "ring").rotY(gear), sc);
		Rigid clock = child(SENTINEL, body, "body", "clock");
		sink.place("clock", clock.rotAxis(-2 * gear, BossModels.get(SENTINEL, "clock").spinAxis()), sc);
		for (int side = 0; side < SIDES.length; side++) {
			String q = SIDES[side], arm = "arm" + q;
			float sign = side == 0 ? 1 : -1;
			float sway = sign * sin(cycle - 0.28F) * 6 * (1 - power);
			float wind = -35 * cast.prepare() - 58 * sweep.prepare() - 49 * channel.prepare();
			float release = -68 * cast.release() - 25 * sweep.release() - 32 * channel.release() - 40 * phase.release();
			Rigid a = child(SENTINEL, body, "body", arm).rotX(clamp(wind + release + sway, -120, 15))
				.rotZ(sign * (4 + 28 * sweep.release() + 16 * power));
			sink.place(arm, a, sc);
			sink.place("blade" + q, child(SENTINEL, a, arm, "blade" + q)
				.rotX(-29 * follow.release() + sign * sin(cycle - 0.85F) * 5), sc);
		}
		sink.place("pendulum", child(SENTINEL, body, "body", "pendulum").rotZ(sin(2 * cycle - 0.42F) * 12), sc);
		return clock;
	}

	record Flight(float flap, float lift, float tuck, float fold, float wingAngle, float wingPower,
	              float pitch, float bank, float landing, float lookYaw, float lookPitch, float dive, float death) {}

	static Rigid wyvern(Sink sink, float t, float yaw, Flight f, Frame cast, Frame sweep, Frame channel, Frame phase) {
		float breath = sin(t * 0.06F);
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
		Rigid waist = joint(body, "waist").rotY(sin(t * 0.08F - 0.28F) * (1.8F + 2.8F * tuck))
			.rotX(sin(f.flap() - 0.55F) * 3.2F * f.lift() - 3 * land);
		sink.place("waist", waist, sc);
		Rigid hips = joint(waist, "hips").rotY(sin(t * 0.08F - 0.62F) * (2.2F + 3.5F * tuck))
			.rotX(sin(f.flap() - 0.92F) * 3.8F * f.lift() - 2 * land);
		sink.place("hips", hips, sc);
		float lookY = -f.lookYaw() * (1 - side), lookP = f.lookPitch() + 40 * side;
		Rigid n1 = joint(body, "neck_1").rotY(lookY * 0.24F + sin(t * 0.05F) * 4)
			.rotX(lookP * 0.20F + sin(t * 0.07F) * 3 + 24 * tuck - 12 * charge + 8 * exhale);
		sink.place("neck_1", n1, sc);
		Rigid n2 = joint(n1, "neck_2").rotY(lookY * 0.26F + sin(t * 0.05F - 0.45F) * 3)
			.rotX(lookP * 0.24F + sin(t * 0.07F - 0.45F) * 2.2F + 8 * tuck - 7 * charge);
		sink.place("neck_2", n2, sc);
		Rigid n3 = joint(n2, "neck_3").rotY(lookY * 0.25F + sin(t * 0.05F - 0.9F) * 2)
			.rotX(lookP * 0.24F + sin(t * 0.07F - 0.9F) * 1.6F - 8 * tuck + 6 * exhale);
		sink.place("neck_3", n3, sc);
		Rigid head = joint(n3, "head").rotY(lookY * 0.25F).rotX(lookP * 0.32F - 20 * tuck + 8 * dive - 8 * phase.release());
		sink.place("head", head, sc);
		float jaw = Math.max(0, sin(t * 0.05F)) * 5 + 22 * dive + 11 * charge + 34 * exhale + 20 * phase.release();
		sink.place("jaw", joint(head, "jaw").rotX(death > 0 ? 30 * ease(death * 3) : Math.min(48, jaw)), sc);
		Rigid tail = hips;
		for (int i = 0; i < TAIL.length; i++) {
			float lag = i * 0.57F;
			tail = joint(tail, TAIL[i])
				.rotY(sin(t * 0.12F - lag) * (5 + 3 * tuck) - f.bank() * 0.07F + 12 * death * (i + 1))
				.rotX(sin(f.flap() - 1.1F - lag) * 3.6F * f.lift() + 2 * tuck + 8 * death);
			sink.place(TAIL[i], tail, sc);
		}
		for (int i = 0; i < SIDES.length; i++) {
			float sign = i == 0 ? 1 : -1;
			String q = SIDES[i], armName = "wing_arm" + q, foreName = "wing_fore" + q;
			float a = f.wingAngle() + sway * f.wingPower() + 17 * flare + 12 * land - 40 * side;
			Rigid arm = joint(body, armName).rotZ(sign * a).rotY(sign * 24 * fold);
			sink.place(armName, arm, sc);
			Rigid fore = joint(arm, foreName).rotY(sign * 54 * fold)
				.rotZ(sign * sin(f.flap() - 0.52F) * 7 * (1 - fold) * f.lift());
			sink.place(foreName, fore, sc);
			for (int finger = 0; finger < WING_FINGERS.length; finger++) {
				String name = WING_FINGERS[finger] + q;
				float ripple = sin(f.flap() - 0.94F - finger * 0.36F) * (4 + 2 * finger) * (1 - fold) * f.lift();
				sink.place(name, joint(fore, name).rotY(sign * FINGER_FOLD[finger] * fold).rotZ(sign * ripple), sc);
			}
		}
		float toes = sway * 5 * tuck;
		for (String sideTag : SIDES) {
			char legSide = sideTag.charAt(1);
			wyvernLeg(sink, body, "f" + legSide, 70 * tuck + toes, 35 * tuck + 12 * land, 45 * tuck - 27 * land, sc);
			wyvernLeg(sink, hips, "h" + legSide, 85 * tuck + toes - 7 * land, 10 * tuck + 12 * land, 50 * tuck - 11 * land, sc);
		}
		return head.at(0, -0.1F, 1.05F);
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
