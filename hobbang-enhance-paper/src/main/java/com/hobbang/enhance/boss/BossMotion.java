package com.hobbang.enhance.boss;

import java.util.ArrayList;
import java.util.List;

/** Visual timing only. Combat still owns every damage, movement and cooldown clock. */
final class BossMotion {
	enum Move { MELEE, STRIKE, SLAM, SWEEP, CAST, CHANNEL, PHASE }

	record Frame(float prepare, float release) {
		static final Frame REST = new Frame(0, 0);

		float strength() { return Math.min(1, prepare + release); }

		Frame blend(Frame other) {
			float p = Math.max(prepare, other.prepare);
			float r = Math.max(release, other.release);
			float sum = Math.max(1, p + r);
			return new Frame(p / sum, r / sum);
		}
	}

	private record Cue(Move move, int start, int hit, int live, int recovery) {
		int end() { return start + hit + live + recovery; }
	}

	private final List<Cue> cues = new ArrayList<>();

	void play(Move move, int now, int hit, int live, int recovery) {
		cues.add(new Cue(move, now, hit, live, recovery));
	}

	void expire(int now) {
		cues.removeIf(cue -> now >= cue.end() + 3);
	}

	Frame sample(Move move, int now, float delay) {
		Frame frame = Frame.REST;
		for (Cue cue : cues) {
			if (cue.move() == move) {
				frame = frame.blend(frame(now - cue.start() - delay, cue.hit(), cue.live(), cue.recovery()));
			}
		}
		return frame;
	}

	static Frame frame(float elapsed, int hit, int live, int recovery) {
		if (elapsed < 0 || elapsed >= hit + live + recovery) return Frame.REST;
		float before = hit - elapsed;
		if (before > 0) {
			float wind = ease(elapsed / Math.max(1F, hit * 0.62F));
			float follow = ease(1 - before / Math.min(4F, hit * 0.38F));
			return new Frame(wind * (1 - follow), wind * follow);
		}
		float after = -before;
		if (after < live) return new Frame(0, 1 - 0.35F * ease(after / Math.min(live, 12)));
		float endWeight = live == 0 ? 1 : 1 - 0.35F * ease(live / Math.min(live, 12F));
		return new Frame(0, endWeight * (1 - ease((after - live) / recovery)));
	}

	static Frame forecast(float cooldown, float rate, int lead) {
		return new Frame(ease(1 - cooldown / (rate * lead)), 0);
	}

	static float ease(float x) {
		float u = clamp(x, 0, 1);
		return u * u * (3 - 2 * u);
	}

	static float clamp(float x, float min, float max) { return Math.max(min, Math.min(max, x)); }

	static float mix(float from, float to, float weight) { return from + (to - from) * weight; }

	static float sin(float x) { return (float) Math.sin(x); }

	static float wrap(float angle) {
		angle %= 360;
		if (angle >= 180) return angle - 360;
		if (angle < -180) return angle + 360;
		return angle;
	}
}
