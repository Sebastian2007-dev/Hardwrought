package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.underworld.UnderworldAtmosphere;
import de.ipnats.hardwrought.underworld.UnderworldGate;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;

/**
 * The seal in the abyss: what it needs, and the ground opening into the shaft. Going down and coming
 * back up need the Underworld itself, which the test server does not have; see UnderworldClientGameTest.
 */
public final class UnderworldGameTests {
    @GameTest(maxTicks = 200)
    public void theSealOpensTheGroundInTheAbyss(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos high = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(high, UnderworldGate.SEAL_STONE.defaultBlockState(), 2);
        helper.assertTrue(UnderworldGate.wanting(level, high) != null, "Up here, out of the abyss, it does not take");
        level.setBlock(high, Blocks.AIR.defaultBlockState(), 2);

        // Down in the abyss, under the test, a floor and the stone on it.
        BlockPos stone = new BlockPos(high.getX(), UnderworldGate.ABYSS_BELOW - 30, high.getZ());
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                level.setBlock(stone.offset(dx, -1, dz), Blocks.DEEPSLATE.defaultBlockState(), 2);
                for (int dy = 0; dy <= 3; dy++) level.setBlock(stone.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        level.setBlock(stone, UnderworldGate.SEAL_STONE.defaultBlockState(), 2);
        helper.assertTrue(UnderworldGate.wanting(level, stone) != null, "Without its pillars it does not take");
        for (int dx : new int[] {-2, 2}) {
            for (int dz : new int[] {-2, 2}) {
                level.setBlock(stone.offset(dx, 0, dz), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
            }
        }
        helper.assertTrue(UnderworldGate.wanting(level, stone) != null, "nor without the soul lanterns on them");
        for (int dx : new int[] {-2, 2}) {
            for (int dz : new int[] {-2, 2}) {
                level.setBlock(stone.offset(dx, 1, dz), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
            }
        }
        helper.assertTrue(UnderworldGate.wanting(level, stone) == null, "Whole, in the abyss, it takes");
        UnderworldGate.open(level, stone);
        helper.succeedWhen(() -> {
            BlockPos bottom = stone.below(UnderworldGate.PIT_DEPTH);
            helper.assertTrue(level.getBlockState(stone).isAir() && level.getBlockState(stone.below()).isAir(), "The ground falls in where the stone was");
            helper.assertTrue(level.getBlockState(bottom).is(UnderworldGate.SHAFT), "the shaft at the bottom of the pit");
            helper.assertTrue(level.getBlockState(bottom.offset(1, 0, 1)).is(UnderworldGate.SHAFT), "three wide");
            helper.assertTrue(level.getBlockEntity(bottom) instanceof UnderworldGate.ShaftEntity, "with what the view down into it is drawn for");
            helper.assertTrue(level.getBlockState(stone.offset(2, -1, 0)).is(UnderworldGate.SEAL_CRACK), "walled with the cracked seal");
        });
    }

    @GameTest
    public void aPaleShardRekindlesAnAscentShaft(GameTestHelper helper) {
        var dim = UnderworldGate.ASCENT_SHAFT.defaultBlockState();
        helper.assertTrue(dim.getLightEmission() == 0, "An ascent shaft found in a ruin is dim");
        helper.assertTrue(dim.setValue(UnderworldGate.LIT, true).getLightEmission() > 0, "rekindled, it gives light");
        helper.succeed();
    }

    @GameTest(maxTicks = 120)
    public void aStalactiteBreaksLooseAndFalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos roof = helper.absolutePos(new BlockPos(1, 8, 1));
        level.setBlock(roof, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), 2);
        var down = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);
        level.setBlock(roof.below(), down.setValue(PointedDripstoneBlock.THICKNESS, SpeleothemThickness.BASE), 2);
        level.setBlock(roof.below(2), down.setValue(PointedDripstoneBlock.THICKNESS, SpeleothemThickness.TIP), 2);
        BlockPos root = UnderworldAtmosphere.findStalactite(level, helper.absolutePos(new BlockPos(1, 1, 1)), 0, RandomSource.create(1));
        helper.assertTrue(roof.below().equals(root), "The stalactite is found by its root: " + root);
        UnderworldAtmosphere.crack(level, root);
        helper.assertBlockPresent(Blocks.POINTED_DRIPSTONE, new BlockPos(1, 7, 1));
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(Blocks.POINTED_DRIPSTONE, new BlockPos(1, 7, 1));
            helper.assertBlockNotPresent(Blocks.POINTED_DRIPSTONE, new BlockPos(1, 6, 1));
        });
    }

    @GameTest
    public void aLightGoesOutBehindThePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(7, 1, 11));
        player.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0, 0);
        // Looking south (+z): torches all along the north, behind, and one ahead.
        for (int x = -6; x <= 6; x++) {
            for (int dz = 6; dz <= 10; dz++) level.setBlock(feet.offset(x, 0, -dz), Blocks.TORCH.defaultBlockState(), 2);
        }
        BlockPos ahead = feet.offset(0, 0, 8);
        level.setBlock(ahead, Blocks.TORCH.defaultBlockState(), 2);
        helper.assertTrue(UnderworldAtmosphere.lightOutBehind(level, player, RandomSource.create(3)), "A light behind goes out");
        int lit = 0;
        for (int x = -6; x <= 6; x++) {
            for (int dz = 6; dz <= 10; dz++) if (level.getBlockState(feet.offset(x, 0, -dz)).is(Blocks.TORCH)) lit++;
        }
        helper.assertTrue(lit == 13 * 5 - 1, "just the one: " + lit);
        helper.assertTrue(level.getBlockState(ahead).is(Blocks.TORCH), "and never one in front of them");
        helper.succeed();
    }

    @GameTest(maxTicks = 60)
    public void theShaftSwallowsTheWaterThatRunsIntoThePit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos shaft = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(shaft, UnderworldGate.SHAFT.defaultBlockState(), 3);
        // A narrow pit of glass over it, so the water has nowhere to go but down the shaft.
        for (int up = 1; up <= 4; up++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) level.setBlock(shaft.offset(dx, up, dz), Blocks.GLASS.defaultBlockState(), 3);
                }
            }
        }
        BlockPos above = shaft.above(2);
        de.ipnats.hardwrought.water.WaterStorage.setAmount(level, above, de.ipnats.hardwrought.water.WaterAmounts.BLOCK);
        helper.assertTrue(de.ipnats.hardwrought.water.WaterStorage.amount(level, above) > 0, "Water stands in the pit");
        helper.succeedWhen(() -> {
            for (int up = 1; up <= 4; up++) {
                helper.assertTrue(de.ipnats.hardwrought.water.WaterStorage.amount(level, shaft.above(up)) == 0, "and pours away down the shaft");
            }
            level.setBlock(shaft, Blocks.AIR.defaultBlockState(), 3);
        });
    }

    /** Every block of a box round a point that is of a kind, with what it holds. */
    private static java.util.List<BlockPos> find(ServerLevel level, BlockPos center, int radius, int up, net.minecraft.world.level.block.Block block) {
        java.util.List<BlockPos> found = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, up, radius))) {
            if (level.getBlockState(pos).is(block)) found.add(pos.immutable());
        }
        return found;
    }

    /**
     * What a container's loot table puts in it. Rolled straight from the table: the test runs by the world
     * spawn, where chests open empty on purpose (SpawnLoot), which never reaches the Underworld.
     */
    private static java.util.List<net.minecraft.world.item.ItemStack> open(ServerLevel level, BlockPos pos) {
        var container = (net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity) level.getBlockEntity(pos);
        var key = container.getLootTable();
        if (key == null) throw new AssertionError("No loot table to fill it from");
        return level.getServer().reloadableRegistries().getLootTable(key).getRandomItems(new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(pos))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST));
    }

    @GameTest
    public void aRuinHasItsColdFireplaceAndAChestOfWornThings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        de.ipnats.hardwrought.underworld.UnderworldRuins.Ruin.build(level, center, RandomSource.create(5));
        var fire = find(level, center, 3, 3, Blocks.CAMPFIRE);
        helper.assertTrue(fire.size() == 1 && !level.getBlockState(fire.getFirst()).getValue(net.minecraft.world.level.block.CampfireBlock.LIT),
                "A cold fireplace: " + fire);
        var chests = find(level, center, 3, 3, Blocks.CHEST);
        helper.assertTrue(chests.size() == 1, "A chest: " + chests);
        var loot = open(level, chests.getFirst());
        helper.assertTrue(!loot.isEmpty() && loot.size() <= 5, "with a little in it: " + loot);
        helper.succeed();
    }

    @GameTest
    public void aCampHasItsBedrollsAndBarrels(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        de.ipnats.hardwrought.underworld.UnderworldRuins.Camp.build(level, center, RandomSource.create(2));
        helper.assertTrue(find(level, center, 2, 1, Blocks.CAMPFIRE).size() == 1, "A burnt-out campfire");
        var barrels = find(level, center, 3, 1, Blocks.BARREL);
        helper.assertTrue(!barrels.isEmpty(), "Barrels");
        var stocked = barrels.stream().filter(pos -> ((net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity) level.getBlockEntity(pos))
                .getLootTable() != null).findFirst().orElseThrow();
        helper.assertTrue(!open(level, stocked).isEmpty(), "with stores left in them");
        helper.succeed();
    }

    @GameTest
    public void aWatchtowerLooksOutOverTheDrop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        de.ipnats.hardwrought.underworld.UnderworldRuins.Watchtower.build(level, center, Direction.EAST, RandomSource.create(9));
        helper.assertTrue(find(level, center, 1, 12, Blocks.LADDER).size() >= 5, "A ladder up the inside");
        var chests = find(level, center, 2, 14, Blocks.CHEST);
        helper.assertTrue(chests.size() == 1 && chests.getFirst().getY() > center.getY() + 7, "A chest at the top: " + chests);
        helper.assertTrue(!open(level, chests.getFirst()).isEmpty(), "with something left in it");
        helper.succeed();
    }

    @GameTest
    public void aJournalReadsInTheReadersOwnLanguage(GameTestHelper helper) {
        for (var journal : de.ipnats.hardwrought.underworld.Journals.ALL) {
            var book = de.ipnats.hardwrought.underworld.Journals.stack(journal);
            var content = book.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
            helper.assertTrue(content != null && content.pages().size() == journal.pages() && content.resolved(), journal.key() + " has its pages");
            helper.assertTrue(content.pages().getFirst().raw().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents,
                    journal.key() + " is translated");
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 140)
    public void aGraveWalkerComesUpOutOfTheGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 1; dy <= 3; dy++) level.setBlock(floor.offset(dx, -dy, dz), Blocks.SOUL_SOIL.defaultBlockState(), 2);
            }
        }
        var walker = de.ipnats.hardwrought.underworld.GraveUndead.GRAVE_WALKER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        walker.snapTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5, 0, 0);
        level.addFreshEntity(walker);
        walker.rise();
        helper.assertTrue(walker.getY() < floor.getY() - 1.5, "It starts in the ground: " + walker.getY());
        helper.runAfterDelay(de.ipnats.hardwrought.underworld.GraveUndead.SHIFTING - 2, () ->
                helper.assertTrue(walker.getY() < floor.getY() - 1.5, "and stays there while the soil shifts"));
        helper.succeedWhen(() -> {
            helper.assertTrue(!walker.rising(), "It has risen");
            helper.assertTrue(Math.abs(walker.getY() - floor.getY()) < 0.1, "and stands on the floor: " + walker.getY());
            walker.discard();
        });
    }

    @GameTest
    public void theGraveUndeadAreUndead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var walker = de.ipnats.hardwrought.underworld.GraveUndead.GRAVE_WALKER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        var skeleton = de.ipnats.hardwrought.underworld.GraveUndead.GRAVE_SKELETON.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        helper.assertTrue(walker.is(net.minecraft.tags.EntityTypeTags.UNDEAD) && skeleton.is(net.minecraft.tags.EntityTypeTags.UNDEAD),
                "Both are undead, for smite and healing");
        helper.assertTrue(walker.getMaxHealth() > 20 && walker.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) < 0.23,
                "The walker is tougher and slower than a zombie");
        helper.assertTrue(de.ipnats.hardwrought.health.Wounds.biter(walker), "and bites");
        skeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(helper.absolutePos(BlockPos.ZERO)), net.minecraft.world.entity.EntitySpawnReason.COMMAND, null);
        var weapon = skeleton.getMainHandItem();
        helper.assertTrue(weapon.is(net.minecraft.world.item.Items.BOW) || weapon.is(net.minecraft.world.item.Items.IRON_SWORD) && weapon.isDamaged(),
                "The skeleton has an old bow or a worn sword: " + weapon);
        helper.succeed();
    }

    @GameTest
    public void aGraveHasItsMoundAndHeadstone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos head = helper.absolutePos(new BlockPos(3, 1, 3));
        int stones = 0;
        for (int seed = 0; seed < 6; seed++) {
            BlockPos at = head.offset((seed % 3) * 0, 0, 0);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlock(at.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                    for (int dy = 0; dy <= 1; dy++) level.setBlock(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            de.ipnats.hardwrought.underworld.Graveyard.Graves.grave(level, at, RandomSource.create(seed));
            helper.assertTrue(!level.getBlockState(at.below()).is(Blocks.STONE), "The grave is dug: " + level.getBlockState(at.below()));
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 0, 2))) {
                var state = level.getBlockState(pos);
                if (state.is(net.minecraft.tags.BlockTags.WALLS) || state.is(Blocks.CRACKED_STONE_BRICKS) || state.is(Blocks.STONE_BRICK_SLAB)) stones++;
            }
        }
        helper.assertTrue(stones >= 5, "and nearly always a headstone, standing or fallen: " + stones + " of 6");
        helper.succeed();
    }
}
