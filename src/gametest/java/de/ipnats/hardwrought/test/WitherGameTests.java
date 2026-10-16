package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModEffects;
import de.ipnats.hardwrought.wither.SoulSkulls;
import de.ipnats.hardwrought.wither.WitherPowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.phys.AABB;

/** The Wither: soul-charged skulls, its storm, and being walled in. */
public final class WitherGameTests {
    @GameTest
    public void aSkullInSoulFireChargesUnburnt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fire = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(fire.below(), Blocks.SOUL_SOIL.defaultBlockState(), 2);
        level.setBlock(fire, Blocks.SOUL_FIRE.defaultBlockState(), 2);
        ItemEntity skull = new ItemEntity(level, fire.getX() + 0.5, fire.getY() + 0.1, fire.getZ() + 0.5,
                new ItemStack(Items.WITHER_SKELETON_SKULL), 0, 0, 0);
        level.addFreshEntity(skull);
        helper.assertFalse(skull.hurtServer(level, level.damageSources().inFire(), 1.0f), "Soul fire does not burn a skull lying in it");
        for (int step = 0; step < SoulSkulls.CHARGE_TICKS / 10 - 1; step++) SoulSkulls.charge(skull);
        helper.assertFalse(SoulSkulls.charged(skull.getItem()), "Not before its half minute is up");
        SoulSkulls.charge(skull);
        helper.assertTrue(SoulSkulls.charged(skull.getItem()), "and charged when it is");
        skull.discard();
        helper.succeed();
    }

    @GameTest
    public void onlyChargedSkullsRaiseTheWither(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        // Soul sand in a T, three skulls on top.
        level.setBlock(base, Blocks.SOUL_SAND.defaultBlockState(), 2);
        for (int x = -1; x <= 1; x++) level.setBlock(base.offset(x, 1, 0), Blocks.SOUL_SAND.defaultBlockState(), 2);
        for (int x = -1; x <= 1; x++) level.setBlock(base.offset(x, 2, 0), Blocks.WITHER_SKELETON_SKULL.defaultBlockState(), 2);
        AABB around = new AABB(base).inflate(8);
        WitherSkullBlock.checkSpawn(level, base.offset(1, 2, 0));
        helper.assertTrue(level.getEntitiesOfClass(WitherBoss.class, around).isEmpty(), "Empty skulls raise nothing");
        for (int x = -1; x <= 1; x++) {
            var entity = level.getBlockEntity(base.offset(x, 2, 0));
            entity.setComponents(DataComponentMap.builder().set(SoulSkulls.CHARGED, Unit.INSTANCE).build());
        }
        WitherSkullBlock.checkSpawn(level, base.offset(1, 2, 0));
        var withers = level.getEntitiesOfClass(WitherBoss.class, around);
        helper.assertTrue(withers.size() == 1, "Three charged skulls raise the Wither");
        withers.forEach(WitherBoss::discard);
        helper.succeed();
    }

    @GameTest
    public void theWithersStormDrainsThoseNearIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        WitherBoss wither = EntityTypes.WITHER.create(level, EntitySpawnReason.EVENT);
        wither.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        wither.setNoAi(true);
        level.addFreshEntity(wither);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(at.getX() + 3, at.getY(), at.getZ());
        WitherPowers.storm(wither, level);
        helper.assertTrue(player.hasEffect(ModEffects.DRAIN), "Its storm drains a player beside it");
        helper.assertFalse(WitherPowers.trapped(wither, level), "In the open it is not walled in");
        wither.discard();
        helper.succeed();
    }
}
