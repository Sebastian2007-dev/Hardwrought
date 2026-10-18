package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.survival.GroundSpawn;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

/** Nobody starts in the crown of a tree; see {@link GroundSpawn}. */
public final class SpawnGameTests {
    @GameTest
    public void aPlayerInATreeIsSetDownOnTheGround(GameTestHelper helper) {
        // Stone ground, a trunk four high, a crown of leaves round its top.
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        for (int y = 2; y <= 5; y++) helper.setBlock(new BlockPos(2, y, 2), Blocks.OAK_LOG);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(x, 6, z), Blocks.OAK_LEAVES);
        }
        @SuppressWarnings("removal") ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos crown = helper.absolutePos(new BlockPos(2, 7, 2));
        player.teleportTo(crown.getX() + 0.5, crown.getY(), crown.getZ() + 0.5);
        helper.assertTrue(GroundSpawn.inTree(helper.getLevel(), player.blockPosition()), "Standing on the leaves is standing in a tree");
        helper.assertTrue(GroundSpawn.settle(player), "and the player is moved");
        BlockPos feet = player.blockPosition();
        helper.assertTrue(feet.getY() == helper.absolutePos(new BlockPos(0, 2, 0)).getY(),
                "down onto the ground: " + feet + ", the stone is at " + helper.absolutePos(new BlockPos(0, 1, 0)).getY());
        helper.assertFalse(helper.getLevel().getBlockState(feet.below()).is(BlockTags.LOGS)
                || helper.getLevel().getBlockState(feet.below()).is(BlockTags.LEAVES), "beside the trunk, not on it");
        helper.assertFalse(GroundSpawn.settle(player), "and a player on the ground stays where they are");
        helper.succeed();
    }
}
