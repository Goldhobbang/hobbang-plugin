package com.hobbang.enhance.boss;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * v1.4.2 소환 조립 연출 상수/테마.
 * 실제 틱 로직은 DisplayBoss 에 있다 (파츠·라이더 오프셋에 접근하려고).
 * 총 90틱(4.5초). 파츠는 interpolation_duration 으로만 날아오며, 틱마다 변환을 쓰지 않는다.
 */
final class AssembleAnim {
	enum Style { ASSEMBLE, DIVE, EMERGE, COMPRESS }

	static final int DURATION = 90;
	/** 흩어진 모습이 보이는 채 멈춰 있는 틱 */
	static final int HOLD = 4;
	/** 한 파츠가 제자리로 날아가는 시간 */
	static final int FLIGHT = 16;
	/** 역할 간 출발 간격 */
	static final int STAGGER = 12;

	/* v1.4.3 와이번 급강하 */
	static final int DIVE_HANG = 8;
	static final int DIVE_FLIGHT = 28;
	static final int DIVE_SPREAD = 16;
	static final int DIVE_DURATION = DIVE_HANG + DIVE_FLIGHT + DIVE_SPREAD + 4; // 56

	/* v1.4.3 콜로서스 기어오름 */
	static final int EMERGE_RISE = 32;
	static final int EMERGE_DURATION = 58;

	/* v1.4.3 센티넬 수축 후 전개 */
	static final int COMP_IN = 16;
	static final int COMP_HOLD = 8;
	static final int COMP_OUT = 20;
	static final int COMP_DURATION = HOLD + COMP_IN + COMP_HOLD + COMP_OUT + 4; // 52

	static Style style(BossKind kind) {
		return switch (kind) {
			case ABYSSAL_SMITH -> Style.ASSEMBLE;
			case TEMPEST_WYVERN -> Style.DIVE;
			case PRISM_COLOSSUS -> Style.EMERGE;
			case CHRONO_SENTINEL -> Style.COMPRESS;
		};
	}

	static int duration(Style style) {
		return switch (style) {
			case ASSEMBLE -> DURATION;
			case DIVE -> DIVE_DURATION;
			case EMERGE -> EMERGE_DURATION;
			case COMPRESS -> COMP_DURATION;
		};
	}

	private AssembleAnim() {}

	/**
	 * 0 다리 → 1 몸통 → 2 팔/날개/꼬리 → 3 머리/고리/시계 → 4 무기 → 5 장식.
	 * id 가 없으면 휴식 자세 높이로 나눈다.
	 */
	static int role(String id, float restY, float height) {
		if (id != null && !id.isEmpty()) {
			String s = id.toLowerCase();
			if (s.contains("leg")) return 0;
			if (s.contains("hammer") || s.contains("weapon")) return 4;
			if (s.contains("head") || s.contains("ring") || s.contains("clock") || s.contains("crown")
				|| s.contains("eye") || s.contains("horn") || s.contains("jaw") || s.contains("neck")
				|| s.contains("face") || s.contains("bell") || s.contains("pend") || s.contains("label")
				|| s.contains("fin")) return 3;
			if (s.contains("arm") || s.contains("wing") || s.contains("tail") || s.contains("fist")
				|| s.contains("shoulder")) return 2;
			if (s.contains("orbit") || s.contains("back") || s.contains("chest") || s.contains("core")) {
				if (s.contains("clock")) return 3;
				return s.contains("orbit") || s.contains("back") ? 5 : 1;
			}
			if (s.contains("torso") || s.equals("body") || s.contains("belly")) return 1;
		}
		if (height < 0.5F) height = 4F;
		float k = Math.max(0F, Math.min(1F, restY / height));
		if (k < 0.22F) return 0;
		if (k < 0.50F) return 1;
		if (k < 0.72F) return 2;
		if (k < 0.90F) return 3;
		return 5;
	}

	static int startTick(int role) {
		return HOLD + Math.max(0, role) * STAGGER;
	}

	record Theme(Material[] blocks, Sound[] launch, Sound snap, Sound finish, int glowRgb) {}

	static Theme theme(BossKind kind) {
		return switch (kind) {
			case ABYSSAL_SMITH -> new Theme(
				new Material[]{Material.MAGMA_BLOCK, Material.BASALT, Material.POLISHED_BASALT, Material.ANCIENT_DEBRIS,
					Material.NETHER_BRICKS, Material.BLACKSTONE, Material.SOUL_SAND, Material.LAVA, Material.GILDED_BLACKSTONE},
				new Sound[]{Sound.BLOCK_LAVA_POP, Sound.BLOCK_NETHER_BRICKS_PLACE, Sound.ITEM_FIRECHARGE_USE, Sound.BLOCK_BASALT_PLACE},
				Sound.BLOCK_ANVIL_PLACE,
				Sound.BLOCK_ANVIL_LAND,
				0xFF4400);
			case PRISM_COLOSSUS -> new Theme(
				new Material[]{Material.AMETHYST_BLOCK, Material.AMETHYST_CLUSTER, Material.BUDDING_AMETHYST,
					Material.PURPLE_STAINED_GLASS, Material.SMOOTH_BASALT, Material.MEDIUM_AMETHYST_BUD, Material.AMETHYST_CLUSTER},
				new Sound[]{Sound.BLOCK_AMETHYST_BLOCK_CHIME, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, Sound.BLOCK_AMETHYST_BLOCK_PLACE},
				Sound.BLOCK_AMETHYST_BLOCK_RESONATE,
				Sound.BLOCK_AMETHYST_BLOCK_RESONATE,
				0xC080FF);
			case CHRONO_SENTINEL -> new Theme(
				new Material[]{Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER, Material.GOLD_BLOCK, Material.LIGHTNING_ROD,
					Material.COPPER_BULB, Material.WEATHERED_CUT_COPPER, Material.WAXED_CUT_COPPER},
				new Sound[]{Sound.BLOCK_COPPER_PLACE, Sound.BLOCK_COPPER_BREAK, Sound.ITEM_ARMOR_EQUIP_IRON},
				Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE,
				Sound.BLOCK_BELL_USE,
				0x40FFD0);
			case TEMPEST_WYVERN -> new Theme(
				new Material[]{Material.DARK_PRISMARINE, Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.SEA_LANTERN,
					Material.CYAN_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS},
				new Sound[]{Sound.ENTITY_PHANTOM_FLAP, Sound.ENTITY_ENDER_DRAGON_FLAP, Sound.ENTITY_WIND_CHARGE_WIND_BURST},
				Sound.ENTITY_LIGHTNING_BOLT_IMPACT,
				Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
				0x60F0FF);
		};
	}

	static void burst(org.bukkit.World w, double x, double y, double z, BossKind kind, int n) {
		switch (kind) {
			case ABYSSAL_SMITH -> {
				w.spawnParticle(Particle.LAVA, x, y, z, Math.max(2, n / 2), 0.35, 0.35, 0.35, 0);
				w.spawnParticle(Particle.FLAME, x, y, z, n, 0.45, 0.4, 0.45, 0.02);
				w.spawnParticle(Particle.SOUL_FIRE_FLAME, x, y, z, Math.max(2, n / 2), 0.3, 0.35, 0.3, 0.01);
			}
			case PRISM_COLOSSUS -> {
				w.spawnParticle(Particle.END_ROD, x, y, z, n, 0.4, 0.4, 0.4, 0.02);
				w.spawnParticle(Particle.DUST, x, y, z, n, 0.45, 0.45, 0.45, 0, BU.dust(0xC080FF, 1.15F));
				w.spawnParticle(Particle.BLOCK, x, y, z, n, 0.35, 0.35, 0.35, 0.08, Material.AMETHYST_BLOCK.createBlockData());
			}
			case CHRONO_SENTINEL -> {
				w.spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, n, 0.4, 0.4, 0.4, 0.02);
				w.spawnParticle(Particle.WAX_ON, x, y, z, Math.max(2, n / 2), 0.35, 0.35, 0.35, 0.01);
				w.spawnParticle(Particle.ENCHANT, x, y, z, n, 0.4, 0.5, 0.4, 0.3);
			}
			case TEMPEST_WYVERN -> {
				w.spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, n, 0.45, 0.45, 0.45, 0.05);
				w.spawnParticle(Particle.CLOUD, x, y, z, Math.max(2, n / 2), 0.4, 0.25, 0.4, 0.01);
				w.spawnParticle(Particle.FIREWORK, x, y, z, Math.max(1, n / 3), 0.3, 0.3, 0.3, 0.02);
			}
		}
	}
}
