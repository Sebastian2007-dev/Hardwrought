package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.core.registry.ModToolMaterials;
import de.ipnats.hardwrought.progression.BenchTier;
import de.ipnats.hardwrought.progression.BlockBreaking;
import de.ipnats.hardwrought.progression.NailedWorkbenchBlock;
import de.ipnats.hardwrought.survival.SurvivalSystem;
import de.ipnats.hardwrought.progression.HewnWood;
import de.ipnats.hardwrought.progression.HewnWorkbenchBlock;
import de.ipnats.hardwrought.progression.LogWorking;
import de.ipnats.hardwrought.progression.NailDriving;
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
        helper.assertTrue(hasRecipe(helper, "hardwrought:tin_ingot_from_campfire")
                        && !hasRecipe(helper, "hardwrought:iron_ingot_from_campfire"),
                "Section 55: the soft metals are cast in the fire pit, but iron is forged, not cast");
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
        var stringHatchetLayout = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(
                new ItemStack(ModItems.FLINT_SHARD), new ItemStack(Items.STRING),
                new ItemStack(ModItems.FLINT_SHARD), new ItemStack(Items.STICK)));
        var stringPickaxeLayout = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(
                new ItemStack(ModItems.FLINT_SHARD), new ItemStack(ModItems.FLINT_SHARD),
                new ItemStack(Items.STRING), new ItemStack(Items.STICK)));

        helper.assertTrue(hatchet.matches(hatchetLayout, helper.getLevel())
                        && !pickaxe.matches(hatchetLayout, helper.getLevel()),
                "The vertical flint head makes only the hatchet");
        helper.assertTrue(pickaxe.matches(pickaxeLayout, helper.getLevel())
                        && !hatchet.matches(pickaxeLayout, helper.getLevel()),
                "The horizontal flint head makes only the pickaxe, even with recipe mirroring");
        helper.assertTrue(hatchet.matches(stringHatchetLayout, helper.getLevel())
                        && pickaxe.matches(stringPickaxeLayout, helper.getLevel()),
                "Vanilla string can replace leaf cord in primitive tool recipes");

        var stoneHatchet = (net.minecraft.world.item.crafting.ShapedRecipe) recipes.byKey(ResourceKey.create(
                Registries.RECIPE, Identifier.parse("hardwrought:stone_hatchet"))).orElseThrow().value();
        var stonePickaxe = (net.minecraft.world.item.crafting.ShapedRecipe) recipes.byKey(ResourceKey.create(
                Registries.RECIPE, Identifier.parse("hardwrought:stone_pickaxe"))).orElseThrow().value();
        var stone = new ItemStack(ModItems.COBBLESTONE_PIECE);
        for (var layout : java.util.List.of(
                net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(
                        stone, stone, new ItemStack(ModItems.LEAF_STRING), new ItemStack(Items.STICK))),
                net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(
                        stone, stone, new ItemStack(Items.STICK), new ItemStack(ModItems.LEAF_STRING))),
                net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(
                        stone, new ItemStack(ModItems.LEAF_STRING), stone, new ItemStack(Items.STICK))))) {
            helper.assertFalse(stoneHatchet.matches(layout, helper.getLevel()) && stonePickaxe.matches(layout, helper.getLevel()),
                    "No layout of stone pieces makes both the stone hatchet and the stone pickaxe");
        }
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
        helper.assertTrue(hasRecipe(helper, "minecraft:crafting_table"),
                "Vanilla's crafting table recipe is replaced rather than removed: the game refers "
                        + "to it, so the id has to stay bound, and it now nails the hewn bench");

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
    public void aHammerDrivesBronzeNailsIntoTheHewnBench(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos pos = player.blockPosition().above(3);
        BlockState previous = level.getBlockState(pos);
        try {
            helper.assertFalse(hasRecipe(helper, "hardwrought:bronze_nails"),
                    "Bronze nails are not crafted any more");
            helper.assertTrue(de.ipnats.hardwrought.smithing.Smithing.recipeFor(ModItems.BRONZE_INGOT,
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.BRONZE_NAILS)) != null
                            && hasRecipe(helper, "hardwrought:hammer"),
                    "Bronze is forged out into nails, and there is a hammer to drive them");
            for (Item item : new Item[]{ModItems.BRONZE_NAILS, ModItems.BRONZE_MIXTURE, ModItems.HAMMER}) {
                helper.assertTrue(BenchTier.allows(BenchTier.HEWN, new ItemStack(item)),
                        item + " is made at the hewn bench, or the way to the nailed one would be closed");
            }

            level.setBlockAndUpdate(pos, ModBlocks.HEWN_WORKBENCH.defaultBlockState()
                    .setValue(HewnWorkbenchBlock.WOOD, HewnWood.BIRCH));
            ItemStack hammer = new ItemStack(ModItems.HAMMER);
            player.getInventory().clearContent();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hammer);
            for (int blow = 0; blow < NailDriving.STROKES + 2; blow++) {
                NailDriving.strike(player, level, pos, level.getBlockState(pos),
                        net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            helper.assertTrue(level.getBlockState(pos).is(ModBlocks.HEWN_WORKBENCH),
                    "A hammer with no nails to drive only knocks on wood");

            ItemStack nails = new ItemStack(ModItems.BRONZE_NAILS, 3);
            player.getInventory().add(nails);
            for (int blow = 1; blow < NailDriving.STROKES; blow++) {
                helper.assertFalse(NailDriving.strike(player, level, pos, level.getBlockState(pos),
                                net.minecraft.world.InteractionHand.MAIN_HAND),
                        "One blow is not a nailed bench: blow " + blow);
            }
            helper.assertTrue(level.getBlockState(pos).is(ModBlocks.HEWN_WORKBENCH),
                    "It takes every blow, not most of them");
            helper.assertTrue(NailDriving.strike(player, level, pos, level.getBlockState(pos),
                            net.minecraft.world.InteractionHand.MAIN_HAND),
                    "The last blow finishes it");

            BlockState nailed = level.getBlockState(pos);
            helper.assertTrue(nailed.is(ModBlocks.NAILED_WORKBENCH), "as the nailed bench, where it stands");
            helper.assertTrue(nailed.getValue(NailedWorkbenchBlock.WOOD) == HewnWood.BIRCH,
                    "in the wood it was cut from");
            helper.assertTrue(player.getInventory().countItem(ModItems.BRONZE_NAILS) == 2,
                    "It costs one handful of nails");
            helper.assertTrue(player.getMainHandItem().getDamageValue() == NailDriving.TOOL_WEAR,
                    "and wears the hammer");
            helper.assertTrue(BenchTier.of(nailed) == BenchTier.JOINED, "which is the next rung");
        } finally {
            player.getInventory().clearContent();
            level.setBlockAndUpdate(pos, previous);
        }
        helper.succeed();
    }

    @GameTest
    public void theNailedBenchKeepsWhatIsLeftOnIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer first = helper.makeMockServerPlayerInLevel();
        net.minecraft.server.level.ServerPlayer second = helper.makeMockServerPlayerInLevel();
        BlockPos pos = first.blockPosition().above(3);
        BlockState previous = level.getBlockState(pos);
        try {
            level.setBlockAndUpdate(pos, ModBlocks.NAILED_WORKBENCH.defaultBlockState());
            var grid = (de.ipnats.hardwrought.progression.KeptGridBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(grid != null, "A nailed bench has somewhere to keep its grid");
            var access = net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos);
            first.getInventory().clearContent();

            helper.assertTrue(grid.claim(first), "The first player to use it gets the bench");
            var menu = new de.ipnats.hardwrought.progression.KeptCraftingMenu(1, first.getInventory(), access, grid);
            first.containerMenu = menu;
            menu.getSlot(1).set(new ItemStack(Items.STICK, 3));
            helper.assertFalse(grid.claim(second), "and nobody else can work the same grid meanwhile");

            menu.removed(first);
            first.containerMenu = first.inventoryMenu;
            helper.assertTrue(first.getInventory().countItem(Items.STICK) == 0,
                    "Closing it does not hand the grid back to the player");
            helper.assertTrue(grid.contents().getFirst().is(Items.STICK) && grid.contents().getFirst().getCount() == 3,
                    "it stays on the bench");

            helper.assertTrue(grid.claim(second), "Once the first player has left it, it is free again");
            var again = new de.ipnats.hardwrought.progression.KeptCraftingMenu(2, second.getInventory(), access, grid);
            helper.assertTrue(again.getSlot(1).getItem().is(Items.STICK),
                    "and whoever opens it next finds what was left there");
            again.removed(second);

            level.destroyBlock(pos, false);
            var dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(2),
                    entity -> entity.getItem().is(Items.STICK));
            helper.assertTrue(dropped.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == 3,
                    "Breaking the bench drops what lay on it, once");
            dropped.forEach(net.minecraft.world.entity.Entity::discard);
        } finally {
            first.getInventory().clearContent();
            level.setBlockAndUpdate(pos, previous);
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
        // Anything without a shape — shapeless, or a part and its handle transmuted into a tool — fits
        // whenever it asks for no more than the four slots there are.
        return recipe.placementInfo().ingredients().size() <= 4;
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

    @GameTest
    public void aStickRubbedOnStoneBecomesANeedleAndCordBecomesAWovenPack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos stone = player.blockPosition().above(4);
        BlockState previous = level.getBlockState(stone);
        try {
            level.setBlockAndUpdate(stone, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 2));
            for (int stroke = 1; stroke < de.ipnats.hardwrought.progression.Weaving.SHARPEN_STROKES; stroke++) {
                helper.assertFalse(de.ipnats.hardwrought.progression.Weaving.sharpen(player, level, stone),
                        "One stroke does not make a point");
            }
            helper.assertTrue(de.ipnats.hardwrought.progression.Weaving.sharpen(player, level, stone),
                    "A few strokes on stone do");
            helper.assertTrue(player.getInventory().countItem(ModItems.POINTED_STICK) == 1
                            && player.getMainHandItem().getCount() == 1,
                    "One stick has become a pointed stick");
            helper.assertTrue(hasRecipe(helper, "hardwrought:sewing_needle")
                            && BenchTier.allows(BenchTier.INVENTORY, new ItemStack(ModItems.SEWING_NEEDLE)),
                    "A needle is made from it in the hands");

            ItemStack needle = new ItemStack(ModItems.SEWING_NEEDLE);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, needle);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(ModItems.LEAF_STRING, 3));
            helper.assertFalse(de.ipnats.hardwrought.progression.Weaving.weave(player, needle),
                    "Three cords are not enough for a piece of cloth");
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(ModItems.LEAF_STRING, 5));
            helper.assertTrue(de.ipnats.hardwrought.progression.Weaving.weave(player, needle),
                    "Four are");
            helper.assertTrue(player.getOffhandItem().getCount() == 1
                            && player.getInventory().countItem(ModItems.WOVEN) == 1 && needle.getDamageValue() == 1,
                    "Four cords became one woven cloth, and the needle wore a little");
            var wooden = (de.ipnats.hardwrought.progression.SewingNeedleItem) ModItems.SEWING_NEEDLE;
            var iron = (de.ipnats.hardwrought.progression.SewingNeedleItem) ModItems.IRON_SEWING_NEEDLE;
            helper.assertTrue(wooden.weaveTicks() > 3 * iron.weaveTicks() - 1,
                    "Weaving with wood takes a while; a forged iron needle is much quicker");
            helper.assertTrue(de.ipnats.hardwrought.smithing.Smithing.recipeFor(Items.IRON_INGOT,
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.IRON_SEWING_NEEDLE)) != null,
                    "and it is forged at the anvil from an iron bar");
            helper.assertFalse(hasRecipe(helper, "hardwrought:starter_backpack"), "The pack is not made in a grid");
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(ModItems.WOVEN, 3));
            helper.assertFalse(de.ipnats.hardwrought.progression.Weaving.sewPack(player, needle),
                    "Three cloths are not enough for a pack");
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(ModItems.WOVEN, 4));
            helper.assertTrue(needle.getUseDuration(player) == 2 * wooden.weaveTicks(),
                    "Sewing a pack takes twice as long as weaving a cloth");
            helper.assertTrue(de.ipnats.hardwrought.progression.Weaving.sewPack(player, needle)
                            && player.getInventory().countItem(ModItems.STARTER_BACKPACK) == 1
                            && player.getOffhandItem().isEmpty(),
                    "Four woven cloths in the other hand are sewn into the woven pack");
        } finally {
            level.setBlockAndUpdate(stone, previous);
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest
    public void chestsNearTheSpawnHoldNoLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getRespawnData().pos();
        BlockPos near = new BlockPos(spawn.getX() + 20, level.getMinY() + 2, spawn.getZ() + 20);
        BlockPos far = new BlockPos(spawn.getX() + 2 * de.ipnats.hardwrought.progression.SpawnLoot.RADIUS,
                level.getMinY() + 2, spawn.getZ());
        BlockState nearBefore = level.getBlockState(near);
        BlockState farBefore = level.getBlockState(far);
        try {
            for (BlockPos pos : new BlockPos[]{near, far}) {
                level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
                net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos,
                        net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON);
                ((net.minecraft.world.RandomizableContainer) level.getBlockEntity(pos)).unpackLootTable(null);
            }
            var nearChest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(near);
            var farChest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(far);
            helper.assertTrue(nearChest.isEmpty() && nearChest.getLootTable() == null,
                    "A dungeon chest by the spawn opens empty");
            helper.assertFalse(farChest.isEmpty(), "One far from it holds its loot");
        } finally {
            for (BlockPos pos : new BlockPos[]{near, far}) {
                if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) container.clearContent();
            }
            level.setBlockAndUpdate(near, nearBefore);
            level.setBlockAndUpdate(far, farBefore);
        }
        helper.succeed();
    }

    @GameTest
    public void greenFibreDriesOnARackInTheSunIntoCord(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        try {
            level.setBlockAndUpdate(pos, de.ipnats.hardwrought.core.registry.ModBlocks.DRYING_RACK.defaultBlockState());
            var rack = (de.ipnats.hardwrought.progression.DryingRackBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(rack.hang(new ItemStack(ModItems.GREEN_FIBRE)), "Green fibre hangs on the rack");
            helper.assertFalse(rack.hang(new ItemStack(Items.STICK)), "A stick has nothing to dry");
            rack.dry(de.ipnats.hardwrought.progression.DryingRackBlockEntity.DRY_TICKS - 20);
            helper.assertTrue(rack.items().getFirst().is(ModItems.GREEN_FIBRE), "Not quite half a day is not enough");
            rack.dry(20);
            helper.assertTrue(rack.items().getFirst().is(ModItems.LEAF_STRING), "Half a day of sun makes it cord");
            helper.assertTrue(rack.takeDown().is(ModItems.LEAF_STRING), "and it comes off the rack as cord");

            level.setBlockAndUpdate(pos.above(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            helper.assertFalse(de.ipnats.hardwrought.progression.DryingRackBlockEntity.drying(level, pos),
                    "Under a roof nothing dries");

            ItemStack needle = new ItemStack(ModItems.SEWING_NEEDLE);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, needle);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(ModItems.GREEN_FIBRE, 8));
            helper.assertFalse(de.ipnats.hardwrought.progression.Weaving.hasCord(player),
                    "Green fibre is too soft to weave");
            helper.assertTrue(new ItemStack(ModItems.GREEN_FIBRE).is(net.minecraft.tags.TagKey.create(
                            net.minecraft.core.registries.Registries.ITEM, Identifier.fromNamespaceAndPath("c", "strings"))),
                    "but it binds a tool as it is");
        } finally {
            level.setBlockAndUpdate(pos.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest
    public void inUltraThePlayersOwnGridOpensARowAtATime(GameTestHelper helper) {
        var tiers = new de.ipnats.hardwrought.equipment.BackpackTier[]{
                de.ipnats.hardwrought.equipment.BackpackTier.STARTER,
                de.ipnats.hardwrought.equipment.BackpackTier.FRAME,
                de.ipnats.hardwrought.equipment.BackpackTier.BASIC};
        for (int i = 0; i < tiers.length; i++) {
            helper.assertTrue(tiers[i].mainRows(true) == i + 1,
                    "In Ultra the " + tiers[i].getSerializedName() + " pack opens " + (i + 1) + " rows");
            helper.assertTrue(tiers[i].mainRows(false) == de.ipnats.hardwrought.equipment.BackpackTier.MAIN_ROWS,
                    "Elsewhere every pack opens the whole grid");
        }
        helper.assertTrue(hasRecipe(helper, "hardwrought:frame_backpack") && hasRecipe(helper, "hardwrought:drying_rack"),
                "The frame and the drying rack are made in a grid");
        helper.succeed();
    }

    @GameTest
    public void aPrimitiveRodIsMadeInTheHandsFromGreenFibre(GameTestHelper helper) {
        var rod = (net.minecraft.world.item.crafting.CraftingRecipe) helper.getLevel().recipeAccess()
                .byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                        Identifier.parse("hardwrought:primitive_fishing_rod"))).orElseThrow().value();
        var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(
                ItemStack.EMPTY, new ItemStack(Items.STICK), new ItemStack(Items.STICK), new ItemStack(ModItems.GREEN_FIBRE)));
        helper.assertTrue(rod.matches(input, helper.getLevel()), "Two sticks and green fibre fit the two-by-two grid");
        ItemStack made = rod.assemble(input);
        helper.assertTrue(made.is(Items.FISHING_ROD) && made.getMaxDamage() == 24,
                "It is a real fishing rod, only a weak one: " + made.getMaxDamage());
        helper.assertTrue(BenchTier.allows(BenchTier.INVENTORY, made), "and it is made in the hands");
        helper.succeed();
    }

    @GameTest
    public void structuresComeUnfurnishedAndTheWoodenAnvilIsMadeInTheHands(GameTestHelper helper) {
        var infos = java.util.List.of(
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                        BlockPos.ZERO, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(), null),
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                        BlockPos.ZERO.above(), net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState(), null),
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                        BlockPos.ZERO.above(2), net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState(), null),
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(
                        BlockPos.ZERO.above(3), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), null));
        var placed = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.processBlockInfos(
                helper.getLevel(), BlockPos.ZERO, BlockPos.ZERO,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), infos);
        helper.assertTrue(placed.get(0).state().is(net.minecraft.world.level.block.Blocks.OAK_PLANKS)
                        && placed.get(1).state().is(net.minecraft.world.level.block.Blocks.COBBLESTONE)
                        && placed.get(2).state().isAir()
                        && placed.get(3).state().is(net.minecraft.world.level.block.Blocks.OAK_PLANKS),
                "A structure places no crafting table, furnace or anvil, and nothing else changes");
        helper.assertTrue(BenchTier.of(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState())
                        == BenchTier.INVENTORY, "A vanilla crafting table is no bench");

        var anvil = (net.minecraft.world.item.crafting.CraftingRecipe) helper.getLevel().recipeAccess()
                .byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                        Identifier.parse("hardwrought:wooden_anvil"))).orElseThrow().value();
        ItemStack log = new ItemStack(Items.OAK_LOG);
        var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(log, log, log, ItemStack.EMPTY));
        helper.assertTrue(anvil.matches(input, helper.getLevel())
                        && BenchTier.allows(BenchTier.INVENTORY, new ItemStack(ModItems.WOODEN_ANVIL)),
                "Three logs make the wooden anvil in the hands");
        helper.succeed();
    }

    @GameTest
    public void aPlayerCanChooseToCrawl(GameTestHelper helper) {
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var standing = net.minecraft.world.entity.Pose.STANDING;
        helper.assertTrue(de.ipnats.hardwrought.survival.Crawling.desired(player, standing) == standing,
                "Nobody crawls who has not chosen to");
        ((de.ipnats.hardwrought.survival.Crawling.Crawler) player).hardwrought$setCrawling(true);
        helper.assertTrue(de.ipnats.hardwrought.survival.Crawling.desired(player, standing)
                        == net.minecraft.world.entity.Pose.SWIMMING,
                "A player who has chosen to crawl wants to be down on the ground");
        helper.assertTrue(de.ipnats.hardwrought.survival.Crawling.desired(player, net.minecraft.world.entity.Pose.SLEEPING)
                        == net.minecraft.world.entity.Pose.SLEEPING,
                "but sleeping still wins");
        ((de.ipnats.hardwrought.survival.Crawling.Crawler) player).hardwrought$setCrawling(false);
        helper.succeed();
    }

    @GameTest
    public void ultraTakesAwayNaturalHealingAndThePackAtTheStart(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var rules = server.overworld().getGameRules();
        boolean regenerationBefore = rules.get(net.minecraft.world.level.gamerules.GameRules.NATURAL_HEALTH_REGENERATION);
        var difficultyBefore = server.getWorldData().getDifficulty();
        try {
            rules.set(de.ipnats.hardwrought.survival.Ultra.RULE, true, server);
            helper.assertTrue(de.ipnats.hardwrought.survival.Ultra.active(), "The Ultra rule switches Ultra on");
            helper.assertFalse(rules.get(net.minecraft.world.level.gamerules.GameRules.NATURAL_HEALTH_REGENERATION),
                    "and health no longer comes back by itself");
            helper.assertTrue(server.getWorldData().getDifficulty() == net.minecraft.world.Difficulty.HARD
                            && server.getWorldData().isDifficultyLocked(),
                    "and the difficulty is Hard, locked");
            helper.assertTrue(de.ipnats.hardwrought.equipment.BackpackTier.STARTER.mainRows(
                            de.ipnats.hardwrought.survival.Ultra.active(server)) == 1,
                    "A woven pack opens one row in Ultra");
        } finally {
            rules.set(de.ipnats.hardwrought.survival.Ultra.RULE, false, server);
            rules.set(net.minecraft.world.level.gamerules.GameRules.NATURAL_HEALTH_REGENERATION, regenerationBefore, server);
            server.setDifficultyLocked(false);
            server.setDifficulty(difficultyBefore, true);
        }
        helper.assertFalse(de.ipnats.hardwrought.survival.Ultra.active(), "and switches off again");
        helper.succeed();
    }
}
