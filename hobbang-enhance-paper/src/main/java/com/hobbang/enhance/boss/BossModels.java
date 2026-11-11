package com.hobbang.enhance.boss;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** 자동 생성 (obj2mc/make_java_rig.py) — 리소스팩 hobbang-pack 의 보스 모델 피벗/스케일 표. 손으로 고치지 말 것 */
public final class BossModels {
	public record Spec(String boss, String part, Vector3f pivot, float scale, Vector3f spinAxis, int cuboids, String key) {
	}

	private static final Map<String, Spec> SPECS = new HashMap<>();
	private static final Map<String, Float> HEIGHT = new HashMap<>();

	private BossModels() {}

	private static void put(String b, String p, float x, float y, float z, float s, float ax, float ay, float az, int n, String key) {
		SPECS.put(b + "/" + p, new Spec(b, p, new Vector3f(x, y, z), s, new Vector3f(ax, ay, az).normalize(), n, key));
	}

	public static Spec get(String boss, String part) {
		Spec s = SPECS.get(boss + "/" + part);
		if (s == null) throw new IllegalArgumentException("no boss model " + boss + "/" + part);
		return s;
	}

	static Iterable<Spec> all() {
		return SPECS.values();
	}

	public static float height(String boss) {
		return HEIGHT.getOrDefault(boss, 0F);
	}

	static {
		put("abyssal_smith", "head", 0.0000F, 7.2000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 180, "boss/abyssal_smith/head");
		put("abyssal_smith", "arm_r", -2.0000F, 6.8000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 427, "boss/abyssal_smith/arm_r");
		put("abyssal_smith", "arm_l", 2.0000F, 6.8000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 445, "boss/abyssal_smith/arm_l");
		put("abyssal_smith", "leg_r", -0.9000F, 2.6000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 304, "boss/abyssal_smith/leg_r");
		put("abyssal_smith", "leg_l", 0.9000F, 2.6000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 305, "boss/abyssal_smith/leg_l");
		put("abyssal_smith", "torso", 0.0000F, 2.7000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 1083, "boss/abyssal_smith/torso");
		HEIGHT.put("abyssal_smith", 9.0000F);
		put("chrono_sentinel", "ring", 0.0041F, 4.2407F, 0.1224F, 2.2857F, 0.0041F, 0.9326F, 0.3608F, 538, "boss/chrono_sentinel/ring");
		put("chrono_sentinel", "clock", 0.0013F, 3.1295F, 0.5763F, 2.2857F, -0.0033F, -0.0311F, 0.9995F, 73, "boss/chrono_sentinel/clock");
		put("chrono_sentinel", "arm_r", -0.9821F, 3.6607F, 0.0000F, 2.2857F, 0.0000F, 1.0000F, 0.0000F, 865, "boss/chrono_sentinel/arm_r");
		put("chrono_sentinel", "arm_l", 0.9821F, 3.6607F, 0.0000F, 2.2857F, 0.0000F, 1.0000F, 0.0000F, 861, "boss/chrono_sentinel/arm_l");
		put("chrono_sentinel", "body", 0.0000F, 2.5000F, 0.0000F, 2.2857F, 0.0000F, 1.0000F, 0.0000F, 1561, "boss/chrono_sentinel/body");
		HEIGHT.put("chrono_sentinel", 5.0000F);
		put("prism_colossus", "arm_r", -1.9783F, 5.3261F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 958, "boss/prism_colossus/arm_r");
		put("prism_colossus", "arm_l", 1.9783F, 5.3261F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 955, "boss/prism_colossus/arm_l");
		put("prism_colossus", "head", 0.0000F, 5.7826F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 282, "boss/prism_colossus/head");
		put("prism_colossus", "leg_r", -0.8370F, 2.3587F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 337, "boss/prism_colossus/leg_r");
		put("prism_colossus", "leg_l", 0.8370F, 2.3587F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 321, "boss/prism_colossus/leg_l");
		put("prism_colossus", "torso", 0.0000F, 2.3587F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 896, "boss/prism_colossus/torso");
		HEIGHT.put("prism_colossus", 7.0000F);
		put("abyssal_smith", "hammer", -2.3500F, 3.5000F, 0.4500F, 2.0000F, 0.0000F, 1.0000F, 0.0000F, 259, "boss/smith_hammer/hammer");
		put("tempest_wyvern", "foot_fl", 0.6000F, 0.3300F, 2.3250F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 43, "boss/tempest_wyvern/foot_fl");
		put("tempest_wyvern", "foot_fr", -0.6000F, 0.3300F, 2.3250F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 40, "boss/tempest_wyvern/foot_fr");
		put("tempest_wyvern", "foot_hl", 0.6000F, 0.3300F, -0.1500F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 48, "boss/tempest_wyvern/foot_hl");
		put("tempest_wyvern", "foot_hr", -0.6000F, 0.3300F, -0.1500F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 55, "boss/tempest_wyvern/foot_hr");
		put("tempest_wyvern", "shin_fl", 0.6000F, 0.7200F, 2.2200F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 46, "boss/tempest_wyvern/shin_fl");
		put("tempest_wyvern", "shin_fr", -0.6000F, 0.7200F, 2.2200F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 39, "boss/tempest_wyvern/shin_fr");
		put("tempest_wyvern", "shin_hl", 0.6000F, 0.7200F, -0.1200F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 41, "boss/tempest_wyvern/shin_hl");
		put("tempest_wyvern", "shin_hr", -0.6000F, 0.7200F, -0.1200F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 38, "boss/tempest_wyvern/shin_hr");
		put("tempest_wyvern", "thigh_fl", 0.6300F, 1.3800F, 1.8750F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 84, "boss/tempest_wyvern/thigh_fl");
		put("tempest_wyvern", "thigh_fr", -0.6300F, 1.3800F, 1.8750F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 57, "boss/tempest_wyvern/thigh_fr");
		put("tempest_wyvern", "thigh_hl", 0.6300F, 1.3800F, -0.0750F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 134, "boss/tempest_wyvern/thigh_hl");
		put("tempest_wyvern", "thigh_hr", -0.6300F, 1.3800F, -0.0750F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 28, "boss/tempest_wyvern/thigh_hr");
		put("tempest_wyvern", "head", 0.0000F, 3.1500F, 2.1300F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 521, "boss/tempest_wyvern/head");
		put("tempest_wyvern", "neck_2", 0.0000F, 2.9250F, 1.3500F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 225, "boss/tempest_wyvern/neck_2");
		put("tempest_wyvern", "neck_1", 0.0000F, 2.6250F, 0.6000F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 89, "boss/tempest_wyvern/neck_1");
		put("tempest_wyvern", "wing_l", 0.8250F, 3.0000F, 0.6000F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 373, "boss/tempest_wyvern/wing_l");
		put("tempest_wyvern", "wing_r", -0.8250F, 3.0000F, 0.6000F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 352, "boss/tempest_wyvern/wing_r");
		put("tempest_wyvern", "tail_4", 0.0000F, 2.0250F, -2.7000F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 190, "boss/tempest_wyvern/tail_4");
		put("tempest_wyvern", "tail_3", 0.0000F, 1.5750F, -1.8750F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 84, "boss/tempest_wyvern/tail_3");
		put("tempest_wyvern", "tail_2", 0.0000F, 1.5000F, -1.2750F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 108, "boss/tempest_wyvern/tail_2");
		put("tempest_wyvern", "tail_1", 0.0000F, 1.6500F, -0.7500F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 130, "boss/tempest_wyvern/tail_1");
		put("tempest_wyvern", "hips", 0.0000F, 1.7250F, -0.3000F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 196, "boss/tempest_wyvern/hips");
		put("tempest_wyvern", "body", 0.0000F, 1.8000F, 0.2250F, 2.3704F, 0.0000F, 1.0000F, 0.0000F, 883, "boss/tempest_wyvern/body");
		HEIGHT.put("tempest_wyvern", 6.0000F);
	}
}
