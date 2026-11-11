package com.hobbang.enhance.arena;

import com.hobbang.enhance.HobbangPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.util.List;
import java.util.function.Consumer;

/**
 * 관리자용 구조물 설치 (/hobbang structure place). 데이터팩 템플릿(data/hobbang/structure/*.nbt)을 읽어
 * 틱당 1,000블록씩 나눠 놓는다 (한 번에 놓으면 4.7만 블록 → 렉). 물리/이웃 갱신 없이 놓고, 끝나면 제단 PDC 를 찍는다.
 * 위치: 플레이어 발밑 = 투기장 바닥, 제단 = 플레이어 위치 (구조물 정중앙).
 */
public final class StructurePlacer {
	private static final int PER_TICK = 1000;

	private StructurePlacer() {}

	public static Structure template(Site s) {
		Structure st = Bukkit.getStructureManager().getStructure(s.key());
		if (st == null) st = Bukkit.getStructureManager().loadStructure(s.key());
		return st;
	}

	/** @return 예상 소요 틱 (템플릿이 없으면 -1) */
	public static int place(Site s, Location at, Consumer<Block> done) {
		Structure st = template(s);
		if (st == null || st.getPalettes().isEmpty()) return -1;
		BlockVector size = st.getSize();
		World w = at.getWorld();
		int ox = at.getBlockX() - size.getBlockX() / 2, oz = at.getBlockZ() - size.getBlockZ() / 2, oy = at.getBlockY() - 3;
		List<BlockState> blocks = st.getPalettes().get(0).getBlocks();
		int altarY = s == Site.TEMPLE ? 5 : 4;
		Block altar = w.getBlockAt(ox + size.getBlockX() / 2, oy + altarY, oz + size.getBlockZ() / 2);
		int[] cur = {0};
		BukkitTask[] task = {null};
		task[0] = Bukkit.getScheduler().runTaskTimer(HobbangPlugin.get(), () -> {
			int end = Math.min(blocks.size(), cur[0] + PER_TICK);
			for (int i = cur[0]; i < end; i++) {
				BlockState bs = blocks.get(i);
				w.getBlockAt(ox + bs.getX(), oy + bs.getY(), oz + bs.getZ()).setBlockData(bs.getBlockData(), false);
			}
			cur[0] = end;
			if (end % (PER_TICK * 4) < PER_TICK) w.spawnParticle(Particle.CLOUD, at.getX(), oy + 2 + (end * size.getBlockY()) / (double) blocks.size(), at.getZ(), 30, 8, 0.5, 8, 0.02);
			if (end >= blocks.size()) {
				task[0].cancel();
				Arenas.markAltar(altar, s);
				if (done != null) done.accept(altar);
			}
		}, 1, 1);
		return (blocks.size() + PER_TICK - 1) / PER_TICK;
	}
}
