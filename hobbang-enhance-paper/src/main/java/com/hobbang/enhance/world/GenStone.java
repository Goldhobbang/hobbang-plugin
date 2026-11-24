package com.hobbang.enhance.world;

import com.hobbang.enhance.Keys;
import com.hobbang.enhance.enhance.EnhanceConfig;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 강화석 드롭 대상 = 자연 생성된 돌/심층암/응회암만.
 * 플레이어가 놓았거나 용암+물로 생긴(돌 생성기) 블록, 피스톤으로 옮긴 블록은 청크 PDC 에 위치를 기록해 두고 드롭을 막는다.
 * 기록은 Y {@code stoneDropMaxY} 아래만, 청크당 최대 {@link #CAP}개 (넘치면 오래된 것부터 버림).
 */
public final class GenStone implements Listener {
	static final NamespacedKey KEY = Keys.k("gen_stone");
	static final int CAP = 4096;

	static boolean isStoneLike(Material m) {
		return m == Material.STONE || m == Material.DEEPSLATE || m == Material.TUFF;
	}

	private static long pack(Block b) {
		return ((long) (b.getX() & 15) << 60) | ((long) (b.getZ() & 15) << 56) | ((b.getY() + 2048L) & 0xFFFFL);
	}

	private static long[] get(PersistentDataContainer pdc) {
		if (!pdc.has(KEY, PersistentDataType.LONG_ARRAY)) return new long[0];
		long[] a = pdc.get(KEY, PersistentDataType.LONG_ARRAY);
		return a == null ? new long[0] : a;
	}

	static boolean isMarked(Block b) {
		long k = pack(b);
		for (long v : get(b.getChunk().getPersistentDataContainer())) if (v == k) return true;
		return false;
	}

	static void mark(Block b) {
		if (b.getY() >= EnhanceConfig.CFG.stoneDropMaxY) return;
		Chunk c = b.getChunk();
		PersistentDataContainer pdc = c.getPersistentDataContainer();
		long k = pack(b);
		long[] a = get(pdc);
		for (long v : a) if (v == k) return;
		long[] n;
		if (a.length >= CAP) {   // 오래된 기록부터 밀어낸다
			n = new long[CAP];
			System.arraycopy(a, a.length - CAP + 1, n, 0, CAP - 1);
			n[CAP - 1] = k;
		} else {
			n = java.util.Arrays.copyOf(a, a.length + 1);
			n[a.length] = k;
		}
		pdc.set(KEY, PersistentDataType.LONG_ARRAY, n);
	}

	/** 기록이 있으면 지우고 true (= 드롭 금지) */
	static boolean consume(Block b) {
		PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
		long[] a = get(pdc);
		if (a.length == 0) return false;
		long k = pack(b);
		int idx = -1;
		for (int i = 0; i < a.length; i++) if (a[i] == k) { idx = i; break; }
		if (idx < 0) return false;
		if (a.length == 1) pdc.remove(KEY);
		else {
			long[] n = new long[a.length - 1];
			System.arraycopy(a, 0, n, 0, idx);
			System.arraycopy(a, idx + 1, n, idx, a.length - idx - 1);
			pdc.set(KEY, PersistentDataType.LONG_ARRAY, n);
		}
		return true;
	}

	/** 용암+물로 생긴 돌 (돌 생성기) */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onForm(BlockFormEvent e) {
		if (isStoneLike(e.getNewState().getType())) mark(e.getBlock());
	}

	/** 플레이어가 놓은 돌 (섬세한 손길로 캔 돌 등) */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent e) {
		if (isStoneLike(e.getBlockPlaced().getType())) mark(e.getBlockPlaced());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onExtend(BlockPistonExtendEvent e) { moved(e, e.getBlocks(), true); }

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onRetract(BlockPistonRetractEvent e) { moved(e, e.getBlocks(), false); }

	/** 피스톤으로 옮긴 돌은 도착 칸을 기록 (생성기 돌을 밀어 내는 농장 방지) */
	private static void moved(BlockPistonEvent e, List<Block> blocks, boolean extend) {
		if (blocks.isEmpty() || !(e.getBlock().getBlockData() instanceof Directional d)) return;
		BlockFace to = extend ? d.getFacing() : d.getFacing().getOppositeFace();
		for (Block b : blocks) if (isStoneLike(b.getType())) mark(b.getRelative(to));
	}
}
