package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.environment.Altitude;
import de.ipnats.hardwrought.environment.EnvironmentSystem;
import de.ipnats.hardwrought.environment.GasMixture;
import de.ipnats.hardwrought.environment.VerticalZone;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * Milestone 5: the world is 1280 blocks tall, and the height a player stands at is a gameplay input
 * rather than a coordinate.
 */
public final class VerticalWorldGameTests {
    @GameTest
    public void theWorldIsTwelveHundredAndEightyBlocksTall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getMinY() == VerticalZone.WORLD_FLOOR_Y,
                "Section 41: the world begins at Y -256, which the dimension type states");
        helper.assertTrue(level.getHeight() == 1280,
                "and it is 1280 blocks tall, so the highest block is Y 1023");
        helper.assertTrue(level.getMinY() + level.getHeight() == VerticalZone.WORLD_CEILING_Y,
                "The stated ceiling of Y +1024 is the exclusive bound the height adds up to");

        // The dimension type alone would leave the new depth as void: terrain is only generated
        // where the noise settings reach, so they have to state the same range.
        var settings = level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getValueOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        helper.assertTrue(settings.noiseSettings().minY() == VerticalZone.WORLD_FLOOR_Y
                        && settings.noiseSettings().height() == 1280,
                "and the overworld noise settings generate through the whole of it");
        helper.succeed();
    }

    @GameTest
    public void verticalZonesCutTheWorldWhereTheSpecificationDoes(GameTestHelper helper) {
        helper.assertTrue(VerticalZone.at(-256) == VerticalZone.ABYSS
                        && VerticalZone.at(-181) == VerticalZone.ABYSS
                        && VerticalZone.at(-180) == VerticalZone.LOWER_CAVERNS
                        && VerticalZone.at(-100) == VerticalZone.DEEP_CAVES
                        && VerticalZone.at(-20) == VerticalZone.SHALLOW_CAVES
                        && VerticalZone.at(64) == VerticalZone.NORMAL_SURFACE,
                "Section 49: the cave layers are where the specification puts them");
        helper.assertTrue(VerticalZone.at(200) == VerticalZone.ALPINE
                        && VerticalZone.at(400) == VerticalZone.HIGH_MOUNTAINS
                        && VerticalZone.at(1023) == VerticalZone.EXTREME_ALTITUDE,
                "and section 42 stacks the altitude bands above them");
        helper.assertTrue(VerticalZone.at(-5).underground() && !VerticalZone.at(80).underground(),
                "A zone knows which side of the surface it is on");
        helper.assertTrue(VerticalZone.at(900).thinAir() && !VerticalZone.at(120).thinAir(),
                "and whether section 43 has anything to say about its air");
        helper.assertTrue(VerticalZone.byName("abyss") == VerticalZone.ABYSS
                        && VerticalZone.byName("nowhere") == null,
                "The names round trip for datapacks and diagnostics");
        helper.succeed();
    }

    @GameTest
    public void airThinsWithHeight(GameTestHelper helper) {
        helper.assertTrue(Altitude.pressure(Altitude.SEA_LEVEL) == 1.0
                        && Altitude.pressure(-200) == 1.0,
                "At and below sea level the air is whole");
        helper.assertTrue(Altitude.pressure(300) < 1.0
                        && Altitude.pressure(600) < Altitude.pressure(300)
                        && Altitude.pressure(900) < Altitude.pressure(600),
                "Section 43: Y 300 slightly reduced, Y 600 low, Y 900 very low");
        helper.assertTrue(Altitude.pressure(100_000) >= Altitude.MIN_PRESSURE,
                "and it never falls to vacuum, however high the number");

        GasMixture high = Altitude.outsideAir(900);
        helper.assertTrue(high.oxygen() < GasMixture.OUTDOOR_OXYGEN,
                "Thin air carries less oxygen in the same breath");
        helper.assertTrue(Altitude.outsideAir(Altitude.SEA_LEVEL).equals(GasMixture.OUTDOOR),
                "while sea-level air is exactly the baseline the rest of the model is built on");
        helper.assertTrue(high.oxygen() < GasMixture.OXYGEN_IMPAIRED,
                "Very low is low enough to impair someone standing in it");

        helper.assertFalse(Altitude.tooThinToBurn(300),
                "An open fire still holds in the alpine zone");
        helper.assertTrue(Altitude.tooThinToBurn(900),
                "but at extreme altitude there is not enough air left to keep one");
        helper.assertTrue(Altitude.outsideAir(900).oxygen() < EnvironmentSystem.FIRE_MINIMUM_OXYGEN,
                "which is the same threshold a room full of smoke is judged by, not a second rule");
        helper.succeed();
    }

    @GameTest
    public void theDeepIsHotAndTheHeightsAreCold(GameTestHelper helper) {
        helper.assertTrue(Altitude.geothermal(0) == 0 && Altitude.geothermal(64) == 0,
                "Section 47: the rock adds nothing at the surface");
        helper.assertTrue(Math.abs(Altitude.geothermal(-100) - 7.0) < 1.5,
                "about seven degrees at Y -100");
        helper.assertTrue(Math.abs(Altitude.geothermal(-200) - 20.0) < 1.5,
                "twenty at Y -200");
        helper.assertTrue(Altitude.geothermal(-256) == Altitude.GEOTHERMAL_MAX
                        && Altitude.geothermal(-400) == Altitude.GEOTHERMAL_MAX,
                "and the full thirty at the floor, with nothing beyond it");

        helper.assertTrue(Altitude.lapse(Altitude.SEA_LEVEL) == 0 && Altitude.lapse(-100) == 0,
                "Height costs temperature only above sea level");
        helper.assertTrue(Altitude.lapse(1000) > Altitude.lapse(400)
                        && Altitude.lapse(400) > Altitude.lapse(100),
                "and it keeps costing more the higher it gets");
        helper.assertTrue(Altitude.lapse(1000) - Altitude.lapse(900)
                        > Altitude.lapse(200) - Altitude.lapse(100),
                "faster where the air is thin, which is what makes extreme altitude lethal");

        helper.assertTrue(Altitude.gale(200) == 0 && Altitude.gale(1000) == 1.0
                        && Altitude.gale(500) > 0 && Altitude.gale(500) < 1.0,
                "Section 45: above the alpine zone the wind is there whatever the sky is doing");
        helper.succeed();
    }

    @GameTest
    public void aRoomCanOnlyBeAiredOutWithWhatIsOutsideIt(GameTestHelper helper) {
        GasMixture spent = new GasMixture(0.14, 0.03, 0.0, 0.0);
        GasMixture thin = Altitude.outsideAir(900);
        GasMixture aired = spent;
        for (int step = 0; step < 200; step++) aired = aired.ventilate(0.5, thin);
        helper.assertTrue(Math.abs(aired.oxygen() - thin.oxygen()) < 0.001,
                "Airing a room out at altitude settles at the thin air outside it");
        helper.assertTrue(aired.oxygen() < GasMixture.OUTDOOR_OXYGEN,
                "not at the sea-level air the room has no access to");

        GasMixture atSeaLevel = spent;
        for (int step = 0; step < 200; step++) atSeaLevel = atSeaLevel.ventilate(0.5);
        helper.assertTrue(Math.abs(atSeaLevel.oxygen() - GasMixture.OUTDOOR_OXYGEN) < 0.001,
                "while the plain call still means ordinary outside air");
        expectFailure(() -> spent.ventilate(0.5, null));
        helper.succeed();
    }

    @GameTest
    public void depthAndHeightReachTheLiveEnvironment(GameTestHelper helper) {
        var environment = CoreLifecycle.require(helper.getLevel().getServer()).environment();
        ServerLevel level = helper.getLevel();
        BlockPos here = helper.absolutePos(BlockPos.ZERO);
        BlockPos deep = new BlockPos(here.getX(), -200, here.getZ());
        BlockPos high = new BlockPos(here.getX(), 800, here.getZ());
        BlockPos surface = new BlockPos(here.getX(), 100, here.getZ());

        helper.assertTrue(environment.outdoorTemperature(level, deep)
                        > environment.outdoorTemperature(level, surface) + 10,
                "The same place is far hotter two hundred blocks down than at the surface");
        helper.assertTrue(environment.outdoorTemperature(level, high)
                        < environment.outdoorTemperature(level, surface),
                "and colder eight hundred blocks up");
        helper.assertTrue(environment.wind(level, high) > environment.wind(level, surface),
                "Section 45: wind grows with elevation");

        var lines = CoreLifecycle.require(level.getServer()).snapshot(level, here);
        helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith("ENVIRONMENT | ")
                        && line.contains("pressure=")),
                "The environment channel reports the zone and what its height does to the air");
        helper.succeed();
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
