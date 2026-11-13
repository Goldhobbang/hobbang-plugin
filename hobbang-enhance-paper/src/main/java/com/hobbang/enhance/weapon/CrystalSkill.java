package com.hobbang.enhance.weapon;

import com.hobbang.enhance.boss.BU;
import com.hobbang.enhance.item.HItem;
import com.hobbang.enhance.boss.ColossusBoss;
import com.hobbang.enhance.boss.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 수정 대검: (3강~) 우클릭 관통 베기, (7강~) 거대 수정 대검 낙하 + 풀강(15강) 웅크리고 우클릭 수정 가시(바라보는 방향으로 4갈래) */
final class CrystalSkill {
	private CrystalSkill() {}

	/** 관통 베기 기본 거리(3강) — 실제 거리는 EnhanceTier.crystalPierceRange(lv) = 10 + (lv − 3) */
	static final double PIERCE_RANGE = 10.0, SWORD_DMG = 20.0;

	/** 수정이 뻗는 방향 수: 13강 4 / 10강 3 / 그 미만 1 (t390: 전방위 8갈래 취소, 원래 전방 부채꼴로) */
	static int lines(int lv) {
		return lv >= 13 ? 4 : lv >= 10 ? 3 : 1;
	}
	/** t388: 관통 베기 뒤 거대 대검 낙하(꽂기)는 7강부터 */
	public static final int PLUNGE_LV = 7;

	static void spikes(Player p, int lv, double mult, int tier) {
		World sl = p.getWorld();
		// t390: 바라보는 방향 그대로(위·아래 포함 3D)로 뻗는다 — 위를 보면 공중으로, 아래를 보면 앞쪽 땅으로. (보스 가시는 그대로 지면)
		Vector dir = p.getLocation().getDirection();
		if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
		dir.normalize();
		int n = lines(lv), spikes = Math.max(2, (int) Math.round((8 + lv / 5) * WeaponListener.RANGE_CUT * 0.5));   // 길이 추가 50% 축소
		Vector o = p.getLocation().toVector();
		java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();   // 여러 갈래·가시에 같은 대상은 시전당 1번만
		for (int j = 0; j < n; j++) {
			double off = Math.toRadians((j - (n - 1) / 2.0) * 22.0 * WeaponListener.RANGE_CUT);   // 갈래 각도 약 13° (원래대로)
			Vector d = dir.clone().rotateAroundY(off);   // 좌우로만 벌어지고 위아래 각도는 시선 그대로
			for (int i = 1; i <= spikes; i++) {
				Vector at = o.clone().add(d.clone().multiply(i * 1.2));
				// 막힌 블록 안이면: 한 칸 위가 비었으면 그 위로 올려 계속(턱 넘기), 아니면 그 갈래는 여기서 멈춘다
				if (!passable(sl, at)) {
					Vector up = at.clone().add(new Vector(0, 1, 0));
					if (!passable(sl, up)) break;
					at = new Vector(at.getX(), Math.floor(at.getY()) + 1, at.getZ());
				}
				ColossusBoss.spike(sl, at, i, (tier >= 1 ? 1.35F : 1.1F) + i * 0.04F, p.getUniqueId(), p, 10 * mult, hit);
			}
		}
		WeaponFx.crystalCast(sl, p, tier);
	}

	/**
	 * 우클릭(기본 스킬, 3강 해금): 바라보는 방향(위·아래 포함 전방위)으로 최대 10칸(3강) + 강화당 1칸 순간이동하며
	 * 지나가는 모든 적을 베고(관통), 벤 적이 없으면(실패) 그냥 그 지점으로 순간이동.
	 * 벽은 뚫지 않고 그 앞에서 멈춘다.
	 * 7강부터 벤 모든 적 위로 거대한 수정 대검이 떨어져 20 피해.
	 */
	/** @return 관통 베기(또는 이후 대검 낙하)로 적을 처치했으면 true. 처치 시 쿨 초기화. */
	static boolean pierce(Player p, int lv, double mult, int tier) {
		World sl = p.getWorld();
		Location start = p.getLocation();
		Location eye = p.getEyeLocation();
		Vector dir = eye.getDirection();
		if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
		dir.normalize();
		Vector eyeV = eye.toVector();
		final double range = com.hobbang.enhance.item.EnhanceTier.crystalPierceRange(lv);   // 3강 10칸 … 15강 22칸
		double eyeH = p.getEyeHeight();
		if (eyeH < 0.8) eyeH = 1.62;

		// 시선 경로에서 벽에 막히기 전까지. 도착 시 발·몸·머리가 비어 있어야 함
		double clear = 0;
		for (double d = 0.5; d <= range; d += 0.5) {
			Vector feet = eyeV.clone().add(dir.clone().multiply(d)).subtract(new Vector(0, eyeH, 0));
			if (!passable(sl, feet) || !passable(sl, feet.clone().add(new Vector(0, 1.0, 0))) || !passable(sl, feet.clone().add(new Vector(0, 1.8, 0)))) break;
			clear = d;
		}
		// 베기 판정: 시선 경로(3D 전방위) 옆 약 1칸(기존 1.6의 60%) 안의 적 전부 — 뚫고 지나가며 벤다
		List<LivingEntity> hits = new ArrayList<>();
		double farthest = 0;
		Vector dd = dir;
		for (LivingEntity t : WeaponListener.around(p, range + 2)) {
			Vector to = t.getLocation().toVector().add(new Vector(0, t.getHeight() * 0.5, 0)).subtract(eyeV);
			double along = to.dot(dd);
			double side = to.clone().subtract(dd.clone().multiply(along)).length();
			if (along < -0.5 || along > clear + 1.5 || side > 1.6 * WeaponListener.RANGE_CUT + t.getWidth() / 2) continue;
			hits.add(t);
			farthest = Math.max(farthest, along);
		}
		hits.sort(Comparator.comparingDouble(t -> t.getLocation().toVector().add(new Vector(0, t.getHeight() * 0.5, 0)).distanceSquared(eyeV)));
		boolean success = !hits.isEmpty();
		double go = success ? Math.min(clear, farthest + 1.2) : clear;   // 성공: 적을 지나쳐 서고, 실패: 갈 수 있는 곳(최대 range 칸)까지 순간이동

		// 궤적 연출 (시선 경로)
		for (double d = 0; d <= go; d += 0.6) {
			Vector v = eyeV.clone().add(dir.clone().multiply(d));
			sl.spawnParticle(Particle.END_ROD, v.getX(), v.getY(), v.getZ(), 1, 0.05, 0.3, 0.05, 0.02);
			if (((int) (d / 0.6)) % 3 == 0) sl.spawnParticle(Particle.SWEEP_ATTACK, v.getX(), v.getY(), v.getZ(), 1, 0, 0, 0, 0);
			sl.spawnParticle(Particle.BLOCK, v.getX(), v.getY(), v.getZ(), 2, 0.2, 0.1, 0.2, 0.05, Material.AMETHYST_BLOCK.createBlockData());
		}
		BU.sound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.4F, 0.6F);
		BU.sound(start, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.4F, 1.4F);

		// 베기 피해 (뚫고 지나가므로 전원). 처치하면 쿨 즉시 초기화
		boolean killed = false;
		for (LivingEntity t : hits) {
			WeaponListener.skillHit(t, p, 12.0 * mult);
			if (dead(t)) killed = true;
			Location tl = t.getLocation();
			sl.spawnParticle(Particle.CRIT, tl.getX(), tl.getY() + t.getHeight() * 0.5, tl.getZ(), 14, 0.3, 0.4, 0.3, 0.3);
		}
		if (go > 0.4) {
			Vector feet = eyeV.clone().add(dir.clone().multiply(go)).subtract(new Vector(0, eyeH, 0));
			Location to = new Location(sl, feet.getX(), feet.getY(), feet.getZ(), start.getYaw(), start.getPitch());
			p.teleport(to, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
			p.setFallDistance(0);
			BU.sound(to, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8F, 1.6F);
		}
		p.sendActionBar(Component.text(success ? "✦ 관통 베기 " + hits.size() + "마리" : "✦ 베기 실패 — 순간이동", NamedTextColor.LIGHT_PURPLE));

		// 벤 모든 적 위로 거대 수정 대검 낙하 (대상마다 2틱씩 엇갈려) — t388: 7강부터
		if (lv >= PLUNGE_LV) for (int i = 0; i < hits.size(); i++) {
			LivingEntity t = hits.get(i);
			Fx.later(8 + 2 * i, () -> {
				if (t.isValid() && !t.isDead() && p.isOnline()) fallingSword(p, t);
			});
		}
		return killed;
	}

	private static boolean dead(LivingEntity t) {
		return t == null || !t.isValid() || t.isDead() || t.getHealth() <= 0;
	}

	private static void clearCrystalCooldown(Player p) {
		if (p != null && p.isOnline()) p.setCooldown(HItem.CRYSTAL_GREATSWORD.cooldownGroup(), 0);
	}

	private static boolean passable(World w, Vector v) {
		if (!w.isChunkLoaded(v.getBlockX() >> 4, v.getBlockZ() >> 4)) return false;
		Block b = w.getBlockAt(v.getBlockX(), v.getBlockY(), v.getBlockZ());
		return b.isPassable();
	}

	/** 블록 디스플레이로 만든 거대 수정 대검(끝이 아래)이 대상 머리 위에서 내리꽂힌다 → 20 피해 */
	private static void fallingSword(Player p, LivingEntity t) {
		World sl = t.getWorld();
		Vector at = t.getLocation().toVector();
		final float H = 18F;
		final List<Part> parts = new ArrayList<>();
		Location al = new Location(sl, at.getX(), at.getY(), at.getZ());
		Fx.spawn(sl, p.getUniqueId(), at, 30, (fx, w) -> {
			if (fx.age == 1) {
				BU.sound(al, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.PLAYERS, 1.6F, 0.5F);
				// 날: 수정 블록 + 가장자리 유리 + 끝(45° 마름모), 가드: 퍼퍼, 손잡이/폼멜 (원점 = 칼끝)
				addPart(fx, parts, Material.AMETHYST_BLOCK, 0.9F, 5.6F, 0.28F, 1.1F, H);
				addPart(fx, parts, Material.PURPLE_STAINED_GLASS, 1.15F, 5.0F, 0.12F, 1.4F, H);
				addTip(fx, parts, H);
				addPart(fx, parts, Material.PURPUR_BLOCK, 2.8F, 0.4F, 0.5F, 6.7F, H);
				addPart(fx, parts, Material.CRYING_OBSIDIAN, 0.3F, 1.5F, 0.3F, 7.1F, H);
				addPart(fx, parts, Material.AMETHYST_BLOCK, 0.6F, 0.5F, 0.6F, 8.6F, H);
				for (var d : fx.displays) { d.setGlowing(true); d.setGlowColorOverride(org.bukkit.Color.fromRGB(0xC77DFF)); }
			} else if (fx.age == 2) {
				for (Part pt : parts) Fx.shape(pt.d(), pt.t(), pt.q(), pt.s(), 4);   // 4틱에 내리꽂힘
			} else if (fx.age == 7) {
				if (t.isValid() && !t.isDead()) {
					WeaponListener.skillHit(t, p, SWORD_DMG);
					if (dead(t)) clearCrystalCooldown(p);
				}
				w.spawnParticle(Particle.EXPLOSION, at.getX(), at.getY() + 0.4, at.getZ(), 2, 0.3, 0.1, 0.3, 0);
				w.spawnParticle(Particle.BLOCK, at.getX(), at.getY() + 0.3, at.getZ(), 40, 0.8, 0.2, 0.8, 0.15, Material.AMETHYST_BLOCK.createBlockData());
				w.spawnParticle(Particle.END_ROD, at.getX(), at.getY() + 1.0, at.getZ(), 16, 0.5, 0.9, 0.5, 0.12);
				BU.sound(al, Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 2.0F, 0.5F);
				BU.sound(al, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0F, 1.2F);
				BU.sound(al, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.0F, 0.7F);
			} else if (fx.age == 22) {
				for (Part pt : parts) Fx.shape(pt.d(), new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(0), 6);
			}
		});
	}

	private record Part(BlockDisplay d, Vector3f t, Quaternionf q, Vector3f s) {}

	/** 칼끝 위 y0 에서 시작하는 직육면체 (x 가운데 정렬). 처음엔 lift 만큼 위에 둔다 */
	private static void addPart(Fx fx, List<Part> parts, Material m, float w, float h, float d, float y0, float lift) {
		BlockDisplay b = fx.addBlock(m.createBlockData(), true);
		Vector3f t = new Vector3f(-w / 2, y0, -d / 2);
		Vector3f sc = new Vector3f(w, h, d);
		Fx.shape(b, new Vector3f(t).add(0, lift, 0), new Quaternionf(), sc, 0);
		parts.add(new Part(b, t, new Quaternionf(), sc));
	}

	private static void addTip(Fx fx, List<Part> parts, float lift) {
		float a = 0.9F / 1.4142F;   // 한 변을 45° 로 눕히면 폭·높이 0.9 의 마름모
		BlockDisplay b = fx.addBlock(Material.AMETHYST_BLOCK.createBlockData(), true);
		Quaternionf q = new Quaternionf().rotationZ((float) Math.PI / 4);
		Vector3f sc = new Vector3f(a, a, 0.28F);
		Vector3f t = new Vector3f(0, 0.45F, 0).sub(new Vector3f(a / 2, a / 2, 0.14F).rotate(q));
		Fx.shape(b, new Vector3f(t).add(0, lift, 0), q, sc, 0);
		parts.add(new Part(b, t, q, sc));
	}
}
