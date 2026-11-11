package com.hobbang.enhance.boss;

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
	/** 망치 내려찍기: 예고(들어올림) 14틱 → 4틱째 타격 */
	private static final int SLAM_TIME = 14, SLAM_HIT = 4;
	private static final double SLAM_RADIUS = 7.0;
	private int slamTimer = 0, swingAnim = 0;
	private Vector slamAt = null;
	private Rigid headRig, hammerRig;
	private double lastX = Double.NaN, lastZ, moved = 0;
	private float slamCd = 100, fireCd = 160, waveCd = 200, vexCd = 240, thunderCd = 80;
	private float spiralCd = 180, meteorCd = 220, ringCd = 160;
	private boolean waveActive = false;
	private double waveRadius = 0;
	private Vector waveCenter = new Vector();
	private final Set<UUID> waveHit = new HashSet<>();
	private final List<double[]> strikes = new ArrayList<>();   // {x, y, z, ticksLeft}

	public SmithBoss(Mob mob) {
		super(mob, BossKind.ABYSSAL_SMITH);
	}

	@Override protected float[] phaseThresholds() { return new float[]{0.66F, 0.33F}; }
	@Override protected BossBar.Color phaseColor(int p) { return p == 1 ? BossBar.Color.PURPLE : BossBar.Color.RED; }
	@Override protected int xpReward() { return 500; }
	@Override protected float legacyScale() { return 1.0F; }
	@Override protected void bossTick(List<Player> near, LivingEntity target) {}
	@Override protected void onPhaseChange(int newPhase) {}

	// ───────── 모델 (v1.3.0) ─────────
	private Part pTorso, pLegL, pLegR, pHead, pArmL, pArmR, pHammer;

	@Override
	protected void buildParts() {
		pTorso = pLegL = pLegR = pHead = pArmL = pArmR = pHammer = null;
		if (!modelMode) return;   // 블록 모델 모드: 위더 스켈레톤 자체가 외형
		pTorso = model("torso", false); pLegL = model("leg_l", false); pLegR = model("leg_r", false);
		pHead = model("head", false); pArmL = model("arm_l", false); pArmR = model("arm_r", false); pHammer = model("hammer", false);
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

	private static float sin(float f) { return (float) Math.sin(f); }

	private static float wrapDeg(float d) {
		d %= 360F;
		if (d >= 180F) d -= 360F;
		if (d < -180F) d += 360F;
		return d;
	}

	@Override
	protected void poseParts(float t) {
		if (!modelMode || pTorso == null) return;
		float walk = (float) Math.min(1.0, moved * 6);
		float sw = sin(t * 0.18F) * 22F * walk;
		float breathe = sin(t * 0.07F) * 0.06F + Math.abs(sin(t * 0.18F)) * 0.12F * walk;
		Rigid root = Rigid.yaw(modelYaw());
		// v1.4.0: 망치는 오른손(arm_r) 피벗에 붙은 별도 파츠 — wrist = 손목 꺾임 (망치만 회전)
		float lean = 0, armRX = sw * 0.6F, armLX = -sw * 0.6F, armRZ = 0, wrist = 0;
		if (slamTimer > 0) {
			if (slamTimer > SLAM_HIT) { float k = Math.min(1F, (SLAM_TIME - slamTimer) / 6F); armRX = -165 * k; lean = -10 * k; armRZ = -10 * k; wrist = -25 * k; }
			else { armRX = -10; lean = 30; wrist = 30; }
		} else if (swingAnim > 0) {
			armRX = -95 + (10 - swingAnim) * 9;
			wrist = -20 + (10 - swingAnim) * 2;
		}
		Vector3f tp = pv(kind, "torso");
		Rigid torso = root.at(tp.x, tp.y + breathe, tp.z).rotX(lean);
		place(pTorso, torso);
		Vector3f ll = pv(kind, "leg_l"), lr = pv(kind, "leg_r");
		place(pLegL, root.at(ll.x, ll.y, ll.z).rotX(-sw));
		place(pLegR, root.at(lr.x, lr.y, lr.z).rotX(sw));
		float headYaw = wrapDeg(mob.getLocation().getYaw() - mob.getBodyYaw());
		headRig = child(torso, "torso", "head").rotY(-Math.max(-50, Math.min(50, headYaw))).rotX(slamTimer > SLAM_HIT ? -8 : 0);
		place(pHead, headRig);
		place(pArmL, child(torso, "torso", "arm_l").rotX(armLX).rotZ(-4));
		Rigid armR = child(torso, "torso", "arm_r").rotX(armRX).rotZ(armRZ);
		place(pArmR, armR);
		// v1.4.3: 손잡이는 주먹, 머리는 팔과 수직(앞으로) — 플레이어 망치 그립과 같게
		hammerRig = child(armR, "arm_r", "hammer").rotX(wrist - 90F);
		place(pHammer, hammerRig);
	}

	/** 망치 머리 (월드 좌표). 블록 모델 모드에서는 몸 앞 1.5블록 */
	private Vector hammerHead() {
		if (modelMode && hammerRig != null) return worldPoint(hammerRig, -0.3F, -2.3F, 0F);   // 망치 머리 중심 (손잡이 피벗 기준)
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
		if (modelMode && headRig != null) return worldPoint(headRig, 0, 0.5F, 1.1F);
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
		if (dying) { tickDeath(); return; }
		tickParts();
		if (assembling()) return;
		EntityEquipment eq = mob.getEquipment();
		if (eq != null && eq.getItemInMainHand().getType().isAir()) eq.setItemInMainHand(hammerItem());
		World w = mob.getWorld();
		Location l = mob.getLocation();
		if (!Double.isNaN(lastX)) moved = Math.hypot(l.getX() - lastX, l.getZ() - lastZ);
		lastX = l.getX(); lastZ = l.getZ();
		if (swingAnim > 0) swingAnim--;
		tickSlam();
		double hp = health() / maxHealth();
		if (phase == 1 && hp <= 0.66) enterPhase(2);
		else if (phase == 2 && hp <= 0.33) enterPhase(3);

		if (invulTicks > 0) {
			invulTicks--;
			double bh = mob.getHeight(), bw = mob.getWidth();
			if (invulTicks % 3 == 0) w.spawnParticle(Particle.SOUL_FIRE_FLAME, l.getX(), l.getY() + bh / 2, l.getZ(), 12, bw * 0.6, bh / 3, bw * 0.6, 0.02);
		}

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
		if (target != null && (l.distance(target.getLocation()) > 20 || inWall()) && tickCount % 40 == 0) {
			w.spawnParticle(Particle.PORTAL, l.getX(), l.getY() + mob.getHeight() / 2, l.getZ(), 40, mob.getWidth() / 2, mob.getHeight() / 3, mob.getWidth() / 2, 0.5);
			Location tl = target.getLocation();
			if (randomTeleport(tl.getX() + random.nextInt(5) - 2, tl.getY(), tl.getZ() + random.nextInt(5) - 2)) {
				BU.sound(mob.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5F, 0.6F);
			}
		}

		int speedUp = phase == 3 ? 2 : 1;
		if (invulTicks <= 0) {
			slamCd -= speedUp;
			fireCd -= speedUp;
			if (slamCd <= 0 && slamTimer <= 0 && target != null && l.distance(target.getLocation()) < 9) {
				startSlam();
				slamCd = phase == 1 ? 120 : 100;
			}
			if (fireCd <= 0 && target != null) {
				fireVolley(target);
				fireCd = 200;
			}
			if (phase >= 2) {
				waveCd -= speedUp;
				vexCd -= speedUp;
				if (waveCd <= 0 && !waveActive) {
					startWave();
					waveCd = 240;
				}
				if (vexCd <= 0) {
					summonVex(2);
					vexCd = 300;
				}
			}
			int[] col = BossPatterns.colors(kind);
			spiralCd -= speedUp;
			if (spiralCd <= 0 && target != null && l.distance(target.getLocation()) < 10) {
				BossPatterns.warnAll(this, "화염 나선! 불길의 팔 사이로 피하세요!");
				BossPatterns.spiral(this, phase >= 3 ? 4 : 3, 9, 20, 80, 7, Particle.SOUL_FIRE_FLAME, 0xFF8030);
				spiralCd = 260;
			}
			if (phase >= 2) {
				meteorCd -= speedUp;
				if (meteorCd <= 0) {
					BossPatterns.warnAll(this, "운석 단조! 붉은 원에서 벗어나세요!");
					BossPatterns.meteorsOnPlayers(this, 24, 2.8, 40, 9, Material.MAGMA_BLOCK, true);
					meteorCd = 240;
				}
			}
			if (phase == 3) {
				ringCd -= speedUp;
				if (ringCd <= 0) {
					BossPatterns.warnAll(this, "연속 충격파! 고리가 오면 점프!");
					for (int i = 0; i < 3; i++) BossPatterns.later(this, 1 + i * 18, () -> BossPatterns.shockRing(this, mob.getLocation().toVector(), 12, 0.45, 8, 16, col[0], col[1]));
					ringCd = 220;
				}
			}
			if (phase == 3) {
				if (--thunderCd <= 0) {
					for (Player p : near) {
						if (!BU.valid(p)) continue;
						Location pl = p.getLocation();
						strikes.add(new double[]{pl.getX(), pl.getY(), pl.getZ(), 20});
					}
					thunderCd = 80;
				}
			}
		}
		tickWave();
		tickStrikes();
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
			return mob.teleport(to, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
		}
		return false;
	}

	private void enterPhase(int newPhase) {
		setPhase(newPhase);
		invulTicks = 60;
		Location l = mob.getLocation();
		BU.sound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 3.0F, newPhase == 3 ? 0.6F : 0.9F);
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
		slamAt = null;
		BU.sound(mob.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 2.0F, 0.5F);
	}

	private void tickSlam() {
		if (slamTimer <= 0) return;
		slamTimer--;
		World w = mob.getWorld();
		Vector at = slamPoint();
		at.setY(BU.groundY(w, at.getX(), mob.getLocation().getY() + 1, at.getZ()));
		if (slamTimer > SLAM_HIT) {
			if (slamTimer % 2 == 0) BU.ring(w, Particle.DUST, BU.dust(0xFF2020, 1.6F), at, SLAM_RADIUS, 44);
			slamAt = at;
		} else if (slamTimer == SLAM_HIT) {
			hammerSlam(slamAt != null ? slamAt : at);
		}
	}

	private void hammerSlam(Vector c) {
		Location l = new Location(mob.getWorld(), c.getX(), c.getY(), c.getZ());
		World w = mob.getWorld();
		BU.sound(l, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 2.0F, 0.5F);
		BU.sound(l, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.0F, 0.5F);
		w.spawnParticle(Particle.EXPLOSION, c.getX(), c.getY() + 0.3, c.getZ(), 8, 3.0, 0.2, 3.0, 0.0);
		w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c.getX(), c.getY() + 0.3, c.getZ(), 30, 3.0, 0.1, 3.0, 0.02);
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

	private void fireVolley(LivingEntity target) {
		World w = mob.getWorld();
		BU.sound(mob.getLocation(), Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5F, 0.7F);
		int n = phase == 3 ? 5 : 3;
		Vector m = mouth();
		for (int i = 0; i < n; i++) {
			Location tl = target.getLocation();
			Vector dir = new Vector(tl.getX() - m.getX(), tl.getY() + target.getHeight() * 0.5 - m.getY(), tl.getZ() - m.getZ());
			double len = dir.length();
			dir.add(new Vector(tri() * len, 0, tri() * len)).normalize();
			Location at = new Location(w, m.getX(), m.getY(), m.getZ());
			w.spawn(at, SmallFireball.class, fb -> {
				fb.setShooter(mob);
				fb.setDirection(dir);
			});
		}
	}

	/** Mojang random.triangle(0, 0.15) */
	private double tri() {
		return 0.15 * (random.nextDouble() - random.nextDouble());
	}

	private void startWave() {
		waveActive = true;
		waveRadius = 1.0;
		waveCenter = mob.getLocation().toVector();
		waveHit.clear();
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
			if (s[3] <= 0) {
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
		}
	}

	private void summonVex(int n) {
		World w = mob.getWorld();
		long existing = w.getNearbyEntitiesByType(Vex.class, mob.getLocation(), 32 + mob.getWidth() / 2, 32 + mob.getHeight() / 2).size();
		if (existing >= 6) return;
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
		if (e.getDamager() == mob) swingAnim = 10;
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
		BU.sound(mob.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 2.0F, 1.5F);
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
		// 다리가 접히면 골반이 내려간다
		float hipDrop = kneel * 0.85F;
		Rigid torso = root.at(tp.x, tp.y - hipDrop - sink, tp.z).rotX(8 * kneel + 38 * slump);
		place(pTorso, torso, sc);
		Vector3f ll = pv(kind, "leg_l"), lr = pv(kind, "leg_r");
		place(pLegL, root.at(ll.x, ll.y - sink, ll.z).rotX(55 * kneel), sc);
		place(pLegR, root.at(lr.x, lr.y - sink, lr.z).rotX(50 * kneel), sc);
		headRig = child(torso, "torso", "head").rotX(28 * slump);
		place(pHead, headRig, sc);
		place(pArmL, child(torso, "torso", "arm_l").rotX(20 * slump).rotZ(-12 * slump), sc);
		Rigid armR = child(torso, "torso", "arm_r").rotX(-25 * hammerDown + 15 * slump).rotZ(8 * hammerDown);
		place(pArmR, armR, sc);
		// 망치가 손에서 아래로 내려 머리가 바닥에
		hammerRig = child(armR, "arm_r", "hammer").rotX(-90F + 70 * hammerDown);
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
