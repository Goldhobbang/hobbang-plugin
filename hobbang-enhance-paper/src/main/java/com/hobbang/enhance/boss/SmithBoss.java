package com.hobbang.enhance.boss;

import static com.hobbang.enhance.boss.BossMotion.Move.*;

import com.hobbang.enhance.Keys;
import com.hobbang.enhance.item.HItem;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
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
	/** 망치 내려찍기: 14틱 중 남은 4틱에 타격 */
	private static final int SLAM_TIME = 14, SLAM_HIT = 4;
	private static final double SLAM_RADIUS = 7.0;
	private int slamTimer;
	/** 내려찍기를 시작한 틱 — 어떤 이유로든 타이머가 제때 안 끝나도 자세가 기울어진 채 굳지 않게 하는 안전장치용 */
	private int slamStart;
	private int forgeSurgeUntil;
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
	}

	@Override
	public void unload() {
		leapReset(true);   // 청크 언로드·서버 종료 시 무중력 상태가 저장되지 않게
		super.unload();
	}

	@Override protected float[] phaseThresholds() { return new float[]{0.66F, 0.33F}; }
	@Override protected BossBar.Color phaseColor(int p) { return p == 1 ? BossBar.Color.PURPLE : BossBar.Color.RED; }
	@Override protected int xpReward() { return 500; }
	@Override protected float legacyScale() { return 1.0F; }
	@Override protected void bossTick(List<Player> near, LivingEntity target) {}

	// t401: 모델 방향을 바닐라 몸통 yaw 에서 떼어낸다. 바닐라 MoveControl 은 경로 노드마다 yaw 를 틱당 최대 90°씩 꺾고,
	// 수습생·벡스에게 밀리거나 대상이 발밑에 들어오면 몸통이 옆으로 홱 돌아간다 (2페이즈부터 두드러짐).
	// 모델은 대상 쪽을 향하되 틱당 FACE_TURN° 까지만 돈다. 내려찍기 도약 중엔 leapYaw 를 따른다.
	private static final float FACE_TURN = 12F;
	private float faceYaw = Float.NaN;

	@Override protected float modelYaw() {
		return Float.isNaN(faceYaw) ? mob.getBodyYaw() : faceYaw;
	}

	private void tickFace() {
		if (assembling()) { faceYaw = Float.NaN; return; }
		if (Float.isNaN(faceYaw)) faceYaw = mob.getBodyYaw();
		float want = faceYaw;
		if (leapStage != 0) want = leapYaw;
		else {
			LivingEntity t = mob.getTarget();
			Location l = mob.getLocation();
			if (isTargetable(t)) {
				Location tl = t.getLocation();
				double dx = tl.getX() - l.getX(), dz = tl.getZ() - l.getZ();
				double near = Math.max(1.5, mob.getWidth() * 0.75);
				// 대상이 몸 아래로 파고들면 방향이 마구 튀므로 그대로 둔다
				if (dx * dx + dz * dz > near * near) want = (float) Math.toDegrees(Math.atan2(-dx, dz));
			} else if (!Double.isNaN(lastX)) {
				double dx = l.getX() - lastX, dz = l.getZ() - lastZ;
				if (dx * dx + dz * dz > 0.0025) want = (float) Math.toDegrees(Math.atan2(-dx, dz));
			}
		}
		float diff = BossMotion.wrap(want - faceYaw);
		faceYaw = BossMotion.wrap(faceYaw + BossMotion.clamp(diff, -FACE_TURN, FACE_TURN));
	}
	@Override protected void onPhaseChange(int newPhase) {}

	// ───────── 모델 (v1.3.0) ─────────
	private Part pTorso, pChest, pLegL, pLegR, pShinL, pShinR, pFootL, pFootR, pHead, pArmL, pArmR, pForeL, pForeR, pHandL, pHandR, pHammer;

	@Override
	protected void buildParts() {
		pTorso = pChest = pLegL = pLegR = pShinL = pShinR = pFootL = pFootR = pHead = pArmL = pArmR = pForeL = pForeR = pHandL = pHandR = pHammer = null;
		if (!modelMode) return;   // 블록 모델 모드: 위더 스켈레톤 자체가 외형
		// v1.5.0 리그 16파트: 골반(torso)/상체(chest)/머리, 팔 = 상박-전완-손, 다리 = 허벅지-정강이-발, 망치는 오른손에
		pTorso = energyModel("torso", false, true); pChest = energyModel("chest", false, true); pHead = model("head", false);
		model("jaw", false);
		pLegL = model("leg_l", false); pShinL = model("shin_l", false); pFootL = model("foot_l", false);
		pLegR = model("leg_r", false); pShinR = model("shin_r", false); pFootR = model("foot_r", false);
		pArmL = model("arm_l", false); pForeL = model("fore_l", false); pHandL = model("hand_l", false);
		pArmR = model("arm_r", false); pForeR = model("fore_r", false); pHandR = model("hand_r", false);
		pHammer = model("hammer", false);
	}

	@Override
	protected void onPartsRebuilt() {
		applyLook();
	}

	@Override
	protected void onAssembleFinished() {
		applyLook();
	}

	/** 모델 모드: 몹 투명 + 투구 대신 같은 방어력 수식어 + 망치는 보이지 않는 모델로 (공격력 유지) */
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
		if (hide && !modelMode) eq.setItemInMainHand(null);
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
	protected void poseParts(float t) {
		boolean leaping = leapStage != 0;
		playFootfall(t * 0.18F, leaping ? 0 : moved, "hobbang:smith.step", 0.70F);
		if (!modelMode || pTorso == null) return;
		aimModel(7.7F, 48, 26, 0.34F);
		walkWeight = BossMotion.mix(walkWeight, leaping ? 0 : Math.min(1, (float) moved * 6), 0.35F);
		BossMotion.Frame strike = leaping ? leapStrike() : slamTimer > 0
			? BossMotion.frame(SLAM_TIME - slamTimer, SLAM_TIME - SLAM_HIT, 0, SLAM_HIT + 6)
			: motion(SLAM);
		BossMotion.Frame follow = leaping ? leapStrike() : slamTimer > 0
			? BossMotion.frame(SLAM_TIME - slamTimer - 1.5F, SLAM_TIME - SLAM_HIT, 0, SLAM_HIT + 6)
			: motion(SLAM, 1.5F);
		BossMotion.Frame cast = motion(CAST).blend(motion(CHANNEL));
		BossMotion.Frame sweep = motion(SWEEP);
		if (invulTicks <= 0 && !assembling() && isTargetable(mob.getTarget())) {
			cast = cast.blend(BossMotion.forecast(fireCd, 1, 9));
			if (phase >= 2) cast = cast.blend(BossMotion.forecast(vexCd, 1, 9));
			if (phase >= 2 && !waveActive) sweep = sweep.blend(BossMotion.forecast(waveCd, 1, 10));
		}
		BossPoses.SmithRig rig = BossPoses.smith(this::placeModel, idle, t, modelYaw(), walkWeight, lookYaw, lookPitch,
			strike, follow, motion(STRIKE), cast, sweep, motion(PHASE), leaping ? leapPose() : BossPoses.Leap.NONE);
		headRig = rig.head();
		mouthRig = rig.mouth();
		hammerRig = rig.hammer();
	}

	/** t391/t392 숨결: 입 양옆 이빨 사이에서 하얀 김이 "푸슈" 하고 얼굴 정확히 양옆(머리 로컬 ±X)으로 짧게 뿜어진다.
	 *  타이밍은 BossPoses.smithJetTick — 턱 벌림(smithBreathOpen)과 같은 시계라 턱이 벌어져 있는 동안만 분사 */
	private static final int BREATH_JET = BossPoses.SMITH_JET_END - BossPoses.SMITH_JET_START;

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

	/** 망치 머리 (월드 좌표). 블록 모델 모드에서는 몸 앞 1.5블록 */
	private Vector hammerHead() {
		if (modelMode && hammerRig != null) return worldPoint(hammerRig, 0F, -2.808F, 0F);   // 망치 머리 중심 (손잡이 피벗 기준)
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
		if (dying) { leapReset(false); tickDeath(); return; }   // startDeath 가 무중력/AI 를 직접 다룬다
		// 안전장치: 내려찍기 자세는 조립(소환) 중이거나 정해진 시간(SLAM_TIME)을 넘기면 즉시 풀어, 몸이 다시 곧게 선다
		if (slamTimer > 0 && (assembling() || tickCount - slamStart > SLAM_TIME + 4)) { slamTimer = 0; slamAt = null; }
		if (leapStage != 0 && assembling()) leapReset(true);
		tickFace();
		tickParts();
		if (assembling()) return;
		EntityEquipment eq = mob.getEquipment();
		if (eq != null && eq.getItemInMainHand().getType().isAir()) eq.setItemInMainHand(hammerItem());
		World w = mob.getWorld();
		Location l = mob.getLocation();
		if (!Double.isNaN(lastX)) moved = Math.hypot(l.getX() - lastX, l.getZ() - lastZ);
		lastX = l.getX(); lastZ = l.getZ();
		tickSlam();
		tickLeap();
		double hp = health() / maxHealth();
		if (phase == 1 && hp <= 0.66) enterPhase(2);
		else if (phase == 2 && hp <= 0.33) enterPhase(3);

		if (invulTicks > 0) {
			invulTicks--;
			double bh = mob.getHeight(), bw = mob.getWidth();
			if (invulTicks % 3 == 0) w.spawnParticle(Particle.SOUL_FIRE_FLAME, l.getX(), l.getY() + bh / 2, l.getZ(), 12, bw * 0.6, bh / 3, bw * 0.6, 0.02);
		}

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

		// 순간이동 (멀어지거나 끼었을 때)
		if (leapStage == 0 && target != null && (l.distance(target.getLocation()) > 20 || inWall()) && tickCount % 40 == 0) {
			w.spawnParticle(Particle.PORTAL, l.getX(), l.getY() + mob.getHeight() / 2, l.getZ(), 40, mob.getWidth() / 2, mob.getHeight() / 3, mob.getWidth() / 2, 0.5);
			Location tl = target.getLocation();
			if (randomTeleport(tl.getX() + random.nextInt(5) - 2, tl.getY(), tl.getZ() + random.nextInt(5) - 2)) {
				BU.sound(mob.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5F, 0.6F);
			}
		}

		if (invulTicks <= 0 && leapStage == 0 && !tickWindup()) pickSkill(target, l);
		tickWave();
		tickStrikes();
	}

	/** 스킬 전 전역 대기: 쿨이 끝난 스킬 하나를 골라 1페 30 / 2페 20 / 3페 12틱 뒤 발동. 대기 중엔 다른 스킬 없음. */
	private Runnable queued;
	private int windup;

	private void queue(Runnable skill) {
		queued = skill;
		windup = phase == 1 ? 30 : phase == 2 ? 20 : 12;
	}

	/** 대기 중이면 true (발동 틱 포함 — 같은 틱에 다른 스킬을 고르지 않는다) */
	private boolean tickWindup() {
		if (queued == null) return false;
		if (--windup <= 0) {
			Runnable r = queued;
			queued = null;
			r.run();
		}
		return true;
	}

	private void pickSkill(LivingEntity target, Location l) {
		double dist = target != null ? l.distance(target.getLocation()) : Double.MAX_VALUE;
		if (slamCd <= 0 && slamTimer <= 0 && dist < 9) {
			queue(() -> { startSlam(); slamCd = slamCooldown(); });
		} else if (fireCd <= 0 && target != null) {
			queue(() -> {
				LivingEntity t = mob.getTarget();
				if (t != null && !t.isDead()) fireVolley(t);
				fireCd = FIRE_CD;
			});
		} else if (phase >= 2 && waveCd <= 0 && !waveActive) {
			queue(() -> { startWave(); waveCd = WAVE_CD; });
		} else if (phase >= 2 && vexCd <= 0) {
			queue(() -> { summonVex(2); vexCd = VEX_CD; });
		} else if (spiralCd <= 0 && dist < 10) {
			queue(() -> {
				BossPatterns.warnAll(this, "화염 나선! 불길의 팔 사이로 피하세요!");
				BossPatterns.spiral(this, phase >= 3 ? 4 : 3, 9, 20, 80, 7, Particle.SOUL_FIRE_FLAME, 0xFF8030);
				spiralCd = SPIRAL_CD;
			});
		} else if (phase >= 2 && meteorCd <= 0) {
			queue(() -> {
				BossPatterns.warnAll(this, "운석 단조! 붉은 원에서 벗어나세요!");
				forgeSurgeUntil = tickCount + 24;
				BossPatterns.meteorsOnPlayers(this, 24, 2.8, 40, 9, Material.MAGMA_BLOCK, true);
				meteorCd = METEOR_CD;
			});
		} else if (phase == 3 && ringCd <= 0 && target != null && dist < 30) {
			queue(() -> {
				if (leapStage == 0) startLeap();
				ringCd = RING_CD;
			});
		} else if (phase == 3 && thunderCd <= 0) {
			queue(() -> {
				cue(CAST, 19, 0, 10);
				for (Player p : nearbyPlayers(32)) {
					if (!BU.valid(p)) continue;
					Location pl = p.getLocation();
					strikes.add(new double[]{pl.getX(), pl.getY(), pl.getZ(), 20});
				}
				thunderCd = THUNDER_CD;
			});
		}
	}

	private boolean inWall() {
		Location e = mob.getEyeLocation();
		return e.getBlock().getType().isOccluding();
	}

	/** Fabric randomTeleport: 지정 좌표 아래로 바닥을 찾아 이동 */
	private boolean randomTeleport(double x, double y, double z) {
		World w = mob.getWorld();
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		for (int dy = 0; dy < 16; dy++) {
			int by = (int) Math.floor(y) - dy;
			Block floor = w.getBlockAt(bx, by - 1, bz);
			if (!floor.isCollidable()) continue;
			int need = Math.max(4, (int) Math.ceil(mob.getHeight()));
			for (int h = 0; h < need; h++) if (w.getBlockAt(bx, by + h, bz).isCollidable()) return false;
			Location to = new Location(w, bx + 0.5, by, bz + 0.5, mob.getLocation().getYaw(), 0);
			// v1.3.0: 모델 파츠(탑승 디스플레이)를 태운 채로 순간이동
			Vector from = mob.getLocation().toVector();
			boolean moved = mob.teleport(to, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
			if (moved) {
				Fx.burst(w, from.add(new Vector(0, 1.1, 0)), kind, 14, 0.9);
				Fx.burst(w, to.toVector().add(new Vector(0, 1.1, 0)), kind, 18, 0.9);
				Fx.ripple(this, to.toVector(), 2.5, 8);
			}
			return moved;
		}
		return false;
	}

	private void enterPhase(int newPhase) {
		setPhase(newPhase);
		forgeSurgeUntil = tickCount + 24;
		invulTicks = 60;
		Location l = mob.getLocation();
		Fx.puff(l.getWorld(), Particle.SOUL_FIRE_FLAME, l.toVector().add(new Vector(0, 1.5, 0)), 10, 0.8, 0.06);
		BU.sound(l, "hobbang:smith.scrape", SoundCategory.HOSTILE, 2.0F, 0.95F);
		BU.sound(l, "hobbang:smith.slam", SoundCategory.HOSTILE, 2.0F, 1.0F);
		mob.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, l.getX(), l.getY() + 1, l.getZ(), 1, 0, 0, 0, 0);
		if (newPhase == 2) {
			bar.color(BossBar.Color.RED);
			BU.say(mob, Component.text("심연의 대장장이: \"제련의 시간이다! 수습생들이여, 불을 지펴라!\"", NamedTextColor.RED, TextDecoration.ITALIC));
			summonApprentices(4);
		} else if (newPhase == 3) {
			bar.color(BossBar.Color.RED);
			bar.addFlag(BossBar.Flag.DARKEN_SCREEN);
			BU.say(mob, Component.text("심연의 대장장이: \"최후의 담금질... 너희 모두 녹여주마!!\"", NamedTextColor.DARK_RED, TextDecoration.BOLD));
			mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
			mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 1, false, false));
		}
	}

	/** v1.3.0: 망치를 머리 위로 들어올리는 예고(붉은 원) 뒤 내려찍기 */
	private void startSlam() {
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
		Vector at = slamPoint();
		at.setY(BU.groundY(w, at.getX(), mob.getLocation().getY() + 1, at.getZ()));
		if (slamTimer > SLAM_HIT) {
			if (slamTimer % 2 == 0) {
				BU.ring(w, Particle.DUST, BU.dust(0xFF2020, 1.6F), at, SLAM_RADIUS, 44);
				Fx.groundMark(w, at, SLAM_RADIUS, (SLAM_TIME - slamTimer) / (double) (SLAM_TIME - SLAM_HIT), kind);
			}
			slamAt = at;
		} else if (slamTimer == SLAM_HIT) {
			cue(SLAM, 0, 0, SLAM_HIT + 6);
			hammerSlam(slamAt != null ? slamAt : at);
		}
	}

	private void hammerSlam(Vector c) {
		Location l = new Location(mob.getWorld(), c.getX(), c.getY(), c.getZ());
		World w = mob.getWorld();
		BU.sound(l, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 2.0F, 0.5F);
		BU.sound(l, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.0F, 0.5F);
		BU.sound(l, "hobbang:smith.slam", SoundCategory.HOSTILE, 2.0F, 1.0F);
		w.spawnParticle(Particle.EXPLOSION, c.getX(), c.getY() + 0.3, c.getZ(), 8, 3.0, 0.2, 3.0, 0.0);
		w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c.getX(), c.getY() + 0.3, c.getZ(), 30, 3.0, 0.1, 3.0, 0.02);
		Fx.slam(this, c, SLAM_RADIUS);
		for (Player p : BU.victims(w, BU.box(c, SLAM_RADIUS, 3.0, SLAM_RADIUS))) {
			Location pl = p.getLocation();
			double dx = pl.getX() - c.getX(), dz = pl.getZ() - c.getZ();
			if (dx * dx + dz * dz > SLAM_RADIUS * SLAM_RADIUS) continue;
			p.damage(10.0, BU.mob(mob));
			Vector push = p.getLocation().toVector().subtract(c);
			if (push.lengthSquared() > 1e-6) push.normalize().multiply(0.8);
			p.setVelocity(p.getVelocity().add(new Vector(push.getX(), 1.0, push.getZ())));
		}
	}

	// ───────── 점프 내려찍기 (3페이즈): 웅크림 → 도약 → 체공 → 가속 낙하 → 착지·내려찍기 → 숨 고르기 ─────────
	private static final int ST_CROUCH = 1, ST_RISE = 2, ST_HANG = 3, ST_FALL = 4, ST_SMASH = 5, ST_RECOVER = 6;
	/** 단계별 틱 수. 착지 후 LEAP_HIT 틱에 충격파(발 닿음 → 3틱 뒤 내려찍기 시작 → 모델이 바닥에 닿는 시점), 이후 LEAP_RECOVER 틱 경직 */
	private static final int LEAP_CROUCH = 15, LEAP_RISE = 14, LEAP_HANG = 7, LEAP_FALL_MAX = 40, LEAP_HIT = 8, LEAP_RECOVER = 20;
	private static final int LEAP_MAX = 150;   // 패턴 전체 안전 타임아웃
	private static final double LEAP_HEIGHT = 9.0, LEAP_RADIUS = 15.0, LEAP_DAMAGE = 40.0;
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
				double v = Math.min(3.2, 0.4 + 0.35 * k);   // 틱마다 하강 속도 증가 (바닐라 중력보다 훨씬 빠름)
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

	/** 보스 머리 위 천장까지 빈 블록 수 (최대 14) */
	private double ceilingGap(double x, double y, double z) {
		World w = mob.getWorld();
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return 0;
		double top = y + mob.getHeight();
		int by = (int) Math.floor(top);
		for (int i = 0; i < 14; i++) if (w.getBlockAt(bx, by + i, bz).isCollidable()) return Math.max(0, by + i - top);
		return 14;
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

	/** 지상 고정 (웅크림·착지·경직): 바닐라 AI 가 밀어내도 제자리 */
	private void leapHold(Vector p) {
		Location cur = mob.getLocation();
		mob.setBodyYaw(leapYaw);
		if (cur.toVector().distanceSquared(p) > 0.0025) {
			mob.teleport(new Location(mob.getWorld(), p.getX(), p.getY(), p.getZ(), leapYaw, 0), io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
		}
	}

	private void leapFace() {
		double dx = leapTo.getX() - leapPin.getX(), dz = leapTo.getZ() - leapPin.getZ();
		if (dx * dx + dz * dz > 1.0) leapYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
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
			case ST_CROUCH -> BossMotion.ease(k / 12F);
			case ST_RISE -> 1 - BossMotion.ease(k / 3F);
			case ST_SMASH -> 0.4F * (1 - BossMotion.ease((k - 3) / 2F));
			default -> 0F;
		};
		float grip = switch (leapStage) {
			case ST_RISE -> BossMotion.ease((k - 3) / 8F);
			case ST_HANG, ST_FALL, ST_SMASH -> 1F;
			case ST_RECOVER -> 1 - BossMotion.ease((k - 8) / 12F);
			default -> 0F;
		};
		return new BossPoses.Leap(crouch, grip);
	}

	/** 망치 프레임: prepare = 머리 위로 치켜듦, release = 바닥 내려찍기 */
	private BossMotion.Frame leapStrike() {
		float k = leapTick;
		return switch (leapStage) {
			case ST_RISE -> new BossMotion.Frame(BossMotion.ease(k / 10F), 0);
			case ST_HANG, ST_FALL -> new BossMotion.Frame(1, 0);
			case ST_SMASH -> {
				float s = BossMotion.ease((k - 3) / 2F);   // 발이 닿고 3틱 뒤부터 2틱 만에 내려찍는다
				yield new BossMotion.Frame(1 - s, s);
			}
			case ST_RECOVER -> new BossMotion.Frame(0, 1 - BossMotion.ease((k - 8) / 12F));   // 8틱 박은 채 버틴 뒤 12틱에 걸쳐 복귀
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
		BU.sound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 3.0F, 0.5F);
		BU.sound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 3.0F, 0.7F);
		BU.sound(at, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 3.0F, 0.5F);
		BU.sound(at, "hobbang:smith.slam", SoundCategory.HOSTILE, 3.0F, 0.6F);
		BU.sound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0F, 0.5F);
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
		for (int i = 0; i < pts; i++) {
			double a = Math.PI * 2 * i / pts;
			w.spawnParticle(Particle.FLAME, c.getX() + Math.cos(a) * r, c.getY() + 0.25, c.getZ() + Math.sin(a) * r, 0, 0, 0.12, 0, 1, null, true);
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
			p.damage(LEAP_DAMAGE, BU.mob(mob));
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
		Fx.puff(w, Particle.SOUL_FIRE_FLAME, m, 9, 0.36, 0.07);
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
		BU.say(mob, Component.text("⚠ 용암 파동! 점프해서 피하세요!", NamedTextColor.GOLD));
	}

	private void tickWave() {
		if (!waveActive) return;
		World w = mob.getWorld();
		waveRadius += 0.45;
		int pts = Math.max(10, Math.min(40, (int) (waveRadius * 4)));   // v1.5.1: 최대 90 → 40
		for (int i = 0; i < pts; i++) {
			double a = (Math.PI * 2 * i) / pts;
			w.spawnParticle(Particle.FLAME, waveCenter.getX() + Math.cos(a) * waveRadius, waveCenter.getY() + 0.2, waveCenter.getZ() + Math.sin(a) * waveRadius,
				0, 0, 0.05, 0, 1);
		}
		if (tickCount % 3 == 0) {
			Fx.groundMark(w, waveCenter, waveRadius, 0, kind);
			Fx.puff(w, Particle.LAVA, waveCenter.clone().add(new Vector(0, 0.3, 0)), 3, waveRadius * 0.55, 0.03);
		}
		for (Player p : BU.victims(w, BU.box(waveCenter, waveRadius + 1, 3, waveRadius + 1))) {
			Location pl = p.getLocation();
			double d = Math.hypot(pl.getX() - waveCenter.getX(), pl.getZ() - waveCenter.getZ());
			if (Math.abs(d - waveRadius) < 0.8 && p.isOnGround() && Math.abs(pl.getY() - waveCenter.getY()) < 2.5 && waveHit.add(p.getUniqueId())) {
				p.damage(8.0, BU.mob(mob));
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
			if (s[3] > 0 && s[3] % 5 == 0) Fx.groundMark(w, new Vector(x, y, z), 2, 1 - s[3] / 20, kind);
			if (s[3] <= 0) {
				Vector at = new Vector(x, y + 0.2, z);
				Fx.burst(w, at, kind, 13, 0.78);
				Fx.ripple(this, at, 2, 8);
				it.remove();
				BU.lightning(w, x, y, z);
				for (Player p : BU.victims(w, new org.bukkit.util.BoundingBox(x - 2, y - 2, z - 2, x + 2, y + 3, z + 2))) p.damage(12.0, BU.mob(mob));
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
		if (e.getDamager() == mob) {
			cue(STRIKE, 0, 0, 11);
			Fx.burst(mob.getWorld(), e.getEntity().getLocation().toVector().add(new Vector(0, 0.9, 0)), kind, 8, 0.38);
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
	protected int deathDuration() { return 100; }

	@Override
	protected void onDeathStart() {
		Location at = mob.getLocation();
		Fx.burst(at.getWorld(), hammerHead(), kind, 24, 1.2);
		Fx.puff(at.getWorld(), Particle.SOUL_FIRE_FLAME, at.toVector().add(new Vector(0, 1.3, 0)), 18, 1.2, 0.11);
		BU.sound(mob.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 2.0F, 1.5F);
		BU.sound(mob.getLocation(), "hobbang:smith.slam", SoundCategory.HOSTILE, 2.0F, 1.0F);
	}

	@Override
	protected void poseDeath(float u) {
		if (!modelMode || pTorso == null) { super.poseDeath(u); return; }
		// 0-0.35 무릎, 0.25-0.55 상체 숙임+고개 숙임, 0.4-0.65 망치가 땅에, 0.65-0.8 숨 고르기, 0.8-1 가라앉으며 사라짐
		float kneel = smooth(clamp01(u / 0.35F));
		float slump = smooth(clamp01((u - 0.25F) / 0.30F));
		float hammerDown = smooth(clamp01((u - 0.40F) / 0.25F));
		float fade = smooth(clamp01((u - 0.80F) / 0.20F));
		float sink = fade * 1.4F;
		float sc = Math.max(0.05F, 1F - fade);
		Rigid root = Rigid.yaw(modelYaw());
		Vector3f tp = pv(kind, "torso");
		// 무릎을 꿇으면 골반이 내려간다 (허벅지 앞으로, 정강이는 뒤로 눕는다)
		float hipDrop = kneel * 1.4F * BossPoses.SMITH_LEG_SCALE;
		Rigid base = root.at(0, BossPoses.smithLegLift() - hipDrop - sink, 0);
		Rigid torso = base.at(tp.x, tp.y, tp.z).rotX(6 * kneel + 10 * slump);
		place(pTorso, torso, sc);
		Rigid chest = child(torso, "torso", "chest").rotX(4 * kneel + 30 * slump);
		place(pChest, chest, sc);
		poseLeg(base, "_l", pLegL, pShinL, pFootL, 0, kneel, sc);
		poseLeg(base, "_r", pLegR, pShinR, pFootR, 0, kneel * 0.92F, sc);
		headRig = child(chest, "chest", "head").rotX(28 * slump);
		place(pHead, headRig, sc);
		placeModel("jaw", child(headRig, "head", "jaw").rotX(5 * slump), sc);
		Rigid armL = child(chest, "chest", "arm_l").rotX(20 * slump).rotZ(-12 * slump);
		place(pArmL, armL, sc);
		Rigid foreL = child(armL, "arm_l", "fore_l").rotX(-25 * slump);
		place(pForeL, foreL, sc);
		place(pHandL, child(foreL, "fore_l", "hand_l"), sc);
		Rigid armR = child(chest, "chest", "arm_r").rotX(-25 * hammerDown + 15 * slump).rotZ(8 * hammerDown);
		place(pArmR, armR, sc);
		Rigid foreR = child(armR, "arm_r", "fore_r").rotX(-15 * hammerDown);
		place(pForeR, foreR, sc);
		Rigid handR = child(foreR, "fore_r", "hand_r");
		place(pHandR, handR, sc);
		// 망치가 손에서 아래로 내려 머리가 바닥에
		hammerRig = child(handR, "hand_r", "hammer").rotX(-90F + 70 * hammerDown);
		place(pHammer, hammerRig, sc);
	}

	@Override
	protected void deathFx(int tick, float u) {
		org.bukkit.Location l = mob.getLocation();
		if (u < 0.8F) {
			if (tick % 5 == 0) l.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, l.getX(), l.getY() + 1.0 - u * 0.4, l.getZ(), 3, 0.4, 0.5, 0.4, 0.01);
		} else if (tick % 3 == 0) {
			l.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, l.getX(), l.getY() + 0.4, l.getZ(), 8, 0.5, 0.4, 0.5, 0.02);
		}
	}

	private static float clamp01(float v) { return Math.max(0F, Math.min(1F, v)); }
	private static float smooth(float t) { return t * t * (3 - 2 * t); }
}
