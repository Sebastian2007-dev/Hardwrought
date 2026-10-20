package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.automation.AssemblerBlockEntity;
import de.ipnats.hardwrought.automation.AutomationBlocks;
import de.ipnats.hardwrought.automation.ConveyorBlock;
import de.ipnats.hardwrought.automation.ConveyorBlockEntity;
import de.ipnats.hardwrought.automation.DrawingItem;
import de.ipnats.hardwrought.carried.CarriedItems;
import de.ipnats.hardwrought.core.networking.StoragePayloads;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.electricity.DynamoBlock;
import de.ipnats.hardwrought.electricity.ElectricBlocks;
import de.ipnats.hardwrought.electricity.ElectricDriveBlock;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.Gauge;
import de.ipnats.hardwrought.electricity.MachineBlock;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import de.ipnats.hardwrought.haven.Appliance;
import de.ipnats.hardwrought.haven.HavenBlocks;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.storage.StorageBlocks;
import de.ipnats.hardwrought.storage.StorageDeskMenu;
import de.ipnats.hardwrought.storage.StorageNetwork;
import de.ipnats.hardwrought.storage.StoragePortBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Automation (docs/electricity-roadmap.md, stage E5): the assembler and its drawings, ordering at the
 * storage desk, the conveyor, and machines that wear.
 */
public final class AutomationGameTests {
    /** A drawing of the strap: two leather and an iron nugget make two. */
    private static ItemStack strapDrawing() {
        ItemStack drawing = new ItemStack(AutomationBlocks.DRAWING);
        drawing.set(DrawingItem.PLAN, new DrawingItem.Plan(de.ipnats.hardwrought.Hardwrought.id("strap"), new ItemStack(CarriedItems.STRAP, 2)));
        return drawing;
    }

    private static BlockPos dynamo(ServerLevel level, List<BlockPos> used, BlockPos base) {
        place(level, used, base.east(), ElectricBlocks.DYNAMO.defaultBlockState().setValue(DynamoBlock.FACING, Direction.EAST));
        place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
        return base.east();
    }

    @GameTest(maxTicks = 300)
    public void anAssemblerMakesWhatItsDrawingShowsAndWaitsForParts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(150);
        List<BlockPos> used = new ArrayList<>();
        BlockPos dynamo = dynamo(level, used, base);
        BlockPos at = base.offset(0, 0, 4);
        place(level, used, at, HavenBlocks.of(Appliance.ASSEMBLER).defaultBlockState());
        Electricity.connect(level, dynamo, at, Gauge.WIRE);
        var assembler = (AssemblerBlockEntity) level.getBlockEntity(at);
        assembler.items().setItem(0, strapDrawing());
        assembler.items().setItem(1, new ItemStack(Items.LEATHER, 2));
        assembler.items().setItem(2, new ItemStack(Items.IRON_NUGGET, 1));
        helper.runAfterDelay(260, () -> {
            try {
                int straps = 0, leather = 0;
                for (ItemStack stack : assembler.items().getItems()) {
                    if (stack.is(CarriedItems.STRAP)) straps += stack.getCount();
                    if (stack.is(Items.LEATHER)) leather += stack.getCount();
                }
                helper.assertTrue(straps == 2 && leather == 0, "It made what its drawing shows from the parts: " + straps + " straps");
                helper.assertTrue(assembler.conductance() == 0.0, "and with nothing left to make it from it takes no current");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 400)
    public void aDeskOrdersWhatTheStoreRoomLacksFromItsAssembler(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(160);
        List<BlockPos> used = new ArrayList<>();
        BlockPos desk = base.offset(0, 0, 4);
        place(level, used, desk, StorageBlocks.DESK.defaultBlockState());
        for (int step = 1; step <= 2; step++) place(level, used, desk.east(step), StorageBlocks.TUBE.defaultBlockState());
        BlockPos port = desk.east(1).south();
        place(level, used, port, StorageBlocks.PORT.defaultBlockState().setValue(StoragePortBlock.FACING, Direction.SOUTH));
        place(level, used, port.south(), Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(port.south());
        chest.setItem(0, new ItemStack(Items.LEATHER, 6));
        chest.setItem(1, new ItemStack(Items.IRON_NUGGET, 4));
        BlockPos at = desk.east(3);
        place(level, used, at, HavenBlocks.of(Appliance.ASSEMBLER).defaultBlockState());
        ((AssemblerBlockEntity) level.getBlockEntity(at)).items().setItem(0, strapDrawing());
        BlockPos dynamo = dynamo(level, used, base);
        Electricity.connect(level, dynamo, desk, Gauge.WIRE);
        Electricity.connect(level, dynamo, at, Gauge.WIRE);
        StorageNetwork network = StorageNetwork.find(level, desk);
        helper.assertTrue(network.makers().size() == 1 && network.maker(new ItemStack(CarriedItems.STRAP)) != null,
                "The assembler on the tubes is a maker for the desk");
        var player = helper.makeMockServerPlayerInLevel();
        player.teleportTo(desk.getX() + 0.5, desk.getY() + 1, desk.getZ() - 1.5);
        helper.runAfterDelay(20, () -> {
            StorageDeskMenu menu = new StorageDeskMenu(1, player.getInventory(), level, desk);
            menu.click(new ItemStack(CarriedItems.STRAP), StoragePayloads.TAKE);
            helper.assertTrue(((AssemblerBlockEntity) level.getBlockEntity(at)).owed() == 1, "Asked for what it lacks, the desk orders it");
        });
        helper.runAfterDelay(360, () -> {
            try {
                int straps = 0, leather = 0;
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    if (chest.getItem(slot).is(CarriedItems.STRAP)) straps += chest.getItem(slot).getCount();
                    if (chest.getItem(slot).is(Items.LEATHER)) leather += chest.getItem(slot).getCount();
                }
                helper.assertTrue(straps == 2, "The assembler fetched its parts from the store room and put the straps in it: " + straps);
                helper.assertTrue(leather == 4, "two leather gone into them: " + leather);
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 300)
    public void aConveyorCarriesItsLoadIntoTheChestAtItsEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(170);
        List<BlockPos> used = new ArrayList<>();
        BlockPos dynamo = dynamo(level, used, base);
        for (int x = -1; x <= 1; x++) for (int z = 2; z <= 9; z++) place(level, used, base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        // A drive to the west of the first length, its axle across the belt; the belt runs north to a chest.
        BlockPos first = base.offset(0, 0, 8);
        place(level, used, first.west(), ElectricBlocks.ELECTRIC_DRIVE.defaultBlockState().setValue(ElectricDriveBlock.FACING, Direction.EAST));
        for (int i = 0; i < 4; i++) place(level, used, first.north(i), AutomationBlocks.CONVEYOR.defaultBlockState().setValue(ConveyorBlock.FACING, Direction.NORTH));
        BlockPos chestAt = first.north(4);
        place(level, used, chestAt, Blocks.CHEST.defaultBlockState());
        Electricity.connect(level, dynamo, first.west(), Gauge.WIRE);
        ItemEntity load = new ItemEntity(level, first.getX() + 0.5, first.getY() + 0.5, first.getZ() + 0.5, new ItemStack(Items.IRON_INGOT, 3));
        load.setPickUpDelay(10000);
        helper.runAfterDelay(40, () -> level.addFreshEntity(load));
        helper.runAfterDelay(260, () -> {
            try {
                helper.assertTrue(((ConveyorBlockEntity) level.getBlockEntity(first.north(3))).beltSpeed() > 0.0,
                        "The whole belt turns from the one drive");
                var chest = (ChestBlockEntity) level.getBlockEntity(chestAt);
                int ingots = 0;
                for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(Items.IRON_INGOT)) ingots += chest.getItem(slot).getCount();
                helper.assertTrue(ingots == 3, "and carries the ingots into the chest at its end: " + ingots);
            } finally {
                load.discard();
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest
    public void aMachineRunDryWearsAndSeizes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(at, ElectricBlocks.BASIC_CRUSHER.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        var machine = (MachineBlockEntity) level.getBlockEntity(at);
        helper.assertTrue(machine.oil() == 1.0f && machine.condition() == 1.0, "A new machine comes oiled and works at full speed");
        machine.age(1.0, false);
        float oiled = machine.wear();
        machine.age(1.0, true);
        float dry = machine.wear() - oiled;
        helper.assertTrue(dry > oiled * 5.0f, "Dry, it wears six times as fast: " + oiled + " / " + dry);
        helper.assertTrue(machine.condition() < 1.0, "Worn, it works slower: " + machine.condition());
        machine.age(5.0, true);
        machine.insertFrom(new ItemStack(Items.RAW_IRON), true);
        helper.assertTrue(machine.seized() && machine.conductance() == 0.0, "Worn through, it seizes and takes nothing");
        helper.assertTrue(machine.note() != null, "and the meter says so");
        helper.assertTrue(machine.serviced(level) && !machine.seized() && machine.conductance() > 0.0, "A service kit sets it going again");
        helper.assertTrue(machine.oiled(), "and oil fills it again");
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        helper.succeed();
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
