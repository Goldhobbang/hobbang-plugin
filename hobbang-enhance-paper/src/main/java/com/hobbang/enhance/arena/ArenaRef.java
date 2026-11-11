package com.hobbang.enhance.arena;

import com.hobbang.enhance.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;

/** 보스가 묶여 있는 투기장 (제단 블록 좌표). 몹 PDC "site;world;x;y;z" */
public record ArenaRef(Site site, World world, int x, int y, int z) {
	public static ArenaRef read(Mob m) {
		String s = m.getPersistentDataContainer().get(Keys.ARENA, PersistentDataType.STRING);
		if (s == null) return null;
		try {
			String[] a = s.split(";");
			World w = Bukkit.getWorld(a[1]);
			if (w == null) return null;
			return new ArenaRef(Site.valueOf(a[0]), w, Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]));
		} catch (Exception e) {
			return null;
		}
	}

	public static ArenaRef at(Site site, Block altar) {
		return new ArenaRef(site, altar.getWorld(), altar.getX(), altar.getY(), altar.getZ());
	}

	public void write(Mob m) {
		m.getPersistentDataContainer().set(Keys.ARENA, PersistentDataType.STRING, site.name() + ";" + world.getName() + ";" + x + ";" + y + ";" + z);
	}

	public boolean contains(World w, double bx, double by, double bz) {
		if (w != world) return false;
		return Math.abs(bx - (x + 0.5)) <= site.half + 0.5 && Math.abs(bz - (z + 0.5)) <= site.half + 0.5 && by >= y + site.yLo && by <= y + site.yHi + 1;
	}

	public boolean contains(Block b) {
		return contains(b.getWorld(), b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
	}

	public boolean contains(Location l) {
		return contains(l.getWorld(), l.getX(), l.getY(), l.getZ());
	}

	public Location spawn() {
		return new Location(world, x + site.spawnDx, y + site.spawnDy, z + site.spawnDz);
	}

	public Location center() {
		return new Location(world, x + 0.5, y, z + 0.5);
	}
}
