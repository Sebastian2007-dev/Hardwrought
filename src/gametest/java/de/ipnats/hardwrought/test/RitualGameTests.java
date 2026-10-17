package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.knowledge.Multiblocks;
import de.ipnats.hardwrought.ritual.Ritual;
import de.ipnats.hardwrought.ritual.RitualAltar;
import de.ipnats.hardwrought.ritual.RitualContent;
import de.ipnats.hardwrought.ritual.RitualPedestal;
import de.ipnats.hardwrought.ritual.Temple;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** The temples at the end of the game, built by their players as multiblocks, and the ritual in them. */
public final class RitualGameTests {
    @GameTest
    public void theThreeTemplesAreMultiblocksToBuild(GameTestHelper helper) {
        for (Temple.Theme theme : Temple.Theme.values()) {
            Multiblocks.Multiblock temple = Temple.multiblock(theme);
            helper.assertTrue(Multiblocks.ALL.contains(temple), "The compendium knows the " + theme.id() + " temple");
            helper.assertTrue(temple.width() == 2 * (Temple.HALF + Temple.STAIRS) + 1 && temple.height() > 25,
                    "a great one: " + temple.width() + " wide, " + temple.height() + " high");
            int blocks = temple.count(-1).values().stream().mapToInt(Integer::intValue).sum();
            helper.assertTrue(blocks > 5000, "of thousands of blocks: " + blocks);
            helper.assertTrue(temple.blocks().contains(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(RitualContent.ALTAR)),
                    "with the altar in it");
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 400)
    public void aStandingTempleTakesTheOfferingsAndTheRitualSetsItsPlayersFree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos altar = new BlockPos(30_000, 200, 30_000);
        Temple.buildNow(level, altar, Temple.Theme.DESERT);
        Temple.Survey survey = Temple.survey(level, altar);
        helper.assertTrue(survey.stands() && survey.theme() == Temple.Theme.DESERT && survey.wrong() == 0,
                "Built whole, the desert temple stands: " + survey);
        // A few blocks wrong do not undo it; a wall gone does.
        level.setBlock(altar.offset(0, 20, 0).east(9), Blocks.AIR.defaultBlockState(), 2);
        for (int i = 0; i < 5; i++) level.setBlock(altar.offset(Temple.HALF - 1 - i, -2, Temple.HALF - 2), Blocks.DIRT.defaultBlockState(), 2);
        helper.assertTrue(Temple.survey(level, altar).stands(), "A stray block or two do not undo it");
        var altarEntity = (RitualAltar.Entity) level.getBlockEntity(altar);
        helper.assertTrue(altarEntity != null, "The altar stands in its middle");
        // Every pedestal wants its own offering.
        List<BlockPos> pedestals = Temple.pedestals(altar);
        for (int i = 0; i < pedestals.size(); i++) {
            var pedestal = (RitualPedestal.Entity) level.getBlockEntity(pedestals.get(i));
            helper.assertFalse(pedestal.offerForTesting(level, new ItemStack(RitualContent.offering((i + 1) % pedestals.size()))),
                    "pedestal " + i + " refuses another's offering");
            helper.assertTrue(pedestal.offerForTesting(level, new ItemStack(RitualContent.offering(i))), "and takes its own: " + i);
        }
        var player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(altar.getX() + 3.5, altar.getY() - 1, altar.getZ() + 0.5);
        helper.assertTrue(Ritual.inChamber(level, altar).contains(player), "Whoever stands in the chamber is counted");
        helper.assertTrue(altarEntity.beginForTesting(level), "The heart laid on the altar begins the ritual");
        helper.assertTrue(Ritual.running(level, altar), "and it runs");
        Ritual.runForTesting(level, altar);
        helper.assertTrue(altarEntity.done(), "To its end: the ritual is done");
        for (BlockPos at : pedestals) {
            helper.assertFalse(((RitualPedestal.Entity) level.getBlockEntity(at)).filled(), "and its offerings burnt up");
        }
        // A temple with its hall broken away does not stand.
        for (int y = 0; y <= 12; y++) for (int x = -13; x <= 13; x++) level.setBlock(altar.offset(x, y, Temple.HALL), Blocks.AIR.defaultBlockState(), 2);
        helper.assertFalse(Temple.survey(level, altar).stands(), "A temple with a wall gone does not stand: " + Temple.survey(level, altar));
        helper.succeed();
    }

    /**
     * Every temple stands under the building physics once it is whole: nothing in it unsupported, nothing
     * overloaded, the terrace's foundation standing on the ground.
     */
    @GameTest
    public void everyTempleStandsUnderTheBuildingPhysics(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        StringBuilder report = new StringBuilder();
        for (Temple.Theme theme : Temple.Theme.values()) {
            var blocks = Temple.required(theme);
            de.ipnats.hardwrought.building.Statics.View view = pos -> {
                if (pos.getY() <= -2 && Math.abs(pos.getX()) <= Temple.HALF + Temple.STAIRS && Math.abs(pos.getZ()) <= Temple.HALF + Temple.STAIRS) {
                    return de.ipnats.hardwrought.building.Statics.ANCHOR;
                }
                var state = blocks.get(pos);
                if (state == null) return de.ipnats.hardwrought.building.Statics.EMPTY;
                return de.ipnats.hardwrought.building.StructuralMaterials.of(server, state)
                        .<de.ipnats.hardwrought.building.Statics.Cell>map(m -> m).orElse(de.ipnats.hardwrought.building.Statics.EMPTY);
            };
            var results = de.ipnats.hardwrought.building.Statics.solve(view, blocks.keySet(), 1_000_000);
            int failing = 0;
            StringBuilder examples = new StringBuilder();
            for (var e : results.entrySet()) {
                var r = e.getValue();
                if (r.supported() && r.utilisation() <= 1) continue;
                // The roof over the hall — dome, pyramid, spire — no physics would hold; its altar does (TempleSupport).
                BlockPos at = e.getKey();
                if (at.getY() >= 13 && Math.abs(at.getX()) <= Temple.HALL + 2 && Math.abs(at.getZ()) <= Temple.HALL + 2) continue;
                if (failing++ < 12) {
                    examples.append(String.format(java.util.Locale.ROOT, " %s %s %s %.2f;", e.getKey().toShortString(),
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blocks.get(e.getKey()).getBlock()).getPath(),
                            r.held(), r.utilisation()));
                }
            }
            if (failing > 0) report.append(theme.id()).append(": ").append(failing).append(" failing:").append(examples).append(" | ");
        }
        helper.assertTrue(report.isEmpty(), "Every temple stands: " + report);
        helper.succeed();
    }

    /**
     * An offering held to a pedestal is only ever laid there or refused, never used on it: the wrong one
     * stays in the hand, whole, and the right one is laid.
     */
    @GameTest
    public void anOfferingHeldToAPedestalIsNeverUsed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos altar = new BlockPos(-30_000, 200, -30_000);
        Temple.buildNow(level, altar, Temple.Theme.PLAINS);
        BlockPos at = Temple.pedestals(altar).getFirst();
        // The test world is kept between runs: an empty pedestal, not the one an earlier run left its offering on.
        level.removeBlock(at, false);
        level.setBlock(at, RitualContent.PEDESTAL.defaultBlockState(), 3);
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(3))) item.discard();
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), net.minecraft.core.Direction.UP, at, false);
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        int other = RitualContent.OFFERINGS.size() - 1;
        ItemStack wrong = new ItemStack(RitualContent.offering(other));
        player.setItemInHand(hand, wrong);
        var refused = level.getBlockState(at).useItemOn(wrong, level, player, hand, hit);
        helper.assertTrue(refused.consumesAction() || refused instanceof net.minecraft.world.InteractionResult.Fail,
                "Another's offering is taken as the click, not passed on to be used: " + refused);
        helper.assertTrue(player.getItemInHand(hand).is(RitualContent.offering(other)) && player.getItemInHand(hand).getCount() == 1,
                "and stays in the hand as it was");
        ItemStack right = new ItemStack(RitualContent.offering(0));
        player.setItemInHand(hand, right);
        level.getBlockState(at).useItemOn(right, level, player, hand, hit);
        var pedestal = (RitualPedestal.Entity) level.getBlockEntity(at);
        helper.assertTrue(pedestal.filled() && player.getItemInHand(hand).isEmpty(), "Its own is laid on it");
        helper.assertTrue(pedestal.wanted().isEmpty() && pedestal.held().is(RitualContent.offering(0)),
                "and shown there whole, no longer as the ghost of what it wants");
        helper.succeed();
    }

    /** Once the ritual has begun, whoever is in the chamber is held there: out through the door, they are set back inside. */
    @GameTest
    public void theRitualHoldsItsPlayersInTheChamber(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos altar = new BlockPos(-30_000, 200, -29_600);
        Temple.buildNow(level, altar, Temple.Theme.STONY);
        List<BlockPos> pedestals = Temple.pedestals(altar);
        for (int i = 0; i < pedestals.size(); i++) {
            ((RitualPedestal.Entity) level.getBlockEntity(pedestals.get(i))).offerForTesting(level, new ItemStack(RitualContent.offering(i)));
        }
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.teleportTo(altar.getX() + 4.5, altar.getY() - 1, altar.getZ() + 0.5);
        var altarEntity = (RitualAltar.Entity) level.getBlockEntity(altar);
        helper.assertTrue(altarEntity.beginForTesting(level), "The ritual begins");
        Ritual.stepForTesting(level, altar, 1);
        helper.assertTrue(Ritual.boundForTesting(level, altar, player), "and holds whoever is in the chamber");
        // Out through the door, and over the wall's height.
        player.teleportTo(altar.getX() + Temple.HALL + 4.5, altar.getY() - 1, altar.getZ() + 0.5);
        Ritual.stepForTesting(level, altar, 1);
        double flat = Math.hypot(player.getX() - (altar.getX() + 0.5), player.getZ() - (altar.getZ() + 0.5));
        helper.assertTrue(flat < Temple.CHAMBER, "Gone out through the door, they are set back inside: " + flat);
        player.teleportTo(altar.getX() + 0.5, altar.getY() + 30, altar.getZ() + 0.5);
        Ritual.stepForTesting(level, altar, 1);
        helper.assertTrue(player.getY() < altar.getY() + 12, "Flown up for the opening in the roof, they are brought down under it: " + player.getY());
        Ritual.runForTesting(level, altar);
        helper.assertTrue(player.isCreative(), "At its end they are set free");
        helper.succeed();
    }

    /** In creative a projection is built whole at a click: a temple so that it stands, a machine block for block. */
    @GameTest
    public void inCreativeAStructureIsBuiltAtOneClick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int turns = 0; turns < 2; turns++) {
            BlockPos anchor = new BlockPos(30_000 + 200 * turns, 200, -30_000);
            var temple = Temple.multiblock(Temple.Theme.STONY);
            de.ipnats.hardwrought.knowledge.MultiblockPlacing.build(level, temple, anchor, turns);
            BlockPos altar = null;
            for (int dx = -2; dx <= 2 && altar == null; dx++) for (int dz = -2; dz <= 2 && altar == null; dz++) for (int y = 0; y < temple.height(); y++) {
                if (level.getBlockState(anchor.offset(dx, y, dz)).is(RitualContent.ALTAR)) { altar = anchor.offset(dx, y, dz); break; }
            }
            helper.assertTrue(altar != null, "The altar stands in the middle of where the ghost was, turned " + turns);
            Temple.Survey survey = Temple.survey(level, altar);
            helper.assertTrue(survey.stands() && survey.theme() == Temple.Theme.STONY, "and the temple round it: " + survey);
        }
        BlockPos anchor = new BlockPos(30_000, 200, -29_600);
        var seal = de.ipnats.hardwrought.knowledge.Multiblocks.UNDERWORLD_SEAL;
        de.ipnats.hardwrought.knowledge.MultiblockPlacing.build(level, seal, anchor, 1);
        for (int y = 0; y < seal.height(); y++) for (int z = 0; z < seal.depth(); z++) for (int x = 0; x < seal.width(); x++) {
            char symbol = seal.at(x, y, z);
            if (symbol == ' ') continue;
            BlockPos at = de.ipnats.hardwrought.knowledge.MultiblockPlacing.at(seal, anchor, 1, x, y, z);
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock());
            helper.assertTrue(seal.legend().get(symbol).contains(id), "The seal stands block for block: " + at.toShortString() + " " + id);
        }
        helper.succeed();
    }

    /** A standing altar holds the blocks of its temple where the plan has them, and nothing else. */
    @GameTest(maxTicks = 60)
    public void theAltarHoldsItsTempleUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos altar = new BlockPos(-30_000, 200, 30_000);
        Temple.buildNow(level, altar, Temple.Theme.PLAINS);
        // Beside the opening over the altar, where the sky beam goes out.
        BlockPos dome = altar.offset(6, 12, 0);
        while (level.getBlockState(dome).isAir() && dome.getY() < altar.getY() + Temple.TOP) dome = dome.above();
        BlockPos top = dome;
        helper.succeedWhen(() -> {
            helper.assertTrue(de.ipnats.hardwrought.ritual.TempleSupport.holds(level, top, level.getBlockState(top)),
                    "The altar holds the top of its dome at " + top.toShortString());
            helper.assertFalse(de.ipnats.hardwrought.ritual.TempleSupport.holds(level, top, Blocks.DIRT.defaultBlockState()),
                    "but not what the plan does not have there");
            helper.assertFalse(de.ipnats.hardwrought.ritual.TempleSupport.holds(level, top.above(60), level.getBlockState(top)),
                    "nor anything out of its reach");
        });
    }
}
