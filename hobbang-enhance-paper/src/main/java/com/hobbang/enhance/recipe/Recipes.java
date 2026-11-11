package com.hobbang.enhance.recipe;

import com.hobbang.enhance.Keys;
import com.hobbang.enhance.item.HItem;
import com.hobbang.enhance.item.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Crafter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LoomInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fabric 레시피 19종 + 바닐라 레시피에 홉방 아이템이 섞이지 않게 막는 가드 */
public final class Recipes implements Listener {
	public static final List<NamespacedKey> KEYS = new ArrayList<>();

	private static RecipeChoice c(Object o) {
		if (o instanceof HItem h) return new RecipeChoice.ExactChoice(h.create());
		return new RecipeChoice.MaterialChoice((Material) o);
	}

	private static void shaped(String id, HItem result, int count, String[] pattern, Object... keys) {
		NamespacedKey k = Keys.k(id);
		ShapedRecipe r = new ShapedRecipe(k, result.create(count));
		r.shape(pattern);
		for (int i = 0; i < keys.length; i += 2) r.setIngredient((Character) keys[i], c(keys[i + 1]));
		r.setCategory(org.bukkit.inventory.recipe.CraftingBookCategory.MISC);
		add(r, k);
	}

	private static void shapeless(String id, HItem result, Object... ing) {
		NamespacedKey k = Keys.k(id);
		ShapelessRecipe r = new ShapelessRecipe(k, result.create());
		for (Object o : ing) r.addIngredient(c(o));
		r.setCategory(org.bukkit.inventory.recipe.CraftingBookCategory.MISC);
		add(r, k);
	}

	private static void smithing(String id, HItem result, Material base, HItem addition) {
		NamespacedKey k = Keys.k(id);
		add(new SmithingTransformRecipe(k, result.create(), c(HItem.RELIC_TEMPLATE), new RecipeChoice.MaterialChoice(base), c(addition), false), k);
	}

	private static void add(Recipe r, NamespacedKey k) {
		Bukkit.removeRecipe(k, false);
		Bukkit.addRecipe(r, false);
		KEYS.add(k);
	}

	public static void register() {
		KEYS.clear();
		HItem ES = HItem.ENHANCE_STONE, RS = HItem.RADIANT_STONE;
		shaped("abyss_core", HItem.ABYSS_CORE, 1, new String[]{"CSC", "SDS", "CSC"}, 'C', Material.CRYING_OBSIDIAN, 'S', RS, 'D', Material.DIAMOND_BLOCK);
		shaped("prism_core", HItem.PRISM_CORE, 1, new String[]{"CSC", "SDS", "CSC"}, 'C', Material.AMETHYST_BLOCK, 'S', RS, 'D', HItem.ABYSS_ESSENCE);
		shaped("storm_core", HItem.STORM_CORE, 1, new String[]{"CSC", "SDS", "CSC"}, 'C', Material.PHANTOM_MEMBRANE, 'S', RS, 'D', HItem.PRISM_CRYSTAL);
		shaped("chrono_core", HItem.CHRONO_CORE, 1, new String[]{"CSC", "SDS", "CSC"}, 'C', Material.CLOCK, 'S', RS, 'D', HItem.STORM_FEATHER);
		shaped("enhance_forge", HItem.ENHANCE_FORGE, 1, new String[]{"SSS", " A ", "OOO"}, 'S', ES, 'A', Material.ANVIL, 'O', Material.OBSIDIAN);
		shaped("summon_altar", HItem.SUMMON_ALTAR, 1, new String[]{"ERE", "SKS", "CCC"}, 'E', Material.ECHO_SHARD, 'R', RS, 'S', ES, 'K', Material.SCULK_SENSOR, 'C', Material.SCULK);
		shaped("radiant_stone", RS, 1, new String[]{"SSS", "SDS", "SSS"}, 'S', ES, 'D', Material.DIAMOND);
		shaped("relic_template", HItem.RELIC_TEMPLATE, 1, new String[]{"DRD", "RNR", "DRD"}, 'D', Material.DIAMOND, 'R', RS, 'N', Material.NETHERITE_INGOT);
		shaped("relic_template_duplicate", HItem.RELIC_TEMPLATE, 2, new String[]{"DTD", "DOD", "DDD"}, 'D', Material.DIAMOND, 'T', HItem.RELIC_TEMPLATE, 'O', Material.CRYING_OBSIDIAN);
		shaped("rewind_hourglass", HItem.REWIND_HOURGLASS, 1, new String[]{"GSG", "SCS", "GSG"}, 'G', Material.GLASS, 'S', Material.SAND, 'C', HItem.CHRONO_GEAR);
		shapeless("blessing_scroll", HItem.BLESSING_SCROLL, Material.PAPER, ES, ES, Material.GOLDEN_APPLE);
		shapeless("guide", HItem.GUIDE, Material.BOOK, ES);
		shapeless("immortal_scroll", HItem.IMMORTAL_SCROLL, Material.PAPER, HItem.STORM_FEATHER, RS, RS);
		shapeless("luck_charm", HItem.LUCK_CHARM, Material.EMERALD, Material.GOLD_INGOT, ES, Material.STRING);
		shapeless("prism_scroll", HItem.PRISM_SCROLL, Material.PAPER, HItem.PRISM_CRYSTAL, RS);
		shapeless("protection_scroll", HItem.PROTECTION_SCROLL, Material.PAPER, ES, ES, ES, Material.DIAMOND);
		smithing("smith_hammer_smithing", HItem.SMITH_HAMMER, Material.NETHERITE_AXE, HItem.ABYSS_ESSENCE);
		smithing("crystal_greatsword_smithing", HItem.CRYSTAL_GREATSWORD, Material.NETHERITE_SWORD, HItem.PRISM_CRYSTAL);
		smithing("stormcaller_smithing", HItem.STORMCALLER, Material.NETHERITE_AXE, HItem.STORM_FEATHER);
		smithing("chrono_dagger_smithing", HItem.CHRONO_DAGGER, Material.NETHERITE_SWORD, HItem.CHRONO_GEAR);
		Bukkit.updateRecipes();
	}

	public static void unregister() {
		for (NamespacedKey k : KEYS) Bukkit.removeRecipe(k, false);
		KEYS.clear();
	}

	private static boolean ours(Recipe r) {
		return r instanceof Keyed k && k.getKey().getNamespace().equals(Keys.NS);
	}

	private static boolean anyHobbang(ItemStack[] items) {
		for (ItemStack s : items) if (HItem.of(s) != null) return true;
		return false;
	}

	/** 유물 단조: 기반 장비의 인첸트/내구도/강화 단계/장인의 기운을 이어받은 보스 무기 (Fabric 스미싱 믹스인과 동일) */
	public static ItemStack transform(ItemStack base, HItem h) {
		ItemStack out = h.create();
		for (Map.Entry<Enchantment, Integer> en : base.getEnchantments().entrySet()) out.addUnsafeEnchantment(en.getKey(), en.getValue());
		if (base.getItemMeta() instanceof Damageable bd && bd.hasDamage()) out.editMeta(Damageable.class, m -> m.setDamage(bd.getDamage()));
		int art = ItemUtil.getArtisan(base);
		if (art > 0) ItemUtil.setArtisan(out, art);
		ItemUtil.setLevel(out, ItemUtil.getLevel(base));
		return out;
	}

	/** 레시피 책에 모두 공개 */
	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		e.getPlayer().discoverRecipes(KEYS);
	}

	// ───────── 가드 ─────────
	@EventHandler(priority = EventPriority.HIGH)
	public void onCraft(PrepareItemCraftEvent e) {
		Recipe r = e.getRecipe();
		if (ours(r)) return;
		if (anyHobbang(e.getInventory().getMatrix())) e.getInventory().setResult(null);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCrafter(CrafterCraftEvent e) {
		if (ours(e.getRecipe())) return;
		if (e.getBlock().getState(false) instanceof Crafter cr && anyHobbang(cr.getInventory().getContents())) e.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onSmith(PrepareSmithingEvent e) {
		SmithingInventory inv = e.getInventory();
		ItemStack base = inv.getInputEquipment();
		ItemStack res = e.getResult();
		if (res == null || res.getType().isAir() || base == null) return;
		Recipe r = inv.getRecipe();
		HItem bh = HItem.of(base);
		if (bh != null) { e.setResult(null); return; }   // 보스 무기는 다른 변환의 재료가 될 수 없음
		if (ours(r)) {
			HItem h = HItem.of(res);
			if (h == null) return;
			e.setResult(transform(base, h));
			return;
		}
		// 바닐라 네더라이트 강화 등: 강화 수치를 결과 장비 기준으로 재계산
		if (ItemUtil.getLevel(res) > 0 || res.getPersistentDataContainer().has(Keys.LEVEL)) {
			ItemStack out = res.clone();
			ItemUtil.reapply(out);
			e.setResult(out);
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onResult(PrepareResultEvent e) {
		if (e instanceof PrepareSmithingEvent || e instanceof PrepareAnvilEvent) return;
		boolean block = false;
		if (e.getInventory() instanceof CartographyInventory || e.getInventory() instanceof LoomInventory) block = anyHobbang(e.getInventory().getContents());
		else if (e instanceof PrepareGrindstoneEvent g) {
			ItemStack a = g.getInventory().getUpperItem(), b = g.getInventory().getLowerItem();
			boolean both = a != null && !a.getType().isAir() && b != null && !b.getType().isAir();
			block = both && (HItem.of(a) != null || HItem.of(b) != null);   // 보스 무기 합치기 금지 (단독 인첸트 제거는 허용)
		}
		if (block) e.setResult(null);
	}

	/** 모루 이름 변경: 강화 태그 [+N] 유지 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onAnvil(PrepareAnvilEvent e) {
		ItemStack res = e.getResult();
		ItemStack first = e.getInventory().getFirstItem();
		if (res == null || res.getType().isAir() || first == null) return;
		ItemStack second = e.getInventory().getSecondItem();
		if (second != null && HItem.of(second) != null && HItem.of(second).base == Material.KNOWLEDGE_BOOK) { e.setResult(null); return; }
		if (ItemUtil.getLevel(first) <= 0 && HItem.of(first) == null) return;
		String text = e.getView().getRenameText();
		ItemStack out = res.clone();
		String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(ItemUtil.plainName(first));
		if (text != null && !text.isBlank() && !text.equals(plain) && !text.startsWith("[+") && !text.startsWith("★[+")) {
			String json = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(net.kyori.adventure.text.Component.text(text));
			out.editPersistentDataContainer(p -> p.set(Keys.BASE_NAME, PersistentDataType.STRING, json));
		} else if (text == null || text.isBlank()) {
			out.editPersistentDataContainer(p -> p.remove(Keys.BASE_NAME));
		}
		out.resetData(io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_NAME);
		ItemUtil.refreshName(out);
		ItemUtil.refreshLore(out);
		e.setResult(out);
	}
}
