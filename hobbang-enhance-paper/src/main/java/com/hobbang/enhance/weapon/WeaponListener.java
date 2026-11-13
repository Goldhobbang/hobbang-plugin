package com.hobbang.enhance.weapon;

import com.hobbang.enhance.boss.BU;
import com.hobbang.enhance.boss.ColossusBoss;
import com.hobbang.enhance.boss.Fx;
import com.hobbang.enhance.boss.TimeStun;
import com.hobbang.enhance.item.HItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** 보스 무기 4종: 우클릭 스킬 / 근접 타격 연출 */
public final class WeaponListener implements Listener {
	private static boolean skill = false;

	/** 스킬 피해 (근접 타격 연출이 다시 터지지 않도록 표시) */
	public static void skillDamage(LivingEntity e, Player p, double dmg) {
		boolean prev = skill;
		skill = true;
		try {
			e.damage(dmg, BU.player(p));
		} finally {
			skill = prev;
		}
	}

	/** 스킬 피해 + 무적 시간을 풀어 연속 타격이 다 들어가게 (이중 낙뢰·심판·톱니 일격 용) */
	public static void skillHit(LivingEntity e, Player p, double dmg) {
		e.setNoDamageTicks(0);
		skillDamage(e, p, dmg);
	}

	/** 중심 c 에서 가로 hr · 세로 vr 안의 적 (플레이어·마커 제외) */
	static List<LivingEntity> enemiesAt(World w, Vector c, double hr, double vr, Player self) {
		List<LivingEntity> out = new ArrayList<>();
		for (Entity e : w.getNearbyEntities(BoundingBox.of(c, hr, vr, hr))) {
			if (e instanceof LivingEntity le && e != self && !(e instanceof Player) && !le.isDead() && !(e instanceof org.bukkit.entity.ArmorStand as && as.isMarker())
				&& Math.hypot(le.getLocation().getX() - c.getX(), le.getLocation().getZ() - c.getZ()) <= hr + le.getWidth() / 2) out.add(le);
		}
		return out;
	}

	static List<LivingEntity> around(Player p, double r) {
		List<LivingEntity> out = new ArrayList<>();
		BoundingBox box = p.getBoundingBox().expand(r);
		for (Entity e : p.getWorld().getNearbyEntities(box)) {
			if (e instanceof LivingEntity le && e != p && !(e instanceof Player) && !le.isDead() && !(e instanceof org.bukkit.entity.ArmorStand as && as.isMarker()))
				out.add(le);
		}
		return out;
	}

	/** 이동 방향(거의 정지면 시선)에서 coneDeg 이내·거리 r 안의 적. 바로 옆(0.5칸)은 포함한다. */
	static List<LivingEntity> ahead(Player p, double r, Vector dir, double coneDeg) {
		Vector move = dir == null ? new Vector() : dir.clone();
		if (move.lengthSquared() < 1e-6) {
			move = p.getVelocity();
			if (move.lengthSquared() < 0.04) move = p.getLocation().getDirection();
		}
		if (move.lengthSquared() < 1e-6) move = new Vector(0, 0, 1);
		else move.normalize();
		Vector origin = p.getLocation().toVector().add(new Vector(0, p.getHeight() * 0.5, 0));
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity t : around(p, r)) {
			Vector to = t.getLocation().toVector().add(new Vector(0, t.getHeight() * 0.5, 0)).subtract(origin);
			if (to.lengthSquared() < 0.25) { out.add(t); continue; }
			if (Math.toDegrees(to.angle(move)) <= coneDeg) out.add(t);
		}
		return out;
	}

	static final double AHEAD_CONE = 55;
	/** 공격 대상 범위를 약 40% 줄일 때 쓰는 배율 */
	static final double RANGE_CUT = 0.6;

	@EventHandler(priority = EventPriority.HIGH)
	public void onUse(PlayerInteractEvent e) {
		if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		if (e.useItemInHand() == Event.Result.DENY && e.getAction() == Action.RIGHT_CLICK_BLOCK) return;   // 강화대 등에서 처리됨
		ItemStack stack = e.getItem();
		HItem h = HItem.of(stack);
		if (h == null) return;
		Player p = e.getPlayer();
		if (TimeStun.isActive(p)) return;
		if (stack.getType() == Material.KNOWLEDGE_BOOK) {   // 재료 아이템: 지식의 책 사용(소모) 방지
			e.setUseItemInHand(Event.Result.DENY);
			return;
		}
		if (!h.isWeapon()) return;
		// 상호작용 블록(상자 등) 우클릭은 원래 동작 유지 (웅크리면 스킬)
		Block cb = e.getClickedBlock();
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK && cb != null && cb.getType().isInteractable() && !p.isSneaking()) {
			if (h.base == Material.NETHERITE_AXE) e.setUseItemInHand(Event.Result.DENY);   // 망치·전투도끼: 껍질 벗기기 막기
			return;
		}
		e.setUseItemInHand(Event.Result.DENY);   // 도끼 껍질 벗기기 등 막기
		if (p.getCooldown(h.cooldownGroup()) > 0) return;
		EquipmentSlot slot = e.getHand() == null ? EquipmentSlot.HAND : e.getHand();
		World sl = p.getWorld();
		int tier = WeaponFx.tierOf(stack);
		final int lv = com.hobbang.enhance.item.ItemUtil.getLevel(stack);
		if (!com.hobbang.enhance.item.EnhanceTier.bossSkillUnlocked(lv)) {   // t384: 모든 보스 무기 기본 스킬은 3강 해금
			p.sendActionBar(Component.text("✖ 스킬은 +" + com.hobbang.enhance.item.EnhanceTier.BOSS_SKILL_UNLOCK + "강부터 쓸 수 있습니다 (현재 +" + lv + ")", NamedTextColor.RED));
			return;
		}
		final double mult = com.hobbang.enhance.item.EnhanceTier.bossDamageMult(lv), rad = com.hobbang.enhance.item.EnhanceTier.bossRadiusAdd(lv);
			int cdMult = 1;
		switch (h) {
			case SMITH_HAMMER -> {
				HammerSkill.cast(p, stack, lv, mult, rad, tier);
				damage(p, slot, 2);
			}
			case CRYSTAL_GREATSWORD -> {
				if (p.isSneaking() && lv >= 15) CrystalSkill.spikes(p, lv, mult, tier);   // 15강~ 웅크리고 우클릭: 수정 가시
				else if (CrystalSkill.pierce(p, lv, mult, tier)) cdMult = 0;   // 3강~ 우클릭: 관통 베기 (거리 10 + 강화당 1칸) — 처치 시 쿨 즉시 초기화
				damage(p, slot, 2);
			}
			case STORMCALLER -> {
				if (p.isSneaking() && lv >= 10) {   // 10강~: 심판 (낙뢰가 쏟아지므로 재사용 시간 2배)
					StormSkill.judgment(p, lv, mult, rad, tier);
					cdMult = 2;
				} else StormSkill.dash(p, lv, mult, rad, tier);
				damage(p, slot, 1);
			}
			case CHRONO_DAGGER -> {
				if (p.isSneaking() && lv >= 15) ChronoSkill.timer(p, tier);   // 풀강: 5초 타이머
				else ChronoSkill.slashAndStop(p, lv, mult, tier);
				damage(p, slot, 2);
			}
			default -> { return; }
		}
		p.setCooldown(h.cooldownGroup(), (int) Math.round(h.cooldown * com.hobbang.enhance.item.EnhanceTier.bossCooldownMult(lv)) * cdMult);
		p.swingHand(slot);
	}

	private static void damage(Player p, EquipmentSlot slot, int amount) {
		if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
		p.damageItemStack(slot, amount);
	}

	/** 근접 타격 시 테마 연출 (+ 크로노 낫 구속) */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onMelee(EntityDamageByEntityEvent e) {
		if (skill || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
		if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) return;
		if (TimeStun.isActive(p)) return;
		ItemStack w = p.getInventory().getItemInMainHand();
		HItem h = HItem.of(w);
		if (h == null || !h.isWeapon()) return;
		WeaponFx.Theme theme = switch (h) {
			case SMITH_HAMMER -> WeaponFx.Theme.ABYSS;
			case CRYSTAL_GREATSWORD -> WeaponFx.Theme.CRYSTAL;
			case STORMCALLER -> WeaponFx.Theme.STORM;
			default -> WeaponFx.Theme.CHRONO;
		};
		if (h == HItem.CHRONO_DAGGER) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
		WeaponFx.onHit(w, target, theme);
	}
}
