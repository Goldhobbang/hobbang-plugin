package com.hobbang.enhance.world;

import com.hobbang.enhance.HobbangPlugin;
import com.hobbang.enhance.Keys;
import com.hobbang.enhance.boss.BossManager;
import com.hobbang.enhance.enhance.Enchants;
import com.hobbang.enhance.enhance.EnhanceConfig;
import com.hobbang.enhance.item.Guide;
import com.hobbang.enhance.item.HItem;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BrushableBlock;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** 야생 시스템: 몹 드롭 / 인첸트 효과 / 채굴 / 상자·낚시·고고학 전리품 / 가이드북 */
public final class WorldListener implements Listener {
	private static boolean breaking = false;

	private static ThreadLocalRandom rnd() { return ThreadLocalRandom.current(); }

	// ───────── 가이드북 ─────────
	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		if (p.getPersistentDataContainer().has(Keys.GOT_GUIDE) || p.getScoreboardTags().contains("hobbang_got_guide")) return;
		p.getPersistentDataContainer().set(Keys.GOT_GUIDE, PersistentDataType.BYTE, (byte) 1);
		ItemStack book = HItem.GUIDE.create();
		for (ItemStack left : p.getInventory().addItem(book).values()) p.getWorld().dropItem(p.getLocation(), left);
	}

	/** 가이드북을 열 때 항상 최신 내용으로 (옛 버전 책도 자동 갱신) */
	@EventHandler(priority = EventPriority.HIGH)
	public void onGuide(PlayerInteractEvent e) {
		if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		if (!HItem.GUIDE.is(e.getItem())) return;
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.useItemInHand() == Event.Result.DENY) return;
		Block cb = e.getClickedBlock();
		if (cb != null && cb.getType() == Material.LECTERN) return;   // 독서대에 올리기
		e.setUseItemInHand(Event.Result.DENY);
		e.getPlayer().openBook(HItem.GUIDE.create());
	}

	// ───────── 흡혈 ─────────
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent e) {
		if (!(e.getDamager() instanceof Player p) || e.getFinalDamage() <= 0) return;
		int lv = Enchants.level(Enchants.LIFESTEAL, p.getInventory().getItemInMainHand());
		if (lv > 0 && !p.isDead()) p.heal(e.getFinalDamage() * 0.04 * lv);
	}

	// ───────── 몹 처치 드롭 / 영혼 수확 ─────────
	@EventHandler(priority = EventPriority.HIGH)
	public void onDeath(EntityDeathEvent e) {
		LivingEntity entity = e.getEntity();
		if (entity instanceof Player || BossManager.isBoss(entity)) return;
		if (!(e.getDamageSource().getCausingEntity() instanceof Player p)) return;
		ItemStack weapon = p.getInventory().getItemInMainHand();
		int looting = weapon.getType().isAir() ? 0 : weapon.getEnchantmentLevel(Enchantment.LOOTING);
		List<ItemStack> drops = e.getDrops();
		if (entity instanceof Wither || entity instanceof EnderDragon || entity instanceof Warden || entity instanceof ElderGuardian) {
			drops.add(HItem.ENHANCE_STONE.create(16));
			drops.add(HItem.RADIANT_STONE.create(2));
			if (entity instanceof Warden) drops.add(HItem.ABYSS_ESSENCE.create(1));
			if (entity instanceof ElderGuardian) drops.add(HItem.PRISM_CRYSTAL.create(1));
			if (entity instanceof EnderDragon) {
				// 드래곤은 공중에서 죽으므로 처치자 발밑에 직접 떨군다
				Location at = p.getLocation();
				for (ItemStack s : List.of(HItem.ENHANCE_STONE.create(16), HItem.RADIANT_STONE.create(2), HItem.IMMORTAL_SCROLL.create(1)))
					p.getWorld().dropItemNaturally(at, s);
				drops.removeIf(s -> HItem.of(s) != null);
			}
			if (entity instanceof Wither) drops.add(HItem.PROTECTION_SCROLL.create(2));
			return;
		}
		ThreadLocalRandom r = rnd();
		if (entity instanceof Phantom && r.nextFloat() < 0.02F + 0.01F * looting) drops.add(HItem.STORM_FEATHER.create());
		if (entity instanceof Evoker && r.nextFloat() < 0.25F + 0.05F * looting) drops.add(HItem.LUCK_CHARM.create());
		if (entity instanceof PiglinBrute && r.nextFloat() < 0.15F + 0.05F * looting) drops.add(HItem.RADIANT_STONE.create());
		if (entity instanceof Enemy) {
			double chance = EnhanceConfig.CFG.mobStoneChance + looting;
			if (r.nextDouble() * 100 < chance) drops.add(HItem.ENHANCE_STONE.create());
			int soul = Enchants.level(Enchants.SOUL_HARVEST, weapon);
			if (soul > 0) {
				p.heal(1.0);
				if (r.nextDouble() * 100 < soul * 3.0) {
					drops.add(HItem.ENHANCE_STONE.create());
					Location l = entity.getLocation();
					entity.getWorld().spawnParticle(Particle.SOUL, l.getX(), l.getY() + 1, l.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
				}
			}
		}
	}

	// ───────── 채굴 ─────────
	private static boolean silk(ItemStack t) {
		return t != null && !t.getType().isAir() && t.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
	}

	/** ore_drops 공식 (행운) */
	private static int oreDrops(int count, int fortune) {
		if (fortune <= 0) return count;
		int bonus = rnd().nextInt(fortune + 2) - 1;
		return count * (Math.max(0, bonus) + 1);
	}

	private static boolean isStoneLike(Material m) {
		return m == Material.STONE || m == Material.DEEPSLATE || m == Material.TUFF;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent e) {
		Player p = e.getPlayer();
		Block b = e.getBlock();
		ItemStack tool = p.getInventory().getItemInMainHand();
		boolean creative = p.getGameMode() == GameMode.CREATIVE;
		Material type = b.getType();
		// 강화석 광맥 (돌·심층암·응회암을 캐다 보면 가끔): Fabric 강화석 광석 대체
		if (!creative && !silk(tool) && isStoneLike(type) && b.getY() < EnhanceConfig.CFG.stoneDropMaxY && e.isDropItems()
			&& !b.getDrops(tool, p).isEmpty() && rnd().nextDouble() * 100 < EnhanceConfig.CFG.stoneDropChance) {
			boolean deep = type == Material.DEEPSLATE;
			int fortune = tool.getType().isAir() ? 0 : tool.getEnchantmentLevel(Enchantment.FORTUNE);
			int n = oreDrops(deep ? 1 + rnd().nextInt(3) : 1 + rnd().nextInt(2), fortune);
			e.setDropItems(false);
			Location at = b.getLocation().add(0.5, 0.5, 0.5);
			EnchantListener.dropOrGive(p, at, HItem.ENHANCE_STONE.create(n));
			if (deep && rnd().nextDouble() * 100 < EnhanceConfig.CFG.deepslateRadiantChance)
				EnchantListener.dropOrGive(p, at, HItem.RADIANT_STONE.create());
			e.setExpToDrop(e.getExpToDrop() + (deep ? 3 + rnd().nextInt(4) : 2 + rnd().nextInt(4)));
			b.getWorld().spawnParticle(Particle.WAX_ON, at, 12, 0.4, 0.4, 0.4, 0.5);
			b.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0F, 1.4F);
		}
		if (breaking) return;
		if (!creative && !silk(tool)) {
			if (Tag.DIAMOND_ORES.isTagged(type) || Tag.EMERALD_ORES.isTagged(type) || type == Material.ANCIENT_DEBRIS) {
				if (rnd().nextDouble() * 100 < EnhanceConfig.CFG.oreBonusStoneChance)
					EnchantListener.dropOrGive(p, b.getLocation().add(0.5, 0.5, 0.5), HItem.ENHANCE_STONE.create());
			}
		}
		if (p.isSneaking() || tool.getType().isAir()) return;
		boolean exc = Enchants.level(Enchants.EXCAVATION, tool) > 0;
		boolean tim = !exc && Enchants.level(Enchants.TIMBER, tool) > 0 && Tag.LOGS.isTagged(type);
		if (!exc && !tim) return;
		final float pitch = p.getLocation().getPitch();
		final org.bukkit.block.BlockFace facing = p.getFacing();
		final float originHardness = type.getHardness();
		// 원래 블록이 깨진 뒤 처리 (같은 틱, 이벤트 밖)
		Bukkit.getScheduler().runTask(HobbangPlugin.get(), () -> {
			if (!p.isOnline()) return;
			breaking = true;
			try {
				if (exc) excavate(p, b, pitch, facing, originHardness, tool);
				else timber(p, b, tool);
			} finally {
				breaking = false;
			}
		});
	}

	private static boolean sameTool(Player p, ItemStack tool) {
		ItemStack now = p.getInventory().getItemInMainHand();
		return !now.getType().isAir() && now.isSimilar(tool);
	}

	private static void excavate(Player p, Block center, float pitch, org.bukkit.block.BlockFace facing, float originHardness, ItemStack tool) {
		char axis = Math.abs(pitch) > 50 ? 'Y' : (facing.getModX() != 0 ? 'X' : 'Z');
		for (int a = -1; a <= 1; a++) {
			for (int c = -1; c <= 1; c++) {
				if (a == 0 && c == 0) continue;
				Block t = switch (axis) {
					case 'Y' -> center.getRelative(a, 0, c);
					case 'X' -> center.getRelative(0, a, c);
					default -> center.getRelative(a, c, 0);
				};
				Material m = t.getType();
				if (m.isAir() || t.isLiquid()) continue;
				float h = m.getHardness();
				if (h < 0 || h > originHardness + 3) continue;
				ItemStack now = p.getInventory().getItemInMainHand();
				if (!sameTool(p, tool)) return;
				if (!t.isPreferredTool(now) || t.getDestroySpeed(now) <= 1.0F) continue;
				p.breakBlock(t);
			}
		}
	}

	private static void timber(Player p, Block start, ItemStack tool) {
		ArrayDeque<Block> queue = new ArrayDeque<>();
		Set<Block> seen = new HashSet<>();
		queue.add(start);
		seen.add(start);
		int broken = 0;
		while (!queue.isEmpty() && broken < 96) {
			Block cur = queue.poll();
			for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				Block n = cur.getRelative(dx, dy, dz);
				if (seen.add(n) && Tag.LOGS.isTagged(n.getType())) {
					if (!sameTool(p, tool)) return;
					if (p.breakBlock(n)) {
						broken++;
						queue.add(n);
					}
				}
			}
		}
	}

	// ───────── 전리품 ─────────
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onLoot(LootGenerateEvent e) {
		String key = e.getLootTable().getKey().toString();
		List<ModLoot.Drop> drops = ModLoot.CHESTS.getOrDefault(key, List.of());
		for (ModLoot.Drop d : drops) {
			if (rnd().nextFloat() < d.chance()) e.getLoot().add(d.item().create(d.min() + rnd().nextInt(d.max() - d.min() + 1)));
		}
		ModLoot.rollBooks(key, e.getLoot());
	}

	/** 시련의 회랑 금고 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onVault(BlockDispenseLootEvent e) {
		String key = e.getLootTable().getKey().toString();
		List<ModLoot.Drop> drops = ModLoot.CHESTS.get(key);
		if (drops == null && !ModLoot.hasExtra(key)) return;
		if (drops == null) drops = List.of();
		List<ItemStack> loot = new java.util.ArrayList<>(e.getDispensedLoot());
		for (ModLoot.Drop d : drops)
			if (rnd().nextFloat() < d.chance()) loot.add(d.item().create(d.min() + rnd().nextInt(d.max() - d.min() + 1)));
		ModLoot.rollBooks(key, loot);
		e.setDispensedLoot(loot);
	}

	/** 낚시 보물: 바닐라 보물 6종(가중치 각 1)에 강화석 2 · 행운의 부적 1 가중치 추가와 동일 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onFish(PlayerFishEvent e) {
		if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item item)) return;
		ItemStack s = item.getItemStack();
		Material m = s.getType();
		boolean treasure = m == Material.ENCHANTED_BOOK || m == Material.NAME_TAG || m == Material.NAUTILUS_SHELL || m == Material.SADDLE
			|| ((m == Material.BOW || m == Material.FISHING_ROD) && !s.getEnchantments().isEmpty());
		if (!treasure) return;
		ModLoot.Entry pick = ModLoot.pickSingle("minecraft:gameplay/fishing/treasure");
		if (pick != null) item.setItemStack(pick.item().create(pick.min() + rnd().nextInt(pick.max() - pick.min() + 1)));
	}

	/** 고고학: 수상한 블록을 처음 솔질할 때 전리품표 대신 모드 아이템이 들어 있을 확률 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onBrush(PlayerInteractEvent e) {
		if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getItem() == null || e.getItem().getType() != Material.BRUSH) return;
		Block b = e.getClickedBlock();
		if (b == null || (b.getType() != Material.SUSPICIOUS_SAND && b.getType() != Material.SUSPICIOUS_GRAVEL)) return;
		if (!(b.getState(false) instanceof BrushableBlock bb) || bb.getLootTable() == null) return;
		ModLoot.Entry pick = ModLoot.pickSingle(bb.getLootTable().getKey().toString());
		if (pick == null) return;
		bb.setLootTable(null);
		bb.setItem(pick.item().create(pick.min() + rnd().nextInt(pick.max() - pick.min() + 1)));
	}
}
