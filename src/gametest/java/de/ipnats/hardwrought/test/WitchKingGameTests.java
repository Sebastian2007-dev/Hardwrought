package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.witchking.Citadel;
import de.ipnats.hardwrought.witchking.HexBlade;
import de.ipnats.hardwrought.witchking.WitchKing;
import de.ipnats.hardwrought.witchking.WitchKingContent;
import de.ipnats.hardwrought.witchking.WitchKingMantle;
import de.ipnats.hardwrought.witchking.WitchKingThrone;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The Witch King: his three phases, his crystals, his citadel and throne, his swords, his mantle. */
public final class WitchKingGameTests {
    private static WitchKing king(GameTestHelper helper, BlockPos home) {
        ServerLevel level = helper.getLevel();
        WitchKing king = WitchKingContent.WITCH_KING.create(level, EntitySpawnReason.COMMAND);
        king.snapTo(home.getX() + 0.5, home.getY() + 3, home.getZ() + 0.5, 0, 0);
        king.settle(home, null);
        level.addFreshEntity(king);
        return king;
    }

    @GameTest(maxTicks = 200)
    public void theWitchKingFightsInThreePhases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos home = helper.absolutePos(new BlockPos(4, 1, 4));
        WitchKing king = king(helper, home);
        var player = helper.makeMockServerPlayerInLevel();
        float before = king.getHealth();
        king.hurtServer(level, level.damageSources().playerAttack(player), 20);
        float byHand = before - king.getHealth();
        helper.assertTrue(byHand > 0 && byHand <= 5.01f, "In his first phase a blow of the hand barely touches him: " + byHand);
        before = king.getHealth();
        king.damageCooldownTime = 0;
        king.hurtServer(level, level.damageSources().magic(), 20);
        helper.assertTrue(before - king.getHealth() > 15, "what comes from afar does");
        king.testHealth(0.7f);
        king.damageCooldownTime = 0;
        king.hurtServer(level, level.damageSources().magic(), 30);
        helper.assertTrue(king.phase() == 2, "Below two thirds he is warded: phase " + king.phase());
        helper.assertTrue(Math.abs(king.getHealth() - king.getMaxHealth() * WitchKing.PHASE_TWO_AT) < 0.5f, "his health held at two thirds");
        for (BlockPos spot : WitchKing.CRYSTAL_SPOTS) {
            helper.assertTrue(level.getBlockState(home.offset(spot)).is(WitchKingContent.CRYSTAL), "a crystal raised at " + spot);
        }
        before = king.getHealth();
        helper.assertTrue(!king.hurtServer(level, level.damageSources().magic(), 30) && king.getHealth() == before, "nothing gets through the ward");
        for (BlockPos spot : WitchKing.CRYSTAL_SPOTS) level.destroyBlock(home.offset(spot), false);
        helper.succeedWhen(() -> {
            helper.assertTrue(king.phase() == 3, "The crystals broken, the ward breaks");
            helper.assertTrue(king.getScale() > WitchKing.GIANT - 0.05, "and he grows to a giant: " + king.getScale());
            float health = king.getHealth();
            king.damageCooldownTime = 0;
            king.hurtServer(level, level.damageSources().playerAttack(player), 10);
            helper.assertTrue(health - king.getHealth() > 2, "who can be struck by hand");
            king.discard();
        });
    }

    @GameTest
    public void aSpectralSwordStrikesWithMagic(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 6));
        zombie.setNoAi(true);
        HexBlade blade = WitchKingContent.HEX_BLADE.create(level, EntitySpawnReason.COMMAND);
        BlockPos from = helper.absolutePos(new BlockPos(2, 2, 1));
        blade.setPos(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        level.addFreshEntity(blade);
        blade.loose(zombie.getEyePosition().subtract(blade.position()), 1.2);
        float health = zombie.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getHealth() < health, "The sword strikes what it flies at");
            helper.assertTrue(!blade.isAlive(), "and is gone");
        });
    }

    @GameTest(maxTicks = 200)
    public void theCitadelWakesItsKing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos home = helper.absolutePos(new BlockPos(4, 60, 4));
        Citadel.build(level, home, RandomSource.create(4));
        BlockPos throne = home.offset(0, 1, -Citadel.THRONE_DISTANCE);
        helper.assertTrue(level.getBlockState(throne).is(WitchKingContent.THRONE), "The throne stands at the head of the hall");
        helper.assertTrue(WitchKingThrone.home(throne).equals(home), "looking down the hall to its middle");
        for (BlockPos spot : WitchKing.CRYSTAL_SPOTS) {
            helper.assertTrue(level.getBlockState(home.offset(spot.getX(), 0, spot.getZ())).is(Blocks.CHISELED_POLISHED_BLACKSTONE), "a pedestal at " + spot);
        }
        helper.assertTrue(level.getBlockState(home.above(5)).isAir() && !level.getBlockState(home.above(Citadel.HEIGHT)).isAir(), "a hall under a roof");
        var seat = (WitchKingThrone.Entity) level.getBlockEntity(throne);
        seat.wake(level);
        helper.succeedWhen(() -> {
            var kings = level.getEntitiesOfClass(WitchKing.class, new AABB(home).inflate(24));
            helper.assertTrue(kings.size() == 1, "Woken, he rises: " + kings.size());
            helper.assertTrue(seat.state() == WitchKingThrone.Entity.AWAKE, "and the throne is empty");
            kings.forEach(WitchKing::discard);
            for (BlockPos pos : BlockPos.betweenClosed(home.offset(-24, -3, -24), home.offset(24, Citadel.HEIGHT + 2, 24))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        });
    }

    @GameTest
    public void theMantleDrivesItsWearerOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        ItemStack mantle = new ItemStack(WitchKingContent.MANTLE);
        player.setItemSlot(EquipmentSlot.CHEST, mantle);
        player.setDeltaMovement(Vec3.ZERO);
        player.setXRot(0);
        player.setYRot(0);
        for (int i = 0; i < 24; i++) WitchKingMantle.drive(player, level, player.getItemBySlot(EquipmentSlot.CHEST));
        helper.assertTrue(player.getDeltaMovement().z > 1.0, "It drives on along the gaze: " + player.getDeltaMovement());
        helper.assertTrue(mantle.has(net.minecraft.core.component.DataComponents.GLIDER), "and glides as elytra do");
        helper.succeed();
    }
}
