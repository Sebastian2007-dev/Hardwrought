package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.worldgen.DeepWorld;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The shape of the overworld, read off the terrain functions themselves rather than off a generated
 * world: where the land stands over a wide stretch of it, and how open the deep is.
 */
public final class WorldgenGameTests {
    /** What the measure of depth is at height nought: it is 1.5 at -64 and falls by one every 128 blocks. */
    private static final double DEPTH_AT_ZERO = 1.0;

    /**
     * Draws the world cut open from floor to top through this place, west to east, as a picture: rock
     * dark, air light, lava level marked. Only where the environment asks for it (HARDWROUGHT_SLICE
     * names the file), because it is for a person to look at and takes a few seconds.
     */
    private static void slice(RandomState state, net.minecraft.world.level.levelgen.densityfunction.DensityFunction density, int x, int z) {
        String file = System.getenv("HARDWROUGHT_SLICE");
        if (file == null || file.isBlank()) return;
        int step = 4, across = 1200, top = DeepWorld.TOP, bottom = DeepWorld.BOTTOM;
        var image = new java.awt.image.BufferedImage(across, (top - bottom) / step, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int px = 0; px < across; px++) {
            for (int py = 0; py < image.getHeight(); py++) {
                int y = top - 1 - py * step;
                boolean rock = state.sampleBlockValueUncached(density, x + (px - across / 2) * step, y, z) > 0;
                int colour = rock ? (y < -64 ? 0x3A3A44 : y > 320 ? 0x8A8A8A : 0x6A6258)
                        : y < DeepWorld.LAVA_LEVEL ? 0xE06010 : y < 63 && y > 0 ? 0x3050A0 : y < 0 ? 0x101018 : 0xBFD8F0;
                if (y == 0 || y == 320 || y == 900) colour = (colour & 0xFEFEFE) / 2 + 0x404040;
                image.setRGB(px, py, colour);
            }
        }
        try {
            javax.imageio.ImageIO.write(image, "png", new java.io.File(file));
            System.out.println("[worldgen] slice through " + x + ", " + z + " written to " + file);
        } catch (java.io.IOException failed) {
            System.out.println("[worldgen] slice not written: " + failed);
        }
    }

    @GameTest
    public void theLandRisesToGreatMountainsAndTheDeepIsOpen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NoiseGeneratorSettings settings = level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        helper.assertTrue(settings.noiseSettings().minY() == DeepWorld.BOTTOM
                        && settings.noiseSettings().minY() + settings.noiseSettings().height() == DeepWorld.TOP,
                "The world reaches from its floor to its top");
        RandomState state = RandomState.create(level.registryAccess().lookupOrThrow(Registries.NOISE), 20261008L, settings);
        var router = settings.noiseRouter();

        // Where the land stands, column by column, over forty kilometres square.
        int columns = 0, low = 0, hills = 0, high = 0, giant = 0;
        double highest = -1000;
        int[] highestAt = new int[2];
        java.util.List<Double> heights = new java.util.ArrayList<>();
        for (int x = -20000; x <= 20000; x += 160) {
            for (int z = -20000; z <= 20000; z += 160) {
                double offset = state.sampleBlockValueUncached(router.depth(), x, 0, z) - DEPTH_AT_ZERO;
                double surface = -64 + (1.5 + offset) * 128;
                heights.add(surface);
                columns++;
                if (surface > highest) {
                    highestAt[0] = x;
                    highestAt[1] = z;
                }
                highest = Math.max(highest, surface);
                if (surface < 63) low++;
                if (surface >= 150) hills++;
                if (surface >= 320) high++;
                if (surface >= 700) giant++;
            }
        }
        slice(state, router.finalDensity(), highestAt[0], highestAt[1]);
        heights.sort(null);
        double median = heights.get(heights.size() / 2);
        String land = String.format(java.util.Locale.ROOT, "highest %.0f, median %.0f, %d columns: %.1f %% under the sea, %.1f %% over 150, "
                        + "%.2f %% over 320, %.2f %% over 700", highest, median, columns, 100.0 * low / columns, 100.0 * hills / columns,
                100.0 * high / columns, 100.0 * giant / columns);
        System.out.println("[worldgen] land: " + land);
        helper.assertTrue(highest >= 900, "Somewhere the mountains reach 900 and more: " + land);
        helper.assertTrue(giant > 0 && 100.0 * giant / columns < 2.0, "but giants are rare: " + land);
        helper.assertTrue(100.0 * high / columns < 10.0 && median > 40 && median < 110,
                "and most of the land is the land it was: " + land);

        // How open the rock is, layer by layer, well inland where the land is solid from top to bottom.
        StringBuilder deep = new StringBuilder();
        double lowerOpen = 0, abyssOpen = 0, betweenOpen = 0, floorOpen = 0;
        for (int y : new int[] {-30, -90, -140, -185, -215, -250}) {
            int open = 0, points = 0;
            for (int x = 3000; x < 7000; x += 97) {
                for (int z = 3000; z < 7000; z += 97) {
                    points++;
                    if (state.sampleBlockValueUncached(router.finalDensity(), x, y, z) <= 0) open++;
                }
            }
            double share = 100.0 * open / points;
            deep.append(String.format(java.util.Locale.ROOT, "%d: %.1f %%  ", y, share));
            if (y == -140) lowerOpen = share;
            if (y == -215) abyssOpen = share;
            if (y == -185) betweenOpen = share;
            if (y == -250) floorOpen = share;
        }
        System.out.println("[worldgen] open rock by height: " + deep);
        helper.assertTrue(lowerOpen >= 8 && lowerOpen <= 45, "The lower caverns are open halls in solid rock: " + deep);
        helper.assertTrue(abyssOpen >= 20 && abyssOpen <= 70, "The abyss is wider still: " + deep);
        helper.assertTrue(betweenOpen < lowerOpen && betweenOpen < abyssOpen, "with more rock between the two: " + deep);
        helper.assertTrue(floorOpen == 0, "and the world has a floor: " + deep);
        helper.assertTrue(DeepWorld.LAVA_LEVEL == DeepWorld.BOTTOM + 20 && DeepWorld.LAVA_LEVEL < -215,
                "Lava fills only what lies under the abyss");
        helper.succeed();
    }

    /** The heights and the depths wear a body down: the higher and the deeper, the sooner. */
    @GameTest
    public void theHeightsAndTheDeepAreNoPlaceToStay(GameTestHelper helper) {
        var outside = de.ipnats.hardwrought.environment.Altitude.class;
        java.util.function.IntToDoubleFunction minutes = y -> {
            double strain = de.ipnats.hardwrought.environment.Altitude.strain(
                    de.ipnats.hardwrought.environment.Altitude.outsideAir(y).oxygenStress(), y);
            return strain <= 0 ? Double.POSITIVE_INFINITY : 100 / strain / 60;
        };
        String figures = String.format(java.util.Locale.ROOT, "minutes to failing: Y 950 %.0f, 900 %.0f, 600 %.0f, 400 %.0f, 64 %.0f, -32 %.0f, "
                        + "-100 %.0f, -200 %.0f, -256 %.0f", minutes.applyAsDouble(950), minutes.applyAsDouble(900), minutes.applyAsDouble(600),
                minutes.applyAsDouble(400), minutes.applyAsDouble(64), minutes.applyAsDouble(-32), minutes.applyAsDouble(-100),
                minutes.applyAsDouble(-200), minutes.applyAsDouble(-256));
        System.out.println("[worldgen] " + figures);
        helper.assertTrue(Double.isInfinite(minutes.applyAsDouble(64)) && Double.isInfinite(minutes.applyAsDouble(300))
                        && Double.isInfinite(minutes.applyAsDouble(-20)), "Where people live there is no strain: " + figures);
        helper.assertTrue(minutes.applyAsDouble(900) < minutes.applyAsDouble(600) && minutes.applyAsDouble(900) > 6
                        && minutes.applyAsDouble(900) < 15, "The highest summits are minutes, not hours: " + figures);
        helper.assertTrue(minutes.applyAsDouble(-256) < minutes.applyAsDouble(-200) && minutes.applyAsDouble(-200) < minutes.applyAsDouble(-100)
                        && minutes.applyAsDouble(-256) > 5 && minutes.applyAsDouble(-256) < 10, "and so is the floor of the world: " + figures);
        helper.assertTrue(de.ipnats.hardwrought.environment.Altitude.depthPressure(64) == 1.0
                        && Math.abs(de.ipnats.hardwrought.environment.Altitude.depthPressure(-256) - 3.0) < 1e-9
                        && de.ipnats.hardwrought.environment.Altitude.pressure(-256) == 1.0,
                "The deep presses three times as hard as the surface, and has all the air a lung can use");
        helper.assertTrue(outside != null, "");
        // The test world is flat, not made of noise: the finder says so instead of inventing a mountain.
        helper.assertTrue(de.ipnats.hardwrought.worldgen.MountainFinder.landHeight(helper.getLevel(), 0, 0) == null
                        && de.ipnats.hardwrought.worldgen.MountainFinder.nearest(helper.getLevel(), net.minecraft.core.BlockPos.ZERO, 900) == null,
                "A world without generated terrain has no mountain to find");

        // A body gets used to it: by being there, slowly, and loses it more slowly still by staying away.
        double habit = 0, unfed = 0;
        int seconds = 0;
        while (habit < 1 && seconds < 100_000) {
            habit = de.ipnats.hardwrought.environment.Altitude.habit(habit, 1.0, true);
            unfed = de.ipnats.hardwrought.environment.Altitude.habit(unfed, 1.0, false);
            seconds++;
        }
        helper.assertTrue(Math.abs(seconds - de.ipnats.hardwrought.environment.Altitude.HABIT_SECONDS) < 2 && unfed < 0.51 && unfed > 0.49,
                "Fully used to the worst of it after two and a half hours there, half as fast on a poor diet: " + seconds + " s, " + unfed);
        double mild = 0;
        for (int i = 0; i < seconds; i++) mild = de.ipnats.hardwrought.environment.Altitude.habit(mild, 0.24, true);
        helper.assertTrue(mild > 0.4 && mild < 0.55, "Half way up a mountain teaches too, more slowly: " + mild);
        int fading = 0;
        while (habit > 0 && fading < 100_000) {
            habit = de.ipnats.hardwrought.environment.Altitude.habit(habit, 0, true);
            fading++;
        }
        helper.assertTrue(Math.abs(fading - de.ipnats.hardwrought.environment.Altitude.HABIT_FADING_SECONDS) < 2,
                "and eight hours away lose it all: " + fading + " s");
        double raw = de.ipnats.hardwrought.environment.Altitude.strain(1.0, -256), used = de.ipnats.hardwrought.environment.Altitude.strain(1.0, -256, 1, 1);
        helper.assertTrue(Math.abs(used / raw - 0.3) < 1e-9 && used > 0, "Being used to it takes most of the strain away, never all: " + used / raw);
        helper.assertTrue(de.ipnats.hardwrought.environment.Altitude.strain(0.6, 900, 1, 0) < de.ipnats.hardwrought.environment.Altitude.strain(0.6, 900, 0, 1)
                        && de.ipnats.hardwrought.environment.Altitude.strain(0, -256, 0, 1) < de.ipnats.hardwrought.environment.Altitude.strain(0, -256, 1, 0),
                "and being used to the heights is no help in the deep, nor the other way round");
        var grown = de.ipnats.hardwrought.survival.PlayerVitals.defaults().withHabits(0.4, 0.9);
        var saved = de.ipnats.hardwrought.survival.PlayerVitals.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, grown).getOrThrow();
        helper.assertTrue(de.ipnats.hardwrought.survival.PlayerVitals.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow().equals(grown),
                "What a body is used to is kept with the world");
        helper.succeed();
    }
}
