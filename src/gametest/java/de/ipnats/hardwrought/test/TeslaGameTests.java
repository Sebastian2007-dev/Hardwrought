package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.carried.CarriedItems;
import de.ipnats.hardwrought.carried.Cells;
import de.ipnats.hardwrought.haven.Appliance;
import de.ipnats.hardwrought.haven.ApplianceBlock;
import de.ipnats.hardwrought.haven.HavenBlocks;
import de.ipnats.hardwrought.tesla.TeslaBlocks;
import de.ipnats.hardwrought.tesla.TeslaTowerBlockEntity;
import de.ipnats.hardwrought.tesla.TeslaTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The Tesla tower (docs/electricity-roadmap.md, stage E6): wireless charge, wireless lamps and its defence. */
public final class TeslaGameTests {
    /** A tower built whole at this foot, its bank filled; returns the foot. */
    private static BlockPos tower(ServerLevel level, List<BlockPos> used, BlockPos foot, boolean whole) {
        place(level, used, foot, HavenBlocks.of(Appliance.TESLA_TOWER).defaultBlockState().setValue(ApplianceBlock.FACING, Direction.NORTH));
        for (int i = 1; i <= TeslaTowerBlockEntity.COILS; i++) place(level, used, foot.above(i), TeslaBlocks.RESONANT_COIL.defaultBlockState());
        if (whole) place(level, used, foot.above(TeslaTowerBlockEntity.COILS + 1), TeslaBlocks.TOP_LOAD.defaultBlockState());
        place(level, used, foot.east(), TeslaBlocks.CAPACITOR_BANK.defaultBlockState());
        ((TeslaTowerBlockEntity) level.getBlockEntity(foot)).fill(TeslaTowerBlockEntity.BANK_JOULES);
        return foot;
    }

    @GameTest(maxTicks = 120)
    public void aTowerChargesTheCellsInItsReachAndLightsLampsWithoutWire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(180);
        List<BlockPos> used = new ArrayList<>();
        BlockPos foot = tower(level, used, base, true);
        var near = helper.makeMockServerPlayerInLevel();
        near.teleportTo(base.getX() + 6.5, base.getY(), base.getZ() + 0.5);
        var far = helper.makeMockServerPlayerInLevel();
        far.teleportTo(base.getX() + 40.5, base.getY(), base.getZ() + 0.5);
        for (var player : List.of(near, far)) {
            ItemStack cell = new ItemStack(CarriedItems.LEAD_ACID_PACK);
            cell.set(Cells.CHARGE, 0.0f);
            player.getInventory().setItem(0, cell);
        }
        BlockPos lamp = base.offset(0, 0, 8), farLamp = base.offset(0, 0, 40);
        place(level, used, lamp, HavenBlocks.of(Appliance.ELECTRIC_LIGHT).defaultBlockState());
        place(level, used, farLamp, HavenBlocks.of(Appliance.ELECTRIC_LIGHT).defaultBlockState());
        helper.runAfterDelay(80, () -> {
            try {
                var tower = (TeslaTowerBlockEntity) level.getBlockEntity(foot);
                helper.assertTrue(tower.whole(), "Built whole, the tower works");
                helper.assertTrue(TeslaTowers.near(level, Vec3.atCenterOf(lamp)) == tower, "and reaches what is round it");
                helper.assertTrue(Cells.charge(near.getInventory().getItem(0)) > 0.0, "A cell carried within its reach charges without wire");
                helper.assertTrue(Cells.charge(far.getInventory().getItem(0)) == 0.0, "and one outside it does not");
                helper.assertTrue(level.getBlockState(lamp).getValue(ApplianceBlock.ON), "An electric light in reach burns with no line to it");
                helper.assertTrue(!level.getBlockState(farLamp).getValue(ApplianceBlock.ON), "and one beyond it is dark");
                helper.assertTrue(tower.bank() < TeslaTowerBlockEntity.BANK_JOULES, "all of it spent from the bank");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void aTowerStrikesAMonsterInReachAtTheCostOfItsBank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(230);
        List<BlockPos> used = new ArrayList<>();
        BlockPos foot = tower(level, used, base, true);
        for (int x = 3; x <= 9; x++) for (int z = -2; z <= 2; z++) place(level, used, base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        var zombie = EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        zombie.snapTo(base.getX() + 6.5, base.getY(), base.getZ() + 0.5, 0, 0);
        zombie.setNoAi(true);
        level.addFreshEntity(zombie);
        float health = zombie.getHealth();
        helper.runAfterDelay(60, () -> {
            try {
                var tower = (TeslaTowerBlockEntity) level.getBlockEntity(foot);
                helper.assertTrue(zombie.getHealth() < health || !zombie.isAlive(), "A monster in reach is struck: " + zombie.getHealth());
                helper.assertTrue(tower.bank() <= TeslaTowerBlockEntity.BANK_JOULES - TeslaTowerBlockEntity.STRIKE_JOULES,
                        "and every strike costs the bank: " + tower.bank());
            } finally {
                zombie.discard();
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void aTowerNotBuiltWholeDoesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(280);
        List<BlockPos> used = new ArrayList<>();
        BlockPos foot = tower(level, used, base, false);
        BlockPos lamp = base.offset(0, 0, 5);
        place(level, used, lamp, HavenBlocks.of(Appliance.ELECTRIC_LIGHT).defaultBlockState());
        helper.runAfterDelay(50, () -> {
            try {
                var tower = (TeslaTowerBlockEntity) level.getBlockEntity(foot);
                helper.assertTrue(!tower.whole() && tower.note() != null, "Without its top load the tower is not whole, and says so");
                helper.assertTrue(!level.getBlockState(lamp).getValue(ApplianceBlock.ON), "and lights nothing");
                helper.assertTrue(TeslaTowerBlockEntity.wearsMetal(helper.makeMockServerPlayerInLevel()) == false, "A bare player wears no metal");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    private static void place(ServerLevel level, List<BlockPos> used, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
        used.add(pos.immutable());
    }

    private static void clear(ServerLevel level, List<BlockPos> used) {
        for (int i = used.size() - 1; i >= 0; i--) {
            level.setBlock(used.get(i), Blocks.AIR.defaultBlockState(), 2 | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        }
    }
}
