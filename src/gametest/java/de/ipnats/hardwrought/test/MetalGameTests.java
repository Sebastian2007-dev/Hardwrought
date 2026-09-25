package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.geology.DrillTier;
import de.ipnats.hardwrought.geology.DrillYield;
import de.ipnats.hardwrought.geology.Geology;
import de.ipnats.hardwrought.geology.RockProfile;
import de.ipnats.hardwrought.geology.RockType;
import de.ipnats.hardwrought.metallurgy.Metal;
import de.ipnats.hardwrought.metallurgy.MetalWorldgen;
import de.ipnats.hardwrought.metallurgy.ModMetals;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;

/**
 * Milestone 7, sections 55 and 56: the metals the specification names, each with an ore in the
 * ground, a raw material, an ingot and a place in the geology.
 */
public final class MetalGameTests {
    @GameTest
    public void everyMetalIsARealOreAndARealIngot(GameTestHelper helper) {
        helper.assertTrue(Metal.values().length == 15,
                "Every metal the specification names is in the table");
        for (Metal metal : Metal.values()) {
            helper.assertTrue(ModMetals.ore(metal) != null && ModMetals.deepslateOre(metal) != null,
                    metal.id() + " has an ore in stone and in deepslate");
            helper.assertTrue(!new ItemStack(ModMetals.raw(metal)).isEmpty()
                            && !new ItemStack(ModMetals.ingot(metal)).isEmpty(),
                    metal.id() + " has a raw material and an ingot");
            helper.assertTrue(ModMetals.ore(metal).asItem() != net.minecraft.world.item.Items.AIR,
                    metal.id() + " ore can be carried and placed");
            helper.assertTrue(metal.maxY() > metal.minY(),
                    metal.id() + " sits in a band that is the right way up");
        }
        helper.assertTrue(Metal.byId("tungsten") == Metal.TUNGSTEN && Metal.byId("mithril") == null,
                "Metals are found by name, and only the ones that exist");

        // A block item that does not take its name from its block asks for a translation key that
        // nobody writes, and shows up in the inventory as "item.hardwrought.deepslate_cobalt_ore".
        for (Metal metal : Metal.values()) {
            for (var block : new net.minecraft.world.level.block.Block[]{
                    ModMetals.ore(metal), ModMetals.deepslateOre(metal)}) {
                helper.assertTrue(block.asItem().getDescriptionId().equals(block.getDescriptionId()),
                        block.getDescriptionId() + " is named after its block, not after itself");
                helper.assertTrue(block.getDescriptionId().startsWith("block.hardwrought."),
                        "and under this mod's own block prefix");
            }
        }
        helper.succeed();
    }

    @GameTest
    public void oreHardnessAndTagsFollowTheTable(GameTestHelper helper) {
        for (Metal metal : Metal.values()) {
            var state = ModMetals.ore(metal).defaultBlockState();
            helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE),
                    metal.id() + " ore is mined with a pickaxe");
            helper.assertTrue(state.is(BlockTags.ORES),
                    metal.id() + " ore counts as ore, so the grade of Milestone 6 pays out on it");
            helper.assertTrue(state.requiresCorrectToolForDrops(),
                    metal.id() + " ore gives nothing to the wrong tool");
        }
        // The three hardness bands are really different: tin gives way to stone, tungsten does not.
        var stonePick = new ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE);
        var ironPick = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        var diamondPick = new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
        helper.assertTrue(stonePick.isCorrectToolForDrops(ModMetals.ore(Metal.TIN).defaultBlockState()),
                "Tin is a bronze-age metal and a stone pick is enough for it");
        helper.assertFalse(stonePick.isCorrectToolForDrops(ModMetals.ore(Metal.NICKEL).defaultBlockState()),
                "Nickel is not");
        helper.assertFalse(ironPick.isCorrectToolForDrops(ModMetals.ore(Metal.TUNGSTEN).defaultBlockState()),
                "and tungsten resists iron, as the hardest metal in the table should");
        helper.assertTrue(diamondPick.isCorrectToolForDrops(ModMetals.ore(Metal.TUNGSTEN).defaultBlockState()),
                "until the tool is hard enough");
        helper.succeed();
    }

    @GameTest
    public void everyOreGeneratesAndNothingVanillaWasTakenAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var features = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        for (ResourceKey<PlacedFeature> ore : MetalWorldgen.oreFeatures()) {
            helper.assertTrue(features.get(ore).isPresent(),
                    "Every metal has a loaded ore feature: " + ore.identifier());
        }

        List<Identifier> ores = oreFeaturesOf(level.getBiome(helper.absolutePos(BlockPos.ZERO)).value());
        helper.assertTrue(ores.contains(Hardwrought.id("ore_tin"))
                        && ores.contains(Hardwrought.id("ore_platinum")),
                "An overworld biome generates the new ores, shallow and deep, has " + ores);
        helper.assertTrue(ores.contains(Identifier.withDefaultNamespace("ore_iron_middle"))
                        || ores.contains(Identifier.withDefaultNamespace("ore_coal_upper")),
                "and vanilla's own veins are still exactly where they were");
        helper.succeed();
    }

    @GameTest
    public void metalsHaveASmeltingPathThatFitsTheirAge(GameTestHelper helper) {
        for (Metal metal : Metal.values()) {
            helper.assertTrue(hasRecipe(helper, "hardwrought:" + metal.ingotId() + "_from_smelting"),
                    metal.id() + " smelts in a furnace");
            boolean firePit = hasRecipe(helper, "hardwrought:" + metal.ingotId() + "_from_campfire");
            helper.assertTrue(firePit == metal.smeltsInAFirePit(),
                    metal.id() + " belongs in a fire pit only if the early game may reach it");
        }
        helper.assertTrue(Metal.TIN.smeltsInAFirePit(),
                "Tin is a fire-pit metal, or there is no bronze before the workbench");
        helper.assertFalse(Metal.PLATINUM.smeltsInAFirePit() || Metal.TUNGSTEN.smeltsInAFirePit(),
                "while the hard ones wait for a real furnace");
        helper.succeed();
    }

    @GameTest
    public void theGeologyKnowsTheNewMetals(GameTestHelper helper) {
        var profiles = CoreLifecycle.require(helper.getLevel().getServer()).rockProfiles();
        for (Metal metal : Metal.values()) {
            RockType rock = RockType.byName(metal.rock().serializedName());
            helper.assertTrue(rock != null, metal.id() + " names a rock that exists");
            RockProfile profile = profiles.get(rock.id());
            helper.assertTrue(profile.deposits().stream().anyMatch(deposit ->
                            deposit.ore().equals(Hardwrought.id(metal.oreId()))),
                    metal.id() + " is carried by the rock its table names, so it can be prospected");
            helper.assertTrue(profile.deposits().stream()
                            .filter(deposit -> deposit.ore().equals(Hardwrought.id(metal.oreId())))
                            .allMatch(deposit -> deposit.drillTier() == metal.drillTier()),
                    metal.id() + " needs the same drill in the rock table as in its own");
        }

        // An iron drill on sedimentary rock reaches tin somewhere, and nothing that needs a better frame.
        int x = 0;
        while (Geology.rockAt(4242L, x, 0) != RockType.SEDIMENTARY) x += Geology.REGION_SIZE_BLOCKS;
        boolean tin = false;
        for (int chunk = 0; chunk < 12; chunk++) {
            DrillYield iron = DrillYield.forChunk(4242L, profiles, x + chunk * 16, 0, DrillTier.IRON);
            List<Identifier> reachable = iron.entries().stream().map(DrillYield.Entry::ore).toList();
            tin |= reachable.contains(Hardwrought.id("tin_ore"));
            helper.assertFalse(reachable.contains(Hardwrought.id("aluminum_ore")),
                    "An iron drill does not reach the metals that need a better machine");
        }
        helper.assertTrue(tin, "An iron drill brings up tin");
        helper.succeed();
    }

    private static List<Identifier> oreFeaturesOf(Biome biome) {
        var steps = biome.getGenerationSettings().features();
        int step = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
        if (step >= steps.size()) return List.of();
        return steps.get(step).stream()
                .map(holder -> holder.unwrapKey().map(ResourceKey::identifier).orElse(null))
                .filter(id -> id != null)
                .toList();
    }

    @GameTest
    public void everyOreMeltsAtItsOwnPointAndTheFurnacesAreALadder(GameTestHelper helper) {
        var materials = CoreLifecycle.require(helper.getLevel().getServer()).materials();
        for (Metal metal : Metal.values()) {
            helper.assertTrue(de.ipnats.hardwrought.metallurgy.Smelting.meltingPoint(
                            ModMetals.raw(metal), materials).isPresent(),
                    "Raw " + metal.id() + " has a melting point, read from its material");
            helper.assertTrue(de.ipnats.hardwrought.metallurgy.Smelting.meltingPoint(
                            de.ipnats.hardwrought.metallurgy.OrePowders.powder(metal), materials).isPresent(),
                    "and so does its powder, without being listed anywhere");
        }
        double iron = de.ipnats.hardwrought.metallurgy.Smelting.meltingPoint(
                net.minecraft.world.item.Items.RAW_IRON, materials).orElseThrow();
        double titanium = de.ipnats.hardwrought.metallurgy.Smelting.meltingPoint(
                ModMetals.raw(Metal.TITANIUM), materials).orElseThrow();
        double tungsten = de.ipnats.hardwrought.metallurgy.Smelting.meltingPoint(
                ModMetals.raw(Metal.TUNGSTEN), materials).orElseThrow();
        helper.assertTrue(iron <= de.ipnats.hardwrought.metallurgy.Smelting.BRICK_FURNACE_MAX_C
                        && titanium > de.ipnats.hardwrought.metallurgy.Smelting.BRICK_FURNACE_MAX_C,
                "A brick furnace gets as far as iron and no further");
        helper.assertTrue(titanium <= de.ipnats.hardwrought.metallurgy.Smelting.FURNACE_MAX_C
                        && tungsten > de.ipnats.hardwrought.metallurgy.Smelting.FURNACE_MAX_C,
                "a stone furnace reaches titanium, and tungsten needs a blast furnace");
        helper.assertTrue(de.ipnats.hardwrought.progression.BenchTier.required(
                        new ItemStack(net.minecraft.world.item.Items.FURNACE))
                        == de.ipnats.hardwrought.progression.BenchTier.JOINED,
                "A stone furnace is made at the nailed bench, not before");
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void aFurnaceTooColdForItsLoadLeavesItAndBurnsNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos brick = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos stone = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos warm = helper.absolutePos(new BlockPos(5, 1, 1));
        level.setBlockAndUpdate(brick, de.ipnats.hardwrought.core.registry.ModBlocks.BRICK_FURNACE.defaultBlockState());
        level.setBlockAndUpdate(stone, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
        level.setBlockAndUpdate(warm, de.ipnats.hardwrought.core.registry.ModBlocks.BRICK_FURNACE.defaultBlockState());
        var tooCold = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) level.getBlockEntity(brick);
        var hotEnough = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) level.getBlockEntity(stone);
        var ironBrick = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) level.getBlockEntity(warm);
        for (var furnace : List.of(tooCold, hotEnough)) {
            furnace.setItem(0, new ItemStack(ModMetals.raw(Metal.TUNGSTEN)));
            furnace.setItem(1, new ItemStack(net.minecraft.world.item.Items.COAL, 4));
        }
        ironBrick.setItem(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        ironBrick.setItem(1, new ItemStack(net.minecraft.world.item.Items.COAL, 4));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(tooCold.getItem(1).getCount() == 4,
                    "A brick furnace does not burn fuel on tungsten it cannot even bring to working heat");
            helper.assertTrue(hotEnough.getItem(1).getCount() == 3,
                    "a stone furnace lights for the same load");
            helper.assertTrue(ironBrick.getItem(1).getCount() == 3,
                    "and the brick furnace lights for iron, which it can melt");
            for (BlockPos pos : List.of(brick, stone, warm)) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            helper.succeed();
        });
    }

    private static boolean hasRecipe(GameTestHelper helper, String id) {
        return helper.getLevel().recipeAccess()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).isPresent();
    }
}
