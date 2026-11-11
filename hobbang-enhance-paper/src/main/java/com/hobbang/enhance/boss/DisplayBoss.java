package com.hobbang.enhance.boss;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 블록/아이템/텍스트 디스플레이로 외형을 만드는 보스 (Fabric DisplayBoss 포팅).
 * 본체 = 투명한 바닐라 몹 히트박스, 파츠 = 본체에 탑승한 디스플레이.
 * - 파츠는 setPersistent(false) → 디스크에 저장되지 않음. 청크 로드 시 컨트롤러가 다시 만든다 (고아 불가)
 * - 같은 모양이면 변환 패킷을 보내지 않음 / 애니메이션은 3틱 간격 + 같은 보간 시간
 */
public abstract class DisplayBoss extends Boss {
	public static final int ANIM_INTERVAL = 3;

	protected int hurtFlash = 0;
	protected int animTick = 0;
	protected final List<Part> parts = new ArrayList<>();
	private boolean partsBuilt = false;
	private final Vector3f riderOff = new Vector3f();
	/** v1.3.0: 리소스팩 모델 사용 여부 (파츠를 만들 때 결정, 설정이 바뀌면 다시 만든다) */
	protected boolean modelMode = false;
	/** v1.3.0: 예전 블록 모델을 새 보스 크기에 맞춰 키우는 배율 (리소스팩 모델은 이미 새 크기라 1) */
	protected float vs = 1.0F;
	/** v1.4.2: 소환 조립 연출. -1 = 비활성, 0+ = 경과 틱 */
	private int assembleTick = -1;
	private boolean assembleInit = false;
	private boolean assembleFrozen = false;
	private boolean savedAi = true;
	private Transformation[] assembleRest;
	private int[] assembleStartAt;
	private boolean[] assembleLaunched;
	private final List<Display> assembleShards = new ArrayList<>();
	private int[] shardStartAt;
	private Transformation[] shardArrive;
	private Transformation[] shardGone;
	private boolean[] shardLaunched;
	private boolean[] shardShrinking;
	private final boolean[] roleLaunched = new boolean[6];
	private int assembleWait = 0;
	private AssembleAnim.Style assembleStyle = AssembleAnim.Style.ASSEMBLE;
	// v1.4.4 사망 연출
	protected boolean dying = false;
	protected int deathTick = 0, deathLen = 0;
	protected org.bukkit.entity.Player deathKiller;
	/** v1.5.0: 마지막으로 때린 플레이어 (치명타 이벤트에서 못 구했을 때 방송용) */
	protected org.bukkit.entity.Player lastHitter;
	protected org.bukkit.Location deathAt;
	private Transformation[] deathRest;
	private Transformation[] assembleCompressed;

	public static final class Part {
		public final Display display;
		public final Vector3f offset;
		public final Vector3f size;
		public final boolean glowPart;
		/** 모델 파츠 이름 (torso, head, …). 블록 모델은 null → 조립 순서는 높이로 */
		public String id;
		Transformation last;

		Part(Display display, Vector3f offset, Vector3f size, boolean glowPart) {
			this.display = display; this.offset = offset; this.size = size; this.glowPart = glowPart;
		}

		public void setBlock(BlockData data) {
			if (display instanceof BlockDisplay bd) bd.setBlock(data);
		}
	}

	protected DisplayBoss(Mob mob, BossKind kind) {
		super(mob, kind);
	}

	/** 리소스팩 모델을 쓸지 (설정 bossModels: auto / on / off) */
	public static boolean modelsEnabled() {
		String m = com.hobbang.enhance.enhance.EnhanceConfig.CFG.bossModels;
		if (m != null && m.equalsIgnoreCase("on")) return true;
		if (m != null && m.equalsIgnoreCase("off")) return false;
		String rp = org.bukkit.Bukkit.getResourcePack();
		return rp != null && !rp.isBlank();
	}

	/** 예전 블록 모델의 배율 (보스 크기 / v1.2.0 크기) */
	protected abstract float legacyScale();

	protected abstract void buildParts();
	protected abstract void poseParts(float t);
	protected abstract void bossTick(List<Player> near, LivingEntity target);
	protected abstract void onPhaseChange(int newPhase);

	protected float modelYaw() {
		return mob.getBodyYaw();
	}

	protected boolean glowParts() { return false; }
	protected int glowColor() { return 0xFFFFFF; }
	protected float damageMultiplier() { return 1.0F; }
	protected void onHurtBy(EntityDamageEvent e, double amount) {}
	protected void onPartsRebuilt() {}

	// ───────── 파츠 ─────────
	private Location spawnAt() {
		Location l = mob.getLocation();
		l.setYaw(0);
		l.setPitch(0);
		return l;
	}

	protected Part block(BlockData state, float ox, float oy, float oz, float sx, float sy, float sz, boolean bright) {
		BlockDisplay d = Fx.spawnBlockDisplay(spawnAt(), state, bright, Fx.PART_TAG, ANIM_INTERVAL);
		return attach(d, new Vector3f(ox, oy, oz), new Vector3f(sx, sy, sz), bright);
	}

	protected Part item(ItemStack stack, float cx, float cy, float cz, float scale, boolean bright) {
		ItemDisplay d = mob.getWorld().spawn(spawnAt(), ItemDisplay.class, e -> {
			e.setPersistent(false);
			e.setItemStack(stack);
			e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
			e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf()));
			e.setInterpolationDuration(ANIM_INTERVAL);
			if (bright) e.setBrightness(new Display.Brightness(15, 15));
			e.addScoreboardTag(Fx.PART_TAG);
		});
		return attach(d, new Vector3f(cx, cy, cz), new Vector3f(scale, scale, scale), bright);
	}

	/** v1.3.0: 리소스팩 모델 파츠 (hobbang:boss/<boss>/<part>). 모델 원점 = 피벗, 크기 = 매니페스트의 item_display_scale */
	protected Part model(String part, boolean bright) {
		BossModels.Spec sp = BossModels.get(kind.id, part);
		ItemStack st = new ItemStack(org.bukkit.Material.PAPER);
		st.editMeta(m -> m.setItemModel(new org.bukkit.NamespacedKey("hobbang", sp.key())));
		Part p = item(st, 0, 0, 0, sp.scale(), bright);
		p.id = part;
		return p;
	}

	/** 피벗 (보스 발밑 기준, 블록 단위) */
	protected static Vector3f pv(BossKind k, String part) {
		return BossModels.get(k.id, part).pivot();
	}

	/** 부모 파츠 리그에서 자식 파츠 피벗으로 이동한 리그 */
	protected Rigid child(Rigid parent, String parentPart, String childPart) {
		Vector3f a = pv(kind, parentPart), b = pv(kind, childPart);
		return parent.at(b.x - a.x, b.y - a.y, b.z - a.z);
	}

	/**
	 * v1.5.0 박스 리그 관절: 부모 좌표계에서 오프셋만큼 옮긴 뒤 rest 회전을 건다 (모델 element 회전 한계를 넘는
	 * 기울기는 여기, 즉 item_display 변환으로 낸다). 애니메이션 회전은 반환값 뒤에 이어 붙인다.
	 */
	protected Rigid joint(Rigid parent, String part) {
		Vector3f[] j = BossModels.rig(kind.id, part);
		return parent.at(j[0].x, j[0].y, j[0].z).rotX(j[1].x).rotY(j[1].y).rotZ(j[1].z);
	}

	/** 리그 로컬 좌표 → 월드 좌표 (패턴 시작점: 망치/입/코어) */
	protected org.bukkit.util.Vector worldPoint(Rigid r, float lx, float ly, float lz) {
		Vector3f p = new Vector3f(lx, ly, lz).rotate(r.rot()).add(r.pos()).mul(vs);
		Location l = mob.getLocation();
		return new org.bukkit.util.Vector(l.getX() + p.x, l.getY() + p.y, l.getZ() + p.z);
	}

	protected Part text(Component c, float cx, float cy, float cz, float scale) {
		TextDisplay d = mob.getWorld().spawn(spawnAt(), TextDisplay.class, e -> {
			e.setPersistent(false);
			e.text(c);
			e.setBillboard(Display.Billboard.CENTER);
			e.setBackgroundColor(Color.fromARGB(0x40, 0, 0, 0));
			e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf()));
			e.setInterpolationDuration(ANIM_INTERVAL);
			e.setBrightness(new Display.Brightness(15, 15));
			e.addScoreboardTag(Fx.PART_TAG);
		});
		Part tp = attach(d, new Vector3f(cx, cy, cz), new Vector3f(scale, scale, scale), true);
		tp.id = "label";
		return tp;
	}

	private Part attach(Display d, Vector3f offset, Vector3f size, boolean bright) {
		d.setViewRange(2.0F);
		mob.addPassenger(d);
		Part p = new Part(d, offset, size, bright);
		parts.add(p);
		Fx.LIVE++;
		return p;
	}

	/** 바닐라 몹의 탑승 위치(머리 위)만큼 내려서, 원점을 히트박스 발밑으로 맞춘다 */
	private void updateRiderOffset() {
		for (Part p : parts) {
			if (!p.display.isValid() || p.display.getVehicle() != mob) continue;
			Location dl = p.display.getLocation(), ml = mob.getLocation();
			riderOff.set((float) (dl.getX() - ml.getX()), (float) (dl.getY() - ml.getY()), (float) (dl.getZ() - ml.getZ()));
			return;
		}
	}

	protected void place(Part p, Rigid r) {
		place(p, r, 1.0F);
	}

	protected void place(Part p, Rigid r, float scaleMul) {
		Vector3f s = new Vector3f(p.size).mul(scaleMul * vs);
		Vector3f off = new Vector3f(p.offset).mul(scaleMul * vs);
		Vector3f t = off.rotate(r.rot()).add(new Vector3f(r.pos()).mul(vs)).sub(riderOff);
		Transformation tr = new Transformation(t, new Quaternionf(r.rot()), s, new Quaternionf());
		if (tr.equals(p.last)) return;   // v1.5.1: 모양이 그대로면 패킷 없음
		p.last = tr;
		p.display.setTransformation(tr);
		p.display.setInterpolationDelay(0);
	}

	private void discardParts() {
		for (Part p : parts) { Fx.forget(p.display); p.display.remove(); Fx.LIVE--; }
		parts.clear();
		for (Entity e : new ArrayList<>(mob.getPassengers())) {
			if (e.getScoreboardTags().contains(Fx.PART_TAG)) e.remove();
		}
	}

	private void ensureParts() {
		boolean broken = !partsBuilt;
		if (!broken && tickCount % 20 == 0 && modelMode != modelsEnabled()) broken = true;
		if (!broken && tickCount % 20 == 0) {
			for (Part p : parts) {
				if (!p.display.isValid() || p.display.getVehicle() != mob) { broken = true; break; }
			}
		}
		if (broken) {
			discardParts();
			modelMode = modelsEnabled();
			vs = modelMode ? 1.0F : legacyScale();
			buildParts();
			partsBuilt = true;
			onPartsRebuilt();
			updateRiderOffset();
			if (assembling()) {
				assembleInit = false;
			} else {
				poseParts(animTick);
			}
		}
	}

	@Override
	public void unload() {
		super.unload();
		cancelAssemble(false);
		discardParts();
	}

	// ───────── 틱 ─────────
	@Override
	protected void serverTick() {
		if (dead) return;
		if (dying) { tickDeath(); return; }
		tickParts();
		if (assembling()) return;
		aiStep();
	}

	/** 파츠 유지 + 애니메이션 (3틱 간격) + 피격 번쩍임 */
	protected void tickParts() {
		ensureParts();
		if (assembling()) {
			tickAssemble();
			if (hurtFlash > 0 && --hurtFlash == 0) setFlash(false);
			return;
		}
		animTick++;
		if (animTick % ANIM_INTERVAL == 0) {
			updateRiderOffset();
			poseParts(animTick);
		}
		if (hurtFlash > 0 && --hurtFlash == 0) setFlash(false);
	}


	// ───────── v1.4.2 소환 조립 ─────────
	public boolean assembling() {
		return assembleTick >= 0;
	}

	/** 방금 소환됐을 때만 호출 (청크 로드 재부착에서는 호출하지 않음) */
	public void startAssemble() {
		assembleStyle = AssembleAnim.style(kind);
		assembleTick = 0;
		assembleInit = false;
		assembleWait = 0;
		invulTicks = Math.max(invulTicks, AssembleAnim.duration(assembleStyle) + 12);
		freezeForAssemble();
		if (kind == BossKind.ABYSSAL_SMITH) {
			mob.setInvisible(true);
			org.bukkit.inventory.EntityEquipment eq = mob.getEquipment();
			if (eq != null) {
				eq.setHelmet(null);
				eq.setItemInMainHand(null);
			}
		}
	}

	private void freezeForAssemble() {
		if (assembleFrozen) return;
		assembleFrozen = true;
		savedAi = mob.hasAI();
		mob.setAI(false);
		mob.setTarget(null);
		mob.setVelocity(new org.bukkit.util.Vector());
	}

	private void unfreezeAfterAssemble() {
		if (!assembleFrozen) return;
		assembleFrozen = false;
		mob.setAI(savedAi);
	}

	private void tickAssemble() {
		if (!partsBuilt) return;
		if (!assembleInit) {
			updateRiderOffset();
			// 탑승 오프셋이 잡히기 전에 포즈를 잡으면 파츠가 공중에 조립됐다가 나중에 내려앉는다
			if (assembleWait++ < 3 && Math.abs(riderOff.y) < 0.2F) return;
			if (Math.abs(riderOff.y) < 0.2F) riderOff.set(0, (float) Math.max(1.0, mob.getHeight() * 0.5), 0);
			initAssemble();
			assembleInit = true;
			return;
		}
		assembleTick++;
		Location l = mob.getLocation();
		World w = mob.getWorld();
		double bh = Math.max(2.0, mob.getHeight());
		AssembleAnim.Theme theme = AssembleAnim.theme(kind);
		switch (assembleStyle) {
			case ASSEMBLE -> tickAssembleClassic(theme, l, w, bh);
			case DIVE -> tickDive(theme, l, w, bh);
			case EMERGE -> tickEmerge(theme, l, w, bh);
			case COMPRESS -> tickCompress(theme, l, w, bh);
		}
		mob.setVelocity(new org.bukkit.util.Vector());
		if (assembleTick >= AssembleAnim.duration(assembleStyle)) finishAssemble(theme);
	}

	private void tickAssembleClassic(AssembleAnim.Theme theme, Location l, World w, double bh) {
		if (assembleTick % 3 == 0) {
			double r = 5.5 - 4.0 * assembleTick / AssembleAnim.DURATION;
			double a = assembleTick * 0.35;
			for (int i = 0; i < 3; i++) {
				double ang = a + i * (Math.PI * 2 / 3);
				AssembleAnim.burst(w, l.getX() + Math.cos(ang) * r, l.getY() + 0.4 + bh * 0.35, l.getZ() + Math.sin(ang) * r, kind, 3);
			}
		}
		if (assembleRest != null) {
			for (int i = 0; i < parts.size() && i < assembleRest.length; i++) {
				if (!assembleLaunched[i] && assembleTick >= assembleStartAt[i]) launchPart(i, theme, AssembleAnim.FLIGHT);
			}
		}
		tickShards();
	}

	private void tickDive(AssembleAnim.Theme theme, Location l, World w, double bh) {
		float prog = 0;
		if (assembleTick > AssembleAnim.DIVE_HANG) {
			prog = Math.min(1F, (assembleTick - AssembleAnim.DIVE_HANG) / (float) AssembleAnim.DIVE_FLIGHT);
		}
		double hy = l.getY() + (1.0 - prog) * 22.0 + bh * 0.3;
		if (assembleTick % 2 == 0 && assembleTick <= AssembleAnim.DIVE_HANG + AssembleAnim.DIVE_FLIGHT) {
			w.spawnParticle(Particle.CLOUD, l.getX(), hy, l.getZ(), 6, 0.8, 0.4, 0.8, 0.02);
			w.spawnParticle(Particle.ELECTRIC_SPARK, l.getX(), hy, l.getZ(), 8, 0.6, 0.5, 0.6, 0.05);
		}
		if (assembleTick == AssembleAnim.DIVE_HANG && assembleRest != null) {
			BU.sound(l, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 2.2F, 0.7F);
			BU.sound(l, Sound.ENTITY_WIND_CHARGE_WIND_BURST, SoundCategory.HOSTILE, 1.6F, 0.6F);
			for (int i = 0; i < parts.size() && i < assembleRest.length; i++) {
				Part p = parts.get(i);
				if (!p.display.isValid() || assembleRest[i] == null) continue;
				p.display.setInterpolationDuration(AssembleAnim.DIVE_FLIGHT);
				p.display.setTransformation(assembleRest[i]);
				p.display.setInterpolationDelay(0);
				p.last = assembleRest[i];
			}
		}
		if (assembleTick == AssembleAnim.DIVE_HANG + AssembleAnim.DIVE_FLIGHT) {
			BU.lightning(w, l.getX(), l.getY(), l.getZ());
			AssembleAnim.burst(w, l.getX(), l.getY() + 0.2, l.getZ(), kind, 24);
			w.spawnParticle(Particle.EXPLOSION, l.getX(), l.getY() + 0.4, l.getZ(), 6, 1.2, 0.3, 1.2, 0);
			w.spawnParticle(Particle.CLOUD, l.getX(), l.getY() + 0.4, l.getZ(), 20, 1.4, 0.3, 1.4, 0.05);
			BU.sound(l, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 2.4F, 0.85F);
			BU.sound(l, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.0F, 0.9F);
			if (this instanceof WyvernBoss wb) {
				wb.spawnFold = 0F;
				for (Part p : parts) if (p.display.isValid()) p.display.setInterpolationDuration(AssembleAnim.DIVE_SPREAD);
				poseParts(animTick);
				if (assembleRest != null) {
					for (int i = 0; i < parts.size() && i < assembleRest.length; i++) assembleRest[i] = parts.get(i).last;
				}
			}
		}
	}

	private void tickEmerge(AssembleAnim.Theme theme, Location l, World w, double bh) {
		if (assembleTick % 2 == 0) {
			w.spawnParticle(Particle.BLOCK, l.getX(), l.getY() + 0.15, l.getZ(), 14, 1.1, 0.15, 1.1, 0.08, org.bukkit.Material.AMETHYST_BLOCK.createBlockData());
			w.spawnParticle(Particle.BLOCK, l.getX(), l.getY() + 0.1, l.getZ(), 8, 0.9, 0.1, 0.9, 0.06, org.bukkit.Material.SMOOTH_BASALT.createBlockData());
			w.spawnParticle(Particle.DUST, l.getX(), l.getY() + 0.4, l.getZ(), 8, 0.8, 0.4, 0.8, 0, BU.dust(0xC080FF, 1.2F));
			w.spawnParticle(Particle.END_ROD, l.getX(), l.getY() + 0.3, l.getZ(), 3, 0.6, 0.4, 0.6, 0.01);
		}
		if (assembleTick % 8 == 2) {
			BU.sound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 1.1F, 0.7F + random.nextFloat() * 0.3F);
			BU.sound(l, Sound.BLOCK_GRAVEL_BREAK, SoundCategory.HOSTILE, 0.9F, 0.6F);
		}
		if (assembleRest != null) {
			for (int i = 0; i < parts.size() && i < assembleRest.length; i++) {
				if (!assembleLaunched[i] && assembleTick >= assembleStartAt[i]) launchPart(i, theme, AssembleAnim.EMERGE_RISE);
			}
		}
		tickShards();
	}

	private void tickCompress(AssembleAnim.Theme theme, Location l, World w, double bh) {
		if (assembleTick % 3 == 0) {
			w.spawnParticle(Particle.ENCHANT, l.getX(), l.getY() + bh * 0.45, l.getZ(), 12, 0.4, 0.5, 0.4, 0.4);
			w.spawnParticle(Particle.ELECTRIC_SPARK, l.getX(), l.getY() + bh * 0.45, l.getZ(), 6, 0.35, 0.35, 0.35, 0.02);
			w.spawnParticle(Particle.WAX_ON, l.getX(), l.getY() + bh * 0.45, l.getZ(), 4, 0.3, 0.3, 0.3, 0.01);
		}
		int inAt = AssembleAnim.HOLD;
		int spinAt = AssembleAnim.HOLD + AssembleAnim.COMP_IN;
		int outAt = spinAt + AssembleAnim.COMP_HOLD;
		if (assembleTick == inAt && assembleCompressed != null) {
			BU.sound(l, Sound.ITEM_ARMOR_EQUIP_IRON, SoundCategory.HOSTILE, 1.6F, 0.7F);
			BU.sound(l, Sound.BLOCK_COPPER_PLACE, SoundCategory.HOSTILE, 1.2F, 0.8F);
			for (int i = 0; i < parts.size() && i < assembleCompressed.length; i++) {
				Part p = parts.get(i);
				if (!p.display.isValid() || assembleCompressed[i] == null) continue;
				p.display.setInterpolationDuration(AssembleAnim.COMP_IN);
				p.display.setTransformation(assembleCompressed[i]);
				p.display.setInterpolationDelay(0);
				p.last = assembleCompressed[i];
			}
		}
		if (assembleTick == spinAt && assembleCompressed != null) {
			for (int i = 0; i < parts.size() && i < assembleCompressed.length; i++) {
				Part p = parts.get(i);
				if (!p.display.isValid() || assembleCompressed[i] == null) continue;
				Transformation c = assembleCompressed[i];
				Quaternionf spin = new Quaternionf().rotateY((float) Math.PI).mul(new Quaternionf(c.getLeftRotation()));
				Transformation spun = new Transformation(new Vector3f(c.getTranslation()), spin, new Vector3f(c.getScale()), new Quaternionf());
				p.display.setInterpolationDuration(AssembleAnim.COMP_HOLD);
				p.display.setTransformation(spun);
				p.display.setInterpolationDelay(0);
				p.last = spun;
			}
		}
		if (assembleTick == outAt && assembleRest != null) {
			BU.sound(l, Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1.8F, 0.85F);
			BU.sound(l, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, SoundCategory.HOSTILE, 1.4F, 0.9F);
			for (int i = 0; i < parts.size() && i < assembleRest.length; i++) {
				Part p = parts.get(i);
				if (!p.display.isValid() || assembleRest[i] == null) continue;
				p.display.setInterpolationDuration(AssembleAnim.COMP_OUT);
				p.display.setTransformation(assembleRest[i]);
				p.display.setInterpolationDelay(0);
				p.last = assembleRest[i];
			}
		}
	}

	private void tickShards() {
		if (shardStartAt == null) return;
		for (int i = 0; i < assembleShards.size() && i < shardStartAt.length; i++) {
			Display d = assembleShards.get(i);
			if (!d.isValid()) continue;
			if (!shardLaunched[i] && assembleTick >= shardStartAt[i]) {
				shardLaunched[i] = true;
				d.setInterpolationDuration(AssembleAnim.FLIGHT);
				d.setTransformation(shardArrive[i]);
				d.setInterpolationDelay(0);
			} else if (shardLaunched[i] && !shardShrinking[i] && assembleTick >= shardStartAt[i] + AssembleAnim.FLIGHT) {
				shardShrinking[i] = true;
				d.setInterpolationDuration(5);
				d.setTransformation(shardGone[i]);
				d.setInterpolationDelay(0);
				final Display fd = d;
				Fx.later(6, () -> {
					if (!fd.isValid()) return;
					if (fd.getVehicle() != null) fd.getVehicle().removePassenger(fd);
					Fx.forget(fd);
					fd.remove();
					Fx.LIVE--;
				});
			}
		}
	}

	private void initAssemble() {
		updateRiderOffset();
		for (Part p : parts) {
			if (p.display.isValid()) p.display.setInterpolationDuration(0);
		}
		if (this instanceof WyvernBoss wb && assembleStyle == AssembleAnim.Style.DIVE) {
			wb.spawnFold = 1F;
			wb.spawnPitch = 0F;
		}
		poseParts(0);
		int n = parts.size();
		assembleRest = new Transformation[n];
		assembleStartAt = new int[n];
		assembleLaunched = new boolean[n];
		java.util.Arrays.fill(roleLaunched, false);
		float height = BossModels.height(kind.id);
		if (height < 1F) height = (float) Math.max(3.0, mob.getHeight());
		AssembleAnim.Theme theme = AssembleAnim.theme(kind);
		for (int i = 0; i < n; i++) {
			Part p = parts.get(i);
			Transformation rest = p.last;
			if (rest == null) {
				rest = p.display.getTransformation();
				p.last = rest;
			}
			assembleRest[i] = rest;
		}
		switch (assembleStyle) {
			case ASSEMBLE -> initAssembleScatter(height, theme);
			case DIVE -> initDive(theme);
			case EMERGE -> initEmerge(height, theme);
			case COMPRESS -> initCompress(height, theme);
		}
	}

	private void initAssembleScatter(float height, AssembleAnim.Theme theme) {
		for (int i = 0; i < parts.size(); i++) {
			Part p = parts.get(i);
			Transformation rest = assembleRest[i];
			int role = AssembleAnim.role(p.id, rest.getTranslation().y + riderOff.y, height);
			if (p.display instanceof TextDisplay) role = 3;
			assembleStartAt[i] = AssembleAnim.startTick(role);
			Vector3f rt = rest.getTranslation();
			Quaternionf rr = new Quaternionf(rest.getLeftRotation());
			Vector3f rs = new Vector3f(rest.getScale());
			float dist = 5.0F + random.nextFloat() * 4.5F;
			float ang = (i * 2.399963F) + random.nextFloat() * 0.5F;
			float yOff;
			if (role == 0) yOff = -2.8F - random.nextFloat() * 2.2F;
			else if (role >= 3) yOff = 4.5F + random.nextFloat() * 3.5F;
			else yOff = (random.nextFloat() - 0.25F) * 6.0F;
			Vector3f startT = new Vector3f((float) Math.cos(ang) * dist, rt.y + yOff, (float) Math.sin(ang) * dist);
			Quaternionf startR = new Quaternionf().rotateXYZ(
				(random.nextFloat() - 0.5F) * 5.5F,
				(random.nextFloat() - 0.5F) * 5.5F,
				(random.nextFloat() - 0.5F) * 5.5F).mul(rr);
			Vector3f startS = new Vector3f(rs).mul(0.55F + random.nextFloat() * 0.25F);
			if (p.display instanceof TextDisplay) startS.mul(0.05F);
			Transformation scatter = new Transformation(startT, startR, startS, new Quaternionf());
			p.display.setInterpolationDuration(0);
			p.display.setTransformation(scatter);
			p.display.setInterpolationDelay(0);
			p.last = scatter;
			p.display.setGlowColorOverride(Color.fromRGB(theme.glowRgb()));
			p.display.setGlowing(!(p.display instanceof TextDisplay));
		}
		spawnAssembleShards(height);
	}

	private void initDive(AssembleAnim.Theme theme) {
		for (int i = 0; i < parts.size(); i++) {
			Part p = parts.get(i);
			Transformation rest = assembleRest[i];
			Vector3f startT = new Vector3f(rest.getTranslation()).add(0, 22.0F, 0);
			Quaternionf startR = new Quaternionf().rotateX((float) Math.toRadians(70)).mul(new Quaternionf(rest.getLeftRotation()));
			Transformation start = new Transformation(startT, startR, new Vector3f(rest.getScale()), new Quaternionf());
			p.display.setInterpolationDuration(0);
			p.display.setTransformation(start);
			p.display.setInterpolationDelay(0);
			p.last = start;
			p.display.setGlowColorOverride(Color.fromRGB(theme.glowRgb()));
			p.display.setGlowing(!(p.display instanceof TextDisplay));
		}
	}

	private void initEmerge(float height, AssembleAnim.Theme theme) {
		for (int i = 0; i < parts.size(); i++) {
			Part p = parts.get(i);
			Transformation rest = assembleRest[i];
			int role = AssembleAnim.role(p.id, rest.getTranslation().y + riderOff.y, height);
			if (p.display instanceof TextDisplay) role = 3;
			assembleStartAt[i] = 2 + role * 6;
			Vector3f rt = rest.getTranslation();
			float deep = role == 0 ? 3.4F : 6.2F;
			Vector3f startT = new Vector3f(rt.x, rt.y - deep, rt.z);
			Quaternionf startR = new Quaternionf().rotateX((float) Math.toRadians(28)).mul(new Quaternionf(rest.getLeftRotation()));
			Vector3f startS = new Vector3f(rest.getScale()).mul(1.0F, 0.82F, 1.0F);
			Transformation start = new Transformation(startT, startR, startS, new Quaternionf());
			p.display.setInterpolationDuration(0);
			p.display.setTransformation(start);
			p.display.setInterpolationDelay(0);
			p.last = start;
			p.display.setGlowColorOverride(Color.fromRGB(theme.glowRgb()));
			p.display.setGlowing(!(p.display instanceof TextDisplay));
		}
		// 바닥에서 같이 솟는 자수정 파편
		clearAssembleShards();
		int n = 8;
		shardStartAt = new int[n];
		shardArrive = new Transformation[n];
		shardGone = new Transformation[n];
		shardLaunched = new boolean[n];
		shardShrinking = new boolean[n];
		Location at = mob.getLocation();
		at.setYaw(0);
		at.setPitch(0);
		for (int i = 0; i < n; i++) {
			org.bukkit.Material mat = theme.blocks()[i % theme.blocks().length];
			float sz = 0.14F + random.nextFloat() * 0.16F;
			Display d = Fx.spawnBlockDisplay(at, mat.createBlockData(), true, Fx.FX_TAG, 0);
			d.setViewRange(2.0F);
			mob.addPassenger(d);
			assembleShards.add(d);
			Fx.LIVE++;
			float ang = i * 0.85F;
			float rad = 1.2F + random.nextFloat() * 1.8F;
			Vector3f startT = new Vector3f((float) Math.cos(ang) * rad, -2.8F - random.nextFloat(), (float) Math.sin(ang) * rad).sub(riderOff);
			Vector3f arriveT = new Vector3f((float) Math.cos(ang) * rad, 0.4F + random.nextFloat() * 1.6F, (float) Math.sin(ang) * rad).sub(riderOff);
			Quaternionf rot = new Quaternionf().rotateXYZ(random.nextFloat() * 4, random.nextFloat() * 4, random.nextFloat() * 4);
			Vector3f sc = new Vector3f(sz, sz, sz);
			shardArrive[i] = new Transformation(new Vector3f(arriveT), rot, sc, new Quaternionf());
			shardGone[i] = new Transformation(new Vector3f(arriveT), rot, new Vector3f(0.04F), new Quaternionf());
			shardStartAt[i] = 2 + i * 3;
			d.setInterpolationDuration(0);
			d.setTransformation(new Transformation(startT, rot, sc, new Quaternionf()));
			d.setInterpolationDelay(0);
			d.setGlowColorOverride(Color.fromRGB(theme.glowRgb()));
			d.setGlowing(true);
		}
	}

	private void initCompress(float height, AssembleAnim.Theme theme) {
		assembleCompressed = new Transformation[parts.size()];
		float mid = Math.max(1.2F, height * 0.42F);
		for (int i = 0; i < parts.size(); i++) {
			Part p = parts.get(i);
			Transformation rest = assembleRest[i];
			Vector3f rt = rest.getTranslation();
			float ang = i * 2.399963F;
			float dist = 2.8F + random.nextFloat() * 1.6F;
			Vector3f startT = new Vector3f((float) Math.cos(ang) * dist, mid + (random.nextFloat() - 0.5F) * 2.2F, (float) Math.sin(ang) * dist);
			Quaternionf startR = new Quaternionf().rotateXYZ(
				(random.nextFloat() - 0.5F) * 3.0F,
				(random.nextFloat() - 0.5F) * 3.0F,
				(random.nextFloat() - 0.5F) * 3.0F).mul(new Quaternionf(rest.getLeftRotation()));
			Vector3f startS = new Vector3f(rest.getScale()).mul(0.45F);
			Transformation start = new Transformation(startT, startR, startS, new Quaternionf());
			Vector3f midT = new Vector3f(0, mid, 0);
			// 살짝 어긋난 압축점 (완전히 한 점에 겹치면 깜빡임)
			midT.add((random.nextFloat() - 0.5F) * 0.25F, (random.nextFloat() - 0.5F) * 0.2F, (random.nextFloat() - 0.5F) * 0.25F);
			Quaternionf midR = new Quaternionf().rotateY(i * 0.4F).mul(new Quaternionf(rest.getLeftRotation()));
			Vector3f midS = new Vector3f(rest.getScale()).mul(0.16F);
			assembleCompressed[i] = new Transformation(midT, midR, midS, new Quaternionf());
			p.display.setInterpolationDuration(0);
			p.display.setTransformation(start);
			p.display.setInterpolationDelay(0);
			p.last = start;
			p.display.setGlowColorOverride(Color.fromRGB(theme.glowRgb()));
			p.display.setGlowing(!(p.display instanceof TextDisplay));
		}
	}

	private void launchPart(int i, AssembleAnim.Theme theme, int flight) {
		assembleLaunched[i] = true;
		Part p = parts.get(i);
		if (!p.display.isValid() || assembleRest[i] == null) return;
		p.display.setInterpolationDuration(flight);
		p.display.setTransformation(assembleRest[i]);
		p.display.setInterpolationDelay(0);
		p.last = assembleRest[i];
		int role = 0;
		for (int r = 0; r < 6; r++) {
			if (AssembleAnim.startTick(r) == assembleStartAt[i]) { role = r; break; }
		}
		if (!roleLaunched[role]) {
			roleLaunched[role] = true;
			Location at = mob.getLocation().add(0, Math.max(1.0, mob.getHeight() * 0.4), 0);
			Sound s = theme.launch()[role % theme.launch().length];
			BU.sound(at, s, SoundCategory.HOSTILE, 1.4F, 0.7F + role * 0.08F);
		}
		final Part fp = p;
		final int fi = i;
		Fx.later(flight, () -> {
			if (dead || !fp.display.isValid()) return;
			Location at = fp.display.getLocation();
			AssembleAnim.burst(at.getWorld(), at.getX(), at.getY() + 0.4, at.getZ(), kind, 6);
			BU.sound(at, theme.snap(), SoundCategory.HOSTILE, 1.1F, 0.85F + random.nextFloat() * 0.3F);
			if (hurtFlash == 0 && assembleRest != null && fi < assembleRest.length) {
				fp.display.setGlowColorOverride(Color.fromRGB(glowColor()));
				fp.display.setGlowing(fp.glowPart && glowParts());
			}
		});
	}

	private void spawnAssembleShards(float height) {
		clearAssembleShards();
		AssembleAnim.Theme theme = AssembleAnim.theme(kind);
		boolean smithFallback = parts.isEmpty() && kind == BossKind.ABYSSAL_SMITH;
		int nBlocks = smithFallback ? 16 : 8;
		int n = nBlocks + (smithFallback ? 1 : 0);
		shardStartAt = new int[n];
		shardArrive = new Transformation[n];
		shardGone = new Transformation[n];
		shardLaunched = new boolean[n];
		shardShrinking = new boolean[n];
		Location at = mob.getLocation();
		at.setYaw(0);
		at.setPitch(0);
		for (int i = 0; i < nBlocks; i++) {
			org.bukkit.Material mat = theme.blocks()[i % theme.blocks().length];
			float sz = 0.12F + random.nextFloat() * 0.16F;
			Display d = Fx.spawnBlockDisplay(at, mat.createBlockData(), true, Fx.FX_TAG, 0);
			d.setViewRange(2.0F);
			mob.addPassenger(d);
			assembleShards.add(d);
			Fx.LIVE++;
			float dist = 6.0F + random.nextFloat() * 5.0F;
			float ang = (i * 2.399963F) + 0.4F;
			float startY;
			if (i % 3 == 0) startY = -2.5F - random.nextFloat() * 2F;
			else if (i % 3 == 1) startY = height * 0.4F + (random.nextFloat() - 0.5F) * 5F;
			else startY = height + 3F + random.nextFloat() * 3F;
			Vector3f startT = new Vector3f((float) Math.cos(ang) * dist, startY, (float) Math.sin(ang) * dist).sub(riderOff);
			Quaternionf startR = new Quaternionf().rotateXYZ(random.nextFloat() * 6, random.nextFloat() * 6, random.nextFloat() * 6);
			Vector3f startS = new Vector3f(sz, sz, sz);
			float ay, ar;
			if (smithFallback) {
				// 위더 스켈레톤 실루엣에 가깝게 모이게
				int slot = i % 6;
				ay = switch (slot) {
					case 0, 1 -> 1.2F + random.nextFloat() * 1.4F;   // 다리
					case 2 -> 3.4F + random.nextFloat() * 1.8F;      // 몸
					case 3, 4 -> 4.0F + random.nextFloat() * 2.0F;   // 팔
					default -> 6.2F + random.nextFloat() * 1.0F;     // 머리
				};
				ar = slot <= 1 ? 0.55F : slot <= 2 ? 0.35F : slot <= 4 ? 1.1F : 0.2F;
			} else {
				ay = 0.6F + random.nextFloat() * Math.max(1.2F, height * 0.75F);
				ar = 0.3F + random.nextFloat() * 1.1F;
			}
			float aang = random.nextFloat() * 6.2832F;
			Vector3f arriveT = new Vector3f((float) Math.cos(aang) * ar, ay, (float) Math.sin(aang) * ar).sub(riderOff);
			Quaternionf arriveR = new Quaternionf().rotateXYZ(random.nextFloat() * 3, random.nextFloat() * 3, random.nextFloat() * 3);
			shardArrive[i] = new Transformation(new Vector3f(arriveT), arriveR, new Vector3f(startS), new Quaternionf());
			shardGone[i] = new Transformation(new Vector3f(arriveT), arriveR, new Vector3f(0.04F), new Quaternionf());
			shardStartAt[i] = 2 + i * 3;
			d.setInterpolationDuration(0);
			d.setTransformation(new Transformation(startT, startR, startS, new Quaternionf()));
			d.setInterpolationDelay(0);
			d.setGlowColorOverride(Color.fromRGB(theme.glowRgb()));
			d.setGlowing(true);
		}
		if (smithFallback) {
			int i = nBlocks;
			ItemStack hammer = com.hobbang.enhance.item.HItem.SMITH_HAMMER.create();
			Display hd = mob.getWorld().spawn(at, ItemDisplay.class, e -> {
				e.setPersistent(false);
				e.setItemStack(hammer);
				e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
				e.setInterpolationDuration(0);
				e.setBrightness(new Display.Brightness(15, 15));
				e.addScoreboardTag(Fx.FX_TAG);
				e.setViewRange(2.0F);
			});
			mob.addPassenger(hd);
			assembleShards.add(hd);
			Fx.LIVE++;
			Vector3f startT = new Vector3f(6.5F, 5.0F, -2.0F).sub(riderOff);
			Quaternionf startR = new Quaternionf().rotateXYZ(1.2F, 2.4F, 0.6F);
			Vector3f startS = new Vector3f(2.0F, 2.0F, 2.0F);
			Vector3f dest = new Vector3f(-1.6F, 3.4F, 0.6F).sub(riderOff);
			Quaternionf destR = new Quaternionf().rotateXYZ(0.4F, 0, 0.2F);
			shardArrive[i] = new Transformation(dest, destR, startS, new Quaternionf());
			shardGone[i] = new Transformation(dest, destR, new Vector3f(0.04F), new Quaternionf());
			shardStartAt[i] = AssembleAnim.startTick(4);
			hd.setInterpolationDuration(0);
			hd.setTransformation(new Transformation(startT, startR, startS, new Quaternionf()));
			hd.setInterpolationDelay(0);
			hd.setGlowColorOverride(Color.fromRGB(theme.glowRgb()));
			hd.setGlowing(true);
		}
	}

	private void clearAssembleShards() {
		for (Display d : assembleShards) {
			if (d.isValid()) {
				if (d.getVehicle() != null) d.getVehicle().removePassenger(d);
				Fx.forget(d);
				d.remove();
				Fx.LIVE--;
			}
		}
		assembleShards.clear();
		shardStartAt = null;
		shardArrive = null;
		shardGone = null;
		shardLaunched = null;
		shardShrinking = null;
	}

	private void finishAssemble(AssembleAnim.Theme theme) {
		Location l = mob.getLocation();
		World w = l.getWorld();
		double bh = Math.max(2.0, mob.getHeight());
		AssembleAnim.burst(w, l.getX(), l.getY() + bh * 0.5, l.getZ(), kind, 16);
		BU.sound(l, theme.finish(), SoundCategory.HOSTILE, 2.2F, 0.75F);
		if (kind == BossKind.TEMPEST_WYVERN) {
			BU.sound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.0F, 0.9F);
		} else if (kind == BossKind.ABYSSAL_SMITH) {
			BU.sound(l, Sound.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 2.0F, 0.6F);
		} else if (kind == BossKind.PRISM_COLOSSUS) {
			w.spawnParticle(Particle.END_ROD, l.getX(), l.getY() + bh * 0.6, l.getZ(), 24, 1.0, 1.2, 1.0, 0.08);
		} else if (kind == BossKind.CHRONO_SENTINEL) {
			BU.sound(l, Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 1.6F, 0.8F);
		}
		if (assembleRest != null) {
			for (int i = 0; i < parts.size() && i < assembleRest.length; i++) {
				Part p = parts.get(i);
				if (!p.display.isValid() || assembleRest[i] == null) continue;
				p.display.setInterpolationDuration(0);
				p.display.setTransformation(assembleRest[i]);
				p.display.setInterpolationDelay(0);
				p.last = assembleRest[i];
				p.display.setInterpolationDuration(ANIM_INTERVAL);
				if (hurtFlash == 0) {
					p.display.setGlowColorOverride(Color.fromRGB(glowColor()));
					p.display.setGlowing(p.glowPart && glowParts());
				}
			}
		}
		clearAssembleShards();
		assembleTick = -1;
		assembleInit = false;
		assembleRest = null;
		assembleStartAt = null;
		assembleLaunched = null;
		assembleCompressed = null;
		if (this instanceof WyvernBoss wb) { wb.spawnFold = 0; wb.spawnPitch = 0; }
		unfreezeAfterAssemble();
		invulTicks = Math.max(invulTicks, 10);
		onAssembleFinished();
		poseParts(animTick);
	}

	/** 조립이 끝났을 때 (대장장이 외형 복구 등) */
	protected void onAssembleFinished() {}

	private void cancelAssemble(boolean restorePose) {
		if (assembleTick < 0 && assembleShards.isEmpty()) return;
		clearAssembleShards();
		assembleTick = -1;
		assembleInit = false;
		assembleRest = null;
		assembleStartAt = null;
		assembleLaunched = null;
		assembleCompressed = null;
		if (this instanceof WyvernBoss wb) { wb.spawnFold = 0; wb.spawnPitch = 0; }
		unfreezeAfterAssemble();
		if (restorePose && partsBuilt) {
			for (Part p : parts) {
				if (p.display.isValid()) p.display.setInterpolationDuration(ANIM_INTERVAL);
			}
		}
	}


	protected void flash() {
		hurtFlash = 4;
		setFlash(true);
	}

	private void setFlash(boolean on) {
		for (Part p : parts) {
			if (p.display instanceof TextDisplay) continue;
			if (on) {
				p.display.setGlowColorOverride(Color.fromRGB(0xFF3030));
				p.display.setGlowing(true);
			} else {
				p.display.setGlowColorOverride(Color.fromRGB(glowColor()));
				p.display.setGlowing(p.glowPart && glowParts());
			}
		}
	}

	protected void refreshGlow() {
		for (Part p : parts) {
			if (p.glowPart && !(p.display instanceof TextDisplay)) {
				p.display.setGlowColorOverride(Color.fromRGB(glowColor()));
				p.display.setGlowing(glowParts());
			}
		}
	}

	private void aiStep() {
		World w = mob.getWorld();
		double hp = health() / maxHealth();
		float[] th = phaseThresholds();
		if (phase - 1 < th.length && hp <= th[phase - 1]) {
			setPhase(phase + 1);
			invulTicks = 50;
			bar.color(phaseColor(phase));
			if (phase == th.length + 1) bar.addFlag(BossBar.Flag.DARKEN_SCREEN);
			BU.sound(mob.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 3.0F, 0.6F + phase * 0.15F);
			Location l = mob.getLocation();
			w.spawnParticle(Particle.EXPLOSION_EMITTER, l.getX(), l.getY() + 1, l.getZ(), 1, 0, 0, 0, 0);
			onPhaseChange(phase);
		}
		double bw = mob.getWidth(), bh = mob.getHeight();
		if (invulTicks > 0) {
			invulTicks--;
			Location l = mob.getLocation();
			if (invulTicks % 4 == 0) w.spawnParticle(Particle.END_ROD, l.getX(), l.getY() + bh / 2, l.getZ(), 6, bw / 2, bh / 2, bw / 2, 0.02);
		}
		List<Player> near = nearbyPlayers(40);
		if (near.isEmpty() && !mob.getScoreboardTags().contains("hobbang_noregen")) {
			if (++regenTick >= 20) {
				regenTick = 0;
				heal(maxHealth() * 0.01);
			}
		}
		LivingEntity target = mob.getTarget();
		if (!isTargetable(target)) {
			target = null;
			Player best = null;
			double bd = 1e18;
			for (Player p : near) {
				if (!BU.valid(p)) continue;
				double d = p.getLocation().distanceSquared(mob.getLocation());
				if (d < bd) { bd = d; best = p; }
			}
			mob.setTarget(best);
			target = best;
		}
		bossTick(near, target);
	}

	// ───────── 피해 ─────────
	@Override
	public void onDamage(EntityDamageEvent e) {
		EntityDamageEvent.DamageCause c = e.getCause();
		if (c == EntityDamageEvent.DamageCause.VOID || c == EntityDamageEvent.DamageCause.WORLD_BORDER) return;
		if (c == EntityDamageEvent.DamageCause.KILL) {
			if (dying || dead) { e.setCancelled(true); return; }
			e.setCancelled(true);
			deathKiller = mob.getKiller();
			startDeath();
			return;
		}
		if (invulTicks > 0 || c == EntityDamageEvent.DamageCause.FALL || c == EntityDamageEvent.DamageCause.DROWNING
			|| c == EntityDamageEvent.DamageCause.LIGHTNING || c == EntityDamageEvent.DamageCause.SUFFOCATION || c == EntityDamageEvent.DamageCause.CRAMMING
			|| fromSelf(e, mob)) {
			e.setCancelled(true);
			return;
		}
		if (dying) { e.setCancelled(true); return; }
		double amount = e.getDamage();
		if (e instanceof EntityDamageByEntityEvent hit0) {
			org.bukkit.entity.Entity dm = hit0.getDamager();
			if (dm instanceof org.bukkit.entity.Player pl0) lastHitter = pl0;
			else if (dm instanceof org.bukkit.entity.Projectile pj0 && pj0.getShooter() instanceof org.bukkit.entity.Player pl0) lastHitter = pl0;
		}
		e.setDamage(Math.min(amount * damageMultiplier(), 40.0));
		if (health() - e.getFinalDamage() <= 0) {
			e.setCancelled(true);
			if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent ev) {
				org.bukkit.entity.Entity dmg = ev.getDamager();
				if (dmg instanceof org.bukkit.entity.Player pl) deathKiller = pl;
				else if (dmg instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof org.bukkit.entity.Player pl) deathKiller = pl;
			}
			if (deathKiller == null) deathKiller = lastHitter != null ? lastHitter : mob.getKiller();
			startDeath();
			return;
		}
		hurtFlash = 4;
		setFlash(true);
		onHurtBy(e, amount);
	}

	// ───────── v1.4.4 죽음 연출 ─────────
	protected int deathDuration() { return 80; }

	protected void startDeath() {
		if (dying || dead) return;
		dying = true;
		deathTick = 0;
		deathLen = Math.max(1, deathDuration());
		deathAt = mob.getLocation().clone();
		cancelAssemble(false);
		mob.setAI(false);
		mob.setTarget(null);
		mob.setInvulnerable(true);
		mob.setGravity(false);
		mob.setCollidable(false);
		mob.setVelocity(new org.bukkit.util.Vector());
		bar.progress(0);
		try { if (mob.getHealth() > 1.0) mob.setHealth(1.0); } catch (Exception ignored) {}
		invulTicks = deathLen + 20;
		for (Part p : parts) {
			if (p.display.isValid()) p.display.setInterpolationDuration(8);
		}
		updateRiderOffset();
		if (partsBuilt) poseParts(animTick);
		deathRest = new Transformation[parts.size()];
		for (int i = 0; i < parts.size(); i++) deathRest[i] = parts.get(i).last;
		onDeathStart();
	}

	protected void onDeathStart() {}

	protected void tickDeath() {
		deathTick++;
		float u = Math.min(1F, deathTick / (float) deathLen);
		// v1.5.0: 연출 중 예외가 나도 finishDeath 는 반드시 도달 (예전엔 예외가 틱을 끊어 정산·discard 가 미뤄졌다)
		try {
			if (deathTick == 1 || deathTick % ANIM_INTERVAL == 0) {
				updateRiderOffset();
				poseDeath(u);
			}
			deathFx(deathTick, u);
		} catch (Exception ex) {
			if (deathTick < 4) com.hobbang.enhance.HobbangPlugin.get().getLogger().warning("death anim error (" + kind.id + "): " + ex);
		}
		if (deathTick >= deathLen) finishDeath();
	}

	/** u = 0..1. 기본: 제자리 가라앉으며 축소. 각 보스가 덮어쓴다. */
	protected void poseDeath(float u) {
		if (deathRest == null) return;
		float sink = u * u * 1.8F;
		float sc = Math.max(0.02F, 1F - u * u);
		int n = Math.min(parts.size(), deathRest.length);
		for (int i = 0; i < n; i++) {
			Transformation last = deathRest[i];
			Part p = parts.get(i);
			if (last == null || !p.display.isValid()) continue;
			org.joml.Vector3f t = new org.joml.Vector3f(last.getTranslation()).add(0, -sink, 0);
			org.joml.Vector3f s = new org.joml.Vector3f(last.getScale()).mul(sc);
			Transformation tr = new Transformation(t, last.getLeftRotation(), s, last.getRightRotation());
			p.last = tr;
			p.display.setTransformation(tr);
			p.display.setInterpolationDelay(0);
		}
	}

	protected void deathFx(int tick, float u) {
		if (tick % 4 != 0) return;
		org.bukkit.Location l = mob.isValid() ? mob.getLocation() : deathAt;
		if (l == null || l.getWorld() == null) return;
		l.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, l.getX(), l.getY() + 0.6, l.getZ(), 4, 0.6, 0.5, 0.6, 0.01);
	}

	protected void finishDeath() {
		if (dead) return;
		dead = true;
		dying = false;
		org.bukkit.Location at = (deathAt != null ? deathAt : (mob.isValid() ? mob.getLocation() : null));
		if (at == null) at = new org.bukkit.Location(org.bukkit.Bukkit.getWorlds().get(0), 0, 64, 0);
		org.bukkit.entity.Player killer = deathKiller != null ? deathKiller : (lastHitter != null ? lastHitter : (mob.isValid() ? mob.getKiller() : null));
		final org.bukkit.Location fat = at;
		// v1.5.0: 단계마다 격리 — 보상/투기장 복구에서 예외가 나도 파츠 discard·히트박스 제거는 항상 실행
		safe("rewards", () -> settleRewards(fat, killer));
		safe("arena", () -> com.hobbang.enhance.arena.ArenaDress.restore(this));
		safe("fx", () -> Fx.discardOwned(uuid));
		safe("parts", this::discardParts);
		safe("bar", this::hideBar);
		safe("mob", () -> { if (mob.isValid() && !mob.isDead()) mob.remove(); });
	}

	private void safe(String what, Runnable r) {
		try { r.run(); }
		catch (Exception ex) { com.hobbang.enhance.HobbangPlugin.get().getLogger().warning("finishDeath " + what + " (" + kind.id + "): " + ex); }
	}

	@Override
	public void onDeath(EntityDeathEvent e) {
		// 연출 중에 히트박스가 먼저 죽으면(보이드 등) 연출을 건너뛰고 바로 정산
		if (dead) {
			e.getDrops().clear();
			e.setDroppedExp(0);
			return;
		}
		if (dying) {
			e.getDrops().clear();
			e.setDroppedExp(0);
			e.getEntity().getKiller();
			return;
		}
		cancelAssemble(false);
		super.onDeath(e);
		discardParts();
	}

	protected void scatterParts() {
		Random r = random;
		Location at = mob.getLocation();
		at.setYaw(0); at.setPitch(0);
		float h = (float) mob.getHeight();
		List<Display> ds = new ArrayList<>();
		for (Part p : parts) {
			Display d = p.display;
			if (!d.isValid()) continue;
			mob.removePassenger(d);
			d.setTeleportDuration(0);
			d.teleport(at);
			Vector3f dir = new Vector3f(r.nextFloat() - 0.5F, r.nextFloat() * 0.8F, r.nextFloat() - 0.5F).normalize().mul(2.5F + r.nextFloat() * 2);
			d.setInterpolationDuration(40);
			d.setTransformation(new Transformation(new Vector3f(dir).add(0, h * 0.5F, 0),
				new Quaternionf().rotateXYZ(r.nextFloat() * 6, r.nextFloat() * 6, r.nextFloat() * 6), new Vector3f(p.size).mul(0.15F), new Quaternionf()));
			d.setInterpolationDelay(0);
			ds.add(d);
		}
		parts.clear();
		partsBuilt = true;
		World w = at.getWorld();
		BU.sound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0F, 0.6F);
		double bw = mob.getWidth();
		for (int i = 4; i <= 40; i += 4) {
			Fx.later(i, () -> w.spawnParticle(Particle.EXPLOSION, at.getX(), at.getY() + h / 2, at.getZ(), 2, bw, h / 2, bw, 0));
		}
		Fx.later(41, () -> {
			for (Display d : ds) { Fx.forget(d); d.remove(); Fx.LIVE--; }
		});
	}
}
