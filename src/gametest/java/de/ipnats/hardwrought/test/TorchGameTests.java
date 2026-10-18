package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.environment.LightBurn;
import de.ipnats.hardwrought.environment.Torches;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Torches are made unlit, are lit, and burn down wherever they are carried; see {@link Torches}. */
public final class TorchGameTests {
    @SuppressWarnings("removal")
    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static void useOn(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    @GameTest
    public void theGridMakesTorchesUnlit(GameTestHelper helper) {
        var recipe = helper.getLevel().getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("torch"))).orElseThrow();
        var result = recipe.value().display().getFirst().result()
                .resolveForFirstStack(net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(helper.getLevel()));
        helper.assertTrue(result.is(ModItems.UNLIT_TORCH) && result.getCount() == 4,
                "Coal on a stick makes four unlit torches, not burning ones: " + result);
        helper.succeed();
    }

    @GameTest
    public void aTorchBurnsDownInTheBagAndAPlainOneIsUnlit(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        player.getInventory().setItem(3, new ItemStack(Items.TORCH, 5));
        player.getInventory().setItem(4, Torches.lit());
        player.getInventory().setItem(5, Torches.lit(Torches.BURN_SECONDS - 1));
        Torches.burnCarried(player);
        helper.assertTrue(player.getInventory().getItem(3).is(ModItems.UNLIT_TORCH)
                        && player.getInventory().getItem(3).getCount() == 5,
                "Torches nobody lit are unlit torches in a player's hands");
        helper.assertTrue(Torches.burnt(player.getInventory().getItem(4)) == 1,
                "A burning torch in the bag has burnt a second more");
        helper.assertTrue(player.getInventory().getItem(5).is(Items.STICK),
                "and one at the end of its time is a stick");
        helper.assertTrue(Torches.lit().getMaxStackSize() == 1, "A burning torch does not stack");
        helper.succeed();
    }

    @GameTest
    public void anUnlitTorchIsLitAtAFireOrWithFlintAndSteel(GameTestHelper helper) {
        BlockPos fire = new BlockPos(1, 1, 1);
        helper.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        ServerPlayer player = survivor(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.UNLIT_TORCH, 3));
        useOn(helper, player, fire);
        helper.assertTrue(player.getMainHandItem().is(ModItems.UNLIT_TORCH) && player.getMainHandItem().getCount() == 2,
                "One torch of the stack is held to the fire");
        helper.assertTrue(player.getInventory().contains(stack -> Torches.isLit(stack)), "and it burns");

        ServerPlayer other = survivor(helper);
        other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.UNLIT_TORCH));
        other.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        other.gameMode.useItem(other, helper.getLevel(), other.getMainHandItem(), InteractionHand.MAIN_HAND);
        helper.assertTrue(Torches.isLit(other.getMainHandItem()), "Flint and steel in the other hand lights the last one in hand");
        helper.succeed();
    }

    @GameTest
    public void aBurningTorchLightsALaidCampfire(GameTestHelper helper) {
        BlockPos fire = new BlockPos(1, 1, 1);
        helper.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        ServerPlayer player = survivor(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, Torches.lit());
        useOn(helper, player, fire);
        helper.assertTrue(helper.getBlockState(fire).getValue(CampfireBlock.LIT), "The torch lights the campfire");
        helper.assertTrue(Torches.isLit(player.getMainHandItem()), "and is still in hand, not set down on it");
        helper.succeed();
    }

    @GameTest
    public void anUnlitTorchIsSetDownLitThereAndTakenDownBurning(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = new BlockPos(1, 1, 1), torch = ground.above();
        helper.setBlock(ground, Blocks.STONE);
        ServerPlayer player = survivor(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.UNLIT_TORCH, 2));
        useOn(helper, player, ground);
        helper.assertTrue(helper.getBlockState(torch).is(de.ipnats.hardwrought.core.registry.ModBlocks.UNLIT_TORCH),
                "Set against the ground, an unlit torch stands there unlit");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        useOn(helper, player, torch);
        helper.assertTrue(helper.getBlockState(torch).is(Blocks.TORCH), "Flint and steel lights it where it stands");
        BlockPos pos = helper.absolutePos(torch);
        LightBurn.litSince(level, pos, level.getGameTime() - 300 * 20L);
        var drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(pos), level, pos, null);
        helper.assertTrue(drops.size() == 1 && Torches.isLit(drops.getFirst()) && Torches.burnt(drops.getFirst()) == 300,
                "Taken down after burning a quarter of a day, it comes off burning with the rest: " + drops + " lit=" + Torches.isLit(drops.getFirst()) + " burnt=" + Torches.burnt(drops.getFirst()) + " litAt=" + LightBurn.litAt(level, pos) + " now=" + level.getGameTime());
        LightBurn.litSince(level, pos, level.getGameTime() - Torches.BURN_SECONDS * 20L - 20);
        drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(pos), level, pos, null);
        helper.assertTrue(drops.getFirst().is(Items.STICK), "and one past its time is a stick");
        helper.setBlock(torch, de.ipnats.hardwrought.core.registry.ModBlocks.UNLIT_TORCH);
        drops = net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(torch), level, pos, null);
        helper.assertTrue(drops.getFirst().is(ModItems.UNLIT_TORCH), "An unlit one gives back an unlit torch");
        helper.succeed();
    }

    @GameTest
    public void aTorchSetDownBurnsOnForWhatItHadLeft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos torch = new BlockPos(1, 2, 1);
        helper.setBlock(torch.below(), Blocks.STONE);
        helper.setBlock(torch, Blocks.TORCH);
        BlockPos pos = helper.absolutePos(torch);
        Torches.placed(level, pos, Torches.lit(600));
        helper.assertTrue(LightBurn.litAt(level, pos) == level.getGameTime() - 600 * 20L,
                "Half burnt in the hand, it has half a day left on the wall");
        helper.succeed();
    }
}
