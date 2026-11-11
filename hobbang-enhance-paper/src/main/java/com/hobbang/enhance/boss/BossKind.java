package com.hobbang.enhance.boss;

import com.hobbang.enhance.item.HItem;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * 보스 4종. 히트박스는 바닐라 몹 + SCALE 속성.
 * v1.3.0: 컨셉에 맞춰 키움 (모델 높이 / 히트박스)
 *  대장장이 = 위더 스켈레톤 ×3.0 (2.1×7.2, 모델 9.0) — v1.2.0 의 2배
 *  콜로서스 = 철 골렘 ×2.5 (3.5×6.75, 모델 7.0)
 *  와이번 = 좀벌레 ×9.0 (3.6×2.7, 모델 6.0 / 날개폭 ~7.9)
 *  센티넬 = 늑대 ×5.6 (3.36×4.76, 모델 5.0)
 */
public enum BossKind {
	ABYSSAL_SMITH("abyssal_smith", "심연의 대장장이", HItem.ABYSS_CORE, Particle.SOUL_FIRE_FLAME, 0, EntityType.WITHER_SKELETON, 3.0, "slay"),
	PRISM_COLOSSUS("prism_colossus", "프리즘 콜로서스", HItem.PRISM_CORE, Particle.END_ROD, 0, EntityType.IRON_GOLEM, 2.5, "slay_prism"),
	TEMPEST_WYVERN("tempest_wyvern", "폭풍의 와이번", HItem.STORM_CORE, Particle.ELECTRIC_SPARK, 4, EntityType.SILVERFISH, 9.0, "slay_tempest"),
	CHRONO_SENTINEL("chrono_sentinel", "크로노 센티넬", HItem.CHRONO_CORE, Particle.WAX_ON, 2, EntityType.WOLF, 5.6, "slay_chrono");

	public final String id;
	public final String name;
	public final HItem core;
	public final Particle particle;
	public final int lift;
	public final EntityType hitbox;
	public final double scale;
	public final String advancement;

	BossKind(String id, String name, HItem core, Particle particle, int lift, EntityType hitbox, double scale, String adv) {
		this.id = id; this.name = name; this.core = core; this.particle = particle; this.lift = lift; this.hitbox = hitbox;
		this.scale = scale; this.advancement = adv;
	}

	public static BossKind byCore(ItemStack s) {
		HItem h = HItem.of(s);
		if (h == null) return null;
		for (BossKind k : values()) if (k.core == h) return k;
		return null;
	}

	public static BossKind byId(String id) {
		for (BossKind k : values()) if (k.id.equals(id)) return k;
		return null;
	}
}
