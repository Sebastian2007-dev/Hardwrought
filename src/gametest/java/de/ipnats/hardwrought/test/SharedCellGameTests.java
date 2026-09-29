package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.building.BuildingPhysics;
import de.ipnats.hardwrought.building.BuiltBlocks;
import de.ipnats.hardwrought.building.SharedCell;
import de.ipnats.hardwrought.building.SharedCellBlock;
import de.ipnats.hardwrought.building.SharedCellBlockEntity;
import de.ipnats.hardwrought.core.registry.ModBlocks;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 17: stairs, slabs, doors and trapdoors no longer keep panes, bars and carpets out of their
 * cell. The two share it, each keeping its own shape and behaviour.
 */
public final class SharedCellGameTests {
    @GameTest
    public void carpetsLieOnTheFloorTheHalfBlockLeaves(GameTestHelper helper) {
        helper.assertTrue(SharedCell.carpetFloor(Blocks.OAK_SLAB.defaultBlockState()) == 0.5,
                "On a bottom slab a carpet lies half a block up, on the slab");
        helper.assertTrue(SharedCell.carpetFloor(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)) == 0,
                "under a top slab, on the floor");
        helper.assertTrue(SharedCell.carpetFloor(Blocks.OAK_STAIRS.defaultBlockState()) == 0.5,
                "on a stair, on its lower step");
        helper.assertTrue(SharedCell.carpetFloor(Blocks.OAK_DOOR.defaultBlockState()) == 0,
                "through a doorway, on the floor");
        helper.assertTrue(SharedCell.carpetFloor(Blocks.OAK_TRAPDOOR.defaultBlockState()) == 3.0 / 16,
                "and on a closed trapdoor, on its boards");
        helper.assertFalse(SharedCell.canHost(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)),
                "A double slab is a whole block: nothing shares it");
        helper.assertFalse(SharedCell.canHost(Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)),
                "nor a stair standing in water");
        helper.succeed();
    }

    @GameTest
    public void aPaneGoesIntoAStairAndItsNeighboursJoinIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stair = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos wall = stair.west();
        level.setBlockAndUpdate(stair.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(stair, Blocks.BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(wall, Blocks.BRICKS.defaultBlockState());
        BuildingPhysics.placed(level, stair, level.getBlockState(stair));

        // Clicking the brick wall's east face, which borders the stair's cell: vanilla would refuse.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_PANE, 2));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(wall).add(0.5, 0, 0), Direction.EAST, wall, false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);

        BlockState cell = level.getBlockState(stair);
        helper.assertTrue(cell.is(ModBlocks.SHARED_CELL) && cell.getValue(SharedCellBlock.CONNECTS),
                "The pane went into the stair's cell: " + cell);
        SharedCellBlockEntity entity = (SharedCellBlockEntity) level.getBlockEntity(stair);
        helper.assertTrue(entity.host().is(Blocks.BRICK_STAIRS) && entity.insert().is(Blocks.GLASS_PANE),
                "and both are kept: the stair as it was, the pane beside it");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "One pane was used");
        helper.assertTrue(BuiltBlocks.isBuilt(level, stair), "The stair is still built");

        BlockPos neighbour = stair.east();
        level.setBlockAndUpdate(neighbour, Blocks.GLASS_PANE.defaultBlockState());
        level.setBlockAndUpdate(neighbour, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(
                level.getBlockState(neighbour), level, neighbour));
        helper.assertTrue(level.getBlockState(neighbour).getValue(IronBarsBlock.WEST),
                "A pane beside it joins onto the pane inside the stair");

        player.gameMode.destroyBlock(stair);
        helper.assertTrue(level.getBlockState(stair).is(Blocks.BRICK_STAIRS),
                "Breaking the cell takes the pane out and leaves the stair");
        helper.assertTrue(BuiltBlocks.isBuilt(level, stair), "still built");
        helper.succeed();
    }

    @GameTest
    public void aCarpetLiesOnASlabInsteadOfFloatingAboveIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(slab.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(slab, Blocks.OAK_SLAB.defaultBlockState());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARPET.red()));
        // The top of a bottom slab is inside its cell, half a block up.
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(slab), Direction.UP, slab, false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(level.getBlockState(slab).is(ModBlocks.SHARED_CELL) && level.getBlockState(slab.above()).isAir(),
                "The carpet lies in the slab's cell, not in the air above it");
        var shape = level.getBlockState(slab).getCollisionShape(level, slab);
        helper.assertTrue(Math.abs(shape.max(Direction.Axis.Y) - (0.5 + 1.0 / 16)) < 1e-6,
                "and it is walked on at the top of the slab: " + shape.max(Direction.Axis.Y));
        helper.succeed();
    }

    @GameTest
    public void aDoorWithACarpetInItStillOpensAsOneDoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(lower.below(), Blocks.STONE.defaultBlockState());
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        level.setBlock(lower, door, 3);
        level.setBlock(lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        SharedCell.combine(level, lower, level.getBlockState(lower), Blocks.CARPET.blue().defaultBlockState());
        helper.assertTrue(level.getBlockState(lower.above()).is(Blocks.OAK_DOOR),
                "The upper half of the door recognises its lower half inside the shared cell");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(lower), Direction.NORTH, lower, false);
        level.getBlockState(lower).useWithoutItem(level, player, hit);
        SharedCellBlockEntity entity = (SharedCellBlockEntity) level.getBlockEntity(lower);
        helper.assertTrue(entity.host().getValue(DoorBlock.OPEN) && level.getBlockState(lower.above()).getValue(DoorBlock.OPEN),
                "Opening it opens both halves");
        helper.succeed();
    }

    @GameTest
    public void aTrapdoorKeepsItsCarpetAndItsHinge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM));
        SharedCell.combine(level, pos, level.getBlockState(pos), Blocks.CARPET.white().defaultBlockState());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        level.getBlockState(pos).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        SharedCellBlockEntity entity = (SharedCellBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(entity.host().getValue(TrapDoorBlock.OPEN) && entity.insert().is(Blocks.CARPET.white()),
                "A trapdoor opens with the carpet still in its cell");
        helper.succeed();
    }
}
