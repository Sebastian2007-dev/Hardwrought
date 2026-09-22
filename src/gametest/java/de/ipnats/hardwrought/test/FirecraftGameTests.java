package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.metallurgy.BrickFurnaceBlockEntity;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fire, and the first furnace: a campfire is laid rather than lit, and four fired bricks make a bad
 * furnace that is nonetheless the first one a player can build.
 */
public final class FirecraftGameTests {
    @GameTest
    public void aPlacedCampfireIsLaidRatherThanLit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        BlockPos above = ground.above();
        level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());

        ItemStack campfire = new ItemStack(Items.CAMPFIRE);
        player.setItemInHand(InteractionHand.MAIN_HAND, campfire);
        campfire.useOn(context(level, player, campfire, ground, Direction.UP));

        BlockState placed = level.getBlockState(above);
        helper.assertTrue(placed.is(Blocks.CAMPFIRE), "The campfire is placed");
        helper.assertTrue(!placed.getValue(BlockStateProperties.LIT),
                "Laying the wood is the easy half; a placed campfire is not already burning");

        // A village fire is one somebody else has already lit; only placement is touched.
        helper.assertTrue(Blocks.CAMPFIRE.defaultBlockState().getValue(BlockStateProperties.LIT),
                "The default state is untouched, so generated fires still burn");
        helper.succeed();
    }

    @GameTest
    public void fireSticksLightWhatIsLaid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, Blocks.CAMPFIRE.defaultBlockState()
                .setValue(BlockStateProperties.LIT, false));

        ItemStack sticks = new ItemStack(ModItems.LIGHTING_STICKS);
        player.setItemInHand(InteractionHand.MAIN_HAND, sticks);
        helper.assertTrue(sticks.isDamageableItem(), "The sticks wear out with use");
        sticks.useOn(context(level, player, sticks, pos, Direction.UP));

        helper.assertTrue(level.getBlockState(pos).getValue(BlockStateProperties.LIT),
                "Working the sticks against the wood starts the fire");
        // A mock player is always in creative and creative players never wear tools out, so the wear
        // itself is checked where a real one exists, in the client test.
        helper.assertTrue(sticks.getMaxDamage() > 0 && sticks.getMaxDamage() <= 32,
                "And it costs something to do: the sticks are spent long before a steel is");

        // Lighting what is already burning does nothing at all.
        BlockState lit = level.getBlockState(pos);
        helper.assertTrue(sticks.useOn(context(level, player, sticks, pos, Direction.UP))
                        == net.minecraft.world.InteractionResult.PASS,
                "There is nothing to light about a fire that is already lit");
        helper.assertTrue(level.getBlockState(pos) == lit, "And nothing about it changes");

        // A drowned fire cannot be started either.
        level.setBlockAndUpdate(pos, Blocks.CAMPFIRE.defaultBlockState()
                .setValue(BlockStateProperties.LIT, false)
                .setValue(BlockStateProperties.WATERLOGGED, true));
        sticks.useOn(context(level, player, sticks, pos, Direction.UP));
        helper.assertTrue(!level.getBlockState(pos).getValue(BlockStateProperties.LIT),
                "Nothing catches under water");
        helper.succeed();
    }

    @GameTest
    public void theBrickFurnaceClosesTheChainAndIsBadAtIt(GameTestHelper helper) {
        var recipes = helper.getLevel().recipeAccess();
        helper.assertTrue(recipes.byKey(recipe("hardwrought:brick_from_campfire")).isPresent(),
                "Clay is fired into bricks on a campfire, before any furnace exists");
        helper.assertTrue(recipes.byKey(recipe("hardwrought:brick_furnace")).isPresent(),
                "And four bricks make the furnace");
        helper.assertTrue(recipes.byKey(recipe("hardwrought:lighting_sticks")).isPresent(),
                "Two sticks make the means of lighting it");

        // The whole point: both fit in the inventory square, so neither needs a crafting table —
        // which needs an iron hatchet, which needs iron, which needs a furnace.
        helper.assertTrue(fitsInInventoryGrid(helper, "hardwrought:brick_furnace"),
                "Section 56: the first furnace is buildable without a crafting table");
        helper.assertTrue(fitsInInventoryGrid(helper, "hardwrought:lighting_sticks"),
                "And so is the means of lighting it");

        helper.assertTrue(BrickFurnaceBlockEntity.SPEED < 1.0f,
                "A brick furnace smelts more slowly than a proper one");
        helper.assertTrue(BrickFurnaceBlockEntity.FUEL_EFFICIENCY < 1.0f,
                "And gets less out of the same fuel");
        helper.assertTrue(BrickFurnaceBlockEntity.SPEED > 0.2f
                        && BrickFurnaceBlockEntity.FUEL_EFFICIENCY > 0.4f,
                "Bad, but not so bad that nobody would build one");

        helper.assertTrue(ModBlocks.BRICK_FURNACE.asItem().getDescriptionId()
                        .equals(ModBlocks.BRICK_FURNACE.getDescriptionId()),
                "And it is named after the block rather than as a loose item");
        helper.succeed();
    }

    private static boolean fitsInInventoryGrid(GameTestHelper helper, String id) {
        var holder = helper.getLevel().recipeAccess().byKey(recipe(id)).orElse(null);
        if (holder == null) return false;
        for (var display : holder.value().display()) {
            if (display instanceof net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay shaped
                    && (shaped.width() > 2 || shaped.height() > 2)) {
                return false;
            }
        }
        return true;
    }

    private static ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> recipe(String id) {
        return ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
    }

    private static UseOnContext context(ServerLevel level, ServerPlayer player, ItemStack stack,
                                        BlockPos pos, Direction face) {
        return new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
    }
}
