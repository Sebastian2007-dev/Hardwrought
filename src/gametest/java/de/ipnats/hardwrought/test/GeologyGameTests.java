package de.ipnats.hardwrought.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.geology.DepositProfile;
import de.ipnats.hardwrought.geology.Geology;
import de.ipnats.hardwrought.geology.GeologyEvents;
import de.ipnats.hardwrought.geology.OreDeposit;
import de.ipnats.hardwrought.geology.RockProfile;
import de.ipnats.hardwrought.geology.RockType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Milestone 6: the ground is made of something, the ore in it sits in few large bodies, and what a
 * body is worth varies from its middle to its rim.
 */
public final class GeologyGameTests {
    private static final long SEED = 1234567890123L;

    @GameTest
    public void rockRegionsAreRegionalAndRepeatable(GameTestHelper helper) {
        RockType rock = Geology.rockAt(SEED, 0, 0);
        helper.assertTrue(Geology.rockAt(SEED, 0, 0) == rock,
                "The same place always has the same rock under it");
        helper.assertTrue(Geology.rockAt(SEED, Geology.REGION_SIZE_BLOCKS - 1,
                        Geology.REGION_SIZE_BLOCKS - 1) == rock,
                "Section 51: a region is one rock, not a rock per block");
        helper.assertTrue(Geology.regionX(-1) == -1 && Geology.regionX(0) == 0,
                "and the region grid does not fold over at the origin");

        boolean varies = false;
        for (int region = 1; region <= 12 && !varies; region++) {
            varies = Geology.rockAt(SEED, region * Geology.REGION_SIZE_BLOCKS, 0) != rock;
        }
        helper.assertTrue(varies, "Different regions are made of different rock");
        helper.assertTrue(Geology.rockAt(SEED + 1, 0, 0) != rock
                        || Geology.rockAt(SEED + 2, 0, 0) != rock,
                "and a different world is a different geology");
        helper.succeed();
    }

    @GameTest
    public void depositsAreFewLargeAndTrueToTheirRock(GameTestHelper helper) {
        var profiles = CoreLifecycle.require(helper.getLevel().getServer()).rockProfiles();
        int total = 0;
        for (int region = 0; region < 24; region++) {
            List<OreDeposit> deposits = Geology.deposits(SEED, profiles, region, region / 3);
            helper.assertTrue(deposits.size() >= 1 && deposits.size() <= Geology.MAX_DEPOSITS_PER_REGION,
                    "Section 52: a region holds a few bodies, not a vein in every chunk");
            helper.assertTrue(deposits.equals(Geology.deposits(SEED, profiles, region, region / 3)),
                    "and asking twice gives the same bodies, because nothing is stored");

            RockType rock = Geology.rockAt(SEED, region * Geology.REGION_SIZE_BLOCKS,
                    (region / 3) * Geology.REGION_SIZE_BLOCKS);
            RockProfile profile = profiles.get(rock.id());
            for (OreDeposit deposit : deposits) {
                total++;
                DepositProfile stated = profile.deposits().stream()
                        .filter(entry -> entry.ore().equals(deposit.ore())).findFirst().orElse(null);
                helper.assertTrue(stated != null,
                        "A body only ever carries an ore its rock actually holds");
                helper.assertTrue(deposit.centre().getY() >= stated.minY()
                                && deposit.centre().getY() <= stated.maxY(),
                        "and it sits in the depth band the profile states");
                helper.assertTrue(deposit.horizontalRadius() >= stated.minRadius()
                                && deposit.horizontalRadius() <= stated.maxRadius(),
                        "at the size it states");
                helper.assertTrue(deposit.coreGrade() >= stated.minGrade()
                                && deposit.coreGrade() <= stated.maxGrade(),
                        "and with a grade inside its range");
                helper.assertTrue(deposit.horizontalRadius() >= 8,
                        "Bodies are large: this is what replaces scattered veins");
                int localX = Math.floorMod(deposit.centre().getX(), Geology.REGION_SIZE_BLOCKS);
                helper.assertTrue(localX >= Geology.BORDER_MARGIN
                                && localX <= Geology.REGION_SIZE_BLOCKS - Geology.BORDER_MARGIN,
                        "and each one is kept clear of its own region border");
            }
        }
        helper.assertTrue(total >= 24, "Every region examined really produced bodies");
        helper.succeed();
    }

    @GameTest
    public void aBodyIsRichestInTheMiddle(GameTestHelper helper) {
        OreDeposit body = new OreDeposit(Identifier.withDefaultNamespace("iron_ore"),
                new BlockPos(0, 0, 0), 30, 10, 0.20);
        helper.assertTrue(body.gradeAt(0, 0, 0) == 0.20,
                "Section 53: the middle of a body assays what the body is worth");
        helper.assertTrue(Math.abs(body.gradeAt(30, 0, 0) - 0.20 * OreDeposit.RIM_SHARE) < 1e-9,
                "and the rim only a fraction of it");
        helper.assertTrue(body.gradeAt(15, 0, 0) > body.gradeAt(25, 0, 0),
                "so working outward is working worse rock");
        helper.assertTrue(body.contains(0, 9, 0) && !body.contains(0, 11, 0),
                "A body is flatter than it is wide, the way a seam lies");
        helper.assertTrue(body.gradeAt(100, 0, 0) == 0,
                "and outside it there is no ore to grade at all");
        expectFailure(() -> new OreDeposit(Identifier.withDefaultNamespace("iron_ore"),
                BlockPos.ZERO, 0, 10, 0.2));
        expectFailure(() -> new OreDeposit(Identifier.withDefaultNamespace("iron_ore"),
                BlockPos.ZERO, 10, 10, 4.0));
        helper.succeed();
    }

    @GameTest
    public void aBodyCanBeFoundFromTheCoordinateAlone(GameTestHelper helper) {
        var profiles = CoreLifecycle.require(helper.getLevel().getServer()).rockProfiles();
        OreDeposit body = null;
        for (int region = 0; region < 12 && body == null; region++) {
            List<OreDeposit> deposits = Geology.deposits(SEED, profiles, region, 0);
            if (!deposits.isEmpty()) body = deposits.get(0);
        }
        helper.assertTrue(body != null, "There is a body somewhere to look for");

        BlockPos centre = body.centre();
        OreDeposit found = Geology.depositAt(SEED, profiles, centre.getX(), centre.getY(), centre.getZ());
        helper.assertTrue(found != null && found.centre().equals(centre),
                "Standing in a body finds that body, from the seed and the coordinate alone");
        helper.assertTrue(Geology.gradeAt(SEED, profiles, centre.getX(), centre.getY(), centre.getZ()) > 0,
                "and the rock there assays something");

        int farAbove = centre.getY() + body.verticalRadius() + 40;
        helper.assertTrue(Geology.depositAt(SEED, profiles, centre.getX(), farAbove, centre.getZ()) == null,
                "while the rock well above it is barren");
        helper.assertTrue(Geology.depositOver(SEED, profiles, centre.getX(), centre.getZ(), body.ore()) != null,
                "Worldgen asking which body stands under a column finds the same one");
        helper.assertTrue(Geology.depositOver(SEED, profiles, centre.getX(), centre.getZ(),
                        Identifier.withDefaultNamespace("nothing_ore")) == null,
                "and asking for an ore that is not there finds nothing");
        helper.succeed();
    }

    @GameTest
    public void rockProfilesAreLoadedAndBounded(GameTestHelper helper) {
        Map<Identifier, RockProfile> profiles =
                CoreLifecycle.require(helper.getLevel().getServer()).rockProfiles();
        for (RockType rock : RockType.values()) {
            RockProfile profile = profiles.get(rock.id());
            helper.assertTrue(profile != null && !profile.deposits().isEmpty(),
                    "Every rock type has a shipped profile saying what it carries");
        }
        helper.assertTrue(profiles.get(RockType.SEDIMENTARY.id()).deposits().stream()
                        .anyMatch(deposit -> deposit.ore().getPath().equals("coal_ore")),
                "Coal is a sedimentary rock, which is where the profile puts it");
        expectFailure(() -> profiles.clear());

        RockProfile parsed = RockProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"rocks":["hardwrought:granite"],"deposits":[
                  {"ore":"minecraft:iron_ore","weight":3,"min_y":0,"max_y":64},
                  {"ore":"minecraft:gold_ore","weight":1,"min_y":0,"max_y":64}]}
                """)).getOrThrow();
        helper.assertTrue(parsed.totalWeight() == 4, "Weights add up to what a roll is taken against");
        helper.assertTrue(parsed.pick(0).ore().getPath().equals("iron_ore")
                        && parsed.pick(3).ore().getPath().equals("gold_ore"),
                "and a roll lands in the band it belongs to");
        // The record rejects it while it is being decoded, which aborts the whole reload rather
        // than letting a half-valid rock table reach a world.
        expectFailure(() -> DepositProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"ore":"minecraft:iron_ore","min_y":64,"max_y":0}
                """)).result());
        helper.succeed();
    }

    @GameTest
    public void richRockPaysMoreThanPoorRock(GameTestHelper helper) {
        helper.assertTrue(GeologyEvents.bonusDrops(0) == 0 && GeologyEvents.bonusDrops(-1) == 0,
                "Barren rock pays nothing extra");
        helper.assertTrue(GeologyEvents.bonusDrops(0.05) == 0,
                "and a poor seam is barely worth the pick");
        helper.assertTrue(GeologyEvents.bonusDrops(0.20) > GeologyEvents.bonusDrops(0.10),
                "Section 53: better rock pays better");
        helper.assertTrue(GeologyEvents.bonusDrops(0.95) == GeologyEvents.MAX_BONUS_DROPS,
                "up to a stated ceiling, so nothing can run away");

        helper.assertTrue(GeologyEvents.bearing(BlockPos.ZERO, new BlockPos(100, 0, 10))
                        == net.minecraft.core.Direction.EAST
                        && GeologyEvents.bearing(BlockPos.ZERO, new BlockPos(0, 0, -80))
                        == net.minecraft.core.Direction.NORTH,
                "Prospecting points the way in whole compass directions");
        helper.succeed();
    }

    /**
     * The balance question, asked as a number rather than a feeling: how much of the ground has an
     * ore body under it somewhere? Too little and a world reads as empty however correct the model
     * is; too much and bodies stop being worth finding.
     */
    @GameTest
    public void oreBodiesCoverEnoughGroundToBeWorthLookingFor(GameTestHelper helper) {
        var profiles = CoreLifecycle.require(helper.getLevel().getServer()).rockProfiles();
        int samples = 0;
        int covered = 0;
        for (int x = 0; x < 1024; x += 16) {
            for (int z = 0; z < 1024; z += 16) {
                samples++;
                if (Geology.bodyOver(SEED, profiles, x, z) != null) covered++;
            }
        }
        double coverage = covered / (double) samples;
        helper.assertTrue(coverage > 0.20,
                "A fifth of the ground at least has something under it, or the world reads as "
                        + "empty and nobody prospects twice (was " + coverage + ")");
        helper.assertTrue(coverage < 0.85,
                "and not so much that finding a body stops meaning anything (was " + coverage + ")");
        helper.succeed();
    }

    @GameTest
    public void richGroundIsThreadedWithRealVeins(GameTestHelper helper) {
        var profiles = CoreLifecycle.require(helper.getLevel().getServer()).rockProfiles();
        long core = 0, coreOre = 0, rim = 0, rimOre = 0;
        int bodies = 0;
        for (int region = 0; region < 12; region++) {
            for (OreDeposit body : Geology.deposits(SEED, profiles, region, region)) {
                bodies++;
                var fields = de.ipnats.hardwrought.geology.OreBodies.fields(SEED, body.ore());
                int r = body.horizontalRadius(), v = body.verticalRadius();
                for (int dx = -r; dx <= r; dx += 2) {
                    for (int dz = -r; dz <= r; dz += 2) {
                        for (int dy = -v; dy <= v; dy++) {
                            int x = body.centre().getX() + dx, y = body.centre().getY() + dy, z = body.centre().getZ() + dz;
                            double reach = body.reach(x, y, z);
                            boolean ore = de.ipnats.hardwrought.geology.OreBodies.veinAt(fields, body, x, y, z);
                            if (reach < 0.35) {
                                core++;
                                if (ore) coreOre++;
                            } else if (reach > 0.8 && reach <= 1.0) {
                                rim++;
                                if (ore) rimOre++;
                            }
                            if (reach > 1.0 && ore) throw new AssertionError("A vein never runs outside its body");
                        }
                    }
                }
            }
        }
        double coreShare = coreOre / (double) core, rimShare = rimOre / (double) rim;
        System.out.printf("[ore bodies] %d bodies, core %.4f, rim %.4f%n", bodies, coreShare, rimShare);
        helper.assertTrue(coreShare > 0.03 && coreShare < 0.15,
                "The core of a body is worth tunnelling into, without being solid ore: " + coreShare);
        helper.assertTrue(rimShare < coreShare / 2 && rimShare > 0.002,
                "and the strands thin out towards the rim: " + rimShare + " against " + coreShare);
        var features = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);
        helper.assertTrue(features.get(de.ipnats.hardwrought.geology.OreBodies.PLACED).isPresent(),
                "The veins are part of world generation");
        helper.succeed();
    }

    @GameTest
    public void theOreChannelReportsTheGround(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        var lines = runtime.snapshot(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith("ORE | ")
                        && line.contains("region")),
                "The ore channel reports the rock of the region it is asked about");
        helper.assertTrue(lines.stream().noneMatch(line -> line.startsWith("ORE | unavailable")),
                "and is a real measurement rather than a placeholder");
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
