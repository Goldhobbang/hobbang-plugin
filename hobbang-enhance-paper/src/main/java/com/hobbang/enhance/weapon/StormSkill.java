package com.hobbang.enhance.weapon;

import com.hobbang.enhance.boss.BU;
import com.hobbang.enhance.boss.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 뇌운 전투도끼. 돌진 낙뢰 / 낙뢰 연쇄 / 풀강 이중 낙뢰는 "심판"과 수치를 공유하지 않는다 (아래 상수가 따로 있음).
 */
public final class StormSkill {
	private StormSkill() {}

	/** 돌진 낙뢰 · 연쇄 낙뢰 피해 (기본, ×강화 배율) */
	static final double DASH_DMG = 9.0, CHAIN_DMG = 6.0;
	/** 심판 낙뢰 1발 피해(×강화 배율) / 폭발 반경 / 삼지창 고정 피해 & 범위 */
	static final double JUDGE_DMG = 5.0, JUDGE_BLAST_R = 2.3, TRIDENT_DMG = 30.0, TRIDENT_AOE_R = 4.5;
	/** 삼지창 피해: 30 고정 */
	static double tridentDmg(int lv) { return TRIDENT_DMG; }
	/** 심판 범위 = 4 + 0.5 × bossSkillLv (기본 5 → 4, 20% 축소) */
	public static double judgeRadius(int lv) { return 4.0 + 0.5 * com.hobbang.enhance.item.EnhanceTier.bossSkillLv(lv); }
	/** 심판 첫 낙뢰 수 = 10 + 2 × (강화 − 10) → 10강 10발, 15강 20발, 20강 30발 */
	public static int judgeBolts(int lv) { return 10 + 2 * Math.max(0, lv - 10); }
	/** 심판 문양 아이템 모델 (리소스팩 assets/hobbang/items/fx/judgment_glyph.json) */
	static final org.bukkit.NamespacedKey GLYPH_MODEL = new org.bukkit.NamespacedKey("hobbang", "fx/judgment_glyph");
	/** 심판 표식이 다 커지는 시간(틱, 40=2.0초), 해금(10강) 이후 강화당 0.1초(2틱) 감소, 최소 20틱(1.0초) */
	static final int MARK = 40, MARK_MIN = 20, SPREAD = 12;
	public static int judgeMarkTicks(int lv) { return Math.max(MARK_MIN, MARK - 2 * Math.max(0, lv - 10)); }

	/** 낙뢰 연쇄 마릿수: 10강 1마리부터 강화마다 +1 (15강 6마리) */
	static int chain(int lv) {
		return lv >= 10 ? lv - 9 : 0;
	}

	// ───────── 돌진 ─────────
	static void dash(Player p, int lv, double mult, double rad, int tier) {
		World sl = p.getWorld();
		Vector look = p.getLocation().getDirection();
		p.setVelocity(new Vector(look.getX() * 2.4, Math.max(0.25, look.getY() * 1.2), look.getZ() * 2.4));
		p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, false));
		Location pl = p.getLocation();
		BU.sound(pl, Sound.ENTITY_BREEZE_JUMP, SoundCategory.PLAYERS, 1.5F, 1.2F);
		BU.sound(pl, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.6F, 1.6F);
		WeaponFx.stormTrail(sl, p, 7);
		sl.spawnParticle(Particle.GUST, pl.getX(), pl.getY() + 0.5, pl.getZ(), 3, 0.3, 0.3, 0.3, 0);

		// 돌진하는 동안, 이동 방향 근처의 적만 기억 (범위는 기존 대비 약 40% 축소)
		final Set<LivingEntity> path = new LinkedHashSet<>();
		Vector dashDir = look.clone();
		if (dashDir.lengthSquared() < 1e-6) dashDir = new Vector(0, 0, 1);
		else dashDir.normalize();
		final Vector dir = dashDir;
		final double reachR = (2.2 + rad * 0.7) * WeaponListener.RANGE_CUT;   // 강화당 범위 증가 30% 축소
		for (int i = 0; i <= 7; i++) {
			Runnable sample = () -> {
				if (p.isDead() || !p.isOnline()) return;
				path.addAll(WeaponListener.ahead(p, reachR, dir, WeaponListener.AHEAD_CONE));
			};
			if (i == 0) sample.run();
			else Fx.later(i, sample);
		}
		// 돌진이 끝난 뒤(7틱) 0.3초(6틱) 후 낙뢰폭발. 풀강은 그 낙뢰를 한 번 더 (4틱 뒤)
		Fx.later(7, () -> {
			if (p.isDead() || !p.isOnline()) return;
			Vector end = p.getLocation().toVector();
			List<LivingEntity> chained = new ArrayList<>();
			int n = chain(lv);
			if (n > 0) {
				List<LivingEntity> far = WeaponListener.ahead(p, 10 * WeaponListener.RANGE_CUT, dir, WeaponListener.AHEAD_CONE);
				far.removeAll(path);
				far.sort(java.util.Comparator.comparingDouble(t -> t.getLocation().toVector().distanceSquared(end)));
				for (int k = 0; k < Math.min(n, far.size()); k++) chained.add(far.get(k));
			}
			int rounds = lv >= 15 ? 2 : 1;
			for (int r = 0; r < rounds; r++) {
				final int round = r;
				Fx.later(6 + 4 * r, () -> {
					int hit = 0;
					for (LivingEntity t : path) if (alive(t)) { bolt(p, t, DASH_DMG * mult, tier); hit++; }
					for (LivingEntity t : chained) if (alive(t)) { bolt(p, t, CHAIN_DMG * mult, tier); hit++; }
					if (round == 0 && p.isOnline())
						p.sendActionBar(Component.text("⚡ 낙뢰폭발 " + path.size() + "마리" + (chained.isEmpty() ? "" : " · 연쇄 " + chained.size() + "마리") + (rounds > 1 ? " ×2" : ""), NamedTextColor.AQUA));
				});
			}
		});
	}

	private static boolean alive(LivingEntity t) {
		return t != null && t.isValid() && !t.isDead();
	}

	/** 낙뢰폭발 1발: 번개 + 충격 연출 + 그 대상에게 피해 */
	private static void bolt(Player p, LivingEntity t, double dmg, int tier) {
		Location tl = t.getLocation();
		World w = tl.getWorld();
		BU.lightning(w, tl.getX(), tl.getY(), tl.getZ());
		WeaponFx.stormImpact(w, p, tl.toVector(), tier);
		WeaponListener.skillHit(t, p, dmg);
	}

	// ───────── 심판 (10강~, 웅크리고 우클릭) ─────────
	/** 시선 직선 위에 표식이 커지다가 그 안에서 낙뢰폭발이 랜덤하게 lv번. 15강은 마지막에 삼지창.
	 * 최대 사거리 50칸, 심판(10강)부터 강화당 +10칸. 블록·엔티티에 맞으면 그 히트 지점(시선 직선 위), 아니면 사거리 끝. */
	static void judgment(Player p, int lv, double mult, double rad, int tier) {
		World sl = p.getWorld();
		Location eye = p.getEyeLocation();
		Vector dir = eye.getDirection();
		Vector dirN = dir.clone();
		if (dirN.lengthSquared() < 1e-6) dirN = new Vector(0, 0, 1);
		else dirN.normalize();
		final double reach = 50.0 + 10.0 * Math.max(0, lv - 10);   // 기본 50, 심판(10강)부터 강화당 +10
		Vector eyeV = eye.toVector();
		// 표식은 시선 직선 위에만: 엔티티 중심·지면 스냅으로 빼지 않는다. 블록이면 살짝 앞(같은 직선)에.
		RayTraceResult er = sl.rayTraceEntities(eye, dirN, reach, 0.0, e ->
			e instanceof LivingEntity le && e != p && !le.isDead()
				&& !(e instanceof ArmorStand as && as.isMarker()));
		RayTraceResult br = sl.rayTraceBlocks(eye, dirN, reach, FluidCollisionMode.NEVER, true);
		double eDist = (er != null && er.getHitEntity() instanceof LivingEntity)
			? er.getHitPosition().distance(eyeV) : Double.POSITIVE_INFINITY;
		double bDist = br != null ? br.getHitPosition().distance(eyeV) : Double.POSITIVE_INFINITY;
		Vector c;
		if (eDist <= bDist && eDist != Double.POSITIVE_INFINITY) {
			c = er.getHitPosition().clone();
		} else if (br != null) {
			c = br.getHitPosition().clone().subtract(dirN.clone().multiply(0.15));
		} else {
			c = eyeV.clone().add(dirN.clone().multiply(reach));
		}
		final Vector center = c;
		final int skillLv = com.hobbang.enhance.item.EnhanceTier.bossSkillLv(lv);
		final double R = judgeRadius(lv);
		final int markTicks = judgeMarkTicks(lv);   // 10강 24틱(1.2초) → 강화당 -4틱(-0.2초), 최소 8틱(0.4초)
		final int bolts = judgeBolts(lv);   // t413: 10강 10번 … 15강 20번 (+2/강화)
		Location cl = new Location(sl, c.getX(), c.getY(), c.getZ());
		BU.sound(cl, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.8F, 1.5F);
		BU.sound(cl, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2F, 1.8F);

		// t413: 파티클 문양 대신 거대한 문양 하나(아이템 디스플레이, 평면 쿼드 텍스처)가 표식 높이에 눕혀진 채
		// 선딜 동안 지름 0 → 2R 로 자라며 천천히 돈다. 낙뢰(·15강 삼지창)가 끝나면 줄어들며 사라진다.
		final int strikeEnd = markTicks + 2 + SPREAD + (lv >= 15 ? 8 + 8 : 4);
		final int life = strikeEnd + 8;
		final ItemStack glyph = new ItemStack(Material.PAPER);
		glyph.editMeta(m -> m.setItemModel(GLYPH_MODEL));
		final double dir0 = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
		Fx.spawn(sl, p.getUniqueId(), center.clone().add(new Vector(0, 0.06, 0)), life, (fx, w) -> {
			int a = fx.age;
			if (a == 1) {
				ItemDisplay d = fx.addItem(glyph, ItemDisplay.ItemDisplayTransform.FIXED, true);
				d.setViewRange(4.0F);   // 멀리(공중 표식 50칸+)에서도 보이게
				d.setShadowRadius(0F);
				Fx.shape(d, new Vector3f(), new Quaternionf().rotationY((float) dir0), new Vector3f(0.01F, 1F, 0.01F), 0);
				return;
			}
			if (fx.displays.isEmpty() || a % 2 != 0) return;
			Display d = fx.displays.getFirst();
			double u = Math.min(1.0, (a - 1) / (double) markTicks);
			double grow = 1 - (1 - u) * (1 - u) * (1 - u);   // 처음엔 빠르게, 끝에서 감속
			double shrink = a <= strikeEnd ? 1.0 : Math.max(0.0, 1.0 - (a - strikeEnd) / 7.0);
			float dia = (float) (2 * R * grow * shrink);
			float rot = (float) (dir0 + a * 0.035);   // 초당 약 40° 회전
			Fx.shape(d, new Vector3f(), new Quaternionf().rotationY(rot), new Vector3f(Math.max(0.01F, dia), 1F, Math.max(0.01F, dia)), 2);
			// 가장자리에 소수의 강조 파티클만
			if (a % 4 == 0 && a <= strikeEnd) {
				double rr = R * grow;
				for (int k = 0; k < 4; k++) {
					double ang = rot + k * Math.PI / 2;
					w.spawnParticle(Particle.ELECTRIC_SPARK, center.getX() + Math.cos(ang) * rr * 0.92, center.getY() + 0.3, center.getZ() + Math.sin(ang) * rr * 0.92, 2, 0.05, 0.25, 0.05, 0.01);
				}
			}
			if (a % 6 == 0 && a <= markTicks) BU.sound(cl, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8F, 0.6F + (float) u);
		});

		// 낙뢰: 표식이 다 그려지면 SPREAD 틱 동안 랜덤 자리에 쏟아진다
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (int i = 0; i < bolts; i++) {
			int tick = markTicks + 2 + (int) (i * (double) SPREAD / bolts);
			Fx.later(tick, () -> {
				double a = rnd.nextDouble() * Math.PI * 2, r = R * Math.sqrt(rnd.nextDouble());
				// t399: 낙뢰는 언제나 심판 표식 위(표식 원 안, 표식과 같은 높이)에만 — 바닥 스냅 없음
				Vector at = new Vector(center.getX() + Math.cos(a) * r, center.getY(), center.getZ() + Math.sin(a) * r);
				blast(p, sl, at, JUDGE_DMG * mult);
			});
		}
		if (lv >= 15) Fx.later(markTicks + 2 + SPREAD + 8, () -> trident(p, sl, center, R, lv));
	}

	/** 심판 낙뢰폭발 1발 (가벼운 연출 — 번개 이펙트 + 폭발 + 반경 내 모든 적) */
	private static void blast(Player p, World sl, Vector at, double dmg) {
		BU.lightning(sl, at.getX(), at.getY(), at.getZ());
		sl.spawnParticle(Particle.EXPLOSION, at.getX(), at.getY() + 0.3, at.getZ(), 1, 0, 0, 0, 0);
		sl.spawnParticle(Particle.ELECTRIC_SPARK, at.getX(), at.getY() + 0.5, at.getZ(), 22, 0.7, 0.6, 0.7, 0.25);
		sl.spawnParticle(Particle.BLOCK, at.getX(), at.getY() + 0.2, at.getZ(), 10, 0.5, 0.1, 0.5, 0.1, Material.LIGHT_BLUE_CONCRETE_POWDER.createBlockData());
		BU.sound(new Location(sl, at.getX(), at.getY(), at.getZ()), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5F, 1.5F);
		for (LivingEntity t : WeaponListener.enemiesAt(sl, at, JUDGE_BLAST_R, 3.0, p)) WeaponListener.skillHit(t, p, dmg);
	}

	/** 15강: 위에서 바닐라 삼지창(아이템 스프라이트, 대형)이 꽂힌다 — 10 피해 + 퍼지며 사라지는 원 충격파 */
	private static void trident(Player p, World sl, Vector c, double R, int lv) {
		final double tDmg = tridentDmg(lv);
		final float S = 12F;                       // FIXED 스프라이트는 기본 0.5배 → 약 8.5칸 길이
		final double tip = 0.332 * S;              // 스프라이트 중심 → 끝(삼지 끝)까지
		final float drop = 24F;
		Location cl = new Location(sl, c.getX(), c.getY(), c.getZ());
		Quaternionf rot = new Quaternionf().rotationZ((float) Math.toRadians(-135));   // 끝이 오른쪽 위 → 아래
		BU.sound(cl, Sound.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 2.0F, 0.6F);
		Fx.spawn(sl, p.getUniqueId(), c.clone().add(new Vector(0, tip, 0)), 34, (fx, w) -> {
			if (fx.age == 1) {
				ItemDisplay d = fx.addItem(new ItemStack(Material.TRIDENT), ItemDisplay.ItemDisplayTransform.FIXED, true);
				d.setBillboard(Display.Billboard.VERTICAL);
				d.setGlowing(true);
				d.setGlowColorOverride(Color.fromRGB(0x9FEFFF));
				Fx.shape(d, new Vector3f(0, drop, 0), rot, new Vector3f(S), 0);
			} else if (fx.age == 2) {
				Fx.shape(fx.displays.getFirst(), new Vector3f(0, 0, 0), rot, new Vector3f(S), 4);   // 4틱에 꽂힘
			} else if (fx.age == 7) {
				BU.lightning(w, c.getX(), c.getY(), c.getZ());
				BU.sound(cl, Sound.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 2.5F, 0.7F);
				BU.sound(cl, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5F, 0.6F);
				w.spawnParticle(Particle.EXPLOSION_EMITTER, c.getX(), c.getY() + 0.5, c.getZ(), 1, 0, 0, 0, 0);
				w.spawnParticle(Particle.ELECTRIC_SPARK, c.getX(), c.getY() + 0.8, c.getZ(), 60, 1.2, 0.8, 1.2, 0.4);
				Set<LivingEntity> targets = new LinkedHashSet<>();
				// 삼지창 엔티티 직격 판정 (필중): 삼지창 낙하 기둥(반경 1.5, 높이 9칸) 내 대상
				for (Entity en : w.getNearbyEntities(org.bukkit.util.BoundingBox.of(c.clone().add(new Vector(0, 4.0, 0)), 1.5, 5.0, 1.5))) {
					if (en instanceof LivingEntity le && en != p && !(en instanceof Player) && !le.isDead() && !(en instanceof ArmorStand as && as.isMarker())) {
						targets.add(le);
					}
				}
				// 박힌 지점 기준 주변 범위 피해 (직격 대상은 Set으로 중복 제외)
				targets.addAll(WeaponListener.enemiesAt(w, c, TRIDENT_AOE_R, 4.0, p));
				for (LivingEntity t : targets) WeaponListener.skillHit(t, p, tDmg);
				shock(p, w, c, R);
			} else if (fx.age == 26) {
				Fx.shape(fx.displays.getFirst(), new Vector3f(0, 0, 0), rot, new Vector3f(0), 6);   // 서서히 사라짐
			}
		});
	}

	/** 꽂히는 순간 바닥에서 원이 커지며 옅어지는 충격파 */
	private static void shock(Player p, World sl, Vector c, double R) {
		final int T = 14;
		Fx.spawn(sl, p.getUniqueId(), c, T + 1, (fx, w) -> {
			double u = fx.age / (double) T;
			double r = R * 1.15 * u;
			float size = (float) (2.4 * (1 - u) + 0.4);                     // 점점 작아지며 사라짐
			int pts = Math.max(14, (int) (80 * (1 - 0.6 * u)));
			Particle.DustOptions d = BU.dust(0xBFF4FF, size);
			for (int i = 0; i < pts; i++) {
				double a = Math.PI * 2 * i / pts;
				w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * r, c.getY() + 0.12, c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, d);
			}
			if (fx.age % 3 == 0) w.spawnParticle(Particle.CLOUD, c.getX() + Math.cos(fx.age) * r, c.getY() + 0.2, c.getZ() + Math.sin(fx.age) * r, 2, 0.2, 0.05, 0.2, 0.02);
		});
	}
}
