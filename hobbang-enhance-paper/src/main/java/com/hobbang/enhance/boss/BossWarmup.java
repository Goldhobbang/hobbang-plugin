package com.hobbang.enhance.boss;

import com.hobbang.enhance.HobbangPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.4.3: 서버 시작 직후 보스 파츠 item_display / 테마 블록 디스플레이를 미리 만들어
 * 첫 대장장이 소환 때 한 번만 나타나던 62–128ms 스파이크를 줄인다.
 */
public final class BossWarmup {
	private BossWarmup() {}

	public static void start() {
		List<World> worlds = Bukkit.getWorlds();
		if (worlds.isEmpty()) return;
		World w = worlds.get(0);
		Location at = new Location(w, 0.5, 1, 0.5);
		List<ItemStack> items = new ArrayList<>();
		for (BossModels.Spec sp : BossModels.all()) {
			ItemStack st = new ItemStack(Material.PAPER);
			st.editMeta(m -> m.setItemModel(new org.bukkit.NamespacedKey("hobbang", sp.key())));
			items.add(st);
		}
		items.add(com.hobbang.enhance.item.HItem.SMITH_HAMMER.create());
		List<Material> blocks = new ArrayList<>();
		for (BossKind k : BossKind.values()) {
			AssembleAnim.theme(k);
			for (Material m : AssembleAnim.theme(k).blocks()) if (!blocks.contains(m) && m.isBlock()) blocks.add(m);
		}
		blocks.add(Material.MAGMA_BLOCK);
		blocks.add(Material.AMETHYST_BLOCK);
		blocks.add(Material.CUT_COPPER);
		int[] i = {0};
		final int itemN = items.size();
		final int blockN = blocks.size();
		Bukkit.getScheduler().runTaskTimer(HobbangPlugin.get(), task -> {
			try {
				int done = 0;
				while (done < 2 && i[0] < itemN + blockN) {
					int k = i[0]++;
					if (k < itemN) {
						ItemStack st = items.get(k);
						ItemDisplay d = w.spawn(at, ItemDisplay.class, e -> {
							e.setPersistent(false);
							e.setItemStack(st);
							e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
							e.setViewRange(0);
							e.setInvisible(true);
							e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.01F), new Quaternionf()));
							e.setInterpolationDuration(0);
						});
						d.remove();
					} else {
						Material mat = blocks.get(k - itemN);
						BlockDisplay d = Fx.spawnBlockDisplay(at, mat.createBlockData(), false, Fx.FX_TAG, 0);
						d.setViewRange(0);
						d.remove();
					}
					done++;
				}
			} catch (Exception ex) {
				HobbangPlugin.get().getLogger().warning("보스 워밍업 실패: " + ex);
				task.cancel();
				return;
			}
			if (i[0] >= itemN + blockN) {
				HobbangPlugin.get().getLogger().info("보스 모델 워밍업 완료 (" + itemN + " 파츠, " + blockN + " 블록)");
				task.cancel();
			}
		}, 1L, 1L);
	}
}
