package com.hobbang.enhance.world;

import com.hobbang.enhance.enhance.Enchants;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.tag.DamageTypeTags;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v1.1.0 신규 인첸트 중 JSON 으로 표현할 수 없는 효과.
 * (상위 날카로움 · 맹독 · 사거리는 데이터팩 JSON 만으로 동작)
 */
@SuppressWarnings("deprecation")
public final class EnchantListener implements Listener {
	private static boolean stomping = false;

	// ───────── 상위 보호 ─────────
	/** 상위 보호 레벨당 EPF (바닐라 보호 IV = 4) */
	public static int superiorProtEpf(int level) { return level <= 0 ? 0 : 4 + level; }

	/**
	 * 같은 장비에 보호와 상위 보호가 함께 있으면 더 높은 쪽만 적용 (합산하지 않음).
	 * 장비마다 max(보호 Lv, 상위 EPF) − 보호 Lv 만큼 EPF 를 더하고, 바닐라와 같이 총 EPF 20(80%) 상한.
	 * 바닐라 MAGIC 수치에서 현재 EPF 를 역산해 다시 계산하므로 다른 보호 인첸트와도 정확히 맞물린다.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onProtect(EntityDamageEvent e) {
		if (Enchants.SUPERIOR_PROTECTION == null || !(e.getEntity() instanceof LivingEntity le)) return;
		EntityEquipment eq = le.getEquipment();
		if (eq == null) return;
		int bonus = 0;
		for (ItemStack a : eq.getArmorContents()) {
			int lv = Enchants.level(Enchants.SUPERIOR_PROTECTION, a);
			if (lv <= 0) continue;
			int vanilla = a.getEnchantmentLevel(Enchantment.PROTECTION);
			bonus += Math.max(0, superiorProtEpf(lv) - vanilla);
		}
		if (bonus <= 0) return;
		DamageType type = e.getDamageSource().getDamageType();
		if (DamageTypeTags.BYPASSES_ENCHANTMENTS.isTagged(type) || DamageTypeTags.BYPASSES_INVULNERABILITY.isTagged(type)) return;
		if (!e.isApplicable(EntityDamageEvent.DamageModifier.MAGIC)) return;
		double before = 0;
		for (EntityDamageEvent.DamageModifier m : EntityDamageEvent.DamageModifier.values()) {
			if (m.ordinal() >= EntityDamageEvent.DamageModifier.MAGIC.ordinal()) break;
			if (e.isApplicable(m)) before += e.getDamage(m);
		}
		if (before <= 0) return;
		double epf = Math.min(20.0, Math.max(0.0, -e.getDamage(EntityDamageEvent.DamageModifier.MAGIC) / before * 25.0));
		double now = Math.min(20.0, epf + bonus);
		e.setDamage(EntityDamageEvent.DamageModifier.MAGIC, -before * now / 25.0);
	}

	// ───────── 급소 ─────────
	/** 근접 공격 시 레벨당 5% 확률로 피해 ×1.5 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCritical(EntityDamageByEntityEvent e) {
		if (!(e.getDamager() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
		int lv = Enchants.level(Enchants.CRITICAL, p.getInventory().getItemInMainHand());
		if (lv <= 0 || ThreadLocalRandom.current().nextDouble() >= 0.05 * lv) return;
		e.setDamage(e.getDamage() * 1.5);
		Location l = e.getEntity().getLocation().add(0, e.getEntity().getHeight() * 0.6, 0);
		l.getWorld().spawnParticle(Particle.ENCHANTED_HIT, l, 14, 0.3, 0.3, 0.3, 0.3);
		l.getWorld().playSound(l, Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 0.8F);
	}

	// ───────── 짓밟기 ─────────
	/** 낙하 피해의 레벨당 25% 를 반경 3칸 안의 적에게 나눠 준다 (본인 피해는 그대로, 플레이어·길들인 동물 제외) */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onStomp(EntityDamageEvent e) {
		if (stomping || e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
		int lv = Enchants.level(Enchants.STOMPING, p.getInventory().getBoots());
		if (lv <= 0 || e.getDamage() <= 0) return;
		double dmg = e.getDamage() * 0.25 * lv;
		DamageSource src = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(p).withDirectEntity(p).build();
		int hit = 0;
		stomping = true;
		try {
			for (Entity n : p.getNearbyEntities(3, 2, 3)) {
				if (!(n instanceof LivingEntity t) || n instanceof Player || t.isDead()) continue;
				if (n instanceof Tameable tm && tm.isTamed()) continue;
				t.damage(dmg, src);
				hit++;
			}
		} finally {
			stomping = false;
		}
		if (hit > 0) {
			Location l = p.getLocation();
			l.getWorld().spawnParticle(Particle.BLOCK, l, 30, 1.2, 0.1, 1.2, 0.1, l.clone().subtract(0, 1, 0).getBlock().getBlockData());
			l.getWorld().playSound(l, Sound.ENTITY_GENERIC_BIG_FALL, SoundCategory.PLAYERS, 1.2F, 0.6F);
		}
	}

	// ───────── 생명 수선 ─────────
	/** 체력이 회복될 때 생명 수선이 붙은 착용·손 장비의 내구도를 (회복량 × 레벨)만큼 수리 (회복량은 그대로) */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onRegain(EntityRegainHealthEvent e) {
		if (Enchants.LIFE_MENDING == null || !(e.getEntity() instanceof Player p) || e.getAmount() <= 0) return;
		EntityEquipment eq = p.getEquipment();
		ItemStack[] armor = eq.getArmorContents();
		ItemStack[] all = {eq.getItemInMainHand(), eq.getItemInOffHand(), armor[0], armor[1], armor[2], armor[3]};
		for (ItemStack s : all) {
			int lv = Enchants.level(Enchants.LIFE_MENDING, s);
			if (lv <= 0 || !(s.getItemMeta() instanceof Damageable d) || d.getDamage() <= 0) continue;
			double raw = e.getAmount() * lv;
			int fix = (int) raw + (ThreadLocalRandom.current().nextDouble() < raw - (int) raw ? 1 : 0);
			if (fix <= 0) continue;
			d.setDamage(Math.max(0, d.getDamage() - fix));
			s.setItemMeta(d);
		}
	}

	// ───────── 자동 제련 / 염동력 ─────────
	private static final Map<Material, CookingRecipe<?>> SMELT = new EnumMap<>(Material.class);

	/** 화로 레시피 표 (서버 시작 시 1회) */
	public static void initSmelting() {
		SMELT.clear();
		for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
			Recipe r = it.next();
			if (!(r instanceof FurnaceRecipe fr)) continue;
			ItemStack in = fr.getInput();
			if (in == null || in.getType().isAir() || fr.getResult().getType() == Material.CHARCOAL) continue;
			SMELT.putIfAbsent(in.getType(), fr);
		}
	}

	public static int smeltCount() { return SMELT.size(); }

	/** 자동 제련: 화로 레시피가 있는 드롭을 구운 결과로 바꾸고 화로 경험치를 준다 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onSmelt(BlockDropItemEvent e) {
		Player p = e.getPlayer();
		ItemStack tool = p.getInventory().getItemInMainHand();
		if (Enchants.level(Enchants.AUTO_SMELT, tool) <= 0) return;
		float xp = 0;
		boolean any = false;
		for (Item it : e.getItems()) {
			ItemStack s = it.getItemStack();
			CookingRecipe<?> r = SMELT.get(s.getType());
			if (r == null || s.hasItemMeta() && s.getItemMeta().hasCustomName()) continue;
			ItemStack out = r.getResult().clone();
			out.setAmount(Math.min(out.getMaxStackSize(), s.getAmount() * out.getAmount()));
			it.setItemStack(out);
			xp += r.getExperience() * s.getAmount();
			any = true;
		}
		if (!any) return;
		Location at = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
		int orb = (int) xp + (ThreadLocalRandom.current().nextFloat() < xp - (int) xp ? 1 : 0);
		if (orb > 0) at.getWorld().spawn(at, ExperienceOrb.class, o -> o.setExperience(orb));
		at.getWorld().spawnParticle(Particle.FLAME, at, 6, 0.25, 0.25, 0.25, 0.01);
	}

	/** 염동력: 캔 블록의 드롭을 바로 가방으로 (가득 차면 남은 것만 바닥에) */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onTelekinesis(BlockDropItemEvent e) {
		Player p = e.getPlayer();
		if (Enchants.level(Enchants.TELEKINESIS, p.getInventory().getItemInMainHand()) <= 0) return;
		List<Item> items = e.getItems();
		items.removeIf(it -> {
			Map<Integer, ItemStack> left = p.getInventory().addItem(it.getItemStack());
			if (left.isEmpty()) return true;
			it.setItemStack(left.values().iterator().next());
			return false;
		});
	}

	/** 플러그인이 직접 떨구는 드롭(강화석 광맥 등)도 염동력이면 가방으로 */
	public static void dropOrGive(Player p, Location at, ItemStack s) {
		if (Enchants.level(Enchants.TELEKINESIS, p.getInventory().getItemInMainHand()) > 0) {
			for (ItemStack left : p.getInventory().addItem(s).values()) at.getWorld().dropItemNaturally(at, left);
			return;
		}
		at.getWorld().dropItemNaturally(at, s);
	}
}
