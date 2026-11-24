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

	public static boolean isSkillDamage() { return skill; }

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
		if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
			Player p = e.getPlayer();
			if (p.isSneaking() && HItem.of(e.getItem()) == HItem.ONEPUNCH_GLOVE && !TimeStun.isActive(p)) {
				seriousPunch(p, e.getItem());
			}
			return;
		}
		if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		if (e.useItemInHand() == Event.Result.DENY && e.getAction() == Action.RIGHT_CLICK_BLOCK) return;   // 강화대 등에서 처리됨
		ItemStack stack = e.getItem();
		HItem h = HItem.of(stack);
		if (h == null) return;
		Player p = e.getPlayer();
		if (TimeStun.isActive(p)) {
			e.setCancelled(true);
			e.setUseItemInHand(Event.Result.DENY);
			p.sendActionBar(Component.text("⏸ 시간이 멈춰 스킬을 사용할 수 없습니다", NamedTextColor.AQUA));
			return;
		}
		if (stack.getType() == Material.KNOWLEDGE_BOOK) {   // 재료 아이템: 지식의 책 사용(소모) 방지
			e.setUseItemInHand(Event.Result.DENY);
			return;
		}
		if (!h.isWeapon()) return;
		if (h == HItem.ONEPUNCH_GLOVE) {
			e.setUseItemInHand(Event.Result.DENY);
			handleOnePunchCharge(p, stack);
			return;
		}
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
				if (p.isSneaking() && lv >= 15) ChronoSkill.timer(p, lv, tier);   // 15강~: 업보청산
				else ChronoSkill.slashAndStop(p, lv, mult, tier);
				damage(p, slot, 2);
			}
			default -> { return; }
		}
		// t413: 처치 초기화(cdMult 0)여도 3틱은 잠근다 — 블록을 보고 우클릭하면 같은 클릭에 상호작용 이벤트가 2번(블록·공중) 와서
		// 쿨 0 이면 관통 베기가 연달아 두 번 나가던 문제 (아래를 보고 베어 처치할 때 2배 거리로 날아감)
		p.setCooldown(h.cooldownGroup(), Math.max(cdMult == 0 ? 3 : 0, (int) Math.round(h.cooldown * com.hobbang.enhance.item.EnhanceTier.bossCooldownMult(h, lv)) * cdMult));
		p.swingHand(slot);
	}

	private static void damage(Player p, EquipmentSlot slot, int amount) {
		if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
		p.damageItemStack(slot, amount);
	}

	public static final org.bukkit.NamespacedKey ONEPUNCH_DMG = com.hobbang.enhance.Keys.k("onepunch_dmg");

	private static final class ChargeState {
		final int startTick;
		int lastTick;
		int lastStep;
		ChargeState(int startTick) { this.startTick = startTick; this.lastTick = startTick; }
	}
	private final java.util.Map<java.util.UUID, ChargeState> punchCharges = new java.util.HashMap<>();

	public static double onePunchDamage(ItemStack s) {
		if (s == null || s.getType().isAir()) return 0.0;
		return s.getPersistentDataContainer().getOrDefault(ONEPUNCH_DMG, org.bukkit.persistence.PersistentDataType.DOUBLE, 0.0);
	}

	@SuppressWarnings("UnstableApiUsage")
	private static void setOnePunchDamage(ItemStack s, double dmg) {
		s.editPersistentDataContainer(pdc -> {
			if (dmg <= 0.0) pdc.remove(ONEPUNCH_DMG);
			else pdc.set(ONEPUNCH_DMG, org.bukkit.persistence.PersistentDataType.DOUBLE, dmg);
		});
		io.papermc.paper.datacomponent.item.ItemAttributeModifiers.Builder b = io.papermc.paper.datacomponent.item.ItemAttributeModifiers.itemAttributes();
		b.addModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
			new org.bukkit.attribute.AttributeModifier(org.bukkit.NamespacedKey.minecraft("base_attack_damage"),
				dmg <= 0.0 ? HItem.ONEPUNCH_GLOVE.damage : dmg,
				org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
		b.addModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED,
			new org.bukkit.attribute.AttributeModifier(org.bukkit.NamespacedKey.minecraft("base_attack_speed"),
				HItem.ONEPUNCH_GLOVE.speed,
				org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
		s.setData(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS, b.build());
	}

	private void handleOnePunchCharge(Player p, ItemStack stack) {
		int now = org.bukkit.Bukkit.getCurrentTick();
		java.util.UUID uid = p.getUniqueId();
		ChargeState existing = punchCharges.get(uid);
		if (existing != null && now - existing.lastTick <= 6) {
			existing.lastTick = now;
			return;
		}
		ChargeState st = new ChargeState(now);
		punchCharges.put(uid, st);
		setOnePunchDamage(stack, 0.0);
		p.sendActionBar(Component.text("⚡ 원펀맨 출력: ∞ (꾹 누르면 0.5초마다 +20 데미지)", NamedTextColor.RED));
		p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, org.bukkit.SoundCategory.PLAYERS, 0.8F, 1.4F);

		org.bukkit.Bukkit.getScheduler().runTaskTimer(com.hobbang.enhance.HobbangPlugin.get(), task -> {
			if (!p.isOnline() || p.isDead()) { punchCharges.remove(uid); task.cancel(); return; }
			ItemStack hand = p.getInventory().getItemInMainHand();
			if (HItem.of(hand) != HItem.ONEPUNCH_GLOVE) { punchCharges.remove(uid); task.cancel(); return; }
			int cur = org.bukkit.Bukkit.getCurrentTick();
			if (cur - st.lastTick > 6) {
				punchCharges.remove(uid);
				if (st.lastStep >= 1) {
					int finalDmg = st.lastStep * 20;
					p.sendActionBar(Component.text("✔ 원펀맨 출력 설정 완료: " + finalDmg + " DMG", NamedTextColor.GOLD));
					p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, org.bukkit.SoundCategory.PLAYERS, 0.9F, 1.2F);
				}
				task.cancel();
				return;
			}
			int step = (cur - st.startTick) / 10;
			if (step >= 1) {
				if (step != st.lastStep) {
					st.lastStep = step;
					setOnePunchDamage(hand, step * 20.0);
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, org.bukkit.SoundCategory.PLAYERS, 0.9F, Math.min(2.0F, 0.7F + step * 0.1F));
				}
				int bars = Math.min(10, step);
				String gauge = "█".repeat(bars) + "░".repeat(10 - bars);
				p.sendActionBar(Component.text("⚡ 원펀맨 출력 게이지: " + (step * 20) + " DMG [" + gauge + "]", NamedTextColor.GOLD));
			}
		}, 2L, 2L);
	}

	private final java.util.Map<java.util.UUID, Integer> lastSeriousTick = new java.util.HashMap<>();

	static double seriousShockRadius(double dist) {
		return Math.min(8.5, 2.5 + Math.max(0.0, dist) * 0.03);
	}

	/** 원펀맨 진심펀치: 쉬프트+좌클릭 시 전방 1000블럭 충격파 범위 내 모든 적 섬멸 */
	private void seriousPunch(Player p, ItemStack hand) {
		int now = org.bukkit.Bukkit.getCurrentTick();
		Integer prev = lastSeriousTick.put(p.getUniqueId(), now);
		if (prev != null && now - prev <= 2) return;

		double rawDmg = onePunchDamage(hand);
		double dmg = rawDmg <= 0.0 ? 9999999.0 : rawDmg;
		Location eye = p.getEyeLocation();
		Vector dir = eye.getDirection();
		if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
		else dir.normalize();
		World w = p.getWorld();
		Vector origin = eye.toVector();

		final double range = 1000.0;
		int hitCount = 0;
		for (LivingEntity le : w.getLivingEntities()) {
			if (le == p || le instanceof Player || le.isDead()) continue;
			if (le instanceof org.bukkit.entity.ArmorStand as && as.isMarker()) continue;
			Vector center = le.getLocation().toVector().add(new Vector(0, le.getHeight() * 0.5, 0));
			Vector to = center.clone().subtract(origin);
			double proj = to.dot(dir);
			if (proj < -0.5 || proj > range) continue;
			double perp = to.clone().subtract(dir.clone().multiply(Math.max(0.0, proj))).length();
			double limit = seriousShockRadius(proj) + le.getWidth() * 0.5;
			if (perp > limit) continue;
			le.setNoDamageTicks(0);
			skillDamage(le, p, dmg);
			hitCount++;
			if (le.isValid() && !le.isDead()) {
				le.setVelocity(dir.clone().multiply(2.8).add(new Vector(0, 0.9, 0)));
			}
			w.spawnParticle(Particle.EXPLOSION, center.getX(), center.getY(), center.getZ(), 2, 0.4, 0.4, 0.4, 0, null, true);
		}

		// 음향: 폭발 + 워든 소닉붐 + 번개 + 원펀치 타격음
		w.playSound(eye, "hobbang:weapon.onepunch.hit", org.bukkit.SoundCategory.PLAYERS, 2.5f, 0.72f);
		w.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, org.bukkit.SoundCategory.PLAYERS, 2.2f, 0.5f);
		w.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, org.bukkit.SoundCategory.PLAYERS, 2.0f, 0.85f);
		w.playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, org.bukkit.SoundCategory.PLAYERS, 1.8f, 0.6f);

		// 전방 1000블럭 충격파 연출 (충격파 링 반경 == seriousShockRadius(d) 로 타격 범위와 일치)
		Vector right = new Vector(-dir.getZ(), 0, dir.getX());
		if (right.lengthSquared() < 1e-6) right = new Vector(1, 0, 0);
		else right.normalize();
		Vector up = right.clone().crossProduct(dir).normalize();
		final Vector fDir = dir.clone(), fRight = right.clone(), fUp = up.clone();
		for (int step = 0; step < 20; step++) {
			final int s = step;
			Fx.later(s, () -> {
				double dStart = 1.5 + s * 50.0;
				double dEnd = Math.min(range, dStart + 50.0);
				double d = dStart;
				while (d <= dEnd) {
					Vector pt = origin.clone().add(fDir.clone().multiply(d));
					w.spawnParticle(Particle.SONIC_BOOM, pt.getX(), pt.getY(), pt.getZ(), 1, 0, 0, 0, 0, null, true);
					double ringR = seriousShockRadius(d);
					if (d <= 200.0 || ((int) Math.round(d)) % 20 == 0) {
						w.spawnParticle(Particle.EXPLOSION_EMITTER, pt.getX(), pt.getY(), pt.getZ(), 1, 0.6, 0.6, 0.6, 0, null, true);
					}
					int points = d <= 80.0 ? 12 : 8;
					for (int k = 0; k < points; k++) {
						double a = d * 0.35 + k * (Math.PI * 2 / points);
						Vector off = fRight.clone().multiply(Math.cos(a) * ringR).add(fUp.clone().multiply(Math.sin(a) * ringR));
						Vector rp = pt.clone().add(off);
						w.spawnParticle(Particle.CLOUD, rp.getX(), rp.getY(), rp.getZ(), 1, 0.2, 0.2, 0.2, 0.05, null, true);
						if (k % 2 == 0) w.spawnParticle(Particle.FLAME, rp.getX(), rp.getY(), rp.getZ(), 1, 0.12, 0.12, 0.12, 0.03, null, true);
						if (d <= 150.0 && k % 3 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, rp.getX(), rp.getY(), rp.getZ(), 1, 0.15, 0.15, 0.15, 0.06, null, true);
					}
					w.spawnParticle(Particle.END_ROD, pt.getX(), pt.getY(), pt.getZ(), 3, 0.8, 0.8, 0.8, 0.06, null, true);
					if (d <= 80.0) {
						int gy = (int) BU.groundY(w, pt.getX(), pt.getY() + 2, pt.getZ()) - 1;
						if (gy >= w.getMinHeight()) {
							org.bukkit.block.Block gb = w.getBlockAt((int) Math.floor(pt.getX()), gy, (int) Math.floor(pt.getZ()));
							if (gb.getType().isSolid()) {
								w.spawnParticle(Particle.BLOCK, pt.getX(), gy + 1.05, pt.getZ(), 10, 1.8, 0.2, 1.8, 0.15, gb.getBlockData(), true);
							}
						}
					}
					d += d < 60.0 ? 2.5 : (d < 250.0 ? 6.0 : 12.5);
				}
			});
		}
		p.swingMainHand();
		String dmgLabel = rawDmg <= 0.0 ? "∞" : String.valueOf((int) Math.round(rawDmg));
		p.sendActionBar(Component.text("💥 진심 펀치! " + hitCount + "마리 타격 (데미지: " + dmgLabel + ")", NamedTextColor.RED));
	}

	/** 원펀맨 글러브: 설정된 데미지(∞ 또는 20 단위) + 무한 공속 + 전용 펀치 사운드 */
	@EventHandler(priority = EventPriority.LOW)
	public void onOnePunch(EntityDamageByEntityEvent e) {
		if (skill || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
		if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) return;
		ItemStack hand = p.getInventory().getItemInMainHand();
		if (HItem.of(hand) != HItem.ONEPUNCH_GLOVE) return;
		if (p.isSneaking()) {
			e.setCancelled(true);
			seriousPunch(p, hand);
			return;
		}
		target.setNoDamageTicks(0);
		double dmg = onePunchDamage(hand);
		e.setDamage(dmg <= 0.0 ? 9999999.0 : dmg);
		Location loc = target.getLocation().add(0, target.getHeight() * 0.5, 0);
		World w = loc.getWorld();
		w.playSound(loc, "hobbang:weapon.onepunch.hit", org.bukkit.SoundCategory.PLAYERS, 2.0f, 1.0f);
		w.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
		w.spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0);
		w.spawnParticle(Particle.CLOUD, loc, 22, 0.5, 0.5, 0.5, 0.25);
	}

	/** 수정 대검 10강~: 근접 타격 직전 대상 무적시간 제거 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onPreCrystalAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
		if (!(e.getAttacked() instanceof LivingEntity le)) return;
		ItemStack w = e.getPlayer().getInventory().getItemInMainHand();
		if (HItem.of(w) == HItem.CRYSTAL_GREATSWORD && com.hobbang.enhance.item.ItemUtil.getLevel(w) >= 10) {
			le.setNoDamageTicks(0);
		}
	}

	/** 근접 타격 시 테마 연출 (+ 크로노 낫 구속) */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onMelee(EntityDamageByEntityEvent e) {
		if (skill || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
		if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) return;
		if (TimeStun.isActive(p)) return;
		ItemStack w = p.getInventory().getItemInMainHand();
		HItem h = HItem.of(w);
		if (h == null || !h.isWeapon() || h == HItem.ONEPUNCH_GLOVE) return;
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
