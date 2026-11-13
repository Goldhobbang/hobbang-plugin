package com.hobbang.enhance.boss;

import java.util.Locale;

import static com.hobbang.enhance.boss.BossMotion.*;

/** Samples the live pack-model pose functions without Paper or a Minecraft client. */
public final class BossPosePreview {
	private static BossPoses.Sink output(String boss, String title) {
		return (name, rig, scale) -> System.out.printf(Locale.ROOT,
			"%s\t%s\t%s\t%.8f\t%.8f\t%.8f\t%.8f\t%.8f\t%.8f\t%.8f\t%.8f%n",
			boss, title, name, rig.pos().x, rig.pos().y, rig.pos().z,
			rig.rot().x, rig.rot().y, rig.rot().z, rig.rot().w, scale);
	}

	private static void smith() {
		String boss = "abyssal_smith";
		BossPoses.smith(output(boss, "Idle"), BossMotion.Idle.preview(0), 0, 0, 0, 0, 0,
			Frame.REST, Frame.REST, Frame.REST, Frame.REST, Frame.REST, Frame.REST);
		String[] titles = {"Stride", "Hammer raised", "Impact", "Recovery"};
		int[] ages = {-1, 6, 10, 16};
		for (int i = 0; i < titles.length; i++) {
			BossPoses.smith(output(boss, titles[i]), BossMotion.Idle.preview(6 + i * 4), 6 + i * 4, 0, i == 0 ? 0.9F : 0,
				i == 0 ? 22 : 0, 3, frame(ages[i], 10, 0, 10), frame(ages[i] - 1.5F, 10, 0, 10),
				Frame.REST, Frame.REST, Frame.REST, Frame.REST);
		}
	}

	private static void colossus() {
		String boss = "prism_colossus";
		BossPoses.colossus(output(boss, "Stride"), BossMotion.Idle.preview(17), 17, 0, 0.95F, 15, -4,
			Frame.REST, Frame.REST, Frame.REST, Frame.REST, Frame.REST, Frame.REST);
		BossPoses.colossus(output(boss, "Double-fist windup"), BossMotion.Idle.preview(31), 31, 0, 0, 3, 4,
			frame(13, 20, 0, 6), Frame.REST, Frame.REST, Frame.REST, Frame.REST, Frame.REST);
		BossPoses.colossus(output(boss, "Slam impact"), BossMotion.Idle.preview(38), 38, 0, 0, 3, 5,
			frame(20, 20, 0, 6), Frame.REST, Frame.REST, Frame.REST, Frame.REST, Frame.REST);
		BossPoses.colossus(output(boss, "Prism release"), BossMotion.Idle.preview(65), 65, 0, 0, -21, 8,
			Frame.REST, Frame.REST, frame(30, 30, 30, 12), Frame.REST, Frame.REST, Frame.REST);
	}

	private static void sentinel() {
		String boss = "chrono_sentinel";
		BossPoses.sentinel(output(boss, "Tracking"), BossMotion.Idle.preview(0), 0, 32, 1, 24, 10, 0,
			Frame.REST, Frame.REST, Frame.REST, Frame.REST, Frame.REST);
		BossPoses.sentinel(output(boss, "Time stop windup"), BossMotion.Idle.preview(0), 0, 72, 2, 0, 10, 0,
			Frame.REST, Frame.REST, Frame.REST, frame(44, 61, 0, 12), Frame.REST);
		BossPoses.sentinel(output(boss, "Gears stopped"), BossMotion.Idle.preview(0), 0, 72, 2, 0, 10, 0,
			Frame.REST, Frame.REST, Frame.REST, frame(0, 0, 8, 12), Frame.REST);
		BossPoses.sentinel(output(boss, "Blade follow-through"), BossMotion.Idle.preview(0), 0, 101, 2, 0, 6, 0,
			frame(20, 19, 15, 12), frame(18, 19, 15, 12), frame(29, 25, 50, 12), Frame.REST, Frame.REST);
	}

	private static void wyvern() {
		String boss = "tempest_wyvern";
		BossPoses.wyvern(output(boss, "Upstroke and turn"), BossMotion.Idle.preview(16), 16, 0,
			new BossPoses.Flight(1.14F, 1, 1, 0.21F, -21, 35, -6, 22, 0, 20, 10, 0, 0),
			Frame.REST, Frame.REST, Frame.REST, Frame.REST);
		BossPoses.wyvern(output(boss, "Downstroke"), BossMotion.Idle.preview(24), 24, 0,
			new BossPoses.Flight(4.2F, 1, 1, 0.05F, -21, 35, -6, 0, 0, 4, 4, 0, 0),
			Frame.REST, Frame.REST, Frame.REST, Frame.REST);
		BossPoses.wyvern(output(boss, "Landing compression"), BossMotion.Idle.preview(36), 36, 0,
			new BossPoses.Flight(3, 0.15F, 0.09F, 0.79F, -32, 3, 1, 0, 0.85F, 0, 0, 0.12F, 0),
			Frame.REST, Frame.REST, Frame.REST, Frame.REST);
		BossPoses.wyvern(output(boss, "Rest and wings folded"), BossMotion.Idle.preview(50), 50, 0,
			new BossPoses.Flight(4.2F, 0, 0, 0.86F, -32, 2, 0, 0, 0, 0, 0, 0, 0),
			Frame.REST, Frame.REST, Frame.REST, Frame.REST);
	}

	public static void main(String[] args) {
		smith();
		colossus();
		sentinel();
		wyvern();
	}
}
