package com.hobbang.enhance.command;

import com.hobbang.enhance.boss.BossKind;
import com.hobbang.enhance.boss.BossManager;
import com.hobbang.enhance.boss.Fx;
import com.hobbang.enhance.enhance.Enchants;
import com.hobbang.enhance.enhance.EnhanceConfig;
import com.hobbang.enhance.enhance.EnhanceLogic;
import com.hobbang.enhance.forge.ForgeManager;
import com.hobbang.enhance.item.HItem;
import com.hobbang.enhance.item.ItemUtil;
import com.hobbang.enhance.recipe.Recipes;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.Predicate;

/** /hobbang 명령어 (Fabric HobbangCommand 와 같은 구성) */
public final class HobbangCommand {
	private HobbangCommand() {}

	private static final Predicate<CommandSourceStack> OP = s -> s.getSender().hasPermission("hobbang.admin");

	private static Player player(CommandContext<CommandSourceStack> ctx) {
		if (ctx.getSource().getExecutor() instanceof Player p) return p;
		if (ctx.getSource().getSender() instanceof Player p) return p;
		ctx.getSource().getSender().sendMessage(Component.text("플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
		return null;
	}

	private static void ok(CommandContext<CommandSourceStack> ctx, Component c) {
		ctx.getSource().getSender().sendMessage(c);
	}

	public static LiteralCommandNode<CommandSourceStack> build() {
		return Commands.literal("hobbang")
			.executes(HobbangCommand::help)
			.then(Commands.literal("help").executes(HobbangCommand::help))
			.then(Commands.literal("rates").executes(HobbangCommand::rates))
			.then(Commands.literal("info").executes(ctx -> {
				Player p = player(ctx);
				if (p == null) return 0;
				EnhanceLogic.preview(p, p.getInventory().getItemInMainHand());
				return 1;
			}))
			.then(Commands.literal("guide").executes(ctx -> {
				Player p = player(ctx);
				if (p == null) return 0;
				EnhanceLogic.give(p, HItem.GUIDE.create());
				ok(ctx, Component.text("가이드북을 받았습니다! 우클릭으로 읽으세요.", NamedTextColor.GREEN));
				return 1;
			}))
			.then(Commands.literal("set").requires(OP)
				.then(Commands.argument("level", IntegerArgumentType.integer(0, 15)).executes(ctx -> {
					Player p = player(ctx);
					if (p == null) return 0;
					ItemStack s = p.getInventory().getItemInMainHand();
					if (!ItemUtil.canEnhance(s)) {
						ok(ctx, Component.text("강화 가능한 장비를 주 손에 드세요.", NamedTextColor.RED));
						return 0;
					}
					int lv = IntegerArgumentType.getInteger(ctx, "level");
					ItemUtil.setLevel(s, lv);
					p.getInventory().setItemInMainHand(s);
					ok(ctx, Component.text("강화 단계를 ").append(ItemUtil.levelTag(lv)).append(Component.text("로 설정했습니다.")));
					return 1;
				})))
			.then(Commands.literal("kit").requires(OP).executes(ctx -> {
				Player p = player(ctx);
				if (p == null) return 0;
				EnhanceLogic.give(p, HItem.ENHANCE_FORGE.create());
				EnhanceLogic.give(p, HItem.SUMMON_ALTAR.create());
				EnhanceLogic.give(p, HItem.ENHANCE_STONE.create(64));
				EnhanceLogic.give(p, HItem.RADIANT_STONE.create(32));
				EnhanceLogic.give(p, HItem.PROTECTION_SCROLL.create(8));
				EnhanceLogic.give(p, HItem.BLESSING_SCROLL.create(8));
				EnhanceLogic.give(p, HItem.LUCK_CHARM.create(8));
				EnhanceLogic.give(p, HItem.ABYSS_CORE.create());
				EnhanceLogic.give(p, HItem.GUIDE.create());
				p.giveExpLevels(100);
				ok(ctx, Component.text("테스트 키트 지급 완료 (+경험치 100레벨)", NamedTextColor.GREEN));
				return 1;
			}))
			.then(Commands.literal("kit2").requires(OP).executes(ctx -> {
				Player p = player(ctx);
				if (p == null) return 0;
				EnhanceLogic.give(p, HItem.PRISM_CORE.create());
				EnhanceLogic.give(p, HItem.STORM_CORE.create());
				EnhanceLogic.give(p, HItem.CHRONO_CORE.create());
				EnhanceLogic.give(p, HItem.PRISM_SCROLL.create(8));
				EnhanceLogic.give(p, HItem.IMMORTAL_SCROLL.create(8));
				EnhanceLogic.give(p, HItem.REWIND_HOURGLASS.create(8));
				EnhanceLogic.give(p, HItem.CRYSTAL_GREATSWORD.create());
				EnhanceLogic.give(p, HItem.STORMCALLER.create());
				EnhanceLogic.give(p, HItem.CHRONO_DAGGER.create());
				ok(ctx, Component.text("보스 키트 지급 완료 (핵 3종, 신규 주문서, 보스 무기 3종)", NamedTextColor.GREEN));
				return 1;
			}))
			.then(Commands.literal("give").requires(OP)
				.then(Commands.argument("item", StringArgumentType.word())
					.suggests((c, b) -> { for (HItem h : HItem.values()) if (h.id.startsWith(b.getRemainingLowerCase())) b.suggest(h.id); return b.buildFuture(); })
					.executes(ctx -> giveItem(ctx, 1))
					.then(Commands.argument("count", IntegerArgumentType.integer(1, 6400)).executes(ctx -> giveItem(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
			.then(Commands.literal("book").requires(OP)
				.then(Commands.argument("enchant", StringArgumentType.word())
					.suggests((c, b) -> { for (String id : Enchants.IDS) if (id.startsWith(b.getRemainingLowerCase())) b.suggest(id); return b.buildFuture(); })
					.executes(ctx -> giveBook(ctx, 0))
					.then(Commands.argument("level", IntegerArgumentType.integer(1, 10)).executes(ctx -> giveBook(ctx, IntegerArgumentType.getInteger(ctx, "level"))))))
			.then(Commands.literal("boss").requires(OP)
				.executes(ctx -> spawnBoss(ctx, BossKind.ABYSSAL_SMITH))
				.then(Commands.argument("name", StringArgumentType.word())
					.suggests((c, b) -> { for (BossKind k : BossKind.values()) if (k.id.startsWith(b.getRemainingLowerCase())) b.suggest(k.id); return b.buildFuture(); })
					.executes(ctx -> {
						BossKind k = BossKind.byId(StringArgumentType.getString(ctx, "name"));
						if (k == null) {
							ok(ctx, Component.text("보스 이름: abyssal_smith, prism_colossus, tempest_wyvern, chrono_sentinel", NamedTextColor.RED));
							return 0;
						}
						return spawnBoss(ctx, k);
					})))
			.then(Commands.literal("structure").requires(OP)
				.then(Commands.literal("place")
					.then(Commands.argument("which", StringArgumentType.word())
						.suggests((c, b) -> { for (String n : new String[]{"smith", "colossus", "sentinel"}) if (n.startsWith(b.getRemainingLowerCase())) b.suggest(n); return b.buildFuture(); })
						.executes(HobbangCommand::placeStructure)))
				.then(Commands.literal("crumble").executes(ctx -> {
					int n = com.hobbang.enhance.arena.StormArena.crumbleAll();
					ok(ctx, Component.text("[홉방] 폭풍 투기장 " + n + "곳을 무너뜨립니다 (원래 블록으로 복구)."));
					return n;
				})))
			.then(Commands.literal("storm").executes(ctx -> {
				com.hobbang.enhance.forge.Altar.stormHelp((Player) ctx.getSource().getSender());
				return 1;
			}))
			.then(Commands.literal("recipetest").requires(OP).executes(HobbangCommand::recipeTest))
			.then(Commands.literal("displays").requires(OP).executes(ctx -> {
				int n = BossManager.countDisplays();
				ok(ctx, Component.text("[홉방] 보스 파츠/이펙트 디스플레이 엔티티 수: " + n + " (활성 FX " + Fx.active() + ", 보스 " + BossManager.all().size() + ")"));
				return n;
			})
				.then(Commands.literal("clean").executes(ctx -> {
					int n = BossManager.cleanDisplays();
					ok(ctx, Component.text("[홉방] 주인 없는 디스플레이 " + n + "개를 지웠습니다."));
					return n;
				})))
			.then(Commands.literal("selftest").requires(OP).executes(HobbangCommand::status))
			.then(Commands.literal("reload").requires(OP).executes(ctx -> {
				String msg = EnhanceConfig.load();
				ok(ctx, Component.text("[홉방] " + msg));
				return 1;
			}))
			.build();
	}

	private static int giveItem(CommandContext<CommandSourceStack> ctx, int count) {
		Player p = player(ctx);
		if (p == null) return 0;
		HItem h = HItem.byId(StringArgumentType.getString(ctx, "item"));
		if (h == null) {
			ok(ctx, Component.text("알 수 없는 아이템입니다.", NamedTextColor.RED));
			return 0;
		}
		int left = count;
		while (left > 0) {
			int n = Math.min(left, h.maxStack);
			EnhanceLogic.give(p, h.create(n));
			left -= n;
		}
		ok(ctx, Component.text("[홉방] " + h.name + " x" + count + " 지급", NamedTextColor.GREEN));
		return 1;
	}

	/** 홉방 인첸트 책 지급 (level 0 = 최대 레벨) */
	private static int giveBook(CommandContext<CommandSourceStack> ctx, int level) {
		Player p = player(ctx);
		if (p == null) return 0;
		String id = StringArgumentType.getString(ctx, "enchant");
		org.bukkit.enchantments.Enchantment e = Enchants.get(id);
		if (e == null) {
			ok(ctx, Component.text("알 수 없는 인첸트입니다: " + String.join(", ", Enchants.IDS), NamedTextColor.RED));
			return 0;
		}
		int lv = level <= 0 ? e.getMaxLevel() : level;
		EnhanceLogic.give(p, Enchants.book(e, lv));
		ok(ctx, Component.text("[홉방] ").append(e.displayName(lv)).append(Component.text(" 책 지급", NamedTextColor.GREEN)));
		return 1;
	}

	private static int spawnBoss(CommandContext<CommandSourceStack> ctx, BossKind k) {
		Player p = player(ctx);
		if (p == null) return 0;
		Location l = p.getLocation().getBlock().getLocation();
		org.bukkit.block.BlockFace f = p.getFacing();
		l.add(f.getModX() * 8, 0, f.getModZ() * 8);
		BossManager.spawn(k, l);
		return 1;
	}

	private static int placeStructure(CommandContext<CommandSourceStack> ctx) {
		Player p = player(ctx);
		if (p == null) return 0;
		com.hobbang.enhance.arena.Site site = com.hobbang.enhance.arena.Site.byName(StringArgumentType.getString(ctx, "which"));
		if (site == null || site.structure == null) {
			ok(ctx, Component.text("구조물: smith(심연의 대장간) / colossus(프리즘 정동) / sentinel(크로노 신전)", NamedTextColor.RED));
			return 0;
		}
		long t0 = System.nanoTime();
		int ticks = com.hobbang.enhance.arena.StructurePlacer.place(site, p.getLocation(), altar ->
			p.sendMessage(Component.text("[홉방] " + site.title + " 설치 완료 — 제단 " + altar.getX() + ", " + altar.getY() + ", " + altar.getZ(), NamedTextColor.GREEN)));
		if (ticks < 0) { ok(ctx, Component.text("템플릿을 찾을 수 없습니다: hobbang:" + site.structure, NamedTextColor.RED)); return 0; }
		ok(ctx, Component.text("[홉방] " + site.title + " 설치 중… (" + ticks + "틱에 나눠 설치, 템플릿 로드 " + (System.nanoTime() - t0) / 1_000_000 + "ms)", NamedTextColor.YELLOW));
		return 1;
	}

	private static Component cmd(String c, String desc) {
		return Component.text("  " + c, NamedTextColor.AQUA).clickEvent(ClickEvent.suggestCommand(c))
			.append(Component.text(" - " + desc, NamedTextColor.GRAY));
	}

	private static int help(CommandContext<CommandSourceStack> ctx) {
		CommandSender s = ctx.getSource().getSender();
		s.sendMessage(Component.text("━━━━ 홉방 강화 ━━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
		s.sendMessage(Component.text("① 강화석 모으기: Y32 아래 돌·심층암 채굴, 몬스터 처치, 다이아 채굴", NamedTextColor.WHITE));
		s.sendMessage(Component.text("② 강화대 조합: 강화석3/모루/흑요석3 → 설치", NamedTextColor.WHITE));
		s.sendMessage(Component.text("③ 강화대 우클릭 = 강화 창 (장비 칸 + 보조 칸 + 모루 버튼), 장비 들고 좌클릭 = 확률 보기", NamedTextColor.WHITE));
		s.sendMessage(Component.text("④ +10부터 빛나는 강화석 필요 (강화석8+다이아)", NamedTextColor.WHITE));
		s.sendMessage(Component.text("⑤ 보스는 정해진 제단에서만 소환 (핵 들고 제단 우클릭 → 5초 의식):", NamedTextColor.DARK_PURPLE));
		s.sendMessage(Component.text("   심연의 핵 → 심연의 대장간(네더 현무암 삼각주) 가운데 용광로", NamedTextColor.LIGHT_PURPLE));
		s.sendMessage(Component.text("   프리즘 핵 → 프리즘 정동(오버월드 지하 무성한 동굴) 가운데 신호기", NamedTextColor.LIGHT_PURPLE));
		s.sendMessage(Component.text("   시간의 핵 → 크로노 신전(오버월드 평원·초원 등 지상) 가운데 마법부여대", NamedTextColor.LIGHT_PURPLE));
		s.sendMessage(Component.text("   폭풍 핵 → 어디서나 직접 쌓은 폭풍 제단, 천둥번개 칠 때 피뢰침 우클릭 (/hobbang storm)", NamedTextColor.LIGHT_PURPLE));
		s.sendMessage(Component.text("   소환대(스컬크 감지체) = 보스 안내판: 구조물 방향 찾기 · 제단 설계도", NamedTextColor.DARK_PURPLE));
		s.sendMessage(Component.text("⑥ 부적·주문서는 강화 창 보조 칸에: 프리즘(+20%p), 불멸(파괴 시 유지), 역행 모래시계(실패 무효)", NamedTextColor.AQUA));
		s.sendMessage(Component.text("⑦ 인첸트 테이블 비용 1/3 (최대 10레벨, 실제 소모 1~3레벨) · 모루 비용 1/3 (최대 10, '너무 비쌈' 없음)", NamedTextColor.GREEN));
		s.sendMessage(Component.text("⑨ +5/+10/+15 마일스톤: 흉갑 체력, 투구 산소, 각반 속도, 신발 낙하, 검 출혈·관통·장인의 일격, 도구 내구도, 활 화살 보존, 방패 반사 (장비 설명 ◆)", NamedTextColor.GOLD));
		s.sendMessage(Component.text("⑧ 희귀 인첸트(염동력·자동 제련·생명 수선·뇌격·폭렬·광역 채굴·벌목꾼·강화 친화)는 구조물 상자에서만", NamedTextColor.GREEN));
		s.sendMessage(cmd("/hobbang rates", "강화 확률표"));
		s.sendMessage(cmd("/hobbang info", "손에 든 장비 정보"));
		s.sendMessage(cmd("/hobbang guide", "가이드북 받기"));
		s.sendMessage(cmd("/hobbang storm", "폭풍 제단 설계도"));
		if (s.hasPermission("hobbang.admin")) {
			s.sendMessage(cmd("/hobbang kit", "(OP) 테스트 재료"));
			s.sendMessage(cmd("/hobbang set 10", "(OP) 강화 단계 설정"));
			s.sendMessage(cmd("/hobbang kit2", "(OP) 신규 보스 핵/주문서/무기"));
			s.sendMessage(cmd("/hobbang give enhance_stone 64", "(OP) 홉방 아이템 지급"));
			s.sendMessage(cmd("/hobbang book superior_sharpness 3", "(OP) 홉방 인첸트 책 지급"));
			s.sendMessage(cmd("/hobbang boss prism_colossus", "(OP) 보스 즉시 소환 (4종, 제단 없이 테스트용)"));
			s.sendMessage(cmd("/hobbang structure place smith", "(OP) 구조물을 내 위치에 설치 (smith/colossus/sentinel)"));
			s.sendMessage(cmd("/hobbang structure crumble", "(OP) 폭풍 투기장 강제 정리"));
			s.sendMessage(cmd("/hobbang displays", "(OP) 디스플레이 엔티티 수 확인 · clean = 고아 청소"));
			s.sendMessage(cmd("/hobbang recipetest", "(OP) 레시피 점검"));
			s.sendMessage(cmd("/hobbang selftest", "(OP) 플러그인 상태 점검"));
			s.sendMessage(cmd("/hobbang reload", "(OP) 설정 다시 읽기"));
		}
		return 1;
	}

	private static int rates(CommandContext<CommandSourceStack> ctx) {
		CommandSender s = ctx.getSource().getSender();
		s.sendMessage(Component.text("━━ 강화 확률표 (성공 / 실패시 하락 / 실패시 파괴 / 재료) ━━", NamedTextColor.GOLD));
		EnhanceConfig.Stage[] st = EnhanceConfig.CFG.stages;
		for (int i = 0; i < st.length; i++) {
			boolean radiant = i >= EnhanceConfig.CFG.radiantFrom;
			HItem stone = radiant ? HItem.RADIANT_STONE : HItem.ENHANCE_STONE;
			s.sendMessage(Component.empty().append(ItemUtil.levelTag(i + 1))
				.append(Component.text(String.format("%3.0f%% / %2.0f%% / %2.0f%% / ", st[i].success, st[i].downgrade, st[i].destroy), NamedTextColor.WHITE))
				.append(Component.text(stone.name, radiant ? NamedTextColor.AQUA : NamedTextColor.GREEN))
				.append(Component.text(" x" + st[i].stones + ", " + st[i].xpLevels + "Lv", NamedTextColor.GRAY)));
		}
		return 1;
	}

	private static String attrs(ItemStack s) {
		StringBuilder b = new StringBuilder();
		var m = s.getData(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS);
		if (m != null) for (var e : m.modifiers())
			b.append(e.attribute().getKey().getKey()).append('=').append(e.modifier().getAmount()).append(' ');
		return b.toString();
	}

	private static String show(ItemStack s) {
		if (s == null || s.getType().isAir()) return "(없음)";
		HItem h = HItem.of(s);
		return (h != null ? "hobbang:" + h.id : s.getType().getKey().toString()) + " x" + s.getAmount();
	}

	/** 실제 서버 레시피 시스템으로 조합 결과 확인 */
	private static int recipeTest(CommandContext<CommandSourceStack> ctx) {
		CommandSender s = ctx.getSource().getSender();
		org.bukkit.World w = ctx.getSource().getLocation().getWorld();
		ItemStack D = new ItemStack(Material.DIAMOND), R = HItem.RADIANT_STONE.create(), N = new ItemStack(Material.NETHERITE_INGOT);
		ItemStack tpl = Bukkit.craftItemResult(new ItemStack[]{D, R, D, R, N, R, D, R, D}, w).getResult();
		s.sendMessage(Component.text("[recipetest] template craft -> " + show(tpl)));
		ItemStack T = HItem.RELIC_TEMPLATE.create(), O = new ItemStack(Material.CRYING_OBSIDIAN);
		ItemStack dup = Bukkit.craftItemResult(new ItemStack[]{D, T, D, D, O, D, D, D, D}, w).getResult();
		s.sendMessage(Component.text("[recipetest] template duplicate -> " + show(dup)));
		ItemStack ES = HItem.ENHANCE_STONE.create();
		ItemStack forge = Bukkit.craftItemResult(new ItemStack[]{ES, ES, ES, null, new ItemStack(Material.ANVIL), null,
			new ItemStack(Material.OBSIDIAN), new ItemStack(Material.OBSIDIAN), new ItemStack(Material.OBSIDIAN)}, w).getResult();
		s.sendMessage(Component.text("[recipetest] enhance forge -> " + show(forge)));
		ItemStack guide = Bukkit.craftItemResult(new ItemStack[]{new ItemStack(Material.BOOK), ES, null, null, null, null, null, null, null}, w).getResult();
		s.sendMessage(Component.text("[recipetest] guide -> " + show(guide)));
		ItemStack fake = new ItemStack(Material.KNOWLEDGE_BOOK);
		ItemStack fakeForge = Bukkit.craftItemResult(new ItemStack[]{fake, fake, fake, null, new ItemStack(Material.ANVIL), null,
			new ItemStack(Material.OBSIDIAN), new ItemStack(Material.OBSIDIAN), new ItemStack(Material.OBSIDIAN)}, w).getResult();
		s.sendMessage(Component.text("[recipetest] plain knowledge_book forge (must be empty) -> " + show(fakeForge)));
		int smithing = 0;
		for (NamespacedKey k : Recipes.KEYS) if (Bukkit.getRecipe(k) instanceof org.bukkit.inventory.SmithingTransformRecipe) smithing++;
		s.sendMessage(Component.text("[recipetest] registered=" + Recipes.KEYS.size() + " smithing=" + smithing));
		Object[][] cases = {{Material.NETHERITE_AXE, HItem.SMITH_HAMMER}, {Material.NETHERITE_SWORD, HItem.CRYSTAL_GREATSWORD},
			{Material.NETHERITE_AXE, HItem.STORMCALLER}, {Material.NETHERITE_SWORD, HItem.CHRONO_DAGGER}};
		for (Object[] c : cases) {
			ItemStack base = new ItemStack((Material) c[0]);
			ItemUtil.setLevel(base, 12);
			ItemStack out = Recipes.transform(base, (HItem) c[1]);
			s.sendMessage(Component.text("[recipetest] smithing " + c[0] + " -> " + show(out) + " lv=" + ItemUtil.getLevel(out) + " attrs: " + attrs(out)));
		}
		return 1;
	}

	/** 플러그인 상태 점검 (인첸트/레시피/보스/디스플레이) */
	private static int status(CommandContext<CommandSourceStack> ctx) {
		CommandSender s = ctx.getSource().getSender();
		s.sendMessage(Component.text("[selftest] enchantments=" + Enchants.loaded + "/" + Enchants.TOTAL + " smelt=" + com.hobbang.enhance.world.EnchantListener.smeltCount() + " recipes=" + Recipes.KEYS.size()
			+ " bosses=" + BossManager.all().size() + " fx=" + Fx.active() + " displays=" + BossManager.countDisplays()
			+ " forges(loaded)=" + ForgeManager.loadedCount() + " forgeGuis=" + com.hobbang.enhance.forge.ForgeGui.openCount(), Enchants.loaded == Enchants.TOTAL ? NamedTextColor.GREEN : NamedTextColor.RED));
		int adv = 0;
		for (String id : new String[]{"root", "forge", "first", "ten", "fifteen", "core", "slay", "slay_prism", "slay_tempest", "slay_chrono", "master"})
			if (Bukkit.getAdvancement(new NamespacedKey("hobbang", id)) != null) adv++;
		s.sendMessage(Component.text("[selftest] advancements=" + adv + "/11", adv == 11 ? NamedTextColor.GREEN : NamedTextColor.RED));
		if (player(ctx) instanceof Player p) {
			ItemStack h = p.getInventory().getItemInMainHand();
			ItemMeta m = h.getItemMeta();
			s.sendMessage(Component.text("[selftest] hand=" + show(h) + " lv=" + ItemUtil.getLevel(h) + " canEnhance=" + ItemUtil.canEnhance(h)
				+ (m != null ? " enchants=" + h.getEnchantments() : "") + " attrs: " + attrs(h)));
		}
		return Command.SINGLE_SUCCESS;
	}
}
