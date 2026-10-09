package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.enchanting.RuneEnchanting;
import de.ipnats.hardwrought.enchanting.RuneWork;
import de.ipnats.hardwrought.smithing.Mask;
import de.ipnats.hardwrought.smithing.ToolParts;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

/** Runes written on a piece: what a rune is, how much of it has to lie on the piece, and what it leaves there. */
public final class EnchantingGameTests {
    private static final Identifier EFFICIENCY = Enchantments.EFFICIENCY.identifier();
    private static final Identifier UNBREAKING = Enchantments.UNBREAKING.identifier();
    private static final Identifier FORTUNE = Enchantments.FORTUNE.identifier();
    private static final Identifier SILK_TOUCH = Enchantments.SILK_TOUCH.identifier();

    /** A piece that is piece everywhere, and one that is piece only in its left half. */
    private static Mask whole(int width) {
        Mask mask = Mask.empty();
        for (int y = 0; y < Mask.SIZE; y++) for (int x = 0; x < width; x++) mask = mask.with(x, y, true);
        return mask;
    }

    private static Holder<Enchantment> rune(ServerLevel level, Identifier id) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, id));
    }

    @GameTest
    public void aRuneIsOneFigureAndAlwaysTheSame(GameTestHelper helper) {
        for (Identifier id : List.of(EFFICIENCY, UNBREAKING, FORTUNE, SILK_TOUCH)) {
            for (int tier = 0; tier <= 3; tier++) {
                RuneWork.Shape shape = RuneWork.shape(id, tier);
                helper.assertTrue(shape.size() == (tier >= 2 ? 9 : 7) && shape.count() >= shape.size(),
                        "A rune has a spine at least: " + id + " has " + shape.count() + " squares");
                helper.assertTrue(java.util.Arrays.equals(shape.cells(), RuneWork.shape(id, tier).cells()),
                        "and is the same every time it is drawn");
                helper.assertTrue(java.util.Arrays.equals(shape.cells(), shape.turned(4).cells())
                                && shape.turned(1).count() == shape.count(),
                        "Turned four times it is itself again, and turning loses nothing");
            }
        }
        // Adamant takes the finest strokes: on mithril a rune is a square larger, on anything below two.
        helper.assertTrue(RuneWork.shape(EFFICIENCY, 0, 0).size() == 7 && RuneWork.shape(EFFICIENCY, 0, 1).size() == 8
                        && RuneWork.shape(EFFICIENCY, 0, 2).size() == 9 && RuneWork.shape(SILK_TOUCH, 3, 2).size() == 11
                        && RuneWork.shape(EFFICIENCY, 0, 2).count() > RuneWork.shape(EFFICIENCY, 0, 0).count(),
                "A rune is larger the coarser the material");
        helper.assertTrue(RuneEnchanting.coarser(new ItemStack(Items.IRON_PICKAXE)) == 2
                        && RuneEnchanting.coarser(new ItemStack(Items.NETHERITE_PICKAXE)) == 2
                        && RuneEnchanting.coarser(new ItemStack(de.ipnats.hardwrought.smithing.UpgradeEquipment.tool(
                                de.ipnats.hardwrought.smithing.UpgradeEquipment.Tier.MITHRIL, de.ipnats.hardwrought.smithing.UpgradeEquipment.Tool.PICKAXE))) == 1
                        && RuneEnchanting.coarser(new ItemStack(de.ipnats.hardwrought.smithing.UpgradeEquipment.tool(
                                de.ipnats.hardwrought.smithing.UpgradeEquipment.Tier.ADAMANT, de.ipnats.hardwrought.smithing.UpgradeEquipment.Tool.PICKAXE))) == 0,
                "Iron and netherite write coarsest, mithril finer, adamant finest");
        helper.assertFalse(java.util.Arrays.equals(RuneWork.shape(EFFICIENCY, 0).cells(), RuneWork.shape(UNBREAKING, 0).cells()),
                "Two enchantments have two runes");
        helper.succeed();
    }

    @GameTest
    public void theMoreOfARuneLiesOnThePieceTheHigherItsLevel(GameTestHelper helper) {
        helper.assertTrue(RuneWork.level(10, 10, 5, true) == 5, "Whole on a part: the highest level");
        helper.assertTrue(RuneWork.level(9, 10, 5, true) == 5, "A stroke over the edge is forgiven");
        helper.assertTrue(RuneWork.level(5, 10, 5, true) == 2, "Half of it gives about half: " + RuneWork.level(5, 10, 5, true));
        helper.assertTrue(RuneWork.level(1, 10, 5, true) == 0, "A tenth of it does nothing");
        helper.assertTrue(RuneWork.level(10, 10, 5, false) == 4, "Whole on a finished tool: one level short");
        helper.assertTrue(RuneWork.level(10, 10, 1, false) == 1 && RuneWork.level(8, 10, 1, true) == 0,
                "An enchantment of one level takes on a tool too, but only whole");
        // Every ink can be made: the crusher grinds lapis lazuli, and mithril and adamant from the bar or the raw ore.
        helper.assertTrue(de.ipnats.hardwrought.metallurgy.Crushing.result(new ItemStack(Items.LAPIS_LAZULI)) == RuneEnchanting.powder(RuneWork.Ink.LAPIS)
                        && de.ipnats.hardwrought.metallurgy.Crushing.result(new ItemStack(de.ipnats.hardwrought.metallurgy.ModMetals.ingot(
                                de.ipnats.hardwrought.metallurgy.Metal.MITHRIL))) == RuneEnchanting.powder(RuneWork.Ink.MITHRIL)
                        && de.ipnats.hardwrought.metallurgy.Crushing.result(new ItemStack(de.ipnats.hardwrought.metallurgy.ModMetals.raw(
                                de.ipnats.hardwrought.metallurgy.Metal.ADAMANTIUM))) == RuneEnchanting.powder(RuneWork.Ink.ADAMANT),
                "Lapis, mithril and adamant are all ground to their powder in the crusher");
        helper.assertTrue(RuneWork.powder(1) == 1 && RuneWork.powder(6) == 1 && RuneWork.powder(7) == 2 && RuneWork.powder(0) == 0,
                "Powder is used by the stroke");
        helper.assertTrue(RuneWork.capacity(22) == 6 && RuneWork.capacity(14) == 4 && RuneWork.capacity(10) == 4 && RuneWork.capacity(5) == 3,
                "Gold takes more runes than iron, iron more than stone");
        helper.succeed();
    }

    @GameTest
    public void whatIsWrittenIsJudged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Identifier> ready = List.of(EFFICIENCY, UNBREAKING, FORTUNE, SILK_TOUCH);
        Mask piece = whole(Mask.SIZE);
        var efficiency = new RuneWork.Placement(EFFICIENCY, 2, 2, 0);
        var unbreaking = new RuneWork.Placement(UNBREAKING, 16, 16, 1);

        var both = RuneEnchanting.judge(level, piece, List.of(efficiency, unbreaking), ready, 3, true);
        helper.assertTrue(both.allowed() && both.levels().get(rune(level, EFFICIENCY)) == 5
                        && both.levels().get(rune(level, UNBREAKING)) == 3 && both.powder(RuneWork.Ink.LAPIS) > 0
                        && both.powder(RuneWork.Ink.ADAMANT) == 0 && both.experience() >= 2,
                "Two runes apart on a part, whole: each its highest level, for powder and experience: " + both);
        helper.assertTrue(RuneEnchanting.judge(level, piece, List.of(efficiency), ready, 3, false)
                        .levels().get(rune(level, EFFICIENCY)) == 4, "On the finished tool one level less");

        // Every rune has a spine through its middle, so two laid on the same spot lie over each other.
        helper.assertTrue("overlapping".equals(RuneEnchanting.judge(level, piece,
                        List.of(efficiency, new RuneWork.Placement(UNBREAKING, 2, 2, 0)), ready, 3, true).refusal()),
                "Runes may not lie over each other");
        helper.assertTrue(RuneEnchanting.judge(level, piece,
                        List.of(efficiency, new RuneWork.Placement(UNBREAKING, 9, 2, 0)), ready, 3, true).allowed(),
                "but side by side, touching, they may");
        helper.assertTrue("clash".equals(RuneEnchanting.judge(level, piece,
                        List.of(efficiency, new RuneWork.Placement(EFFICIENCY, 20, 20, 0)), ready, 3, true).refusal()),
                "nor be written twice");
        // Vanilla's exclusions are not kept: fortune lies beside silk touch, and protection beside fire protection.
        helper.assertTrue(RuneEnchanting.judge(level, piece,
                        List.of(new RuneWork.Placement(FORTUNE, 2, 2, 0), new RuneWork.Placement(SILK_TOUCH, 20, 20, 0)), ready, 3, true).allowed(),
                "Runes that vanilla would not let lie together do");
        var protections = List.of(Enchantments.PROTECTION.identifier(), Enchantments.FIRE_PROTECTION.identifier(),
                Enchantments.BLAST_PROTECTION.identifier(), Enchantments.PROJECTILE_PROTECTION.identifier());
        var armoured = RuneEnchanting.judge(level, piece, List.of(new RuneWork.Placement(protections.get(0), 1, 1, 0),
                new RuneWork.Placement(protections.get(1), 12, 1, 0), new RuneWork.Placement(protections.get(2), 23, 1, 0),
                new RuneWork.Placement(protections.get(3), 34, 1, 0)), protections, 4, true);
        helper.assertTrue(armoured.allowed() && armoured.levels().size() == 4 && armoured.levels().values().stream().allMatch(given -> given == 4),
                "so one piece of armor carries all four protections at their highest: " + armoured);
        // The better a piece is made, the more runes it holds.
        helper.assertTrue(RuneWork.forCraftsmanship(0.7f) == 0 && RuneWork.forCraftsmanship(0.75f) == 1
                        && RuneWork.forCraftsmanship(1.0f) == 2 && RuneWork.forCraftsmanship(1.25f) == 3 && RuneWork.forCraftsmanship(1.4f) == 3,
                "A well made piece holds a rune more, a perfect one two, one ground beyond that three");
        // Written in mithril or adamant a rune is overcharged, and it is that powder which is used up.
        var charged = RuneEnchanting.judge(level, piece, List.of(new RuneWork.Placement(EFFICIENCY, 2, 2, 0, RuneWork.Ink.MITHRIL),
                new RuneWork.Placement(UNBREAKING, 16, 16, 0, RuneWork.Ink.ADAMANT),
                new RuneWork.Placement(SILK_TOUCH, 30, 30, 0, RuneWork.Ink.ADAMANT)), ready, 3, true);
        helper.assertTrue(charged.allowed() && charged.levels().get(rune(level, EFFICIENCY)) == 6
                        && charged.levels().get(rune(level, UNBREAKING)) == 5 && charged.levels().get(rune(level, SILK_TOUCH)) == 1,
                "Mithril gives one level more and adamant two; what has only one level stays at one: " + charged.levels());
        helper.assertTrue(charged.powder(RuneWork.Ink.MITHRIL) > 0 && charged.powder(RuneWork.Ink.ADAMANT) > 0
                        && charged.powder(RuneWork.Ink.LAPIS) == 0, "and each rune uses the powder it was written in");
        // The finest ink on the piece also lets it hold more runes: mithril one more, adamant three.
        helper.assertTrue(RuneEnchanting.judge(level, piece, List.of(new RuneWork.Placement(EFFICIENCY, 2, 2, 0, RuneWork.Ink.MITHRIL),
                        unbreaking), ready, 1, true).allowed(), "With mithril on it a piece holds one rune more");
        helper.assertTrue(RuneEnchanting.judge(level, piece, List.of(efficiency, unbreaking,
                        new RuneWork.Placement(FORTUNE, 30, 2, 0), new RuneWork.Placement(Enchantments.MENDING.identifier(), 30, 30, 0, RuneWork.Ink.ADAMANT)),
                        List.of(EFFICIENCY, UNBREAKING, FORTUNE, Enchantments.MENDING.identifier()), 1, true).allowed(),
                "and with adamant on it three more");
        helper.assertTrue("too_many".equals(RuneEnchanting.judge(level, piece, List.of(efficiency, unbreaking), ready, 1, true).refusal()),
                "nor more of them than the material takes");
        helper.assertTrue("not_ready".equals(RuneEnchanting.judge(level, piece, List.of(efficiency), List.of(UNBREAKING), 3, true).refusal()),
                "nor one that did not lie ready");
        // Half on the piece: a lower level. All but off it: nothing.
        Mask half = whole(4);
        var hanging = RuneEnchanting.judge(level, half, List.of(new RuneWork.Placement(EFFICIENCY, 9, 2, 0)), ready, 3, true);
        helper.assertTrue(hanging.allowed() && hanging.levels().get(rune(level, EFFICIENCY)) < 5,
                "A rune hanging over the edge gives less: " + hanging);
        helper.assertTrue("too_little".equals(RuneEnchanting.judge(level, half,
                        List.of(new RuneWork.Placement(EFFICIENCY, 12, 2, 0)), ready, 3, true).refusal()),
                "and one that misses the piece gives nothing");
        helper.succeed();
    }

    /** A piece that already carries runes takes more beside them, and remembers where they all lie. */
    @GameTest
    public void aPieceThatCarriesRunesTakesMoreBesideThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Identifier> ready = List.of(EFFICIENCY, UNBREAKING, FORTUNE);
        Mask piece = whole(Mask.SIZE);
        var old = new RuneWork.Placement(EFFICIENCY, 2, 2, 0);
        java.util.Set<net.minecraft.core.Holder<Enchantment>> present = java.util.Set.of(rune(level, EFFICIENCY));

        var beside = RuneEnchanting.judge(level, piece, List.of(new RuneWork.Placement(UNBREAKING, 16, 16, 0)), ready, 2, true,
                List.of(old), present);
        helper.assertTrue(beside.allowed() && beside.levels().size() == 1 && beside.levels().get(rune(level, UNBREAKING)) == 3,
                "A second rune is written beside the first, and only the new one is paid for: " + beside);
        helper.assertTrue("overlapping".equals(RuneEnchanting.judge(level, piece,
                        List.of(new RuneWork.Placement(UNBREAKING, 2, 2, 0)), ready, 2, true, List.of(old), present).refusal()),
                "The old rune keeps its room");
        helper.assertTrue("clash".equals(RuneEnchanting.judge(level, piece,
                        List.of(new RuneWork.Placement(EFFICIENCY, 20, 20, 0)), ready, 2, true, List.of(old), present).refusal()),
                "and is not written a second time while it is there");
        helper.assertTrue("too_many".equals(RuneEnchanting.judge(level, piece, List.of(new RuneWork.Placement(UNBREAKING, 16, 16, 0),
                        new RuneWork.Placement(FORTUNE, 30, 30, 0)), ready, 2, true, List.of(old), present).refusal()),
                "It counts towards what the piece takes");
        // Wiped off, its room is free and it can be written again, in a finer ink.
        var again = RuneEnchanting.judge(level, piece, List.of(new RuneWork.Placement(EFFICIENCY, 2, 2, 0, RuneWork.Ink.ADAMANT)),
                ready, 2, true, List.of(), java.util.Set.of());
        helper.assertTrue(again.allowed() && again.levels().get(rune(level, EFFICIENCY)) == 7,
                "Wiped off, it is written again, overcharged: " + again.levels());

        ItemStack head = new ItemStack(ToolParts.part(ToolParts.SmithMetal.IRON, ToolParts.Part.PICKAXE_HEAD));
        head.set(de.ipnats.hardwrought.core.registry.ModDataComponents.RUNES, new RuneWork.Marks(List.of(old)));
        var saved = ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), head)
                .getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), saved)
                .getOrThrow();
        helper.assertTrue(RuneEnchanting.carried(loaded).equals(List.of(old)) && RuneEnchanting.carried(new ItemStack(Items.STICK)).isEmpty(),
                "A piece remembers its runes through being saved: " + RuneEnchanting.carried(loaded));
        helper.succeed();
    }

    @GameTest
    public void aPartTakesTheRunesOfItsToolAndHandsThemOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack head = new ItemStack(ToolParts.part(ToolParts.SmithMetal.IRON, ToolParts.Part.PICKAXE_HEAD));
        ItemStack golden = new ItemStack(ToolParts.part(ToolParts.SmithMetal.GOLD, ToolParts.Part.PICKAXE_HEAD));
        helper.assertTrue(RuneEnchanting.isPart(head) && !RuneEnchanting.isPart(new ItemStack(Items.IRON_PICKAXE)),
                "A pickaxe head is a part, a pickaxe is not");
        helper.assertTrue(RuneEnchanting.enchantability(golden) > RuneEnchanting.enchantability(head)
                        && RuneEnchanting.enchantability(new ItemStack(Items.IRON_PICKAXE)) > 0
                        && RuneEnchanting.enchantability(new ItemStack(Items.STICK)) == 0,
                "Gold takes enchantment more readily than iron, and a stick not at all");
        var suited = RuneEnchanting.suited(level, head);
        helper.assertTrue(suited.contains(rune(level, EFFICIENCY)) && suited.contains(rune(level, UNBREAKING))
                        && !suited.contains(rune(level, Enchantments.SHARPNESS.identifier())),
                "A pickaxe head takes a pickaxe's runes and no sword's: " + suited.size());
        helper.assertTrue(RuneEnchanting.ready(rune(level, EFFICIENCY), 0, 1)
                        && !RuneEnchanting.ready(rune(level, SILK_TOUCH), 0, 30)
                        && !RuneEnchanting.ready(rune(level, SILK_TOUCH), 15, 5)
                        && RuneEnchanting.ready(rune(level, SILK_TOUCH), 15, 30),
                "A common rune lies ready at a bare table; a rare one wants its shelves and its levels both");
        // With no librarian to sell it, mending has a rune of its own: of the rarest sort, and no curse has one.
        var mending = rune(level, Enchantments.MENDING.identifier());
        helper.assertTrue(suited.contains(mending) && RuneWork.tier(mending) == 3
                        && !suited.contains(rune(level, Enchantments.VANISHING_CURSE.identifier())),
                "Mending can be written, as the rarest kind of rune; a curse cannot");

        head.enchant(rune(level, EFFICIENCY), 5);
        CraftingRecipe recipe = (CraftingRecipe) level.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.withDefaultNamespace("iron_pickaxe"))).orElseThrow().value();
        ItemStack pickaxe = recipe.assemble(CraftingInput.of(1, 3, List.of(head, new ItemStack(Items.STICK), new ItemStack(Items.STICK))));
        helper.assertTrue(pickaxe.is(Items.IRON_PICKAXE) && pickaxe.getEnchantments().getLevel(rune(level, EFFICIENCY)) == 5,
                "The runes on the head are on the pickaxe it becomes: " + pickaxe.getEnchantments());
        helper.succeed();
    }
}
