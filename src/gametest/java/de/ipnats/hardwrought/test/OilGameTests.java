package de.ipnats.hardwrought.test;

import com.mojang.serialization.JsonOps;
import de.ipnats.hardwrought.chemistry.Purity;
import de.ipnats.hardwrought.chemistry.StillBlockEntity;
import de.ipnats.hardwrought.chemistry.StillRecipes;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import de.ipnats.hardwrought.environment.Gas;
import de.ipnats.hardwrought.environment.Gases;
import de.ipnats.hardwrought.geology.Geology;
import de.ipnats.hardwrought.geology.Reservoir;
import de.ipnats.hardwrought.geology.Reservoirs;
import de.ipnats.hardwrought.geology.RockType;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.oil.DrillingRigBlockEntity;
import de.ipnats.hardwrought.oil.Oilfield;
import de.ipnats.hardwrought.water.WaterQuality;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/** Milestone 11: oil and gas in the rock, the rig that reaches them, and the still that parts them. */
public final class OilGameTests {
    private static final int WORKSPACE = 24;

    @GameTest
    public void oilAndGasLieOnlyUnderSedimentaryRockAndNotUnderAllOfIt(GameTestHelper helper) {
        long seed = helper.getLevel().getSeed();
        int sedimentary = 0;
        int withReservoir = 0;
        int oil = 0;
        for (int rx = -30; rx <= 30; rx++) {
            for (int rz = -30; rz <= 30; rz++) {
                int x = rx * Geology.REGION_SIZE_BLOCKS + Geology.REGION_SIZE_BLOCKS / 2;
                int z = rz * Geology.REGION_SIZE_BLOCKS + Geology.REGION_SIZE_BLOCKS / 2;
                boolean sediment = Geology.rockAt(seed, x, z) == RockType.SEDIMENTARY;
                Reservoir reservoir = Reservoirs.inRegion(seed, rx, rz);
                if (sediment) sedimentary++;
                if (reservoir == null) continue;
                helper.assertTrue(sediment, "A reservoir lies only under sedimentary rock");
                withReservoir++;
                if (reservoir.kind() == Reservoir.Kind.OIL) oil++;
                helper.assertTrue(Geology.regionX(reservoir.centre().getX() - reservoir.horizontalRadius()) == rx
                                && Geology.regionX(reservoir.centre().getX() + reservoir.horizontalRadius()) == rx
                                && Geology.regionZ(reservoir.centre().getZ() - reservoir.horizontalRadius()) == rz
                                && Geology.regionZ(reservoir.centre().getZ() + reservoir.horizontalRadius()) == rz,
                        "and stays inside its own region");
                helper.assertTrue(reservoir.equals(Reservoirs.inRegion(seed, rx, rz)),
                        "The same region always has the same reservoir");
                helper.assertTrue(Reservoirs.under(seed, reservoir.centre().getX(), reservoir.centre().getZ())
                        .equals(reservoir), "and a well over its middle finds it");
            }
        }
        double share = withReservoir / (double) sedimentary;
        helper.assertTrue(share > 0.4 && share < 0.9,
                "Most sedimentary regions hold oil or gas, not all of them: " + share);
        helper.assertTrue(oil > 0 && oil < withReservoir, "There are oil reservoirs and gas fields both");
        helper.succeed();
    }

    @GameTest
    public void aReservoirGivesLessAsItEmpties(GameTestHelper helper) {
        Reservoir reservoir = new Reservoir(Reservoir.Kind.OIL, 0, 0, new BlockPos(96, -20, 96), 30, 8, 1.0);
        helper.assertTrue(reservoir.topAt(96, 96) == -12, "Its top is highest over its middle");
        helper.assertTrue(reservoir.topAt(96 + 29, 96) < -12 && reservoir.topAt(96 + 31, 96) == Integer.MIN_VALUE,
                "and lower towards its edge, with nothing beyond it");
        long capacity = reservoir.capacity();
        double fresh = Oilfield.perTick(reservoir, 0, Oilfield.RATED_SPEED);
        double half = Oilfield.perTick(reservoir, capacity / 2, Oilfield.RATED_SPEED);
        helper.assertTrue(Math.abs(fresh * 20 - Oilfield.OIL_PER_SECOND) < 1e-9,
                "A fresh reservoir at full pressure gives its rate at a rig's rated speed");
        helper.assertTrue(half < fresh && half > 0, "Half empty, it gives less but still gives");
        helper.assertTrue(Oilfield.perTick(reservoir, capacity, Oilfield.RATED_SPEED) == 0, "Empty, it gives nothing");
        helper.assertTrue(Oilfield.perTick(reservoir, 0, Oilfield.RATED_SPEED * 2) == fresh * 2,
                "Turned twice as fast, a well pumps twice as much");
        helper.assertTrue(Oilfield.borePerTick(Oilfield.RATED_SPEED) * 20 == Oilfield.BORE_PER_SECOND,
                "A rig bores half a block a second at its rated speed");
        helper.assertTrue(DrillingRigBlockEntity.depthTo(null, new BlockPos(0, 64, 0), -64) == 127,
                "Over nothing it bores to the bottom of the world");
        helper.succeed();
    }

    @GameTest(maxTicks = 400)
    public void aRigBoresIntoOilAndPumpsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        BlockPos rigPos = base.above();
        List<BlockPos> used = new ArrayList<>();
        place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
        place(level, used, rigPos, ModBlocks.DRILLING_RIG.defaultBlockState());
        DrillingRigBlockEntity rig = (DrillingRigBlockEntity) level.getBlockEntity(rigPos);
        Reservoir reservoir = new Reservoir(Reservoir.Kind.OIL, 9_000, 9_000,
                rigPos.below(40), 30, 8, 1.0);
        rig.overrideReservoirForTesting(reservoir);
        var save = CoreLifecycle.require(level.getServer()).saveData();
        long before = save.reservoirDrawn(reservoir.key());
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(rig.stage() == DrillingRigBlockEntity.Stage.BORING && rig.bored() > 0,
                    "Turned, the rig bores: " + rig.bored());
            helper.assertTrue(rig.depth() == 31, "down to the top of the reservoir under it: " + rig.depth());
            rig.setBoredForTesting(rig.depth() - 0.01);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(rig.stage() == DrillingRigBlockEntity.Stage.WELL, "Breaking into it, it becomes a well");
            helper.assertTrue(rig.oil() > 0 && rig.gas() > 0, "that brings up oil, and the gas of its cap with it");
        });
        helper.runAfterDelay(260, () -> {
            try {
                ItemStack bucket = rig.drawOil();
                helper.assertTrue(bucket.is(ModItems.CRUDE_OIL_BUCKET), "A bucketful comes off it as crude oil");
                helper.assertTrue(save.reservoirDrawn(reservoir.key()) > before,
                        "and what it has taken is gone from the reservoir for good");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest
    public void gasWithNowhereToGoEscapesAtTheRigAndAPipeTakesItAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rigPos = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            place(level, used, rigPos, ModBlocks.DRILLING_RIG.defaultBlockState());
            DrillingRigBlockEntity rig = (DrillingRigBlockEntity) level.getBlockEntity(rigPos);
            rig.setGasForTesting(DrillingRigBlockEntity.GAS_STORE + 5);
            helper.assertTrue(rig.release(level, rigPos) == 5, "What the rig cannot hold, it lets go");
            helper.assertTrue(Gases.units(level.getBlockState(rigPos.above()), Gas.METHANE) == 5,
                    "With no pipe on it, the gas escapes right where it stands");
            level.setBlock(rigPos.above(), Blocks.AIR.defaultBlockState(), 2);

            place(level, used, rigPos.above(), ModBlocks.GAS_PIPE.defaultBlockState());
            level.setBlockAndUpdate(rigPos.above(), net.minecraft.world.level.block.Block.updateFromNeighbourShapes(
                    level.getBlockState(rigPos.above()), level, rigPos.above()));
            rig.setGasForTesting(DrillingRigBlockEntity.GAS_STORE + 5);
            helper.assertTrue(rig.release(level, rigPos) == 5, "With a pipe on it, it lets the gas go as well");
            helper.assertTrue(Gases.units(level.getBlockState(rigPos.above(2)), Gas.METHANE) == 5,
                    "but out at the pipe's open end");
            used.add(rigPos.above(2));
            helper.assertTrue(rig.drawGas().isEmpty() == (rig.gas() < DrillingRigBlockEntity.CANISTER_UNITS),
                    "A canister is filled only where there is a canister's worth");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 500)
    public void aStillPartsCrudeOilOverAFire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        place(level, used, base, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        place(level, used, base.above(), ModBlocks.STILL.defaultBlockState());
        place(level, used, base.east(2).above(), ModBlocks.STILL.defaultBlockState());
        StillBlockEntity hot = (StillBlockEntity) level.getBlockEntity(base.above());
        StillBlockEntity cold = (StillBlockEntity) level.getBlockEntity(base.east(2).above());
        for (StillBlockEntity still : List.of(hot, cold)) {
            still.setItem(StillBlockEntity.CHARGE, new ItemStack(ModItems.CRUDE_OIL_BUCKET));
            still.setItem(StillBlockEntity.BOTTLES, new ItemStack(Items.GLASS_BOTTLE, 3));
        }
        helper.runAfterDelay(StillRecipes.BATCH_TICKS + 20, () -> {
            try {
                helper.assertTrue(cold.progress() == 0 && cold.getItem(StillBlockEntity.CHARGE).is(ModItems.CRUDE_OIL_BUCKET),
                        "A still with no fire under it does nothing");
                List<ItemStack> products = new ArrayList<>();
                for (int i = 0; i < StillBlockEntity.OUTPUTS; i++) products.add(hot.getItem(StillBlockEntity.FIRST_OUTPUT + i));
                for (var fraction : List.of(ModItems.LIGHT_FRACTION, ModItems.FUEL_FRACTION, ModItems.HEAVY_OIL, ModItems.BITUMEN)) {
                    helper.assertTrue(products.stream().anyMatch(stack -> stack.is(fraction)),
                            "Over a fire, crude oil comes apart into " + fraction);
                }
                helper.assertTrue(hot.getItem(StillBlockEntity.CHARGE).isEmpty()
                                && hot.getItem(StillBlockEntity.BOTTLES).isEmpty()
                                && hot.getItem(StillBlockEntity.RETURNED).is(Items.BUCKET),
                        "using up the charge and three bottles, and giving the bucket back");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest
    public void rawMaterialsHaveAPurityThatRefiningRaises(GameTestHelper helper) {
        var random = helper.getLevel().getRandom();
        ItemStack sea = new ItemStack(Items.WATER_BUCKET);
        sea.set(ModDataComponents.WATER_QUALITY, WaterQuality.SALT);
        helper.assertTrue(StillRecipes.accepts(sea) && !StillRecipes.accepts(new ItemStack(Items.WATER_BUCKET)),
                "The still boils down seawater, not fresh water");
        ItemStack salt = StillRecipes.batch(sea, random).products().getFirst();
        helper.assertTrue(salt.is(ModItems.SALT) && Purity.of(salt) >= 0.85f && Purity.of(salt) <= 0.95f,
                "Seawater gives salt of a middling purity: " + Purity.of(salt));

        ItemStack raw = Purity.with(new ItemStack(ModItems.RAW_SULFUR), 0.7);
        ItemStack refined = StillRecipes.batch(raw, random).products().getFirst();
        helper.assertTrue(refined.is(ModItems.SULFUR) && Purity.of(refined) > 0.97f,
                "Raw sulfur at seventy percent comes out refined at over ninety-seven: " + Purity.of(refined));
        helper.assertTrue(Purity.refined(0.5) > 0.95 && Purity.refined(1.0) == 1.0, "Refining never makes it worse");
        helper.succeed();
    }

    @GameTest
    public void whatAReservoirHasGivenIsSaved(GameTestHelper helper) {
        var data = CoreSaveData.TYPE.constructor().get();
        data.drawFromReservoir("3,-7", 12_345);
        data.drawFromReservoir("3,-7", 5);
        var encoded = CoreSaveData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var decoded = CoreSaveData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(decoded.reservoirDrawn("3,-7") == 12_350, "What was drawn survives a restart");
        helper.assertTrue(decoded.reservoirDrawn("0,0") == 0, "and a reservoir nobody touched is still full");
        helper.succeed();
    }

    private static void place(ServerLevel level, List<BlockPos> used, BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state) {
        level.setBlockAndUpdate(pos, state);
        used.add(pos.immutable());
    }

    private static void clear(ServerLevel level, List<BlockPos> used) {
        for (int i = used.size() - 1; i >= 0; i--) level.setBlock(used.get(i), Blocks.AIR.defaultBlockState(), 2);
    }
}
