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

	/**
	 * v1.5.0: 첫 대장장이 소환 스파이크(~104ms)는 디스플레이 모델 로딩만이 아니라 보스 클래스 초기화,
	 * 위더 스켈레톤 + 장비 + 탑승 디스플레이(발광) 경로의 첫 실행 비용이 섞여 있다. 같은 경로를 한 번
	 * 미리 밟는다 (보이지 않는 더미, 바로 제거. 방송/투기장/소리 없음).
	 */
	private static void heavy(World w, Location at, List<ItemStack> items) {
		try {
			for (String c : new String[]{"SmithBoss", "ColossusBoss", "SentinelBoss", "WyvernBoss", "AssembleAnim", "BossPatterns", "BU", "Fx", "Rigid", "BossModels"})
				Class.forName("com.hobbang.enhance.boss." + c);
			Class.forName("com.hobbang.enhance.arena.ArenaDress");
			if (!w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
			org.bukkit.entity.WitherSkeleton ws = w.spawn(at, org.bukkit.entity.WitherSkeleton.class, e -> {
				e.setPersistent(false);
				e.setAI(false);
				e.setSilent(true);
				e.setInvisible(true);
				e.setRemoveWhenFarAway(true);
				var sc = e.getAttribute(org.bukkit.attribute.Attribute.SCALE);
				if (sc != null) sc.setBaseValue(3.0);
				if (e.getEquipment() != null) {
					e.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
					e.getEquipment().setItemInMainHand(com.hobbang.enhance.item.HItem.SMITH_HAMMER.create());
				}
			});
			List<org.bukkit.entity.Display> ds = new ArrayList<>();
			for (ItemStack st : items) {
				ItemDisplay d = w.spawn(at, ItemDisplay.class, e -> {
					e.setPersistent(false);
					e.setItemStack(st);
					e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
					e.setViewRange(0);
					e.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
					e.setGlowColorOverride(org.bukkit.Color.fromRGB(0x8040C0));
					e.setGlowing(true);
					e.setInterpolationDuration(3);
					e.setTransformation(new Transformation(new Vector3f(0, 1, 0), new Quaternionf().rotateX(0.4F), new Vector3f(1F), new Quaternionf()));
				});
				ws.addPassenger(d);
				ds.add(d);
				if (ds.size() >= 8) break;
			}
			Bukkit.getScheduler().runTaskLater(HobbangPlugin.get(), () -> {
				for (var d : ds) d.remove();
				ws.remove();
			}, 3L);
		} catch (Exception ex) {
			HobbangPlugin.get().getLogger().warning("보스 워밍업(히트박스 경로) 실패: " + ex);
		}
	}
}
