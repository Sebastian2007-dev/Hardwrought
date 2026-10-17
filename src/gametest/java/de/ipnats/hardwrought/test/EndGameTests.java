package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.end.EndRules;
import de.ipnats.hardwrought.end.VoidLight;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** The End's rules: arriving in the void on a floor of light, and no building while the dragon lives. */
public final class EndGameTests {
    @GameTest
    public void theIslandLiesBrokenInTheVoid(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        var pillars = net.minecraft.world.level.levelgen.feature.EndSpikeFeature.getSpikesForLevel(end);
        var fragments = de.ipnats.hardwrought.end.BrokenIsland.fragments(end.getSeed(), pillars);
        helper.assertTrue(fragments.size() > 40, "Many pieces: " + fragments.size());
        var middle = fragments.getFirst();
        helper.assertTrue(middle.flat() && middle.x() == 0 && middle.z() == 0 && middle.top() == de.ipnats.hardwrought.end.BrokenIsland.MIDDLE_TOP,
                "the largest in the middle, flat, to carry the portal");
        for (var f : fragments) {
            for (var pillar : pillars) {
                helper.assertTrue(Math.hypot(pillar.getCenterX() - f.x(), pillar.getCenterZ() - f.z()) > f.radius() + pillar.getRadius(),
                        "no piece grown into a pillar: " + f);
            }
            helper.assertTrue(Math.hypot(EndRules.ARRIVAL.x - f.x(), EndRules.ARRIVAL.z - f.z()) > f.radius() + 20,
                    "and none where travellers arrive: " + f);
        }
        helper.assertTrue(fragments.equals(de.ipnats.hardwrought.end.BrokenIsland.fragments(end.getSeed(), pillars)), "always the same for a world");
        helper.succeed();
    }

    @GameTest
    public void theWayInSetsTravellersDownInTheVoidOnAFloorOfLight(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "There is an End");
        TeleportTransition vanilla = new TeleportTransition(end, new Vec3(100.5, 49, 0.5), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING);
        TeleportTransition ours = EndRules.arrival(vanilla);
        helper.assertTrue(ours.position().equals(EndRules.ARRIVAL), "The traveller comes down out in the void: " + ours.position());
        BlockPos middle = BlockPos.containing(EndRules.ARRIVAL).below();
        EndRules.lightPlatform(end);
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-EndRules.PLATFORM_RADIUS, 0, -EndRules.PLATFORM_RADIUS),
                middle.offset(EndRules.PLATFORM_RADIUS, 0, EndRules.PLATFORM_RADIUS))) {
            helper.assertTrue(end.getBlockState(pos).is(EndRules.VOID_LIGHT), "on a floor of light at " + pos.toShortString());
        }
        helper.assertTrue(!end.getBlockState(middle.offset(EndRules.PLATFORM_RADIUS + 1, 0, 0)).is(EndRules.VOID_LIGHT), "no wider than it is");
        helper.assertTrue(end.getBlockEntity(middle) instanceof VoidLight.Entity, "which knows when it goes out");
        helper.assertTrue(EndRules.dragonAlive(end), "The dragon lives in a new world");
        helper.assertTrue(!EndRules.dragonAlive(helper.getLevel()), "and nowhere but in the End");
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-EndRules.PLATFORM_RADIUS, 0, -EndRules.PLATFORM_RADIUS),
                middle.offset(EndRules.PLATFORM_RADIUS, 0, EndRules.PLATFORM_RADIUS))) {
            end.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest
    public void theDragonHuntsInItsOwnWay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var dragon = net.minecraft.world.entity.EntityTypes.ENDER_DRAGON.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        BlockPos at = helper.absolutePos(new BlockPos(2, 30, 2));
        dragon.snapTo(at.getX(), at.getY(), at.getZ(), 0, 0);
        level.addFreshEntity(dragon);
        helper.assertTrue(dragon.getPhaseManager().getCurrentPhase() instanceof de.ipnats.hardwrought.end.dragon.DragonHunt,
                "It hunts in its own way from the start: " + dragon.getPhaseManager().getCurrentPhase());
        dragon.getPhaseManager().setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.LANDING_APPROACH);
        helper.assertTrue(dragon.getPhaseManager().getCurrentPhase().getPhase() == de.ipnats.hardwrought.end.dragon.VoidDragon.HUNT,
                "and whatever vanilla would have it do, it goes on hunting");
        helper.assertTrue(Math.abs(dragon.getMaxHealth() - de.ipnats.hardwrought.end.dragon.VoidDragon.HEALTH) < 0.01, "far more to it: " + dragon.getMaxHealth());
        dragon.getPhaseManager().setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING);
        helper.assertTrue(dragon.getPhaseManager().getCurrentPhase().getPhase()
                == net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING, "only its dying is left to vanilla");
        dragon.discard();
        helper.succeed();
    }

    @GameTest
    public void aCrystalsWardHasToBeBrokenFirst(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        end.getChunk(400 >> 4, 400 >> 4);
        var crystal = new net.minecraft.world.entity.boss.enderdragon.EndCrystal(end, 400.5, 120, 400.5);
        end.addFreshEntity(crystal);
        ((de.ipnats.hardwrought.end.dragon.CrystalWards.Warded) crystal).hardwrought$setWard(de.ipnats.hardwrought.end.dragon.CrystalWards.WARD);
        var player = helper.makeMockServerPlayerInLevel();
        var hit = end.damageSources().playerAttack(player);
        helper.assertFalse(crystal.hurtServer(end, hit, 15), "Its ward takes the blow");
        helper.assertTrue(crystal.isAlive() && Math.abs(de.ipnats.hardwrought.end.dragon.CrystalWards.ward(crystal) - 25) < 0.01,
                "and is worn down by it: " + de.ipnats.hardwrought.end.dragon.CrystalWards.ward(crystal));
        crystal.hurtServer(end, hit, 30);
        helper.assertTrue(crystal.isAlive() && de.ipnats.hardwrought.end.dragon.CrystalWards.ward(crystal) <= 0, "until it breaks, the crystal whole");
        crystal.hurtServer(end, hit, 1);
        helper.assertFalse(crystal.isAlive(), "and then the crystal can be struck");
        helper.succeed();
    }
}
