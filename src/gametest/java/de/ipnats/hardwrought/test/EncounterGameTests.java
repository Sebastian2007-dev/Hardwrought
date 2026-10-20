package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.encounter.Brand;
import de.ipnats.hardwrought.encounter.Encounter;
import de.ipnats.hardwrought.encounter.Encounters;
import de.ipnats.hardwrought.encounter.SharedBurden;
import de.ipnats.hardwrought.encounter.Tether;
import de.ipnats.hardwrought.mobs.Bosses;
import de.ipnats.hardwrought.witchking.WitchKing;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Boss fights in company (specification §§ 97 to 101): who counts, how slowly the count follows, and
 * the dangers only a group can answer. Never more health.
 */
public final class EncounterGameTests {
    private static ServerPlayer survivor(GameTestHelper helper, double x, double z) {
        return TestPlayers.survivor(helper, x, z);
    }

    /** A zombie that stands still, to be the boss of a test fight. */
    private static Zombie standIn(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        zombie.setNoAi(true);
        return zombie;
    }

    @GameTest
    public void theFighterCountFollowsSlowly(GameTestHelper helper) {
        Zombie boss = standIn(helper);
        Encounter fight = Encounters.of(boss, boss::position, 16, 0);
        helper.assertTrue(fight.fighters() == 1, "A fight starts with one fighter");
        for (int t = 0; t < Encounter.JOIN_TICKS / 20 - 1; t++) fight.follow(3, 20);
        helper.assertTrue(fight.fighters() == 1, "Two arriving are not felt at once: " + fight.scale());
        fight.follow(3, 20);
        helper.assertTrue(fight.fighters() == 2, "one is, after a while");
        for (int t = 0; t < Encounter.JOIN_TICKS / 20; t++) fight.follow(3, 20);
        helper.assertTrue(fight.fighters() == 3, "and then the other");
        for (int t = 0; t < Encounter.LEAVE_GRACE / 20; t++) fight.follow(1, 20);
        helper.assertTrue(fight.fighters() == 3, "Stepping out of the fight does not make it easier at once");
        for (int t = 0; t < Encounter.LEAVE_TICKS / 20; t++) fight.follow(1, 20);
        helper.assertTrue(fight.fighters() == 2, "only slowly: " + fight.scale());
        boss.discard();
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void strikingTheBossCountsAsFighting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie boss = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        boss.setNoAi(true);
        Encounter fight = Encounters.of(boss, boss::position, 16, 0);
        ServerPlayer fighter = survivor(helper, 2, 2);
        ServerPlayer onlooker = survivor(helper, 3, 3);
        boss.hurtServer(level, level.damageSources().playerAttack(fighter), 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(fight.active().contains(fighter), "Whoever struck it is in the fight");
            helper.assertTrue(!fight.active().contains(onlooker), "whoever only stood by is not");
            boss.discard();
        });
    }

    @GameTest
    public void aBurdenIsLighterShared(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie boss = standIn(helper);
        Encounter fight = Encounters.of(boss, boss::position, 16, 0);
        ServerPlayer bearer = survivor(helper, 4, 4), friend = survivor(helper, 5, 4), far = survivor(helper, 4, 14);
        SharedBurden burden = new SharedBurden(bearer, boss, DamageTypes.MAGIC, 12);
        for (int t = 0; t < SharedBurden.TICKS; t++) burden.tick(level, fight);
        helper.assertTrue(burden.shared() == 2, "Two stood under it: " + burden.shared() + " bearer=" + bearer.position()
                + " friend=" + friend.position() + " far=" + far.position());
        float taken = bearer.getMaxHealth() - bearer.getHealth(), friendTook = friend.getMaxHealth() - friend.getHealth();
        helper.assertTrue(taken > 0 && Math.abs(taken - friendTook) < 0.01f, "each took the same share: " + taken + " / " + friendTook);
        helper.assertTrue(far.getHealth() == far.getMaxHealth(), "the one far away took none of it");
        // The same burden carried alone.
        SharedBurden alone = new SharedBurden(far, boss, DamageTypes.MAGIC, 12);
        for (int t = 0; t < SharedBurden.TICKS; t++) alone.tick(level, fight);
        float carried = far.getMaxHealth() - far.getHealth();
        helper.assertTrue(alone.shared() == 1 && carried > taken * 1.6f, "Carried alone it weighs about twice as much: " + carried + " against " + taken);
        boss.discard();
        helper.succeed();
    }

    @GameTest
    public void aBrandTearsThoseNearItsBearer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie boss = standIn(helper);
        Encounter fight = Encounters.of(boss, boss::position, 16, 0);
        ServerPlayer bearer = survivor(helper, 4, 4), near = survivor(helper, 6, 4), apart = survivor(helper, 4, 14);
        Brand brand = new Brand(bearer, boss, DamageTypes.MAGIC, 10, at -> { });
        for (int t = 0; t < Brand.TICKS; t++) brand.tick(level, fight);
        helper.assertTrue(brand.caught() == 1, "It caught the one standing with its bearer: " + brand.caught());
        helper.assertTrue(near.getMaxHealth() - near.getHealth() > bearer.getMaxHealth() - bearer.getHealth(),
                "who is torn worse than the bearer");
        helper.assertTrue(apart.getHealth() == apart.getMaxHealth(), "and not the one who kept away");
        boss.discard();
        helper.succeed();
    }

    @GameTest
    public void aTetherSnapsWhenPulledApart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie boss = standIn(helper);
        Encounter fight = Encounters.of(boss, boss::position, 32, 0);
        ServerPlayer a = survivor(helper, 2, 2), b = survivor(helper, 5, 2);
        int[] punished = {0};
        Tether held = new Tether(a, b, boss, (l, player) -> punished[0]++);
        boolean over = false;
        for (int t = 0; t < Tether.TICKS && !over; t++) over = held.tick(level, fight);
        helper.assertTrue(held.held() && punished[0] == 2, "Kept close, it holds and strikes both");

        Tether pulled = new Tether(a, b, boss, (l, player) -> punished[0]++);
        pulled.tick(level, fight);
        Vec3 away = a.position().add(Tether.SNAP + 2, 0, 0);
        b.snapTo(away.x, away.y, away.z, 0, 0);
        helper.assertTrue(pulled.tick(level, fight) && pulled.snapped() && punished[0] == 2, "Pulled apart, it snaps harmlessly");
        boss.discard();
        helper.succeed();
    }

    @GameTest
    public void moreFightersBringMoreNotMoreHealth(GameTestHelper helper) {
        helper.assertTrue(Bosses.witherAdds(1) == 3 && Bosses.witherAdds(2) == 5 && Bosses.witherAdds(3) == 7,
                "The Wither brings two more skeletons for every fighter beyond the first");
        helper.assertTrue(Bosses.witherAdds(8) == 9, "up to a bound");
        helper.assertTrue(WitchKing.chainWindow(2) > WitchKing.chainWindow(3), "His crystals give a larger group less time");
        helper.assertTrue(de.ipnats.hardwrought.wither.WitherPowers.burden(4) / 4 < de.ipnats.hardwrought.wither.WitherPowers.burden(2) / 2,
                "A burden shared by more is lighter for each");
        Zombie boss = standIn(helper);
        float health = boss.getMaxHealth();
        Encounters.of(boss, boss::position, 16, 0).forceScale(4);
        helper.assertTrue(boss.getMaxHealth() == health, "and the count never touches a boss's health");
        boss.discard();
        helper.succeed();
    }
}
