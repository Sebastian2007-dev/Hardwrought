package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.machinery.CogwheelBlock;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.machinery.ShaftBlock;
import de.ipnats.hardwrought.machinery.ShaftBlockEntity;
import de.ipnats.hardwrought.machinery.WaterWheelBlock;
import de.ipnats.hardwrought.machinery.WindmillBlock;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Milestone 10 on screen: gears meshing, a belt between two shafts, a water wheel and a windmill.
 * Photographed, because a gear drawn on the wrong axis passes every server test there is.
 */
public final class MachineryClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                // Face north and build in front of the player, so the picture is the same every time.
                player.teleportTo(level, player.getX(), player.getY(), player.getZ(), java.util.Set.of(), 180.0f, -12.0f, true);
                BlockPos base = player.blockPosition().north(10).east(4).above();
                // A crank box driving a shaft east into a small gear, which meshes with another and a large one.
                level.setBlockAndUpdate(base.west(3), ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
                level.setBlockAndUpdate(base.west(2), ModBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
                level.setBlockAndUpdate(base.west(1), ModBlocks.GEARBOX.defaultBlockState());
                level.setBlockAndUpdate(base.west(1).south(), ModBlocks.COGWHEEL.defaultBlockState()
                        .setValue(CogwheelBlock.AXIS, Direction.Axis.Z));
                level.setBlockAndUpdate(base.west(1).south().above(), ModBlocks.COGWHEEL.defaultBlockState()
                        .setValue(CogwheelBlock.AXIS, Direction.Axis.Z));
                level.setBlockAndUpdate(base.west(2).south().above(2), ModBlocks.LARGE_COGWHEEL.defaultBlockState()
                        .setValue(CogwheelBlock.AXIS, Direction.Axis.Z));
                // A belt from the shaft up to another above it.
                BlockPos upper = base.west(2).above(3);
                level.setBlockAndUpdate(upper, ModBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
                ((ShaftBlockEntity) level.getBlockEntity(base.west(2))).setBelt(upper);
                ((ShaftBlockEntity) level.getBlockEntity(upper)).setBelt(base.west(2));
                de.ipnats.hardwrought.machinery.Kinetics.update(level, base.west(2));
                // A water wheel and a windmill, standing still here but drawn in full.
                level.setBlockAndUpdate(base.east(3).above(), ModBlocks.WATER_WHEEL.defaultBlockState()
                        .setValue(WaterWheelBlock.AXIS, Direction.Axis.X));
                level.setBlockAndUpdate(base.east(2).above(5), ModBlocks.WINDMILL.defaultBlockState()
                        .setValue(WindmillBlock.FACING, Direction.SOUTH));
            });
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-machinery");

            // Milestone 11: a rig on a crank box, and a still over a campfire with a charge in it.
            BlockPos[] still = new BlockPos[1];
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                BlockPos base = player.blockPosition().north(5).above();
                level.setBlockAndUpdate(base.west(2), ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
                level.setBlockAndUpdate(base.west(2).above(), ModBlocks.DRILLING_RIG.defaultBlockState());
                level.setBlockAndUpdate(base.east(2), net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
                level.setBlockAndUpdate(base.east(2).above(), ModBlocks.STILL.defaultBlockState());
                // An ore drill: a titanium frame round its head, a bronze block in it, a crank box under it.
                BlockPos head = base.north(3).above();
                level.setBlockAndUpdate(head.below(), ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
                // Facing south, toward the camera, so its chute is in the picture.
                level.setBlockAndUpdate(head, ModBlocks.ORE_DRILL.defaultBlockState()
                        .setValue(de.ipnats.hardwrought.machinery.OreDrillBlock.FACING, net.minecraft.core.Direction.SOUTH));
                // A pool of crude oil beside the still.
                level.setBlockAndUpdate(base.below().south(1).west(4), ModBlocks.CRUDE_OIL.defaultBlockState());
                var frame = de.ipnats.hardwrought.machinery.OreDrillBlockEntity.framePositions(head);
                for (int i = 0; i < frame.size(); i++) {
                    level.setBlockAndUpdate(frame.get(i), (i == 4 ? ModBlocks.DRILL_FRAME_BRONZE
                            : ModBlocks.DRILL_FRAME_TITANIUM).defaultBlockState());
                }
                still[0] = base.east(2).above();
                var entity = (de.ipnats.hardwrought.chemistry.StillBlockEntity) level.getBlockEntity(still[0]);
                entity.setItem(de.ipnats.hardwrought.chemistry.StillBlockEntity.CHARGE,
                        new net.minecraft.world.item.ItemStack(de.ipnats.hardwrought.core.registry.ModItems.CRUDE_OIL_BUCKET));
                entity.setItem(de.ipnats.hardwrought.chemistry.StillBlockEntity.BOTTLES,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 3));
                entity.setItem(de.ipnats.hardwrought.chemistry.StillBlockEntity.FIRST_OUTPUT,
                        de.ipnats.hardwrought.chemistry.Purity.with(new net.minecraft.world.item.ItemStack(
                                de.ipnats.hardwrought.core.registry.ModItems.RAW_SULFUR), 0.68));
            });
            context.waitTicks(60);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-oil-rig-and-still");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.openMenu((de.ipnats.hardwrought.chemistry.StillBlockEntity) player.level().getBlockEntity(still[0]));
            });
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-still-screen");
            context.runOnClient(client -> client.player.closeContainer());
            // The drill's own screen: what it is doing and what its chunk holds.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos head = player.blockPosition().north(5).above().north(3).above();
                var drill = (de.ipnats.hardwrought.machinery.OreDrillBlockEntity) player.level().getBlockEntity(head);
                drill.lookOverFrameNow();
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, drill.info(player, true));
            });
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-ore-drill-screen");
            context.runOnClient(client -> client.gui.setScreen(null));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                        de.ipnats.hardwrought.geology.OreScannerItem.scan(player));
            });
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-ore-scanner");
            context.runOnClient(client -> client.gui.setScreen(null));
            // How gasoline is made: one bookmark for the cracker, one for the reformer, each with the machine on it.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().add(new net.minecraft.world.item.ItemStack(de.ipnats.hardwrought.refining.RefineryBlocks.CRACKER));
            });
            context.waitTicks(10);
            context.runOnClient(client -> de.ipnats.hardwrought.client.knowledge.CompendiumClient.openRecipes(
                    de.ipnats.hardwrought.refining.RefineryProduct.GASOLINE.bucket()));
            context.waitTicks(20);
            context.takeScreenshot("hardwrought-compendium-gasoline");
            context.runOnClient(client -> de.ipnats.hardwrought.client.knowledge.CompendiumClient.openRecipes(
                    de.ipnats.hardwrought.refining.RefineryProduct.DIESEL.bucket()));
            context.waitTicks(20);
            context.takeScreenshot("hardwrought-compendium-diesel");
            context.runOnClient(client -> client.gui.setScreen(null));
            // The refinery's windows: a column of two trays over magma, half through its crude oil; a cracker; a gas tank.
            BlockPos[] column = new BlockPos[1];
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                BlockPos base = player.blockPosition().east(4);
                column[0] = base;
                level.setBlockAndUpdate(base.below(), net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(base, de.ipnats.hardwrought.refining.RefineryBlocks.BASE.defaultBlockState());
                level.setBlockAndUpdate(base.above(), de.ipnats.hardwrought.refining.RefineryBlocks.STAGE.defaultBlockState());
                level.setBlockAndUpdate(base.above(2), de.ipnats.hardwrought.refining.RefineryBlocks.STAGE.defaultBlockState());
                var entity = (de.ipnats.hardwrought.refining.RefineryBaseBlock.Entity) level.getBlockEntity(base);
                try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    entity.crude.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(de.ipnats.hardwrought.oil.ModFluids.CRUDE_OIL),
                            net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET * 6, transaction);
                    transaction.commit();
                }
                BlockPos cracker = base.south(2);
                level.setBlockAndUpdate(cracker.below(), net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(cracker, de.ipnats.hardwrought.refining.RefineryBlocks.CRACKER.defaultBlockState());
                var processor = (de.ipnats.hardwrought.refining.ProcessorBlock.Entity) level.getBlockEntity(cracker);
                try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    processor.input.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(
                            de.ipnats.hardwrought.refining.RefineryProduct.HEAVY_OIL.source()),
                            net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET * 3, transaction);
                    transaction.commit();
                }
                BlockPos gasTank = base.north(2);
                level.setBlockAndUpdate(gasTank, de.ipnats.hardwrought.gasline.GasLines.TANK.defaultBlockState());
                ((de.ipnats.hardwrought.gasline.GasTankBlock.Entity) level.getBlockEntity(gasTank)).store
                        .insert(de.ipnats.hardwrought.gasline.GasKind.LPG, 160);
                BlockPos flareTank = base.west(2);
                level.setBlockAndUpdate(flareTank, de.ipnats.hardwrought.fluids.FluidBlocks.TANK.defaultBlockState());
                try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    de.ipnats.hardwrought.fluids.FluidTankBlock.storage(level, flareTank).insert(
                            net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(de.ipnats.hardwrought.refining.RefineryProduct.NAPHTHA.source()),
                            net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET * 12, transaction);
                    transaction.commit();
                }
                BlockPos power = base.west(4);
                level.setBlockAndUpdate(power, de.ipnats.hardwrought.power.PowerBlocks.DIESEL_GENERATOR.defaultBlockState());
                var generator = (de.ipnats.hardwrought.power.DieselGeneratorBlock.Entity) level.getBlockEntity(power);
                try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    generator.fuel.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(
                            de.ipnats.hardwrought.refining.RefineryProduct.DIESEL.source()),
                            net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET * 3, transaction);
                    transaction.commit();
                }
                level.setBlockAndUpdate(power.north(), de.ipnats.hardwrought.power.PowerBlocks.GASOLINE_ENGINE.defaultBlockState());
                var engine = (de.ipnats.hardwrought.power.GasolineEngineBlock.Entity) level.getBlockEntity(power.north());
                engine.setPlugForTesting(de.ipnats.hardwrought.power.GasolineEngineBlock.Entity.PLUG_LIFE * 3 / 4);
                try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    engine.fuel.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(
                            de.ipnats.hardwrought.refining.RefineryProduct.GASOLINE.source()),
                            net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET * 2, transaction);
                    transaction.commit();
                }
                level.setBlockAndUpdate(power.south(), de.ipnats.hardwrought.power.PowerBlocks.GAS_BURNER.defaultBlockState());
                ((de.ipnats.hardwrought.power.GasBurnerBlock.Entity) level.getBlockEntity(power.south())).intake
                        .insert(de.ipnats.hardwrought.gasline.GasKind.NATURAL_GAS, 40);
                level.setBlockAndUpdate(power.south(2), de.ipnats.hardwrought.power.PowerBlocks.PETROLEUM_LAMP.defaultBlockState()
                        .setValue(de.ipnats.hardwrought.power.PetroleumLampBlock.LIT, true));
                BlockPos chem = base.west(6);
                level.setBlockAndUpdate(chem, de.ipnats.hardwrought.materials.MaterialBlocks.block(
                        de.ipnats.hardwrought.materials.ChemMachine.CHEMICAL_REACTOR).defaultBlockState());
                var reactor = (de.ipnats.hardwrought.materials.ChemMachineBlockEntity) level.getBlockEntity(chem);
                try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    reactor.fluid().insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(
                            de.ipnats.hardwrought.refining.RefineryProduct.NAPHTHA.source()),
                            net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET * 2, transaction);
                    transaction.commit();
                }
                reactor.gasIn.insert(de.ipnats.hardwrought.gasline.GasKind.LPG, 30);
                reactor.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RESIN_CLUMP, 6));
                reactor.setItem(2, new net.minecraft.world.item.ItemStack(de.ipnats.hardwrought.materials.Materials.PLASTIC_PELLETS, 12));
                level.setBlockAndUpdate(chem.north(), de.ipnats.hardwrought.electricity.ElectricBlocks.BASIC_CRUSHER_MK2.defaultBlockState());
                level.setBlockAndUpdate(chem.north(2), de.ipnats.hardwrought.electricity.ElectricBlocks.PLATE_PRESS_MK3.defaultBlockState());
                level.setBlockAndUpdate(chem.south(), de.ipnats.hardwrought.materials.MaterialBlocks.block(
                        de.ipnats.hardwrought.materials.ChemMachine.ELECTROLYZER).defaultBlockState());
                level.setBlockAndUpdate(chem.south(2), de.ipnats.hardwrought.materials.MaterialBlocks.block(
                        de.ipnats.hardwrought.materials.ChemMachine.ARC_FURNACE).defaultBlockState());
                level.setBlockAndUpdate(flareTank.above(), de.ipnats.hardwrought.refining.RefineryBlocks.FLARE.defaultBlockState()
                        .setValue(de.ipnats.hardwrought.refining.FlareStackBlock.LIT, true));
            });
            context.waitTicks(200);
            String[] shots = {"hardwrought-refinery-base", "hardwrought-refinery-tray", "hardwrought-cracker", "hardwrought-gas-tank", "hardwrought-burning-stack",
                    "hardwrought-diesel-generator", "hardwrought-gasoline-engine", "hardwrought-gas-burner",
                    "hardwrought-chemical-reactor"};
            for (int shot = 0; shot < shots.length; shot++) {
                int which = shot;
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    BlockPos at = switch (which) {
                        case 0 -> column[0];
                        case 1 -> column[0].above(2);
                        case 2 -> column[0].south(2);
                        case 4 -> column[0].west(2).above();
                        case 5 -> column[0].west(4);
                        case 6 -> column[0].west(4).north();
                        case 7 -> column[0].west(4).south();
                        case 8 -> column[0].west(6);
                        default -> column[0].north(2);
                    };
                    player.openMenu((net.minecraft.world.MenuProvider) player.level().getBlockEntity(at));
                });
                context.waitTicks(10);
                context.takeScreenshot(shots[shot]);
                context.runOnClient(client -> client.player.closeContainer());
            }
            // The compendium's view of the drill, whole and then its bottom layer on its own.
            var rig = de.ipnats.hardwrought.knowledge.Multiblocks.ORE_DRILL_RIG;
            var viewer = context.computeOnClient(client -> {
                var screen = new de.ipnats.hardwrought.client.knowledge.MultiblockScreen(null, rig, rig.blocks());
                client.gui.setScreen(screen);
                return screen;
            });
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-multiblock-view");
            context.runOnClient(client -> viewer.stepLayer(1));
            context.waitTicks(5);
            context.takeScreenshot("hardwrought-multiblock-layer");
            for (var big : java.util.List.of(de.ipnats.hardwrought.knowledge.Multiblocks.LARGE_ARC_FURNACE,
                    de.ipnats.hardwrought.knowledge.Multiblocks.LARGE_DIESEL)) {
                context.runOnClient(client -> client.gui.setScreen(
                        new de.ipnats.hardwrought.client.knowledge.MultiblockScreen(null, big, big.blocks())));
                context.waitTicks(10);
                context.takeScreenshot("hardwrought-multiblock-" + big.id().getPath());
            }
            var smeltery = de.ipnats.hardwrought.knowledge.Multiblocks.SMELTERY;
            context.runOnClient(client -> client.gui.setScreen(
                    new de.ipnats.hardwrought.client.knowledge.MultiblockScreen(null, smeltery, smeltery.blocks())));
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-multiblock-smeltery");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
        // The Ultra switch sits under the game mode when a world is made.
        context.runOnClient(client -> net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(client, null));
        context.waitTicks(20);
        context.runOnClient(client -> {
            for (var child : client.gui.screen().children()) {
                if (child instanceof net.minecraft.client.gui.components.CycleButton<?> button
                        && button.getMessage().getString().startsWith("Ultra")) {
                    button.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
                }
            }
        });
        context.waitTicks(5);
        context.takeScreenshot("hardwrought-create-world-ultra");
        context.runOnClient(client -> client.gui.setScreen(null));
    }
}
