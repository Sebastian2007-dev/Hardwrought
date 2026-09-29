package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.building.BuildingPhysics;
import de.ipnats.hardwrought.building.BuiltBlocks;
import de.ipnats.hardwrought.building.Statics;
import de.ipnats.hardwrought.building.StructuralMaterials;
import de.ipnats.hardwrought.core.registry.MaterialDefinition.StructuralProperties;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Milestone 17, section 39: built blocks need a path of support to the ground, reach sideways only
 * as far as their material allows, and fail under more load than they can bear — with cracks first.
 */
public final class BuildingGameTests {
    private static final StructuralProperties WOOD = new StructuralProperties(30, 40, 5);
    private static final StructuralProperties STONE = new StructuralProperties(100, 5, 2);
    private static final StructuralProperties SOIL = new StructuralProperties(0.3, 0, 1);
    private static final double SOIL_BLOCK_N = 1500 * 9.81;

    /** A world of its own for the arithmetic: ground everywhere below y = 0, nothing else. */
    private static final class Plan implements Statics.View {
        final Map<BlockPos, Statics.Cell> cells = new HashMap<>();

        Plan put(int x, int y, int z, StructuralProperties material, double weightN) {
            cells.put(new BlockPos(x, y, z), new Statics.Member(material, weightN));
            return this;
        }

        Plan column(int x, int z, int height, StructuralProperties material) {
            for (int y = 0; y < height; y++) put(x, y, z, material, 1000);
            return this;
        }

        @Override
        public Statics.Cell at(BlockPos pos) {
            if (pos.getY() < 0) return Statics.ANCHOR;
            return cells.getOrDefault(pos, Statics.EMPTY);
        }

        Map<BlockPos, Statics.Result> solve() {
            return Statics.solve(this, List.of(cells.keySet().iterator().next()), 10_000);
        }
    }

    @GameTest
    public void aSpanReachesAsFarAsItsMaterialAllows(GameTestHelper helper) {
        Plan wood = new Plan().column(0, 0, 3, WOOD);
        for (int x = 1; x <= 7; x++) wood.put(x, 2, 0, WOOD, 1000);
        var results = wood.solve();
        helper.assertTrue(results.get(new BlockPos(5, 2, 0)).supported(),
                "A wooden beam reaches five blocks out from its post");
        helper.assertFalse(results.get(new BlockPos(6, 2, 0)).supported(),
                "but not six: past its support distance it has nothing holding it");

        Plan stone = new Plan().column(0, 0, 3, STONE);
        for (int x = 1; x <= 3; x++) stone.put(x, 2, 0, STONE, 1000);
        results = stone.solve();
        helper.assertTrue(results.get(new BlockPos(2, 2, 0)).supported()
                        && !results.get(new BlockPos(3, 2, 0)).supported(),
                "Stone is strong in compression and poor in tension: it spans two blocks, not three");

        Plan bridge = new Plan().column(0, 0, 1, STONE).column(5, 0, 1, STONE);
        for (int x = 1; x <= 4; x++) bridge.put(x, 0, 0, STONE, 1000);
        helper.assertTrue(bridge.solve().values().stream().allMatch(Statics.Result::supported),
                "Held from both ends, the same stone bridges a gap of four");
        helper.succeed();
    }

    @GameTest
    public void anAnchorHoldsPastTheSpanButNotWithoutLimit(GameTestHelper helper) {
        double stoneN = 2600 * 9.81;
        Plan plan = new Plan().column(0, 0, 3, STONE);
        for (int x = 1; x <= 3; x++) plan.put(x, 2, 0, STONE, stoneN);
        plan.cells.put(new BlockPos(3, 2, 0), new Statics.Member(STONE, stoneN).withAnchor());
        var results = plan.solve();
        helper.assertTrue(results.get(new BlockPos(3, 2, 0)).held() == Statics.Held.ANCHORED
                        && results.get(new BlockPos(3, 2, 0)).utilisation() <= 1,
                "An anchored stone holds on three blocks out, where stone alone reaches two");

        Plan chain = new Plan().column(0, 0, 3, STONE);
        for (int x = 1; x <= 4; x++) chain.cells.put(new BlockPos(x, 2, 0), new Statics.Member(STONE, stoneN).withAnchor());
        var first = chain.solve().get(new BlockPos(1, 2, 0));
        helper.assertTrue(first.held() == Statics.Held.ANCHORED && first.utilisation() > 1,
                "but a chain of anchors hangs on the first one, and four stones are more than it holds");
        helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void anAnchoredBlockStaysWhereItWouldFall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(1, 0, 1)), Blocks.STONE.defaultBlockState());
        for (int y = 1; y <= 3; y++) build(level, helper.absolutePos(new BlockPos(1, y, 1)), stone);
        for (int x = 2; x <= 4; x++) build(level, helper.absolutePos(new BlockPos(x, 3, 1)), stone);
        // Three blocks out, one more than stone reaches: without the anchor it would fall in two seconds.
        BlockPos end = helper.absolutePos(new BlockPos(4, 3, 1));
        helper.assertTrue(BuildingPhysics.anchor(level, end), "A built block takes an anchor");
        helper.assertFalse(BuildingPhysics.anchor(level, end), "but only one");
        helper.assertFalse(BuildingPhysics.anchor(level, helper.absolutePos(new BlockPos(1, 0, 1))),
                "and terrain needs none");
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(level.getBlockState(end).is(Blocks.STONE_BRICKS) && BuiltBlocks.isAnchored(level, end),
                    "The anchored block is still there");
            level.setBlockAndUpdate(end, Blocks.AIR.defaultBlockState());
            helper.assertFalse(BuiltBlocks.isAnchored(level, end), "and the anchor goes with the block");
            helper.succeed();
        });
    }

    @GameTest
    public void onlyWhatHasTensileStrengthCanHang(GameTestHelper helper) {
        // A post with a beam out along x and a second beam out along z; below the ends, nothing beside.
        Plan plan = new Plan().column(0, 0, 4, WOOD);
        plan.put(1, 3, 0, WOOD, 1000).put(2, 3, 0, WOOD, 1000).put(1, 3, 1, WOOD, 1000);
        plan.put(2, 2, 0, WOOD, 1000).put(1, 2, 1, SOIL, SOIL_BLOCK_N);
        var results = plan.solve();
        helper.assertTrue(results.get(new BlockPos(2, 2, 0)).held() == Statics.Held.ABOVE,
                "Wood hangs below a beam");
        helper.assertFalse(results.get(new BlockPos(1, 2, 1)).supported(),
                "earth never hangs: it has no tensile strength to hang by");
        helper.succeed();
    }

    @GameTest
    public void aDirtPillarCannotCarryATower(GameTestHelper helper) {
        int safe = (int) Math.floor(SOIL.compressionStrengthMpa() * 1_000_000 / SOIL_BLOCK_N);
        Plan short_ = new Plan();
        for (int y = 0; y < safe; y++) short_.put(0, y, 0, SOIL, SOIL_BLOCK_N);
        helper.assertTrue(short_.solve().values().stream().allMatch(result -> result.utilisation() <= 1),
                "A dirt column of " + safe + " carries itself");

        Plan tall = new Plan();
        for (int y = 0; y <= safe; y++) tall.put(0, y, 0, SOIL, SOIL_BLOCK_N);
        var bottom = tall.solve().get(BlockPos.ZERO);
        helper.assertTrue(bottom.utilisation() > 1 && bottom.held() == Statics.Held.GROUND,
                "One block more and the lowest block is crushed by the weight above it");

        Plan stoneOnDirt = new Plan().put(0, 0, 0, SOIL, SOIL_BLOCK_N);
        for (int y = 1; y <= 12; y++) stoneOnDirt.put(0, y, 0, STONE, 2600 * 9.81);
        helper.assertTrue(stoneOnDirt.solve().get(BlockPos.ZERO).utilisation() > 1,
                "Section 39: massive building does not stand on a single dirt block");
        helper.succeed();
    }

    @GameTest
    public void materialsComeFromTheDatapack(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var planks = StructuralMaterials.of(server, Blocks.OAK_PLANKS.defaultBlockState()).orElseThrow();
        var stone = StructuralMaterials.of(server, Blocks.STONE_BRICKS.defaultBlockState()).orElseThrow();
        helper.assertTrue(planks.material().supportDistanceBlocks() > stone.material().supportDistanceBlocks(),
                "Wood spans farther than stone");
        helper.assertTrue(StructuralMaterials.of(server, Blocks.OAK_SLAB.defaultBlockState()).orElseThrow().weightN()
                        < planks.weightN(),
                "A slab weighs half a block: tag priority picks wood, the shape its weight");
        helper.assertTrue(StructuralMaterials.of(server, Blocks.TORCH.defaultBlockState()).isEmpty(),
                "A torch carries nothing");
        helper.assertTrue(StructuralMaterials.materialId(server, Blocks.CRAFTING_TABLE.defaultBlockState())
                        .orElseThrow().equals(Hardwrought.id("wood")),
                "A block named directly wins over any tag");
        helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void anOverreachingBeamCracksFallsAndStaysBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        // Natural ground to stand on and to catch what falls.
        for (int x = 1; x <= 5; x++) level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, 1)), Blocks.STONE.defaultBlockState());
        for (int y = 1; y <= 3; y++) build(level, helper.absolutePos(new BlockPos(1, y, 1)), stone);
        for (int x = 2; x <= 5; x++) build(level, helper.absolutePos(new BlockPos(x, 3, 1)), stone);
        BlockPos held = helper.absolutePos(new BlockPos(3, 3, 1));
        BlockPos overreach = helper.absolutePos(new BlockPos(5, 3, 1));

        helper.runAfterDelay(20, () -> helper.assertTrue(level.getBlockState(overreach).is(Blocks.STONE_BRICKS)
                        && BuiltBlocks.isBuilt(level, overreach),
                "An unsupported block does not vanish at once: it cracks first"));
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(level.getBlockState(held).is(Blocks.STONE_BRICKS),
                    "Stone two blocks out from its pillar holds");
            helper.assertTrue(level.getBlockState(overreach).isAir()
                            && level.getBlockState(helper.absolutePos(new BlockPos(4, 3, 1))).isAir(),
                    "three and four blocks out it has fallen");
            BlockPos landed = helper.absolutePos(new BlockPos(5, 1, 1));
            helper.assertTrue(level.getBlockState(landed).is(Blocks.STONE_BRICKS) && BuiltBlocks.isBuilt(level, landed),
                    "and where it landed it is still built, not new terrain to build on");
            helper.succeed();
        });
    }

    /**
     * A crushed block has something under it and cannot fall. It once did anyway: it landed in its own
     * place, whole and uncracked, was found overloaded again and cracked again, for ever.
     */
    @GameTest(maxTicks = 300)
    public void anOverloadedBlockIsCrushedNotRepeatedlyCracked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(2, 0, 2));
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        BlockPos dirt = ground.above();
        build(level, dirt, Blocks.DIRT.defaultBlockState());
        // Five blocks of gold weigh close to a hundred tonnes: far past what one block of earth bears.
        for (int y = 1; y <= 5; y++) build(level, dirt.above(y), Blocks.GOLD_BLOCK.defaultBlockState());
        helper.runAfterDelay(200, () -> {
            helper.assertFalse(level.getBlockState(dirt).is(Blocks.DIRT),
                    "The overloaded earth is crushed within seconds, not cracked over and over");
            helper.assertTrue(BuiltBlocks.isBuilt(level, dirt) == level.getBlockState(dirt).is(Blocks.GOLD_BLOCK),
                    "and the gold above comes down into its place, still built");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void miningTheGroundFromUnderAPillarBringsItDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(ground.below(), Blocks.AIR.defaultBlockState());
        BlockPos pillar = ground.above();
        build(level, pillar, Blocks.OAK_PLANKS.defaultBlockState());
        build(level, pillar.above(), Blocks.OAK_PLANKS.defaultBlockState());
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(level.getBlockState(pillar).is(Blocks.OAK_PLANKS), "Standing on rock it stands");
            level.setBlockAndUpdate(ground, Blocks.AIR.defaultBlockState());
        });
        helper.runAfterDelay(120, () -> {
            helper.assertFalse(level.getBlockState(pillar.above()).is(Blocks.OAK_PLANKS),
                    "With the rock under it mined away, nothing holds it up");
            helper.succeed();
        });
    }

    /**
     * Natural ground is not built, but a piece of it that has come away from the land is on its own:
     * a tree cut through at the foot does not hang in the air.
     */
    @GameTest(maxTicks = 200)
    public void aTreeCutAtTheFootComesDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(2, 2, 2));
        // A trunk with a branch, standing on nothing but its own foot.
        for (int y = 0; y < 4; y++) level.setBlockAndUpdate(foot.above(y), Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(foot.above(3).east(), Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(foot.above(4), Blocks.OAK_LEAVES.defaultBlockState());
        // Cut by a player: only a player's break is followed up.
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        helper.runAfterDelay(5, () -> player.gameMode.destroyBlock(foot));
        helper.runAfterDelay(20, () -> helper.assertTrue(level.getBlockState(foot.above(2)).is(Blocks.OAK_LOG),
                "It cracks before it falls"));
        helper.runAfterDelay(100, () -> {
            helper.assertFalse(level.getBlockState(foot.above(3)).is(Blocks.OAK_LOG)
                            || level.getBlockState(foot.above(3).east()).is(Blocks.OAK_LOG),
                    "Cut at the foot, the trunk and its branch come down");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void digingIntoTheLandBringsNothingDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // An overhang of more than a thousand blocks of rock: part of the land, however undercut.
        BlockPos corner = helper.absolutePos(new BlockPos(0, 2, 0));
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                for (int y = 0; y < 17; y++) level.setBlock(corner.offset(x, y, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        BlockPos dug = corner.offset(4, 0, 4);
        level.setBlockAndUpdate(dug, Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(level.getBlockState(dug.above()).is(Blocks.STONE),
                    "The rock over a hole dug into the land stays where it is");
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    for (int y = 0; y < 17; y++) level.setBlock(corner.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            helper.succeed();
        });
    }

    /** A shaft is timber: it carries a gearbox on its end like a beam, not like air. */
    @GameTest(maxTicks = 200)
    public void aShaftHoldsAGearbox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(1, 0, 1)), Blocks.STONE.defaultBlockState());
        for (int y = 1; y <= 2; y++) build(level, helper.absolutePos(new BlockPos(1, y, 1)), Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos shaft = helper.absolutePos(new BlockPos(2, 2, 1));
        BlockPos gearbox = helper.absolutePos(new BlockPos(3, 2, 1));
        build(level, shaft, de.ipnats.hardwrought.core.registry.ModBlocks.SHAFT.defaultBlockState()
                .setValue(de.ipnats.hardwrought.machinery.ShaftBlock.AXIS, net.minecraft.core.Direction.Axis.X));
        build(level, gearbox, de.ipnats.hardwrought.core.registry.ModBlocks.GEARBOX.defaultBlockState());
        helper.assertTrue(StructuralMaterials.of(level.getServer(), level.getBlockState(shaft)).isPresent(),
                "A shaft carries load: it is timber");
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(level.getBlockState(gearbox).is(de.ipnats.hardwrought.core.registry.ModBlocks.GEARBOX),
                    "The gearbox on the end of the shaft stays where it is");
            helper.succeed();
        });
    }

    /** A gearbox, a standing shaft and a gearbox on it: a column of timber, and it stands. */
    @GameTest(maxTicks = 200)
    public void aShaftStandsBetweenTwoGearboxes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(1, 0, 1)), Blocks.STONE.defaultBlockState());
        BlockPos lower = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos shaft = lower.above();
        BlockPos upper = shaft.above();
        build(level, lower, de.ipnats.hardwrought.core.registry.ModBlocks.GEARBOX.defaultBlockState());
        build(level, shaft, de.ipnats.hardwrought.core.registry.ModBlocks.SHAFT.defaultBlockState()
                .setValue(de.ipnats.hardwrought.machinery.ShaftBlock.AXIS, net.minecraft.core.Direction.Axis.Y));
        build(level, upper, de.ipnats.hardwrought.core.registry.ModBlocks.GEARBOX.defaultBlockState());
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(level.getBlockState(lower).is(de.ipnats.hardwrought.core.registry.ModBlocks.GEARBOX)
                            && level.getBlockState(shaft).is(de.ipnats.hardwrought.core.registry.ModBlocks.SHAFT)
                            && level.getBlockState(upper).is(de.ipnats.hardwrought.core.registry.ModBlocks.GEARBOX),
                    "Gearbox, shaft and gearbox all stand");
            helper.succeed();
        });
    }

    private static void build(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
        BuildingPhysics.placed(level, pos, state);
    }
}
