package com.hobbang.enhance.boss;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.hobbang.enhance.item.HItem;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * v1.2.0 보스 보상 연출: 등급 색 윤곽선(발광) + 무한 수명 + 무적 아이템 엔티티.
 * 색은 메인 스코어보드 팀(hb_glow_*) 색으로 정해지고, 아이템이 사라지면 팀 항목도 지운다.
 */
public final class BossLoot implements Listener {
	public static final NamespacedKey KEY = new NamespacedKey("hobbang", "boss_loot");
	private static final Set<HItem> GOLD = EnumSet.noneOf(HItem.class);
	static {
		for (String id : new String[]{"smith_hammer", "crystal_greatsword", "stormcaller", "chrono_dagger"})
			for (HItem h : HItem.values()) if (h.id.equals(id)) GOLD.add(h);
	}
	private static final Random R = new Random();

	public static NamedTextColor colorOf(ItemStack s) {
		HItem h = HItem.of(s);
		if (h != null && GOLD.contains(h)) return NamedTextColor.GOLD;
		if (s.getType() == Material.ENCHANTED_BOOK && s.getItemMeta() instanceof EnchantmentStorageMeta m) {
			for (Enchantment e : m.getStoredEnchants().keySet())
				if (e.getKey().getNamespace().equals("hobbang") && e.getKey().getKey().startsWith("superior")) return NamedTextColor.GOLD;
		}
		ItemRarity r = s.getData(DataComponentTypes.RARITY);
		if (r == null) r = ItemRarity.COMMON;
		return switch (r) {
			case EPIC -> NamedTextColor.LIGHT_PURPLE;
			case RARE -> NamedTextColor.AQUA;
			case UNCOMMON -> NamedTextColor.YELLOW;
			default -> NamedTextColor.WHITE;
		};
	}

	private static Team team(NamedTextColor c) {
		Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
		String name = "hb_glow_" + c.toString();
		Team t = sb.getTeam(name);
		if (t == null) t = sb.registerNewTeam(name);
		t.color(c);
		return t;
	}

	public static void drop(Location at, List<ItemStack> loot) {
		World w = at.getWorld();
		if (w == null) return;
		Location base = at.clone().add(0, 0.6, 0);
		for (ItemStack s : loot) {
			if (s == null || s.getType().isAir()) continue;
			ItemStack st = s.clone();
			// 보스 고유 드롭은 원래부터 반짝임(glint) — 주로 HItem 쪽에서 설정. 혹시 빠졌으면 보강.
			HItem h = HItem.of(st);
			if (h != null && h.glint) st.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
			NamedTextColor col = colorOf(st);
			w.dropItem(base, st, it -> {
				it.setVelocity(new Vector((R.nextDouble() - 0.5) * 0.3, 0.3, (R.nextDouble() - 0.5) * 0.3));
				it.setGlowing(true);
				it.setUnlimitedLifetime(true);
				it.setInvulnerable(true);
				it.setPersistent(true);
				it.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
				team(col).addEntry(it.getUniqueId().toString());
			});
		}
		w.spawnParticle(Particle.TOTEM_OF_UNDYING, base, 60, 0.6, 0.8, 0.6, 0.4);
		w.spawnParticle(Particle.END_ROD, base, 30, 0.4, 1.2, 0.4, 0.05);
		BU.sound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1F, 1F);
	}

	@EventHandler
	public void onRemove(EntityRemoveFromWorldEvent e) {
		if (!(e.getEntity() instanceof Item it)) return;
		if (!it.getPersistentDataContainer().has(KEY)) return;
		String entry = it.getUniqueId().toString();
		Team t = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(entry);
		if (t != null && t.getName().startsWith("hb_glow_")) t.removeEntry(entry);
	}
}
