package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.witchking.Citadel;
import de.ipnats.hardwrought.witchking.HexBlade;
import de.ipnats.hardwrought.witchking.SeatMount;
import de.ipnats.hardwrought.witchking.WitchKing;
import de.ipnats.hardwrought.witchking.WitchKingContent;
import de.ipnats.hardwrought.witchking.WitchKingMantle;
import de.ipnats.hardwrought.witchking.WitchKingSeat;
import de.ipnats.hardwrought.witchking.WitchKingThrone;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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
    public void theMantleHoversUntilItsChargeIsSpentAndGivesOutTooHigh(GameTestHelper helper) {
        var player = (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(WitchKingContent.MANTLE));
        BlockPos air = helper.absolutePos(new BlockPos(2, 6, 2));
        player.snapTo(air.getX() + 0.5, air.getY(), air.getZ() + 0.5, 0, 0);
        player.setOnGround(false);
        de.ipnats.hardwrought.witchking.MantleFlight.requestForTesting(player, true);
        helper.assertTrue(de.ipnats.hardwrought.witchking.MantleFlight.hovering(player), "In the air, at the key, it hovers");
        for (int i = 0; i < de.ipnats.hardwrought.witchking.MantleFlight.HOVER_TICKS / 2; i++) {
            de.ipnats.hardwrought.witchking.MantleFlight.stepForTesting(player);
        }
        float half = de.ipnats.hardwrought.witchking.MantleFlight.chargeForTesting(player);
        helper.assertTrue(half > 0.4f && half < 0.6f, "Hovering spends its charge: " + half);
        for (int i = 0; i < de.ipnats.hardwrought.witchking.MantleFlight.HOVER_TICKS; i++) {
            de.ipnats.hardwrought.witchking.MantleFlight.stepForTesting(player);
        }
        helper.assertFalse(de.ipnats.hardwrought.witchking.MantleFlight.hovering(player), "and spent, it drops its wearer");
        de.ipnats.hardwrought.witchking.MantleFlight.requestForTesting(player, true);
        helper.assertFalse(de.ipnats.hardwrought.witchking.MantleFlight.hovering(player), "nor hovers again until it has filled");
        player.setOnGround(true);
        for (int i = 0; i < de.ipnats.hardwrought.witchking.MantleFlight.REFILL_TICKS; i++) {
            de.ipnats.hardwrought.witchking.MantleFlight.stepForTesting(player);
        }
        helper.assertTrue(de.ipnats.hardwrought.witchking.MantleFlight.chargeForTesting(player) >= 0.99f, "Standing, it fills again");
        player.setOnGround(false);
        de.ipnats.hardwrought.witchking.MantleFlight.requestForTesting(player, true);
        double ceiling = de.ipnats.hardwrought.witchking.MantleFlight.ceiling(player);
        player.snapTo(player.getX(), ceiling + 2, player.getZ(), 0, 0);
        de.ipnats.hardwrought.witchking.MantleFlight.stepForTesting(player);
        helper.assertFalse(de.ipnats.hardwrought.witchking.MantleFlight.hovering(player), "Too high, it gives out");
        helper.succeed();
    }

    @GameTest
    public void hisMagicTearsThroughRunedArmour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var protection = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION);
        var shielded = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        var rent = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
        for (var zombie : java.util.List.of(shielded, rent)) {
            zombie.setNoAi(true);
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack piece = new ItemStack(switch (slot) {
                    case HEAD -> net.minecraft.world.item.Items.DIAMOND_HELMET;
                    case CHEST -> net.minecraft.world.item.Items.DIAMOND_CHESTPLATE;
                    case LEGS -> net.minecraft.world.item.Items.DIAMOND_LEGGINGS;
                    default -> net.minecraft.world.item.Items.DIAMOND_BOOTS;
                });
                piece.enchant(protection, 6);
                zombie.setItemSlot(slot, piece);
            }
        }
        float before = shielded.getHealth();
        shielded.hurtServer(level, level.damageSources().magic(), 10);
        float byMagic = before - shielded.getHealth();
        before = rent.getHealth();
        WitchKing.rend(level, rent, null, 10, 0);
        float byHim = before - rent.getHealth();
        helper.assertTrue(byMagic < 3, "Runes hold off nearly all of other magic: " + byMagic);
        helper.assertTrue(byHim > 5.5f && byHim < 6.5f, "but only half against his: " + byHim);
        before = rent.getHealth();
        rent.damageCooldownTime = 0;
        WitchKing.rend(level, rent, null, 10, 0.5f);
        float blow = before - rent.getHealth();
        helper.assertTrue(blow > 2 && blow < byHim, "Against his blows armour holds, half of it: " + blow);
        helper.succeed();
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
        var runes = de.ipnats.hardwrought.enchanting.RuneEnchanting.suited(level, mantle).stream()
                .map(rune -> rune.unwrapKey().orElseThrow()).toList();
        helper.assertTrue(runes.contains(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING)
                        && runes.contains(net.minecraft.world.item.enchantment.Enchantments.MENDING)
                        && runes.contains(net.minecraft.world.item.enchantment.Enchantments.PROTECTION),
                "Runes can be written on it, as on a chestplate: " + runes);
        helper.succeed();
    }

    @GameTest(maxTicks = 120)
    public void theColdThroneLeadsUpToItsSeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos throne = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(throne, WitchKingContent.THRONE.defaultBlockState().setValue(WitchKingThrone.COLD, true));
        BlockPos home = helper.absolutePos(new BlockPos(6, 1, 6));
        var player = helper.makeMockServerPlayerInLevel();
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, home, 0, 0), true), false);
        level.getBlockState(throne).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(throne), Direction.UP, throne, false));
        helper.assertTrue(player.getVehicle() instanceof SeatMount, "One sits down on the cold throne");
        helper.runAfterDelay(60, () -> {
            BlockPos seat = ((WitchKingThrone.Entity) level.getBlockEntity(throne)).seat();
            helper.assertTrue(seat != null && level.getBlockState(seat).is(WitchKingContent.SEAT), "Sat on, it sets its seat down: " + seat);
            helper.assertTrue(seat.distManhattan(home) <= 12, "where the player sleeps: " + seat + " / " + home);
            helper.assertTrue(throne.equals(((WitchKingSeat.Entity) level.getBlockEntity(seat)).throne()), "bound to the throne");
            helper.assertTrue(!player.isPassenger() && player.position().distanceTo(Vec3.atCenterOf(seat)) < 6,
                    "and carries the player up beside it: " + player.position());
            player.setShiftKeyDown(true);
            level.getBlockState(seat).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(seat), Direction.UP, seat, false));
            helper.assertTrue(level.getBlockState(seat).isAir(), "Sneaking picks the seat up");
            ItemStack carried = ItemStack.EMPTY;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (player.getInventory().getItem(slot).is(WitchKingContent.SEAT_ITEM)) carried = player.getInventory().getItem(slot);
            }
            helper.assertTrue(carried.is(WitchKingContent.SEAT_ITEM) && throne.equals(carried.get(WitchKingContent.THRONE_LINK)),
                    "still bound to its throne: " + carried);
            helper.succeed();
        });
    }
}
