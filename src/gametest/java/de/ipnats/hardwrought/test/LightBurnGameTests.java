package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.environment.BurntLanternBlock;
import de.ipnats.hardwrought.environment.LightBurn;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Lights burn down and can be lit again; see {@link LightBurn}. */
public final class LightBurnGameTests {
    private static final long DAY = 24000;
    private static final net.minecraft.world.level.block.Block WAXED_EXPOSED_COPPER_LANTERN = net.minecraft.core.registries.BuiltInRegistries.BLOCK
            .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("waxed_exposed_copper_lantern"));

    /** Lets the light at a position have burnt this long, then gives it the random tick that checks it. */
    private static boolean burnFor(GameTestHelper helper, BlockPos relative, long ticks) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relative);
        LightBurn.litSince(level, pos, level.getGameTime() - ticks);
        return LightBurn.tick(level.getBlockState(pos), level, pos);
    }

    private static void use(GameTestHelper helper, BlockPos relative, ItemStack stack) {
        @SuppressWarnings("removal") ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        helper.getLevel().getBlockState(pos).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    @GameTest
    public void aTorchBurnsADayAndIsLitAgain(GameTestHelper helper) {
        BlockPos torch = new BlockPos(1, 2, 1);
        helper.setBlock(torch.below(), Blocks.STONE);
        helper.setBlock(torch, Blocks.TORCH);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(LightBurn.litAt(level, helper.absolutePos(torch)) == level.getGameTime(), "A torch set down starts burning at once");
        helper.assertFalse(burnFor(helper, torch, DAY - 1200), "Within its day it burns on");
        helper.assertBlockPresent(Blocks.TORCH, torch);
        helper.assertTrue(burnFor(helper, torch, DAY), "and after a day it goes out");
        helper.assertBlockPresent(ModBlocks.BURNT_TORCH, torch);
        helper.assertTrue(level.getBlockState(helper.absolutePos(torch)).getLightEmission() == 0, "and gives no more light");

        use(helper, torch, new ItemStack(Items.FLINT_AND_STEEL));
        helper.assertBlockPresent(Blocks.TORCH, torch);
        helper.assertTrue(LightBurn.litAt(level, helper.absolutePos(torch)) == level.getGameTime(), "Lit again, it burns a fresh day");
        helper.succeed();
    }

    @GameTest
    public void aSoulTorchOnAWallComesBackAsItself(GameTestHelper helper) {
        BlockPos torch = new BlockPos(1, 2, 1);
        helper.setBlock(torch.north(), Blocks.STONE);
        helper.setBlock(torch, Blocks.SOUL_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        helper.assertTrue(burnFor(helper, torch, DAY), "A soul torch burns a day too");
        BlockState burnt = helper.getBlockState(torch);
        helper.assertTrue(burnt.is(ModBlocks.BURNT_WALL_TORCH) && burnt.getValue(WallTorchBlock.FACING) == Direction.SOUTH,
                "and hangs on its wall burnt out");
        // A burning torch held to it lights it.
        use(helper, torch, new ItemStack(Items.TORCH));
        BlockState lit = helper.getBlockState(torch);
        helper.assertTrue(lit.is(Blocks.SOUL_WALL_TORCH) && lit.getValue(WallTorchBlock.FACING) == Direction.SOUTH,
                "Lit again it is the soul torch it was, facing as it did; was " + lit);
        helper.succeed();
    }

    @GameTest
    public void aLanternBurnsThreeDaysAndKeepsWhatItIs(GameTestHelper helper) {
        BlockPos lantern = new BlockPos(1, 2, 1);
        helper.setBlock(lantern.above(), Blocks.STONE);
        helper.setBlock(lantern, WAXED_EXPOSED_COPPER_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        helper.assertFalse(burnFor(helper, lantern, 2 * DAY), "A lantern outlasts a torch: after two days it still burns");
        helper.assertTrue(burnFor(helper, lantern, 3 * DAY), "after three it goes out");
        BlockState dark = helper.getBlockState(lantern);
        helper.assertTrue(dark.is(ModBlocks.BURNT_LANTERN) && dark.getValue(LanternBlock.HANGING)
                        && dark.getValue(BurntLanternBlock.VARIANT) == BurntLanternBlock.Variant.EXPOSED_COPPER_LANTERN,
                "still hanging, still an exposed copper lantern to look at; was " + dark);
        use(helper, lantern, new ItemStack(Items.FIRE_CHARGE));
        BlockState lit = helper.getBlockState(lantern);
        helper.assertTrue(lit.is(WAXED_EXPOSED_COPPER_LANTERN) && lit.getValue(LanternBlock.HANGING),
                "Lit again it is the very lantern it was, wax and all; was " + lit);
        helper.succeed();
    }

    @GameTest
    public void campfiresCandlesAndPumpkinsGoOutToo(GameTestHelper helper) {
        BlockPos campfire = new BlockPos(1, 2, 1), candle = new BlockPos(3, 2, 1), pumpkin = new BlockPos(5, 2, 1);
        for (BlockPos pos : new BlockPos[] {campfire, candle, pumpkin}) helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(campfire, Blocks.CAMPFIRE);
        helper.setBlock(candle, Blocks.CANDLE.defaultBlockState().setValue(AbstractCandleBlock.LIT, true));
        helper.setBlock(pumpkin, Blocks.JACK_O_LANTERN);
        helper.assertTrue(burnFor(helper, campfire, 2 * DAY), "A campfire burns two days");
        helper.assertFalse(helper.getBlockState(campfire).getValue(CampfireBlock.LIT), "and is a cold campfire after");
        helper.assertTrue(burnFor(helper, candle, 3 * DAY / 2), "A candle burns a day and a half");
        helper.assertFalse(helper.getBlockState(candle).getValue(AbstractCandleBlock.LIT), "and is snuffed after");
        helper.assertTrue(burnFor(helper, pumpkin, 2 * DAY), "A jack o'lantern burns two days");
        helper.assertBlockPresent(Blocks.CARVED_PUMPKIN, pumpkin);
        helper.assertFalse(LightBurn.burns(Blocks.REDSTONE_TORCH.defaultBlockState()), "A redstone torch is no flame and never goes out");
        helper.assertFalse(LightBurn.burns(Blocks.GLOWSTONE.defaultBlockState()), "nor does glowstone");
        helper.succeed();
    }
}
