package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.environment.BiomeClimate;
import de.ipnats.hardwrought.environment.RadiantHeat;
import de.ipnats.hardwrought.environment.Season;
import de.ipnats.hardwrought.survival.Temperance;
import de.ipnats.hardwrought.survival.Thermal;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Locale;

/**
 * The thermal model, pinned to the outcomes it was designed for.
 *
 * <p>These are mostly tests of arithmetic rather than of the world, and that is deliberate: the
 * numbers in {@link Thermal} and {@link Season} were chosen against a table of places and seasons,
 * and a table is exactly the kind of thing that quietly stops being true. What is asserted here is
 * not the constants but the shape they were picked to produce — a tundra you can visit in summer
 * and not in winter, a desert that is the other way round, leather that beats steel in the cold,
 * and water that is a relief in one place and a death in another.
 */
public class ThermalGameTests {
    /** The same figure {@code EnvironmentSystem.outdoorTemperature} starts from, for a bare biome. */
    private static double outdoor(double biomeBaseTemperature, Season season) {
        double temperature = 14.0 + (biomeBaseTemperature - 0.8) * 18.0;
        return temperature + season.temperatureOffset(
                BiomeClimate.fromBaseTemperature((float) biomeBaseTemperature));
    }

    private static final double SNOWY = 0.0;
    private static final double PLAINS = 0.8;
    private static final double DESERT = 2.0;

    /** Full suits, as the armor profiles have them once coverage weighting is applied. */
    private static final double BARE = 0.0, LEATHER = 0.85, PLATE = -0.45;
    private static final double LEATHER_KG = 4.0, PLATE_KG = 10.0;

    private static double body(double air, double insulation, double armorKg) {
        return Thermal.targetBody(air, false, insulation, armorKg, 0.35, 0, false, false);
    }

    private static boolean deadly(double body) {
        return Thermal.thermalDamage(body) > 0;
    }

    private static String say(String what, double body) {
        return String.format(Locale.ROOT, "%s comes to %.1f °C", what, body);
    }

    /**
     * The one the whole season model exists for: a cold biome is a place you can go in summer and
     * a place that kills you in winter. If a season ever moved every climate by the same amount
     * this is the test that would notice.
     */
    @GameTest
    public void aColdBiomeIsSeasonalRatherThanPermanentlyHostile(GameTestHelper helper) {
        double summer = outdoor(SNOWY, Season.SUMMER);
        double winter = outdoor(SNOWY, Season.WINTER);
        helper.assertTrue(summer > 10, say("A snowy biome in summer", summer));
        helper.assertTrue(winter < -18, say("The same biome in winter", winter));

        helper.assertTrue(!deadly(body(summer, BARE, 0)),
                say("Summer there is survivable with nothing on at all", body(summer, BARE, 0)));
        helper.assertTrue(deadly(body(winter, BARE, 0)),
                say("Winter there is not", body(winter, BARE, 0)));
        helper.assertTrue(!deadly(body(winter, LEATHER, LEATHER_KG)),
                say("But winter there is survivable in leather", body(winter, LEATHER, LEATHER_KG)));
        helper.succeed();
    }

    /** And the desert is the other way round: the season that opens the tundra closes the sand. */
    @GameTest
    public void theDesertRunsTheOtherWay(GameTestHelper helper) {
        double summer = outdoor(DESERT, Season.SUMMER) + 3.0;
        double winter = outdoor(DESERT, Season.WINTER);
        helper.assertTrue(summer > winter + 8, "A desert still has a year, it is simply a smaller one");
        helper.assertTrue(!deadly(body(winter, BARE, 0)), "A desert winter is a pleasant thing");
        helper.assertTrue(body(summer, BARE, 0) > 39.0,
                say("A desert summer at noon is near the edge", body(summer, BARE, 0)));
        // Section 36: there is no universally perfect armor. In the sand, armor is the problem.
        helper.assertTrue(body(summer, PLATE, PLATE_KG) > body(summer, BARE, 0),
                "Wearing plate in a summer desert is worse than wearing nothing");
        helper.assertTrue(body(summer, LEATHER, LEATHER_KG) > body(summer, BARE, 0),
                "And so is wearing leather: insulation traps a body's own heat");
        helper.succeed();
    }

    /** Leather is the warm thing and metal is the cold thing, whatever the numbers become. */
    @GameTest
    public void leatherKeepsHeatAndMetalTakesIt(GameTestHelper helper) {
        double winter = outdoor(SNOWY, Season.WINTER);
        double bare = body(winter, BARE, 0);
        double leather = body(winter, LEATHER, LEATHER_KG);
        double plate = body(winter, PLATE, PLATE_KG);
        helper.assertTrue(leather > bare, say("Leather is warmer than nothing", leather));
        helper.assertTrue(plate < bare,
                say("Metal is colder than nothing: it conducts heat off the body", plate));
        helper.assertTrue(deadly(plate), "Walking into a winter tundra in iron is a way of dying");
        helper.succeed();
    }

    /**
     * Water is not cold air. A body in it is answering a different balance point, which is why a
     * lake can be the best thing in a desert and the last thing in a tundra.
     */
    @GameTest
    public void waterCoolsAndWaterKills(GameTestHelper helper) {
        helper.assertTrue(Thermal.COMFORT_WATER > Thermal.COMFORT_AIR,
                "Water has to be far warmer than air before it stops taking heat off a body");

        double desert = Thermal.waterTemperature(outdoor(DESERT, Season.SUMMER) + 3.0);
        double desertBody = Thermal.targetBody(desert, true, 0, 0, 0, 1.0, false, false);
        helper.assertTrue(desertBody < Thermal.NORMAL_BODY && !deadly(desertBody),
                say("A desert lake puts a body just under its own temperature", desertBody));

        double plains = Thermal.waterTemperature(outdoor(PLAINS, Season.SUMMER));
        double plainsBody = Thermal.targetBody(plains, true, 0, 0, 0, 1.0, false, false);
        helper.assertTrue(!deadly(plainsBody),
                say("Swimming on an ordinary summer day is simply swimming", plainsBody));

        double frozen = Thermal.waterTemperature(outdoor(SNOWY, Season.WINTER));
        helper.assertTrue(frozen <= 0, say("Water in a frozen place sits at freezing", frozen));
        double frozenBody = Thermal.targetBody(frozen, true, 0, 0, 0, 1.0, false, false);
        helper.assertTrue(deadly(frozenBody), say("And going into it is fatal", frozenBody));

        // At one and the same temperature, water is far the worse of the two. Air is only ever more
        // dangerous than water because air can reach −35 °C and liquid water cannot.
        double chilly = 10.0;
        double inAir = Thermal.targetBody(chilly, false, 0, 0, 0, 0, false, false);
        double inWater = Thermal.targetBody(chilly, true, 0, 0, 0, 0, false, false);
        helper.assertTrue(inWater < inAir - 1.0, String.format(Locale.ROOT,
                "Ten degrees of water (%.1f °C of body) must cost far more than ten of air (%.1f °C)",
                inWater, inAir));
        helper.succeed();
    }

    /**
     * A fire is worth sitting next to, worth sitting <em>close</em> to, and worth nothing at all
     * once it has gone out. The last of those is the one the old flat bonus got wrong.
     */
    @GameTest
    public void aFireIsWorthSittingCloseTo(GameTestHelper helper) {
        helper.assertTrue(RadiantHeat.strength(Blocks.FIRE.defaultBlockState()) > 0, "Fire is hot");
        helper.assertTrue(RadiantHeat.strength(
                        Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)) > 0,
                "A lit campfire is hot");
        helper.assertTrue(RadiantHeat.strength(
                        Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)) == 0,
                "A campfire that has been put out is a block of logs");
        helper.assertTrue(RadiantHeat.strength(
                        Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, false)) == 0,
                "And an unlit furnace warms nobody");
        helper.assertTrue(RadiantHeat.falloff(0.5) > RadiantHeat.falloff(3.0),
                "There is a right distance to sit at, and a player can find it");
        helper.assertTrue(RadiantHeat.falloff(RadiantHeat.RANGE) == 0, "Out of reach is out of reach");

        // Built in the world: a hearth is worth real degrees, and stepping away loses them.
        BlockPos hearth = new BlockPos(1, 2, 1);
        helper.setBlock(hearth.below(), Blocks.STONE);
        helper.setBlock(hearth, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        helper.assertTrue(helper.getBlockState(hearth).getBlock() instanceof CampfireBlock,
                "The hearth has to actually be there for the rest of this to mean anything");
        double atFire = RadiantHeat.nearby(helper.getLevel(), helper.absolutePos(hearth.above()));
        double awayFromFire = RadiantHeat.nearby(helper.getLevel(),
                helper.absolutePos(hearth.offset(4, 0, 0)));
        helper.assertTrue(atFire > 8, say("Sitting at the hearth is warm", atFire));
        helper.assertTrue(awayFromFire < atFire && awayFromFire >= 0,
                say("Four blocks off is much less so", awayFromFire));
        helper.setBlock(hearth, Blocks.AIR);

        // A fire alone does not make a winter tundra safe; it makes a camp with clothing safe.
        double winter = outdoor(SNOWY, Season.WINTER);
        helper.assertTrue(deadly(body(winter + 10, BARE, 0)),
                "A fire is not a substitute for having brought something to wear");
        helper.assertTrue(!deadly(body(winter + 10, LEATHER, LEATHER_KG)),
                "Clothing plus a fire is a camp");
        helper.succeed();
    }

    /** The rune is graded, it needs the whole suit, and it works in both directions. */
    @GameTest
    public void temperanceIsGradedAndWorksBothWays(GameTestHelper helper) {
        double winter = outdoor(SNOWY, Season.WINTER);
        double desert = outdoor(DESERT, Season.SUMMER) + 3.0;
        helper.assertTrue(Temperance.temper(winter, 0, false) == winter,
                "Without the rune the weather is the weather");

        double half = Temperance.temper(winter, 0.5, false);
        double whole = Temperance.temper(winter, 1.0, false);
        helper.assertTrue(half > winter && whole > half,
                "Each piece and each level is worth something on its own");
        helper.assertTrue(!deadly(body(whole, BARE, 0)),
                say("A full suit at full level is weatherproof in the cold", body(whole, BARE, 0)));
        helper.assertTrue(!deadly(Thermal.targetBody(Temperance.temper(desert, 1.0, false),
                        false, 0, 0, 0, 0, false, false)),
                "And in the heat, from the same rule rather than a second one");
        helper.assertTrue(Temperance.temper(winter, 1.0, false) < Thermal.COMFORT_AIR,
                "There is always a little weather left; it is a rune, not an off switch");
        helper.succeed();
    }

    /** What the seasons do to everything that grows, and when it rains. */
    @GameTest
    public void theYearHasAShape(GameTestHelper helper) {
        helper.assertTrue(Season.SPRING.growthFactor() > 1.0 && Season.SUMMER.growthFactor()
                        > Season.SPRING.growthFactor(),
                "Spring pushes and summer pushes harder");
        helper.assertTrue(Season.AUTUMN.growthFactor() == 1.0, "Autumn is merely ordinary");
        helper.assertTrue(Season.WINTER.growthFactor() < 1.0, "And in winter half of it does not ripen");
        helper.assertTrue(Season.SUMMER.rainFactor() < Season.AUTUMN.rainFactor(),
                "A dry summer and a wet autumn");
        helper.assertTrue(Season.SUMMER.evaporationFactor() > 1.4,
                "Summer takes the puddles with it");
        helper.assertTrue(Season.AUTUMN.fogChance() > 0
                        && Season.SPRING.fogChance() == 0
                        && Season.SUMMER.fogChance() == 0
                        && Season.WINTER.fogChance() == 0,
                "Fog belongs to autumn and to no other season");
        helper.succeed();
    }
}
