package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasLineTier;
import de.ipnats.hardwrought.gasline.GasLines;
import de.ipnats.hardwrought.gasline.GasValveBlock;
import de.ipnats.hardwrought.machinery.KineticHolder;
import de.ipnats.hardwrought.machinery.ShaftBlock;
import de.ipnats.hardwrought.steam.BoilerBlock;
import de.ipnats.hardwrought.steam.FireboxBlock;
import de.ipnats.hardwrought.steam.SafetyValveBlock;
import de.ipnats.hardwrought.steam.SteamBlocks;
import de.ipnats.hardwrought.steam.SteamEngineBlock;
import de.ipnats.hardwrought.steam.SteamWhistleBlock;
import de.ipnats.hardwrought.water.WaterQuality;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Steam: the firebox and the boiler, the valves, the engine, and what goes wrong with them. */
public final class SteamGameTests {
    private static BoilerBlock.Entity boiler(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, SteamBlocks.BOILER);
        return helper.getBlockEntity(pos, BoilerBlock.Entity.class);
    }

    /** Units of steam that make so many bar in a boiler's drum. */
    private static double units(double bar) {
        return bar * BoilerBlock.Entity.STEAM_CAPACITY / BoilerBlock.Entity.FULL_BAR;
    }

    @GameTest(maxTicks = 200)
    public void aLitFireboxRaisesSteamInTheBoilerOnIt(GameTestHelper helper) {
        BlockPos box = new BlockPos(2, 1, 2), drum = box.above();
        helper.setBlock(box, SteamBlocks.FIREBOX);
        BoilerBlock.Entity boiler = boiler(helper, drum);
        FireboxBlock.Entity fire = helper.getBlockEntity(box, FireboxBlock.Entity.class);
        fire.setFuelForTesting(new ItemStack(Items.COAL, 8));
        boiler.water.fill(4000, WaterQuality.PURIFIED);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(fire.watts() == 0, "Laid but not lit, it gives nothing");
            fire.light();
            helper.assertTrue(helper.getBlockState(box).getValue(FireboxBlock.LIT), "Lit, it burns");
            // A cold boiler takes minutes to come up; start this one hot.
            boiler.setForTesting(170, 0, 0);
        });
        double[] early = new double[1];
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(boiler.pressure() > 3, "Hot water shut in the drum flashes to pressure: " + boiler.pressure());
            helper.assertTrue(boiler.pressure() < 8, "only as much as its heat is worth: " + boiler.pressure());
            early[0] = boiler.pressure();
        });
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(boiler.pressure() > early[0], "and the fire raises it further: " + boiler.pressure());
            helper.assertTrue(boiler.water.millibuckets() < 4000, "made of the water in it: " + boiler.water.millibuckets());
            helper.assertTrue(boiler.scale() == 0, "purified water leaves no scale");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void aSafetyValveLetsOffWhatIsAboveItsSetting(GameTestHelper helper) {
        BlockPos drum = new BlockPos(2, 1, 2), top = drum.above();
        BoilerBlock.Entity boiler = boiler(helper, drum);
        helper.setBlock(top, SteamBlocks.SAFETY_VALVE.defaultBlockState().setValue(SafetyValveBlock.SETTING, 0));
        boiler.steam.produce(GasKind.STEAM, units(14));
        helper.runAfterDelay(2, () -> helper.assertTrue(helper.getBlockState(top).getValue(SafetyValveBlock.LIFTING), "Above 4 bar it lifts"));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(boiler.pressure() <= 4 && boiler.pressure() >= 4 - SafetyValveBlock.BLOWDOWN - 1e-6,
                    "and lets the drum down to a little under its setting: " + boiler.pressure());
            helper.assertTrue(!helper.getBlockState(top).getValue(SafetyValveBlock.LIFTING), "where it reseats");
            helper.assertTrue(helper.getBlockState(drum).is(SteamBlocks.BOILER), "which stands");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 600)
    public void aBoilerWithoutASafetyValveBurstsAboveItsLimit(GameTestHelper helper) {
        BlockPos drum = new BlockPos(3, 2, 3);
        BoilerBlock.Entity boiler = boiler(helper, drum);
        boiler.steam.produce(GasKind.STEAM, units(BoilerBlock.Entity.BURST_BAR + 2));
        helper.succeedWhen(() -> helper.assertBlockNotPresent(SteamBlocks.BOILER, drum));
    }

    @GameTest(maxTicks = 100)
    public void waterOnRedHotIronFlashesAndBurstsTheBoiler(GameTestHelper helper) {
        BlockPos drum = new BlockPos(3, 2, 3);
        BoilerBlock.Entity boiler = boiler(helper, drum);
        boiler.setForTesting(BoilerBlock.Entity.FLASH + 100, 0, 0);
        helper.runAfterDelay(5, () -> {
            helper.assertBlockPresent(SteamBlocks.BOILER, drum);
            boiler.water.fill(1000, WaterQuality.PURIFIED);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertBlockNotPresent(SteamBlocks.BOILER, drum);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void seawaterLeavesScaleThatAPickaxeChipsOffACooledBoiler(GameTestHelper helper) {
        BlockPos drum = new BlockPos(2, 1, 2);
        BoilerBlock.Entity boiler = boiler(helper, drum);
        boiler.water.fill(4000, WaterQuality.SALT);
        // Hot water with no pressure on it boils off at once.
        boiler.setForTesting(180, 0, 0);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(boiler.scale() > 0.01, "Boiling seawater leaves scale: " + boiler.scale());
            @SuppressWarnings("removal") ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            helper.useBlock(drum, player);
            helper.assertTrue(boiler.scale() > 0.01, "A pickaxe does nothing to a hot boiler");
            boiler.steam.extract(1000);
            boiler.setForTesting(20, boiler.scale(), 0);
            helper.useBlock(drum, player);
            helper.assertTrue(boiler.scale() == 0, "and chips it off a cold one");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void aDamagedBoilerLeaksAndAPatchMendsIt(GameTestHelper helper) {
        BlockPos drum = new BlockPos(2, 1, 2);
        BoilerBlock.Entity boiler = boiler(helper, drum);
        boiler.setForTesting(20, 0, 0.8);
        boiler.steam.produce(GasKind.STEAM, units(6));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(helper.getBlockState(drum).getValue(BoilerBlock.LEAKING), "Badly damaged, it leaks");
            helper.assertTrue(boiler.pressure() < 6, "and loses its steam: " + boiler.pressure());
            helper.assertTrue(boiler.burstBar() < BoilerBlock.Entity.BURST_BAR, "and would burst sooner: " + boiler.burstBar());
            boiler.steam.extract(1000);
            @SuppressWarnings("removal") ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(de.ipnats.hardwrought.smithing.MetalStock.plate("iron"), 4));
            helper.useBlock(drum, player);
            helper.assertTrue(Math.abs(boiler.damage() - 0.55) < 1e-6, "A plate riveted over it takes a quarter off: " + boiler.damage());
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void aBoilerRunDryOverAFireIsRuined(GameTestHelper helper) {
        BlockPos drum = new BlockPos(2, 2, 2);
        helper.setBlock(drum.below(), Blocks.MAGMA_BLOCK);
        BoilerBlock.Entity boiler = boiler(helper, drum);
        boiler.setForTesting(BoilerBlock.Entity.SCORCHING + 200, 0, 0);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(boiler.damage() > 0.05, "Bare iron over a fire is ruined: " + boiler.damage());
            helper.succeed();
        });
    }

    /** A boiler with steam at so many bar, a line of iron to the east, and an engine at its end driving a shaft. */
    private static SteamEngineBlock.Entity plant(GameTestHelper helper, double bar, boolean shutValve) {
        BlockPos drum = new BlockPos(0, 1, 2), engine = new BlockPos(4, 1, 2);
        BoilerBlock.Entity boiler = boiler(helper, drum);
        boiler.steam.produce(GasKind.STEAM, units(bar));
        // Facing north: the axle leaves by the south face, into the shaft; the line comes in from the west.
        helper.setBlock(engine, SteamBlocks.STEAM_ENGINE.defaultBlockState().setValue(SteamEngineBlock.FACING, Direction.NORTH));
        helper.setBlock(engine.south(), ModBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Z));
        helper.setBlock(new BlockPos(1, 1, 2), GasLines.LINES.get(GasLineTier.IRON));
        helper.setBlock(new BlockPos(2, 1, 2), GasLines.VALVE.defaultBlockState().setValue(GasValveBlock.OPEN, !shutValve));
        helper.setBlock(new BlockPos(3, 1, 2), GasLines.LINES.get(GasLineTier.IRON));
        return helper.getBlockEntity(engine, SteamEngineBlock.Entity.class);
    }

    @GameTest(maxTicks = 100)
    public void aSteamEngineDrivesItsShaftOnSteamFromABoiler(GameTestHelper helper) {
        SteamEngineBlock.Entity engine = plant(helper, 12, false);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(engine.inlet.pressure() > SteamEngineBlock.Entity.FULL_BAR, "Steam has come down the line: " + engine.inlet.pressure());
            float speed = Math.abs(((KineticHolder) helper.getBlockEntity(new BlockPos(4, 1, 3), BlockEntity.class)).kineticSpeed());
            helper.assertTrue(speed == SteamEngineBlock.Entity.RPM, "and the engine turns its shaft: " + speed);
            helper.assertTrue(engine.capacity() == SteamEngineBlock.Entity.CAPACITY, "at full strength: " + engine.capacity());
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 2)).getValue(SteamEngineBlock.RUNNING), "and shows it");
            helper.setBlock(new BlockPos(5, 1, 2), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(engine.output() == 0, "A redstone signal shuts its throttle");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void aShutStopValveKeepsTheSteamBack(GameTestHelper helper) {
        SteamEngineBlock.Entity engine = plant(helper, 12, true);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(engine.inlet.amount() == 0, "Behind a shut valve the engine gets nothing: " + engine.inlet.amount());
            helper.assertTrue(engine.output() == 0, "and stands");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 400)
    public void aBurstSteamLineScaldsWhoStandsBy(GameTestHelper helper) {
        BlockPos drum = new BlockPos(1, 1, 2), weak = new BlockPos(2, 1, 2), whistle = new BlockPos(3, 1, 2);
        BoilerBlock.Entity boiler = boiler(helper, drum);
        helper.setBlock(whistle, SteamBlocks.STEAM_WHISTLE);
        helper.setBlock(weak, GasLines.LINES.get(GasLineTier.BRONZE));
        // Bronze holds 6 bar.
        boiler.steam.produce(GasKind.STEAM, units(12));
        Pig pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, weak.north());
        float health = pig.getHealth();
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(GasLines.LINES.get(GasLineTier.BRONZE), weak);
            helper.assertTrue(pig.getHealth() < health || !pig.isAlive(), "The steam scalds the pig by the line");
        });
    }

    @GameTest(maxTicks = 100)
    public void aWhistleSoundsOnSteamAndTakesSome(GameTestHelper helper) {
        BlockPos drum = new BlockPos(1, 1, 2), whistle = new BlockPos(3, 1, 2);
        BoilerBlock.Entity boiler = boiler(helper, drum);
        helper.setBlock(whistle, SteamBlocks.STEAM_WHISTLE);
        helper.setBlock(new BlockPos(2, 1, 2), GasLines.LINES.get(GasLineTier.IRON));
        SteamWhistleBlock.Entity entity = helper.getBlockEntity(whistle, SteamWhistleBlock.Entity.class);
        helper.runAfterDelay(5, () -> helper.assertTrue(!entity.blow(helper.getLevel(), helper.absolutePos(whistle)), "Without steam it is silent"));
        helper.runAfterDelay(6, () -> boiler.steam.produce(GasKind.STEAM, units(6)));
        helper.runAfterDelay(30, () -> {
            double before = entity.inlet.amount();
            helper.assertTrue(entity.blow(helper.getLevel(), helper.absolutePos(whistle)), "With steam behind it, it sounds");
            helper.assertTrue(entity.inlet.amount() < before, "and takes steam for it");
            helper.succeed();
        });
    }
}
