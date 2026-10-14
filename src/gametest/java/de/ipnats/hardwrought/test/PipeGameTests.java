package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.chemistry.StillBlockEntity;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.fluids.FluidBlocks;
import de.ipnats.hardwrought.fluids.FluidPipeTier;
import de.ipnats.hardwrought.fluids.FluidTankBlock;
import de.ipnats.hardwrought.fluids.MechanicalPumpBlock;
import de.ipnats.hardwrought.machinery.ConnectedPipeBlock;
import de.ipnats.hardwrought.machinery.PipeLink;
import de.ipnats.hardwrought.oil.DrillingRigBlockEntity;
import de.ipnats.hardwrought.oil.ModFluids;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Pipes set with a wrench, item pipes that draw, fluid pipes that run downhill by weight and uphill with a pump. */
public final class PipeGameTests {
    private static final long MB = FluidConstants.BUCKET / 1000;
    /**
     * A batch of its own for tests that fill basins with world water: run beside the water-flow tests,
     * which sum the whole world's flow, a basin of a hundred blocks upsets their count.
     */
    private static final String STANDING_WATER = "hardwrought-test:standing_water";

    /** Strikes a side of the pipe at pos with a wrench, as a player would, at the face of its core. */
    private static void wrench(GameTestHelper helper, BlockPos relative, Direction side) {
        @SuppressWarnings("removal") ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ItemStack wrench = new ItemStack(FluidBlocks.PIPE_WRENCH);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        BlockPos pos = helper.absolutePos(relative);
        Vec3 face = Vec3.atCenterOf(pos).add(side.getStepX() * 0.24, side.getStepY() * 0.24, side.getStepZ() * 0.24);
        helper.getLevel().getBlockState(pos).useItemOn(wrench, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(face, side, pos, false));
    }

    private static PipeLink link(GameTestHelper helper, BlockPos relative, Direction side) {
        return ConnectedPipeBlock.link(helper.getBlockState(relative), side);
    }

    @GameTest
    public void aWrenchSetsEachSideOfAPipe(GameTestHelper helper) {
        BlockPos chest = new BlockPos(1, 2, 1), pipe = chest.east(), next = pipe.east();
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(pipe, ModBlocks.ITEM_PIPE_IRON);
        helper.setBlock(next, ModBlocks.ITEM_PIPE_IRON);
        helper.assertTrue(link(helper, pipe, Direction.WEST) == PipeLink.CONNECTED, "Set beside a chest, a pipe fills it");
        wrench(helper, pipe, Direction.WEST);
        helper.assertTrue(link(helper, pipe, Direction.WEST) == PipeLink.EXTRACT, "Struck once, it draws from it");
        wrench(helper, pipe, Direction.WEST);
        helper.assertTrue(link(helper, pipe, Direction.WEST) == PipeLink.NONE, "Struck again, it is cut off");
        wrench(helper, pipe, Direction.WEST);
        helper.assertTrue(link(helper, pipe, Direction.WEST) == PipeLink.CONNECTED, "and once more it fills it again");

        helper.assertTrue(link(helper, pipe, Direction.EAST) == PipeLink.CONNECTED, "Two pipes join");
        wrench(helper, pipe, Direction.EAST);
        helper.assertTrue(link(helper, pipe, Direction.EAST) == PipeLink.NONE && link(helper, next, Direction.WEST) == PipeLink.NONE,
                "Cut apart, they are apart from both ends");
        wrench(helper, pipe, Direction.EAST);
        helper.assertTrue(link(helper, pipe, Direction.EAST) == PipeLink.CONNECTED && link(helper, next, Direction.WEST) == PipeLink.CONNECTED,
                "and joined again from both");
        helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void anItemPipeDrawsFromAChestAndFillsAnother(GameTestHelper helper) {
        BlockPos from = new BlockPos(1, 2, 1), to = from.east(4);
        helper.setBlock(from, Blocks.CHEST);
        helper.setBlock(to, Blocks.CHEST);
        for (int i = 1; i <= 3; i++) helper.setBlock(from.east(i), ModBlocks.ITEM_PIPE_TITANIUM);
        ((ChestBlockEntity) helper.getBlockEntity(from, ChestBlockEntity.class)).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        wrench(helper, from.east(), Direction.WEST);
        helper.succeedWhen(() -> {
            ChestBlockEntity target = helper.getBlockEntity(to, ChestBlockEntity.class);
            helper.assertTrue(target.getItem(0).getCount() >= 32, "The far chest fills from the near one: " + target.getItem(0));
            helper.assertTrue(helper.getBlockEntity(from, ChestBlockEntity.class).getItem(0).getCount() <= 32,
                    "and nothing goes back into the chest it draws from");
        });
    }

    private static FluidTankBlock.Entity tank(GameTestHelper helper, BlockPos relative) {
        return helper.getBlockEntity(relative, FluidTankBlock.Entity.class);
    }

    private static void fill(FluidTankBlock.Entity tank, long millibuckets) {
        try (Transaction transaction = Transaction.openOuter()) {
            tank.storage.insert(FluidVariant.of(ModFluids.CRUDE_OIL), millibuckets * MB, transaction);
            transaction.commit();
        }
    }

    @GameTest(maxTicks = 200)
    public void fluidPipesLiftNothingWithoutAPump(GameTestHelper helper) {
        // tank — pipe — pipe — pump — pipe, then up a pipe into a tank a block higher than the first,
        // the first pipe drawing from the first tank. Uphill, only a pump moves anything.
        BlockPos source = new BlockPos(1, 2, 1), target = source.east(5).above(), pump = source.east(3);
        helper.setBlock(source, FluidBlocks.TANK);
        helper.setBlock(target, FluidBlocks.TANK);
        helper.setBlock(source.east(1), FluidBlocks.PIPES.get(FluidPipeTier.IRON));
        helper.setBlock(source.east(2), FluidBlocks.PIPES.get(FluidPipeTier.IRON));
        helper.setBlock(pump, FluidBlocks.MECHANICAL_PUMP);
        helper.setBlock(source.east(4), FluidBlocks.PIPES.get(FluidPipeTier.IRON));
        helper.setBlock(source.east(4).above(), FluidBlocks.PIPES.get(FluidPipeTier.IRON));
        fill(tank(helper, source), 4000);
        wrench(helper, source.east(1), Direction.WEST);
        helper.assertTrue(link(helper, source.east(1), Direction.WEST) == PipeLink.EXTRACT, "The first pipe draws from the tank");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(tank(helper, target).storage.amount == 0, "With the pump standing, nothing moves");
            MechanicalPumpBlock.Entity driven = helper.getBlockEntity(pump, MechanicalPumpBlock.Entity.class);
            driven.setKineticSpeed(16);
            helper.runAfterDelay(40, () -> {
                long moved = tank(helper, target).storage.amount / MB;
                // 250 mB a second at 16 turns: about 500 mB in two seconds.
                helper.assertTrue(moved >= 400 && moved <= 600, "Turned, the pump drives the run at its pace: " + moved + " mB");
                helper.assertTrue(tank(helper, source).storage.amount + tank(helper, target).storage.amount == 4000 * MB,
                        "and nothing is lost on the way");
                helper.succeed();
            });
        });
    }

    @GameTest(maxTicks = 400)
    public void aTankEmptiesDownhillWithoutAPump(GameTestHelper helper) {
        // A tank on a pillar, a pipe under it drawing from it, and along the ground a lower tank.
        BlockPos source = new BlockPos(1, 3, 1), pipe = source.below(), target = pipe.east(3);
        helper.setBlock(source, FluidBlocks.TANK);
        helper.setBlock(target, FluidBlocks.TANK);
        for (int i = 0; i < 3; i++) helper.setBlock(pipe.east(i), FluidBlocks.PIPES.get(FluidPipeTier.IRON));
        fill(tank(helper, source), 8000);
        wrench(helper, pipe, Direction.UP);
        helper.assertTrue(link(helper, pipe, Direction.UP) == PipeLink.EXTRACT, "The pipe draws from the tank above it");
        helper.runAfterDelay(40, () -> {
            long moved = tank(helper, target).storage.amount / MB;
            // A drop of one and a half blocks: about 150 mB a second, so some 300 in two seconds.
            helper.assertTrue(moved >= 200 && moved <= 400, "With no pump, the drop drives the oil down: " + moved + " mB");
            helper.assertTrue(tank(helper, source).storage.amount + tank(helper, target).storage.amount == 8000 * MB,
                    "and nothing is lost on the way");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 400)
    public void tanksOnALevelEvenOutThroughAPipe(GameTestHelper helper) {
        // Two tanks on one level but apart, joined by a pipe drawing from the first: joined vessels.
        BlockPos source = new BlockPos(1, 2, 1), target = source.east(3);
        helper.setBlock(source, FluidBlocks.TANK);
        helper.setBlock(target, FluidBlocks.TANK);
        helper.setBlock(source.east(1), FluidBlocks.PIPES.get(FluidPipeTier.TITANIUM));
        helper.setBlock(source.east(2), FluidBlocks.PIPES.get(FluidPipeTier.TITANIUM));
        fill(tank(helper, source), 16_000);
        wrench(helper, source.east(1), Direction.WEST);
        helper.succeedWhen(() -> {
            long left = tank(helper, source).storage.amount / MB, right = tank(helper, target).storage.amount / MB;
            helper.assertTrue(left + right == 16_000, "Nothing is lost: " + left + " + " + right);
            helper.assertTrue(right >= 1000 && left > right,
                    "The second fills, and never rises above the first: " + left + " / " + right);
        });
    }

    /**
     * A stone basin of water, inner corner at (1, 1, 1), with a mechanical pump set into its east wall at
     * the top layer and a pipe up from the pump into a tank. Returns the pump.
     */
    private static BlockPos basin(GameTestHelper helper, int width, int depth) {
        int top = depth;
        for (int x = 0; x <= width + 1; x++) {
            for (int z = 0; z <= width + 1; z++) {
                for (int y = 0; y <= top; y++) {
                    boolean inside = x >= 1 && x <= width && z >= 1 && z <= width && y >= 1;
                    helper.setBlock(new BlockPos(x, y, z), inside ? Blocks.WATER : Blocks.STONE);
                }
            }
        }
        BlockPos pump = new BlockPos(width + 1, top, 1 + width / 2);
        helper.setBlock(pump, FluidBlocks.MECHANICAL_PUMP);
        helper.setBlock(pump.above(), FluidBlocks.PIPES.get(FluidPipeTier.IRON));
        helper.setBlock(pump.above(2), FluidBlocks.TANK);
        helper.getBlockEntity(pump, MechanicalPumpBlock.Entity.class).setKineticSpeed(16);
        return pump;
    }

    /** Takes the basin's water out of the world's water table too, so no other test finds it. */
    private static void drain(GameTestHelper helper, int width, int depth) {
        for (int x = 0; x <= width + 1; x++) {
            for (int z = 0; z <= width + 1; z++) {
                for (int y = 0; y <= depth; y++) {
                    BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
                    de.ipnats.hardwrought.water.WaterStorage.setAmount(helper.getLevel(), at, 0);
                    helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static long water(GameTestHelper helper, int width, int depth) {
        long total = 0;
        for (int x = 1; x <= width; x++) {
            for (int z = 1; z <= width; z++) {
                for (int y = 1; y <= depth; y++) {
                    total += de.ipnats.hardwrought.water.WaterStorage.amount(helper.getLevel(), helper.absolutePos(new BlockPos(x, y, z)));
                }
            }
        }
        return total;
    }

    @GameTest(maxTicks = 200, environment = STANDING_WATER)
    public void aPumpInALakeNeverEmptiesIt(GameTestHelper helper) {
        // Eight by eight, two deep: 128 blocks of water, enough to count as endless. Kept shallow, since
        // water deeper than two blocks is pressed, and that disturbs the water-flow tests running beside.
        BlockPos pump = basin(helper, 8, 2);
        long before = water(helper, 8, 2);
        helper.runAfterDelay(40, () -> {
            long pumped = tank(helper, pump.above(2)).storage.amount / MB;
            // 250 mB a second at sixteen turns: about 500 in two seconds.
            helper.assertTrue(pumped >= 400 && pumped <= 600, "The pump draws water at its pace: " + pumped + " mB");
            long after = water(helper, 8, 2);
            drain(helper, 8, 2);
            helper.assertTrue(after == before, "and the lake is none the less for it: " + after);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200, environment = STANDING_WATER)
    public void aPumpInAPondDrawsItDown(GameTestHelper helper) {
        // Three by three, one deep: nine blocks, a pond a pump can empty.
        BlockPos pump = basin(helper, 3, 1);
        long before = water(helper, 3, 1);
        helper.runAfterDelay(40, () -> {
            long pumped = tank(helper, pump.above(2)).storage.amount / MB;
            long after = water(helper, 3, 1);
            drain(helper, 3, 1);
            helper.assertTrue(pumped >= 400 && pumped <= 600, "The pump draws water at its pace: " + pumped + " mB");
            helper.assertTrue(after == before - pumped, "and what is in the tank is missing from the pond: " + after + " of " + before);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 700)
    public void aSaltPanOverAFireLeavesSaltFromSeawater(GameTestHelper helper) {
        BlockPos fire = new BlockPos(1, 1, 1), pan = fire.above();
        helper.setBlock(fire, Blocks.CAMPFIRE);
        helper.setBlock(pan, de.ipnats.hardwrought.chemistry.SaltPan.BLOCK);
        var entity = helper.getBlockEntity(pan, de.ipnats.hardwrought.chemistry.SaltPanBlock.Entity.class);
        try (Transaction transaction = Transaction.openOuter()) {
            helper.assertTrue(entity.brine.insert(FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER),
                    1000 * MB, transaction) == 0, "Fresh water is turned away: it leaves nothing");
            helper.assertTrue(entity.brine.insert(de.ipnats.hardwrought.chemistry.SaltPan.water(
                    de.ipnats.hardwrought.water.WaterQuality.SALT), 1000 * MB, transaction) == 1000 * MB, "Seawater goes in");
            transaction.commit();
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(entity.saltCount() == de.ipnats.hardwrought.chemistry.SaltPanBlock.Entity.SALT_PER_BUCKET,
                    "A bucket boiled off over the fire leaves its salt: " + entity.saltCount() + ", " + entity.brineMillibuckets() + " mB left");
            helper.assertTrue(entity.brineMillibuckets() == 0, "and no water");
            helper.assertTrue(helper.getBlockState(pan).getValue(de.ipnats.hardwrought.chemistry.SaltPanBlock.CONTENTS)
                    == de.ipnats.hardwrought.chemistry.SaltPanBlock.Contents.SALT, "The pan shows a crust of salt");
        });
    }

    @GameTest(maxTicks = 300)
    public void aRunDrawsOilFromAWellAndALake(GameTestHelper helper) {
        // rig — pipe — pump — pipe — tank, and a lake of oil beside the first pipe.
        BlockPos rig = new BlockPos(1, 2, 2), target = rig.east(4), lake = rig.east(1).north();
        helper.setBlock(rig, ModBlocks.DRILLING_RIG);
        helper.setBlock(rig.east(1), FluidBlocks.PIPES.get(FluidPipeTier.TITANIUM));
        helper.setBlock(rig.east(2), FluidBlocks.MECHANICAL_PUMP);
        helper.setBlock(rig.east(3), FluidBlocks.PIPES.get(FluidPipeTier.TITANIUM));
        helper.setBlock(target, FluidBlocks.TANK);
        helper.setBlock(lake, ModFluids.CRUDE_OIL.defaultFluidState().createLegacyBlock());
        helper.getBlockEntity(rig, DrillingRigBlockEntity.class).setOilForTesting(600);
        helper.getBlockEntity(rig.east(2), MechanicalPumpBlock.Entity.class).setKineticSpeed(64);
        wrench(helper, rig.east(1), Direction.WEST);
        wrench(helper, rig.east(1), Direction.NORTH);
        helper.assertTrue(link(helper, rig.east(1), Direction.NORTH) == PipeLink.EXTRACT, "The pipe draws from the lake too");
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockEntity(rig, DrillingRigBlockEntity.class).oil() < 1, "The well is drawn dry");
            helper.assertTrue(helper.getBlockState(lake).isAir(), "and the lake beside the pipe is taken up, a bucket");
            long oil = tank(helper, target).storage.amount / MB;
            helper.assertTrue(oil >= 1599, "and all of it is in the tank: " + oil + " mB");
        });
    }

    @GameTest(maxTicks = 300)
    public void aStillTakesCrudeOilFromAPipe(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 2, 1), still = source.east(4);
        helper.setBlock(source, FluidBlocks.TANK);
        helper.setBlock(source.east(1), FluidBlocks.PIPES.get(FluidPipeTier.TITANIUM));
        helper.setBlock(source.east(2), FluidBlocks.MECHANICAL_PUMP);
        helper.setBlock(source.east(3), FluidBlocks.PIPES.get(FluidPipeTier.TITANIUM));
        helper.setBlock(still, ModBlocks.STILL);
        fill(tank(helper, source), 3000);
        helper.getBlockEntity(source.east(2), MechanicalPumpBlock.Entity.class).setKineticSpeed(64);
        wrench(helper, source.east(1), Direction.WEST);
        helper.succeedWhen(() -> {
            long oil = helper.getBlockEntity(still, StillBlockEntity.class).oilInlet().iterator().next().getAmount() / MB;
            helper.assertTrue(oil == 1000, "The still takes a bucket of oil, and no more: " + oil);
            helper.assertTrue(tank(helper, source).storage.amount / MB == 2000, "and the rest stays in the tank");
        });
    }

    @GameTest(maxTicks = 200)
    public void tanksThatTouchAreOneTank(GameTestHelper helper) {
        // Two tanks side by side, a third on top of the first.
        BlockPos left = new BlockPos(1, 2, 1), right = left.east(), top = left.above();
        helper.setBlock(left, FluidBlocks.TANK);
        helper.setBlock(right, FluidBlocks.TANK);
        helper.setBlock(top, FluidBlocks.TANK);
        helper.assertTrue(FluidTankBlock.joined(helper.getBlockState(left), Direction.EAST)
                && FluidTankBlock.joined(helper.getBlockState(left), Direction.UP), "The tanks join");
        // Twenty buckets poured in at the top tank.
        var group = FluidTankBlock.storage(helper.getLevel(), helper.absolutePos(top));
        try (Transaction transaction = Transaction.openOuter()) {
            long taken = group.insert(FluidVariant.of(ModFluids.CRUDE_OIL), 20_000 * MB, transaction);
            transaction.commit();
            helper.assertTrue(taken == 20_000 * MB, "Three tanks take twenty buckets though one holds sixteen");
        }
        // A bucket of something else is no part of it.
        try (Transaction transaction = Transaction.openOuter()) {
            helper.assertTrue(group.insert(FluidVariant.of(net.minecraft.world.level.material.Fluids.LAVA), 1000 * MB, transaction) == 0,
                    "and they hold one liquid only");
        }
        helper.succeedWhen(() -> {
            long l = tank(helper, left).storage.amount / MB, r = tank(helper, right).storage.amount / MB;
            long t = tank(helper, top).storage.amount / MB;
            helper.assertTrue(t == 0, "The liquid settles out of the top tank: " + t);
            helper.assertTrue(Math.abs(l - r) <= 1 && l + r + t == 20_000, "and stands level in the two below: " + l + " / " + r);
        });
    }
}
