package com.hobbang.enhance.boss;

import com.hobbang.enhance.item.HItem;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** 크로노 센티넬 — 시간을 지키는 태엽 감시자 (부유형). 히트박스 = 투명 늑대(AI 끔, 중력 끔) */
public class SentinelBoss extends DisplayBoss {
	private float sweepCd = 100, boltCd = 50, stopCd = 200, rewindCd = 0;
	private float ringCd = 150, echoCd = 240, bladeCd = 280;
	private int stopTimer = 0, rewindTimer = 0, stunTimer = 0;
	private float rewindDamage = 0;
	private final float spinMul = 1;
	private static final float REWIND_BREAK = 80;

	private Part core, face, hourHand, minHand, bell, pendRod, pendBob, label;
	private final Part[] ringA = new Part[8];
	private final Part[] ringB = new Part[10];
	/** v1.3.0 리소스팩 모델 파츠 (톱니 고리 / 시계 코어는 따로 회전) */
	private Part mBody, mRing, mClock, mArmL, mArmR;
	private Rigid coreRig;
	private float ringAngle = 0, clockAngle = 0, deathU = 0, deathSpin = 1;
	private int castAnim = 0;
	private static final float LEGACY = 5.6F / (2.0F / 0.6F);

	public SentinelBoss(Mob mob) {
		super(mob, BossKind.CHRONO_SENTINEL);
		if (phase >= 3) rewindCd = 60;
	}

	@Override protected float[] phaseThresholds() { return new float[]{0.6F, 0.3F}; }

	@Override
	protected BossBar.Color phaseColor(int p) {
		return p == 1 ? BossBar.Color.YELLOW : p == 2 ? BossBar.Color.GREEN : BossBar.Color.RED;
	}

	@Override protected boolean glowParts() { return phase >= 3; }
	@Override protected int glowColor() { return 0x40FFD0; }
	@Override protected float damageMultiplier() { return stunTimer > 0 ? 1.5F : 1.0F; }
	@Override protected int xpReward() { return 1000; }
	@Override protected float legacyScale() { return LEGACY; }

	private static BlockData bd(Material m) { return m.createBlockData(); }
	private static float sin(double f) { return (float) Math.sin(f); }

	private BlockData copper(int ring) {
		return switch (phase) {
			case 1 -> ring == 0 ? bd(Material.CUT_COPPER) : bd(Material.EXPOSED_CUT_COPPER);
			case 2 -> ring == 0 ? bd(Material.WEATHERED_CUT_COPPER) : bd(Material.EXPOSED_CUT_COPPER);
			default -> bd(Material.OXIDIZED_CUT_COPPER);
		};
	}

	// ───────── 모델 ─────────
	@Override
	protected void buildParts() {
		core = face = hourHand = minHand = bell = pendRod = pendBob = label = null;
		mBody = mRing = mClock = mArmL = mArmR = null;
		java.util.Arrays.fill(ringA, null);
		java.util.Arrays.fill(ringB, null);
		if (modelMode) {
			mBody = model("body", false); mArmL = model("arm_l", false); mArmR = model("arm_r", false);
			mRing = model("ring", false); mClock = model("clock", true);
			label = text(Component.empty(), 0, 0, 0, 1.6F);
			return;
		}
		core = block(bd(Material.GOLD_BLOCK), -0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, false);
		face = item(new ItemStack(Material.CLOCK), 0, 0, 0.53F, 1.3F, true);
		hourHand = block(bd(Material.BLACK_CONCRETE), -0.04F, 0F, 0F, 0.08F, 0.35F, 0.03F, false);
		minHand = block(bd(Material.RED_CONCRETE), -0.03F, 0F, 0F, 0.06F, 0.5F, 0.03F, true);
		bell = block(bd(Material.GOLD_BLOCK), -0.3F, 0F, -0.3F, 0.6F, 0.35F, 0.6F, false);
		pendRod = block(bd(Material.IRON_BARS), -0.5F, -1.3F, -0.5F, 1.0F, 1.3F, 1.0F, false);
		pendBob = block(bd(Material.GOLD_BLOCK), -0.25F, -1.7F, -0.1F, 0.5F, 0.5F, 0.2F, true);
		for (int i = 0; i < ringA.length; i++) ringA[i] = block(copper(0), -0.3F, -0.14F, -0.14F, 0.6F, 0.28F, 0.28F, false);
		for (int i = 0; i < ringB.length; i++) ringB[i] = block(copper(1), -0.25F, -0.12F, -0.12F, 0.5F, 0.24F, 0.24F, false);
		label = text(Component.empty(), 0, 0, 0, 1.2F);
	}

	@Override
	protected void onPartsRebuilt() {
		refreshGlow();
	}

	@Override
	protected void poseParts(float t) {
		float sp = spinMul * (rewindTimer > 0 ? -3.5F : phase >= 3 ? 1.8F : 1.0F);
		if (stunTimer > 0) sp = 0.1F;
		if (dying) sp *= deathSpin;
		if (modelMode) { poseModel(t, sp); return; }
		Rigid root = Rigid.yaw(modelYaw());
		Rigid c = root.at(0, 1.6F + sin(t * 0.06F) * 0.15F, 0);
		coreRig = c;
		place(core, c.rotY(t * 1.5F * sp));
		place(face, c);
		Rigid fc = c.at(0, 0, 0.62F);
		place(hourHand, fc.rotZ(-t * 1.5F * sp));
		place(minHand, fc.rotZ(-t * 12F * sp));
		place(bell, c.at(0, 0.5F, 0));
		Rigid pend = c.at(0, -0.5F, 0).rotX(sin(t * 0.12F * Math.abs(sp)) * 28);
		place(pendRod, pend);
		place(pendBob, pend);
		for (int i = 0; i < ringA.length; i++) {
			float a = t * 3 * sp + i * 360F / ringA.length;
			place(ringA[i], c.rotY(a).at(0, 0, 1.6F).rotY(90));
		}
		for (int i = 0; i < ringB.length; i++) {
			float a = -t * 4 * sp + i * 360F / ringB.length;
			place(ringB[i], c.rotX(65).rotZ(20).rotY(a).at(0, 0, 2.0F).rotY(90));
		}
		place(label, root.at(0, 3.6F, 0));
	}

	/** v1.3.0: 모델 포즈 — 부유 흔들림, 톱니 고리는 수직축으로, 시계 코어는 자기 축으로 회전 (역행 중 반대로, 과부하 때 거의 멈춤) */
	private void poseModel(float t, float sp) {
		if (dying) sp *= deathSpin;
		Rigid root = Rigid.yaw(modelYaw());
		org.joml.Vector3f bp = pv(kind, "body");
		float bob = dying ? 0 : sin(t * 0.06F) * 0.2F;
		float tilt = stunTimer > 0 ? 12 : sin(t * 0.045F) * 2.5F;
		float fade = dying && deathU > 0.92F ? (deathU - 0.92F) / 0.08F : 0;
		float sc = Math.max(0.05F, 1F - fade);
		Rigid b = root.at(bp.x, bp.y + bob - fade * 1.2F, bp.z).rotX(tilt);
		place(mBody, b, sc);
		ringAngle += 3 * sp * ANIM_INTERVAL;
		clockAngle += 6 * sp * ANIM_INTERVAL;
		place(mRing, child(b, "body", "ring").rotY(ringAngle), sc);
		BossModels.Spec cs = BossModels.get(kind.id, "clock");
		coreRig = child(b, "body", "clock");
		place(mClock, coreRig.rotAxis(-clockAngle, cs.spinAxis()), sc);
		float lift = castAnim > 0 ? -70 * Math.min(1F, castAnim / 6F) : 0;
		float sway = dying ? 0 : sin(t * 0.05F) * 6;
		place(mArmL, child(b, "body", "arm_l").rotX(lift + sway).rotZ(castAnim > 0 ? 25 : 4), sc);
		place(mArmR, child(b, "body", "arm_r").rotX(lift - sway).rotZ(castAnim > 0 ? -25 : -4), sc);
		place(label, root.at(0, 5.7F, 0), sc);
	}

	/** 패턴 시작점 = 시계 코어 */
	private Vector coreAt() {
		if (coreRig != null) return worldPoint(coreRig, 0, 0, 0);
		return mob.getLocation().toVector().add(new Vector(0, 1.6 * vs, 0));
	}

	private void setLabel(Component c) {
		if (label != null && label.display instanceof TextDisplay td && td.isValid()) td.text(c);
	}

	@Override
	protected void onPhaseChange(int p) {
		for (Part r : ringA) if (r != null) r.setBlock(copper(0));
		for (Part r : ringB) if (r != null) r.setBlock(copper(1));
		if (p == 2) {
			BU.say(mob, Component.text("크로노 센티넬: \"시간은 나의 것. 너희의 1초는 이제 내 허락이 필요하다.\"", NamedTextColor.GOLD, TextDecoration.ITALIC));
			BU.say(mob, Component.text("⚠ 2페이즈: 시간 정지 - 카운트다운이 끝날 때 보스를 등지세요!", NamedTextColor.GRAY));
		} else if (p == 3) {
			BU.say(mob, Component.text("크로노 센티넬: \"되감는다... 너희가 이긴 순간마저도!\"", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
			BU.say(mob, Component.text("⚠ 3페이즈: 시간 역행 - 채널링 중 " + (int) REWIND_BREAK + " 피해를 주면 저지! 실패 시 체력 회복", NamedTextColor.GRAY));
			rewindCd = 60;
		}
		refreshGlow();
	}

	@Override
	protected void onHurtBy(EntityDamageEvent e, double amount) {
		if (rewindTimer > 0) {
			rewindDamage += (float) Math.min(amount, 40);
			if (rewindDamage >= REWIND_BREAK) {
				rewindTimer = 0;
				stunTimer = 70;
				Location l = mob.getLocation();
				Vector cc = coreAt();
				BU.sound(l, Sound.BLOCK_ANVIL_BREAK, SoundCategory.HOSTILE, 2F, 0.5F);
				mob.getWorld().spawnParticle(Particle.EXPLOSION, cc.getX(), cc.getY(), cc.getZ(), 4, 1.5, 1.5, 1.5, 0);
				BU.say(mob, Component.text("✦ 시간 역행 저지! 센티넬이 과부하 상태입니다 (받는 피해 +50%)", NamedTextColor.GREEN, TextDecoration.BOLD));
				setLabel(Component.text("과부하!", NamedTextColor.RED, TextDecoration.BOLD));
			}
		}
	}

	@Override
	protected void bossTick(List<Player> near, LivingEntity target) {
		World w = mob.getWorld();
		Location here = mob.getLocation();
		if (castAnim > 0) castAnim--;
		// 부유 이동
		if (target != null) {
			Vector to = target.getLocation().toVector().subtract(here.toVector());
			Vector flat = new Vector(to.getX(), 0, to.getZ());
			double dist = flat.length();
			Vector desired = here.toVector();
			if (dist > 1e-6) {
				if (dist > 9) desired.add(flat.clone().normalize().multiply(dist - 7));
				else if (dist < 5) desired.subtract(flat.clone().normalize().multiply(5 - dist));
			}
			double gy = BU.groundY(w, here.getX(), here.getY(), here.getZ());
			desired.setY(Math.max(gy + 2.5, target.getLocation().getY() + 2.5));
			Vector v = mob.getVelocity().multiply(0.8).add(desired.subtract(here.toVector()).multiply(0.02));
			if (stunTimer > 0) v = new Vector(0, -0.04, 0);
			mob.setVelocity(v);
			if (tickCount % 3 == 0) {
				float yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
				mob.setRotation(yaw, 0);
				mob.setBodyYaw(yaw);
			}
		} else {
			mob.setVelocity(mob.getVelocity().multiply(0.8));
		}
		if (stunTimer > 0) {
			stunTimer--;
			if (stunTimer % 5 == 0) { Vector cc = coreAt(); w.spawnParticle(Particle.SMOKE, cc.getX(), cc.getY(), cc.getZ(), 8, 1.0, 1.0, 1.0, 0.02); }
			if (stunTimer == 0) setLabel(Component.empty());
			return;
		}
		if (rewindTimer > 0) {
			tickRewind();
			return;
		}
		if (stopTimer > 0) tickTimeStop();
		if (invulTicks > 0 || target == null) return;
		float spd = phase >= 3 ? 1.4F : 1.0F;
		sweepCd -= spd; boltCd -= spd;
		if (phase >= 2) stopCd -= spd;
		if (phase >= 3) rewindCd -= 1;
		if (phase >= 3 && rewindCd <= 0) { startRewind(); rewindCd = 520; return; }
		if (phase >= 2 && stopCd <= 0 && stopTimer <= 0) {
			stopTimer = 61; stopCd = 320;
			BU.say(mob, Component.text("⚠ 시간 정지! 카운트다운이 끝날 때 보스에게서 시선을 돌리세요!", NamedTextColor.GOLD));
		}
		if (sweepCd <= 0) { sweep(phase >= 3); sweepCd = 170; }
		if (boltCd <= 0) { bolts(phase >= 2 ? 8 : 6, null); boltCd = 80; }
		// v1.2.0: 시간 고리 / 잔상 폭발 / 자정의 칼날
		int[] col = BossPatterns.colors(kind);
		ringCd -= spd;
		if (phase >= 2) echoCd -= spd;
		if (phase >= 3) bladeCd -= 1;
		Vector c = here.toVector();
		if (ringCd <= 0) { BossPatterns.shockRing(this, c, 11, 0.4, 7, 20, col[0], col[1]); ringCd = 200; }
		else if (phase >= 2 && echoCd <= 0) {
			BossPatterns.warnAll(this, "잔상 폭발! 2초 뒤 지금 자리가 터집니다 — 계속 움직이세요!");
			BossPatterns.meteorsOnPlayers(this, 24, 2.5, 40, 8, Material.YELLOW_STAINED_GLASS, false);
			echoCd = 260;
		} else if (phase >= 3 && bladeCd <= 0) {
			BossPatterns.warnAll(this, "자정의 칼날! 빛나는 예고선에서 벗어나세요!");
			BossPatterns.groundBeams(this, c, 4, 0, 13, 30, 24, 9, Material.YELLOW_STAINED_GLASS, col[0]);
			BossPatterns.later(this, 40, () -> BossPatterns.groundBeams(this, c, 4, Math.PI / 4, 13, 26, 24, 9, Material.WHITE_STAINED_GLASS, col[1]));
			bladeCd = 300;
		}
	}

	private void tickTimeStop() {
		stopTimer--;
		Location here = mob.getLocation();
		if (stopTimer % 20 == 0 && stopTimer > 0) {
			setLabel(Component.text("⏳ " + (stopTimer / 20), NamedTextColor.GOLD, TextDecoration.BOLD));
			BU.sound(here, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.HOSTILE, 3F, 0.5F + stopTimer / 60F);
		}
		if (stopTimer == 0) {
			setLabel(Component.text("정지!", NamedTextColor.AQUA, TextDecoration.BOLD));
			Fx.later(30, () -> setLabel(Component.empty()));
			BU.sound(here, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 3F, 0.5F);
			Vector me = coreAt();
			Set<UUID> frozen = new HashSet<>();
			for (Player p : nearbyPlayers(32)) {
				if (!BU.valid(p)) continue;
				Vector toMe = me.clone().subtract(p.getEyeLocation().toVector()).normalize();
				if (p.getEyeLocation().getDirection().dot(toMe) > 0.3) {
					p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 6));
					p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 2));
					p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
					p.sendMessage(Component.text("⏸ 시간이 멈췄습니다...", NamedTextColor.AQUA));
					frozen.add(p.getUniqueId());
				} else {
					p.sendMessage(Component.text("✓ 시선을 피했습니다!", NamedTextColor.GREEN));
				}
			}
			if (!frozen.isEmpty()) bolts(6, frozen);
		}
	}

	private void startRewind() {
		rewindTimer = 160;
		rewindDamage = 0;
		BU.sound(mob.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.HOSTILE, 2F, 1.5F);
		BU.say(mob, Component.text("⚠ 시간 역행 시작! 8초 안에 " + (int) REWIND_BREAK + " 피해를 주세요!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
	}

	private void tickRewind() {
		rewindTimer--;
		Location here = mob.getLocation();
		if (rewindTimer % 5 == 0) {
			setLabel(Component.text("되감는 중… 저지까지 " + Math.max(0, (int) (REWIND_BREAK - rewindDamage)), NamedTextColor.AQUA));
			Vector cc = coreAt();
			mob.getWorld().spawnParticle(Particle.REVERSE_PORTAL, cc.getX(), cc.getY(), cc.getZ(), 24, 2.2, 2.2, 2.2, 0.05);
		}
		if (rewindTimer == 0) {
			heal(maxHealth() * 0.12);
			BU.sound(here, Sound.ITEM_TOTEM_USE, SoundCategory.HOSTILE, 2F, 0.6F);
			BU.say(mob, Component.text("✖ 시간 역행 성공... 센티넬의 체력이 회복되었습니다", NamedTextColor.RED));
			setLabel(Component.empty());
		}
	}

	private void sweep(boolean dbl) {
		World world = mob.getWorld();
		Location c0 = mob.getLocation();
		double gy = BU.groundY(world, c0.getX(), c0.getY(), c0.getZ());
		Vector c = new Vector(c0.getX(), gy, c0.getZ());
		float startAngle = random.nextFloat() * 360;
		float dir = random.nextBoolean() ? 1 : -1;
		int hands = dbl ? 2 : 1;
		final float len = 12;
		BU.sound(c0, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.HOSTILE, 3F, 0.6F);
		BU.say(mob, Component.text("⚠ 시계침이 돕니다! 점프하거나 범위 밖으로!", NamedTextColor.YELLOW));
		Fx.spawn(world, uuid, c, 80, (f, w) -> {
			if (f.age == 1) {
				for (int h = 0; h < hands; h++) {
					BlockDisplay d = f.addBlock(bd(Material.RED_STAINED_GLASS), true);
					d.setGlowing(true); d.setGlowColorOverride(Color.fromRGB(0xFF3030));
				}
			}
			int warm = 25;
			float angle = startAngle + (f.age <= warm ? 0 : (f.age - warm) * 7.2F * dir);
			float prev = startAngle + (f.age - 1 <= warm ? 0 : (f.age - 1 - warm) * 7.2F * dir);
			if (f.age == warm) {
				for (Display d : f.displays) {
					if (d instanceof BlockDisplay bdd) bdd.setBlock(bd(Material.BLACK_CONCRETE));
					d.setGlowColorOverride(Color.fromRGB(0xFFD040));
				}
				BU.sound(new Location(w, c.getX(), c.getY(), c.getZ()), Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2F, 0.6F);
			}
			for (int h = 0; h < f.displays.size(); h++) {
				float a = angle + h * 180;
				Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(a));
				Fx.shape(f.displays.get(h), new Vector3f(-0.2F, 0.05F, 0.5F).rotate(q), q, new Vector3f(0.4F, f.age < warm ? 0.1F : 0.6F, len), 1);
			}
			if (f.age > warm && f.age <= warm + 50) {
				for (Player p : BU.victims(w, BU.box(c, len + 1, 3, len + 1))) {
					Location pl = p.getLocation();
					double dx = pl.getX() - c.getX(), dz = pl.getZ() - c.getZ();
					double dist = Math.sqrt(dx * dx + dz * dz);
					if (dist < 0.5 || dist > len + 0.5 || pl.getY() > c.getY() + 1.1) continue;
					float pa = (float) Math.toDegrees(Math.atan2(dx, dz));
					for (int h = 0; h < hands; h++) {
						float a0 = prev + h * 180, a1 = angle + h * 180;
						float lo = Math.min(a0, a1) - 4, hi = Math.max(a0, a1) + 4;
						float rel = lo + (((pa - lo) % 360) + 360) % 360;
						if (rel <= hi && p.getNoDamageTicks() == 0) {
							BU.hit(p, mob, 10, c, 1.0, 0.5);
							w.spawnParticle(Particle.CRIT, pl.getX(), pl.getY() + 1, pl.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
						}
					}
				}
			}
			if (f.age == warm + 52) for (Display d : f.displays) Fx.shape(d, new Vector3f(), new Quaternionf(), new Vector3f(0, 0, 0), 6);
		});
	}

	private void bolts(int n, Set<UUID> only) {
		World world = mob.getWorld();
		Vector c = coreAt();
		castAnim = 14;
		double br = 1.2 + mob.getWidth() / 2;
		BU.sound(mob.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 3F, 1.6F);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			Vector start = c.clone().add(new Vector(Math.cos(a) * br, Math.sin(i) * 0.5, Math.sin(a) * br));
			final int idx = i;
			Fx.spawn(world, uuid, start, 90, (f, w) -> {
				if (f.age == 1) {
					BlockDisplay d = f.addBlock(bd(Material.YELLOW_STAINED_GLASS), true);
					d.setGlowing(true); d.setGlowColorOverride(Color.fromRGB(0xFFE070));
				}
				for (Display d : f.displays)
					Fx.shape(d, new Vector3f(-0.25F), new Quaternionf().rotateXYZ(f.age * 0.4F, f.age * 0.3F, idx), new Vector3f(0.5F), 1);
				if (f.age == 20 + idx * 2) {
					Player tgt = null;
					double best = 1e9;
					for (Player p : nearbyPlayers(40)) {
						if (!BU.valid(p)) continue;
						if (only != null && !only.contains(p.getUniqueId())) continue;
						double d = p.getLocation().toVector().distanceSquared(f.pos);
						if (d < best) { best = d; tgt = p; }
					}
					if (tgt == null) { f.kill(); return; }
					f.vel = tgt.getLocation().toVector().add(new Vector(0, 1, 0)).subtract(f.pos).normalize().multiply(0.7);
					BU.sound(new Location(w, f.pos.getX(), f.pos.getY(), f.pos.getZ()), Sound.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1F, 1.8F);
				}
				if (f.age > 20) {
					w.spawnParticle(Particle.WAX_ON, f.pos.getX(), f.pos.getY(), f.pos.getZ(), 1, 0, 0, 0, 0);
					boolean burst = BU.solidAt(w, f.pos);
					for (Player p : BU.victims(w, BU.box(f.pos, 0.9, 0.9, 0.9))) {
						p.damage(6, BU.magic(mob));
						p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
						burst = true;
					}
					if (burst) {
						w.spawnParticle(Particle.END_ROD, f.pos.getX(), f.pos.getY(), f.pos.getZ(), 8, 0.2, 0.2, 0.2, 0.08);
						f.kill();
					}
				}
			});
		}
	}

	@Override
	protected void dropLoot(List<ItemStack> drops, Random r) {
		drops.add(HItem.CHRONO_GEAR.create(4 + r.nextInt(5)));
		drops.add(HItem.RADIANT_STONE.create(10 + r.nextInt(7)));
		drops.add(HItem.REWIND_HOURGLASS.create(1 + r.nextInt(2)));
		drops.add(HItem.BLESSING_SCROLL.create(3));
		if (r.nextFloat() < 0.3F) drops.add(HItem.CHRONO_DAGGER.create());
	}

	@Override
	protected int deathDuration() { return 150; } // ~2s 감속 + 5s 정지 + 사라짐

	@Override
	protected void onDeathStart() {
		deathU = 0;
		deathSpin = 1;
		BU.sound(mob.getLocation(), Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 2F, 0.6F);
	}

	@Override
	protected void poseDeath(float u) {
		deathU = u;
		// 0-0.28 톱니·시계가 느려지고 불이 꺼짐, 0.28-0.92 정지 홀드(~5초), 이후 사라짐
		float slow = u < 0.28F ? 1F - u / 0.28F : 0F;
		deathSpin = slow * slow;
		applyDeathLights(u);
		if (modelMode) poseModel(animTick, 1F);
		else if (u < 0.92F) poseParts(animTick);
		else super.poseDeath((u - 0.92F) / 0.08F);
	}

	private void applyDeathLights(float u) {
		int br = u < 0.20F ? Math.max(0, (int) (15 * (1F - u / 0.20F))) : 0;
		org.bukkit.entity.Display.Brightness dim = new org.bukkit.entity.Display.Brightness(br, br);
		for (Part p : new Part[]{mBody, mRing, mClock, mArmL, mArmR}) {
			if (p != null && p.display.isValid()) p.display.setBrightness(dim);
		}
		if (core != null && core.display.isValid()) core.display.setBrightness(dim);
		if (face != null && face.display.isValid()) face.display.setBrightness(dim);
		if (label != null && label.display instanceof org.bukkit.entity.TextDisplay td && u > 0.15F) {
			td.text(net.kyori.adventure.text.Component.empty());
		}
	}

	@Override
	protected void deathFx(int tick, float u) {
		org.bukkit.Location l = mob.getLocation();
		if (u < 0.28F && tick % 5 == 0) {
			l.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, l.getX(), l.getY() + 1.6, l.getZ(), 4, 0.5, 0.5, 0.5, 0.01);
		} else if (u > 0.92F && tick % 3 == 0) {
			l.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, l.getX(), l.getY() + 0.8, l.getZ(), 6, 0.4, 0.4, 0.4, 0.02);
		}
	}

}
