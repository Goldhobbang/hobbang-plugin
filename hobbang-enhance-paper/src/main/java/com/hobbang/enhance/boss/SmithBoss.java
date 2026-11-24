package com.hobbang.enhance.boss;

import static com.hobbang.enhance.boss.BossMotion.Move.*;

import com.hobbang.enhance.Keys;
import com.hobbang.enhance.item.HItem;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Vex;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 심연의 대장장이 — 3페이즈 보스. 바닐라 위더 스켈레톤(×3.0, 2.1×7.2) + 바닐라 AI 위에 패턴을 얹는다.
 * v1.3.0: DisplayBoss 로 이전. 리소스팩 모델을 쓰면 위더 스켈레톤은 투명 히트박스가 되고 9블록 거인 모델(7파츠)이 탄다.
 * 블록 모델 모드(bossModels=off / 팩 없음)에서는 예전처럼 커진 위더 스켈레톤이 그대로 보인다.
 */
public class SmithBoss extends DisplayBoss {
	private static final org.bukkit.NamespacedKey EMPTY_MODEL = new org.bukkit.NamespacedKey("hobbang", "empty");
	private static final org.bukkit.NamespacedKey HELM_ARMOR = com.hobbang.enhance.Keys.k("model_helmet_armor");
	private static final org.bukkit.NamespacedKey HELM_TOUGH = com.hobbang.enhance.Keys.k("model_helmet_tough");
	/** 망치 내려찍기: 28틱 중 남은 6틱에 타격 (묵직한 준비 동작) */
	private static final int SLAM_TIME = 28, SLAM_HIT = 6;
	private static final double SLAM_RADIUS = 7.0;
	private int slamTimer;
	/** 내려찍기를 시작한 틱 — 어떤 이유로든 타이머가 제때 안 끝나도 자세가 기울어진 채 굳지 않게 하는 안전장치용 */
	private int slamStart;
	private int forgeSurgeUntil;
	private static final int PRE_PHASE_TICKS = 60;
	private int p2ForgeTick = 0, p3AwakenTick = 0;
	private boolean purpleActive = false;
	private boolean deathHeadOff = false;
	private boolean deathBodyOff = false;
	private boolean shattered = false;
	private Vector3f[] physPos, physVel, physAng, physScale;
	private Quaternionf[] physRot;
	private float walkWeight;
	private Vector slamAt = null;
	private Rigid headRig, mouthRig, hammerRig;
	private double lastX = Double.NaN, lastZ, moved = 0;
	private static final int SLAM_CD = 160, SLAM_CD_LATE = 140, FIRE_CD = 240, SPIRAL_CD = 280, WAVE_CD = 360;
	private static final int VEX_CD = 440, METEOR_CD = 440, RING_CD = 320, THUNDER_CD = 200;
	private int slamCd = slamCooldown(), fireCd = FIRE_CD, waveCd = WAVE_CD, vexCd = VEX_CD, thunderCd = THUNDER_CD;
	private int spiralCd = SPIRAL_CD, meteorCd = METEOR_CD, ringCd = RING_CD;

	private int slamCooldown() { return phase >= 2 ? SLAM_CD_LATE : SLAM_CD; }
	private boolean waveActive = false;
	private double waveRadius = 0;
	private Vector waveCenter = new Vector();
	private final Set<UUID> waveHit = new HashSet<>();
	private final List<double[]> strikes = new ArrayList<>();   // {x, y, z, ticksLeft}

	public SmithBoss(Mob mob) {
		super(mob, BossKind.ABYSSAL_SMITH);
		mob.setGravity(true);   // 점프 도중 서버가 꺼져 무중력이 저장된 경우 복구
		if (this.phase >= 3) this.purpleActive = true;
	}

	@Override
	public void unload() {
		leapReset(true);   // 청크 언로드·서버 종료 시 무중력 상태가 저장되지 않게
		tpJumpReset(true);
		super.unload();
	}

	@Override protected float[] phaseThresholds() { return new float[]{0.66F, 0.33F}; }
	@Override protected BossBar.Color phaseColor(int p) { return p == 1 ? BossBar.Color.BLUE : p == 2 ? BossBar.Color.RED : BossBar.Color.PURPLE; }
	@Override protected int phaseInvulTicks(int newPhase) { return newPhase == 2 ? 122 : newPhase == 3 ? 225 : 50; }
	@Override protected boolean glowParts() { return purpleActive && !dying; }
	@Override protected int glowColor() { return purpleActive ? 0xB040FF : 0x40E0FF; }
	@Override protected boolean overrideEnergyModels() { return purpleActive; }
	@Override protected int xpReward() { return 500; }
	@Override protected float legacyScale() { return 1.0F; }
	@Override protected void bossTick(List<Player> near, LivingEntity target) {}

	// t401/t405: 모델 방향을 바닐라 몸통 yaw 에서 완전히 분리하고 매 틱 바닐라 bodyYaw/rotation 도 faceYaw 로 동기화해
	// MoveControl·밀림·근접 공격 시 90° 회전 재발을 막는다. 소환/재로드 직후엔 가까운 플레이어를 바라보고, 찍기 중엔 방향을 잠근다.
	private static final float FACE_TURN = 7F;
	private float faceYaw = Float.NaN;

	public float facingYaw() {
		return Float.isNaN(faceYaw) ? mob.getBodyYaw() : faceYaw;
	}

	@Override protected float modelYaw() {
		return !Float.isNaN(slamYaw) && (slamQueued || slamTimer > 0) ? slamYaw : facingYaw();
	}

	private void tickFace() {
		Location l = mob.getLocation();
		if (assembling()) {
			Player near = nearestPlayer(32);
			if (near != null) {
				Location tl = near.getLocation();
				faceYaw = (float) Math.toDegrees(Math.atan2(-(tl.getX() - l.getX()), tl.getZ() - l.getZ()));
				mob.setBodyYaw(faceYaw);
				mob.setRotation(faceYaw, l.getPitch());
			} else {
				faceYaw = Float.NaN;
			}
			return;
		}
		if (Float.isNaN(faceYaw)) {
			LivingEntity init = isTargetable(mob.getTarget()) ? mob.getTarget() : nearestPlayer(32);
			if (init != null) {
				Location tl = init.getLocation();
				faceYaw = (float) Math.toDegrees(Math.atan2(-(tl.getX() - l.getX()), tl.getZ() - l.getZ()));
			} else {
				faceYaw = mob.getBodyYaw();
			}
		}
		float want = faceYaw;
		if (leapStage != 0) want = leapYaw;
		else if ((tpTicks > 0 || tpJumping) && tpTarget != null) want = (float) Math.toDegrees(Math.atan2(-(tpTarget.getX() - l.getX()), tpTarget.getZ() - l.getZ()));
		else if (slamTimer > 0 && slamTimer <= SLAM_HIT + 2) want = faceYaw;
		else {
			LivingEntity t = mob.getTarget();
			if (isTargetable(t)) {
				Location tl = t.getLocation();
				double dx = tl.getX() - l.getX(), dz = tl.getZ() - l.getZ();
				// 타깃이 코앞에 붙어도 방향이 90°씩 튀거나 굳지 않게 최소 데드존만 둔다
				if (dx * dx + dz * dz > 0.35 * 0.35) want = (float) Math.toDegrees(Math.atan2(-dx, dz));
			} else if (!Double.isNaN(lastX)) {
				double dx = l.getX() - lastX, dz = l.getZ() - lastZ;
				if (dx * dx + dz * dz > 0.0025) want = (float) Math.toDegrees(Math.atan2(-dx, dz));
			}
		}
		float diff = BossMotion.wrap(want - faceYaw);
		faceYaw = BossMotion.wrap(faceYaw + BossMotion.clamp(diff, -FACE_TURN, FACE_TURN));
		mob.setBodyYaw(faceYaw);
		mob.setRotation(faceYaw, 0);
	}

	private Player nearestPlayer(double range) {
		double best = range * range;
		Player found = null;
		Location l = mob.getLocation();
		for (Player p : mob.getWorld().getPlayers()) {
			if (!BU.valid(p)) continue;
			double d2 = p.getLocation().distanceSquared(l);
			if (d2 < best) { best = d2; found = p; }
		}
		return found;
	}
	// ───────── 모델 (v1.3.0) ─────────
	private Part pTorso, pChest, pLegL, pLegR, pShinL, pShinR, pFootL, pFootR, pHead, pJaw, pArmL, pArmR, pForeL, pForeR, pHandL, pHandR, pHammer;
	private final Part[] pFingersL = new Part[5], pFingersR = new Part[5];
	private final Part[] pTipsL = new Part[5], pTipsR = new Part[5];
	private boolean skillDamage = false;

	private boolean hasHammer() {
		return !assembling() && (phase >= 2 || (p2ForgeTick > 0 && p2ForgeTick >= 22));
	}

	@Override
	protected void buildParts() {
		pTorso = pChest = pLegL = pLegR = pShinL = pShinR = pFootL = pFootR = pHead = pJaw = pArmL = pArmR = pForeL = pForeR = pHandL = pHandR = pHammer = null;
		java.util.Arrays.fill(pFingersL, null);
		java.util.Arrays.fill(pFingersR, null);
		java.util.Arrays.fill(pTipsL, null);
		java.util.Arrays.fill(pTipsR, null);
		if (!modelMode) return;   // 블록 모델 모드: 위더 스켈레톤 자체가 외형
		// v1.5.0 리그: 16 기본 파츠 + 20 2마디 손가락 관절(근위+끝마디) + 2페이즈 망치
		pTorso = energyModel("torso", false, true); pChest = energyModel("chest", false, true); pHead = energyModel("head", false, false);
		pJaw = model("jaw", false);
		pLegL = model("leg_l", false); pShinL = model("shin_l", false); pFootL = model("foot_l", false);
		pLegR = model("leg_r", false); pShinR = model("shin_r", false); pFootR = model("foot_r", false);
		pArmL = model("arm_l", false); pForeL = model("fore_l", false); pHandL = model("hand_l", false);
		pArmR = model("arm_r", false); pForeR = model("fore_r", false); pHandR = model("hand_r", false);
		for (int i = 0; i < 5; i++) {
			pFingersL[i] = model("finger_l" + (i + 1), false);
			pFingersR[i] = model("finger_r" + (i + 1), false);
			pTipsL[i] = model("tip_l" + (i + 1), false);
			pTipsR[i] = model("tip_r" + (i + 1), false);
		}
		pHammer = model("hammer", false);
	}

	private void applyPurpleModels() {
		for (Part p : parts) {
			if (p.id == null || !(p.display instanceof org.bukkit.entity.ItemDisplay idsp) || !idsp.isValid()) continue;
			String key = p.id.equals("hammer") ? "boss/smith_hammer/hammer_purple" : "boss/abyssal_smith/" + p.id + "_purple";
			ItemStack st = new ItemStack(Material.PAPER);
			st.editMeta(m -> m.setItemModel(new org.bukkit.NamespacedKey("hobbang", key)));
			idsp.setItemStack(st);
		}
		refreshGlow();
	}

	@Override
	protected void onPartsRebuilt() {
		applyLook();
		if (phase >= 3 || purpleActive) {
			purpleActive = true;
			applyPurpleModels();
		}
	}

	@Override
	protected void onAssembleFinished() {
		invulTicks = 0;
		applyLook();
	}

	/** 모델 모드: 몹 투명 + 투구 대신 같은 방어력 수식어 + 망치는 2페이즈부터 착용 */
	private void applyLook() {
		boolean hide = modelMode || assembling();
		mob.setInvisible(hide);
		EntityEquipment eq = mob.getEquipment();
		if (eq == null) return;
		org.bukkit.attribute.AttributeInstance ar = mob.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
		org.bukkit.attribute.AttributeInstance tg = mob.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS);
		if (modelMode) {
			eq.setHelmet(null);
			if (ar != null && ar.getModifier(HELM_ARMOR) == null) ar.addModifier(new org.bukkit.attribute.AttributeModifier(HELM_ARMOR, 3, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
			if (tg != null && tg.getModifier(HELM_TOUGH) == null) tg.addModifier(new org.bukkit.attribute.AttributeModifier(HELM_TOUGH, 3, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
		} else {
			if (ar != null) ar.removeModifier(HELM_ARMOR);
			if (tg != null) tg.removeModifier(HELM_TOUGH);
			if (hide) eq.setHelmet(null);
			else {
				eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
				eq.setHelmetDropChance(0);
			}
		}
		if ((hide && !modelMode) || !hasHammer()) eq.setItemInMainHand(null);
		else {
			eq.setItemInMainHand(hammerItem());
			eq.setItemInMainHandDropChance(0);
		}
	}

	private ItemStack hammerItem() {
		ItemStack h = HItem.SMITH_HAMMER.create();
		if (modelMode) h.editMeta(m -> m.setItemModel(EMPTY_MODEL));
		return h;
	}

	@Override
	protected boolean fastPose() {
		return (slamTimer > 0 && slamTimer <= SLAM_HIT + 5) || leapStage == ST_SMASH || tpJumping || p2ForgeTick > 0 || p3AwakenTick > 0;
	}

	@Override
	protected void poseParts(float t) {
		boolean leaping = leapStage != 0;
		playFootfall(t * 0.18F, (leaping || p2ForgeTick > 0 || p3AwakenTick > 0) ? 0 : moved, "hobbang:smith.step", 0.70F);
		if (!modelMode || pTorso == null) return;
		if (p3AwakenTick > 0) { posePhase3Awaken(p3AwakenTick); return; }
		if (p2ForgeTick > 0) { posePhase2Forge(p2ForgeTick); return; }
		aimModel(7.7F, 48, 26, 0.34F);
		walkWeight = BossMotion.mix(walkWeight, leaping ? 0 : Math.min(1, (float) moved * 6), 0.35F);
		BossMotion.Frame strike = leaping ? leapStrike() : motion(SLAM, -2F);
		BossMotion.Frame follow = leaping ? leapStrike() : motion(SLAM, -0.5F);
		BossMotion.Frame cast = motion(CAST).blend(motion(CHANNEL));
		BossMotion.Frame sweep = motion(SWEEP);
		BossMotion.Frame swing = motion(STRIKE), phaseF = motion(PHASE);
		// 쿨다운 예고(forecast)는 제거: 쿨이 끝났는데 대기열이 밀려 스킬이 안 뽑히면 예고 자세(뒤로 젖힘·상체 비틀기)가
		// 계속 1 로 고정돼, 걷는 내내 몸이 기울었다. 실제 예고는 queue() 의 cue(windup) 가 담당한다.
		// 안전장치: 도약·내려찍기·재생 중 큐가 없으면 모든 공격 프레임을 REST 로 → 몸통 pitch/roll 0 (직립), yaw 만 회전
		boolean idleUpright = !leaping && slamTimer <= 0 && !posing();
		if (idleUpright) {
			strike = follow = cast = sweep = swing = phaseF = BossMotion.Frame.REST;
			if (Math.abs(mob.getLocation().getPitch()) > 0.01F) mob.setRotation(mob.getYaw(), 0);
			slamYaw = Float.NaN;
			for (Part p : parts) {
				if (p.display.isValid() && (Math.abs(p.display.getPitch()) > 0.01F || Math.abs(p.display.getYaw()) > 0.01F)) {
					p.display.setRotation(0, 0);
				}
			}
		}
		BossPoses.SmithRig rig = BossPoses.smith(this::placeModel, idle, t, modelYaw(), walkWeight, lookYaw, lookPitch,
			strike, follow, swing, cast, sweep, phaseF, leaping ? leapPose() : BossPoses.Leap.NONE, !hasHammer());
		headRig = rig.head();
		mouthRig = rig.mouth();
		hammerRig = rig.hammer();
	}

	/** t391/t392 숨결: 입 양옆 이빨 사이에서 하얀 김이 "푸슈" 하고 얼굴 정확히 양옆(머리 로컬 ±X)으로 짧게 뿜어진다.
	 *  타이밍은 BossPoses.smithJetTick — 턱 벌림(smithBreathOpen)과 같은 시계라 턱이 벌어져 있는 동안만 분사 */
	private static final int BREATH_JET = BossPoses.SMITH_JET_END - BossPoses.SMITH_JET_START;

	@Override
	protected void onFootfall(int plant) {
		Location l = mob.getLocation();
		float p = plant % 2 == 0 ? 0.52F : 0.58F;
		BU.sound(l, Sound.ENTITY_IRON_GOLEM_STEP, SoundCategory.HOSTILE, 1.3F, p);
		BU.sound(l, Sound.BLOCK_NETHERITE_BLOCK_STEP, SoundCategory.HOSTILE, 1.2F, p);
		BU.sound(l, Sound.BLOCK_CHAIN_STEP, SoundCategory.HOSTILE, 0.75F, 0.72F);
	}

	@Override
	protected void idleDetails() {
		int k = BossPoses.smithJetTick(idle.ticks());   // 0..9, 분사 아니면 -1
		if (k < 0 || idle.weight() < 0.5F) return;
		World world = mob.getWorld();
		Rigid head = modelMode ? headRig : Rigid.yaw(modelYaw()).at(0, (float) (mob.getEyeHeight() / vs), 0);
		if (head == null) return;
		// 입꼬리 양옆(옆니 사이) — 머리 리그 기준이라 고개를 돌리면 같이 돈다
		float width = modelMode ? 0.27F : 0.18F;
		float front = modelMode ? 0.58F : 0.26F;
		float height = modelMode ? 0.15F : -0.14F;
		// 짧게: 처음 0.07 → 끝 0.035 (CLOUD 감속 0.96/틱 → 입꼬리에서 약 0.6~1블록)
		double speed = 0.08 - 0.04 * k / (double) BREATH_JET;
		for (int side = -1; side <= 1; side += 2) {
			Vector mouth = worldPoint(head, side * width, height, front);
			// 정확히 얼굴 양옆: 머리 로컬 ±X 만 (앞/아래 성분 없음) — 고개를 돌리면 같이 돈다
			Vector dir = worldPoint(head, side * (width + 1.0F), height, front).subtract(mouth);
			if (dir.lengthSquared() < 1e-6) continue;
			dir.normalize();
			// 입꼬리 바로 바깥에서 시작 → 얼굴(턱)을 가리지 않음
			Vector from = mouth.clone().add(dir.clone().multiply(0.15));
			Vector v = dir.clone().add(new Vector((Math.random() - 0.5) * 0.08, (Math.random() - 0.5) * 0.08, (Math.random() - 0.5) * 0.08));
			world.spawnParticle(Particle.CLOUD, from.getX(), from.getY(), from.getZ(), 0, v.getX(), v.getY(), v.getZ(), speed, null, true);
			if (k >= 3) {   // 얼굴 옆에서 퍼지며 살짝 위로 흩어진다
				Vector near = mouth.clone().add(dir.clone().multiply(0.45 + k * 0.04));
				world.spawnParticle(Particle.WHITE_SMOKE, near.getX(), near.getY(), near.getZ(), 1, 0.05, 0.04, 0.05, 0.004, null, true);
				if (k % 2 == 1) world.spawnParticle(Particle.POOF, near.getX(), near.getY(), near.getZ(), 0, 0, 1, 0, 0.02, null, true);
			}
		}
		if (k == 0) {
			org.bukkit.Location at = worldPoint(head, 0, height, front).toLocation(world);
			BU.sound(at, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.35F, 1.35F);   // 푸슈우
			BU.sound(at, "hobbang:smith.exhale", SoundCategory.HOSTILE, 0.18F, 1.0F);
		}
	}

	@Override
	protected boolean energySurge(String part) {
		return tickCount < forgeSurgeUntil;
	}

	/** 다리 3관절. swing + = 뒤로. 앞으로 나가는 다리는 무릎을 굽혀 발을 든다. kneel 0..1 = 무릎 꿇기 */
	private void poseLeg(Rigid root, String q, Part thigh, Part shin, Part foot, float swing, float kneel, float sc) {
		Vector3f lp = pv(kind, "leg" + q);
		float knee = Math.max(0, -swing) * 0.9F + 4;
		float th = swing * (1 - kneel) - 35 * kneel;
		float kn = knee * (1 - kneel) + 115 * kneel;
		Rigid t0 = root.at(lp.x, lp.y, lp.z).rotX(th);
		place(thigh, t0, sc);
		Rigid s0 = BossPoses.smithLegChild(t0, "leg" + q, "shin" + q).rotX(kn);
		place(shin, s0, sc);
		place(foot, BossPoses.smithLegChild(s0, "shin" + q, "foot" + q).rotX(-(th + kn) * 0.6F * (1 - kneel)), sc);
	}

	/** 망치 머리 (월드 좌표). 1페이즈 맨손·블록 모델 모드에서는 주먹/타격점 */
	private Vector hammerHead() {
		if (modelMode && hasHammer() && hammerRig != null) return worldPoint(hammerRig, 0F, -2.808F, 0F);   // 망치 머리 중심 (손잡이 피벗 기준)
		if (modelMode) {
			Vector hand = modelPoint("hand_r", 0, -0.35F, 0);
			if (hand != null) return hand;
		}
		Location l = mob.getLocation();
		Vector f = l.getDirection().setY(0);
		if (f.lengthSquared() < 1e-6) f = new Vector(0, 0, 1);
		return l.toVector().add(f.normalize().multiply(1.5));
	}

	/** 내려찍기 착지점: 모델 모드에서는 내려찍는 자세의 망치 머리 위치(몸 앞 ~4.6블록) */
	private Vector slamPoint() {
		if (!modelMode) return hammerHead();
		Location l = mob.getLocation();
		double a = Math.toRadians(modelYaw());
		return l.toVector().add(new Vector(-Math.sin(a), 0, Math.cos(a)).multiply(4.6 * vs));
	}

	/** 화염구 발사 위치: 머리(입) 앞 */
	private Vector mouth() {
		if (modelMode && mouthRig != null) return worldPoint(mouthRig, 0, 0.5F, 1.1F);
		Location l = mob.getLocation();
		return new Vector(l.getX(), l.getY() + mob.getHeight() * 0.7 + 0.5, l.getZ());
	}

	public static void equip(Mob m) {
		EntityEquipment eq = m.getEquipment();
		if (eq == null) return;
		ItemStack h = HItem.SMITH_HAMMER.create();
		if (modelsEnabled()) h.editMeta(mm -> mm.setItemModel(EMPTY_MODEL));
		eq.setItemInMainHand(h);
		eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
		eq.setItemInMainHandDropChance(0);
		eq.setHelmetDropChance(0);
	}

	@Override
	protected void serverTick() {
		if (dead) return;
		// v1.5.0: 죽음 연출 틱 누락 수정 — 이 분기가 없어서 무릎 꿇기·finishDeath 가 돌지 않았다
		if (dying) { leapReset(false); tpJumpReset(false); tpTicks = 0; tpTarget = null; tickDeath(); return; }   // startDeath 가 무중력/AI 를 직접 다룬다
		// 안전장치: 내려찍기 자세는 조립(소환) 중이거나 정해진 시간(SLAM_TIME)을 넘기면 즉시 풀어, 몸이 다시 곧게 선다
		if (slamTimer > 0 && (assembling() || tickCount - slamStart > SLAM_TIME + 4)) { slamTimer = 0; slamAt = null; slamQueued = false; slamYaw = Float.NaN; }
		if (slamQueued || slamTimer > 0) {
			if (Float.isNaN(slamYaw)) slamYaw = facingYaw();
			mob.setRotation(slamYaw, 0);
			mob.setBodyYaw(slamYaw);
		}
		if (leapStage != 0 && assembling()) leapReset(true);
		if ((tpTicks > 0 || tpJumping) && assembling()) { tpJumpReset(true); tpTicks = 0; tpTarget = null; }
		tickFace();
		tickParts();
		if (assembling()) return;
		if (tickCount % 26 == 0) {
			BU.sound(mob.getLocation(), Sound.BLOCK_CHAIN_STEP, SoundCategory.HOSTILE, 0.65F, 0.65F + random.nextFloat() * 0.2F);
		}
		if (tickCount % 52 == 0) {
			BU.sound(mob.getLocation(), Sound.BLOCK_CHAIN_PLACE, SoundCategory.HOSTILE, 0.45F, 0.55F);
		}
		EntityEquipment eq = mob.getEquipment();
		if (eq != null) {
			if (hasHammer()) {
				if (eq.getItemInMainHand().getType().isAir()) eq.setItemInMainHand(hammerItem());
			} else if (!eq.getItemInMainHand().getType().isAir()) {
				eq.setItemInMainHand(null);
			}
		}
		World w = mob.getWorld();
		Location l = mob.getLocation();
		if (!Double.isNaN(lastX)) moved = Math.hypot(l.getX() - lastX, l.getZ() - lastZ);
		lastX = l.getX(); lastZ = l.getZ();
		if (invulTicks > 0) {
			invulTicks--;
			double bh = mob.getHeight(), bw = mob.getWidth();
			if (invulTicks % 3 == 0) {
				if (purpleActive) w.spawnParticle(Particle.WITCH, l.getX(), l.getY() + bh / 2, l.getZ(), 12, bw * 0.6, bh / 3, bw * 0.6, 0.04);
				else w.spawnParticle(Particle.SOUL_FIRE_FLAME, l.getX(), l.getY() + bh / 2, l.getZ(), 12, bw * 0.6, bh / 3, bw * 0.6, 0.02);
			}
		}
		if (p2ForgeTick > 0 || p3AwakenTick > 0) {
			mob.setVelocity(new Vector());
			mob.getPathfinder().stopPathfinding();
			tickPhaseCutscene(w, l);
			tickWave();
			tickStrikes();
			return;
		}
		tickSlam();
		tickLeap();

		int speedUp = phase == 3 ? 2 : 1;
		slamCd -= speedUp;
		fireCd -= 1;
		spiralCd -= 1;
		if (phase >= 2) { waveCd -= 1; vexCd -= 1; meteorCd -= 1; }
		if (phase == 3) { ringCd -= 1; thunderCd -= speedUp; }

		List<Player> near = nearbyPlayers(32);
		if (near.isEmpty()) {
			if (++regenTick >= 20) {
				regenTick = 0;
				heal(maxHealth() * 0.01);
			}
			tickWave();
			tickStrikes();
			return;
		}

		LivingEntity target = mob.getTarget();
		if (target == null || target.isDead()) {
			Player p = near.get(random.nextInt(near.size()));
			if (BU.valid(p)) mob.setTarget(p);
			target = mob.getTarget();
		}

		// 이동 점프 (멀어지거나 끼었을 때): 준비 동작 후 장애물 무시 포물선 점프로 목표 지점에 정확히 착지
		if (tpTicks == 0 && !tpJumping && leapStage == 0 && slamTimer <= 0 && target != null && (l.distance(target.getLocation()) > 20 || inWall()) && tickCount % 40 == 0) {
			Location tl = target.getLocation();
			Location spot = findTeleportSpot(tl.getX() + random.nextInt(5) - 2, tl.getY(), tl.getZ() + random.nextInt(5) - 2);
			if (spot != null) {
				tpTicks = TP_PREP;
				tpTarget = spot;
				queued = null;
				windup = 0;
				slamQueued = false;
				slamYaw = Float.NaN;
				clearMove(SLAM);
				BU.sound(l, "hobbang:smith.scrape", SoundCategory.HOSTILE, 1.5F, 0.7F);
				BU.sound(l, Sound.ENTITY_IRON_GOLEM_STEP, SoundCategory.HOSTILE, 1.4F, 0.55F);
				cue(PHASE, TP_PREP - 10, 10, 12);
			}
		}
		if (tickTeleport(w, l)) {
			tickWave();
			tickStrikes();
			return;
		}

		if (invulTicks <= 0 && leapStage == 0 && !tickWindup()) pickSkill(target, l);
		tickWave();
		tickStrikes();
	}

	private static final int TP_PREP = 24, TP_JUMP_TICKS = 18;
	private int tpTicks, tpJumpTick;
	private boolean tpJumping = false;
	private Location tpTarget, tpFrom;

	private void tpJumpReset(boolean restore) {
		if (!tpJumping) return;
		tpJumping = false;
		tpJumpTick = 0;
		tpFrom = null;
		if (restore && mob != null && mob.isValid()) {
			mob.setGravity(true);
			mob.setAI(true);
		}
	}

	private boolean tickTeleport(World w, Location l) {
		if (tpJumping && tpTarget != null && tpFrom != null) {
			tpJumpTick++;
			double u = Math.min(1.0, tpJumpTick / (double) TP_JUMP_TICKS);
			double dist = Math.hypot(tpTarget.getX() - tpFrom.getX(), tpTarget.getZ() - tpFrom.getZ());
			double peak = Math.max(5.5, Math.min(12.0, dist * 0.28));
			double x = tpFrom.getX() + (tpTarget.getX() - tpFrom.getX()) * u;
			double z = tpFrom.getZ() + (tpTarget.getZ() - tpFrom.getZ()) * u;
			double y = tpFrom.getY() + (tpTarget.getY() - tpFrom.getY()) * u + 4.0 * peak * u * (1.0 - u);
			float yaw = facingYaw();
			Location step = new Location(w, x, y, z, yaw, 0);
			mob.teleport(step, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
			mob.setVelocity(new Vector());
			faceYaw = yaw;
			mob.setBodyYaw(yaw);
			mob.setRotation(yaw, 0);
			w.spawnParticle(purpleActive ? Particle.WITCH : Particle.SOUL_FIRE_FLAME, x, y + 0.6, z, 6, 0.35, 0.3, 0.35, 0.03);
			w.spawnParticle(Particle.CLOUD, x, y + 0.3, z, 3, 0.25, 0.2, 0.25, 0.02);
			if (tpJumpTick >= TP_JUMP_TICKS) {
				Location dest = tpTarget;
				tpTarget = null;
				tpJumpReset(true);
				landFromJump(dest);
			}
			return true;
		}
		if (tpTicks <= 0 || tpTarget == null) return false;
		mob.setVelocity(new Vector());
		mob.getPathfinder().stopPathfinding();
		double u = (TP_PREP - tpTicks) / (double) TP_PREP;
		if (tpTicks % 2 == 0) {
			w.spawnParticle(purpleActive ? Particle.WITCH : Particle.SOUL_FIRE_FLAME, l.getX(), l.getY() + 0.4, l.getZ(), 8, 0.7, 0.3, 0.7, 0.04);
			Vector dv = tpTarget.toVector();
			BU.ring(w, Particle.DUST, BU.dust(purpleActive ? 0xB848FF : 0xFF7030, 1.4F), dv.clone().add(new Vector(0, 0.15, 0)), 2.2 - 1.2 * u, 24);
		}
		if (--tpTicks <= 0) {
			tpFrom = l.clone();
			tpJumping = true;
			tpJumpTick = 0;
			mob.setGravity(false);
			mob.setAI(false);
			mob.setVelocity(new Vector());
			BU.sound(l, "hobbang:smith.slam", SoundCategory.HOSTILE, 1.5F, 0.75F);
			BU.sound(l, Sound.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 1.6F, 0.5F);
			w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, l.getX(), l.getY() + 0.2, l.getZ(), 18, 0.9, 0.15, 0.9, 0.04);
			Fx.burst(w, l.toVector().add(new Vector(0, 0.6, 0)), kind, 14, 0.8);
		}
		return true;
	}

	private void landFromJump(Location dest) {
		World w = mob.getWorld();
		float yaw = facingYaw();
		Location at = new Location(w, dest.getX(), dest.getY(), dest.getZ(), yaw, 0);
		mob.teleport(at, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
		faceYaw = yaw;
		mob.setBodyYaw(yaw);
		mob.setRotation(yaw, 0);
		Fx.burst(w, dest.toVector().add(new Vector(0, 0.8, 0)), kind, 22, 1.0);
		Fx.ripple(this, dest.toVector(), 3.2, 10);
		w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, dest.getX(), dest.getY() + 0.2, dest.getZ(), 24, 1.2, 0.15, 1.2, 0.05);
		w.spawnParticle(purpleActive ? Particle.WITCH : Particle.SOUL_FIRE_FLAME, dest.getX(), dest.getY() + 0.8, dest.getZ(), 20, 0.6, 0.6, 0.6, 0.06);
		BU.sound(dest, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.4F, 0.55F);
		BU.sound(dest, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 1.6F, 0.6F);
	}

	/** 스킬 전 전역 대기: 쿨이 끝난 스킬 하나를 골라 1페 38 / 2페 28 / 3페 20틱 뒤 발동. 대기 중엔 다른 스킬 없음. */
	private Runnable queued;
	private int windup;
	private boolean slamQueued = false;
	private float slamYaw = Float.NaN;

	private void queue(Runnable skill) {
		queued = skill;
		windup = phase == 1 ? 38 : phase == 2 ? 28 : 20;
		BU.sound(mob.getLocation(), "hobbang:smith.scrape", SoundCategory.HOSTILE, 1.2F, 0.7F);
		Vector p = mob.getLocation().toVector().add(new Vector(0, 2.0, 0));
		if (purpleActive) {
			mob.getWorld().spawnParticle(Particle.DUST, p.getX(), p.getY(), p.getZ(), 10, 0.45, 0.45, 0.45, 0,
				new Particle.DustOptions(Color.fromRGB(0xB0, 0x40, 0xFF), 1.4F));
			Fx.puff(mob.getWorld(), Particle.REVERSE_PORTAL, p, 8, 0.45, 0.04);
		} else {
			Fx.puff(mob.getWorld(), Particle.SOUL_FIRE_FLAME, p, 8, 0.45, 0.03);
		}
	}

	/** 대기 중이면 true (발동 틱 포함 — 같은 틱에 다른 스킬을 고르지 않는다) */
	private boolean tickWindup() {
		if (queued == null) return false;
		windup--;
		if (windup == 10) {
			BU.sound(mob.getLocation(), Sound.BLOCK_ANVIL_PLACE, SoundCategory.HOSTILE, 1.0F, 0.55F);
			Fx.burst(mob.getWorld(), hammerHead(), kind, 6, 0.35);
		}
		if (windup <= 0) {
			Runnable r = queued;
			queued = null;
			r.run();
		}
		return true;
	}

	private void pickSkill(LivingEntity target, Location l) {
		double dist = target != null ? l.distance(target.getLocation()) : Double.MAX_VALUE;
		if (slamCd <= 0 && slamTimer <= 0 && dist < 9) {
			slamQueued = true;
			slamYaw = facingYaw();
			mob.setRotation(slamYaw, 0);
			mob.setBodyYaw(slamYaw);
			queue(() -> { startSlam(); slamCd = slamCooldown(); });
			cue(SLAM, windup + SLAM_TIME - SLAM_HIT, 3, SLAM_HIT + 5);
		} else if (fireCd <= 0 && target != null) {
			queue(() -> {
				LivingEntity t = mob.getTarget();
				if (t != null && !t.isDead()) fireVolley(t);
				fireCd = FIRE_CD;
			});
			cue(CAST, windup, 2, 14);
		} else if (phase >= 2 && waveCd <= 0 && !waveActive) {
			queue(() -> { startWave(); waveCd = WAVE_CD; });
			cue(SWEEP, windup, 6, 16);
		} else if (phase >= 2 && vexCd <= 0) {
			queue(() -> { summonVex(2); vexCd = VEX_CD; });
			cue(CHANNEL, windup, 2, 14);
		} else if (spiralCd <= 0 && dist < 10) {
			queue(() -> {
				BossPatterns.warnAll(this, "화염 나선! 불길의 팔 사이로 피하세요!");
				BossPatterns.spiral(this, phase >= 3 ? 4 : 3, 9, 32, 80, 7, purpleActive ? Particle.WITCH : Particle.SOUL_FIRE_FLAME, purpleActive ? 0xB848FF : 0xFF8030);
				spiralCd = SPIRAL_CD;
			});
			cue(CAST, windup, 4, 14);
		} else if (phase >= 2 && meteorCd <= 0) {
			queue(() -> {
				BossPatterns.warnAll(this, "운석 단조! 붉은 원에서 벗어나세요!");
				forgeSurgeUntil = tickCount + 28;
				BossPatterns.meteorsOnPlayers(this, 24, 2.8, 56, 9, Material.MAGMA_BLOCK, true);
				meteorCd = METEOR_CD;
			});
			cue(CAST, windup, 4, 14);
		} else if (phase == 3 && ringCd <= 0 && target != null && dist < 30) {
			queue(() -> {
				if (leapStage == 0) startLeap();
				ringCd = RING_CD;
			});
			cue(PHASE, windup, 0, 10);
		} else if (phase == 3 && thunderCd <= 0) {
			queue(() -> {
				cue(CAST, 24, 0, 14);
				for (Player p : nearbyPlayers(32)) {
					if (!BU.valid(p)) continue;
					Location pl = p.getLocation();
					strikes.add(new double[]{pl.getX(), pl.getY(), pl.getZ(), 34});
				}
				thunderCd = THUNDER_CD;
			});
			cue(CAST, windup, 2, 12);
		}
	}

	private boolean inWall() {
		Location e = mob.getEyeLocation();
		return e.getBlock().getType().isOccluding();
	}

	private Location findTeleportSpot(double x, double y, double z) {
		World w = mob.getWorld();
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		for (int dy = 0; dy < 16; dy++) {
			int by = (int) Math.floor(y) - dy;
			Block floor = w.getBlockAt(bx, by - 1, bz);
			if (!floor.isCollidable()) continue;
			int need = Math.max(4, (int) Math.ceil(mob.getHeight()));
			boolean blocked = false;
			for (int h = 0; h < need; h++) if (w.getBlockAt(bx, by + h, bz).isCollidable()) { blocked = true; break; }
			if (blocked) return null;
			return new Location(w, bx + 0.5, by, bz + 0.5, facingYaw(), 0);
		}
		return null;
	}

	/** 지정 위치로 순간이동: 출발/도착 이펙트·사운드 + 모델 방향 유지 */
	private boolean executeTeleport(Location to) {
		World w = mob.getWorld();
		Location fromLoc = mob.getLocation();
		Vector from = fromLoc.toVector();
		float yaw = facingYaw();
		Location dest = new Location(w, to.getX(), to.getY(), to.getZ(), yaw, 0);
		w.spawnParticle(Particle.EXPLOSION, from.getX(), from.getY() + mob.getHeight() * 0.4, from.getZ(), 4, mob.getWidth() * 0.4, mob.getHeight() * 0.3, mob.getWidth() * 0.4, 0.0);
		w.spawnParticle(Particle.PORTAL, from.getX(), from.getY() + mob.getHeight() / 2, from.getZ(), 40, mob.getWidth() / 2, mob.getHeight() / 3, mob.getWidth() / 2, 0.5);
		Fx.burst(w, from.clone().add(new Vector(0, 1.1, 0)), kind, 18, 1.0);
		BU.sound(fromLoc, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.6F, 0.55F);
		BU.sound(fromLoc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.HOSTILE, 1.2F, 0.65F);
		boolean moved = mob.teleport(dest, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
		if (moved) {
			faceYaw = yaw;
			mob.setBodyYaw(yaw);
			mob.setRotation(yaw, 0);
			Fx.burst(w, dest.toVector().add(new Vector(0, 1.1, 0)), kind, 22, 1.0);
			Fx.ripple(this, dest.toVector(), 3.0, 10);
			w.spawnParticle(purpleActive ? Particle.WITCH : Particle.SOUL_FIRE_FLAME, dest.getX(), dest.getY() + 1.0, dest.getZ(), 24, mob.getWidth() * 0.45, 0.8, mob.getWidth() * 0.45, 0.06);
			BU.sound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.6F, 0.65F);
			BU.sound(dest, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.2F, 0.6F);
		}
		return moved;
	}

	private int lastAssembleFxTick = -1;

	@Override
	protected void onPhaseChange(int newPhase) {
		queued = null;
		windup = 0;
		slamTimer = 0;
		slamQueued = false;
		slamAt = null;
		slamYaw = Float.NaN;
		for (BossMotion.Move m : BossMotion.Move.values()) clearMove(m);
		leapReset(false);
		forgeSurgeUntil = tickCount + (newPhase == 3 ? 225 : 122);
		Location l = mob.getLocation();
		mob.setVelocity(new Vector());
		if (newPhase == 2) {
			p2ForgeTick = 1;
			BU.say(mob, Component.text("심연의 대장장이: \"맨손의 시련은 끝났다... 심연의 망치여, 벼려져라!\"", NamedTextColor.RED, TextDecoration.ITALIC));
			BU.sound(l, Sound.ENTITY_IRON_GOLEM_HURT, SoundCategory.HOSTILE, 1.8F, 0.48F);
			BU.sound(l, Sound.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 1.5F, 0.72F);
			Fx.puff(l.getWorld(), Particle.SMOKE, l.toVector().add(new Vector(0, 2.2, 0)), 14, 0.7, 0.04);
		} else if (newPhase == 3) {
			p2ForgeTick = 0;
			p3AwakenTick = 1;
			bar.addFlag(BossBar.Flag.DARKEN_SCREEN);
			BU.say(mob, Component.text("심연의 대장장이: \"심연의 심장이여... 보라빛 재앙으로 깨어나라!!\"", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
			mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 1, false, false));
			BU.sound(l, Sound.ENTITY_IRON_GOLEM_HURT, SoundCategory.HOSTILE, 1.8F, 0.45F);
			BU.sound(l, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.HOSTILE, 1.8F, 0.55F);
		}
	}

	private void tickPhaseCutscene(World w, Location l) {
		mob.setVelocity(new Vector(0, Math.min(0, mob.getVelocity().getY()), 0));
		Vector base = l.toVector();
		if (p2ForgeTick > 0) {
			int t = p2ForgeTick;
			if (t <= PRE_PHASE_TICKS) {
				if (t % 6 == 0) {
					w.spawnParticle(Particle.SMOKE, l.getX(), l.getY() + 2.3, l.getZ(), 4, 0.45, 0.5, 0.45, 0.02);
					w.spawnParticle(Particle.SOUL_FIRE_FLAME, l.getX(), l.getY() + 1.8, l.getZ(), 2, 0.35, 0.4, 0.35, 0.02);
				}
				if (t >= 44 && t % 2 == 0) {
					double a = t * 0.5;
					double r = 2.0 * (1.0 - (t - 44) / 16.0);
					w.spawnParticle(Particle.FLAME, l.getX() + Math.cos(a) * r, l.getY() + 1.6, l.getZ() + Math.sin(a) * r, 3, 0.1, 0.15, 0.1, 0.02);
				}
				if (t == PRE_PHASE_TICKS) BU.sound(l, "hobbang:smith.emerge", SoundCategory.HOSTILE, 1.5F, 1.05F);
				p2ForgeTick++;
				return;
			}
			int lt = t - PRE_PHASE_TICKS;
			if (lt < 22 && lt % 2 == 0) {
				double ang = lt * 0.65;
				double rad = Math.max(0.25, 2.2 * (1.0 - lt / 22.0));
				Vector p = base.clone().add(new Vector(Math.cos(ang) * rad, 1.2 + lt * 0.07, Math.sin(ang) * rad));
				w.spawnParticle(Particle.FLAME, p.getX(), p.getY(), p.getZ(), 4, 0.12, 0.12, 0.12, 0.04);
				w.spawnParticle(Particle.SOUL_FIRE_FLAME, p.getX(), p.getY(), p.getZ(), 2, 0.08, 0.08, 0.08, 0.03);
			}
			if (lt == 12) BU.sound(l, "hobbang:smith.scrape", SoundCategory.HOSTILE, 1.6F, 0.85F);
			if (lt == 22) {
				Location handLoc = l.clone().add(l.getDirection().setY(0).normalize().multiply(0.9)).add(0, 2.2, 0);
				w.spawnParticle(Particle.EXPLOSION_EMITTER, handLoc, 1, 0, 0, 0, 0);
				Fx.burst(w, handLoc.toVector(), kind, 24, 0.75);
				Fx.ripple(this, base.clone().add(new Vector(0, 0.15, 0)), 8.5, 16);
				spawnEruptChunks(w, base.clone().add(new Vector(0, 0.3, 0)), 10, false);
				playSlamSound(l, 2);
				BU.sound(l, Sound.ITEM_TOTEM_USE, SoundCategory.HOSTILE, 1.2F, 0.55F);
				playPhaseRoar(l, 2);
				summonApprentices(4);
			}
			if (lt == 34) BU.sound(l, "hobbang:smith.scrape", SoundCategory.HOSTILE, 1.5F, 1.0F);
			if (lt >= 56) p2ForgeTick = 0;
			else p2ForgeTick++;
			return;
		}
		if (p3AwakenTick > 0) {
			int t = p3AwakenTick;
			Particle.DustOptions pDust = new Particle.DustOptions(Color.fromRGB(0xB0, 0x40, 0xFF), 1.65F);
			if (t <= PRE_PHASE_TICKS) {
				if (t % 5 == 0) {
					w.spawnParticle(Particle.SMOKE, l.getX(), l.getY() + 2.3, l.getZ(), 5, 0.5, 0.6, 0.5, 0.02);
					w.spawnParticle(Particle.DUST, l.getX(), l.getY() + 2.1, l.getZ(), 4, 0.45, 0.5, 0.45, 0, pDust);
				}
				if (t >= 42 && t % 2 == 0) {
					double a = t * 0.55;
					double r = 2.4 * (1.0 - (t - 42) / 18.0);
					w.spawnParticle(Particle.REVERSE_PORTAL, l.getX() + Math.cos(a) * r, l.getY() + 2.2, l.getZ() + Math.sin(a) * r, 3, 0.1, 0.2, 0.1, 0.08);
				}
				if (t == PRE_PHASE_TICKS) BU.sound(l, "hobbang:smith.emerge", SoundCategory.HOSTILE, 1.8F, 0.72F);
				p3AwakenTick++;
				return;
			}
			int lt = t - PRE_PHASE_TICKS;
			if (lt == 14) {
				playSlamSound(l, 2);
				w.spawnParticle(Particle.BLOCK, l.getX(), l.getY() + 0.15, l.getZ(), 24, 1.1, 0.1, 1.1, 0.15,
					org.bukkit.Material.DEEPSLATE_BRICKS.createBlockData());
			}
			if (lt == 25) {
				BU.sound(l, "hobbang:smith.awaken_beam", SoundCategory.HOSTILE, 2.4F, 1.0F);
				playPhaseRoar(l, 3);
			}
			if (lt >= 20 && lt < 38) {
				float u = (lt - 20) / 18.0F;
				double topY = 26.0 * (1.0 - u) + 5.8;
				double rad = 2.6 * (1.0 - u * 0.75);
				for (int i = 0; i < 10; i++) {
					double a = lt * 0.45 + Math.PI * 2 * i / 10.0;
					w.spawnParticle(Particle.DUST, l.getX() + Math.cos(a) * rad, l.getY() + topY, l.getZ() + Math.sin(a) * rad,
						1, 0.08, 0.15, 0.08, 0, pDust);
				}
				w.spawnParticle(Particle.REVERSE_PORTAL, l.getX(), l.getY() + 5.4, l.getZ(), 6, 0.4, 0.8, 0.4, 0.12);
			}
			if (lt == 38) {
				Vector top = base.clone().add(new Vector(0, 5.2, 0));
				spawnPurpleSkyBeam(w, base, top);
				Fx.ripple(this, base.clone().add(new Vector(0, 0.2, 0)), 14.0, 28);
				spawnEruptChunks(w, base.clone().add(new Vector(0, 0.35, 0)), 16, true);
				BU.sound(l, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.9F, 0.62F);
				BU.sound(l, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.0F, 0.75F);
				BU.sound(l, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 2.0F, 0.65F);
			}
			if (lt >= 38 && lt <= 142 && lt % 2 == 0) {
				w.spawnParticle(Particle.WITCH, l.getX(), l.getY() + 3.4, l.getZ(), 10, 0.9, 1.8, 0.9, 0.08);
				w.spawnParticle(Particle.END_ROD, l.getX(), l.getY() + 7.2, l.getZ(), 6, 0.35, 4.0, 0.35, 0.04);
				if (lt % 14 == 0) Fx.ripple(this, base.clone().add(new Vector(0, 0.2, 0)), 11.0, 14);
			}
			if (lt == 88) BU.sound(l, "hobbang:smith.awaken_beam", SoundCategory.HOSTILE, 2.0F, 0.92F);
			if (lt == 68) {
				purpleActive = true;
				applyPurpleModels();
				BU.sound(l, Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.4F, 0.68F);
				w.spawnParticle(Particle.EXPLOSION_EMITTER, l.getX(), l.getY() + 3.0, l.getZ(), 1, 0, 0, 0, 0);
				for (int i = 0; i < 32; i++) {
					double a = Math.PI * 2 * i / 32.0;
					w.spawnParticle(Particle.REVERSE_PORTAL, l.getX() + Math.cos(a) * 1.4, l.getY() + 2.6, l.getZ() + Math.sin(a) * 1.4,
						2, 0.2, 0.3, 0.2, 0.25);
				}
			}
			if (lt >= 160) p3AwakenTick = 0;
			else p3AwakenTick++;
		}
	}

	@Override
	protected void poseAssemble(AssembleAnim.Style style, int tick) {
		if (style != AssembleAnim.Style.SMITH_EMERGE) {
			super.poseAssemble(style, tick);
			return;
		}
		if (!modelMode || pTorso == null) return;
		float sc = 1.0F;
		float yaw = modelYaw();
		if (tick != lastAssembleFxTick) {
			lastAssembleFxTick = tick;
			World w = mob.getWorld();
			Location l = mob.getLocation();
			if (tick == 18 || tick == 46) {
				BU.sound(l, "hobbang:smith.slam", SoundCategory.HOSTILE, 1.45F, tick == 18 ? 0.78F : 0.70F);
				BU.sound(l, Sound.BLOCK_BASALT_BREAK, SoundCategory.HOSTILE, 1.5F, 0.55F);
				w.spawnParticle(Particle.BLOCK, l.getX(), l.getY() + 0.15, l.getZ(), 22, 1.3, 0.1, 1.3, 0.18,
					org.bukkit.Material.MAGMA_BLOCK.createBlockData());
			} else if (tick == 114) {
				playPhaseRoar(l, 1);
				BU.sound(l, "hobbang:smith.slam", SoundCategory.HOSTILE, 1.8F, 0.65F);
				Vector c = l.toVector();
				spawnEruptChunks(w, c.clone().add(new Vector(0, 0.35, 0)), 14, false);
				Fx.ripple(this, c.clone().add(new Vector(0, 0.15, 0)), 12.0, 22);
				Fx.burst(w, c.clone().add(new Vector(0, 3.0, 0)), kind, 28, 0.85);
			}
		}

		float yOff;
		float torsoX = 0, torsoY = 0, chestX = 0, headX = 0, jawX = 1.2F;
		float armLX = 0, armLY = 0, armLZ = 0, foreLX = 0, foreLY = 0, foreLZ = 0, handLX = 0, handLZ = 0;
		float armRX = 0, armRY = 0, armRZ = 0, foreRX = 0, foreRY = 0, foreRZ = 0, handRX = 0, handRZ = 0;
		float legLX = 0, legLY = 0, legLZ = 0, shinLX = 0, footLX = 0;
		float legRX = 0, legRY = 0, legRZ = 0, shinRX = 0, footRX = 0;
		float torsoSc = 0F, armLSc = 0F, armRSc = sc, legSc = 0F;
		float[] fCurlL = DEFAULT_PCURL.clone();
		float[] fSplayL = DEFAULT_SPLAY.clone();
		float[] tCurlL = DEFAULT_TCURL.clone();
		float[] fCurlR = DEFAULT_PCURL.clone();
		float[] fSplayR = DEFAULT_SPLAY.clone();
		float[] tCurlR = DEFAULT_TCURL.clone();

		float lift = BossPoses.smithLegLift();
		if (tick <= 25) {
			float burst = seg(tick, 1, 9), slam = seg(tick, 14, 22);
			yOff = -8.4F + 2.4F * burst - 0.8F * slam;
			armRX = BossMotion.mix(-162F, -138F, burst);
			armRX = BossMotion.mix(armRX, -66F, slam);
			armRZ = BossMotion.mix(-8F, -22F, slam);
			foreRX = BossMotion.mix(-18F, -34F, slam);
			foreRY = BossMotion.mix(0F, 84F, slam);
			handRZ = BossMotion.mix(0F, -28F, slam);
			handRX = BossMotion.mix(-12F, 10F, slam);
			float grab = seg(tick, 16, 24);
			for (int i = 0; i < 5; i++) {
				fCurlR[i] = BossMotion.mix(-10F, 14F + (i % 3) * 5F, grab);
				tCurlR[i] = BossMotion.mix(18F, 34F + (i % 2) * 8F, grab);
				fSplayR[i] = BossMotion.mix(i == 0 ? -24F : (2 - i) * 9F, i == 0 ? -30F : (2.5F - i) * 11F, grab);
			}
		} else if (tick <= 55) {
			armLSc = sc;
			float pull = seg(tick, 26, 48);
			float lBurst = seg(tick, 26, 35), lSlam = seg(tick, 36, 47);
			yOff = BossMotion.mix(-6.8F, -4.6F, pull);
			torsoX = BossMotion.mix(24F, 34F, pull);
			chestX = 16F;
			if (tick >= 30) {
				torsoSc = sc;
				headX = BossMotion.mix(-12F, -28F, pull);
				jawX = 8F;
			}
			armRX = BossMotion.mix(-66F, -72F, pull);
			armRZ = -22F;
			foreRX = -34F;
			foreRY = 84F;
			handRZ = -28F;
			handRX = 10F;
			for (int i = 0; i < 5; i++) {
				fCurlR[i] = 16F + (i % 3) * 4F;
				tCurlR[i] = 36F + (i % 2) * 8F;
				fSplayR[i] = i == 0 ? -28F : (2.5F - i) * 10F;
			}

			armLX = BossMotion.mix(-162F, -140F, lBurst);
			armLX = BossMotion.mix(armLX, -70F, lSlam);
			armLZ = BossMotion.mix(10F, 24F, lSlam);
			foreLX = BossMotion.mix(-16F, -32F, lSlam);
			foreLY = BossMotion.mix(0F, -84F, lSlam);
			handLZ = BossMotion.mix(0F, 28F, lSlam);
			handLX = BossMotion.mix(-10F, 10F, lSlam);
			float lGrab = seg(tick, 39, 49);
			for (int i = 0; i < 5; i++) {
				fCurlL[i] = BossMotion.mix(-10F, 16F + ((4 - i) % 3) * 5F, lGrab);
				tCurlL[i] = BossMotion.mix(18F, 36F + (i % 2) * 8F, lGrab);
				fSplayL[i] = BossMotion.mix(i == 0 ? -24F : (2 - i) * 9F, i == 0 ? -30F : (2.5F - i) * 11F, lGrab);
			}
		} else if (tick <= 96) {
			torsoSc = sc;
			armLSc = sc;
			float heave = seg(tick, 56, 94);
			float stagger = 0.14F * BossMotion.sin(heave * (float) Math.PI * 3F);
			yOff = BossMotion.mix(-4.6F, lift - 0.35F, heave) + stagger;
			if (tick >= 64) legSc = BossPoses.SMITH_LEG_SCALE * sc;
			float drag = seg(tick, 64, 96);
			torsoX = BossMotion.mix(34F, 18F, heave);
			chestX = BossMotion.mix(16F, 8F, heave);
			headX = BossMotion.mix(-28F, -12F, heave);
			jawX = BossMotion.mix(8F, 2F, heave);
			armLX = BossMotion.mix(-70F, -16F, heave);
			armRX = BossMotion.mix(-72F, -16F, heave);
			armLZ = BossMotion.mix(24F, 18F, heave);
			armRZ = BossMotion.mix(-22F, -18F, heave);
			foreLX = BossMotion.mix(-32F, -28F, heave);
			foreRX = BossMotion.mix(-34F, -28F, heave);
			foreLY = BossMotion.mix(-84F, 0F, heave);
			foreRY = BossMotion.mix(84F, 0F, heave);
			handLZ = BossMotion.mix(28F, 0F, heave);
			handRZ = BossMotion.mix(-28F, 0F, heave);
			handLX = BossMotion.mix(10F, 4F, heave);
			handRX = BossMotion.mix(10F, 4F, heave);
			for (int i = 0; i < 5; i++) {
				fCurlL[i] = fCurlR[i] = BossMotion.mix(18F, 52F, heave);
				tCurlL[i] = tCurlR[i] = BossMotion.mix(36F, 68F, heave);
				fSplayL[i] = fSplayR[i] = BossMotion.mix(i == 0 ? -28F : (2.5F - i) * 10F, DEFAULT_SPLAY[i], heave);
			}
			legLX = legRX = BossMotion.mix(-55F, -25F, drag);
			shinLX = shinRX = BossMotion.mix(85F, 45F, drag);
			legLZ = 12F; legRZ = -12F;
			footLX = footRX = -legLX - shinLX;
		} else if (tick <= 114) {
			torsoSc = sc; armLSc = sc; legSc = BossPoses.SMITH_LEG_SCALE * sc;
			float coil = seg(tick, 96, 114);
			float quiver = tick >= 106 ? 1.8F * BossMotion.sin(tick * 2.1F) : 0F;
			yOff = BossMotion.mix(lift - 0.35F, lift - 1.15F, coil);
			torsoX = BossMotion.mix(18F, 34F, coil);
			chestX = BossMotion.mix(8F, 24F, coil) + quiver;
			headX = BossMotion.mix(-12F, 22F, coil);
			jawX = 1.0F;
			armLX = armRX = BossMotion.mix(-16F, -44F, coil);
			armLZ = BossMotion.mix(18F, -10F, coil);
			armRZ = BossMotion.mix(-18F, 10F, coil);
			foreLX = foreRX = BossMotion.mix(-28F, -70F, coil);
			for (int i = 0; i < 5; i++) {
				fCurlL[i] = fCurlR[i] = BossMotion.mix(52F, DEFAULT_PCURL[i], coil);
				tCurlL[i] = tCurlR[i] = BossMotion.mix(68F, DEFAULT_TCURL[i], coil);
				fSplayL[i] = fSplayR[i] = DEFAULT_SPLAY[i];
			}
			legLX = legRX = BossMotion.mix(-25F, -44F, coil);
			shinLX = shinRX = BossMotion.mix(45F, 78F, coil);
			legLZ = BossMotion.mix(12F, 22F, coil);
			legRZ = BossMotion.mix(-12F, -22F, coil);
			footLX = footRX = -legLX - shinLX;
		} else if (tick <= 152) {
			torsoSc = sc; armLSc = sc; legSc = BossPoses.SMITH_LEG_SCALE * sc;
			float snap = seg(tick, 114, 121);
			float tremble = tick >= 121 ? 1.6F * BossMotion.sin(tick * 1.4F) : 0F;
			float over = tick <= 125 ? 1.08F * snap : 1.08F - 0.08F * seg(tick, 125, 134);
			yOff = BossMotion.mix(lift - 1.15F, lift - 0.65F, snap);
			torsoX = BossMotion.mix(34F, -12F, over);
			chestX = BossMotion.mix(24F, -24F, over) + tremble * 0.5F;
			headX = BossMotion.mix(22F, -38F, over) + tremble;
			jawX = BossMotion.mix(1.0F, 28F, snap) + Math.abs(tremble);
			armLX = armRX = BossMotion.mix(-44F, 16F, over);
			armLZ = BossMotion.mix(-10F, 44F, over);
			armRZ = BossMotion.mix(10F, -44F, over);
			foreLX = foreRX = BossMotion.mix(-70F, -68F, over);
			foreLY = 18F * snap; foreRY = -18F * snap;
			foreLZ = 28F * snap; foreRZ = -28F * snap;
			handLX = handRX = 18F * snap;
			// 비명: 손가락 사이를 쫙 펼치고(splay 넓게 + 근위 폄) 끝마디(tip)만 날카롭게 갈고리처럼 구부림
			for (int i = 0; i < 5; i++) {
				fCurlL[i] = fCurlR[i] = BossMotion.mix(DEFAULT_PCURL[i], i == 0 ? -8F : -4F, snap);
				tCurlL[i] = tCurlR[i] = BossMotion.mix(DEFAULT_TCURL[i], (i == 0 ? 50F : 64F) + Math.abs(tremble) * 1.5F, snap);
				fSplayL[i] = fSplayR[i] = BossMotion.mix(DEFAULT_SPLAY[i], i == 0 ? -36F : (2.5F - i) * 14F, snap);
			}
			legLX = legRX = BossMotion.mix(-44F, -28F, snap);
			shinLX = shinRX = BossMotion.mix(78F, 52F, snap);
			legLZ = BossMotion.mix(22F, 28F, snap);
			legRZ = BossMotion.mix(-22F, -28F, snap);
			legLY = 12F * snap; legRY = -12F * snap;
			footLX = footRX = BossMotion.mix(-34F, -24F, snap);
		} else {
			torsoSc = sc; armLSc = sc; legSc = BossPoses.SMITH_LEG_SCALE * sc;
			float rest = seg(tick, 152, 166);
			yOff = BossMotion.mix(lift - 0.65F, lift, rest);
			torsoX = BossMotion.mix(-12F, 0F, rest);
			chestX = BossMotion.mix(-24F, 0F, rest);
			headX = BossMotion.mix(-38F, 0F, rest);
			jawX = BossMotion.mix(28F, 1.2F, rest);
			armLX = armRX = BossMotion.mix(16F, 0F, rest);
			armLZ = BossMotion.mix(44F, 4F, rest);
			armRZ = BossMotion.mix(-44F, -4F, rest);
			foreLX = foreRX = BossMotion.mix(-68F, -8F, rest);
			foreLY = BossMotion.mix(18F, 0F, rest);
			foreRY = BossMotion.mix(-18F, 0F, rest);
			foreLZ = BossMotion.mix(28F, 0F, rest);
			foreRZ = BossMotion.mix(-28F, 0F, rest);
			handLX = handRX = BossMotion.mix(18F, 0F, rest);
			// 비명 종료 후 기본 주먹 상태로 복귀
			for (int i = 0; i < 5; i++) {
				fCurlL[i] = fCurlR[i] = BossMotion.mix(i == 0 ? -8F : -4F, DEFAULT_PCURL[i], rest);
				tCurlL[i] = tCurlR[i] = BossMotion.mix(i == 0 ? 50F : 64F, DEFAULT_TCURL[i], rest);
				fSplayL[i] = fSplayR[i] = BossMotion.mix(i == 0 ? -36F : (2.5F - i) * 14F, DEFAULT_SPLAY[i], rest);
			}
			legLX = legRX = BossMotion.mix(-28F, 0F, rest);
			shinLX = shinRX = BossMotion.mix(52F, 0F, rest);
			legLZ = BossMotion.mix(28F, 0F, rest);
			legRZ = BossMotion.mix(-28F, 0F, rest);
			legLY = BossMotion.mix(12F, 0F, rest);
			legRY = BossMotion.mix(-12F, 0F, rest);
			footLX = footRX = BossMotion.mix(-24F, 0F, rest);
		}

		placeSmithRig(yaw, yOff, torsoSc, armLSc, armRSc, legSc, 0F,
			torsoX, torsoY, chestX, headX, jawX,
			armLX, armLY, armLZ, foreLX, foreLY, foreLZ, handLX, handLZ,
			armRX, armRY, armRZ, foreRX, foreRY, foreRZ, handRX, handRZ,
			legLX, legLY, legLZ, shinLX, footLX,
			legRX, legRY, legRZ, shinRX, footRX,
			0F, fCurlL, fSplayL, tCurlL, fCurlR, fSplayR, tCurlR);
	}

	private static final float[] CLUTCH_PCURL = new float[]{24F, 18F, 16F, 20F, 26F};
	private static final float[] CLUTCH_SPLAY = new float[]{-22F, 14F, 4F, -6F, -16F};
	private static final float[] CLUTCH_TCURL = new float[]{64F, 76F, 80F, 76F, 70F};

	private void posePrePhaseStagger(int t, boolean hasHam) {
		float sc = 1.0F;
		float yaw = modelYaw();
		float lift = BossPoses.smithLegLift();
		float kneel = seg(t, 1, 18);
		float clutch = seg(t, 6, 24);
		float pant = (t >= 16) ? BossMotion.sin(t * 0.45F) : 0F;

		float yOff = lift - 1.22F * kneel;
		float torsoX = 14F * kneel + 2F * pant;
		float chestX = 22F * kneel + 4F * pant;
		float headX = 20F * kneel + 3F * pant;
		float jawX = 1.2F + 10F * (0.5F + 0.5F * pant);

		// 팔꿈치는 바깥으로 두고 전완·손바닥이 가슴 정중앙(심장)을 향하게 하며 끝마디를 구부려 움켜잡음
		float armLX = -26F * clutch;
		float armLY = -40F * clutch;
		float armLZ = 18F * clutch;
		float foreLX = -76F * clutch;
		float foreLY = -15F * clutch;
		float foreLZ = -28F * clutch;
		float handLX = 10F * clutch;
		float handLZ = -10F * clutch;

		float[] fCurlL = new float[5], fSplayL = new float[5], tCurlL = new float[5];
		for (int i = 0; i < 5; i++) {
			fCurlL[i] = BossMotion.mix(DEFAULT_PCURL[i], CLUTCH_PCURL[i], clutch);
			fSplayL[i] = BossMotion.mix(DEFAULT_SPLAY[i], CLUTCH_SPLAY[i], clutch);
			tCurlL[i] = BossMotion.mix(DEFAULT_TCURL[i], CLUTCH_TCURL[i], clutch);
		}

		float armRX = -14F * kneel;
		float armRY = 0F;
		float armRZ = -12F * kneel;
		float foreRX = -18F * kneel;
		float hammerX = -90F + 14F * kneel;

		float legLX = 18F * kneel, shinLX = 74F * kneel, footLX = -28F * kneel;
		float legRX = -62F * kneel, shinRX = 64F * kneel, footRX = -2F * kneel;

		placeSmithRig(yaw, yOff, sc, sc, sc, BossPoses.SMITH_LEG_SCALE * sc, hasHam ? sc : 0F,
			torsoX, 0F, chestX, headX, jawX,
			armLX, armLY, armLZ, foreLX, foreLY, foreLZ, handLX, handLZ,
			armRX, armRY, armRZ, foreRX, 0F, 0F, 0F, 0F,
			legLX, 0F, 10F * kneel, shinLX, footLX,
			legRX, 0F, -14F * kneel, shinRX, footRX,
			hammerX, fCurlL, fSplayL, tCurlL, DEFAULT_PCURL, DEFAULT_SPLAY, DEFAULT_TCURL);
	}

	private void posePhase2Forge(int rawTick) {
		if (!modelMode || pTorso == null) return;
		if (rawTick <= PRE_PHASE_TICKS) { posePrePhaseStagger(rawTick, false); return; }
		int t = rawTick - PRE_PHASE_TICKS;
		float sc = 1.0F;
		float yaw = modelYaw();
		float lift = BossPoses.smithLegLift();
		float drop = seg(t, 1, 16) * (1F - seg(t, 34, 56));
		float reach = seg(t, 4, 20) * (1F - seg(t, 26, 48));
		float raise = seg(t, 22, 42) * (1F - seg(t, 42, 56));
		float hScale = t < 18 ? 0F : t < 26 ? sc * (0.4F + 0.78F * seg(t, 18, 22) - 0.18F * seg(t, 22, 26)) : sc;
		float yOff = lift - 0.45F * drop;
		float torsoX = 24F * drop - 8F * raise;
		float chestX = 16F * drop - 10F * raise;
		float headX = 14F * drop - 18F * raise;
		float jawX = 1.2F + 14F * seg(t, 20, 32) * (1F - seg(t, 40, 54));
		float armRX = -62F * reach - 115F * raise;
		float armRZ = -16F * drop - 22F * raise;
		float foreRX = -44F * reach - 20F * raise;
		float armLX = -52F * drop - 20F * raise;
		float armLZ = 18F * drop + 14F * raise;
		float foreLX = -40F * drop;
		float legX = -22F * drop, shinX = 44F * drop, footX = -22F * drop;
		float hammerX = -90F + 28F * raise;

		placeSmithRig(yaw, yOff, sc, sc, sc, BossPoses.SMITH_LEG_SCALE * sc, hScale,
			torsoX, 0F, chestX, headX, jawX,
			armLX, 0F, armLZ, foreLX, 0F, 0F, 0F,
			armRX, 0F, armRZ, foreRX, 0F, 0F, 0F,
			legX, 0F, 12F * drop, shinX, footX,
			legX, 0F, -12F * drop, shinX, footX,
			hammerX);
	}

	private void posePhase3Awaken(int rawTick) {
		if (!modelMode || pTorso == null) return;
		if (rawTick <= PRE_PHASE_TICKS) { posePrePhaseStagger(rawTick, true); return; }
		int t = rawTick - PRE_PHASE_TICKS;
		float sc = 1.0F;
		float yaw = modelYaw();
		float lift = BossPoses.smithLegLift();
		// 준비과정(무릎 꿇고 가슴 부여잡기)에서 이어져 무릎 꿇은 채 고개를 젖히고 망치를 하늘로 들어 보라 빔 흡수 → 130..158 기상
		float kneel = 1F - seg(t, 130, 158);
		float clutchOut = 1F - seg(t, 1, 16);
		float thrust = seg(t, 12, 26) * (1F - seg(t, 128, 156));
		float coilR = seg(t, 1, 12) * (1F - seg(t, 12, 22));
		float tremor = (t >= 25 && t <= 135) ? 1.5F * BossMotion.sin(t * 1.4F) : 0F;

		float yOff = lift - 1.22F * kneel;
		float torsoX = 14F * clutchOut - 8F * thrust + 10F * coilR;
		float chestX = 22F * clutchOut - 22F * thrust + 8F * coilR + tremor * 0.4F;
		float headX = 20F * clutchOut - 42F * (1F - clutchOut) * kneel + tremor;
		float jawX = 1.2F + 20F * seg(t, 20, 34) * (1F - seg(t, 120, 146));

		float armRX = -14F * clutchOut - 45F * coilR - 148F * thrust;
		float armRY = 0F;
		float armRZ = -12F * clutchOut - 6F * thrust;
		float foreRX = -18F * clutchOut - 35F * coilR - 12F * thrust;
		float hammerX = -90F + 14F * clutchOut + 100F * thrust;

		float armLX = -26F * clutchOut - 42F * (1F - clutchOut) * kneel;
		float armLY = -40F * clutchOut;
		float armLZ = 18F * clutchOut + 20F * (1F - clutchOut) * kneel;
		float foreLX = -76F * clutchOut - 26F * (1F - clutchOut) * kneel;
		float foreLY = -15F * clutchOut;
		float foreLZ = -28F * clutchOut;
		float handLX = 10F * clutchOut;
		float handLZ = -10F * clutchOut;

		float[] fCurlL = new float[5], fSplayL = new float[5], tCurlL = new float[5];
		for (int i = 0; i < 5; i++) {
			fCurlL[i] = BossMotion.mix(DEFAULT_PCURL[i], CLUTCH_PCURL[i], clutchOut);
			fSplayL[i] = BossMotion.mix(DEFAULT_SPLAY[i], CLUTCH_SPLAY[i], clutchOut);
			tCurlL[i] = BossMotion.mix(DEFAULT_TCURL[i], CLUTCH_TCURL[i], clutchOut);
		}

		float legLX = 18F * kneel, shinLX = 74F * kneel, footLX = -28F * kneel;
		float legRX = -62F * kneel, shinRX = 64F * kneel, footRX = -2F * kneel;

		placeSmithRig(yaw, yOff, sc, sc, sc, BossPoses.SMITH_LEG_SCALE * sc, sc,
			torsoX, 0F, chestX, headX, jawX,
			armLX, armLY, armLZ, foreLX, foreLY, foreLZ, handLX, handLZ,
			armRX, armRY, armRZ, foreRX, 0F, 0F, 0F, 0F,
			legLX, 0F, 10F * kneel, shinLX, footLX,
			legRX, 0F, -14F * kneel, shinRX, footRX,
			hammerX, fCurlL, fSplayL, tCurlL, DEFAULT_PCURL, DEFAULT_SPLAY, DEFAULT_TCURL);
	}

	private static float seg(int tick, int a, int b) {
		return BossMotion.ease((tick - a) / (float) Math.max(1, b - a));
	}

	private static Rigid smithPivot(Rigid root, String part) {
		org.joml.Vector3f p = BossModels.get("abyssal_smith", part).pivot();
		return root.at(p.x, p.y, p.z);
	}

	private static Rigid smithLegChild(Rigid parent, String from, String to) {
		org.joml.Vector3f a = BossModels.get("abyssal_smith", from).pivot();
		org.joml.Vector3f b = BossModels.get("abyssal_smith", to).pivot();
		float s = BossPoses.SMITH_LEG_SCALE;
		return parent.at((b.x - a.x) * s, (b.y - a.y) * s, (b.z - a.z) * s);
	}

	private static final float[] DEFAULT_PCURL = new float[]{58F, 76F, 80F, 78F, 72F};
	private static final float[] DEFAULT_SPLAY = new float[]{-10F, 5F, 0F, -3F, -6F};
	private static final float[] DEFAULT_TCURL = new float[]{66F, 84F, 88F, 84F, 78F};

	private void placeSmithRig(float yaw, float yOff, float torsoSc, float armLSc, float armRSc, float legSc, float hammerSc,
	                           float torsoX, float torsoY, float chestX, float headX, float jawX,
	                           float armLX, float armLY, float armLZ, float foreLX, float foreLY, float foreLZ, float handLX,
	                           float armRX, float armRY, float armRZ, float foreRX, float foreRY, float foreRZ, float handRX,
	                           float legLX, float legLY, float legLZ, float shinLX, float footLX,
	                           float legRX, float legRY, float legRZ, float shinRX, float footRX,
	                           float hammerX) {
		placeSmithRig(yaw, yOff, torsoSc, armLSc, armRSc, legSc, hammerSc,
			torsoX, torsoY, chestX, headX, jawX,
			armLX, armLY, armLZ, foreLX, foreLY, foreLZ, handLX, 0F,
			armRX, armRY, armRZ, foreRX, foreRY, foreRZ, handRX, 0F,
			legLX, legLY, legLZ, shinLX, footLX,
			legRX, legRY, legRZ, shinRX, footRX,
			hammerX, DEFAULT_PCURL, DEFAULT_SPLAY, DEFAULT_TCURL, DEFAULT_PCURL, DEFAULT_SPLAY, DEFAULT_TCURL);
	}

	private void placeSmithRig(float yaw, float yOff, float torsoSc, float armLSc, float armRSc, float legSc, float hammerSc,
	                           float torsoX, float torsoY, float chestX, float headX, float jawX,
	                           float armLX, float armLY, float armLZ, float foreLX, float foreLY, float foreLZ, float handLX, float handLZ,
	                           float armRX, float armRY, float armRZ, float foreRX, float foreRY, float foreRZ, float handRX, float handRZ,
	                           float legLX, float legLY, float legLZ, float shinLX, float footLX,
	                           float legRX, float legRY, float legRZ, float shinRX, float footRX,
	                           float hammerX, float[] pCurlL, float[] fSplayL, float[] tCurlL, float[] pCurlR, float[] fSplayR, float[] tCurlR) {
		Rigid base = Rigid.yaw(yaw).at(0F, yOff, 0F);
		Rigid torso = smithPivot(base, "torso").rotY(torsoY).rotX(torsoX);
		Rigid chest = child(torso, "torso", "chest").rotX(chestX);
		Rigid head = child(chest, "chest", "head").rotX(headX);
		Rigid jaw = child(head, "head", "jaw").rotX(jawX);

		Rigid armL = child(chest, "chest", "arm_l").rotZ(armLZ).rotY(armLY).rotX(armLX);
		Rigid foreL = child(armL, "arm_l", "fore_l").rotZ(foreLZ).rotY(foreLY).rotX(foreLX);
		Rigid handL = child(foreL, "fore_l", "hand_l").rotZ(handLZ).rotX(handLX);

		Rigid armR = child(chest, "chest", "arm_r").rotZ(armRZ).rotY(armRY).rotX(armRX);
		Rigid foreR = child(armR, "arm_r", "fore_r").rotZ(foreRZ).rotY(foreRY).rotX(foreRX);
		Rigid handR = child(foreR, "fore_r", "hand_r").rotZ(handRZ).rotX(handRX);
		Rigid hammer = child(handR, "hand_r", "hammer").rotX(hammerX);

		Rigid legL = smithPivot(base, "leg_l").rotY(legLY).rotZ(legLZ).rotX(legLX);
		Rigid shinL = smithLegChild(legL, "leg_l", "shin_l").rotX(shinLX);
		Rigid footL = smithLegChild(shinL, "shin_l", "foot_l").rotX(footLX);

		Rigid legR = smithPivot(base, "leg_r").rotY(legRY).rotZ(legRZ).rotX(legRX);
		Rigid shinR = smithLegChild(legR, "leg_r", "shin_r").rotX(shinRX);
		Rigid footR = smithLegChild(shinR, "shin_r", "foot_r").rotX(footRX);

		placeModel("torso", torso, torsoSc);
		placeModel("chest", chest, torsoSc);
		placeModel("head", head, torsoSc);
		placeModel("jaw", jaw, torsoSc);
		placeModel("arm_l", armL, armLSc);
		placeModel("fore_l", foreL, armLSc);
		placeModel("hand_l", handL, armLSc);
		placeSmithFingers(handL, "l", armLSc, pCurlL, fSplayL, tCurlL, false);
		placeModel("arm_r", armR, armRSc);
		placeModel("fore_r", foreR, armRSc);
		placeModel("hand_r", handR, armRSc);
		placeSmithFingers(handR, "r", armRSc, pCurlR, fSplayR, tCurlR, false);
		placeModel("leg_l", legL, legSc);
		placeModel("shin_l", shinL, legSc);
		placeModel("foot_l", footL, legSc);
		placeModel("leg_r", legR, legSc);
		placeModel("shin_r", shinR, legSc);
		placeModel("foot_r", footR, legSc);
		placeModel("hammer", hammer, hammerSc);
	}

	private void placeSmithFingers(Rigid hand, String side, float sc, float[] pCurl, float[] splay, float[] tCurl, boolean death) {
		String handName = "hand_" + side;
		float sign = "l".equals(side) ? -1F : 1F;
		Part[] fArr = "l".equals(side) ? pFingersL : pFingersR;
		Part[] tArr = "l".equals(side) ? pTipsL : pTipsR;
		for (int i = 0; i < 5; i++) {
			String fn = "finger_" + side + (i + 1);
			String tn = "tip_" + side + (i + 1);
			Rigid fg = child(hand, handName, fn).rotZ(sign * pCurl[i]).rotX(splay[i]);
			Rigid tip = child(fg, fn, tn).rotZ(sign * tCurl[i]);
			if (death) {
				placeDeath(fArr[i], fg, sc);
				placeDeath(tArr[i], tip, sc);
			} else {
				placeModel(fn, fg, sc);
				placeModel(tn, tip, sc);
			}
		}
	}

	private void spawnEruptChunks(World w, Vector center, int count, boolean purple) {
		org.bukkit.block.data.BlockData[] mats = purple
			? new org.bukkit.block.data.BlockData[]{
				org.bukkit.Material.AMETHYST_BLOCK.createBlockData(),
				org.bukkit.Material.CRYING_OBSIDIAN.createBlockData(),
				org.bukkit.Material.PURPLE_CONCRETE.createBlockData()}
			: new org.bukkit.block.data.BlockData[]{
				org.bukkit.Material.MAGMA_BLOCK.createBlockData(),
				org.bukkit.Material.DEEPSLATE_BRICKS.createBlockData(),
				org.bukkit.Material.BASALT.createBlockData()};
		double[] vel = new double[count * 3];
		double[] pos = new double[count * 3];
		float[] rot = new float[count * 2];
		List<BlockDisplay> chunks = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			double a = (Math.PI * 2 * i) / count + (Math.random() - 0.5) * 0.35;
			double spd = 0.24 + Math.random() * 0.28;
			vel[i * 3] = Math.cos(a) * spd;
			vel[i * 3 + 1] = 0.38 + Math.random() * 0.32;
			vel[i * 3 + 2] = Math.sin(a) * spd;
			pos[i * 3] = Math.cos(a) * 0.9;
			pos[i * 3 + 1] = 0.2;
			pos[i * 3 + 2] = Math.sin(a) * 0.9;
			rot[i * 2] = 0.22F + (float) Math.random() * 0.18F;
			rot[i * 2 + 1] = 0.42F + (float) Math.random() * 0.35F;
		}
		Fx.spawn(w, mob.getUniqueId(), center, 26, (fx, world) -> {
			if (fx.age == 1) {
				for (int i = 0; i < count; i++) {
					BlockDisplay d = fx.addBlock(mats[i % mats.length], purple || i % 2 == 0);
					float s = rot[i * 2 + 1];
					Fx.shape(d, new Vector3f((float) pos[i * 3], (float) pos[i * 3 + 1], (float) pos[i * 3 + 2]),
						new Quaternionf(), new Vector3f(s, s, s), 1);
					chunks.add(d);
				}
				return;
			}
			for (int i = 0; i < chunks.size(); i++) {
				BlockDisplay d = chunks.get(i);
				if (!d.isValid()) continue;
				vel[i * 3 + 1] -= 0.048;
				pos[i * 3] += vel[i * 3];
				pos[i * 3 + 1] = Math.max(-0.2, pos[i * 3 + 1] + vel[i * 3 + 1]);
				pos[i * 3 + 2] += vel[i * 3 + 2];
				float s = rot[i * 2 + 1] * Math.max(0F, 1F - fx.age / 26F);
				Quaternionf q = new Quaternionf().rotationXYZ(fx.age * rot[i * 2], fx.age * 0.15F, fx.age * rot[i * 2] * 0.7F);
				Fx.shape(d, new Vector3f((float) pos[i * 3] - s * 0.5F, (float) pos[i * 3 + 1] - s * 0.5F, (float) pos[i * 3 + 2] - s * 0.5F),
					q, new Vector3f(s, s, s), 1);
			}
		});
	}

	private void spawnPurpleSkyBeam(World w, Vector base, Vector hitTop) {
		Vector skyOrigin = hitTop.clone().add(new Vector(0, 32.0, 0));
		Fx.beamThick(this, () -> skyOrigin, () -> new Vector(0, -1, 0), 32.0F, 112,
			Material.PURPLE_STAINED_GLASS, 0xB040FF, 2.6F);
	}

	/** v1.3.0: 망치를 머리 위로 들어올리는 예고(붉은 원) 뒤 내려찍기 */
	private void startSlam() {
		slamQueued = false;
		if (Float.isNaN(slamYaw)) slamYaw = facingYaw();
		slamTimer = SLAM_TIME;
		slamStart = tickCount;
		slamAt = null;
		Fx.burst(mob.getWorld(), hammerHead(), kind, 8, 0.45);
		BU.sound(mob.getLocation(), "hobbang:smith.scrape", SoundCategory.HOSTILE, 2.0F, 0.95F);
	}

	private void tickSlam() {
		if (slamTimer <= 0) return;
		slamTimer--;
		World w = mob.getWorld();
		Vector at = slamAt != null ? slamAt : slamPoint();
		at.setY(BU.groundY(w, at.getX(), mob.getLocation().getY() + 1, at.getZ()));
		if (slamAt == null) slamAt = at;
		if (slamTimer > SLAM_HIT) {
			if (slamTimer % 2 == 0) {
				BU.ring(w, Particle.DUST, BU.dust(0xFF2020, 1.6F), slamAt, SLAM_RADIUS, 44);
				Fx.groundMark(w, slamAt, SLAM_RADIUS, (SLAM_TIME - slamTimer) / (double) (SLAM_TIME - SLAM_HIT), kind);
			}
		} else if (slamTimer == SLAM_HIT) {
			if (!posing()) cue(SLAM, 0, 3, SLAM_HIT + 5);
			hammerSlam(slamAt);
		}
		if (slamTimer <= 0) {
			slamAt = null;
			slamYaw = Float.NaN;
		}
	}

	/** 페이즈별 파괴적인 내려찍기 타격음: 전체 볼륨 강화 + 기본 파괴음 + 2페(무거운 망치/모루) / 3페(대지 붕괴·초강력 단조 일격) */
	private void playSlamSound(Location l, int ph) {
		float v = ph >= 3 ? 5.5F : (ph == 2 ? 4.4F : 3.6F);
		BU.sound(l, "hobbang:smith.slam", SoundCategory.HOSTILE, v, ph >= 3 ? 0.58F : (ph == 2 ? 0.80F : 0.96F));
		BU.sound(l, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, v + 0.4F, ph >= 3 ? 0.45F : (ph == 2 ? 0.56F : 0.68F));
		BU.sound(l, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, v - 0.3F, ph >= 3 ? 0.48F : (ph == 2 ? 0.62F : 0.76F));
		BU.sound(l, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, SoundCategory.HOSTILE, v - 0.4F, ph >= 3 ? 0.45F : 0.58F);
		if (ph >= 2) {
			BU.sound(l, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, v, ph >= 3 ? 0.42F : 0.58F);
		}
		if (ph >= 3) {
			BU.sound(l, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 5.2F, 0.72F);
			BU.sound(l, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 4.2F, 0.56F);
			BU.sound(l, Sound.BLOCK_BASALT_BREAK, SoundCategory.HOSTILE, 4.5F, 0.45F);
		}
	}

	private void dealSkillDamage(Player p, double dmg) {
		skillDamage = true;
		try {
			p.damage(dmg, BU.mob(mob));
		} finally {
			skillDamage = false;
		}
	}

	/** 등장(1페)은 거대한 괴물 포효, 2·3페이즈 전환 시에는 각기 다른 결의 포효 재생 */
	private void playPhaseRoar(Location l, int kindRoar) {
		if (kindRoar <= 1) {
			BU.sound(l, "hobbang:smith.roar", SoundCategory.HOSTILE, 2.8F, 0.72F);
			BU.sound(l, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.6F, 0.52F);
			BU.sound(l, Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 2.8F, 0.58F);
			BU.sound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.4F, 0.50F);
			BU.sound(l, Sound.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 2.0F, 0.48F);
		} else if (kindRoar == 2) {
			BU.sound(l, "hobbang:smith.roar", SoundCategory.HOSTILE, 1.9F, 1.04F);
			BU.sound(l, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.6F, 0.78F);
			BU.sound(l, Sound.ENTITY_HOGLIN_ANGRY, SoundCategory.HOSTILE, 1.5F, 0.56F);
		} else {
			BU.sound(l, "hobbang:smith.roar", SoundCategory.HOSTILE, 2.1F, 0.86F);
			BU.sound(l, Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1.8F, 0.75F);
			BU.sound(l, Sound.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 1.5F, 0.62F);
		}
	}

	private void hammerSlam(Vector c) {
		Location l = new Location(mob.getWorld(), c.getX(), c.getY(), c.getZ());
		World w = mob.getWorld();
		playSlamSound(l, phase);
		w.spawnParticle(Particle.EXPLOSION, c.getX(), c.getY() + 0.3, c.getZ(), 8, 3.0, 0.2, 3.0, 0.0);
		w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c.getX(), c.getY() + 0.3, c.getZ(), 30, 3.0, 0.1, 3.0, 0.02);
		Fx.slam(this, c, SLAM_RADIUS);
		for (Player p : BU.victims(w, BU.box(c, SLAM_RADIUS, 3.0, SLAM_RADIUS))) {
			Location pl = p.getLocation();
			double dx = pl.getX() - c.getX(), dz = pl.getZ() - c.getZ();
			if (dx * dx + dz * dz > SLAM_RADIUS * SLAM_RADIUS) continue;
			dealSkillDamage(p, 10.0);
			Vector push = p.getLocation().toVector().subtract(c);
			if (push.lengthSquared() > 1e-6) push.normalize().multiply(0.8);
			p.setVelocity(p.getVelocity().add(new Vector(push.getX(), 1.0, push.getZ())));
		}
	}

	// ───────── 점프 내려찍기 (3페이즈): 웅크림 → 도약 → 체공 → 가속 낙하 → 착지·내려찍기 → 숨 고르기 ─────────
	private static final int ST_CROUCH = 1, ST_RISE = 2, ST_HANG = 3, ST_FALL = 4, ST_SMASH = 5, ST_RECOVER = 6;
	/** 단계별 틱 수. 착지 즉시 딜레이 없이 내려찍기 시작 → LEAP_HIT(6틱=0.3초) 안에 충격파, 이후 LEAP_RECOVER 틱 경직 */
	private static final int LEAP_CROUCH = 26, LEAP_RISE = 24, LEAP_HANG = 3, LEAP_FALL_MAX = 40, LEAP_HIT = 6, LEAP_RECOVER = 26;
	private static final int LEAP_MAX = 200;   // 패턴 전체 안전 타임아웃
	private static final double LEAP_HEIGHT = 14.0, LEAP_RADIUS = 15.0, LEAP_DAMAGE = 40.0;
	private static final int LEAP_RING_TICKS = 14;   // 충격파가 반경 끝까지 퍼지는 틱 (Fx.slam 의 땅 요동과 같은 속도)
	private int leapStage, leapTick, leapStart;
	private float leapYaw;
	private double leapPeak;
	private Vector leapPin, leapTo;
	private boolean leapRingOn;
	private int leapRingAge;
	private Vector leapRingC;
	private final Set<UUID> leapHit = new HashSet<>();

	private void startLeap() {
		leapStage = ST_CROUCH;
		leapTick = 0;
		leapStart = tickCount;
		leapPin = mob.getLocation().toVector();
		leapTo = leapPin.clone();
		leapYaw = modelYaw();   // t401: 보이는 모델 방향에서 출발 (머리 yaw 로 시작하면 몸이 홱 돈다)
		BossPatterns.warnAll(this, "대지 분쇄! 착지 지점에서 멀리 벗어나세요!");
		BU.sound(mob.getLocation(), "hobbang:smith.scrape", SoundCategory.HOSTILE, 2.0F, 0.8F);
	}

	/** 상태 해제. restore = 무중력 되돌리기 (사망 연출은 startDeath 가 직접 다루므로 false) */
	private void leapReset(boolean restore) {
		leapStage = 0;
		leapTick = 0;
		leapPin = leapTo = null;
		leapRingOn = false;
		if (restore && mob.isValid()) mob.setGravity(true);
	}

	private void tickLeap() {
		tickLeapRing();
		if (leapStage == 0) return;
		World w = mob.getWorld();
		Location cur = mob.getLocation();
		if (!mob.isValid() || !w.isChunkLoaded(cur.getBlockX() >> 4, cur.getBlockZ() >> 4) || tickCount - leapStart > LEAP_MAX) {
			leapReset(true);   // 타임아웃: 중력이 돌아와 그 자리에서 떨어진다
			return;
		}
		mob.setVelocity(new Vector());
		mob.setFallDistance(0);
		mob.getPathfinder().stopPathfinding();
		int k = leapTick;
		switch (leapStage) {
			case ST_CROUCH -> {
				LivingEntity t = mob.getTarget();
				if (k < LEAP_CROUCH - 1 && t != null && t.isValid() && !t.isDead()) {
					Vector spot = landingSpot(t.getLocation().toVector());
					if (spot != null) leapTo = spot;   // 확정 직전까지 타깃을 따라간다
				}
				leapFace();
				leapMark();
				leapHold(leapPin);
				if (k == 0) Fx.burst(w, leapPin.clone().add(new Vector(0, 0.4, 0)), kind, 12, 1.2);
				if (++leapTick >= LEAP_CROUCH) {
					Vector a = leapPin, b = leapTo;
					// 도약 높이: 출발·중간·착지 지점의 천장 여유 중 가장 작은 값까지만
					double base = Math.max(a.getY(), b.getY());
					double h = LEAP_HEIGHT;
					for (int i = 0; i <= 2; i++) {
						double f = i / 2.0;
						double y = a.getY() + (b.getY() - a.getY()) * f;
						h = Math.min(h, ceilingGap(a.getX() + (b.getX() - a.getX()) * f, y, a.getZ() + (b.getZ() - a.getZ()) * f) - (base - y));
					}
					leapPeak = base + Math.max(0, h);
					mob.setGravity(false);
					leapStage = ST_RISE;
					leapTick = 0;
					BU.sound(cur, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2F, 1.5F);
					BU.sound(cur, "hobbang:smith.step", SoundCategory.HOSTILE, 2.0F, 0.6F);
					w.spawnParticle(Particle.EXPLOSION, a.getX(), a.getY() + 0.3, a.getZ(), 4, 1.5, 0.1, 1.5, 0);
					w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, a.getX(), a.getY() + 0.3, a.getZ(), 20, 1.5, 0.1, 1.5, 0.03);
				}
			}
			case ST_RISE -> {
				leapMark();
				double u = (k + 1) / (double) LEAP_RISE;
				double up = 1 - (1 - u) * (1 - u);   // 꼭대기로 갈수록 감속
				double hz = u * u * (3 - 2 * u);
				leapMoveTo(leapPin.getX() + (leapTo.getX() - leapPin.getX()) * hz,
					leapPin.getY() + (leapPeak - leapPin.getY()) * up,
					leapPin.getZ() + (leapTo.getZ() - leapPin.getZ()) * hz);
				w.spawnParticle(Particle.CLOUD, cur.getX(), cur.getY() + 0.3, cur.getZ(), 4, 0.8, 0.1, 0.8, 0.02);
				if (++leapTick >= LEAP_RISE) { leapStage = ST_HANG; leapTick = 0; }
			}
			case ST_HANG -> {
				leapMark();
				leapMoveTo(leapTo.getX(), leapPeak + 0.3 * Math.sin((k + 1) * Math.PI / LEAP_HANG), leapTo.getZ());
				if (++leapTick >= LEAP_HANG) {
					leapStage = ST_FALL;
					leapTick = 0;
					BU.sound(cur, Sound.ITEM_MACE_SMASH_AIR, SoundCategory.HOSTILE, 2.0F, 0.6F);
				}
			}
			case ST_FALL -> {
				leapMark();
				double v = Math.min(4.0, 0.6 + 0.48 * k);   // 틱마다 하강 속도 증가 (바닐라 중력보다 훨씬 빠름)
				double ny = cur.getY() - v;
				if (ny <= leapTo.getY() || k >= LEAP_FALL_MAX) {   // 착지 (타임아웃이면 강제로 지면에 붙인다)
					leapMoveTo(leapTo.getX(), leapTo.getY(), leapTo.getZ());
					mob.setGravity(true);
					leapPin = leapTo.clone();
					leapStage = ST_SMASH;
					leapTick = 0;
					BU.sound(cur, "hobbang:smith.step", SoundCategory.HOSTILE, 2.0F, 0.5F);
					BU.sound(cur, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.5F, 0.6F);
					w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, leapTo.getX(), leapTo.getY() + 0.3, leapTo.getZ(), 24, 1.6, 0.1, 1.6, 0.03);
					Fx.burst(w, leapTo.clone().add(new Vector(0, 0.4, 0)), kind, 12, 1.2);
					return;
				}
				leapMoveTo(leapTo.getX(), ny, leapTo.getZ());
				for (int i = 0; i < 6; i++) {   // 낙하 중 위로 흐르는 바람 + 머리 위 불티
					double a = random.nextDouble() * Math.PI * 2, r = 1.0 + random.nextDouble() * 1.3;
					double py = cur.getY() + 0.5 + random.nextDouble() * mob.getHeight();
					w.spawnParticle(Particle.CLOUD, cur.getX() + Math.cos(a) * r, py, cur.getZ() + Math.sin(a) * r, 0, 0, 1, 0, 0.9, null, true);
				}
				w.spawnParticle(Particle.FLAME, cur.getX(), cur.getY() + mob.getHeight() + 0.5, cur.getZ(), 5, 1.0, 0.4, 1.0, 0.05, null, true);
				leapTick++;
			}
			case ST_SMASH -> {
				leapHold(leapPin);
				if (++leapTick >= LEAP_HIT) { leapImpact(); leapStage = ST_RECOVER; leapTick = 0; }
			}
			case ST_RECOVER -> {
				if (k >= LEAP_EMBED) leapTrack();   // t413: 망치를 박고 버티는 10틱(타격 프레임)이 지나면 천천히 타깃 쪽으로
				leapHold(leapPin);
				if (++leapTick >= LEAP_RECOVER) leapReset(true);
			}
			default -> leapReset(true);
		}
	}

	/** 착지 가능한 지점(지면 + 보스 키만큼 빈 공간)이면 좌표, 아니면 null */
	private Vector landingSpot(Vector p) {
		World w = mob.getWorld();
		int bx = p.getBlockX(), bz = p.getBlockZ();
		if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return null;
		double gy = BU.groundY(w, p.getX(), p.getY() + 1, p.getZ());
		int by = (int) Math.floor(gy + 0.01);
		int need = Math.max(4, (int) Math.ceil(mob.getHeight()));
		for (int h = 0; h < need; h++) if (w.getBlockAt(bx, by + h, bz).isCollidable()) return null;
		return new Vector(p.getX(), gy, p.getZ());
	}

	/** 보스 머리 위 천장까지 빈 블록 수 (최대 18) */
	private double ceilingGap(double x, double y, double z) {
		World w = mob.getWorld();
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return 0;
		double top = y + mob.getHeight();
		int by = (int) Math.floor(top);
		for (int i = 0; i < 18; i++) if (w.getBlockAt(bx, by + i, bz).isCollidable()) return Math.max(0, by + i - top);
		return 18;
	}

	/** 공중 이동: 몸통 3곳 중 하나가 블록에 막히면 수평 이동은 보류하고 높이만 바꾼다 (파츠는 RETAIN_PASSENGERS 로 같이 이동) */
	private void leapMoveTo(double x, double y, double z) {
		World w = mob.getWorld();
		Location cur = mob.getLocation();
		if (leapBlocked(w, x, y, z)) { x = cur.getX(); z = cur.getZ(); }
		mob.teleport(new Location(w, x, y, z, leapYaw, 0), io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
	}

	private boolean leapBlocked(World w, double x, double y, double z) {
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return true;
		double h = mob.getHeight();
		for (double dy : new double[]{0.5, h * 0.5, h - 0.2}) {
			if (w.getBlockAt(bx, (int) Math.floor(y + dy), bz).isCollidable()) return true;
		}
		return false;
	}

	/** 지상 고정 (웅크림·착지·경직): 바닐라 AI 가 밀어내도 제자리 + yaw 고정 */
	private void leapHold(Vector p) {
		Location cur = mob.getLocation();
		faceYaw = leapYaw;
		mob.setBodyYaw(leapYaw);
		mob.setRotation(leapYaw, 0);
		if (cur.toVector().distanceSquared(p) > 0.0025) {
			mob.teleport(new Location(mob.getWorld(), p.getX(), p.getY(), p.getZ(), leapYaw, 0), io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
		}
	}

	/** t413: 도약 중(상승·체공·낙하·경직 후반) 방향이 완전히 잠겨 등을 2초가량 보이던 문제 → 타깃 쪽으로 틱당 LEAP_TRACK_TURN° 까지만 천천히 돈다.
	 *  착지~충격파(ST_SMASH)와 박은 채 버티는 경직 초반(LEAP_EMBED 틱)은 그대로 잠근다. */
	private static final float LEAP_TRACK_TURN = 3F;
	private static final int LEAP_EMBED = 10;

	private void leapTrack() {
		LivingEntity t = mob.getTarget();
		if (!isTargetable(t)) return;
		Location l = mob.getLocation(), tl = t.getLocation();
		double dx = tl.getX() - l.getX(), dz = tl.getZ() - l.getZ();
		if (dx * dx + dz * dz < 1.0) return;
		float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
		leapYaw = BossMotion.wrap(leapYaw + BossMotion.clamp(BossMotion.wrap(want - leapYaw), -LEAP_TRACK_TURN, LEAP_TRACK_TURN));
		faceYaw = leapYaw;
	}

	private void leapFace() {
		double dx = leapTo.getX() - leapPin.getX(), dz = leapTo.getZ() - leapPin.getZ();
		if (dx * dx + dz * dz > 1.0) {
			float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float diff = BossMotion.wrap(want - leapYaw);
			leapYaw = BossMotion.wrap(leapYaw + BossMotion.clamp(diff, -FACE_TURN, FACE_TURN));
		}
	}

	/** 착지 예고: 충격파 최대 반경의 붉은 원 (도약~낙하 내내 유지, 시간이 갈수록 안쪽 표시가 수렴) */
	private void leapMark() {
		if (tickCount % 2 != 0) return;
		World w = mob.getWorld();
		Vector c = new Vector(leapTo.getX(), leapTo.getY(), leapTo.getZ());
		BU.ring(w, Particle.DUST, BU.dust(0xFF2020, 1.6F), c, LEAP_RADIUS, 60);
		double total = LEAP_CROUCH + LEAP_RISE + LEAP_HANG + 8;
		Fx.groundMark(w, c, LEAP_RADIUS, Math.min(1, (tickCount - leapStart) / total), kind);
	}

	/** 낙하 스테이지의 포즈 보조 값 — 크게 웅크림 / 착지 때 살짝 굽힘 + 두 손 잡기 */
	private BossPoses.Leap leapPose() {
		float k = leapTick;
		float crouch = switch (leapStage) {
			case ST_CROUCH -> BossMotion.ease(k / 20F);
			case ST_RISE -> 1 - BossMotion.ease(k / 4F);
			case ST_SMASH -> 0.4F * (1 - BossMotion.ease(k / (float) LEAP_HIT));
			default -> 0F;
		};
		float grip = switch (leapStage) {
			case ST_RISE -> BossMotion.ease((k - 3) / 10F);
			case ST_HANG, ST_FALL, ST_SMASH -> 1F;
			case ST_RECOVER -> 1 - BossMotion.ease((k - 10) / 16F);
			default -> 0F;
		};
		return new BossPoses.Leap(crouch, grip);
	}

	/** 망치 프레임: prepare = 머리 위로 치켜듦, release = 바닥 내려찍기 */
	private BossMotion.Frame leapStrike() {
		float k = leapTick;
		return switch (leapStage) {
			case ST_RISE -> new BossMotion.Frame(BossMotion.ease(k / 12F), 0);
			case ST_HANG, ST_FALL -> new BossMotion.Frame(1, 0);
			case ST_SMASH -> {
				float s = BossMotion.ease(k / (float) LEAP_HIT);   // 착지 즉시 내려찍기 시작 → 6틱(0.3초) 안에 임팩트
				yield new BossMotion.Frame(1 - s, s);
			}
			case ST_RECOVER -> new BossMotion.Frame(0, 1 - BossMotion.ease((k - 10) / 16F));   // 10틱 박은 채 버틴 뒤 16틱에 걸쳐 복귀
			default -> BossMotion.Frame.REST;
		};
	}

	/** 망치 머리가 닿은 지점(지면 높이)에서 초거대 충격파 시작 */
	private void leapImpact() {
		World w = mob.getWorld();
		Vector base = mob.getLocation().toVector();
		Vector hh = hammerHead();
		if (!modelMode || hammerRig == null || Math.hypot(hh.getX() - base.getX(), hh.getZ() - base.getZ()) > 9) hh = slamPoint();
		Vector c = new Vector(hh.getX(), BU.groundY(w, hh.getX(), base.getY() + 2, hh.getZ()), hh.getZ());
		Location at = new Location(w, c.getX(), c.getY(), c.getZ());
		forgeSurgeUntil = tickCount + 20;
		playSlamSound(at, Math.max(3, phase));
		BU.sound(at, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 5.2F, 0.70F);
		w.spawnParticle(Particle.EXPLOSION_EMITTER, c.getX(), c.getY() + 0.5, c.getZ(), 3, 3.0, 0.2, 3.0, 0);
		w.spawnParticle(Particle.EXPLOSION, c.getX(), c.getY() + 0.4, c.getZ(), 16, 6.0, 0.3, 6.0, 0);
		w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c.getX(), c.getY() + 0.3, c.getZ(), 70, 6.0, 0.2, 6.0, 0.04, null, true);
		w.spawnParticle(Particle.LAVA, c.getX(), c.getY() + 0.5, c.getZ(), 40, 5.0, 0.3, 5.0, 0);
		w.spawnParticle(Particle.FLAME, c.getX(), c.getY() + 0.4, c.getZ(), 60, 5.0, 0.3, 5.0, 0.25, null, true);
		Fx.slam(this, c, LEAP_RADIUS);
		leapRingOn = true;
		leapRingAge = 0;
		leapRingC = c;
		leapHit.clear();
		shake(c, 0.45);
	}

	/** 화면 흔들림 느낌: 근처 지상 플레이어를 위로 살짝 튕긴다 */
	private void shake(Vector c, double power) {
		for (Player p : BU.victims(mob.getWorld(), BU.box(c, LEAP_RADIUS + 14, 6, LEAP_RADIUS + 14))) {
			if (p.isOnGround()) p.setVelocity(p.getVelocity().add(new Vector((random.nextDouble() - 0.5) * 0.1, power, (random.nextDouble() - 0.5) * 0.1)));
		}
	}

	/** 퍼져 나가는 충격파: 링이 지나가는 순간 지면에 있는 플레이어만 1회 40 피해 (점프로 회피). 플레이어만 판정하므로 수습생·벡스는 제외 */
	private void tickLeapRing() {
		if (!leapRingOn) return;
		World w = mob.getWorld();
		Vector c = leapRingC;
		leapRingAge++;
		double r = LEAP_RADIUS * leapRingAge / LEAP_RING_TICKS;
		int pts = Math.max(12, Math.min(48, (int) (r * 4)));
		Particle.DustOptions pDust = purpleActive ? new Particle.DustOptions(Color.fromRGB(0xB8, 0x4D, 0xFF), 1.5F) : null;
		for (int i = 0; i < pts; i++) {
			double a = Math.PI * 2 * i / pts;
			double px = c.getX() + Math.cos(a) * r, py = c.getY() + 0.25, pz = c.getZ() + Math.sin(a) * r;
			if (pDust != null) {
				w.spawnParticle(Particle.DUST, px, py, pz, 1, 0.04, 0.14, 0.04, 0, pDust);
				if ((i & 1) == 0) w.spawnParticle(Particle.WITCH, px, py, pz, 1, 0, 0.12, 0, 0.02);
			} else {
				w.spawnParticle(Particle.FLAME, px, py, pz, 0, 0, 0.12, 0, 1, null, true);
			}
		}
		if (leapRingAge % 2 == 0 && leapRingAge <= 8) shake(c, 0.2);
		for (Player p : BU.victims(w, BU.box(c, LEAP_RADIUS + 2, 4, LEAP_RADIUS + 2))) {
			Location pl = p.getLocation();
			double dx = pl.getX() - c.getX(), dz = pl.getZ() - c.getZ();
			double d = Math.hypot(dx, dz);
			if (d > r + 0.4 || d < r - 1.8 || leapHit.contains(p.getUniqueId())) continue;   // 링 두께 2.2 > 틱당 이동 ~1.07 이라 건너뛰지 않는다
			double air = pl.getY() - BU.groundY(w, pl.getX(), pl.getY() + 0.5, pl.getZ());
			if (air > 0.7 && !p.isOnGround()) continue;   // 점프로 링을 넘는 중
			leapHit.add(p.getUniqueId());
			dealSkillDamage(p, LEAP_DAMAGE);
			Vector push = new Vector(dx, 0, dz);
			if (push.lengthSquared() > 1e-6) push.normalize().multiply(0.9);
			p.setVelocity(new Vector(push.getX(), 1.25, push.getZ()));
		}
		if (leapRingAge >= LEAP_RING_TICKS) leapRingOn = false;
	}

	private void fireVolley(LivingEntity target) {
		cue(CAST, 0, 0, 12);
		World w = mob.getWorld();
		BU.sound(mob.getLocation(), Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5F, 0.7F);
		BU.sound(mob.getLocation(), "hobbang:smith.ping", SoundCategory.HOSTILE, 0.7F, 1.0F);
		int n = phase == 3 ? 5 : 3;
		Vector m = mouth();
		Fx.burst(w, m, kind, 16, 0.52);
		if (purpleActive) {
			w.spawnParticle(Particle.DUST, m.getX(), m.getY(), m.getZ(), 10, 0.35, 0.35, 0.35, 0,
				new Particle.DustOptions(Color.fromRGB(0xC0, 0x50, 0xFF), 1.6F));
			Fx.puff(w, Particle.REVERSE_PORTAL, m, 9, 0.36, 0.08);
		} else {
			Fx.puff(w, Particle.SOUL_FIRE_FLAME, m, 9, 0.36, 0.07);
		}
		for (int i = 0; i < n; i++) {
			Location tl = target.getLocation();
			Vector dir = new Vector(tl.getX() - m.getX(), tl.getY() + target.getHeight() * 0.5 - m.getY(), tl.getZ() - m.getZ());
			double len = dir.length();
			dir.add(new Vector(tri() * len, 0, tri() * len)).normalize();
			Location at = new Location(w, m.getX(), m.getY(), m.getZ());
			SmallFireball fireball = w.spawn(at, SmallFireball.class, fb -> {
				fb.setShooter(mob);
				fb.setDirection(dir);
			});
			Fx.projectileTrail(this, fireball, 40);
		}
	}

	/** Mojang random.triangle(0, 0.15) */
	private double tri() {
		return 0.15 * (random.nextDouble() - random.nextDouble());
	}

	private void startWave() {
		cue(SWEEP, 0, 6, 12);
		forgeSurgeUntil = tickCount + 18;
		waveActive = true;
		waveRadius = 1.0;
		waveCenter = mob.getLocation().toVector();
		waveHit.clear();
		Fx.burst(mob.getWorld(), waveCenter.clone().add(new Vector(0, 0.35, 0)), kind, 12, 0.7);
		BU.sound(mob.getLocation(), Sound.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 2.0F, 0.5F);
		BU.sound(mob.getLocation(), Sound.BLOCK_LAVA_POP, SoundCategory.HOSTILE, 1.5F, 0.55F);
		BU.say(mob, Component.text("⚠ 용암 파동! 점프해서 피하세요!", NamedTextColor.GOLD));
	}

	private void tickWave() {
		if (!waveActive) return;
		World w = mob.getWorld();
		waveRadius += 0.45;
		int pts = Math.max(10, Math.min(40, (int) (waveRadius * 4)));   // v1.5.1: 최대 90 → 40
		Particle.DustOptions pDust = purpleActive ? new Particle.DustOptions(Color.fromRGB(0xB0, 0x40, 0xFF), 1.6F) : null;
		for (int i = 0; i < pts; i++) {
			double a = (Math.PI * 2 * i) / pts;
			double px = waveCenter.getX() + Math.cos(a) * waveRadius, py = waveCenter.getY() + 0.2, pz = waveCenter.getZ() + Math.sin(a) * waveRadius;
			if (pDust != null) {
				w.spawnParticle(Particle.DUST, px, py, pz, 1, 0, 0.05, 0, 0, pDust);
				if ((i & 1) == 0) w.spawnParticle(Particle.DRAGON_BREATH, px, py, pz, 1, 0, 0.04, 0, 0.01);
			} else {
				w.spawnParticle(Particle.FLAME, px, py, pz, 0, 0, 0.05, 0, 1);
			}
		}
		if (tickCount % 2 == 0) {
			double prog = Math.min(1.0, waveRadius / 14.0);
			float sc = 0.25F + 0.60F * (float) prog;
			int chunks = Math.max(8, (int) (8 + 10 * prog));
			double rotOff = tickCount * 0.23;
			for (int i = 0; i < chunks; i++) {
				double a = rotOff + (Math.PI * 2 * i) / chunks;
				double wx = waveCenter.getX() + Math.cos(a) * waveRadius;
				double wz = waveCenter.getZ() + Math.sin(a) * waveRadius;
				double gy = BU.groundY(w, wx, waveCenter.getY() + 1.5, wz);
				Block gb = w.getBlockAt((int) Math.floor(wx), (int) Math.floor(gy - 0.1), (int) Math.floor(wz));
				org.bukkit.block.data.BlockData bd = gb.getType().isSolid() ? gb.getBlockData() : Material.MAGMA_BLOCK.createBlockData();
				Vector pos = new Vector(wx, gy, wz);
				float h = sc * (0.65F + 0.45F * (float) prog);
				float yaw = (float) -a;
				Fx.spawn(w, uuid, pos, 8, (fx, world) -> {
					if (fx.age == 1) {
						org.bukkit.entity.BlockDisplay d = fx.addBlock(bd, false);
						org.joml.Quaternionf q = new org.joml.Quaternionf().rotationY(yaw).rotateX(0.35F);
						Fx.shape(d, new Vector3f(-sc * 0.5F, -sc * 0.3F, -sc * 0.5F), q, new Vector3f(sc, 0.05F, sc), 0);
					} else if (fx.age == 2 && !fx.displays.isEmpty()) {
						org.joml.Quaternionf q = new org.joml.Quaternionf().rotationY(yaw).rotateX(0.25F);
						Fx.shape(fx.displays.getFirst(), new Vector3f(-sc * 0.5F, 0F, -sc * 0.5F), q, new Vector3f(sc, h, sc), 2);
					} else if (fx.age == 5 && !fx.displays.isEmpty()) {
						org.joml.Quaternionf q = new org.joml.Quaternionf().rotationY(yaw);
						Fx.shape(fx.displays.getFirst(), new Vector3f(-sc * 0.5F, -sc * 0.2F, -sc * 0.5F), q, new Vector3f(sc, 0.02F, sc), 3);
					}
				});
			}
		}
		if (tickCount % 3 == 0) {
			Fx.groundMark(w, waveCenter, waveRadius, 0, kind);
			Fx.puff(w, purpleActive ? Particle.DRAGON_BREATH : Particle.LAVA,
				waveCenter.clone().add(new Vector(0, 0.3, 0)), 3, waveRadius * 0.55, 0.03);
		}
		for (Player p : BU.victims(w, BU.box(waveCenter, waveRadius + 1, 3, waveRadius + 1))) {
			Location pl = p.getLocation();
			double d = Math.hypot(pl.getX() - waveCenter.getX(), pl.getZ() - waveCenter.getZ());
			if (Math.abs(d - waveRadius) < 0.8 && p.isOnGround() && Math.abs(pl.getY() - waveCenter.getY()) < 2.5 && waveHit.add(p.getUniqueId())) {
				dealSkillDamage(p, 8.0);
				p.setFireTicks(Math.max(p.getFireTicks(), 80));
			}
		}
		if (waveRadius > 14) waveActive = false;
	}

	private void tickStrikes() {
		if (strikes.isEmpty()) return;
		World w = mob.getWorld();
		Iterator<double[]> it = strikes.iterator();
		while (it.hasNext()) {
			double[] s = it.next();
			double x = s[0], y = s[1], z = s[2];
			s[3]--;
			w.spawnParticle(Particle.ELECTRIC_SPARK, x, y + 0.1, z, 8, 1.0, 0.05, 1.0, 0.0);
			if (s[3] > 0 && s[3] % 4 == 0) Fx.groundMark(w, new Vector(x, y, z), 2, 1 - s[3] / 34.0, kind);
			if (s[3] <= 0) {
				Vector at = new Vector(x, y + 0.2, z);
				Fx.burst(w, at, kind, 13, 0.78);
				Fx.ripple(this, at, 2, 8);
				it.remove();
				BU.lightning(w, x, y, z);
				for (Player p : BU.victims(w, new org.bukkit.util.BoundingBox(x - 2, y - 2, z - 2, x + 2, y + 3, z + 2))) dealSkillDamage(p, 12.0);
			}
		}
	}

	private void summonApprentices(int n) {
		World w = mob.getWorld();
		Location l = mob.getLocation();
		LivingEntity tgt = mob.getTarget();
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			double rr = 1.5 + mob.getWidth();
			Location at = new Location(w, l.getX() + Math.cos(a) * rr, l.getY(), l.getZ() + Math.sin(a) * rr);
			WitherSkeleton ws = w.spawn(at, WitherSkeleton.class, e -> {
				EntityEquipment eq = e.getEquipment();
				eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
				eq.setItemInMainHandDropChance(0);
				e.customName(Component.text("심연의 수습생", NamedTextColor.DARK_PURPLE));
				e.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.STRING, uuid.toString());
			});
			if (tgt != null) ws.setTarget(tgt);
			w.spawnParticle(Particle.SOUL, at.getX(), at.getY() + 1, at.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
			Fx.ripple(this, at.toVector(), 1.45, 8);
			Fx.puff(w, Particle.SOUL_FIRE_FLAME, at.toVector().add(new Vector(0, 0.7, 0)), 8, 0.42, 0.07);
		}
	}

	private void summonVex(int n) {
		cue(CAST, 0, 0, 14);
		World w = mob.getWorld();
		long existing = w.getNearbyEntitiesByType(Vex.class, mob.getLocation(), 32 + mob.getWidth() / 2, 32 + mob.getHeight() / 2).size();
		if (existing >= 6) return;
		BU.sound(mob.getLocation(), "hobbang:smith.grind", SoundCategory.HOSTILE, 1.5F, 1.0F);
		BU.sound(mob.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.5F, 0.8F);
		Location l = mob.getLocation();
		LivingEntity tgt = mob.getTarget();
		for (int i = 0; i < n; i++) {
			Location at = new Location(w, l.getX() + random.nextInt(3) - 1, l.getY() + mob.getHeight() * 0.6, l.getZ() + random.nextInt(3) - 1);
			Vex v = w.spawn(at, Vex.class, e -> {
				e.setSummoner(mob);
				e.setBound(l.getBlock().getLocation());
				e.setLimitedLifetime(true);
				e.setLimitedLifetimeTicks(20 * (30 + random.nextInt(60)));
				e.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
				e.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.STRING, uuid.toString());
			});
			if (tgt != null) v.setTarget(tgt);
			Fx.puff(w, Particle.REVERSE_PORTAL, at.toVector(), 12, 0.38, 0.08);
			Fx.burst(w, at.toVector(), kind, 9, 0.38);
		}
	}

	// ───────── 피해 ─────────
	@Override
	public void onDamage(EntityDamageEvent e) {
		if (e instanceof EntityDamageByEntityEvent be) {
			Entity d = be.getDamager();
			if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
			if (d instanceof Vex || d instanceof WitherSkeleton) { e.setCancelled(true); return; }   // 아군 피해 무시
		}
		super.onDamage(e);
		if (!e.isCancelled() && !dying && modelMode) flash();
	}

	@Override
	public void onAttack(EntityDamageByEntityEvent e) {
		// 기본 근접 때리기 제거: 가까이 있어도 스킬 피해(skillDamage) 외 일반 평타는 취소
		if (e.getDamager() == mob && !skillDamage) {
			e.setCancelled(true);
			return;
		}
		if (phase >= 2 && e.getEntity() instanceof LivingEntity le && e.getDamager() == mob) {
			le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
		}
	}

	@Override
	protected void dropLoot(List<ItemStack> drops, Random r) {
		drops.add(HItem.ENHANCE_STONE.create(16 + r.nextInt(17)));
		drops.add(HItem.RADIANT_STONE.create(6 + r.nextInt(7)));
		drops.add(HItem.PROTECTION_SCROLL.create(1 + r.nextInt(2)));
		drops.add(HItem.BLESSING_SCROLL.create(1 + r.nextInt(2)));
		drops.add(HItem.LUCK_CHARM.create(2));
		drops.add(HItem.ABYSS_ESSENCE.create(1 + r.nextInt(3)));
		if (r.nextFloat() < 0.25F) drops.add(HItem.SMITH_HAMMER.create());
	}

	@Override
	protected Component deathLine(Component who) {
		return Component.text("⚔ [홉방] ", NamedTextColor.DARK_PURPLE).append(who.colorIfAbsent(NamedTextColor.YELLOW))
			.append(Component.text("님이 심연의 대장장이를 쓰러뜨렸습니다!", NamedTextColor.LIGHT_PURPLE));
	}

	@Override
	protected int deathDuration() { return 185; }

	private float[] physDelay;
	private boolean[] physDetached;

	private boolean isDetached(Part p) {
		if (p == null || physDetached == null) return false;
		int idx = parts.indexOf(p);
		return idx >= 0 && idx < physDetached.length && physDetached[idx];
	}

	private void placeDeath(Part p, Rigid rig, float sc) {
		if (!isDetached(p)) place(p, rig, sc);
	}

	@Override
	protected void onDeathStart() {
		Location at = mob.getLocation();
		Fx.burst(at.getWorld(), hammerHead(), kind, 24, 1.2);
		Fx.puff(at.getWorld(), Particle.SOUL_FIRE_FLAME, at.toVector().add(new Vector(0, 1.3, 0)), 18, 1.2, 0.11);
		BU.sound(mob.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 2.0F, 1.5F);
		BU.sound(mob.getLocation(), "hobbang:smith.slam", SoundCategory.HOSTILE, 2.0F, 1.0F);
	}

	private void setDeathPartOff(Part p, boolean off) {
		if (p == null || p.id == null || !(p.display instanceof org.bukkit.entity.ItemDisplay idsp) || !idsp.isValid()) return;
		String base = p.id.equals("hammer") ? "boss/smith_hammer/hammer" : "boss/abyssal_smith/" + p.id;
		String key = off ? base + "_off" : (purpleActive ? base + "_purple" : base);
		ItemStack st = new ItemStack(Material.PAPER);
		st.editMeta(m -> m.setItemModel(new org.bukkit.NamespacedKey("hobbang", key)));
		idsp.setItemStack(st);
	}

	@Override
	protected void poseDeath(float u) {
		if (!modelMode || pTorso == null) { super.poseDeath(u); return; }
		// 0-0.34 보라색/시안 에너지 빛이 서서히 꺼지며 _off 텍스처로 전환 → 0.36 이후 위에서부터 한 조각씩 낙하
		float fade = u < 0.34F ? Math.max(0F, 1F - u / 0.34F) : 0F;
		int br = Math.max(0, Math.round(15F * fade));
		boolean eyesOff = u >= 0.32F || (u >= 0.12F && (deathTick / 2) % (u < 0.22F ? 3 : 2) == 0);
		boolean bodyOff = u >= 0.28F;
		int pBr = eyesOff ? 0 : br;
		org.bukkit.entity.Display.Brightness dim = new org.bukkit.entity.Display.Brightness(pBr, pBr);
		Color glowFade = Color.fromRGB(Math.round(0xB0 * fade), Math.round(0x40 * fade), Math.round(0xFF * fade));
		for (Part p : parts) {
			if (p != null && p.display.isValid()) {
				p.display.setBrightness(dim);
				if (purpleActive && p.glowPart && u < 0.34F) {
					p.display.setGlowColorOverride(glowFade);
					p.display.setGlowing(true);
				} else if (u >= 0.12F) {
					p.display.setGlowing(false);
				}
			}
		}
		if (eyesOff != deathHeadOff) {
			deathHeadOff = eyesOff;
			setDeathPartOff(pHead, eyesOff);
		}
		if (bodyOff && !deathBodyOff) {
			deathBodyOff = true;
			for (Part p : parts) {
				if (p != pHead) setDeathPartOff(p, true);
			}
		}
		// 0-0.26 무릎 꿇음, 0.16-0.36 상체 숙임+망치 떨굼, 이후 무릎 꿇은 채 위에서부터 차례로 분해
		float kneel = smooth(clamp01(u / 0.26F));
		float slump = smooth(clamp01((u - 0.16F) / 0.20F));
		float hammerDown = smooth(clamp01((u - 0.20F) / 0.16F));
		float sc = 1.0F;
		Rigid root = Rigid.yaw(modelYaw());
		Vector3f tp = pv(kind, "torso");
		float hipDrop = kneel * 1.4F * BossPoses.SMITH_LEG_SCALE;
		Rigid base = root.at(0, BossPoses.smithLegLift() - hipDrop, 0);
		Rigid torso = base.at(tp.x, tp.y, tp.z).rotX(6 * kneel + 10 * slump);
		placeDeath(pTorso, torso, sc);
		Rigid chest = child(torso, "torso", "chest").rotX(4 * kneel + 30 * slump);
		placeDeath(pChest, chest, sc);
		if (!isDetached(pLegL)) poseLeg(base, "_l", pLegL, pShinL, pFootL, 0, kneel, sc);
		if (!isDetached(pLegR)) poseLeg(base, "_r", pLegR, pShinR, pFootR, 0, kneel * 0.92F, sc);
		headRig = child(chest, "chest", "head").rotX(28 * slump);
		placeDeath(pHead, headRig, sc);
		placeDeath(pJaw, child(headRig, "head", "jaw").rotX(5 * slump), sc);
		Rigid armL = child(chest, "chest", "arm_l").rotX(20 * slump).rotZ(-12 * slump);
		placeDeath(pArmL, armL, sc);
		Rigid foreL = child(armL, "arm_l", "fore_l").rotX(-25 * slump);
		placeDeath(pForeL, foreL, sc);
		Rigid handL = child(foreL, "fore_l", "hand_l");
		placeDeath(pHandL, handL, sc);
		placeSmithFingers(handL, "l", sc, DEFAULT_PCURL, DEFAULT_SPLAY, DEFAULT_TCURL, true);
		Rigid armR = child(chest, "chest", "arm_r").rotX(-25 * hammerDown + 15 * slump).rotZ(8 * hammerDown);
		placeDeath(pArmR, armR, sc);
		Rigid foreR = child(armR, "arm_r", "fore_r").rotX(-15 * hammerDown);
		placeDeath(pForeR, foreR, sc);
		Rigid handR = child(foreR, "fore_r", "hand_r");
		placeDeath(pHandR, handR, sc);
		placeSmithFingers(handR, "r", sc, DEFAULT_PCURL, DEFAULT_SPLAY, DEFAULT_TCURL, true);
		hammerRig = child(handR, "hand_r", "hammer").rotX(-90F + 70 * hammerDown);
		placeDeath(pHammer, hammerRig, hasHammer() ? sc : 0F);
	}

	@Override
	protected void deathFx(int tick, float u) {
		if (tick <= 1) { shattered = false; physDetached = null; physDelay = null; }
		Location l = mob.isValid() ? mob.getLocation() : deathAt;
		if (l == null || l.getWorld() == null) return;
		World w = l.getWorld();

		if (u < 0.36F) {
			if (tick % 4 == 0) {
				if (purpleActive) {
					w.spawnParticle(Particle.DUST, l.getX(), l.getY() + 1.2, l.getZ(), 4, 0.4, 0.6, 0.4, 0,
						new Particle.DustOptions(Color.fromRGB(0xB0, 0x40, 0xFF), 1.2F));
					w.spawnParticle(Particle.REVERSE_PORTAL, l.getX(), l.getY() + 1.2, l.getZ(), 2, 0.4, 0.5, 0.4, 0.02);
				} else {
					w.spawnParticle(Particle.SOUL_FIRE_FLAME, l.getX(), l.getY() + 1.2, l.getZ(), 3, 0.4, 0.6, 0.4, 0.01);
				}
			}
			return;
		}

		if (modelMode && !parts.isEmpty()) {
			if (!shattered) initShatterPhysics(l);
			stepShatterPhysics(w, l, u);
		}
		if (tick % 4 == 0) {
			w.spawnParticle(Particle.SMOKE, l.getX(), l.getY() + 0.5, l.getZ(), 6, 0.9, 0.4, 0.9, 0.02);
		}
	}

	private void initShatterPhysics(Location l) {
		shattered = true;
		int n = parts.size();
		physPos = new Vector3f[n];
		physVel = new Vector3f[n];
		physAng = new Vector3f[n];
		physScale = new Vector3f[n];
		physRot = new Quaternionf[n];
		physDelay = new float[n];
		physDetached = new boolean[n];
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Part p = parts.get(i);
			if (p == null || !p.display.isValid() || p.last == null) continue;
			Vector3f s = p.last.getScale();
			if (s.x < 0.01F && s.y < 0.01F && s.z < 0.01F) continue;
			order.add(i);
		}
		order.sort((a, b) -> Float.compare(parts.get(b).last.getTranslation().y, parts.get(a).last.getTranslation().y));
		int total = Math.max(1, order.size() - 1);
		for (int k = 0; k < order.size(); k++) {
			physDelay[order.get(k)] = 0.28F + 0.48F * (k / (float) total);
		}
	}

	private void stepShatterPhysics(World w, Location baseAt, float u) {
		float shrink = u < 0.92F ? 1.0F : Math.max(0.02F, 1.0F - (u - 0.92F) / 0.08F);
		int n = Math.min(parts.size(), physPos.length);
		org.bukkit.entity.Display.Brightness deadBr = new org.bukkit.entity.Display.Brightness(0, 0);
		for (int i = 0; i < n; i++) {
			Part p = parts.get(i);
			if (p == null || !p.display.isValid()) continue;
			if (!physDetached[i]) {
				if (u < physDelay[i] || physDelay[i] <= 0F || p.last == null) continue;
				physDetached[i] = true;
				p.display.setBrightness(deadBr);
				p.display.setGlowing(false);
				p.display.setInterpolationDuration(1);
				Vector3f t = new Vector3f(p.last.getTranslation());
				Vector3f s = new Vector3f(p.last.getScale());
				Quaternionf q = new Quaternionf(p.last.getLeftRotation());
				physPos[i] = t;
				physRot[i] = q;
				physScale[i] = s;
				double rx = t.x + (Math.random() - 0.5) * 0.35;
				double rz = t.z + (Math.random() - 0.5) * 0.35;
				double len = Math.max(0.25, Math.hypot(rx, rz));
				float spd = 0.015F + (float) Math.random() * 0.025F;
				float up = -0.01F;
				physVel[i] = new Vector3f((float) (rx / len) * spd, up, (float) (rz / len) * spd);
				physAng[i] = new Vector3f(
					(float) ((Math.random() - 0.5) * 0.14),
					(float) ((Math.random() - 0.5) * 0.14),
					(float) ((Math.random() - 0.5) * 0.14));
				Location pl = baseAt.clone().add(t.x, t.y + riderOff.y, t.z);
				BU.sound(pl, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, SoundCategory.HOSTILE, 1.2F, 0.55F + (float) Math.random() * 0.2F);
				w.spawnParticle(Particle.BLOCK, pl.getX(), pl.getY(), pl.getZ(), 10, 0.25, 0.25, 0.25, 0.08,
					Material.DEEPSLATE_BRICKS.createBlockData());
			}
			if (physPos[i] == null) continue;
			Vector3f pos = physPos[i];
			Vector3f vel = physVel[i];
			Vector3f ang = physAng[i];
			Quaternionf rot = physRot[i];

			vel.y -= 0.018F;
			vel.x *= 0.985F;
			vel.z *= 0.985F;
			pos.add(vel);
			rot.rotateXYZ(ang.x, ang.y, ang.z);

			double wx = baseAt.getX() + pos.x;
			double wz = baseAt.getZ() + pos.z;
			float floorRel = (float) (BU.groundY(w, wx, baseAt.getY() + 2.5, wz) - baseAt.getY()) - riderOff.y + 0.10F;
			if (pos.y <= floorRel) {
				pos.y = floorRel;
				if (vel.y < -0.04F) {
					vel.y = -vel.y * 0.22F;
					if (vel.lengthSquared() > 0.02F) {
						w.spawnParticle(Particle.BLOCK, wx, baseAt.getY() + riderOff.y + floorRel, wz, 3, 0.12, 0.04, 0.12, 0.04,
							Material.BLACKSTONE.createBlockData());
					}
				} else {
					vel.y = 0F;
				}
				vel.x *= 0.65F;
				vel.z *= 0.65F;
				ang.mul(0.65F);
			}

			Vector3f curScale = new Vector3f(physScale[i]).mul(shrink);
			org.bukkit.util.Transformation tr = new org.bukkit.util.Transformation(
				new Vector3f(pos), new Quaternionf(rot), curScale, new Quaternionf());
			p.last = tr;
			p.display.setTransformation(tr);
			p.display.setInterpolationDelay(0);
		}
	}

	private static float clamp01(float v) { return Math.max(0F, Math.min(1F, v)); }
	private static float smooth(float t) { return t * t * (3 - 2 * t); }
}
