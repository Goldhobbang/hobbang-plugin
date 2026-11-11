package com.hobbang.enhance.boss;

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
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/** 프리즘 콜로서스 — 강화석 광맥이 깨어난 거대 수정 골렘 (지상형). 히트박스 = 투명 철 골렘 */
public class ColossusBoss extends DisplayBoss {
	private static final int ACT_NONE = 0, ACT_SLAM = 1, ACT_SPIKE = 2, ACT_BEAM = 3, ACT_RAIN = 4;
	private int action = ACT_NONE, actionTimer = 0;
	private float slamCd = 80, spikeCd = 120, beamCd = 200, rainCd = 160;
	private int swingAnim = 0, attackCd = 0;
	private Vector lockDir = new Vector();
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
			mChest = model("chest", false);
			mForeL = model("fore_l", false); mForeR = model("fore_r", false); mFistL = model("fist_l", false); mFistR = model("fist_r", false);
			mShinL = model("shin_l", false); mShinR = model("shin_r", false);
			torso = model("torso", false); legL = model("leg_l", false); legR = model("leg_r", false);
			head = model("head", false); armL = model("arm_l", false); armR = model("arm_r", false);
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
		if (modelMode) { poseModel(t); return; }
		float walk = (float) Math.min(1.0, moved * 8);
		float sw = sin(t * 0.25F) * 28F * walk;
		float breathe = sin(t * 0.08F) * 0.04F;
		Rigid root = Rigid.yaw(modelYaw());

		float lean = 0, armLX = sw * -0.5F, armRX = sw * 0.5F, armLZ = -8, armRZ = 8;
		if (action == ACT_SLAM) {
			if (actionTimer > 6) { armLX = armRX = -165; lean = -12; } else { armLX = armRX = -25; lean = 18; }
		} else if (action == ACT_SPIKE) {
			armRX = actionTimer > 4 ? -150 : -30; lean = actionTimer > 4 ? -5 : 12;
		} else if (action == ACT_BEAM) {
			armLZ = -75; armRZ = 75; armLX = armRX = -20;
		} else if (action == ACT_RAIN) {
			armLX = armRX = -180; armLZ = -20; armRZ = 20;
		}
		if (swingAnim > 0) armRX = -110 + (10 - swingAnim) * 10;

		place(legL, root.at(0.55F, 1.7F, 0).rotX(sw));
		place(legR, root.at(-0.55F, 1.7F, 0).rotX(-sw));
		Rigid body = root.at(0, 1.6F + breathe, 0).rotX(lean);
		place(torso, body);
		place(chest, body);
		place(core, body.at(0, 0.85F, 0.72F).rotZ(t * 6));
		float headYaw = wrap(mob.getLocation().getYaw() - mob.getBodyYaw());
		Rigid hd = body.at(0, 1.7F, 0).rotY(-Math.max(-60, Math.min(60, headYaw)));
		headRig = hd.at(0, 0.4F, 0.45F);
		place(head, hd);
		place(eyeL, hd.at(0.2F, 0.4F, 0.45F));
		place(eyeR, hd.at(-0.2F, 0.4F, 0.45F));
		place(crown, hd.at(0, 0.75F, 0).rotY(t * 2));
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
			Rigid o = root.at(0, 2.8F + sin(t * 0.1F + i) * 0.3F, 0).rotY(t * (phase >= 3 ? 9 : 5) + i * 120).at(2.3F, 0, 0).rotX(t * 8).rotZ(t * 5);
			place(orbit[i], o, orbScale);
		}
	}

	/** v1.3.0: 리소스팩 모델 포즈 — 걷기 흔들림 / 숨쉬기 / 행동별 팔 자세(예고 동작) */
	private void poseModel(float t) {
		float walk = (float) Math.min(1.0, moved * 8);
		float sw = sin(t * 0.2F) * 24F * walk;
		float breathe = sin(t * 0.08F) * 0.06F + Math.abs(sin(t * 0.2F)) * 0.1F * walk;
		Rigid root = Rigid.yaw(modelYaw());
		float lean = 0, armLX = sw * -0.5F, armRX = sw * 0.5F, armLZ = -4, armRZ = 4;
		if (action == ACT_SLAM) {
			if (actionTimer > 6) { armLX = armRX = -165; lean = -12; } else { armLX = armRX = -30; lean = 18; }
		} else if (action == ACT_SPIKE) {
			armRX = actionTimer > 4 ? -150 : -30; lean = actionTimer > 4 ? -5 : 12;
		} else if (action == ACT_BEAM) {
			armLZ = -70; armRZ = 70; armLX = armRX = -20; lean = -6;
		} else if (action == ACT_RAIN) {
			armLX = armRX = -175; armLZ = -20; armRZ = 20;
		}
		if (swingAnim > 0) armRX = -110 + (10 - swingAnim) * 10;
		org.joml.Vector3f ll = pv(kind, "leg_l"), lr = pv(kind, "leg_r"), tp = pv(kind, "torso");
		// 다리: 앞으로 나가는 쪽 무릎이 굽는다 (rotX + = 뒤로)
		Rigid lgL = root.at(ll.x, ll.y, ll.z).rotX(sw);
		place(legL, lgL);
		place(mShinL, child(lgL, "leg_l", "shin_l").rotX(Math.max(0, -sw) * 0.8F + 3));
		Rigid lgR = root.at(lr.x, lr.y, lr.z).rotX(-sw);
		place(legR, lgR);
		place(mShinR, child(lgR, "leg_r", "shin_r").rotX(Math.max(0, sw) * 0.8F + 3));
		// 골반은 조금, 상체가 나머지를 숙인다
		Rigid pelvis = root.at(tp.x, tp.y + breathe, tp.z).rotX(lean * 0.4F);
		place(torso, pelvis);
		Rigid body = child(pelvis, "torso", "chest").rotX(lean * 0.6F).rotY(sw * 0.1F);
		place(mChest, body);
		float headYaw = wrap(mob.getLocation().getYaw() - mob.getBodyYaw());
		Rigid hd = child(body, "chest", "head").rotY(-Math.max(-60, Math.min(60, headYaw)));
		headRig = hd.at(0, 0.45F, 0.6F);
		place(head, hd);
		// 팔 3마디: 팔꿈치는 앞으로 굽고, 주먹이 조금 더 따라온다. 내려찍기 예비동작에서 크게 접는다.
		float elbow = -10 - Math.abs(sw) * 0.3F;
		float elbowL = elbow, elbowR = elbow;
		if (action == ACT_SLAM) elbowL = elbowR = actionTimer > 6 ? -35 : -8;
		else if (action == ACT_SPIKE) elbowR = actionTimer > 4 ? -40 : -8;
		else if (action == ACT_RAIN) elbowL = elbowR = -15;
		if (swingAnim > 0) elbowR = -35 + (10 - swingAnim) * 3;
		Rigid aL = child(body, "chest", "arm_l").rotX(armLX).rotZ(-armLZ);
		place(armL, aL);
		Rigid fL = child(aL, "arm_l", "fore_l").rotX(elbowL);
		place(mForeL, fL);
		place(mFistL, child(fL, "fore_l", "fist_l").rotX(elbowL * 0.4F));
		Rigid aR = child(body, "chest", "arm_r").rotX(armRX).rotZ(-armRZ);
		place(armR, aR);
		Rigid fR = child(aR, "arm_r", "fore_r").rotX(elbowR);
		place(mForeR, fR);
		place(mFistR, child(fR, "fore_r", "fist_r").rotX(elbowR * 0.4F));
		float orbScale = phase >= 2 ? 1.0F : 0.0F;
		for (int i = 0; i < 3; i++) {
			Rigid o = root.at(0, 4.2F + sin(t * 0.1F + i) * 0.4F, 0).rotY(t * (phase >= 3 ? 9 : 5) + i * 120).at(3.4F, 0, 0).rotX(t * 8).rotZ(t * 5);
			place(orbit[i], o, orbScale);
		}
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
		}
	}

	@Override
	protected void onPhaseChange(int p) {
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
			boolean ok = BU.hurtable(target);
			target.damage(16, BU.mob(mob));
			if (ok) {
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
		chase(target);
		if (invulTicks > 0) return;
		float spd = phase >= 3 ? 1.5F : 1.0F;
		if (action != ACT_NONE) {
			tickAction(target);
			return;
		}
		if (target == null) return;
		slamCd -= spd; spikeCd -= spd; if (phase >= 2) beamCd -= spd; if (phase >= 3) rainCd -= spd;
		double dist = mob.getLocation().distance(target.getLocation());
		if (rainCd <= 0 && phase >= 3) { start(ACT_RAIN, 34); rainCd = 180; }
		else if (newPatterns(target, dist)) { /* v1.2.0 신규 패턴 */ }
		else if (beamCd <= 0 && phase >= 2) { start(ACT_BEAM, 60); beamCd = 240; BU.sound(here, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2F, 0.6F); }
		else if (slamCd <= 0 && dist < 8.5) { start(ACT_SLAM, 26); slamCd = 120; BU.sound(here, Sound.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 2F, 0.5F); }
		else if (spikeCd <= 0 && dist < 18) {
			start(ACT_SPIKE, 24); spikeCd = 130;
			Vector d = target.getLocation().toVector().subtract(here.toVector()).setY(0);
			lockDir = d.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : d.normalize();
		}
	}

	private float prismRingCd = 140, crossCd = 220, gravCd = 300;

	/** v1.2.0: 수정 충격파 / 프리즘 십자 광선 / 중력장 */
	private boolean newPatterns(LivingEntity target, double dist) {
		float spd = phase >= 3 ? 1.5F : 1.0F;
		prismRingCd -= spd;
		if (phase >= 2) crossCd -= spd;
		if (phase >= 3) gravCd -= spd;
		int[] col = BossPatterns.colors(kind);
		Vector here = mob.getLocation().toVector();
		if (phase >= 3 && gravCd <= 0) {
			BossPatterns.warnAll(this, "수정 중력장! 원 밖으로 달리세요!");
			BossPatterns.vortex(this, here, 9, 70, 12, col[0], col[1]);
			start(ACT_NONE, 0);
			gravCd = 320;
			return true;
		}
		if (phase >= 2 && crossCd <= 0) {
			BossPatterns.warnAll(this, "프리즘 십자 광선! 빛줄기 예고선을 피하세요!");
			double a0 = random.nextDouble() * Math.PI;
			BossPatterns.groundBeams(this, here, 4, a0, 15, 30, 26, 9, Material.PINK_STAINED_GLASS, col[0]);
			if (phase >= 3) BossPatterns.later(this, 34, () -> BossPatterns.groundBeams(this, here, 4, a0 + Math.PI / 4, 15, 26, 26, 9, Material.LIGHT_BLUE_STAINED_GLASS, col[1]));
			crossCd = 260;
			return true;
		}
		if (prismRingCd <= 0 && dist < 14) {
			BossPatterns.shockRing(this, here, 13, 0.5, 8, 18, col[0], col[1]);
			prismRingCd = phase >= 2 ? 180 : 220;
			return true;
		}
		return false;
	}

	private void start(int act, int time) {
		action = act;
		actionTimer = time;
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
					if (actionTimer % 2 == 0) BU.ring(w, Particle.DUST, BU.dust(0xFF2020, 1.5F), c, SLAM_R, 48);
				} else if (actionTimer == 6) {
					BU.sound(mob.getLocation(), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 2.5F, 0.5F);
					w.spawnParticle(Particle.EXPLOSION, c.getX(), c.getY() + 0.3, c.getZ(), 10, 3.8, 0.2, 3.8, 0);
					w.spawnParticle(Particle.BLOCK, c.getX(), c.getY() + 0.3, c.getZ(), 80, 3.8, 0.3, 3.8, 0.2, bd(Material.AMETHYST_BLOCK));
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
					if (actionTimer % 2 == 0) BU.line(w, Particle.DUST, BU.dust(0xC060FF, 1.2F), from.clone().add(new Vector(0, 0.15, 0)),
						from.clone().add(lockDir.clone().multiply(16)).add(new Vector(0, 0.15, 0)), 0.6);
				} else if (actionTimer == 4) {
					BU.sound(mob.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 3F, 0.5F);
					for (int i = 0; i < 13; i++) {
						Vector p = from.clone().add(lockDir.clone().multiply(i * 1.25));
						spike(w, new Vector(p.getX(), BU.groundY(w, p.getX(), c.getY() + 2, p.getZ()), p.getZ()), i, 1.5F, uuid, mob);
					}
				}
			}
			case ACT_BEAM -> {
				Vector eye = eye();
				if (actionTimer > 30) {
					if (target != null) {
						Vector d = target.getEyeLocation().toVector().subtract(eye);
						if (d.lengthSquared() > 1e-6) lockDir = d.normalize();
					}
					if (actionTimer > 36) BU.line(w, Particle.DUST, BU.dust(0x80E0FF, 0.8F), eye, eye.clone().add(lockDir.clone().multiply(22)), 0.8);
					else BU.line(w, Particle.DUST, BU.dust(0xFF3030, 1.0F), eye, eye.clone().add(lockDir.clone().multiply(22)), 0.5);
					if (core != null) core.display.setGlowing(true);
					else if (head != null) { head.display.setGlowColorOverride(org.bukkit.Color.fromRGB(0x80E0FF)); head.display.setGlowing(true); }
				} else if (actionTimer == 30) {
					BU.sound(mob.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 3F, 1.6F);
					beam(w, eye, lockDir.clone(), 22, 30);
				}
				if (actionTimer == 0) { refreshGlow(); if (modelMode && head != null && hurtFlash == 0) head.display.setGlowing(false); }
			}
			case ACT_RAIN -> {
				if (actionTimer == 33) {
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
		Fx.spawn(world, owner, at, delay + 30, (fx, w) -> {
			if (fx.age == delay + 1) {
				BlockDisplay d = fx.addBlock(Material.AMETHYST_CLUSTER.createBlockData(), true);
				Fx.shape(d, new Vector3f(-size / 2, -0.2F, -size / 2), new Quaternionf(), new Vector3f(size, 0.05F, size), 1);
			} else if (fx.age == delay + 2) {
				for (var d : fx.displays) Fx.shape(d, new Vector3f(-size / 2, -0.2F, -size / 2), new Quaternionf(), new Vector3f(size, size * 1.6F, size), 3);
				w.spawnParticle(Particle.BLOCK, at.getX(), at.getY() + 0.3, at.getZ(), 12, 0.3, 0.2, 0.3, 0.1, Material.AMETHYST_BLOCK.createBlockData());
				BU.sound(new Location(w, at.getX(), at.getY(), at.getZ()), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 1.2F, 0.7F);
				for (Entity ent : w.getNearbyEntities(BU.box(at, 1.3, 2.5, 1.3), en -> en instanceof LivingEntity le && !le.isDead() && en != source)) {
					LivingEntity e = (LivingEntity) ent;
					if (BossManager.isBoss(source)) {
						if (!(e instanceof Player pl) || !BU.valid(pl)) continue;
						e.damage(dmg, BU.mob(source));
					} else if (source instanceof Player pl) {
						if (e instanceof Player) continue;
						com.hobbang.enhance.weapon.WeaponListener.skillDamage(e, pl, dmg);
					}
					e.setVelocity(e.getVelocity().add(new Vector(0, 0.9, 0)));
				}
			} else if (fx.age == delay + 24) {
				for (var d : fx.displays) Fx.shape(d, new Vector3f(0, -0.2F, 0), new Quaternionf(), new Vector3f(0, 0, 0), 5);
			}
		});
	}

	private void beam(World world, Vector start, Vector dir, float len, int life) {
		Quaternionf q = new Quaternionf().rotationTo(0, 0, 1, (float) dir.getX(), (float) dir.getY(), (float) dir.getZ());
		Fx.spawn(world, uuid, start, life, (fx, w) -> {
			if (fx.age == 1) {
				BlockDisplay d = fx.addBlock(bd(Material.LIGHT_BLUE_STAINED_GLASS), true);
				BlockDisplay d2 = fx.addBlock(bd(Material.WHITE_STAINED_GLASS), true);
				d.setGlowing(true);
				d.setGlowColorOverride(org.bukkit.Color.fromRGB(0x60E0FF));
				Fx.shape(d, new Vector3f(-0.35F, -0.35F, 0).rotate(q), q, new Vector3f(0.7F, 0.7F, len), 2);
				Fx.shape(d2, new Vector3f(-0.15F, -0.15F, 0).rotate(q), q, new Vector3f(0.3F, 0.3F, len), 2);
			}
			if (fx.age % 5 == 0) {
				Vector end = start.clone().add(dir.clone().multiply(len));
				BoundingBox box = BoundingBox.of(start, end).expand(1.5);
				for (Player p : BU.victims(w, box)) {
					if (BU.distToSegment(p.getLocation().toVector().add(new Vector(0, 0.9, 0)), start, end) < 1.1) {
						p.damage(6, BU.magic(mob));
						p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
					}
				}
				double k = random.nextDouble();
				w.spawnParticle(Particle.END_ROD, start.getX() + dir.getX() * len * k, start.getY() + dir.getY() * len * random.nextDouble(),
					start.getZ() + dir.getZ() * len * random.nextDouble(), 4, 0.2, 0.2, 0.2, 0.02);
			}
			if (fx.age == life - 4) for (var d : fx.displays) Fx.shape(d, new Vector3f(), q, new Vector3f(0, 0, len), 4);
		});
	}

	private void fallingShard(World world, Vector at) {
		Fx.spawn(world, uuid, at, 56, (fx, w) -> {
			if (fx.age < 30) {
				if (fx.age % 3 == 0) BU.ring(w, Particle.DUST, BU.dust(0xFF60C0, 1.3F), at, 2.2, 18);
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
