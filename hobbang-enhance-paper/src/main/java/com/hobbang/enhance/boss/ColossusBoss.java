package com.hobbang.enhance.boss;

import static com.hobbang.enhance.boss.BossMotion.Move.*;

import com.hobbang.enhance.Keys;
import com.hobbang.enhance.item.HItem;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import java.util.function.Supplier;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/** 프리즘 콜로서스 — 강화석 광맥이 깨어난 거대 수정 골렘 (지상형). 히트박스 = 투명 철 골렘 */
public class ColossusBoss extends DisplayBoss {
	private static final int ACT_NONE = 0, ACT_SLAM = 1, ACT_SPIKE = 2, ACT_BEAM = 3, ACT_RAIN = 4;
	private int action = ACT_NONE, actionTimer, actionLength;
	private float walkWeight, orbitAngle;
	private int coreSurgeUntil;
	private static final int SLAM_CD = 160, SPIKE_CD = 240, BEAM_CD = 560, RAIN_CD = 400;
	private static final int PRISM_RING_CD = 280, CROSS_CD = 400, GRAV_CD = 560;
	private float slamCd = SLAM_CD;
	private int spikeCd = SPIKE_CD, beamCd = BEAM_CD, rainCd = RAIN_CD;
	private int prismRingCd = PRISM_RING_CD, crossCd = CROSS_CD, gravCd = GRAV_CD;
	private int swingAnim = 0, attackCd = 0;
	private Vector lockDir = new Vector();
	/** Prism laser: 3.0 s charge, then 5.0 s of continuous fire tracking at 4 m/s. */
	private static final int BEAM_CHARGE = 60;
	private static final int BEAM_FIRE = 100;
	private static final float BEAM_LEN = 22.0F;
	private static final double BEAM_TRACK_PER_TICK = 4.0 / 20.0;
	private double lastX = Double.NaN, lastZ, moved = 0;

	private Part legL, legR, torso, chest, core, head, eyeL, eyeR, crown, armL, armR, fistL, fistR, shL, shR, back1, back2, back3;
	private final Part[] orbit = new Part[3];
	/** v1.5.0 리그 추가 파츠 (리소스팩 모델 모드): 상체, 전완·주먹, 정강이 */
	private Part mChest, mForeL, mForeR, mFistL, mFistR, mShinL, mShinR;
	/** v1.2.0 블록 모델 → v1.3.0 크기 (모델 4.6 → 7.0 블록) */
	private static final float LEGACY = 7.0F / 4.8F;
	private static final double SLAM_R = 6.9;
	private Rigid headRig;

	public ColossusBoss(Mob mob) {
		super(mob, BossKind.PRISM_COLOSSUS);
	}

	@Override protected float[] phaseThresholds() { return new float[]{0.6F, 0.3F}; }

	@Override
	protected BossBar.Color phaseColor(int p) {
		return p == 1 ? BossBar.Color.PINK : p == 2 ? BossBar.Color.PURPLE : BossBar.Color.RED;
	}

	@Override protected boolean glowParts() { return phase >= 3; }
	@Override protected int glowColor() { return phase >= 3 ? 0xFF4060 : 0xC080FF; }
	@Override protected int xpReward() { return 800; }
	@Override protected float legacyScale() { return LEGACY; }

	private static BlockData bd(Material m) {
		return m.createBlockData();
	}

	private BlockData coreBlock() {
		return phase == 1 ? bd(Material.SEA_LANTERN) : phase == 2 ? bd(Material.AMETHYST_BLOCK) : bd(Material.REDSTONE_BLOCK);
	}

	// ───────── 모델 ─────────
	@Override
	protected void buildParts() {
		legL = legR = torso = chest = core = head = eyeL = eyeR = crown = armL = armR = fistL = fistR = shL = shR = back1 = back2 = back3 = null;
		mChest = mForeL = mForeR = mFistL = mFistR = mShinL = mShinR = null;
		if (modelMode) {
			// v1.5.0: 리소스팩 모델 13파츠 (골반/상체/머리, 팔 3마디, 다리 2마디) + 2페이즈 궤도 수정(블록)
			mChest = energyModel("chest", false, true);
			mForeL = energyModel("fore_l", false, true); mForeR = energyModel("fore_r", false, true); mFistL = energyModel("fist_l", false, true); mFistR = energyModel("fist_r", false, true);
			mShinL = model("shin_l", false); mShinR = model("shin_r", false);
			torso = energyModel("torso", false, true); legL = model("leg_l", false); legR = model("leg_r", false);
			head = energyModel("head", false, true); armL = energyModel("arm_l", false, true); armR = energyModel("arm_r", false, true);
			for (int i = 0; i < 3; i++) orbit[i] = block(bd(Material.PURPLE_STAINED_GLASS), -0.35F, -0.35F, -0.35F, 0.7F, 0.7F, 0.7F, true);
			return;
		}
		BlockData deep = bd(Material.DEEPSLATE_TILES), ame = bd(Material.AMETHYST_BLOCK), cluster = bd(Material.AMETHYST_CLUSTER), calcite = bd(Material.CALCITE);
		legL = block(deep, -0.4F, -1.7F, -0.4F, 0.8F, 1.7F, 0.8F, false);
		legR = block(deep, -0.4F, -1.7F, -0.4F, 0.8F, 1.7F, 0.8F, false);
		torso = block(ame, -1.0F, 0F, -0.65F, 2.0F, 1.7F, 1.3F, false);
		chest = block(calcite, -0.7F, 0.3F, 0.62F, 1.4F, 1.1F, 0.1F, false);
		core = block(coreBlock(), -0.25F, -0.25F, -0.1F, 0.5F, 0.5F, 0.2F, true);
		head = block(calcite, -0.45F, 0F, -0.45F, 0.9F, 0.8F, 0.9F, false);
		BlockData eye = phase >= 3 ? bd(Material.REDSTONE_BLOCK) : bd(Material.SEA_LANTERN);
		eyeL = block(eye, -0.1F, 0F, 0F, 0.2F, 0.12F, 0.04F, true);
		eyeR = block(eye, -0.1F, 0F, 0F, 0.2F, 0.12F, 0.04F, true);
		crown = block(cluster, -0.4F, 0F, -0.4F, 0.8F, 0.8F, 0.8F, false);
		armL = block(deep, -0.35F, -1.9F, -0.35F, 0.7F, 1.9F, 0.7F, false);
		armR = block(deep, -0.35F, -1.9F, -0.35F, 0.7F, 1.9F, 0.7F, false);
		fistL = block(ame, -0.5F, -2.6F, -0.5F, 1.0F, 0.75F, 1.0F, false);
		fistR = block(ame, -0.5F, -2.6F, -0.5F, 1.0F, 0.75F, 1.0F, false);
		shL = block(cluster, -0.5F, 0F, -0.5F, 1.0F, 1.0F, 1.0F, false);
		shR = block(cluster, -0.5F, 0F, -0.5F, 1.0F, 1.0F, 1.0F, false);
		back1 = block(cluster, -0.6F, 0F, -0.6F, 1.2F, 1.2F, 1.2F, false);
		back2 = block(cluster, -0.5F, 0F, -0.5F, 1.0F, 1.0F, 1.0F, false);
		back3 = block(cluster, -0.7F, 0F, -0.7F, 1.4F, 1.4F, 1.4F, false);
		for (int i = 0; i < 3; i++) orbit[i] = block(bd(Material.PURPLE_STAINED_GLASS), -0.25F, -0.25F, -0.25F, 0.5F, 0.5F, 0.5F, true);
	}

	@Override
	protected void onPartsRebuilt() {
		refreshGlow();
	}

	private static float sin(float f) { return (float) Math.sin(f); }

	private static float wrap(float d) {
		d %= 360F;
		if (d >= 180F) d -= 360F;
		if (d < -180F) d += 360F;
		return d;
	}

	@Override
	protected void poseParts(float t) {
		float step = t * (modelMode ? 0.074F : 0.25F);
		playFootfall(step * (modelMode ? 2.8F : 1), moved, "hobbang:colossus.step", 0.78F);
		if (modelMode) { poseModel(t); return; }
		float walk = (float) Math.min(1.0, moved * 8);
		float sw = sin(t * 0.25F) * 28F * walk;
		float breathe = idle.wave(0.058F, 0) * 0.06F;
		Rigid root = Rigid.yaw(modelYaw());

		float lean = 0, armLX = sw * -0.5F, armRX = sw * 0.5F, armLZ = -8, armRZ = 8;
		if (action == ACT_SLAM) {
			if (actionTimer > 6) { armLX = armRX = -165; lean = -12; } else { armLX = armRX = -25; lean = 18; }
		} else if (action == ACT_SPIKE) {
			armRX = actionTimer > 4 ? -150 : -30; lean = actionTimer > 4 ? -5 : 12;
		} else if (action == ACT_BEAM) {
			float kick = motion(CAST).release();
			armLZ = -75 - 13 * kick; armRZ = 75 + 13 * kick;
			armLX = armRX = -20 - 35 * kick;
		} else if (action == ACT_RAIN) {
			armLX = armRX = -180; armLZ = -20; armRZ = 20;
		}
		BossMotion.Frame awaken = motion(PHASE);
		armLX -= 45 * awaken.prepare() + 65 * awaken.release();
		armRX -= 45 * awaken.prepare() + 65 * awaken.release();
		if (swingAnim > 0) armRX = -110 + (10 - swingAnim) * 10;

		place(legL, root.at(0.55F, 1.7F, 0).rotX(sw));
		place(legR, root.at(-0.55F, 1.7F, 0).rotX(-sw));
		Rigid body = root.at(0, 1.6F + breathe, 0).rotX(lean);
		place(torso, body);
		place(chest, body);
		place(core, body.at(0, 0.85F, 0.72F).rotZ(orbitAngle));
		float headYaw = wrap(mob.getLocation().getYaw() - mob.getBodyYaw());
		Rigid hd = body.at(0, 1.7F, 0).rotY(-Math.max(-60, Math.min(60, headYaw)));
		headRig = root.at(0, 1.6F + sin(t * 0.08F) * 0.04F, 0).rotX(lean)
			.at(0, 1.7F, 0).rotY(-Math.max(-60, Math.min(60, headYaw))).at(0, 0.4F, 0.45F);
		place(head, hd);
		place(eyeL, hd.at(0.2F, 0.4F, 0.45F));
		place(eyeR, hd.at(-0.2F, 0.4F, 0.45F));
		place(crown, hd.at(0, 0.75F, 0).rotY(orbitAngle));
		Rigid aL = body.at(1.3F, 1.45F, 0).rotX(armLX).rotZ(-armLZ);
		Rigid aR = body.at(-1.3F, 1.45F, 0).rotX(armRX).rotZ(-armRZ);
		place(armL, aL); place(fistL, aL);
		place(armR, aR); place(fistR, aR);
		place(shL, body.at(1.3F, 1.5F, 0).rotZ(-15));
		place(shR, body.at(-1.3F, 1.5F, 0).rotZ(15));
		place(back1, body.at(-0.5F, 1.1F, -0.65F).rotX(-35).rotZ(15));
		place(back2, body.at(0.55F, 0.8F, -0.65F).rotX(-30).rotZ(-20));
		place(back3, body.at(0, 1.5F, -0.7F).rotX(-40));
		float orbScale = phase >= 2 ? 1.0F : 0.0F;
		for (int i = 0; i < 3; i++) {
			Rigid o = root.at(0, 2.8F + idle.wave(0.1F, -i) * 0.3F, 0).rotY(orbitAngle + i * 120).at(2.3F, 0, 0).rotX(orbitAngle * 2).rotZ(orbitAngle);
			place(orbit[i], o, orbScale);
		}
	}

	private void poseModel(float t) {
		aimModel(6.05F, 48, 30, 0.29F);
		walkWeight = BossMotion.mix(walkWeight, Math.min(1, (float) moved * 8), 0.33F);
		float age = actionLength - actionTimer;
		BossMotion.Frame slam = action == ACT_SLAM ? BossMotion.frame(age, 20, 0, 6) : motion(SWEEP);
		BossMotion.Frame spike = action == ACT_SPIKE ? BossMotion.frame(age, 20, 0, 4).blend(motion(STRIKE)) : motion(STRIKE);
		BossMotion.Frame beam = (action == ACT_BEAM && actionTimer > BEAM_FIRE ? BossMotion.frame(age, BEAM_CHARGE, 0, 1) : BossMotion.Frame.REST)
			.blend(motion(CAST));
		BossMotion.Frame rain = motion(CHANNEL);
		BossMotion.Frame phasePose = motion(PHASE);
		headRig = BossPoses.colossus(this::placeModel, idle, t, modelYaw(), walkWeight, lookYaw, lookPitch,
			slam, spike, beam, rain, motion(MELEE), phasePose);

		Rigid root = Rigid.yaw(modelYaw());
		float resonance = Math.max(phasePose.strength(), Math.max(beam.strength(), rain.strength()));
		float orbScale = phase >= 2 ? 1.0F : 0.0F;
		for (int i = 0; i < orbit.length; i++) {
			float vibration = sin(t * 0.91F - i * 0.7F) * 0.18F * resonance;
			Rigid o = root.at(0, 4.2F + idle.wave(0.1F, -i) * 0.4F + vibration, 0)
				.rotY(orbitAngle + i * 120).at(3.4F + vibration + 0.46F * resonance, 0, 0)
				.rotX(orbitAngle * 2 + i * 30 + 11 * vibration).rotZ(orbitAngle + 12 * vibration);
			place(orbit[i], o, orbScale);
		}
	}

	@Override
	protected boolean energySurge(String part) {
		int age = actionLength - actionTimer;
		boolean core = part.equals("chest") || part.equals("torso");
		if (motion(PHASE).strength() > 0.28F) return true;
		if (core && tickCount < coreSurgeUntil) return true;
		return switch (action) {
			case ACT_SLAM -> age >= 8 && (part.startsWith("fist") || age < 18 && part.startsWith("fore"));
			case ACT_SPIKE -> age >= 7 && (part.equals("fist_r") || part.equals("fore_r") || age < 18 && part.equals("arm_r"));
			case ACT_BEAM -> part.equals("torso") && age < 25
				|| part.equals("chest") && age >= 12 || part.equals("head") && age >= 38;
			case ACT_RAIN -> part.startsWith("arm") || part.equals("chest");
			default -> false;
		};
	}

	private void chargeAlong(World world, Vector start, Vector end, double progress, int color) {
		if (start == null || end == null) return;
		Vector point = end.clone().subtract(start).multiply(progress).add(start);
		world.spawnParticle(Particle.DUST, point.getX(), point.getY(), point.getZ(), 2, 0.10, 0.10, 0.10, 0,
			BU.dust(color, 1.1F));
	}

	private Vector surfaceCore() {
		Vector chestFront = modelPoint("chest", 0, 0.53F, 0.90F);
		return chestFront != null ? chestFront : mob.getLocation().toVector().add(new Vector(0, 2.4 * vs, 0.72 * vs));
	}

	private void corePulse(boolean inward) {
		Vector center = surfaceCore();
		Fx.visual(this, center, 12, (fx, world) -> {
			if (fx.age % 3 != 0) return;
			double progress = fx.age / 12.0;
			double radius = 0.22 + 1.1 * (inward ? 1 - progress : progress);
			Vector origin = surfaceCore();
			Rigid facing = Rigid.yaw(modelYaw());
			for (int i = 0; i < 6; i++) {
				double angle = i * Math.PI / 3;
				Vector3f offset = new Vector3f((float) (radius * Math.cos(angle)), (float) (radius * Math.sin(angle)), 0)
					.rotate(facing.rot());
				world.spawnParticle(Particle.DUST, origin.getX() + offset.x, origin.getY() + offset.y, origin.getZ() + offset.z,
					1, 0, 0, 0, 0, BU.dust(inward ? 0xBA62EE : 0xF6C7FF, 1.0F));
			}
		});
		coreSurgeUntil = tickCount + 15;
	}

	private void liftShoulderShards() {
		// The targeting markers begin at the same time. Existing shards start falling on age 30.
		Fx.visual(this, surfaceCore(), 39, (fx, world) -> {
			if (fx.age == 1) {
				fx.addBlock(bd(Material.AMETHYST_BLOCK), true);
				fx.addBlock(bd(Material.AMETHYST_BLOCK), true);
			} else if (fx.age % 3 != 0) {
				return;
			}
			float lift = 0.85F * BossMotion.ease(fx.age / 28F)
				- 1.3F * BossMotion.ease((fx.age - 30) / 7F);
			float size = 0.21F * BossMotion.ease(fx.age / 6F) * (1 - BossMotion.ease((fx.age - 33) / 4F));
			for (int i = 0; i < fx.displays.size(); i++) {
				float side = i == 0 ? 1 : -1;
				Vector shoulder = modelPoint(i == 0 ? "arm_l" : "arm_r", 0, 0.53F, -0.44F);
				if (shoulder == null) {
					shoulder = worldPoint(Rigid.yaw(modelYaw()).at(side * 1.2F, 3.05F, -0.38F), 0, 0, 0);
				}
				shoulder.subtract(fx.pos);
				Vector3f offset = new Vector3f((float) shoulder.getX(), (float) shoulder.getY() + lift, (float) shoulder.getZ());
				Quaternionf spin = new Quaternionf().rotateXYZ(0.48F + fx.age * 0.05F, side * fx.age * 0.06F, 0.55F);
				Fx.shape(fx.displays.get(i), offset, spin, new Vector3f(size, size * 1.4F, size), ANIM_INTERVAL);
			}
		});
	}

	/** 레이저 시작점 = 눈 */
	private Vector eye() {
		if (headRig != null) return worldPoint(headRig, 0, 0, 0);
		return mob.getLocation().toVector().add(new Vector(0, 2.5 * vs, 0));
	}

	// ───────── 전투 ─────────
	@Override
	protected void onHurtBy(EntityDamageEvent e, double amount) {
		if (phase >= 2 && e instanceof EntityDamageByEntityEvent be && be.getDamager() instanceof Player p) {
			p.damage(amount * 0.2, BU.thorns(mob));
			Location l = p.getLocation();
			p.getWorld().spawnParticle(Particle.END_ROD, l.getX(), l.getY() + 1, l.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
			Fx.puff(p.getWorld(), Particle.ELECTRIC_SPARK, l.toVector().add(new Vector(0, 1, 0)), 5, 0.31, 0.06);
		}
	}

	@Override
	protected void onPhaseChange(int p) {
		cue(PHASE, 9, 6, 14);
		Fx.burst(mob.getWorld(), eye(), kind, 12, 0.7);
		if (core != null) core.setBlock(coreBlock());
		if (p == 2) {
			BU.say(mob, Component.text("프리즘 콜로서스: \"공명하라, 수정이여... 너희의 강철을 산산이 비추리라!\"", NamedTextColor.LIGHT_PURPLE, TextDecoration.ITALIC));
			BU.say(mob, Component.text("⚠ 2페이즈: 근접 공격 시 피해 20% 반사 / 프리즘 레이저 등장", NamedTextColor.GRAY));
		} else if (p == 3) {
			BU.say(mob, Component.text("프리즘 콜로서스: \"부서진다... 그렇다면 너희도 함께!!\"", NamedTextColor.RED, TextDecoration.BOLD));
			BU.say(mob, Component.text("⚠ 3페이즈: 수정 낙하 - 바닥의 분홍 원을 피하세요!", NamedTextColor.GRAY));
			AttributeInstance speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
			if (speed != null && speed.getModifier(Keys.k("enrage")) == null)
				speed.addModifier(new AttributeModifier(Keys.k("enrage"), 0.3, AttributeModifier.Operation.ADD_SCALAR));
			if (eyeL != null) eyeL.setBlock(bd(Material.REDSTONE_BLOCK));
			if (eyeR != null) eyeR.setBlock(bd(Material.REDSTONE_BLOCK));
		}
		refreshGlow();
	}

	/** Fabric MeleeAttackGoal(1.0, true) 대체: 추격 + 20틱 간격 근접 공격 */
	private void chase(LivingEntity target) {
		if (target == null || action != ACT_NONE) {
			if (action != ACT_NONE) mob.getPathfinder().stopPathfinding();
			return;
		}
		if (tickCount % 2 == 0) mob.lookAt(target);
		if (tickCount % 10 == 0 || !mob.getPathfinder().hasPath()) mob.getPathfinder().moveTo(target, 1.0);
		if (attackCd > 0) attackCd--;
		BoundingBox reach = mob.getBoundingBox().expand(0.83, 0, 0.83);
		if (attackCd <= 0 && reach.overlaps(target.getBoundingBox())) {
			attackCd = 20;
			swingAnim = 10;
			cue(MELEE, 0, 0, 11);
			boolean ok = BU.hurtable(target);
			target.damage(16, BU.mob(mob));
			if (ok) {
				Fx.burst(mob.getWorld(), target.getLocation().toVector().add(new Vector(0, 0.85, 0)), kind, 7, 0.4);
				Vector d = target.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);
				if (d.lengthSquared() > 1e-4) d.normalize();
				target.setVelocity(target.getVelocity().add(new Vector(d.getX() * 1.0, 0.45, d.getZ() * 1.0)));
			}
		}
	}

	@Override
	protected void bossTick(List<Player> near, LivingEntity target) {
		Location here = mob.getLocation();
		if (!Double.isNaN(lastX)) moved = Math.hypot(here.getX() - lastX, here.getZ() - lastZ);
		lastX = here.getX(); lastZ = here.getZ();
		if (swingAnim > 0) swingAnim--;
		if (idle.active()) orbitAngle = (orbitAngle + (phase >= 3 ? 9 : 5) * idle.weight()) % 360;
		float spd = phase >= 3 ? 1.5F : 1.0F;
		slamCd -= spd;
		spikeCd -= 1;
		prismRingCd -= 1;
		if (phase >= 2) { beamCd -= 1; crossCd -= 1; }
		if (phase >= 3) { rainCd -= 1; gravCd -= 1; }

		chase(target);
		if (invulTicks > 0) return;
		if (action != ACT_NONE) {
			tickAction(target);
			return;
		}
		if (target == null) return;
		double dist = mob.getLocation().distance(target.getLocation());
		if (rainCd <= 0 && phase >= 3) { start(ACT_RAIN, 34); rainCd = RAIN_CD; }
		else if (newPatterns(dist)) { /* v1.2.0 신규 패턴 */ }
		else if (beamCd <= 0 && phase >= 2) { start(ACT_BEAM, BEAM_CHARGE + BEAM_FIRE); beamCd = BEAM_CD; BU.sound(here, "hobbang:colossus.beam.charge", SoundCategory.HOSTILE, 2.0F, 1.0F); }
		else if (slamCd <= 0 && dist < 8.5) { start(ACT_SLAM, 26); slamCd = SLAM_CD; BU.sound(here, Sound.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 2F, 0.5F); }
		else if (spikeCd <= 0 && dist < 18) {
			start(ACT_SPIKE, 24); spikeCd = SPIKE_CD;
			Vector d = target.getLocation().toVector().subtract(here.toVector()).setY(0);
			lockDir = d.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : d.normalize();
		}
	}

	/** v1.2.0: 수정 충격파 / 프리즘 십자 광선 / 중력장 */
	private boolean newPatterns(double dist) {
		int[] col = BossPatterns.colors(kind);
		Vector here = mob.getLocation().toVector();
		if (phase >= 3 && gravCd <= 0) {
			BossPatterns.warnAll(this, "수정 중력장! 원 밖으로 달리세요!");
			BossPatterns.vortex(this, here, 9, 70, 12, col[0], col[1]);
			corePulse(true);
			BU.sound(mob.getLocation(), "hobbang:colossus.gravity", SoundCategory.HOSTILE, 1.35F, 1.0F);
			start(ACT_NONE, 0);
			gravCd = GRAV_CD;
			return true;
		}
		if (phase >= 2 && crossCd <= 0) {
			BossPatterns.warnAll(this, "프리즘 십자 광선! 빛줄기 예고선을 피하세요!");
			double a0 = random.nextDouble() * Math.PI;
			BossPatterns.groundBeams(this, here, 4, a0, 15, 30, 26, 9, Material.PINK_STAINED_GLASS, col[0]);
			corePulse(false);
			BU.sound(mob.getLocation(), "hobbang:colossus.cross", SoundCategory.HOSTILE, 1.15F, 1.0F);
			if (phase >= 3) BossPatterns.later(this, 34, () -> BossPatterns.groundBeams(this, here, 4, a0 + Math.PI / 4, 15, 26, 26, 9, Material.LIGHT_BLUE_STAINED_GLASS, col[1]));
			crossCd = CROSS_CD;
			return true;
		}
		if (prismRingCd <= 0 && dist < 14) {
			BossPatterns.shockRing(this, here, 13, 0.5, 8, 18, col[0], col[1]);
			corePulse(false);
			BU.sound(mob.getLocation(), "hobbang:colossus.ring", SoundCategory.HOSTILE, 1.15F, 1.0F);
			prismRingCd = PRISM_RING_CD;
			return true;
		}
		return false;
	}

	private void start(int act, int time) {
		action = act;
		actionTimer = actionLength = time;
		if (act == ACT_RAIN) cue(CHANNEL, 38, 0, 14);
		mob.getPathfinder().stopPathfinding();
	}

	private void tickAction(LivingEntity target) {
		actionTimer--;
		mob.getPathfinder().stopPathfinding();
		World w = mob.getWorld();
		Vector c = mob.getLocation().toVector();
		switch (action) {
			case ACT_SLAM -> {
				if (actionTimer > 6) {
					if (actionTimer % 4 == 0) {
						double progress = (actionLength - actionTimer) / 20.0;
						chargeAlong(w, surfaceCore(), modelPoint("fist_l", 0, -0.35F, 0.36F), progress, 0xE8B3FF);
						chargeAlong(w, surfaceCore(), modelPoint("fist_r", 0, -0.35F, 0.36F), progress, 0xE8B3FF);
					}
					if (actionTimer % 2 == 0) {
						BU.ring(w, Particle.DUST, BU.dust(0xFF2020, 1.5F), c, SLAM_R, 48);
						Fx.groundMark(w, c, SLAM_R, (actionLength - actionTimer) / (double) (actionLength - 6), kind);
					}
				} else if (actionTimer == 6) {
					BU.sound(mob.getLocation(), "hobbang:colossus.slam", SoundCategory.HOSTILE, 2.3F, 1.0F);
					for (String part : new String[]{"fist_l", "fist_r"}) {
						Vector fist = modelPoint(part, 0, -0.42F, 0.28F);
						if (fist != null) Fx.puff(w, Particle.END_ROD, fist, 4, 0.18, 0.04);
					}
					w.spawnParticle(Particle.EXPLOSION, c.getX(), c.getY() + 0.3, c.getZ(), 10, 3.8, 0.2, 3.8, 0);
					w.spawnParticle(Particle.BLOCK, c.getX(), c.getY() + 0.3, c.getZ(), 80, 3.8, 0.3, 3.8, 0.2, bd(Material.AMETHYST_BLOCK));
					Fx.slam(this, c, SLAM_R);
					for (Player p : BU.victims(w, mob.getBoundingBox().expand(SLAM_R, 2, SLAM_R))) {
						Location pl = p.getLocation();
						double dx = pl.getX() - c.getX(), dz = pl.getZ() - c.getZ();
						if (dx * dx + dz * dz < SLAM_R * SLAM_R) BU.hit(p, mob, 14, c, 0.9, 1.0);
					}
				}
			}
			case ACT_SPIKE -> {
				Vector from = c.clone().add(lockDir.clone().multiply(1.0 + mob.getWidth() / 2));
				if (actionTimer > 4) {
					if (actionTimer % 4 == 0) {
						Vector hand = modelPoint("fist_r", 0, -0.38F, 0.30F);
						double progress = (actionLength - actionTimer) / 20.0;
						if (progress <= 0.5) chargeAlong(w, surfaceCore(), hand, progress * 2, 0xD298FC);
						else chargeAlong(w, hand, from.clone().add(new Vector(0, 0.12, 0)), progress * 2 - 1, 0xF2CBFF);
					}
					if (actionTimer % 2 == 0) BU.line(w, Particle.DUST, BU.dust(0xC060FF, 1.2F), from.clone().add(new Vector(0, 0.15, 0)),
						from.clone().add(lockDir.clone().multiply(16)).add(new Vector(0, 0.15, 0)), 0.6);
				} else if (actionTimer == 4) {
					BU.sound(mob.getLocation(), "hobbang:colossus.spike", SoundCategory.HOSTILE, 2.0F, 1.0F);
					cue(STRIKE, 1, 12, 12);
					for (int i = 0; i < 13; i++) {
						Vector p = from.clone().add(lockDir.clone().multiply(i * 1.25));
						spike(w, new Vector(p.getX(), BU.groundY(w, p.getX(), c.getY() + 2, p.getZ()), p.getZ()), i, 1.5F, uuid, mob);
					}
				}
			}
			case ACT_BEAM -> {
				Vector eye = eye();
				if (actionTimer > BEAM_FIRE) {
					if (target != null) {
						Vector d = target.getEyeLocation().toVector().subtract(eye);
						if (d.lengthSquared() > 1e-6) lockDir = d.normalize();
					}
					if (actionTimer > BEAM_FIRE + 6) BU.line(w, Particle.DUST, BU.dust(0x80E0FF, 0.8F), eye, eye.clone().add(lockDir.clone().multiply(22)), 0.8);
					else BU.line(w, Particle.DUST, BU.dust(0xFF3030, 1.0F), eye, eye.clone().add(lockDir.clone().multiply(22)), 0.5);
					if (actionTimer % 4 == 0) {
						int elapsed = actionLength - actionTimer;
						Vector lower = modelPoint("torso", 0, 0.5F, 0.74F);
						Vector chestFront = surfaceCore();
						if (elapsed < 25 && lower != null) chargeAlong(w, lower, chestFront, elapsed / 25.0, 0xD594FF);
						else if (elapsed >= 25) {
							Vector litEye = modelPoint("head", 0, 0.45F, 0.6F);
							chargeAlong(w, chestFront, litEye == null ? eye : litEye, (elapsed - 25) / 35.0, 0x9DEAFF);
						}
					}
					if (actionTimer % 3 == 0) Fx.beamCharge(w, eye, lockDir, actionLength - actionTimer, kind);
					if (core != null) core.display.setGlowing(true);
					else if (head != null) { head.display.setGlowColorOverride(org.bukkit.Color.fromRGB(0x80E0FF)); head.display.setGlowing(true); }
				} else if (actionTimer == BEAM_FIRE) {
					cue(CAST, 0, BEAM_FIRE, 12);
					BU.sound(mob.getLocation(), "hobbang:colossus.beam.fire", SoundCategory.HOSTILE, 2.5F, 1.0F);
					trackingBeam(w, eye, lockDir.clone(), target, BEAM_LEN, BEAM_FIRE);
				}
				if (actionTimer == 0) { refreshGlow(); if (modelMode && head != null && hurtFlash == 0) head.display.setGlowing(false); }
			}
			case ACT_RAIN -> {
				if (actionTimer == 33) {
					liftShoulderShards();
					BU.sound(mob.getLocation(), "hobbang:colossus.rain", SoundCategory.HOSTILE, 1.4F, 1.0F);
					Fx.burst(w, eye().add(new Vector(0, 0.6, 0)), kind, 14, 0.75);
					BU.say(mob, Component.text("⚠ 수정 낙하!", NamedTextColor.LIGHT_PURPLE));
					for (Player p : nearbyPlayers(32)) {
						if (!BU.valid(p)) continue;
						for (int k = 0; k < 3; k++) {
							Vector at = p.getLocation().toVector();
							if (k > 0) at.add(new Vector(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
							fallingShard(w, new Vector(at.getX(), BU.groundY(w, at.getX(), at.getY() + 2, at.getZ()), at.getZ()));
						}
					}
				}
			}
			default -> {}
		}
		if (actionTimer <= 0) action = ACT_NONE;
	}

	/** 땅에서 솟는 수정 가시 (보스 & 수정 대검 공용) */
	public static void spike(World world, Vector at, int delay, float size, UUID owner, LivingEntity source) {
		spike(world, at, delay, size, owner, source, 10);
	}

	public static void spike(World world, Vector at, int delay, float size, UUID owner, LivingEntity source, double dmg) {
		spike(world, at, delay, size, owner, source, dmg, null);
	}

	/** t388: hitOnce != null 이면 한 번의 시전(여러 가시·여러 갈래)에서 같은 대상은 한 번만 맞는다 */
	public static void spike(World world, Vector at, int delay, float size, UUID owner, LivingEntity source, double dmg, java.util.Set<UUID> hitOnce) {
		Fx.spawn(world, owner, at, delay + 30, (fx, w) -> {
			if (fx.age == delay + 1) {
				BlockDisplay d = fx.addBlock(Material.AMETHYST_CLUSTER.createBlockData(), true);
				Fx.shape(d, new Vector3f(-size / 2, -0.2F, -size / 2), new Quaternionf(), new Vector3f(size, 0.05F, size), 1);
			} else if (fx.age == delay + 2) {
				for (var d : fx.displays) Fx.shape(d, new Vector3f(-size / 2, -0.2F, -size / 2), new Quaternionf(), new Vector3f(size, size * 1.6F, size), 3);
				w.spawnParticle(Particle.BLOCK, at.getX(), at.getY() + 0.3, at.getZ(), 12, 0.3, 0.2, 0.3, 0.1, Material.AMETHYST_BLOCK.createBlockData());
				if (BossManager.isBoss(source)) {
					Fx.puff(w, Particle.END_ROD, at.clone().add(new Vector(0, size, 0)), 5, 0.45, 0.13);
					Fx.groundMark(w, at, 1.3, 0.2, BossKind.PRISM_COLOSSUS);
				}
				BU.sound(new Location(w, at.getX(), at.getY(), at.getZ()), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 1.2F, 0.7F);
				for (Entity ent : w.getNearbyEntities(BU.box(at, 1.3, 2.5, 1.3), en -> en instanceof LivingEntity le && !le.isDead() && en != source)) {
					LivingEntity e = (LivingEntity) ent;
					if (hitOnce != null && !(e instanceof Player) && !hitOnce.add(e.getUniqueId())) continue;   // 이미 이번 시전에 맞음
					if (BossManager.isBoss(source)) {
						if (!(e instanceof Player pl) || !BU.valid(pl)) continue;
						e.damage(dmg, BU.mob(source));
					} else if (source instanceof Player pl) {
						if (e instanceof Player) continue;
						com.hobbang.enhance.weapon.WeaponListener.skillDamage(e, pl, dmg);
					}
					e.setVelocity(e.getVelocity().add(new Vector(0, source instanceof Player ? 0.45 : 0.9, 0)));   // 무기 스킬은 띄우는 높이 절반
				}
			} else if (fx.age == delay + 24) {
				for (var d : fx.displays) Fx.shape(d, new Vector3f(0, -0.2F, 0), new Quaternionf(), new Vector3f(0, 0, 0), 5);
			}
		});
	}


	/** Continuous laser that follows the player at 4 m/s (0.2 block/tick) for {@code life} ticks. */
	private void trackingBeam(World world, Vector start, Vector dir0, LivingEntity target, float len, int life) {
		Vector dir = dir0.lengthSquared() > 1e-6 ? dir0.clone().normalize() : new Vector(0, 0, 1);
		double d0 = target != null && target.getWorld() == world ? Math.max(1.0, target.getEyeLocation().toVector().distance(start)) : len;
		Vector aim = start.clone().add(dir.clone().multiply(d0));
		Vector[] cur = {dir.clone()};
		Supplier<Vector> dirSource = () -> cur[0];
		Fx.spawn(world, uuid, start, life, (fx, w) -> {
			if (fx.age > 1 && target != null && target.isValid() && !target.isDead() && target.getWorld() == w) {
				Vector want = target.getEyeLocation().toVector();
				Vector step = want.subtract(aim);
				double dist = step.length();
				if (dist > BEAM_TRACK_PER_TICK) step.multiply(BEAM_TRACK_PER_TICK / dist);
				aim.add(step);
				Vector nd = aim.clone().subtract(start);
				if (nd.lengthSquared() > 1e-6) {
					cur[0] = nd.normalize();
					lockDir = cur[0].clone();
				}
			}
			if (fx.age == 1) Fx.beam(this, () -> start, dirSource, len, life, Material.LIGHT_BLUE_STAINED_GLASS, 0x60E0FF);
			Vector d = cur[0];
			if (fx.age % 5 == 0 && life - fx.age >= Fx.BEAM_RETRACT) {
				Vector end = start.clone().add(d.clone().multiply(len));
				BoundingBox box = BoundingBox.of(start, end).expand(1.5);
				int visibleHits = 0;
				for (Player p : BU.victims(w, box)) {
					Vector contact = p.getLocation().toVector().add(new Vector(0, 0.9, 0));
					if (BU.distToSegment(contact, start, end) < 1.1) {
						p.damage(6, BU.magic(mob));
						p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
						if (visibleHits++ < 2) Fx.beamHit(this, contact);
					}
				}
				double k = random.nextDouble();
				w.spawnParticle(Particle.END_ROD, start.getX() + d.getX() * len * k, start.getY() + d.getY() * len * k,
					start.getZ() + d.getZ() * len * k, 4, 0.2, 0.2, 0.2, 0.02);
			}
			if (fx.age % 20 == 10 && life - fx.age > 20) {
				BU.sound(new Location(w, start.getX(), start.getY(), start.getZ()), "hobbang:colossus.beam.hum", SoundCategory.HOSTILE, 1.15F, 1.0F);
			}
		}).group(Fx.BEAM_GROUP);
	}

	private void beam(World world, Vector start, Vector dir, float len, int life) {
		Fx.spawn(world, uuid, start, life, (fx, w) -> {
			if (fx.age == 1) Fx.beam(this, () -> start, dir, len, life, Material.LIGHT_BLUE_STAINED_GLASS, 0x60E0FF);
			if (fx.age % 5 == 0) {
				Vector end = start.clone().add(dir.clone().multiply(len));
				BoundingBox box = BoundingBox.of(start, end).expand(1.5);
				int visibleHits = 0;
				for (Player p : BU.victims(w, box)) {
					Vector contact = p.getLocation().toVector().add(new Vector(0, 0.9, 0));
					if (BU.distToSegment(contact, start, end) < 1.1) {
						p.damage(6, BU.magic(mob));
						p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
						if (visibleHits++ < 2) Fx.beamHit(this, contact);
					}
				}
				double k = random.nextDouble();
				w.spawnParticle(Particle.END_ROD, start.getX() + dir.getX() * len * k, start.getY() + dir.getY() * len * random.nextDouble(),
					start.getZ() + dir.getZ() * len * random.nextDouble(), 4, 0.2, 0.2, 0.2, 0.02);
			}
		});
	}

	private void fallingShard(World world, Vector at) {
		Fx.spawn(world, uuid, at, 56, (fx, w) -> {
			if (fx.age >= 30 && fx.age < 37 && fx.age % 2 == 0) {
				Vector trail = at.clone().add(new Vector(0, (37 - fx.age) * 1.7, 0));
				Fx.burst(w, trail, kind, 5, 0.52);
			}
			if (fx.age < 30) {
				if (fx.age % 3 == 0) BU.ring(w, Particle.DUST, BU.dust(0xFF60C0, 1.3F), at, 2.2, 18);
				if (fx.age % 6 == 0) Fx.groundMark(w, at, 2.2, fx.age / 30.0, kind);
			} else if (fx.age == 30) {
				BlockDisplay d = fx.addBlock(bd(Material.AMETHYST_BLOCK), true);
				Quaternionf q = new Quaternionf().rotateXYZ(0.6F, 0.8F, 0.3F);
				Fx.shape(d, new Vector3f(-0.6F, 12, -0.6F), q, new Vector3f(1.2F, 1.2F, 1.2F), 0);
			} else if (fx.age == 31) {
				Quaternionf q = new Quaternionf().rotateXYZ(1.6F, 2.0F, 0.9F);
				for (var d : fx.displays) Fx.shape(d, new Vector3f(-0.6F, 0, -0.6F), q, new Vector3f(1.2F, 1.2F, 1.2F), 6);
			} else if (fx.age == 37) {
				Location l = new Location(w, at.getX(), at.getY(), at.getZ());
				BU.sound(l, Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.HOSTILE, 2F, 0.6F);
				w.spawnParticle(Particle.EXPLOSION, at.getX(), at.getY() + 0.5, at.getZ(), 1, 0, 0, 0, 0);
				w.spawnParticle(Particle.BLOCK, at.getX(), at.getY() + 0.5, at.getZ(), 30, 0.6, 0.4, 0.6, 0.2, bd(Material.AMETHYST_BLOCK));
				Fx.burst(w, at.clone().add(new Vector(0, 0.5, 0)), kind, 10, 0.62);
				Fx.ripple(this, at, 2.2, 8);
				for (Player p : BU.victims(w, BU.box(at, 2.2, 2.5, 2.2))) BU.hit(p, mob, 10, at, 0.5, 0.4);
			} else if (fx.age == 48) {
				for (var d : fx.displays) Fx.shape(d, new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(0, 0, 0), 6);
			}
		});
	}

	@Override
	protected void dropLoot(List<ItemStack> drops, Random r) {
		drops.add(HItem.PRISM_CRYSTAL.create(4 + r.nextInt(5)));
		drops.add(HItem.RADIANT_STONE.create(8 + r.nextInt(7)));
		drops.add(HItem.PRISM_SCROLL.create(2 + r.nextInt(2)));
		drops.add(HItem.PROTECTION_SCROLL.create(2));
		if (r.nextFloat() < 0.3F) drops.add(HItem.CRYSTAL_GREATSWORD.create());
	}

	@Override
	protected int deathDuration() { return 100; }

	@Override
	protected void onDeathStart() {
		Fx.burst(mob.getWorld(), eye(), kind, 22, 1.1);
		BU.sound(mob.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.HOSTILE, 2F, 0.6F);
	}

	@Override
	protected void poseDeath(float u) {
		// 파츠가 하나씩 떨어져 구르고, 자수정이 부서지며, 마지막에 사라짐
		Rigid root = Rigid.yaw(modelYaw());
		org.joml.Vector3f ll, lr, tp;
		if (modelMode) {
			ll = pv(kind, "leg_l"); lr = pv(kind, "leg_r"); tp = pv(kind, "torso");
		} else {
			ll = new org.joml.Vector3f(0.8F, 0.9F, 0);
			lr = new org.joml.Vector3f(-0.8F, 0.9F, 0);
			tp = new org.joml.Vector3f(0, 2.2F, 0);
		}
		tumble(legL, root.at(ll.x, ll.y, ll.z), u, 0.00F,  1,  0.6F);
		tumble(legR, root.at(lr.x, lr.y, lr.z), u, 0.08F, -1,  0.5F);
		Rigid body = root.at(tp.x, tp.y, tp.z);
		tumble(torso, body, u, 0.16F, 1, 0.2F);
		if (modelMode) {
			// v1.5.0: 마디마다 따로 떨어진다 (끝마디가 먼저)
			Rigid ch = child(body, "torso", "chest");
			tumble(mChest, ch, u, 0.20F, -1, 0.3F);
			tumble(head, child(ch, "chest", "head"), u, 0.24F, -1, 0.9F);
			Rigid aL = child(ch, "chest", "arm_l"), aR = child(ch, "chest", "arm_r");
			tumble(armL, aL, u, 0.14F, 1, 0.8F);
			tumble(armR, aR, u, 0.22F, -1, 0.7F);
			Rigid fL = child(aL, "arm_l", "fore_l"), fR = child(aR, "arm_r", "fore_r");
			tumble(mForeL, fL, u, 0.08F, 1, 0.9F);
			tumble(mForeR, fR, u, 0.16F, -1, 0.8F);
			tumble(mFistL, child(fL, "fore_l", "fist_l"), u, 0.03F, 1, 1.0F);
			tumble(mFistR, child(fR, "fore_r", "fist_r"), u, 0.10F, -1, 1.0F);
			tumble(mShinL, child(root.at(ll.x, ll.y, ll.z), "leg_l", "shin_l"), u, 0.05F, 1, 0.3F);
			tumble(mShinR, child(root.at(lr.x, lr.y, lr.z), "leg_r", "shin_r"), u, 0.12F, -1, 0.3F);
		} else {
			if (head != null) tumble(head, body.at(0, 1.6F, 0.3F), u, 0.24F, -1, 0.9F);
			if (armL != null) tumble(armL, body.at(1.3F, 0.6F, 0), u, 0.12F, 1, 0.8F);
			if (armR != null) tumble(armR, body.at(-1.3F, 0.6F, 0), u, 0.20F, -1, 0.7F);
		}
		if (chest != null) tumble(chest, body.at(0, 0.6F, 0.6F), u, 0.22F, 1, 0.4F);
		if (core != null) tumble(core, body.at(0, 0.5F, 0.5F), u, 0.18F, -1, 0.3F);
		if (crown != null) tumble(crown, (headRig != null ? headRig : body).at(0, 0.8F, 0), u, 0.28F, 1, 1.1F);
		if (fistL != null) tumble(fistL, body.at(1.2F, -0.4F, 0.3F), u, 0.14F, 1, 0.9F);
		if (fistR != null) tumble(fistR, body.at(-1.2F, -0.4F, 0.3F), u, 0.22F, -1, 0.9F);
		if (shL != null) tumble(shL, body.at(0.8F, 1.0F, -0.2F), u, 0.26F, 1, 0.7F);
		if (shR != null) tumble(shR, body.at(-0.8F, 1.0F, -0.2F), u, 0.30F, -1, 0.7F);
		if (eyeL != null) tumble(eyeL, body.at(0.2F, 1.6F, 0.6F), u, 0.32F, 1, 0.5F);
		if (eyeR != null) tumble(eyeR, body.at(-0.2F, 1.6F, 0.6F), u, 0.32F, -1, 0.5F);
		if (back1 != null) tumble(back1, body.at(0, 0.8F, -0.6F), u, 0.28F, 1, 0.6F);
		if (back2 != null) tumble(back2, body.at(0.4F, 1.1F, -0.5F), u, 0.34F, -1, 0.6F);
		if (back3 != null) tumble(back3, body.at(-0.4F, 1.1F, -0.5F), u, 0.36F, 1, 0.6F);
		float fade = u > 0.82F ? (u - 0.82F) / 0.18F : 0;
		for (int i = 0; i < 3; i++) {
			if (orbit[i] != null) place(orbit[i], root.at(0, 3.5F - fade * 2, 0).rotY(u * 200 + i * 120).at(2.4F, 0, 0), Math.max(0, 1F - u * 1.2F));
		}
	}

	private void tumble(Part p, Rigid bind, float u, float delay, int side, float up) {
		if (p == null) return;
		float local = u <= delay ? 0F : Math.min(1F, (u - delay) / 0.35F);
		local = local * local * (3 - 2 * local);
		float fade = u > 0.82F ? (u - 0.82F) / 0.18F : 0;
		float sc = Math.max(0.04F, 1F - fade);
		Rigid r = bind
			.at(side * local * 1.6F, local * up * 0.4F - local * local * 2.8F, local * 0.8F)
			.rotX(local * 90 * side)
			.rotZ(local * 70 * side)
			.rotY(local * 50);
		place(p, r, sc);
	}

	@Override
	protected void deathFx(int tick, float u) {
		org.bukkit.Location l = mob.getLocation();
		if (tick % 4 == 0) {
			l.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, l.getX(), l.getY() + 1.4, l.getZ(), 10, 0.8, 0.9, 0.8, 0.08, org.bukkit.Material.AMETHYST_BLOCK.createBlockData());
			l.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, l.getX(), l.getY() + 1.2, l.getZ(), 3, 0.6, 0.6, 0.6, 0.02);
		}
		if (tick == 4 || tick == 16 || tick == 28 || tick == 40) {
			BU.sound(l, Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.HOSTILE, 1.4F, 0.7F + tick * 0.01F);
		}
	}

}
