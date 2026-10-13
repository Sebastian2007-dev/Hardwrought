package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.electricity.ElectricBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Blocks that come apart as one thing, and loot tables written the way the game still reads them. */
public final class LootTableGameTests {
    @GameTest(maxTicks = 40)
    public void aMastBrokenAnywhereGivesOneMast(GameTestHelper helper) {
        BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        @SuppressWarnings("removal") ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ElectricBlocks.MAST.asItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false)));
        helper.assertTrue(helper.getLevel().getBlockState(ground.above(4)).is(ElectricBlocks.MAST), "The mast stands four blocks tall");

        // Taken down by its middle, as a player reaching up would.
        player.gameMode.destroyBlock(ground.above(2));
        for (int i = 1; i <= 4; i++) {
            helper.assertTrue(helper.getLevel().getBlockState(ground.above(i)).isAir(), "The whole mast comes down");
        }
        helper.runAfterDelay(2, () -> {
            int masts = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(ground).inflate(4)).stream()
                    .filter(e -> e.getItem().is(ElectricBlocks.MAST.asItem())).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(masts == 1, "and gives back the one mast it was, not one per block: " + masts);
            helper.succeed();
        });
    }

    /**
     * Since 26.x a loot table says {@code "condition"} and {@code "modifier"}; the old {@code "conditions"}
     * and {@code "functions"} are passed over without a word, so a table written so drops what it should not.
     */
    @GameTest
    public void lootTablesAreWrittenAsTheGameReadsThem(GameTestHelper helper) throws IOException {
        List<String> stale = new ArrayList<>();
        Map<Identifier, net.minecraft.server.packs.resources.Resource> tables = helper.getLevel().getServer().getResourceManager()
                .listResources("loot_table", id -> id.getNamespace().equals("hardwrought") && id.getPath().endsWith(".json"));
        for (var entry : tables.entrySet()) {
            String text;
            try (var in = entry.getValue().open()) {
                text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            if (text.contains("\"conditions\"") || text.contains("\"functions\"") || text.contains("block_state_property")) {
                stale.add(entry.getKey().toString());
            }
        }
        helper.assertTrue(!tables.isEmpty(), "The mod's loot tables are found");
        helper.assertTrue(stale.isEmpty(), "No loot table in the old form: " + stale);
        helper.succeed();
    }
}
