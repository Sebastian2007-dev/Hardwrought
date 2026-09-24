package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.machinery.Driveline;
import de.ipnats.hardwrought.machinery.HandCrankBlock;
import de.ipnats.hardwrought.machinery.StarterCrusherBlockEntity;
import de.ipnats.hardwrought.metallurgy.Crushing;
import de.ipnats.hardwrought.metallurgy.Metal;
import de.ipnats.hardwrought.metallurgy.ModMetals;
import de.ipnats.hardwrought.metallurgy.OrePowders;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.progression.BenchTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import de.ipnats.hardwrought.machinery.ShaftBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Milestone-72 groundwork: a line of shafts turns when something at one of its ends does.
 *
 * <p>Nothing here looks at the drawing, which is the part that has to be judged by eye. What it does
 * hold down is the half that can be wrong silently: which shafts a crank reaches, which it does not,
 * and that it stops reaching at all after a bounded distance.
 */
public final class MachineryGameTests {
    private static final int WORKSPACE = 30;

    @GameTest
    public void aCrankTurnsTheLineItIsAttachedTo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        try {
            // A crank, then four shafts running east away from it.
            level.setBlockAndUpdate(base, ModBlocks.CRANK_BOX.defaultBlockState());
            for (int step = 1; step <= 4; step++) {
                level.setBlockAndUpdate(base.east(step), ModBlocks.SHAFT.defaultBlockState()
                        .setValue(ShaftBlock.AXIS, Direction.Axis.X));
            }

            for (int step = 1; step <= 4; step++) {
                helper.assertFalse(driven(level, base.east(step)),
                        "A crank that is not being turned turns nothing: shaft " + step);
            }

            turn(level, base, true);
            for (int step = 1; step <= 4; step++) {
                helper.assertTrue(driven(level, base.east(step)),
                        "Motion carries along the whole line, not just the first one: shaft " + step);
            }

            turn(level, base, false);
            for (int step = 1; step <= 4; step++) {
                helper.assertFalse(driven(level, base.east(step)),
                        "and stopping the crank stops all of it: shaft " + step);
            }
        } finally {
            clear(level, base, 6);
        }
        helper.succeed();
    }

    @GameTest
    public void aShaftAcrossTheLineIsNotOnIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        try {
            level.setBlockAndUpdate(base, ModBlocks.CRANK_BOX.defaultBlockState());
            level.setBlockAndUpdate(base.east(1), ModBlocks.SHAFT.defaultBlockState()
                    .setValue(ShaftBlock.AXIS, Direction.Axis.X));
            // Touching the line, but lying across it. A shaft has two ends and they are both on its
            // own axis; a bar at right angles is a bar that happens to be next to it.
            level.setBlockAndUpdate(base.east(2), ModBlocks.SHAFT.defaultBlockState()
                    .setValue(ShaftBlock.AXIS, Direction.Axis.Z));
            level.setBlockAndUpdate(base.east(3), ModBlocks.SHAFT.defaultBlockState()
                    .setValue(ShaftBlock.AXIS, Direction.Axis.X));

            turn(level, base, true);
            helper.assertTrue(driven(level, base.east(1)), "The shaft on the crank's own axis turns");
            helper.assertFalse(driven(level, base.east(2)),
                    "the one lying across it does not, however close it is");
            helper.assertFalse(driven(level, base.east(3)),
                    "and nothing past the break gets any motion either");
        } finally {
            clear(level, base, 5);
        }
        helper.succeed();
    }

    @GameTest
    public void aLineStopsCarryingAfterAWhile(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        int length = Driveline.MAX_LENGTH + 8;
        try {
            level.setBlockAndUpdate(base, ModBlocks.CRANK_BOX.defaultBlockState());
            for (int step = 1; step <= length; step++) {
                level.setBlock(base.east(step), ModBlocks.SHAFT.defaultBlockState()
                        .setValue(ShaftBlock.AXIS, Direction.Axis.X), 2);
            }
            turn(level, base, true);

            helper.assertTrue(driven(level, base.east(1)),
                    "A long line still turns where the crank is");
            helper.assertFalse(driven(level, base.east(length)),
                    "but a shaft built across a continent stops turning rather than costing a walk "
                            + "of the continent every time anything changes");
        } finally {
            clear(level, base, length + 2);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 60)
    public void aHandCrankTurnsOnlyWhileItIsBeingCranked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        // Mounted against the first of three shafts running east.
        level.setBlockAndUpdate(base, ModBlocks.HAND_CRANK.defaultBlockState()
                .setValue(HandCrankBlock.FACING, Direction.EAST));
        for (int step = 1; step <= 3; step++) {
            level.setBlockAndUpdate(base.east(step), ModBlocks.SHAFT.defaultBlockState()
                    .setValue(ShaftBlock.AXIS, Direction.Axis.X));
        }
        helper.assertFalse(driven(level, base.east(3)), "A crank nobody is holding turns nothing");

        // Held: the use repeats every four ticks, and the line keeps turning through all of them.
        HandCrankBlock.crank(level, base);
        helper.assertTrue(driven(level, base.east(3)), "One stroke sets the whole line going");
        helper.runAfterDelay(4, () -> HandCrankBlock.crank(level, base));
        helper.runAfterDelay(8, () -> HandCrankBlock.crank(level, base));
        helper.runAfterDelay(12, () -> {
            HandCrankBlock.crank(level, base);
            helper.assertTrue(driven(level, base.east(3)),
                    "and it keeps going for as long as the strokes keep coming");
        });
        // Let go: the last stroke's window runs out and the line stops with it.
        helper.runAfterDelay(12 + HandCrankBlock.HOLD_TICKS + 3, () -> {
            try {
                helper.assertFalse(level.getBlockState(base).getValue(HandCrankBlock.TURNING),
                        "A crank that is let go stops");
                for (int step = 1; step <= 3; step++) {
                    helper.assertFalse(driven(level, base.east(step)),
                            "and so does everything it was turning: shaft " + step);
                }
            } finally {
                clear(level, base, 4);
            }
            helper.succeed();
        });
    }

    @GameTest
    public void aHandCrankDrivesOnlyTheFaceItIsMountedOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos crank = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE).east(3);
        try {
            level.setBlockAndUpdate(crank, ModBlocks.HAND_CRANK.defaultBlockState()
                    .setValue(HandCrankBlock.FACING, Direction.EAST));
            level.setBlockAndUpdate(crank.east(), ModBlocks.SHAFT.defaultBlockState()
                    .setValue(ShaftBlock.AXIS, Direction.Axis.X));
            // On the crank's own axis, but behind it: where the hand is, not where the axle goes.
            level.setBlockAndUpdate(crank.west(), ModBlocks.SHAFT.defaultBlockState()
                    .setValue(ShaftBlock.AXIS, Direction.Axis.X));

            HandCrankBlock.crank(level, crank);
            helper.assertTrue(driven(level, crank.east()), "The shaft the axle goes into turns");
            helper.assertFalse(driven(level, crank.west()),
                    "the one behind the handle does not, unlike next to a crank box");
        } finally {
            clear(level, crank.west(), 3);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 400)
    public void aCrusherBreaksOreOnlyWhileTheLineTurns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        // Crank box, two shafts, crusher: power has to come along the line to reach it.
        level.setBlockAndUpdate(base, ModBlocks.CRANK_BOX.defaultBlockState());
        for (int step = 1; step <= 2; step++) {
            level.setBlockAndUpdate(base.east(step), ModBlocks.SHAFT.defaultBlockState()
                    .setValue(ShaftBlock.AXIS, Direction.Axis.X));
        }
        BlockPos jaws = base.east(3);
        level.setBlockAndUpdate(jaws, ModBlocks.STARTER_CRUSHER.defaultBlockState());
        StarterCrusherBlockEntity crusher = (StarterCrusherBlockEntity) level.getBlockEntity(jaws);
        ItemStack ore = new ItemStack(Items.RAW_IRON, 2);
        helper.assertTrue(crusher.insertFrom(ore, true), "Raw ore goes into the crusher");
        helper.assertTrue(ore.isEmpty(), "all of it, since it fits");
        helper.assertFalse(crusher.insertFrom(new ItemStack(Items.DIAMOND), true),
                "and something that is not raw ore does not");

        int crush = StarterCrusherBlockEntity.CRUSH_TICKS;
        helper.runAfterDelay(crush + 5, () -> {
            helper.assertTrue(crusher.getItem(StarterCrusherBlockEntity.OUTPUT).isEmpty(),
                    "A crusher nothing turns crushes nothing");
            turn(level, base, true);
        });
        // The crank box turns twice as fast as the jaws are built for, so they crush twice as fast.
        int fast = (int) (crush * StarterCrusherBlockEntity.RATED_SPEED / CrankBoxBlock.SPEED);
        helper.runAfterDelay(crush + 5 + fast + 10, () -> {
            ItemStack powder = crusher.getItem(StarterCrusherBlockEntity.OUTPUT);
            helper.assertTrue(powder.is(OrePowders.powder(OrePowders.VanillaOre.IRON)),
                    "Turned along the line, it breaks raw iron to iron powder");
            helper.assertTrue(powder.getCount() == 1, "one chunk per crushing time, not all at once");
            turn(level, base, false);
        });
        helper.runAfterDelay(4 * crush, () -> {
            try {
                helper.assertTrue(crusher.getItem(StarterCrusherBlockEntity.OUTPUT).getCount() == 1,
                        "and it stops when the crank does, with the second chunk still waiting");
                helper.assertTrue(crusher.getItem(StarterCrusherBlockEntity.INPUT).getCount() == 1,
                        "the second chunk is still in the jaws");
            } finally {
                clear(level, base, 4);
            }
            helper.succeed();
        });
    }

    @GameTest
    public void aCrusherTakesPowerFromACrankOrAShaftPointingAtIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos jaws = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE).east(2);
        try {
            level.setBlockAndUpdate(jaws, ModBlocks.STARTER_CRUSHER.defaultBlockState());
            helper.assertFalse(Driveline.isDrivenInto(level, jaws), "Nothing next to it, no power");

            // A crank box right against it drives it without any shaft in between.
            level.setBlockAndUpdate(jaws.west(), ModBlocks.CRANK_BOX.defaultBlockState()
                    .setValue(CrankBoxBlock.TURNING, true));
            helper.assertTrue(Driveline.isDrivenInto(level, jaws), "A turning crank box beside it drives it");
            level.setBlock(jaws.west(), Blocks.AIR.defaultBlockState(), 2);

            // A hand crank only when it is mounted facing the crusher.
            level.setBlockAndUpdate(jaws.east(), ModBlocks.HAND_CRANK.defaultBlockState()
                    .setValue(HandCrankBlock.FACING, Direction.WEST).setValue(HandCrankBlock.TURNING, true));
            helper.assertTrue(Driveline.isDrivenInto(level, jaws), "A hand crank mounted on it drives it");
            level.setBlockAndUpdate(jaws.east(), ModBlocks.HAND_CRANK.defaultBlockState()
                    .setValue(HandCrankBlock.FACING, Direction.EAST).setValue(HandCrankBlock.TURNING, true));
            helper.assertFalse(Driveline.isDrivenInto(level, jaws),
                    "one turned the other way drives whatever is on its far side, not the crusher");
            level.setBlock(jaws.east(), Blocks.AIR.defaultBlockState(), 2);

            // A turning shaft lying across it touches it but turns nothing in it.
            level.setBlockAndUpdate(jaws.north(), ModBlocks.SHAFT.defaultBlockState()
                    .setValue(ShaftBlock.AXIS, Direction.Axis.X));
            level.setBlockAndUpdate(jaws.north().west(), ModBlocks.CRANK_BOX.defaultBlockState()
                    .setValue(CrankBoxBlock.TURNING, true));
            helper.assertTrue(driven(level, jaws.north()), "The shaft beside it turns");
            helper.assertFalse(Driveline.isDrivenInto(level, jaws), "but, lying across it, does not drive it");
            level.setBlockAndUpdate(jaws.north().west(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(jaws.north(), ModBlocks.SHAFT.defaultBlockState()
                    .setValue(ShaftBlock.AXIS, Direction.Axis.Z));
            level.setBlockAndUpdate(jaws.north(2), ModBlocks.CRANK_BOX.defaultBlockState()
                    .setValue(CrankBoxBlock.TURNING, true));
            helper.assertTrue(Driveline.isDrivenInto(level, jaws), "one whose end points into it does");
        } finally {
            level.setBlock(jaws.north(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(jaws.north(2), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(jaws.north().west(), Blocks.AIR.defaultBlockState(), 2);
            clear(level, jaws.west(), 2);
        }
        helper.succeed();
    }

    @GameTest
    public void aCrusherFilesIngotsBackToPowderButLeavesGemsAlone(GameTestHelper helper) {
        helper.assertTrue(Crushing.result(new ItemStack(Items.COPPER_INGOT))
                        == OrePowders.powder(OrePowders.VanillaOre.COPPER),
                "A copper ingot is crushed back to copper powder, the way into bronze");
        helper.assertTrue(Crushing.result(new ItemStack(ModMetals.ingot(Metal.TIN)))
                        == OrePowders.powder(Metal.TIN),
                "and so is a tin ingot, the other half of it");
        helper.assertTrue(Crushing.result(new ItemStack(ModMetals.raw(Metal.TIN)))
                        == OrePowders.powder(Metal.TIN),
                "Raw ore still crushes as before");
        helper.assertTrue(Crushing.result(new ItemStack(Items.DIAMOND)) == null,
                "A diamond is not ground to dust");
        helper.succeed();
    }

    @GameTest
    public void theDrivelineIsMadeAtTheHewnBench(GameTestHelper helper) {
        var recipes = helper.getLevel().recipeAccess();
        for (String id : new String[] { "shaft", "hand_crank", "starter_crusher" }) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE,
                            Identifier.parse("hardwrought:" + id))).isPresent(),
                    "There is a recipe for the " + id);
        }
        // Wood, stone and flint only, so none of it waits for metal.
        for (Item item : new Item[] { ModItems.SHAFT, ModItems.HAND_CRANK, ModItems.STARTER_CRUSHER }) {
            helper.assertTrue(BenchTier.allows(BenchTier.HEWN, new ItemStack(item)),
                    "The hewn bench can make " + item);
        }
        helper.assertFalse(BenchTier.allows(BenchTier.HEWN, new ItemStack(ModItems.CRANK_BOX)),
                "The crank box is the creative source and is not made at all");
        helper.succeed();
    }

    private static boolean driven(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ShaftBlock && state.getValue(ShaftBlock.DRIVEN);
    }

    private static void turn(ServerLevel level, BlockPos crank, boolean on) {
        level.setBlockAndUpdate(crank, ModBlocks.CRANK_BOX.defaultBlockState()
                .setValue(CrankBoxBlock.TURNING, on));
        Driveline.update(level, crank);
    }

    private static void clear(ServerLevel level, BlockPos base, int length) {
        for (int step = 0; step <= length; step++) {
            level.setBlock(base.east(step), Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
