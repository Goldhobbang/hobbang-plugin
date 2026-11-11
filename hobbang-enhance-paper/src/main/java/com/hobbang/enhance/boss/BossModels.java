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
	private static final Map<String, Vector3f[]> RIG = new HashMap<>();

	private BossModels() {}

	private static void put(String b, String p, float x, float y, float z, float s, float ax, float ay, float az, int n, String key) {
		SPECS.put(b + "/" + p, new Spec(b, p, new Vector3f(x, y, z), s, new Vector3f(ax, ay, az).normalize(), n, key));
	}

	public static Spec get(String boss, String part) {
		Spec s = SPECS.get(boss + "/" + part);
		if (s == null) throw new IllegalArgumentException("no boss model " + boss + "/" + part);
		return s;
	}

	private static void rig(String b, String p, float ox, float oy, float oz, float rx, float ry, float rz) {
		RIG.put(b + "/" + p, new Vector3f[]{new Vector3f(ox, oy, oz), new Vector3f(rx, ry, rz)});
	}

	/** v1.5.0 박스 리그 관절: {부모 피벗 기준 오프셋(부모 좌표계), rest 회전(도, X→Y→Z 순)}. 복셀 모델 파츠는 null */
	public static Vector3f[] rig(String boss, String part) {
		return RIG.get(boss + "/" + part);
	}

	static Iterable<Spec> all() {
		return SPECS.values();
	}

	public static float height(String boss) {
		return HEIGHT.getOrDefault(boss, 0F);
	}

	static {
		put("abyssal_smith", "head", 0.0000F, 7.2000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 181, "boss/abyssal_smith/head");
		put("abyssal_smith", "hand_r", -2.2917F, 4.4500F, 0.2083F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 185, "boss/abyssal_smith/hand_r");
		put("abyssal_smith", "fore_r", -2.0278F, 5.3000F, 0.0278F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 43, "boss/abyssal_smith/fore_r");
		put("abyssal_smith", "arm_r", -2.0000F, 6.8000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 109, "boss/abyssal_smith/arm_r");
		put("abyssal_smith", "hand_l", 2.2917F, 4.4500F, 0.2083F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 191, "boss/abyssal_smith/hand_l");
		put("abyssal_smith", "fore_l", 2.0278F, 5.3000F, 0.0278F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 53, "boss/abyssal_smith/fore_l");
		put("abyssal_smith", "arm_l", 2.0000F, 6.8000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 93, "boss/abyssal_smith/arm_l");
		put("abyssal_smith", "foot_r", -1.3056F, 0.6500F, 0.2500F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 5, "boss/abyssal_smith/foot_r");
		put("abyssal_smith", "shin_r", -1.1389F, 1.5500F, -0.0833F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 57, "boss/abyssal_smith/shin_r");
		put("abyssal_smith", "leg_r", -0.9000F, 2.6000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 35, "boss/abyssal_smith/leg_r");
		put("abyssal_smith", "foot_l", 1.3056F, 0.6500F, 0.1944F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 5, "boss/abyssal_smith/foot_l");
		put("abyssal_smith", "shin_l", 1.0833F, 1.5500F, -0.0833F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 54, "boss/abyssal_smith/shin_l");
		put("abyssal_smith", "leg_l", 0.9000F, 2.6000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 37, "boss/abyssal_smith/leg_l");
		put("abyssal_smith", "chest", 0.0000F, 4.5000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 447, "boss/abyssal_smith/chest");
		put("abyssal_smith", "torso", 0.0000F, 2.7000F, 0.0000F, 3.7647F, 0.0000F, 1.0000F, 0.0000F, 361, "boss/abyssal_smith/torso");
		HEIGHT.put("abyssal_smith", 9.0000F);
		put("chrono_sentinel", "ring", 0.0041F, 4.2407F, 0.1224F, 2.2857F, 0.0041F, 0.9326F, 0.3608F, 538, "boss/chrono_sentinel/ring");
		put("chrono_sentinel", "clock", 0.0013F, 3.1295F, 0.5763F, 2.2857F, -0.0033F, -0.0311F, 0.9995F, 73, "boss/chrono_sentinel/clock");
		put("chrono_sentinel", "blade_r", -1.7708F, 2.5536F, 0.1875F, 2.2857F, 0.0000F, 1.0000F, 0.0000F, 299, "boss/chrono_sentinel/blade_r");
		put("chrono_sentinel", "arm_r", -0.9821F, 3.6607F, 0.0000F, 2.2857F, 0.0000F, 1.0000F, 0.0000F, 321, "boss/chrono_sentinel/arm_r");
		put("chrono_sentinel", "blade_l", 1.7708F, 2.5536F, 0.1875F, 2.2857F, 0.0000F, 1.0000F, 0.0000F, 293, "boss/chrono_sentinel/blade_l");
		put("chrono_sentinel", "arm_l", 0.9821F, 3.6607F, 0.0000F, 2.2857F, 0.0000F, 1.0000F, 0.0000F, 332, "boss/chrono_sentinel/arm_l");
		put("chrono_sentinel", "pendulum", 0.0000F, 2.1964F, 0.2292F, 2.2857F, 0.0000F, 1.0000F, 0.0000F, 621, "boss/chrono_sentinel/pendulum");
		put("chrono_sentinel", "body", 0.0000F, 2.5000F, 0.0000F, 2.2857F, 0.0000F, 1.0000F, 0.0000F, 978, "boss/chrono_sentinel/body");
		HEIGHT.put("chrono_sentinel", 5.0000F);
		put("prism_colossus", "head", 0.0000F, 5.7826F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 290, "boss/prism_colossus/head");
		put("prism_colossus", "fist_r", -2.0938F, 3.3022F, 0.2188F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 466, "boss/prism_colossus/fist_r");
		put("prism_colossus", "fore_r", -1.7812F, 4.3065F, -0.1562F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 281, "boss/prism_colossus/fore_r");
		put("prism_colossus", "arm_r", -1.9783F, 5.3261F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 286, "boss/prism_colossus/arm_r");
		put("prism_colossus", "fist_l", 2.0938F, 3.3022F, 0.2188F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 464, "boss/prism_colossus/fist_l");
		put("prism_colossus", "fore_l", 1.7188F, 4.3065F, -0.1562F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 277, "boss/prism_colossus/fore_l");
		put("prism_colossus", "arm_l", 1.9783F, 5.3261F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 275, "boss/prism_colossus/arm_l");
		put("prism_colossus", "shin_r", -0.9062F, 1.4000F, -0.3542F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 40, "boss/prism_colossus/shin_r");
		put("prism_colossus", "leg_r", -0.8370F, 2.3587F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 79, "boss/prism_colossus/leg_r");
		put("prism_colossus", "shin_l", 0.8958F, 1.4000F, -0.3542F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 39, "boss/prism_colossus/shin_l");
		put("prism_colossus", "leg_l", 0.8370F, 2.3587F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 116, "boss/prism_colossus/leg_l");
		put("prism_colossus", "chest", 0.0000F, 4.0935F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 436, "boss/prism_colossus/chest");
		put("prism_colossus", "torso", 0.0000F, 2.3587F, 0.0000F, 3.2000F, 0.0000F, 1.0000F, 0.0000F, 59, "boss/prism_colossus/torso");
		HEIGHT.put("prism_colossus", 7.0000F);
		put("abyssal_smith", "hammer", -2.3500F, 3.5000F, 0.4500F, 2.0000F, 0.0000F, 1.0000F, 0.0000F, 76, "boss/smith_hammer/hammer");
		put("tempest_wyvern", "body", 0.0000F, 1.5640F, 0.3740F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 4, "boss/tempest_wyvern/body");
		put("tempest_wyvern", "waist", 0.0000F, 1.5640F, -0.1700F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 4, "boss/tempest_wyvern/waist");
		put("tempest_wyvern", "hips", 0.0000F, 1.5640F, -1.0200F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 4, "boss/tempest_wyvern/hips");
		put("tempest_wyvern", "tail_1", 0.0000F, 1.5300F, -1.8020F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 4, "boss/tempest_wyvern/tail_1");
		put("tempest_wyvern", "tail_2", 0.0000F, 1.4306F, -2.5091F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 4, "boss/tempest_wyvern/tail_2");
		put("tempest_wyvern", "tail_3", 0.0000F, 1.2893F, -3.1742F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 3, "boss/tempest_wyvern/tail_3");
		put("tempest_wyvern", "tail_4", 0.0000F, 1.1712F, -3.8439F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 2, "boss/tempest_wyvern/tail_4");
		put("tempest_wyvern", "tail_5", 0.0000F, 1.1036F, -4.4863F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 2, "boss/tempest_wyvern/tail_5");
		put("tempest_wyvern", "tail_6", 0.0000F, 1.0823F, -5.0979F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 3, "boss/tempest_wyvern/tail_6");
		put("tempest_wyvern", "neck_1", 0.0000F, 1.7340F, 0.9180F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 3, "boss/tempest_wyvern/neck_1");
		put("tempest_wyvern", "neck_2", 0.0000F, 2.1317F, 1.4271F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 3, "boss/tempest_wyvern/neck_2");
		put("tempest_wyvern", "neck_3", 0.0000F, 2.6005F, 1.8204F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 3, "boss/tempest_wyvern/neck_3");
		put("tempest_wyvern", "head", 0.0000F, 2.9564F, 2.2759F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 21, "boss/tempest_wyvern/head");
		put("tempest_wyvern", "jaw", 0.0000F, 2.8145F, 2.4411F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 11, "boss/tempest_wyvern/jaw");
		put("tempest_wyvern", "thigh_fl", 0.4896F, 1.3260F, 0.6800F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 1, "boss/tempest_wyvern/thigh_fl");
		put("tempest_wyvern", "shin_fl", 0.4896F, 0.7097F, 0.9674F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 1, "boss/tempest_wyvern/shin_fl");
		put("tempest_wyvern", "foot_fl", 0.4896F, 0.1027F, 0.7464F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 7, "boss/tempest_wyvern/foot_fl");
		put("tempest_wyvern", "thigh_fr", -0.4896F, 1.3260F, 0.6800F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 1, "boss/tempest_wyvern/thigh_fr");
		put("tempest_wyvern", "shin_fr", -0.4896F, 0.7097F, 0.9674F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 1, "boss/tempest_wyvern/shin_fr");
		put("tempest_wyvern", "foot_fr", -0.4896F, 0.1027F, 0.7464F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 7, "boss/tempest_wyvern/foot_fr");
		put("tempest_wyvern", "thigh_hl", 0.4624F, 1.3600F, -1.3940F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 1, "boss/tempest_wyvern/thigh_hl");
		put("tempest_wyvern", "shin_hl", 0.4624F, 0.7122F, -1.0200F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 1, "boss/tempest_wyvern/shin_hl");
		put("tempest_wyvern", "foot_hl", 0.4624F, 0.1233F, -1.3600F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 7, "boss/tempest_wyvern/foot_hl");
		put("tempest_wyvern", "thigh_hr", -0.4624F, 1.3600F, -1.3940F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 1, "boss/tempest_wyvern/thigh_hr");
		put("tempest_wyvern", "shin_hr", -0.4624F, 0.7122F, -1.0200F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 1, "boss/tempest_wyvern/shin_hr");
		put("tempest_wyvern", "foot_hr", -0.4624F, 0.1233F, -1.3600F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 7, "boss/tempest_wyvern/foot_hr");
		put("tempest_wyvern", "wing_arm_l", 0.4216F, 1.9720F, 0.6120F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 2, "boss/tempest_wyvern/wing_arm_l");
		put("tempest_wyvern", "wing_fore_l", 1.2677F, 2.5125F, 0.4322F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 3, "boss/tempest_wyvern/wing_fore_l");
		put("tempest_wyvern", "wing_f1_l", 2.3351F, 2.7901F, 0.7786F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 2, "boss/tempest_wyvern/wing_f1_l");
		put("tempest_wyvern", "wing_f2_l", 2.3351F, 2.7901F, 0.7786F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 6, "boss/tempest_wyvern/wing_f2_l");
		put("tempest_wyvern", "wing_f3_l", 2.3351F, 2.7901F, 0.7786F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 9, "boss/tempest_wyvern/wing_f3_l");
		put("tempest_wyvern", "wing_arm_r", -0.4216F, 1.9720F, 0.6120F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 2, "boss/tempest_wyvern/wing_arm_r");
		put("tempest_wyvern", "wing_fore_r", -1.2677F, 2.5125F, 0.4322F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 3, "boss/tempest_wyvern/wing_fore_r");
		put("tempest_wyvern", "wing_f1_r", -2.3351F, 2.7901F, 0.7786F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 2, "boss/tempest_wyvern/wing_f1_r");
		put("tempest_wyvern", "wing_f2_r", -2.3351F, 2.7901F, 0.7786F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 6, "boss/tempest_wyvern/wing_f2_r");
		put("tempest_wyvern", "wing_f3_r", -2.3351F, 2.7901F, 0.7786F, 1.6000F, 0.0000F, 1.0000F, 0.0000F, 9, "boss/tempest_wyvern/wing_f3_r");
		rig("tempest_wyvern", "body", 0.0000F, 1.5640F, 0.3740F, 0.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "waist", 0.0000F, 0.0000F, -0.5440F, 0.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "hips", 0.0000F, 0.0000F, -0.8500F, 0.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "tail_1", 0.0000F, -0.0340F, -0.7820F, -8.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "tail_2", 0.0000F, 0.0000F, -0.7140F, -4.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "tail_3", 0.0000F, 0.0000F, -0.6800F, 2.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "tail_4", 0.0000F, 0.0000F, -0.6800F, 4.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "tail_5", 0.0000F, 0.0000F, -0.6460F, 4.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "tail_6", 0.0000F, 0.0000F, -0.6120F, 4.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "neck_1", 0.0000F, 0.1700F, 0.5440F, -38.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "neck_2", 0.0000F, 0.0000F, 0.6460F, -12.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "neck_3", 0.0000F, 0.0000F, 0.6120F, 12.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "head", 0.0000F, 0.0000F, 0.5780F, 40.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "jaw", 0.0000F, -0.1360F, 0.1700F, 6.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "thigh_fl", 0.4896F, -0.2380F, 0.3060F, -25.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "shin_fl", 0.0000F, -0.6800F, 0.0000F, 45.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "foot_fl", 0.0000F, -0.6460F, 0.0000F, -20.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "thigh_fr", -0.4896F, -0.2380F, 0.3060F, -25.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "shin_fr", 0.0000F, -0.6800F, 0.0000F, 45.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "foot_fr", 0.0000F, -0.6460F, 0.0000F, -20.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "thigh_hl", 0.4624F, -0.2040F, -0.3740F, -30.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "shin_hl", 0.0000F, -0.7480F, 0.0000F, 60.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "foot_hl", 0.0000F, -0.6800F, 0.0000F, -30.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "thigh_hr", -0.4624F, -0.2040F, -0.3740F, -30.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "shin_hr", 0.0000F, -0.7480F, 0.0000F, 60.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "foot_hr", 0.0000F, -0.6800F, 0.0000F, -30.0000F, 0.0000F, 0.0000F);
		rig("tempest_wyvern", "wing_arm_l", 0.4216F, 0.4080F, 0.2380F, 0.0000F, 12.0000F, 32.0000F);
		rig("tempest_wyvern", "wing_fore_l", 1.0200F, 0.0000F, 0.0000F, 0.0000F, -30.0000F, -14.0000F);
		rig("tempest_wyvern", "wing_f1_l", 1.1560F, 0.0000F, 0.0000F, 0.0000F, 8.0000F, 0.0000F);
		rig("tempest_wyvern", "wing_f2_l", 1.1560F, 0.0000F, 0.0000F, 0.0000F, 40.0000F, 0.0000F);
		rig("tempest_wyvern", "wing_f3_l", 1.1560F, 0.0000F, 0.0000F, 0.0000F, 74.0000F, 0.0000F);
		rig("tempest_wyvern", "wing_arm_r", -0.4216F, 0.4080F, 0.2380F, 0.0000F, -12.0000F, -32.0000F);
		rig("tempest_wyvern", "wing_fore_r", -1.0200F, 0.0000F, 0.0000F, 0.0000F, 30.0000F, 14.0000F);
		rig("tempest_wyvern", "wing_f1_r", -1.1560F, 0.0000F, 0.0000F, 0.0000F, -8.0000F, 0.0000F);
		rig("tempest_wyvern", "wing_f2_r", -1.1560F, 0.0000F, 0.0000F, 0.0000F, -40.0000F, 0.0000F);
		rig("tempest_wyvern", "wing_f3_r", -1.1560F, 0.0000F, 0.0000F, 0.0000F, -74.0000F, 0.0000F);
		HEIGHT.put("tempest_wyvern", 6.0000F);
	}
}
