package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.core.registry.ModToolMaterials;
import de.ipnats.hardwrought.progression.BlockBreaking;
import de.ipnats.hardwrought.survival.SurvivalSystem;
import de.ipnats.hardwrought.progression.HewnWood;
import de.ipnats.hardwrought.progression.HewnWorkbenchBlock;
import de.ipnats.hardwrought.progression.LogWorking;
import de.ipnats.hardwrought.progression.RecipeSelectionMenu;
import de.ipnats.hardwrought.progression.ToolCrafting;
import de.ipnats.hardwrought.progression.BreakingVerdict;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Milestone 7, the rule the whole early game rests on: what gives way to what, and what is left of
 * it afterwards.
 */
public final class ProgressionGameTests {
    @GameTest
    public void bareHandsDoNotBreakSolidMaterial(GameTestHelper helper) {
        ItemStack hand = ItemStack.EMPTY;
        for (BlockState solid : new BlockState[]{
                Blocks.STONE.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(),
                Blocks.IRON_ORE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState()}) {
            BreakingVerdict verdict = BlockBreaking.verdict(hand, solid);
            helper.assertTrue(verdict == BreakingVerdict.IMPOSSIBLE,
                    "Section 71.1: fists do nothing to " + solid.getBlock().getName().getString());
            helper.assertFalse(verdict.breaks(), "and no amount of punching changes that");
            helper.assertTrue(BlockBreaking.staminaCost(solid, verdict) == 0,
                    "Work that cannot be done costs no stamina either");
        }
        helper.assertTrue(BlockBreaking.solid(Blocks.OAK_LOG.defaultBlockState())
                        && BlockBreaking.solid(Blocks.DEEPSLATE.defaultBlockState()),
                "Timber and stone are both solid material");
        helper.assertFalse(BlockBreaking.solid(Blocks.SHORT_GRASS.defaultBlockState()),
                "while a tuft of grass is not");
        expectFailure(() -> BlockBreaking.verdict(null, Blocks.STONE.defaultBlockState()));
        helper.succeed();
    }

    @GameTest
    public void theRightToolIsTheOneThatWorks(GameTestHelper helper) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.assertTrue(BlockBreaking.verdict(new ItemStack(ModItems.FLINT_PICKAXE), stone)
                        == BreakingVerdict.PROPER,
                "A knapped pick is the right tool for stone, which is the whole point of making one");
        helper.assertTrue(BlockBreaking.verdict(new ItemStack(ModItems.FLINT_HATCHET), stone)
                        == BreakingVerdict.IMPROVISED,
                "An axe against stone works, badly");
        helper.assertTrue(BlockBreaking.verdict(new ItemStack(ModItems.FLINT_HATCHET),
                        Blocks.OAK_LOG.defaultBlockState()) == BreakingVerdict.PROPER,
                "and the same axe against a log is exactly right");

        // Section 71.1.5 and .10: the right kind of tool but too soft is vanilla's own rule, and it
        // already refuses the drops. Hardwrought only makes it slow and exhausting as well.
        helper.assertTrue(BlockBreaking.verdict(new ItemStack(ModItems.FLINT_PICKAXE),
                        Blocks.DIAMOND_ORE.defaultBlockState()) == BreakingVerdict.IMPROVISED,
                "A flint pick on diamond ore is a tool too soft for the rock");

        helper.assertTrue(BreakingVerdict.PROPER.speedFactor() > BreakingVerdict.IMPROVISED.speedFactor()
                        && BreakingVerdict.IMPROVISED.staminaFactor() > BreakingVerdict.PROPER.staminaFactor(),
                "Section 71.1.9: the wrong tool is slower and more exhausting, which is what makes a "
                        + "better one feel like one");
        helper.assertTrue(BreakingVerdict.IMPROVISED.toolDamage() > 0,
                "and it wears out faster for being misused");
        helper.succeed();
    }

    @GameTest
    public void workedMaterialIsSlowerThanVanillaEvenWithTheRightTool(GameTestHelper helper) {
        // The rule the mod exists for: a knapped edge against rock is work. Vanilla speed with the
        // correct tool was the one place the progression said nothing at all.
        for (BlockState worked : new BlockState[]{
                Blocks.STONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
                Blocks.IRON_ORE.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(),
                Blocks.DIRT.defaultBlockState()}) {
            double factor = BlockBreaking.laborFactor(worked);
            helper.assertTrue(factor < 1.0 && factor >= BlockBreaking.MIN_LABOR,
                    "Worked material costs more than vanilla and never stalls: "
                            + worked.getBlock().getName().getString() + " at " + factor);
        }

        // Harder material drags further, which is what makes deepslate feel unlike stone.
        helper.assertTrue(BlockBreaking.laborFactor(Blocks.DEEPSLATE.defaultBlockState())
                        < BlockBreaking.laborFactor(Blocks.STONE.defaultBlockState()),
                "Deepslate is heavier going than stone");
        helper.assertTrue(BlockBreaking.laborFactor(Blocks.STONE.defaultBlockState())
                        < BlockBreaking.laborFactor(Blocks.DIRT.defaultBlockState()),
                "and stone heavier going than soil");
        helper.assertTrue(BlockBreaking.laborFactor(Blocks.OBSIDIAN.defaultBlockState())
                        == BlockBreaking.MIN_LABOR,
                "However hard the rock, the floor holds and a swing still lands");

        // Anything gathered rather than worked is untouched. Punching a bush was never the problem.
        for (BlockState gathered : new BlockState[]{
                Blocks.SHORT_GRASS.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(),
                Blocks.WHEAT.defaultBlockState()}) {
            helper.assertTrue(BlockBreaking.laborFactor(gathered) == 1.0,
                    "Gathered material keeps vanilla speed: "
                            + gathered.getBlock().getName().getString());
        }
        helper.assertTrue(BlockBreaking.laborFactor(null) == 1.0, "and nothing at all is harmless");
        helper.succeed();
    }

    @GameTest
    public void breakingRockCostsMoreThanRestingRestores(GameTestHelper helper) {
        // Mining has to be self-limiting or none of the slowdown is felt: at the old cost a player
        // standing still recovered more stamina per block than the block took off them.
        BlockState stone = Blocks.STONE.defaultBlockState();
        // Both costs land on the same break: the effort the material takes, and the flat cost of
        // having broken a block at all.
        double perBlock = BlockBreaking.staminaCost(stone, BreakingVerdict.PROPER)
                + SurvivalSystem.MINED_BLOCK_STAMINA;
        double seconds = 1.5 * 1.5 / 4.0 / BlockBreaking.laborFactor(stone);
        double restored = seconds * 20.0 * SurvivalSystem.IDLE_RECOVERY_PER_TICK;
        helper.assertTrue(perBlock > restored,
                "One block of stone costs more than standing over it gives back: "
                        + perBlock + " against " + restored);
        helper.assertTrue(BlockBreaking.staminaCost(Blocks.DEEPSLATE.defaultBlockState(),
                        BreakingVerdict.PROPER) > perBlock,
                "and harder rock costs more again");
        helper.succeed();
    }

    @GameTest
    public void flintToolsStaySlowAndFragile(GameTestHelper helper) {
        ItemStack flintPickaxe = new ItemStack(ModItems.FLINT_PICKAXE);
        float flintSpeed = flintPickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState());
        float woodenSpeed = new ItemStack(Items.WOODEN_PICKAXE)
                .getDestroySpeed(Blocks.STONE.defaultBlockState());
        helper.assertTrue(flintSpeed > 1.0F && flintSpeed < woodenSpeed,
                "Flint must work as a proper tool, but mine more slowly than wood");

        int goldenDurability = new ItemStack(Items.GOLDEN_PICKAXE).getMaxDamage();
        helper.assertTrue(ModToolMaterials.FLINT_DURABILITY == goldenDurability,
                "Flint has roughly gold-tier durability");
        for (Item flintTool : new Item[]{
                ModItems.FLINT_DAGGER, ModItems.FLINT_HATCHET, ModItems.FLINT_HOE,
                ModItems.FLINT_PICKAXE, ModItems.FLINT_SHOVEL, ModItems.FLINT_SWORD}) {
            helper.assertTrue(new ItemStack(flintTool).getMaxDamage() == goldenDurability,
                    "Every flint tool must inherit the fragile flint material");
        }
        helper.succeed();
    }

    @GameTest
    public void groundAndGrowthStayWithinReachOfHands(GameTestHelper helper) {
        ItemStack hand = ItemStack.EMPTY;
        helper.assertTrue(BlockBreaking.verdict(hand, Blocks.SHORT_GRASS.defaultBlockState())
                        == BreakingVerdict.LOOSE,
                "Section 71.1.1: loose growth is gathered by hand");
        helper.assertTrue(BlockBreaking.verdict(hand, Blocks.OAK_LEAVES.defaultBlockState())
                        == BreakingVerdict.LOOSE,
                "and so are leaves, which is where the first fibre comes from");

        BreakingVerdict dirt = BlockBreaking.verdict(hand, Blocks.DIRT.defaultBlockState());
        helper.assertTrue(dirt == BreakingVerdict.DIGGABLE,
                "Section 71.1.2: soil can be moved by hand");
        helper.assertTrue(dirt.breaks() && dirt.speedFactor() < 1.0,
                "slowly");
        helper.assertTrue(BlockBreaking.staminaCost(Blocks.DIRT.defaultBlockState(), dirt)
                        > BlockBreaking.staminaCost(Blocks.DIRT.defaultBlockState(), BreakingVerdict.PROPER),
                "and at a far higher price than with a shovel");
        helper.assertTrue(dirt.yield() == BreakingVerdict.Yield.HANDFUL,
                "and what comes up is a handful of loose ground, not a clean block");
        helper.assertTrue(BlockBreaking.handful(Blocks.DIRT.defaultBlockState()).is(ModItems.DIRT_BLOB),
                "Dirt dug by hand comes up as blobs of dirt");
        helper.assertTrue(BlockBreaking.handful(Blocks.GRAVEL.defaultBlockState()).is(ModItems.FLINT_SHARD),
                "and gravel gives up the flint in it, which is the start of every tool");

        helper.assertTrue(BlockBreaking.verdict(new ItemStack(Items.IRON_SHOVEL),
                        Blocks.DIRT.defaultBlockState()) == BreakingVerdict.PROPER,
                "A shovel does the same work properly");
        helper.succeed();
    }

    @GameTest
    public void glassBreaksButIsNotHarvested(GameTestHelper helper) {
        BreakingVerdict glass = BlockBreaking.verdict(ItemStack.EMPTY, Blocks.GLASS.defaultBlockState());
        helper.assertTrue(glass == BreakingVerdict.SHATTERS,
                "Section 71.1.7: glass gives way to a fist");
        helper.assertTrue(glass.breaks() && glass.yield() == BreakingVerdict.Yield.NOTHING,
                "and leaves nothing to pick up, which is breaking without harvesting");
        helper.assertTrue(BreakingVerdict.SHATTERS.interceptsDrops()
                        && BreakingVerdict.DIGGABLE.interceptsDrops()
                        && !BreakingVerdict.PROPER.interceptsDrops(),
                "Only the verdicts that change the drops take them over from vanilla");
        helper.succeed();
    }

    @GameTest
    public void aPlayerWithNothingCanStartAgain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Section 71.1.8: no softlock. Everything the first tool needs has to be reachable with bare
        // hands, so this walks the chain rather than trusting that it exists.
        ItemStack hand = ItemStack.EMPTY;
        helper.assertTrue(BlockBreaking.verdict(hand, Blocks.OAK_LEAVES.defaultBlockState()).breaks(),
                "Leaves give sticks and fibre");
        helper.assertTrue(BlockBreaking.verdict(hand, Blocks.GRAVEL.defaultBlockState()).breaks(),
                "gravel gives flint");
        helper.assertTrue(BlockBreaking.handful(Blocks.GRAVEL.defaultBlockState()).is(ModItems.FLINT_SHARD),
                "and the flint is what a first blade is knapped from");

        var recipes = level.recipeAccess();
        helper.assertTrue(recipes != null, "The recipe table exists");
        helper.assertTrue(BlockBreaking.verdict(new ItemStack(ModItems.FLINT_HATCHET),
                        Blocks.OAK_LOG.defaultBlockState()) == BreakingVerdict.PROPER,
                "and the tool it becomes is what finally opens the wood");
        helper.succeed();
    }

    @GameTest
    public void earthAndStoneAlwaysBreakIntoParts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO).above(30);
        try {
            assertOnlyDrops(helper, level, pos, Blocks.DIRT.defaultBlockState(),
                    new ItemStack(Items.IRON_SHOVEL), ModItems.DIRT_BLOB, 4);
            assertOnlyDrops(helper, level, pos, Blocks.GRASS_BLOCK.defaultBlockState(),
                    new ItemStack(Items.IRON_SHOVEL), ModItems.DIRT_BLOB, 4);
            assertOnlyDrops(helper, level, pos, Blocks.STONE.defaultBlockState(),
                    new ItemStack(Items.IRON_PICKAXE), ModItems.COBBLESTONE_PIECE, 4);

            ItemStack silkShovel = new ItemStack(Items.IRON_SHOVEL);
            ItemStack silkPickaxe = new ItemStack(Items.IRON_PICKAXE);
            var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var silkTouch = enchantments.getOrThrow(Enchantments.SILK_TOUCH);
            silkShovel.enchant(silkTouch, 1);
            silkPickaxe.enchant(silkTouch, 1);
            assertOnlyDrops(helper, level, pos, Blocks.DIRT.defaultBlockState(),
                    silkShovel, Items.DIRT, 1);
            assertOnlyDrops(helper, level, pos, Blocks.GRASS_BLOCK.defaultBlockState(),
                    silkShovel, Items.GRASS_BLOCK, 1);
            assertOnlyDrops(helper, level, pos, Blocks.STONE.defaultBlockState(),
                    silkPickaxe, Items.STONE, 1);

            helper.assertTrue(ModBlocks.DIRT_SLAB.asItem() == ModItems.DIRT_SLAB,
                    "The dirt slab is registered as a placeable block item");
            assertOnlyDrops(helper, level, pos, ModBlocks.DIRT_SLAB.defaultBlockState(),
                    new ItemStack(Items.IRON_SHOVEL), ModItems.DIRT_SLAB, 1);
            assertOnlyDrops(helper, level, pos, ModBlocks.DIRT_SLAB.defaultBlockState()
                            .setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                    new ItemStack(Items.IRON_SHOVEL), ModItems.DIRT_SLAB, 2);
        } finally {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }


    @GameTest
    public void aBenchIsCutOutOfTheTreeItKeepsTheBarkOf(GameTestHelper helper) {
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        helper.assertTrue(LogWorking.isTimber(log)
                        && !LogWorking.isTimber(Blocks.DIRT.defaultBlockState()),
                "Section 71.1.3: a log is timber, soil is not");
        helper.assertTrue(LogWorking.strokesNeeded(ItemStack.EMPTY, log) == Integer.MAX_VALUE,
                "Bare hands cut nothing out of a log, however long they try");
        helper.assertTrue(LogWorking.strokesNeeded(new ItemStack(ModItems.FLINT_HATCHET), log)
                        == Integer.MAX_VALUE,
                "and a knapped edge cannot cut a flat working surface out of round timber");

        int iron = LogWorking.strokesNeeded(new ItemStack(ModItems.IRON_HATCHET), log);
        helper.assertTrue(iron < Integer.MAX_VALUE && iron >= LogWorking.MIN_STROKES,
                "The joiner's hatchet can, and it takes real work");
        helper.assertTrue(iron <= LogWorking.BASE_STROKES, "though it does finish");

        // The bench keeps the bark of its own tree; an oak stump under a birch looks pasted in.
        helper.assertTrue(HewnWood.of(Blocks.BIRCH_LOG.defaultBlockState()) == HewnWood.BIRCH
                        && HewnWood.of(Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()) == HewnWood.SPRUCE
                        && HewnWood.of(Blocks.DARK_OAK_WOOD.defaultBlockState()) == HewnWood.DARK_OAK
                        && HewnWood.of(Blocks.POPLAR_LOG.defaultBlockState()) == HewnWood.POPLAR
                        && HewnWood.of(Blocks.CRIMSON_STEM.defaultBlockState()) == HewnWood.CRIMSON,
                "Every kind of timber is recognised, stripped, wood and stem alike");
        helper.assertTrue(HewnWood.of(Blocks.STONE.defaultBlockState()) == HewnWood.OAK,
                "and anything unknown falls back to oak rather than failing");
        helper.assertTrue(ModBlocks.HEWN_WORKBENCH.defaultBlockState()
                        .setValue(HewnWorkbenchBlock.WOOD, HewnWood.CHERRY)
                        .getValue(HewnWorkbenchBlock.WOOD) == HewnWood.CHERRY,
                "The wood is part of the block, so it survives being looked at again");

        ItemStack poplarBench = ModBlocks.HEWN_WORKBENCH.itemStack(HewnWood.POPLAR);
        var itemState = poplarBench.get(DataComponents.BLOCK_STATE);
        helper.assertTrue(itemState != null
                        && itemState.get(HewnWorkbenchBlock.WOOD) == HewnWood.POPLAR
                        && itemState.apply(ModBlocks.HEWN_WORKBENCH.defaultBlockState())
                                .getValue(HewnWorkbenchBlock.WOOD) == HewnWood.POPLAR,
                "The inventory stack keeps its wood for its model and when placed again");
        helper.succeed();
    }

    @GameTest
    public void aToolInARecipeIsUsedNotUsedUp(GameTestHelper helper) {
        ItemStack hatchet = new ItemStack(ModItems.IRON_HATCHET);
        helper.assertTrue(ToolCrafting.isCraftingTool(hatchet),
                "The iron hatchet is what a workbench is joined with");
        helper.assertFalse(ToolCrafting.isCraftingTool(new ItemStack(Items.OAK_PLANKS)),
                "while a board is a material, not a tool");

        ItemStack returned = ToolCrafting.worn(hatchet);
        helper.assertTrue(returned.is(ModItems.IRON_HATCHET) && returned.getCount() == 1,
                "Crafting with it gives it back");
        helper.assertTrue(returned.getDamageValue() == hatchet.getDamageValue() + ToolCrafting.WEAR_PER_CRAFT,
                "one use of wear worse");

        ItemStack spent = new ItemStack(ModItems.IRON_HATCHET);
        spent.setDamageValue(spent.getMaxDamage() - 1);
        helper.assertTrue(ToolCrafting.worn(spent).isEmpty(),
                "and a tool that finally wears through does not come back");
        helper.succeed();
    }

    @GameTest
    public void anyAxeOfIronTierOrBetterJoins(GameTestHelper helper) {
        // The rule is about reach, not about one item: a player holding a diamond axe has plainly
        // got past the point this gate exists for, and saying so in a list would miss every axe the
        // mod has never heard of.
        for (Item good : new Item[]{ModItems.IRON_HATCHET, ModItems.BRONZE_HATCHET,
                Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE}) {
            helper.assertTrue(ToolCrafting.isCraftingTool(new ItemStack(good)),
                    "An axe of iron tier or better joins: " + good.getName(new ItemStack(good)).getString());
        }

        // Below iron it is refused, which is the progression the whole milestone rests on.
        for (Item soft : new Item[]{ModItems.FLINT_HATCHET, ModItems.STONE_HATCHET,
                Items.WOODEN_AXE, Items.STONE_AXE, Items.COPPER_AXE, Items.GOLDEN_AXE}) {
            helper.assertFalse(ToolCrafting.isCraftingTool(new ItemStack(soft)),
                    "and a softer edge is not enough: "
                            + soft.getName(new ItemStack(soft)).getString());
        }

        // Gold is the case a mining-speed test would get wrong: it is the fastest tool in the game
        // and still too soft to be trusted with anything.
        helper.assertTrue(new ItemStack(Items.GOLDEN_AXE).getDestroySpeed(
                        Blocks.OAK_LOG.defaultBlockState())
                        > new ItemStack(Items.IRON_AXE).getDestroySpeed(
                                Blocks.OAK_LOG.defaultBlockState()),
                "A golden axe cuts faster than an iron one");
        helper.assertFalse(ToolCrafting.isJoinersAxe(new ItemStack(Items.GOLDEN_AXE)),
                "and is still refused, because the question is tier and not speed");

        // The right tier of the wrong kind is no help either: a bench is cut, not quarried.
        for (Item wrongKind : new Item[]{Items.DIAMOND_PICKAXE, Items.NETHERITE_SHOVEL,
                Items.IRON_SWORD, Items.SHEARS, Items.OAK_PLANKS}) {
            helper.assertFalse(ToolCrafting.isCraftingTool(new ItemStack(wrongKind)),
                    "Only an axe joins timber: "
                            + wrongKind.getName(new ItemStack(wrongKind)).getString());
        }
        helper.assertFalse(ToolCrafting.isCraftingTool(ItemStack.EMPTY),
                "and an empty hand joins nothing");

        // A good axe carries the recipe rule with it: used, not used up.
        ItemStack diamond = new ItemStack(Items.DIAMOND_AXE);
        ItemStack returned = ToolCrafting.worn(diamond);
        helper.assertTrue(returned.is(Items.DIAMOND_AXE)
                        && returned.getDamageValue() == ToolCrafting.WEAR_PER_CRAFT,
                "A diamond axe comes back out of the grid one point of wear worse");

        // A better axe is never slower at the bench. Iron already reaches the floor of MIN_STROKES,
        // so nothing above it is faster either — the reward for a diamond axe is reach, not speed.
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        int byDiamond = LogWorking.strokesNeeded(diamond, log);
        int byIron = LogWorking.strokesNeeded(new ItemStack(ModItems.IRON_HATCHET), log);
        helper.assertTrue(byDiamond <= byIron && byDiamond == LogWorking.MIN_STROKES,
                "Every axe that may hew is already at the floor of the stroke count: "
                        + byDiamond + " against " + byIron);
        helper.assertTrue(LogWorking.strokesNeeded(new ItemStack(ModItems.FLINT_HATCHET), log)
                        == Integer.MAX_VALUE,
                "while one that may not never finishes at all");
        helper.succeed();
    }


    @GameTest
    public void exhaustionIsOnlyFeltOnTheLastFourDrops(GameTestHelper helper) {
        helper.assertTrue(SurvivalSystem.exhaustion(100) == 0 && SurvivalSystem.exhaustion(41) == 0,
                "A player with reserves left is working, not struggling");
        helper.assertTrue(SurvivalSystem.exhaustion(SurvivalSystem.EXHAUSTION_THRESHOLD) == 0,
                "and the threshold itself still costs nothing");
        helper.assertTrue(SurvivalSystem.exhaustion(20) > 0 && SurvivalSystem.exhaustion(20) < 1,
                "Below it the penalty comes on gradually");
        helper.assertTrue(SurvivalSystem.exhaustion(0) == 1.0 && SurvivalSystem.exhaustion(-5) == 1.0,
                "and reaches its full weight on an empty bar, never more");
        helper.assertTrue(SurvivalSystem.exhaustion(10) > SurvivalSystem.exhaustion(30),
                "The emptier the bar, the worse it is");
        helper.succeed();
    }

    @GameTest
    public void thereIsAWayToMetalWithoutAWorkbench(GameTestHelper helper) {
        var recipes = helper.getLevel().recipeAccess();
        helper.assertTrue(hasRecipe(helper, "minecraft:campfire"),
                "A fire pit can be scraped together");
        helper.assertTrue(recipeFitsInventoryGrid(helper, "minecraft:campfire"),
                "and it fits the 2x2 grid, or the chain to a workbench is closed");
        helper.assertTrue(recipeFitsInventoryGrid(helper, "hardwrought:iron_hatchet")
                        && recipeFitsInventoryGrid(helper, "hardwrought:flint_hatchet"),
                "Every tool on the way to a workbench fits it too");
        helper.assertTrue(hasRecipe(helper, "hardwrought:iron_ingot_from_campfire"),
                "Section 55: ore becomes metal in the fire pit, slowly");
        helper.assertTrue(hasRecipe(helper, "hardwrought:bronze_mixture")
                        && hasRecipe(helper, "hardwrought:bronze_ingot_from_campfire"),
                "Section 56: three parts copper to one of tin, and back into the fire");
        helper.assertTrue(recipes != null, "The recipe table is loaded");
        helper.succeed();
    }

    @GameTest
    public void primitiveHatchetAndPickaxeRecipesAreUnambiguous(GameTestHelper helper) {
        var recipes = helper.getLevel().recipeAccess();
        var hatchetHolder = recipes.byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.parse("hardwrought:flint_hatchet")));
        var pickaxeHolder = recipes.byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.parse("hardwrought:flint_pickaxe")));
        helper.assertTrue(hatchetHolder.isPresent() && pickaxeHolder.isPresent()
                        && hatchetHolder.get().value() instanceof net.minecraft.world.item.crafting.ShapedRecipe
                        && pickaxeHolder.get().value() instanceof net.minecraft.world.item.crafting.ShapedRecipe,
                "Both primitive tool recipes are loaded as shaped recipes");

        var hatchet = (net.minecraft.world.item.crafting.ShapedRecipe) hatchetHolder.get().value();
        var pickaxe = (net.minecraft.world.item.crafting.ShapedRecipe) pickaxeHolder.get().value();
        var hatchetLayout = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(
                new ItemStack(ModItems.FLINT_SHARD), new ItemStack(ModItems.LEAF_STRING),
                new ItemStack(ModItems.FLINT_SHARD), new ItemStack(Items.STICK)));
        var pickaxeLayout = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(
                new ItemStack(ModItems.FLINT_SHARD), new ItemStack(ModItems.FLINT_SHARD),
                new ItemStack(ModItems.LEAF_STRING), new ItemStack(Items.STICK)));

        helper.assertTrue(hatchet.matches(hatchetLayout, helper.getLevel())
                        && !pickaxe.matches(hatchetLayout, helper.getLevel()),
                "The vertical flint head makes only the hatchet");
        helper.assertTrue(pickaxe.matches(pickaxeLayout, helper.getLevel())
                        && !hatchet.matches(pickaxeLayout, helper.getLevel()),
                "The horizontal flint head makes only the pickaxe, even with recipe mirroring");
        helper.succeed();
    }

    @GameTest
    public void matchingCraftingRecipesCanBeSelected(GameTestHelper helper) {
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        net.minecraft.world.inventory.InventoryMenu menu = player.inventoryMenu;
        try {
            menu.getCraftSlots().setItem(0, new ItemStack(Items.BARRIER));
            menu.slotsChanged(menu.getCraftSlots());
            RecipeSelectionMenu selection = (RecipeSelectionMenu) menu;
            helper.assertTrue(selection.hardwrought$recipeChoiceCount() == 2,
                    "The inventory notices both recipes that match the same ingredient");

            Item before = menu.getResultSlot().getItem().getItem();
            helper.assertTrue(before == Items.DIAMOND || before == Items.EMERALD,
                    "One of the matching results is shown first");
            helper.assertTrue(menu.clickMenuButton(player, RecipeSelectionMenu.NEXT_RECIPE_BUTTON),
                    "The recipe choice button is accepted by the server");
            Item after = menu.getResultSlot().getItem().getItem();
            helper.assertTrue(after != before && (after == Items.DIAMOND || after == Items.EMERALD),
                    "Choosing again changes to the other matching result");

            menu.slotsChanged(menu.getCraftSlots());
            helper.assertTrue(menu.getResultSlot().getItem().is(after),
                    "The chosen result remains selected when the grid is recalculated");
        } finally {
            menu.getCraftSlots().clearContent();
            player.closeContainer();
        }
        helper.succeed();
    }


    @GameTest
    public void theFirstWorkbenchIsHewnOutOfALog(GameTestHelper helper) {
        helper.assertFalse(recipeFitsInventoryGrid(helper, "minecraft:crafting_table"),
                "An ordinary workbench is joinery that needs a workbench: six boards, three wide");
        helper.assertTrue(hasRecipe(helper, "minecraft:crafting_table"),
                "so it stays craftable once the first bench exists");

        helper.assertTrue(ModBlocks.HEWN_WORKBENCH.asItem() == ModItems.HEWN_WORKBENCH,
                "The hewn bench is a real block with an item of its own");
        helper.assertTrue(ModBlocks.HEWN_WORKBENCH
                        instanceof net.minecraft.world.level.block.CraftingTableBlock,
                "and it opens the same grid a crafting table does");
        for (var block : new net.minecraft.world.level.block.Block[]{
                ModBlocks.HEWN_WORKBENCH, ModBlocks.DIRT_SLAB}) {
            helper.assertTrue(block.asItem().getDescriptionId().equals(block.getDescriptionId()),
                    block.getDescriptionId() + " is named after its block rather than after itself");
        }
        helper.succeed();
    }


    @GameTest
    public void theHewnBenchOpensAndStaysOpen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = player.blockPosition();
        BlockState previous = level.getBlockState(pos);
        try {
            level.setBlockAndUpdate(pos, ModBlocks.HEWN_WORKBENCH.defaultBlockState()
                    .setValue(HewnWorkbenchBlock.WOOD, HewnWood.BIRCH));
            var provider = level.getBlockState(pos).getMenuProvider(level, pos);
            helper.assertTrue(provider != null, "A hewn bench offers a menu at all");

            player.openMenu(provider);
            helper.assertTrue(player.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu,
                    "and what opens is the crafting grid");
            // The menu is re-checked every tick against the block it was opened on. Vanilla asks for
            // minecraft:crafting_table by identity, which shut this grid again in the same breath.
            helper.assertTrue(player.containerMenu.stillValid(player),
                    "and it stays open on a bench that is not vanilla's own crafting table");
        } finally {
            player.closeContainer();
            level.setBlockAndUpdate(pos, previous);
        }
        helper.succeed();
    }

    private static boolean hasRecipe(GameTestHelper helper, String id) {
        return helper.getLevel().recipeAccess()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).isPresent();
    }

    /** Whether this recipe can be made in the 2x2 grid every player carries. */
    private static boolean recipeFitsInventoryGrid(GameTestHelper helper, String id) {
        var holder = helper.getLevel().recipeAccess()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)));
        if (holder.isEmpty()) return false;
        var recipe = holder.get().value();
        if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
            return shaped.getWidth() <= 2 && shaped.getHeight() <= 2;
        }
        // A shapeless recipe fits whenever it asks for no more than the four slots there are.
        return recipe instanceof net.minecraft.world.item.crafting.ShapelessRecipe shapeless
                && shapeless.placementInfo().ingredients().size() <= 4;
    }

    private static void assertOnlyDrops(GameTestHelper helper, ServerLevel level, BlockPos pos,
                                        BlockState state, ItemStack tool, net.minecraft.world.item.Item item,
                                        int count) {
        level.setBlockAndUpdate(pos, state);
        var drops = Block.getDrops(state, level, pos, null, null, tool);
        int matching = drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(matching == count && drops.stream().allMatch(stack -> stack.is(item)),
                state.getBlock().getName().getString() + " must drop only " + count + " parts");
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected operation to be rejected");
    }
}
