package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.electricity.MachineBlock;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import de.ipnats.hardwrought.electricity.BatteryBlockEntity;
import de.ipnats.hardwrought.electricity.DynamoBlock;
import de.ipnats.hardwrought.electricity.ElectricBlocks;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.Gauge;
import de.ipnats.hardwrought.electricity.MastBlock;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Section 76 on screen: a dynamo on a crank box, a line strung over two masts, a battery with a lamp
 * on it and a crusher at work. Photographed by day and by night, because a wire drawn to the wrong corner of a block or a
 * lamp that gives light without looking lit passes every server test there is.
 */
public final class ElectricityClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("time set 6000");
            BlockPos[] opened = new BlockPos[1];
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                player.teleportTo(level, player.getX(), player.getY(), player.getZ(), java.util.Set.of(), 180.0f, -8.0f, true);
                BlockPos base = player.blockPosition().north(9).above();
                BlockPos dynamo = base.west(5);
                level.setBlockAndUpdate(dynamo, ElectricBlocks.DYNAMO.defaultBlockState().setValue(DynamoBlock.FACING, Direction.EAST));
                level.setBlockAndUpdate(dynamo.west(), ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
                // Two masts, each set up whole.
                BlockPos[] heads = {base.west(3).above(3), base.east(4).above(3)};
                for (BlockPos head : heads) {
                    for (int i = 0; i < MastBlock.HEIGHT; i++) {
                        level.setBlockAndUpdate(head.below(3 - i), ElectricBlocks.MAST.defaultBlockState().setValue(MastBlock.SEGMENT, i));
                    }
                }
                // Under the first a battery with a lamp on it, under the second a crusher at work.
                BlockPos battery = base.west(1);
                level.setBlockAndUpdate(battery, ElectricBlocks.BATTERY.defaultBlockState());
                ((BatteryBlockEntity) level.getBlockEntity(battery)).setCharge(BatteryBlockEntity.CAPACITY * 0.5);
                BlockPos lamp = base.east(1);
                level.setBlockAndUpdate(lamp, ElectricBlocks.ELECTRIC_LAMP.defaultBlockState());
                BlockPos crusher = base.east(2).south(3);
                level.setBlockAndUpdate(crusher, ElectricBlocks.BASIC_CRUSHER.defaultBlockState()
                        .setValue(MachineBlock.FACING, Direction.SOUTH));
                ((MachineBlockEntity) level.getBlockEntity(crusher)).insertFrom(
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON, 64), true);
                // Each of them takes its wire through a copper coil set on it.
                for (BlockPos seat : new BlockPos[] {dynamo, battery, crusher}) {
                    level.setBlockAndUpdate(seat.above(), ElectricBlocks.COPPER_COIL.defaultBlockState());
                }
                Electricity.connect(level, dynamo.above(), heads[0], Gauge.WIRE);
                Electricity.connect(level, heads[0], heads[1], Gauge.CABLE);
                Electricity.connect(level, heads[0], battery.above(), Gauge.WIRE);
                Electricity.connect(level, battery.above(), lamp, Gauge.WIRE);
                Electricity.connect(level, heads[1], crusher.above(), Gauge.WIRE);
                opened[0] = crusher;
            });
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-electricity-day");
            world.getServer().runCommand("time set 114000");
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-electricity-night");

            // The crusher opened: ore going through it, and the voltmeter beside the slots.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.openMenu((MachineBlockEntity) player.level().getBlockEntity(opened[0]));
            });
            context.waitTicks(15);
            context.takeScreenshot("hardwrought-machine-screen");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }
}
